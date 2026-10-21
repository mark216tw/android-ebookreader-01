package com.minireader.app.ui.reader

import android.content.Intent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minireader.app.MiniReaderApp
import com.minireader.app.ui.system.SystemBarAppearance
import com.minireader.app.ui.system.findActivity
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as MiniReaderApp
    val viewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModel.factory(application, bookId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val effectiveTheme = state.prefs.effectiveTheme(isSystemInDarkTheme())
    val colors = readerColors(effectiveTheme)

    val controller = remember { EpubWebViewController() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var externalUrl by remember { mutableStateOf<String?>(null) }
    var bookmarkExcerpt by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.persistNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.persistNow()
        }
    }

    DisposableEffect(state.prefs.keepScreenOn) {
        val window = activity?.window
        if (state.prefs.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (state.prefs.keepScreenOn) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    SystemBarAppearance(
        backgroundColor = colors.background,
        darkIcons = !colors.isDark,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }

            state.error != null -> {
                ReaderError(
                    message = state.error.orEmpty(),
                    colors = colors,
                    onBack = onBack,
                )
            }

            else -> {
                val chapter = state.chapters.getOrNull(state.chapterIndex)
                val archive = state.archive
                if (chapter == null || archive == null) {
                    ReaderError(
                        message = "找不到可閱讀的章節",
                        colors = colors,
                        onBack = onBack,
                    )
                } else {
                    ReaderWebView(
                        bookId = bookId,
                        archive = archive,
                        chapter = chapter,
                        chapterIndex = state.chapterIndex,
                        chapterCount = state.chapters.size,
                        navigationToken = state.navigationToken,
                        restoreRatio = state.restoreRatio,
                        restoreFragment = state.restoreFragment,
                        prefs = state.prefs.copy(theme = effectiveTheme),
                        colors = colors,
                        controller = controller,
                        onScroll = viewModel::onScroll,
                        onInternalLink = viewModel::jumpToPath,
                        onExternalLink = { externalUrl = it },
                        onReady = {},
                        onPreviousChapter = viewModel::previousChapter,
                        onNextChapter = viewModel::nextChapter,
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .padding(top = ReaderTopBarHeight),
                    )

                    ReaderTopBar(
                        title = state.book?.displayTitle ?: "mini閱讀器",
                        progress = state.bookProgress,
                        colors = colors,
                        onBack = {
                            viewModel.persistNow()
                            onBack()
                        },
                        onAddBookmark = { showBookmarks = true },
                        onOpenToc = { showToc = true },
                        onOpenSettings = { showSettings = true },
                    )

                }
            }
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        )
    }

    if (showToc) {
        ReaderTocSheet(
            toc = state.toc,
            currentChapterIndex = state.chapterIndex,
            colors = colors,
            onSelect = { entry ->
                val index = entry.chapterIndex
                if (index != null) {
                    viewModel.jumpToChapter(index, entry.fragment)
                    showToc = false
                }
            },
            onDismiss = { showToc = false },
        )
    }

    if (showBookmarks) {
        ReaderBookmarksSheet(
            bookmarks = bookmarks,
            colors = colors,
            onAddCurrent = {
                showBookmarks = false
                controller.captureExcerpt { excerpt ->
                    bookmarkExcerpt = excerpt.ifBlank { "目前閱讀位置" }
                }
            },
            onSelect = { bookmark ->
                viewModel.jumpToBookmark(bookmark)
                showBookmarks = false
            },
            onDelete = viewModel::deleteBookmark,
            onDismiss = { showBookmarks = false },
        )
    }

    if (showSettings) {
        ReaderSettingsSheet(
            prefs = state.prefs,
            onPrefsChange = viewModel::updatePrefs,
            onDismiss = { showSettings = false },
        )
    }

    bookmarkExcerpt?.let { excerpt ->
        AddBookmarkDialog(
            initialExcerpt = excerpt,
            onConfirm = { text, note ->
                viewModel.addBookmark(text, note)
                bookmarkExcerpt = null
                scope.launch {
                    snackbarHost.showSnackbar("已加入書籤")
                }
            },
            onDismiss = { bookmarkExcerpt = null },
        )
    }

    externalUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { externalUrl = null },
            title = { Text("開啟外部連結") },
            text = {
                Text(
                    url,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        externalUrl = null
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }.onFailure {
                            scope.launch {
                                snackbarHost.showSnackbar("無法開啟連結")
                            }
                        }
                    },
                ) {
                    Text("開啟")
                }
            },
            dismissButton = {
                TextButton(onClick = { externalUrl = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ReaderError(
    message: String,
    colors: ReaderColors,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = colors.secondary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "無法開啟這本書",
            style = MaterialTheme.typography.titleLarge,
            color = colors.foreground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) {
            Text("返回書架")
        }
    }
}
