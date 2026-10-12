# Informe del agente D · drenaje (auditoría RINGS/AUGE, 2026-10-12)

> Entregado por un subagente Haiku 5.5 (tipo Explore, solo lectura) y copiado **sin editar**. La verificación, las correcciones de severidad y las comprobaciones propias están en el anexo A del plan (`PLAN.md` de esta carpeta). Las referencias `archivo:línea` son de `master` @ `00431a27d`.

# Auditoría de DRENAJE RINGS/AUGE: informe final (solo lectura)

HEAD `00431a27d`. No se escribió ningún archivo. Las cuentas son réplicas en `python -c` de las fórmulas leídas y reproducen los pines de `AugeRingCharacterizationTest` (Energía 80,7→80; Energía ligera 90,5→90; Columna 93,5→93). Salvo indicación: fixture de ese test (sentadilla 4,2/4,5/1,0 axial 1,0; banca 3,2/3,5/0,35 axial 0) y tanques de ENTHUSIAST (valor por defecto, `Settings.kt:39`).

**Resumen**
- **P0:** Energía y Columna convierten la misma unidad (% de tanque) con escalas distintas. En la misma sesión, Columna muestra 1,67 puntos por punto de tanque y Energía 0,40. Los pines 80 y 93 fijan esa escala.
- **P0:** el editor no reescala Columna y, en una sesión solo cardio, muestra el fallback inventado 10/14/8.
- **P1 (8):** aprendizaje y términos de Home que no llegan a todas las superficies; capacidad autorreferencial con dos motores; Core persistido por el creador; carga invisible sin homologación; progresivo del SNC por músculo; suelo de 6 series; superserie con doble conteo; serie de tiempo tratada como fuerza.

## A. Mapa del cálculo y constantes reales

Flujo: (1) `isSetEffective` (RPE≥6) → (2) `calculateSetBatteryDrain` (`AugeFatigueEngine.kt:412–537`): reps^0,65 × RPE × progresivo × descanso × sesgo de reps × técnica × densidad × near-RM × (carga solo en columna) × 1,85/1,15/5,2, expresado en % de tanque → (3) `aggregateSetIntoSessionDrain` (`:588–635`): acumulado por pilar primario, conservación, atenuación, soft-cap por canal → (4) `finalizeSessionDrain` (`:637–667`): Columna reescalada (`:539–544`) y suelo de sesión (`:788–807`) → (5) superficies: finish (`WorkoutFinishController.kt:303`), chip V2 (`MuscularSessionImpactEngine.kt:148–247`), editor (`SessionEditorAugeComputation.kt:485–772`) y Home (`AugeRecoveryEngine.kt:767–891`, `:923–1096`).

