package com.example.kpkn.data.remote

import android.content.Context
import android.os.SystemClock
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.secure.DeepSeekCredentialStore
import com.example.kpkn.telemetry.nutrition.NutritionTelemetry
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DeepSeekJsonCompletion(
    val content: String,
    val requestId: String?,
    val model: String,
)

class DeepSeekClientException(
    val httpCode: Int?,
    message: String,
    val retryable: Boolean,
) : IOException(message)

/** Single outbound AI client. The model is deliberately not configurable. */
class DeepSeekV4FlashClient(
    private val context: Context,
    private val keyProvider: suspend () -> String? = { DeepSeekCredentialStore.read(context) },
) {
    companion object {
        const val MODEL = "deepseek-v4-flash"
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        private const val CONNECT_TIMEOUT_SECONDS = 8L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 8L
        private const val CALL_TIMEOUT_SECONDS = 40L
        private const val MAX_KNOWN_FOOD_HINTS = 24
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeNutrition(request: AiNutritionRequest): Result<AiNutritionResult> {
        val startedAtMs = SystemClock.elapsedRealtime()
        val knownFoods = request.knownFoods.asSequence().map(String::trim).filter(String::isNotBlank).distinct().take(MAX_KNOWN_FOOD_HINTS).toList()
        val knownBlock = knownFoods.takeIf { it.isNotEmpty() }?.let { "\nNombres conocidos: ${it.joinToString(", ")}" }.orEmpty()
        val hintsBlock = request.userHints.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            .takeIf { it.isNotBlank() }
            ?.let { "\nDatos explícitos por 100g:\n$it" }
            .orEmpty()
        val system = """
            Eres un nutricionista experto. Devuelve exclusivamente JSON válido.
            No inventes alimentos que no aparezcan en la descripción. Si una cantidad no está clara,
            estima una porción razonable y marca reviewRequired=true.
            Esquema: {"items":[{"rawText":"","canonicalName":"","grams":0,"quantity":1,"preparation":null,"confidence":0.0,"nutritionPer100g":{"calories":0,"protein":0,"carbs":0,"fats":0},"reviewRequired":false}],"overallConfidence":0.0,"usedModel":true,"modelVersion":"deepseek-v4-flash"}
        """.trimIndent()
        val user = "Descripción del usuario: ${request.description}$hintsBlock$knownBlock\nDevuelve solo el JSON final."
        val result = completeJson(system, user, 1536, operation = "nutrition").mapCatching { completion ->
            parseNutrition(completion.content).copy(
                elapsedMs = 0,
                modelVersion = MODEL,
                usedModel = true,
            )
        }
        // NutriTelemetry: latencia y resultado de la llamada externa (jamás la API key).
        NutritionTelemetry.event(
            "api_call",
            mapOf(
                "provider" to "deepseek",
                "operation" to "nutrition",
                "durationMs" to SystemClock.elapsedRealtime() - startedAtMs,
                "ok" to result.isSuccess,
                "httpCode" to (result.exceptionOrNull() as? DeepSeekClientException)?.httpCode,
                "errorType" to result.exceptionOrNull()?.javaClass?.simpleName,
                "items" to result.getOrNull()?.items?.size,
                "overallConfidence" to result.getOrNull()?.overallConfidence,
            ),
        )
        return result
    }

    suspend fun completeJson(systemPrompt: String, userPrompt: String, maxTokens: Int = 2048, operation: String = "report_enrichment"): Result<DeepSeekJsonCompletion> {
        val key = keyProvider()?.trim().orEmpty()
        if (key.isBlank()) {
            KpknDiagnosticLogger.event(
                "backend",
                "deepseek_request_failed",
                mapOf("operation" to operation, "reason" to "missing_key"),
            )
            return Result.failure(DeepSeekClientException(null, "deepseek_key_missing", false))
        }
        val startedAt = SystemClock.elapsedRealtime()
        KpknDiagnosticLogger.event(
            "backend",
            "deepseek_request_started",
            mapOf(
                "operation" to operation,
                "model" to MODEL,
                "promptChars" to (systemPrompt.length + userPrompt.length),
                "maxTokens" to maxTokens.coerceIn(256, 8192),
            ),
        )
        return runCatching {
            val body = JSONObject()
                .put("model", MODEL)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)))
                .put("temperature", 0.2)
                .put("max_tokens", maxTokens.coerceIn(256, 8192))
                .put("response_format", JSONObject().put("type", "json_object"))
                .toString()
            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val responseBody = execute(request)
            val root = JSONObject(responseBody)
            val responseModel = root.optString("model").trim()
            if (responseModel.isNotBlank() && responseModel != MODEL) {
                throw DeepSeekClientException(null, "deepseek_unexpected_model", false)
            }
            val choice = root.optJSONArray("choices")?.optJSONObject(0)
                ?: throw DeepSeekClientException(null, "deepseek_missing_choice", false)
            val content = choice.optJSONObject("message")?.optString("content")?.trim().orEmpty()
            if (content.isBlank()) throw DeepSeekClientException(null, "deepseek_empty_content", false)
            DeepSeekJsonCompletion(content, root.optString("id").takeIf(String::isNotBlank), root.optString("model", MODEL))
        }.onSuccess { completion ->
            KpknDiagnosticLogger.event(
                "backend",
                "deepseek_request_succeeded",
                mapOf(
                    "operation" to operation,
                    "model" to MODEL,
                    "requestId" to completion.requestId,
                    "latencyMs" to (SystemClock.elapsedRealtime() - startedAt),
                ),
            )
        }.onFailure { error ->
            KpknDiagnosticLogger.event(
                "backend",
                "deepseek_request_failed",
                mapOf(
                    "operation" to operation,
                    "httpCode" to (error as? DeepSeekClientException)?.httpCode,
                    "errorType" to error.javaClass.simpleName,
                    "retryable" to (error as? DeepSeekClientException)?.retryable,
                    "latencyMs" to (SystemClock.elapsedRealtime() - startedAt),
                ),
            )
        }
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, error: IOException) {
                if (!continuation.isActive) return
                continuation.resumeWithException(if (error is InterruptedIOException) IOException("deepseek_timeout", error) else error)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    response.use { current ->
                        val text = current.body?.string().orEmpty()
                        if (!current.isSuccessful || text.isBlank()) {
                            val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                                ?.takeIf(String::isNotBlank) ?: "deepseek_http_${current.code}"
                            throw DeepSeekClientException(current.code, message.take(180), current.code == 429 || current.code >= 500)
                        }
                        if (continuation.isActive) continuation.resume(text)
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private fun parseNutrition(content: String): AiNutritionResult {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return AiNutritionResult(failureReason = "deepseek_invalid_json")
        val root = JSONObject(content.substring(start, end + 1))
        val items = buildList {
            val array = root.optJSONArray("items") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("canonicalName").ifBlank {
                    item.optString("name").ifBlank { item.optString("food") }
                }
                if (name.isBlank()) continue
                val nutrition = item.optJSONObject("nutritionPer100g") ?: item.optJSONObject("nutrition")
                add(
                    AiNutritionItem(
                        rawText = item.optString("rawText", name),
                        canonicalName = name,
                        grams = item.optDoubleOrNull("grams"),
                        quantity = item.optIntOrNull("quantity"),
                        preparation = item.optString("preparation").takeIf(String::isNotBlank),
                        confidence = item.optDouble("confidence", 0.5).coerceIn(0.0, 1.0),
                        nutritionPer100g = nutrition?.let {
                            NutritionPer100g(
                                calories = it.optDouble("calories", 0.0),
                                protein = it.optDouble("protein", 0.0),
                                carbs = it.optDouble("carbs", 0.0),
                                fats = it.optDouble("fats", 0.0),
                            )
                        },
                        reviewRequired = item.optBoolean("reviewRequired", false),
                    ),
                )
            }
        }
        return AiNutritionResult(
            items = items,
            overallConfidence = root.optDouble("overallConfidence", items.map { it.confidence }.average().takeUnless(Double::isNaN) ?: 0.0),
            usedModel = true,
            modelVersion = MODEL,
        )
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name)
}
