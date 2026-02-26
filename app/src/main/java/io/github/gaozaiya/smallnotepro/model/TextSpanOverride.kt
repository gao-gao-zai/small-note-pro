package io.github.gaozaiya.smallnotepro.model

import androidx.compose.ui.graphics.Color

data class TextSpanOverride(
    val start: Int,
    val end: Int,
    val fontSizeSp: Float? = null,
    val color: Color? = null,
    val brightness: Float? = null,
)
