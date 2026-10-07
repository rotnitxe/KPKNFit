package com.example.kpkn.debug

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.FreshDayRow
import com.example.kpkn.screens.onboarding.design.entreno.LocalWeekFrozenTime
import com.example.kpkn.screens.onboarding.design.entreno.LocalWeekMotionScale
import com.example.kpkn.screens.onboarding.design.entreno.LocalWeekReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SessionClockDial
import com.example.kpkn.screens.onboarding.design.entreno.WeekCalendar

/**
 * SOLO DEPURACIÓN (no se integra): muestra los componentes de semana y tiempo de Entreno sobre la página negra, con el título y
 * el subtítulo del paso, y con estado propio para poder tocarlos.
 *
 * Extras (`adb shell am start … --es scenario dial`):
 *  - `scenario`: `fresh` (día de más energía), `calendar` (días de entreno), `calendar_places` (el calendario con gimnasio y casa)
 *    o `dial` (el reloj de la sesión).
 *  - `day` (int, fresh): día de más energía elegido (1 = lunes … 7); sin él, ninguno.
 *  - `start` (int, calendar): primer día de la semana (por defecto 1).
 *  - `selected` (string «4,5,7», calendar): días de entreno elegidos; `fresh` (int): día de más energía (con marca de sol).
 *  - `places` (string «gym,home,public», calendar): lugares elegidos (por defecto: ninguno en `calendar`, gym y home en
 *    `calendar_places`); `dayplaces` («4:home,5:public»): lugar asignado a cada día.
 *  - `minutes` (int, dial): minutos iniciales (por defecto 75).
 *  - `width` (int, dp): ancho de la «pantalla» (la página lleva 24 dp de margen a cada lado); `fontscale` (float): escala de letra.
 *  - `t` (float): congela el reloj de los bucles (onda, sol, segundera) en ese segundo; `reduced` (bool): movimiento reducido.
 *  - `slow` (float): alarga las animaciones de duración fija (relleno, deslizado, cifra) para capturarlas a medias.
 *  - Para capturar una animación a medias (el `screencap` tarda más que ella): `autoday`, `autostart`, `autotoggle` (un día) y
 *    `autominutes` hacen ese cambio SOLO, `autodelay` milisegundos (2600 por defecto) después de abrirse; se capturan varios
 *    cuadros seguidos con `emu_run.py --frames`.
 */
class EntrenoWeekPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
        val scenario = intent.getStringExtra("scenario") ?: "fresh"
        val config = PreviewConfig(
            scenario = scenario,
            day = intent.takeIf { it.hasExtra("day") }?.getIntExtra("day", 0),
            start = intent.getIntExtra("start", 1),
            selected = intent.getStringExtra("selected").toIntSet(),
            freshest = intent.takeIf { it.hasExtra("fresh") }?.getIntExtra("fresh", 0),
            places = intent.getStringExtra("places")?.toPlaces()
                ?: if (scenario == "calendar_places") setOf(TrainingPlace.GYM, TrainingPlace.HOME) else emptySet(),
            dayPlaces = intent.getStringExtra("dayplaces").toDayPlaces(),
            minutes = intent.getIntExtra("minutes", 75),
            widthDp = intent.takeIf { it.hasExtra("width") }?.getIntExtra("width", 0),
            fontScale = intent.takeIf { it.hasExtra("fontscale") }?.getFloatExtra("fontscale", 1f),
            frozenTime = intent.takeIf { it.hasExtra("t") }?.getFloatExtra("t", 0f),
            reduced = intent.takeIf { it.hasExtra("reduced") }?.getBooleanExtra("reduced", false),
            autoDay = intent.takeIf { it.hasExtra("autoday") }?.getIntExtra("autoday", 0),
            autoStart = intent.takeIf { it.hasExtra("autostart") }?.getIntExtra("autostart", 0),
            autoToggle = intent.takeIf { it.hasExtra("autotoggle") }?.getIntExtra("autotoggle", 0),
            autoMinutes = intent.takeIf { it.hasExtra("autominutes") }?.getIntExtra("autominutes", 0),
            autoDelayMs = intent.getIntExtra("autodelay", 2600).toLong(),
            slow = intent.getFloatExtra("slow", 1f),
        )
        setContent { PreviewHost(config) }
    }
}

