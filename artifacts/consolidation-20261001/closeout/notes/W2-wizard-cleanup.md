# W2-wizard-cleanup — restos de C3, D2.5, D2.4, C4, B-03

Estado: DONE. Tests dirigidos en verde tras un reintento (343 + 20 tests; los 3 fallos iniciales eran tests de la validación de inventario retirada, ya sustituidos). Todo compila (main + tests JVM + androidTest). Sin emulador; git solo lectura.

## Ítems cerrados

### (0) Restos de C3 en `SetupReviewStep.kt` + dato previo de grasa
- Textos pasados por `SpanishPlurals`: «N sesión(es) · M ejercicio(s)» (`previewSessionsSummary`), «Ver las N semanas», «N sesión(es) en M semana(s)» (`allSessionsSummary`), pie «Se muestran las primeras 8 de N semanas (M sesiones en total)», «1 serie» sin repeticiones, «N día(s) con objetivo». Funciones internas puras para poder probarlas.
- La fila «Grasa corporal» de la revisión usa `bodyFatReviewValue`: si quien vuelve no actuó y Ajustes tenía un dato creíble muestra «Guardada en Ajustes: X %» (no se declara como respuesta nueva); sin nada sigue «Sin declarar»; omitido «No lo sé»; visual/medido como antes.

### D2.5 — inventario con pesos retirado de la interfaz
- Test de reubicación PRIMERO: `SetupInventoryCursorRelocationTest` (un borrador con el cursor en cada paso INVENTORY_* se reubica en la ruta, conserva respuestas e inventario y reparar es idempotente).
- Los 6 componentes genéricos pasan a `screens/onboarding/SetupTrainingControls.kt` (nuevo): `TrainingNoticeTone`, `formatTrainingNumber`, `TrainingNumberField`, `TrainingNotice`, `TrainingLoading`, `TrainingSummaryRow`.
- Movidos a `closeout/deleted/` (rutas relativas al repo bajo `artifacts/consolidation-20261001/closeout/deleted/`): `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupInventoryControls.kt` y `android-native/app/src/test/java/com/example/kpkn/screens/onboarding/SetupInventoryEditorTest.kt`.
- Quitadas las 5 ramas INVENTORY_* de `SetupTrainingSteps.kt`, los helpers `inventoryReasonAffects` y `hasOwnInventoryData`, y de la revisión `INVENTORY_STEPS` e `inventoryLabel` (la fila «Inventario»). La validación de `INVENTORY_BARBELL…INVENTORY_MACHINES` devuelve `emptyList()`.
- NO se tocaron: enums `SetupStepId.INVENTORY_*`, definiciones de paso, `SetupInventoryGroup`, `EquipmentInventory`, `Settings.equipmentInventory`, `SetupSettingsPatch`.
- Tres tests de `SetupStepAnswersTest` verificaban la validación retirada (`inventoryDeclarationMustSatisfyTrainingOptionsContract`, `noneVsUnsetInventoryIsExplicit`, `openRowEditorBlocksStepUntilClosed`): se sustituyen por `retiredInventoryStepsNeverBlockWhateverTheSavedDraftHolds`.

### D2.4 — `android.*` fuera de `domain/`
- Nueva interfaz pura `domain/storage/KeyValueStore.kt` (`getString`, `putString` = apply, `putStringDurably` = commit con resultado).
- `ProgramSnapshotStore` y `VariantPreferenceStore` conservan paquete, nombre, JSON, claves (`program_<id>`, `aspect_defaults_<id>`, `last_variant_<id>`) y la lógica de `MAX_VERSIONS = 10`; ahora reciben un `KeyValueStore`.
- Adaptador en `data/preferences/PreferenceStores.kt`: `SharedPreferencesKeyValueStore` (perezoso: no toca el disco hasta el primer acceso, conserva el comportamiento que fija `ProgramSnapshotStoreTest`), `PreferenceFiles` (`program_structure_snapshots`, `variant_preferences`), fábricas `programSnapshotStore(context)` / `variantPreferenceStore(context)` y las instancias compartidas `PreferenceStores.programSnapshots/variantPreferences(context)` (sustituyen a `getInstance`). No se promete StrictMode para las variantes.
- Usos actualizados con edición mínima: `ProgramDetailScreen.kt` (1 línea), `CatalogSelectionWizard.kt` (2 líneas), `ProgramDetailViewModelTest.kt` (reemplazo de `ProgramSnapshotStore(...)` por `programSnapshotStore(...)` en 8 puntos + 1 import; `CommitFailingContext` sigue funcionando porque el adaptador pide el archivo al `Context`) y `ProgramSnapshotStoreTest.kt`.
- Pruebas: `PreferenceStoreNamesTest` (Robolectric: nombres de archivo y claves reales, lectura de datos escritos "por el código antiguo", tope de 10 copias) y `DomainHasNoAndroidImportsTest` (guardián: falla si algún `.kt` de `domain/` contiene `import android.`).

### C4 — solo instrumentación del barrido de candidatos
- `updateCandidates` registra con `Log.i` (etiqueta `SetupPlanSweep`, `adb logcat -s SetupPlanSweep`) una línea por barrido: `totalMs`, `catalogMs` (carga del catálogo por separado), `sweepMs`, `catalogoYaCargado`, `primerBarrido`, `publicados`, `evaluados`, `aciertosCache`, `pases` (2 = segundo pase adaptado), `viables`, `adaptado`. Sin datos personales; formato en `candidateSweepLogLine` (probado). No se optimizó nada ni se añadió «Evaluando plan N de M…». Pendiente tuyo: medir en teléfono real con el APK definitivo.

