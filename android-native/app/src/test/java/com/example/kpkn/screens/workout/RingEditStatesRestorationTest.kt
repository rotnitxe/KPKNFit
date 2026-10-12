package com.example.kpkn.screens.workout

import android.os.Parcel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.screens.workout.components.FinishRingEdits
import com.example.kpkn.screens.workout.components.MuscleIntMapSaver
import com.example.kpkn.screens.workout.components.ReadinessSheetState
import com.example.kpkn.screens.workout.components.rememberFinishRingEdits
import com.example.kpkn.screens.workout.components.rememberReadinessSheetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val START_NEURAL = 80
private const val START_MUSCULAR = 90
private const val START_SPINAL = 70

/**
 * Las ediciones de los anillos (hoja de readiness y hoja de cierre) sobreviven a la recreación de la Activity: plegar el
 * Flip, girar la pantalla o la muerte del proceso. `MainActivity` no declara `configChanges`, así que Compose solo conserva
 * lo que está en `rememberSaveable`. Se maneja el contenedor de estado directamente (sin dibujar la hoja entera) dentro de
 * [StateRestorationTester] (el de `ui.test.junit4`; el de `ui.test` es la variante experimental de `ComposeUiTest`), que
 * guarda el estado, lo pasa por un `Bundle`/`Parcel` de verdad, descarta la composición y la vuelve a componer con lo
 * restaurado. Sin relojes ni zonas: todo son enteros y mapas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class RingEditStatesRestorationTest {

    @get:Rule
    val rule = createComposeRule()

    /** Recoge lo que crea la composición; tras restaurar, la composición nueva lo reescribe con la instancia restaurada. */
    private class Holder<T : Any> {
        lateinit var value: T
    }

    private class FinishFixture {
        var neuralSeed by mutableStateOf(60)
        var spinalSeed by mutableStateOf(70)
        var muscleSeed by mutableStateOf(mapOf("Pectorales" to 78, "Tríceps" to 86))
        val edits = Holder<FinishRingEdits>()
    }

    /** Valores de partida que la pantalla le pasa a la hoja de readiness. */
    private data class Start(
        val neural: Int,
        val muscular: Int,
        val spinal: Int,
        val muscles: Map<String, Int>,
        val discomforts: List<String>,
    )

    private val startMuscles = mapOf("Pectorales" to 85, "Tríceps" to 90, "Deltoides" to 95)

    private val saverScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun readinessHolder(tester: StateRestorationTester): Holder<ReadinessSheetState> {
        val holder = Holder<ReadinessSheetState>()
        tester.setContent { holder.value = rememberReadinessSheetState(START_NEURAL, START_MUSCULAR, START_SPINAL) }
        rule.waitForIdle()
        return holder
    }

    /** Las correcciones que haría el usuario en la hoja: Energía, Columna y dos músculos. */
    private fun editReadiness(state: ReadinessSheetState) {
        state.neural = 55
        state.userEditedNeural = true
        state.spinal = 62
        state.userEditedSpinal = true
        state.muscleAdjustments["Pectorales"] = 60
        state.userEditedMuscles["Pectorales"] = true
        state.muscleAdjustments["Tríceps"] = 40
        state.userEditedMuscles["Tríceps"] = true
    }

    private fun finishFixture(tester: StateRestorationTester): FinishFixture {
        val fixture = FinishFixture()
        tester.setContent {
            fixture.edits.value = rememberFinishRingEdits(fixture.neuralSeed, fixture.spinalSeed, fixture.muscleSeed)
        }
        rule.waitForIdle()
        return fixture
    }

    /** Las ediciones del cierre: Energía, Columna y un músculo. */
    private fun editFinish(edits: FinishRingEdits) {
        edits.neuralFinal.intValue = 45
        edits.neuralEdited.value = true
        edits.spinalFinal.intValue = 52
        edits.spinalEdited.value = true
        edits.muscleFinal["Pectorales"] = 70
        edits.musclesEdited.value = true
    }

    private fun roundTrip(state: ReadinessSheetState): ReadinessSheetState {
        val saver = ReadinessSheetState.Saver
        val saved = with(saver) { saverScope.save(state) }
        return checkNotNull(saver.restore(checkNotNull(saved)))
    }

    private fun assertSameReadiness(expected: ReadinessSheetState, actual: ReadinessSheetState) {
        assertEquals(expected.neural, actual.neural)
        assertEquals(expected.muscular, actual.muscular)
        assertEquals(expected.spinal, actual.spinal)
        assertEquals(expected.userEditedNeural, actual.userEditedNeural)
        assertEquals(expected.userEditedSpinal, actual.userEditedSpinal)
        assertEquals(expected.initialized, actual.initialized)
        assertEquals(expected.selectedDiscomforts, actual.selectedDiscomforts)
        assertEquals(expected.muscleAdjustments.toMap(), actual.muscleAdjustments.toMap())
        assertEquals(expected.userEditedMuscles.toMap(), actual.userEditedMuscles.toMap())
    }

    // ── Hoja de readiness ──────────────────────────────────────────────────────

    @Test
    fun readinessEditsSurviveRecreation() {
        val tester = StateRestorationTester(rule)
        val holder = readinessHolder(tester)
        val before = holder.value
        rule.runOnIdle {
            before.reconcile(START_NEURAL, START_MUSCULAR, START_SPINAL, startMuscles, listOf("lumbar"))
            editReadiness(before)
        }

        tester.emulateSavedInstanceStateRestore()

        val after = holder.value
        assertNotSame("la restauración debe crear un estado nuevo", before, after)
        rule.runOnIdle {
            assertEquals(55, after.neural)
            assertTrue(after.userEditedNeural)
            assertEquals(62, after.spinal)
            assertTrue(after.userEditedSpinal)
            assertEquals(60, after.muscleAdjustments["Pectorales"])
            assertEquals(40, after.muscleAdjustments["Tríceps"])
            assertEquals(95, after.muscleAdjustments["Deltoides"])
            assertEquals(true, after.userEditedMuscles["Pectorales"])
            assertEquals(true, after.userEditedMuscles["Tríceps"])
            assertNull(after.userEditedMuscles["Deltoides"])
            assertTrue(after.initialized)
            assertEquals(listOf("lumbar"), after.selectedDiscomforts)
        }
    }

    @Test
    fun readinessReconcileAfterRestoreKeepsEditedMuscles() {
        val tester = StateRestorationTester(rule)
        val holder = readinessHolder(tester)
        rule.runOnIdle {
            val state = holder.value
            state.reconcile(START_NEURAL, START_MUSCULAR, START_SPINAL, startMuscles, emptyList())
            // El usuario corrige Energía, Columna y Pectorales; Tríceps y Deltoides quedan como vinieron.
            state.neural = 55
            state.userEditedNeural = true
            state.spinal = 62
            state.userEditedSpinal = true
            state.muscleAdjustments["Pectorales"] = 60
            state.userEditedMuscles["Pectorales"] = true
        }

        tester.emulateSavedInstanceStateRestore()

        // Tras recrear, el modelo ofrece otros valores de partida (y Deltoides ya no es de la sesión).
        val newStartMuscles = mapOf("Pectorales" to 70, "Tríceps" to 75)
        val after = holder.value
        rule.runOnIdle {
            after.reconcile(
                startNeural = 66,
                startMuscular = 71,
                startSpinal = 77,
                startMuscles = newStartMuscles,
                initialDiscomforts = emptyList(),
            )
        }
        rule.runOnIdle {
            assertEquals("Energía editada se respeta", 55, after.neural)
            assertEquals("Columna editada se respeta", 62, after.spinal)
            assertEquals("el músculo editado conserva lo que puso el usuario", 60, after.muscleAdjustments["Pectorales"])
            assertEquals("el músculo sin editar sigue el valor de partida nuevo", 75, after.muscleAdjustments["Tríceps"])
            assertFalse("el músculo que ya no es de la sesión sale", "Deltoides" in after.muscleAdjustments)
            assertEquals(71, after.muscular)
        }
    }

    @Test
    fun readinessReconcileAfterRestoreRefreshesUntouchedChannels() {
        val tester = StateRestorationTester(rule)
        val holder = readinessHolder(tester)
        rule.runOnIdle {
            holder.value.reconcile(START_NEURAL, START_MUSCULAR, START_SPINAL, startMuscles, emptyList())
            // Solo se toca Pectorales: Energía, Columna y el resto de músculos siguen a los valores de partida.
            holder.value.muscleAdjustments["Pectorales"] = 60
            holder.value.userEditedMuscles["Pectorales"] = true
        }

        tester.emulateSavedInstanceStateRestore()

        val after = holder.value
        rule.runOnIdle {
            after.reconcile(
                startNeural = 66,
                startMuscular = 71,
                startSpinal = 77,
                startMuscles = mapOf("Pectorales" to 70, "Tríceps" to 75, "Deltoides" to 80),
                initialDiscomforts = emptyList(),
            )
        }
        rule.runOnIdle {
            assertEquals(66, after.neural)
            assertEquals(77, after.spinal)
            assertEquals(60, after.muscleAdjustments["Pectorales"])
            assertEquals(75, after.muscleAdjustments["Tríceps"])
            assertEquals(80, after.muscleAdjustments["Deltoides"])
        }
    }

    @Test
    fun readinessFirstInitialisationSeedsFromStart() {
        val tester = StateRestorationTester(rule)
        val state = readinessHolder(tester).value
        rule.runOnIdle {
            assertFalse(state.initialized)
            // Los valores de partida fuera de 0..100 se acotan, como antes de extraer la lógica.
            state.reconcile(
                startNeural = START_NEURAL,
                startMuscular = START_MUSCULAR,
                startSpinal = START_SPINAL,
                startMuscles = mapOf("Pectorales" to 140, "Tríceps" to -5, "Deltoides" to 80),
                initialDiscomforts = listOf("lumbar"),
            )
            assertEquals(START_NEURAL, state.neural)
            assertEquals(START_MUSCULAR, state.muscular)
            assertEquals(START_SPINAL, state.spinal)
            assertEquals(mapOf("Pectorales" to 100, "Tríceps" to 0, "Deltoides" to 80), state.muscleAdjustments.toMap())
            assertEquals(60, state.derivedMuscular(START_MUSCULAR))
            assertEquals(listOf("lumbar"), state.selectedDiscomforts)
            assertTrue(state.userEditedMuscles.isEmpty())
            assertFalse(state.userEditedNeural)
            assertFalse(state.userEditedSpinal)
            assertTrue(state.initialized)
        }
    }

    /** La misma llamada y las mismas claves que el `LaunchedEffect` de `WorkoutReadinessSheet`, sobre el contenedor real. */
    @Test
    fun readinessEffectWiringKeepsEditsAfterRecreation() {
        val tester = StateRestorationTester(rule)
        val holder = Holder<ReadinessSheetState>()
        tester.setContent {
            val state = rememberReadinessSheetState(START_NEURAL, START_MUSCULAR, START_SPINAL)
            holder.value = state
            LaunchedEffect(state.initialized, START_NEURAL, START_MUSCULAR, START_SPINAL, startMuscles) {
                state.reconcile(START_NEURAL, START_MUSCULAR, START_SPINAL, startMuscles, listOf("lumbar"))
            }
        }
        rule.waitForIdle()
        val before = holder.value
        rule.runOnIdle {
            assertTrue("el efecto inicializa la hoja", before.initialized)
            editReadiness(before)
        }

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        val after = holder.value
        assertNotSame(before, after)
        rule.runOnIdle {
            assertEquals(55, after.neural)
            assertEquals(62, after.spinal)
            assertEquals(60, after.muscleAdjustments["Pectorales"])
            assertEquals(40, after.muscleAdjustments["Tríceps"])
            assertEquals(95, after.muscleAdjustments["Deltoides"])
        }
    }

    /** Transcripción literal del cuerpo anterior del `LaunchedEffect` (campos simples): la referencia de que extraerlo no cambió nada. */
    private class OriginalEffectModel {
        var neural = 0
        var muscular = 0
        var spinal = 0
        var userEditedNeural = false
        var userEditedSpinal = false
        var initialized = false
        var selectedDiscomforts: List<String> = emptyList()
        val muscleAdjustments = mutableMapOf<String, Int>()
        val userEditedMuscles = mutableMapOf<String, Boolean>()

        private fun derivedMuscular(readinessMuscularStart: Int): Int =
            if (muscleAdjustments.isEmpty()) readinessMuscularStart.coerceIn(0, 100)
            else muscleAdjustments.values.average().toInt().coerceIn(0, 100)

        fun effect(start: Start) {
            val readinessNeuralStart = start.neural
            val readinessMuscularStart = start.muscular
            val readinessSpinalStart = start.spinal
            val sessionMuscleStartingBatteries = start.muscles
            val initialDiscomforts = start.discomforts
            if (!initialized) {
                neural = readinessNeuralStart
                muscular = derivedMuscular(readinessMuscularStart)
                spinal = readinessSpinalStart
                muscleAdjustments.clear()
                sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                    muscleAdjustments[muscleId] = value.coerceIn(0, 100)
                }
                selectedDiscomforts = initialDiscomforts
                initialized = true
            } else {
                if (!userEditedNeural) neural = readinessNeuralStart
                muscular = readinessMuscularStart
                if (!userEditedSpinal) spinal = readinessSpinalStart
                val validMuscles = sessionMuscleStartingBatteries.keys
                muscleAdjustments.keys
                    .filter { it !in validMuscles }
                    .forEach { muscleAdjustments.remove(it) }
                sessionMuscleStartingBatteries.forEach { (muscleId, value) ->
                    if (userEditedMuscles[muscleId] != true) {
                        muscleAdjustments[muscleId] = value.coerceIn(0, 100)
                    }
                }
            }
        }
    }

    private fun assertSameAsOriginal(step: String, expected: OriginalEffectModel, actual: ReadinessSheetState) {
        assertEquals("$step: Energía", expected.neural, actual.neural)
        assertEquals("$step: Músculos (campo guardado)", expected.muscular, actual.muscular)
        assertEquals("$step: Columna", expected.spinal, actual.spinal)
        assertEquals("$step: Energía editada", expected.userEditedNeural, actual.userEditedNeural)
        assertEquals("$step: Columna editada", expected.userEditedSpinal, actual.userEditedSpinal)
        assertEquals("$step: inicializada", expected.initialized, actual.initialized)
        assertEquals("$step: molestias", expected.selectedDiscomforts, actual.selectedDiscomforts)
        assertEquals("$step: músculos", expected.muscleAdjustments.toMap(), actual.muscleAdjustments.toMap())
        assertEquals("$step: músculos editados", expected.userEditedMuscles.toMap(), actual.userEditedMuscles.toMap())
    }

    /** Sin recreación `reconcile` hace lo mismo que el cuerpo anterior del efecto, paso a paso (primera vez, ediciones y refrescos). */
    @Test
    fun reconcileMatchesTheOriginalEffectBody() {
        val original = OriginalEffectModel()
        val state = ReadinessSheetState(START_NEURAL, START_MUSCULAR, START_SPINAL)
        fun reconcileBoth(step: String, start: Start) {
            original.effect(start)
            state.reconcile(start.neural, start.muscular, start.spinal, start.muscles, start.discomforts)
            assertSameAsOriginal(step, original, state)
        }

        val first = Start(80, 90, 70, startMuscles, listOf("lumbar"))
        reconcileBoth("primera inicialización", first)
        // El efecto se vuelve a lanzar al cambiar `initialized`: segunda pasada idéntica sobre lo recién sembrado.
        reconcileBoth("segunda pasada", first)

        // El usuario toca Energía y Pectorales (en ambos modelos).
        original.neural = 55
        original.userEditedNeural = true
        original.muscleAdjustments["Pectorales"] = 60
        original.userEditedMuscles["Pectorales"] = true
        state.neural = 55
        state.userEditedNeural = true
        state.muscleAdjustments["Pectorales"] = 60
        state.userEditedMuscles["Pectorales"] = true
        assertSameAsOriginal("ediciones", original, state)

        reconcileBoth("nuevos valores de partida", Start(66, 71, 77, mapOf("Pectorales" to 70, "Tríceps" to 75), emptyList()))

        original.spinal = 62
        original.userEditedSpinal = true
        state.spinal = 62
        state.userEditedSpinal = true
        reconcileBoth("Columna editada y fuera de rango", Start(50, 40, 30, mapOf("Pectorales" to 20, "Tríceps" to 150, "Glúteos" to -3), listOf("x")))
    }

    // ── Hoja de cierre ─────────────────────────────────────────────────────────

    @Test
    fun finishEditsSurviveRecreation() {
        val tester = StateRestorationTester(rule)
        val fixture = finishFixture(tester)
        val before = fixture.edits.value
        rule.runOnIdle { editFinish(before) }

        tester.emulateSavedInstanceStateRestore()

        val after = fixture.edits.value
        assertNotSame("la restauración debe crear las ediciones de nuevo", before, after)
        rule.runOnIdle {
            assertEquals(45, after.neuralFinal.intValue)
            assertTrue(after.neuralEdited.value)
            assertEquals(52, after.spinalFinal.intValue)
            assertTrue(after.spinalEdited.value)
            assertEquals(mapOf("Pectorales" to 70, "Tríceps" to 86), after.muscleFinal.toMap())
            assertTrue(after.musclesEdited.value)
        }

        // Independencia por semilla: cambiar SOLO la de Columna reinicia únicamente Columna.
        rule.runOnIdle { fixture.spinalSeed = 75 }
        rule.waitForIdle()
        val reseeded = fixture.edits.value
        rule.runOnIdle {
            assertEquals(75, reseeded.spinalFinal.intValue)
            assertFalse(reseeded.spinalEdited.value)
            assertEquals(45, reseeded.neuralFinal.intValue)
            assertTrue(reseeded.neuralEdited.value)
            assertEquals(mapOf("Pectorales" to 70, "Tríceps" to 86), reseeded.muscleFinal.toMap())
            assertTrue(reseeded.musclesEdited.value)
        }
    }

    /** Cada grupo se reinicia solo con SU semilla; con o sin recreación de la Activity de por medio. */
    private fun assertSeedsResetOnlyTheirOwnGroup(recreate: Boolean) {
        val tester = StateRestorationTester(rule)
        val fixture = finishFixture(tester)
        rule.runOnIdle { editFinish(fixture.edits.value) }
        if (recreate) tester.emulateSavedInstanceStateRestore()

        // Solo la semilla de Energía.
        rule.runOnIdle { fixture.neuralSeed = 65 }
        rule.waitForIdle()
        val afterNeural = fixture.edits.value
        rule.runOnIdle {
            assertEquals(65, afterNeural.neuralFinal.intValue)
            assertFalse(afterNeural.neuralEdited.value)
            assertEquals(52, afterNeural.spinalFinal.intValue)
            assertTrue(afterNeural.spinalEdited.value)
            assertEquals(70, afterNeural.muscleFinal["Pectorales"])
            assertTrue(afterNeural.musclesEdited.value)
        }

        // Solo la semilla de los músculos.
        rule.runOnIdle { fixture.muscleSeed = mapOf("Pectorales" to 80, "Tríceps" to 88) }
        rule.waitForIdle()
        val afterMuscles = fixture.edits.value
        rule.runOnIdle {
            assertEquals(mapOf("Pectorales" to 80, "Tríceps" to 88), afterMuscles.muscleFinal.toMap())
            assertFalse(afterMuscles.musclesEdited.value)
            assertEquals(65, afterMuscles.neuralFinal.intValue)
            assertEquals(52, afterMuscles.spinalFinal.intValue)
            assertTrue(afterMuscles.spinalEdited.value)
        }
    }

    @Test
    fun finishSeedsResetOnlyTheirOwnGroup() = assertSeedsResetOnlyTheirOwnGroup(recreate = false)

    @Test
    fun finishSeedsResetOnlyTheirOwnGroupAfterRecreation() = assertSeedsResetOnlyTheirOwnGroup(recreate = true)

    // ── Savers ─────────────────────────────────────────────────────────────────

    @Test
    fun saversRoundTripBoundaryValues() {
        // Mapas vacíos, 0 y 100, solo uno de los indicadores activo.
        val empty = ReadinessSheetState(startNeural = 0, startMuscular = 100, startSpinal = 0).apply {
            userEditedSpinal = true
        }
        assertSameReadiness(empty, roundTrip(empty))

        // Valores límite dentro de los mapas y todos los indicadores activos.
        val full = ReadinessSheetState(startNeural = 100, startMuscular = 0, startSpinal = 100).apply {
            userEditedNeural = true
            userEditedSpinal = true
            initialized = true
            selectedDiscomforts = listOf("lumbar", "rodilla")
            muscleAdjustments["Pectorales"] = 0
            muscleAdjustments["Tríceps"] = 100
            userEditedMuscles["Pectorales"] = true
        }
        assertSameReadiness(full, roundTrip(full))

        // Mapa de músculos del cierre: 0 y 100 vuelven tal cual; vacío no guarda nada (rememberSaveable usa la semilla).
        val finalMuscles = mutableStateMapOf("Pectorales" to 0, "Tríceps" to 100)
        val savedMuscles = with(MuscleIntMapSaver) { saverScope.save(finalMuscles) }
        val restoredMuscles = checkNotNull(MuscleIntMapSaver.restore(checkNotNull(savedMuscles)))
        assertEquals(finalMuscles.toMap(), restoredMuscles.toMap())
        assertNull(with(MuscleIntMapSaver) { saverScope.save(mutableStateMapOf()) })
    }

    /** Muerte del proceso: lo guardado pasa por un `Parcel` de verdad (el del `Bundle` del sistema) antes de restaurarse. */
    @Test
    fun readinessSavedFormSurvivesAParcel() {
        val state = ReadinessSheetState(START_NEURAL, START_MUSCULAR, START_SPINAL)
        state.reconcile(START_NEURAL, START_MUSCULAR, START_SPINAL, startMuscles, listOf("lumbar"))
        editReadiness(state)
        val saved = with(ReadinessSheetState.Saver) { saverScope.save(state) }
        val parcel = Parcel.obtain()
        try {
            parcel.writeValue(arrayListOf(saved))
            parcel.setDataPosition(0)
            val back = parcel.readValue(javaClass.classLoader) as List<*>
            val restored = checkNotNull(ReadinessSheetState.Saver.restore(checkNotNull(back.first())))
            assertSameReadiness(state, restored)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun readinessSaverRejectsIncompleteSavedData() {
        // Un guardado al que le falta un entero no revienta: restore devuelve null y rememberSaveable usa los valores de partida.
        assertNull(ReadinessSheetState.Saver.restore(listOf("neural", "no es un entero")))
    }
}
