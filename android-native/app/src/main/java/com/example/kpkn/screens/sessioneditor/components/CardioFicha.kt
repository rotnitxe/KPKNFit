package com.example.kpkn.screens.sessioneditor.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioFeaturedTemplates
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.cardio.CardioFichaGuides
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.domain.cardio.CardioTypeHistory
import com.example.kpkn.domain.cardio.CardioZones
import com.example.kpkn.domain.exercises.exerciseFichaIdentityKeys
import com.example.kpkn.domain.exercises.normalizeExerciseIdentityToken
import com.example.kpkn.screens.progress.LocalExerciseProgressOpener
import com.example.kpkn.screens.sessioneditor.design.CardioTypeArt
import com.example.kpkn.screens.workout.CardioZoneColors
import com.example.kpkn.screens.workout.CardioZoneNames
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.design.depth.KpknFilterPreset
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknGradient
import com.example.kpkn.ui.design.depth.kpknShadow

/*
 * Pulido v1 · CARDIO DEL EDITOR · punto 9: la ficha propia del cardio (`ceFichaView` de `scenes-ce.js`). Un espacio dedicado, no el
 * «Sin imagen revisada para esta configuración»: la ilustración de la modalidad, qué es, qué entrena, cómo medir el esfuerzo, formatos
 * recomendados, técnica y seguridad, y «Ver progreso» arriba con su última sesión. Los textos son de `CardioFichaGuides` (contenido nuevo).
 */

/** La ficha de un ejercicio de cardio de la sesión. Se abre desde «Ficha del cardio» del menú de pulsación larga. */
@Composable
internal fun CardioFicha(
    exercise: Exercise,
    details: CardioDetails,
    history: CardioTypeHistory?,
    onDismiss: () -> Unit,
) {
    val openProgress = LocalExerciseProgressOpener.current
    val keys = remember(exercise) { exerciseFichaIdentityKeys(exercise) }
    val last = history?.takeUnless { it.isEmpty }?.let(::cardioLastLine)
    DisposableEffect(Unit) {
        KpknModalTracker.onOpened()
        onDispose { KpknModalTracker.onClosed() }
    }
    BackHandler(onBack = onDismiss)
    KpknPortal {
        CardioFichaContent(
            type = details.type,
            lastLine = last,
            onOpenProgress = {
                val key = if (keys.isNotEmpty()) "keys:${keys.joinToString(",")}" else "name:${normalizeExerciseIdentityToken(exercise.name)}"
                onDismiss()
                openProgress(key)
            },
            onDismiss = onDismiss,
        )
    }
}

/** «18 min · 3,1 km · 5:48/km · RPE 7»: la última sesión de ese cardio, para «Ver progreso». */
internal fun cardioLastLine(stats: CardioTypeHistory): String? = listOfNotNull(
    stats.lastDurationSeconds?.let { CardioPrescriptionFormatter.formatDuration(it) },
    stats.lastDistanceKm?.let { "${cellNumber(it)} km" },
    stats.lastPaceSecondsPerKm?.let { CardioPrescriptionFormatter.formatPace(it) },
    stats.lastRpe?.let { "RPE ${it.toInt()}" },
).joinToString(" · ").takeIf { it.isNotBlank() }

