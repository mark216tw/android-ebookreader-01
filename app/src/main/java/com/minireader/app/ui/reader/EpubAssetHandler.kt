package com.minireader.app.ui.reader

import android.util.LruCache
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import com.minireader.app.data.epub.EpubArchive
import com.minireader.app.data.epub.EpubPaths
import com.minireader.app.data.epub.HtmlDecoder
import com.minireader.app.data.epub.MimeTypes
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry

/**
 * Serves files out of an EPUB ZIP through the WebView asset loader. HTML
 * responses are rewritten with the current reader stylesheet.
 */
class EpubAssetHandler(
    private val archive: EpubArchive,
    private val cssProvider: () -> String,
) : WebViewAssetLoader.PathHandler {

    private val injector = HtmlInjector()
    private val cache = object : LruCache<String, ByteArray>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private val htmlCache = object : LruCache<String, CachedHtml>(HTML_CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: CachedHtml): Int = value.body.size
    }
    private val cacheLock = Any()

    override fun handle(path: String): WebResourceResponse? {
        val decoded = EpubPaths.percentDecode(path.substringBefore('?').trimStart('/'))
        val entry = archive.findEntry(decoded) ?: return notFound()

        return if (MimeTypes.isHtml(entry.name)) {
            val css = cssProvider()
            val key = entry.name + '\u0000' + css
            val injected = htmlCache.get(key) ?: run {
                val bytes = archive.readBytes(entry.name) ?: return notFound()
                val html = HtmlDecoder.decode(bytes)
                val result = injector.inject(html, css)
                CachedHtml(
                    body = result.html.toByteArray(StandardCharsets.UTF_8),
                    csp = result.csp,
                ).also { htmlCache.put(key, it) }
            }
            val responseHeaders = headers(injected.body.size.toLong())
            responseHeaders["Content-Security-Policy"] = injected.csp
            WebResourceResponse(
                "text/html",
                "utf-8",
                200,
                "OK",
                responseHeaders,
                ByteArrayInputStream(injected.body),
            )
        } else {
            val cached = cachedBytes(entry)
            if (cached != null) {
                WebResourceResponse(
                    MimeTypes.forPath(entry.name),
                    null,
                    200,
                    "OK",
                    headers(cached.size.toLong()),
                    ByteArrayInputStream(cached),
                )
            } else {
                val stream = archive.open(entry.name) ?: return notFound()
                WebResourceResponse(
                    MimeTypes.forPath(entry.name),
                    null,
                    200,
                    "OK",
                    headers(entry.size.takeIf { it >= 0 }),
                    stream,
                )
            }
        }
    }

    private fun cachedBytes(entry: ZipEntry): ByteArray? {
        val key = entry.name
        cache.get(key)?.let { return it }
        if (entry.size < 0 || entry.size > MAX_CACHED_ENTRY_BYTES) return null
        synchronized(cacheLock) {
            cache.get(key)?.let { return it }
            val bytes = archive.readBytes(key) ?: return null
            if (bytes.size <= MAX_CACHED_ENTRY_BYTES) {
                cache.put(key, bytes)
            }
            return bytes
        }
    }

    private fun headers(contentLength: Long?): MutableMap<String, String> {
        val headers = mutableMapOf(
            "Cache-Control" to "no-store",
            "Access-Control-Allow-Origin" to "*",
        )
        if (contentLength != null) {
            headers["Content-Length"] = contentLength.toString()
        }
        return headers
    }

    private fun notFound(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            headers(0L),
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    private data class CachedHtml(
        val body: ByteArray,
        val csp: String,
    )

    private companion object {
        const val MAX_CACHED_ENTRY_BYTES = 2 * 1024 * 1024
        const val CACHE_SIZE_BYTES = 16 * 1024 * 1024
        const val HTML_CACHE_SIZE_BYTES = 4 * 1024 * 1024
    }
}
