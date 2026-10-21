# WL Analysis 2.13.9 · ingeniería inversa v2 (20-oct-2026)

Análisis **estático** del XAPK que aportó el usuario. No se ejecutó ni se instaló la app; no se usaron teléfono, emulador, git
ni Gradle. Parte del informe del 17-oct (`docs/video-technique/WL_ANALYSIS_RE.md` y
`artifacts/wl-analysis-re-20261017/INFORME.md`), que no se repite: aquí se corrige y se amplía. Sala limpia: las funciones,
fórmulas y algoritmos se describen con palabras propias; no se copia código descompilado ni se extraen modelos o recursos para
reutilizarlos. Los nombres de clase o método aparecen solo como evidencia.

**Mismo archivo que el 17-oct.** `WL+Analysis+-+bar+path+tracker_2.13.9_APKPure(1).xapk`, 102.246.282 B, SHA-256
`879bdf580f106965868547abc127488a1cde8752e29f1bd3ab28cffa924d4f1b`: idéntico byte a byte al analizado entonces.

---

## 0. Qué cambia respecto al 17-oct

| Punto del 17-oct | Estado hoy | Evidencia |
|---|---|---|
| «Ghidra en Windows rechazó el ELF ARM; no hubo pseudocódigo». | El proveedor Ghidra **de REA** sigue rechazándolo («Windows Ghidra P0 accepts native PE and explicitly selected raw 6502 targets only»). **Ghidra 12.1.4 lanzado en modo headless directamente**, con un script de exportación propio y bajo el candado, sí lo analizó: 83 funciones con pseudocódigo. | `rea-ghidra-refusal.json`, `native-ghidra-index.tsv` |
| «La biblioteca incluye `Kalman`; no se prueba que participe». | **Corregido:** el `Kalman` es un **miembro de `TemplateTracker`** (la instancia global está en el desplazamiento +0x80 de ese objeto) y solo lo usan `TemplateTracker::init/update`. El camino activo (KCF) **no tiene Kalman**. | `_INIT_1`, `TemplateTracker::init` 0x4524c, `::update` 0x455ac |
| «`BarTracker` → `KCFTracker::init/update`». | Confirmado y ampliado: KCF con HOG, ventana fija y **sin escala** (parámetros en §4.2); `update0` ignora el valor del pico y **siempre devuelve una caja**: no hay detección de pérdida. Una sola instancia nativa global. | `BarTracker_update0` 0x405b4, `KCFTracker::KCFTracker` 0x41d74 |
| «El detector neuronal no corre en cada actualización». | Confirmado y precisado: en la importación corre **una sola vez, sobre el primer fotograma** del tramo recortado; en la cámara, **cada 15 fotogramas** hasta que el usuario toca una caja. Nunca vuelve a correr durante el seguimiento: **no hay reinicialización ni recuperación**. | `m0` caso 3, `od0.f` |
| «Reconstrucción de bucle dudosa en el suavizado». | Era un **artefacto de JADX**: el smali sale del bucle al llegar a n−2. | `LiftTrajectoryProcessor.lowPassFilter` (smali) |
| «El modelo: no se inspeccionaron capas ni tensores». | Leída la cabecera: entrada `[1,640,640,3]` float32, salida `[1,5,8400]` float32, 495 operadores (bloque de atención propio de YOLO11); compatible con YOLO11n. | `model-shapes.txt` |
| «Escala por el radio vertical del disco; sin elipse confirmada». | Confirmado: la escala sale **solo de la caja del primer fotograma** (su semialto) y vale para horizontal y vertical; no hay elipse, homografía, IMU ni corrección de lente en ningún camino. | `Lift.getDisplacement`, `getDisplacementGraph` |
| No se trataron. | Nuevo: análisis con IA (Gemini) con cuotas y suscripción; modo cámara en vivo; voz (Vosk); grabación de pantalla; **exportación de vídeo con telemetría** (Media3); CSV; rejilla métrica; modo marcador ArUco con PDF imprimible; fuerza, potencia y «% de carga»; sincronización de comparaciones; tres productos de pago; ObjectBox completo; 624 ejercicios de Catalyst Athletics. | §2–§6 |
| «`ColorBarFinder`» (no tratado). | **No es un detector por color:** en Java lanza «no implementado» y su nativo solo calcula un pHash. `YoloBarFinder` nativo tiene cuerpos vacíos. `PHasher` no tiene referencias. | §4.1 |

---

## 1. Identidad

### 1.1 Paquete, archivos y firma
| Elemento | Valor |
|---|---|
| Paquete | `com.karolsmolak.wlanalysis`, versión **2.13.9** (código 203), minSdk 29, targetSdk 36 |
| Firma | APK Signature Scheme **v3** verificada (apksigner, estático); 1 firmante, «CN=Android, O=Google Inc.» (probablemente una clave generada por Play App Signing), SHA-256 del certificado `9c2171c542719295a9b6223cdc3a60d4c9c8101c9f7e284ddbd38fff2f94a9f4`; sello de origen de Google Play con fecha 2026-09-28 |
| XAPK | 22 entradas: 20 APK (base, `config.armeabi_v7a`, `hdpi` y 17 splits de idioma: ar, de, en, es, fr, hi, in, it, ja, ko, my, pt, ru, th, tr, vi, zh), `icon.png` y `manifest.json` |
| Protección | `com.pairip.licensecheck` (protección automática de Google Play) y permiso `CHECK_LICENSE` |

| Archivo | Tamaño (B) | SHA-256 |
|---|---:|---|
| `com.karolsmolak.wlanalysis.apk` | 70.289.687 | `f11ba85b22b58e767d746d3a695b6b401506afad7600f98c205c7424fb1ee4e7` |
| `config.armeabi_v7a.apk` | 31.020.311 | `7e70e873b42465d84ec8712e9862db5f73d14c981f04251d9a28e154a2e10492` |
| `lib/armeabi-v7a/libnative-lib.so` | 649.488 | `5a3b5ec93d588ac524188445a6c7a553c192abc04e282c1a7c2e346906fbc528` |
| `assets/best.tflite` | 10.658.479 | `460e0385d4d81cfa36ab4d89c2d68f55087cc9cb9c0e5b1af336fe879dec6d11` |
| `assets/metadata.yaml` | 231 | `b01f9b5be65f54099231a7f4b76308026a8514a1dade667ce0d754e3fbaaab22` |
| `assets/catalyst_exercises.json` | 94.972 | `f59862cec3f13ccd96c5d6f0e776d90e5e2d22d1438237bd8313a6151180fea4` |

Los SHA-256 de los demás splits, bibliotecas nativas y DEX están en `C:/kw/re/evidencias/wl-analysis-re-20261020/hashes-sha256.txt`.

### 1.2 Arquitectura
- **Kotlin + Jetpack Compose** (Material 3), navegación Compose, Koin (inyección), corrutinas y Flow, WorkManager, DataStore.
- **R8 agresivo:** 17.787 clases en 3 DEX, de ellas 11.838 reempaquetadas en el paquete por defecto. Solo 99 conservan
  `com.karolsmolak.wlanalysis.*` (JNI, entidades ObjectBox, workers).
- **C++ con NDK:** `libnative-lib.so`, que depende solo de `libopencv_java4.so`. Sin huellas de Flutter, React Native ni Unity.
- **ABI:** solo `armeabi-v7a` en este XAPK (32 bits).

### 1.3 Bibliotecas nativas
| Biblioteca | Tamaño | Para qué |
|---|---:|---|
| `libnative-lib.so` | 649 KB | Propia: KCF (KCFcpp con fHOG), `TemplateTracker` + Kalman (sin uso), pHash, JNI |
| `libopencv_java4.so` | 15,6 MB | OpenCV 4.x (≥ 4.7, por `ArucoDetector`) |
| `libtensorflowlite_jni.so` | 2,4 MB | LiteRT 1.0.1 |
| `libvosk.so` + `libjnidispatch.so` | 9,0 MB + 116 KB | Voz sin conexión (Vosk) por JNA |
| `libobjectbox-jni.so` | 1,7 MB | Base de datos ObjectBox |
| `libimage_processing.so` | 3,7 KB | Conversión ARGB → tensor RGB/255 para YOLO |
| `libsentry*.so` | 537 KB | Informes de fallos |
| `libimage_processing_util_jni`, `libsurface_util_jni`, `libandroidx.graphics.path`, `libdatastore_shared_counter`, `libc++_shared` | — | CameraX, Compose, DataStore, runtime C++ |

