# wizvm — razones tipadas y planes de autor en el wizard real

Fecha: 2026-10-01. Solo edición; no se ejecutó Gradle, adb ni tests. La compilación fue mental (Grep de cada símbolo)
y las expectativas de los tests sobre el catálogo se precalcularon con un port en Python del resolver
(`scratchpad/sim.py`, NO es evidencia de ejecución: ver «Riesgos»). Respaldo para diffs:
`artifacts/consolidation-20261001/snapshot-pre-wave1/android-native/app/src/...`.

## Hallazgo (tarea 2 / punto 4): Q5-F2 CONFIRMADO por lectura

`SetupWizardViewModel.materializeProgram` (rama `CatalogSource.PROTOCOL`) hacía `PROTOCOL_LIBRARY.first { it.id == entry.sourceId }`.
Los `sourceId` de los cuatro planes de autor (`phul-ms-2021-r1`, `phat-biolayne-2016-r1`, `phul-kpkn-r1`, `phat-kpkn-r1`) no existen en
`PROTOCOL_LIBRARY` (grep en todo `main`: solo aparecen en `PersonalizedPlanCatalog`). `NoSuchElementException` no la capturaba
`candidateEngine` (solo Cancellation / PlanMaterializationException / SetupCandidateFailureException), así que el evaluador la
convertía en `Rejected(INTERNAL_MATERIALIZATION)` para los cuatro en cualquier recorrido real. No existe otro camino que los materialice:
T006 los materializa con `PlanMaterializer.materialize(strict=false)` directo y `PlanAdaptationResolver.adapt` no tenía llamador de producción
(confirmado por grep). Tampoco `Program.planProvenance` se escribía en ningún sitio (`PlanDetailsSummary` lo lee para «Plan y procedencia»
y para los «Cambios registrados»).

## Qué cambió y por qué

### Producción
1. **`domain/onboarding/NativePlanFailureMapper.kt` (nuevo)** — F-A2. `typedFailure(report)`: `TIME_BUDGET → SESSION_DURATION` con
   `requiredMinutes = report.maxSessionMinutes`; `APPARATUS_ABSENT → MATERIAL`; `PROFILE_MISMATCH → PROFILE`; `COMPOSITION → COMPOSITION`
   (mismo mapeo que `PlanGenerationCoverageT006Test.kt:264-286`). `null` si `reasonCode` es null o desconocido → el llamador conserva su
   `SetupCandidateFailureException` heredada (clasificación por texto). `DEFAULT_MESSAGE` compartido.
2. **`SetupWizardViewModel.kt`**
   - Rama NATIVE: `throw (NativePlanFailureMapper.typedFailure(report) ?: SetupCandidateFailureException(...))`. `candidateEngine` ya re-lanza
     `PlanMaterializationException` sin tocarla; el aviso «Este plan necesita N min por sesión; elegiste M min» (SetupTrainingSteps.kt:926)
     ya consume `rejection.requiredMinutes`, que `setupRejectionOf` ya propagaba, así que **SetupTrainingSteps.kt no necesita cambios**.
     `translateCandidateFailure`/`stageForUnavailable` quedan solo como respaldo.
   - `materializeProgram`: si `entry.source == PROTOCOL && entry.authoredSource != null` → nuevo `materializeAuthoredPlan(...)`, que arma un
     `AuthoredPlanRequest` (equipo efectivo REAL con `resolveEffectiveEquipment`, `availability`, catálogo, metadatos de composición,
     opciones, frecuencia pedida) y delega en `AuthoredPlanMaterializer`. La ruta histórica `PROTOCOL_LIBRARY` queda intacta para los
     protocolos heredados.
   - `candidateEngine`: para entradas de autor la receta del `PlanMaterializationOutcome` es `program.sourceRecipe` (la efectiva, derivada si
     hubo adaptación), no la del catálogo.
   - `collectViable`: orden ESTABLE que mueve las entradas con procedencia ADAPTED detrás del resto (el desempate por id del planificador
     habría puesto «adapted:» delante de «native:»; §15.3 «propios primero»). No toca el ranking entre las demás.
