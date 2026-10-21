FIX-TM · Separación de TM y 1RM

Hecho en `C:\kw\u13`, rama `sv2/programas`, sobre `6aacc31f4`. Un agente anterior (Codex) se cortó por límite de uso tras el diagnóstico, sin cambiar código; este informe recoge su análisis, lo confirma y describe la corrección completa. Se leyeron el encargo, `CLAUDE.md` y los informes CAL1 y PROG.

## Regla aplicada (la de CAL1)

- `reference1RM` significa siempre 1RM; `referenceTrainingMax` es el TM.
- Una serie `PERCENT_1RM` (y `PERCENT_DESIRED_MAX`) lee el 1RM.
- Una serie `PERCENT_TM` lee el TM. Sin TM declarado la carga queda pendiente: no se usa el 1RM como TM ni se deduce un TM desde un PR o el historial (decisión D7, ya fijada por `AuthoredTmLoadConsumerContractTest` y `Cal1CapacityContractTest`). Una excepción acotada: en `PlanMaterializer`, con un 1RM capturado como referencia declarada y sin TM, TM = 1RM × la fracción de TM de la receta (ver abajo).
- Nunca se deduce un 1RM desde un TM.

## Diagnóstico confirmado

- Origen: `git show af6e6e546^:.../Calculations.kt` muestra que antes de CAL1 `explicitTrainingMaxLoad` leía `exercise.reference1RM` como TM; CAL1 (`af6e6e546`) la pasó a `referenceTrainingMax` y `PlanMaterializer` empezó a guardar el 1RM en `reference1RM` y el TM en `referenceTrainingMax`. Los archivos de las pruebas rojas no tienen diferencias entre `373729ae0` y el HEAD de PROG, así que PROG no las rompió. No se repitió la corrida completa sobre `373729ae0`: la comparación de código anterior a CAL1 es la que permitía el encargo y la cola de Gradle no lo justificaba.
- Las pruebas que daban «esperaba 90/180, recibe 100/200» codificaban la mezcla antigua (`reference1RM` con el TM dentro): fallo de prueba.
- Además había fallos reales de la app, los tres del análisis de Codex, y uno más que apareció al barrer:
  1. `PlanMaterializer.materializeSlot` elegía la base de TODAS las series del slot a partir de la primera. Un slot con una serie %TM y otra %1RM resolvía una de las dos sobre la referencia equivocada.
  2. Con un 1RM capturado como referencia declarada (`EXERCISE_1RM`) y series %TM, la carga se calculaba como %TM × 1RM (80 % de 100 = 80 kg en vez de 80 % de TM 90 = 72 kg) y `referenceTrainingMax` quedaba nulo, así que el editor no podía reproducir esa carga. Faltaba derivar el TM con la fracción de TM de la receta.
  3. Varios consumidores multiplicaban cualquier `targetPercentageRM` por el 1RM: `AugeFatigueEngine` (carga planificada), `MuscularSessionImpactEngine`, `SessionAssistantEngine`, `TrainingEnergyEngine.resolvePlannedExternalLoadKg`, `HomeSessionHero` (dosis), `WorkoutLoadSuggestionController.getWarmupWorkingWeightAnchor` (ancla de calentamiento; además usaba capacidad estimada × %TM), y la intensidad media del editor (`SessionEditorAugeComputation`).
  4. Editor: la fila de serie (`InlineSetRow`) habilitaba el deslizador y calculaba el % sobre el 1RM aunque la serie fuera %TM; cambiar el TM en «Carga inteligente» no recalculaba las cargas %TM (solo miraba el 1RM); el aviso «falta referencia» no distinguía TM de 1RM; el diálogo de superseries no tenía campo de TM.

## Cambios en la app

Archivos bajo `android-native/app/src/main/java/com/example/kpkn/`:

