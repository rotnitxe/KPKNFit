# ED-A · Marco del editor de sesiones (Main/A1, A2, A2b, A3, A4, A5, A6, C1)

**Worktree:** `C:\kw\u14`, rama `sv2/editor`, nacida de `feat/screens-v2` @ `373729ae0`. Trabaja **solo** ahí.

Otros agentes trabajan en paralelo y tocan zonas vecinas:
- `u11`: sesión en vivo y calibrador.
- `u12`: motores del editor (`SessionEditorAugeComputation`, sugerencias, creador). No reescribas esos motores; si tienes que tocar sus llamadas en la pantalla, hazlo de forma mínima.
- `u13`: programas.

## Qué leer

- `C:\kw\u14\CLAUDE.md`.
- `docs/screens-v2/PLAN_IMPLEMENTACION.md` (leyes) y `docs/screens-v2/DECISIONES_PROTOTIPOS_2026-10-20.md`, con las decisiones del usuario sobre cada tablero.
- `docs/screens-v2/maps/MAPA_editor_catalogo.md` (el mapa del código) y `docs/screens-v2/audits/AUD2_bugs_editor_catalogo.md`. Sus 28 arreglos ya están en la rama: no los rompas.
- Los tableros aprobados, que mandan en aspecto e interacción, en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`:
  - **Main.dc.html** (A1, editor de la sesión), **A2-Vacia**, **A2b-SoloCardio**, **A3-Arrastrar**, **A4-BorrarDeshacer**, **A5-Semana**, **A6-Estados** y **C1-Superserie**.
  - Los informes de cada ronda están en la carpeta de arriba (`INFORME_R1.txt`, `INFORME_R2.txt`, `INFORME_R4_A.txt`) y explican de qué código sale cada valor.
  - Para ver los tableros, ábrelos con un servidor local estático y un navegador headless, si tienes Playwright o Node. Si no, lee el HTML: cada tablero es un HTML autocontenido con su lógica en un `<script type="text/x-dc">`.

## Qué construir (decisiones del usuario)

1. **A1 · Editor de la sesión**:
   - **Sin RIR global**: fuera el chip «Fuerza · RIR 2» de la cabecera. Cada ejercicio y cada serie llevan su propio RIR o RPE.
   - Cabecera con día y semana, isla inferior (tiempo estimado, «+ Añadir», Asistente y Reglas) y grupos con sus ejercicios, con el aspecto de Main.
2. **A2 · Sesión vacía**:
   - Solo **dos** opciones con su símbolo animado: **Fuerza** y **Cardio**. Al tocar una aparece su apartado y la otra queda abajo como opción pendiente.
   - Solo si están las dos aparece la colocación del cardio, «Al inicio / Al final».
   - Sin descripciones que relacionen una con otra; nada de «puede ser toda tu sesión».
   - **A2b:** sesión solo de cardio.
3. **A3 · Arrastrar**, estable y preciso. Se puede mover libremente:
   - entre grupos;
   - fuera de un grupo;
   - dentro de un grupo;
   - a una superserie ya formada.

   Cada destino lleva su señal de soltado. Conserva el arreglo AUD2-03: si el destino desaparece, no se pierde el ejercicio.
4. **A4 · Borrar con deshacer**, con la ventana de 5 s de AUD2-01/02.
5. **A5 · Semana y variantes A/B/C/D**.
6. **A6 · Estados**: cargando, error y guardado bloqueado, con **un solo aviso por estado y sin solapes**.
7. **C1 · Superserie dentro de su grupo**: si una superserie está en un grupo, se muestra dentro de él, no aparte.

**Leyes:**
- Refinar, no quitar: ninguna función del editor desaparece. Si cambia de sitio, se migra y se prueba.
- No inventes opciones.
- Objetivos táctiles de al menos 44–48 dp.
- Paleta KPKN: fondo #000, módulos #111, tinta #F2EEE6; músculo #F49A6E, energía #F7CF73, columna #8FB2FF, mente #C9B8FF, ok #43D18C, peligro #EF6F64. Fuentes Syne e Inter.

## Pruebas

- **Robolectric/Compose:**
  - sesión vacía con sus dos caminos y la colocación;
  - solo cardio;
  - arrastre a los cuatro destinos;
  - borrar y deshacer;
  - semana y variantes;
  - cada estado de A6 con un solo aviso;
  - superserie dentro del grupo;
  - ausencia del chip de RIR global.
- **Renders de revisión** con el arnés que ya exista (`*RenderSheetTest`, `KPKN_RENDER_DIR`): Main, A2, A2b, A3 (destinos), A4, A5, A6 y C1 a 360 × 880.
- Adapta las pruebas existentes del editor que dependan del diseño anterior, sin rebajar aserciones sin explicarlo.

## Gradle (importante)

- Solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u14/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`. Hay cola: es normal esperar.
- Tu Bash corta a los 10 min. Lanza Gradle en segundo plano con un log (`C:/kw/logs/eda-<n>.log`) y espera con bucles `until grep -qE "BUILD (SUCCESSFUL|FAILED)" log; do sleep 20; done` de 9 minutos como máximo, repetidos. Nunca dos corridas tuyas a la vez.
- Ejecuta `:app:compileBaseDebugKotlin`, `:app:compileBaseDebugAndroidTestKotlin` y `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*"` junto con tus pruebas. No ejecutes la batería completa.
- Nunca `--stop` ni matar java. Sin teléfono ni adb. Sin push.

## Commits e informe

- Commits por tablero, en español, terminados en `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Informe en `docs/screens-v2/reports/ED-A.md`:
  - qué cambió por tablero;
  - qué funciones se migraron y a dónde;
  - las pruebas, con su comando y su resultado;
  - las rutas de los renders;
  - los riesgos de integración.