3. **`domain/onboarding/AuthoredPlanMaterializer.kt` (nuevo)** — la ÚNICA ruta de PHUL/PHAT para candidatos, preview y activación.
   - ORIGINAL: `missingMaterialOf` sobre todas las configuraciones de la receta; sin material → `PlanMaterializationException(MATERIAL,
     APPARATUS_ABSENT|APPARATUS_UNKNOWN)` con mensaje `«Esta receta necesita material que no has declarado: tok1, tok2»` (misma convención
     que la guardia fija: la UI saca de ahí «Falta confirmar X»). Configuración inexistente → `CATALOG/UNRESOLVED_CONFIGURATION`. Nunca
     sustituye ni muta la receta.
   - ADAPTED: `PlanAdaptationResolver.adapt(...)` con el equipo efectivo real; `NotViable` → etapa/motivo cerrados de §15.2 (sin publicar nada).
   - Ambos: `ProgramRecipeValidator.hardFindings` ANTES de materializar (COMPOSITION tipado, con exenciones por día/regla de la receta) y
     `PlanMaterializer.materialize(strict = false)` (los HARD ya se comprobaron con motivo); comprueba la frecuencia pedida y rellena
     `schedulePlan`; escribe `planProvenance = recipe.provenance` y `structureTemplateId = entry.id` (como los nativos).
4. **`PlanCandidateEvaluator.kt`**: `Ready.recipeSnapshot = outcome.recipe ?: recipe` y `Ready.provenance = outcome.recipe?.provenance ?: entry.provenance`.
   Sin motor que aporte receta se comporta igual que antes.
5. **`PlanAdaptationResolver.kt`** (sale de ser código sin llamador; se corrigieron tres defectos que solo aparecen con recetas reales de 6/12 semanas):
   - cada `DayRecipe` distinto se adapta UNA vez (`dayMemo`): antes un cambio se registraba 12 veces en PHUL y el coste crecía con las semanas;
   - `changes` se deduplica por (slot, desde, hacia) (PHAT tiene RIR distinto en las semanas 5–6: dos días distintos, el mismo cambio);
     `adaptedRecipeId`, `slotChanges` y `Adapted.changes` usan la lista deduplicada (el test existente `assertEquals(first.changes, provenance.slotChanges)` se mantiene);
   - un slot SPEED con material ausente ya no se «sustituye»: antes `applySubstitution` reescribía sus 6×3 al 65 % en series de RIR 8–12 y rompía
     la base «carga de trabajo de 3–5 reps del mismo ejercicio» (§10.3/§14.2). Ahora → `NotViable(NO_VALID_SUBSTITUTION, slotId=speed-…)`;
   - una adaptación de una adaptación conserva a su ORIGINAL como `parentId/parentRevision` (antes pasaba a ser hija de `adapted:phul-kpkn-r1`).

### Decisiones propias (revisables por el orquestador)
- **Nivel**: los planes de autor NO se filtran por nivel (`level = null` en `adapt`, y no hay rechazo LEVEL_UNSUITABLE para el original). Razón: §15.3
  «sin ocultar los originales» y paridad con el comportamiento actual de los protocolos (todos visibles para todos los niveles); un original
  visible y su adaptación oculta sería incoherente. Efecto colateral deliberado: las sustituciones usan el RIR 2 inicial del autor
  (`INITIAL_RIR`), no el RIR 3 de principiante de los planes propios. Si se prefiere filtrar, basta pasar `draft.experience.toCatalogLevel()` en
  `AuthoredPlanMaterializer.adaptedRecipe` (y añadir el mismo chequeo al original).
- **Presupuesto**: `sessionBudgetMinutes = null` en `adapt`; el tiempo lo decide el estimador común en el paso 9 del evaluador (§12.2),
  no la aproximación `sets*(rest+45)` del resolver (§14.3 «H6 debe delegar en el estimador común»).
