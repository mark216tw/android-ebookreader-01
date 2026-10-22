package com.minireader.app.data.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTextExtractorTest {

    @Test
    fun stripsMarkupAndDecodesEntities() {
        val html = """
            <html>
              <head><style>body { color: red; }</style></head>
              <body>
                <nav>目錄</nav>
                <h1>第一章</h1>
                <p>你好&amp;世界。</p>
                <script>bad()</script>
              </body>
            </html>
        """.trimIndent()

        val result = EpubTextExtractor.extract(html)

        assertEquals("第一章\n你好&世界。", result.chunks.joinToString("\n") { it.text })
        assertEquals(result.totalChars, result.chunks.last().endChar)
    }

    @Test
    fun splitsChineseSentences() {
        val sentences = EpubTextExtractor.splitSentences("第一句。第二句！第三句？")

        assertEquals(listOf("第一句。", "第二句！", "第三句？"), sentences)
    }

    @Test
    fun splitsEnglishSentences() {
        val sentences = EpubTextExtractor.splitSentences("First sentence. Second one! Third?")

        assertEquals(listOf("First sentence.", "Second one!", "Third?"), sentences)
    }

    @Test
    fun chunksLongTextUnderLimit() {
        val text = "甲".repeat(100) + "。" + "乙".repeat(100) + "。"

        val result = EpubTextExtractor.extract(text, maxChunkChars = 120)

        assertTrue(result.chunks.isNotEmpty())
        assertTrue(result.chunks.all { it.text.length <= 120 })
        assertEquals(202, result.totalChars)
    }

    @Test
    fun splitsOverlongSentenceAndKeepsOffsets() {
        val text = "字".repeat(500)

        val result = EpubTextExtractor.extract(text, maxChunkChars = 120)

        assertTrue(result.chunks.size > 1)
        result.chunks.forEach { chunk ->
            assertTrue(chunk.text.length <= 120)
            assertTrue(chunk.startChar < chunk.endChar)
        }
        assertEquals(0, result.chunks.first().startChar)
        assertEquals(500, result.chunks.last().endChar)
    }

    @Test
    fun defaultChunksStaySmall() {
        val text = (1..40).joinToString("") { "第${it}句測試內容。" }

        val result = EpubTextExtractor.extract(text)

        assertTrue(result.chunks.size > 1)
        assertTrue(
            result.chunks.all { it.text.length <= EpubTextExtractor.DEFAULT_MAX_CHUNK_CHARS },
        )
    }

    @Test
    fun chunkIndexForRatioStartsMidChapter() {
        val text = "甲".repeat(500) + "。" + "乙".repeat(500) + "。"
        val result = EpubTextExtractor.extract(text, maxChunkChars = 100)

        assertEquals(0, result.chunkIndexForRatio(0f))
        assertTrue(result.chunkIndexForRatio(0.5f) > 0)
        assertTrue(result.chunks[result.chunkIndexForRatio(0.5f)].startChar > 0)
        assertEquals(result.chunks.lastIndex, result.chunkIndexForRatio(1f))
    }

    @Test
    fun blankHtmlReturnsNoChunks() {
        val result = EpubTextExtractor.extract("<html><body>   </body></html>")

        assertTrue(result.chunks.isEmpty())
        assertEquals(0, result.totalChars)
    }
}
