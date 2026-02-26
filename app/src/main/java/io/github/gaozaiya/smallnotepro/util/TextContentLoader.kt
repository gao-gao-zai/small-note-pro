package io.github.gaozaiya.smallnotepro.util

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TextContentLoader {
    data class Result(
        val text: String?,
        val charsetName: String?,
        val errorMessage: String?,
    ) {
        val isSuccess: Boolean = text != null && errorMessage == null
    }

    fun load(appContext: Context, uri: Uri, maxBytes: Int = 1_000_000): Result {
        val read = try {
            readAllBytesCapped(appContext, uri, maxBytes)
        } catch (e: Exception) {
            return Result(text = null, charsetName = null, errorMessage = "读取失败")
        }

        if (read.isTruncated) {
            return Result(text = null, charsetName = null, errorMessage = "文件过大")
        }

        val bytes = read.bytes

        if (bytes.isEmpty()) {
            return Result(text = "", charsetName = "", errorMessage = null)
        }

        val bom = detectBom(bytes)

        if (looksBinary(bytes, bom)) {
            return Result(text = null, charsetName = null, errorMessage = "不是纯文本文件")
        }
        val decodeCandidates = buildList {
            bom?.let { add(it) }
            add(Charsets.UTF_8)
            add(Charsets.UTF_16LE)
            add(Charsets.UTF_16BE)

            addIfSupported("GB18030")
            addIfSupported("GBK")
            addIfSupported("Big5")
            addIfSupported("Shift_JIS")
            addIfSupported("EUC-KR")
        }

        val best = decodeCandidates
            .distinctBy { it.name() }
            .mapNotNull { charset ->
                strictDecode(bytes, charset, bom)?.let { decoded ->
                    val score = textQualityScore(decoded)
                    charset to (decoded to score)
                }
            }
            .maxByOrNull { it.second.second }

        val decoded = best?.second?.first
        val charsetName = best?.first?.name()

        if (decoded == null) {
            return Result(text = null, charsetName = null, errorMessage = "无法识别文本编码")
        }

        if (textQualityScore(decoded) < 0.90f) {
            return Result(text = null, charsetName = charsetName, errorMessage = "不是纯文本文件")
        }

        return Result(text = decoded, charsetName = charsetName, errorMessage = null)
    }

    private fun buildList(builder: MutableList<Charset>.() -> Unit): List<Charset> {
        val list = mutableListOf<Charset>()
        list.builder()
        return list
    }

    private fun MutableList<Charset>.addIfSupported(name: String) {
        runCatching { add(Charset.forName(name)) }
    }

    private data class ReadResult(
        val bytes: ByteArray,
        val isTruncated: Boolean,
    )

    private fun readAllBytesCapped(appContext: Context, uri: Uri, maxBytes: Int): ReadResult {
        val out = ByteArrayOutputStream()
        var truncated = false
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break

                val remaining = maxBytes - out.size()
                if (remaining <= 0) {
                    truncated = true
                    break
                }

                out.write(buffer, 0, minOf(read, remaining))
                if (read > remaining) {
                    truncated = true
                    break
                }
            }
        }
        return ReadResult(bytes = out.toByteArray(), isTruncated = truncated)
    }

    private fun looksBinary(bytes: ByteArray, bom: Charset?): Boolean {
        val nulCount = bytes.count { it == 0.toByte() }
        if (nulCount > 0) {
            if (bom == Charsets.UTF_16LE || bom == Charsets.UTF_16BE) {
                return false
            }

            val sampleSize = minOf(bytes.size, 8 * 1024)
            var evenNul = 0
            var oddNul = 0
            for (i in 0 until sampleSize) {
                if (bytes[i] == 0.toByte()) {
                    if (i % 2 == 0) evenNul += 1 else oddNul += 1
                }
            }
            val nulRatio = (evenNul + oddNul).toFloat() / sampleSize.toFloat()
            val likelyUtf16 = nulRatio > 0.10f && (evenNul == 0 || oddNul == 0 || (maxOf(evenNul, oddNul).toFloat() / (evenNul + oddNul).toFloat()) > 0.9f)
            if (!likelyUtf16) return true
        }

        val sampleSize = minOf(bytes.size, 8 * 1024)
        var suspicious = 0

        for (i in 0 until sampleSize) {
            val b = bytes[i].toInt() and 0xFF
            if (b < 0x09) {
                suspicious += 1
                continue
            }
            if (b in 0x0B..0x1F) {
                suspicious += 1
                continue
            }
            if (b == 0x7F) {
                suspicious += 1
            }
        }

        return (suspicious.toFloat() / sampleSize.toFloat()) > 0.02f
    }

    private fun detectBom(bytes: ByteArray): Charset? {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE
        }
        return null
    }

    private fun strictDecode(bytes: ByteArray, charset: Charset, bom: Charset?): String? {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val shouldStripBom = when (bom) {
            Charsets.UTF_8 -> charset == Charsets.UTF_8
            Charsets.UTF_16LE -> charset == Charsets.UTF_16LE
            Charsets.UTF_16BE -> charset == Charsets.UTF_16BE
            else -> false
        }

        val buffer = ByteBuffer.wrap(if (shouldStripBom) stripBom(bytes) else bytes)
        return try {
            val out: CharBuffer = decoder.decode(buffer)
            out.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun stripBom(bytes: ByteArray): ByteArray {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return bytes.copyOfRange(2, bytes.size)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return bytes.copyOfRange(2, bytes.size)
        }
        return bytes
    }

    private fun textQualityScore(text: String): Float {
        if (text.isEmpty()) return 1f

        var bad = 0
        for (c in text) {
            if (c == '\uFFFD') {
                bad += 1
                continue
            }
            val code = c.code
            if (code < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                bad += 1
            }
        }
        return 1f - (bad.toFloat() / text.length.toFloat())
    }
}
