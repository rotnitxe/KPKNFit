package com.example.kpkn.services.diagnostics

import android.content.Context
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

enum class ReportOrigin { GESTURE, VOICE }

data class ReportRequest(
    val origin: ReportOrigin,
    val comment: String,
    val category: String? = null,
    val screen: String? = null,
    val sessionId: String? = null,
    val workoutId: String? = null,
)

data class CreatedReport(
    val reportId: String,
    val file: File,
    val contextHash: String,
)

/** Durable report evidence. AI status is appended; raw report lines are never replaced. */
object KpknReportManager {
    private val writeLock = Any()
    private const val DIRECTORY_NAME = "kpkn_logs/reports"
    private const val MAX_COMMENT_LENGTH = 8_000
    private const val MAX_CATEGORY_LENGTH = 80
    private const val MAX_BUNDLE_BYTES = 1_024 * 1_024
    private const val MAX_REPORT_FILES = 64
    private const val MAX_REPORT_BYTES = 50L * 1024L * 1024L
    private const val MAX_REPORT_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    private const val REPORT_CREATED = "report_created"
    private const val REPORT_CONTEXT = "report_context"
    private const val REPORT_PROMPT_VERSION = "daily-report-v1"

    fun create(context: Context, request: ReportRequest): CreatedReport {
        val appContext = context.applicationContext
        KpknDiagnosticLogger.initialize(appContext)
        val reportId = "rpt-${UUID.randomUUID()}"
        val directory = File(appContext.filesDir, DIRECTORY_NAME).apply { mkdirs() }
        pruneReportFiles(directory)
        val file = File(directory, "report-$reportId.jsonl")
        val snapshots = KpknDiagnosticLogger.officialAreas.associateWith { area ->
            KpknDiagnosticLogger.snapshotWithRefs(area, KpknDiagnosticLogger.MAX_CONTEXT_EVENTS)
        }
        val omittedByArea = KpknDiagnosticLogger.officialAreas.associateWith { area ->
            (
                KpknDiagnosticLogger.eventCount(area) -
                    snapshots[area].orEmpty().size
            ).coerceAtLeast(0)
        }
        val redactedComment = redactSecrets(request.comment).trim().take(MAX_COMMENT_LENGTH)
        val contextLines = snapshots.flatMap { (area, lines) ->
            lines.mapNotNull { source ->
                val event = runCatching { Json.parseToJsonElement(source.raw) }.getOrNull()
                    ?: return@mapNotNull null
                val sourceFile = evidencePath(source.file)
                reportJsonLine(
                    reportId = reportId,
                    event = REPORT_CONTEXT,
                    sessionId = request.sessionId,
                    screen = request.screen,
                    fields = mapOf(
                        "sourceArea" to JsonPrimitive(area),
                        "sourceFile" to JsonPrimitive(sourceFile),
                        "sourceLocalFile" to JsonPrimitive(source.file),
                        "sourceLine" to JsonPrimitive(source.line),
                        "sourceRef" to JsonPrimitive("$sourceFile#L${source.line}"),
                        "sourceEventId" to ((event as? JsonObject)?.get("eventId") ?: JsonNull),
                        "sourceEvent" to event,
                    ),
                )
            }
        }
        val contextText = contextLines.joinToString("\n")
        val contextHash = sha256(contextText)
        val lines = buildList {
            add(
                reportJsonLine(
                    reportId = reportId,
                    event = REPORT_CREATED,
                    sessionId = request.sessionId,
                    screen = request.screen,
                    fields = mapOf(
                        "origin" to JsonPrimitive(request.origin.name.lowercase()),
                        "userComment" to JsonPrimitive(redactedComment),
                        "commentRedacted" to JsonPrimitive(redactedComment != request.comment.trim()),
                        "category" to request.category?.trim()?.take(MAX_CATEGORY_LENGTH).toJsonElement(),
                        "reportScreen" to (request.screen ?: KpknDiagnosticLogger.currentScreen()).toJsonElement(),
                        "workoutSessionId" to request.sessionId.toJsonElement(),
                        "workoutId" to request.workoutId.toJsonElement(),
                        "contextEventCount" to JsonPrimitive(contextLines.size),
                        "contextOmittedByArea" to JsonObject(
                            omittedByArea.mapValues { (_, count) -> JsonPrimitive(count) },
                        ),
                        "contextTruncated" to JsonPrimitive(omittedByArea.values.any { it > 0 }),
                        "contextMaxEventsPerNamespace" to JsonPrimitive(KpknDiagnosticLogger.MAX_CONTEXT_EVENTS),
                        "contextMaxBundleBytes" to JsonPrimitive(MAX_BUNDLE_BYTES),
                        "contextHash" to JsonPrimitive(contextHash),
                        "audioStored" to JsonPrimitive(false),
                    ),
                ),
            )
            addAll(contextLines)
            add(
                reportJsonLine(
                    reportId = reportId,
                    event = "report_ai_pending",
                    sessionId = request.sessionId,
                    screen = request.screen,
                    fields = mapOf(
                        "model" to JsonPrimitive("deepseek-v4-flash"),
                        "contextHash" to JsonPrimitive(contextHash),
                    ),
                ),
            )
        }
        writeLines(file, lines)
        KpknDiagnosticStorage.mirrorRecoveryFiles(appContext)
        KpknDiagnosticLogger.event(
            namespace = KpknDiagnosticLogger.REPORT_NAMESPACE,
            name = "report_created",
            fields = mapOf(
                "reportId" to reportId,
                "origin" to request.origin.name.lowercase(),
                "contextEventCount" to contextLines.size,
                "contextHash" to contextHash,
            ),
            traceId = reportId,
            sessionId = request.sessionId,
            reportId = reportId,
        )
        return CreatedReport(reportId, file, contextHash)
    }

