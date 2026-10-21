# LV-A · Tercio inferior de la sesión en vivo (V1), ruta de la sesión y migración del cockpit

Worktree `C:\kw\u11`, rama `feat/screens-v2`.

## Lectura previa

- `C:\kw\u11\CLAUDE.md` y `docs/screens-v2/PLAN_IMPLEMENTACION.md`, con sus leyes.
- `docs/screens-v2/reports/PROTO_R3_vivo_tercio_inferior.md`: la propuesta V1, la tabla de migración del cockpit y las evidencias de código.
- `docs/screens-v2/reports/PROTO_R4B_modos_vivo.md`.
- `docs/screens-v2/reports/QA_EMU_LQA1.md`.
- Tableros aprobados, en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`:
  - **V1-PropuestaLinea.dc.html**, el elegido por el usuario;
  - **V3-PantallaCorta.dc.html**, solo su mitad V1, la de 360 × 740;
  - **V4-RutaSesion.dc.html**;
  - **V5-MigracionCockpit.dc.html**;
  - B8b-SerieVivoROM.dc.html, solo como referencia del deslizador. Las tarjetas por modo van en LV-B.
- Ábrelos en un navegador local (`node` o Playwright) para ver su comportamiento: son la referencia de aspecto e interacción.

## Qué construir

1. **Línea de sesión V1** en lugar del stepper, el FAB de registrar y el roadmap separados de hoy.
   - Una sola isla de 112 dp de alto.
   - Contiene la preparación (aproximación y movilidad como pasos con su icono), las series del ejercicio y los ejercicios plegados.
   - El ✓ de registrar va **integrado en la línea**, en su extremo derecho.
   - El paso activo queda centrado y los lejanos se comprimen, incluso con 14 series.
   - Conserva todo lo de la línea actual:
     - añadir serie (con pulsación larga en series normales, `WorkoutV2Body.kt` ≈ :1552-1560);
     - gesto entre ejercicios (`WorkoutSetPager.kt` ≈ :895-920);
     - alcances de técnica;
     - lados L/R;
     - rondas de superserie.
2. **Cabecera.**
   - El modo de voz sube a la cabecera, junto a «Historia», que es un acceso nuevo a la historia del resumen final.
   - «Historial» pasa a ser un botón pequeño junto a «Modo visual».
   - Esa fila es dinámica y nunca se desborda hacia abajo: en pantalla corta usa iconos (V3).
   - El relator de arriba gana el espacio que liberan el cockpit y el FAB (V1).
3. **Retirar el cockpit de la sesión** (`components/WorkoutSessionCockpit.kt`, `WorkoutScreen.kt` ≈ :315 y :1331) y **migrar cada función sin perder ninguna**, según V5 y la tabla de PROTO_R3 §4:

   | Función | Destino | Qué conserva |
   |---|---|---|
   | Sesión | Hoja «Ruta» desde la línea (V4) | Reloj, métricas, ritmo, RINGS y selección de ejercicios o superseries (`CockpitSessionSummary.kt`) |
   | Hitos | Cabecera ⋯ → hoja Hitos | — |
   | Fotos | Cabecera ⋯ → álbum existente | Cámara, galería, pendientes, reintentos, selección, vista previa, análisis y borrar con deshacer |
   | Temporizador | Cabecera ⋯ → hoja | Iniciar, pausar y reiniciar |
   | Calculadora RM | Cabecera ⋯ → hoja | Tabla de 1 a 10 RM con el híbrido común |
   | Calorías | Cabecera ⋯ → hoja | Avisos cuando faltan datos |
   | Notas y tareas | Cabecera ⋯ → hoja | Borrador, guardado, última nota y tareas; la nota llega al resumen final |

   Ninguna función desaparece: antes de borrar código del cockpit, enumera sus funciones y comprueba que cada una tiene destino y prueba.
4. **Tamaños.**
   - Con 360 × 880 dp, la tarjeta de la serie mide 312 × 425 (`LiveSessionCardFrame.kt`) y no cambia de tamaño.
   - Con 360 × 740 dp (V3), la tarjeta mide lo mismo y se compactan el relator y la línea, sin solapes.
   - Con la barra de estado de 45 dp del emulador, nada queda recortado. La QA de LQA1 mostró que hoy la fila de series se recorta con unos 12 dp menos (`SessionRevisionFlowDeviceTest`).
5. **Defectos pendientes de LQA1** (QA_EMU_LQA1):
   - **B1:** en la ficha desplazada, el título pequeño se superpone al subtítulo que se desvanece. El cruce tiene que ser limpio: el subtítulo grande desaparece por completo antes de que entre el título fijo, o sus capas no coinciden en el mismo sitio.
   - **Tarjeta de descanso** (`rest-card` y `rest-track-card`): la línea «Plan de sesión · 3:00» queda cortada entre «Última serie» y «Siguiente». Nada se corta: si no cabe, se desplaza dentro con un borde que lo indique, o se reorganiza.

## Pruebas

- **Robolectric o Compose, en `app/src/test`:**
  - la línea con 1, 4 y 14 series y con preparación;
  - registrar desde el ✓;
  - añadir serie;
  - abrir la ruta;
  - cada destino del cockpit migrado abre su hoja y conserva su función: temporizador, RM, notas con tareas, hitos y fotos (entrada);
  - voz desde la cabecera;
  - fila de cabecera en pantalla corta.
- **Instrumentadas, en `app/src/androidTest`:** adapta `SessionRevisionFlowDeviceTest`, `LiveHeaderSheetsDeviceTest` y cualquier otra prueba que dependa del cockpit o del stepper antiguo, para que comprueben **las mismas invariantes con la UI nueva**.
  - No rebajes ninguna aserción sin decir por qué.
  - Añade una prueba de alto reducido (por ejemplo, 360 × 760 dp): la tarjeta y la línea caben completas.
  - **Compílalas.** Las corre el orquestador en el emulador.
- Renders de revisión, si el proyecto ya tiene su arnés (`*RenderSheetTest`, `KPKN_RENDER_DIR`): PNG de V1 a 880 y a 740, de la ruta y de cada hoja migrada, para que el orquestador los compare con los tableros.

## Reglas

- Gradle solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`, en primer plano.
- Nunca `--stop` ni matar java. Sin teléfono ni adb. Nada fuera de `C:\kw\u11`. Sin push.
- Commits por tema en español, con `Co-Authored-By: Codex <noreply@openai.com>`.
- Tareas obligatorias:
  - `:app:compileBaseDebugKotlin`;
  - `:app:compileBaseDebugAndroidTestKotlin`;
  - `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*"`, más tus pruebas nuevas;
  - al final, `:app:assembleBaseDebug :app:assembleBaseDebugAndroidTest -I C:/kw/tools/dbg-suffix.init.gradle`, para que el orquestador instale `com.example.kpkn.dbg`.
- No toques el editor de sesiones ni el calibrador (CAL3 vendrá después y usará esta línea).

## Informe

Escríbelo en `docs/screens-v2/reports/LV-A.md`:
- qué cambió;
- la tabla «función del cockpit → destino → prueba»;
- las medidas comprobadas;
- las pruebas instrumentadas adaptadas y por qué;
- los riesgos;
- los pasos para la QA en el emulador.
