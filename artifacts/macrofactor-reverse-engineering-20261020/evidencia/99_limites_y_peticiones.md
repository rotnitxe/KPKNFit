# 99 — Límites de este análisis, herramientas usadas y peticiones (actualizado tras la ronda 2)

## 1. Inventario de entregables

| Entregable | Ruta (relativa a `<Base>\re\`) |
|---|---|
| Formato del snapshot y método | `00_formato_snapshot.md` |
| Herramientas propias (Python 3, biblioteca estándar; `disasm.py` necesita capstone) | `tools\dartsnap.py` (parser del snapshot), `tools\dump_model.py` (modelo de clases/funciones/campos/pool/estáticos), `tools\codescan.py` (escaneo ligero arm64/arm32), `tools\report.py` y `tools\digest_text.py` (digests), **`tools\disasm.py`** (ronda 2: desensamblador anotado arm64/arm32 con capstone: pool, stubs de la VM nombrados desde las raíces, tabla de despacho con origen 4096/1023, entradas de runtime THR+off, campos `wN`, dobles; opciones `--refs`, `--double`, `--pool`, `--poolfn`, `--dispatch`, `--fnrefs`, `--range`, `--callers`, `--callers-name`, `--vtable`, `--name`, `--layout`) |
| Modelos completos | `out_nu\`, `out_wk\` (`snapshot_info.json`, `roots_info.json`, `classes.json`, `functions.json`, `libraries.json`, `pool.json`, `static_fields.json`, `ranges_summary.json`, `ranges.pickle`) |
| Clases por app | `clases_nu.json`, `clases_wk.json` |
| Digests por sistema | `digests\A_nu*`, `B_nu*`, `C_nu*`, `C_wk*`, `C2_wk*`, `D_nu*`, `E_wk*` |
| Informes por sistema (ronda 1) | `sistemas\A_gasto_y_tendencia.md`, `B_coaching.md` (con §B.3 de la ronda 2), `C_entrenamiento.md`, `D_alimentos_ia.md`, `E_weight_stack_scan.md` |
| **Fórmulas (ronda 2)** | `sistemas\A_formulas_gasto_tendencia.md` (piso, BMR/Cunningham, núcleo V1/V2, EMA, Kalman V2, V3 parcial), `sistemas\C_formulas_entrenamiento.md` (curva x-RM, reps fatigantes, fatiga acumulada, ranking, calentamiento, descarga, ponderación muscular, selección automática reps/peso, `Range`) |
| Listados desensamblados | `dis_nu\dis_<addr>.txt` (93 funciones de Nutrition) y `dis_wk\dis_<addr>.txt` (40 de Workouts); cada línea lleva dirección, instrucción y anotación |
| Fuentes de referencia del SDK (sólo lectura, conservadas con autorización) | `ref_src\3.13.3\` (35 ficheros: los 33 de la ronda 1 más `runtime_entry_list.h` y `dispatch_table.cc`) |
| Entorno aislado | `<Base>\venv\` con capstone 5.0.7 (instalado por el coordinador); uso: `..\venv\Scripts\python.exe -I tools\disasm.py <so> <out_dir> [--opción …|<dir_salida> <addr>…]` |

## 2. Qué se consiguió

**Ronda 1 (OBSERVADO):** versión exacta del runtime (Dart 3.13.3 stable, hash `0451907c…`); parseo completo de ambos snapshots; clases, funciones supervivientes, campos, pool, estáticos, dispatch table, InstructionsTable; mapa `código → función` (57 384 rangos nu / 52 781 wk); enumeraciones, instancias const, firmas, nombres de argumentos con nombre y claves JSON.

**Ronda 2 (desensamblado con capstone; fórmulas cerradas):**

| Sistema | Fórmula | Nivel |
|---|---|---|
| A | Piso de gasto `max(598, 48.1·peso^0.783)` | OBSERVADO |
| A | TDEE inicial: Cunningham `500 + 22·min(LBM, 100)` × (`Activity` 1.2/1.4/1.6 + bono de ejercicio 0.1/0.2/0.3) | OBSERVADO |
| A | Núcleo de gasto V1/V2: ventana 10/25 días con ≥ 85 % de días presentes (0 < kcal ≤ 50 000), regresión lineal de la tendencia, 7700 kcal/kg al perder y 4400 al ganar, candidato = ingesta media − ΔE, rampa 0.33/0.66, paso máximo 75/30 kcal/día, suelo `max(750, 0.85·BMR)`, combinación (corta+2·larga)/3 o media, tres EMA α = 0.1 | OBSERVADO (semántica de `crossover` INFERIDA) |
| A | EMA `y = (1−α)·y₋₁ + α·x`, α = 0.1 (ema20) / 0.2222 (ema9) | OBSERVADO |
| A | Kalman V2: F con φₛ = e^(−ln2/30), φ_f = e^(−ln2/2); Q = diag(0.0025, q₂²(1−φₛ²), q₃²(1−φ_f²)) con q₂ = clamp(0.0008·W, 0.04, 0.12), q₃ = clamp(0.01·W, 0.6, 1.4); σ_obs = clamp(0.007·W, 0.45, 0.85)·escala; nivel inicial = mediana de los 7 primeros días (≥ 3 pesajes) | OBSERVADO (nombres de estado INFERIDOS) |
| A | V3: tres ejecuciones del núcleo con parámetros A/B/C (≈ ×1.0/×1.1/×0.9), `GoalAdjustmentBudget`, ventana 1–14 días, límites ±21.43 kcal (150/7) | OBSERVADO (parámetros) / DESCONOCIDO (fórmula) |
| B | Check-in semanal: `none`/`dismissed`/`now`/`future`, umbral `> 2` días, cadencia 7 días, regla `< 3` días, lunes por defecto, orden de módulos | OBSERVADO (semántica de fechas INFERIDA) |
| C | Curva x-RM: e = 0.965/0.975, `e1RM = W/(0.2 + 0.8·e^(n−1))`, inversa `n = ln((w − w4)/w3)/ln e` | OBSERVADO |
| C | Reps fatigantes `c + reps·e^(−0.5619·rir)` (0.582, −0.327, 1.718213); fatiga acumulada `−2.481·x^0.582/100` | OBSERVADO |
| C | Ranking `|reps − T|·(|W(reps) − offset − w| + 1)`; calentamiento [0.4, 0.6, 0.8, 0.95] con interpolación 0.6; descarga 0.8 en series (mín. 1) y en reps (`round`), `failure → standard`; ponderación muscular 1.0/0.5/0.0 | OBSERVADO |
| C | `getAutoTargetTotalRepsAndWeight`: lista ordenada de pesos disponibles, atajo «mantener peso», dos pasadas `Range(rir, T)` y `Range(T, rir + 20)`, deduplicación por peso, filtro de rango de reps (`checkRepRange`), desempate por ranking; `Range` con `inclusiveMax = true` | OBSERVADO (estructura) / INFERIDO (nombres de campos) |

## 3. Qué sigue DESCONOCIDO y enfoques agotados (tope: 3 por función)

| Pendiente | Enfoques usados | Qué haría falta |
|---|---|---|
| Fórmula cerrada de `ExpenditureAlgorithmV3` y `GoalAdjustmentBudget` (núcleo de 6,3 KB @0x12c46ac) | (1) constantes y llamadas del rango, (2) lectura de los ayudantes @0x12c5fe4/@0x12c62c0/@0x12c6344/@0x12c6d34, (3) combinador @0x12c442c y parámetros de las tres ejecuciones | Lectura manual completa del núcleo (≈ 1 500 instrucciones) o descompilador (Ghidra) |
| Bucle del Kalman V2 (ganancia, uso de R como σ o σ², horizonte del suavizador) | (1) constantes del driver @0xaa9dcc, (2) lectura de los constructores de F/Q/R, (3) `smoothStateToHorizon` sólo por firma | Lectura del driver de 12,5 KB |
| `crossover` y `MacdEntry` en V2 | (1) α = 2/7 observada, (2) closure booleano no seguido | Seguir el closure (timestamp, x3) y @0x12c1a58 |
| Pesos de la media ponderada de V1 | (1) sólo `AverageType.weighted` observado | Leer la rama `weighted` de `MovingAverage.call` |
| Umbrales de `partialLogging`, `loggingBreak`, `fasting` | (1) refs a los enums, (2) `--vtable` sobre cids 2415–2420 sin filtro, (3) con filtro anti-alineación: candidatos ajenos (streams, gradientes, `LogicalKeyboardKey`) | Ejecutar la app con logcat o localizar el *provider* que construye `CoachingModuleData` |
| Ventana temporal del panel de grupos musculares | (1) `--refs _DataExperienceGraphSettings` sin instancia const, (2) sólo `toJson` | Lectura del `provider` del panel o captura en el teléfono |
| `getProgressionSet` (3976 B) y tabla series/reps/RIR por objetivo (@0xd1a368) | listado generado, no leído (prioridad baja en el encargo) | Lectura manual (≈ 1 000 instrucciones cada uno) |
| Nombres reales de campos (`TdeeEntry`, `TrendWeightEntry` sí; estado del objetivo S y `MacroFactorUser` no) | `toJson` cuando existe | Esquema de la base de datos local (teléfono con root/backup) |

## 4. Herramientas

- **Instalado y suficiente:** capstone 5.0.7 en `<Base>\venv\` (autorizado por el usuario). Con `disasm.py` se cerraron todas las fórmulas de prioridad 1 y 3 y la regla de disparo del check-in; no hizo falta nada más.
- **No instalado ni necesario:** Ghidra (sólo aceleraría la lectura de los dos cuerpos grandes: núcleo V3 y driver Kalman), blutter (misma información que `dartsnap.py` + capstone, coste alto). Los símbolos DWARF (`app.android-arm64.symbols`) siguen siendo inobtenibles.
- No se ejecutó nada procedente de los XAPK: `disasm.py` sólo lee bytes del `.so`; Python se lanzó siempre con `-I` desde `<Base>\re\`.

## 5. Zona gris que debe revisar el usuario

`<Base>\re\ref_src\3.13.3\` contiene 35 ficheros fuente del SDK de Dart (tag 3.13.3, repositorio público) descargados con `curl` como texto de sólo lectura: 33 en la ronda 1 y, en la ronda 2, `runtime_entry_list.h` (para nombrar las entradas `THR+off`: `LibcPow`, `LibcExp`, `LibcLog`, `LibcFloor`, `LibcCeil`, `LibcRound`…) y `dispatch_table.cc` (origen de la tabla de despacho). El usuario autorizó conservarlos. No se compiló ni ejecutó nada de ahí.

## 6. ¿Aportaría algo explorar en el Galaxy Z Flip5?

Sí, pero datos de configuración y UX, no fórmulas (las fórmulas ya están cerradas por desensamblado). Requiere ejecutar las apps (prohibido en este encargo) y permiso explícito: pantallas de los módulos de coaching (cerraría los umbrales de registro parcial), ajustes de Smart Progression, panel muscular (ventana), tráfico a Typesense/Remote Config/Firebase AI (sistema D), `logcat` de los *loggers*, y el esquema local de `TdeeEntry`/`TrendWeightEntry`/`WorkoutSession` (nombres de campo) si hay backup depurable.

## 7. Siguiente paso recomendado

1. Si interesa V3 o el bucle del Kalman: dedicar una sesión a leer `dis_nu\dis_12c46ac.txt` y `dis_nu\dis_aa9dcc.txt` (ya generables con `disasm.py`), o autorizar Ghidra para descompilarlos.
2. Para los umbrales de coaching y la ventana del panel: captura en el teléfono (con permiso) en lugar de más desensamblado.
