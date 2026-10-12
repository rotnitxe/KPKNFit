# Informe del agente T · pruebas (auditoría RINGS/AUGE, 2026-10-12)

> Entregado por un subagente Haiku 5.5 (tipo Explore, solo lectura) y copiado **sin editar**. La verificación, las correcciones de severidad y las comprobaciones propias están en el anexo A del plan (`PLAN.md` de esta carpeta). Las referencias `archivo:línea` son de `master` @ `00431a27d`.

## Auditoría AUGE: inventario de pruebas (solo lectura)

**Convenciones.** **T** = `C:\Users\valen\Documents\KPKNFit\android-native\app\src\test\java\com\example\kpkn\`. **K** = `…\src\main\java\com\example\kpkn\`. Sin prefijo, los archivos están en `T\domain\auge\`. L = línea. Tipo: K contrato, C caracterización, Reg regresión, P propiedad, D tabla fija o dorado. Veredicto: M mantener, R re-anclar si cambian números, O obsoleta, F frágil, E engañosa.

**Alcance.** Hay 40 `.kt` en `T\domain\auge\`: 39 con `@Test` (215 pruebas) y `AugeRealSessionFixtures.kt` (fixture). El brief dice 39.

### A. Inventario

| Archivo | n | Tipo | Pines clave (L) | Producción (núcleo) | Reloj | Coste | Veredicto |
|---|---|---|---|---|---|---|---|
| AugeRecoveryEngineRingTest | 14 | C/K | L48-50 =100; L163-164 pesos 0,40/0,35/0,25; L170-171 ≥20 / ≥12; L325-326 (80,97) | getPerMuscleBatteries, calculateGlobalBatteries, calculateRecoveryDashboard, previewPostSessionBatteries | Sí (8) | M | F+R: L101-104 tautológica tras L99; L135-148 «usesNowMs» no comprueba reloj; L54-108 con `perMuscle` vacío no entra en la rama lineal (AugeRecoveryEngine.kt:1856-1878) |
| AugeRingsAudit2026Test (13 clases) | 13 | K/C/Reg | L68 τ ±5 % (ida-vuelta); L80 espinal >5; L89-91 curl espinal 0; L141-148 migración v2→v3; L176 ±1; L197 ±3; L233 ±2; L258-259 exacto; L276-279 ±25 % | AugeAdaptiveEngine, AugeFatigueEngine, AugeRecoveryEngine, PerformanceTauLearner, AugeAdaptiveCacheMigration | No | L | M salvo SystemTauRoundTripTest (R+E, ver B) y CardioSystemicDrainTest (F: L121-123 solo desigualdades) |
| ExerciseReadinessEngineTest | 17 | K/C | L85,105,116,130,140,149 factores 1,0/0,95/0,90 (±0,001); L177 100 %; L221 80 %; L296 cadena exacta | computeDiscomfortPenaltyFactor, buildPatternCoaching | No | L | M; F: 21 aserciones de texto (`contains`) |
| InitialRecoveryEvidencePolicyTest | 13 | K | L61 =76 (sensación muscular 5, sin sesiones); L121 =85; L138 confianza ≤82; L180 «Sin calibrar»; L285 =35 | InitialRecoveryEvidenceFactory.fromInputs (v1), InitialRecoveryEvidencePolicy.resolve | No | L | R (76, 85, 82); O parcial al retirar v1 |
| PerformanceTauObservationTest | 10 | C/K | L50-51 70→60 exacto; L140 =62; L170-171 70→60; L52 47-49 h; L194-196 τ<48 y totalObservations=1 | PerformanceTauLearner.observations/applyToCache | No | L | R (valores exactos); E: nombre «betterThanPredicted» (L175) cuando τ no lee la predicción (AugeAdaptiveEngine.kt:27-48) |
| ExerciseReadinessEngineArticularTest | 6 | K | L107 =40; L148-150 80/40/40 (mín.); L183-185 sin mapeo→100, estructura 85; L226 ≤50; L269 1/3,5 ±0,001 | calculatePerExerciseReadiness, articularBatteriesFor | No | L | M; F: reflexión sobre `ExerciseDatabaseKt.exerciseDatabaseByIdCache` (L41-53); el @After vacía solo ese mapa (ExerciseDatabase.kt:36) |
| AugeRingDrainRealismTest | 6 | C (ruta legacy) | L98 78-86; L122-123 88-93; L125 <97; L147 Δ 5-15; L148 ≥85; L174 ≤5; L208 86-92; L228-230 55-85, ≥22, <90 | calculateMuscleBattery sin `muscularImpactV2`: recálculo legacy y capacidad fallback (AugeRecoveryEngine.kt:380-456) | Sí (6) | M | R (cabecera L15-17: «DEBEN FALLAR tras Fase 1»); E: comentarios «floor 260 (antes 500)» (L93) y «floor (500)» (L160), frente a 2600 para ENTHUSIAST (AugeFatigueEngine.kt:32,43) |
| AugeAdaptiveEngineTest | 11 | K | L19, L34, L46, L99, L111, L125 (α a mano); L67, L87 τ∈[12,144]; L141-142 orden; L157, L173 | updateSystemLearningDeltas y updateMuscleDeltas (muertas), updatePersonalizedRecoveryHours, reflexión a deriveImpliedRecoveryTime (L176-181) | No | L | O: 6 pruebas (L10-47, L90-126); F: 2 por reflexión (L146-174); M: 3 de τ |
| AugeRecoveryEngineManualOverrideTest | 3 | K/Reg | L91-93 igualdad: entreno previo al ancla no cuenta; L117-123 dashboard = batería; estructura 97 (L119-120); L146-147 legado | calculateSystemicFatigue, calculateSpinalBattery, calculateMuscleBattery, calculateRecoveryDashboard | Sí (L41, L44, L100) | L | M; F (reloj); oráculo débil: L119-120 no discrimina pesos |
| SessionDrainBoundsTest | 5 | K (cotas) | L50-52 ≥10; L90 Deltoides >0; L169-171 ≥suelos; L185-187 topes 32/32/35 | calculateCompletedSessionDrain(Breakdown), calculateSetBatteryDrain, physiologicalFloor | Sí (L130) | M | E: `predictedAndCompleted_…` no compara predicción; L123 `||` tautológico; F: L130 reloj |
| AugeRingCharacterizationTest | 2 | C | L63-74 pines ±2 (energía/columna/pecho a 0-72 h; ligera 90/92); L93-95 energía 87/86/85; L56-57 y L91-92 monotonía | calculateSystemicFatigue, calculateSpinalBattery, calculateMuscleBattery con V2 escrito a mano (L106-109, L142-145) | No | M | R (todos los pines); F: L91-92 con ≥/≤ no estrictos sobre valores casi planos |
| AugeUnitsConsistencyTest | 6 | K/Reg | L31, L35 ACWR null con legado; L55 igual ±1e-4; L67 null con fecha inválida; L112-113 capacidad >baja y ≥1200 | muscularAcwrFor (AugeRecoveryEngine.kt:352), AugeMuscleCapacityEngine.calculateUserWorkCapacity | No | L | M; F: L34-35 null por umbral de 4 sesiones (no por ignorar legado); L112 cambia stress e immediateDrainPct a la vez; E: L114 `completionMs > 0` tautológico |
| AugeTtcRecoveryHoursTest | 2 | O/C | L55-58 igualdad exacta: 8,0, k=2/60, `toInt()` | AugeTtcEngine.calculateArticularBatteries (hoursToRecovery) | Sí (2) | L | R; F: L128-130 aserción condicional (puede no ejecutarse) |
| AugePerMuscleInitialEvidenceTest | 6 | K | L61 Cuádriceps =88; L75-76 100 con log real del mismo día; L96 ±1 sin doble resta; L123-124 =77 (v1); L141 100−sensationPenalty(5) | getPerMuscleBatteries, calculateGlobalBatteries, fromInputsV2, sensationPenalty (InitialRecoveryEvidence.kt:205,219) | No | L | M; R (88, 77); el 88 se repite en InitialRecoveryEvidenceV2Test L150 |
| InitialRecoveryEvidenceV2Test | 8 | K/D | L56-58 =82/88/94 (ley v1, τ 72 h, a mano); L81 97,5 ±1 (100·0,6^0,05); L96 estructura null; L110-116 =100; L150-151 88/100; L159-160 `estimatorVersion` 2 y «initial-recovery-v3» | InitialRecoveryEvidencePolicy.resolve, v2PillarBatteries; InitialRecoveryEvidence.kt:287,298 | No | L | M (mejor oráculo del grupo); R (88, contractRevision) |
| AugeLanguageConsistencyTest | 6 | K (cruzado) | L29-42 secundario = 0,5 en 4 puntos; L102 =78; L115 ACWR >1,5; L117 =1,12 ±1e-4; L125-126 negativos | resolveMuscleVolumeContribution, FATIGUE_ROLE_MULTIPLIERS, VolumeCalculator, muscularAcwrFor, AugeClassifiers | No | L | M (0,5 es contrato); R (78, 1,12, ACWR); F: L125-126 pasan si la lista está vacía (hoy hay 1 sugerencia, AugeTtcEngine.kt:464-481) |
| NutritionRecoveryEngineTest | 7 | K | L66-67 MAINTENANCE 1,0; L113-114 DEFICIT; L126-127; L149-151 fechas rotas sin excepción; L173 | computeNutritionRecoveryMultiplier (hoursWindow=48, NutritionRecoveryEngine.kt:30) | Sí (12) | L | M; E: L161-174 «4000 kcal» da MAINTENANCE solo porque la ventana de 48 h promedia 2000/día |
| AdaptiveCalibrationAppliesTest | 6 | K | L31 y L74 τ=36 exacto (0,5 h y 0,25 h no aprenden); L53-54 ∈[12,144] y ≠36; L102-109 signos; L129-131 clamps 0,5-1,6; L159 sueño no altera τ | updateSystemRecoveryHours; updateDrainMultipliers (muerta); calculateGlobalBatteries | Sí (2) | L | O (L113-132); F: L54 solo comprueba «≠»; M resto |
| AugeMuscleNormalizationTest | 6 | K | L60-61 medias 1,0 y 1,1; L67 1,35 legado; L37-49 gana el pilar (int); L52-62 promedia (multiplicador) | getAugeMusclePillarId, remapMuscleIntMapToPillars, remapMuscleMultiplierMapToPillars, lookupMuscleDrainMultiplier | No | L | M; sin prueba que cruce las dos políticas |
| AugeMuscleAttributionTest | 3 | K/P | L34 reordenar no cambia (±0,001); L43 harder ≥ base | MuscularSessionImpactEngine.evaluate, AugeRealSessionFixtures | No | M | M; E: nombre «_once» (L9) sin aserción de conteo único |
| AugeUnilateralImpactTest | 1 | K | L59-60 tolerancia 8 % + 1,0 | MuscularSessionImpactEngine.evaluate | No | L | M |
| AugeMuscleProfileTest | 1 | K | L10-13 | getAugeMusclePillarId / DisplayId | No | L | M (redundante con AugeMuscleNormalizationTest: fusionar) |
| AugeSupersetDensityTest | 1 | P | L31-34 >1, creciente, ≤1,45, no-superserie =1,0 | getDensityMultiplierForExercise | No | L | M |
| AugeFatigueEngineEffectiveSetTest | 4 | K | booleanos: calentamiento, RPE por defecto 7, plank, fallo | isSetEffective | No | L | M (duplica AugeUnitsConsistencyTest L118-121) |
| CardioRingDrainBoundsTest | 2 | P | L21-23 RPE 3<6<9 en 3 canales; L35 SIT>HIIT>continuo; L42 0-100; L44 carrera>bici espinal | CardioRingDrainEngine.drain, CardioHiitProgramBuilder.buildDetails | No | L | M |
| ExerciseReadinessBlendPinTest | 1 | K | L69 =74 sin derivación; L70 =60; L71-72 signos | calculatePerExerciseReadiness | No | L | R (74); F: reflexión duplicada (L75-87) |
| AugeEditorCompletedParityTest | 1 | P | L46 ±2 drenaje global; L47 mismos músculos; L49-51 ±2,0 por músculo | MuscularSessionImpactEngine.fromPlannedSession / fromCompletedExercises | No | M | M; PARCIAL: solo motor, sin editor ni UI |
| AugePostSessionPreviewImmediateTest | 2 | K | L23-26 ISO preservado; L32-34 orden y DST | AugeUtils.parseIsoMs (AugeUtils.kt:96) | No | L | E: L13 «hoursAreZeroAtFinish» no comprueba horas; L34 usa 03:00 del 6-sep-2026, fuera del hueco de Santiago (00:00-01:00, verificado) |
| PerformanceImpliedBatteryTest | 2 | D | L10-15 tabla ERM→batería; L20-24 tabla RPE→energía | PerformanceImpliedBattery | No | L | M; R solo si cambian las curvas |
| SleepLogExtendedToSleepLogTest | 1 | Reg | L21-26 endTime ISO-8601 | SleepLogExtended.toSleepLog | No | L | M; F: L24-25 OR con regex (sin zona) |
| WorkoutLogAugeImpactSerializationTest | 1 | Reg | L20 legado sin impacto; L29-31 round-trip | WorkoutLog JSON, MuscularSessionImpactEngine | No | L | F: L30 compara solo `setInputHash`, no `perMuscle` |
| OvernightRecoverySensationTest | 2 | D/K | L48 50-62 (40 % + noche); L55 sigmoide 10 h ∈[4,5; 5,5] | calculateMuscleBattery(sleepLogs), getSigmoidalHours (AugeUtils.kt:136) | No | L | M; R si cambia la curva |
| OvertrainingDetectorTest | 2 | K | L46, L66 vacío; L138-139 no vacío y primero = Cuádriceps | OvertrainingDetector.detectOvertrainedMuscles | No | L | F: solo caso positivo; sin negativo bajo umbral |
| AxialLoadMonitorTest | 2 | K | L33 historial corto insuficiente; L54 ≥3 de 4 días o ≥4 de 6; L64 WATCH\|ADJUST; L74 ADJUST | AxialLoadMonitor.evaluate, LoadAdvisoryEngine.levelFor | No | L | E: «threeAxialDaysInFour_reachAdjust» acepta WATCH (L64) y L74 usa entradas sintéticas; F |
| RingsIntroSimulatorTest | 14 | D/K | L27 (84,89,77) a 0 h exacto; L52 (99,96,99) a 36 h exacto; L58 ≥90 a 72 h; L39-48 monótona; L62-65 recorte [0,72]; L99-100 redondeo | RingsIntroSimulator (cabecera L7-10: pasa por fromInputsV2) | No | L | R (la cabecera dice «si el motor cambia, cambian aquí»); M resto |
| WellbeingSaveMergeTest | 10 | Reg/K | L43; L53-54 V2 se conserva; L65 copia V1 no tira V2; L75-76; L99-102 ancla y globales; L113 no re-ancla; L124-125; L135-136; L146 unión de `capturedFields` | mergeWellbeingForSave (WellbeingSaveMerge.kt:20) | No | L | M; cubre guardado, no `applyManualBatteries` |
| DiscomfortSuggestionEngineTest | 9 | K (catálogo) | sin cifras; ids shoulder_anterior, knee_patellar, lumbar, achilles | DiscomfortSuggestionEngine (usa pilares AUGE) | No | L | M |
| AugeRecoveryEnginePreWorkoutDiscomfortTest | 2 | K | L84-87 penalizado < base (precomputedCapacity 150, L66/L80); L118 hombro < base | calculateMuscleBattery, calculateArticularBatteries(wellbeing) | Sí (5) | L | M; F: sin `nowOverride` con historial a 8 h del reloj real |
| AugeRingsGuideAndMonitorTest (7) | 7 | K | L17-26 bandas 85/70/50/35/34 y etiquetas; L24 ADJUSTMENT_THRESHOLD = NORMAL_MIN; L33 0 a 80; L35 >0 a 50; L46-48 p25/p75 con ≥4 instantáneas; L61-75 histéresis; L94-98 descarte; L128 texto exacto | RecoveryBands (RecoveryBands.kt:136), readinessLabel, personalBaseline, LoadAdvisoryEngine | No | L | M; F: L35 no fija cifra (no detecta la subestimación 20-27 %, hallazgo 11) |
| AugeRealSessionFixtures.kt | 0 | fixture | L39 check de 30 series; L66-68 efc 3,0 / cnc 3,0 / ssc 0,7 iguales para todos; L54-63 sets/peso/reps/RPE no usados | — | No | — | F/E: calibración uniforme; lo usan 4 archivos |
| `screens\auge\AugeSnapshotCoverageTest` (fuera de domain) | 4 | K/Reg (Robolectric + Room) | L89-97 sin calibrar → NO_DATA; L130 =75; L163 incertidumbre 30 (conf. 70); L200-217 caducidad: 1 ms antes vigente, exacto ya no | computeSnapshotCoverage (AugeViewModel.kt:1175), SetupCommitCoordinator, KpknDatabase.createInMemory | Sí (L48, fecha clave) | H | M; R (caducidad v1); F: el encabezado (L37-43) dice que prueba el productor del ViewModel y solo prueba la función (AugeViewModel.kt:285 no se ejercita) |

### B. Oráculo

**Autoconfirmación** (el valor esperado reimplementa la producción):
- **AugeRingsAudit2026Test / SystemTauRoundTripTest (L46-69).** El modelo directo usa `AugeUtils.TAU_K_95` (AugeUtils.kt:30), la misma convención que el aprendizaje. `predictedBattery` se fija igual al real (L54-60) y `deriveImpliedRecoveryTime` (AugeAdaptiveEngine.kt:27-48) no lo lee: la prueba no ejercita el error de predicción. Es ida-vuelta solo en CNS, donde el tiempo es lineal. Cálculo con python: con `toInt()` la τ implícita es 37,90 (+5,3 %) y tras el blend 36,32 (+0,9 %); sin truncar, 36,00. La tolerancia del 5 % (L68) absorbe el sesgo. La ida-vuelta **oculta** el sesgo y no prueba la inversión sigmoidal (`getSigmoidalHours`, AugeUtils.kt:136) ni la espinal (`getSpinalRecoveryHours`, AugeUtils.kt:149), que sí aparecen en `deriveImpliedRecoveryTime` (AugeAdaptiveEngine.kt:37-41).
- **AugeTtcRecoveryHoursTest L55-58.** Replica la fórmula con constantes (8,0; 2/60; `toInt()`). Fija el truncamiento y no detecta la subestimación 20-27 %.
- **AugeRecoveryEngineManualOverrideTest L119-120.** `min(97, int(97·0,6+100·0,4))` = 97, así que cualquier mezcla ≥97 da lo mismo: no discrimina los pesos.
- **AugePerMuscleInitialEvidenceTest L141 e InitialRecoveryEvidenceV2Test L130-131.** Esperado = 100 − `sensationPenalty` de producción: comprueba consistencia, no magnitud.
- **WorkoutFinishCalibrationTest.** L28 compara `seed` con `seed` (tautología); L33-37 y L40-48 no llaman a producción. Oráculo vacío.
- **AugeEditorCompletedParityTest.** El plan se construye desde las series completadas: la equivalencia es casi por construcción (±2).

**Referencia independiente** (cálculo a mano o tabla fija): InitialRecoveryEvidenceV2Test L56-58 y L81; ExerciseReadinessEngineArticularTest L269; AugeMuscleNormalizationTest L60; PerformanceImpliedBatteryTest; AugeRingsAudit2026Test L276-279 (±25 % literal); AugeAdaptiveEngineTest (α en comentarios); OvernightRecoverySensationTest L55; SpinalWarpContinuityTest (propiedad, sin fórmula).

**Caracterización** (salidas medidas, no derivadas): AugeRingDrainRealismTest, AugeRingCharacterizationTest (entradas V2 a mano, salidas medidas), RingsIntroSimulatorTest, AugeLanguageConsistencyTest L102 y L117, AugeRecoveryEngineRingTest L170-171.

**Contratos que quedarían sin protección al unificar la receta** (si el motor único cambia resultados y se re-ancla fuera de estas pruebas, nada los contrasta):
1. Aprendizaje de τ en las ramas sigmoidal y espinal, incluidos los clamps 6–200 (AugeAdaptiveEngine.kt:47) y el caso `actualDepletion ≥ sessionStress` (fracción forzada a 0,99).
2. Drenaje legacy (sin `muscularImpactV2`) y capacidad fallback (AugeRecoveryEngine.kt:380-456) frente a la pública (AugeMuscleCapacityEngine.kt:14). Ninguna prueba compara ambas.
3. Mezcla estructural 0,6/0,4 y tope con la columna.
4. Normalización crónica del ACWR (`/4`): solo cifras medidas.
5. Diferencia preview↔Home por músculo con base <100 (AugeRecoveryEngine.kt:1856-1878).
6. Magnitud de la penalización por sensación (solo consistencia).
7. Horas de recuperación (`hoursUntilNormal`, RecoveryBands.kt:136; `hoursToRecovery`): solo signo o fórmula replicada.

### C. Matriz de huecos

| # | Ítem | Estado | Evidencia y hueco |
|---|---|---|---|
| 1 | τ invierte el modelo (sesgo) | NO CUBIERTO | Ida-vuelta CNS lineal (AugeRingsAudit2026Test L46-69) que oculta el sesgo. El resto de pruebas de τ solo comprueba signo o cambio (AugeAdaptiveEngineTest L129-142; AdaptiveCalibrationAppliesTest L54). Sin inversión sigmoide ni espinal. |
| 2 | `recompute` bloquea (mutex) | NO CUBIERTO | Lock en AugeViewModel.kt:396, dentro de `recompute` (L188). Llamadores con lock previo: L455 (lock L412), L697 (L611), L1066 (L1060), L1138 (L1135), L1151 (L1148). ViewModel sin prueba. |
| 3 | `targetPercentageRM` sin /100; NaN | NO CUBIERTO | ExerciseReadinessEngine.kt:146-147 multiplica sin /100; :76, :94 y :262 dividen o promedian sin guarda. Cero pruebas con `targetPercentageRM` o listas vacías en `T\domain\auge`. |
| 4 | Baterías con `toInt()` (meseta 99 %) | NO CUBIERTO | Ningún test fija redondeo ni meseta. Home trunca con `toInt()` (screens\home\HomeSessionSection.kt:435). Solo hay redondeo de horas (RingsIntroSimulatorTest L99-100). |
| 5 | ACWR con denominador /4 | PARCIAL | `chronicWeekly = month/4` (AugeRecoveryEngine.kt:356); exige ≥4 sesiones V2 y ≥14 días (L364-366). Pines que cambiarán: 1,12 (AugeLanguageConsistencyTest L117) y >1,5 (L115). Sin prueba con 14-27 días de historial. |
| 6 | Ancla manual caduca a 18 h (escalón) | NO CUBIERTO | Anclas de 5 min y 60 s (ManualSpinalOverrideSticksTest L191-197; AugeRecoveryEngineManualOverrideTest L41). Ninguna prueba de caducidad de ancla. |
| 7 | Preview ≠ Home (resta lineal) | NO CUBIERTO con historial | Rama lineal AugeRecoveryEngine.kt:1856-1878 (L1859). Paridad solo con base=100 (MuscleColdStartParityTest L201-234). AugeRecoveryEngineRingTest L54-108 no entra en la rama (`perMuscle` vacío). |
| 8 | Editor ≠ finish ≠ Home | NO CUBIERTO | AugeEditorCompletedParityTest L13-53 cubre solo el motor. Sin UI ni ViewModel. |
| 9 | `applyManualBatteries` pierde `source` / `capturedFields` | NO CUBIERTO | Cero pruebas en T (grep). AugeViewModel.kt:596. WellbeingSaveMergeTest L140-147 cubre el merge de guardado, no este camino. |
| 10 | Evidencia inicial aditiva | PARCIAL | Sin doble resta con precomputed (AugePerMuscleInitialEvidenceTest L80-97). Una fuente por canal: el check-in 75 gana a la evidencia 96 (SetupRingsMergeTest L329-336). La suma sensación+sesiones se fija tal cual (InitialRecoveryEvidencePolicyTest L61 =76): re-anclar. |
| 11 | `hoursToRecovery` / `hoursUntilNormal` subestiman | NO CUBIERTO | AugeRingsGuideAndMonitorTest L33-35 solo comprueba el signo; AugeTtcRecoveryHoursTest L57-58 replica la fórmula. |
| 12 | Logs con fecha futura en Energía/Columna | NO CUBIERTO | Ningún test con log posterior a `nowMs` (lectura completa de los 39 archivos). |
| 13 | Código muerto | PARCIAL (verificado) | Sin llamadores en `app\src`: updateSystemLearningDeltas (AugeAdaptiveEngine.kt:91), updateMuscleDeltas (:221), updateDrainMultipliers (:159), learnRecoveryRate (AugeClassifiers.kt:117), calculatePersonalRecoveryStats (AugeRecoveryEngine.kt:1704), calculateSessionDrainRankings (:1600), calculateExerciseDrainRankings (:1629), calculateSleepRecommendations (:1531), mapWorkoutToPostSessionFeedback (screens\workout\WorkoutFeedbackModels.kt:309). Tienen pruebas solo las tres de AugeAdaptiveEngine. AugeSheetParts.kt no revisado. |
| 14 | Determinismo con `nowMs` fijo | PARCIAL | `computeSnapshotCoverage` es determinista (AugeSnapshotCoverageTest L174-218). El recompute no es testeable. Ocho archivos usan reloj real (A) y `calculateUserWorkCapacity` llama a `nowMs()` sin recibir reloj (AugeRecoveryEngine.kt:387). |
| 15 | Idempotencia del aprendizaje por logId | NO CUBIERTO | Guarda en el ViewModel (AugeViewModel.kt:854). `applyToCache` solo protege observaciones vacías (PerformanceTauLearner.kt:94-96). Prueba de una sola aplicación (PerformanceTauObservationTest L175-196). |
| 16 | Aprendizaje con sesiones apiladas | NO CUBIERTO | Solo llamadas únicas con `totalObservations` fijo. |
| 17 | Ley del ancla entre canales | PARCIAL | WellbeingSaveMergeTest L93-126; ManualSpinalOverrideSticksTest L180-198. Sin prueba del efecto tras el decaimiento. |
| 18 | Guardado de caché bajo concurrencia | NO CUBIERTO | AugeRepository.kt:130-153 sin pruebas; mutex del ViewModel sin prueba (ítem 2). |
| 19 | Round-trip de `AugeAdaptiveCache` (schemaVersion) | NO CUBIERTO | La caché aparece solo en memoria en 4 archivos; ninguna serialización. |
| 20 | Migración v2→v3 | PARCIAL | AdaptiveCacheSchema3MigrationTest L127-150: solo v2, en memoria. Faltan v1 y la identidad v3 (AugeAdaptiveCacheMigration.kt:14). |
| 21 | Redondeo de pantalla | NO CUBIERTO | HomeSessionSection.kt:435 sin prueba. |
| 22 | Monotonía del drenaje (series/RPE) | PARCIAL | RPE sí: CardioRingDrainBoundsTest L21-23, AugeRingCharacterizationTest L56-57, AugeMuscleAttributionTest L43. Series: ninguna prueba de más series → más drenaje. |
| 23 | No-NaN con entradas raras | PARCIAL | Sí: fechas rotas (NutritionRecoveryEngineTest L131-159) y suelos (SessionDrainBoundsTest L169-171). No: ExerciseReadinessEngine con lista vacía. |
| 24 | Equivalencia planificado↔completado | PARCIAL | Solo motor, con fixture y ±2 (AugeEditorCompletedParityTest L46-51). |
| 25 | Paridad preview↔Home con historial largo | NO CUBIERTO | Todas las pruebas de paridad usan una sesión o base=100 (SpineRingSingleDefinitionTest L152-177; ReadinessSheetHomeParityTest L237-261; MuscleColdStartParityTest). |

**Cubierto y sólido:** monotonía temporal de recuperación (InitialRecoveryEvidenceV2Test L62-71; RingsIntroSimulatorTest L39-48); secundario = 0,5 en todo el código (AugeLanguageConsistencyTest L26-43); fechas rotas (NutritionRecoveryEngineTest L131-159); suelos y topes (SessionDrainBoundsTest L127-188); evidencia sin doble conteo (AugePerMuscleInitialEvidenceTest L80-97).

### D. Infraestructura

**Stack (build.gradle.kts L241-246).** JUnit 4, Robolectric 4.12.2, kotlinx-coroutines-test 1.8.1, androidx.test core 1.6.1, Room testing y Compose UI test. **No hay** mockk ni turbine. No hay bloque `test {}`, así que Gradle usa su fork por defecto y el estado estático se filtra entre clases. Ninguna prueba de `domain\auge` importa `android.*` (verificado): son JVM puras.

**AugeViewModel.** `class AugeViewModel(application: Application)` (AugeViewModel.kt:56) con singletons `AugeRepository`, `ProgramRepository` y `NutritionRepository` (L62-64), `viewModelScope` (L132-173), `withContext(Dispatchers.Default)` (L226, L526, L874) y mutex privado (L70). **No es instanciable en JVM pura.** Sí en Robolectric: `SessionEditorViewModelRulesTest` (`@RunWith(RobolectricTestRunner)` en L46, `ApplicationProvider`, `Dispatchers.setMain` en L59). Hay reseteo por reflexión de `AugeRepository.INSTANCE` (WorkoutFinishCancellationTest L66-67; WorkoutFinishControllerPostCommitTest L57-58). `Dispatchers.setMain` aparece en unas 40 pruebas. Sin inyección de dispatchers, la concurrencia solo se puede probar con `withTimeout` de tiempo real.

**Historiales y ajustes.** Cada archivo define sus constructores privados (unas 24 funciones en 15 archivos, conteo aproximado). Hay duplicados idénticos: `logWithStress` (AugeUnitsConsistencyTest L142-167 y AugeLanguageConsistencyTest L129-155) y `heavyWorkoutLog` (AugeRecoveryEngineManualOverrideTest L150 y AugeRecoveryEnginePreWorkoutDiscomfortTest L23). El mapa `bench` con efc 5,0 aparece en tres archivos. El único fijo compartido es `AugeRealSessionFixtures` (9 ejercicios, 30 series), donde `info()` descarta sets, peso, reps y RPE y fija efc 3,0, cnc 3,0 y ssc 0,7 para todos. No hay fixture de ajustes manuales ni de anclas. Los fakes de onboarding (`FakeSetupWizardPersistence`, `FakeSetupWizardEnvironment`, SetupWizardTestFakes.kt) no sirven para AUGE.

**Reloj.** `AugeClock.nowMs()` (AugeClock.kt:9) es un objeto global sin override. Los tests pasan `nowOverride`, pero `calculateUserWorkCapacity` (AugeRecoveryEngine.kt:380-387) no recibe reloj y llama a `nowMs()`. Riesgo: las pruebas con fechas fijas de 2026-09 que caen en la ruta legacy (ReadinessSheetHomeParityTest, SpinalWarpContinuityTest) dependen de la fecha de ejecución. No he comprobado si la batería espinal usa esa ruta.

**Esqueletos propuestos**

**(a) Arnés de usuarios sintéticos con motores reales.** Archivos: `T\domain\auge\AugeSyntheticUserHarness.kt` y `T\domain\auge\AugeSyntheticUserInvariantsTest.kt`.
- `data class SyntheticUser(id, athlete: AthleteType, sessionsPerWeek, intensity: Double, weeks, seed: Long)`.
- `SyntheticHistory.build(user, startMs)`: reparte `AugeRealSessionFixtures.completedExercises` con escala determinista (`kotlin.random.Random(seed)`) y rellena `muscularImpactV2` con `MuscularSessionImpactEngine.evaluate` (producción real).
- Muestreo cada 6 h: `getPerMuscleBatteries(…, nowOverrideMs = t)` → `calculateGlobalBatteries(…, precomputedMuscles)` → `calculateRecoveryDashboard` → `computeSnapshotCoverage` (AugeViewModel.kt:1175, `internal`, visible al módulo).
- Invariantes: rango 0-100; ≥ `physiologicalFloor`; sin NaN; recuperación monótona entre sesiones; ACWR null con <14 días; paridad preview↔Home por músculo (`previewPostSessionBatteries`) con un presupuesto de divergencia medido y explícito.
- Determinismo: misma semilla, mismo SHA-256 de la salida. Detecta relojes ocultos.
- Coste: 10 usuarios × 90 días × 4 muestras ≈ 3 600 cadenas completas. Pesado: filtro propio.

**(b) Máster dorado.** Corpus en `src\test\resources\auge\golden\corpus.json` (carpeta hoy inexistente; `src\test\resources` sí existe), con 30 sesiones (plantillas de `AugeRealSessionFixtures` y variaciones con semilla fija) y `expected.json`. Harness `T\domain\auge\AugeGoldenMasterTest.kt`: produce una tabla canónica (claves ordenadas, enteros con `toInt()` como la UI) con drenajes por sesión (`calculateCompletedSessionDrainBreakdown`), baterías por músculo a 0/6/12/24/48/72 h, globales, dashboard y preview. Compara y muestra el diff por fila (músculo, t, antes, después). Regeneración con `-Dauge.golden.update=true`, escribiendo en `build\auge-golden\` y sin tocar `src`. Una sola tabla cubre las 8 copias de la receta: un cambio intencional produce un diff revisable en lugar de re-anclar 40 pruebas.

**(c) Concurrencia del mutex.** `T\screens\auge\AugeWriteMutexTest.kt` (Robolectric, `@Config(sdk=[34])`, como AugeSnapshotCoverageTest). `Dispatchers.setMain(StandardTestDispatcher())`, reset de `AugeRepository.INSTANCE`, historial y ajustes sembrados en `ProgramRepository` y `NutritionRepository` (requiere un seam: hoy son singletons sin constructor) y `AugeViewModel(app)`. Un caso por llamador con lock previo: `saveWellbeing` (L410 → L455), `applyManualBatteries` (L596 → L697), `dismissAdvisory` (L1058 → L1066), `clearManualBatteryOverrides` (L1133 → L1138) y `resetAdaptiveCache` (L1146 → L1151). Cada caso: `runTest { withTimeout(5_000) { vm.X(…) } }` y afirmar que termina. Debe fallar hoy con `TimeoutCancellationException`. El bloqueo es estructural (`Mutex` no es reentrante), no depende del planificador: basta con `advanceUntilIdle()`.

### E. Filtros para compuertas rápidas

Todos los patrones llevan el prefijo `com.example.kpkn.domain.auge.` salvo E6. Tarea Gradle probable: `:app:testBaseDebugUnitTest` (no verificada; comprobar con `:app:tasks`). Cada `--tests` va en su propio argumento.

| Lote | Filtro (`--tests`) | Clases | Coste relativo |
|---|---|---|---|
| E1 Motor de drenaje | `AugeMuscle*`, `*DrainBounds*`, `AugeUnilateralImpactTest`, `AugeSupersetDensityTest`, `AugeFatigueEngineEffectiveSetTest`, `PerformanceImpliedBatteryTest`, `AugeEditorCompletedParityTest`, `AugePostSessionPreviewImmediateTest`, `WorkoutLogAugeImpactSerializationTest`, `SleepLogExtendedToSleepLogTest` | 14 clases puras | Ligera |
| E2 Recuperación y anillos | `AugeRecovery*`, `AugeRingCharacterizationTest`, `AugeRingDrainRealismTest`, `AugeRingsAudit2026Test` (13 clases, incluye τ espinal y migración), `OvernightRecoverySensationTest`, `AugeTtcRecoveryHoursTest`, `NutritionRecoveryEngineTest` | 19 clases | Media |
| E3 Aprendizaje | `AugeAdaptiveEngineTest`, `AdaptiveCalibrationAppliesTest`, `PerformanceTauObservationTest` (AugeRingsAudit2026Test ya está en E2) | 3 clases | Ligera |
| E4 Evidencia inicial y arranque | `InitialRecovery*`, `AugePerMuscleInitialEvidenceTest`, `RingsIntroSimulatorTest`, `WellbeingSaveMergeTest` | 5 clases | Ligera-media |
| E5 Readiness, avisos y carga | `ExerciseReadiness*`, `AxialLoadMonitorTest`, `OvertrainingDetectorTest`, `DiscomfortSuggestionEngineTest`, `AugeRingsGuideAndMonitorTest`, `AugeUnitsConsistencyTest`, `AugeLanguageConsistencyTest` | 9 clases | Ligera |
| E6 Cobertura, onboarding y UI (fuera de domain) | `com.example.kpkn.screens.auge.AugeSnapshotCoverageTest`; `com.example.kpkn.domain.onboarding.*Rings*`; `com.example.kpkn.screens.onboarding.rings.*`; `com.example.kpkn.screens.onboarding.SetupRingsAnswersV2Test`; `com.example.kpkn.render.Rings*`; `com.example.kpkn.data.onboarding.SetupRingsPreviewPayloadTest`; `com.example.kpkn.screens.workout.WorkoutFinish*`; `com.example.kpkn.screens.workout.PlannedIntensityPolicyTest` | Robolectric/Room; WorkoutFinish* usa AugeRepository por reflexión | Pesada |
| E7 Gates | Rápida: E1+E3+E5 (JVM pura). Completa AUGE: `com.example.kpkn.domain.auge.*` | ~40 clases, sin Android | Ligera / media |

Dependencias fuera de `domain\auge` que tocan motores AUGE (8 archivos con llamadas directas): AugeSnapshotCoverageTest, SetupRingsMergeTest, SetupRingsHomeCoverageTest, RingsResultV2Test, RingsIntroStepTest, RingsIntroRenderSheetTest, RingsChannelCoverageTest (domain.onboarding) y PlannedIntensityPolicyTest (`AugeFatigueEngine.getEffectiveRPE`). RingsIntroStepTest, RingsIntroRenderSheetTest y RingsResultV2Test dependen de `RingsIntroSimulator` o de la ley de evidencia, así que habrá que re-anclarlos junto con RingsIntroSimulatorTest.

### F. Cobertura real

- **Leído completo:** los 39 archivos con pruebas de `T\domain\auge\`, `AugeRealSessionFixtures.kt`, `T\screens\auge\AugeSnapshotCoverageTest.kt`, `T\screens\workout\WorkoutFinishCalibrationTest.kt`, `T\domain\onboarding\RingsChannelCoverageTest.kt` (L17-111) y `build.gradle.kts`.
- **Parcial o solo grep:** SetupRingsMergeTest (L308-338 y aserciones numéricas), SetupRingsMapperTest, SetupRingsResponseMappingTest, SetupRingsHomeCoverageTest, SetupRingsCheckInPersistenceTest, `T\screens\onboarding\rings\*`, WorkoutFinishControllerPostCommitTest, WorkoutFinishCancellationTest, SetupWizardTestFakes.kt (solo declaraciones).
- **Producción:** tramos de AugeAdaptiveEngine.kt (1-160), AugeRecoveryEngine.kt (334-460 y 1780-1905), NutritionRecoveryEngine.kt (60-135), AugeTtcEngine.kt (460-505), ExerciseDatabase.kt (30-120) y PerformanceTauLearner.kt (86-98). AugeViewModel.kt solo por grep de firmas, bloqueos y llamadas. ExerciseReadinessEngine.kt, InitialRecoveryEvidence.kt, AugeRepository.kt y WorkoutFinishHost.kt solo por grep.
- **No hecho:** no ejecuté Gradle ni ningún test. Los cálculos de τ se hicieron con `python -c`. La comprobación DST usó la zona de Windows (`TimeZoneInfo.IsInvalidTime`), porque Python no tiene tzdata. AugeSheetParts.kt no revisado.
