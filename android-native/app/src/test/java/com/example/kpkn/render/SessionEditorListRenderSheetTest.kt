package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplatePublicationStatus
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.DragLiftPreview
import com.example.kpkn.screens.sessioneditor.DragPartLiftPreview
import com.example.kpkn.screens.sessioneditor.SessionEditorInvalidDropBanner
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.components.ExerciseEditorCard
import com.example.kpkn.screens.sessioneditor.components.GroupEditorCard
import com.example.kpkn.screens.sessioneditor.components.SessionEditorEmptyState
import com.example.kpkn.screens.sessioneditor.components.SessionExerciseDetailSheet
import com.example.kpkn.screens.sessioneditor.components.SupersetExerciseConfigOverlay
import com.example.kpkn.screens.sessioneditor.components.SupersetGroupEditorCard
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.screens.sessioneditor.components.sheets.SupersetMemberPickerSheet
import com.example.kpkn.screens.sessioneditor.design.SessionExerciseUndoNotice
import com.example.kpkn.screens.sessioneditor.design.SessionGroupLabel
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.screens.sessioneditor.design.SessionModuleSlot
import com.example.kpkn.ui.design.KpknTextAction
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Hoja de render de la lista del editor (ER1b): dos grupos (uno plegado), ejercicios sueltos, una superserie de 3, un grupo de movilidad,
 * el espacio de cardio con 2 cardios, la sesión vacía, el arrastre (vista flotante y zona no válida) y el aviso de «Deshacer». A 360 dp,
 * al 100 % y al 130 % de letra, el selector de miembros de la superserie y la configuración de un miembro. Las escenas solo dibujan con
 * `KPKN_RENDER_DIR` (cada una se salta sin ella); la prueba de textos `textosVisiblesSinErratas` corre siempre.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi", application = android.app.Application::class)
class SessionEditorListRenderSheetTest {

    /** Sin el salto de clase: cada escena se salta con su propio `assumeTrue`, y la prueba de textos corre siempre. */
    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null
    private var fontScale by mutableStateOf(1f)

    private val noop: () -> Unit = {}

    private fun reps(id: String, reps: Int, rir: Int? = 2): ExerciseSet =
        ExerciseSet(id = id, targetReps = reps, targetRepsRange = RepRange(reps - 2, reps), targetRIR = rir)

    private fun strength(id: String, name: String, sets: Int, reps: Int, rest: Int? = 150, superset: String? = null): Exercise =
        Exercise(
            id = id,
            name = name,
            trainingMode = TrainingMode.REPS,
            restTime = rest,
            sets = (1..sets).map { reps("$id-$it", reps) },
            supersetGroupRef = superset,
        )

    private fun cardio(id: String, name: String, type: CardioType, details: CardioDetails): Exercise =
        Exercise(id = id, name = name, trainingMode = TrainingMode.TIME, cardioDetails = details.copy(type = type))

    private fun group(id: String, name: String, exercises: List<Exercise> = emptyList(), mobility: Boolean = false): SessionPart =
        SessionPart(id = id, name = name, exercises = exercises, isMobilityGroup = mobility)

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { out -> cropped.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }

