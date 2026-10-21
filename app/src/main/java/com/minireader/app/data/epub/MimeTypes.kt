package com.minireader.app.data.epub

object MimeTypes {

    fun forPath(path: String): String {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "xhtml", "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "otf" -> "font/otf"
            "ttf" -> "font/ttf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "mp4" -> "video/mp4"
            "ogg" -> "audio/ogg"
            "xml" -> "application/xml"
            "ncx" -> "application/x-dtbncx+xml"
            "json" -> "application/json"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    fun isHtml(path: String): Boolean {
        return path.substringAfterLast('.', "").lowercase() in setOf("xhtml", "html", "htm")
    }
}
