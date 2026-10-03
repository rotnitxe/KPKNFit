# Nota del paquete `prog` (progresión real del plan §12.4)

Solo edición (Edit / archivos nuevos); sin Gradle, adb, emuladores ni tests. Cada símbolo nuevo se verificó con Grep
(firmas, visibilidad, nulabilidad) y el diff completo se revisó contra `snapshot-pre-wave1`. Además se comprobó el
balance de paréntesis/llaves de todos los archivos tocados con un script.

## Qué cambió y por qué

| Hallazgo | Cambio | Dónde |
|---|---|---|
| P1 / F-01 | La primera carga elegida a mano se conserva para las sesiones futuras del ciclo cuyo peso sigue **sin resolver**: marcador `manualLoadRequiredSides` **o** (ejercicio `nativeProgressionManaged` con peso nulo del lado, mismo criterio que `SetExecutionCard`). Nunca pisa una carga ya resuelta. | `NativeWorkoutProgressionRuntime.captureManualLoadForFuture/captureManualLoad/isLoadUnresolved` |
| P2 / F-02 | «Entrenada» pasa de `sessionId` a par `(ciclo, sesión)`. El ciclo de un log sale del id de instancia `inst_c<N>_…` (los COMPLEX nativos antiguos sellaban `cycleNumber = 1`), después `cycleNumber`, después 1. `resolveProposal` usa el ciclo en curso de `runState`. Público: `NativeWorkoutProgressionRuntime.logCycle(log)`. | `logCycle`, `executionKey`, `executedKeys`, `hasFuturePrescription`, `resolveProposal` |
| P3 / F-02 | Nuevo `carryForwardToNextCycle(program, logs, closedCycle, nowMs)`: (a) caduca las propuestas nativas pendientes con motivo «Caducada al cerrar el ciclo N…» y consume sus logs; (b) escribe la última referencia por identidad (última exposición **no de descarga** del ciclo cerrado) en todas las series de trabajo de todas las sesiones y en la bolsa `exerciseLoadReferences`; (c) adopta la última variante corporal aceptada en las sesiones y en la receta efectiva del ciclo nuevo. Idempotente (probado) y además protegida por la guarda `native-progression-c<N>`. Se invoca desde `registerNativeContinuationOnce(..., logs)` (parámetro nuevo con default) **después** de comprobar la guarda. | runtime + `ProgramProgressEngine.completeCycle/registerNativeContinuationOnce` |
| P4 / F-03 | `Exercise.loadQuantityConvention` de los slots F/H/I nativos se deriva del `equipmentId` del catálogo (barra, ez/hex/safety/t/h bar, discos, máquina, polea, Smith → `TOTAL_EXTERNAL`; mancuernas, kettlebell → `PER_IMPLEMENT`; peso corporal/banda/TRX → `UNSPECIFIED`). Una referencia declarada o capturada sigue ganando. `knownNextLoad` decide la familia de stock por equipo del catálogo; la subcadena del id queda **solo** como último recurso cuando el catálogo no conoce la configuración (ids sintéticos/legados). | nuevo `NativeLoadConventions.kt`; `PlanMaterializer.materializeSlot` (el «materializeExercise» del encargo); `ExerciseCompositionMetadata.equipmentId` (+ `CatalogCompositionMetadataProvider`) |
| P5 / F-06, F-08d | La rama la decide la identidad: `BODYWEIGHT` + REPS → variante corporal, aunque la receta sea de carga; el resto con REPS y receta `REP_RANGE_THEN_LOAD` → carga; `unitMode != REPS` nunca progresa por esta regla. `resolveProposal` aplica el mismo criterio al validar la propuesta. | `progressionBranchFor/proposalMatchesStrategy` |
| P7 / F-08 | Misma carga en toda la ventana (tolerancia 0,0001 kg); la carga de la exposición es la de la **primera** serie de trabajo; subir exige además carga uniforme en las series; fallo → RIR 0; fuera exposiciones de descarga (no cuentan ni como éxito ni como fallo, tampoco para capturar carga); fuera exposiciones con drop/rest-pause/AMRAP vivos; `recipeSet` indexa `slot.sets` sin calentamientos y el día/slot se resuelve por la semana de la sesión (`progressionIndex` → `WeekRecipe`, con respaldo a la primera coincidencia). | `exposuresForLog`, `sameLoadAcrossWindow`, `actualRirOf`, `hasLiveTechnique` |
| P8 / F-10 | Al aceptar una variante corporal se registra en `Program.effectiveWeekRecipes` de cada ocurrencia futura del ciclo en curso (`slot.lift.configurationId` cambiado + `PlanSlotChange(samePattern = true, loadReferenceKept = false)` + `AppliedRecipeProposal(kind = NATIVE_BODYWEIGHT_VARIANT)`), de modo que `rematerializeWeek` ya no la pierde. El arrastre de ciclo la repite para el ciclo nuevo. Hecho íntegro dentro del runtime (sin tocar `PlanMaterializer` fuera de `materializeSlot`). | `withVariantInEffectiveRecipes/upsertVariantInWeek/carryBodyweightVariants` |
| P10 / F-11 | Barra: la menor carga **formable** con los discos y cantidades declaradas que cambie la carga en la dirección pedida (suma de subconjuntos por lado en gramos enteros), con la barra declarada (`resolvedBarbellWeightKg`) y sin bajar nunca de la barra vacía. Mancuernas/kettlebell/máquina: si hay material declarado pero ningún paso en esa dirección → aviso de «tope del material» (`EXPIRED`, `userFacingNotice`), no un «sube ligeramente» sin salida. Sin material declarado → carga por elegir como antes. | `NextLoad`, `knownNextLoad`, `nextBarbellLoad` |
| Test | Aserción tautológica de `ProgramProgressCycleCloseTest` sustituida por `weekOccurrence == 1` y `runState.weekInstanceId == inst_c2_w1`; `workoutLog` del test del runtime usa fechas distintas y crecientes por log (sufijo numérico del id). | tests |

