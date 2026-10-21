# LR1b · Sesión en vivo ORIGINAL: refinado de lo de antes de la primera serie (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `9ccf4831c`, con LR1a integrado). Lee ENTERO
`docs/screens-v1/briefs/LR1_COMUN.md` y lo que pide leer.

## Tus pantallas y archivos (solo estos)
Todos bajo `screens/workout/`.

| Mapa | Lo que ve la persona | Archivo |
|---|---|---|
| A1 | Hoja de estado «Reporta tu estado antes de entrenar», con sus Popup de ajuste de anillo y de información del modo voz | `components/WorkoutReadinessSheet.kt` |
| A2 | Página «APROXIMACIÓN» (series de aproximación con peso, añadir serie, barra de descanso, saltar) | `components/WarmupMobilityLiveCards.kt` |
| A3 | Página «MOVILIDAD» (lista, reloj global, omitir y continuar) | `components/WarmupMobilityLiveCards.kt` |
| G10 | «¿Cómo quieres entrenar hoy?» (modo música o manos libres) | `components/VoiceCaptureModeDialog.kt` |

## Qué cambias (solo la piel)
1. **Hoja de estado (A1).** Mírala en `C:\kw\shots\screens\phone-lr1a\s4_a.png`.
   - El eyebrow amarillo «Reporta tu estado antes de entrenar» pasa a eyebrow de `KpknType` en `textMuted`.
   - El título Syne «¿Cómo llegas a entrenar hoy?» se queda.
   - Los RINGS (`KpknTowerRings`) se quedan como están.
   - «MÚSCULOS / ENERGÍA / COLUMNA»: eyebrow con el color de su anillo. Las cifras, en Syne `metricM` y en tinta, con espacio
     inseparable («90 %»).
   - Las tres líneas de explicación («Músculos: Normal · …»): el nombre en tinta Inter SemiBold y el resto en `body` / `textMuted`.
   - El ✓ amarillo flotante pasa al ✓ de tinta de LR1a (`WorkoutRecordFab`), con el mismo tamaño y el mismo sitio. Si no puede
     reutilizarlo tal cual, copia su aspecto.
   - Los controles de dentro (Switch, SegmentedButton, Slider, OutlinedTextField, FilterChip, FAB y Button), a sus piezas de
     `ui/design` según la tabla del común.
   - Los dos Popup (`ReadinessAdjustCard`, `VoiceModeInfoCard`): superficie `module` con filete y sin vidrio.
   - `#1F1F1F` y `#FACC15`, a tokens.
2. **Aproximación (A2) y movilidad (A3).** Son tarjetas de la página, como la de la serie: `module`, cifras e inputs en `inset`.
   - El rótulo «APROXIMACIÓN» / «MOVILIDAD», como eyebrow.
   - Las líneas de la lista, con `KpknCheckRow` o un check propio en el mismo sitio. La marca hecha, en `ok`.
   - El campo de peso, como celda `inset` con coma decimal.
   - «Agregar serie», «Saltar» y «Omitir», a `KpknSecondaryPill` / `KpknTextAction` según su peso actual, en el mismo sitio.
   - El reloj de movilidad, en Syne con cifras tabulares, como el del descanso.
   - El `#FF5252`, a `danger`, y el `#1B232E`, a su token.
3. **Modo de voz (G10).** Lo de dentro del `KpknGlassDialog`: «Empezar» a `KpknPrimaryPill`, «Ahora no» a `KpknTextAction` y las
   dos opciones como módulos de opción con la elegida en tinta.

## Hoja de render
`render/LiveSessionPrepRenderSheetTest`: la hoja de estado entera, aproximación (con 2 series hechas y 1 pendiente), movilidad y el
diálogo de voz, a 360 dp, al 100 % y al 130 %. PNG en `C:/kw/shots/screens/lr1b`.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lr1b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*LiveSessionPrep*" > /c/kw/logs/screens-lr1b-<n>.log 2>&1`

`.dbg`: `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-lr1b-dbg-<n>.log 2>&1`,
copiado a `C:/kw/shots/lr1b/app-lr1b-dbg.apk`.

## Commit
- `style(workout): la hoja de estado de la sesión original con la piel nueva (LR1b)`.
- `style(workout): aproximación, movilidad y modo de voz con la piel nueva (LR1b)`.
- `test(workout): hoja de render de lo de antes de la primera serie (LR1b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto (archivo y composable).
- Los PNG, con tu comparación frente a la original.
- Pruebas: número y resultado.
- La ruta del `.dbg`.
