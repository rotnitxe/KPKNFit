CAL3 · Calibrador en vivo: persistencia, decisión y resultado (V7)

CAL3 completado en `C:\kw\u11`, rama `feat/screens-v2`, sobre CAL1, CAL2, LV-A y LV-B. Lo dejó muy avanzado Codex (sin commitear, cortado por un error del servicio) y lo cerró Claude: revisión punto por punto contra el encargo, una corrección de integridad (descartar una sesión), validación, ensamblado y commits. Los tres commits de código y el informe llevan `Co-Authored-By: Codex` y `Co-Authored-By: Claude Sonnet 5.5`.

| Commit | Contenido |
|---|---|
| `07d36cbe8` | Room v30: tabla `calibration_decisions`, migración 29 → 30, esquema `30.json`, DAO, repositorio, copias JSON y pruebas de versión. |
| `58d407268` | Enganche tras registrar, decisión PROPOSED, tarjeta V7 (seis estados y AMRAP normal), Aplicar / Conservar / Revertir, SUPERSEDED, persistencia y referencia de la sesión siguiente. |
| `a913d3742` | Marca «calibrada» en la línea (LV-A) y la ruta, resumen final, recorrido V7 compartido y la instrumentada `Cal3V7DeviceTest`. |
| (este informe) | Informe CAL3. |

## Qué hace

1. **Room v29 → v30.** Tabla `calibration_decisions` con los campos de CALIBRADOR_V1 §1 (identidad y origen, capacidad C, referencia B y su origen, cociente, factor, alcance, elegibilidad, efectos, estado, fechas, versión del estimador) más el registro completo serializado. `propagated` queda siempre en `[]`: ninguna decisión toca otro ejercicio. Índices por sesión y por ejercicio/contexto/estado. La serie y su decisión se confirman en la misma transacción que la sesión en curso; al cerrar, las decisiones se vinculan al identificador del registro definitivo (mismo `id`, sin duplicados). Las copias JSON incluyen la sección y restaurar una copia antigua conserva las decisiones existentes. Todo el acceso a datos va en `Dispatchers.IO`; la interfaz se publica en Main.
2. **Enganche tras registrar.** Al registrar una serie con propósito CALIBRATOR se llama a `CalibrationEngine.calibrate` (CAL2) con la referencia resuelta, por orden: calibración aceptada vigente (menos de 42 días y versión del estimador correspondiente), e1RM contextual del historial recalculado desde reps completas (nunca volumen ni el caché de puntuaciones), 1RM declarado, PR declarado. Un 1RM o PR declarados no se reutilizan bajo otra etiqueta, equipo o lateralidad. La decisión queda PROPOSED. Desaparece el `amrapCalibrationMessage` con la regla 1,10 / 0,90. Con 69 kg × 10 y RIR 1 la capacidad es 94,3 kg; frente a B = 88 propone 60 → 62,5 kg.
3. **Resultado en la tarjeta V7** (ver estados y medidas más abajo). Antes de registrar, insignia «Mínimo N · Calibrador» o «· AMRAP» y la consigna «Haz al menos N. Cuenta todas las que completes.».
4. **Aplicar, Conservar, Revertir.** Aplicar es una asignación absoluta: escribe las cargas «después» en las series pendientes compatibles; no multiplica nunca un factor sobre una carga ya ajustada, así que repetirlo o reintentar no cambia nada. Comprueba ejercicio, contexto, identidad estable de la serie, receta, lado y carga base; excluye las hechas, las omitidas y las fijadas a mano (incluidos borradores manuales). Conservar deja KEPT y fija el plan original (no puede pasar luego a APPLIED). Revertir deja REVERTED y restaura solo las pendientes compatibles. Si se edita y se vuelve a registrar la serie calibradora (o se corrige por la vía compartida de voz/vídeo), la decisión anterior pasa a SUPERSEDED y la nueva se reconstruye desde la carga base original: no se acumulan ajustes.
5. **Persistencia y retorno.** Al reabrir la app se restauran la decisión, las cargas aplicadas y el acceso a Revertir, y la tarjeta sigue en el resultado con el descanso minimizado. Las series ajustadas llevan la marca «calibrada» en la línea de LV-A y en la ruta. La sesión siguiente del mismo ejercicio y contexto parte de la referencia aceptada: en sesiones libres y programas KPKN como base de la carga sugerida («Referencia de calibración aceptada»). NEXT_SESSIONS no cambia nada hoy, tampoco tras reiniciar. En recetas con TM la sugerencia se deja a la receta y el efecto va por el canal existente de `ProgramAutoregulationEngine` (la serie calibradora cuenta como serie de propósito AMRAP en `collectAmrapHits`, así que al cierre de semana pasa por OFF / PROPONER / AUTO y nunca por encima de la receta).
6. **Resumen final.** Una línea por calibración vigente con su resolución («Aplicada · 3 series calibradas», «Conservaste las cargas del plan», «Revertida · cargas pendientes restauradas», «Propuesta guardada para la próxima sesión de este ejercicio», «No aplicada: motivo»).
7. **Nunca se toca otro ejercicio.** Verificado por pruebas (se compara el ejercicio contiguo antes y después de aplicar y revertir).

