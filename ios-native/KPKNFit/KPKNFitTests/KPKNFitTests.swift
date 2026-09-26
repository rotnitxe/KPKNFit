import XCTest
@testable import KPKNFit

final class KPKNFitTests: XCTestCase {
    func testApprovedExerciseCatalogV2LoadsWithUniqueIdentity() throws {
        let repository = try ExerciseCatalogV2Repository(bundle: Bundle(for: Self.self))
        XCTAssertEqual(repository.catalog.schemaVersion, 2)
        XCTAssertEqual(repository.catalog.catalogRevision, "v2-approved-2026-08-10-c")
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
