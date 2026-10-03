# P1 · Detalle del programa (D2.3 + D2.8 + D2.9 + H-UI) — nota de entrega

Estado: **DONE** (pruebas dirigidas en verde; sin revisión visual en teléfono, queda para H-VERIF).

## Ítems
| Ítem | Estado | Qué se hizo |
|---|---|---|
| D2.3 | Cerrado | `applyProgramTemplate`: copia recuperable DESPUÉS de calcular la plantilla y dentro de `runCatching`; sin almacén o con `commit()` fallido no se reemplaza nada y sale el snackbar existente; `CancellationException` se relanza; guarda de reentrada (`templateApplyJob`, compartida con `protocolApplyJob`). |
| D2.8 | Cerrado (parte obligatoria) | Guardia de sesión en curso con el mismo mensaje que protocolo (`SESSION_IN_PROGRESS_MESSAGE`), al inicio y de nuevo justo antes de escribir. **No hecho**: la mejora opcional de enrutar overwrite=true por `replaceProgramSafely` (requiere comprobar `REPLACE_ALL` runState=null [I]; el spec no la pedía). |
| D2.9 | Cerrado | `_feedbacks` privado + `feedbacks` con `asStateFlow()`; `loadFeedbacks` relanza cancelación y registra el error con `Log.w`. |
| H-UI | Cerrado, incluida la insignia opcional | Ver abajo. |

### H-UI
- Nombre del ejercicio resuelto al dibujar (programa → catálogo → id legible); sin cambio de modelo ni Room. Unilaterales indican el lado.
- Tarjeta: título en negrita + texto llano, p. ej. «Lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 20 a 22 kg por mancuerna.» Sin material declarado: «…elige una carga un poco mayor…». Asistencia: «bajar la asistencia…». Variantes: «pasar a «Flexión», una variante más difícil». Coma decimal (62,5).
- Línea del identificador eliminada. Cabecera «N progresiones por revisar».
- Confirmación al responder (snackbar con nombre: «Listo: … subirá a 22 kg por mancuerna…», «Propuesta rechazada…», «No se aplicó la propuesta de …» si caducó). Doble toque no repite el aviso.
- Avisos informativos: caducan a los 7 días, máximo 3 visibles, botón «ENTENDIDO» (apaga `userFacingNotice`; el registro se conserva).
- Insignia «N progresiones por revisar · VER» en las pestañas Estructura/Volumen (lleva a Semana).
- Propuestas guardadas con el texto antiguo (empiezan «Dos exposiciones completas») se reescriben al dibujar.
- Snackbar: «No se pudo…» y «Termina o descarta…» rojo; «No se aplicó…» sugerencia (`snackbarTypeFor`).
- testTags para H-VERIF: `program_proposals_card`, `native_progression_header`, `native_progression_item_<id>`, `native_progression_apply_<id>`, `native_progression_reject_<id>`, `native_progression_notice_<id>`, `native_progression_badge`.

## Archivos (ruta relativa al repo `C:\Users\valen\Documents\KPKNFit`)
Modificados:
- android-native/app/src/main/java/com/example/kpkn/screens/programdetail/ProgramDetailViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/programdetail/ProgramDetailScreen.kt
- android-native/app/src/main/java/com/example/kpkn/domain/training/NativeWorkoutProgressionRuntime.kt
- android-native/app/src/test/java/com/example/kpkn/screens/programdetail/ProgramDetailViewModelTest.kt
- android-native/app/src/test/java/com/example/kpkn/domain/training/NativeWorkoutProgressionRuntimeTest.kt
- android-native/app/src/test/java/com/example/kpkn/domain/training/NativeProgressionEquipmentAndVariantTest.kt
- android-native/app/src/test/java/com/example/kpkn/domain/training/NativeProgressionRealPlanTest.kt (aserto negativo ahora usa `NativeProgressionText.noFutureSessionNotice()`)

Creados:
- android-native/app/src/main/java/com/example/kpkn/domain/training/NativeProgressionText.kt (redacción pura, sin nombres)
- android-native/app/src/main/java/com/example/kpkn/screens/programdetail/NativeProgressionCardModel.kt (nombres, avisos, confirmaciones)
- android-native/app/src/test/java/com/example/kpkn/screens/programdetail/CommitFailingPreferences.kt
- android-native/app/src/test/java/com/example/kpkn/screens/programdetail/NativeProgressionCardModelTest.kt
- android-native/app/src/test/java/com/example/kpkn/domain/training/NativeProgressionTextTest.kt

