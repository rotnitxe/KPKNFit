package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.ImportDocumentType
import com.example.kpkn.data.importer.ocr.OcrImportSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.text.Normalizer
import java.nio.charset.Charset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val URL_TOO_LARGE = "La página o el archivo supera el límite de 5 MB."
private const val URL_UNREADABLE = "No pudimos leer ese enlace. Usa una página pública https:// o pega el texto del programa."
private const val MAX_REDIRECTS = 5
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000

/** OCR local que usa la descarga HTTPS sin cambiar su procedencia ni subir los bytes a un servicio. */
internal interface ProgramUrlOcr {
    suspend fun readPdf(bytes: ByteArray, title: String, cacheDir: File?, onProgress: (Int, Int) -> Unit): List<ImportSheet>
    suspend fun readImage(bytes: ByteArray, title: String): List<ImportSheet>
}

private object DeviceProgramUrlOcr : ProgramUrlOcr {
    override suspend fun readPdf(
        bytes: ByteArray,
        title: String,
        cacheDir: File?,
        onProgress: (Int, Int) -> Unit,
    ): List<ImportSheet> = OcrImportSource.readPdfBytes(bytes, title, cacheDir, onProgress)

    override suspend fun readImage(bytes: ByteArray, title: String): List<ImportSheet> =
        OcrImportSource.readImageBytes(bytes, title)
}

/** Resultado de leer una página pública o un archivo compartido desde un enlace HTTPS. */
data class ProgramUrlDocument(
    val title: String,
    val sheets: List<ImportSheet>,
    /** URL pública de procedencia; conserva ruta y puerto, pero nunca credenciales, query ni fragmento. */
    val sourceUrl: String,
    /** Texto HTML seleccionado para la lectura, disponible para corregirlo en la revisión. */
    val extractedText: String? = null,
    /** Texto de navegación/editorial descartado por la extracción, disponible para inspección. */
    val discardedText: String? = null,
    val discardedTextTruncated: Boolean = false,
    /** Attachment hints from the response; never includes cookies or a provider URI. */
    val displayName: String? = null,
    val mediaType: String? = null,
    /** Transient downloaded bytes used only to retain a bounded private preview of linked PDFs/photos. */
    internal val sourceBytes: ByteArray? = null,
)

/** Descarga acotada de páginas HTML o archivos de rutina, sin ejecutar scripts ni enviar cookies. */
object ProgramUrlReader {
    /** Lee una página pública o un archivo compartido. La red y el parseo corren en IO. */
    suspend fun read(
        rawUrl: String,
        cacheDir: File? = null,
        onProgress: (page: Int, pages: Int) -> Unit = { _, _ -> },
    ): ProgramUrlDocument = readWithTransport(
        rawUrl = rawUrl,
        validateHost = ::validatePublicHost,
        openConnection = { uri ->
            (uri.toURL().openConnection() as? HttpURLConnection)
                ?: throw ImportFileException(URL_UNREADABLE)
        },
        cacheDir = cacheDir,
        onProgress = onProgress,
        ocr = DeviceProgramUrlOcr,
    )