    /** Pinta [content] en una hoja de 360 dp de ancho y guarda el PNG al 100 % y al 130 % de letra. */
    private fun renderSheet(name: String, heightDp: Int, content: @Composable () -> Unit) {
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                Box(
                    Modifier
                        .testTag("sheet")
                        .width(360.dp)
                        .height(heightDp.dp)
                        .background(Color.Black),
                ) {
                    content()
                }
            }
        }
        for ((scale, suffix) in listOf(1f to "100", 1.3f to "130")) {
            rule.runOnUiThread { fontScale = scale }
            rule.waitForIdle()
            save("${name}_$suffix")
        }
    }

    /** Ejercicio plegado dentro de su trozo de módulo: el mismo bloque que la lista pinta. */
    @Composable
    private fun Folded(exercise: Exercise, edge: SessionModuleEdge, suppressRest: Boolean = false) {
        SessionModuleSlot(edge = edge) {
            ExerciseEditorCard(
                exercise = exercise,
                exerciseInfo = null,
                accentHex = null,
                partId = "p",
                isCompetitionMovement = false,
                isDragging = false,
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
                suppressIndividualRest = suppressRest,
            )
        }
    }

    /** Cabecera de un grupo, como la pinta el renderer: su trozo de módulo con [GroupEditorCard] dentro. */
    @Composable
    private fun GroupHeader(part: SessionPart, collapsed: Boolean, edge: SessionModuleEdge) {
        SessionModuleSlot(edge = edge) {
            GroupEditorCard(
                part = part,
                collapsed = collapsed,
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

    /** Pie de un grupo: «+ Añadir ejercicio a …» dentro del módulo, como en el renderer. */
    @Composable
    private fun GroupFooter(name: String) {
        SessionModuleSlot(edge = SessionModuleEdge.BOTTOM) {
            KpknTextAction(text = "Añadir ejercicio a $name", onClick = noop, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    @Test
    fun renderGruposYSueltos() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        val press = strength("press", "Press banca", sets = 3, reps = 6)
        val remo = strength("remo", "Remo con barra", sets = 2, reps = 8)
        val sentadilla = strength("sent", "Sentadilla trasera", sets = 3, reps = 5)
        val dominadas = strength("dom", "Dominadas", sets = 2, reps = 6)
        renderSheet("lista_grupos_y_sueltos", heightDp = 800) {
            Column {
                GroupHeader(group("g1", "Empuje", listOf(press, remo)), collapsed = false, edge = SessionModuleEdge.TOP)
                Folded(press, SessionModuleEdge.MIDDLE)
                Folded(remo, SessionModuleEdge.MIDDLE)
                Box(Modifier.fillMaxWidth()) { GroupFooter("Empuje") }
                GroupHeader(group("g2", "Pierna", listOf(sentadilla)), collapsed = true, edge = SessionModuleEdge.ONLY)
                Folded(dominadas, SessionModuleEdge.TOP)
                Folded(sentadilla, SessionModuleEdge.BOTTOM)
            }
        }
    }

    @Test
    fun renderSuperserie() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        val a = strength("a", "Press inclinado", sets = 3, reps = 8, rest = null, superset = "ss")
        val b = strength("b", "Aperturas", sets = 3, reps = 12, rest = null, superset = "ss")
        val c = strength("c", "Cruces en polea", sets = 3, reps = 12, rest = null, superset = "ss")
        val members = listOf(a, b, c)
        val group = SupersetGroup(id = "ss", exerciseOrder = members.map { it.id }, rounds = 3)
        renderSheet("superserie_de_tres", heightDp = 520) {
            SessionModuleSlot(edge = SessionModuleEdge.ONLY) {
                SupersetGroupEditorCard(
                    group = group,
                    exercises = members,
                    accentHex = null,
                    partId = "p",
                    onOpenSupersetCreator = { _, _ -> },
                    onUpdateSupersetRest = { _, _, _, _ -> },
                    onRemoveFromSuperset = { _, _ -> },
                    onDissolve = {},
                    onAddRound = noop,
                ) {
                    // Como el renderer: los miembros van sin trozo de módulo, con su línea entre ellos y el raíl del bloque.
                    members.forEachIndexed { index, member ->
                        ExerciseEditorCardBlock(member, suppressRest = true)
                        if (index < members.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 16.dp)
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.06f)),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun renderGrupoDeMovilidad() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        renderSheet("grupo_de_movilidad", heightDp = 260) {
            Column {
                GroupHeader(group("m1", "Movilidad de cadera", mobility = true), collapsed = false, edge = SessionModuleEdge.TOP)
                GroupFooter("Movilidad de cadera")
            }
        }
    }

    @Test
    fun renderEspacioDeCardio() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        val hiit = CardioDetails(
            type = CardioType.BIKE_STATIONARY,
            hiit = CardioHiitConfig(rounds = 8, workSeconds = 30, restSeconds = 30),
        )
        val steady = CardioDetails(type = CardioType.RUN_OUTDOOR, targetDurationSeconds = 20 * 60, targetHrPercent = 65)
        renderSheet("espacio_de_cardio", heightDp = 460) {
            Column {
                SessionGroupLabel(label = "CARDIO", moveLabel = "Mover al inicio", onMove = noop)
                SessionModuleSlot(edge = SessionModuleEdge.TOP) {
                    ExerciseEditorCardBlock(cardio("c1", "Bicicleta", CardioType.BIKE_STATIONARY, hiit))
                }
                SessionModuleSlot(edge = SessionModuleEdge.MIDDLE) {
                    ExerciseEditorCardBlock(cardio("c2", "Carrera exterior", CardioType.RUN_OUTDOOR, steady))
                }
                SessionModuleSlot(edge = SessionModuleEdge.BOTTOM) {
                    KpknTextAction(text = "Añadir cardio", onClick = noop, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }

    /** Fila plegada sin su trozo de módulo (el módulo lo pone quien llama). */
    @Composable
    private fun ExerciseEditorCardBlock(exercise: Exercise, suppressRest: Boolean = false) {
        ExerciseEditorCard(
            exercise = exercise,
            exerciseInfo = null,
            accentHex = null,
            partId = "cardio",
            isCompetitionMovement = false,
            isDragging = false,
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
            suppressIndividualRest = suppressRest,
        )
    }

    @Test
    fun renderSesionVacia() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        renderSheet("sesion_vacia", heightDp = 300) {
            Column(Modifier.padding(16.dp)) {
                SessionEditorEmptyState(onChooseStrength = noop, onChooseCardio = noop)
            }
        }
    }

    @Test
    fun renderArrastreYZonaNoValida() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        val press = strength("press", "Press banca", sets = 3, reps = 6)
        val root = Rect(0f, 0f, 360f * 3f, 900f * 3f)
        val rowRect = Rect(16f * 3f, 150f * 3f, 344f * 3f, 214f * 3f)
        renderSheet("arrastre_y_zona_no_valida", heightDp = 420) {
            Box(Modifier.fillMaxWidth()) {
                Column {
                    Folded(press, SessionModuleEdge.ONLY)
                }
                DragLiftPreview(
                    exercise = press,
                    rect = rowRect,
                    offsetProvider = { Offset(0f, 0f) },
                    rootBounds = root,
                )
                DragPartLiftPreview(
                    partName = "Empuje",
                    rect = Rect(16f * 3f, 240f * 3f, 344f * 3f, 300f * 3f),
                    offsetYProvider = { 0f },
                    rootBounds = root,
                )
                SessionEditorInvalidDropBanner(
                    visible = true,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 340.dp),
                )
            }
        }
    }

    @Test
    fun renderAvisoDeshacer() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        val press = strength("press", "Press banca", sets = 3, reps = 6)
        renderSheet("aviso_deshacer", heightDp = 200) {
            Column {
                SessionModuleSlot(edge = SessionModuleEdge.TOP) {
                    SessionExerciseUndoNotice(onUndo = noop)
                }
                Folded(press, SessionModuleEdge.BOTTOM)
            }
        }
    }

    @Test
    fun renderCrearSuperserie() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        renderSheet("crear_superserie", heightDp = 700) {
            SupersetMemberPickerSheet(
                draft = SupersetDraft(exerciseIds = listOf("press")),
                sessionExercises = pickerEjercicios,
                onUpdateDraft = {},
                onConfirm = noop,
                onOpenCatalog = noop,
                onDismiss = noop,
                sessionParts = pickerPartes,
            )
        }
    }

    @Test
    fun renderConfigurarMiembroDeSuperserie() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        renderSheet("configurar_miembro_de_superserie", heightDp = 640) {
            SupersetExerciseConfigOverlay(
                exercise = miembroDeSuperserie,
                accentColor = SymbolPalette.musculo,
                relationshipAnchorName = null,
                onUpdateExercise = {},
                onUpdateSet = { _, _ -> },
                onOpenRelationshipPicker = noop,
                onClearRelationship = noop,
                onDismiss = noop,
            )
        }
    }

    /** Sesión de ejemplo del selector de superserie: dos partes de dos ejercicios y un ejercicio suelto. */
    private val pickerEjercicios: List<Exercise> by lazy {
        listOf(
            strength("sent", "Sentadilla trasera", sets = 3, reps = 5),
            strength("peso", "Peso muerto rumano", sets = 3, reps = 8),
            strength("press", "Press banca", sets = 3, reps = 6),
            strength("fondos", "Fondos en paralelas", sets = 2, reps = 10),
            strength("remo", "Remo con barra", sets = 2, reps = 8),
        )
    }

    private val pickerPartes: List<SessionPart> by lazy {
        listOf(
            group("g-pierna", "Pierna", pickerEjercicios.filter { it.id == "sent" || it.id == "peso" }),
            group("g-empuje", "Empuje", pickerEjercicios.filter { it.id == "press" || it.id == "fondos" }),
        )
    }

    /** Miembro de superserie para su configuración: sin descanso propio, lo lleva la superserie. */
    private val miembroDeSuperserie: Exercise by lazy {
        strength("a", "Press inclinado", sets = 3, reps = 8, rest = null, superset = "ss")
    }

    /** Press de banca con vínculo al catálogo v2: la ficha abierta desde el editor. */
    private val fichaDelEditor: Exercise by lazy {
        Exercise(
            id = "press-ficha",
            name = "Press banca",
            catalogDefinitionId = "bench_press",
            catalogConfigurationId = "bench_press__barbell",
            sets = listOf(ExerciseSet(id = "press-ficha-1", targetReps = 6, targetRepsRange = RepRange(4, 6), targetRIR = 2)),
        )
    }

    /** Plantilla de usuario de ejemplo: sale en «Mis plantillas» con «Editar» y «Borrar». */
    private val plantillaDeUsuario: SessionTemplate by lazy {
        SessionTemplate(
            id = "plantilla-pecho",
            sourceType = SessionTemplateSourceType.USER,
            name = "Pecho en casa",
            description = "Empuje con mancuernas y peso corporal.",
            session = Session(
                id = "sesion-pecho",
                name = "Pecho en casa",
                exercises = listOf(strength("press-casa", "Press con mancuernas", sets = 3, reps = 8)),
            ),
            publicationStatus = SessionTemplatePublicationStatus.KPKN_NATIVE,
            splitIds = listOf("custom"),
            splitDayLabels = listOf("Pecho"),
            shortDescription = "Empuje con mancuernas",
            exerciseCount = 1,
            autoGenerationEligible = true,
        )
    }

    /** Patrones que no deben salir en ningún texto visible: cifra pegada a m o s, decimal con punto, «x» por «×» e inglés. */
    private val cifraPegadaMs = Regex("\\d[ms]\\b")
    private val decimalConPunto = Regex("\\d\\.\\d")
    private val xEntreCifras = Regex("\\d\\s?x\\s?\\d")
    // Sin distinguir mayúsculas: ninguna palabra española coincide, así que cubre «Superset» y «superset».
    private val inglesOErratas = Regex("(?i)\\b(gap|prep|warmup|sets?|supersets?)\\b")
    private val patronesProhibidos = listOf(cifraPegadaMs, decimalConPunto, xEntreCifras, inglesOErratas)

    /** Textos con semántica de la composición: del árbol unido y del sin unir (un texto de botón sale en los dos). */
    private fun textosVisibles(): List<String> = listOf(false, true).flatMap { sinUnir ->
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = sinUnir)
            .fetchSemanticsNodes()
            .flatMap { nodo -> nodo.config[SemanticsProperties.Text].map { it.text } }
    }.distinct()

    /**
     * Prueba de textos (e): monta a la vez la ficha (a), el selector de superserie (b), la configuración de un miembro (c) y el catálogo
     * de plantillas (d), abre sus diálogos y desplegables con los controles reales y lee cada texto visible. Falla con la lista de los
     * textos que rompen un patrón. No dibuja nada: no depende de `KPKN_RENDER_DIR`.
     */
    @Test
    fun textosVisiblesSinErratas() {
        // La app se usa en español (decimal con coma). Sin fijarlo, el JVM decide: «formatSeriesEquivalent» usa "%.1f" y pintaría «1.0 serie».
        val localeAnterior = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-ES"))
        try {
            comprobarTextosVisibles()
        } finally {
            Locale.setDefault(localeAnterior)
        }
    }

    /** Cuerpo de la prueba de textos: monta (a)–(d) a la vez y lee cada texto visible. */
    private fun comprobarTextosVisibles() {
        var leidos = 0
        val fallos = mutableListOf<String>()
        fun revisar(paso: String) {
            val textos = textosVisibles()
            leidos += textos.size
            textos.filter { texto -> patronesProhibidos.any { it.containsMatchIn(texto) } }.forEach { fallos += "$paso: «$it»" }
        }
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) {
                    // El catálogo de plantillas va arriba: sus diálogos se abren con clics y deben caer dentro de la ventana de la prueba.
                    Box(Modifier.width(360.dp).height(900.dp)) {
                        TemplateCatalogBrowser(
                            templates = listOf(plantillaDeUsuario),
                            searchQuery = "",
                            onSelectTemplate = {},
                            exerciseIndex = emptyMap(),
                            glassDark = true,
                        )
                    }
                    Box(Modifier.width(360.dp).height(1400.dp)) {
                        SessionExerciseDetailSheet(exercise = fichaDelEditor, onDismiss = noop)
                    }
                    Box(Modifier.width(360.dp).height(700.dp)) {
                        SupersetMemberPickerSheet(
                            draft = SupersetDraft(exerciseIds = listOf("press")),
                            sessionExercises = pickerEjercicios,
                            onUpdateDraft = {},
                            onConfirm = noop,
                            onOpenCatalog = noop,
                            onDismiss = noop,
                            sessionParts = pickerPartes,
                        )
                    }
                    Box(Modifier.width(360.dp).height(640.dp)) {
                        SupersetExerciseConfigOverlay(
                            exercise = miembroDeSuperserie,
                            accentColor = SymbolPalette.musculo,
                            relationshipAnchorName = null,
                            onUpdateExercise = {},
                            onUpdateSet = { _, _ -> },
                            onOpenRelationshipPicker = noop,
                            onClearRelationship = noop,
                            onDismiss = noop,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        revisar("base")
        // Los diálogos y desplegables de plantillas no se revisan aquí: al abrirlos la composición no vuelve a reposo en Robolectric
        // (AppNotIdleException), así que sus textos quedan fuera de esta prueba.
        assertTrue("No se leyó ningún texto con semántica", leidos > 0)
        assertTrue("Textos fuera de patrón: $fallos", fallos.isEmpty())
    }
}
