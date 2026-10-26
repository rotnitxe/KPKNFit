# Estado de la ola — programas y periodización

Integración autorizada el26-oct: el estado histórico de esta ola Room35 se conserva abajo. La rama integrada mantiene Room36; ver INTEGRACION_MASTER_ROOM36.md para la validación del árbol conjunto y la conservación del WIP.

Cierre documentado el 26-oct-2026; validaciones del 25-oct. **Plan aplicado y cierre Android aprobado**: olas A–E y T60–62 terminadas, con exclusiones autorizadas y límites de plataforma documentados. Rama `codex/programs-periodization-current`, base `1d399c1fc`, Room35. Sin push ni integración en master. El checkout principal y el worktree histórico se conservan.

## Implementación y evidencia

- T01–04, T11–18, T50–53: guardadas con pruebas dirigidas en EJECUCION_ACTUAL.md. T10 consume la progresión ya integrada, sin duplicar CAL5/CAL5b; T19 no tiene especificación en el original.
- T20–26 y T90–94: guardadas. La cuarta verificación RIR autorizada pasó 1/1. P9 ampliado 41/41. Android B: evidencia verde por clase a lo largo de tres intentos, sin afirmar corrida global nueva de 228.
- T30–35, T70–74, T82–84: guardadas. Base pura P3 aprobada 87/87 y continuación 93/93. P7: 144/145 primero, única expectativa de versión corregida, FidelityRegression 72/72 segundo. Sustitución/opciones 14/14 y proyector 5/5. Android C: 13 clases/112 aprobados primero; VM 100 + PlanInfo 52 segundo. Evidencia verde por clase; no nueva corrida conjunta.
- T100–103: guardadas; P10 puro 93/93 segundo. T110–117 y T120–124: guardadas; puros de competición/temporada 10/10 y preparación/semanal/Inicio 36 verdes por clase en dos intentos.
- Android D primero 207/212: cinco fallos de finalización sin receta. Se conserva metadata obligatoria para recetas y opcional para programas manuales. Segundo 133/133, cero fallos/errores/omitidos, VM 112 + CompetitionModule 4 + FinalizeWorkout 11 + MissedPolicy 6; 139 hashes sin deriva, rc0. Las otras clases tienen evidencia verde del primer intento.
- Integración compartida T103/115/121:9eda33090. T116 Inicio:7cdd02d75. La transacción/VM/pantalla compartida se guarda junta para mantener coherencia; excepción documentada a un commit por tarea. T117: 6753c7175, T124: 923c57c90, T61: 919ee60d6.
- T40–43 aplicadas: dos ofertas, catálogo plegado, entrada a TRAINING_ONLY sin preselección y protocolo exacto con evento independiente. Nuevas pruebas preparadas de familias, alcance real, interruptor, huella, activación y navegación. Protección de proyección negativa/overflow aplicada. **Android E1 aprobado**, sesión 52978, ocho clases/76 tests, cero fallos/errores/omitidos y 154 hashes sin deriva. El ajuste posterior de sección vacía aprobó EntrenoPlanStepTest20/20 en cierre1 y se guardó58ec7525d.
- iOS: transporte opcional de competición/copy y calibración/notas; pruebas Swift preparadas, NO ejecutadas. No hay compilador Swift ni QA iOS en este host.

## Cierre verificado y límites

1. Cierre2 terminado rc0: 286 clases/3074 tests, cero fallos/errores/omitidos, 159 fuentes sin deriva. Las siete familias y las nueve clases Android exigidas están cubiertas conjuntamente. El arnés JVM literal no compiló dependencias Android; la corrida Android posterior proporciona la evidencia conjunta real.
2. T60/62 completadas; índice, auditoría original y recibo portable en esta carpeta. Los cinco ajustes de fixtures se guardan en36ae90fc7; la sección vacía en58ec7525d.
3. APK Room35 construido y firmado, sin instalarlo sobre Room36. QA física usa una copia aislada de compatibilidad Room36 con master1d5ba0ce7, misma identidad/firma y 181 tests verdes. Su overlay no modifica la rama entregable.
4. KPKN Dev instalada y lanzada en Z Flip por ADB inalámbrico bajo phone.lock. Biblioteca, entrada/salida del asistente, alcance, progreso y temporada observados. Registros originales, sesión en curso, programas y ajustes conservan sus huellas. Queda únicamente el borrador sin activar generado en esta QA; Salir lo conserva y no se alteró la DB para retirarlo. Los recorridos completos de activación/competición no se ejercitaron físicamente.
5. Emuladores cerrados por orden del usuario. Pruebas iOS preparadas, no ejecutadas en Windows; backend revisado como transporte sin nueva suite. Evidencia extensa archivada localmente; `VALIDACION_FINAL.json` conserva el resumen portable.

T104 permanece aplazada por el plan. T80/81 y los enlaces readiness/AugeSnapshotComputer están retirados, no son deuda de implementación. Las demás olas MFP no se acreditan de nuevo desde esta entrega.

## Contratos preservados