| Constante | Valor | Ubicación | Propósito / unidad |
|---|---|---|---|
| Tanques base CNS/músculo/espinal | 250/300/4000 × nivel × mult. | `AugeFatigueEngine.kt:92, 106–108` | % de tanque (ENTH: 258,75/310,5/4140) |
| Capacidad base ENTHUSIAST | 2600 | `:32` | stressUnits V2 |
| Caps por serie | 32 / 32 / 35 | `:533–535` | % tanque |
| Pisos ENTHUSIAST → caps de sesión | 22/26/18 → 78/74/82 | `AugeUtils.kt:72`; `AugeFatigueEngine.kt:677–679` | |
| Conservación; K de atenuación | 0,85; 0,65 | `AugeUtils.kt:19, 22`; `AugeFatigueEngine.kt:622` | |
| Soft-cap | 1−0,5p; 0,8e^(−3,2(p−0,4)); 0,3e^(−5,5(p−0,7)) | `AugeUtils.kt:167–169` | breakpoints 0,40 y 0,70 |
| Stress units; capacidad | ×10; [1200; 35000] | `AugeUtils.kt:33–35` | |
| Pesos de rol | 1,0 / 0,5 / 0,4 / 0,1 | `ExerciseMuscleInfo.kt:230–235` | fijados por `AugeLanguageConsistencyTest.kt:30` |
| RPE: defecto / umbral / multiplicador | 7 / ≥6 / 1+(min(rpe,10)/10)^4,2 | `:258`, `:282`, `:288` | cap 2,3 por encima de 10 (`:293`) |
| Progresivo muscular | 1+0,05(n−1); 1,10−0,10(n−3); max(0,3; 0,8e^(−0,25(n−6))) | `:328–330` | por pilar primario |
| Progresivo SNC y columna | 1+0,055(n−1)^1,55, [1; 2,6] | `:336` | por pilar primario (D-04) |
| Descanso (músc./SNC/estructura) | 0,85→1,10 / 1,35→0,85 / 1,25→0,88 | `:453–478` | |
| Sesgo de reps (músc./SNC/columna) | ≤4: 0,78/1,25/1,22; ≥12: 1,08/0,88/0,85 | `:481–496` | |
| Carga (solo columna) | 1+0,25·ln(1+kg/20) | `:499–501` | |
| Near-RM | ratio ≥0,85/0,90/0,94/0,98 → ×1,10/1,20/1,30/1,40 | `:396–400`, proxy `:386–390` | |
| Densidad superserie | [1,10; 1,45]; SNC ×1,5; músculo ×0,35; columna ×1,15 | `:322`, `:514–516` | |
| Dropset | RPE forzado a 10; SNC ×(1+0,20(2ⁿ−1)) | `:264–268`, `:364` | D-12 |
| Asistidas | 1+0,20·n | `:518–519` | D-19 |
| Suelo de sesión dura | ≥6 series con RPE≥8 → SNC≥10, músculo≥10, columna≥8 si axial | `:757–758`, `:796–802` | D-05 |
| Cardio | escalas 0,45/0,06/0,65; protocolo SIT 1,25/HIIT 1,0/nulo 0,6 | `CardioRingDrainEngine.kt:21–23, 75–79` | |
| Capacidad V2 | ventana 35 d; semanas [1;5]; ×1,8 | `AugeMuscleCapacityEngine.kt:23, 47–50` | |
| Capacidad legacy | 4 semanas fijas (/4,0; ×1,8) | `AugeRecoveryEngine.kt:388–390, 453–454` | H11 |
| Energía (Home) | max(80; 1,15·TC); 1−e^(−x/75); τ 36 h; singles ×1,15; duración ×1,08/×1,15 | `:779`, `:866–868`, `:877–878`, `:883–885` | |
| Columna (Home) | max(70; 0,02·TS); 1−e^(−x/70); SPF; duración ×1,08 | `:920`, `:936`, `:949`, `:1078`, `:1086`, `:1093–1095` | |
| Columna (finish) | igual que Home sin SPF ni duración | `AugeFatigueEngine.kt:540–542` | |

**Unidades (Q2).** % tanque = raw/tanque·100 (`:533–535`). Stress = %·10 (`AugeUtils.kt:33`; `MuscularSessionImpactEngine.kt:210`). Inmediato V2 = 100(1−e^(−stress/cap)), cap ≥1200 (`MuscularSessionImpactEngine.kt:229`). Truncamientos: `toInt()` en finish (`AugeFatigueEngine.kt:649–651`) y en EMA (`:1039–1041`). `roundToInt` en editor (`SessionEditorAugeComputation.kt:711–713`). Divisiones: tanques >0; `coerceAtLeast(1.0)` en `CardioRingDrainEngine.kt:91`. No hallé división por cero. NaN o negativos desde la entrada: HIPÓTESIS NO VERIFICADA.

**Sesiones canónicas (Q8), réplica:**

| Sesión | CNS | Músculo | Columna | Energía | Test |
|---|---|---|---|---|---|
| (a) sentadilla 5×5 @140 + banca 4×6 @90, RPE 8 | 44 | 50 | 5 (8 con suelo) | 81 | sin pin |
| (a′) igual, RPE 8,5 (fixture) | 47 | 52 | 6,5 (8 con suelo) | 80,7 → 80 | pin 80 ✓ (`:63`); Columna 93,5 → 93 ✓ (`:64`) |
| (b) banca 3×10 @60, RPE 7 | 22 | 25 | 0 | 90,5 → 90 | pin 90 ✓ (`:73`) |
| (c) 10 curls sin ficha, RPE 9 | 48 | 35 | 2 (8 con suelo) | 80,5 | sin pin |

## B. Estado de los hallazgos previos asignados

