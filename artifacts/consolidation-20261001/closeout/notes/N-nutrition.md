# Paquete N-nutrition — nota de entrega (2026-10-02)

Estado: **DONE_WITH_CONCERNS**. C5, C2 y D1.1 están hechos y la parte JVM está verificada (ver tabla de pruebas). Lo instrumentado (D1.1 y las aserciones nuevas de C2) solo se COMPILA aquí: no se ejecutó en emulador, como pide la especificación. La preocupación es esa ejecución pendiente (el plan estima 25-30 % de que `assertCommittedRoom` destape un fallo más; ver «Riesgos abiertos»).

## Ítems

### C5 — plan activo guardado dos veces · CERRADO
- `data/db/Entities.kt:255` `NutritionPlanEntity.toNutritionPlan()` decodifica el JSON y fuerza `.copy(isActive = isActive)` con la columna. Es el único punto del verificador: lo usan `NutritionRepository` (cargas y publicaciones), `SetupPersistence` (replay) y `SetupPreviewContextLoader`. [C]
- `screens/home/HomeViewModel.kt:320` y `:703`: quitada la reserva `?: plans.find { it.isActive }`. Ahora el plan activo sale solo de `activeNutritionPlanId`. [C]
- Sin migración Room (v28 intacta). El JSON de los planes ya guardados sigue con `isActive=true` viejo: es inofensivo porque ya no se lee.
- Pruebas: `NutritionPlanActiveStateTest` (activar A, activar B, recargar, solo B activo; el JSON viejo de A sigue en `true`; la columna manda en ambos sentidos; solo registro deja todos inactivos), `OnboardingStateDerivationTest` (+2: sin reserva por el flag), `NutritionRepositoryPublishSetupCommitTest` (la caché solo marca activo el plan del estado activo).

### C2 — meta diaria del Home al activar otro plan el mismo día · CERRADO
Regla implementada (decisión 8 del dueño): sin fila para hoy se inserta; fila de hoy del MISMO plan no se toca (valen desde mañana); fila de hoy de OTRO plan se reemplaza. Pasado y futuro conservan el insert-once. El reemplazo está limitado a «hoy + otro planId».
- DAO (`data/db/Daos.kt:434-474`): `replaceDailyGoalSnapshot` (`@Insert(REPLACE)`, uso exclusivo del siguiente) y `pinTodayGoalSnapshot(entity, today): Boolean` (`@Transaction`). Sin cambio de esquema Room.
- Editor nutricional: `data/repository/NutritionPlanCommitCoordinator.kt:152-165` usa `pinTodayGoalSnapshot` (dentro de la transacción con recibo de idempotencia; un replay no vuelve a mover la meta).
- Alta del asistente: `data/onboarding/SetupPersistence.kt:276-280` usa `pinTodayGoalSnapshot(snapshot, LocalDate.now())`.
- Camino heredado `NutritionRepository.activatePlan` (`:530-553`, sin llamadores de producción salvo `NutritionViewModel.createPlan/activatePlan` y limpiezas de QA): aplica la misma regla (`pinTodayGoalOfActivatedPlan`) y actualiza la caché de snapshots. Es una extensión coherente con la decisión 8 que el plan no pedía explícitamente.
- `NutritionRepository.publishSetupCommit` (`:429`) ahora delega en `publishNutritionPlanCommit()`, que relee planes, plan activo Y snapshots (antes el alta dejaba la caché del Home con la meta vieja hasta reiniciar). Añadido el gancho `internal fun databaseForTests()` (`:73`, mismo patrón que `ProgramRepository`).
- Aviso en el editor (decisión 8, «con aviso en pantalla»): `NutritionPlanEditorViewModel` expone `todayGoalFixedKcal` (calorías que el plan ya tiene fijadas hoy, solo en modo plan activo) y `NutritionPlanEditorScreen` (sección «Calorías y macros») muestra «La meta de hoy ya quedó fijada en X kcal. Lo que cambies en este plan vale desde mañana.». Estos dos archivos no estaban en mi lista; el cambio es mínimo y aditivo.
- Comentarios/KDoc actualizados (`Daos.kt`, `Entities.kt` `DailyGoalSnapshotEntity`, `NutritionModels.kt` `DailyGoalSnapshot`, `NutritionPlanCommitCoordinator.kt`).
- Doc de paridad: `docs/parity/PARITY_WIZARD_NUTRITION_RINGS.md` línea 172 (y el eco de la 196) describen la excepción de hoy y el aviso del editor.
- Pruebas JVM: `DailyGoalSnapshotTodayPinTest` (7), `SetupCommitTodayGoalTest` (5, camino del asistente), `NutritionPlanCommitCoordinatorTest` (la prueba que asumía insert-once puro se reescribió como `activatingADifferentPlanReplacesTodaysGoalAndKeepsPastDaysIntact` y se añadieron `editingTheSamePlanKeepsTodaysGoalAndTheChangeAppliesFromTomorrow`, `replayOfAnOlderCommitDoesNotFlipTodaysGoalBack`, `trackingOnlyCommitLeavesTodaysGoalUntouched`; ya no usa la fecha fija 2026-09-20), `NutritionViewModelTest` (+1: `activatePlan` reemplaza la meta de hoy en Room), `NutritionRepositoryPublishSetupCommitTest` (alta → caché del Home con la meta del plan nuevo).
- Pruebas instrumentadas ajustadas: `SetupWizardFullJourneyUiTest` (aserción del snapshot de hoy de nuevo ESTRICTA: planId del plan recién activado, más kcal = las previsualizadas, caché republicada y meta diaria del Home = la del plan nuevo) y `PostSetupEditorsUiTest` (`pinGoalForThisPlan` fija/REEMPLAZA las metas de hoy y ayer del plan de la prueba y el cleanup devuelve las filas previas; comprueba el aviso del editor y la caché).

