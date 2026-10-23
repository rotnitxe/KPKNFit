LV-A · Línea V1, ruta de la sesión y migración del cockpit

Implementado en `C:\kw\u11`, rama `feat/screens-v2`, sobre CAL1 y CAL2. Compilan producción e instrumentadas y pasan las 40 pruebas nuevas. La batería completa ejecutó 1.336 pruebas: 1.313 pasan y 23 fallan; los mismos 23 casos se reprodujeron en la comparación con el código anterior. Ambos APK `.dbg` están generados y verificados.

Se leyeron el encargo, `CLAUDE.md`, el plan, PROTO_R3, PROTO_R4B, QA_EMU_LQA1 y CAL1/CAL2. Se abrieron los cinco tableros aprobados en Edge mediante Playwright, con un runtime local de revisión porque sus exportaciones no incluyen `support.js`. Se comprobaron navegación, 14 series, técnica, registro y destinos de herramientas; los tableros se conservaron intactos.

La sesión usa una isla V1 de 112 dp con ruta, preparación por iconos, series, ejercicios plegados y el ✓ integrado. Centra el paso activo, comprime los lejanos y permite abrir todos los pasos conservando sus índices originales. Mantiene añadir serie, pulsación larga, técnica, lados L/R, rondas, descanso, gesto entre ejercicios, Deshacer estructural y los accesos pendientes de estado previo y cierre. El ✓ reutiliza el registrador activo y sus bloqueos de persistencia, guardado, cierre y cancelación. Los rótulos pendientes se ajustan sin recorte al 130 %; volver desde un borde al interior de la línea conserva la navegación actual.

Voz, Historia y ⋯ están en la cabecera. Historia abre la función existente del resumen final, sin guardar automáticamente el entrenamiento. Historial queda junto a Modo visual y Avanzado; la fila pasa a iconos en pantalla corta, con poco ancho o fuente grande. El relator ocupa el alto disponible tras reservar cabecera, tarjeta, isla y barras del sistema.

Se retiró el componente `WorkoutSessionCockpit` después de inventariar y migrar sus siete funciones. Las hojas reutilizan datos y callbacks existentes; RM usa el híbrido común de CAL1/CAL2. El temporizador usa reloj monótono y conserva su estado al cerrar la hoja. Se conservan los borradores de RM y de tarea al cambiar de destino.

| Función del cockpit | Destino | Prueba y conservación |
|---|---|---|
| Sesión | Línea → Ruta | `LiveSessionLineTest` y `LiveSessionSheetsTest`: reloj, métricas, ritmo, RINGS, selección real de ejercicio/superserie y opciones. `WorkoutCockpitDeviceTest` conserva tonelaje 1500 kg, RIR medio 1,5 y ausencia de kcal inventadas. |
| Hitos | Cabecera ⋯ → Hitos | `LiveSessionSheetsTest`: hito reciente y progreso de ejercicio estrella con el híbrido común. |
| Fotos | Cabecera ⋯ → álbum existente | `LiveSessionSheetsTest`: entrada, Cámara, Galería, captura pendiente y Reintentar. Se mantienen `AlbumSheetContent`, visor/análisis y `DeferredMediaDelete` con selección y Deshacer; sus pruebas existentes permanecen. |
| Temporizador | Cabecera ⋯ → Temporizador | `LiveSessionSheetsTest` y `LiveSessionTimerStateTest`: iniciar, pausar, reiniciar, cerrar/reabrir y cómputo exacto del tiempo pausado. |
| Calculadora RM | Cabecera ⋯ → Calculadora RM | `LiveSessionSheetsTest`: entrada real y filas 1–10 RM; 100 × 5 produce 112,5 kg. |
| Calorías | Cabecera ⋯ → Calorías | `LiveSessionSheetsTest`: estimación común y aviso si faltan datos. |
| Notas y tareas | Cabecera ⋯ → Notas y tareas | `LiveSessionSheetsTest`: borrador, guardado, última nota, añadir/marcar/eliminar tareas y reabrir. `SessionRevisionFlowDeviceTest` comprueba persistencia y llegada al resumen final real. |

