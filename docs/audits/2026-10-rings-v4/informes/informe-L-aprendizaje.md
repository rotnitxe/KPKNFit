# Informe del agente L · aprendizaje (auditoría RINGS/AUGE, 2026-10-12)

> Entregado por un subagente Haiku 5.5 (tipo Explore, solo lectura) y copiado **sin editar**. La verificación, las correcciones de severidad y las comprobaciones propias están en el anexo A del plan (`PLAN.md` de esta carpeta). Las referencias `archivo:línea` son de `master` @ `00431a27d`.

# Auditoría de aprendizaje AUGE (RINGS): informe final

**Método.** Raíz `C:\Users\valen\Documents\KPKNFit\`. K/ = `android-native\app\src\main\java\com\example\kpkn\`. T/ = `android-native\app\src\test\java\com\example\kpkn\`. HEAD `00431a27d`. Solo lectura. No ejecuté Gradle ni tests: las cifras son simulaciones en `python -c` con las fórmulas del código. Supuesto de todas las simulaciones: ENTHUSIAST (tanks.cns = 250·0,9·1,15 = 258,75; capacidad CNS = max(80; 1,15·258,75) = 297,56; capacidad espinal = max(70; 0,02·4140) = 82,8). Ese supuesto reproduce la fila F0 (80/92/97 a 0/12/24 h). Interpreto «deriveImpliedRecoveryText» como `deriveImpliedRecoveryTime` (K/domain/auge/AugeAdaptiveEngine.kt:27).

**Resumen.**
- P0: L-01 (la calibración se bloquea en el mutex), L-02 (Energía aprende con la suma cruda de drenaje, no con la caída de batería: ×2,5 con ENTHUSIAST), L-03 (la inversión supone caída lineal; el forward es logarítmico: τ entre 1,1× y 2,9× el real a 8–24 h, en los tres canales).
- Deltas y multiplicadores ya no se escriben (retirados el 30-ago). Quedan lecturas vivas de valores heredados (L-06) y código muerto (L-14).
- Con ruido de ±5 puntos, un gesto no identifica τ (L-04): hacen falta umbral y prior.

## A. Mapa, constantes y señales

**Flujo.** (1) Readiness: `WorkoutSessionOverlaysHost.kt:74–101` → `saveWellbeing` (K/screens/auge/AugeViewModel.kt:410–460). Finish: `WorkoutScreen.kt:1740–1772` → `applyManualBatteries` (VM 596–702). Ambos → `learnFromManualAdjustment` (VM 712–835) → `updateSystemRecoveryHours` / `updatePersonalizedRecoveryHours` (K/domain/auge/AugeAdaptiveEngine.kt:65–153) → caché. (2) Rendimiento: `recompute` (VM 188) → `learnFromLatestFinishedLog` (VM 840–944) → `PerformanceTauLearner`. (3) Forward: calculateMuscleBattery (K/domain/auge/AugeRecoveryEngine.kt:461–699), calculateSystemicFatigue (767–891), calculateSpinalBattery (923–1097), calculateGlobalBatteries (1101–1264).

| constante | valor | ruta:línea | propósito |
|---|---|---|---|
| MIN_HOURS_FOR_TAU_LEARNING | 8 h | AugeAdaptiveEngine.kt:12 | gate de inversión |
| OBSERVATION_TAU_RATIO | 0,25 | :50 | tope ±25 % por observación (:52–56) |
| α | max(0,05; min(0,5; 1,5/(n+1))) | :76–77 | tasa EMA; n = contador **global** |
| clamp implied τ | [6; 200] | :47 | h |
| clamp τ aprendido | [12; 144] músculo y CNS (:80, :150); [18; 144] espinal (:151) | | h |
| TAU_K_95 | 2,9957 = ln 20 | K/domain/auge/AugeUtils.kt:30 | decaimiento exp(−2,9957·h_eff/τ) (:39–43) |
| warp muscular | h·0,5 (h<24); 12+(h−24)·1,25 | AugeUtils.kt:136–143 | h_eff |
| warp espinal | h+18·(h−12)/12 en [12;24]; h+18 si h≥24 | AugeUtils.kt:149–157 | h_eff |
| τ por defecto | CNS 36 (AugeRecoveryEngine.kt:779); espinal 52 (:936); perfiles 24/48/72/96 (:37–42) | | h |
| clamp τ forward músculo | [18; 144] | AugeRecoveryEngine.kt:487 | h |
| ERM_SLOPE / RPE | 400 pts por unidad de ratio; 8 pts por RPE | K/domain/auge/PerformanceImpliedBattery.kt:16–17 | constantes de diseño sin fuente en el código |
| NOISE_THRESHOLD | 8 pts | PerformanceTauLearner.kt:57 | umbral de evento |
| C del forward | CNS 75 (AugeRecoveryEngine.kt:885); músculo 90 (:653); espinal 70 (:1095) | | |
| Pisos ENTHUSIAST | músculo 22; CNS 26; espinal 18 | AugeUtils.kt:72 | |
| Decel | 30·√(b/30) si b<30 | AugeUtils.kt:126–129 | |
| Caps DOMS | 5→20; 4→50; 3→85 | AugeRecoveryEngine.kt:658–663 | |
| Malestar | τ×(1+p/120); batería −0,35·p; p ≤ 30 | AugeRecoveryEngine.kt:504, :680, :235 | |

**Señales (Q1 y Q9).**

| señal | captura | efecto | estado |
|---|---|---|---|
| Sensación readiness (Energía, Columna, V1 muscular) | WorkoutSessionOverlaysHost.kt:87–97 → VM 410–460 | τ_CNS, τ_espinal, τ músculo; α global; cap ±25 %; gates VM 440–444 (`≠ pred`), 739–740 (8 h ≤ Δt ≤ 336 h) | VIVA |
| Sensación finish (neural, espinal, V2 por músculo) | WorkoutScreen.kt:1740–1772 → VM 596–702 | ancla V2 (VM 624–632; AugeRecoveryEngine.kt:523–525) y mismo aprendizaje | VIVA; aprendizaje de τ condicionado al orden de historia (E) |
| Global muscular V1 | WorkoutSessionOverlaysHost.kt:87 | ancla lineal del Músculo global (AugeRecoveryEngine.kt:1150–1164) | ANCLA; no aprende |
| Rendimiento (ratio e1RM, ΔRPE, axial) | VM 840–944 → PerformanceTauLearner | τ por canal | VIVA |
| RPE declarado / inferido | `getEffectiveRPE` (AugeFatigueEngine.kt:249–259; default 7,0) | entra en el rendimiento (PerformanceTauLearner.kt:588–593) | VIVA; el inferido es circular (L-11) |
| Malestar (`discomfortIds`, pre-entreno) | WorkoutSessionOverlaysHost.kt:99; Session.kt:631 | modifica el forward; no aprende | MODIFICADOR (L-10) |
| DOMS de readiness | AugeModels.kt:15 | cap de batería (AugeRecoveryEngine.kt:658–664) | MODIFICADOR |
| Feedback post-sesión (DOMS, cnsRecovery) | `savePostSessionFeedback` (VM 463); `mapWorkoutToPostSessionFeedback` (WorkoutFeedbackModels.kt:309) | forward (:238–279) | MUERTA: sin llamadas de producción (H33 confirmado) |
| Evidencia inicial del wizard | Settings.initialRecoveryEvidence | ancla v2 de 14 días (InitialRecoveryEvidence.kt:186, :271) | VIVA; no aprende |
| Overrides de evidencia | — | solo valores por defecto (InitialRecoveryEvidencePolicy.kt:24, :29) | MUERTA |
| augePredictionBias | WorkoutFinishController.kt:547 → WorkoutViewModel.kt:910–912 | stub vacío | MUERTA; campo sin lector Android (Settings.kt:122, :277) |
| Deltas y multiplicadores | ninguna desde 03e54de5b (30-ago) | se leen en forward | ZOMBIS (L-06, L-14) |
| Descanso, duración, notas, motivación, sueño | WorkoutLog.kt:12, :16; AugeModels.kt:16 | descanso y duración alimentan el forward (AugeRecoveryEngine.kt:617, :876–878); lo demás no alimenta nada | NO APRENDE |

## B. Estado de hallazgos previos

| ID | estado | evidencia |
|---|---|---|
| H19 triple aprendizaje y `+3/+5` | ARREGLADO (residuo L-06) | VM 684–695 solo τ; VM 831 `totalObservations = cache.totalObservations + 1,`; commit 03e54de5b |
| H20 `max(0,5 h)` | ARREGLADO | VM 737–740 sin piso; AugeAdaptiveEngine.kt:28 devuelve null si <8 h |
| H21 inversión exp ≠ forward | ABIERTO (CAMBIÓ-DE-FORMA) | AugeAdaptiveEngine.kt:32, :34, :43; L-03 |
| H22 sessionStress = 20 | ARREGLADO (sustituido) | VM 750–766 sin 20; ahora Σ cruda (L-02) |
| H23 sessionStress global por músculo | ARREGLADO | VM 807 `muscleStressFromLog(lastSession, muscle)`; VM 1108–1124 |
| H24 `predicted ?: 100` | ARREGLADO en aprendiz; residuo muerto | VM 805 `?: continue`; VM 630 `?: 100` sin lector (L-13) |
| H25 `lastSession = max(logDateMs)` | ABIERTO | VM 730–736; `sourceSessionId = null` (VM 629) |
| H26 tocar y volver aprende | ARREGLADO para τ | VM 806; VM 440–444; WorkoutScreen.kt:1740–1744. Variante nueva: L-05 |
| H27 migración v1→v2 deja τ | ARREGLADO | AugeAdaptiveCacheMigration.kt:16–21 |
| H28 comentario α ≠ código | ARREGLADO | AugeAdaptiveEngine.kt:63 = :77 |
| H30 segundo loop augePredictionBias | ARREGLADO (Android) | WorkoutViewModel.kt:910–912 stub; campo muerto (Settings.kt:122) |
| H31 caché corrupta sin salida | PARCIAL | Entities.kt:224–238 cae a caché vacía sin telemetría; sin validación de rango; NaN no persistible (dbJson sin `allowSpecialFloatingPointValues`, Entities.kt:16–20); sin UI |
| H32 readiness V1 vs finish V2 | ABIERTO (líneas movidas) | WorkoutSessionOverlaysHost.kt:91–97 (ancla de fila) vs VM 624–632 (ancla por músculo) |
| B12 logDate ≤0; tope 14 días | ARREGLADO | VM 59, 740; PerformanceTauLearner.kt:59, :463, :480 |
| F0–F6 «tope ±25 %» | CONFIRMADO | AugeAdaptiveEngine.kt:50–56; T/domain/auge/AugeRingsAudit2026Test.kt:266–280 |
| F0–F6 «cold-start» | CONFIRMADO | AugeRecoveryEngine.kt:1254–1259 («Sin calibrar»); T/…/InitialRecoveryEvidenceV2Test.kt:101 |
| F0–F6 «override que ancla» | CONFIRMADO | AugeRecoveryEngine.kt:523–525 ↔ InitialRecoveryEvidencePolicy.kt:398–400; T/…/InitialRecoveryEvidencePolicyTest.kt:273 |
| F0–F6 «migración schema 3» | CONFIRMADO con hueco | AugeRingsAudit2026Test.kt:134–149 no afirma `muscleDeltas` ni `muscleDrainMultipliers` (L-06) |
| Fuera de lista: H2 | CAMBIÓ-DE-FORMA | blend 0,50/0,25/0,25 (AugeRecoveryEngine.kt:296–297) |
| Fuera de lista: H4 | ARREGLADO | `systemicRecoveryMultiplier` = 1,0 (AugeRecoveryEngine.kt:169–172); readiness sin 7,5 (WorkoutSessionOverlaysHost.kt:83) |
| Fuera de lista: H13 | CAMBIÓ-DE-FORMA | τ95 unificado (AugeUtils.kt:39–43); curvas por canal distintas |

## C. Hallazgos nuevos (de mayor a menor severidad)

**L-01 · P0 · robustez · VM 395–396, 412, 455.**
Cita: `if (cacheToPersist != null && generation == recomputeGeneration.get()) {` / `augeWriteMutex.lock()`.
`recompute` vuelve a pedir el mutex. Lo llaman, ya dentro del mutex: saveWellbeing (412→455), applyManualBatteries (611→697), dismissAdvisory (1060→1066), clearManualBatteryOverrides (1135→1138) y resetAdaptiveCache (1148→1151). `cacheToPersist` no es null cuando cambia el día UTC o el nivel de aviso: LoadAdvisoryEngine.kt:157 `lastAdvisoryDay = today,` (siempre hoy; `sameDay` en :116).
Escenario: primer recálculo del día (00:03 UTC) disparado desde readiness. VM 430 guarda; VM 347 publica; VM 396 pide el mutex que la misma corrutina tiene → suspendida; 402 y 457 no se alcanzan. El siguiente readiness (412) o finish (611) espera para siempre sin guardar.
Efecto: pérdida silenciosa de calibraciones y de resets hasta reiniciar la app.
Arreglo: `recomputeLocked()` sin lock para llamadas internas. Test: `m.lock(); m.lock()` no retorna en kotlinx `Mutex` sin owner (comportamiento de la librería; no ejecutado).

**L-02 · P0 · matemática/unidades · AugeFatigueEngine.kt:649; PerformanceTauLearner.kt:488; VM 750.**
Cita: `cns = acc.totalCns.coerceAtMost(cnsCap).toInt(),` (:649), frente a `spinal = scaledSpinal.coerceAtMost(spinalCap).toInt()` (:651; el espinal sí pasa por `scaleSpinalDrainToUi`, :539–544). Consumidores: `val stress = if (spinal) drain.spinal.toDouble() else drain.cns.toDouble()` y `val cnsStress = sessionCnsDrain.takeIf { it > 0.0 } ?: lastDrain?.cns?.toDouble()`.
Forward: `val capacity = max(80.0, tanks.cns * 1.15)` (AugeRecoveryEngine.kt:883) y `100.0 * (1.0 - safeExp(-rawGymPct / 75.0))` (:885).
Cuenta (Σ = 49,8 de F0): caída real a 0 h = 100·(1 − exp(−(100·49,8/297,56)/75)) = 20,0. El aprendiz usa D = 49,8: factor ×2,5. Con otro tipo de atleta el factor cambia: ≈2,4 (BODYBUILDER), ≈3,6 (POWERLIFTER).
La vía de finish sí usa caída: WorkoutScreen.kt:1766 `sessionCnsDrain = postSessionPreview.globalCnsDrain.toDouble(),` ↔ AugeRecoveryEngine.kt:1919 `globalCnsDrain = (baseBatteries.cnc - batteries.cnc).coerceIn(0, 100),`. La de readiness (sessionCnsDrain = 0, VM 445) cae a la Σ cruda.
Efecto: cada corrección de Energía en readiness empuja τ_CNS a la baja (0,44·τ₀ a 8 h).
Arreglo: `cnsDrop = 100*(1-exp(-(100*Σ/capCns)/75))` con la `capCns` del forward; usarlo en VM 750 y PerformanceTauLearner 488.

**L-03 · P0 · matemática · AugeAdaptiveEngine.kt:32, :34, :43 (docstring :23).**
Cita: `val initialDepletion = max(actualDepletion, obs.sessionStress)` · `val remainingFraction = clamp(actualDepletion / initialDepletion, 0.01, 0.99)` · `val k = -ln(remainingFraction) / effectiveHours`.
El forward es B(t) = 100·exp(−x·e^{−kt}), con x = carga/C. La fracción restante en espacio de caída es (1−e^{−x·e})/(1−e^{−x}), no e^{−kt}. El docstring (:23) describe `(100 - actualBattery) / (100 - initialDepletion)`; el código divide por `sessionStress`.

Cuadro Q3 (τ_aprendido/τ₀). Columna «código» = tal como está; «caída+lineal» = D corregido con la misma inversión; «log» = `rem = ln(B/100)/ln((100−D)/100)`. n.c. = no calculado (requiere residuo).

| escenario | h | código | caída+lineal | log |
|---|---|---|---|---|
| CNS 1 sesión Σ=50 | 8 / 24 / 48 | 0,44 / 0,71 / 1,02 | 1,11 / 1,05 / 1,33 | 1,02 / 1,00 / 1,05 |
| CNS 2 sesiones apiladas (24 h) | 8 / 24 | 0,47 / 0,71 | 1,29 / 1,05 | n.c. |
| CNS 3 sesiones apiladas (24 h) | 8 / 24 | 0,47 / 0,71 | 1,29 / 1,05 | n.c. |
| CNS depleción pequeña (Σ=20; caída 8,6) | 8 / 24 / 48 | 0,48 / 0,67 / 1,33 | 1,23 / 0,93 / 1,86 | 1,19 / 0,91 / 1,39 |
| CNS depleción grande (Σ=200; caída 59) | 8 / 24 / 48 | 0,39 / 0,69 / 0,87 | 1,42 / 1,19 / 1,18 | 1,01 / 0,98 / 1,05 |
| CNS valor sentido ±5 (Σ=50, h=24), −5 / +5 | 24 | 1,09 / 0,51 | 2,17 / 0,67 | 2,02 / 0,53 |
| Músculo X=0,36 (D=30,2) | 8 / 24 / 48 | 2,21 / 1,30 / 1,13 | = código | 1,04 / 0,98 / 1,02 |
| Músculo X=0,10 (D=9,5) | 8 / 24 / 48 | 1,44 / 1,16 / 1,16 | = código | 0,87 / 0,97 / 1,09 |
| Músculo X=1,0 (D=63) | 8 / 24 / 48 | 2,90 / 1,73 / 1,27 | = código | 1,01 / 1,01 / 1,01 |
| Músculo 2 sesiones (24 h) | 8 | 4,17 | = código | root-solve sobre el modelo completo: 48,6 (verdadero 48) |
| Columna L=20 (D≈29) | 8 / 24 / 48 | 1,22 / 1,06 / 1,13 | = código | 1,06 / 1,00 / 1,08 |
| Columna L=50 (D≈58) | 8 / 24 / 48 | 1,44 / 1,15 / 1,13 | = código | 1,00 / 0,98 / 1,01 |
| Columna L=120 (D≈87) | 8 / 24 / 48 | 2,56 / 1,48 / 1,33 | = código | 1,01 / 1,01 / 1,03 |

Cuentas: CNS, Σ=50, h=24. B = 100·exp(−(100·50·e^{−0,0832·24}/297,56)/75) = 97,0. Código: act = 3, D = 50, rem = 0,06, τ = 2,9957·24/2,813 = 25,6 (0,71). Con caída 20,1: rem = 0,149, τ = 37,8 (1,05). Log: e = ln(0,97)/ln(0,799) = 0,136, τ = 36,1 (1,00).
Músculo, X=0,36, h=8: B = 73,2 (Sig(8) = 4); código con B redondeada a 73: rem = 27/30,2 = 0,894, τ ≈ 107 (2,2). Log: e = ln(0,73)/ln(0,670) = 0,787, τ = 49,9 (1,04).
Columna, L=50, h=8: B = 58,0; D = 57,8; código rem = 0,727, τ = 75,0 (1,44). Log: e = 0,632, τ = 52,2 (1,00).
Lectura: el sesgo está justo en la ventana permitida (≥8 h) y no es ruido. El test de ida y vuelta lo oculta (L-15). Q3 también pregunta por los pisos, decel, DOMS, bloque articular y overrides: no los simulé. Cualitativo: decel con B = 20 corresponde a raw = 13,3 (el aprendiz usa 20); un cap DOMS de 85 con raw 92 hace que confirmar 92 se lea como recuperación más rápida (τ↓); el piso de 26 de Energía satura la lectura.
Arreglo: `rem = ln(B/100)/ln((100−D)/100)`; censurar B ≥ 99,5 y D < 15 (H-1).

**L-04 · P1 · ruido · MinimalMuscleSlider.kt:77; WorkoutReadinessSheet.kt:627–630; VM 440, 806.**
Cita: `val newValue = ((offset.x / barWidth) * 100f).toInt().coerceIn(0, 100)`; `valueRange = 0f..100f,` (sin `steps`); `if (predicted == manualBattery) continue`.
Sin zona muerta: cualquier diferencia de 1 punto es evento. Cuenta (D=50, h=24): manual 97 con pred 98 → act 3 → τ = 25,6; manual 98 → act 2 → τ = 22,3. Un punto mueve τ un 15 % antes del cap y de α. Ruido ±5 (Σ=50, h=24): τ/τ₀ = 1,09 y 0,51 (código); 2,02 y 0,53 (log). La inversión de un gesto no identifica τ cuando la depleción es ~20.
Arreglo: umbral |manual − pred| ≥ 5 pts y prior (H-2).

**L-05 · P1 · idempotencia y contaminación · WorkoutSessionOverlaysHost.kt:87–88; VM 422–423, 430, 439–452; WellbeingSaveMerge.kt:36.**
Cita: `manualNeuralBattery = manualNeural ?: todayWellbeing?.manualNeuralBattery,` (WorkoutSessionOverlaysHost.kt:88); `val anchoredLog = if (hasManualOverrides) {` (VM 422); `manualNeural = anchoredLog.manualNeuralBattery?.takeIf { it != predictedNeural },` (VM 440); `val channels = if (declaresChannels) incoming else existing` (WellbeingSaveMerge.kt:36).
El readiness copia los valores de hoy. VM 430 guarda la fusión, pero VM 439–452 aprende del registro entrante, no de la fusión. Además, con copias `hasManualOverrides` es true y el ancla pasa a «ahora»; si cambia un V1 muscular, `declaresChannels` es true y los globales copiados heredan ese ancla.
Ejemplo ilustrativo: Energía 60 fijada a las 08:00 (aprendida). A las 10:00 se guarda readiness con un V1 muscular cambiado: copia neural 60, predicción 62 → vuelve a aprender τ del mismo gesto y el anillo salta a 60 (en vez de ~62).
Arreglo: aprender solo de canales cuyo valor difiere del guardado (`existing`); no reanclar canales no declarados. Hueco: WellbeingSaveMergeTest.kt:106 (`copiedGlobalDoesNotReanchor`) cubre solo el caso sin cambios.

**L-06 · P1 · migración · AugeAdaptiveCacheMigration.kt:16–22, 23–32; AugeRecoveryEngine.kt:675–677, 1203–1212.**
Cita: `if (storedVersion < 2) {` (:16) — dentro solo se limpian `muscleDeltas` y `muscleDrainMultipliers`; `if (storedVersion < 3) {` (:23) no los toca. Lectura viva: `val myDelta = adaptiveCache.muscleDeltas[muscleName.lowercase().trim()]` (:675); `adaptiveCache.muscleDeltas.values.average().coerceIn(-25.0, 25.0)` (:1204); `clamp(decelMuscular + avgMuscleDelta, 0.0, 100.0),` (:1212).
Historia: schemaVersion por defecto 2 desde el 23-ago (commit f2b17f620) y triple aprendizaje hasta el 30-ago (03e54de5b). Los cachés con esquema 2 conservan deltas y multiplicadores.
Escenario: un único delta de Pectorales −20. Por músculo: −20. Global: −20/13 ≈ −1,5 del pilar más el `avgMuscleDelta` de −20 ⇒ ≈ −21,5 en el anillo Músculos (esperado ≈ −1,5). Además, los multiplicadores siguen en el drenaje (AugeFatigueEngine.kt:681).
Efecto: sesgo persistente en los usuarios afectados. La población es acotada: cachés creados o reseteados entre el 23 y el 30-ago. Prevalencia real: sin datos (E).
Arreglo: en `storedVersion < 3`, limpiar `muscleDeltas` y `muscleDrainMultipliers`.

**L-07 · P1 · matemática · PerformanceTauLearner.kt:431–437; ProgramRepository.kt:838; Daos.kt:69; PerformanceRangeCalculator.kt:54–100.**
Cita: `currentErm = erMs.last(),` (PerformanceTauLearner.kt:437); `_history.update { listOf(normalized) + it.filterNot { existing -> existing.id == normalized.id } }` (ProgramRepository.kt:838); `SELECT * FROM workout_logs ORDER BY date DESC` (Daos.kt:69); pesos `exp(-0.1 * (size - 1 - i))` en computeRange.
`history` es más reciente primero, pero `computeRange` asigna el peso máximo al último índice (cronológico). Simulación (8 sesiones, ±1,5 %/sesión): base subida 104,6 frente a 106,2 esperada; base bajada 95,7 frente a 94,2. Con ERM_SLOPE 400, ≈6 pts, comparable al umbral de 8. Efecto: en progresión suprime aprendizaje de fatiga; en regresión inventa fatiga.
Arreglo: ordenar `erMs` cronológicamente antes de `computeRange`; test con historia descendente.

**L-08 · P1 · estadística · AugeAdaptiveEngine.kt:76–77; VM 795, 821, 831; PerformanceTauLearner.kt:98, 133.**
Cita: `val alpha = max(0.05, min(0.5, 1.5 / samples))`; `totalObservations = cache.totalObservations + 1,` (VM 831).
α depende de un único contador global, no del parámetro ni del canal. α(n): 0,50 (n ≤ 2); 0,375 (3); 0,25 (5); 0,136 (10); 0,071 (20); 0,05 (≥29).
Simulación (y = 36 ideal, τ₀ = 48; primera observación del músculo tras N₀ eventos globales; τ tras k = 1…6):

| N₀ | k=1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| 0 | 42,0 | 39,0 | 37,5 | 36,9 | 36,7 | 36,5 |
| 5 | 45,0 | 43,1 | 41,7 | 40,8 | 40,1 | 39,5 |
| 15 | 46,9 | 45,9 | 45,1 | 44,4 | 43,7 | 43,2 |
| 40 | 47,4 | 46,8 | 46,3 | 45,8 | 45,3 | 44,8 |
| Kalman escalar (σ₀ = σ_obs = 30 %) | 40,3 | 38,6 | 37,9 | 37,5 | 37,2 | 37,0 |

Cuenta N₀ = 40: α = max(0,05; 1,5/41) = 0,05; τ₁ = 48·0,95 + 36·0,05 = 47,4 (cierra 5 % de la brecha). Kalman: K = 207/(207+117) = 0,64, τ₁ = 48 − 0,64·12 = 40,3.
Efecto: un músculo nuevo aprende al 5 % si otros tuvieron ~30 eventos.
Arreglo: contador por parámetro y ganancia de Kalman (H-2).

**L-09 · P1 · clamps · AugeAdaptiveEngine.kt:80, :150; AugeRecoveryEngine.kt:487, :779, :1155–1163.**
Cita: `return current + (muscleKey to clamp(newTau, 12.0, 144.0))` frente a `val baseRecoveryTime = clamp(adaptiveHours ?: RECOVERY_PROFILES[profileKey] ?: 48.0, 18.0, 144.0)`.
τ aprendido en [12; 18) no tiene efecto en el forward. En CNS el forward no tiene clamp (`val baseTau = adaptiveCache.cnsRecoveryHours ?: 36.0`). El ancla global muscular usa la media de todos los τ personalizados (:1155–1158) con ley aditiva `100.0 - penalty * exp(...)` (:1163), distinta de la multiplicativa por músculo (:525) y del 48 documentado en InitialRecoveryEvidencePolicy.kt:257.
Trayectoria (objetivo fijo 15,8 = 0,44·36, cap ±25 %): 36 → 31,5 → 27,6 → 24,1 → 21,8 → 20,2 tras 5 observaciones. La deriva es monótona y converge al objetivo sesgado; no se queda en 12 en 5 observaciones.
Arreglo: un único clamp coherente; una sola ley de ancla.

**L-10 · P1 · contaminación · AugeRecoveryEngine.kt:502–504, :680, :658–664.**
Cita: `(discomfortPenaltyPct.coerceAtLeast(0.0) / 120.0)` (:504); `battery = clamp(battery - discomfortPenaltyPct * 0.35, 0.0, 100.0)` (:680).
El aprendiz invierte sin modelar malestar ni DOMS. Malestar de 30: τ×1,25 y −10,5 pts. Ejemplo de DOMS 3 (cap 85), con raw 92: el usuario confirma 92 y la diferencia con 85 se lee como recuperación más rápida (τ↓). Es confusión, no ruido.
Arreglo: H-3 (root-solve con las mismas penalizaciones).

**L-11 · P1 · contaminación · PerformanceTauLearner.kt:441–452, 574–581, 315; AugeFatigueEngine.kt:257–258.**
Cita: `sets.maxOf { calculateHybrid1RM(it.weight, it.reps) }` (:451); `return (report?.technicalQuality ?: 10) <= 2` (:580).
Lesiones: no se excluyen (`discomfortIds` no aparece en el aprendiz). Deload con RPE igual: no se excluye. PR: solo se excluye si implied > predicted (:315). RPE inferido: `else -> 7.0` (AugeFatigueEngine.kt:258) entra en `matchedRpeDelta`.
Cuenta e1RM (Brzycki, Calculations.kt:60–66): 100×5 = 112,5; 100×4 = 109,1; ratio = 0,9697 → −12,1 pts, por encima del umbral de 8 con solo una repetición. P(evento | pred 98) con σ del e1RM de 1/2/3 %: 0,6 / 10,6 / 20,2 %. Con pred 90: 0 / 1,2 / 6,7 %. σ supuesta, no medida. Δ RPE = 1 → 8 pts (PerformanceImpliedBattery.kt:25–28).
Arreglo: excluir sesiones con `discomfortIds` ≠ none; no usar RPE inferido; usar mediana de 3 sesiones en lugar de máximo.

**L-12 · P2 · reloj · VM 866–867, 889, 961–1001; PerformanceTauLearner.kt:335.**
Cita: `if (ageHours > 6.0 || ageHours < 0.0) {` (VM 867); `val hours = (nowMs - previous.logMs) / 3_600_000.0` (:335).
La predicción de inicio no pasa `nowOverrideMs`: se evalúa en el instante de aprender, no en el de la sesión. Cuenta: Σ = 80 → B₀ = 69,9; B(+6 h) = 80,4; Δ = 10,5 ≥ 8 → «fatiga» falsa y τ↑. Con Σ = 50: Δ ≈ 7,4 (bajo el umbral). El reloj mixto (VM `System.currentTimeMillis`; motores `AugeClock` en AugeRecoveryEngine.kt:100 y directo en :1709) impide tests deterministas.
Arreglo: `nowOverrideMs = newestMs` en la predicción de inicio y `hours = newestMs − previous.logMs`.

**L-13 · P2 · claves y código muerto · AugeAdaptiveEngine.kt:72; VM 630; VolumeCalculator.kt:155; AugeMuscleNormalization.kt:84.**
Cita: `val muscleKey = observation.muscle.lowercase().trim()` (:72). El aprendizaje manual pasa nombres canónicos (VM 687, 808). «Romboides» predice con el pilar Dorsales (AugeMuscleNormalization.kt:84) pero escribe la clave `romboides`, que `calculateMuscleBattery` nunca lee (AugeRecoveryEngine.kt:482 usa pilar). `automaticBatteryAtAnchor = predictedMuscleBatteries[muscle] ?: 100,` (VM 630) no tiene lector (AugeModels.kt:117 solo define).
Arreglo: `toAugeAdaptiveMuscleKey(observation.muscle)` en :72; borrar el campo.

**L-14 · P2 · código muerto · AugeAdaptiveEngine.kt:91, :159, :221; AugeRecoveryEngine.kt:1206–1207; AugeFatigueEngine.kt:691–693, 746–747, 857–859, 925–926.**
Cita: `val cnsDelta = adaptiveCache.cnsLearningDelta.coerceIn(-15.0, 15.0)` (AugeRecoveryEngine.kt:1206).
Sin llamadas de producción: `updateSystemLearningDeltas`, `updateDrainMultipliers`, `updateMuscleDeltas` (solo definiciones y tests). Se leen `cnsLearningDelta`, `spinalLearningDelta`, `cnsDrainMultiplier`, `spinalDrainMultiplier`, `muscleDrainMultipliers` (también en AugeRecoveryEngine.kt:603, 619–620, 744–745, 820, 853–854, 1032, 1066–1067 y MuscularSessionImpactEngine.kt:204–205, 304–305). Efecto: constantes salvo los cachés heredados (L-06). Tests que fijan funciones muertas: T/domain/auge/AdaptiveCalibrationAppliesTest.kt:78, :113; T/domain/auge/AugeAdaptiveEngineTest.kt:11–115.
Arreglo: borrar campos y funciones, o aislarlos, y retirar esos tests.

**L-15 · P2 · hueco de test · T/domain/auge/AugeRingsAudit2026Test.kt:52–53.**
Cita: `val remaining = exp(-AugeUtils.TAU_K_95 * hours / tau)` / `val actual = (100.0 - stress * remaining).toInt()`.
El valor sentido es lineal, no sale de `calculateSystemicFatigue` (AugeRecoveryEngine.kt:885). Por eso no detecta L-02 ni L-03.
Arreglo: round-trip contra `calculateSystemicFatigue(nowOverride)` con Σ de `calculateCompletedSessionDrain`.

**L-16 · P2 · concurrencia · VM 209, 742, 834, 869, 904 · HIPÓTESIS NO VERIFICADA.**
`learnFromLatestFinishedLog` guarda sin mutex (869, 904) y `recompute` lee la caché sin mutex (209). Intercalado posible: T1 (applyManualBatteries) lee C₀ (742); T2 (recompute) lee C₀ (209), aprende y guarda C₀+rendimiento (904); T1 guarda C₀+gesto (834). Se pierde uno. Si se pierde el sello `lastPerformanceLearnLogId`, tras reiniciar se reaprende (el in-memory de VM 838 no sobrevive). No ejecutado.

**L-17 · P2 · matemática · AugeRecoveryEngine.kt:1240, :1356.**
Cita: `if (enabled && initial != null) (real - (100 - initial)).coerceIn(minimum, 100) else real`.
La combinación es aditiva. Cuenta: real 50, evidencia 80 → aditivo 30; producto de supervivencias (la ley multiplicativa del modelo) 40. Diferencia de 10 pts cuando ambos son bajos.
Arreglo: `real * initial / 100`.

## D. Lo sano (invariantes verificados)

- Ley de ancla consistente: AugeRecoveryEngine.kt:523–525 = InitialRecoveryEvidencePolicy.kt:398–400, B(t) = 100·(B₀/100)^{d(Δt)}. Test: InitialRecoveryEvidencePolicyTest.kt:273.
- Inversión de Columna exacta: AugeRecoveryEngine.kt:296–297 ↔ :304–315. Si S ≤ 2I, D = S; si no, S = 2(D−I). Test: AugeRingsAudit2026Test.kt:182.
- Sin doble resta de evidencia: `initialInPillars` (AugeRecoveryEngine.kt:1243–1245) y `keptDayLoads` (InitialRecoveryEvidencePolicy.kt:210–220). Test: AugePerMuscleInitialEvidenceTest.kt:66.
- Guardas de τ: <8 h (AugeAdaptiveEngine.kt:28; VM 739); >336 h (VM 740); cap ±25 % (:50–56); α acotada (:77). Tests: AugeAdaptiveEngineTest.kt:146; AdaptiveCalibrationAppliesTest.kt:14; PerformanceTauObservationTest.kt:97.
- Idempotencia del rendimiento en memoria y persistida: VM 853–861, 854, 904.
- Mediana de ΔRPE robusta a outliers (PerformanceTauLearner.kt:414).
- Migración: decode fallido → caché por defecto, sin excepción (Entities.kt:224–238); v<2 limpia τ muscular (AugeAdaptiveCacheMigration.kt:16–21).
- Descartadas por lectura: (a) «schemaVersion se omite y la migración lee v1»: refutado, `dbJson` tiene `encodeDefaults = true` (Entities.kt:18); (b) «respaldo a stressUnits mezcla % y unidades»: no alcanzable en V2 (rawDrain = 100·(1−e^{−stress/cap}) = 0 ⇔ stress = 0; MuscularSessionImpactEngine.kt:229–233); (c) «H23 sesión global para cada músculo»: refutado (VM 807).

## E. Preguntas abiertas y no verificado

- **Orden de persistencia en finish** (HIPÓTESIS NO VERIFICADA): si `_history` aún no contiene la sesión cerrada cuando corre `applyManualBatteries`, `lastSession` apunta a la anterior. ProgramRepository.kt:1046 actualiza `_history` tras `persistFinalization`; no leí la cadena completa ni su `await` frente a `onComplete` (WorkoutFinishController.kt:557).
- **`channelScore(SYSTEM)`** (VM 998) frente a `batteries.cnc` (VM 436, 892): si difieren, la predicción del rendimiento usa otra escala. No leído (AugeModels.kt).
- `calculateMuscleSessionStress` (AugeRecoveryEngine.kt:701–766, fallback de logs legacy): no leído; unidades sin verificar.
- `calculateSpineFatigueMultiplier` y bono de duración espinal: afectan el forward espinal, no el aprendiz; efecto no cuantificado.
- Las simulaciones no incluyen pisos, decel, DOMS, bloque articular, overrides, spineProtection ni bonos (ver L-03).
- Prevalencia de cachés con deltas heredados: sin datos.
- Git: una llamada devolvió error de memoria del entorno. Los cambios sin commit del árbol (p. ej. ExerciseTechniqueImageLookup.kt) están fuera de mi alcance; no comprobé el estado completo de mis archivos más allá de lo leído.
- Tests leídos solo por nombre y aserciones puntuales: PerformanceTauObservationTest, InitialRecoveryEvidencePolicyTest, InitialRecoveryEvidenceV2Test, AugePerMuscleInitialEvidenceTest, WellbeingSaveMergeTest, AugeAdaptiveEngineTest, AdaptiveCalibrationAppliesTest.

## F. Cobertura real

| archivo | leído | total |
|---|---|---|
| K/domain/auge/AugeAdaptiveEngine.kt | 1–275 | 275 |
| K/domain/auge/PerformanceTauLearner.kt | 1–618 | 618 |
| K/domain/auge/PerformanceImpliedBattery.kt | 1–29 | 29 |
| K/domain/auge/AugeAdaptiveCacheMigration.kt | 1–38 | 38 |
| K/domain/auge/InitialRecoveryEvidencePolicy.kt | 1–461 | 461 |
| K/domain/auge/WellbeingSaveMerge.kt | 1–50 | 50 |
| K/data/models/AugeAdaptiveModels.kt | 1–39 | 39 |
| K/data/models/InitialRecoveryEvidence.kt | 1–312 | 312 |
| K/data/repository/AugeRepository.kt | 1–187 | 187 |
| K/screens/auge/AugeViewModel.kt | 1–1213 (completo) | 1213 |
| K/domain/auge/AugeRecoveryEngine.kt | 30–109, 160–259, 296–355, 461–710, 767–901, 923–1102, 1101–1369 (+grep) | 1935 |
| K/domain/auge/AugeFatigueEngine.kt | 24–135, 233–302, 412–471, 536–544, 588–832 (+grep) | 1069 |
| K/domain/auge/MuscularSessionImpactEngine.kt | 196–245 | 327 |
| K/domain/auge/AugeUtils.kt | 1–173 | 173 |
| K/domain/auge/AugeMuscleNormalization.kt | 1–197 | 197 |
| K/domain/auge/AugeClock.kt | 1–10 | 10 |
| K/domain/auge/LoadAdvisoryEngine.kt | 104–165 | 281 |
| K/data/models/Settings.kt | grep L122, L277 | 452 |
| K/screens/workout/WorkoutFinishController.kt | 520–561 (+grep 547, 557) | 857 |
| K/screens/workout/WorkoutSessionOverlaysHost.kt | 70–110 | — |
| K/screens/workout/WorkoutScreen.kt | 1736–1775 | — |
| K/screens/workout/WorkoutViewModel.kt | 905–914 | — |
| K/data/repository/ProgramRepository.kt | 832–840, 954 (grep), 1036–1051, 2012–2026 | — |
| K/data/db/Entities.kt | 16–20 (grep), 160–260 | — |
| K/data/db/Daos.kt | 69 (grep) | — |
| K/domain/training/VolumeCalculator.kt | 140–169 | — |
| K/domain/performance/PerformanceRangeCalculator.kt | 50–100 | — |
| K/domain/calculations/Calculations.kt | 55–80 | — |
| T/domain/auge/AugeRingsAudit2026Test.kt | 40–75, 134–155, 266–300 | 369 |
| Resto de tests de aprendizaje | nombres y aserciones puntuales | — |
| Documentos de auditoría (4) | completos | 466 + 31 + 23 + 43 |

## G. Sueño (informativo)

- El multiplicador de sueño está neutralizado: AugeRecoveryEngine.kt:169–172 devuelve 1,0, y el readiness ya no siembra 7,5 (WorkoutSessionOverlaysHost.kt:83).
- Residuo: `sleepQuality` entra en `RecoveryLearningObservation` (AugeAdaptiveModels.kt:36) y en VM 760 y 781, pero la inversión (AugeAdaptiveEngine.kt:27–48) no lo usa. `sleepLogs` se consultan en recompute (VM 207) sin efecto en el aprendizaje.

## H. Hasta tres mejoras de aprendizaje

**H-1 (corrige L-02 y L-03): unidades y espacio logarítmico.**
- Esbozo: D = 100·(1 − exp(−(100·Σ/capCns)/75)); B₀ = 100 − D; e = ln(B/100)/ln(B₀/100); τ = 2,9957·h_eff/(−ln e). Censura: B ≥ 99,5 o D < 15 → sin evento.
- Enchufe: VM 750–751 (en lugar de `lastDrain?.cns` y `spinal`), PerformanceTauLearner.kt:488, y un helper junto a AugeAdaptiveEngine.kt:27–48.
- Riesgo: cambia τ de Energía y Columna de todos los usuarios. Migrar con schemaVersion 4 (poner `cnsRecoveryHours` a null) o aplicarlo solo a eventos nuevos. La censura reduce eventos admitidos.
- Test JVM determinista: round-trip contra `calculateSystemicFatigue(nowOverride)` con τ ∈ {24, 36, 60}, h ∈ {8, 24, 48}, Σ ∈ {50, 200}; exigir |τ̂/τ − 1| ≤ 0,05 cuando D ≥ 15 y B < 99,5.

**H-2 (corrige L-04, L-05, L-08): prior por parámetro, umbral y dedupe de gesto.**
- Esbozo: por parámetro p, m_p (τ), v_p (varianza), n_p. m₀ = τ₀; v₀ = (0,3·τ₀)². Observación y con R = (0,3·y)²: K = v/(v+R); m ← m + K·(y − m); v ← (1 − K)·v. Umbral: |manual − pred| ≥ 5 pts. Dedupe: id = (logId, canal, anchorMs); si el id ya se usó, no aprende.
- Enchufe: AugeAdaptiveEngine.kt:65–81 y :123–153 (α → K); campos nuevos en AugeAdaptiveModels.kt:6–27 (`obsCountByParam`, `usedGestureIds`, con valores por defecto); filtros en VM 440–452 y 804–827.
- Riesgo: aprendizaje más conservador; cambia el comportamiento visible. Los contadores nuevos empiezan vacíos en la migración.
- Test JVM determinista: con N₀ ∈ {0, 5, 15, 40}, la primera observación debe cerrar ≥ 35 % de la brecha (hoy: 5 % con N₀ = 40); aplicar dos veces el mismo id deja τ idéntico.

**H-3 (corrige L-03 en apilado, L-10, L-11, L-17): inversión por raíz sobre el forward.**
- Esbozo: τ* = argmin_{τ ∈ [12; 144]} |F(τ) − manual|, con F = calculateSystemicFatigue / calculateSpinalBattery / calculateMuscleBattery y la caché candidata. Bisección (F es monótona en τ fuera de topes). Aceptar si |F(τ*) − manual| ≤ 2 y la lectura no está en piso, DOMS ni tope.
- Enchufe: sustituye `deriveImpliedRecoveryTime` (AugeAdaptiveEngine.kt:27–48) y las llamadas VM 789–802 y 804–826, usando `nowOverride` = instante de la sesión.
- Riesgo: coste (≈12 evaluaciones sobre 10 días de historia); no es monótona donde hay caps o pisos, así que hay que censurar esas zonas; cambia la semántica de τ.
- Test JVM determinista: historia de 3 sesiones apiladas con τ* = 40 → τ̂ ∈ [39; 41]; con manual ±1 → |Δτ| ≤ 3 %; con DOMS = 3 y manual en el tope → sin evento.
