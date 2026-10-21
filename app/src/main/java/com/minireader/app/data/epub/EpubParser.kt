package com.minireader.app.data.epub

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal, dependency-free EPUB 2/3 parser.
 *
 * It reads the container, OPF package document, EPUB3 navigation document and
 * EPUB2 NCX table of contents. Rendering itself is done by a WebView.
 */
class EpubParser {

    fun parse(archive: EpubArchive): EpubBook {
        val containerBytes = archive.readBytes("META-INF/container.xml")
            ?: throw EpubException("這不是有效的 EPUB：缺少 META-INF/container.xml")

        val container = parseXml(containerBytes, "container.xml")
        val rootFile = firstElement(container, "rootfile")
            ?: throw EpubException("EPUB 結構錯誤：找不到 OPF 檔案")
        val opfPath = EpubPaths.normalize(rootFile.getAttribute("full-path"))
        if (opfPath.isBlank()) {
            throw EpubException("EPUB 結構錯誤：OPF 路徑為空")
        }

        if (archive.findEntry("META-INF/rights.xml") != null) {
            throw EpubException("此 EPUB 受 DRM 保護，無法開啟")
        }

        val opfBytes = archive.readBytes(opfPath)
            ?: throw EpubException("EPUB 結構錯誤：找不到 $opfPath")
        val opf = parseXml(opfBytes, opfPath)

        val title = firstText(opf, "title")
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "未命名書籍"
        val authors = allTexts(opf, "creator")
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("、")
            .takeIf { it.isNotEmpty() }
        val language = firstText(opf, "language")?.trim()?.takeIf { it.isNotEmpty() }

        val manifest = readManifest(opf, opfPath)
        val spineElement = firstElement(opf, "spine")
        val chapters = readSpine(opf, manifest)
        if (chapters.isEmpty()) {
            throw EpubException("此 EPUB 沒有可閱讀的章節")
        }

        val toc = buildList<TocEntry> {
            parseNav(archive, manifest)?.let { addAll(it) }
            if (isEmpty()) {
                addAll(parseNcx(archive, manifest, spineElement?.getAttribute("toc")))
            }
        }

        val chapterIndexByPath = chapters.associateBy { it.href }
        val titleByPath = LinkedHashMap<String, String>()
        toc.forEach { entry ->
            if (entry.path.isNotEmpty()) {
                titleByPath.putIfAbsent(entry.path, entry.title)
            }
        }

        val finalChapters = chapters.map { chapter ->
            chapter.copy(title = titleByPath[chapter.href] ?: "第 ${chapter.index + 1} 章")
        }
        val finalToc = toc.map { entry ->
            entry.copy(chapterIndex = chapterIndexByPath[entry.path]?.index)
        }

        return EpubBook(
            title = title,
            author = authors,
            language = language,
            coverEntryPath = findCoverEntry(opf, manifest),
            opfPath = opfPath,
            chapters = finalChapters,
            toc = finalToc,
        )
    }

    private fun readManifest(opf: Document, opfPath: String): Map<String, ManifestItem> {
        val manifest = LinkedHashMap<String, ManifestItem>()
        val items = opf.getElementsByTagNameNS("*", "item")
        for (index in 0 until items.length) {
            val element = items.item(index) as? Element ?: continue
            val id = element.getAttribute("id")
            val href = element.getAttribute("href")
            if (id.isBlank() || href.isBlank() || href.contains("://")) continue
            val (path, _) = EpubPaths.resolve(opfPath, href)
            val mediaType = element.getAttribute("media-type").orEmpty()
            val properties = element.getAttribute("properties")
                .orEmpty()
                .split(' ')
                .filter { it.isNotBlank() }
                .toSet()
            manifest[id] = ManifestItem(id, path, mediaType, properties)
        }
        return manifest
    }

    private fun readSpine(opf: Document, manifest: Map<String, ManifestItem>): List<EpubChapter> {
        val chapters = mutableListOf<EpubChapter>()
        val itemRefs = opf.getElementsByTagNameNS("*", "itemref")
        for (index in 0 until itemRefs.length) {
            val element = itemRefs.item(index) as? Element ?: continue
            val item = manifest[element.getAttribute("idref")] ?: continue
            if (!isReadableMediaType(item.mediaType)) continue
            chapters += EpubChapter(
                index = chapters.size,
                href = item.href,
                mediaType = item.mediaType,
                title = "",
            )
        }
        return chapters
    }

