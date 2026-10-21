package com.minireader.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.minireader.app.data.db.BookDao
import com.minireader.app.data.db.BookEntity
import com.minireader.app.data.epub.EpubArchive
import com.minireader.app.data.epub.EpubParser
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ImportOutcome {
    data class Success(val bookId: String, val title: String) : ImportOutcome
    data class Duplicate(val title: String) : ImportOutcome
    data class Failure(val fileName: String, val reason: String) : ImportOutcome
}

class BookImporter(
    private val context: Context,
    private val bookDao: BookDao,
) {

    suspend fun import(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "未命名.epub"
        val tempFile = File(context.cacheDir, "import_${UUID.randomUUID()}.epub")
        var bookFile: File? = null
        var coverFile: File? = null
        try {
            val hash = copyAndHash(uri, tempFile)
            val duplicate = bookDao.findByHash(hash)
            if (duplicate != null) {
                tempFile.delete()
                return@withContext ImportOutcome.Duplicate(duplicate.displayTitle)
            }

            val bookId = UUID.randomUUID().toString()
            val parsed = EpubArchive(tempFile).use { archive ->
                EpubParser().parse(archive)
            }

            val booksDirectory = File(context.filesDir, "books").apply { mkdirs() }
            val coversDirectory = File(context.filesDir, "covers").apply { mkdirs() }
            coverFile = File(coversDirectory, "$bookId.jpg")
            val coverPath = if (parsed.coverEntryPath != null) {
                extractCover(archivePath = tempFile, entryPath = parsed.coverEntryPath, output = coverFile)
            } else {
                null
            }

            val target = File(booksDirectory, "$bookId.epub")
            bookFile = target
            if (!tempFile.renameTo(target)) {
                tempFile.copyTo(target, overwrite = true)
                tempFile.delete()
            }

            val now = System.currentTimeMillis()
            val title = parsed.title.ifBlank {
                displayName.substringBeforeLast('.').ifBlank { "未命名書籍" }
            }
            val entity = BookEntity(
                id = bookId,
                displayTitle = title,
                originalTitle = parsed.title,
                author = parsed.author,
                coverPath = coverPath,
                filePath = target.absolutePath,
                fileHash = hash,
                fileSize = target.length(),
                addedAt = now,
                lastOpenedAt = null,
                progressHref = null,
                progressRatio = 0f,
                progressFraction = 0f,
                updatedAt = now,
            )
            bookDao.insert(entity)
            ImportOutcome.Success(bookId, title)
        } catch (exception: Exception) {
            tempFile.delete()
            bookFile?.delete()
            coverFile?.delete()
            ImportOutcome.Failure(
                fileName = displayName,
                reason = exception.message ?: "無法匯入此檔案",
            )
        }
    }

    private fun copyAndHash(uri: Uri, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("無法讀取檔案")
        input.use { source ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Opens the EPUB again for cover extraction so the caller does not need to
     * keep the archive alive after parsing.
     */
    private fun extractCover(archivePath: File, entryPath: String, output: File): String? {
        return try {
            EpubArchive(archivePath).use { archive ->
                val bytes = archive.readBytes(entryPath) ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                var sampleSize = 1
                while (bounds.outWidth / sampleSize > 900 || bounds.outHeight / sampleSize > 1400) {
                    sampleSize *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: return null
                try {
                    FileOutputStream(output).use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 86, stream)
                    }
                    output.absolutePath
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (_: Exception) {
            output.delete()
            null
        } catch (_: OutOfMemoryError) {
            output.delete()
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            } ?: uri.lastPathSegment?.substringAfterLast('/')
        } catch (_: Exception) {
            uri.lastPathSegment?.substringAfterLast('/')
        }
    }
}
