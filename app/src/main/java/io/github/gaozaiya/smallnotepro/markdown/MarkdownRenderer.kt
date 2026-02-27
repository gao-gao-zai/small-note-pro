package io.github.gaozaiya.smallnotepro.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser

/**
 * Markdown 渲染器。
 *
 * 将 Markdown 文本转换为 Compose AnnotatedString，支持标题、粗体、斜体、代码、链接等样式。
 */
object MarkdownRenderer {
    private val parser: Parser = Parser.builder().build()

    /**
     * 渲染 Markdown 文本。
     *
     * @param markdown Markdown 源文本。
     * @param baseFontSizeSp 基础字号（sp）。
     * @param baseColor 基础文字颜色。
     * @return 带样式的 AnnotatedString。
     */
    fun render(markdown: String, baseFontSizeSp: Float, baseColor: Color): AnnotatedString {
        val document = parser.parse(markdown)
        return buildAnnotatedString {
            renderNode(
                node = document,
                baseFontSizeSp = baseFontSizeSp,
                baseColor = baseColor,
                currentStyle = SpanStyle(fontSize = baseFontSizeSp.sp, color = baseColor),
            )
        }
    }

    private fun AnnotatedString.Builder.renderNode(
        node: Node,
        baseFontSizeSp: Float,
        baseColor: Color,
        currentStyle: SpanStyle,
    ) {
        var child: Node? = node.firstChild
        while (child != null) {
            when (child) {
                is Text -> {
                    val textNode = child
                    withStyle(currentStyle) {
                        append(textNode.literal)
                    }
                }
                is SoftLineBreak -> append("\n")
                is Paragraph -> {
                    renderNode(child, baseFontSizeSp, baseColor, currentStyle)
                    append("\n\n")
                }
                is Heading -> {
                    val scale = when (child.level) {
                        1 -> 1.6f
                        2 -> 1.4f
                        3 -> 1.25f
                        else -> 1.15f
                    }
                    val headingStyle = currentStyle.merge(
                        SpanStyle(fontSize = (baseFontSizeSp * scale).sp, fontWeight = FontWeight.SemiBold),
                    )
                    renderNode(child, baseFontSizeSp, baseColor, headingStyle)
                    append("\n\n")
                }
                is StrongEmphasis -> {
                    renderNode(
                        child,
                        baseFontSizeSp,
                        baseColor,
                        currentStyle.merge(SpanStyle(fontWeight = FontWeight.Bold)),
                    )
                }
                is Emphasis -> {
                    renderNode(
                        child,
                        baseFontSizeSp,
                        baseColor,
                        currentStyle.merge(SpanStyle(fontStyle = FontStyle.Italic)),
                    )
                }
                is Code -> {
                    val codeNode = child
                    val codeStyle = currentStyle.merge(
                        SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                    )
                    withStyle(codeStyle) {
                        append(codeNode.literal)
                    }
                }
                is Link -> {
                    val linkStyle = currentStyle.merge(
                        SpanStyle(color = Color(0xFF64B5F6), fontWeight = FontWeight.Medium),
                    )
                    renderNode(child, baseFontSizeSp, baseColor, linkStyle)
                }
                else -> renderNode(child, baseFontSizeSp, baseColor, currentStyle)
            }
            child = child.next
        }
    }
}
