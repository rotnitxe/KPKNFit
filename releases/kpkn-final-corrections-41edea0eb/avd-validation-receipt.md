# Recibo de validación AVD

Destino de las pruebas instrumentadas: `emulator-5580` (`Pixel_9_Pro_XL`), paquete de prueba `com.example.kpkn.dbg`; usuario AVD `0`, fuente del sistema `1.3`. No se inspeccionó ni exportó la base de datos del usuario.

## APK de release final

- APK final de `2ad50d96f`: `com.example.kpkn`, `versionCode 55`, SHA-256 `0DFA793AAB19D43B632B3BBB6C78A2DF104FACFD7737EF73643D737B8B83EDB8`, en `releases/app-baseRelease-2ad50d96f.apk`.
- Antes de actualizar, el APK instalado daba el SHA histórico `A897E5C3C6EBB0059C6E71C16C6A68310877CFCE555E97463F103800B2DFDEB4`, correspondiente al certificado release esperado. Candidato e instalado usan certificado SHA-256 `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1`; ambos declaran Room 38. La actualización se hizo con `adb install -r --no-streaming`, sin borrar datos.
- Hash remoto posterior a la instalación, calculado sobre `pm path` → `base.apk`: `0dfa793aab19d43b632b3bbb6c78a2df104facfd7737ef73643d737b8b83edb8`; coincide con el artefacto local. `dumpsys package` confirma versión 55 / `KPKN Pre Release 21` y `lastUpdateTime=2026-11-15 13:38:58`.
- Smoke launch: `com.example.kpkn/.MainActivity` quedó como `topResumedActivity` en `emulator-5580`.
- Recuperación observada: el servidor ADB aislado `15045` quedó sin respuesta; se reinició únicamente ese servidor. `emulator-5580` se encontró en el ADB existente `5045` y todos los comandos posteriores llevaron `-s emulator-5580`. El primer `install -r` con streaming falló al cargar el APK temporal. El primer `--no-streaming` encontró Package Manager ausente; `/data` tenía 10–11 GB libres, así que no fue falta de espacio. Se hizo un reboot normal del AVD sin `-wipe-data`; después `sys.boot_completed=1`, Package Manager volvió a responder y el segundo `--no-streaming` terminó en `Success`.
- Ningún comando se dirigió a otro serial. No se ejecutó `pm clear`, no se inspeccionó ni exportó la base de datos y no se borraron datos. El reboot conservó el paquete anterior hasta completar la actualización.
- Las 121 pruebas AVD seleccionadas se ejecutaron en el paquete de pruebas `com.example.kpkn.dbg`; el smoke launch del release es evidencia aparte, no sustituye los recorridos de aceptación completos.

## Suites aprobadas

| Suite | Resultado | Evidencia |
|---|---:|---|
| `SessionRevisionFlowDeviceTest` | 11/11 | `avd-session-revision-20261115.log` |
| `WorkoutLifecycleDurabilityInstrumentedTest` | 5/5 | `avd-workout-lifecycle-20261115.log` |
| `PdfRendererImportInstrumentedTest` | 1/1 | `avd-pdf-renderer-20261115.log` |
| `AuditClosureMainActivityDeviceTest` | 1/1 | `avd-audit-main-activity-20261115.log` |
| `AuditClosureRuntimeDeviceTest` | 7/7 | `avd-audit-runtime-20261115.log` |
| `ManualSessionRestoreUiTest` | 5/5 | `avd-manual-session-restore-20261115.log` |
| `ExercisePickerCardsUiTest` | 2/2 | `avd-route-ExercisePickerCardsUiTest-20261115.log` |
| `ExerciseRmDeviceTest` | 5/5 | `avd-route-ExerciseRmDeviceTest-20261115.log` |
| `WorkoutStoryPreviewDeviceTest` | 2/2 | `avd-route-WorkoutStoryPreviewDeviceTest-20261115.log` |
| `WorkoutStoryDeviceTest` | 6/6 | `avd-route-WorkoutStoryDeviceTest-20261115.log` |
| `ProgramStoryExportDeviceTest` | 1/1 | `avd-route-ProgramStoryExportDeviceTest-20261115.log` |
| `ConceptosClaveUiTest` | 3/3 | `avd-fixed-ConceptosClaveUiTest-20261115.log` |
| `ExerciseFichaDeviceTest` | 7/7 | `avd-fixed-ExerciseFichaDeviceTest-20261115.log` |

Lote principal seleccionado: **56/56 aprobados**. Las primeras ejecuciones habían dado 54/56 por dos expectativas antiguas de test. `ConceptosClaveUiTest` ahora abre el control plegado `concept_search_toggle` y verifica el campo por tag. `ExerciseFichaDeviceTest` verifica los encabezados actuales «Preparación» y «Ejecución». No se cambió producción para hacer pasar esos casos.

## Reconciliación de selectores — 2026-11-15