| ID | Estado | Evidencia |
|---|---|---|
| H5 | CAMBIÓ-DE-FORMA | Series solo de tiempo son efectivas (`AugeFatigueEngine.kt:278–280`); test fija «isométrico» (`AugeUnitsConsistencyTest.kt:118–120`). «Cardio sin cardioDetails no mueve Energía» ya no es cierto (D-03) |
| H10 | ARREGLADO en doble rol; ABIERTO en coexistencia | Legacy aplica `resolveMuscleVolumeContribution` una vez (`AugeRecoveryEngine.kt:632`); `FATIGUE_ROLE_MULTIPLIERS` solo en tests (`ExerciseMuscleInfo.kt:238`); V2 y legacy conviven (`AugeRecoveryEngine.kt:547–550`) |
| H11 | ABIERTO (parcial) | Home usa V2 (`:1299–1308`, `:1341`); legacy vivo (`:380–456`, `:476`, `:1122`). D-08 |
| H12 | CAMBIÓ-DE-FORMA | Secundario 0,5 (`ExerciseMuscleInfo.kt:237–238`); el total global no usa roles (`roleWeightForDrain`, `AugeFatigueEngine.kt:563–564`, solo alimenta `perMuscleMuscular` sin consumidor). Queda la divergencia chip/cuota (D-15) |
| H14 (pisos, drenaje) | ABIERTO, sin cambio | Pisos en `AugeUtils.kt:67–73`; caps 100−piso en `AugeFatigueEngine.kt:677–679` |
| H15 | ARREGLADO en motor; REGRESIÓN en creación | Motor sin Core (`AugeFatigueEngine.kt:243`, `else → emptyList()`); el creador persiste Core (`ExerciseAugeInference.kt:137–140`). D-09 |
| B9 | ARREGLADO | Capacidad con `stressUnits` (`AugeMuscleCapacityEngine.kt:37, 44`) |
| B11 | ARREGLADO | `accumulatedSets += 0.5` (`MuscularSessionImpactEngine.kt:196`; `AugeFatigueEngine.kt:609`) |
| B14 | CAMBIÓ-DE-FORMA (contrato invertido) | Grep de pines «no drena» en tests: 0. El test vigente fija lo contrario (`AugeUnitsConsistencyTest.kt:118–120`). D-03 |
| B15 | NO-APLICA al drenaje; pesos fijados | Pesos 1,0/0,5/0,4/0,1 fijados por `AugeLanguageConsistencyTest.kt:30` |

## C. Hallazgos nuevos (de mayor a menor severidad)

**D-01 [P0 · matemática/unidades] Energía y Columna dividen dos veces por el tanque.**
- `AugeRecoveryEngine.kt:883–885`: `val capacity = max(80.0, tanks.cns * 1.15)` / `val rawGymPct = (accumulatedGymLoad / capacity) * 100.0` / `val normalizedGymFatigue = clamp(100.0 * (1.0 - safeExp(-rawGymPct / 75.0)), 0.0, 100.0)`. El acumulado ya es % de tanque (`AugeFatigueEngine.kt:534`).
- Columna: `AugeRecoveryEngine.kt:1093–1095` y `AugeFatigueEngine.kt:540–542`: `max(70.0, tanks.spinal * 0.02)`, después `1 − e^(−rawPct/70)`.
- Cuenta (S1 del test, Σ CNS = 47,95): Energía = 100·e^(−(47,95/297,56·100)/75) = 80,7. Caída visible 19,3 = 0,40·Σ. Con Σ = 100 (tanque agotado), Energía = 63,9. Columna: Σ espinal = 3,92 → 6,54 → batería 93,5.
- Powerlifter (TS 6720), mismo S1: Columna 2,58 frente a 6,54 (−61 %). El tanque ya entra en el % por serie (×0,63); el reescalado añade ×0,62 (=82,8/134,4). La intención de «más tanque, menos drenaje» daría −37 %.
- Efecto: magnitud de Energía y Columna sin escala física, y dependencia cuadrática del tipo de atleta en Columna.
- Arreglo: una sola conversión % tanque → batería para ambos anillos, y recalibrar los pines 80/93. Es decisión de producto.

**D-02 [P0 · inconsistencia] Editor: Columna sin escalar, cardio fuera del global y fallback inventado.**
- `SessionEditorAugeComputation.kt:711–713`: `spinal = pipelinedTotalSpinal.roundToInt().coerceIn(0, 100),`. El finish reescala (`AugeFatigueEngine.kt:647`: `val scaledSpinal = scaleSpinalDrainToUi(acc.totalSpinal, tanks)`). S1: editor 4 frente a finish 6,5 (8 con suelo).
- Cardio: `:525–530` suma a `muscleDrainMapPipelined` y a `totalSpinalLoad` (`:533`), pero no a `pipelinedTotal*`, que alimentan `predictedDrain` (`:709–714`). `totalSets += 1` (`:530`) cuenta el cardio.
- `:727`: `if (adjusted.cns == 0 && adjusted.muscular == 0 && adjusted.spinal == 0 && totalSets > 0) {` → `DEFAULT_FALLBACK_PREDICTED_DRAIN` (10/14/8, `AugeUtils.kt:61`).
- Cuenta: bici 30 min RPE 6 (MET 8,5 para nivel 6, `CardioIntervalEngine.kt:184`). Finish = CNS 2,9 / músculo 10,9 / columna 0,7. Editor = 10/14/8.
- Arreglo: el editor debe usar la misma conversión, acumular el cardio en el global y activar el fallback solo si el cálculo falla de verdad.

