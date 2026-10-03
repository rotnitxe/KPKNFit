package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.NativeProgressionIdentity
import com.example.kpkn.data.models.NativeProgressionProposal
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolution
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.training.CompositionMetadataHolder
import com.example.kpkn.domain.training.NativeLoadConventions
import com.example.kpkn.domain.training.NativeProgressionText
import com.example.kpkn.domain.training.NativeWorkoutProgressionRuntime

/** Un renglón de la tarjeta de progresión: nombre del ejercicio y texto llano. */
data class NativeProgressionItemUi(
    val id: String,
    val title: String,
    val body: String,
)

/** Lo que dibuja la tarjeta de Detalle del programa: progresiones por revisar y avisos recientes. */
data class NativeProgressionCardUi(
    val proposals: List<NativeProgressionItemUi> = emptyList(),
    val notices: List<NativeProgressionItemUi> = emptyList(),
) {
    val pendingCount: Int get() = proposals.size

    /** «1 progresión por revisar» / «N progresiones por revisar»; null si no hay ninguna. */
    val pendingLabel: String?
        get() = when (proposals.size) {
            0 -> null
            1 -> "1 progresión por revisar"
            else -> "${proposals.size} progresiones por revisar"
        }
}

/**
 * Presentación de las propuestas de progresión nativa (H-UI). Funciones puras: el nombre del
 * ejercicio se resuelve al dibujar (desde el programa y, si no, desde el catálogo), nunca se
 * imprime un identificador interno, los avisos caducan y cada respuesta tiene su confirmación.
 */
object NativeProgressionCardModel {
    /** Un aviso informativo se muestra una semana; después deja de aparecer (queda en el registro). */
    const val NOTICE_VISIBLE_MS: Long = 7L * 24L * 60L * 60L * 1000L

    /** Tope de avisos a la vez; al entenderlos o caducar aparecen los siguientes. */
    const val MAX_VISIBLE_NOTICES: Int = 3

    /** Las propuestas guardadas antes de H-UI empiezan así (jerga interna); se reescriben al dibujar. */
    private const val LEGACY_EXPLANATION_PREFIX = "Dos exposiciones completas"

    fun build(
        program: Program,
        nowMs: Long,
        displayNameOf: (String) -> String? = { null },
    ): NativeProgressionCardUi {
        val proposals = program.nativeProgressionProposals.map { proposal ->
            NativeProgressionItemUi(
                id = proposal.proposalId,
                title = exerciseName(program, proposal.identity, displayNameOf),
                body = proposalBody(proposal, displayNameOf),
            )
        }
        val notices = program.nativeProgressionAudit
            .filter { it.userFacingNotice && nowMs - it.resolvedAtMs <= NOTICE_VISIBLE_MS }
            .sortedByDescending { it.resolvedAtMs }
            .take(MAX_VISIBLE_NOTICES)
            .map { notice ->
                NativeProgressionItemUi(
                    id = notice.proposalId,
                    title = notice.identity?.let { exerciseName(program, it, displayNameOf) }
                        ?: if (notice.proposalId.startsWith(NativeWorkoutProgressionRuntime.NEW_BLOCK_NOTICE_PREFIX)) {
                            "Nuevo bloque"
                        } else {
                            "Progresión de carga"
                        },
                    body = notice.reason,
                )
            }
        return NativeProgressionCardUi(proposals = proposals, notices = notices)
    }

    /**
     * Nombre del ejercicio como lo ve el atleta en sus sesiones. Si el programa ya no lo tiene,
     * el nombre del catálogo; en último caso, el identificador sin guiones bajos. Los ejercicios
     * unilaterales indican el lado.
     */
    fun exerciseName(
        program: Program,
        identity: NativeProgressionIdentity,
        displayNameOf: (String) -> String? = { null },
    ): String {
        val planned = program.macrocycles.asSequence()
            .flatMap { it.blocks.asSequence() }
            .flatMap { it.mesocycles.asSequence() }
            .flatMap { it.weeks.asSequence() }
            .flatMap { it.sessions.asSequence() }
            .flatMap { it.allExercises().asSequence() }
            .firstOrNull { it.catalogConfigurationId == identity.configurationId && it.name.isNotBlank() }
        val base = planned?.name?.trim()
            ?: displayNameOf(identity.configurationId)?.trim()?.takeIf { it.isNotEmpty() }
            ?: readable(identity.configurationId)
        return when (identity.side) {
            "left" -> "$base (lado izquierdo)"
            "right" -> "$base (lado derecho)"
            else -> base
        }
    }

    /** Texto de la propuesta: el guardado, salvo las de antes de H-UI, que se redactan de nuevo. */
    fun proposalBody(
        proposal: NativeProgressionProposal,
        displayNameOf: (String) -> String? = { null },
    ): String =
        if (proposal.explanation.startsWith(LEGACY_EXPLANATION_PREFIX)) legacyBody(proposal, displayNameOf)
        else proposal.explanation

