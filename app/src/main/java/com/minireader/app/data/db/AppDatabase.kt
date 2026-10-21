package com.minireader.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        TagEntity::class,
        BookTagCrossRef::class,
        BookmarkEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun tagDao(): TagDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "minireader.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
