package com.example.kpkn.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.domain.concepts.projectConceptoClave
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.screens.home.design.HomeSectionAction
import com.example.kpkn.screens.home.design.HomeSectionLabel
import com.example.kpkn.screens.home.design.HomeTypography
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.SymbolStroke
import com.example.kpkn.screens.onboarding.design.entreno.onSymbolsVisible
import com.example.kpkn.screens.onboarding.design.entreno.pingPong
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Lado del libro en unidades lógicas (el dibujo se hace sobre 64 × 64). */
private const val BOOK_UNITS = 64f

/** Cuánto late el libro (1 → 1,04) y cuánto dura un latido completo, en segundos. */
private const val BOOK_BREATH = 0.04f
private const val BOOK_BREATH_PERIOD = 3f

/**
 * «Aprende»: el concepto del día, sin caja. Un libro abierto de línea que late despacio, el título en Syne, dos líneas de resumen
 * y «Leer». Toda la fila abre el concepto; «Ver todos» abre la biblioteca. El concepto cambia cada día.
 * Etiquetas de prueba: `concept_daily_card_<id>` (la fila) y `concept_daily_read_more_<id>` («Leer»).
 */
@Composable
fun HomeWikiLabSection(onNavigate: (String) -> Unit) {
    if (TRAINING_CONCEPTS_DATABASE.isEmpty()) return

    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            today = LocalDate.now()
        }
    }
    val concept = remember(today) {
        TRAINING_CONCEPTS_DATABASE[
            Math.floorMod(today.toEpochDay().toInt(), TRAINING_CONCEPTS_DATABASE.size)
        ]
    }
    val conceptProjection = projectConceptoClave(concept)
    val openConcept = { onNavigate(KpknRoute.Concepts.create(concept.id)) }
    // El resumen de dos líneas es el corto que Home ya enseñaba; si falta, la descripción del concepto.
    val summary = concept.shortDescription.ifBlank { conceptProjection.description }

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionLabel(
            text = "APRENDE",
            action = { HomeSectionAction(text = "Ver todos", onClick = { onNavigate(KpknRoute.Concepts.create()) }) },
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WizardSpacing.gutter)
                .testTag("concept_daily_card_${concept.id}")
                .clickable(role = Role.Button, onClick = openConcept),
            verticalAlignment = Alignment.Top,
        ) {
            ConceptBook(modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conceptProjection.name,
                    style = HomeTypography.conceptTitle,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = summary,
                        style = WizardTypography.body,
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = WizardSpacing.touchTarget)
                        .testTag("concept_daily_read_more_${concept.id}"),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(text = "Leer", style = WizardTypography.controlLabel, color = WizardColors.text)
                }
            }
        }
    }
}

/**
 * Libro abierto de línea, en el color de «mente». Respira despacio solo si se ve y sin movimiento reducido; si no, queda en su
 * cuadro final, quieto.
 */
@Composable
private fun ConceptBook(modifier: Modifier) {
    val reduced = wizardReducedMotion()
    var visible by remember { mutableStateOf(true) }
    val clock = rememberSymbolClock(active = !reduced && visible)
    val pen = remember { SymbolPen() }
    val book = remember { openBookPath() }
    Canvas(
        modifier = modifier
            .onSymbolsVisible { visible = it }
            .graphicsLayer {
                val s = 1f + BOOK_BREATH * pingPong(clock.seconds / BOOK_BREATH_PERIOD)
                scaleX = s
                scaleY = s
            },
    ) {
        val k = size.minDimension / BOOK_UNITS
        withTransform({ scale(k, k, Offset.Zero) }) {
            pen.begin(this, 1f, SymbolPalette.mente)
            pen.path(book, SymbolPalette.mente, SymbolStroke.LINE)
            // Dos renglones por página, en el mismo color al 50 %.
            val soft = SymbolPalette.mente.copy(alpha = SymbolPalette.SOFT)
            pen.line(11f, 28f, 26f, 28f, soft, SymbolStroke.FINE)
            pen.line(11f, 36f, 26f, 36f, soft, SymbolStroke.FINE)
            pen.line(38f, 28f, 53f, 28f, soft, SymbolStroke.FINE)
            pen.line(38f, 36f, 53f, 36f, soft, SymbolStroke.FINE)
        }
    }
}

/** El libro abierto en unidades de 64: dos páginas con el lomo en el centro. Se arma una vez, fuera del dibujo. */
private fun openBookPath(): Path = Path().apply {
    moveTo(32f, 20f)
    quadraticTo(20f, 12f, 6f, 15f)
    lineTo(6f, 50f)
    quadraticTo(20f, 47f, 32f, 55f)
    moveTo(32f, 20f)
    quadraticTo(44f, 12f, 58f, 15f)
    lineTo(58f, 50f)
    quadraticTo(44f, 47f, 32f, 55f)
    moveTo(32f, 20f)
    lineTo(32f, 55f)
}
