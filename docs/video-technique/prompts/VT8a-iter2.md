Eres el agente que continúa el paquete **VT8a** (grabador de técnica a pantalla completa) de **KPKN Fit**, una app Android en Kotlin con Jetpack Compose. Esta es la **iteración 2**: la revisión del orquestador encontró fallos en la iteración 1 y aquí van, con lo que hay que hacer en cada uno.

## Dónde y con qué reglas
- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt8a`, que ya está en checkout.
  - Ya tiene los 4 commits de la iteración 1: `5d55f315f`, `b9f34abf7`, `53141e29b` y `c3dedca47`.
  - Añade commits NUEVOS encima. Nada de `amend`, `rebase` ni `reset`.
- **Lee primero:**
  - `docs/video-technique/prompts/VT8a.md`: el prompt de la iteración 1. TODAS sus reglas siguen valiendo (Gradle solo por `gradle_slot.sh`, el emulador solo por `emu_run.py`, Git, archivos permitidos, disco, informe…);
  - `docs/video-technique/briefs/VT8a.md`: el brief;
  - el código de la iteración 1 que vas a corregir.
- **Logs** en `C:\kw\logs\vt-8a-2-<n>.log` y **PNG** en `C:\kw\shots\vt\8a\iter2\`.

## Imprescindible (sin esto no se integra)
1. **Fuera la grabación simulada.** Hoy, sin cámara enlazada, `TechniqueRecorderScreen` «simula» una grabación: copia `R.raw.welcome_entreno` en `filesDir/workout_media/simulated_technique.mp4` y escribe su `capture.json`. Es un vídeo falso en el código de producción: en un móvil sin permiso de cámara, o con la cámara ocupada, el usuario «grabaría» el vídeo de bienvenida.
   - Bórralo entero: `isSimulatedRecording`, `simulatedElapsedNanos`, la copia del recurso y todo lo que cuelga de ahí.
   - **Estado sin cámara, en su lugar.** El botón REC queda desactivado y se muestra un mensaje claro sobre la vista previa, con las piezas KPKN v2 (no Material):
     - **sin permiso:** «Permite la cámara para grabar», con un botón que vuelve a pedirlo; si se denegó para siempre, otro que abre los ajustes de la app;
     - **sin cámara o con fallo al enlazar:** «No se pudo abrir la cámara», con «Cerrar».
2. **Fuera los registros en archivo.** Borra todas las escrituras a `recorder_log.txt` (`getExternalFilesDir("vt-recorder")`) del código de producción. Los errores, con `Log.w`/`Log.e` y un TAG; nada más. La exportación del banco de depuración (`src/debug`) puede quedarse.
3. **El roll de la gravedad sale 180° girado.**
   - Hoy es `atan2(gx, -gy)`: con el móvil derecho da ≈ 180° (el emulador escribió 179,97).
   - `TrackPreparation` aplica el roll si pasa de un par de grados, así que TODOS los vídeos del grabador se analizarían con la pose girada 180°.
   - **Corrígelo** con una función pura y probada, `rollForFrameDeg(gx, gy, targetRotation): Float?`:
     - el roll respecto al fotograma GRABADO es `wrap(atan2(gx, gy) − grados(targetRotation))`, con ROTATION_0/90/180/270 = 0/90/180/270 y `wrap` a (−180, 180];
     - el convenio es el de VT1b (`FrameGeometry.rollDeg`): positivo si el contenido se ve girado en sentido horario, es decir, el móvil girado en sentido antihorario.
     - Si |roll| > 45°, devuelve `null`: eso no es una inclinación, es otra orientación.
   - **Pruebas obligatorias** (g = 9,81):
     - (0, g) con ROTATION_0 → 0;
     - (g·sin 10°, g·cos 10°) con ROTATION_0 → +10;
     - (−g·sin 10°, g·cos 10°) → −10;
     - (g, 0) con ROTATION_90 → 0;
     - (−g, 0) con ROTATION_270 → 0;
     - (0, −g) con ROTATION_180 → 0;
     - (0, −g) con ROTATION_0 → null.
   - **Comprueba la tabla de `OrientationEventListener` → `targetRotation`** contra la documentación de CameraX, y dilo en el informe. La rotación que cuenta es la que tenía `videoCapture.targetRotation` al empezar a grabar.
   - El pitch se queda como está (≈ 90° en vertical).
4. **Parar al salir.** Si el grabador se cierra o se va de la composición mientras graba (el `DisposableEffect` de `TechniqueRecorderScreen`), hay que llamar a `stopVideo()`. Hoy solo baja `cameraBusy` y restaura la orientación, así que la grabación seguiría sin pantalla hasta el límite de duración.

## Corrige también
5. **El perfil del ejercicio en el grabador.**
   - Hoy se arma un `ExerciseRef` a mano con `equipmentId = null` e `isCustom = false`. Con eso una sentadilla con barra no se reconoce como perfil con disco: en el emulador no salió la insignia «Disco 45 cm», y el encuadre no pide ver el disco.
   - Resuélvelo como el pipeline: `ExerciseRefResolver` (VT4c), con el catálogo y los ids del ejercicio. Hazlo FUERA del hilo principal (por ejemplo, `produceState` con `Dispatchers.IO`). Hasta que llegue, el grabador funciona sin insignias.
6. **Los ajustes de verdad.**
   - `WorkoutScreen` pasa hoy `prefer60FpsSetting = true` fijo, y el límite de 180 s y el disco de 450 mm también están fijos.
   - Lee `Settings.techniqueVideo` (`prefer60Fps`, `maxRecordSeconds` y `plateDiameterMm`) como lo hace el resto de la pantalla, y pásalo al grabador.
   - **El diámetro que elija el usuario en el grabador:**
     - se guarda para ESE vídeo en su `capture.json` (clave `plateDiameterMm`) con `CaptureOverridesFile.update`, el mismo escritor que usa VT5b, y solo si es distinto del de los ajustes;
     - quita «Otro» del grabador y deja 45, 40 y 35: el diámetro a medida ya está en la calibración de la pantalla de análisis.
7. **La focal en píxeles.**
   - `calculateFocalLengthPx` usa un `recordedLongEdge = 1920f` fijo. Con el selector HD→SD el vídeo puede salir a 1280 o a 720, y la focal saldría mal por 1,5–2,7×.
   - Usa el tamaño REAL del archivo grabado: lee ancho y alto del vídeo ya finalizado, fuera del hilo principal.
   - Saca la cuenta a una función pura, `focalPx(focalMm, sensorLongEdgeMm, videoLongEdgePx)`, con su prueba.
8. **E/S fuera del hilo principal.**
   - En `Finalize`, `metadataRecorder.save(dest)` escribe el archivo en el hilo principal.
   - Pásalo a `Dispatchers.IO` dentro de la corrutina del controlador.
   - Mantén el orden: primero `capture.json` y DESPUÉS `submitFinalizedVideoCapture`, para que el análisis ya lo encuentre.
9. **Disposición.**
   - En `emu_idle.png` la línea guía («De lado · cuerpo entero con margen…») queda debajo del anillo del botón REC.
   - Separa la guía del botón, respetando el área segura inferior.
   - Añade a la hoja de render la PANTALLA ENTERA del grabador, a 360 dp, al 100 % y al 130 %, en tres estados: reposo con guía y chips, grabando con el cronómetro, y sin cámara o sin permiso. Las piezas sueltas no detectan solapes.
10. **La silueta de la guía.**
    - La de lado está mal formada: el brazo sale de la cabeza hasta el muslo y hay una sola pierna.
    - Dibuja una figura de líneas coherente:
      - de lado: cabeza, cuello, tronco, un brazo y las dos piernas un poco desplazadas;
      - de frente: simétrica.
    - Tinta al 25 % y discreta: es una guía, no un dibujo.
    - Mira los PNG y critícalos con el listón de VT_COMUN (máximo 3 rondas).

## Pruebas y corridas
- **Pruebas JVM nuevas:**
  - `rollForFrameDeg` (punto 3);
  - `focalPx` (punto 7);
  - el estado sin cámara y sin permiso, en la lógica del estado de la pantalla;
  - el diámetro: solo se escribe si cambió, y conserva las otras claves de `capture.json`.
- **Corridas:** las 5 del brief. La 2 lleva además `--tests "com.example.kpkn.data.work.*"`.
- **Emulador:**
  - El emulador compartido (`Pixel_9_Pro_XL`) NO tiene cámara (`hw.camera.back = none`). No lo reinicies ni cambies su configuración: lo usan otras sesiones.
  - La grabación real se prueba más adelante, en el QA final y en el teléfono.
  - Comprueba solo esto:
    - el modo `recorder_preview` muestra el estado «sin cámara» sin caerse (`--crash-log` limpio);
    - no se crea ningún archivo en `workout_media`;
    - captura `emu_nocamera.png`.

## Informe final
- Es el formato de VT_COMUN, en un máximo de 400 palabras.
- **Para cada uno de los 10 puntos:** qué cambiaste, en qué commit, y qué prueba o captura lo demuestra.
- **Corridas y PNG:** cada corrida con su log y su línea de resultado, más las rutas de los PNG.
