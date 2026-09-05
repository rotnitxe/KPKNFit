package com.example.kpkn.screens.competitions.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.competitions.CompetitionMediaStore
import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionEquipment
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.PowerliftingCompetitionDetails
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.competitions.CompetitionExerciseSuggestion
import com.example.kpkn.domain.competitions.CompetitionExerciseTypeahead
import com.example.kpkn.domain.competitions.CompetitionPlaceHonors
import com.example.kpkn.domain.competitions.CompetitionScoring
import com.example.kpkn.domain.competitions.PowerliftingWizardDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.net.Uri

enum class CompetitionWizardStep {
    EVENT,
    LIFTS,
    PLACE,
    ALBUM,
}

class CompetitionWizardViewModel(
    private val competitionId: String?,
    private val repository: CompetitionRepository = CompetitionRepository.getInstance(),
) : ViewModel() {

    private val isNew = competitionId.isNullOrBlank() || competitionId == "new"

    private val _draft = MutableStateFlow(PowerliftingWizardDraft.createEmpty())
    val draft: StateFlow<CompetitionRecord> = _draft.asStateFlow()

    private val _step = MutableStateFlow(CompetitionWizardStep.EVENT)
    val step: StateFlow<CompetitionWizardStep> = _step.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = runCatching { ProgramRepository.getInstance().settings.value }.getOrNull()
            val sex = when (settings?.userVitals?.gender) {
                Gender.FEMALE -> "female"
                else -> "male"
            }
            val weight = settings?.userVitals?.weight
            if (isNew) {
                _draft.value = PowerliftingWizardDraft.createEmpty(
                    sexCategory = sex,
                    bodyweightKg = weight,
                )
                _ready.value = true
                return@launch
            }
            repository.isReady.collect { loaded ->
                if (!loaded) return@collect
                val existing = repository.getById(competitionId.orEmpty())
                    ?: repository.records.value.firstOrNull { it.id == competitionId }
                if (existing != null) {
                    val seeded = PowerliftingWizardDraft.ensureSbd(existing)
                    val withSex = if (seeded.powerliftingDetails?.sexCategory.isNullOrBlank()) {
                        seeded.copy(
                            powerliftingDetails = (seeded.powerliftingDetails
                                ?: PowerliftingCompetitionDetails()).copy(sexCategory = sex),
                        )
                    } else {
                        seeded
                    }
                    _draft.value = withSex
                } else {
                    _draft.value = PowerliftingWizardDraft.createEmpty(
                        id = competitionId.orEmpty(),
                        sexCategory = sex,
                        bodyweightKg = weight,
                    )
                }
                _ready.value = true
            }
        }
    }

    fun update(transform: (CompetitionRecord) -> CompetitionRecord) {
        _draft.update(transform)
    }

    fun setDate(iso: String) = update { it.copy(eventDate = iso) }

    fun setVenue(value: String) = update { it.copy(location = value) }

    fun setMeetName(value: String) = update { it.copy(title = value) }

    fun setFederation(id: String?, customName: String? = null) = update {
        PowerliftingWizardDraft.applyFederation(it, id, customName)
    }

    fun setCustomFederationName(name: String) = update {
        PowerliftingWizardDraft.applyFederation(it, PowerliftingFederationCatalog.CUSTOM_ID, name)
    }

    fun setBodyweight(kg: Double) = update { it.copy(bodyweightKg = kg.coerceAtLeast(20.0)) }

    fun setWeightClass(value: String) = updateDetails { it.copy(weightClass = value) }

    fun setDivision(value: String) = updateDetails { it.copy(division = value) }

    fun setEquipment(value: CompetitionEquipment) = updateDetails { it.copy(equipment = value) }

    fun updateAttempt(
        blockId: String,
        attemptNumber: Int,
        transform: (CompetitionAttempt) -> CompetitionAttempt,
    ) = update { record ->
        record.copy(
            technicalBlocks = record.technicalBlocks.map { block ->
                if (block.id != blockId) block else block.copy(
                    attempts = block.attempts.map { attempt ->
                        if (attempt.attemptNumber != attemptNumber) attempt else transform(attempt)
                    },
                )
            },
        )
    }

    fun cycleAttemptResult(blockId: String, attemptNumber: Int) {
        updateAttempt(blockId, attemptNumber) { attempt ->
            attempt.copy(resultType = nextResult(attempt.resultType))
        }
    }

    fun nudgeAttemptWeight(blockId: String, attemptNumber: Int, delta: Double) {
        updateAttempt(blockId, attemptNumber) { attempt ->
            val base = attempt.weightKg ?: 100.0
            attempt.copy(weightKg = (base + delta).coerceAtLeast(0.0))
        }
    }

    fun suggestions(query: String): List<CompetitionExerciseSuggestion> =
        CompetitionExerciseTypeahead.suggest(
            query = query,
            catalogIndex = catalogExerciseIndex(),
            catalogV2 = approvedExerciseCatalogV2(),
        )

    fun bindExercise(blockId: String, exercise: ExerciseMuscleInfo) {
        update { record ->
            record.copy(
                technicalBlocks = record.technicalBlocks.map { block ->
                    if (block.id != blockId) block else PowerliftingWizardDraft.bindExercise(block, exercise)
                },
            )
        }
    }

    fun useCustomExerciseName(blockId: String, name: String) {
        val created = PowerliftingWizardDraft.createCustomExercise(name, exerciseCatalogSnapshot())
        CustomExerciseRepository.upsert(created)
        bindExercise(blockId, created)
    }

    fun addExtraLift() = update(PowerliftingWizardDraft::addExtraLift)

    fun setPlace(place: Int, trophyId: String? = null) = update {
        PowerliftingWizardDraft.applyPlace(it, place, trophyId)
    }

    fun addMedia(context: Context, uri: Uri) {
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) {
                CompetitionMediaStore.copyIntoApp(context, uri, _draft.value.id)
            } ?: return@launch
            update { it.copy(photos = it.photos + copied) }
        }
    }

    fun removeMedia(id: String) = update { it.copy(photos = it.photos.filterNot { photo -> photo.id == id }) }

    fun canContinue(): Boolean = when (_step.value) {
        CompetitionWizardStep.EVENT -> {
            val current = _draft.value
            !current.eventDate.isNullOrBlank() &&
                (
                    PowerliftingFederationCatalog.byId(current.federationId) != null ||
                        (!current.federation.isNullOrBlank() && current.federationId == PowerliftingFederationCatalog.CUSTOM_ID)
                    )
        }
        CompetitionWizardStep.LIFTS -> true
        CompetitionWizardStep.PLACE -> {
            val current = _draft.value
            CompetitionPlaceHonors.isValid(
                CompetitionPlaceHonors.parsePlace(current.placement),
                current.trophyId,
            )
        }
        CompetitionWizardStep.ALBUM -> true
    }

    fun goNext() {
        if (!canContinue()) return
        _step.update { current ->
            when (current) {
                CompetitionWizardStep.EVENT -> CompetitionWizardStep.LIFTS
                CompetitionWizardStep.LIFTS -> CompetitionWizardStep.PLACE
                CompetitionWizardStep.PLACE -> CompetitionWizardStep.ALBUM
                CompetitionWizardStep.ALBUM -> current
            }
        }
    }

    fun goBack(): Boolean {
        val current = _step.value
        if (current == CompetitionWizardStep.EVENT) return false
        _step.value = when (current) {
            CompetitionWizardStep.LIFTS -> CompetitionWizardStep.EVENT
            CompetitionWizardStep.PLACE -> CompetitionWizardStep.LIFTS
            CompetitionWizardStep.ALBUM -> CompetitionWizardStep.PLACE
            CompetitionWizardStep.EVENT -> current
        }
        return true
    }

    fun saveCompleted() {
        val completed = CompetitionScoring.recalculate(
            PowerliftingWizardDraft.ensureSbd(_draft.value).copy(
                title = PowerliftingWizardDraft.derivedTitle(_draft.value),
                status = CompetitionRecordStatus.COMPLETED,
                reminderOneWeekEnabled = false,
                reminder48hEnabled = false,
                reminderStartEnabled = false,
                journal = null,
            ),
        )
        repository.upsert(completed)
    }

    fun livePointsLabel(): String {
        val scored = CompetitionScoring.recalculate(_draft.value)
        val points = CompetitionScoring.displayedPoints(scored)
        val total = scored.powerliftingDetails?.totalKg
        return buildString {
            if (total != null) append("${CompetitionScoring.formatKg(total)} kg")
            if (points != null) {
                if (isNotEmpty()) append("  ·  ")
                append("${points.label} ${CompetitionScoring.formatPoints(points.value)}")
            }
            if (isEmpty()) append("Total y puntos")
        }
    }

    private fun updateDetails(transform: (PowerliftingCompetitionDetails) -> PowerliftingCompetitionDetails) {
        update { record ->
            record.copy(powerliftingDetails = transform(record.powerliftingDetails ?: PowerliftingCompetitionDetails()))
        }
    }

    private fun nextResult(current: CompetitionAttemptResult): CompetitionAttemptResult = when (current) {
        CompetitionAttemptResult.PENDING -> CompetitionAttemptResult.GOOD_LIFT
        CompetitionAttemptResult.GOOD_LIFT -> CompetitionAttemptResult.NO_LIFT
        CompetitionAttemptResult.NO_LIFT -> CompetitionAttemptResult.SKIPPED
        CompetitionAttemptResult.SKIPPED -> CompetitionAttemptResult.PENDING
    }
}
