# Handoff para auditoría — KPKNFit Android

## Entrega

- Commit de código: `2ad50d96fdfb28cb828281a89d104e881435edaa` (`fix: close Android UI remediation regressions`), hijo de `41edea0eb` en `master`.
- APK: [app-baseRelease-2ad50d96f.apk](../app-baseRelease-2ad50d96f.apk), SHA-256 `0DFA793AAB19D43B632B3BBB6C78A2DF104FACFD7737EF73643D737B8B83EDB8`.
- Certificado v2/v3 válido: `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1`; paquete `com.example.kpkn`, versión 55 / `KPKN Pre Release 21`, Room 38.
- Build Debug y Release exitoso en el commit de código; resultado, firma, mapping y límite de instalación en [release-build-receipt.md](release-build-receipt.md).
- iOS queda fuera del alcance por instrucción del usuario.

## Fase y aceptación

- Fase alcanzada: correcciones Android implementadas y pruebas focalizadas ejecutadas; build/firma/copia canónica completados. La aceptación del plan sigue parcial.
- `acceptance-matrix.md` contiene las 13 filas y las regresiones adicionales: requisito 3 aprobado; otros 12 parciales. No presentar 121 pruebas de componentes como 121 recorridos de producto.
- Suites JVM dirigidas: 264 tests aprobados en 32 suites únicas; cierre adicional `EntrenoPlanStepTest` 20/20 y `ProgramStructureSheetsTest` 8/8. Suite Base completa: timeout histórico de 45 minutos, no aprobada.
- AVD: 121/121 en 30 suites seleccionadas, fijadas al serial autorizado `emulator-5580` y al paquete de pruebas `com.example.kpkn.dbg`. Los logs y la reconciliación están en [avd-validation-receipt.md](avd-validation-receipt.md).
- El APK Release final está instalado con `-r` en `emulator-5580`; su SHA remoto coincide con el artefacto y `MainActivity` quedó reanudada. Se hizo un reboot normal del AVD para recuperar Package Manager, sin `-wipe-data`; no se ejecutó `pm clear` ni se inspeccionó la base de datos. Z Flip físico no se usó.

## Cambios finales del commit fuente

- `EntrenoPlanStep`: el perfil general presenta las alternativas a medida en carrusel, conserva recomendación/selección y permite solicitar otra versión; el catálogo queda expandible.
- Hojas de ciclos: etiquetas visibles traducidas a español; se añadió `ProgramCyclesSheetDeviceTest`.
- `HomeRetiredSessionCueUiTest` y la cobertura de hojas live se ajustaron a recorridos/componentes actuales; correcciones de semántica de sesión y observaciones se cubren en las suites existentes.
- Los cambios de código y sus pruebas están en `2ad50d96f`; los recibos y el log reproducible acompañan la publicación. El APK binario queda como artefacto local canónico y no se añade al historial Git.

## Verificaciones y pendientes

- Verificar el SHA final de `origin/master` tras la publicación; la respuesta de entrega da ese SHA exacto. El commit fuente del APK es `2ad50d96f`.
- Ejecutar los 12 recorridos Android todavía parciales de la matriz, incluidos cargas/derivados, reanudación/recurrencia, edición permanente en vivo, cierre, progreso, editor, portadas, menús, recetas, onboarding, check-in nutricional e importador/modo visual.
- AVD QA10 pendiente: Owner estaba en `0`; no se creó otro usuario. La conexión ADB final no expuso el serial autorizado.
- No se tocó la app personal del Z Flip, no se accedió al teléfono y no se inspeccionaron datos personales.
- El checkout conserva cambios y archivos ajenos existentes. El stage de esta entrega se limita a matriz, recibos y log; no incluir `android-native/output`, APKs, worktrees ni otros WIP.

## Actualización de auditoría — 2026-11-16