private class PreviewConfig(
    val scenario: String,
    val day: Int?,
    val start: Int,
    val selected: Set<Int>,
    val freshest: Int?,
    val places: Set<TrainingPlace>,
    val dayPlaces: Map<Int, TrainingPlace>,
    val minutes: Int,
    val widthDp: Int?,
    val fontScale: Float?,
    val frozenTime: Float?,
    val reduced: Boolean?,
    val autoDay: Int?,
    val autoStart: Int?,
    val autoToggle: Int?,
    val autoMinutes: Int?,
    val autoDelayMs: Long,
    val slow: Float,
)

private fun String?.toIntSet(): Set<Int> =
    this?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet().orEmpty()

private fun placeOf(name: String): TrainingPlace? = when (name.trim().lowercase()) {
    "gym", "gimnasio" -> TrainingPlace.GYM
    "home", "casa" -> TrainingPlace.HOME
    "public", "park", "parque" -> TrainingPlace.PUBLIC
    else -> null
}

private fun String.toPlaces(): Set<TrainingPlace> = split(',').mapNotNull { placeOf(it) }.toSet()

private fun String?.toDayPlaces(): Map<Int, TrainingPlace> =
    this?.split(',')?.mapNotNull { pair ->
        val parts = pair.split(':')
        val day = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val place = parts.getOrNull(1)?.let { placeOf(it) }
        if (day != null && place != null) day to place else null
    }?.toMap().orEmpty()

@Composable
private fun PreviewHost(config: PreviewConfig) {
    val base = LocalDensity.current
    val density = if (config.fontScale != null) Density(base.density, config.fontScale) else base
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalWeekReducedMotion provides config.reduced,
        LocalWeekFrozenTime provides config.frozenTime,
        LocalWeekMotionScale provides config.slow,
    ) {
        Box(Modifier.fillMaxSize().background(WizardColors.background).statusBarsPadding()) {
            val page = if (config.widthDp != null) Modifier.width(config.widthDp.dp) else Modifier.fillMaxWidth()
            Column(
                modifier = page
                    .align(Alignment.TopStart)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = "${config.scenario}  ·  ${config.widthDp ?: "pantalla"} dp  ·  letra ${config.fontScale ?: 1f}" +
                        (config.frozenTime?.let { "  ·  t = $it s" } ?: "") + (if (config.reduced == true) "  ·  reducido" else ""),
                    color = Color(0xFF6A6A6A),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                when (config.scenario) {
                    "dial" -> DialPreview(config)
                    "calendar", "calendar_places" -> CalendarPreview(config)
                    else -> FreshPreview(config)
                }
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(text = title, style = WizardTypography.stepTitle, color = WizardColors.text)
    Spacer(Modifier.height(8.dp))
    Text(text = subtitle, style = WizardTypography.stepSubtitle, color = WizardColors.textMuted)
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun FreshPreview(config: PreviewConfig) {
    var day by remember { mutableStateOf(config.day?.takeIf { it in 1..7 }) }
    LaunchedEffect(Unit) {
        config.autoDay?.let { delay(config.autoDelayMs); day = it }
    }
    StepHeader("¿Qué día llegas con más energía?", "Tu sesión más fuerte caerá ese día.")
    FreshDayRow(selectedDay = day, onSelect = { day = it })
}

@Composable
private fun CalendarPreview(config: PreviewConfig) {
    var start by remember { mutableIntStateOf(config.start) }
    var selected by remember { mutableStateOf(config.selected) }
    var dayPlaces by remember { mutableStateOf(config.dayPlaces) }
    LaunchedEffect(Unit) {
        delay(config.autoDelayMs)
        config.autoStart?.let { start = it }
        config.autoToggle?.let { day -> selected = if (day in selected) selected - day else selected + day }
    }
    StepHeader("¿Qué días puedes entrenar?", "Entre 1 y 7. El programa se adapta a tu semana.")
    WeekCalendar(
        weekStartDay = start,
        selectedDays = selected,
        freshestDay = config.freshest,
        onToggleDay = { day -> selected = if (day in selected) selected - day else selected + day },
        onWeekStartChange = { start = it },
        places = config.places,
        dayPlaces = dayPlaces,
        onDayPlace = { day, place -> dayPlaces = dayPlaces + (day to place) },
    )
}

@Composable
private fun DialPreview(config: PreviewConfig) {
    var minutes by remember { mutableIntStateOf(config.minutes) }
    LaunchedEffect(Unit) {
        config.autoMinutes?.let { delay(config.autoDelayMs); minutes = it }
    }
    StepHeader("¿Cuánto tiempo tienes por sesión?", "Es un rango: el programa se ajusta a ti.")
    SessionClockDial(minutes = minutes, onMinutesChange = { minutes = it })
}
