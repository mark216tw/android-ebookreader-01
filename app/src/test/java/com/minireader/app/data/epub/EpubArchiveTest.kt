package com.minireader.app.data.epub

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubArchiveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun readsEntryWithinLimit() {
        val file = createArchive(mapOf("chapter.xhtml" to "<html>hello</html>"))

        EpubArchive(file, maxEntryBytes = 1024).use { archive ->
            val bytes = archive.readBytes("chapter.xhtml")
            assertEquals("<html>hello</html>", bytes?.toString(Charsets.UTF_8))
        }
    }

    @Test
    fun returnsNullForOversizedEntry() {
        val file = createArchive(mapOf("chapter.xhtml" to "x".repeat(2048)))

        EpubArchive(file, maxEntryBytes = 1024).use { archive ->
            assertNull(archive.readBytes("chapter.xhtml"))
        }
    }

    private fun createArchive(entries: Map<String, String>): File {
        val file = temporaryFolder.newFile("archive.epub")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }
}
