import Foundation

// ─── Constants ──────────────────────────────────────────────────────────────

// ─── Thread Safety ─────────────────────────────────────────────────────────

private let exerciseCatalogLock = DispatchQueue(label: "kpkn.exercise.catalog.lock")

// ─── Caches ────────────────────────────────────────────────────────────────

private var exerciseDatabaseCache: [ExerciseMuscleInfo] = []
private var staticExerciseCache: [ExerciseMuscleInfo] = []
private var customExerciseOverlayCache: [ExerciseMuscleInfo] = []
private var exerciseDatabaseByIdCache: [String: ExerciseMuscleInfo] = [:]
private var v2ConfigurationLookupCache: [String: ExerciseMuscleInfo] = [:]
private var exerciseCatalogV2Cache: ExerciseCatalogV2Repository?
private var exerciseCatalogInitialized = false

// ─── Public API ────────────────────────────────────────────────────────────

public func initializeExerciseDatabase() {
    if exerciseCatalogInitialized { return }

    exerciseCatalogLock.sync {
        if exerciseCatalogInitialized { return }

        let catalog: ExerciseCatalogV2Repository
        do {
            catalog = try ExerciseCatalogV2Repository(bundle: .main)
        } catch {
            fatalError("Approved exercise catalog v2 failed to load: \(error.localizedDescription)")
        }
        exerciseCatalogV2Cache = catalog
        staticExerciseCache = catalog.catalog.families.flatMap { family in
            family.definitions.compactMap { definition in
                guard let configuration = definition.configurations.first(where: { $0.id == definition.defaultConfigurationId }) else {
                    return nil
                }
                return legacyExerciseInfo(family: family, definition: definition, configuration: configuration)
            }
        }.map { normalizeExerciseLabels($0) }

        let exercises = buildMergedExerciseCatalog()
        exerciseDatabaseCache = exercises
        v2ConfigurationLookupCache = catalog.catalog.families.flatMap { family in
            family.definitions.flatMap { definition in
                definition.configurations.map { configuration in
                    legacyExerciseInfo(
                        family: family,
                        definition: definition,
                        configuration: configuration,
                        id: configuration.id
                    )
                }
            }
        }.reduce(into: [String: ExerciseMuscleInfo]()) { result, info in
            result[info.id.lowercased()] = normalizeExerciseLabels(info)
        }
        exerciseDatabaseByIdCache = Dictionary(uniqueKeysWithValues: exercises.map { ($0.id.lowercased(), $0) })
            .merging(v2ConfigurationLookupCache) { _, v2 in v2 }

        exerciseCatalogInitialized = true
    }
}

public func loadCustomExercisesAsync() async {
    let customExercises: [ExerciseMuscleInfo]
    do {
        let entities = try await KpknDatabase.instance().customExerciseDao.getAll()
        customExercises = entities.compactMap { $0.toExerciseMuscleInfo() }.map { $0.copy(isCustom: true) }
    } catch {
        customExercises = []
    }

    exerciseCatalogLock.sync {
        customExerciseOverlayCache = customExercises
        let merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = Dictionary(uniqueKeysWithValues: merged.map { ($0.id.lowercased(), $0) })
            .merging(v2ConfigurationLookupCache) { _, v2 in v2 }
    }
}

public var catalogExerciseList: [ExerciseMuscleInfo] {
    initializeExerciseDatabase()
    exerciseDatabaseCache
}

/// Exact ID index for definitions, configurations and custom exercises.
public func catalogExerciseIndex() -> [String: ExerciseMuscleInfo] {
    initializeExerciseDatabase()
    return exerciseDatabaseByIdCache
}

/// Search terms are owned by the v2 repository; there is no runtime redirect table.
public func catalogSearchRedirects() -> [String: String] {
    [:]
}

public func exerciseCatalogV2() -> ExerciseCatalogV2Repository {
    initializeExerciseDatabase()
    return exerciseCatalogV2Cache!
}

public func resolveExerciseId(_ rawId: String?) -> String? {
    initializeExerciseDatabase()
    guard let rawId = rawId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), !rawId.isEmpty else {
        return nil
    }
    return exerciseDatabaseByIdCache[rawId] != nil ? rawId : nil
}

