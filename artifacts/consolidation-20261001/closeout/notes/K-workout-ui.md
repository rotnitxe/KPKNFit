# Nota del paquete K-workout-ui (D2.2 y D2.1)

Estado: DONE (código y pruebas JVM listos; falta solo la pasada de emulador, que hace quien coordina).

## Ítems
- **D2.2 cerrado** (botón «Registrar serie» tapado por el teclado).
- **D2.1 cerrado** (aviso de series pendientes en el resumen, opción B).
- No hubo ítems parciales ni sin hacer.

## Qué se hizo
### D2.2
- Causa confirmada [C]: edge-to-edge (`MainActivity.kt:157`, `windowSoftInputMode=adjustResize` en el manifiesto no reserva espacio con edge-to-edge) + `Scaffold(contentWindowInsets = 0)` + botón anclado a `navigationBarsPadding + dockBottomClearance + 12 dp`. Ningún ancestro consume insets del teclado (comprobado por búsqueda en `MainActivity`, `navigation/`, `ui/adapt`), así que `WindowInsets.ime` devuelve el valor real.
- Nuevo `WorkoutRecordFabPlacement.kt`:
  - `resolveRecordFabBottomOffsetDp(imeBottomDp, navigationBarBottomDp, dockBottomClearanceDp)` (función pura): con teclado visible = altura del teclado + 12 dp (sin sumar barra de navegación ni dock); sin teclado = barra + dock + 12 dp (igual que antes).
  - `WorkoutRecordFabHost` (composable `BoxScope`): lee `WindowInsets.ime` / `navigationBars` DENTRO de este composable (solo se recompone el botón, no el cuerpo del entreno) y anima el margen con `animateDpAsState` (160 ms).
- `WorkoutScreen.kt`: la llamada a `WorkoutRecordFab` pasa a `WorkoutRecordFabHost` (se quitó el `Modifier.align/navigationBarsPadding/padding/zIndex` inline; el host aplica los mismos `align(BottomEnd)`, `end = 16.dp`, `zIndex(12f)`). El import de `WorkoutRecordFab` en `WorkoutScreen.kt` queda sin uso (solo advertencia).
- NO se tocó: editor de sesión, `SetExecutionCard.kt`, `MainActivity.kt`, el workaround `dismiss_ime_if_shown` ni ninguna herramienta.
- El botón NO se oculta con el teclado (sigue disponible para TalkBack / Switch Access).

### D2.1
- `WorkoutStepNavigator.pendingSeriesSteps(state)` (función nueva, solo lectura; `nextSet` y `nextIncompleteStepAfter` intactos): pasos WORKING_SET y CARDIO no hechos, usando el mismo `isWorkoutStepDone` canónico. Excluye por construcción los ejercicios saltados a propósito (`skippedExerciseIds` ya no son visibles) y las series omitidas (`omittedSetKeys` no generan paso). Calentamiento y movilidad no cuentan (no son series). Una serie unilateral con los dos lados pendientes cuenta una vez.
- Nuevo `WorkoutPendingSeries.kt` (puro): `buildPendingSeriesNotice(...)` y el texto:
  - «Te quedan 5 series sin hacer: Press de banca (3), Remo (2).» / «Te queda 1 serie sin hacer: Carrera.»; máximo 3 ejercicios nombrados y luego «y N ejercicios más».
  - Botón: «Seguir con ellas» (si queda 1 serie: «Seguir con ella»).
- `WorkoutViewModel.kt`: `pendingSeriesSteps(state)` (envoltorio) y `continuePendingSeriesFromFinish()`: `hideFinish()` (igual que «Volver») y luego `selectWorkoutStep(<primera serie pendiente>)`.
- `WorkoutFinishHost.kt`: `FinishWorkoutSheet` recibe `pendingSeriesNotice` y `onContinuePendingSeries` (con valores por defecto); tarjeta ámbar `PendingSeriesNoticeCard` bajo el título, `liveRegion = Polite`, `testTag` `finish_pending_series_notice` y `finish_pending_series_continue`. El resumen y el guardado no cambian: quien terminó a propósito puede guardar igual.
- `WorkoutScreen.kt`: calcula el aviso (con `remember`) con el nombre de pantalla (`displayWorkoutExerciseName`) y lo pasa a la hoja.
- Cubre también la corrección del verificador: «Terminar hasta acá» marca como saltados el ejercicio actual y los siguientes, pero los ejercicios ANTERIORES sin hacer ahora aparecen en el aviso (antes se perdían en silencio).