    fun appendAiEnrichment(
        context: Context,
        reportId: String,
        contextHash: String,
        payload: JsonObject,
        requestId: String? = null,
    ) = synchronized(writeLock) {
        val file = reportFile(context, reportId) ?: return@synchronized
        if (hasAiEnrichmentLocked(file)) return@synchronized
        val structuredPayload = JsonObject(
            payload + mapOf(
                "reportId" to JsonPrimitive(reportId),
                "model" to JsonPrimitive("deepseek-v4-flash"),
                "promptVersion" to JsonPrimitive(REPORT_PROMPT_VERSION),
                "contextHash" to JsonPrimitive(contextHash),
                "requestId" to requestId.toJsonElement(),
            ),
        )
        appendLine(
            file,
            reportJsonLine(
                reportId = reportId,
                event = "report_ai_enrichment",
                sessionId = null,
                screen = KpknDiagnosticLogger.currentScreen(),
                fields = mapOf(
                    "model" to JsonPrimitive("deepseek-v4-flash"),
                    "contextHash" to JsonPrimitive(contextHash),
                    "payload" to sanitizeJson(structuredPayload),
                ),
            ),
        )
        val markdown = File(file.parentFile, "report-$reportId.md")
        runCatching {
            markdown.writeText(renderMarkdown(reportId, structuredPayload), Charsets.UTF_8)
            KpknDiagnosticStorage.mirrorFile(
                context.applicationContext,
                markdown,
                "reports/${markdown.name}",
            )
        }
        KpknDiagnosticStorage.mirrorRecoveryFiles(context.applicationContext)
    }

    fun appendAiFailed(context: Context, reportId: String, contextHash: String?, code: String, retryable: Boolean) =
        synchronized(writeLock) {
            val file = reportFile(context, reportId) ?: return@synchronized
            appendLine(
                file,
                reportJsonLine(
                    reportId = reportId,
                    event = "report_ai_failed",
                    sessionId = null,
                    screen = KpknDiagnosticLogger.currentScreen(),
                    fields = mapOf(
                        "model" to JsonPrimitive("deepseek-v4-flash"),
                        "contextHash" to contextHash.toJsonElement(),
                        "code" to JsonPrimitive(redactSecrets(code).take(160)),
                        "retryable" to JsonPrimitive(retryable),
                    ),
                ),
            )
            val markdown = File(file.parentFile, "report-$reportId.md")
            runCatching {
                markdown.writeText(
                    "# Reporte $reportId\n\n## Estado\n\nEl análisis de IA falló: `${redactSecrets(code).take(160)}`.\n\nReintentable: `$retryable`.\n",
                    Charsets.UTF_8,
                )
                KpknDiagnosticStorage.mirrorFile(
                    context.applicationContext,
                    markdown,
                    "reports/${markdown.name}",
                )
            }
            KpknDiagnosticStorage.mirrorRecoveryFiles(context.applicationContext)
        }