- Fase exacta alcanzada: plan §5.3, aceptación Android en AVD; foco actual Req. 8. iOS excluido por instrucción del usuario.
- Revalidación nueva de Req. 8: `LiveSetConfigSheetTest` 1/1 JVM (`TEST-com.example.kpkn.screens.workout.components.LiveSetConfigSheetTest.xml`) y clase `LiveProgramPersistenceFlowDeviceTest` 9/9 en `emulator-5580`, logs `avd-live-program-prescription-edit-r59-20261116.log` y `avd-live-program-persistence-r60-20261116.log`. `baseDebug` SHA-256 `1ED461806514701DBE5D0D97D48A0B2A764CDC65A126A5CACBFC779D9C446111`; runner SHA-256 `58277497D44AA05D6F1712C387D5B72E3CA910FBFBB66114AB63BA7A5318B32F`; certificado debug cotejado `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Las dos instalaciones quedaron fijadas a `-P 5045 -s emulator-5580`; el APK release y la app personal no se tocaron.
- La edición real cubre el rango `8–10`, persistencia en origen/futura ocurrencia de variante A, preservación de semana pasada/sesión ajena/variante B y reapertura de Room fixture. El resto de la clase conserva cobertura de reemplazo, altas/bajas, error transitorio/reintento, doble toque y conflicto de borrador sucio.
- Remediación añadida: `applyLiveSetConfig` ahora permite guardar objetivos cuando el descanso es opcional y no hay descanso heredable; regresión en `LiveSetConfigSheetTest`. Sin cambio de esquema.
- Diagnósticos conservados: r57 montó por error el APK viejo (SHA `9152531E…`, timestamp 06:18 anterior al fuente), r58 detectó que el wait condition del test aceptaba cualquier estado pendiente y no el valor editado. La prueba se sincronizó y pasó en r59/r60. Consultar logs r57/r58 en el recibo AVD; no cuentan como aprobados.
- La matriz general sigue en 1 requisito aprobado / 12 parciales; el agregado histórico 121/121 no se recalculó. Falta compilar y verificar `baseRelease` desde el árbol final, completar aceptación AVD por requisito y ambos bloques de regresión, y vincular el artefacto a un SHA de código final.
- Estado Git observado: `master` y `origin/master` en `4c4bab0d1`; los cambios de esta continuación siguen locales, sin commit ni push. Preservar el WIP concurrente al preparar la integración.
- Discrepancia pendiente para la auditoría: el plan original pide Room 37, mientras la base publicada actual y el release anterior declaran Room 38 por la migración aditiva `37 → 38`. Esta continuación no cambió Room. No proclamar conformidad con el punto hasta documentar la decisión/base final.

## Continuación de Req. 8 — 2026-11-16

- El alta desde el menú `Añadir serie` reveló que el replay añadía al final de todas las semanas, aunque las series futuras tuvieran otro orden. `PendingStructuralChange.AddSet` ahora conserva ordinal y predecesor estable; el replay de futuro inserta tras el predecesor único y falla cerrado si el mapa no es inequívoco.
- Evidencia actual: `WorkoutStructuralPersistenceReplayTest` **14/14** JVM; `permanentSeriesMenuAdditionChangesOnlySourceAndFutureVariantA` **1/1**; `LiveProgramPersistenceFlowDeviceTest` **10/10** en `emulator-5580`. La actualización fue por `adb -P 5045 -s emulator-5580 install -r`, tras verificar certificado y Room38 sin cambios. `baseDebug` SHA `D683909D6499DE6363DCB3887FD7814A0EE5E79EE34CFAB094C9FD5F4F9BB490`; runner SHA `96DCA61821886B6F2604DF8C2068035868E1191550CC07CC941F251A7BFD5BED`; certificado debug SHA `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.
- Evidencia enlazada en `avd-validation-receipt.md`, logs r63/r64/r65 y XML JVM `junit-targeted/TEST-WorkoutStructuralPersistenceReplayTest-addset-r63.xml`. r62 sigue como intento diagnóstico con app vieja; no cuenta como fallo del parche ni como aprobación.
- Fase actual: §5.3 AVD, requisito 8, aceptación adicional de alta de serie concluida. Req. 8 continúa **Parcial** hasta cerrar voz, sesión libre, edición de series realizadas, UI visual, rematerialización y matrices completas por alcance. Release limpio, aceptación general de los 13 requisitos y regresiones, publicación `master` y sincronización del checkout siguen pendientes. iOS queda fuera del alcance.
## Revalidación de Recurrencia — 2026-11-16

