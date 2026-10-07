package com.example.kpkn.domain.training.generator

import com.example.kpkn.domain.onboarding.MuscleSymbol

/**
 * Textos de la rutina (nombre, frase, razones y notas). Español neutro, sin género gramatical cuando se puede.
 * Cada nota de límite es accionable: dice qué falta Y qué lo resuelve.
 */
internal object RoutineNarrative {

    private val dayNames = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

    fun dayName(day: Int): String = dayNames[(day - 1).coerceIn(0, 6)]

    fun dayNameCapitalized(day: Int): String = dayName(day).replaceFirstChar { it.uppercase() }

    private fun days(n: Int): String = if (n == 1) "1 día" else "$n días"

    fun suggestedName(ctx: GenContext): String {
        val base = ctx.mode.label
        val tail = days(ctx.days.size)
        return "$base · $tail"
    }

    /** Reparto de la semana en una frase corta. */
    private fun splitDescription(ctx: GenContext): String {
        val n = ctx.days.size
        return when (ctx.mode) {
            RoutineMode.GENERAL_HYBRID -> when (n) {
                1 -> "una sesión mixta de fuerza y cardio"
                2 -> "fuerza de cuerpo completo y un día de cardio y potencia"
                else -> "fuerza de pierna y de torso, cardio y sesiones mixtas"
            }
            RoutineMode.GENERAL_FUNCTIONAL -> "cuerpo completo funcional con potencia, cardio suave y movilidad"
            else -> when (n) {
                1 -> "cuerpo completo"
                2 -> "cuerpo completo A y B"
                3 -> if (ctx.level.ordinal >= RoutineLevel.INTERMEDIATE.ordinal && ctx.targetMinutes >= 60) {
                    "torso, pierna y cuerpo completo"
                } else {
                    "cuerpo completo A, B y C"
                }
                4 -> "torso y pierna dos veces por semana"
                5 -> "torso y pierna, más empuje, tirón y pierna"
                6 -> "empuje, tirón y pierna dos veces por semana"
                else -> "empuje, tirón y pierna dos veces, más un día de movilidad y cardio suave"
            }
        }
    }

    fun oneLiner(ctx: GenContext, results: List<AssembledSession>): String {
        val avg = results.map { it.minutes }.average().let { if (it.isNaN()) ctx.targetMinutes else it.toInt() }
        return "${days(ctx.days.size).replaceFirstChar { it.uppercase() }} de ${splitDescription(ctx)}, unos $avg min por sesión."
    }

    private fun priorityLabels(symbols: List<MuscleSymbol>): String {
        val labels = symbols.distinct().take(5).map { it.label.lowercase() }
        return when (labels.size) {
            0 -> ""
            1 -> labels[0]
            else -> labels.dropLast(1).joinToString(", ") + " y " + labels.last()
        }
    }

    fun reasons(ctx: GenContext, results: List<AssembledSession>, mainDay: Int): List<String> {
        val out = ArrayList<String>()
        val n = ctx.days.size
        // 1) Días → reparto.
        out += when (ctx.mode) {
            RoutineMode.GENERAL_HYBRID -> "Con ${days(n)} repartimos ${splitDescription(ctx)}: el cardio no se recorta y la fuerza se concentra donde más rinde."
            RoutineMode.GENERAL_FUNCTIONAL -> "Con ${days(n)}, todas las sesiones son de cuerpo completo funcional: bisagra, sentadilla, empuje, tirón, acarreo y rotación."
            else -> when (n) {
                1 -> "Con 1 día, una sola sesión de cuerpo completo: los patrones esenciales entran todos en cada visita."
                2 -> "Con 2 días, cuerpo completo A y B: cada patrón se entrena en una sesión y los accesorios se alternan."
                3 -> "Con 3 días, ${splitDescription(ctx)}: cada músculo se trabaja 2 o 3 veces por semana con descanso entre sesiones."
                4 -> "Con 4 días, torso y pierna dos veces por semana: cada músculo se entrena 2 veces con 48 h de descanso."
                5 -> "Con 5 días, torso y pierna más empuje, tirón y pierna: más frecuencia sin repetir el mismo músculo dos días seguidos."
                6 -> "Con 6 días, empuje, tirón y pierna dos veces por semana: cada músculo se entrena 2 veces."
                else -> "Con 7 días, seis de entrenamiento y uno de movilidad y cardio suave: nunca siete días de pesas."
            }
        }
        // 2) Minutos → estructura.
        val strength = results.filter { it.strengthCount > 0 }
        val avgExercises = if (strength.isEmpty()) 0 else (strength.sumOf { it.strengthCount }.toDouble() / strength.size).toInt()
        out += if (strength.isEmpty()) {
            "Con ${ctx.targetMinutes} min por sesión, el plan se llena de cardio y movilidad."
        } else {
            "Con ${ctx.targetMinutes} min por sesión, cada sesión de fuerza lleva ${avgExercises} ejercicios: primero los compuestos y después los accesorios" +
                if (results.any { it.hasCardio }) ", con el cardio en su sitio." else "."
        }
        // 3) Día fresco.
        val freshness = ctx.request.freshestDay
        val mainSession = results.firstOrNull { it.dayOfWeek == mainDay }
        out += if (freshness != null && freshness == mainDay) {
            "Tu sesión más exigente (${mainSession?.plan?.title ?: "la principal"}) cae el ${dayName(mainDay)}, el día que llegas con más energía."
        } else if (freshness != null) {
            "Tu día con más energía no es de entreno: la sesión más exigente (${mainSession?.plan?.title ?: "la principal"}) pasa al ${dayName(mainDay)}, el primer día de entreno después."
        } else {
            "La sesión más exigente (${mainSession?.plan?.title ?: "la principal"}) cae el ${dayName(mainDay)}, el día que sigue a tu descanso más largo."
        }
        // 4) Prioridades.
        val priorities = ctx.request.priorityMuscles.distinct().take(5)
        if (priorities.isNotEmpty()) {
            out += "Prioridad en ${priorityLabels(priorities)}: +25 % de series en sus ejercicios, un ejercicio extra y van al principio de la sesión."
        }
        // 5) Variantes y material.
        val variants = results.flatMap { it.session.allExercises() }.mapNotNull { it.variantName }.distinct()
        val bodyweightOnly = ctx.request.availability.categories.isEmpty()
        if (variants.isNotEmpty()) {
            out += "Elegimos variantes a tu medida (${variants.take(3).joinToString(", ") { it.replaceFirstChar { c -> c.lowercase() } }}) según lo que ya te sale."
        } else if (bodyweightOnly) {
            out += "Sin material, el plan usa tu peso corporal con progresiones que puedes subir o bajar según te salgan."
        }
        return out.take(5)
    }