### 1.4 Modelos de ML
| Modelo | Tamaño | Entrada | Salida | Clases |
|---|---:|---|---|---|
| `best.tflite` (detector) | 10,66 MB | `images` [1, 640, 640, 3] float32, RGB/255, con letterbox | `Identity` [1, 5, 8400] float32: cx, cy, w, h normalizados + 1 puntuación; sin «objectness» | `0: bar` (es el disco) |
| `model-en-us/` (Vosk) | 68 MB | audio 16 kHz | texto parcial | — (voz en inglés) |

- **Detector:** 495 operadores (209 CONV_2D; SiLU como LOGISTIC × MUL; 3 MAX_POOL de SPPF; 2 RESIZE_NEAREST; un bloque de
  atención con 4 FULLY_CONNECTED, 2 SOFTMAX y 13 TRANSPOSE). 8400 anclas = 80² + 40² + 20² (pasos 8, 16 y 32). En float32, ≈ 2,6 M
  parámetros: **compatible con YOLO11n** (sin confirmar). `metadata.yaml`: autor Karol Smolak, 27-ene-2025, `half=false`,
  `int8=false`, `nms=false`. Ejecución: intérprete LiteRT, 4 hilos, XNNPACK, CPU.
- **No hay modelo de pose** (ni MediaPipe, ni ML Kit, ni MoveNet).

### 1.5 Bibliotecas de terceros (versión · licencia)
Versiones de `assets/sentry-external-modules.txt` y de los `*.properties`. Licencias según cada proyecto público: verificar antes
de usar.

| Biblioteca | Versión | Licencia | Uso en la app |
|---|---|---|---|
| OpenCV (Java + nativo) | 4.x (≥ 4.7) | Apache-2.0 | Mat, ArUco, conversión de color |
| KCFcpp (Faro, Bailer, Henriques) + fHOG, dentro de `libnative-lib` | — | BSD-3-Clause (confirmado en su repositorio) | Tracker del disco |
| LiteRT (TFLite) + support | 1.0.1 | Apache-2.0 | Detector YOLO |
| Pesos YOLO11 (Ultralytics) | — | AGPL-3.0 o licencia Enterprise de Ultralytics (sin confirmar cuál aplica) | `best.tflite` |
| Código auxiliar YOLO (`TfliteDetector`, `PostProcessUtils`, `Predictor`, `LocalYoloModel`, `argb2yolo`) | — | Estructura coincidente con el plugin `ultralytics_yolo` (AGPL-3.0); sin confirmar archivo a archivo | Inferencia |
| `ObjectDetectorHelper` (constantes MobileNet y EfficientDet) | — | Patrón del ejemplo TFLite de detección (Apache-2.0); sin confirmar | Envoltorio |
| AndroidX Media3 (ExoPlayer, Transformer, Effect, Muxer) | 1.11.0 | Apache-2.0 | Reproductor, recorte, exportación |
| CameraX (core, camera2, video, view, extensions) | 1.6.1 | Apache-2.0 | Cámara |
| Vosk Android / JNA | 0.3.75 / 5.17.0 | Apache-2.0 / Apache-2.0 o LGPL-2.1 | Voz |
| ObjectBox | 5.4.2 | Java Apache-2.0; núcleo nativo binario propio | Base de datos |
| HBRecorder | 3.0.9 | MIT | Grabación de pantalla |
| MPAndroidChart (incrustado) | sin confirmar | Apache-2.0 | Gráficos |
| Koin / Ktor / OkHttp / Okio | 4.2.2 / 3.0.3 / 4.12.0 / 3.11.0 | Apache-2.0 | DI y red |
| Coil / Telephoto | 2.7.0 y 3.0.4 / 0.19.0 | Apache-2.0 | Imágenes y zoom |
| Markwon / compose-markdown / commonmark-java | 4.6.2 / 0.7.2 / 0.13.0 | Apache-2.0 / MIT / BSD-2-Clause | Resultado de la IA |
| Calendar (kizitonwose) / material-calendarview | 2.10.1 / fork | MIT | Calendario |
| SnakeYAML / Guava / Dagger / Accompanist | 1.29 / 33.3.1 / 2.59 / 0.36–0.37 | Apache-2.0 | Varios |
| Sentry Android / sentry-native | 8.52.0 / 0.16.1 | MIT | Fallos (con consentimiento) |
| Firebase (Analytics 23.2.0, AI 17.15.0, App Check 19.4.0, Functions 22.1.1, Remote Config 23.1.0, mensajería), Mobile Ads 25.4.0, UMP 4.0.0, Billing 9.1.0, Play Integrity 1.4.0, Install Referrer 2.2 | — | SDK propietarios de Google | IA, cuotas, anuncios, pagos |

### 1.6 Permisos y sensores
- **Cámara y audio:** `CAMERA`, `RECORD_AUDIO` (voz y audio de la grabación de pantalla).
- **Primer plano:** `FOREGROUND_SERVICE`, más `_MEDIA_PROJECTION`, `_MICROPHONE` y `_DATA_SYNC`; `WAKE_LOCK`; `POST_NOTIFICATIONS`.
- **Red y almacenamiento:** `INTERNET`, `ACCESS_NETWORK_STATE`; `READ/WRITE_EXTERNAL_STORAGE` heredados.
- **Pagos y anuncios:** `BILLING`, `CHECK_LICENSE`, `AD_ID`, `ACCESS_ADSERVICES_*`, referidor de instalación, `c2dm.RECEIVE`.
- **Integración con el sistema:** la actividad atiende `VIDEO_CAPTURE` y `media.action.INSERT` y recibe vídeos compartidos
  (`SEND`, `SEND_MULTIPLE`, `VIEW` de `video/*`). Expone un `DOCUMENTS_PROVIDER` (`VideoProvider`) y tiene accesos directos
  «Grabar» e «Importar».
- **Sensores:** el código propio **no usa IMU** para nivelar. Gravedad y rotación aparecen solo en clases de bibliotecas (anuncios,
  CameraX).

---

## 2. Inventario completo de funciones, pantalla por pantalla

Rutas de navegación: `home`, `trim/{ur}`, `barbell_picker/{ur}/{minTs}/{maxTs}/{persist}`, `lift/{liftId}` (varios ids unidos por
guion), `camera`, `exported`, `settings`, `premium`, `backup`, `about`, `feedback`. «PRO» = compra `extra_options`.

### 2.1 Inicio (`home`)
- **Lista de levantamientos:** miniatura, peso, fecha y etiquetas; buscar por etiqueta; orden y modo de vista configurables;
  vista **calendario**.
- **Selección múltiple:** comparar, exportar o borrar.
- **Acciones:** «Grabar vídeo» (cámara), «Importar vídeo» (selector del sistema; también desde «Compartir» de otras apps).
- **Menú:** Ajustes, Vídeos exportados, Copia, Premium (Funciones extra), Tutorial (web), Acerca de, Contacto.
- **Versión gratuita:** anuncios AdMob con consentimiento UMP.
- **Promoción:** aviso «Nuevo: análisis de vídeo con IA».

### 2.2 Importación: recorte (`trim/{ur}`)
- Elegir el tramo del vídeo.
- «¿Copiar?»: guarda una copia recortada con Media3 Transformer, sin alinear a fotograma clave; muestra el espacio necesario.
  Ajuste `PERSIST_VIDEOS_PREF`.
- Sin copia, la app guarda solo el URI del original. Si este se mueve o se borra, el levantamiento queda «Vídeo no disponible» y se
  puede **reenlazar**; con copia recortada perdida no se puede.