## Estados de la tarjeta

| Estado | Título en la tarjeta | Acciones |
|---|---|---|
| Sube | «Subir las cargas» con C, B, motivo, cambio medido y filas «S2 60 → 62,5 kg» | Aplicar a S2–S4 · Conservar |
| Mantiene | «Mantener las cargas» con el motivo (zona muerta o redondeo al material) y filas «S2 60 → 60 kg» | Aplicar a S2–S4 · Conservar (si no queda ninguna serie compatible, el botón dice «Aceptar referencia») |
| Baja | «Bajar las cargas» (también la bajada fija de 0–2 reps) | Aplicar · Conservar |
| Primera referencia | «Primera referencia», cargas desde C para las reps y la reserva de cada serie; sin % de mejora | Aplicar · Conservar |
| No aplicada | «Calibración no aplicada» con su motivo (sin esfuerzo, reserva alta, corporal, sin carga externa, solo parciales, reps fuera de rango) | Conservar; la serie se registra igual |
| Próximas sesiones | «Referencia para próximas sesiones»; hoy no cambia ninguna carga | Guardar propuesta · Conservar; tras guardar: «Propuesta guardada para la próxima sesión de este ejercicio» |
| Tras resolver | «Cargas aplicadas · calibrada», «Conservaste las cargas del plan» o «Revertida · cargas pendientes restauradas» | Revertir (solo si APPLIED) · Continuar · Editar |
| AMRAP normal | «Mínimo superado / cumplido / no alcanzado · N/M reps», «Registrado en tu historial y récords» y el efecto de la regla: vista previa pura de TM con OFF («queda registrado»), PROPONER («TM 100 → 102,5 kg al cerrar la semana») o AUTO; sin regla: «Sin regla de progresión: queda registrado» | Continuar · Editar |

Las filas de series que no se pueden tocar muestran por qué («Hecha · se conserva», «Fijada · se conserva», «Receta editada · se conserva»). El error de persistencia se muestra dentro de la tarjeta y deja reintentar sin perder cargas ni borradores.

## Medidas de la tarjeta (Robolectric, ventana de 360 dp)

| Medida | 880 de alto, 100 % | 740 de alto, 130 % |
|---|---|---|
| Marco de la tarjeta | 312 × 425 dp, y = 299 | 312 × 425 dp, y = 159 |
| Panel de resultado | 280 × 405 dp | 280 × 405 dp |
| Aplicar / Conservar / Revertir | 280 × 44 dp cada uno (y = 614 y 668) | 280 × 44 dp cada uno (y = 474 y 528) |
| Isla de la sesión (línea) | 328 × 112 dp, y = 736 (12 dp bajo la tarjeta) | 328 × 112 dp, y = 596 (12 dp bajo la tarjeta) |

El cuerpo del resultado se desplaza; las acciones quedan siempre dentro del marco y con objetivos de al menos 44 dp. A 740 dp al 130 % se ven C, B, el cambio y la primera fila, y el resto de filas se alcanza con el desplazamiento. Renders en `android-native/app/build/cal3-renders/` (escenas sube, mantiene, baja, primera, futura, sin esfuerzo, aplicada, resumen).

## Corrección de esta entrega: descartar una sesión

Revisando el código se encontró que las decisiones persistidas en Room sobrevivían al **descarte** de una sesión (cancelar entrenamiento). Una calibración APPLIED de una sesión cancelada seguía contando como referencia aceptada de la sesión siguiente, sin ninguna serie en el historial que la respalde; la prueba anterior de «consumo en la sesión siguiente» pasaba precisamente por un descarte. Ahora `clearOngoingWorkoutAndFlush` (y el borrado de un programa con sesión en curso) borran en la misma transacción las decisiones de esa ejecución, salvo cuando el registro de la sesión ya se escribió (`logAlreadyWrittenId`, caso del aviso de volumen tras cerrar). La identidad de la ejecución se unificó en `CalibrationDecisionRepository.executionId`. La prueba de consumo en la sesión siguiente ahora termina la sesión en lugar de descartarla, y hay dos pruebas nuevas del descarte.