| Archivo | Cambio |
|---|---|
| `domain/calculations/Calculations.kt` | Nuevas funciones puras `percentLoadReferenceKg`, `percentLoadKg`, `percentOfOneRm` y `missingPercentReferences`: una sola regla de base por serie para todos los consumidores. `calculateSuggestedLoad` no cambia de semántica (ya leía TM en %TM). |
| `domain/training/PlanMaterializer.kt` | Base de carga POR SERIE según su `loadBasis` (`baseLoadKgFor`). Con `EXERCISE_1RM` capturado: %1RM sobre el 1RM y %TM sobre TM = 1RM × fracción de TM de la receta; ese TM derivado se guarda en `referenceTrainingMax` cuando hay series %TM. Con `EXERCISE_TM`: %TM sobre el TM y %1RM pendiente (no se deduce 1RM). Trabajo observado y lastre siguen siendo la base de todas las series. |
| `domain/auge/AugeFatigueEngine.kt`, `domain/auge/MuscularSessionImpactEngine.kt`, `domain/sessionassistant/SessionAssistantEngine.kt` | Carga planificada de una serie %RM con `percentLoadKg` (TM en %TM, 1RM en el resto). |
| `domain/energy/TrainingEnergyEngine.kt` | Las series %TM se resuelven sobre el TM; sin TM ya no se calcula 1RM × %. |
| `screens/home/design/HomeSessionHero.kt` | Dosis en kg con `percentLoadKg`. |
| `screens/workout/WorkoutLoadSuggestionController.kt` | `getWarmupWorkingWeightAnchor` lee el %RM sobre la referencia de su base y no usa la capacidad estimada con un %TM. La mejora de sesión sigue comparando capacidades con 1RM (CAL1). |
| `screens/sessioneditor/SessionEditorRmLoads.kt` | Cambiar el TM recalcula las series %TM; cambiar el 1RM, las series de 1RM (por serie). |
| `screens/sessioneditor/SessionEditorAugeComputation.kt` | La intensidad media se expresa en %1RM: un %TM se convierte con TM ÷ 1RM; sin ambos no se mide. |
| `screens/sessioneditor/components/InlineSetRow.kt`, `ExerciseSetList.kt`, `SupersetRoundsAndConfig.kt` | Las series %TM leen el TM para el deslizador, el % mostrado y el cálculo; la clave de recordar de las cargas previstas incluye el TM. |
| `screens/sessioneditor/components/ExerciseEditorDialogs.kt`, `SupersetRoundsAndConfig.kt` | «Carga inteligente»: el campo se llama «1RM referencial»; el diálogo de superseries gana el campo «Training max (TM)»; el aviso de falta de referencia distingue TM de 1RM. |

## Cambios en las pruebas

Adaptadas porque codificaban la mezcla anterior a CAL1 (cada una lleva su explicación en un comentario):

- `PlanAdaptationResolverTest`: leía el TM en `reference1RM`; ahora 1RM 100 en `reference1RM` y TM 90 en `referenceTrainingMax`.
- `ProgramProtocolEngineTest` (dos casos, los de ≈ :248 y :268): comparaban 1RM × % en una serie %TM y esperaban el TM en `reference1RM`. Ahora se comprueba por separado %1RM (1RM), %TM sin TM (pendiente), %TM con TM (TM) y las metas (1RM 200/120/220, TM 180/108/198).
- `ProgramTemplateEngineTest.assertSquatPhase`: la «referencia de la fase» en %TM es el TM (180), no `reference1RM` (200).
- `PlanMaterializerCardioIdentityTest`: el 1RM capturado con series %TM da TM 90 y carga 72 kg (antes 80 kg); la squat del perfil tiene `reference1RM` 200 y TM 180 (antes se esperaba 180 en `reference1RM`). Caso nuevo: 1RM capturado con series %1RM, 80 kg y sin TM inventado.

Añadidas (explícitas, con y sin TM):

