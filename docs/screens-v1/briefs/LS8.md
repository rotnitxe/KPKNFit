# LS8 · Sesión en vivo: cierre (pendientes, QA de una sesión entera en el emulador, accesibilidad y documentación) (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LS0 a LS7b integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md` entero;
- las decisiones 97 a 134 del registro del día (`C:\kw\analysis\decisiones_2026-10-13_dia.md`): de ahí sale la lista de pendientes;
- `C:\kw\tools\emu_run.py` y `C:\kw\tools\emu_start.py` (uso en su cabecera).

La sesión en vivo ya está rediseñada por partes. Este paquete la DEJA TERMINADA: cierra los pendientes, la recorre entera en el emulador
como lo haría una persona y documenta. La lógica NO cambia.

## Qué haces
1. **Pendientes de la ola:**
   - **ritmo del panel de la serie** (LS2b y LS4): a 360 × 740 dp, con el pie fijo (tira, «Registrar serie» e isla), tienen que verse
     sin desplazar las cifras, sus ±, la fila de intensidad y la sugerencia:
     - el espacio entre bloques baja de 20 a 14 dp;
     - la etiqueta «RIR» o «RPE» va en la misma línea que sus chips;
     - el relleno vertical del pie baja de 8 + 8 a 4 + 4 dp;
     - compruébalo en el emulador con `wm size 1080x2220` y `wm density 480`, y deja el emulador como estaba al terminar;
   - **feedback del ejercicio** (LS3):
     - «Registrar feedback» pasa a «Guardar sensaciones» y «Qué tan intenso fue» a «Esfuerzo»;
     - los chips de molestias van dentro de su módulo;
   - **preparación** (LS5a):
     - las comillas “…” de la explicación de la voz pasan a «…»;
     - el eyebrow «Reporta tu estado antes de entrenar» no repite el título: quítalo o cámbialo por «Antes de empezar»;
   - **`KpknAlertDialog`** (`ui/components`, pieza compartida; LS6): cuando las dos acciones no caben en una fila, se apilan (la
     principal arriba, a todo el ancho). Revisa con Grep que ningún diálogo de la app quede peor;
   - **cabecera del ejercicio** (LS4): el eyebrow «EJERCICIO 3 DE 6 · TORSO A» se parte en dos líneas a 360 dp; que quepa (tamaño o
     texto: «3 DE 6 · TORSO A»);
   - **círculo de selección:** LS4 copió el de SE6a; sácalo a `ui/design/KpknSelectionCircle.kt` y que lo usen los dos;
   - **gráfica de intervalos de la sesión en vivo** (`CardioIntervalChart`): si LS5b no lo hizo, las pausas van en blanco al 12 %.
2. **QA de una sesión entera en el emulador** (la parte más importante):
   - compila el `.dbg` de tu rama: `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-ls8-dbg-<n>.log 2>&1`;
   - arranca el emulador si no lo está (`python -I -X utf8 C:/kw/tools/emu_start.py`) y usa SOLO `emu_run.py` para todo (instalar,
     abrir, tocar, capturar);
   - prepara los datos como en la QA de la PR11:
     - `--clear-dbg` con `WizardHarnessActivity` y los extras `--es persona qa --es scope full --es start REVIEW_ACTIVATE --ez pro true`;
     - esperar unos 35 s y tocar «Activar» en (672, 2805);
     - después, `DemoSeedActivity` con `--ez pro true`;
     - si sale el permiso de notificaciones del sistema, «Don't allow»; y el aviso de la app, «Ahora no»;
   - recorre una sesión como una persona y captura cada paso:
     1. empezar una sesión desde Program Detail (un día con sesión);
     2. la preparación y «Empezar»;
     3. registrar dos series (cambiar kg con ± y teclado, elegir RIR, «Usar» la sugerencia);
     4. el descanso (±15 s, minimizar y volver con la píldora, saltarlo);
     5. el feedback del ejercicio;
     6. pasar al siguiente ejercicio con la isla;
     7. abrir la ruta y reordenar un ejercicio;
     8. la hoja «Técnica» desde el «Info»;
     9. las opciones avanzadas;
     10. una superserie si la sesión la tiene;
     11. terminar la sesión y el resumen;
   - mira cada captura con Read: lo que se rompa, se corte o se vea fuera del lenguaje, arréglalo si es tuyo y anótalo si no;
   - las capturas van a `C:/kw/shots/screens/ls8-qa/`. Usa la regla de los 20 minutos por tanda: si algo se atasca, para, anota y
     sigue.
3. **Accesibilidad y movimiento:**
   - TalkBack lee el panel («Serie 2 de 4, 80 kilos, 8 repeticiones, RIR 2»), la tira, el descanso y la ruta;
   - todo lo tocable mide 48 dp como mínimo;
   - con `wizardReducedMotion()`, nada se anima salvo fundidos de 120 ms. Revisa con Grep `animate`, `AnimatedVisibility` y `tween`
     en `screens/workout/`.
4. **Hojas de render de la sesión entera** (`render/WorkoutSessionRenderSheetTest`): la serie, el descanso, una superserie, el cardio,
   el calentamiento y el resumen, con las piezas montadas y datos de ejemplo; 360 dp, al 100 % y al 130 %, PNG en
   `C:/kw/shots/screens/ls8`.
5. **Documentación:** al final de `docs/screens-v1/LIVE_SESSION_V1.md`, una sección «Estado (LS8)» con:
   - lo terminado por punto de la especificación;
   - lo que no se hizo y su porqué;
   - las decisiones de los paquetes que cambian lo especificado;
   - lo que la QA del emulador encontró y no se arregló.

## Pruebas
- **Siguen en verde:**
  - las JVM de `screens/workout/` y `domain/workout/`;
  - `services.workout.*`;
  - `*OngoingWorkout*`, `*FinalizeWorkout*` y `*StartWorkoutConflict*`;
  - las de `ui/` (por `KpknAlertDialog` y el círculo de selección);
  - la compilación de `androidTest`.
- **Nueva:** `KpknAlertDialogStackTest` (Robolectric): con etiquetas largas, las acciones se apilan y las dos se leen enteras.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls8 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "com.example.kpkn.services.workout.*" --tests "com.example.kpkn.ui.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*WorkoutSessionRender*" --tests "*KpknAlertDialogStack*" > /c/kw/logs/screens-ls8-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `fix(workout): pendientes de la ola (ritmo del panel, feedback, preparación, cabecera y gráfica) (LS8)`.
- `fix(ui): KpknAlertDialog apila sus acciones cuando no caben y círculo de selección compartido (LS8)`.
- `feat(workout): hojas de render de la sesión entera y accesibilidad (LS8)`.
- `docs(workout): estado de la sesión en vivo v1 y QA en el emulador (LS8)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- **Los medios y el grabador** (`SetCardExerciseMediaBack`, `WorkoutMediaCaptureController`, `WorkoutMediaPreviewDialog`,
  `screens/workout/technique/`): los trabaja la ola VT.
- La lógica de la sesión (`WorkoutViewModel` y sus controladores). Lo que falte, AL FINAL del VM.
- `MainActivity.kt` y `screens/sessioneditor/` (salvo el círculo de selección compartido).
- Dejar el emulador con otro tamaño o densidad: `wm size reset` y `wm density reset` al terminar.
- Push.

## Informe
- Qué hiciste, por punto.
- La QA del emulador paso a paso, con la ruta de cada captura y lo que encontraste.
- Pruebas: número y resultado.
- PNG y crítica.
