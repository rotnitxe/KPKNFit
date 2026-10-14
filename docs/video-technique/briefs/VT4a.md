# VT4a · `VideoFrameSource`: decodificación secuencial con EGL y su arnés en el emulador

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt4a`, desde `feat/video-technique` con VT-A integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3.7 (bloque `data/media/video`) y §9 punto 2;
  - el arnés que sirve de patrón, `src/debug/java/com/example/kpkn/debug/ImportOcrHarnessActivity.kt`, con su entrada en `src/debug/AndroidManifest.xml`.
- **Logs:** `C:\kw\logs\vt-4a-<n>.log`.

El analizador necesita TODOS los fotogramas del vídeo, en orden, ya girados y a ~720 px, con PTS reales. Hoy se usa `MediaMetadataRetriever.getScaledFrameAtTime` a 10 fps, que busca cada fotograma por separado: es lento e impreciso. Esto lo sustituye.

## Archivos permitidos
- **Código** (`data/media/video/`, nuevos):
  - `VideoInfo.kt`: `VideoInfo`, `FrameNeeds` y `DecodedFrame`;
  - `VideoFrameSource.kt`: la interfaz y `VideoFrameSources.open`;
  - `VideoInfoProbe.kt`;
  - `GlVideoFrameSource.kt`, con sus clases internas: EGL, el dibujante OES→FBO y el lector;
  - `RetrieverVideoFrameSource.kt`;
  - `VideoFrameMath.kt`: tamaños de salida, rotación y luma, puro y probado en JVM.
- **Arnés de depuración:**
  - `src/debug/java/com/example/kpkn/debug/TechniqueBenchActivity.kt`, con el modo `decode_selftest`; VT4b le añadirá modos;
  - `src/debug/java/com/example/kpkn/debug/SyntheticClipEncoder.kt`;
  - su `<activity android:exported="true" …>` en `src/debug/AndroidManifest.xml`.
- **Pruebas:** `src/test/java/com/example/kpkn/data/media/video/VideoFrameMathTest.kt`.

## Qué haces
1. **Contrato** (§3.7):
   - `VideoInfo`, `FrameNeeds` y `DecodedFrame`;
   - `VideoFrameSource.decode(fromUs, targetLongEdge, needs, onFrame)`, que es `suspend` y cancelable;
   - `VideoFrameSources.open(file)`: GL primero; con `RetrieverVideoFrameSource` si GL falla al abrir o con `CodecException` no transitoria repetida.
2. **`VideoInfoProbe`** (MediaExtractor + MediaMetadataRetriever):
   - pista de vídeo, mime, ancho y alto codificados, `KEY_ROTATION` y el tamaño de display (girado);
   - duración;
   - fps nominal: `KEY_FRAME_RATE` o, si falta, la mediana de los PTS de las primeras 60 muestras;
   - `captureFps`: `METADATA_KEY_CAPTURE_FRAMERATE`, en API 23+;
   - `isHdr`: transfer ST2084 o HLG.
3. **`GlVideoFrameSource`**, con el patrón bigflake «ExtractMpegFramesTest»:
   - **Contexto GL:** EGL14 con pbuffer 1×1, GLES 2.0, textura OES y un FBO del tamaño de salida.
     - Salida: display escalado a `targetLongEdge` en el lado largo, con el otro lado par y la proporción conservada.
   - **Formato al configurar:**
     - `KEY_ROTATION` a 0 antes de `configure()`; la rotación se aplica en la matriz del vértice;
     - `KEY_PRIORITY = 1`;
     - `KEY_OPERATING_RATE = Short.MAX_VALUE.toInt()`: si `configure` falla con él, reintenta sin él;
     - HDR con API 31+: `KEY_COLOR_TRANSFER_REQUEST = MediaFormat.COLOR_TRANSFER_SDR_VIDEO`.
   - **Bucle:** salida a la Surface de un `SurfaceTexture`, con `setOnFrameAvailableListener` en un `HandlerThread` propio.
     - Un buffer con `render = true` → `awaitNewImage(2500 ms)` → `updateTexImage()` → `getTransformMatrix()` → dibujar con la Y invertida → `glReadPixels` a un `ByteBuffer` directo RGBA reutilizado.
     - Con `FrameNeeds.SKIP`: `releaseOutputBuffer(i, false)`, sin dibujar.
     - LUMA: `(77r + 150g + 29b) shr 8`, en un `ByteArray` reutilizado.
   - **Tiempos:** los de `BufferInfo.presentationTimeUs`; si uno no es creciente, se repara al anterior + 1.
   - **`fromUs > 0`:** `seekTo(fromUs, SEEK_TO_PREVIOUS_SYNC)` y descartar hasta `fromUs`.
   - **Hilos y cancelación:**
     - todo lo de GL vive en un hilo propio (`newSingleThreadContext` o un `HandlerThread` con su despachador);
     - `ensureActive()` en cada fotograma;
     - todo se libera en `finally`, en ese hilo.
   - **Pool:** 3 buffers RGBA, y `DecodedFrame` solo es válido dentro de `onFrame`.
   - **Decodificador 4K/60:** se elige con `MediaCodecList(REGULAR_CODECS)` y `areSizeAndRateSupported`, prefiriendo hardware.
4. **`RetrieverVideoFrameSource`:**
   - con API 28+, `getFramesAtIndex` en bloques de 30;
   - más antiguo, `getScaledFrameAtTime(OPTION_CLOSEST)` a ≤ 15 fps;
   - convierte el Bitmap a RGBA y luma con los mismos tamaños;
   - los PTS salen del índice y del fps nominal.
5. **Arnés `TechniqueBenchActivity`**, en modo `decode_selftest` (`--es mode decode_selftest`):
   1. **Genera** con `SyntheticClipEncoder` (MediaCodec AVC, entrada por Surface y GLES, más MediaMuxer, como bigflake «EncodeAndMuxTest») 4 clips de 320×240 en `getExternalFilesDir("vt-selftest")`, con un disco blanco de radio 20 sobre negro que recorre una trayectoria conocida:
      - (a) 30 fps y 3 s;
      - (b) 60 fps y 3 s;
      - (c) 30 fps con `setOrientationHint(90)`;
      - (d) VFR: PTS alternos de +25 y +41 ms.
   2. **Decodifica** cada clip con `VideoFrameSources.open`, con `targetLongEdge = 320` y RGBA_AND_LUMA, y además con el respaldo forzado.
   3. **Mide** por clip, con el centroide de la luma > 128:
      - fotogramas esperados y decodificados;
      - PTS que no coinciden con los del muxer;
      - error máximo del centroide en px, con la rotación aplicada a la verdad;
      - tamaño de salida;
      - camino (GL o retriever);
      - ms por fotograma.
   4. **Escribe** `vt-selftest/result.json`, muestra «Listo» y deja una línea en logcat con la etiqueta `KpknTechniqueBench`.

   Los errores se escriben en el JSON y nunca tumban la actividad.

## Pruebas
- **JVM, `VideoFrameMathTest`:**
  - tamaño de salida para 1920×1080 con rotación 90 → 405×720 (pares);
  - la luma de colores puros;
  - la matriz de rotación aplicada a un punto.
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.data.media.video.*"`
  3. `-I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug`
