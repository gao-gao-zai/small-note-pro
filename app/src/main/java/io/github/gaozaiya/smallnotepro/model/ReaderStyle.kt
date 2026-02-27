package io.github.gaozaiya.smallnotepro.model

import androidx.compose.ui.graphics.Color

/**
 * 阅读器样式配置。
 *
 * 定义阅读界面的视觉样式，包括背景色、文字颜色/字号/亮度，以及 UI 控件的配色。
 */
data class ReaderStyle(
    val backgroundColor: Color = Color(0xFF000000),
    val textColor: Color = Color(0xFFECECEC),
    val textAlpha: Float = 1f,
    val textBrightness: Float = 1f,
    val fontSizeSp: Float = 16f,
    val uiSurfaceColor: Color = Color(0xFF1B1B1B),
    val uiOnSurfaceColor: Color = Color(0xFFECECEC),
    val uiAccentColor: Color = Color(0xFF4CAF50),
)
