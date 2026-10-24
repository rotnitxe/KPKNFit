package com.example.kpkn.screens.sessioneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.Session
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.catalogSearchRedirects
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.training.VolumeCalculator
import kotlin.math.abs

internal fun suggestWarmupReps(percentage: Double): Int = when {
    percentage >= 90.0 -> 1
    percentage >= 85.0 -> 2
    percentage >= 80.0 -> 3
    percentage >= 75.0 -> 4
    percentage >= 70.0 -> 5
    percentage >= 65.0 -> 6
    percentage >= 60.0 -> 8
    percentage >= 50.0 -> 10
    else -> 12
}


// ===== COMPACT COMPONENTS FOR OPTIMIZED EXERCISE EDITOR =====




internal fun resolveSpecificSubMuscle(muscle: String, emphasis: String?): String {
    return MuscleHeadResolution.resolveDisplayHead(muscle, emphasis) ?: muscle
}

internal fun com.example.kpkn.data.models.ExerciseSet.editorEffectiveTargetRpe(): Double {
    if (isFailure || intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE) return 10.0
    targetRPE?.let { return it.coerceIn(1.0, 10.0) }
    targetRIR?.let { return (10 - it).toDouble().coerceIn(1.0, 10.0) }
    return 8.0
}

/** Intensity tiers for session volume display (assistant muscle card). */
enum class SessionIntensityTier(val label: String, val ordinalRank: Int) {
    MEDIA("Media", 0),
    ALTA("Alta", 1),
    MUY_ALTA("Muy Alta", 2),
    MAXIMA("Máxima", 3),
}

/**
 * Classify a planned set's intensity for the assistant volume card.
 * - Máxima: failure + dropset/rest-pause
 * - Muy Alta: failure
 * - Alta: RPE 8–9 / RIR 0–2
 * - Media: RPE 6–7 / RIR 3–4 (and default)
 */
internal fun com.example.kpkn.data.models.ExerciseSet.sessionIntensityTier(): SessionIntensityTier {
    val isFail = isFailure || intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE
    val hasTechnique = isDropSet || isRestPause || dropSets.isNotEmpty() || restPauses.isNotEmpty()
    if (isFail && hasTechnique) return SessionIntensityTier.MAXIMA
    if (isFail) return SessionIntensityTier.MUY_ALTA
    val rpe = editorEffectiveTargetRpe()
    return if (rpe >= 8.0) SessionIntensityTier.ALTA else SessionIntensityTier.MEDIA
}

/** Average intensity tier across sets (ordinal mean → nearest tier). */
internal fun averageSessionIntensityTier(
    sets: List<com.example.kpkn.data.models.ExerciseSet>,
): SessionIntensityTier {
    val active = sets.filterNot { it.isIneffective || it.isEmptySlot }
    if (active.isEmpty()) return SessionIntensityTier.MEDIA
    val avg = active.map { it.sessionIntensityTier().ordinalRank }.average()
    return SessionIntensityTier.entries.minByOrNull { kotlin.math.abs(it.ordinalRank - avg) }
        ?: SessionIntensityTier.MEDIA
}

/**
 * Per-muscle role-separated set counts for the assistant volume card.
 * Direct = PRIMARY contribution × sets. SecondaryIndirect = SECONDARY contribution × sets.
 * StabilizerIndirect = STABILIZER contribution × sets.
 * Intensity is averaged only over exercises that contribute PRIMARY volume to that muscle.
 */
internal data class MuscleVolumeRow(
    val muscle: String,
    val directSets: Double,
    val secondarySets: Double,
    val stabilizerSets: Double,
    val intensity: SessionIntensityTier,
)

internal fun buildMuscleVolumeRows(session: Session): List<MuscleVolumeRow> {
    val volumes = VolumeCalculator.calculateRoleSeparatedMuscleVolume(
        sessions = listOf(session),
        exerciseList = catalogIndexForVolume.values.toList(),
        aliases = catalogSearchRedirects(),
    )
    val exerciseIndex = catalogIndexForVolume
    val intensitySets = mutableMapOf<String, MutableList<com.example.kpkn.data.models.ExerciseSet>>()

    session.allExercises().forEach { exercise ->
        if (VolumeCalculator.countEffectiveSets(exercise.sets) <= 0) return@forEach
        val muscles = ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)
        muscles.filter { it.role == MuscleRole.PRIMARY }
            .map { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
            .distinct()
            .forEach { muscle ->
                intensitySets.getOrPut(muscle) { mutableListOf() }
                    .addAll(exercise.sets.filterNot { it.isIneffective || it.isEmptySlot })
            }
    }

    return volumes.map { (muscle, volume) ->
        MuscleVolumeRow(
            muscle = muscle,
            directSets = volume.directSets,
            secondarySets = volume.secondarySets,
            stabilizerSets = volume.stabilizerSets,
            intensity = averageSessionIntensityTier(intensitySets[muscle].orEmpty()),
        )
    }.filter { it.directSets > 0.0 || it.secondarySets > 0.0 || it.stabilizerSets > 0.0 }
        .sortedByDescending { it.directSets }
}