## Pruebas

Comando (gradle_slot, `-Xmx8g`, en segundo plano con log en `C:\kw\logs\cal3s-1.log`):

`:app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.data.*" --tests "com.example.kpkn.domain.calibration.*" --tests "com.example.kpkn.screens.workout.*" --continue`

Resultado: **BUILD SUCCESSFUL en 20 min 15 s; 2.765 pruebas, 0 fallos, 0 errores, 3 omitidas** (las mismas 3 que ya salían antes de mis cambios y ninguna es de CAL3). Compilan producción y las instrumentadas. Las 76 pruebas propias de CAL3, todas en verde:

| Clase | Pruebas | Cubre |
|---|---|---|
| `CalibrationMigration29To30Test` | 3 | Migración física v29 → v30 sin perder filas y con todas las columnas validadas por Room; cierre y reapertura; solo APPLIED vigentes son candidatas y `propagated` siempre vacío. |
| `WorkoutCalibrationRecorderRoomTest` | 17 | Enganche del registro para sube, mantiene, baja, primera referencia, sin esfuerzo, reserva alta, corporal, asistida, solo parciales, 2 reps (bajada fija), cero reps con fallo y NEXT_SESSIONS; AMRAP normal sin decisión; fallo SQL con retroceso y reintento; sustitución e idempotencia; la referencia aceptada gana al 1RM declarado; conservar y revertir sobre registros duraderos. |
| `WorkoutCalibrationTest` | 22 | Aplicar solo a series pendientes no fijadas ni de otro ejercicio; asignación absoluta idempotente; conservar fija el plan; revertir solo pendientes; receta o contexto editados no se pisan; unilateral; incremento por máquina; TM gestionado; consumo por la sesión siguiente y rechazo de otro contexto o referencia caducada; historial con EWMA común; lastre; resumen. |
| `Cal3ViewModelRoomTest` | 7 | ViewModel real: aplicar, persistir, reabrir y revertir sin tocar otro ejercicio; NEXT_SESSIONS ignorado hoy y consumido en la ejecución siguiente tras terminar; fallo de aplicar y reintento; corrección por la vía de voz/vídeo; cierre con vínculo al registro real; **descarte borra decisiones**; **limpieza tras escribir el registro las conserva**. |
| `Cal3ProgramPreviewTest` | 5 | Vista previa de TM idéntica al motor del programa con OFF / PROPONER / AUTO, sin regla, determinismo. |
| `Cal3CardUiTest` | 20 | Escenas de la tarjeta a 880 y 740 de alto (también al 130 %): estados, marco completo, acciones de 44 dp, línea, Editar / Continuar. |
| `Cal3SummaryUiTest` | 2 | Resumen final a 880 y a 740 con texto grande: cada resolución una sola vez. |

En el mismo filtro siguen en verde las 59 de `domain.calibration.*` (CAL2) y las regresiones de LV-A y LV-B de `screens.workout.*`. Los XML de las clases propias están copiados en `android-native/app/build/cal3-final-results/`.

## Ensamblado y APK

Comando: `:app:assembleBaseDebug :app:assembleBaseDebugAndroidTest -I C:/kw/tools/dbg-suffix.init.gradle` (log `C:\kw\logs\cal3s-2.log`).

Resultado: **BUILD SUCCESSFUL en 14 min 58 s** (tras unos 65 min de cola). Sus metadatos confirman `com.example.kpkn.dbg` (versionName «KPKN Pre Release 17», versionCode 50) y `com.example.kpkn.dbg.test`. Se comprobó dentro de los dex que el APK de la app contiene `calibration_decisions` y el de instrumentación contiene `Cal3V7DeviceTest`.

- [APK de la app](C:/kw/u11/android-native/app/build/outputs/apk/base/debug/app-base-debug.apk) (487 MB).
- [APK de instrumentación](C:/kw/u11/android-native/app/build/outputs/apk/androidTest/base/debug/app-base-debug-androidTest.apk) (3 MB).

Los APK se ensamblaron con el árbol en el estado de `a913d3742`, antes del commit del informe (que solo añade el documento).

## Riesgos y decisiones

