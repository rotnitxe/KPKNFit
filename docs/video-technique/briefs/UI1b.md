# UI-1b · Modo visual: el grabador de técnica dentro de la vista expandida (G3)

- **Rama:** `vt/ui1b` en `C:\kw\vt-a`, que sale de `feat/vt-ui` con UI-1a integrado.
- **Logs:** `C:\kw\logs\vt-ui1b-<n>.log`.
- **PNG:** `C:/kw/shots/vt/ui/1b`.
- **Antes de empezar, lee:**
  - `UI_COMUN.md` (y lo que cita) y `docs/video-technique/UI_PLAN.md` (D36);
  - el prototipo `G3-ModoVisualExpandido.dc.html` y `A3-ModoVisual.dc.html`.
- **Código** (léelo antes de editar):
  - `screens/workout/components/SetCardExerciseMediaBack.kt`:
    - `onShutter`, `expanded` y `cameraActive`/`recorderOpen`;
    - `MediaStage`;
    - `ExpandedMediaOverlay` (~L925), con su crecimiento desde `faceBounds`, el velo, el botón de plegar y `BackHandler`;
  - `screens/workout/technique/TechniqueRecorderScreen.kt`, entero;
  - `TechniqueRecorderHolder.kt`;
  - `screens/workout/components/SetExecutionCard.kt` (~L3285-3313: `onOpenRecorder` pone `cardFace = SetCardFace.Front`);
  - `screens/workout/WorkoutScreen.kt`: el anfitrión del grabador (~L1003 y ~L1431-1441) y `settings` (~L349);
  - `WorkoutV2Body.kt` (`techniqueRecorderHolder`, ~L121);
  - el banco de depuración `src/debug/.../TechniqueBenchActivity.kt`, en su modo `recorder_preview`.

## Por qué
Hoy, en «Vídeo», el botón de grabar abre otra pantalla completa y negra, con su propia cabecera y una «×». Al terminar, la tarjeta vuelve
al anverso. Se siente como salir de la sesión. Lo aprobado (A3, G3 y la ley 1) es otra cosa: el Modo visual cabe en la tarjeta y
«Expandir» lo lleva a casi toda la pantalla, ENCIMA de todo, creciendo desde la tarjeta. El grabador de técnica tiene que ser ESA vista
expandida.

## Qué haces

### 1. Separa el contenido del grabador
- Del cuerpo de `TechniqueRecorderScreen` sale un composable nuevo, `TechniqueRecorderStage(...)`. Lleva todo lo que hoy hay dentro del
  `Dialog`, salvo la barra superior y la «×»:
  - el enlace de CameraX con `RecorderSessionFactory`, con los 60 fps cuando el móvil los garantiza;
  - `CaptureMetadataRecorder` (`startPreview`/`start`/`stop`);
  - `FramingCheckController` con sus chips y la silueta guía;
  - las insignias de 60 fps y del disco, con el selector 45, 40 y 35;
  - REC con el cronómetro y el límite de duración;
  - el aviso sin cámara o sin permiso;
  - el bloqueo de la orientación mientras graba;
  - parar al salir.
- **Firma, como mínimo:**
  `TechniqueRecorderStage(request, mediaCapture, prefer60FpsSetting, maxRecordSeconds, defaultPlateDiameterMm, onFinished: () -> Unit, onSwitchToPhoto: () -> Unit, onOpenFicha: (() -> Unit)?, modifier)`.
  - `onFinished` se llama al guardar, después de `Finalize`.
- `TechniqueRecorderScreen` se queda como envoltorio fino (`Dialog` + su barra + `TechniqueRecorderStage`). Solo lo usa el banco de
  depuración.

### 2. En el Modo visual, el vídeo se graba en la vista expandida
- **Estado nuevo** en `SetCardExerciseMediaBack`: `techniqueOpen`.
- **Al abrir:** en «Vídeo», el botón redondo de la tarjeta y «Expandir» hacen las dos cosas: `techniqueOpen = true` y `expanded = true`.
  - Ya NO llaman a `onOpenRecorder`.
  - En «Foto» todo sigue igual.
- **La escena:** `ExpandedMediaOverlay` (la misma, con su crecimiento desde la tarjeta, su velo, su cabecera «Modo visual · serie N» con
  «95 kg × 6 · RIR 2» y su botón de plegar) pinta `TechniqueRecorderStage` como escena cuando `techniqueOpen`.
- **Disposición de la escena del grabador**, dentro de la escena con esquinas de 24 dp, como en G3:
  - arriba a la izquierda, el segmentado «Foto | Vídeo», con «Vídeo» marcado. «Foto» llama a `onSwitchToPhoto`: pliega, deja
    `techniqueOpen = false` y la tarjeta queda en «Foto»;
  - arriba a la derecha, «Ficha», como en el Modo visual, y debajo las insignias pequeñas (60 FPS · Disco 45 cm);
  - debajo, centrado, el chip de encuadre («✓ Encuadre», «Aléjate: no se ven los pies»…);
  - en el centro, la silueta guía, tenue (25 %);
  - una franja inferior con la guía de texto («De lado · cuerpo entero con margen…»), sobre `Color.Black` al 55 % y con esquinas de 16 dp.
    NUNCA se monta sobre el botón;
  - abajo a la izquierda, «Ver álbum»; abajo a la derecha, el botón de grabar rojo del Modo visual (`RecordRed`), con el cronómetro en
    píldora encima mientras graba;
  - sin cámara o sin permiso, el aviso KPKN v2 ocupa la escena y el botón queda desactivado.