- `assembleBaseDebug assembleBaseDebugAndroidTest`: `BUILD SUCCESSFUL`; APK baseDebug y runner instrumentado recompilados o verificados up-to-date.
- El APK baseDebug instalado (`com.example.kpkn.dbg`) coincide por SHA-256 con el build local. El runner `com.example.kpkn.dbg.test` se actualizó tras verificar certificado debug SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`; las suites previas usaron SHA `E1F29B176D527A19FABD17A1FB5A44B44B2B9B5D2241D402FD73871CC304472A`, y la repetición de cockpit `A3DF2FCAA3F92E257DF78A64F8AF1B4E9BEB24B764A5AC1458C5380EF7A5D346`. En la repetición final de LiveHeader el SHA-256 local e instalado coincide en `91060C52C35883AC6B6CCF28CFB6F263E6A9FF9E46356118A27CECA01491BD79`.
- Se ejecutaron los dos runners directamente y fijados: `adb -P 5045 -s emulator-5580 shell am instrument ...`; Conceptos 3/3 y ficha 7/7. Logs: `avd-fixed-ConceptosClaveUiTest-20261115.log` y `avd-fixed-ExerciseFichaDeviceTest-20261115.log`.
- `WorkoutCockpitDeviceTest` se recompiló con el fixture conectado al `KpknOverlayHost` de producción, instalando solo el runner tras verificar el certificado. En la repetición, ADB usó el puerto aislado 15045 y cada comando de dispositivo llevó `-s emulator-5580`. Resultado **6/6**. Log `avd-fixed-WorkoutCockpitDeviceTest-20261115.log`, SHA-256 `B309409F207AB02780317FAE88C0E9D5A09F826FF05B3FA5E23D055D77DA6E55`.
- `LiveHeaderSheetsDeviceTest` se recompiló con el host real de overlays, un ciclo de vida `STARTED` determinista para la hoja del cronómetro, y selectores alineados al contenido actual. El runner local e instalado coincide por SHA. Repetición completa **14/14** en `emulator-5580`; log `avd-fixed-LiveHeaderSheetsDeviceTest-20261115.log`, SHA-256 `F40452D785E80A3B6174E45C6E77117A2F3F1A9F758ACE78D0CBAD3F16371ECD`.
- El servidor ADB en 15045 informó una conexión de transporte terminada para `emulator-5562`; no se ejecutó ningún comando de estado, shell, instalación ni prueba contra ese serial. Todas las operaciones de esta repetición estuvieron fijadas a `emulator-5580`.
- `FoodLoggerDrawerDraftUiTest` se recompiló con `assembleBaseDebugAndroidTest` y pasó 3/3 con ese mismo runner fijado a `emulator-5580`. Log `avd-fixed-FoodLoggerDrawerDraftUiTest-20261115.log`, SHA-256 `B63C7C617C0A0D8BA23AF79409A7E37E853CD099B7230C623B6F940F28EC2A68`.
- Una invocación previa de `connectedBaseDebugAndroidTest` falló antes de instalar o ejecutar pruebas: Gradle usó el servidor ADB por defecto y encontró `emulator-5562` offline. No se interactuó con ese serial. Los ensayos posteriores fijaron todos los comandos al único AVD autorizado `emulator-5580`.
- El release `com.example.kpkn` continuó instalado y verificado por hash; las suites instrumentadas de Android corren contra el paquete de prueba `com.example.kpkn.dbg`. No atribuir el test runner al APK release.

## Suites AVD complementarias

Se ejecutaron, siempre contra `com.example.kpkn.dbg.test` y `emulator-5580`, estas suites adicionales. Sus fixtures no requieren importar datos personales:

| Suite | Resultado | Evidencia |
|---|---:|---|
| `WorkoutRecordingEffortDeviceTest` | 8/8 | `avd-route-WorkoutRecordingEffortDeviceTest-20261115.log` |
| `WorkoutSetInputOwnershipDeviceTest` | 4/4 | `avd-route-WorkoutSetInputOwnershipDeviceTest-20261115.log` |
| `WorkoutMediaPersistentRecoveryInstrumentedTest` | 1/1 | `avd-route-WorkoutMediaPersistentRecoveryInstrumentedTest-20261115.log` |
| `WorkoutPreparationDurabilityInstrumentedTest` | 1/1 | `avd-route-WorkoutPreparationDurabilityInstrumentedTest-20261115.log` |
| `WorkoutWarmupInventoryIntegrationTest` | 3/3 | `avd-route-WorkoutWarmupInventoryIntegrationTest-20261115.log` |
| `WorkoutRestModalUiTest` | 3/3 | `avd-route-WorkoutRestModalUiTest-20261115.log` |
| `WorkoutRestFeedbackDeviceTest` | 2/2 | `avd-fixed-WorkoutRestFeedbackDeviceTest-20261115.log` |
| `WorkoutExternalCameraCaptureInstrumentedTest` | 4/4 | `avd-fixed-WorkoutExternalCameraCaptureInstrumentedTest-20261115.log` |
| `WorkoutMobilityCanonicalKnowledgeUiTest` | 1/1 | `avd-route-WorkoutMobilityCanonicalKnowledgeUiTest-20261115.log` |
| `WorkoutSupportCardsUiTest` | 1/1 | `avd-route-WorkoutSupportCardsUiTest-20261115.log` |
| `WorkoutV2UiTest` | 7/7 | `avd-fixed-WorkoutV2UiTest-20261115.log` |
| `FoodLoggerDrawerDraftUiTest` | 3/3 | `avd-fixed-FoodLoggerDrawerDraftUiTest-20261115.log` |
| `VoiceFirstUseDeviceTest` | 4/4 | `avd-route-VoiceFirstUseDeviceTest-20261115.log` |
| `WorkoutCockpitDeviceTest` | 6/6 | `avd-fixed-WorkoutCockpitDeviceTest-20261115.log` |
| `LiveHeaderSheetsDeviceTest` | 15/15 | `avd-followup-20261115/TEST-LiveHeaderSheetsDeviceTest.xml` y `avd-followup-20261115/logcat-live-set-config.txt` |
| `HomeRetiredSessionCueUiTest` | 1/1 | `avd-followup-20261115/TEST-HomeRetiredSessionCueUiTest.xml` y `avd-followup-20261115/logcat-home-retired-cue.txt` |
| `ProgramCyclesSheetDeviceTest` | 1/1 | `plan-ui-fixes-20261115/avd-cycles-test.xml` y `plan-ui-fixes-20261115/avd-cycles-logcat.txt` |

Resultado complementario vigente: **65/65 en 17 suites**. Sumado al lote principal, hay **121/121 pruebas aprobadas en 30 suites seleccionadas**, no 121 recorridos completos del plan.

## Revalidación incremental posterior al agregado — 2026-11-16

- `LiveProgramPersistenceFlowDeviceTest` pasó **4/4** en `emulator-5580` con el runner `com.example.kpkn.dbg.test`. APK instrumentado SHA-256 `576335049CD1988955350B9FB86BFEC6AD01AD49961E8649A07B3DBA3F03C693`, certificado debug SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Antes de `install -r`, se verificó que el APK candidato y el instalado compartían el certificado. Solo se actualizó el paquete `.dbg.test`.
- El caso nuevo ejecutó `WorkoutScreen → Guardar en el programa → ProgramDetail → SessionEditor` con un borrador sucio escrito bajo la clave canónica de semana; se confirmó la persistencia del marcador de cambio en vivo, ambas versiones y las dos opciones del diálogo. La fixture canceló solo su sesión, descartó el aviso de permisos con Ahora no, y limpió su DB/preferencias al terminar.
- Log de clase: `avd-live-program-draft-conflict-class-r12-20261116.log`; repetición individual: `avd-live-program-draft-conflict-ui-r11-20261116.log`. Esta revalidación es posterior al agregado deduplicado 121/121; no se sumó al total histórico porque no se volvió a deduplicar el inventario completo.
- `assembleBaseDebugAndroidTest` reconstruyó el runner con el candado Gradle compartido y reutilizando la distribución/caché local. La clase `LiveProgramPersistenceFlowDeviceTest` volvió a pasar **4/4** tras añadir el doble toque; log `avd-live-program-persistence-flow-class-r16-20261116.log`. El APK instrumentado instalado tuvo SHA-256 `50EC661AF68526A75D5D97F9A5FC979A7B957B69240F77078827CF40722F9C49` y certificado debug `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. El test comprobó un solo registro con el `operationId` capturado y distinguió la foto inicial de la edición nueva; solo se actualizó `com.example.kpkn.dbg.test`.
- Para evitar mezclar la medición con la escritura previa del cambio de sesión, la prueba espera `recordingSetKey == null` antes de activar dos veces «Guardar en el programa». El estado anterior se registra en `avd-live-program-permanent-doubletap-r14-20261116.log` (la aserción inicial contó incorrectamente la foto inicial) y `avd-live-program-permanent-doubletap-r15-20261116.log` (el guard de escritura seguía ocupado); ninguno se contabiliza como aprobado. R16 es la repetición vigente.
- El caso `permanentEditRetriesAfterTransientRoomWriteFailure` pasó **1/1** en `avd-live-program-transient-retry-r17-20261116.log`; después, la clase completa pasó **5/5** en `avd-live-program-persistence-flow-class-r18-20261116.log`. APK instrumentado SHA-256 `AD8E039FBE7C04409D8925B37C13FBE1B30FF24D4E0D11CE88ADADECC379DBE8`; certificado debug SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.
- El fallo fue inyectado con triggers SQLite temporales sobre `programs` en la DB desechable `qa-live-edit-<suffix>.db`. La UI mantuvo el cambio y la confirmación pendiente; el programa quedó idéntico y no apareció una versión parcial para ese `operationId`. El test quitó los triggers, reintentó desde el diálogo con el mismo ID y confirmó el programa, la foto inicial y una sola versión de edición. El `finally` elimina ambos triggers y la fixture cierra y borra su DB y preferencias propias.

