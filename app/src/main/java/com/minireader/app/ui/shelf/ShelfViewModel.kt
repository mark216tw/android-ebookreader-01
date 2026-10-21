package com.minireader.app.ui.shelf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minireader.app.MiniReaderApp
import com.minireader.app.data.db.BookEntity
import com.minireader.app.data.db.TagEntity
import com.minireader.app.data.epub.EpubArchive
import com.minireader.app.data.epub.EpubParser
import com.minireader.app.data.epub.EpubTextStats
import com.minireader.app.data.prefs.DisplayMode
import com.minireader.app.data.prefs.ReaderTheme
import com.minireader.app.data.prefs.ShelfSort
import com.minireader.app.data.repository.ImportOutcome
import com.minireader.app.data.repository.ShelfBook
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShelfUiState(
    val books: List<ShelfBook> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val sortOrder: ShelfSort = ShelfSort.ADDED_DESC,
    val query: String = "",
    val selectedTagId: String? = null,
    val loading: Boolean = true,
    val lightTheme: ReaderTheme = ReaderTheme.SEPIA,
    val darkTheme: ReaderTheme = ReaderTheme.DARK,
    val displayMode: DisplayMode = DisplayMode.SYSTEM,
)

data class ImportUiState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val success: Int = 0,
    val duplicate: Int = 0,
    val failure: Int = 0,
    val errors: List<String> = emptyList(),
)

class ShelfViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MiniReaderApp).container
    private val repository = container.repository
    private val importer = container.importer
    private val preferences = container.preferences
    private val metadataCache = container.epubMetadata
    private val wordCountCache = ConcurrentHashMap<String, Int>()

    private val query = MutableStateFlow("")
    private val selectedTagId = MutableStateFlow<String?>(null)
    private val _importState = MutableStateFlow(ImportUiState())
    private val messagesChannel = Channel<String>(Channel.BUFFERED)
    private val collator = Collator.getInstance(Locale.TRADITIONAL_CHINESE).apply {
        strength = Collator.PRIMARY
    }

    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()
    val messages = messagesChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observeTags().collect { tags ->
                val current = selectedTagId.value
                if (current != null && tags.none { it.id == current }) {
                    selectedTagId.value = null
                }
            }
        }
    }

    val uiState: StateFlow<ShelfUiState> = combine(
        repository.observeShelfBooks(),
        repository.observeTags(),
        preferences.data,
        query,
        selectedTagId,
    ) { books: List<ShelfBook>, tags: List<TagEntity>, prefs, currentQuery, tagId ->
        val trimmedQuery = currentQuery.trim()
        var filtered = books
        if (trimmedQuery.isNotEmpty()) {
            filtered = filtered.filter { shelfBook ->
                val book = shelfBook.book
                book.displayTitle.contains(trimmedQuery, ignoreCase = true) ||
                    book.originalTitle.contains(trimmedQuery, ignoreCase = true) ||
                    book.author?.contains(trimmedQuery, ignoreCase = true) == true ||
                    shelfBook.tags.any { it.name.contains(trimmedQuery, ignoreCase = true) }
            }
        }
        if (tagId != null) {
            filtered = filtered.filter { shelfBook -> shelfBook.tags.any { it.id == tagId } }
        }

        val sorted = when (prefs.sortOrder) {
            ShelfSort.ADDED_DESC -> filtered.sortedByDescending { it.book.addedAt }
            ShelfSort.LAST_READ_DESC -> filtered.sortedWith(
                compareByDescending<ShelfBook> { it.book.lastOpenedAt ?: Long.MIN_VALUE }
                    .thenByDescending { it.book.addedAt },
            )
            ShelfSort.TITLE_ASC -> filtered.sortedWith { first, second ->
                collator.compare(first.book.displayTitle, second.book.displayTitle)
            }
            ShelfSort.AUTHOR_ASC -> filtered.sortedWith(
                compareBy<ShelfBook> { it.book.author.isNullOrBlank() }
                    .thenComparator { first, second ->
                        collator.compare(first.book.author.orEmpty(), second.book.author.orEmpty())
                    }
                    .thenComparator { first, second ->
                        collator.compare(first.book.displayTitle, second.book.displayTitle)
                    },
            )
            ShelfSort.PROGRESS_DESC -> filtered.sortedByDescending { it.book.progressFraction }
        }

        ShelfUiState(
            books = sorted,
            tags = tags,
            sortOrder = prefs.sortOrder,
            query = currentQuery,
            selectedTagId = tagId,
            loading = false,
            lightTheme = prefs.theme,
            darkTheme = prefs.darkTheme,
            displayMode = prefs.displayMode,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShelfUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun selectTag(tagId: String?) {
        selectedTagId.value = tagId
    }

    fun setSortOrder(sort: ShelfSort) {
        viewModelScope.launch {
            preferences.update { it.copy(sortOrder = sort) }
        }
    }

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (_importState.value.running) return
        val accepted = if (uris.size > MAX_IMPORT_BATCH) {
            messagesChannel.trySend(
                "一次最多匯入 $MAX_IMPORT_BATCH 本，已忽略其餘 ${uris.size - MAX_IMPORT_BATCH} 本",
            )
            uris.take(MAX_IMPORT_BATCH)
        } else {
            uris
        }
        viewModelScope.launch {
            _importState.value = ImportUiState(
                running = true,
                total = accepted.size,
            )
            var success = 0
            var duplicate = 0
            val errors = mutableListOf<String>()
            accepted.forEachIndexed { index, uri ->
                when (val result = importer.import(uri)) {
                    is ImportOutcome.Success -> success++
                    is ImportOutcome.Duplicate -> duplicate++
                    is ImportOutcome.Failure -> errors += "${result.fileName}：${result.reason}"
                }
                _importState.value = ImportUiState(
                    running = true,
                    total = accepted.size,
                    processed = index + 1,
                    success = success,
                    duplicate = duplicate,
                    failure = errors.size,
                    errors = errors.toList(),
                )
            }
            _importState.value = ImportUiState(
                running = false,
                finished = true,
                processed = accepted.size,
                total = accepted.size,
                success = success,
                duplicate = duplicate,
                failure = errors.size,
                errors = errors.toList(),
            )

            val parts = mutableListOf<String>()
            if (success > 0) parts += "成功 $success 本"
            if (duplicate > 0) parts += "重複 $duplicate 本"
            if (errors.isNotEmpty()) parts += "失敗 ${errors.size} 本"
            if (parts.isEmpty()) parts += "沒有匯入任何檔案"
            messagesChannel.send("匯入完成：${parts.joinToString("、")}")
        }
    }

    fun dismissImportResult() {
        _importState.value = ImportUiState()
    }

    fun renameBook(bookId: String, title: String) {
        viewModelScope.launch {
            repository.renameBook(bookId, title)
        }
    }

    fun deleteBook(shelfBook: ShelfBook) {
        viewModelScope.launch {
            repository.deleteBook(shelfBook.book)
            messagesChannel.send("已刪除「${shelfBook.book.displayTitle}」")
        }
    }

    fun loadTagIds(bookId: String, onLoaded: (Set<String>) -> Unit) {
        viewModelScope.launch {
            onLoaded(repository.tagIdsForBook(bookId))
        }
    }

    fun loadDetails(book: BookEntity, onLoaded: (BookDetails?) -> Unit) {
        viewModelScope.launch {
            val details = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(book.filePath)
                    if (!file.exists()) return@runCatching null
                    coroutineScope {
                        val bookmarkCount = async { repository.bookmarkCount(book.id) }
                        val cachedBook = metadataCache.get(book.fileHash)
                        val cachedWordCount = wordCountCache[book.fileHash]
                        val (parsed, wordCount) =
                            if (cachedBook != null && cachedWordCount != null) {
                                cachedBook to cachedWordCount
                            } else {
                                EpubArchive(file).use { archive ->
                                    val info = cachedBook
                                        ?: EpubParser().parse(archive).also {
                                            metadataCache.put(book.fileHash, it)
                                        }
                                    val count = cachedWordCount
                                        ?: EpubTextStats.countWords(archive, info.chapters).also {
                                            wordCountCache[book.fileHash] = it
                                        }
                                    info to count
                                }
                            }
                        val chapterIndex = parsed.chapters
                            .indexOfFirst { it.href == book.progressHref }
                        BookDetails(
                            language = parsed.language,
                            wordCount = wordCount,
                            chapterCount = parsed.chapters.size,
                            chapterIndex = chapterIndex,
                            chapterTitle = parsed.chapters.getOrNull(chapterIndex)?.title,
                            bookmarkCount = bookmarkCount.await(),
                        )
                    }
                }.getOrNull()
            }
            onLoaded(details)
        }
    }

    fun setTags(bookId: String, tagIds: Set<String>) {
        viewModelScope.launch {
            repository.setBookTags(bookId, tagIds)
        }
    }

    fun createTag(name: String, color: Long) {
        viewModelScope.launch {
            runCatching { repository.createTag(name, color) }
                .onFailure { messagesChannel.send(it.message ?: "無法建立標籤") }
        }
    }

    fun updateTag(tag: TagEntity, name: String, color: Long) {
        viewModelScope.launch {
            repository.updateTag(tag, name, color)
                .onFailure { messagesChannel.send(it.message ?: "無法更新標籤") }
        }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }

    private companion object {
        const val MAX_IMPORT_BATCH = 20
    }
}