- **Emulador** (autorizado en este paquete; sin `adb` suelto):
  - si no está arriba, `python C:\kw\tools\emu_start.py`;
  - después:
    ```
    python C:\kw\tools\emu_run.py --apk C:\kw\vt-a\android-native\app\build\outputs\apk\base\debug\app-base-debug.apk ^
      --component com.example.kpkn.dbg/com.example.kpkn.debug.TechniqueBenchActivity --extra "--es mode decode_selftest" ^
      --out C:\kw\shots\vt\4a --tag selftest --wait-file vt-selftest/result.json --wait-secs 300 ^
      --pull-dbg vt-selftest=C:\kw\shots\vt\4a\selftest --crash-log
    ```
    (comprueba el nombre exacto del APK en `outputs/apk/base/debug/`).
  - **Aceptación con GL:**
    - fotogramas exactos;
    - PTS exactos;
    - en (c), tamaño girado y centroide a ±1,5 px;
    - en (d), los PTS reales;
    - ms/fotograma anotados.
  - **Respaldo:** que funcione, con una tolerancia de ±3 px y menos fotogramas.
  - **Si el emulador falla por motivos ajenos** (GPU del emulador), anótalo con el registro del crash y entrega igual las pruebas JVM: la validación en el teléfono queda pendiente.

## Commits
- `feat(technique): decodificación secuencial de vídeo con EGL (VideoFrameSource) y respaldo`
- `test(technique): arnés de depuración con autoprueba de decodificación`