- El ensayo r19 quedó conservado como fallo diagnóstico: al cerrar y reabrir la DB fixture, el reemplazo había perdido los `plannedSlotId` de las series. `WorkoutReplacementRules.slotPlan` ahora conserva esa identidad estable; el fallo no se cuenta como aprobado.
- `WorkoutReplacementRulesTest` pasó **8/8**; XML `junit-targeted/TEST-com.example.kpkn.screens.workout.WorkoutReplacementRulesTest.xml`; log Gradle `gradle-logs/workout-replacement-planned-slot-r2-20261116.log`.
- `assembleBaseDebug assembleBaseDebugAndroidTest` pasó con el candado Gradle; log `gradle-logs/live-replacement-planned-slot-app-r21-20261116.log`. APK baseDebug SHA-256 `9152531EE06FD8AC7F03DBBA063BFF56FE94477A41556229338E84D59BFDE87E`; runner SHA-256 `C668237683001E834C4CE7858D31240381120D875A0E38A48E6D9F7A6F6D8590`; ambos certificados debug SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.
- En `emulator-5580`, `com.example.kpkn.dbg` y `com.example.kpkn.dbg.test` se actualizaron con `adb install -r` después de comparar sus certificados. La extracción posterior por `pm path` confirmó que ambos APKs instalados coinciden byte a byte con los locales. La app es versionCode 55 y Room v38; no se modificó el esquema ni se borraron datos.
- `permanentReplacementFromLiveOptionsChangesOnlySourceAndFutureVariantA` pasó **1/1** en r20 y `LiveProgramPersistenceFlowDeviceTest` pasó **6/6** en r21 tras reconstruir y reinstalar KPKN Dev. Verifica el reemplazo en la hoja real, el origen y la próxima ocurrencia, la semana pasada y variante B intactas, y la persistencia de Room tras cerrar/reabrir la fixture. Logs `avd-live-program-replacement-planned-slot-r20-20261116.log` y `avd-live-program-persistence-flow-class-r21-20261116.log`. No se añadió al agregado histórico 121/121.
- La baja permanente se cubrió con el flujo real de opciones → confirmación «Quitar» → «Guardar en el programa». El primer selector r23 buscó un `ContentDescription` que la opción no publica y falló antes de actuar; el log se conserva sin contarse. Alineado al texto visible, el caso pasó **1/1** en r25 con cierre y reapertura de Room. Log `avd-live-program-permanent-exercise-removal-r25-20261116.log`.
- El primer pase combinado r26 y su repetición r28 agotaron 30 s en `permanentEditRetriesAfterTransientRoomWriteFailure`; ambos logs permanecen como resultados negativos. El caso pasó **1/1** aislado en r27 y la repetición final de `LiveProgramPersistenceFlowDeviceTest` pasó **7/7** en r29. Log r27 `avd-live-program-transient-retry-r27-20261116.log`; log vigente `avd-live-program-persistence-flow-class-r29-20261116.log`.
- El runner r29 conserva certificado debug SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`; el SHA-256 local e instalado coincide en `B9D25F46D22253ADCCC1FCF7187886404D00BB097EB934EBA86707D292E975E1`. Se actualizó con `install -r` solo `com.example.kpkn.dbg.test` en `emulator-5580`.
- `permanentCatalogAdditionChangesOnlySourceAndFutureVariantA` pasó **1/1** en r42. En la sesión real: Opciones → Agregar ejercicio → catálogo → «Aperturas Declinadas» → «Guardar en el programa». La fixture confirmó alta en semana 2 y próxima ocurrencia por el ancla estable, preservó semana 1, sesión ajena y ambas variantes B, comprobó una sola operación y reabrió Room antes de eliminar su DB y preferencias QA. Base `com.example.kpkn.dbg` no se reinstaló y conservó SHA-256 `9152531EE06FD8AC7F03DBBA063BFF56FE94477A41556229338E84D59BFDE87E`; solo se actualizó `com.example.kpkn.dbg.test` con `install -r` tras confirmar certificado debug `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Runner SHA-256 `17FC16784DD556524E273255B65D67FF4AE26AA265AF6C6DD545DC7AD65421E`. Log r42 `avd-live-program-permanent-catalog-add-r42-20261116.log`; build runner `gradle-logs/live-program-permanent-catalog-add-scope-r41-20261116.log`.
- La clase completa `LiveProgramPersistenceFlowDeviceTest` pasó **8/8** en r43 sobre el mismo `emulator-5580` y el mismo runner. Log `avd-live-program-persistence-flow-class-r43-20261116.log`.
- Intentos r32/r34/r36/r38/r40 se conservan como negativos de automatización/selectores; ocurrieron antes de confirmar la escritura permanente y no cuentan como aceptación. El positivo r42 valida el flujo completo. Logs: `avd-live-program-permanent-catalog-add-r32-20261116.log`, `avd-live-program-permanent-catalog-add-r34-20261116.log`, `avd-live-program-permanent-catalog-add-r36-20261116.log`, `avd-live-program-permanent-catalog-add-r38-20261116.log` y `avd-live-program-permanent-catalog-add-r40-20261116.log`.
## Pruebas exploratorias fuera del total

