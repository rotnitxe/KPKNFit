# Parity: Wizard commit `ac3ff1c88` — Nutrición y Rings (Android → iOS / Backend)

**Fecha:** 2026-09-24
**Commit auditado:** `ac3ff1c88` "feat(onboarding): wizard nativo unificado"
**Alcance:** solo parity de puntos equivalentes (sin puertos UI completos, sin commits, sin builds/adb Android). No se modificó ningún archivo de `android-native/`.

**Revisión (misma fecha, pass «M10»):** corrección del defecto de presencia en `NutritionPlan`/`deriveMacroGoals` de iOS (antes: `plan.calorieTarget > 0 ? value : nil` borraba los 0 explícitos y, si todo el plan era 0/nil, caía a los ajustes) + consumidor de alertas que ignoraba el plan; oráculos nuevos iOS (sin ejecutar: sin toolchain Swift en Windows) y oráculo backend «meta 0 = neutra» (ejecutado, 15 passed). Backend sin bug de división por meta. Cardio sigue N/A en iOS/backend; resolución por fecha y modo solo-seguimiento siguen sin portar (§3).

**Revisión 2 (árbol ACTUAL, 2026-09-25, pass «M10»):** paridad de los cambios M12 de Android sobre RINGS (check-in parcial, `capturedFields`, prioridad manual por canal, cobertura `NO_DATA`), **sin limitarse al commit `ac3ff1c88`** y **sin editar nada en `android-native/`**. Ver **§8**: 4 divergencias reales corregidas en iOS, backend sin cambio de código (3 oráculos nuevos ejecutados) y límites no portados con evidencia.

**Revisión 3 (árbol ACTUAL, 2026-09-25, pass «M10»):** cierres Android de gasto por calendario (`MISSING`⇒`NotEstimable`, `BODYWEIGHT_ONLY`+peso conocido), `Program.optionalSessionConfirmations` (unión por sesión+fecha, `variantKey` null⇒A), presupuesto restante desde mañana / hoy-pasado fijo e inventario finito de material. **Sin ediciones en Android, sin cambios en backend (18 tests re-ejecutados), sin puertos iOS nuevos.** Ver **§9**.

Este documento separa (a) mecanismos equivalentes portados, (b) no aplicables / no validados, y (c) limitaciones con evidencia. Las fuentes autoritativas son el código Android y los esquemas Room; cuando la documentación difiere, manda el código.

---

## 1. Referencia Android (commit `ac3ff1c88`) — sin ediciones

| Cambio | Evidencia (líneas) |
|---|---|
| `CardioCalorieEngine`: `kcal = MET * 3.5 * kg / 200 * minutes` (corrige el error `/60` de multiplicar por horas) | `android-native/.../domain/calculations/CardioCalorieEngine.kt:41,63` — oráculo: 8 MET · 70 kg · 1800 s → **294.0 kcal** (`CardioCalorieEngineTest.kt`) |
| `TrainingEnergyEngine`: excluye set planificado con cardio: `.filter { ex -> ex.cardioDetails == null }` | `domain/energy/TrainingEnergyEngine.kt:673` |
| `MacroGoals` todos los campos `Int?`; `macroGoalsOf` **sin fallback a ajustes**; `deriveMacroGoals` all-null sin evidencia; `GoalsAbsence`/`DayGoalsResult` | `domain/nutrition/MacroCalculator.kt:373-388, 391-408, 454-468, 528-529` |
| `NutritionRecoveryEngine`: neutro 1.0 con ventana vacía («Sin comidas…») o sin metas («Sin metas…»); guarda por campo `goal > 0` | `domain/auge/NutritionRecoveryEngine.kt:47-76, 90-91` |
| `AugeRecoveryEngine.getNutritionMultiplier` resuelve metas por fecha vía `resolveDayGoalsByDate` (7 días) | `domain/auge/AugeRecoveryEngine.kt:1516-1536` |
| `InitialRecoveryEvidencePolicy` (TAU 72 h) + metadatos de fuente en `GlobalBatteries` (`sourceLabel/sourceConfidence/sourceId/sourceAnchorMs/sourceExpiresAtMs`) | núcleo de `AugeRecoveryEngine.kt` (`initialRecoveryContribution`) |

## 2. iOS — mecanismos equivalentes portados (cambios reales)

