# Fórmulas recuperadas — Sistema A (gasto, tendencia de peso, BMR)

**Método (ronda 2):** desensamblado con capstone 5.0.7 (`re\tools\disasm.py`, listados en `re\dis_nu\dis_<addr>.txt`) sobre `nu_libapp.so` (arm64). Cada entrada del object pool se resolvió al valor real; los stubs de la VM se nombraron a partir de las raíces del snapshot (`VM_STUB_CODE_LIST`); las llamadas por tabla de despacho se resolvieron con el origen `DispatchTable::OriginElement = 4096` (comprobado: el selector −4096 sobre los cids 1951/1952/1953 lleva a las tres implementaciones de `ExpenditureAlgorithm`). Los nombres de campo de los modelos se obtuvieron del código de `toJson` (`TdeeEntry.toJson` @0x629a5c, `TrendWeightEntry.toJson` @0xa93614).
**Niveles:** OBSERVADO = leído en el desensamblado (constante + operación); INFERIDO = semántica deducida (nombre de campo, papel de un parámetro); DESCONOCIDO = no leído.

## A.F0 Campos de los modelos (OBSERVADO, por `toJson`)

| Modelo | Ranuras (palabras comprimidas de 4 B) |
|---|---|
| `TdeeEntry` (16 palabras) | w2 `timestamp` (DateTime), w3-4 `energy` (double), w5 `energySmoothed`, w6 `energySmoothedShort`, w7 `energySmoothedLong`, w8 `paused` (bool), w9 `crossover` (bool), w10 `energyChange`, w11-12 `missingDays` (double), w13 `missingWeightEntries`, w14 `beyondCalculableHorizon`, w15 `goalAdjustment` |
| `TrendWeightEntry` (12 palabras) | w2 `timestamp`, w3 `weightScaleKg`, w4 `weightTweenKg`, w5 `weightTrendKg`, w6 `weightTransientKg`, w7 `weightNoiseKg`, w8 `tweenChangeKg`, w9 `trendChangeKg` |

## A.F1 Piso de gasto (`ExpenditureFloorProvider`) — OBSERVADO

- Código: closure @0xf6b2dc → cuerpo @0x142be70–0x142c158. Entrada: `await trendWeightStreamProvider.future` (lista de `TrendWeightEntry`) y `date` opcional.
- `x = entrada(date)?.weightTrendKg ?? última.weightTrendKg` (campo w5). Si no hay valor → devuelve **598.0** (pool[32107]).
- **`floor = max(598.0, 48.1 · x^0.783)`** — constantes: 598.0 (pool[32088]), 0.783 (pool[32108] = 0x3FE90E5604189375), 48.1 (pool[32109]); potencia por el *fast-path* inline de `pow` + `LibcPow` (THR+0x750).
- Comprobación numérica: x = 60 kg → 48.1·24.68 = 1187 kcal; 80 kg → 1486; 100 kg → 1770. El mínimo absoluto 598 sólo actúa por debajo de ≈25 kg (598/48.1 = 12.43 → 12.43^(1/0.783) = 24.98).
- Unidad de x: kg (campo `weightTrendKg`) → INFERIDO que el resultado está en kcal/día (es un `ExpenditureFloor`).

## A.F2 Estimación inicial de gasto (día 0) — función @0x12c0dac (usada por V1 y V2)

Entradas: `user` (x2), objeto con el peso actual (x3, campo w5 = double; INFERIDO: `TrendWeightEntry.weightTrendKg` o peso de báscula), BMR externo opcional (x5, INFERIDO: resultado de `BmrProvider`), bandera (x6), `% graso` (d0).
1. Si `user.w50` existe, no es `'default'` y la bandera es verdadera → devuelve `double.parse(user.w50)` (INFERIDO: gasto inicial introducido por el usuario).
2. `BMR = x5 ?? (500 + 22 · min(100, W · (1 − bf/100)))` — **ecuación de Cunningham con masa magra limitada a 100 kg** (OBSERVADO: 100.0 pool[6663], 22.0 inmediato, 500.0 pool[24157]).
3. `factor = double.parse(Activity.valor) + bonus`, con `Activity` ∈ {"1.2", "1.4", "1.6"} (campo w2.w5 del usuario) y `bonus` según el enum `Exercise` (campo w3 del usuario): `low "1-3"` → 0.1, `med "4-6"` → 0.2, `high "7+"` → 0.3, otro → 0.0 (OBSERVADO).
4. **`gastoInicial = BMR · factor`** (OBSERVADO).
- Comprobación: W = 80, bf = 20 % → LBM 64 → BMR 1908; moderadamente activo (1.4) + 4-6 sesiones (0.2) → 1.6 → 3053 kcal/día.

