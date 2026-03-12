package com.yourprime.app.modules;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.play.core.assetpacks.AssetPackLocation;
import com.google.android.play.core.assetpacks.AssetPackManager;
import com.google.android.play.core.assetpacks.AssetPackManagerFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalAiModule extends ReactContextBaseJavaModule {
    private static final String TAG = "KPKNLocalAiRN";
    private static final String MODULE_NAME = "KPKNLocalAi";
    private static final String INSTALL_TIME_PACK_NAME = "kpkn_local_ai";
    private static final String MODEL_VERSION = "kpkn-food-fg270m-v1";
    private static final String MODEL_FILENAME = MODEL_VERSION + ".litertlm";
    private static final String MODEL_TASK_FILENAME = MODEL_VERSION + ".task";
    private static final long INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final long RUNTIME_RETRY_COOLDOWN_MS = 3 * 60 * 1000L;
    private static final int MAX_RUNTIME_FAILURES_PER_SESSION = 3;
    private static final Pattern EXPLICIT_GRAMS_PATTERN = Pattern.compile(
        "(\\d+(?:[.,]\\d+)?)\\s*(g|gr|gramos|gramo|ml|mililitros)",
        Pattern.CASE_INSENSITIVE
    );

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoUnloadRunnable = this::unloadRuntimeInternal;
    private final Deque<DiagnosticEvent> diagnosticEvents = new ArrayDeque<>();
    private Future<?> currentTask;
    private Object llmInference;
    private Method llmGenerateResponseMethod;
    private Method llmCloseMethod;
    private File runtimeModelFile;
    private String deliveryMode = "bundled-asset";
    private String lastError = null;
    private long runtimeRetryBlockedUntilMs = 0L;
    private int runtimeFailureCount = 0;
    private long lastWarmupAtMs = 0L;
    private long lastAnalysisAtMs = 0L;
    private long lastAnalysisElapsedMs = 0L;
    private String lastAnalysisEngine = "unavailable";
    private String lastDescriptionPreview = null;
    private String lastResolvedModelPath = null;

    // ── Heuristic food table ─────────────────────────────────────────────────
    // IMPORTANTE: estos valores están alineados manualmente con:
    //   packages/shared-domain/src/nutrition/heuristicFoodCatalog.ts
    // Los campos son NutritionProfile(calories, protein, carbs, fats) por 100g.
    // Si modificas heuristicFoodCatalog.ts, actualiza también esta tabla.
    // Limitación conocida: no podemos consumir el TS en runtime Java.
    // Deuda parcial B1 — pendiente generación automática o assets compartidos.
    private final List<HeuristicFood> heuristicFoods = Arrays.asList(
        // key: completo-italiano | defaultGrams: 250
        new HeuristicFood("completo-italiano", "Completo Italiano", new String[]{"completo italiano", "completos italianos", "completo", "completos"}, "completo", 250, new NutritionProfile(220.0, 6.0, 18.0, 14.0), "armado"),
        // key: pastel-de-choclo | defaultGrams: 350
        new HeuristicFood("pastel-de-choclo", "Pastel de Choclo", new String[]{"pastel de choclo", "pastel choclo"}, "plato", 350, new NutritionProfile(171.0, 7.1, 17.1, 8.6), "horno"),
        // key: cazuela-de-vacuno | defaultGrams: 500
        new HeuristicFood("cazuela-de-vacuno", "Cazuela de Vacuno", new String[]{"cazuela de vacuno", "cazuela vacuno", "cazuela"}, "plato", 500, new NutritionProfile(96.0, 7.0, 8.0, 4.0), "cocido"),
        // key: porotos-granados | defaultGrams: 250
        new HeuristicFood("porotos-granados", "Porotos Granados", new String[]{"porotos granados", "porotos verano"}, "plato", 250, new NutritionProfile(116.0, 5.6, 18.8, 2.0), "cocido"),
        // key: charquican | defaultGrams: 350
        new HeuristicFood("charquican", "Charquicán", new String[]{"charquican", "charquicán"}, "plato", 350, new NutritionProfile(111.0, 5.1, 14.0, 3.7), "guiso"),
        // key: pan-con-palta-y-jamon | defaultGrams: 160
        new HeuristicFood("pan-con-palta-y-jamon", "Pan con Palta y Jamón", new String[]{"pan con palta y jamon", "pan con palta y jamón", "sandwich de palta y jamon"}, "unidad", 160, new NutritionProfile(143.0, 8.8, 19.4, 7.8), "armado"),
        // key: batido-proteina-platano-leche | defaultGrams: 450
        new HeuristicFood("batido-proteina-platano-leche", "Batido de Proteína con Plátano y Leche", new String[]{"batido con platano y proteina", "batido con plátano y proteína", "batido con proteina y leche", "batido con proteína y leche"}, "vaso", 450, new NutritionProfile(76.0, 6.9, 7.6, 1.8), "licuado"),
        // key: empanada-de-pino | defaultGrams: 200
        new HeuristicFood("empanada-de-pino", "Empanada de Pino", new String[]{"empanada de pino", "empanada pino", "empanada", "empanadas"}, "unidad", 200, new NutritionProfile(233.0, 8.5, 25.0, 11.0), "horno"),
        // key: arroz-cocido | defaultGrams: 200
        new HeuristicFood("arroz-cocido", "Arroz", new String[]{"arroz", "arroz blanco", "arroz cocido", "arroz graneado"}, "plato", 200, new NutritionProfile(130.0, 2.7, 28.2, 0.3), "cocido"),
        // key: pollo-cocido | defaultGrams: 150
        new HeuristicFood("pollo-cocido", "Pollo", new String[]{"pollo", "pechuga de pollo", "pechuga", "pollo a la plancha"}, "porcion", 150, new NutritionProfile(165.0, 31.0, 0.0, 3.6), "plancha"),
        // key: huevo | defaultGrams: 50
        new HeuristicFood("huevo", "Huevo", new String[]{"huevo", "huevos", "huevo frito", "huevo revuelto", "huevo duro"}, "unidad", 50, new NutritionProfile(155.0, 13.0, 1.1, 11.0), "cocido"),
        // key: pan-marraqueta | defaultGrams: 100
        new HeuristicFood("pan-marraqueta", "Pan (Marraqueta)", new String[]{"pan", "marraqueta", "pan frances", "hallulla", "pan amasado"}, "unidad", 100, new NutritionProfile(270.0, 8.0, 52.0, 2.5), "horneado")
    );

    public LocalAiModule(ReactApplicationContext reactContext) {
        super(reactContext);
        recordDiagnostic("runtime", "info", "Modulo LocalAi RN inicializado.");
        scheduleAutoUnload();
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public void invalidate() {
        cancelCurrentTaskInternal();
        unloadRuntimeInternal();
        mainHandler.removeCallbacks(autoUnloadRunnable);
        executor.shutdownNow();
        super.invalidate();
    }

    @ReactMethod
    public void getStatus(Promise promise) {
        resolvePromise(promise, buildStatus(false));
    }

    @ReactMethod
    public void getDiagnostics(Promise promise) {
        resolvePromise(promise, buildDiagnostics());
    }

    @ReactMethod
    public void warmup(Promise promise) {
        recordDiagnostic("warmup", "info", "Warmup solicitado desde React Native.");
        cancelCurrentTaskInternal();
        currentTask = executor.submit(() -> {
            try {
                boolean runtimeReady = ensureRuntimeIfPossible();
                lastWarmupAtMs = System.currentTimeMillis();
                recordDiagnostic(
                    "warmup",
                    runtimeReady ? "info" : "warn",
                    runtimeReady
                        ? "Warmup completado. Runtime local listo."
                        : "Warmup completado, pero el modulo sigue en heuristicas."
                );
                resolvePromise(promise, buildStatus(llmInference != null));
            } catch (Exception error) {
                lastError = error.getMessage();
                Log.w(TAG, "Warmup failed", error);
                recordDiagnostic("warmup", "error", "Warmup fallo: " + safeMessage(error));
                resolvePromise(promise, buildStatus(false));
            } finally {
                scheduleAutoUnload();
            }
        });
    }

    @ReactMethod
    public void analyzeNutritionDescription(ReadableMap request, Promise promise) {
        String description = request.hasKey("description") && !request.isNull("description") ? request.getString("description") : null;
        if (description == null || description.trim().isEmpty()) {
            promise.reject("local_ai_invalid_request", "Debe proveer una descripcion.");
            return;
        }

        List<KnownFoodHint> knownFoods = readKnownFoods(request.hasKey("knownFoods") && !request.isNull("knownFoods") ? request.getArray("knownFoods") : null);
        List<String> userMemory = readStringArray(request.hasKey("userMemory") && !request.isNull("userMemory") ? request.getArray("userMemory") : null);
        lastDescriptionPreview = buildDescriptionPreview(description);
        recordDiagnostic("analyze", "info", "Analisis solicitado para: " + lastDescriptionPreview);
        cancelCurrentTaskInternal();
        currentTask = executor.submit(() -> {
            long startedAt = SystemClock.elapsedRealtime();
            try {
                boolean runtimeReady = ensureRuntimeIfPossible();
                JSONObject result = runtimeReady
                    ? analyzeWithRuntime(description.trim(), knownFoods, userMemory)
                    : analyzeWithHeuristics(description.trim(), knownFoods, userMemory);
                long elapsedMs = SystemClock.elapsedRealtime() - startedAt;
                result.put("elapsedMs", elapsedMs);
                if (!result.has("modelVersion")) {
                    result.put("modelVersion", llmInference != null ? MODEL_VERSION : JSONObject.NULL);
                }
                lastAnalysisAtMs = System.currentTimeMillis();
                lastAnalysisElapsedMs = elapsedMs;
                lastAnalysisEngine = result.optString("engine", runtimeReady ? "runtime" : "heuristics");
                recordDiagnostic(
                    "analyze",
                    "info",
                    "Analisis completado con " + lastAnalysisEngine + " en " + elapsedMs + " ms."
                );
                resolvePromise(promise, toWritableMap(result));
            } catch (Exception error) {
                lastError = error.getMessage();
                recordDiagnostic("analyze", "warn", "Runtime fallo, se usa fallback heuristico: " + safeMessage(error));
                try {
                    JSONObject fallback = analyzeWithHeuristics(description.trim(), knownFoods, userMemory);
                    long elapsedMs = SystemClock.elapsedRealtime() - startedAt;
                    fallback.put("elapsedMs", elapsedMs);
                    fallback.put("runtimeError", error.getMessage() != null ? error.getMessage() : "El runtime local no pudo completar el analisis.");
                    lastAnalysisAtMs = System.currentTimeMillis();
                    lastAnalysisElapsedMs = elapsedMs;
                    lastAnalysisEngine = fallback.optString("engine", "heuristics");
                    resolvePromise(promise, toWritableMap(fallback));
                } catch (Exception fallbackError) {
                    recordDiagnostic("analyze", "error", "Fallback heuristico tambien fallo: " + safeMessage(fallbackError));
                    rejectPromise(
                        promise,
                        "local_ai_analysis_failed",
                        fallbackError.getMessage() != null ? fallbackError.getMessage() : "No se pudo analizar la descripcion."
                    );
                }
            } finally {
                scheduleAutoUnload();
            }
        });
    }

    @ReactMethod
    public void cancelCurrentAnalysis(Promise promise) {
        cancelCurrentTaskInternal();
        WritableMap response = Arguments.createMap();
        response.putBoolean("cancelled", true);
        resolvePromise(promise, response);
    }

    @ReactMethod
    public void unload(Promise promise) {
        cancelCurrentTaskInternal();
        unloadRuntimeInternal();
        WritableMap response = Arguments.createMap();
        response.putBoolean("unloaded", true);
        resolvePromise(promise, response);
    }

    private boolean ensureRuntimeIfPossible() {
        touchLastUsed();
        if (runtimeFailureCount >= MAX_RUNTIME_FAILURES_PER_SESSION || runtimeRetryBlockedUntilMs > SystemClock.elapsedRealtime()) {
            if (runtimeRetryBlockedUntilMs > SystemClock.elapsedRealtime()) {
                recordDiagnostic("runtime", "warn", "Runtime en cooldown temporal. Se mantiene heuristicas.");
            }
            return false;
        }
        File modelFile = resolveModelFile();
        if (modelFile == null || !modelFile.exists()) {
            runtimeModelFile = null;
            recordDiagnostic("model", "warn", "No encontramos el modelo local empaquetado.");
            return false;
        }
        if (llmInference != null && runtimeModelFile != null && runtimeModelFile.equals(modelFile)) return true;

        try {
            unloadRuntimeInternal();
            runtimeModelFile = modelFile;
            lastResolvedModelPath = modelFile.getAbsolutePath();
            Class<?> llmClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference");
            Class<?> optionsClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference$LlmInferenceOptions");
            Object builder = optionsClass.getMethod("builder").invoke(null);
            invokeBuilder(builder, "setModelPath", String.class, modelFile.getAbsolutePath());
            invokeBuilder(builder, "setMaxTokens", Integer.TYPE, 1024);
            if (!invokeBuilder(builder, "setMaxTopK", Integer.TYPE, 40)) invokeBuilder(builder, "setTopK", Integer.TYPE, 40);
            invokeBuilder(builder, "setTemperature", Float.TYPE, 0.15f);
            Object options = builder.getClass().getMethod("build").invoke(builder);
            llmInference = llmClass.getMethod("createFromOptions", Context.class, optionsClass).invoke(null, getReactApplicationContext(), options);
            llmGenerateResponseMethod = llmClass.getMethod("generateResponse", String.class);
            llmCloseMethod = llmClass.getMethod("close");
            lastError = null;
            runtimeFailureCount = 0;
            runtimeRetryBlockedUntilMs = 0L;
            recordDiagnostic("runtime", "info", "Runtime inicializado desde " + deliveryMode + ".");
            return true;
        } catch (Throwable error) {
            unloadRuntimeInternal();
            runtimeModelFile = null;
            runtimeFailureCount++;
            runtimeRetryBlockedUntilMs = SystemClock.elapsedRealtime() + RUNTIME_RETRY_COOLDOWN_MS;
            lastError = "Runtime local no disponible en esta JVM: " + error.getMessage();
            Log.w(TAG, lastError + " (failure #" + runtimeFailureCount + ")", error);
            recordDiagnostic("runtime", "error", lastError);
            return false;
        }
    }

    private JSONObject analyzeWithRuntime(String description, List<KnownFoodHint> knownFoods, List<String> userMemory) throws Exception {
        if (llmInference == null) {
            throw new IllegalStateException("Runtime no inicializado.");
        }

        String response = (String) llmGenerateResponseMethod.invoke(llmInference, buildPrompt(description, knownFoods, userMemory));
        JSONObject parsed;
        try {
            parsed = extractAndValidateJson(response);
        } catch (JSONException firstParseError) {
            recordDiagnostic("analyze", "warn", "La salida inicial del runtime no fue JSON valido. Se intenta una reparacion estricta.");
            String repairedResponse = (String) llmGenerateResponseMethod.invoke(
                llmInference,
                buildRepairPrompt(description, knownFoods, userMemory, response)
            );
            parsed = extractAndValidateJson(repairedResponse);
        }
        enrichRuntimeItemsWithHeuristics(parsed);
        parsed.put("engine", "runtime");
        parsed.put("runtimeError", JSONObject.NULL);
        parsed.put("modelVersion", MODEL_VERSION);
        return parsed;
    }

    private void enrichRuntimeItemsWithHeuristics(JSONObject parsed) throws JSONException {
        JSONArray items = parsed.optJSONArray("items");
        if (items == null) {
            return;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String normalizedCanonical = normalizeText(item.optString("canonicalName", ""));
            for (HeuristicFood heuristic : heuristicFoods) {
                if (!normalizeText(heuristic.canonicalName).equals(normalizedCanonical)) {
                    continue;
                }
                item.put("preparation", heuristic.preparation);
                item.put("source", "local-heuristic");
                item.put("nutritionPer100g", heuristic.nutritionProfile.toJson());
                hydrateItemTotals(item);
                break;
            }
        }
    }

    private JSONObject analyzeWithHeuristics(String description, List<KnownFoodHint> knownFoods, List<String> userMemory) throws JSONException {
        String normalized = normalizeText(description);
        String working = normalized;
        JSONArray items = new JSONArray();
        double confidenceAccumulator = 0.0;
        for (KnownFoodHint knownFood : knownFoods) {
            if (normalized.contains(knownFood.normalizedName)) {
                items.put(buildKnownFoodJson(knownFood, description));
                confidenceAccumulator += 0.9;
                working = working.replace(knownFood.normalizedName, " ").replaceAll("\\s+", " ").trim();
            }
        }
        if (items.length() == 0) {
            for (HeuristicFood heuristicFood : heuristicFoods) {
                MatchResult match = heuristicFood.match(working, normalized, description, knownFoods, userMemory);
                if (match != null) {
                    working = working.replace(match.aliasUsed, " ").replaceAll("\\s+", " ").trim();
                    items.put(match.toJson());
                    confidenceAccumulator += match.confidence;
                }
            }
        }
        if (items.length() == 0) {
            items.put(buildFallbackJson(description));
            confidenceAccumulator = 0.48;
        }

        JSONObject result = new JSONObject();
        result.put("items", items);
        result.put("overallConfidence", round(items.length() == 0 ? 0 : Math.min(0.92, confidenceAccumulator / items.length())));
        result.put("containsEstimatedItems", containsEstimated(items));
        result.put("requiresReview", false);
        result.put("modelVersion", JSONObject.NULL);
        result.put("engine", "heuristics");
        result.put("runtimeError", lastError == null ? JSONObject.NULL : lastError);
        return result;
    }

    private JSONObject buildKnownFoodJson(KnownFoodHint food, String rawText) throws JSONException {
        double grams = food.servingSize > 0 ? food.servingSize : 100.0;
        JSONObject nutritionPer100g = new JSONObject();
        nutritionPer100g.put("calories", round((food.calories / grams) * 100.0));
        nutritionPer100g.put("protein", round((food.protein / grams) * 100.0));
        nutritionPer100g.put("carbs", round((food.carbs / grams) * 100.0));
        nutritionPer100g.put("fats", round((food.fats / grams) * 100.0));
        JSONObject item = new JSONObject();
        item.put("rawText", rawText);
        item.put("canonicalName", food.name);
        item.put("grams", grams);
        item.put("quantity", 1.0);
        item.put("preparation", JSONObject.NULL);
        item.put("source", "database");
        item.put("confidence", 0.9);
        item.put("reviewRequired", false);
        item.put("nutritionPer100g", nutritionPer100g);
        item.put("calories", round(food.calories));
        item.put("protein", round(food.protein));
        item.put("carbs", round(food.carbs));
        item.put("fats", round(food.fats));
        return item;
    }

    private JSONObject buildFallbackJson(String rawText) throws JSONException {
        JSONObject nutritionPer100g = new JSONObject();
        nutritionPer100g.put("calories", 144.4);
        nutritionPer100g.put("protein", 5.6);
        nutritionPer100g.put("carbs", 11.1);
        nutritionPer100g.put("fats", 5.6);
        JSONObject item = new JSONObject();
        item.put("rawText", rawText);
        item.put("canonicalName", rawText);
        item.put("grams", 180.0);
        item.put("quantity", 1.0);
        item.put("preparation", JSONObject.NULL);
        item.put("source", "fallback-estimate");
        item.put("confidence", 0.48);
        item.put("reviewRequired", false);
        item.put("nutritionPer100g", nutritionPer100g);
        item.put("calories", 260.0);
        item.put("protein", 10.0);
        item.put("carbs", 20.0);
        item.put("fats", 10.0);
        return item;
    }

    private JSONObject extractAndValidateJson(String response) throws JSONException {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.startsWith("```")) trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) trimmed = trimmed.substring(firstBrace, lastBrace + 1);

        JSONObject parsed = new JSONObject(trimmed);
        normalizeResultObject(parsed);
        JSONArray items = parsed.optJSONArray("items");
        if (items == null) items = new JSONArray();
        JSONArray validItems = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            if (!item.has("rawText")) item.put("rawText", item.optString("canonicalName", "item"));
            if (!item.has("canonicalName")) item.put("canonicalName", item.optString("rawText", "item"));
            if (!item.has("grams")) item.put("grams", 100.0);
            if (!item.has("quantity")) item.put("quantity", 1.0);
            if (!item.has("source")) item.put("source", "local-ai-estimate");
            if (!item.has("confidence")) item.put("confidence", 0.55);
            if (!item.has("reviewRequired")) item.put("reviewRequired", false);
            if (!item.has("preparation")) item.put("preparation", JSONObject.NULL);
            JSONObject per100g = item.optJSONObject("nutritionPer100g");
            if (per100g == null) {
                per100g = new JSONObject();
                per100g.put("calories", item.optDouble("calories", 100.0));
                per100g.put("protein", item.optDouble("protein", 5.0));
                per100g.put("carbs", item.optDouble("carbs", 15.0));
                per100g.put("fats", item.optDouble("fats", 3.0));
                item.put("nutritionPer100g", per100g);
            }
            hydrateItemTotals(item);
            validItems.put(item);
        }
        parsed.put("items", validItems);
        if (!parsed.has("overallConfidence")) parsed.put("overallConfidence", validItems.length() > 0 ? 0.62 : 0.0);
        if (!parsed.has("containsEstimatedItems")) parsed.put("containsEstimatedItems", containsEstimated(validItems));
        if (!parsed.has("requiresReview")) parsed.put("requiresReview", false);
        if (!parsed.has("runtimeError")) parsed.put("runtimeError", JSONObject.NULL);
        return parsed;
    }

    private void normalizeResultObject(JSONObject parsed) throws JSONException {
        if (!parsed.has("items")) {
            parsed.put("items", new JSONArray());
        }
        if (!parsed.has("overallConfidence")) {
            parsed.put("overallConfidence", 0.62);
        }
        if (!parsed.has("containsEstimatedItems")) {
            parsed.put("containsEstimatedItems", true);
        }
        if (!parsed.has("requiresReview")) {
            parsed.put("requiresReview", false);
        }
    }

    private void hydrateItemTotals(JSONObject item) throws JSONException {
        JSONObject per100g = item.optJSONObject("nutritionPer100g");
        if (per100g == null) return;
        double grams = item.optDouble("grams", 100.0);
        double multiplier = grams / 100.0;
        item.put("calories", round(per100g.optDouble("calories", item.optDouble("calories", 0.0)) * multiplier));
        item.put("protein", round(per100g.optDouble("protein", item.optDouble("protein", 0.0)) * multiplier));
        item.put("carbs", round(per100g.optDouble("carbs", item.optDouble("carbs", 0.0)) * multiplier));
        item.put("fats", round(per100g.optDouble("fats", item.optDouble("fats", 0.0)) * multiplier));
    }

    private boolean containsEstimated(JSONArray items) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && !"database".equals(item.optString("source", ""))) return true;
        }
        return false;
    }

    private String buildPrompt(String description, List<KnownFoodHint> knownFoods, List<String> userMemory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Eres un asistente de nutricion. Dado un texto libre con comidas, haz dos cosas:\n");
        prompt.append("1. Extraer cada item alimentario con cantidad en gramos\n");
        prompt.append("2. Para cada item, estimar calorias y macros por 100g\n\n");
        prompt.append("Responde SOLO JSON valido, sin markdown ni explicaciones.\n\n");
        prompt.append("Tu respuesta DEBE comenzar con '{' y terminar con '}'.\n");
        prompt.append("No uses encabezados, no uses viñetas, no uses asteriscos.\n\n");
        prompt.append("Schema:\n");
        prompt.append("{\"items\":[{\"rawText\":\"lo que escribio el usuario\",\"canonicalName\":\"nombre estandar\",\"grams\":150,\"quantity\":1,\"source\":\"local-ai-estimate\",\"confidence\":0.6,\"nutritionPer100g\":{\"calories\":130,\"protein\":2.7,\"carbs\":28,\"fats\":0.3},\"reviewRequired\":false}],\"overallConfidence\":0.6,\"containsEstimatedItems\":true,\"requiresReview\":false}\n\n");
        prompt.append("Reglas MUY ESTRICTAS para evitar alucinaciones:\n");
        prompt.append("- Las proteinas y carbohidratos aportan ~4 kcal/g, las grasas ~9 kcal/g. Asegurate de que (protein*4 + carbs*4 + fats*9) sea casi igual a las calorias reportadas.\n");
        prompt.append("- Mantente en rangos logicos: la mayoria de los alimentos crudos no superan las 300 kcal/100g, y los vegetales tienen menos de 50 kcal/100g por su gran contenido de agua.\n");
        prompt.append("- Usa tablas nutricionales comunes como referencia. No inventes macros extremos.\n");
        prompt.append("- Si es un plato o preparacion compleja, promedia el perfil de sus ingredientes.\n");
        prompt.append("- Si el usuario no especifica gramos, estima una porcion tipica real.\n");
        prompt.append("- Usa los nombres de knownFoods cuando aplique de manera prioritaria.\n");
        prompt.append("- Responde en espanol.\n");
        if (!knownFoods.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (KnownFoodHint food : knownFoods) names.add(food.name);
            prompt.append("knownFoods: ").append(TextUtils.join(", ", names.subList(0, Math.min(names.size(), 40)))).append("\n");
        }
        if (!userMemory.isEmpty()) {
            prompt.append("userMemory: ").append(TextUtils.join(", ", userMemory.subList(0, Math.min(userMemory.size(), 20)))).append("\n");
        }
        prompt.append("descripcion: ").append(description);
        return prompt.toString();
    }

    private String buildRepairPrompt(String description, List<KnownFoodHint> knownFoods, List<String> userMemory, String invalidResponse) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Tu respuesta anterior fue invalida porque no fue JSON puro.\n");
        prompt.append("Repite la tarea y responde EXACTAMENTE un objeto JSON valido.\n");
        prompt.append("La primera letra debe ser '{' y la ultima debe ser '}'.\n");
        prompt.append("No uses markdown, no uses encabezados, no uses texto extra, no uses asteriscos.\n");
        prompt.append("Si no sabes algo, estima una porcion razonable y manten el schema.\n\n");
        prompt.append("Schema obligatorio:\n");
        prompt.append("{\"items\":[{\"rawText\":\"texto original\",\"canonicalName\":\"nombre estandar\",\"grams\":150,\"quantity\":1,\"source\":\"local-ai-estimate\",\"confidence\":0.6,\"nutritionPer100g\":{\"calories\":130,\"protein\":2.7,\"carbs\":28,\"fats\":0.3},\"reviewRequired\":false}],\"overallConfidence\":0.6,\"containsEstimatedItems\":true,\"requiresReview\":false}\n\n");
        if (!knownFoods.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (KnownFoodHint food : knownFoods) names.add(food.name);
            prompt.append("knownFoods: ").append(TextUtils.join(", ", names.subList(0, Math.min(names.size(), 40)))).append("\n");
        }
        if (!userMemory.isEmpty()) {
            prompt.append("userMemory: ").append(TextUtils.join(", ", userMemory.subList(0, Math.min(userMemory.size(), 20)))).append("\n");
        }
        prompt.append("descripcion: ").append(description).append("\n");
        prompt.append("Respuesta invalida previa (solo referencia, no la repitas): ").append(truncateForPrompt(invalidResponse, 320));
        return prompt.toString();
    }

    private WritableMap buildStatus(boolean runtimeReady) {
        WritableMap status = Arguments.createMap();
        boolean runtimeActive = runtimeReady || llmInference != null;
        File installTimeModel = findInstallTimePackModel();
        File privateModel = findPrivateModelFile();
        boolean bundledAssetExists = hasBundledModelAsset();
        boolean modelBundleFound = installTimeModel != null || privateModel != null || bundledAssetExists;
        String statusDeliveryMode = installTimeModel != null ? "install-time-pack" : bundledAssetExists || privateModel != null ? "bundled-asset" : "unknown";
        if (runtimeModelFile != null) {
            statusDeliveryMode = deliveryMode;
        }
        status.putBoolean("available", modelBundleFound || runtimeActive);
        status.putBoolean("modelReady", runtimeActive);
        putNullableString(status, "modelVersion", modelBundleFound || runtimeActive ? MODEL_VERSION : null);
        status.putString("deliveryMode", statusDeliveryMode);
        status.putString("backend", "cpu");
        status.putString("engine", runtimeActive ? "runtime" : "heuristics");
        putNullableString(status, "lastError", lastError);
        return status;
    }

    private WritableMap buildDiagnostics() {
        WritableMap diagnostics = Arguments.createMap();
        File installTimeModel = findInstallTimePackModel();
        File privateModel = findPrivateModelFile();
        String diagnosticsDeliveryMode = runtimeModelFile != null
            ? deliveryMode
            : installTimeModel != null
                ? "install-time-pack"
                : privateModel != null || hasBundledModelAsset()
                    ? "bundled-asset"
                    : "unknown";
        diagnostics.putBoolean("runtimeLoaded", llmInference != null);
        putNullableString(
            diagnostics,
            "modelPath",
            runtimeModelFile != null ? runtimeModelFile.getAbsolutePath() : lastResolvedModelPath
        );
        diagnostics.putString("deliveryMode", diagnosticsDeliveryMode);
        diagnostics.putDouble("runtimeFailureCount", runtimeFailureCount);
        diagnostics.putDouble("cooldownRemainingMs", Math.max(0L, runtimeRetryBlockedUntilMs - SystemClock.elapsedRealtime()));
        putNullableString(diagnostics, "lastError", lastError);
        putNullableNumber(diagnostics, "lastWarmupAtMs", lastWarmupAtMs > 0 ? lastWarmupAtMs : null);
        putNullableNumber(diagnostics, "lastAnalysisAtMs", lastAnalysisAtMs > 0 ? lastAnalysisAtMs : null);
        putNullableString(diagnostics, "lastAnalysisEngine", lastAnalysisEngine);
        putNullableNumber(diagnostics, "lastAnalysisElapsedMs", lastAnalysisElapsedMs > 0 ? lastAnalysisElapsedMs : null);
        putNullableString(diagnostics, "lastDescriptionPreview", lastDescriptionPreview);

        WritableArray recentEvents = Arguments.createArray();
        synchronized (diagnosticEvents) {
            for (DiagnosticEvent event : diagnosticEvents) {
                WritableMap item = Arguments.createMap();
                item.putDouble("timestampMs", event.timestampMs);
                item.putString("level", event.level);
                item.putString("scope", event.scope);
                item.putString("message", event.message);
                recentEvents.pushMap(item);
            }
        }
        diagnostics.putArray("recentEvents", recentEvents);
        return diagnostics;
    }

    private File resolveModelFile() {
        File installTimeFile = findInstallTimePackModel();
        if (installTimeFile != null) {
            deliveryMode = "install-time-pack";
            lastResolvedModelPath = installTimeFile.getAbsolutePath();
            return installTimeFile;
        }
        File privateModel = findPrivateModelFile();
        if (privateModel != null) {
            deliveryMode = "bundled-asset";
            lastResolvedModelPath = privateModel.getAbsolutePath();
            return privateModel;
        }
        for (String name : new String[]{MODEL_FILENAME, MODEL_TASK_FILENAME}) {
            if (assetExists("models/" + name)) {
                File copied = copyBundledAsset("models/" + name, name);
                if (copied != null) {
                    deliveryMode = "bundled-asset";
                    lastResolvedModelPath = copied.getAbsolutePath();
                    return copied;
                }
            }
        }
        return null;
    }

    private File findPrivateModelFile() {
        File targetDir = new File(getReactApplicationContext().getFilesDir(), "local-ai");
        for (String name : new String[]{MODEL_FILENAME, MODEL_TASK_FILENAME}) {
            File file = new File(targetDir, name);
            if (file.exists() && file.length() > 0) return file;
        }
        return null;
    }

    private boolean hasBundledModelAsset() {
        return assetExists("models/" + MODEL_FILENAME) || assetExists("models/" + MODEL_TASK_FILENAME);
    }

    private File findInstallTimePackModel() {
        try {
            AssetPackManager assetPackManager = AssetPackManagerFactory.getInstance(getReactApplicationContext());
            AssetPackLocation location = assetPackManager.getPackLocation(INSTALL_TIME_PACK_NAME);
            if (location == null || location.assetsPath() == null) {
                return null;
            }

            for (String name : new String[]{MODEL_FILENAME, MODEL_TASK_FILENAME}) {
                File candidate = new File(location.assetsPath(), "install-time-models/" + name);
                if (candidate.exists() && candidate.length() > 0) {
                    return candidate;
                }
            }
        } catch (Throwable error) {
            lastError = "No se pudo consultar el asset pack local: " + error.getMessage();
            Log.w(TAG, lastError, error);
            recordDiagnostic("model", "warn", lastError);
        }

        return null;
    }

    private File copyBundledAsset(String assetPath, String targetName) {
        File targetDir = new File(getReactApplicationContext().getFilesDir(), "local-ai");
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            lastError = "No se pudo crear el directorio del modelo local.";
            return null;
        }
        File target = new File(targetDir, targetName);
        if (target.exists() && target.length() > 0) return target;
        try (InputStream input = getReactApplicationContext().getAssets().open(assetPath); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
            return target;
        } catch (IOException error) {
            lastError = "No se pudo copiar el modelo local: " + error.getMessage();
            Log.w(TAG, lastError, error);
            return null;
        }
    }

    private boolean assetExists(String assetPath) {
        try (InputStream ignored = getReactApplicationContext().getAssets().open(assetPath)) {
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private boolean invokeBuilder(Object builder, String methodName, Class<?> parameterType, Object value) throws Exception {
        try {
            Method method = builder.getClass().getMethod(methodName, parameterType);
            method.invoke(builder, value);
            return true;
        } catch (NoSuchMethodException missingMethod) {
            return false;
        }
    }

    private List<KnownFoodHint> readKnownFoods(ReadableArray array) {
        List<KnownFoodHint> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.size(); i++) {
            ReadableMap food = array.getMap(i);
            if (food == null || !food.hasKey("name") || food.isNull("name")) continue;
            values.add(new KnownFoodHint(
                food.getString("name"),
                food.hasKey("servingSize") && !food.isNull("servingSize") ? food.getDouble("servingSize") : 100.0,
                food.hasKey("calories") && !food.isNull("calories") ? food.getDouble("calories") : 0.0,
                food.hasKey("protein") && !food.isNull("protein") ? food.getDouble("protein") : 0.0,
                food.hasKey("carbs") && !food.isNull("carbs") ? food.getDouble("carbs") : 0.0,
                food.hasKey("fats") && !food.isNull("fats") ? food.getDouble("fats") : 0.0
            ));
        }
        return values;
    }

    private List<String> readStringArray(ReadableArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.size(); i++) {
            if (array.isNull(i)) {
                continue;
            }
            String value = array.getString(i);
            if (value != null && !value.trim().isEmpty()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private void cancelCurrentTaskInternal() {
        if (currentTask != null && !currentTask.isDone()) currentTask.cancel(true);
        currentTask = null;
    }

    private void unloadRuntimeInternal() {
        mainHandler.removeCallbacks(autoUnloadRunnable);
        if (llmInference != null) {
            try {
                if (llmCloseMethod != null) llmCloseMethod.invoke(llmInference);
            } catch (Exception error) {
                Log.w(TAG, "No se pudo cerrar el runtime local", error);
            }
        }
        llmInference = null;
        llmGenerateResponseMethod = null;
        llmCloseMethod = null;
        runtimeModelFile = null;
        recordDiagnostic("runtime", "info", "Runtime descargado de memoria.");
    }

    private void scheduleAutoUnload() {
        mainHandler.removeCallbacks(autoUnloadRunnable);
        mainHandler.postDelayed(autoUnloadRunnable, INACTIVITY_TIMEOUT_MS);
    }

    private void touchLastUsed() {
        lastError = null;
        scheduleAutoUnload();
    }

    private void resolvePromise(Promise promise, Object value) {
        mainHandler.post(() -> promise.resolve(value));
    }

    private void rejectPromise(Promise promise, String code, String message) {
        mainHandler.post(() -> promise.reject(code, message));
    }

    private void putNullableString(WritableMap map, String key, String value) {
        if (value == null) {
            map.putNull(key);
        } else {
            map.putString(key, value);
        }
    }

    private void putNullableNumber(WritableMap map, String key, Number value) {
        if (value == null) {
            map.putNull(key);
        } else {
            map.putDouble(key, value.doubleValue());
        }
    }

    private void recordDiagnostic(String scope, String level, String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        synchronized (diagnosticEvents) {
            if (diagnosticEvents.size() >= 12) {
                diagnosticEvents.removeFirst();
            }
            diagnosticEvents.addLast(new DiagnosticEvent(System.currentTimeMillis(), scope, level, message.trim()));
        }
    }

    private static String safeMessage(Throwable error) {
        return error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()
            ? "Sin detalle adicional."
            : error.getMessage().trim();
    }

    private static String buildDescriptionPreview(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 56) {
            return normalized;
        }
        return normalized.substring(0, 53) + "...";
    }

    private static String truncateForPrompt(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String normalizeText(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static double parseExplicitGrams(String rawText) {
        Matcher matcher = EXPLICIT_GRAMS_PATTERN.matcher(rawText == null ? "" : rawText);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Double.parseDouble(matcher.group(1).replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static double parseQuantity(String normalizedText, String quantityKeyword) {
        if (quantityKeyword == null || quantityKeyword.isEmpty()) {
            return 1;
        }
        Pattern pattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s+(?:" + Pattern.quote(quantityKeyword) + ")(?:es|s)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(normalizedText == null ? "" : normalizedText);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1).replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        if ((normalizedText != null && normalizedText.contains("un " + quantityKeyword))
            || (normalizedText != null && normalizedText.contains("una " + quantityKeyword))) {
            return 1;
        }
        return 1;
    }

    private WritableMap toWritableMap(JSONObject object) throws JSONException {
        WritableMap map = Arguments.createMap();
        JSONArray names = object.names();
        if (names == null) return map;
        for (int i = 0; i < names.length(); i++) {
            String key = names.getString(i);
            Object value = object.get(key);
            if (value == JSONObject.NULL) map.putNull(key);
            else if (value instanceof JSONObject) map.putMap(key, toWritableMap((JSONObject) value));
            else if (value instanceof JSONArray) map.putArray(key, toWritableArray((JSONArray) value));
            else if (value instanceof Boolean) map.putBoolean(key, (Boolean) value);
            else if (value instanceof Number) map.putDouble(key, ((Number) value).doubleValue());
            else map.putString(key, String.valueOf(value));
        }
        return map;
    }

    private WritableArray toWritableArray(JSONArray array) throws JSONException {
        WritableArray writableArray = Arguments.createArray();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value == JSONObject.NULL) writableArray.pushNull();
            else if (value instanceof JSONObject) writableArray.pushMap(toWritableMap((JSONObject) value));
            else if (value instanceof JSONArray) writableArray.pushArray(toWritableArray((JSONArray) value));
            else if (value instanceof Boolean) writableArray.pushBoolean((Boolean) value);
            else if (value instanceof Number) writableArray.pushDouble(((Number) value).doubleValue());
            else writableArray.pushString(String.valueOf(value));
        }
        return writableArray;
    }

    private static final class KnownFoodHint {
        final String name;
        final String normalizedName;
        final double servingSize;
        final double calories;
        final double protein;
        final double carbs;
        final double fats;

        KnownFoodHint(String name, double servingSize, double calories, double protein, double carbs, double fats) {
            this.name = name == null ? "" : name;
            this.normalizedName = normalizeText(this.name);
            this.servingSize = servingSize;
            this.calories = calories;
            this.protein = protein;
            this.carbs = carbs;
            this.fats = fats;
        }
    }

    private static final class NutritionProfile {
        final double calories;
        final double protein;
        final double carbs;
        final double fats;

        NutritionProfile(double calories, double protein, double carbs, double fats) {
            this.calories = calories;
            this.protein = protein;
            this.carbs = carbs;
            this.fats = fats;
        }

        JSONObject toJson() throws JSONException {
            JSONObject per100g = new JSONObject();
            per100g.put("calories", calories);
            per100g.put("protein", protein);
            per100g.put("carbs", carbs);
            per100g.put("fats", fats);
            return per100g;
        }
    }

    private static final class MatchResult {
        final String rawText;
        final String canonicalName;
        final String aliasUsed;
        final double grams;
        final double quantity;
        final String preparation;
        final double confidence;
        final NutritionProfile nutritionProfile;

        MatchResult(String rawText, String canonicalName, String aliasUsed, double grams, double quantity, String preparation, double confidence, NutritionProfile nutritionProfile) {
            this.rawText = rawText;
            this.canonicalName = canonicalName;
            this.aliasUsed = aliasUsed;
            this.grams = grams;
            this.quantity = quantity;
            this.preparation = preparation;
            this.confidence = confidence;
            this.nutritionProfile = nutritionProfile;
        }

        JSONObject toJson() throws JSONException {
            JSONObject item = new JSONObject();
            item.put("rawText", rawText);
            item.put("canonicalName", canonicalName);
            item.put("grams", Math.round(grams));
            item.put("quantity", quantity);
            item.put("preparation", preparation);
            item.put("source", "local-heuristic");
            item.put("confidence", confidence);
            item.put("reviewRequired", confidence < 0.55);
            item.put("nutritionPer100g", nutritionProfile.toJson());
            double multiplier = grams / 100.0;
            item.put("calories", round(nutritionProfile.calories * multiplier));
            item.put("protein", round(nutritionProfile.protein * multiplier));
            item.put("carbs", round(nutritionProfile.carbs * multiplier));
            item.put("fats", round(nutritionProfile.fats * multiplier));
            return item;
        }
    }

    private static final class HeuristicFood {
        final String key;
        final String canonicalName;
        final String[] aliases;
        final String quantityKeyword;
        final double defaultGrams;
        final NutritionProfile nutritionProfile;
        final String preparation;

        HeuristicFood(String key, String canonicalName, String[] aliases, String quantityKeyword, double defaultGrams, NutritionProfile nutritionProfile, String preparation) {
            this.key = key;
            this.canonicalName = canonicalName;
            this.aliases = aliases;
            this.quantityKeyword = quantityKeyword;
            this.defaultGrams = defaultGrams;
            this.nutritionProfile = nutritionProfile;
            this.preparation = preparation;
        }

        MatchResult match(String workingText, String originalNormalizedText, String originalRawText, List<KnownFoodHint> knownFoods, List<String> userMemory) {
            String bestAlias = null;
            for (String alias : aliases) {
                String normalizedAlias = normalizeText(alias);
                if (workingText.contains(normalizedAlias)) {
                    if (bestAlias == null || normalizedAlias.length() > bestAlias.length()) {
                        bestAlias = normalizedAlias;
                    }
                }
            }

            if (bestAlias == null) {
                return null;
            }

            double quantity = parseQuantity(originalNormalizedText, quantityKeyword);
            double explicitGrams = parseExplicitGrams(originalRawText);
            double grams = explicitGrams > 0 ? explicitGrams : (defaultGrams * Math.max(1, quantity));
            double confidence = 0.72;

            for (KnownFoodHint hint : knownFoods) {
                if (normalizeText(hint.name).equals(normalizeText(canonicalName))) {
                    confidence += 0.08;
                    break;
                }
            }
            for (String memory : userMemory) {
                if (normalizeText(memory).contains(normalizeText(canonicalName))) {
                    confidence += 0.06;
                    break;
                }
            }
            if (bestAlias.equals(normalizeText(canonicalName)) || bestAlias.length() >= Math.max(10, canonicalName.length() - 4)) {
                confidence += 0.06;
            }
            if (quantity > 1) {
                confidence += 0.04;
            }

            return new MatchResult(
                canonicalName,
                canonicalName,
                bestAlias,
                grams,
                quantity,
                preparation,
                Math.min(confidence, 0.9),
                nutritionProfile
            );
        }
    }

    private static final class DiagnosticEvent {
        final long timestampMs;
        final String scope;
        final String level;
        final String message;

        DiagnosticEvent(long timestampMs, String scope, String level, String message) {
            this.timestampMs = timestampMs;
            this.scope = scope;
            this.level = level;
            this.message = message;
        }
    }
}
