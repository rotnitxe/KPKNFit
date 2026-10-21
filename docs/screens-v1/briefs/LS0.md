# LS0 · Sesión en vivo: limpieza antes del rediseño (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3`). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md` (§1, §6 y §7);
- el mapa `C:\kw\analysis\maps\workout.md`, §6 y §7 enteros (son tu lista de trabajo).

Es un paquete de LIMPIEZA: no cambia ningún diseño ni ninguna lógica. Deja la sesión en vivo lista para rediseñarla.

Código: `android-native/app/src/main/java/com/example/kpkn/screens/workout/` y `.../components/`. Antes de borrar algo, Grep en TODO
`app/src` (main, test y androidTest) con el nombre exacto.

## Qué haces
1. **Código muerto** (el mapa, §7.1 y §7.3), confirmado uno a uno con Grep:
   - archivos enteros sin llamadas: `components/WorkoutWarmupOverlay.kt` (1.049 líneas), `components/GodModeSessionBoard.kt` (580),
     `BarbellPlateVisualizer.kt` (239), `WorkoutRestComponents.kt` (vacío);
   - funciones: `WorkoutWarmupInlineCard`, `WorkoutSupersetWarmupRevealCard`, `WorkoutWarmupChecklistCard`,
     `WorkoutMobilitySeriesCard`, `WorkoutContinuityTransitionBanner`, `WorkoutExerciseEditContent`, `WorkoutExerciseTabs`,
     `ExerciseDrainOverlayHost`, `InfoPill`, `CardioLiveAccentField`, `MuscleProgressCard`, `SupersetRoadmapCardLegacy`,
     `MuscleSliderChip`, `GodModeNicknameDialog`, `UltraFastAppliedBanner`, `WorkoutMetricChip`, `WorkoutSectionTitle`;
   - los marcados «sin verificar» en el mapa (`WorkoutWarmupSheet`, `WorkoutExerciseDrainContent`, `WorkoutSessionEnergyContent`,
     `GodModeRoadmapBadge`): verifícalos y bórralos solo si no tienen uso.
2. **Piezas que solo usan las pruebas, dentro de producción** (§7.2): `WorkoutMobilityOverlay`, `WorkoutCommandDock`,
   `WorkoutContinuityCard`, `WorkoutSetTransitionBanner`, `WorkoutExerciseQuickActionsSheet`, `UnifiedExerciseCarousel` y el archivo
   `WorkoutAndroidTestSupport.kt`. Decide por pieza, y anótalo:
   - si la prueba protege un comportamiento que la app YA no tiene, borra la pieza y su prueba;
   - si la prueba protege algo vivo a través de esa pieza de apoyo, mueve la pieza a `src/androidTest` (mismo paquete) sin cambiarla.
3. **Duplicados** (§7.4):
   - `ExerciseTagSheetContent`, `WorkoutExerciseSetupContent` y `ExerciseSetupSheetContent` existen dos veces
     (`WorkoutContextComponents.kt` y `WorkoutExerciseSheets.kt`): quédate con la familia que usan las llamadas vivas y borra la otra
     si no tiene uso; si las dos tienen llamadas, anótalo y no toques nada;
   - `shouldRenderRestLiveCard` (siempre `true`): quita la función y simplifica su llamada;
   - `RestLiveTinyBadge` y `RestTinyBadge`: si hacen lo mismo, deja una.
4. **Textos** (§7.5):
   - errores de voz en inglés («Voice set persistence failed», «Voice set correction failed») a español llano; si son solo logs, se
     quedan en inglés pero NO llegan a la pantalla;
   - «ERR» y «ERROR» a la vista pasan a «Error»; «Reps mínimas» a «Repeticiones mínimas»; «Reps» a «Repeticiones» donde quepa (si no
     cabe, «Rep.»);
   - voseo: «También podés dictar…» pasa a «También puedes dictar…»;
   - los 16 «...» en ASCII dentro de textos pasan a «…» (salvo los que busque una prueba: anótalos);
   - NO toques las MAYÚSCULAS («DESCANSO», «SERIES EFECTIVAS»…): las buscan pruebas instrumentadas y se van con el rediseño (LS3).
5. **`Locale.getDefault()`** (§7.6): en `components/WorkoutRestOverlay.kt:844, 851, 860` y `WorkoutShareService.kt:489, 690`, usa el
   locale efectivo de la app (`LocaleManager.getEffectiveLocale`, como Nutrición) o `Locale.forLanguageTag("es")` si no hay contexto.

## Pruebas
- **Siguen en verde:** todas las de `src/test/.../screens/workout/`, `src/test/.../domain/workout/` y
  `src/test/.../data/` que toquen la sesión (lista en el mapa, §6.5), y la compilación de `androidTest`.
- Si borras una pieza y su prueba, anótalo.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" > /c/kw/logs/screens-ls0-<n>.log 2>&1`

## Commit
- `refactor(workout): quita el código muerto y las piezas de prueba de la sesión en vivo (LS0)`.
- `fix(workout): textos en español, sin voseo ni «...» y relojes con el locale de la app (LS0)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Cambiar diseño o lógica (registro, descanso, voz, persistencia, cierre).
- `screens/sessioneditor/` (SE0, en paralelo) y `MainActivity.kt`.
- Push.

## Informe
- Líneas quitadas, por archivo.
- Lo que NO borraste porque tenía uso, y lo que moviste a `androidTest`.
- Textos cambiados.
- Pruebas: número y resultado.
