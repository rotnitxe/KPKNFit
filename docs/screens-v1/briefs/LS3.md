# LS3 · Sesión en vivo: el descanso único, su píldora, el feedback del ejercicio y las notificaciones (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LS0, LS1, LS2a y LS2b integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md`, §0, §3 y §6: TU especificación;
- el mapa `C:\kw\analysis\maps\workout.md`, §1.1, §1.3 (enlace profundo y notificaciones), §2.1, §2.6, §4, §5 y §6. Las líneas
  son de ANTES de LS0 y LS2: búscalas por nombre;
- las piezas de LS1 y LS2 en `screens/workout/design/` (panel de la serie, tira, isla) y `ui/components/rings/` para el anillo.

Hoy hay TRES descansos (`RestLiveCard` en el pager, `RestTimerOverlay` y `RestTimerPill`) con etiquetas en MAYÚSCULAS y
`Locale.getDefault()`. Este paquete deja UNO, con piel KPKN. La lógica NO cambia: `RestTimerController`, el orquestador, el
descanso adaptativo, `RestStuckAtZeroPolicy`, la alarma y el servicio en primer plano siguen igual.

## Qué haces
1. **El descanso** (`screens/workout/design/WorkoutRestPanel.kt`, sin estado). Al registrar, ocupa el sitio del panel de la serie
   con un fundido cruzado de 220 ms (con `wizardReducedMotion()`, 120 ms y sin escala):
   - un anillo de 200 dp que se vacía en `SymbolPalette.columna` sobre una pista blanca al 8 %, con el tiempo restante en
     `KpknType.metricXL` tabular («1:45») en el centro y «Descanso» en `caption` debajo;
   - debajo del anillo, «Siguiente: Serie 3 · 80 kg × 8» en `body` (o «Siguiente ronda» y «Entre lados» cuando toque);
   - `KpknSecondaryPill("−15 s")` y `KpknSecondaryPill("+15 s")` en una fila, y `KpknTextAction("Saltar descanso")` debajo;
   - el descanso adaptativo, si sugiere otro tiempo: «Descanso sugerido 2:30» en `caption` con `KpknTextAction("Usar")`;
   - en cero (`RestStuckAtZeroPolicy`): el anillo vacío, «Listo» y «Registrar serie» de LS2b disponible; háptico y sonido opcional
     como hoy;
   - con `wizardReducedMotion()`, el anillo se actualiza a saltos de segundo, sin animación continua.
   - Sustituye a `RestLiveCard` y a `RestTimerOverlay`. Lo que siga en uso de ellos, se queda; lo demás, se borra (Grep en todo
     `app/src`).
2. **La píldora del descanso minimizado** (`design/WorkoutRestPill.kt`): si la persona sale de la página (ruta, cockpit u otra
   pantalla de la sesión), el descanso queda como una píldora de vidrio v2 arriba, bajo la fila superior: «1:45 · Descanso» en
   `bodyStrong` tabular, con un arco fino de progreso. Tocarla vuelve al descanso. Sustituye a `RestTimerPill`.
3. **El feedback del ejercicio** (`WorkoutPostExerciseFeedbackHost`), dentro del descanso como hoy, con piezas KPKN:
   - las molestias y el esfuerzo con `KpknChip`;
   - la barra de molestia propia (sin `Slider` ni `Card` de Material): una regla de 0 a 10 con la marca arrastrable, como
     `WizardWeightRule`, o la pieza KPKN que ya exista para escalas;
   - mismos datos y mismas llamadas.