- `ImportedEditorDraftPersistenceInstrumentedTest`: 0/2; la suite de plataforma se declara compatible solo con API 24/25 y el AVD actual rechazó esa guardia. La suite JVM/Robolectric `ImportedEditorDraftPersistenceTest` sí pasó 19/19, pero no sustituye la comprobación de plataforma API 24/25.
- `LiveSetModesDeviceTest`: 0/18; el fixture usa el `LiveModeSetContent`/Relator legacy, no conectado desde el código principal. Espera una tarjeta fija de 425 dp, mientras `LiveSessionCardFrame` deriva el alto del viewport (394/430/470/488 dp); en este AVD midió 488 dp. También busca selectores del componente antiguo (`live-quantity-input`, «Iniciar»); la ruta activa usa `live-card-quantity-input` y `live-card-set-clock`. `live-rm-edit` permanece solo en ese componente legacy. No se toma esta suite como prueba de un defecto actual ni como aceptación positiva; `WorkoutV2UiTest` (7/7) aporta cobertura del flujo activo, y los recorridos visuales completos siguen pendientes.
- Historial de `WorkoutCockpitDeviceTest`: el intento inicial dio 2/6 con fixture/expectativas anteriores; se conectó el overlay host real y se alinearon los selectores y acciones actuales. La repetición final pasó 6/6 y está incluida en el total aprobado. El log inicial `avd-route-WorkoutCockpitDeviceTest-20261115.log` se conserva como antecedente, no como resultado actual.
- Historial de `LiveHeaderSheetsDeviceTest`: el primer intento dio 7/14 y el intento con selectores actualizados dio 10/14. El host de overlays, el ciclo de vida determinista para los relojes y los rótulos vigentes resolvieron las causas del fixture. La repetición final pasó 14/14 y está incluida en el total. El log inicial `avd-route-LiveHeaderSheetsDeviceTest-20261115.log` se conserva como antecedente; su SHA-256 es `3A9EEE33EF724583BE48CC7E1F52B8E755E6EBBFE6A0ADC0179A50101473DF8F`.
- `WorkoutV2UiTest`: el primer intento dio 3/7 por selectores y estados de fixture anteriores al refinamiento. La fixture ahora toca la ruta real por tag, usa la pestaña `Drop`, el reloj `live-card-set-clock` y la acción «Añadir tramo»; la repetición pasó 7/7.
- `WorkoutRestFeedbackDeviceTest`: el primer intento dio 0/2 por flujo/selectores antiguos; se actualizó para abrir la hoja de molestias y confirmar con «Listo» y «Guardar feedback». La repetición actual pasó 2/2.
- La primera ejecución de `WorkoutExternalCameraCaptureInstrumentedTest` dio 3/4 por una expectativa `Podés` frente al mensaje actual `Puedes`; se corrigió la expectativa y la repetición dio 4/4.
- `FoodLoggerDrawerDraftUiTest`: las primeras ejecuciones usaban el botón y el resumen anteriores, y trataban la descripción analizada como un campo siempre editable. Se alineó el fixture con «Analizar», el resumen actual y el control compacto que abre edición; la repetición pasó 3/3. Los logs de fallos previos permanecen conservados.

Los fallos y guardias exploratorios restantes siguen fuera del total y no son evidencia positiva. Se conservaron los logs `avd-extra-*.log`, el intento inicial de cockpit `avd-route-WorkoutCockpitDeviceTest-20261115.log` (SHA-256 `2112AEA1A75869E5286792FD9B84E1F590402C39A3095E684B395DF8A3CB47D5`) y el intento inicial de cabecera `avd-route-LiveHeaderSheetsDeviceTest-20261115.log` (SHA-256 `3A9EEE33EF724583BE48CC7E1F52B8E755E6EBBFE6A0ADC0179A50101473DF8F`). Los resultados vigentes son cockpit 6/6 y cabeceras 14/14; no se relajaron aserciones del producto.

## Límites de aceptación

- Las pruebas de inicio, sesión, historia y PDF cubren esos casos concretos, no los 13 recorridos completos del plan.
- Room37 se probó con una base sintética; no se actualizó una copia real de datos antiguos con rutinas, recetas y portadas.
- Las pruebas de Setup que exigen el usuario QA10 no se ejecutaron: `emulator-5580` estaba en el usuario Owner `0`; no se creó otro usuario.
- No se utilizó el Z Flip ni se afirmó QA físico.

## Revalidación de edición de prescripción — 2026-11-16

- Hallazgo reproducible: `applyLiveSetConfig` rechazaba el guardado de una edición de repeticiones si la serie y el ejercicio no tenían descanso definido. El descanso es opcional en `Exercise` y `ExerciseSet`; ahora el campo vacío hereda `restTime` del ejercicio cuando existe y permite dejarlo sin configurar cuando no existe. No se alteró Room ni su versión.
- `LiveSetConfigSheetTest.prescriptionEditAllowsAnExerciseWithoutConfiguredRest`: **1/1** pasó en `testBaseDebugUnitTest`; XML `android-native/app/build/test-results/testBaseDebugUnitTest/TEST-com.example.kpkn.screens.workout.components.LiveSetConfigSheetTest.xml`; log `gradle-logs/live-config-rest-fix-r57-20261116.log` (`BUILD SUCCESSFUL`).
- Intentos diagnósticos conservados: r57 instaló un `baseDebug` viejo (SHA `9152531E…`, APK fechado 06:18 antes de la edición de código a las 08:26); su resultado no valida el código nuevo. r58 ya usó el APK nuevo `1ED46180…`, pero el test retornó antes de que cambiara la prescripción porque esperaba cualquier `pendingStructuralPersistence`/aviso, no el valor editado. Se corrigió la sincronización del test; ninguno de esos intentos fallidos se cuenta como aceptación. Logs `avd-live-program-prescription-edit-r57-20261116.log` y `avd-live-program-prescription-edit-r58-20261116.log`.
- `assembleBaseDebug assembleBaseDebugAndroidTest` pasó en r58 y r59 con el candado Gradle compartido; logs `gradle-logs/live-config-rest-assemble-r58-20261116.log` y `gradle-logs/live-config-rest-assemble-r59-20261116.log`. `baseDebug` SHA-256 `1ED461806514701DBE5D0D97D48A0B2A764CDC65A126A5CACBFC779D9C446111`; runner `baseDebugAndroidTest` SHA-256 `58277497D44AA05D6F1712C387D5B72E3CA910FBFBB66114AB63BA7A5318B32F`. Ambos tienen certificado debug SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.
- La prueba individual con la hoja real `permanentPrescriptionEditChangesOnlySourceAndFutureVariantA` pasó **1/1** en r59, log `avd-live-program-prescription-edit-r59-20261116.log`. La clase completa `LiveProgramPersistenceFlowDeviceTest` pasó **9/9** en r60, log `avd-live-program-persistence-r60-20261116.log`. Incluye origen/futura ocurrencia y variante A, pasado/sesión ajena/variante B intactos, Room reabierta y limpieza de la fixture desechable. Ambos APKs se actualizaron con `-r` solo en `emulator-5580` después de cotejar el certificado. El APK release y la app personal no se tocaron.
- Resultado nuevo de Req. 8: edición de prescripción real comprobada en AVD. La matriz general sigue parcial; estos 9 casos no se agregan al total histórico 121/121 porque aún no se deduplicó el inventario.

## Diagnóstico de alta de serie y reconstrucción — 2026-11-16

