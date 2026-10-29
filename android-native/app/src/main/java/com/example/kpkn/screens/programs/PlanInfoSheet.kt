package com.example.kpkn.screens.programs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.kpkn.data.exercises.catalogConfigurationDisplayName
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.domain.training.CompositionMetadataHolder
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaOverlay

/**
 * La ficha de un plan del catálogo (lienzo Arreglos v1, escenas p-catalog y p-catalog-week): la misma hoja del asistente
 * ([PlanFichaOverlay]) en lugar de la hoja de texto «Cómo funciona». La abren el catálogo, el detalle del programa («Ver cómo
 * funciona», en modo lectura) y [ProtocolDetailSheet]. El texto sale de [PlanInfoModelBuilder] y la ficha, de [PlanInfoFicha].
 *
 * @param readyWeek primera semana real del candidato listo del asistente; solo la pintan los planes sin receta.
 * @param onPrimaryAction acción del botón del pie fijo; sin ella no hay pie (consulta de solo lectura).
 * @param onOpenConcept se conserva por compatibilidad con sus llamadores: la ficha no tiene glosario, así que no se usa.
 * @param primaryActionLabel sustituye la etiqueta que el modo le da al botón del pie (la usa [ProtocolDetailSheet] para
 *   conservar «Usar este plan» o «Reemplazar plan» de su llamador).
 * @param secondaryActionLabel texto de una acción secundaria opcional al final («Crear una copia»), con [onSecondaryAction].
 */
@Composable
fun PlanInfoSheet(
    entry: CatalogEntry,
    mode: PlanInfoMode,
    readyWeek: ReadyWeekSnapshot? = null,
    onDismiss: () -> Unit,
    onPrimaryAction: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onOpenConcept: ((String) -> Unit)? = null,
    primaryActionLabel: String? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val ficha = remember(entry.id, mode, readyWeek, primaryActionLabel, secondaryActionLabel, onSecondaryAction != null) {
        val names: (String) -> String? = { configurationId -> catalogConfigurationDisplayName(configurationId) }
        val info = PlanInfoModelBuilder.build(
            entry = entry,
            mode = mode,
            names = names,
            equipmentOf = { configurationId ->
                CompositionMetadataHolder.current?.metadata(configurationId)?.equipmentId
            },
            readyWeek = readyWeek,
        )
        PlanInfoFicha.build(
            entry = entry,
            info = info,
            names = names,
            primaryLabel = primaryActionLabel ?: info.primaryActionLabel,
            secondaryLabel = secondaryActionLabel?.takeIf { onSecondaryAction != null },
        )
    }
    PlanFichaOverlay(
        ficha = ficha,
        onClose = onDismiss,
        onPrimary = onPrimaryAction?.takeIf { ficha.content.primaryLabel != null },
        onSecondary = onSecondaryAction,
    )
}