4. **Las notificaciones abren la sesión:**
   - en `WorkoutRestForegroundService`, `WorkoutRestAlertManager`, `WorkoutVoiceForegroundService`, `CardioGpsForegroundService` y
     `CardioGpsMilestoneNotifier`, el `path` pasa de `"training"` a `workout/<programId>/<sessionId>` (lo resuelve
     `DeepLinkRouter`) cuando la sesión en curso lo tiene. Si no, sigue en `"training"`;
   - antes de cambiarlo, comprueba que esa ruta REANUDA la sesión en curso, igual que la tarjeta flotante de Inicio y Entreno, y que
     no abre el diálogo de conflicto ni apila otra copia de la pantalla. Si no es así sin tocar `MainActivity.kt`, no lo cambies y
     explícalo en el informe;
   - los recordatorios para ir a entrenar (`WorkoutReminderManager`) siguen en `"training"`.
   - La notificación de descanso ya tiene «−15 s», «+15 s», «Saltar» y «Hecho»: revisa que los textos sean esos, en tipo frase.
5. **Textos y relojes:**
   - «Descanso», «Siguiente ronda» y «Entre lados» en tipo frase;
   - `Locale` español explícito en los relojes (fuera `Locale.getDefault()` en `RestClockText` y en el overlay);
   - las pruebas instrumentadas buscan «DESCANSO», «Descanso sugerido», «Ajustar tiempo» y «Saltar descanso». Si un texto pasa a
     eyebrow, se pinta en mayúsculas con el estilo y la cadena se conserva; si cambia, actualiza su prueba en el mismo commit.

## Pruebas
- **Siguen en verde:**
  - las JVM de `screens/workout/` y `domain/workout/` (en especial las de descanso: `*Rest*`);
  - las de `services/workout/` (`*WorkoutRest*`, `*WorkoutVoice*`);
  - `*OngoingWorkout*`, `*FinalizeWorkout*`, `*StartWorkoutConflict*`, `*WorkoutFrame*`, `*WorkoutSetPanel*` y `*DeepLink*`;
  - la compilación de `androidTest`.
- **Robolectric** `WorkoutRestPanelTest`:
  - «−15 s» y «+15 s» llaman con ∓15;
  - «Saltar descanso» llama a saltar;
  - «Usar» aplica el descanso sugerido;
  - en cero se ve «Listo»;
  - la píldora llama a volver.
- **Unitarias:** la ruta de la notificación con y sin sesión en curso (una función pura que la construya, con su prueba).
- **Hoja de render** `render/WorkoutRestRenderSheetTest`, con las piezas montadas con datos de ejemplo:
  - descanso a 1:45 de 2:30;
  - con descanso sugerido;
  - en cero;
  - «Siguiente ronda» en una superserie;
  - la píldora minimizada sobre la fila superior;
  - el feedback del ejercicio;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/ls3`.
  - Mira cada PNG con Read y critica con el listón del usuario: el descanso se mira de reojo a dos metros. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls3 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "com.example.kpkn.services.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*DeepLink*" --tests "*WorkoutRest*" > /c/kw/logs/screens-ls3-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(workout): un solo descanso con anillo, píldora minimizada y feedback del ejercicio en piezas KPKN (LS3)`.
- `fix(workout): las notificaciones de la sesión abren la sesión, no Entreno (LS3)`.
- Si borras piezas viejas, aparte: `refactor(workout): quita los descansos viejos (LS3)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lógica del descanso y de la sesión (`RestTimerController`, `WorkoutRestTimerOrchestrator`, `WorkoutViewModel` y sus
  controladores). Lo que falte, AL FINAL del VM.
- La serie (LS2b, ya integrada: solo el relevo serie ↔ descanso), la ruta (LS4), la preparación y el cardio (LS5), el resumen y el
  cockpit (LS6) y las hojas (LS7).
- Las firmas de las piezas de LS1, LS2a y LS2b: solo puedes añadir parámetros opcionales al final.
- `MainActivity.kt`, `screens/sessioneditor/` y `navigation/` (solo lees `DeepLinkRouter`).
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Notificaciones: qué ruta abre cada una ahora, y cómo comprobaste que reanuda la sesión.
- Textos cambiados y pruebas actualizadas.
- Pruebas: número y resultado.
- PNG y crítica.
