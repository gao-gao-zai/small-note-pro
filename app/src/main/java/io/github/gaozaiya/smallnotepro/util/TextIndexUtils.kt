package io.github.gaozaiya.smallnotepro.util

object TextIndexUtils {
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
