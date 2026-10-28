package com.example.kpkn.screens.programs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.programdetail.design.ProgramAnchoredMenuItem
import com.example.kpkn.screens.programdetail.design.ProgramItemMenu
import com.example.kpkn.screens.programdetail.design.structure.catalogDurationLabel
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.abs

/** Las portadas de líneas de las miniaturas del catálogo: el par sale del id del plan, así cada plan conserva siempre la suya. */
private val CATALOG_LINE_STYLES = listOf(LineCoverStyle.ECO, LineCoverStyle.ORBITAS, LineCoverStyle.BRUMA, LineCoverStyle.RELIEVE)
private val CATALOG_LINE_BACKS = listOf(LineCoverBackground.CARBON, LineCoverBackground.GRAFITO, LineCoverBackground.NOCHE, LineCoverBackground.MUSGO)

/** Qué menú anclado de los chips está abierto. */
private enum class CatalogMenu { DAYS, DURATION }

/** La hoja «Cómo funciona» de una tarjeta abierta y lo que hace su botón principal. */
private data class CatalogOpenSheet(val entry: CatalogEntry, val primary: LibraryPrimary?)

/** El título de la página: Syne Bold 26 sp. */
private val CatalogTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.6).sp,
)

/**
 * La página «Catálogo»: los planes de la biblioteca a pantalla completa, por encima de Programas (no es una ruta nueva). Filtra por
 * perfil, días, duración y procedencia, y cada plan tiene el mismo tap que tenía en la hoja de antes ([libraryTapFor]): con asistente,
 * «Configurar este plan»; sin él, el camino directo. Atrás cierra primero un menú abierto y luego la página.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramCatalogPage(
    onBack: () -> Unit,
    hasPlanAction: Boolean,
    onCreateFromTemplate: (ProgramTemplateOption) -> Unit,
    onSelectProtocol: (Protocol) -> Unit,
    onSelectPlan: ((CatalogEntry) -> Unit)?,
    onOpenConcept: ((String) -> Unit)?,
) {
    var profileFilter by remember { mutableStateOf(LibraryProfileFilter.ALL) }
    var daysFilter by remember { mutableStateOf<Int?>(null) }
    var durationFilter by remember { mutableStateOf<CatalogDuration?>(null) }
    var originFilter by remember { mutableStateOf(LibraryOriginFilter.ALL) }
    var openMenu by remember { mutableStateOf<CatalogMenu?>(null) }
    var daysBounds by remember { mutableStateOf<Rect?>(null) }
    var durationBounds by remember { mutableStateOf<Rect?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var openSheet by remember { mutableStateOf<CatalogOpenSheet?>(null) }

    // Lo mismo que la biblioteca de antes: solo lo listado y publicado, una tarjeta por familia y en el orden editorial.
    val entries = remember {
        libraryOrder(
            PersonalizedPlanCatalog.listedEntries()
                .filter { it.publication == PublicationState.PUBLISHED && isLibraryFamilyEntry(it) },
        )
    }
    val visible = libraryVisibleEntries(entries, daysFilter, durationFilter, profileFilter, originFilter)

    // Atrás: la página se cierra, pero antes un menú abierto (BackHandler posterior = prioridad).
    BackHandler(onBack = onBack)
    BackHandler(enabled = openMenu != null) { openMenu = null }

    KpknPortal {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KpknSurface.canvas)
                .statusBarsPadding(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "barra") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = onBack)
                        Spacer(Modifier.weight(1f))
                        // No hay icono de filtro en el set: «Más opciones» es el que queda, como manda la guía de la página.
                        KpknIconButton(
                            icon = KpknLineIcons.More,
                            contentDescription = "Filtros",
                            onClick = { showFilters = true },
                        )
                    }
                }
                item(key = "titulo") {
                    Text(
                        text = "Catálogo",
                        style = CatalogTitleStyle,
                        color = WizardColors.text,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
                item(key = "perfil") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibraryProfileFilter.entries.forEach { profile ->
                            KpknChip(
                                text = profile.label,
                                selected = profile == profileFilter,
                                onClick = { profileFilter = profile },
                            )
                        }
                    }
                }
                item(key = "dias-duracion") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Cada chip muestra su valor elegido; sin elegir, «Días: Todos» y «Duración: Todas».
                        KpknChip(
                            text = daysFilter?.let(::pluralDias) ?: "Días: Todos",
                            selected = daysFilter != null,
                            onClick = { openMenu = CatalogMenu.DAYS },
                            modifier = Modifier
                                .onGloballyPositioned { daysBounds = it.boundsInWindow() }
                                .testTag("catalog-days"),
                        )
                        KpknChip(
                            text = durationFilter?.let(::catalogDurationLabel) ?: "Duración: Todas",
                            selected = durationFilter != null,
                            onClick = { openMenu = CatalogMenu.DURATION },
                            modifier = Modifier
                                .onGloballyPositioned { durationBounds = it.boundsInWindow() }
                                .testTag("catalog-duration"),
                        )
                    }
                }
                if (visible.isEmpty()) {
                    item(key = "vacio") {
                        Text(
                            text = "No hay planes con esos filtros.",
                            style = KpknType.body,
                            color = WizardColors.textMuted,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                items(visible, key = { entry -> entry.id }) { entry ->
                    CatalogPlanCard(
                        entry = entry,
                        onClick = {
                            when (val tap = libraryTapFor(entry, hasPlanAction)) {
                                is LibraryTap.HandOffProtocol -> onSelectProtocol(tap.protocol)
                                is LibraryTap.OpenSheet -> openSheet = CatalogOpenSheet(entry, tap.primary)
                            }
                        },
                    )
                }
            }
        }
    }

    // Menú de días: 1 a 6 o «Todos»; se ancla al chip de días.
    if (openMenu == CatalogMenu.DAYS) {
        daysBounds?.let { anchor ->
            ProgramItemMenu(
                items = (listOf<Int?>(null) + (1..6).toList()).map { option ->
                    ProgramAnchoredMenuItem(
                        label = option?.let(::pluralDias) ?: "Todos",
                        onClick = { daysFilter = option },
                    )
                },
                anchor = anchor,
                onDismiss = { openMenu = null },
            )
        }
    }
    // Menú de duración: «Todas» o cada duración del catálogo; se ancla al chip de duración.
    if (openMenu == CatalogMenu.DURATION) {
        durationBounds?.let { anchor ->
            ProgramItemMenu(
                items = (listOf<CatalogDuration?>(null) + CatalogDuration.entries).map { option ->
                    ProgramAnchoredMenuItem(
                        label = option?.let(::catalogDurationLabel) ?: "Todas",
                        onClick = { durationFilter = option },
                    )
                },
                anchor = anchor,
                onDismiss = { openMenu = null },
            )
        }
    }

    // Filtros de procedencia: los chips aplican al elegirlos; «Listo» cierra la hoja.
    if (showFilters) {
        KpknSheet(onDismissRequest = { showFilters = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(text = "Filtros", style = NewProgramTitleStyle, color = WizardColors.text)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Procedencia", style = KpknType.caption, color = WizardColors.textMuted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibraryOriginFilter.entries.forEach { origin ->
                            KpknChip(
                                text = origin.label,
                                selected = origin == originFilter,
                                onClick = { originFilter = origin },
                            )
                        }
                    }
                }
                KpknPrimaryPill(text = "Listo", onClick = { showFilters = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // La hoja «Cómo funciona» de la tarjeta: la misma que la biblioteca, con su botón principal si lo tiene.
    openSheet?.let { sheet ->
        val primary = sheet.primary
        // Cierra la hoja y entrega la acción, igual que la biblioteca.
        val confirm: (() -> Unit)? = primary?.let { action ->
            val onConfirm: () -> Unit = {
                openSheet = null
                when (action) {
                    is LibraryPrimary.UseTemplate -> onCreateFromTemplate(action.template)
                    LibraryPrimary.ConfigurePlan -> onSelectPlan?.invoke(sheet.entry)
                }
            }
            onConfirm
        }
        PlanInfoSheet(
            entry = sheet.entry,
            mode = if (primary != null) PlanInfoMode.LIBRARY else PlanInfoMode.READ_ONLY,
            onDismiss = { openSheet = null },
            onPrimaryAction = confirm,
            onOpenConcept = onOpenConcept,
            primaryActionLabel = primary?.label,
        )
    }
}

/**
 * Una tarjeta del catálogo: miniatura de portada de líneas (56 × 70 dp), nombre, meta y procedencia. Al tocarla se abre su hoja.
 * La miniatura sale del id del plan, así que cada plan se ve siempre igual.
 */
@Composable
private fun CatalogPlanCard(entry: CatalogEntry, onClick: () -> Unit) {
    val model = remember(entry.id) { LibraryCardModel(entry) }
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog-plan-${entry.id}")
            .clip(shape)
            .background(KpknSurface.module, shape)
            .border(1.dp, KpknSurface.moduleBorder, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineCoverArt(
            spec = LineCoverSpec(
                style = CATALOG_LINE_STYLES[abs(entry.id.hashCode()) % CATALOG_LINE_STYLES.size],
                background = CATALOG_LINE_BACKS[abs(entry.id.hashCode() / 4) % CATALOG_LINE_BACKS.size],
                seed = abs(entry.id.hashCode()) % 9 + 1,
                weekLoads = emptyList(),
            ),
            modifier = Modifier.size(width = 56.dp, height = 70.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = model.title,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = model.meta,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = model.provenance,
                style = KpknType.caption,
                color = WizardColors.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = KpknLineIcons.ChevronRight,
            contentDescription = null,
            tint = WizardColors.textFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}
