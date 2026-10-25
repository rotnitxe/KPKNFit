# POST RINGS — QA Android ampliado, 24-oct-2026

Seguimiento vigente: [cierre Android aislado y evidencia final](ANDROID_CLOSURE_20261024.md). Este documento conserva el checkpoint anterior con su alcance y binario propios.

Seguimiento posterior de la corrección de permisos y el traslado al Z Flip: [permisos y TalkBack](PERMISSIONS_TALKBACK_20261024.md). Los límites descritos abajo corresponden al checkpoint anterior.

Se ejecutó el seguimiento en la aplicación real con Room y `ProgramRepository`, usando exclusivamente datos sintéticos. El resultado es aprobación acotada de historial vacío, funcionamiento offline, filtros multideporte, texto grande, teclado y conservación del detalle al girar. **No se acredita todavía un recorrido completo con TalkBack.**

## Entorno y binario

- AVD nuevo `KPKN_PostRings_QA`, `emulator-5586`, Android 36, 2 GiB de RAM, pantalla efectiva 1080×2220 a 480 dpi. No se modificaron los datos del AVD original ni el teléfono; tampoco se contactó otro chat.
- Una única identidad de aplicación: `com.example.kpkn.dbg`, **KPKN Dev**, Room 34. Actualización con `-r` después de respaldar la fixture y comprobar identidad Room y certificados coincidentes.
- APK corregido: `android-native/app/build/outputs/apk/base/debug/app-base-debug.apk`, 489.482.000 bytes, SHA-256 `b277df6e1b0e0f156e3332574b8b6789ac1dab62c30c87070ad2a315d598b8ef`.
- Certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. La revisión acotada de los 23 DEX no encuentra las clases/métodos retirados auditados y sí encuentra `TrainingInsightsEngine`.
- Todas las llamadas ADB pasaron por la API de `emu_run.py`, con serial, candado exclusivo y guarda de nombre propios de este AVD. Se conservó el candado ajeno cuando apareció ocupado. Al finalizar se devolvió la conectividad, se dejó fuente 1,3, orientación vertical y lector apagado, y se cerró limpiamente solo este AVD. Su directorio y fixtures permanecen recuperables.

## Matriz de resultados

| Caso | Evidencia efectiva y límite |
|---|---|
| Sin datos | Binario corregido: cero sesiones, programas y recibos. Home indica ausencia de programa; historial muestra «0 sesiones registradas» y «No significa descanso ni esfuerzo cero». Sin puntos ni mediciones de cero fabricadas. |
| Offline | Arranque en frío, filtros, gráficos y detalles con modo avión activo, Wi-Fi y datos deshabilitados. `dumpsys connectivity` confirma `Active default network: none`. Se probó una instalación ya inicializada y fixtures locales; no una primera instalación íntegra sin red. |
| Multideporte | **29/29 filtros** recorridos y seleccionados en runtime; cada gráfico anuncia la disciplina correspondiente. Fixture de 42 `WorkoutLog`: 41 sesiones visibles y una futura excluida. La cobertura de todos los deportes acredita filtros y fallback descriptivo; no equivale a probar los 29 flujos completos de creación, cronometraje o sensores. |
| Comparabilidad | Salto actual 33 cm: mediana de tres anteriores comparables = **31 cm**. La marca futura de 999 cm no entra en el gráfico. La estimación, otra unidad y otra superficie no entran en esa mediana; los puntos incompatibles válidos siguen siendo visibles como hechos independientes. |
| Rotación | En el APK corregido, el diálogo mantiene `qa-salto-3`, fuente, contexto y selección al pasar a horizontal y volver. El historial vacío también conserva su ausencia de datos. El contenido largo es desplazable y Cerrar permanece disponible. Permisos ya resueltos durante estos casos. |
| Accesibilidad comprobada | Etiquetas de disciplina/métrica/unidad diferenciadas; alternativa textual a los puntos; detalle a 130 % y 200 % de texto. TAB+Enter cierra el diálogo y después permite abrir otro punto de la lista. El área de Cerrar mide **103×48 dp** al 200 %. Pruebas Compose de etiquetas, ausencia de datos y restauración aprobadas. |
| TalkBack | Servicio 16.0 enlazado, exploración táctil activa y foco visible capturados. No se consiguió acreditar desplazamiento de foco y activación completos mediante los gestos/atajos inyectados. La causa no está determinada: no se declara aprobación de lectura hablada, recorrido táctil completo ni fallo de KPKN a partir de estos intentos. Queda pendiente una comprobación manual del lector. |