- `permanentSeriesMenuAdditionChangesOnlySourceAndFutureVariantA` en r62 observó la serie al final de la ocurrencia futura, aunque el orden de origen estaba invertido. La prueba no es aceptación ni concluye el resultado del código corregido: la app baseDebug instalada era el APK de las 08:41 (`1ED46180…`), mientras `WorkoutStructuralPersistenceController.kt` ya llevaba el cambio de anclaje a las 09:07. El runner r61 sí era nuevo. Se conserva el log `avd-live-program-series-menu-add-r62-20261116.log` como diagnóstico con binario desfasado.
- El parche actual añade a `PendingStructuralChange.AddSet` el ordinal y el `plannedSlotId` predecesor del orden editado. Cada ocurrencia futura inserta tras ese único predecesor; un anclaje ambiguo o ausente falla cerrado. Para preservar llamadas antiguas sin anclaje, mantiene la compatibilidad de añadir al final cuando el ordinal coincide con el tamaño futuro.
- Regresión JVM añadida en `WorkoutStructuralPersistenceReplayTest.addSetUsesStablePredecessorWhenFutureOccurrenceHasDifferentSetOrder`: verifica origen, próxima semana invertida, semana pasada y variante B. El runner AVD r63 incluye el caso real desde la hoja de opciones y reapertura de Room.
- `testBaseDebugUnitTest --tests "*.WorkoutStructuralPersistenceReplayTest" assembleBaseDebug assembleBaseDebugAndroidTest` terminó **BUILD SUCCESSFUL** en 10m 41s, bajo `run-gradle-locked.ps1`. La regresión JVM pasó **14/14**, XML `junit-targeted/TEST-WorkoutStructuralPersistenceReplayTest-addset-r63.xml`; log completo `gradle-logs/live-addset-anchor-r63-20261116.log`.
- El APK `baseDebug` nuevo es SHA-256 `D683909D6499DE6363DCB3887FD7814A0EE5E79EE34CFAB094C9FD5F4F9BB490`; runner `baseDebugAndroidTest` SHA-256 `96DCA61821886B6F2604DF8C2068035868E1191550CC07CC941F251A7BFD5BED`. Ambos verifican firma debug `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. App `com.example.kpkn.dbg`, v55, y runner `com.example.kpkn.dbg.test` se actualizaron con `-r`, usando `adb -P 5045 -s emulator-5580`; la app anterior también era v55, Room continúa en v38 y no hubo cambios de esquema.
- `permanentSeriesMenuAdditionChangesOnlySourceAndFutureVariantA` pasó **1/1** en r64 y `LiveProgramPersistenceFlowDeviceTest` pasó **10/10** en r65 (72,519 s), con ese APK y Room reabierta por el caso. La nueva serie queda tras su predecesora estable incluso cuando la ocurrencia futura tiene otro orden; semana pasada, sesión ajena y variante B permanecen intactas. Logs `avd-live-program-series-menu-add-r64-20261116.log` y `avd-live-program-persistence-r65-20261116.log`.
- `sha256sum` ejecutado sobre las rutas instaladas en `emulator-5580` confirma coincidencia exacta con los artefactos locales: app `d683909d6499de6363dcb3887fd7814a0ee5e79ee34cfab094c9fd5f4f9bb490`; runner `96dca61821886b6f2604df8c2068035868e1191550cc07cc941f251a7bfd5bed`.
- El primer intento sandbox de r63 no llegó a iniciar Gradle porque solicitó descarga de 9.4.1. Se repitió con el wrapper y la distribución/caché local existentes; no se copió la distribución ni se dejó una descarga adicional.

## Req. 5 — recurrencia, ficha real y reapertura de Room (2026-11-16)

- `ProgramDetailRecurringResumeDeviceTest` compila con `assembleBaseDebugAndroidTest`, wrapper/candado `run-gradle-locked.ps1`; r70 finalizó `BUILD SUCCESSFUL` (1m 4s). Log `gradle-logs/recurrence-avd-build-r70-20261116.log`. APK runner SHA-256 `50B685211D47870ADE981939469D09A6A40B2A189CCBE9DD184641FCD613E7FF`, certificado debug `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.
- Solo se instaló con `-r` el runner `com.example.kpkn.dbg.test` en `emulator-5580`. La app bajo prueba `com.example.kpkn.dbg` continuó en v55 con APK baseDebug SHA-256 `D683909D6499DE6363DCB3887FD7814A0EE5E79EE34CFAB094C9FD5F4F9BB490`; Room sigue en v38. Certificados cotejados antes de la instalación.
- Seed de la prueba AVD: **1/1**, log `avd-recurrence-seed-r70-20261116.log`. Monta `ProgramDetailScreen`; comprueba el diálogo por programa activo, Cancelar sin cambios, confirmación de reanudar una rutina legacy, ciclo 2, mismo `runId`, agenda de días e historial previo.
- Reinicio de proceso: se ejecutó `am force-stop` solo para los dos paquetes QA en `emulator-5580`, se verificó que `pidof com.example.kpkn.dbg` no devolviera PID, y se lanzó una segunda invocación de instrumentación. La reapertura desde el archivo Room confirmó estado activo, ciclo 2, `runId`, agenda e historial: **1/1**, log `avd-recurrence-resume-r70-20261116.log`.
- El teardown eliminó la fixture de DB `qa-recurrence-qaRecurring20261116R68A.db`; una lectura de `run-as com.example.kpkn.dbg ls databases` no encontró ese nombre. No se modificó la app personal ni se usaron otros dispositivos.
- Diagnóstico, no incluido como fallo de producto: r68 expuso que `setUp`/`tearDown` inferían retorno no `Unit`; r69 reveló que el host aislado no había llamado `initializeExerciseDatabase`, requerido por el ViewModel. Se corrigió el fixture y r70 pasó ambos casos. El wrapper del comando también se ajustó: `INSTRUMENTATION_CODE: -1` aparece tras el resultado normal `OK (1 test)`, así que el criterio de pase es `OK (1 test)` y ausencia de `FAILURES!!!`.
- Alcance restante de Req. 5: AVD r97 cierra la ruta UI/Room de resolución terminal medida; siguen pendientes los escenarios finitos/recurrentes de reinicio/reintento enumerados en la matriz. Este pase no cierra el requisito completo.

## Req. 8 — corrección de serie completada en la sesión (2026-11-16)

- `assembleBaseDebugAndroidTest` pasó en r75 (**BUILD SUCCESSFUL**, 1m09s), log `gradle-logs/live-completed-set-edit-build-r75-20261116.log`. Runner `com.example.kpkn.dbg.test` SHA-256 `27626E2A0715591280C714E4ABE5A530F142226010EB755B15CBB4BF4A40FA4D`; certificado debug `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Solo se actualizó el runner con `-r` en `emulator-5580`; `baseDebug` instalada quedó intacta, SHA-256 `D683909D6499DE6363DCB3887FD7814A0EE5E79EE34CFAB094C9FD5F4F9BB490`.
- `correctingACompletedSetChangesExecutionOnlyAndNeverOffersPermanentPlanScope` pasó **1/1** en `avd-live-completed-set-correction-r75-20261116.log`: registra 8 reps y corrige a 7 con `patchCompletedSet` en una sesión real; confirma ejecución actualizada, plan y expectativas originales intactas, sin alcance permanente ni nueva versión del programa; comprueba el plan tras reabrir Room.
- Clase completa r75: **10/11** por timeout de un test preexistente de reintento transitorio Room; la repetición aislada de ese caso pasó **1/1**. Clase completa repetida r76: **11/11**, `avd-live-program-persistence-r76-20261116.log` (`OK (11 tests)`). El test nuevo pasó en ambas corridas. El timeout de r75 no se atribuye a una regresión de producto.
- `run-as com.example.kpkn.dbg ls databases` no mostró fixtures `qa-live-edit-*` remanentes. No se tocó la app personal ni se probó voz acústica: el caso invoca el ViewModel con la sesión UI activa, no automatiza el control visual que introduce el dato.
- Req. 8 permanece **Parcial** por voz, sesión libre, aceptación visual completa, rematerialización y matrices por variante/bloque. No sumar estos resultados al agregado histórico 121/121 sin deduplicar.

## Req. 5 — 1RM terminal registrado desde la ficha y Room reabierta (2026-11-16)

- Build r96: `assembleBaseDebugAndroidTest`, **BUILD SUCCESSFUL** en 1m09s, wrapper/candado y modo offline; log `gradle-logs/terminal-one-rm-avd-build-r96-20261116.log`.
- El test `ProgramDetailTerminalOneRmDeviceTest.recordedTerminalOneRmFromProgramDetailPersistsAfterRoomReopen` pasó **1/1** en r97, log `avd-terminal-one-rm-r97-20261116.log`. En la ficha real abre Más opciones → Pendiente → Registrar tus máximos, introduce y guarda S/B/D de 200/120/220 kg, cierra y reabre Room y comprueba ciclo 1 finito completado, mismo `runId`, resolución `RECORDED`, audit trail, perfil y objetivos.
- Runner local/remoto `.dbg.test`: SHA-256 `D1E6076F4EB0D6F88C4A7845614344980F0D018E91670A41146D21D6D76BEEDD`; certificado debug `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Solo se actualizó el runner con `-r` en `emulator-5580`. App `baseDebug` instalada permaneció en SHA-256 `D683909D6499DE6363DCB3887FD7814A0EE5E79EE34CFAB094C9FD5F4F9BB490`; Room38, sin cambio de esquema.
- Teardown borró la DB fixture `qa1rm-*`; después `run-as com.example.kpkn.dbg ls databases` no encontró coincidencias. El cierre/reapertura fue de Room dentro del mismo proceso de instrumentación, sin `force-stop`; no se tocó la app personal ni otro dispositivo.
- Req. 5 continúa **Parcial** por los estados finito/recurrentes restantes, calendario/pausas, preparación/catálogo/importación y sus reinicios/reintentos. No sumar este 1/1 al agregado histórico 121/121 sin deduplicar.

