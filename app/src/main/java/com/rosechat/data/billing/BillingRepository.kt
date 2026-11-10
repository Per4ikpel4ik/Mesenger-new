package com.rosechat.data.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.android.billingclient.api.*
import com.google.firebase.Timestamp
import com.rosechat.data.repository.FirebaseRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Date
import kotlin.coroutines.resume

/**
 * Google Play Billing — Premium-подписка с автопродлением.
 *
 * ═══ ЧТО НУЖНО НАСТРОИТЬ В GOOGLE PLAY CONSOLE (один раз, до того как это заработает) ═══
 * 1. play.google.com/console → зарегистрировать аккаунт разработчика (разовый взнос $25).
 * 2. Создать приложение, загрузить хотя бы один AAB/APK на закрытый тест (internal testing) —
 *    без этого раздел "Продукты → Подписки" в консоли недоступен вообще.
 * 3. Monetize → Products → Subscriptions → Create subscription. Product ID указать РОВНО
 *    как в PRODUCT_ID ниже — "pinggram_premium_monthly" (или поменяй константу под свой id).
 *    Внутри подписки создать Base plan (auto-renewing, период — месяц), задать цену, Activate.
 * 4. Settings → License testing → добавить свой Google-аккаунт в тестовые, чтобы покупать
 *    без реального списания денег во время проверки.
 *
 * ═══ ВАЖНО ПРО ЗАЩИТУ ОТ НАКРУТКИ (прочитать перед тем как считать это "готово к продажам") ═══
 * После успешной покупки этот код сам пишет isPremium/premiumUntil в Firestore С КЛИЕНТА —
 * ровно так же, как уже работает код "gg" (см. FirebaseRepo.activatePremium). Правила Firestore
 * это разрешают (isMe(uid)), а значит технически тот, кто умеет напрямую вызывать Firestore
 * SDK/REST в обход приложения, может выставить себе isPremium так же, как через "gg" — в обход
 * реальной оплаты. Это тот же уровень доверия, что уже принят в проекте (см. комментарий у
 * isDevCaller() в firestore.rules), не что-то новое, но для НАСТОЯЩЕЙ защиты от накрутки нужна
 * отдельная Cloud Function: клиент передаёт ей purchaseToken, она проверяет его на сервере через
 * Google Play Developer API (нужен сервисный аккаунт — Play Console → Setup → API access) и
 * только тогда сама, через Admin SDK (минуя правила), выставляет isPremium. Это можно добавить
 * отдельным шагом, когда дойдёт очередь и будет создан сервисный аккаунт.
 */
object BillingRepository {
    private const val TAG = "BillingRepository"

    // Должен точь-в-точь совпадать с Product ID, который ты создашь в Play Console.
    const val PRODUCT_ID = "pinggram_premium_monthly"

    // Чуть больше месяца — запас на случай, если пользователь не откроет приложение
    // ровно в день продления (иначе cleanupExpiredPremium погасит Premium раньше времени).
    private const val GRACE_DAYS = 32L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var billingClient: BillingClient? = null
    private var cachedProductDetails: ProductDetails? = null

    var lastError: String? = null
        private set

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null ->
                purchases.forEach { handlePurchase(it) }
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> {
                lastError = result.debugMessage
                Log.e(TAG, "purchase update error: ${result.debugMessage}")
            }
        }
    }

    private fun ensureClient(context: Context): BillingClient {
        billingClient?.let { if (it.isReady) return it }
        val client = BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesListener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        billingClient = client
        return client
    }

    private suspend fun connect(context: Context): BillingClient = suspendCancellableCoroutine { cont ->
        val client = ensureClient(context)
        if (client.isReady) { cont.resume(client); return@suspendCancellableCoroutine }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) lastError = result.debugMessage
                if (cont.isActive) cont.resume(client) // isReady проверяет вызывающая сторона
            }
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "billing service disconnected, will reconnect on next call")
            }
        })
    }

    // Цена берётся напрямую из Play (локализована под страну/валюту пользователя, как и
    // положено) — нигде в коде не хардкодится.
    suspend fun fetchPrice(context: Context): String? {
        val client = connect(context)
        if (!client.isReady) { lastError = "Play Billing недоступен"; return null }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        return suspendCancellableCoroutine { cont ->
            // ВАЖНО (то, что поймала твоя сборка): в версии Billing Library, которую реально
            // подтянул Gradle, queryProductDetailsAsync отдаёт список продуктов НАПРЯМУЮ вторым
            // параметром (классическая, стабильная сигнатура), а не обёрнутым в объект с полем
            // .productDetailsList — я ошибочно предположил более новый вариант API. Из-за этого
            // Kotlin не мог вывести тип `details`, и все три ошибки ниже по цепочке
            // (subscriptionOfferDetails/pricingPhases/formattedPrice) были следствием этой одной
            // строки, а не отдельными багами.
            client.queryProductDetailsAsync(params) { result, list ->
                val details = list.firstOrNull()
                cachedProductDetails = details
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    lastError = result.debugMessage
                } else if (details == null) {
                    lastError = "Подписка \"$PRODUCT_ID\" не найдена в Play Console"
                }
                val price = details?.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                if (cont.isActive) cont.resume(price)
            }
        }
    }

    suspend fun launchPurchase(activity: Activity) {
        val client = connect(activity)
        if (!client.isReady) { lastError = "Play Billing недоступен"; return }
        val details = cachedProductDetails ?: run { fetchPrice(activity); cachedProductDetails }
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (details == null || offerToken == null) {
            lastError = "Подписка не найдена в Play Console (Product ID: $PRODUCT_ID)"
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details).setOfferToken(offerToken).build()
        val flowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build()
        client.launchBillingFlow(activity, flowParams)
        // Результат придёт в purchasesListener (onPurchasesUpdated), не сюда — покупка в Play
        // открывается как отдельный экран поверх приложения.
    }

    // Вызывать при открытии PremiumScreen: (а) восстанавливает Premium, если человек
    // переустановил приложение или сменил телефон; (б) продлевает premiumUntil, если очередное
    // списание подписки прошло успешно. Если пользователь отменил подписку — Play перестаёт
    // возвращать её здесь после конца оплаченного периода, и premiumUntil сам погаснет через
    // cleanupExpiredPremium (Cloud Function) — досрочно отбирать не нужно.
    suspend fun refreshSubscriptionStatus(context: Context) {
        val client = connect(context)
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        suspendCancellableCoroutine<Unit> { cont ->
            client.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.forEach { handlePurchase(it) }
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val uid = FirebaseRepo.currentUid
        if (uid != null) {
            val until = Timestamp(Date(System.currentTimeMillis() + GRACE_DAYS * 24 * 60 * 60 * 1000))
            scope.launch { FirebaseRepo.activatePaidPremium(uid, until) }
        }

        if (!purchase.isAcknowledged) {
            val client = billingClient ?: return
            val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(ackParams) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.e(TAG, "acknowledge failed: ${result.debugMessage}")
                }
            }
        }
    }
}

// BillingClient.launchBillingFlow() требует именно Activity, а Compose LocalContext.current
// на некоторых экранах отдаёт обёрнутый Context (ContextWrapper) — разворачиваем до Activity.
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
