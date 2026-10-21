package com.minireader.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["fileHash"], unique = true)],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val displayTitle: String,
    val originalTitle: String,
    val author: String?,
    val coverPath: String?,
    val filePath: String,
    val fileHash: String,
    val fileSize: Long,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val progressHref: String?,
    val progressRatio: Float,
    val progressFraction: Float,
    val updatedAt: Long,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
)

@Entity(
    tableName = "book_tags",
    primaryKeys = ["bookId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index("tagId")],
)
data class BookTagCrossRef(
    val bookId: String,
    val tagId: String,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterHref: String,
    val chapterTitle: String,
    val scrollRatio: Float,
    val excerpt: String,
    val note: String?,
    val createdAt: Long,
)
