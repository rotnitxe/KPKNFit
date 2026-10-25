package com.example.kpkn.screens.workout

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kpkn.data.models.JointRegion
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.unresolvedDiscomfortIds

/**
 * Lo que la persona va eligiendo en el feedback tras el ejercicio (LV6, B1 y B3): la técnica de cada ejercicio, las molestias y qué
 * molestias de antes siguen o se resolvieron. Vive en UN solo sitio (la pantalla completa y la tarjeta del descanso leen y escriben
 * este mismo borrador), así que al minimizar o expandir se ve lo ya elegido. Es estado de interfaz: no se guarda en disco.
 */
@Stable
internal class PostExerciseFeedbackDraft(
    /** Ejercicios a los que se refiere el feedback (uno, o los de una superserie). */
    val exerciseIds: List<String>,
    initialTechnical: Map<String, Int> = emptyMap(),
    initialDiscomfortIds: Collection<String> = emptyList(),
    initialQuery: String = "",
) {
    private val technicalValues = mutableStateMapOf<String, Int>()
    private val stillPresentById = mutableStateMapOf<String, Boolean>()

    /** Molestias elegidas para estos ejercicios, en el orden en que se eligieron. Nunca contiene `"none"`. */
    val discomfortIds = mutableStateListOf<String>()

    /** Lo escrito en el buscador de molestias. */
    var query by mutableStateOf(initialQuery)

    init {
        exerciseIds.forEach { id ->
            technicalValues[id] = TechniqueQuality.clamp(initialTechnical[id] ?: TechniqueQuality.DEFAULT)
        }
        initialDiscomfortIds
            .filter { it.isNotBlank() && it != NONE }
            .forEach { id -> if (id !in discomfortIds) discomfortIds.add(id) }
    }

    /** Calidad técnica (1–10) del ejercicio. */
    fun technical(exerciseId: String): Int = technicalValues[exerciseId] ?: TechniqueQuality.DEFAULT

    fun setTechnical(exerciseId: String, value: Int) {
        technicalValues[exerciseId] = TechniqueQuality.clamp(value)
    }

    /** La técnica de todos los ejercicios, para el motor de molestias. */
    fun technicalSnapshot(): Map<String, Int> = exerciseIds.associateWith { technical(it) }

    fun isSelected(discomfortId: String): Boolean = discomfortId in discomfortIds

    fun select(discomfortId: String) {
        if (discomfortId.isBlank() || discomfortId == NONE || discomfortId in discomfortIds) return
        discomfortIds.add(discomfortId)
    }

    fun deselect(discomfortId: String) {
        discomfortIds.remove(discomfortId)
    }

    fun toggle(discomfortId: String) {
        if (isSelected(discomfortId)) deselect(discomfortId) else select(discomfortId)
    }

    /** Una molestia de otro ejercicio sigue presente mientras no se marque «Resuelta». */
    fun stillPresent(discomfortId: String): Boolean = stillPresentById[discomfortId] ?: true

    fun setStillPresent(discomfortId: String, value: Boolean) {
        stillPresentById[discomfortId] = value
    }

    companion object {
        const val NONE = "none"

        /** El borrador de arranque de [exercises]: lo ya guardado de cada ejercicio o los valores por defecto (técnica 8, sin molestias). */
        fun from(exercises: List<Exercise>, saved: Map<String, PostExerciseFeedback>): PostExerciseFeedbackDraft =
            PostExerciseFeedbackDraft(
                exerciseIds = exercises.map { it.id },
                initialTechnical = exercises.mapNotNull { ex -> saved[ex.id]?.let { ex.id to it.technicalQuality } }.toMap(),
                initialDiscomfortIds = exercises.flatMap { saved[it.id]?.discomfortIds.orEmpty() }.distinct(),
            )
    }
}

/** Guarda el borrador vivo del feedback: el mismo para la pantalla completa y la tarjeta mientras se pide el feedback del mismo ejercicio. */
internal class PostExerciseFeedbackDraftHolder {
    private var current: PostExerciseFeedbackDraft? = null

    /** El borrador de estos ejercicios: el vivo si es de los mismos, o uno nuevo (que pasa a ser el vivo). */
    fun draftFor(exercises: List<Exercise>, saved: Map<String, PostExerciseFeedback>): PostExerciseFeedbackDraft {
        val ids = exercises.map { it.id }
        current?.takeIf { it.exerciseIds == ids }?.let { return it }
        return PostExerciseFeedbackDraft.from(exercises, saved).also { current = it }
    }

