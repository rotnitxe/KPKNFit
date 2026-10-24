import XCTest
@testable import KPKNFit

final class KPKNFitTests: XCTestCase {
    func testApprovedExerciseCatalogV2LoadsWithUniqueIdentity() throws {
        let repository = try ExerciseCatalogV2Repository(bundle: Bundle(for: Self.self))
        XCTAssertEqual(repository.catalog.schemaVersion, 2)
        XCTAssertEqual(repository.catalog.catalogRevision, "v2-approved-2026-09-29-a")
        XCTAssertFalse(repository.catalog.families.isEmpty)

        let definitions = repository.catalog.families.flatMap(\.definitions)
        let configurations = definitions.flatMap(\.configurations)
        XCTAssertEqual(Set(definitions.map(\.id)).count, definitions.count)
        XCTAssertEqual(Set(configurations.map(\.id)).count, configurations.count)
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

    func testProgramDecodeEditEncodePreservesAndroidPlanTransport() throws {
        let decoder = JSONDecoder()
        let program = try decoder.decode(Program.self, from: Data(Self.planTransportFixture.utf8))
        let macro = try XCTUnwrap(program.macrocycles.first)
        let block = try XCTUnwrap(macro.blocks.first)
        let mesocycle = try XCTUnwrap(block.mesocycles.first)
        let week = try XCTUnwrap(mesocycle.weeks.first)
        let session = try XCTUnwrap(week.sessions.first)
        let strengthPart = try XCTUnwrap(session.parts.first)
        let exercise = try XCTUnwrap(strengthPart.exercises.first)
        let firstSet = try XCTUnwrap(exercise.sets.first)

        // Edit an existing set using the same immutable copy path as the live editor.
        let editedExercise = exercise.copy(
            name: "Press banca DB local",
            sets: [firstSet.copy(targetReps: 4)]
        )
        let editedSession = session.copy(parts: [
            strengthPart.copy(exercises: [editedExercise]),
            try XCTUnwrap(session.parts.dropFirst().first),
        ])
        let upsertedProgram = try XCTUnwrap(program.upsertSessionInWeek(
            weekId: week.id,
            macroIndex: 0,
            mesoIndex: 0,
            session: editedSession,
            createdAtMs: 1234
        ))
        let editedProgram = upsertedProgram.copy(
            name: "Programa local editado"
        )

        let encoded = try JSONEncoder().encode(editedProgram)
        let roundTripped = try decoder.decode(Program.self, from: encoded)
        let resultWeek = try XCTUnwrap(roundTripped.macrocycles.first?.blocks.first?.mesocycles.first?.weeks.first)
        let resultSession = try XCTUnwrap(resultWeek.sessions.first)
        let resultExercise = try XCTUnwrap(resultSession.parts.first?.exercises.first)
        let resultSet = try XCTUnwrap(resultExercise.sets.first)

        XCTAssertEqual(roundTripped.name, "Programa local editado")
        XCTAssertEqual(resultSet.targetReps, 4)
        XCTAssertEqual(roundTripped.opaqueFields["sourceRecipe"], program.opaqueFields["sourceRecipe"])
        XCTAssertEqual(roundTripped.opaqueFields["sourceProtocolId"], .string("phat-verified"))
        XCTAssertEqual(roundTripped.opaqueFields["planWarmupConfig"], program.opaqueFields["planWarmupConfig"])
        XCTAssertEqual(roundTripped.opaqueFields["planProvenance"], program.opaqueFields["planProvenance"])
        XCTAssertEqual(roundTripped.opaqueFields["exerciseLoadReferences"], program.opaqueFields["exerciseLoadReferences"])
        XCTAssertEqual(roundTripped.opaqueFields["effectiveWeekRecipes"], program.opaqueFields["effectiveWeekRecipes"])

        let overrideValue = try XCTUnwrap(roundTripped.opaqueFields["manualSessionOverrides"])
        guard case .array(let overrides) = overrideValue,
              let firstOverride = overrides.first,
              case .object(let override) = firstOverride else {
            return XCTFail("Expected the edited session to be recorded as a manual override")
        }
        XCTAssertEqual(override["sessionId"], .string("session-1"))
        XCTAssertEqual(override["weekId"], .string("week-1"))
        XCTAssertEqual(override["recipeDayId"], .string("day-a"))
        XCTAssertEqual(override["scope"], .string("SESSION"))
        XCTAssertEqual(override["weekOccurrence"], .integer(1))
        XCTAssertEqual(override["cycleNumber"], .integer(1))
        XCTAssertEqual(override["createdAtMs"], .integer(1234))

        XCTAssertEqual(resultWeek.opaqueFields["executionKind"], .string("TRAINING"))
        XCTAssertEqual(resultSession.opaqueFields["persistedRuleDefaults"], programSessionField("persistedRuleDefaults", in: session))
        XCTAssertEqual(resultSession.opaqueFields["recipeDayId"], .string("day-a"))
        XCTAssertEqual(resultBlockField("sourceDefinitionId", in: roundTripped), .string("native:complete-athlete-v2"))
        XCTAssertEqual(resultBlockField("materializationPending", in: roundTripped), .bool(true))

        let resultParts = resultSession.parts
        let cardioPart = try XCTUnwrap(resultParts.first(where: { $0.id == "cardio-part" }))
        XCTAssertEqual(cardioPart.opaqueFields["isCardioGroup"], .bool(true))
        XCTAssertEqual(cardioPart.opaqueFields["cardioPrescription"], .object([
            "type": .string("WALK"),
            "targetDurationSeconds": .integer(1200),
        ]))
        let cardioExercise = try XCTUnwrap(cardioPart.exercises.first)
        XCTAssertEqual(cardioExercise.opaqueFields["cardioDetails"], .object([
            "type": .string("WALK"),
            "targetDurationSeconds": .integer(1200),
            "hiit": .object(["workSeconds": .integer(60), "restSeconds": .integer(60)]),
        ]))
        XCTAssertEqual(resultSet.opaqueFields["targetRepsRange"], .object(["min": .integer(3), "max": .integer(5)]))
        XCTAssertEqual(resultSet.opaqueFields["restAfterSeconds"], .integer(180))
        XCTAssertEqual(resultSet.opaqueFields["loadBasis"], .string("PERCENT_TM"))
        XCTAssertEqual(resultExercise.opaqueFields["recipeSlotId"], .string("slot-bench"))
        XCTAssertEqual(resultExercise.opaqueFields["loadQuantityConvention"], .string("TOTAL_EXTERNAL"))
    }

    func testSessionUpsertUsesFlattenedMesocycleIndexAcrossBlocks() throws {
        let originalSession = Session(id: "target-session", name: "Original")
        let targetWeek = ProgramWeek(
            id: "target-week",
            name: "Target week",
            sessions: [originalSession],
            progressionIndex: 2,
            opaqueFields: ["executionKind": .string("TRAINING")]
        )
        let program = Program(
            id: "flattened-meso-upsert",
            name: "Recipe-backed",
            macrocycles: [
                Macrocycle(id: "macro", name: "Macro", blocks: [
                    Block(id: "block-1", name: "First", mesocycles: [
                        Mesocycle(id: "meso-1", name: "First", weeks: [
                            ProgramWeek(id: "first-week", name: "First", sessions: [
                                Session(id: "first-session", name: "Keep me")
                            ])
                        ])
                    ]),
                    Block(id: "block-2", name: "Second", mesocycles: [
                        Mesocycle(id: "meso-2", name: "Second", weeks: [targetWeek])
                    ])
                ])
            ],
            opaqueFields: ["sourceRecipe": .object(["id": .string("recipe")])]
        )
        let editedSession = Session(
            id: originalSession.id,
            name: "Edited",
            opaqueFields: ["recipeDayId": .string("day-target")]
        )

        let updated = try XCTUnwrap(program.upsertSessionInWeek(
            weekId: targetWeek.id,
            macroIndex: 0,
            mesoIndex: 1,
            session: editedSession,
            createdAtMs: 5678
        ))

        XCTAssertEqual(updated.macrocycles[0].blocks[0].mesocycles[0].weeks[0].sessions[0].name, "Keep me")
        XCTAssertEqual(updated.macrocycles[0].blocks[1].mesocycles[0].weeks[0].sessions[0].name, "Edited")
        XCTAssertEqual(updated.macrocycles[0].blocks[1].mesocycles[0].weeks[0].opaqueFields["executionKind"], .string("TRAINING"))
        XCTAssertEqual(updated.opaqueFields["sourceRecipe"], program.opaqueFields["sourceRecipe"])
        guard case .array(let overrides)? = updated.opaqueFields["manualSessionOverrides"],
              case .object(let override)? = overrides.first else {
            return XCTFail("Expected the upsert to record a recipe-backed manual override")
        }
        XCTAssertEqual(override["sessionId"], .string("target-session"))
        XCTAssertEqual(override["weekId"], .string("target-week"))
        XCTAssertEqual(override["weekOccurrence"], .integer(2))
        XCTAssertEqual(override["recipeDayId"], .string("day-target"))
        XCTAssertEqual(override["createdAtMs"], .integer(5678))
    }

    private func programSessionField(_ key: String, in session: Session) -> JSONValue? {
        session.opaqueFields[key]
    }

    private func resultBlockField(_ key: String, in program: Program) -> JSONValue? {
        program.macrocycles.first?.blocks.first?.opaqueFields[key]
    }

    private static let planTransportFixture = #"""
    {
      "id": "plan-transport-ios",
      "name": "Atleta completo",
      "mode": "POWERBUILDING",
      "structure": "SIMPLE",
      "sourceProtocolId": "phat-verified",
      "planWarmupConfig": [{"targetReps": 8, "loadFraction": 0.4}],
      "sourceRecipe": {
        "id": "native:complete-athlete-v2",
        "contentVersion": 2,
        "weeks": [{"id": "week-recipe-1", "sessions": [{"id": "day-a", "slots": []}]}]
      },
      "planProvenance": {
        "planId": "native:complete-athlete-v2",
        "category": "KPKN",
        "sourceEdition": "r1",
        "parentRevision": null,
        "slotChanges": []
      },
      "exerciseLoadReferences": [{"exerciseId": "ex-1", "references": [{"state": "PENDING", "kind": "EXERCISE_TM"}]}],
      "effectiveWeekRecipes": [{"weekOccurrence": 1, "cycleNumber": 1, "version": 1, "changes": []}],
      "manualSessionOverrides": [],
      "runState": {"cycleNumber": 1, "activeOccurrenceId": "occ-1"},
      "macrocycles": [{
        "id": "macro-1",
        "name": "Macrociclo",
        "blocks": [{
          "id": "block-1",
          "name": "Bloque",
          "goal": "ACCUMULATION",
          "progressionScheme": "LINEAR_LOAD",
          "sourceDefinitionId": "native:complete-athlete-v2",
          "materializationPending": true,
          "mesocycles": [{
            "id": "meso-1",
            "name": "Mesociclo",
            "goal": "ACCUMULATION",
            "weeks": [{
              "id": "week-1",
              "name": "Semana 1",
              "progressionIndex": 1,
              "executionKind": "TRAINING",
              "sessions": [{
                "id": "session-1",
                "name": "Día A",
                "dayOfWeek": 1,
                "recipeDayId": "day-a",
                "origin": "GENERATED_PLACEHOLDER",
                "requirement": "REQUIRED",
                "cardioFirst": false,
                "persistedRuleDefaults": {"setCount": 3, "reps": 10},
                "targetDurationMinutes": 45,
                "parts": [{
                  "id": "strength-part",
                  "name": "Fuerza",
                  "isCardioGroup": false,
                  "exercises": [{
                    "id": "ex-1",
                    "name": "Press banca DB",
                    "exerciseDbId": "bench-db",
                    "recipeDayId": "day-a",
                    "recipeSlotId": "slot-bench",
                    "slotRole": "T1_MAIN",
                    "loadQuantityConvention": "TOTAL_EXTERNAL",
                    "sets": [{
                      "id": "set-1",
                      "targetReps": 5,
                      "targetRepsRange": {"min": 3, "max": 5},
                      "targetRIR": 2,
                      "restAfterSeconds": 180,
                      "loadBasis": "PERCENT_TM",
                      "loadReference": {"state": "PENDING", "kind": "EXERCISE_TM"}
                    }]
                  }]
                }, {
                  "id": "cardio-part",
                  "name": "Cardio",
                  "isCardioGroup": true,
                  "targetDurationMinutes": 20,
                  "cardioPrescription": {"type": "WALK", "targetDurationSeconds": 1200},
                  "exercises": [{
                    "id": "cardio-1",
                    "name": "Caminata",
                    "trainingMode": "TIME",
                    "cardioDetails": {
                      "type": "WALK",
                      "targetDurationSeconds": 1200,
                      "hiit": {"workSeconds": 60, "restSeconds": 60}
                    },
                    "sets": []
                  }]
                }]
              }]
            }]
          }]
        }]
      }]
    }
    """#

    // MARK: — Presencia en `NutritionPlan` / `deriveMacroGoals`
    // Contrato: plan activo manda (0 y nil incluidos, sin fallback a
    // ajustes); sin plan, solo objetivos explícitos de ajustes; sin evidencia,
    // todo nil. Gemelo de Android `dayGoalForecastOf` + `macroGoalsOf`.

    private func settingsWithGoals999() -> Settings {
        var settings = Settings()
        settings.dailyCalorieGoal = 999
        settings.dailyProteinGoal = 999
        settings.dailyCarbGoal = 999
        settings.dailyFatGoal = 999
        return settings
    }

    /// Oráculo: plan activo (2000 kcal, proteína 0, grasa 0) frente a ajustes
    /// 999 → 2000 / 0 / 0. Los 0 explícitos no se borran y los ajustes no
    /// sustituyen al plan.
    func testActivePlanWinsOverSettingsEvenAtZero() {
        let plan = NutritionPlan(calorieTarget: 2000, proteinGoal: 0, carbGoal: nil, fatGoal: 0)
        let goals = deriveMacroGoals(settings: settingsWithGoals999(), activePlan: plan)
        XCTAssertEqual(goals.calorieGoal, 2000)
        XCTAssertEqual(goals.proteinGoal, 0)
        XCTAssertEqual(goals.fatGoal, 0)
        XCTAssertNil(goals.carbGoal)
    }

    /// Oráculo: plan activo sin campos de macro (todos nil) → ausencia real,
    /// sin caer a los ajustes 999. Sin plan y sin ajustes → todo nil.
    func testActivePlanWithoutMacroFieldsDoesNotFallBackToSettings() {
        let plan = NutritionPlan(id: "p-vacio", name: "Vacío")
        let goals = deriveMacroGoals(settings: settingsWithGoals999(), activePlan: plan)
        XCTAssertNil(goals.calorieGoal)
        XCTAssertNil(goals.proteinGoal)
        XCTAssertNil(goals.carbGoal)
        XCTAssertNil(goals.fatGoal)

        let noEvidence = deriveMacroGoals(settings: Settings())
        XCTAssertNil(noEvidence.calorieGoal)
        XCTAssertNil(noEvidence.proteinGoal)
        XCTAssertNil(noEvidence.carbGoal)
        XCTAssertNil(noEvidence.fatGoal)
    }

    /// Oráculo de decodificación: clave ausente → nil; 0 explícito → 0.
    /// (Ausencia y 0 no son lo mismo.)
    func testPlanMacroFieldMissingDecodesNilAndExplicitZeroDecodesZero() throws {
        let jsonBase = """
        {"id":"p1","name":"Plan","goalType":"WEIGHT","goalValue":70.0,"isActive":true,"createdAt":"2026-09-24","weeklyChangeKg":0.5}
        """
        let jsonWithZeros = """
        {"id":"p1","name":"Plan","goalType":"WEIGHT","goalValue":70.0,"isActive":true,"createdAt":"2026-09-24","weeklyChangeKg":0.5,"calorieTarget":0,"proteinGoal":0}
        """
        let decoder = JSONDecoder()

        let missing = try decoder.decode(NutritionPlan.self, from: Data(jsonBase.utf8))
        XCTAssertNil(missing.calorieTarget)
        XCTAssertNil(missing.proteinGoal)
        XCTAssertNil(missing.carbGoal)
        XCTAssertNil(missing.fatGoal)

        let explicitZero = try decoder.decode(NutritionPlan.self, from: Data(jsonWithZeros.utf8))
        XCTAssertEqual(explicitZero.calorieTarget, 0)
        XCTAssertEqual(explicitZero.proteinGoal, 0)
    }

    /// Oráculo: meta calórica declarada en 0 nunca se usa como divisor
    /// (ratio queda 1.0): sin crash ni NaN, y la meta de proteína > 0 sí se
    /// evalúa. Mismo criterio que las guardas `goal > 0` de Android.
    func testZeroCalorieGoalIsNotUsedAsDivisor() {
        let plan = NutritionPlan(calorieTarget: 0, proteinGoal: 100, carbGoal: 0, fatGoal: 0)
        let goals = deriveMacroGoals(settings: settingsWithGoals999(), activePlan: plan)
        XCTAssertEqual(goals.calorieGoal, 0)
        XCTAssertEqual(goals.proteinGoal, 100)

    }
}

/// Cloud supplies source coverage; execution requires the macOS gate.
final class PostRingsTrainingEvidenceTests: XCTestCase {
    func testRetiredPendingActionsDoNotChangeAuthoredData() {
        let original: [String:Any] = ["id":"p","notes":"Nota original","runState":["runId":"run","weekId":"w","pendingAction":["type":"CONFIRM_DELOAD"]]]
        let migrated = LegacyProgramProposalImport.program(original)
        XCTAssertEqual(migrated["notes"] as? String,"Nota original")
        let run = migrated["runState"] as? [String:Any]
        XCTAssertEqual(run?["runId"] as? String,"run")
        XCTAssertEqual(run?["weekId"] as? String,"w")
        XCTAssertTrue(run?["pendingAction"] is NSNull)
    }
    func testExplicitZeroAndOriginalMinutePrecisionStayDifferent() {
        let log = WorkoutLog(id:"log",programId:"p",sessionId:"s",sessionName:"Registro",date:"2026-10-23T12:00:00Z",durationMinutes:17,
            sessionEffortRpe:0,sessionEffortProtocol:"own-scale",sportDocuments:[])
        let now: Int64 = 1_792_843_200_000
        // No discipline is fabricated from an empty log; explicit originals remain in the source.
        XCTAssertTrue(TrainingHistoryProjection.facts([log],nowMs:now).isEmpty)
        XCTAssertEqual(log.sessionEffortRpe,0)
        XCTAssertNil(log.actualDurationSeconds)
    }
    func testRetiredEnergyIsPurgedWithoutLosingOriginalNotesAndPreciseTime() {
        let old = WorkoutLog(id:"l",programId:"p",sessionId:"s",sessionName:"Original",date:"2026-10-23T12:00:00Z",durationMinutes:17,
            notes:"Nota original",energySummary:SessionEnergySummary(totalKcal:CalorieRange(low:100,mid:120,high:140),source:.FINAL,methodVersion:"auge-energy-v1"),
            actualDurationSeconds:61,recordedSportId:"BOXING")
        let restored = old.toEntity().toWorkoutLog()
        XCTAssertNil(restored.energySummary)
        XCTAssertEqual(restored.notes,old.notes)
        XCTAssertEqual(restored.actualDurationSeconds,61)
        XCTAssertEqual(restored.recordedSportId,"BOXING")
        let zero = WorkoutLog(id:"zero",programId:"p",sessionId:"s",sessionName:"Original",date:old.date,durationMinutes:1,
            energySummary:SessionEnergySummary(totalKcal:CalorieRange(low:0,mid:0,high:0),source:.FINAL,methodVersion:"independent-fixture-v1"))
        XCTAssertEqual(zero.toEntity().toWorkoutLog().energySummary?.totalKcal.mid,0)
    }
    func testUnknownContextsNeverBecomeComparable() {
        let fact = TrainingEvidenceFact(sourceId:"x",activityId:"l",occurredAtMs:1,sport:"JUMP",metric:"height",value:31.25,unit:"cm",origin:"OBSERVED",exerciseVariant:nil,method:nil,protocolId:nil,context:[:])
        XCTAssertFalse(TrainingInsightsEngine.comparable(fact,fact))
        XCTAssertTrue(TrainingInsightsEngine.visible([fact],nowMs:0).isEmpty)
        XCTAssertNil(TrainingInsightsEngine.median([fact,fact]))
    }
}

final class RecordedSportContextTests: XCTestCase {
    func testCorrectionKeepsExplicitDisciplineTimeEffortAndOriginalNotes() throws {
        let original = WorkoutLog(id:"activity",programId:"p",sessionId:"s",sessionName:"Registro",date:"2026-10-23T12:00:00Z",durationMinutes:2,
            actualDurationSeconds:61,actualDurationSource:"SESSION_TIMER",sessionEffortRpe:0,sessionEffortProtocol:"session_rpe_0_10_v1",
            sportDocuments:[],recordedSportId:"BOXING",preSessionNotes:"Contexto original")
        let corrected = original.copy(completedExercises:[])
        XCTAssertEqual(corrected.recordedSportId,"BOXING")
        XCTAssertEqual(corrected.preSessionNotes,"Contexto original")
        XCTAssertEqual(corrected.actualDurationSeconds,61)
        XCTAssertEqual(corrected.sessionEffortRpe,0)
        let restored = try JSONDecoder().decode(WorkoutLog.self,from:JSONEncoder().encode(corrected))
        XCTAssertEqual(restored.recordedSportId,original.recordedSportId)
        XCTAssertEqual(restored.actualDurationSeconds,original.actualDurationSeconds)
    }
    func testEveryDeclaredDisciplineUsesAnExistingActivityWithoutAnExtraDocument() {
        let now: Int64 = 1_792_843_200_000
        for id in RecordedSportNames.labels.keys {
            let log = WorkoutLog(id:id,programId:"p",sessionId:"s",sessionName:"Registro",date:"2026-10-23T12:00:00Z",durationMinutes:17,recordedSportId:id)
            let facts = TrainingHistoryProjection.facts([log],nowMs:now)
            XCTAssertEqual(facts.count,1)
            XCTAssertEqual(facts.first?.sport,id)
            XCTAssertEqual(facts.first?.unit,"min")
            XCTAssertEqual(facts.first?.value,17)
        }
    }
}

final class RegisteredTrainingDocumentTests: XCTestCase {
    func testUnknownEnginePreservesRawDataWithoutInventingMeasurements() throws {
        let raw = #"{"id":"doc","engineId":"future.unknown","version":1,"sportId":"RUNNING","purpose":"RESULT","payload":{"distanceMeters":5000}}"#
        let document = try JSONDecoder().decode(SportTrackingDocument.self,from:Data(raw.utf8))
        let log = WorkoutLog(id:"l",programId:"p",sessionId:"s",sessionName:"Registro",date:"2026-10-23T12:00:00Z",durationMinutes:17,sportDocuments:[document])
        let facts = TrainingHistoryProjection.facts([log],nowMs:1_792_843_200_000)
        XCTAssertFalse(facts.contains { $0.sourceId.contains("doc") })
        let restored = try JSONDecoder().decode(WorkoutLog.self,from:JSONEncoder().encode(log))
        XCTAssertEqual(restored.sportDocuments?.first?.engineId,"future.unknown")
        XCTAssertEqual(restored.sportDocuments?.first?.payload.count,1)
    }
}
