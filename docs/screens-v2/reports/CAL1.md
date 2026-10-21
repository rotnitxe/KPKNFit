CAL1 · Contrato de AMRAP y calibrador

CAL1 completado en feat/screens-v2: contrato SetPurpose, mínimo único, compatibilidad del JSON, persistencia del resultado, TM separado y estimador v2 sin bonus AMRAP. Editor e importador conservan la intención de las series.

Compilan producción y androidTest. Pasan las selecciones de 777 pruebas principales, 55 de reemplazo, 280 de importación y 116 de correcciones/persistencia, sin fallos, errores ni omitidas. Las selecciones se solapan.

Seis commits en español con la coautoría solicitada, incluido este informe. Sin push ni cambios en artifacts/ o auditorías. Room sigue en v29; el motor CAL2 y las decisiones CAL3 quedan para sus paquetes. No se ejecutan pruebas en teléfono. Las evaluaciones históricas archivadas conservan su versión original.

Cambios de código y pruebas por archivo. `M/` corresponde a `android-native/app/src/main/java/com/example/kpkn/`; `T/`, a `android-native/app/src/test/java/com/example/kpkn/`.

| Archivo | Cambio |
|---|---|
| `M/data/models/Session.kt` | SetPurpose, mínimo nullable, alcance, referenceTrainingMax y retirada del indicador muerto del ejercicio. |
| `M/data/models/SetContractSerialization.kt` | Conversión de JSON antiguo en todos los lectores; escritura explícita del contrato y migración de TM. |
| `M/data/models/WorkoutLog.kt` | Propósito realizado, carga original/tipo/contexto y versión; reutiliza peso normalizado, reps, parciales y esfuerzo existentes. |
| `M/data/models/WorkoutV2Models.kt` | Propósito y mínimo en payload, versión de homologación y reps completas separadas del trabajo parcial. |
| `M/data/protocols/TrainingPlanRecipe.kt` | Propósito/mínimo/alcance explícitos en recetas nuevas y puente compatible desde SetRecipe.amrap. |
| `M/data/sessions/PlRecipeSessionTemplates.kt` | Materializa las recetas con propósito y mínimo efectivo. |
| `M/data/sessions/SessionTemplates.kt` | Limpia propósito, mínimo y alcance al crear series ordinarias. |
| `M/domain/calculations/Calculations.kt` | Estimador v2 sin bonus AMRAP; usa el mínimo efectivo y referenceTrainingMax para %TM. |
| `M/domain/templates/SessionTemplateEngine.kt` | Reconoce AMRAP ejecutable desde el propósito. |
| `M/domain/templates/SessionTemplateQualityRules.kt` | Lee el propósito al evaluar las series de la plantilla. |
| `M/domain/training/BlockTransitionEngine.kt` | La descarga limpia propósito, mínimo y alcance. |
| `M/domain/training/NativeWorkoutProgressionRuntime.kt` | Excluye AMRAP/calibradores por propósito y limpia TM al sustituir una configuración. |
| `M/domain/training/PlanMaterializer.kt` | Materializa propósito/mínimo/alcance y separa las referencias de TM y 1RM. |
| `M/domain/training/ProgramAutoregulationEngine.kt` | Usa mínimo realizado y evidencia moderna; limita el respaldo por posición a registros antiguos sin evidencia. |
| `M/domain/training/SessionToDayRecipe.kt` | Conserva propósito, mínimo y alcance al convertir la sesión en receta. |
| `M/domain/training/importer/ImportedProgramBuilder.kt` | Traslada el + a la última serie y AMRAP sin cifra a todas; sin mínimo no fabrica objetivos de reps. |
| `M/domain/training/importer/ImportedProgramDraft.kt` | El borrador importado conserva propósito y mínimo como datos. |
| `M/domain/training/importer/ProgramImportParser.kt` | Reconoce 5+, 3x5+, 1+ y AMRAP, conserva la nota y crea el contrato real. |
| `M/domain/workout/LoadSuggestionEngine.kt` | Calcula capacidad desde carga normalizada y reps completas; evita reutilizar sugerencias AMRAP v1 con bonus. |
| `M/domain/workout/ScheduledTechniqueExecution.kt` | Consulta el propósito al resolver el esfuerzo de una técnica. |
| `M/domain/workout/SetPropagationRules.kt` | Propaga propósito, mínimo y alcance con la configuración de la serie. |
| `M/domain/workout/WorkoutPerformanceHomologationEngine.kt` | Elimina el bonus, etiqueta resultados v2 y excluye parciales de la capacidad. |
| `M/screens/home/design/HomeSessionHero.kt` | Resumen AMRAP desde propósito. |
| `M/screens/programdetail/PlanDetailsSummary.kt` | Excluye calibradores mediante SetPurpose. |
| `M/screens/programdetail/components/SessionPrescriptionSummary.kt` | Resumen AMRAP desde propósito. |
| `M/screens/programs/PlanInfoModel.kt` | Presentación de la receta desde propósito. |
| `M/screens/sessioneditor/SessionEditorAugeComputation.kt` | Usa la sugerencia de carga común para respetar %TM en los cálculos del editor. |
| `M/screens/sessioneditor/SessionEditorRulesEngine.kt` | Valida mínimos positivos y alcance; comparte normalización que conserva AMRAP. |
| `M/screens/sessioneditor/SessionEditorSessionHelpers.kt` | Conserva AMRAP al normalizar/guardar; distingue el cambio explícito de modo y separa referencias/capacidad. |
| `M/screens/sessioneditor/SessionEditorViewModelSets.kt` | Duplicar y aplicar configuración conservan propósito, mínimo y alcance. |
| `M/screens/sessioneditor/SessionEditorViewModelStructure.kt` | Aplica la conversión de propósito solo cuando la persona cambia el modo. |
| `M/screens/sessioneditor/SessionExerciseDose.kt` | Resumen de dosis desde propósito. |
| `M/screens/sessioneditor/components/ExerciseEditorDialogs.kt` | Campo de TM independiente; RM directo y PR siguen escribiendo únicamente 1RM. |
| `M/screens/sessioneditor/components/ExerciseSetList.kt` | Resumen de serie con propósito y mínimo efectivo, incluido sin mínimo. |
| `M/screens/sessioneditor/components/InlineSetRow.kt` | Lee/edita el mínimo efectivo y conserva la etiqueta de calibrador desde propósito. |
| `M/screens/workout/RelatorContextBuilder.kt` | Deriva su indicador de presentación del propósito de la serie. |
| `M/screens/workout/SeriesOptionsModel.kt` | Etiqueta AMRAP desde propósito. |
| `M/screens/workout/WorkoutEditingRules.kt` | Normalizar en vivo conserva el propósito; el modo no lo reconstruye. |
| `M/screens/workout/WorkoutFeedbackModels.kt` | Activación y mínimo comunes; respeta registros desactivados y distingue null explícito de borrador antiguo. |
| `M/screens/workout/WorkoutLoadSuggestionController.kt` | La mejora de sesión compara capacidades con 1RM y nunca con TM. |
| `M/screens/workout/WorkoutPlannedSetRules.kt` | Las nuevas series ordinarias limpian propósito, mínimo y alcance. |
| `M/screens/workout/WorkoutReplacementRules.kt` | Conserva intención de cada serie al reemplazar el ejercicio. |
| `M/screens/workout/WorkoutSessionContracts.kt` | Borrador durable distingue sin mínimo explícito de un mínimo ausente en JSON antiguo. |
| `M/screens/workout/WorkoutSetRecorder.kt` | Persiste evidencia realizada, mínimo/deuda, reps completas/parciales, carga original/normalizada y contexto; conserva opt-out al editar. |
| `M/screens/workout/WorkoutStructureSheetsHost.kt` | Cambio explícito de modo en el editor en vivo mediante la transformación común. |
| `M/screens/workout/components/SetExecutionCard.kt` | Lee propósito/mínimo y restaura opt-out/borradores sin revivir AMRAP ni fabricar un mínimo. |
| `T/data/models/SessionSerializationTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/data/models/SetPurposeContractTest.kt` | Regresiones de JSON antiguo/nuevo, alcance, TM y lectura del JSON de Room. |
| `T/domain/calculations/Cal1CapacityContractTest.kt` | Misma capacidad en historial, PR, editor, homologación y sugerencias; versiones, parciales y %TM. |
| `T/domain/training/Cal1AmrapProgressionTest.kt` | Regresiones de mínimo 4 frente a máximo 6, opt-out moderno, respaldo antiguo y activación en vivo. |
| `T/domain/training/PlanMaterializerTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/domain/training/ProgramAutoregulationEngineTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/domain/training/ProgramProgressCycleCloseTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/domain/training/TopSetProgressionTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/domain/training/importer/Cal1AmrapImportTest.kt` | Parser y programa construido: + con mínimo y AMRAP sin mínimo. |
| `T/domain/training/importer/ProgramImportParserNoiseTest.kt` | AMRAP sin cifra ahora se importa como sin mínimo. |
| `T/render/SessionExerciseExpandedRenderSheetTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/screens/home/design/HomeSessionHeroDoseTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/screens/programdetail/ProgramDetailViewModelTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/screens/programs/PlanInfoModelTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/screens/sessioneditor/AuthoredTmLoadConsumerContractTest.kt` | Actualiza las expectativas a TM y 1RM separados. |
| `T/screens/sessioneditor/SessionEditorAmrapTest.kt` | Receta real 5/3/1, receta mixta RM, cambio explícito de modo y validación de mínimos. |
| `T/screens/sessioneditor/components/ExerciseSetListComposeTest.kt` | El fixture selecciona AMRAP explícitamente con el contrato nuevo. |
| `T/screens/workout/Cal1AmrapDraftContractTest.kt` | Compatibilidad del borrador, null explícito y restauración de AMRAP desactivado. |
| `T/screens/workout/Cal1TrainingMaxImprovementTest.kt` | Mejora de sesión independiente del TM y basada en 1RM. |
| `T/screens/workout/PlannedIntensityPolicyTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/screens/workout/WorkoutPlannedSetRulesTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/screens/workout/WorkoutReplacementRulesTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `T/screens/workout/WorkoutSetRecorderDurableRoomTest.kt` | Persistencia/reapertura reales de propósito, mínimo, carga, parciales, sin mínimo y opt-out editado. |
| `T/screens/workout/components/SetExecutionCardEffortTest.kt` | Adapta fixtures y aserciones de series al propósito y mínimo explícitos; conserva la intención de la regresión existente. |
| `M/domain/exercises/ExerciseIdentity.kt` | Al cambiar de configuración, limpia también el TM para no heredar la referencia de otro ejercicio. |
| `T/domain/exercises/ExerciseIdentityTest.kt` | Comprueba que el reemplazo borra tanto 1RM como TM. |
| `M/screens/workout/WorkoutVoiceSetMutations.kt` | Las correcciones de voz actualizan la carga original y el resultado del mínimo efectivo. |
| `M/screens/workout/CompletedSetPatch.kt` | Editar reps o parciales refresca déficit y mínimo no alcanzado con las reps completas. |
| `T/screens/workout/CompletedSetPatchTest.kt` | Regresiones de mínimo 4 al corregir a 5 reps y de cargas original/normalizada tras correcciones sucesivas. |
| `docs/screens-v2/reports/CAL1.md` | Informe del paquete, decisiones, comandos, resultados, riesgos y límites para los siguientes paquetes. |

Decisiones de compatibilidad:

- Un propósito nuevo explícito prevalece sobre los indicadores antiguos, incluido `NONE`. Sin propósito, `isCalibrator=true` tiene prioridad; `isAmrap=true` o intensidad AMRAP migran a AMRAP. El modo AMRAP del ejercicio antiguo solo completa series sin propósito explícito. Se ignora `Exercise.isCalibratorAmrap`, incluso en JSON estricto.
- Un `amrapPerformed=false` presente en un registro JSON antiguo es evidencia de desactivación y se convierte en NONE. Un indicador ausente mantiene el carácter legacy. Los mínimos, ordinales y payloads registrados también impiden tratar un registro moderno como carente de evidencia.
- Las escrituras nuevas no emiten `isAmrap` ni `isCalibrator`. Se conserva `amrapPerformed` como espejo de compatibilidad en registros y payloads; el propósito realizado tiene prioridad. El token de intensidad AMRAP sigue siendo legible y utilizable para el reporte de esfuerzo, pero no activa por sí solo el propósito.
- `amrapMinimumReps` es el único mínimo. En datos antiguos se toma del mínimo del rango o del objetivo fijo positivo. El JSON nuevo escribe también el `null` explícito, con significado «sin mínimo». Los borradores distinguen ese null de un mínimo ausente antiguo. Cero y negativos bloquean el guardado del plan, incluso sin reglas rígidas.
- `calibrationScope` solo corresponde a CALIBRATOR y su valor predeterminado es SESSION. NEXT_SESSIONS se conserva en serialización y copias.
- Si falta la nueva clave de TM y hay series PERCENT_TM, el antiguo `reference1RM` se mueve a `referenceTrainingMax`; `reference1RM` queda vacío. Una nueva clave de TM explícita, incluso null, impide repetir la conversión. No se deduce un 1RM a partir del TM. El diálogo permite editar ambas referencias por separado.
- Estimador `CAPACITY_ESTIMATOR_VERSION=2`: elimina el bonus AMRAP del híbrido y de Brzycki. La capacidad usa reps completas; el trabajo parcial sigue disponible para fatiga. Los resultados nuevos guardan versión 2; los antiguos sin versión conservan versión 1. La migración no reescribe evaluaciones históricas. Al corregir un registro se actualizan carga, reps y deuda, conservando los RM, PR y puntuaciones archivados. Las consultas de capacidad recalculan desde la serie y se descartan las sugerencias AMRAP v1 que podían contener el bonus.
- La clave registrada distingue configuración, equipo/contexto de rendimiento, setup, lateralidad y convención de carga; sus componentes se codifican por longitud. El resultado conserva además su clave de rendimiento previa, sin cambiar las identidades de sus cachés.
- Las notaciones numéricas «+» marcan la última serie y conservan su mínimo y nota. AMRAP sin cifra conserva «sin mínimo» en todas sus series; los anclajes numéricos del borrador no crean objetivos ficticios en el plan guardado.
- Room permanece en v29: cambian objetos JSON contenidos en columnas existentes, no tablas ni columnas SQL. La lectura del JSON de Room y la persistencia/reapertura real del registro tienen cobertura. No corresponde una migración SQL v29→v30 en CAL1.

Riesgos y límites:

- No se garantiza que una aplicación anterior entienda el nuevo JSON: la compatibilidad garantizada es la lectura de datos antiguos con la aplicación actual.
- Las estadísticas EWMA y los indicadores de récord antiguos mantienen su evaluación original. No hay recálculo masivo ni borrado de esas cachés; la capacidad consultada sí usa v2. CAL2 deberá elegir referencias de capacidad coherentes con la versión y el contexto.
- La compilación de androidTest no sustituye una ejecución instrumentada. No se utiliza teléfono ni adb por instrucción del encargo.

Para CAL2 quedan el motor puro de elegibilidad/capacidad/referencia/factor/cargas, la política de alcance y la sustitución del mensaje legado que compara volumen con EWMA. No hay propagación a otros ejercicios. Para CAL3 quedan Room v30 y calibration_decisions, panel de resultado en vivo, Aplicar/Conservar/Revertir idempotentes, recuperación tras reinicio y referencia aceptada para sesiones posteriores. El foco nuevo del editor corresponde a CAL4; las reglas de autor y el TM por ejercicio de CAL5/CAL5b tampoco se implementan aquí.

Validación del encargo:

| Caso | Cobertura |
|---|---|
| Receta RM con última AMRAP al editar/guardar | SessionEditorAmrapTest: receta real 5/3/1 y receta mixta con rango 4–6. |
| 5 reps con mínimo 4 | Registro durable en Room y Cal1AmrapProgressionTest; las parciales no completan el mínimo. |
| AMRAP desactivado | Propósito NONE, JSON antiguo con false explícito, payload previo, ordinales, restauración y edición del registro. |
| JSON antiguo y flag muerto | SetPurposeContractTest y SessionSerializationTest. |
| Importación | Cal1AmrapImportTest y regresión filtrada del paquete importer. |
| Separación de TM/1RM | Migración de lectura, Cal1CapacityContractTest, Cal1TrainingMaxImprovementTest y AuthoredTmLoadConsumerContractTest. |
| Capacidad común sin bonus | Cal1CapacityContractTest: historial, PR, editor, homologación, sugerencias, parciales y versión antigua. |
| Migración SQL | No aplica: esquema Room v29 sin modificaciones; sí se prueba el JSON contenido en Room. |

Comando común ejecutado desde C:\kw\u11, siempre mediante el wrapper en primer plano. La selección es por clases/paquete del área; no se ejecuta la batería completa:

```bash
bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native \
  "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" \
  :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin \
  :app:testBaseDebugUnitTest \
  --tests '*SetPurposeContractTest' \
  --tests '*Cal1*' \
  --tests '*SessionSerializationTest' \
  --tests '*PlanMaterializerTest' \
  --tests '*NativeWorkoutProgressionRuntimeTest' \
  --tests '*ProgramAutoregulationEngineTest' \
  --tests '*ProgramProgressCycleCloseTest' \
  --tests '*TopSetProgressionTest' \
  --tests '*BlockTransitionEngineTest' \
  --tests 'com.example.kpkn.domain.training.importer.*' \
  --tests '*SessionEditorAmrapTest' \
  --tests '*SessionEditorRulesEngineTest' \
  --tests '*SessionEditorRulesEngineValidationTest' \
  --tests '*SessionEditorRulesRm*Test' \
  --tests '*SessionEditorBasicsPreservationTest' \
  --tests '*SessionEditorSessionHelpersTest' \
  --tests '*SessionEditorSharedPerformanceTest' \
  --tests '*SessionEditorViewModelSetsTest' \
  --tests '*SessionEditorViewModelRulesTest' \
  --tests '*SessionEditorRmLoad*Test' \
  --tests '*AuthoredTmLoadConsumerContractTest' \
  --tests '*InlineSetRowStateTest' \
  --tests '*ExerciseSetListComposeTest' \
  --tests '*PlannedIntensityPolicyTest' \
  --tests '*WorkoutPlannedSetRulesTest' \
  --tests '*WorkoutReplacementRulesTest' \
  --tests '*WorkoutSetRecorderDurableRoomTest' \
  --tests '*SetExecutionCardEffortTest' \
  --tests '*WorkoutPerformanceHomologationEngineTest' \
  --tests '*LoadSuggestionEngineTest' \
  --tests '*SmartLoadModeSuggestionTest' \
  --tests '*WorkoutLoadSuggestionIncrementalTest' \
  --tests '*SetPropagationRulesTest' \
  --tests '*PlanInfoModelTest' \
  --tests '*HomeSessionHeroDoseTest' \
  --tests '*ProgramDetailViewModelTest*amrap*'