- **`general_gym` heredado**: no se reinyecta; si el draft lo trae (ruta legacy) el resolver lo reconoce y no hay faltantes.

## Tests añadidos (todos nuevos; T006 y los tests de otros dueños no se tocaron)
- `domain/onboarding/AuthoredPlanFixtures.kt` — fixtures E6/E5/E3/E0 + «E6 sin rack» + «E6 sin barra», `Gear`, `prepare/request`.
- `domain/onboarding/AuthoredPlanMaterializerTest` — 4 planes con gimnasio completo (tablas 18/16/21/18 y 21/17/24/28/28, SPEED 6×3@65 % con carga
  pendiente, procedencia ORIGINAL/ADAPTED, edición 2021-05-26/2016-05-30 + consulta 2026-09-28); «sin rack»: exactamente 2 `slotChanges` (bp, bp-inc),
  `reference1RM/loadReference/percent/weight` nulos, RIR 2, descanso 180 y recetas del catálogo intactas (JSON antes/después); roundtrip Room
  (`toEntity().toProgram()`); originales sin material (E3, E5, E0); adaptaciones sin sustituto (PHAT E0 `row-pendlay`, PHUL E0 `inc-db`); SPEED sin
  sustituto (E6 sin barra); E3 coherente con el veredicto del resolver; frecuencia pedida distinta; configuración inexistente; H6 duro; determinismo.
- `domain/training/PlanAdaptationAuthoredRecipesTest` — resolver con las recetas reales: sin cambios `assertSame`; un cambio por slot en 12 semanas;
  padre = original; dedupe con RIR por semana (PHAT sin barra de dominadas → 1 cambio); SPEED intacto; SPEED sintético sin sustituto.
- `domain/onboarding/NativePlanFailureMapperTest` — tabla del mapeo, `null` sin código, mensaje por defecto y el rechazo REAL del fitter
  (Músculo corporal 1 día/20 min → `requiredMinutes == report.maxSessionMinutes > 20`).
- `domain/onboarding/AuthoredPlansActivationParityTest` (Robolectric, Room en memoria) — el `Ready` del evaluador con el puerto que usa el VM es exactamente lo
  que `SetupCommitCoordinator` activa y lo que se reabre (PHUL original y PHUL adaptado sin rack con sus slotChanges), reintento idempotente, y los
  rechazos (PHUL adaptado E0, PHAT original E3) tipados sin escribir en Room.
- `screens/onboarding/SetupWizardAuthoredPlansTest` (Robolectric, VM real, sin `materializeOverride`) — PHUL (E6, 4 d, 100 min) y PHAT (E6, 5 d, 100 min):
  ningún autor como INTERNAL, original y adaptado viables, preview = programa de la receta de la entrada con procedencia, orden «adapted» detrás de «native»,
  estabilidad A→B→A; PHUL sin material: rechazos tipados, `programPreview == null` y error con el material que falta; F-A2 por el VM (Músculo NEW E0 1 d/20 min →
  `TIME_BUDGET`, etapa DURATION, `requiredMinutes > 20`).

## Verificación sugerida (de la más rápida a la más pesada)
```
./gradlew testBaseDebugUnitTest --tests '*.NativePlanFailureMapperTest'
./gradlew testBaseDebugUnitTest --tests '*.PlanAdaptationAuthoredRecipesTest' --tests '*.PlanAdaptationResolverTest'
./gradlew testBaseDebugUnitTest --tests '*.AuthoredPlanMaterializerTest' --tests '*.AuthoredRecipeCompositionTest'
./gradlew testBaseDebugUnitTest --tests '*.PlanCandidateEvaluatorTest' --tests '*.PlanCandidateSessionCacheTest' --tests '*.PersonalizedPlanCatalogTest'
./gradlew testBaseDebugUnitTest --tests '*.AuthoredPlansActivationParityTest'
./gradlew testBaseDebugUnitTest --tests '*.SetupWizardAuthoredPlansTest'
./gradlew testBaseDebugUnitTest --tests '*.SetupProtocolSourceGuardTest' --tests '*.SetupWizardActivationGateTest' --tests '*.SetupWizardPreviewRaceTest' --tests '*.SetupWizardOrchestrationTest'
./gradlew testBaseDebugUnitTest --tests '*.SetupExecutableAvailabilityMatrixTest.T019_A*' --tests '*.PlanGenerationCoverageT006Test' --tests '*.T006PersistenceAndUseIntegrationTest'
```

