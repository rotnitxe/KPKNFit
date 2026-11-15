# Recibo de validación AVD

Destino de las pruebas instrumentadas: `emulator-5580` (`Pixel_9_Pro_XL`), paquete de prueba `com.example.kpkn.dbg`; usuario AVD `0`, fuente del sistema `1.3`. No se inspeccionó ni exportó la base de datos del usuario.

## APK de release final

- El APK final de `2ad50d96f` se compiló y firmó, pero **no se instaló en el AVD**. El candidato `com.example.kpkn`, `versionCode 55`, SHA-256 `0DFA793AAB19D43B632B3BBB6C78A2DF104FACFD7737EF73643D737B8B83EDB8` está en `releases/app-baseRelease-2ad50d96f.apk`.
- Al intentar consultar el dispositivo, el servidor aislado ADB `15045` no respondió; se reinició únicamente ese servidor y luego no encontró `emulator-5580`. El servidor ya activo `5037`, consultado con `-s emulator-5580`, tampoco encontró el serial. No se ejecutó `pm`, `shell`, `install`, `clear` ni otra operación de paquete/datos después del fallo de conexión.
- El proceso del AVD `Pixel_9_Pro_XL` permaneció activo. No se reinició el AVD y no se borraron datos. La instalación anterior de `com.example.kpkn` que registró el recibo del commit `41edea0eb` es solo evidencia histórica; su hash no se atribuye al APK final nuevo.
- Las 121 pruebas AVD seleccionadas se ejecutaron en el paquete de pruebas `com.example.kpkn.dbg` con serial `emulator-5580`; no equivalen a instalar/verificar el `baseRelease` final.

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
