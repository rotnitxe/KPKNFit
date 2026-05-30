# Skill: Inferencia IA Local y Offline (Local AI Specialist)

Esta guía documenta los estándares de configuración, staging y ejecución de inferencia de Inteligencia Artificial local (On-Device AI) en la aplicación nativa KPKN Fit mediante el modelo optimizado **`kpkn-food-fg270m-v1`** (FunctionGemma 270M) y tuberías heurísticas auxiliares.

---

## 🧠 1. Arquitectura de Inferencia Local en Android
Para garantizar privacidad extrema, respuesta en milisegundos y operatividad total sin internet, la aplicación nativa en Kotlin incorpora procesamiento inteligente offline para la categorización e interpretación de cadenas de texto libre de alimentos.

### El Flujo de Datos (Pipeline):
1. **Input de Usuario**: El atleta ingresa en texto libre lo que comió (ej. *"2 tazas de arroz integral cocido con 150 gramos de pechuga de pollo a la plancha"*).
2. **Puente Local de Inferencia**: Se invoca el servicio de enlace del modelo (`LocalAiPlugin`/`LocalFoodParser`).
3. **Validación de Peso**: Si el modelo está staged (instalado en el almacenamiento local del dispositivo), se ejecuta la inferencia local de baja latencia.
4. **Tubería de Respaldo Offline (Heuristic Fallback)**: Si el dispositivo cuenta con recursos de memoria extremadamente bajos o el modelo no está inicializado, se activa la tubería heurística de expresiones regulares y búsqueda en catálogo local chileno.

---

## ⚙️ 2. Staging y Verificación del Modelo
El modelo debe residir en el almacenamiento interno de la app. Los scripts de compilación automatizan la preparación de estos binarios.

```kotlin
class LocalAiModelManager(private val context: Context) {
    private val modelFileName = "kpkn-food-fg270m-v1.bin"

    // Verificar si los archivos del modelo de lenguaje local están listos para inferencia
    fun isModelStaged(): Boolean {
        val modelFile = File(context.filesDir, "models/$modelFileName")
        return modelFile.exists() && modelFile.length() > 100 * 1024 * 1024 // Debe pesar más de 100MB
    }

    // Copiar el modelo de la carpeta assets o descargas a la memoria interna protegida
    suspend fun stageModel(sourceUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val destinationFile = File(context.filesDir, "models/$modelFileName")
            destinationFile.parentFile?.mkdirs()
            
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            TelemetryHelper.logNonCriticalError("Fallo al copiar modelo IA local: ${e.message}")
            false
        }
    }
}
```

---

## 🍲 3. Parse Inteligente Heurístico (Offline Fallback)
Cuando no hay modelo de lenguaje cargado, el parser local interpreta la entrada separándola en cantidades, unidades y emparejándolo con la base de datos de alimentos de USDA o base local chilena precargada.

```kotlin
data class ParsedFoodItem(
    val originalText: String,
    val quantity: Float,
    val unit: String,
    val foodName: String,
    val estimatedCalories: Float
)

class HeuristicOfflineParser(private val foodCatalog: FoodCatalogRepository) {
    private val portionPattern = Regex("""(\d+(?:[.,]\d+)?)\s*(g|gr|gramos|ml|taza|tazas|vaso|vasos|unidad|unidades|rebanada|rebanadas)\b""", RegexOption.IGNORE_CASE)

    suspend fun parseFreeFormText(input: String): List<ParsedFoodItem> = withContext(Dispatchers.Default) {
        val items = input.split(Regex("(?i)\\b(con|y|e)\\b")).map { it.trim() }
        
        items.mapNotNull { rawText ->
            val match = portionPattern.find(rawText)
            val quantity = match?.groupValues?.get(1)?.replace(",", ".")?.toFloatOrNull() ?: 1.0f
            val unit = match?.groupValues?.get(2)?.lowercase() ?: "unidad"
            
            // Remover la porción descubierta para quedarnos con el nombre puro del alimento
            val foodCleanName = if (match != null) {
                rawText.replace(match.value, "").trim()
            } else {
                rawText
            }

            // Buscar coincidencia en la base de datos local preindexada
            val matchedFood = foodCatalog.searchFoodLocal(foodCleanName)
            
            matchedFood?.let { food ->
                ParsedFoodItem(
                    originalText = rawText,
                    quantity = quantity,
                    unit = unit,
                    foodName = food.name,
                    estimatedCalories = food.caloriesPer100g * (quantity / 100f) // Asumiendo gramos base
                )
            }
        }
    }
}
```

---

## 🚀 4. Integración del Cliente de Inferencia On-Device
```kotlin
class NutritionAiService(
    private val localAiManager: LocalAiModelManager,
    private val heuristicParser: HeuristicOfflineParser
) {
    suspend fun processDailyNutritionInput(text: String): List<ParsedFoodItem> {
        return if (localAiManager.isModelStaged()) {
            try {
                // Invocación a través de librería cargada en C++ nativo (TensorFlow Lite o Gemma C++)
                executeOnDeviceInference(text)
            } catch (e: Exception) {
                TelemetryHelper.logNonCriticalError("Inferencia fallida, activando fallback heurístico: ${e.message}")
                heuristicParser.parseFreeFormText(text)
            }
        } else {
            // El modelo no está instalado, usar el analizador heurístico local
            heuristicParser.parseFreeFormText(text)
        }
    }

    private suspend fun executeOnDeviceInference(text: String): List<ParsedFoodItem> = withContext(Dispatchers.Default) {
        // Enlace JNI nativo para correr FunctionGemma 270M
        // Retorna mapeo estructurado del JSON de salida
        delay(150) // Simulación de latencia de GPU/NPU local rápida
        emptyList()
    }
}
```
