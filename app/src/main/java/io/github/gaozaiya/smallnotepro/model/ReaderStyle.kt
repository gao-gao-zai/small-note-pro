package io.github.gaozaiya.smallnotepro.model

import androidx.compose.ui.graphics.Color

data class ReaderStyle(
    val backgroundColor: Color = Color(0xFF000000),
    val textColor: Color = Color(0xFFECECEC),
    val textBrightness: Float = 1f,
    val fontSizeSp: Float = 16f,
)
