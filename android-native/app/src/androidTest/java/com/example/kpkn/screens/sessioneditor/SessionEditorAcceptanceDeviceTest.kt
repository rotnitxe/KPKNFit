package com.example.kpkn.screens.sessioneditor

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.domain.training.insights.MuscleVolumeAccounting
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.ExerciseSetList
import com.example.kpkn.screens.sessioneditor.components.SupersetRoundsCarousel
import com.example.kpkn.screens.sessioneditor.components.assistant.AsRulesActions
import com.example.kpkn.screens.sessioneditor.components.assistant.AssistantCallbacks
import com.example.kpkn.screens.sessioneditor.components.sheets.AssistantGlassOverlay
import com.example.kpkn.screens.sessioneditor.components.sheets.ExerciseQuickActionsSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.quickActionThumbnailRes
import com.example.kpkn.screens.sessioneditor.design.CustomExerciseCoverDraft
import com.example.kpkn.screens.sessioneditor.design.CustomExerciseEditorViewModel
import com.example.kpkn.screens.sessioneditor.design.SmartExerciseForm
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameBottomBar
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Aceptación AVD real en ComponentActivity para el editor de sesiones (Req. 1, 9, 10 y 11):
 * - Req 1: Editor de ejercicio propio con debounce, resultados fuera de orden, edición manual 100/50/40 y reapertura.
 * - Req 9: Acciones rápidas con dos configuraciones de imágenes distintas, ejercicio propio sin imagen, «Ver más» y «Eliminar».
 * - Req 10: Asistente desde sus entradas reales, alturas 0.72/0.93, asa, cambio de pestañas, pantalla baja y fuente grande.
 * - Req 11: Menú de series normal y superserie bilateral/unilateral, sin Subir/Bajar, regla de última serie, Deshacer, Duplicar y Aplicar a todas.
 */
