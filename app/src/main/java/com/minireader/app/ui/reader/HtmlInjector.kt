package com.minireader.app.ui.reader

/**
 * Injects the reader stylesheet and the small JavaScript bridge used for
 * scroll reporting, restoring positions and creating bookmark excerpts.
 *
 * Scripts and event handlers from the book itself are stripped. EPUB content
 * is not trusted HTML, so external navigation is blocked in the WebView client
 * and inline JavaScript never reaches the page.
 */
class HtmlInjector {

    fun inject(html: String, css: String): String {
        val safeHtml = stripDangerousContent(html)
        val headContent = buildString {
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, ")
            append("maximum-scale=1.0, user-scalable=no\">")
            append("<style id=\"mr-style\">")
            append(css)
            append("</style>")
        }

        val headMatch = HEAD_OPEN.find(safeHtml)
        val headClose = HEAD_CLOSE.find(safeHtml)
        val withHead = when {
            headClose != null -> {
                val start = headClose.range.first
                safeHtml.substring(0, start) + headContent + safeHtml.substring(start)
            }
            headMatch != null -> {
                val end = headMatch.range.last + 1
                safeHtml.substring(0, end) + headContent + safeHtml.substring(end)
            }
            else -> headContent + safeHtml
        }

        val script = "<script>" + ReaderStyle.javascript() + "</script>"
        val bodyMatch = BODY_CLOSE.find(withHead)
        return if (bodyMatch != null) {
            val start = bodyMatch.range.first
            withHead.substring(0, start) + script + withHead.substring(start)
        } else {
            withHead + script
        }
    }

    private fun stripDangerousContent(html: String): String {
        var result = html
        result = SCRIPT.replace(result, "")
        result = SELF_CLOSING_SCRIPT.replace(result, "")
        result = IFRAME.replace(result, "")
        result = OBJECT.replace(result, "")
        result = EMBED.replace(result, "")
        result = BASE.replace(result, "")
        result = META_REFRESH.replace(result, "")
        result = META_CSP.replace(result, "")
        result = EVENT_HANDLER.replace(result, "")
        result = JAVASCRIPT_URL.replace(result) { match ->
            match.groupValues[1] + "=\"#\""
        }
        return result
    }

    private companion object {
        val HEAD_OPEN = Regex("(?i)<head(?:\\s[^>]*)?>")
        val HEAD_CLOSE = Regex("(?i)</head\\s*>")
        val BODY_CLOSE = Regex("(?i)</body\\s*>")
        val SCRIPT = Regex("(?is)<script\\b[^>]*>.*?</script\\s*>")
        val SELF_CLOSING_SCRIPT = Regex("(?i)<script\\b[^>]*/\\s*>")
        val IFRAME = Regex("(?is)<iframe\\b[^>]*>.*?</iframe\\s*>")
        val OBJECT = Regex("(?is)<object\\b[^>]*>.*?</object\\s*>")
        val EMBED = Regex("(?i)<embed\\b[^>]*>")
        val BASE = Regex("(?i)<base\\b[^>]*>")
        val META_REFRESH = Regex("(?i)<meta\\b[^>]*http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*>")
        val META_CSP = Regex(
            "(?i)<meta\\b[^>]*http-equiv\\s*=\\s*[\"']?content-security-policy[\"']?[^>]*>",
        )
        val EVENT_HANDLER = Regex(
            "(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)",
        )
        val JAVASCRIPT_URL = Regex(
            "(?i)(href|src)\\s*=\\s*(\"\\s*javascript:[^\"]*\"|'\\s*javascript:[^']*')",
        )
    }
}
