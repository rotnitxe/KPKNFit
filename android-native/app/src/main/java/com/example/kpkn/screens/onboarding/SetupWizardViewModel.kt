package com.example.kpkn.screens.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogConfigurationDisplayName
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.onboarding.*
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanLabels
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.programModeFor
import com.example.kpkn.data.programs.programNameFor
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.kilogramsFromInput
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.*
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.*
import com.example.kpkn.domain.training.generator.GeneratedRoutine
import com.example.kpkn.domain.training.generator.RoutineGenerationException
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.RoutineMode
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.training.split.CatalogExerciseTraitResolver
import com.example.kpkn.domain.training.split.ExerciseTraitResolver
import com.example.kpkn.domain.training.split.RedistributionOptions
import com.example.kpkn.domain.training.split.SplitRedistributor
import com.example.kpkn.domain.training.split.WeekAssignment
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import com.example.kpkn.screens.programs.ReadyWeekSnapshot
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Single serialized WIZCHAT orchestrator. Room owns the full draft; SavedStateHandle owns IDs only. */
class SetupWizardViewModel @JvmOverloads constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val persistence: SetupWizardPersistence = realSetupWizardPersistence(application),
    private val environment: SetupWizardEnvironment = RealSetupWizardEnvironment(application.applicationContext),
    private val commits: SetupWizardCommits? = null,
    /**
     * Puerto de materialización opcional: producción usa el motor real del VM
     * ([SetupWizardViewModel.materializeProgram]); las tests inyectan el suyo
     * para controlar orden y tiempo de los previews (carrera A→B→A).
     */
    private val materializeOverride: SetupWizardMaterializer? = null,
    /**
     * Paquete A · D3 (B-06): repositorio del catálogo de ejercicios. Producción usa siempre el asset real
     * (el valor por defecto); las pruebas inyectan uno que falla y luego se recupera para fijar que un
     * catálogo que no quedó listo nunca se da por cargado.
     */
    catalogRepositoryOverride: ExerciseCatalogRepositoryV2? = null,
) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val commandMutex = Mutex()
    private val catalogRepository: ExerciseCatalogRepositoryV2 =
        catalogRepositoryOverride ?: ApprovedAssetExerciseCatalogRepositoryV2(application.applicationContext)
    /** Serializa la carga del catálogo: una sola `load()` a la vez y sin ver el `Loading` de otra (B-06). */
    private val catalogLoadMutex = Mutex()
    @Volatile private var catalogLoaded = false
    private var initialized = false
    private var initializeJob: kotlinx.coroutines.Job? = null
    private var currentDraftId: String? = null
    private var previewJob: kotlinx.coroutines.Job? = null
    private var candidateJob: kotlinx.coroutines.Job? = null
    /** Invalidates late candidate results even when inputs return to an earlier A key. */
    @Volatile private var candidateGeneration = 0L
    private var exerciseSearchJob: kotlinx.coroutines.Job? = null
    @Volatile private var exerciseLookup: List<ExerciseMuscleInfo>? = null
    @Volatile private var navigationInFlight = false
    private var ringsPreviewJob: kotlinx.coroutines.Job? = null
    private var lastRingsPreviewKey: List<Any?>? = null
    /** Clock injected into the last rings preview so preview and stored check-in share one date. */
    @Volatile private var ringsPreviewNow: Long? = null
    private var preparingTrainingKey: List<Any?>? = null
    private var lastSuccessfulTrainingKey: List<Any?>? = null
    /**
     * T-005 / §15.3 — caché de sesión de evaluaciones (≤32 por clave completa,
     * invalidada al cambiar la revisión de catálogo). Nunca cachea «catálogo en
     * carga» ni guarda resultados de otra revisión.
     */
    private val candidateCache = PlanCandidateSessionCache()
    /**
     * Paquete A · C3 — caché APARTE de los sondeos del asesor de reparaciones (≤ 8). Cada sondeo lleva su propia clave
     * de entrada (`PlanRepairAdvisor` la deriva de la del pedido), así que nunca se mezcla con las evaluaciones del
     * barrido ni las desaloja de [candidateCache]; guarda solo el veredicto (un `Ready` sin programa), no el programa.
     */
    private val repairProbeCache = PlanCandidateSessionCache(maxSize = REPAIR_PROBE_CACHE_ENTRIES)
    /**
     * Entreno v2 · las rutinas que el generador armó para los programas «a medida» (clave = la de su evaluación,
     * `inputKey|planId`): de ellas sale el revelado (razones, notas, ejercicios principales). Si una ya no está, se
     * vuelve a generar con el mismo pedido, que da la misma rutina (el generador es determinista).
     */
    private val generatedRoutines = GeneratedRoutineMemo(maxSize = GENERATED_ROUTINE_MEMO_ENTRIES)
    /**
     * Generación del cálculo de preview. Cada lanzamiento (o liberación de
     * caché) la incrementa y se hace DUEÑO de `isPreviewLoading` y
     * `preparingTrainingKey`: sólo el job dueño publica o limpia, un job viejo
     * cancelado o tardío no escribe nada y una cancelación nunca publica error.
     */
    @Volatile private var previewGeneration = 0L
    /** Diagnóstico de la última escritura de [mutateDraft]; ver [lastMutateDiagnostic]. */
    @Volatile private var mutateDiagnostic: String = "n/a"

    private val _state = MutableStateFlow(SetupWizardState(SetupWizardDraft(commitId = UUID.randomUUID().toString()), isLoading = true))
    val state: StateFlow<SetupWizardState> = _state.asStateFlow()

    /**
     * [preselectedPlanId] (E-18, C.P6): el plan que la persona eligió en la biblioteca («Configurar este plan»). Entra
     * como INTENCIÓN —`selectedCatalogId`, r2 §15.2— y prefija el objetivo cuando el plan sirve a uno solo y los días
     * cuando admite una sola frecuencia, SIN confirmar ningún paso: el asistente sigue preguntando lo demás y, al llegar
     * a PLAN, la selección queda hecha si el plan es viable (si no, el aviso de la selección caída explica por qué y la
     * intención se conserva hasta que la persona elija otro plan). Un id que no existe, no se ofrece o el planificador
     * no puede ofrecer para ningún objetivo y frecuencia se ignora (con registro). Se aplica UNA vez por plan y
     * sesión: tras recrear el ViewModel no pisa lo que la persona ya cambió.
     */
    fun initialize(
        mode: SetupWizardMode,
        nutritionMode: String = "create",
        nutritionPlanId: String? = null,
        draftId: String? = null,
        preselectedPlanId: String? = null,
    ) {
        if (initialized && _state.value.mode == mode && (draftId == null || draftId == currentDraftId)) return
        initializeJob?.cancel()
        initialized = false
        // Invalida la generación del preview, cancela el job en vuelo y apaga
        // su loading: un cancelado de aquí jamás deja `isPreviewLoading` eterno.
        releasePreviewGeneration(cancelInFlight = true)
        candidateGeneration += 1
        candidateJob?.cancel()
        // §15.3: una nueva inicialización no reutiliza evaluaciones de otra sesión.
        candidateCache.invalidate()
        repairProbeCache.invalidate()
        exerciseSearchJob?.cancel()
        ringsPreviewJob?.cancel()
        lastSuccessfulTrainingKey = null
        lastRingsPreviewKey = null
        ringsPreviewNow = null
        // La intención de volver a la revisión vive en el BORRADOR
        // (`reviewReturnStep`): aquí NO se reinicia para que sobreviva a
        // guardar/salir y a la recreación del ViewModel.
        _state.value = _state.value.copy(mode = mode, isLoading = true, machineState = WizChatMachineState.Loading,
            programPreview = null, planCandidates = emptyList(), availablePlanCandidates = emptyList(),
            candidateRejections = emptyList(), droppedSelection = null,
            isCandidateLoading = false,
            planSweep = SetupPlanSweep.IDLE, planReveals = emptyList(), weekLayout = null,
            isPreviewLoading = false,
            nutritionPlanPreview = null, nutritionPreparation = null, ringsBatteriesPreview = null, ringsCoveragePreview = null,
            errors = emptyMap(), lastFailure = null)
        initializeJob = viewModelScope.launch {
            try {
                environment.awaitReady()
                val storedCandidate = savedStateHandle.get<String>(DRAFT_ID_KEY)?.takeIf(String::isNotBlank)
                val storedId = storedCandidate?.takeIf { mode == SetupWizardMode.RESUME || it.startsWith(SetupDraftResolver.canonicalDraftId(scopeFor(mode))) }
                val resumeId = if (mode == SetupWizardMode.RESUME) persistence.listRecoverable().firstOrNull()?.draftId else null
                val id: String = draftId ?: storedId ?: resumeId ?: SetupDraftResolver.canonicalDraftId(scopeFor(mode))
                val persisted = persistence.load(id)
                val restored = persisted?.let { runCatching { json.decodeFromString<SetupWizardDraft>(it.payloadJson) }.getOrNull() }
                if (persisted != null && (restored == null || restored.wizChat.schemaVersion > 2 || restored.wizChat.scriptVersion > WizChatCopyCatalog.SCRIPT_VERSION)) {
                    currentDraftId = id
                    savedStateHandle[DRAFT_ID_KEY] = id
                    _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.UnsupportedDraft, errors = mapOf("draft" to "Este borrador no es compatible con la versión actual. Puedes conservarlo y comenzar uno nuevo."))
                    return@launch
                }
                val normalized = (restored ?: newDraft(mode, nutritionMode, nutritionPlanId, id)).let { value ->
                    val realScope = value.draftScope.ifBlank { scopeFor(mode).name.lowercase() }
                        .let { if (it == "resume") "full" else it }
                    value.copy(
                        draftId = id,
                        draftScope = realScope,
                        nutritionMode = nutritionMode.takeIf { restored == null } ?: value.nutritionMode,
                        nutritionPlanId = nutritionPlanId ?: value.nutritionPlanId,
                        wizChat = normalizeProgress(value.wizChat, realScope, mode),
                    )
                }.let { SetupDraftCompatibility.repair(it) }
                if (restored != null && normalized.stepProgress.origin == SetupProgressOrigin.NOT_CONVERTIBLE) {
                    // Borrador no convertible: se conserva íntegro (sin perder
                    // datos de desarrollo) hasta que el usuario confirme el descarte.
                    currentDraftId = id
                    savedStateHandle[DRAFT_ID_KEY] = id
                    _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.UnsupportedDraft,
                        errors = mapOf("draft" to "Este borrador no es compatible con la versión actual. Se conserva tal cual; puedes descartarlo cuando quieras."))
                    return@launch
                }
                // Catálogo cambiado: se conservan todas las respuestas; solo se
                // señala qué selección necesita revisión (regla pura testeable).
                val revisionChecked = SetupDraftCompatibility.applyCatalogRevision(
                    normalized,
                    persisted?.catalogRevision,
                    PersonalizedPlanCatalog.REVISION,
                ) { planId -> PersonalizedPlanCatalog.find(planId) != null }
                // E-18 (C.P6): la intención de la biblioteca entra DESPUÉS de reparar y de revisar el catálogo, para que
                // ninguna de las dos la borre; las dos pasadas siguientes (candidatos y preview) ya la ven.
                val draft = applyPreselection(revisionChecked, preselectedPlanId, restoredFromStorage = restored != null)
                if (mode == SetupWizardMode.RESUME) {
                    _state.value = _state.value.copy(mode = when (SetupDraftResolver.scopeOf(draft.draftScope)) {
                        SetupDraftScope.TRAINING_ONLY -> SetupWizardMode.TRAINING_ONLY
                        SetupDraftScope.NUTRITION_ONLY -> SetupWizardMode.NUTRITION_ONLY
                        SetupDraftScope.RINGS_ONLY -> SetupWizardMode.RINGS_ONLY
                        else -> SetupWizardMode.FULL
                    })
                }
                currentDraftId = draft.draftId
                savedStateHandle[DRAFT_ID_KEY] = draft.draftId
                val migrated = if (restored != null && draft != restored) draft.copy(
                    revision = maxOf(draft.revision, persisted?.revision?.toInt() ?: draft.revision) + 1,
                ) else draft
                if (migrated !== draft && !persistDraft(migrated)) {
                    _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.UnsupportedDraft,
                        errors = mapOf("draft" to "No pude actualizar este borrador. Lo conservé para que puedas recuperarlo."))
                    return@launch
                }
                initialized = true
                publishDraft(migrated, restored != null, WizChatMachineState.AwaitingAnswer)
                updateCandidates(migrated)
                preparePreview(migrated)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) {
                _state.value = _state.value.copy(isLoading = false, machineState = WizChatMachineState.RecoverableError, errors = mapOf("initialize" to (error.message ?: "No se pudo cargar el asistente")), lastFailure = error.message)
            }
        }
    }

    // ── Paso → datos: única vía productiva de la UI ─────────────────────────
    //
    // Cada setter escribe el dato del paso, lo marca como declarado por el
    // usuario y persiste con revisión monótona. Ninguno mueve el cursor: solo
    // [submitCurrentStep] confirma y avanza exactamente un paso.

    /** Selección única del paso; guarda el valor estable de sus opciones. */
    fun setStepChoice(step: SetupStepId, value: String) =
        mutateDraft(step) { draft -> draft.withStepChoice(step, value, System.currentTimeMillis()) }

    /** Selección múltiple (días, equipo, molestias…); nunca inventa valores. */
    fun setStepChoices(step: SetupStepId, values: Set<String>) =
        mutateDraft(step) { draft -> draft.withStepChoices(step, values, System.currentTimeMillis()) }

    /**
     * Alterna una opción de un paso multi a partir del **evento** de la tarjeta
     * (solo el valor estable, sin Set calculado en la UI).
     *
     * El conjunto resultante se deriva SIEMPRE del último borrador dentro del
     * mutex ([setupToggleExclusive] sobre `latest.selectedValues`), de modo que
     * dos toques seguidos sin esperar el primer persisto no se pisan: el segundo
     * parte del resultado del primero y un valor excluyente (`none`/`unknown`/
     * `omit`) desplaza al resto. No avanza ni confirma: el cursor lo mueve solo
     * [submitCurrentStep].
     */
    fun toggleStepChoice(step: SetupStepId, value: String) =
        mutateDraft(step) { latest ->
            latest.withStepChoices(
                step,
                setupToggleExclusive(
                    latest.selectedValues(step),
                    value,
                    SetupStepDefinitions.of(step)?.exclusiveValues.orEmpty(),
                ),
                System.currentTimeMillis(),
            )
        }

    /** Texto libre del paso (nombre, filas editoriales). */
    fun setStepText(step: SetupStepId, value: String) =
        mutateDraft(step) { draft -> draft.withStepText(step, value, System.currentTimeMillis()) }

    /** Número del paso; [value] null retira el dato (nunca deja un default). */
    fun setStepNumber(step: SetupStepId, value: Double?) =
        mutateDraft(step) { draft -> draft.withStepNumber(step, value, System.currentTimeMillis()) }

    /**
     * Escritura tipada arbitraria sobre el paso (p. ej. el plan elegido o las
     * filas de marcas). Solo persiste el borrador: no toca `acceptedAnswers`
     * del espejo legacy y no confirma el paso.
     */
    fun updateStep(step: SetupStepId, change: (SetupWizardDraft) -> SetupWizardDraft) =
        mutateDraft(step, change)

    // ── Entreno v2: escritura por símbolos ───────────────────────────────────
    //
    // Envoltorios finos de [mutateDraft] (una sola vía de escritura, la misma de [setStepChoice] y [updateStep]):
    // cada uno calcula su resultado sobre el borrador ÚLTIMO dentro del mutex, así dos toques seguidos no se pisan,
    // marca el paso como declarado y NO navega. Los controles (provisionales o animados) solo llaman a estas
    // funciones; la regla vive en los reductores puros de `SetupStepAnswers`.

    /** Alterna un lugar (gimnasio, casa, espacios públicos); siempre queda al menos uno elegido. */
    fun togglePlace(place: TrainingPlace) = mutateDraft(SetupStepId.EQUIPMENT) { latest ->
        if (latest.trainingPlaces == setOf(place)) latest else latest.withPlaceToggled(place)
    }

    /** Alterna un implemento del material; «solo peso corporal» es exclusivo. */
    fun toggleEquipmentSymbol(symbol: EquipmentSymbolId) =
        mutateDraft(SetupStepId.AVAILABILITY) { latest -> latest.withMaterialToggled(symbol) }

    /**
     * «Tengo bicicleta» (paso de cardio): la bicicleta al aire libre es de la persona, no de un lugar. Solo con ella se
     * ofrece «Bicicleta al aire libre» y el generador la usa; quitarla deja por revisar un cardio de bicicleta ya elegido.
     */
    fun setOutdoorBike(has: Boolean) = mutateDraft(SetupStepId.CARDIO_TYPE) { latest -> latest.withOutdoorBike(has) }

    /** Elige el perfil de objetivo (un perfil específico incompatible con el material se escribe pero no se puede confirmar). */
    fun setGoalProfile(profile: TrainingGoalProfile) =
        mutateDraft(SetupStepId.GOAL) { latest -> latest.withGoalProfile(profile) }

    /** Día con más energía (1 = lunes … 7 = domingo); la semana empieza ese día mientras no se mueva el inicio. */
    fun setFreshDay(day: Int) = mutateDraft(SetupStepId.FRESH_DAY) { latest -> latest.withFreshestDay(day) }

    /** Alterna un día de entreno (de 1 a 7 días en total). */
    fun toggleWeekday(day: Int) = mutateDraft(SetupStepId.WEEKDAYS) { latest -> latest.withWeekdayToggled(day) }

    /** Primer día de la semana. */
    fun setWeekStart(day: Int) = mutateDraft(SetupStepId.WEEKDAYS) { latest -> latest.withWeekStart(day) }

    /** Lugar de un día de entreno (solo con dos o más lugares); null vuelve al lugar por defecto. */
    fun setDayPlace(day: Int, place: TrainingPlace?) =
        mutateDraft(SetupStepId.WEEKDAYS) { latest -> latest.withDayPlace(day, place) }

    /** Minutos por sesión: se redondea al múltiplo de 5 más cercano dentro de 20..180. */
    fun setSessionMinutes(minutes: Int) =
        mutateDraft(SetupStepId.SESSION_TIME) { latest -> latest.withSessionMinutes(minutes) }

    /** Nivel de un ejercicio de peso corporal; null retira la respuesta. */
    fun setCapability(skill: CapabilitySkill, level: CapabilityLevel?) =
        mutateDraft(SetupStepId.CAPABILITIES) { latest -> latest.withCapability(skill, level) }

    /** Alterna un músculo a mejorar (hasta 5). */
    fun toggleMuscle(symbol: MuscleSymbol) =
        mutateDraft(SetupStepId.PRIORITIES) { latest -> latest.withMuscleToggled(symbol) }

    /** «Omitir»: sin músculos elegidos (también las sugerencias), que es una respuesta válida. */
    fun clearMuscles() = mutateDraft(SetupStepId.PRIORITIES) { latest -> latest.withMusclesCleared() }

    /** Marca de un levantamiento en kg; null borra la marca («No la sé»). */
    fun setLiftMark(mark: LiftMark, kg: Double?) =
        mutateDraft(SetupStepId.TRAINING_MAX) { latest -> latest.withLiftMark(mark, kg) }

    /** Unidad en que se muestran las marcas (`kg` o `lb`). */
    fun setMarksUnit(unit: String) = mutateDraft(SetupStepId.TRAINING_MAX) { latest -> latest.withMarksUnit(unit) }

    /**
     * Omite el paso SOLO si su definición lo permite. Registra procedencia y
     * estado ausente sin fabricar ningún dato, y no avanza: la confirmación
     * sigue siendo [submitCurrentStep].
     *
     * La grasa corporal ya no se puede omitir (es obligatoria): aquí se rechaza
     * como cualquier paso sin `allowSkip`. Los borradores antiguos que la
     * omitieron se siguen leyendo, pero no validan hasta declarar un porcentaje.
     */
    fun skipStep(step: SetupStepId) {
        val definition = SetupStepDefinitions.of(step)
        if (definition == null || definition.legacyOnly || !definition.allowSkip) {
            _state.value = _state.value.copy(errors = _state.value.errors + (step.name to "Este paso no se puede omitir"))
            return
        }
        mutateDraft(step) { draft ->
            draft.recordStepAnswer(step, SetupAnswerProvenance.USER_DECLARED, SetupValueState.ABSENT)
        }
    }

    /**
     * Vuelve a un paso anterior sin borrar nada. Si se edita desde la
     * revisión final y al confirmar la ruta sigue siendo válida, el cursor
     * regresa a la revisión: no se vuelve a contestar todo el formulario.
     */
    fun editStep(step: SetupStepId) {
        viewModelScope.launch { commandMutex.withLock {
            val state = _state.value
            if (!initialized || state.isCommitting || state.isSubmittingAnswer || state.isSavingAndExiting ||
                state.machineState == WizChatMachineState.Committed ||
                state.machineState == WizChatMachineState.UnsupportedDraft
            ) return@withLock
            val draft = state.draft
            if (step == draft.stepProgress.currentStepId) return@withLock
            if (step !in SetupStepGraph.stepIds(draft.stepContext())) return@withLock
            // La intención de volver a la revisión vive en el BORRADOR
            // (`reviewReturnStep`), no en un campo volatile: así sobrevive a
            // guardar/salir y a la recreación del ViewModel. Solo una edición
            // empezada desde la revisión final la deja puesta.
            val fromReview = draft.stepProgress.currentStepId == SetupStepId.REVIEW_ACTIVATE
            persistAndPublish(
                draft.editStep(step).copy(
                    reviewReturnStep = if (fromReview) SetupStepId.REVIEW_ACTIVATE else null,
                ),
            )
        } }
    }

    /**
     * Solo selecciona el plan: escribe el id elegido en el paso PLAN sin
     * validar candidatos, sin tocar el espejo conversacional y sin avanzar el
     * paso (la confirmación sigue siendo [submitCurrentStep]).
     *
     * T-005 / §15.4: entrar en la selección UNIFICADA normaliza la ruta a
     * CUSTOMIZABLE + PERSONALIZE (el origen histórico sigue en el espejo
     * legacy); un borrador con ID de plan antiguo se conserva para lectura.
     * Elegir una tarjeta también sale de «lo haré más adelante»: esa ruta no
     * crea programa, y una tarjeta sí. La selección conserva intención
     * (`selectedCatalogId`), nunca cambia de plan en silencio: si las
     * respuestas cambian, el preview queda obsoleto y la activación espera
     * re-preparar.
     *
     * Paquete A · D2 (B-01): elegir un plan descarta el aviso de «tu plan elegido ya no encaja»
     * ([SetupWizardState.droppedSelection]); la elección nueva es la respuesta a ese aviso.
     */
    fun selectPlan(id: String) {
        if (_state.value.droppedSelection != null) _state.value = _state.value.copy(droppedSelection = null)
        updateStep(SetupStepId.PLAN) { draft ->
            if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) draft
            else draft.copy(
                selectedCatalogId = id,
                acceptFixedRecipeDifference = false,
                programRoute = SetupProgramRoute.CUSTOMIZABLE,
                trainingPath = SetupTrainingPath.PERSONALIZE,
            ).let { chosen -> if (draft.selectedCatalogId == id) chosen else chosen.withInvalidatedWeekLayout() }
        }
    }

    /**
     * El usuario aplaza el programa: no elige candidato y la activación no crea
     * ninguno. Sigue en PLAN; Continuar salta la configuración del programa
     * (autorregulación, calentamientos y revisión) porque no hay programa.
     */
    fun deferProgramUntilLater() {
        if (_state.value.droppedSelection != null) _state.value = _state.value.copy(droppedSelection = null)
        updateStep(SetupStepId.PLAN) { draft ->
            if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) draft
            else draft.copy(
                selectedCatalogId = null,
                acceptFixedRecipeDifference = false,
                programRoute = SetupProgramRoute.LATER,
                trainingPath = SetupTrainingPath.PERSONALIZE,
            ).withoutWeekLayout()
        }
    }

    /**
     * Entreno v2 · «Otra versión» del programa «a medida»: la misma persona, las mismas respuestas y otra elección entre
     * ejercicios equivalentes (`planVariantSeed + 1`). La semilla entra en la clave del barrido, así que el programa se
     * vuelve a generar (con su overlay «preparando…») y la semana armada vuelve a la del programa nuevo. No elige ni
     * cambia de plan: si el «a medida» ya estaba elegido, sigue elegido con su versión nueva.
     */
    fun anotherPlanVersion() = updateStep(SetupStepId.PLAN) { draft ->
        draft.copy(planVariantSeed = draft.planVariantSeed + 1).withInvalidatedWeekLayout()
    }

    // ── Entreno v2: la semana armada (paso WEEK_LAYOUT) ──────────────────────
    //
    // Las tres escrituras del tablero. Solo cambian `weekLayoutOverrides` / `adaptedSplitId` del borrador; el programa
    // se re-arma solo (están en la clave de la vista previa) y [applyLayout] es el único que los aplica.

    /**
     * Suelta la sesión [sessionId] en el día [toDay] (1 = lunes … 7 = domingo): a un día libre se muda y a uno ocupado
     * intercambia con su sesión ([WeekAssignment.move]). Se guarda la asignación completa de la semana resultante.
     * Mientras la vista previa se recalcula no se mueve nada (la semana que se ve aún no es la vigente).
     */
    fun moveSession(sessionId: String, toDay: Int) {
        val current = _state.value
        if (current.isPreviewLoading || current.isCommitting || current.isSubmittingAnswer || current.isSavingAndExiting) return
        val program = current.programPreview ?: return
        // El tablero ve el movimiento al instante (si no, a los 700 ms vuelve a lo recibido); la vista previa re-armada
        // con la asignación nueva lo confirma después.
        current.weekLayout?.let { layout ->
            val shown = WeekAssignment.move(layout.assignment, sessionId, toDay)
            if (shown != layout.assignment) {
                _state.value = current.copy(weekLayout = layout.copy(assignment = shown, canReset = true))
            }
        }
        updateStep(SetupStepId.WEEK_LAYOUT) { draft ->
            val assignment = if (draft.weekLayoutOverrides.isNotEmpty()) {
                assignmentOf(draft.weekLayoutOverrides)
            } else {
                WeekAssignment.of(program)
            }
            val moved = WeekAssignment.move(assignment, sessionId, toDay)
            if (moved == assignment) draft else draft.copy(weekLayoutOverrides = overridesOf(moved))
        }
    }

    /**
     * Adapta el programa al reparto [splitId] repartiendo sus ejercicios entre los días ([SplitRedistributor]). Antes de
     * escribir nada comprueba que el reparto encaja con el programa y los días; si no, deja el motivo en
     * `weekLayout.refusal` y no cambia el borrador. Un plan de autor solo se adapta con [authoredConfirmed] (la persona
     * aceptó el aviso «Este programa trae su reparto de autor…»); sin él, el motivo es el del redistribuidor. Adaptar
     * empieza la semana de cero (las sesiones movidas eran de la estructura anterior).
     */
    fun adaptToSplit(splitId: String, authoredConfirmed: Boolean = false) {
        val current = _state.value
        if (current.isPreviewLoading) return
        val draft = current.draft
        if (draft.adaptedSplitId == splitId) return
        viewModelScope.launch {
            val refusal = try {
                withContext(Dispatchers.Default) { splitRefusalFor(draft, splitId, authoredConfirmed) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.w(DIAG_TAG, "no se pudo comprobar el reparto: ${failureDetail(error).take(DIAG_REASON_MAX)}")
                SPLIT_UNAVAILABLE
            }
            if (refusal != null) {
                _state.value.weekLayout?.let { layout ->
                    _state.value = _state.value.copy(weekLayout = layout.copy(refusal = refusal))
                }
                return@launch
            }
            updateStep(SetupStepId.WEEK_LAYOUT) { latest ->
                // Solo si el programa sigue siendo el que se comprobó (mismo plan y mismas respuestas).
                val sameProgram = latest.selectedCatalogId == draft.selectedCatalogId &&
                    candidateSetKey(latest) == candidateSetKey(draft)
                if (sameProgram) latest.copy(adaptedSplitId = splitId, weekLayoutOverrides = emptyMap()) else latest
            }
        }
    }

    /** «Restablecer»: la semana vuelve a ser la del programa (sin sesiones movidas ni reparto adaptado). */
    fun resetWeekLayout() = updateStep(SetupStepId.WEEK_LAYOUT) { draft -> draft.withoutWeekLayout() }

    /**
     * Por qué el programa preparado del plan elegido no se puede adaptar a [splitId] (null = sí se puede): el mismo
     * redistribuidor y los mismos días que usará [applyLayout], sobre el programa ANTES de la semana armada.
     */
    private suspend fun splitRefusalFor(draft: SetupWizardDraft, splitId: String, authoredConfirmed: Boolean): String? {
        val split = SPLIT_TEMPLATES.firstOrNull { it.id == splitId } ?: return SPLIT_UNAVAILABLE
        val source = if (_state.value.planAdaptedToBodyweight) bodyweightAdapted(draft) else draft
        val base = materializeBase(source).program ?: return SPLIT_UNAVAILABLE
        if (sessionPlacesOf(base).size > 1) return SPLIT_MIXED_PLACES
        val resolver = traitResolver() ?: return SPLIT_UNAVAILABLE
        val result = SplitRedistributor.redistribute(
            program = base,
            split = split,
            weekdays = draft.orderedWeekdays(),
            resolver = resolver,
            allowAuthoredRecipes = authoredConfirmed,
            options = RedistributionOptions(propagateToOtherWeeks = true, targetMinutes = draft.minutesPerSession),
        )
        return if (result.compatible) null else result.reason ?: SPLIT_UNAVAILABLE
    }

    /**
     * Paquete A · C3 — aplica UNA reparación de un toque que propuso el asesor ([PlanRepairAdvisor]) para el plan propio
     * rechazado. Usa la misma escritura que el resto de la API de pasos (`mutateDraft`: serializada, con revisión
     * monótona y sin mover el cursor): la persona sigue en PLAN y los candidatos se recalculan solos porque cambia la
     * huella de entradas (material, tiempo, cardio, objetivo o reparto). Nunca borra una respuesta que la reparación
     * no toca. Ver [applyRepairs] para el caso de dos reparaciones encadenadas.
     */
    fun applyRepair(repair: PlanRepair) = applyRepairs(listOf(repair))

    /**
     * Aplica las reparaciones de [repairs] en orden y en UNA sola escritura del borrador (p. ej. confirmar el rack y
     * el banco y, con ellos confirmados, subir los minutos): una sola persistencia, un solo barrido de candidatos y
     * ningún estado intermedio visible. El paso que se marca como declarado es el de la primera reparación.
     */
    fun applyRepairs(repairs: List<PlanRepair>) {
        val first = repairs.firstOrNull() ?: return
        Log.i(DIAG_TAG, "reparación de un toque: ${repairs.joinToString("+") { it::class.java.simpleName }}")
        mutateDraft(step = first.declaredStep()) { draft ->
            repairs.fold(draft) { current, repair -> current.withRepair(repair) }
        }
    }

    /**
     * E-18 — el plan de la biblioteca como intención. Con una selección que no existe o no se ofrece no cambia nada
     * (queda en el registro). [restoredFromStorage] distingue un borrador ya guardado —al que NO se vuelve a aplicar la
     * misma preselección tras recrear el ViewModel, para no pisar lo que la persona cambió después— de uno nuevo, que
     * no tiene nada que proteger y la recibe otra vez.
     */
    private fun applyPreselection(
        draft: SetupWizardDraft,
        planId: String?,
        restoredFromStorage: Boolean,
    ): SetupWizardDraft {
        val wanted = planId?.trim()?.takeIf { it.isNotEmpty() } ?: return draft
        if (restoredFromStorage && savedStateHandle.get<String>(PRESELECTED_PLAN_KEY) == wanted) return draft
        val seeded = draft.withPreselectedPlan(wanted)
        if (seeded == null) {
            Log.w(
                DIAG_TAG,
                "preselección ignorada: el plan «$wanted» no existe, no se ofrece, el planificador no lo puede ofrecer " +
                    "para ningún objetivo y frecuencia o el borrador no incluye entrenamiento",
            )
            return draft
        }
        savedStateHandle[PRESELECTED_PLAN_KEY] = wanted
        return seeded.withChangeImpacts(draft)
    }

    fun setWeightUnit(unit: String) = mutateDraft(step = SetupStepId.WEIGHT) { draft ->
        if (unit !in setOf("kg", "lb") || unit == draft.weightUnit) draft else {
            val nutrition = draft.nutritionDraft?.let { n ->
                val existing = parseLocalizedNumber(n.targetWeightText)
                val kg = existing?.let { kilogramsFromInput(it, n.weightUnit) }
                val targetText = kg?.let { WizChatWeightScale.format(WizChatWeightScale.snap(WizChatWeightScale.toDisplay(it, unit))) }
                n.copy(weightUnit = unit, targetWeightText = targetText ?: n.targetWeightText,
                    targetValueText = if (n.goalMetric == GoalMetric.WEIGHT) targetText ?: n.targetValueText else n.targetValueText)
            }
            draft.copy(weightUnit = unit, weightUnitChanged = true, nutritionDraft = nutrition,
                wizChat = draft.wizChat.copy(revision = draft.wizChat.revision + 1))
        }
    }
    fun updateNutritionInput(update: (NutritionWizardDraft) -> NutritionWizardDraft) = mutateDraft {
        val current = it.nutritionDraft ?: NutritionWizardDraft(mode = it.nutritionMode, planId = it.nutritionPlanId)
        it.copy(nutritionDraft = update(current))
    }
    fun toggleSound() = mutateDraft { it.copy(wizChat = it.wizChat.copy(soundEnabled = !it.wizChat.soundEnabled)) }
    fun confirmActivation(confirmed: Boolean) = mutateDraft { it.copy(confirmActivation = confirmed) }
    fun acceptFixedRecipeDifference(accepted: Boolean) = mutateDraft { it.copy(acceptFixedRecipeDifference = accepted) }
    private fun toggleBranch(branch: String) = mutateDraft { draft ->
        val branches = draft.wizChat.advancedBranches
        draft.copy(wizChat = draft.wizChat.copy(advancedBranches = if (branch in branches) branches - branch else branches + branch))
    }
    fun openAdvanced() = toggleBranch("training")
    fun openNutritionAdvanced() = toggleBranch("nutrition")
    fun openRingsAdvanced() = toggleBranch("rings")
    fun setRingsMuscleScope(scope: InitialRecoveryMuscleScope) = mutateDraft { draft ->
        val answers = draft.ringsAnswers ?: SetupRingsAnswers()
        val selected = if (scope == InitialRecoveryMuscleScope.FULL_BODY) emptySet() else answers.recentMuscles
        val allowed = when (scope) {
            InitialRecoveryMuscleScope.FULL_BODY -> InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS.toSet()
            InitialRecoveryMuscleScope.SELECTED -> selected
            InitialRecoveryMuscleScope.UNKNOWN -> emptySet()
        }
        draft.copy(ringsAnswers = answers.copy(muscleScope = scope, recentMuscles = selected),
            manualMuscleOverrides = draft.manualMuscleOverrides.filterKeys { it in allowed })
    }
    fun toggleRingsMuscle(muscle: String) = mutateDraft { draft ->
        val answers = draft.ringsAnswers ?: SetupRingsAnswers(muscleScope = InitialRecoveryMuscleScope.SELECTED)
        val selected = if (muscle in answers.recentMuscles) answers.recentMuscles - muscle else answers.recentMuscles + muscle
        draft.copy(ringsAnswers = answers.copy(muscleScope = InitialRecoveryMuscleScope.SELECTED, recentMuscles = selected),
            manualMuscleOverrides = draft.manualMuscleOverrides.filterKeys { it in selected })
    }
    fun setManualMuscleOverride(muscle: String, level: Int) = mutateDraft { draft ->
        val a = draft.ringsAnswers
        val allowed = when (a?.muscleScope) {
            InitialRecoveryMuscleScope.FULL_BODY -> muscle in InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS
            InitialRecoveryMuscleScope.SELECTED -> muscle in a.recentMuscles
            else -> false
        }
        if (allowed) draft.copy(manualMuscleOverrides = draft.manualMuscleOverrides + (muscle to level.coerceIn(1, 5))) else draft
    }
    fun setManualEnergyOverride(level: Int) = mutateDraft { it.copy(manualEnergyOverride = level.coerceIn(1, 5)) }
    fun setManualStructureOverride(level: Int) = mutateDraft { it.copy(manualStructureOverride = level.coerceIn(1, 5)) }
    fun candidatePlans(): List<SetupPlanCandidate> = _state.value.planCandidates
    fun showMoreCandidates() {
        val current = _state.value
        _state.value = current.copy(
            planCandidates = visibleCandidatesFor(
                available = current.availablePlanCandidates,
                selectedId = current.draft.selectedCatalogId,
                minimumVisible = current.planCandidates.size + CANDIDATES_PAGE_SIZE,
            ),
        )
    }

    /**
     * La primera semana REAL del candidato [planId] para la hoja «Cómo funciona» (C.P5): la del programa que la
     * evaluación ya dejó preparado (`Ready.preparedPlan`), tal como saldría si se eligiera ese plan con las
     * respuestas de ahora. Solo lee la caché de evaluaciones: nunca materializa nada.
     *
     * Devuelve null si no hay semana lista: el plan no es un candidato viable de la lista vigente, sus respuestas
     * cambiaron desde el barrido, el catálogo de ejercicios todavía no está cargado, o la caché (32 evaluaciones,
     * las tres primeras tarjetas se refrescan al terminar el barrido) ya soltó ese plan. En ese caso la hoja
     * dice «Se genera con tus días, tu tiempo y tu material».
     */
    fun readyWeekSnapshotFor(planId: String): ReadyWeekSnapshot? {
        val ready = readyCandidateFor(planId) ?: return null
        return ReadyWeekSnapshot.from(
            program = ready.preparedPlan,
            names = { configurationId -> catalogConfigurationDisplayName(configurationId) },
            equipmentOf = { configurationId -> CompositionMetadataHolder.current?.metadata(configurationId)?.equipmentId },
        ).takeIf { snapshot -> snapshot.sessions.isNotEmpty() }
    }

    fun activeNutritionPlan(): NutritionPlan? = environment.activeNutritionPlan()
    fun hasInitialRecoveryEvidence(): Boolean = environment.hasInitialRecoveryEvidence()
    fun searchExercises(query: String) {
        exerciseSearchJob?.cancel()
        val term = query.trim()
        if (term.isBlank()) {
            _state.value = _state.value.copy(exerciseSuggestions = emptyList(), isExerciseSearching = false, exerciseSearchError = null)
            return
        }
        _state.value = _state.value.copy(exerciseSuggestions = emptyList(), isExerciseSearching = true, exerciseSearchError = null)
        exerciseSearchJob = viewModelScope.launch {
            try {
                ensureCatalogLoaded()
                val matches = withContext(Dispatchers.IO) {
                    val all = exerciseLookup ?: (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)
                        ?.catalog?.toLegacyConfigurationLookup()?.values?.distinctBy { it.id }
                        .orEmpty().also { exerciseLookup = it }
                    all.asSequence().filter { it.name.contains(term, ignoreCase = true) }.take(14).toList()
                }
                currentCoroutineContext().ensureActive()
                if (_state.value.draft.stepProgress.currentStepId == SetupStepId.PLAN) _state.value = _state.value.copy(
                    exerciseSuggestions = matches, isExerciseSearching = false)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (_state.value.draft.stepProgress.currentStepId == SetupStepId.PLAN) _state.value = _state.value.copy(
                    exerciseSuggestions = emptyList(), isExerciseSearching = false,
                    exerciseSearchError = "No pude consultar los ejercicios. Inténtalo otra vez.")
            }
        }
    }
    fun ringsPreview(): SetupRingsMapping = ringsMapping(_state.value.draft)
    fun retryRingsPreview() {
        if (_state.value.machineState == WizChatMachineState.Committed || _state.value.isCommitting) return
        lastRingsPreviewKey = null
        ringsPreviewNow = null
        _state.value = _state.value.copy(ringsPreviewError = null, errors = _state.value.errors - "rings_preview")
        prepareRingsPreview(_state.value.draft)
    }

    /**
     * Specific retry for one inline error: every failure keeps the key of the
     * operation that produced it, so the UI never offers a generic reload for
     * a save/preview/commit problem.
     */
    fun retryOperationForError(key: String): SetupRetryOperation? = when (key) {
        "initialize" -> SetupRetryOperation.LOAD
        "save" -> SetupRetryOperation.SAVE
        "preview" -> SetupRetryOperation.PREVIEW
        "candidates" -> SetupRetryOperation.CANDIDATES
        "rings_preview" -> SetupRetryOperation.RINGS_PREVIEW
        "commit" -> SetupRetryOperation.COMMIT
        else -> null
    }

    /**
     * Retries the operation that failed. LOAD re-runs initialization (modal
     * RecoverableError only) or, when the draft is already loaded, persists the
     * in-memory draft instead of silently doing nothing; SAVE repersists without
     * losing unpersisted answers; the rest recompute the corresponding preview.
     */
    fun retryFailedOperation(operation: SetupRetryOperation, expectedRevision: Int? = null) {
        when (operation) {
            SetupRetryOperation.LOAD -> {
                _state.value = _state.value.copy(errors = emptyMap(), lastFailure = null)
                if (initialized) retryPersist() else initialize(_state.value.mode, draftId = currentDraftId)
            }
            SetupRetryOperation.SAVE -> retryPersist()
            SetupRetryOperation.PREVIEW -> retryPreview()
            SetupRetryOperation.CANDIDATES -> retryCandidates()
            SetupRetryOperation.RINGS_PREVIEW -> retryRingsPreview()
            SetupRetryOperation.COMMIT -> retryCommit(expectedRevision)
        }
    }

    /** Dismisses one inline error (e.g. closing the banner). */
    fun clearError(key: String) {
        if (key !in _state.value.errors) return
        _state.value = _state.value.copy(errors = _state.value.errors - key)
    }

    /** Dismisses every inline error. */
    fun clearErrors() {
        if (_state.value.errors.isEmpty()) return
        _state.value = _state.value.copy(errors = emptyMap())
    }

    private fun retryPersist() {
        viewModelScope.launch { commandMutex.withLock {
            val current = _state.value
            if (!initialized || current.isCommitting || current.machineState == WizChatMachineState.Committed) return@withLock
            // Reintenta el guardado del borrador en memoria sin perder respuestas:
            // la revisión avanza de forma monótona frente a la fila guardada.
            val next = current.draft.withNextDraftRevision(current.draft)
            _state.value = current.copy(machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap(), isSubmittingAnswer = true)
            if (persistDraft(next)) {
                publishDraft(next, true, WizChatMachineState.AwaitingAnswer)
            } else {
                _state.value = _state.value.copy(draft = next, machineState = WizChatMachineState.AwaitingAnswer, dirty = true,
                    isSubmittingAnswer = false)
            }
        } }
    }

    private fun retryPreview() {
        lastSuccessfulTrainingKey = null
        _state.value = _state.value.copy(previewError = null, errors = _state.value.errors - "preview")
        preparePreview(_state.value.draft)
    }

    private fun retryCandidates() {
        _state.value = _state.value.copy(previewError = null, errors = _state.value.errors - "candidates")
        updateCandidates(_state.value.draft)
    }

    private fun retryCommit(expectedRevision: Int?) {
        viewModelScope.launch {
            if (expectedRevision != null && _state.value.draft.revision != expectedRevision) return@launch
            commit()
        }
    }

    /** Back never deletes answers; it only moves the step cursor. */
    fun back(): Boolean = goBack()

    /** Whether the step cursor can move one step back. */
    fun canGoBack(): Boolean {
        if (!initialized || _state.value.isCommitting || _state.value.machineState == WizChatMachineState.Committed) return false
        return SetupStepGraph.previous(_state.value.draft.stepProgress.currentStepId,
            _state.value.draft.stepContext(), _state.value.draft.stepProgress.visited) != null
    }

    fun goBack(): Boolean {
        if (!initialized || _state.value.isCommitting || _state.value.machineState == WizChatMachineState.Committed) return false
        val expectedStep = _state.value.draft.stepProgress.currentStepId
        if (SetupStepGraph.previous(expectedStep, _state.value.draft.stepContext(), _state.value.draft.stepProgress.visited) == null) return false
        if (navigationInFlight) return false
        navigationInFlight = true
        viewModelScope.launch {
            try { commandMutex.withLock {
                if (_state.value.draft.stepProgress.currentStepId != expectedStep) return@withLock
                // Navegación manual: se retira la intención de volver a la
                // revisión en el BORRADOR persistido (nunca en memoria volátil).
                var landed = _state.value.draft.copy(reviewReturnStep = null).goBack()
                // La edad vive en la página del alias y un hito es el overlay de «bloque completado», no una
                // página: atrás no se detiene en ninguno (desde la primera pregunta de un bloque vuelve a la
                // última pregunta del anterior, sin repetir la celebración).
                while (landed.stepProgress.currentStepId.let { it == SetupStepId.AGE || SetupStepGraph.isMilestone(it) }) {
                    val before = landed.stepProgress.currentStepId
                    val earlier = landed.goBack()
                    if (earlier.stepProgress.currentStepId == before) break
                    landed = earlier
                }
                persistAndPublish(landed)
            } } finally { navigationInFlight = false }
        }
        return true
    }

    /**
     * Safe delegation to [submitCurrentStep]: the same gate, revisions and
     * exactly-once advance that the Continuar CTA uses.
     */
    fun goNext(): Boolean = submitCurrentStep(_state.value.draft.stepProgress.currentStepId).accepted

    /**
     * Confirms and advances exactly one step. Repeated callbacks are dropped:
     * the synchronous gate rejects while a confirmation is in flight, and the
     * cursor/revision are re-checked inside the serialized queue, so a stale
     * callback can never double-advance.
     */
    fun submitCurrentStep(
        step: SetupStepId = _state.value.draft.stepProgress.currentStepId,
        expectedRevision: Int? = null,
    ): SetupSubmitResult {
        val current = _state.value
        if (!initialized || current.isCommitting || current.machineState == WizChatMachineState.Committed ||
            current.machineState == WizChatMachineState.UnsupportedDraft) {
            Log.w(DIAG_TAG, "submit $step → DROP initialized=$initialized committing=${current.isCommitting} machine=${current.machineState}")
            return SetupSubmitResult(SetupSubmitOutcome.DROPPED)
        }
        if (navigationInFlight || current.isSubmittingAnswer || current.isSavingAndExiting ||
            step != current.draft.stepProgress.currentStepId) {
            Log.w(DIAG_TAG, "submit $step → DROP navigation=$navigationInFlight submitting=${current.isSubmittingAnswer} saving=${current.isSavingAndExiting} cursor=${current.draft.stepProgress.currentStepId}")
            return SetupSubmitResult(SetupSubmitOutcome.DROPPED)
        }
        if (expectedRevision != null && expectedRevision != current.draft.revision) {
            Log.w(DIAG_TAG, "submit $step → DROP revision expected=$expectedRevision actual=${current.draft.revision}")
            return SetupSubmitResult(SetupSubmitOutcome.DROPPED)
        }
        val validation = SetupWizardValidation.validateStep(current.draft, step)
        if (validation.any { it.isBlocking }) {
            // Solo claves de validación: nunca el valor del usuario.
            Log.w(DIAG_TAG, "submit $step → REJECT ${validation.filter { it.isBlocking }.map { it.key }}")
            _state.value = current.copy(errors = validation.mapNotNull { check -> check.message?.let { check.key to it } }.toMap())
            return SetupSubmitResult(SetupSubmitOutcome.REJECTED)
        }
        // Paquete A · D2 (B-01): en PLAN, Continuar exige una selección VIABLE de la lista vigente.
        val planGate = planSelectionGate(current, step)
        if (planGate.isNotEmpty()) {
            Log.w(DIAG_TAG, "submit $step → REJECT selección no viable (candidatos=${current.availablePlanCandidates.size} cargando=${current.isCandidateLoading})")
            _state.value = current.copy(errors = planGate)
            return SetupSubmitResult(SetupSubmitOutcome.REJECTED)
        }
        // El plan de alimentación con calorías peligrosamente bajas o una pérdida extrema no se confirma.
        val nutritionGate = nutritionResultGate(current, step)
        if (nutritionGate.isNotEmpty()) {
            Log.w(DIAG_TAG, "submit $step → REJECT plan de nutrición con hardStop")
            _state.value = current.copy(errors = nutritionGate)
            return SetupSubmitResult(SetupSubmitOutcome.REJECTED)
        }
        navigationInFlight = true
        Log.d(DIAG_TAG, "submit $step → ACCEPTED (encolado, cursor=${current.draft.stepProgress.currentStepId} rev=${current.draft.revision})")
        viewModelScope.launch {
            try { commandMutex.withLock { submitCurrentStepLocked(current.draft, step, expectedRevision) } }
            finally { navigationInFlight = false }
        }
        return SetupSubmitResult(SetupSubmitOutcome.ACCEPTED)
    }

    /**
     * Alias y edad viven en la misma página. Confirma los dos de un avance
     * cuando la edad ya se movió. Si el cursor está en la edad, confirma solo esa.
     */
    fun submitAliasAgePair(): SetupSubmitResult {
        val draft = _state.value.draft
        val cursor = draft.stepProgress.currentStepId
        if (cursor == SetupStepId.AGE) return submitCurrentStep(SetupStepId.AGE)
        if (cursor != SetupStepId.NAME || draft.ageYears == null) return submitCurrentStep(SetupStepId.NAME)
        confirmPairedAge = true
        val result = submitCurrentStep(SetupStepId.NAME)
        if (!result.accepted) confirmPairedAge = false
        return result
    }

    private var confirmPairedAge = false

    fun submitAnthropometryPair(): SetupSubmitResult {
        val draft = _state.value.draft
        if (draft.stepProgress.currentStepId != SetupStepId.HEIGHT || draft.weightKg == null) {
            return submitCurrentStep(SetupStepId.HEIGHT)
        }
        confirmPairedWeight = true
        val result = submitCurrentStep(SetupStepId.HEIGHT)
        if (!result.accepted) confirmPairedWeight = false
        return result
    }

    private var confirmPairedWeight = false

    /**
     * Confirma altura y peso en un solo avance cuando las dos reglas caben
     * en la misma pantalla. El peso tiene que existir ya (el usuario lo movió
     * o lo confirmó); si no, se confirma solo la altura.
     */

    /** Runs fully under the command mutex: re-validates, records, advances, persists. */
    private suspend fun submitCurrentStepLocked(snapshot: SetupWizardDraft, expectedStep: SetupStepId, expectedRevision: Int?) {
        val pairWeight = confirmPairedWeight && expectedStep == SetupStepId.HEIGHT
        confirmPairedWeight = false
        val pairAge = confirmPairedAge && expectedStep == SetupStepId.NAME
        confirmPairedAge = false
        val current = _state.value
        if (!initialized || current.isCommitting || current.machineState == WizChatMachineState.Committed) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → DROP initialized=$initialized committing=${current.isCommitting} machine=${current.machineState}")
            return
        }
        if (expectedStep != current.draft.stepProgress.currentStepId) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → DROP cursor=${current.draft.stepProgress.currentStepId}")
            return
        }
        if (expectedRevision != null && expectedRevision != current.draft.revision) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → DROP revision expected=$expectedRevision actual=${current.draft.revision}")
            return
        }
        if (snapshot.draftId != current.draft.draftId) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → DROP draftId ${snapshot.draftId} ≠ ${current.draft.draftId}")
            return
        }
        val validation = SetupWizardValidation.validateStep(current.draft, expectedStep)
        if (validation.any { it.isBlocking }) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → REJECT ${validation.filter { it.isBlocking }.map { it.key }}")
            _state.value = _state.value.copy(errors = validation.mapNotNull { check -> check.message?.let { check.key to it } }.toMap())
            return
        }
        // Misma puerta que en `submitCurrentStep`, ahora sobre el estado vigente dentro de la cola: la lista
        // pudo cambiar entre el toque y este punto.
        val planGate = planSelectionGate(current, expectedStep)
        if (planGate.isNotEmpty()) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → REJECT selección no viable (candidatos=${current.availablePlanCandidates.size} cargando=${current.isCandidateLoading})")
            _state.value = _state.value.copy(errors = planGate)
            return
        }
        val nutritionGate = nutritionResultGate(current, expectedStep)
        if (nutritionGate.isNotEmpty()) {
            Log.w(DIAG_TAG, "submitLocked $expectedStep → REJECT plan de nutrición con hardStop")
            _state.value = _state.value.copy(errors = nutritionGate)
            return
        }
        val previous = current.draft
        var stepping = previous.confirmCurrentStep(expectedStep)
        val paired = pairWeight &&
            stepping.stepProgress.currentStepId == SetupStepId.WEIGHT &&
            stepping.weightKg != null &&
            SetupWizardValidation.validateStep(stepping, SetupStepId.WEIGHT).none { it.isBlocking }
        if (paired) stepping = stepping.confirmCurrentStep(SetupStepId.WEIGHT)
        val pairedAge = pairAge &&
            stepping.stepProgress.currentStepId == SetupStepId.AGE &&
            stepping.ageYears != null &&
            SetupWizardValidation.validateStep(stepping, SetupStepId.AGE).none { it.isBlocking }
        if (pairedAge) stepping = stepping.confirmCurrentStep(SetupStepId.AGE)
        val genderAlreadyChosen = !stepping.stepSelections[SetupStepId.EQUATION_SEX].isNullOrEmpty()
        val skippedGender = stepping.stepProgress.currentStepId == SetupStepId.EQUATION_SEX && genderAlreadyChosen &&
            SetupWizardValidation.validateStep(stepping, SetupStepId.EQUATION_SEX).none { it.isBlocking }
        if (skippedGender) stepping = stepping.confirmCurrentStep(SetupStepId.EQUATION_SEX)
        val confirmedStep = when {
            skippedGender -> SetupStepId.EQUATION_SEX
            paired -> SetupStepId.WEIGHT
            pairedAge -> SetupStepId.AGE
            else -> expectedStep
        }
        val confirmed = SetupDraftCompatibility.normalizeLegacyRouteAtPlan(
            resumeReviewAfterEdit(stepping, confirmedStep),
        ).withNextDraftRevision(previous)
        val previousCandidateKey = candidateSetKey(previous)
        _state.value = _state.value.copy(machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap(), isSubmittingAnswer = true)
        val persisted = persistDraft(confirmed)
        if (persisted) {
            publishDraft(confirmed, true, WizChatMachineState.AwaitingAnswer)
        } else {
            _state.value = _state.value.copy(draft = confirmed, dirty = true, machineState = WizChatMachineState.AwaitingAnswer,
                isSubmittingAnswer = false)
        }
        Log.d(DIAG_TAG, "submitLocked $expectedStep → ${confirmed.stepProgress.currentStepId} rev=${confirmed.revision} persisted=$persisted")
        if (previousCandidateKey != candidateSetKey(confirmed)) updateCandidates(confirmed)
        preparePreview(confirmed)
    }

    /**
     * Editing from the final review and confirming the edited step lands back
     * on the review when the route still contains it and the step still
     * validates, so the user never re-answers the whole form.
     *
     * La intención ([SetupWizardDraft.reviewReturnStep]) vive en el borrador y
     * se consume AQUÍ, dentro del borrador confirmado que se persiste: sobrevive
     * a guardar/salir y a la recreación del ViewModel, y se consume exactamente
     * una vez.
     */
    private fun resumeReviewAfterEdit(confirmed: SetupWizardDraft, editedStep: SetupStepId): SetupWizardDraft {
        val target = confirmed.reviewReturnStep
        val consumed = confirmed.copy(reviewReturnStep = null)
        if (target != SetupStepId.REVIEW_ACTIVATE) return consumed
        if (target !in SetupStepGraph.stepIds(consumed.stepContext())) return consumed
        if (SetupWizardValidation.validateStep(consumed, editedStep).any { it.isBlocking }) return consumed
        return consumed.copy(
            stepProgress = consumed.stepProgress.at(target, consumed.stepContext()),
            wizChat = consumed.wizChat.copy(
                currentQuestionId = WizChatQuestionId.REVIEW,
                stage = WizChatStage.REVIEW,
                terminal = true,
                revision = consumed.wizChat.revision + 1,
            ),
        )
    }

    /** Compatibility entry point for advanced editor callers; same queue, same Room draft. */
    fun update(change: (SetupWizardDraft) -> SetupWizardDraft) = mutateDraft(change = change)
    fun setName(value: String) = setStepText(SetupStepId.NAME, value)
    fun setAge(value: Int?) = setStepNumber(SetupStepId.AGE, value?.toDouble())
    fun setWeightKg(value: Double?) = setStepNumber(SetupStepId.WEIGHT, value)
    fun setHeightCm(value: Double?) = setStepNumber(SetupStepId.HEIGHT, value)
    fun setChapter(chapter: SetupWizardChapter) = Unit
    fun setProgramRoute(route: SetupProgramRoute) = mutateDraft { it.copy(programRoute = route) }
    fun setModuleChoice(choice: SetupModuleChoice) = mutateDraft { it.copy(includeNutrition = choice == SetupModuleChoice.TRAINING_AND_NUTRITION) }
    fun setVolumeAnswer(change: (SetupVolumeAnswers) -> SetupVolumeAnswers) = mutateDraft { draft ->
        val answers = change(draft.volumeAnswers)
        val profile = buildVolumeProfile(draft.copy(volumeAnswers = answers))
        draft.copy(volumeAnswers = answers, volumeCalibrationProfile = profile, volumeRecommendations = profile?.recommendations.orEmpty(), athleteProfileScore = profile?.athleteProfileScore)
    }
    fun setPriorityMuscles(value: Set<String>) = mutateDraft {
        val priorities = value.map(String::trim).filter(String::isNotBlank).take(3).toSet()
        it.copy(priorityMuscles = priorities, lowerEmphasisMuscles = it.lowerEmphasisMuscles - priorities)
    }
    fun setLowerEmphasisMuscles(value: Set<String>) = mutateDraft {
        it.copy(lowerEmphasisMuscles = value.map(String::trim).filter(String::isNotBlank).toSet() - it.priorityMuscles)
    }
    fun resetManualRecoveryAdjustments() = mutateDraft { it.copy(manualMuscleOverrides = emptyMap(), manualEnergyOverride = null, manualStructureOverride = null) }

    fun addExercise(weekday: Int, info: ExerciseMuscleInfo) {
        val id = "${_state.value.draft.commitId}-exercise-$weekday-${info.id}"
        val exercise = Exercise(id = id, name = info.name, exerciseDbId = info.id, exerciseId = info.id, canonicalExerciseId = info.id, catalogConfigurationId = info.catalogConfigurationId, catalogDefinitionId = info.catalogDefinitionId, catalogRevision = info.catalogRevision, performanceProfileId = info.performanceProfileId, occurrenceId = id, effectiveMuscles = info.involvedMuscles, sets = (1..3).map { ExerciseSet("$id-set-$it", targetReps = 10) })
        mutateDraft { draft ->
            val sessions = draft.sessions.ensureSession(weekday).map { session -> if (session.weekday == weekday && session.exercises.none { it.exercise.exerciseDbId == info.id }) session.copy(exercises = session.exercises + SetupExerciseDraft(id, exercise, info)) else session }
            draft.copy(sessions = sessions)
        }
    }
    fun removeExercise(weekday: Int, id: String) = mutateDraft { draft -> draft.copy(sessions = draft.sessions.map { session -> if (session.weekday == weekday) session.copy(exercises = session.exercises.filterNot { it.id == id || it.exercise.exerciseDbId == id }) else session }) }
    fun renameSession(weekday: Int, title: String) = mutateDraft { draft ->
        val clean = WizChatValidation.cleanText(title).take(40)
        if (clean.isBlank()) draft else draft.copy(sessions = draft.sessions.ensureSession(weekday).map { session ->
            if (session.weekday == weekday) session.copy(title = clean) else session
        })
    }
    fun moveExercise(weekday: Int, id: String, delta: Int) = mutateDraft { draft -> draft.copy(sessions = draft.sessions.map { session ->
        if (session.weekday != weekday) session else {
            val from = session.exercises.indexOfFirst { it.id == id || it.exercise.exerciseDbId == id }
            if (from < 0) session else session.copy(exercises = session.exercises.toMutableList().also { list -> val to = (from + delta).coerceIn(0, list.lastIndex); list.add(to, list.removeAt(from)) })
        }
    }) }
    fun changeSets(weekday: Int, id: String, count: Int) = mutateDraft { draft -> draft.copy(sessions = draft.sessions.map { session -> if (session.weekday != weekday) session else session.copy(exercises = session.exercises.map { item -> if (item.id != id && item.exercise.exerciseDbId != id) item else item.copy(exercise = item.exercise.copy(sets = (1..count.coerceIn(1, 30)).map { n -> item.exercise.sets.getOrNull(n - 1)?.copy(id = "${item.exercise.id}-set-$n") ?: ExerciseSet("${item.exercise.id}-set-$n", targetReps = 10) })) }) }) }
    fun changeReps(weekday: Int, id: String, reps: String) = mutateDraft { draft -> draft.copy(sessions = draft.sessions.map { session -> if (session.weekday != weekday) session else session.copy(exercises = session.exercises.map { item -> if (item.id != id && item.exercise.exerciseDbId != id) item else item.copy(exercise = item.exercise.copy(sets = item.exercise.sets.map { it.copy(targetReps = reps.toIntOrNull()) })) }) }) }

    fun canContinue(): Boolean = _state.value.machineState == WizChatMachineState.AwaitingAnswer

    /**
     * Deprecated behaviour: it used to discard the draft without confirmation.
     * Now it only opens the explicit discard confirmation; the data survives
     * until [confirmDiscard] runs.
     */
    fun clear() = requestDiscard()

    /**
     * Salir no abre ningún cuadro. Lo respondido ya está persistido en cada
     * cambio; esto solo empuja una revisión final y marca la salida.
     */
    suspend fun leaveImmediately(): Boolean = commandMutex.withLock {
        if (!initialized || _state.value.isCommitting) return@withLock false
        val previous = _state.value.draft
        val toSave = previous.withNextDraftRevision(previous)
        val previousState = _state.value.machineState
        _state.value = _state.value.copy(isSavingAndExiting = true, dialog = SetupWizardDialog.NONE, errors = emptyMap())
        val saved = withContext(NonCancellable) { persistDraft(toSave) }
        _state.value = if (saved) {
            _state.value.copy(
                draft = toSave,
                machineState = previousState,
                isSavingAndExiting = false,
                exitCompleted = true,
                dialog = SetupWizardDialog.NONE,
                dirty = false,
                errors = emptyMap(),
                lastFailure = null,
            )
        } else {
            _state.value.copy(isSavingAndExiting = false, machineState = previousState, dialog = SetupWizardDialog.NONE)
        }
        saved
    }

    /** Exit intention: opens the "Guardar y salir / Seguir configurando" dialog. */
    fun requestExit() {
        _state.value = _state.value.copy(dialog = session().requestExit().dialog)
    }

    fun keepConfiguring() {
        _state.value = _state.value.copy(dialog = session().keepConfiguring().dialog)
    }

    /** Separate discard intention; never reachable from the back button. */
    fun requestDiscard() {
        _state.value = _state.value.copy(dialog = session().requestDiscard().dialog)
    }

    /**
     * Saves and leaves. The save must finish before the caller abandons the
     * wizard: the persistence call runs non-cancellable and the result is only
     * published after it completes.
     */
    suspend fun saveAndExit(): Boolean = commandMutex.withLock {
        val plan = session().saveAndExit() as? SetupWizardExitPlan.SaveAndExit ?: return@withLock false
        // La revisión siempre avanza: el guardado no puede perder contra la fila
        // persistida aunque el borrador en memoria lleve cambios sin escribir.
        val previous = _state.value.draft
        val toSave = plan.draft.withNextDraftRevision(previous)
        val previousState = _state.value.machineState
        _state.value = _state.value.copy(isSavingAndExiting = true, machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap())
        val saved = withContext(NonCancellable) { persistDraft(toSave) }
        if (saved) {
            val finished = session().onSavedAndExited()
            _state.value = _state.value.copy(draft = toSave, machineState = previousState,
                isSavingAndExiting = finished.isSavingAndExiting,
                exitCompleted = finished.exitCompleted, dialog = finished.dialog, dirty = false, errors = emptyMap(), lastFailure = null)
        } else {
            // No se navega cuando el guardado falla: el diálogo se mantiene y el
            // error queda inline para poder reintentar sin perder nada.
            _state.value = _state.value.copy(isSavingAndExiting = false, machineState = previousState)
        }
        saved
    }

    /** Discarding requires the explicit confirmation dialog; back never calls this. */
    suspend fun confirmDiscard(): Boolean = commandMutex.withLock {
        if (_state.value.isCommitting) return@withLock false
        val plan = session().confirmDiscard() as? SetupWizardExitPlan.Discard ?: return@withLock false
        // Un borrador no convertible nunca se publica en el estado: su id real
        // vive en currentDraftId. Sin id conocido todavía no se cargó nada y no
        // se borra nada (nunca se destruye datos que el usuario no ha visto).
        val draftId = currentDraftId?.takeIf(String::isNotBlank) ?: plan.draftId.takeIf(String::isNotBlank)
        if (draftId == null) {
            val nothingToDiscard = session().onDiscarded()
            _state.value = _state.value.copy(dialog = nothingToDiscard.dialog, exitCompleted = nothingToDiscard.exitCompleted)
            return@withLock true
        }
        previewJob?.cancel()
        candidateGeneration += 1
        candidateJob?.cancel()
        exerciseSearchJob?.cancel()
        ringsPreviewJob?.cancel()
        try {
            withContext(NonCancellable) { persistence.discard(draftId) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            // El fallo de descarte se reporta: nunca se navega mintiendo.
            _state.value = _state.value.copy(isSavingAndExiting = false, machineState = stateForCurrentStep(),
                errors = mapOf("draft" to "No pude descartar el borrador. Inténtalo de nuevo."), lastFailure = error.message)
            return@withLock false
        }
        savedStateHandle[DRAFT_ID_KEY] = null
        currentDraftId = null
        initialized = true
        val fresh = newDraft(_state.value.mode, "create", null, draftId)
        publishDraft(fresh, false, WizChatMachineState.AwaitingAnswer)
        val finished = session().onDiscarded()
        _state.value = _state.value.copy(dialog = finished.dialog, exitCompleted = finished.exitCompleted)
        true
    }

    private fun session(): SetupWizardSession = SetupWizardSession(
        draft = _state.value.draft,
        dialog = _state.value.dialog,
        isSavingAndExiting = _state.value.isSavingAndExiting,
        exitCompleted = _state.value.exitCompleted,
    )

    suspend fun commit(): String? = commandMutex.withLock {
        val current = _state.value
        if (current.machineState == WizChatMachineState.Committed) return@withLock current.receiptId
        _state.value = current.copy(isCommitting = true, machineState = WizChatMachineState.Committing, errors = emptyMap())
        try {
            // Puerta real: revisión de pasos + validación completa (sin
            // `wizChat.terminal` ni respuestas legacy aceptadas).
            val errors = reviewErrors()
            if (errors.isNotEmpty()) {
                _state.value = _state.value.copy(isCommitting = false, machineState = stateForCurrentStep(), errors = errors)
                return@withLock null
            }
            val draft = _state.value.draft
            val program = if (draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER) _state.value.programPreview ?: error("La vista previa del programa no es ejecutable") else null
            val trackingOnly = isTrackingOnly(draft)
            // Se reutiliza la preparación revisada del preview (planId estable
            // derivado del commitId); nunca se fabrica un EER ni un objetivo.
            val preparation = if (draft.includeNutrition) prepareNutrition(draft) else null
            if (draft.includeNutrition && !trackingOnly && preparation?.plan == null) {
                error(preparation?.errors?.values?.firstOrNull() ?: "Completa el plan de nutrición")
            }
            val nutrition = if (trackingOnly) null else preparation?.plan
            // Un solo reloj para el check-in: el previewado y el persistido en
            // esta misma operación comparten exactamente la fecha.
            val ringsNow = ringsPreviewNow ?: System.currentTimeMillis()
            val rings = ringsMapping(draft, ringsNow)
            val base = environment.settings
            val typed = nutrition?.typedBodyGoal
            val bodyGoals = typed?.targetValueSi?.let { target -> listOf(BodyGoal("plan:${nutrition.id}:${typed.metric.name}", typed.metric.toBodyMetric(), target, typed.unitSi, typed.origin, nutrition.id, System.currentTimeMillis(), System.currentTimeMillis())) }.orEmpty()
            // Check-in real: evidencia completa, calibración parcial o solo
            // molestias declaradas. Nunca se fabrican sesiones sintéticas.
            val wellbeing = if (rings.savesRealCheckIn) {
                calculateRingsPreview(draft, rings, ringsNow).stagedWellbeing
            } else null
            val pendingNutritionDraft = if (SetupPendingNutrition.shouldPreserve(draft)) {
                val pending = SetupPendingNutrition.build(draft)
                SetupPendingNutrition.toCommitField(pending, json.encodeToString(pending))
            } else null
            // Snapshot del día REAL: el objetivo de HOY calculado por el reparto
            // de la ventana; si hoy no tiene objetivo no se graba ninguno.
            val today = LocalDate.now()
            val snapshot = if (!trackingOnly && draft.activateNutrition && nutrition != null) {
                preparation?.days?.firstOrNull { it.date == today }?.let { day ->
                    DailyGoalSnapshot(today.toString(), nutrition.id, day.calorieTargetKcal,
                        day.proteinG, day.carbsG, day.fatG, nutrition.direction,
                        nutrition.calculationOrigin, System.currentTimeMillis())
                }
            } else null
            // El adaptador real se resuelve solo aquí: las tests inyectan el
            // suyo y la construcción del VM nunca toca Room.
            val port = commits ?: realSetupWizardCommits(getApplication())
            val result = port.commit(SetupCommitRequest(
                commitId = draft.commitId,
                draftId = draft.draftId,
                settings = base,
                program = program,
                nutritionPlan = nutrition,
                activateProgram = draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER && draft.activateProgram,
                activateNutrition = draft.includeNutrition && draft.activateNutrition && !trackingOnly,
                derivedBodyGoals = bodyGoals,
                initialWellbeing = wellbeing,
                settingsPatch = buildSettingsPatch(base, draft, program, nutrition, rings, trackingOnly),
                dailyGoalSnapshot = snapshot,
                pendingNutritionDraft = pendingNutritionDraft,
                nutritionTrackingOnly = trackingOnly,
                bodyObservations = SetupActivationPayload.bodyObservations(draft),
            ))
            _state.value = _state.value.copy(draft = draft.copy(
                wizChat = draft.wizChat.copy(terminal = true, currentQuestionId = WizChatQuestionId.REVIEW, stage = WizChatStage.REVIEW),
                stepProgress = draft.stepProgress.at(SetupStepId.REVIEW_ACTIVATE, draft.stepContext()),
            ), dirty = false, receiptId = result.commitId, isCommitting = false, machineState = WizChatMachineState.Committed, errors = emptyMap())
            savedStateHandle[DRAFT_ID_KEY] = null
            // Post-éxito: Body Progress relee su almacenamiento para mostrar
            // las observaciones recién escritas sin reiniciar la app.
            environment.refreshBodyProgress()
            result.commitId
        } catch (cancel: CancellationException) {
            // Un commit interrumpido no se maquilla: vuelve a la pantalla real
            // con el error inline para poder reintentar la operación exacta.
            _state.value = _state.value.copy(isCommitting = false, machineState = stateForCurrentStep(), errors = mapOf("commit" to "La configuración se interrumpió. Inténtalo de nuevo."))
            throw cancel
        }
        catch (error: Throwable) {
            _state.value = _state.value.copy(isCommitting = false, machineState = stateForCurrentStep(), errors = mapOf("commit" to (error.message ?: "No se pudo guardar la configuración")))
            null
        }
    }

    /**
     * Machine state that renders the REAL step screen for the current cursor.
     * Failures (commit, preview) keep the steps visible with an inline error
     * instead of hiding them behind a status dialog.
     */
    private fun stateForCurrentStep(): WizChatMachineState =
        if (_state.value.draft.stepProgress.currentStepId == SetupStepId.REVIEW_ACTIVATE) {
            WizChatMachineState.Reviewing
        } else {
            WizChatMachineState.AwaitingAnswer
        }

    private suspend fun persistAndPublish(draft: SetupWizardDraft) {
        val previous = _state.value.draft
        val previousCandidateKey = candidateSetKey(previous)
        // Cambios fisiológicos reales: marcan pendientes y previews obsoletos.
        // La navegación pura no cambia la huella y por eso no dispara nada.
        // Revisión monótona: models nunca toca draft.revision, así que la frontera
        // de persistencia siempre supera la fila guardada (guard de Room en 126).
        val changed = SetupDraftCompatibility.normalizeLegacyRouteAtPlan(draft)
            .withNextDraftRevision(previous).withChangeImpacts(previous)
        val candidateInputsChanged = previousCandidateKey != candidateSetKey(changed)
        // Entreno v2: la semana armada (sesiones movidas, reparto adaptado) es del programa de las respuestas
        // anteriores; con respuestas nuevas el programa se vuelve a armar y su semana empieza de cero, y el paso
        // WEEK_LAYOUT queda pendiente de revisar (nunca se limpia en silencio).
        val next = if (candidateInputsChanged) changed.withInvalidatedWeekLayout() else changed
        if (candidateInputsChanged) candidateGeneration += 1
        val beforePersist = _state.value.copy(machineState = WizChatMachineState.PersistingAnswer, errors = emptyMap())
        _state.value = if (candidateInputsChanged) {
            withoutStaleCandidatesError(beforePersist.copy(
                planCandidates = emptyList(),
                availablePlanCandidates = emptyList(),
                candidateRejections = emptyList(),
                candidateCounts = SetupCandidateCounts(),
                isCandidateLoading = false,
                planAdaptedToBodyweight = false,
                planSweep = SetupPlanSweep.IDLE,
                planReveals = emptyList(),
            ))
        } else beforePersist
        if (persistDraft(next)) {
            publishDraft(next, true, WizChatMachineState.AwaitingAnswer)
            if (candidateInputsChanged) updateCandidates(next)
            preparePreview(next)
        } else {
            // Guardar falló: el borrador (respuestas incluidas) sigue visible en
            // memoria con un error inline y sin perder nada, para reintentar.
            _state.value = _state.value.copy(draft = next, dirty = true, machineState = WizChatMachineState.AwaitingAnswer,
                isSubmittingAnswer = false, isSavingAndExiting = false,
                selectionStale = selectionStaleFor(next))
        }
    }
    /**
     * Serialized write: applies [change] to the current draft and persists with
     * a strictly monotonic revision. It NEVER moves the cursor: confirmation
     * and advance belong to [submitCurrentStep] alone.
     *
     * Declaración explícita: una escritura de usuario con `step != null`
     * declara el paso aunque el valor no cambie (p. ej. confirmar el peso por
     * defecto 70 kg con la regla); si ya estaba declarado y el valor no varía,
     * no se persiste nada (sin revisión inútil).
     */
    private fun mutateDraft(step: SetupStepId? = null, change: (SetupWizardDraft) -> SetupWizardDraft) {
        // Diagnóstico de caídas silenciosas (AC-T005-03): si una escritura no
        // llega a publicarse, el último estado explica si ni siquiera se
        // encoló, si esperó el mutex, si la puerta la descartó o si se aplicó.
        mutateDiagnostic = "launched:${step ?: "-"}"
        viewModelScope.launch {
            commandMutex.withLock {
                if (!initialized || _state.value.isCommitting || _state.value.isSubmittingAnswer || _state.value.isSavingAndExiting ||
                    _state.value.machineState == WizChatMachineState.Committed ||
                    _state.value.machineState == WizChatMachineState.UnsupportedDraft
                ) {
                    mutateDiagnostic = "dropped:${step ?: "-"} init=$initialized submitting=${_state.value.isSubmittingAnswer}" +
                        " saving=${_state.value.isSavingAndExiting} machine=${_state.value.machineState}"
                    return@withLock
                }
                mutateDiagnostic = "applied:${step ?: "-"}"
                val old = _state.value.draft
                val changed = change(old)
                val alreadyDeclared = step != null && step in old.declaredSteps
                val marked = if (step != null && (changed != old || !alreadyDeclared)) changed.touchStep(step) else changed
                if (marked != old) persistAndPublish(marked.copy(revision = old.revision + 1))
            }
        }
    }

    /** Última escritura observada por [mutateDraft]; solo diagnóstico de pruebas. */
    internal fun lastMutateDiagnostic(): String = mutateDiagnostic
    private suspend fun persistDraft(draft: SetupWizardDraft): Boolean = try {
        persistence.save(draft.draftId, json.encodeToString(draft), draft.revision.toLong(), PersonalizedPlanCatalog.REVISION)
        savedStateHandle[DRAFT_ID_KEY] = draft.draftId
        true
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: Exception) {
        // Fracaso de guardado: error inline (dismissible), nunca un modal; el
        // estado de máquina lo decide quien llama (AwaitingAnswer + inicializado).
        _state.value = _state.value.copy(errors = mapOf("save" to "No pude guardar esta respuesta. Tu información sigue aquí; inténtalo de nuevo."), lastFailure = error.message)
        false
    }
    private fun publishDraft(draft: SetupWizardDraft, dirty: Boolean, machine: WizChatMachineState) {
        // Éxito de escritura: se limpia el último fallo, salvo el diagnóstico
        // de RINGS, que solo lo retira su propio cálculo cuando sale bien (el
        // gate de RINGS debe poder seguir mostrando la causa real).
        val ringsDiagnosis = _state.value.lastFailure
            ?.takeIf { it.startsWith(RINGS_FAILURE_PREFIX) }
        val current = _state.value
        // H3: si la selección pasó a un plan viable que no estaba entre las tarjetas visibles (una escritura del
        // borrador, la biblioteca…), se amplía lo visible hasta incluirlo; sin lista calculada no hay nada que ampliar.
        val visibleCards = if (current.availablePlanCandidates.isEmpty()) {
            current.planCandidates
        } else {
            visibleCandidatesFor(
                available = current.availablePlanCandidates,
                selectedId = draft.selectedCatalogId,
                minimumVisible = maxOf(current.planCandidates.size, INITIAL_VISIBLE_CANDIDATES),
            )
        }
        _state.value = current.copy(draft = draft, dirty = dirty, isLoading = false, machineState = machine,
            planCandidates = visibleCards,
            errors = emptyMap(), previewError = null, lastFailure = ringsDiagnosis, isSubmittingAnswer = false,
            // §15.2: la selección guardada queda obsoleta en cuanto las
            // respuestas dejan de coincidir con el preview preparado; el
            // preview nuevo la vuelve a poner vigente.
            selectionStale = selectionStaleFor(draft))
    }

    /**
     * §15.2: `selectedCatalogId` conserva la INTENCIÓN; el preview deja de estar
     * vigente cuando la huella actual no es la del último preview publicado.
     * La activación usa exactamente el mismo gate, así que nunca se activa un
     * resultado de otras respuestas.
     */
    private fun selectionStaleFor(draft: SetupWizardDraft): Boolean =
        draft.selectedCatalogId != null &&
            draft.includeTraining &&
            draft.programRoute != SetupProgramRoute.LATER &&
            lastSuccessfulTrainingKey != previewKey(draft)

    private val trainingPreviewKinds = setOf(
        SetupPreviewKind.EXERCISES, SetupPreviewKind.LOADS, SetupPreviewKind.WARMUPS,
        SetupPreviewKind.PLAN_CANDIDATES, SetupPreviewKind.SPLIT, SetupPreviewKind.RECIPE, SetupPreviewKind.MARKS,
    )
    private val nutritionPreviewKinds = setOf(
        SetupPreviewKind.EER, SetupPreviewKind.MACROS, SetupPreviewKind.EXPENDITURE,
        SetupPreviewKind.NUTRITION_REFERENCES, SetupPreviewKind.NUTRITION_DISTRIBUTION,
    )
    private val ringsPreviewKinds = setOf(SetupPreviewKind.RINGS_BATTERIES)

    /** Once a preview is recomputed its stale flag is dropped; results whose footprint no longer matches are discarded. */
    private fun clearStalePreviews(kinds: Set<SetupPreviewKind>) {
        val current = _state.value.draft.stepProgress.stalePreviews
        if (kinds.none { it in current }) return
        _state.value = _state.value.copy(draft = _state.value.draft.let { draft ->
            draft.copy(stepProgress = draft.stepProgress.previewsComputed(kinds))
        })
    }

    /**
     * Fingerprint of everything the training engines consume: options, split,
     * marks and VITALS. Any change makes the cached preview stale so the next
     * computation runs against the real inputs.
     */
    private fun trainingKey(draft: SetupWizardDraft): List<Any?> = listOf(
        draft.commitId, draft.includeTraining, draft.programRoute, draft.trainingPath, draft.goal, draft.focus,
        draft.experience, draft.daysPerWeek, draft.selectedWeekdays, draft.minutesPerSession, draft.equipment,
        draft.cardioType, draft.cardioNoPreference, draft.cardioMinutes,
        draft.ageYears, draft.heightCm, draft.weightKg, draft.profileGender,
        draft.trainingOptions,
        // Inventario declarado (P0): su cambio invalida candidatos y preview de
        // programa, aunque el resto de opciones no varíen.
        draft.trainingOptions.inventory,
        // §15.3: material completo (categorías + aparatos/soportes del panel
        // §13.2), revisiones reales de catálogo y todo lo que consume el motor.
        draft.trainingOptions.availability,
        draft.trainingEnvironment, draft.knowsTrainingMarks,
        draft.volumeAnswers, draft.volumeRecommendations, draft.priorityMuscles, draft.lowerEmphasisMuscles,
        draft.selectedSplitId, draft.customSplitPattern, draft.customSplitName,
        draft.selectedCatalogId, draft.sessions, draft.powerliftingProfile, draft.catalogRevision,
        // Entreno v2: todo dato nuevo que el motor (o el generador) pueda leer invalida candidatos y vista previa.
        draft.trainingPlaces, draft.goalProfile, draft.freshestDay, draft.weekStartDay, draft.dayPlaces,
        draft.capabilities, draft.liftMarks,
        // «Otra versión»: la semilla cambia el programa «a medida» (vuelve a barrer), nunca la lista de autores.
        draft.planVariantSeed,
        PersonalizedPlanCatalog.REVISION,
    )

    /** Candidate evaluation is independent of which candidate card is selected. */
    private fun candidateSetKey(draft: SetupWizardDraft): List<Any?> =
        trainingKey(draft.copy(selectedCatalogId = null))

    /**
     * T-001 / AC-T001-03: un resultado de candidatos sólo se publica si sus
     * entradas siguen siendo las vigentes. Es la MISMA comparación que ya usaba
     * el job dentro de [updateCandidates]; queda como función interna para que
     * una prueba determinista demuestre que la clave obsoleta se rechaza, sin
     * depender de carreras ni de tiempos.
     */
    internal fun isCurrentCandidateKey(draft: SetupWizardDraft): Boolean =
        candidateSetKey(_state.value.draft) == candidateSetKey(draft)

    /**
     * T-001 / AC-T001-02: causa CONCISA de un rechazo. Conserva la clase y el
     * mensaje de la excepción —lo único que explica de verdad el fallo— sin
     * datos personales y sin volcar el borrador entero.
     */
    private fun failureDetail(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        val type = error::class.java.name
        return if (message.isEmpty()) type else "$type: $message"
    }

    /** Rechazo estructurado de un candidato: etapa tipada si la hubo, si no MATERIALIZACIÓN. */
    @Suppress("unused")
    private fun rejectionOf(planId: String, error: Exception): SetupCandidateRejection =
        SetupCandidateRejection(
            planId = planId,
            stage = (error as? SetupCandidateFailureException)?.stage ?: SetupCandidateRejectionStage.MATERIALIZATION,
            reason = failureDetail(error),
        )

    /**
     * Etapa de un `unavailable(...)` del personalizador: su mensaje ya describe
     * la causa real (frecuencia, equipo, tiempo, volumen…). Aquí sólo se asigna
     * a la etapa correcta para que la UI no lo presente todo como «falta
     * material» (AC-T001-02); si no hay señal clara, MATERIALIZACIÓN.
     */
    private fun stageForUnavailable(message: String): SetupCandidateRejectionStage {
        val text = message.lowercase()
        return when {
            "catálogo" in text || "catalogo" in text -> SetupCandidateRejectionStage.CATALOG
            "frecuencia" in text -> SetupCandidateRejectionStage.FREQUENCY
            "material" in text || "equipo" in text || "aparato" in text || "enfoque" in text ->
                SetupCandidateRejectionStage.MATERIAL
            "minut" in text || "tiempo" in text -> SetupCandidateRejectionStage.DURATION
            "experiencia" in text || "nivel" in text -> SetupCandidateRejectionStage.PROFILE
            "volumen" in text -> SetupCandidateRejectionStage.COMPOSITION
            else -> SetupCandidateRejectionStage.MATERIALIZATION
        }
    }

    /** Escaneo de UN pase de candidatos (T-001): publicados, viables y rechazos estructurados. */
    private data class CandidateScan(
        val published: List<CatalogEntry>,
        val viable: List<CatalogEntry>,
        val rejections: List<SetupCandidateRejection>,
        /** El `Ready` de cada viable (programa preparado): de él sale el revelado. */
        val ready: Map<String, PlanCandidateEvaluation.Ready> = emptyMap(),
    ) {
        /** §15.2: evaluados = viables + no viables; NO «publicados» filtrados. */
        val counts: SetupCandidateCounts
            get() = SetupCandidateCounts(
                evaluated = viable.size + rejections.size,
                viable = viable.size,
                nonViable = rejections.size,
            )

        /** Entreno v2: el «a medida» delante y, detrás, los del planificador (sin repetir ninguno). */
        operator fun plus(other: CandidateScan): CandidateScan = CandidateScan(
            published = (published + other.published).distinctBy { it.id },
            viable = (viable + other.viable).distinctBy { it.id },
            rejections = rejections + other.rejections,
            ready = other.ready + ready,
        )

        companion object {
            val EMPTY = CandidateScan(emptyList(), emptyList(), emptyList())
        }
    }

    /**
     * Resultado del cálculo de candidatos: el pase PEDIDO ([requested], el único que explica los rechazos y
     * los conteos que se publican), el pase que se MUESTRA ([scan]: el pedido o, si se activó, el de peso
     * corporal) y si se usó el segundo pase adaptado (Paquete A · D4, B-07). [cards] y [reveals] son las tarjetas y
     * el revelado de los viables mostrados, ya calculados fuera del hilo principal.
     */
    private data class CandidateOutcome(
        val requested: CandidateScan,
        val scan: CandidateScan,
        val useAdapted: Boolean,
        val cards: List<SetupPlanCandidate> = emptyList(),
        val reveals: List<SetupPlanReveal> = emptyList(),
    )

    /**
     * C4: contadores del barrido de candidatos. SOLO medición: no influyen en el
     * resultado ni en la caché. Se actualizan desde Dispatchers.Default.
     */
    private class CandidateSweepStats {
        val evaluated = java.util.concurrent.atomic.AtomicInteger()
        val cacheHits = java.util.concurrent.atomic.AtomicInteger()
        val passes = java.util.concurrent.atomic.AtomicInteger()
    }

    /** Barridos de candidatos lanzados por este ViewModel (el primero suele ser en frío). */
    @Volatile private var candidateSweepsStarted = 0

    /**
     * Catalog is loaded lazily, only when a preview or candidate computation requires it.
     *
     * Paquete A · D3 (B-06): `catalogLoaded` pasa a true SOLO cuando, tras `load()`, el repositorio está en
     * Ready. `load()` no lanza si el catálogo falla (publica `Error` en su estado), y marcarlo como cargado
     * igualmente dejaba el barrido sin revisión de catálogo —todo `CatalogLoading`, ningún rechazo— y hacía
     * que «Reintentar» no volviera a cargar nunca. Ahora un catálogo que no quedó listo lanza un fallo
     * TIPADO ([PlanRejectionReason.CATALOG_NOT_READY], texto llano) y deja `catalogLoaded` en false, así
     * que el siguiente intento vuelve a leerlo. Cada llamador ya captura la excepción: el barrido publica un
     * rechazo CATALOG visible, el preview su `previewError` y la búsqueda de ejercicios su aviso.
     */
    private suspend fun ensureCatalogLoaded() {
        if (catalogLoaded) return
        // §15.3: lectura de asset SIEMPRE en IO; la materialización corre en Default.
        withContext(Dispatchers.IO) {
            catalogLoadMutex.withLock {
                if (!catalogLoaded) {
                    catalogRepository.load()
                    val loaded = catalogRepository.state.value
                    if (loaded !is ExerciseCatalogStateV2.Ready) {
                        // Solo la clase y el código del estado: sin datos personales, como el resto del diagnóstico.
                        val detail = (loaded as? ExerciseCatalogStateV2.Error)?.reason ?: "sin resultado"
                        Log.w(DIAG_TAG, "catálogo de ejercicios no listo tras load(): ${loaded::class.java.simpleName} ($detail)")
                        throw PlanMaterializationException(
                            PlanEvaluationStage.CATALOG,
                            PlanRejectionReason.CATALOG_NOT_READY,
                            CATALOG_UNAVAILABLE_MESSAGE,
                        )
                    }
                    catalogLoaded = true
                }
            }
        }
    }

    /**
     * Propiedad de generación/job del preview: cada lanzamiento (o liberación
     * de caché) incrementa [previewGeneration] y toma la PROPIEDAD de
     * `isPreviewLoading`/`preparingTrainingKey`.
     *
     * Reglas:
     *  - el dedup sólo vale con un job **activo** calculando la misma clave;
     *  - un cache-hit **libera** el cálculo intermedio (cancela y apaga el
     *    loading): es la carrera A→B→A que dejaba `isPreviewLoading=true` eterno;
     *  - sólo el job **dueño** publica o limpia — un job viejo (cancelado o
     *    tardío) no escribe nada —;
     *  - una cancelación nunca publica error.
     */
    private fun previewKey(draft: SetupWizardDraft): List<Any?> =
        // La semana armada (colocación de sesiones, reparto adaptado, otra versión) cambia el programa preparado pero
        // no el conjunto de candidatos: solo entra en la clave de la vista previa.
        trainingKey(draft) + listOf(
            _state.value.planAdaptedToBodyweight, draft.weekLayoutOverrides, draft.adaptedSplitId,
        )

    // La bicicleta es de la persona, no del material: el segundo pase a peso corporal también la conserva.
    private fun bodyweightAdapted(draft: SetupWizardDraft): SetupWizardDraft = draft.copy(
        equipment = setOf(SetupEquipment.BODYWEIGHT),
        trainingOptions = draft.trainingOptions.copy(
            availability = SetupApparatusPanel.withBikeOf(draft.trainingOptions.availability, EquipmentAvailability()),
        ),
    )

    private fun preparePreview(draft: SetupWizardDraft) {
        prepareRingsPreview(draft)
        // «Lo haré más adelante» y el alta sin entreno no tienen programa. Se retira
        // cualquier vista previa anterior aunque la lista de planes siga calculándose.
        if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) {
            previewJob?.cancel()
            previewJob = null
            lastSuccessfulTrainingKey = null
            preparingTrainingKey = null
            _state.value = _state.value.copy(
                programPreview = null,
                previewReport = null,
                programSessionMinutes = null,
                fixedTrainingDays = null,
                isPreviewLoading = false,
                previewError = null,
                weekLayout = null,
            )
            updateNutritionPreview(draft)
            return
        }
        // Paquete A · D2 (B-01): una selección que ya no está entre los viables no tiene programa que
        // preparar. Mientras siga marcada como caída ([SetupWizardState.droppedSelection]) ninguna vía
        // (guardado de una respuesta, confirmación, reintento) relanza su preview: sería el error del plan
        // que ya no encaja pegado a la lista. Elegir otro plan o una búsqueda nueva retiran la marca.
        val dropped = _state.value.droppedSelection
        if (dropped != null && dropped.planId == draft.selectedCatalogId) {
            updateNutritionPreview(draft)
            return
        }
        // Mientras se recalculan candidatos, el pase adaptado todavía no está
        // decidido. Materializar aquí mostraría un error de equipo que el
        // segundo pase puede resolver. El job de candidatos relanza la vista.
        if (_state.value.isCandidateLoading) {
            // Tracking-only has no plan/calendar dependency, so its empty
            // preparation can be published independently of candidate work.
            if (isTrackingOnly(draft)) updateNutritionPreview(draft)
            return
        }
        val key = previewKey(draft)
        val jobActive = previewJob?.isActive == true
        if (jobActive && preparingTrainingKey == key && _state.value.isPreviewLoading) {
            updateNutritionPreview(draft)
            return
        }
        if (lastSuccessfulTrainingKey == key && _state.value.programPreview != null && _state.value.previewError == null) {
            // Caché válida: se recupera el resultado Y se retira la rama
            // intermedia que aún estuviera calculando.
            releasePreviewGeneration(cancelInFlight = true)
            clearStalePreviews(trainingPreviewKinds)
            updateNutritionPreview(draft)
            return
        }
        val generation = previewGeneration + 1
        previewGeneration = generation
        previewJob?.cancel()
        preparingTrainingKey = key
        _state.value = _state.value.copy(isPreviewLoading = true)
        previewJob = viewModelScope.launch { runPreview(generation, key, draft) }
    }

    /**
     * Retira la propiedad del cálculo en vuelo: invalida la generación, cancela
     * si hace falta y apaga el loading. Sólo la invoca el NUEVO dueño
     * (cache-hit / nueva generación) o [initialize].
     */
    private fun releasePreviewGeneration(cancelInFlight: Boolean) {
        previewGeneration += 1
        if (cancelInFlight) previewJob?.cancel()
        previewJob = null
        preparingTrainingKey = null
        retirePreviewLoading()
    }

    private fun ownsPreview(generation: Long): Boolean = generation == previewGeneration

    /** Sólo el dueño retira su señal de carga, sin publicar ningún resultado. */
    private fun retirePreviewLoading() {
        preparingTrainingKey = null
        val current = _state.value
        if (current.isPreviewLoading || current.machineState == WizChatMachineState.PreparingPreview) {
            _state.value = current.copy(
                isPreviewLoading = false,
                machineState = if (current.machineState == WizChatMachineState.PreparingPreview) {
                    stateForCurrentStep()
                } else {
                    current.machineState
                },
            )
        }
    }

    /**
     * Lo que publica la vista previa: el programa ya con la semana armada ([preview]), el mismo ANTES de ella ([base], de
     * él cuelgan las decisiones del tablero) y el resultado de aplicarla ([layout]).
     */
    private data class PreparedPreview(val preview: SetupPreview, val base: Program?, val layout: LayoutOutcome?)

    /**
     * Materialización de la vista previa: el programa preparado del plan elegido ([materializeBase]) y, al final, la
     * semana armada del borrador por el ÚNICO punto que la aplica ([applyLayout]). La activación guarda este mismo
     * programa (`programPreview`), así lo que se ve es lo que se activa. La semana armada no entra en la evaluación de
     * candidatos (su caché no depende de dónde caiga cada sesión): mover una sesión re-arma solo la vista previa.
     *
     * Con dos o más lugares el mismo punto pone al día el lugar de cada sesión según el material de su día
     * ([placeFitFor]), con o sin sesiones movidas: así el programa que se previsualiza y se activa nunca trae una sesión
     * que no se pueda hacer en su día sin que la semana lo avise ([LayoutOutcome.placeConflicts]).
     */
    private suspend fun materialize(draft: SetupWizardDraft): PreparedPreview {
        val base = materializeBase(draft)
        val program = base.program ?: return PreparedPreview(base, null, null)
        val placeFit = placeFitFor(draft)
        if (!draft.hasWeekLayout && placeFit == null) return PreparedPreview(base, program, LayoutOutcome(program))
        val resolver = if (draft.adaptedSplitId != null) traitResolver() else null
        val outcome = applyLayout(draft, program, resolver, placeFit)
        ProgramExecutionContract.requireExecutable(outcome.program)
        return PreparedPreview(SetupPreview(outcome.program, base.report), program, outcome)
    }

    /**
     * El contraste de las sesiones con el material de cada lugar ([SessionPlaceFit]); null con un solo lugar (no hay a
     * qué contrastar) o sin catálogo de ejercicios cargado (no se puede saber qué pide cada ejercicio).
     */
    private fun placeFitFor(draft: SetupWizardDraft): SessionPlaceFit? {
        if (draft.trainingPlaces.size < 2) return null
        val catalog = (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog ?: return null
        return SessionPlaceFit.of(catalog, draft.trainingOptions.availability, draft.trainingPlaces)
    }

    /**
     * Materialización real, salvo el puerto inyectado por las tests. El catálogo
     * es un PREREQUISITO del motor real: con override las tests controlan el
     * materializado completo sin cargar assets.
     */
    private suspend fun materializeBase(draft: SetupWizardDraft): SetupPreview {
        cachedReadyPreview(draft)?.let { return it }
        val override = materializeOverride
        if (override != null) return override.materialize(draft)
        // A manual plan does not use the catalog evaluator. Every catalog plan,
        // including a cache miss or a restored selection, must pass through the
        // same revision-matched evaluator before its preview can be published.
        if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) return materializeProgram(draft)
        ensureCatalogLoaded()
        return evaluateSelectedCandidate(draft)
    }

    /**
     * La semana del tablero (paso WEEK_LAYOUT) para la vista previa [prepared]. Sin catálogo de ejercicios cargado no se
     * puede describir el foco de cada sesión, pero la semana se dibuja igual (sin foco): nunca tumba la vista previa.
     */
    private fun weekLayoutFor(draft: SetupWizardDraft, prepared: PreparedPreview): SetupWeekLayout? {
        val program = prepared.preview.program ?: return null
        if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) return null
        return weekLayoutOf(
            draft = draft,
            program = program,
            base = prepared.base ?: program,
            resolver = loadedTraitResolver() ?: ExerciseTraitResolver { null },
            outcome = prepared.layout ?: LayoutOutcome(program),
        )
    }

    /** Resolutor de rasgos de ejercicio del redistribuidor, uno por revisión del catálogo cargado (construirlo indexa todo). */
    @Volatile private var traitResolverCache: Pair<String, ExerciseTraitResolver>? = null

    /** El resolutor del catálogo ya cargado, o null si el catálogo todavía no está (nunca lo carga). */
    private fun loadedTraitResolver(): ExerciseTraitResolver? {
        val catalog = (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog ?: return null
        traitResolverCache?.takeIf { it.first == catalog.catalogRevision }?.let { return it.second }
        return CatalogExerciseTraitResolver(catalog).also { traitResolverCache = catalog.catalogRevision to it }
    }

    /** El resolutor del catálogo, cargándolo si hace falta (lo necesita la adaptación a un reparto). */
    private suspend fun traitResolver(): ExerciseTraitResolver? {
        ensureCatalogLoaded()
        return loadedTraitResolver()
    }

    /**
     * Evaluation engine used by both the candidate scan and a selected-plan
     * cache miss. Deliberately bypasses the Ready lookup to avoid recursive
     * evaluation; its result is always returned through PlanCandidateEvaluator.
     */
    private suspend fun materializeForEvaluation(draft: SetupWizardDraft): SetupPreview {
        materializeOverride?.let { return it.materialize(draft) }
        ensureCatalogLoaded()
        return materializeProgram(draft)
    }

    /** Evaluates one selected catalog plan and returns that exact prepared Ready. */
    private suspend fun evaluateSelectedCandidate(draft: SetupWizardDraft): SetupPreview {
        val planId = draft.selectedCatalogId ?: error("Selecciona un plan")
        val exerciseRevision = exerciseCatalogRevision()
            ?: throw PlanMaterializationException(
                PlanEvaluationStage.CATALOG,
                PlanRejectionReason.CATALOG_NOT_READY,
                "El catálogo de ejercicios todavía no está disponible",
            )
        val equipment = effectiveEquipmentIds(draft)
        val request = candidateRequest(draft, equipment, exerciseRevision)
        val cacheRevision = "${PersonalizedPlanCatalog.REVISION}|$exerciseRevision"
        val cacheKey = "${request.inputKey}|$planId"

        fun preview(ready: PlanCandidateEvaluation.Ready): SetupPreview {
            check(ready.planId == planId && ready.inputKey == request.inputKey) {
                "La evaluación preparada no corresponde al plan y las respuestas actuales"
            }
            return SetupPreview(ready.preparedPlan, ready.report)
        }

        when (val cached = candidateCache.get(cacheRevision, cacheKey)) {
            is PlanCandidateEvaluation.Ready -> return preview(cached)
            is PlanCandidateEvaluation.Rejected -> throw PlanMaterializationException(
                cached.stage, cached.reasonCode,
                cached.details ?: cached.reasonCode.name,
                cached.affectedSlots, cached.requiredMinutes,
                missingRequirements = cached.missingRequirements,
            )
            PlanCandidateEvaluation.CatalogLoading, null -> Unit
        }

        val snapshot = PlanCatalogSnapshot(
            entries = PersonalizedPlanCatalog.entries(),
            planRevision = PersonalizedPlanCatalog.REVISION,
            exerciseCatalogRevision = exerciseRevision,
        )
        val evaluation = PlanCandidateEvaluator.evaluate(
            request = evaluationRequestFor(request, planId),
            snapshot = snapshot,
            entryId = planId,
            engine = candidateEngine(draft, request),
        )
        return when (evaluation) {
            is PlanCandidateEvaluation.Ready -> {
                candidateCache.put(cacheRevision, cacheKey, evaluation)
                preview(evaluation)
            }
            is PlanCandidateEvaluation.Rejected -> {
                candidateCache.put(cacheRevision, cacheKey, evaluation)
                throw PlanMaterializationException(
                    evaluation.stage, evaluation.reasonCode,
                    evaluation.details ?: evaluation.reasonCode.name,
                    evaluation.affectedSlots, evaluation.requiredMinutes,
                    missingRequirements = evaluation.missingRequirements,
                )
            }
            PlanCandidateEvaluation.CatalogLoading -> throw PlanMaterializationException(
                PlanEvaluationStage.CATALOG,
                PlanRejectionReason.CATALOG_NOT_READY,
                "El catálogo cambió durante la preparación; vuelve a intentarlo",
            )
        }
    }

    /** The evaluation cache owns the prepared program/report for this exact candidate. */
    private fun cachedReadyPreview(draft: SetupWizardDraft): SetupPreview? {
        if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) return null
        val planId = draft.selectedCatalogId ?: return null
        val exerciseRevision = exerciseCatalogRevision() ?: return null
        val equipment = effectiveEquipmentIds(draft)
        val inputKey = candidateInputKey(draft, equipment, exerciseRevision)
        val cacheRevision = "${PersonalizedPlanCatalog.REVISION}|$exerciseRevision"
        val ready = candidateCache.get(cacheRevision, "$inputKey|$planId")
            as? PlanCandidateEvaluation.Ready
            ?: return null
        if (ready.planId != planId || ready.inputKey != inputKey) return null
        return SetupPreview(ready.preparedPlan, ready.report)
    }

    /**
     * El `Ready` que el barrido vigente dejó en la caché para [planId], o null. La clave se reconstruye como la
     * armó `collectViable`: el pase pedido evalúa con el material declarado y el pase a peso corporal
     * ([SetupWizardState.planAdaptedToBodyweight]) con el borrador adaptado, que es el que lleva su `Ready`.
     * Una respuesta que cambió desde el barrido cambia la clave, y entonces no hay `Ready` que enseñar.
     */
    private fun readyCandidateFor(planId: String): PlanCandidateEvaluation.Ready? {
        val current = _state.value
        val draft = current.draft
        if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) return null
        val exerciseRevision = exerciseCatalogRevision() ?: return null
        val equipment = if (current.planAdaptedToBodyweight) setOf("bodyweight") else effectiveEquipmentIds(draft)
        val source = if (equipment == setOf("bodyweight")) bodyweightAdapted(draft) else draft
        val inputKey = candidateInputKey(source, equipment, exerciseRevision)
        val cacheRevision = "${PersonalizedPlanCatalog.REVISION}|$exerciseRevision"
        val ready = candidateCache.get(cacheRevision, "$inputKey|$planId") as? PlanCandidateEvaluation.Ready
            ?: return null
        return ready.takeIf { it.planId == planId && it.inputKey == inputKey }
    }

    private suspend fun runPreview(generation: Long, key: List<Any?>, draft: SetupWizardDraft) {
        if (!initialized) {
            if (ownsPreview(generation)) {
                preparingTrainingKey = null
                _state.value = _state.value.copy(isPreviewLoading = false)
            }
            return
        }
        val withoutPreview = !draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER ||
            previewInputsIncomplete(draft)
        if (withoutPreview) {
            if (ownsPreview(generation)) {
                lastSuccessfulTrainingKey = null
                preparingTrainingKey = null
                _state.value = _state.value.copy(
                    programPreview = null, previewReport = null,
                    programSessionMinutes = null, fixedTrainingDays = null,
                    isPreviewLoading = false,
                    weekLayout = null,
                )
                updateNutritionPreview(draft)
            }
            return
        }
        if (ownsPreview(generation)) {
            val machine = _state.value.machineState
            // §15.3 / AC-T005-03: el preview NUNCA pisa un persist en vuelo.
            // `PersistingAnswer` es la señal de que la escritura aún no se ha
            // publicado; pisarla dejaba la puerta «en reposo» abierta antes de
            // tiempo y un submit leía un borrador anterior al de la escritura.
            val previewOwnsMachine = machine == WizChatMachineState.AwaitingAnswer ||
                machine == WizChatMachineState.Reviewing ||
                machine == WizChatMachineState.PreparingPreview
            _state.value = _state.value.copy(
                machineState = if (previewOwnsMachine) WizChatMachineState.PreparingPreview else machine,
                isPreviewLoading = true, previewError = null,
            )
        }
        try {
            val source = if (_state.value.planAdaptedToBodyweight) bodyweightAdapted(draft) else draft
            val prepared = withContext(Dispatchers.Default) { materialize(source) }
            val result = prepared.preview
            val layout = withContext(Dispatchers.Default) { weekLayoutFor(draft, prepared) }
            when {
                // Dueño + clave vigente: publica y se retira.
                ownsPreview(generation) && previewKey(_state.value.draft) == key -> {
                    lastSuccessfulTrainingKey = key
                    preparingTrainingKey = null
                    clearStalePreviews(trainingPreviewKinds)
                    val isFixed = isFixedRecipe(draft.selectedCatalogId)
                    // Los minutos de la revisión: la sesión más larga del programa YA ARMADO, con el estimador común.
                    val minutes = longestSessionMinutes(result.program)
                    val machine = _state.value.machineState
                    val restoredMachine = if (machine == WizChatMachineState.PreparingPreview) {
                        if (_state.value.draft.wizChat.currentQuestionId == WizChatQuestionId.REVIEW) {
                            WizChatMachineState.Reviewing
                        } else {
                            WizChatMachineState.AwaitingAnswer
                        }
                    } else {
                        // Un persist en vuelo mantiene su señal: su propio
                        // publishDraft es quien la retira al terminar.
                        machine
                    }
                    _state.value = _state.value.copy(programPreview = result.program, previewReport = result.report, programSessionMinutes = minutes,
                        fixedTrainingDays = if (isFixed) result.program?.let(::fixedTrainingDays) else null,
                        isPreviewLoading = false, machineState = restoredMachine,
                        selectionStale = selectionStaleFor(_state.value.draft),
                        weekLayout = layout,
                        requiresActivationConfirmation = activationConfirmation(draft, result.program))
                    updateNutritionPreview(_state.value.draft)
                }
                // Dueño con clave ya vieja: sólo retira su carga; el resultado
                // se descarta (nunca se publica un preview que ya no corresponde).
                ownsPreview(generation) -> retirePreviewLoading()
            }
            // No dueño: manda el job actual; este job no escribe nada.
        } catch (cancel: CancellationException) {
            // Cancelación: sin error y sin tocar el estado del job dueño.
            throw cancel
        } catch (error: Throwable) {
            if (ownsPreview(generation)) {
                preparingTrainingKey = null
                if (previewKey(_state.value.draft) == key) {
                    val machine = _state.value.machineState
                    // H10: el texto crudo del motor va solo al registro; la persona lee un texto llano (catálogo o fallo
                    // interno por el presentador único) y los rechazos que sí puede arreglar conservan su mensaje.
                    val shown = previewFailureText(error, _state.value.draft)
                    Log.w(DIAG_TAG, "el preview falló: ${failureDetail(error).take(DIAG_REASON_MAX)}")
                    _state.value = _state.value.copy(isPreviewLoading = false,
                        machineState = if (machine == WizChatMachineState.PreparingPreview) stateForCurrentStep() else machine,
                        previewError = shown, errors = _state.value.errors + ("preview" to shown), lastFailure = error.message)
                } else {
                    retirePreviewLoading()
                }
            }
        }
    }
    private fun firstWeekSessions(program: Program): List<Session> = program.macrocycles.firstOrNull()?.blocks?.firstOrNull()
        ?.mesocycles?.firstOrNull()?.weeks?.firstOrNull()?.sessions.orEmpty()

    private fun fixedTrainingDays(program: Program): Set<Int> = program.resolvedSchedulePlan().trainingDays
        .ifEmpty { firstWeekSessions(program).mapNotNull { it.dayOfWeek }.toSet() }

    private fun ringsKey(draft: SetupWizardDraft): List<Any?> = listOf(draft.ringsAnswers,
        draft.manualMuscleOverrides, draft.manualEnergyOverride, draft.manualStructureOverride)

    private fun prepareRingsPreview(draft: SetupWizardDraft) {
        // Un solo reloj para el mapeo y el cálculo: el check-in previewado y
        // el que se guardaría en el commit comparten fecha.
        val now = System.currentTimeMillis()
        val mapping = ringsMapping(draft, now)
        val key = ringsKey(draft)
        if (!mapping.savesRealCheckIn) {
            ringsPreviewJob?.cancel()
            // Sin check-in real no hay baterías NI cobertura: nunca se muestra
            // una cobertura calculada sobre datos que no existen.
            lastRingsPreviewKey = null
            ringsPreviewNow = null
            _state.value = _state.value.copy(ringsBatteriesPreview = null, ringsCoveragePreview = null,
                ringsPreviewLoading = false, ringsPreviewError = null,
                errors = _state.value.errors - "rings_preview",
                // Tampoco queda un diagnóstico de RINGS que ya no aplica.
                lastFailure = _state.value.lastFailure
                    ?.takeUnless { it.startsWith(RINGS_FAILURE_PREFIX) })
            // Baterías, cobertura y marcas obsoletas se limpian juntas.
            clearStalePreviews(ringsPreviewKinds)
            return
        }
        if (lastRingsPreviewKey == key && _state.value.ringsBatteriesPreview != null) return
        ringsPreviewJob?.cancel()
        _state.value = _state.value.copy(ringsPreviewLoading = true, ringsPreviewError = null,
            errors = _state.value.errors - "rings_preview")
        ringsPreviewJob = viewModelScope.launch {
            try {
                val result = calculateRingsPreview(draft, mapping, now)
                if (ringsKey(_state.value.draft) == key) {
                    lastRingsPreviewKey = key
                    // El reloj inyectado se conserva: el commit reutiliza esta
                    // MISMA fecha para el check-in que se guardará.
                    ringsPreviewNow = now
                    // Éxito: se retira SOLO el diagnóstico de RINGS; los fallos
                    // de guardado/carga que sigan abiertos no se enmascaran.
                    val stillFailed = _state.value.lastFailure
                        ?.takeUnless { it.startsWith(RINGS_FAILURE_PREFIX) }
                    // Baterías y cobertura se publican JUNTAS: la cobertura sale
                    // del mismo cálculo, nunca de una etiqueta global derivada
                    // de las baterías (un canal desconocido no es cobertura).
                    _state.value = _state.value.copy(
                        ringsBatteriesPreview = result.batteries,
                        ringsCoveragePreview = result.coverage,
                        ringsPreviewLoading = false,
                        lastFailure = stillFailed,
                    )
                    clearStalePreviews(ringsPreviewKinds)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Throwable) {
                // Diagnóstico honesto: la CAUSA REAL (clase + mensaje) queda en
                // `lastFailure` —nunca un genérico— y se registra en logcat con
                // la traza. Solo se manda la excepción: ni borrador, ni
                // respuestas, ni ajustes (sin datos personales). Sin cancelación
                // encima, y sin inventar ningún preview en su lugar.
                val cause = "${error::class.java.simpleName}: ${error.message ?: "sin detalle"}"
                Log.e(DIAG_TAG, "Rings preview falló → $cause", error)
                if (ringsKey(_state.value.draft) == key) _state.value = _state.value.copy(
                    ringsPreviewLoading = false,
                    ringsPreviewError = "No pude preparar tus RINGS. Reintenta desde esta pregunta.",
                    errors = _state.value.errors + ("rings_preview" to "No pude preparar tus RINGS. Reintenta desde esta pregunta."),
                    lastFailure = "$RINGS_FAILURE_PREFIX · $cause",
                )
            }
        }
    }

    private suspend fun calculateRingsPreview(
        draft: SetupWizardDraft,
        mapping: SetupRingsMapping,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SetupRingsPreview {
        val a = requireNotNull(draft.ringsAnswers)
        // Mismo alcance que usaba la evidencia (perMuscleScores), sin depender de ella.
        val allowed = when (a.muscleScope) {
            InitialRecoveryMuscleScope.FULL_BODY -> InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS.toSet()
            InitialRecoveryMuscleScope.SELECTED -> a.recentMuscles
            InitialRecoveryMuscleScope.UNKNOWN -> mapping.evidence?.perMuscleScores?.keys ?: emptySet()
        }
        val selected = draft.manualMuscleOverrides.filterKeys { it in allowed }
        val discomforts = SetupRingsResponseMapping.discomfortField(mapping.discomfortResponse, mapping.discomfortIds)
        val settings = environment.settings
        val evidenceInput = mapping.evidence?.let { SetupRingsEvidenceInput.Available(it) }
            ?: if (settings.initialRecoveryEvidence != null) SetupRingsEvidenceInput.Preserved
            else SetupRingsEvidenceInput.Absent
        // El preview y el registro guardado comparten el MISMO `now` inyectado
        // (mapping.previewCheckIn() lleva la fecha del mapeo): nunca se mezclan
        // dos relojes para el mismo check-in.
        return SetupRingsPreviewCalculator(getApplication()).calculate(
            settings, evidenceInput, draft.commitId,
            selected, draft.manualEnergyOverride, draft.manualStructureOverride, discomforts,
            nowEpochMs, checkIn = mapping.previewCheckIn(),
        )
    }
    private fun updateNutritionPreview(draft: SetupWizardDraft) {
        val result = if (draft.includeNutrition) prepareNutrition(draft) else null
        _state.value = _state.value.copy(nutritionPreparation = result,
            nutritionPlanPreview = result?.plan,
            nutritionErrors = result?.errors.orEmpty(),
            nutritionPacePercentPerWeek = result?.recommendation?.suggestedRatePercentBodyWeightPerWeek?.times(100.0),
            requiresActivationConfirmation = activationConfirmation(draft, _state.value.programPreview))
        if (result != null) clearStalePreviews(nutritionPreviewKinds)
    }

    /**
     * Equipo Efectivo para los motores (M7: `TrainingOptions.effectiveEquipment`):
     * con inventario declarado manda él —en casa los 5 grupos finitos del wizard,
     * sin asumir `general_gym`— y sin inventario se conserva el perfil legacy.
     * Incluye siempre BODYWEIGHT.
     */
    private fun effectiveEquipmentIds(draft: SetupWizardDraft): Set<String> =
        draft.trainingOptions.effectiveEquipment(draft.equipment.map { it.catalogId }.toSet())

    // ── Evaluación única (T-005 / §15.2) ─────────────────────────────────────

    /**
     * Token canónico de la huella: colecciones ordenadas, `null` distinto de
     * vacío y enums por nombre (§15.3: clave determinista, sin hora real).
     */
    private fun canonicalToken(value: Any?): String = when (value) {
        null -> "~"
        is Enum<*> -> value.name
        is Set<*> -> "[" + value.map(::canonicalToken).sorted().joinToString(",") + "]"
        is List<*> -> "[" + value.map(::canonicalToken).joinToString(",") + "]"
        is Map<*, *> -> "{" + value.entries
            .map { entry -> "${canonicalToken(entry.key)}=${canonicalToken(entry.value)}" }
            .sorted()
            .joinToString(",") + "}"
        else -> value.toString()
    }

    /**
     * Clave completa de evaluación (§15.3): huella de entradas + equipo efectivo
     * + revisión del catálogo de planes + revisión REAL del catálogo de
     * ejercicios decodificado. Sin hashes de asset inventados.
     */
    internal fun candidateInputKey(draft: SetupWizardDraft, equipment: Set<String>): String =
        candidateInputKey(draft, equipment, exerciseCatalogRevision())

    private fun candidateInputKey(
        draft: SetupWizardDraft,
        equipment: Set<String>,
        exerciseRevision: String?,
    ): String = buildString {
        appendCandidateInputs(draft, equipment, exerciseRevision)
    }

    private fun StringBuilder.appendCandidateInputs(
        draft: SetupWizardDraft,
        equipment: Set<String>,
        exerciseRevision: String?,
    ) {
        // La evaluación pertenece a los inputs del plan, no a la tarjeta que
        // está marcada. Un Ready del barrido se puede reutilizar al seleccionarla.
        append(candidateSetKey(draft).joinToString("|", transform = ::canonicalToken))
        append("|equip=").append(canonicalToken(equipment.sorted()))
        append("|planRev=").append(PersonalizedPlanCatalog.REVISION)
        append("|exRev=").append(exerciseRevision.orEmpty())
    }

    /** Revisión real del catálogo de ejercicios ya decodificado; null = sin cargar. */
    internal fun exerciseCatalogRevision(): String? =
        (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog?.catalogRevision

    private fun goalProfileOf(draft: SetupWizardDraft): PlanGoalProfile = planGoalProfileOf(draft.goal)

    private fun candidateRequest(
        draft: SetupWizardDraft,
        equipment: Set<String>,
        exerciseRevision: String?,
    ): PlanCandidateRequest = PlanCandidateRequest(
        inputKey = candidateInputKey(draft, equipment, exerciseRevision),
        goalProfile = goalProfileOf(draft),
        level = draft.experience.toCatalogLevel(),
        focus = draft.focus.toTrainingFocus(),
        reference = draft.trainingReference(),
        daysPerWeek = requireNotNull(draft.daysPerWeek),
        weekdays = draft.selectedWeekdays,
        minutesPerSession = requireNotNull(draft.minutesPerSession),
        effectiveEquipment = equipment,
        cardioMinutes = draft.cardioMinutes,
        requiresCardio = draft.requiresCardio,
        selectedSplitId = draft.selectedSplitId,
        planCatalogRevision = PersonalizedPlanCatalog.REVISION,
        exerciseCatalogRevision = exerciseRevision,
    )

    /**
     * Entreno v2 · el pedido con el que el evaluador mide UN plan (la clave [PlanCandidateRequest.inputKey] no cambia: la
     * caché y la puerta de activación siguen siendo las mismas).
     *
     * - Programa «a medida»: sin disciplina que filtrar (su modo ya es el del perfil) y sin tope de minutos: el generador
     *   ajusta cada sesión a su ventana (85–110 % del tiempo pedido) y lo que se pase lo dice el revelado con una nota.
     * - Plan del catálogo: el tiempo pedido con una tolerancia del 15 % ([timeBudgetWithTolerance]); el que cabe así es
     *   viable con la nota «~N min por sesión». El resto de reglas de rechazo no cambia.
     */
    private fun evaluationRequestFor(request: PlanCandidateRequest, entryId: String): PlanCandidateRequest =
        if (GeneratedPlans.isGenerated(entryId)) {
            request.copy(reference = null, minutesPerSession = Int.MAX_VALUE)
        } else {
            request.copy(minutesPerSession = SessionTimeFit.limit(request.minutesPerSession, generated = false))
        }

    /**
     * Puerto real de materialización: el MISMO motor del preview, con el plan
     * candidato ya elegido. Traduce los fallos tipados del motor a los motivos
     * cerrados de §15.2; `CancellationException` se propaga y cualquier otro
     * error inesperado lo captura el evaluador como INTERNAL_MATERIALIZATION.
     */
    private fun candidateEngine(source: SetupWizardDraft, request: PlanCandidateRequest): PlanMaterializationPort =
        PlanMaterializationPort { entry, _ ->
            val outcome = try {
                materializeForEvaluation(source.copy(selectedCatalogId = entry.id))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (typed: PlanMaterializationException) {
                throw typed
            } catch (failure: SetupCandidateFailureException) {
                throw translateCandidateFailure(failure)
            }
            val program = outcome.program ?: throw PlanMaterializationException(
                PlanEvaluationStage.MATERIALIZATION,
                PlanRejectionReason.INTERNAL_MATERIALIZATION,
                "el motor no devolvió programa para ${entry.id}",
            )
            // Plan de autor: la receta efectiva (derivada si hubo sustituciones) y su
            // procedencia son las del programa preparado, no las del catálogo vigente.
            val recipe = if (entry.authoredSource != null) program.sourceRecipe else entry.recipe ?: entry.template?.recipe
            PlanMaterializationOutcome(program, recipe, outcome.report)
        }

    /** Etapa/ causa tipadas de un fallo del motor (T-001 / §15.2). */
    private fun translateCandidateFailure(
        failure: SetupCandidateFailureException,
    ): PlanMaterializationException {
        val message = failure.message?.trim().orEmpty().ifBlank { "fallo de materialización sin mensaje" }
        return when (failure.stage) {
            SetupCandidateRejectionStage.CATALOG -> PlanMaterializationException(
                PlanEvaluationStage.CATALOG, PlanRejectionReason.CATALOG_NOT_READY, message)
            SetupCandidateRejectionStage.PROFILE -> PlanMaterializationException(
                PlanEvaluationStage.PROFILE, PlanRejectionReason.LEVEL_UNSUITABLE, message)
            SetupCandidateRejectionStage.FREQUENCY -> PlanMaterializationException(
                PlanEvaluationStage.FREQUENCY_SPLIT, PlanRejectionReason.FREQUENCY, message)
            SetupCandidateRejectionStage.DURATION -> PlanMaterializationException(
                PlanEvaluationStage.SESSION_DURATION, PlanRejectionReason.TIME_BUDGET, message,
                requiredMinutes = message.substringAfter("estima ", "").substringBefore(" min", "")
                    .toIntOrNull(),
            )
            SetupCandidateRejectionStage.COMPOSITION -> PlanMaterializationException(
                PlanEvaluationStage.COMPOSITION, PlanRejectionReason.COMPOSITION, message)
            SetupCandidateRejectionStage.MATERIAL -> PlanMaterializationException(
                PlanEvaluationStage.MATERIAL, PlanRejectionReason.APPARATUS_ABSENT, message)
            SetupCandidateRejectionStage.MATERIALIZATION -> {
                // El motor ya clasificó el mensaje; sin señal clara el fallo es
                // interno (nunca se disfraza de «falta de material»).
                when (stageForUnavailable(message)) {
                    SetupCandidateRejectionStage.CATALOG -> PlanMaterializationException(
                        PlanEvaluationStage.CATALOG, PlanRejectionReason.CATALOG_NOT_READY, message)
                    SetupCandidateRejectionStage.FREQUENCY -> PlanMaterializationException(
                        PlanEvaluationStage.FREQUENCY_SPLIT, PlanRejectionReason.FREQUENCY, message)
                    SetupCandidateRejectionStage.MATERIAL -> PlanMaterializationException(
                        PlanEvaluationStage.MATERIAL, PlanRejectionReason.APPARATUS_ABSENT, message)
                    SetupCandidateRejectionStage.DURATION -> PlanMaterializationException(
                        PlanEvaluationStage.SESSION_DURATION, PlanRejectionReason.TIME_BUDGET, message)
                    SetupCandidateRejectionStage.PROFILE -> PlanMaterializationException(
                        PlanEvaluationStage.PROFILE, PlanRejectionReason.LEVEL_UNSUITABLE, message)
                    else -> PlanMaterializationException(
                        PlanEvaluationStage.MATERIALIZATION, PlanRejectionReason.INTERNAL_MATERIALIZATION, message)
                }
            }
        }
    }

    /**
     * ¿Falta confirmar el aparato (UNKNOWN) o está realmente ausente (ABSENT)?
     * Los requisitos estructurados se contrastan con la misma evidencia que usa el adaptador.
     * Sin tokens no existe una confirmación de material que la UI pueda ofrecer.
     */
    internal fun apparatusReason(tokens: List<String>, draft: SetupWizardDraft): PlanRejectionReason {
        if (tokens.isEmpty()) return PlanRejectionReason.APPARATUS_ABSENT
        val evidence = tokens.map { requirementEvidence(it, draft) }
        return when {
            // Una ausencia declarada ya es un bloqueo definitivo: no se debe
            // explicar una mezcla como si bastara confirmar lo desconocido.
            evidence.any { it == RequirementEvidence.ABSENT } -> PlanRejectionReason.APPARATUS_ABSENT
            evidence.any { it == RequirementEvidence.UNKNOWN } -> PlanRejectionReason.APPARATUS_UNKNOWN
            else -> PlanRejectionReason.APPARATUS_ABSENT
        }
    }

    private fun requirementEvidence(token: String, draft: SetupWizardDraft): RequirementEvidence {
        val resolved = draft.trainingOptions
            .resolveEffectiveEquipment(draft.equipment.map { it.catalogId }.toSet())
        val configuration = token.takeIf { it.startsWith("machine_config:") }?.removePrefix("machine_config:")
        return evidenceOfKind(
            kind = if (configuration != null) "machine" else token,
            configurationId = configuration.orEmpty(),
            equipment = resolved,
            availability = draft.trainingOptions.availability,
        )
    }

    /**
     * Rechazo del evaluador → instrumentación estructurada del wizard (paquete A). `internal` para que las
     * pruebas fijen la copia de `missingRequirements` y la llave confirmable (A.B1) sin montar un barrido completo.
     */
    internal fun setupRejectionOf(evaluation: PlanCandidateEvaluation.Rejected): SetupCandidateRejection {
        val apparatusKey = apparatusKeyFor(evaluation)
        return SetupCandidateRejection(
            planId = evaluation.planId,
            stage = setupStageOf(evaluation.stage),
            // Conserva la CAUSA concreta (mensaje real), nunca solo una clase.
            reason = evaluation.details?.takeIf { it.isNotBlank() } ?: evaluation.reasonCode.name,
            reasonCode = evaluation.reasonCode,
            affectedSlots = evaluation.affectedSlots,
            missingCapabilities = evaluation.missingCapabilities,
            requiredMinutes = evaluation.requiredMinutes,
            needsApparatusConfirmation = evaluation.reasonCode == PlanRejectionReason.APPARATUS_UNKNOWN &&
                apparatusKey != null,
            apparatusKey = apparatusKey,
            missingRequirements = evaluation.missingRequirements,
        )
    }

    /**
     * `SetupCandidateRejectionStage.CATALOG` es GLOBAL (planId nulo): un
     * candidato concreto nunca se rechaza en esa etapa, así que una receta
     * ausente se reporta en materialización con su causa escrita.
     */
    private fun setupStageOf(stage: PlanEvaluationStage): SetupCandidateRejectionStage = when (stage) {
        PlanEvaluationStage.CATALOG -> SetupCandidateRejectionStage.MATERIALIZATION
        PlanEvaluationStage.PROFILE, PlanEvaluationStage.MODALITY_DOSE ->
            SetupCandidateRejectionStage.PROFILE
        PlanEvaluationStage.FREQUENCY_SPLIT -> SetupCandidateRejectionStage.FREQUENCY
        PlanEvaluationStage.MATERIAL, PlanEvaluationStage.REPRESENTABLE_LOAD ->
            SetupCandidateRejectionStage.MATERIAL
        PlanEvaluationStage.COMPOSITION, PlanEvaluationStage.EXECUTABLE_CONTRACT ->
            SetupCandidateRejectionStage.COMPOSITION
        PlanEvaluationStage.MATERIALIZATION -> SetupCandidateRejectionStage.MATERIALIZATION
        PlanEvaluationStage.SESSION_DURATION -> SetupCandidateRejectionStage.DURATION
    }

    /**
     * Clave curada del panel a la que lleva la acción «Falta confirmar X».
     *
     * Paquete A · B1: con `missingRequirements` informados por el motor la llave sale del panel
     * ([PlanRepairAdvisor.confirmableKeyFor], la que sigue sin responder) y NO se lee el texto del mensaje.
     * Sin una llave pendiente o con
     * un requisito ausente no existe confirmación de un toque; el CTA de material solo es para UNKNOWN.
     */
    private fun apparatusKeyFor(evaluation: PlanCandidateEvaluation.Rejected): String? {
        if (evaluation.reasonCode != PlanRejectionReason.APPARATUS_UNKNOWN) return null
        val availability = _state.value.draft.trainingOptions.availability
        return evaluation.missingRequirements.firstNotNullOfOrNull { token ->
            PlanRepairAdvisor.confirmableKeyFor(token, availability)
        }
    }

    /**
     * Paquete A · C3 — reparaciones de UN toque para el rechazo [rejected] del plan propio ([request] es el pedido del
     * barrido; [source] el borrador que lo originó). El asesor PRUEBA cada candidata con el evaluador real del
     * asistente ([evaluateRepairProbe]); nunca adivina. Es un extra: cualquier fallo (salvo la cancelación) deja la
     * lista vacía y el rechazo se explica como siempre, sin botón de un toque.
     *
     * Sin disponibilidad de material declarada (ruta legacy con inventario) no hay panel que confirmar ni «sin
     * confirmar» que reparar: no se propone nada.
     */
    private suspend fun ownPlanRepairsFor(
        source: SetupWizardDraft,
        request: PlanCandidateRequest,
        rejected: PlanCandidateEvaluation.Rejected,
        snapshot: PlanCatalogSnapshot,
        cacheRevision: String,
    ): List<PlanRepair> {
        val availability = source.trainingOptions.availability ?: return emptyList()
        return try {
            PlanRepairAdvisor.suggest(request, rejected, availability) { probe, probeAvailability ->
                evaluateRepairProbe(source, probe, probeAvailability, snapshot, cacheRevision)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Log.w(DIAG_TAG, "reparaciones del plan propio no disponibles: ${failureDetail(error)}")
            emptyList()
        }
    }

    /**
     * El evaluador de sondeos del asesor: rehace el borrador con lo que el sondeo cambia (objetivo, minutos, cardio,
     * reparto y, sobre todo, el MATERIAL que manda: el equipo efectivo se deriva de la disponibilidad recibida, no del
     * pedido original) y evalúa el plan propio del objetivo sondeado con el motor real. Cada veredicto se guarda en
     * [repairProbeCache] por la clave propia del sondeo; el programa no se guarda (solo importa si queda listo).
     */
    private suspend fun evaluateRepairProbe(
        source: SetupWizardDraft,
        probe: PlanCandidateRequest,
        availability: EquipmentAvailability,
        snapshot: PlanCatalogSnapshot,
        cacheRevision: String,
    ): PlanCandidateEvaluation {
        val ownId = ownPlanIdOf(probe.goalProfile)
            ?: return PlanCandidateEvaluation.Rejected(
                planId = probe.goalProfile.name,
                stage = PlanEvaluationStage.CATALOG,
                reasonCode = PlanRejectionReason.RECIPE_UNAVAILABLE,
                details = "el objetivo no tiene plan propio",
            )
        val cacheKey = "${probe.inputKey}|$ownId"
        repairProbeCache.get(cacheRevision, cacheKey)?.let { return it }
        val probeDraft = repairProbeDraft(source, probe, availability)
        val probeRequest = probe.copy(effectiveEquipment = effectiveEquipmentIds(probeDraft))
        val evaluation = PlanCandidateEvaluator.evaluate(
            evaluationRequestFor(probeRequest, ownId),
            snapshot,
            ownId,
            candidateEngine(probeDraft, probeRequest),
        )
        val verdict = if (evaluation is PlanCandidateEvaluation.Ready) {
            evaluation.copy(
                preparedPlan = Program(id = evaluation.preparedPlan.id, name = evaluation.preparedPlan.name),
                recipeSnapshot = null,
                report = null,
            )
        } else {
            evaluation
        }
        repairProbeCache.put(cacheRevision, cacheKey, verdict)
        return verdict
    }

    private fun updateCandidates(draft: SetupWizardDraft) {
        candidateJob?.cancel()
        val generation = ++candidateGeneration
        val equipmentIds = effectiveEquipmentIds(draft)
        // §15.1: Atleta completo también exige preferencias de cardio en la ruta.
        val requiresCardio = draft.requiresCardio
        val canPrepare = draft.includeTraining && draft.programRoute != SetupProgramRoute.LATER &&
            draft.trainingPath != SetupTrainingPath.FROM_SCRATCH && draft.daysPerWeek != null &&
            draft.minutesPerSession != null && draft.selectedWeekdays.size == draft.daysPerWeek && equipmentIds.isNotEmpty() &&
            (!requiresCardio || (draft.hasCardioAnswer && draft.cardioMinutes != null))
        if (!canPrepare) {
            // Entradas incompletas: se retira SOLO la marca de candidatos
            // (`errors["candidates"]`); los errores de otras operaciones (incluido el
            // `previewError` de la selección) nunca se tocan ni se filtran aquí. Los
            // rechazos estructurados del cálculo anterior tampoco significan nada sin
            // entradas completas, así que se limpian con la lista.
            _state.value = withoutStaleCandidatesError(
                _state.value.copy(
                    planCandidates = emptyList(),
                    availablePlanCandidates = emptyList(),
                    candidateRejections = emptyList(),
                    candidateCounts = SetupCandidateCounts(),
                    droppedSelection = null,
                    isCandidateLoading = false,
                    planAdaptedToBodyweight = false,
                    selectionStale = false,
                    planSweep = SetupPlanSweep.IDLE,
                    planReveals = emptyList(),
                ),
            )
            return
        }
        // Nueva carga: la marca vieja de candidatos sale YA, para que un error
        // anterior no tape la lista que está a punto de llegar. La selección
        // caída de la lista anterior tampoco significa nada hasta que llegue la nueva.
        _state.value = withoutStaleCandidatesError(
            _state.value.copy(
                planCandidates = emptyList(),
                availablePlanCandidates = emptyList(),
                candidateRejections = emptyList(),
                candidateCounts = SetupCandidateCounts(),
                droppedSelection = null,
                isCandidateLoading = true,
                planAdaptedToBodyweight = false,
                planSweep = SetupPlanSweep.LOADING,
                planReveals = emptyList(),
            ),
        )
        val sources = candidateSourcesOf(draft)
        candidateJob = viewModelScope.launch {
            try {
                val sweepStats = CandidateSweepStats()
                val firstSweep = candidateSweepsStarted == 0
                candidateSweepsStarted += 1
                val catalogWasLoaded = catalogLoaded
                val catalogStartedNs = System.nanoTime()
                ensureCatalogLoaded()
                val catalogMs = (System.nanoTime() - catalogStartedNs) / 1_000_000L
                val sweepStartedNs = System.nanoTime()
                val outcome = withContext(Dispatchers.Default) {
                    val exerciseRevision = exerciseCatalogRevision()
                    suspend fun collectViable(
                        equipment: Set<String>,
                        protocolOnly: Boolean,
                        // Paquete A · C3: solo el pase PEDIDO adjunta reparaciones al rechazo del plan propio; el
                        // pase a peso corporal es una alternativa y sus rechazos nunca se publican.
                        attachOwnPlanRepairs: Boolean = false,
                    ): CandidateScan {
                    sweepStats.passes.incrementAndGet()
                    val source = if (equipment == setOf("bodyweight")) bodyweightAdapted(draft) else draft
                    // Atleta completo NO filtra por `schedulesCardio` (§15.1):
                    // su capability real la exige el evaluador sobre la receta. El
                    // planner solo aplica el prefiltro BARATO de capacidades declaradas
                    // (fuerza + hipertrofia + potencia + cardio, DEC-w2-06): así los
                    // PROFILE_MISMATCH triviales de medio catálogo no encabezan los rechazos.
                    val reference = draft.trainingReference()
                    val publishedEntries = SetupTrainingPlanner.candidates(SetupTrainingPlannerInput(reference, draft.daysPerWeek,
                        equipment, draft.experience.toCatalogLevel(),
                        draft.focus.toTrainingFocus(), protocolOnly = protocolOnly,
                        mixedTraining = draft.goal == SetupGoal.MIXED,
                        requiredCapabilities = PlanGoalMatcher.requiredCapabilities(goalProfileOf(draft))))
                    val request = candidateRequest(source, equipment, exerciseRevision)
                    val snapshot = PlanCatalogSnapshot(
                        entries = PersonalizedPlanCatalog.entries(),
                        planRevision = PersonalizedPlanCatalog.REVISION,
                        exerciseCatalogRevision = exerciseRevision,
                    )
                    val engine = candidateEngine(source, request)
                    val cacheRevision = "${PersonalizedPlanCatalog.REVISION}|$exerciseRevision"
                    val viableEntries = mutableListOf<CatalogEntry>()
                    val readySnapshots = linkedMapOf<String, PlanCandidateEvaluation.Ready>()
                    // T-001 / AC-T001-02: por cada candidato que NO encaja se
                    // conserva un rechazo ESTRUCTURADO (etapa + motivo cerrado +
                    // causa concreta). El antiguo `catch (_: Exception) { false }`
                    // fundía material, tiempo y catálogo en el mismo aviso.
                    val rejections = mutableListOf<SetupCandidateRejection>()
                    // Paquete A · C3: el plan PROPIO del objetivo (el que la persona espera) y, si queda rechazado, su
                    // evaluación cruda: de ella salen las reparaciones de un toque.
                    val ownPlanId = if (attachOwnPlanRepairs) ownPlanIdOf(goalProfileOf(draft)) else null
                    var ownRejected: PlanCandidateEvaluation.Rejected? = null
                    // §15.2: se evalúa TODA la lista (el límite de 6 tarjetas es
                    // visual): la búsqueda no se corta antes de tener testigos, y
                    // paginar no rematerializa (la caché de sesión guarda el Ready).
                    for (entry in publishedEntries) {
                        currentCoroutineContext().ensureActive()
                        val cacheKey = "${request.inputKey}|${entry.id}"
                        val cached = candidateCache.get(cacheRevision, cacheKey)
                        if (cached != null) sweepStats.cacheHits.incrementAndGet() else sweepStats.evaluated.incrementAndGet()
                        val evaluation = cached
                            ?: PlanCandidateEvaluator.evaluate(evaluationRequestFor(request, entry.id), snapshot, entry.id, engine)
                                .also { result -> candidateCache.put(cacheRevision, cacheKey, result) }
                        when (evaluation) {
                            is PlanCandidateEvaluation.Ready -> {
                                readySnapshots[evaluation.planId] = evaluation
                                if (viableEntries.none { it.id == evaluation.planId }) viableEntries += entry
                            }
                            is PlanCandidateEvaluation.Rejected -> {
                                rejections += setupRejectionOf(evaluation)
                                if (evaluation.planId == ownPlanId) ownRejected = evaluation
                            }
                            PlanCandidateEvaluation.CatalogLoading -> Unit
                        }
                    }
                    // Paquete A · C3: con el plan propio rechazado, el asesor propone sus reparaciones de un toque (como
                    // mucho unas pocas evaluaciones extra, y solo en este caso) y se adjuntan a SU rechazo.
                    val rejectedOwn = ownRejected
                    val publishedRejections = if (rejectedOwn == null) {
                        rejections
                    } else {
                        val repairs = ownPlanRepairsFor(source, request, rejectedOwn, snapshot, cacheRevision)
                        rejections.map { rejection ->
                            if (rejection.planId == rejectedOwn.planId) rejection.copy(repairs = repairs) else rejection
                        }
                    }
                    // La UI expone primero tres tarjetas. Recién terminado el
                    // barrido, se refrescan esas tres entradas en el LRU para
                    // que el primer toque pueda usar su snapshot aunque el
                    // catálogo completo supere el límite de 32.
                    // Orden: el del planificador, sin reordenar aquí. El `rank` editorial ya pone
                    // los planes propios y los originales delante de las adaptaciones (DEC-w2-06);
                    // el antiguo `sortedBy { ADAPTED → 1 }` era redundante y además empujaba las
                    // adaptaciones por detrás de BBB y de las versiones anteriores de PHUL y PHAT.
                    viableEntries.take(3).forEach { entry ->
                        readySnapshots[entry.id]?.let { ready ->
                            candidateCache.put(cacheRevision, "${request.inputKey}|${entry.id}", ready)
                        }
                    }
                    return CandidateScan(publishedEntries, viableEntries, publishedRejections, readySnapshots)
                    }

                    /**
                     * Entreno v2 · el programa «a medida» del perfil: UNA evaluación (el generador nunca falla por
                     * días, minutos ni material), por el mismo evaluador, la misma caché y la misma clave que los
                     * demás candidatos, así la vista previa y la activación lo reutilizan tal cual.
                     */
                    suspend fun collectGenerated(profile: TrainingGoalProfile): CandidateScan {
                        sweepStats.passes.incrementAndGet()
                        val entry = PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(profile))
                            ?: return CandidateScan.EMPTY
                        val request = candidateRequest(draft, equipmentIds, exerciseRevision)
                        val snapshot = PlanCatalogSnapshot(
                            entries = PersonalizedPlanCatalog.entries(),
                            planRevision = PersonalizedPlanCatalog.REVISION,
                            exerciseCatalogRevision = exerciseRevision,
                        )
                        val cacheRevision = "${PersonalizedPlanCatalog.REVISION}|$exerciseRevision"
                        val cacheKey = "${request.inputKey}|${entry.id}"
                        currentCoroutineContext().ensureActive()
                        val cached = candidateCache.get(cacheRevision, cacheKey)
                        if (cached != null) sweepStats.cacheHits.incrementAndGet() else sweepStats.evaluated.incrementAndGet()
                        val evaluation = cached
                            ?: PlanCandidateEvaluator.evaluate(
                                evaluationRequestFor(request, entry.id),
                                snapshot,
                                entry.id,
                                candidateEngine(draft, request),
                            ).also { result -> candidateCache.put(cacheRevision, cacheKey, result) }
                        return when (evaluation) {
                            is PlanCandidateEvaluation.Ready ->
                                CandidateScan(listOf(entry), listOf(entry), emptyList(), mapOf(entry.id to evaluation))
                            is PlanCandidateEvaluation.Rejected -> {
                                Log.w(DIAG_TAG, "programa a medida rechazado: ${evaluation.reasonCode} ${evaluation.details.orEmpty().take(DIAG_REASON_MAX)}")
                                CandidateScan(listOf(entry), emptyList(), listOf(setupRejectionOf(evaluation)))
                            }
                            PlanCandidateEvaluation.CatalogLoading -> CandidateScan(listOf(entry), emptyList(), emptyList())
                        }
                    }

                    val profile = draft.goalProfile
                    if (profile != null && sources != PlanCandidateSources.CATALOG) {
                        // Entreno v2: el «a medida» siempre existe, así que no hay pase a peso corporal (ese pase solo
                        // servía para no dejar la lista vacía). Las disciplinas con autores conservan las reparaciones
                        // de un toque de su plan propio (el aviso de la selección caída las ofrece).
                        val generated = collectGenerated(profile)
                        val scan = if (sources == PlanCandidateSources.GENERATED_AND_CATALOG) {
                            generated + collectViable(equipmentIds, protocolOnly = false, attachOwnPlanRepairs = true)
                        } else {
                            generated
                        }
                        return@withContext candidateOutcomeWithViews(draft, CandidateOutcome(scan, scan, useAdapted = false))
                    }
                    val requested = collectViable(
                        equipmentIds,
                        protocolOnly = draft.programRoute == SetupProgramRoute.PROTOCOL,
                        attachOwnPlanRepairs = true,
                    )
                    // D-005/E-015: bajo PROTOCOL NUNCA hay segundo pase
                    // `protocolOnly=false`. Si no hay protocolo viable, la UI
                    // muestra la razón y una acción EXPLÍCITA de cambiar de ruta;
                    // el código jamás sustituye la ruta ni publica un nativo.
                    // En el recorrido unificado nuevo la ruta es CUSTOMIZABLE, así
                    // que este guard solo aplica a drafts legacy de protocolo.
                    //
                    // Paquete A · D4 (B-07, DEC-w2-05): el pase a peso corporal solo se intenta cuando NADA
                    // fue viable y TODOS los rechazos del pase pedido son de material (APPARATUS_UNKNOWN o
                    // APPARATUS_ABSENT). Un TIME_BUDGET, PROFILE_MISMATCH, COMPOSITION o un fallo interno lo
                    // impiden: la persona tiene que ver el motivo real, no un plan «adaptado» que no
                    // resuelve el problema (antes, un TIME_BUDGET del plan con su material podía acabar en
                    // un plan de peso corporal presentado como «tu material no tenía una receta ejecutable»).
                    val adaptedPass = draft.programRoute != SetupProgramRoute.PROTOCOL &&
                        equipmentIds != setOf("bodyweight") &&
                        bodyweightPassAllowed(requested.viable.size, requested.rejections)
                    val bodyweightScan = if (adaptedPass) {
                        collectViable(setOf("bodyweight"), protocolOnly = false)
                    } else {
                        null
                    }
                    // Se MUESTRAN los candidatos del pase corporal solo si hubo alguno; los rechazos y los
                    // conteos que se publican son SIEMPRE los del pase pedido (ver `CandidateOutcome`).
                    val adaptedScan = bodyweightScan?.takeIf { it.viable.isNotEmpty() }
                    candidateOutcomeWithViews(
                        draft,
                        CandidateOutcome(requested, adaptedScan ?: requested, useAdapted = adaptedScan != null),
                    )
                }
                // C4: solo medición. Sin datos personales: milisegundos y contadores.
                Log.i(
                    PERF_TAG,
                    candidateSweepLogLine(
                        totalMs = catalogMs + (System.nanoTime() - sweepStartedNs) / 1_000_000L,
                        catalogMs = catalogMs,
                        sweepMs = (System.nanoTime() - sweepStartedNs) / 1_000_000L,
                        catalogWasLoaded = catalogWasLoaded,
                        firstSweep = firstSweep,
                        published = outcome.scan.published.size,
                        evaluated = sweepStats.evaluated.get(),
                        cacheHits = sweepStats.cacheHits.get(),
                        passes = sweepStats.passes.get(),
                        viable = outcome.scan.viable.size,
                        useAdapted = outcome.useAdapted,
                    ),
                )
                // Paquete A · D4 (B-07): lo que se explica (publicados, rechazos, conteos) es SIEMPRE el pase
                // pedido; lo que se muestra como tarjetas es el pase mostrado (`scan`).
                val published = outcome.requested.published
                val viable = outcome.scan.viable
                val useAdapted = outcome.useAdapted
                // AC-T001-03: sólo publica si las entradas de ESTE cálculo siguen
                // vigentes; un job cancelado o tardío jamás sobreescribe la respuesta más nueva.
                if (ownsCandidateGeneration(generation) && isCurrentCandidateKey(draft)) {
                    // C.P11: el texto crudo del motor NUNCA se pinta; la causa concreta (`reason`) y el código cerrado del
                    // plan propio van solo al registro, sin datos personales.
                    ownPlanIdOf(goalProfileOf(draft))?.let { ownId ->
                        outcome.requested.rejections.firstOrNull { it.planId == ownId }?.let { own ->
                            Log.i(
                                DIAG_TAG,
                                "plan propio rechazado: plan=$ownId motivo=${own.reasonCode} etapa=${own.stage} " +
                                    "reparaciones=${own.repairs.size} causa=${own.reason.take(DIAG_REASON_MAX)}",
                            )
                        }
                    }
                    val options = outcome.cards
                    check(options.map { it.id } == viable.map { it.id }) { "las tarjetas no son los viables mostrados" }
                    val current = _state.value
                    if (options.isEmpty()) {
                        // Estado explícito «no compatible» con motivo REAL (UDF):
                        // una lista vacía nunca se publica en silencio. El motivo
                        // sale de los datos del propio borrador, sin fabricar nada.
                        // Sin la lista de tokens de material (`general_gym`, `barbell`…): son identificadores internos y
                        // este aviso lo lee la persona (C.P11: nada de ids ni códigos en los textos).
                        val frequency = draft.daysPerWeek?.let { "${SpanishPlurals.days(it)} por semana" } ?: "esta frecuencia"
                        val reason = when {
                            // Entreno v2: con un programa «a medida» en juego, quedarse sin nada es un fallo de
                            // preparación (el generador no falla por días, minutos ni material): texto de COPY.
                            sources != PlanCandidateSources.CATALOG -> PLAN_PREPARE_FAILED_MESSAGE
                            published.isEmpty() -> "No hay planes publicados compatibles con tu material y $frequency."
                            else -> SpanishPlurals.choose(
                                published.size,
                                "1 plan publicado, pero no es ejecutable con tu material y $frequency.",
                                "${published.size} planes publicados; ninguno es ejecutable con tu material y $frequency.",
                            )
                        }
                        _state.value = current.copy(
                            planCandidates = emptyList(), availablePlanCandidates = emptyList(),
                            isCandidateLoading = false,
                            planAdaptedToBodyweight = false,
                            // El motivo vive en `errors["candidates"]`; los rechazos
                            // ESTRUCTURADOS por candidato van en `candidateRejections`
                            // (AC-T001-02). El preview anterior se retira: ya no
                            // corresponde a este material.
                            errors = current.errors + ("candidates" to reason),
                            candidateRejections = outcome.requested.rejections,
                            candidateCounts = outcome.requested.counts,
                            programPreview = null,
                            previewReport = null,
                            planSweep = SetupPlanSweep.FAILED,
                            planReveals = emptyList(),
                        )
                    } else {
                        // Éxito: sólo se retira la marca propia de candidatos;
                        // `previewError` ajeno (p. ej. del programa) se conserva.
                        _state.value = current.copy(
                            // H3: las tarjetas visibles son las primeras, pero el plan elegido (o el preseleccionado desde
                            // la biblioteca) viable nunca queda escondido detrás de «Ver más opciones».
                            planCandidates = visibleCandidatesFor(options, current.draft.selectedCatalogId),
                            availablePlanCandidates = options,
                            isCandidateLoading = false,
                            planAdaptedToBodyweight = useAdapted,
                            errors = current.errors - "candidates",
                            // Los que se evaluaron y no encajaron siguen visibles
                            // para que la UI muestre evaluados/viables/no viables.
                            // Paquete A · D4 (B-07): son los del pase PEDIDO, también cuando las
                            // tarjetas salen del pase a peso corporal.
                            candidateRejections = outcome.requested.rejections,
                            candidateCounts = outcome.requested.counts,
                            planSweep = SetupPlanSweep.READY,
                            planReveals = outcome.reveals,
                        )
                        refreshPreviewAfterCandidates(options.map { it.id }.toSet(), outcome.requested.rejections)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (ownsCandidateGeneration(generation) && isCurrentCandidateKey(draft)) {
                    // Paquete A · D3 (B-06): un catálogo que no quedó listo se explica en llano y con su
                    // motivo cerrado; cualquier otro fallo global conserva su aviso genérico y su causa
                    // (clase + mensaje) solo en el rechazo estructurado.
                    val catalogNotReady = error is PlanMaterializationException &&
                        error.reason == PlanRejectionReason.CATALOG_NOT_READY
                    val message = when {
                        catalogNotReady -> CATALOG_UNAVAILABLE_MESSAGE
                        // Entreno v2: el fallo del barrido de programas se dice con el texto de COPY.
                        sources != PlanCandidateSources.CATALOG -> PLAN_PREPARE_FAILED_MESSAGE
                        else -> "No pude comprobar los planes. Prueba de nuevo."
                    }
                    val rejection = if (catalogNotReady) {
                        SetupCandidateRejection(
                            planId = null,
                            stage = SetupCandidateRejectionStage.CATALOG,
                            reason = CATALOG_UNAVAILABLE_MESSAGE,
                            reasonCode = PlanRejectionReason.CATALOG_NOT_READY,
                        )
                    } else {
                        // AC-T001-02: la causa ESTRUCTURADA de este fallo global es de
                        // etapa CATALOG y conserva clase+mensaje; el texto amable de arriba
                        // es para la UI, el registro es para diagnosticar de verdad.
                        SetupCandidateRejection(null, SetupCandidateRejectionStage.CATALOG, failureDetail(error))
                    }
                    // Paquete A · D2 (B-01): el fallo de la BÚSQUEDA vive solo en `errors["candidates"]`;
                    // `previewError` es únicamente el del preview de la selección.
                    _state.value = _state.value.copy(
                        planCandidates = emptyList(), availablePlanCandidates = emptyList(),
                        isCandidateLoading = false, planAdaptedToBodyweight = false,
                        errors = _state.value.errors + ("candidates" to message),
                        candidateRejections = listOf(rejection),
                        planSweep = SetupPlanSweep.FAILED,
                        planReveals = emptyList(),
                    )
                }
            }
        }
    }

    private fun ownsCandidateGeneration(generation: Long): Boolean = generation == candidateGeneration

    // ─── Entreno v2: fuentes de candidatos, tarjetas y revelado ──────────────────────────────────────────

    /**
     * De dónde salen los programas del borrador: por perfil de objetivo ([GeneratedPlans.sourcesFor]). La ruta legacy de
     * protocolo (solo recetas de autor) y un borrador sin perfil siguen con el planificador de siempre.
     */
    private fun candidateSourcesOf(draft: SetupWizardDraft): PlanCandidateSources =
        if (draft.programRoute == SetupProgramRoute.PROTOCOL) PlanCandidateSources.CATALOG
        else GeneratedPlans.sourcesFor(draft.goalProfile)

    /** Las tarjetas y el revelado de los viables mostrados por [outcome] (fuera del hilo principal). */
    private fun candidateOutcomeWithViews(draft: SetupWizardDraft, outcome: CandidateOutcome): CandidateOutcome {
        val viable = outcome.scan.viable
        val cards = viable.map { entry ->
            if (entry.isGenerated) generatedCandidateCard(entry, generatedRoutineFor(draft, entry))
            else catalogCandidateCard(entry, draft, outcome.useAdapted)
        }
        val reveals = viable.mapIndexedNotNull { index, entry ->
            val ready = outcome.scan.ready[entry.id] ?: return@mapIndexedNotNull null
            val program = ready.preparedPlan
            if (entry.isGenerated) {
                SetupPlanReveals.forGenerated(
                    entry = entry,
                    program = program,
                    routine = generatedRoutineFor(draft, entry),
                    profile = draft.goalProfile,
                    level = draft.experience.toPlanLevel(),
                    declaredMinutes = draft.minutesPerSession,
                    // «Otra versión» también se ve en la portada: cada semilla mueve su degradado y su ilustración.
                    coverSeed = index + draft.planVariantSeed,
                )
            } else {
                SetupPlanReveals.forCatalog(
                    entry = entry,
                    program = program,
                    reasons = cards[index].reasons,
                    profile = draft.goalProfile,
                    declaredMinutes = draft.minutesPerSession,
                    coverSeed = index,
                )
            }
        }
        return outcome.copy(cards = cards, reveals = reveals)
    }

    /** Tarjeta de un plan del catálogo (C.P5): ficha editorial y motivos de encaje. */
    private fun catalogCandidateCard(entry: CatalogEntry, draft: SetupWizardDraft, useAdapted: Boolean): SetupPlanCandidate =
        SetupPlanCandidate(
            id = entry.id,
            // C.P5: la ficha editorial, no los alias heredados del catálogo. La tarjeta pinta
            // título, subtítulo y motivos; `description` y `details` no los pinta ninguna
            // pantalla (ver KDoc de `SetupPlanCandidate`).
            title = entry.displayName,
            subtitle = PlanLabels.subtitle(entry),
            description = entry.summary,
            source = entry.source.name,
            reasons = buildList {
                draft.daysPerWeek?.let { days ->
                    if (entry.supportedFrequencies.contains(days)) add(weekFitReason(days))
                }
                if (useAdapted) {
                    add("Plan KPKN adaptado a peso corporal: tu material no tenía una receta ejecutable")
                } else {
                    add("Se ejecuta con el equipo que has elegido")
                }
                if (entry.level == draft.experience.toCatalogLevel()) add("Su nivel coincide con tu experiencia")
                if (draft.goal == SetupGoal.MIXED && entry.schedulesCardio) add("Programa el cardio que has pedido")
                // A.E1 (D6): qué hace ESTA tarjeta con la bolsa de prioridades (si la hay).
                planOrderPriorityReason(entry, draft.trainingOptions.orderPriorities)?.let { add(it) }
            },
            details = entry.attributionLine?.takeIf { it.isNotBlank() },
        )

    /** Tarjeta del programa «a medida»: sus razones son las del generador («por qué este programa»). */
    private fun generatedCandidateCard(entry: CatalogEntry, routine: GeneratedRoutine?): SetupPlanCandidate =
        SetupPlanCandidate(
            id = entry.id,
            title = entry.displayName,
            subtitle = PlanLabels.subtitle(entry),
            description = routine?.summary?.oneLiner ?: entry.summary,
            source = entry.source.name,
            reasons = routine?.summary?.reasons.orEmpty(),
        )

    /** Clave de la rutina generada para [draft] (la misma que la de su evaluación: `inputKey|planId`). */
    private fun generatedRoutineKey(draft: SetupWizardDraft): String =
        "${candidateInputKey(draft, effectiveEquipmentIds(draft), exerciseCatalogRevision())}|${draft.selectedCatalogId}"

    /**
     * La rutina que el generador armó para [entry] con las respuestas de [draft]: la de la memoria o, si ya no está,
     * la misma vuelta a generar (determinista). Null sin catálogo cargado o si el generador rechaza el pedido.
     */
    private fun generatedRoutineFor(draft: SetupWizardDraft, entry: CatalogEntry): GeneratedRoutine? {
        val chosen = draft.copy(selectedCatalogId = entry.id)
        generatedRoutines.get(generatedRoutineKey(chosen))?.let { return it }
        val mode = GeneratedPlans.modeOf(entry.id) ?: return null
        val catalog = (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog ?: return null
        return runCatching { RoutineGenerator.generate(chosen.routineRequest(mode, catalog)) }
            .onFailure { error -> if (error is CancellationException) throw error }
            .getOrNull()
            ?.also { routine -> generatedRoutines.put(generatedRoutineKey(chosen), routine) }
    }

    /**
     * Entreno v2 · el programa «a medida» de [entry] para [draft]: el generador de rutinas con el pedido del borrador
     * ([routineRequest], ids derivados del alta), así el barrido, la vista previa y la activación generan exactamente lo
     * mismo. Lo que el generador no sabe del alta lo completa [generatedProgramOf].
     */
    private fun materializeGenerated(draft: SetupWizardDraft, entry: CatalogEntry, mode: RoutineMode): SetupPreview {
        val catalog = (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog
            ?: throw PlanMaterializationException(
                PlanEvaluationStage.CATALOG,
                PlanRejectionReason.CATALOG_NOT_READY,
                CATALOG_UNAVAILABLE_MESSAGE,
            )
        val routine = try {
            RoutineGenerator.generate(draft.routineRequest(mode, catalog))
        } catch (impossible: RoutineGenerationException) {
            throw PlanMaterializationException(
                PlanEvaluationStage.MATERIALIZATION,
                PlanRejectionReason.INTERNAL_MATERIALIZATION,
                impossible.message ?: "el generador rechazó el pedido",
            )
        }
        generatedRoutines.put(generatedRoutineKey(draft), routine)
        return SetupPreview(generatedProgramOf(routine, entry, draft), null)
    }

    /**
     * Paquete A · D2 (B-01): relanza el programa SOLO cuando el plan elegido sigue entre los viables de la
     * lista que acaba de llegar. Si ya no lo está, NO se prepara su preview (antes el error de ese preview
     * escondía la lista entera): la selección pasa a [SetupWizardState.droppedSelection] con el rechazo de
     * ese plan en el barrido ([viableIds] y [rejections] son los de este cálculo). Sin plan elegido no hay
     * nada que relanzar.
     */
    private fun refreshPreviewAfterCandidates(viableIds: Set<String>, rejections: List<SetupCandidateRejection>) {
        val selected = _state.value.draft.selectedCatalogId ?: return
        if (selected in viableIds) {
            preparePreview(_state.value.draft)
        } else {
            dropSelection(selected, rejections)
        }
    }

    /**
     * Marca la selección como caída y deja el borrador coherente con ello:
     *  - se cancela cualquier preview en vuelo y se retira el programa del plan que ya no encaja, junto con su
     *    error (todo eso era de la selección anterior y ya no corresponde a ninguna respuesta);
     *  - si el paso PLAN todavía NO se confirmó, la selección se limpia: la persona no confirmó nada;
     *  - si ya se confirmó, la selección se CONSERVA (no se borra una respuesta confirmada) y el paso queda
     *    por revisar con la API de dependencias (`withPendingReview`); mientras tanto
     *    [planSelectionGate] impide continuar con ella;
     *  - excepción (H2 c): si la selección es el plan que la persona trajo de la biblioteca ([PRESELECTED_PLAN_KEY]),
     *    se conserva aunque PLAN no esté confirmado. Es su intención: el aviso explica qué falta y, cuando el plan
     *    vuelve a ser viable, el barrido siguiente lo prepara solo; elegir otro plan la reemplaza.
     *
     * Si el barrido no evaluó el plan (el planificador lo excluyó), el aviso lleva el motivo sintetizado por
     * [synthesizedRejectionFor] (días o disciplina).
     */
    private fun dropSelection(planId: String, rejections: List<SetupCandidateRejection>) {
        val entry = PersonalizedPlanCatalog.find(planId)
        val dropped = SetupDroppedSelection(
            planId = planId,
            // C.P5/C.P6: el nombre de la ficha editorial, no el alias heredado `title`.
            title = entry?.displayName ?: DROPPED_SELECTION_FALLBACK_TITLE,
            // H2 (b): un plan que el barrido ni evaluó (el planificador lo excluyó por días u objetivo) se explica con
            // el motivo que lo excluyó, para que el presentador dé su texto y su botón («Este plan usa 4 días distintos;
            // elegiste 3 días.» → Cambiar días) en lugar del genérico «ya no está entre los planes…».
            rejection = rejections.firstOrNull { it.planId == planId }
                ?: entry?.let { synthesizedRejectionFor(it, _state.value.draft) },
            // Entreno v2: un plan propio de la biblioteca que un perfil GENERAL ya no ofrece (solo ofrece su «a medida»)
            // no «cae» por culpa de nada: el asistente lo arma a medida y el aviso lo dice, sin alarma.
            tailoredId = tailoredReplacementOf(entry, _state.value.draft.goalProfile),
        )
        // H2 (c): el plan que la persona trajo de la biblioteca es su INTENCIÓN mientras siga siendo la selección: no se
        // limpia aunque no encaje todavía (el aviso explica por qué) y, en cuanto vuelve a encajar, ya está elegido.
        val libraryIntent = savedStateHandle.get<String>(PRESELECTED_PLAN_KEY) == planId
        // El código cerrado del motivo solo va al registro (la pantalla muestra texto llano): id del plan y
        // código, sin respuestas ni datos personales.
        Log.i(
            DIAG_TAG,
            "selección caída: plan=$planId motivo=${dropped.rejection?.reasonCode ?: "sin rechazo"} " +
                "intención de la biblioteca=$libraryIntent",
        )
        // Primero se retira la propiedad del preview en vuelo; después se publica la marca (el guard de
        // `preparePreview` la lee) para que el guardado de abajo no relance nada.
        releasePreviewGeneration(cancelInFlight = true)
        val current = _state.value
        _state.value = current.copy(
            droppedSelection = dropped,
            programPreview = null, previewReport = null,
            programSessionMinutes = null, fixedTrainingDays = null,
            previewError = null, errors = current.errors - "preview",
        )
        mutateDraft { draft ->
            when {
                // La persona ya eligió otro plan mientras tanto: no se toca.
                draft.selectedCatalogId != planId -> draft
                SetupStepId.PLAN in draft.stepProgress.answers ->
                    draft.copy(stepProgress = draft.stepProgress.withPendingReview(setOf(SetupStepId.PLAN)))
                // La intención de la biblioteca se conserva (H2 c); `planSelectionGate` impide continuar con ella.
                libraryIntent -> draft
                else -> draft.copy(selectedCatalogId = null, acceptFixedRecipeDifference = false)
            }
        }
    }

    /**
     * Retira SOLO la marca de candidatos (`errors["candidates"]`): así un error viejo nunca se queda
     * ocultando una lista nueva y los fallos de otras operaciones siguen visibles. Paquete A · D2 (B-01):
     * `previewError` ya no es de los candidatos (es solo el del preview de la selección), así que esta
     * función no lo toca.
     */
    private fun withoutStaleCandidatesError(state: SetupWizardState): SetupWizardState =
        state.copy(errors = state.errors - "candidates")
    private fun previewInputsIncomplete(draft: SetupWizardDraft): Boolean { if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) return false; val days = draft.daysPerWeek ?: return true; if (draft.minutesPerSession == null || draft.selectedWeekdays.size != days || draft.requiresCardio && (!draft.hasCardioAnswer || draft.cardioMinutes == null)) return true; return if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) { val selected = draft.sessions.filter { it.weekday in draft.selectedWeekdays }; selected.size != draft.selectedWeekdays.size || selected.any { it.exercises.isEmpty() } } else draft.selectedCatalogId == null }

    private suspend fun materializeProgram(draft: SetupWizardDraft): SetupPreview {
        if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) return SetupPreview(null, null)
        if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
            check(!previewInputsIncomplete(draft)) { "Completa todas las sesiones antes de crear el programa" }
            val id = draft.commitId
            val sessions = draft.sessions.filter { it.weekday in draft.selectedWeekdays }
                .sortedBy { it.weekday }.map { it.toSession(id) }
            val week = ProgramWeek("$id-week", "Semana de entrenamiento", sessions = sessions)
            val mesocycle = Mesocycle("$id-meso", "Mi entrenamiento", weeks = listOf(week))
            val block = Block("$id-block", "Bloque inicial", mesocycles = listOf(mesocycle))
            val macrocycle = Macrocycle("$id-macro", "Mi programa", blocks = listOf(block))
            val program = Program(id = id, name = "Mi plan de entrenamiento",
                startDay = draft.selectedWeekdays.minOrNull(), weekDays = draft.daysPerWeek,
                macrocycles = listOf(macrocycle), volumeRecommendations = draft.volumeRecommendations,
                athleteProfileScore = draft.athleteProfileScore,
                schedulePlan = ProgramSchedulePlan(
                    weekStartDay = draft.selectedWeekdays.minOrNull(),
                    trainingDays = draft.selectedWeekdays.toSet(),
                ))
            ProgramExecutionContract.requireExecutable(program)
            return SetupPreview(program, null)
        }
        val entry = draft.selectedCatalogId?.let(PersonalizedPlanCatalog::find) ?: error("Selecciona un plan")
        // D-005/E-015 guard de fuente: la ruta PROTOCOL solo acepta recetas de
        // autor. Un ID nativo forzado (selección manipulada o borrador
        // restaurado) se rechaza con motivo tipado de FUENTE EQUIVOCADA, tanto
        // en PLAN/review como en materialización; nunca llega a receipt.
        if (draft.programRoute == SetupProgramRoute.PROTOCOL && entry.source != CatalogSource.PROTOCOL) {
            error("Fuente equivocada para la ruta elegida: la ruta de protocolo solo acepta recetas de autor. Cambia de ruta de forma explícita para usar un plan nativo.")
        }
        // Entreno v2: un programa «a medida» lo arma el generador de rutinas, nunca el personalizador nativo.
        GeneratedPlans.modeOf(entry.id)?.let { mode -> return materializeGenerated(draft, entry, mode) }
        if (entry.source == CatalogSource.NATIVE) {
            val frequency = draft.daysPerWeek ?: error("Selecciona los días de entrenamiento")
            val result = OnboardingPlanGenerator(SimpleCyclePersonalizer(catalogRepository)).generate(
                draft.commitId,
                PersonalizerInput(
                    catalogEntryId = entry.id,
                    focus = draft.focus.toTrainingFocus(),
                    frequency = frequency,
                    weekdays = draft.selectedWeekdays.sorted(),
                    equipment = effectiveEquipmentIds(draft),
                    level = draft.experience.toCatalogLevel(),
                    // Entreno v2: el alta acepta hasta 180 min, pero los planes propios no se estiran más allá de su
                    // tope de 100 (el personalizador rechaza más): con más tiempo se arman con 100 y el «a medida»
                    // es quien aprovecha el resto.
                    availableMinutes = (draft.minutesPerSession ?: error("Indica el tiempo disponible"))
                        .coerceAtMost(OWN_PLAN_MAX_MINUTES),
                    cardio = if (draft.requiresCardio) requireNotNull(draft.cardioPreference()) else null,
                    calibration = if (draft.volumeRecommendations.isNotEmpty()) Calibration.CALIBRATED else Calibration.CONSERVATIVE,
                    volumeRecommendations = draft.volumeRecommendations,
                    priorityMuscles = draft.priorityMuscles,
                    lowerEmphasisMuscles = draft.lowerEmphasisMuscles,
                    splitId = draft.selectedSplitId,
                    splitPattern = draft.customSplitPattern,
                    splitName = draft.customSplitName,
                ),
                // Cadena real de entrenamiento: el motor recibe las opciones del
                // usuario (inventario, prioridades, calentamientos, autorreg.)
                options = draft.trainingOptions,
            )
            return SetupPreview(result.program?.copy(id = draft.commitId)
                // F-A2: el fitter rellena `reasonCode`/`maxSessionMinutes`; la razón
                // TIPADA (y los minutos mínimos del TIME_BUDGET) viajan al evaluador.
                // Solo sin `reasonCode` conocido se conserva la clasificación por texto.
                ?: throw (NativePlanFailureMapper.typedFailure(result.report)
                    ?: SetupCandidateFailureException(
                        stageForUnavailable(result.report.limitations.joinToString(" ")),
                        result.report.limitations.joinToString(" ")
                            .ifBlank { NativePlanFailureMapper.DEFAULT_MESSAGE },
                    )), result.report)
        }
        val catalog = (catalogRepository.state.value as? ExerciseCatalogStateV2.Ready)?.catalog
            ?: throw SetupCandidateFailureException(
                SetupCandidateRejectionStage.CATALOG,
                "El catálogo de ejercicios todavía no está disponible",
            )
        // Mismo equipo efectivo que candidatos y preview (helper de M7): nunca
        // se reinyecta `general_gym` ni se retira el modelo finito de inventario.
        val effectiveEquipment = effectiveEquipmentIds(draft)
        // Opciones del usuario ANTES de materializar: la autoregulación entra en
        // la base (PlanMaterializer la conserva sin mezclar) y los calentamientos
        // viajan como `defaultOptions` hasta el materializador, que da
        // precedencia a la elección/autor ya guardada sobre `options`.
        val options = draft.trainingOptions
        // C.P6: el programa se llama como su plan (ficha editorial) y toma el modo de su disciplina; antes salía como
        // «Plan de {nombre de la persona}», con el modo de hipertrofia por defecto aunque el método fuera de powerlifting.
        val base = options.applyTo(
            Program(
                id = draft.commitId,
                name = programNameFor(entry),
                mode = programModeFor(entry),
                startDay = draft.selectedWeekdays.minOrNull(),
                powerliftingProfile = draft.powerliftingProfile,
            ),
        )
        // Q5-F2 / §10.1: PHUL y PHAT originales y adaptados (entradas PROTOCOL con
        // `authoredSource`) NO existen en PROTOCOL_LIBRARY, que solo guarda las
        // versiones históricas `phul-verified`/`phat-verified`. Se preparan desde la
        // receta de la propia entrada con la MISMA función para candidatos, preview
        // y activación (original intacto; adaptación vía PlanAdaptationResolver).
        if (entry.source == CatalogSource.PROTOCOL && entry.authoredSource != null) {
            return materializeAuthoredPlan(entry, draft, catalog, base)
        }
        val program = when (entry.source) {
            CatalogSource.PROTOCOL -> {
                val protocol = PROTOCOL_LIBRARY.first { it.id == entry.sourceId }
                ProgramProtocolEngine.applyProtocol(
                    program = base.copy(selectedSplitId = protocol.defaultSplit),
                    protocol = protocol,
                    metadata = CatalogCompositionMetadataProvider.fromCatalog(catalog),
                    exerciseList = catalog.toLegacyConfigurationLookup().values.toList(),
                    defaultOptions = options,
                )
            }
            CatalogSource.TEMPLATE -> ProgramTemplateEngine.applyTemplate(
                base,
                requireNotNull(entry.template),
                forceReplace = true,
                defaultOptions = options,
            ).program
            CatalogSource.NATIVE -> error("Ruta nativa no válida")
        }.copy(id = draft.commitId)
        // Guardia de material real (helper de M7) DESPUÉS de aplicar la receta
        // fija y ANTES del preview: si la receta exige material no declarado, el
        // error es honesto en lugar de presentarla como compatible. Así los
        // candidatos viables se podan solos en `updateCandidates` (su `catch`
        // marca la entrada como no viable) sin afirmar compatibilidad falsa.
        val missingMaterial = missingFixedRecipeEquipment(program, effectiveEquipment, catalog)
        if (missingMaterial.isNotEmpty()) {
            // T-001 / AC-T001-02: etapa MATERIAL tipada. No todo rechazo es
            // material, pero ÉSTE sí lo es y se reporta como tal.
            throw PlanMaterializationException(
                PlanEvaluationStage.MATERIAL,
                apparatusReason(missingMaterial.sorted(), draft),
                "Esta receta necesita material que no has declarado: " +
                    missingMaterial.sorted().joinToString(", "),
                missingRequirements = missingMaterial.sorted(),
            )
        }
        val sessionDays = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.flatMap { it.sessions }.mapNotNull { it.dayOfWeek }.toSet()
        val frequency = sessionDays.size
        if (draft.daysPerWeek != null && frequency != draft.daysPerWeek) throw SetupCandidateFailureException(
            SetupCandidateRejectionStage.FREQUENCY,
            "La receta fija produce ${SpanishPlurals.days(frequency)}, no ${draft.daysPerWeek}",
        )
        val scheduled = program.copy(
            schedulePlan = ProgramSchedulePlan(
                weekStartDay = program.startDay,
                trainingDays = sessionDays,
            ),
        )
        // Sin copia posterior de `planWarmupConfig`: una vez materializada la
        // receta, reasignar la config no vuelve a aplicar los warmups (bug
        // confirmado). La resolución correcta ocurre EN la primera
        // materialización, dentro del materializador (elección/autor guardada >
        // `defaultOptions`), que es el que asigna los pasos por ejercicio.
        return SetupPreview(scheduled, null)
    }

    /**
     * Preparación de un plan de autor (§10.1, §13.4, §15.2). Traduce el borrador a
     * [AuthoredPlanRequest] y delega TODO en [AuthoredPlanMaterializer]: el equipo
     * es el efectivo REAL (`resolveEffectiveEquipment` con la disponibilidad
     * declarada, nunca `general_gym` reinyectado) y los fallos salen ya como
     * [PlanMaterializationException] con etapa y motivo cerrados.
     */
    private fun materializeAuthoredPlan(
        entry: CatalogEntry,
        draft: SetupWizardDraft,
        catalog: com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2,
        base: Program,
    ): SetupPreview {
        val options = draft.trainingOptions
        val program = AuthoredPlanMaterializer.prepare(
            AuthoredPlanRequest(
                entry = entry,
                baseProgram = base,
                equipment = options.resolveEffectiveEquipment(draft.equipment.map { it.catalogId }.toSet()),
                availability = options.availability,
                catalog = catalog,
                metadata = CatalogCompositionMetadataProvider.fromCatalog(catalog),
                options = options,
                expectedDaysPerWeek = draft.daysPerWeek,
                targetProfile = when (draft.goal) {
                    SetupGoal.STRENGTH_MUSCLE -> PlanTargetProfile.POWERBUILDING
                    SetupGoal.MUSCLE -> PlanTargetProfile.HYPERTROPHY
                    else -> null
                },
            ),
        )
        return SetupPreview(program, null)
    }

    /**
     * Preparación nutricional REAL del alta: motor `SetupNutritionPreparation`
     * con el borrador enriquecido (vitales del wizard y grasa corporal ACTUAL
     * medida o estimada, nunca usada como meta), el programa previewado como
     * calendario y los ajustes del entorno con las vitales del borrador. El
     * planId se deriva del commitId, así que cada preview reutiliza el mismo
     * id estable en lugar de generar uno nuevo.
     */
    private fun prepareNutrition(draft: SetupWizardDraft): SetupNutritionPreparationResult? {
        if (!draft.includeNutrition) return null
        val wizard = draft.nutritionDraft ?: return null
        // Solo una fuente EXPLÍCITA (medida o estimación visual) enriquece el
        // borrador; «No lo sé» o fuente sin declarar no aportan grasa alguna.
        // Mismo rango que la validación del commit (0–100 %) para que la
        // nutrición y las observaciones corporales no diverjan.
        val currentBodyFat = draft.bodyFatPercent
            ?.takeIf { it.isFinite() && it in 0.0..100.0 }
            ?.takeIf {
                draft.bodyFatSource == SetupBodyFatSource.MEASURED ||
                    draft.bodyFatSource == SetupBodyFatSource.VISUAL_ESTIMATE
            }
        val enriched = wizard.copy(
            planId = draft.nutritionPlanId ?: draft.commitId,
            ageText = wizard.ageText.ifBlank { draft.ageYears?.toString().orEmpty() },
            heightText = wizard.heightText.ifBlank { draft.heightCm?.toString().orEmpty() },
            weightText = wizard.weightText.ifBlank { draft.weightKg?.toString().orEmpty() },
            bodyFatText = wizard.bodyFatText.ifBlank {
                currentBodyFat?.let { String.format(java.util.Locale.ROOT, "%.1f", it) }.orEmpty()
            },
        )
        return SetupNutritionPreparation.prepare(SetupNutritionPreparationInput(
            draft = enriched,
            program = _state.value.programPreview,
            settings = settingsWithVitals(draft),
            today = LocalDate.now(),
        ))
    }

    /** Entorno con las vitales del borrador: las ecuaciones leen el alta, no el viejo Settings. */
    private fun settingsWithVitals(draft: SetupWizardDraft): Settings {
        val base = environment.settings
        val vitals = base.userVitals.copy(
            age = draft.ageYears ?: base.userVitals.age,
            height = draft.heightCm ?: base.userVitals.height,
            weight = draft.weightKg ?: base.userVitals.weight,
            gender = draft.profileGender ?: base.userVitals.gender,
            bodyFatPercentage = draft.bodyFatPercent ?: base.userVitals.bodyFatPercentage,
        )
        return base.copy(userVitals = vitals, age = draft.ageYears ?: base.age)
    }

    /** «Solo registro»: el modo explícito del borrador, nunca inferido. */
    private fun isTrackingOnly(draft: SetupWizardDraft): Boolean =
        draft.includeNutrition &&
            draft.nutritionDraft?.configurationMode == NutritionConfigurationMode.TRACKING_ONLY

    /**
     * Puerta de activación REAL: revisión de pasos + validación completa. No
     * depende del espejo conversacional (`wizChat.terminal` ni
     * `acceptedAnswers`): el estado de la ruta sale de `stepProgress` y de
     * [SetupWizardValidation.validateAll].
     */
    private fun reviewErrors(): Map<String, String> = buildMap {
        val s = _state.value; val d = s.draft
        val route = SetupStepGraph.stepIds(d.stepContext())
        if (SetupStepId.REVIEW_ACTIVATE !in route || d.stepProgress.currentStepId != SetupStepId.REVIEW_ACTIVATE) {
            put("flow", "Completa la revisión antes de activar")
        }
        // Revisión REAL del camino: cada paso se confirma (o llega confirmado
        // desde un borrador migrado). «Obligatorio» significa revisado, no
        // fabricar sensaciones ni historia: omitir con la opción explícita
        // también cuenta como revisado.
        val unreviewed = route.filterNot { step ->
            step == SetupStepId.REVIEW_ACTIVATE ||
                step in d.stepProgress.answers ||
                (SetupStepGraph.questionForStep(step)?.let { question ->
                    d.wizChat.acceptedAnswers.any { it.questionId == question }
                } == true)
        }
        unreviewed.firstOrNull()?.let { step ->
            val title = SetupStepDefinitions.of(step)?.title ?: "la revisión"
            put("review", "Falta revisar «$title» antes de activar")
        }
        if (SetupDraftCompatibility.pendingMandatoryVitals(d).isNotEmpty()) put("profile", "Completa tu edad, estatura y peso para continuar")
        val blocking = SetupWizardValidation.validateAll(d).firstOrNull { it.isBlocking }
        if (blocking != null) put(blocking.key, blocking.message ?: "Revisa tus respuestas antes de activar")
        if (d.includeTraining && d.goal?.isLegacyOnly == true) {
            put("goalReview", "Revisa tu objetivo anterior y elige explícitamente uno de los perfiles actuales")
        }
        // §15.4 / AC-T005-05: la activación exige que el preview preparado
        // corresponda a la huella COMPLETA actual (`previewKey` = material,
        // soportes, objetivos, días, minutos, cardio, split, calibración,
        // referencias, warmups/orden y revisiones). Una respuesta editada deja
        // la activación bloqueada hasta re-preparar; nunca se activa un
        // resultado de otras entradas.
        if (d.includeTraining && d.programRoute != SetupProgramRoute.LATER &&
            (s.programPreview == null || s.previewError != null || s.isPreviewLoading ||
                lastSuccessfulTrainingKey != previewKey(d))
        ) put("program", "Prepara una vista previa ejecutable con tus respuestas actuales")
        // D-005/E-015 guard de fuente en la puerta REAL de activación: bajo
        // PROTOCOL, una selección con fuente distinta de PROTOCOL bloquea el
        // commit (no puede generarse receipt) y exige cambio de ruta explícito.
        if (d.includeTraining && d.programRoute == SetupProgramRoute.PROTOCOL) {
            val selected = d.selectedCatalogId?.let(PersonalizedPlanCatalog::find)
            if (selected != null && selected.source != CatalogSource.PROTOCOL) {
                put("programSource", "Fuente equivocada para la ruta elegida: la ruta de protocolo solo acepta recetas de autor. Cambia de ruta de forma explícita para usar un plan nativo.")
            }
        }
        // Entreno v2: los minutos son los del estimador común sobre el programa ya armado y «¿cabe?» es la regla única
        // del asistente ([SessionTimeFit]: la misma tolerancia del 15 % con la que el barrido da por viable un plan de
        // autor); por encima de ella la receta no cabe en el tiempo pedido. Entre lo pedido y la tolerancia se pide
        // confirmar la duración real, igual que cuando la receta trae otros días.
        val longest = s.programSessionMinutes
        if (isFixedRecipe(d.selectedCatalogId) && longest != null &&
            !SessionTimeFit.fits(d.requestedSessionMinutes(), longest, generated = false)
        ) {
            put("time", "Esta receta supera los ${d.requestedSessionMinutes()} minutos por sesión; elige otra o ajusta el tiempo")
        }
        if (s.fixedRecipeDiffers() && !d.acceptFixedRecipeDifference) put("schedule", "Confirma la rotación y la duración reales de la receta")
        // Solo registro = sin plan y sin metas; no se exige una preparación que
        // el modo rechaza explícitamente.
        if (d.includeNutrition && !isTrackingOnly(d) && (s.nutritionPlanPreview == null || s.nutritionErrors.isNotEmpty())) {
            put("nutrition", s.nutritionErrors.values.firstOrNull() ?: "Completa la nutrición")
        }
        if (ringsMapping(d).savesRealCheckIn && (s.ringsBatteriesPreview == null || s.ringsPreviewLoading || s.ringsPreviewError != null || lastRingsPreviewKey != ringsKey(d))) {
            // Causa real de RINGS primero (clase+mensaje del último fallo de
            // este cálculo): nunca un genérico cuando existe diagnóstico, ni el
            // fallo de otra operación colado bajo esta clave.
            val ringsCause = s.lastFailure?.takeIf { it.startsWith(RINGS_FAILURE_PREFIX) }
            put("rings", ringsCause ?: s.ringsPreviewError ?: "Espera a que la vista previa de RINGS esté lista")
        }
        if (activationConfirmation(d, s.programPreview) && !d.confirmActivation) put("activation", "Confirma la activación del plan")
    }
    private fun activationConfirmation(draft: SetupWizardDraft, preview: Program?): Boolean = (draft.activateProgram && preview != null && environment.activeProgramId()?.let { it != preview.id } == true) || (draft.includeNutrition && draft.activateNutrition && environment.activeNutritionPlanId()?.let { it != (draft.nutritionPlanId ?: draft.commitId) } == true)

    private fun buildSettingsPatch(
        base: Settings,
        draft: SetupWizardDraft,
        program: Program?,
        nutrition: NutritionPlan?,
        rings: SetupRingsMapping,
        trackingOnly: Boolean,
    ): SetupSettingsPatch {
        // Evidencia REAL de que el usuario escribió el dato: el paso declarado.
        // Los borradores migrados conservan el espejo legacy como alternativa;
        // un valor nunca se declara solo por existir en el borrador.
        val declared = draft.declaredSteps
        val legacyProvided = draft.wizChat.acceptedAnswers
            .filter { it.source != WizChatAnswerSource.OMITTED }
            .map { it.questionId }.toSet()
        val legacyAccepted = draft.wizChat.acceptedAnswers.map { it.questionId }.toSet()
        fun provided(step: SetupStepId, question: WizChatQuestionId): Boolean =
            step in declared || question in legacyProvided
        val age = draft.ageYears ?: parseLocalizedNumber(draft.nutritionDraft?.ageText.orEmpty())?.toInt()
        // Procedencia ORDENADA de la disponibilidad categórica. Sin estado nuevo:
        // solo se leen marcas que el borrador ya lleva.
        // 1) El paso AVAILABILITY —el dueño real del material— está confirmado:
        //    declarado, o sugerencia aceptada con Continuar. Se persisten las
        //    categorías vivas EXACTAS, vacío incluido (solo peso corporal), y un
        //    vacío explícito nunca se confunde con «sin dato». Un simple
        //    prefill, una selección sin confirmar o un DERIVED/ENGINE_RESULT no
        //    son confirmaciones: no se autofirman.
        // 2) La ruta «Sin material» ni siquiera pregunta AVAILABILITY, así que
        //    su vacío explícito se persiste cuando el propio EQUIPMENT está
        //    declarado o confirmado y el entorno lo dice de forma literal. Un
        //    entorno nulo, en blanco o desconocido NO es «sin material» y jamás
        //    autoriza un vacío. Nunca se fabrica una respuesta legacy.
        // 3) Un borrador legacy sin ninguna señal del flujo actual conserva su
        //    HOME_EQUIPMENT: environment sin elegir y sin marcas de
        //    EQUIPMENT/AVAILABILITY (declaración, respuesta o selección).
        // 4) Cualquier otro caso, incluido `live == null`, es Unchanged: ni
        //    autofirmación de una semilla, ni borrado por accidente.
        val liveAvailability = draft.trainingOptions.availability
        val availabilityConfirmed = draft.isStepDeclared(SetupStepId.AVAILABILITY) ||
            draft.stepProgress.answers[SetupStepId.AVAILABILITY]?.canPersistAsDeclared() == true
        val noMaterialConfirmed = draft.trainingEnvironment in NO_MATERIAL_ENVIRONMENTS &&
            liveAvailability == EquipmentAvailability(emptySet()) &&
            (draft.isStepDeclared(SetupStepId.EQUIPMENT) ||
                draft.stepProgress.answers[SetupStepId.EQUIPMENT]?.canPersistAsDeclared() == true)
        val legacyHomeEquipment = draft.isStepDeclared(SetupStepId.HOME_EQUIPMENT) ||
            draft.stepProgress.answers[SetupStepId.HOME_EQUIPMENT]?.canPersistAsDeclared() == true
        val hasCurrentEquipmentTrace = SetupStepId.EQUIPMENT in declared ||
            draft.stepProgress.answers[SetupStepId.EQUIPMENT] != null ||
            draft.stepSelections[SetupStepId.EQUIPMENT] != null
        val hasCurrentAvailabilityTrace = SetupStepId.AVAILABILITY in declared ||
            draft.stepProgress.answers[SetupStepId.AVAILABILITY] != null ||
            draft.stepSelections[SetupStepId.AVAILABILITY] != null
        val legacyOnlyAuthorization = legacyHomeEquipment &&
            draft.trainingEnvironment.isNullOrBlank() &&
            !hasCurrentEquipmentTrace && !hasCurrentAvailabilityTrace
        val equipmentAvailabilityConfirmed =
            availabilityConfirmed || noMaterialConfirmed || legacyOnlyAuthorization
        val declaredName = draft.name.takeIf {
            it.isNotBlank() && provided(SetupStepId.NAME, WizChatQuestionId.P_NAME)
        }
        val goals = nutrition?.takeIf { draft.activateNutrition }
        // La unidad de peso de la app: la que eligió la persona para su peso corporal o, si no la tocó, la de sus marcas (quien declara
        // su sentadilla en libras ve las cargas de su programa en libras). La del peso corporal manda si las dos se tocaron.
        val weightUnitField: SetupPatchField<WeightUnit> = when {
            draft.weightUnitChanged -> SetupPatchField.Set(if (draft.weightUnit == "lb") WeightUnit.LBS else WeightUnit.KG)
            draft.marksUnit == "lb" && draft.isStepDeclared(SetupStepId.TRAINING_MAX) -> SetupPatchField.Set(WeightUnit.LBS)
            else -> SetupPatchField.Unchanged
        }
        // SET/CLEAR/UNCHANGED decididos por el mapper: una calibración parcial deja
        // initialRecoveryEvidence sin tocar (Unchanged) y PRESERVE no rejuvenece nada.
        val evidence = rings.toEvidencePatchField()
        val trackingChoice: NutritionTrackingChoice? = when {
            goals != null -> NutritionTrackingChoice.ENABLED
            trackingOnly -> null
            !draft.includeNutrition && !SetupPendingNutrition.shouldPreserve(draft) &&
                (provided(SetupStepId.NUTRITION_START, WizChatQuestionId.N_START) ||
                    WizChatQuestionId.N_START in legacyAccepted) &&
                environment.activeNutritionPlanId() == null -> NutritionTrackingChoice.SKIPPED
            else -> null
        }
        return SetupSettingsPatch(
            username = setOrKeep(declaredName),
            age = setIf(age, provided(SetupStepId.AGE, WizChatQuestionId.P_AGE) && age != null),
            vitalsPatch = SetupUserVitalsPatch(
                age = setIf(age, provided(SetupStepId.AGE, WizChatQuestionId.P_AGE) && age != null),
                height = setIf(draft.heightCm, provided(SetupStepId.HEIGHT, WizChatQuestionId.P_HEIGHT) && draft.heightCm != null),
                weight = setIf(draft.weightKg, provided(SetupStepId.WEIGHT, WizChatQuestionId.P_WEIGHT) && draft.weightKg != null),
                gender = setIf(draft.profileGender, provided(SetupStepId.GENDER, WizChatQuestionId.P_GENDER) && draft.profileGender != null),
            ),
            weightUnit = weightUnitField,
            dailyCalorieGoal = setIf(goals?.calorieTarget, goals != null),
            dailyProteinGoal = setIf(goals?.proteinGoal, goals != null),
            dailyCarbGoal = setIf(goals?.carbGoal, goals != null),
            dailyFatGoal = setIf(goals?.fatGoal, goals != null),
            onboardingCompleted = setIf(
                true,
                draft.draftScope == "full" &&
                    _state.value.mode in setOf(SetupWizardMode.FULL, SetupWizardMode.RESUME),
            ),
            onboardingNameDone = setIf(true, declaredName != null),
            // El tipo de atleta (capacidad de fatiga de AUGE) sale del perfil de objetivo, y solo cuando GOAL se
            // respondió en este alta ([athleteTypeToPersist]).
            athleteType = setOrKeep(draft.athleteTypeToPersist()),
            onboardingProgramDone = setIf(
                true,
                _state.value.mode in setOf(SetupWizardMode.FULL, SetupWizardMode.RESUME, SetupWizardMode.TRAINING_ONLY) &&
                    (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER || program != null),
            ),
            onboardingNutritionDone = setIf(
                true,
                _state.value.mode in setOf(SetupWizardMode.FULL, SetupWizardMode.RESUME, SetupWizardMode.NUTRITION_ONLY) &&
                    (!draft.includeNutrition || nutrition != null || trackingOnly),
            ),
            nutritionTrackingChoice = setOrKeep(trackingChoice),
            initialRecoveryEvidence = evidence,
            volumeCalibrationProfile = setIf(draft.volumeCalibrationProfile, draft.volumeCalibrationProfile != null),
            // Inventario declarado en el wizard (`trainingOptions.inventory`);
            // sin datos se conserva el actual, nunca se limpia por accidente.
            equipmentInventory = setIf(draft.trainingOptions.inventory, draft.trainingOptions.inventory != null),
            nutritionTrackingOnly = setIf(true, trackingOnly),
            // Un sugerido solo se guarda después de confirmarse: confirmCurrentStep
            // deja su procedencia en stepProgress sin convertirlo en declarado.
            // La autorización la da el paso AVAILABILITY (o la ruta «Sin material»
            // confirmada); HOME_EQUIPMENT solo sobrevive en borradores legacy sin
            // ninguna marca del flujo actual.
            equipmentAvailability = if (equipmentAvailabilityConfirmed) {
                setOrKeep(liveAvailability)
            } else {
                SetupPatchField.Unchanged
            },
        )
    }

    /**
     * `Set` cuando hay valor; `Unchanged` conserva lo actual. El tipo de
     * retorno es explícito a propósito: construir `SetupSettingsPatch` con
     * `if (…) Set(…) else Unchanged` degrada la inferencia en cascada.
     */
    private fun <T> setOrKeep(value: T?): SetupPatchField<T> =
        if (value == null) SetupPatchField.Unchanged else SetupPatchField.Set(value)

    /**
     * `Set` solo cuando [present]; si no, `Unchanged`. Pensado para campos
     * cuyo tipo ya es anulable (`SetupPatchField<Int?>`,
     * `SetupPatchField<EquipmentInventory?>`): un dato sin declarar nunca se
     * escribe como `Set(null)`.
     */
    private fun <T> setIf(value: T, present: Boolean): SetupPatchField<T> =
        if (present) SetupPatchField.Set(value) else SetupPatchField.Unchanged

    private fun ringsMapping(draft: SetupWizardDraft, nowEpochMs: Long = System.currentTimeMillis()): SetupRingsMapping {
        val a = draft.ringsAnswers ?: return SetupRingsMapping(RingsCompletion.UNKNOWN)
        return SetupRingsMapper.map(SetupRingsInput(
            startAction = a.startAction,
            recentTraining = a.recentTraining,
            recentTrainingUnknown = a.recentTrainingState == SetupRecentTrainingState.UNKNOWN,
            sessions = a.sessionsLastSevenDays,
            recencyDays = a.lastSessionRecencyDays ?: a.recencyDays,
            activityType = a.activityType,
            intensity = a.intensityLevel,
            muscleFeeling = a.muscleFeeling,
            energy = a.energy,
            structureFeeling = a.structureFeeling,
            axialState = a.axialExposure.state,
            axialSessions = a.axialExposure.sessions,
            axialIntensity = a.intensityLevel,
            muscleScope = a.muscleScope,
            selectedMuscles = a.recentMuscles.toList(),
            discomfortIds = a.discomfortIds,
            capturedAtMs = a.capturedAtMs,
            // NONE (lista vacía explícita) es distinto de no informado/omitido.
            discomfortResponse = when (a.discomfortState) {
                SetupDiscomfortState.NONE -> RingsDiscomfortResponse.NONE
                SetupDiscomfortState.DECLARED -> RingsDiscomfortResponse.DECLARED
                SetupDiscomfortState.OMITTED -> RingsDiscomfortResponse.OMITTED
                SetupDiscomfortState.NOT_ANSWERED -> RingsDiscomfortResponse.NOT_ANSWERED
            },
        ), nowEpochMs)
    }
    private fun buildVolumeProfile(draft: SetupWizardDraft): VolumeCalibrationProfile? {
        val a = draft.volumeAnswers; val style = a.style ?: return null; val t = a.technique ?: return null; val c = a.consistency ?: return null; val s = a.strength ?: return null; val m = a.mobility ?: return null; val output = VolumeCalibrationEngine.calculate(style, t, c, s, m)
        return VolumeCalibrationProfile(style, output.score, VolumeCalibrationResponses(t, c, s, m, a.responseState), output.recommendations, System.currentTimeMillis(), VolumeCalibrationEngine.REVISION)
    }

    private fun newDraft(mode: SetupWizardMode, nutritionMode: String, nutritionPlanId: String?, id: String): SetupWizardDraft {
        val settings = environment.settings
        val training = mode != SetupWizardMode.NUTRITION_ONLY && mode != SetupWizardMode.RINGS_ONLY
        val nutrition = mode != SetupWizardMode.TRAINING_ONLY && mode != SetupWizardMode.RINGS_ONLY
        val first = WizChatGraph.firstFor(WizChatGraphContext(training, nutrition))
        val progress = WizChatProgress(scriptVersion = WizChatCopyCatalog.SCRIPT_VERSION,
            draftScope = scopeFor(mode).name.lowercase(), currentQuestionId = first, stage = WizChatGraph.stageFor(first))
        val unit = if (settings.weightUnit == WeightUnit.LBS) "lb" else "kg"
        return SetupWizardDraft(draftId = id, commitId = UUID.randomUUID().toString(), draftScope = scopeFor(mode).name.lowercase(), name = settings.username.takeIf { it != "Usuario" }.orEmpty(), moduleChoice = if (nutrition) SetupModuleChoice.TRAINING_AND_NUTRITION else SetupModuleChoice.TRAINING, ageYears = settings.userVitals.age ?: settings.age, heightCm = settings.userVitals.height, weightKg = settings.userVitals.weight, importedWeightKg = settings.userVitals.weight, importedBodyFatPercent = settings.userVitals.bodyFatPercentage, weightUnit = unit, includeTraining = training, includeNutrition = nutrition, programRoute = if (training) SetupProgramRoute.CUSTOMIZABLE else SetupProgramRoute.LATER, trainingPath = if (training) SetupTrainingPath.PERSONALIZE else null, nutritionMode = nutritionMode, nutritionPlanId = nutritionPlanId, nutritionDraft = if (nutrition) NutritionWizardDraft(mode = nutritionMode, planId = nutritionPlanId, weightUnit = unit) else null, catalogRevision = PersonalizedPlanCatalog.REVISION, wizChat = progress,
            trainingOptions = SetupTrainingOptions(availability = settings.equipmentAvailability))
            .let { draft -> draft.copy(stepProgress = SetupStepProgress.initial(draft.stepContext())) }
    }
    private fun normalizeProgress(progress: WizChatProgress, scope: String, mode: SetupWizardMode): WizChatProgress {
        val missingGender = SetupDraftResolver.scopeOf(scope) == SetupDraftScope.FULL &&
            progress.schemaVersion == 1 && progress.acceptedAnswers.none { it.questionId == WizChatQuestionId.P_GENDER } &&
            progress.currentQuestionId != WizChatQuestionId.P_NAME
        val next = if (missingGender) WizChatQuestionId.P_GENDER else progress.currentQuestionId
        val safeQuestion = next.takeIf { WizChatGraph.question(it) != null } ?: WizChatQuestionId.P_NAME
        return progress.copy(schemaVersion = 2, scriptVersion = WizChatCopyCatalog.SCRIPT_VERSION,
            draftScope = scope, currentQuestionId = safeQuestion, stage = WizChatGraph.stageFor(safeQuestion))
    }
    private fun scopeFor(mode: SetupWizardMode): SetupDraftScope = when (mode) { SetupWizardMode.TRAINING_ONLY -> SetupDraftScope.TRAINING_ONLY; SetupWizardMode.NUTRITION_ONLY -> SetupDraftScope.NUTRITION_ONLY; SetupWizardMode.RINGS_ONLY -> SetupDraftScope.RINGS_ONLY; else -> SetupDraftScope.FULL }
    private fun SetupFocus.toTrainingFocus() = TrainingFocus.valueOf(name)
    private fun SetupExperience?.toCatalogLevel() = when (this) { SetupExperience.ADVANCED -> CatalogLevel.ADVANCED; SetupExperience.INTERMEDIATE -> CatalogLevel.INTERMEDIATE; else -> CatalogLevel.BEGINNER }
    private val SetupEquipment.catalogId: String get() = when (this) { SetupEquipment.NONE, SetupEquipment.BODYWEIGHT -> "bodyweight"; SetupEquipment.BANDS -> "band"; SetupEquipment.DUMBBELLS -> "dumbbells"; SetupEquipment.MACHINE -> "machine"; SetupEquipment.CABLE -> "cable"; SetupEquipment.BARBELL -> "barbell"; SetupEquipment.PULL_UP -> "pull_up_bar"; SetupEquipment.GYM -> "general_gym"; SetupEquipment.SUPPORT -> "support"; SetupEquipment.BALL -> "ball"; SetupEquipment.SMITH -> "smith_machine" }
    private fun com.example.kpkn.data.models.GoalMetric.toBodyMetric() = when (this) { GoalMetric.WEIGHT -> BodyMetric.WEIGHT; GoalMetric.BODY_FAT -> BodyMetric.BODY_FAT_PERCENT; GoalMetric.MUSCLE_MASS -> BodyMetric.MUSCLE_MASS_PERCENT }

    companion object {
        private const val DRAFT_ID_KEY = "setup_wizard_draft_id"
        /** Plan de la biblioteca ya aplicado como intención (SavedStateHandle: sobrevive a recrear el ViewModel). */
        private const val PRESELECTED_PLAN_KEY = "setup_wizard_preselected_plan_id"
        /** Veredictos de sondeo que guarda la caché aparte del asesor de reparaciones (cada uno ocupa casi nada). */
        private const val REPAIR_PROBE_CACHE_ENTRIES = 8
        /** Rutinas «a medida» que se recuerdan para el revelado (una por perfil y respuestas; pocas bastan). */
        private const val GENERATED_ROUTINE_MEMO_ENTRIES = 8
        /** Minutos por sesión con los que se arma un plan propio (el personalizador no admite más). */
        private const val OWN_PLAN_MAX_MINUTES = 100
        /**
         * Únicos valores de entorno que el reductor de EQUIPMENT acepta como
         * «Sin material». Lista positiva a propósito: un entorno nulo, en blanco
         * o desconocido NUNCA autoriza persistir una disponibilidad vacía.
         */
        private val NO_MATERIAL_ENVIRONMENTS = setOf("none", "Sin material")
        /** Etiqueta de logcat de diagnóstico: solo clase+mensaje, nunca datos del usuario. */
        private const val DIAG_TAG = "SetupWizard"
        /** Largo máximo de la causa cruda de un rechazo en el registro. */
        private const val DIAG_REASON_MAX = 200
        /** Etiqueta de logcat de la medición del barrido de candidatos (C4): `adb logcat -s SetupPlanSweep`. */
        private const val PERF_TAG = "SetupPlanSweep"
        /** Marca en `lastFailure` del fallo de RINGS; permite limpiarlo sin pisar otros fallos. */
        private const val RINGS_FAILURE_PREFIX = "Rings preview"
    }
}

private fun List<SetupSessionDraft>.ensureSession(weekday: Int): List<SetupSessionDraft> = if (any { it.weekday == weekday }) this else this + SetupSessionDraft(weekday, "Sesión del día $weekday")

/** Paquete A · E1: razón de una tarjeta de plan KPKN (nativo) cuando la bolsa de prioridades trae puntos. */
internal const val ORDER_PRIORITIES_NATIVE_REASON = "Tus prioridades ordenan los ejercicios de cada día"

/** Paquete A · E1: razón de una tarjeta de método (receta de autor o de plantilla) cuando la bolsa trae puntos. */
internal const val ORDER_PRIORITIES_RECIPE_FIXED_REASON = "Conserva el orden del método"

/**
 * Paquete A · E1 (D6): razón de tarjeta sobre la bolsa de prioridades de orden, solo cuando la bolsa trae algún
 * punto. Un plan KPKN (nativo) ordena con ella los ejercicios de cada día; un método de autor o una plantilla con
 * receta fija su orden (`OrderOwnership.RECIPE_FIXED`) y la bolsa no lo toca. Las plantillas sin receta no
 * declaran nada: no hay orden de método que conservar ni plan generado que se ordene.
 */
internal fun planOrderPriorityReason(entry: CatalogEntry, bag: Map<String, Int>): String? = when {
    bag.values.none { points -> points > 0 } -> null
    entry.source == CatalogSource.NATIVE -> ORDER_PRIORITIES_NATIVE_REASON
    (entry.recipe ?: entry.template?.recipe) != null -> ORDER_PRIORITIES_RECIPE_FIXED_REASON
    else -> null
}

/**
 * Paquete A · D3 (B-06): texto llano de un catálogo de ejercicios que no quedó listo. Es el motivo visible
 * del rechazo CATALOG y el aviso de la búsqueda; el estado crudo del repositorio solo va al registro.
 */
internal const val CATALOG_UNAVAILABLE_MESSAGE = "No pudimos cargar el catálogo de ejercicios. Reintenta."

/** Paquete A · D2 (B-01): aviso de «Continuar» en el paso PLAN sin una selección viable de la lista vigente. */
internal const val PLAN_SELECTION_REQUIRED_MESSAGE = "Elige un plan de la lista para continuar"

/**
 * H9: aviso de «Continuar» en el paso PLAN mientras la lista de planes se está calculando. Con la lista en carga no
 * se puede decir «elige un plan de la lista» (la lista todavía no está): se explica la espera y se pide repetir.
 */
internal const val PLAN_CANDIDATES_LOADING_MESSAGE =
    "Estamos revisando los planes; vuelve a tocar Continuar en un momento"

/** Nombre de reserva de una selección caída cuyo id ya no resuelve en el catálogo (nunca se pinta el id crudo). */
private const val DROPPED_SELECTION_FALLBACK_TITLE = "Tu plan elegido"

/**
 * Paquete A · D2 (B-01): puerta de «Continuar» en el paso PLAN. Con un plan del catálogo elegido exige que esa
 * selección esté entre los candidatos VIABLES de la lista vigente (`availablePlanCandidates`, la lista entera,
 * no solo las tarjetas visibles) y que la lista no esté calculándose; si no, devuelve el error de paso
 * (`errors["plan"]`) y el paso no avanza. Es una función pura para poder probarla sin montar el ViewModel.
 *
 * Con la lista calculándose el aviso es propio ([PLAN_CANDIDATES_LOADING_MESSAGE], H9): no es que el plan no
 * sirva, es que todavía no se sabe. Con la lista lista y la selección fuera de ella vale
 * [PLAN_SELECTION_REQUIRED_MESSAGE].
 *
 * No añade nada cuando la validación del paso ya habla por sí sola (sin plan elegido), ni en «lo haré más
 * adelante», ni en la ruta «desde cero», que no usan candidatos.
 */
internal fun planSelectionGate(state: SetupWizardState, step: SetupStepId): Map<String, String> {
    if (step != SetupStepId.PLAN) return emptyMap()
    val draft = state.draft
    if (draft.programRoute == SetupProgramRoute.LATER) return emptyMap()
    if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) return emptyMap()
    val selected = draft.selectedCatalogId ?: return emptyMap()
    if (state.isCandidateLoading) return mapOf("plan" to PLAN_CANDIDATES_LOADING_MESSAGE)
    val viable = state.availablePlanCandidates.any { it.id == selected }
    return if (viable) emptyMap() else mapOf("plan" to PLAN_SELECTION_REQUIRED_MESSAGE)
}

/** Tarjetas de plan que se ven al principio de la lista (el resto sale con «Ver más opciones»). */
internal const val INITIAL_VISIBLE_CANDIDATES = 3

/** Cuántas tarjetas más suma cada toque de «Ver más opciones». */
private const val CANDIDATES_PAGE_SIZE = 3

/**
 * H3: las tarjetas visibles de la lista [available] (el orden del planificador no cambia): las primeras
 * [minimumVisible] y, si el plan elegido [selectedId] es viable pero está más allá, las que hagan falta para incluirlo.
 * Así el plan elegido —o el que la persona trajo de la biblioteca— nunca queda escondido detrás de «Ver más
 * opciones» y no hay que ir a buscarlo para ver que está marcado. Sin selección, o con una que no está en la lista
 * (no es viable), son las [minimumVisible] primeras.
 */
internal fun visibleCandidatesFor(
    available: List<SetupPlanCandidate>,
    selectedId: String?,
    minimumVisible: Int = INITIAL_VISIBLE_CANDIDATES,
): List<SetupPlanCandidate> {
    val selectedIndex = selectedId?.let { id -> available.indexOfFirst { candidate -> candidate.id == id } } ?: -1
    return available.take(maxOf(minimumVisible, selectedIndex + 1))
}

/**
 * H10: el texto que la persona lee cuando el preview falla. El mensaje crudo del motor (`error.message`: ids de
 * configuración, tokens, nombres de clases) va solo al registro. El catálogo que no quedó listo se dice con el texto
 * del presentador único ([PlanRejectionPresenter.CATALOG_TEXT]) y un fallo interno —composición, materialización,
 * configuración sin resolver, base de carga, o cualquier error sin tipar— con [PlanRejectionPresenter.INTERNAL_TEXT].
 * Los rechazos que la persona puede arreglar (material, tiempo, perfil, frecuencia…) usan el mismo presentador
 * que la lista: el motivo y los valores estructurados se conservan, los tokens e ids del mensaje no se pintan.
 */
internal fun previewFailureText(error: Throwable, draft: SetupWizardDraft? = null): String {
    val typed = error as? PlanMaterializationException ?: return PlanRejectionPresenter.INTERNAL_TEXT
    return when (typed.reason) {
        PlanRejectionReason.CATALOG_NOT_READY -> PlanRejectionPresenter.CATALOG_TEXT
        PlanRejectionReason.UNRESOLVED_CONFIGURATION,
        PlanRejectionReason.INTERNAL_MATERIALIZATION,
        PlanRejectionReason.COMPOSITION,
        PlanRejectionReason.LOAD_BASIS_UNREPRESENTABLE -> PlanRejectionPresenter.INTERNAL_TEXT
        PlanRejectionReason.RECIPE_UNAVAILABLE,
        PlanRejectionReason.PROFILE_MISMATCH,
        PlanRejectionReason.LEVEL_UNSUITABLE,
        PlanRejectionReason.FREQUENCY,
        PlanRejectionReason.SPLIT,
        PlanRejectionReason.APPARATUS_UNKNOWN,
        PlanRejectionReason.APPARATUS_ABSENT,
        PlanRejectionReason.NO_VALID_SUBSTITUTION,
        PlanRejectionReason.TIME_BUDGET -> {
            val key = if (typed.reason == PlanRejectionReason.APPARATUS_UNKNOWN) {
                typed.missingRequirements.firstNotNullOfOrNull { token ->
                    PlanRepairAdvisor.confirmableKeyFor(token, draft?.trainingOptions?.availability)
                }
            } else null
            PlanRejectionPresenter.present(
                RejectionView(
                    planId = draft?.selectedCatalogId,
                    reasonCode = typed.reason,
                    requiredMinutes = typed.requiredMinutes,
                    missingRequirements = typed.missingRequirements,
                    apparatusKey = key,
                    needsApparatusConfirmation = key != null,
                ),
                draft?.let(::presentationContextOf) ?: PresentationContext(),
            ).text
        }
    }
}

/**
 * Paquete A · D4 (B-07, DEC-w2-05): ¿se intenta el segundo pase a peso corporal? Solo cuando el pase pedido no
 * dejó NINGÚN plan viable y TODOS sus rechazos son de material (`APPARATUS_UNKNOWN` o `APPARATUS_ABSENT`).
 * Sin rechazos no hay nada que explicar con el material (p. ej. el catálogo no se evaluó), y un rechazo de otra
 * clase (tiempo, perfil, composición, fallo interno, sustitución sin resolver…) significa que el material no es
 * la causa: la persona debe ver ese motivo, no un plan «adaptado a peso corporal» que no lo resuelve. Un
 * rechazo heredado sin código (`reasonCode == null`) tampoco habilita el pase.
 */
internal fun bodyweightPassAllowed(requestedViableCount: Int, rejections: List<SetupCandidateRejection>): Boolean =
    requestedViableCount == 0 && rejections.isNotEmpty() && rejections.all { rejection ->
        rejection.reasonCode == PlanRejectionReason.APPARATUS_UNKNOWN ||
            rejection.reasonCode == PlanRejectionReason.APPARATUS_ABSENT
    }

// ─── Paquete A · C3 y Paquete C · C.P6: reparaciones, plan propio y preselección (funciones puras) ─────────────

/**
 * Perfil de objetivo del asistente para el objetivo del borrador. Sin objetivo o con «Salud» (legacy) vale
 * [PlanGoalProfile.LEGACY_HEALTH]: ninguno de los dos tiene plan propio.
 */
internal fun planGoalProfileOf(goal: SetupGoal?): PlanGoalProfile = when (goal) {
    SetupGoal.STRENGTH -> PlanGoalProfile.STRENGTH
    SetupGoal.MUSCLE -> PlanGoalProfile.MUSCLE
    SetupGoal.STRENGTH_MUSCLE -> PlanGoalProfile.STRENGTH_MUSCLE
    // Funcional y saludable se sirve, de momento, con el plan propio de Atleta completo (fuerza, potencia y cardio).
    SetupGoal.COMPLETE_ATHLETE, SetupGoal.FUNCTIONAL -> PlanGoalProfile.COMPLETE_ATHLETE
    SetupGoal.MIXED -> PlanGoalProfile.LEGACY_MIXED
    // Las cuatro disciplinas nuevas no tienen plan propio hasta que las sirva el generador nuevo: sin disciplina que
    // filtre (la referencia sale del estilo de calibración del perfil) y sin reparaciones de un toque.
    SetupGoal.HEALTH, SetupGoal.CALISTHENICS, SetupGoal.WEIGHTLIFTING, SetupGoal.ARMWRESTLING,
    SetupGoal.STRONGMAN, null -> PlanGoalProfile.LEGACY_HEALTH
}

/** Objetivo del borrador que ofrece el asistente para este perfil; null para los legacy, que nunca se ofrecen de nuevo. */
internal fun PlanGoalProfile.toSetupGoal(): SetupGoal? = when (this) {
    PlanGoalProfile.STRENGTH -> SetupGoal.STRENGTH
    PlanGoalProfile.MUSCLE -> SetupGoal.MUSCLE
    PlanGoalProfile.STRENGTH_MUSCLE -> SetupGoal.STRENGTH_MUSCLE
    PlanGoalProfile.COMPLETE_ATHLETE -> SetupGoal.COMPLETE_ATHLETE
    PlanGoalProfile.LEGACY_MIXED, PlanGoalProfile.LEGACY_HEALTH -> null
}

/**
 * Valor estable de la opción del paso GOAL que lleva a los planes de este objetivo. Entreno v2: es el perfil que ofrece
 * su plan propio y los de autor (Fuerza → `powerlifting`, Músculo → `bodybuilding`, Fuerza y músculo → `powerbuilding`);
 * «Fuerza y masa muscular» es un perfil general y solo ofrece su programa «a medida», así que una reparación o una
 * preselección de la biblioteca nunca lleva ahí. Atleta completo → `strength_cardio` (el perfil general que lo
 * sustituye). Null para los legacy, que nunca se ofrecen de nuevo.
 */
internal fun goalChoiceValueOf(goal: PlanGoalProfile): String? = when (goal) {
    PlanGoalProfile.STRENGTH -> TrainingGoalProfile.POWERLIFTING
    PlanGoalProfile.MUSCLE -> TrainingGoalProfile.BODYBUILDING
    PlanGoalProfile.STRENGTH_MUSCLE -> TrainingGoalProfile.POWERBUILDING
    PlanGoalProfile.COMPLETE_ATHLETE -> TrainingGoalProfile.STRENGTH_CARDIO
    PlanGoalProfile.LEGACY_MIXED, PlanGoalProfile.LEGACY_HEALTH -> null
}?.let(EntrenoStepValues::goalValue)

/**
 * El plan PROPIO de un objetivo (el que la persona espera ver): `native:strength-foundation-v2`,
 * `native:muscle-foundation-v2`, `native:powerbuilding-foundation-v2` o `native:complete-athlete-v2`. Los objetivos
 * legacy no tienen plan propio.
 */
internal fun ownPlanIdOf(goal: PlanGoalProfile): String? = when (goal) {
    PlanGoalProfile.STRENGTH -> NativeProfileKind.STRENGTH.entryId
    PlanGoalProfile.MUSCLE -> NativeProfileKind.MUSCLE.entryId
    PlanGoalProfile.STRENGTH_MUSCLE -> NativeProfileKind.POWERBUILDING.entryId
    PlanGoalProfile.COMPLETE_ATHLETE -> NativeProfileKind.COMPLETE_ATHLETE.entryId
    PlanGoalProfile.LEGACY_MIXED, PlanGoalProfile.LEGACY_HEALTH -> null
}

/**
 * Paso del asistente que se marca como declarado al aplicar [this]: el que la persona habría tocado a mano. El reparto
 * no se marca (quitarlo es volver al calendario propio del plan, no declarar nada).
 */
internal fun PlanRepair.declaredStep(): SetupStepId? = when (this) {
    is PlanRepair.SetMinutes -> SetupStepId.SESSION_TIME
    is PlanRepair.SetCardioMinutes -> SetupStepId.CARDIO_TIME
    is PlanRepair.ConfirmApparatus -> SetupStepId.AVAILABILITY
    is PlanRepair.SwitchGoal -> SetupStepId.GOAL
    PlanRepair.ClearSplit -> null
}

/**
 * Borrador con la reparación [repair] aplicada, con las MISMAS escrituras de la API de pasos que usaría la persona a
 * mano (`withStepNumber`, `withStepChoice`, la disponibilidad del panel de material). Es la única definición de qué
 * cambia cada reparación: la usan `applyRepairs` (producción) y el sondeo del asesor ([repairProbeDraft]), así lo que
 * se prueba es exactamente lo que después se aplica.
 */
internal fun SetupWizardDraft.withRepair(
    repair: PlanRepair,
    nowEpochMs: Long = System.currentTimeMillis(),
): SetupWizardDraft = when (repair) {
    // Los minutos exactos que probó el asesor (no se redondean al reloj de 5 en 5).
    is PlanRepair.SetMinutes -> withExactSessionMinutes(repair.minutes)
    is PlanRepair.SetCardioMinutes -> withStepChoice(SetupStepId.CARDIO_TIME, repair.minutes.toString(), nowEpochMs)
    is PlanRepair.ConfirmApparatus -> withConfirmedApparatus(repair)
    is PlanRepair.SwitchGoal -> withSwitchedGoal(repair, nowEpochMs)
    PlanRepair.ClearSplit -> withRecommendedSplit(nowEpochMs)
}

/**
 * Valor estable de la tarjeta «Recomendado» del paso SPLIT: sin reparto forzado, el motor ordena los días como mejor
 * encaje. Es el mismo valor que escribe esa tarjeta (`SetupTrainingSteps`, `selectSplit`).
 */
internal const val SPLIT_CHOICE_RECOMMENDED = "recommended"

/**
 * El borrador con el reparto retirado y escrito EXACTAMENTE como lo deja la tarjeta «Recomendado» del paso SPLIT:
 * la selección `recommended` del paso (`withStepChoice`), sin reparto elegido y sin el patrón ni el nombre del reparto
 * propio. Quitar el reparto con «Quitar el reparto» o por un cambio de objetivo (H4) deja así la misma huella que
 * hubiera dejado la persona; antes solo se ponía `selectedSplitId = null` y el paso SPLIT conservaba la selección y el
 * patrón del reparto retirado (un reparto propio seguía marcado y con su nombre).
 */
internal fun SetupWizardDraft.withRecommendedSplit(
    nowEpochMs: Long = System.currentTimeMillis(),
): SetupWizardDraft =
    withStepChoice(SetupStepId.SPLIT, SPLIT_CHOICE_RECOMMENDED, nowEpochMs)
        .copy(selectedSplitId = null, customSplitPattern = emptyList(), customSplitName = null)

/**
 * Confirma las llaves y las categorías de la reparación. Si la persona ya había marcado categorías en el paso de
 * material, su selección guardada las incluye también (así la tarjeta del paso y la disponibilidad dicen lo mismo);
 * la opción «solo peso corporal» se retira, porque ya no es verdad.
 */
private fun SetupWizardDraft.withConfirmedApparatus(repair: PlanRepair.ConfirmApparatus): SetupWizardDraft {
    val confirmed = repair.applyTo(trainingOptions.availability ?: EquipmentAvailability())
    return copy(
        trainingOptions = trainingOptions.copy(availability = confirmed),
        // La selección de material es la lectura inversa de la disponibilidad: ya no se guarda aparte.
        stepSelections = stepSelections - SetupStepId.AVAILABILITY,
    )
}

/**
 * Cambia el objetivo como lo haría el paso GOAL (con el estilo de volumen que infiere), sube los minutos si el destino
 * solo cabe con más tiempo y retira el reparto elegido, que era del objetivo anterior, como lo hace la tarjeta
 * «Recomendado» ([withRecommendedSplit], H4). Nunca va a Atleta completo.
 */
private fun SetupWizardDraft.withSwitchedGoal(repair: PlanRepair.SwitchGoal, nowEpochMs: Long): SetupWizardDraft {
    val value = goalChoiceValueOf(repair.goal) ?: return this
    val switched = withStepChoice(SetupStepId.GOAL, value, nowEpochMs)
    val timed = repair.alsoMinutes
        ?.let { minutes -> switched.withExactSessionMinutes(minutes) }
        ?: switched
    return timed.withRecommendedSplit(nowEpochMs)
}

/**
 * El borrador sobre el que se evalúa un sondeo del asesor: [source] con lo que el sondeo [probe] cambia respecto del
 * pedido (objetivo, minutos, cardio, reparto) y con el material [availability] que manda en el sondeo. Se construye
 * aplicando las mismas reparaciones de [withRepair], de modo que el borrador sondeado y el que dejará
 * `applyRepairs` tengan la misma huella de entradas.
 */
internal fun repairProbeDraft(
    source: SetupWizardDraft,
    probe: PlanCandidateRequest,
    availability: EquipmentAvailability,
): SetupWizardDraft {
    var draft = source
    if (probe.goalProfile != planGoalProfileOf(draft.goal)) {
        draft = draft.withRepair(PlanRepair.SwitchGoal(probe.goalProfile))
    }
    if (probe.minutesPerSession != draft.minutesPerSession) {
        draft = draft.withRepair(PlanRepair.SetMinutes(probe.minutesPerSession))
    }
    val cardio = probe.cardioMinutes
    if (probe.requiresCardio && cardio != null && cardio != draft.cardioMinutes) {
        draft = draft.withRepair(PlanRepair.SetCardioMinutes(cardio))
    }
    if (probe.selectedSplitId == null && draft.selectedSplitId != null) {
        draft = draft.withRepair(PlanRepair.ClearSplit)
    }
    return draft.copy(trainingOptions = draft.trainingOptions.copy(availability = availability))
}

/** Los cuatro objetivos que ofrece el asistente: los únicos que la preselección sabe prefijar. */
private val PRESELECTABLE_GOALS: List<PlanGoalProfile> = listOf(
    PlanGoalProfile.STRENGTH,
    PlanGoalProfile.MUSCLE,
    PlanGoalProfile.STRENGTH_MUSCLE,
    PlanGoalProfile.COMPLETE_ATHLETE,
)

/**
 * H1 (c): ¿puede el planificador ofrecer [entry] para ALGÚN objetivo del asistente y ALGUNA frecuencia que la entrada
 * admite? Se pregunta al propio [SetupTrainingPlanner] con el pedido que arma el barrido (referencia y capacidades
 * requeridas de cada objetivo), no a una regla paralela: una entrada que ahí no sale —las tres estructuras en blanco y
 * `native:strength-cardio`, cuyas `references` y `capabilities` no sirven a ningún objetivo— no puede llegar a ser un
 * candidato por mucho que se pida desde la biblioteca o desde un enlace con un id a mano.
 */
internal fun wizardCanOffer(entry: CatalogEntry): Boolean = PRESELECTABLE_GOALS.any { goal ->
    val reference = PlanRepairAdvisor.referenceOf(goal)
    val capabilities = PlanGoalMatcher.requiredCapabilities(goal)
    entry.supportedFrequencies.any { days ->
        days in 1..MAX_WIZARD_DAYS && SetupTrainingPlanner.candidates(
            SetupTrainingPlannerInput(
                reference = reference,
                frequency = days,
                equipment = setOf("general_gym"),
                level = CatalogLevel.BEGINNER,
                focus = TrainingFocus.FULL_BODY,
                requiredCapabilities = capabilities,
            ),
        ).any { candidate -> candidate.id == entry.id }
    }
}

/** Días por semana máximos que pregunta el asistente. */
private const val MAX_WIZARD_DAYS = 6

/**
 * H2 (b): el rechazo que explica por qué el planificador excluyó [entry] con las respuestas de [draft], para un plan
 * que el barrido ni evaluó. Mismo orden que los filtros del planificador: primero la frecuencia (el plan no admite los
 * días elegidos) y después la disciplina (el plan no sirve al objetivo elegido, [PlanGoalMatcher]). Null si ninguna de
 * las dos lo explica (la exclusión es de otra clase y el aviso queda en la forma general). El texto de `reason` solo
 * va al registro: la persona lee el del presentador.
 */
internal fun synthesizedRejectionFor(entry: CatalogEntry, draft: SetupWizardDraft): SetupCandidateRejection? {
    val days = draft.daysPerWeek
    if (days != null && days !in entry.supportedFrequencies) {
        return SetupCandidateRejection(
            planId = entry.id,
            stage = SetupCandidateRejectionStage.FREQUENCY,
            reason = "el plan admite ${entry.supportedFrequencies} días por semana y se eligieron $days",
            reasonCode = PlanRejectionReason.FREQUENCY,
        )
    }
    val goalProfile = planGoalProfileOf(draft.goal)
    if (!PlanGoalMatcher.matches(entry, goalProfile)) {
        return SetupCandidateRejection(
            planId = entry.id,
            stage = SetupCandidateRejectionStage.PROFILE,
            reason = "el plan no sirve al objetivo $goalProfile",
            reasonCode = PlanRejectionReason.PROFILE_MISMATCH,
        )
    }
    return null
}

/**
 * E-18 (C.P6) — el borrador con el plan [planId] de la biblioteca como INTENCIÓN, o null si no se puede (el id no
 * existe, la entrada no se ofrece, el planificador no la puede ofrecer para ningún objetivo y frecuencia
 * ([wizardCanOffer]) o el borrador no incluye entrenamiento).
 *
 *  - `selectedCatalogId` guarda la intención (r2 §15.2); no confirma el paso PLAN ni ningún otro: el asistente
 *    sigue preguntando todo y, al llegar a PLAN, la selección queda hecha si el plan es viable y, si no, cae con el
 *    aviso de selección caída.
 *  - El objetivo se prefija SIN confirmar cuando el plan sirve a UNO solo ([PlanGoalMatcher.matches]); un plan que
 *    casa con varios (PHUL: Músculo y Fuerza y músculo) no prefija nada y deja que la persona elija.
 *  - H2 (a): con el mismo criterio, los días por semana se prefijan SIN confirmar cuando el plan admite una sola
 *    frecuencia (los métodos y las plantillas con receta: «4 días»: se siembra una semana repartida de esos días);
 *    los planes de 1 a 6 días no prefijan nada.
 *  - H18: un paso que ya estaba CONFIRMADO (borrador restaurado) y cuyo valor cambia por la preselección —el objetivo,
 *    los días o el plan elegido— no se cambia en silencio: se deja marcado para revisar (`withPendingReview`) y su
 *    respuesta conserva el dato.
 *  - La ruta se normaliza como al elegir una tarjeta (personalizable) y se descarta «lo decidiré después»: la
 *    persona acaba de pedir configurar este plan.
 */
internal fun SetupWizardDraft.withPreselectedPlan(
    planId: String,
    nowEpochMs: Long = System.currentTimeMillis(),
): SetupWizardDraft? {
    if (!includeTraining) return null
    val entry = PersonalizedPlanCatalog.find(planId)
        ?.takeIf { it.listed && it.publication == PublicationState.PUBLISHED && wizardCanOffer(it) }
        ?: return null
    val servedGoal = PRESELECTABLE_GOALS.filter { candidate -> PlanGoalMatcher.matches(entry, candidate) }.singleOrNull()
    val goalValue = servedGoal
        ?.takeIf { planGoalProfileOf(goal) != it }
        ?.let { goalChoiceValueOf(it) }
    val withGoal = if (goalValue != null) withStepChoice(SetupStepId.GOAL, goalValue, nowEpochMs) else this
    val fixedDays = entry.supportedFrequencies.takeIf { it.first == it.last }?.first
    val changesDays = fixedDays != null && selectedWeekdays.size != fixedDays
    // Los días ya no se preguntan por número: el plan de días fijos siembra una semana repartida (sin confirmarla).
    val withDays = if (fixedDays != null && changesDays) {
        withGoal.withWeekdays(EntrenoStepValues.defaultWeekdays(fixedDays))
    } else {
        withGoal
    }
    val confirmedAndChanged = buildSet {
        if (goalValue != null && SetupStepId.GOAL in stepProgress.answers) add(SetupStepId.GOAL)
        if (changesDays && SetupStepId.WEEKDAYS in stepProgress.answers) add(SetupStepId.WEEKDAYS)
        if (selectedCatalogId != entry.id && SetupStepId.PLAN in stepProgress.answers) add(SetupStepId.PLAN)
    }
    return withDays.copy(
        selectedCatalogId = entry.id,
        acceptFixedRecipeDifference = false,
        programRoute = SetupProgramRoute.CUSTOMIZABLE,
        trainingPath = SetupTrainingPath.PERSONALIZE,
        stepProgress = withDays.stepProgress.withPendingReview(confirmedAndChanged),
    )
}