- Pruebas dirigidas r66: 3/3 passed en JVM/Robolectric. `ProgramRepositoryConsolidationTest` reconcilió una rutina completada tras cierre/reapertura de Room (ciclo 2, mismo `runId`) y reanudó una rutina legacy desde estado completado mientras reemplazaba otro programa activo, conservando calendario e historial. `ProgramDetailViewModelTest` verificó el nombre del otro programa y el cambio activo al reanudar. XML y log en `acceptance-matrix.md`.
- El call site de producción de `reconcileOnOpen` está presente en `ProgramRepository.kt` al cargar el programa activo.
- La aceptación UI/AVD ahora pasó en r70 (seed 1/1 + proceso detenido + Room reabierta 1/1; detalle en `avd-validation-receipt.md`). Req. 5 sigue **Parcial** por la integración durable del resultado 1RM registrado y escenarios restantes de finitos/recurrentes con reinicio y reintento. iOS sigue excluido.

## Seguimiento AVD r70 — 2026-11-16

- Fase exacta: plan §5.3, aceptación Android en AVD; Req. 5 avanzó de cobertura JVM a aceptación real de la ficha y persistencia tras detener el proceso. Aún no es aceptación completa del plan ni fase de release final.
- `ProgramDetailRecurringResumeDeviceTest` compiló bajo el wrapper/candado Gradle; `assembleBaseDebugAndroidTest` quedó `BUILD SUCCESSFUL`. El diálogo de cambio de programa se canceló sin mutar el activo y luego confirmó la reanudación. La segunda invocación, tras `am force-stop` dirigido a `com.example.kpkn.dbg` en `emulator-5580`, recuperó de Room la misma identidad, ciclo 2, agenda e historial.
- El único artefacto instalado en este paso fue el runner `.dbg.test`, con certificado cotejado, SHA-256 `50B685211D47870ADE981939469D09A6A40B2A189CCBE9DD184641FCD613E7FF`; el APK app `baseDebug` permaneció `D683909D6499DE6363DCB3887FD7814A0EE5E79EE34CFAB094C9FD5F4F9BB490`, v55, Room38. No hubo cambio de esquema ni toque de datos personales. Fixture eliminada.
- Evidencia primaria: `avd-recurrence-seed-r70-20261116.log`, `avd-recurrence-resume-r70-20261116.log` y `gradle-logs/recurrence-avd-build-r70-20261116.log`. Intentos r68/r69 quedan clasificados como diagnósticos del test, no aceptación ni fallo de producción.
- Pendiente de Req. 5: integración durable del resultado 1RM registrado y los casos restantes de estados finito/recurrente, reinicios y reintentos descritos en el plan. Pendientes globales: cerrar todos los criterios parciales, bloque de regresión, build/copia release limpios contra SHA final y publicación/sincronización de `master`; verificar el árbol actual antes de integrar para conservar WIP. Room fuente es v38, frente al Room37 que aparece en la versión antigua del plan.

## Revalidación de la puerta terminal 1RM — 2026-11-16

- Focalizado r71: `ProgramProgressEngineTest` **17/17** y `BlockReviewLifecycleTest` **7/7**, `BUILD SUCCESSFUL` en 37 s. XMLs `junit-targeted/TEST-ProgramProgressEngineTest-recurrence-r71.xml` y `junit-targeted/TEST-BlockReviewLifecycleTest-recurrence-r71.xml`; log `gradle-logs/recurrence-terminal-1rm-r71-20261116.log`.
- El test final compara la última puerta 1RM con `REPEATING` (ciclo siguiente activo) y `HAS_END` (run completado). El lifecycle test confirma que la continuidad se ofrece solo después de resolver el cierre. Esto cubre el motor; sigue pendiente persistir este caso terminal a través del repositorio/reinicio y completar los demás escenarios del Req. 5.
- Fase exacta sigue §5.3: aceptación Android por requisitos en AVD y cierres focalizados. La aceptación general sigue parcial; no se inició la build release final ni la publicación. iOS excluido.

## Persistencia del cierre terminal 1RM — 2026-11-16

