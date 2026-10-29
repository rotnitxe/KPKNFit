package com.example.kpkn.screens.nutrition.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.collectAsState
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.NutritionCalibrationRepository
import com.example.kpkn.data.models.*
import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.domain.nutrition.HouseholdPortions
import com.example.kpkn.domain.nutrition.FoodLoggerPrimaryAction
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.telemetry.nutrition.NutritionTelemetry
import com.example.kpkn.telemetry.nutrition.NutritionTelemetrySanitizer
import com.example.kpkn.domain.nutrition.SmartFoodResolver
import com.example.kpkn.domain.nutrition.SubjectivePortionEngine
import com.example.kpkn.domain.nutrition.CookingStateResolver
import com.example.kpkn.domain.nutrition.scaleFoodByPortion
import com.example.kpkn.domain.nutrition.createLoggedFood
import com.example.kpkn.domain.nutrition.parseMealDescription
import com.example.kpkn.domain.nutrition.FoodCombinationParser
import com.example.kpkn.domain.nutrition.ContextDetector
import com.example.kpkn.domain.nutrition.FoodState
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.FoodResolutionStatus
import com.example.kpkn.domain.nutrition.NutritionSourceKind
import com.example.kpkn.domain.nutrition.NutrientBasis
import com.example.kpkn.domain.nutrition.COOKING_FACTORS
import com.example.kpkn.domain.nutrition.SemanticPortionRetriever
import com.example.kpkn.domain.nutrition.LastResortSplitter
import com.example.kpkn.domain.nutrition.NutritionHeuristicEstimator
import com.example.kpkn.domain.nutrition.MacroValidator
import com.example.kpkn.domain.nutrition.NutritionInterpretationBridge
import com.example.kpkn.domain.nutrition.TagResolver
import com.example.kpkn.domain.nutrition.NutritionCalibrationWizardEngine
import com.example.kpkn.domain.nutrition.FoodResolutionPort
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.data.food.v2.NutritionParserV2Holder
import com.example.kpkn.domain.nutrition.v2.ResolvedTagAdapterV2
import com.example.kpkn.domain.nutrition.TagOrigin
import com.example.kpkn.domain.nutrition.FoodLearningConfirmation
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.PortionOption
import com.example.kpkn.domain.nutrition.canFinalize
import com.example.kpkn.domain.nutrition.toLoggedFood
import com.example.kpkn.domain.nutrition.confirmedLearning
import com.example.kpkn.domain.nutrition.rescaleEstimatedFood
import com.example.kpkn.domain.nutrition.refreshAfterEdit
import com.example.kpkn.domain.nutrition.applyEdit
import com.example.kpkn.domain.nutrition.mergeReanalyzedTags
import com.example.kpkn.domain.nutrition.absolutePortionOptions
import com.example.kpkn.domain.nutrition.hasMaterialQuestion
import com.example.kpkn.domain.nutrition.applyModifierScale
import com.example.kpkn.domain.nutrition.adjustLoggedFoodForOil
import com.example.kpkn.domain.nutrition.stripOilFromLoggedFood
import com.example.kpkn.domain.nutrition.scalingForIntent
import com.example.kpkn.domain.nutrition.oilGramsForLevel
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.locale.LocaleManager
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.nutrition.design.logger.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.screens.nutrition.customfood.CustomFoodFormOverlay
import com.example.kpkn.screens.nutrition.customfood.FoodFavoriteAction
import com.example.kpkn.screens.nutrition.scan.BarcodeIcon
import com.example.kpkn.screens.nutrition.scan.BarcodeScanFlow
import com.example.kpkn.screens.nutrition.scan.CameraPreviewSurface
import com.example.kpkn.screens.nutrition.scan.rememberCameraBarcodeSource
import com.example.kpkn.screens.nutrition.scan.scannedFoodTag
import com.example.kpkn.screens.nutrition.reuse.QuickEntryOverlay
import com.example.kpkn.screens.nutrition.reuse.TemplatesSheet
import com.example.kpkn.screens.nutrition.recipes.RecipeLibraryOverlay
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.domain.nutrition.MealTime
import com.example.kpkn.domain.nutrition.loggedFoodToEditableTag
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import java.time.LocalDate
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel

// ─── Constants ───────────────────────────────────────────────────────────────

// WP-U7: internal (not private) because the draft holder in FoodLoggerViewModel.kt stores these three types.
internal enum class ParseStage { INTERPRETING, ESTIMATING }

/** Pausa tras la última tecla de «Corrección avanzada» antes de buscar: cada pulsación cancela la búsqueda pendiente. */
private const val CORRECTION_SEARCH_DEBOUNCE_MS = 250L

/** Texto del aviso cuando el guardado falla: el único que ofrece «Reintentar». */
private const val SAVE_FAILED_MESSAGE = "No se pudo guardar la comida. Tu borrador sigue aquí; vuelve a intentarlo."

/** El nombre de una porción tal como se le muestra al usuario: nunca el identificador `SMALL`/`MEDIUM`/`LARGE`. */
internal fun portionPresetLabel(preset: PortionPreset): String = loggerSizeLabel(preset)

/** CRI-ANALYSIS: fragmento corto y seguro (errorType: mensaje) para mostrar en avisos
 *  de fallo. On-device, truncado; ayuda a diagnosticar la causa raíz. Puro y top-level
 *  para poder referenciarlo desde cualquier función local. */
private fun technicalDetailOf(error: Throwable): String {
    val message = error.message?.take(160)?.replace('\n', ' ').orEmpty()
    return if (message.isBlank()) error.javaClass.simpleName else "${error.javaClass.simpleName}: $message"
}

internal enum class AnalysisNoticeTone { INFO, WARNING }

internal data class AnalysisNotice(
    val title: String,
    val message: String,
    val tone: AnalysisNoticeTone,
    /** CRI-ANALYSIS: detalle técnico de la excepción (errorType: mensaje), on-device.
     *  Truncado. Solo para diagnóstico; nunca texto crudo de comidas. */
    val technicalDetail: String? = null,
)

/** Cómo se abre el registro: la página entera (f-*, s-*) o la media hoja de Inicio (f-home-ana, f-home-res). */
enum class FoodLoggerPresentation { FULL, QUICK }

