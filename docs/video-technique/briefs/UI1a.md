# UI-1a · Modo visual: el estado del análisis en las miniaturas y en la línea de abajo

- **Rama:** `vt/ui1a` en `C:\kw\vt-a`, que sale de `feat/vt-ui`.
- **Logs:** `C:\kw\logs\vt-ui1a-<n>.log`.
- **PNG:** `C:/kw/shots/vt/ui/1a`.
- **Antes de empezar, lee:** `UI_COMUN.md` (y lo que cita) y `docs/video-technique/UI_PLAN.md`.
- **Código que tocas o usas** (léelo antes de editar):
  - `screens/workout/components/SetCardExerciseMediaBack.kt`:
    - `SetCardExerciseMediaBack`;
    - la línea inferior «Se guarda en el álbum…» y el error con «Reintentar» (~L363-415);
    - `MediaStage` y sus miniaturas `thumbTiles` (~L560-760).
  - `screens/workout/components/WorkoutMediaThumb.kt`.
  - `screens/workout/WorkoutVideoAnalysisController.kt`: `SetVideoAnalysisState`, `stateFor`, `snapshots` y `videoAnalysisBySetKey`.
  - `screens/workout/WorkoutViewModel.kt` (~L7660), `WorkoutV2Body.kt` (~L125 y ~L2138) y `components/SetExecutionCard.kt`:
    - los parámetros de `SetInputCardV2`, ~L886-893;
    - la llamada a `SetCardExerciseMediaBack`, ~L3285-3313.
  - `screens/workout/design/WorkoutVideoRepsSuggestion.kt` (sus textos).
  - `data/repository/VideoAnalysisRepository.kt` (`reanalyze`).

## Qué haces

### 1. Estado «falló»
- Añade `data class Failed(val errorCode: String?)` AL FINAL de `SetVideoAnalysisState`.
- En `stateFor`, una fila con estado `FAILED` devuelve `Failed(errorCode)`. Hoy cae en `Inconclusive`.
- Completa cada `when` exhaustivo:
  - en `workoutVideoRepsText`, el texto es «No se pudo analizar el vídeo»;
  - la pieza del anverso ofrece «Ver análisis», igual que con `Inconclusive`. El anverso se rehace en UI-2: aquí solo que compile y
    diga la verdad.

### 2. Insignia de estado en cada miniatura de vídeo
- **Modelo puro** en el archivo nuevo `screens/workout/design/VisualModeAnalysis.kt`:
  `sealed interface VideoThumbBadge { Analyzing(progress: Int); Reps(count: Int, approximate: Boolean); Inconclusive; Failed }`.
- **La función de mapeo** desde la fila del análisis:

  | Fila | Insignia |
  |---|---|
  | QUEUED o RUNNING | `Analyzing(progressPct)` |
  | DONE con confianza ALTA y conteo | `Reps(n, approximate = false)` |
  | DONE con confianza MEDIA | `Reps(n, approximate = true)` |
  | DONE con BAJA o sin conteo | `Inconclusive` |
  | FAILED | `Failed` |
  | UNSUPPORTED | sin insignia (no entra en el mapa) |

- **Flujo:**
  - `WorkoutVideoAnalysisController` expone, AL FINAL, `videoThumbBadgeByMediaId: StateFlow<Map<String, VideoThumbBadge>>`, calculado
    desde las mismas `snapshots`, sin E/S nueva;
  - `WorkoutViewModel` lo reexpone AL FINAL;
  - `WorkoutV2Body` lo recoge y lo pasa a `SetInputCardV2` como `videoThumbBadges: Map<String, VideoThumbBadge> = emptyMap()`, último
    parámetro;
  - de ahí, a `SetCardExerciseMediaBack` y a `MediaStage`.
- **Aspecto**, dentro de la miniatura (que sigue con esquinas de 12 dp):
  - círculo de 20 dp abajo a la derecha, a 3 dp del borde;
  - fondo `Color.Black` al 72 % y borde de 1 dp `WizardColors.text` al 28 %;
  - `Analyzing`: anillo de progreso de 2 dp (pista `WizardColors.text` al 20 %, arco `WizardColors.text`) y sin texto;
  - `Reps`: «8» o «≈8» en `KpknType.chip` a 11 sp, con `WizardColors.text`;
  - `Inconclusive`: «?» en `WizardColors.textMuted`;
  - `Failed`: «!» en `WizardColors.danger`;
  - con movimiento reducido, sin animación.
- **Descripción de la miniatura para TalkBack:**
  - «Vídeo, analizando 42 %»;
  - «Vídeo, 8 repeticiones» y «Vídeo, unas 8 repeticiones»;
  - «Vídeo no concluyente»;
  - «Vídeo, no se pudo analizar».

### 3. La línea de abajo del Modo visual pasa a ser la del análisis
- **Cuándo:** si la serie activa tiene vídeo (`videoAnalysis`, que `SetInputCardV2` ya recibe: pásalo también a
  `SetCardExerciseMediaBack` como último parámetro) y no hay `captureError`, la línea muestra el análisis en lugar de «Se guarda en el
  álbum…». El error de guardado mantiene su prioridad.
