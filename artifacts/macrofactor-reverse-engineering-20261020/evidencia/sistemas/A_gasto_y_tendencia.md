# Sistema A — Gasto energético (expenditure), tendencia de peso, BMR, grasa corporal y macro/micro

**Fuente principal:** `nu_libapp.so` (MacroFactor Nutrition 5.8.9, arm64, punteros comprimidos).
**Nota:** `wk_libapp.so` (Workouts 1.4.1, armeabi-v7a) contiene **las mismas librerías** `package:macrofactor/src/services/fitness_analytics/*` (comprobado en `re/out_wk/libraries.json`, líneas 4222-7504: `expenditure_algorithm_v1/v2/v3.dart`, `weight_trend_service.dart`, `bmr_equation_v1/v2.dart`, …). Ambas apps se compilan del mismo monorepo; para este sistema sólo se analizó en detalle el binario arm64.

**Artefactos usados:** modelo `re/out_nu/*.json`; digests `re/digests/A_nu.txt` (150 clases) y `re/digests/A_nu_fa.txt` (sólo `fitness_analytics`); herramientas `re/tools/*.py`.
**Convenciones:** los offsets son direcciones virtuales dentro de `nu_libapp.so` (el texto del snapshot empieza en `0x600000`). «smi n» = entero Dart etiquetado (valor n). «palabras» = ranuras de 4 bytes de la instancia, incluidas 2 de cabecera.

---

## A.0 Mapa de librerías y clases (OBSERVADO en el snapshot)

| Librería (`package:macrofactor/src/…`) | Clases / funciones supervivientes |
|---|---|
| `services/fitness_analytics/expenditure_algorithm.dart` | `ExpenditureAlgorithm` (abstracta, usada como interfaz), `ExpenditureAlgorithmFactoryProvider`, `ExpenditureAlgorithmFactoryFamily.call(ExpenditureVersion)` |
| `…/expenditure_algorithm_v1.dart` | `ExpenditureAlgorithmV1` (const, 4 palabras ⇒ sin estado propio) |
| `…/expenditure_algorithm_v2.dart` | `ExpenditureAlgorithmV2` (const); closures sobre `MacdEntry` y `TdeeEntry` |
| `…/expenditure_algorithm_v3.dart` | `ExpenditureAlgorithmV3` (const), `GoalAdjustmentBudget` (12 palabras), `_GoalTimelineCursor` (8), `_GoalTimelineEntry` (const, 8; getter `startDate`) |
| `…/expenditure_service.dart` | `ExpenditureService`; providers `expenditureProvider` (`Future<List<TdeeEntry>?>`), `expenditureStreamOldProvider` (`Stream<List<TdeeEntry>?>`), `ExpenditureAlgorithmProvider`/`Family.call({bool ignoreInitTdee, ExpenditureVersion? expenditureVersionOverride})`, `ExpenditureFloorProvider`/`Family.call({DateTime? date})` → `Future<double>`, `ExpenditureMinMaxProvider`/`Family.call({DateTime? date})` → `(double, double?)` |
| `…/weight_trend_service.dart` | `WeightTrendService`, `_DatedWeight` (getters `weightKg`, `day`), `_WeightedValue`, `_ThreeStateKalmanModel`, `_ThreeStateDailyStates`, `_ThreeStateTransition`, `_WeightTrendV2Series`, `_V2Prediction`, `WeightTrendV2Prediction` (const), enum `WeightTrendV2PredictionBasis`; funciones locales `smoothStateToHorizon(int, int) -> List<double>` y `clusterEndPosition(int) -> int`; providers `trendWeightStreamProvider`, `trendWeightEma9StreamProvider`, `TrendWeightFamily.call({TrendWeightAlpha trendWeightAlpha})`, `currentWeightTrendProvider` (`Future<double?>`), `currentWeightTweenProvider` (`Future<double?>`) |
| `…/bmr_service.dart`, `bmr_equation.dart`, `bmr_equation_v1.dart`, `bmr_equation_v2.dart` | `BmrService`, `BmrProvider`/`BmrFamily.call({BmrVersion? bmrVersionOverride, DateTime? onDate})` → `Future<double?>`, `BmrEquation` (interfaz), `BmrEquationV1`, `BmrEquationV2`, `BmrEquationFactoryFamily.call(BmrVersion)`, `birthdayProvider` |
| `…/body_fat_service.dart` | `BodyFatService`, `visualBodyFatProvider` (`Future<double>`), `bodyFatServiceProvider` |
| `…/macro_nutrients_service.dart` | `macroNutrientsProvider` → `Future<SplayTreeMap<DateTime, NutritionEntry>>` |
| `…/micro_nutrients_goal_service.dart` | `allNutrientGoalsProvider` → `Future<SplayTreeMap<DateTime, MicroGoalEntry>>` |
| `services_stream/fitness_analytics_stream.dart`, `services/weight_analytics_service.dart`, `steps_analytics_service.dart`, `nutrition_analytics_service.dart`, `unified_analytics_service.dart` | capas de streaming/analítica (sólo inventariadas) |
| `models/tdee_entry.dart`, `trend_weight_entry.dart`, `scale_entry.dart`, `body_entry.dart`, `macd_entry.dart`, `goal_entry.dart` | modelos (§A.3) |
| `enums/*.dart` | `ExpenditureVersion`, `WeightTrendVersion`, `WeightTrendPredictionStyle`, `TrendWeightAlpha`, `BmrVersion`, `TdeeMode`, `CalorieFloor`, `MaintenanceApproach`, `ExpenditureStrategy`, `Activity`, `GoalType`, `GoalStatus`, `WeightChange`, `LifetimeWeight`, `Sex`, `ProteinPreference` |