1. **`KPKNFit/Data/Models/NutritionModels.swift`** — `MacroGoals` pasa a `Int?` con defaults `nil` (se eliminan los literales 2500/150/250/70 y los `?? 2500/...` de `deriveMacroGoals`). `NutritionPlan.calorieTarget/proteinGoal/carbGoal/fatGoal` pasan a `Int?` **presence-aware**: la síntesis de `Codable` usa `decodeIfPresent`, así que una clave ausente decodifica `nil` y un `0` explícito se conserva `0` (ausencia ≠ 0); el `init` ya no fabrica `0` por defecto. `deriveMacroGoals` es gemelo de `macroGoalsOf` + `dayGoalForecastOf`: con plan activo mandan sus campos **tal cual** (0 y `nil` incluidos, sin filtro `> 0` y **sin fallback a ajustes** aunque el plan esté vacío); sin plan, solo objetivos explícitos de ajustes; sin evidencia, macros `nil` (nunca 2500/150/250/70). Firmas: `NutritionPlan.init(... calorieTarget: Int? = nil, proteinGoal: Int? = nil, carbGoal: Int? = nil, fatGoal: Int? = nil ...)`, `public func deriveMacroGoals(settings: Settings, activePlan: NutritionPlan? = nil) -> MacroGoals` (firma intacta).
2. **`KPKNFit/Domain/Auge/NutritionRecoveryEngine.swift`** — ventana vacía → neutro `1.0 .maintenance` con factor «Sin comidas…» (se elimina la fabricación por `calorieGoalObjective`: 1.25 déficit / 0.95 superávit); sin metas (nil o ≤ 0 en cal y proteína) → neutro `1.0` «Sin metas…»; ratios con guarda por campo `goal > 0` — mismo contrato numérico que Android.
3. **`KPKNFit/Presentation/Screens/HomeViewModel.swift`** — `dailyCalorieGoal/dailyProteinGoal/dailyCarbGoal/dailyFatGoal` pasan a `@Published Int? = nil` y se asignan desde `MacroGoals` sin fabricar defaults. Consumidores mínimos ajustados para compilar y no renderizar valores inventados: `HomeScreen.swift` (`MiniNutritionCard` muestra `–` y progreso 0 sin meta) y `Presentation/Components/HomeCardsSection.swift` (`MacroProgressBars` oculta filas sin meta).
4. **`KPKNFit/Services/Nutrition/NutritionNotificationManager.swift`** — `sendMacroDeficitAlert` guarda cada campo `if let goal = goals.x, goal > 0` (gemelo de `macroDeficitAlerts`): sin meta no se manda «te faltan macros». Además `checkAndSendMacroAlert` ahora llama `deriveMacroGoals(settings:activePlan: NutritionRepository.shared.activeNutritionPlan)`, de modo que con plan activo la alerta usa el plan y no objetivos de ajustes obsoletos (antes ignoraba el plan por completo).

**Pruebas iOS agregadas:** `KPKNFitTests/KPKNFitTests.swift` — 5 oráculos portados 1:1 de `NutritionRecoveryEngineTest.kt` (vacío→1.0; sin metas→1.0; plan de ceros→1.0, ahora además afirma `goals == 0`; déficit real 200 vs 2000→DEFICIT >1.0; solo meta calórica con proteína→1.0 sin penalización) **+ 4 oráculos de presencia** escritos en este paso:

| Oráculo | Test |
|---|---|
| Plan activo 2000 kcal / proteína 0 / grasa 0 vs ajustes 999 → `2000 / 0 / 0` (los 0 no se borran, los ajustes no sustituyen al plan) | `testActivePlanWinsOverSettingsEvenAtZero` |
| Plan activo con campos todos `nil` → macros `nil` (sin fallback a ajustes 999); sin plan y sin ajustes → todo `nil` | `testActivePlanWithoutMacroFieldsDoesNotFallBackToSettings` |
| Decodificar sin la clave → `nil`; con `"calorieTarget":0` → `0` (ausencia ≠ 0) | `testPlanMacroFieldMissingDecodesNilAndExplicitZeroDecodesZero` |
| Meta calórica 0 → nunca divisor (ratio queda 1.0, sin crash/NaN); proteína 100 sí se evalúa → mantenimiento 1.0 | `testZeroCalorieGoalIsNotUsedAsDivisor` |

> ⚠️ **No ejecutables en esta máquina:** Windows no tiene `xcodebuild` ni toolchain Swift (`Get-Command swiftc/xcodebuild` → ausentes); las pruebas iOS están escritas pero **no ejecutadas**. Necesitan `xcodebuild test` en macOS para validarse.

## 3. iOS — no aplicable / no implementado (limitaciones con evidencia)