### 2.3 Importación: elegir el disco (`barbell_picker`)
- Se decodifica el **primer fotograma** del tramo (lado largo ≤ 1920 px) y el detector propone cajas:
  - una caja: «Si la barra está bien detectada pulsa OK; si no, detección manual y haz zoom hasta que el disco coincida con el
    cuadrado»;
  - varias: «Pulsa la caja de la barra…»;
  - ninguna: «No se detectó la barra; haz zoom hasta que el disco coincida con el cuadrado».
- **Datos de la serie:** peso de la barra (kg o lb), diámetro del disco **estándar 45 cm o no estándar** (cm) y «Mantener el peso
  entre series».
- **Al confirmar:** se guarda la caja elegida (o la primera, o la manual) como primera posición y se encolan en WorkManager
  «PersistanceWorker» (si hay copia) y «ProcessingWorker». Corren en primer plano con la notificación «Importando vídeos…»,
  progreso y cancelar.

### 2.4 Levantamiento (`lift/{id}`)
- **Reproductor:** ExoPlayer o MediaPlayer (ajuste); silencio opcional.
- **Superposición:** trayectoria, rejilla métrica, línea vertical en la x actual de la barra y en la x del inicio de la repetición,
  información (peso, fecha, etiquetas).
- **Dos gráficos** (eje izquierdo y derecho), cada uno con función × componente (§3).
  - Tocar el gráfico pausa en ese instante.
  - **Ampliar y arrastrar crea un bucle** de fragmento que define el rango de las medias, del trazo y de la exportación.
  - Mínimo y máximo saltan al fotograma; media, «Avg(+)» y «Avg(−)».
- **Repeticiones:** «Dividir la trayectoria en reps» (las demás en gris). Las cadenas «Ir al anterior» e «Ir al siguiente» existen
  en los recursos pero el código no las usa.
- **PRO:** velocidad de reproducción variable, pantalla completa, zoom del vídeo, barrido fotograma a fotograma (Android ≥ 8),
  color del trazo por velocidad, fuerza o potencia.
- **Editar levantamiento:** peso, etiquetas (con sugerencias en PRO), **fps reales** para vídeos a cámara lenta y diámetro del
  disco.
- **Acciones:** análisis con IA, exportar vídeo, exportar CSV (PRO), borrar.

### 2.5 Comparación (`lift/{id1-id2}`)
- **Modo:** lado a lado (PRO), o **superpuestos (beta)**.
- **Sincronización:** por inicio del movimiento, o por el **mínimo o máximo** de un parámetro vertical elegido.
- **Comparación con IA** (sus resultados no se guardan).
- **Exportación:** hasta 2 vídeos.

### 2.6 Cámara (`camera`, «beta»)
- **Vista previa CameraX:** el detector propone cajas cada 15 fotogramas de análisis. Se toca el disco, o se usa la «mano» para
  colocar la caja.
- **Seguimiento en vivo:** KCF o ArUco, con trayectoria y, opcionalmente, **gráficos en la cámara**.
- **Controles:** cuenta atrás, linterna, cámara frontal o trasera, calidad (muy baja…muy alta), peso y diámetro (o tamaño del
  marcador).
- **Comandos de voz:** Vosk sin conexión, en inglés; frases configurables, por defecto «start» y «stop», con sonido.
- Botón flotante de **grabación de pantalla**.
- **Al terminar**, con el seguimiento en vivo activo (bandera activada por defecto), el levantamiento se crea **directamente con la
  trayectoria en vivo**, sin reprocesar, con el fps del rango de la cámara. Con el seguimiento desactivado, el vídeo pasa al flujo
  de importación (recorte y elección del disco). No se confirmó qué control de la interfaz cambia esa bandera.

### 2.7 Vídeos exportados (`exported`)
Pestañas «Vídeos» y «Grabaciones de pantalla»: duración, tamaño, compartir, ver y borrar.

### 2.8 Ajustes (`settings`)
Claves en `settings-keys.txt`.
- **General:** modo oscuro, idioma, unidades (masa, velocidad, desplazamiento, potencia, fuerza, aceleración), silenciar, mantener
  peso, diámetro por defecto.
- **Reproductor:** ExoPlayer, «independiente del dispositivo», o MediaPlayer, «barrido más suave».
- **Trayectoria:** color, grosor, transparencia, gradiente (Plano, Velocidad, Fuerza o Potencia), dividir en reps; líneas
  verticales (color y grosor) del inicio de la repetición y de la posición actual.
- **Rejilla:** tamaño, unidad (cm o in), grosor, transparencia, etiquetas, reiniciar en cada repetición.
- **Exportación:** calidad; incluir fecha, gráficos, agregados, etiquetas o peso; parámetros del CSV; datos de cada fotograma.
- **Cámara:** calidad, comandos de voz y sus frases, cuenta atrás, gráficos en la cámara.
- **Rastreador:** método **Disco o Marcador**; tamaño del marcador de **20 a 60 mm** (diámetro del círculo de corte; 40 por
  defecto); «Generar e imprimir el marcador de calibración» en PDF.
- **IA:** tono (Amateur, «explicaciones simples», o Profesional, «terminología de entrenador»); consentimiento de subida.
- **Privacidad:** opciones de anuncios personalizados; informes de fallos opcionales, con una pantalla que detalla los datos que
  se envían.

### 2.9 Premium (`premium`)
| Producto | Tipo | Qué desbloquea |
|---|---|---|
| `extra_options` | compra única («PRO») | velocidad variable, lado a lado, pantalla completa, zoom, color por fuerza, potencia o velocidad, más opciones de dibujo, CSV, sugerencias de etiquetas, copia y restauración, barrido (Android ≥ 8) y **3 análisis con IA gratis** |
| `remove_ads` | compra única | sin anuncios y **sin marca de agua** en los vídeos exportados |
| `ai` | suscripción mensual | análisis y comparación con IA, con límite diario que se renueva a medianoche UTC; exige integridad de Play: con el bootloader abierto se bloquea |

### 2.10 Copia (`backup`)
- **Copia:** carpeta elegida por el usuario (SAF) con `data.json` (levantamientos, trayectorias y rejillas) y cada vídeo como
  `<id>.<ext>`.
- **Restauración** de la misma carpeta; muestra la fecha de la última copia.
- La interfaz habla de un «.zip», pero el código escribe una carpeta.

### 2.11 Acerca de y Contacto
- **Acerca de:** autor, web, Instagram, Facebook, YouTube y valoración en Play.
- **Contacto:** formulario con email y descripción.

### 2.12 Trabajos en segundo plano
| Worker | Qué hace |
|---|---|
| `PersistanceWorker` | Recorte y copia del vídeo |
| `ProcessingWorker` | Seguimiento completo del vídeo |
| `ExportWorker` | Composición del vídeo exportado |
| `GeminiAnalysisWorker` / `GeminiComparisonWorker` | Análisis y comparación con IA |
| `AiQuotaReleaseWorker` | Libera la cuota reservada si la IA falla |
| `BackupWorker` / `RestoreWorker` | Copia y restauración |
| Servicio de HBRecorder | Grabación de pantalla |

---

## 3. Catálogo de métricas

Todas salen del **centro de la caja seguida**: x e y normalizados al ancho W y al alto H del fotograma de proceso. Escala:
k = (D/2)/(H·r₂⁰), con r₂⁰ el semialto normalizado de la **primera** caja y D el diámetro (0,45 m si no se indica).
Paso temporal: dt = mediana(Δts)/f_aj, con f_aj = fps declarados/fps medidos para la cámara lenta (1 si no se declaran).