    // ─── Notas de límites ───────────────────────────────────────────────────────────────────────────────────

    private val patternAdvice: Map<RoutinePattern, String> = mapOf(
        RoutinePattern.VERTICAL_PULL to
            "Sin barra de dominadas, poleas ni bandas con una barra donde anclarlas no hay tracción vertical: añadir una barra de dominadas o unas bandas completa tu semana.",
        RoutinePattern.HORIZONTAL_PULL to
            "Sin barra baja, bandas, mancuernas, poleas ni barra no hay remo: añadir unas bandas o unas mancuernas completa tu espalda.",
        RoutinePattern.VERTICAL_PUSH to
            "Sin mancuernas, barra, kettlebell ni poleas no hay empuje vertical (press de hombros): unas mancuernas o una barra lo resuelven.",
        RoutinePattern.HORIZONTAL_PUSH to
            "Con tu material no hay empuje horizontal: unas mancuernas, una barra o simplemente suelo y manos (flexiones) lo cubren.",
        RoutinePattern.SQUAT to
            "Con tu material no hay sentadilla cargada: unas mancuernas, una kettlebell o una barra con rack la completan.",
        RoutinePattern.SINGLE_LEG to
            "Con tu material no hay trabajo a una pierna: unas mancuernas o una kettlebell lo completan.",
        RoutinePattern.POWER to
            "Sin kettlebell, mancuernas ni barra no hay trabajo de potencia (balanceos, press de impulso): una kettlebell lo resuelve; los saltos y lanzamientos aún no están en el catálogo.",
    )

    private val isolationLabels: Map<RoutinePattern, String> = mapOf(
        RoutinePattern.BICEPS to "bíceps",
        RoutinePattern.TRICEPS to "tríceps",
        RoutinePattern.SHOULDER_LATERAL to "hombro lateral",
        RoutinePattern.REAR_DELT to "hombro posterior",
        RoutinePattern.CHEST_ISOLATION to "pecho aislado",
        RoutinePattern.HAMSTRING_CURL to "femoral aislado",
        RoutinePattern.QUAD_ISOLATION to "cuádriceps aislado",
        RoutinePattern.CALF to "pantorrilla",
        RoutinePattern.TRAPS to "trapecio",
        RoutinePattern.GRIP to "antebrazo y agarre",
        RoutinePattern.CORE_ROTATION to "core de rotación",
        RoutinePattern.CARRY to "acarreos",
    )

    /** Una sola nota con las sesiones que no entraron en la ventana de minutos y por qué. */
    fun timeNote(ctx: GenContext): String? {
        if (ctx.outside.isEmpty()) return null
        val detail = ctx.outside.joinToString("; ") { (title, minutes) -> "$title $minutes min" }
        return "Tiempo: con ${ctx.targetMinutes} min por sesión, $detail quedan fuera del rango del ${ctx.windowMinutes.first}–${ctx.windowMinutes.last} min: " +
            "con tu material y tus techos de volumen es lo más cerca que se llega."
    }

    /** Notas por los patrones que ninguna sesión pudo cubrir (accionables) y por la bisagra sin carga. */
    fun gapNotes(ctx: GenContext, missing: Set<RoutinePattern>): List<String> {
        val notes = ArrayList<String>()
        patternAdvice.forEach { (pattern, text) -> if (pattern in missing) notes += text }
        if (RoutinePattern.HINGE in missing) {
            notes += "Sin carga no hay bisagra de cadera (peso muerto): unas mancuernas o una kettlebell la completan."
        } else if ("hinge_bodyweight" in ctx.flags) {
            notes += "Sin carga la bisagra de cadera se hace con puentes de glúteos (el catálogo no trae peso muerto a una pierna sin carga): unas mancuernas o una kettlebell la completan."
        }
        if ("pull_gap_compensation" in ctx.flags) {
            notes += "Como no hay tracción, la semana suma extensiones de espalda y trabajo escapular; no sustituyen a un remo ni a una dominada."
        }
        val isolation = isolationLabels.filterKeys { it in missing }.values.toList()
        if (isolation.isNotEmpty()) {
            notes += "Con tu material no hay ejercicio específico de ${isolation.joinToString(", ")}: unas mancuernas, unas poleas o unas bandas lo cubren."
        }
        return notes
    }
}