// ─── Composable ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLoggerDrawer(
    nutritionRepo: NutritionRepository,
    onSave: suspend (NutritionLog, List<FoodLearningConfirmation>) -> Unit,
    onDismiss: () -> Unit,
    isOpen: Boolean,
    foodDatabase: List<FoodItem>,
    initialDate: String,
    initialMealType: MealType,
    initialDescription: String? = null,
    initialTab: Int = 0,
    /** WP-U11: a registered meal to edit; the sheet opens with its foods as decided cards and saves over the same id. */
    initialLog: NutritionLog? = null,
    /** QUICK: la media hoja de Inicio, que se puede abrir completa («Abrir completo»). */
    presentation: FoodLoggerPresentation = FoodLoggerPresentation.FULL,
    /** El objetivo de calorías del día: el aro de la tarjeta resumen y «te quedan N para hoy». Sin plan, null. */
    dailyKcalTarget: Int? = null,
    /** Lo guardado, después de guardar (Inicio enseña su aviso con «Deshacer»). */
    onSaved: ((NutritionLog) -> Unit)? = null,
    /** Punto de conexión: la huella de vitaminas y minerales de la comida (f-result), bajo la tarjeta resumen. */
    mealFootprint: (@Composable (List<LoggedFood>) -> Unit)? = null,
    /** Punto de conexión: la huella de un alimento en su hoja de ajuste (f-edit). */
    foodFootprint: (@Composable (LoggedFood) -> Unit)? = null,
    /** Punto de conexión: la huella de una ficha del buscador (s-detail) para los gramos elegidos. */
    catalogFootprint: (@Composable (FoodItem, Double) -> Unit)? = null,
) {
    val programRepo = ProgramRepository.getInstance()
    val settings by programRepo.settings.collectAsState()
    if (!isOpen) return

    // WP-U7 (C5): the draft lives in a ViewModel owned by the host's NavBackStackEntry (Nutrition and Home keep separate
    // drafts), so rotation, folding or a recreated composition keep the text, the interpreted foods and the analysis in
    // flight. Only a primitive seed goes through SavedStateHandle (process death); the foods are interpreted again from it.
    val vm: FoodLoggerViewModel = viewModel(key = FOOD_LOGGER_VIEW_MODEL_KEY) {
        FoodLoggerViewModel(createSavedStateHandle())
    }
    remember(vm) {
        // WP-U11: a registered meal opened to edit seeds the draft with its cards (once, like any seed); otherwise the
        // host's values seed a new meal.
        val seededFromLog = initialLog != null && vm.seedFromLog(initialLog)
        if (!seededFromLog) {
            vm.seedIfEmpty(date = initialDate, meal = initialMealType, description = initialDescription.orEmpty(), tab = initialTab)
        }
    }
    LaunchedEffect(vm) { vm.persistSeed() }
    val draft = vm.draft
    // The work (analysis, search, save) outlives the composition, so it runs in the ViewModel scope; scrolling stays in effects.
    val scope = vm.viewModelScope
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    // WP-S8: avance (0..1) de la importación del catálogo global; null si no hay ninguna en curso.
    val catalogImportProgress by nutritionRepo.catalogImportProgress.collectAsState()

    // Draft state (FoodLoggerDraft): the delegates keep every local function below working unchanged. The property
    // references are remembered: lambdas that capture them are memoized by identity, so a fresh reference on every
    // recomposition would hand every food row new callbacks.
    var description by remember(draft) { draft::description }
    var lastAnalyzedDescription by remember(draft) { draft::lastAnalyzedDescription }
    var mealType by remember(draft) { draft::mealType }
    var logDate by remember(draft) { draft::logDate }
    var logTime by remember(draft) { draft::logTime }
    var tags by remember(draft) { draft::tags }
    var detectedContext by remember(draft) { draft::detectedContext }
    var searchQuery by remember(draft) { draft::searchQuery }
    var searchResults by remember(draft) { draft::searchResults }
    // Último texto con la búsqueda ya resuelta: distingue «sin resultados» de «todavía no se ha buscado».
    var searchedQuery by remember { mutableStateOf<String?>(null) }
    // The search in flight: a newer search (or an emptied box) cancels it instead of letting it finish for nothing.
    var searchJob by remember(vm) { vm::searchJob }
    var activeTab by remember(draft) { draft::activeTab }
    var isSaving by remember(draft) { draft::isSaving }
    var saveError by remember(draft) { draft::saveError }
    // Bumped on every rejected search tap so the same message still scrolls into view (never a silent no-op).
    var errorPulse by remember(draft) { draft::errorPulse }
    // Transient sheet state: it does not need to survive a recreation.
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var sheetRevision by remember { mutableIntStateOf(0) }
    var draftLogId by remember(draft) { draft::draftLogId }
    // WP-U11: editing a registered meal (title, primary action, discard text) comes from the draft, so it survives rotation.
    val isEditing = draft.editingLogId != null
    var showSuccess by remember(draft) { draft::showSuccess }
    var isAnalyzing by remember(draft) { draft::isAnalyzing }
    var compositionRequestToken by remember { mutableIntStateOf(0) }
    var reviewRequired by remember(draft) { draft::reviewRequired }
    var analysisStage by remember(draft) { draft::analysisStage }
    var analysisElapsedMs by remember { mutableStateOf(0L) }
    var analysisStartedAtMs by remember(draft) { draft::analysisStartedAtMs }
    var analysisNotice by remember(draft) { draft::analysisNotice }

    // E16/IT2: invalidación del aprendizaje desde la UI.
    var learnedMemoryCleared by remember(draft) { draft::learnedMemoryCleared }

    // IT3: incertidumbre preservada como rango (referencia del dataset local).
    var analysisKcalRange by remember(draft) { draft::analysisKcalRange }

    // Configuración general de comidas (medidas / aprendizaje)
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDateSheet by remember { mutableStateOf(false) }

    // IT3: utensilios configurables (ml por utensilio).
    var showUtensilDialog by remember { mutableStateOf(false) }
    var showPrivacyInfo by remember { mutableStateOf(false) }
    // N3: escáner de códigos de barras y formulario de alimento propio (alta, o edición si hay un alimento).
    var showScanner by rememberSaveable { mutableStateOf(false) }
    var showCustomForm by remember { mutableStateOf(false) }
    var customFoodToEdit by remember { mutableStateOf<FoodItem?>(null) }
    // N4: la lista de plantillas y la entrada rápida (las dos se abren desde la fila discreta bajo las pestañas).
    var showTemplates by remember { mutableStateOf(false) }
    var showQuickEntry by remember { mutableStateOf(false) }
    var showRecipes by remember { mutableStateOf(false) }
    var recipeToOpen by remember { mutableStateOf<FoodItem?>(null) }
    var editRecipe by remember { mutableStateOf(false) }
    val mealTemplates by nutritionRepo.mealTemplates.collectAsState()
    val favoriteIds by nutritionRepo.favoriteFoodIds.collectAsState()
    val favoriteFoods by produceState(emptyList<FoodItem>(), favoriteIds, foodDatabase) {
        try {
            value = favoriteIds.filterNot { it.startsWith("template:") }.sorted()
                .mapNotNull { nutritionRepo.getFoodById(it) }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { saveError = "No se pudieron cargar los favoritos. Inténtalo de nuevo." }
    }
    // Detalle técnico de un aviso de análisis: oculto hasta pedir «Ver detalle»; cada aviso empieza oculto.
    var showNoticeDetail by remember(analysisNotice) { mutableStateOf(false) }
    // WP-U12 (C11): el diálogo parte de lo vigente (base + lo guardado), no solo de la base.
    var utensilValues by remember(draft) { draft::utensilValues }

    val listState = rememberLazyListState()
    // Registros v4: estado de la pantalla (no del borrador): filtro de búsqueda, hojas abiertas, avisos y respuestas recién elegidas.
    var searchFilter by remember { mutableStateOf(LoggerSearchFilter.ALL) }
    val keepEditing: () -> Unit = {
        showDiscardConfirmation = false
        // The sheet animates offscreen before requesting dismissal. Recreate only
        // its visual state; description, resolved foods and scroll state stay here.
        sheetRevision++
    }
    // WP-U7 (C5): every exit (discard, empty close, saved) drops the draft: work in flight stops and nothing is seeded again.
    val closeLogger: () -> Unit = {
        vm.clearDraft()
        onDismiss()
    }
    val requestDismiss: () -> Unit = {
        // Prevent accidental dismiss when there's content (description typed or foods added)
        if (!isSaving) {
            // An edit that changed nothing closes at once: there is no draft to lose.
            if ((description.isBlank() && tags.isEmpty()) || draft.isUnchangedEdit) closeLogger()
            else showDiscardConfirmation = true
        } else sheetRevision++
    }

    // Auto-scroll to show newly detected foods when analysis finishes. A recreated composition (rotation) starts with the
    // analysis already done and keeps the user's scroll position: only a run seen by this composition scrolls.
    var analysisSeenHere by remember { mutableStateOf(false) }
    LaunchedEffect(isAnalyzing) {
        if (isAnalyzing) {
            analysisSeenHere = true
        } else if (analysisSeenHere && tags.isNotEmpty()) {
            // Registros v4 (f-result): el resultado se lee desde arriba (texto, dudas, resumen y tarjetas); los errores van en el aviso.
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(isAnalyzing, analysisStartedAtMs) {
        if (!isAnalyzing || analysisStartedAtMs == 0L) {
            analysisElapsedMs = 0L
            return@LaunchedEffect
        }
        while (isAnalyzing) {
            analysisElapsedMs = System.currentTimeMillis() - analysisStartedAtMs
            delay(900)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    suspend fun createTagResolver(): TagResolver {
        val calibrationProfile = NutritionCalibrationRepository.getInstance(appContext).get()
        return TagResolver(object : FoodResolutionPort {
            override suspend fun resolveSmart(
                tag: String,
                brandHint: String?,
                contextHint: String?,
                stateHint: FoodState?,
            ) = nutritionRepo.resolveFoodWithSmartResolver(tag, brandHint, contextHint, stateHint)

            override suspend fun getFoodById(id: String) = nutritionRepo.getFoodById(id)

            override suspend fun staticFood(tag: String): FoodItem? =
                HouseholdPortions.householdStaticFood(tag)

            override fun staticIsExact(tag: String): Boolean =
                findFoodExactByNormalized(tag) != null

            override fun recordLearned(
                query: String,
                brandHint: String?,
                foodId: String,
                portionGrams: Double?,
                cookingMethod: String?,
            ) {
                // Resolution is read-only. Confirmed dimensions are committed after save.
            }
        }, calibrationProfile = calibrationProfile)

    }

    /**
     * Resuelve [description] a etiquetas y devuelve quién respondió: "v2" o "v1" (respaldo interno).
     * El intérprete anterior (`parseMealDescription`) solo corre, una vez, cuando hace falta: respaldo de v2 o enriquecer
     * etiquetas conservadas del análisis anterior. [onParsed] recibe los ms que tardó ese parse (la etapa de traza "parse").
     */
    suspend fun resolveTags(
        description: String,
        retrieval: SemanticPortionRetriever.RetrievalResult? = null,
        onParsed: (durationMs: Long) -> Unit = {},
    ): String {
        // WP-N1: resolution, merge and enrichment run off the main thread. The inputs of resolveAll
        // are read before the hop and every Compose state write happens after withContext returns.
        val contextBefore = detectedContext
        val currentMealType = mealType
        var parseDurationMs: Long? = null
        var interpreter = "v2"
        val (resolvedContext, mergedTags) = withContext(Dispatchers.Default) {
            var parsedCache: ParsedMealDescription? = null
            fun parsed(): ParsedMealDescription = parsedCache ?: run {
                val startedAtMs = System.currentTimeMillis()
                parseMealDescription(description, retrieval).also {
                    parseDurationMs = System.currentTimeMillis() - startedAtMs
                    parsedCache = it
                }
            }
            // El intérprete v2 es el sistema; el anterior solo responde si v2 no reconoce ninguna mención o falla (respaldo interno).
            val v2Tags = try {
                NutritionParserV2Holder.parser(appContext)?.let { parserV2 ->
                    // v2 se midió con el texto tal como se escribió (trim, igual que verbatimDescription de v1) y tiene su propio normalizador.
                    ResolvedTagAdapterV2.toResolvedTags(parserV2.parse(description.trim()), parserV2.catalog)
                }?.takeIf { candidate -> candidate.any { it.isResolved } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("FoodLoggerDrawer", "Intérprete v2 falló; responde el anterior", error)
                null
            }
            val result = if (v2Tags != null) v2Tags to contextBefore
            else {
                interpreter = "v1"
                createTagResolver().resolveAll(parsed(), contextBefore, currentMealType)
            }
            val newTags = result.first
            // FIX NUT-04: key drawer state per request — don't keep stale tags from previous description
            // If description changed, old manual edits for hallulla shouldn't block marraqueta analysis.
            // WP-U9 (C7): only what the text produced is superseded. Foods added by search (or loaded to edit)
            // are not part of the text, so they survive every re-analysis.
            val previousTags = tags
            val isSameRequest = lastAnalyzedDescription.isNotBlank() &&
                FoodIdentity.normalize(description) == FoodIdentity.normalize(lastAnalyzedDescription)
            val merged = mergeReanalyzedTags(previousTags, newTags, isSameRequest)
            // resolveAll already enriched every tag it returned (TagResolution.kt), and enriching again
            // is a no-op for them, so only the tags kept from the previous list (manual edits) are
            // enriched here against the new parse, exactly as before. Pinned tags do not belong to this
            // parse (and were enriched when they were created), so they stay as they are.
            val freshIds = newTags.mapTo(HashSet()) { it.id }
            result.second to merged.map {
                if (it.id in freshIds || it.origin.isPinned) it else NutritionInterpretationBridge.enrich(it, parsed())
            }
        }
        detectedContext = resolvedContext
        tags = mergedTags
        analysisKcalRange = tags.mapNotNull { it.interpretationV2 }.takeIf { it.isNotEmpty() }?.let { values ->
            values.sumOf { it.caloriesMin }.toInt() to values.sumOf { it.caloriesMax }.toInt()
        }
        reviewRequired = tags.any { it.hasMaterialQuestion() }
        parseDurationMs?.let(onParsed)
        return interpreter
    }

    /** D7: aviso no-silencioso cuando el dataset de porciones no cargó. */
    fun datasetNotReadyNotice(): AnalysisNotice? {
        val status = SemanticPortionRetriever.status()
        return if (!status.ready) {
            AnalysisNotice(
                title = "Dataset de porciones no cargado",
                message = "Las porciones sugeridas pueden ser menos precisas. Reintenta; si persiste, avísanos.",
                tone = AnalysisNoticeTone.WARNING,
            )
        } else null
    }

    // CRASH-FIX: última red de seguridad. Cualquier fallo no capturado en el
    // pipeline de análisis se registra en NutriTelemetry y degrada a un aviso
    // visible en vez de tumbar el proceso.
    val analysisErrorHandler = CoroutineExceptionHandler { _, handlerError ->
        // Telemetría de fallos: tipo + primer frame de la app. Nunca el mensaje de la excepción (puede citar lo que escribió
        // el usuario); `technicalDetailOf` es solo para el aviso en pantalla, en el teléfono.
        NutritionTelemetry.event(
            "analysis_coroutine_crash",
            mapOf(
                "errorType" to handlerError.javaClass.name,
                "errorSummary" to NutritionTelemetrySanitizer.errorSummary(handlerError),
            ),
        )
        NutritionTelemetry.clearInFlight()
        analysisNotice = AnalysisNotice(
            title = "Error inesperado durante el análisis",
            message = "Puedes reintentarlo o registrar los alimentos manualmente.",
            tone = AnalysisNoticeTone.WARNING,
        )
        isAnalyzing = false
        analysisStage = null
        analysisStartedAtMs = 0L
    }

    /** CRI-ANALYSIS: último nivel del pipeline. No usa parser, dataset, resolver, Room
     *  ni telemetría: solo separa la descripción en fragmentos y crea tags para revisar.
     *  No puede lanzar (garantía del llamador vía runCatching). Devuelve si produjo tags.
     *  Declarado AQUÍ (antes de analyzeDescription) porque las funciones locales no se
     *  pueden referenciar antes de su declaración. */
    fun createLastResortManualTags(raw: String): Boolean {
        val fragments = LastResortSplitter.split(raw)
        val trimmed = raw.trim()
        // WP-U9 (C7): foods added by search (or loaded to edit) are not part of the text: keep them.
        val pinned = tags.filter { it.origin.isPinned }
                // El último nivel muestra una estimación editable, pero exige una
                // confirmación explícita antes de guardarla y de alimentar aprendizaje.
        fun manualTag(fragment: String): ResolvedTag {
            // This last fallback must not silently replace an unread measure with 100 g.
            val unreadMeasure = Regex("(?<!\\p{L})(?:kg|g|gr|gramos?|kilos?|kilogramos?|ml|mililitros?|l|litros?|oz|onzas?|lb|libras?|tazas?|cucharadas?|cucharaditas?|puñados?|rebanadas?|rodajas?|platos?|bowl)\\b", RegexOption.IGNORE_CASE).containsMatchIn(fragment)
            val profile = runCatching {
                NutritionHeuristicEstimator.estimateWithEvidence(fragment)
            }.getOrNull()
            val logged = profile?.profile?.let {
                createLoggedFood(
                    foodName = "$fragment (estimado)",
                    amount = 100.0,
                    calories = it.calories,
                    protein = it.protein,
                    carbs = it.carbs,
                    fats = it.fats,
                )
            }
            return ResolvedTag(
                tag = fragment,
                isExcluded = Regex("^(?:sin|no|ni)\\b", RegexOption.IGNORE_CASE).containsMatchIn(fragment),
                portion = PortionPreset.MEDIUM,
                quantity = 1.0,
                amountGrams = logged?.amount,
                unresolvedDeclaredAmount = unreadMeasure,
                nutritionEstimate = profile?.evidence,
                foodItem = null,
                loggedFood = logged,
                analysisSource = AnalysisSource.LOCAL_HEURISTIC,
                statusText = if (logged != null) {
                    "Estimación local. Toca la tarjeta para editar."
                } else {
                    "No pude identificar el alimento."
                },
                isResolved = logged != null,
                isFuzzyMatch = true,
                resolutionStatus = if (logged != null) FoodResolutionStatus.NEEDS_REVIEW else FoodResolutionStatus.NO_RESOLVED,
                nutritionSource = NutritionSourceKind.HEURISTIC_ESTIMATE,
                resolutionConfidence = 0.35,
                resolutionMargin = 0.0,
                origin = TagOrigin.LAST_RESORT,
            )
        }
        if (fragments.isEmpty()) {
            if (trimmed.isEmpty()) return false
            tags = pinned + NutritionInterpretationBridge.refresh(manualTag(trimmed))
            return true
        }
        tags = pinned + fragments.map { NutritionInterpretationBridge.refresh(manualTag(it)) }
        // La estimación queda explícitamente pendiente de revisión; Guardar se
        // mantiene bloqueado hasta que el usuario confirme o elija una ficha.
        return true
    }

    /** Interprets [text] (the box by default). WP-U7: the restore after process death passes the last analyzed text. */
    fun analyzeDescription(text: String = description) {
        if (text.isBlank() || isAnalyzing) return

        analysisNotice = null
        analysisKcalRange = null
        analysisStage = ParseStage.INTERPRETING
        analysisStartedAtMs = System.currentTimeMillis()
        analysisElapsedMs = 0L
        KpknDiagnosticLogger.event(
            namespace = "nutrition",
            name = "analysis_started",
            fields = mapOf(
                "descriptionLength" to text.length,
                "engine" to "local",
            ),
        )

        val descriptionSnapshot = text
        // NutriTelemetry: una traza por análisis, solo con métricas (sin texto).
        val analysisTrace = NutritionTelemetry.startTrace(
            source = if (!initialDescription.isNullOrBlank()) "shared" else "manual",
            fields = mapOf(
                "descriptionLength" to descriptionSnapshot.length,
                "engine" to "local",
            ),
        )
        NutritionTelemetry.markInFlight(analysisTrace.traceId, "start")

        isAnalyzing = true
        vm.analysisJob = scope.launch(analysisErrorHandler) {
            var traceFinished = false
            fun endTraceOnce(outcome: String, fields: Map<String, Any?> = emptyMap()) {
                if (!traceFinished) {
                    traceFinished = true
                    NutritionTelemetry.clearInFlight()
                    analysisTrace.end(outcome, fields)
                }
            }
            try {
                val contextMealType = mealType
                detectedContext = analysisTrace.stage("context_detect") {
                    withContext(Dispatchers.Default) {
                        ContextDetector.detect(descriptionSnapshot, contextMealType)
                    }
                }
                // Interpret every declared mention before applying compatible individual habits.
                // Whole-meal templates must never replace the user's new description.
                NutritionTelemetry.markInFlight(analysisTrace.traceId, "dataset_prepare")
                analysisTrace.stage("dataset_prepare") {
                    nutritionRepo.prepareSemanticDataset()
                }
                NutritionTelemetry.markInFlight(analysisTrace.traceId, "retrieval")
                val descriptionRetrieval = analysisTrace.stage("retrieval") {
                    withContext(Dispatchers.Default) {
                        SemanticPortionRetriever.retrieve(descriptionSnapshot)
                    }
                }
                analysisKcalRange = descriptionRetrieval.macroRange
                    ?.takeIf { it.kcalMin > 0 && it.kcalMax > it.kcalMin }
                    ?.takeIf { descriptionRetrieval.confidence >= 0.35 }
                    ?.let { kotlin.math.round(it.kcalMin).toInt() to kotlin.math.round(it.kcalMax).toInt() }
                    ?.takeIf { it.second - it.first >= 30 }
                NutritionTelemetry.markInFlight(analysisTrace.traceId, "resolve_tags")
                // El intérprete anterior solo corre dentro de resolveTags si v2 no responde (etapa "parse" solo entonces).
                val interpreter = analysisTrace.stage("resolve_tags") {
                    resolveTags(descriptionSnapshot, descriptionRetrieval) { parseMs ->
                        analysisTrace.stageEnded(name = "parse", durationMs = parseMs, ok = true)
                    }
                }
                // CRI-AUDIT (P2): si el parseo local devolvió 0 alimentos para un texto
                // no-vacío, NUNCA dejar tags vacíos en silencio: caemos al último nivel
                // para que el usuario siempre tenga algo que revisar y guardar.
                // WP-U9: los tags fijados (búsqueda/edición) no salen del texto, así que no lo representan.
                if (tags.all { it.origin.isPinned } && descriptionSnapshot.isNotBlank()) {
                    createLastResortManualTags(descriptionSnapshot)
                }
                lastAnalyzedDescription = descriptionSnapshot
                analysisNotice = datasetNotReadyNotice()
                endTraceOnce(
                    "completed",
                    mapOf(
                        "items" to tags.count { !it.origin.isPinned },
                        "interpreter" to interpreter,
                        "tags" to tags.size,
                        "resolved" to tags.count { it.isResolved },
                        "kcalRangeKnown" to (analysisKcalRange != null),
                    ),
                )
            } catch (cancelled: CancellationException) {
                NutritionTelemetry.clearInFlight()
                analysisTrace.event("analysis_cancelled")
                throw cancelled
            } catch (pipelineError: Throwable) {
                android.util.Log.w("FoodLogger", "Parse failed, usando determinístico", pipelineError)
                analysisKcalRange = null
                val pipelineDetail = technicalDetailOf(pipelineError)
                analysisTrace.event(
                    "analysis_pipeline_failed",
                    mapOf(
                        "errorType" to pipelineError.javaClass.name,
                        "errorSummary" to NutritionTelemetrySanitizer.errorSummary(pipelineError),
                    ),
                )
                // CRASH-FIX: el fallback ejecuta las mismas operaciones que pueden
                // fallar; va protegido en su propio try para que nunca escape.
                NutritionTelemetry.markInFlight(analysisTrace.traceId, "salvage")
                val (salvageDetail, salvaged) = try {
                    analysisTrace.stage("salvage_parse") {
                        resolveTags(descriptionSnapshot)
                    }
                    // CRI-ANALYSIS: el éxito del salvage NO depende de la telemetría
                    // (stage ya no puede lanzar por emisión). LastAnalyzed solo tras éxito.
                    lastAnalyzedDescription = descriptionSnapshot
                    null to true
                } catch (cancelled: CancellationException) {
                    NutritionTelemetry.clearInFlight()
                    throw cancelled
                } catch (salvageError: Throwable) {
                    val salvageFailedDetail = technicalDetailOf(salvageError)
                    analysisTrace.event(
                        "analysis_salvage_failed",
                        mapOf(
                            "errorType" to salvageError.javaClass.name,
                            "errorSummary" to NutritionTelemetrySanitizer.errorSummary(salvageError),
                        ),
                    )
                    // ÚLTIMO NIVEL: independiente de parser/dataset/resolver/Room. Crea tags
                    // manuales no-resueltos desde un split trivial para que NUNCA quede un
                    // callejón sin salida. runCatching: no puede lanzar.
                    val lastResortOk = runCatching {
                        createLastResortManualTags(descriptionSnapshot)
                    }.getOrDefault(false)
                    if (lastResortOk) lastAnalyzedDescription = descriptionSnapshot
                    salvageFailedDetail to lastResortOk
                }
                analysisNotice = when {
                    salvaged -> AnalysisNotice(
                        title = "No se pudo leer la comida completa",
                        message = "Preparamos una versión base del registro para que puedas revisarla y guardar igual.",
                        tone = AnalysisNoticeTone.WARNING,
                        technicalDetail = pipelineDetail,
                    )
                    salvageDetail != null -> AnalysisNotice(
                        title = if (tags.isNotEmpty()) "Preparamos un borrador manual" else "No se pudo analizar esta descripción",
                        message = if (tags.isNotEmpty()) {
                            "El análisis automático falló; revisa cada alimento y ajústalo antes de guardar."
                        } else {
                            "Inténtalo de nuevo o registra los alimentos manualmente."
                        },
                        tone = AnalysisNoticeTone.WARNING,
                        technicalDetail = salvageDetail,
                    )
                    else -> AnalysisNotice(
                        title = "No se pudo analizar esta descripción",
                        message = "Inténtalo de nuevo o registra los alimentos manualmente.",
                        tone = AnalysisNoticeTone.WARNING,
                        technicalDetail = pipelineDetail,
                    )
                }
                endTraceOnce(if (salvaged) "salvaged" else if (tags.isNotEmpty()) "last_resort" else "failed")
            } finally {
                KpknDiagnosticLogger.event(
                    namespace = "nutrition",
                    name = "analysis_finished",
                    fields = mapOf(
                        "tagCount" to tags.size,
                        "engine" to "local",
                    ),
                )
                NutritionTelemetry.clearInFlight()
                isAnalyzing = false
                analysisStage = null
                analysisStartedAtMs = 0L
            }
        }
    }

    // CRI-AUDIT (P0): precalentar dataset + índice de alimentos al abrir el registro,
    // para que el primer toque de ANALIZAR no se topen con la carga (dataset 1.3MB + índice).
    LaunchedEffect(Unit) {
        nutritionRepo.prepareSemanticDataset()
        nutritionRepo.initFoodIndex()
    }

    // Precarga el vocabulario v2 y el índice de «Buscar» al abrir el registro (≈0,4 s la primera vez), no en el primer análisis ni en la primera búsqueda.
    LaunchedEffect(Unit) {
        nutritionRepo.prepareLoggerSearch()
    }

    // WP-U7 (C5): after process death the draft comes back from its seed without cards: interpret the last analyzed text
    // again (what the cards showed), once. A rotation never gets here: the ViewModel kept the cards and the request is one-shot.
    LaunchedEffect(vm) {
        if (vm.takeRestoreRequest() && tags.isEmpty() && lastAnalyzedDescription.isNotBlank()) {
            analyzeDescription(lastAnalyzedDescription)
        }
    }

    LaunchedEffect(initialDescription, initialTab) {
        if (initialDescription.isNullOrBlank()) return@LaunchedEffect
        val normalized = initialDescription.trim()
        if (normalized.isBlank()) return@LaunchedEffect
        // WP-U7 (C5): a shared text is applied once. A recreated composition (rotation, folding) or a restored draft gets
        // the same text again and must not overwrite what the user has typed since.
        if (draft.consumedInitialDescription == normalized) return@LaunchedEffect
        draft.consumedInitialDescription = normalized
        description = normalized
        activeTab = initialTab.coerceIn(0, 1)
        if (tags.isEmpty()) {
            analyzeDescription()
        }
    }

    // FIX NUT-04: deduplicate rapid double taps and fix rank 0 hardcode

    fun refreshTag(tagId: String, dimension: String? = null) {
        // WP-U11: refreshAfterEdit is the body that used to live here for the foods of the text and of the search, and it
        // keeps a reopened (EDIT) food decided: editing its amount never opens a question or confirms a dimension.
        tags = tags.map { tag -> if (tag.id != tagId) tag else refreshAfterEdit(tag, dimension) }
        reviewRequired = tags.any { it.hasMaterialQuestion() }
    }

    fun resolveFood(tagId: String, food: FoodItem, correctedQuery: String? = null) {
        val targetTag = tags.firstOrNull { it.id == tagId } ?: return
        val selectedQuery = correctedQuery ?: targetTag.foodQuery.ifBlank { targetTag.tag }
        if (!FoodIdentity.matchesDeclaredIdentity(selectedQuery, food)) {
            saveError = "Esta ficha no coincide con el alimento indicado. Revisa la búsqueda."
            return
        }
        if (!NutrientBasis.isVerified(food)) {
            saveError = "Esta ficha no tiene nutrientes verificables. Elige otra o conserva la estimación."
            return
        }
        if (!FoodIdentity.matchesExclusions(food, targetTag.excludedIngredients)) {
            saveError = "Esta ficha incluye un ingrediente que excluiste. Elige una preparación compatible."
            return
        }
        // FIX NUT-04: real rank from candidate position (was always 0)
        val realRank = targetTag.reviewCandidates.indexOfFirst { it.id == food.id }
            .let { if (it >= 0) it + 1 else 0 }
        NutritionTelemetry.event("candidate_selected", mapOf("source" to "manual", "rank" to realRank))
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val adj = scalingForIntent(tag.amountIntent, portionAdj)
                val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, tag.cookingMethod)
                val scaleMethod = if (usePrepared) null else tag.cookingMethod
                val applyOil = CookingStateResolver.shouldApplyOil(food, tag.cookingMethod) && !usePrepared
                val logged = scaleFoodByPortion(
                    food = food,
                    quantity = tag.quantity,
                    portion = tag.portion,
                    amountGrams = tag.amountGrams,
                    cookingMethod = scaleMethod,
                    portionAdjustment = adj,
                )
                val adjustedLogged = if (applyOil) {
                    adjustLoggedFoodForOil(logged, tag.cookingMethod, tag.oilLevel, foodName = food.name)
                } else {
                    logged.copy(cookingMethod = tag.cookingMethod ?: logged.cookingMethod)
                }
                tag.copy(
                    foodItem = food,
                    loggedFood = adjustedLogged.copy(analysisSource = AnalysisSource.DATABASE),
                    baseLoggedFood = adjustedLogged,
                    nutrientsManuallyEdited = false,
                    nutritionEstimate = null,
                    foodQuery = selectedQuery,
                    baseAmountGrams = tag.baseAmountGrams ?: tag.amountGrams ?: adjustedLogged.amount,
                    portionMinGrams = tag.portionMinGrams ?: tag.amountGrams ?: adjustedLogged.amount,
                    portionMaxGrams = tag.portionMaxGrams ?: tag.amountGrams ?: adjustedLogged.amount,
                    isResolved = true,
                    isFuzzyMatch = false,
                    analysisSource = AnalysisSource.DATABASE,
                    statusText = "",
                    hasManualEdits = true,
                    oilApplied = applyOil,
                    appliedOilGrams = (adjustedLogged.fats - logged.fats).coerceAtLeast(0.0),
                    learnedWeightBasis = null, learnedOilGramsPer100g = null,
                    needsCookingClarification = false,
                    clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                    canonicalFamily = FoodIdentity.familyFor(food),
                    foodState = CookingStateResolver.stateForMethod(tag.cookingMethod) ?: FoodIdentity.stateFor(food),
                    resolutionStatus = FoodResolutionStatus.AUTO,
                    nutritionSource = NutrientBasis.source(food),
                    resolutionConfidence = 1.0,
                    explicitDecision = true,
                    isUncertain = false,
                )
            } else tag
        }
        refreshTag(tagId, "identity")
    }

    fun updateTagPortion(tagId: String, portion: PortionPreset) {
        NutritionTelemetry.event("manual_correction", mapOf("field" to "portion"))
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        val selectedTag = tags.firstOrNull { it.id == tagId }
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val food = tag.foodItem
                val adj = scalingForIntent(tag.amountIntent, portionAdj)
                if (food != null && !tag.nutrientsManuallyEdited && tag.analysisSource != AnalysisSource.LOCAL_AI_ESTIMATE && tag.analysisSource != AnalysisSource.EXTERNAL_API_ESTIMATE) {
                    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
                    // Never derive the next value from the previous selection.
                    // The immutable anchor makes Grande → Pequeña → Grande exact.
                    val baseGrams = tag.baseAmountGrams
                        ?: tag.amountGrams?.takeIf { it > 0.0 }
                        ?: HouseholdPortions.defaultGrams(food, tag.tag)
                    val newGrams = baseGrams * multiplier
                    val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, tag.cookingMethod)
                    val scaleMethod = if (usePrepared) null else tag.cookingMethod
                    val logged = scaleFoodByPortion(
                        food = food,
                        quantity = tag.quantity,
                        portion = portion,
                        amountGrams = newGrams,
                        cookingMethod = scaleMethod,
                        portionAdjustment = adj,
                    )
                    val adjustedLogged = if (tag.oilApplied) {
                        adjustLoggedFoodForOil(logged, tag.cookingMethod, tag.oilLevel, foodName = food.name)
                    } else logged
                    tag.copy(
                        portion = portion,
                        amountGrams = newGrams,
                        baseAmountGrams = baseGrams,
                        portionMinGrams = newGrams * 0.75,
                        portionMaxGrams = newGrams * 1.25,
                        amountIntent = AmountIntent.RESOLVED_SUBJECTIVE,
                        unresolvedDeclaredAmount = false,
                        ambiguousPackageGrams = null,
                        loggedFood = adjustedLogged,
                        appliedOilGrams = (adjustedLogged.fats - logged.fats).coerceAtLeast(0.0),
                        hasManualEdits = true,
                    )
                } else if (tag.loggedFood != null) {
                    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
                    val baseGrams = tag.baseAmountGrams
                        ?: tag.amountGrams?.takeIf { it > 0 }
                        ?: tag.loggedFood.amount.takeIf { it > 0 }
                        ?: 100.0
                    val newGrams = baseGrams * multiplier
                    val scale = if (baseGrams > 0.0) newGrams / baseGrams else 1.0
                    val baseLogged = tag.baseLoggedFood ?: tag.loggedFood
                    val scaledLogged = rescaleEstimatedFood(baseLogged, newGrams)
                    tag.copy(
                        portion = portion,
                        amountGrams = newGrams,
                        baseAmountGrams = baseGrams,
                        portionMinGrams = newGrams * 0.75,
                        portionMaxGrams = newGrams * 1.25,
                        amountIntent = AmountIntent.RESOLVED_SUBJECTIVE,
                        unresolvedDeclaredAmount = false,
                        ambiguousPackageGrams = null,
                        loggedFood = scaledLogged,
                        hasManualEdits = true,
                    )
                } else {
                    tag.copy(portion = portion, hasManualEdits = true)
                }
            } else tag
        }
        refreshTag(tagId, "portion")
    }

    fun confirmComposition(tagId: String) {
        tags = tags.map { if (it.id == tagId) NutritionInterpretationBridge.confirmEstimatedComposition(it) else it }
        reviewRequired = tags.any { it.hasMaterialQuestion() }
    }

    fun correctTortillaComposition(tagId: String, composition: String) {
        if (isAnalyzing || isSaving) return
        val original = tags.firstOrNull { it.id == tagId } ?: return
        val normalizedComposition = composition.trim().lowercase()
        val query = when (normalizedComposition) {
            "maíz", "maiz" -> "tortilla de maíz"
            "huevo" -> "tortilla de huevo"
            "papas", "patatas" -> "tortilla de papas"
            "trigo" -> "tortilla de trigo"
            else -> return
        }
        val draftId = draftLogId
        val requestToken = compositionRequestToken + 1
        compositionRequestToken = requestToken
        // The answer is a declaration, so a stale wheat preview must immediately
        // stop being eligible for Unsure or saving while the replacement resolves.
        val pending = ResolvedTag(id = original.id, tag = query, foodQuery = query, origin = original.origin,
            isExpanded = original.isExpanded, excludedIngredients = original.excludedIngredients,
            quantity = original.quantity, portion = original.portion, cookingMethod = original.cookingMethod,
            amountIntent = original.amountIntent,
            amountGrams = original.amountGrams.takeIf {
                original.amountIntent == AmountIntent.EXPLICIT_MASS ||
                    (original.amountIntent == AmountIntent.RESOLVED_SUBJECTIVE && original.unitId != null)
            },
            unitId = original.unitId.takeIf {
                original.amountIntent == AmountIntent.EXPLICIT_MASS ||
                    original.amountIntent == AmountIntent.RESOLVED_SUBJECTIVE
            },
            hasManualEdits = true, statusText = "Actualizando composición…")
        tags = tags.map { if (it.id == tagId) pending else it }
        isAnalyzing = true
        analysisStage = ParseStage.INTERPRETING
        analysisStartedAtMs = System.currentTimeMillis()
        saveError = null
        vm.compositionJob = scope.launch {
            try {
                val replacement = createTagResolver().resolveDeclaredComposition(original, query, detectedContext, mealType)
                if (draftLogId == draftId && compositionRequestToken == requestToken && tags.any { it == pending }) {
                    tags = tags.map { if (it == pending) replacement else it }
                    reviewRequired = tags.any { it.hasMaterialQuestion() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (draftLogId == draftId && compositionRequestToken == requestToken && tags.any { it == pending }) {
                    saveError = "No pudimos actualizar $query. Conservamos tu corrección; abre su edición para elegir una ficha."
                    tags = tags.map { if (it == pending) it.copy(isExpanded = true) else it }
                }
            } finally {
                if (compositionRequestToken == requestToken) {
                    isAnalyzing = false
                    analysisStage = null
                    analysisStartedAtMs = 0L
                }
            }
        }
    }

    /** Accept the central estimate without training any learned resolver. */
    fun useEstimate(tagId: String) {
        NutritionTelemetry.event("clarification_answered", mapOf("kind" to "unsure"))
        tags = tags.map { tag ->
            if (tag.id != tagId) tag else NutritionInterpretationBridge.acceptEstimate(tag)
        }
        reviewRequired = tags.any { it.hasMaterialQuestion() }
    }

    /** Accept the central estimate of every card that asks for confirmation, in one step. */
    fun useEstimateForAll() {
        val acceptedCount = tags.count { NutritionInterpretationBridge.canAcceptEstimate(it) }
        NutritionTelemetry.event("clarification_answered", mapOf("kind" to "unsure_all", "count" to acceptedCount))
        tags = NutritionInterpretationBridge.acceptAllEstimates(tags)
        reviewRequired = tags.any { it.hasMaterialQuestion() }
    }

    fun updateTagGrams(tagId: String, grams: Double) {
        if (!grams.isFinite() || grams <= 0.0) return
        NutritionTelemetry.event("manual_correction", mapOf("field" to "grams"))
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val food = tag.foodItem
                val adj = scalingForIntent(AmountIntent.EXPLICIT_MASS, portionAdj)
                var appliedOil = tag.appliedOilGrams
                val logged = if (food != null && !tag.nutrientsManuallyEdited && tag.analysisSource != AnalysisSource.LOCAL_AI_ESTIMATE && tag.analysisSource != AnalysisSource.EXTERNAL_API_ESTIMATE) {
                    val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, tag.cookingMethod)
                    val scaleMethod = if (usePrepared) null else tag.cookingMethod
                    val baseLogged = scaleFoodByPortion(
                        food = food,
                        quantity = tag.quantity,
                        portion = tag.portion,
                        amountGrams = grams,
                        cookingMethod = scaleMethod,
                        portionAdjustment = adj,
                    )
                    if (tag.oilApplied) {
                        adjustLoggedFoodForOil(baseLogged, tag.cookingMethod, tag.oilLevel, foodName = food.name).also {
                            appliedOil = (it.fats - baseLogged.fats).coerceAtLeast(0.0)
                        }
                    } else baseLogged
                } else {
                    (tag.baseLoggedFood ?: tag.loggedFood)?.let { rescaleEstimatedFood(it, grams) }
                }
                tag.copy(
                    amountGrams = grams,
                    portion = PortionPreset.MEDIUM,
                    baseAmountGrams = grams,
                    portionMinGrams = grams,
                    portionMaxGrams = grams,
                    amountIntent = AmountIntent.EXPLICIT_MASS,
                    unresolvedDeclaredAmount = false,
                    ambiguousPackageGrams = null,
                    loggedFood = logged,
                    baseLoggedFood = logged,
                    appliedOilGrams = appliedOil,
                    hasManualEdits = true,
                )
            } else tag
        }
        refreshTag(tagId, "portion")
    }

    fun applyPortionOption(tagId: String, option: PortionOption) {
        updateTagGrams(tagId, option.grams)
        if (option.minGrams != option.maxGrams) {
            tags = tags.map { tag -> if (tag.id != tagId) tag else tag.copy(
                amountIntent = AmountIntent.RESOLVED_SUBJECTIVE,
                portionMinGrams = option.minGrams, portionMaxGrams = option.maxGrams,
            ) }
            refreshTag(tagId, "portion")
        }
    }

    fun updateTagOilLevel(tagId: String, oilLevel: String) {
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        tags = tags.map { tag ->
            if (tag.id != tagId) return@map tag
            if (!tag.oilApplied && tag.cookingMethod != CookingMethod.FRITO &&
                tag.cookingMethod != CookingMethod.EMPANIZADO_FRITO
            ) {
                return@map tag.copy(oilLevel = oilLevel, hasManualEdits = true)
            }
            val food = tag.foodItem
            val adj = scalingForIntent(tag.amountIntent, portionAdj)
            if (food != null && tag.analysisSource != AnalysisSource.LOCAL_AI_ESTIMATE &&
                tag.analysisSource != AnalysisSource.EXTERNAL_API_ESTIMATE
            ) {
                val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, tag.cookingMethod)
                val scaleMethod = if (usePrepared) null else tag.cookingMethod
                val logged = scaleFoodByPortion(
                    food = food,
                    quantity = tag.quantity,
                    portion = tag.portion,
                    amountGrams = tag.amountGrams,
                    cookingMethod = scaleMethod,
                    portionAdjustment = adj,
                )
                val adjustedLogged = adjustLoggedFoodForOil(logged, tag.cookingMethod, oilLevel, foodName = food.name)
                tag.copy(
                    oilLevel = oilLevel,
                    appliedOilGrams = (adjustedLogged.fats - logged.fats).coerceAtLeast(0.0),
                    learnedOilGramsPer100g = null,
                    loggedFood = adjustedLogged,
                    hasManualEdits = true,
                    oilApplied = true,
                    needsOilClarification = false,
                    explicitDecision = true,
                    isResolved = true,
                    resolutionStatus = FoodResolutionStatus.AUTO,
                )
            } else if (tag.loggedFood != null) {
                val stripped = stripOilFromLoggedFood(tag.loggedFood, tag.cookingMethod, tag.oilLevel, foodName = tag.foodItem?.name)
                val adjusted = adjustLoggedFoodForOil(stripped, tag.cookingMethod, oilLevel, foodName = tag.foodItem?.name)
                tag.copy(
                    oilLevel = oilLevel,
                    appliedOilGrams = (adjusted.fats - stripped.fats).coerceAtLeast(0.0),
                    learnedOilGramsPer100g = null,
                    loggedFood = adjusted,
                    hasManualEdits = true,
                    oilApplied = true,
                    needsOilClarification = false,
                    explicitDecision = true,
                    isResolved = true,
                    resolutionStatus = FoodResolutionStatus.AUTO,
                )
            } else {
                tag.copy(oilLevel = oilLevel, hasManualEdits = true)
            }
        }
        refreshTag(tagId, "oil")
    }

    fun updateTagCutClarification(tagId: String, foodId: String) {
        NutritionTelemetry.event("clarification_answered", mapOf("kind" to "staple_cut", "foodId" to foodId))
        val target = tags.firstOrNull { it.id == tagId }
        tags = tags.map { tag ->
            if (tag.id != tagId) tag else NutritionInterpretationBridge.applyCutOption(tag, foodId)
        }
        reviewRequired = tags.any { it.hasMaterialQuestion() }
    }

    fun updateTagCookingClarification(tagId: String, wantCooked: Boolean) {
        val target = tags.firstOrNull { it.id == tagId } ?: return
        val selectedVariant = CookingStateResolver.findDryOrCookedVariant(target.tag, wantCooked) ?: target.foodItem
        if (selectedVariant != null && (!NutrientBasis.isVerified(selectedVariant) || !FoodIdentity.matchesExclusions(selectedVariant, target.excludedIngredients))) {
            saveError = "No hay una ficha compatible para ese estado. Elige otra preparación o conserva la estimación."
            return
        }
        NutritionTelemetry.event("clarification_answered", mapOf("kind" to "cooking_state", "answer" to if (wantCooked) "cooked" else "raw"))
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        tags = tags.map { tag ->
            if (tag.id != tagId) return@map tag
            val method = if (wantCooked) CookingMethod.COCIDO else CookingMethod.CRUDO
            val variant = CookingStateResolver.findDryOrCookedVariant(tag.tag, wantCooked)
                ?: tag.foodItem
            val food = variant ?: tag.foodItem
            if (food == null) return@map tag.copy(
                cookingMethod = method,
                needsCookingClarification = false,
                clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                statusText = "",
                hasManualEdits = true,
            )
            val adj = scalingForIntent(tag.amountIntent, portionAdj)
            val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, method)
            val scaleMethod = if (usePrepared) null else method
            val logged = scaleFoodByPortion(
                food = food,
                quantity = tag.quantity,
                portion = tag.portion,
                amountGrams = tag.amountGrams,
                cookingMethod = scaleMethod,
                portionAdjustment = adj,
            )
            tag.copy(
                cookingMethod = method,
                foodItem = food,
                loggedFood = logged.copy(analysisSource = AnalysisSource.DATABASE, cookingMethod = method),
                baseAmountGrams = tag.baseAmountGrams ?: tag.amountGrams ?: logged.amount,
                portionMinGrams = tag.portionMinGrams ?: tag.amountGrams ?: logged.amount,
                portionMaxGrams = tag.portionMaxGrams ?: tag.amountGrams ?: logged.amount,
                isResolved = true,
                analysisSource = AnalysisSource.DATABASE,
                statusText = "",
                needsCookingClarification = false,
                clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                hasManualEdits = true,
                oilApplied = false, appliedOilGrams = 0.0, learnedWeightBasis = null,
                canonicalFamily = FoodIdentity.familyFor(food),
                foodState = CookingStateResolver.stateForMethod(method) ?: FoodIdentity.stateFor(food),
                resolutionStatus = FoodResolutionStatus.AUTO,
                nutritionSource = NutrientBasis.source(food),
                resolutionConfidence = 1.0,
                explicitDecision = true,
                isUncertain = false,
            )
        }
        refreshTag(tagId, "state")
    }

                    fun updateTagCalories(tagId: String, calories: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                if (old.calories <= 0.0) return@map tag
                val scale = calories / old.calories
                tag.copy(loggedFood = old.copy(
                    calories = calories,
                    protein = kotlin.math.round(old.protein * scale * 10) / 10.0,
                    carbs = kotlin.math.round(old.carbs * scale * 10) / 10.0,
                    fats = kotlin.math.round(old.fats * scale * 10) / 10.0,
                ), hasManualEdits = true)
            } else tag
        }
        refreshTag(tagId)
    }

    fun updateTagProtein(tagId: String, protein: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                val roundedP = kotlin.math.round(protein * 10) / 10.0
                val newCalories = kotlin.math.round(roundedP * 4.0 + old.carbs * 4.0 + old.fats * 9.0)
                tag.copy(loggedFood = old.copy(
                    protein = roundedP,
                    calories = newCalories,
                ), hasManualEdits = true)
            } else tag
        }
        refreshTag(tagId)
    }

    fun updateTagCarbs(tagId: String, carbs: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                val roundedC = kotlin.math.round(carbs * 10) / 10.0
                val newCalories = kotlin.math.round(old.protein * 4.0 + roundedC * 4.0 + old.fats * 9.0)
                tag.copy(loggedFood = old.copy(
                    carbs = roundedC,
                    calories = newCalories,
                ), hasManualEdits = true)
            } else tag
        }
        refreshTag(tagId)
    }

    fun updateTagFats(tagId: String, fats: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                val roundedF = kotlin.math.round(fats * 10) / 10.0
                val newCalories = kotlin.math.round(old.protein * 4.0 + old.carbs * 4.0 + roundedF * 9.0)
                tag.copy(loggedFood = old.copy(
                    fats = roundedF,
                    calories = newCalories,
                ), hasManualEdits = true)
            } else tag
        }
        refreshTag(tagId)
    }

    fun removeTag(tagId: String) { tags = tags.filter { it.id != tagId } }
    fun toggleTagExpanded(tagId: String) {
        tags = tags.map { if (it.id == tagId) it.copy(isExpanded = !it.isExpanded) else it }
    }

    // N3: un producto confirmado en el escáner entra como cualquier fila del buscador (misma ficha, mismo guardado).
    fun toggleFavorite(id: String) {
        scope.launch {
            try { nutritionRepo.toggleFavoriteFood(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { saveError = "No se pudo guardar el favorito. Inténtalo de nuevo." }
        }
    }

    fun addScannedFood(food: FoodItem, grams: Double) {
        tags = tags + scannedFoodTag(food, grams)
        saveError = null
    }

    // N4: una plantilla entra como BORRADOR editable: una tarjeta por alimento (id nuevo, su instantánea intacta, sin volver al catálogo).
    // Las cantidades se editan alimento a alimento y nada se guarda hasta confirmar.
    fun applyTemplate(template: MealTemplate) {
        tags = tags + MealReuse.templateDraftFoods(template).map(::loggedFoodToEditableTag)
        reviewRequired = tags.any { it.hasMaterialQuestion() }
        saveError = null
    }

    // N4: la entrada rápida entra como una tarjeta más de la comida (un alimento marcado como rápido); se guarda con la comida.
    fun addQuickEntry(food: LoggedFood, meal: MealType) {
        tags = tags + loggedFoodToEditableTag(food)
        mealType = meal
        saveError = null
    }

    fun performSearch() {
        searchJob?.cancel()
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            return
        }
        val query = searchQuery
        searchJob = scope.launch {
            // Vocabulario v2, lo propio y lo aprendido, el buscador anterior y los productos del paquete: todas las filas pasan
            // el filtro del registro (nutrientes verificables e identidad declarada) antes del límite.
            // S-D5: los filtros filtran la lista del buscador; con uno activo se piden más filas para que queden suficientes.
            val results = nutritionRepo.searchLoggerCandidates(query, limit = if (searchFilter == LoggerSearchFilter.ALL) 20 else 60)
                .distinctBy { it.foodId.ifBlank { "${it.food.name}_${it.food.brand.orEmpty()}" } } // C13: una ficha por id (keys únicas)
            if (searchQuery == query) {
                searchResults = results
                searchedQuery = query
            }
        }
    }

    // WP-S8: una búsqueda hecha con el catálogo a medias se repite cuando termina de prepararse (solo si el aviso se vio).
    var importWasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(catalogImportProgress != null) {
        if (catalogImportProgress != null) {
            importWasRunning = true
        } else if (importWasRunning) {
            importWasRunning = false
            if (searchQuery.isNotBlank()) performSearch()
        }
    }

    // f-saved: lo guardado y lo que queda del día («548 kcal · te quedan 1.752 para hoy»).
    val allLogs by nutritionRepo.nutritionLogs.collectAsState()
    var savedLogSnapshot by remember { mutableStateOf<NutritionLog?>(null) }
    var savedKcal by remember { mutableIntStateOf(0) }
    var savedRemaining by remember { mutableStateOf<Int?>(null) }

    fun saveLog() {
        if (isSaving || showSuccess) return
        // Registros v4 (f-result): se guarda con la respuesta por defecto de cada duda (la estimación central, «≈» en la tarjeta).
        val defaulted = tags.count { !it.isExcluded && NutritionInterpretationBridge.canAcceptEstimate(it) }
        if (defaulted > 0) {
            NutritionTelemetry.event("clarification_answered", mapOf("kind" to "default_on_save", "count" to defaulted))
            tags = NutritionInterpretationBridge.acceptAllEstimates(tags)
        }
        val activeTags = tags.filterNot { it.isExcluded }
        if (activeTags.isEmpty() || activeTags.any { it.loggedFood == null || it.interpretationV2?.canFinalize() != true }) {
            reviewRequired = true
            saveError = "Ajusta la cantidad de lo que no pude leer antes de guardar."
            return
        }
        val foods = activeTags.map { tag -> tag.interpretationV2!!.toLoggedFood(tag.loggedFood!!) }
        // WP-U11: a registered meal being edited keeps its id, notes, status and the foods the user did not touch, and its
        // save never teaches a habit: editing is not a confirmation.
        val original = draft.editBaseline?.original
        val log = if (original != null) {
            val edited = applyEdit(original, foods, mealType, logDate)
            if (original.date.take(10) == logDate && MealTime.label(original.date) == logTime) edited
            else edited.copy(date = MealTime.stamp(LocalDate.parse(logDate), logTime, java.time.ZoneId.systemDefault()))
        } else {
            NutritionLog(id = draftLogId, date = MealTime.stamp(LocalDate.parse(logDate), logTime, java.time.ZoneId.systemDefault()),
                mealType = mealType, foods = foods, status = NutritionStatus.CONSUMED)
        }
        val confirmations = if (original != null) emptyList() else activeTags.mapNotNull { it.confirmedLearning() }
        isSaving = true
        saveError = null
        scope.launch {
            try {
                onSave(log, confirmations)
                val mealKcal = kotlin.math.round(foods.sumOf { it.calories }).toInt()
                val before = allLogs.filter {
                    it.date.take(10) == logDate && it.status == NutritionStatus.CONSUMED && it.id != log.id
                }.sumOf { day -> day.foods.sumOf { it.calories } }
                savedLogSnapshot = log
                savedKcal = mealKcal
                savedRemaining = dailyKcalTarget?.let { target -> target - kotlin.math.round(before).toInt() - mealKcal }
                NutritionTelemetry.event("save_log", mapOf("foodCount" to foods.size, "tagCount" to activeTags.size, "edited" to (original != null)))
                showSuccess = true
                reviewRequired = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                saveError = SAVE_FAILED_MESSAGE
                NutritionTelemetry.event(
                    "save_failed",
                    mapOf("errorType" to error.javaClass.simpleName, "errorSummary" to NutritionTelemetrySanitizer.errorSummary(error)),
                )
            } finally { isSaving = false }
        }
    }

    val tagTotals = remember(tags) {
        val activeTags = tags.filterNot { it.isExcluded }
        DailyMacroTotals(
            calories = activeTags.sumOf { it.loggedFood?.calories ?: 0.0 },
            protein = activeTags.sumOf { it.loggedFood?.protein ?: 0.0 },
            carbs = activeTags.sumOf { it.loggedFood?.carbs ?: 0.0 },
            fats = activeTags.sumOf { it.loggedFood?.fats ?: 0.0 },
        )
    }

    // ─── Registros v4: lo que la pantalla deriva del borrador ─────────────────────
    val locale = java.util.Locale.forLanguageTag("es")
    val mealLabel = MealSlots.mealLabel(mealType)
    val activeTagsNow = tags.filterNot { it.isExcluded }
    val hasTagsNow = tags.isNotEmpty()
    val pendingCount = activeTagsNow.count { it.interpretationV2?.pendingQuestions?.isNotEmpty() == true }
    // Lo que no se puede guardar ni con la estimación: la cantidad que no se pudo leer o un alimento sin datos.
    val blockingCount = activeTagsNow.count { tag ->
        (tag.loggedFood == null || tag.interpretationV2?.canFinalize() != true) && !NutritionInterpretationBridge.canAcceptEstimate(tag)
    }
    val descriptionEditedNow = FoodLoggerPrimaryAction.isDescriptionEdited(
        current = description,
        lastAnalyzed = lastAnalyzedDescription,
        hasTags = hasTagsNow,
        describeTab = activeTab == 0,
    )
    val analyzeLabel = FoodLoggerPrimaryAction.label(hasTags = false, descriptionEdited = descriptionEditedNow, isSearchMode = false)
    val kcalNow = kotlin.math.round(tagTotals.calories).toInt()
    val readingIndex = rememberLoggerReadingIndex(description, isAnalyzing)
    val foodMentions = remember(tags) { tags.filterNot { it.origin.isPinned }.map { it.tag } }
    val dictation = rememberLoggerDictation(current = { description }, onText = { description = it })

    // El aviso de abajo (lienzo `.toast`): lo añadido o quitado con «Deshacer» durante 4 s, y los errores.
    var toast by remember { mutableStateOf<LoggerToastData?>(null) }
    fun showToast(message: String, actionLabel: String? = null, error: Boolean = false, onAction: (() -> Unit)? = null) {
        toast = LoggerToastData(System.nanoTime(), message, actionLabel, onAction, error)
    }
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(4_000)
            toast = null
        }
    }
    LaunchedEffect(saveError, errorPulse) {
        val message = saveError ?: return@LaunchedEffect
        showToast(message, if (message == SAVE_FAILED_MESSAGE) "Reintentar" else null, error = true) {
            if (message == SAVE_FAILED_MESSAGE) saveLog()
        }
        saveError = null
    }
    LaunchedEffect(dictation.error) {
        dictation.error?.let { showToast(it, error = true) }
        dictation.clearError()
    }

    // Respuesta recién elegida de una duda: queda marcada un momento antes de plegarse (f-clar).
    val answered = remember { mutableStateMapOf<String, LoggerQuestion>() }
    val cookingChoice = remember { mutableStateMapOf<String, LoggerCooking>() }
    var editingText by remember { mutableStateOf(false) }
    var sheetTagId by remember { mutableStateOf<String?>(null) }
    var identityTagId by remember { mutableStateOf<String?>(null) }
    var identityQuery by remember { mutableStateOf("") }
    var identityResults by remember { mutableStateOf<List<FoodCandidate>?>(null) }
    var detailFood by remember { mutableStateOf<FoodItem?>(null) }
    var showForget by remember { mutableStateOf(false) }
    var showAllFavorites by remember { mutableStateOf(false) }
    var createPrefill by remember { mutableStateOf<String?>(null) }
    var quickExpanded by rememberSaveable { mutableStateOf(false) }
    var quickAppend by remember { mutableStateOf(false) }
    val quick = presentation == FoodLoggerPresentation.QUICK && !quickExpanded
    LaunchedEffect(isAnalyzing) { if (!isAnalyzing) editingText = false }

    // s-loading: la búsqueda va sola al escribir, con 250 ms de espera.
    LaunchedEffect(searchQuery, searchFilter, activeTab) {
        if (activeTab != 1) return@LaunchedEffect
        if (searchQuery.isBlank()) {
            searchJob?.cancel()
            searchResults = emptyList()
            searchedQuery = null
            return@LaunchedEffect
        }
        delay(250)
        performSearch()
    }
    LaunchedEffect(identityQuery, identityTagId) {
        val target = tags.firstOrNull { it.id == identityTagId }
        if (target == null || identityQuery.isBlank()) {
            identityResults = null
            return@LaunchedEffect
        }
        delay(CORRECTION_SEARCH_DEBOUNCE_MS)
        identityResults = withContext(Dispatchers.Default) {
            nutritionRepo.searchLoggerCandidates(identityQuery, limit = 15)
                .filter { FoodIdentity.matchesExclusions(it.food, target.excludedIngredients) }
                .take(5)
        }
    }

    /** Un alimento del buscador, de la ficha o de un atajo entra como una tarjeta más; la ración es la mostrada salvo [gramsOverride]. */
    fun addSearchFood(selectedFood: FoodItem, gramsOverride: Double? = null) {
        val queryUsed = searchQuery.ifBlank { selectedFood.name }
        if (selectedFood.recipe != null) {
            if (!FoodIdentity.matchesDeclaredIdentity(queryUsed, selectedFood)) {
                saveError = "Esa ficha no coincide con lo que buscaste. Elige otra."
                errorPulse++
                return
            }
            recipeToOpen = selectedFood; editRecipe = false; showRecipes = true
            return
        }
        if (!NutrientBasis.isVerified(selectedFood)) {
            saveError = "Esta ficha no tiene nutrientes verificables. Elige otro alimento."
            errorPulse++
            return
        }
        if (!FoodIdentity.matchesDeclaredIdentity(queryUsed, selectedFood)) {
            saveError = "Esa ficha no coincide con lo que buscaste. Elige otra."
            errorPulse++
            return
        }
        // The tapped row IS the eaten identity; a pack or per-100 g row only changes the portion.
        val identity = HouseholdPortions.identityForSearchPick(selectedFood, queryUsed)
        val grams = gramsOverride ?: HouseholdPortions.eatenGramsForSearchPick(identity, queryUsed)
        val logged = scaleFoodByPortion(identity, amountGrams = grams)
        val status = HouseholdPortions.operationalAutoStatus(
            food = identity,
            grams = grams,
            brandHint = null,
            explicitKilogram = HouseholdPortions.isExplicitKilogram(queryUsed),
            amountIntent = AmountIntent.UNSPECIFIED,
            explicitPick = true,
        )
        val tag = NutritionInterpretationBridge.refresh(
            ResolvedTag(
                tag = identity.name,
                portion = PortionPreset.MEDIUM,
                quantity = 1.0,
                amountGrams = grams,
                foodItem = identity,
                loggedFood = logged,
                isResolved = status == FoodResolutionStatus.AUTO,
                statusText = "",
                hasManualEdits = true,
                resolutionStatus = status,
                nutritionSource = NutrientBasis.source(identity),
                resolutionConfidence = 1.0,
                origin = TagOrigin.SEARCH,
            ).copy(foodQuery = queryUsed, confirmedDimensions = setOf("identity")),
        )
        tags = tags + tag
        saveError = null
        showToast("${identity.name.trim().replaceFirstChar { it.uppercase() }} añadido", "Deshacer") {
            tags = tags.filterNot { it.id == tag.id }
        }
    }

    /** Lo ya registrado otra vez (Repetir, Recientes): una tarjeta por alimento con id nuevo, con «Deshacer». */
    fun addLoggedFoods(foods: List<LoggedFood>, title: String) {
        val added = foods.map { food -> loggedFoodToEditableTag(food.copy(id = UUID.randomUUID().toString())) }
        if (added.isEmpty()) return
        tags = tags + added
        reviewRequired = tags.any { it.hasMaterialQuestion() }
        saveError = null
        val ids = added.map { it.id }.toSet()
        showToast("$title añadido", "Deshacer") { tags = tags.filterNot { it.id in ids } }
    }

    fun removeTagWithUndo(tagId: String) {
        val index = tags.indexOfFirst { it.id == tagId }
        if (index < 0) return
        val removed = tags[index]
        removeTag(tagId)
        showToast("${tagDisplayName(removed)} quitado", "Deshacer") {
            tags = tags.toMutableList().apply { add(index.coerceAtMost(size), removed) }
        }
    }

    fun identityChoice(tag: ResolvedTag, id: String, requestId: String?) {
        when {
            id == NutritionInterpretationBridge.VEGETABLE_COMPOSITION_ID -> confirmComposition(tag.id)
            requestId == "cut" -> updateTagCutClarification(tag.id, id)
            else -> ((tag.variantChoices.map { it.food } + tag.reviewCandidates + foodDatabase).firstOrNull { it.id == id }
                ?: identityResults?.firstOrNull { it.food.id == id }?.food
                ?: findStaticFoodById(id))
                ?.let { resolveFood(tag.id, it, identityQuery.takeIf { q -> q.isNotBlank() }) }
        }
    }

    /** La respuesta [key] de la duda de [tag] (claves de [LoggerChoiceKeys]). */
    fun answerQuestion(tag: ResolvedTag, question: ClarificationRequest, shown: LoggerQuestion, key: String) {
        when {
            key == LoggerChoiceKeys.MORE -> { identityQuery = ""; identityTagId = tag.id; return }
            key == LoggerChoiceKeys.ADJUST -> { sheetTagId = tag.id; return }
            key.startsWith(LoggerChoiceKeys.IDENTITY) -> identityChoice(tag, key.removePrefix(LoggerChoiceKeys.IDENTITY), question.requestId)
            key.startsWith(LoggerChoiceKeys.COMPOSITION) -> correctTortillaComposition(tag.id, key.removePrefix(LoggerChoiceKeys.COMPOSITION))
            key == LoggerChoiceKeys.RAW -> updateTagCookingClarification(tag.id, false)
            key == LoggerChoiceKeys.COOKED -> updateTagCookingClarification(tag.id, true)
            key.startsWith(LoggerChoiceKeys.OIL) -> updateTagOilLevel(tag.id, key.removePrefix(LoggerChoiceKeys.OIL))
            key.startsWith(LoggerChoiceKeys.PORTION) -> (question as? ClarificationRequest.Portion)?.options
                ?.getOrNull(key.removePrefix(LoggerChoiceKeys.PORTION).toIntOrNull() ?: -1)
                ?.let { applyPortionOption(tag.id, it) }
        }
        answered[tag.id] = shown.copy(selectedKey = key)
    }

    // ─── Hojas, diálogos y avisos (s-settings, s-forget, s-measures, s-date, s-discard) ─────────────
    if (showSettingsDialog) {
        LoggerSettingsSheet(
            onMeasures = {
                utensilValues = currentUtensilValues()
                showUtensilDialog = true
            },
            onFavoritesAndRecipes = { recipeToOpen = null; editRecipe = false; showRecipes = true },
            onForget = { showForget = true },
            onDismiss = { showSettingsDialog = false },
        )
    }
    if (showForget) {
        LoggerForgetDialog(
            onCancel = { showForget = false },
            onForget = {
                showForget = false
                // S-F9: olvida lo aprendido y no toca la comida que se está registrando.
                scope.launch {
                    try {
                        nutritionRepo.clearLearnedResolutions()
                        learnedMemoryCleared = true
                        showToast("Olvidé lo aprendido")
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { saveError = "No se pudo olvidar lo aprendido. Inténtalo otra vez." }
                }
            },
        )
    }
    if (showUtensilDialog) {
        LoggerMeasuresSheet(
            values = utensilValues,
            onValueChange = { key, ml -> utensilValues = utensilValues + (key to ml) },
            onRestore = { utensilValues = SubjectivePortionEngine.UTENSIL_DEFAULTS.mapValues { (_, base) -> base.toFloat() } },
            onSave = {
                // WP-U12 (C11): solo lo que difiere de la base se guarda; lo que volvió a la base deja de ser un override.
                utensilValues.forEach { (key, ml) ->
                    val volume = ml.toDouble()
                    if (volume == SubjectivePortionEngine.UTENSIL_DEFAULTS[key]) {
                        nutritionRepo.clearUtensilOverride(key)
                    } else {
                        nutritionRepo.saveUtensilOverride(key, volume)
                    }
                }
                showUtensilDialog = false
            },
            onDismiss = { showUtensilDialog = false },
        )
    }
    if (showDiscardConfirmation) {
        LoggerDiscardDialog(
            foodCount = tags.size,
            isEditing = isEditing,
            onDiscard = {
                showDiscardConfirmation = false
                closeLogger()
            },
            onKeepEditing = keepEditing,
        )
    }
    if (showDateSheet) {
        LoggerDateSheet(
            selected = runCatching { LocalDate.parse(logDate) }.getOrDefault(LocalDate.now()),
            selectedTime = logTime,
            today = LocalDate.now(),
            now = java.time.LocalTime.now(),
            onDone = { day, time ->
                logDate = day.toString()
                logTime = time
                showDateSheet = false
            },
            onDismiss = { showDateSheet = false },
        )
    }

    // ─── Piezas de la página ────────────────────────────────────────────────────

    @Composable
    fun FoodCardFor(tag: ResolvedTag, index: Int, compact: Boolean) {
        val logged = tag.loggedFood
        val name = tagDisplayName(tag)
        val units = remember(tag.foodItem?.id, name) { loggerUnits(name, tag.foodItem, tag.foodQuery.ifBlank { tag.tag }) }
        val grams = tag.amountGrams ?: logged?.amount ?: 0.0
        val amount = when {
            tag.isExcluded -> "No cuenta en el total"
            logged == null -> "Sin datos: tócalo para elegir"
            logged.recipeId != null -> loggerRecipeUnits(logged.unit).first().let { unit ->
                val noun = if (unit.kind == LoggerUnitKind.GRAMS) logged.unit else loggerUnitNoun(logged.amount, unit)
                "${loggerFormatValue(logged.amount, unit)} $noun"
            }
            else -> loggerAmountLine(grams, units, volumeUnit = logged.unit == "ml")
        }
        val approx = logged != null && (tag.isUncertain || logged.isUncertain || tag.interpretationV2?.isUncertain == true ||
            tag.nutritionSource == NutritionSourceKind.HEURISTIC_ESTIMATE)
        val question = if (tag.isExcluded) null else tag.interpretationV2?.pendingQuestions?.firstOrNull()
        val shown = question?.let { loggerQuestionFor(tag, it) }
        val justAnswered = answered[tag.id]
        if (justAnswered != null && shown == null) {
            LaunchedEffect(justAnswered) {
                delay(1_200)
                answered.remove(tag.id)
            }
        }
        LoggerFoodCard(
            name = name,
            subtitle = if (tag.isExcluded) amount else loggerFoodSubtitle(amount, approx),
            kcal = logged?.let { loggerFoodKcal(kotlin.math.round(it.calories).toInt(), approx) } ?: "—",
            protein = logged?.protein ?: 0.0,
            carbs = logged?.carbs ?: 0.0,
            fats = logged?.fats ?: 0.0,
            icon = loggerFoodIcon(name, tag.canonicalFamily),
            onOpen = { if (logged == null) { identityQuery = ""; identityTagId = tag.id } else sheetTagId = tag.id },
            question = shown,
            answered = if (shown == null) justAnswered else null,
            onChoice = { key -> if (question != null && shown != null) answerQuestion(tag, question, shown, key) },
            onSkip = { useEstimate(tag.id) },
            index = index,
            compact = compact,
            modifier = Modifier.alpha(if (tag.isExcluded) 0.5f else 1f),
        )
    }

    val gutter = Modifier.padding(horizontal = 16.dp)
    val showSeg = activeTab == 1 || (!hasTagsNow && !isAnalyzing && analysisNotice == null)
    val writeMode = when {
        isAnalyzing -> LoggerWriteMode.READING
        editingText || description.isBlank() -> LoggerWriteMode.EDIT
        hasTagsNow || analysisNotice != null -> LoggerWriteMode.RESULT
        else -> LoggerWriteMode.EDIT
    }
    val showSaveBar = hasTagsNow && !isAnalyzing
    val repeatCards = remember(allLogs, mealType) { loggerRepeatCards(allLogs, mealType, LocalDate.now()) }
    val recentFoods = remember(allLogs) { loggerRecentFoods(allLogs, LocalDate.now()) }

    @Composable
    fun WriteBox(modifier: Modifier = Modifier) {
        LoggerDescribeField(
            text = description,
            onTextChange = { description = it },
            onAnalyze = { editingText = false; analyzeDescription() },
            mode = writeMode,
            analyzeLabel = analyzeLabel,
            showAnalyze = description.isNotBlank() && (!hasTagsNow || descriptionEditedNow),
            onDictate = { dictation.toggle() },
            listening = dictation.listening,
            foodMentions = if (analysisNotice != null) emptyList() else foodMentions,
            onEditRequest = { editingText = true },
            fontSize = if (writeMode == LoggerWriteMode.RESULT && analysisNotice != null) 16f else null,
            readingIndex = readingIndex,
            modifier = modifier,
        )
    }

    // ─── La página completa (f-*, s-*) ───────────────────────────────────────────
    @Composable
    fun FullPage() {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = if (showSaveBar) 150.dp else 40.dp),
            ) {
                item(key = "head") {
                    LoggerHeader(
                        title = if (isEditing) "Editar comida" else "Registrar comida",
                        dateLabel = loggerDateLabel(logDate, locale, time = logTime),
                        mealType = if (activeTab == 0 || searchQuery.isBlank()) mealType else null,
                        onMealSelect = { mealType = it },
                        onSettings = { showSettingsDialog = true },
                        onClose = requestDismiss,
                        onDateClick = { showDateSheet = true },
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
                if (showSeg) {
                    item(key = "seg") {
                        LoggerTabs(activeTab = activeTab, onTabSelect = { activeTab = it }, modifier = gutter)
                    }
                }

                if (activeTab == 0) {
                    item(key = "write") { WriteBox(gutter) }
                    if (isAnalyzing) {
                        item(key = "ana") {
                            LoggerAnalysisPanel(
                                text = description,
                                readingIndex = readingIndex,
                                icons = loggerMentionRanges(description).take(4).map { range ->
                                    loggerFoodIcon(description.substring(range.first, range.last + 1))
                                },
                                modifier = gutter.padding(top = 12.dp),
                            )
                        }
                    } else {
                        analysisNotice?.let {
                            item(key = "notice") {
                                val text = loggerNoticeText(
                                    if (tags.any { !it.origin.isPinned }) LoggerNoticeKind.PARTIAL else LoggerNoticeKind.NOTHING,
                                    tags.filterNot { it.origin.isPinned }.map(::tagDisplayName),
                                )
                                LoggerAnalysisNotice(
                                    title = text.title,
                                    message = text.message,
                                    onRetry = { analyzeDescription() },
                                    onSearch = { searchQuery = description; activeTab = 1 },
                                    modifier = gutter.padding(top = 12.dp),
                                )
                            }
                        }
                        if (!hasTagsNow && description.isBlank()) {
                            if (repeatCards.isNotEmpty()) {
                                item(key = "repeat_label") { LoggerPageGroup("Repetir", modifier = gutter) }
                                item(key = "repeat") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 4.dp),
                                    ) {
                                        items(repeatCards, key = { it.key }) { card ->
                                            LoggerRepeatCardView(card.title, card.subtitle) { addLoggedFoods(card.foods, card.title) }
                                        }
                                    }
                                }
                            }
                            val favoriteTemplates = mealTemplates.filter { "template:${it.id}" in favoriteIds }
                            val favoriteCount = favoriteFoods.size + favoriteTemplates.size
                            if (favoriteCount > 0) {
                                item(key = "fav_label") {
                                    LoggerPageGroup(
                                        "Favoritos",
                                        modifier = gutter,
                                        action = if (favoriteCount > 2) {
                                            {
                                                LoggerAct(
                                                    text = if (showAllFavorites) "Ver menos" else "Ver todos",
                                                    onClick = { showAllFavorites = !showAllFavorites },
                                                    color = LoggerColors.faint,
                                                )
                                            }
                                        } else null,
                                    )
                                }
                                item(key = "favorites") {
                                    LoggerTightModule(gutter) {
                                        val rows: List<LoggerFavoriteEntry> = favoriteFoods.map { food ->
                                            LoggerFavoriteEntry(
                                                name = food.name.trim().replaceFirstChar { it.uppercase() },
                                                caption = "${loggerSearchPortion(food, food.name).kcal} kcal",
                                                onOpen = {
                                                    if (food.recipe != null) { recipeToOpen = food; editRecipe = false; showRecipes = true }
                                                    else detailFood = food
                                                },
                                                onAdd = { addSearchFood(food) },
                                            )
                                        } + favoriteTemplates.map { template ->
                                            LoggerFavoriteEntry(
                                                name = template.name,
                                                caption = "${kotlin.math.round(template.foods.sumOf { it.calories }).toInt()} kcal",
                                                onOpen = { applyTemplate(template) },
                                                onAdd = { applyTemplate(template) },
                                            )
                                        }
                                        rows.take(if (showAllFavorites) rows.size else 2).forEachIndexed { index, row ->
                                            LoggerFavoriteRow(row.name, row.caption, onOpen = row.onOpen, onAdd = row.onAdd, divider = index > 0)
                                        }
                                    }
                                }
                            }
                        }
                        if (hasTagsNow) {
                            if (pendingCount > 0) {
                                item(key = "pending") { LoggerPendingBanner(loggerPendingLine(pendingCount), gutter.padding(top = 12.dp)) }
                            }
                            item(key = "sum") {
                                LoggerSummaryCard(
                                    kcal = kcalNow,
                                    protein = tagTotals.protein,
                                    carbs = tagTotals.carbs,
                                    fats = tagTotals.fats,
                                    targetKcal = dailyKcalTarget,
                                    modifier = gutter.padding(top = 12.dp),
                                )
                            }
                            mealFootprint?.let { footprint ->
                                item(key = "meal_footprint") {
                                    Box(gutter) { footprint(activeTagsNow.mapNotNull { it.loggedFood }) }
                                }
                            }
                            itemsIndexed(tags, key = { _, tag -> "food_" + tag.id }) { index, tag ->
                                Box(gutter.padding(top = 8.dp)) { FoodCardFor(tag, index, compact = false) }
                            }
                            item(key = "add_more") {
                                LoggerAct(
                                    text = "Añadir otro alimento",
                                    onClick = { activeTab = 1 },
                                    leadingIcon = KpknLineIcons.Plus,
                                    modifier = gutter.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }

                if (activeTab == 1) {
                    item(key = "search_field") {
                        LoggerSearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onScan = { showScanner = true },
                            modifier = gutter,
                        )
                    }
                    val filtered = filterLoggerCandidates(searchResults, searchFilter)
                    val loading = searchQuery.isNotBlank() &&
                        (searchedQuery != searchQuery || (catalogImportProgress != null && searchResults.isEmpty()))
                    when {
                        searchQuery.isBlank() -> item(key = "search_start") {
                            LoggerSearchStart(
                                recents = recentFoods,
                                favoriteIds = favoriteIds,
                                onAddRecent = { recent -> addLoggedFoods(listOf(recent.food), recent.name) },
                                onOpenRecent = { recent -> addLoggedFoods(listOf(recent.food), recent.name) },
                                onScan = { showScanner = true },
                                onQuickEntry = { showQuickEntry = true },
                                onRecipes = { recipeToOpen = null; editRecipe = false; showRecipes = true },
                                onTemplates = { showTemplates = true },
                                recipeCount = foodDatabase.count { it.recipe != null && it.isCustom },
                                modifier = gutter,
                            )
                        }
                        loading -> item(key = "search_loading") { LoggerSearchSkeleton(gutter) }
                        filtered.isNotEmpty() -> {
                            item(key = "search_filters") {
                                LoggerSearchFilters(selected = searchFilter, onSelect = { searchFilter = it }, modifier = gutter)
                            }
                            item(key = "search_results") {
                                LoggerTightModule(gutter.padding(top = 8.dp)) {
                                    filtered.forEachIndexed { index, candidate ->
                                        val food = candidate.food
                                        val portion = loggerSearchPortion(food, searchQuery)
                                        LoggerSearchRow(
                                            name = food.name.trim().replaceFirstChar { it.uppercase() },
                                            caption = loggerSearchCaption(candidate, searchQuery),
                                            kcal = portion.kcal,
                                            favorite = food.id in favoriteIds,
                                            onToggleFavorite = if (food.id.isNotBlank()) ({ toggleFavorite(food.id) }) else null,
                                            onAdd = { addSearchFood(food) },
                                            onOpen = {
                                                if (food.recipe != null) { recipeToOpen = food; editRecipe = false; showRecipes = true }
                                                else detailFood = food
                                            },
                                            divider = index > 0,
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            if (searchResults.isNotEmpty()) {
                                item(key = "search_filters") {
                                    LoggerSearchFilters(selected = searchFilter, onSelect = { searchFilter = it }, modifier = gutter)
                                }
                            }
                            item(key = "search_empty") {
                                LoggerSearchEmpty(
                                    query = searchQuery.trim(),
                                    onDescribe = {
                                        val text = searchQuery.trim()
                                        description = text
                                        activeTab = 0
                                        analyzeDescription(text)
                                    },
                                    onCreate = {
                                        customFoodToEdit = null
                                        createPrefill = searchQuery.trim()
                                        showCustomForm = true
                                    },
                                    modifier = gutter,
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedVisibility(visible = toast != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                    toast?.let { current ->
                        LoggerToast(
                            message = current.message,
                            icon = if (current.error) KpknLineIcons.Alert else KpknLineIcons.Check,
                            actionLabel = current.actionLabel,
                            onAction = current.onAction?.let { action -> { action(); toast = null } },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                if (showSaveBar) {
                    LoggerSaveBar(
                        kcal = kcalNow,
                        line = loggerSaveBarLine(activeTagsNow.size, kotlin.math.round(tagTotals.protein).toInt(), blockingCount),
                        actionLabel = loggerSaveLabel(mealLabel, isEditing),
                        enabled = !isSaving && !showSuccess && activeTagsNow.isNotEmpty() && blockingCount == 0,
                        onAction = { saveLog() },
                    )
                }
            }

            if (showSuccess) {
                LoggerSavedOverlay(
                    title = loggerSavedTitle(mealType, isEditing),
                    line = loggerSavedLine(savedKcal, savedRemaining),
                )
            }
        }
    }

    // ─── La media hoja de Inicio (f-home-ana, f-home-res) ──────────────────────────
    @Composable
    fun QuickPage() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = if (isAnalyzing) 6.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "$mealLabel · $logTime",
                        style = LoggerText.figure(16).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = LoggerColors.ink,
                    )
                    Text(
                        text = if (isAnalyzing || !hasTagsNow) description else loggerQuickSubtitle(activeTagsNow.size, pendingCount),
                        style = LoggerText.cap,
                        color = LoggerColors.faint,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                if (!isAnalyzing) {
                    LoggerAct(
                        text = "Abrir completo",
                        onClick = { quickExpanded = true },
                        color = LoggerColors.faint,
                        trailingIcon = KpknLineIcons.ChevronRight,
                    )
                }
            }
            if (isAnalyzing) {
                LoggerAnalysisPanel(
                    text = description,
                    readingIndex = readingIndex,
                    icons = loggerMentionRanges(description).take(4).map { range ->
                        loggerFoodIcon(description.substring(range.first, range.last + 1))
                    },
                    height = 210.dp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                analysisNotice?.let {
                    val text = loggerNoticeText(
                        if (tags.any { !it.origin.isPinned }) LoggerNoticeKind.PARTIAL else LoggerNoticeKind.NOTHING,
                        tags.filterNot { it.origin.isPinned }.map(::tagDisplayName),
                    )
                    LoggerAnalysisNotice(
                        title = text.title,
                        message = text.message,
                        onRetry = { analyzeDescription() },
                        onSearch = { searchQuery = description; activeTab = 1; quickExpanded = true },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                tags.forEachIndexed { index, tag ->
                    Box(Modifier.padding(top = 6.dp)) { FoodCardFor(tag, index, compact = true) }
                }
                if (quickAppend) {
                    WriteBox(Modifier.padding(top = 8.dp))
                }
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoggerPill(
                        text = "",
                        onClick = { quickAppend = true; editingText = true },
                        tone = LoggerPillTone.SECONDARY,
                        icon = KpknLineIcons.Plus,
                        contentDescription = "Añadir otro alimento",
                    )
                    LoggerPill(
                        text = "Guardar · ${loggerThousands(kcalNow)} kcal",
                        onClick = { saveLog() },
                        enabled = !isSaving && !showSuccess && activeTagsNow.isNotEmpty() && blockingCount == 0,
                        modifier = Modifier.weight(1f),
                    )
                }
                toast?.let { current ->
                    LoggerToast(
                        message = current.message,
                        icon = if (current.error) KpknLineIcons.Alert else KpknLineIcons.Check,
                        actionLabel = current.actionLabel,
                        onAction = current.onAction?.let { action -> { action(); toast = null } },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    // ─── Hoja ────────────────────────────────────────────────────────────────
    key(sheetRevision, quick) {
        if (quick) {
            KpknSheet(
                onDismissRequest = requestDismiss,
                dismissible = !isSaving && !showSuccess,
                maxHeightFraction = if (isAnalyzing) 0.78f else 0.82f,
                backdropScrim = Color.Black.copy(alpha = 0.467f),
            ) {
                QuickPage()
            }
        } else {
            // El lienzo dibuja el registro como página entera (`.lg`): la hoja ocupa toda la pantalla, sin asa.
            KpknSheet(
                onDismissRequest = requestDismiss,
                dismissible = !isSaving && !showSuccess,
                showDragHandle = false,
                safeTopInset = true,
                maxHeightFraction = 1f,
                stableHeightFraction = 1f,
            ) {
                Box(Modifier.fillMaxWidth().weight(1f)) { FullPage() }
            }
        }
    }

    // ─── Hojas encima del registro: ajuste (f-edit), identidad (f-identity) y ficha (s-detail) ─────
    tags.firstOrNull { it.id == sheetTagId }?.let { tag ->
        val logged = tag.loggedFood
        if (logged == null) {
            sheetTagId = null
        } else {
            val name = tagDisplayName(tag)
            val currentFoodId = tag.foodItem?.id
            val source = when {
                logged.recipeId != null -> "tu receta"
                tag.foodItem?.isCustom == true -> "tu alimento"
                !tag.foodItem?.brand.isNullOrBlank() -> tag.foodItem?.brand.orEmpty()
                else -> "base KPKN"
            }
            val cookable = tag.oilApplied || tag.cookingMethod == CookingMethod.FRITO || tag.cookingMethod == CookingMethod.EMPANIZADO_FRITO
            LoggerFoodSheet(
                state = LoggerFoodSheetState(
                    name = name,
                    icon = loggerFoodIcon(name, tag.canonicalFamily),
                    matchLine = loggerMatchLine(tag.resolutionConfidence, tag.nutritionSource == NutritionSourceKind.HEURISTIC_ESTIMATE, source),
                    grams = tag.amountGrams ?: logged.amount,
                    units = if (logged.recipeId != null) loggerRecipeUnits(logged.unit)
                    else loggerUnits(name, tag.foodItem, tag.foodQuery.ifBlank { tag.tag }),
                    kcal = kotlin.math.round(logged.calories).toInt(),
                    protein = logged.protein,
                    carbs = logged.carbs,
                    fats = logged.fats,
                    portion = if (logged.recipeId == null) tag.portion else null,
                    cooking = if (cookable) {
                        cookingChoice[tag.id] ?: (if (tag.oilLevel == "sin aceite") LoggerCooking.NO_FAT else LoggerCooking.OIL)
                    } else null,
                    variants = if (tag.variantChoices.any { it.food.id == currentFoodId }) {
                        tag.variantChoices.map { LoggerVariantChip(it.dimension, it.food.id, it.label, it.food.id == currentFoodId) }
                    } else emptyList(),
                    excluded = tag.isExcluded,
                    volume = logged.unit == "ml",
                ),
                onGramsChange = { updateTagGrams(tag.id, it) },
                onPortionChange = { updateTagPortion(tag.id, it) },
                onCookingChange = { option ->
                    cookingChoice[tag.id] = option
                    updateTagOilLevel(
                        tag.id,
                        when (option) {
                            LoggerCooking.OIL -> "medio"
                            LoggerCooking.NO_FAT -> "sin aceite"
                            LoggerCooking.BUTTER -> "poco"
                        },
                    )
                },
                onVariant = { id -> identityChoice(tag, id, null) },
                onToggleExcluded = {
                    tags = tags.map { if (it.id == tag.id) NutritionInterpretationBridge.refresh(it.copy(isExcluded = !it.isExcluded)) else it }
                },
                onMacroChange = { macro, value ->
                    when (macro) {
                        LoggerMacro.CALORIES -> updateTagCalories(tag.id, value)
                        LoggerMacro.PROTEIN -> updateTagProtein(tag.id, value)
                        LoggerMacro.CARBS -> updateTagCarbs(tag.id, value)
                        LoggerMacro.FATS -> updateTagFats(tag.id, value)
                    }
                },
                onChange = {
                    sheetTagId = null
                    identityQuery = ""
                    identityTagId = tag.id
                },
                onRemove = {
                    sheetTagId = null
                    removeTagWithUndo(tag.id)
                },
                onDismiss = { sheetTagId = null },
                footprint = foodFootprint?.let { footprint -> { footprint(logged) } },
            )
        }
    }

    tags.firstOrNull { it.id == identityTagId }?.let { tag ->
        val question = tag.interpretationV2?.pendingQuestions?.firstOrNull() as? ClarificationRequest.Identity
        val grams = tag.amountGrams ?: tag.loggedFood?.amount ?: 100.0
        fun captionFor(food: FoodItem?): String {
            if (food == null) return ""
            val kcal = kotlin.math.round(scaleFoodByPortion(food, amountGrams = grams).calories).toInt()
            val units = loggerUnits(food.name, food, tag.foodQuery.ifBlank { tag.tag })
            val natural = units.firstOrNull { it.kind == LoggerUnitKind.NATURAL }
            val amount = natural?.let { "${loggerFormatValue(loggerUnitValue(grams, it), it)} ${loggerUnitNoun(loggerUnitValue(grams, it), it)}" }
                ?: "${kotlin.math.round(grams).toInt()} g"
            return "$amount · $kcal kcal"
        }
        val pool = tag.variantChoices.map { it.food } + tag.reviewCandidates + foodDatabase
        val fromQuestion = question?.let { clarificationCandidateChoices(it) }.orEmpty().map { (id, label) ->
            LoggerCandidate(id, label, captionFor(pool.firstOrNull { it.id == id } ?: findStaticFoodById(id)), likely = false)
        }
        val current = tag.foodItem?.let { food ->
            LoggerCandidate(food.id, food.name, captionFor(food), likely = (tag.resolutionConfidence ?: 0.0) >= 0.8)
        }
        val others = (tag.reviewCandidates + tag.variantChoices.map { it.food }).distinctBy { it.id }
            .filter { it.id != current?.id }
            .map { LoggerCandidate(it.id, it.name, captionFor(it), likely = false) }
        val candidates = (listOfNotNull(current) + fromQuestion + others).distinctBy { it.id }.take(4)
        LoggerIdentitySheet(
            typed = tag.foodQuery.ifBlank { tag.tag },
            candidates = candidates,
            onUse = { id ->
                identityTagId = null
                if (id != tag.foodItem?.id) identityChoice(tag, id, question?.requestId)
            },
            onDismiss = { identityTagId = null },
            searchQuery = identityQuery,
            onSearchQueryChange = { identityQuery = it },
            searchResults = identityResults?.map { LoggerCandidate(it.food.id, it.food.name, captionFor(it.food), likely = false) },
        )
    }

    detailFood?.let { food ->
        LoggerFoodDetailSheet(
            food = food,
            query = searchQuery.ifBlank { food.name },
            mealLabel = mealLabel.lowercase(locale),
            favorite = food.id in favoriteIds,
            onToggleFavorite = { toggleFavorite(food.id) },
            onAdd = { grams ->
                detailFood = null
                addSearchFood(food, grams)
            },
            onDismiss = { detailFood = null },
            // S-D1: editar lo propio vive en el ⋯ de la ficha.
            onEdit = if (food.isCustom && !food.isAiInferred) ({
                if (food.recipe != null) { recipeToOpen = food; editRecipe = true; showRecipes = true }
                else { customFoodToEdit = food; showCustomForm = true }
            }) else null,
            editLabel = if (food.recipe != null) "Editar receta" else "Editar alimento propio",
            footprint = catalogFootprint?.let { footprint -> { grams -> footprint(food, grams) } },
        )
    }

    // ── N3: escáner de códigos de barras y alimentos propios (pantallas completas sobre la hoja) ──
    if (showScanner) {
        val cameraSource = rememberCameraBarcodeSource()
        BarcodeScanFlow(
            source = cameraSource,
            lookup = { code -> nutritionRepo.findFoodByBarcode(code) },
            saveCustomFood = { food -> nutritionRepo.saveCustomFood(food) },
            onConfirm = { food, grams ->
                addScannedFood(food, grams)
                showScanner = false
            },
            onDismiss = { showScanner = false },
            preview = { previewModifier -> CameraPreviewSurface(cameraSource, previewModifier) },
        )
    }
    if (showCustomForm) {
        val closeCustomForm = {
            showCustomForm = false
            customFoodToEdit = null
            createPrefill = null
        }
        CustomFoodFormOverlay(
            initial = customFoodToEdit,
            save = { food -> nutritionRepo.saveCustomFood(food) },
            onSaved = {
                closeCustomForm()
                if (searchQuery.isNotBlank()) performSearch()
            },
            onDismiss = closeCustomForm,
            prefillName = createPrefill,
            delete = { id -> nutritionRepo.deleteCustomFood(id) },
            onDeleted = {
                val removedId = customFoodToEdit?.id
                closeCustomForm()
                if (removedId != null) searchResults = searchResults.filterNot { it.foodId == removedId }
            },
        )
    }

    if (showRecipes) {
        RecipeLibraryOverlay(
            repository = nutritionRepo, initial = recipeToOpen, startInEditor = editRecipe,
            onAdd = { food ->
                tags = tags + loggedFoodToEditableTag(food)
                saveError = null
                showRecipes = false
                showToast("${food.foodName.trim().replaceFirstChar { it.uppercase() }} añadido")
            }, onDismiss = { showRecipes = false },
        )
    }

    // ── N4: plantillas (hoja) y entrada rápida (hoja, s-quick) ──
    if (showTemplates) {
        TemplatesSheet(
            templates = mealTemplates,
            onApply = { template ->
                applyTemplate(template)
                showTemplates = false
            },
            rename = { template, name -> nutritionRepo.saveMealTemplate(template.copy(name = name)) },
            delete = { template -> nutritionRepo.deleteMealTemplate(template.id) },
            onDismiss = { showTemplates = false },
            favoriteIds = favoriteIds.filter { it.startsWith("template:") }.map { it.removePrefix("template:") }.toSet(),
            onToggleFavorite = { toggleFavorite("template:$it") },
        )
    }
    if (showQuickEntry) {
        QuickEntryOverlay(
            initialMeal = mealType,
            onAdd = { food, meal ->
                addQuickEntry(food, meal)
                showQuickEntry = false
            },
            onDismiss = { showQuickEntry = false },
        )
    }

    // ── Guardado: en la página, «Desayuno guardado» y se cierra a los 0,8 s (f-saved); en Inicio, la hoja baja al momento y
    // Inicio enseña su aviso con «Deshacer» (f-home-done). ──
    if (showSuccess) {
        val holdMs = if (wizardReducedMotion()) LoggerSuccessHoldReducedMs else LoggerSuccessHoldMs
        LaunchedEffect(Unit) {
            if (!quick) delay(LoggerSuccessDrawMs + holdMs)
            savedLogSnapshot?.let { onSaved?.invoke(it) }
            showSuccess = false
            closeLogger()
        }
    }
}

/** El nombre que lleva la tarjeta de un alimento: la identidad leída, el alimento elegido o lo escrito, con mayúscula. */
private fun tagDisplayName(tag: ResolvedTag): String =
    (tag.interpretationV2?.canonicalIdentity ?: tag.foodItem?.name ?: tag.tag).trim().replaceFirstChar { it.uppercase() }

/** Una fila de «Favoritos»: un alimento o una plantilla marcada. */
private class LoggerFavoriteEntry(val name: String, val caption: String, val onOpen: () -> Unit, val onAdd: () -> Unit)

/** Un aviso del registro (lienzo `.toast`). */
private data class LoggerToastData(
    val id: Long,
    val message: String,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val error: Boolean,
)

/** «Repetir» (`hcard`): 170 dp, radio 20, el título en una línea y de cuándo es. */
@Composable
private fun LoggerRepeatCardView(title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .width(170.dp)
            .clip(shape)
            .background(LoggerColors.card, shape)
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Repetir $title, $subtitle" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = LoggerText.rowTitle.copy(fontSize = 13.5.sp),
            color = LoggerColors.ink,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = LoggerText.cap,
            color = LoggerColors.faint,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** Una fila de «Favoritos» (f-empty): la estrella llena en energía en su cuadro de 36 dp, nombre, kcal y «+». */
@Composable
private fun LoggerFavoriteRow(name: String, caption: String, onOpen: () -> Unit, onAdd: () -> Unit, divider: Boolean) {
    Column {
        if (divider) LoggerRowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 2.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LoggerIcons.StarFilled, contentDescription = null, modifier = Modifier.size(16.dp), tint = LoggerColors.ene)
            }
            Column(Modifier.weight(1f)) {
                Text(text = name, style = LoggerText.rowTitle, color = LoggerColors.ink, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(text = caption, style = LoggerText.cap, color = LoggerColors.faint, modifier = Modifier.padding(top = 2.dp))
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.063f))
                    .clickable(role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = "Añadir $name" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(KpknLineIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp), tint = LoggerColors.ink)
            }
        }
    }
}

/** `.pending`: «2 dudas: tócalas para afinar o guarda con la estimación» en energía, con la interrogación. */
@Composable
private fun LoggerPendingBanner(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LoggerColors.ene.copy(alpha = 0.063f), shape)
            .border(1.dp, LoggerColors.ene.copy(alpha = 0.2f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(LoggerIcons.Question, contentDescription = null, modifier = Modifier.size(16.dp), tint = LoggerColors.ene)
        Text(text = text, style = LoggerText.cap, color = LoggerColors.ene)
    }
}