    fun reportFile(context: Context, reportId: String): File? {
        val file = File(context.applicationContext.filesDir, "$DIRECTORY_NAME/report-$reportId.jsonl")
        return file.takeIf { it.isFile }
    }

    fun markdownFile(context: Context, reportId: String): File? =
        File(context.applicationContext.filesDir, "$DIRECTORY_NAME/report-$reportId.md")
            .takeIf(File::isFile)

    fun reportMarkdownFiles(context: Context): List<File> =
        File(context.applicationContext.filesDir, DIRECTORY_NAME)
            .listFiles { file -> file.isFile && file.extension == "md" }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()

    fun reportJsonlFiles(context: Context): List<File> =
        File(context.applicationContext.filesDir, DIRECTORY_NAME)
            .listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.sortedByDescending(File::lastModified)
            .orEmpty()

    fun readBundle(context: Context, reportId: String): String? {
        val file = reportFile(context, reportId) ?: return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return bytes.copyOf(minOf(bytes.size, MAX_BUNDLE_BYTES)).toString(Charsets.UTF_8)
    }

    fun contextHash(bundle: String): String = sha256(bundle)

    fun pendingReportIds(context: Context): List<String> {
        val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)
        return directory.listFiles { file -> file.isFile && file.name.startsWith("report-") && file.extension == "jsonl" }
            ?.mapNotNull { file ->
                val id = file.name.removePrefix("report-").removeSuffix(".jsonl")
                val text = runCatching { file.readText() }.getOrDefault("")
                id.takeIf { "\"event\":\"report_ai_enrichment\"" !in text && !("\"event\":\"report_ai_failed\"" in text && "\"retryable\":false" in text) }
            }
            .orEmpty()
    }

    private fun pruneReportFiles(directory: File) {
        val now = System.currentTimeMillis()
        val files = directory.listFiles { file ->
            file.isFile && file.name.startsWith("report-") && file.extension == "jsonl"
        }?.sortedByDescending(File::lastModified).orEmpty().toMutableList()
        files.filter { now - it.lastModified() > MAX_REPORT_AGE_MS }.forEach(File::delete)
        val remaining = files.filter(File::exists).sortedByDescending(File::lastModified).toMutableList()
        var total = remaining.sumOf(File::length)
        while (remaining.size > MAX_REPORT_FILES || total > MAX_REPORT_BYTES) {
            val oldest = remaining.removeLastOrNull() ?: break
            total -= oldest.length()
            oldest.delete()
        }
    }
    private fun writeLines(file: File, lines: List<String>) {
        FileOutputStream(file, false).use { output ->
            lines.forEach { line ->
                output.write(line.toByteArray(Charsets.UTF_8))
                output.write('\n'.code)
            }
            output.flush()
            runCatching { output.fd.sync() }
        }
    }

    private fun appendLine(file: File, line: String) {
        FileOutputStream(file, true).use { output ->
            output.write(line.toByteArray(Charsets.UTF_8))
            output.write('\n'.code)
            output.flush()
            runCatching { output.fd.sync() }
        }
    }

    private fun hasAiEnrichmentLocked(file: File): Boolean =
        runCatching {
            file.useLines { lines ->
                lines.any { line -> "\"event\":\"report_ai_enrichment\"" in line }
            }
        }.getOrDefault(false)

    private fun sanitizeJson(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (_, value) -> sanitizeJson(value) })
        is JsonArray -> JsonArray(element.map(::sanitizeJson))
        is JsonPrimitive -> if (element.isString) {
            JsonPrimitive(redactSecrets(element.content).take(MAX_TEXT_LENGTH))
        } else {
            element
        }
        else -> element
    }

    private const val MAX_TEXT_LENGTH = 12_000

    private fun reportJsonLine(
        reportId: String,
        event: String,
        sessionId: String?,
        screen: String?,
        fields: Map<String, JsonElement>,
    ): String {
        val resolvedSessionId = sessionId?.trim()?.takeIf(String::isNotBlank) ?: "report-$reportId"
        val base = linkedMapOf<String, JsonElement>(
            "schemaVersion" to JsonPrimitive(KpknDiagnosticLogger.SCHEMA_VERSION),
            "eventId" to JsonPrimitive(UUID.randomUUID().toString()),
            "timestamp" to JsonPrimitive(Instant.now().toString()),
            "elapsedMs" to JsonPrimitive(KpknDiagnosticLogger.currentElapsedMs()),
            "area" to JsonPrimitive("reports"),
            "subsystem" to JsonNull,
            "event" to JsonPrimitive(event),
            "screen" to JsonPrimitive(screen?.trim()?.takeIf(String::isNotBlank) ?: "reports"),
            "sessionId" to JsonPrimitive(resolvedSessionId),
            "traceId" to JsonPrimitive(reportId),
            "process" to JsonPrimitive("main"),
            "reportId" to JsonPrimitive(reportId),
        )
        fields.forEach { (key, value) ->
            if (key !in base) base[key] = sanitizeJson(value)
        }
        return JsonObject(base).toString()
    }

    private fun evidencePath(path: String): String {
        val normalized = path.replace('\\', '/')
        val local = normalized.removePrefix("$DIRECTORY_NAME/")
        return if (local.startsWith("reports/")) local else "logs/$local"
    }

    private fun String?.toJsonElement(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

    private fun redactSecrets(value: String): String = value
        .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._-]+"), "$1[REDACTED]")
        .replace(Regex("(?i)(api[_ -]?key\\s*[:=]\\s*)[^\\s]+"), "$1[REDACTED]")
        .replace(Regex("(?i)sk-[A-Za-z0-9_-]{12,}"), "[REDACTED]")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun renderMarkdown(reportId: String, payload: JsonObject): String {
        val model = payload["model"]?.toString()?.trim('"').orEmpty().ifBlank { "deepseek-v4-flash" }
        val summary = payload["summary"]?.toString()?.trim('"').orEmpty().ifBlank { "Sin resumen disponible." }
        val sections = buildString {
            appendLine("# Reporte $reportId")
            appendLine("> generado por $model · prompt $REPORT_PROMPT_VERSION")
            appendLine()
            appendLine("## Resumen")
            appendLine(summary)
            appendLine()
            appendJsonSection("Hechos", payload["facts"])
            appendJsonSection("Lo que reportaste", payload["userClaims"])
            appendJsonSection("Hipótesis", payload["hypotheses"])
            appendJsonSection("Inconsistencias detectadas", payload["inconsistencies"])
            appendJsonSection("Evidencia faltante", payload["missingEvidence"])
            appendJsonSection("Chequeos sugeridos", payload["suggestedChecks"] ?: payload["nextChecks"])
            appendEvidenceRefs(payload["evidenceRefs"])
        }
        return sections
    }

    private fun StringBuilder.appendJsonSection(title: String, value: JsonElement?) {
        appendLine("## $title")
        when (value) {
            is JsonArray -> value.forEach { item ->
                val text = when (item) {
                    is JsonObject -> item["text"]?.toString()?.trim('"').orEmpty()
                    else -> item.toString().trim('"')
                }
                val refs = (item as? JsonObject)?.get("evidenceRefs")?.let(::renderEvidenceRefs).orEmpty()
                if (text.isNotBlank()) appendLine("- $text${if (refs.isNotBlank()) " $refs" else ""}")
            }
            null -> appendLine("- Sin datos.")
            else -> appendLine("- ${value.toString().trim('"')}")
        }
        appendLine()
    }

    private fun StringBuilder.appendEvidenceRefs(value: JsonElement?) {
        appendLine("## Referencias físicas")
        val refs = renderEvidenceRefs(value)
        appendLine(if (refs.isNotBlank()) refs else "- Sin referencias.")
        appendLine()
    }

    private fun renderEvidenceRefs(value: JsonElement?): String = when (value) {
        is JsonArray -> value.mapNotNull { ref ->
            when (ref) {
                is JsonPrimitive -> ref.content.takeIf(String::isNotBlank)?.let { "[$it]" }
                is JsonObject -> {
                    val file = ref["file"]?.toString()?.trim('"')
                    val start = ref["startLine"]?.toString()?.toIntOrNull()
                    val end = ref["endLine"]?.toString()?.toIntOrNull() ?: start
                    if (!file.isNullOrBlank() && start != null) {
                        "[$file#L$start${if (end != null && end != start) "-L$end" else ""}]"
                    } else {
                        null
                    }
                }
                else -> null
            }
        }.joinToString(" ")
        is JsonPrimitive -> value.content.takeIf(String::isNotBlank)?.let { "[$it]" }.orEmpty()
        else -> ""
    }
}
