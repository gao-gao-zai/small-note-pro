package io.github.gaozaiya.smallnotepro.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object ColorUtils {
    fun applyBrightness(color: Color, brightness: Float): Color {
        val clamped = brightness.coerceAtLeast(0f)
        val hsv = FloatArray(3)
        val argb = color.toArgb()
        android.graphics.Color.colorToHSV(argb, hsv)

        hsv[2] = (hsv[2] * clamped).coerceIn(0f, 1f)

        val alpha = android.graphics.Color.alpha(argb)
        val rgb = android.graphics.Color.HSVToColor(alpha, hsv)
        return Color(rgb)
    }
}
