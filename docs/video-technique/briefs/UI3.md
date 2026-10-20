# UI-3 · El descanso: el vídeo de la serie recién registrada, en una línea con miniatura

- **Rama:** `vt/ui3` en `C:\kw\vt-a`, que sale de `feat/vt-ui` con UI-1 y UI-2 integrados.
- **Logs:** `C:\kw\logs\vt-ui3-<n>.log`.
- **PNG:** `C:/kw/shots/vt/ui/3`.
- **Antes de empezar, lee:**
  - `UI_COMUN.md` (y lo que cita) y `docs/video-technique/UI_PLAN.md` (D37);
  - los prototipos `F1`, `F2`, `F3` y `F4` del descanso.
- **Código:**
  - `screens/workout/design/WorkoutCompletedVideoSuggestion.kt`;
  - `screens/workout/WorkoutOverlayHost.kt` (~L60-80) y `WorkoutV2Body.kt` (~L125-135): cómo se arma `videoSlot`;
  - `screens/workout/components/RestLiveCard.kt` (~L230-245), `RestFeedbackCard.kt` (~L136-150) y `WorkoutRestOverlay.kt`, donde se
    pinta `videoSlot`;
  - `screens/workout/design/VisualModeAnalysis.kt` (UI-1a: insignia y frase) y `WorkoutVideoChip.kt` (UI-2);
  - `screens/workout/components/WorkoutMediaThumb.kt`.

## Por qué
En el descanso, el vídeo de la serie recién registrada aparece hoy como la misma fila de texto plano del anverso. «Corregir» es el
camino principal del análisis, porque casi siempre se registra antes de que termine. Merece una pieza clara, que no empuje el resto de
la tarjeta de descanso.

## Qué haces
- **Pieza nueva** sin estado, `WorkoutRestVideoLine(media: WorkoutMedia?, value: SetVideoAnalysisUi, badge: VideoThumbBadge?, …)`, en
  `screens/workout/design/`.
  - Una sola fila de 48 dp: miniatura de 40 dp con esquinas de 10 dp y la insignia de UI-1a; a su derecha, la frase con la acción
    dentro (ley 3) en `KpknType.caption`, con 2 líneas como máximo.
- **Frases y acciones:**

  | Estado | Frase | Acción |
  |---|---|---|
  | Analizando | «Analizando el vídeo… 42 %» | ninguna |
  | `Mismatch` ALTA con conteos distintos | «Registraste 8; el vídeo cuenta 7 · corregir» | corregir |
  | `Mismatch` ALTA con fallos | añade « · marcar fallo» | corregir y marcar fallo |
  | `Agrees` | «El vídeo confirma 8 · ver análisis» | abrir |
  | MEDIA | «El vídeo cuenta unas 7 · revisar» | abrir |
  | `Inconclusive` | «Vídeo no concluyente · ver por qué» | abrir |
  | `Failed` | «No se pudo analizar el vídeo · reintentar» | reintentar |

  - Tocar la miniatura abre el análisis.
  - Las acciones hacen EXACTAMENTE lo que hacen hoy: «corregir» llama a `onPatch` con el mismo `CompletedSetPatch` que
    `WorkoutCompletedVideoSuggestion`, y «marcar fallo» igual.
  - «Reintentar» va por `retryVideoAnalysis` (UI-1a).
- **La miniatura:** `WorkoutMedia` sale de `mediaCapture.sessionMedia` por `mediaId`, donde se arma `videoSlot`: `WorkoutOverlayHost`
  y `WorkoutV2Body`. La insignia sale de `videoThumbBadgeByMediaId` (UI-1a).
- **Dónde va** en la tarjeta de descanso (`RestLiveCard`, `RestFeedbackCard`) y en el descanso completo (`WorkoutRestOverlay`): en el
  mismo hueco de hoy, `videoSlot`.
  - La tarjeta de descanso NO cambia de tamaño (F3 y F4 son de altura fija).
  - Si el hueco va dentro de una columna desplazable, la línea ocupa una sola fila y no empuja los RINGS ni el reloj fuera de la vista.
  - Compruébalo en la hoja al 130 %.
- **Lo viejo:** `WorkoutCompletedVideoSuggestion` pasa a usar la pieza nueva, o se sustituye en sus dos llamadas. La lógica de los
  parches no cambia.

## Archivos permitidos
- `screens/workout/design/WorkoutRestVideoLine.kt` (nuevo) y `WorkoutCompletedVideoSuggestion.kt`.
- `screens/workout/WorkoutOverlayHost.kt` y `screens/workout/WorkoutV2Body.kt`: solo armar `videoSlot` con la miniatura, la insignia y
  el reintento.
- `screens/workout/components/RestLiveCard.kt`, `RestFeedbackCard.kt` y `WorkoutRestOverlay.kt`: solo si el hueco necesita un ajuste de
  espacio. Su estructura NO cambia.
- Pruebas: las del descanso que ya existan (`*Rest*`) y la hoja de render del descanso que hoy pinta `video_rest`. Búscala con
  `grep -rl video_rest src/test`.

## Pruebas
- **Pura:** la frase y las acciones de cada fila de la tabla.
- **Robolectric:** «corregir» envía el mismo `CompletedSetPatch` que hoy, y la tarjeta de descanso mide lo mismo con y sin vídeo.
- **Hoja de render**, al 100 % y al 130 %:
  - la tarjeta de descanso y el descanso completo, sin vídeo;
  - la línea en cada estado de la tabla.

## Corridas
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*Rest*" --tests "com.example.kpkn.screens.workout.*VideoAnalysis*" --tests "com.example.kpkn.screens.workout.*RestVideo*"`
3. La hoja del descanso, con `KPKN_RENDER_DIR=C:/kw/shots/vt/ui/3`.

## Commits
- `feat(workout): el vídeo de la serie en el descanso, en una línea con miniatura y la acción en la frase`
- `test(workout): hoja del descanso con el vídeo en cada estado`