| Métrica | Definición (palabras propias) | Unidades | Filtros y umbrales | Cuándo se muestra y cuándo se abstiene |
|---|---|---|---|---|
| Trayectoria | Posiciones del centro por fotograma | — | Suavizado de 3 puntos en dos etapas (§4.5) | Siempre que haya ≥ 2 posiciones |
| Desplazamiento vertical | (y₀ − y)·H·k: positivo hacia arriba | cm o in | — | Siempre |
| Desplazamiento horizontal | (x − x₀)·W·k: positivo hacia la derecha de la imagen, sin normalizar por el lado del atleta | cm o in | Misma escala que el vertical | Siempre |
| Desplazamiento «total» | Norma de los dos anteriores: distancia al punto inicial, no longitud recorrida | cm o in | — | Siempre |
| Velocidad vertical u horizontal | Derivada de Savitzky–Golay del desplazamiento | m/s o ft/s | Ventana 3, 5, 7, 9 u 11 según dt (≥ 50, < 50, < 20, < 14 o < 10 ms) | Siempre; sin calibración real del disco, con 0,45 m por defecto |
| Velocidad total | Norma de las componentes | m/s o ft/s | — | Siempre |
| Aceleración vertical u horizontal | Segunda derivada SG (ventana 7, 9, 11 o 13) + suavizado [¼, ½, ¼] | m/s² o ft/s² | Vertical recortada a ≤ 20 m/s², sin cota inferior | Siempre |
| Aceleración total | Norma | m/s² | — | Siempre |
| Fuerza vertical | m·max(a_v + 9,81; 0)/1000 | kN, lbf, kp o «% de carga» | Con m = 1 si no hay peso | Solo con peso (requisito de la función) |
| Fuerza horizontal | m·max(a_h; 0)/1000: **descarta la aceleración horizontal negativa** | igual | — | Solo con peso |
| Fuerza total | Norma de las componentes | igual | — | Solo con peso |
| «% de carga» | F/(m·g)·100: fuerza relativa al peso de la barra | % | — | Solo con peso |
| Potencia vertical u horizontal | F_componente·max(v_componente; 0) | kW o ft·lbf/s | — | Solo con peso |
| Potencia «total» | **Norma de las potencias por componente** (no es F·v; no tiene sentido físico) | kW | — | Solo con peso |
| Mínimo y máximo | Extremos del parámetro en el fragmento, con su instante | según el parámetro | — | Bajo el gráfico, en el CSV y en la superposición exportada |
| Media («Avg») | Media de todas las muestras del fragmento | igual | — | Igual |
| «Avg(+)» y «Avg(−)» | Media solo de las muestras > 0,04 o < −0,04, **en las unidades elegidas** (el umbral cambia con ft/s) | igual | Umbral ±0,04 | Igual. El tutorial llama «media concéntrica» a la del fragmento en bucle |
| Repeticiones | Segmentos entre reanudaciones del movimiento (§4.6) | n | \|v\| ≤ 0,083 m/s durante > 260 ms | Siempre; sin parciales ni fallidas, sin confianza |
| Inicio del movimiento | Primera muestra con \|v_vertical\| > 0,083 m/s | ms | Aparentemente sobre la velocidad sin suavizar: filtra una copia que no usa (tramo mal descompilado) | Sincronización «Inicio» |
| Valores para el color | \|v_v\|, \|F_dibujo + g\| (suma g dos veces) o potencia de dibujo, normalizados al mín. y máx. del tramo dibujado | — | Paleta amarillo → rojo de 5 paradas | En el trazo (el tutorial dice verde → rojo; el código 2.13.9 va de amarillo a rojo) |

**No existen:** fases excéntrica y concéntrica por repetición (solo el filtro de signo y el fragmento elegido); MCV o MPV por
repetición; velocidad pico por repetición, aparte del máximo del fragmento; pérdida de velocidad; 1RM y perfil carga-velocidad;
TUT y tempo; ROM como métrica, aparte del desplazamiento; ningún ángulo ni métrica de pose; simetría; confianza o abstención de
cualquier cifra.

---

## 4. Algoritmos

### 4.1 Detección
- **Cableado** (Koin): `SwitchingBarFinder(Yolo11Finder, ArucoBarFinder)`, elegido por la preferencia `TRACKER_MODE` (`PLATE` o
  `MARKER`). Si la preferencia cambia, el buscador se intercambia en caliente. Es un **ajuste del usuario: no hay recurso
  automático** de un modo a otro.
- **YOLO** (`Yolo11Finder` → `ObjectDetectorHelper` → `YoloDetector` → `TfliteDetector`):
  1. Mat → Bitmap ARGB.
  2. Redimensionado manteniendo el aspecto al lado 640 y relleno negro centrado.
  3. Tensor RGB/255 en nativo (`argb2yolo`).
  4. Inferencia.
  5. Posprocesado: argmax de clase; umbral de confianza **0,5** (el del ayudante; el detector trae 0,25); orden descendente;
     **NMS voraz con IoU 0,3**; hasta 30 cajas; recorte a [0,1]; la caja vuelve a coordenadas del fotograma deshaciendo el relleno.
     El «maxResults = 3» del ayudante no se aplica.
- **Cuándo se usa:**
  - en la importación, **una vez**, en el primer fotograma del tramo;
  - en la cámara, cada 15 fotogramas mientras no se ha elegido barra.
- **Del detector al tracker:** la caja elegida (o la primera, o el cuadrado manual) se guarda como primera posición (centro y
  semiejes normalizados). Al procesar, el tracker se inicializa en el primer fotograma decodificado con esa caja y después solo se
  actualiza.
- **No hay reinicialización, ni recuperación, ni nueva detección.** El tutorial lo reconoce: se pierde el disco si alguien pasa por
  delante; aconseja no cruzarse y recortar antes de importar.
- **Código muerto:** `YoloBarFinder` (detector nativo antiguo: sus JNI tienen cuerpos vacíos y su bucle Java avanza de 1 en vez de
  4), `ColorBarFinder` (Java «no implementado»; el nativo solo calcula un pHash) y `PHasher` (sin referencias).

### 4.2 Seguimiento
**Modo disco: `BarTracker` → KCF nativo.**
- Es el KCFcpp público (BSD-3-Clause), con FFTTools, RectTools y fHOG de 31 canales (celda 4, truncado 0,2, PCA).
- **Configuración** de la instancia global: HOG sí, ventana fija sí, **multiescala no**, Lab no.
  - Parámetros: relleno 2,5; λ = 10⁻⁴; σ de salida 0,125; interpolación 0,012; σ del núcleo gaussiano 0,6; plantilla 96 px;
    paso de escala 1,0.
  - **Coinciden exactamente** con los valores por defecto del repositorio público para esa combinación.
- **Cada fotograma:**
  1. subventana con borde replicado;
  2. ventana de Hann;
  3. correlación gaussiana en Fourier;
  4. pico por `minMaxLoc` con refinamiento subpíxel;
  5. desplazamiento del centro;
  6. recorte a la imagen;
  7. reentrenamiento con interpolación 0,012.
- **Consecuencias:**
  - **sin estimación de escala**: la caja no cambia de tamaño;
  - **sin criterio de pérdida**: el valor del pico se calcula pero el JNI lo ignora y siempre devuelve 4 números;
  - el modelo se actualiza aunque haya oclusión: deriva;
  - **una sola instancia nativa global**: si la cámara en vivo y una importación corrieran a la vez se pisarían.
- **Resolución:** el proceso decodifica con MediaCodec → textura OES → GLES2 → `glReadPixels` RGBA y **reduce a la mitad**
  (un 1080p se sigue a 960×540). Procesa todos los fotogramas, sin saltos. Si no hay decodificador Dolby Vision, recurre a HEVC.

**Modo marcador: `ArucoBarTracker`.**
- ArUco de OpenCV con diccionario predefinido 0 (**DICT_4X4_50**), **id 0** y refinado de esquinas subpíxel.
- Caja = rectángulo alineado a los ejes de las 4 esquinas.
- **Detección en cada fotograma; si falta, mantiene la última caja.** Sin predicción ni Kalman.

**Sin uso: `TemplateTracker` + `Kalman`.**
- Plantilla con **máscara de anillo** (disco de radio r menos el buje de radio r/3), filtro bilateral (d = 15, 50, 75),
  `matchTemplate` por diferencia cuadrática, suavizado 7×7, mínimo y Kalman de velocidad constante.
- Kalman: 4 estados, 2 medidas, Δt = 1 fotograma, ruidos por defecto de OpenCV. Centra la siguiente búsqueda (r + r/3).
- Es el diseño más parecido al de KPKN, y lo tienen apagado.