### B-03 — aviso de volumen alto y notas del plan en la revisión
- Lee `previewReport.highVolume` (API de la nota G), no `muscles`. La revisión (bajo «Reparto semanal») muestra «Volumen alto en Glúteos: 17,5 series por semana (recomendado 16). Es un exceso pequeño y aceptable.» solo si el informe trae aviso, y luego las notas del plan. Solo planes propios: PHUL/PHAT no traen informe, no se muestra nada (el aviso para ellos no se hizo, como se pidió). No cambia el programa.
- Notas DEV-r2-01 reescritas en lenguaje llano en `SimpleCyclePersonalizer.kt` (edición mínima, solo las dos líneas de notas): constantes `GLUTE_BRIDGE_ONCE_NOTE` («Hacemos el puente de glúteo 1 vez por semana para no pasar de 16 series de glúteo; los otros días lo cambiamos por flexión de isquiotibiales.») y `ATHLETE_SIX_DAY_BRIDGE_NOTE`. Siguen yendo a `limitations` y a la descripción del programa; además `PersonalizationReport` tiene el campo nuevo `planNotes` (solo esas notas llanas) para que la revisión no enseñe las otras limitaciones, que sí llevan jerga («§13.3», ids de configuración). `NativeProfileRecipeAndFitterTest` (2 aserciones) ahora comprueba las constantes.
- Docs: `docs/WIZARD_PLAN_DEVIATIONS.md` enlaza el Anexo A1 de DEV-r2-02 en las tres frases que dicen «prohibido degradar W2-MRV a SOFT / severidad HARD» (excepción: 16 → 17,5, solo glúteos y planes propios, aprobada por el dueño el 2026-10-02); DEV-r2-02 pasa de «en curso» a **IMPLEMENTADA** citando `G-glutes-tolerance.md` (fila del registro y línea de estado); se actualiza el párrafo de DEV-r2-01 que decía que B-03 «la reescribe».

## Archivos tocados (ruta relativa al repo)
Creados: `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupTrainingControls.kt`, `.../domain/storage/KeyValueStore.kt`, `.../data/preferences/PreferenceStores.kt`; tests `.../test/java/com/example/kpkn/screens/onboarding/{SetupInventoryCursorRelocationTest,WizardReviewAndSweepTest}.kt`, `.../data/preferences/PreferenceStoreNamesTest.kt`, `.../domain/DomainHasNoAndroidImportsTest.kt`.
Modificados: `.../screens/onboarding/{SetupReviewStep,SetupTrainingSteps,SetupWizardModels,SetupWizardViewModel}.kt`, `.../domain/training/{ProgramSnapshotStore,SimpleCyclePersonalizer}.kt`, `.../domain/exercises/VariantPreferenceStore.kt`, `.../screens/programdetail/ProgramDetailScreen.kt`, `.../screens/sessioneditor/CatalogSelectionWizard.kt`; tests `SetupStepAnswersTest.kt`, `ProgramSnapshotStoreTest.kt`, `ProgramDetailViewModelTest.kt`, `NativeProfileRecipeAndFitterTest.kt`; `docs/WIZARD_PLAN_DEVIATIONS.md`.
Movidos (a `closeout/deleted/`): `SetupInventoryControls.kt`, `SetupInventoryEditorTest.kt`.
androidTest: sin cambios (compilado; `SetupInventoryTypingUiTest` ya no existe).

## Gradle
- `W2-compile-1.log`: `compileBaseDebugKotlin compileBaseDebugUnitTestKotlin compileBaseDebugAndroidTestKotlin --continue` → exit 0.
- `W2-tests-1.log`: 26 clases dirigidas, 343 tests, 3 fallos (los tres de inventario en `SetupStepAnswersTest`, tests de la validación retirada; corregidos). `W2-tests-2.log`: reintento de `SetupStepAnswersTest` → exit 0, 20 tests, 0 fallos (evidencia `test-evidence\W2` y `W2b`).
- Clases para verificar el paquete: `WizardReviewAndSweepTest, SetupInventoryCursorRelocationTest, PreferenceStoreNamesTest, DomainHasNoAndroidImportsTest, ProgramSnapshotStoreTest, ProgramDetailViewModelTest, NativeProfileRecipeAndFitterTest, SetupStepAnswersTest, SetupBodyFatStepTest, SetupWizardBodyFatViewModelTest, SetupWizardFullJourneyTest, SetupWizardActivationGateTest, SetupWizardStepApiTest, SetupDraftCompatibilityTest, SetupDraftRepairTableTest, SetupStepDefinitionsTest, SetupStepGraphTest, PersonalizedPlanCatalogTest, VolumeSoftBandTest, SessionCompositionPolicyTest` (+ las de W1).

## Riesgos abiertos
- `ProgramSnapshotStore.getInstance` / `VariantPreferenceStore.getInstance` ya no existen: cualquier agente que los use debe pasar a `PreferenceStores.*` (no queda ningún uso en el árbol actual).
- Los textos nuevos de la revisión (aviso y notas) se probaron como funciones de texto, no visualmente.
- `docs/REPO_STRUCTURE.md` no se actualizó con los paquetes nuevos (`domain/text`, `domain/storage`, `data/preferences`).