**D-03 [P1 · robustez] Series solo de tiempo sin `cardioDetails` entran en el motor de fuerza.**
- `AugeFatigueEngine.kt:278–280`: `val hasTime = (set.timeSeconds ?: 0) > 0` / `if (set.isFailedSet && set.reps <= 0 && !hasTime) return false` / `if (set.reps <= 0 && !hasTime && set.weight <= 0.0) return false`. Y `:429`: `(set.timeSeconds ?: 0) > 0 -> ((set.timeSeconds ?: 0).coerceAtLeast(5) / 5.0)`.
- Cuenta: 1 serie de 2400 s, RPE 6,0, sin ficha (2,5/2,5/0,5), descanso 90 s → reps = 480; CNS y músculo topan en 32 %; sesión 27/27/5. Con RPE 5,9 → 0/0/0 (`:282`). Cardio para 40 min a RPE 6: 3,8/14,6/0,9.
- Efecto: salto de 27 puntos por 0,1 RPE y sobreestimación ×7 en SNC frente al modelo de cardio.
- Alcance: `WorkoutCompletedExercises.kt:50` conserva `cardioDetails` en el finish. Que el usuario pueda crear un ejercicio sin ficha con temporizador: HIPÓTESIS NO VERIFICADA (no leí la UI de creación).

**D-04 [P1 · sesgo] El progresivo del SNC y de la columna se reinicia por músculo primario.**
- `AugeFatigueEngine.kt:608–609`: `var accumulated = acc.muscleVolumeMap[primaryPillar] ?: 0.0` / `accumulated += if (set.side != null) 0.5 else 1.0`. `:450`: `val systemicProgressiveMult = calculateSystemicProgressiveMultiplier(accumulatedSets)`, usado en SNC y columna (`:525–528`). Energía repite el patrón: `AugeRecoveryEngine.kt:835`.
- Cuenta (ENTH, réplica): 6 sentadillas (5 reps, 140 kg, RPE 8) en un músculo → SNC 37,4. 3+3 en dos músculos → 32,6 (−13 %). 6 músculos distintos → 30,7 (−18 %). 10 curls RPE 9 (`cnc` 1,5): mismo músculo → SNC 48,3; 10 músculos distintos → 35,2 (−27 %), mientras el músculo sube de 35,2 a 42,5 (+21 %). En Energía, 10 curls (80,5) ≈ sentadilla+banca (80,7): el SNC depende del recuento, casi nada de `cnc`.
- Arreglo: contador de series duras global para el progresivo del SNC; por pilar solo para el muscular.

**D-05 [P1 · acantilado/fantasma] Suelo de sesión dura: columna 0→8 en la sexta serie, y cuenta el cardio.**
- `AugeFatigueEngine.kt:757–758`: `val hardSets = completedExercises.flatMap { it.sets }` / `.count { isSetEffective(it) && getEffectiveRPE(it) >= 8.0 }`. Cuenta también series de cardio. `:796` y `:801–802`: `if (hardEffectiveSets < 6) return drain` / `max(drain.spinal, (minDrop * 0.8).toInt())` si `hasAxialLoad`.
- `:67–68`: `axial == null || axial > 0.0`. Un ejercicio sin ficha se trata como axial.
- Cuenta: 5 curls a RPE 8 sin ficha → columna 0 (raw 0,46). 6 curls → columna 8 (raw 0,58, escalado 1,0). CNS 20→24 y músculo 21→24 sin activar el suelo.
- HIPÓTESIS NO VERIFICADA: un cardio registrado como ≥6 series RPE≥8 (intervalos) activa el suelo. `expandedBlocks` (`CardioIntervalEngine.kt:27–35`) repite bloques, pero no creo series.
- Arreglo: el suelo solo si el canal tiene drenaje bruto >0 y la carga axial es conocida (`axial != null`).

**D-06 [P1 · doble conteo] Superserie: el descanso corto se penaliza dos veces.**
- El mismo `restTime` alimenta `systemRestMult` (`:462–470`, 1,15 para ≤75 s) y `getDensityMultiplierForExercise` (`:305–306`, 1,24 para ≤75 s), que sube el SNC con `systemDensityMult = 1.0 + (density - 1.0) * 1.50` (`:515`). Ambos reciben `restTime = ex.supersetRestBetween ?: ex.restTime` (`:713`, `:741`).
- Cuenta (6×8 @90 RPE 8, descanso 60 s): SNC por serie 9,62 sin superserie y 13,08 con ella (×1,36). Sesión: 45,6 → 53,3 (+17 %). Músculo ×1,08 por serie; columna ×1,28.
- Arreglo: aplicar el descanso en un solo término (densidad o `systemRest`).