Sin cambios en `ProgramScheduleModels.kt`, Room (v28) ni versiones.

## Regiones tocadas en NativeWorkoutProgressionRuntime.kt (para P2)
- Import `catalogConfigurationDisplayName` y parámetro nuevo `displayNameOf` (con valor por defecto) en `observeCompletedWorkout`.
- Dentro de `observeCompletedWorkout`: SOLO textos: aviso «sin sesión futura»; locales `unit`/`assistance` en la rama LOAD; los `explanation`/`reason` de INCREASE/REDUCE/variantes/avisos de tope ahora llaman a `NativeProgressionText.*`. No cambié ninguna regla de cuándo se propone.
- `resolveProposal`: reescritas 5 cadenas de `expire(...)` (sin «slot»/«identidad»); lógica intacta.
- `exposuresForLog`: la construcción de `Exposure` añade `topReps` y `minReps`; `Exposure` tiene esos 2 campos opcionales.
- Fondo del archivo: eliminadas `manualLoadAdjustment` y `quantityLabel`; `formatKg` delega en `NativeProgressionText.formatKg` (coma decimal).
- Para P2: `NativeProgressionCardModel.exerciseName` solo usa `identity.configurationId` y `identity.side`, así que quitar `recipeDayId/recipeSlotId` de la identidad no lo rompe, pero estas pruebas construyen `NativeProgressionIdentity(recipeDayId=…, recipeSlotId=…)` y habrá que actualizarlas: `ProgramDetailViewModelTest` (`nativeProposalProgram`), `NativeProgressionCardModelTest` (`identity()`).

## Pruebas
Nuevas: `NativeProgressionTextTest` (11), `NativeProgressionCardModelTest` (13); en `ProgramDetailViewModelTest`: commit fallido con plantilla, commit fallido con protocolo (los 2 con SharedPreferences falso), sin almacén, camino feliz con copia, sesión en curso, doble toque, `feedbacks` solo lectura, tarjeta con nombre, rechazo con confirmación, aceptar caducado + «Entendido», `snackbarTypeFor`; en `NativeWorkoutProgressionRuntimeTest`: texto de subida/bajada/variante y motivo de caducidad sin jerga; en `NativeProgressionEquipmentAndVariantTest`: texto con unidad real por equipo.

Clases a ejecutar: `ProgramDetailViewModelTest`, `ProgramDetailOptionalConfirmationTest`, `NativeProgressionTextTest`, `NativeProgressionCardModelTest`, `NativeWorkoutProgressionRuntimeTest`, `NativeProgressionEquipmentAndVariantTest`, `NativeProgressionRealPlanTest`, `ProgramRepositoryConsolidationTest`, `ProgramProgressCycleCloseTest`.

## Gradle
- `P1-compile-1` (compileBaseDebugKotlin + UnitTestKotlin): lo cancelé en cola porque la compilación de W1 (`W1-compile-1.log`, BUILD SUCCESSFUL, 9 m 2 s) ya compiló main y tests con mi código.
- `P1-tests-1` (las 9 clases anteriores, `-Exclude *.SessionTemplateRepositoryTest`): exitCode 0, 47 s; ProgramRepositoryConsolidationTest 12, EquipmentAndVariant 10, RealPlan 6, TextTest 11, RuntimeTest 32, CycleClose 6, CardModelTest 13, OptionalConfirmation 6, ProgramDetailViewModelTest 58: **0 fallos**.

## Riesgos abiertos / decisiones
- Diseño visual (título + texto + botones, insignia) no visto en pantalla: verificar en H-VERIF.
- Con plantilla «Reemplazar todo» sin almacén conectado ahora se rechaza; con protocolo se mantiene el comportamiento anterior (una prueba existente depende de ello; en la app el almacén siempre se conecta antes).
- Guarda de reentrada compartida plantilla/protocolo: un segundo toque durante el cálculo se ignora en silencio.
- D2.4 (W2) moverá `ProgramSnapshotStore` a una interfaz: `CommitFailingContext` depende del constructor `ProgramSnapshotStore(Context)` y de `SharedPreferences.commit()`.
- El título del ejercicio se resuelve por `configurationId` (primer ejercicio del programa con esa configuración).
