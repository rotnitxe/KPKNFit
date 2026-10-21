# LQA1 · Correcciones de la sesión en vivo tras la QA en el Z Flip (2026-10-20)

Rama: `feat/screens-v2` (worktree `C:\kw\u11`, base `master` 8ac57c9d4 = Pre Release 17, versionCode 50).
Capturas de la QA: `C:\kw\shots\u11-live-qa-1\files\` (PNG del teléfono, 1080×2640) y hojas de contacto en `C:\kw\shots\u11-live-qa-1\sheet-*.png`.
Registros de instrumentación: `C:\kw\logs\u11-instr-1.log` (57 pruebas, 13 fallos) y `C:\kw\logs\u11-instr-2.log` (repetición de 6: siguen fallando).
Prototipos aprobados (la referencia de aspecto): `docs/screens-v1/proposals/sesion-en-vivo/*.dc.html` y su `README.md`.

## Leyes que mandan (aprobadas por el usuario el 16-oct)

1. La tarjeta de la serie nunca cambia de tamaño; lo que no cabe se desplaza dentro, nunca se solapa.
2. RIR/RPE empieza en el mínimo; el plan es una marca tenue; valores de la app.
3. Las acciones del relator van dentro de la frase, sin caja.
4. El resumen nunca deja huecos (ni sobre los FAB ni en medio).
5. Aspecto del prototipo, contenido de la app: no inventar opciones, **no quitar funciones** («refinar, no quitar»).

## A · Pruebas de dispositivo que fallan

| # | Prueba | Error | Qué hacer |
|---|---|---|---|
| A1 | `SessionRevisionFlowDeviceTest` (7 métodos) | `IllegalStateException: This fixture only accepts revision16/code49` (línea 121) | Master ya es versionCode 50. Que el guardia no fije un número: que compare con `BuildConfig.VERSION_CODE` o lo que el dato de prueba realmente necesite. Explica en el informe qué protegía. |
| A2 | `LiveHeaderSheetsDeviceTest#realVoiceModeIsActivatedOnlyAfterExplicitStart` y `LiveHeaderSheetsRenderSheetTest#voice100/voice130` | No existe ningún nodo «Empezar» ni «Música» | El commit `d5ffa3816` (Pre Release 16) cambió la hoja de voz (LV12: «voz en dos losas», `980ffaab2`). Decide con evidencia: si la hoja perdió una función que existía (iniciar el modo de voz de forma explícita, la opción de música), **restáurala** con el aspecto de `B5-Voz.dc.html`; si solo cambió el texto y la función sigue, actualiza las pruebas al texto real. La invariante de la prueba (el modo de voz solo se activa tras un inicio explícito; rechazar no activa nada) debe seguir comprobándose. |
| A3 | `LiveHeaderSheetsDeviceTest#managerRetainsRenameSetupAndResetConfirmation` | `The component with (ScrollBy) && (VerticalScrollAxisRange) is not displayed` (línea 136) | Diagnostica. Ojo: el teléfono estaba en postura `HALF_OPENED` (Flex) durante la corrida; la prueba debe ser robusta a la altura de ventana. Si la hoja ya no tiene contenedor desplazable cuando debería, es un defecto de la app. |
| A4 | `LiveHeaderSheetsDeviceTest#ultraFastCountLimitsAndRecalculatedProtectionRemainReversible` | `performScrollTo() failed` (línea 225) | Igual que A3. |
| A5 | `RedesignReviewSetCaptureTest#relatorReposoAccionProcesoYHecho` | `ComposeTimeoutException` tras 5000 ms (línea 192) | Diagnostica qué condición no llega (¿estado «hecho» del relator?). Corrige la app o la prueba según la evidencia. |

## B · Defectos visibles en las capturas

| # | Captura | Defecto | Corrección esperada | Referencia |
|---|---|---|---|---|
| B1 | `review-set/10-ficha-desplazada-historial.png` | Al desplazar la ficha se ven **dos títulos superpuestos** (el grande que se desvanece y el pequeño fijo). | Un solo título visible en cada momento: cruce limpio (el grande se va del todo antes de que entre el pequeño, o se transforma uno en otro). | `G4b-FichaScroll.dc.html` |
| B2 | `review-set/03-avanzado-drop.png`, `04-avanzado-rest-pause.png` | En el modo Avanzado de la tarjeta, la fila «Así se ve un drop-set / rest-pause» queda **cortada** por el botón «Error de ejecución». | Nada se solapa: el contenido interior se desplaza o se ajustan espaciados, sin cambiar el tamaño de la tarjeta (ley 1). | `A2-Avanzado.dc.html` |
| B3 | `review-set/04-avanzado-rest-pause.png` | La cadena «Principal › Pausa 1 › Pausa 2» corta el último elemento a mitad sin señal de que se desplaza. | Borde de desvanecimiento o tamaño que deje ver que hay más. | `A2-Avanzado.dc.html` |
| B4 | `qa/lv13/Levantamientos-100.png` | «Tonelaje de Pierna» con un solo dato pinta un **bloque naranja macizo a todo el ancho**; «Sin registro previo» sale dos veces; hueco grande en medio. | Con un solo punto: barra de ancho acotado (o cifra) que se lea como dato; sin repeticiones de texto; sin hueco (ley 4). | `D2-ResumenLevantamientos.dc.html` |
| B5 | `review-rest/cockpit-hitos.png`, `cockpit-fotos.png`, `cockpit-notas.png` | En la hoja «Sesión» (Hitos, Fotos, Notas) el contenido **empieza a media hoja** y deja vacío el tercio superior. | Contenido anclado arriba, bajo las pestañas. | `B1-Cockpit.dc.html` |
| B6 | `review-set/07-relator-procesando.png` | Los personajes de la animación tocan el texto y una línea horizontal pasa pegada a la frase. | Separación vertical: el carril de la animación queda por encima sin tocar el texto. | `H2-RelatorAcciones.dc.html`, `H5-RelatorMomentos.dc.html` |
| B7 | `review-rings/03-ajuste-oficial-muscular-frente-g6.png` | En «Ajusta cómo llegas → Músculos», el músculo elegido se pinta en **azul (color de Columna)** y no hay leyenda de qué significan azul y marrón. | Colores y leyenda según `G6-AjusteMusculos.dc.html` (acento de músculos #F49A6E para lo elegido si el prototipo lo dice). | `G6-AjusteMusculos.dc.html` |
| B8 | `review-rest/rest-card.png` frente a `rest-track.png` / `rest-spiral.png` | Orden del usuario: el descanso **se homologa también a la tarjeta**. La tarjeta siempre pinta la Torre; comprueba que siga el estilo elegido (Torre, Pista o Espiral) igual que la pantalla completa. | Mismo estilo de RINGS y mismas piezas en tarjeta y pantalla completa, con el contenido de la app. | `F1`, `F3`, `F5` |
| B9 | `review-rest/rest-tower.png` | «de 4:09 · sugerido» y debajo «DESCANSO DINÁMICO 3:00» sin explicar la relación. | No cambies la lógica. Propón en el informe un texto (≤ 40 caracteres) que deje clara la relación con lo que la app calcula de verdad; si es obvio y no inventa nada, aplícalo. | — |
| B10 | `qa/lv13/*.png`, `lv12-render/*.png` | Franja **gris clara** al pie de las capturas. | Averigua si es el anfitrión de la prueba (barra de navegación del `ComponentActivity`) o la app. Si es la prueba, ponla de borde a borde en negro para que las capturas sean representativas; si es la app, corrígela. | — |

## Reglas firmes

- Gradle SOLO con `& 'C:\Program Files\Git\bin\bash.exe' -c "bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native <tareas>"`, en primer plano, timeout ≥ 3 600 000 ms. Si falta memoria, añade como primer argumento `"-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8"`.
- Prohibido: `--stop`, matar procesos java, `GRADLE_USER_HOME` paralelo, `JAVA_TOOL_OPTIONS`. Si el candado de Gradle es de otro proceso, para y cuéntalo.
- **No uses el teléfono ni emuladores** (las pruebas de dispositivo las corre el orquestador). Usa Robolectric y compila `androidTest`.
- No toques `C:\kw\msi`, otros worktrees, el checkout principal ni nada fuera de `C:\kw\u11`. Sin push.
- Commits pequeños en `feat/screens-v2`, mensajes en inglés tipo `fix(workout): …`, terminados con `Co-Authored-By: Codex <noreply@openai.com>`. No commitees `docs/screens-v2/maps/`.
- Textos de la app en español neutro.

## Hecho cuando

1. `:app:compileBaseDebugKotlin` y `:app:compileBaseDebugAndroidTestKotlin` pasan.
2. Pasan las pruebas unitarias filtradas: `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*"` (si alguna ya fallaba en master, dilo con evidencia).
3. Quedan generados `app-base-debug.apk` (con `-I C:/kw/tools/dbg-suffix.init.gradle`, id `com.example.kpkn.dbg`) y el APK de `androidTest`.
4. Informe final (en español) con: tabla A1–A5 y B1–B10 (estado: corregido / sin cambio necesario / no reproducible, con causa y archivo:línea), lista de commits, pruebas corridas con su resultado y cualquier decisión que deba validar el usuario.
