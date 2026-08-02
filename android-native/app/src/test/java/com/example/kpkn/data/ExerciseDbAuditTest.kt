package com.example.kpkn.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * Auditoría de calidad de datos de exercise_database.json.
 * No falla el build — solo reporta warnings que el equipo debe corregir manualmente.
 * Output en: build/reports/exercise_audit.txt
 */
class ExerciseDbAuditTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Localiza exercise_database.json buscando desde la raíz del módulo hacia arriba.
     * Funciona tanto en Robolectric (resources) como en JVM puro (classpath search).
     */
    private fun findDbFile(): File {
        // Intento 1: resource en classpath de test
        val resource = javaClass.classLoader?.getResource("exercise_database.json")
        if (resource != null) return File(resource.toURI())

        // Intento 2: ruta relativa desde el directorio de trabajo (Gradle)
        val candidates = listOf(
            "../../catalog/exercises/v2/curation/evidence/legacy/exercise_database.json",
            "../catalog/exercises/v2/curation/evidence/legacy/exercise_database.json",
            "catalog/exercises/v2/curation/evidence/legacy/exercise_database.json",
        )
        for (path in candidates) {
            val f = File(path)
            if (f.exists()) return f
        }
        error("No se encontró exercise_database.json. Ejecuta la auditoría desde el módulo :app.")
    }

    @Test
    fun auditExerciseDatabase() {
        val dbFile = try { findDbFile() } catch (e: Exception) {
            println("⚠ Auditoría omitida: ${e.message}")
            return
        }

        val root = json.parseToJsonElement(dbFile.readText())
        val exercises: List<JsonObject> = when {
            root is JsonArray -> root.map { it.jsonObject }
            root is JsonObject && root.containsKey("exercises") ->
                root["exercises"]!!.jsonArray.map { it.jsonObject }
            else -> {
                println("⚠ Formato de DB no reconocido")
                return
            }
        }

        val warnings = mutableListOf<String>()
        val totalCount = exercises.size

        for (ex in exercises) {
            val id = ex["id"]?.jsonPrimitive?.contentOrNull ?: "?"
            val name = ex["name"]?.jsonPrimitive?.contentOrNull ?: "?"
            val nameLower = name.lowercase()

            val muscles = ex["involvedMuscles"]?.jsonArray ?: continue

            // 1. Ejercicios con > 3 músculos PRIMARY
            val primaryCount = muscles.count { m ->
                m.jsonObject["role"]?.jsonPrimitive?.contentOrNull?.equals("PRIMARY", ignoreCase = true) == true
            }
            if (primaryCount > 3) {
                warnings += "[$id] \"$name\" tiene $primaryCount músculos PRIMARY (>3)"
            }

            // 2. Press de pecho con tríceps PRIMARY
            if (nameLower.contains("press banca") || nameLower.contains("bench press") ||
                nameLower.contains("press de pecho") || nameLower.contains("press inclinado")
            ) {
                muscles.forEach { m ->
                    val muscleName = m.jsonObject["muscle"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                    val role = m.jsonObject["role"]?.jsonPrimitive?.contentOrNull ?: ""
                    if ((muscleName.contains("tríceps") || muscleName.contains("triceps")) &&
                        role.equals("PRIMARY", ignoreCase = true)
                    ) {
                        warnings += "[$id] \"$name\" tiene tríceps como PRIMARY (debería ser SECONDARY, activation≈0.5)"
                    }
                }
            }

            // 3. Sentadillas con glúteos SECONDARY — revisión manual
            if (nameLower.contains("sentadilla") || nameLower.contains("squat")) {
                muscles.forEach { m ->
                    val muscleName = m.jsonObject["muscle"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                    val role = m.jsonObject["role"]?.jsonPrimitive?.contentOrNull ?: ""
                    if ((muscleName.contains("glúteo") || muscleName.contains("gluteo")) &&
                        role.equals("SECONDARY", ignoreCase = true)
                    ) {
                        warnings += "[$id] \"$name\" → glúteos SECONDARY (revisar si PRIMARY es más apropiado)"
                    }
                }
            }

            // 4. involvedMuscles vacío
            if (muscles.isEmpty()) {
                warnings += "[$id] \"$name\" no tiene involvedMuscles definidos"
            }

            // 5. Declara tanto "Core" como "Abdomen" simultáneamente
            val muscleNames = muscles.mapNotNull {
                it.jsonObject["muscle"]?.jsonPrimitive?.contentOrNull?.lowercase()
            }
            val hasCore = muscleNames.any { it == "core" || it.contains("transverso") || it.contains("serrato") }
            val hasAbdomen = muscleNames.any { it.contains("abdominal") || it.contains("abdomen") || it.contains("oblicuo") }
            if (hasCore && hasAbdomen) {
                warnings += "[$id] \"$name\" declara Core Y Abdomen simultáneamente — " +
                    "verificar si es correcto o duplicado"
            }

            // 6. Mismo muscle listado dos veces sin emphasis distinto
            val muscleEmphasisPairs = muscles.mapNotNull { m ->
                val muscleName = m.jsonObject["muscle"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val emphasis = m.jsonObject["emphasis"]?.jsonPrimitive?.contentOrNull
                muscleName to emphasis
            }
            val duplicates = muscleEmphasisPairs
                .groupBy { it }
                .filter { it.value.size > 1 }
                .keys
            if (duplicates.isNotEmpty()) {
                duplicates.forEach { (muscle, emphasis) ->
                    warnings += "[$id] \"$name\" → músculo \"$muscle\" (emphasis=$emphasis) listado ${
                        muscleEmphasisPairs.count { it == muscle to emphasis }
                    } veces"
                }
            }

            // 7. Falta efc/cnc/ssc (sin valor en DB — afecta cálculos AUGE)
            val hasEfc = ex["efc"]?.jsonPrimitive?.contentOrNull != null
            val hasCnc = ex["cnc"]?.jsonPrimitive?.contentOrNull != null
            val hasSsc = ex["ssc"]?.jsonPrimitive?.contentOrNull != null
            if (!hasEfc || !hasCnc || !hasSsc) {
                val missing = listOfNotNull(
                    if (!hasEfc) "efc" else null,
                    if (!hasCnc) "cnc" else null,
                    if (!hasSsc) "ssc" else null,
                ).joinToString(", ")
                warnings += "[$id] \"$name\" falta campo(s) AUGE: $missing → mostrará '—' en MyRings"
            }
        }

        // Escribir reporte
        val reportDir = File("build/reports")
        reportDir.mkdirs()
        val reportFile = File(reportDir, "exercise_audit.txt")
        val report = buildString {
            appendLine("=== KPKN Exercise DB Audit ===")
            appendLine("Ejercicios analizados: $totalCount")
            appendLine("Warnings: ${warnings.size}")
            appendLine()
            if (warnings.isEmpty()) {
                appendLine("✓ Sin problemas detectados.")
            } else {
                warnings.forEachIndexed { i, w -> appendLine("${i + 1}. $w") }
            }
        }
        reportFile.writeText(report)
        println(report)

        // No falla el test — solo informa
        if (warnings.isNotEmpty()) {
            println("⚠ ${warnings.size} warnings encontrados. Ver build/reports/exercise_audit.txt")
        }
    }
}
