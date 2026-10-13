package com.example.kpkn.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.totalProgramWeeks
import com.example.kpkn.data.programs.programCoverEntryIdOf
import com.example.kpkn.domain.training.HomeProgramLine
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.screens.home.design.HomeSectionAction
import com.example.kpkn.screens.home.design.HomeSectionLabel
import com.example.kpkn.screens.home.design.HomeTypography
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.COVER_INK
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.rememberProgramCover

/** Etiqueta de pruebas de la fila horizontal de programas del Home (sin efecto visual). */
const val HOME_PROGRAMS_ROW_TAG = "home-programs-row"

/**
 * Etiqueta de pruebas del póster de UN programa del Home (sin efecto visual).
 * Es única porque lleva el id del programa: los nombres se repiten, los ids no.
 */
fun homeProgramCardTag(programId: String): String = "home-program-card-$programId"

/** Ancho y alto de un póster (3:4). */
private val PosterWidth = 152.dp
private val PosterHeight = 204.dp

/** Tallas del título del póster, de la mayor a la menor: 17 sp es la de diseño y solo baja si la palabra más larga no cabe. */
private val PosterTitleSizesSp: List<Float> = listOf(17f, 16f, 15f, 14f)

/** Lo que sale del título de un póster: la talla en sp y si va en una sola línea con elipsis (una palabra no cabe ni a la talla menor). */
private class PosterTitleFit(val sizeSp: Float, val oneLine: Boolean)

/**
 * «Tus programas»: una fila de pósteres con la portada de cada programa, el activo primero y «Nuevo» al final. Sin programas, solo
 * «Nuevo» y un texto para empezar. [activeProgramLine] es la línea del programa activo (semana y progreso), de la que sale su
 * «Semana N de M»; [onSeeAll], si se pasa, abre la lista completa con «Ver todos».
 */
@Composable
fun HomeProgramsSection(
    programs: List<Program>,
    activeProgramId: String?,
    onProgramClick: (String) -> Unit,
    onCreateProgram: () -> Unit,
    modifier: Modifier = Modifier,
    activeProgramLine: HomeProgramLine? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (onSeeAll != null) {
            HomeSectionLabel(
                text = "TUS PROGRAMAS",
                action = { HomeSectionAction(text = "Ver todos", onClick = onSeeAll) },
            )
        } else {
            HomeSectionLabel(text = "TUS PROGRAMAS")
        }
        Spacer(Modifier.height(16.dp))

        if (programs.isEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = WizardSpacing.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NewProgramPoster(onClick = onCreateProgram)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "Crea tu primer programa o importa el tuyo.",
                    style = WizardTypography.body,
                    color = WizardColors.textMuted,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            // El activo primero y el resto en su orden (sortedByDescending es estable).
            val ordered = remember(programs, activeProgramId) { programs.sortedByDescending { it.id == activeProgramId } }
            LazyRow(
                modifier = Modifier.testTag(HOME_PROGRAMS_ROW_TAG),
                contentPadding = PaddingValues(horizontal = WizardSpacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ordered, key = { it.id }) { program ->
                    val active = program.id == activeProgramId
                    ProgramPoster(
                        program = program,
                        isActive = active,
                        line = if (active) activeProgramLine else null,
                        onClick = { onProgramClick(program.id) },
                    )
                }
                item(key = "create-program") {
                    NewProgramPoster(onClick = onCreateProgram)
                }
            }
        }
    }
}

/**
 * Póster de un programa: su portada (la foto propia si la hay; si no, el arte del motor de portadas), un velo abajo y el título. Si
 * es el activo, lleva la pastilla «ACTIVO» arriba y, abajo, su progreso y «Semana N de M»; si no, sus semanas y días, si se saben.
 * Toque = abrir el programa. Etiqueta de prueba: `homeProgramCardTag(id)`.
 */
