package com.minireader.app.ui.reader

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object HtmlDecoder {

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }

        val prefix = String(
            bytes,
            0,
            minOf(bytes.size, 2048),
            StandardCharsets.ISO_8859_1,
        )
        val charsetName = CHARSET_PATTERN.find(prefix)?.groupValues?.get(1)
        val charset = runCatching { Charset.forName(charsetName ?: "UTF-8") }
            .getOrDefault(StandardCharsets.UTF_8)
        return runCatching { String(bytes, charset) }
            .getOrDefault(String(bytes, StandardCharsets.UTF_8))
    }

    private val CHARSET_PATTERN = Regex(
        "charset\\s*=\\s*[\"']?([A-Za-z0-9._-]+)",
        RegexOption.IGNORE_CASE,
    )
}
