package com.yourprime.app.localai;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.content.Context;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CapacitorPlugin(name = "LocalAi")
public class LocalAiPlugin extends Plugin {

    private static final String TAG = "KPKNLocalAi";
    private static final String INSTALL_TIME_PACK_NAME = "kpkn_local_ai";

    // --- Model: FunctionGemma 270M (optimized for structured JSON output) ---
    private static final String MODEL_VERSION = "kpkn-food-fg270m-v1";
    private static final String MODEL_FILENAME = MODEL_VERSION + ".litertlm";
    private static final String MODEL_TASK_FILENAME = MODEL_VERSION + ".task";

    private static final long INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final long RUNTIME_RETRY_COOLDOWN_MS = 3 * 60 * 1000L;
    private static final int MAX_RUNTIME_FAILURES_PER_SESSION = 3;

    // Fix: pattern applied on RAW text (not normalized) so decimals survive
    private static final Pattern EXPLICIT_GRAMS_PATTERN = Pattern.compile(
        "(\\d+(?:[.,]\\d+)?)\\s*(g|gr|gramos|gramo|ml|mililitros)",
        Pattern.CASE_INSENSITIVE
    );

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Expanded heuristics: Chilean dishes + universal generic foods
    private final List<HeuristicFood> heuristicFoods = Arrays.asList(
        // --- Platos chilenos ---
        new HeuristicFood(
            "completo-italiano", "Completo Italiano",
            new String[] { "completo italiano", "completos italianos", "completo", "completos" },
            "completo", 250, new NutritionProfile(220, 6.0, 18.0, 14.0), "armado"
        ),
        new HeuristicFood(
            "pastel-de-choclo", "Pastel de Choclo",
            new String[] { "pastel de choclo", "pastel choclo" },
            "plato", 350, new NutritionProfile(171.4, 7.1, 17.1, 8.6), "horno"
        ),
        new HeuristicFood(
            "cazuela-de-vacuno", "Cazuela de Vacuno",
            new String[] { "cazuela de vacuno", "cazuela vacuno", "cazuela" },
            "plato", 500, new NutritionProfile(96.0, 7.0, 8.0, 4.0), "cocido"
        ),
        new HeuristicFood(
            "porotos-granados", "Porotos Granados",
            new String[] { "porotos granados", "porotos verano" },
            "plato", 250, new NutritionProfile(116.0, 5.6, 18.8, 2.0), "cocido"
        ),
        new HeuristicFood(
            "charquican", "Charquicán",
            new String[] { "charquican", "charquicán" },
            "plato", 350, new NutritionProfile(111.4, 5.1, 14.0, 3.7), "guiso"
        ),
        new HeuristicFood(
            "pan-con-palta-y-jamon", "Pan con Palta y Jamón",
            new String[] { "pan con palta y jamon", "pan con palta y jamón", "sandwich de palta y jamon" },
            "unidad", 160, new NutritionProfile(228.1, 8.8, 19.4, 12.5), "armado"
        ),
        new HeuristicFood(
            "pan-con-palta", "Pan con Palta",
            new String[] { "pan con palta", "pan palta", "tostada con palta" },
            "unidad", 120, new NutritionProfile(241.7, 5.0, 25.8, 13.3), "armado"
        ),
        new HeuristicFood(
            "batido-proteina-platano-leche", "Batido de Proteína con Plátano y Leche",
            new String[] {
                "batido con platano y proteina", "batido con plátano y proteína",
                "batido con proteina y leche", "batido con proteína y leche",
                "proteina con leche y platano", "proteína con leche y plátano",
                "batido de proteina con platano", "batido de proteína con plátano"
            },
            "vaso", 450, new NutritionProfile(75.6, 6.9, 7.6, 1.8), "licuado"
        ),
        // --- Platos chilenos adicionales (unificados desde localChileanFoods.ts) ---
        new HeuristicFood(
            "empanada-de-pino", "Empanada de Pino",
            new String[] { "empanada de pino", "empanada pino", "empanada", "empanadas" },
            "unidad", 200, new NutritionProfile(232.5, 8.5, 25.0, 11.0), "horno"
        ),
        new HeuristicFood(
            "sopaipilla", "Sopaipilla",
            new String[] { "sopaipilla", "sopaipillas", "sopaipilla pasada" },
            "unidad", 80, new NutritionProfile(312.5, 5.0, 40.0, 15.0), "frito"
        ),
        new HeuristicFood(
            "humita", "Humita",
            new String[] { "humita", "humitas" },
            "unidad", 250, new NutritionProfile(116.0, 3.2, 16.4, 4.4), "cocido"
        ),
        new HeuristicFood(
            "porotos-con-riendas", "Porotos con Riendas",
            new String[] { "porotos con riendas", "porotos riendas" },
            "plato", 300, new NutritionProfile(123.3, 6.0, 18.7, 2.7), "cocido"
        ),
        new HeuristicFood(
            "mote-con-huesillo", "Mote con Huesillo",
            new String[] { "mote con huesillo", "mote con huesillos", "mote huesillo" },
            "vaso", 350, new NutritionProfile(120.0, 1.5, 28.0, 0.3), "cocido"
        ),
        new HeuristicFood(
            "ensalada-chilena", "Ensalada Chilena",
            new String[] { "ensalada chilena", "ensalada a la chilena", "ensalada tomate cebolla" },
            "plato", 200, new NutritionProfile(45.0, 1.0, 5.0, 2.5), "crudo"
        ),
        // --- Alimentos genéricos universales ---
        new HeuristicFood(
            "arroz-cocido", "Arroz",
            new String[] { "arroz", "arroz blanco", "arroz cocido", "arroz graneado" },
            "plato", 200, new NutritionProfile(130.0, 2.7, 28.2, 0.3), "cocido"
        ),
        new HeuristicFood(
            "pollo-cocido", "Pollo",
            new String[] { "pollo", "pechuga de pollo", "pechuga", "pollo a la plancha" },
            "porcion", 150, new NutritionProfile(165.0, 31.0, 0.0, 3.6), "plancha"
        ),
        new HeuristicFood(
            "huevo", "Huevo",
            new String[] { "huevo", "huevos", "huevo frito", "huevo revuelto", "huevo duro" },
            "unidad", 50, new NutritionProfile(155.0, 13.0, 1.1, 11.0), "cocido"
        ),
        new HeuristicFood(
            "pan-marraqueta", "Pan (Marraqueta)",
            new String[] { "pan", "marraqueta", "pan frances", "hallulla", "pan amasado" },
            "unidad", 100, new NutritionProfile(270.0, 8.0, 52.0, 2.5), "horneado"
        ),
        new HeuristicFood(
            "leche-entera", "Leche",
            new String[] { "leche", "leche entera", "taza de leche", "vaso de leche" },
            "vaso", 250, new NutritionProfile(60.0, 3.2, 4.7, 3.2), "liquido"
        ),
        new HeuristicFood(
            "platano", "Plátano",
            new String[] { "platano", "plátano", "banana" },
            "unidad", 120, new NutritionProfile(89.0, 1.1, 22.8, 0.3), "crudo"
        ),
        new HeuristicFood(
            "cafe-con-leche", "Café con Leche",
            new String[] { "cafe con leche", "café con leche", "cafecito", "cafe", "café" },
            "taza", 200, new NutritionProfile(30.0, 1.6, 2.4, 1.6), "liquido"
        ),
        new HeuristicFood(
            "arroz-con-huevo", "Arroz con Huevo",
            new String[] { "arroz con huevo", "arroz con huevos" },
            "plato", 250, new NutritionProfile(145.0, 6.5, 20.0, 4.5), "cocido"
        ),
        new HeuristicFood(
            "pan-con-queso", "Pan con Queso",
            new String[] { "pan con queso", "sandwich de queso" },
            "unidad", 120, new NutritionProfile(290.0, 12.0, 32.0, 12.0), "armado"
        ),
        new HeuristicFood(
            "pan-con-huevo", "Pan con Huevo",
            new String[] { "pan con huevo", "sandwich de huevo" },
            "unidad", 130, new NutritionProfile(235.0, 11.5, 28.0, 8.5), "armado"
        )
    );