    /**
     * Mensaje tras aceptar o rechazar una propuesta, según cómo quedó resuelta ([resolution]).
     * [proposal] es la propuesta tal como estaba ANTES de resolverse.
     */
    fun confirmation(
        accepted: Boolean,
        proposal: NativeProgressionProposal?,
        resolution: NativeProgressionResolution?,
        exerciseName: String,
        displayNameOf: (String) -> String? = { null },
    ): String {
        if (resolution == null) return if (accepted) "Propuesta aplicada." else "Propuesta rechazada."
        return when (resolution.status) {
            NativeProgressionResolutionStatus.REJECTED -> "Propuesta rechazada. $exerciseName se queda como está."
            NativeProgressionResolutionStatus.EXPIRED ->
                "No se aplicó la propuesta de $exerciseName. ${resolution.reason}"
            NativeProgressionResolutionStatus.APPLIED ->
                if (proposal == null) "Propuesta aplicada a $exerciseName."
                else appliedMessage(proposal, exerciseName, displayNameOf)
            NativeProgressionResolutionStatus.NOTICE ->
                if (accepted) "Propuesta aplicada." else "Propuesta rechazada."
        }
    }

    private fun appliedMessage(
        proposal: NativeProgressionProposal,
        exerciseName: String,
        displayNameOf: (String) -> String?,
    ): String {
        val identity = proposal.identity
        return when (proposal.kind) {
            NativeProgressionProposalKind.INCREASE_LOAD, NativeProgressionProposalKind.REDUCE_LOAD -> {
                val increasing = proposal.kind == NativeProgressionProposalKind.INCREASE_LOAD
                val assistance = isAssistance(identity)
                val target = proposal.targetLoadKg
                val unit = NativeProgressionText.loadUnit(identity.quantityConvention, stockKindOf(identity))
                when {
                    target == null && assistance ->
                        "Listo: en tu próxima sesión de $exerciseName elige una asistencia un poco ${if (increasing) "menor" else "mayor"}."
                    target == null ->
                        "Listo: en tu próxima sesión de $exerciseName elige una carga un poco ${if (increasing) "mayor" else "menor"}."
                    assistance ->
                        "Listo: $exerciseName tendrá ${if (increasing) "menos" else "más"} asistencia " +
                            "(${NativeProgressionText.formatKg(target)} $unit) en tus próximas sesiones."
                    else ->
                        "Listo: $exerciseName ${if (increasing) "subirá" else "bajará"} a " +
                            "${NativeProgressionText.formatKg(target)} $unit en tus próximas sesiones."
                }
            }
            NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT ->
                "Listo: $exerciseName pasa a ${variantLabel(proposal, "más difícil", displayNameOf)} en tus próximas sesiones."
            NativeProgressionProposalKind.EASIER_BODYWEIGHT_VARIANT ->
                "Listo: $exerciseName pasa a ${variantLabel(proposal, "más fácil", displayNameOf)} en tus próximas sesiones."
        }
    }

    private fun variantLabel(
        proposal: NativeProgressionProposal,
        comparison: String,
        displayNameOf: (String) -> String?,
    ): String {
        val name = proposal.targetConfigurationId?.let(displayNameOf)?.trim()?.takeIf { it.isNotEmpty() }
        return if (name != null) "«$name»" else "una variante $comparison"
    }

    private fun legacyBody(proposal: NativeProgressionProposal, displayNameOf: (String) -> String?): String {
        val identity = proposal.identity
        val times = proposal.sourceLogIds.size.coerceAtLeast(1)
        val assistance = isAssistance(identity)
        val unit = NativeProgressionText.loadUnit(identity.quantityConvention, stockKindOf(identity))
        val targetName = proposal.targetConfigurationId?.let(displayNameOf)
        return when (proposal.kind) {
            NativeProgressionProposalKind.INCREASE_LOAD ->
                NativeProgressionText.loadIncrease(times, null, null, proposal.targetLoadKg, unit, assistance)
            NativeProgressionProposalKind.REDUCE_LOAD ->
                NativeProgressionText.loadReduction(times, null, null, proposal.targetLoadKg, unit, assistance)
            NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT ->
                NativeProgressionText.bodyweightVariant(true, times, null, null, targetName)
            NativeProgressionProposalKind.EASIER_BODYWEIGHT_VARIANT ->
                NativeProgressionText.bodyweightVariant(false, times, null, null, targetName)
        }
    }

    private fun isAssistance(identity: NativeProgressionIdentity): Boolean =
        identity.loadMode == LoadModeV2.ASSISTED || identity.quantityConvention == LoadQuantityConvention.ASSISTANCE

    private fun stockKindOf(identity: NativeProgressionIdentity): NativeLoadConventions.StockKind =
        NativeLoadConventions.stockKindFor(
            CompositionMetadataHolder.current?.metadata(identity.configurationId)?.equipmentId,
            identity.configurationId,
        )

    /** `bench_press__barbell` → «Bench press»: último recurso cuando ni el programa ni el catálogo lo nombran. */
    private fun readable(configurationId: String): String =
        configurationId.substringBefore("__").replace('_', ' ').trim()
            .replaceFirstChar { it.uppercase() }
            .ifEmpty { "Ejercicio" }
}
