package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.importer.ocr.OcrImportSource
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.ImportWarning
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.design.ProgramDayModule
import com.example.kpkn.screens.programs.ProgramsHeader
import com.example.kpkn.screens.programs.importer.ImportProgramScreen
import com.example.kpkn.screens.programs.importer.ImportProgramState
import com.example.kpkn.screens.programs.importer.ImportProgramViewModel
import com.example.kpkn.screens.programs.importer.ImportWarningsPanel
import com.example.kpkn.screens.programs.importer.ImportWeekdaysSection
import com.example.kpkn.screens.programs.importer.importInitialWeekdays
import com.example.kpkn.ui.entitlements.LocalPlan
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

/**
 * HOJAS DE DIBUJO de la importación en la app (no comprueba nada: escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se
 * salta. A 360 dp, con la letra al 100 % y al 130 %: la vista previa con la barra de guardar al final, los avisos plegados y
 * desplegados, los días de entrenamiento, la cabecera de Programas y una tarjeta de sesión con título largo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class ImportAppRenderTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        /** Tres días con un ejercicio cada uno: la semana de la vista previa. */
        private const val PROGRAMA = "Día 1 — Pierna\nSentadilla 4x6 @120kg\nRemo con barra 3x10 @50kg\n" +
            "Día 2 — Empuje\nPress banca 4x8 @80kg\nDía 3 — Tirón\nDominadas 3x8"
    }

    @get:Rule
    val rule = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    /** Cada VM va al store de la prueba: [tearDown] lo limpia y cancela su viewModelScope antes de que acabe la prueba. */
    private val viewModelStore = ViewModelStore()
    private var nextViewModelKey = 0

    private fun newViewModel(): ImportProgramViewModel =
        ImportProgramViewModel(app).also { vm -> viewModelStore.put("import-${nextViewModelKey++}", vm) }

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    /** La vista de Compose de la actividad, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Un recuadro de la hoja: fondo negro, el ancho pedido y [pad] de margen (como la pantalla, 16 dp). */
    @Composable
    private fun Sheet(width: Dp, fontScale: Float, pad: Dp = 16.dp, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        hostView = LocalView.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
            // Con el tema de la app: sin él, el texto sale con el esquema claro por defecto y no se ve sobre el negro.
            KPKNTheme {
                Box(Modifier.testTag("sheet").wrapContentSize().background(Color.Black)) {
                    Box(Modifier.width(width).padding(horizontal = pad, vertical = pad)) {
                        content()
                    }
                }
            }
        }
    }

    /** La pantalla entera, como en la app: la vista previa, la revisión y la barra de abajo. */
    @Composable
    private fun FullScreen(vm: ImportProgramViewModel, fontScale: Float) {
        val base = LocalDensity.current
        hostView = LocalView.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
            KPKNTheme {
                Box(Modifier.testTag("sheet").fillMaxSize().background(Color.Black)) {
                    CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                        ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
                    }
                }
            }
        }
    }

    /** Cuatro avisos sin revisar y el de la imagen: el panel empieza plegado. */
    private fun avisosDeEjemplo(): ImportedProgramDraft = ImportedProgramDraft(
        title = "Plan",
        weeks = listOf(
            ImportedWeek(
                1,
                listOf(
                    ImportedDay("w1-d1", "Pierna", listOf(ImportedExercise("w1-d1-e1", "Sentadilla", sets = 4, repsMin = 6, repsMax = 8, loadKg = 120.0))),
                    ImportedDay("w1-d2", "Empuje", listOf(ImportedExercise("w1-d2-e1", "Press banca", sets = 4, repsMin = 8, repsMax = 8))),
                ),
            ),
        ),
        warnings = listOf(
            ImportWarning(
                id = "a1",
                kind = ImportWarningKind.SKIPPED_ROW,
                location = "Hoja «Pierna», fila 3",
                sourceText = "Remo con barra · 3 · 10",
                message = "Hoja «Pierna», fila 3: «Remo con barra» sin repeticiones; se omite",
                weekIndex = 1,
                dayId = "w1-d1",
                exerciseId = null,
            ),
            ImportWarning(
                id = "a2",
                kind = ImportWarningKind.IGNORED_VALUE,
                location = "Hoja «Pierna», fila 2",
                sourceText = "Sentadilla · 4 · 6 · 120 kg · RPE 12",
                message = "Hoja «Pierna», fila 2: el RPE de «Sentadilla» está fuera de rango; se ignora",
                weekIndex = 1,
                dayId = "w1-d1",
                exerciseId = "w1-d1-e1",
            ),
            ImportWarning(
                id = "a3",
                kind = ImportWarningKind.EMPTY_DAY,
                location = "Hoja «Semana 2», fila 4",
                sourceText = "Descanso",
                message = "«Descanso» no tiene ejercicios; se omite",
                weekIndex = 2,
                dayId = null,
                exerciseId = null,
            ),
            ImportWarning(
                id = "a4",
                kind = ImportWarningKind.SHEET_NOTE,
                location = "Hoja «Guía»",
                sourceText = null,
                message = "Hoja «Guía»: se omitieron 2 líneas de texto.",
                weekIndex = null,
                dayId = null,
                exerciseId = null,
            ),
            ImportWarning(
                id = "a5",
                kind = ImportWarningKind.SHEET_NOTE,
                location = "Hoja «Foto»",
                sourceText = null,
                message = "Hoja «Foto»: ${OcrImportSource.IMAGE_NOTE}",
                weekIndex = null,
                dayId = null,
                exerciseId = null,
            ),
        ),
    )

    private fun settle(vm: ImportProgramViewModel, done: (ImportProgramState) -> Boolean) {
        repeat(600) {
            ShadowLooper.idleMainLooper()
            if (done(vm.state.value)) return
            Thread.sleep(10)
        }
        error("la importación no terminó: ${vm.state.value}")
    }

    private fun previewConBarraDeGuardar(name: String, fontScale: Float) {
        onlyWhenAsked()
        val vm = newViewModel()
        vm.importFromText(PROGRAMA)
        settle(vm) { it.imported != null && !it.busy }
        decideAll(vm)
        rule.setContent { FullScreen(vm, fontScale) }
        save(name)
    }

    /** Deja «propio» todo lo que el emparejador no decidió solo, como haría la persona con «Dejarlo como está». */
    private fun decideAll(vm: ImportProgramViewModel) {
        val imported = vm.state.value.imported ?: error("sin programa importado")
        imported.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
            .map { it.rawName }
            .distinct()
            .filterNot { vm.state.value.resolutions.containsKey(it) }
            .forEach { vm.resolve(it, null) }
    }

    private fun avisos(name: String, fontScale: Float, abrir: Boolean) {
        onlyWhenAsked()
        val draft = avisosDeEjemplo()
        rule.setContent {
            Sheet(width = 360.dp, fontScale = fontScale) {
                Column {
                    ImportWarningsPanel(
                        imported = draft,
                        reveal = 0,
                        search = { emptyList() },
                        onOpenExercise = {},
                        onEdit = {},
                    )
                }
            }
        }
        if (abrir) rule.onNodeWithTag("import-warnings-see").performClick()
        save(name)
    }

    @Test
    fun laVistaPreviaConLaBarraDeGuardarAl360() = previewConBarraDeGuardar("a_vista_previa_barra_360", 1f)

    @Test
    fun laVistaPreviaConLaBarraDeGuardarAl360ConLetraAl130() =
        previewConBarraDeGuardar("a2_vista_previa_barra_360_letra_130", 1.3f)

    @Test
    fun losAvisosPlegadosConLaImagenVisible() = avisos("b_avisos_plegados_360", fontScale = 1f, abrir = false)

    @Test
    fun losAvisosPlegadosAl130() = avisos("b2_avisos_plegados_360_letra_130", fontScale = 1.3f, abrir = false)

    @Test
    fun losAvisosDesplegadosAl360() = avisos("c_avisos_desplegados_360", fontScale = 1f, abrir = true)

    @Test
    fun losDiasDeEntrenamientoAl360() {
        onlyWhenAsked()
        rule.setContent {
            Sheet(width = 360.dp, fontScale = 1f, pad = 0.dp) {
                ImportWeekdaysSection(selected = importInitialWeekdays(3), sessionsPerWeek = 3, onToggle = {})
            }
        }
        save("d_dias_360")
    }

    @Test
    fun losDiasDeEntrenamientoAl360ConLetraAl130() {
        onlyWhenAsked()
        rule.setContent {
            Sheet(width = 360.dp, fontScale = 1.3f, pad = 0.dp) {
                ImportWeekdaysSection(selected = importInitialWeekdays(3), sessionsPerWeek = 3, onToggle = {})
            }
        }
        save("d2_dias_360_letra_130")
    }

    private fun cabecera(name: String, fontScale: Float) {
        onlyWhenAsked()
        rule.setContent {
            Sheet(width = 360.dp, fontScale = fontScale, pad = 0.dp) {
                ProgramsHeader(
                    onNew = {},
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
        }
        save(name)
    }

    @Test
    fun laCabeceraDeProgramasAl360() = cabecera("e_cabecera_programas_360", 1f)

    @Test
    fun laCabeceraDeProgramasAl360ConLetraAl130() = cabecera("e2_cabecera_programas_360_letra_130", 1.3f)

    private fun tarjetaDeSesion(name: String, fontScale: Float) {
        onlyWhenAsked()
        val sesion = Session(
            id = "s2",
            name = "Day 2 - Upper body strength and hypertrophy focus",
            exercises = listOf(Exercise(id = "e1", name = "Press banca"), Exercise(id = "e2", name = "Remo con barra")),
        )
        // La fila de la sesión la pinta ahora el módulo del día (P7 retira SessionCard): un día con esa sesión, desplegado.
        val day = ProgramDetailSamples.day(
            id = 2,
            name = "Martes",
            short = "MAR",
            sessions = listOf(ProgramDetailSamples.sessionUi(sesion)),
            number = "14",
        )
        rule.setContent {
            Sheet(width = 360.dp, fontScale = fontScale) {
                ProgramDayModule(
                    day = day,
                    expandedSessionIds = day.sessions.map { it.session.id }.toSet(),
                    onToggleSession = {},
                    onLongPressDay = {},
                    onEditDate = {},
                    onAddSession = {},
                    onStartSession = {},
                    onEditSession = {},
                    onMoreSession = { _, _ -> },
                    onRestoreSession = {},
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
            }
        }
        save(name)
    }

    @Test
    fun unaTarjetaDeSesionConTituloLargoAl360() = tarjetaDeSesion("f_tarjeta_sesion_titulo_largo_360", 1f)

    @Test
    fun unaTarjetaDeSesionConTituloLargoAl360ConLetraAl130() =
        tarjetaDeSesion("f2_tarjeta_sesion_titulo_largo_360_letra_130", 1.3f)

    /** Dibuja la vista de la actividad y recorta el recuadro «sheet» a `KPKN_RENDER_DIR/<name>.png`. */
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
}