### 4.3 Calibración y escala
- La escala sale del **semialto de la primera caja**: la de YOLO, el cuadrado manual o el ArUco. Se aplica igual al eje horizontal.
- **Diámetro:** 0,45 m por defecto; «no estándar» lo introduce el usuario.
- **Modo marcador:** el «diámetro» es el **tamaño del marcador** (20–60 mm).
- **Posible error del modo marcador** (deducción estática, sin confirmar en ejecución):
  - el PDF imprime un cuadrado ArUco de lado D/√2 inscrito en el círculo de corte de diámetro D;
  - la escala trata D como si fuera el alto de la caja;
  - con el marcador derecho, la caja mide D/√2 y los desplazamientos saldrían **hasta un 41 % más grandes**; solo girado 45° coincide.
- **No hay altura del usuario, ni intrínsecos de cámara, ni comprobación de cordura.**
- **PDF del marcador:** A4, hasta 4×4 marcadores, círculo discontinuo de corte y una línea de control de 50 mm («imprime al 100 %»).

### 4.4 Perspectiva y ángulo de cámara
- **No hay ninguna corrección:** ni homografía, ni elipse del disco, ni ejes del rack, ni IMU, ni distorsión de lente.
- El tutorial exige:
  - grabar **de lado**;
  - el objetivo **nivelado y paralelo al suelo**;
  - la altura a **mitad del recorrido** (en el peso muerto, a la altura de la rodilla);
  - **2,5–3,5 m** de distancia.

### 4.5 Suavizado y derivadas
1. **Al guardar** (importación): núcleo simétrico [0,275; 0,45; 0,275] sobre x, y, r₁ y r₂, aplicado n = redondeo(fps/30) veces;
   los extremos no se filtran.
2. **Al mostrar:** núcleo [0,25; 0,5; 0,25] una vez.
3. **Velocidad:** derivadores de Savitzky–Golay estándar (3, 5, 7 y 9 lineales; 11 y 13 cúbicos), divididos por dt.
4. **Aceleración:** segunda derivada SG (7–19 en la tabla, 7–13 en uso) dividida por dt², más [¼, ½, ¼].
5. **Bordes:** se replica el último valor válido.
6. **Cámara en vivo:** la trayectoria se guarda sin la primera etapa.

**Ancho de banda efectivo** (cálculo propio de la cadena completa, ganancia a −3 dB respecto a un derivador ideal;
`filter-response.txt`):

| fps | Pasadas | Posición −3 dB | Ventana SG de velocidad | Velocidad −3 dB |
|---:|---:|---:|---:|---:|
| 30 | 1 | 3,8 Hz | 5 | **2,7 Hz** |
| 60 | 2 | 6,2 Hz | 7 | **4,0 Hz** |
| 120 | 4 | 9,6 Hz | 11 | 8,7 Hz |
| 240 | 8 | 14,3 Hz | 11 | 13,8 Hz |

A 30–60 fps la velocidad queda muy filtrada. Es coherente con el **sesgo negativo de velocidad pico** publicado para WL Analysis en
arrancada y cargada (−0,07 a −0,21 m/s; Lemus 2024, vía Puppyn y Brughelli 2026, en `MERCADO_FIABILIDAD.md` §2.8). La relación
causal es una hipótesis.

### 4.6 Repeticiones y fases
1. Velocidad total (norma) con media móvil de 3 puntos dos veces.
2. Se abre una «quietud» cuando |v| ≤ **0,083 m/s**. Al reanudarse el movimiento, si duró **> 260 ms**, se marca un límite.
3. El último límite es el último fotograma; cada par de límites consecutivos es una repetición: movimiento y pausa siguiente.

Consecuencias:
- pensado para levantamientos con pausa (halterofilia, singles y dobles);
- sin pausa (*touch-and-go*, series continuas) las repeticiones se fusionan;
- un vídeo que empieza en movimiento pierde el primer tramo;
- **no hay fases** ni repeticiones parciales o fallidas.

### 4.7 fps y cámara lenta
- **fps medido:** 1/mediana(Δts) de la trayectoria.
- **Cámara lenta:** el usuario introduce los fps reales y dt se reescala por fps_usuario/fps_medido.
- **Cámara en vivo:** usa el límite superior del rango de fps del sensor.
- No detecta VFR ni se abstiene a fps bajos; las ventanas de derivada se adaptan a dt.

### 4.8 Controles de calidad y avisos
- **Hay mensajes para:** detección (una caja, varias o ninguna), vídeo movido o borrado (reenlazar), fallo de reproducción,
  fallo de exportación («prueba un fragmento más corto o baja la calidad»), espacio para el recorte, tamaño máximo de la grabación
  de pantalla, y errores de la IA (cuota, sin red, sin suscripción confirmada, integridad).
- **No hay:** métrica de calidad del seguimiento, confianza ni abstención en ninguna cifra.
- **Consejos del tutorial:** luz y contraste suficientes, sin desenfoque y sin cruzarse con el disco.

### 4.9 Comparación y sincronización
- **Desfase entre dos levantamientos:**
  - por «Inicio»: diferencia de los instantes de inicio del movimiento;
  - por «Mín.» o «Máx.»: alinea el instante del mínimo o del máximo del parámetro vertical elegido (por ejemplo, la velocidad
    pico).
- **Superposición (beta):** ambos vídeos en el mismo lienzo. La mezcla de opacidad está sin confirmar.

### 4.10 IA (Gemini)
- **Envío:**
  - el fragmento elegido (≤ 15 s), re-codificado a ≤ 720 px de alto, sin audio, en base64;
  - la trayectoria como texto «ms, dx cm, dy cm» por fotograma desde el inicio del fragmento;
  - peso con unidad, etiquetas, idioma y tono.
- **Plantilla en el servidor** («input-system-instructions», Firebase AI): **el prompt no está en el APK**.
- **Control de acceso:**
  - consentimiento una vez, revocable;
  - cuota diaria reservada con Firebase Functions y el token de compra; se libera si falla;
  - App Check con Play Integrity.
- **Resultado:** Markdown guardado en el levantamiento. Los marcadores `{ejercicio|…}` se enlazan a la página de Catalyst Athletics
  del ejercicio (624 ejercicios en 10 categorías), con atribución.
- **Aviso de la app:** la IA puede equivocarse y es una pista, no un veredicto.

---

## 5. Exportación y compartir

### 5.1 Vídeo con la telemetría quemada (`ExportWorker`)
**Motor:** Media3 Transformer 1.11.0 con una `Composition`. Por cada vídeo:
- recorte al fragmento, más el desfase de sincronización;
- `OverlayEffect` con un `BitmapOverlay` que redibuja cada fotograma con **el mismo pintor del reproductor**;
- escalado y recorte (el zoom del usuario);
- una bandera de espejo por vídeo en los ajustes (`ExportSettings.mirror`); no se localizó dónde se aplica;
- `SpeedChangeEffect` para cámara lenta y audio con Sonic. El audio se quita si el factor es < 0,5 o si hay comparación.

**Capas, en orden:**
1. Rejilla métrica (cm o in, con etiquetas), anclada en la posición de la barra al inicio de la repetición y limitada al rectángulo
   del usuario.
2. Trayectoria **progresiva**: lo futuro y las repeticiones previas en gris translúcido; plana o con gradiente amarillo → rojo
   normalizado al tramo; extremos redondeados; grosor proporcional al ancho del vídeo.
3. Línea vertical en la x actual y en la x del inicio de la repetición.
4. Texto: peso, fecha y etiquetas.
5. Tabla de agregados (mín., máx., media) de los parámetros elegidos; 2 decimales si el valor es < 10.
6. **Marca de agua** «WL Analysis» en verde y negrita abajo a la derecha (alto ≈ lado menor/21), salvo con `remove_ads`.

**Gráficos:** con un solo vídeo se añade debajo una **franja del 30 % del alto** con el gráfico de líneas de MPAndroidChart
renderizado a bitmap (leyenda y ejes escalados al alto de la franja).