## Reintento de edición permanente en vivo — Req. 8 (2026-11-16)

- La clase completa r103 quedó 10/11: el caso de escritura transitoria no vio el rechazo esperado porque el test pulsaba «Guardar en el programa» mientras seguía activa la escritura previa de la edición local. `canMutateLivePlan()` rechazó correctamente el toque ocupado; los triggers seguían instalados y no hubo versión parcial ni intento de mutación. El caso aislado anterior r17 ya había pasado, así que no se clasificó como regresión del producto.
- Se corrigió la sincronización del test `removeFirstPlannedSet`: tras esperar `recordingSetKey == null`, ahora espera `awaitPendingLiveMutation(30_000)` antes de pulsar el alcance. Build del runner `assembleBaseDebugAndroidTest --offline`: **BUILD SUCCESSFUL**, log `gradle-logs/req8-avd-retry-sync-build-r104-20261116.log`.
- El runner actualizado tiene SHA-256 `9A0160A79127C6C887A8321B7C9DA1517C41CA5E300ECC6D450BFF0B60BD98D1` y certificado debug `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Se instaló con `-r` únicamente en `emulator-5580`; el APK baseDebug mantuvo SHA-256 `D683909D6499DE6363DCB3887FD7814A0EE5E79EE34CFAB094C9FD5F4F9BB490` y Room38.
- El caso aislado `permanentEditRetriesAfterTransientRoomWriteFailure` pasó **1/1** en r104 (`avd-live-program-transient-retry-r104-isolated-20261116.log`); la repetición de la clase completa pasó **11/11** en r105 (`avd-live-program-persistence-r105-20261116.log`). Incluye la operación única tras retry y persistencia con reapertura de la fixture Room.
- r103 se conserva como diagnóstico; r104 y r105 son la evidencia aprobada. Esta prueba no cubre entrada por voz, sesión libre, rematerialización ni aceptación visual en Semana/Inicio/editor; Req. 8 continúa **Parcial**.

## Req. 5 — dos ciclos en dos bloques y reintento tras reabrir Room (2026-11-16)

- Build con el wrapper y candado compartido: `:app:assembleBaseDebug :app:assembleBaseDebugAndroidTest`, **BUILD SUCCESSFUL** en 1m34s. Log `gradle-logs/recurrence-multiblock-avd-build-r120-20261116.log`.
- Los APK locales tenían identidad `com.example.kpkn.dbg`/`.dbg.test` y certificado debug SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`, igual al de los paquetes instalados. Base y runner se actualizaron con `install -r` solo en `emulator-5580`, sin borrar datos. `baseDebug` local e instalado: `5FCE830E3933DBC171F09F7A8E1DF392C778265627A1857F5D1FA805564D0460`; runner local e instalado: `BB8FC7B5E809A99572D4902419EEB3F3B26BCDE061386824FE2499276C524480`. KPKN Dev es v55 y Room38; no cambió el esquema.
- `ProgramRecurringMultiBlockCycleDeviceTest.twoBlockRoutineClosesTwoCyclesAndRetryAfterRoomReopenDoesNotAdvanceAgain` pasó **1/1** (`OK (1 test)`, 3.028 s), log `avd-recurrence-multiblock-r120-20261116.log`. La fixture crea dos bloques con dos semanas y dos sesiones semanales. `ProgramRepository.finalizeWorkout` cierra las 16 sesiones en dos ciclos, mantiene el mismo `runId`, identidades por ciclo, días 2/4 y política recurrente. Después de reabrir Room, el run permanece activo en ciclo 3 y reintentar el último recibo no avanza ni duplica los 16 registros.
- Es una prueba AVD de la API de producción del repositorio y Room; no automatiza la pantalla de cierre. La reapertura cierra Room dentro del mismo proceso de instrumentación y no equivale a muerte del proceso. `@After` eliminó la DB de fixture; `run-as com.example.kpkn.dbg ls databases` no encontró `qa-cycle-*`. No se modificó la app personal ni se usó otro serial.
- r121 revalidó la ruta UI de reanudación legacy en el `baseDebug` r120: el seed de la ficha y su confirmación pasó **1/1** (`avd-recurrence-seed-r121-20261116.log`); después se detuvieron solo `com.example.kpkn.dbg` y `.dbg.test`, se comprobó que no quedaran PID y la segunda invocación leyó Room y pasó **1/1** (`avd-recurrence-resume-r121-20261116.log`). La fixture `qa-recurrence-r121_20261116` no quedó en `databases/`.
- Al terminar, KPKN Dev y su runner quedaron detenidos y `emulator-5580` siguió encendido/disponible. No se usó ni detuvo otro dispositivo.
- Req. 5 queda **Parcial**: aún faltan calendario abierto, pausa calendarizada/retorno, preparación finita, catálogo `FINITE_CYCLE`, rutina importada y las matrices de reinicio/reintento para esos estados.

## Req. 5 / regresión del importador — pegar, guardar y reabrir — r125 (2026-11-16)