**D-07 [P1 · inconsistencia] Aprendizaje y términos de Home que no llegan a todas las superficies.**
- (a) Chip V2 sin multiplicador muscular: `MuscularSessionImpactEngine.kt:206` (`muscleMultiplier = 1.0,`) frente a `AugeFatigueEngine.kt:731, 745`. Los logs con V2 reutilizan `storedImpact` (`AugeRecoveryEngine.kt:544` comenta «V2 logs already contain canonical local stress and the capacity»; `:547–555` sin multiplicador). Los logs sin V2 sí aplican `muscleMult` (`:621`). Multiplicador aprendido 1,20 en Pectorales: el global sube ×1,20 y el chip de logs nuevos no.
- (b) Términos solo en Home: singles `if (s.weight > 0.0 && s.reps <= 3 && …getEffectiveRPE(s) >= 9.5) {` / `currentCns += adjustedCns * 0.15` (`AugeRecoveryEngine.kt:866–868`); duración `if (durationMin > 90) sessionCns *= 1.15` (`:877`). Columna: `× spineProtectionMultiplier × axialGate` (`:1078`) y `if (log.durationMinutes > 90) sessionSpinalLoad *= 1.08` (`:1086`). Ni el finish ni el editor los aplican.
- (c) Editor sin multiplicadores (`SessionEditorAugeComputation.kt:616–626`) y con EMA (`:722–723`; factor 0,925–1,03, `AugeFatigueEngine.kt:1026–1036`). Con multiplicador CNS aprendido 1,2: editor ×1,0, finish ×1,2 (`AugeFatigueEngine.kt:746`).
- Arreglo: un solo camino de cálculo (finish = editor = Home) con el mismo conjunto de multiplicadores.

**D-08 [P1 · sesgo] Capacidad autorreferencial y dos motores de capacidad.**
- `AugeMuscleCapacityEngine.kt:50`: `return max(baseCapacity(settings), averageWeekly * 1.8).let(AugeUtils::clampWorkCapacity)`, con el historial de 35 días (`:23, 25`). Cuanto más se entrena, mayor la capacidad y menor el drenaje inmediato.
- Cuenta (S = 750, base 2600, régimen estable): 1 sesión/semana → 25,1 %; 2 → 24,3 %; 3 → 16,9 %; 5 → 10,5 %.
- Dos motores: el legacy (`AugeRecoveryEngine.kt:380–456`) divide por 4 semanas fijas (`:453`: `val weeklyAvg = totalStress / 4.0`) y se usa cuando falta `precomputedCapacity` (`:476`) y en `calculateGlobalBatteries` sin `precomputedMuscles` (`:1122`). Cálculo a mano: 4 sesiones de 750 en una semana → V2 cap 5400 (13 %); legacy 2600 (25 %). Home usa V2 (`:1299–1308`). Los pines de pecho (`AugeRingDrainRealismTest.kt:83–90`) llaman sin `precomputedCapacity` y miden el legacy.
- Arreglo: un solo motor; documentar o neutralizar la autorreferencia.

**D-09 [P1 · dato] El creador persiste Core sin coincidencias; heurísticas por nombre.**
- `ExercisePickerV2Catalog.kt:3284–3285`: `val info = SmartExerciseCreator.createAutomatic(query, catalog)` / `CustomExerciseRepository.upsert(info)`. `SmartExerciseCreator.kt:82` devuelve `preview(request, catalog).exercise`. Guarda `efc/cnc/ssc` de la inferencia (`:140–142`: `ssc = suggestions.ssc ?: 0.3,`) y `axialLoadFactor = top?.axialLoadFactor ?: 0.0` (`:144`).
- Sin coincidencias, los músculos vienen de `inferExerciseMetrics` (`ExerciseAugeInference.kt:137–140`: `else -> listOf(` / `InvolvedMuscle("Core", MuscleRole.PRIMARY, 1.0),`).
- El overlay entra en el índice de drenaje (`ExerciseDatabase.kt:94, 101, 117`). El motor usa `dbInfo.efc` (`AugeFatigueEngine.kt:116`) y sus músculos (`:209`). Por tanto, las métricas por nombre dejan de aplicarse.
- Cuenta: «Hip thrust», «Prensa de pierna» o «Fondos» sin coincidencias → Core 1,0 + Erectores 0,4 (no hay regla que los capte). Cuántos nombres reales caen aquí: HIPÓTESIS NO VERIFICADA.
- Heurística sin ficha: «Zancada lateral» → Deltoides Lateral (`AugeFatigueEngine.kt:235`, `lower.contains("lateral")`, precede a `:241`). «Sentadilla búlgara» → métricas de sentadilla (`:139` precede a `:146`): ssc 1,2 frente a 0,5 (×2,4) y efc 3,8 frente a 2,8.
- Arreglo: el creador debe marcar «sin datos» y no inventar músculos. La regla de «lateral» debe ir después de las de zancada.

