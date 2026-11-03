package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.screens.programdetail.design.structure.ProgramLibraryItem
import com.example.kpkn.screens.programdetail.design.structure.ProgramTemplatesSheet
import com.example.kpkn.screens.programdetail.design.structure.libraryFrequencyLine
import com.example.kpkn.screens.programdetail.design.structure.libraryMethodLine
import com.example.kpkn.screens.programs.isLibraryFamilyEntry
import com.example.kpkn.screens.programs.libraryOrder
import com.example.kpkn.screens.programs.visibleProtocolOf

/**
 * e-library, «Aplicar plantilla o método»: las plantillas y los métodos de progresión de la biblioteca publicada, en su orden. Tocar uno lo
 * aplica en el momento con el interruptor «Sustituir semanas con sesiones» como esté (apagado de salida: no se pisa nada) y cierra la hoja.
 */
@Composable
internal fun LibrarySection(
    onApplyTemplate: (
        template: ProgramTemplateOption,
        replace: Boolean,
        onResult: (success: Boolean, undoSnapshotId: String?) -> Unit,
    ) -> Unit,
    onApplyProtocol: (
        protocol: Protocol,
        replace: Boolean,
        onResult: (success: Boolean, undoSnapshotId: String?) -> Unit,
    ) -> Unit,
    onUndo: (snapshotId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val library = remember { programLibrary() }
    var replace by rememberSaveable { mutableStateOf(false) }
    ProgramTemplatesSheet(
        templates = library.templateItems,
        methods = library.methodItems,
        replace = replace,
        onReplaceChange = { replace = it },
        onPickTemplate = { key, replaceNow, onResult ->
            val template = library.templates[key]
            if (template == null) {
                onResult(false, null)
            } else {
                onApplyTemplate(template, replaceNow) { success, snapshotId ->
                    onResult(success, snapshotId)
                    // Copia: se cierra al confirmar. Sustitución: se queda para mostrar el «Deshacer» real.
                    if (success && !replaceNow) onDismiss()
                }
            }
        },
        onPickMethod = { key, replaceNow, onResult ->
            val protocol = library.protocols[key]
            if (protocol == null) {
                onResult(false, null)
            } else {
                onApplyProtocol(protocol, replaceNow) { success, snapshotId ->
                    onResult(success, snapshotId)
                    if (success && !replaceNow) onDismiss()
                }
            }
        },
        onUndo = onUndo,
        onDismiss = onDismiss,
        applicableIds = library.templates.keys + library.protocols.keys,
    )
}

/** Lo que la hoja necesita: las filas y, por cada una, lo que se aplica al tocarla. */
internal class ProgramLibrary(
    val templateItems: List<ProgramLibraryItem>,
    val methodItems: List<ProgramLibraryItem>,
    val templates: Map<String, ProgramTemplateOption>,
    val protocols: Map<String, Protocol>,
)

/**
 * La biblioteca del editor. Una entrada con plantilla es una plantilla (tiene prioridad); una entrada del catálogo de tipo protocolo, sin
 * plantilla, es un método de progresión. El resto no se puede aplicar desde el editor y no aparece.
 */
internal fun programLibrary(): ProgramLibrary {
    val entries = libraryOrder(
        PersonalizedPlanCatalog.listedEntries()
            .filter { it.publication == PublicationState.PUBLISHED && isLibraryFamilyEntry(it) },
    )
    val templateEntries = entries.mapNotNull { entry -> entry.template?.let { template -> entry to template } }
    val protocolEntries = entries.mapNotNull { entry ->
        if (entry.template != null || entry.source != CatalogSource.PROTOCOL) return@mapNotNull null
        visibleProtocolOf(entry)?.let { protocol -> entry to protocol }
    }
    return ProgramLibrary(
        templateItems = templateEntries.map { (entry, template) ->
            ProgramLibraryItem(key = entry.id, name = template.name, detail = libraryFrequencyLine(entry.supportedFrequencies))
        },
        methodItems = protocolEntries.map { (entry, protocol) ->
            ProgramLibraryItem(key = entry.id, name = protocol.name, detail = libraryMethodLine(protocol.description))
        },
        templates = templateEntries.associate { (entry, template) -> entry.id to template },
        protocols = protocolEntries.associate { (entry, protocol) -> entry.id to protocol },
    )
}
