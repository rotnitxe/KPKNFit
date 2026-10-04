package com.example.kpkn.screens.programs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanLabels
import com.example.kpkn.data.programs.PlanOrigin
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.domain.onboarding.PlanGoalMatcher
import com.example.kpkn.domain.onboarding.PlanGoalProfile
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetLightChip
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.KpknSheetWhiteButton

private enum class LibraryProfileFilter(val label: String) {
    ALL("Todos"), STRENGTH("Fuerza"), MUSCLE("Músculo"), STRENGTH_MUSCLE("Fuerza y músculo"),
    COMPLETE_ATHLETE("Atleta completo"), OTHER("Otros"),
}

/** Los filtros de procedencia coinciden con la etiqueta de la tarjeta (ver [originMatches]). */
internal enum class LibraryOriginFilter(val label: String) {
    ALL("Todas"), ORIGINAL("Original fiel"), ADAPTED("Adaptación KPKN"), KPKN("Plan KPKN"), LEGACY("Versión KPKN"),
}

/**
 * Lo que enseña la tarjeta de un plan en la biblioteca (C.P5): solo la ficha editorial, ningún texto escrito
 * a mano ni el nivel en inglés de `level.name`. Es un modelo puro para poder fijarlo con una prueba de JVM.
 *
 * - [title]: el nombre del plan (`displayName`).
 * - [provenance]: de dónde sale («Plan KPKN», «Versión KPKN del método de …», «Versión anterior»).
 * - [summary]: qué haces, para quién es y qué necesitas.
 * - [meta]: «4 días por semana · Semana que se repite · Intermedio».
 * - [attribution]: la línea de atribución de la ficha (autor, edición y «No afiliado a …»); sustituye a la
 *   antigua línea «autor · edición» de las fuentes de autor.
 */
internal class LibraryCardModel(entry: CatalogEntry) {
    val title: String = entry.displayName
    val provenance: String = PlanLabels.provenanceLabel(entry)
    val summary: String = entry.summary
    val meta: String = PlanLabels.metaLine(entry)
    val attribution: String? = entry.attributionLine?.takeIf { line -> line.isNotBlank() }
}

/**
 * El orden de la biblioteca: el editorial de DEC-w2-06, por `rank` y, a igual `rank`, por nombre. Primero los
 * planes propios, luego los originales y las adaptaciones de autor, las plantillas y los planes KPKN de receta
 * fija, los métodos de terceros y los históricos; las especializaciones, los complementos, las estructuras en
 * blanco y las versiones anteriores cierran la lista (todo lo que no es un plan completo tiene `rank` >= 900).
 * Antes salía en el orden natural del catálogo, que no dice nada del plan.
 */
internal fun libraryOrder(entries: List<CatalogEntry>): List<CatalogEntry> =
    entries.sortedWith(compareBy<CatalogEntry> { entry -> entry.rank }.thenBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.displayName })

/** Qué hace el botón principal de la hoja «Cómo funciona» cuando se abre desde una tarjeta de la biblioteca. */
internal sealed class LibraryPrimary(val label: String) {
    /** Crea el programa desde la plantilla (el camino directo, solo cuando no hay asistente al que seguir). */
    data class UseTemplate(val template: ProgramTemplateOption) : LibraryPrimary("Usar esta plantilla")

    /** Sigue al asistente de configuración con este plan. */
    data object ConfigurePlan : LibraryPrimary(PlanInfoModelBuilder.PRIMARY_LIBRARY)
}

/** Qué pasa al tocar una tarjeta de la biblioteca. */
internal sealed interface LibraryTap {
    /**
     * Un método de la biblioteca, SOLO cuando no hay asistente al que seguir (la biblioteca embebida del editor,
     * `MacrocycleEditorLegacy`): el tap se lo entrega al llamador (`onSelectProtocol`), que lo presenta con
     * `ProtocolDetailSheet`, y esa hoja delega en [PlanInfoSheet]: abrir otra hoja aquí encima mostraría dos
     * veces la misma ficha, una tras otra. Con asistente (Planes, Inicio) el método sigue al asistente.
     */
    data class HandOffProtocol(val protocol: Protocol) : LibraryTap

    /**
     * Abre la hoja «Cómo funciona» de la tarjeta. [primary] es lo que hace su botón principal; sin él no hay
     * acción posible (un plan sin asistente al que seguir) y la hoja es de solo lectura.
     */
    data class OpenSheet(val primary: LibraryPrimary?) : LibraryTap
}

/** El método de la biblioteca que corresponde a una entrada, si existe y se ofrece (`null` en los planes propios y de autor). */
internal fun visibleProtocolOf(entry: CatalogEntry): Protocol? =
    PROTOCOL_LIBRARY.firstOrNull { protocol -> protocol.id == entry.sourceId && protocol.isVisibleForApplication }

