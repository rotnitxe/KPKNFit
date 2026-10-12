package com.example.kpkn.screens.workout.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap

// Estado editable de las hojas de readiness y de cierre, guardable. `MainActivity` no declara `configChanges`: plegar el
// Flip, girar la pantalla o que el sistema mate el proceso recrea la Activity, y Compose solo conserva lo que pasa por
// `rememberSaveable` (con un `Saver` si el tipo no es primitivo). Un `remember` suelto pierde la edición en silencio.

/**
 * Todo lo que el usuario puede cambiar en la hoja de readiness, en un solo contenedor guardable ([Saver]).
 *
 * Antes `userEditedMuscles` era un `remember` suelto: tras recrear la Activity quedaba vacío y [reconcile] pisaba con la
 * semilla las correcciones por músculo (Energía y Columna sí se conservaban). Los tres argumentos son los valores
 * iniciales de cada anillo.
 */
@Stable
internal class ReadinessSheetState(startNeural: Int, startMuscular: Int, startSpinal: Int) {
    var neural by mutableIntStateOf(startNeural)
    var muscular by mutableIntStateOf(startMuscular)
    var spinal by mutableIntStateOf(startSpinal)
    var userEditedNeural by mutableStateOf(false)
    var userEditedSpinal by mutableStateOf(false)
    var initialized by mutableStateOf(false)
    var selectedDiscomforts by mutableStateOf<List<String>>(emptyList())

    /** Frescura por músculo que muestra la hoja: la semilla o la corrección del usuario. */
    val muscleAdjustments: SnapshotStateMap<String, Int> = mutableStateMapOf()

    /** Músculos que el usuario corrigió a mano: [reconcile] no los pisa. */
    val userEditedMuscles: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    /** Valor del anillo de Músculos: el promedio de los ajustes por músculo, o la semilla si aún no hay ninguno. */
    fun derivedMuscular(startMuscular: Int): Int =
        if (muscleAdjustments.isEmpty()) startMuscular.coerceIn(0, 100)
        else muscleAdjustments.values.average().toInt().coerceIn(0, 100)

    /**
     * Cuerpo del `LaunchedEffect` de la hoja, extraído sin cambios para poder probarlo. La primera vez copia los valores de
     * partida; las siguientes (también la primera composición tras recrear la Activity) solo refrescan lo que el usuario
     * no tocó.
     */
    fun reconcile(
        startNeural: Int,
        startMuscular: Int,
        startSpinal: Int,
        startMuscles: Map<String, Int>,
        initialDiscomforts: List<String>,
    ) {
        if (!initialized) {
            neural = startNeural
            muscular = derivedMuscular(startMuscular)
            spinal = startSpinal
            muscleAdjustments.clear()
            startMuscles.forEach { (muscleId, value) ->
                muscleAdjustments[muscleId] = value.coerceIn(0, 100)
            }
            selectedDiscomforts = initialDiscomforts
            initialized = true
        } else {
            if (!userEditedNeural) neural = startNeural
            muscular = startMuscular
            if (!userEditedSpinal) spinal = startSpinal
            val validMuscles = startMuscles.keys
            muscleAdjustments.keys
                .filter { it !in validMuscles }
                .forEach { muscleAdjustments.remove(it) }
            startMuscles.forEach { (muscleId, value) ->
                if (userEditedMuscles[muscleId] != true) {
                    muscleAdjustments[muscleId] = value.coerceIn(0, 100)
                }
            }
        }
    }

    companion object {
        private const val NEURAL = "neural"
        private const val MUSCULAR = "muscular"
        private const val SPINAL = "spinal"
        private const val EDITED_NEURAL = "userEditedNeural"
        private const val EDITED_SPINAL = "userEditedSpinal"
        private const val INITIALIZED = "initialized"
        private const val DISCOMFORTS = "selectedDiscomforts"
        private const val ADJUSTMENTS = "muscleAdjustments"
        private const val EDITED_MUSCLES = "userEditedMuscles"

        /**
         * Guarda solo tipos que el `Bundle` acepta (enteros, booleanos y copias `ArrayList`/`HashMap`) y restaura los mapas
         * como `mutableStateMapOf`, igual que el `mapSaver` anterior de `muscleAdjustments`. Si falta un entero, devuelve
         * `null` y `rememberSaveable` vuelve a crear el estado desde los valores de partida.
         */
        val Saver: Saver<ReadinessSheetState, Any> = mapSaver(
            save = { state ->
                mapOf(
                    NEURAL to state.neural,
                    MUSCULAR to state.muscular,
                    SPINAL to state.spinal,
                    EDITED_NEURAL to state.userEditedNeural,
                    EDITED_SPINAL to state.userEditedSpinal,
                    INITIALIZED to state.initialized,
                    DISCOMFORTS to ArrayList(state.selectedDiscomforts),
                    ADJUSTMENTS to HashMap(state.muscleAdjustments),
                    EDITED_MUSCLES to HashMap(state.userEditedMuscles),
                )
            },
            restore = { saved ->
                val savedNeural = saved[NEURAL] as? Int ?: return@mapSaver null
                val savedMuscular = saved[MUSCULAR] as? Int ?: return@mapSaver null
                val savedSpinal = saved[SPINAL] as? Int ?: return@mapSaver null
                ReadinessSheetState(savedNeural, savedMuscular, savedSpinal).apply {
                    userEditedNeural = saved[EDITED_NEURAL] as? Boolean ?: false
                    userEditedSpinal = saved[EDITED_SPINAL] as? Boolean ?: false
                    initialized = saved[INITIALIZED] as? Boolean ?: false
                    selectedDiscomforts = (saved[DISCOMFORTS] as? List<*>)?.filterIsInstance<String>().orEmpty()
                    muscleAdjustments.putAll(savedEntries(saved[ADJUSTMENTS]) { (it as? Number)?.toInt() })
                    userEditedMuscles.putAll(savedEntries(saved[EDITED_MUSCLES]) { it as? Boolean })
                }
            },
        )
    }
}