## Archivos tocados (ruta relativa al repo)
- creado: `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutRecordFabPlacement.kt`
- creado: `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutPendingSeries.kt`
- creado: `android-native/app/src/test/java/com/example/kpkn/screens/workout/WorkoutRecordFabPlacementTest.kt`
- creado: `android-native/app/src/test/java/com/example/kpkn/screens/workout/WorkoutPendingSeriesTest.kt`
- modificado: `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt` (CRLF conservado)
- modificado: `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutFinishHost.kt` (CRLF conservado)
- modificado: `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutStepNavigator.kt` (solo se añadió `pendingSeriesSteps`; CRLF conservado)
- modificado: `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt` (2 funciones pequeñas; CRLF conservado)
- Nada movido ni borrado.

## Pruebas
- `WorkoutRecordFabPlacementTest` (5): sin teclado conserva el anclaje; con teclado = teclado + 12 dp sin sumar barra ni dock; siempre sobre el teclado; sigue la altura mientras se anima; inset <= 0 cuenta como teclado oculto.
- `WorkoutPendingSeriesTest` (9): cardio hecho primero con fuerza pendiente (y `nextIncompleteStepAfter` sigue en null, es decir, la navegación no cambió); nada pendiente; ejercicios saltados y series omitidas no cuentan; «Terminar hasta acá» (cuenta el anterior sin hacer, no los saltados); calentamiento/movilidad no cuentan; serie de cardio pendiente (singular y «Seguir con ella»); unilateral cuenta una vez; máximo 3 ejercicios nombrados; nombre de pantalla por parámetro.
- No hay prueba JVM de `continuePendingSeriesFromFinish` (exige construir el ViewModel completo); se cubre en la pasada de emulador.

## Clases a ejecutar para verificar el paquete
`WorkoutRecordFabPlacementTest`, `WorkoutPendingSeriesTest`, `WorkoutStepNavigatorNavigationTest`, `WorkoutStepNavigatorResumeTest`, `WorkoutStepNavigatorCardioTimerLockTest`, `WorkoutFinishCalibrationTest`, `WorkoutFinishControllerPostCommitTest`, `WorkoutFinishControllerTimeoutTest`, `WorkoutFinishEmptySessionTest`, `WorkoutFinishSheetEntryTest`, `WorkoutFinishVolumeAdvanceDurableTest`, `WorkoutStepRulesTest`, `WorkoutSetPagerCardioMultiSetTest`. (No existen pruebas `WorkoutFeedback*`.)

## Gradle ejecutado
- `K-compile-1.log`: exitCode 1 (2 min 22 s). Dos errores `e:` en `programdetail/ProgramDetailViewModel.kt` (ajeno, otro agente a mitad de edición: `asStateFlow` sin resolver y `val` reasignado). Ninguno en archivos míos. No los toqué.
- `K-compile-2.log`: `compileBaseDebugKotlin` + `compileBaseDebugUnitTestKotlin`, BUILD SUCCESSFUL (8 min 32 s, exitCode 0).
- `K-tests-1.log`: 13 clases, 54 pruebas, 0 fallos, 0 omitidas, exitCode 0. Recibos en `...\consolidation-20261001\receipts\K-*.receipt.json`.
- No se compiló androidTest (no se cambiaron androidTest). Solo flavor base.

## Pasos de emulador para quien coordina (emulator-5582 o 5580; NO 5554/5556)
APK: base debug recompilado con estos cambios (`assembleBaseDebug`).

### D2.2 — botón sobre el teclado
1. Abrir una sesión de fuerza en vivo (fixture `finish-v2-normal-v27`, o `ui_strength_s1_s2`: Sentadilla Trasera con Barra Baja, ver README §12). Esperar a la tarjeta de la serie S1 con «Carga (kg)» y el botón redondo naranja (✓) abajo a la derecha.
2. Tocar el valor de «Carga (kg)» (o el campo de repeticiones) para abrir el teclado numérico.
3. Debe verse: el botón ✓ sube y queda ~12 dp POR ENCIMA del teclado, entero y tocable (nunca detrás del teclado); la animación es suave. La tarjeta no cambia de tamaño. El campo enfocado debe seguir visible encima del teclado.
4. Escribir un valor y tocar el botón: la serie se registra (no cae en una tecla del teclado). Cerrar el teclado (Back o tocar fuera): el botón vuelve a su sitio de siempre (sobre el dock) con animación. Back con teclado abierto solo cierra el teclado, no abre «¿Qué deseas hacer?».
5. Repetir en pantalla pequeña: `adb -s emulator-5582 shell wm size 720x1280` y `wm density 240` (≈360x640 dp); al terminar `wm size reset` y `wm density reset`. Comprobar que el botón no sale por arriba de la pantalla ni tapa el campo que se edita. Con el botón en el borde derecho puede solapar la parte baja de la tarjeta (riesgo previsto en el plan): anotar si tapa algo útil.
6. Si el campo enfocado queda tapado por el teclado en pantalla pequeña (hipótesis [I] del plan, que no pude verificar sin emulador), el arreglo sería dar al pager un `bottom` igual al inset del teclado menos el dock, cosa que implica tocar `WorkoutV2Body.kt` (fuera de mi lista); no se hizo. Los campos tienen `bringIntoViewRequester`, pero sin padding de teclado no hay contenedor que se reduzca.
7. No quitar `dismiss_ime_if_shown` de `qa_common.py` (README §17): con el botón ya visible los drivers siguen funcionando, pero ese cierre del teclado evita regresiones.

