import Foundation

public final class WikiLabRepository {

    static let shared = WikiLabRepository()

    private var dao: WikiLabDao?
    private let db = KpknDatabase.instance()

    private var _muscles: [MuscleGroupEntity] = []
    private var _joints: [JointEntity] = []
    private var _tendons: [TendonEntity] = []
    private var _patterns: [MovementPatternEntity] = []
    private var _chains: [KineticChainEntity] = []

    private let musclesLock = NSLock()
    private let jointsLock = NSLock()
    private let tendonsLock = NSLock()
    private let patternsLock = NSLock()
    private let chainsLock = NSLock()

    public var muscles: [MuscleGroupEntity] {
        musclesLock.lock()
        defer { musclesLock.unlock() }
        return _muscles
    }

    public var joints: [JointEntity] {
        jointsLock.lock()
        defer { jointsLock.unlock() }
        return _joints
    }

    public var tendons: [TendonEntity] {
        tendonsLock.lock()
        defer { tendonsLock.unlock() }
        return _tendons
    }

    public var patterns: [MovementPatternEntity] {
        patternsLock.lock()
        defer { patternsLock.unlock() }
        return _patterns
    }

    public var chains: [KineticChainEntity] {
        chainsLock.lock()
        defer { chainsLock.unlock() }
        return _chains
    }

    private let jsonDecoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .useDefaultKeys
        return d
    }()

    private init() {}

    public static func getInstance() -> WikiLabRepository { shared }

    public func initialize() {
        let wikiLabDao = db.wikiLabDao
        self.dao = wikiLabDao

        Task { [weak self] in
            guard let self = self else { return }

            let muscles = await wikiLabDao.getAllMuscleGroups()
            let joints = await wikiLabDao.getAllJoints()
            let tendons = await wikiLabDao.getAllTendons()
            let patterns = await wikiLabDao.getAllMovementPatterns()
            let chains = await wikiLabDao.getAllKineticChains()

            self.musclesLock.lock()
            self._muscles = muscles
            self.musclesLock.unlock()

            self.jointsLock.lock()
            self._joints = joints
            self.jointsLock.unlock()

            self.tendonsLock.lock()
            self._tendons = tendons
            self.tendonsLock.unlock()

            self.patternsLock.lock()
            self._patterns = patterns
            self.patternsLock.unlock()

            self.chainsLock.lock()
            self._chains = chains
            self.chainsLock.unlock()
        }
    }

    public func getMuscleById(id: String) -> MuscleGroupEntity? {
        musclesLock.lock()
        defer { musclesLock.unlock() }
        return _muscles.first { $0.id == id }
    }

    public func getJointById(id: String) -> JointEntity? {
        jointsLock.lock()
        defer { jointsLock.unlock() }
        return _joints.first { $0.id == id }
    }

    public func getTendonById(id: String) -> TendonEntity? {
        tendonsLock.lock()
        defer { tendonsLock.unlock() }
        return _tendons.first { $0.id == id }
    }

    public func getPatternById(id: String) -> MovementPatternEntity? {
        patternsLock.lock()
        defer { patternsLock.unlock() }
        return _patterns.first { $0.id == id }
    }

    public func getChainById(id: String) -> KineticChainEntity? {
        chainsLock.lock()
        defer { chainsLock.unlock() }
        return _chains.first { $0.id == id }
    }

    public func parseStringList(jsonStr: String?) -> [String] {
        guard let jsonStr = jsonStr else { return [] }
        guard let data = jsonStr.data(using: .utf8) else { return [] }
        return (try? jsonDecoder.decode([String].self, from: data)) ?? []
    }

    public func parseInjuries(jsonStr: String?) -> [InjuryInfo] {
        guard let jsonStr = jsonStr else { return [] }
        guard let data = jsonStr.data(using: .utf8) else { return [] }
        return (try? jsonDecoder.decode([InjuryInfo].self, from: data)) ?? []
    }

    public func getMusclesByBodyPart(bodyPart: String) -> [MuscleGroupEntity] {
        musclesLock.lock()
        defer { musclesLock.unlock() }
        return _muscles.filter { $0.bodyPart == bodyPart }
    }

    public func getJointsByBodyPart(bodyPart: String) -> [JointEntity] {
        jointsLock.lock()
        defer { jointsLock.unlock() }
        return _joints.filter { $0.bodyPart == bodyPart }
    }

    public func getTendonsByMuscle(muscleId: String) -> [TendonEntity] {
        tendonsLock.lock()
        defer { tendonsLock.unlock() }
        return _tendons.filter { $0.muscleId == muscleId }
    }

    public func getTendonsByJoint(jointId: String) -> [TendonEntity] {
        tendonsLock.lock()
        defer { tendonsLock.unlock() }
        return _tendons.filter { $0.jointId == jointId }
    }

    public let bodyPartCategories: [String] = ["upper", "lower", "core", "spine"]

    public func getBodyPartLabel(bodyPart: String?) -> String {
        guard let bodyPart = bodyPart else { return "Otro" }
        switch bodyPart {
        case "upper": return "Tren Superior"
        case "lower": return "Tren Inferior"
        case "core": return "Core"
        case "spine": return "Columna"
        default: return "Otro"
        }
    }

    public func getJointTypeLabel(type: String) -> String {
        switch type {
        case "hinge": return "Bisagra"
        case "ball-socket": return "Esferoidea"
        case "pivot": return "Pivote"
        case "gliding": return "Deslizante"
        case "saddle": return "Silla de montar"
        case "condyloid": return "Condílea"
        default: return type
        }
    }
}

public struct InjuryInfo: Codable {
    public let name: String
    public let description: String?
    public let contraindications: [String]?
    public let returnProgressions: [String]?
    
    enum CodingKeys: String, CodingKey {
        case name, description, contraindications
        case returnProgressions = "returnProgressions"
    }
}
