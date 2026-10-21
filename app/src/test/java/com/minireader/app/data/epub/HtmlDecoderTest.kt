package com.minireader.app.data.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlDecoderTest {

    @Test
    fun decodesUtf16LeWithBom() {
        val text = "<p>你好</p>"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)

        assertEquals(text, HtmlDecoder.decode(bytes))
    }
}
