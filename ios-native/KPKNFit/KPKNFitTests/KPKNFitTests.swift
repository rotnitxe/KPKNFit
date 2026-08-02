import XCTest
@testable import KPKNFit

final class KPKNFitTests: XCTestCase {
    func testApprovedExerciseCatalogV2LoadsWithUniqueIdentity() throws {
        let repository = try ExerciseCatalogV2Repository(bundle: Bundle(for: Self.self))
        XCTAssertEqual(repository.catalog.schemaVersion, 2)
        XCTAssertEqual(repository.catalog.catalogRevision, "v2-approved-2026-08-02")
        XCTAssertFalse(repository.catalog.families.isEmpty)

        let definitions = repository.catalog.families.flatMap(\.definitions)
        let configurations = definitions.flatMap(\.configurations)
        XCTAssertEqual(Set(definitions.map(\.id).count), definitions.count)
        XCTAssertEqual(Set(configurations.map(\.id).count), configurations.count)
        XCTAssertTrue(configurations.allSatisfy { $0.profile.automationEligible })

        for definition in definitions {
            XCTAssertTrue(definition.configurations.contains { $0.id == definition.defaultConfigurationId })
        }
    }

    func testExactResolutionSearchAndRevisionGuard() throws {
        let repository = try ExerciseCatalogV2Repository(bundle: Bundle(for: Self.self))
        let definition = try XCTUnwrap(repository.catalog.families.first?.definitions.first)
        let configuration = try repository.defaultConfiguration(for: definition.id)

        XCTAssertNoThrow(try repository.resolve(
            definitionId: definition.id,
            configurationId: configuration.id,
            catalogRevision: repository.catalogRevision
        ))
        XCTAssertThrowsError(try repository.resolve(
            definitionId: definition.id,
            configurationId: configuration.id,
            catalogRevision: "wrong-revision"
        ))
        XCTAssertFalse(repository.search(definition.canonicalName).isEmpty)
        XCTAssertTrue(repository.search("id-que-no-existe").isEmpty)
    }
}