Decisiones respetadas: la identidad de progresión no cambia (`recipeDayId`/`recipeSlotId` se conservan); AUGE global intacto;
las propuestas siguen confirmándose por el flujo existente; ningún kilo registrado se reescribe (solo prescripción futura/plantilla).

## Decisiones y matices que conviene conocer

* **Ventanas entre ciclos.** Una ventana puede mezclar la última semana de entrenamiento del ciclo N con la semana 1 del N+1
  (misma identidad, misma carga, no descarga). Es deliberado: son dos exposiciones consecutivas reales. Lo consumido por una propuesta
  caducada (cierre de ciclo) no se reutiliza.
* **El arrastre reescribe la prescripción de sesiones ya entrenadas del ciclo cerrado** (los ciclos reutilizan las mismas sesiones). Los
  logs, los ids de sesión/ejercicio/serie y las sesiones congeladas por edición manual (`manualSessionOverrides`) no se tocan.
* **Orden del cierre en `finalizeWorkout`:** `advanceAfterSessionComplete` (incluye `completeCycle` y el arrastre) corre **antes** del hook
  nativo; el log de la semana de descarga ya no captura carga a sesiones futuras (excluido por ser descarga).
* **`ASSISTANCE` / `ADDITIONAL_BODYWEIGHT` no se derivan del catálogo:** dependen del modo de carga que registra el atleta (el catálogo no
  tiene configuraciones asistidas/lastradas para los slots propios) y no tienen paso basado en stock; el runtime las sigue respetando
  cuando vienen en la convención.
* **Escalera corporal:** sigue siendo una sola pareja (`knee_push_up__default` ↔ `push_up__flat`); moverla a datos del catálogo es la
  pregunta abierta 4 del informe y no se decidió.