    private Future<?> currentTask;
    private Object llmInference;
    private Method llmGenerateResponseMethod;
    private Method llmCloseMethod;
    private File runtimeModelFile;
    private String deliveryMode = "bundled-asset";
    private String lastError = null;
    private long runtimeRetryBlockedUntilMs = 0L;
    private int runtimeFailureCount = 0;

    private final Runnable autoUnloadRunnable = this::unloadRuntimeInternal;

    @Override
    public void load() {
        super.load();
        scheduleAutoUnload();
    }

    @Override
    protected void handleOnPause() {
        scheduleAutoUnload();
    }

    @Override
    protected void handleOnDestroy() {
        cancelCurrentTaskInternal();
        unloadRuntimeInternal();
        executor.shutdownNow();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        call.resolve(buildStatus(false));
    }

    @PluginMethod
    public void warmup(PluginCall call) {
        cancelCurrentTaskInternal();
        currentTask = executor.submit(() -> {
            try {
                ensureRuntimeIfPossible();
                resolveCall(call, buildStatus(llmInference != null));
            } catch (Exception error) {
                lastError = error.getMessage();
                Log.w(TAG, "Warmup failed", error);
                resolveCall(call, buildStatus(false));
            } finally {
                scheduleAutoUnload();
            }
        });
    }

