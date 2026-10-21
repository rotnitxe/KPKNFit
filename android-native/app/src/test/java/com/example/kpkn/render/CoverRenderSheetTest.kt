package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanEditorialTable
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.periodization.ProgramVariant
import com.example.kpkn.screens.onboarding.design.entreno.plan.CoverLayout
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCover
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverIdentity
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.assignedCoverIdentity
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.assignedCoverProgramIds
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE DIBUJO de las portadas con identidad (`CoverStudio`): no comprueba nada, escribe PNG. Solo corre con `KPKN_RENDER_DIR`; sin
 * ella se salta. Cada hoja sale a escala de letra 100 % y 130 %: `k3a_contacto_30.png` (las 30 portadas a medida a 260 dp, una fila por
 * perfil y tres columnas: semana simple, bloques y periodizada), `k3a_detalle_estilos.png` (un programa por estilo a 300 dp) y
 * `k3a_bandas.png` (la banda ancha de tres estilos, como en la cabecera del detalle). Las versiones a 130 % llevan `_letra130`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h3800dp-mdpi", application = android.app.Application::class)
class CoverRenderSheetTest {

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

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private var hostView: View? = null

    /** La tarjeta de un programa a medida: título real de la tabla editorial y datos de ejemplo. */
    private fun modelOf(profile: TrainingGoalProfile, variant: ProgramVariant): PlanCardModel {
        val id = GeneratedPlans.entryIdFor(profile, variant)
        val kicker = when (variant) {
            ProgramVariant.SIMPLE -> "Semana que se repite"
            ProgramVariant.BLOCKS -> "6 semanas · 2 bloques"
            ProgramVariant.PERIODIZED -> "12 semanas · periodizado"
        }
        return PlanCardModel(
            id = id,
            title = PlanEditorialTable.forId(id).displayName,
            kicker = kicker,
            blurb = "",
            profile = profile,
            daysLabel = "4 días",
            minutesLabel = "60 min",
            levelLabel = "Intermedio",
            coverSeed = 0,
        )
    }

    /** Un programa por tarjeta con su id, para las hojas de detalle. */
    private fun modelOfId(id: String, profile: TrainingGoalProfile): PlanCardModel = PlanCardModel(
        id = id,
        title = PlanEditorialTable.forId(id).displayName,
        kicker = "Plan a medida",
        blurb = "",
        profile = profile,
        daysLabel = "4 días",
        minutesLabel = "60 min",
        levelLabel = "Intermedio",
        coverSeed = 0,
    )

