package com.rosechat.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// После перезагрузки телефона или обновления приложения поднимаем фоновый сервис
// уведомлений — иначе до первого ручного открытия приложения ничего бы не приходило.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            NotificationService.start(context)
        }
    }
}