RecipeContractInventoryTest mantiene CEILING 100 y CEILING_AFTER_EXEMPTIONS 18. C11 es editorial/advisory, sin rebajar C1–C10 ni agregar exenciones por sello KPKN. Tres plantillas propias largas publican v3; snapshots previos intactos. Room/data/db/esquemas idénticos a la base en la comprobación actual. Gate estático del código final: rc0, 0 active retirement violations y controles de detección aprobados; se preservan ejercicios/material de anillas o TRX. No es QA de hardware. Repetir si hay nuevos cambios que lo justifiquen.

## Clases Android nuevas en esta rama

Inventario de 36 archivos de prueba nuevos frente a la base; las ampliaciones de clases existentes no se incluyen. La evidencia de ejecución se consulta por clase en los recibos; esta lista no acredita por sí sola una corrida.

- com.example.kpkn.data.models.CompetitionModelsJsonTest
- com.example.kpkn.domain.competitions.CompetitionPlanningTest
- com.example.kpkn.domain.season.SeasonAdvisorTest
- com.example.kpkn.domain.training.ProgramExerciseSwapTest
- com.example.kpkn.domain.training.ProgramProgressReportTest
- com.example.kpkn.domain.training.ProgramScopeTest
- com.example.kpkn.domain.training.RpeTopSetEngineTest
- com.example.kpkn.domain.training.block.BlockReviewEngineTest
- com.example.kpkn.domain.training.block.BlockReviewJsonTest
- com.example.kpkn.domain.training.block.BlockReviewLifecycleTest
- com.example.kpkn.domain.training.block.ContinuationProgramFactoryTest
- com.example.kpkn.domain.training.block.NextBlockAdjusterTest
- com.example.kpkn.domain.training.calibration.CalibrationEvidenceTest
- com.example.kpkn.domain.training.calibration.PlanningRangeProposalsTest
- com.example.kpkn.domain.training.calibration.PlanningVolumeTargetsTest
- com.example.kpkn.domain.training.calibration.WeeklyCycleEvidenceTest
- com.example.kpkn.domain.training.cycle.RirWaveTest
- com.example.kpkn.domain.training.cycle.WeeklyAdjustmentEngineTest
- com.example.kpkn.domain.training.cycle.WeeklyCheckinEngineTest
- com.example.kpkn.domain.training.cycle.WeeklyCheckinJsonTest
- com.example.kpkn.domain.training.periodization.BlockVariantRotationTest
- com.example.kpkn.domain.training.planning.PlannedLoadProjectorTest
- com.example.kpkn.domain.training.schedule.InactivityDetectorTest
- com.example.kpkn.domain.training.schedule.MissedSessionJsonTest
- com.example.kpkn.domain.training.schedule.MissedSessionPolicyTest
- com.example.kpkn.screens.programdetail.design.PlanningRangeProposalsSheetTest
- com.example.kpkn.screens.programdetail.design.ProgramBlockReviewSheetTest
- com.example.kpkn.screens.programdetail.design.ProgramCompetitionModuleTest
- com.example.kpkn.screens.programdetail.design.ProgramContinuationSheetTest
- com.example.kpkn.screens.programdetail.design.ProgramExerciseSwapEntryTest
- com.example.kpkn.screens.programdetail.design.ProgramPlannedLoadModuleTest
- com.example.kpkn.screens.programdetail.design.ProgramPlanningVolumeTargetSheetTest
- com.example.kpkn.screens.programdetail.design.ProgramScheduleModuleTest
- com.example.kpkn.screens.programdetail.design.ProgramSwapSheetTest
- com.example.kpkn.screens.programdetail.design.VolumeCalibrationOriginalsTest
- com.example.kpkn.screens.programdetail.design.WeeklyCheckinSheetTest

## Archivos calientes para revisión antes de integrar

- data/models/Program.kt,Settings.kt,PowerliftingProfile.kt,CompetitionModels.kt; recetas/contratos data/protocols y data/programs.
- ProgramProgressEngine,BlockTransitionEngine,PlanMaterializer,cycle,block,calibration,periodization,planning,schedule,competitions,season y ProgramExerciseSwap.
- ProgramRepository,CompetitionRepository,ProgramDetailViewModel,ProgramDetailScreen y módulos/hojas; cadena DayView→ProgramDayModule→ProgramSessionBlock.
- MainActivity,ProgramsScreen/ProgramsViewModel,HomeScreen/HomeSessionSection; wizard SetupWizardModels/VM,SetupGeneratedPlans,SetupActivationPayload,EntrenoPlan/CompetitionStep,PlanModels/PlanCarousel y PostDischargeRouting.
- iOS Settings.swift,CompetitionRecord.swift,helper copy y pruebas de transporte; no port funcional de los motores nuevos acreditado.

No se modificaron NativeWorkoutProgressionRuntime,Calculations,LoadSuggestionEngine,VolumeCalculator,VolumeView ni WorkoutViewModel. En el editor de sesiones sólo la cadena autorizada T02. No se contacta a otros chats sin autorización humana específica; esta lista sirve para revisión previa a fusionar.

Las pruebas, compilación, APK, instalación y QA son evidencias separadas. Los registros originales, nutrición y referencias por configuración conservan su procedencia. El índice de commits está en README.md y los recibos detallados en EJECUCION_ACTUAL.md.
