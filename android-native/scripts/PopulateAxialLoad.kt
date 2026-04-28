#!/usr/bin/env kotlin
/**
 * PopulateAxialLoad.kt — Script determinista para poblar `axialLoadFactor` en exercise_database.json.
 *
 * Uso:
 *   kotlinc -script PopulateAxialLoad.kt -- [ruta/a/exercise_database.json]
 *   # Si no se especifica ruta, busca en app/src/main/assets/exercise_database.json
 *
 * Reglas de mapeo (fuente: documento de diseño KPKN AUGE):
 *   1.0  → carga axial máxima: sentadilla back, peso muerto, good morning, zercher squat
 *   0.9  → movimientos olímpicos: clean, snatch, cargada, arranque
 *   0.8  → carga axial alta: OHP/press militar, push press, hack squat (barra)
 *   0.3  → carga moderada: row con barra, bench press, press banca (barra libre horizontal)
 *   0.1  → carga mínima: smith/multipower, máquina selectorizada, sentado/acostado
 *   0.0  → aislamiento: curl, extensión, elevación lateral, etc.
 *   null → no matchea ningún patrón — no inventar
 *
 * Solo sobreescribe `axialLoadFactor` si el ejercicio no lo tiene ya declarado.
 * Commit sugerido: "Populate axialLoadFactor via deterministic script (PopulateAxialLoad.kt)"
 */

import java.io.File

// ─── Reglas de asignación (orden: más específico primero) ───────────────────

data class AxialRule(val pattern: Regex, val factor: Double, val description: String)

val AXIAL_RULES = listOf(
    // 1.0 — Carga axial máxima
    AxialRule(Regex("peso muerto|deadlift|good morning|zercher|sentadilla back|back squat|low bar|high bar squat", RegexOption.IGNORE_CASE), 1.0, "carga axial máxima"),

    // 0.9 — Movimientos olímpicos
    AxialRule(Regex("\\bclean\\b|\\bsnatch\\b|cargada|arranque|clean and jerk|power clean|hang clean", RegexOption.IGNORE_CASE), 0.9, "movimiento olímpico"),

    // 0.8 — Press militar / OHP / hack squat barra
    AxialRule(Regex("press militar|overhead press|\\bohp\\b|push press|hack squat", RegexOption.IGNORE_CASE), 0.8, "carga axial alta"),

    // 0.3 — Remo con barra, bench press/press banca barra horizontal
    AxialRule(Regex("remo.*barra|barbell row|press banca|bench press|press de pecho.*barra", RegexOption.IGNORE_CASE), 0.3, "carga axial moderada"),

    // 0.1 — Smith/multipower, máquina, selectorizado, sentado, acostado, inclinado máquina
    AxialRule(Regex("smith|multipower|máquina|maquina|selectorizado|sentado|acostado|banca inclinada máquina", RegexOption.IGNORE_CASE), 0.1, "carga mínima (máquina/sentado)"),

    // 0.0 — Ejercicios de aislamiento
    AxialRule(Regex("\\bcurl\\b|extensión.*cuádriceps|extensión de pierna|leg extension|elevación lateral|elevación frontal|fly|pec deck|face pull|pullover", RegexOption.IGNORE_CASE), 0.0, "aislamiento"),
)

/**
 * Determina el factor de carga axial para el nombre del ejercicio.
 * Retorna null si ningún patrón coincide (no inventar).
 */
fun resolveAxialLoad(name: String): Double? {
    for (rule in AXIAL_RULES) {
        if (rule.pattern.containsMatchIn(name)) return rule.factor
    }
    return null
}

// ─── Main ────────────────────────────────────────────────────────────────────

val dbPath = args.getOrNull(0)
    ?: "app/src/main/assets/exercise_database.json"

val dbFile = File(dbPath).let { if (it.isAbsolute) it else File(System.getProperty("user.dir"), dbPath) }

if (!dbFile.exists()) {
    System.err.println("Error: No se encontró $dbPath")
    System.err.println("Uso: kotlinc -script PopulateAxialLoad.kt -- [ruta/exercise_database.json]")
    System.exit(1)
}

println("Leyendo: ${dbFile.absolutePath}")
val original = dbFile.readText()

// Regex para parsear campos en cada entrada JSON de ejercicio.
// Procesamos línea a línea para preservar formato original.
var populated = 0
var skipped = 0
var unchanged = 0

