package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programs.LibraryOriginFilter
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknType

/*
 * Filtros de la biblioteca de plantillas (P3c). Son los cuatro de siempre (perfil, días, duración y procedencia): un plan se queda si
 * cumple todos los que están puestos. Sin estado: la hoja guarda la elección y los pasa aquí.
 */

/** Perfil de la biblioteca: los cuatro objetivos del asistente y «Otros», que es lo que no encaja en ninguno. */
internal enum class ProgramLibraryProfile(val label: String) {
    ALL("Todos"),
    STRENGTH("Fuerza"),
    MUSCLE("Músculo"),
    STRENGTH_MUSCLE("Fuerza y músculo"),
    COMPLETE_ATHLETE("Atleta completo"),
    OTHER("Otros"),
}

/** Lo que se puede filtrar de un plan: sus perfiles, sus días por semana, su duración y sus procedencias. */
internal data class ProgramLibraryTraits(
    val profiles: Set<ProgramLibraryProfile>,
    val frequencies: IntRange,
    val duration: CatalogDuration,
    val origins: Set<LibraryOriginFilter>,
)

/** Los filtros elegidos. [days] null = todos los días; [duration] null = todas las duraciones. */
internal data class ProgramLibraryFilters(
    val profile: ProgramLibraryProfile = ProgramLibraryProfile.ALL,
    val days: Int? = null,
    val duration: CatalogDuration? = null,
    val origin: LibraryOriginFilter = LibraryOriginFilter.ALL,
) {
    /** Cuántos filtros están puestos (0 = ninguno). */
    val activeCount: Int
        get() = listOf(
            profile != ProgramLibraryProfile.ALL,
            days != null,
            duration != null,
            origin != LibraryOriginFilter.ALL,
        ).count { it }
}

/** ¿Pasa el plan los filtros? Cada filtro puesto tiene que cumplirse; uno en «Todos» no restringe nada. */
internal fun ProgramLibraryTraits.matches(filters: ProgramLibraryFilters): Boolean =
    (filters.profile == ProgramLibraryProfile.ALL || filters.profile in profiles) &&
        (filters.days == null || filters.days in frequencies) &&
        (filters.duration == null || filters.duration == duration) &&
        (filters.origin == LibraryOriginFilter.ALL || filters.origin in origins)

/** Cómo se nombra cada duración en el filtro. */
internal fun catalogDurationLabel(duration: CatalogDuration): String = when (duration) {
    CatalogDuration.REPEATING_WEEK -> "Semana repetible"
    CatalogDuration.REPEATING_CYCLE -> "Ciclo repetible"
    CatalogDuration.FINITE_CYCLE -> "Ciclo finito"
}

/**
 * El panel de filtros: una fila de chips por filtro, con desplazamiento horizontal si no caben. El chip elegido va relleno; tocarlo otra
 * vez no lo quita, se elige «Todos» o «Todas».
 */
@Composable
internal fun ProgramLibraryFilterPanel(
    filters: ProgramLibraryFilters,
    onChange: (ProgramLibraryFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterChipRow(
            label = "Perfil",
            options = ProgramLibraryProfile.entries,
            optionLabel = { option -> option.label },
            isSelected = { option -> option == filters.profile },
            onSelect = { option -> onChange(filters.copy(profile = option)) },
        )
        FilterChipRow(
            label = "Días por semana",
            options = listOf<Int?>(null) + (1..6).toList(),
            optionLabel = { option -> option?.toString() ?: "Todos" },
            isSelected = { option -> option == filters.days },
            onSelect = { option -> onChange(filters.copy(days = option)) },
        )
        FilterChipRow(
            label = "Duración",
            options = listOf<CatalogDuration?>(null) + CatalogDuration.entries,
            optionLabel = { option -> option?.let(::catalogDurationLabel) ?: "Todas" },
            isSelected = { option -> option == filters.duration },
            onSelect = { option -> onChange(filters.copy(duration = option)) },
        )
        FilterChipRow(
            label = "Procedencia",
            options = LibraryOriginFilter.entries,
            optionLabel = { option -> option.label },
            isSelected = { option -> option == filters.origin },
            onSelect = { option -> onChange(filters.copy(origin = option)) },
        )
    }
}

/** Una etiqueta de filtro y su fila de chips. */
@Composable
private fun <T> FilterChipRow(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                KpknChip(
                    text = optionLabel(option),
                    selected = isSelected(option),
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}