- `ProgramRepositoryConsolidationTest.terminalOneRmRoomReopen` pasó **1/1** con Robolectric/Room (`TEST-ProgramRepositoryConsolidationTest-terminal-one-rm-r74.xml`; log `recurrence-terminal-room-r74-20261116.log`, BUILD SUCCESSFUL en 3m43s). Valida dos fixtures aisladas: `REPEATING` avanza a ciclo 2/ACTIVE, `HAS_END` queda en ciclo 1/COMPLETED; ambas conservan `runId`, resolución SKIPPED e historial tras cerrar/reabrir Room. El guard de segunda resolución impide duplicar avance y cada base se borra al finalizar.
- r72/r73 diagnosticaron límite de ruta del sandbox Windows al usar nombre largo; logs conservados. Se usó `t.db` y test name corto en r74.
- Combinada con r71/r74, la puerta terminal ya cubría motor y resolución omitida; r80 añade persistencia Room del resultado medido. Req. 5 sigue parcial por la aceptación UI/AVD pendiente y el resto de la matriz finito/recurrente.
- Fase actual permanece §5.3, aceptación Android focalizada. La matriz completa, regresiones globales, release final y publicación `master` siguen pendientes.

## Corrección de serie completada — Req. 8 (2026-11-16)

- Fase exacta: plan §5.3, aceptación Android por requisito en AVD. Req. 8 ahora incluye una corrección de serie completada en sesión activa: `correctingACompletedSetChangesExecutionOnlyAndNeverOffersPermanentPlanScope` registra 8 reps, corrige a 7 por `patchCompletedSet` y verifica estado de ejecución, plan intacto, ausencia de alcance permanente/nueva versión y preservación al reabrir Room.
- La prueba individual pasó 1/1 en r75 y la clase `LiveProgramPersistenceFlowDeviceTest` pasó 11/11 en r76. El primer pase de clase r75 fue 10/11 por timeout transitorio del test existente de retry Room; su ejecución aislada pasó 1/1 y la repetición de clase r76 quedó verde. Logs y alcance están detallados en `avd-validation-receipt.md`.
- El runner instalado `.dbg.test` quedó verificado por SHA/certificado; la app `baseDebug` instalada no cambió. La prueba no valida captura visual del input ni entrada por voz. Req. 8 sigue **Parcial** junto con Req. 5; la aceptación de 13 requisitos y regresiones aún no cierra.
- Próximo tramo: completar los casos faltantes por matriz, priorizando los escenarios de cierre/reinicio pendientes de Req. 5, además de voz/sesión libre/rematerialización y recorridos visuales de Req. 8. Después ejecutar regresiones globales, construir y verificar release limpio contra el SHA final y publicar/sincronizar `master` conservando el WIP local.

## Resultado terminal 1RM medido persistido — Req. 5 (2026-11-16)

- `ProgramRepositoryConsolidationTest.terminalOneRmRoomReopen` pasó bajo wrapper/candado en r80: **18/18** de la clase, con cuatro variantes finitas/recurrentes y `SKIPPED`/`RECORDED`. XML copiado a `junit-targeted/TEST-ProgramRepositoryConsolidationTest-terminal-one-rm-r80.xml`; log `gradle-logs/recurrence-terminal-recorded-r80-20261116.log` (`BUILD SUCCESSFUL`, 3m53s, offline).
- Los casos registrados conservan S/B/D (200/120/220 kg) en `powerliftingProfile` y `goals` tras reabrir Room, junto con audit trail, progreso del programa e historial. `REPEATING` termina activo en ciclo 2, `HAS_END` completado en ciclo 1; un segundo cierre no duplica avance. Fixtures Room se borran en `finally`.
- Alcance exacto de r80: prueba JVM/Robolectric a través del motor y la mutación durable de repositorio. La ruta UI/ViewModel terminal en `emulator-5580` pasó después en r97 (ver sección siguiente). Req. 5 permanece **Parcial** por los demás escenarios de la matriz.

## Ruta UI/AVD del 1RM terminal registrado — Req. 5 (2026-11-16)