## Riesgos
1. **Ninguna prueba se ejecutó.** Las expectativas sobre el catálogo (qué slots cambian en cada escenario) salen de un port en Python del resolver sobre
   `exercise_catalog_v2.json`; el port reproduce `resolveWithAvailability`, `requirementsOf`, `evidenceOfKind`, `pickCandidate` y las tablas curadas, pero
   no es el código Kotlin. Primeros sospechosos si algo falla: «E6 sin rack» (2 cambios), PHAT adaptado E0 (`row-pendlay`), PHUL adaptado E0 (`inc-db`),
   E6 sin barra (`speed-*`), PHAT adaptado E5 (`hack`).
2. `AuthoredPlanMaterializer` usa `hardFindings` (estricto). Depende de que `AuthoredRecipeCompositionTest.authored_recipes_have_no_uncovered_hard_composition_findings`
   esté verde; si hoy no lo está, los originales se rechazarán con COMPOSITION (hallazgo real, no oráculo flojo). T006 usaba `strict=false` y por eso no lo veía.
3. PHUL adaptado con mancuernas+banco (E3) devuelve `Adapted` con ~16 cambios y 4 accesorios descartados (`skull`, `tri`, `ext`, `curl-h`): lo que decida la
   composición después (H6/TAXONOMY de configuraciones nuevas como `calf_raise__bilateral__bodyweight`) puede rechazarlo con COMPOSITION; los tests lo aceptan
   explícitamente (única razón de rechazo permitida) y comprueban todas las demás invariantes si se publica.
4. Estimación estática de duración (SessionDurationEstimator: preparación 60 s por ejercicio, series a max(4·reps_max,45) s, descanso solo entre series, sin
   aproximaciones en PHUL por no usar porcentajes): PHUL ≈ 53–65 min por día y PHAT día 6 ≈ 73 min, por lo que los tests exigen que ambos quepan en 100 min.
5. Los tests de VM dependen del reloj de pared (hasta 90 s por espera, `runTest` con 6 min). Siguen el patrón de `SetupExecutableAvailabilityMatrixTest`
   (updateStep + espera acotada) y de `SetupWizardActivationGateTest` (persistencia/entorno en memoria); no incluyen `commit()` porque exigiría los ~40 pasos de la ruta
   (la igualdad preview==activado a nivel Room la cubre `AuthoredPlansActivationParityTest`).
6. Las entradas de autor ahora son candidatos reales: cualquier androidTest/UI que eligiera «la primera tarjeta» con 4–5 días podría ver una (las adaptadas van al final).

## No hecho / pospuesto
- Tarjeta de candidato con «Adaptado a tu material: N cambios» (el dato está en `Ready.provenance.slotChanges`; haría falta exponerlo en `CandidateScan`/`SetupPlanCandidate`).
- Tercer estimador `estimateFixedSessionMinutes` del VM (sets*(45+descanso)): sigue ahí; para planes de autor devuelve null (las sesiones materializadas guardan los
  ejercicios en `parts`), así que no impone ningún aviso falso.
- Chequeo de nivel de originales (ver decisión).
- `SetupTrainingSteps.kt`: sin cambios (ya mostraba «Este plan necesita N min» cuando `requiredMinutes != null`; elige `candidateRejections.firstOrNull()`).