    @PluginMethod
    public void analyzeNutritionDescription(PluginCall call) {
        String description = call.getString("description");
        if (description == null || description.trim().isEmpty()) {
            call.reject("Debe proveer una descripcion.");
            return;
        }

        List<String> knownFoods = jsArrayToStrings(call.getArray("knownFoods", new JSArray()));
        List<String> userMemory = jsArrayToStrings(call.getArray("userMemory", new JSArray()));

        cancelCurrentTaskInternal();
        currentTask = executor.submit(() -> {
            long startedAt = SystemClock.elapsedRealtime();
            try {
                JSONObject result;
                if (ensureRuntimeIfPossible()) {
                    result = analyzeWithRuntime(description.trim(), knownFoods, userMemory);
                } else {
                    result = analyzeWithHeuristics(description.trim(), knownFoods, userMemory);
                }

                result.put("elapsedMs", SystemClock.elapsedRealtime() - startedAt);
                if (!result.has("modelVersion")) {
                    result.put("modelVersion", llmInference != null ? MODEL_VERSION : JSONObject.NULL);
                }
                resolveCall(call, JSObject.fromJSONObject(result));
            } catch (Exception error) {
                lastError = error.getMessage();
                Log.w(TAG, "Native nutrition analysis failed, using heuristic fallback", error);

                try {
                    JSONObject fallback = analyzeWithHeuristics(description.trim(), knownFoods, userMemory);
                    fallback.put("elapsedMs", SystemClock.elapsedRealtime() - startedAt);
                    resolveCall(call, JSObject.fromJSONObject(fallback));
                } catch (Exception fallbackError) {
                    rejectCall(call, fallbackError.getMessage() != null ? fallbackError.getMessage() : "No se pudo analizar la descripcion.");
                }
            } finally {
                scheduleAutoUnload();
            }
        });
    }

    @PluginMethod
    public void cancelCurrentAnalysis(PluginCall call) {
        cancelCurrentTaskInternal();
        call.resolve();
    }

    @PluginMethod
    public void unload(PluginCall call) {
        cancelCurrentTaskInternal();
        unloadRuntimeInternal();
        call.resolve();
    }

    @PluginMethod
    public void resetRuntime(PluginCall call) {
        runtimeRetryBlockedUntilMs = 0L;
        runtimeFailureCount = 0;
        lastError = null;
        call.resolve(buildStatus(false));
    }

