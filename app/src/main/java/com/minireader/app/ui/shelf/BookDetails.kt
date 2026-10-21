package com.minireader.app.ui.shelf

data class BookDetails(
    val language: String?,
    val wordCount: Int,
    val chapterCount: Int,
    val chapterIndex: Int,
    val chapterTitle: String?,
    val bookmarkCount: Int,
)
