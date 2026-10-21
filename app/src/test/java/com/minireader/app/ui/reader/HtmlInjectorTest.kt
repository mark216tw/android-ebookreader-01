package com.minireader.app.ui.reader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlInjectorTest {

    @Test
    fun injectsStyleAndScriptIntoHeadAndBody() {
        val html = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>第一章</title><link rel="stylesheet" href="style.css"/></head>
              <body><h1>第一章 起點</h1><p>這是一本用來測試 mini 閱讀器的示範電子書。</p></body>
            </html>
        """.trimIndent()

        val injected = HtmlInjector().inject(html, "body { color: red; }")
        File("build/injected-test.html").writeText(injected, Charsets.UTF_8)
        assertTrue(injected.contains("<style id=\"mr-style\">"))
        assertTrue(injected.contains("window.__mrCaptureExcerpt"))
        assertTrue(injected.indexOf("<script>") < injected.lastIndexOf("</body>"))
    }
}