- Fase actual: plan §5.3, aceptación Android por requisito en AVD. `ProgramDetailTerminalOneRmDeviceTest.recordedTerminalOneRmFromProgramDetailPersistsAfterRoomReopen` pasó **1/1** en r97. Ejecuta el camino visible de ficha y hoja, persiste 200/120/220 kg, cierra/reabre Room y verifica resultado finito `COMPLETED`, ciclo 1, mismo `runId`, resolución/audit trail, perfil y objetivos.
- Build runner r96 quedó `BUILD SUCCESSFUL`; SHA local/remoto del `.dbg.test` coincide (`D1E6076F4EB0D6F88C4A7845614344980F0D018E91670A41146D21D6D76BEEDD`) y firma debug fue cotejada. La app base `.dbg` siguió intacta en `D683909D6499DE6363DCB3887FD7814A0EE5E79EE34CFAB094C9FD5F4F9BB490`. La fixture fue eliminada. La evidencia y sus límites están en `avd-validation-receipt.md`.
- La aceptación AVD de la puerta terminal medida ya no está pendiente. Req. 5 y Req. 8 siguen **Parciales** por el resto de sus criterios; continúan pendientes aceptación integral, regresiones, build release final, publicación y sincronización segura de `master`.
- Siguiente tramo: cerrar escenarios de recurrencia restantes y las rutas de voz/sesión libre/rematerialización y visuales de Req. 8; luego consolidar regresiones por matriz, construir/verificar release limpio contra el SHA final y publicar/sincronizar `master`, preservando WIP local.

## Actualización Req. 8 — sincronización del retry y AVD (2026-11-16)

- La clase de `LiveProgramPersistenceFlowDeviceTest` pasó **11/11** en r105 en `emulator-5580`; el retry aislado pasó **1/1** en r104. La primera repetición r103 quedó 10/11 porque el test tocaba el alcance antes de que terminara la escritura previa de la sesión viva. El guard de producción rechazó correctamente ese toque; el test ahora espera `awaitPendingLiveMutation` antes de ejercitar el fallo transitorio.
- Runner actualizado `.dbg.test`: SHA-256 `9A0160A79127C6C887A8321B7C9DA1517C41CA5E300ECC6D450BFF0B60BD98D1`, certificado debug cotejado. Se actualizó solo el runner con `-r`; `baseDebug` quedó en SHA-256 `D683909D6499DE6363DCB3887FD7814A0EE5E79EE34CFAB094C9FD5F4F9BB490`, Room38. Logs: `avd-live-program-persistence-r103-20261116.log`, `avd-live-program-transient-retry-r104-isolated-20261116.log`, `avd-live-program-persistence-r105-20261116.log` y `gradle-logs/req8-avd-retry-sync-build-r104-20261116.log`.
- Req. 8 sigue **Parcial**; faltan voz, sesión libre, rematerialización y recorridos visuales/reflejo del cambio en Semana/Inicio/editor. El plan general conserva 1 requisito aprobado y 12 parciales. Fase §5.3; todavía no es publicación final del árbol local.

## Continuación Android r118-r119 — 2026-11-16

