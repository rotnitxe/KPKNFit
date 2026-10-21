# Photo Finish – Automatic Timing 5.2.2 (Android) · ingeniería inversa estática (versión corta)

Fecha: 2026-10-21. Alcance recortado por instrucción del usuario: solo lo que no está en los demás informes de `re/` (cruce de línea,
sincronía de varios móviles y precisión del tiempo). El análisis completo, hecho antes del recorte (pantallas, catálogo, algoritmos,
brechas; 73 KB), queda fuera del repo en `C:\kw\re\evidencias\photofinish-re-20261020\PHOTO_FINISH_RE_COMPLETO.md`. Sala limpia: nada se
ejecutó ni instaló, sin código copiado ni modelos extraídos, y sin reproducir claves de SDK. Lo no verificado lleva «sin confirmar».

## 1. Identidad

| Dato | Valor |
|---|---|
| Paquete · versión | `com.ultrajuicy.photofinish` · 5.2.2 (versionCode 568) · minSdk 24 · targetSdk 36 |
| XAPK | `Photo+Finish_+Automatic+Timing_5.2.2_APKPure.xapk`, 37 949 948 B, SHA-256 `b28d50706cb1fe7dbb03233e5ca4075baa74425027b01de8d9db33cd570dc9fd` |
| APK internos | base `503253f4949de0bcf45ac36ca17a9baa79c3d729fbbd4a8847ef74c1120afc3a` · `config.armeabi_v7a` `96e1fd9cc4c2c99027f1d9159e2e50a153c6f05258c4779ad645ad714801318d` · los otros 18 splits en `xapk_inventory.json` |
| Arquitectura | Flutter 3.38 / Dart 3.10.7 AOT sin ofuscar; cámara CameraX (`camera_android_camerax`, `ImageAnalysis`); Java propio mínimo (103 clases: audio, BLE, calibración) |
| ML | **Ninguno** (ni modelos ni OpenCV, MediaPipe o TFLite): procesamiento de imagen clásico en Dart |
| Terceros y permisos | Firebase, RevenueCat, Branch, `flutter_blue_plus` y `sensors_plus` (BSD-3), `flutter_tts` (MIT); 104 paquetes Dart con licencia en `dart_packages_licenses.tsv`. Cámara, micrófono, BLE, sensores rápidos, internet, facturación, `AD_ID` |

## 2. Por qué casi no aporta

Es un cronómetro de cruces de línea: **solo mide tiempos** (a la centésima). No hay pose, ángulos, ROM, velocidad ni aceleración; no
guarda vídeo; su única exportación es un CSV de pago. Las tres preguntas del usuario:
1. **Perspectiva:** no la corrige; exige el móvil EN la línea de meta, perpendicular a la pista, en trípode, a 1–3 m.
2. **Tracking híbrido:** no existe; solo dos modos de manchas (persona u objeto) elegidos a mano.
3. **Vídeo con telemetría:** no existe; guarda dos fotogramas pequeños (≈ 160×120) por cruce.

## 3. Lo nuevo: solo tres cosas

### 3.1 Cruce de línea con instante sub-fotograma
- **Imagen y hora:** `ImageAnalysis` en preajuste bajo (≈ 320×240) reducido a la mitad (luminancia ≈ 160×120), unos 30 fps (no los pide;
  lo declara la FAQ). Hora del fotograma = marca del sensor + ¾ de la exposición (saneada: < 0 → 0; > 33 ms → 66 ms). No compensa el
  obturador rodante.
- **Línea:** la **columna central** (ancho × 0,5), o sea el plano vertical que contiene el centro óptico; no es configurable. La distancia
  al móvil no sesga la hora; sí su posición a lo largo de la pista y su guiñada (análisis propio: `d / v` y `D · tan θ / v`, 5–10 ms con
  5–7 cm de error).
- **Marcador del «pecho»:** diferencia de **gradientes Sobel** entre fotogramas de un buffer de 4 (insensible a la luz global),
  desenfoque, relleno de la silueta, compacidad ≥ 0,3, área mínima por distancia (persona 15 / 7 / 2,8 % del cuadro) y perfil por
  columnas; un marcador por sentido.
- **Cruce:** cada marcador se empareja con el más cercano del fotograma anterior (≤ 0,4 × ancho); hay cruce si pasa de un lado de la
  columna al otro, y con **dos o más a la vez se descarta**. **Instante:** `t = t_ant + f · (t_act − t_ant)` con
  `f = (x_línea − x_ant) / (x_act − x_ant)`; supone velocidad constante en 33 ms.
- **Falsos disparos:** tiempo muerto **contado en fotogramas** (10 / 30 / 90, ≈ 0,3 / 1 / 3 s); calentamiento de 15 fotogramas y
  bloqueo si giroscopio y acelerómetro indican que el móvil se mueve; con sol baja la compensación de exposición y avisa.
- **Lo que guarda cada parcial:** tiempo desde la salida, hora de los dos fotogramas que enmarcan el cruce y las dos imágenes. El
  sentido se detecta pero no se guarda.

### 3.2 Sincronía de varios móviles y latencias
- **El BLE solo empareja y mide el desfase; los eventos viajan por internet (Firestore).** El anfitrión anuncia la sesión y su servidor
  GATT devuelve su reloj (`elapsedRealtime`, µs). El cliente calcula `ping = t1 − t0` y `desfase = reloj_anfitrión − t0 − ping/2`
  (hasta 10 lecturas por conexión y también en sentido inverso).
