# K2-finish-fix: diálogo «The coroutine scope left the composition» al terminar el entreno

Estado: DONE (causa real encontrada en dispositivo; la hipótesis inicial del coordinador era solo parcial).

## Causa (comprobada en emulator-5582, usuario 10)
- La hipótesis «WorkoutFinishController:540/:593 + scope de composición» NO era la causa del diálogo. `WorkoutFinishController` ya corre en `viewModelScope`, y el texto de la captura (`DEF-01/02.png`: texto negro sobre el entreno + botón «Cerrar») es la rama `FinishAugePreviewState.Error` de `WorkoutScreen.kt` (el `Text(reason)` + `OutlinedButton("Cerrar")` del cálculo del resumen de recuperación), no `finishWarning` (que sale como Toast).
- El cálculo del resumen vive en un `produceState` de la composición. Sus claves incluían `completedExercisesForSummary`, `duration`, `settings`, `completionIso` y el hash. En dispositivo (APK previo, `K2-assemble-1`) el productor se REINICIABA cada ~1 s mientras la hoja estaba abierta: 121 eventos `finish_auto_preview` seguidos con el MISMO `finishOperationId`/`completionInstantIso`/`inputHash` (log `app-233403.jsonl`), la hoja parpadeaba entre «Calculando recuperación muscular…» y «RESUMEN DE ENTRENAMIENTO» y GC/JIT continuos de `WorkoutScreen` en logcat. Cada reinicio cancela el productor anterior con `LeftCompositionCancellationException` («The coroutine scope left the composition»); el `runCatching` que envolvía el cálculo tragaba esa `CancellationException` y escribía `Error(error.message)` por encima del resultado, y esa cadena en inglés es la que se veía. Por eso fallaba «al instante y siempre» mientras la hoja estuviera abierta (con el cálculo más lento que el reinicio).
- Qué clave exacta se reinicia no se aisló (la identidad del snapshot era estable); no hace falta saberlo: el arreglo quita esas claves.

## Cambios
1. `WorkoutScreen.kt` (preview del resumen):
   - Claves del `produceState` = `finishSnapshot?.finishOperationId`, `finishSnapshot?.completedSetInputHash` (y la lista solo si no hay snapshot). Lista, duración, ajustes y hora congelada se leen con `rememberUpdatedState` al empezar el cálculo. Un cálculo por operación de cierre.
   - `CancellationException` se relanza (nunca escribe `Error`); antes de relanzar deja el evento `finish_preview_cancelled`. Otros errores → evento `finish_preview_failed` + texto en español `FINISH_PREVIEW_FAILED_MESSAGE` («No se pudo calcular el estado muscular. Cierra e inténtalo de nuevo.»). El aviso de hash distinto también quedó en español sin «preview».
   - Al (re)iniciar el cálculo se pone `Loading` para no dejar a la vista un resultado de otras claves.
2. `WorkoutFinishController.kt` (lo que pidió el coordinador, sin romper F-07 ni Room-first):
   - `catch (Exception)`: una `CancellationException` ANTES del commit libera `isFinishingWorkout`, no muestra aviso, registra `session_finish_cancelled` y se relanza; DESPUÉS del commit conserva la recuperación existente (éxito publicado, `onComplete`) y relanza. `onFailure` solo se llama en fallos reales (antes ya se evitaba para cancelación).
   - `finishWarning` ya no usa `error.message`: `finishFailureMessage(error)` devuelve el texto en español conocido (los `check` de `finalizeWorkout`) o `FINISH_SAVE_FAILED_MESSAGE` («No se pudo guardar la sesión. Tu entreno sigue en curso: inténtalo de nuevo.»). El texto técnico solo va al diagnóstico (`session_finish_failed`).
   - `finalizeWorkout` sigue en `NonCancellable` (sin cambios): salir de la pantalla durante el commit no pierde el guardado.
3. Prueba nueva `WorkoutFinishCancellationTest` (4): cancelación antes del commit (candado liberado, sin aviso, sin `onFailure`, sesión sigue en curso, y el siguiente `finish` guarda); tres cancelaciones seguidas y luego guardado; fallo inesperado con el texto de Compose → mensaje en español y reintento OK; `finishFailureMessage`.

