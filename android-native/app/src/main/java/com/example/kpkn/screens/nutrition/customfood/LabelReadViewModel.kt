package com.example.kpkn.screens.nutrition.customfood

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.nutrition.label.*
import com.example.kpkn.domain.nutrition.label.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LabelFormState(
    val form: CustomFoodForm, val reading: Boolean = false, val label: LabelParse? = null,
    val markers: Map<CustomFoodField, LabelConfidence> = emptyMap(), val manualFields: Set<CustomFoodField> = emptySet(),
    val canUndo: Boolean = false, val needsBasisConfirmation: Boolean = false, val error: String? = null,
)

/** Lee y prepara un formulario revisable. No recibe repositorios ni tiene una operación de guardado. */
class LabelReadViewModel(initial: CustomFoodForm, private val reader: LabelReaderPort) : ViewModel() {
    private val _state = MutableStateFlow(LabelFormState(initial))
    val state: StateFlow<LabelFormState> = _state.asStateFlow()
    private var readJob: Job? = null
    private var generation = 0L
    private var beforeRead: LabelFormState? = null
    fun read(uri: Uri) {
        cancelReading()
        val before = _state.value
        val token = ++generation
        _state.value = before.copy(reading = true, error = null)
        readJob = viewModelScope.launch {
            try {
                val result = reader.read(uri)
                if (token != generation) return@launch
                when (result) {
                    is LabelReadResult.Ready -> {
                        beforeRead = before
                        val markers = result.label.fields.mapKeys { LabelFormAdapter.fields.getValue(it.key) }.mapValues { it.value.confidence }
                        _state.value = LabelFormState(LabelFormAdapter.apply(before.form, result.label), label = result.label,
                            markers = markers, canUndo = true, needsBasisConfirmation = result.label.basis == LabelBasis.UNKNOWN)
                    }
                    is LabelReadResult.NoTable -> _state.value = before.copy(error = result.message)
                    is LabelReadResult.Failed -> _state.value = before.copy(error = result.message)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == generation) _state.value = before.copy(error = "No se pudo leer la etiqueta. Tu formulario se conserva.") }
        }
    }
    fun cancelReading() {
        generation++; readJob?.cancel(); readJob = null
        _state.update { it.copy(reading = false) }
    }
    fun editForm(form: CustomFoodForm) {
        val old = _state.value
        if (old.reading) return
        if (old.label != null && old.form.basis != form.basis) {
            selectBasis(if (form.basis == CustomFoodBasis.PER_100G) LabelBasis.PER_100G else LabelBasis.PER_SERVING)
            return
        }
        val changed = (LabelFormAdapter.fields.values + CustomFoodField.SERVING).filter { LabelFormAdapter.value(old.form, it) != LabelFormAdapter.value(form, it) }.toSet()
        val portionChanged = CustomFoodField.SERVING in changed && form.basis == CustomFoodBasis.PER_SERVING && old.label != null
        val reviewedLabel = if(portionChanged) old.label?.let { label -> label.copy(basis = LabelBasis.PER_SERVING,
            warnings = (label.warnings + "Cambiaste la porción manualmente: revisa que las cifras correspondan a esa cantidad.").distinct()) } else old.label
        _state.value = old.copy(form = form, label = reviewedLabel, markers = if(portionChanged) emptyMap() else old.markers - changed,
            manualFields = old.manualFields + changed + if(portionChanged) LabelFormAdapter.fields.values else emptyList(), error = null)
    }
    fun selectBasis(target: LabelBasis) {
        val old = _state.value; val label = old.label ?: return
        if (old.reading) return
        if (old.needsBasisConfirmation) {
            val form = old.form.copy(basis = if (target == LabelBasis.PER_100G) CustomFoodBasis.PER_100G else CustomFoodBasis.PER_SERVING,
                servingUnit = if (target == LabelBasis.PER_100ML) "ml" else old.form.servingUnit,
                servingAmount = if (target == LabelBasis.PER_100ML) "100" else old.form.servingAmount)
            _state.value = old.copy(form = form, label = label.copy(basis = target, warnings = label.warnings.filterNot { it.startsWith("No se leyó la base") } + "Base confirmada por ti; revisa que corresponda a la etiqueta."), needsBasisConfirmation = false,
                markers = old.markers - CustomFoodField.SERVING, manualFields = old.manualFields + CustomFoodField.BASIS, error = null)
            return
        }
        if (old.manualFields.isEmpty()) {
            val next = label.forBasis(target)
            if (next.basis != target || next.fields.values.any { !it.value.isFinite() }) {
                _state.value = old.copy(error = "No se puede convertir la base sin una porción compatible. Conservamos tus cifras.")
                return
            }
            _state.value = old.copy(form = LabelFormAdapter.apply(old.form, next), label = next,
                markers = next.fields.mapKeys { LabelFormAdapter.fields.getValue(it.key) }.mapValues { it.value.confidence }, error = null)
        } else {
            val factor = LabelFormAdapter.conversionFactor(old.form, target)
            val converted = factor?.let { LabelFormAdapter.convertCurrent(old.form, target, it) }
            if (converted == null) { _state.value = old.copy(error = "Indica una porción compatible para convertir tus cifras corregidas."); return }
            _state.value = old.copy(form = converted, label = label.copy(basis = target),
                markers = old.markers.mapValues { if (it.value == LabelConfidence.UNSURE) it.value else LabelConfidence.DERIVED }, error = null)
        }
    }
    fun undo() {
        cancelReading()
        beforeRead?.let { _state.value = it.copy(reading = false, canUndo = false); beforeRead = null }
    }
}