## A.1 Enumeraciones (OBSERVADO: instancias const del snapshot con `index`, `_name` y valor serializado)

| Enum | Valores (índice: nombre → valor JSON) |
|---|---|
| `ExpenditureVersion` | 0: expenditureV1 → "1", 1: expenditureV2 → "2", 2: expenditureV3 → "3" |
| `WeightTrendVersion` | 0: weightTrendV1 → "1", 1: weightTrendV2 → "2" |
| `WeightTrendPredictionStyle` | 0: weightData → "w", 1: blended → "b", 2: goalRate → "g" |
| `WeightTrendV2PredictionBasis` | 0: goalRate, 1: weightData, 2: blended |
| `TrendWeightAlpha` | 0: ema20, 1: ema9 |
| `BmrVersion` | 0: macrofactor → "1", 1: cunningham → "2" |
| `TdeeMode` | 0: static, 1: dynamic |
| `CalorieFloor` | 0: standard, 1: low, 2: none |
| `MaintenanceApproach` | 0: loss, 1: keep, 2: gain |
| `ExpenditureStrategy` | 0: updating, 1: holding |
| `Activity` | 0: mostlySedentary → "1.2", 1: moderatelyActive → "1.4", 2: veryActive → "1.6" |
| `GoalType` | 0: bulk → "b", 1: cut → "c", 2: maintenance → "m" |
| `GoalStatus` | 0: completed → "c", 1: achieved → "a", 2: inProgress → "i", 3: failed → "f" |
| `WeightChange` | 0: gaining → "g", 1: losing → "l", 2: maintaining → "m", 3: unknown → "u" |
| `LifetimeWeight` | 0: higher → "h", 1: lower → "l", 2: unknown → "u" |
| `Sex` | 0: male → "M", 1: female → "F" (con fallback a masculino en el deserializador) |
| `ProteinPreference` | 0: low, 1: moderate, 2: high, 3: extra |

Los valores "1.2 / 1.4 / 1.6" de `Activity` son cadenas serializadas (OBSERVADO); que se usen como multiplicador PAL es **INFERIDO** (coinciden con los multiplicadores clásicos, pero no se ha visto el código que los convierte a número).