    private fun parseNav(
        archive: EpubArchive,
        manifest: Map<String, ManifestItem>,
    ): List<TocEntry>? {
        val navItem = manifest.values.firstOrNull { "nav" in it.properties } ?: return null
        val bytes = archive.readBytes(navItem.href) ?: return null
        val document = try {
            parseXml(bytes, navItem.href)
        } catch (_: EpubException) {
            return parseNavFallback(bytes, navItem.href)
        }

        val navs = document.getElementsByTagNameNS("*", "nav")
        val navElements = (0 until navs.length).mapNotNull { navs.item(it) as? Element }
        val tocNav = navElements.firstOrNull { element ->
            val type = element.getAttributeNS(OPS_NS, "type")
                .ifBlank { element.getAttribute("epub:type") }
            type.split(' ').contains("toc")
        } ?: navElements.firstOrNull() ?: return null

        val list = tocNav.childElements().firstOrNull { it.localNameOf() == "ol" }
            ?: tocNav.descendants("ol").firstOrNull()
            ?: return null

        val entries = mutableListOf<TocEntry>()
        walkNavList(list, 0, navItem.href, entries)
        return entries.ifEmpty { null }
    }

    /**
     * Some EPUB3 navigation documents contain HTML named entities that are not
     * valid XML (for example &nbsp;). Rather than losing the table of contents,
     * fall back to a small tolerant link extractor.
     */
    private fun parseNavFallback(bytes: ByteArray, documentPath: String): List<TocEntry>? {
        val text = String(bytes, Charsets.UTF_8)
        val navBody = NAV_TOC_BLOCK.find(text)?.groupValues?.get(1) ?: text
        val entries = mutableListOf<TocEntry>()
        ANCHOR.findAll(navBody).forEach { match ->
            val href = decodeEntities(match.groupValues[2]).trim()
            val label = decodeEntities(match.groupValues[3])
                .replace(TAG, " ")
                .replace(WHITESPACE, " ")
                .trim()
            if (href.isNotEmpty() && label.isNotEmpty()) {
                val (path, fragment) = EpubPaths.resolve(documentPath, href)
                entries += TocEntry(label, path, fragment, 0, null)
            }
        }
        return entries.ifEmpty { null }
    }

    private fun decodeEntities(value: String): String {
        return value
            .replace("&nbsp;", "\u00A0", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace(NUMERIC_ENTITY) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                    ?: match.groupValues[2].toIntOrNull()
                if (code != null && Character.isValidCodePoint(code)) {
                    String(Character.toChars(code))
                } else {
                    match.value
                }
            }
    }

    private fun walkNavList(
        list: Element,
        depth: Int,
        documentPath: String,
        output: MutableList<TocEntry>,
    ) {
        for (item in list.childElements().filter { it.localNameOf() == "li" }) {
            val children = item.childElements()
            val anchor = children.firstOrNull { it.localNameOf() == "a" }
            val span = children.firstOrNull { it.localNameOf() == "span" }
            val label = (anchor ?: span)?.textContent?.replace(WHITESPACE, " ")?.trim()
            val href = anchor?.getAttribute("href")?.takeIf { it.isNotBlank() }
            if (!label.isNullOrEmpty() && href != null) {
                val (path, fragment) = EpubPaths.resolve(documentPath, href)
                output += TocEntry(label, path, fragment, depth, null)
            }
            children.firstOrNull { it.localNameOf() == "ol" }?.let {
                walkNavList(it, depth + 1, documentPath, output)
            }
        }
    }

    private fun parseNcx(
        archive: EpubArchive,
        manifest: Map<String, ManifestItem>,
        ncxId: String?,
    ): List<TocEntry> {
        val ncxItem = ncxId?.let(manifest::get)
            ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            ?: return emptyList()
        val bytes = archive.readBytes(ncxItem.href) ?: return emptyList()
        val document = try {
            parseXml(bytes, ncxItem.href)
        } catch (_: EpubException) {
            return emptyList()
        }
        val navMap = firstElement(document, "navMap") ?: return emptyList()
        val entries = mutableListOf<TocEntry>()
        walkNavPoints(navMap, 0, ncxItem.href, entries)
        return entries
    }

