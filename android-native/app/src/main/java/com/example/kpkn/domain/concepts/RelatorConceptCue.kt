package com.example.kpkn.domain.concepts

import com.example.kpkn.data.models.IntensityMode

const val RELATOR_AXIAL_CUE_MIN = 0.7

data class RelatorConceptSignals(
    val axialLoadFactor: Double? = null,
    val equipmentId: String? = null,
    val movementPatternId: String? = null,
    val exerciseName: String = "",
    val intensityMode: IntensityMode? = null,
    val plannedIntensity: Double? = null,
    val plannedFailure: Boolean = false,
    val plannedReps: Double? = null,
    val plannedDropCount: Int = 0,
    val hasIsoHold: Boolean = false,
    val hasNegatives: Boolean = false,
    val isCompound: Boolean = false,
    val shownConceptIds: Set<String> = emptySet(),
)

data class RelatorConceptCue(
    val id: String,
    val lines: List<String>,
)

private val RELATOR_CONCEPT_LINES: Map<String, List<String>> = mapOf(
    "carga-axial" to listOf(
        "Carga axial: la barra comprime en vertical por tu columna. Baja con el tronco firme.",
        "Esto es carga axial: el peso viaja a lo largo de tu columna. Mantén la postura.",
        "Carga axial: comprime en vertical. No dejes que el tronco se rompa.",
    ),
    "pliometria" to listOf(
        "Pliometría: aprovecha el rebote elástico. Aterriza quieto; si se desordena, para.",
        "Esto es pliometría: rápido al suelo y fuera. Calidad de contacto, no altura a lo loco.",
        "Pliometría pide contactos limpios. Si el aterrizaje se desarma, baja la intensidad.",
    ),
    "isometria" to listOf(
        "Isometría: el músculo empuja sin acortarse. Sostén la posición, no el balanceo.",
        "Aquí el trabajo es isométrico: tensión quieta. La calidad es no perder el ángulo.",
        "Isometría: aguantar el punto. Si se rompe la postura, acorta el tiempo.",
    ),
    "fallo-muscular" to listOf(
        "Fallo: no sale otra rep concéntrica con buena forma. Si no estaba en el plan, no lo persigas.",
        "Fallo muscular: la barra ya no sube limpia. Úsalo poco; cansa más de lo que parece.",
        "Llegar al fallo termina la serie. En compuestos, mejor dejar una rep limpia.",
    ),
    "rir" to listOf(
        "RIR son las reps que te quedarían limpias. 2 RIR = podrías hacer dos más sin romper la técnica.",
        "RIR: cuántas repeticiones válidas te quedan. No cuentes una rep fea.",
        "Con RIR mides el margen al fallo. Si pones 2, para cuando aún podrías hacer dos más.",
    ),
    "rpe" to listOf(
        "RPE es lo duro que se siente, del 1 al 10. Un 8 deja unas 2 reps limpias.",
        "RPE 1–10: cómo de exigente fue la serie. 7–8 suele ser el trabajo útil.",
        "El RPE es tu esfuerzo percibido. Anótalo con honestidad, no con el ego.",
    ),
    "fase-excentrica" to listOf(
        "Excéntrica: la fase de bajada, el músculo se alarga bajo carga. Contrólala.",
        "Negativas: el trabajo está en frenar. Baja con intención, no te dejes caer.",
        "La excéntrica es el descenso. Si va de golpe, pierde el estímulo y carga la articulación.",
    ),
    "estres-metabolico" to listOf(
        "Estrés metabólico: la serie arde por metabolitos. Mantén el rango; no acortes por quemazón.",
        "Muchas reps o drops: más ardor, no más peso. Repite el recorrido completo.",
        "Ese ardor es estrés metabólico. Sirve si las reps siguen siendo válidas.",
    ),
    "poleas" to listOf(
        "Polea: el cable sigue tirando en esa línea. Elige un recorrido que puedas repetir.",
        "El cable no descansa en ningún punto. Mantén tensión útil de principio a fin.",
        "Polea: cambia el ángulo según la altura. Ancla esa configuración si quieres comparar.",
    ),
    "maquinas" to listOf(
        "Máquina: la trayectoria va guiada. Ajusta el asiento para que el recorrido te quede natural.",
        "En máquina el riel sostiene. Úsalo para aislar, no para desconectar la postura.",
        "Máquina: menos estabilización, más foco en el músculo. El ajuste del banco manda.",
    ),
    "pesos-libres" to listOf(
        "Pesos libres: la barra no te guía. Tú estabilizas la trayectoria.",
        "Barra o mancuerna: más libertad y más exigencia de control. No dejes que se desvíe.",
        "Con peso libre el equilibrio cuenta. Si la técnica baila, baja un poco.",
    ),
    "fuerza" to listOf(
        "Fuerza: pocas reps, más carga. Prioriza el control del peso sobre la prisa.",
        "Aquí el objetivo es fuerza: cada rep vale. Cierra bien la posición.",
        "Pocas repeticiones pesadas. Si la barra se tuerce, el número ya no mide fuerza.",
    ),
)