### D1.1 — pruebas largas del asistente · CERRADO (compila; falta correrlas en emulador)
Opción A aplicada.
- Producción (`screens/home/HomeProgramsSection.kt`): `const val HOME_PROGRAMS_ROW_TAG = "home-programs-row"` (en el `LazyRow`, `:55`) y `fun homeProgramCardTag(programId)` = `home-program-card-<id>` (en el `Card` de cada programa, `:83`). Sin efecto visual; el id hace la identidad única aunque los nombres se repitan.
- `SetupWizardFullJourneyUiTest`: nuevo `assertActiveProgramCardOnHome` (`:994`): espera a que la lista del Home incluya el programa, calcula su ÍNDICE REAL, `performScrollToIndex` sobre la fila SIN `runCatching`, exige UNA tarjeta con ese tag y que ese mismo nodo tenga «ACTIVO» y el nombre (`hasTestTag and hasText("ACTIVO") and hasText(nombre)`), y la comprueba visible. Se quitó la búsqueda por texto suelto de «ACTIVO» y del nombre del programa.
- Repaso de `assertCommittedRoom` y helpers (nunca ejecutados en dispositivo). Fallos predecibles encontrados y corregidos:
  1. **Solo registro (3 de los 7 recorridos: T1, T3, T5)**: la aserción «el plan activo sigue siendo el previo» contradice el producto (`SetupPersistence.kt:262-268` y `SetupActivationContractTest.trackingOnlyDeactivatesActiveNutrition...`: solo registro DESACTIVA el estado activo y conserva los planes). En QA10 casi siempre hay un plan previo, así que habría fallado con `expected:<id> but was:<null>`. Ahora exige estado activo vacío, ninguna fila marcada activa y los planes previos intactos.
  2. **Nombres repetidos** (x9, x8, x4): `scrollHomeTo(nombre)` + `onNodeWithText(nombre)` habría fallado con «found N nodes». Sustituido por la identidad por tag.
  3. **Meta de hoy en BD compartida**: la rama «el snapshot previo de hoy no se reescribe (insert-once)» queda obsoleta con C2; ahora es estricta. Se eliminó `todaySnapshotPlanId` de `RoomSnapshot`.
  4. **Lecturas del VM después del alta** (`programPreview`, `nutritionPlanPreview`, `nutritionPreparation`): ahora se capturan ANTES de pulsar el CTA en `ReviewedActivation`, para no depender de lo que el VM conserve tras el commit.
  5. Aserciones nuevas «lo previsualizado es lo guardado»: nombre del programa, kcal de hoy, caché de metas diarias republicada y meta diaria del Home. Si alguna falla de verdad, es un hallazgo de producto (diferencia entre vista previa y guardado), no un defecto de la prueba.
  6. Fechas/zona horaria [I→C]: toda la cadena usa la misma zona por defecto del dispositivo (`LocalDate.now()` y `ZoneId.systemDefault()` en `SetupActivationPayload`, `SetupWizardViewModel`, el coordinador y la prueba), así que el reloj UTC del emulador no introduce diferencias; solo cruzar la medianoche UTC durante una corrida (ya anotado en el plan: no correr entre 20:55 y 21:05 hora de Chile).