| Contraparte Android | Estado iOS | Evidencia |
|---|---|---|
| `CardioCalorieEngine` (MET-minutos) | **NOT IMPLEMENTED** | No existe ningún modelo de cardio en iOS: `grep CardioDetails` en `ios-native/` → 0 coincidencias. No hay `CardioTypes`/`CardioIntensity` en ningún Model. No aplica portar la fórmula. |
| `TrainingEnergyEngine` filtro cardio en sets planificados | **NOT APPLICABLE** | Sin concepto de cardio en iOS, no hay sets con `cardioDetails` que filtrar. |
| `resolveDayGoalsByDate` / `NutritionGoalResolver` / `DailyGoalSnapshot` / `PlanDayTarget` / `GoalsAbsence` / `TrackingOnly` / `nutritionTrackingOnly` | **NOT IMPLEMENTED** | `grep -i 'nutritionTrackingOnly|TrackingOnly|DailyGoalSnapshot|NutritionGoalResolver|PlanDayTarget' ios-native/` → 0 coincidencias. La resolución por fecha con snapshots históricos no se portó; el equivalente parcial es «neutro cuando no hay metas» en `deriveMacroGoals` + `NutritionRecoveryEngine`. |
| `InitialRecoveryEvidencePolicy` + metadatos de fuente en `GlobalBatteries` | **PARCIAL** | `AugeRecoveryEngine.initialRecoveryContribution` (≈ líneas 828-859) implementa el núcleo (TAU 72 h, guarda de solapamiento), pero `GlobalBatteries` (`AugeModels.swift:290-303`, Codable con `init` custom) **no tiene** `sourceLabel/sourceConfidence/sourceId/sourceAnchorMs/sourceExpiresAtMs`. Añadirlos requiere `decodeIfPresent` para datos persistidos. |
| `AugeRecoveryEngine.getNutritionMultiplier` con `goalsByDate` | **PARCIAL** | iOS (línea 1189) usa `deriveMacroGoals(settings:activePlan:)` (previsión de hoy) en lugar de la resolución por fecha de 7 días; firma intacta, compila con los nuevos `MacroGoals`. |

**Divergencias conocidas y deliberadas (no reclaman paridad total):**

| Punto | Android | iOS | Efecto |
|---|---|---|---|
| Presencia en `NutritionPlan` | `calorieTarget: Int = 0` (`data/models/NutritionModels.kt:164-169`) — una clave ausente decodifica `0`, indistinguible de un 0 explícito | `Int?` con `decodeIfPresent` — ausente → `nil`, `0` explícito → `0` | iOS es estrictamente más preciso: un plan sin campos macro oculta el anillo (ausencia) en vez de mostrar meta 0. En `NutritionRecoveryEngine` ambos son neutros (guardas `goal > 0`). |
| Metas micro sin evidencia macro | `deriveMacroGoals` devuelve `MacroGoals()` (todo `nil`, micros incluidos) | sigue exponiendo `fiberGoal/sugarLimit/sodium/potassium/hydration` de ajustes explícitos | **Inerte**: grep en `ios-native/` → ningún consumidor lee esos campos de `MacroGoals` (solo el propio modelo). |

## 4. Backend — cambios reales (`backend/engines/recovery_engine.py`)

Port legacy de `recoveryService.ts`; no contiene `CardioCalorieEngine` ni un módulo `NutritionRecoveryEngine` como tal.

1. **`calculate_muscle_battery`** (bloque Nutrition): se elimina la inicialización `status = settings.calorieGoalObjective` (fabricación sin datos) y el mult por objetivo declarado. Ahora solo clasifica si hay `recent_nut` **y** `settings.dailyCalorieGoal`; sin ese binomio el mult queda neutro.
2. **`calculate_daily_readiness`**: se elimina el bloque incondicional `if settings.calorieGoalObjective == "deficit": mult *= 1.3` (el objetivo declarado no es evidencia; la firma no recibe nutrición). Se deja nota de paridad en el código.
3. **`calculate_global_batteries`**: `nut_status` ya no parte del objetivo; solo se clasifica con logs recientes **y** `dailyCalorieGoal`; sin ellos, neutro (mantenimiento → sin cambio de half-life).

**Pruebas oráculo nuevas:** `backend/tests/test_recovery_nutrition_absence.py` (9 tests): objetivo=deficit sin logs → idéntico al neutro (igualdad exacta); logs sin meta → idéntico; **meta 0 explícita → idéntica al neutro y nunca usada como divisor** (`test_explicit_zero_goal_is_neutral`, añadido en este paso); déficit real (avg 100 vs meta 1000) → batería muscular **estrictamente menor** (oráculo empírico 89→88 con sesión 6 h atrás, 5×RPE10@90 kg); superávit real (avg 1300 vs 1000) → **mayor** (89→90); global batteries neutro en ausencia; `calculate_daily_readiness` sin modulación por objetivo.

4. **Verificación «goal 0 = división»:** no había bug que corregir en backend. `recovery_engine.py` solo clasifica bajo la guarda de truthiness `if recent_nut and settings.dailyCalorieGoal:` (líneas 174 y 521) y compara `avg < goal * 0.9` / `avg > goal * 1.1` (multiplicación, nunca división por la meta); con `dailyCalorieGoal=0.0` la rama ni se entra (falsy) → neutro. Tampoco hay meta de proteína ni ningún otro objetivo numérico en los motores. Comando real ejecutado:

