# Fórmulas recuperadas — Sistema C (StrengthService, calentamiento, descarga, panel muscular)

**Método:** desensamblado capstone de `wk_libapp.so` (arm32; listados en `re\dis_wk\`), origen de la tabla de despacho `1023` (comprobado: el selector 105849 sobre `_GrowableList` resuelve a `_GrowableList.add`). Constantes de 64 bits reconstruidas a partir de pares de entradas del pool. Entradas de runtime en arm32: `LibcPow` = THR+0x374, `LibcRound` = THR+0x38c, `LibcExp` = THR+0x3ac.

## C.F1 Curva x-RM (`XRmCurve`) — OBSERVADO

- Construcción @0xb6b3d8 (llamada desde `getSetAutoProgressionCurve` @0xc80c34 y `getProgressionSet`). Entradas: `SimpleSet` (reps en w1-2, `Weight` en w3, RIR en w4-5) y `RegionTrained`.
- Base por región: **e = 0.965 si `RegionTrained.upperBody`, 0.975 en otro caso** (pool[31639] / pool[31641]).
- `n = reps + RIR` (repeticiones hasta el fallo).
- **`e1RM = peso / (0.2 + 0.8 · e^(n − 1))`** (0.8 pool[31649]/[19269], 0.2 pool[31651]/[19399]).
- Objeto `XRmCurve`: w1-2 = `e`, w3 = `0.8·e1RM/e`, w4 = `0.2·e1RM`, w5-6 = `n`.
- Evaluación @0xb68120 (`(XRmCurve, double n, {progress})`): **`W(n) = (w3·eⁿ + w4) · (1 + progress) = e1RM · (0.2 + 0.8·e^(n−1)) · (1 + progress)`**; `progress` es un argumento con nombre (por defecto 0; INFERIDO: fracción de progresión deseada).
- `getRepsWeight(n)` @0xc7fc24 = `W(n) − offset` con `offset` capturado en el contexto (w12; INFERIDO: parte de peso corporal/asistencia).
- Comprobación: 100 kg × 5 reps @ RIR 0, tren superior: n = 5 → 0.2 + 0.8·0.965⁴ = 0.8937 → e1RM = 111.9 kg (tren inferior: 0.975⁴ → 0.923 → 108.3 kg). W(1) = e1RM.

## C.F2 Repeticiones fatigantes esperadas — OBSERVADO

`getExpectedFatiguingReps({double reps, int rir})` @0xc811c0:
**`f = c + (reps^0.582 · e^(−0.327·rir))^1.718213`**, con 0.582 = pool[31710], −0.327 = pool[31628], 1.718213 = pool[31718] = 1/0.582 (producto 0.582·1.718213 = 1.0000). Simplificado: **`f = c + reps · e^(−0.5619·rir)`**. `c` = campo w4 de un objeto opcional capturado (`?? 0.0`, pool[2998]); INFERIDO que normalmente es 0.
Comprobación: reps 10, RIR 0 → 10; RIR 1 → 5.70; RIR 2 → 3.25; RIR 3 → 1.85.

## C.F3 Fatiga acumulada — OBSERVADO

`getCumulativeFatigue(double x)` @0xc80fe8: **`−2.481 · x^0.582 / 100`** (2.481 pool[31700], 0.582 pool[31702], 100 pool[31698]). Ejemplo: x = 10 → −0.0948; x = 20 → −0.142. INFERIDO: factor multiplicativo negativo aplicado a la capacidad (p. ej. e1RM·(1 + fatiga)).

## C.F4 Ranking de candidatos — OBSERVADO

`getRanking(int reps, Weight w)` @0xc7fa78: **`|reps − repsObjetivo| · (|W(reps) − offset − w| + 1)`**, con `repsObjetivo` y la curva tomados del contexto (w20, w21.w5.w17). INFERIDO: el candidato de menor valor gana (0 si coincide en reps); `getAutoTargetTotalRepsAndWeight` genera candidatos con un `Range(step, inclusiveMax)` y los ordena con esta función.

## C.F5 Calentamiento inteligente — OBSERVADO

`getSmartWarmUpRatios({double oneRepMaxRatio})` @0xc8c454: con la lista constante `[0.4, 0.6, 0.8, 0.95]` (pool[31861]) y `k = 0.6` (pool[31862]):
- se recorren las constantes **mientras sean menores que `oneRepMaxRatio`** (la primera ≥ al ratio detiene el bucle);
- primer escalón = constante (0.4); cada escalón siguiente = **`anterior + 0.6 · (oneRepMaxRatio − anterior)`** (la constante sólo decide cuántos escalones hay).
Ejemplos: ratio 0.85 → [0.40, 0.67, 0.778]; ratio 0.97 → [0.40, 0.742, 0.879, 0.934]; ratio 0.5 → [0.40].
INFERIDO: `oneRepMaxRatio` = peso de trabajo / e1RM y cada ratio se multiplica por el peso de trabajo; además `hasWorkedOutPrimaryAgonists`/`hasDoneSameExercise` recortan la lista y `getClosestWarmUpWeight` redondea al equipamiento.

## C.F6 Descarga (deload) — OBSERVADO

- Selección del ciclo base @0xc15cf8: `SimplePeriodization` → su único objetivo; `PeriodizedPeriodization` → el objetivo del **primer** ciclo si `WorkoutDeload.firstCycle`, del **último** si `lastCycle`.
- Transformación @0xc15ffc: **series = round(0.8 · nSeries), mínimo 1** (0.8 pool[19269]; el redondeo se hace con un método `num` resuelto por despacho — `round`/`ceil` no distinguidos: INFERIDO `round`); se conservan las primeras series.
- Por serie (@0xc16238): tipo `failure` → `standard` (otros tipos sin cambio); registro objetivo transformado en @0xc1633c mediante dos closures idénticos @0xc1654c: **`x → round(0.8 · x)`** (`LibcRound`, THR+0x38c) sobre los dos campos enteros `<int,int>` del `ExerciseTargetLog` (INFERIDO: `minReps` y `maxReps`); RIR, duración y distancia no se tocan (INFERIDO).
- Resultado: `_CycleTarget(objetivo transformado, true)`.

## C.F7 Series y volumen por grupo muscular — OBSERVADO (ponderación) / DESCONOCIDO (ventana)

- @0xb78440: si el grupo está en la lista w8 del `Exercise` → **1.0**; si está en la lista w9 → **0.5**; si no → **0.0** (pool[5539]). INFERIDO: w8 = músculos primarios, w9 = secundarios.
- La ventana del panel la fija `DataExperienceGraphSettings` (sin instancia constante por defecto en el snapshot; sólo `toJson` @0x953a60) → DESCONOCIDO.

## C.F8 Tiempos de la creación de programas (recordatorio, OBSERVADO)
90 s / 180 s por categoría de ejercicio (@0xd19364), 120 s de descanso y 120 s de calentamiento salvo `WarmUpAutomation.none` (@0xc8efe4, @0xd19408); `spillover` con tolerancia 1.25 y tope de 100 iteraciones (@0xd1cb14).

## C.F9 Selección automática de reps totales y peso (`getAutoTargetTotalRepsAndWeight`) — OBSERVADO (estructura y constantes) / INFERIDO (nombres de campos)

- Código: @0xc7eb60 (2844 B; `dis_wk\dis_c7eb60.txt`), closures @0xc7f6e4–0xc7fa44, ayudantes @0xc7f67c (peso disponible ≥ objetivo), @0xc7e71c (inversa de la curva), @0xc06910 (`Range`). Firma: `({bool checkRepRange = true, groupId})` → `(int, Weight)?` (nombres en pool[31600]/[31601]; el valor por defecto es el objeto `true` de THR+0x48, el mismo que devuelven los predicados).
- Entradas (S = `this.w5`, estado del objetivo del ejercicio; nombres INFERIDOS por su uso): `rir = S.w19` (int; se resta a las reps totales antes de comparar con el rango), `T = S.w20` (reps totales objetivo = reps + RIR), `repRange = S.w13` (dos int64: mín en w1-2, máx en w3-4), `curve = S.w21.w5.w17` (`XRmCurve`), `offset = S.w21.w5.w12` (`Weight` que se resta a la carga de la curva), `S.w11` = closure «pesos disponibles del grupo», `S.w10` = grupo por defecto, `S.w4` = parámetro de conversión de cada peso (@0xc7c864), `S.w14` = bandera booleana, `S.w6` = registro anterior opcional (`S.w6.w7.w7.w3` = `Weight`).
- `W(n)` = carga de la curva x-RM para n reps totales (C.F1, @0xb68120 sin `progress`).

Algoritmo (orden y constantes OBSERVADOS):
1. `L = List.of(S.w11(groupId ?? S.w10).map((x) => conv(x, S.w4)))..sort()` — pesos disponibles ordenados (`map` → `MappedListIterable`, `List.of` @0xc0bd40, `sort` @0x78ef70).
2. Atajo «mantener el peso anterior», sólo si `groupId == null`, `S.w14 == true` y `S.w6 != null`: `w = S.w6.w7.w7.w3`; **`n = ceil( ln((w − curve.w4)/curve.w3) / ln(curve.e) )`** (inversa de `W(n) = w3·eⁿ + w4`; `LibcLog` THR+0x3b0, `LibcCeil` THR+0x384); si `n − rir ∈ [repRange.min, repRange.max]` → devuelve `(n, w − offset)`.
3. `ideal = L.firstWhereOrNull((x) => x > W(T) − offset || x == W(T) − offset)` (@0xc7f67c + closure @0xc7f6e4; `==` = `Weight.==` @0x12bd334) → el menor peso disponible ≥ peso ideal. Si es null → devuelve null.
4. `cand(i) = (i, L.firstWhereOrNull((x) => x ≥ W(i) − offset))` (closure @0xc7f844; tipo `(int, Weight?)`).
5. Primera pasada (cargas ≥ ideal): `R1 = Range(rir, T, inclusiveMax: true).map(cand).whereType<(int, Weight)>()` (`WhereTypeIterable` → descarta reps sin peso disponible) `.fold(<(int,Weight)>[], (acc, e) => acc.lastOrNull?.$2 == e.$2 ? acc : acc..add(e))` (closure @0xc7f998: deja una sola entrada por peso, la de menos reps; `fold` INFERIDO por la forma de la llamada) `.where((e) => e.$1 > rir)` (@0xc7f934) `.where((e) => !checkRepRange || e.$1 − rir ∈ repRange)` (@0xc7f788) `.firstWhereOrNull((e) => e.$2 == ideal)` (@0xc7f8f8).
6. Si `R1 != null && R1.$1 == T` → devuelve `R1` (el peso ideal existe para las reps objetivo).
7. Segunda pasada (cargas < ideal): `R2 = Range(T, rir + 20, inclusiveMax: true).map(cand).whereType<(int, Weight)>().where(mismo filtro de rango).firstWhereOrNull((e) => e.$2 != ideal)` (@0xc7f744 es la negación del predicado anterior; el tope **20** = inmediato smi 40 en @0xc7f1a4).
8. Si `R2 == null` → `R1`; si `R1 == null` → `R2`; si no, **`score(R) = |R.$1 − T| · (|W(R.$1) − offset − R.$2| + 1)`** (es C.F4 inlineada) y devuelve `score(R1) < score(R2) ? R1 : R2`.

Comprobación numérica (con C.F1, tren superior, e1RM 111.89 kg, offset 0, rango de reps [3, 8], T = 7 = 5 reps + RIR 2): `W(n) = 111.89·(0.2 + 0.8·0.965^(n−1))` → W(2…9) = 108.8, 105.7, 102.8, 100.0, 97.3, 94.7, 92.1, 89.7. Con discos de 2.5 kg: `ideal = 95`; pasada 1 (i = 2…7) → pesos 110, 107.5, 105, 100, 97.5, 95; `R1 = (7, 95)` y `R1.$1 == T` → resultado `(7 reps totales, 95 kg)` = 5 reps a RIR 2. Si sólo existieran 90, 100 y 110 kg: `ideal = 100`; pasada 1 → (2,110), (3,110), (4,110), (5,100), (6,100), (7,100) → deduplicada (2,110), (5,100) → `i > rir` deja (5,100) → `R1 = (5, 100)`, distinto de T; pasada 2 (i = 7…22): (7,100), (8,100), (9,90)… → primera carga ≠ 100: `R2 = (9, 90)`; `score(R1) = |5−7|·(|100.0 − 100| + 1) = 2.0`, `score(R2) = |9−7|·(|89.7 − 90| + 1) = 2.6` → devuelve `(5, 100)` = 3 reps a RIR 2 con 100 kg.

## C.F10 `Range(start, end, {inclusiveMax = true, step = 1})` (@0xc06910) — OBSERVADO
Iterable vacío si alguno de los tres valores no es finito, si `step ≤ 0` o si `end < start`. `n = (end − start) ~/ step + (inclusiveMax ? 1 : (resto == 0 ? 0 : 1))`; elemento `i` = `start + i·step` (`Iterable.generate`, @0x900688). Para doubles escala los tres valores por `10^decimales` (máximo de decimales de start/end/step, `Range._decimalPlaces`), opera en enteros y divide al final (closure @0xc07180).

## C.F11 Pendiente / DESCONOCIDO
- `getProgressionSet` (3976 B, @0xc7fcac): listado generado pero no leído; usa C.F1–C.F4 y C.F9.
- Tabla de series/reps/RIR por objetivo en la creación de programas (closure de 4556 B @0xd1a368).
- Nombres reales de los campos de S (`w6`, `w14`, `w19`, `w20`) y del `offset` (w12); el modo de redondeo (`round`/`ceil`) de las series de descarga.
- Ventana temporal del panel de grupos musculares (C.F7).
