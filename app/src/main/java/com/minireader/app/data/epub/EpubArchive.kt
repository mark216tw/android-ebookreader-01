package com.minireader.app.data.epub

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Read-only access to the entries of an EPUB (which is just a ZIP archive).
 * Entries are served straight from the ZIP; nothing is extracted to disk.
 */
class EpubArchive(file: File) : Closeable {

    private val zipFile = ZipFile(file)
    private val entriesByName = HashMap<String, ZipEntry>()
    private val entriesByLowerCaseName = HashMap<String, ZipEntry>()

    init {
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val normalized = EpubPaths.normalize(entry.name)
            entriesByName[normalized] = entry
            entriesByLowerCaseName.putIfAbsent(normalized.lowercase(), entry)
        }
    }

    fun findEntry(path: String): ZipEntry? {
        val normalized = EpubPaths.normalize(path)
        entriesByName[normalized]?.let { return it }
        entriesByName[EpubPaths.normalize(EpubPaths.percentDecode(path))]?.let { return it }
        return entriesByLowerCaseName[normalized.lowercase()]
    }

    fun readBytes(path: String): ByteArray? {
        val entry = findEntry(path) ?: return null
        return zipFile.getInputStream(entry).use { it.readBytes() }
    }

    fun open(path: String): InputStream? {
        val entry = findEntry(path) ?: return null
        return zipFile.getInputStream(entry)
    }

    fun size(path: String): Long = findEntry(path)?.size ?: -1L

    override fun close() {
        zipFile.close()
    }
}
