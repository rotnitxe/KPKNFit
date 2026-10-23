package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.DragLiftPreview
import com.example.kpkn.screens.sessioneditor.SessionEditorDropKind
import com.example.kpkn.screens.sessioneditor.SessionEditorDropPlan
import com.example.kpkn.screens.sessioneditor.SessionEditorInvalidDropBanner
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import com.example.kpkn.screens.sessioneditor.components.ExerciseEditorCard
import com.example.kpkn.screens.sessioneditor.components.GroupEditorCard
import com.example.kpkn.screens.sessioneditor.components.SessionEditorCardioPlacementControl
import com.example.kpkn.screens.sessioneditor.components.SessionEditorEmptyState
import com.example.kpkn.screens.sessioneditor.components.SessionEditorSpace
import com.example.kpkn.screens.sessioneditor.components.SessionEditorSpaceOption
import com.example.kpkn.screens.sessioneditor.components.SupersetGroupEditorCard
import com.example.kpkn.screens.sessioneditor.design.SessionEditorDashedAddButton
import com.example.kpkn.screens.sessioneditor.design.SessionEditorDropHintBanner
import com.example.kpkn.screens.sessioneditor.design.SessionEditorOutsideDropZone
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseUndoNotice
import com.example.kpkn.screens.sessioneditor.design.SessionGroupLabel
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.SessionModuleSlot
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameBlockNotice
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameBottomBar
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameHeader
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameTopBar
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameWeekContent
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorLoadErrorState
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorLoadingState
import com.example.kpkn.screens.sessioneditor.frame.frameWeekOrder
import com.example.kpkn.screens.sessioneditor.frame.nextFreeVariant
import com.example.kpkn.screens.sessioneditor.frame.sessionFrameSummaryChips
import com.example.kpkn.screens.sessioneditor.SessionEditorDropIndicator
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.components.icons.KpknLineIcons
import dev.chrisbanes.haze.HazeState
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * HOJA DE DIBUJO de ED-A (marco del editor): Main (A1), la sesión vacía y sus pasos (A2), solo cardio (A2b), los cuatro destinos del
 * arrastre (A3), borrar con deshacer (A4), la hoja de semana y variantes (A5), los tres estados (A6) y la superserie dentro de su grupo
 * (C1). A 360 × 880 dp, al 100 % y al 130 % de letra. No comprueba nada: escribe PNG con `eda_<escena>[_s130].png`. Solo corre con
 * `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class SessionEditorEdaRenderSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val noop: () -> Unit = {}
    private var scene by mutableStateOf("main")
    private var fontScale by mutableStateOf(1f)
    private var hostView: View? = null
    private val mainScroll = ScrollState(0)
    private val glassHaze = HazeState()

    // ───────────────────────────── datos ─────────────────────────────

    private fun strength(
        id: String,
        name: String,
        sets: Int,
        low: Int,
        high: Int,
        rest: Int? = 150,
        group: String? = null,
        unilateral: Boolean = false,
    ) = Exercise(
        id = id,
        name = name,
        trainingMode = TrainingMode.REPS,
        restTime = rest,
        isUnilateral = unilateral,
        supersetGroupRef = group,
        sets = (1..sets).map { ExerciseSet(id = "$id-$it", targetReps = high, targetRepsRange = RepRange(low, high), targetRIR = 2) },
    )

    private fun cardio(id: String, name: String, details: CardioDetails) =
        Exercise(id = id, name = name, trainingMode = TrainingMode.TIME, cardioDetails = details)

    private val sentadilla = strength("sent", "Sentadilla trasera con barra alta", 4, 4, 6, rest = 180)
    private val peso = strength("peso", "Peso muerto rumano", 4, 6, 8, rest = 150)
    private val zancada = strength("zan", "Zancada con mancuernas", 3, 8, 10, rest = null, group = "ss", unilateral = true)
    private val curl = strength("curl", "Curl femoral tumbado", 3, 10, 12, rest = null, group = "ss")
    private val talones = strength("tal", "Elevación de talones de pie", 3, 12, 15, rest = null, group = "ss")
    private val dominadas = strength("dom", "Dominadas", 3, 6, 8)
    private val bici = cardio("bici", "Bici estática", CardioDetails(type = CardioType.BIKE_STATIONARY, targetDurationSeconds = 20 * 60))
    private val remo = cardio(
        "remo",
        "Remo ergómetro",
        CardioDetails(type = CardioType.ROW_MACHINE, hiit = CardioHiitConfig(rounds = 6, workSeconds = 30, restSeconds = 30)),
    )
    private val superset2 = SupersetGroup(id = "ss", exerciseOrder = listOf("zan", "curl"), rounds = 3, restBetweenExercises = 60, restAfterSuperset = 90)
    private val superset3 = SupersetGroup(id = "ss", exerciseOrder = listOf("zan", "curl", "tal"), rounds = 3, restBetweenExercises = 60, restAfterSuperset = 120)

    // ───────────────────────────── piezas ─────────────────────────────

    @Composable
    private fun Row64(exercise: Exercise, badge: String? = null, dragging: Boolean = false) {
        ExerciseEditorCard(
            exercise = exercise,
            exerciseInfo = null,
            accentHex = null,
            partId = "p",
            isCompetitionMovement = false,
            isDragging = dragging,
            isDropTarget = false,
            isPartDropTarget = false,
            onBoundsChange = {},
            onDragStart = {},
            onDrag = {},
            onDragEnd = noop,
            onUpdateExercise = {},
            onDeleteExercise = noop,
            onAddSet = {},
            onUpdateSet = { _, _ -> },
            onRemoveSet = {},
            onMoveSet = { _, _ -> },
            onRemoveMobility = {},
            onOpenQuickActions = noop,
            relationshipAnchorName = null,
            onOpenRelationshipPicker = noop,
            onClearRelationship = noop,
            onUpdateRelationshipType = {},
            onUpdateRelationshipNotes = {},
            autoExpand = false,
            onAutoExpandHandled = noop,
            suppressIndividualRest = badge != null,
            memberBadge = badge,
        )
    }

    @Composable
    private fun GroupHeader(name: String, exercises: List<Exercise>, edge: SessionModuleEdge, highlighted: Boolean = false) {
        SessionModuleSlot(edge = edge, highlighted = highlighted) {
            GroupEditorCard(
                part = SessionPart(id = name, name = name, exercises = exercises),
                collapsed = false,
                onToggleCollapse = noop,
                onRename = {},
                onRemove = {},
                isDragging = false,
                isDropTarget = false,
                onBoundsChange = {},
                onDrag = {},
                onDragEnd = noop,
            )
        }
    }

    @Composable
    private fun ExerciseSlot(exercise: Exercise, edge: SessionModuleEdge, highlighted: Boolean = false, dragging: Boolean = false) {
        SessionModuleSlot(edge = edge, highlighted = highlighted) { Row64(exercise, dragging = dragging) }
    }

    @Composable
    private fun AddFooter(groupName: String, highlighted: Boolean = false) {
        SessionModuleSlot(edge = SessionModuleEdge.BOTTOM, highlighted = highlighted) {
            SessionEditorDashedAddButton(
                text = "Añadir a $groupName",
                onClick = noop,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
            )
        }
    }

    @Composable
    private fun SupersetSlot(
        members: List<Exercise>,
        group: SupersetGroup,
        edge: SessionModuleEdge,
        highlighted: Boolean = false,
        dropTarget: Boolean = false,
    ) {
        SessionModuleSlot(edge = edge, highlighted = highlighted) {
            SupersetGroupEditorCard(
                group = group,
                exercises = members,
                accentHex = null,
                partId = "acc",
                isDropTarget = dropTarget,
                onOpenSupersetCreator = { _, _ -> },
                onUpdateSupersetRest = { _, _, _, _ -> },
                onRemoveFromSuperset = { _, _ -> },
                onDissolve = {},
                onAddRound = noop,
            ) {
                members.forEachIndexed { index, member -> Row64(member, badge = "A${index + 1}") }
            }
        }
    }

    @Composable
    private fun CardioModule() {
        SessionGroupLabel(
            label = "CARDIO · AL FINAL",
            moveLabel = "Mover al inicio",
            onMove = noop,
            accent = SymbolPalette.columna,
            showRule = true,
        )
        SessionModuleSlot(edge = SessionModuleEdge.TOP) { Row64(bici) }
        SessionModuleSlot(edge = SessionModuleEdge.BOTTOM) {
            KpknTextAction(text = "Añadir cardio", onClick = noop, modifier = Modifier.padding(horizontal = 16.dp), leadingIcon = KpknLineIcons.Plus)
        }
    }

    /** El marco del editor: fila superior, cabecera y lista desplazable, con la isla inferior encima. */
    @Composable
    private fun EditorFrame(
        name: String,
        chips: List<String>,
        minutes: Int?,
        suggestions: Int = 3,
        scroll: ScrollState = rememberScrollState(),
        overlay: @Composable BoxScope.() -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize()) {
                SessionEditorFrameTopBar(
                    dayLabel = "Jue · Semana 3",
                    forward = true,
                    canGoPrevious = true,
                    canGoNext = true,
                    onBack = {},
                    onPrevious = {},
                    onNext = {},
                    onOpenWeek = {},
                    onTransfer = {},
                    onHistory = {},
                )
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(bottom = 110.dp)) {
                    SessionEditorFrameHeader(
                        sessionId = name,
                        name = name,
                        description = "",
                        protocolLabel = null,
                        summaryChips = chips,
                        onNameChange = {},
                        onDescriptionChange = {},
                        onDaySwipe = { false },
                    )
                    content()
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                SessionEditorFrameBottomBar(
                    hazeState = glassHaze,
                    suggestionCount = suggestions,
                    estimatedMinutes = minutes,
                    limitMinutes = 60,
                    onAdd = {},
                    onAssistant = {},
                    onRules = {},
                    onTime = {},
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            overlay()
        }
    }

    @Composable
    private fun PendingLabel() {
        androidx.compose.material3.Text(
            text = "AÑADIR A LA SESIÓN",
            style = com.example.kpkn.ui.design.KpknType.eyebrow,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }

    // ───────────────────────────── escenas ─────────────────────────────

    private val sceneNames = listOf("main", "a2_vacia", "a2_fuerza", "a2_cardio", "a2_ambas", "a2b_solo_cardio", "a3_entre_grupos", "a3_fuera_de_grupo", "a3_fuera_sin_sueltos", "a3_dentro_de_grupo", "a3_a_superserie", "a3_superserie_llena", "a4_borrar_deshacer", "a5_semana", "a6_cargando", "a6_error", "a6_bloqueado", "c1_superserie")

    @Composable
    private fun SceneContent(name: String) {
        when (name) {
        // A1 · Main
        "main" -> {
            EditorFrame(name = "Pierna A", chips = sessionFrameSummaryChips(5, 14, 58), minutes = 58, scroll = mainScroll) {
                GroupHeader("Principal", listOf(sentadilla, peso), SessionModuleEdge.TOP)
                ExerciseSlot(sentadilla.copy(isStarTarget = true), SessionModuleEdge.MIDDLE)
                ExerciseSlot(peso, SessionModuleEdge.MIDDLE)
                AddFooter("Principal")
                GroupHeader("Accesorios", listOf(zancada, curl), SessionModuleEdge.TOP)
                SupersetSlot(listOf(zancada, curl), superset2, SessionModuleEdge.MIDDLE)
                AddFooter("Accesorios")
                CardioModule()
            }
        }
        // A2 · sesión vacía y sus pasos
        "a2_vacia" -> {
            EditorFrame(name = "", chips = sessionFrameSummaryChips(0, 0, null), minutes = null, suggestions = 0) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SessionEditorEmptyState(onChooseStrength = noop, onChooseCardio = noop)
                }
            }
        }
        "a2_fuerza" -> {
            EditorFrame(name = "", chips = sessionFrameSummaryChips(0, 0, null), minutes = null, suggestions = 0) {
                SessionGroupLabel(label = "FUERZA")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    KpknTextAction(text = "Añadir ejercicio", onClick = noop, modifier = Modifier.padding(start = 4.dp), leadingIcon = KpknLineIcons.Plus)
                    KpknSecondaryPill(text = "Nuevo grupo", onClick = noop, modifier = Modifier.fillMaxWidth())
                    PendingLabel()
                    SessionEditorSpaceOption(space = SessionEditorSpace.CARDIO, onClick = noop)
                }
            }
        }
        "a2_cardio" -> {
            EditorFrame(name = "", chips = sessionFrameSummaryChips(0, 0, null), minutes = null, suggestions = 0) {
                SessionGroupLabel(label = "CARDIO", accent = SymbolPalette.columna, showRule = true)
                SessionModuleSlot(edge = SessionModuleEdge.ONLY) {
                    KpknTextAction(text = "Añadir cardio", onClick = noop, modifier = Modifier.padding(horizontal = 16.dp), leadingIcon = KpknLineIcons.Plus)
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PendingLabel()
                    SessionEditorSpaceOption(space = SessionEditorSpace.STRENGTH, onClick = noop)
                }
            }
        }
        "a2_ambas" -> {
            EditorFrame(name = "", chips = sessionFrameSummaryChips(0, 0, null), minutes = null, suggestions = 0) {
                SessionEditorCardioPlacementControl(atStart = false, onChange = {}, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                SessionGroupLabel(label = "FUERZA", moveLabel = "Mover al final")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    KpknTextAction(text = "Añadir ejercicio", onClick = noop, modifier = Modifier.padding(start = 4.dp), leadingIcon = KpknLineIcons.Plus)
                    KpknSecondaryPill(text = "Nuevo grupo", onClick = noop, modifier = Modifier.fillMaxWidth())
                }
                SessionGroupLabel(label = "CARDIO · AL FINAL", moveLabel = "Mover al inicio", accent = SymbolPalette.columna, showRule = true)
                SessionModuleSlot(edge = SessionModuleEdge.ONLY) {
                    KpknTextAction(text = "Añadir cardio", onClick = noop, modifier = Modifier.padding(horizontal = 16.dp), leadingIcon = KpknLineIcons.Plus)
                }
            }
        }
        // A2b · solo cardio
        "a2b_solo_cardio" -> {
            EditorFrame(name = "Cardio Z2 + series", chips = sessionFrameSummaryChips(2, 0, 42, showSets = false), minutes = 42, suggestions = 0) {
                Column {
                    SessionModuleSlot(edge = SessionModuleEdge.TOP) { Row64(bici) }
                    SessionModuleSlot(edge = SessionModuleEdge.MIDDLE) { Row64(remo) }
                    SessionModuleSlot(edge = SessionModuleEdge.BOTTOM) {
                        KpknTextAction(text = "Añadir cardio", onClick = noop, modifier = Modifier.padding(horizontal = 16.dp), leadingIcon = KpknLineIcons.Plus)
                    }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        KpknSecondaryPill(text = "Añadir fuerza", onClick = noop, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        // A3 · los destinos del arrastre
        "a3_entre_grupos" -> {
            A3Scene(
                plan = SessionEditorDropPlan(SessionEditorDropKind.BETWEEN_GROUPS, "acc", null, 0, "Al soltar pasará de «Principal» a «Accesorios»."),
            ) {
                GroupHeader("Principal", listOf(sentadilla, peso), SessionModuleEdge.TOP)
                ExerciseSlot(sentadilla, SessionModuleEdge.MIDDLE)
                ExerciseSlot(peso, SessionModuleEdge.MIDDLE, dragging = true)
                AddFooter("Principal")
                GroupHeader("Accesorios", listOf(zancada, curl), SessionModuleEdge.TOP, highlighted = true)
                SessionEditorDropIndicator(visible = true)
                SupersetSlot(listOf(zancada, curl), superset2, SessionModuleEdge.MIDDLE, highlighted = true)
                AddFooter("Accesorios", highlighted = true)
            }
        }
        "a3_fuera_de_grupo" -> {
            A3Scene(
                plan = SessionEditorDropPlan(SessionEditorDropKind.OUT_OF_GROUP, null, null, 1, "Al soltar saldrá de «Principal» y quedará sin grupo."),
            ) {
                SessionGroupLabel(label = "SIN GRUPO")
                ExerciseSlot(dominadas, SessionModuleEdge.ONLY, highlighted = true)
                SessionEditorDropIndicator(visible = true)
                GroupHeader("Principal", listOf(sentadilla, peso), SessionModuleEdge.TOP)
                ExerciseSlot(sentadilla, SessionModuleEdge.MIDDLE, dragging = true)
                ExerciseSlot(peso, SessionModuleEdge.MIDDLE)
                AddFooter("Principal")
            }
        }
        "a3_fuera_sin_sueltos" -> {
            A3Scene(
                plan = SessionEditorDropPlan(SessionEditorDropKind.OUT_OF_GROUP, null, null, 0, "Al soltar saldrá de «Principal» y quedará sin grupo."),
            ) {
                SessionEditorOutsideDropZone(visible = true)
                GroupHeader("Principal", listOf(sentadilla, peso), SessionModuleEdge.TOP)
                ExerciseSlot(sentadilla, SessionModuleEdge.MIDDLE, dragging = true)
                ExerciseSlot(peso, SessionModuleEdge.MIDDLE)
                AddFooter("Principal")
            }
        }
        "a3_dentro_de_grupo" -> {
            A3Scene(
                plan = SessionEditorDropPlan(SessionEditorDropKind.REORDER, "p1", null, 0, "Al soltar quedará en la posición 1 de «Principal»."),
            ) {
                GroupHeader("Principal", listOf(sentadilla, peso), SessionModuleEdge.TOP)
                SessionEditorDropIndicator(visible = true)
                ExerciseSlot(sentadilla, SessionModuleEdge.MIDDLE)
                ExerciseSlot(peso, SessionModuleEdge.MIDDLE, dragging = true)
                AddFooter("Principal")
                GroupHeader("Accesorios", listOf(zancada, curl), SessionModuleEdge.TOP)
                SupersetSlot(listOf(zancada, curl), superset2, SessionModuleEdge.MIDDLE)
                AddFooter("Accesorios")
            }
        }
        "a3_a_superserie" -> {
            A3Scene(
                plan = SessionEditorDropPlan(
                    SessionEditorDropKind.INTO_SUPERSET, "acc", "ss", 2,
                    "Al soltar entrará como A3 en la superserie, después de «Curl femoral tumbado».", "A3",
                ),
            ) {
                SessionGroupLabel(label = "SIN GRUPO")
                ExerciseSlot(dominadas, SessionModuleEdge.ONLY, dragging = true)
                GroupHeader("Accesorios", listOf(zancada, curl), SessionModuleEdge.TOP, highlighted = false)
                SupersetSlot(listOf(zancada, curl), superset2, SessionModuleEdge.MIDDLE, dropTarget = true)
                AddFooter("Accesorios")
            }
        }
        "a3_superserie_llena" -> {
            A3Scene(
                plan = SessionEditorDropPlan(SessionEditorDropKind.INVALID, "acc", "ss", 2, "La superserie ya tiene 4 ejercicios."),
            ) {
                SessionGroupLabel(label = "SIN GRUPO")
                ExerciseSlot(dominadas, SessionModuleEdge.ONLY, dragging = true)
                GroupHeader("Accesorios", listOf(zancada, curl, talones), SessionModuleEdge.TOP)
                SupersetSlot(listOf(zancada, curl, talones), superset3, SessionModuleEdge.MIDDLE, dropTarget = true)
                AddFooter("Accesorios")
            }
        }
        // A4 · borrar con deshacer
        "a4_borrar_deshacer" -> {
            EditorFrame(name = "Pierna A", chips = sessionFrameSummaryChips(4, 14, 52), minutes = 52) {
                GroupHeader("Principal", listOf(sentadilla), SessionModuleEdge.TOP)
                ExerciseSlot(sentadilla, SessionModuleEdge.MIDDLE)
                SessionModuleSlot(edge = SessionModuleEdge.MIDDLE) { SessionExerciseUndoNotice(onUndo = noop) }
                AddFooter("Principal")
                GroupHeader("Accesorios", listOf(zancada, curl), SessionModuleEdge.TOP)
                ExerciseSlot(peso, SessionModuleEdge.MIDDLE)
                ExerciseSlot(dominadas, SessionModuleEdge.BOTTOM)
            }
        }
        // A5 · semana y variantes
        "a5_semana" -> {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
                SessionEditorFrameWeekContent(
                    orderedDays = frameWeekOrder(1),
                    sessions = listOf(
                        Session(id = "lun", name = "Torso A", dayOfWeek = 1, isMainSession = true),
                        Session(id = "mie", name = "Torso B", dayOfWeek = 3, isMainSession = true),
                        Session(id = "jue-1", name = "Pierna A", dayOfWeek = 4, isMainSession = true),
                        Session(id = "jue-2", name = "Pierna B", dayOfWeek = 4, isMainSession = false),
                        Session(id = "vie", name = "Torso C", dayOfWeek = 5, isMainSession = true),
                    ),
                    currentSessionId = "jue-1",
                    currentDayOfWeek = 4,
                    competitionKeyDaysInWeek = setOf(7),
                    roadmapOptions = listOf(
                        SessionRoadmapOption(0, 0, 0, 1, "w2", "Macro", "Base", "Acumulación", 5),
                        SessionRoadmapOption(0, 0, 0, 2, "w3", "Macro", "Base", "Acumulación", 5),
                    ),
                    currentWeekId = "w3",
                    isSimpleProgram = true,
                    hasActiveLoops = true,
                    activeVariant = WeekVariant.B,
                    availableVariants = listOf(WeekVariant.A, WeekVariant.B, WeekVariant.C),
                    variantName = { variant ->
                        when (variant) {
                            WeekVariant.A -> "Original"
                            WeekVariant.B -> "Pierna A – Rápida"
                            else -> "Pierna A – Pesada"
                        }
                    },
                    nextVariant = nextFreeVariant(listOf(WeekVariant.A, WeekVariant.B, WeekVariant.C)),
                    defaultVariantName = "Pierna A – Rápida",
                    onSelectSession = {},
                    onSetMainSession = {},
                    onCreateSessionForDay = {},
                    onSelectRoadmapOption = {},
                    onSelectVariant = {},
                    onCreateVariant = { _, _ -> },
                    onDeleteActiveVariant = {},
                    onDone = {},
                )
            }
        }
        // A6 · estados
        "a6_cargando" -> SessionEditorLoadingState()
        "a6_error" -> SessionEditorLoadErrorState(message = null, onBack = {}, onRetry = {})
        "a6_bloqueado" -> {
            EditorFrame(name = "Torso A", chips = sessionFrameSummaryChips(4, 16, 55), minutes = 55, suggestions = 0) {
                SessionEditorFrameBlockNotice(
                    message = "No se pudo guardar: «Press militar con barra» no tiene su ficha del catálogo. Cámbialo por uno del catálogo o quítalo.",
                    onOpenExercise = {},
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                ExerciseSlot(strength("pm", "Press militar con barra", 4, 6, 8), SessionModuleEdge.ONLY)
            }
        }
        // C1 · la superserie dentro de su grupo
        "c1_superserie" -> {
            EditorFrame(name = "Pierna A", chips = sessionFrameSummaryChips(3, 9, 40), minutes = 40) {
                GroupHeader("Accesorios", listOf(zancada, curl, talones), SessionModuleEdge.TOP)
                SupersetSlot(listOf(zancada, curl, talones), superset3, SessionModuleEdge.MIDDLE)
                AddFooter("Accesorios")
            }
        }
            else -> Unit
        }
    }

    /** Un arrastre: la frase de arriba, la fila levantada y la lista, que no se mueve. */
    @Composable
    private fun A3Scene(plan: SessionEditorDropPlan, list: @Composable () -> Unit) {
        val density = LocalDensity.current.density
        val root = Rect(0f, 0f, 360f * density, 880f * density)
        val lifted = Rect(16f * density, 500f * density, 344f * density, 564f * density)
        EditorFrame(name = "Pierna A", chips = sessionFrameSummaryChips(5, 14, 58), minutes = 58, overlay = {
            DragLiftPreview(
                exercise = peso,
                rect = lifted,
                offsetProvider = { Offset(0f, 0f) },
                rootBounds = root,
            )
            SessionEditorDropHintBanner(plan = plan, modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp))
        }) {
            list()
        }
    }

    // ───────────────────────────── captura ─────────────────────────────

    private fun drawView(): Bitmap {
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        return full
    }

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { drawView().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderEdaScenes() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                // Los símbolos animados y el brillo de carga se dibujan quietos: un cuadro fijo y repetible.
                LocalWizardReducedMotion provides true,
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black)) { SceneContent(scene) }
            }
        }
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle { fontScale = scale }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            for (name in sceneNames) {
                rule.runOnIdle { scene = name }
                rule.mainClock.advanceTimeByFrame()
                rule.waitForIdle()
                rule.mainClock.advanceTimeByFrame()
                save("eda_$name$suffix")
                if (name == "main") {
                    // La parte de abajo de Main: la superserie dentro de su grupo y el cardio.
                    rule.runOnIdle { mainScroll.dispatchRawDelta(mainScroll.maxValue.toFloat()) }
                    rule.mainClock.advanceTimeByFrame()
                    rule.waitForIdle()
                    save("eda_main_abajo$suffix")
                    rule.runOnIdle { mainScroll.dispatchRawDelta(-mainScroll.maxValue.toFloat()) }
                    rule.mainClock.advanceTimeByFrame()
                }
            }
        }
    }
}
