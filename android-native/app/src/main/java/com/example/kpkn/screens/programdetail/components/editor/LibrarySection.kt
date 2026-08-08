package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.screens.programdetail.components.LegacyLibrarySection

/** Unified template/protocol library sheet; the protocol compiler stays in the domain layer. */
@Composable
internal fun LibrarySection(
    currentProgram: Program,
    onApplyTemplate: (ProgramTemplateOption) -> Unit,
    onApplyProtocol: (Protocol) -> Unit,
    onDismiss: () -> Unit,
) {
    LegacyLibrarySection(
        currentProgram = currentProgram,
        onApplyTemplate = onApplyTemplate,
        onApplyProtocol = onApplyProtocol,
        onDismiss = onDismiss,
    )
}