- `:app:assembleBaseDebugAndroidTest` pasó con wrapper/candado Gradle: **BUILD SUCCESSFUL** en 1m20s; log `gradle-logs/import-schedule-avd-build-r125-20261116.log`.
- `ImportedRoutineScheduleDeviceTest.pasteMoveThursdayToTuesdaySaveReopenRoomAndOpenMatchingProgramDetail` pasó **1/1** en `emulator-5580` (`OK (1 test)`, 42.985 s), log `avd-import-schedule-r125-20261116.log`. La prueba pega en la pantalla real del importador dos sesiones (jueves/Sentadilla y sábado/Press banca), elige una sugerencia de catálogo válida para cada ejercicio, cambia jueves a martes en el planificador, guarda, comprueba `ProgramEndMode.REPEATING` y días `{2,6}`, cierra/reabre Room y muestra el `ProgramDetailScreen` del ID/título persistido.
- El runner local `.dbg.test` SHA-256 es `CA17C7F038E18FE303E9A5EC4851D58BF70349A496368C8D2C992AD138CD288F`; certificado debug cotejado con KPKN Dev: `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Se actualizó con `install -r` solo el runner en `emulator-5580`; la app base `.dbg` no se reinstaló ni cambió (v55, Room38; SHA de base r120 `5FCE830E3933DBC171F09F7A8E1DF392C778265627A1857F5D1FA805564D0460`). No se borraron datos de la app.
- La base aislada `qa-import-schedule-*` y su borrador temporal se borraron en `@After`; `run-as com.example.kpkn.dbg ls databases` devolvió solo `kpkn.db`. Al terminar se detuvieron únicamente `com.example.kpkn.dbg` y `com.example.kpkn.dbg.test`; ambos quedaron sin PID y `emulator-5580` continuó encendido/disponible.
- Diagnósticos del test, no fallos de producto: r122 detectó un `performScrollTo` aplicado al dock fijo; r123 mostró que «Dejarlo como propio» no crea un ejercicio propio utilizable; r124 detectó el segundo `setContent` en el mismo `Activity`. Se corrigió la prueba para usar sugerencias reales y una composición única; r125 es el pase de aceptación.
- Alcance que sigue pendiente: entrada completa desde Programas → Nuevo → Importar, cambios rápidos durante guardado, deshacer, cerrar/reanudar, foto jueves/sábado con `SABADO`, planilla con pestañas auxiliares, copia entre semanas, avisos de baja confianza, OCR de galería/cámara y progreso real de ciclos de la rutina importada. Esto no cierra Req. 5 ni la regresión del importador.

## Req. 5 / ciclo de rutina importada y retry tras Room reopen — r126 (2026-11-16)

- `:app:assembleBaseDebugAndroidTest` pasó con wrapper/candado Gradle: **BUILD SUCCESSFUL** en 1m54s; log `gradle-logs/import-schedule-avd-build-r126-20261116.log`.
- `ImportedRoutineScheduleDeviceTest.pasteMoveThursdayToTuesdaySaveReopenRoomAndOpenMatchingProgramDetail` pasó **1/1** en `emulator-5580` (`OK (1 test)`, 51.765 s), log `avd-import-schedule-r126-20261116.log`. Además del recorrido UI descrito en r125, inicia la rutina importada mediante el repositorio, finaliza sus dos sesiones con `ProgramRepository.finalizeWorkout`, verifica ciclo 2, `runId` estable y dos registros; cierra/reabre Room y reintenta el último recibo sin volver a avanzar ni duplicar el historial.
- Runner local `.dbg.test` SHA-256 `9CC12722734739079014A06CD2725C37CABAEB2BD3DD209B93CD333A2A77D070`; certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Se actualizó únicamente el runner con `install -r` en `emulator-5580`; `baseDebug` permaneció en la SHA r120 `5FCE830E3933DBC171F09F7A8E1DF392C778265627A1857F5D1FA805564D0460`, app v55, Room38. No se borraron datos de KPKN Dev.
- La DB aislada `qa-import-schedule-*` y su borrador se eliminaron; `run-as ... ls databases` devolvió solo `kpkn.db`. Se detuvieron únicamente `.dbg` y `.dbg.test`, no quedaron PIDs y el AVD siguió encendido/disponible.
- Límite: las pantallas de pegar, planificar, guardar y detalle son UI Compose real; inicio/cierre de entrenamientos se ejerce mediante las APIs de producción del repositorio, no mediante la navegación de Entreno o controles de finalización. Room se cierra/reabre en el mismo proceso instrumentado; el retry valida idempotencia. No se suma r126 al agregado histórico 121/121 sin deduplicación.
- r126 demuestra progreso y retry de una rutina importada, pero Req. 5 y la regresión del importador siguen **Parciales** por calendario/pausas/preparación/catálogo finito, la entrada de navegación real y las regresiones adicionales enumeradas en la matriz.

## Room 37→38 con datos representativos y tablas nuevas — r128 (2026-11-16)

- `:app:assembleBaseDebugAndroidTest` pasó con wrapper/candado: **BUILD SUCCESSFUL** en 1m15s; log `gradle-logs/room37-avd-build-r128-20261116.log`.
- `Room37To38UpgradeDeviceTest.room37RowsSurviveUpgradeAndVersionAndSharedLoadTablesSurviveRoomReopen` pasó **1/1** en `emulator-5580` (`OK (1 test)`, 0.609 s), log `avd-room37-upgrade-r128-20261116.log`. `MigrationTestHelper` crea una DB descartable desde el esquema exportado v37; la prueba inserta programa con portada/sesión, historial de entrenamiento y comida histórica, migra con `PROGRAM_EDIT_HISTORY_37_38`, verifica versión 38, las tres filas originales, ambas tablas nuevas e `integrity_check=ok`.
- Con la DB v38 abierta como `KpknDatabase`, guarda `ProgramVersionEntity` y `SharedExerciseLoadReferenceEntity` por sus DAO, cierra/reabre Room y vuelve a leer ambas entidades junto con el programa anterior. Usa exclusivamente el archivo `qa-room37-*` del paquete `com.example.kpkn.dbg`; no abre ni modifica `kpkn.db`.
- Runner `.dbg.test` SHA-256 `90A7B002D2E9814288698A6E57F7C266D2B040A26A489E8563243EFEC491D2EB`, certificado debug `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Se actualizó solo el runner con `-r`; la app QA v55/Room38 no se reinstaló y la personal no se tocó.
- r127 también pasó, pero dejó un `.lck` de `MigrationTestHelper`; se añadió limpieza explícita del lock, se eliminó el archivo de aquella fixture y r128 repitió el pase. Después de r128 no quedan `qa-room37-*` ni `.lck`; permanecen los archivos `kpkn.db`, `kpkn.db-shm` y `kpkn.db-wal` de la app. Se detuvieron solo `.dbg` y `.dbg.test`; `emulator-5580` sigue encendido/disponible.
- **Límites:** el origen es una DB representativa construida desde el esquema exportado, no una instalación/downgrade de un APK v37. No se recorrieron todavía la migración y los flujos legacy de nutrición desde la UI con esa base. Req. 6 permanece **Parcial**; ver la matriz.

## Consolidación final AVD sobre código `6bf2f5792` (`r135`–`r141`) — 2026-11-16

Todas las suites nuevas de aceptación y el bloque de suites modificadas y regresiones clave se ejecutaron contra `emulator-5580` (`-P 5045 -s emulator-5580`, `com.example.kpkn.dbg` v55 Room 38 SHA-256 `8B70B6387682D5247F45C3FFBBA45F121698D34B35F764029BAFBC7C448D7DDD`, runner `.dbg.test` SHA-256 `77F6020CFE28F4F8523C08C3277CE5E1E8365EC8E2D5170ECD9B468EFD667270` en `r138`–`r141`). No se tocó el Z Flip físico ni la app personal.

