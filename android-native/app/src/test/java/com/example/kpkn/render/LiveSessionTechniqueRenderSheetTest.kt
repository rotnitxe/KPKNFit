package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.MuscleAdvance
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.VolumeDiscountProposal
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.domain.sessionassistant.UltraFastExerciseChange
import com.example.kpkn.domain.sessionassistant.UltraFastPreview
import com.example.kpkn.domain.sessionassistant.UltraFastReason
import com.example.kpkn.domain.sessionassistant.UltraFastSupersetChange
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.SeriesTypeTarget
import com.example.kpkn.screens.workout.WeightSuggestion
import com.example.kpkn.screens.workout.components.GodModeBatchBar
import com.example.kpkn.screens.workout.components.GodModePlusCard
import com.example.kpkn.screens.workout.components.GodModeRoadmapBadge
import com.example.kpkn.screens.workout.components.GodModeSetActionsOverlay
import com.example.kpkn.screens.workout.components.GodModeTechniqueScopeDialog
import com.example.kpkn.screens.workout.components.GuidedTechniquePanel
import com.example.kpkn.screens.workout.components.GuidedTechniquePhase
import com.example.kpkn.screens.workout.components.SeriesTypeSheet
import com.example.kpkn.screens.workout.components.SetAdjustmentOverlay
import com.example.kpkn.screens.workout.components.SetCardTechniqueBack
import com.example.kpkn.screens.workout.components.UltraFastPreviewSheet
import com.example.kpkn.screens.workout.components.VolumeAdvanceModal
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Etiqueta de la caja de cada escena: la hoja recorta por ella. */
private const val ESCENA_TAG = "lr1c2-escena"

// Datos de ejemplo: la serie de la captura del peso muerto (100,5 kg, 6 reps, RIR 2) y un reverso con una serie de 8 reps.
private val SERIE_REVERSO = ExerciseSet(id = "set-1", targetReps = 8, weight = 100.0)

private val SERIE_PM = ExerciseSet(
    id = "pm-1",
    targetReps = 6,
    targetRIR = 2,
    intensityMode = IntensityMode.RIR,
    weight = 100.5,
    loadModeV2 = LoadModeV2.LOAD,
    unitModeV2 = UnitModeV2.REPS,
)

private val EJERCICIO_PM = Exercise(
    id = "pm",
    name = "Peso Muerto Convencional",
    trainingMode = TrainingMode.REPS,
    sets = listOf(
        SERIE_PM,
        SERIE_PM.copy(id = "pm-2"),
        SERIE_PM.copy(id = "pm-3"),
        SERIE_PM.copy(id = "pm-4"),
    ),
)

private val PREPARACION = ExerciseReadiness(
    exerciseId = "pm",
    exerciseName = "Peso Muerto Convencional",
    overallScore = 62,
    muscularComponent = 70,
    cnsComponent = 55,
    spinalComponent = 48,
    articularComponent = 80,
    structuralComponent = 75,
    relatedArticular = emptyList(),
    muscularWeight = 0.5,
    cnsWeight = 0.3,
    spinalWeight = 0.2,
    articularWeight = 0.0,
    setsPenaltyFactor = 1.0,
    intensityPenaltyFactor = 1.0,
    ermProximityFactor = 1.0,
    patternId = null,
    involvedMuscleIds = emptyList(),
)

private val PREVISUALIZACION = UltraFastPreview(
    beforeSeconds = 2520,
    afterSeconds = 1740,
    savedSeconds = 780,
    perExercise = listOf(
        UltraFastExerciseChange(
            exerciseId = "sq",
            exerciseName = "Sentadilla trasera",
            beforeSets = 4,
            afterSets = 4,
            beforeTechnique = "Normal",
            afterTechnique = "Normal",
            reason = UltraFastReason.PROTECTED_BASIC,
            wasDensified = false,
            wasReduced = false,
        ),
        UltraFastExerciseChange(
            exerciseId = "pb",
            exerciseName = "Press banca",
            beforeSets = 4,
            afterSets = 3,
            beforeTechnique = "Normal",
            afterTechnique = "Normal",
            reason = UltraFastReason.COMPOUND_REDUCED,
            wasDensified = false,
            wasReduced = true,
        ),
        UltraFastExerciseChange(
            exerciseId = "el",
            exerciseName = "Elevaciones laterales",
            beforeSets = 3,
            afterSets = 3,
            beforeTechnique = "Normal",
            afterTechnique = "Dropset",
            reason = UltraFastReason.ISOLATION_DENSIFIED,
            wasDensified = true,
            wasReduced = false,
        ),
    ),
    supersets = listOf(
        UltraFastSupersetChange(
            exerciseIdA = "rp",
            exerciseIdB = "pm2",
            nameA = "Remo en polea",
            nameB = "Pecho en máquina",
            machineKey = "Polea",
        ),
    ),
)