- Se corrigió un defecto de calibración que la prueba libre CAL7 reprodujo dos veces: durante una sesión real el hidratador asigna un tag predeterminado UUID; el 1RM declarado sin tag quedaba como `NONE` y la sugerencia calculada ignoraba el dato de 88 kg. `declaredReferenceMatchesBaseContext` acepta únicamente la equivalencia con la UUID del tag `Default` registrado para el mismo ejercicio. Un tag de atleta, marca/setup, técnica, lado, carga o perfil distinto no pasa esa equivalencia.
- Evidencia JVM/Room r118: **43/43** (`WorkoutCalibrationTest` 23, `Cal7FreeSessionRoomTest` 3, `WorkoutCalibrationRecorderRoomTest` 17). El CAL7 completo ahora pasa, incluida resolución, reversión, cierre real del ViewModel, reapertura e inicio siguiente. Logs/XML en `acceptance-matrix.md`; el fallo r107/r114 fue diagnóstico reproducible y queda resuelto en r118. No es una aceptación AVD ni prueba de entrada acústica.
- Política de recurrencia revalidada sobre el código actual en r119: **59/59** (`ProgramEditorViewModelTest` 17, `ProgramTemplateEngineTest` 16, `ProgramMigrationEngineTest` 9, `ProgramProgressEngineTest` 17), con XML por clase en `junit-targeted/`.
- r109/r110/r111 y r113 fueron intentos de ejecutar dentro de una caché sandbox sin los plugins Foojay/AGP y no llegaron a compilar; durante r111 `settings.gradle.kts` se restauró byte por byte (SHA-256 antes/después `770C55B30FD15834AD1E178DFC90A0CED22D95634F04FE3CB8659B130B73C6DE`). Las tandas r112 y r114-r119 se ejecutaron con el wrapper/candado en el entorno autorizado con los plugins disponibles. No hubo instalación ni acción ADB durante estos tests.
- **Fase exacta:** §5.3, aceptación Android por requisito; se avanzó en la regresión de referencia/carga y política de recurrencia mediante JVM/Room. Req. 5, Req. 8 y Req. 13 siguen **Parciales**. Siguiente prioridad es completar en `emulator-5580` los cierres/reinicios recurrentes que faltan y los recorridos de sesión libre/voz/rematerialización de Req. 8. Después faltan las regresiones globales, build/copia `baseRelease` del SHA final, instalación de ese release en AVD y publicación/sincronización segura de `master`. iOS excluido.

## Continuación r120 — Req. 5 en AVD

- Se añadió `ProgramRecurringMultiBlockCycleDeviceTest`: 1/1 en `emulator-5580`. Cubre dos ciclos completos de dos bloques/cuatro semanas vía `finalizeWorkout`, la persistencia `Room`, y retry idempotente después de cerrar/reabrir Room. La base temporal se eliminó.
- `baseDebug` y runner instalados coinciden por SHA con las salidas locales; firma debug compatible y Room38. Evidencia y límites detallados en `avd-validation-receipt.md` y `acceptance-matrix.md`.
- r121 volvió a validar, sobre el `baseDebug` r120, la confirmación de reanudación legacy y el proceso detenido + Room reabierta: 1/1 + 1/1. La fixture fue eliminada, se detuvieron los paquetes de prueba y el AVD quedó disponible.
- Esta prueba no cierra todo Req. 5: siguen calendario abierto, pausa y retorno, preparación finita, catálogo `FINITE_CYCLE`, rutina importada y los reinicios/reintentos de esos estados. Tampoco es el recorrido de UI del cierre.
- La fase permanece §5.3; el estado global continúa en 1 requisito aprobado y 12 parciales. Próximo trabajo: esos estados restantes de Req. 5, sin retirar evidencia previa, y luego continuar las filas parciales antes del build release y la publicación final.

## Continuación r125 — aceptación UI del importador

- **Fase exacta:** plan §5 (Importador), dentro de §5.3 de aceptación Android por requisito en AVD. `ImportedRoutineScheduleDeviceTest` pasó **1/1** en `emulator-5580`: pegar dos sesiones, elegir variantes reales del catálogo, mover jueves a martes, guardar como rutina `REPEATING`, cerrar/reabrir Room y mostrar el `ProgramDetail` persistido. Días `{2,6}` preservados. Evidencia primaria: `avd-import-schedule-r125-20261116.log` y build `gradle-logs/import-schedule-avd-build-r125-20261116.log`; recibo detallado en `avd-validation-receipt.md`.
- El test directo de `ImportProgramScreen` no abre todavía la ruta de navegación desde Programas → Nuevo → Importar ni cubre cambios concurrentes del borrador, deshacer, cerrar/reanudar, foto/planilla/SABADO, avisos de baja confianza u OCR de galería/cámara. La progresión de ciclos y los reintentos de una rutina importada también quedan pendientes; no subir Req. 5 ni el importador a aprobado.
- Se actualizó solo el runner `.dbg.test` con `-r`, certificado cotejado; no se reinstaló `baseDebug`, ni se borraron los datos de KPKN Dev. La base QA y el borrador temporal desaparecieron; la lista `databases/` contiene solo `kpkn.db`. Se detuvieron únicamente `.dbg` y `.dbg.test`; `emulator-5580` quedó encendido y disponible.
- r122/r123/r124 quedan registrados como diagnósticos del test, no como fallos de producción. El agregado histórico AVD 121/121 no se recalculó ni se sumó este caso sin deduplicación.
- Siguen pendientes: aceptación del resto de filas parciales, migración representativa Room37→38, suites globales solicitadas, release firmado vinculado al SHA final y sincronización/publicación de `master`. El HEAD de la carpeta principal al hacer esta anotación es `4c4bab0d` y estos cambios continúan locales, sin commit ni push; iOS excluido.