private val catalogIndexForVolume: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>
    get() {
        // Full index (definition + configuration + custom ids) so v2-picked exercises
        // resolve their involved muscles and actually contribute volume.
        return catalogExerciseIndex()
    }

internal fun buildDisplayContributions(
    involvedMuscles: List<com.example.kpkn.data.models.InvolvedMuscle>,
    countIndirect: Boolean
): Map<String, Double> {
    val grouped = linkedMapOf<String, Double>()
    involvedMuscles.forEach { involvement ->
        val isMatch = if (countIndirect) {
            involvement.role == com.example.kpkn.data.models.MuscleRole.SECONDARY || involvement.role == com.example.kpkn.data.models.MuscleRole.STABILIZER
        } else {
            involvement.role == com.example.kpkn.data.models.MuscleRole.PRIMARY
        }
        if (isMatch) {
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            val contribution = com.example.kpkn.data.models.resolveMuscleVolumeContribution(involvement)
            val current = grouped[canonical] ?: 0.0
            if (contribution > current) {
                grouped[canonical] = contribution
            }
        }
    }
    return grouped.filterValues { it > 0.0 }
}

internal fun countDisplaySets(exerciseSets: List<com.example.kpkn.data.models.ExerciseSet>, adjustByIntensity: Boolean): Double {
    var total = 0.0
    val activeSets = exerciseSets.filterNot { it.isIneffective || it.isEmptySlot }
    val counted = activeSets.filter { set ->
        ((set.completedReps ?: set.plannedRepAnchor() ?: 0) > 0 || (set.weight ?: 0.0) > 0.0)
    }
    val targetList = if (counted.isEmpty()) activeSets else counted
    total = targetList.size.toDouble()
    return total
}

internal fun calculateSubMuscleBreakdown(
    canonicalMuscle: String,
    session: Session,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    countIndirect: Boolean,
    adjustByIntensity: Boolean
): List<Pair<String, List<Pair<String, Double>>>> {
    val targetSubMuscles = when (canonicalMuscle) {
        "Deltoides" -> listOf("Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior")
        "Glúteos" -> listOf("Glúteo Mayor", "Glúteo Medio", "Glúteo Menor")
        "Pectorales" -> listOf("Pectoral Superior", "Pectoral Medio", "Pectoral Inferior")
        else -> return emptyList()
    }
    
    val subMuscleVolumes = targetSubMuscles.associateWith { mutableMapOf<String, Double>() }.toMutableMap()
    
    session.allExercises().forEach { exercise ->
        val effectiveSets = countDisplaySets(exercise.sets, adjustByIntensity)
        if (effectiveSets <= 0.0) return@forEach
        val musclesToCount = ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)

        musclesToCount.forEach { involvement ->
            // El breakdown de porciones desglosa TODO el trabajo de ese músculo
            // (directo + secundario + estabilizador), no filtra por rol.
            if (involvement.role != com.example.kpkn.data.models.MuscleRole.NEUTRALIZER) {
                val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
                if (canonical == canonicalMuscle) {
                    val subMuscle = resolveSpecificSubMuscle(involvement.muscle, involvement.emphasis)
                    val map = subMuscleVolumes[subMuscle]
                    if (map != null) {
                        val contribution = com.example.kpkn.data.models.resolveMuscleVolumeContribution(involvement)
                        val current = map[exercise.name] ?: 0.0
                        if (effectiveSets * contribution > current) {
                            map[exercise.name] = effectiveSets * contribution
                        }
                    }
                }
            }
        }
    }
    
    return targetSubMuscles.map { subName ->
        val exerciseMap = subMuscleVolumes[subName] ?: emptyMap()
        subName to exerciseMap.entries
            .map { it.key to it.value }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
    }
}