```
Select-String -Path "engines\*.py","models\*.py" -Pattern "goal"
# → solo: 2 guardas truthiness (174, 521), 4 multiplicaciones *0.9/*1.1 (177,179,523,525),
#   2 comentarios y los Optional[float] de models/common.py:303-307 (None por defecto).
# 0 divisiones por una meta → sin corrección de código, solo el test oráculo nuevo.
```

### Comandos exactos ejecutados

```
# backend (dir: C:\Users\valen\Documents\KPKNFit\backend)
python --version                                                          # Python 3.14.3
python -m pytest tests/test_recovery_nutrition_absence.py -q              # 9 passed in 0.14s
python -m pytest tests/test_recovery_nutrition_absence.py tests/test_initial_recovery_evidence.py tests/test_auge_muscular_session_impact.py -q   # 15 passed in 0.31s (14 preexistentes + 1 nuevo)
```
- `python -m pytest -q` (suite completa en `backend/`) falla en recolección por **`test_exercises_catalog_v2.py` preexistente**: importa `backend.exercises_catalog_v2` (paquete) y solo recolecta desde la raíz del repo; no es regresión de este cambio.

## 5. Backend — no aplicable / limitaciones

| Contraparte Android | Estado Backend | Evidencia |
|---|---|---|
| `CardioCalorieEngine` MET-minutos | **NOT IMPLEMENTED** | No existe cálculo MET en `backend/engines/` (recovery_engine.py no tiene cardio). |
| `NutritionRecoveryEngine` contrato actual (neutral-when-no-data, `goalsByDate`) | **PARCIAL (contrato, no AST)** | `recovery_engine.py` es un port legacy independiente; este cambio alinea el *contrato* (neutro sin datos/meta, guardas por campo) sin reproducir el módulo Android 1:1. |
| `_initial_recovery_contribution` | **PARCIAL** | Coincide con el núcleo Android (TAU 72 h, guarda de solapamiento, `estimated`); faltan overrides por músculo, labels y metadatos de fuente, y el `combine` no aplica el piso fisiológico mínimo. |
| `augeEnableNutritionTracking` default | Ver nota | Backend default `True` (`models/common.py:281`) vs Android gate y iOS (default `False`). Diferencia latente: el backend modula nutrición por defecto. |
| Guardas de meta 0 («goal 0 no division») | **SIN BUG** | No existe división por una meta en backend: solo truthiness `if recent_nut and settings.dailyCalorieGoal:` (`recovery_engine.py:174,521`) y `goal * 0.9/1.1`. `dailyCalorieGoal=0.0` → falsy → neutro (cubierto por `test_explicit_zero_goal_is_neutral`). |

## 6. Fuera de alcance — evaluación para el lead

El wizard introduce distribución/calendario nuevos que **no** se portan aquí (otro flujo lo atiende). Contrato documentado para quien lo implemente:

- **`NutritionDayDistribution.kt`** y afines: **«Reparto T_i = B + alfa·(E_i − media(E))»** — presupuesto semanal exacto, `alfa` común, reparto uniforme provisional cuando falta estimación. Cambia la composición real de las sesiones (no solo etiquetas).
- **Riesgos de contrato:** cualquier puerto iOS/backend que reparta volumen por día debe validar contra los tests unitarios Android (presupuesto exacto por semana, `alfa` único, uniforme provisional), y revisar el materializador de planes (`PlanMaterializer`) cuando se apliquen prioridades musculares como solo-orden (sin alterar volumen/series/frecuencia).

## 7. Resumen ejecutivo

- **Portados (equivalentes):** iOS `MacroGoals`/`deriveMacroGoals` sin defaults fabricados, `NutritionPlan` con campos de macro presence-aware (`Int?`, ausente ≠ 0) y sin fallback a ajustes con plan activo (0 y `nil` del plan mandan), `NutritionRecoveryEngine` neutro sin datos/meta y sin dividir por metas ≤ 0, alertas de macros con guarda por campo **y con el plan activo**, Home sin metas inventadas; backend `recovery_engine.py` sin fabricación déficit/superávit desde el objetivo y neutro con meta 0 (sin divisiones por meta).
- **No aplicables:** cardio (MET) y filtro de cardio en `TrainingEnergyEngine` — iOS no tiene modelo de cardio, backend no tiene cardio.
- **No implementados / parciales (requieren decisión):** resolución de metas por fecha con snapshots (iOS no tiene `DailyGoalSnapshot`/`NutritionGoalResolver`/`TrackingOnly`), metadatos de fuente en `GlobalBatteries` (iOS/backend), piso fisiológico en `combine` (backend), default de `augeEnableNutritionTracking` (backend `True` vs iOS `False`).
- **Pendiente de validación:** pruebas iOS (solo escribibles en Windows; ejecutar `xcodebuild test` en macOS).

