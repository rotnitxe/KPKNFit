package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanLabels
import com.example.kpkn.data.protocols.KPKN_OWN_PLAN_DISCLAIMER
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.programs.PlanInfoModelBuilder

/**
 * Lo que la tarjeta «Plan y procedencia» del detalle del programa enseña, ya redactado en español llano.
 * Se calcula con [buildProgramPlanDisplaySummary], una función pura (sin Compose) que se prueba en JVM.
 *
 * @property catalogEntry entrada del catálogo a la que pertenece el programa; null si lo creó el usuario a mano
 *   o si viene de un método que ya no está en el catálogo. Con ella la tarjeta ofrece «Ver cómo funciona».
 * @property planName el título editorial del plan; sin entrada, el nombre del propio programa.
 * @property summary «Qué haces»: el resumen editorial de la entrada; null sin entrada.
 * @property planNotes notas del plan en lenguaje llano (las líneas que siguen a la descripción del programa).
 */
internal data class ProgramPlanDisplaySummary(
    val catalogEntry: CatalogEntry?,
    val planName: String,
    val provenanceLabel: String,
    val sourceLine: String?,
    val summary: String?,
    val planNotes: List<String>,
    val requiredMaterial: String,
    val changes: List<String>,
    val daysPerWeek: String,
    val maxSessionMinutes: Int?,
    val weeklyVolume: String,
    val pendingLoadCount: Int,
)

/** Las notas del plan que se enseñan antes de resumir el resto en «+N notas más». */
internal const val MAX_PLAN_NOTES = 6

/** Los cambios registrados que se enseñan antes de resumir el resto en «+N cambios más». */
internal const val MAX_CHANGES = 4

internal fun buildProgramPlanDisplaySummary(program: Program): ProgramPlanDisplaySummary {
    val entry = PersonalizedPlanCatalog.findForProgram(program)
    val provenance = program.planProvenance ?: program.sourceRecipe?.provenance
    val weeks = program.macrocycles.flatMap { macro ->
        macro.blocks.flatMap { block -> block.mesocycles.flatMap { it.weeks } }
    }
    val sessions = weeks.flatMap { it.sessions }
    val material = requiredMaterial(program, sessions.flatMap { it.allExercises().mapNotNull { exercise -> exercise.catalogConfigurationId } }.toSet())
    val dayCounts = weeks.mapNotNull { week ->
        week.sessions.mapNotNull { session -> session.dayOfWeek }.toSet().takeIf { it.isNotEmpty() }?.size
    }
    val weekVolumes = weeks.mapNotNull(::materializedWorkingSetCount)
    val durations = sessions.map(::estimatedSessionDurationMinutes).filter { it > 0 }
    val pendingLoads = program.exerciseLoadReferences.sumOf { loadRef ->
        loadRef.references.count { it.state == com.example.kpkn.data.protocols.PlanLoadReferenceState.PENDING }
    }
    return ProgramPlanDisplaySummary(
        catalogEntry = entry,
        planName = entry?.displayName ?: program.name,
        provenanceLabel = entry?.let(PlanLabels::provenanceLabel) ?: provenanceLabelWithoutEntry(program, provenance),
        sourceLine = if (entry != null) {
            entry.attributionLine?.takeIf { it.isNotBlank() } ?: provenance?.let(::sourceLine)
        } else {
            provenance?.let(::sourceLine) ?: authorLine(program)
        },
        summary = entry?.summary?.takeIf { it.isNotBlank() },
        planNotes = planNotesOf(program.description, entry),
        requiredMaterial = material,
        changes = provenance?.slotChanges.orEmpty().mapNotNull { change ->
            val from = change.fromConfigurationId?.let(::shortConfigurationName)
            val to = change.toConfigurationId?.let(::shortConfigurationName)
            val transition = when {
                from != null && to != null -> "$from → $to"
                to != null -> "Configuración: $to"
                else -> null
            }
            transition?.let { "$it · ${change.reason.ifBlank { "cambio registrado" }}" }
        },
        daysPerWeek = dayCounts.rangeLabel("días/semana"),
        maxSessionMinutes = durations.maxOrNull(),
        weeklyVolume = weekVolumes.rangeLabel("series materializadas/semana"),
        pendingLoadCount = pendingLoads,
    )
}

/**
 * Texto del chip ámbar del encabezado del detalle: el nombre corto del plan del catálogo (sin los días y sin
 * emoji) o, si el programa viene de un protocolo que el catálogo ya no ofrece, el nombre de ese protocolo.
 * Null cuando el programa no viene de ningún método.
 */
internal fun planChipLabel(program: Program): String? =
    PersonalizedPlanCatalog.findForProgram(program)?.shortName
        ?: program.sourceProtocolId?.let { id -> PROTOCOL_LIBRARY.firstOrNull { it.id == id }?.name }