**Comparación:** 2 vídeos, lado a lado (en vertical uno junto al otro; en horizontal apilados) o superpuestos. Más de dos:
«no admitido».

**Salida:**
- MP4 con el lado largo ≤ **1920 px**, en múltiplos de 16;
- reintentos ante HDR: forzar la interpretación SDR con codificador hardware, luego software y por último el mapeo de tonos
  OpenGL;
- se guarda en `Movies/WLanalysis/exports/aaaa_MM_dd_HH_mm.mp4` (MediaStore) o en una carpeta elegida, con «Compartir» y «Ver»;
- el ajuste de calidad (muy baja…muy alta) existe; su traducción al codificador está sin confirmar.

**Estética:** sobria y técnica. No hay plantillas para redes, ni pegatinas, ni formatos 9:16 dedicados.

### 5.2 Grabación de pantalla
HBRecorder (MediaProjection y micrófono en primer plano). Graba la interfaz completa con trazo y gráficos, «para entrenadores que
crean correcciones», y la guarda en «Grabaciones de pantalla».

### 5.3 CSV
- **Por vídeo:** id, fecha, resolución, fps, peso y etiquetas.
- **Por parámetro elegido:** media, mínimo e instante, máximo e instante, en el fragmento.
- **Opcional:** una fila por fotograma (ordinal, tiempo en s y parámetros).
- Si no hay peso, se omiten fuerza y potencia. Punto decimal.

### 5.4 Nube
- La única subida de vídeo es la de la IA, con consentimiento.
- SDK de Firebase presentes: analítica, Remote Config, App Check, Functions (cuotas) e IA. Que la app use Remote Config está sin
  confirmar.
- Sentry solo con consentimiento.
- No hay cuentas, ni sincronización, ni copia en la nube: la copia es local (§2.10).

---

## 6. Datos e integraciones

| Tipo | Detalle |
|---|---|
| Base de datos | **ObjectBox 5.4.2.** `LiftData`: id, peso, URI, ancho y alto, fecha, minTs y maxTs, diámetro, etiquetas, fps declarados, análisis de IA en texto, desplazamiento original del recorte. `ToMany<Barbell>`: x, y, r₁, r₂ normalizados y ts en ms. `ToMany<GridSettings>`: rectángulo x₁, y₁, x₂, y₂ |
| Preferencias | DataStore, ~70 claves (`settings-keys.txt`) |
| Room | Solo interna de WorkManager |
| Sensores BLE, wearables, Health Connect | No hay |
| Cuentas | No hay inicio de sesión. Las compras de Play identifican la suscripción y la cuota |

---

## 7. Las tres preguntas del usuario

**1. Perspectiva: ¿exige grabar a 90° de perfil, o corrige ángulos oblicuos?**
- **Exige el perfil y no corrige nada.** El tutorial pide vista lateral, el objetivo nivelado y paralelo al suelo, la altura a mitad
  del recorrido y 2,5–3,5 m.
- En el código no hay homografía, elipse del disco, ejes del rack, nivel por IMU ni corrección de lente.
- La escala es única: el semialto de la primera caja. Con la cámara girada hacia un lado, el desplazamiento horizontal se
  subestima y mezcla movimiento en profundidad; con la cámara inclinada, se distorsiona el vertical.
- La rejilla métrica y las líneas verticales son ayudas visuales, no correcciones.
- **KPKN ya va por delante aquí:** elipse con escalas horizontal y vertical, avisos y rechazo, paralaje, giro por gravedad y chips
  de encuadre.

**2. Tracking híbrido: ¿qué hace sin disco olímpico?**
- **No hay pose ni cambio automático de objetivo.** El tracker se elige con un ajuste manual (Disco o Marcador) y se fija al
  iniciar.
- **Sin disco hay dos salidas:**
  1. Elegir a mano cualquier objeto con el cuadrado y el zoom (la cabeza de una mancuerna, una kettlebell, un disco de máquina) e
     introducir su tamaño real como «diámetro no estándar». KCF sigue la textura; YOLO solo conoce «bar».
  2. El **modo marcador**: imprimir el ArUco 4×4, de id 0 y 20–60 mm, pegarlo al implemento y detectarlo en cada fotograma,
     manteniendo la última posición si falta. La escala es el tamaño del marcador, con la posible sobreescala de §4.3.
- Poleas y máquinas solo funcionan con el marcador o con una textura que KCF pueda seguir.
- **Las repeticiones siempre salen de las pausas de la velocidad de ese objeto.**

**3. ¿Exporta vídeo con telemetría para redes?**
- **Sí** (§5.1). MP4 con trazo progresivo y gradiente, rejilla métrica, líneas de referencia, peso, fecha y etiquetas, tabla de
  agregados y franja de gráfico; zoom y cámara lenta (hay una bandera de espejo cuya aplicación no se localizó); 2 vídeos lado a
  lado o superpuestos; ≤ 1920 px.
- Marca de agua salvo pago.
- Además, grabación de pantalla.
- **No tiene** plantillas para redes ni pegatinas.

---

## 8. Brechas frente a KPKN

Se leyeron `VIDEO_TECHNIQUE_V1.md` (incluido el §8 del disco), `BANCO_COMPLETO.md`, `INFORME_CONFIANZA_20261020.md` y
`MERCADO_FIABILIDAD.md`; también el código actual (`TechniqueAnalysisPipeline.tryAcquire` sigue exigiendo muñecas visibles y
quietas). Esfuerzo: S ≤ 2 días, M ≤ 1 semana, L > 1 semana.

