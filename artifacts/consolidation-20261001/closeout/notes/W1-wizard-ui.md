# W1-wizard-ui — pluralización en español (C3, pasada 1) y paso de grasa corporal (C1)

Estado: DONE. Compila main + tests JVM + androidTest; 197 tests dirigidos en verde. No se usó emulador.

## Ítems cerrados

### C3 pasada 1 — singular en el copy del wizard y del catálogo
- Ayudante puro de dominio `domain/text/SpanishPlurals.kt` (sin `android.*`): `isSingular`, `choose` (palabra o frase completa), `withNoun`, atajos `days/weeks/sessions/exercises/sets/reps/blocks/plans/steps`. Solo el 1 va en singular (0 y demás, plural).
- Sitios corregidos (18):
  - `SetupWizardViewModel.kt`: motivo «Encaja con tu semana de 1 día» / «Encaja con tus N días por semana» (función `weekFitReason`, en SetupWizardModels), «N día(s) por semana», «1 plan publicado, pero no es ejecutable…» / «N planes publicados; ninguno…», «La receta fija produce N día(s), no M».
  - `AuthoredPlanMaterializer.kt` (domain/onboarding): mismo mensaje «La receta fija produce…».
  - `SetupWizardModels.kt`: «Selecciona N día(s) en tu semana» (2 sitios).
  - `SetupTrainingSteps.kt`: contador de WEEKDAYS («Elige 1 día · 0 de 1 elegido»), aviso del split («Define el foco de tu día»), conteos de candidatos («1 plan evaluado · 1 viable · 0 no viables»), línea «1 serie × 1 rep» (ruta desde cero), `exerciseSummary` («1 serie»), «Personalizado (1 paso)». Se hicieron `internal` funciones puras de texto para poder probarlas.
  - `PersonalizedPlanCatalog.kt`: subtítulo nativo («Semana cíclica · 1 día»; rangos «2–3 días» intactos), plantilla («1 semana · una fase», «N bloques»), protocolo («N día(s)», «ciclo de N semana(s)», descripción).
- NO hecho (fuera de alcance): pasada 2. Pendiente para el paquete que toque `SetupReviewStep.kt` (no lo toqué): líneas 230 («N sesiones · M ejercicios»), 246/252/263 («N semanas»), 264, 308 («N series») y 397 («N días con objetivo»). `SetupStepDefinitions.kt:155` ya manejaba el 1 por su cuenta (no se tocó). `WizChatCopyCatalog` (chat legacy) ya usa «un día».

### C1 — grasa corporal: nada se avanza sin acción explícita
- `SetupWizardModels.kt`: nuevo `SetupBodyFatState` (PENDING / ON_FILE / VISUAL / MEASURED / SKIPPED) y `SetupWizardDraft.bodyFatState()` derivado del borrador (nada nuevo persistido salvo el campo de abajo). La validación de BODY_FAT devuelve bloqueo `absent` con `BODY_FAT_PENDING_MESSAGE` solo en PENDING; las ramas previas (texto inválido, rango 3–60, fuente sin porcentaje) no cambian.
- Corrección (b): `SetupWizardViewModel.skipStep` aplica `withStepChoice(UNKNOWN)` solo para BODY_FAT (limpia porcentaje, fecha y texto) y luego registra la respuesta; deja Continuar habilitado y NO avanza.
- Correcciones (a) y (c): el texto del selector compartido `WizardPhysiqueSelector` NO se tocó (ni ese archivo). En `SetupBasicSteps.kt`, `SetupVisualBodyFat` recibe el parámetro nuevo `bodyFatState` y muestra bajo la figura una línea de estado («Sin dato todavía», «Estimación visual guardada: ≈ N %», «Medición guardada: N %», «Dato guardado antes: ≈ N %», «Omitido») con ayuda breve, y una línea tocable «Usar ≈ N %» (testTags `setup-bodyfat-status`, `setup-bodyfat-use`) mientras no haya estimación/medición propia. «Omitir» además cierra el campo de medición exacta.
- Usuarios que vuelven — decisión: si Ajustes ya tiene grasa (`userVitals.bodyFatPercentage` creíble, 3–60 %), no se bloquea. `newDraft` siembra el campo nuevo `importedBodyFatPercent` (análogo a `importedWeightKg`). No se declara nada: `bodyFatPercent`/`bodyFatSource` siguen nulos, no se crea observación (la fila corporal exige fuente) y Ajustes conserva su valor; la respuesta queda como SUGERIDA. Puede omitir o reemplazar. Borradores en curso guardados antes de este cambio no traen el campo: se les pide una acción al volver al paso (y la puerta de activación la exige). Limitación conocida: la revisión final (`SetupReviewStep.kt`, no mío) mostrará «Sin declarar» para el dato previo de Ajustes no tocado; conviene que el paquete de revisión lo muestre.

