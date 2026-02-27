package io.github.gaozaiya.smallnotepro.util

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 大文件分页读取器。
 *
 * 针对超大文本文件（如长篇小说），采用按字节偏移分页的策略，避免一次性将全文载入内存。
 * 分页时尽量在换行符处切分，减少"半行被切开"的观感。
 */
object PagedTextFileReader {
    /**
     * 分页索引，记录每页起始字节偏移。
     *
     * @property offsets 每页起始字节偏移列表，首元素始终为 0。
     * @property fileSize 文件总字节数。
     */
    data class PageIndex(
        val offsets: List<Long>,
        val fileSize: Long,
    ) {
        val pageCount: Int = offsets.size

        /**
         * 获取指定页的字节范围。
         *
         * @param pageIndex 页索引（0-based）。
         * @return 该页的字节范围 [start, end)。
         */
        fun pageRange(pageIndex: Int): LongRange {
            val start = offsets[pageIndex]
            val end = if (pageIndex + 1 < offsets.size) offsets[pageIndex + 1] else fileSize
            return start until end
        }
    }

    /**
     * 构建分页索引。
     *
     * 扫描文件，在接近 [pageTargetBytes] 时寻找换行符进行分页。
     * 支持 UTF-8、UTF-16LE、UTF-16BE 等编码的换行符识别。
     *
     * @param appContext Android 应用上下文。
     * @param uri 文件 URI。
     * @param charset 文本编码。
     * @param pageTargetBytes 目标页大小（字节），默认 128KB。
     * @return 分页索引。
     */
    fun buildPageIndex(
        appContext: Context,
        uri: Uri,
        charset: Charset,
        pageTargetBytes: Int = 128 * 1024,
    ): PageIndex {
        val offsets = mutableListOf(0L)
        var fileSize = 0L

        val isUtf16Le = charset == Charsets.UTF_16LE
        val isUtf16Be = charset == Charsets.UTF_16BE
        val isUtf16 = isUtf16Le || isUtf16Be

        // UTF-16 换行符本质是 0x000A；但字节序不同导致两个字节的排列不同。
        val newLineFirst = if (isUtf16Be) 0x00 else 0x0A
        val newLineSecond = if (isUtf16Be) 0x0A else 0x00

        appContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var pageBytes = 0
            var shouldSplit = false

            // UTF-16 可能出现跨 buffer 的“半个字符”，这里把最后一个字节暂存，和下一块拼成 2 字节再判断换行。
            var carry: Int? = null

            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break

                if (!isUtf16) {
                    for (i in 0 until read) {
                        val b = buffer[i].toInt() and 0xFF
                        fileSize += 1
                        pageBytes += 1

                        if (!shouldSplit && pageBytes >= pageTargetBytes) {
                            shouldSplit = true
                        }

                        if (shouldSplit && b == 0x0A) {
                            val nextStart = fileSize
                            if (offsets.last() != nextStart && nextStart < Long.MAX_VALUE) {
                                offsets.add(nextStart)
                            }
                            pageBytes = 0
                            shouldSplit = false
                        }
                    }
                } else {
                    var i = 0

                    if (carry != null) {
                        val b0 = carry
                        val b1 = buffer[0].toInt() and 0xFF
                        fileSize += 1
                        pageBytes += 1

                        if (!shouldSplit && pageBytes >= pageTargetBytes) {
                            shouldSplit = true
                        }

                        fileSize += 1
                        pageBytes += 1

                        if (!shouldSplit && pageBytes >= pageTargetBytes) {
                            shouldSplit = true
                        }

                        if (shouldSplit && b0 == newLineFirst && b1 == newLineSecond) {
                            val nextStart = fileSize
                            if (offsets.last() != nextStart) {
                                offsets.add(nextStart)
                            }
                            pageBytes = 0
                            shouldSplit = false
                        }

                        carry = null
                        i = 1
                    }

                    while (i + 1 < read) {
                        val b0 = buffer[i].toInt() and 0xFF
                        val b1 = buffer[i + 1].toInt() and 0xFF

                        fileSize += 2
                        pageBytes += 2

                        if (!shouldSplit && pageBytes >= pageTargetBytes) {
                            shouldSplit = true
                        }

                        if (shouldSplit && b0 == newLineFirst && b1 == newLineSecond) {
                            val nextStart = fileSize
                            if (offsets.last() != nextStart) {
                                offsets.add(nextStart)
                            }
                            pageBytes = 0
                            shouldSplit = false
                        }

                        i += 2
                    }

                    if (i < read) {
                        carry = buffer[i].toInt() and 0xFF
                        fileSize += 1
                        pageBytes += 1

                        if (!shouldSplit && pageBytes >= pageTargetBytes) {
                            shouldSplit = true
                        }
                    }
                }
            }

            if (carry != null) {
                fileSize += 1
            }
        }

        if (offsets.isEmpty()) {
            offsets.add(0L)
        }

        val normalized = offsets.distinct().sorted().filter { it < fileSize }
        return PageIndex(offsets = if (normalized.isEmpty()) listOf(0L) else normalized, fileSize = fileSize)
    }

    /**
     * 读取指定字节范围的文本内容。
     *
     * 通过 FileChannel 定位读取，无需从头遍历文件。
     * 自动处理 BOM（仅首页需要）和解码错误。
     *
     * @param appContext Android 应用上下文。
     * @param uri 文件 URI。
     * @param charset 文本编码。
     * @param range 字节范围 [start, end]。
     * @return 解码后的文本内容。
     */
    fun readPage(
        appContext: Context,
        uri: Uri,
        charset: Charset,
        range: LongRange,
    ): String {
        val start = range.first
        val endExclusive = range.last + 1
        val size = (endExclusive - start).coerceAtLeast(0L)
        if (size == 0L) return ""

        // 通过 FileChannel.position() 定位读取，不需要把文件流从头读到尾。
        val bytes = appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                readBytes(channel, start, size)
            }
        } ?: return ""

        val normalizedBytes = if (start == 0L) stripBom(bytes) else bytes

        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)

        return decoder.decode(ByteBuffer.wrap(normalizedBytes)).toString()
    }

    private fun readBytes(channel: FileChannel, start: Long, size: Long): ByteArray {
        val length = size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val bytes = ByteArray(length)

        channel.position(start)
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer)
            if (read <= 0) break
        }
        return bytes
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
}