fun relatorConceptLines(id: String): List<String> = RELATOR_CONCEPT_LINES[id].orEmpty()

fun pickRelatorConceptCue(signals: RelatorConceptSignals): RelatorConceptCue? {
    val candidates = buildList {
        if ((signals.axialLoadFactor ?: 0.0) >= RELATOR_AXIAL_CUE_MIN) add("carga-axial")
        if (isPlyometricConcept(signals.exerciseName, signals.movementPatternId)) add("pliometria")
        if (isIsometricConcept(signals)) add("isometria")
        if (signals.plannedFailure || signals.intensityMode == IntensityMode.FAILURE) add("fallo-muscular")
        if (signals.intensityMode == IntensityMode.RIR) add("rir")
        if (shouldCueRpe(signals)) add("rpe")
        if (signals.hasNegatives) add("fase-excentrica")
        if (signals.plannedDropCount > 0 || (signals.plannedReps ?: 0.0) >= 15.0) add("estres-metabolico")
        when (relatorEquipmentFamily(signals.equipmentId)) {
            RelatorEquipmentFamily.CABLE -> add("poleas")
            RelatorEquipmentFamily.MACHINE -> add("maquinas")
            RelatorEquipmentFamily.FREE -> add("pesos-libres")
            RelatorEquipmentFamily.OTHER -> Unit
        }
        if (isFuerzaConcept(signals)) add("fuerza")
    }
    val id = candidates.firstOrNull { it !in signals.shownConceptIds } ?: return null
    val lines = RELATOR_CONCEPT_LINES[id].orEmpty()
    if (lines.size < 2) return null
    return RelatorConceptCue(id = id, lines = lines)
}

internal enum class RelatorEquipmentFamily {
    FREE,
    MACHINE,
    CABLE,
    OTHER,
}

internal fun relatorEquipmentFamily(equipmentId: String?): RelatorEquipmentFamily {
    val id = equipmentId?.trim()?.lowercase().orEmpty()
    if (id.isEmpty()) return RelatorEquipmentFamily.OTHER
    return when {
        id == "cable" || "pulley" in id || id == "polea" -> RelatorEquipmentFamily.CABLE
        id == "machine" || id == "smith" || "selector" in id || "maquina" in id || "máquina" in id ->
            RelatorEquipmentFamily.MACHINE
        id in setOf("barbell", "dumbbell", "kettlebell", "ezbar", "ez_bar", "trap_bar", "trapbar") ->
            RelatorEquipmentFamily.FREE
        else -> RelatorEquipmentFamily.OTHER
    }
}

internal fun isPlyometricConcept(exerciseName: String, movementPatternId: String?): Boolean {
    val hay = "${exerciseName.lowercase()} ${movementPatternId.orEmpty().lowercase()}"
    return listOf(
        "plyo", "pliometr", "jump", "salto", "bound", "rebound", "rebote",
        "box jump", "depth jump", "bounce",
    ).any { it in hay }
}

internal fun isIsometricConcept(signals: RelatorConceptSignals): Boolean {
    if (signals.hasIsoHold) return true
    val hay = "${signals.exerciseName.lowercase()} ${signals.movementPatternId.orEmpty().lowercase()}"
    return listOf("isometric", "isometr", "iso_hold", "iso-hold", "plank", "plancha", "wall sit")
        .any { it in hay }
}

private fun shouldCueRpe(signals: RelatorConceptSignals): Boolean {
    if (signals.intensityMode == IntensityMode.RIR ||
        signals.intensityMode == IntensityMode.FAILURE ||
        signals.plannedFailure
    ) {
        return false
    }
    return signals.intensityMode == IntensityMode.RPE
}

private fun isFuerzaConcept(signals: RelatorConceptSignals): Boolean {
    val reps = signals.plannedReps ?: return false
    if (reps > 5.0) return false
    return signals.isCompound || (signals.axialLoadFactor ?: 0.0) >= RELATOR_AXIAL_CUE_MIN
}
