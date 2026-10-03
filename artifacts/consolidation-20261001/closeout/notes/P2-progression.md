# P2 · Progresión §12.4 — nota de entrega

Estado: **DONE** (código y pruebas JVM en verde; la pasada de emulador queda para quien coordina, pasos abajo).

## Ítems
| Ítem | Estado | Qué se hizo |
|---|---|---|
| H-IDENT | Cerrado | La identidad ya no incluye día ni posición. `NativeProgressionIdentity.recipeDayId/recipeSlotId` quedan con valor por defecto `""` (JSON antiguo sigue leyéndose, sin migración). El destino se decide por configuración + ejecución + lado + unidad + modo de carga + convención + **propósito del slot** (índice `slotIntents` de la receta; los slots SPEED nunca son evidencia ni destino). Dos slots del mismo ejercicio en un entreno cuentan como UNA exposición (`mergeSameLog`, gana el caso más desfavorable). La primera carga elegida a mano en un día se conserva en los demás días. Cambios de variante: alcanzan a todos los slots con esa configuración y propósito; `NativeProgressionResolution` gana `targetConfigurationId` (opcional) para arrastrar la variante al ciclo siguiente. |
| H-DESCARGA | Cerrado | Reproducido por lectura [C]: `resolveProposal` y `hasFuturePrescription` no filtraban descarga. Ahora la semana de descarga (`executionKind` o receta) se excluye de los destinos y de «hay sesión futura»; si solo queda la descarga, no hay propuesta y sale el aviso «Ya no quedan sesiones sin entrenar…». Pruebas nuevas. Efecto esperado: `NativeProgressionRealPlanTest` ahora afirma que la semana 6 conserva 60 kg. |
| R16 (solo PHUL) | Cerrado | `phulOriginal.nativeProgression = REP_RANGE_THEN_LOAD, 2 exposiciones`, nota «Recomendación KPKN §12.4…»; `phulAdapted` lo hereda por `copy`. PHAT sin cambios. La sugerencia genérica +2,5 % ya no actúa en F/H/I de PHUL porque esos slots quedan `nativeProgressionManaged` (`LoadSuggestionEngine.shouldDeferToNativeProgression`); no hizo falta tocar ese archivo. Series/reps/descansos intactos (prueba de oráculos 18/16/21/18). Cierre de ciclo: «Continuación de N semanas» usa las semanas reales (12 en PHUL). |
| H-CICLO | Cerrado | Las propuestas que caducan al cerrar el ciclo se guardan con `userFacingNotice=false`; se añade UN aviso `NOTICE` (`native-cycle-c<N>`): «Empiezas un nuevo bloque de N semanas con tus últimas cargas.» (+ «Las propuestas sin responder caducaron.» si las hubo). Idempotente; sin pantalla de oferta. Nuevo valor `NativeProgressionResolutionStatus.NOTICE`. La tarjeta lo titula «Nuevo bloque». |
| H-BW | Cerrado | Escalera de flexión: rodillas → estándar → pies elevados (y peldaño inverso). Pies elevados exige `supportEquipment` con `support`; sin él no se propone y se avisa («necesita un apoyo estable…»). Sentadilla/zancada/puente fuera. |
| H-VERIF (código) | Cerrado | (a) Confirmado por lectura [C]: `SetExecutionCard` rellena la reserva con lo planificado (LaunchedEffects de ~l.1620-1640) y `allSetsAtTop` la tomaba como cumplida. Ahora `SetAdvancedFeedback.intensityAdjusted` → `RecordedSetPayload.intensityAdjusted` (por defecto `true`; `false` solo si el atleta no movió el selector, ni fallo ni AMRAP) y `actualRirOf` devuelve null en ese caso (sin dato: ni éxito ni fallo; un registro corto sigue proponiendo bajar). El dato RIR se sigue guardando; el resto de consumidores no cambia. (b) androidTest `SetExecutionCardManagedLoadUiTest` (compila; ejecutar en emulador). Nota: el texto real usa guion largo «6–8». (c) Pasos de emulador abajo. |

## Archivos (rutas relativas al repo)
Modificados: android-native/app/src/main/java/com/example/kpkn/ — `data/models/ProgramScheduleModels.kt`, `data/models/WorkoutV2Models.kt`, `data/protocols/definitions/AuthoredPhulPhat.kt`, `domain/training/NativeWorkoutProgressionRuntime.kt`, `domain/training/NativeProgressionText.kt`, `domain/training/ProgramProgressEngine.kt` (solo el texto de la continuación), `screens/programdetail/NativeProgressionCardModel.kt`, `screens/workout/WorkoutFeedbackModels.kt`, `screens/workout/WorkoutSetRecorder.kt`, `screens/workout/components/SetExecutionCard.kt` (3 ediciones pequeñas: estado `intensityTouched`, marca al mover el selector, campo en `SetAdvancedFeedback`).
Tests modificados: `ProgramDetailViewModelTest`, `NativeProgressionCardModelTest`, `NativeWorkoutProgressionRuntimeTest` (helpers `dayNumber`, `extraDays`, `withDuplicateSlot`, `rirAdjusted` + tests nuevos), `NativeProgressionRealPlanTest`, `ProgramProgressCycleCloseTest`.
Tests creados: `src/test/.../domain/onboarding/PhulNativeProgressionTest.kt`; `src/androidTest/.../screens/workout/components/SetExecutionCardManagedLoadUiTest.kt`.
No se tocó `SimpleCyclePersonalizer.kt` (los ids de día no hacía falta cambiarlos), `PlanAdaptationResolver.kt` ni `LoadSuggestionEngine.kt`. Nada movido ni borrado. Room sigue en v28.

