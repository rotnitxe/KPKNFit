package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.screens.body.BODY_SAMPLE_BODY_FAT
import com.example.kpkn.screens.body.BODY_SAMPLE_MUSCLE
import com.example.kpkn.screens.body.bodySampleBmi
import com.example.kpkn.screens.body.bodySampleFatKg
import com.example.kpkn.screens.body.bodySampleFfmi
import com.example.kpkn.screens.body.bodySampleGoalDraft
import com.example.kpkn.screens.body.bodySampleLeanKg
import com.example.kpkn.screens.body.bodySampleResolvedGoal
import com.example.kpkn.screens.body.bodySampleSchedule
import com.example.kpkn.screens.body.design.BodyCompositionModule
import com.example.kpkn.screens.body.design.BodyGoalModule
import com.example.kpkn.screens.body.design.BodyGoalSheetContent
import com.example.kpkn.screens.body.design.BodyInfoSheetContent
import com.example.kpkn.screens.body.design.BodyInfoTopic
import com.example.kpkn.screens.body.design.BodyReminderModule
import com.example.kpkn.screens.body.design.bodyInfoFor
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJAS DE DIBUJO de los módulos inferiores de Cuerpo: composición, objetivo, su hoja, la explicación y el recordatorio (no comprueba nada:
 * escribe PNG). Solo corre con `KPKN_RENDER_DIR`; sin ella se salta. 360 dp a densidad 3, al 100 % y al 130 % de letra, con los datos de
 * ejemplo de `screens/body/BodySamples.kt`. Las hojas se dibujan sobre un panel del color de hoja, porque el vidrio solo se ve en la app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class BodyPageLowerRenderSheetTest {

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

    /** Vista de la prueba: se dibuja a un bitmap, como en las demás hojas. */
    private var hostView: View? = null

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private fun guardar(nombre: String) {
        val bounds = rule.onNodeWithTag("pagina").fetchSemanticsNode().boundsInRoot
        val vista = hostView!!
        rule.runOnUiThread { SymbolRenderSheet.savePng(vista, bounds, nombre) }
    }

    /** La escala de letra del teléfono: misma densidad, con [escala] en el texto. */
    @Composable
    private fun AlTamano(escala: Float, contenido: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, escala)) { contenido() }
    }

    /** Monta [contenido] sobre el negro de la página, con la etiqueta «pagina» que delimita el PNG. */
    private fun montar(escala: Float = 1f, contenido: @Composable () -> Unit) {
        rule.setContent {
            hostView = LocalView.current
            AlTamano(escala) {
                Box(Modifier.fillMaxWidth().background(KpknSurface.canvas)) {
                    Column(Modifier.fillMaxWidth().testTag("pagina").padding(vertical = 8.dp)) {
                        contenido()
                    }
                }
            }
        }
    }

    /** Panel de hoja: el color de hoja con el radio superior, sin vidrio. */
    @Composable
    private fun PanelHoja(contenido: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(KpknShape.sheet)
                .background(KpknSurface.module),
        ) {
            contenido()
        }
    }

    @Composable
    private fun ModuloComposicion(conAltura: Boolean, conComposicion: Boolean = true) {
        BodyCompositionModule(
            weightKg = 64.5,
            leanKg = if (conComposicion) bodySampleLeanKg() else null,
            fatKg = if (conComposicion) bodySampleFatKg() else null,
            bodyFatPercent = if (conComposicion) BODY_SAMPLE_BODY_FAT else null,
            musclePercent = if (conComposicion) BODY_SAMPLE_MUSCLE else null,
            ffmi = if (conAltura && conComposicion) bodySampleFfmi() else null,
            bmi = if (conAltura) bodySampleBmi() else null,
            hasHeight = conAltura,
            onAddHeight = {},
            onInfo = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    @Composable
    private fun ModuloObjetivo(conMeta: Boolean, conPlan: Boolean) {
        BodyGoalModule(
            goal = if (conMeta) bodySampleResolvedGoal() else null,
            weightUnit = WizardMassUnit.KG,
            activePlan = conPlan,
            onEditInPlan = {},
            onEditOwn = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    @Composable
    private fun ModuloRecordatorio(schedule: MeasurementSchedule, otra: Boolean) {
        BodyReminderModule(
            schedule = schedule,
            customInterval = otra,
            onCustomIntervalChange = {},
            onUpdate = {},
            onEditTime = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    @Composable
    private fun FilaIconos() {
        KpknModule(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            KpknModuleHeader(label = "Iconos de cantidad y de cifra")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                listOf(
                    KpknLineIcons.Minus,
                    KpknLineIcons.Plus,
                    KpknLineIcons.Info,
                    KpknLineIcons.Person,
                    KpknLineIcons.Clock,
                    KpknLineIcons.Bell,
                ).forEach { icono ->
                    Icon(
                        imageVector = icono,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = WizardColors.text,
                    )
                }
            }
        }
    }

    /** Referencia: los cinco segmentos de la frecuencia, para comprobar si caben en el módulo. */
    @Composable
    private fun SegmentosReferencia() {
        KpknModule(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            KpknModuleHeader(label = "Referencia: cinco segmentos")
            Spacer(Modifier.height(12.dp))
            KpknSegmentedControl(
                options = listOf("Cada día", "7 días", "14 días", "30 días", "Otra"),
                selectedIndex = 1,
                onSelect = {},
                size = KpknSegmentedSize.Compact,
            )
        }
    }

    @Test
    fun composicionCompleta100() {
        onlyWhenAsked()
        montar { ModuloComposicion(conAltura = true) }
        rule.waitForIdle()
        guardar("cu2_composicion_100")
    }

    @Test
    fun composicionCompleta130() {
        onlyWhenAsked()
        montar(escala = 1.3f) { ModuloComposicion(conAltura = true) }
        rule.waitForIdle()
        guardar("cu2_composicion_130")
    }

    @Test
    fun composicionSinAlturaSeVeElAviso() {
        onlyWhenAsked()
        montar { ModuloComposicion(conAltura = false) }
        rule.waitForIdle()
        guardar("cu2_composicion_sin_altura_100")
    }

    @Test
    fun composicionSinSesionCompatible() {
        onlyWhenAsked()
        montar { ModuloComposicion(conAltura = true, conComposicion = false) }
        rule.waitForIdle()
        guardar("cu2_composicion_sin_sesion_100")
    }

    @Test
    fun objetivoConPlanActivo100() {
        onlyWhenAsked()
        montar { ModuloObjetivo(conMeta = true, conPlan = true) }
        rule.waitForIdle()
        guardar("cu2_objetivo_plan_100")
    }

    @Test
    fun objetivoConPlanActivo130() {
        onlyWhenAsked()
        montar(escala = 1.3f) { ModuloObjetivo(conMeta = true, conPlan = true) }
        rule.waitForIdle()
        guardar("cu2_objetivo_plan_130")
    }

    @Test
    fun objetivoPropioSinPlan100() {
        onlyWhenAsked()
        montar { ModuloObjetivo(conMeta = true, conPlan = false) }
        rule.waitForIdle()
        guardar("cu2_objetivo_propio_100")
    }

    @Test
    fun sinObjetivoNiPlan100() {
        onlyWhenAsked()
        montar { ModuloObjetivo(conMeta = false, conPlan = false) }
        rule.waitForIdle()
        guardar("cu2_sin_objetivo_100")
    }

    @Test
    fun sinObjetivoConPlan130() {
        onlyWhenAsked()
        montar(escala = 1.3f) { ModuloObjetivo(conMeta = false, conPlan = true) }
        rule.waitForIdle()
        guardar("cu2_sin_objetivo_plan_130")
    }

    @Test
    fun hojaDeObjetivoPropio100() {
        onlyWhenAsked()
        montar {
            PanelHoja {
                BodyGoalSheetContent(
                    draft = bodySampleGoalDraft(),
                    weightUnit = WizardMassUnit.KG,
                    canRemove = true,
                    onDraftChange = {},
                    onSave = {},
                    onRemove = {},
                    onDismiss = {},
                )
            }
        }
        rule.waitForIdle()
        guardar("cu2_hoja_objetivo_100")
    }

    @Test
    fun hojaDeObjetivoPropio130() {
        onlyWhenAsked()
        montar(escala = 1.3f) {
            PanelHoja {
                BodyGoalSheetContent(
                    draft = bodySampleGoalDraft(),
                    weightUnit = WizardMassUnit.KG,
                    canRemove = true,
                    onDraftChange = {},
                    onSave = {},
                    onRemove = {},
                    onDismiss = {},
                )
            }
        }
        rule.waitForIdle()
        guardar("cu2_hoja_objetivo_130")
    }

    @Test
    fun hojaDeExplicacionDelFfmi100() {
        onlyWhenAsked()
        montar {
            PanelHoja {
                BodyInfoSheetContent(
                    info = bodyInfoFor(BodyInfoTopic.FFMI, bodySampleFfmi(), BODY_SAMPLE_BODY_FAT),
                    onDismiss = {},
                )
            }
        }
        rule.waitForIdle()
        guardar("cu2_hoja_ffmi_100")
    }

    @Test
    fun recordatorioActivoCadaSieteDias100() {
        onlyWhenAsked()
        montar { ModuloRecordatorio(schedule = bodySampleSchedule(7), otra = false) }
        rule.waitForIdle()
        guardar("cu2_recordatorio_100")
    }

    @Test
    fun recordatorioActivoCadaSieteDias130() {
        onlyWhenAsked()
        montar(escala = 1.3f) { ModuloRecordatorio(schedule = bodySampleSchedule(7), otra = false) }
        rule.waitForIdle()
        guardar("cu2_recordatorio_130")
    }

    @Test
    fun recordatorioConOtraYSuSelector100() {
        onlyWhenAsked()
        montar { ModuloRecordatorio(schedule = bodySampleSchedule(21), otra = true) }
        rule.waitForIdle()
        guardar("cu2_recordatorio_otra_100")
    }

    @Test
    fun recordatorioDesactivado100() {
        onlyWhenAsked()
        montar { ModuloRecordatorio(schedule = bodySampleSchedule(7).copy(enabled = false), otra = false) }
        rule.waitForIdle()
        guardar("cu2_recordatorio_off_100")
    }

    @Test
    fun iconosDeCantidad100() {
        onlyWhenAsked()
        montar { FilaIconos() }
        rule.waitForIdle()
        guardar("cu2_iconos_100")
    }

    @Test
    fun segmentosDeReferencia100() {
        onlyWhenAsked()
        montar { SegmentosReferencia() }
        rule.waitForIdle()
        guardar("cu2_segmentos_referencia_100")
    }

    @Test
    fun segmentosDeReferencia130() {
        onlyWhenAsked()
        montar(escala = 1.3f) { SegmentosReferencia() }
        rule.waitForIdle()
        guardar("cu2_segmentos_referencia_130")
    }
}