/**
 * Qué hace el tap de una tarjeta (C.P6, decisión D8 y r2 §16.1: la biblioteca no se salta el evaluador).
 *
 *  - CON asistente al que seguir ([hasPlanAction], Planes e Inicio): TODA tarjeta —plan propio, de autor, plantilla
 *    o método— abre su hoja «Cómo funciona» y su botón principal es «Configurar este plan», que lleva el plan al
 *    asistente. Plantillas y métodos ya no crean el programa directo: el asistente evalúa con las respuestas de la
 *    persona (material, días, tiempo) antes de proponerlo.
 *  - SIN asistente (la biblioteca embebida del editor, que reemplaza o añade estructura): el camino directo de
 *    siempre. Plantilla → crear desde la plantilla; método con ficha en la biblioteca → entregarlo al llamador; el
 *    resto abre la hoja de solo lectura.
 */
internal fun libraryTapFor(
    entry: CatalogEntry,
    hasPlanAction: Boolean,
    protocolOf: (CatalogEntry) -> Protocol? = ::visibleProtocolOf,
): LibraryTap {
    if (hasPlanAction) return LibraryTap.OpenSheet(LibraryPrimary.ConfigurePlan)
    entry.template?.let { template -> return LibraryTap.OpenSheet(LibraryPrimary.UseTemplate(template)) }
    if (entry.source == CatalogSource.PROTOCOL) {
        protocolOf(entry)?.let { protocol -> return LibraryTap.HandOffProtocol(protocol) }
    }
    return LibraryTap.OpenSheet(null)
}

/** La hoja de una tarjeta abierta y lo que hace su botón principal. */
private data class LibrarySheet(val entry: CatalogEntry, val primary: LibraryPrimary?)

