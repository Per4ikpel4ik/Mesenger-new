package com.rosechat.shared.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Видеозвонки есть только на iPhone (там движок WebRTC); тут ничего не рисуем.
@Composable
actual fun CallVideoSurface(remote: Boolean, modifier: Modifier) {}