## Cierre adicional r126 — ciclo de la rutina importada

- **Fase exacta:** sigue plan §5.3, aceptación Android por requisito en AVD; la ruta del importador ya guarda/reabre y demuestra un cierre recurrente. `ImportedRoutineScheduleDeviceTest` pasó 1/1 en 51.765 s en `emulator-5580`: la rutina `REPEATING` importada avanza ciclo 1→2 al cerrar sus dos sesiones, conserva `runId` y dos registros al reabrir Room, y un retry no duplica progreso ni historial. Build del runner: **BUILD SUCCESSFUL** (1m54s). Evidencia y hash en `avd-validation-receipt.md`.
- No es aceptación completa: el start/finalize posterior a `ProgramDetail` llama a las APIs reales de `ProgramRepository`; no recorre visualmente Entreno ni su pantalla de cierre. Room se reabrió en el mismo proceso. Siguen pendientes las rutas/casos enumerados en la matriz, además de navegación desde Programas, migración Room37 representativa, suite completa, build del release actual y publicación/sincronización `master`.
- **Por qué se extendió:** el alcance tiene 13 filas Android más Room37→38, regresiones seleccionadas/completas y una publicación vinculada al SHA final. Varias pruebas antiguas eran motor/componente y no demostraban el recorrido pedido. La ejecución completa JVM había excedido su timeout; una compilación paralela anterior agotó heap; y la entrega baseRelease disponible corresponde a `2ad50d96f`, no al árbol actual `4c4bab0d`, por lo que no es seguro entregarla como final. En la aceptación nueva del importador, r122/r123/r124 consumieron rondas por fallos del propio arnés (scroll del dock, selección «propio» sin identidad catalogada y doble `setContent`), no por tres defectos distintos de producción; quedaron corregidos antes del pase r126. También faltó imponer antes una línea de corte y evitar revalidaciones seriales del mismo flujo. Esos factores explican la demora, pero no completan las filas pendientes.
- Estado de entrega: esta evidencia y el test siguen como cambios locales sin commit. No hay SHA de código final ni APK release ligado a él; no se hizo push. iOS excluido.

## Continuación r128 — compatibilidad Room 37→38 en AVD

- **Fase exacta:** plan §5.3, aceptación Android en AVD y migración. `Room37To38UpgradeDeviceTest` pasó **1/1** en `emulator-5580` tras compilar el runner con el wrapper bloqueado. La base QA desde el esquema v37 preserva programa/portada/sesión, log y comida histórica; crea Room38; guarda ambas entidades nuevas por DAO, cierra/reabre Room y vuelve a leerlas. `PRAGMA integrity_check` devuelve `ok`. Logs `avd-room37-upgrade-r128-20261116.log` y `gradle-logs/room37-avd-build-r128-20261116.log`; detalle en el recibo AVD.
- El teardown r128 limpia también el `.lck` de MigrationTestHelper. Ya no quedan DB/lock `qa-room37-*`; `kpkn.db` y sus WAL/SHM siguen intactos. Se detuvieron solo `.dbg` y `.dbg.test`; `emulator-5580` permanece encendido. El APK base Room38 no se reinstaló, no se usó APK v37 y no se probó con datos reales del usuario.
- Req. 6 sigue **Parcial**: faltan navegación legacy de nutrición, plan/receta/comida representativos durante la actualización, ajuste/registro y check-in de Entreno con comparación antes/después. Req. 5 e importador también parciales; continúan suite completa, release actual ligado a SHA final y publicación/sincronización `master`.
- Cambios del test y de la evidencia siguen locales sobre `master` en `4c4bab0d`, sin commit/push. El APK Release disponible es el candidato histórico `2ad50d96f`, no debe entregarse como si incluyera la revisión final.

