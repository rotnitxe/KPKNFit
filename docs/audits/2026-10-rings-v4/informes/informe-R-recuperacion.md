# Informe del agente R · recuperacion (auditoría RINGS/AUGE, 2026-10-12)

> Entregado por un subagente Haiku 5.5 (tipo Explore, solo lectura) y copiado **sin editar**. La verificación, las correcciones de severidad y las comprobaciones propias están en el anexo A del plan (`PLAN.md` de esta carpeta). Las referencias `archivo:línea` son de `master` @ `00431a27d`.

## Informe final: recuperación y composición de los anillos AUGE (solo lectura)

**Prefijos absolutos:** K = `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\java\com\example\kpkn\`. T = `C:\Users\valen\Documents\KPKNFit\android-native\app\src\test\java\com\example\kpkn\`. VM = `K/screens/auge/AugeViewModel.kt`. No ejecuté Gradle ni tests. La aritmética se hizo con `python -c` de una línea.

**Resumen**
- La ley de batería `100·e^(−R/C)` (C = 90/75/70) y las curvas por canal son coherentes entre sí. Los pins de F0 se reproducen a mano.
- Lo que cambia números visibles: el aprendizaje de τ tiene un sesgo sistemático (R-01, P0). Las vistas no coinciden entre sí (blend, overlay del finish, evidencia inicial; R-02, R-06, R-07). Hay escalones sin entrenar (R-03, R-04) y horas a normal mal escaladas (R-05).
- Latente o muerto: tope DOMS, deltas musculares y de Energía/Columna, multiplicadores de drenaje (sin escritor en K), y varias funciones sin llamadores.

---

## A. Mapa, orden de operaciones y constantes

**A.1 Orden de operaciones (Q1)**

- **Músculo por pilar** (`K/domain/auge/AugeRecoveryEngine.kt` 461–699): τ = clamp(τ aprendida ?? perfil, 18, 144) (l.487) × max(0.5, nutrición) × (1 + P_fb/48 + P_molestias/120) (l.502–506). k = 2.9957/τ (l.508). Residuo V2 = stress·C/C_log·e^(−k·sig(h)) con soft cap (l.547–557). Legado por serie (l.571–644). Ancla manual fija el estado (l.513–529). raw = acc/C·100, b = 100·e^(−raw/90) (l.652–654). Después: si acc ≤ 0.1 y DOMS ≤ 2 → 100 (l.656); tope DOMS 20/50/85 (l.658–664); −0.35·P_molestias (l.680); +Δ_pilar y suelo (l.682).
- **Músculos global** (l.1101–1265): cada pilar × (0.6 + 0.4·articular/100) (l.1136–1147). Luego 0.85·media + 0.15·cuartil inferior (l.1168–1172). Con ancla manual se usa 100 − penalty·e^(−k·sig) con τ = media de τ aprendidas, sin clamp (l.1150–1164). ACWR solo sin ancla (l.1175–1179). Decel <30 (l.1210). +Δ medio (l.1203–1205, sin escritor). Suelo (l.1211–1214). Evidencia inicial aditiva (l.1239–1240).
- **Energía** (`calculateSystemicFatigue`, l.767–891): τ = adaptativa ?? 36 (l.779, sin clamp propio). Decaimiento lineal e^(−2.9957·h/τ). Bonus +15 % con ≤3 reps y RPE ≥ 9.5 (l.866–868). ×1.08/×1.15 por duración (l.877–878). raw = acc/max(80, 1.15·tanque) (l.795, 883–885). Global: decel(b+Δ) y suelo 26 en ENTHUSIAST (l.1215–1220).
- **Columna** (`calculateSpinalBattery`, l.923–1097): τ = adaptativa ?? 52 (l.936). Decaimiento sobre `getSpinalRecoveryHours` (`K/domain/auge/AugeUtils.kt:149–157`). Cada serie × multiplicador SPF (l.1002–1015) × compuerta axial (l.1053, 1078). ×1.08 si >90 min (l.1086). raw = acc/max(70, 0.02·tanque) (l.949, 1093–1095). Global: decel(b+Δ) y suelo 18 (l.1221–1226). Dashboard: Columna = min(espinal, round(0.5·espinal + 0.25·A + 0.25·G)) (l.290–298, 1389–1393). A = media de las 2 articulares más bajas de las 7 (l.321–327). G = mínimo de Erectores/Dorsales/Trapecio (l.317–332).
- **Articular** (`K/domain/auge/AugeTtcEngine.kt` 205–380): stress·e^(−k·eff). k = 2/τ con τ = 48 o 60 (l.312–313). eff = 0.05·h si h < 24, y 1.2 + (h − 24) después (l.322–326). Molestias 10·q·e^(−h/96) (l.228–249). Pre-entreno 10 sin decay (l.264–267). Feedback no resuelto 15·max(0, 1 − h/168) (l.340–349). b = 100 − 100·acc/80 (l.360), lineal.
- **Preview** (`previewPostSessionBatteries`, l.1786–1934): por músculo, overlay lineal b_base − immediateDrainPct (l.1853–1879), que es lo que expone `perMuscle` (l.1918). Columna del preview con guardias del overlay (l.1884–1885). Global exponencial (l.1880–1883). "Mañana" con reloj de pared (l.1888–1924).

**Orden: coherencia.** Dentro de cada canal es coherente. Entre vistas no: el suelo de pilar (l.682) va antes del blend global (l.1146), que puede dejar un pilar bajo su suelo. Decel y +Δ no conmutan (l.1210–1212). DOMS (min) y la resta por molestias no conmutan. La evidencia inicial es aditiva y no sigue la ley de composición (R-07). El overlay es lineal y el Home exponencial (R-06).

**A.2 Constantes reales**

| Constante | Valor | Ubicación | Unidad | Propósito |
|---|---|---|---|---|
| TAU_K_95 | 2.9957 (ln 20) | `K/domain/auge/AugeUtils.kt:30` | – | k = 2.9957/τ, 95 % en τ |
| Conservación / decay sesión | 0.85 / 0.65 | `AugeUtils.kt:19, 22` | – | serie y rendimientos decrecientes |
| Pesos CNS / músculo / espinal | 0.45 / 0.25 / 0.30 | `AugeUtils.kt:25–27` | – | ranking de sesión |
| Unidades V2 | ×10 por % de drenaje | `AugeUtils.kt:33` | stressUnits | |
| Capacidad V2 clamp | 1200 – 35000 | `AugeUtils.kt:34–35, 45–46` | stressUnits | |
| Sigmoide músculo | 0.5·h (<24 h); 12 + 1.25·(h − 24) | `AugeUtils.kt:136–143` | h efectivas | |
| Warp columna | +18 h lineal entre 12 y 24 h | `AugeUtils.kt:149–157` | h efectivas | |
| Perfiles τ | 24 / 48 / 72 / 96 | `K/domain/auge/AugeRecoveryEngine.kt:38–41` | h | rápido/medio/lento/pesado |
| Clamp τ músculo | 18 – 144 | `AugeRecoveryEngine.kt:487` | h | |
| Factores τ | feedback/48; molestias/120 | `AugeRecoveryEngine.kt:503–504` | – | |
| Molestias por coincidencia | +8; tope 30 | `AugeRecoveryEngine.kt:217, 232, 235` | pts | |
| Resta por molestias | 0.35·P | `AugeRecoveryEngine.kt:680` | pts | |
| Tope DOMS 5 / 4 / 3 | 20 / 50 / 85 | `AugeRecoveryEngine.kt:658–663` | % | ver R-08 |
| Tope por sesión | (100 − suelo)% de C | `AugeRecoveryEngine.kt:510–511` | – | |
| C músculo / energía / columna | 90 / 75 / 70 | `AugeRecoveryEngine.kt:653, 885, 1095` | – | b = 100·e^(−R/C) |
| τ base energía / columna / ancla global | 36 / 52 / 48 | `AugeRecoveryEngine.kt:779, 936, 1158` | h | |
| Capacidad energía (ENT) | max(80, 1.15·tanque) = 297.6 | `AugeRecoveryEngine.kt:795`; tanque en `K/domain/auge/AugeFatigueEngine.kt:92–108` | % | |
| Capacidad columna (ENT) | max(70, 0.02·tanque) = 82.8 | `AugeRecoveryEngine.kt:949` | % | |
| Capacidad base músculo (ENT) | 2600 | `AugeFatigueEngine.kt:31–43` | stressUnits | |
| Suelos ENT | 22 / 26 / 18 | `AugeUtils.kt:72` | % | |
| SPF | 0.45 erect. + 0.30 dors. + 0.25 trap.; ×(1 + d²·0.75) si <80 | `AugeRecoveryEngine.kt:915–920` | | |
| Blend pilar / global | 0.6 + 0.4·art/100; 0.85 media + 0.15 cuartil | `AugeRecoveryEngine.kt:1146, 1172` | | |
| Columna blend | 0.50·espinal + 0.25·A + 0.25·G | `AugeRecoveryEngine.kt:296` | | techo en R-14 |
| Pesos dashboard | 0.40 / 0.35 / 0.25 | `AugeRecoveryEngine.kt:1485–1487` | | |
| Decel | 30·√(b/30) si b < 30 | `AugeUtils.kt:126–129` | % | |
| ACWR | <0.8 U; ≤1.3 S; ≤1.5 C; >1.5 D; factor 1.06 / 1.12 | `K/domain/auge/AugeClassifiers.kt:37–42, 57–61` | | ver R-03 |
| Articular | cap 80; τ 48/60 | `K/domain/auge/AugeTtcEngine.kt:26–28` | | |
| Bandas / estado de pilar | 85/70/50/35 · 95/85/40 | `K/domain/auge/RecoveryBands.kt:11–14` · `AugeRecoveryEngine.kt:684–689` | % | ver R-17 |
| Aprendizaje | mín 8 h; α = max(0.05, min(0.5, 1.5/(n+1))); ±25 % | `K/domain/auge/AugeAdaptiveEngine.kt:12, 28, 50, 52–56, 77` | | |
| Clamps aprendidos | τ músculo 12–144; CNS 12–144; columna 18–144 | `AugeAdaptiveEngine.kt:80, 150–151` | h | |
| Δ globales | músculo ±25 (medio); CNS y columna ±15 | `AugeRecoveryEngine.kt:1204, 1206–1207` | pts | sin escritor (R-12) |
| Flag nutrición | false | `K/data/models/Settings.kt:294` | | |
| Atleta por defecto | ENTHUSIAST | `Settings.kt:39` | | |

**A.3 Curvas temporales (Q2).** Batería inicial 70 (caída de 30) con τ por defecto. Calculado con la ley del motor.

| h | Músculo τ48 | Músculo τ24 | Músculo τ96 | Energía τ36 | Columna τ52 | Articular k=2/48 |
|---|---|---|---|---|---|---|
| 0 | 70.0 | 70.0 | 70.0 | 70.0 | 70.0 | 70.0 |
| 6 | 74.4 | 78.2 | 72.3 | 80.5 | 77.7 | 70.4 |
| 12 | 78.2 | 84.5 | 74.4 | 87.7 | 83.6 | 70.7 |
| 24 | 84.5 | 92.3 | 78.2 | 95.3 | 96.9 | 71.5 |
| 48 | 97.4 | 99.8 | 90.8 | 99.3 | 99.2 | 89.5 |
| 72 | 99.6 | 100.0 | 96.3 | 99.9 | 99.8 | 96.1 |

- **Recuperado a 24 h** (fracción de la caída): músculo 48 %, energía 84 %, columna 90 %, articular 5 %.
- **Quiebres de derivada**: músculo, pendiente efectiva 0.5 → 1.25 a 24 h (valor continuo). Columna, 1 → 2.5 a 12 h y 2.5 → 1 a 24 h. Articular, 0.05 → 1 a 24 h (×20). Ninguno salta en valor.

**A.4 Respuestas breves a Q3–Q8**

- **Q3 (bajar sin entrenar, determinismo, bordes).** Sí puede bajar: por ACWR (R-03) y por el corte de anclas a las 18 h (R-04). Con el mismo `nowMs` el resultado depende del reloj en ACWR y articular (R-11). Historial vacío = 100 (D). Fecha ≤ 0 excluida de las ventanas (D). Fecha futura cuenta en Energía y Columna pero no en Músculos (R-10). El finish guarda ISO (`K/screens/workout/WorkoutFinishController.kt:366`), así que la fecha solo-día no sale de ese flujo. Medianoche: cambia el wellbeing "de hoy" (R-04).
- **Q4 (apilado).** Suma lineal de residuos en R y exponencial al final (l.646–654). Ver D.
- **Q5 (dos caminos).** V2 frente a legado solo difiere si falta la entrada V2 (l.547–549 frente a 571). Capacidad V2 frente a legado es invariante para logs V2 (l.551–557) y solo afecta a logs sin V2 (H11). Global frente a pilares difiere por blend, ACWR y decel (R-02, R-03). Preview frente a Home difiere por el overlay (R-06). Dashboard frente a batería cruda difiere en Columna (H2).
- **Q6 (previews y overlays).** El overlay es lineal (R-06). `calculateDailyReadiness` usa `dashboard.overallScore` (l.1512) y tiene uso de producción (VM 263, `K/data/repository/ProgramRepository.kt:1196`, `K/screens/workout/WorkoutViewModel.kt:3977`). Los rankings de sesión, de ejercicio y la recuperación personal no tienen llamadores (R-12).
- **Q7 (dashboard).** `ringScore` = canal del dashboard (`K/data/models/AugeModels.kt:174–178`). Músculos y Energía coinciden con la batería interna. Columna no coincide (blend). El motor no tiene histéresis (la de los avisos está fuera de alcance). La confianza depende del número de sesiones y del sueño (l.1399–1411).
- **Q8 (sin llamadores de producción).** Confirmado por grep sobre K: `calculateSessionDrainRankings` (l.1600), `calculateExerciseDrainRankings` (l.1629), `calculatePersonalRecoveryStats` (l.1704), `calculateSleepRecommendations` (l.1531), `pillarMuscles` (l.1592; solo tests). En RecoveryBands, `description` se rellena en el snapshot (l.1452, 1464, 1476) pero ninguna pantalla la pinta. El resto de RecoveryBands y AugeUtils tiene consumidor. La rama legado `calculateUserWorkCapacity` (l.380–456) solo la usan tests.

---

## B. Estado de los hallazgos previos asignados

| ID | Estado | Evidencia |
|---|---|---|
| H2 | ABIERTO | Columna = min(espinal, blend) (l.296–297, 1389–1393). La descripción que explica el blend (`K/domain/auge/RecoveryBands.kt:96`) no la pinta ninguna pantalla. `K/screens/home/RingDetailSheet.kt:73, 104` pinta `meaning` y `action`. |
| H3 | ABIERTO (residual intencional) | l.890 `return Triple(cnsBattery, normalizedGymFatigue.toInt(), 0)`. |
| H10 | PARCIAL: rol ×1 ARREGLADO; dos caminos ABIERTO | Legado con un solo factor (l.632 `share = resolveMuscleVolumeContribution(...)`). `FATIGUE_ROLE_MULTIPLIERS` es alias de la tabla de contribución (`K/data/models/ExerciseMuscleInfo.kt:238`). V2 sigue sin rol (l.547–569). |
| H11 | CAMBIÓ-DE-FORMA; sin impacto en V2 | Todos los llamadores de producción pasan `precomputedMuscles`: VM 227–254 y 955–1000, `K/data/onboarding/SetupRingsPreviewCalculator.kt:164–207`, `ProgramRepository.kt:1165–1176`. La rama legado (l.476–478) solo corre sin precomputar. Con logs V2 la capacidad actual se cancela (l.551–557). Solo divergen los logs sin V2: ventana /4 fija frente a `weeks ∈ [1, 5]` (`K/domain/auge/AugeMuscleCapacityEngine.kt:47–50`). |
| H13 | ABIERTO | Curvas sin cambio (`AugeUtils.kt:136–143, 149–157`; `AugeTtcEngine.kt:318–329`). Ver A.3. |
| H14 | ABIERTO | Suelos sin cambio (`AugeUtils.kt:67–73`). Decel sin cambio (l.126–129). |
| H16 | ARREGLADO (flag off; sin comidas = neutro) | `Settings.kt:294` false; l.1571 `return 1.0`; `K/domain/auge/NutritionRecoveryEngine.kt:51–56` devuelve 1.0 "no se asume déficit". Residual con flag ON: rama DEFICIT con comidas (`NutritionRecoveryEngine.kt:100`); CNS sin nutrición. |
| H17 | ARREGLADO (eliminado) | Grep de sexo, género, mujer, edad y age en `K/domain/auge`: sin multiplicadores. Los 0.85 son otros factores. |
| H27 | PARCIAL | v1→v2 limpia `personalizedRecoveryHours` (`K/domain/auge/AugeAdaptiveCacheMigration.kt:16–22`). v2→v3 conserva τ musculares (comentario l.12; código l.23–32) y no toca `muscleDeltas`. |
| B16 | ARREGLADO en k; unidad sin convertir; sin efecto visible | `AugeTtcEngine.kt:313` y `:369` comparten la familia k = 2/τ. El valor sigue en horas efectivas (l.367–371). `estimatedHoursToRecovery` no tiene lectores (`AugeModels.kt:268`). El test fija la fórmula (`T/domain/auge/AugeTtcRecoveryHoursTest.kt:56–58`). |
| F0 | VERIFICADO | Pines `T/domain/auge/AugeRingCharacterizationTest.kt:63–74` reproducidos a mano: Energía 80/92/97/99, Columna 93/96/99, Pecho 72/80/85/97/99. |
| F warp | CUMPLE | `AugeUtils.kt:149–157` continuo en 12 h y 24 h. |
| F compuerta axial | CUMPLE (anillo y editor) | `AugeRecoveryEngine.kt:1053, 1078`; `K/domain/auge/AugeFatigueEngine.kt:49–53, 752`. |
| F piso espinal solo con axial | PARCIAL | Cumple en el drenaje de sesión (`AugeFatigueEngine.kt:788–807`, condición en l.801). El anillo aplica piso incondicional (`AugeRecoveryEngine.kt:1221–1226`). |
| F Columna blended | CUMPLE (forma) | l.290–298, 1389–1393, 1884–1885. Techo implícito en R-14. |
| F override que ancla | CUMPLE con matiz | El ancla fija el estado (l.513–529). Después se resta 0.35·P (l.680) y se suma Δ (l.682). El valor mostrado no es X si hay molestias pre-entreno. |
| F cold-start | CUMPLE en forma; NO en composición | `K/domain/auge/InitialRecoveryEvidencePolicy.kt:243–258` usa C y τ del motor. La composición es aditiva (l.1240, 1356): R-07. |
| Fuera de lista | | H4/B13 ARREGLADO (l.169–172; `K/screens/workout/WorkoutSessionOverlaysHost.kt:83`). H9 headline pintado (`K/screens/home/HomeRingsSection.kt:121`). H19 PARCIAL: solo escribe τ (VM 818–824), con `totalObservations` +1 (VM 830). H20 ARREGLADO en τ (`AugeAdaptiveEngine.kt:12, 28`). H21 CAMBIÓ-DE-FORMA: warp correcto (`AugeAdaptiveEngine.kt:37–41`), fracción incorrecta (R-01). |

---

## C. Hallazgos nuevos

**R-01 · P0 · matemática; aprendizaje silencioso**
- Evidencia: `K/domain/auge/AugeAdaptiveEngine.kt:31–34, 43, 46`; VM 797–800, 818–824, 830.
- Cita: `val remainingFraction = clamp(actualDepletion / initialDepletion, 0.01, 0.99)` / `val impliedTau = 2.9957 / k`.
- Escenario: el motor decae R = −C·ln(b/100) (l.653–654), así que b(t) = b0^x con x = R(t)/R(0). El aprendiz usa la depleción (100 − b)/(100 − b0), que no es x. Con b0 = 70: x = 0.5 → b = 83.7, fracción 0.544, τ_aprendida/τ = ln0.5/ln0.544 = **1.14**; x = 0.25 → 1.10. Con b0 = 40: x = 0.5 → **1.41**; x = 0.25 → 1.29. Además el denominador real es max(depleción, immediateDrainPct) (l.32; `VM 1108–1115`). immediateDrainPct = 100(1 − e^(−S/C)) (`K/domain/auge/MuscularSessionImpactEngine.kt:229`) es menor que la depleción inicial del motor, 100(1 − e^(−S/0.9C)). Eso añade sesgo en la misma dirección (no cuantificado).
- Efecto: cada calibración con ancla alarga τ de Pecho, Energía o Columna entre 10 y 40 %. Las sesiones siguientes se recuperan más lento. Sin señal visible.
- Arreglo: `val rFrac = ln(actual/100) / ln(predicted/100)`; `k = -ln(rFrac) / effHours`, con C por canal.

**R-02 · P1 · inconsistencia**
- Evidencia: `K/domain/auge/AugeRecoveryEngine.kt:1136–1147, 1168–1172, 1330–1346`; `K/domain/auge/RecoveryBands.kt:94`.
- Cita: `val coef = 0.6 + 0.4 * (articularAvg / 100.0)`.
- Escenario: Pectorales a 90 con articular de hombro media 50 → coef 0.80 → **72** en el global, mientras `perMuscle` (sin blend, l.1330–1346) muestra 90. Con varios pilares afectados, el global queda por debajo de la media de los chips. El texto "Promedio del estado de todos tus músculos hoy" (RecoveryBands.kt:94) no es cierto.
- Efecto: el anillo no se puede reproducir desde la lista por músculo. Si la UI las muestra juntas: HIPÓTESIS NO VERIFICADA.
- Arreglo: mostrar el blend por pilar en `perMuscle` o quitarlo del agregado. Corregir el texto.

**R-03 · P1 · matemática temporal (escalón sin entrenar)**
- Evidencia: `K/domain/auge/AugeRecoveryEngine.kt:356, 364–366, 371`; `AugeClassifiers.kt:37–42, 57–61`; `AugeMuscleCapacityEngine.kt:47–50`.
- Cita: `val chronicWeekly = month / 4.0` / `if (nowMs - oldest < 14L * day) return null`.
- Escenario (Python, misma lógica de ventanas): cuatro sesiones de carga 1000 a −14, −6, −4 y −2 días. El día 0, cuando la primera cumple 14 días, el ACWR pasa de null a 3.0 (aguda 3000 / crónica 1000). Factor 1.12 → Músculos global **60 → 55** (100 − 40·1.12 = 55.2). El factor vuelve a 1.0 el día 3.5, cuando sale la sesión de −4 de la ventana de 7 días. Resultado: 60 → 55 → 60 sin entrenar.
- Segundo caso (Python): sesiones −26 (500), −20 (≈0), −15, −12 y −2 (1000). Al salir la de −26 (día 2), ACWR 1.14 → 1.33, factor 1.06, **60 → 57** durante 3 días.
- Caso a mano: 2 sesiones de 1000 por semana durante 14 días → aguda 2000 / crónica 4000/4 = **2.0** → "Alto riesgo" para un usuario regular.
- Efecto: el anillo sube y baja sin entrenar. Hay falsa zona de riesgo en el primer mes. Los pilares no reciben el factor.
- Arreglo: dividir la crónica por min(4, semanas reales), como en `AugeMuscleCapacityEngine.kt:47–48`. Pasar `nowMs` a `applyMuscularAcwr` (l.371).

**R-04 · P1 · escalón temporal**
- Evidencia: `K/data/repository/AugeRepository.kt:55, 65–66`; VM 201–205.
- Cita: `if (w.date == todayStr) true` / `else (w.manualBatteryAnchorMs ?: 0L) >= eighteenHoursAgo`.
- Escenario (Python): ancla de Energía a 60 con automático 20 en el instante del ancla (brecha 40). A las 18 h, el ancla vale 89.2 y el automático 69.8: **−19.4 puntos sin entrenar**. El mismo mecanismo afecta a anclas de músculo y de columna. El corte es intencional ("una noche", `AugeRepository.kt:55`), pero el valor no decae: cambia de fuente.
- Efecto: escalón visible en Home (recalcula cada 5 min).
- Arreglo: conservar el offset Δ0·decay(t) sobre el automático en lugar de descartar el ancla.

**R-05 · P1 · matemática (unidades de tiempo)**
- Evidencia: `K/domain/auge/RecoveryBands.kt:145–148`; `AugeRecoveryEngine.kt:666–672`; VM 321–331, 1087–1096; `K/screens/home/RingDetailSheet.kt:107–110`.
- Cita: `val remainingEffective = -kotlin.math.ln(targetFatigue / fatigue) / k`.
- Escenario (Python): músculo a 60 con τ = 48 → el motor devuelve **25 h** (l.670). La vuelta real a 90 son **34.6 h**, por la inversa de la sigmoide. Energía 60 → 70 con τ = 36: `hoursUntilNormal` devuelve **3 h**, real **4.3 h**. Columna cruda con τ = 52: **5 h** frente a **6.2 h**. La UI muestra "Vuelve a Normal en ~N h".
- Efecto: subestimación de 20–27 %. `warpedHoursAlready` nunca se pasa (VM 323–330 llama con dos argumentos).
- Arreglo: invertir la curva real con `getSigmoidalHours` (músculo) o con `getSpinalRecoveryHours` (columna), y con `R = −C·ln(b/100)` (energía).

**R-06 · P1 · inconsistencia (preview frente a Home)**
- Evidencia: `K/domain/auge/AugeRecoveryEngine.kt:1859, 1884–1885`; `K/domain/auge/MuscularSessionImpactEngine.kt:229, 233`; `K/screens/workout/WorkoutFinishHost.kt:220–222, 654` (el finish pinta `perMuscle` del preview).
- Cita: `val score = ((base?.recoveryScore ?: 100) - impact.immediateDrainPct.roundToInt())`.
- Escenario (Python), b_base = 70, x = S/C. Overlay frente a Home: x = 0.1 → 60.5 / 62.6; x = 0.3 → **44.1 / 50.2**; x = 0.5 → **30.7 / 40.2**. Con b_base = 100: x = 0.3 → 74.1 / 71.7; x = 0.5 → 60.7 / 57.4. La Columna del preview usa las guardias del overlay (l.1884–1885).
- Efecto: el finish muestra un número y, al cerrar, Home muestra otro para el mismo instante (salto de hasta ~10 puntos). Los tests no lo cubren: `T/domain/auge/AugePostSessionPreviewImmediateTest.kt:26` solo asegura ≥ 0.
- Arreglo: derivar el overlay de la misma ley: `100*exp(-(-90*ln(base/100) + 100*stressUnits/capacityAtCompletion)/90)`.

**R-07 · P1 · matemática (composición de la evidencia inicial)**
- Evidencia: `K/domain/auge/AugeRecoveryEngine.kt:1239–1240, 1315–1327, 1344–1345, 1356`; `K/data/models/InitialRecoveryEvidence.kt:287` (estimatorVersion = 2).
- Cita: `if (enabled && initial != null) (real - (100 - initial)).coerceIn(minimum, 100) else real`.
- Escenario: la fatiga inicial y la real se suman en puntos de batería, no en R. Error = −(100 − b_real)(100 − b_inicial)/100. Con (70, 70): **40** frente a **49** de la ley del motor. Con (60, 60): 20 (suelo 22) frente a 36.
- Efecto: un usuario con evidencia del wizard ve entre 9 y 16 puntos menos que el mismo estado producido por sesiones.
- Arreglo: `score = 100*exp(-(rReal + rInit)/C)` con `rInit = -C*ln(init/100)`.

**R-08 · P2 (latente; P0 si se activa) · robustez**
- Evidencia: `K/domain/auge/AugeRecoveryEngine.kt:656–664, 915–920`. `VM 644` y `K/screens/workout/WorkoutSessionOverlaysHost.kt:81` preservan `doms ?: 1`. `K/screens/workout/WorkoutFeedbackModels.kt:338` escribe `MuscleFeedbackEntry.doms`, no el wellbeing.
- Cita: `battery = min(battery, domsCap)`.
- Estado: ningún flujo de K escribe `DailyWellbeingLog.doms` con valor de usuario. Si se reactiva, el tope también afecta a músculos sin carga, porque la guarda de l.656 solo exime DOMS ≤ 2. Con DOMS 5 y sin entrenar: Músculos global **25** (pilares a 22; decel(22) = 25.7). Columna = round(0.5·100 + 0.25·100 + 0.25·22) = **81**. Con la sentadilla de F0 (espinal 93): SPF = 22 → ×1.456 → espinal 90.0 → Columna **75.5**.
- Arreglo: aplicar el tope solo si acc > 0.1 y no a guardias sin carga.

**R-09 · P2 · causalidad**
- Evidencia: `K/domain/auge/AugeRecoveryEngine.kt:994, 1002–1015`.
- Cita: `val historyUpToLog = history.filter { logDateMs(it) <= logTime }`.
- Escenario: (a) incluye el propio log a 0 h, así que su drenaje de erectores baja la guardia que multiplica su drenaje espinal. Con SPF 70, multiplicador 1 + 0.3²·0.75 = 1.0675. (b) El wellbeing de hoy y las anclas V2 se evalúan en cada `logTime`. Ancla de erectores a 40 con dorsales y trapecio a 100 → SPF 73 → **×1.055** en todas las sesiones de 10 días.
- Efecto: el estado de hoy reescribe el drenaje espinal pasado. Magnitud pequeña salvo guardias muy cargadas.
- Arreglo: `<` en l.994, y `wellbeing = null` para logs anteriores al ancla de las guardias.

**R-10 · P2 · robustez (fechas futuras)**
- Evidencia: `K/domain/auge/AugeRecoveryEngine.kt:800, 804, 957, 991`, frente a 1293–1296.
- Cita: `history.filter { logDateMs(it) > last10Days }`.
- Escenario: sesión dura fechada +2 días → Energía **80** (como si fuera de hace 0 h; pin `T/.../AugeRingCharacterizationTest.kt:63`). Pecho no la cuenta, porque `getPerMuscleBatteries` filtra a ≤ now.
- Arreglo: añadir `&& logDateMs(it) <= now` en l.800 y l.957.

**R-11 · P2 · determinismo**
- Evidencia: `AugeRecoveryEngine.kt:371, 387, 1573, 1709`; `K/domain/auge/AugeTtcEngine.kt:211`; `K/domain/auge/NutritionRecoveryEngine.kt:35`. `K/domain/auge/AugeClock.kt` solo tiene `System.currentTimeMillis()`, sin inyección.
- Cita: `val acwr = muscularAcwrFor(history) ?: return muscularAvg`.
- Efecto: con el mismo `nowMs`, el resultado depende del reloj en ACWR y articular. El preview "mañana" (l.1888–1924) usa el ACWR y la articular de ahora (la articular pasa de 70 a 71.5 en 24 h: 1–2 puntos). Los tests no pueden fijar el ACWR.
- Arreglo: pasar `nowMs` a `applyMuscularAcwr` y a `calculateArticularBatteries`.

**R-12 · P2 · código muerto y campos sin escritor o lector**
- Sin llamadores de producción: `calculateSessionDrainRankings` (l.1600), `calculateExerciseDrainRankings` (l.1629), `calculatePersonalRecoveryStats` (l.1704; reloj directo en l.1709; heurística l.1753–1755), `calculateSleepRecommendations` (l.1531), `pillarMuscles` (l.1592; solo tests en `T/.../AugeRecoveryEngineRingTest.kt:305, 331, 361, 378`).
- Sin escritor: `muscleDeltas`, `cnsLearningDelta`, `spinalLearningDelta`, `cnsDrainMultiplier`, `spinalDrainMultiplier`, `muscleDrainMultipliers`. Solo la migración los resetea (`AugeAdaptiveCacheMigration.kt:18, 25–30`). `updateSystemLearningDeltas` no tiene llamadores (`AugeAdaptiveEngine.kt:91`).
- Sin lector: `ArticularBatteryState.estimatedHoursToRecovery` (`K/data/models/AugeModels.kt:268`; escritor `AugeTtcEngine.kt:375`). `automaticBatteryAtAnchor` (`AugeModels.kt:117`; escrito en VM 630, nunca leído).
- Redundancia: `manualBatteryAnchorMs` tiene dos ramas idénticas (l.122–128).
- Efecto: el motor lee Δ muscular que nunca cambia. El aprendizaje vivo es solo τ. Los caches heredados conservan `muscleDeltas` porque v2→v3 no los borra (`AugeAdaptiveCacheMigration.kt:23–32`). HIPÓTESIS NO VERIFICADA sobre datos existentes.
- Arreglo: borrar o marcar. Quitar las lecturas de Δ (l.675–682, 1203–1207) o documentarlas.

**R-13 · P2 · huecos de test**
- Sin pin del tope DOMS. Todos los `doms` de tests valen 1 o 2.
- Sin pin de Δ muscular ni de Δ de Energía y Columna en el global.
- Sin test temporal del ACWR (`T/.../AugeUnitsConsistencyTest.kt:23` cubre el legado).
- Overlay frente a Home no probado (`AugePostSessionPreviewImmediateTest.kt:26` solo asegura ≥ 0).
- `hoursUntilNormal` solo verifica el signo (`T/.../AugeRingsGuideAndMonitorTest.kt:32–34`).
- `T/.../AugeTtcRecoveryHoursTest.kt:128–130`: aserción condicional `if (… > 0 && … > 0)`; puede pasar vacía.
- `T/.../AugeRecoveryEngineManualOverrideTest.kt:119`: usa 0.6/0.4. El motor usa 0.5/0.25/0.25 (l.296). Pasa solo porque articular y guardia valen 100.
- Sin test del multiplicador espinal ni del blend de Columna con guardia < 100 (`AugeRecoveryEngineRingTest.kt:325–326` solo afirma < 97 y > 80).

**R-14 · P2 · semántica de Columna**
- Evidencia: `K/domain/auge/AugeRecoveryEngine.kt:296–297, 310, 321–327`; `AugeTtcEngine.kt:360`.
- Cita: `val blended = (spinal * 0.50) + (articularFloor * 0.25) + (muscleGuard * 0.25)`.
- Efecto: techo implícito de 50 + I, con I = 0.25·A + 0.25·G. Con A = G = 80, la Columna no pasa de 90 aunque la espalda esté entera. El piso articular es la media de las 2 articulares más bajas de las 7, incluidas rodilla y tobillo. La batería articular es lineal (1 − acc/80) y el resto es exponencial.
- Arreglo: definir qué articulares forman el piso de Columna y documentar el techo.

**R-15 · P2 · pregunta de calibración**
- Evidencia: `K/domain/auge/AugeFatigueEngine.kt:92–108`; `AugeRecoveryEngine.kt:795, 949`.
- Escala, ENT con C base: músculo 100·10/2600 = **0.385** por punto de drenaje; energía 100/297.6 = **0.336**; columna 100/82.8 = **1.208**. La columna responde 3.6 veces más que la energía al mismo %. Puede ser intencionado (la documentación lo llama "tanque 4000 escalado ×0.02"). No lo verifiqué.

**R-16 · P2 · divergencia tras aprender**
- Evidencia: `K/domain/auge/InitialRecoveryEvidencePolicy.kt:252–258, 264–269`, frente a `AugeRecoveryEngine.kt:482–487, 779, 936`.
- Efecto: la evidencia v2 decae con τ fijas (36, 52, 48 y el perfil). El motor usa τ aprendidas. Tras calibrar, el componente inicial y el real decaen con curvas distintas.

**R-17 · P2 · redondeo y taxonomías**
- Evidencia: truncado `toInt()` en `AugeRecoveryEngine.kt:693, 1172, 1220`, frente a `roundToInt()` en `:297, 1147`. Estados de pilar 95/85/40 (`:684–689`), frente a bandas 85/70/50/35 (`RecoveryBands.kt:11–14`).
- Efecto: Columna = min(espinal truncado, blend redondeado), con ±1 de error. Un 38 es EXHAUSTED en el pilar y LOW en la banda.

**R-18 · P2 · texto de causas fuera del anillo**
- Evidencia: `AugeRecoveryEngine.kt:1378, 1414–1415`; los 13 pilares en `:62–66`; el set completo en `:72–73`.
- Efecto: las causas de Músculos toman los 2 peores de `perMuscle`, que incluye Cuello o Psoas. Esos no entran en el global, así que el texto puede nombrar un músculo que no pesa en el anillo.

---

## D. Lo que está sano y no debe tocarse

- Historial vacío → 100 en los tres anillos (`T/.../AugeRecoveryEngineRingTest.kt:46–51`; motor l.652–656).
- Fechas no parseables (`logDateMs` ≤ 0) excluidas de todas las ventanas (l.194, 339, 348, 390, 1290–1295).
- Trabajo previo al ancla no cuenta (`T/.../AugeRecoveryEngineManualOverrideTest.kt:40–94`; l.526, 955).
- Preview muscular global = Home (`AugeRecoveryEngineRingTest.kt:99`).
- Pins de F0 reproducibles (ver B). Overnight 40 → ~51, con C = 2600 y τ = 48 (`T/.../OvernightRecoverySensationTest.kt:48–49`).
- Inversión de Columna consistente (`AugeRecoveryEngine.kt:304–315`). Para d ≤ 2I devuelve d. Para d > 2I, raw = 2(d − I) y el blend recupera d (verificado algebraicamente).
- Ley del ancla igual a la del motor: B(t) = 100·(B0/100)^d (`InitialRecoveryEvidencePolicy.kt:399–400` frente a `AugeRecoveryEngine.kt:523–529`).
- Batería V2 independiente de la capacidad actual (`equivalent = decayed·C/C_log`, l.551–557).
- Soft cap escala-invariante (`K/domain/auge/AugeUtils.kt:163–172`).
- **Apilado (Q4).** Suma lineal de residuos y exponencial final (l.646–654). Daily 5 desde 70: músculo **57.4**, energía 66.2, columna 67.1. Doce horas ×2: 54.8; ×3: 46.3; ×5: 42.2. Plausible y coherente con el modelo.
- **Monotonía sin ACWR ni cortes.** Cada decaimiento reduce el residuo (l.646–654, 797, 880, 1087–1096; `AugeTtcEngine.kt:318–329`). Las molestias decaen (l.196, `AugeTtcEngine.kt:228`). El feedback sale de ventana a 14 días (l.493) y la batería sube.
- Aprendizaje: una observación por gesto (VM 830). Mínimo 8 h (`AugeAdaptiveEngine.kt:12, 28`). ±25 % por observación (l.50, 52–56). Clamps de τ (l.47, 80, 150–151).
- Migración v1→v2 limpia τ, Δ y multiplicadores (`AugeAdaptiveCacheMigration.kt:16–22`).
- Sueño fuera de τ (`AugeRecoveryEngine.kt:169–172`).

---

## E. Preguntas abiertas y no verificado

1. Intención del tope DOMS (global o por músculo). Si hay wellbeing heredados con DOMS ≥ 3: HIPÓTESIS NO VERIFICADA (requiere datos del dispositivo).
2. Si la UI muestra la lista por músculo junto al anillo global: no verificado. Sí se pinta `perMuscle` en el finish (`WorkoutFinishHost.kt:220–222, 654`).
3. Logs importados con fecha solo-día: HIPÓTESIS NO VERIFICADA. No leí la importación. El finish guarda ISO (`WorkoutFinishController.kt:366`).
4. Cobertura de `muscularImpactV2.perMuscle` sobre los músculos involucrados. Leí `MuscularSessionImpactEngine.kt` solo en 180–255. Si falta, un mismo log mezcla V2 y legado (`AugeRecoveryEngine.kt:547–549` frente a 571).
5. Intención de la escala por tanque (R-15), de la resta por molestias sobre el ancla (l.680) y de la autoinclusión (R-09).
6. Piso articular con cualquier articulación (R-14).
7. R-01 no se probó con un caso real. El sesgo es la ley del motor aplicada a la fracción del aprendiz. El denominador real depende de immediateDrainPct.
8. Regla 1: no ejecuté tests ni Gradle. Los pins se verificaron por lectura y aritmética.

---

## F. Cobertura real

| Archivo | Líneas leídas / total | Estado |
|---|---|---|
| `K/domain/auge/AugeRecoveryEngine.kt` | 1–1935 / 1935 | completo |
| `K/domain/auge/RecoveryBands.kt` | 1–152 / 152 | completo |
| `K/domain/auge/AugeUtils.kt` | 1–173 / 173 | completo |
| `K/domain/auge/AugeMuscleNormalization.kt` | 1–197 / 197 | completo |
| `K/data/models/AugeModels.kt` | 1–489 / 489 | completo |
| `T/domain/auge/AugeRingCharacterizationTest.kt` | 1–160 / 160 | completo |
| `T/domain/auge/AugeRecoveryEngineRingTest.kt` | 1–407 / 407 | completo |
| `T/domain/auge/OvernightRecoverySensationTest.kt` | 1–57 / 57 | completo |
| `T/domain/auge/AugeTtcRecoveryHoursTest.kt` | 1–136 / 136 | completo |
| `T/domain/auge/AugeRecoveryEngineManualOverrideTest.kt` | 1–175 / 175 | completo (complementario) |
| `K/domain/auge/AugeMuscleCapacityEngine.kt` | 1–75 / 75 | completo (auxiliar) |
| `K/domain/auge/InitialRecoveryEvidencePolicy.kt` | 1–461 / 461 | completo (auxiliar) |
| `K/data/models/AugeAdaptiveModels.kt` | 1–39 / 39 | completo |
| `K/domain/auge/AugeAdaptiveCacheMigration.kt` | 1–38 / 38 | completo |
| `K/domain/auge/AugeClock.kt` | completo | auxiliar |
| `K/domain/auge/AugeAdaptiveEngine.kt` | 1–110, 123–152 | parcial |
| `K/domain/auge/AugeFatigueEngine.kt` | 1–130, 190–310, 405–555, 750–810 | parcial |
| `K/domain/auge/AugeTtcEngine.kt` | 1–130, 200–390 | parcial |
| `K/domain/auge/AugeClassifiers.kt` | 1–90 | parcial |
| `K/domain/auge/MuscularSessionImpactEngine.kt` | 180–255 | parcial |
| `K/domain/auge/NutritionRecoveryEngine.kt` | 20–60 y grep | parcial |
| `K/data/models/ExerciseMuscleInfo.kt` | 225–265 | parcial |
| `K/screens/auge/AugeViewModel.kt` | 188–374, 596–660, 745–840, 862–900, 944–1003, 1080–1139 | parcial |
| `K/data/repository/AugeRepository.kt` | 46–72 | parcial |
| `K/screens/workout/WorkoutFinishController.kt` | 218–237, 346–385 | parcial |
| `K/screens/workout/WorkoutFinishHost.kt` | 220–222, 654 | parcial |
| `K/data/onboarding/SetupRingsPreviewCalculator.kt` | 153–215 | parcial |
| `K/data/repository/ProgramRepository.kt` | 1160–1200 | parcial |

**Sin leer:** el resto de `AugeFatigueEngine.kt` (131–189, 311–404, 556–749, 811–fin); `AugeTtcEngine.kt` (131–199, 391–fin); `AugeClassifiers.kt` (91–fin); `AugeAdaptiveEngine.kt` (111–122); `AugeViewModel.kt` (1–187, 375–595, 661–744, 841–861, 901–943, 1004–1079, 1140–fin); `MuscularSessionImpactEngine.kt` (1–179, 256–fin); `NutritionRecoveryEngine.kt` (61–fin); `ExerciseMuscleInfo.kt` (1–224, 266–fin); `AugeRepository.kt` (1–45, 73–fin). Los usos de estos archivos se verificaron por grep.

---

## G. Observaciones de sueño (informativas)

- `sleepMult` queda en 1.0 (`AugeRecoveryEngine.kt:169–172`). La readiness conserva el `sleepHours` guardado (`WorkoutSessionOverlaysHost.kt:83`). H4 y B13 quedan cerrados en el motor.
- Residuo informativo: `calculateWeightedSleepHours` (l.149–162) alimenta textos de causa de Energía (l.1421, 1426–1428) y la confianza (l.1408, +12 con ≥ 3 registros). Es texto causal sin efecto en la batería.
- `calculateSleepRecommendations` (l.1531–1562) no tiene llamadores (= H9).
