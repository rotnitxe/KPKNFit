package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.ExerciseTechniqueImageVariant
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.domain.exercises.VariantGroup
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.CatalogSelectionWizardContent
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import com.example.kpkn.screens.sessioneditor.components.CatalogDetailAxis
import com.example.kpkn.screens.sessioneditor.components.CatalogDetailContent
import com.example.kpkn.screens.sessioneditor.components.CatalogDetailModel
import com.example.kpkn.screens.sessioneditor.components.CatalogDetailOption
import com.example.kpkn.screens.sessioneditor.components.SessionExerciseDetailSheet
import com.example.kpkn.screens.sessioneditor.design.CatalogSupersetConfiguratorContent
import com.example.kpkn.screens.sessioneditor.design.SmartExerciseEditorPage
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

/**
 * HOJA DE DIBUJO de la ficha, la superserie, el editor de ejercicio propio y el asistente de variantes (SE6b). No comprueba nada: escribe
 * PNG. Pinta los cuerpos sueltos con datos de ejemplo, a 360 dp, al 100 % y al 130 %, sin movimiento. Solo corre con `KPKN_RENDER_DIR`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class CatalogDetailRenderSheetTest {

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

    /** Los casos de la hoja, en orden. */
    private val cases = listOf(
        "ficha_3_implementos",
        "ficha_1_implemento",
        "ficha_7_musculos",
        "ficha_sin_imagen",
        "crear_ejercicio",
        "configurar_superserie",
        "asistente_paso",
        "ficha_desde_editor",
    )

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Alto de cada caso: la ficha entera cabe en 1500 dp; el editor es el más alto. */
    private fun heightOf(name: String): Int = when (name) {
        "hoy_3_implementos", "hoy_musculos_desplegados", "ficha_3_implementos", "ficha_7_musculos", "ficha_desde_editor" -> 1500
        "ficha_1_implemento", "ficha_sin_imagen" -> 1300
        "crear_ejercicio" -> 2600
        "configurar_superserie" -> 1100
        else -> 900
    }

    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
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

    // Datos de ejemplo: un press de banca con barra, sus músculos, sus articulaciones y su grupo de variantes.

    private val sampleMuscles = listOf(
        InvolvedMuscle(muscle = "Pectorales", role = MuscleRole.PRIMARY, volumeContribution = 1.0),
        InvolvedMuscle(muscle = "Deltoides", role = MuscleRole.SECONDARY, volumeContribution = 0.5),
        InvolvedMuscle(muscle = "Tríceps", role = MuscleRole.SECONDARY, volumeContribution = 0.4),
    )

    private val sampleInfo: ExerciseMuscleInfo by lazy {
        ExerciseMuscleInfo(
            id = "press-banca",
            name = "Press banca",
            description = "Empuje horizontal del tren superior con barra.",
            involvedMuscles = sampleMuscles,
        )
    }

    /** Siete músculos con aportes distintos: el módulo muestra cuatro y «Ver todos» el resto. */
    private val sampleSevenMuscles: ExerciseMuscleInfo by lazy {
        sampleInfo.copy(
            involvedMuscles = listOf(
                InvolvedMuscle(muscle = "Pectorales", role = MuscleRole.PRIMARY, volumeContribution = 1.0),
                InvolvedMuscle(muscle = "Deltoides", role = MuscleRole.SECONDARY, volumeContribution = 0.8),
                InvolvedMuscle(muscle = "Tríceps", role = MuscleRole.SECONDARY, volumeContribution = 0.6),
                InvolvedMuscle(muscle = "Dorsales", role = MuscleRole.STABILIZER, volumeContribution = 0.5),
                InvolvedMuscle(muscle = "Trapecio", role = MuscleRole.STABILIZER, volumeContribution = 0.4),
                InvolvedMuscle(muscle = "Bíceps", role = MuscleRole.NEUTRALIZER, volumeContribution = 0.3),
                InvolvedMuscle(muscle = "Romboides", role = MuscleRole.NEUTRALIZER, volumeContribution = 0.2),
            ),
        )
    }

    private val sampleVariants: List<ExerciseMuscleInfo> by lazy {
        listOf(
            sampleInfo.copy(id = "press-barra", variantName = "Con barra", variantGroupId = "press"),
            sampleInfo.copy(id = "press-mancuernas", variantName = "Con mancuernas", variantGroupId = "press"),
            sampleInfo.copy(id = "press-smith", variantName = "En Smith", variantGroupId = "press"),
        )
    }

    private val sampleGroup: VariantGroup by lazy {
        VariantGroup(id = "press", name = "Press", variants = sampleVariants, sharedAspects = emptyList())
    }

    private val sampleImages by lazy {
        ExerciseTechniqueImageLookup.variants(ExerciseTechniqueImageLookup.FLAT_BENCH_PRESS_DEFINITION_ID)
    }

    private val sampleJoints = listOf(
        JointInvolvementV2(jointId = "glenohumeral", role = JointRoleV2.PRIMARY, actions = listOf("flexion", "aducción")),
        JointInvolvementV2(jointId = "codo", role = JointRoleV2.SECONDARY, actions = listOf("extensión")),
    )

    private val sampleDescription =
        "Empuje horizontal del tren superior con barra. Escápulas juntas y bajada controlada hasta el pecho. " +
            "El codo baja en diagonal, no pegado al cuerpo, y la barra toca la parte baja del pecho sin rebotar."

    /** Press de banca con su vínculo al catálogo v2 (definición y configuración): la ficha abierta desde el editor (ER1f). */
    private val sampleEditorExercise: Exercise by lazy {
        Exercise(
            id = "press-editor",
            name = "Press banca",
            catalogDefinitionId = ExerciseTechniqueImageLookup.FLAT_BENCH_PRESS_DEFINITION_ID,
            catalogConfigurationId = "bench_press__barbell",
            sets = listOf(ExerciseSet(id = "press-editor-1", targetReps = 6, targetRepsRange = RepRange(4, 6), targetRIR = 2)),
        )
    }

    /** Barra, mancuerna y máquina: los tres implementos de la muestra, con sus nombres de la configuración. */
    private val sampleImplementOptions = listOf(
        CatalogDetailOption(value = "barbell", label = "Barra", selected = false, enabled = true),
        CatalogDetailOption(value = "dumbbells", label = "Mancuerna", selected = false, enabled = true),
        CatalogDetailOption(value = "machine", label = "Máquina", selected = false, enabled = true),
    )

    private fun sampleModel(
        images: List<ExerciseTechniqueImageVariant> = sampleImages.filter { it.implementation in setOf("barbell", "dumbbells", "machine") },
        selected: String? = "dumbbells",
        implementOptions: List<CatalogDetailOption> = sampleImplementOptions,
        muscles: ExerciseMuscleInfo = sampleInfo,
    ) = CatalogDetailModel(
        name = "Press banca",
        caption = "Pectorales · ${implementOptions.firstOrNull { it.value == (selected ?: "barbell") }?.label ?: "Barra"} · Empuje horizontal",
        description = sampleDescription,
        imageVariants = images,
        selectedImplementation = selected,
        axes = listOf(
            CatalogDetailAxis(
                axis = "implement",
                label = "Implemento",
                options = implementOptions.map { it.copy(selected = it.value == selected) },
            ),
            CatalogDetailAxis(
                axis = "grip",
                label = "Agarre",
                options = listOf(
                    CatalogDetailOption(value = "pronated", label = "Prono", selected = true, enabled = true),
                    CatalogDetailOption(value = "neutral", label = "Neutro", selected = false, enabled = true),
                ),
            ),
        ),
        configurationLine = "Barra · Agarre prono",
        configurationIncomplete = false,
        muscleInfo = muscles,
        joints = sampleJoints,
        patternId = null,
        isSelected = false,
        canSelect = true,
    )

    /** El caso [name], con sus datos de ejemplo. Sin estado: todo llega por parámetros. */
    @Composable
    private fun Case(name: String) {
        when (name) {
            "hoy_3_implementos" -> CatalogDetailContent(
                model = sampleModel(),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
            "hoy_musculos_desplegados" -> CatalogDetailContent(
                model = sampleModel(),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
                startExpanded = true,
            )
            "ficha_3_implementos" -> CatalogDetailContent(
                model = sampleModel(),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
            "ficha_1_implemento" -> CatalogDetailContent(
                model = sampleModel(
                    images = sampleImages.filter { it.implementation == "barbell" },
                    selected = "barbell",
                    implementOptions = sampleImplementOptions.take(1),
                ),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
            "ficha_sin_imagen" -> CatalogDetailContent(
                model = sampleModel(images = emptyList(), selected = null),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
            "ficha_7_musculos" -> CatalogDetailContent(
                model = sampleModel(selected = "barbell", muscles = sampleSevenMuscles),
                onDismiss = {},
                onToggleSelection = {},
                onSelectOption = { _, _ -> },
                onImplementationSettled = {},
            )
            "crear_ejercicio" -> SmartExerciseEditorPage(
                exercise = sampleInfo,
                onSave = {},
                onClose = {},
            )
            "configurar_superserie" -> Column(Modifier.fillMaxWidth()) {
                CatalogSupersetConfiguratorContent(
                    selectedExercises = listOf(sampleInfo, sampleInfo.copy(id = "remo", name = "Remo con barra")),
                    initialConfig = CatalogSupersetConfig(rounds = 3, restBetweenExercisesSeconds = 60, restAfterSupersetSeconds = 120),
                    onDismiss = {},
                    onConfirm = {},
                )
            }
            "ficha_desde_editor" -> SessionExerciseDetailSheet(exercise = sampleEditorExercise, onDismiss = {})
            else -> CatalogSelectionWizardContent(
                group = sampleGroup,
                initialExercise = sampleVariants[0],
                onConfirm = { _, _ -> },
                onDismiss = {},
            )
        }
    }

    @Test
    fun renderCatalogDetail() {
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
                    Case(cases[current])
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
                save("sheet", "${name}_$suffix")
            }
        }
    }
}