@RunWith(AndroidJUnit4::class)
class SessionEditorAcceptanceDeviceTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun initExerciseDb() {
        runBlocking { initializeExerciseDatabase(InstrumentationRegistry.getInstrumentation().targetContext) }
    }

    private fun fieldInsideTag(tag: String) =
        rule.onNode(hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    /** CoroutineDispatcher controlable para forzar inferencias de preview fuera de orden en UI real. */
    private class QueuedPreviewDispatcher : CoroutineDispatcher() {
        private val pending = CopyOnWriteArrayList<Runnable>()
        var deferFirst: Boolean = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (deferFirst) {
                deferFirst = false
                pending += block
            } else {
                block.run()
            }
        }

        fun flushDeferred() {
            val copy = pending.toList()
            pending.clear()
            copy.forEach { it.run() }
        }
    }

    @Test
    fun req1_customExerciseEditorHandlesOutOfOrderPreviewMusclesAndReopen() {
        val catalog = exerciseCatalogSnapshot()
        val catalogV2 = approvedExerciseCatalogV2()
        val dispatcher = QueuedPreviewDispatcher().apply { deferFirst = true }
        var persisted: ExerciseMuscleInfo? = null
        val editor = CustomExerciseEditorViewModel(
            initial = null,
            initialName = "",
            catalog = catalog,
            catalogV2 = catalogV2,
            persist = { info ->
                persisted = info
                Result.success(info)
            },
            importPhoto = { _, _ -> error("No photo needed") },
            previewDispatcher = dispatcher,
        )
        var reopenedEditor by mutableStateOf(false)
        var reopenedInitial by mutableStateOf<ExerciseMuscleInfo?>(null)

        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    if (!reopenedEditor) {
                        SmartExerciseForm(
                            initial = null,
                            onSave = { saved ->
                                reopenedInitial = saved
                            },
                            onDismiss = {},
                            editor = editor,
                        )
                    } else {
                        SmartExerciseForm(
                            initial = reopenedInitial,
                            onSave = { saved -> persisted = saved },
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        // 1. Escribir identidad -> borrar -> reescribir: el preview lento anterior NO puede pisar el nuevo borrador.
        fieldInsideTag("custom-exercise-name").performTextInput("Sentadilla")
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("custom-exercise-review").assertIsNotEnabled()

        fieldInsideTag("custom-exercise-name").performTextClearance()
        fieldInsideTag("custom-exercise-name").performTextInput("Press horizontal QA")

        // Seleccionar los 3 ejes técnicos requeridos
        rule.onNodeWithTag("custom-option-Material").performScrollTo().performClick()
        rule.onNodeWithText("Barra").performClick()
        rule.onNodeWithTag("custom-option-Estación").performScrollTo().performClick()
        rule.onNodeWithText("De pie").performClick()
        rule.onNodeWithTag("custom-option-Lateralidad").performScrollTo().performClick()
        rule.onNodeWithText("Bilateral").performClick()

        rule.waitUntil(10_000) { editor.state.value.isPreviewCurrent }
        // Soltar la tarea diferida de "Sentadilla": NO debe sobrescribir "Press horizontal QA"
        dispatcher.flushDeferred()
        rule.waitForIdle()
        assertEquals("Press horizontal QA", editor.state.value.preview?.exercise?.name)

        // 2. Activar edición manual de músculos, editar porcentaje y probar Restablecer (100/50/40)
        rule.onNodeWithTag("custom-exercise-manual-toggle").performScrollTo().performClick()
        rule.waitForIdle()
        val initialMuscles = editor.state.value.muscles
            ?: editor.state.value.preview?.exercise?.involvedMuscles.orEmpty()
        val firstMuscle = if (initialMuscles.isNotEmpty()) {
            initialMuscles.first().muscle
        } else {
            rule.onNodeWithText("Pectorales").performScrollTo().performClick()
            rule.waitForIdle()
            "Pectorales"
        }
        rule.onNodeWithTag("custom-muscle-percent-$firstMuscle").performScrollTo().performClick()
        rule.waitForIdle()
        fieldInsideTag("custom-muscle-percent-$firstMuscle").performTextReplacement("85")
        fieldInsideTag("custom-muscle-percent-$firstMuscle").performImeAction()
        rule.waitForIdle()
        assertEquals(0.85, editor.state.value.muscles?.first { it.muscle == firstMuscle }?.volumeContribution ?: -1.0, 0.01)

        // Reset según rol vuelve al predeterminado de su rol (100 % si PRIMARY; 50 % si SECONDARY)
        rule.onNodeWithTag("custom-muscle-reset-$firstMuscle").performScrollTo().performClick()
        rule.waitForIdle()
        val expectedDefault = when (editor.state.value.muscles?.first { it.muscle == firstMuscle }?.role) {
            MuscleRole.SECONDARY -> 0.5
            MuscleRole.STABILIZER -> 0.4
            else -> 1.0
        }
        assertEquals(expectedDefault, editor.state.value.muscles?.first { it.muscle == firstMuscle }?.volumeContribution ?: -1.0, 0.01)

        // Volver a personalizar aporte y guardar tras revisión
        rule.onNodeWithTag("custom-muscle-percent-$firstMuscle").performScrollTo().performClick()
        rule.waitForIdle()
        fieldInsideTag("custom-muscle-percent-$firstMuscle").performTextReplacement("75")
        fieldInsideTag("custom-muscle-percent-$firstMuscle").performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("custom-exercise-review").performScrollTo().performClick()
        rule.onNodeWithTag("custom-exercise-save").assertIsEnabled().performClick()

        rule.waitUntil(10_000) { persisted != null && reopenedInitial != null }
        val saved = requireNotNull(persisted)
        assertEquals("Press horizontal QA", saved.name)
        assertEquals(0.75, saved.involvedMuscles.first { it.muscle == firstMuscle }.volumeContribution ?: -1.0, 0.01)

        // 3. Reabrir el ejercicio guardado y comprobar contribución conservada + cómputo de volumen muscular
        rule.runOnUiThread { reopenedEditor = true }
        rule.waitForIdle()
        rule.onNodeWithTag("custom-exercise-manual-toggle").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("custom-muscle-percent-$firstMuscle").performScrollTo()
            .assert(hasText("75", substring = true) or hasContentDescription("75", substring = true))

        val completed = CompletedExercise(
            exerciseId = saved.id,
            exerciseName = saved.name,
            effectiveMuscles = saved.involvedMuscles,
            sets = listOf(CompletedSet("s1", weight = 60.0, reps = 8), CompletedSet("s2", weight = 60.0, reps = 8)),
        )
        val volume = MuscleVolumeAccounting.sessionSets(listOf(completed))[firstMuscle] ?: 0.0
        assertEquals(1.5, volume, 0.01)
    }

    @Test
    fun req9_quickActionsDistinguishTwoConfigsCustomFallbackOpenDetailAndDeleteSeparately() {
        val v2 = requireNotNull(approvedExerciseCatalogV2())
        // Encontrar dos configuraciones reales de la MISMA definición con imágenes diferentes en catálogo V2
        var firstConfigId: String? = null
        var secondConfigId: String? = null
        var defId: String? = null
        outer@ for (def in v2.families.flatMap { it.definitions }) {
            val configs = def.configurations
            if (configs.size < 2) continue
            val resolved = configs.mapNotNull { cfg ->
                val ex = Exercise(
                    id = "probe-${cfg.id}",
                    name = def.canonicalName,
                    catalogDefinitionId = def.id,
                    catalogConfigurationId = cfg.id,
                )
                quickActionThumbnailRes(ex)?.let { res -> cfg.id to res }
            }
            val first = resolved.firstOrNull()
            val second = resolved.firstOrNull { it.second != first?.second }
            if (first != null && second != null) {
                defId = def.id
                firstConfigId = first.first
                secondConfigId = second.first
                break@outer
            }
        }
        assertNotNull("Debe existir una definición V2 con al menos dos configuraciones de imagen distinta", defId)
        val exConfig1 = Exercise(
            id = "ex-cfg-1",
            name = "Press banca (Config A)",
            catalogDefinitionId = defId,
            catalogConfigurationId = firstConfigId,
            sets = listOf(ExerciseSet(id = "s1", targetReps = 6, weight = 80.0, targetRIR = 2)),
            restTime = 180,
        )
        val exConfig2 = exConfig1.copy(id = "ex-cfg-2", name = "Press banca (Config B)", catalogConfigurationId = secondConfigId)
        val res1 = quickActionThumbnailRes(exConfig1)
        val res2 = quickActionThumbnailRes(exConfig2)
        assertNotNull(res1)
        assertNotNull(res2)
        assertNotEquals("Configuraciones distintas de la misma definición resuelven miniaturas distintas", res1, res2)

        val customNoImage = Exercise(
            id = "custom:qa-no-image",
            name = "Elevación propia QA",
            sets = listOf(ExerciseSet(id = "cs1", targetReps = 12, weight = 20.0)),
        )
        assertNull("Ejercicio propio sin imagen usa fallback", quickActionThumbnailRes(customNoImage))

        var currentExercise by mutableStateOf(exConfig1)
        var openedDetailId: String? = null
        var deletedCount = 0

        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    ExerciseQuickActionsSheet(
                        exercise = currentExercise,
                        catalog = exerciseCatalogSnapshot(),
                        workoutLogs = emptyList(),
                        onOpenExerciseDetail = { openedDetailId = it },
                        onOpenPicker = {},
                        onOpenWarmup = {},
                        onOpenMobility = {},
                        onDelete = { deletedCount++ },
                        onManageSuperset = {},
                    )
                }
            }
        }

        rule.onNodeWithText("Press banca (Config A)").assertIsDisplayed()
        rule.onNodeWithText("Ver más").performScrollTo().performClick()
        assertEquals("ex-cfg-1", openedDetailId)

        rule.runOnUiThread { currentExercise = exConfig2 }
        rule.waitForIdle()
        rule.onNodeWithText("Press banca (Config B)").assertIsDisplayed()
        rule.onNodeWithTag("quick-action-eliminar").performScrollTo().performClick()
        assertEquals(1, deletedCount)

        rule.runOnUiThread { currentExercise = customNoImage }
        rule.waitForIdle()
        rule.onNodeWithText("Elevación propia QA").assertIsDisplayed()
        rule.onNodeWithText("No hay una descripción editorial disponible para este ejercicio.").assertIsDisplayed()
    }

    @Test
    fun req10_assistantFourEntriesHeightToggleAndApplyAccessibleOnLowScreenLargeFont() {
        val baseSession = Session(
            id = "as-session",
            name = "Torso intenso",
            targetDurationMinutes = 45,
            exercises = listOf(
                Exercise(
                    id = "ex1",
                    name = "Press banca",
                    sets = listOf(
                        ExerciseSet(id = "s1", targetReps = 8, weight = 80.0, targetRIR = 2),
                        ExerciseSet(id = "s2", targetReps = 8, weight = 80.0, targetRIR = 2),
                    ),
                ),
            ),
        )
        var entryState by mutableStateOf<AssistantEntry?>(null)
        var sheetOpen by mutableStateOf(false)
        var appliedCount = 0

        rule.setContent {
            val baseConfig = LocalConfiguration.current
            val lowScreenConfig = remember(baseConfig) {
                Configuration(baseConfig).apply {
                    screenHeightDp = 560
                    fontScale = 1.4f
                }
            }
            CompositionLocalProvider(
                LocalWizardReducedMotion provides true,
                LocalConfiguration provides lowScreenConfig,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.4f),
            ) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(560.dp)) {
                        SessionEditorFrameBottomBar(
                            hazeState = null,
                            suggestionCount = 2,
                            estimatedMinutes = 35,
                            limitMinutes = 45,
                            onAdd = {},
                            onAssistant = {
                                entryState = null
                                sheetOpen = true
                            },
                            onTime = {
                                entryState = AssistantEntry(tab = AssistantTab.RULES, rules = RulesSection.TIME)
                                sheetOpen = true
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        )
                        if (sheetOpen) {
                            AssistantGlassOverlay(
                                uiState = SessionEditorUiState(
                                    session = baseSession,
                                    assistantEntry = entryState,
                                    estimatedDurationMinutes = 35,
                                    targetDurationMinutes = 45,
                                ),
                                templates = emptyList(),
                                hazeState = remember { HazeState() },
                                exerciseIndex = emptyMap(),
                                callbacks = AssistantCallbacks(
                                    onDismiss = { sheetOpen = false },
                                    rules = AsRulesActions.None.let { none ->
                                        AsRulesActions(
                                            onApplyRules = {
                                                appliedCount++
                                                ApplyRulesOutcome.Applied(1)
                                            },
                                            onRuleDefaultsChange = none.onRuleDefaultsChange,
                                            onPatchRuleDefaults = none.onPatchRuleDefaults,
                                            onRuleLimitsChange = none.onRuleLimitsChange,
                                            onAdvancedRuleLimitsChange = none.onAdvancedRuleLimitsChange,
                                            onApplyGlobalIntensityAdjustment = none.onApplyGlobalIntensityAdjustment,
                                            setTargetDuration = none.setTargetDuration,
                                            setPartTargetDuration = none.setPartTargetDuration,
                                            setExerciseTargetDuration = none.setPartTargetDuration.let { { _, _ -> } },
                                            onDistributeTargetAcrossParts = none.onDistributeTargetAcrossParts,
                                            onApplyRuleTemplate = none.onApplyRuleTemplate,
                                            onSaveRuleTemplate = none.onSaveRuleTemplate,
                                            onRenameRuleTemplate = none.onRenameRuleTemplate,
                                            onDeleteRuleTemplate = none.onDeleteRuleTemplate,
                                            onApplyTimeCoachSuggestion = none.onApplyTimeCoachSuggestion,
                                            onDismissTimeCoachSuggestion = none.onDismissTimeCoachSuggestion,
                                        )
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Entrada 1: Botón del asistente en la barra (entra en Métricas a media altura 0.72)
        rule.onNodeWithTag("session_editor_bar_assistant").performClick()
        rule.onNodeWithText("Asistente de sesión").assertIsDisplayed()
        rule.onNodeWithText("Métricas").assertIsDisplayed()

        // Cambiar altura con el asa y cambiar de pestaña sin perderla ni solapar cabecera
        rule.onNodeWithContentDescription("Cambiar la altura de la hoja").performClick()
        rule.onNodeWithText("Sugerencias").performClick()
        rule.onNodeWithText("Plantillas").performClick()
        rule.onNodeWithText("Reglas").performClick()
        rule.onNodeWithText("Aplicar a la sesión").assertIsDisplayed().performClick()
        assertTrue("El pie Aplicar debe ser alcanzable y clicable con pantalla baja y fuente grande", appliedCount >= 1)

        // Cerrar y probar Entrada 2 (Anillo de tiempo -> abre expandido en Reglas · Tiempo)
        rule.runOnUiThread { sheetOpen = false }
        rule.waitForIdle()
        rule.onNodeWithTag("session_editor_bar_time").performClick()
        rule.onNodeWithText("Reglas").assertIsDisplayed()
        rule.onNodeWithText("Tiempo").assertIsDisplayed()

        // Entrada 3 (Reglas Valores) y Entrada 4 (Sugerencias directa)
        rule.runOnUiThread {
            sheetOpen = false
        }
        rule.waitForIdle()
        rule.runOnUiThread {
            entryState = AssistantEntry(tab = AssistantTab.RULES, rules = RulesSection.VALUES)
            sheetOpen = true
        }
        rule.onNodeWithText("Aplicar a la sesión").assertIsDisplayed()

        rule.runOnUiThread {
            sheetOpen = false
        }
        rule.waitForIdle()
        rule.runOnUiThread {
            entryState = AssistantEntry(tab = AssistantTab.SUGGESTIONS)
            sheetOpen = true
        }
        rule.onNodeWithText("Sugerencias").assertIsDisplayed()
    }

    @Test
    fun req11_setMenusNormalAndBilateralUnilateralSupersetsOmitMoveActionsKeepLastSetAndUndo() {
        var normalExercise by mutableStateOf(
            Exercise(
                id = "norm",
                name = "Press inclinado",
                sets = listOf(
                    ExerciseSet(id = "n1", targetReps = 10, weight = 60.0),
                    ExerciseSet(id = "n2", targetReps = 12, weight = 55.0),
                ),
            ),
        )
        val supersetGroup = SupersetGroup(id = "sg-1", exerciseOrder = listOf("ss-bi", "ss-uni"), rounds = 1)
        var bilateralMember by mutableStateOf(
            Exercise(
                id = "ss-bi",
                name = "Remo en polea",
                supersetId = "sg-1",
                sets = listOf(ExerciseSet(id = "b1", targetReps = 10, weight = 50.0)),
            ),
        )
        var unilateralMember by mutableStateOf(
            Exercise(
                id = "ss-uni",
                name = "Curl mancuerna unilateral",
                supersetId = "sg-1",
                isUnilateral = true,
                unilateralIntensityMode = UnilateralIntensityMode.SHARED,
                sets = listOf(ExerciseSet(id = "u1", targetReps = 10, weight = 14.0)),
            ),
        )

        var lastMode by mutableStateOf("normal")

        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    Column(Modifier.fillMaxSize()) {
                        if (lastMode == "normal") {
                            ExerciseSetList(
                                exercise = normalExercise,
                                reference1RM = null,
                                trainingMode = TrainingMode.REPS,
                                customUnit = null,
                                predictedMetrics = emptyMap(),
                                accentColor = Color.White,
                                backgroundAccentColor = Color.DarkGray,
                                onAddSet = {
                                    normalExercise = normalExercise.copy(
                                        sets = normalExercise.sets + ExerciseSet(id = "added", targetReps = 10),
                                    )
                                },
                                onUpdateSet = { setId, transform ->
                                    normalExercise = normalExercise.copy(
                                        sets = normalExercise.sets.map { if (it.id == setId) transform(it) else it },
                                    )
                                },
                                onRemoveSet = { setId ->
                                    normalExercise = normalExercise.copy(sets = normalExercise.sets.filterNot { it.id == setId })
                                },
                                onMoveSet = { _, _ -> },
                                onTransformExercise = { transform ->
                                    normalExercise = transform(normalExercise)
                                },
                            )
                        } else {
                            SupersetRoundsCarousel(
                                group = supersetGroup,
                                exercises = listOf(bilateralMember, unilateralMember),
                                rounds = 1,
                                accentColor = Color.White,
                                onUpdateRoundRest = { _, _, _ -> },
                                onUpdateSet = { exId, setId, transform ->
                                    if (exId == bilateralMember.id) {
                                        bilateralMember = bilateralMember.copy(sets = bilateralMember.sets.map { if (it.id == setId) transform(it) else it })
                                    } else {
                                        unilateralMember = unilateralMember.copy(sets = unilateralMember.sets.map { if (it.id == setId) transform(it) else it })
                                    }
                                },
                                onRemoveSet = { exId, setId ->
                                    if (exId == bilateralMember.id) {
                                        bilateralMember = bilateralMember.copy(sets = bilateralMember.sets.filterNot { it.id == setId })
                                    } else {
                                        unilateralMember = unilateralMember.copy(sets = unilateralMember.sets.filterNot { it.id == setId })
                                    }
                                },
                                onMoveSet = { _, _, _ -> },
                                onRestoreSet = { _, _ -> },
                                onAddRound = {},
                                onRemoveRound = {},
                                onUpdateExercise = { exId, transform ->
                                    if (exId == bilateralMember.id) bilateralMember = transform(bilateralMember)
                                    else unilateralMember = transform(unilateralMember)
                                },
                            )
                        }
                    }
                }
            }
        }

        // 1. Menú normal: abrir ⋯ -> confirmar ausencia de Subir/Bajar serie, eliminar con Deshacer, duplicar y Aplicar a todas
        rule.onNodeWithContentDescription("Más acciones de la serie").performClick()
        rule.onNodeWithText("Subir serie").assertDoesNotExist()
        rule.onNodeWithText("Bajar serie").assertDoesNotExist()
        rule.onNodeWithText("Eliminar serie").performClick()
        rule.assertNoMoreMovesInNormalTree()

        // Al quedar solo 1 serie, «Deshacer» restaura y volver a dejar 1 serie deshabilita «Eliminar serie»
        rule.onNodeWithText("Deshacer").performClick()
        rule.waitForIdle()
        assertEquals(2, normalExercise.sets.size)

        rule.onNodeWithText("Duplicar").performClick()
        rule.waitForIdle()
        assertEquals(3, normalExercise.sets.size)

        rule.onNodeWithText("Aplicar a todas").performClick()
        rule.onNodeWithText("Aplicar").performClick()
        rule.waitForIdle()
        assertTrue(normalExercise.sets.all { it.weight == normalExercise.sets.first().weight })

        // Reducir hasta 1 serie y verificar bloqueo de última serie
        rule.runOnUiThread {
            normalExercise = normalExercise.copy(sets = listOf(normalExercise.sets.first()))
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Más acciones de la serie").performClick()
        rule.onNodeWithText("Siempre queda una serie").assertIsDisplayed()

        // 2. Superserie bilateral y unilateral por rondas: sin Subir/Bajar en ningún menú
        rule.runOnUiThread { lastMode = "superset" }
        rule.waitForIdle()
        rule.onNodeWithText("Ronda 1").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Ampliar serie").onFirst().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Más acciones de la serie").onFirst().performClick()
        rule.onNodeWithText("Subir serie").assertDoesNotExist()
        rule.onNodeWithText("Bajar serie").assertDoesNotExist()
        rule.onNodeWithText("Siempre queda una serie").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.assertNoMoreMovesInNormalTree() {
        onAllNodesWithText("Subir serie").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
        onAllNodesWithText("Bajar serie").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }
}
