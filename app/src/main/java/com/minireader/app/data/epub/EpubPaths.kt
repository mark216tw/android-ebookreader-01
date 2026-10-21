package com.minireader.app.data.epub

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Pure-Java helpers for resolving EPUB hrefs. Keeping these free of Android
 * classes makes the parser unit-testable on the JVM.
 */
object EpubPaths {

    fun normalize(path: String): String {
        val parts = path.replace('\\', '/').split('/')
        val output = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                "", "." -> Unit
                ".." -> if (output.isNotEmpty()) output.removeLast()
                else -> output.addLast(part)
            }
        }
        return output.joinToString("/")
    }

    fun percentDecode(input: String): String {
        if ('%' !in input) return input
        val output = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            if (input[index] == '%' && index + 2 < input.length) {
                val bytes = ByteArrayOutputStream()
                while (index + 2 < input.length && input[index] == '%') {
                    val value = input.substring(index + 1, index + 3).toIntOrNull(16)
                        ?: break
                    bytes.write(value)
                    index += 3
                }
                output.append(String(bytes.toByteArray(), StandardCharsets.UTF_8))
            } else {
                output.append(input[index])
                index++
            }
        }
        return output.toString()
    }

    fun encodeSegment(segment: String): String {
        val bytes = segment.toByteArray(StandardCharsets.UTF_8)
        val output = StringBuilder(bytes.size)
        for (raw in bytes) {
            val value = raw.toInt() and 0xFF
            val char = value.toChar()
            if (char.isLetterOrDigit() && value < 128 || char in "-._~") {
                output.append(char)
            } else {
                output.append('%')
                output.append(HEX[value ushr 4])
                output.append(HEX[value and 0x0F])
            }
        }
        return output.toString()
    }

    fun encodePath(path: String): String {
        return path.split('/').joinToString("/") { encodeSegment(it) }
    }

    /**
     * Resolves an href relative to the document that contains it.
     *
     * @return archive path (decoded, no fragment) and optional decoded fragment.
     */
    fun resolve(fromDocument: String, href: String): Pair<String, String?> {
        val hashIndex = href.indexOf('#')
        val beforeHash = if (hashIndex >= 0) href.substring(0, hashIndex) else href
        val rawPath = beforeHash.substringBefore('?')
        val fragment = if (hashIndex >= 0) {
            href.substring(hashIndex + 1).takeIf { it.isNotEmpty() }?.let(::percentDecode)
        } else {
            null
        }

        val decodedPath = percentDecode(rawPath)
        val combined = when {
            decodedPath.isEmpty() -> fromDocument
            decodedPath.startsWith("/") -> decodedPath.drop(1)
            else -> {
                val directory = fromDocument.substringBeforeLast('/', "")
                if (directory.isEmpty()) decodedPath else "$directory/$decodedPath"
            }
        }
        return normalize(combined) to fragment
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
}
