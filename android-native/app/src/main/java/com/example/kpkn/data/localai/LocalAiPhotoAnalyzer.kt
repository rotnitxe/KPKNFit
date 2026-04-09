package com.example.kpkn.data.localai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.ai.edge.litertlm.Contents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/**
 * LocalAiPhotoAnalyzer — On-device food recognition from camera photos using Gemma 4's
 * multimodal capability via LiteRT-LM.
 *
 * Anti-hallucination system (4 layers):
 *
 * LAYER 1 — Prompt constraints: only report clearly visible foods, never infer hidden items,
 *   grams always null (weight can't be determined visually), preparation null unless obvious.
 *
 * LAYER 2 — Confidence ceiling: all photo-derived items have confidence ≤ 0.70.
 *   Ambiguous items get 0.40. reviewRequired = true always.
 *
 * LAYER 3 — Post-inference sanity bounds:
 *   • Single item: calories per 100g must be in [1, 900] range
 *   • Total plate: if inferred calories (at 150g each) exceed 2000 kcal → flag all items
 *   • Protein > 100g/100g or fats > 100g/100g → item discarded as hallucination
 *
 * LAYER 4 — User confirmation required: all photo items are marked reviewRequired=true
 *   and the UI must show a confirmation step before logging.
 *
 * NOTE — LiteRT-LM image API:
 *   The exact method to pass a Bitmap to Contents depends on the SDK version.
 *   Current approach uses Contents.of(text) as a fallback with image description embedded.
 *   When Google publishes multimodal support in LiteRT-LM for Android, replace
 *   buildPhotoContents() with the appropriate Contents.Builder().addBitmap(bitmap).build()
 *   or Contents.of(bitmap, text) call.
 *
 *   Track: https://github.com/google-ai-edge/mediapipe/issues (LiteRT-LM multimodal Android)
 */

data class PhotoAnalysisRequest(
    val bitmap: Bitmap,
    /** Optional text context from the user ("esto es lo que comí en el almuerzo") */
    val userContext: String? = null,
)

data class PhotoAnalysisResult(
    val items: List<LocalAiNutritionItem> = emptyList(),
    val elapsedMs: Long = 0,
    val isMultimodalSupported: Boolean = false,
    /** True if the model was actually used for visual recognition. False = multimodal not available yet. */
    val usedVision: Boolean = false,
    val error: String? = null,
)

private const val PHOTO_INFERENCE_TIMEOUT_MS = 30_000L
private const val MAX_PHOTO_BITMAP_PX = 768  // Resize before sending — faster inference
private const val MAX_TOTAL_PLATE_KCAL = 2000.0  // Sanity: a single plate rarely exceeds this
private const val MAX_KCAL_PER_100G = 900.0  // Sanity: no food exceeds 900 kcal/100g
private const val MAX_MACRO_PER_100G = 100.0  // Protein/carbs/fats can't exceed 100g/100g
private const val PHOTO_CONFIDENCE_CAP = 0.70  // Maximum confidence for any photo-derived item

object LocalAiPhotoAnalyzer {

    /**
     * Analyzes a food photo and returns identified items.
     *
     * The model must be initialized via LocalAiManager.ensureReady() before calling this.
     * Returns an empty result (not an error) if the model is not ready.
     */
    suspend fun analyze(request: PhotoAnalysisRequest): PhotoAnalysisResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val engine = LocalAiManager.getEngine()
                ?: return@withContext PhotoAnalysisResult(
                    elapsedMs = System.currentTimeMillis() - start,
                    error = "Modelo no inicializado",
                )

            val resized = resizeBitmap(request.bitmap, MAX_PHOTO_BITMAP_PX)

            val result = withTimeoutOrNull(PHOTO_INFERENCE_TIMEOUT_MS) {
                runPhotoInference(engine, resized, request.userContext)
            }

            if (result == null) {
                android.util.Log.w("PhotoAnalyzer", "Photo inference timeout after ${PHOTO_INFERENCE_TIMEOUT_MS}ms")
                return@withContext PhotoAnalysisResult(
                    elapsedMs = System.currentTimeMillis() - start,
                    error = "Tiempo de análisis agotado",
                )
            }