## Archivos (relativos al repo)
- modificado `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt`
- modificado `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutFinishController.kt`
- creado `android-native/app/src/test/java/com/example/kpkn/screens/workout/WorkoutFinishCancellationTest.kt`
- evidencia: `artifacts/consolidation-20261001/device-evidence/closeout/k2/` (capturas, `logcat-k2.txt`)

## Gradle
- `K2-compile-1.log`: compile main + tests, BUILD SUCCESSFUL (5 min 1 s).
- `K2-tests-1.log`: 11 clases, 48 pruebas, 0 fallos (WorkoutFinishCancellationTest 4, WorkoutFinishCalibrationTest 4, WorkoutFinishControllerPostCommitTest 2, WorkoutFinishControllerTimeoutTest 1, WorkoutFinishEmptySessionTest 3, WorkoutFinishSheetEntryTest 1, WorkoutFinishVolumeAdvanceDurableTest 1, WorkoutSnapshotCommitTest 7, ProgramRepositoryFinalizeWorkoutTest 11, y de K: WorkoutPendingSeriesTest 9, WorkoutRecordFabPlacementTest 5). Esa corrida precede al cambio de claves del `produceState` en `WorkoutScreen.kt` (solo UI; compila en `K2-assemble-2`).
- `K2-assemble-1.log` (assembleBaseDebug + androidTest) y `K2-assemble-2.log` (assembleBaseDebug tras el cambio de claves): BUILD SUCCESSFUL. El androidTest APK no se reconstruyó en la 2ª (no se usa).

## Dispositivo (emulator-5582, AVD KPKNWizchatQA, usuario 10, modo avión; sin `--allow-stale`; nada entre 20:55 y 21:05)
- APK previo `a5e22a73…` (23:29Z): reproduce el reinicio continuo (121 cálculos, parpadeo «Calculando…» ⇄ resumen). 
- APK final `bbf4a64a…` instalado con `install_apks.py --flavor base --serial emulator-5582 --user 10 --no-test-apk` (hash origen == instalado), mitigación s20 aplicada (`compile -m verify` + `am kill-all`):
  - Hoja de resumen retomada: 1 solo `finish_auto_preview` y hoja estable (4 comprobaciones en 12 s), 0 `finish_preview_cancelled/failed`, mientras `snapshot_published` sigue cada ~1 s.
  - «Terminar hasta acá» tres veces seguidas en el mismo proceso, con «Volver» entre ellas: las 3 muestran «RESUMEN DE ENTRENAMIENTO», sin texto «coroutine/composition» (`k2/t2-terminar-1..3.png`).
  - «Guardar y terminar entrenamiento»: vuelve a Home sin diálogo; Room: `workout_logs` 5→6, `ongoing_workout` 0.
  - Segundo entreno (Día 2, 1 serie): «Terminar hasta acá» → «Guardar y terminar» y a los 0,7 s un deep link a Ajustes (salir de la pantalla con el guardado en curso): sin diálogo; Room `workout_logs` 7, `ongoing_workout` 0 (el commit no se pierde).
  - `logcat-scan` (31 831 líneas): 0 FATAL, 0 ANR de kpkn, 0 IllegalState de kpkn, 0 SQLite, 0 «left the composition», 0 CancellationException de kpkn (221 StrictMode informativas).
- No reproduje el caso «proceso con la cola de cancelaciones»: no hace falta, la causa (reinicio continuo) desaparece.
- Cierre del entorno: `screen_off_timeout` del usuario 10 devuelto a 60000 (lo había subido a 1 800 000 para trabajar), usuario 0 en primer plano (`switch-user 0`), modo avión = 1, AVD apagado con `avd.py stop --yes`. Datos nuevos en QA10: 2 logs de prueba (Día 1 y Día 2 del programa «Fuerza y músculo KPKN»).

## Riesgos / pendientes
- El cálculo del resumen ya no se actualiza si cambian `settings` con la hoja abierta (usa el valor al empezar); es lo previsto (snapshot congelado).
- Si el VM se limpia ANTES del commit, el guardado se cancela de forma limpia y la sesión queda en curso (reanudable); tras el commit sigue la recuperación ya existente.
- La causa del churn de claves original (qué objeto cambiaba cada ~1 s) no se aisló; conviene vigilar en dispositivo los eventos `finish_preview_cancelled` (no deberían aparecer más de una vez por cierre).
- Sin prueba JVM del `produceState` (es composición); queda cubierto por el recorrido en dispositivo de arriba.
