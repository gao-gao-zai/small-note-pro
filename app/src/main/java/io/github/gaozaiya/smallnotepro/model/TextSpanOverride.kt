package io.github.gaozaiya.smallnotepro.model

import androidx.compose.ui.graphics.Color

/**
 * 文本片段样式覆盖。
 *
 * 用于对文本的特定区间应用不同于全局样式的局部样式，如高亮某行或调整字号。
 *
 * @property start 起始字符偏移（包含）。
 * @property end 结束字符偏移（不包含）。
 * @property fontSizeSp 局部字号，null 表示使用全局字号。
 * @property color 局部颜色，null 表示使用全局颜色。
 * @property brightness 局部亮度，null 表示使用全局亮度。
 */
data class TextSpanOverride(
    val start: Int,
    val end: Int,
    val fontSizeSp: Float? = null,
    val color: Color? = null,
    val brightness: Float? = null,
)