private val ADELANTOS = listOf(
    MuscleAdvance(
        muscleId = "pecho",
        muscleName = "Pecho",
        currentSets = 14.0,
        targetSets = 12.0,
        deficitSets = 2.0,
        targetSessionId = "s2",
        targetSessionName = "Empuje B",
        discountProposals = listOf(
            VolumeDiscountProposal(
                exerciseId = "pb",
                exerciseName = "Press banca",
                currentRole = "Principal",
                discountSets = 2.0,
                reason = "Solapamiento con el press inclinado",
            ),
        ),
    ),
)

/**
 * HOJA DE RENDER de la técnica, el tipo de serie y las acciones de serie (LR1c2). No comprueba nada: escribe PNG. Solo corre con la
 * variable `KPKN_RENDER_DIR` apuntando a una carpeta; sin ella se salta. Dibuja las piezas REALES de la sesión con datos de ejemplo,
 * a 360 dp, con la letra al 100 % y al 130 %. El reverso se llena con dos «Añadir» y el peso de la 3.ª fila se escribe como «62,5».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiveSessionTechniqueRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (igual que LiveSessionRefineRenderSheetTest). */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    // ── Reverso de técnica (C1) ──

    @Test
    fun reversoTresDrops100() {
        onlyWhenAsked()
        dibujar("reverso_tres_drops_100", 1f, 540, accion = { rellenarTresDrops() }) { ReversoDeTecnica(fallida = false) }
    }

    @Test
    fun reversoTresDrops130() {
        onlyWhenAsked()
        dibujar("reverso_tres_drops_130", 1.3f, 540, accion = { rellenarTresDrops() }) { ReversoDeTecnica(fallida = false) }
    }

    @Test
    fun reversoFallida100() {
        onlyWhenAsked()
        dibujar("reverso_fallida_100", 1f, 540, accion = { rellenarTresDrops() }) { ReversoDeTecnica(fallida = true) }
    }

    // ── Técnica guiada (C2) ──

    @Test
    fun tecnicaGuiada100() {
        onlyWhenAsked()
        dibujar("tecnica_guiada_100", 1f, 340) {
            PanelGuiado(GuidedTechniquePhase.DropSet(index = 0, total = 2, suggestedWeight = 62.5))
        }
    }

    @Test
    fun tecnicaGuiada130() {
        onlyWhenAsked()
        dibujar("tecnica_guiada_130", 1.3f, 400) {
            PanelGuiado(GuidedTechniquePhase.DropSet(index = 0, total = 2, suggestedWeight = 62.5))
        }
    }

    @Test
    fun tecnicaGuiadaDialogo100() {
        onlyWhenAsked()
        dibujar(
            "tecnica_guiada_dialogo_100",
            1f,
            420,
            accion = {
                rule.onNodeWithText("Saltar técnica y registrar solo la serie").performClick()
                rule.waitForIdle()
            },
        ) {
            PanelGuiado(GuidedTechniquePhase.DropSet(index = 0, total = 2, suggestedWeight = 62.5))
        }
    }

    @Test
    fun tecnicaGuiadaDescanso100() {
        onlyWhenAsked()
        dibujar("tecnica_guiada_descanso_100", 1f, 260) {
            PanelGuiado(GuidedTechniquePhase.RestPauseCountdown(index = 0, total = 2, secondsLeft = 9))
        }
    }

    // ── Ajuste por estado (C4) ──

    @Test
    fun ajusteDeEstado100() {
        onlyWhenAsked()
        dibujar("ajuste_estado_100", 1f, 760) { AjusteDeEstado() }
    }

    @Test
    fun ajusteDeEstado130() {
        onlyWhenAsked()
        dibujar("ajuste_estado_130", 1.3f, 900) { AjusteDeEstado() }
    }

    // ── Acciones de serie y alcance (C5, B9) ──

    @Test
    fun accionesDeSerie100() {
        onlyWhenAsked()
        dibujar("acciones_serie_100", 1f, 240) { AccionesDeSerie() }
    }

    @Test
    fun accionesDeSerie130() {
        onlyWhenAsked()
        dibujar("acciones_serie_130", 1.3f, 300) { AccionesDeSerie() }
    }

    @Test
    fun alcanceDeTecnica100() {
        onlyWhenAsked()
        dibujar("alcance_tecnica_100", 1f, 340) {
            GodModeTechniqueScopeDialog(technique = SeriesTechnique.DROPSET, onPick = {}, onDismiss = {})
        }
    }

    @Test
    fun alcanceDeTecnica130() {
        onlyWhenAsked()
        dibujar("alcance_tecnica_130", 1.3f, 400) {
            GodModeTechniqueScopeDialog(technique = SeriesTechnique.DROPSET, onPick = {}, onDismiss = {})
        }
    }

    @Test
    fun tiraGodMode100() {
        onlyWhenAsked()
        dibujar("tira_godmode_100", 1f, 170) { TiraGodMode() }
    }

    // ── Tipo de serie (B10), ultrarrápido (B11) y adelanto de volumen (B12) ──

    @Test
    fun tipoDeSerie100() {
        onlyWhenAsked()
        dibujar("tipo_serie_100", 1f, 820) { TipoDeSerie() }
    }

    @Test
    fun tipoDeSerie130() {
        onlyWhenAsked()
        dibujar("tipo_serie_130", 1.3f, 960) { TipoDeSerie() }
    }

    @Test
    fun ultrarapido100() {
        onlyWhenAsked()
        dibujar("ultrarapido_100", 1f, 900) { Ultrarapido() }
    }

    @Test
    fun ultrarapido130() {
        onlyWhenAsked()
        dibujar("ultrarapido_130", 1.3f, 1000) { Ultrarapido() }
    }

    @Test
    fun adelantoVolumen100() {
        onlyWhenAsked()
        dibujar("adelanto_volumen_100", 1f, 760) { VolumeAdvanceModal(advances = ADELANTOS, onAccept = {}, onDismiss = {}) }
    }

    @Test
    fun adelantoVolumen130() {
        onlyWhenAsked()
        dibujar("adelanto_volumen_130", 1.3f, 880) { VolumeAdvanceModal(advances = ADELANTOS, onAccept = {}, onDismiss = {}) }
    }

    // ── Piezas compuestas ──

    @Composable
    private fun ReversoDeTecnica(fallida: Boolean) {
        Box(Modifier.width(272.dp).height(520.dp)) {
            SetCardTechniqueBack(
                currentSet = SERIE_REVERSO,
                coverWeightLabel = "100 kg",
                coverValueLabel = "8",
                coverIntensityLabel = null,
                mainWeight = 100.0,
                mainReps = 8,
                sessionAccentColor = SymbolPalette.musculo,
                isFailedSet = fallida,
                onFailedSetChange = {},
                onFlipBack = {},
                onCompleteTechniques = { _, _ -> },
                noteText = "",
                onNoteChange = {},
                initialDropEnabled = true,
            )
        }
    }

    @Composable
    private fun PanelGuiado(fase: GuidedTechniquePhase) {
        Box(Modifier.width(336.dp).padding(12.dp)) {
            GuidedTechniquePanel(
                phase = fase,
                accentColor = SymbolPalette.musculo,
                dropWeightText = "62,5",
                dropRepsText = "3",
                restPauseRepsText = "3",
                onDropWeightChange = {},
                onDropRepsChange = {},
                onRestPauseRepsChange = {},
                onSkipTechnique = {},
            )
        }
    }

    @Composable
    private fun AjusteDeEstado() {
        SetAdjustmentOverlay(
            exercise = EJERCICIO_PM,
            currentSet = SERIE_PM,
            setIndex = 0,
            exerciseReadiness = PREPARACION,
            weightSuggestion = WeightSuggestion(suggestedWeight = 98.0, reason = "Ajuste por RIR"),
            averageErm = 118.0,
            bodyWeight = 80.0,
            loadMode = LoadModeV2.LOAD,
            onDismiss = {},
            onApply = {},
        )
    }

    @Composable
    private fun AccionesDeSerie() {
        Box(Modifier.width(360.dp)) {
            GodModeSetActionsOverlay(
                visible = true,
                isDropSet = true,
                isRestPause = false,
                canDeleteSet = true,
                onDropSet = {},
                onRestPause = {},
                onDeleteSet = {},
                onOmitSet = {},
            )
        }
    }

    @Composable
    private fun TiraGodMode() {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GodModeBatchBar(
                selectedCount = 2,
                onCreateSuperset = {},
                onSkip = {},
                onDelete = {},
                onUltraFast = {},
                onClear = {},
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GodModePlusCard(onClick = {}, accent = SymbolPalette.musculo)
                GodModeRoadmapBadge(
                    icon = KpknLineIcons.Close,
                    contentDescription = "Eliminar ejercicio",
                    onClick = {},
                    danger = true,
                )
                GodModeRoadmapBadge(
                    icon = KpknLineIcons.Close,
                    contentDescription = "Eliminar ejercicio",
                    onClick = {},
                )
            }
        }
    }

    @Composable
    private fun TipoDeSerie() {
        SeriesTypeSheet(
            exercise = EJERCICIO_PM,
            target = SeriesTypeTarget(
                exerciseId = "pm",
                fromSetIdx = 1,
                toSetIdx = 1,
                selectedSetIndices = setOf(1),
            ),
            completedSetIndices = setOf(0),
            onDismiss = {},
            onApply = { _, _ -> },
        )
    }

    @Composable
    private fun Ultrarapido() {
        UltraFastPreviewSheet(
            preview = PREVISUALIZACION,
            savedSeconds = 780,
            visibleExercises = emptyList(),
            ultraFastManualOverrides = emptyMap(),
            onToggleOverride = {},
            onConfirm = {},
            onDismiss = {},
        )
    }

    // ── Escena y recorte ──

    /** Monta [contenido] en una caja de 360 dp × [alturaDp], ejecuta [accion] (si la hay) y guarda la caja como PNG. */
    private fun dibujar(
        nombre: String,
        escala: Float,
        alturaDp: Int,
        accion: (() -> Unit)? = null,
        contenido: @Composable () -> Unit,
    ) {
        rule.setContent {
            hostView = LocalView.current
            // 3 px por dp: pantalla de 360 dp. La letra se escala aparte (100 % o 130 %).
            CompositionLocalProvider(LocalDensity provides Density(3f, escala)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(alturaDp.dp)
                                .background(Color.Black)
                                .testTag(ESCENA_TAG),
                        ) {
                            contenido()
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        accion?.invoke()
        rule.waitForIdle()
        guardar(nombre)
    }

    /** Dos «Añadir» sobre el drop inicial (tres filas) y el peso de la 3.ª fila, que es el 5.º campo editable, pasa a «102,5» (el caso más ancho). */
    private fun rellenarTresDrops() {
        rule.onNodeWithText("Añadir").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Añadir").performClick()
        rule.waitForIdle()
        // Campos editables en orden de composición: peso y reps de cada fila; la nota va al final.
        rule.onAllNodes(hasSetTextAction())[4].performTextReplacement("102,5")
        rule.waitForIdle()
    }

    /** Recorta la escena por su etiqueta y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun guardar(nombre: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(ESCENA_TAG).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val completa = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = android.graphics.Canvas(completa)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val izquierda = bounds.left.toInt().coerceIn(0, completa.width - 1)
        val arriba = bounds.top.toInt().coerceIn(0, completa.height - 1)
        val ancho = bounds.width.toInt().coerceIn(1, completa.width - izquierda)
        val alto = bounds.height.toInt().coerceIn(1, completa.height - arriba)
        val recorte = Bitmap.createBitmap(completa, izquierda, arriba, ancho, alto)
        FileOutputStream(File(dir, "$nombre.png")).use { recorte.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
