# LS5b · Sesión en vivo: calentamiento, movilidad y cardio (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LS0, LS1, LS2a, LS2b, LS3 y SE5 integrados). Lee
antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md`, §0, §2 (la serie, para seguir su lenguaje), §5 («Calentamiento y movilidad» y «Cardio») y
  §6: TU especificación;
- el mapa `C:\kw\analysis\maps\workout.md`, §1.2, §2.1 («Calentamiento y movilidad», «Cardio») y §6. Las líneas son de ANTES:
  búscalas por nombre;
- el panel de la serie de LS2b (`design/WorkoutSetPanel.kt`, `WorkoutRecordPill`) y la mini línea de intervalos que dejó SE5 en el
  editor (`screens/sessioneditor/design/SessionCardioModule.kt`).

Las páginas de CALENTAMIENTO (series de aproximación, `Exercise.warmupSets`), MOVILIDAD y CARDIO del pager de la sesión siguen con
tarjetas viejas (`WarmupMobilityLiveCards.kt`, unas 870 líneas; `CardioLiveCard.kt`, 520; `CardioIntervalChart.kt`, 350). Este
paquete las pone al nivel de la serie. La lógica NO cambia: mismos datos, mismos temporizadores, mismo GPS y mismas llamadas.

## Qué haces
1. **Calentamiento y movilidad** (`WarmupPhaseLiveCard`, `MobilityPhaseLiveCard`, `LivePagerCardFrame`, `WarmupChecklistLine`,
   `PrepPhaseSkipBar`), con el lenguaje del panel de la serie, sin caja y sobre el negro:
   - el eyebrow «Aproximación 1 de 3» o «Movilidad 2 de 5» (mayúsculas por estilo);
   - el elemento actual en grande: su nombre en `KpknType.title` y su dosis en `metricXL` tabular («2 × 10» o «0:30»);
   - si lleva tiempo, el temporizador con `KpknSecondaryPill` «Empezar»/«Pausar» con su icono delante, como la serie por tiempo de
     LS2b;
   - debajo, la lista del bloque como checklist: una fila de 48 dp por elemento con un círculo (vacío, actual o con `Check`), su
     nombre y su dosis en `caption`;
   - la acción principal es la píldora de LS2b (la página ya registra su acción en `recordActionHolder`); su texto en estas páginas
     es «Hecho» si la pieza lo permite sin cambiar su firma (parámetro opcional al final); si no, se queda «Registrar serie»;
   - «Saltar calentamiento» y «Saltar movilidad» como `KpknTextAction`.
2. **Cardio** (`CardioLiveCard`, `CardioIntervalChart`):
   - el tiempo en `metricXL` tabular, centrado, con «Transcurrido» o «Restante» en `caption`;
   - con GPS: distancia y ritmo en `metricL` tabular, en dos columnas, con su unidad en `caption` («km», «/km»);
   - intervalos: la línea de tiempo con los colores de KPKN (trabajo en `SymbolPalette.energia`, recuperación en blanco al 12 % y
     el tramo actual con borde de tinta), la misma de la mini línea del editor en grande; debajo, «Intervalo 3 de 8 · Trabajo» en
     `caption`;
   - pausa y reanudar como `KpknSecondaryPill` con su icono delante; terminar como la píldora principal si hoy existe;
   - fuera `Surface` con color, `Icons.*`, `Color(0x…)` y tamaños sueltos.
3. **Textos:** «Calentamiento» (nunca «Warm-up»), «Movilidad», «Aproximación», tipo frase y «…» de un carácter. Las pruebas que
   buscan «Warm-up» o «Warm-up inteligente» se actualizan en el mismo commit (Grep en `src/androidTest` y `src/test`).
4. **Lo que queda sin uso:** bórralo con Grep en todo `app/src`.

## Pruebas
- **Siguen en verde:**
  - las JVM de `screens/workout/` y `domain/workout/` (en especial las de cardio, calentamiento y movilidad);
  - `*Cardio*`, `*Warmup*` y `*Mobility*`;
  - la compilación de `androidTest`.
- **Robolectric** `WorkoutPrepAndCardioTest`:
  - el calentamiento muestra «Aproximación 1 de 3» y la dosis;
  - «Empezar» del temporizador llama a empezar;
  - «Saltar calentamiento» llama a saltar;
  - el cardio con GPS muestra distancia y ritmo con su unidad;
  - pausar llama a pausar.
- **Hoja de render** `render/WorkoutPrepAndCardioRenderSheetTest`:
  - una aproximación;
  - movilidad con temporizador;
  - cardio continuo con GPS;
  - intervalos en el 3 de 8;
  - cardio en pausa;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/ls5b`.
  - Mira cada PNG con Read y critica con el listón del usuario: tiene que verse de la misma familia que la serie. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls5b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*Cardio*" --tests "*Warmup*" --tests "*Mobility*" --tests "*WorkoutPrepAndCardio*" > /c/kw/logs/screens-ls5b-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(workout): calentamiento, movilidad y cardio en vivo con el lenguaje de la serie (LS5b)`.
- Si borras piezas viejas, aparte: `refactor(workout): quita las tarjetas viejas de calentamiento, movilidad y cardio (LS5b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lógica de los temporizadores, del GPS, del pulso y de la sesión. Lo que falte, AL FINAL del VM.
- La serie, el descanso, la ruta, el resumen y las hojas (LS2b, LS3, LS4, LS6 y LS7) y la preparación (LS5a).
- Los medios y el grabador de la técnica (`SetCardExerciseMediaBack`, ola VT).
- Las firmas de `CardioValuePill`, `CardioDistanceWheelDialog` y `CardioIntWheelDialog` (editor) y de las piezas de LS1 a LS3:
  solo parámetros opcionales al final.
- `MainActivity.kt` y `screens/sessioneditor/`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Textos cambiados y pruebas actualizadas.
- Pruebas: número y resultado.
- PNG y crítica.
