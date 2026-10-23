# G · Otros editores (G1, G2, G3, G4)

**Worktree:** `C:\kw\u16`, rama `sv2/otros`, nacida de `feat/screens-v2` @ `6e0853dbd`, que ya incluye CAL1–CAL3, LV-A, LV-B, MOT y PROG. Trabaja **solo** ahí.

Otros agentes trabajan en paralelo y tocan zonas vecinas:
- `u11`: LV-C, las tarjetas de aproximación y movilidad en vivo. No toques `WarmupMobilityLiveCards.kt`, `PreparationCarouselCards.kt` ni `WarmupRules.kt`.
- `u12`: hojas y motores del editor (ED-E).
- `u13`: TM y 1RM (FIX-TM).
- `u14`: marco del editor de sesiones (ED-A).
- `u15`: catálogo (CAT-A).

Si alguno de tus cambios cae en esas zonas, que sea mínimo y anótalo en «Riesgos de integración».

## Qué leer

- `C:\kw\u16\CLAUDE.md`.
- En `docs/screens-v2/`:
  - `PLAN_IMPLEMENTACION.md`, por sus leyes y por la regla del **esquema de Room congelado**;
  - `DECISIONES_PROTOTIPOS_2026-10-20.md`: G1–G4 están aprobados, con «confirmar antes de aplicar» en G4 y «descanso y flechas» en G1;
  - `maps/MAPA_editor_catalogo.md`;
  - `reports/LV-A.md` y `reports/LV-B.md`, para el marco en vivo actual.
- Los tableros aprobados, que mandan en aspecto e interacción, en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`:
  - **G1-EditarSeriesVivo.dc.html**
  - **G2-EditarCardioVivo.dc.html**
  - **G3-Importador.dc.html**
  - **G4-WizardCambiar.dc.html**

  Son HTML autocontenidos, con la lógica en `<script type="text/x-dc">`, y de solo lectura. Si puedes, ábrelos en un navegador headless; si no, lee el HTML.

## Qué construir

Primero localiza en el código la pantalla actual de cada tablero, con su archivo y línea. Después refínala hasta que se parezca al tablero. **Refinar, no quitar:** ninguna función actual desaparece, y si cambia de sitio se migra y se prueba.

1. **G1 · Editar series en vivo.** Es la hoja que se abre desde la sesión en vivo.
   - Zona «Ejercicio», que vale para todas las series.
   - Lista compacta de series.
   - Descanso y flechas, como en el tablero.
   - Los valores salen del estado real de la sesión.
2. **G2 · Editar cardio en vivo.** Hoja con la prescripción en grande y las plantillas, como en el tablero, conectada al cardio real de la sesión.
3. **G3 · Importador de programas.**
   - Fila del ejercicio abierto.
   - Panel «Ajusta el ejercicio».
   - Línea de deshacer.
   - Isla inferior con «Añadir ejercicio», que abre el catálogo contextual, y «Guardar programa».
   - Mantén el motor de importación actual: el cambio es de interfaz.
4. **G4 · Cambiar en el asistente.**
   - Panel «Cambiar» sobre el día desplegado del plan, con el aspecto del reemplazo del tablero.
   - Confirmar antes de aplicar.
   - Usa la sustitución que ya exista; no inventes alternativas.

**Leyes:**
- No inventes opciones ni números: todo sale del código o del tablero aprobado.
- Objetivos táctiles de al menos 48 dp.
- Paleta KPKN: fondo #000, módulos #111, tinta #F2EEE6; músculo #F49A6E, energía #F7CF73, columna #8FB2FF, mente #C9B8FF, ok #43D18C, peligro #EF6F64. Fuentes Syne e Inter.
- **Esquema de Room congelado:** no toques entidades, migraciones, `schemas/` ni la versión de `KpknDatabase`.

## Pruebas

- **Robolectric/Compose:** por tablero, cada control visible y funcionando, con sus valores reales; deshacer en G3; confirmar y cancelar en G4; las funciones anteriores, en su sitio nuevo.
- **Renders de revisión** con el arnés que ya exista (`*RenderSheetTest`, `KPKN_RENDER_DIR`): G1, G2, G3 y G4 a 360 × 880 y a 360 × 740.
- Adapta las pruebas existentes que dependan del diseño anterior, sin rebajar aserciones sin explicarlo.

## Gradle

- Solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u16/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`. La cola es global y compartida: es normal esperar.
- Tu Bash corta a los 10 min. Lanza Gradle en segundo plano con un log (`C:/kw/logs/g-<n>.log`) y espera con bucles `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" C:/kw/logs/g-<n>.log; do sleep 20; done'`, repetidos hasta que termine. Nunca dos corridas tuyas a la vez.
- Ejecuta `:app:compileBaseDebugKotlin`, `:app:compileBaseDebugAndroidTestKotlin` y `:app:testBaseDebugUnitTest`, este con `--tests` de los paquetes que toques (por ejemplo `screens.workout.*`, `screens.programs.*`, `screens.onboarding.*`) y tus pruebas. Junta las tareas en una sola corrida cuando puedas. Sin la batería completa.
- Nunca `--stop`, nunca matar java. Sin teléfono, sin adb, sin emulador y sin push.

## Commits e informe

- Commits por tablero, en español, terminados en `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. `git add` solo de tus rutas.
- Informe en `docs/screens-v2/reports/G.md`:
  - por tablero, qué pantalla era (archivo:línea) y qué cambió;
  - qué funciones se migraron y a dónde;
  - las pruebas, con su comando y su resultado;
  - las rutas de los renders;
  - los riesgos de integración.
