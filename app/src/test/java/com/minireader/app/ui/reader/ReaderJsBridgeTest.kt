package com.minireader.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderJsBridgeTest {

    @Test
    fun ignoresNonFiniteRatios() {
        val received = mutableListOf<Float>()
        val bridge = ReaderJsBridge(
            scrollHandler = received::add,
            previousChapterHandler = {},
            nextChapterHandler = {},
        )

        bridge.onScroll(Double.NaN)
        bridge.onScroll(Double.POSITIVE_INFINITY)
        bridge.onScroll(Double.NEGATIVE_INFINITY)

        assertTrue(received.isEmpty())
    }

    @Test
    fun clampsFiniteRatios() {
        val received = mutableListOf<Float>()
        val bridge = ReaderJsBridge(
            scrollHandler = received::add,
            previousChapterHandler = {},
            nextChapterHandler = {},
        )

        bridge.onScroll(-0.5)
        bridge.onScroll(1.5)
        bridge.onScroll(0.25)

        assertEquals(listOf(0f, 1f, 0.25f), received)
    }
}
