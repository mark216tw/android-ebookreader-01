package com.minireader.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE fileHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    @Query("UPDATE books SET displayTitle = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, now: Long)

    @Query("UPDATE books SET lastOpenedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun markOpened(id: String, now: Long)

    @Query(
        """
        UPDATE books
        SET progressHref = :href,
            progressRatio = :ratio,
            progressFraction = :fraction,
            lastOpenedAt = :now,
            updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun saveProgress(
        id: String,
        href: String,
        ratio: Float,
        fraction: Float,
        now: Long,
    )

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM book_tags")
    fun observeAllCrossRefs(): Flow<List<BookTagCrossRef>>
}

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM book_tags WHERE bookId = :bookId")
    suspend fun clearBookTags(bookId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<BookTagCrossRef>)

    @Query("SELECT tagId FROM book_tags WHERE bookId = :bookId")
    suspend fun tagIdsForBook(bookId: String): List<String>

    @Transaction
    suspend fun setBookTags(bookId: String, tagIds: Collection<String>) {
        clearBookTags(bookId)
        if (tagIds.isNotEmpty()) {
            insertCrossRefs(tagIds.map { BookTagCrossRef(bookId, it) })
        }
    }
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE bookId = :bookId")
    suspend fun countForBook(bookId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}
