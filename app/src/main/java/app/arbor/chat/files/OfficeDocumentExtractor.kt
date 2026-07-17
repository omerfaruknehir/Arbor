package app.arbor.chat.files

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Safe, dependency-free text extraction for modern Office Open XML documents. */
object OfficeDocumentExtractor {
    private const val MAX_XML_BYTES = 16 * 1024 * 1024
    private const val DEFAULT_MAX_CHARS = 1_000_000

    fun supports(mimeType: String, name: String): Boolean = when (name.substringAfterLast('.', "").lowercase()) {
        "docx", "xlsx", "pptx" -> true
        else -> mimeType in setOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        )
    }

    fun extract(file: File, mimeType: String, maxChars: Int = DEFAULT_MAX_CHARS): String? {
        if (!file.isFile || !supports(mimeType, file.name)) return null
        return runCatching {
            ZipFile(file).use { zip ->
                when (file.extension.lowercase()) {
                    "docx" -> extractDocx(zip)
                    "pptx" -> extractPptx(zip)
                    "xlsx" -> extractXlsx(zip)
                    else -> when (mimeType) {
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> extractDocx(zip)
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> extractPptx(zip)
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> extractXlsx(zip)
                        else -> ""
                    }
                }.trim().take(maxChars).takeIf(String::isNotBlank)
            }
        }.getOrNull()
    }

    private fun extractDocx(zip: ZipFile): String {
        val document = parse(zip, "word/document.xml") ?: return ""
        val paragraphs = document.getElementsByTagNameNS("*", "p")
        return buildString {
            for (index in 0 until paragraphs.length) {
                val text = collectText(paragraphs.item(index)).trimEnd()
                if (text.isNotBlank()) appendLine(text)
            }
        }
    }

    private fun extractPptx(zip: ZipFile): String {
        val slides = zip.entries().asSequence()
            .filter { !it.isDirectory && SLIDE_PATH.matches(it.name) }
            .sortedBy { SLIDE_PATH.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
            .toList()
        return buildString {
            slides.forEachIndexed { index, entry ->
                val document = parse(zip, entry) ?: return@forEachIndexed
                if (isNotEmpty()) appendLine()
                appendLine("Slide ${index + 1}")
                val paragraphs = document.getElementsByTagNameNS("*", "p")
                var wrote = false
                for (paragraphIndex in 0 until paragraphs.length) {
                    val text = collectText(paragraphs.item(paragraphIndex)).trim()
                    if (text.isNotBlank()) {
                        appendLine(text)
                        wrote = true
                    }
                }
                if (!wrote) {
                    val textNodes = document.getElementsByTagNameNS("*", "t")
                    for (textIndex in 0 until textNodes.length) {
                        textNodes.item(textIndex).textContent?.trim()?.takeIf(String::isNotBlank)?.let(::appendLine)
                    }
                }
            }
        }
    }

    private fun extractXlsx(zip: ZipFile): String {
        val sharedStrings = parse(zip, "xl/sharedStrings.xml")?.let { document ->
            val items = document.getElementsByTagNameNS("*", "si")
            List(items.length) { index -> collectText(items.item(index)).trim() }
        }.orEmpty()
        val namedSheets = workbookSheets(zip)
        val sheets = if (namedSheets.isNotEmpty()) namedSheets else zip.entries().asSequence()
            .filter { !it.isDirectory && SHEET_PATH.matches(it.name) }
            .sortedBy { SHEET_PATH.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
            .mapIndexed { index, entry -> "Sheet ${index + 1}" to entry.name }
            .toList()

        return buildString {
            sheets.forEach { (sheetName, path) ->
                val document = parse(zip, path) ?: return@forEach
                if (isNotEmpty()) appendLine()
                appendLine(sheetName)
                val rows = document.getElementsByTagNameNS("*", "row")
                for (rowIndex in 0 until rows.length) {
                    val row = rows.item(rowIndex) as? Element ?: continue
                    val cells = row.getElementsByTagNameNS("*", "c")
                    val values = mutableListOf<String>()
                    var nextColumn = 0
                    for (cellIndex in 0 until cells.length) {
                        val cell = cells.item(cellIndex) as? Element ?: continue
                        val column = cell.getAttribute("r").takeWhile(Char::isLetter).let(::columnIndex).takeIf { it >= 0 } ?: nextColumn
                        while (values.size < column) values += ""
                        val value = cellValue(cell, sharedStrings)
                        if (values.size == column) values += value else values[column] = value
                        nextColumn = column + 1
                    }
                    val line = values.dropLastWhile(String::isBlank).joinToString("\t")
                    if (line.isNotBlank()) appendLine(line)
                }
            }
        }
    }

    private fun workbookSheets(zip: ZipFile): List<Pair<String, String>> {
        val workbook = parse(zip, "xl/workbook.xml") ?: return emptyList()
        val relationships = parse(zip, "xl/_rels/workbook.xml.rels")?.let { rels ->
            val nodes = rels.getElementsByTagNameNS("*", "Relationship")
            buildMap {
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    val id = element.getAttribute("Id")
                    val target = element.getAttribute("Target")
                    if (id.isNotBlank() && target.isNotBlank() && !target.contains("..")) {
                        put(id, if (target.startsWith('/')) target.removePrefix("/") else "xl/$target")
                    }
                }
            }
        }.orEmpty()
        val sheetNodes = workbook.getElementsByTagNameNS("*", "sheet")
        return buildList {
            for (index in 0 until sheetNodes.length) {
                val sheet = sheetNodes.item(index) as? Element ?: continue
                val id = sheet.getAttributeNS(OFFICE_REL_NS, "id").ifBlank { sheet.getAttribute("r:id") }
                val path = relationships[id] ?: continue
                add(sheet.getAttribute("name").ifBlank { "Sheet ${index + 1}" } to path)
            }
        }
    }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        if (type == "inlineStr") return collectText(cell).trim()
        val value = cell.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
        return when (type) {
            "s" -> value.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
            "b" -> if (value == "1") "TRUE" else "FALSE"
            else -> value
        }
    }

    private fun collectText(node: Node): String = buildString { appendNodeText(node, this) }

    private fun appendNodeText(node: Node, output: StringBuilder) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            when (node.localName ?: node.nodeName.substringAfter(':')) {
                "t" -> output.append(node.textContent.orEmpty())
                "tab" -> output.append('\t')
                "br" -> output.append('\n')
                else -> {
                    val children = node.childNodes
                    for (index in 0 until children.length) appendNodeText(children.item(index), output)
                }
            }
        }
    }

    private fun parse(zip: ZipFile, path: String) = zip.getEntry(path)?.let { parse(zip, it) }

    private fun parse(zip: ZipFile, entry: ZipEntry) = readLimited(zip, entry)?.let { bytes ->
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun readLimited(zip: ZipFile, entry: ZipEntry): ByteArray? {
        if (entry.isDirectory || entry.size > MAX_XML_BYTES) return null
        return zip.getInputStream(entry).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_XML_BYTES) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun columnIndex(value: String): Int {
        if (value.isBlank()) return -1
        var result = 0
        value.uppercase().forEach { character ->
            if (character !in 'A'..'Z') return -1
            result = result * 26 + (character - 'A' + 1)
        }
        return result - 1
    }

    private val SLIDE_PATH = Regex("ppt/slides/slide(\\d+)\\.xml")
    private val SHEET_PATH = Regex("xl/worksheets/sheet(\\d+)\\.xml")
    private const val OFFICE_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
}
