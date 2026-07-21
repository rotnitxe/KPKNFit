import Foundation

public struct AugeDbMetrics: Codable {
    public let efc: Double
    public let cnc: Double
    public let ssc: Double
    public let axialLoad: Double?

    public init(efc: Double, cnc: Double, ssc: Double, axialLoad: Double? = nil) {
        self.efc = efc
        self.cnc = cnc
        self.ssc = ssc
        self.axialLoad = axialLoad
    }

    public var snc: Double { cnc }
}

public final class AugeMetricsRepository {
    private let exerciseIndex: [String: ExerciseMuscleInfo]

    public init(exerciseIndex: [String: ExerciseMuscleInfo] = [:]) {
        self.exerciseIndex = exerciseIndex
    }

    public func metricsFor(exerciseDbId: String?) -> AugeDbMetrics? {
        guard let id = exerciseDbId?.lowercased() else { return nil }
        guard let info = exerciseIndex[id] else { return nil }
        guard let efc = info.efc else { return nil }
        guard let cnc = info.cnc else { return nil }
        guard let ssc = info.ssc else { return nil }
        return AugeDbMetrics(
            efc: min(max(efc, 1.0), 5.0),
            cnc: min(max(cnc, 1.0), 5.0),
            ssc: min(max(ssc, 0.0), 2.0),
            axialLoad: info.axialLoadFactor
        )
    }

    public static func from(exerciseList: [ExerciseMuscleInfo]) -> AugeMetricsRepository {
        let index = Dictionary(exerciseList.map { ($0.id.lowercased(), $0) }, uniquingKeysWith: { first, _ in first })
        return AugeMetricsRepository(exerciseIndex: index)
    }
}