**D-10 [P1 · sesgo] La carga absoluta no entra en SNC ni músculo sin homologación.**
- SNC y músculo no tienen término de carga (`AugeFatigueEngine.kt:524–529`); solo la columna usa `loadFactor` (`:499–501`). La carga relativa entra solo vía `nearRm` (`:393–402`), y solo si hay `homologatedResultV3` (`WorkoutSetRecorder.kt:460`). Los sets planificados no lo tienen (`syntheticSet`, `:901–913`).
- Cuenta: 5×5 RPE 8 a 60 kg frente a 140 kg: SNC 30,89 frente a 30,88; músculo 37,54 frente a 37,53; columna 2,90 frente a 3,27 (+13 %).
- Escalón: con homologación, `ratio >= 0.85 -> 1.10` (`:399`). 84 % → 85 %: +10 % SNC.
- Peso corporal (`weight = 0`): 5 reps a RPE 8,5 activan `nearRm 0,85` por reps/RPE (`:384–390`), sin conocer la carga: +10 % SNC.
- Arreglo: exigir carga o homologación antes de `nearRm`, y suavizar el escalón.

**D-11 [P2 · acantilados] Escalones de reps, RPE, serie y cap.**
- Reps 4→5 (RPE 8, 100 kg): músculo 4,78→7,08 (+48 %; sesgo `:483`, `reps <= 4.0 -> 0.78`); SNC 6,00→5,55.
- RPE 8,4→8,5 (5 reps): SNC 5,90→6,60 (+12 %) y columna 0,77→0,87 (+13 %).
- Serie 6→7: multiplicador muscular 0,80→0,62 (−22 %; `:329–330`). Marginal de la serie 12 en sentadilla: 0,8 frente a 8,9 de la primera.
- `AugeUtils.kt:168–169`: en p = 0,70 el factor pasa de 0,306 a 0,300 (−2 %).
- Descanso: `LR` ≥240 s = ×1,10 y ≤120 s = ×1,00 (`:453–461`). Más descanso, más drenaje muscular. El comentario `:452` lo declara intencional; a confirmar.

**D-12 [P2 · triple conteo] Dropset.**
- `:264–265, 268`: un drop fuerza RPE 10 (`if (techniqueBonus > 0 && baseRpe < 10.0) baseRpe = 10.0`); `:364` penaliza técnica; `:439` suma reps (`val repsCns = baseReps + 0.85 * dropReps + 0.90 * rpReps`).
- Cuenta: 6 reps a RPE 8 más un drop de 4 reps → SNC ×2,31 (reps ×1,34 · RPE ×1,44 · técnica ×1,20). Músculo ×1,84.

**D-13 [P2 · orden] El drenaje depende del orden de los ejercicios.**
- `AugeFatigueEngine.kt:622` (`diminishingFactor`) y `:626–628` (caps) usan el acumulado previo.
- Sentadilla→banca frente a banca→sentadilla: SNC 44,66 frente a 44,94 (+0,6 %); músculo 50,04 frente a 50,09; columna raw 3,27 frente a 2,96 (−9,5 %).
- Arreglo: aplicar saturación al total por canal, no secuencialmente.

**D-14 [P2 · redondeo] Truncamiento a entero.**
- `AugeFatigueEngine.kt:649–651`: `cns = acc.totalCns.coerceAtMost(cnsCap).toInt(),` (y análogos). EMA en `:1039–1041`. Sesgo medio ≈ −0,5 pt por canal (supuesto de fracción uniforme). S1: 47,95→47; 52,40→52; 6,54→6.
- Arreglo: `roundToInt()` y conservar `Double` hasta presentar.

**D-15 [P2 · inconsistencia] Chip V2 frente a cuota del editor.**
- `MuscularSessionImpactEngine.kt:210–213`: `val baseStress = drain.muscularDrainPct * 10.0 * diminishing` / `contributions.forEach { (muscle, contribution) ->` / `val stress = baseStress * contribution`. Pesos absolutos 1,0/0,45/0,35, sin conservación ni cap.
- Editor: `SessionEditorAugeComputation.kt:635`: `val share = roleWeight / totalRoleWeight`.
- Cuenta (banca 4×6 @90 RPE 8, cuatro pines de cuota): V2 Pecho stress 322,8 → 11,7 %; Tríceps 145,3 → 5,4 %. Editor: músculo global 26,8 → Pecho 14,9, Tríceps 6,7.
- Arreglo: una sola curva para chip y editor.

