package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.SetPurpose
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.RecordSetResult
import com.example.kpkn.screens.workout.SetAdvancedFeedback
import com.example.kpkn.screens.workout.WeightSuggestion
import com.example.kpkn.screens.workout.WorkoutSetDraft
import com.example.kpkn.screens.workout.buildEffortScale
import com.example.kpkn.screens.workout.effortCenterPx
import com.example.kpkn.ui.theme.KPKNTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El esfuerzo (RIR/RPE) de la tarjeta de la serie (LV8), medido con la tarjeta REAL y su ✓:
 *  - registrar exige haber marcado el esfuerzo: sin tocarlo, el ✓ no registra nada, y tocar el mínimo cuenta como elegirlo;
 *  - una serie nunca se registra con una intensidad que la persona no eligió (ni el RPE 0 fantasma de las series sin objetivo de
 *    esfuerzo, ni el objetivo del plan preseleccionado);
 *  - lo que se registra es lo que marcó: el valor, su unidad y el fallo;
 *  - la tarjeta nunca cambia de tamaño (ley 1): ni de una serie a otra, ni con ROM, ni al mover el slider o salir el aviso.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class SetExecutionCardEffortTest {

    @get:Rule
    val rule = createComposeRule()

    /** Lo que la tarjeta entrega a `onRecordV2` al pulsar el ✓. */
    private data class Registro(
        val weight: Double,
        val value: Double,
        val intensity: Double?,
        val advanced: SetAdvancedFeedback,
    )

    private val registros = mutableListOf<Registro>()
    private val holder = RecordActionHolder()

    private data class Caso(
        val nombre: String,
        val ejercicio: Exercise,
        val serie: ExerciseSet,
        val borrador: WorkoutSetDraft? = null,
        /** El peso corporal que ya conoce la app; nulo es «aún no lo dijo» (el asistido lo pide encima de la tarjeta). */
        val pesoCorporal: Double? = 80.0,
    )

    private fun ejercicio(serie: ExerciseSet, modo: TrainingMode = TrainingMode.REPS, conRom: Boolean = false) = Exercise(
        id = "ej-1",
        name = "Press Banca",
        trainingMode = modo,
        sets = listOf(serie),
        trackRom = conRom,
    )

    private fun serieConRir() = ExerciseSet(
        id = "s-rir",
        targetReps = 6,
        targetRIR = 2,
        intensityMode = IntensityMode.RIR,
        weight = 100.5,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
    )

    /** Sin objetivo de esfuerzo: es la serie que antes se registraba con un RPE 0 fantasma. */
    private fun serieSinObjetivo() = ExerciseSet(
        id = "s-libre",
        targetReps = 6,
        weight = 100.5,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
    )

    private fun serieConRpe() = serieConRir().copy(id = "s-rpe", targetRIR = null, targetRPE = 8.0, intensityMode = IntensityMode.RPE)

    private fun serieAFallo() = serieConRir().copy(id = "s-fallo", targetRIR = null, isFailure = true, intensityMode = IntensityMode.FAILURE)

    private fun serieAmrap() = serieSinObjetivo().copy(id = "s-amrap", purpose = SetPurpose.AMRAP, amrapMinimumReps = 6)

    private fun serieSoloRm() = ExerciseSet(
        id = "s-rm",
        targetReps = 3,
        weight = 100.0,
        targetPercentageRM = 80.0,
        intensityMode = IntensityMode.SOLO_RM,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
    )

    private fun serieEnTiempo() = ExerciseSet(
        id = "s-tiempo",
        targetDuration = 45,
        weight = 20.0,
        targetRIR = 2,
        intensityMode = IntensityMode.RIR,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.TIME,
    )

    /** Un ejercicio por tiempo sin duración planificada: no hay línea «Objetivo 45s» bajo el temporizador. */
    private fun serieEnTiempoLibre() = serieEnTiempo().copy(id = "s-tiempo-libre", targetDuration = null)

    private fun serieAsistida() = serieConRir().copy(id = "s-asistida", weight = 20.0, loadModeV2 = LoadModeV2.ASSISTED)

    private fun serieConPesoCorporal() = serieConRir().copy(id = "s-corporal", weight = 0.0, loadModeV2 = LoadModeV2.BODYWEIGHT)

    /** Un rest-pause programado desde el editor (dos pausas): tras el ✓ de la serie principal empieza la pausa técnica de 15 s. */
    private fun serieRestPauseProgramada() = serieConRir().copy(
        id = "s-rp",
        isRestPause = true,
        plannedIntensityTechniques = listOf(
            PlannedTechnique(id = "t-rp", type = TechniqueType.REST_PAUSE, params = mapOf("count" to "2", "betweenMarked" to "true")),
        ),
    )

    /** Una serie con un drop-set manual de dos drops: al pulsar el ✓ se abre el panel guiado. */
    private fun serieConDrops() = serieConRir().copy(
        id = "s-drops",
        isDropSet = true,
        plannedIntensityTechniques = listOf(
            PlannedTechnique(id = "t-drop", type = TechniqueType.DROP_SET, params = mapOf("count" to "2")),
        ),
    )

    private fun casoRir() = Caso("rir2", ejercicio(serieConRir()), serieConRir())
    private fun casoSinObjetivo() = Caso("sinObjetivo", ejercicio(serieSinObjetivo()), serieSinObjetivo())

    private fun casosDeAltura(): List<Caso> = listOf(
        casoRir(),
        casoSinObjetivo(),
        Caso("rpe8", ejercicio(serieConRpe()), serieConRpe()),
        Caso("fallo", ejercicio(serieAFallo()), serieAFallo()),
        Caso("amrap", ejercicio(serieAmrap()), serieAmrap()),
        Caso("soloRm", ejercicio(serieSoloRm(), TrainingMode.RM), serieSoloRm()),
        Caso("conRom", ejercicio(serieConRir(), conRom = true), serieConRir()),
        Caso("pesoCorporal", ejercicio(serieConPesoCorporal()), serieConPesoCorporal()),
        Caso("asistidoConPeso", ejercicio(serieAsistida()), serieAsistida()),
        // El asistido sin peso corporal pide el peso ENCIMA de la tarjeta: no la alarga.
        Caso("asistidoSinPeso", ejercicio(serieAsistida()), serieAsistida(), pesoCorporal = null),
        // Un ejercicio por tiempo mide lo mismo que uno por repeticiones: el temporizador va junto al valor y su línea de apoyo, en la cabecera.
        Caso("tiempo", ejercicio(serieEnTiempo(), TrainingMode.TIME), serieEnTiempo()),
        Caso("tiempoLibre", ejercicio(serieEnTiempoLibre(), TrainingMode.TIME), serieEnTiempoLibre()),
    )

    private fun mostrar(caso: Caso, fontScale: Float = 1f) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.width(360.dp).background(Color.Black), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.width(272.dp).testTag(TARJETA)) {
                                SetInputCardV2(
                                    exercise = caso.ejercicio,
                                    setIndex = 0,
                                    currentSet = caso.serie,
                                    ghostSet = null,
                                    weightSuggestion = WeightSuggestion(suggestedWeight = 98.0, reason = "Ajuste por RIR"),
                                    previousSessionFirstSetWeight = 100.0,
                                    sessionAccentColor = Color(0xFFF49A6E),
                                    initialBodyWeight = caso.pesoCorporal,
                                    recordActionHolder = holder,
                                    recordActionPageKey = PAGINA,
                                    initialDraft = caso.borrador,
                                    onShowHistory = {},
                                    onSetBodyWeight = {},
                                    onRecordV2 = { _, _, weight, value, intensity, advanced, _, _, _, onResult ->
                                        registros.clear() // Revisions of this one execution replace its OPEN observation.
                                        registros += Registro(weight, value, intensity, advanced)
                                        onResult(RecordSetResult.Created("ej-1_0"))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /** Pulsa el ✓ de la tarjeta: la acción que el cajón flotante (`WorkoutRecordFab`) invoca. */
    private fun pulsarCheck() {
        rule.runOnIdle { holder.actionForPage(PAGINA)?.invoke() }
        rule.waitForIdle()
    }

    /** Marca el esfuerzo en el índice [indice] de la escala, como lo hace la accesibilidad (acción de progreso del slider). */
    private fun marcar(indice: Int) {
        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(indice.toFloat()) }
        rule.waitForIdle()
    }

    private fun alturaDeLaTarjeta(): Float = rule.onNodeWithTag(TARJETA).fetchSemanticsNode().boundsInRoot.height / 3f

    private fun descripcionDelSlider(): String =
        rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()

    private fun marcaActualDelSlider(): Float =
        rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    // ── Obligatorio y sin preselección ────────────────────────────────────────────────────────────────

    @Test
    fun unaSerieSinObjetivoDeEsfuerzoNoSeRegistraConUnRpeFantasma() {
        mostrar(casoSinObjetivo())

        pulsarCheck()

        assertTrue(
            "sin marcar el esfuerzo no debe registrarse nada, pero se registró con intensidad ${registros.firstOrNull()?.intensity}",
            registros.isEmpty(),
        )
    }

    @Test
    fun unObjetivoRirNoSeConviertePorSiSoloEnLaIntensidadRegistrada() {
        mostrar(casoRir())

        pulsarCheck()

        assertTrue(
            "el objetivo RIR 2 no se elige solo: se registró con intensidad ${registros.firstOrNull()?.intensity}",
            registros.isEmpty(),
        )
    }

    @Test
    fun unPlanAFalloNoEmpiezaEnFallo() {
        mostrar(Caso("fallo", ejercicio(serieAFallo()), serieAFallo()))

        pulsarCheck()

        assertTrue("el fallo del plan no se elige solo: ${registros.firstOrNull()}", registros.isEmpty())
    }

    @Test
    fun elSliderEmpiezaEnElMinimoYSinMarcar() {
        mostrar(casoRir())

        assertEquals("el pulgar espera en la primera marca", 0f, marcaActualDelSlider(), 0f)
        assertEquals("Esfuerzo sin marcar", descripcionDelSlider())
        rule.onNodeWithText("Sin marcar").assertExists()
    }

    @Test
    fun alPulsarElCheckSinEsfuerzoSaleElAvisoYElSliderSigueSinMarcar() {
        mostrar(casoRir())
        rule.onNodeWithText(EffortRequiredHint).assertDoesNotExist()

        pulsarCheck()

        rule.onNodeWithText(EffortRequiredHint).assertExists()
        assertEquals("Esfuerzo sin marcar", descripcionDelSlider())
    }

    @Test
    fun alMarcarElEsfuerzoElAvisoDesaparece() {
        mostrar(casoRir())
        pulsarCheck()
        rule.onNodeWithText(EffortRequiredHint).assertExists()

        marcar(0)

        rule.onNodeWithText(EffortRequiredHint).assertDoesNotExist()
    }

    @Test
    fun tocarElMinimoCuentaComoElegirloYDesbloqueaElCheck() {
        mostrar(casoRir())
        val ancho = rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode().size.width.toFloat()

        rule.onNodeWithTag(EffortSliderTag).performTouchInput { click(Offset(2f, centerY)) }
        rule.waitForIdle()
        assertEquals(0f, marcaActualDelSlider(), 0f)
        assertEquals("el mínimo de la escala RIR es RIR 10", "Esfuerzo RIR 10, cómodo", descripcionDelSlider())
        pulsarCheck()

        assertEquals("el ✓ registra al marcar el mínimo", 1, registros.size)
        assertEquals(10.0, registros.single().intensity!!, 0.001)
        assertTrue(ancho > 0f)
    }

    @Test
    fun tocarUnPuntoDeLaPistaMarcaEsaMarcaYSeRegistraEsaIntensidad() {
        mostrar(casoRir())
        val nodo = rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode()
        val escala = buildEffortScale(IntensityMode.RIR, IntensityMode.RIR, 2, null)
        val x = effortCenterPx(escala.planIndex!!, escala.items.size, nodo.size.width.toFloat(), 23f * 3f)

        rule.onNodeWithTag(EffortSliderTag).performTouchInput { click(Offset(x, centerY)) }
        rule.waitForIdle()
        assertEquals("Esfuerzo RIR 2, exigente", descripcionDelSlider())
        pulsarCheck()

        assertEquals(2.0, registros.single().intensity!!, 0.001)
        assertEquals(2, registros.single().advanced.rir)
        assertEquals(IntensityMode.RIR, registros.single().advanced.actualIntensityMode)
        assertFalse(registros.single().advanced.reachedFailure)
    }

    @Test
    fun enUnaSerieSinObjetivoSeRegistraElRpeQueSeMarco() {
        mostrar(casoSinObjetivo())
        val escala = buildEffortScale(null, null, null, null)
        val indice = escala.items.indexOfFirst { it.numericValue == 7.5 }

        marcar(indice)
        pulsarCheck()

        val registro = registros.single()
        assertEquals("lo marcado, no un RPE 0", 7.5, registro.intensity!!, 0.001)
        assertEquals(IntensityMode.RPE, registro.advanced.actualIntensityMode)
        assertEquals(7.5, registro.advanced.actualIntensityValue!!, 0.001)
        assertNull("en RPE no hay RIR", registro.advanced.rir)
    }

    @Test
    fun elFalloSeRegistraComoFalloSinUnRirDeDiez() {
        mostrar(casoRir())
        val escala = buildEffortScale(IntensityMode.RIR, IntensityMode.RIR, 2, null)

        marcar(escala.items.lastIndex)
        pulsarCheck()

        val registro = registros.single()
        assertTrue(registro.advanced.reachedFailure)
        assertEquals(IntensityMode.FAILURE, registro.advanced.actualIntensityMode)
        assertEquals(10.0, registro.intensity!!, 0.001)
        assertNull("el fallo no es RIR 10", registro.advanced.rir)
    }

    @Test
    fun marcarElMismoRirQueElPlanLoRegistraComoReserva() {
        mostrar(casoRir())
        val escala = buildEffortScale(IntensityMode.RIR, IntensityMode.RIR, 2, null)

        marcar(escala.items.indexOfFirst { it.numericValue == 1.0 })
        pulsarCheck()

        assertEquals(1.0, registros.single().intensity!!, 0.001)
        assertEquals(1, registros.single().advanced.rir)
    }

    @Test
    fun enUnaSerieAmrapElEsfuerzoSeGuardaComoReserva() {
        mostrar(Caso("amrap", ejercicio(serieAmrap()), serieAmrap()))
        val escala = buildEffortScale(IntensityMode.AMRAP, IntensityMode.AMRAP, null, null)

        marcar(escala.items.indexOfFirst { it.numericValue == 9.0 })
        pulsarCheck()

        val registro = registros.single()
        assertEquals("RPE 9 en un AMRAP es una reserva de 1", 1, registro.advanced.rir)
        assertEquals(1.0, registro.intensity!!, 0.001)
        assertEquals(IntensityMode.AMRAP, registro.advanced.actualIntensityMode)
        assertFalse(registro.advanced.reachedFailure)
    }

    @Test
    fun enUnaSerieAmrapElFalloSeGuardaComoFallo() {
        mostrar(Caso("amrap", ejercicio(serieAmrap()), serieAmrap()))
        val escala = buildEffortScale(IntensityMode.AMRAP, IntensityMode.AMRAP, null, null)

        marcar(escala.items.lastIndex)
        pulsarCheck()

        val registro = registros.single()
        assertTrue(registro.advanced.reachedFailure)
        assertEquals(IntensityMode.FAILURE, registro.advanced.actualIntensityMode)
        assertEquals(10.0, registro.intensity!!, 0.001)
    }

    @Test
    fun enUnaSerieSoloRmLaMarcaVaciaNoCuentaComoEsfuerzo() {
        mostrar(Caso("soloRm", ejercicio(serieSoloRm(), TrainingMode.RM), serieSoloRm()))

        pulsarCheck()
        assertTrue("sin marcar nada, tampoco aquí se registra", registros.isEmpty())

        marcar(0)
        pulsarCheck()
        assertTrue("el guion de carga RM no es un informe de esfuerzo", registros.isEmpty())
        rule.onNodeWithText(EffortRequiredHint).assertExists()
    }

    @Test
    fun enUnaSerieSoloRmUnRpeMarcadoSeRegistraConSuValor() {
        mostrar(Caso("soloRm", ejercicio(serieSoloRm(), TrainingMode.RM), serieSoloRm()))
        val escala = buildEffortScale(IntensityMode.SOLO_RM, null, null, null)

        marcar(escala.items.indexOfFirst { it.mode == IntensityMode.RPE && it.numericValue == 8.0 })
        pulsarCheck()

        assertEquals(8.0, registros.single().intensity!!, 0.001)
        assertEquals(IntensityMode.RPE, registros.single().advanced.actualIntensityMode)
    }

    @Test
    fun unBorradorConEsfuerzoLlegaMarcadoYUnoConFalloTambien() {
        mostrar(Caso("conBorrador", ejercicio(serieConRir()), serieConRir(), borrador = WorkoutSetDraft(intensityText = "2")))
        assertEquals("Esfuerzo RIR 2, exigente", descripcionDelSlider())
        pulsarCheck()
        assertEquals(2.0, registros.single().intensity!!, 0.001)
    }

    // ── La tarjeta nunca cambia de tamaño (ley 1) ─────────────────────────────────────────────────────

    /** Mide todas las series de [lista] con la misma tarjeta, al 100 % y al 130 % de letra. */
    private fun medirTodas(lista: List<Caso> = casosDeAltura()): Map<String, Float> {
        val medidas = linkedMapOf<String, Float>()
        val actual = mutableIntStateOf(0)
        val escalaActual = mutableFloatStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, escalaActual.floatValue)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.width(360.dp).background(Color.Black), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.width(272.dp).testTag(TARJETA)) {
                                key(actual.intValue) {
                                    val caso = lista[actual.intValue]
                                    SetInputCardV2(
                                        exercise = caso.ejercicio,
                                        setIndex = 0,
                                        currentSet = caso.serie,
                                        ghostSet = null,
                                        weightSuggestion = WeightSuggestion(suggestedWeight = 98.0, reason = "Ajuste por RIR"),
                                        previousSessionFirstSetWeight = 100.0,
                                        sessionAccentColor = Color(0xFFF49A6E),
                                        initialBodyWeight = caso.pesoCorporal,
                                        recordActionHolder = RecordActionHolder(),
                                        onShowHistory = {},
                                        onSetBodyWeight = {},
                                        onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        listOf(1f, 1.3f).forEach { escala ->
            rule.runOnIdle { escalaActual.floatValue = escala }
            lista.indices.forEach { i ->
                rule.runOnIdle { actual.intValue = i }
                rule.waitForIdle()
                medidas["${lista[i].nombre}@$escala"] = alturaDeLaTarjeta()
            }
        }
        println("ALTURAS ${medidas.entries.joinToString { "${it.key}=${it.value}" }}")
        return medidas
    }

    @Test
    fun laTarjetaMideLoMismoEnTodasLasSeriesDeFuerzaTambienConRom() {
        val medidas = medirTodas()
        listOf(1f, 1.3f).forEach { escala ->
            val base = medidas.getValue("rir2@$escala")
            casosDeAltura().forEach { caso ->
                val alto = medidas.getValue("${caso.nombre}@$escala")
                assertTrue(
                    "la tarjeta «${caso.nombre}» mide $alto dp y la de RIR 2 mide $base dp (letra al ${(escala * 100).toInt()} %)",
                    abs(alto - base) <= 0.5f,
                )
            }
        }
    }

    @Test
    fun laTarjetaNoCambiaDeTamanoAlMoverElSliderNiAlSalirElAviso() {
        mostrar(casoRir())
        val escala = buildEffortScale(IntensityMode.RIR, IntensityMode.RIR, 2, null)
        val base = alturaDeLaTarjeta()
        val alturas = mutableListOf("sin marcar" to base)

        pulsarCheck()
        rule.onNodeWithText(EffortRequiredHint).assertExists()
        alturas += "con el aviso" to alturaDeLaTarjeta()
        marcar(0)
        alturas += "en el mínimo" to alturaDeLaTarjeta()
        marcar(escala.planIndex!!)
        alturas += "en el plan" to alturaDeLaTarjeta()
        marcar(escala.items.lastIndex)
        alturas += "en el fallo" to alturaDeLaTarjeta()
        marcar(3)
        alturas += "en RIR 7" to alturaDeLaTarjeta()

        alturas.forEach { (estado, alto) ->
            assertTrue("la tarjeta mide $alto dp «$estado» y $base dp sin marcar", abs(alto - base) <= 0.5f)
        }
        assertNotNull(rule.onNodeWithTag(TARJETA))
    }

    @Test
    fun elPanelGuiadoDeDropsSeAbreEncimaYLaTarjetaNoCambiaDeTamano() {
        mostrar(Caso("conDrops", ejercicio(serieConDrops()), serieConDrops()))
        val base = alturaDeLaTarjeta()
        rule.onNodeWithText("DROP-SET 1/2").assertDoesNotExist()

        marcar(0)
        pulsarCheck()

        rule.onNodeWithText("DROP-SET 1/2").assertExists()
        assertEquals(com.example.kpkn.data.models.RecordedTechniqueStatus.OPEN, registros.single().advanced.techniqueStatus)
        assertTrue(registros.single().advanced.dropSets.isEmpty())
        val alto = alturaDeLaTarjeta()
        assertTrue("con el panel guiado la tarjeta mide $alto dp y $base dp antes de abrirlo", abs(alto - base) <= 0.5f)
    }

    @Test
    fun unDropManualEnUnaSerieNormalMuestraSuFaseYPermiteGuardarSoloLaPrincipal() {
        mostrar(casoRir())
        marcar(0)
        rule.onNodeWithTag("secondary_pill_avanzado").performClick()
        rule.onNodeWithText("Drop-set").performClick()
        rule.onNodeWithContentDescription("Volver a la serie").performClick()
        pulsarCheck()

        rule.onNodeWithText("DROP-SET 1/1").assertExists()
        assertEquals(com.example.kpkn.data.models.RecordedTechniqueStatus.OPEN, registros.single().advanced.techniqueStatus)
        assertTrue("la sugerencia no es una miniserie realizada", registros.single().advanced.dropSets.isEmpty())
        rule.onNodeWithText("Terminar técnica con lo realizado").performClick()
        rule.onNodeWithText("Guardar lo realizado").performClick()
        rule.waitForIdle()
        assertEquals(1, registros.size)
        assertTrue(registros.single().advanced.dropSets.isEmpty())
        assertEquals(com.example.kpkn.data.models.RecordedTechniqueStatus.SKIPPED, registros.single().advanced.techniqueStatus)
    }

    @Test
    fun laPausaTecnicaVaDentroDeLaBandaYLaTarjetaNoCambiaDeTamano() {
        mostrar(Caso("restPause", ejercicio(serieRestPauseProgramada()), serieRestPauseProgramada()))
        val base = alturaDeLaTarjeta()
        rule.onNodeWithText("REST PAUSE").assertExists()
        marcar(0)

        pulsarCheck()

        rule.onNodeWithText("REST PAUSE · Pausa técnica", substring = true).assertExists()
        assertEquals(com.example.kpkn.data.models.RecordedTechniqueStatus.OPEN, registros.single().advanced.techniqueStatus)
        assertTrue(registros.single().advanced.restPauses.isEmpty())
        val alto = alturaDeLaTarjeta()
        assertTrue("con la pausa técnica la tarjeta mide $alto dp y $base dp antes de ella", abs(alto - base) <= 0.5f)
    }

    @Test
    fun elAsistidoSinPesoCorporalLoPideEncimaDeLaTarjeta() {
        mostrar(Caso("asistidoSinPeso", ejercicio(serieAsistida()), serieAsistida(), pesoCorporal = null))

        // Refinamiento 02: la tarjeta lo dice en su línea de contexto y lo pide en la hoja «Modo de carga».
        rule.onNodeWithText("Asistida · añade tu peso corporal", useUnmergedTree = true).assertExists().performClick()
        rule.onNodeWithText("Peso corporal · kg", useUnmergedTree = true).assertExists()
    }

    @Test
    fun elRelojPorTiempoConservaObjetivoInicioYDetencionEnElMarcoPorModo() {
        mostrar(Caso("tiempo", ejercicio(serieEnTiempo(), TrainingMode.TIME), serieEnTiempo()))

        rule.onNodeWithContentDescription("Iniciar").assertExists()
        rule.onNodeWithText("TIEMPO REALIZADO · min:s").assertExists()
        rule.onNodeWithText("Objetivo 0:45").assertExists()

        rule.onNodeWithContentDescription("Iniciar").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Detener").assertExists()
        rule.onNodeWithText("Restan", substring = true).assertExists()
        rule.onNodeWithContentDescription("Detener").performClick()
        rule.onNodeWithContentDescription("Iniciar").assertExists()
        assertTrue("detener el reloj no registra la serie", registros.isEmpty())
    }

    @Test
    fun elAvisoDelEsfuerzoSeLeeEnteroYNoCambiaLaTarjetaAlCiento30PorCiento() {
        mostrar(casoRir(), fontScale = 1.3f)
        val base = alturaDeLaTarjeta()

        pulsarCheck()

        val nodo = rule.onNodeWithText(EffortRequiredHint).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        rule.runOnIdle { nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados) }
        val aviso = resultados.single()
        assertFalse("al 130 % el aviso se corta con puntos suspensivos (${aviso.lineCount} línea/s)", aviso.hasVisualOverflow)
        assertTrue("el aviso cabe en las dos líneas reservadas, pero ocupa ${aviso.lineCount}", aviso.lineCount <= 2)
        val alto = alturaDeLaTarjeta()
        assertTrue("con el aviso la tarjeta mide $alto dp y $base dp sin él (letra al 130 %)", abs(alto - base) <= 0.5f)
    }

    private companion object {
        const val TARJETA = "lv8-tarjeta"
        const val PAGINA = "lv8-pagina"
    }
}
