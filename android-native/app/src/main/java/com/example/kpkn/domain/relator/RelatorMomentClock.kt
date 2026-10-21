package com.example.kpkn.domain.relator

/** Permanencia mínima de un mensaje antes de cambiar por uno de prioridad media o baja (LR1g). */
internal const val RELATOR_PERMANENCE_MS = 12_000L

/** Tiempo sin ediciones tras el último cambio de carga, reps o RIR para que cuente como momento (LR1g). */
internal const val RELATOR_EDIT_SETTLE_MS = 1_500L

/** Sin registrar ni editar durante este tiempo en la serie: IDLE_NUDGE (LR1g). */
internal const val RELATOR_IDLE_NUDGE_MS = 120_000L

/** Segundos de descanso que faltan para REST_PREP (LR1g). */
internal const val RELATOR_REST_PREP_SECONDS = 15

/** Descanso mínimo, en segundos, para que haya REST_MID (LR1g). */
internal const val RELATOR_REST_MID_MIN_SECONDS = 150

/**
 * Señales que la pantalla pasa al reloj del relator en cada tick o cambio (LR1g). Sin Android.
 * [loggedKey] cambia al registrar una serie; [editRevision] sube con cada edición de la serie actual;
 * [milestoneKey] es el hito de sesión activo ahora (mitad, último ejercicio, tiempo justo) o null.
 */
data class RelatorMomentInput(
    val nowMs: Long,
    val setKey: String,
    val exerciseId: String,
    val resting: Boolean,
    val loggedKey: String? = null,
    val editRevision: Long = 0L,
    val assistKey: String? = null,
    val restRemainingSeconds: Int = 0,
    val restPlannedSeconds: Int = 0,
    val milestoneKey: String? = null,
)

/**
 * Decide CUÁNDO habla el relator (LR1g). Puro: el ViewModel le pasa señales y él devuelve el momento que toca, o
 * null. Reglas:
 * - un momento de prioridad alta sustituye el mensaje al instante;
 * - uno de prioridad media o baja espera a que el mensaje actual lleve [RELATOR_PERMANENCE_MS];
 * - una edición se asienta [RELATOR_EDIT_SETTLE_MS] después de la última;
 * - REST_PREP, REST_MID, MILESTONE e IDLE_NUDGE se disparan una sola vez (por descanso, sesión o serie).
 * El ViewModel llama a [nextWakeMs] para despertarse cuando algo pendiente venza.
 */
class RelatorMomentClock {
    private var initialized = false
    private var displayedSetKey: String? = null
    private var displayedExerciseId: String? = null
    private var wasResting = false
    private var loggedKey: String? = null
    private var editRevision = 0L
    private var editPending = false
    private var lastEditMs = 0L
    private var assistKey: String? = null
    private var lastActivityMs = 0L
    private var idleNudgedSetKey: String? = null
    private var restPrepDone = false
    private var restMidDone = false
    private val milestonesDone = HashSet<String>()
    private var shownAtMs: Long? = null
    private var pending: RelatorMoment? = null

