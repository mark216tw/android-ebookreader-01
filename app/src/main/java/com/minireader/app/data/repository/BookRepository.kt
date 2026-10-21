package com.minireader.app.data.repository

import com.minireader.app.data.db.BookDao
import com.minireader.app.data.db.BookEntity
import com.minireader.app.data.db.BookmarkDao
import com.minireader.app.data.db.BookmarkEntity
import com.minireader.app.data.db.TagDao
import com.minireader.app.data.db.TagEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

data class ShelfBook(
    val book: BookEntity,
    val tags: List<TagEntity>,
)

class BookRepository(
    private val bookDao: BookDao,
    private val tagDao: TagDao,
    private val bookmarkDao: BookmarkDao,
) {

    fun observeShelfBooks(): Flow<List<ShelfBook>> {
        return combine(
            bookDao.observeAll(),
            bookDao.observeAllCrossRefs(),
            tagDao.observeAll(),
        ) { books, crossRefs, tags ->
            val tagsById = tags.associateBy { it.id }
            val tagIdsByBook = crossRefs.groupBy({ it.bookId }, { it.tagId })
            books.map { book ->
                val bookTags = tagIdsByBook[book.id]
                    .orEmpty()
                    .mapNotNull(tagsById::get)
                    .sortedBy { it.name }
                ShelfBook(book, bookTags)
            }
        }
    }

    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    fun observeBook(bookId: String): Flow<BookEntity?> = bookDao.observeById(bookId)

    suspend fun getBook(bookId: String): BookEntity? = bookDao.getById(bookId)

    suspend fun renameBook(bookId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        bookDao.rename(bookId, trimmed, System.currentTimeMillis())
    }

    suspend fun deleteBook(book: BookEntity) {
        bookDao.deleteById(book.id)
        withContext(Dispatchers.IO) {
            runCatching { File(book.filePath).delete() }
            book.coverPath?.let { path -> runCatching { File(path).delete() } }
        }
    }

    suspend fun markOpened(bookId: String) {
        bookDao.markOpened(bookId, System.currentTimeMillis())
    }

    suspend fun saveProgress(bookId: String, href: String, ratio: Float, fraction: Float) {
        bookDao.saveProgress(
            id = bookId,
            href = href,
            ratio = ratio.coerceIn(0f, 1f),
            fraction = fraction.coerceIn(0f, 1f),
            now = System.currentTimeMillis(),
        )
    }

    suspend fun setBookTags(bookId: String, tagIds: Collection<String>) {
        tagDao.setBookTags(bookId, tagIds)
    }

    suspend fun tagIdsForBook(bookId: String): Set<String> {
        return tagDao.tagIdsForBook(bookId).toSet()
    }

    suspend fun createTag(name: String, color: Long): TagEntity {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "標籤名稱不能為空" }
        tagDao.findByName(trimmed)?.let { return it }
        val tag = TagEntity(id = UUID.randomUUID().toString(), name = trimmed, color = color)
        tagDao.upsert(tag)
        return tag
    }

    suspend fun updateTag(tag: TagEntity, name: String, color: Long): Result<TagEntity> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("標籤名稱不能為空"))
        }
        val existing = tagDao.findByName(trimmed)
        if (existing != null && existing.id != tag.id) {
            return Result.failure(IllegalArgumentException("已有同名標籤"))
        }
        val updated = tag.copy(name = trimmed, color = color)
        tagDao.upsert(updated)
        return Result.success(updated)
    }

    suspend fun deleteTag(tag: TagEntity) {
        tagDao.deleteById(tag.id)
    }

    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>> {
        return bookmarkDao.observeForBook(bookId)
    }

    suspend fun bookmarkCount(bookId: String): Int = bookmarkDao.countForBook(bookId)

    suspend fun addBookmark(
        bookId: String,
        chapterHref: String,
        chapterTitle: String,
        scrollRatio: Float,
        excerpt: String,
        note: String?,
    ): BookmarkEntity {
        val bookmark = BookmarkEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            chapterHref = chapterHref,
            chapterTitle = chapterTitle,
            scrollRatio = scrollRatio.coerceIn(0f, 1f),
            excerpt = excerpt.trim(),
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = System.currentTimeMillis(),
        )
        bookmarkDao.insert(bookmark)
        return bookmark
    }

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        bookmarkDao.delete(bookmark)
    }
}
