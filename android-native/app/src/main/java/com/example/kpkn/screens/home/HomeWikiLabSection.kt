package com.example.kpkn.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.domain.concepts.projectConceptoClave
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.screens.home.design.HomeModuleMargin
import com.example.kpkn.screens.home.design.HomeTypography
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * «Aprende»: un módulo con su cabecera y «Ver todos», y el concepto del día: un libro de línea en un círculo `inset`, el título en Syne Bold
 * 18, dos líneas de resumen y «Leer». Toda la fila abre el concepto; «Ver todos» abre la biblioteca. El concepto cambia cada día.
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

    KpknModule(modifier = Modifier.fillMaxWidth().padding(horizontal = HomeModuleMargin)) {
        KpknModuleHeader(
            label = "Aprende",
            action = { KpknTextAction(text = "Ver todos", onClick = { onNavigate(KpknRoute.Concepts.create()) }) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag("concept_daily_card_${concept.id}")
                .clickable(role = Role.Button, onClick = openConcept),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(KpknSurface.inset, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Book,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = SymbolPalette.mente,
                )
            }
            Spacer(Modifier.width(14.dp))
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
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                KpknTextAction(
                    text = "Leer",
                    onClick = openConcept,
                    icon = KpknLineIcons.ChevronRight,
                    modifier = Modifier.padding(top = 2.dp).testTag("concept_daily_read_more_${concept.id}"),
                )
            }
        }
    }
}