### D2.1 — aviso de fuerza pendiente tras el cardio
1. Fixture `cardio-two-series-ui-v27` («Carrera GPS QA dos series», ver `tools/avd/ui_cardio_two_series.py` y `cardio_nav.py`): fuerza (Floor Press y demás) primero en el carril y el cardio al final bajo «— CARDIO —» (tarjeta «Estático · 60 min»).
2. Abrir el entreno y, SIN hacer ninguna serie de fuerza, deslizar el carril y tocar la tarjeta de cardio. Registrar la serie 1 de cardio y la serie 2 («Finalizar y registrar» → confirmar; aceptar la tarjeta de sensaciones si aparece).
3. Debe verse: la hoja «RESUMEN DE ENTRENAMIENTO» con, bajo el título, un recuadro ámbar «Te quedan N series sin hacer: <ejercicio> (n), …» (N = series de fuerza sin registrar; calentamiento y movilidad no cuentan) y el botón «Seguir con ellas». Debajo, el resumen de siempre sin cambios. (Con 0 series de fuerza registradas aparece además el aviso previo de sesión vacía.)
4. Tocar «Seguir con ellas»: la hoja se cierra y el cursor salta a la primera serie de fuerza pendiente (tarjeta de ese ejercicio, serie 1). Registrar todas las series de fuerza: al terminar la última, la hoja de resumen aparece SIN el recuadro ámbar.
5. Caso «nada pendiente»: sesión terminada completa (p. ej. `finish-v2-normal-v27` con todas las series registradas): el resumen no muestra el recuadro.
6. Caso «saltados a propósito»: en una sesión con varios ejercicios, saltar un ejercicio entero (menú del ejercicio → Saltar) y registrar el resto: el resumen no cuenta el saltado. Variante «Terminar hasta acá» desde un ejercicio intermedio: los ejercicios siguientes (y el actual si está incompleto) no cuentan; un ejercicio anterior que quedó sin hacer SÍ cuenta.
7. Tocar «Volver» en el resumen sigue funcionando como antes. Guardar con el aviso presente guarda igual (el aviso es informativo).
Textos exactos: «Te quedan N series sin hacer: …», «Te queda 1 serie sin hacer: …», botón «Seguir con ellas» («Seguir con ella» si N = 1). TestTags: `finish_pending_series_notice`, `finish_pending_series_continue`.

## Riesgos abiertos
- Pantalla pequeña con teclado: posible solape del botón con la parte baja de la tarjeta y/o campo tapado (ver D2.2 paso 5-6); requiere la pasada de emulador.
- `continuePendingSeriesFromFinish` (cierra la hoja y selecciona un paso) no tiene prueba JVM; depende de `hideFinish()` + `selectWorkoutStep`, ambos ya existentes y probados en uso.
- Si el temporizador de cardio protege la serie activa, `selectWorkoutStep` puede bloquear el salto (comportamiento existente); tras terminar el cardio no aplica.
- El WorkoutScreen.kt tiene un import ahora sin uso (`WorkoutRecordFab`): solo advertencia.

## Decisiones
- Se contaron series de fuerza Y de cardio (una serie de cardio no hecha también se perdería al guardar); calentamiento y movilidad no.
- Lectura del estado pendiente vía función nueva en el navegador en vez de reutilizar `firstIncompleteStep`, que incluye calentamiento/movilidad.
- «Seguir con ellas» salta a la primera serie pendiente en el orden canónico de la sesión (puede ser de cardio si es lo único pendiente).
- Botón sin ocultar con el teclado (accesibilidad), anclado a `WindowInsets.ime` + 12 dp.
