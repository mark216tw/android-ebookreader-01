package com.minireader.app.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.toArgb
import androidx.webkit.WebViewAssetLoader
import com.minireader.app.data.epub.EpubArchive
import com.minireader.app.data.epub.EpubChapter
import com.minireader.app.data.epub.EpubPaths
import com.minireader.app.data.prefs.ReaderPrefs
import java.io.ByteArrayInputStream
import org.json.JSONObject
import org.json.JSONTokener

class EpubWebViewController {

    private var webView: WebView? = null
    private var assetLoader: WebViewAssetLoader? = null
    private var archive: EpubArchive? = null
    private var bookId: String? = null
    private var loadedHref: String? = null
    private var pendingUrl: String? = null
    private var pendingRatio = 0f
    private var pendingFragment: String? = null
    private var lastNavigationToken = Long.MIN_VALUE
    private var chapterIndex = 0
    private var chapterCount = 0
    private var lastNavSignature = ""
    private var prefs = ReaderPrefs()
    private var colors = readerColors(prefs.theme)
    @Volatile private var css = ReaderStyle.css(prefs, colors)
    private var quotedCss = JSONObject.quote(css)

    private var onScroll: (Float) -> Unit = {}
    private var onInternalLink: (String, String?) -> Unit = { _, _ -> }
    private var onExternalLink: (String) -> Unit = {}
    private var onReady: () -> Unit = {}
    private var onPreviousChapter: () -> Unit = {}
    private var onNextChapter: () -> Unit = {}

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context): WebView {
        val view = WebView(context)
        view.setBackgroundColor(Color.TRANSPARENT)
        view.overScrollMode = WebView.OVER_SCROLL_NEVER
        view.isVerticalScrollBarEnabled = false

        with(view.settings) {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(false)
            textZoom = 100
        }

        view.addJavascriptInterface(
            ReaderJsBridge(
                scrollHandler = { ratio ->
                    view.post {
                        onScroll(ratio)
                    }
                },
                previousChapterHandler = {
                    view.post { onPreviousChapter() }
                },
                nextChapterHandler = {
                    view.post { onNextChapter() }
                },
            ),
            "Android",
        )

        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val url = request.url
                if (url.scheme == "data" || url.scheme == "blob") return null
                if (url.host != DOMAIN) return blocked()
                return assetLoader?.shouldInterceptRequest(url) ?: blocked()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                return handleNavigation(request.url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val expected = pendingUrl ?: return
                if (url.substringBefore('#') != expected) return
                updateChapterNav(view)
                val fragment = pendingFragment?.let { JSONObject.quote(it) } ?: "null"
                val script = "window.__mrRestore && window.__mrRestore(${pendingRatio}, $fragment);"
                view.evaluateJavascript(script, null)
                onReady()
            }
        }

        webView = view
        return view
    }

    fun update(
        bookId: String,
        archive: EpubArchive,
        chapter: EpubChapter,
        chapterIndex: Int,
        chapterCount: Int,
        navigationToken: Long,
        restoreRatio: Float,
        restoreFragment: String?,
        prefs: ReaderPrefs,
        colors: ReaderColors,
        onScroll: (Float) -> Unit,
        onInternalLink: (String, String?) -> Unit,
        onExternalLink: (String) -> Unit,
        onReady: () -> Unit,
        onPreviousChapter: () -> Unit,
        onNextChapter: () -> Unit,
    ) {
        val view = webView ?: return
        this.onScroll = onScroll
        this.onInternalLink = onInternalLink
        this.onExternalLink = onExternalLink
        this.onReady = onReady
        this.onPreviousChapter = onPreviousChapter
        this.onNextChapter = onNextChapter
        this.chapterIndex = chapterIndex
        this.chapterCount = chapterCount

        val styleChanged = this.prefs != prefs || this.colors != colors
        if (styleChanged) {
            this.prefs = prefs
            this.colors = colors
            css = ReaderStyle.css(prefs, colors)
            quotedCss = JSONObject.quote(css)
        }
        view.setBackgroundColor(colors.background.toArgb())

        if (this.bookId != bookId || this.archive !== archive) {
            this.bookId = bookId
            this.archive = archive
            this.loadedHref = null
            assetLoader = WebViewAssetLoader.Builder()
                .setDomain(DOMAIN)
                .addPathHandler(
                    "/epub/$bookId/",
                    EpubAssetHandler(archive) { css },
                )
                .build()
        }

        if (loadedHref != chapter.href) {
            loadedHref = chapter.href
            pendingRatio = restoreRatio
            pendingFragment = restoreFragment
            lastNavigationToken = navigationToken
            val url = chapterUrl(bookId, chapter.href)
            pendingUrl = url
            view.loadUrl(url)
            return
        }

        if (lastNavSignature != navSignature()) {
            updateChapterNav(view)
        }

        if (styleChanged) {
            applyStyle(view)
        }

        if (navigationToken != lastNavigationToken) {
            lastNavigationToken = navigationToken
            if (restoreFragment != null) {
                scrollToFragment(restoreFragment)
            } else {
                view.evaluateJavascript(
                    "window.__mrRestore && window.__mrRestore(${restoreRatio}, null);",
                    null,
                )
            }
        }
    }

    fun captureExcerpt(callback: (String) -> Unit) {
        val view = webView
        if (view == null) {
            callback("")
            return
        }
        view.evaluateJavascript(
            "(window.__mrCaptureExcerpt ? window.__mrCaptureExcerpt() : '')",
        ) { result ->
            val value = runCatching { JSONTokener(result).nextValue() }.getOrNull()
            callback(value as? String ?: "")
        }
    }

    fun scrollToFragment(fragment: String) {
        webView?.evaluateJavascript(
            "window.__mrScrollToFragment && window.__mrScrollToFragment(${JSONObject.quote(fragment)});",
            null,
        )
    }

    fun scrollToRatio(ratio: Float) {
        val safeRatio = ratio.coerceIn(0f, 1f)
        webView?.evaluateJavascript(
            "window.__mrScrollToRatio && window.__mrScrollToRatio($safeRatio);",
            null,
        )
    }

    fun destroy() {
        val view = webView ?: return
        webView = null
        runCatching { view.removeJavascriptInterface("Android") }
        runCatching { view.stopLoading() }
        runCatching { view.loadUrl("about:blank") }
        runCatching { view.destroy() }
    }

    private fun navSignature(): String = "$chapterIndex/$chapterCount"

    private fun updateChapterNav(view: WebView) {
        lastNavSignature = navSignature()
        view.evaluateJavascript(
            "window.__mrSetChapterNav && window.__mrSetChapterNav($chapterIndex, $chapterCount);",
            null,
        )
    }

    private fun applyStyle(view: WebView) {
        view.evaluateJavascript(
            "window.__mrApplyStyle && window.__mrApplyStyle($quotedCss);",
            null,
        )
    }

    private fun handleNavigation(uri: Uri): Boolean {
        if (uri.host == DOMAIN) {
            val currentBookId = bookId ?: return true
            val path = uri.path ?: return true
            val prefix = "/epub/$currentBookId/"
            if (path.startsWith(prefix)) {
                val target = EpubPaths.normalize(
                    EpubPaths.percentDecode(path.removePrefix(prefix)),
                )
                val fragment = uri.fragment?.let(EpubPaths::percentDecode)
                if (target == loadedHref) {
                    if (fragment != null) scrollToFragment(fragment)
                } else {
                    onInternalLink(target, fragment)
                }
            }
            return true
        }

        when (uri.scheme?.lowercase()) {
            "http", "https" -> onExternalLink(uri.toString())
        }
        return true
    }

    private fun chapterUrl(bookId: String, href: String): String {
        return "https://$DOMAIN/epub/$bookId/${EpubPaths.encodePath(href)}"
    }

    private fun blocked(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            403,
            "Forbidden",
            mutableMapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    companion object {
        const val DOMAIN = "minireader.local"
    }
}

