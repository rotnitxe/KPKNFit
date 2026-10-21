# T5b · Progreso por ejercicio y récords

**Prioridad P1 · esfuerzo M.** Informe §4.6 (V5, V6), §5.4. Depende de **T3** (`StrengthModel`) y **T5a** (rutas y bloque «Progreso» de Perfil), ya integrados.

## Por qué
Después de borrar `ProgressView`/`HistoryView` (commit `d978c8a4b`), KPKN solo tiene piezas sueltas: historial de 10 sesiones, tabla de RM, mini-gráfica al cerrar la sesión y «Ejercicios estrella» con una fórmula distinta. No hay **una pantalla de progreso por ejercicio** con métricas elegibles, ventanas y récords, y un mismo levantamiento muestra e1RM distintos según la pantalla (T3 lo unifica). Hay que dar esa vista, accesible desde la ficha del ejercicio y desde Perfil, **comparable** (sin mezclar modalidades de carga) y explicable.

## Reglas de significado
- **Un solo e1RM:** `StrengthModel` (T3), con RIR/RPE cuando existan. Nada de fórmulas locales.
- **Comparable o no se compara:** usa `domain/workout/WorkoutPerformanceHomologationEngine` para tratar carga externa, peso corporal, lastre, asistencia, tiempo y distancia. Una **reducción de asistencia es progreso**, no una bajada de fuerza porque bajen los kg introducidos. Si el rendimiento no es comparable (cambio de implemento o de variante), la serie se corta y se rotula «no comparable con lo anterior»; nunca se unen puntos de modalidades distintas.
- **Identidad:** el ejercicio es una **configuración exacta del catálogo** cuando la haya (si no, la definición, y si no, el nombre normalizado); no mezcles variantes.
- Series saltadas, calentamientos y series sin carga/reps válidas **no cuentan** (di la política en pantalla en una línea).

## Cambio exacto
1. **Dominio (`domain/training/ExerciseProgress.kt`, puro):** a partir del historial de registros produce, por ejercicio e intervalo, una serie de puntos por sesión para las métricas: **e1RM**, **mejor serie (kg × reps)**, **peso máximo**, **repeticiones máximas**, **volumen de la sesión (kg)** y **series de la sesión**; y, para modos de tiempo/distancia, **duración** o **distancia**. Más los **récords** (mejor e1RM, mayor peso, más repeticiones con una carga, mayor volumen de sesión) con fecha y sesión, y las marcas de «PR» sobre la serie. Referencia de solo lectura: `C:\kw\ref\mf-wip\untracked\android-native\app\src\main\java\com\example\kpkn\domain\training\PerformanceMetricProjection.kt` y su test (mismo problema); porta a mano lo que encaje con el código actual y este contrato.
2. **Pantalla `screens/progress/ExerciseProgressScreen` + ViewModel:**
   - Cabecera: nombre y configuración del ejercicio, selector de **métrica** (chips: e1RM · Mejor serie · Peso máx. · Reps máx. · Volumen · Series, y duración/distancia si aplica) y de **rango** (1 mes · 3 meses · 1 año · Todo).
   - **Gráfica** con Canvas (`docs/android/SKILL_COMPOSE_CANVAS.md`): puntos por sesión, línea de tendencia suave opcional, marcas de PR, cortes «no comparable», ejes con unidades; toque en un punto → tarjeta con fecha, sesión, series y el cálculo (p. ej. «100 kg × 5 @ RIR 2 → e1RM 118 kg»). Resumen para TalkBack.
   - **Récords** (módulo de datos con contenedor) y **lista de sesiones** recientes con sus mejores series.
   - Estados: sin datos suficientes (< 2 sesiones comparables: dilo y muestra lo que haya), cargando, con datos.
3. **Entradas:** (a) la **ficha del ejercicio** (`screens/sessioneditor/components/ExerciseCatalogInfoDialog.kt`/`ExerciseFicha*.kt`): un botón discreto «Ver progreso»; (b) **Perfil**: en el bloque «Progreso» de T5a, una tarjeta «Progreso por ejercicio» con los ejercicios más entrenados, y que «Ejercicios estrella» abra esta pantalla al tocar (sin cambiar lo que muestra hoy salvo usar el e1RM unificado). Ruta nueva `KpknRoute.ExerciseProgress` (con el identificador del ejercicio).
4. **Diseño:** KPKN v2 (`docs/design/KPKN_UI_V2.md`, `S_COMUN.md`), español, objetivos ≥ 48 dp, `reducedMotion`. Hoja de render (PNG a 360 dp, 100 % y 130 %): fuerza con PR, peso corporal, asistido, tiempo, sin datos, con corte «no comparable». Máx. 3 rondas.
5. **Vista previa en dispositivo (COMUN.md):** `ExerciseProgressHarnessActivity` con `state` = `strength` | `bodyweight` | `assisted` | `timed` | `sparse` | `empty` | `broken_lineage` y datos en memoria.

## Archivos permitidos
- Nuevos: `domain/training/ExerciseProgress.kt`, `screens/progress/ExerciseProgress*`, `app/src/debug/.../ExerciseProgressHarnessActivity.kt` + bloque de manifiesto; tests y hoja de render.
- `screens/profile/ProfileScreen.kt` (tarjeta y apertura desde «Ejercicios estrella»), `screens/sessioneditor/components/ExerciseCatalogInfoDialog.kt` y `ExerciseFicha*.kt` (solo el botón), `navigation/Navigation.kt`, `MainActivity.kt` (ruta nueva).
- `docs/macrofactor-parity/CAMBIOS_VISIBLES.md`.

## Pruebas obligatorias
- El e1RM de la serie coincide con `StrengthModel` para el mismo set (con y sin RIR).
- Récords y marcas de PR: el PR de la gráfica es el del módulo de récords; un PR empatado no se duplica; un PR en una sesión posterior a una bajada se marca.
- Modalidades: peso corporal puro, lastre (+kg), asistido (−kg → bajar asistencia = progreso), tiempo y distancia; sin mezclar modalidades en una misma serie (corte «no comparable»).
- Series saltadas y calentamientos no cuentan; sesión con series válidas y no válidas mezcladas.
- Rangos de tiempo: 1 mes / 3 meses / 1 año / todo con zona horaria explícita.
- Identidad: dos configuraciones del mismo movimiento (p. ej. barra vs mancuernas) no se mezclan.
- ViewModel: cambiar métrica/rango recalcula; < 2 sesiones comparables → estado «sin datos suficientes» sin romper.

## Comandos
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.*" --tests "com.example.kpkn.domain.performance.*" --tests "com.example.kpkn.domain.workout.*" --tests "com.example.kpkn.screens.progress.*" --tests "com.example.kpkn.screens.profile.*"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/mfp/T5b` con `--tests "com.example.kpkn.render.ExerciseProgressRenderSheetTest"`.

## Fuera de alcance
Cambiar el modelo de fuerza (T3), el panel por músculo (T5a), AUGE, la forma de guardar los registros, o comparar entre personas.
