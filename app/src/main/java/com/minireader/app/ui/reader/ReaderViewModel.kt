package com.minireader.app.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.minireader.app.MiniReaderApp
import com.minireader.app.data.db.BookEntity
import com.minireader.app.data.db.BookmarkEntity
import com.minireader.app.data.epub.EpubArchive
import com.minireader.app.data.epub.EpubChapter
import com.minireader.app.data.epub.EpubParser
import com.minireader.app.data.epub.TocEntry
import com.minireader.app.data.prefs.ReaderPrefs
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val book: BookEntity? = null,
    val archive: EpubArchive? = null,
    val chapters: List<EpubChapter> = emptyList(),
    val toc: List<TocEntry> = emptyList(),
    val chapterIndex: Int = 0,
    val navigationToken: Long = 0L,
    val restoreRatio: Float = 0f,
    val restoreFragment: String? = null,
    val bookProgress: Float = 0f,
    val prefs: ReaderPrefs = ReaderPrefs(),
)

class ReaderViewModel(
    application: Application,
    private val bookId: String,
) : AndroidViewModel(application) {

    private val container = (application as MiniReaderApp).container
    private val repository = container.repository
    private val preferences = container.preferences

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.observeBookmarks(bookId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private var archive: EpubArchive? = null
    private var currentRatio = 0f
    private var navigationToken = 0L
    private var persistJob: Job? = null
    private var lastProgressUiAt = 0L

    init {
        viewModelScope.launch {
            preferences.data.collect { prefs ->
                _uiState.value = _uiState.value.copy(prefs = prefs)
            }
        }
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            val book = repository.getBook(bookId)
            if (book == null) {
                _uiState.value = _uiState.value.copy(loading = false, error = "找不到這本書")
                return@launch
            }
            val file = File(book.filePath)
            if (!file.exists()) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    book = book,
                    error = "檔案遺失，請重新匯入這本書",
                )
                return@launch
            }

            var opened: EpubArchive? = null
            try {
                val (openedArchive, parsed) = withContext(Dispatchers.IO) {
                    val archive = EpubArchive(file)
                    try {
                        archive to EpubParser().parse(archive)
                    } catch (exception: Exception) {
                        runCatching { archive.close() }
                        throw exception
                    }
                }
                opened = openedArchive
                archive = openedArchive

                val resumeIndex = parsed.chapters
                    .indexOfFirst { it.href == book.progressHref }
                    .takeIf { it >= 0 }
                    ?: 0
                val resumeRatio = if (parsed.chapters[resumeIndex].href == book.progressHref) {
                    book.progressRatio.coerceIn(0f, 1f)
                } else {
                    0f
                }
                currentRatio = resumeRatio
                navigationToken += 1
                val progress = ((resumeIndex + resumeRatio) / parsed.chapters.size)
                    .coerceIn(0f, 1f)

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = null,
                    book = book,
                    archive = opened,
                    chapters = parsed.chapters,
                    toc = parsed.toc,
                    chapterIndex = resumeIndex,
                    navigationToken = navigationToken,
                    restoreRatio = resumeRatio,
                    restoreFragment = null,
                    bookProgress = progress,
                )
                repository.markOpened(bookId)
            } catch (exception: Exception) {
                runCatching { opened?.close() }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    book = book,
                    error = exception.message ?: "無法開啟這本書",
                )
            }
        }
    }

    fun onScroll(ratio: Float) {
        val safeRatio = ratio.coerceIn(0f, 1f)
        currentRatio = safeRatio
        val state = _uiState.value
        if (state.chapters.isEmpty()) return

        val progress = ((state.chapterIndex + safeRatio) / state.chapters.size)
            .coerceIn(0f, 1f)
        val now = System.currentTimeMillis()
        if (now - lastProgressUiAt >= 700) {
            lastProgressUiAt = now
            _uiState.value = state.copy(bookProgress = progress)
        }

        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(900)
            saveSnapshot()
        }
    }

    fun persistNow() {
        persistJob?.cancel()
        saveSnapshot()
    }

    private fun saveSnapshot() {
        val state = _uiState.value
        val chapter = state.chapters.getOrNull(state.chapterIndex) ?: return
        val ratio = currentRatio
        val fraction = ((state.chapterIndex + ratio) / state.chapters.size)
            .coerceIn(0f, 1f)
        viewModelScope.launch {
            repository.saveProgress(bookId, chapter.href, ratio, fraction)
        }
    }

    fun jumpToChapter(
        index: Int,
        fragment: String? = null,
        restoreRatio: Float = 0f,
    ) {
        val state = _uiState.value
        if (state.chapters.isEmpty()) return
        persistJob?.cancel()
        goTo(index, restoreRatio, fragment)
    }

    fun jumpToPath(path: String, fragment: String?) {
        val index = _uiState.value.chapters.indexOfFirst { it.href == path }
        if (index >= 0) {
            jumpToChapter(index, fragment)
        }
    }

    fun nextChapter() {
        jumpToChapter(_uiState.value.chapterIndex + 1)
    }

    fun previousChapter() {
        jumpToChapter(_uiState.value.chapterIndex - 1)
    }

    fun jumpToBookmark(bookmark: BookmarkEntity) {
        val index = _uiState.value.chapters.indexOfFirst { it.href == bookmark.chapterHref }
        if (index >= 0) {
            jumpToChapter(index, fragment = null, restoreRatio = bookmark.scrollRatio)
        }
    }

    fun addBookmark(excerpt: String, note: String?) {
        val state = _uiState.value
        val chapter = state.chapters.getOrNull(state.chapterIndex) ?: return
        viewModelScope.launch {
            repository.addBookmark(
                bookId = bookId,
                chapterHref = chapter.href,
                chapterTitle = chapter.title,
                scrollRatio = currentRatio,
                excerpt = excerpt.ifBlank { "目前閱讀位置" },
                note = note,
            )
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    fun updatePrefs(prefs: ReaderPrefs) {
        viewModelScope.launch {
            preferences.update { prefs }
        }
    }

    private fun goTo(index: Int, ratio: Float, fragment: String?) {
        val state = _uiState.value
        if (state.chapters.isEmpty()) return
        val safeIndex = index.coerceIn(0, state.chapters.lastIndex)
        val safeRatio = ratio.coerceIn(0f, 1f)
        currentRatio = safeRatio
        navigationToken += 1
        val progress = ((safeIndex + safeRatio) / state.chapters.size).coerceIn(0f, 1f)
        _uiState.value = state.copy(
            chapterIndex = safeIndex,
            navigationToken = navigationToken,
            restoreRatio = safeRatio,
            restoreFragment = fragment,
            bookProgress = progress,
        )
        saveSnapshot()
    }

    override fun onCleared() {
        persistJob?.cancel()
        runCatching { archive?.close() }
        archive = null
        super.onCleared()
    }

    companion object {
        fun factory(application: MiniReaderApp, bookId: String): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    ReaderViewModel(application, bookId)
                }
            }
        }
    }
}
