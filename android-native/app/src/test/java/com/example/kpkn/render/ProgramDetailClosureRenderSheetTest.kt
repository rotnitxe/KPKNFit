package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.ProgramStructureSamples
import com.example.kpkn.screens.programdetail.design.ProgramAboutModule
import com.example.kpkn.screens.programdetail.design.ProgramProgressModule
import com.example.kpkn.domain.training.ProgramProgressReport
import com.example.kpkn.domain.training.ProgressPosition
import com.example.kpkn.domain.training.ProgramWeekAdherence
import com.example.kpkn.domain.training.LiftTrend
import com.example.kpkn.domain.training.DecisionLine
import com.example.kpkn.screens.programdetail.design.ProgramDayModule
import com.example.kpkn.screens.programdetail.design.ProgramDayUi
import com.example.kpkn.screens.programdetail.design.ProgramHeader
import com.example.kpkn.screens.programdetail.design.ProgramHeaderActions
import com.example.kpkn.screens.programdetail.design.ProgramHeaderPrimary
import com.example.kpkn.screens.programdetail.design.ProgramHeaderState
import com.example.kpkn.screens.programdetail.design.ProgramHeaderStatus
import com.example.kpkn.screens.programdetail.design.ProgramPhaseBlockUi
import com.example.kpkn.screens.programdetail.design.ProgramPhasesModule
import com.example.kpkn.screens.programdetail.design.ProgramVolumeCountControls
import com.example.kpkn.screens.programdetail.design.ProgramVolumeInactive
import com.example.kpkn.screens.programdetail.design.ProgramVolumeModule
import com.example.kpkn.screens.programdetail.design.ProgramVolumeSplit
import com.example.kpkn.screens.programdetail.design.ProgramVolumeSummaryModule
import com.example.kpkn.screens.programdetail.design.programDetailTabsOptions
import com.example.kpkn.screens.programdetail.design.programHeaderDotColor
import com.example.kpkn.screens.programdetail.design.split.SplitCatalogMode
import com.example.kpkn.screens.programdetail.design.split.SplitCatalogScreen
import com.example.kpkn.screens.programdetail.design.structure.ProgramBaseWeekModule
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockList
import com.example.kpkn.screens.programdetail.design.structure.ProgramFromProgramItem
import com.example.kpkn.screens.programdetail.design.structure.ProgramFromProgramModule
import com.example.kpkn.screens.programdetail.design.structure.ProgramMapModule
import com.example.kpkn.screens.programdetail.design.structure.StructureGroupLabel
import com.example.kpkn.screens.programdetail.design.structure.StructureSecondarySmallPill
import com.example.kpkn.screens.programdetail.design.structure.programMapSegments
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknTopIsland
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
import java.time.LocalDate