## A.2 Gasto energético — afirmaciones

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Hay tres algoritmos de gasto (V1, V2, V3) y se elige uno por `ExpenditureVersion`. | OBSERVADO | `ExpenditureAlgorithmFactoryProvider.<closure>` @0xab5360 llama a @0xab53e0 (120 B) que compara el índice del enum (cmp 0 / 1 / 2) y llama a los *alloc stubs* de `ExpenditureAlgorithmV1`, `V2` y `V3`. |
| Entradas del algoritmo: versión (`expenditureVersionOverride`) y bandera `ignoreInitTdee`. | OBSERVADO | Firma de `ExpenditureAlgorithmFamily.call({bool ignoreInitTdee, ExpenditureVersion? expenditureVersionOverride})` (@0xab4fd4). |
| Salida: lista de `TdeeEntry` (un valor de gasto por día). | OBSERVADO (tipo) / INFERIDO (un valor por día) | Tipo de retorno `Future<List<TdeeEntry>?>`; `TdeeEntry` serializa fechas con `DateFormat` 'yyyy-MM-dd' (campo estático `_dateFormatter`). |
| El proveedor del algoritmo decide versión y parámetros: referencia las tres versiones, `TrendWeightAlpha.ema20` y `.ema9`, `WeightTrendVersion.weightTrendV1` y pide la BMR por fecha (`BmrFamily.call(onDate: …)`). | OBSERVADO (pool y llamadas) | Cuerpo @0x13c710c (1980 B): pool con esas constantes y llamada a `BmrFamily.call` @0xab5698. |
| V1 y V2 usan la tendencia de peso EMA (alpha 20 o 9) como entrada; V3 usa además la línea temporal de objetivos. | INFERIDO | Combinación de lo anterior con las clases de V3 (`_GoalTimelineCursor` construido desde `MapEntry<DateTime, GoalEntry>` @0x12c70e4). Qué alpha usa cada versión no se ve. |
| V1/V2 reconstruyen la serie con closures `(List<TdeeEntry>, num) -> TdeeEntry`. | OBSERVADO (firmas) / INFERIDO (interpolación o acceso con índice fraccional para rellenar días) | V1: @0x12c10a4 (616 B); V2: @0x12c3428 (936 B), @0x12c3884 (940 B), @0x12c3ce4 (1076 B); todas asignan `TdeeEntry` y comprueban `double?`. Los inmediatos 60/62 de esas funciones son comprobaciones de *class id* (Double = 62), **no** una ventana de 30 días. |
| V2 añade una serie tipo MACD. | OBSERVADO (nombres) / INFERIDO (semántica) | Closure `(List<MacdEntry>, num) -> MacdEntry` @0x12c2020 (340 B) exclusiva de V2; modelo `models/macd_entry.dart`. "MACD" se infiere del nombre (media móvil de convergencia/divergencia). |
| V3 introduce un «presupuesto de ajuste por objetivo». | OBSERVADO (clases) / INFERIDO (semántica) | `GoalAdjustmentBudget` (12 palabras), `_GoalTimelineCursor`, `_GoalTimelineEntry.startDate`; closure `() -> double` @0x12c6344 con constantes 100.0 y 4 (porcentaje y divisor/exponente: DESCONOCIDO). |
| Existe un «piso» de gasto (`ExpenditureFloorProvider`) dependiente de fecha, calculado con las constantes 598.0, 0.783 y 48.1. | OBSERVADO (constantes y firma) / DESCONOCIDO (fórmula) | Closure @0xf6b2dc → cuerpo @0x142be70 (744 B): pool `598.0`, inmediatos de doble `0.783` (0x3FE90E5604189375), `48.1` (0x40480CCCCCCCCCCD) y `598` (0x4082B00000000000); llama a tres funciones sin nombre (@0x66d730, @0x6759ec, @0x685444). No se ha desensamblado la aritmética. |
| La preferencia de usuario `CalorieFloor {standard, low, none}` modula ese piso. | INFERIDO | Sólo por nombre; no se ha visto el enum referenciado desde el cuerpo del piso. |
| Hay un proveedor de mínimo/máximo de gasto por fecha que usa las entradas corporales por ventana temporal. | OBSERVADO (firma, llamada) / INFERIDO (semántica) | `ExpenditureMinMaxFamily.call({DateTime? date})` → `(double, double?)`; llama a `BodyEntryByTimeFrameFamily.call` (@0x976ec8). |
| Pausa / reanudación del gasto: existe una estrategia `ExpenditureStrategy {updating, holding}` y pasos de coaching `ExpenditurePauseStep` / `ExpenditureResumeStep` (módulo *logging break*). | OBSERVADO (nombres) / INFERIDO (mecánica) | Enum y clases de `coaching_modules/logging_break_module/steps/*` (ver `B_coaching.md`). INFERIDO: con `holding` el algoritmo no actualiza el gasto durante un parón de registro; `ignoreInitTdee` permite recalcular sin el TDEE inicial. Umbrales (días sin registro) DESCONOCIDOS. |
| `TdeeMode {static, dynamic}` distingue gasto fijado por el usuario de gasto calculado. | INFERIDO | Sólo por nombre. |
| Factor de actividad inicial: `Activity` con valores "1.2", "1.4", "1.6". | OBSERVADO (valores) / INFERIDO (uso como multiplicador) | Enum en `enums/activity.dart`. |

