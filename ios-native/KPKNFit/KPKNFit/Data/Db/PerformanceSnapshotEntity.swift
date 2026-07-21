import Foundation

// MARK: - JSON Helpers

private let dbJson: JSONEncoder = {
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.sortedKeys]
    encoder.dateEncodingStrategy = .iso8601
    return encoder
}()

private let dbJsonDecoder: JSONDecoder = {
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .iso8601
    decoder.keyDecodingStrategy = .useDefaultKeys
    return decoder
}()

private func encodeModel<T: Encodable>(_ value: T) -> String {
    guard let data = try? dbJson.encode(value) else { return "{}" }
    return String(decoding: data, as: UTF8.self)
}

private func decodeModel<T: Decodable>(_ type: T.Type, from data: String) -> T? {
    try? dbJsonDecoder.decode(type, from: Data(data.utf8))
}

// MARK: - PerformanceSnapshotData

internal struct PerformanceSnapshotData: Codable {
    let contextKey: String
    let sessionId: String
    let erm: Double
    let setCount: Int
    let avgRpe: Double?
    let reachedFailure: Bool
    let recordedAtMs: Int64
    let isTechnicalInvalid: Bool
}

// MARK: - PerformanceSnapshotEntity

internal struct PerformanceSnapshotEntity: Codable {
    let id: Int64
    let contextKey: String
    let data: String
}

// MARK: - Conversions

extension PerformanceSnapshotData {
    func toEntity() -> PerformanceSnapshotEntity {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let resolvedRecordedAt = recordedAtMs == 0 ? now : recordedAtMs
        let dataToEncode = PerformanceSnapshotData(
            contextKey: contextKey,
            sessionId: sessionId,
            erm: erm,
            setCount: setCount,
            avgRpe: avgRpe,
            reachedFailure: reachedFailure,
            recordedAtMs: resolvedRecordedAt,
            isTechnicalInvalid: isTechnicalInvalid
        )
        return PerformanceSnapshotEntity(id: 0, contextKey: contextKey, data: encodeModel(dataToEncode))
    }
}

extension PerformanceSnapshotEntity {
    func toPerformanceSnapshotData() -> PerformanceSnapshotData {
        decodeModel(PerformanceSnapshotData.self, from: data)!
    }
}
