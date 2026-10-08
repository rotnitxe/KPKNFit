import Foundation
import XCTest
@testable import KPKNContracts
final class PersonalOperationTests: XCTestCase {
    func testStablePublicIdentityMatchesTheSharedUtf8Hash() {
        XCTAssertEqual(PersonalOperation.entityIdentity(for: "measurement-1"), "measurement-1")
        XCTAssertEqual(PersonalOperation.entityIdentity(for: "program/session/week/exercise/tag"),
            "r_27db345fabd14c5e5a84e456105a8ef57fd70e3c4ef6873911153c9d80d85830")
    }
    func testSharedFixturesRoundTripWithoutLosingUnitsOriginsOrNullTombstones() throws {
        let directory = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let source = directory.appendingPathComponent("cloud-sync/contracts/fixtures.json")
        let fixtures = try JSONDecoder().decode([PersonalOperation].self, from: Data(contentsOf: source))
        fixtures.forEach { XCTAssertNoThrow(try $0.validateEnvelope()) }
        let encoded = try JSONEncoder().encode(fixtures)
        XCTAssertEqual(fixtures, try JSONDecoder().decode([PersonalOperation].self, from: encoded))
        let values = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [[String: Any]])
        XCTAssertEqual(values.first?["unitSystem"] as? String, "IMPERIAL")
        let payload = try XCTUnwrap(values.first?["payload"] as? [String: Any])
        XCTAssertEqual(payload["unitSi"] as? String, "kg")
        XCTAssertEqual(payload["source"] as? String, "MANUAL")
        XCTAssertTrue(values.first { $0["deleted"] as? Bool == true }?["payload"] is NSNull)
    }
}
