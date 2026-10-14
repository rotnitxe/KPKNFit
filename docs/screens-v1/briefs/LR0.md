# LR0 · Sesión en vivo: vuelve la ORIGINAL de la PR11 (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`.

**Por qué.** El usuario vio en su teléfono la sesión en vivo rediseñada (LS1 a LS8) y la rechazó: «Devuélveme la original». La
original es la de la PR11, la que tiene hoy en el teléfono: `feat/wizard-v3`, commit `8f0afc9dc`. Más adelante, en otro paquete y
con su visto bueno, se le refinará el aspecto. Este paquete NO cambia el diseño: solo restaura.

El rediseño queda archivado en la etiqueta `archive/live-session-redesign-v1`. No lo borres ni lo cambies.

## Qué haces
1. **Restaura la sesión en vivo de la PR11** sobre la rama, sin tocar el resto (el editor, Q4, Q5 y las piezas compartidas se quedan):
   - `git checkout 8f0afc9dc -- android-native/app/src/main/java/com/example/kpkn/screens/workout android-native/app/src/test/java/com/example/kpkn/screens/workout android-native/app/src/main/java/com/example/kpkn/domain/workout/UnilateralRules.kt`;
   - `git checkout` NO borra lo que se creó después: lista lo añadido con
     `git diff --name-only --diff-filter=A 8f0afc9dc HEAD -- android-native/app/src/main/java/com/example/kpkn/screens/workout android-native/app/src/test/java/com/example/kpkn/screens/workout`
     y bórralo con `git rm`;
   - **excepciones que se quedan como están hoy**, si compilan con la sesión restaurada:
     - `screens/workout/CardioIntervalChart.kt`: la usa el editor;
     - `RepairBenchmarkTrace`: también la usa el editor;
     - el arreglo de LS3 `48f7e759c` («las notificaciones de la sesión abren la sesión, no Entreno»): `services/workout/*`,
       `WorkoutSessionNotificationPath.kt` y `strings.xml`. Si ese commit tocaba también algo dentro de `screens/workout/`, vuelve a
       aplicar esa parte sobre la sesión original, de forma mínima;
   - **pruebas fuera de `screens/workout/`** que prueban piezas de LS que ya no existen (por ejemplo, las hojas de render de LS en
     `src/test/.../render/`): bórralas. Las de las piezas compartidas que usa el editor (`ui/design/…`) se quedan.
2. **Arreglos de lógica que salieron en LS y valen para la original.** Revisa
   `git log --no-merges --format='%h %s' 8f0afc9dc..archive/live-session-redesign-v1 -- android-native/app/src/main/java/com/example/kpkn/screens/workout android-native/app/src/main/java/com/example/kpkn/domain/workout android-native/app/src/main/java/com/example/kpkn/services`
   y busca fallos de LÓGICA (no de aspecto) que la original también tenga. Al menos estos:
   - **drop-sets que no se guardaban** (LS7b, `5260542a4`): nadie llamaba a `commit()` desde `9f941d8ab`, así que lo escrito en los
     drops no llegaba a la serie. Comprueba si la técnica original (`SetCardTechniqueBack` de la PR11) tiene el mismo fallo y, si lo
     tiene, arréglalo ahí;
   - **peso de calentamiento con coma** (LS5b, `942dff861`): «62,5» se perdía y caía a la sugerencia. Mira cómo lo arregló LS5b y,
     si la original parsea igual, aplica el arreglo;
   - cualquier otro `fix(` de LS que sea de lógica y valga para la original. Un commit por arreglo, con una prueba que falle sin él.
   - Lo que sea de aspecto NO se porta: lo hará el paquete de refinado.
3. **Documentación:** arriba de `docs/screens-v1/LIVE_SESSION_V1.md`, una nota: «2026-10-14: el usuario rechazó el rediseño y pidió
   volver a la sesión de la PR11. Esta especificación queda archivada con el código en la etiqueta
   `archive/live-session-redesign-v1`. El refinado del aspecto de la original irá en un documento aparte».
4. **El `.dbg` para la prueba en el teléfono** (la hago yo): al final, compílalo con
   `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-lr0-dbg-<n>.log 2>&1`.
   NO uses ni el teléfono ni el emulador.

## Pruebas
- **En verde:**
  - `com.example.kpkn.screens.workout.*`, `com.example.kpkn.domain.workout.*` y `com.example.kpkn.services.workout.*`;
  - `*OngoingWorkout*`, `*FinalizeWorkout*` y `*StartWorkoutConflict*`;
  - `com.example.kpkn.screens.sessioneditor.*`, por la gráfica compartida;
  - `com.example.kpkn.screens.home.*` y `com.example.kpkn.ui.*`;
  - la compilación de `androidTest`.
- Si una prueba de `screens/workout/` de la PR11 falla con la original restaurada, mira si ya fallaba en la PR11 (`git stash` NO: usa
  el log de la corrida de la PR11 si existe o dilo en el informe). No la «arregles» cambiando la conducta.
- Las tuyas de los arreglos del punto 2.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "com.example.kpkn.services.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.screens.home.*" --tests "com.example.kpkn.ui.*" > /c/kw/logs/screens-lr0-<n>.log 2>&1`

Las corridas intermedias pueden ir sin `:app:compileBaseDebugAndroidTestKotlin`; la final, con el comando entero.

## Commit
- `revert(workout): vuelve la sesión en vivo de la PR11 (el rediseño queda en archive/live-session-redesign-v1) (LR0)`.
- Un commit por cada arreglo de lógica portado: `fix(workout): … (LR0)`.
- `docs(workout): la especificación del rediseño queda archivada (LR0)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Cambiar el aspecto o la conducta de la sesión original (eso va en el refinado).
- `screens/sessioneditor/`, salvo lo justo para que compile con la gráfica compartida.
- Los archivos de la ola VT: `SetCardExerciseMediaBack`, `WorkoutMediaCaptureController`, `WorkoutMediaPreviewDialog` y
  `screens/workout/technique/`. Deben quedar como en la PR11, y LS nunca los tocó: si `git diff 8f0afc9dc` muestra cambios en
  ellos, PARA y avísame.
- `MainActivity.kt`, el teléfono, el emulador y push.

## Informe
- Qué restauraste, qué excepciones quedaron y por qué.
- Los arreglos de lógica portados (commit original → commit nuevo, con su prueba).
- Pruebas: número y resultado.
- La ruta del `.dbg`.
