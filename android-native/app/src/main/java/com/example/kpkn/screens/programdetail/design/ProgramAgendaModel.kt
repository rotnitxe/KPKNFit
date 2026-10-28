package com.example.kpkn.screens.programdetail.design

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/** Cuántos puntos de foco caben como mucho en la tarjeta de una sesión (P2 agenda). */
private const val MAX_FOCUS_DOTS = 3

/** El foco de una sesión (P2 agenda): el grupo que trabaja, con el color de su punto en la tarjeta (`MUS` del lienzo v6). */
internal enum class SessionFocus(val color: Color) {
    LEG(SymbolPalette.musculo),
    PUSH(SymbolPalette.energia),
    PULL(SymbolPalette.columna),
    CORE(Color(0xFFC9A8FF)),
    CARDIO(SymbolPalette.ok),
}

/**
 * El foco de un grupo muscular canónico, el que da `VolumeCalculator.normalizeCanonicalMuscleGroup`; null si no cuenta para el foco.
 * Los deltoides posteriores tiran; los demás deltoides empujan.
 */
internal fun focusOfCanonical(canonical: String, emphasis: String?): SessionFocus? = when (canonical) {
    "Pectorales", "Tríceps" -> SessionFocus.PUSH
    "Deltoides" -> if (emphasis.orEmpty().lowercase().contains("posterior")) SessionFocus.PULL else SessionFocus.PUSH
    "Dorsales", "Trapecio", "Romboides", "Bíceps", "Antebrazo" -> SessionFocus.PULL
    "Glúteos", "Aductores", "Cuádriceps", "Isquiosurales", "Pantorrillas", "Tibial Anterior", "Erectores Espinales" -> SessionFocus.LEG
    "Core", "Abdomen" -> SessionFocus.CORE
    else -> null
}

/**
 * Los focos de una sesión (P2 agenda), como mucho tres puntos. Cada ejercicio aporta uno: el cardio es [SessionFocus.CARDIO] y el resto
 * sale del músculo principal (o del primero) con [infoOf], la ficha del catálogo; sin ficha, no aporta. Los puntos se reparten por
 * frecuencia (mayor resto; a igualdad, el grupo que aparece antes) y salen del más frecuente al menos frecuente.
 */
internal fun sessionFocus(
    exercises: List<Exercise>,
    infoOf: (Exercise) -> ExerciseMuscleInfo?,
): List<SessionFocus> {
    val foci = exercises.mapNotNull { exercise -> focusOfExercise(exercise, infoOf) }
    if (foci.isEmpty()) return emptyList()
    val seats = minOf(MAX_FOCUS_DOTS, foci.size)
    val total = foci.size
    // Los grupos en orden de aparición, con cuántos ejercicios aporta cada uno.
    val groups = foci.distinct()
    val counts = groups.associateWith { focus -> foci.count { it == focus } }
    // Reparto proporcional: la parte entera primero y después los puestos que sobran al mayor resto.
    val points = groups.associateWith { focus -> counts.getValue(focus) * seats / total }.toMutableMap()
    var left = seats - points.values.sum()
    val byRemainder = groups.sortedWith(
        compareByDescending<SessionFocus> { focus -> counts.getValue(focus) * seats % total }
            .thenBy { focus -> groups.indexOf(focus) },
    )
    for (focus in byRemainder) {
        if (left == 0) break
        points[focus] = points.getValue(focus) + 1
        left--
    }
    return groups
        .sortedWith(compareByDescending<SessionFocus> { focus -> counts.getValue(focus) }.thenBy { focus -> groups.indexOf(focus) })
        .flatMap { focus -> List(points.getValue(focus)) { focus } }
}

