# wave6 — tres fallos de la segunda vuelta de QA en dispositivo (base, 2026-10-02)

Fecha: 2026-10-02. Solo lectura/edición: NO se ejecutó Gradle, adb ni emuladores. La compilación fue mental (Grep de cada símbolo).
Evidencia de origen: `device-evidence\instrumentation\base\*all-android-tests*` y `*isolated-*`; resumen en `tools\avd\README.md` §14.

## 1. SetupInventoryTypingUiTest (3 tests) — ELIMINADA

Decisión: la clase está obsoleta y se borra (`android-native/app/src/androidTest/java/com/example/kpkn/screens/onboarding/SetupInventoryTypingUiTest.kt`,
tracked en `857a036e8`; copias de respaldo en `snapshot-pre-wave1\` y `backup-main-pre-integration\` de este directorio).

Por qué falla: `seedInventoryStep` hace `stepProgress.at(INVENTORY_BARBELL, ctx)` y espera `currentStep == INVENTORY_BARBELL`, pero
- `SetupWizardModels.kt:403` `SetupWizardDraft.inventoryGroups() = emptySet()` → `stepContext().inventoryGroups` siempre vacío (l.386);
- `SetupStepGraph.kt:346-348` solo inserta pasos `INVENTORY_*` si `context.inventoryGroups` no está vacío;
- `SetupStepProgress.at` (`SetupStepGraph.kt:902-904`) devuelve `this` si el paso no está en la ruta → el cursor nunca llega;
- `SetupWizardViewModel.editStep` (l.280) rechaza pasos fuera de ruta y `SetupDraftCompatibility.repairStepProgress` (l.218-236) recoloca
  cualquier cursor fuera de ruta en el primer paso pendiente, así que ni un borrador antiguo puede dejar el cursor en `INVENTORY_*`.

Alcanzabilidad de los composables (Grep sobre `app/src/main`): `InventoryBarbellStep/PlatesStep/DumbbellsStep/KettlebellsStep/MachinesStep`
(`SetupInventoryControls.kt:543,699,808,923,1017`) y `InventoryEditField` (testTag `inventory-field-<clave>`, l.127-152) solo los usa el
`when(step)` de `SetupTrainingSteps.kt:102-106`, inalcanzable por lo anterior. No hay ruta de Ajustes ni de `navigation/` que use el editor
de inventario con pesos (Grep `Inventory` en `navigation/` = 0; `EquipmentInventory` en `screens/` solo en `WorkoutViewModel`, `SetupWizardViewModel` y
`SetupInventoryControls`). Es decir: el editor existe como código muerto, no es alcanzable ni en el wizard ni en otra ruta de la app.

Referencias normativas (`C:\Users\valen\.opencode\plan\KPKNFit\2026-09-28-planes-adaptativos-wizard.md`): §13.1 «sin introducir inventario de pesos
en el wizard»; §13.2 «sin pesos/cantidades … no resucitar `INVENTORY_*` ocultos»; §15.1 «Inventario kg/cantidades no aparece».
Por eso se descartó también reapuntar la prueba a los composables vía `setContent` (sería blindar código muerto que el plan manda no resucitar).

Qué cubre hoy el contrato vigente (no se añadió nada nuevo, ya existe):
- JVM: `SetupStepAnswersTest.homeEnvironmentAsksAllInventoryGroups` (l.341-349: `inventoryGroups()` vacío, `asksAvailability`),
  `SetupWizardFullJourneyTest` l.742-760 (AC-T005-01: sin inventario persistido; categorías → `equipmentAvailability`; apparatus/supports vacíos = UNKNOWN),
  `SetupStepGraphTest.inventoryGroupsInsertTheirStepsAfterTheEnvironmentInGroupOrder` (el grafo puro conserva la inserción si el contexto lo pide).
- androidTest: `SetupWizardFullJourneyUiTest` l.474-540 (EQUIPMENT → AVAILABILITY → GOAL en la UI real).

Efecto lateral que queda PENDIENTE de aplicar por quien tenga permiso (el clasificador de permisos denegó reescribir estos archivos): quitar la línea
`com.example.kpkn.screens.onboarding.SetupInventoryTypingUiTest` de
- `artifacts\consolidation-20261001\tools\avd\suites\all-android-tests.txt` (l.13)
- `artifacts\consolidation-20261001\tools\avd\suites\all-android-tests-base-r2.txt` (l.9)
- `artifacts\consolidation-20261001\tools\avd\suites\wizard-ui.txt` (l.5)
Sin eso, `tools\avd\tests\test_suite_plan.py::test_every_suite_entry_is_a_real_integrated_class` y `test_all_android_tests_plus_the_two_special_classes_cover_the_tree`
fallan, y una corrida de suite pediría una clase inexistente. (Los archivos son CRLF; conservar.) `tools\avd\README.md` §14 menciona la clase solo como resultado histórico.

## 2. TelemetryIntegrationTest.app_should_initialize_telemetry_on_startup

Archivo: `android-native/app/src/androidTest/java/com/example/kpkn/telemetry/TelemetryIntegrationTest.kt` (tracked, CRLF conservado).
Se sustituyó el placeholder (`createComposeRule()` sin `setContent`) por una comprobación real SIN Compose sobre la Application del proceso instrumentado:
1. `ApplicationProvider.getApplicationContext<Application>() is KpknApplication`.
2. `NutritionTelemetry.isInitialized()` y `isEnabled()` (`KpknApplication.onCreate` l.46 llama `NutritionTelemetry.initialize(this)` en el proceso principal).
3. Sonda de extremo a extremo: `NutritionTelemetry.event("qa_startup_probe_<nanoTime>")` → `KpknDiagnosticLogger.awaitIdle()` → el nombre aparece en uno de los
   5 JSONL más recientes de `filesForArea(app, "nutrition")`.
Sin `MainActivity` a propósito (evita onboarding/navegación y no depende de la UI). Se descartó buscar `session_start` en disco: los JSONL conservan
sesiones previas y no hay forma de ligarlo al `sessionId` privado de esta ejecución. Se eliminaron los imports de Compose y la `@Rule` (ya sin uso);
`telemetry_helper_should_be_accessible_from_app_context` queda intacto.

## 3. WorkoutMediaCaptureJournal.readAll() tolerante a la desaparición legítima del marcador

Producción: `android-native/app/src/main/java/com/example/kpkn/data/media/WorkoutMediaCaptureJournal.kt` (CRLF conservado)
- Constructor: `WorkoutMediaCaptureJournal(filesDir: File, private val readEntryText: (File) -> String = { it.readText(Charsets.UTF_8) })`.
  Único cambio de API, con valor por defecto: los 30+ llamadores (`WorkoutMediaCaptureJournal(dir)`) no cambian.
- `readAll()`: `files.mapNotNull`. Lectura (`readEntryText`) y decodificación JSON van en `try` separados:
  - `FileNotFoundException` **y** `!file.exists()` → el marcador se omite (limpieza del ingest entre listar y leer).
  - `FileNotFoundException` con el archivo todavía presente (p. ej. permiso denegado) → `IOException("No se pudo leer una captura pendiente.", causa)` (mensaje actual).
  - Cualquier otra excepción de lectura o JSON ilegible/corrupto → mismo `IOException` y mismo mensaje.
  No se usa `NoSuchFileException` (java.nio, API 26) porque `base` tiene minSdk 24 y `File.readText` solo lanza `FileNotFoundException`.
- Mensaje extraído a `private const val PENDING_READ_FAILURE` (mismo texto). `read(id)` hereda el comportamiento (devuelve null si el marcador desaparece).
- No se tocó `validateExisting` (bajo `JOURNAL_WRITE_LOCK`; ahí el archivo se espera presente).

Pruebas JVM añadidas (`android-native/app/src/test/java/com/example/kpkn/data/media/WorkoutMediaCaptureJournalTest.kt`, CRLF conservado, mismo estilo Robolectric):
- `WorkoutMediaCaptureJournalTest#read_all_skips_a_marker_removed_between_listing_and_reading` — el reader inyectado borra el primer marcador listado y lo lee (FNFE real): devuelve solo el superviviente, igual que una lectura posterior.
- `WorkoutMediaCaptureJournalTest#read_returns_null_when_the_marker_is_removed_between_listing_and_reading` — `read(id)` → null y el journal queda vacío.
- `WorkoutMediaCaptureJournalTest#read_all_still_fails_on_a_corrupt_marker_that_exists` — JSON corrupto en un `.ready.json` presente: `IOException` con el mensaje actual, causa distinta de FNFE, el marcador sigue en disco.
- `WorkoutMediaCaptureJournalTest#read_all_still_fails_when_an_existing_marker_cannot_be_opened` — reader que lanza `FileNotFoundException("… (Permission denied)")` con el archivo presente: sigue fallando con el mensaje actual y causa FNFE.
Helpers privados: `captureEntry(...)`, `readyMarkers()`.

Endurecimiento adicional de la prueba intermitente (mismo hallazgo): `android-native/app/src/androidTest/java/com/example/kpkn/data/repository/WorkoutMediaUriDurabilityInstrumentedTest.kt`
(archivo no versionado, LF). La aserción de la l.145 (`readAll().isEmpty()`) corría justo tras `awaitSaved` (la fila de Room es visible ANTES de que
`cleanupCommittedCapture` cierre el marcador), así que además del FileNotFoundException había una carrera de simple lectura-antes-del-borrado (marcador
aún presente → «no vacío»). Ahora espera, acotado a 10 s con `withTimeout`+`delay(20)` (patrón ya usado en el mismo archivo), a que el journal quede vacío y
después mantiene la aserción original. Las aserciones de las l.~181/218 siguen a llamadas síncronas (`retryPendingCapturesForSession`) y no se tocaron.

## Comandos de verificación (NO ejecutados)

JVM (desde `android-native/`):
```
./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.data.media.WorkoutMediaCaptureJournalTest" --tests "com.example.kpkn.data.media.WorkoutMediaRepositoryRecoveryTest"
./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.screens.onboarding.SetupStepAnswersTest" --tests "com.example.kpkn.domain.onboarding.SetupStepGraphTest"
```
androidTest (APKs base recompilados; emulador de auditoría, no el del wizard):
```
python -X utf8 tools\avd\run_instrumentation.py --flavor base --classes com.example.kpkn.telemetry.TelemetryIntegrationTest,com.example.kpkn.data.repository.WorkoutMediaUriDurabilityInstrumentedTest --label wave6
python -X utf8 tools\avd\run_instrumentation.py --flavor base --classes-file suites\all-android-tests-base-r2.txt --timeout 3600 --label all-android-tests-r3   # tras quitar la línea de la clase borrada
python -X utf8 tools\avd\tests\test_suite_plan.py   # tras actualizar las suites (comprueba que cada entrada es una clase real y que all-android-tests cubre el árbol)
```
Equivalente Gradle: `./gradlew connectedBaseDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.kpkn.telemetry.TelemetryIntegrationTest`.

## No resuelto / riesgos

- Suites de `tools\avd\suites\` aún listan la clase eliminada (ver §1): acción pendiente, bloqueada por permisos.
- El código muerto de inventario con pesos sigue en `main` (`SetupInventoryControls.kt` ~1000 líneas, dispatch `SetupTrainingSteps.kt:102-106`, `SetupReviewStep.kt` `INVENTORY_STEPS`,
  definiciones en `SetupStepDefinitions.kt`). No se borró: está fuera del encargo y sus pruebas JVM puras (`SetupInventoryEditorTest`, `SetupStepAnswersTest`, `SetupStepDefinitionsTest`)
  lo cubren. Candidato a limpieza posterior si se decide cerrar definitivamente el camino (§13.2).
- Nada se ejecutó: la prueba nueva de telemetría depende de que el proceso instrumentado use `KpknApplication` (así es: manifiesto `.KpknApplication`, runner `AndroidJUnitRunner`)
  y de que `awaitIdle()` (5 s) drene la cola; el sibling ya usa el mismo `awaitIdle()`.
- Edición de archivos: la herramienta de edición convirtió a LF los archivos no versionados `WorkoutMediaCaptureJournal.kt` y `WorkoutMediaCaptureJournalTest.kt`
  (eran CRLF); se restauraron a CRLF byte a byte (303 y 215 líneas, sin LF sueltos). `WorkoutMediaUriDurabilityInstrumentedTest.kt` era LF y sigue LF.
