# LS6 · Sesión en vivo: el resumen al terminar, el cockpit «Sesión» y los diálogos (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LS0, LS1, LS2a y LS2b integrados; el mensaje dice
qué más hay). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md`, §0, §5 («Cockpit», «Terminar» y «Diálogos») y §6: TU especificación;
- el mapa `C:\kw\analysis\maps\workout.md`, §1.1, §2.1 («Finalizar y resumen», «Cockpit», «Álbum»), §4 y §6. Las líneas son de
  ANTES de LS0 y LS2: búscalas por nombre;
- `ui/components/rings/` (`KpknTowerRings`, `KpknRingColors`) y el `KpknMetric` de `ui/design/`.

## Qué haces
1. **Resumen al terminar** (`FinishWorkoutSheet` en `WorkoutFinishHost.kt`, unas 950 líneas), revestido con piezas sin estado en
   `screens/workout/design/WorkoutFinishSummary.kt`:
   - arriba, el título «Sesión terminada» en `KpknType.display` y el nombre de la sesión en `caption`;
   - **las cifras:** cuatro `KpknMetric` en rejilla de 2 × 2: duración («58 min»), series («22»), volumen («8.450 kg», con el
     separador de miles español) y récords («2»). Sin récords, la cuarta es «Ejercicios» con su número: nunca «0 récords»;
   - **la torre de los RINGS** (`KpknTowerRings`, 120 dp) gastándose UNA vez de lo que había a lo que queda (900 ms; con
     `wizardReducedMotion()`, ya en su valor), con el gasto por canal en `caption` y «−» (U+2212): «Músculos −32 · Energía −18 ·
     Columna −12». Solo con datos del motor: sin datos, el bloque no se pinta;
   - **las series pendientes**, si quedan, como hoy y con sus tags `finish_pending_series_notice` y
     `finish_pending_series_continue`;
   - **las notas** de la sesión en un `KpknTextField` de varias líneas;
   - al pie, `KpknPrimaryPill("Guardar entrenamiento")` a todo el ancho y `KpknSecondaryPill("Compartir")`. «Compartir en Instagram
     Stories» sigue donde esté hoy, con su descripción;
   - **calculando:** esqueletos (`KpknSkeleton`) en las cifras y la torre, y «Calculando la recuperación…» en `caption` si tarda más
     de 600 ms. Fuera el spinner;
   - **error del cálculo:** un `KpknModule(accent = WizardColors.danger)` compacto con el texto en lenguaje llano y
     `KpknTextAction("Reintentar")` si hoy existe. Nunca `error.message`;
   - sin series registradas: `KpknEmptyState` con el texto de hoy («Sin series registradas»).
2. **Cockpit «Sesión»** (`components/WorkoutSessionCockpit.kt`), en una `KpknSheet` en lugar del panel que sube desde abajo:
   - un `KpknSegmentedControl` con «Resumen · Fotos · Herramientas · Notas» en lugar del `HorizontalPager` con pestañas;
   - abre en la página que pida quien lo abre: el menú de LS2a manda «Notas» y «Fotos» a su página (hoy abren el cockpit en la
     primera). Añade un parámetro opcional `initialPage` al final;
   - cada página por dentro, con piezas KPKN (fuera `Surface` con color, `Icons.*`, `Color(0x…)` y tamaños sueltos). Mismos datos y
     mismas llamadas;
   - el álbum (`WorkoutSessionAlbumSheet`, hoy `ModalBottomSheet`) pasa a `KpknSheet`.
3. **Diálogos** (`WorkoutScreen.kt`, ya en `KpknAlertDialog`): conflicto de sesión en curso, lectura corrupta, error al iniciar y
   error al cancelar.
   - Revisa los textos (tipo frase, lenguaje llano, sin `error.message`).
   - Marca `destructive = true` en los que descartan o borran una sesión.
4. **Textos:** «Guardar entrenamiento», «Compartir», «Calculando la recuperación…», sin MAYÚSCULAS escritas a mano, «…» de un
   carácter. Antes de cambiar un texto, Grep en `src/androidTest`.
5. **Lo que queda sin uso:** bórralo con Grep en todo `app/src`.

## Pruebas
- **Siguen en verde:**
  - las JVM de `screens/workout/` y `domain/workout/`, sobre todo las de cierre;
  - `*FinalizeWorkout*`, `*OngoingWorkout*` y `*StartWorkoutConflict*`;
  - la compilación de `androidTest`.
- **Robolectric** `WorkoutFinishSummaryTest`:
  - las cuatro cifras con su unidad;
  - sin récords, la cuarta es «Ejercicios»;
  - sin datos del motor, sin torre;
  - «Guardar entrenamiento» llama una vez;
  - los tags de series pendientes existen.
- **Robolectric** `WorkoutCockpitTest`, con el cuerpo montado suelto: abre en «Notas» si se pide y cambiar de pestaña muestra su
  página.
- **Hoja de render** `render/WorkoutFinishRenderSheetTest`:
  - el resumen con récords y con la torre;
  - sin récords;
  - calculando;
  - error del cálculo;
  - con series pendientes;
  - el cockpit en sus cuatro páginas;
  - un diálogo destructivo;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/ls6`.
  - Mira cada PNG con Read y critica con el listón del usuario: es el momento de orgullo de la sesión. Tiene que verse mejor que el
    resumen de Hevy. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls6 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*FinalizeWorkout*" --tests "*OngoingWorkout*" --tests "*StartWorkoutConflict*" --tests "*WorkoutFinish*" --tests "*WorkoutCockpit*" > /c/kw/logs/screens-ls6-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(workout): resumen al terminar con cifras y la torre de los RINGS, cockpit en hoja y diálogos revisados (LS6)`.
- Si borras piezas viejas, aparte: `refactor(workout): quita las piezas viejas del cierre y del cockpit (LS6)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lógica del cierre (`WorkoutFinishController`, el cálculo de la recuperación, el guardado) y de la sesión. Lo que falte, AL FINAL
  del VM.
- La serie (LS2b), el descanso (LS3), la ruta (LS4), la preparación y el cardio (LS5) y las hojas de estructura y de contexto (LS7).
- Las firmas de las piezas de LS1 a LS4: solo puedes añadir parámetros opcionales al final.
- `MainActivity.kt` y `screens/sessioneditor/`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Textos cambiados y pruebas actualizadas.
- Pruebas: número y resultado.
- PNG y crítica.