**D-16 [P2 · mapeo] Pilares divergentes V2 frente a global.**
- «Espalda baja»: V2 → Dorsales (`VolumeCalculator.kt:156`, `espalda` precede a `:159`, erector); global → Erectores (`AugeMuscleNormalization.kt:38`).
- «Romboides»: V2 → «Romboides» (`VolumeCalculator.kt:155`, en la lista estándar de `:64`); global → Dorsales (`AugeMuscleNormalization.kt:84`).
- Análisis estático. Si el catálogo contiene estos nombres: HIPÓTESIS NO VERIFICADA (grep: 1 aparición entre 8 términos, sin identificar cuál).

**D-17 [P2 · código muerto y duplicado]**
- `calculateCompletedSessionMuscleDrains` (`AugeFatigueEngine.kt:776–786`): sin llamadas de producción; solo tests (`SessionDrainBoundsTest.kt:118`, `AugeLanguageConsistencyTest.kt:76, 93`). `perMuscleMuscular` y `roleWeightForDrain` (`:551, 560, 563–564`) no tienen otro consumidor.
- `globalFromLocal` (`MuscularSessionImpactEngine.kt:311–315`, `/ 700.0`): inalcanzable, porque ambas construcciones pasan `editorGlobalMuscularDrain` (`:78–82`, `:140–144`).
- `FATIGUE_ROLE_MULTIPLIERS` (`ExerciseMuscleInfo.kt:238`): alias usado solo en tests. `HYPERTROPHY_ROLE_MULTIPLIERS` (`:223`): sin uso; solo importado (`ProgramDetailViewModel.kt:17`).
- `inferExerciseMetrics` (deprecated): solo `ExerciseMatchEngine.kt:195` como prefill, pero se persiste (D-09).
- `augeFatigueSensitivity` (`Settings.kt:297`): solo entra en el hash de contexto (`MuscularSessionImpactEngine.kt:302`). Flag muerto para el drenaje (ya citado en 2026-08-29 §9.18).

**D-18 [P2 · entradas raras]**
- Reps 0 con peso >0 y RPE ≥6 es serie efectiva (`AugeFatigueEngine.kt:280–282`) y sube el progresivo: la primera serie real pasa de MP(1)=1,00 a MP(2)=1,05 y SP(2)=1,055, con drenaje 0.
- Cardio sin tiempos por serie: duración 0 y drenaje 0 sin aviso (`:685–688`).
- RPE 0 → 1,0 → no efectiva (`:270`, `:282`) sin aviso.
- HIPÓTESIS NO VERIFICADA: RPE NaN o reps negativas en `dropSets` producen NaN, que `toInt()` convierte en 0.

**D-19 [P2 · pregunta de signo] Reps con ayuda suben el drenaje.**
- `AugeFatigueEngine.kt:518–519`: `val assistedMultiplier = 1.0 + (assistedCount * 0.20)`, sobre los tres canales y sin tope. n=5 → ×2,0; n=10 → ×3,0 antes de los caps.
- La UI dice «con ayuda» (`WorkoutContextComponents.kt:434`); el homologador lo trata como «consolidar carga sin ayuda» (`WorkoutPerformanceHomologationEngine.kt:350`). Puede ser intencionado (esfuerzo cercano al fallo), pero no hay comentario que lo justifique.

## D. Lo que está sano y no debe tocarse

- Caps por serie 32/32/35 con test (`SessionDrainBoundsTest.kt:175–188`).
- Suelo de sesión dura pinneado (`SessionDrainBoundsTest.kt:32–53`) y pisos fisiológicos respetados por los anillos (`:126–172`).
- Atribución por cabeza de deltoides por máximo, sin doble conteo (`VolumeCalculator.kt:199–201`; test `SessionDrainBoundsTest.kt:56–96`).
- Capacidad V2 excluye la sesión actual (`AugeMuscleCapacityEngine.kt:25`).
- Rangos: densidad [1,10; 1,45] (`AugeFatigueEngine.kt:322`, `:511`); RPE en [1; 12] (`:270`); capacidad en [1200; 35000] (`AugeUtils.kt:34–35`).
- Monotonía: el drenaje crece con cada serie adicional (replica de 12 series), con RPE (`:285–294`) y con la carga en columna.
- Sin Core por defecto en el motor (`AugeFatigueEngine.kt:202`, `:243`).
- Axial 0 → sin columna (`:51`, `spinalAxialGate`).
- Pines de Energía y columna (80/90/93): `AugeRingCharacterizationTest.kt:63–73`. Cambiar D-01 exige recalibrarlos.