public func resolveExercise(_ rawId: String?) -> ExerciseMuscleInfo? {
    initializeExerciseDatabase()
    guard let resolvedId = resolveExerciseId(rawId) else {
        return nil
    }
    return exerciseDatabaseByIdCache[resolvedId]
}

public func setCustomExerciseOverlay(exercises: [ExerciseMuscleInfo]) {
    exerciseCatalogLock.sync {
        customExerciseOverlayCache = exercises.map { $0.copy(isCustom: true) }
        let merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = Dictionary(uniqueKeysWithValues: merged.map { ($0.id.lowercased(), $0) })
            .merging(v2ConfigurationLookupCache) { _, v2 in v2 }
    }
}

public func upsertCustomExerciseOverlay(exercise: ExerciseMuscleInfo) {
    exerciseCatalogLock.sync {
        let normalized = normalizeExerciseLabels(exercise.copy(isCustom: true))
        customExerciseOverlayCache = customExerciseOverlayCache.filter { $0.id.caseInsensitiveCompare(normalized.id) != .orderedSame } + [normalized]
        let merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = Dictionary(uniqueKeysWithValues: merged.map { ($0.id.lowercased(), $0) })
            .merging(v2ConfigurationLookupCache) { _, v2 in v2 }
    }
}

public func removeCustomExerciseOverlay(exerciseId: String) {
    exerciseCatalogLock.sync {
        customExerciseOverlayCache = customExerciseOverlayCache.filter { $0.id.caseInsensitiveCompare(exerciseId) != .orderedSame }
        let merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = Dictionary(uniqueKeysWithValues: merged.map { ($0.id.lowercased(), $0) })
            .merging(v2ConfigurationLookupCache) { _, v2 in v2 }
    }
}

public func addOrUpdateCustomExercise(_ exercise: ExerciseMuscleInfo) async {
    let normalized = normalizeExerciseLabels(exercise.copy(isCustom: true))
    upsertCustomExerciseOverlay(exercise: normalized)
    do {
        try await KpknDatabase.instance().customExerciseDao.upsert(entity: normalized.toEntity())
    } catch {
        // runCatching swallows errors
    }
}

// ─── v2 -> legacy materialization boundary ─────────────────────────────────

/// Legacy consumers receive a read-only projection of the approved v2
/// configuration. The source of truth remains the v2 repository and its
/// exact definition/configuration IDs; this adapter exists only until every
/// iOS feature consumes the v2 model directly.
private func legacyExerciseInfo(
    family: ExerciseCatalogFamilyV2,
    definition: ExerciseCatalogDefinitionV2,
    configuration: ExerciseCatalogConfigurationV2,
    id: String? = nil
) -> ExerciseMuscleInfo {
    let profile = configuration.profile
    let rich = profile.richMetadata
    let involved: [InvolvedMuscle] = profile.primaryMuscles.map {
        InvolvedMuscle(muscle: muscleLabel($0), role: .PRIMARY)
    } + profile.secondaryMuscles.map {
        InvolvedMuscle(muscle: muscleLabel($0), role: .SECONDARY)
    } + profile.stabilizerMuscles.map {
        InvolvedMuscle(muscle: muscleLabel($0), role: .STABILIZER)
    }
    let coaching = rich.coaching
    return ExerciseMuscleInfo(
        id: id ?? definition.id,
        name: rich.display.displayName,
        alias: definition.searchTerms.joined(separator: ", ").nilIfBlank,
        description: "\(definition.description) \(configuration.displaySummary).",
        involvedMuscles: involved,
        equipment: equipmentLabel(profile.equipmentId),
        category: definition.kind == "SPECIALTY" ? "Especialidad" : "Fuerza",
        type: definition.kind == "SPECIALTY" ? "Especialidad" : "Accesorio",
        force: profile.movementPatternId,
        chain: profile.kineticChain.lowercased(),
        bodyPart: profile.bodyRegion.lowercased(),
        tier: rich.programming.role,
        efc: profile.efc,
        cnc: profile.cnc,
        ssc: profile.ssc,
        ttc: profile.ttc,
        axialLoadFactor: profile.axialLoadFactor,
        technicalDifficulty: profile.technicalDifficulty,
        resistanceProfile: ResistanceProfile(
            curve: profile.resistanceProfile,
            peakTensionPoint: rich.biomechanics.rangeOfMotion,
            description: rich.biomechanics.stability
        ),
        commonMistakes: profile.commonMistakes.map {
            CommonMistake(mistake: $0, correction: "Reduce la carga y repite el cue técnico.")
        },
        setupCues: profile.setupCues,
        executionCues: profile.executionCues,
        progressions: coaching.progressions.map { Progression(name: "Progresión", description: $0) },
        regressions: coaching.regressions.map { Progression(name: "Regresión", description: $0) },
        recommendedMobility: coaching.relevantMobility,
        functionalTransfer: rich.programming.objectives.joined(separator: " ").nilIfBlank,
        sportsRelevance: rich.programming.splitSuitability,
        setupTime: rich.programming.indicativeRestSeconds.min,
        averageRestSeconds: rich.programming.indicativeRestSeconds.max,
        executionOptions: definition.optionAxes,
        movementPattern: profile.movementPatternId
    )
}