internal fun getMuscleEmphasisEducationalText(muscle: String, headName: String?): String {
    val normalizedMuscle = muscle.trim().lowercase()
    val normalizedHead = headName?.trim()?.lowercase() ?: ""

    return when (normalizedMuscle) {
        "pectorales" -> {
            when {
                normalizedHead.contains("clavicular") || normalizedHead.contains("superior") ->
                    "La porción superior (clavicular) se enfatiza mediante la flexión del hombro (ej. press inclinado o cruces en polea baja), donde la trayectoria ascendente alinea la línea de tracción con la dirección de sus fibras musculares."
                normalizedHead.contains("esternal") || normalizedHead.contains("inferior") ->
                    "La porción inferior (esternal/costal) se enfatiza mediante la aducción horizontal declinada (ej. fondos en paralelas o cruces en polea alta), alineando el plano de empuje con las fibras inferiores."
                normalizedHead.contains("plano") || normalizedHead.contains("medio") ->
                    "La porción media se enfatiza con la aducción horizontal pura perpendicular al torso (ej. press de banca plano o aperturas planas)."
                else ->
                    "El pectoral se divide en porciones clavicular, esternal y costal. Modificar la inclinación del press o la trayectoria de los cruces de polea cambia la alineación de las fibras activadas por los brazos de momento de la carga."
            }
        }
        "deltoides" -> {
            when {
                normalizedHead.contains("anterior") ->
                    "El deltoides anterior se enfatiza mediante la flexión del hombro (ej. press militar, press de hombros con mancuernas o elevaciones frontales)."
                normalizedHead.contains("lateral") || normalizedHead.contains("medio") ->
                    "El deltoides lateral se enfatiza mediante la abducción pura del hombro (ej. elevaciones laterales), idealmente realizadas en el plano escapular (30° al frente) para mayor seguridad articular."
                normalizedHead.contains("posterior") ->
                    "El deltoides posterior se enfatiza mediante la abducción horizontal y extensión del hombro (ej. pájaros con mancuernas o cruces invertidos en polea)."
                else ->
                    "El deltoides consta de cabezas anterior, lateral y posterior. Se enfatizan modificando la dirección del plano del movimiento del hombro (flexión, abducción o abducción horizontal)."
            }
        }
        "trapecio" -> {
            when {
                normalizedHead.contains("descendente") || normalizedHead.contains("superior") ->
                    "El trapecio superior se enfatiza mediante la elevación escapular (ej. encogimientos de hombros), donde las fibras tiran hacia arriba, y también contribuye en la abducción del brazo por encima de la cabeza."
                normalizedHead.contains("transversa") || normalizedHead.contains("media") ->
                    "El trapecio medio se enfatiza mediante la retracción escapular (ej. jalones a la cara/face pulls, o remos abiertos juntando las escápulas)."
                normalizedHead.contains("ascendente") || normalizedHead.contains("inferior") ->
                    "El trapecio inferior se enfatiza mediante la depresión escapular (ej. jalones escapulares o elevaciones en Y), donde las fibras tiran de la escápula hacia abajo."
                else ->
                    "El trapecio se divide en superior, medio e inferior. Sus fibras cambian de orientación funcional, requiriendo elevación, retracción o depresión de las escápulas para enfatizar cada zona."
            }
        }
        "cuádriceps" -> {
            when {
                normalizedHead.contains("recto femoral") ->
                    "El recto femoral es biarticular (cruza cadera y rodilla). Se enfatiza cuando la cadera está extendida y la rodilla se flexiona (ej. sentadilla sissy o extensiones con el torso inclinado hacia atrás), aumentando su tensión de estiramiento."
                else ->
                    "Los vastos (lateral, medial, intermedio) son monoarticulares y se activan en conjunto en flexo-extensión de rodilla (ej. prensa o sentadillas). El recto femoral requiere cambios en la extensión de la cadera para modificar su participación relativa."
            }
        }
        "glúteos" -> {
            when {
                normalizedHead.contains("mayor") ->
                    "El glúteo mayor es el extensor primario de cadera. Se enfatiza con cargas donde la máxima tensión coincide con la cadera extendida (ej. hip thrust) o estirada (ej. peso muerto rumano o sentadilla profunda)."
                normalizedHead.contains("medio") || normalizedHead.contains("menor") ->
                    "Los glúteos medio y menor actúan como abductores y rotadores. Se enfatizan mediante la abducción pura de la cadera (ej. abducciones en polea, máquina de abductores o caminatas laterales con banda)."
                else ->
                    "El complejo glúteo incluye el mayor (extensor principal) y el medio/menor (estabilizadores y abductores). Alternar ejercicios de extensión pura con movimientos de abducción cambia el énfasis entre estas porciones."
            }
        }
        "pantorrillas" -> {
            when {
                normalizedHead.contains("gastrocnemio") ->
                    "El gastrocnemio es biarticular. Se enfatiza con la rodilla extendida (ej. elevaciones de talones de pie o en prensa), donde puede estirarse y contraerse en condiciones óptimas."
                normalizedHead.contains("sóleo") ->
                    "El sóleo es monoarticular. Se enfatiza con la rodilla flexionada a 90° (ej. elevaciones de talones sentado), posición que acorta e inactiva en gran parte al gastrocnemio."
                else ->
                    "La pantorrilla se compone de gastrocnemios y sóleo. Flexionar la rodilla altera drásticamente la contribución de los gastrocnemios debido a la insuficiencia activa, dejando la mayor parte del trabajo al sóleo."
            }
        }
        "bíceps" -> {
            when {
                normalizedHead.contains("larga") ->
                    "La cabeza larga (biarticular) se enfatiza al colocar el hombro en extensión (ej. curl en banco inclinado), lo que la sitúa en una posición de mayor preestiramiento."
                normalizedHead.contains("corta") ->
                    "La cabeza corta se enfatiza cuando el hombro está flexionado (ej. curl predicador o curl araña), lo que reduce el preestiramiento de la cabeza larga e incrementa el estímulo relativo en la porción interna."
                normalizedHead.contains("braquial") ->
                    "El braquial y braquiorradial se enfatizan usando agarres neutros o pronos (ej. curl martillo o curl invertido), donde disminuye la ventaja mecánica de las cabezas del bíceps."
                else ->
                    "El flexor del codo incluye la cabeza larga, corta y el músculo braquial. Modificar la posición del hombro respecto al torso o cambiar la orientación del agarre altera la ventaja mecánica de cada porción."
            }
        }
        "tríceps" -> {
            when {
                normalizedHead.contains("larga") ->
                    "La cabeza larga es la única biarticular del tríceps. Se enfatiza mediante la flexión del hombro (brazo elevado sobre la cabeza, ej. copas de tríceps o extensiones tras nuca), colocándola en una posición de estiramiento máximo."
                normalizedHead.contains("lateral") ->
                    "La cabeza lateral se enfatiza con el brazo al costado del cuerpo y agarre prono o neutro (ej. extensiones en polea alta con cuerda o barra V)."
                normalizedHead.contains("medial") ->
                    "La cabeza medial es el caballo de batalla del tríceps, activa en todos los movimientos de extensión, siendo especialmente demandada al final del rango en el bloqueo del codo."
                else ->
                    "El tríceps tiene cabezas lateral, medial y larga. Dado que solo la cabeza larga cruza la articulación del hombro, elevar el brazo por encima de la cabeza es indispensable para estirarla y enfatizarla."
            }
        }
        "antebrazo" -> {
            when {
                normalizedHead.contains("flexores") ->
                    "Los flexores se enfatizan mediante movimientos de flexión de muñeca (palma hacia el antebrazo) bajo resistencia."
                normalizedHead.contains("extensores") ->
                    "Los extensores se enfatizan mediante la extensión de muñeca (dorso de la mano hacia el antebrazo)."
                else ->
                    "El antebrazo se divide principalmente en extensores y flexores. Cambiar la orientación del agarre de la barra (prono o supino) redirige el estímulo y la tensión mecánica a cada grupo."
            }
        }
        else -> ""
    }
}

@Composable
internal fun SessionSubMuscleBreakdownList(
    muscleName: String,
    session: Session,
    countIndirect: Boolean,
    adjustByIntensity: Boolean,
) {
    val exerciseIndex = catalogExerciseIndex()
    val breakdown = remember(muscleName, session, countIndirect, adjustByIntensity, exerciseIndex) {
        calculateSubMuscleBreakdown(muscleName, session, exerciseIndex, countIndirect, adjustByIntensity)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        breakdown.forEach { (subName, exercises) ->
            val totalSubSets = exercises.sumOf { it.second }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = subName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${if (totalSubSets == totalSubSets.toLong().toDouble()) totalSubSets.toLong().toString() else "%.1f".format(totalSubSets)} series",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (exercises.isEmpty()) {
                    Text(
                        text = "  Sin aportes registrados para esta cabeza.",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                } else {
                    exercises.forEach { (exName, valSets) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• $exName",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${if (valSets == valSets.toLong().toDouble()) valSets.toLong().toString() else "%.1f".format(valSets)} series",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
