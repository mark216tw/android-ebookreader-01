package com.minireader.app.ui.reader

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import com.minireader.app.data.epub.EpubArchive
import com.minireader.app.data.epub.EpubPaths
import com.minireader.app.data.epub.MimeTypes
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Serves files out of an EPUB ZIP through the WebView asset loader. HTML
 * responses are rewritten with the current reader stylesheet.
 */
class EpubAssetHandler(
    private val archive: EpubArchive,
    private val cssProvider: () -> String,
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        val decoded = EpubPaths.percentDecode(path.substringBefore('?').trimStart('/'))
        val entry = archive.findEntry(decoded) ?: return notFound()

        return if (MimeTypes.isHtml(entry.name)) {
            val bytes = archive.readBytes(entry.name) ?: return notFound()
            val html = HtmlDecoder.decode(bytes)
            val injected = HtmlInjector().inject(html, cssProvider())
            WebResourceResponse(
                "text/html",
                "utf-8",
                200,
                "OK",
                headers(),
                ByteArrayInputStream(injected.toByteArray(StandardCharsets.UTF_8)),
            )
        } else {
            val stream = archive.open(entry.name) ?: return notFound()
            WebResourceResponse(
                MimeTypes.forPath(entry.name),
                null,
                200,
                "OK",
                headers(),
                stream,
            )
        }
    }

    private fun headers(): MutableMap<String, String> {
        return mutableMapOf(
            "Cache-Control" to "no-store",
            "Access-Control-Allow-Origin" to "*",
        )
    }

    private fun notFound(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            headers(),
            ByteArrayInputStream(ByteArray(0)),
        )
    }
}
