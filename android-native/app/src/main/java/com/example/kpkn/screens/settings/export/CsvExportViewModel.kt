package com.example.kpkn.screens.settings.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.export.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class CsvDocumentRequest(val token: String, val name: String, val mimeType: String)
data class CsvExportUiState(val busy: Boolean = false, val request: CsvDocumentRequest? = null, val error: String? = null, val notice: String? = null)

class CsvExportViewModel(private val port: CsvExportPort, private val io: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val _state = MutableStateFlow(CsvExportUiState())
    val state: StateFlow<CsvExportUiState> = _state.asStateFlow()
    private var bundle: CsvExportBundle? = null
    private var writing = false
    private var destinationHandled = false
    fun prepare(domains: Set<CsvExportDomain>) {
        if (_state.value.busy || domains.isEmpty()) return
        destinationHandled = false
        _state.value = CsvExportUiState(busy = true)
        viewModelScope.launch {
            try {
                val prepared = withContext(io) { port.prepare(domains) }
                bundle = prepared
                _state.value = CsvExportUiState(busy = true, request = CsvDocumentRequest(UUID.randomUUID().toString(), prepared.suggestedName,
                    if (prepared.isZip) "application/zip" else "text/csv"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail("No se pudo preparar la exportación: ${error.message ?: "error desconocido"}") }
        }
    }
    fun acknowledge(token: String) {
        if (_state.value.request?.token == token) _state.update { it.copy(request = null) }
    }
    fun destinationChosen(destination: Uri?) {
        if (writing || destinationHandled) return
        if (destination == null) { destinationHandled = true; bundle = null; _state.value = CsvExportUiState(); return }
        val prepared = bundle ?: run { fail("La exportación ya no está preparada. Vuelve a elegir los datos."); return }
        writing = true
        destinationHandled = true
        viewModelScope.launch {
            try {
                withContext(io) { port.write(destination, prepared) }
                val skipped = if (prepared.issues.isEmpty()) "" else " Se omitieron ${prepared.issues.size} registros ilegibles; los originales se conservan."
                _state.value = CsvExportUiState(notice = "Exportación completada (${prepared.files.size} archivos).$skipped")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail("No se pudo exportar: ${error.message ?: "error desconocido"}") }
            finally { writing = false; bundle = null }
        }
    }
    fun consumeNotice() { _state.update { it.copy(notice = null) } }
    private fun fail(message: String) { bundle = null; _state.value = CsvExportUiState(error = message, notice = message) }
}