```

Resultados de las comprobaciones:

| Comprobación | Resultado |
|---|---|
| Compilación inicial de producción | Correcta, BUILD SUCCESSFUL en 7 min 6 s. |
| Selección principal antes de la lectura adicional de false explícito | 775 pruebas, 66 clases, cero fallos/errores/omitidas; BUILD SUCCESSFUL en 15 min 10 s. |
| Selección principal con la lectura de false explícito | Producción, androidTest y 777 pruebas correctos; 66 clases, cero fallos/errores/omitidas; BUILD SUCCESSFUL en 10 min 9 s. |
| Regresión de identidad/reemplazo con limpieza de TM | Producción, androidTest y 55 pruebas correctos; 4 clases, sin fallos/errores/omitidas; BUILD SUCCESSFUL en 9 min 22 s. |
| Importador final, con + en carga como caso negativo | 280 pruebas, 24 clases, cero fallos/errores/omitidas; BUILD SUCCESSFUL en 9 min 21 s. |
| Correcciones de registros, voz y persistencia | Producción, androidTest y 116 pruebas correctos; 5 clases, cero fallos/errores/omitidas; BUILD SUCCESSFUL en 10 min 29 s. |

Hubo dos iteraciones intermedias con errores de compilación de tests: referencias antiguas del fixture/import y una prueba incorporada después de empezar a compilar producción. Se corrigieron y la selección principal posterior pasó completa. No fueron fallos de ejecución de tests.

La comprobación de identidad y reemplazo usa:

```bash
bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native \
  "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" \
  :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin \
  :app:testBaseDebugUnitTest \
  --tests '*ExerciseIdentityTest' \
  --tests '*SessionEditorReplaceIdentityTest' \
  --tests '*WorkoutReplacementRulesTest' \
  --tests '*NativeWorkoutProgressionRuntimeTest'