---

## 8. Revisión posterior (árbol ACTUAL, 2026-09-25) — M12 RINGS: check-in parcial, prioridad manual y cobertura

Alcance de este pass: los cambios M12 de Android (`SetupRingsMapper`/`SetupPersistence`, `RingsChannelCoverage`, prioridad manual por canal en `AugeRecoveryEngine` y cobertura `NO_DATA` en UI) verificados contra los equivalentes **iOS/backend del árbol actual** (no solo el commit `ac3ff1c88`). **Cero ediciones en `android-native/`, cero cambios de fórmula fisiológica, sin puertos UI nuevos.** No se afirma paridad total.

### 8.1 Verificado equivalente (no requería cambios)

| Punto | iOS | Backend |
|---|---|---|
| Manual por canal = fuente única (sin mezclar con evidencia inicial) | `AugeRecoveryEngine.swift:933-948`: `muscularEstimated = initial.isEstimated && !hasMuscularManual && initial.muscular != nil` con `hasMuscularManual` = `manualMuscularBattery` OR `manualMuscleBatteries` OR `manualMuscleOverridesV2`; `systemEstimated` exige `manualNeuralBattery == nil`; `structureEstimated` exige `manualSpinalBattery == nil`. Espejo de `AugeRecoveryEngine.kt:1235-1244`. | `recovery_engine.py:590-597`: `initial["estimated"] and not has_muscular_manual` — solo el canal muscular, porque `models/common.py:234-243` solo transporta `manualMuscleOverridesV2`. |
| Ausencia ≠ 0 en la evidencia inicial | `initialRecoveryContribution` (`AugeRecoveryEngine.swift:828-859`): sin evidencia / expirada / con solape real → campos `nil` y `isEstimated=false`; jamás un 0. | `_initial_recovery_contribution` (`recovery_engine.py:475-497`): `None` / `estimated=False` (cubierto por `test_initial_recovery_evidence.py`, 4 passed). |
| Check-in parcial no inventa canales | `ReadinessSheetView.swift:296-300` escribe `nil` en canales que el usuario no editó y deja el anillo global muscular en `nil` (no promedia per-muscle hacia el anillo global). Ver límite §8.3 (borra en vez de conservar). | N/A: el backend no recibe check-ins; `DailyWellbeingLog` no tiene campos de anillo global. |

### 8.2 Divergencias reales corregidas (solo iOS; 4 archivos)

1. **`Data/Repository/AugeRepository.swift` — `getActiveWellbeingWithManualOverrides()`**: rango `daysAgo: 2`…hoy (3 días) + solo los 3 anillos + **sin ventana de 18 h** → ahora ayer…hoy, todas las fuentes manuales (incl. `manualMuscleBatteries`/`manualMuscleOverridesV2`) y ancla de ayer dentro de las últimas 18 h, como `AugeRepository.kt:50-68`. Nueva API testeable: `public static func isActiveManualOverride(_ log: DailyWellbeingLog, nowMs: Int64, today: String) -> Bool`. Efecto: un ajuste caducado ya no sigue «mandando» y un check-in solo per-muscle ya no se ignora.
2. **`AugeRepository.saveWellbeingLog(log:)`**: reutiliza el id de la fila existente de esa fecha (como Android, que lo exige por `unique(date)`). Antes iOS podía dejar **dos filas para el mismo día** (el esquema iOS no tiene `unique(date)`: `KpknDatabase.swift:366` solo `PRIMARY KEY(id)`), y resoluciones posteriores (hoy/activa) habrían leído una fila arbitraria.
3. **`Domain/Auge/AugeRecoveryEngine.swift` — `manualBatteryAnchorMs(_:)`** (pasa de `private` a `static` para poder testearla): sin ancla explícita devuelve la medianoche del día del registro (como Android `AugeRecoveryEngine.kt:119-129`); antes devolvía `nowMs()`, que re-anclaba en cada evaluación → `hoursSinceAnchor ≈ 0` (ajuste manual congelado, sin decaimiento) y `relevantHistory` filtrado a partir de «ahora».
4. **`Presentation/Screens/Auge/AugeViewModel.swift` — `applyManualBatteries(...)`**: contrato parcial espejo de Android (`AugeViewModel.kt:592-698`). Firmas: antes `neural: Int, spinal: Int` **obligatorios** y `perMuscle: [String: Int]`; ahora `neural: Int? = nil, muscular: Int? = nil, spinal: Int? = nil, perMuscle: [String: Int]? = nil, perMuscleDelta: [String: Int]? = nil, ...` (resto igual). Corrige: (a) escribía siempre sistema/estructura (fabricaba canales en un depósito solo-muscular) → ahora `valorTocado ?? base?…` conservando `nil` como ausencia; (b) **reemplazaba** `manualMuscleOverridesV2` en vez de fusionar con los existentes; (c) refrescaba el ancla aunque nada cambiara; (d) borraba `preWorkoutDiscomforts`; (e) disparaba `learnFromManualAdjustment` sin gesto (ahora lo decide su propia guarda, como Android). Nota: este método hoy **no tiene llamadores en el repo** (grep → 1 coincidencia, la declaración), la corrección es de contrato, no de comportamiento visible.

