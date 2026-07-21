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

// MARK: - PerformanceRangeData

internal struct PerformanceRangeData: Codable {
    let contextKey: String
    let ermMin: Double
    let ermMax: Double
    let ermRms: Double
    let sampleCount: Int
    let lastUpdatedMs: Int64
    let consecutiveAbove: Int
    let consecutiveBelow: Int
}

// MARK: - PerformanceRangeEntity

internal struct PerformanceRangeEntity: Codable {
    let contextKey: String
    let data: String
}

// MARK: - Conversions

extension PerformanceRangeData {
    func toEntity() -> PerformanceRangeEntity {
        PerformanceRangeEntity(contextKey: contextKey, data: encodeModel(self))
    }
}

extension PerformanceRangeEntity {
    func toPerformanceRangeData() -> PerformanceRangeData {
        decodeModel(PerformanceRangeData.self, from: data)!
    }
}
