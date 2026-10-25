# P2-Tb · Verificaciones de calentamiento y descarga nativa, e interruptores de entrenamiento sin interfaz

**Prioridad P2 · esfuerzo S–M.** Informe §4.7 (S2), §4.9 (H-g, H-e), §5.7–§5.8. Es un paquete de **verificación primero, cambio mínimo después**: el calentamiento (`domain/training/approach/*`) y la descarga son categoría 3 («no tocar»); aquí solo se prueban y se corrige lo que se demuestre defectuoso.

## 1. S2 — el calentamiento nunca pide más que el trabajo y siempre es montable
Contrato a demostrar con tests (`domain/training/approach/ApproachRamp.kt`, `ApproachPlanner.kt`, `WarmupFeasibility.kt`):
- **Ningún escalón de calentamiento supera el peso de trabajo** (ni lo iguala, salvo la regla que ya documente el código para cargas muy bajas).
- **Todos los escalones son cargables con el inventario declarado** (T1): barra, discos con cantidades, mancuernas en pares, máquinas con su incremento. Con inventario vacío conserva el comportamiento actual (no regresión).
- El tiempo total del calentamiento queda acotado (documenta el tope vigente).
Genera un banco de casos (pesos de trabajo de 20 a 250 kg; inventarios: comercial, casa con discos limitados, solo mancuernas, máquina de 5 kg) y comprueba el contrato. **Si hay un caso que lo viola**, corrígelo con el cambio mínimo, documenta el caso y la razón en el informe y añade su test. **Si no hay violaciones, no cambies código de producción**: deja los tests como red de seguridad y lo dices.

## 2. H-g — descarga nativa: coherencia de carga, series y RIR
Informe: «la semana 6 recibe la misma carga capturada, con RIR 4 y las mismas reps» (`domain/training/NativeWorkoutProgressionRuntime.kt` ≈ l. 1199-1217 y `data/protocols/definitions/NativeProfileSpec.kt` ≈ l. 218-219; nativa: series/2 y RIR 4). Averigua si es una descarga de **volumen** deliberada (series a la mitad, misma intensidad de carga, más reserva) y si la carga/reps/RIR resultantes son coherentes entre sí y con el resto del ciclo. Resultado esperado: o bien (a) está bien y lo dejas **documentado con un test que fija el comportamiento y un comentario que explica la intención**, o bien (b) hay una incoherencia concreta (p. ej. RIR 4 con reps y carga del tope del rango) y la corriges con el cambio mínimo y su test. No rediseñes la descarga.

## 3. H-e — interruptores de entrenamiento sin interfaz
En `data/models/Settings.kt`: `restTimerDefaultSeconds` (90, ya se lee en varios sitios), `restTimerAutoStart` (false, **sin lector alguno**) y `algorithmSettings.augeAutoDeload` (se lee en `domain/auge/AugeFatigueEngine.kt`, sin UI). Añade filas en la pantalla de ajustes de entrenamiento (busca dónde viven hoy los ajustes de entreno: `KpknRoute.SettingsTraining`, `screens/settings/*`; si esa ruta no tiene pantalla propia, un bloque nuevo «Entrenamiento» en `SettingsScreen` con el mismo patrón que el bloque «Material» de T1):
- **Descanso por defecto:** selector de 30 a 300 s en pasos de 15 (90 por defecto) con su descripción; debe ser exactamente el valor que ya usan los lectores.
- **Iniciar el descanso automáticamente al registrar una serie** (`restTimerAutoStart`): hoy NO lo lee nadie; implementa el lector en el flujo de la sesión en vivo (`screens/workout/WorkoutSetRecorder.kt` donde se arranca el descanso) **sin cambiar el comportamiento actual por defecto**: mira qué hace la app hoy al completar una serie (¿arranca el descanso siempre?) y haz que el valor por defecto del ajuste lo preserve (si hoy siempre arranca, el campo nuevo debe interpretarse de modo que el valor por defecto lo siga haciendo; documenta cómo resuelves la discrepancia con el `false` actual sin romper backups: p. ej. un campo nuevo con otro nombre y valor por defecto `true`, dejando el antiguo como legado).
- **Descarga automática sugerida por AUGE** (`augeAutoDeload`): fila con interruptor y una frase honesta de lo que hace (lee `AugeFatigueEngine` para describirlo con precisión).
Estilo KPKN v2 (`docs/design/KPKN_UI_V2.md`), español, objetivos ≥ 48 dp. Hoja de render (PNG a 360 dp, 100 % y 130 %) del bloque de ajustes y `TrainingSettingsHarnessActivity` (COMUN.md; `state` = `defaults` | `custom`).

## Archivos permitidos
- Tests nuevos en `src/test/.../domain/training/approach/` y `.../domain/training/` (calentamiento, descarga nativa); solo cambias código de `approach/*`, `NativeWorkoutProgressionRuntime.kt` o `NativeProfileSpec.kt` si DEMUESTRAS un defecto concreto.
- `data/models/Settings.kt` (solo campos nuevos con valor por defecto), `screens/settings/*` (bloque nuevo), `screens/workout/WorkoutSetRecorder.kt` (solo el lector de auto-inicio), `navigation/Navigation.kt`/`MainActivity.kt` solo si hace falta una ruta.
- `app/src/debug/.../TrainingSettingsHarnessActivity.kt` + bloque de manifiesto; hoja de render; `docs/macrofactor-parity/CAMBIOS_VISIBLES.md`.

## Pruebas obligatorias
- Banco del calentamiento (punto 1) con sus aserciones; casos de inventario vacío (regresión).
- Descarga nativa: test que fija el comportamiento (o su corrección) con valores escritos a mano.
- Ajustes: ida y vuelta de los tres valores por la copia JSON (`data/settings/SettingsJsonBackup.kt`) y por Room; el valor por defecto reproduce el comportamiento actual (test del lector de auto-inicio: con el valor por defecto, el descanso arranca exactamente cuando arrancaba antes).
- Regresión de `domain.training.*`, `domain.auge.*`, `screens.workout.*` y `screens.settings.*` sin cambiar expectativas ajenas (COMUN.md).

## Comandos
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.*" --tests "com.example.kpkn.domain.auge.*" --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.screens.settings.*" --tests "com.example.kpkn.data.settings.*"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/mfp/P2Tb` con `--tests "com.example.kpkn.render.TrainingSettingsRenderSheetTest"`.

## Fuera de alcance
Rediseñar el calentamiento o la descarga; AUGE; cambiar valores por defecto existentes.
