import Foundation
import CryptoKit

/// Payloads preserve unknown fields, canonical SI magnitudes, civil dates and original provenance.
public indirect enum PersonalJSON: Codable, Equatable, Sendable {
    case object([String: PersonalJSON]), array([PersonalJSON]), string(String), integer(Int64), number(Double), bool(Bool), null
    public init(from decoder: Decoder) throws {
        let value = try decoder.singleValueContainer()
        if value.decodeNil() { self = .null }
        else if let item = try? value.decode(Bool.self) { self = .bool(item) }
        else if let item = try? value.decode(Int64.self) { self = .integer(item) }
        else if let item = try? value.decode(Double.self), item.isFinite { self = .number(item) }
        else if let item = try? value.decode(String.self) { self = .string(item) }
        else if let item = try? value.decode([PersonalJSON].self) { self = .array(item) }
        else { self = .object(try value.decode([String: PersonalJSON].self)) }
    }
    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .object(let value): try container.encode(value)
        case .array(let value): try container.encode(value)
        case .string(let value): try container.encode(value)
        case .integer(let value): try container.encode(value)
        case .number(let value): try container.encode(value)
        case .bool(let value): try container.encode(value)
        case .null: try container.encodeNil()
        }
    }
}
public struct PrivateDataReference: Codable, Equatable, Sendable { public let sha256: String; public let bytes: Int64 }
public struct PersonalOperation: Codable, Equatable, Sendable {
    public static func entityIdentity(for source: String) -> String {
        if source.range(of: "^[A-Za-z0-9_.:-]{1,128}$", options: .regularExpression) != nil { return source }
        return "r_" + SHA256.hash(data: Data(source.utf8)).map { String(format: "%02x", $0) }.joined()
    }
    public let schemaVersion: Int
    public let operationId: String
    public let entityId: String
    public let sourceId: String?
    public let deviceId: String
    public let accountGeneration: Int64
    public let baseRevision: Int64
    public let kind: String
    public let changedAtEpochMs: Int64
    public let zoneId: String
    public let unitSystem: String
    public let deleted: Bool
    public let payload: PersonalJSON?
    public let payloadRef: PrivateDataReference?
    public let guestImportId: String?
    public func validateEnvelope() throws {
        guard schemaVersion == 1, accountGeneration >= 1, baseRevision >= 0,
              ["METRIC", "IMPERIAL"].contains(unitSystem), changedAtEpochMs >= 0,
              !zoneId.isEmpty, zoneId.count <= 80,
              [operationId, entityId, deviceId].allSatisfy({ $0.range(of: "^[A-Za-z0-9_.:-]{1,128}$", options: .regularExpression) != nil }),
              sourceId == nil || (!sourceId!.isEmpty && sourceId!.utf16.count <= 4096 && entityId == Self.entityIdentity(for: sourceId!)) else { throw ContractError.invalidEnvelope }
        if deleted { guard payload == nil && payloadRef == nil else { throw ContractError.invalidEnvelope } }
        else if let reference = payloadRef {
            guard payload == nil, reference.bytes > 0, reference.bytes <= 67_108_864,
                  reference.sha256.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil else { throw ContractError.invalidEnvelope }
        } else { guard case .object? = payload else { throw ContractError.invalidEnvelope } }
    }
    // Explicit null tombstones are required by the server protocol; synthesized encodeIfPresent would omit them.
    private enum CodingKeys: String, CodingKey { case schemaVersion, operationId, entityId, sourceId, deviceId, accountGeneration,
        baseRevision, kind, changedAtEpochMs, zoneId, unitSystem, deleted, payload, payloadRef, guestImportId }
    public func encode(to encoder: Encoder) throws {
        var values = encoder.container(keyedBy: CodingKeys.self)
        try values.encode(schemaVersion, forKey: .schemaVersion); try values.encode(operationId, forKey: .operationId)
        try values.encode(entityId, forKey: .entityId); try values.encodeIfPresent(sourceId, forKey: .sourceId)
        try values.encode(deviceId, forKey: .deviceId); try values.encode(accountGeneration, forKey: .accountGeneration)
        try values.encode(baseRevision, forKey: .baseRevision); try values.encode(kind, forKey: .kind)
        try values.encode(changedAtEpochMs, forKey: .changedAtEpochMs); try values.encode(zoneId, forKey: .zoneId)
        try values.encode(unitSystem, forKey: .unitSystem); try values.encode(deleted, forKey: .deleted)
        try values.encode(payload, forKey: .payload); try values.encodeIfPresent(payloadRef, forKey: .payloadRef)
        try values.encodeIfPresent(guestImportId, forKey: .guestImportId)
    }
}
public enum ContractError: Error { case invalidEnvelope }
