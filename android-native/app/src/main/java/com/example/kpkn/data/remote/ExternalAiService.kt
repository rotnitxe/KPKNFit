package com.example.kpkn.data.remote

import com.example.kpkn.data.models.ApiProvider
import com.example.kpkn.data.models.ApiKeys
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

/**
 * Servicio para llamadas a APIs externas de IA (Gemini y OpenAI)
 * Maneja el fallback automático cuando no hay internet o las llamadas fallan
 */
class ExternalAiService(
    private val apiKeys: ApiKeys,
    private val apiProvider: ApiProvider
) {
    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 8L
        private const val READ_TIMEOUT_SECONDS = 18L
        private const val WRITE_TIMEOUT_SECONDS = 8L
        private const val CALL_TIMEOUT_SECONDS = 20L
        private const val MAX_KNOWN_FOOD_HINTS = 24
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Intenta analizar nutrición usando API externa
     * @return Result con respuesta o null si falla
     */
    suspend fun analyzeNutrition(request: AiNutritionRequest): Result<AiNutritionResult> {
        val apiKey = when (apiProvider) {
            ApiProvider.GEMINI -> apiKeys.gemini
            ApiProvider.GPT -> apiKeys.gpt
            ApiProvider.DEEPSEEK -> apiKeys.deepseek
        }

        if (apiKey.isNullOrBlank()) {
            return Result.failure(Exception("API key not configured for $apiProvider"))
        }

        return try {
            when (apiProvider) {
                ApiProvider.GEMINI -> analyzeWithGemini(request, apiKey)
                ApiProvider.GPT -> analyzeWithGPT(request, apiKey)
                ApiProvider.DEEPSEEK -> analyzeWithDeepSeek(request, apiKey)
            }
        } catch (e: IOException) {
            // Error de red - devolver failure para que el llamante sepa que debe fallback
            Result.failure(e)
        } catch (e: Exception) {
            // Otros errores (API key inválida, etc.)
            Result.failure(e)
        }
    }

    private suspend fun analyzeWithGemini(request: AiNutritionRequest, apiKey: String): Result<AiNutritionResult> {
        val prompt = buildGeminiPrompt(request)

        val requestBody = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.1)
                .put("maxOutputTokens", 1536)
                .put("responseMimeType", "application/json")
            )
            .toString()

        val httpRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = executeJsonRequest(httpRequest)
        return Result.success(parseGeminiResponse(responseBody))
    }

    private suspend fun analyzeWithGPT(request: AiNutritionRequest, apiKey: String): Result<AiNutritionResult> {
        val requestBody = buildChatCompletionBody(
            model = "gpt-4o-mini",
            systemPrompt = buildStructuredNutritionSystemPrompt(),
            userPrompt = buildStructuredNutritionUserPrompt(request),
            includeResponseFormat = true,
        )

        val httpRequest = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = executeJsonRequest(httpRequest)
        return Result.success(parseGPTResponse(responseBody))
    }

    private suspend fun analyzeWithDeepSeek(request: AiNutritionRequest, apiKey: String): Result<AiNutritionResult> {
        val requestBody = buildChatCompletionBody(
            model = "deepseek-chat",
            systemPrompt = buildDeepSeekSystemPrompt(),
            userPrompt = buildStructuredNutritionUserPrompt(request),
            includeResponseFormat = true,
        )

        val httpRequest = Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = executeJsonRequest(httpRequest)
        return Result.success(parseGPTResponse(responseBody))
    }

    private fun buildGeminiPrompt(request: AiNutritionRequest): String {
        return buildString {
            append(buildStructuredNutritionSystemPrompt())
            append("\n\n")
            append(buildStructuredNutritionUserPrompt(request))
        }
    }

    private fun buildStructuredNutritionSystemPrompt(): String {
        return """
        Eres un nutricionista experto.
        Tu tarea es analizar descripciones de alimentos y devolver exclusivamente un objeto JSON válido, sin texto adicional ni comentarios.
        Usa USDA o información nutricional estándar.
        Si una cantidad no está clara, estima una porción típica realista, conviértela a gramos cuando puedas y marca reviewRequired=true.
        Convierte medidas caseras a gramos cuando sea razonable (1 taza de arroz cocido = 200 g; 1 cda de aceite = 15 ml).
        No inventes alimentos que no aparezcan en la descripción.

        Debes responder con este esquema exacto:
        {
          "items": [
            {
              "rawText": "fragmento original",
              "canonicalName": "nombre del alimento",
              "grams": 200,
              "quantity": 1,
              "preparation": "cocido|plancha|horno|frito|crudo|null",
              "confidence": 0.84,
              "nutritionPer100g": {
                "calories": 130,
                "protein": 2.7,
                "carbs": 28.2,
                "fats": 0.3
              },
              "reviewRequired": false
            }
          ],
          "overallConfidence": 0.84,
          "usedModel": true,
          "modelVersion": "external-api"
        }

        Si no puedes resolver nada, devuelve {"items":[],"overallConfidence":0.0,"usedModel":true,"modelVersion":"external-api"}.
        """.trimIndent()
    }

    private fun buildDeepSeekSystemPrompt(): String {
        return buildStructuredNutritionSystemPrompt() + "\nResponde exclusivamente con un JSON válido; usa response_format=json_object."
    }

    private fun buildStructuredNutritionUserPrompt(request: AiNutritionRequest): String {
        val knownFoods = request.knownFoods
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_KNOWN_FOOD_HINTS)
            .toList()

        val userHintsBlock = if (request.userHints.isEmpty()) {
            ""
        } else {
            buildString {
                append("\n\nDatos nutricionales explícitos dados por el usuario por 100g (respétalos exactamente si aplican):")
                request.userHints.forEach { (key, value) ->
                    append("\n- $key: $value")
                }
            }
        }

        val knownFoodsBlock = if (knownFoods.isEmpty()) {
            ""
        } else {
            "\n\nNombres conocidos a priorizar si coinciden con la descripción: ${knownFoods.joinToString(", ")}"
        }

        return "Descripción del usuario: ${request.description}$userHintsBlock$knownFoodsBlock\n\nDevuelve solo el JSON final."
    }

    private fun buildChatCompletionBody(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        includeResponseFormat: Boolean,
    ): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userPrompt))

        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.1)
            .put("max_tokens", 1024)
            .apply {
                if (includeResponseFormat) {
                    put("response_format", JSONObject().put("type", "json_object"))
                }
            }
            .toString()
    }

    private suspend fun executeJsonRequest(httpRequest: Request): String {
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(httpRequest)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (!continuation.isActive) return
                    val normalized = if (e is InterruptedIOException) {
                        IOException("request-timeout", e)
                    } else {
                        e
                    }
                    continuation.resumeWithException(normalized)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        response.use { httpResponse ->
                            val responseBody = httpResponse.body?.string()
                            if (!httpResponse.isSuccessful || responseBody.isNullOrBlank()) {
                                val errorMessage = extractApiErrorMessage(responseBody)
                                throw IOException(
                                    buildString {
                                        append("API call failed: ")
                                        append(httpResponse.code)
                                        if (!errorMessage.isNullOrBlank()) {
                                            append(" - ")
                                            append(errorMessage)
                                        }
                                    }
                                )
                            }

                            if (!continuation.isActive) return
                            continuation.resume(responseBody)
                        }
                    } catch (e: Exception) {
                        if (!continuation.isActive) return
                        continuation.resumeWithException(
                            if (e is IOException) e else IOException(e.message ?: "API response parsing failed", e)
                        )
                    }
                }
            })
        }
    }

    private fun extractApiErrorMessage(responseBody: String?): String? {
        if (responseBody.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(responseBody)
            root.optJSONObject("error")?.optString("message")
                ?: root.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: responseBody.take(180)
    }

    private fun parseGeminiResponse(responseBody: String): AiNutritionResult {
        try {
            val generatedText = extractGeminiText(responseBody) ?: return AiNutritionResult()
            return parseNutritionJson(generatedText)
        } catch (e: Exception) {
            throw Exception("Failed to parse Gemini response: ${e.message}")
        }
    }

    private fun parseGPTResponse(responseBody: String): AiNutritionResult {
        try {
            val generatedText = extractChatCompletionText(responseBody) ?: return AiNutritionResult()
            return parseNutritionJson(generatedText)
        } catch (e: Exception) {
            throw Exception("Failed to parse GPT response: ${e.message}")
        }
    }

    private fun parseNutritionJson(jsonContent: String): AiNutritionResult {
        val jsonBlock = extractJsonBlock(jsonContent) ?: return AiNutritionResult()
        val root = JSONObject(jsonBlock)
        val items = root.optJSONArray("items").toNutritionItems()
        val overallConfidence = root.optNullableDouble("overallConfidence")
            ?: if (items.isNotEmpty()) items.map { it.confidence }.average() else 0.0

        return AiNutritionResult(
            items = items,
            overallConfidence = overallConfidence,
            usedModel = root.optNullableBoolean("usedModel") ?: items.isNotEmpty(),
            modelVersion = root.optString("modelVersion").takeIf { it.isNotBlank() } ?: "external-api",
        )
    }

    private fun parseNutritionItem(obj: JSONObject): AiNutritionItem? {
        val canonicalName = obj.optNullableString("canonicalName")
            ?: obj.optNullableString("name")
            ?: obj.optNullableString("food")
            ?: obj.optNullableString("alimento")
            ?: return null

        val estimatedAmount = obj.optNullableString("cantidad_estimada")
            ?: obj.optNullableString("estimatedAmount")

        val grams = obj.optNullableDouble("grams")
            ?: parseAmountGrams(estimatedAmount)
        val quantity = obj.optNullableInt("quantity")
            ?: parseAmountQuantity(estimatedAmount)
            ?: 1
        val directNutrition = parseFlatNutrition(obj)
        val usesDirectTotals = obj.has("cantidad_estimada") || obj.has("alimento") || obj.has("calorias") || obj.has("proteinas_g")

        val nutritionObj = obj.optJSONObject("nutritionPer100g")
            ?: obj.optJSONObject("nutrition")
            ?: obj.optJSONObject("macros")

        val nutrition = nutritionObj?.let {
            NutritionPer100g(
                calories = it.optNullableDouble("calories") ?: it.optNullableDouble("kcal") ?: it.optNullableDouble("energy") ?: 0.0,
                protein = it.optNullableDouble("protein") ?: it.optNullableDouble("proteins") ?: it.optNullableDouble("p") ?: 0.0,
                carbs = it.optNullableDouble("carbs") ?: it.optNullableDouble("carbohydrates") ?: it.optNullableDouble("c") ?: 0.0,
                fats = it.optNullableDouble("fats") ?: it.optNullableDouble("fat") ?: it.optNullableDouble("f") ?: 0.0,
            )
        } ?: when {
            directNutrition != null && usesDirectTotals && grams != null && grams > 0.0 -> {
                val factor = grams / 100.0
                NutritionPer100g(
                    calories = if (factor > 0.0) directNutrition.calories / factor else directNutrition.calories,
                    protein = if (factor > 0.0) directNutrition.protein / factor else directNutrition.protein,
                    carbs = if (factor > 0.0) directNutrition.carbs / factor else directNutrition.carbs,
                    fats = if (factor > 0.0) directNutrition.fats / factor else directNutrition.fats,
                )
            }
            else -> directNutrition
        }

        return AiNutritionItem(
            rawText = obj.optNullableString("rawText") ?: canonicalName,
            canonicalName = canonicalName,
            grams = grams,
            quantity = quantity,
            preparation = obj.optNullableString("preparation") ?: obj.optNullableString("coccion"),
            confidence = obj.optNullableDouble("confidence") ?: obj.optNullableDouble("confianza") ?: 0.7,
            nutritionPer100g = nutrition,
            reviewRequired = obj.optNullableBoolean("reviewRequired")
                ?: obj.optNullableBoolean("requiereRevision")
                ?: (estimatedAmount != null && grams == null),
        )
    }

    private fun parseFlatNutrition(obj: JSONObject): NutritionPer100g? {
        val calories = obj.optNullableDouble("calories")
            ?: obj.optNullableDouble("calorias")
            ?: obj.optNullableDouble("kcal")
            ?: obj.optNullableDouble("energy")
        val protein = obj.optNullableDouble("protein")
            ?: obj.optNullableDouble("proteins")
            ?: obj.optNullableDouble("proteinas_g")
            ?: obj.optNullableDouble("p")
        val carbs = obj.optNullableDouble("carbs")
            ?: obj.optNullableDouble("carbohydrates")
            ?: obj.optNullableDouble("carbohidratos_g")
            ?: obj.optNullableDouble("c")
        val fats = obj.optNullableDouble("fats")
            ?: obj.optNullableDouble("fat")
            ?: obj.optNullableDouble("grasas_g")
            ?: obj.optNullableDouble("f")

        if (calories == null && protein == null && carbs == null && fats == null) return null

        return NutritionPer100g(
            calories = calories ?: 0.0,
            protein = protein ?: 0.0,
            carbs = carbs ?: 0.0,
            fats = fats ?: 0.0,
        )
    }

    private fun parseAmountGrams(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val match = Regex("""(\d+(?:[.,]\d+)?)\s*(g|gr|gramos?|ml)\b""", RegexOption.IGNORE_CASE).find(value)
            ?: return null
        return match.groupValues[1].replace(',', '.').toDoubleOrNull()
    }

    private fun parseAmountQuantity(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val match = Regex("""(\d+)""").find(value) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun extractGeminiText(responseBody: String): String? {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val parts = candidates.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return null

        val combined = buildString {
            for (index in 0 until parts.length()) {
                append(parts.optJSONObject(index)?.optString("text").orEmpty())
            }
        }
        return combined.ifBlank { null }
    }

    private fun extractChatCompletionText(responseBody: String): String? {
        val root = JSONObject(responseBody)
        val choices = root.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val message = choices.optJSONObject(0)?.optJSONObject("message") ?: return null

        val rawContent = message.opt("content") ?: return null
        return when (rawContent) {
            is String -> rawContent.takeIf { it.isNotBlank() }
            is JSONArray -> buildString {
                for (index in 0 until rawContent.length()) {
                    append(rawContent.optJSONObject(index)?.optString("text").orEmpty())
                }
            }.ifBlank { null }
            else -> null
        }
    }

    private fun extractJsonBlock(content: String): String? {
        val stripped = content
            .replace(Regex("^```(?:json)?\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("```\\s*$", RegexOption.MULTILINE), "")

        val start = stripped.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until stripped.length) {
            val current = stripped[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (current == '\\' && inString) {
                escaped = true
                continue
            }
            if (current == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            when (current) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return stripped.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun JSONArray?.toNutritionItems(): List<AiNutritionItem> {
        if (this == null) return emptyList()
        val result = mutableListOf<AiNutritionItem>()
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::parseNutritionItem)?.let(result::add)
        }
        return result
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optString(key).replace(',', '.').toDoubleOrNull()
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optString(key).toIntOrNull()
    }

    private fun JSONObject.optNullableBoolean(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }
    }
}