- **La cámara:** con `techniqueOpen`, la cámara de la tarjeta (`MediaCameraSession`) se suelta antes de que el grabador enlace la suya.
  Usa la misma condición que `recorderOpen` (`cameraActive = false`). Al cerrarse, la de la tarjeta vuelve. Comprueba que no quedan dos
  enlaces a la vez: el grabador ya llama a `unbindAll()` antes de enlazar.
- **Al terminar** (`onFinished`) o al plegar:
  - la vista se pliega hacia la tarjeta (la misma animación) y `techniqueOpen = false`;
  - la tarjeta SIGUE en el Modo visual, NO vuelve al anverso: así se ve la miniatura nueva con su insignia «analizando» (UI-1a);
  - plegar mientras graba primero para la grabación, como hoy hace `BackHandler`.
- **Los ajustes** (`prefer60Fps`, `maxRecordSeconds` y `plateDiameterMm` de `settings.techniqueVideo`) llegan a
  `SetCardExerciseMediaBack` como último parámetro (`techniqueVideoSettings`), por `WorkoutV2Body` → `SetInputCardV2`.

### 3. Fuera el camino viejo
- **Anfitrión del grabador:** quita el de `WorkoutScreen` (~L1431-1441) y el `TechniqueRecorderHolder` de `WorkoutScreen`,
  `WorkoutV2Body` y `SetInputCardV2`.
- **`onOpenRecorder` y `recorderOpen`:** quita su uso desde `SetExecutionCard`, con su `cardFace = SetCardFace.Front`. Si
  `SetCardExerciseMediaBack` ya no los necesita, quítalos de su firma y de las pruebas que los pasen.
- **Lo que se queda:** `TechniqueRecorderRequest`, que lo usa el banco de depuración, y el modo `recorder_preview`, que sigue
  compilando y funcionando.
- **`TechniqueRecorderHolder.kt`:** si nadie más lo usa, se borra.

## Archivos permitidos
- `screens/workout/technique/TechniqueRecorderScreen.kt`, `TechniqueRecorderStage.kt` (nuevo) y `TechniqueRecorderHolder.kt` (borrar o
  dejar).
- `screens/workout/technique/design/*`, solo si una pieza necesita un parámetro opcional al final para encajar en la escena.
- `screens/workout/components/SetCardExerciseMediaBack.kt` y `screens/workout/components/SetExecutionCard.kt`: solo el paso de
  parámetros y quitar `onOpenRecorder`, `recorderOpen` y el holder.
- `screens/workout/WorkoutV2Body.kt` y `screens/workout/WorkoutScreen.kt`: solo el paso de ajustes y quitar el anfitrión viejo.
- `src/debug/.../TechniqueBenchActivity.kt`: solo lo justo para que `recorder_preview` siga compilando.
- Pruebas:
  - `render/TechniqueRecorderRenderSheetTest.kt` y `render/LiveCardRenderSheetMediaTest.kt` (escenas nuevas);
  - las de `screens/workout/components/` del Modo visual y `screens/workout/technique/`.

## Pruebas
- **Robolectric** (`SetCardExerciseMediaBack` con `showControlsWithoutCamera = true` y en «Vídeo»):
  - pulsar el botón redondo deja visibles `MediaExpandedTag` y una etiqueta nueva de la escena del grabador
    (`TechniqueRecorderStageTag`), y NO llama a ningún `onOpenRecorder`;
  - «Foto» en la escena pliega y deja la tarjeta en «Foto».
- **Hoja de render**, a 360 dp, al 100 % y al 130 %:
  - la vista expandida del grabador en reposo con «✓ Encuadre»;
  - la misma grabando, a 0:12;
  - sin permiso;
  - la tarjeta en el Modo visual justo después de grabar, con la miniatura nueva «analizando» (UI-1a).
  - Compara la cabecera y los bordes con la vista expandida de fotos: deben ser la misma pieza.

## Corridas
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.technique.*" --tests "com.example.kpkn.screens.workout.components.*Media*" --tests "com.example.kpkn.screens.workout.components.SetExecutionCardFacesTest" --tests "com.example.kpkn.screens.workout.*VisualMode*"`
3. Las hojas: `KPKN_RENDER_DIR=C:/kw/shots/vt/ui/1b` con `--tests "com.example.kpkn.render.TechniqueRecorderRenderSheetTest" --tests "com.example.kpkn.render.LiveCardRenderSheetMediaTest"`.
4. `-I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug`, para que el orquestador lo pruebe con la cámara del emulador.

## Commits
- `refactor(technique): el grabador como escena reutilizable`
- `feat(technique): grabar en la vista expandida del Modo visual, sin salir de la sesión`
- `test(technique): hojas del grabador dentro del Modo visual`