/** El cuerpo de la ficha (sin repositorios: también lo dibuja la hoja de render). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardioFichaContent(
    type: CardioType,
    lastLine: String?,
    onOpenProgress: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = CardioCatalog.findByType(type)
    val guide = CardioFichaGuides.of(type)
    val name = item?.name ?: CardioPrescriptionFormatter.typeLabel(type)
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 70.dp)) {
            // El héroe: la viñeta radial, la ilustración de 168 dp abajo y un velo hacia el negro del cuerpo.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    // ce-G01 `.ce-fi-hero` viñeta radial (preset vineta)
                    .kpknGradient(KpknGradient.Vineta),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(modifier = Modifier.padding(bottom = 20.dp)) { CardioTypeArt(type = type, size = 168.dp) }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                        // ce-G02 `.ce-fi-hero::after` velo hacia el negro (preset velo-negro-1)
                        .kpknGradient(KpknGradient.VeloNegro1),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // `.ce-fi-t{margin-top:-6px}` y el margen del h1 (0,67 em de 26 px = 17,42 dp arriba y abajo, que en flex no se funde con el hueco de 12).
                Column(modifier = Modifier.padding(top = 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        name,
                        modifier = Modifier.padding(top = 11.42.dp, bottom = 17.42.dp),
                        style = edDisplay(26f, FontWeight.Bold, EdColors.ink, -0.7f).copy(lineHeight = 28.1.sp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag("Cardio")
                        Tag(if (item?.requiresGps == true) "Exterior · GPS" else "Interior")
                    }
                }
                ProgressRow(lastLine, onOpenProgress)
                Section("Qué es") { Text(guide.what, style = body()) }
                Section("Qué entrena") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        guide.trains.forEach { Tag(it, compact = true) }
                    }
                }
                Section("Cómo medir el esfuerzo") {
                    Zones()
                    Text(guide.effort, modifier = Modifier.padding(top = 12.dp), style = body())
                }
                Section("Formatos recomendados") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        guide.formats.forEach { id -> FormatCard(id, Modifier.weight(1f)) }
                    }
                }
                Section("Técnica y seguridad") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { guide.tips.forEach { Tip(it) } }
                }
            }
        }
        // `.ce-x`: el cierre, un círculo de vidrio de 48 dp arriba a la derecha.
        val closeShape = CircleShape
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 2.dp, end = 14.dp)
                .size(48.dp)
                // ce-S07 `.pl-glass` sombra 0 8px 28px + realce interior (preset isla-vidrio) · ce-V02 `.pl-glass` vidrio blur(22px) saturate(1.5) (preset vidrio-isla)
                .kpknShadow(KpknShadow.IslaVidrio, closeShape)
                .kpknGlassPreset(null, KpknGlassPreset.VidrioIsla, closeShape, withBorder = true)
                .clip(closeShape)
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = "Cerrar" },
            contentAlignment = Alignment.Center,
        ) { CeIcon(CeIcons.Close, 20.dp, EdColors.ink) }
    }
}

private fun body() = edText(14.5f, FontWeight.Normal, EdColors.muted, 23.2f)

@Composable
private fun Tag(text: String, compact: Boolean = false) {
    val shape = RoundedCornerShape(if (compact) 17.dp else 15.dp)
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = if (compact) 34.dp else 30.dp)
            .clip(shape)
            .then(if (compact) Modifier.background(CeColors.module, shape) else Modifier)
            .border(1.dp, if (compact) Color.White.copy(alpha = 0.051f) else Color.White.copy(alpha = 0.122f), shape)
            .padding(horizontal = if (compact) 13.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = edText(if (compact) 13f else 12.5f, FontWeight.Medium, EdColors.ink, 16f), maxLines = 1)
    }
}

@Composable
private fun ProgressRow(lastLine: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(shape)
            .background(CeColors.module, shape)
            .border(1.dp, Color.White.copy(alpha = 0.051f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CeRowIcon(CeIcons.Chart)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Ver progreso", style = edText(15f, FontWeight.SemiBold, EdColors.ink, 19.5f))
            Text(
                if (lastLine != null) "Última vez · $lastLine" else "Aún sin sesiones de este cardio.",
                style = edText(12.5f, FontWeight.Normal, EdColors.faint, 17.5f, true),
            )
        }
        CeIcon(CeIcons.Fwd, 16.dp, EdColors.faint)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 28.dp)) {
        Text(title, modifier = Modifier.padding(bottom = 10.dp), style = edDisplay(19f, FontWeight.Bold, EdColors.ink, -0.4f).copy(lineHeight = 21.9.sp))
        content()
    }
}

@Composable
private fun Zones() {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CeColors.module, shape)
            .border(1.dp, Color.White.copy(alpha = 0.051f), shape)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        for (z in 1..5) {
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 42.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(CardioZoneColors[z - 1]))
                Text("Z$z", modifier = Modifier.width(24.dp), style = edText(13.5f, FontWeight.SemiBold, EdColors.ink, 17f, true))
                Text(CardioZoneNames[z - 1], modifier = Modifier.weight(1f), style = edText(13.5f, FontWeight.Normal, EdColors.ink, 17f))
                Text("RPE ${CardioZones.rpeRangeLabel(z)}", style = edText(12.5f, FontWeight.Normal, EdColors.faint, 16f, true))
            }
        }
    }
}

@Composable
private fun FormatCard(templateId: String, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val template = CardioFeaturedTemplates.all.firstOrNull { it.id == templateId } ?: return
    // La forma de la plantilla, calculada sobre un cardio de la modalidad con valores neutros (la forma no depende de la máquina).
    val preview = remember(templateId) { CardioFeaturedTemplates.apply(templateId, CardioDetails(type = CardioType.TREADMILL)) }
    Column(
        modifier = modifier
            .clip(shape)
            .background(CeColors.module, shape)
            .border(1.dp, Color.White.copy(alpha = 0.051f), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (preview.hasIntervals()) CeBlockChart(cardioBars(preview), height = 30.dp, sparkline = true) else CeFlatBar(height = 30.dp)
        Text(template.name, style = edText(13f, FontWeight.SemiBold, EdColors.ink, 16.2f))
        Text(cardioDoseLine(preview), style = edText(11f, FontWeight.Normal, EdColors.faint, 14.9f, true))
    }
}

@Composable
private fun Tip(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.padding(start = 3.dp, top = 9.dp).size(6.dp).clip(CircleShape).background(CeColors.col))
        Text(text, modifier = Modifier.weight(1f), style = edText(14.5f, FontWeight.Normal, EdColors.muted, 22.5f))
    }
}