- **Combinación:** cada muestra da el intervalo `[desfase ± ping/2]`; se **intersectan** los de pares de muestras y se toma el de menor
  semiancho (resultado = centro; error = semiancho). Se acepta con error < **5 ms** en los primeros 5 s, y la tolerancia sube lineal
  hasta **50 ms a los 30 s**. Hora sincronizada = `elapsedRealtime + desfase`, reutilizable 5 min. Supone, sin comprobarlo, que la marca
  del sensor está en esa misma base de tiempo.
- **Latencia de las salidas:** el pitido propio se mide con el micrófono (FFT de 53 muestras a 16 kHz, banda 2,8–3,2 kHz) para saber
  cuándo sonó de verdad, incluso por un altavoz Bluetooth. La **calibración de micrófono y pantalla** usa el acelerómetro como
  referencia del golpe: 10 toques, descarta 2 + 2, falla si el rango supera 20 ms, se repite si cambia más de 3 ms (micrófono) o 5 ms
  (táctil) o tras actualizar el sistema; sin calibrar asume 51 ms.

### 3.3 Precisión del tiempo
- **Declarada:** más del 75 % de las medidas a ≤ 0,01 s y el resto a ≤ 0,02 s (estudio propio, sin revisión por pares); el sitio web da ±0,02 s
  frente a ±0,04 s de las barreras de luz. Es para entrenar, no para competir.
- **Independiente** (Marco-Contreras et al., *Sensors* 24(20):6719, 2024; versión antigua 2.30, dos Xiaomi, **30 fps**, 5 sujetos,
  fotocélulas Witty): sesgo medio 12 / 7 / 6 ms (10 m, 20 m, 5 m ida y vuelta), ICC 0,978–0,995, **SEE 28–39 ms (≈ un fotograma)**, error
  máximo 90 ms; entre los dos móviles, sesgo 4 ms y SEE 13 ms. Los autores señalan los 30 fps como límite.
- **Lectura:** el sesgo medio es pequeño, pero el error por intento es de un fotograma; no vender «precisión de 5 ms». Con 60 / 120 / 240
  fps y PTS la cuantización pasa de ±16,7 ms a ±8,3 / 4,2 / 2,1 ms. En tiempos por **intervalo entre dos cruces de la misma línea** (505,
  lanzadera) los sesgos constantes (posición de la línea, referencia del tronco, barrido) se cancelan.

## 4. Qué tomar para KPKN (en sala limpia)

| Función de Photo Finish | ¿KPKN la tiene? | Cómo implementarla | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| Cruce de línea con instante sub-fotograma e incertidumbre | No | Post-análisis del vídeo: x del tronco (pose) con PTS; ajuste local robusto a 4–6 muestras; ± por la ventana entre fotogramas | MediaPipe, Media3 (Apache-2.0) | **Alta** | Media |
| Sentido y alternancia: 505 y lanzadera con UNA cámara | No | Cruce de ida y de regreso con sentido opuesto (`t₂ − t₁`); cruces alternos; tiempo mínimo entre cruces en ms | — | **Alta** | Media |
| Compensación del reloj del fotograma | No | Guardar exposición, `ROLLING_SHUTTER_SKEW` y fuente de tiempo en `capture.json` (Camera2Interop) | CameraX (Apache-2.0) | **Alta** | Baja-media |
| Salida con latencia medida | Parcial (voz TTS) | Audio y vídeo del mismo archivo; Goertzel ~3 kHz; aplauso visible para el desfase A/V | Oboe (Apache-2.0) | Media | Media |
| Sincronía BLE de varios móviles | No | Tipo Cristian/NTP con intersección de intervalos; transporte local | Android BLE / Nearby | Baja | Alta |

**KPKN hace mejor:** pose en vez de manchas (rechaza brazos y piernas, permite salida desde parado, da contactos y vuelo), 60–240 fps con
PTS frente a 30 fps a ≈ 160×120 px, todo local y sin cuenta, y trazabilidad (dos fotogramas a resolución completa, incertidumbre,
corrección manual). **No copiar:** tiempo muerto en fotogramas, la referencia «pecho» sin documentar su sesgo, la dependencia de la nube
y de la analítica publicitaria, y el «más preciso que un láser» sin revisión por pares.

## 5. Sin confirmar y evidencias

**Sin confirmar:** la fórmula exacta de la diferencia de bordes y la heurística del pecho; el tamaño real del fotograma por dispositivo;
los umbrales del móvil quieto; los estudios de la SRM Institute y del fabricante (no localizados). «Análisis propio» = razonamiento mío.

**Evidencias** en `C:\kw\re\evidencias\photofinish-re-20261020\` (5,3 MB): `xapk_inventory.json`, `function_map_app.tsv`,
`functions_full.tsv`, `native_classes.txt`, `dart_enums.txt`, `detection_census.txt` y el informe completo. En `libapp.so` (armeabi-v7a):
`DetectionService.calculateTimeOfCrossing` `0x8a66cc`, `findRightCrossings` `0x8a8348`, `findLeftCrossings` `0x8a937c`,
`updateTriggerWindow` `0x8b57c4`, `_getDeadtime` `0x8a9320`, `MyFrame.createForAndroid` `0x91ef90`, `TimeService.getCurrentSyncedMicros`
`0x8bf390`. Java: `M5.j` y `M5.F` (BLE y desfase), `T5.c` (pitido por FFT), `S5.w` y `MicrophoneCalibrationActivity` (calibración).