B1 se corrige aplicando el fundido a todo el bloque de nombre, subtítulo y contexto antes de entrar el título fijo. Se toca únicamente la presentación de la ficha compartida `screens/sessioneditor/components/ExerciseFicha.kt`, necesaria para este defecto explícito; la lógica del editor y el calibrador no cambian. `LiveFichaHeadingRegressionTest` verifica la pertenencia al mismo bloque y el cruce al desplazar. En descanso, «Plan de sesión · 3:00» pasa junto al reloj; los detalles inferiores conservan su desplazamiento con fundido de borde. `LiveQaLayoutTest` comprueba la referencia completa sin desplazar en torre, pista y espiral.

Medidas de los componentes de producción en Robolectric, con viewport de 360 dp, barra de estado de 45 dp y navegación de 24 dp. El render reproduce el montaje usando la política de alto de producción; la actividad real se comprobará con las instrumentadas.

| Viewport / fuente | Tarjeta | Isla | Pasos | Acciones de tarjeta |
|---|---|---|---|---|
| 360 × 880 / 100 % | 312 × 425 dp; y = 299 | 328 × 112 dp; y = 736 | 260 × 56 dp | 280 × 44 dp |
| 360 × 740 / 100 % | 312 × 425 dp; y = 159 | 328 × 112 dp; y = 596 | 260 × 56 dp | 280 × 44 dp |
| 360 × 760 / 130 % | 312 × 425 dp; y = 179 | 328 × 112 dp; y = 616 | 260 × 56 dp | 280 × 44 dp |

Se comprueban 12 dp entre tarjeta e isla, contención de la fila de acciones, objetivos de paso de 48 × 56 dp y registro de 52 × 52 dp. Los PNG y sus medidas están en `android-native/app/build/lv-a-renders/`: `lv-a-v1-880`, `lv-a-v1-740`, `lv-a-v1-760-130`, `lv-a-route`, `lv-a-milestones`, `lv-a-photos`, `lv-a-timer`, `lv-a-rm`, `lv-a-calories` y `lv-a-notes`.

Las instrumentadas adaptadas son `SessionRevisionFlowDeviceTest`, `LiveHeaderSheetsDeviceTest`, `WorkoutCockpitDeviceTest`, `WorkoutV2UiTest`, `RedesignReviewRestCaptureTest` y su soporte. Los accesos del cockpit/roadmap/FAB se sustituyen por línea, cabecera y hojas. Se conservan las invariantes de navegación, registro único en Room, opciones, voz, métricas, ausencia de solapes, IME y prioridad de Atrás. Se añaden nota/tareas en el resumen real, Historia sin guardar y contenido Android real restringido a 760 dp.

La antigua aserción de ver MOV/APROX y todas las series simultáneamente cambia por comprobar cada destino íntegro al desplazar la línea: V1 exige centrar el activo en una ventana horizontal. La fila antigua de 35 dp pasa a comprobar 56 dp dentro de la isla de 112 dp. La prueba unitaria de flechas antiguas pasa a comprobar navegación por pasos y registro, manteniendo objetivos de al menos 48 dp. Los controles de icono se buscan por nombre accesible; no se eliminan aserciones funcionales.

La comparación acotada con la tarjeta de `0502af225`, manteniendo las cinco suites originales sin modificar, reprodujo exactamente los mismos 23 casos fallidos de 47 pruebas. Los ayudantes nuevos de presentación se conservaron sólo para compilar los fixtures de LV-A. La tarjeta actual se restauró byte por byte y se verificó por SHA-256 antes de la entrega. Evidencia: `android-native/app/build/lv-a-baseline-summary.json` y `lv-a-baseline-validation.log`.

| Suite heredada | Fallos reproducidos | Causa observada |
|---|---:|---|
| `WorkoutReadinessSheetAdjustTest` | 16 | Expectativas del contenedor de ajuste, aviso y controles de voz anteriores. |
| `WorkoutReadinessSheetCompactTest` | 1 | Aviso antiguo del estado previo. |
| `LiveExerciseOptionsUiTest` | 2 | Opciones de fuerza de la interfaz anterior. |
| `LiveOptionsLargeFontTest` | 3 | Las mismas opciones anteriores al 130 %. |
| `SetExecutionCardVideoTest` | 1 | Restauración del esfuerzo tras marcar fallo muscular en una serie ya registrada. |