```

La comprobación final del importador usa:

```bash
bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native \
  "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" \
  :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin \
  :app:testBaseDebugUnitTest \
  --tests 'com.example.kpkn.domain.training.importer.*'
```

La comprobación final de correcciones de registros, voz y persistencia usa:

```bash
bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native \
  "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" \
  :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin \
  :app:testBaseDebugUnitTest \
  --tests '*CompletedSetPatchTest' \
  --tests '*WorkoutVoiceSetMutationsTest' \
  --tests '*WorkoutVoiceSetMutationRoomTest' \
  --tests '*WorkoutVoiceMutationFeedbackTest' \
  --tests '*WorkoutSetRecorderDurableRoomTest'
```

Las selecciones se solapan; sus cantidades no se suman como pruebas únicas. Las esperas del wrapper por una ranura no se incluyen en los tiempos de BUILD SUCCESSFUL.

Los logs y resúmenes locales de validación quedan en android-native/app/build/cal1-*. No se incluyen en los commits.

Commits por tema en feat/screens-v2:

| Commit | Asunto |
|---|---|
| `d3b4568a4` | feat(cal1): define el contrato compatible de series AMRAP |
| `af6e6e546` | fix(cal1): unifica capacidad, TM y consumidores del dominio |
| `22218feb0` | fix(editor): conserva AMRAP al editar y guardar las sesiones |
| `89499d778` | fix(entreno): conserva propósito, mínimo y contexto al registrar |
| `dcaf54815` | fix(importador): materializa AMRAP y su mínimo desde la notación |

Todos incluyen `Co-Authored-By: Codex <noreply@openai.com>`. El informe se registra en un commit documental separado. No se hace push. Se conservan los archivos ajenos ya presentes; artifacts/ y los documentos de auditoría no se modifican.
