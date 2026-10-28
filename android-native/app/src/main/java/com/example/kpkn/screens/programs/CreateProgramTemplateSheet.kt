package com.example.kpkn.screens.programs

import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PlanKind
import com.example.kpkn.data.programs.PlanLabels
import com.example.kpkn.data.programs.PlanOrigin
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.domain.onboarding.PlanGoalMatcher
import com.example.kpkn.domain.onboarding.PlanGoalProfile

internal enum class LibraryProfileFilter(val label: String) {
    ALL("Todos"), STRENGTH("Fuerza"), MUSCLE("Músculo"), STRENGTH_MUSCLE("Fuerza y músculo"),
    COMPLETE_ATHLETE("Atleta"),
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
 * - [meta]: «4 días · 16 semanas · Fuerza» o, en una rutina que se repite, «4 días · Rutina · Músculo» (escena pr-catalog del lienzo).
 * - [attribution]: la línea de atribución de la ficha (autor, edición y «No afiliado a …»); sustituye a la
 *   antigua línea «autor · edición» de las fuentes de autor.
 */
internal class LibraryCardModel(entry: CatalogEntry) {
    val title: String = entry.displayName
    val provenance: String = PlanLabels.provenanceLabel(entry)
    val summary: String = entry.summary
    val meta: String = libraryCardMeta(entry)
    val attribution: String? = entry.attributionLine?.takeIf { line -> line.isNotBlank() }
}

/**
 * La línea de meta de una tarjeta del catálogo, como el lienzo: «{días} · {semanas} · {objetivo}», p. ej. «4 días · 16 semanas ·
 * Fuerza»; en una semana que se repite, «4 días · Rutina · Músculo». El objetivo sale de las disciplinas de la ficha: «Fuerza y músculo»
 * si es de powerbuilding (o de powerlifting e hipertrofia a la vez), «Fuerza» o «Músculo»; si no es ninguna, se omite. Sin nivel ni
 * «por semana».
 */
internal fun libraryCardMeta(entry: CatalogEntry): String {
    val range = entry.supportedFrequencies
    val days = if (range.first == range.last) pluralDias(range.first) else "${range.first}–${range.last} días"
    val weeks = PlanLabels.weeksOf(entry)
    val span = when {
        entry.duration == CatalogDuration.REPEATING_WEEK -> "Rutina"
        entry.duration == CatalogDuration.REPEATING_CYCLE && weeks <= 1 -> "Rutina"
        else -> pluralSemanas(weeks)
    }
    val strength = TrainingReference.POWERLIFTING in entry.references
    val muscle = TrainingReference.HYPERTROPHY in entry.references
    val goal = when {
        TrainingReference.POWERBUILDING in entry.references || (strength && muscle) -> "Fuerza y músculo"
        strength -> "Fuerza"
        muscle -> "Músculo"
        else -> null
    }
    return listOfNotNull(days, span, goal).joinToString(PlanLabels.SEPARATOR)
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
 * ¿Es una estructura en blanco? (`kind == ESTRUCTURA`, o una plantilla sin receta): un esqueleto que la persona
 * rellena. No tiene semana escrita que el asistente pueda evaluar con el material, los días y el tiempo, y por eso el
 * asistente no la ofrece nunca como candidata (`SetupTrainingPlanner`): solo se crea desde la plantilla.
 */
internal fun isBlankStructure(entry: CatalogEntry): Boolean {
    val template = entry.template ?: return false
    return entry.kind == PlanKind.ESTRUCTURA || template.recipe == null
}

/**
 * ¿Sirve la entrada a algún objetivo del asistente? Misma regla que el prefiltro del planificador ([PlanGoalMatcher]). Lo que no sirve a ninguno (la versión anterior
 * «Músculo y cardio», `references = ∅`) no puede llegar a ser candidato: «Configurar este plan» lo dejaría en un
 * asistente que nunca lo ofrece.
 */
internal fun servesSomeWizardGoal(entry: CatalogEntry): Boolean =
    LIBRARY_GOAL_PROFILES.any { goal -> PlanGoalMatcher.matches(entry, goal) }

/**
 * Qué hace el tap de una tarjeta (C.P6, decisión D8: la biblioteca no se salta el evaluador).
 *
 *  - CON asistente al que seguir ([hasPlanAction], Planes e Inicio): la tarjeta abre su hoja «Cómo funciona» y su
 *    botón principal es «Configurar este plan», que lleva el plan al asistente. Plantillas y métodos ya no crean el
 *    programa directo: el asistente evalúa con las respuestas de la persona (material, días, tiempo) antes de
 *    proponerlo. Dos excepciones, porque el asistente nunca las ofrece y «Configurar este plan» sería un callejón sin
 *    salida (H1):
 *     · las estructuras en blanco ([isBlankStructure]) conservan el camino directo «Usar esta plantilla»: el asistente
 *       no materializa plantillas vacías (excepción de implementación al camino directo de C.P6;
 *       conserva la creación en blanco de r2 §16.1);
 *     · lo que no sirve a ningún objetivo del asistente ([servesSomeWizardGoal]; hoy, `native:strength-cardio`) abre
 *       la hoja de solo lectura, sin botón.
 *    `LibraryReachabilityTest` fija que toda tarjeta con «Configurar este plan» sale entre los candidatos del
 *    planificador para algún objetivo y alguna frecuencia.
 *  - SIN asistente (la biblioteca embebida del editor, que reemplaza o añade estructura): el camino directo de
 *    siempre. Plantilla → crear desde la plantilla; método con ficha en la biblioteca → entregarlo al llamador; el
 *    resto abre la hoja de solo lectura.
 */
internal fun libraryTapFor(
    entry: CatalogEntry,
    hasPlanAction: Boolean,
    protocolOf: (CatalogEntry) -> Protocol? = ::visibleProtocolOf,
): LibraryTap {
    if (hasPlanAction) {
        entry.template?.let { template ->
            if (isBlankStructure(entry)) return LibraryTap.OpenSheet(LibraryPrimary.UseTemplate(template))
        }
        if (!servesSomeWizardGoal(entry)) return LibraryTap.OpenSheet(null)
        return LibraryTap.OpenSheet(LibraryPrimary.ConfigurePlan)
    }
    entry.template?.let { template -> return LibraryTap.OpenSheet(LibraryPrimary.UseTemplate(template)) }
    if (entry.source == CatalogSource.PROTOCOL) {
        protocolOf(entry)?.let { protocol -> return LibraryTap.HandOffProtocol(protocol) }
    }
    return LibraryTap.OpenSheet(null)
}

/**
 * Una familia de autor con dos versiones (PHUL, PHAT) tiene una sola tarjeta en la biblioteca: la original. La adaptada a tu
 * material se alcanza desde el paso PLAN y su detalle.
 */
internal fun isLibraryFamilyEntry(entry: CatalogEntry): Boolean =
    entry.familyId == null || entry.origin == PlanOrigin.ORIGINAL

/**
 * Los planes que enseña la biblioteca con los filtros puestos (null = «Todos» o «Todas»). Es la regla que la hoja aplica a
 * sus chips, en una función pura para fijarla con una prueba sobre el catálogo real, sin depender de un clic.
 */
internal fun libraryVisibleEntries(
    entries: List<CatalogEntry>,
    days: Int?,
    duration: CatalogDuration?,
    profile: LibraryProfileFilter,
    origin: LibraryOriginFilter,
): List<CatalogEntry> = entries.filter { entry ->
    (days == null || days in entry.supportedFrequencies) &&
        (duration == null || entry.duration == duration) &&
        profileMatches(entry, profile) && originMatches(entry, origin)
}

/** Los cuatro objetivos del wizard: lo que no sirve a ninguno no es candidato del asistente ([servesSomeWizardGoal]). */
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