Se ejecutaron `compileBaseDebugKotlin`, `compileBaseDebugAndroidTestKotlin` y `testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*"`, añadiendo `LiveSessionLineRenderSheetTest` y la regresión existente `ExerciseFichaVideoAuditTest`. Resultado final: **1.336 pruebas, 23 fallos heredados, 0 errores y 0 omitidas**. Las **40 nuevas pasan**: 20 de línea, 8 de hojas, 1 de temporizador, 1 de B1 y 10 renders. También pasan las pruebas adaptadas de tarjeta compacta, cierre de capas, cabecera y layout.

El comando global termina con código 1 por esos 23 fallos; `--continue` permitió completar ambos ensamblados independientes. La comprobación final confirmó igualdad exacta de los casos fallidos con la comparación, los dos APK válidos y su destino de instrumentación. Evidencia: [resumen final](C:/kw/u11/android-native/app/build/lv-a-delivery-summary.json) y [log de entrega](C:/kw/u11/android-native/app/build/lv-a-delivery.log).

Todos los comandos Gradle se ejecutaron en primer plano mediante `bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8"`. Se completaron `assembleBaseDebug` y `assembleBaseDebugAndroidTest` con `-I C:/kw/tools/dbg-suffix.init.gradle`. Sus metadatos confirman `com.example.kpkn.dbg` y `com.example.kpkn.dbg.test`; el manifiesto instrumentado apunta a `com.example.kpkn.dbg`.

- [APK de la app](C:/kw/u11/android-native/app/build/outputs/apk/base/debug/app-base-debug.apk).
- [APK de instrumentación](C:/kw/u11/android-native/app/build/outputs/apk/androidTest/base/debug/app-base-debug-androidTest.apk).

Los riesgos pendientes son los 23 fallos heredados descritos arriba y la ejecución de las instrumentadas en el emulador por el orquestador. Las instrumentadas se compilaron; no se ejecutaron en este encargo. No se usaron teléfono ni adb.

Para la QA del orquestador:

1. Instalar ambos APK y ejecutar las suites instrumentadas adaptadas, especialmente `SessionRevisionFlowDeviceTest` y su caso de 760 dp. Comparar 880/740 con barra de 45 dp y fuente al 130 %; tarjeta, acciones e isla deben quedar enteras.
2. Probar 1, 4 y 14 series con movilidad/aproximación; centrar primero/último, abrir pasos comprimidos, añadir y mantener pulsado. Comprobar lados, rondas, técnica, gesto entre ejercicios y Deshacer estructural.
3. Registrar con el ✓ y verificar un único registro con carga, repeticiones y esfuerzo reales; editar una serie hecha y confirmar actualización. Revisar IME, Historial, Modo visual y voz desde la cabecera.
4. Abrir Ruta y los seis destinos de ⋯, comprobar métricas/RINGS, navegación y Atrás. En fotos, cámara/galería, pendiente/reintento, selección, visor/análisis y borrar con Deshacer. En temporizador, cerrar/reabrir mientras corre y mientras está pausado.
5. Guardar nota y tareas, abrir Historia y el resumen final, y confirmar conservación sin guardado automático. Desplazar la ficha por el cruce del título y revisar la referencia del plan en descanso torre/pista.

Commits de código, en español y con `Co-Authored-By: Codex <noreply@openai.com>`:

| Commit | Cambio |
|---|---|
| `53d79d927` | Limpia el cruce de la ficha y la referencia del descanso. |
| `c5ffb0ef2` | Integra la línea V1 y migra las funciones del cockpit, sus pruebas y renders. |

Este informe se registra en un commit documental separado con el mismo coautor.

No se hizo push. No se modificaron `artifacts/`, documentos de auditoría ni los tableros del scratchpad. Los archivos ajenos presentes se dejan fuera de los commits.
