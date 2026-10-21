# P2-Ta · Myo-reps como preset de rest-pause y discos por lado en la tarjeta de la serie

**Prioridad P2 · esfuerzo M.** Informe §4.7 (S7), §4.5 (P5). Depende de **T1** (inventario resuelto) y **T3**, ya integrados. Pantalla de la sesión en vivo con leyes visuales estrictas: **refinar, no quitar** y «la tarjeta nunca cambia de tamaño».

## Parte A — Myo-reps (S7)
KPKN tiene drop-set y rest-pause pero no myo-reps; el reverso de la tarjeta (`screens/workout/components/SetCardTechniqueBack.kt`) lo dice: `TechniqueSegments = listOf("Normal", "Drop-set", "Rest-pause")` («sin Myo-reps: no existe en la app»). El rest-pause ya soporta una serie principal y hasta 5 eslabones (`ExerciseSet.isRestPause`, `restPauses: List<RestPauseData>`, `RestPausePlanDefaults`: 1–5, 2 por defecto) con su registro, su fatiga en AUGE y su conteo de volumen. Las myo-reps son exactamente eso con otros valores: **una serie de activación cerca del fallo y mini-series cortas con 15–20 s de pausa**.

**Decisión D13 de la ola (README; bajo riesgo): NO añadas un valor nuevo a `TechniqueType` ni una estructura paralela** (hay ~140 usos de rest-pause y `when` exhaustivos que se romperían). Impleméntalo como un **preset/variante de rest-pause**:
- Un **preset «Myo-reps»** que rellena el rest-pause con: 4 mini-series (rango permitido 1–5), 3–5 repeticiones por mini-serie (por defecto 4) y 15 s de pausa (rango 10–30 s). Marca la variante con un parámetro/campo con valor por defecto en el JSON (`style = "myo"` o equivalente) para que la UI la reconozca sin afectar a los lectores existentes (JSON antiguo sigue leyéndose igual).
- **Editor** (`screens/sessioneditor/components/InlineSetRowTechniqueChips.kt` y vecinos): un chip «Myo-reps» junto a los de rest-pause/drop-set que aplica el preset (editable después: número de mini-series, repeticiones y pausa).
- **Sesión en vivo** (`SetCardTechniqueBack`): un cuarto segmento «Myo-reps» que usa el mismo flujo y estados que rest-pause (cadena Principal → Mini 1 → Mini 2 …), con los textos de la explicación adaptados («Serie de activación y mini-series de pocas repeticiones con pausas cortas»), el temporizador de la pausa corta y el registro de cada mini-serie. Sin cambiar el tamaño de la tarjeta.
- **Conteo:** un set con myo-reps cuenta para volumen y fatiga **igual que un rest-pause** (no inventes una equivalencia nueva); documenta cómo cuenta en el informe. Resumen/historial: se muestra como «Myo-reps» con sus mini-series.
- **Guiado por voz / asistente** (`WorkoutVoiceInput`, `GuidedTechniqueFlow`…): si ya entienden rest-pause, deben entender el preset sin código nuevo; si hay cadenas de texto que dicen «rest-pause», que muestren «myo-reps» cuando la variante sea myo.

## Parte B — Discos por lado en la tarjeta de la serie (P5)
`domain/calculations/PlateCalculator.kt` (programación dinámica exacta) solo lo usan los calentamientos (`domain/training/WarmupFeasibility.kt`) y no tiene UI. Para ejercicios de **barra con carga total externa** (misma convención que usa el runtime nativo: `LoadQuantityConvention.TOTAL_EXTERNAL`/`StockKind.BARBELL`) y con inventario declarado (T1: barra + discos), muestra en la tarjeta de la serie, en el sitio donde ya aparece la carga (etiqueta secundaria de la portada de la tarjeta), **«por lado: 20 + 5 + 1,25»**:
- La carga mostrada debe coincidir con los discos listados (barra + 2 × suma por lado = carga). Si la carga prescrita no se puede montar exacta con el material, muestra la más cercana montable y dilo con tacto («más cercana montable»); nunca inventes discos que el inventario no tiene.
- Sin inventario declarado, sin barra, mancuernas, máquinas o peso corporal: no se muestra nada (sin hueco, sin mensaje de relleno).
- **No cambies el tamaño de la tarjeta** ni el de sus controles (ley 1 del lenguaje de sesión); mira `docs/screens-v1/briefs/S_COMUN.md` y `docs/design/KPKN_UI_V2.md` antes de tocar. Si el espacio no alcanza a 360 dp al 130 %, abrevia con criterio (p. ej. agrupa «2 × 20 + 5») antes de recortar.

## Vista previa y render
Hoja de render (PNG a 360 dp, 100 % y 130 %): tarjeta con y sin «por lado», con el reverso de técnicas en los cuatro segmentos, el editor con el chip nuevo. `MyoRepsPlatesHarnessActivity` (COMUN.md) con `state` = `myo_back` | `plates` | `plates_unreachable` | `no_inventory`.

## Archivos permitidos
- `screens/workout/components/SetCardTechniqueBack.kt`, `SetExecutionCard.kt` (solo la etiqueta de discos y los textos), `GuidedTechniquePanel.kt`/`GuidedTechniqueFlow.kt` (solo textos), `screens/sessioneditor/components/InlineSetRowTechniqueChips.kt`, `data/models/Session.kt` (solo un campo con valor por defecto para la variante), `domain/workout/ScheduledTechniqueExecution.kt` (solo si el preset lo necesita).
- Nuevos: `domain/training/PlateLoadLabel.kt` (puro: carga + inventario → texto/lista de discos por lado), tests, hoja de render, `app/src/debug/.../MyoRepsPlatesHarnessActivity.kt` + bloque de manifiesto.
- `docs/macrofactor-parity/CAMBIOS_VISIBLES.md`.

## Pruebas obligatorias
- Preset: aplica 4 × 4 reps con 15 s; editable; JSON antiguo de rest-pause se lee y se guarda idéntico (ida y vuelta); la variante myo sobrevive a guardar/reabrir.
- Registro: cada mini-serie se registra como un eslabón de rest-pause; volumen y fatiga iguales a rest-pause con los mismos datos (test comparativo).
- Discos: 100 kg con barra de 20 kg y discos 25/20/15/10/5/2,5/1,25 → «por lado: 25 + 10 + 5» (40 por lado); 62,5 kg → «por lado: 20 + 1,25»; carga inmontable → la más cercana con el aviso; sin inventario → nada; mancuerna/máquina/peso corporal → nada. La suma de discos × 2 + barra = carga mostrada.
- Regresión: tests de `screens.workout.*` y `screens.sessioneditor.*` sin cambiar expectativas ajenas (ver COMUN.md «Sin efectos colaterales»).

## Comandos
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.*" --tests "com.example.kpkn.domain.workout.*" --tests "com.example.kpkn.domain.calculations.*" --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.screens.sessioneditor.*"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/mfp/P2Ta` con `--tests "com.example.kpkn.render.MyoRepsPlatesRenderSheetTest"`.

## Fuera de alcance
Un nuevo `TechniqueType`; cambiar el cálculo de fatiga de AUGE; el calentamiento (P2-Tb solo lo verifica); tocar el tamaño de la tarjeta.