### 8.3 No portado / límites (evidencia; no es paridad total)

- **Cobertura RINGS por canal (`RingsCoverage`, `RingsCoverageSource`, `NO_DATA`, snapshot atómico):** sin equivalente. `grep 'RingsCoverage|capturedFields|declaredCheckInChannels' ios-native/` → 0; en `backend/` → 0; `ios-native/.../Home/HomeRingsSection.swift` es un placeholder de 2 líneas. Contraparte Android: `domain/onboarding/RingsChannelCoverage.kt` + `screens/auge/AugeViewModel.kt:1168` + tests `RingsChannelCoverageTest.kt`/`AugeSnapshotCoverageTest.kt`.
- **Preview de check-in del alta (sensaciones `FULL_EVIDENCE` además de `PARTIAL` en `Wellbeing.capturedFields`):** no existe flujo de alta con rings en iOS/backend (`grep 'FULL_EVIDENCE|WellbeingSource' ios-native/` → 0; `grep capturedFields backend/` → 0). El merge por canales de `SetupPersistence.kt:423-434` no tiene contraparte; tampoco `WellbeingSource.ONBOARDING_INITIAL`.
- **Check-in pre-entreno EN VIVO de iOS no deposita nada** (comportamiento no corregido por estar fuera del alcance de pantalla): `WorkoutScreen.swift:265` descarta los 5 argumentos de `onSave`; `WorkoutViewModel.saveReadinessAdjustments` (`WorkoutViewModel.swift:2033`) no tiene llamadores; la hoja sí trackea `userEditedNeural/userEditedSpinal/userEditedMuscles` pero no los expone en `onSave`; iOS no tiene `invertDisplayedStructure` (Android lo aplica al guardar la columna: `AugeViewModel.kt:634`). Android deposita en `WorkoutScreen.kt:1641` y `WorkoutSessionOverlaysHost.kt:74-101`. Requiere wiring de UI → no se portó.
- **Vistas iOS sin sitios de construcción:** `ReadinessGateScreen` (Auge y Workout) y `ReadinessSheetView`/`ReadinessSheetFullView` (grep → solo definiciones). `ReadinessSheetFullView` (`ReadinessSheetView.swift:286-301`) escribe `manualNeuralBattery/manualSpinalBattery: nil` cuando el usuario no los editó (borra la evidencia previa en lugar de conservarla), no fija ancla y **reemplaza** V2 en vez de fusionar: comportamiento no tocado por estar muerto, señalado por exactitud.
- **Backend:** `has_muscular_manual` evalúa `any(...)` sobre toda la lista recibida (`recovery_engine.py:590`), mientras Android e iOS resuelven UNA fila activa (ventana 18 h corregida en §8.2.1); sin cambio porque depende de lo que el cliente envíe. Además no modela manuales de sistema/estructura ni publica procedencia/cobertura por canal → no puede expresar «Sin datos».

### 8.4 Tests y ejecución (exacto)

iOS — escritos, **NO ejecutados** (Windows sin toolchain Swift: `Get-Command swiftc,xcodebuild` → ausentes):
- `ios-native/KPKNFit/KPKNFitTests/KPKNFitTests.swift::testManualOverrideActiveWindowMatchesAndroid18Hours` — fila de hoy con solo per-muscle → activa; ayer con ancla de 17 h → activa; ayer con ancla de 20 h o sin ancla → no activa; sin fuentes manuales → nunca.
- `...::testManualBatteryAnchorFallsBackToWellbeingDateNotNow` — ancla = medianoche UTC del día del registro (nunca `now`), ancla explícita respetada, sin fila → 0.

Backend — ejecutados desde el workspace original (`C:\Users\valen\Documents\KPKNFit\backend`), sin `.env` ni secretos:

```
python -m pytest tests/test_recovery_manual_override_source.py -q                    # 3 passed in 0.33s
python -m pytest tests/test_recovery_nutrition_absence.py tests/test_initial_recovery_evidence.py tests/test_auge_muscular_session_impact.py tests/test_recovery_manual_override_source.py -q   # 18 passed in 0.17s
```

Nuevo `backend/tests/test_recovery_manual_override_source.py` (3 oráculos, cero cambios en `backend/engines/`): evidencia inicial mezcla los 3 canales sin manual (60/70/80 sobre baterías sin fatiga = 100); con `manualMuscleOverridesV2` el canal muscular **no** mezcla (100) mientras sistema/estructura siguen mezclando (70/80, límite documentado); sin evidencia, manual y no-manual coinciden (el backend expone números crudos sin metadato «sin datos»).