/**
 * Las notas de un plan: lo que el programa guarda en su descripción después de la descripción misma. Los
 * personalizadores escriben `{resumen}` + una línea por nota; el resumen ya lo cuenta la sección «Qué haces», y
 * el banner del encabezado solo deja ver dos líneas, así que las notas no se leen en ningún otro sitio.
 *
 * - Se descartan el resumen de la entrada y la descripción heredada de ella (aunque ocupen varias líneas), las
 *   líneas vacías y las repetidas, y se conserva el orden.
 * - Si la descripción no contiene ninguno de esos textos (el programa se creó con una versión anterior de la
 *   entrada, o el usuario reescribió la descripción), su primera línea sigue siendo la descripción y tampoco
 *   es una nota. Sin entrada ocurre siempre así.
 */
@Suppress("DEPRECATION")
internal fun planNotesOf(description: String?, entry: CatalogEntry?): List<String> {
    val text = description?.replace("\r\n", "\n")?.takeIf { it.isNotBlank() } ?: return emptyList()
    val inherited = listOfNotNull(entry?.summary, entry?.description)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedByDescending { it.length }
    var rest = text
    var inheritedFound = false
    inherited.forEach { known ->
        if (rest.contains(known)) {
            rest = rest.replace(known, "\n")
            inheritedFound = true
        }
    }
    val lines = rest.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return (if (inheritedFound) lines else lines.drop(1)).distinct()
}

/**
 * Etiqueta de procedencia de un programa que no resuelve a ninguna entrada del catálogo. La clase declarada por
 * la procedencia manda; sin ella se dice de dónde viene lo poco que se sabe, sin mostrar nunca un identificador.
 */
private fun provenanceLabelWithoutEntry(program: Program, provenance: PlanProvenance?): String =
    when (provenance?.category) {
        PlanProvenanceClass.ORIGINAL -> "Original fiel"
        PlanProvenanceClass.ADAPTED -> "Adaptación KPKN"
        PlanProvenanceClass.KPKN -> "Plan KPKN"
        PlanProvenanceClass.IMPORTED -> "Programa importado"
        PlanProvenanceClass.LEGACY, null -> when {
            // Viene de un método (protocolo o plantilla) que el catálogo ya no ofrece.
            !program.sourceProtocolId.isNullOrBlank() || !program.structureTemplateId.isNullOrBlank() ->
                "Versión anterior de un método"
            !program.author.isNullOrBlank() -> "Programa guardado"
            else -> "Programa creado a mano"
        }
    }

/** «Autor · {nombre}» cuando el programa guarda un autor; los ids de método no se enseñan nunca. */
private fun authorLine(program: Program): String? =
    program.author?.trim()?.takeIf { it.isNotEmpty() }?.let { "Autor · $it" }

/** «+1 nota más», «+3 notas más»: concuerda con la cantidad que se resume. */
internal fun moreLabel(remaining: Int, singular: String, plural: String): String =
    "+${SpanishPlurals.withNoun(remaining, singular, plural)} más"

/**
 * Línea de máximos de entrenamiento del detalle: «TM: sentadilla 180 · banca 120 · peso muerto 220 kg».
 * Solo sale lo que existe: un máximo que falta no aparece (nunca un «—» suelto), y con un único valor la línea es, por ejemplo,
 * «TM: banca 120 kg». Los enteros van sin decimales y los demás con coma («110,5»). Sin ningún máximo, null: la fila no se pinta.
 */
internal fun trainingMaxLine(profile: PowerliftingProfile?): String? {
    val parts = listOfNotNull(
        trainingMaxValue(profile?.squatTM)?.let { "sentadilla $it" },
        trainingMaxValue(profile?.benchTM)?.let { "banca $it" },
        trainingMaxValue(profile?.deadliftTM)?.let { "peso muerto $it" },
    )
    if (parts.isEmpty()) return null
    return "TM: " + parts.joinToString(" · ") + " kg"
}

/** Un máximo que existe, con su formato; null si falta, no es un número finito o no es positivo. */
private fun trainingMaxValue(value: Double?): String? =
    value?.takeIf { it.isFinite() && it > 0.0 }?.let(PlanInfoModelBuilder::formatNumber)

/** Qué dice la fila de máximos de Propuestas: lo que hay fijado, la invitación a fijarlo si el programa usa TM, o nada. */
internal sealed interface TrainingMaxRow {
    /** Los máximos que existen, en una línea («TM: sentadilla 108 kg»). */
    data class Value(val line: String) : TrainingMaxRow

    /** El programa usa TM y ningún máximo está fijado: «Añadir» abre el editor. */
    object Unset : TrainingMaxRow

    /** El programa no usa TM: no hay fila. */
    object None : TrainingMaxRow
}

