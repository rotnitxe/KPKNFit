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

    // MARK: — NutritionRecoveryEngine (paridad con Android, commit ac3ff1c88)
    // Oracle numérico portado de android-native
    // app/src/test/java/com/example/kpkn/domain/auge/NutritionRecoveryEngineTest.kt.

    private func todayString() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }

    private func log(calories: Double, protein: Double) -> NutritionLog {
        NutritionLog(
            id: "log-\(UUID().uuidString)",
            date: todayString(),
            mealType: .LUNCH,
            foods: [
                LoggedFood(
                    id: "food-\(UUID().uuidString)",
                    foodName: "Comida",
                    amount: 100.0,
                    calories: calories,
                    protein: protein
                )
            ]
        )
    }

    func testEmptyLogsDoNotAssumeDeficit() {
        var settings = Settings()
        settings.dailyCalorieGoal = 2500
        settings.dailyProteinGoal = 150
        settings.calorieGoalObjective = .DEFICIT

        let result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs: [],
            settings: settings
        )
        XCTAssertEqual(result.recoveryTimeMultiplier, 1.0, accuracy: 0.001)
        XCTAssertEqual(result.status, .maintenance)
        XCTAssertTrue(result.factors.contains { $0.contains("Sin comidas") })
    }

    func testNoGoalsDoNotInferDeficitNorProteinPenalty() {
        // Ingesta muy baja: sin metas no se infiere déficit ni se penaliza la
        // proteína (mismo criterio neutro que «sin comidas en la ventana»).
        let settings = Settings() // sin dailyCalorieGoal/dailyProteinGoal, sin plan

        let result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs: [log(calories: 200.0, protein: 5.0)],
            settings: settings
        )
        XCTAssertEqual(result.recoveryTimeMultiplier, 1.0, accuracy: 0.001)
        XCTAssertEqual(result.status, .maintenance)
        XCTAssertTrue(result.factors.contains { $0.contains("Sin metas") })
    }

    func testZeroOrAbsentPlanGoalsDoNotInferDeficitNorProteinPenalty() {
        // Un plan con 0 explícitos conserva esos 0 como metas declaradas (no
        // son ausencia), pero la recuperación sigue neutra: las guardas exigen
        // meta > 0, igual que `explicitZeroGoals` de Android. Sin plan ni
        // ajustes con metas, todo sigue nil → mismo neutro.
        let plan = NutritionPlan(calorieTarget: 0, proteinGoal: 0, carbGoal: 0, fatGoal: 0)
        let settings = Settings()

        let goals = deriveMacroGoals(settings: settings, activePlan: plan)
        XCTAssertEqual(goals.calorieGoal, 0)
        XCTAssertEqual(goals.proteinGoal, 0)

        let result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs: [log(calories: 200.0, protein: 5.0)],
            settings: settings,
            activePlan: plan
        )
        XCTAssertEqual(result.recoveryTimeMultiplier, 1.0, accuracy: 0.001)
        XCTAssertEqual(result.status, .maintenance)
        XCTAssertTrue(result.factors.contains { $0.contains("Sin metas") })
    }

    func testCalorieDeficitIsStillInferredWhenGoalsExist() {
        // Las fórmulas no cambian: con metas reales el déficit se calcula igual
        // (200 kcal promedio vs 2000 de meta → ratio 0.1 → DEFICIT > 1.0).
        var settings = Settings()
        settings.dailyCalorieGoal = 2000
        settings.dailyProteinGoal = 100

        let result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs: [log(calories: 400.0, protein: 10.0)],
            settings: settings
        )
        XCTAssertEqual(result.status, .deficit)
        XCTAssertGreaterThan(result.recoveryTimeMultiplier, 1.0)
    }

    func testProteinPenaltyNeedsAnExplicitProteinGoal() {
        // Con meta calórica pero sin meta de proteína no se penaliza la
        // proteína: 4000 kcal/2 días = 2000 = meta → mantenimiento neutro.
        var settings = Settings()
        settings.dailyCalorieGoal = 2000
        // dailyProteinGoal queda nil

        let result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs: [log(calories: 4000.0, protein: 10.0)],
            settings: settings
        )
        XCTAssertEqual(result.status, .maintenance)
        XCTAssertEqual(result.recoveryTimeMultiplier, 1.0, accuracy: 0.001)
    }

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

        let result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs: [log(calories: 4000.0, protein: 200.0)],
            settings: Settings(),
            activePlan: plan
        )
        XCTAssertEqual(result.status, .maintenance)
        XCTAssertEqual(result.recoveryTimeMultiplier, 1.0, accuracy: 0.001)
        XCTAssertTrue(result.factors.contains { $0.contains("Mantenimiento") })
    }

    // MARK: — Overrides manuales: ventana activa y ancla (paridad Android)
    // Espejo de `AugeRepository.getActiveWellbeingWithManualOverrides` y
    // `AugeRecoveryEngine.manualBatteryAnchorMs` de Android (árbol actual).

    private func wellbeingLog(
        date: String,
        manualMuscular: Int? = nil,
        manualNeural: Int? = nil,
        manualSpinal: Int? = nil,
        manualMuscles: [String: Int] = [:],
        overridesV2: [String: ManualMuscleBatteryOverride]? = nil,
        anchorMs: Int64? = nil
    ) -> DailyWellbeingLog {
        DailyWellbeingLog(
            id: UUID().uuidString,
            date: date,
            manualMuscularBattery: manualMuscular,
            manualNeuralBattery: manualNeural,
            manualSpinalBattery: manualSpinal,
            manualMuscleBatteries: manualMuscles,
            manualMuscleOverridesV2: overridesV2,
            manualBatteryAnchorMs: anchorMs
        )
    }

    /// Oráculo: solo los overrides manuales ACTIVOS cuentan. Fila de hoy con
    /// cualquier fuente manual (incluido solo per-muscle) → activa; fila de
    /// ayer → solo con ancla dentro de las últimas 18 h (ancla ausente o
    /// vencida → no activa); sin fuentes manuales → nunca.
    func testManualOverrideActiveWindowMatchesAndroid18Hours() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let today = "2026-09-25"
        let yesterday = "2026-09-24"
        let anchorFresh = now - 17 * 3_600_000
        let anchorStale = now - 20 * 3_600_000
        let v2 = ["Pectorales": ManualMuscleBatteryOverride(battery: 90, anchorEpochMs: anchorStale, automaticBatteryAtAnchor: 100)]

        // Hoy: cualquier fuente manual → activa (per-muscle ya no se ignora).
        XCTAssertTrue(AugeRepository.isActiveManualOverride(
            wellbeingLog(date: today, overridesV2: v2), nowMs: now, today: today
        ))
        XCTAssertTrue(AugeRepository.isActiveManualOverride(
            wellbeingLog(date: today, manualNeural: 70), nowMs: now, today: today
        ))
        // Ayer: dentro de la ventana de 18 h → activa.
        XCTAssertTrue(AugeRepository.isActiveManualOverride(
            wellbeingLog(date: yesterday, manualMuscles: ["Isquios": 65], anchorMs: anchorFresh),
            nowMs: now, today: today
        ))
        // Ayer: ancla vencida o ausente → no activa (no se inventa vigencia).
        XCTAssertFalse(AugeRepository.isActiveManualOverride(
            wellbeingLog(date: yesterday, manualNeural: 70, anchorMs: anchorStale),
            nowMs: now, today: today
        ))
        XCTAssertFalse(AugeRepository.isActiveManualOverride(
            wellbeingLog(date: yesterday, manualNeural: 70),
            nowMs: now, today: today
        ))
        // Sin fuentes manuales → nunca activa, aunque sea la fila de hoy.
        XCTAssertFalse(AugeRepository.isActiveManualOverride(
            wellbeingLog(date: today), nowMs: now, today: today
        ))
    }

    /// Oráculo: sin ancla explícita el motor usa la medianoche del día del
    /// registro (como Android), NUNCA «ahora» — re-anclar en `nowMs()`
    /// congelaría el ajuste manual sin decaimiento.
    func testManualBatteryAnchorFallsBackToWellbeingDateNotNow() {
        let date = "2026-09-20"
        let rowWithoutAnchor = wellbeingLog(date: date, manualNeural: 55)

        let expected: Int64 = {
            let f = ISO8601DateFormatter()
            f.formatOptions = [.withFullDate]
            return Int64(f.date(from: date)!.timeIntervalSince1970 * 1000)
        }()
        XCTAssertEqual(AugeRecoveryEngine.manualBatteryAnchorMs(rowWithoutAnchor), expected)

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        XCTAssertLessThan(AugeRecoveryEngine.manualBatteryAnchorMs(rowWithoutAnchor), now)

        // Ancla explícita → se respeta tal cual.
        XCTAssertEqual(
            AugeRecoveryEngine.manualBatteryAnchorMs(wellbeingLog(date: date, manualNeural: 55, anchorMs: 1_234_567)),
            1_234_567
        )
        // Sin fila → 0 (ausencia, no un valor fabricado).
        XCTAssertEqual(AugeRecoveryEngine.manualBatteryAnchorMs(nil), 0)
    }
}