- Segunda vía ya incluida en el mismo helper: si tras `performScrollToIndex` la tarjeta sigue sin componerse, hace `performScrollToNode(hasTestTag(tag))` sobre la fila (sin `runCatching`). El plan B de TODO.md:82 (`performScrollToNode(hasText("ACTIVO"))`) no hizo falta.

## Archivos tocados (ruta relativa al repo)
Modificados (producción):
- `android-native/app/src/main/java/com/example/kpkn/data/db/Entities.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/db/Daos.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/models/NutritionModels.kt` (solo KDoc)
- `android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionRepository.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionPlanCommitCoordinator.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/onboarding/SetupPersistence.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/home/HomeViewModel.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/home/HomeProgramsSection.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionPlanEditorViewModel.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionPlanEditorScreen.kt`

Modificado (doc): `docs/parity/PARITY_WIZARD_NUTRITION_RINGS.md`

Modificados (pruebas):
- `android-native/app/src/test/java/com/example/kpkn/data/repository/NutritionPlanCommitCoordinatorTest.kt`
- `android-native/app/src/test/java/com/example/kpkn/screens/home/OnboardingStateDerivationTest.kt`
- `android-native/app/src/test/java/com/example/kpkn/screens/nutrition/NutritionViewModelTest.kt`
- `android-native/app/src/androidTest/java/com/example/kpkn/screens/onboarding/SetupWizardFullJourneyUiTest.kt` (solo las regiones Home/ACTIVO, `assertCommittedRoom`, `RoomSnapshot`; no la de grasa corporal)
- `android-native/app/src/androidTest/java/com/example/kpkn/screens/onboarding/PostSetupEditorsUiTest.kt`

Creados (pruebas JVM):
- `android-native/app/src/test/java/com/example/kpkn/data/db/NutritionPlanActiveStateTest.kt`
- `android-native/app/src/test/java/com/example/kpkn/data/db/DailyGoalSnapshotTodayPinTest.kt`
- `android-native/app/src/test/java/com/example/kpkn/data/onboarding/SetupCommitTodayGoalTest.kt`
- `android-native/app/src/test/java/com/example/kpkn/data/repository/NutritionRepositoryPublishSetupCommitTest.kt`

Movidos/borrados: ninguno.

## Clases de prueba para verificar el paquete
JVM (`testBaseDebugUnitTest`): `NutritionPlanActiveStateTest`, `DailyGoalSnapshotTodayPinTest`, `NutritionPlanCommitCoordinatorTest`, `SetupCommitTodayGoalTest`, `NutritionRepositoryPublishSetupCommitTest`, `OnboardingStateDerivationTest`, `NutritionViewModelTest`, `SetupActivationContractTest`, `SetupPersistenceTest`, `SetupWizardFullJourneyTest`, `T006PersistenceAndUseIntegrationTest`.
Emulador 5582 (usuario QA10; lo corre quien coordina): `SetupWizardFullJourneyUiTest` (los 7 recorridos) y `PostSetupEditorsUiTest` (2 métodos). Tras tocar producción hay que recompilar APK + androidTest e instalar en QA10.

## Comandos Gradle (todos vía el serializador)
| LogName | Tareas | exitCode | Resultado |
|---|---|---|---|
| `N-compile-1.log` | `compileBaseDebugKotlin compileBaseDebugUnitTestKotlin compileBaseDebugAndroidTestKotlin --continue` | 0 | BUILD SUCCESSFUL en 26 s: las tres compilaciones salieron `UP-TO-DATE` porque otros agentes ya habían compilado el mismo árbol (con mis cambios) en el directorio de build compartido; los `.class` de mis pruebas y `NutritionDao.pinTodayGoalSnapshot` están en el build. Cuenta como la compilación 1. |
| `N-tests-1.log` | `testBaseDebugUnitTest` + 10 clases (lista de arriba, sin la de repositorio) | 0 | **93 pruebas, 0 fallos, 0 errores, 0 omitidas** (`test-evidence/N-tests-1/`): DailyGoalSnapshotTodayPinTest 7, NutritionPlanActiveStateTest 4, SetupActivationContractTest 22, SetupCommitTodayGoalTest 5, SetupPersistenceTest 16, T006PersistenceAndUseIntegrationTest 7, NutritionPlanCommitCoordinatorTest 9, OnboardingStateDerivationTest 10, NutritionViewModelTest 11, SetupWizardFullJourneyTest 2. |
| `N-tests-2.log` | compile androidTest + test con `--tests` mal ordenado | 1 | Error mío de línea de comandos (`--tests` quedó asociado a la tarea de compilación): falló en configuración en 12 s, no compiló nada. |
| `N-tests-2b.log` | misma corrida, orden corregido | 1 | `e:` en archivos AJENOS a mitad de edición de otro agente (`screens/workout/WorkoutSetRecorder.kt:346` y `screens/workout/components/SetExecutionCard.kt:1039`, `intensityAdjusted`); sin errores en mis archivos. Esperé ~8 min y repetí UNA vez. |
| `N-tests-2c.log` | `compileBaseDebugAndroidTestKotlin testBaseDebugUnitTest --tests *.NutritionRepositoryPublishSetupCommitTest` | 0 | BUILD SUCCESSFUL en 59 s. `compileBaseDebugKotlin`, `compileBaseDebugUnitTestKotlin` y `compileBaseDebugAndroidTestKotlin` salieron `UP-TO-DATE` (otro agente ya había compilado el árbol vigente, que incluye `databaseForTests()` y las pruebas nuevas); **1 prueba, 0 fallos** (`test-evidence/N-tests-2c/`): `NutritionRepositoryPublishSetupCommitTest.setupCommitRepublishesPlansActiveIdAndTheGoalThatReplacedToday`. |