private func muscleLabel(_ id: String) -> String {
    [
        "pectoralis": "Pectorales",
        "deltoid": "Deltoides",
        "triceps": "Tríceps",
        "biceps": "Bíceps",
        "forearm": "Antebrazo",
        "latissimus_dorsi": "Dorsales",
        "erector_spinae": "Erectores Espinales",
        "hamstrings": "Isquiosurales",
        "gluteus_maximus": "Glúteos",
        "quadriceps": "Cuádriceps",
        "calves": "Pantorrillas",
        "tibialis_anterior": "Tibial Anterior",
        "hip_flexors": "Flexores Cadera",
        "neck": "Cuello",
        "adductors": "Aductores",
        "tensor_fasciae_latae": "Tensor Fascia Lata",
        "trapezius": "Trapecio",
        "rhomboids": "Romboides",
        "abdominals": "Abdomen",
        "core": "Core",
    ][id] ?? id
}

private func equipmentLabel(_ id: String) -> String {
    [
        "barbell": "Barra",
        "dumbbells": "Mancuerna",
        "machine": "Máquina",
        "cable": "Polea",
        "bodyweight": "Peso Corporal",
        "plate": "Disco",
        "band": "Banda",
        "kettlebell": "Kettlebell",
        "ez_bar": "Barra EZ",
        "trx": "TRX",
        "smith_machine": "Máquina Smith",
    ][id] ?? id
}

private extension String {
    var nilIfBlank: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}

// ─── Private Helpers ───────────────────────────────────────────────────────

private func buildMergedExerciseCatalog() -> [ExerciseMuscleInfo] {
    let merged = staticExerciseCache + customExerciseOverlayCache
    var dict = [String: ExerciseMuscleInfo]()
    for exercise in merged {
        dict[exercise.id.lowercased()] = exercise
    }
    return Array(dict.values)
}

private func normalizeExerciseLabels(_ exercise: ExerciseMuscleInfo) -> ExerciseMuscleInfo {
    exercise.copy(
        name: normalizeInlineUppercaseP(exercise.name),
        alias: exercise.alias.map { normalizeInlineUppercaseP($0) }
    )
}

private func normalizeInlineUppercaseP(_ value: String) -> String {
    var chars = Array(value)
    for index in 1..<(chars.count - 1) {
        if chars[index] == "P" && chars[index - 1].isLetter && chars[index + 1].isLowercase {
            chars[index] = "p"
        }
    }
    return String(chars)
}

// ─── ExerciseMuscleInfo Copy Extension ────────────────────────────────────