---

## 9. Cierres Android (árbol ACTUAL, 2026-09-25) — gasto por calendario, confirmaciones e inventario finito

Alcance: **cero ediciones en `android-native/`**, **cero cambios en `backend/`**, **cero puertos nuevos en iOS**. Solo revisión de equivalentes y actualización de este documento.

### 9.1 Qué cerró Android (referencia, sin editar)

| Cierre | Evidencia Android |
|---|---|
| `NutritionTrainingCalendarAdapter`: carga externa `MISSING` ⇒ `DayExpenditure.NotEstimable`, **nunca 0** aunque `totalKcal.mid > 0` (el descanso/participación corporal parcial suma kcal); `BODYWEIGHT_ONLY` ⇒ estimable con **peso corporal conocido** y participación > 0; cardio solo con `cardioDetails` + peso válido; sesión no fechable/programa sin ancla ⇒ `NotEstimable`, no `Rest` | `domain/nutrition/NutritionTrainingCalendarAdapter.kt:67-107, 130-169, 210-230, 292`; oráculos `NutritionTrainingCalendarAdapterTest.kt:263` (`rir-only session without load is NotEstimable and never zero`), `:274` (`unknown external load with positive rest kcal is NotEstimable`), `:295` (sin ancla ⇒ `NotEstimable`), `:527-562` (`BODYWEIGHT_ONLY` con/sin peso), `:378` (variante materializada no estimable ⇒ honesto) |
| `Program.optionalSessionConfirmations` (JSON `default []`; `dayIso`/`sessionId`/`variantKey` null o ilegible ⇒ se ignora; `variantKey` ⇒ `A`), **unión** de fuentes por sesión+fecha (`effectiveOptionalConfirmations` = registros reales + confirmaciones manuales + refuerzo), claves por `(sesión, fecha)` | `data/models/Program.kt:90,101`; `NutritionTrainingCalendarAdapter.kt:358-431`; tests `:641-666` (solo su instancia), `:668-692` (retiro puntual), `:695-721` (una variante, nunca B+C), `:724-736` (registro corrupto ignorado), `:456-481` («never from thin air»); `data/repository/NutritionCalendarForecastCoordinatorTest.kt:315` |
| Presupuesto: cambios **desde mañana** reparten el restante **exacto**; **hoy/pasado** son objetivos fijos (`fixedTargets`, insert-once) que no se mueven; **sin compensar** ingesta/gasto real en la previsión | `domain/nutrition/NutritionDayDistribution.kt:164-180` (`remainingBudget = periodBudget − Σ fixed`), `NutritionWeeklyForecastPlanner.kt:44,183`, `NutritionPlanEditorEngine.kt:360-370`, `NutritionGoalResolver.kt:325` (`fixedTargetsFor`) |
| Inventario de material **finito** para calentamientos (sin inventario ⇒ `UNKNOWN`, nunca «ilimitado»; sin alcanzar ⇒ `null`/pendiente, nunca 0 kg) — solo UI/engine Android, **no AUGE** | `domain/training/WarmupFeasibility.kt:10-51,114-134`; `screens/onboarding/SetupWizardFullJourneyUiTest.kt:363-401,812-814` («inventario finito») |

### 9.2 iOS/backend — estado real (no había motor conectado que alinear; sin cambios)

