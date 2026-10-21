package com.minireader.app.data.epub

data class EpubBook(
    val title: String,
    val author: String?,
    val language: String?,
    val coverEntryPath: String?,
    val opfPath: String,
    val chapters: List<EpubChapter>,
    val toc: List<TocEntry>,
)

data class EpubChapter(
    val index: Int,
    val href: String,
    val mediaType: String,
    val title: String,
)

data class TocEntry(
    val title: String,
    val path: String,
    val fragment: String?,
    val depth: Int,
    val chapterIndex: Int?,
)

class EpubException(message: String) : Exception(message)