### 8.1 Tabla
| Función o métrica de WL Analysis | ¿La tiene KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| **Exportar vídeo con la telemetría quemada** (trazo, rejilla, datos, gráfico, cámara lenta, comparación 2×) | No | Media3 Transformer: `Composition` + `OverlayEffect`. Llevar el pintor de `TechniqueOverlay` a un `Canvas` puro y reutilizarlo por fotograma. Franja propia (rep, MCV, VL %, ROM, tempo) con «≈» donde toque. Salida ≤ 1080p en múltiplos de 16, reintento HDR → SDR, `Movies/KPKN`, Compartir. KPKN puede **superarlos** con formatos 9:16 y 1:1 y una tarjeta final de la serie | androidx.media3 transformer/effect (Apache-2.0) | **Alta** | M |
| **Adquisición del disco sin pose:** proponer en el primer fotograma, tocar para confirmar o cuadrado manual | Parcial: `PlateDetector` necesita muñecas quietas; existe la calibración manual | a) Búsqueda geométrica global (Hough multiescala en todo el fotograma, primer segundo; ranking por cobertura y consistencia de radio) con toque para confirmar. b) Después, un detector propio entrenado con datos propios o con licencia. Ataca el hueco de r02 (PENDING, cero intentos) | LiteRT (Apache-2.0); MediaPipe Model Maker (Apache-2.0), YOLOX o NanoDet (Apache-2.0). **Evitar Ultralytics (AGPL-3.0)** | **Alta** | M (a) / L (b) |
| **Recortar el vídeo** antes de analizar (con copia) | No: el importador no recorta | Media3 Transformer con `ClippingConfiguration`. Quita preparación y re-rack y alimenta la «ventana activa» | Media3 (Apache-2.0) | Media | S |
| **Modo marcador fiduciario** para mancuernas, kettlebells, máquinas y poleas | No | AprilTag o ArUco impreso de **lado** conocido. Escala por el lado medido (homografía o PnP con intrínsecos aproximados), **no por la caja alineada** (evita su error de √2). Detección por fotograma + el Kalman de KPKN. PDF con línea de control. Da m/s donde KPKN hoy solo cuenta por pose | OpenCV ArUco (Apache-2.0) o AprilTag 3 (BSD-2-Clause) | Media | M |
| **Rejilla métrica** y líneas verticales (inicio de la rep y posición actual) | Guía vertical sí; rejilla no | Capa con paso en cm usando los m/px del disco calibrado; solo con calibración; anclada al inicio de la rep; respeta la puerta de paralaje | Propia | Media | S |
| **Fuerza y potencia** de la barra; «% de carga» | No (MCV, pico ≥ 50 fps, VL %, 1RM) | P(t) = m·(a_v + g)·v_v con la señal de fase cero y a solo a ≥ 50 fps; potencia media y pico concéntrica por rep. **Nunca** la norma de potencias por componente ni recortar fuerzas negativas. Abstención sin peso, sin calibración o < 50 fps | Propia | Media | S |
| **CSV** por fotograma y resumen | No (sidecars internos) | Desde `BarTrack` + `SetTechniqueReport`: t, x, y, v, rep, fase y un resumen por rep | Propia | Media | S |
| **Agregados de una ventana elegida** (mín. y máx. con instante, media, media de fase) y bucle de fragmento | Por rep sí; ventana libre no | Selección en el gráfico del análisis y estadísticos sobre muestras medidas (sin predichas) | Propia | Baja | S |
| **Sincronizar la comparación** por velocidad pico o mínimo | Sincroniza en la rep 1 | Opción de anclaje: inicio de la CON, pico de v o fondo | Propia | Baja | S |
| **Seguimiento y gráficos en vivo** en la cámara | No (análisis al terminar; contador en vivo previsto en la fase 2) | `ImageAnalysis` + tracker ligero solo como vista previa, sin m/s hasta el análisis completo | CameraX (Apache-2.0) | Media-baja | L |
| **Cuenta atrás, linterna y cámara frontal** en el grabador | No consta (sin confirmar) | Controles en `TechniqueRecorderScreen` | CameraX | Baja-media | S |
| **Voz «start/stop»** para grabar | KPKN tiene voz en la sesión; no consta en el grabador | Reutilizar el motor de voz de KPKN o el reconocimiento del sistema en el dispositivo | Vosk (Apache-2.0) | Baja | M |
| **Grabación de pantalla** para entrenadores | No | MediaProjection; la exportación cubre casi todo | HBRecorder (MIT) | Baja | M |
| **Color por fuerza o potencia**; repeticiones previas en gris | Color por velocidad sí; gris sin confirmar | Opción del pintor | Propia | Baja | S |
| **Análisis con IA en la nube** con ejercicios accesorios | No | Choca con «El vídeo nunca sale del teléfono»: decisión del usuario. Alternativa sin vídeo: texto desde el `SetTechniqueReport` | — | Baja (decisión) | L |
| Unidades imperiales en técnica (ft/s, in, lbf) | Sin confirmar | Formateadores | Propia | Baja | S |

### 8.2 Lo que KPKN hace mejor
- **Pose completa** (33 puntos): perfiles de 23 ejercicios, ROM, profundidad, bloqueo, tronco, trayectoria respecto al medio pie,
  fases por jerk mínimo, TUT, tempo y observaciones. WL Analysis **no tiene pose**.
- **Disco robusto:**
  - Hough de gradiente con el anillo más externo y refino de Kåsa;
  - Kalman con puerta de Mahalanobis;
  - NCC de respaldo, pérdidas y readquisición, seguimiento hacia atrás.
  - WL usa KCF de escala fija **sin detección de pérdida**, con el modelo actualizándose aunque haya oclusión.
- **Geometría:** elipse con escalas horizontal y vertical (aviso < 0,85, rechazo < 0,70), corrección y supresión del paralaje,
  giro por gravedad, escala con la mediana de radios quietos. WL usa una sola caja inicial.
- **Honestidad:** niveles ALTA, MEDIA y BAJA, banderas, «sin dato la línea no se pinta» y m/s solo con calibración. WL siempre da
  cifras, incluso con el diámetro por defecto.
- **Señal:** Butterworth de fase cero a 6–8 Hz con el disco. La cadena de WL deja la velocidad en 2,7–4,0 Hz a 30–60 fps, lo que
  probablemente aplasta los picos.
- **Física:** WL calcula la «potencia total» como norma de componentes y descarta las fuerzas horizontales negativas. KPKN no
  publica cifras físicamente dudosas.
- **VBT:** MCV por rep, pérdida de velocidad y perfil carga-velocidad con 1RM y criterios estrictos. WL no tiene nada de eso.
- **Conteo:** segmentador con ACF, prominencias, parciales y fallidas, polaridad, mini-series y lados. WL usa pausas > 260 ms y
  falla sin pausa.
- **fps:** detección automática de cámara lenta (`captureFps`), VFR y abstención LOW_FPS. WL pide los fps a mano.
- **Privacidad:** todo en el teléfono. WL sube el clip para la IA.

### 8.3 Errores de WL que KPKN debe evitar al copiar ideas
- Escala tomada de una sola caja inicial, y sobre una caja alineada de un cuadrado que puede girar.
- Seguidor sin criterio de pérdida que sigue aprendiendo durante la oclusión.
- Potencia como norma de componentes y fuerza horizontal recortada.
- Umbral de «Avg(±)» que depende de la unidad de visualización.
- Inicio del movimiento medido, al parecer, sobre la velocidad sin suavizar.
- Instancia nativa global compartida entre la cámara y el proceso en segundo plano.

---

## 9. Índice de evidencias

