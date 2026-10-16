# LV4 · Fallos graves: la sesión en negro al cerrar una hoja, reordenar no reordena y la auditoría de las opciones (puntos 15, 10.1 y 10.2)

Rama y worktree: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `f6da01f52`). Lee antes `LV_COMUN.md`.

## 15. Cerrar una hoja por accidente deja la sesión en negro
- **Lo que pasa:** el usuario cierra una hoja sin querer, con Atrás o tocando la zona de fuera, y la sesión se queda en negro, sin
  salida. Debe verse la sesión normal.
- **Qué haces:**
  1. **Reproduce el fallo en TODAS las hojas y capas de la sesión en vivo**, con Atrás y tocando fuera:
     - estado previo (readiness);
     - resumen final;
     - cockpit;
     - feedback del ejercicio y molestias;
     - las hojas de estructura (`WorkoutStructureSheetsHost`);
     - setup, editar series, etiquetas, historial y ultrarrápido;
     - las de `WorkoutSessionOverlaysHost` y `WorkoutOverlayHost`.
  2. **Encuentra la causa en el origen.** Por ejemplo: un estado de capa que no se limpia en `onDismiss`, un velo o fondo negro que
     queda sin contenido, o una `ProtectedWorkoutBottomSheet` que no avisa.
  3. **Comportamiento correcto:**
     - **Estado previo cerrado:** se ve la sesión. El estado previo queda como pendiente, se puede abrir después y nada se rompe.
     - **Resumen final cerrado:** se vuelve a la sesión con el cierre pendiente; no se pierde nada. Hay una forma visible de volver al
       resumen: el mismo botón de terminar.
     - **Cualquier otra hoja:** se cierra y deja la sesión como estaba.
- **Prueba:** una por hoja (Robolectric con Compose): Atrás o tocar fuera → el contenido de la sesión es visible y no queda ningún
  velo opaco.

## 10.1. Reordenar ejercicios no cambia el orden
- **Lo que pasa:** desde las opciones de un ejercicio (pulsación larga en el carrusel de abajo), el usuario reordena y el orden
  original se mantiene.
- **El camino a seguir:**
  1. `components/WorkoutRoadmapBar.kt`: `onReorderExercises` (~L121, ~L585);
  2. la hoja de reordenar de `WorkoutStructureSheetsHost`;
  3. el VM;
  4. `domain/workout/WorkoutStructuralEditor.reorderExercisesByIds` (~L74);
  5. `WorkoutStructuralPersistenceController.reorderSessionByCanonicalKeys` (~L400).
- **Qué haces:** encuentra dónde se pierde el orden y arréglalo en el origen.
- **Prueba:** tras reordenar, el orden nuevo se ve en:
  - el carrusel;
  - el navegador de pasos (qué ejercicio viene después);
  - la sesión guardada;
  - la sesión rehidratada después de recrear el proceso.

## 10.2. Auditoría de las opciones del ejercicio (pulsación larga en el carrusel)
- **Qué haces:**
  - lista TODAS las opciones de esa hoja;
  - para cada una, una prueba que la ejecuta y comprueba el efecto real: sustituir, saltar u omitir, eliminar, crear superserie,
    ultrarrápido, etiquetas, historial, notas, reordenar y las demás que haya;
  - arregla lo que falle, en su origen.
- **En el informe:** una tabla con opción → funciona / arreglado / roto (y por qué).
- **El diseño de la hoja no cambia** en este paquete; va con propuesta aparte.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.workout.*` y `com.example.kpkn.domain.workout.*`.
- Las nuevas: una por hoja (15), las de reordenar (10.1) y una por opción (10.2). Cada una tiene que fallar sin su arreglo. Si no
  puedes comprobarlo, dilo.

## Permitido y prohibido
- **Permitido:**
  - `screens/workout/`, salvo lo de abajo;
  - `domain/workout/`;
  - pruebas.
- **Prohibido** (otros paquetes trabajan a la vez):
  - `WorkoutHeaderBar.kt` y `components/WorkoutReadinessSheet.kt` en lo visual (LV1). Arreglar su cierre está permitido: toca lo
    mínimo y avísalo;
  - `WorkoutLiveRelator*.kt` (LV2);
  - `components/SetExecutionCard.kt` y las flechas de `WorkoutSetPager.kt` (LV3);
  - el diseño del cockpit y del modo dios (van con propuesta aparte);
  - push;
  - el teléfono y el emulador.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" > /c/kw/logs/lv4-<n>.log 2>&1`

Tope: 5 corridas.

## Commits
- `fix(workout): cerrar una hoja por accidente deja la sesión a la vista (LV4)`.
- `fix(workout): reordenar ejercicios aplica el orden nuevo en el carrusel, los pasos y lo guardado (LV4)`.
- `fix(workout): <cada opción arreglada> (LV4)`.
