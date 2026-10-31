package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.components.LiveSheetTokens
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import com.example.kpkn.ui.design.motion.KpknWaveEdgeFade
import com.example.kpkn.ui.design.motion.KpknWaveField
import com.example.kpkn.ui.design.motion.KpknWaveState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val readyInk = Color(0xFFF2EEE6)
private val readyBody = TextStyle(fontFamily = WizardFonts.body)
private val readyNumber = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "'tnum' 1")

/** Pantalla previa del cardio; la isla y el nombre pertenecen al encabezado compartido. */
@Composable
internal fun CardioReadyPulido(
    steps: List<CardioStep>, plannedSeconds: Int, hasLiveHr: Boolean, gpsMode: Boolean,
    gpsState: CardioGpsState?, keepScreenOn: Boolean, hazeState: HazeState?,
    onStart: () -> Unit, onSeePlan: () -> Unit,
) {
    val first = steps.firstOrNull()
    val zone = first?.zone ?: 2
    val compact = LocalConfiguration.current.screenHeightDp < 800
    val denied = gpsMode && (gpsState?.status == CardioGpsStatus.PERMISSION_DENIED || gpsState?.status == CardioGpsStatus.LOCATION_DISABLED)
    Box(Modifier.fillMaxSize().testTag("cardio-ready")) {
        Column(Modifier.fillMaxSize().then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
            .verticalScroll(rememberScrollState()).padding(bottom = if (compact) 130.dp else 150.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 3.dp).heightIn(min = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(cardioZoneColor(zone)))
                Text("Antes de empezar", style = readyBody.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium), color = readyInk)
                Text("\u00b7  ${steps.size} ${if (steps.size == 1) "paso" else "pasos"}", style = readyBody.copy(fontSize = 12.5.sp), color = LiveSheetTokens.Faint)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp).height(if (compact) 138.dp else 292.dp).testTag("cardio-clock")) {
                CvHalo(cardioZoneColor(zone), maxHeight)
                KpknWaveField(KpknWaveState.Idle, Modifier.fillMaxSize(), zoneColor = cardioZoneColor(zone),
                    lines = if (compact) 14 else 22, edgeFade = KpknWaveEdgeFade.Hero)
                Column(Modifier.fillMaxSize().padding(top = if (compact) 4.dp else 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val label = cardioClock(plannedSeconds)
                    val font = when { label.length >= 6 -> if (compact) 46 else 54; label.length >= 5 -> if (compact) 56 else 68; else -> if (compact) 64 else 80 }
                    Text(label, Modifier.testTag("cardio-clock-label"), style = readyNumber.copy(fontSize = font.sp, lineHeight = font.sp, letterSpacing = (-2.5).sp), color = readyInk, maxLines = 1)
                    Text("duraci\u00f3n \u00b7 empieza en Z$zone", style = readyBody.copy(fontSize = 12.5.sp), color = LiveSheetTokens.Muted)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF111111)).border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                ReadyCheck(KpknLineIcons.Pulse, "Pulso", if (hasLiveHr) "Reloj \u00b7 listo" else "Sin reloj \u00b7 gu\u00eda por esfuerzo", if (hasLiveHr) "Listo" else "RPE")
                ReadyCheck(KpknLineIcons.Route, "Lugar", if (!gpsMode) "Interior \u00b7 sin GPS" else if (denied) "Exterior \u00b7 falta permiso de ubicaci\u00f3n" else "Exterior \u00b7 GPS", if (denied) "Revisar" else "Listo")
                ReadyCheck(KpknLineIcons.Sun, "Pantalla", if (keepScreenOn) "Siempre encendida" else "Se apaga sola", "Listo")
            }
            first?.cue?.takeIf { it.isNotBlank() }?.let { cue ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF111111)).border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x0CFFFFFF)), contentAlignment = Alignment.Center) {
                        Icon(KpknLineIcons.Sliders, null, Modifier.size(17.dp), tint = cardioZoneColor(zone))
                    }
                    Text("Primer bloque: ajusta a $cue", style = readyBody.copy(fontSize = 13.sp), color = readyInk)
                }
            }
            if (steps.size > 1) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF111111))
                    .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("PLAN DEL CARDIO", style = readyBody.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Faint)
                        Text("Ver el plan", Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSeePlan).wrapContentHeight().testTag("cardio-ready-plan"), style = readyBody.copy(fontSize = 12.5.sp), color = LiveSheetTokens.Muted)
                    }
                    CardioProfile(steps, -1, 0f, height = 40.dp)
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Ver el plan", Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onSeePlan).padding(horizontal = 18.dp, vertical = 12.dp).testTag("cardio-ready-plan"), style = readyBody.copy(fontSize = 12.5.sp), color = readyInk)
                }
            }
        }
        val shape = RoundedCornerShape(32.dp)
        Column(Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = if (compact) 14.dp else 20.dp)
            .fillMaxWidth().kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, shape).border(1.dp, Color(0x1FFFFFFF), shape).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(readyInk).clickable(role = Role.Button, onClick = onStart).testTag("cardio-start"),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(KpknLineIcons.Play, null, Modifier.size(20.dp), tint = Color(0xFF111111))
                Text("Empezar", style = readyBody.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = Color(0xFF111111))
            }
            Text("Empieza con una cuenta atr\u00e1s de 3 segundos", style = readyBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Faint)
        }
    }
}

@Composable
private fun ReadyCheck(icon: ImageVector, title: String, description: String, status: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x0CFFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = LiveSheetTokens.Muted)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = readyBody.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = readyInk)
            Text(description, style = readyBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Muted)
        }
        Text(status, style = readyBody.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = if (status == "Listo") Color(0xFF87C9A5) else LiveSheetTokens.Faint)
    }
}