## A.F3 Núcleo de gasto por ventana (V1 y V2)

Código: V2 → `ExpenditureAlgorithmV2` entrada @0x12c1364 → núcleo @0x12c2174 (3352 B) con ayudantes @0x12c3038 (búsqueda de ventana), @0x12c0a30 (suma de ingesta), @0x12c029c (regresión), @0x12c2e8c (conversión a energía). V1 → @0x12bf530 → núcleo @0x12bf568 (2952 B; mismas constantes y ayudantes duplicados @0x12c0704/@0x12c00f0).

| Paso | Fórmula / regla | Nivel | Dónde |
|---|---|---|---|
| Días 0–6 | `energy = energySmoothed = gastoInicial` (A.F2); `paused = crossover = false`, `energyChange = 0`, `missingDays = 7`, `missingWeightEntries = 7` | OBSERVADO | @0x12c2390–0x12c24dc |
| Ventana | `n = min(días disponibles, N)`; se reduce `n` de uno en uno mientras `díasPresentes(n)/n < 0.85`, con mínimo 7; si ni 7 cumple → sin solución (`paused`) | OBSERVADO (0.85 pool[40893], mínimo 7) | @0x12c3038 |
| Día presente | día con ingesta registrada `0 < kcal ≤ 50 000` (pool[40831]) o día marcado con un indicador booleano en el mapa de ayuno/vacío (INFERIDO: día de ayuno declarado) | OBSERVADO (umbrales) / INFERIDO (indicador) | @0x12c0a30 |
| Ingesta | `ingestaMedia = Σ kcal(ventana) / díasPresentes` | OBSERVADO | @0x12c2174 l.652-655 |
| Pendiente | regresión lineal por mínimos cuadrados (`LeastSquaresSolver`, grado 1, pesos 1.0) de `weightTrendKg` frente al índice de día; `solution` = pendiente (kg/día); `days_present` (segundo contador) = días con `weightScaleKg ≥ 25` | OBSERVADO | @0x12c029c |
| Energía del cambio de peso | `ΔE = pendiente < 0 ? pendiente·7700 : pendiente·4400` (kcal/día; **7700 kcal/kg al perder, 4400 kcal/kg al ganar**) | OBSERVADO (7700 pool[28700], 4400 pool[40895]) | @0x12c2e8c |
| Candidato | `candidato = ingestaMedia − ΔE` | OBSERVADO | @0x12c2174 l.521-522 |
| Rampa inicial | Δ = candidato − anterior; día < 6 → `anterior + 0.33·Δ`; 6 ≤ día < 13 → `anterior + 0.66·Δ`; día ≥ 13 → `candidato` limitado a `anterior ± paso` | OBSERVADO (0.33 pool[40891], 0.66 pool[40892]; límites 6 y 13) | l.524-592 |
| Paso máximo diario | V2 serie **corta**: ventana N = 10 días, paso 75 kcal/día; serie **larga**: N = 25 días, paso 30 kcal/día (argumentos de las dos llamadas en @0x12c13b0–0x12c1420). V1: una sola serie, N = 10 (INFERIDO por `cmp 20 (smi 10)`), sin rampa 0.33/0.66 visible | OBSERVADO (V2) / INFERIDO (V1) | @0x12c1364, @0x12bf568 |
| Suelo del gasto | `energy ≥ max(750, 0.85 · BMR_día)`, con `BMR_día` = Cunningham del día (mismo cálculo que A.F2) | OBSERVADO (750 pool[40894], 0.85 pool[40893]) | l.717-734 |
| Días sin datos | `missingDays = floor(max(0, 7 − 7·díasBáscula/n))` (y análogo para ingesta) — `LibcFloor` (THR+0x768) | OBSERVADO | l.650-680 |
| Sin solución | si la ventana no cumple el 85 % → `paused = true`, la energía se mantiene | INFERIDO | l.608-639 |
| Combinación V2 | `energySmoothed = crossover ? (corta + 2·larga)/3 : (corta + larga)/2`; `energySmoothedShort = corta`, `energySmoothedLong = larga`; `crossover` = resultado booleano de un closure sobre (timestamp, …) | OBSERVADO (pesos) / INFERIDO (semántica de `crossover`) | @0x12c1558–0x12c16dc |
| Alisado final V2 | tres pasadas de EMA con **α = 0.1** (A.F4) sobre `energySmoothedLong` (w7), `energySmoothedShort` (w6) y `energySmoothed` (w5), en ese orden | OBSERVADO (α, campos leídos por los *getters* @0x12c37d0/@0x12c3c30/@0x12c4118) | @0x12c1848–0x12c19f0 |
| V2 además | serie `MacdEntry` sobre la tendencia de peso con EMA α = 0.2857 (= 2/7) en @0x12c1cf4 | OBSERVADO (α) / INFERIDO (MACD por el nombre) | @0x12c1cf4 |
| V1 | `AverageType.weighted` (media móvil ponderada) en lugar de la EMA | OBSERVADO (enum) / DESCONOCIDO (pesos) | @0x12bf568 |