## Archivos tocados (ruta relativa al repo)
Creados: `android-native/app/src/main/java/com/example/kpkn/domain/text/SpanishPlurals.kt`; tests `.../test/java/com/example/kpkn/domain/text/SpanishPluralsTest.kt`, `.../screens/onboarding/WizardPluralCopyTest.kt`, `SetupWizardOneDayCopyTest.kt`, `SetupBodyFatStepTest.kt`, `SetupWizardBodyFatViewModelTest.kt`.
Modificados: `.../screens/onboarding/SetupWizardViewModel.kt`, `SetupWizardModels.kt`, `SetupTrainingSteps.kt`, `SetupBasicSteps.kt`; `.../data/programs/PersonalizedPlanCatalog.kt`; `.../domain/onboarding/AuthoredPlanMaterializer.kt`; androidTest `.../screens/onboarding/SetupWizardFullJourneyUiTest.kt` (solo región BODY_FAT: comprueba «Sin dato todavía» y CTA bloqueado si no hay dato previo, y «Omitido»/fuente UNKNOWN tras omitir).
No tocados: `WizardGateComponentsUiTest.kt` (no hizo falta: el texto del selector no cambió), `SetupStepAnswers.kt` (el reductor UNKNOWN ya limpiaba), `SetupReviewStep.kt`. Sin movimientos ni borrados. CRLF conservado.
Aviso: `SetupWizardFullJourneyUiTest.kt` lo edita en paralelo otro agente (región Home); mi edición sigue presente tras su última escritura que vi.

## Pruebas
- Nuevas (JVM): `SpanishPluralsTest` (6), `WizardPluralCopyTest` (12), `SetupWizardOneDayCopyTest` (1, ViewModel real con 1 día: «Encaja con tu semana de 1 día», nunca «1 días»), `SetupBodyFatStepTest` (14), `SetupWizardBodyFatViewModelTest` (8: bloqueo sin acción, figura, medición, omitir sin avanzar, omitir limpia lo elegido, mover tras omitir, usuario que vuelve con dato en Ajustes, omitir con dato previo).
- Clases a ejecutar para verificar el paquete: las 5 nuevas + `SetupStepAnswersTest`, `SetupWizardStateTest`, `SetupWizardOrchestrationTest`, `SetupWizardFullJourneyTest`, `SetupWizardActivationGateTest`, `SetupWizardStepApiTest`, `SetupWizardModelsSubmitTest`, `SetupWizardToggleStepChoiceTest`, `SetupDraftCompatibilityTest`, `SetupDraftRepairTableTest`, `SetupProtocolSourceGuardTest`, `SetupStepDefinitionsTest`, `PersonalizedPlanCatalogTest`, `PlanCandidateEvaluatorTest`. Opcionales y largas (no ejecutadas; no se ven afectadas salvo texto): `SetupExecutableAvailabilityMatrixTest` (fija «N planes publicados» solo con N>1, sin cambio), `SetupWizardAuthoredPlansTest`.
- androidTest: solo se compiló (sin emulador). Pendiente de ejecutar por quien coordina: `SetupWizardFullJourneyUiTest` (región grasa) y, por seguridad, `WizardGateComponentsUiTest`.

## Gradle
- `W1-compile-1.log`: `compileBaseDebugKotlin compileBaseDebugUnitTestKotlin compileBaseDebugAndroidTestKotlin --continue` — exitCode 0 (9 min 2 s).
- `W1-tests-1.log`: `testBaseDebugUnitTest` con 19 clases (`-Exclude *.SessionTemplateRepositoryTest`) — exitCode 0; 197 tests, 0 fallos, 0 omitidos (resumen en `test-evidence\W1`).

## Riesgos abiertos
- El estado «Sin dato todavía»/«Omitido» y la línea «Usar ≈ N %» se comprobaron por código y tests de lógica, no visualmente (sin emulador); revisar el espacio vertical en pantallas pequeñas.
- Borradores en curso previos al cambio y sin grasa declarada ahora exigen una acción en ese paso y en la activación.
