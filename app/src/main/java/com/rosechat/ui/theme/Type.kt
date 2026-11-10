package com.rosechat.ui.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
val AppTypography = Typography(
    headlineLarge  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 28.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 16.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 12.sp),
    labelMedium    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 11.sp)
)