/**
 * La fila de máximos de Propuestas. [usesTm] es la regla de siempre (la receta, o el perfil si no hay receta). Si el programa usa TM y no
 * hay ningún máximo, la fila invita a fijarlos; si no usa TM, no hay fila aunque haya máximos guardados.
 */
internal fun trainingMaxRowOf(usesTm: Boolean, profile: PowerliftingProfile?): TrainingMaxRow {
    if (!usesTm) return TrainingMaxRow.None
    val line = trainingMaxLine(profile) ?: return TrainingMaxRow.Unset
    return TrainingMaxRow.Value(line)
}

/**
 * La línea de procedencia de «Sobre este programa». «Plan KPKN · Plan propio de KPKN.» repetía la etiqueta: con la descripción genérica
 * (el «Plan propio de KPKN.» de siempre) la línea pasa a ser el resumen de la ficha del catálogo, si existe; si no, no se pinta (null).
 */
internal fun aboutProvenanceLine(summary: ProgramPlanDisplaySummary): String? {
    val source = summary.sourceLine?.trim()?.takeIf { it.isNotEmpty() }
    if (source != null && source.trimEnd('.') == KPKN_OWN_PLAN_DISCLAIMER.trimEnd('.')) return summary.summary
    return listOfNotNull(summary.provenanceLabel.takeIf { it.isNotBlank() }, source).joinToString(" · ").ifEmpty { null }
}

private fun sourceLine(provenance: PlanProvenance): String? = listOfNotNull(
    provenance.sourceTitle,
    provenance.sourceAuthor,
    provenance.sourceEdition,
).filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { null }

private fun requiredMaterial(program: Program, configurationIds: Set<String>): String {
    if (configurationIds.isEmpty()) return "Sin configuraciones catalogadas"
    val catalog = approvedExerciseCatalogV2() ?: return "No disponible: catálogo de configuraciones pendiente"
    val configurations = catalog.families.flatMap { it.definitions }.flatMap { it.configurations }
        .filter { it.id in configurationIds }
    if (configurations.isEmpty()) return "No disponible para las configuraciones materializadas"
    val terms = configurations.flatMap { configuration ->
        configuration.profile.richMetadata?.programming?.requiredEquipment.orEmpty() + configuration.profile.equipmentId
    }.map(::materialLabel).filter { it.isNotBlank() }.distinct().sorted()
    val adaptations = program.planProvenance?.slotChanges.orEmpty()
    val opaqueChangeCount = adaptations.count { it.toConfigurationId != null && it.toConfigurationId !in configurationIds }
    return buildList {
        addAll(terms)
        if (opaqueChangeCount > 0) add("Revisar cambios de configuración")
    }.distinct().joinToString(", ").ifBlank { "Sin material específico declarado" }
}

private fun materialLabel(raw: String): String {
    val token = raw.trim().lowercase().substringAfterLast(':').replace('-', '_').replace(' ', '_')
    return when (token) {
        "barbell", "bar", "barbell_and_plates" -> "barra y discos"
        "dumbbells", "dumbbell" -> "mancuernas"
        "bench", "flat_bench", "incline_bench" -> "banco"
        "rack", "squat_rack", "power_rack" -> "rack"
        "pull_up_bar", "chin_up_bar" -> "barra de dominadas"
        "cable", "pulley" -> "polea"
        "band", "bands", "resistance_band" -> "bandas"
        "bodyweight", "none" -> "peso corporal"
        "machine" -> "máquina específica"
        "smith_machine", "smith" -> "máquina Smith"
        "kettlebell" -> "kettlebell"
        "cardio_machine", "treadmill", "bike" -> "aparato de cardio"
        else -> raw.trim().replace('_', ' ').takeIf { it.isNotEmpty() } ?: ""
    }
}

private fun shortConfigurationName(id: String): String =
    com.example.kpkn.data.exercises.catalogConfigurationDisplayName(id) ?: id.replace('_', ' ')

private fun materializedWorkingSetCount(week: ProgramWeek): Int? = week.sessions
    .takeIf { it.isNotEmpty() }
    ?.sumOf { session ->
        session.allExercises().sumOf { exercise ->
            exercise.sets.count { !it.isEmptySlot && !it.isCalibrator && !it.isIneffective }
        }
    }

private fun estimatedSessionDurationMinutes(session: com.example.kpkn.data.models.Session): Int =
    com.example.kpkn.domain.training.SessionDurationEstimator.estimate(session).totalMinutes

private fun List<Int>.rangeLabel(suffix: String): String = when {
    isEmpty() -> "Sin datos"
    minOrNull() == maxOrNull() -> "${maxOrNull()} $suffix"
    else -> "${minOrNull()}–${maxOrNull()} $suffix"
}