    private fun walkNavPoints(
        parent: Element,
        depth: Int,
        documentPath: String,
        output: MutableList<TocEntry>,
    ) {
        for (navPoint in parent.childElements().filter { it.localNameOf() == "navPoint" }) {
            val label = navPoint.childElements()
                .firstOrNull { it.localNameOf() == "navLabel" }
                ?.childElements()
                ?.firstOrNull { it.localNameOf() == "text" }
                ?.textContent
                ?.replace(WHITESPACE, " ")
                ?.trim()
            val source = navPoint.childElements()
                .firstOrNull { it.localNameOf() == "content" }
                ?.getAttribute("src")
            if (!label.isNullOrEmpty() && !source.isNullOrBlank()) {
                val (path, fragment) = EpubPaths.resolve(documentPath, source)
                output += TocEntry(label, path, fragment, depth, null)
            }
            walkNavPoints(navPoint, depth + 1, documentPath, output)
        }
    }

    private fun findCoverEntry(opf: Document, manifest: Map<String, ManifestItem>): String? {
        val metas = opf.getElementsByTagNameNS("*", "meta")
        for (index in 0 until metas.length) {
            val meta = metas.item(index) as? Element ?: continue
            if (meta.getAttribute("name") == "cover") {
                val id = meta.getAttribute("content")
                manifest[id]?.let { return it.href }
            }
        }
        manifest.values.firstOrNull { "cover-image" in it.properties }?.let { return it.href }
        return manifest.values.firstOrNull { it.mediaType.startsWith("image/") }?.href
    }

    private fun parseXml(bytes: ByteArray, name: String): Document {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.isExpandEntityReferences = false
            trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false)
            trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false)
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            return builder.parse(ByteArrayInputStream(bytes))
        } catch (exception: Exception) {
            throw EpubException("無法解析 $name：${exception.message ?: "格式錯誤"}")
        }
    }

    private fun trySetFeature(
        factory: DocumentBuilderFactory,
        feature: String,
        value: Boolean,
    ) {
        try {
            factory.setFeature(feature, value)
        } catch (_: Exception) {
            // Some Android XML implementations do not expose every feature.
        }
    }

    private fun firstElement(document: Document, localName: String): Element? {
        val nodes = document.getElementsByTagNameNS("*", localName)
        return nodes.item(0) as? Element
    }

    private fun firstText(document: Document, localName: String): String? {
        return firstElement(document, localName)?.textContent
    }

    private fun allTexts(document: Document, localName: String): List<String> {
        val nodes = document.getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).mapNotNull { nodes.item(it)?.textContent }
    }

    private fun isReadableMediaType(mediaType: String): Boolean {
        val type = mediaType.lowercase()
        return type == "application/xhtml+xml" ||
            type == "text/html" ||
            type == "application/xml" ||
            type.contains("xhtml")
    }

    private fun Element.localNameOf(): String {
        return localName ?: tagName.substringAfter(':')
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) result += node as Element
        }
        return result
    }

    private fun Element.descendants(localName: String): List<Element> {
        val result = mutableListOf<Element>()
        fun walk(element: Element) {
            element.childElements().forEach { child ->
                if (child.localNameOf() == localName) result += child
                walk(child)
            }
        }
        walk(this)
        return result
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: Set<String>,
    )

    private companion object {
        const val OPS_NS = "http://www.idpf.org/2007/ops"
        val WHITESPACE = Regex("\\s+")
        val TAG = Regex("<[^>]+>")
        val NUMERIC_ENTITY = Regex("&#(?:x([0-9a-fA-F]+)|([0-9]+));")
        val NAV_TOC_BLOCK = Regex(
            "(?is)<nav\\b[^>]*(?:epub:type\\s*=\\s*[\"']?toc[\"']?)[^>]*>(.*?)</nav\\s*>",
        )
        val ANCHOR = Regex(
            "(?is)<a\\b[^>]*?href\\s*=\\s*([\"'])(.*?)\\1[^>]*>(.*?)</a\\s*>",
        )
    }
}