    private boolean ensureRuntimeIfPossible() {
        touchLastUsed();

        // Session-level kill switch
        if (runtimeFailureCount >= MAX_RUNTIME_FAILURES_PER_SESSION) {
            return false;
        }

        if (runtimeRetryBlockedUntilMs > SystemClock.elapsedRealtime()) {
            return false;
        }

        File modelFile = resolveModelFile();
        if (modelFile == null || !modelFile.exists()) {
            runtimeModelFile = null;
            return false;
        }

        if (llmInference != null && runtimeModelFile != null && runtimeModelFile.equals(modelFile)) {
            return true;
        }

        try {
            unloadRuntimeInternal();
            runtimeModelFile = modelFile;

            Class<?> llmClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference");
            Class<?> optionsClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference$LlmInferenceOptions");

            Method builderMethod = optionsClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);

            invokeBuilder(builder, "setModelPath", String.class, modelFile.getAbsolutePath());
            invokeBuilder(builder, "setMaxTokens", Integer.TYPE, 1024);
            if (!invokeBuilder(builder, "setMaxTopK", Integer.TYPE, 40)) {
                invokeBuilder(builder, "setTopK", Integer.TYPE, 40);
            }
            invokeBuilder(builder, "setTemperature", Float.TYPE, 0.15f);

            Method buildMethod = builder.getClass().getMethod("build");
            Object options = buildMethod.invoke(builder);

            Method createFromOptions = llmClass.getMethod("createFromOptions", Context.class, optionsClass);
            llmInference = createFromOptions.invoke(null, getContext(), options);
            llmGenerateResponseMethod = llmClass.getMethod("generateResponse", String.class);
            llmCloseMethod = llmClass.getMethod("close");
            lastError = null;
            runtimeRetryBlockedUntilMs = 0L;
            runtimeFailureCount = 0;
            return true;
        } catch (Throwable error) {
            unloadRuntimeInternal();
            runtimeModelFile = null;
            lastError = "Runtime local no disponible en esta JVM: " + error.getMessage();
            runtimeFailureCount++;
            runtimeRetryBlockedUntilMs = SystemClock.elapsedRealtime() + RUNTIME_RETRY_COOLDOWN_MS;
            Log.w(TAG, lastError + " (failure #" + runtimeFailureCount + ")", error);
            return false;
        }
    }

    private JSONObject analyzeWithRuntime(String description, List<String> knownFoods, List<String> userMemory) throws JSONException {
        if (llmInference == null) {
            throw new IllegalStateException("Runtime no inicializado.");
        }

        try {
            String response = (String) llmGenerateResponseMethod.invoke(llmInference, buildPrompt(description, knownFoods, userMemory));
            JSONObject parsed = extractAndValidateJson(response);
            enrichRuntimeItemsWithHeuristics(parsed);
            if (!parsed.has("modelVersion")) {
                parsed.put("modelVersion", MODEL_VERSION);
            }
            parsed.put("engine", "runtime");
            return parsed;
        } catch (Exception error) {
            throw new IllegalStateException("El runtime local no pudo generar una respuesta.", error);
        }
    }

    /** Cross-reference runtime LLM items against heuristic foods: prefer curated data when available. */
    private void enrichRuntimeItemsWithHeuristics(JSONObject parsed) throws JSONException {
        if (!parsed.has("items")) return;
        JSONArray items = parsed.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String canonicalName = item.optString("canonicalName", "");
            String normalizedCanonical = normalizeText(canonicalName);

            boolean matchedHeuristic = false;
            for (HeuristicFood heuristic : heuristicFoods) {
                if (normalizeText(heuristic.canonicalName).equals(normalizedCanonical)) {
                    // Replace LLM nutrition with curated heuristic data
                    JSONObject per100g = new JSONObject();
                    per100g.put("calories", heuristic.nutritionProfile.calories);
                    per100g.put("protein", heuristic.nutritionProfile.protein);
                    per100g.put("carbs", heuristic.nutritionProfile.carbs);
                    per100g.put("fats", heuristic.nutritionProfile.fats);
                    item.put("nutritionPer100g", per100g);
                    item.put("source", "local-heuristic");
                    matchedHeuristic = true;
                    break;
                }
            }

            if (!matchedHeuristic) {
                // Legitimate: LLM estimated these values. Keep them as-is.
                if (!item.has("source")) {
                    item.put("source", "local-ai-estimate");
                }
            }
        }
    }

    private JSONObject analyzeWithHeuristics(String description, List<String> knownFoods, List<String> userMemory) throws JSONException {
        String normalized = normalizeText(description);
        String working = normalized;
        JSONArray items = new JSONArray();
        double confidenceAccumulator = 0;

        for (HeuristicFood heuristicFood : heuristicFoods) {
            MatchResult match = heuristicFood.match(working, normalized, description, knownFoods, userMemory);
            if (match == null) {
                continue;
            }

            working = working.replace(match.aliasUsed, " ").replaceAll("\\s+", " ").trim();
            items.put(match.toJson());
            confidenceAccumulator += match.confidence;
        }

        JSONObject result = new JSONObject();
        result.put("items", items);
        result.put("overallConfidence", items.length() > 0 ? Math.min(0.92, confidenceAccumulator / items.length()) : 0.0);
        result.put("containsEstimatedItems", items.length() > 0);
        result.put("requiresReview", items.length() == 0);
        result.put("modelVersion", JSONObject.NULL);
        result.put("engine", "heuristics");
        if (items.length() == 0) {
            result.put("lastError", lastError != null ? lastError : "Modelo local no disponible en este build.");
        }
        return result;
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

    /** Extract JSON from LLM response and validate structure. */
    private JSONObject extractAndValidateJson(String response) throws JSONException {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }

        JSONObject parsed = new JSONObject(trimmed);
        normalizeResultObject(parsed);

        // Validate items structure
        JSONArray items = parsed.getJSONArray("items");
        JSONArray validItems = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                // Must have at least rawText or canonicalName as a string
                if (!item.has("rawText") && !item.has("canonicalName")) continue;
                String rawText = item.optString("rawText", item.optString("canonicalName", ""));
                if (rawText.isEmpty()) continue;
                // Ensure minimum fields
                if (!item.has("rawText")) item.put("rawText", rawText);
                if (!item.has("canonicalName")) item.put("canonicalName", rawText);
                if (!item.has("grams")) item.put("grams", 100);
                if (!item.has("quantity")) item.put("quantity", 1);
                if (!item.has("source")) item.put("source", "local-ai-estimate");
                if (!item.has("confidence")) item.put("confidence", 0.55);
                if (!item.has("reviewRequired")) item.put("reviewRequired", true);
                // Ensure nutritionPer100g exists
                if (!item.has("nutritionPer100g")) {
                    JSONObject fallbackNutrition = new JSONObject();
                    fallbackNutrition.put("calories", 100);
                    fallbackNutrition.put("protein", 5);
                    fallbackNutrition.put("carbs", 15);
                    fallbackNutrition.put("fats", 3);
                    item.put("nutritionPer100g", fallbackNutrition);
                    item.put("reviewRequired", true);
                }
                validItems.put(item);
            } catch (JSONException ignored) {
                // Skip malformed items
            }
        }
        parsed.put("items", validItems);
        return parsed;
    }

    // --- New hybrid prompt: parsing + nutrition estimation ---
    private String buildPrompt(String description, List<String> knownFoods, List<String> userMemory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Eres un asistente de nutricion. Dado un texto libre con comidas, haz dos cosas:\n");
        prompt.append("1. Extraer cada item alimentario con cantidad en gramos\n");
        prompt.append("2. Para cada item, estimar calorias y macros por 100g\n\n");
        prompt.append("Responde SOLO JSON valido, sin markdown ni explicaciones.\n\n");
        prompt.append("Schema:\n");
        prompt.append("{\"items\":[{\"rawText\":\"lo que escribio el usuario\",\"canonicalName\":\"nombre estandar\",\"grams\":150,\"quantity\":1,\"source\":\"local-ai-estimate\",\"confidence\":0.6,\"nutritionPer100g\":{\"calories\":130,\"protein\":2.7,\"carbs\":28,\"fats\":0.3},\"reviewRequired\":false}],\"overallConfidence\":0.6,\"containsEstimatedItems\":true,\"requiresReview\":false}\n\n");
        prompt.append("Reglas MUY ESTRICTAS para evitar alucinaciones:\n");
        prompt.append("- Las proteinas y carbohidratos aportan ~4 kcal/g, las grasas ~9 kcal/g. Asegurate de que (protein*4 + carbs*4 + fats*9) sea casi igual a las calorias reportadas.\n");
        prompt.append("- Mantente en rangos logicos: la mayoria de los alimentos crudos no superan las 300 kcal/100g, y los vegetales tienen menos de 50 kcal/100g por su gran contenido de agua.\n");
        prompt.append("- Usa tablas nutricionales comunes como referencia. No inventes macros extremos.\n");
        prompt.append("- Si es un plato o preparacion compleja (ej: empanada, arroz con pollo), promedia el perfil de sus ingredientes.\n");
        prompt.append("- Si el usuario no especifica gramos, estima una porcion tipica real.\n");
        prompt.append("- Usa los nombres de knownFoods cuando aplique de manera prioritaria.\n");
        prompt.append("- Considera que la respuesta se usa para un cálculo dietético estricto.\n");
        prompt.append("- Responde en espanol.\n");
        if (!knownFoods.isEmpty()) {
            prompt.append("knownFoods: ").append(TextUtils.join(", ", knownFoods.subList(0, Math.min(knownFoods.size(), 40)))).append("\n");
        }
        if (!userMemory.isEmpty()) {
            prompt.append("userMemory: ").append(TextUtils.join(", ", userMemory.subList(0, Math.min(userMemory.size(), 20)))).append("\n");
        }
        prompt.append("descripcion: ").append(description);
        return prompt.toString();
    }

    // --- Fix: available reflects reality ---
    private JSObject buildStatus(boolean runtimeReady) {
        JSObject status = new JSObject();
        boolean runtimeActive = runtimeReady || llmInference != null;
        File installTimeModel = findInstallTimePackModel();
        File privateModel = findPrivateModelFile();
        boolean bundledAssetExists = hasBundledModelAsset();
        boolean modelBundleFound = installTimeModel != null || privateModel != null || bundledAssetExists;
        String statusDeliveryMode = installTimeModel != null ? "install-time-pack" : "bundled-asset";
        if (runtimeModelFile != null) {
            statusDeliveryMode = deliveryMode;
        }
        // Fix: available = true ONLY if model bundle exists or runtime is active
        status.put("available", modelBundleFound || runtimeActive);
        status.put("heuristicsAvailable", true);
        status.put("modelReady", runtimeActive);
        status.put("modelVersion", (modelBundleFound || runtimeActive) ? MODEL_VERSION : null);
        status.put("deliveryMode", modelBundleFound || runtimeActive ? statusDeliveryMode : "bundled-asset");
        status.put("backend", "cpu");
        status.put("lastError", lastError);
        return status;
    }

    private File resolveModelFile() {
        // Install-time pack
        File installTimeFile = findInstallTimePackModel();
        if (installTimeFile != null) {
            deliveryMode = "install-time-pack";
            return installTimeFile;
        }

        // Private files
        File targetDir = new File(getContext().getFilesDir(), "local-ai");
        for (String name : new String[]{MODEL_FILENAME, MODEL_TASK_FILENAME}) {
            File f = new File(targetDir, name);
            if (f.exists() && f.length() > 0) {
                deliveryMode = "bundled-asset";
                return f;
            }
        }

        // Bundled assets
        for (String name : new String[]{MODEL_FILENAME, MODEL_TASK_FILENAME}) {
            if (assetExists("models/" + name)) {
                File copied = copyBundledAsset("models/" + name, name);
                if (copied != null) {
                    deliveryMode = "bundled-asset";
                    return copied;
                }
            }
        }
        return null;
    }

    private File findPrivateModelFile() {
        File targetDir = new File(getContext().getFilesDir(), "local-ai");
        for (String name : new String[]{MODEL_FILENAME, MODEL_TASK_FILENAME}) {
            File f = new File(targetDir, name);
            if (f.exists() && f.length() > 0) return f;
        }
        return null;
    }

    private boolean hasBundledModelAsset() {
        return assetExists("models/" + MODEL_FILENAME)
            || assetExists("models/" + MODEL_TASK_FILENAME);
    }

    private File findInstallTimePackModel() {
        try {
            AssetPackManager assetPackManager = AssetPackManagerFactory.getInstance(getContext());
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
        }

        return null;
    }

    private File copyBundledAsset(String assetPath, String targetName) {
        File targetDir = new File(getContext().getFilesDir(), "local-ai");
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            lastError = "No se pudo crear el directorio del modelo local.";
            return null;
        }

        File target = new File(targetDir, targetName);
        if (target.exists() && target.length() > 0) {
            return target;
        }

        try (InputStream input = getContext().getAssets().open(assetPath);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            return target;
        } catch (IOException error) {
            lastError = "No se pudo copiar el modelo local: " + error.getMessage();
            Log.w(TAG, lastError, error);
            return null;
        }
    }

    private boolean assetExists(String assetPath) {
        try (InputStream ignored = getContext().getAssets().open(assetPath)) {
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private List<String> jsArrayToStrings(JSArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }

        for (int index = 0; index < array.length(); index++) {
            String value = array.optString(index, "").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
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

    private void cancelCurrentTaskInternal() {
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }
        currentTask = null;
    }

    private void unloadRuntimeInternal() {
        mainHandler.removeCallbacks(autoUnloadRunnable);
        if (llmInference != null) {
            try {
                if (llmCloseMethod != null) {
                    llmCloseMethod.invoke(llmInference);
                }
            } catch (Exception error) {
                Log.w(TAG, "No se pudo cerrar el runtime local AI", error);
            }
        }
        llmInference = null;
        llmGenerateResponseMethod = null;
        llmCloseMethod = null;
        runtimeModelFile = null;
    }

    private void scheduleAutoUnload() {
        mainHandler.removeCallbacks(autoUnloadRunnable);
        mainHandler.postDelayed(autoUnloadRunnable, INACTIVITY_TIMEOUT_MS);
    }

    private void touchLastUsed() {
        lastError = null;
        scheduleAutoUnload();
    }

    private void resolveCall(PluginCall call, JSObject result) {
        mainHandler.post(() -> call.resolve(result));
    }

    private void rejectCall(PluginCall call, String message) {
        mainHandler.post(() -> call.reject(message));
    }

    private static String normalizeText(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        return normalized;
    }

    /**
     * Fix: Parse explicit grams from RAW text (before normalizeText strips decimals).
     * The regex expects digits, dots, and commas which normalizeText removes.
     */
    private static double parseExplicitGrams(String rawText) {
        Matcher matcher = EXPLICIT_GRAMS_PATTERN.matcher(rawText);
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
        Matcher matcher = pattern.matcher(normalizedText);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1).replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }

        if (normalizedText.contains("un " + quantityKeyword) || normalizedText.contains("una " + quantityKeyword)) {
            return 1;
        }

        return 1;
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
            // Fix: heuristic items are honestly labeled
            item.put("source", "local-heuristic");
            item.put("confidence", confidence);
            item.put("reviewRequired", confidence < 0.55);

            JSONObject per100g = new JSONObject();
            per100g.put("calories", nutritionProfile.calories);
            per100g.put("protein", nutritionProfile.protein);
            per100g.put("carbs", nutritionProfile.carbs);
            per100g.put("fats", nutritionProfile.fats);
            item.put("nutritionPer100g", per100g);
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

        /**
         * @param workingText normalized text with previously matched aliases removed
         * @param originalNormalizedText full normalized text for quantity parsing
         * @param originalRawText raw user input for decimal grams parsing (fix)
         */
        MatchResult match(String workingText, String originalNormalizedText, String originalRawText, List<String> knownFoods, List<String> userMemory) {
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
            // Fix: parse grams from RAW text so "200.5g" and "200,5g" work
            double explicitGrams = parseExplicitGrams(originalRawText);
            double grams = explicitGrams > 0 ? explicitGrams : (defaultGrams * Math.max(1, quantity));
            double confidence = 0.72;

            if (knownFoods.stream().anyMatch(hint -> normalizeText(hint).equals(normalizeText(canonicalName)))) {
                confidence += 0.08;
            }

            if (userMemory.stream().anyMatch(memory -> normalizeText(memory).contains(normalizeText(canonicalName)))) {
                confidence += 0.06;
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
}