## Propuestas guardadas / concurrencia
- Compatibilidad [C]: la función no se ha publicado; JSON viejo con día/slot se lee (valores por defecto) y una propuesta vieja se resuelve igual (el propósito se valida contra la receta). Solo pierde la deduplicación frente a propuestas nuevas.
- No se tocaron los candados de `ProgramRepository`; el runtime sigue siendo función pura. `ProgramRepositoryConsolidationTest` (incluye las pruebas de concurrencia F-07) pasó entero.

## Gradle
- `P2-compile-1` (main + unit tests + androidTest): exitCode 0.
- `P2-tests-1` (`*Progression*`, `*ProgramProgress*`, `ProgramRepositoryConsolidationTest`, `*AuthoredPlan*`, `*PlanAdaptation*`, `*Phul*`, `*Phat*`, `*LoadSuggestion*`, `ProgramDetailViewModelTest`, `NativeProgressionCardModelTest`, `*AuthoredRecipe*`, `ProtocolRecipeFidelityTest`; 28 clases): 1 fallo, `NativeProgressionRealPlanTest.cycleTwo_exposures_applyToCycleTwoSessions_andPendingProposalsExpireAtCycleClose` («semana 6 …: la propuesta llega a las sesiones sin entrenar del ciclo 2»): era el comportamiento que H-DESCARGA elimina a propósito (la semana 6 es descarga). Aserción actualizada: semanas 2-5 = 62,5 kg y semana 6 = 60 kg.
- `P2-tests-2` (reintento de esa clase): exitCode 0, 6/6.

## Pasos exactos de la pasada de emulador (H-VERIF) — los ejecuta quien coordina
1. Instalar `assembleBaseDebug assembleBaseDebugAndroidTest` en emulator-5582 (usuario QA10). Opcional: `connectedBaseDebugAndroidTest` con `SetExecutionCardManagedLoadUiTest`.
2. Sembrar un plan propio KPKN activo (Fuerza o Músculo) con material declarado: en Material añadir «Soportes» (`support`), y mancuernas/discos para ver cargas concretas.
3. Entrenar la sesión A de la semana 1: en un ejercicio gestionado verificar el texto «Elige una carga para 6–8 reps dejando 2 en reserva y registra la serie.», escribir la carga, **mover el selector de reserva** (aunque sea y volver al valor del plan) y completar todas las series en el tope del rango. Repetir en la segunda sesión que contenga el mismo ejercicio (otro día de la semana vale: sube junto).
4. Abrir Detalle del programa → pestaña Semana. Esperado: tarjeta `program_proposals_card` con cabecera `native_progression_header` «1 progresión por revisar» (o N), un `native_progression_item_<id>` con el NOMBRE del ejercicio y el texto «Lo hiciste dos veces con N reps en todas las series. Propuesta: subir de X a Y kg …», sin ids técnicos. En las pestañas Estructura/Volumen debe verse `native_progression_badge`.
5. Tocar `native_progression_apply_<id>` (APLICAR). Esperado: snackbar «Listo: <ejercicio> subirá a Y kg … en tus próximas sesiones.» y la propuesta desaparece.
6. Abrir la siguiente sesión que tenga ese ejercicio (en cualquier día, salvo la semana de descarga): la carga prescrita debe ser Y.
7. Contraprueba de reserva: repetir el paso 3 SIN tocar el selector de reserva en dos sesiones: no debe aparecer propuesta.
8. Contraprueba de rechazo: `native_progression_reject_<id>` → «Propuesta rechazada. <ejercicio> se queda como está.»
9. (Opcional) Aviso: cerrar un ciclo completo y comprobar el aviso único `native_progression_notice_native-cycle-c2` con «ENTENDIDO».

## Riesgos abiertos
- Con la regla de reserva, un atleta que nunca toca el selector no recibe propuestas de subida (decisión 12); conviene que la pasada de emulador confirme que mover y volver al valor planificado cuenta como «ajustado».
- PHUL ahora es plan con progresión nativa: usa ids de semana por ciclo (`inst_c<N>_…`) como los planes propios; las pruebas de materialización/activación de autor pasaron, pero falta verlo en dispositivo. Los PHUL ya activos conservan su receta antigua.
- Una subida nacida cuando solo queda la semana de descarga no se aplica (aviso); la carga se arrastra desde la última exposición real.
- No hay prueba de la UI de la tarjeta en pantalla (solo modelo/ViewModel).
