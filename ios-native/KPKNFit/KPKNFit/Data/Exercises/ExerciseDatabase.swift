import Foundation

// ─── Constants ──────────────────────────────────────────────────────────────

private let EXERCISE_DATABASE_ASSET = "exercise_database"
private let EXERCISE_ALIASES_ASSET = "exercise_id_aliases"

// ─── JSON Decoder ──────────────────────────────────────────────────────────

private let exerciseCatalogDecoder: JSONDecoder = {
    let decoder = JSONDecoder()
    decoder.keyDecodingStrategy = .useDefaultKeys
    return decoder
}()

// ─── Thread Safety ─────────────────────────────────────────────────────────

private let exerciseCatalogLock = DispatchQueue(label: "kpkn.exercise.catalog.lock")

// ─── Hardcoded Aliases ─────────────────────────────────────────────────────

private let extraWikiLabExerciseAliases: [String: String] = [
    "db_exp_face_pull": "tren_superior_face_pull_polea",
    "db_plank": "ultimo_plancha_frontal",
    "db_exp_hammer_curl": "tren_superior_curl_martillo_mancuernas",
    "db_ab_wheel": "ultimo_plancha_rodillo",
    "db_hanging_leg_raises": "ultimo_elevacion_piernas_paralelas",
]

// ─── Caches ────────────────────────────────────────────────────────────────

private var exerciseDatabaseCache: [ExerciseMuscleInfo] = []
private var staticExerciseCache: [ExerciseMuscleInfo] = []
private var customExerciseOverlayCache: [ExerciseMuscleInfo] = []
private var exerciseDatabaseByIdCache: [String: ExerciseMuscleInfo] = [:]
private var exerciseAliasCache: [String: String] = [:]
private var exerciseCatalogInitialized = false

// ─── Public API ────────────────────────────────────────────────────────────

public func initializeExerciseDatabase() {
    if exerciseCatalogInitialized { return }

    exerciseCatalogLock.sync {
        if exerciseCatalogInitialized { return }

        guard
            let exercisesURL = Bundle.main.url(forResource: EXERCISE_DATABASE_ASSET, withExtension: "json"),
            let aliasesURL = Bundle.main.url(forResource: EXERCISE_ALIASES_ASSET, withExtension: "json"),
            let exercisesData = try? Data(contentsOf: exercisesURL),
            let aliasesData = try? Data(contentsOf: aliasesURL)
        else {
            return
        }

        let baseExercises: [ExerciseMuscleInfo] = (try? exerciseCatalogDecoder.decode([ExerciseMuscleInfo].self, from: exercisesData)) ?? []
        staticExerciseCache = baseExercises.map { normalizeExerciseLabels($0) }

        let exercises = buildMergedExerciseCatalog()
        let aliases = (try? exerciseCatalogDecoder.decode([String: String].self, from: aliasesData)) ?? [:]

        exerciseDatabaseCache = exercises
        exerciseDatabaseByIdCache = Dictionary(uniqueKeysWithValues: exercises.map { ($0.id.lowercased(), $0) })

        var mergedAliases = aliases.reduce(into: [String: String]()) { result, pair in
            result[pair.key.lowercased()] = pair.value.lowercased()
        }
        for (key, value) in extraWikiLabExerciseAliases {
            mergedAliases[key.lowercased()] = value.lowercased()
        }
        exerciseAliasCache = mergedAliases

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
    }
}

public var EXERCISE_DATABASE: [ExerciseMuscleInfo] {
    exerciseDatabaseCache
}

public var EXERCISE_DATABASE_BY_ID: [String: ExerciseMuscleInfo] {
    exerciseDatabaseByIdCache
}

public var EXERCISE_ID_ALIASES: [String: String] {
    exerciseAliasCache
}

public func resolveExerciseId(_ rawId: String?) -> String? {
    guard let rawId = rawId?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), !rawId.isEmpty else {
        return nil
    }
    if exerciseDatabaseByIdCache[rawId] != nil {
        return rawId
    }
    guard let canonical = exerciseAliasCache[rawId] else {
        return nil
    }
    return exerciseDatabaseByIdCache[canonical] != nil ? canonical : nil
}

public func resolveExercise(_ rawId: String?) -> ExerciseMuscleInfo? {
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
    }
}

public func upsertCustomExerciseOverlay(exercise: ExerciseMuscleInfo) {
    exerciseCatalogLock.sync {
        let normalized = normalizeExerciseLabels(exercise.copy(isCustom: true))
        customExerciseOverlayCache = customExerciseOverlayCache.filter { $0.id.caseInsensitiveCompare(normalized.id) != .orderedSame } + [normalized]
        let merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = Dictionary(uniqueKeysWithValues: merged.map { ($0.id.lowercased(), $0) })
    }
}

public func removeCustomExerciseOverlay(exerciseId: String) {
    exerciseCatalogLock.sync {
        customExerciseOverlayCache = customExerciseOverlayCache.filter { $0.id.caseInsensitiveCompare(exerciseId) != .orderedSame }
        let merged = buildMergedExerciseCatalog()
        exerciseDatabaseCache = merged
        exerciseDatabaseByIdCache = Dictionary(uniqueKeysWithValues: merged.map { ($0.id.lowercased(), $0) })
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