## Cierre final y entrega Android — `6bf2f5792` (2026-11-16)

- **Commit final de código en `master`**: `6bf2f5792a72147850e919e7606c0be0bd94067e` (`fix: complete Android remediation delivery and acceptance suite`). Incluye de forma selectiva los 45 archivos verificados (`17` de producción, `10` de `test` y `18` de `androidTest` bajo `android-native/app/src/`), preservando íntegro el WIP concurrente fuera del alcance.
- **APK `baseRelease` canónico**: [app-baseRelease-6bf2f5792.apk](../app-baseRelease-6bf2f5792.apk) (idéntico por hash a `android-native/app/build/outputs/apk/base/release/app-base-release.apk`).
  - SHA-256: `F10DDA01AA833D83C465236DB3F7B825B35B37520ED33D7B25CD32331BBFBD8F` (`286087403` bytes).
  - Identidad: `com.example.kpkn`, `versionCode 55`, `KPKN Pre Release 21`, Room 38 con migración `37 → 38`.
  - Firma (`apksigner verify --verbose --print-certs`): `Verifies`, v2/v3 válidos, certificado SHA-256 `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1`.
  - Mapping R8: `android-native/app/build/outputs/mapping/baseRelease/mapping.txt` (`526583695` bytes, SHA-256 `3B9EFA2888AA6CBE3FCBDA9D899B3E176E0879D1A674B8EE654C8532A60D4949`).
  - Build: `:app:assembleBaseRelease --max-workers=1`, **BUILD SUCCESSFUL** en `14m 16s`; log `gradle-logs/assemble-baseRelease-6bf2f5792.log` (SHA-256 `563835F1F557086DECB10C47992FA0C8D3A2BCE0F828AEC16C4F16B8482F881A`).
- **Smoke launch en AVD (`emulator-5580`)**: instalado con `adb -P 5045 -s emulator-5580 install -r --no-streaming releases/app-baseRelease-6bf2f5792.apk` conservando datos y tras cotejar certificado (`9E:8B:…:4B:C1`). Hash remoto verificado igual al canónico; arranque en frío (`LaunchState: COLD`, `TotalTime: 1724 ms`, `PID: 6966`, `topResumedActivity: com.example.kpkn/.MainActivity`, `CRASH_LINES=0`). Detalle en `avd-release-smoke-6bf2f5792.log`.
- **Validación ejecutada contra la revisión final**:
  - **JVM/Robolectric (`r141`)**: **22/22 clases aprobadas (250/250 pruebas, 0 fallos, 0 errores, 0 omitidas)** con candado Gradle (`gradle-logs/targeted-jvm-r141.log`, `junit-targeted/*-r141.xml`). La suite JVM completa histórica (>45 min timeout) no se repitió y se declara explícitamente como cobertura dirigida.
  - **AVD `emulator-5580` (`r135`–`r141`)**: **102/102 pruebas aprobadas en 25 suites** (5 suites nuevas de aceptación `16/16` + 20 suites modificadas y regresiones clave `86/86`; `baseDebug` SHA-256 `8B70B6387682D5247F45C3FFBBA45F121698D34B35F764029BAFBC7C448D7DDD`, runner `.dbg.test` `77F6020CFE28F4F8523C08C3277CE5E1E8365EC8E2D5170ECD9B468EFD667270`). `[]` bases `qa-` residuales; `font_scale` devuelto a `1.3`.
- **Estado de matriz (`acceptance-matrix.md`) y límites declarados**: **9 requisitos Aprobados (Android)** (`1, 2, 3, 4, 7, 9, 10, 11, 12`) y **4 Parciales** (`5` sin `force-stop` por separado en todas las subvariantes; `6` migrado desde DB v37 representativa y no desde APK histórico v37; `8` sin voz acústica física ni recorrido único continuo Semana→Inicio→editor; `13` con fixture común aprobado en Android AVD+JVM, pendiente paridad iOS/backend). Z Flip físico no tocado, iOS excluido, `QA10` secundario no creado en `emulator-5580` (Owner `0`). El APK binario `app-baseRelease-6bf2f5792.apk` queda en `releases/` sin seguimiento Git por política de tamaño.

