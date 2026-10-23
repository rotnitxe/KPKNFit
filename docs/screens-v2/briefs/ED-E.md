# ED-E · Hojas del editor (D1–D8) sobre los motores nuevos

**Worktree:** `C:\kw\u12`, rama `sv2/motores`. Continúa encima de MOT (`735dd49d2`): lee `docs/screens-v2/reports/MOT.md`. Trabaja **solo** ahí.

En paralelo trabajan otros agentes:
- `u14`: marco del editor (`SessionEditorScreen`); no lo reescribas.
- `u15`: catálogo.
- `u11`: sesión en vivo.
- `u13`: programas.

**Lee primero:** `CLAUDE.md`, `docs/screens-v2/PLAN_IMPLEMENTACION.md` (leyes), `docs/screens-v2/DECISIONES_PROTOTIPOS_2026-10-20.md` (D2, D3 y D4), `docs/screens-v2/maps/MAPA_editor_catalogo.md` y `docs/screens-v2/audits/AUD1_motores_editor.md` §1–§2.

**Tableros aprobados** (solo lectura): `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`, con **D1-AccionesRapidas, D2-Impacto, D3-Sugerencias, D4-Plantillas, D5-Reglas, D6-Tiempo, D7-Versiones y D8-Transferir**. Sus informes (`INFORME_R1.txt`, `INFORME_R2.txt`) dicen de qué código sale cada valor.

## Qué construir

- **D1 · Acciones rápidas**, como en el tablero.
- **D2 · Impacto**, con los valores del motor de MOT: «<1», cero y «sin estimación», más el cardio en los totales.
- **D3 · Sugerencias**:
  - como mucho 3 avisos, sin alarmismo y con cada acción verificada (motor de MOT);
  - el diseño del tablero con su lenguaje.
- **D4 · Plantillas**:
  - el buscador de plantillas en primer plano;
  - «Mis plantillas» como **botón** que abre su lista, no desplegada.
- **D5 · Reglas** (filas plegadas), **D6 · Tiempo**, **D7 · Versiones** y **D8 · Transferir** (solo «Copiar»), según sus tableros.

**Leyes:**
- Refinar, no quitar: ninguna función desaparece.
- No inventes opciones.
- Objetivos táctiles de al menos 44–48 dp.
- Paleta y tipografía KPKN.
- Un solo aviso por estado, sin solapes.

## Pruebas

- Compose/Robolectric de cada hoja.
- Renders a 360 × 880 con el arnés existente.
- Adapta las pruebas que dependan del diseño anterior sin rebajar aserciones sin explicarlo.

## Reglas

- **Gradle:**
  - solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u12/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`, en primer plano (hay cola);
  - solo `:app:compileBaseDebugKotlin`, `:app:compileBaseDebugAndroidTestKotlin` y `:app:testBaseDebugUnitTest --tests` filtrado a `screens.sessioneditor.*` más tus pruebas nuevas;
  - no ejecutes la batería completa.
- **Prohibido:** `--stop`, matar java, un `GRADLE_USER_HOME` paralelo, teléfono, adb y push.
- **Commits** en español, que terminen con `Co-Authored-By: Codex <noreply@openai.com>`.
- **Informe** en `docs/screens-v2/reports/ED-E.md`.
