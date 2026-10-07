package com.example.kpkn.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.CapabilitySymbols
import com.example.kpkn.screens.onboarding.design.entreno.LiftMarksPicker
import com.example.kpkn.screens.onboarding.design.entreno.MuscleSymbolGrid

/**
 * SOLO DEPURACIÓN (no se integra): muestra uno de los tres componentes de Entreno v2 sobre la página negra, con el
 * título y el subtítulo del paso, para criticar el dibujo, el contraste y la letra grande.
 *
 * Extras (`adb shell am start … --es scenario muscles`):
 *  - `scenario`: `muscles` (por defecto), `marks` o `capabilities`.
 *  - `selected` (`CHEST,BACK`) y `suggested` (`QUADS,GLUTES`): músculos elegidos y sugeridos; `max` (entero, 5 por defecto).
 *  - `lifts` (`SQUAT,BENCH,DEADLIFT` por defecto; `all` para los seis), `marks` (`SQUAT:142.5,BENCH:100`, en kg) y `unit` (`kg`|`lb`).
 *  - `skills` (las cuatro por defecto) y `levels` (`PULL_UP:SOME,PUSH_UP:MANY,DIP:NONE`).
 *  - `fontScale` (`1.3`) y `widthDp` (`390`): para probar letra grande y pantallas estrechas sin tocar los ajustes.
 */
class EntrenoMusclesPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "muscles"
        val fontScale = intent.getStringExtra("fontScale")?.toFloatOrNull() ?: 1f
        val widthDp = intent.getStringExtra("widthDp")?.toIntOrNull() ?: 390
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        Modifier
                            .width(widthDp.dp)
                            .padding(horizontal = 24.dp)
                            .padding(top = 56.dp, bottom = 40.dp),
                    ) {
                        when (scenario) {
                            "marks" -> MarksScene()
                            "capabilities" -> CapabilitiesScene()
                            else -> MusclesScene()
                        }
                    }
                }
            }
        }
    }

    private inline fun <reified T : Enum<T>> parseEnums(raw: String?): List<T> =
        raw?.split(',')?.mapNotNull { name -> enumValues<T>().firstOrNull { it.name == name.trim() } }.orEmpty()

    @Composable
    private fun Header(title: String, subtitle: String) {
        Text(title, style = WizardTypography.stepTitle, color = WizardColors.text)
        Text(
            subtitle,
            style = WizardTypography.stepSubtitle,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
        )
    }

    @Composable
    private fun MusclesScene() {
        val max = intent.getStringExtra("max")?.toIntOrNull() ?: 5
        var selected by remember { mutableStateOf(parseEnums<MuscleSymbol>(intent.getStringExtra("selected")).toSet()) }
        val suggested = remember { parseEnums<MuscleSymbol>(intent.getStringExtra("suggested")).toSet() }
        Header("¿Qué músculos quieres mejorar más?", "Elige hasta 5. Puedes omitir este paso.")
        MuscleSymbolGrid(
            selected = selected,
            suggested = suggested,
            maxSelected = max,
            onToggle = { m -> selected = if (m in selected) selected - m else selected + m },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun MarksScene() {
        val liftsRaw = intent.getStringExtra("lifts")
        val lifts = if (liftsRaw == "all") {
            LiftMark.entries.toList()
        } else {
            parseEnums<LiftMark>(liftsRaw).ifEmpty { listOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT) }
        }
        val values = remember {
            mutableStateMapOf<LiftMark, Double>().also { map ->
                intent.getStringExtra("marks")?.split(',')?.forEach { pair ->
                    val parts = pair.split(':')
                    val lift = LiftMark.entries.firstOrNull { it.name == parts.getOrNull(0)?.trim() }
                    val kg = parts.getOrNull(1)?.toDoubleOrNull()
                    if (lift != null && kg != null) map[lift] = kg
                }
            }
        }
        var unit by remember { mutableStateOf(intent.getStringExtra("unit") ?: "kg") }
        Header("¿Conoces tus marcas?", "Con una basta. Sin marcas, el programa sigue siendo válido.")
        LiftMarksPicker(
            lifts = lifts,
            valuesKg = values.toMap(),
            unit = unit,
            onValueKg = { lift, kg -> if (kg == null) values.remove(lift) else values[lift] = kg },
            onUnit = { unit = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun CapabilitiesScene() {
        val skills = parseEnums<CapabilitySkill>(intent.getStringExtra("skills")).ifEmpty { CapabilitySkill.entries.toList() }
        val levels = remember {
            mutableStateMapOf<CapabilitySkill, CapabilityLevel>().also { map ->
                intent.getStringExtra("levels")?.split(',')?.forEach { pair ->
                    val parts = pair.split(':')
                    val skill = CapabilitySkill.entries.firstOrNull { it.name == parts.getOrNull(0)?.trim() }
                    val level = CapabilityLevel.entries.firstOrNull { it.name == parts.getOrNull(1)?.trim() }
                    if (skill != null && level != null) map[skill] = level
                }
            }
        }
        Header("¿Qué ejercicios ya te salen?", "Así elegimos variantes a tu medida.")
        CapabilitySymbols(
            skills = skills,
            levels = levels.toMap(),
            onLevel = { skill, level -> levels[skill] = level },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Sin presión: siempre podrás cambiarlo.",
            style = WizardTypography.note,
            color = WizardColors.textFaint,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}