    /** Momento que toca decir ahora, o null si el mensaje actual sigue. */
    fun step(input: RelatorMomentInput): RelatorMoment? {
        val now = input.nowMs
        if (!initialized) {
            initialized = true
            lastActivityMs = now
            loggedKey = input.loggedKey
            editRevision = input.editRevision
            assistKey = input.assistKey
        }
        val high = ArrayList<RelatorMoment>(3)
        val lowOrMedium = ArrayList<RelatorMoment>(2)
        val restStarted = !wasResting && input.resting
        val restEnded = wasResting && !input.resting
        wasResting = input.resting
        if (restStarted) {
            restPrepDone = false
            restMidDone = false
        }

        // Registro de serie: lo primero que se ve en el descanso.
        if (input.loggedKey != null && input.loggedKey != loggedKey) {
            high += RelatorMoment.SET_LOGGED
            lastActivityMs = now
        }
        if (input.loggedKey != null) loggedKey = input.loggedKey

        // Ediciones: se asientan tras la última; varias seguidas dan un solo cambio.
        if (input.editRevision != editRevision) {
            editRevision = input.editRevision
            editPending = true
            lastEditMs = now
            lastActivityMs = now
        }
        if (editPending && now - lastEditMs >= RELATOR_EDIT_SETTLE_MS) {
            editPending = false
            high += RelatorMoment.USER_EDIT
        }

        // Ayuda de estructura: al aparecer.
        if (input.assistKey != assistKey) {
            if (input.assistKey != null) high += RelatorMoment.ASSIST
            assistKey = input.assistKey
        }

        // Serie actual: cambia cuando acaba el descanso (durante el descanso la siguiente aún no es la actual).
        if (!input.resting) {
            val setChanged = input.setKey != displayedSetKey
            if (setChanged || restEnded) {
                val exerciseChanged = input.exerciseId != displayedExerciseId
                high += if (exerciseChanged) RelatorMoment.EXERCISE_START else RelatorMoment.SET_READY
                displayedSetKey = input.setKey
                displayedExerciseId = input.exerciseId
                lastActivityMs = now
            }
        }

        // Descanso: aviso al acabarse y mitad, una vez cada uno.
        if (input.resting) {
            val remaining = input.restRemainingSeconds
            val planned = input.restPlannedSeconds
            if (!restPrepDone && remaining <= RELATOR_REST_PREP_SECONDS) {
                restPrepDone = true
                lowOrMedium += RelatorMoment.REST_PREP
            }
            if (!restMidDone && planned >= RELATOR_REST_MID_MIN_SECONDS && remaining <= planned / 2) {
                restMidDone = true
                lowOrMedium += RelatorMoment.REST_MID
            }
        }

        // Hitos de sesión: una vez cada uno.
        input.milestoneKey?.let { key ->
            if (milestonesDone.add(key)) lowOrMedium += RelatorMoment.MILESTONE
        }

        // Serie sin movimiento: una vez por serie.
        if (!input.resting && idleNudgedSetKey != input.setKey && now - lastActivityMs >= RELATOR_IDLE_NUDGE_MS) {
            idleNudgedSetKey = input.setKey
            lowOrMedium += RelatorMoment.IDLE_NUDGE
        }

        for (moment in lowOrMedium) {
            val current = pending
            if (current == null || moment.level.order < current.level.order) pending = moment
        }

        val urgent = HIGH_ORDER.firstOrNull { it in high }
        if (urgent != null) {
            shownAtMs = now
            return urgent
        }
        val due = pending ?: return null
        val shown = shownAtMs
        if (shown == null || now - shown >= RELATOR_PERMANENCE_MS) {
            pending = null
            shownAtMs = now
            return due
        }
        return null
    }

    /** Milisegundo en que algo pendiente puede disparar (permanencia, asentado de edición o inactividad), o null. */
    fun nextWakeMs(nowMs: Long): Long? {
        val wakes = ArrayList<Long>(3)
        if (pending != null) wakes += (shownAtMs?.plus(RELATOR_PERMANENCE_MS) ?: nowMs)
        if (editPending) wakes += lastEditMs + RELATOR_EDIT_SETTLE_MS
        val setKey = displayedSetKey
        if (!wasResting && setKey != null && idleNudgedSetKey != setKey) {
            wakes += lastActivityMs + RELATOR_IDLE_NUDGE_MS
        }
        return wakes.minOrNull()
    }

    private companion object {
        /** Orden de prioridad entre momentos de nivel alto que llegan en el mismo paso. */
        val HIGH_ORDER = listOf(
            RelatorMoment.SET_LOGGED,
            RelatorMoment.EXERCISE_START,
            RelatorMoment.SET_READY,
            RelatorMoment.ASSIST,
            RelatorMoment.USER_EDIT,
        )
    }
}
