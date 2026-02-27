package io.github.gaozaiya.smallnotepro.util

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 文本内容加载器。
 *
 * 负责从文件加载文本内容，自动检测编码格式。
 * 支持 BOM 检测、多编码候选尝试、文本质量评分等策略。
 */
object TextContentLoader {
    /**
     * 文本加载结果。
     *
     * @property text 加载成功的文本内容，失败时为 null。
     * @property charsetName 检测到的编码名称。
     * @property errorMessage 错误信息，成功时为 null。
     */
    data class Result(
        val text: String?,
        val charsetName: String?,
        val errorMessage: String?,
    ) {
        val isSuccess: Boolean = text != null && errorMessage == null
    }

    /**
     * 编码检测结果。
     *
     * @property charset 检测到的编码，失败时为 null。
     * @property charsetName 编码名称。
     * @property errorMessage 错误信息。
     */
    data class CharsetDetectResult(
        val charset: Charset?,
        val charsetName: String?,
        val errorMessage: String?,
    ) {
        val isSuccess: Boolean = charset != null && errorMessage == null
    }

    /**
     * 加载文本文件内容。
     *
     * 自动检测编码，优先尝试 BOM 指示的编码，再依次尝试 UTF-8、UTF-16、GB18030 等常见编码。
     * 通过文本质量评分选择最佳解码结果。
     *
     * @param appContext Android 应用上下文。
     * @param uri 文件 URI。
     * @param maxBytes 最大读取字节数，超过则返回"文件过大"错误。
     * @return 加载结果。
     */
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

    /**
     * 检测文件编码（仅采样，不加载全文）。
     *
     * 用于大文件模式，只读取前 [sampleBytes] 字节来推断编码。
     * 采样越大越准确，但 IO/内存成本也越高。
     *
     * @param appContext Android 应用上下文。
     * @param uri 文件 URI。
     * @param sampleBytes 采样字节数，默认 256KB。
     * @return 编码检测结果。
     */
    fun detectCharset(appContext: Context, uri: Uri, sampleBytes: Int = 256 * 1024): CharsetDetectResult {
        // 大文件模式下不能一次性读入全文；这里只采样前 N 字节来推断编码。
        // 采样越大越准确，但 IO/内存成本也越高。
        val bytes = try {
            readSampleBytes(appContext, uri, sampleBytes)
        } catch (_: Exception) {
            return CharsetDetectResult(charset = null, charsetName = null, errorMessage = "读取失败")
        }

        if (bytes.isEmpty()) {
            return CharsetDetectResult(charset = Charsets.UTF_8, charsetName = Charsets.UTF_8.name(), errorMessage = null)
        }

        val bom = detectBom(bytes)
        if (looksBinary(bytes, bom)) {
            return CharsetDetectResult(charset = null, charsetName = null, errorMessage = "不是纯文本文件")
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
                    // 复用全文加载时的“文本质量评分”策略（避免误把二进制当文本）。
                    val score = textQualityScore(decoded)
                    charset to score
                }
            }
            .maxByOrNull { it.second }

        val charset = best?.first
        val charsetName = charset?.name()

        if (charset == null) {
            return CharsetDetectResult(charset = null, charsetName = null, errorMessage = "无法识别文本编码")
        }

        return CharsetDetectResult(charset = charset, charsetName = charsetName, errorMessage = null)
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

    private fun readSampleBytes(appContext: Context, uri: Uri, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (out.size() < maxBytes) {
                val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - out.size()))
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
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
