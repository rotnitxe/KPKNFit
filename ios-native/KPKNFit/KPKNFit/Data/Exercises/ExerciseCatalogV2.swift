import Foundation

/// The single, generated exercise catalog contract shared by Android, iOS and
/// the backend.  This file intentionally models the generated JSON directly;
/// it is not a second editorial taxonomy.
public struct ExerciseCatalogV2: Codable {
    public let schemaVersion: Int
    public let catalogRevision: String
    public let ontologyRevision: String
    public let families: [ExerciseCatalogFamilyV2]
}

public struct ExerciseCatalogEvidenceV2: Codable {
    public let reviewStatus: String
    public let confidence: String
    public let evidenceRefs: [String]
    public let rationale: String?
}

public struct ExerciseCatalogFamilyV2: Codable {
    public let id: String
    public let canonicalName: String
    public let description: String
    public let definitions: [ExerciseCatalogDefinitionV2]
    public let evidence: ExerciseCatalogEvidenceV2
    public let taxonomy: [String]?
}

public struct ExerciseCatalogDefinitionV2: Codable {
    public let id: String
    public let familyId: String
    public let kind: String
    public let canonicalName: String
    public let description: String
    public let searchTerms: [String]
    public let optionAxes: [String]
    public let configurations: [ExerciseCatalogConfigurationV2]
    public let defaultConfigurationId: String
    public let evidence: ExerciseCatalogEvidenceV2
}

public struct ExerciseCatalogConfigurationV2: Codable {
    public let id: String
    public let selectedOptions: [String: String]
    public let displaySummary: String
    public let profile: ExerciseCatalogProfileV2
    public let evidence: ExerciseCatalogEvidenceV2
}

public struct ExerciseCatalogProfileV2: Codable {
    public let movementPatternId: String
    public let bodyRegion: String
    public let kineticChain: String
    public let laterality: String
    public let equipmentId: String
    public let loadMode: String
    public let primaryMuscles: [String]
    public let secondaryMuscles: [String]
    public let stabilizerMuscles: [String]
    public let efc: Double
    public let cnc: Double
    public let ssc: Double
    public let ttc: Double
    public let axialLoadFactor: Double
    public let technicalDifficulty: Double
    public let resistanceProfile: String
    public let setupCues: [String]
    public let executionCues: [String]
    public let commonMistakes: [String]
    public let performanceProfileId: String
    public let replacementGroup: String?
    public let replacementPriority: Int?
    public let automationEligible: Bool
    public let richMetadata: ExerciseCatalogRichMetadataV2
}

public struct ExerciseCatalogRichMetadataV2: Codable {
    public let identity: ExerciseCatalogIdentityV2
    public let anatomy: ExerciseCatalogAnatomyV2
    public let biomechanics: ExerciseCatalogBiomechanicsV2
    public let programming: ExerciseCatalogProgrammingV2
    public let fatigue: ExerciseCatalogFatigueV2
    public let replacement: ExerciseCatalogReplacementV2
    public let coaching: ExerciseCatalogCoachingV2
    public let safety: ExerciseCatalogSafetyV2
    public let display: ExerciseCatalogDisplayV2
    public let evidenceConfidence: String
}

public struct ExerciseCatalogIdentityV2: Codable {
    public let canonicalName: String
    public let catalogRevision: String
    public let configurationId: String
    public let definitionId: String
    public let familyId: String
    public let kind: String
    public let performanceProfileId: String
    public let searchTerms: [String]
}

public struct ExerciseCatalogAnatomyV2: Codable {
    public let jointActions: [String]
    public let muscleLengthBias: String
    public let primaryMuscles: [String]
    public let secondaryMuscles: [String]
    public let stabilizationDemand: String
    public let stabilizerMuscles: [String]
    public let targetRegions: [String]
    public let volumeContribution: String
}

