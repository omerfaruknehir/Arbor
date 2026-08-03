package app.xylune.chat.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class OfficeDocumentExtractorTest {
    @Test
    fun extractsDocxParagraphs() {
        val file = officeZip("sample.docx", mapOf(
            "word/document.xml" to """<?xml version="1.0"?><w:document xmlns:w="urn:w"><w:body><w:p><w:r><w:t>Hello</w:t></w:r><w:r><w:t> world</w:t></w:r></w:p><w:p><w:r><w:t>Second line</w:t></w:r></w:p></w:body></w:document>""",
        ))
        assertEquals("Hello world\nSecond line", OfficeDocumentExtractor.extract(file, DOCX))
    }

    @Test
    fun extractsPptxSlidesInNumericOrder() {
        val file = officeZip("slides.pptx", mapOf(
            "ppt/slides/slide2.xml" to slide("Second"),
            "ppt/slides/slide1.xml" to slide("First"),
        ))
        val text = OfficeDocumentExtractor.extract(file, PPTX).orEmpty()
        assertTrue(text.indexOf("First") < text.indexOf("Second"))
        assertTrue(text.contains("Slide 1"))
    }

    @Test
    fun extractsXlsxSharedStringsAndBooleanCells() {
        val file = officeZip("book.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst><si><t>Name</t></si><si><t>Xylune</t></si></sst>""",
            "xl/worksheets/sheet1.xml" to """<worksheet><sheetData><row><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row><row><c r="A2"><v>42</v></c><c r="B2" t="b"><v>1</v></c></row></sheetData></worksheet>""",
        ))
        val text = OfficeDocumentExtractor.extract(file, XLSX).orEmpty()
        assertTrue(text.contains("Data"))
        assertTrue(text.contains("Name\tXylune"))
        assertTrue(text.contains("42\tTRUE"))
    }

    @Test
    fun rejectsDocumentsWithDoctypeEntities() {
        val file = officeZip("unsafe.docx", mapOf(
            "word/document.xml" to """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY leak SYSTEM "file:///etc/passwd">]><w:document xmlns:w="urn:w"><w:body><w:p><w:r><w:t>&leak;</w:t></w:r></w:p></w:body></w:document>""",
        ))
        assertNull(OfficeDocumentExtractor.extract(file, DOCX))
    }

    private fun slide(text: String) = """<p:sld xmlns:p="urn:p" xmlns:a="urn:a"><p:cSld><a:p><a:r><a:t>$text</a:t></a:r></a:p></p:cSld></p:sld>"""

    private fun officeZip(name: String, entries: Map<String, String>): File {
        val directory = createTempDirectory("xylune-office-").toFile()
        val file = File(directory, name)
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
        file.deleteOnExit()
        directory.deleteOnExit()
        return file
    }

    companion object {
        private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        private const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
