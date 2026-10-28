package com.example.kpkn.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.wikilab.ConceptCategory
import com.example.kpkn.data.wikilab.TrainingConcept
import com.example.kpkn.domain.concepts.conceptMatchRanges
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknSurface

/** Tokens de Conceptos clave: los del prototipo aprobado (`docs/design/profile-concepts-v1/src/styles.css`). */
internal object ConceptTokens {
    val Ink = WizardColors.text
    val Ink2 = Color(0xFFDCD7CE)
    val Muted = Color(0xFFB9B5AE)
    val Faint = WizardColors.textFaint
    val Dim = Color(0xFF6F6D69)
    val Module = KpknSurface.module
    val Inset = KpknSurface.inset
    val Line = Color(0x12FFFFFF)
    val Mark = Color(0x1FF2EEE6)
    val ModuleShape = RoundedCornerShape(22.dp)
    val GlassBrush = Brush.linearGradient(listOf(Color(0xCC2A2A2A), Color(0xC0161616)))
}

internal object ConceptType {
    val eyebrow = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.5.sp)
    val rowTitle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val rowSub = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.sp, lineHeight = 18.sp)
    val body = TextStyle(fontFamily = WizardFonts.body, fontSize = 15.sp, lineHeight = 25.sp)
    val lead = TextStyle(fontFamily = WizardFonts.body, fontSize = 17.sp, lineHeight = 25.sp)
    val action = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    fun syne(size: Int, weight: FontWeight = FontWeight.Bold, line: Int = size + 3) = TextStyle(
        fontFamily = WizardFonts.display, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-0.03 * size).sp,
    )
}

/** Glifo de línea de cada tema. */
internal val ConceptCategory.glyph: ImageVector
    get() = when (this) {
        ConceptCategory.LOAD_MANAGEMENT -> KpknLineIcons.Training
        ConceptCategory.INTENSITY -> KpknLineIcons.Flame
        ConceptCategory.METHODS -> KpknLineIcons.Repeat
        ConceptCategory.PERIODIZATION -> KpknLineIcons.Calendar
        ConceptCategory.QUALITIES -> KpknLineIcons.Target
        ConceptCategory.CARDIO -> KpknLineIcons.Pulse
        ConceptCategory.EQUIPMENT -> KpknLineIcons.Grip
        ConceptCategory.HYPERTROPHY -> KpknLineIcons.Layers
        ConceptCategory.MUSCLES -> KpknLineIcons.Body
        ConceptCategory.BODY_STRUCTURES -> KpknLineIcons.Joint
        ConceptCategory.MOVEMENT -> KpknLineIcons.Route
        ConceptCategory.PATTERNS -> KpknLineIcons.Run
        ConceptCategory.NUTRITION -> KpknLineIcons.Apple
        ConceptCategory.SUPPLEMENTS -> KpknLineIcons.Tag
        ConceptCategory.FATIGUE -> KpknLineIcons.Moon
        ConceptCategory.INJURIES -> KpknLineIcons.Alert
        ConceptCategory.STUDIES -> KpknLineIcons.Book
        ConceptCategory.SCIENCE_LITERACY -> KpknLineIcons.Bulb
    }

/** Texto con las coincidencias de [query] resaltadas en crema. */
internal fun highlighted(text: String, query: String): AnnotatedString = buildAnnotatedString {
    append(text)
    conceptMatchRanges(text, query).forEach { range ->
        addStyle(SpanStyle(background = ConceptTokens.Mark, color = ConceptTokens.Ink, fontWeight = FontWeight.SemiBold), range.first, range.last + 1)
    }
}

/** Botón redondo de vidrio de 44 dp (cerrar, atrás). */
@Composable
internal fun ConceptGlassButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .kpknGlassOrFallback(LocalHazeState.current, CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = ConceptTokens.Ink)
    }
}

@Composable
internal fun ConceptModule(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ConceptTokens.ModuleShape)
            .background(ConceptTokens.Module)
            .border(1.dp, ConceptTokens.Line, ConceptTokens.ModuleShape),
        content = content,
    )
}

/** Rótulo de sección: punto de color del tema (si lo hay) y texto en mayúsculas. */
@Composable
internal fun ConceptEyebrow(text: String, modifier: Modifier = Modifier, dot: Color? = null, annotated: AnnotatedString? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        dot?.let {
            Box(Modifier.size(7.dp).clip(CircleShape).background(it))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        if (annotated != null) {
            Text(annotated, style = ConceptType.eyebrow, color = ConceptTokens.Faint)
        } else {
            Text(text.uppercase(), style = ConceptType.eyebrow, color = ConceptTokens.Faint)
        }
    }
}

/** Fila de concepto: nombre y resumen (o fragmento), resaltando la búsqueda; «En el texto» si solo coincidió en el cuerpo. */
@Composable
internal fun ConceptRow(
    concept: TrainingConcept,
    onOpen: (String) -> Unit,
    divider: Boolean,
    query: String = "",
    snippet: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { if (divider) drawLine(ConceptTokens.Line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .clickable(role = Role.Button, onClickLabel = "Leer ${concept.name}") { onOpen(concept.id) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag("concept_row_${concept.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(highlighted(concept.name, query), style = ConceptType.rowTitle, color = ConceptTokens.Ink)
            val sub = snippet ?: concept.shortDescription
            if (sub.isNotBlank()) {
                Text(highlighted(sub, query), Modifier.padding(top = 3.dp), style = ConceptType.rowSub, color = ConceptTokens.Muted)
            }
            if (snippet != null) {
                Text("EN EL TEXTO", Modifier.padding(top = 6.dp), style = ConceptType.eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.2.sp), color = ConceptTokens.Faint)
            }
        }
        Icon(KpknLineIcons.ChevronRight, null, Modifier.size(18.dp), tint = ConceptTokens.Dim)
    }
}

/** Cuadro tonal con el glifo del tema, en su color apagado. */
@Composable
internal fun ConceptGlyphTile(category: ConceptCategory, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(category.color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.glyph, null, Modifier.size((size * 0.53f).dp), tint = category.color)
    }
}