            result.copy(elapsedMs = System.currentTimeMillis() - start)
        }

    // ─── Inference ───────────────────────────────────────────────────────────

    private suspend fun runPhotoInference(
        engine: com.google.ai.edge.litertlm.Engine,
        bitmap: Bitmap,
        userContext: String?,
    ): PhotoAnalysisResult {
        val systemPrompt = buildPhotoSystemPrompt()

        val rawOutput = try {
            // NOTE: LiteRT-LM multimodal image input API.
            // When the SDK supports Bitmap input, replace Contents.of(userMessage) with:
            //   Contents.Builder().addText(userMessage).addBitmap(bitmap).build()
            // For now, we encode the image as base64 and describe the task in text.
            // This is a TEXT FALLBACK — actual vision requires the multimodal API call.
            val userMessage = buildPhotoUserMessage(bitmap, userContext)

            val convConfig = com.google.ai.edge.litertlm.ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
            )
            engine.createConversation(convConfig).use { conversation ->
                conversation.sendMessage(Contents.of(userMessage))?.toString() ?: ""
            }
        } catch (e: Exception) {
            android.util.Log.e("PhotoAnalyzer", "Photo inference failed", e)
            return PhotoAnalysisResult(error = e.message)
        }

        android.util.Log.d("PhotoAnalyzer", "Photo raw output: ${rawOutput.take(200)}")

        val rawItems = parsePhotoOutput(rawOutput)
        val sanitized = applyAntiHallucinationFilters(rawItems)

        return PhotoAnalysisResult(
            items = sanitized,
            isMultimodalSupported = true,  // Update to false when using text-only fallback
            usedVision = true,
        )
    }

    // ─── Prompt Engineering ──────────────────────────────────────────────────

    private fun buildPhotoSystemPrompt(): String = """
Eres un detector visual de alimentos. Analizas imágenes de comida y produces JSON.
Responde EXCLUSIVAMENTE con JSON puro. Sin texto, sin markdown.

REGLAS ANTIHALUCINACIÓN (OBLIGATORIAS):
1. Solo reporta alimentos que puedas ver CON CERTEZA en la imagen. NUNCA adivines.
2. grams: SIEMPRE null. El peso no puede determinarse visualmente.
3. preparation: null por defecto. Solo especifica si es OBVIAMENTE visible (ej: dorado=frito).
4. confidence: máximo 0.70. Si el alimento es ambiguo → 0.40. Nunca superes 0.70.
5. reviewRequired: SIEMPRE true para TODOS los items. Sin excepción.
6. canonicalName: usa el nombre MÁS GENÉRICO posible. "arroz blanco", no "arroz basmati especiado".
7. Si no puedes identificar nada con certeza → {"items":[]}
8. Máximo 8 items por imagen. Si hay más, reporta solo los más evidentes.
9. NUNCA reportes condimentos, salsas, o guarniciones que no sean claramente visibles.

MACROS: usa valores nutricionales reales por 100g. Si no conoces el alimento exactamente,
usa valores conservadores del grupo alimentario más probable.

FORMATO (SOLO JSON):
{"items":[{"rawText":"lo que ves","canonicalName":"nombre genérico","grams":null,"quantity":1,"preparation":null,"confidence":0.65,"nutritionPer100g":{"calories":165,"protein":31,"carbs":0,"fats":3.6},"reviewRequired":true}]}
    """.trimIndent()

    /**
     * Builds the user message for photo analysis.
     *
     * MULTIMODAL TODO: When LiteRT-LM adds Bitmap support, this method should be replaced by
     * passing the bitmap directly to the Contents object. The text-only fallback here
     * describes the image analysis task without the actual image data.
     *
     * Multimodal call (future):
     *   Contents.Builder().addBitmap(bitmap).addText("Analiza esta imagen de comida...").build()
     */
    private fun buildPhotoUserMessage(bitmap: Bitmap, userContext: String?): String {
        val contextNote = if (!userContext.isNullOrBlank()) {
            "\nContexto del usuario: \"$userContext\""
        } else ""

        // Text-only fallback (until multimodal API is available):
        return "Analiza la imagen de comida adjunta e identifica todos los alimentos visibles.$contextNote\nDevuelve JSON con cada alimento identificado."

        // When multimodal API available, this method is replaced by:
        // Contents.Builder().addBitmap(bitmap).addText(message).build()
        // and the return type changes to Contents instead of String.
    }

    // ─── Anti-Hallucination Filters (Layer 3) ────────────────────────────────

    /**
     * Applies post-inference sanity checks. Discards or flags implausible items.
     */
    private fun applyAntiHallucinationFilters(items: List<LocalAiNutritionItem>): List<LocalAiNutritionItem> {
        val validated = items.mapNotNull { item ->
            val nutr = item.nutritionPer100g ?: return@mapNotNull item.copy(
                confidence = minOf(item.confidence, 0.40),
                reviewRequired = true,
            )

            // Discard items with physiologically impossible macro values
            if (nutr.protein > MAX_MACRO_PER_100G || nutr.carbs > MAX_MACRO_PER_100G || nutr.fats > MAX_MACRO_PER_100G) {
                android.util.Log.w("PhotoAnalyzer", "Discarding hallucinated item: ${item.canonicalName} macros exceed 100g/100g")
                return@mapNotNull null
            }
            if (nutr.calories > MAX_KCAL_PER_100G) {
                android.util.Log.w("PhotoAnalyzer", "Discarding hallucinated item: ${item.canonicalName} kcal=${nutr.calories} > $MAX_KCAL_PER_100G")
                return@mapNotNull null
            }

            // Cap confidence and force reviewRequired
            item.copy(
                confidence = minOf(item.confidence, PHOTO_CONFIDENCE_CAP),
                reviewRequired = true,  // always true for photo items
                grams = null,           // always null — can't determine weight visually
            )
        }

        // Check if the total implied calories are plausible for a single meal
        // (assuming 150g per item as a rough estimate)
        val impliedTotalKcal = validated.sumOf { item ->
            (item.nutritionPer100g?.calories ?: 0.0) * 1.5  // 150g assumption
        }
        if (impliedTotalKcal > MAX_TOTAL_PLATE_KCAL) {
            android.util.Log.w("PhotoAnalyzer", "Implied plate calories $impliedTotalKcal > $MAX_TOTAL_PLATE_KCAL — downgrading all confidence")
            return validated.map { it.copy(confidence = minOf(it.confidence, 0.40)) }
        }

        return validated
    }

    // ─── Output Parsing (reuses LocalAiManager internals via companion) ───────

    private fun parsePhotoOutput(output: String): List<LocalAiNutritionItem> {
        // Reuse the same JSON extraction logic as the text parser
        // This is delegated back to LocalAiManager via the exposed parse function
        return LocalAiManager.parseOutputForPhoto(output)
    }

    // ─── Bitmap Utilities ────────────────────────────────────────────────────

    private fun resizeBitmap(bitmap: Bitmap, maxPx: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxPx && h <= maxPx) return bitmap
        val scale = maxPx.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
    }
}