    /** Al guardar o descartar el feedback el borrador ya no vale. */
    fun clear() {
        current = null
    }
}

/** Una molestia que se reportó en OTRO ejercicio, comparte articulación con el actual y aún no se resolvió (se pregunta «Sigue» o «Resuelta»). */
internal data class LinkedDiscomfort(val id: String, val label: String, val reportedIn: String)

/**
 * Las molestias previas relacionadas con [feedbackExercises]: las no resueltas de los demás ejercicios de la sesión cuya articulación
 * coincide con alguna de las que cargan estos. [articulationsOf] da las articulaciones de un ejercicio.
 */
internal fun linkedDiscomfortsFor(
    feedbackExercises: List<Exercise>,
    feedbackByExerciseId: Map<String, PostExerciseFeedback>,
    articulationsOf: (Exercise) -> List<JointRegion>,
): List<LinkedDiscomfort> {
    val current = feedbackExercises.flatMap(articulationsOf).distinct()
    val currentIds = feedbackExercises.map { it.id }.toSet()
    return feedbackByExerciseId
        .filter { (exerciseId, _) -> exerciseId !in currentIds }
        .flatMap { (_, previous) ->
            previous.unresolvedDiscomfortIds().mapNotNull { id ->
                val entry = DISCOMFORT_CATALOG_BY_ID[id] ?: return@mapNotNull null
                val shared = entry.relatedArticular.firstOrNull { it in current }
                if (shared != null) LinkedDiscomfort(id, entry.label, previous.exerciseName) else null
            }
        }
        .distinctBy { it.id }
}

/**
 * Lo que se guarda con «Guardar» (el «Registrar feedback» de siempre): UNA función para la pantalla completa y la tarjeta, así guardan lo
 * mismo. Por cada ejercicio, su técnica y las molestias elegidas (`"none"` si no hay); la pregunta «Qué tan intenso fue» ya no existe, de
 * modo que la intensidad percibida y el fallo se conservan tal como estaban (los escribe la voz o un registro anterior) y, sin ellos,
 * quedan en null. Además actualiza las molestias previas de otros ejercicios que se marcaron «Resuelta».
 */
internal fun buildPostExerciseFeedbackPayloads(
    feedbackExercises: List<Exercise>,
    draft: PostExerciseFeedbackDraft,
    previousFeedback: Map<String, PostExerciseFeedback>,
    linked: List<LinkedDiscomfort>,
    canonicalKeyOf: (Exercise) -> String,
    nameOf: (Exercise) -> String,
): List<PostExerciseFeedback> {
    val stillLinkedIds = linked.map { it.id }.filter { draft.stillPresent(it) }
    val resolvedIds = linked.map { it.id }.filter { !draft.stillPresent(it) }.toSet()
    val chosen = draft.discomfortIds.filter { it != PostExerciseFeedbackDraft.NONE }
    val stillForCurrent = (chosen + stillLinkedIds).distinct().ifEmpty { listOf(PostExerciseFeedbackDraft.NONE) }

    val payloads = mutableListOf<PostExerciseFeedback>()
    feedbackExercises.forEach { exercise ->
        val before = previousFeedback[exercise.id]
        payloads += PostExerciseFeedback(
            exerciseId = exercise.id,
            exerciseDbId = exercise.exerciseDbId ?: canonicalKeyOf(exercise),
            canonicalExerciseId = canonicalKeyOf(exercise),
            exerciseName = nameOf(exercise),
            technicalQuality = draft.technical(exercise.id),
            discomfortIds = chosen.ifEmpty { listOf(PostExerciseFeedbackDraft.NONE) },
            notes = before?.notes,
            perceivedIntensityRpe = before?.perceivedIntensityRpe,
            perceivedFailure = before?.perceivedFailure ?: false,
            stillPresentDiscomfortIds = stillForCurrent,
        )
    }
    val feedbackIds = feedbackExercises.map { it.id }.toSet()
    previousFeedback.forEach { (exerciseId, previous) ->
        if (exerciseId in feedbackIds) return@forEach
        val remaining = previous.unresolvedDiscomfortIds().filter { it !in resolvedIds }
        if (remaining != previous.unresolvedDiscomfortIds()) {
            payloads += previous.copy(stillPresentDiscomfortIds = remaining.ifEmpty { listOf(PostExerciseFeedbackDraft.NONE) })
        }
    }
    return payloads
}
