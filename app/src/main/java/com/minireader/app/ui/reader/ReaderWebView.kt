package com.minireader.app.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.minireader.app.data.epub.EpubArchive
import com.minireader.app.data.epub.EpubChapter
import com.minireader.app.data.prefs.ReaderPrefs

@Composable
fun ReaderWebView(
    bookId: String,
    archive: EpubArchive,
    chapter: EpubChapter,
    chapterIndex: Int,
    chapterCount: Int,
    navigationToken: Long,
    restoreRatio: Float,
    restoreFragment: String?,
    prefs: ReaderPrefs,
    colors: ReaderColors,
    controller: EpubWebViewController,
    onScroll: (Float) -> Unit,
    onTap: () -> Unit,
    onInternalLink: (String, String?) -> Unit,
    onExternalLink: (String) -> Unit,
    onReady: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context -> controller.create(context) },
        onRelease = { controller.destroy() },
        update = { webView ->
            controller.update(
                bookId = bookId,
                archive = archive,
                chapter = chapter,
                chapterIndex = chapterIndex,
                chapterCount = chapterCount,
                navigationToken = navigationToken,
                restoreRatio = restoreRatio,
                restoreFragment = restoreFragment,
                prefs = prefs,
                colors = colors,
                onScroll = onScroll,
                onTap = onTap,
                onInternalLink = onInternalLink,
                onExternalLink = onExternalLink,
                onReady = onReady,
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter,
            )
            webView.contentDescription = chapter.title
        },
        modifier = modifier,
    )
}