- **Cobertura de `Cal3V7DeviceTest`.** Está compilada, no ejecutada (sin emulador por instrucción). Los renders y las medidas son de Robolectric; las barras reales del sistema y la fuente al 130 % del dispositivo solo se confirman en el emulador.
- **NEXT_SESSIONS con TM.** No se empuja ninguna propuesta nueva al programa: la serie calibradora ya entra como AMRAP en el cierre de semana y la receta manda. Las reglas propias de cada programa (5/3/1, nSuns, Juggernaut, GZCLP…) y la conversión de las banderas AMRAP muertas de `power-*` son de CAL5; el editor, de CAL4. Ni uno ni otro se tocó.
- **Referencia aceptada.** Solo cuenta APPLIED (también NEXT_SESSIONS guardada), con menos de 42 días y misma versión del estimador. REVERTED, KEPT y SUPERSEDED no cuentan. Una decisión de un registro que el usuario borre del historial seguiría en la tabla (no hay borrado en cascada desde el historial); es un caso raro y el impacto es solo una referencia más.
- **Camino «terminar con aviso de volumen».** Tras cerrar con el aviso de volumen la sesión en curso se conserva un momento con el registro ya escrito; si en ese intervalo se vuelve a guardar la sesión en curso, la fila de la decisión vuelve a llevar la identidad de ejecución en lugar del id del registro. La referencia aceptada no se ve afectada (la consulta no depende de ese id), solo el vínculo decisión → registro. No se tocó el flujo de volumen.
- **Esquema congelado (orden del coordinador).** `CalibrationDecisionEntity`, `CALIBRATION_MIGRATION_29_30`, `30.json` y la versión 30 de `KpknDatabase` quedan exactamente como en `a913d3742` (se comprobó con suma MD5 de los dos archivos y `git diff HEAD` sobre `schemas/`). La única adición en ese archivo es una consulta del DAO (`deleteForSession`, un `DELETE` sobre columnas ya existentes, hecha en `07d36cbe8`) que no cambia columnas, índices ni nombres. No hizo falta ningún cambio de esquema. Lo que necesitaría esquema y se deja fuera: una clave externa con borrado en cascada desde `workout_logs` (hoy una decisión de un registro que el usuario borre del historial sigue en la tabla) y una columna propia para el id de ejecución separado del id de registro; la próxima versión libre de la base es la 32 y la migración la hará el orquestador tras fusionar con master.
- **Archivos de worktree ajenos.** `docs/screens-v2/reports/QA_EMU_LVB.md` modificado, los briefs nuevos (`ED-A`, `LV-C`, `MOT`, `PROG`) y `artifacts/aud2-editor-catalogo-20261020/` no son de esta entrega y quedan fuera de los commits. Tampoco se editó `CLAUDE.md`, que sigue diciendo «Room v29»: conviene que lo actualice quien lo mantiene.

## Pasos de QA en el emulador (orquestador)

1. Instalar los dos APK `.dbg` y ejecutar `Cal3V7DeviceTest` (clase `com.example.kpkn.screens.workout.components.Cal3V7DeviceTest`, dos pruebas: 880 y 740). Comparar los PNG de `getExternalFilesDir/cal3-v7-renders` con los renders de `build/cal3-renders`. Revisar barras reales y la fuente al 130 %.
2. Sesión libre con un ejercicio de barra con 1RM declarado, y una serie calibradora al menos de 3 reps (RIR 1 o fallo) seguida de 2–3 series pendientes: registrar y ver el resultado dentro de la tarjeta. Comprobar que ninguna acción queda fuera del marco y que la isla de la sesión conserva su separación.
3. Aplicar: las series pendientes cambian, aparece «calibrada» en la línea y en la ruta. Pulsar Aplicar otra vez o salir y volver: no cambia nada más. Revertir: vuelven las cargas «antes». Conservar: las cargas del plan se mantienen y ya no se puede aplicar.
4. Fijar a mano la carga de una serie pendiente antes de aplicar: debe quedar «Fijada · se conserva». Hacer una serie y verificar que queda «Hecha · se conserva».
5. Editar la serie calibradora y registrar de nuevo: la decisión anterior no se acumula (una sola línea en el resumen final).
6. Matar la app con la sesión a medias y reabrirla: resultado, cargas y Revertir siguen ahí.
7. Un ejercicio con alcance «Próximas sesiones»: hoy no cambia nada; tras terminar la sesión, la sesión siguiente de ese ejercicio y contexto sugiere desde la capacidad aceptada. Repetir cancelando la sesión: la siguiente NO debe partir de esa referencia.
8. Estados no aplicables: sin esfuerzo declarado, RIR 3, peso corporal, asistida y solo parciales. Debe registrarse la serie y explicarse el motivo.
9. AMRAP normal con y sin regla de TM, en un programa con OFF, PROPONER y AUTO: el texto del efecto debe coincidir con el modo.
10. Cerrar la sesión: el resumen muestra una línea por calibración con su resolución.
