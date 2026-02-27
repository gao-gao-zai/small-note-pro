package io.github.gaozaiya.smallnotepro.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 颜色工具类。
 *
 * 提供颜色亮度调整等实用方法。
 */
object ColorUtils {
    /**
     * 调整颜色亮度。
     *
     * 通过 HSV 色彩空间的 V（明度）分量调整亮度。
     *
     * @param color 原始颜色。
     * @param brightness 亮度系数，1.0 为原始亮度，>1 增亮，<1 变暗。
     * @return 调整后的颜色。
     */
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
