package com.minireader.app.ui.reader

import android.webkit.JavascriptInterface

class ReaderJsBridge(
    private val scrollHandler: (Float) -> Unit,
    private val previousChapterHandler: () -> Unit,
    private val nextChapterHandler: () -> Unit,
) {

    @JavascriptInterface
    fun onScroll(ratio: Double) {
        if (!ratio.isFinite()) return
        val value = ratio.toFloat()
        if (!value.isFinite()) return
        scrollHandler(value.coerceIn(0f, 1f))
    }

    @JavascriptInterface
    fun onPreviousChapter() {
        previousChapterHandler()
    }

    @JavascriptInterface
    fun onNextChapter() {
        nextChapterHandler()
    }
}