@Composable
private fun ProgramPoster(
    program: Program,
    isActive: Boolean,
    line: HomeProgramLine?,
    onClick: () -> Unit,
) {
    val reduced = wizardReducedMotion()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleWidthPx = with(density) { (PosterWidth - 24.dp).toPx() }
    // El título baja de talla antes que partir una palabra: a 130 % de letra, «Camino» no cabe a 17 sp en 128 dp. Si ni a la
    // talla menor cabe una palabra, va en una línea con elipsis (como el héroe): nunca se parte por letras.
    val titleFit = remember(program.name, titleWidthPx, density.density, density.fontScale) {
        // A escala grande (más de 1,15) el título va de 15 sp hacia abajo: así el bloque inferior no sube por encima del velo.
        val sizes = if (density.fontScale > 1.15f) PosterTitleSizesSp.filter { it <= 15f } else PosterTitleSizesSp
        val measureWord: (String, Float) -> Float = { word, sp ->
            measurer.measure(
                text = word,
                style = HomeTypography.posterTitle.copy(fontSize = sp.sp),
                maxLines = 1,
                softWrap = false,
            ).size.width.toFloat()
        }
        PosterTitleFit(
            sizeSp = fitPosterTitleSp(program.name, titleWidthPx, sizes, measureWord),
            oneLine = posterTitleOneLine(program.name, titleWidthPx, sizes, measureWord),
        )
    }
    // La foto propia manda; el arte del motor sale del id del programa y de su plantilla (la de Home, como en las portadas).
    val cover = rememberProgramCover(programCoverEntryIdOf(program.coverImage), with(LocalDensity.current) { PosterWidth.roundToPx() })
    // La portada de estudio («Editar programa») manda; sin ella, la identidad de siempre.
    val identity = remember(program.id, program.structureTemplateId, program.coverImage) { coverIdentityForProgram(program) }
    val summary = if (isActive) null else programSummaryLine(program)
    val weekText = line?.weekLabel
    val description = buildList {
        add(program.name)
        if (isActive) add("activo")
        val detail = if (isActive) weekText?.replaceFirstChar { it.lowercaseChar() } else summary
        if (detail != null) add(detail)
    }.joinToString(", ")

    Box(
        modifier = Modifier
            .size(width = PosterWidth, height = PosterHeight)
            .clip(WizardShapes.panel)
            .testTag(homeProgramCardTag(program.id))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            CoverStudioArt(identity = identity, modifier = Modifier.fillMaxSize(), animate = false, reduced = reduced)
        }
        // Velo de la parte baja, con el criterio de K3a (PlanCover.CoverBottomVeil): en tinta y en papel, una peana negra sólida desde
        // el 56 % para que el título se lea sobre cualquier arte; en noche, un degradado; con foto propia, el velo de foto de K3a.
        val solidBase = cover == null && identity.palette != CoverPaletteKind.NOCHE
        val veil = when {
            cover != null -> arrayOf(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
            solidBase -> arrayOf(0.50f to Color.Transparent, 0.56f to COVER_INK, 1f to COVER_INK)
            else -> arrayOf(0.45f to Color.Transparent, 1f to COVER_INK.copy(alpha = 0.92f))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colorStops = veil)),
        ) {}
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .height(24.dp)
                    .clip(KpknShape.pill)
                    .background(WizardColors.text)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "ACTIVO", style = HomeTypography.posterPill, color = WizardColors.ctaContent)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            Text(
                text = program.name,
                style = HomeTypography.posterTitle.copy(fontSize = titleFit.sizeSp.sp, lineHeight = (titleFit.sizeSp * 1.12f).sp),
                color = WizardColors.text,
                maxLines = if (titleFit.oneLine) 1 else 2,
                softWrap = !titleFit.oneLine,
                overflow = TextOverflow.Ellipsis,
            )
            if (isActive) {
                val index = line?.weekIndex
                val count = line?.weekCount
                if (index != null && count != null && count > 0) {
                    Spacer(Modifier.height(10.dp))
                    PosterProgress(fraction = index.toFloat() / count)
                }
                if (weekText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = weekText,
                        style = WizardTypography.note.copy(lineHeight = 14.sp),
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (summary != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = WizardTypography.note.copy(lineHeight = 14.sp),
                    color = WizardColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Progreso del programa activo: una línea de 3 dp, blanca al 16 %, con el avance en el músculo. */
@Composable
private fun PosterProgress(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(SymbolPalette.musculo),
        ) {}
    }
}

/**
 * Póster «Nuevo», del mismo tamaño que los demás: un módulo con su filete fino (`moduleBorder`), el «+» en un círculo `inset` de 44 dp y
 * «Crear o importar» en `bodyStrong`. Etiqueta de prueba: `home-programs-new`.
 */
@Composable
private fun NewProgramPoster(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(width = PosterWidth, height = PosterHeight)
            .clip(WizardShapes.panel)
            .background(KpknSurface.module, WizardShapes.panel)
            .border(1.dp, KpknSurface.moduleBorder, WizardShapes.panel)
            .testTag("home-programs-new")
            .semantics(mergeDescendants = true) { contentDescription = "Crear o importar un programa" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(KpknSurface.inset, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = KpknLineIcons.Plus,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = WizardColors.text,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Crear o importar",
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Lo que se sabe del programa para su póster, como «12 semanas · 4 días». Las semanas solo cuentan desde dos (un ciclo de una
 * semana no son «1 semana»); los días salen de `weekDays` si está. Null si no hay nada que decir.
 */
internal fun programSummaryLine(program: Program): String? {
    val weeks = program.totalProgramWeeks
    val days = program.weekDays?.takeIf { it in 1..7 }
    val parts = buildList {
        if (weeks >= 2) add("$weeks semanas")
        if (days != null) add(if (days == 1) "1 día" else "$days días")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Talla en sp del título de un póster: la mayor de [sizesSp] (de mayor a menor) con la que cabe la palabra más larga de [name] en
 * [widthPx]; si ninguna cabe, la menor. Así una palabra no se parte por letras y el título sigue en dos líneas con «…».
 */
internal fun fitPosterTitleSp(
    name: String,
    widthPx: Float,
    sizesSp: List<Float>,
    measureWordPx: (word: String, sp: Float) -> Float,
): Float {
    val words = name.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return sizesSp.first()
    return sizesSp.firstOrNull { sp -> words.all { measureWordPx(it, sp) <= widthPx } } ?: sizesSp.last()
}

/**
 * Si alguna palabra de [name] no cabe en [widthPx] ni a la talla menor de [sizesSp]: el título va entonces en una sola línea con
 * elipsis, como el héroe, y ninguna palabra se parte por letras.
 */
internal fun posterTitleOneLine(
    name: String,
    widthPx: Float,
    sizesSp: List<Float>,
    measureWordPx: (word: String, sp: Float) -> Float,
): Boolean {
    val smallest = sizesSp.last()
    return name.split(Regex("\\s+")).filter { it.isNotBlank() }.any { measureWordPx(it, smallest) > widthPx }
}
