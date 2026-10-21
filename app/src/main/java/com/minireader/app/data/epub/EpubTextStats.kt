package com.minireader.app.data.epub

/**
 * Counts readable text in EPUB chapters without keeping the whole book in
 * memory. CJK characters count as one word each; runs of Latin letters or
 * digits count as a single word.
 */
object EpubTextStats {

    fun countWords(archive: EpubArchive, chapters: List<EpubChapter>): Int {
        var total = 0
        for (chapter in chapters) {
            val bytes = archive.readBytes(chapter.href) ?: continue
            total += countWordsInHtml(HtmlDecoder.decode(bytes))
        }
        return total
    }

    fun countWordsInHtml(html: String): Int {
        val withoutScripts = SCRIPT_OR_STYLE.replace(COMMENT.replace(html, " "), " ")
        val text = ENTITY.replace(TAG.replace(withoutScripts, " "), " ")
        var count = 0
        var inLatinWord = false
        for (character in text) {
            when {
                isCjk(character) -> {
                    count++
                    inLatinWord = false
                }
                character.isLetterOrDigit() -> {
                    if (!inLatinWord) {
                        count++
                        inLatinWord = true
                    }
                }
                else -> inLatinWord = false
            }
        }
        return count
    }

    private fun isCjk(character: Char): Boolean {
        val code = character.code
        return code in 0x3400..0x4DBF ||
            code in 0x4E00..0x9FFF ||
            code in 0xF900..0xFAFF ||
            code in 0x3040..0x30FF ||
            code in 0xAC00..0xD7AF
    }

    private val COMMENT = Regex("(?s)<!--.*?-->")
    private val SCRIPT_OR_STYLE = Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>")
    private val TAG = Regex("(?s)<[^>]+>")
    private val ENTITY = Regex("&(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")
}
