package com.minireader.app.data.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubTextStatsTest {

    @Test
    fun countsCjkCharactersAndLatinWords() {
        val html = """
            <html><head><style>body { color: red; }</style></head>
            <body><p>你好世界</p><p>Hello world 2026</p><script>ignore()</script></body></html>
        """.trimIndent()

        assertEquals(7, EpubTextStats.countWordsInHtml(html))
    }

    @Test
    fun ignoresEntitiesAndMarkup() {
        val html = "<p>A&amp;B&nbsp;<b>測試</b></p>"

        assertEquals(4, EpubTextStats.countWordsInHtml(html))
    }
}