**iOS** (grep sobre `ios-native/`):
- **Sin adaptador ni consumidor de gasto previsto para nutrición:** `grep 'DayExpenditure|NotEstimable|TrainingCalendar|weeklyForecast|NutritionSessionInstance|optionalSessionConfirmations|SessionVariant|resolvePlannedExternalLoadKg'` → **0 coincidencias**. No existe pantalla ni motor que consuma kcal planificadas por fecha ⇒ no hay dónde aplicar `MISSING ⇒ NotEstimable` sin portar el adaptador completo (fuera de alcance: no se implementa el wizard iOS entero).
- **Motor de gasto parcial y desconectado:** existe `TrainingEnergyEngine.estimatePlannedSession` (`Domain/Energy/TrainingEnergyEngine.swift:230`) pero con **0 llamadores** (`grep estimatePlannedSession ios-native/` → solo la declaración) y **sin** `ResolvedLoadSource`/`MISSING`/`BODYWEIGHT_ONLY` en su archivo (grep → 0): su ruta planificada estima «con carga base» cuando falta peso (`TrainingEnergyEngine.swift:167`). Sus rutas usadas son solo en vivo/terminadas (`WorkoutViewModel.swift:1379` `estimateLiveSession`, `:2190` `estimateCompletedSession`), sin consumo nutricional.
- **Proyección de fechas sí existe, sin gasto asociado:** `Domain/Training/ProgramCalendarEngine.swift:51` (`project`, `CalendarWeekProjection`) conectada a `ProgramDetailHelpers.swift:380` y `WorkoutViewModel.swift:3245`.
- **Confirmaciones/variantes:** `Data/Models/Program.swift:477` **no tiene** `optionalSessionConfirmations` (Codable ignora claves desconocidas ⇒ un payload Android nuevo decodifica sin fabricar confirmaciones ni romper). La regla compartida existente ya coincide con Android: fallback a variante **A** con `base.sessionB ?? base` (`WorkoutScreen.swift:343`, `WorkoutViewModel.swift:302`) — misma caída que `chosenVariantOf` (`NutritionTrainingCalendarAdapter.kt:190-199`).
- **Sin presupuesto/reparto ni snapshots:** `grep 'DayDistribution|remainingBudget|fixedTargets|periodBudget|DailyGoalSnapshot|resolveDayGoals'` → 0 (ya en §3 y §6). Por tanto no existen «macros fijos de hoy/pasado» ni «restante exacto desde mañana» que alinear.
- **Sin inventario finito:** `grep 'EquipmentInventory|MachineLoadRange|WarmupFeasibility|barbellWeightKg|TrainingOptions'` → 0. Existe `Domain/Calculations/PlateCalculator.swift:10` pero recibe `availablePlates` como entrada y **no tiene llamadores** (`grep 'PlateCalculator\.'` → solo `project.pbxproj`): no hay pantalla de inventario ni viabilidad de calentamiento.

**Backend:**
- `Program`/`ProgramWeek` (`models/common.py:325,352`) solo alimentan `routers/analysis.py` (volumen/progressión). **Sin** `optionalSessionConfirmations` y con `extra` por defecto *ignore* (sólo `exercises_catalog_v2.py:31` declara `extra="forbid"`, modelo ajeno) ⇒ un payload con el campo nuevo se parsea y se descarta: sin error y sin fabricar confirmaciones.
- **Sin motor de gasto/calendario nutricional:** `grep 'DayExpenditure|TrainingCalendar|optionalSessionConfirmations|bodyweight' backend/` → 0. `forecast` en backend es únicamente la trayectoria Banister de recuperación (`engines/banister_model.py:100`, `routers/adaptive.py:135`), ajena al presupuesto nutricional.

**Conclusión del pass:** no existía un motor de calendario/gasto equivalente *conectado* en iOS/backend, por lo que **no se conectó ni se ajustó código** (hacerlo exigiría portar el adaptador/reparto entero, fuera de alcance). En ningún sitio se inventó una observación ni un `0` en ausencia.

### 9.3 Límites honestos (siguen sin paridad total)

- Los límites de §8.3 siguen vigentes (cobertura RINGS, preview de alta, check-in de workout sin depositar, vistas muertas, `any(...)` del backend, sin manuales de sistema/estructura).
- **Nuevo:** `MISSING`/`NotEstimable` vs `Rest` no existen fuera de Android porque iOS/backend no calculan gasto previsto por fecha (iOS: 0 consumidores; backend: sin endpoint).
- **Nuevo:** uniones de confirmaciones por sesión+fecha, `variantKey ⇒ A` persistido y «confirmar una opcional no activa a las del día» no tienen contraparte fuera de Android (iOS `Program` carece del campo; backend lo descarta).
- **Nuevo:** presupuesto restante exacto desde mañana / macros fijos hoy-pasado (`fixedTargets` insert-once) y «sin compensar ingesta/gasto real» no portados (iOS sin `DailyGoalSnapshot`/`resolveDayGoals`, §3; backend sin reparto).
- **Nuevo:** inventario finito de material y viabilidad de calentamiento (`WarmupFeasibility`, `EquipmentInventory`) es UI/engine Android, no AUGE y sin contraparte iOS/backend (N/A, no reclamado).
- **Nuevo:** `TrainingEnergyEngine` iOS sigue sin la semántica de fuentes de carga (`ResolvedLoadSource`/`MISSING`/`BODYWEIGHT_ONLY`) y su estimación planificada no está conectada a ningún consumidor.

### 9.4 Validación de este pass

- **Backend sin cambios** → comando de §8.4 re-ejecutado: `python -m pytest tests/test_recovery_nutrition_absence.py tests/test_initial_recovery_evidence.py tests/test_auge_muscular_session_impact.py tests/test_recovery_manual_override_source.py -q` → **18 passed in 0.23s** (evidencia previa de 18 mantenida). No se añadió test porque no se modificó backend; los tests focused se añaden sólo al cambiar su código.
- **iOS:** sin `xcodebuild`/`swiftc` en Windows → no se ejecutó nada y **no se afirma pass de XCTest** (correr `xcodebuild test` en macOS).