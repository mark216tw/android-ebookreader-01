package com.minireader.app

import android.app.Application
import android.content.Context
import com.minireader.app.data.db.AppDatabase
import com.minireader.app.data.prefs.ReaderPreferences
import com.minireader.app.data.repository.BookImporter
import com.minireader.app.data.repository.BookRepository

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val preferences = ReaderPreferences(context)
    val repository = BookRepository(
        bookDao = database.bookDao(),
        tagDao = database.tagDao(),
        bookmarkDao = database.bookmarkDao(),
    )
    val importer = BookImporter(context, database.bookDao())
}

class MiniReaderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
