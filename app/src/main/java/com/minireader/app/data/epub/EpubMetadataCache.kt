package com.minireader.app.data.epub

class EpubMetadataCache(private val maxEntries: Int = 8) {

    private val entries = object : LinkedHashMap<String, EpubBook>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EpubBook>): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(key: String): EpubBook? = entries[key]

    @Synchronized
    fun put(key: String, book: EpubBook) {
        entries[key] = book
    }
}
