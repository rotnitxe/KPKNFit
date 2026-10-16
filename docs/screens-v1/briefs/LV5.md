# LV5 · Fallos de reemplazar ejercicio y de editar series en vivo, y su auditoría (bugs 1–4 del usuario)

Rama y worktree: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `145d8e523`). Lee antes `LV_COMUN.md`.

El usuario encontró cuatro fallos y pidió revisar que no haya más en esos flujos ni en los relacionados.

## Bug 1. «Reemplazar» abre la ficha en vez del catálogo
- **Lo que pasa:** al reemplazar un ejercicio en la sesión en vivo, la app abre la FICHA del ejercicio.
- **Lo que debe pasar:** se abre el CATÁLOGO de reemplazo con el ejercicio actual como punto de partida:
  - el ejercicio actual se ve arriba como «Reemplazando: <nombre>», o seleccionado;
  - la lista ya filtrada o ordenada por su patrón, para empezar con contexto;
  - la ficha sigue a un toque desde el catálogo, como siempre.
- **El camino a seguir:**
  - `WorkoutScreen.kt` ~L801-811, ~L1064, ~L1100-1108 y ~L1353-1361 (`OPEN_REPLACE`);
  - `WorkoutStructureSheetsHost.kt` ~L135, ~L300 y ~L343-355 (`showReplaceExercisePicker`, `replaceTargetExerciseId`).
- **Ojo:** puede que el relator (`RelatorAssistActionKind.OPEN_REPLACE`) entre por otro camino. Los dos tienen que llegar al catálogo.

## Bug 2. Variante técnica preseleccionada con una imagen equivocada
- **Lo que pasa:** al reemplazar (y también en el catálogo normal), sale preseleccionada una variante técnica, por ejemplo un
  implemento, cuya imagen no le corresponde.
- **Qué haces:**
  - busca la causa en la selección de variante por defecto y en cómo se resuelve su imagen;
  - mira `ExercisePickerV2Catalog`, `CatalogSelectionWizard`, `screens/sessioneditor/components/ExercisePickerSheet.kt` y la
    resolución de imagen (`ExerciseTechniqueImageLookup`, `ExerciseCatalogV2Resolver`);
  - la variante por defecto debe ser coherente: la del ejercicio actual si existe, o la primera del catálogo;
  - la imagen siempre es la de la variante que se ve.
- **Prueba:** al menos tres ejercicios con varias variantes. La imagen preseleccionada es la de su variante.

## Bug 3. Reemplazar dos veces hereda la configuración del primero
- **Lo que pasa:** si reemplazas A por B y luego B por C, C se queda con la configuración de A: series, modo, cargas, carga
  inteligente, etc.
- **Qué haces:**
  - revisa `WorkoutStructuralPersistenceController.replaceExercise` (~L633) y el estado que guarda la pantalla entre reemplazos;
  - al reemplazar, el ejercicio nuevo hereda solo lo que el producto define (las series planificadas del hueco: cuántas, el descanso
    y la intensidad objetivo) y NUNCA datos del ejercicio que había antes del anterior;
  - si el ejercicio nuevo tiene historial propio, la carga sugerida sale de su historial.
- **Prueba:** A → B → C. C tiene lo que dice la regla, no lo de A ni lo de B.

## Bug 4. Editar los valores de una serie en vivo no aplica todos los cambios
- **Lo que pasa:** al cambiar el tipo de carga (externa, corporal, lastre o asistido) se vuelve al tipo que había. Lo mismo con la
  carga inteligente.
- **Qué haces:**
  - recorre los caminos de edición de una serie en vivo: el setup de la serie, «Editar series», el reverso «Avanzado» y la hoja de
    tipo de serie;
  - busca qué pisa la edición. Sospechosos:
    - la normalización de `WorkoutEditingRules.kt` ~L280 (`loadModeV2 ?: LoadModeV2.LOAD`);
    - `WorkoutViewModel.kt` ~L1297, ~L2828 y ~L3433;
    - `WorkoutStructuralPersistenceController.kt` ~L103, ~L310 y ~L891;
    - `WorkoutStructureSheetsHost.kt` ~L1687 (`lastSet?.loadModeV2`);
    - una rehidratación que vuelve a leer el plan;
  - cada edición se aplica entera y sobrevive a:
    - pasar de serie;
    - registrar;
    - salir y volver a la sesión (rehidratar).

## Auditoría de los flujos relacionados
- Además de los cuatro, revisa los flujos vecinos:
  - reemplazar desde el relator y desde las opciones del ejercicio;
  - reemplazar dentro de una superserie;
  - deshacer un reemplazo;
  - editar series de una serie ya registrada;
  - cambiar el modo (Reps, RM, Tiempo…);
  - la carga inteligente (meta y referencia);
  - unilateral;
  - añadir y quitar series en vivo.
- Anota cada fallo nuevo que encuentres, arréglalo en su origen y ponle prueba.
- **En el informe:** una tabla con flujo → funciona / arreglado / roto (y por qué).

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.workout.*`, `com.example.kpkn.domain.workout.*` y
  `com.example.kpkn.screens.sessioneditor.*` (el selector se comparte).
- Las nuevas: una por bug y una por cada fallo nuevo. Tienen que fallar sin el arreglo; si no puedes comprobarlo, dilo.

## Permitido y prohibido
- **Permitido:**
  - `screens/workout/` (salvo lo de abajo);
  - `domain/workout/`;
  - el selector y el catálogo (`screens/sessioneditor/components/ExercisePicker*`, `CatalogSelectionWizard`, `design/Catalog*`), y la
    resolución de imagen y variantes en `data/exercises` y `domain/exercises`, solo para los bugs 1 y 2;
  - pruebas.
- **Prohibido** (otros paquetes trabajan a la vez):
  - `WorkoutHeaderBar.kt` y `components/WorkoutReadinessSheet.kt` (LV1);
  - `WorkoutLiveRelator*.kt` (LV2);
  - `components/SetExecutionCard.kt` en lo visual y las flechas de `WorkoutSetPager.kt` (LV3). La LÓGICA de edición de la serie sí
    se puede tocar: toca lo mínimo y avísalo;
  - las hojas de cierre y de reordenar (LV4);
  - push;
  - el teléfono y el emulador.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "com.example.kpkn.screens.sessioneditor.*" > /c/kw/logs/lv5-<n>.log 2>&1`

Tope: 5 corridas.

## Commits
- `fix(workout): reemplazar abre el catálogo con el ejercicio actual como contexto (LV5)`.
- `fix(catalog): la variante preseleccionada y su imagen coinciden (LV5)`.
- `fix(workout): reemplazar dos veces no hereda la configuración del primer ejercicio (LV5)`.
- `fix(workout): editar una serie en vivo conserva el tipo de carga y la carga inteligente (LV5)`.
- `fix(workout): <cada fallo nuevo de la auditoría> (LV5)`.
