package com.rosechat.shared.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

// Swift-класс WebRTCCallEngine реализует и этот интерфейс: отдаёт готовые UIView для видео.
interface CallVideoViews {
    fun localView(): UIView
    fun remoteView(): UIView
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
@Composable
actual fun CallVideoSurface(remote: Boolean, modifier: Modifier) {
    val views = CallPlatform.engine as? CallVideoViews ?: return
    UIKitView(
        factory = { if (remote) views.remoteView() else views.localView() },
        modifier = modifier,
    )
}