Comprobación numérica del núcleo: ingesta media 2500 kcal, pendiente −0.05 kg/día → ΔE = −385 → candidato = 2885 kcal/día; si la pendiente fuese +0.05 → ΔE = +220 → 2280.

## A.F4 Media móvil exponencial (`MovingAverage`, tipo `exponential`) — OBSERVADO

- Código: `MovingAverage.call` @0xab2620 → pasada exponencial @0xab294c.
- **`y₀ = x₀`; `yᵢ = (1 − α)·yᵢ₋₁ + α·xᵢ`**; el valor se escribe mediante el closure constructor (`(List<T>, num) -> T`).
- Alphas de la tendencia de peso V1 (@0xab252c): `TrendWeightAlpha.ema20` → **α = 0.1** (pool[6704]); `ema9` → **α = 0.22222** (pool[28647]). Es decir α = 2/N (no 2/(N+1)).
- INFERIDO: la tendencia V1 (`weightTrendV1`) es esta EMA aplicada a `weightScaleKg`.

## A.F5 Tendencia de peso V2 — filtro de Kalman de tres estados

| Elemento | Resultado | Nivel | Dónde |
|---|---|---|---|
| Estado | `[nivel, pendiente, transitorio]` → campos `weightTrendKg`, `trendChangeKg`, `weightTransientKg` | INFERIDO (por la estructura de F y los nombres) | — |
| Inicialización | nivel₀ = **mediana** de los pesajes de los primeros 7 días si hay ≥ 3; si no, el primer pesaje (`_ThreeStateKalmanModel(w2 = hayDatos, w3 = nivel₀)`) | OBSERVADO | @0xab1a88, mediana @0xaa7a50 |
| Matriz de transición (por día) | `φₛ = e^(−ln2/30) = 0.97716` (vida media 30 días), `φ_f = e^(−ln2/2) = 0.70711` (vida media 2 días), `c = (1 − φₛ)/(ln2/30) = 0.98855`; **F = [[1, c, 0], [0, φₛ, 0], [0, 0, φ_f]]** | OBSERVADO (`LibcLog` THR+0x7c8 de 2.0, `/30`, `/2`, `LibcExp` THR+0x7c0; lista de 9 elementos) | @0xab0fbc l.5-36, 98-187 |
| Ruido de proceso | **Q = diag(0.0025, q₂²·(1 − φₛ²), q₃²·(1 − φ_f²))** con `q₂ = clamp(0.0008·max(|W|, 50), 0.04, 0.12)` y `q₃ = clamp(0.01·max(|W|, 50), 0.6, 1.4)` (W = peso de referencia, kg). Un término `q₁ = 0.001·max(|W|,50)` se calcula pero se multiplica por `max(0, −2) = 0` (constante plegada) → no contribuye | OBSERVADO | @0xab0fbc l.196-290, @0xab1788, @0xab16f4, @0xab1470, @0xab0f14 |
| Ruido de observación | `σ_obs = clamp(0.007·max(|W|, 50), 0.45, 0.85)`, multiplicado por el parámetro con nombre `observationNoiseScale` (por defecto 1.0) y vuelto a limitar a [0.45, 0.85] | OBSERVADO | @0xaafb0c, @0xab19b4 |
| Comprobación | W = 80 kg: q₂ = 0.064 → Q₁₁ = 0.004096·(1 − 0.95484) = 1.85·10⁻⁴; q₃ = 0.8 → Q₂₂ = 0.64·0.5 = 0.32; σ_obs = 0.56 kg | — | — |
| Suavizado / horizonte | `smoothStateToHorizon(int, int)` @0xaae27c existe (suavizado hacia atrás sobre listas de 9 doubles); el valor numérico del horizonte y si R entra como σ o σ² no se leyeron (el bucle principal @0xaa9dcc mide 12,5 KB) | DESCONOCIDO | — |
| Robustez | ventana de 21 días (1 814 400 000 000 µs) y constante **1.4826** (factor MAD→σ) con 0.02 en la ponderación de pesajes `_WeightedValue` | OBSERVADO (constantes) / INFERIDO (ponderación robusta de atípicos) | @0xaa6614 |
| Tasa objetivo | `0x12c62c0`: `bulk` → +tasa, `cut` → −tasa, `maintenance` → 0; en @0xaa7cc4 aparecen 0.15, 100 y **0.680389 kg (= 1.5 lb)** junto a `GoalType` y 7.0 → INFERIDO: tasa semanal por defecto/limite en kg/semana convertida a kg/día | OBSERVADO (constantes) / INFERIDO | @0xaa7cc4 |

