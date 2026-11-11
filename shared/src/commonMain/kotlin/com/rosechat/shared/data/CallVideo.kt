package com.rosechat.shared.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Нативная поверхность для видео звонка: remote = true — собеседник, false — своя камера.
// На iPhone это UIView из Swift-движка (WebRTC), на остальных платформах пусто.
@Composable
expect fun CallVideoSurface(remote: Boolean, modifier: Modifier)