- **Función pura** `visualModeAnalysisLine(state, setIndex): VisualModeAnalysisLine(text, actionLabel?, action)`, con
  `action ∈ {OPEN, RETRY, NONE}`:

  | Estado | Texto | Acción |
  |---|---|---|
  | `Analyzing(p)` | «Analizando el vídeo de la serie N… p %» | ninguna |
  | `Suggest` ALTA | «El vídeo cuenta 8 · +1 parcial · ver análisis» | abrir |
  | `Mismatch` ALTA con conteos distintos | «Registraste 8; el vídeo cuenta 7 · ver análisis» | abrir |
  | `Suggest` o `Mismatch` MEDIA | «El vídeo cuenta unas 8 · revisar» | abrir |
  | `Agrees(n)` | «El vídeo confirma 8 · ver análisis» | abrir |
  | `Inconclusive` | «Vídeo no concluyente · ver por qué» | abrir |
  | `Failed` | «No se pudo analizar el vídeo · reintentar» | reintentar |
  | `Unsupported(motivo)` | el motivo | ninguna |

  Parciales y fallos van como en `workoutVideoRepsText` («+1 parcial», «1 al fallo»).
- **La acción es una palabra de la frase** (ley 3), en el mismo `Text`:
  - `LinkAnnotation.Clickable` en semibold y al mismo tamaño (`KpknType.caption`);
  - «ver análisis», «revisar» y «ver por qué» llaman a `onOpenAnalysis(mediaId)`;
  - «reintentar» llama a `onRetryAnalysis(mediaId)`.
- **Ley 1:** el mismo contenedor, `maxLines = 2` con elipsis. La tarjeta NO cambia de tamaño en ningún estado; compruébalo en la hoja,
  al 130 %.

### 4. «Reintentar» el análisis
- `WorkoutViewModel.retryVideoAnalysis(mediaId)`, AL FINAL: `VideoAnalysisRepository.getInstance().reanalyze(mediaId)` en IO. Es una
  petición explícita (D31): corre aunque el ajuste esté apagado.
- Llévalo como `onRetryAnalysis: ((String) -> Unit)? = null` por el mismo camino que `onOpenAnalysis`: `WorkoutV2Body` →
  `SetInputCardV2` → `SetCardExerciseMediaBack`, siempre como últimos parámetros.

### 5. Textos sin voseo
- En los mensajes visibles de `screens/workout/WorkoutMediaCaptureController.kt` y `data/repository/WorkoutMediaRepository.kt`,
  cambia el voseo por español neutro sin cambiar el sentido: «Podés reintentar» → «Puedes reintentar», y así con el resto.
- Comprueba con `grep -rnE "Podés|podés|Querés|Tenés|Elegí|Mirá|Probá|Intentá|Volvé|Grabá|Tocá"` que no queda ninguno en
  `screens/workout`, `screens/technique`, `data/media` ni `data/repository`.

## Archivos permitidos
- `screens/workout/WorkoutVideoAnalysisController.kt`.
- `screens/workout/WorkoutViewModel.kt` (SOLO al final), `screens/workout/WorkoutV2Body.kt` y `screens/workout/components/SetExecutionCard.kt`
  (parámetros nuevos al final y su paso; nada más).
- `screens/workout/components/SetCardExerciseMediaBack.kt`.
- `screens/workout/design/WorkoutVideoRepsSuggestion.kt` y `WorkoutCompletedVideoSuggestion.kt` (solo la rama `Failed`).
- `screens/workout/design/VisualModeAnalysis.kt` (nuevo).
- `screens/workout/WorkoutMediaCaptureController.kt` y `data/repository/WorkoutMediaRepository.kt` (SOLO textos).
- Pruebas:
  - `src/test/.../screens/workout/VisualModeAnalysisTest.kt` (nueva);
  - `render/LiveCardRenderSheetMediaTest.kt` (escenas nuevas);
  - las de `screens/workout/components/` del Modo visual, solo si un cambio tuyo las rompe con razón.

## Pruebas
- **`VisualModeAnalysisTest`** (pura):
  - el texto y la acción de cada fila de la tabla del punto 3;
  - el mapeo de insignias del punto 2;
  - un `FAILED` ya no es `Inconclusive`.
- **Hoja de render** (`LiveCardRenderSheetMediaTest`), al 100 % y al 130 %:
  - el Modo visual compacto con 3 miniaturas: analizando, «8» y «?»;
  - la línea en cada estado: sin vídeo, analizando 42 %, ALTA, MEDIA, no concluyente, falló y sin soporte;
  - el borde de la tarjeta idéntico en todas, por la ley 1.

## Corridas
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*VideoAnalysis*" --tests "com.example.kpkn.screens.workout.*VisualMode*" --tests "com.example.kpkn.screens.workout.components.*Media*" --tests "com.example.kpkn.screens.workout.components.SetExecutionCardFacesTest"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/vt/ui/1a` con `--tests "com.example.kpkn.render.LiveCardRenderSheetMediaTest"`. Puede ir junto a
   la 2.

## Commits
- `feat(technique): estado del análisis en las miniaturas y en la línea del Modo visual`
- `fix(workout): mensajes de captura sin voseo`
- `test(technique): hoja del Modo visual con el estado del análisis`