## A.F6 ExpenditureAlgorithmV3 — estructura (parcial)

- @0x12c41cc llama tres veces al núcleo V3 @0x12c46ac (6284 B) con tres juegos de parámetros y combina los resultados en @0x12c442c: `energySmoothed` = ejecución A, `energySmoothedShort` = ejecución B, `energySmoothedLong` = ejecución C (OBSERVADO).
- Parámetros (OBSERVADO; significado de cada posición DESCONOCIDO): A = (1.0, 0.025; 0.1625, 0.1, 0.5, 0.001, 0.02); B = (1.1, 0.0275; 0.2149, 0.1176, 0.6, 0.00105, 0.021); C = (0.9, 0.0225; 0.1307, 0.0869, 0.4, 0.00095, 0.019). B y C son variantes ≈ ×1.1 / ×0.9 de A → INFERIDO: escenarios alto/bajo (coherente con `ExpenditureMinMaxProvider`).
- El núcleo V3 usa `GoalAdjustmentBudget`, `_GoalTimelineCursor` (objetivos por fecha), 7700 kcal/kg, 300.0, ±0.1 y **±21.428571 (= 150/7)** como límites, y una ventana de 1–14 días desde el cambio de objetivo (@0x12c5fe4: `0 < días ≤ 14`, resultado `/14`). El closure `() -> double` @0x12c6344 devuelve `ctx[0] · (ctx[1] · 4 / 100)`.
- Dos banderas del usuario (`MacroFactorUser` w94/w95, `?? false`) activan el cursor de objetivos.
- Fórmula cerrada de V3: DESCONOCIDO (no se leyó el cuerpo de 6,3 KB; 3 enfoques agotados: resumen de constantes, lectura de ayudantes, desensamblado parcial del combinador).

## A.F7 Lo que sigue DESCONOCIDO en A
- Semántica exacta de `crossover` y del closure `(timestamp, …) -> bool` en V2.
- Pesos de la media ponderada de V1.
- Ecuación «macrofactor» de BMR (`BmrEquationV1`): sólo se localizó Cunningham (en A.F2).
- Paso de actualización del Kalman (ganancia, uso de R) y horizonte del suavizador.
