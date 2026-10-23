# ED-D · Cardio y superseries en el editor (C2, C3, C4, C5, C6, C7)

**Worktree:** `C:\kw\u17`, rama `sv2/cardio`, nacida de `sv2/editor` @ `8f44b089d`, es decir, encima de ED-A (marco nuevo del editor). Trabaja **solo** ahí.

En paralelo trabajan:
- `u14`: ED-B, el ejercicio abierto, el foco de la serie, los modos y CAL4. Zona: `ExerciseEditorCard`, `InlineSetRow`, los focos y los diálogos.
- `u18`: ED-C, aproximación y movilidad en el editor.
- `u12`: ED-E, las hojas del editor (D1–D8) y los motores.
- `u15`: CAT-A, el catálogo.
- `u16`: G, otros editores.
- `u11`: la sesión en vivo.

Si tocas algo de esas zonas, que sea mínimo, y anótalo en «Riesgos de integración».

## Qué leer

- `C:\kw\u17\CLAUDE.md`.
- En `docs/screens-v2/`:
  - `PLAN_IMPLEMENTACION.md`: las leyes y el **esquema de Room congelado**;
  - `DECISIONES_PROTOTIPOS_2026-10-20.md`: C2–C7 están aprobados, con el creador único de superseries (C2) y los avisos del cardio continuo (C4);
  - `maps/MAPA_editor_catalogo.md` y `audits/AUD2_bugs_editor_catalogo.md`, cuyos arreglos ya están: no los rompas;
  - `reports/ED-A.md`: el marco que acaba de cambiar, con sus firmas nuevas.
- Los tableros, que mandan, en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`:
  - **C2-CrearSuperserie**
  - **C3-Rondas**
  - **C4-CardioContinuo**
  - **C5-CardioSeries**
  - **C6-CardioIntervalos**
  - **C7-SelectorCardio**
  - **C1-Superserie**, solo de referencia: ya lo hizo ED-A.

  Son HTML autocontenidos, con la lógica en `<script type="text/x-dc">`, y de solo lectura.

## Qué construir

Por cada tablero, localiza primero la pantalla actual (archivo y línea) y refínala hasta el tablero. **Refinar, no quitar:** ninguna función desaparece; si cambia de sitio, se migra y se prueba.

1. **C2 · Crear superserie**: un único creador de superseries para todos los puntos de entrada.
2. **C3 · Rondas y descansos**: rondas, descanso entre ejercicios y descanso tras la ronda, con los valores reales.
3. **C4 · Cardio continuo**, con sus avisos.
4. **C5 · Cardio en series (HIIT)**.
5. **C6 · Cardio en intervalos**.
6. **C7 · Selector de cardio**.

Desde ED-A, el cardio de la sesión ya tiene su apartado y su colocación «Al inicio / Al final». Respétalo. El total de tiempo y el impacto del cardio salen del motor general (MOT): no los dupliques.

**Leyes:**
- No inventes opciones ni números.
- Objetivos táctiles de al menos 48 dp.
- Paleta KPKN: fondo #000, módulos #111, tinta #F2EEE6; energía #F7CF73, columna #8FB2FF, mente #C9B8FF, ok #43D18C, peligro #EF6F64. Fuentes Syne e Inter.
- **Esquema de Room congelado:** no toques entidades, migraciones, `schemas/` ni la versión de la base.

## Pruebas

- **Robolectric/Compose**, por tablero: controles y valores reales; crear una superserie desde cada entrada; editar rondas y descansos; los tres tipos de cardio con sus campos y avisos; el selector; que las funciones anteriores siguen en su sitio nuevo.
- **Renders de revisión** con el arnés que ya exista (`*RenderSheetTest`, `KPKN_RENDER_DIR`): C2–C7 a 360 × 880, con la fuente al 100 % y al 130 %. Guárdalos en `C:\kw\shots\edd\`.
- Adapta las pruebas existentes que dependan del diseño anterior, sin rebajar aserciones sin explicarlo.

## Gradle

- Solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u17/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`. La cola es global y compartida.
- Bash corta a los 10 min. Lanza Gradle en segundo plano con un log (`C:/kw/logs/edd-<n>.log`) y espera con bucles `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" C:/kw/logs/edd-<n>.log; do sleep 20; done'`, repetidos hasta que termine. Nunca dos corridas tuyas a la vez.
- Tareas: `:app:compileBaseDebugKotlin`, `:app:compileBaseDebugAndroidTestKotlin` y `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*"`, con tus filtros. Júntalas en una sola corrida cuando puedas.
- **No** edites `build.gradle.kts` para filtrar pruebas: usa `--tests`.
- `ExerciseCatalogImageCoverageTest` falla en los worktrees dispersos porque falta `catalog/exercises/images/`. Es del entorno: no lo toques.
- Nunca `--stop`, nunca matar java. Sin teléfono, sin adb, sin emulador y sin push.

## Commits e informe

- Commits por tablero, en español, terminados en `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Haz `git add` solo de tus rutas. Este brief va en tu primer commit.
- Informe en `docs/screens-v2/reports/ED-D.md`:
  - por tablero, de dónde se partía y qué cambió;
  - qué funciones se migraron;
  - las pruebas, con su comando y su resultado;
  - los renders;
  - los riesgos de integración, en especial con ED-B, ED-C y ED-E.