## A.3 Tendencia de peso — afirmaciones

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Hay dos versiones de tendencia (`weightTrendV1`, `weightTrendV2`) y dos *alphas* nominales (`ema20`, `ema9`). | OBSERVADO | Enums `WeightTrendVersion`, `TrendWeightAlpha`; providers `trendWeightStreamProvider` y `trendWeightEma9StreamProvider`; `TrendWeightFamily.call({TrendWeightAlpha trendWeightAlpha})`. |
| V1 es una media móvil exponencial con dos constantes de suavizado (nominalmente 20 y 9 periodos). | INFERIDO | Por los nombres `ema20`/`ema9`. Los valores numéricos del alpha **no se han localizado** (el código del cálculo es un método normal sin nombre). |
| V2 es un filtro de Kalman de tres estados evaluado por día, con suavizado posterior. | OBSERVADO (nombres) / INFERIDO (semántica) | Clases `_ThreeStateKalmanModel`, `_ThreeStateDailyStates`, `_ThreeStateTransition`, `_WeightTrendV2Series`, `_V2Prediction`; función `smoothStateToHorizon(int, int) -> List<double>` @0xaae27c (1956 B) cuyo auxiliar @0xaaea20 (3304 B) usa 1e-14, NaN, −∞ y los inmediatos 0.5, 1, 2, 3, 10, con bucles comparados contra 3 (INFERIDO: dimensión 3 del estado). Qué representan los tres estados (p. ej. nivel, pendiente, …) es DESCONOCIDO. |
| Los pesajes se agrupan por día natural. | OBSERVADO (constante) / INFERIDO (semántica) | `clusterEndPosition(int) -> int` @0xaaf708 contiene 86 400 000 000 (µs por día). |
| Varios pesajes de un día se combinan con pesos (`_WeightedValue`). | INFERIDO | Comparador `(_WeightedValue, _WeightedValue) -> int` @0xaa7bb0 y reducción `(double, _WeightedValue) -> double` @0xaa7b4c. |
| La predicción V2 puede basarse en la tasa objetivo, en los datos de peso o en una mezcla; por defecto `blended`, y la elección se guarda como `weightTrendPredictionStyle` junto a `goalEntries`. | OBSERVADO | `TrendWeightProvider` (pool con claves 'goalEntries', 'weightTrendPredictionStyle' y `WeightTrendPredictionStyle.blended`, `A_nu_fa.txt` l.40); const `WeightTrendV2Prediction` por defecto {0.0, null, basis goalRate, []}. |
| Salida: `List<TrendWeightEntry>` (12 palabras, fecha 'yyyy-MM-dd'), más `currentWeightTrend` y `currentWeightTween` (valor interpolado para animación). | OBSERVADO (tipos) / INFERIDO (tween = interpolación) | Providers y modelo. |
| Entrada: `ScaleEntry` (pesajes de báscula) con `weightKg`, `fatPercent`, `timestamp`, `source`, `dataOrigin`. | OBSERVADO | Getters `dyn:get:*` de `ScaleEntry` (8 palabras; interfaces `HasTimestamp`, `SourcedData`). |