/** One Plans library for complete programs; standalone session templates stay in the session editor. */
@Composable
fun CreateProgramTemplateSheet(
    onDismiss: () -> Unit,
    onCreateBlank: (() -> Unit)?,
    onCreateFromTemplate: (ProgramTemplateOption) -> Unit,
    onSelectProtocol: (Protocol) -> Unit = {},
    /**
     * Con el asistente disponible TODA tarjeta —plan propio, de autor, plantilla o método— sigue al asistente con el
     * plan elegido, en lugar de crear el programa directo (C.P6, decisión D8). Sin él (la biblioteca embebida del
     * editor) rige el camino directo: [onCreateFromTemplate] y [onSelectProtocol].
     */
    onSelectPlan: ((CatalogEntry) -> Unit)? = null,
    /** Abre un concepto de «Conceptos clave» desde el glosario de la hoja «Cómo funciona»; null = sin enlace. */
    onOpenConcept: ((String) -> Unit)? = null,
) {
    var profileFilter by remember { mutableStateOf(LibraryProfileFilter.ALL) }
    var daysFilter by remember { mutableStateOf<Int?>(null) }
    var durationFilter by remember { mutableStateOf<CatalogDuration?>(null) }
    var originFilter by remember { mutableStateOf(LibraryOriginFilter.ALL) }
    var openSheet by remember { mutableStateOf<LibrarySheet?>(null) }

    // Solo lo listado: los históricos ocultos (C.P2b) siguen resolviéndose por id, pero no se ofrecen. En el
    // orden editorial (rank y nombre), no en el natural del catálogo.
    val entries = remember {
        libraryOrder(PersonalizedPlanCatalog.listedEntries().filter { it.publication == PublicationState.PUBLISHED })
    }
    val visible = entries.filter { entry ->
        (daysFilter == null || daysFilter in entry.supportedFrequencies) &&
            (durationFilter == null || entry.duration == durationFilter) &&
            profileMatches(entry, profileFilter) && originMatches(entry, originFilter)
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Planes", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            Text(
                "Elige un plan para ver cómo funciona. Antes de activarlo confirmamos tu material, tus días y tu tiempo.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )

            FilterRow(
                label = "Perfil",
                options = LibraryProfileFilter.entries.map { it.label },
                selected = profileFilter.label,
                onSelect = { label -> profileFilter = LibraryProfileFilter.entries.first { it.label == label } },
            )
            FilterRow(
                label = "Días",
                options = listOf("Todos") + (1..6).map(Int::toString),
                selected = daysFilter?.toString() ?: "Todos",
                onSelect = { label -> daysFilter = label.toIntOrNull() },
            )
            FilterRow(
                label = "Duración",
                options = listOf("Todas", "Semana repetible", "Ciclo finito"),
                selected = when (durationFilter) {
                    null -> "Todas"
                    CatalogDuration.REPEATING_WEEK -> "Semana repetible"
                    CatalogDuration.REPEATING_CYCLE -> "Ciclo repetible"
                    CatalogDuration.FINITE_CYCLE -> "Ciclo finito"
                },
                onSelect = { label ->
                    durationFilter = when (label) {
                        "Semana repetible" -> CatalogDuration.REPEATING_WEEK
                        "Ciclo repetible" -> CatalogDuration.REPEATING_CYCLE
                        "Ciclo finito" -> CatalogDuration.FINITE_CYCLE
                        else -> null
                    }
                },
            )
            FilterRow(
                label = "Procedencia",
                options = LibraryOriginFilter.entries.map { it.label },
                selected = originFilter.label,
                onSelect = { label -> originFilter = LibraryOriginFilter.entries.first { it.label == label } },
            )

            Column(
                Modifier.fillMaxWidth().heightIn(max = 390.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (visible.isEmpty()) {
                    Text("No hay planes con esos filtros.", color = Color.White.copy(alpha = 0.72f))
                }
                visible.forEach { entry ->
                    LibraryCard(
                        entry = entry,
                        onClick = {
                            when (val tap = libraryTapFor(entry, hasPlanAction = onSelectPlan != null)) {
                                is LibraryTap.HandOffProtocol -> onSelectProtocol(tap.protocol)
                                is LibraryTap.OpenSheet -> openSheet = LibrarySheet(entry, tap.primary)
                            }
                        },
                    )
                }
            }
            onCreateBlank?.let { create -> KpknSheetWhiteButton(text = "Crear desde cero", onClick = create) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar", color = Color.White) }
        }
    }

    openSheet?.let { sheet ->
        val primary = sheet.primary
        // Cierra la hoja y entrega la acción: lo mismo que ejecutaba el tap antes de abrir la hoja.
        val confirm: (() -> Unit)? = primary?.let { action ->
            val onConfirm: () -> Unit = {
                openSheet = null
                when (action) {
                    is LibraryPrimary.UseTemplate -> onCreateFromTemplate(action.template)
                    LibraryPrimary.ConfigurePlan -> {
                        onSelectPlan?.invoke(sheet.entry)
                    }
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

/** La tarjeta de un plan: nombre, procedencia, qué haces, metadatos y atribución. Al tocarla se abre su hoja. */
@Composable
private fun LibraryCard(entry: CatalogEntry, onClick: () -> Unit) {
    val model = remember(entry.id) { LibraryCardModel(entry) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("library-plan-${entry.id}"),
        shape = RoundedCornerShape(16.dp),
        color = KpknSheetTokens.Panel,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.title, fontWeight = FontWeight.Bold, color = Color.White)
            Text(model.provenance, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.labelMedium)
            Text(model.summary, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Text(model.meta, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
            model.attribution?.let { attribution ->
                Text(attribution, color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.58f))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                KpknSheetLightChip(label = option, selected = selected == option, onClick = { onSelect(option) })
            }
        }
    }
}

/** Los cuatro objetivos del wizard: lo que no sirve a ninguno cae en «Otros». */
private val LIBRARY_GOAL_PROFILES: List<PlanGoalProfile> = listOf(
    PlanGoalProfile.STRENGTH,
    PlanGoalProfile.MUSCLE,
    PlanGoalProfile.STRENGTH_MUSCLE,
    PlanGoalProfile.COMPLETE_ATHLETE,
)

/**
 * Misma regla que el wizard (DEC-w2-06): decide [PlanGoalMatcher], no las capacidades
 * sueltas. Así «Fuerza» no lista `powerbuilding-foundation` solo por declarar fuerza.
 */
private fun profileMatches(entry: CatalogEntry, filter: LibraryProfileFilter): Boolean = when (filter) {
    LibraryProfileFilter.ALL -> true
    LibraryProfileFilter.STRENGTH -> PlanGoalMatcher.matches(entry, PlanGoalProfile.STRENGTH)
    LibraryProfileFilter.MUSCLE -> PlanGoalMatcher.matches(entry, PlanGoalProfile.MUSCLE)
    LibraryProfileFilter.STRENGTH_MUSCLE -> PlanGoalMatcher.matches(entry, PlanGoalProfile.STRENGTH_MUSCLE)
    LibraryProfileFilter.COMPLETE_ATHLETE -> PlanGoalMatcher.matches(entry, PlanGoalProfile.COMPLETE_ATHLETE)
    LibraryProfileFilter.OTHER -> LIBRARY_GOAL_PROFILES.none { PlanGoalMatcher.matches(entry, it) }
}

/**
 * La procedencia sale de la ficha editorial ([CatalogEntry.origin]), la misma que decide la etiqueta de la tarjeta
 * ([PlanLabels.provenanceLabel]): lo que la tarjeta llama «Plan KPKN» sale en «Plan KPKN», también los cinco planes
 * KPKN de receta fija (autor «KPKN Fit»), que antes caían en «Versión KPKN». «Versión KPKN» agrupa los métodos de
 * terceros y sus versiones anteriores.
 */
internal fun originMatches(entry: CatalogEntry, filter: LibraryOriginFilter): Boolean = when (filter) {
    LibraryOriginFilter.ALL -> true
    LibraryOriginFilter.ORIGINAL -> entry.origin == PlanOrigin.ORIGINAL
    LibraryOriginFilter.ADAPTED -> entry.origin == PlanOrigin.ADAPTED
    LibraryOriginFilter.KPKN -> entry.origin == PlanOrigin.KPKN
    LibraryOriginFilter.LEGACY -> entry.origin == PlanOrigin.KPKN_VERSION || entry.origin == PlanOrigin.LEGACY_VERSION
}