public struct ExerciseCatalogBiomechanicsV2: Codable {
    public let bodyRegion: String
    public let equipmentId: String
    public let kineticChain: String
    public let laterality: String
    public let loadMode: String
    public let movementPatternId: String
    public let rangeOfMotion: String
    public let relevantJoints: [String]
    public let relevantTendons: [String]
    public let resistanceProfile: String
    public let stability: String
}

public struct ExerciseCatalogProgrammingV2: Codable {
    public let fatigueCost: String
    public let indicativeRestSeconds: ExerciseCatalogRestRangeV2
    public let objectives: [String]
    public let recoveryCost: String
    public let requiredEquipment: [String]
    public let role: String
    public let setupTransitionCost: String
    public let splitSuitability: [String]
    public let suitableRepRanges: [String]
}

public struct ExerciseCatalogRestRangeV2: Codable {
    public let max: Int
    public let min: Int
}

public struct ExerciseCatalogFatigueV2: Codable {
    public let axialLoadFactor: Double
    public let cnc: Double
    public let efc: Double
    public let ssc: Double
    public let technicalDifficulty: Double
    public let ttc: Double
}

public struct ExerciseCatalogReplacementV2: Codable {
    public let compatibleEquipmentIds: [String]
    public let preservesIntent: [String]
    public let replacementGroup: String?
    public let replacementPriority: Int?
}

public struct ExerciseCatalogCoachingV2: Codable {
    public let commonMistakes: [String]
    public let cues: [String]
    public let execution: [String]
    public let progressions: [String]
    public let regressions: [String]
    public let relevantMobility: [String]
    public let setup: [String]
}

public struct ExerciseCatalogSafetyV2: Codable {
    public let medicalDisclaimerRequired: Bool
    public let precautions: [String]
    public let risks: [String]
}

public struct ExerciseCatalogDisplayV2: Codable {
    public let displayName: String
    public let displaySummary: String
    public let selectedOptions: [String: String]
}

public struct ExerciseCatalogSearchHitV2: Hashable {
    public let familyId: String
    public let definitionId: String
    public let configurationId: String
    public let canonicalName: String
    public let displaySummary: String
    public let kind: String

    public init(
        familyId: String,
        definitionId: String,
        configurationId: String,
        canonicalName: String,
        displaySummary: String,
        kind: String
    ) {
        self.familyId = familyId
        self.definitionId = definitionId
        self.configurationId = configurationId
        self.canonicalName = canonicalName
        self.displaySummary = displaySummary
        self.kind = kind
    }
}

public enum ExerciseCatalogV2Error: Error, LocalizedError, Equatable {
    case missingAsset
    case unreadableAsset(String)
    case invalidSchema(Int)
    case missingRevision
    case emptyFamilies
    case invalidIdentity(String)
    case duplicateId(String)
    case invalidConfiguration(String)
    case invalidDefault(String)
    case nonApproved(String)
    case notEligible(String)
    case revisionMismatch(String)
    case unknownDefinition(String)
    case unknownConfiguration(String)

    public var errorDescription: String? {
        switch self {
        case .missingAsset: return "exercise_catalog_v2.json no está incluido en el bundle iOS"
        case .unreadableAsset(let reason): return "No se pudo deserializar exercise_catalog_v2.json: \(reason)"
        case .invalidSchema(let version): return "schemaVersion incompatible: \(version)"
        case .missingRevision: return "El catálogo v2 no tiene catalogRevision u ontologyRevision"
        case .emptyFamilies: return "El catálogo v2 no contiene familias"
        case .invalidIdentity(let id): return "Identidad v2 inconsistente: \(id)"
        case .duplicateId(let id): return "ID v2 duplicado: \(id)"
        case .invalidConfiguration(let id): return "Configuración v2 inválida: \(id)"
        case .invalidDefault(let id): return "Default v2 inexistente: \(id)"
        case .nonApproved(let id): return "Entrada v2 no aprobada: \(id)"
        case .notEligible(let id): return "Configuración v2 no elegible para runtime: \(id)"
        case .revisionMismatch(let revision): return "Revisión v2 incorrecta: \(revision)"
        case .unknownDefinition(let id): return "Definición v2 desconocida: \(id)"
        case .unknownConfiguration(let id): return "Configuración v2 desconocida: \(id)"
        }
    }
}

