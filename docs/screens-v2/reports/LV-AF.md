LV-AF completado sobre LV-B, en `feat/screens-v2`. **1.415 pruebas correctas, 0 fallos, 0 errores y 0 omitidas.** Compilan producción e instrumentadas y están ensamblados y verificados ambos APK `.dbg`.

Se leyeron `CLAUDE.md`, el plan, los briefs e informes de LV-A y LV-B y `QA_EMU_LVA.md`. Se revisaron en lectura las capturas de `C:\kw\shots\emu-lva\`, incluidos todos los fotogramas `story/f095–f103.png` y las capturas de `files/qa16-live-controls/` pertinentes.

| Punto | Corrección y reproducción |
|---|---|
| Historia | La reproducción local encontró la hoja colocada y con alto, pero el grupo `story-templates` quedaba fusionado en el panel clickable de `KpknSheet`. Historia y sus plantillas tienen ahora grupos semánticos propios. Se conserva la aserción instrumentada original. `LiveStoryEntryTest` comprueba controles íntegros, vista previa 9:16 y salida al resumen sin confirmar la sesión a 880/100 % y 740/130 %. La revisión visual detectó además el rótulo de compartir cortado al 130 %: admite dos líneas dentro del mismo botón de 48 dp y se comprueba su texto maquetado. |
| Ultrarrápido | El registrador cierra la hoja al publicar el registro confirmado por Room, antes de los efectos de descanso y sugerencias. El retorno tardío del comando tampoco cierra un formulario abierto después. Una prueba retiene deliberadamente el comando después de confirmar Room; otra inyecta un fallo SQL y comprueba conservación y reintento. El error permanece visible dentro de la hoja, conservando 95 kg, 6 reps y RIR 2. |
| Línea V1 | El formulario real, ViewModel, registrador, Room, paginador y línea se prueban juntos: 1/4, ✓S1 y S2 activa, y después tres registros sucesivos sin reducir las cuatro series ni duplicar registros. Se mantiene el registrador canónico. |
| Decimales | La calculadora acepta `82,5` y usa formato español para e1RM y las diez filas RM incluso con el sistema en inglés. La selección conserva la carga numérica original. Las otras hojas migradas ya usaban formatos con coma o valores enteros. |
| Drenaje | Conserva sus cifras y los 1.650 ms de duración, más 220 ms de salida. Se coloca 8 dp por encima de toda la isla, reservando navegación/IME, y se cierra al tocar. Cada registro tiene su propio estado de presentación. `LiveDrainOverlayTest` comprueba posición, acceso al ✓, caducidad, cierre y nueva presentación. |

Medidas de Robolectric con ancho de 360 dp: a 880 dp, Drenaje termina en y=728 y la isla empieza en y=736; a 740 dp y fuente al 130 %, termina en y=588 y la isla empieza en y=596. La separación es de 8 dp. Los renders de LV-A y LV-B comprueban también la tarjeta de 312 × 425 dp y sus modos.

Las instrumentadas se compilaron. `SessionRevisionFlowDeviceTest` conserva sus invariantes y añade preview visible, límites de los controles, ausencia de nuevos logs al entrar/salir de Historia y tres registros rápidos sucesivos. Espera a que termine el comando antes de detener el descanso, porque el cierre de la hoja sucede antes, al confirmar Room. No se rebajaron aserciones funcionales.

La validación final incluye **1.389 pruebas de `screens.workout.*` y 26 renders de regresión de LV-A/LV-B**, en 179 suites. Pasan las 12 pruebas nuevas. Se generaron 32 PNG: seis de estos arreglos y los 26 anteriores. Evidencia: [log final](C:/kw/u11/android-native/app/build/lv-af-green-validation.log), [resumen y SHA-256](C:/kw/u11/android-native/app/build/lv-af-delivery-summary.json), XML preservados en `android-native/app/build/lv-af-final-results/` y PNG en `lv-af-renders/`. La reproducción anterior se conserva en `lv-af-baseline-results/`.

Los comandos Gradle usaron exclusivamente `bash /c/kw/tools/gradle_slot.sh`, en primer plano y con `-Xmx8g`. Las tareas de entrega incluyen compilación Kotlin de producción e instrumentación, `screens.workout.*`, renders de LV-A/LV-B y ambos ensamblados con `-I C:/kw/tools/dbg-suffix.init.gradle`.

Se comprobaron los manifiestos empaquetados, la integridad ZIP y el SHA-256 de ambos APK:

- [APK de la app](C:/kw/u11/android-native/app/build/outputs/apk/base/debug/app-base-debug.apk): `com.example.kpkn.dbg`.
- [APK instrumentado](C:/kw/u11/android-native/app/build/outputs/apk/androidTest/base/debug/app-base-debug-androidTest.apk): `com.example.kpkn.dbg.test`, con destino `com.example.kpkn.dbg`.

Pendiente del orquestador: ejecutar las instrumentadas en el emulador. Repetir especialmente Historia desde cabecera, el menú RM → Ultrarrápido y tres registros consecutivos; comprobar error/reintento, coma con idioma inglés y Drenaje al tocar y tras su caducidad. Revisar 880/740 dp, barras reales y fuente al 130 %. Las instrumentadas compiladas y los renders locales no sustituyen esa ejecución.

Commit de código y pruebas: `e3d2dc294` — «corrige Historia, registro rápido, RM y drenaje». Este informe se registra en un commit documental separado. Ambos llevan `Co-Authored-By: Codex <noreply@openai.com>`.

No se utilizaron teléfono, adb, `--stop` ni cierres de Java; tampoco se hizo push. Las capturas se conservaron en lectura. No se modificaron `artifacts/`, documentos de auditoría, editor ni calibrador; los archivos ajenos quedan fuera de los commits.