/**
 * HOJA DE DIBUJO de cierre de Program Detail (P7): la pantalla por partes, con sus datos de muestra. Semana con un día desplegado y la
 * isla al hacer scroll; Estructura de un programa por bloques y de uno simple; Volumen; un programa sin activar y otro pausado, y
 * Cambiar reparto. Pinta a 360 dp, con la letra al 100 % y al 130 %, sin movimiento. No comprueba nada: escribe PNG. Solo corre con
 * `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-xxhdpi", application = android.app.Application::class)
class ProgramDetailClosureRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Las escenas de la hoja, en orden. */
    private val cases = listOf(
        "semana", "isla", "estructuraBloques", "estructuraSimple",
        "volumen", "sinActivar", "pausado", "cambiarReparto", "progreso",
    )

    private val active = ProgramDetailSamples.active
    private val activeState: ProgramHeaderState = ProgramDetailSamples.headerState(
        active, ProgramHeaderStatus.ACTIVE, ProgramHeaderPrimary.Start("Torso A"), 12, 3, "Semana 3 de 12 · Acumulación",
    )
    private val pausedState: ProgramHeaderState = ProgramDetailSamples.headerState(
        active, ProgramHeaderStatus.PAUSED, ProgramHeaderPrimary.Activate, 12, 3, "Semana 3 de 12 · Acumulación",
    )
    private val draftState: ProgramHeaderState = ProgramDetailSamples.headerState(
        active, ProgramHeaderStatus.DRAFT, ProgramHeaderPrimary.Activate, 12, null, null,
    )
    private val simpleState: ProgramHeaderState = ProgramDetailSamples.headerState(
        ProgramDetailSamples.simple, ProgramHeaderStatus.ACTIVE, ProgramHeaderPrimary.Continue, 1, 1,
        "Semana que se repite · ciclo 5", repeating = true,
    )

    /** El día de la semana que se despliega: lunes, con su sesión de torso hecha. */
    private val torsoDay: ProgramDayUi = ProgramDetailSamples.day(
        id = 1,
        name = "Lunes",
        short = "LUN",
        sessions = listOf(ProgramDetailSamples.sessionUi(ProgramDetailSamples.torsoA, done = true)),
        number = "13",
        today = true,
        start = true,
    )

    /** Las fases con la semana 3 en curso, como dice la cabecera: 1 y 2 hechas. */
    private val phasesOfWeek3: List<ProgramPhaseBlockUi> = ProgramDetailSamples.phaseBlocks().map { block ->
        block.copy(weeks = block.weeks.map { week -> week.copy(isCurrent = week.number == 3, isDone = week.number < 3) })
    }

    /** Lo que se dibuja ahora y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

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
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Alto de la hoja de cada escena: lo que ocupa su pantalla completa, con holgura. */
    private fun heightOf(name: String): Int = when (name) {
        "isla" -> 300
        "estructuraBloques" -> 2250
        "estructuraSimple" -> 1350
        "volumen" -> 2950
        "semana" -> 1500
        "pausado" -> 1550
        "sinActivar" -> 1000
        "cambiarReparto" -> 1700
        else -> 900
    }

    /** Las llamadas que no hacen nada en la hoja: la escena solo dibuja. */
    @Composable
    private fun dayModule(day: ProgramDayUi, expanded: Boolean) = ProgramDayModule(
        day = day,
        expandedSessionIds = if (expanded) day.sessions.map { it.session.id }.toSet() else emptySet(),
        onToggleSession = {},
        onLongPressDay = {},
        onEditDate = {},
        onAddSession = {},
        onStartSession = {},
        onEditSession = {},
        onMoreSession = { _, _ -> },
        onRestoreSession = {},
        onToggleOptional = { _, _ -> },
        draggingSessionId = null,
        insertIndex = null,
        anchorFirstSession = false,
        onDayBounds = {},
        onSessionBounds = { _, _ -> },
        onSessionDragStart = {},
        onSessionDrag = { _, _ -> },
        onSessionDragEnd = {},
        onSessionDragCancel = {},
    )

    /** Pinta la escena [name] dentro de la caja de 360 dp. */
    @Composable
    private fun Scene(name: String) {
        val tabs: @Composable (Int) -> Unit = { selected ->
            KpknSegmentedControl(
                options = programDetailTabsOptions,
                selectedIndex = selected,
                onSelect = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val controls: @Composable () -> Unit = {
            ProgramVolumeCountControls(
                indirectMode = 0,
                onIndirectMode = {},
                
                
            )
        }
        val header: @Composable (ProgramHeaderState) -> Unit = { state ->
            ProgramHeader(state = state, actions = ProgramHeaderActions(onPrimary = {}, onStartPause = {}, onEdit = {}))
        }
        val about: @Composable () -> Unit = {
            ProgramAboutModule(
                provenance = "Plan KPKN · Adaptación KPKN",
                notes = listOf("Dos series de calentamiento en cada básico."),
                onOpenPlanInfo = {},
                onOpenMore = {},
            )
        }
        val column: @Composable (@Composable () -> Unit) -> Unit = { content ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
        when (name) {
            "progreso" -> column {
                ProgramProgressModule(
                    report = ProgramProgressReport(
                        ProgressPosition("Por bloques · 3 bloques · 12 semanas", "Bloque de acumulación", "Acumulación", 3, 12, null, "Descarga en 2 semanas"),
                        92,
                        listOf(ProgramWeekAdherence("w1", "Semana 1", 4, 4), ProgramWeekAdherence("w2", "Semana 2", 4, 4), ProgramWeekAdherence("w3", "Semana 3", 4, 3)),
                        listOf(LiftTrend("Sentadilla", 140.0, 145.0), LiftTrend("Press de banca", 92.5, 95.0), LiftTrend("Peso muerto", 175.0, 180.0)),
                        listOf(DecisionLine(0, "WEEKLY_CHECKIN", "Semana ajustada", "Una serie más de dorsales")),
                    ),
                    onDecisions = {},
                )
            }
            "semana" -> column {
                header(activeState)
                about()
                tabs(0)
                ProgramPhasesModule(
                    blocks = phasesOfWeek3,
                    selectedWeekId = "w3",
                    onSelectWeek = {},
                    onLongPressWeek = { _, _ -> },
                    onAddWeek = {},
                )
                dayModule(torsoDay, expanded = true)
            }
            "isla" -> Box(Modifier.fillMaxWidth().height(300.dp)) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 100.dp)) {
                    ProgramAboutModule(
                        provenance = "Plan KPKN · Adaptación KPKN",
                        notes = listOf("Dos series de calentamiento en cada básico."),
                        onOpenPlanInfo = null,
                        onOpenMore = {},
                    )
                }
                KpknTopIsland(
                    visible = true,
                    title = active.name,
                    leading = {
                        KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = {})
                    },
                    actions = {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(programHeaderDotColor(ProgramHeaderStatus.ACTIVE)))
                        Spacer(Modifier.width(8.dp))
                        KpknIconButton(icon = KpknLineIcons.More, contentDescription = "Más opciones", onClick = {})
                    },
                )
            }
            "estructuraBloques" -> column {
                header(activeState)
                about()
                tabs(1)
                ProgramMapModule(
                    segments = programMapSegments(ProgramStructureSamples.programaCuatroBloques),
                    startLabel = "6 oct",
                    nowLabel = "Hoy · semana 4",
                    endLabel = "16 nov",
                    onDates = {},
                )
                StructureGroupLabel(text = "Bloques")
                ProgramBlockList(
                    blocks = ProgramStructureSamples.programaCuatroBloques,
                    expandedBlockId = ProgramStructureSamples.intensificacion.id,
                    progressionOf = { "Top set a RPE 8 y dos series de back-off al 90 %. Sube 2,5 kg cuando el top set sale a RPE 7 o menos." },
                    onToggleBlock = {},
                    onFocusWeek = { _, _ -> },
                    onWeekLongPress = { _, _, _ -> },
                    onBlockMore = { _, _ -> },
                    onAddWeekToBlock = { _ -> },
                )
                StructureSecondarySmallPill(text = "Añadir bloque", onClick = {}, icon = KpknLineIcons.Plus, modifier = Modifier.fillMaxWidth())
                ProgramFromProgramModule(
                    items = listOf(
                        ProgramFromProgramItem("Cargas de referencia (TM)", "4 ejercicios · falta press militar", KpknLineIcons.Target, "tm", {}),
                        ProgramFromProgramItem("Aplicar plantilla o método", "Rellena semanas con una plantilla de la biblioteca", KpknLineIcons.Book, "pl", {}),
                        ProgramFromProgramItem("Versiones guardadas", "Última: hoy 08:12, antes de aplicar la propuesta", KpknLineIcons.History, "ve", {}),
                    ),
                )
            }
            "estructuraSimple" -> column {
                header(simpleState)
                about()
                tabs(1)
                ProgramBaseWeekModule(headline = "4 sesiones · ≈ 3 h 10", trainingDays = setOf(1, 2, 3, 5), cycleLabel = null, onEdit = {})
                ProgramFromProgramModule(
                    label = "Ciclos",
                    items = listOf(
                        ProgramFromProgramItem("Descarga cada 4 semanas", "Próxima: semana 8", KpknLineIcons.Repeat, "c1", {}),
                        ProgramFromProgramItem("Test de 1RM cada 8 semanas", "Próximo: semana 8", KpknLineIcons.Target, "c2", {}),
                        ProgramFromProgramItem("Añadir ciclo", null, KpknLineIcons.Plus, "c3", {}),
                    ),
                )
                ProgramFromProgramModule(
                    label = null,
                    items = listOf(ProgramFromProgramItem("Pasar a programa por bloques", null, KpknLineIcons.Training, "pa", {})),
                )
            }
            "volumen" -> column {
                header(activeState)
                about()
                tabs(2)
                ProgramVolumeModule(
                    muscles = ProgramDetailSamples.volumeWeek,
                    scope = ProgramDetailSamples.volumeScopeWeek,
                    onSelectKind = {},
                    onPreviousScope = {},
                    onNextScope = {},
                    expandedMuscle = null,
                    onToggleMuscle = {},
                    breakdown = null,
                    controls = controls,
                )
                ProgramVolumeSummaryModule(
                    totalSets = 96.0,
                    activeMuscles = 12,
                    topMuscle = "Pectorales",
                    split = ProgramVolumeSplit(push = 40.0, pull = 35.0, legs = 25.0),
                    discomforts = emptyList(),
                    associations = emptyList(),
                )
            }
            "sinActivar" -> column {
                header(draftState)
                ProgramAboutModule(
                    provenance = "Plan KPKN · Adaptación KPKN",
                    notes = listOf("Dos series de calentamiento en cada básico."),
                    onOpenPlanInfo = {},
                    onOpenMore = {},
                )
                ProgramVolumeInactive(onActivate = {})
            }
            "pausado" -> column {
                header(pausedState)
                about()
                tabs(0)
                ProgramPhasesModule(
                    blocks = phasesOfWeek3,
                    selectedWeekId = "w3",
                    onSelectWeek = {},
                    onLongPressWeek = { _, _ -> },
                    onAddWeek = {},
                )
                dayModule(torsoDay, expanded = false)
            }
            "cambiarReparto" -> {
                val catalog = listOf("ul_x4", "ppl_ul", "ppl_x6", "bro_split").map { id ->
                    SPLIT_TEMPLATES.first { it.id == id }
                }
                SplitCatalogScreen(
                    current = catalog.first(),
                    startDay = 1,
                    query = "",
                    onQueryChange = {},
                    selectedTag = null,
                    onSelectTag = {},
                    splits = catalog,
                    customSplit = null,
                    unavailableReason = { null },
                    isCurrent = { split -> split.id == catalog.first().id },
                    mode = SplitCatalogMode.BROWSE,
                    selectedIds = emptyList(),
                    onBack = {},
                    onToggleCompareMode = {},
                    onToggleMultiMode = {},
                    onApply = {},
                    onToggleSelect = {},
                    onEditCustom = {},
                    onApplyCustom = {},
                    onCompare = {},
                    onApplySelected = {},
                )
            }
        }
    }

    @Test
    fun renderProgramDetailClosureScenes() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Box(
                    Modifier
                        .testTag("sheet")
                        .width(360.dp)
                        .height(heightOf(cases[current]).dp)
                        .background(Color.Black),
                ) {
                    Scene(cases[current])
                }
            }
        }
        for ((index, name) in cases.withIndex()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    current = index
                    fontScale = scale
                }
                rule.waitForIdle()
                save("${name}_$suffix")
            }
        }
    }
}
