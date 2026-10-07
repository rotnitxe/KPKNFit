package com.example.kpkn.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.design.entreno.EquipmentSymbolGrid
import com.example.kpkn.screens.onboarding.design.entreno.PlaceSymbolRow
import com.example.kpkn.screens.onboarding.design.entreno.SceneArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolCanvas
import com.example.kpkn.screens.onboarding.design.entreno.equipmentArt
import com.example.kpkn.screens.onboarding.design.entreno.placeArt
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock

/**
 * SOLO DEPURACIÓN (no se integra): vista previa de los símbolos de Entreno sobre la página negra.
 *
 * Extras (`adb shell am start … --es scenario places`), siempre a través de `emu_run.py`:
 *  - `scenario`: `places` (por defecto), `equipment` (los 17), `equipment_gym`, `equipment_home`,
 *    `equipment_public` (los que ofrece cada lugar) o `big` (un solo símbolo ampliado, con `--es id GYM|BARBELL|…`).
 *  - `selectAll` (booleano): arranca con todo marcado. `select` (texto «GYM,PUBLIC» / «BARBELL,RACK»): arranca con esos.
 *  - `widthDp` (decimal): ancho del contenido (p. ej. 360). `fontScale` (decimal): escala de fuente (p. ej. 1.3).
 *  - `reducedMotion` (booleano): fuerza «reducir movimiento» (cuadro estático, sin bucles ni resorte).
 *  Tocar un símbolo lo marca y desmarca de verdad (la pantalla lleva su propio estado).
 */
class EntrenoSymbolsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "places"
        val selectAll = intent.getBooleanExtra("selectAll", false)
        val select = intent.getStringExtra("select")?.split(',')?.map { it.trim() }.orEmpty()
        val widthDp = intent.getFloatExtra("widthDp", 0f)
        val fontScale = intent.getFloatExtra("fontScale", 0f)
        val id = intent.getStringExtra("id") ?: "GYM"
        val reduced = intent.getBooleanExtra("reducedMotion", false)
        setContent { Preview(scenario, selectAll, select, widthDp, fontScale, id, reduced) }
    }
}

private val GYM_EQUIPMENT = EquipmentSymbolId.entries.filter { it != EquipmentSymbolId.BODYWEIGHT_ONLY }
private val HOME_EQUIPMENT = listOf(
    EquipmentSymbolId.BENCH, EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.KETTLEBELL, EquipmentSymbolId.PULL_UP_BAR,
    EquipmentSymbolId.RINGS, EquipmentSymbolId.BANDS, EquipmentSymbolId.BALL, EquipmentSymbolId.JUMP_ROPE,
    EquipmentSymbolId.BOX, EquipmentSymbolId.CARDIO, EquipmentSymbolId.BODYWEIGHT_ONLY,
)
private val PUBLIC_EQUIPMENT = listOf(
    EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS, EquipmentSymbolId.RINGS, EquipmentSymbolId.BANDS,
    EquipmentSymbolId.BALL, EquipmentSymbolId.JUMP_ROPE, EquipmentSymbolId.BOX, EquipmentSymbolId.BODYWEIGHT_ONLY,
)

@Composable
private fun Preview(
    scenario: String, selectAll: Boolean, select: List<String>, widthDp: Float, fontScale: Float, id: String, reduced: Boolean,
) {
    val base = LocalDensity.current
    val density = if (fontScale > 0f) Density(base.density, fontScale) else base
    CompositionLocalProvider(LocalDensity provides density) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(
                modifier = (if (widthDp > 0f) Modifier.width(widthDp.dp) else Modifier.fillMaxWidth())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp, bottom = 32.dp),
            ) {
                Text(
                    text = "$scenario${if (widthDp > 0f) " · ${widthDp.toInt()} dp" else ""}${if (fontScale > 0f) " · fuente $fontScale" else ""}",
                    color = Color(0xFF8A8A8A),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(20.dp))
                when (scenario) {
                    "places" -> {
                        var selected by remember {
                            mutableStateOf(
                                if (selectAll) TrainingPlace.entries.toSet()
                                else TrainingPlace.entries.filter { it.name in select }.toSet(),
                            )
                        }
                        PlaceSymbolRow(selected, { selected = if (it in selected) selected - it else selected + it }, reduced)
                    }
                    "big" -> BigSymbol(id, reduced)
                    else -> {
                        val symbols = when (scenario) {
                            "equipment_gym" -> GYM_EQUIPMENT
                            "equipment_home" -> HOME_EQUIPMENT
                            "equipment_public" -> PUBLIC_EQUIPMENT
                            else -> EquipmentSymbolId.entries.toList()
                        }
                        var selected by remember {
                            mutableStateOf(
                                if (selectAll) symbols.toSet()
                                else symbols.filter { it.name in select }.toSet(),
                            )
                        }
                        EquipmentSymbolGrid(symbols, selected, { selected = if (it in selected) selected - it else selected + it }, reduced)
                    }
                }
            }
        }
    }
}

/** Un solo símbolo ampliado (un toque lo marca o desmarca): para criticar el dibujo y su movimiento de cerca. */
@Composable
private fun BigSymbol(id: String, reduced: Boolean) {
    val art: SymbolArt = TrainingPlace.entries.firstOrNull { it.name == id }?.let { placeArt(it) }
        ?: equipmentArt(EquipmentSymbolId.valueOf(id))
    var selected by remember { mutableStateOf(true) }
    val clock = rememberSymbolClock(selected)
    val size = if (art is SceneArt) Modifier.width(300.dp).aspectRatio(120f / 170f) else Modifier.size(320.dp)
    SymbolCanvas(
        art = art,
        selected = selected,
        clock = clock,
        reducedMotion = reduced,
        modifier = size.toggleable(value = selected, onValueChange = { selected = it }),
        badgeRadius = 16.dp,
    )
}
