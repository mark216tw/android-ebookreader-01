package com.minireader.app.data.epub

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun parsesEpub3MetadataSpineNavAndCover() {
        val file = createEpub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to CONTAINER_EPUB3,
                "OEBPS/content.opf" to OPF_EPUB3,
                "OEBPS/nav.xhtml" to NAV_XHTML,
                "OEBPS/text/ch1.xhtml" to CHAPTER_ONE,
                "OEBPS/text/ch 2.xhtml" to CHAPTER_TWO,
                "OEBPS/images/cover.jpg" to "not-a-real-jpeg",
            ),
        )

        val book = EpubArchive(file).use { EpubParser().parse(it) }

        assertEquals("測試書名", book.title)
        assertEquals("作者甲、作者乙", book.author)
        assertEquals("zh-TW", book.language)
        assertEquals("OEBPS/images/cover.jpg", book.coverEntryPath)
        assertEquals(2, book.chapters.size)

        assertEquals("OEBPS/text/ch1.xhtml", book.chapters[0].href)
        assertEquals("OEBPS/text/ch 2.xhtml", book.chapters[1].href)
        assertEquals("第一章", book.chapters[0].title)
        assertEquals("第二章", book.chapters[1].title)

        assertEquals(3, book.toc.size)
        assertEquals("第一章", book.toc[0].title)
        assertEquals(0, book.toc[0].depth)
        assertEquals(0, book.toc[0].chapterIndex)
        assertEquals("part", book.toc[1].fragment)
        assertEquals(1, book.toc[1].chapterIndex)
        assertEquals(1, book.toc[2].depth)
        assertEquals("sub", book.toc[2].fragment)
        assertEquals(1, book.toc[2].chapterIndex)
    }

    @Test
    fun fallsBackToEpub2Ncx() {
        val file = createEpub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to CONTAINER_EPUB2,
                "content.opf" to OPF_EPUB2,
                "toc.ncx" to NCX,
                "ch1.xhtml" to CHAPTER_ONE,
                "ch2.xhtml" to CHAPTER_TWO,
            ),
        )

        val book = EpubArchive(file).use { EpubParser().parse(it) }

        assertEquals("舊格式書籍", book.title)
        assertEquals(2, book.chapters.size)
        assertEquals(2, book.toc.size)
        assertEquals("第一章", book.toc[0].title)
        assertEquals("第二章", book.toc[1].title)
        assertEquals(0, book.toc[0].chapterIndex)
        assertEquals(1, book.toc[1].chapterIndex)
        assertEquals("第一章", book.chapters[0].title)
    }

    @Test
    fun recoversTocWhenNavContainsHtmlEntity() {
        val file = createEpub(
            mapOf(
                "META-INF/container.xml" to CONTAINER_EPUB3,
                "OEBPS/content.opf" to OPF_EPUB3,
                "OEBPS/nav.xhtml" to NAV_WITH_HTML_ENTITY,
                "OEBPS/text/ch1.xhtml" to CHAPTER_ONE,
                "OEBPS/text/ch 2.xhtml" to CHAPTER_TWO,
            ),
        )

        val book = EpubArchive(file).use { EpubParser().parse(it) }

        assertEquals(1, book.toc.size)
        assertTrue(book.toc[0].title.contains("Chapter"))
        assertEquals(0, book.toc[0].chapterIndex)
    }

    @Test
    fun rejectsAdobeDrmProtectedBook() {
        val file = createEpub(
            mapOf(
                "META-INF/container.xml" to CONTAINER_EPUB3,
                "META-INF/rights.xml" to "<rights/>",
                "OEBPS/content.opf" to OPF_EPUB3,
                "OEBPS/nav.xhtml" to NAV_XHTML,
                "OEBPS/text/ch1.xhtml" to CHAPTER_ONE,
                "OEBPS/text/ch 2.xhtml" to CHAPTER_TWO,
            ),
        )

        var message: String? = null
        try {
            EpubArchive(file).use { EpubParser().parse(it) }
        } catch (exception: EpubException) {
            message = exception.message
        }
        assertNotNull(message)
        assertTrue(message!!.contains("DRM"))
    }

    @Test
    fun reportsMissingContainer() {
        val file = createEpub(mapOf("mimetype" to "application/epub+zip"))
        var message: String? = null
        try {
            EpubArchive(file).use { EpubParser().parse(it) }
        } catch (exception: EpubException) {
            message = exception.message
        }
        assertNotNull(message)
        assertTrue(message!!.contains("container.xml"))
    }

    @Test
    fun resolvesPercentEncodingAndParentSegments() {
        assertEquals("OEBPS/text/ch 1.xhtml", EpubPaths.percentDecode("OEBPS/text/ch%201.xhtml"))
        assertEquals("章節/一.xhtml", EpubPaths.percentDecode("%E7%AB%A0%E7%AF%80/%E4%B8%80.xhtml"))
        assertEquals("OEBPS/text/ch%201.xhtml", EpubPaths.encodePath("OEBPS/text/ch 1.xhtml"))
        assertEquals(
            "OEBPS/text/notes.xhtml",
            EpubPaths.resolve("OEBPS/text/ch1.xhtml", "../text/notes.xhtml").first,
        )
        assertEquals(
            "OEBPS/text/ch1.xhtml",
            EpubPaths.resolve("OEBPS/text/ch1.xhtml", "#note1").first,
        )
    }

    private fun createEpub(entries: Map<String, String>): File {
        val file = temporaryFolder.newFile("book-${System.nanoTime()}.epub")
        ZipOutputStream(FileOutputStream(file)).use { output ->
            entries.forEach { (name, content) ->
                output.putNextEntry(ZipEntry(name))
                output.write(content.trimIndent().toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
        }
        return file
    }

    private companion object {
        const val CONTAINER_EPUB3 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """

        const val CONTAINER_EPUB2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """

        const val OPF_EPUB3 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="id">test-book</dc:identifier>
                <dc:title>測試書名</dc:title>
                <dc:creator>作者甲</dc:creator>
                <dc:creator>作者乙</dc:creator>
                <dc:language>zh-TW</dc:language>
                <meta name="cover" content="cover"/>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="cover" href="images/cover.jpg" media-type="image/jpeg"/>
                <item id="ch1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="text/ch%202.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
        """

        const val NAV_XHTML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <head><title>目錄</title></head>
              <body>
                <nav epub:type="toc">
                  <h1>目錄</h1>
                  <ol>
                    <li><a href="text/ch1.xhtml">第一章</a></li>
                    <li><a href="text/ch%202.xhtml#part">第二章</a>
                      <ol>
                        <li><a href="text/ch%202.xhtml#sub">第二節</a></li>
                      </ol>
                    </li>
                  </ol>
                </nav>
              </body>
            </html>
        """

        const val NAV_WITH_HTML_ENTITY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <head><title>目錄</title></head>
              <body>
                <nav epub:type="toc">
                  <ol>
                    <li><a href="text/ch1.xhtml">Chapter&nbsp;One</a></li>
                  </ol>
                </nav>
              </body>
            </html>
        """

        const val OPF_EPUB2 = """            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="id">old-book</dc:identifier>
                <dc:title>舊格式書籍</dc:title>
                <dc:creator>舊作者</dc:creator>
                <dc:language>zh-TW</dc:language>
              </metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx">
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
        """

        const val NCX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <head/>
              <docTitle><text>舊格式書籍</text></docTitle>
              <navMap>
                <navPoint id="n1" playOrder="1">
                  <navLabel><text>第一章</text></navLabel>
                  <content src="ch1.xhtml"/>
                </navPoint>
                <navPoint id="n2" playOrder="2">
                  <navLabel><text>第二章</text></navLabel>
                  <content src="ch2.xhtml"/>
                </navPoint>
              </navMap>
            </ncx>
        """

        const val CHAPTER_ONE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>第一章</title></head>
              <body><h1>第一章</h1><p>內容一</p></body>
            </html>
        """

        const val CHAPTER_TWO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>第二章</title></head>
              <body><h1>第二章</h1><p>內容二</p></body>
            </html>
        """
    }
}