## A.4 BMR y grasa corporal — afirmaciones

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Dos ecuaciones de BMR: `macrofactor` ("1") y `cunningham` ("2"); se seleccionan por `BmrVersion` con fábrica. | OBSERVADO | Enum `BmrVersion`; `BmrEquationFactoryFamily.call(BmrVersion)`; clases `BmrEquationV1`/`V2` (const, sin estado). |
| `BmrProvider` admite `bmrVersionOverride` y `onDate`, y su cuerpo referencia `BmrVersion.cunningham`. | OBSERVADO | Firma de `BmrFamily.call`; cuerpo @0x13c79d4 (1256 B) con esa constante en el pool. |
| Cunningham depende de la masa magra y por tanto del % graso (`visualBodyFatProvider`). | INFERIDO | Por nombre de la ecuación y por la existencia de `BodyFatService`. Los coeficientes de ninguna de las dos ecuaciones se han localizado (DESCONOCIDO). |
| La app estima un % graso «visual». | OBSERVADO (nombre) / DESCONOCIDO (método) | `visualBodyFatProvider` → `Future<double>`; `BodyFatService` (4 palabras). |

## A.5 Macro y micronutrientes

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Los macros diarios se exponen como mapa ordenado por fecha de `NutritionEntry`. | OBSERVADO | `macroNutrientsProvider` → `SplayTreeMap<DateTime, NutritionEntry>`. |
| Los objetivos de micronutrientes se exponen por fecha (`MicroGoalEntry`) y se calculan a partir de `nutrientRanges` (ver D). | OBSERVADO (tipos) / INFERIDO (cálculo) | `allNutrientGoalsProvider`; `nutrientRanges: Map<Nutrient, Map<SexPregnancyLactation, Map<AgeGroup, NutrientCalculation>>>` (D_nu_fns l.156). |
| `ProteinPreference {low, moderate, high, extra}` y `MaintenanceApproach {loss, keep, gain}` parametrizan el programa de macros. | INFERIDO | Sólo por nombre. |

## A.6 Modelos (tamaños OBSERVADOS; nombres de campos mayoritariamente DESCONOCIDOS)

| Modelo | Palabras | Observado |
|---|---|---|
| `TdeeEntry` | 16 (≈14 ranuras) | mixin `Compare`, interfaz `HasTimestamp`, `toJson`, getter `timestamp`, `_dateFormatter` estático |
| `TrendWeightEntry` | 12 | `HasTimestamp`, `toJson`, 1 instancia const, `_dateFormatter` |
| `ScaleEntry` | 8 | getters `dataOrigin`, `fatPercent`, `source`, `timestamp`, `weightKg` |
| `BodyEntry` | 28 | `HasTimestamp`, `toJson` |
| `MacdEntry` | — | sólo referenciado desde V2 |
| `GoalEntry` | — | usado por V3 y por la predicción (`goalEntries`) |
| `MacroFactorUser` | 100 | perfil de usuario (campos sin nombre) |

Los objetos `Field` de instancia fueron eliminados por el *tree shaker* (sólo sobreviven los campos con inicializador no trivial y los estáticos), por lo que la correspondencia ranura → nombre no se puede recuperar del snapshot.

## A.7 Lo que NO se pudo determinar (resumen)

- Fórmulas numéricas de V1/V2/V3 y del piso (sólo constantes aisladas: 598.0, 0.783, 48.1, 100, 4).
- Valores de alpha de las EMA y matrices del Kalman.
- Coeficientes de las ecuaciones de BMR.
- Condiciones exactas de pausa/reanudación y la diferencia operativa entre `updating`/`holding`.
- Ver `99_limites_y_peticiones.md` para las herramientas que permitirían cerrar estos huecos.