/// Strict loader and exact resolver for the generated v2 artifact.
public final class ExerciseCatalogV2Repository {
    public static let shared: ExerciseCatalogV2Repository = {
        do {
            return try ExerciseCatalogV2Repository(bundle: .main)
        } catch {
            // A missing/corrupt catalog is a packaging error. Returning an
            // empty catalog would make the app appear healthy while silently
            // dropping exercise identity and metadata.
            fatalError("Approved exercise catalog v2 failed to load: \(error.localizedDescription)")
        }
    }()

    public let catalog: ExerciseCatalogV2
    private let definitionsById: [String: ExerciseCatalogDefinitionV2]

    public init(bundle: Bundle) throws {
        guard
            let url = bundle.url(forResource: "exercise_catalog_v2", withExtension: "json")
                ?? bundle.url(forResource: "exercise_catalog_v2", withExtension: "json", subdirectory: "Data/Exercises"),
            let data = try? Data(contentsOf: url)
        else {
            throw ExerciseCatalogV2Error.missingAsset
        }
        try self.init(data: data)
    }

    public init(data: Data) throws {
        let decoder = JSONDecoder()
        do {
            self.catalog = try decoder.decode(ExerciseCatalogV2.self, from: data)
        } catch {
            throw ExerciseCatalogV2Error.unreadableAsset(String(describing: error))
        }
        try Self.validate(catalog)
        self.definitionsById = Dictionary(uniqueKeysWithValues: catalog.families.flatMap { $0.definitions }.map { ($0.id, $0) })
    }

    public var catalogRevision: String { catalog.catalogRevision }
    public var ontologyRevision: String { catalog.ontologyRevision }

    public func resolve(
        definitionId: String,
        configurationId: String,
        catalogRevision: String
    ) throws -> ExerciseCatalogConfigurationV2 {
        guard catalogRevision == self.catalogRevision else {
            throw ExerciseCatalogV2Error.revisionMismatch(catalogRevision)
        }
        let normalizedDefinition = definitionId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let normalizedConfiguration = configurationId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard let definition = definitionsById[normalizedDefinition] else {
            throw ExerciseCatalogV2Error.unknownDefinition(definitionId)
        }
        guard let configuration = definition.configurations.first(where: { $0.id == normalizedConfiguration }) else {
            throw ExerciseCatalogV2Error.unknownConfiguration(configurationId)
        }
        return configuration
    }

    public func defaultConfiguration(for definitionId: String) throws -> ExerciseCatalogConfigurationV2 {
        let normalized = definitionId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard let definition = definitionsById[normalized] else {
            throw ExerciseCatalogV2Error.unknownDefinition(definitionId)
        }
        guard let configuration = definition.configurations.first(where: { $0.id == definition.defaultConfigurationId }) else {
            throw ExerciseCatalogV2Error.invalidDefault(definition.id)
        }
        return configuration
    }

    /// Search is intentionally separate from resolution: search may use the
    /// curated terms, but only exact IDs plus the catalog revision can resolve
    /// a persisted selection.
    public func search(_ query: String, limit: Int = 50) -> [ExerciseCatalogSearchHitV2] {
        let normalizedQuery = Self.normalize(query)
        guard !normalizedQuery.isEmpty else { return [] }
        var scored: [(Int, ExerciseCatalogSearchHitV2)] = []
        for family in catalog.families {
            for definition in family.definitions {
                let canonical = Self.normalize(definition.canonicalName)
                let terms = definition.searchTerms.map(Self.normalize)
                let score: Int?
                if canonical == normalizedQuery { score = 0 }
                else if terms.contains(normalizedQuery) { score = 1 }
                else if canonical.contains(normalizedQuery) { score = 2 }
                else if terms.contains(where: { $0.contains(normalizedQuery) }) { score = 3 }
                else { score = nil }
                guard let score else { continue }
                for configuration in definition.configurations {
                    scored.append((score, ExerciseCatalogSearchHitV2(
                        familyId: family.id,
                        definitionId: definition.id,
                        configurationId: configuration.id,
                        canonicalName: definition.canonicalName,
                        displaySummary: configuration.displaySummary,
                        kind: definition.kind
                    )))
                }
            }
        }
        return scored.sorted { lhs, rhs in
            if lhs.0 != rhs.0 { return lhs.0 < rhs.0 }
            return lhs.1.configurationId < rhs.1.configurationId
        }.prefix(max(limit, 0)).map { $0.1 }
    }

