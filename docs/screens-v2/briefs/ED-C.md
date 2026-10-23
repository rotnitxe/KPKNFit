# ED-C · Aproximación y movilidad en el editor (H1, H2, H3, H4, B6, D9)

**Worktree:** `C:\kw\u18`, rama `sv2/prep`, nacida de `sv2/editor` @ `8f44b089d`, es decir, encima de ED-A (marco nuevo del editor). Trabaja **solo** ahí.

En paralelo trabajan:
- `u14`: ED-B, el ejercicio abierto, el foco de la serie, los modos y CAL4. Su zona: `InlineSetRow`, los focos de serie, los diálogos y las opciones del ejercicio.
- `u17`: ED-D, cardio y superseries.
- `u12`: ED-E, hojas y motores.
- `u15`: CAT-A, el catálogo.
- `u16`: G.
- `u11`: LV-C, aproximación y movilidad **en vivo** (H5 y H6). No toques `WarmupMobilityLiveCards.kt` ni `PreparationCarouselCards.kt`.

Si tienes que tocar `ExerciseEditorCard`, limítate al bloque de preparación y anótalo en «Riesgos de integración».

## Qué leer

- `C:\kw\u18\CLAUDE.md`.
- En `docs/screens-v2/`:
  - `PLAN_IMPLEMENTACION.md`: las leyes y el **esquema de Room congelado**;
  - `DECISIONES_PROTOTIPOS_2026-10-20.md`, sobre todo la ronda 2 sobre «H1 y H2»: cada aproximación y cada movimiento se ven con **el mismo tamaño de ficha que las series efectivas**, sin botones sueltos fuera del ejercicio;
  - `reports/PROTO_R5_aprox_movilidad.md`;
  - `maps/MAPA_editor_catalogo.md`;
  - `reports/ED-A.md`.
- Los tableros, que mandan, en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`:
  - **H1-AproxEditor**
  - **H2-MovilidadEditor**
  - **H3-CatalogoMovilidad**
  - **H4-MovilidadDosis**
  - **B6-Preparacion**
  - **D9-Movilidad**

  Son de solo lectura. La lógica está en `<script type="text/x-dc">`.

## Qué construir

Por cada tablero, localiza la pantalla actual (archivo y línea) y refínala hasta el tablero. **Refinar, no quitar.**

1. **H1 · Aproximación en el editor.**
   - Fichas del mismo tamaño que las series efectivas, todo dentro del ejercicio.
   - Kg con coma decimal y porcentaje.
   - Añadir y quitar.
   - Conserva las reglas reales de `WarmupRules.kt` (≈ :6/14/23): aviso al 85 % y un máximo recomendado de max(4, 2 × series efectivas).
2. **H2 · Movilidad en el editor**, con las mismas fichas y todo dentro.
3. **H3 · Catálogo de movilidad.**
4. **H4 · Dosis de movilidad**, por tiempo o por repeticiones.
5. **B6 · Preparación del ejercicio.**
6. **D9 · Movilidad de la sesión.**

**Base de las aproximaciones.** El porcentaje de una aproximación se aplica al **peso de trabajo** de la serie efectiva objetivo, y solo a la capacidad si no hay peso de trabajo. En vivo, `WorkoutViewModel.recordWarmupHeavinessAndAwait` usa hoy primero la capacidad (1RM); LV-C lo está corrigiendo en u11. En el editor, usa la misma regla, y si existe una función común, úsala o deja una función pura que LV-C pueda adoptar al integrar.

**Leyes:**
- No inventes opciones ni números.
- Objetivos táctiles de al menos 48 dp.
- Paleta KPKN: fondo #000, módulos #111, tinta #F2EEE6; músculo #F49A6E, energía #F7CF73, columna #8FB2FF, mente #C9B8FF, ok #43D18C, peligro #EF6F64. Fuentes Syne e Inter.
- **Esquema de Room congelado.**

## Pruebas

- **Robolectric/Compose:**
  - las fichas de aproximación y movilidad miden igual que las de las series efectivas (mídelo);
  - ningún control queda fuera del ejercicio;
  - añadir, editar kg y %, y quitar;
  - el aviso del 85 % y el máximo recomendado;
  - la dosis por tiempo y por repeticiones;
  - el catálogo de movilidad;
  - la base de la aproximación con números: trabajo 100 kg y 1RM 130 → aproximación al 50 % = 50 kg.
- **Renders de revisión** con el arnés que ya exista (`*RenderSheetTest`, `KPKN_RENDER_DIR`): H1–H4, B6 y D9 a 360 × 880, con la fuente al 100 % y al 130 %. Guárdalos en `C:\kw\shots\edc\`.
- Adapta las pruebas existentes sin rebajar aserciones sin explicarlo.

## Gradle

- Solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u18/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`. La cola es global y compartida.
- Bash corta a los 10 min. Lanza Gradle en segundo plano con un log (`C:/kw/logs/edc-<n>.log`) y espera con bucles `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" C:/kw/logs/edc-<n>.log; do sleep 20; done'`, repetidos. Nunca dos corridas tuyas a la vez.
- Tareas: `:app:compileBaseDebugKotlin`, `:app:compileBaseDebugAndroidTestKotlin` y `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*"`, con tus filtros, juntas en una sola corrida cuando puedas.
- No edites `build.gradle.kts` para filtrar pruebas.
- `ExerciseCatalogImageCoverageTest` falla por el checkout disperso: es del entorno.
- Nunca `--stop`, nunca matar java. Sin teléfono, sin adb, sin emulador y sin push.

## Commits e informe

- Commits por tablero, en español, terminados en `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Haz `git add` solo de tus rutas. Este brief va en tu primer commit.
- Informe en `docs/screens-v2/reports/ED-C.md`:
  - por tablero, de dónde se partía y qué cambió;
  - las medidas de las fichas;
  - las pruebas, con su comando y su resultado;
  - los renders;
  - los riesgos de integración, sobre todo con ED-B y LV-C.