- `domain/calculations/FixTmLoadBasisContractTest` (7): %1RM con TM presente, %1RM sin 1RM pero con TM (pendiente), %TM con TM, %TM sin TM pese a haber 1RM (pendiente, con valores inválidos), ejercicio mixto, avisos de referencia por base y energía por serie.
- `domain/training/FixTmMaterializerLoadBasisTest` (5): perfil con %1RM y %TM, slot mixto en los dos órdenes (el fallo de la primera serie), 1RM capturado → TM derivado, TM capturado (nunca 1RM; %1RM pendiente), sin perfil ni referencia todo pendiente.
- `screens/workout/FixTmWarmupAnchorTest` (3): ancla de calentamiento en %1RM, %TM con TM y %TM sin TM.

## Pruebas ejecutadas

Siempre con `bash /c/kw/tools/gradle_slot.sh /c/kw/u13/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" ...`, en segundo plano con log y espera, una corrida cada vez.

1. `:app:testBaseDebugUnitTest --tests 'com.example.kpkn.domain.training.*' --tests 'com.example.kpkn.domain.calculations.*' --tests '*ProgramProtocolEngineTest' --tests '*AuthoredTmLoadConsumerContractTest' --tests '*Cal1*' --continue` (log `C:/kw/logs/fixtm-1.log`): compila producción y pruebas; 1788 pruebas, 1 fallo: `PlanAdaptationResolverTest` (la corrección de esa prueba se me había quedado sin aplicar al lanzar; se aplicó justo después). El resto, incluidas `ProgramProtocolEngineTest`, `ProgramTemplateEngineTest`, `PlanMaterializerCardioIdentityTest` y las clases nuevas, en verde.
2. `:app:testBaseDebugUnitTest --tests '*PlanAdaptationResolverTest' --tests '*FixTm*' --tests 'com.example.kpkn.domain.energy.*' --tests 'com.example.kpkn.domain.auge.*' --tests 'com.example.kpkn.domain.sessionassistant.*' --tests 'com.example.kpkn.screens.sessioneditor.*' --tests '*HomeSessionHeroDoseTest' --tests '*LiveSetMode*' --tests '*WorkoutLoadSuggestion*' --tests '*Cal1*' --tests '*Cal2LoadAdjustmentParityTest' --continue` (log `C:/kw/logs/fixtm-2.log`): 986 pruebas, 16 omitidas, 2 fallos, ambos de `ExerciseCatalogImageCoverageTest` (`FileNotFoundException`): el worktree `u13` es un checkout parcial (`core.sparseCheckout`) sin la carpeta `catalog/exercises/images/` que esa prueba lee, así que es del entorno y no de este cambio. `PlanAdaptationResolverTest` (33), `AuthoredTmLoadConsumerContractTest` (13), `FixTmLoadBasisContractTest` (7), `FixTmMaterializerLoadBasisTest` (5) y `FixTmWarmupAnchorTest` (3), en verde.

No se ejecutó la batería completa, ni nada en teléfono ni con adb. Sin push.

## Decisión pendiente y observaciones

- **Decisión de producto (sin cambiar):** una serie %TM de un ejercicio con 1RM declarado y sin TM queda pendiente (el editor avisa «Falta el TM»). El encargo proponía resolverla como 1RM × % de la receta en todos los consumidores, pero eso contradice las pruebas D7/CAL1 (`an_explicit_tm_basis_does_not_guess...`, `shared_history_preserves_a_pending_tm...`, `exercise_and_session_normalization_keep_a_missing_tm...`): hidratar un 1RM desde el PR o el historial rellenaría cargas %TM, lo que esas pruebas prohíben. Por eso la derivación «TM = 1RM × % de la receta» vive solo en `PlanMaterializer` (donde la receta y el 1RM declarado se conocen) y se guarda en `referenceTrainingMax`. Si se quiere la derivación también al editar a mano, hace falta guardar la fracción en `Exercise` y decidir cómo distinguir un 1RM declarado de uno inferido del PR.
- **Sin tocar:** las aproximaciones de calentamiento (`WorkoutViewModel.recordWarmupHeavinessAndAwait`, `WarmupPreparationRow`) multiplican `percentageOfWorkingWeight` por el 1RM resuelto en lugar de por la carga de trabajo. No es una confusión TM/1RM sino un criterio aparte; queda anotado.