    private static func normalize(_ value: String) -> String {
        value
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
    }

    private static func validate(_ catalog: ExerciseCatalogV2) throws {
        guard catalog.schemaVersion == 2 else { throw ExerciseCatalogV2Error.invalidSchema(catalog.schemaVersion) }
        guard !catalog.catalogRevision.isEmpty, !catalog.ontologyRevision.isEmpty else {
            throw ExerciseCatalogV2Error.missingRevision
        }
        guard !catalog.families.isEmpty else { throw ExerciseCatalogV2Error.emptyFamilies }

        var familyIds = Set<String>()
        var definitionIds = Set<String>()
        var configurationIds = Set<String>()
        for family in catalog.families {
            guard familyIds.insert(family.id).inserted else { throw ExerciseCatalogV2Error.duplicateId(family.id) }
            try requireApproved(family.evidence, id: family.id)
            for definition in family.definitions {
                guard definition.familyId == family.id else { throw ExerciseCatalogV2Error.invalidIdentity(definition.id) }
                guard definitionIds.insert(definition.id).inserted else { throw ExerciseCatalogV2Error.duplicateId(definition.id) }
                try requireApproved(definition.evidence, id: definition.id)
                guard definition.configurations.contains(where: { $0.id == definition.defaultConfigurationId }) else {
                    throw ExerciseCatalogV2Error.invalidDefault(definition.id)
                }
                var signatures = Set<String>()
                for configuration in definition.configurations {
                    guard configurationIds.insert(configuration.id).inserted else { throw ExerciseCatalogV2Error.duplicateId(configuration.id) }
                    guard Set(configuration.selectedOptions.keys) == Set(definition.optionAxes) else {
                        throw ExerciseCatalogV2Error.invalidConfiguration(configuration.id)
                    }
                    let signature = definition.optionAxes.map { "\($0)=\(configuration.selectedOptions[$0] ?? "")" }.joined(separator: "|")
                    guard signatures.insert(signature).inserted else { throw ExerciseCatalogV2Error.invalidConfiguration(configuration.id) }
                    try requireApproved(configuration.evidence, id: configuration.id)
                    guard configuration.profile.automationEligible else { throw ExerciseCatalogV2Error.notEligible(configuration.id) }
                    let identity = configuration.profile.richMetadata.identity
                    guard identity.catalogRevision == catalog.catalogRevision,
                          identity.familyId == family.id,
                          identity.definitionId == definition.id,
                          identity.configurationId == configuration.id,
                          identity.canonicalName == definition.canonicalName,
                          identity.searchTerms == definition.searchTerms,
                          identity.kind == definition.kind,
                          identity.performanceProfileId == configuration.profile.performanceProfileId,
                          configuration.profile.richMetadata.display.displayName == definition.canonicalName,
                          configuration.profile.richMetadata.display.displaySummary == configuration.displaySummary,
                          configuration.profile.richMetadata.display.selectedOptions == configuration.selectedOptions
                    else { throw ExerciseCatalogV2Error.invalidIdentity(configuration.id) }
                }
            }
        }
    }

    private static func requireApproved(_ evidence: ExerciseCatalogEvidenceV2, id: String) throws {
        guard evidence.reviewStatus == "APPROVED" else { throw ExerciseCatalogV2Error.nonApproved(id) }
    }
}