### 1. Nuevas suites de aceptación por requisito (`16/16` en 5 suites)

| Suite | Resultado | Ronda | Evidencia |
|---|---:|---|---|
| `HomeProgressAndCoverDeviceTest` | 2/2 | r135 | `avd-HomeProgressAndCoverDeviceTest-r135.log` |
| `FinishStoryAndLoadSemanticsDeviceTest` | 4/4 | r136 | `avd-FinishStoryAndLoadSemanticsDeviceTest-r136.log` |
| `ImporterAndVisualModeAcceptanceDeviceTest` | 3/3 | r137 | `avd-ImporterAndVisualModeAcceptanceDeviceTest-r137.log` |
| `SessionEditorAcceptanceDeviceTest` | 4/4 | r138 | `avd-SessionEditorAcceptanceDeviceTest-r138.log` |
| `RecurrenceNutrition37RecipeDeviceTest` | 3/3 | r139 | `avd-RecurrenceNutrition37RecipeDeviceTest-r139.log` |

- Diagnósticos intermedios no contabilizados: `r135`–`r138` expusieron y corrigieron fallos de medición en `KpknSheet` anidado (`CatalogCustomExerciseEditor.kt` y `ProgramCyclesSheet.kt`), sincronización de `settingsDao` en `ProgramRepository.flushPendingWrites()`, el diálogo y botón de confirmación en recetas, el paso `ImportWizardSheet.RESUME`→`import-resume-continue` y la activación previa de `KpknNumberCell`. Ningún código de producción cambió después de `r136`.

### 2. Suites modificadas y regresiones centrales AVD (`86/86` en 20 suites / 21 ejecuciones)

| Suite | Resultado | Ronda | Evidencia |
|---|---:|---|---|
| `ConceptosClaveUiTest` | 3/3 | r139 | `avd-ConceptosClaveUiTest-r139.log` |
| `FoodLoggerDrawerDraftUiTest` | 3/3 | r139 | `avd-FoodLoggerDrawerDraftUiTest-r139.log` |
| `ExerciseFichaDeviceTest` | 7/7 | r139 | `avd-ExerciseFichaDeviceTest-r139.log` |
| `WorkoutExternalCameraCaptureInstrumentedTest` | 4/4 | r139 | `avd-WorkoutExternalCameraCaptureInstrumentedTest-r139.log` |
| `WorkoutRestFeedbackDeviceTest` | 2/2 | r139 | `avd-WorkoutRestFeedbackDeviceTest-r139.log` |
| `WorkoutV2UiTest` | 7/7 | r139 | `avd-WorkoutV2UiTest-r139.log` |
| `WorkoutCockpitDeviceTest` | 6/6 | r139 | `avd-WorkoutCockpitDeviceTest-r139.log` |
| `ProgramCyclesSheetDeviceTest` | 1/1 | r139 | `avd-ProgramCyclesSheetDeviceTest-r139.log` |
| `LiveProgramPersistenceFlowDeviceTest` | 11/11 | r140 | `avd-LiveProgramPersistenceFlowDeviceTest-r140.log` |
| `ProgramDetailTerminalOneRmDeviceTest` | 1/1 | r140 | `avd-ProgramDetailTerminalOneRmDeviceTest-r140.log` |
| `ProgramRecurringMultiBlockCycleDeviceTest` | 1/1 | r140 | `avd-ProgramRecurringMultiBlockCycleDeviceTest-r140.log` |
| `ImportedRoutineScheduleDeviceTest` | 1/1 | r140 | `avd-ImportedRoutineScheduleDeviceTest-r140.log` |
| `PdfRendererImportInstrumentedTest` | 1/1 | r140 | `avd-PdfRendererImportInstrumentedTest-r140.log` |
| `Room37To38UpgradeDeviceTest` | 1/1 | r140 | `avd-Room37To38UpgradeDeviceTest-r140.log` |
| `WorkoutStoryDeviceTest` | 6/6 | r140 | `avd-WorkoutStoryDeviceTest-r140.log` |
| `WorkoutStoryPreviewDeviceTest` | 2/2 | r140 | `avd-WorkoutStoryPreviewDeviceTest-r140.log` |
| `LiveHeaderSheetsDeviceTest` | 15/15 | r140 | `avd-LiveHeaderSheetsDeviceTest-r140.log` |
| `AuditClosureMainActivityDeviceTest` | 1/1 | r141 | `avd-AuditClosureMainActivityDeviceTest-r141.log` (`font_scale` 1.4 temporal, restaurada a `1.3`) |
| `ProgramDetailRecurringResumeDeviceTest` (`seed`) | 1/1 | r141 | `avd-ProgramDetailRecurringResume-seed-r141.log` |
| `ProgramDetailRecurringResumeDeviceTest` (`resume`) | 1/1 | r141 | `avd-ProgramDetailRecurringResume-resume-r141.log` (tras `am force-stop com.example.kpkn.dbg`) |
| `SessionRevisionFlowDeviceTest` | 11/11 | r141 | `avd-SessionRevisionFlowDeviceTest-r141.log` |

- **Total observado en `r135`–`r141` (`emulator-5580`)**: **102/102 pruebas aprobadas** en **25 suites** (26 ejecuciones separando `seed`/`resume`).
- Incidencias recuperadas sin cambio de código: en `r139`, el demonio `adbd` del AVD dejó de responder al entrar a `LiveProgramPersistenceFlowDeviceTest` y fue necesario reiniciar en frío `Pixel_9_Pro_XL` sin `-wipe-data` y eliminar manualmente la base residual `qa-live-edit-57432350370600.db*` (`[]` bases `qa-` restantes al terminar); en `r140`, `AuditClosureMainActivityDeviceTest` falló por `font_scale=1.3` (pasó 1/1 en `r141` con `1.4` temporal y restauración verificada a `1.3`), `SessionRevisionFlowDeviceTest` excedió el timeout de 150 s (pasó 11/11 en `r141` con 300 s), y `ProgramDetailRecurringResumeDeviceTest` requería filtro por método `#seed`/`#resume`.

### 3. Smoke launch final del `baseRelease` (`6bf2f5792`) en `emulator-5580`

- APK instalado con `adb -P 5045 -s emulator-5580 install -r --no-streaming releases/app-baseRelease-6bf2f5792.apk` (`Success`, certificado SHA-256 `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1` coincidente con `0DFA793A…`).
- Hash remoto verificado en `pm path com.example.kpkn`: `F10DDA01AA833D83C465236DB3F7B825B35B37520ED33D7B25CD32331BBFBD8F` (`SHA_MATCH=True`), `versionCode=55`, `KPKN Pre Release 21`.
- Arranque en frío: `LaunchState: COLD`, `TotalTime: 1724 ms`, `PID: 6966`, `topResumedActivity: com.example.kpkn/.MainActivity`, `CRASH_LINES=0`. Log: `avd-release-smoke-6bf2f5792.log`.