    /** Separa transporte y validación para probar respuestas, límites y redirecciones sin usar la red. */
    internal suspend fun readWithTransport(
        rawUrl: String,
        validateHost: (URI) -> URI,
        openConnection: (URI) -> HttpURLConnection,
        cacheDir: File? = null,
        onProgress: (page: Int, pages: Int) -> Unit = { _, _ -> },
        ocr: ProgramUrlOcr = DeviceProgramUrlOcr,
    ): ProgramUrlDocument = withContext(Dispatchers.IO) {
        try {
            var current = parseHttpsUrl(rawUrl)
            var redirects = 0
            while (true) {
                current = validateHost(current)
                currentCoroutineContext().ensureActive()
                val connection = openConnection(current)
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel.sheet.macroenabled.12,application/vnd.oasis.opendocument.spreadsheet,application/pdf,image/*,text/csv,text/plain,application/octet-stream;q=0.8")
                    connection.setRequestProperty("User-Agent", "KPKNFit Program Import")
                    val status = connection.responseCode
                    if (status in REDIRECT_CODES) {
                        if (redirects >= MAX_REDIRECTS) throw ImportFileException(URL_UNREADABLE)
                        val location = connection.getHeaderField("Location")
                            ?: throw ImportFileException(URL_UNREADABLE)
                        current = parseHttpsUrl(current.resolve(location).toString())
                        redirects += 1
                        continue
                    }
                    if (status !in 200..299) throw ImportFileException(URL_UNREADABLE)
                    val contentType = connection.contentType.orEmpty()
                    val fileName = contentDispositionName(connection.getHeaderField("Content-Disposition"))
                        ?: current.path.substringAfterLast('/').takeIf { it.isNotBlank() }
                        ?: "programa"
                    val bytes = connection.inputStream.use { input ->
                        val prefix = readHeader(input, HEADER_BYTES)
                        val kind = downloadKind(prefix, contentType, fileName)
                        val (limit, tooLargeMessage) = downloadLimit(kind)
                        if (connection.contentLengthLong > limit) throw ImportFileException(tooLargeMessage)
                        readBounded(input, limit, tooLargeMessage, prefix)
                    }
                    return@withContext parseDownloadedPayload(bytes, contentType, fileName, current, cacheDir, onProgress, ocr)
                } finally {
                    connection.disconnect()
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw ImportFileException(URL_UNREADABLE)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: ImportFileException) {
            throw error
        } catch (_: Exception) {
            throw ImportFileException(URL_UNREADABLE)
        }
    }

    /** URL HTTPS válida, sin credenciales embebidas ni direcciones locales. Expuesta para validación de formulario y pruebas. */
    internal fun parseHttpsUrl(rawUrl: String): URI {
        val raw = rawUrl.trim()
        if (raw.isEmpty() || raw.length > 2_048) throw ImportFileException(URL_UNREADABLE)
        val alreadyHasScheme = SCHEME_PREFIX.containsMatchIn(raw) && !HOST_WITH_PORT.matches(raw)
        val uri = runCatching { URI(if (alreadyHasScheme) raw else "https://$raw").normalize() }
            .getOrElse { throw ImportFileException(URL_UNREADABLE) }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.port !in -1..65_535
        ) throw ImportFileException(URL_UNREADABLE)
        return uri
    }

    /** Valida también que el host no sea local antes de abrir la conexión. */
    internal fun validatePublicHttpsUrl(rawUrl: String): URI = validatePublicHost(parseHttpsUrl(rawUrl))

    /** Extrae HTML con DOM, conserva tablas como filas tabuladas y lo pasa por el mismo lector que texto pegado. */
    internal fun parsePayload(bytes: ByteArray, contentType: String, fileName: String, source: URI): ProgramUrlDocument {
        if (bytes.size > ImportLimits.MAX_FILE_BYTES) throw ImportFileException(URL_TOO_LARGE)
        val mediaType = contentType.substringBefore(';').trim().lowercase()
        val charset = charsetOf(contentType)
        val prefix = String(bytes, 0, minOf(bytes.size, 512), charset).trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
        val html = mediaType.contains("text/html") || mediaType.contains("application/xhtml+xml") ||
            fileName.substringAfterLast('.', "").lowercase() in setOf("html", "htm") ||
            prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.startsWith("<head")
        if (html) {
            val document = Jsoup.parse(String(bytes, charset), source.toString())
            val pageTitle = document.title().trim()
            val title = pageTitle.ifBlank { ImportFileReader.titleOf(fileName).ifBlank { source.host } }
            val discarded = removeUnrelatedContent(document)
            val plainText = htmlToPlainText(primaryContent(document))
            if (plainText.isBlank()) throw ImportFileException("No encontramos texto en esa página. Prueba con otra URL o pega el texto del programa.")
            val discardedText = discarded.joinToString("\n\n").trim()
            val boundedDiscardedText = discardedText.take(MAX_DISCARDED_TEXT_CHARS)
            val parsed = TextProgramReader.read(plainText, title)
            val sourceNote = "Origen web: ${source.host}"
            return ProgramUrlDocument(
                title = title,
                sheets = listOf(parsed.copy(documentType = ImportDocumentType.URL_HTML, notes = (parsed.notes + sourceNote).distinct())),
                sourceUrl = sourceDocumentUrl(source),
                extractedText = plainText,
                discardedText = boundedDiscardedText.takeIf(String::isNotBlank),
                discardedTextTruncated = discardedText.length > boundedDiscardedText.length,
                displayName = fileName,
                mediaType = mediaType,
            )
        }

        val detectedType = ImportFileReader.documentTypeOf(fileName, mediaType.ifBlank { null }, ImportKind.OTHER)
        val sheets = ImportFileReader.read(bytes, fileName, mediaType.ifBlank { null })
        val title = ImportFileReader.titleOf(fileName).ifBlank { source.host }
        val sourceNote = "Origen web: ${source.host}"
        return ProgramUrlDocument(
            title = title,
            sheets = sheets.map { sheet -> sheet.copy(documentType = detectedType, notes = (sheet.notes + sourceNote).distinct()) },
            sourceUrl = sourceDocumentUrl(source),
            displayName = fileName,
            mediaType = mediaType.ifBlank { null },
        )
    }

    private suspend fun parseDownloadedPayload(
        bytes: ByteArray,
        contentType: String,
        fileName: String,
        source: URI,
        cacheDir: File?,
        onProgress: (page: Int, pages: Int) -> Unit,
        ocr: ProgramUrlOcr,
    ): ProgramUrlDocument {
        val kind = downloadKind(bytes.copyOfRange(0, minOf(bytes.size, HEADER_BYTES)), contentType, fileName)
        if (kind == ImportKind.OTHER) return parsePayload(bytes, contentType, fileName, source)

        val title = ImportFileReader.titleOf(fileName).ifBlank { source.host }
        val sourceNote = "Origen web: ${source.host}"
        val sheets = when (kind) {
            ImportKind.PDF -> ocr.readPdf(bytes, title, cacheDir, onProgress)
            ImportKind.IMAGE -> ocr.readImage(bytes, title)
            ImportKind.OTHER -> error("Handled above")
        }
        val documentType = when (kind) {
            ImportKind.PDF -> ImportDocumentType.URL_PDF
            ImportKind.IMAGE -> ImportDocumentType.URL_IMAGE
            ImportKind.OTHER -> error("Handled above")
        }
        return ProgramUrlDocument(
            title = title,
            sheets = sheets.map { sheet -> sheet.copy(documentType = documentType, notes = (sheet.notes + sourceNote).distinct()) },
            sourceUrl = sourceDocumentUrl(source),
            displayName = fileName,
            mediaType = contentType.substringBefore(';').trim().lowercase().ifBlank { null },
            sourceBytes = bytes.takeIf { kind in setOf(ImportKind.PDF, ImportKind.IMAGE) },
        )
    }

    private fun downloadKind(prefix: ByteArray, contentType: String, fileName: String): ImportKind {
        val mediaType = contentType.substringBefore(';').trim().lowercase()
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (mediaType == "application/pdf" || extension == "pdf" || prefix.hasPrefix(PDF_SIGNATURE)) return ImportKind.PDF
        return importKindOf(prefix, mediaType)
    }

    private fun ByteArray.hasPrefix(signature: ByteArray): Boolean =
        size >= signature.size && signature.indices.all { this[it] == signature[it] }

    private fun downloadLimit(kind: ImportKind): Pair<Long, String> = when (kind) {
        ImportKind.PDF -> ImportLimits.MAX_PDF_BYTES to OcrImportSource.PDF_TOO_LARGE_MESSAGE
        ImportKind.IMAGE -> ImportLimits.MAX_IMAGE_BYTES to OcrImportSource.IMAGE_TOO_LARGE_MESSAGE
        ImportKind.OTHER -> ImportLimits.MAX_FILE_BYTES to URL_TOO_LARGE
    }

    /** Conserva la ruta pública del documento y omite credenciales, query y fragmento. */
    private fun sourceDocumentUrl(source: URI): String {
        if (!source.scheme.equals("https", ignoreCase = true)) throw ImportFileException(URL_UNREADABLE)
        val host = source.host?.removeSurrounding("[", "]")?.lowercase()?.takeIf { it.isNotBlank() }
            ?: throw ImportFileException(URL_UNREADABLE)
        val authorityHost = if (':' in host) "[$host]" else host
        val port = source.port.takeIf { it != -1 && it != 443 }?.let { ":$it" }.orEmpty()
        return "https://$authorityHost$port${source.rawPath.orEmpty()}"
    }

    private suspend fun readBounded(input: InputStream, limit: Long, tooLargeMessage: String, prefix: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        if (prefix.size.toLong() > limit) throw ImportFileException(tooLargeMessage)
        output.write(prefix)
        val buffer = ByteArray(8 * 1024)
        var total = prefix.size.toLong()
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw ImportFileException(tooLargeMessage)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun readHeader(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var total = 0
        while (total < count) {
            val read = input.read(buffer, total, count - total)
            if (read < 0) break
            if (read == 0) {
                val one = input.read()
                if (one < 0) break
                buffer[total++] = one.toByte()
            } else {
                total += read
            }
        }
        return buffer.copyOf(total)
    }

    private fun validatePublicHost(uri: URI): URI {
        val host = uri.host.removeSurrounding("[", "]").removeSuffix(".").lowercase()
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw ImportFileException(URL_UNREADABLE)
        }
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (_: Exception) {
            throw ImportFileException(URL_UNREADABLE)
        }
        if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) }) throw ImportFileException(URL_UNREADABLE)
        return uri
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val bytes = address.address.map { it.toInt() and 0xff }
        return when (address) {
            is Inet4Address -> {
                val a = bytes[0]
                val b = bytes[1]
                !(a == 0 || a == 10 || a == 127 || a >= 224 ||
                    (a == 169 && b == 254) || (a == 172 && b in 16..31) ||
                    (a == 192 && b == 168) || (a == 100 && b in 64..127) ||
                    (a == 192 && b == 0) || (a == 198 && b in 18..19) ||
                    (a == 198 && b == 51 && bytes[2] == 100) ||
                    (a == 203 && b == 0 && bytes[2] == 113))
            }
            is Inet6Address -> (bytes[0] and 0xfe) != 0xfc && !(bytes[0] == 0xfe && (bytes[1] and 0xc0) == 0x80)
            else -> false
        }
    }

    private fun contentDispositionName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val encoded = FILENAME_STAR.find(header)?.groupValues?.get(1)
        val name = encoded?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrNull() }
            ?: FILENAME.find(header)?.groupValues?.get(1)?.trim('"', '\'')
            ?: return null
        return name.substringAfterLast('/').substringAfterLast('\\').take(160).takeIf { it.isNotBlank() }
    }

    private fun charsetOf(contentType: String): Charset {
        val label = CHARSET.find(contentType)?.groupValues?.get(1)?.trim('"', '\'') ?: return Charsets.UTF_8
        return runCatching { Charset.forName(label) }.getOrDefault(Charsets.UTF_8)
    }

    /** Excluye muebles de navegación y tarjetas editoriales, conservando su texto para que la persona lo pueda revisar. */
    private fun removeUnrelatedContent(document: org.jsoup.nodes.Document): List<String> {
        val discarded = mutableListOf<String>()
        val structural = document.select(
            "script, style, noscript, template, nav, aside, footer, form, iframe, svg, object, " +
                "[role=navigation], [role=banner], body > header",
        ).toList()
        structural.sortedByDescending(::domDepth).forEach { element ->
            val text = htmlToPlainText(element).trim()
            if (text.isNotBlank()) discarded += text
            element.remove()
        }

        document.getAllElements().toList().asReversed().forEach { element ->
            if (!hasEditorialNoise(element)) return@forEach
            val text = htmlToPlainText(element).trim()
            if (text.isNotBlank()) discarded += text
            element.remove()
        }

        document.select("article, section, div").toList().asReversed().forEach { element ->
            val heading = element.selectFirst("h1, h2, h3")?.text().orEmpty().trim()
            if (!RELATED_HEADING.containsMatchIn(heading)) return@forEach
            val text = htmlToPlainText(element).trim()
            if (text.isNotBlank()) discarded += text
            element.remove()
        }
        return discarded.distinct()
    }

    private fun hasEditorialNoise(element: Element): Boolean =
        listOf(element.id(), element.className(), element.attr("aria-label")).any { label ->
            EDITORIAL_NOISE.containsMatchIn(label)
        }

    private fun domDepth(element: Element): Int = element.parents().size

    private fun normalizedHeader(value: String): String {
        val unaccented = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")
        return HEADER_PUNCTUATION.replace(unaccented, " ").trim().replace(SPACE_RUN, " ")
    }

    /** Serializa el DOM con saltos de bloque y tablas tabuladas para mantener filas y columnas del programa. */
    private fun htmlToPlainText(root: Element): String {
        val output = StringBuilder()
        fun newline() {
            if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
        }
        fun cells(row: Element): List<String> = row.children()
            .filter { it.normalName() == "td" || it.normalName() == "th" }
            .map { it.text().replace('\u00a0', ' ').trim() }

        fun appendTable(table: Element) {
            val rows = table.select("tr").filter { row ->
                row.parents().firstOrNull { it.normalName() == "table" } === table
            }
            val headerIndex = rows.indexOfFirst { row -> row.children().any { it.normalName() == "th" } }
            if (headerIndex >= 0) {
                val headers = cells(rows[headerIndex]).map(::normalizedHeader)
                val dayIndex = headers.indexOfFirst { it in DAY_HEADERS }
                val listIndex = headers.indexOfFirst { it in EXERCISE_LIST_HEADERS }
                if (dayIndex >= 0 && listIndex >= 0) {
                    val workoutIndex = headers.indexOfFirst { it in WORKOUT_HEADERS }
                    newline()
                    output.append("Exercise\tReps\n")
                    rows.drop(headerIndex + 1).forEach { row ->
                        val values = cells(row)
                        val day = values.getOrNull(dayIndex).orEmpty()
                        val workout = values.getOrNull(workoutIndex).orEmpty()
                        val list = values.getOrNull(listIndex).orEmpty()
                        if (day.isBlank() && list.isBlank()) return@forEach
                        val dayLabel = listOf(day, workout).filter(String::isNotBlank).joinToString(" — ")
                        if (dayLabel.isNotEmpty()) output.append(dayLabel).append('\n')
                        EXERCISE_LIST_SEPARATOR.split(list).map(String::trim).filter(String::isNotEmpty).forEach { exercise ->
                            output.append(exercise).append("\t\n")
                        }
                    }
                    newline()
                    return
                }
            }
            rows.forEach { row ->
                val values = cells(row)
                if (values.any(String::isNotBlank)) output.append(values.joinToString("\t")).append('\n')
            }
            newline()
        }
        fun appendNode(node: Node) {
            when (node) {
                is TextNode -> output.append(node.wholeText)
                is Element -> when (node.normalName()) {
                    "br" -> newline()
                    "table" -> appendTable(node)
                    "li" -> {
                        newline()
                        output.append("• ")
                        node.childNodes().forEach(::appendNode)
                        newline()
                    }
                    in BLOCK_TAGS -> {
                        newline()
                        node.childNodes().forEach(::appendNode)
                        newline()
                    }
                    else -> node.childNodes().forEach(::appendNode)
                }
                else -> node.childNodes().forEach(::appendNode)
            }
        }
        appendNode(root)
        return output.toString().replace('\u00a0', ' ').replace('\r', '\n')
            .split('\n')
            .map { line -> line.split('\t').joinToString("\t") { cell -> SPACE_RUN.replace(cell.replace(WHITESPACE, " "), " ").trim() } }
            .filter { line -> line.split('\t').any(String::isNotBlank) }
            .joinToString("\n")
    }

    /** Prefiere el contenido semántico con más estructura de rutina frente a tarjetas de una página larga. */
    private fun primaryContent(document: org.jsoup.nodes.Document): Element {
        val candidates = document.select(
            "article, main, [role=main], .entry-content, .post-content, .article-content, .article-body, #main-content, #content, #main",
        ).filter { it.text().isNotBlank() }
        return candidates.maxByOrNull(::contentScore) ?: document.body()
    }

    private fun contentScore(element: Element): Int = element.text().length +
        element.select("h1").size * 500 + element.select("table").size * 300 +
        element.select("tr").size * 30 + element.select("li").size * 10 - element.select("a").size * 2

    private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val HOST_WITH_PORT = Regex("^[^/:?#]+:\\d+(?:[/?#].*)?$")
    private val HEADER_PUNCTUATION = Regex("[^a-z0-9]+")
    private val DIACRITICS = Regex("\\p{M}+")
    private val EXERCISE_LIST_SEPARATOR = Regex("\\s*[,;]\\s*")
    private val EXERCISE_LIST_HEADERS = setOf("exercises", "movements", "ejercicios", "movimientos")
    private val DAY_HEADERS = setOf("day", "days", "weekday", "week day", "dia", "dias")
    private val WORKOUT_HEADERS = setOf("workout", "session", "focus", "entrenamiento", "sesion", "enfoque")
    private val BLOCK_TAGS = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "section", "article", "header", "footer", "main", "ul", "ol", "blockquote")
    private val EDITORIAL_NOISE = Regex("(?i)(^|[-_\\s])(ad|ads|advert|advertisement|sponsor|sponsored|promo|promotion|cookie|newsletter|related|recommend|banner|sidebar|share|social|comments?|navigation|menu)([-_\\s]|$)")
    private val RELATED_HEADING = Regex("(?i)^(read more|related|more routines|you may also like|recommended)\\b.*")
    private val WHITESPACE = Regex("\\s+")
    private val SPACE_RUN = Regex(" {2,}")
    private val CHARSET = Regex("(?i)charset\\s*=\\s*([^;\\s]+)")
    private val FILENAME_STAR = Regex("(?i)filename\\*\\s*=\\s*UTF-8''([^;]+)")
    private val FILENAME = Regex("(?i)filename\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^;]+)")
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    private val PDF_SIGNATURE = "%PDF".toByteArray(Charsets.US_ASCII)
    private const val HEADER_BYTES = 16
    private const val MAX_DISCARDED_TEXT_CHARS = 20_000
}
