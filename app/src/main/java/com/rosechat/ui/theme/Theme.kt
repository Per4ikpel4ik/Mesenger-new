package com.rosechat.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val palette = (if (ThemeState.isDark) DarkPalette else LightPalette).withAccent(ThemeState.accentTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            // true = тёмные иконки статус-бара (нужны на светлом фоне), и наоборот
            controller.isAppearanceLightStatusBars = !ThemeState.isDark
            controller.isAppearanceLightNavigationBars = !ThemeState.isDark
        }
    }

    // onPrimary/onSecondary/onError всегда белые — акцентные цвета достаточно
    // насыщенные, чтобы белый текст на них читался и в тёмной, и в светлой теме.
    val scheme = if (ThemeState.isDark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = Color.White,
            primaryContainer = palette.accentMuted, onPrimaryContainer = Color.White,
            secondary = palette.accentLight, onSecondary = Color.White,
            background = palette.bgPrimary, onBackground = palette.textPrimary,
            surface = palette.bgSecondary, onSurface = palette.textPrimary,
            surfaceVariant = palette.bgTertiary, onSurfaceVariant = palette.textSecondary,
            error = palette.errorRed, onError = Color.White,
            outline = palette.bgTertiary, outlineVariant = palette.divider
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = Color.White,
            primaryContainer = palette.accentMuted, onPrimaryContainer = Color.White,
            secondary = palette.accentLight, onSecondary = Color.White,
            background = palette.bgPrimary, onBackground = palette.textPrimary,
            surface = palette.bgSecondary, onSurface = palette.textPrimary,
            surfaceVariant = palette.bgTertiary, onSurfaceVariant = palette.textSecondary,
            error = palette.errorRed, onError = Color.White,
            outline = palette.bgTertiary, outlineVariant = palette.divider
        )
    }

    CompositionLocalProvider(LocalPingGramPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
