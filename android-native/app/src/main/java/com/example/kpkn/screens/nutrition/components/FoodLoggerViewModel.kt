package com.example.kpkn.screens.nutrition.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.ContextDetector
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.domain.nutrition.SubjectivePortionEngine
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Clave con la que el logger pide su ViewModel al dueño del host (el NavBackStackEntry de Nutrición o de Home). */
internal const val FOOD_LOGGER_VIEW_MODEL_KEY = "food_logger"

/** Tope de caracteres que se copian al SavedStateHandle: el Bundle de estado del proceso es pequeño (TransactionTooLarge). */
private const val MAX_PERSISTED_TEXT = 4_000

/**
 * Guarda el tipo de comida por nombre para `rememberSaveable` en los anfitriones (Nutrición, Home): así la hoja del
 * logger vuelve a abrirse con la misma comida tras la muerte del proceso.
 */
val MealTypeSaver: Saver<MealType, String> = Saver(
    save = { it.name },
    restore = { name -> MealType.entries.firstOrNull { it.name == name } },
)

/**
 * WP-U12 (C11): volumen vigente de cada utensilio configurable, es decir la base salvo lo que el usuario ya guardó.
 * Partir siempre de la base hacía que el diálogo mostrara los valores originales y que Guardar pisara con ellos los
 * tamaños que el usuario ya había guardado.
 */
internal fun currentUtensilValues(): Map<String, Float> {
    val saved = SubjectivePortionEngine.currentUtensilOverrides()
    return SubjectivePortionEngine.UTENSIL_DEFAULTS.mapValues { (key, base) -> (saved[key] ?: base).toFloat() }
}

/** Los siete primitivos que se copian al SavedStateHandle (la semilla que sobrevive a la muerte del proceso). */
internal data class FoodLoggerSeed(
    val description: String,
    val lastAnalyzedDescription: String,
    val mealType: MealType,
    val logDate: String,
    val activeTab: Int,
    val searchQuery: String,
    val draftLogId: String,
)

/**
 * Estado del borrador del logger de comidas (WP-U7 / C5). Cada propiedad es un `mutableStateOf`, así que el drawer lo
 * usa por delegación (`var tags by remember(draft) { draft::tags }`) y sus ~40 funciones locales no cambian. Vive en el
 * [FoodLoggerViewModel]: rotar, plegar el teléfono o recrear la composición conserva el texto, las tarjetas
 * interpretadas y el análisis en curso. Aquí NO va el estado transitorio de la hoja (confirmación de descarte,
 * `sheetRevision`, diálogos, contador de la corrección de composición, cronómetro del análisis).
 */
@Stable
internal class FoodLoggerDraft {
    var description by mutableStateOf("")
    var lastAnalyzedDescription by mutableStateOf("")

    /** Último texto compartido que ya se aplicó: el efecto de `initialDescription` no lo reaplica tras rotar o restaurar. */
    var consumedInitialDescription by mutableStateOf<String?>(null)
    var mealType by mutableStateOf(MealType.LUNCH)
    var logDate by mutableStateOf("")
    var tags by mutableStateOf(emptyList<ResolvedTag>())
    var detectedContext by mutableStateOf<ContextDetector.ContextResult?>(null)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf(emptyList<FoodCandidate>())
    var activeTab by mutableIntStateOf(0)

    /** Id durable de la comida: guardar dos veces el mismo borrador reemplaza la fila, nunca la duplica. */
    var draftLogId by mutableStateOf(UUID.randomUUID().toString())

    /** Comida registrada que se está editando (WP-U11); null mientras se crea una nueva. */
    var editingLogId by mutableStateOf<String?>(null)
    var isAnalyzing by mutableStateOf(false)
    var analysisStage by mutableStateOf<ParseStage?>(null)
    var analysisStartedAtMs by mutableLongStateOf(0L)
    var analysisNotice by mutableStateOf<AnalysisNotice?>(null)
    var analysisKcalRange by mutableStateOf<Pair<Int, Int>?>(null)
    var reviewRequired by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var saveError by mutableStateOf<String?>(null)

    /** Sube con cada rechazo para que el mismo mensaje vuelva a desplazar la lista hasta el aviso. */
    var errorPulse by mutableIntStateOf(0)
    var showSuccess by mutableStateOf(false)
    var learnedMemoryCleared by mutableStateOf(false)
    var utensilValues by mutableStateOf(currentUtensilValues())

