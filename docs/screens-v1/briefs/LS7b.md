# LS7b · Sesión en vivo: hojas de contexto, opciones avanzadas de la serie y técnica (drop-sets, descanso y notas) (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LS0 a LS2b integrados; el mensaje dice qué más hay,
y si LS7a ya está). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md`, §0, §2 (punto 4), §6 y §8 (LS7): TU especificación;
- el mapa `C:\kw\analysis\maps\workout.md`, §2.1 («Hojas de contexto», «Ajuste de serie», «Reverso de la tarjeta»), §2.5 y §6. Las
  líneas son de ANTES: búscalas por nombre;
- el panel de la serie de LS2b (`design/WorkoutSetPanel.kt`) y su hoja «Técnica».

LS2b dejó la serie nueva, pero dos cosas siguen con la piel vieja y en el sitio provisional:
- **«Opciones avanzadas»:** se despliega debajo del panel con el contenido de la tarjeta vieja (ROM, repeticiones con ayuda o
  parciales, AMRAP, «Reportar serie»…). La decisión 101 dice que pase a una hoja cuando llegue este paquete;
- **la parte de técnica de la hoja «Técnica»:** `SetCardTechniqueBack` (drop-sets, descanso y notas) va tal cual.

Además están las hojas de contexto: `WorkoutContextComponents.kt` (etiquetas, ajuste de serie, historial y RM), `WorkoutExerciseSheets.kt`
(ultra rápido, RM…), `SetAdjustmentOverlay.kt` (`Card` y `Slider` de Material) y `GuidedTechniquePanel.kt` (un `AlertDialog` de
Material). La lógica NO cambia.

## Qué haces
1. **«Opciones avanzadas» en hoja:** el `KpknTextAction("Opciones avanzadas")` del panel abre una `KpknSheet` «Opciones de la serie».
   Dentro, el contenido avanzado de hoy revestido con piezas KPKN:
   - ROM con una regla KPKN en vez de `RomReportSlider`/`Slider`;
   - repeticiones con ayuda o parciales con `KpknStepperMetric` (LS1) o `KpknNumberCell`;
   - AMRAP (`AmrapConfigSheet`) con `KpknNumberCell` en vez de `OutlinedTextField`;
   - «Reportar serie» como fila de 56 dp.
   - El texto «Opciones avanzadas» se conserva (lo buscan las pruebas instrumentadas).
   - Si el estado de ese contenido vive en `SetInputCardV2` y no se puede sacar sin tocar la lógica, la hoja lo recibe de la tarjeta
     (estado elevado) en vez de copiarlo. Si ni así, explícalo y deja el desplegado.
2. **Técnica (drop-sets, descanso y notas)** (`SetCardTechniqueBack`), por dentro con piezas KPKN:
   - drop-sets como filas con `KpknNumberCell` (carga y repeticiones de cada drop) y «+ Añadir drop-set»;
   - el descanso con el selector KPKN;
   - las notas con `KpknTextField` de varias líneas.
   - Conserva «Añadir drop-sets», «Drop-sets» y «Saltar técnica y registrar solo la serie» (pruebas).
   - `GuidedTechniquePanel`: su `AlertDialog` pasa a `KpknAlertDialog` con los mismos textos.
3. **Hojas de contexto** (`WorkoutContextComponents.kt`, `WorkoutExerciseSheets.kt`, `SetAdjustmentOverlay.kt`): `KpknSheet` con
   módulos, `KpknChip`, `KpknSwitch`, `KpknNumberCell` y reglas KPKN. Fuera `Card`, `Slider`, `Surface` con color, `Icons.*`,
   `Color(0x…)` y tamaños sueltos. «Ultra rápido» muestra el ahorro de tiempo en `metricM` tabular.
4. **Lo que queda sin uso:** bórralo con Grep en todo `app/src`.
5. **Textos:** tipo frase, «…» de un carácter, sin MAYÚSCULAS escritas a mano, «Repeticiones» en lo largo. Antes de cambiar un texto,
   Grep en `src/androidTest`.

## Pruebas
- **Siguen en verde:**
  - las JVM de `screens/workout/` y `domain/workout/`;
  - `*OngoingWorkout*`, `*FinalizeWorkout*` y `*StartWorkoutConflict*`;
  - `*WorkoutSetPanel*` (LS2b);
  - la compilación de `androidTest`.
- **Robolectric** `WorkoutSetOptionsTest`, con los CUERPOS montados sueltos:
  - la hoja de opciones cambia el ROM y llama con el valor;
  - añadir un drop-set llama a añadir;
  - el ajuste de serie llama con su valor.
- **Hoja de render** `render/WorkoutSetOptionsRenderSheetTest`:
  - las opciones de la serie;
  - la técnica con dos drop-sets;
  - cada hoja de contexto con datos de ejemplo;
  - ultra rápido;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/ls7b`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls7b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*WorkoutSetPanel*" --tests "*WorkoutSetOptions*" > /c/kw/logs/screens-ls7b-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(workout): opciones de la serie en hoja, técnica y hojas de contexto en piezas KPKN (LS7b)`.
- Si borras piezas viejas, aparte: `refactor(workout): quita controles viejos de la tarjeta y de las hojas de contexto (LS7b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- **Los medios y el grabador** (`SetCardExerciseMediaBack`, `WorkoutMediaCaptureController`, `screens/workout/technique/`): los
  trabaja la ola VT en otra rama. No los toques, ni siquiera por fuera. La hoja «Técnica» sigue abriendo los medios como lo dejó LS4.
- La lógica de la sesión (`WorkoutViewModel` y sus controladores). Lo que falte, AL FINAL del VM.
- El cajón y las hojas de estructura (LS7a), la serie por fuera, el descanso, la ruta, la preparación y el resumen (LS2 a LS6).
- Las firmas de las piezas de LS1 a LS6: solo parámetros opcionales al final.
- `MainActivity.kt` y `screens/sessioneditor/`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Textos cambiados y pruebas actualizadas.
- Pruebas: número y resultado.
- PNG y crítica.