/** Estado de la hoja de readiness que sobrevive a la recreación de la Activity; los valores de partida solo siembran la primera vez. */
@Composable
internal fun rememberReadinessSheetState(
    startNeural: Int,
    startMuscular: Int,
    startSpinal: Int,
): ReadinessSheetState = rememberSaveable(saver = ReadinessSheetState.Saver) {
    ReadinessSheetState(startNeural, startMuscular, startSpinal)
}

/**
 * Ediciones del usuario en la hoja de cierre. Cada grupo se reinicia solo cuando cambia SU PROPIA semilla (Energía con
 * `neuralSeed`, Columna con `spinalSeed`, músculos con `muscleSeed`), como antes con `remember(semilla)`, pero ahora
 * sobrevive a la recreación de la Activity.
 */
@Stable
internal class FinishRingEdits(
    val neuralFinal: MutableIntState,
    val neuralEdited: MutableState<Boolean>,
    val spinalFinal: MutableIntState,
    val spinalEdited: MutableState<Boolean>,
    val musclesEdited: MutableState<Boolean>,
    val muscleFinal: SnapshotStateMap<String, Int>,
)

/**
 * Crea las ediciones del cierre con `rememberSaveable(semilla)`: no se juntan las tres semillas en una sola clave, así
 * que cambiar una no reinicia los otros grupos. Al restaurar tras recrear la Activity Compose no compara la semilla: lo
 * guardado vuelve tal cual.
 */
@Composable
internal fun rememberFinishRingEdits(
    neuralSeed: Int,
    spinalSeed: Int,
    muscleSeed: Map<String, Int>,
): FinishRingEdits {
    val neuralFinal = rememberSaveable(neuralSeed) { mutableIntStateOf(neuralSeed) }
    val neuralEdited = rememberSaveable(neuralSeed) { mutableStateOf(false) }
    val spinalFinal = rememberSaveable(spinalSeed) { mutableIntStateOf(spinalSeed) }
    val spinalEdited = rememberSaveable(spinalSeed) { mutableStateOf(false) }
    val musclesEdited = rememberSaveable(muscleSeed) { mutableStateOf(false) }
    val muscleFinal = rememberSaveable(muscleSeed, saver = MuscleIntMapSaver) {
        mutableStateMapOf<String, Int>().also { map -> map.putAll(muscleSeed) }
    }
    return remember(neuralFinal, neuralEdited, spinalFinal, spinalEdited, musclesEdited, muscleFinal) {
        FinishRingEdits(neuralFinal, neuralEdited, spinalFinal, spinalEdited, musclesEdited, muscleFinal)
    }
}

/** Mapa músculo→valor guardable; se restaura como `mutableStateMapOf` (el mismo `mapSaver` que tenía `muscleAdjustments`). */
internal val MuscleIntMapSaver: Saver<SnapshotStateMap<String, Int>, Any> = mapSaver(
    save = { map -> map.toMap() },
    restore = { saved ->
        mutableStateMapOf<String, Int>().apply { putAll(savedEntries(saved) { (it as? Number)?.toInt() }) }
    },
)

/** Entradas clave→valor de un mapa guardado; ignora las que no tengan la forma esperada, como el `mapSaver` anterior. */
private fun <V : Any> savedEntries(saved: Any?, valueOf: (Any?) -> V?): Map<String, V> {
    val entries = LinkedHashMap<String, V>()
    (saved as? Map<*, *>)?.forEach { (key, value) ->
        val k = key as? String ?: return@forEach
        val v = valueOf(value) ?: return@forEach
        entries[k] = v
    }
    return entries
}