    /** Verdadero si el usuario ya escribió, buscó o interpretó algo: un borrador así nunca se vuelve a sembrar. */
    val hasContent: Boolean
        get() = description.isNotBlank() || tags.isNotEmpty() || searchQuery.isNotBlank()

    fun seed(): FoodLoggerSeed = FoodLoggerSeed(
        description = description,
        lastAnalyzedDescription = lastAnalyzedDescription,
        mealType = mealType,
        logDate = logDate,
        activeTab = activeTab,
        searchQuery = searchQuery,
        draftLogId = draftLogId,
    )

    /** Deja el holder como recién creado (con un id nuevo): el siguiente borrador no hereda nada del anterior. */
    fun reset() {
        description = ""
        lastAnalyzedDescription = ""
        consumedInitialDescription = null
        mealType = MealType.LUNCH
        logDate = ""
        tags = emptyList()
        detectedContext = null
        searchQuery = ""
        searchResults = emptyList()
        activeTab = 0
        draftLogId = UUID.randomUUID().toString()
        editingLogId = null
        isAnalyzing = false
        analysisStage = null
        analysisStartedAtMs = 0L
        analysisNotice = null
        analysisKcalRange = null
        reviewRequired = false
        isSaving = false
        saveError = null
        errorPulse = 0
        showSuccess = false
        learnedMemoryCleared = false
        utensilValues = currentUtensilValues()
    }
}

/**
 * Dueño del borrador del logger de comidas (WP-U7 / C5). Se obtiene con `viewModel(key = "food_logger")` desde el
 * drawer, así que su dueño es el NavBackStackEntry del host: Nutrición y Home tienen borradores separados y rotar,
 * plegar o recrear la composición no pierde nada (tarjetas incluidas: `ResolvedTag` arrastra interpretaciones que no
 * caben en un Bundle). Al morir el proceso solo vuelve una semilla primitiva (texto, último texto interpretado,
 * comida, fecha, pestaña, búsqueda y id del borrador) y las tarjetas se recalculan desde ese texto.
 *
 * El análisis, la búsqueda y la corrección de composición corren en [viewModelScope]: no se cancelan al recrear la
 * composición, solo con [clearDraft].
 */
internal class FoodLoggerViewModel(private val handle: SavedStateHandle) : ViewModel() {

    val draft = FoodLoggerDraft()

    /** Verdadero si el [SavedStateHandle] ya traía una semilla al construir el ViewModel (muerte del proceso). */
    val restoredFromProcessDeath: Boolean = handle.contains(KEY_DRAFT_LOG_ID)

    /** Análisis en curso; [clearDraft] lo cancela para que un borrador descartado no escriba en el siguiente. */
    var analysisJob: Job? = null

    /** Búsqueda en curso: una nueva (o la caja vacía) cancela la anterior. */
    var searchJob: Job? = null

    /** Corrección de composición (tortilla) en curso. */
    var compositionJob: Job? = null

    private var seeded = restoredFromProcessDeath
    private var restoreAnalysisPending = restoredFromProcessDeath
    private var persistJob: Job? = null

    init {
        if (restoredFromProcessDeath) restoreDraft()
    }

    /**
     * Siembra el borrador con lo que pide el host solo si todavía no se sembró (ni se restauró) y el usuario no ha
     * escrito nada: recomponer o rotar nunca pisa lo ya escrito ni la comida que el usuario eligió. Devuelve si sembró.
     * El drawer lo llama durante la composición: las lecturas no se observan para que escribir justo después el mismo
     * estado no invalide la composición que lo llama.
     */
    fun seedIfEmpty(date: String, meal: MealType, description: String, tab: Int): Boolean =
        Snapshot.withoutReadObservation {
            if (seeded) return@withoutReadObservation false
            seeded = true
            if (draft.hasContent) {
                if (draft.logDate.isBlank()) draft.logDate = date
                return@withoutReadObservation false
            }
            draft.logDate = date
            draft.mealType = meal
            draft.description = description
            draft.activeTab = tab.coerceIn(0, 1)
            true
        }

