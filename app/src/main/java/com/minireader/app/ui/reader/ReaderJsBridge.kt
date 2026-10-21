package com.minireader.app.ui.reader

import android.webkit.JavascriptInterface

class ReaderJsBridge(
    private val scrollHandler: (Float) -> Unit,
    private val previousChapterHandler: () -> Unit,
    private val nextChapterHandler: () -> Unit,
) {

    @JavascriptInterface
    fun onScroll(ratio: Double) {
        scrollHandler(ratio.toFloat().coerceIn(0f, 1f))
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