extension ExerciseMuscleInfo {
    public func copy(
        id: String? = nil,
        name: String? = nil,
        alias: String? = nil,
        description: String? = nil,
        involvedMuscles: [InvolvedMuscle]? = nil,
        equipment: String? = nil,
        category: String? = nil,
        type: String? = nil,
        force: String? = nil,
        chain: String? = nil,
        bodyPart: String? = nil,
        tier: String? = nil,
        isCustom: Bool? = nil,
        efc: Double? = nil,
        cnc: Double? = nil,
        ssc: Double? = nil,
        ttc: Double? = nil,
        axialLoadFactor: Double? = nil,
        technicalDifficulty: Double? = nil,
        coreInvolvement: String? = nil,
        bracingRecommended: Bool? = nil,
        strapsRecommended: Bool? = nil,
        resistanceProfile: ResistanceProfile? = nil,
        anatomicalConsiderations: [AnatomicalConsideration]? = nil,
        commonMistakes: [CommonMistake]? = nil,
        setupCues: [String]? = nil,
        executionCues: [String]? = nil,
        progressions: [Progression]? = nil,
        regressions: [Progression]? = nil,
        recommendedMobility: [String]? = nil,
        periodizationNotes: [PeriodizationNote]? = nil,
        functionalTransfer: String? = nil,
        sportsRelevance: [String]? = nil,
        injuryRisk: InjuryRisk? = nil,
        sfr: ScoreJustification? = nil,
        primeStars: ScoreJustification? = nil,
        bodybuildingScore: Double? = nil,
        communityOpinion: [String]? = nil,
        aiCoachAnalysis: AiCoachAnalysis? = nil,
        images: [String]? = nil,
        videos: [String]? = nil,
        setupDetails: SetupDetails? = nil,
        setupTime: Int? = nil,
        averageRestSeconds: Int? = nil,
        executionOptions: [String]? = nil,
        movementPattern: String? = nil
    ) -> ExerciseMuscleInfo {
        ExerciseMuscleInfo(
            id: id ?? self.id,
            name: name ?? self.name,
            alias: alias ?? self.alias,
            description: description ?? self.description,
            involvedMuscles: involvedMuscles ?? self.involvedMuscles,
            equipment: equipment ?? self.equipment,
            category: category ?? self.category,
            type: type ?? self.type,
            force: force ?? self.force,
            chain: chain ?? self.chain,
            bodyPart: bodyPart ?? self.bodyPart,
            tier: tier ?? self.tier,
            isCustom: isCustom ?? self.isCustom,
            efc: efc ?? self.efc,
            cnc: cnc ?? self.cnc,
            ssc: ssc ?? self.ssc,
            ttc: ttc ?? self.ttc,
            axialLoadFactor: axialLoadFactor ?? self.axialLoadFactor,
            technicalDifficulty: technicalDifficulty ?? self.technicalDifficulty,
            coreInvolvement: coreInvolvement ?? self.coreInvolvement,
            bracingRecommended: bracingRecommended ?? self.bracingRecommended,
            strapsRecommended: strapsRecommended ?? self.strapsRecommended,
            resistanceProfile: resistanceProfile ?? self.resistanceProfile,
            anatomicalConsiderations: anatomicalConsiderations ?? self.anatomicalConsiderations,
            commonMistakes: commonMistakes ?? self.commonMistakes,
            setupCues: setupCues ?? self.setupCues,
            executionCues: executionCues ?? self.executionCues,
            progressions: progressions ?? self.progressions,
            regressions: regressions ?? self.regressions,
            recommendedMobility: recommendedMobility ?? self.recommendedMobility,
            periodizationNotes: periodizationNotes ?? self.periodizationNotes,
            functionalTransfer: functionalTransfer ?? self.functionalTransfer,
            sportsRelevance: sportsRelevance ?? self.sportsRelevance,
            injuryRisk: injuryRisk ?? self.injuryRisk,
            sfr: sfr ?? self.sfr,
            primeStars: primeStars ?? self.primeStars,
            bodybuildingScore: bodybuildingScore ?? self.bodybuildingScore,
            communityOpinion: communityOpinion ?? self.communityOpinion,
            aiCoachAnalysis: aiCoachAnalysis ?? self.aiCoachAnalysis,
            images: images ?? self.images,
            videos: videos ?? self.videos,
            setupDetails: setupDetails ?? self.setupDetails,
            setupTime: setupTime ?? self.setupTime,
            averageRestSeconds: averageRestSeconds ?? self.averageRestSeconds,
            executionOptions: executionOptions ?? self.executionOptions,
            movementPattern: movementPattern ?? self.movementPattern
        )
    }
}