## E. Preguntas abiertas / no verificado

1. D-01: ¿la compresión de Energía (Σ 100 → 64) y la escala de Columna son decisión de producto? Los pines dependen de ellas.
2. D-05: ¿cuántas series crea un cardio por bloque? Determina si el suelo se activa.
3. D-03: ¿la UI permite un ejercicio sin ficha con series de tiempo? No leí la creación de sesión.
4. D-19: semántica deseada de reps con ayuda.
5. D-16: qué términos del catálogo llevan «espalda baja» o «romboides».
6. Pines de pecho (`AugeRingDrainRealismTest.kt:96–99`, `:121–124`): usan la capacidad legacy; no los reproduje con V2.
7. HIPÓTESIS NO VERIFICADA: NaN o negativos desde la UI.
8. SPF (`AugeRecoveryEngine.kt:917–920`) y su circularidad con erectores, dorsales y trapecio: fuera de alcance, no auditado.
9. No leídos: `AugeUnilateralImpactTest.kt`, `AugeRealSessionFixtures.kt`, `Session.kt` (solo grep: `resolvedRpe` en `:434`, `effectiveDurationSeconds` en `:443`), `AugeAdaptiveModels.kt` (solo grep: defaults `:13–15`), y el resto de `AugeRecoveryEngine.kt` y `SmartExerciseCreator.kt`.

## F. Cobertura real (archivo → líneas leídas / total)

| Archivo | Leído | Estado |
|---|---|---|
| AugeFatigueEngine.kt | 1–1069 / 1069 | completo |
| MuscularSessionImpactEngine.kt | 1–327 / 327 | completo |
| AugeClassifiers.kt | 1–132 / 132 | completo |
| ExerciseFatigueIndex.kt | 1–53 / 53 | completo |
| SessionIntensityEngine.kt | 1–96 / 96 | completo |
| CardioRingDrainEngine.kt | 1–118 / 118 | completo |
| AugeMuscleCapacityEngine.kt | 1–75 / 75 | completo |
| AugeMuscleNormalization.kt | 1–197 / 197 | completo |
| SessionMuscleFilter.kt | 1–20 / 20 | completo |
| AugeUtils.kt | 1–173 / 173 | completo |
| ExerciseAugeInference.kt | 1–163 / 163 | completo |
| VolumeCalculator.kt | 1–260 / 548 | solo contribuciones (alcance); 261–548 son tarjetas de volumen, listadas por grep, sin ruta de drenaje |
| AugeModels.kt | 1–489 / 489 | tipos |
| WorkoutLog.kt | 1–402 / 402 | tipos |
| AugeRingCharacterizationTest.kt | 1–160 / 160 | completo |
| AugeRingDrainRealismTest.kt | 1–232 / 232 | completo |
| SessionDrainBoundsTest.kt | 1–189 / 189 | completo |
| AugeFatigueEngineEffectiveSetTest.kt | 1–44 / 44 | completo |
| ExerciseMuscleInfo.kt (dependencia) | 1–247 / 247 | completo |
| CardioIntervalEngine.kt (dependencia) | 1–209 / 209 | completo |
| AugeRecoveryEngine.kt (fuera de alcance; funciones citadas) | 150–172, 376–456, 395–434, 536–565, 612–641, 767–891, 923–1101, 1114–1134, 1296–1308 | parcial |
| SessionEditorAugeComputation.kt | 60–139, 480–808 | parcial |
| WorkoutFinishController.kt | 164, 225–345 | parcial |
| ExerciseDatabase.kt | 88–105, 175–204 | parcial |
| SmartExerciseCreator.kt | 65–191 | parcial |
| ExercisePickerV2Catalog.kt | 3058–3097, 3262–3293 | parcial |
| ExerciseMatchEngine.kt | 180–219 | parcial |
| WorkoutCompletedExercises.kt | 25–68 | parcial |
| Session.kt; AugeAdaptiveModels.kt | grep | no leído |

## G. Observaciones de sueño (informativas)

- Ningún archivo del alcance de drenaje contiene términos de sueño. Grep en `domain/auge`: 47 coincidencias, todas en `AugeRecoveryEngine.kt`.
- `systemicRecoveryMultiplier` devuelve 1,0 (`AugeRecoveryEngine.kt:169–172`). El 7,5 h de H4 ya no altera τ de Energía: H4 ARREGLADO, verificado.
- La función de `:150–162` aún lee `sleepHours` (`:157`). Su consumidor no está verificado; podría ser código muerto.