    /** Las 30 portadas: una fila por perfil con sus tres variantes, a 260 dp de ancho en 3:4. */
    @Composable
    private fun Contact() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (profile in TrainingGoalProfile.entries) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (variant in ProgramVariant.entries) {
                        PlanCover(
                            model = modelOf(profile, variant),
                            modifier = Modifier.width(260.dp).aspectRatio(3f / 4f),
                            reduced = true,
                        )
                    }
                }
            }
        }
    }

    /** Un programa por estilo, a 300 dp de ancho. */
    @Composable
    private fun StyleDetail() {
        val models = listOf(
            modelOfId("generated:strength-muscle:periodized", TrainingGoalProfile.STRENGTH_MUSCLE),
            modelOfId("generated:hybrid:periodized", TrainingGoalProfile.STRENGTH_CARDIO),
            modelOfId("generated:strength-muscle", TrainingGoalProfile.STRENGTH_MUSCLE),
            modelOfId("generated:strength-muscle:blocks", TrainingGoalProfile.STRENGTH_MUSCLE),
            modelOfId("generated:hybrid", TrainingGoalProfile.STRENGTH_CARDIO),
        )
        // En columna: cinco carteles de 300 dp en fila no caben en la ventana de la prueba y la hoja se recortaba.
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (model in models) PlanCover(model, Modifier.width(300.dp).aspectRatio(3f / 4f), reduced = true)
        }
    }

    /** La banda ancha (cabecera del detalle) de tres estilos, con el recorte alrededor de su motivo. */
    @Composable
    private fun Bands() {
        val models = listOf(
            modelOfId("generated:hybrid:periodized", TrainingGoalProfile.STRENGTH_CARDIO),
            modelOfId("generated:strength-muscle:periodized", TrainingGoalProfile.STRENGTH_MUSCLE),
            modelOfId("generated:hybrid", TrainingGoalProfile.STRENGTH_CARDIO),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (model in models) {
                PlanCover(
                    model = model,
                    modifier = Modifier.width(360.dp).height(200.dp),
                    reduced = true,
                    cornerRadius = 0.dp,
                    layout = CoverLayout.BANNER,
                )
            }
        }
    }

    /**
     * Pinta [content] dentro de la caja etiquetada [tag], a densidad 1 y con la escala de letra [fontScale] (100 % o 130 %), y la guarda
     * como [name].png en `KPKN_RENDER_DIR`.
     */
    private fun shoot(name: String, fontScale: Float, tag: String, content: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                Column(Modifier.background(Color.Black)) {
                    Box(Modifier.testTag(tag)) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { SymbolRenderSheet.savePng(hostView!!, bounds, name) }
    }

    @Test
    fun renderTheContactSheet() {
        onlyWhenAsked()
        shoot("k3a_contacto_30", 1f, "contacto") { Contact() }
    }

    @Test
    fun renderTheContactSheetAt130() {
        onlyWhenAsked()
        shoot("k3a_contacto_30_letra130", 1.3f, "contacto") { Contact() }
    }

    @Test
    fun renderTheStyleDetail() {
        onlyWhenAsked()
        shoot("k3a_detalle_estilos", 1f, "detalle") { StyleDetail() }
    }

    @Test
    fun renderTheStyleDetailAt130() {
        onlyWhenAsked()
        shoot("k3a_detalle_estilos_letra130", 1.3f, "detalle") { StyleDetail() }
    }

    @Test
    fun renderTheBands() {
        onlyWhenAsked()
        shoot("k3a_bandas", 1f, "bandas") { Bands() }
    }

    @Test
    fun renderTheBandsAt130() {
        onlyWhenAsked()
        shoot("k3a_bandas_letra130", 1.3f, "bandas") { Bands() }
    }

    /** Estilos nuevos de K3b: trama, pista, ruta, grabado y formas planas. */
    private val newStyles = listOf(
        CoverStudioStyle.HALFTONE,
        CoverStudioStyle.TRACK,
        CoverStudioStyle.TRAIL,
        CoverStudioStyle.ENGRAVING,
        CoverStudioStyle.BAUHAUS,
    )

    /** Una tarjeta con su título real; sin perfil, la portada pone el acento de respaldo (ok). */
    private fun coverModel(id: String, profile: TrainingGoalProfile?, kicker: String): PlanCardModel = PlanCardModel(
        id = id,
        title = PlanEditorialTable.forId(id).displayName,
        kicker = kicker,
        blurb = "",
        profile = profile,
        daysLabel = "4 días",
        minutesLabel = "60 min",
        levelLabel = "Intermedio",
        coverSeed = 0,
    )

    /** Las portadas de una disciplina, en 3:4 a 200 dp, cuatro por fila. */
    @Composable
    private fun DisciplineSheet(models: List<PlanCardModel>) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            models.chunked(4).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    line.forEach { model -> PlanCover(model, Modifier.width(200.dp).aspectRatio(3f / 4f), reduced = true) }
                }
            }
        }
    }

    /** Las portadas de una disciplina: sus tres «a medida» (si tiene) y los programas listados con esa referencia, por orden editorial. */
    private fun disciplineModels(profile: TrainingGoalProfile?, reference: TrainingReference?): List<PlanCardModel> {
        val generated = if (profile == null) {
            emptyList()
        } else {
            ProgramVariant.entries.map { coverModel(GeneratedPlans.entryIdFor(profile, it), profile, "Plan a medida") }
        }
        val listed = PersonalizedPlanCatalog.listedEntries()
            .filter { if (reference == null) it.references.isEmpty() else reference in it.references }
            .sortedBy { it.rank }
            .map { coverModel(it.id, profile, "Biblioteca") }
        return generated + listed
    }

    @Test
    fun renderK3bPowerliftingSheet() {
        onlyWhenAsked()
        val models = disciplineModels(TrainingGoalProfile.POWERLIFTING, TrainingReference.POWERLIFTING)
        shoot("k3b_disciplina_powerlifting", 1f, "disciplina") { DisciplineSheet(models) }
    }

    @Test
    fun renderK3bPowerbuildingSheet() {
        onlyWhenAsked()
        val models = disciplineModels(TrainingGoalProfile.POWERBUILDING, TrainingReference.POWERBUILDING)
        shoot("k3b_disciplina_powerbuilding", 1f, "disciplina") { DisciplineSheet(models) }
    }

    @Test
    fun renderK3bCulturismoSheet() {
        onlyWhenAsked()
        val models = disciplineModels(TrainingGoalProfile.BODYBUILDING, TrainingReference.HYPERTROPHY)
        shoot("k3b_disciplina_culturismo", 1f, "disciplina") { DisciplineSheet(models) }
    }

    @Test
    fun renderK3bSinDisciplinaSheet() {
        onlyWhenAsked()
        val models = disciplineModels(null, null)
        shoot("k3b_sin_disciplina", 1f, "disciplina") { DisciplineSheet(models) }
    }

    /** Una portada real de [style] de la tabla: su acento, su motivo y su semilla. */
    private fun styleSample(style: CoverStudioStyle): CoverIdentity =
        assignedCoverProgramIds.sorted().map { assignedCoverIdentity(it)!! }.first { it.style == style }

    /** Las cinco portadas nuevas, solo el arte, grande (300 dp) y en sus tres paletas: la fila es el estilo. */
    @Composable
    private fun NewStyleArt() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (style in newStyles) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (palette in CoverPaletteKind.entries) {
                        CoverStudioArt(
                            identity = styleSample(style).copy(palette = palette),
                            modifier = Modifier.width(300.dp).aspectRatio(3f / 4f),
                            reduced = true,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun renderK3bNewStyleArt() {
        onlyWhenAsked()
        shoot("k3b_estilos_arte", 1f, "estilos") { NewStyleArt() }
    }

    /** Las portadas completas, con su título real, de cada estilo nuevo: hasta tres por estilo. */
    @Composable
    private fun NewStyleCovers() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (style in newStyles) {
                val ids = assignedCoverProgramIds.sorted().filter { assignedCoverIdentity(it)!!.style == style }.take(3)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (id in ids) PlanCover(coverModel(id, null, "Portada"), Modifier.width(300.dp).aspectRatio(3f / 4f), reduced = true)
                }
            }
        }
    }

    @Test
    fun renderK3bNewStyleCovers() {
        onlyWhenAsked()
        shoot("k3b_estilos_portadas", 1f, "estilos") { NewStyleCovers() }
    }
}