* **Aviso lateral:** un slot corporal unilateral genera el aviso «la variante se aplica al ejercicio completo…» cada dos exposiciones (ya
  ocurría en planes 100 % corporales; ahora también en recetas mixtas).

## NO ENTREGADO (honestidad de cobertura)

* **R13 / R14 (cardio, §12.4 «dos exposiciones conversacionales → 10→15→20→30»).** Sin consumidor. Falta el dato: ni `CompletedSet` ni
  el registro de cardio guardan intensidad conversacional. `WorkoutViewModel` (registro de cardio, ~l.5585) escribe
  `rpe = details.resolvedRpe()`, que es la intensidad **planificada** del bloque, no el esfuerzo reportado; `avgHeartRate` es opcional y
  no hay zonas ni edad con las que decidir «conversacional» sin inventar un umbral. Faltan además la pregunta al terminar el bloque
  (`CardioLiveCard`) y el punto de llamada (un hook cardio en `ProgramRepository.finalizeWorkout`). `RecipeCardioBlock.progression` se
  sigue escribiendo y nadie lo lee; `nextCardioDurationAfterTwoConversationalExposures` sigue siendo un adaptador solo de tests.
  No se añadió UI ni campos.
* **R16 (cláusula de adaptaciones de originales).** Sin consumidor: las recetas de autor/adaptadas llevan `nativeProgression = null`, así que
  el runtime retorna de inmediato. Los datos para detectarlo existen (`PlanSlotChange.loadReferenceKept == false` en
  `PlanProvenance.slotChanges`), pero activarlo exige que `PlanAdaptationResolver` (otro dueño) marque los slots adoptados y decidir qué
  recetas optan; no se inventó.
* **F-14 UI:** no se escribió el test androidTest de `SetExecutionCard` («Elige una carga para X–Y reps dejando Z en reserva»); no está en el
  alcance de este paquete.

## Riesgos

* Nada se ejecutó: los 11 tests nuevos del runtime y los tres archivos de test nuevos son oráculos escritos a mano (trazados con aritmética:
  p. ej. 60 kg con barra de 20 kg = 20 kg por lado; el menor paso con discos de 1,25 kg es 21,25 kg por lado → 62,5 kg; 22 kg bajo mínimo →
  20 kg de barra vacía). Un rojo puede ser un defecto real del generador de planes (los tests de planes reales dependen de que
  `STRENGTH`/`BARBELL`/1 día y `MUSCLE`/`DUMBBELLS`/4 días se generen, como ya exige `NativeProfileRecipeAndFitterTest`).
* `mixedRealPlan_bodyweightSlotProgressesByIdentity…` usa `assumeTrue` si el plan generado no trae un slot corporal gestionado.
* `CompositionTaxonomy.kt` y `CatalogCompositionMetadataProvider.kt` (sin dueño en la tabla) recibieron un campo/asignación mínimos
  (`equipmentId`, con default null).
* El hook nativo ahora lee el equipo de `CompositionMetadataHolder.current` (se rellena en `initializeExerciseDatabase`); si no hay proveedor
  se usa la regla anterior por id. No hace falta cambiar `ProgramRepository`.

## Verificación sugerida (rápidos → pesados)

```
cd android-native
./gradlew :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.ProgramProgressCycleCloseTest"
./gradlew :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.NativeProgressionEquipmentAndVariantTest"
./gradlew :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.NativeProgressionRealPlanTest"
./gradlew :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.NativeWorkoutProgressionRuntimeTest"
./gradlew :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.training.PlanMaterializerRematerializePreservationTest" --tests "com.example.kpkn.domain.training.PlanLoadReferenceTest" --tests "com.example.kpkn.domain.training.ProgramProgressEngineTest" --tests "com.example.kpkn.domain.training.NativeProfileRecipeAndFitterTest" --tests "com.example.kpkn.data.repository.ProgramRepositoryConsolidationTest" --tests "com.example.kpkn.data.protocols.ProtocolProgramStructureTest"
```
