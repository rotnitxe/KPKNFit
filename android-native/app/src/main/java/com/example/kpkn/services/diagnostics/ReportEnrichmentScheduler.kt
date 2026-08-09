package com.example.kpkn.services.diagnostics

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.remote.DeepSeekClientException
import com.example.kpkn.data.remote.DeepSeekV4FlashClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val REPORT_ID_INPUT = "reportId"
private const val MAX_WORK_ATTEMPTS = 3

/**
 * Durable scheduler for report analysis. WorkManager owns retry/resume across
 * process death and only runs when a network is connected.
 */
object ReportEnrichmentScheduler {
    fun enqueue(context: Context, reportId: String) {
        val appContext = context.applicationContext
        val request = OneTimeWorkRequestBuilder<ReportEnrichmentWorker>()
            .setInputData(workDataOf(REPORT_ID_INPUT to reportId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS,
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "kpkn-report-ai-" + reportId,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun resumePending(context: Context) {
        KpknReportManager.pendingReportIds(context).forEach { reportId ->
            enqueue(context, reportId)
        }
    }
}

class ReportEnrichmentWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): ListenableWorker.Result {
        val reportId = inputData.getString(REPORT_ID_INPUT)?.trim().orEmpty()
        if (reportId.isBlank()) return ListenableWorker.Result.failure()
        return ReportEnrichmentProcessor.run(
            context = applicationContext,
            reportId = reportId,
            attempt = runAttemptCount,
        )
    }
}

private object ReportEnrichmentProcessor {
    private const val SYSTEM_PROMPT = """
        Eres el analista de incidencias de KPKN Fit. Contrato exacto: daily-report-v1.
        Devuelve exclusivamente un objeto JSON válido, sin markdown, comentarios ni texto adicional.
        Usa solo el comentario del usuario y los eventos JSONL incluidos en el bundle.
        No presentes hipótesis como hechos y no inventes eventos, timestamps, archivos o referencias.
        El objeto debe incluir: summary, area, severity, facts, userClaims, hypotheses, timeline,
        missingEvidence, nextChecks, tags, confidence y evidenceRefs.
        Cada hecho, hipótesis o elemento de timeline debe llevar evidenceRefs cuando exista evidencia.
        Cada referencia debe ser una cadena con formato exacto logs/<area>/<yyyyMMdd>/<archivo>.jsonl#L<n>
        o logs/<area>/<yyyyMMdd>/<archivo>.jsonl#L<n>-L<m>, usando únicamente sourceRef del bundle.
        Si no hay evidencia suficiente, usa una lista vacía y escríbelo en missingEvidence.
        No ejecutes acciones ni propongas modificar o borrar datos.
    """

    suspend fun run(
        context: Context,
        reportId: String,
        attempt: Int,
    ): ListenableWorker.Result {
        val bundle = KpknReportManager.readBundle(context, reportId)
        if (bundle == null) {
            KpknReportManager.appendAiFailed(
                context = context,
                reportId = reportId,
                contextHash = null,
                code = "report_bundle_missing",
                retryable = false,
            )
            KpknDiagnosticNotificationManager.reportFailed(
                context,
                reportId,
                retryable = false,
                code = "report_bundle_missing",
            )
            return ListenableWorker.Result.failure()
        }
        val contextHash = KpknReportManager.contextHash(bundle)
        val userPrompt = "Reporte JSONL completo, redactado y acotado:" + bundle
        return try {
            val result = withContext(Dispatchers.IO) {
                DeepSeekV4FlashClient(context).completeJson(
                    systemPrompt = SYSTEM_PROMPT.trimIndent(),
                    userPrompt = userPrompt,
                    maxTokens = 4096,
                ).getOrThrow()
            }
            val payload = parsePayload(result.content)
            KpknReportManager.appendAiEnrichment(
                context = context,
                reportId = reportId,
                contextHash = contextHash,
                payload = payload,
                requestId = result.requestId,
            )
            KpknDiagnosticNotificationManager.reportCompleted(
                context,
                reportId,
                payload["summary"]?.toString()?.trim('"'),
            )
            ListenableWorker.Result.success()
        } catch (error: Throwable) {
            val retryable = when (error) {
                is DeepSeekClientException -> error.retryable
                is IOException -> true
                else -> false
            }
            val shouldRetry = retryable && attempt + 1 < MAX_WORK_ATTEMPTS
            if (shouldRetry) {
                KpknDiagnosticLogger.event(
                    "backend",
                    "report_ai_retry_scheduled",
                    mapOf(
                        "reportId" to reportId,
                        "attempt" to attempt + 1,
                        "retryable" to retryable,
                    ),
                    reportId = reportId,
                )
                ListenableWorker.Result.retry()
            } else {
                KpknReportManager.appendAiFailed(
                    context = context,
                    reportId = reportId,
                    contextHash = contextHash,
                    code = safeErrorCode(error),
                    retryable = retryable,
                )
                KpknDiagnosticNotificationManager.reportFailed(
                    context,
                    reportId,
                    retryable = retryable,
                    code = safeErrorCode(error),
                )
                ListenableWorker.Result.failure()
            }
        }
    }

    private fun parsePayload(content: String): JsonObject {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        require(start >= 0 && end > start) { "deepseek_invalid_json" }
        val root = Json.parseToJsonElement(content.substring(start, end + 1))
        require(root is JsonObject) { "deepseek_response_not_object" }
        return root
    }

    private fun safeErrorCode(error: Throwable): String =
        (error.message ?: error.javaClass.simpleName)
            .replace(Regex("\\s+"), "_")
            .take(160)
}