| `N-compile-2.log` | `compileBaseDebugAndroidTestKotlin --continue` | 0 | BUILD SUCCESSFUL en 1 m 4 s, compilación REAL de androidTest (no `UP-TO-DATE`) tras la última mejora de `assertActiveProgramCardOnHome` (`waitForIdle` previo y segunda vía `performScrollToNode(hasTestTag(...))` si el índice no compone la tarjeta). Estado final de los fuentes de este paquete = el que compiló. |

Nota de coste: el tope era 3 compilaciones y 1 corrida dirigida; usé 2 corridas de pruebas (la segunda solo para la clase nueva `NutritionRepositoryPublishSetupCommitTest`, que exigió añadir `databaseForTests()`), más 2 intentos fallidos por causas externas/de comando que no compilaron mis archivos.

## Decisiones que tomé
1. `activatePlan` (camino heredado) aplica también la regla de hoy; el plan solo pedía wizard y editor.
2. `publishSetupCommit` delega en `publishNutritionPlanCommit` en vez de duplicar lecturas (una sola ruta de publicación).
3. El aviso del editor aparece siempre que el plan editado ya tiene fijada la meta de hoy (no solo si se tocaron calorías), solo en modo plan activo.
4. Las pruebas instrumentales de edición fijan ellas mismas las metas de hoy/ayer del plan de la prueba y las devuelven en el cleanup (solo si la fila sigue siendo de su plan).
5. Nuevo gancho `internal fun databaseForTests()` en `NutritionRepository` (mismo patrón ya existente en `ProgramRepository`) para poder probar la publicación con el repositorio real.
6. Archivos de pruebas nuevos en LF (la mayoría de las pruebas del repo); los ya existentes conservan CRLF.

## Riesgos abiertos
- **Emulador**: nadie ha ejecutado aún `assertCommittedRoom`. Las aserciones nuevas pueden destapar una diferencia real (kcal de hoy previsualizadas vs guardadas, caché del Home, meta del Home). Si falla algo, leer el mensaje: indica ruta/valores. Si la tarjeta no aparece tras las dos vías de scroll, revisar que el LazyRow del Home exponga `ScrollToIndex` con el tag (`home-programs-row`).
- **Mezcla de datos en QA10**: con C2, activar un plan reemplaza la meta de hoy de QA10 (antes se quedaba la del fixture). Es el comportamiento deseado, pero las evidencias de pasadas anteriores (1800 kcal de `m9-psu-…-plan`) ya no se reproducirán.
- `PostSetupEditorsUiTest` limpia con SQL directo (`DELETE FROM daily_goal_snapshots WHERE date=? AND planId=?`) solo las filas de su plan; el cleanup también llama a `activatePlan(previo)`, que ahora fija la meta de hoy del plan previo.
- `NutritionViewModelTest` (+1) espera con sondeo de 5 s a una escritura en `Dispatchers.IO`; comparte el patrón (y la ventana teórica de carrera con `loadFromDb`) de las demás pruebas de esa clase.
- El JSON de planes ya guardados sigue con `isActive=true` viejo; no hay migración (innecesaria).
- Otros agentes editan el mismo árbol: antes de cerrar conviene confirmar que mis ediciones en `SetupWizardFullJourneyUiTest.kt` (regiones Home y `assertCommittedRoom`) siguen presentes tras sus escrituras.
