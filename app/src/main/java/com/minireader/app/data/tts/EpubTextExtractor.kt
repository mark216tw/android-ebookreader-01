package com.minireader.app.data.tts

/**
 * Turns EPUB chapter HTML into plain text chunks suitable for TextToSpeech.
 * The output keeps character offsets so playback progress can be mapped back
 * to an approximate position inside the chapter.
 */
object EpubTextExtractor {

    data class Chunk(
        val text: String,
        val startChar: Int,
        val endChar: Int,
    )

    data class ChapterText(
        val chunks: List<Chunk>,
        val totalChars: Int,
    ) {
        fun chunkIndexForRatio(ratio: Float): Int {
            if (chunks.isEmpty()) return 0
            val target = (totalChars * ratio.coerceIn(0f, 1f)).toInt()
            val index = chunks.indexOfFirst { it.endChar > target }
            return if (index >= 0) index else chunks.lastIndex
        }
    }

    fun extract(html: String, maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS): ChapterText {
        require(maxChunkChars > 0) { "maxChunkChars must be positive" }
        val text = htmlToText(html)
        if (text.isBlank()) return ChapterText(emptyList(), 0)

        val ranges = sentenceRanges(text)
        val chunks = mutableListOf<Chunk>()
        var chunkStart = -1
        var chunkEnd = -1

        fun flush() {
            if (chunkStart >= 0 && chunkEnd > chunkStart) {
                var start = chunkStart
                var end = chunkEnd
                while (start < end && text[start].isWhitespace()) start++
                while (end > start && text[end - 1].isWhitespace()) end--
                if (end > start) {
                    chunks += Chunk(
                        text = text.substring(start, end),
                        startChar = start,
                        endChar = end,
                    )
                }
            }
            chunkStart = -1
            chunkEnd = -1
        }

        for (range in ranges) {
            var start = range.first
            val end = range.last + 1
            while (start < end) {
                if (chunkStart < 0) {
                    chunkStart = start
                    chunkEnd = start
                }
                val used = chunkEnd - chunkStart
                val remaining = end - start
                if (used >= maxChunkChars) {
                    flush()
                    continue
                }
                if (used > 0 && used + remaining > maxChunkChars) {
                    flush()
                    continue
                }
                val take = minOf(remaining, maxChunkChars - used)
                chunkEnd = start + take
                start += take
                if (start < end) flush()
            }
        }
        flush()

        return ChapterText(chunks = chunks, totalChars = text.length)
    }

    fun splitSentences(text: String): List<String> {
        return sentenceRanges(text).mapNotNull { range ->
            text.substring(range.first, range.last + 1).trim().takeIf { it.isNotEmpty() }
        }
    }

    private fun sentenceRanges(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = 0
        var index = 0

        fun addRange(from: Int, toExclusive: Int) {
            var fromIndex = from
            var toIndex = toExclusive
            while (fromIndex < toIndex && text[fromIndex].isWhitespace()) fromIndex++
            while (toIndex > fromIndex && text[toIndex - 1].isWhitespace()) toIndex--
            if (toIndex > fromIndex) ranges += fromIndex until toIndex
        }

        while (index < text.length) {
            val character = text[index]
            val boundary = when (character) {
                '\n' -> true
                '。', '！', '？', '；', '…', '!', '?', ';' -> true
                '.' -> {
                    val next = text.getOrNull(index + 1)
                    val previous = text.getOrNull(index - 1)
                    (next == null || next.isWhitespace()) && (previous == null || !previous.isDigit())
                }
                else -> false
            }
            if (boundary) {
                var end = index + 1
                while (end < text.length && isTrailingPunctuation(text[end])) end++
                addRange(start, end)
                start = end
                index = end
            } else {
                index++
            }
        }
        addRange(start, text.length)
        return ranges
    }

    private fun isTrailingPunctuation(character: Char): Boolean {
        return character == '…' ||
            character == '”' ||
            character == '’' ||
            character == '」' ||
            character == '』' ||
            character == '》' ||
            character == '）' ||
            character == ')' ||
            character == ']' ||
            character == '}' ||
            character == '"' ||
            character == '\''
    }

    private fun htmlToText(html: String): String {
        var result = html
        result = COMMENT.replace(result, " ")
        result = SCRIPT_STYLE.replace(result, " ")
        result = NAV.replace(result, " ")
        result = RUBY_TEXT.replace(result, " ")
        result = BLOCK_CLOSE.replace(result, "\n")
        result = BR.replace(result, "\n")
        result = TAG.replace(result, " ")
        result = ENTITY.replace(result) { match -> decodeEntity(match.value) }
        result = result.replace('\u00A0', ' ')
        result = SPACES.replace(result, " ")
        result = SPACES_AROUND_NEWLINE.replace(result, "\n")
        result = MULTIPLE_NEWLINES.replace(result, "\n")
        return result.trim()
    }

    private fun decodeEntity(raw: String): String {
        val body = raw.substring(1, raw.length - 1)
        if (body.startsWith("#x", ignoreCase = true)) {
            val codePoint = body.substring(2).toIntOrNull(16) ?: return " "
            return codePointToString(codePoint)
        }
        if (body.startsWith("#")) {
            val codePoint = body.substring(1).toIntOrNull() ?: return " "
            return codePointToString(codePoint)
        }
        return when (body.lowercase()) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            "hellip" -> "…"
            "mdash" -> "—"
            "ndash" -> "–"
            "ldquo" -> "“"
            "rdquo" -> "”"
            "lsquo" -> "‘"
            "rsquo" -> "’"
            "middot" -> "·"
            "times" -> "×"
            "copy" -> "©"
            "reg" -> "®"
            "deg" -> "°"
            "plusmn" -> "±"
            "laquo" -> "«"
            "raquo" -> "»"
            else -> " "
        }
    }

    private fun codePointToString(codePoint: Int): String {
        if (codePoint <= 0 || codePoint > 0x10FFFF) return " "
        return String(Character.toChars(codePoint))
    }

    const val DEFAULT_MAX_CHUNK_CHARS = 200

    private val COMMENT = Regex("(?s)<!--.*?-->")
    private val SCRIPT_STYLE = Regex("(?is)<(script|style|noscript)\\b[^>]*>.*?</\\1\\s*>")
    private val NAV = Regex("(?is)<nav\\b[^>]*>.*?</nav\\s*>")
    private val RUBY_TEXT = Regex("(?is)<(rt|rp)\\b[^>]*>.*?</\\1\\s*>")
    private val BLOCK_CLOSE = Regex(
        "(?i)</(p|div|section|article|li|tr|td|th|h[1-6]|blockquote|pre|figure|figcaption|aside|header|footer|main)\\s*>",
    )
    private val BR = Regex("(?i)<br\\b[^>]*>")
    private val TAG = Regex("(?s)<[^>]+>")
    private val ENTITY = Regex("&(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")
    private val SPACES = Regex("[ \\t\\x0B\\f\\r]+")
    private val SPACES_AROUND_NEWLINE = Regex(" *\\n *")
    private val MULTIPLE_NEWLINES = Regex("\\n{2,}")
}
