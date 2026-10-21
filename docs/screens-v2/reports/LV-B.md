LV-B completado en `C:\kw\u11`, rama `feat/screens-v2`, sobre LV-A. La validación final ejecutó **1.403 pruebas: 0 fallos, 0 errores y 0 omitidas**. Producción e instrumentadas compilan y ambos APK `.dbg` están generados y verificados.

Se leyeron el encargo, `CLAUDE.md`, el plan, LV-A y PROTO_R4B. Se abrieron V6, V8–V11 y B8b en Edge con Playwright y un runtime local para sus exportaciones. Se comprobaron registro, bloqueo sin esfuerzo, reloj y ROM. Los seis tableros conservan sus SHA-256 originales; evidencia en `android-native/app/build/lv-b-board-review/` y `lv-b-board-hashes-final.json`.

Primero se resolvieron los 23 fallos heredados, comparando commits desde master `8ac57c9d4`. Los 22 casos de preparación y opciones ya existían en master y se remontan a `d5ffa3816`; la regresión de vídeo procede de CAL1, `89499d778`.

| Casos | Evidencia y corrección |
|---|---|
| 17 de preparación | El ajuste pasó a panel interno y cambiaron el aviso y el acceso de voz. Se actualizan los selectores conservando valores, edición manual, restablecimiento y recreación. El icono delega al flujo real; sin anfitrión, abre los controles existentes dentro de la hoja. |
| 5 de opciones de fuerza | Se restauran Historial, Ultrarrápido, Notas, Etiquetas, Saltar y Quitar, cuyos callbacks seguían presentes. Se mantienen Calculadora RM, el rótulo aprobado y las comprobaciones al 130 %. |
| 1 de vídeo | CAL1 eliminó una diferencia artificial del mínimo AMRAP que provocaba publicar siempre el borrador. Al recibir una serie registrada con el mismo id, el snapshot nuevo se restauraba pero no sustituía el borrador anterior. Se publica también ese borrador limpio y se incluye el snapshot en las claves del efecto. La prueba original se conserva íntegra. |

Las cinco suites pasan **48 pruebas**, incluidas las 47 originales y una nueva de delegación de voz. Antes de implementar los modos también pasó la regresión completa: **1.326 pruebas**. Los XML rojos preservados de LV-A, los resultados verdes del punto 0 y los diffs de origen están en `android-native/app/build/lv-b-inherited-results/`, `lv-b-point0-*-results/` y `lv-b-origen-*.diff`.

| Modo | Resultado |
|---|---|
| RM · V6 | Carga y porcentaje como foco; editor interno con reps estimadas, porcentaje y kg enlazados mediante las fórmulas de `Calculations.kt`. Muestra 1RM/PR y TM separados. La referencia local y el porcentaje persisten en el borrador sin modificar el programa ni el TM. Registra carga y reps reales, conserva cuartos de kilo y restaura el porcentaje de la carga registrada. |
| Solo RPE · V8 | Escala protagonista, carga elegida al ejecutar y reps iniciales en cero. Registra kg, reps y esfuerzo sin adoptar una carga fija del plan, histórico o sugerencia. |
| Tiempo · V9 | Reloj monótono con inicio, detención y reanudación; conserva fracciones al pausar. Entrada realizada en min:s, compatible con segundos antiguos. Completar el objetivo detiene el reloj y espera el ✓ de la línea. La corrección manual prevalece y actualiza el aviso. |
| Distancia · V10 | Metros reales, progreso y carga utilizada; registra `DISTANCE`. Conserva decimales al incrementar, persistir y reabrir. |
| Personalizado · V11 | Cantidad real y unidad del ejercicio, también con nombres largos. Registra `CUSTOM` y conserva la unidad al recrear la sesión. |
| Reps / AMRAP | Reps conserva su contenido. AMRAP mantiene su registrador y dispone de un punto de extensión de resultado dentro del marco para CAL3. |

Se conservan parciales, reps con ayuda, técnicas, notas, fotos, vídeo, ficha, Historial, Avanzado y Modo visual. Las cargas rápidas siguen accesibles en el selector de los modos nuevos; Solo RPE mantiene su contrato sin sugerencia fija. Las validaciones rechazan entradas inválidas y cantidades no positivas en los contadores nuevos. Los kg con coma y el tiempo min:s llegan también a técnicas y medios.

El esfuerzo tiene pista de **18 dp**, área táctil de **44 dp**, degradado, marca tenue del plan, destello y chispas crecientes. Empieza en el mínimo **sin marcar**. Reducir movimiento desactiva partículas, halo y destellos. ROM ocupa **44 dp táctiles** dentro de la misma tarjeta. Las acciones quedan ancladas y el contenido adicional se desplaza dentro.

Medidas de los componentes de producción en Robolectric, con viewport de 360 dp y reservas de 45 dp de estado y 24 dp de navegación:

| Viewport / fuente | Tarjeta | Acciones | Isla de sesión | Separación |
|---|---|---|---|---|
| 360 × 880 / 100 % | 312 × 425 dp; x=24, y=299 | 280 × 44 dp; x=40, y=668 | 328 × 112 dp; x=16, y=736 | 12 dp |
| 360 × 740 / 100 % | 312 × 425 dp; x=24, y=159 | 280 × 44 dp; x=40, y=528 | 328 × 112 dp; x=16, y=596 | 12 dp |

Los siete modos con y sin ROM conservan marco y acciones completas a 880/100 % y 740/130 %. Se corrigió el recorte de las acciones al 130 % reservando el pie. Los 16 renders nuevos cubren seis modos a ambas alturas, ROM a ambas alturas, fuego en fallo y editor RM. PNG y medidas: `android-native/app/build/lv-b-renders/`. El montaje usa `LiveSessionCardFrame`, tarjeta, cabecera, relator, línea y política de alto de producción; la actividad real queda para QA del emulador.

La batería final comprende **1.377 pruebas de `screens.workout.*`**, **16 renders nuevos** y **10 renders de LV-A**, en 176 suites. Dentro de ese total pasan las **67 pruebas nuevas**: 15 de lógica/compatibilidad, 29 de Compose, 7 con ViewModel/Room y 16 renders. Se comprueban registro, esfuerzo, unidades, min:s, reloj, confirmación, RM, decimales, ROM, persistencia, actualización sin duplicados y recreación.

Se completaron `:app:compileBaseDebugKotlin` y `:app:compileBaseDebugAndroidTestKotlin`. Se añadieron 16 instrumentadas —14 renders y dos recorridos funcionales— y se adaptaron `WorkoutV2UiTest` al reloj min:s y `Lv8SetCardDeviceTest` al marco 312 × 425, reforzando las aserciones existentes. Las instrumentadas **se compilaron; no se ejecutaron**.

Todos los comandos Gradle usaron `bash /c/kw/tools/gradle_slot.sh`, en primer plano y con `-Xmx8g`. El ensamblado final completó `:app:assembleBaseDebug :app:assembleBaseDebugAndroidTest -I C:/kw/tools/dbg-suffix.init.gradle`. Evidencia: [validación](C:/kw/u11/android-native/app/build/lv-b-delivery-validation.log), [ensamblado](C:/kw/u11/android-native/app/build/lv-b-apk-delivery.log), [resumen y SHA-256](C:/kw/u11/android-native/app/build/lv-b-delivery-summary.json) y `lv-b-final-results/`.

- [APK de la app](C:/kw/u11/android-native/app/build/outputs/apk/base/debug/app-base-debug.apk): `com.example.kpkn.dbg`.
- [APK instrumentado](C:/kw/u11/android-native/app/build/outputs/apk/androidTest/base/debug/app-base-debug-androidTest.apk): `com.example.kpkn.dbg.test`.

Se verificaron integridad ZIP, metadatos y SHA-256 de ambos APK; el manifiesto instrumentado empaquetado apunta a `com.example.kpkn.dbg`.

Commits en español, con `Co-Authored-By: Codex <noreply@openai.com>`:

| Commit | Entrega |
|---|---|
| `4c6d88cd4` | Resuelve el punto 0 y restaura funciones heredadas. |
| `9931c5a44` | Tarjetas por modo, esfuerzo, ROM, persistencia y renders. |
| `09f0eda64` | Adapta y refuerza las instrumentadas existentes. |

Este informe se registra en un commit documental separado.

QA del orquestador en emulador:

1. Instalar ambos APK y ejecutar `LiveSetModesDeviceTest`, `WorkoutV2UiTest` y `Lv8SetCardDeviceTest`. Comparar 880/740 con los tableros y repetir con fuente al 130 % y teclado abierto.
2. En RM, comprobar 80 % de 125 kg → 100 kg y 7 reps estimadas; editar las tres cifras, probar 75 % → 93,75 kg y registrar reps reales distintas. Revisar PR/TM, referencia local y reapertura de una serie registrada.
3. En Solo RPE, elegir carga, reps y esfuerzo desde vacío. Revisar cargas rápidas y modos corporal, lastre y asistido en los demás modos.
4. En Tiempo, iniciar, detener, reanudar, completar y confirmar con ✓. Probar `1:05`, `1:60`, cero y corrección manual. En Distancia y Personalizado, registrar decimales, usar −/+ y una unidad larga.
5. Activar ROM en todos los modos y desplazar dentro de la tarjeta. Comprobar esfuerzo sin marcar, fuego al acercarse al fallo y reducir movimiento.
6. Revisar parciales, ayuda, técnicas, notas y medios; marcar fallo muscular por vídeo en una serie registrada. Confirmar actualización única, recreación, prioridad de Atrás e IME.

La limitación pendiente es la comprobación sobre la actividad y el teclado reales en el emulador; los renders y la compilación instrumentada no sustituyen esa ejecución. No se utilizaron teléfono ni adb, ni se hizo push. No se modificaron `artifacts/`, documentos de auditoría, editor, calibrador ni tableros del scratchpad. El artefacto ajeno preexistente queda fuera de los commits.