    /**
     * Verdadero una sola vez y solo si el borrador viene de una semilla guardada: el drawer vuelve a interpretar el
     * último texto analizado (las tarjetas no sobreviven a la muerte del proceso). Una rotación posterior no repite
     * el análisis ni resucita tarjetas que el usuario quitó.
     */
    fun takeRestoreRequest(): Boolean {
        val pending = restoreAnalysisPending
        restoreAnalysisPending = false
        return pending
    }

    /**
     * Copia los siete primitivos del borrador al [SavedStateHandle] tras cada cambio (un `snapshotFlow` en
     * [viewModelScope]). Idempotente: si ya está activo no hace nada. [clearDraft] lo detiene.
     */
    fun persistSeed() {
        if (persistJob?.isActive == true) return
        persistJob = viewModelScope.launch {
            snapshotFlow { draft.seed() }.collect { writeSeed(it) }
        }
    }

    /** Copia ahora los siete primitivos al [SavedStateHandle] (lo que [persistSeed] hace tras cada cambio). */
    fun flushSeed() = writeSeed(draft.seed())

    /**
     * Cierra el borrador: detiene la persistencia y el trabajo en curso, deja el holder como nuevo y borra la semilla.
     * Toda salida del logger (descartar, cerrar vacío, guardar, cierre del host) pasa por aquí.
     */
    fun clearDraft() {
        // Primero se corta lo que escribe: una persistencia viva volvería a copiar los valores en blanco al handle.
        persistJob?.cancel()
        persistJob = null
        analysisJob?.cancel()
        analysisJob = null
        searchJob?.cancel()
        searchJob = null
        compositionJob?.cancel()
        compositionJob = null
        seeded = false
        restoreAnalysisPending = false
        draft.reset()
        SEED_KEYS.forEach { handle.remove<Any?>(it) }
    }

    private fun writeSeed(seed: FoodLoggerSeed) {
        handle[KEY_DESCRIPTION] = seed.description.take(MAX_PERSISTED_TEXT)
        handle[KEY_LAST_ANALYZED] = seed.lastAnalyzedDescription.take(MAX_PERSISTED_TEXT)
        handle[KEY_MEAL_TYPE] = seed.mealType.name
        handle[KEY_LOG_DATE] = seed.logDate
        handle[KEY_ACTIVE_TAB] = seed.activeTab
        handle[KEY_SEARCH_QUERY] = seed.searchQuery.take(MAX_PERSISTED_TEXT)
        handle[KEY_DRAFT_LOG_ID] = seed.draftLogId
    }

    private fun restoreDraft() {
        draft.description = readString(KEY_DESCRIPTION).orEmpty()
        draft.lastAnalyzedDescription = readString(KEY_LAST_ANALYZED).orEmpty()
        readString(KEY_MEAL_TYPE)
            ?.let { name -> MealType.entries.firstOrNull { it.name == name } }
            ?.let { draft.mealType = it }
        draft.logDate = readString(KEY_LOG_DATE)?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
        draft.activeTab = (readInt(KEY_ACTIVE_TAB) ?: 0).coerceIn(0, 1)
        draft.searchQuery = readString(KEY_SEARCH_QUERY).orEmpty()
        readString(KEY_DRAFT_LOG_ID)?.takeIf { it.isNotBlank() }?.let { draft.draftLogId = it }
    }

    private fun readString(key: String): String? = runCatching { handle.get<String>(key) }.getOrNull()

    private fun readInt(key: String): Int? = runCatching { handle.get<Int>(key) }.getOrNull()

    companion object {
        const val KEY_DESCRIPTION = "food_logger_description"
        const val KEY_LAST_ANALYZED = "food_logger_last_analyzed"
        const val KEY_MEAL_TYPE = "food_logger_meal_type"
        const val KEY_LOG_DATE = "food_logger_log_date"
        const val KEY_ACTIVE_TAB = "food_logger_active_tab"
        const val KEY_SEARCH_QUERY = "food_logger_search_query"
        const val KEY_DRAFT_LOG_ID = "food_logger_draft_log_id"

        val SEED_KEYS: List<String> = listOf(
            KEY_DESCRIPTION,
            KEY_LAST_ANALYZED,
            KEY_MEAL_TYPE,
            KEY_LOG_DATE,
            KEY_ACTIVE_TAB,
            KEY_SEARCH_QUERY,
            KEY_DRAFT_LOG_ID,
        )
    }
}
