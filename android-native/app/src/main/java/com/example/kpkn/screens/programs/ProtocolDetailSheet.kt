package com.example.kpkn.screens.programs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFicha
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaContent
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaCopy
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaOverlay

/** Texto de la acción secundaria que ofrece copiar el método antes de usarlo. */
internal const val CREATE_COPY_LABEL = "Crear una copia"

/**
 * Ficha de un método de la biblioteca antes de usarlo. Conserva su firma para sus llamadores, pero no redacta nada: busca la entrada
 * del catálogo del protocolo y delega en [PlanInfoSheet], la ficha del plan del catálogo. El botón del pie mantiene la etiqueta de
 * cada llamador («Usar este plan», «Reemplazar plan») y «Crear una copia» va como acción secundaria al final.
 */
@Composable
fun ProtocolDetailSheet(
    protocol: Protocol,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    continueLabel: String = "Usar este plan",
    onCreateCopy: (() -> Unit)? = null,
) {
    val entry = remember(protocol.id) { PersonalizedPlanCatalog.find("protocol:${protocol.id}") }
    if (entry != null) {
        PlanInfoSheet(
            entry = entry,
            mode = PlanInfoMode.LIBRARY,
            onDismiss = onDismiss,
            onPrimaryAction = onContinue,
            primaryActionLabel = continueLabel,
            secondaryActionLabel = if (onCreateCopy != null) CREATE_COPY_LABEL else null,
            onSecondaryAction = onCreateCopy,
        )
    } else {
        val ficha = remember(protocol.id, continueLabel, onCreateCopy != null) {
            protocolFallbackFicha(protocol, continueLabel, onCreateCopy != null)
        }
        PlanFichaOverlay(ficha = ficha, onClose = onDismiss, onPrimary = onContinue, onSecondary = onCreateCopy)
    }
}

/** Un protocolo que el catálogo no ofrece (oculto): la misma ficha con solo su nombre y su descripción, sin códigos. */
internal fun protocolFallbackFicha(protocol: Protocol, continueLabel: String, canCopy: Boolean): PlanFicha = PlanFicha(
    detail = PlanDetailModel(
        card = PlanCardModel(
            id = "protocol:${protocol.id}",
            title = protocol.name,
            kicker = PlanFichaCopy.EYEBROW_CATALOG,
            blurb = protocol.description,
            profile = null,
            daysLabel = "",
            minutesLabel = "",
            levelLabel = "",
            coverSeed = Math.floorMod(protocol.id.hashCode(), 9973),
        ),
        description = protocol.description,
        mainExercises = emptyList(),
        blocks = emptyList(),
        week = emptyList(),
        reasons = emptyList(),
        notes = emptyList(),
        attribution = null,
    ),
    content = PlanFichaContent(
        primaryLabel = continueLabel,
        secondaryLabel = if (canCopy) CREATE_COPY_LABEL else null,
    ),
)