/** El foco de un ejercicio: el cardio, o el grupo canónico de su músculo principal. Null si no hay ficha o no cuenta para el foco. */
private fun focusOfExercise(exercise: Exercise, infoOf: (Exercise) -> ExerciseMuscleInfo?): SessionFocus? {
    if (exercise.isCardio) return SessionFocus.CARDIO
    val info = infoOf(exercise) ?: return null
    val muscle = info.involvedMuscles.firstOrNull { it.role == MuscleRole.PRIMARY }
        ?: info.involvedMuscles.firstOrNull()
        ?: return null
    val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
    return focusOfCanonical(canonical, muscle.emphasis)
}

/** Una fila de la agenda: un día, o un tramo de descansos seguidos que se pliega en una sola fila. */
internal sealed interface AgendaRow {
    /** Un día con su columna de fecha y sus sesiones. */
    data class Day(val day: ProgramDayUi) : AgendaRow

    /** Dos o más días de descanso seguidos, plegados en «Descanso · N días». */
    data class RestRun(val days: List<ProgramDayUi>) : AgendaRow
}

/**
 * Las filas de la agenda (lienzo v6, `agendaHtml`): los días en orden, salvo los descansos seguidos (dos o más sin sesiones y sin ser
 * destino de arrastre), que se pliegan en un [AgendaRow.RestRun] y no se despliegan. No se pliegan mientras se arrastra una sesión
 * ([dragging]). Los días fuera del programa (antes del inicio o después del fin) no salen en la agenda (W-D3), salvo que tengan alguna
 * sesión: así no se pierde, y al empezarla avisa el diálogo de fuera de calendario.
 */
internal fun agendaRows(days: List<ProgramDayUi>, dragging: Boolean): List<AgendaRow> {
    val inProgram = days.filterNot { it.isOutsideProgram && it.sessions.isEmpty() }
    val rows = mutableListOf<AgendaRow>()
    var index = 0
    while (index < inProgram.size) {
        if (!isRestSlot(inProgram[index])) {
            rows += AgendaRow.Day(inProgram[index])
            index++
            continue
        }
        var end = index
        while (end < inProgram.size && isRestSlot(inProgram[end])) end++
        val run = inProgram.subList(index, end)
        if (run.size >= 2 && !dragging) {
            rows += AgendaRow.RestRun(run.toList())
        } else {
            run.forEach { rows += AgendaRow.Day(it) }
        }
        index = end
    }
    return rows
}

/** Un día de descanso que puede plegarse: sin sesiones y sin ser destino de arrastre. */
private fun isRestSlot(day: ProgramDayUi): Boolean = day.sessions.isEmpty() && !day.isDropTarget

/**
 * El total de una jornada en la agenda: «≈ 45 min», «≈ 1 h 20 min» o «≈ 2 h» cuando no sobran minutos. Los minutos vienen del estimador.
 */
internal fun dayTotalLabel(minutes: Int): String {
    if (minutes < 60) return "≈ $minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "≈ $hours h" else "≈ $hours h $rest min"
}

/**
 * La cápsula de propuesta de una sesión (s-week, «+2,5 kg»): la primera propuesta de progresión pendiente con carga objetivo cuyo ejercicio
 * está en la sesión, como diferencia con la carga más alta que la sesión prescribe ahora. Null si no hay ninguna o no hay con qué comparar.
 */
internal fun sessionProposalLabel(
    exercises: List<Exercise>,
    proposals: List<com.example.kpkn.data.models.NativeProgressionProposal>,
): String? {
    for (proposal in proposals) {
        val target = proposal.targetLoadKg ?: continue
        val exercise = exercises.firstOrNull { it.catalogConfigurationId == proposal.identity.configurationId } ?: continue
        val current = exercise.sets.mapNotNull { it.weight }.maxOrNull() ?: continue
        val delta = target - current
        if (kotlin.math.abs(delta) < 0.05) continue
        val magnitude = String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", kotlin.math.abs(delta)).removeSuffix(",0")
        return "${if (delta > 0) "+" else "−"}$magnitude kg"
    }
    return null
}
