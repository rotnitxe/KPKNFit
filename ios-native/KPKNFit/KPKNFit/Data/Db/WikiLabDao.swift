import Foundation

internal final class WikiLabDaoImpl: WikiLabDao {
    func getAllMuscleGroups() async -> [MuscleGroupEntity] {
        []
    }

    func getAllJoints() async -> [JointEntity] {
        []
    }

    func getAllTendons() async -> [TendonEntity] {
        []
    }

    func getAllMovementPatterns() async -> [MovementPatternEntity] {
        []
    }

    func getAllKineticChains() async -> [KineticChainEntity] {
        []
    }

    func insertMuscleGroup(_ entity: MuscleGroupEntity) async {}

    func insertJoint(_ entity: JointEntity) async {}

    func insertTendon(_ entity: TendonEntity) async {}

    func insertMovementPattern(_ entity: MovementPatternEntity) async {}

    func insertKineticChain(_ entity: KineticChainEntity) async {}

    func clearAll() async {}

    func count() async -> Int {
        0
    }
}
