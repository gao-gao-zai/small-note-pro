package io.github.gaozaiya.smallnotepro.util

/**
 * 文本索引工具类。
 *
 * 提供基于字符偏移的文本位置计算功能。
 */
object TextIndexUtils {
    /**
     * 根据行号查找文本范围。
     *
     * @param text 文本内容。
     * @param lineNumber1Based 行号（1-based，即第一行为 1）。
     * @return 该行的字符范围 [start, end)，行号无效时返回 null。
     */
    fun findLineRange(text: String, lineNumber1Based: Int): IntRange? {
        if (lineNumber1Based <= 0) return null
        var currentLine = 1
        var start = 0

        val length = text.length
        for (i in 0..length) {
            val isEnd = i == length
            val isNewline = !isEnd && text[i] == '\n'
            if (isEnd || isNewline) {
                val endExclusive = i
                if (currentLine == lineNumber1Based) {
                    return start until endExclusive
                }
                currentLine += 1
                start = i + 1
            }
        }
        return null
    }
}