Los 42 registros sintéticos permanecen idénticos, incluyendo todos sus campos y JSON, después de filtros, giros y navegación. La fixture no creó programas ni recibos. Los documentos no compatibles siguen conservados; la entrega no añade sensores ni cambia programas.

## Corrección derivada del QA

La selección «Todas las disciplinas» presentaba métricas iguales de diferentes deportes con etiquetas idénticas. El selector y la descripción accesible del gráfico ahora incluyen el deporte; el detalle también lo identifica. Se corrigió además «1 semanas» a «1 semana». No cambian cálculos, comparabilidad, esquema o persistencia.

`compileBaseDebugUnitTestKotlin assembleBaseDebug` aprobó con exit 0 en 9 min 49 s. La ejecución focalizada posterior aprobó **15 pruebas en tres clases, cero fallos, errores u omisiones, tres XML nuevos y ninguna clase seleccionada ausente; exit 0**, en 10 min 56 s:

- `TrainingFactChartTest`: tres pruebas, incluidas dos nuevas sobre disciplinas distintas, restauración y estado vacío.
- `TrainingInsightsEngineTest`: tres pruebas.
- `TrainingHistoryProjectionTest`: nueve pruebas.

No se vuelven a contabilizar aquí las 382 pruebas del checkpoint previo ni se afirma haber repetido toda la suite. El gate de retiro vuelve a terminar con cero infracciones activas.

## Hallazgo adicional y límites

Durante la preparación, tras denegar inicialmente notificaciones, el primer giro volvió a presentar la solicitud del sistema. Se conserva ese checkpoint; después se resolvieron los permisos de la fixture y se repitió el giro del historial. Este comportamiento general de permisos no se modificó y necesita revisión separada antes de aprobar el caso de rotación durante permisos iniciales.

Los intentos de lector usaron, entre otras rutas, los [atajos documentados de TalkBack](https://support.google.com/accessibility/android/answer/6110948?hl=en). Un servicio enlazado o un marco de foco no prueban que todo el recorrido sea utilizable. El AVD se lanzó sin audio: tampoco se acredita evaluación auditiva de pronunciación o síntesis de voz. QA físico, plegado, iOS y publicación permanecen fuera de esta tanda. No se creó commit ni se hizo push.

## Evidencia reproducible

Directorio privado:
`C:\Users\valen\.codex\visualizations\2026\10\24\01a16ee1-907f-7643-b0a2-45f779d8f023\post-rings\qa-extended`.

- `CORRECTED_APK.json`, `APK_VERIFICATION.json`, `build.log`, `android-tests.log`, `ANDROID_RESULTS.json` y `android-xml/`.
- `FIXTURE_MANIFEST.json`, bases sintéticas vacía/multideporte, `SPORT_FILTER_RESULTS.json`, los 29 XML `sport-*.xml` y capturas de familias representativas.
- `corrected-empty-*`, `corrected-point-*`, `corrected-keyboard-*`, `TOUCH_TARGETS_200.json`, `PRESERVATION_EXTENDED.json` y `FINAL_EMPTY_COUNTS.json`.
- `network-off.txt`, estado final offline de MainActivity y log acotado sin coincidencias FATAL/ANR del proceso. Los logs de TalkBack y sus intentos fallidos se conservan como límites, no como casos aprobados.
- `qa_extended.py` y `make_qa_fixtures.py`: preparación e interacción, siempre con guarda del AVD sintético. No ejecutarlos contra una instalación con datos del usuario.

La selección Gradle exacta está en `qa-tests.init.gradle`, con un trabajador Gradle de 2 GiB, un fork de tests de 3 GiB y sin excluir las tareas de compilación ya actualizadas.
