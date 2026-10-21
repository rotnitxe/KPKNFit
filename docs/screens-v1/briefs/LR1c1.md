# LR1c1 · Sesión en vivo ORIGINAL: refinado del descanso en página, el cardio y lo que sale tras la serie (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `9ccf4831c`, con LR1a integrado). Lee ENTERO
`docs/screens-v1/briefs/LR1_COMUN.md` y lo que pide leer.

## Tus pantallas y archivos (solo estos)
Todos bajo `screens/workout/`.

| Mapa | Lo que ve la persona | Archivo |
|---|---|---|
| B4 | Descanso dentro del paginador: DESCANSO, SUPERSERIE, SIGUIENTE RONDA, ENTRE LADOS; −15 s, +15 s, saltar y sugerido | `components/RestLiveCard.kt` |
| B1 | Cardio en vivo: reloj, GPS, «Completar bloque», «Finalizar y registrar» y su confirmación | `CardioLiveCard.kt` |
| B5 | La frase del relator bajo la cabecera | `WorkoutLiveRelatorBar.kt` |
| B6 | La tarjeta de gasto tras una serie (Energía, Columna, Muscular) | `WorkoutDrainOverlayUi.kt` |
| B14 | El feedback tras el ejercicio dentro del descanso (técnica, intensidad, «Fallo», molestias, «Registrar feedback») | `WorkoutPostExerciseFeedbackHost.kt` |
| B15 | «Reportar molestias» rápido tras un fallo | `WorkoutDrawerHost.kt` |

## Qué cambias (solo la piel)
1. **Descanso en página (B4).** Es el hermano del descanso a pantalla completa que refinó LR1a (`WorkoutRestOverlay.kt`). Tiene que
   parecer de la misma familia:
   - el reloj, en Syne con cifras tabulares y sin cero delante;
   - −15 s y +15 s como en LR1a: botones de icono `inset` con su texto;
   - «Saltar», en tinta;
   - el resumen de la última serie, como el módulo «ÚLTIMA SERIE» de LR1a;
   - las insignias (`RestLiveTinyBadge`), como píldoras `inset`;
   - `#121820`, `#FFD740` y `#FF5252`, a tokens.
2. **Cardio (B1).** La tarjeta, en `module`.
   - El reloj, en Syne tabular.
   - Los botones, a `KpknPrimaryPill` / `KpknSecondaryPill` / `KpknTextAction` según su peso actual.
   - Los chips de serie de cardio, a `KpknChip`.
   - `#111214`, `#38BDF8` y `#10B981` pasan a tokens. Lo que marca un estado (GPS activo, bloque hecho) usa `ok` o el acento.
   - El `KpknAlertDialog` de confirmación no se toca; sus botones de dentro, sí.
3. **Relator (B5).** El texto, en `body` / `textMuted`, y sus acciones como enlaces en tinta subrayados o `KpknTextAction`. El brillo
   (shimmer) se queda, pero neutro: tinta al 40–100 %, sin color.
4. **Gasto (B6).** Las barras, con los colores de los RINGS (Energía #F7CF73, Columna #8FB2FF, Músculos #F49A6E), con pista blanca al
   8 % y cifras en Syne `metricM`. Fuera `#58C4FF`, `#FFB85C` y `#FF6F7D`. El velo opaco se queda.
5. **Feedback tras el ejercicio (B14).**
   - Los Slider, vestidos: pista blanca al 8 %, progreso en tinta y pulgar de tinta.
   - Los FilterChip, a `KpknChip`; el campo, a `KpknTextField`; «Registrar feedback», a `KpknPrimaryPill`.
   - Los verdes sueltos (`#1E3A2F`, `#388E3C`, `#81C784`…), a `ok`.
   - Va dentro del descanso refinado por LR1a: que se vea de la misma familia.
6. **Molestias rápidas (B15).** Lo de dentro del `KpknSheet`: campo, chips, «Sin molestias» (`KpknSecondaryPill`) y «Guardar»
   (`KpknPrimaryPill`).

## Hoja de render
`render/LiveSessionRestCardioRenderSheetTest`: el descanso en página (normal y de superserie), el cardio (en marcha y su
confirmación), el relator, el gasto, el feedback y las molestias, a 360 dp, al 100 % y al 130 %. PNG en
`C:/kw/shots/screens/lr1c1`.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lr1c1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*LiveSessionRestCardio*" > /c/kw/logs/screens-lr1c1-<n>.log 2>&1`

`.dbg`: `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-lr1c1-dbg-<n>.log 2>&1`,
copiado a `C:/kw/shots/lr1c1/app-lr1c1-dbg.apk`.

## Commit
- `style(workout): el descanso en página y el cardio de la sesión original con la piel nueva (LR1c1)`.
- `style(workout): relator, gasto, feedback y molestias con la piel nueva (LR1c1)`.
- `test(workout): hoja de render del descanso en página, el cardio y lo de tras la serie (LR1c1)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto (archivo y composable).
- Los PNG, con tu comparación frente a la original.
- Pruebas: número y resultado.
- La ruta del `.dbg`.