**Java.** Rutas bajo `C:\kw\re\wlanalysis\jadx\sources\`. El árbol se borró al terminar; se regenera con
`C:\kw\re\_scripts\wlanalysis\FullDecompile.java`, igual que en §10.
| Tema | Evidencia |
|---|---|
| Cableado DI | `defpackage/my0.java:246` (`SwitchingBarFinder(Yolo11Finder, ArucoBarFinder)`); `defpackage/id3.java:142` (`SwitchingBarTracker(BarTracker, ArucoBarTracker)`); `id3.java:45, 60` |
| Modo y cambio en caliente | `barbellfinder/SwitchingBarFinder.java`; `barbelltracker/SwitchingBarTracker.java`; enum `defpackage/vn6.java` (PLATE, MARKER) |
| YOLO | `barbellfinder/Yolo11Finder.java`; `yolo/ObjectDetectorHelper.java` (0,5; 2; 3; CPU); `yolo/YoloDetector.java` (IoU 0,3, letterbox); `yolo/detect/TfliteDetector.java` (4 hilos, XNNPACK); `yolo/detect/PostProcessUtils.java` |
| Detección en la importación | `defpackage/m0.java:457` (primer fotograma, 1920); `defpackage/nf.java` caso 1; `defpackage/r00.java:71` (`b`: caja elegida, primera o manual; cadena de workers) |
| Detección en la cámara | `defpackage/od0.java:81` (`f`), `:96` (cada 15); `defpackage/d8.java` caso 7; `defpackage/e9.java:87` (guardar desde la trayectoria en vivo); `defpackage/vk0.java`, `defpackage/gn.java` caso 3 |
| Proceso | `processing/OnDeviceVideoProcessor.java:73`; `processing/LiftTrajectoryProcessor.java:53` (`getFilteredLocs`), `:114` (`lowPassFilter`); `defpackage/zd2.java` (MediaCodec/GL); `defpackage/hx1.java:67` (factor 1 o 2) |
| ArUco | `defpackage/cr.java` (diccionario 0, id 0, caja); `barbelltracker/ArucoBarTracker.java` (mantener la última); `org/opencv/objdetect/Objdetect.java:12`; `DetectorParameters.java:148` (subpíxel); PDF en `defpackage/bv4.java:144` (caso 11) |
| Métricas | `processing/Lift.java`: fuerza vertical (~2006), potencia (~2019, 2142), reps (~2154), `savgolFilter` 2189, `convolve` 2242, `getAcceleration` 2286, `getDiff` 2547, `getDisplacement` 2571, `getDisplacementGraph` 2589, `getRealPlateDiameter` 2714, `getSecondOrderDiff` 2748, `getVelocity` 2805, `initGrid` 2827, `synchronizeTo` 2964 |
| Parámetros y unidades | `processing/Parameter*.java`; `defpackage/rt6.java`, `iv3`, `py6`, `rm1`, `cs4`, `mb2`, `q3`; `defpackage/kk4.java` (Avg ± 0,04; smali confirmado) |
| Color del trazo | `defpackage/id7.java:634` (`T`), `:2017` (`y`); `defpackage/k31.java` (paleta); `defpackage/ot0.java` |
| Pintor y exportación | Nombres originales que conservan los `toString`: `ExportSettings` (`minTs`, `maxTs`, `crops`, `mirror`, `showGrid`, `gridSize`, `gridThickness`, `gridTransparency`, `showGridLabels`, `verticalLineColor`, `verticalLineStartColor`, `colorGradientType`, `slowMo`, `watermark`, `avgMode`, `exportedData`, `graphs`, `darkMode`, `includeWeight`, `includeTags`, `includeDate`, `includeParameterSummary`, `showTrajectory`, `pathColor`, `pathThickness`, `videoQuality`, `comparisonMode`, `splitPathIntoReps`, `units`, `resetGridAfterEachSet`), `RenderSettings` (path, position, vertLine, vertLineStart, grid, data, summary) y `PathRenderSettings` (`ts`, `minTs`, `maxTs`, `thickness`, `defPathColor`, `colorGradientType`, `splitPathIntoReps`); `export/ExportWorker.java:171` (`a`), `:586–668` (efectos, HDR), `:749` (MediaStore); `defpackage/rz1.java` (BitmapOverlay); `ii4`, `cm4`, `bs4`, `wy6`, `bm2`, `cm2`, `z47.java:28` (marca de agua); `defpackage/wz1.java` (compositor); `defpackage/yv0.java` (lado a lado o superpuestos) |
| CSV | `defpackage/g26.java`; ajustes en `defpackage/w44.java` |
| IA | `ai/GeminiAnalysisWorker.java`; `defpackage/vh2.java:118` (clip), `:205` (plantilla), `ga.java:107` (formato de la trayectoria), `hd.java:1488` (720 px); `defpackage/n42.java:186` (cuota); `defpackage/q8.java` (Catalyst) |
| Productos | `defpackage/a30.java:73–84`; `defpackage/b30.java` |
| Rutas | `defpackage/qj.java` (líneas 358–1052) |
| Voz | `defpackage/q47.java` (frases); `defpackage/lw2.java:325` (Vosk, 16 kHz) |
| Recorte | `defpackage/g17.java` (Transformer); `processing/PersistanceWorker.java` |
| Copia | `backup/BackupWorker.java:141` (`data.json`) |
| Grabación de pantalla | `defpackage/f94.java:2123` |
| Preferencias | `defpackage/z05.java` (claves) |

**Nativo.** `libnative-lib.so`; direcciones ELF de LLVM; en Ghidra, +0x10000.
| Símbolo | Dirección | Qué prueba |
|---|---|---|
| `_INIT_1` | 0x40e54 | Instancia global `KCFTracker(true, true, false, false)` + `TemplateTracker` con `Kalman` |
| `KCFTracker::KCFTracker(bool,bool,bool,bool)` | 0x41d74 | Parámetros de §4.2 |
| `KCFTracker::init` / `update` / `detect` / `train` / `getFeatures` | 0x41f44 / 0x42a90 / 0x42e14 / 0x427c8 / 0x42048 | Flujo KCF y pico ignorado |
| `Java_…BarTracker_init0` / `update0` / `finish0` | 0x40514 / 0x405b4 / 0x40650 | JNI sin control del pico (enlaces PLT/GOT del 17-oct: `native-call-links.json`) |
| `TemplateTracker::init` / `update` | 0x4524c / 0x455ac | Anillo, bilateral, SQDIFF y Kalman |
| `Kalman::Kalman` / `init` / `predict` | 0x457a0 / 0x4546c / 0x4549c | 4×2, velocidad constante |
| `getFeatureMaps` / `normalizeAndTruncate` / `PCAFeatureMaps` | 0x43fbc / 0x44874 / 0x44d44 | fHOG |
| `compute` / `compare`; `Java_…PHasher_*`; `Java_…ColorBarFinder_detectNative` | 0x40660 / 0x40a4a; 0x40af8, 0x40a68; 0x40bd4 | pHash; «color» = pHash |
| `Java_…YoloBarFinder_init0` / `detect0` / `finish0` | 0x40c50 / 0x40c52 / 0x40c5c | Cuerpos vacíos (2–10 B) |

**Pruebas guardadas** en `C:/kw/re/evidencias/wl-analysis-re-20261020\` (186 KB):
- `findings-summary.json`: síntesis estructurada propia;
- `hashes-sha256.txt`, `xapk-inventory.json`, `base-apk-inventory.json`, `armv7-apk-inventory.json`;
- `dex-app-classes.json`;
- `ui-strings-by-file.txt`;
- `settings-keys.txt`;
- `native-symbols.txt`, `native-elf-header.txt`, `native-ghidra-index.tsv`;
- `rea-ghidra-refusal.json`;
- `model-shapes.txt`;
- `filter-response.txt`;
- `signature.txt`.

No se guardó pseudocódigo ni código descompilado. Los identificadores E01–E24 del 17-oct siguen valiendo en
`artifacts/wl-analysis-re-20261017/rea-evidence.json`.

**Sin confirmar:**
- comportamiento en ejecución (no se ejecutó nada);
- el error de escala del modo marcador (§4.3);
- la mezcla de opacidad de la superposición;
- cómo se traduce la «calidad» de exportación al codificador;
- la resolución del `ImageAnalysis` en vivo;
- la licencia que aplica a los pesos YOLO11 y el origen archivo a archivo del código auxiliar de YOLO;
- la versión exacta de MPAndroidChart y del modelo Vosk;
- la causa de que su velocidad pico salga baja (hipótesis del §4.5);
- la exactitud real de cualquier cifra.

---

## 10. Método, recursos y limpieza
- **REA 6.5.0** con sus variables. `rea doctor` sano para Ghidra y JADX.
- **JADX:** `jadx-core` del jar de REA con un programa propio (`FullDecompile.java`); 12.100 archivos y 85 s, bajo el candado.
- **Ghidra:** por REA se rechazó. Headless directo (`run_ghidra_headless.ps1` + `ghidra_scripts/ExportDecomp.java`), 109 s y
  `MAXMEM` 3 GB, bajo el candado.
- **smali** con la API de dexlib2 (`SmaliDump.java`), porque a la CLI de baksmali le falta JCommander.
- **LLVM del NDK** para símbolos y cabeceras; **apksigner** para la firma (verificación estática).
- **Scripts propios:** `safe_extract.py`, `dex_classes.py`, `tflite_shapes.py` y `filter_response.py`, todos en
  `C:\kw\re\_scripts\wlanalysis\`. Python siempre con `-I`.
- **Candado** `C:\kw\re\pesado.lock`: mkdir atómico, `owner.txt` y liberación en `finally`. Comprobado ≥ 6 GB libres antes de cada
  herramienta pesada; hubo esperas por RAM y por el candado de otro agente.
- **Web:** solo se leyeron páginas públicas para contrastar: el tutorial oficial de WL Analysis, el repositorio público de KCFcpp
  (licencia y valores por defecto) y la ficha del plugin `ultralytics_yolo` (AGPL-3.0).
- **Limpieza:** se borraron los DEX descompilados, la salida de Ghidra (sin proyecto: `-deleteProject`), el smali y las
  extracciones (unos 377 MB). En `C:\kw\re\wlanalysis\` quedan solo registros y el JSON de síntesis (≈ 40 KB). No quedan
  procesos java ni Ghidra míos.
- **Nota de entorno:** un gancho de seguridad impide escribir con la herramienta de edición en la copia principal del repo. Como
  `artifacts/` está en `.gitignore` y el brief fija esa ruta, las pruebas se copiaron allí con `cp`.

> Nota del orquestador (20-oct): las pruebas de esta ronda se movieron de `artifacts/` (dentro de la copia principal del repo, que no
> debe tocarse) a `C:/kw/re/evidencias/wl-analysis-re-20261020/`. Las del 17-oct siguen en `artifacts/wl-analysis-re-20261017/`.