// Patrón para extraer el nombre del ejercicio de la entrada JSON actual
val namePattern = Regex(""""name"\s*:\s*"([^"]+)"""")
val axialExistingPattern = Regex(""""axialLoadFactor"\s*:\s*(?:null|-?\d+(?:\.\d+)?)""")
val idPattern = Regex(""""id"\s*:\s*"([^"]+)"""")

// Procesamos el JSON como texto (sin parsear para preservar formato)
// Estrategia: encontrar cada objeto ejercicio y añadir/actualizar axialLoadFactor
val lines = original.lines().toMutableList()
val output = StringBuilder()

var currentExerciseLines = mutableListOf<String>()
var inExercise = false
var braceDepth = 0

fun processExercise(exerciseLines: MutableList<String>): List<String> {
    val exerciseText = exerciseLines.joinToString("\n")
    val name = namePattern.find(exerciseText)?.groupValues?.get(1) ?: return exerciseLines
    val id = idPattern.find(exerciseText)?.groupValues?.get(1) ?: "?"

    val factor = resolveAxialLoad(name)

    // Verificar si ya tiene axialLoadFactor declarado
    val hasExisting = axialExistingPattern.containsMatchIn(exerciseText)
    if (hasExisting) {
        unchanged++
        return exerciseLines
    }

    if (factor == null) {
        skipped++
        return exerciseLines
    }

    // Insertar axialLoadFactor antes del cierre del objeto
    // Buscamos la última línea que no sea "}" para insertar antes del cierre
    val factorLine = "  \"axialLoadFactor\": $factor"
    val result = exerciseLines.toMutableList()

    // Encontrar la línea del cierre "}," o "}" del objeto ejercicio
    val lastContentIdx = result.indexOfLast { it.trim().isNotEmpty() && !it.trim().startsWith("}") }
    if (lastContentIdx >= 0) {
        // Añadir coma en la última línea de contenido si no la tiene
        val lastContent = result[lastContentIdx]
        if (!lastContent.trimEnd().endsWith(",")) {
            result[lastContentIdx] = "$lastContent,"
        }
        result.add(lastContentIdx + 1, factorLine)
        populated++
        println("  [$id] \"$name\" → axialLoadFactor = $factor")
    } else {
        skipped++
    }
    return result
}

// Procesamiento simple: asume que los ejercicios son objetos JSON de primer nivel en un array
var depth = 0
var exerciseStart = -1
val resultLines = mutableListOf<String>()

for ((idx, line) in lines.withIndex()) {
    val opens = line.count { it == '{' }
    val closes = line.count { it == '}' }
    val prevDepth = depth
    depth += opens - closes

    if (prevDepth == 1 && depth == 1 && opens == 0 && closes == 0) {
        // Línea de contenido dentro de un ejercicio
    }

    if (prevDepth == 0 && depth == 1) {
        // Apertura de ejercicio
        exerciseStart = resultLines.size
    }

    resultLines.add(line)

    if (prevDepth == 1 && depth == 0) {
        // Cierre de ejercicio — procesar bloque
        if (exerciseStart >= 0) {
            val exerciseLines = resultLines.subList(exerciseStart, resultLines.size).toMutableList()
            val processed = processExercise(exerciseLines)
            // Reemplazar las líneas en resultLines
            for (i in exerciseLines.indices) {
                resultLines[exerciseStart + i] = processed.getOrElse(i) { "" }
            }
            // Si processed tiene más líneas (insertamos), añadir las extra
            if (processed.size > exerciseLines.size) {
                val extra = processed.drop(exerciseLines.size)
                resultLines.addAll(exerciseStart + exerciseLines.size, extra)
            }
        }
        exerciseStart = -1
    }
}

val outputText = resultLines.joinToString("\n")
dbFile.writeText(outputText)

println()
println("=== Resultado PopulateAxialLoad ===")
println("Poblados:   $populated")
println("Omitidos (sin patrón): $skipped")
println("Sin cambio (ya tenían axialLoadFactor): $unchanged")
println("Archivo actualizado: ${dbFile.absolutePath}")
println()
println("Próximo paso: revisar el diff con `git diff app/src/main/assets/exercise_database.json`")
println("y hacer commit con: git commit -m 'Populate axialLoadFactor via deterministic script'")
