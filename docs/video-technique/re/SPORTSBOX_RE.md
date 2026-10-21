# Sportsbox 3D Golf 1.24.0 · ingeniería inversa (20/21-oct-2026)

Análisis **estático** del XAPK que aportó el usuario. No se ejecutó ni se instaló la app; no se usaron teléfono, emulador, git
ni Gradle. Sala limpia: las funciones, fórmulas y algoritmos se describen con palabras propias; no se copia código descompilado ni
se extraen modelos o recursos para reutilizarlos. Los nombres de clase o método aparecen solo como evidencia (rutas bajo
`jadx/sources/com/sportsbox/`, formato `Clase.método:línea`). **[C]** = confirmado en código; **[D]** = deducido; «sin confirmar»
= no se pudo verificar.

Parte del trabajo lo hicieron tres subagentes con el mismo brief de sala limpia (interfaz y cámara; datos, nube y puente Unity;
comparación, puntuación, diagnóstico y béisbol). Sus hallazgos están integrados y sus referencias conservan el formato.

---

## 0. Lo esencial en una página

1. **Todo el análisis corre en el teléfono** [C]: caja de la persona (ML Kit), pose 2D propia (38 puntos, incluidos 4 del palo),
   «elevación» 2D→3D con una red temporal propia (ventana de 27 fotogramas, 35 puntos), escala métrica por el **ancho de cadera
   del perfil** (191 mm por defecto), **traslación absoluta** por mínimos cuadrados contra los rayos de cámara, y después trackers
   e indicadores en un «ActionSDK» dirigido por tablas CSV. La nube solo recibe los resultados (y los vídeos) y presta la IA
   conversacional.
2. **No corrige la perspectiva**: exige **Face-On** o **Down-the-Line**, clasifica cuál es con un modelo lineal sobre los puntos
   y usa un modelo 3D distinto para cada vista; la salida DTL se **gira −90°** para expresarla en la convención Face-On. Sin
   homografía, sin IMU y con una **focal por defecto** (1300 px a 1080p) porque la app Android **nunca rellena** los intrínsecos.
3. **Segmentación en 16 posiciones** (ADR, B45, BCH, BCV, BAH, TOP, DCV, DCH, DAH, DA30, D45, IMP, F45, FCH, FCV, FIN) a partir
   de **la velocidad angular del palo** (impacto = pico), umbrales de ángulo del eje del palo en el plano frontal y reversión
   monótona (TOP); hay otro detector incremental para la cámara en vivo y detectores de bateo y pitcheo de béisbol.
4. **Catálogo**: 107 trackers por fotograma (giros, flexiones, inclinaciones, desplazamientos, velocidades lineales y angulares,
   centro de masa) sobre **marcos de referencia por segmento** («V-frames») con ángulos de **sistema de coordenadas articular**
   (JCS), y ≈300 indicadores escalares definidos como **fórmulas** (valor en posición, mín/máx por fase, tiempos, secuencia
   cinemática, ratios y contribuciones), comparados con bases de datos de profesionales (μ, σ) por sexo y palo.
5. **Comparación**: referencias 2D (otro vídeo) y 3D (avatar fantasma) sincronizadas por índice de fotograma en Unity; en
   números, z-scores contra la base de pros, percentiles, «Swing Score» (velocidad 0,3 · consistencia 0,4 · eficiencia 0,3).
6. **No exporta vídeo con telemetría**: solo grabación de pantalla con voz del visor 3D, guardada en galería y compartible en su
   red interna.
7. **Lo más valioso para KPKN**: la cadena «ancho de cadera → escala → traslación por rayos de cámara» para dar coordenadas
   métricas absolutas a los puntos «world» de MediaPipe; los ángulos JCS sobre marcos por segmento (robustos a la vista, D42);
   el segmentador de eventos balísticos (pico de velocidad angular + reversión monótona + umbrales de ángulo); la secuencia
   cinemática (orden de transición y de picos, ganancias y contribuciones); y el motor de trackers e indicadores dirigido por
   tablas.

---

## 1. Identidad

### 1.1 Paquete, archivos y firma
| Elemento | Valor |
|---|---|
| Paquete | `com.sportsbox.golfai`, versión **1.24.0-6-release** (código 12400006), minSdk 29, targetSdk 37, compilado con SDK 37 |
| XAPK | 6 entradas: 5 APK + `manifest.json` (APKPure, `xapk_version` 2). SHA-256 del XAPK `569a5ecfbe1efe4d25ab323add3e6d900850939b1bbd5ed6b959a6b8b287f4c3`, 424.879.585 B |
| Firma | Archivos `META-INF/BNDLTOOL.*` y `stamp-cert-sha256` (firma de Play App Signing); no se verificó con apksigner (sin confirmar) |
| Protección | `com.pairip.licensecheck` y permiso `CHECK_LICENSE` |

| APK interno | Tamaño (B) | SHA-256 |
|---|---:|---|
| `com.sportsbox.golfai.apk` (base; 10 DEX, 95 MB de código, recursos y configuración) | 119.094.028 | `97b292e85351362eb0a20b4972e63440e56f9e7233e2e99ee3873938927db100` |
| `config.arm64_v8a.apk` (bibliotecas nativas) | 210.175.432 | `de60cceff59836a7a82cad7bdd402bb3d1aaca4f241fe4b466b22969227d9b7e` |
| `assets_pack.apk` (modelos de ML) | 94.151.492 | `525e7af14f8ee4beded0d21243439eb06e66788f432dda2d19b1c01b0ea73a29` |
| `config.hdpi.apk` | 1.308.130 | `013ca2d0ffd32d3571a8426288a6e95b3812df361e2114d809e2ea5a710f31ea` |
| `config.en.apk` | 147.865 | `94e7b9dad090b095cba2daf2b27d4107a3caf818d89a0be5ff51bcf87f1f8612` |

Inventarios completos y hashes en `C:/kw/re/evidencias/sportsbox-re-20261020/` (`hashes-sha256.json`, `inventario-*.json`).

### 1.2 Arquitectura
- **Kotlin + Java**: Compose (1.10, Material 3 1.4) y vistas clásicas con data binding, Hilt 2.57.2, Room 2.8.2 (base de datos
  v137), WorkManager 2.10.5, Retrofit/OkHttp, kotlinx.serialization, Gson, Paging 3.4.2, Navigation 2.9.5. Kotlin 2.2.20, Gradle
  8.13, AGP 8.13.2. **Sin R8 agresivo**: los paquetes propios conservan nombres (`com.sportsbox.ui|data|sdk|analysis|camerax|…`,
  7.656 clases contando las internas, 2.263 sin ellas).
- **Unity 6000.2.14f1 con IL2CPP** para el avatar 3D («mannequin»), en un **proceso aparte** (`android:process="com.sportsbox.unity"`)
  y con Addressables (`mannequinparts`, `text_assets`). Tipos C# conservados: `AvatarManager`, `RenderingManager`, `Mannequin`,
  `MannequinChest/Pelvis`, `GolfClub`, `GrassArea`, `Ball`, `LeadWristTracker`, `CameraBridge`, gestos de mover/rotar/zoom,
  `Skybox`, `Watermark(s)`, `Overlay`, `JointCollider`.
- **C++ con NDK**: `libnanodet.so` (detector NanoDet sobre ncnn, con Vulkan) y `libmain.so` (puente Unity).
- **OpenCV 4.12.0** (Java y nativo), **TensorFlow Lite** (`libtensorflowlite_jni`, delegado GPU, y un delegado Flex de 75 MB
  que ninguno de los cinco modelos necesita: todos usan operadores integrados; probable dependencia residual, sin confirmar),
  **ML Kit Object Detection 17.0.2**.
- **ABI**: solo `arm64-v8a` en este XAPK.

### 1.3 Bibliotecas nativas (`config.arm64_v8a.apk`, `lib/arm64-v8a/`)
| Biblioteca | Tamaño | Para qué |
|---|---:|---|
| `libtensorflowlite_flex_jni.so` | 75,7 MB | Delegado Flex (operadores TF); sin uso aparente |
| `libil2cpp.so` + `libunity.so` + `libmain.so` | 55,1 + 19,5 MB + 6,7 KB | Unity (avatar 3D) |
| `libopencv_java4.so` | 33,2 MB | OpenCV 4.12.0: `warpAffine`, `undistort`, `FastLineDetector`, `HoughCircles`, `VideoCapture`, álgebra |
| `libmlkitcommonpipeline.so` | 11,0 MB | ML Kit (detección de objetos) |
| `libncnn.so` + `libnanodet.so` | 9,3 MB + 421 KB | NanoDet-Plus en ncnn (solo en compilaciones de desarrollo) |
| `libtensorflowlite_jni.so` + `libtensorflowlite_gpu_jni.so` | 3,0 + 2,7 MB | Intérprete TFLite y delegado GPU |
| `libimage_processing_util_jni`, `libsurface_util_jni`, `libdatastore_shared_counter`, `libandroidx.graphics.path` | — | CameraX, DataStore, Compose |

`libnanodet.so` exporta `JNI_OnLoad`, `…NanoDetBoundingBox$Companion_init/detect` y los métodos C++ `NanoDet::preprocess`,
`decode_infer`, `disPred2Bbox` (DFL) y `nms`; depende de `libncnn`, `libvulkan` y `libjnigraphics`. No hizo falta Ghidra.

### 1.4 Modelos de ML
| Modelo | Tamaño | Entrada | Salida | Notas |
|---|---:|---|---|---|
| `Sportsbox_Golf_Pose2D_FO_DTL_38_270K_20241217.tflite` | 11,2 MB | `[1,256,192,3]` float32, RGB/255 normalizado con media y desviación ImageNet | `[1,64,48,38]` mapas de calor | 78 operadores (40 CONV, 18 depthwise, 2 RESIZE_NN, 1 MAX_POOL): red convolucional ligera con cabeza de mapas de calor (estilo MobileNet/SimpleBaseline, sin confirmar). 38 puntos = 34 corporales + **4 del palo** (`CLUB_MID_HANDS`, `CLUB_TOP_OF_HANDLE`, `CLUB_HOSEL`, `CLUB_HEAD_TOE_TOP`). Un solo modelo para Face-On y DTL. «270K» coincide con el tamaño del conjunto o del modelo (sin confirmar) |
| `Sportsbox_Golf_Pose3D_FO_35_2M_20231004.tflite` (Face-On) | 36,6 MB | `[1,27,35,3]` float32: 27 fotogramas × 35 puntos × (x, y normalizados, confianza) | `[1,1,35,3]`: 35 puntos 3D del fotograma central | 37 operadores: 7 CONV_2D con dilatación (SPACE_TO_BATCH/BATCH_TO_SPACE), 7 ADD residuales, 3 MUL, 1 LOGISTIC; ≈9,1 M parámetros en float32. Es una **red temporal convolucional de «lifting» 2D→3D** con campo receptivo 27 (familia VideoPose3D, sin confirmar) |
| `Sportsbox_Golf_Pose3D_FO_DTL_35_2M_20240305.tflite` (Down-the-Line) | 36,6 MB | ídem | ídem | Mismo tamaño y mismos operadores: misma arquitectura, pesos distintos, entrenada para vídeos DTL |
| `nanodet-plus-m-1.5x-416-golf-sim.param/.bin` (ncnn) | 28,5 KB + 9,7 MB | imagen 416 (por el nombre), 240 capas | 4 niveles × (clases + 4×8 DFL) = 34 canales → **2 clases** | Detector NanoDet-Plus-m 1.5×: clases `PERSON` y `GOLF_BALL` [C por uso]; umbral 0,4 y NMS 0,4; GPU Vulkan. **Solo en compilaciones de desarrollo** |
| `mlkit_odt_localizer/localizer_with_validation.tflite` + `mobile_object_localizer_3_1_anchors.pb` | 1,25 MB | `[1,192,192,3]` uint8 | cajas SSD en 4 escalas | Localizador genérico de ML Kit (**el detector de persona en producción**) |
| `mlkit_odt_default_classifier/labeler_with_validation.tflite` | 1,7 MB | `[1,224,224,3]` uint8 | 6 clases genéricas (moda, hogar, animal, comida, lugar, planta) | Clasificador de ML Kit; sus etiquetas no se usan |

Versiones internas: ActionSDK `v12.3.11`, Unity lib `752fbf80…` (`ModelConfigsKt:12-21`).

### 1.5 Bibliotecas de terceros (versión · licencia)
Versiones de `META-INF/*.version` y `*.properties` (`libs-versions.txt`). Licencias según cada proyecto público: verificar antes
de usar.

| Biblioteca | Versión | Licencia | Uso |
|---|---|---|---|
| OpenCV | 4.12.0 | Apache-2.0 | Decodificación de vídeo, afines, undistort, segmentos de línea, Hough de círculos, PnP por álgebra |
| ncnn + NanoDet-Plus (código) | sin confirmar | BSD-3-Clause / Apache-2.0 (pesos propios) | Detector persona/bola (dev) |
| TensorFlow Lite (+ GPU, + Flex) | sin confirmar | Apache-2.0 | Pose 2D y 3D |
| ML Kit object-detection / vision-common | 17.0.2 / 17.3.0 | Términos de Google (propietaria) | Caja de la persona (producción) |
| Unity | 6000.2.14f1 | Propietaria | Avatar 3D |
| CameraX (core, camera2, camera2-pipe, video, view, viewfinder) | 1.6.1 / 1.5.1 | Apache-2.0 | Cámara; Camera2 directo para alta velocidad |
| Media3 (ExoPlayer, Transformer, Effect, Muxer, UI) | ≈1.9.0 (cadena en DEX; sin confirmar) | Apache-2.0 | Reproducción, recorte |
| Firebase (Auth 24.0.1, Analytics 23.0.0, Firestore, Storage, Messaging, Remote Config, Crashlytics, App Check) | varias | SDK propietarios | Cuentas, chat, subida, telemetría |
| Play Billing 9.1.0, Play Integrity 1.4.0, Places 5.0.0, Maps 17.0.0, Location 21.0.1, reCAPTCHA 18.8.0 | — | SDK propietarios | Pagos, integridad, ubicación |
| Mixpanel, Lottie, Glide, Coil, uCrop, Markwon, gRPC 1.62.2, Tink, OpenCSV, Apache Commons (lang3, collections4), Volley | — | Apache-2.0 / MIT | Analítica, imágenes, recorte de foto, Markdown, red, CSV |
| HBRecorder (copia propia en `ui/inbox/recorder`) | — | MIT | Grabación de pantalla |

### 1.6 Permisos y sensores
- **Cámara y audio**: `CAMERA`, `RECORD_AUDIO`; `uses-feature` cámara y autoenfoque obligatorios, **Vulkan obligatorio** (Unity y ncnn),
  GLES 2.0.
- **Ubicación**: `ACCESS_FINE/COARSE_LOCATION`, `ACCESS_MEDIA_LOCATION` (clima de la sesión con OpenWeatherMap y Places).
- **Pantalla**: `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `SYSTEM_ALERT_WINDOW` (control flotante de la grabación de pantalla).
- **Medios**: `READ_MEDIA_IMAGES/VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`, almacenamiento heredado.
- **Pagos y sistema**: `BILLING`, `CHECK_LICENSE`, `AD_ID`, `ACCESS_ADSERVICES_*`, `POST_NOTIFICATIONS`, `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED`, FCM.
- **Sensores**: el código propio **no usa IMU** (sin `SensorManager` ni tipos de sensor) [C]. La nivelación se delega a la guía
  visual y de voz.
- **Enlaces profundos**: `https://3dgolf.sportsbox.ai/…` y `sportsbox-development.web.app` (Golf Genius Coach360).

---

## 2. Inventario completo de funciones, pantalla por pantalla

### 2.1 Cuentas, perfil y niveles
- **Onboarding** (`UserOnboardingActivity`): carrusel, registro (email o Google; edad mínima 13; consentimiento «usamos tus vídeos
  para mejorar la IA» que, si se rechaza, **no crea la cuenta**: `CreateAccountFragment:384-413`), verificación de email,
  activación de cuentas creadas por el entrenador, suscripción.
- **Golf Profile** (`BodyMeasurementsScreen`): sexo, altura (pies-pulgadas o cm), **ancho de cadera** automático por tabla
  (`sportsbox_hipwidth_table.csv`: por altura, juvenil/adulto, mín-defecto-máx por sexo) con corrección manual, mano dominante.
  Aviso: «las medidas corporales escalan el modelo 3D». Sin perfil completo no se crea sesión.
- **Niveles** (`AccessLevel`): Free, 3D Practice (alumno de pago), Pro-Lite y Pro-Premium (entrenador; «3D Pro»), Player Plus,
  Studio y Enterprise. Límites: 5 sesiones (Free), watchlists 1/3/ilimitadas, trackers y «number tiles» con nivel propio,
  detección automática y guía de voz solo de pago, referencia/dibujo/grabación de pantalla de pago, 2 dispositivos (coach) o 1.
  Productos Play `sportsbox_*` mensuales y addons (LLM, SAMI beta, Foresight, World Wide Golf).

### 2.2 Grabación (`CameraMainActivity`, `camerax/**`)
- **Preparación**: golfista (yo o alumno), palo (maderas, híbridos, hierros, wedges), ángulo **Face-On / Down-the-Line** («algunas
  medidas no están disponibles en DTL»), número de swings (evaluación), guía de instalación: lugar luminoso, elegir ángulo,
  **1,8–3 m** («6-10 ft») de la cámara, trípode, cuerpo entero (imágenes `recording_guid1-4`).
- **Motor**: CameraX si algún rango AE llega a ≥ 60 fps; si no, Camera2 en sesión de alta velocidad (120 fps); si no hay alta
  velocidad, se ofrece la cámara nativa (`CameraCapabilityEvaluator.evaluate:35`). **fps: 120/60/30** (no hay 240 en la app;
  240 solo al importar). Grabar exige ≥ 60 fps. Camera2: mayor tamaño de alta velocidad disponible, H.264 10 Mb/s, **sin audio**,
  estabilización apagada; análisis en vivo a 640×480 y ≤ 15 fps.
- **Exposición**: modo manual (obturador base 1/(2·fps), ISO por luminancia central) o «Auto (Low Light)» («sin velocidad del palo
  en exposición automática»), deslizador ±2 EV.
- **Encuadre y voz**: silueta guía (65 %×55 % de la vista previa), textos «céntrate», guía de voz TTS en inglés (acércate, aléjate,
  paso a la izquierda/derecha, inclina el teléfono), cuenta atrás «3, 2, 1, Go», parada automática al completar N swings.
- **Detección automática de swing en vivo** (`AutoSwingDetectionService`, `SwingActionDetector`): caja + pose 2D por fotograma a
  ≤ 15 fps → detector incremental del SDK (§4.7) → eventos «detectado», «fin», «tracking perdido»; cada swing se recorta a
  `<nombre>_swing_i.mp4` con `MediaExtractor`/`MediaMuxer` (solo vídeo, alineado a fotograma clave).
- **Recorte manual** (`VideoCropActivity`): tira de miniaturas, rango, chips por swing, fps normalizado (115-125→120, 55-65→60,
  27-33→30), Media3 `Transformer` con `ClippingConfiguration`.
- **Sesión**: nombre «Pose Analysis», clima, dispositivo registrado en el servidor; «Analyze» abre el visor; evaluación = N clips.

### 2.3 Importación
Selector del sistema (`video/*`), recorte, **fps detectado y ajustado a 60/120/240** (tolerancia ±5; si no, 30) con aviso si ≠ 120
(«se recomienda 120 fps; forzar un fps incorrecto puede dar swing & miss o trackers inexactos»), golfista, palo y ángulo.

### 2.4 Análisis y visor (`UnityActivity`, proceso aparte)
- **Progreso**: descarga (si el vídeo está en la nube), «Fetching frames», «Generating 2D», «Generation 3D» (con «SKIP to 2D
  only»), «Generating trackers». Errores: «We failed to detect swing», «Swing and a miss!» (confianza 2D media < 0,89 o fps < 60),
  fallo de Pose 3D, fallo de trackers; «Only 2D analysis available. 3D features are only available for FACE ON videos» (solo en
  recursos).
- **Visor**: modos solo 2D (ExoPlayer), solo 3D (avatar Unity) y partido; reproducción 0,1/0,25/0,5/1×, dos deslizadores de
  fotograma (usuario y referencia) con **Sync**, chips de posiciones del swing, **punto de vista** del avatar: desde el objetivo,
  detrás, debajo, arriba, Face-On y DTL; zoom y arrastre; dentro de Unity, gestos de órbita, rotación y zoom (`CameraBridge`,
  sin confirmar su alcance).
- **Visuals** sobre el avatar: palo, líneas de hombros, caderas y rodillas, verticales de cabeza, pecho y pelvis, trayectorias de
  manos y hosel, ángulo de la muñeca adelantada, planos de hombros, varilla y vertical (`sportsbox_tracker_visuals.csv`).
- **Number tiles**: baldosas con valor, unidad y color por rango; favoritos; sets populares (giros y desplazamientos, calidad de
  impacto, secuencia cinemática, orden de transición, «Cool Clubs» para ajuste de palos).
- **Tempo** (`BCKSWNGTIME`, `DWNSWNGTIME`, `FTHRUTIME`, `SWNGTIME`, `TEMPO`, formato «x,x:1»).
- **Ajuste manual de posiciones**: tocar el botón resaltado, desplazar a un fotograma nuevo, guardar o restaurar; los indicadores
  se **recalculan** con las posiciones corregidas (`UnityRepository.updateCustomPositions:761-878`).
- **Referencias**: 2D (grabar, galería, swings guardados, alumnos) y 3D (modelos «pro» de `allUserRefVid`, swings propios, alumnos);
  el 3D de la referencia se **reprocesa en el teléfono** con el mismo SDK.
- **Dibujo** (modo partido): línea, flecha, círculo, rectángulo, lápiz y **ángulo entre dos segmentos**; colores, deshacer/rehacer.
- **Barra**: analizar de nuevo, editar, reportar incidencia, añadir o quitar referencia, métricas de bola (solo desarrollo).
- **Grabación de pantalla con voz** («Record Screen») y revisión posterior (§5).
- **AI Highlights/Insights** (resumen en lenguaje natural generado en el servidor a partir de los indicadores) y **SAMI**, chat con
  LLM en la nube (hilos, prompts predefinidos, me gusta/no me gusta).

### 2.5 Aprendizaje y objetivos
- **Watchlists** («Goals»): 45 presets por fallo (p. ej. HANGING_BACK, NO_SLIDE_BEFORE_TOP, REVERSE_PIVOT, SCOOPING,
  FLAT_SHOULDER_PLANE) con vídeo, carátula, indicadores y límites; creación propia (elegir trackers → rango → nombre → asignar a
  alumno); evaluación con puntos por sesión y «consistency score».
- **Swing Genius**: fallos detectados, consejos «How to fix», drills y vídeos.
- **Swing Score** e informe (velocidad, eficiencia, consistencia, checklist, «superpowers»), rangos por hándicap y edad.
- **Practice Guide / Drills** (CSV de guías y vídeos), tutoriales, encuesta del tiro (salida, curva, altura, contacto).

### 2.6 Entrenador, alumnos, feed e inbox
Lista y búsqueda de alumnos, invitaciones, creación de cuentas de alumno con datos corporales, sesiones por alumno, regalo de 3D
Practice; feed social (posts, comentarios, me gusta, guardados); chat con texto, nota de voz, foto y vídeo (Firestore);
notificaciones; integración Golf Genius Coach360 por enlace profundo.

### 2.7 Ajustes
Perfil y Golf Profile; unidades de distancia (cm/in) y velocidad (mph/m/s); subida por datos móviles; notificaciones; suscripción;
soporte y legal; **ajustes de desarrollador** (inertes en producción): NanoDet, detección por impacto de bola, saltarse el «swing
and miss», trackers experimentales.

### 2.8 Béisbol (sin interfaz visible)
El SDK incluye servicios de **bateo y pitcheo** (`sdk/baseball`) con sus tablas (`configs/baseball/*`): posiciones STC, HND, LFL,
LFP, SWG, IMP, FIN (bateo) y STC, MKH, LFP, SWG (máxima rotación externa), IMP (suelte), FIN (pitcheo); trackers corporales de golf
más `BAT_SWING_SPEED` y `BAT_SWEET_SPOT_SPEED`; rangos por edad (Male_Age12-18). Es código compartido con la app «Sportsbox 3D
Baseball» (sin confirmar que se pueda activar aquí).

### 2.9 Trabajos en segundo plano
`UploadWorkerService` (resultados del análisis y, si falla, también el vídeo), `SessionUploadWorker` (sesiones y vídeos),
descargas, FCM, `ScreenRecordService`.

---

## 3. Catálogo de métricas

### 3.1 Convenciones
- **Unidades internas**: milímetros y grados; se convierten a pulgadas, cm, mph o m/s al mostrar (`CommonServiceImpl.trackerDecimalPlaces:193-232`).
- **Sistema global «AMM»**: X a lo largo de la línea de los pies (hacia el objetivo para diestro tras el cambio de signo), Y hacia la
  bola, Z vertical; origen en el GLOBAL_ORIGIN. Los desplazamientos (sway, thrust, lift) valen **0 en ADR**.
- **Vistas**: cada tracker e indicador declara si vale en Face-On, DTL o ambas (`SWING_ANGLE`); en DTL solo se muestran los
  marcados; «Flip if Lefty» invierte el signo para zurdos.
- **Nivel**: `subscriptionLevel` (Free, 3D Practice, Pro-Lite) y `enableOnProduction`; `analysisEnable` = Always, «Disable Imp»,
  «Disable Auto» (semántica sin confirmar; probablemente se desactivan cuando el impacto se ajusta a mano o en detección automática).

### 3.2 Trackers (señales por fotograma): 107 en golf (`sportsbox_trackers.csv`; listado en `trackers-golf-compact.txt`)
| Familia (algoritmo) | Trackers | Definición (palabras propias) | Unidad / filtro |
|---|---|---|---|
| **Giro (JCS, eje ARZ)** | PELVIS_TURN, CHEST_TURN, SHOULDERS_TURN, SPINE_TURN (**X-Factor** = pecho respecto a pelvis), LEAD/TRAIL_HIP_INT_EXT, LEAD/TRAIL_SHOULDER_INT_EXT | Ángulo de rotación del marco del segmento respecto al marco de referencia (GLOBAL, pelvis o pecho) alrededor del eje vertical, con la convención del sistema de coordenadas articular (eje flotante); negativo = alejado del objetivo | grados; «U» (desenvolver), «90» en algunos |
| **Flexión (BEND, ARX)** | PELVIS_BEND, CHEST_BEND, SPINE_BEND, SHOULDERS_BEND, LEAD/TRAIL_HIP_FLEX, LEAD/TRAIL_SHOULDER_FLEX, LEAD/TRAIL_WRIST_FLEX, LEAD/TRAIL_FOOT_FLEX | Inclinación adelante/atrás del segmento (asin del producto del eje Y del segmento con el Z de referencia) | grados |
| **Inclinación lateral (SBEND, ARY)** | PELVIS_SIDE_BEND, CHEST_SIDE_BEND, SPINE_SIDE_BEND, SHOULDERS_SIDE_BEND, LEAD/TRAIL_WRIST_R_U_DEV, LEAD/TRAIL_FOREARM_SUP_PRO, LEAD/TRAIL_HIP_AD_AB, LEAD/TRAIL_SHOULDER_AD_AB, LEAD/TRAIL_FOOT_IN_EVER | Elevación del eje X del segmento (ángulo sobre la horizontal); positivo hacia el lado trasero | grados; SMOOTH6-8 en muñecas |
| **Desplazamiento (DISP)** | PELVIS/CHEST/HEAD/COM/MID_HANDS/CLUB_HOSEL/CLUB_HEAD_{SWAY,THRUST,LIFT}, LEAD/TRAIL_KNEE_{SWAY,THRUST,LIFT} | Proyección de (punto − punto en ADR) sobre X (sway, signo invertido), Y (thrust) o Z (lift) del marco global | mm → pulgadas |
| **Ángulo proyectado (PANG)** | SHAFT_ANGLE_FACEON, SHAFT_ANGLE_DTL (postproceso U, NEG, 90), LEAD_ARM_ANGLE_FACEON, LEAD_FOREARM_ANGLE_FACEON, TORSO_FORWARD/SIDE_TILT, HIP/SHOULDER_TURN_PROJECTED, CLUBHEAD_SHOULDER/ELBOW/MIDHANDS_DTL (planos de hombros, codo y varilla) | atan2 de las componentes del vector (joint2 − joint1 o eje del segmento) en dos ejes del marco de referencia | grados |
| **Ángulo entre vectores (VANG)** | LEAD/TRAIL_KNEE_FLEX, LEAD/TRAIL_ELBOW_FLEX (3 articulaciones; 180 = recto), LEAD_WRIST_ANGLE (antebrazo vs. varilla, ejes Z y −Z) | Ángulo incluido | grados |
| **Distancia (DIFF)** | STANCE_WIDTH, STANCE_WIDTH_PROJECTED, ARC_WIDTH (pecho↔manos), SWAY_GAP y LIFT_GAP (pelvis↔pecho en X o Z), SHAFT_LENGTH, CLUB_LENGTH, ELBOW_WIDTH, TRAIL_ELBOW_HIP_SEPARATION, CLUB_HAND_THRUST_GAP, COM_STANCE_SWAY | Norma de la diferencia o su proyección en un eje | mm |
| **Velocidad angular local (LOCTURNVEL, ARLOCZ)** | PELVIS_TURN_SPEED, CHEST_TURN_SPEED, SHAFT_TWIST_SPEED | Velocidad angular del marco del segmento alrededor de su propio eje Z, a partir de la variación de sus ejes entre fotogramas × fps | °/s; SMOOTH6-8 |
| **Velocidad de barrido (VVELTIME)** | LEAD_UPPERARM_SWING_SPEED, LEAD_FOREARM_SWING_SPEED, SHAFT_SWING_SPEED | Ángulo entre orientaciones consecutivas del eje del segmento × fps; postproceso «N» hace negativo el backswing (hasta el mínimo cerca de TOP ± 0,1 s) | °/s; IMP6 (varilla) |
| **Velocidad de un ángulo (VVEL)** | LEAD_WRIST_RELEASE_SPEED | Derivada del ángulo antebrazo–varilla × fps | °/s; SMOOTH8 |
| **Velocidad lineal (LINVEL)** | PELVIS/CHEST/MID_HANDS_{SWAY,LIFT,THRUST}_SPEED y resultantes PELVIS_SPEED, CHEST_SPEED, MID_HANDS_SPEED (**Hand Speed**), CLUB_HOSEL_SPEED, **CLUB_HEAD_SPEED** | Diferencia de posición entre fotogramas × fps, proyectada o en norma | mm/s → mph; SMOOTH6-8, IMP6 (palo) |
| **Centro de masa** | COM_* | Suma ponderada de 18 puntos con pesos por sexo (`CoGCoefficient:15`), promedio si no se declara sexo | — |

Trackers comentados (sin uso): cabeza (turn/bend/side bend), rodillas como segmento, versiones «SP» (plano de la varilla) de
antebrazo y muñeca, aceleraciones del palo.

### 3.3 Indicadores (escalares por swing): 304 en golf (`sportsbox_indicators.csv`; listado en `indicators-golf-compact.txt`)
Cada indicador es una **fórmula** sobre trackers y posiciones (§4.9). Familias:
- **Valor en posición** (`TRACKER@POS`): p. ej. `PlvTrnTop`, `ChstTrnImp`, `XFctrImp`, `PlvSwyImp`, `LdWrstAngTop`, `ShftDTLAngDCV`,
  `SwyGapImp`, `ClbHdHndThrstDiffDCH`… Hay además **indicadores generados automáticamente** para cada tracker en cada posición y
  MIN/MAX/ROM por fase (BCKSWNG, DWNSWNG, FTHRU) (`SwingIndicatorService.generateIndicatorConfigs:303-324`).
- **Extremos por fase**: `PlvTrnMaxNrTop` = mín(PELVIS_TURN[ADR:TOP]), `XFctrMaxNrTop`, `ChstSBndFthruMax`, `PlvDrpMaxNrImp`,
  `HandLiftMax`, ROM de giro, flexión, inclinación y sway en el swing completo.
- **Tiempos**: `BckSwngTime` = TOP − ADR, `DwnSwngTime` = IMP − TOP, `FThruTime`, `SwngTime`, **`Tempo`** = backswing/downswing
  (pros ≈ 3,8-3,9:1), `HndSpdMaxTime`, `TransitionTempo`, tiempos de transición (`PlvSwyTransTm`, `PlvTrnTransTm`, `PlvLftTransTm`…:
  cuánto antes del TOP cambia de sentido cada movimiento), `WrstRlsTime` y `WrstRlsPrcnt` (suelte de muñecas: ReleaseTime con
  umbral 15 °/s desde TOP, como porcentaje del downswing).
- **Posiciones compuestas** (definen fotogramas): `BkSwgLeadArmParallel` (BAH), `DwnSwgLeadArmParallel` (DAH) y `DwnSwgLeadArm30`
  (DA30) = instante más cercano a −90° o −30° de `LEAD_ARM_ANGLE_FACEON` en su fase.
- **Secuencia cinemática**: máximos de velocidad (`PlvTrnSpdMax`, `ChstTrnSpdMax`, `LUArmSwngSpdMax`, `LFArmSwngSpdMax`,
  `ShftSwngSpdMax`, `HndSpdMax`, `ClbHdSpdMax`), **ganancias** (`CoreSpdGain` = pecho − pelvis, `ShldrSpdGain`, `ElbSpdGain`,
  `WrstSpdGain`), **factores de ganancia** (ratios; `HndClbSpdGainFctr` = «Release Factor» = velocidad del palo / de las manos),
  **contribuciones** (% de la velocidad de la varilla: piernas, core, hombro, codo, muñeca), **orden de transición** y **orden de
  picos** (pelvis → pecho → brazo → palo; `Order(…)` devuelve el puesto), separaciones de tiempo entre transiciones y entre picos,
  aceleraciones medias (hacia el mínimo del backswing, hacia el máximo, hacia el impacto), % del downswing en que ocurre cada pico.
- **Trayectorias**: `HndPathLenBkswng/Dswng`, `ArcLength`, `ClbhdPathLngth*` (longitud 3D del camino de manos o cabeza del palo),
  ratios backswing/downswing, `ClbHdLowPtImp` (punto más bajo de la cabeza entre DCH y FCH), `ClbHdAvgAccel` y `ClbHdAvgGs`,
  `CCTempo` (ajuste de palos).
- **DTL**: ángulos de la varilla en ADR, BCV, DCV e IMP, diferencias (shallowing/steepening), **`SwingDir_v_Stance`** (ángulo
  entre el plano de la zona de impacto D45-IMP-F45 proyectado al suelo y la línea de los tobillos: in-to-out positivo), varilla
  frente a planos de hombros, codo y varilla en DCV (over-the-top), `MidHndThrstDiff`, `ClbHdHndThrstDiff*`.
- **Centro de masa**: `ComStcPct*` (posición del COM en % del ancho de postura, trasero 0 % → adelantado 100 %), `ComLftintoImp`,
  `ComDrpMaxDwnSwng`.
- **Otros**: `PlvLftintoImp`, `ChstLftintoImp` y sus velocidades, `HandPullDown`, `ChstSldTrans`/`PlvSldTrans` (deslizamiento hacia
  el objetivo antes del TOP), `XFctrStrtch` (estiramiento del X-Factor en la transición), `ChstTrnMaxTopdiff`, `ClbSpdtoHght`.
- **Puntuaciones** (calculadas aparte y expuestas como indicadores): `SpeedScore`, `EfficiencyScore`, `EfficiencyNoContrib`.

La descripción de cada indicador incluye **promedios de pros** por sexo y palo (driver / hierro medio): p. ej. giro máximo de
pelvis −43°/−45° (driver), pecho −100°/−103°, X-Factor −64°, velocidad de cabeza del palo 117/95 mph, velocidad angular de la varilla
2205/1846 °/s, tempo 3,9:1, suelte al 36 % del downswing.

### 3.4 Rangos, colores, puntos y abstención
- **Bases de rangos** (`range_database_{M,F}Pro{Drv,Midir}.json`): por indicador, media y desviación de pros; por tracker, mín/máx por
  fase (BCKSWNG, DWNSWNG, FTHRU) con sus desviaciones; también por posición. Elección por sexo y palo (driver y maderas → «Drv»;
  híbridos, hierros, wedges → «Midir»); reserva: hierro medio masculino. Béisbol: por edad.
- **z** = (x − μ)/σ recortado a ±4 (`TrackerRangeScoreService.rangeScore:134-161`); **color** verde |z| ≤ 0,85, naranja hasta 1,29,
  rojo después; **puntos** 100 si cumple el límite (in range, sobre o bajo según el flag), si no 100 − 40|z| hasta 1σ y 60 − 20(|z| − 1)
  después (0 a 4σ); límites por flag (IN_RANGE, UPPER/LOWER_BOUND, POS/NEG, NO_COMPARE), objetivo de watchlist = μ ± σ·k (`RANGE_ADJUSTOR`).
- **Cuándo se muestra**: siempre que el análisis supere el umbral global (**confianza media 2D ≥ 0,89 y fps ≥ 60**, `Configs:58,60`)
  y el tracker esté habilitado para la vista y el plan. **No hay confianza por métrica ni abstención parcial**: superado el
  umbral, todos los números se muestran sin cualificar.
- **Swing Score** = 0,3·velocidad + 0,4·consistencia + 0,3·eficiencia (`SwingScoreReportFactory:120`); velocidad = percentil de
  `ClbHdSpdMax` sobre 250 valores de pros (`speedRanges`); eficiencia = 0,30·contribuciones + 0,35·ganancias + 0,35·secuencia (z → 100 − 10z
  por encima, 100 + 25z por debajo; órdenes correctos 0/33/67/100); consistencia = media de clamp((1 − σ_sesión/factor)·100) sobre 33
  indicadores en ADR, TOP, IMP y transición; rangos «geográficos» por hándicap y edad con un pseudo-percentil 100 − 25·|z| (no es un
  percentil real).
- **Diagnóstico**: fallos = watchlists cuyos indicadores salen de rango (severidad Σ|z|, prioridad por fase más temprana, 5
  recomendaciones); «velocidad potencial» por un modelo lineal de 15 indicadores con coeficientes por sexo y carry = 3,18·v − 90,3
  yardas; «least consistency» = indicadores más alejados de los pros en un swing (nombre engañoso).

### 3.5 Lo que NO calcula
Ninguna métrica de barra, disco o VBT; ninguna repetición o serie; ningún error por métrica; ninguna métrica de bola en producción
(la de desarrollo: velocidad, ángulo y dirección de salida por visión, §4.11). Tampoco fuerzas ni potencias.

---

## 4. Algoritmos

### 4.1 Orquestación (`AnalyzerRepository.runAnalysisFlow:4705`, `generate2DPose:4823`, `generate3DPose:4986`, `generateSwingTrackers:5454`)
1. Vídeo local (o descarga de Firebase Storage). Caché: si existen `*_2d.json`, `*_3d.json` y `*_ai.json` válidos se salta todo.
2. **2D** por fotograma con OpenCV (`OpenCVHelper`, resolución de proceso sin confirmar): caja de la persona en el **fotograma 0**
   (`generate2DPose$2:543`) y en los siguientes **a partir de la pose anterior** (caja de los 38 puntos ampliada un 15 % y
   conservada si cambia < 10 %, `BoundingBoxHelperKt.adjustHumanBoundingBox:98-163`; rama por fotograma sin confirmar), pose 2D
   (§4.3), abandono si un fotograma falla o si los **primeros ≥ 15** fotogramas tienen baja confianza; los primeros fotogramas
   de baja confianza se «recuperan» (`recoverInvalidPose2dFrames`); fps = máx(metadatos, medido) y, si no es DTL y < 55, un
   **estimador por el ciclo de las muñecas** (`VideoUtil.detectFps:23-102`: duración mediana de la fase «manos altas» → 240/120/60/30);
   corrección de los puntos del palo (§4.4); confianza media; `VideoAnalysisResponse` a JSON.
3. **3D** (§4.5): ventana temporal, escala y traslación; en DTL, giro de −90°.
4. **Trackers e indicadores** (§4.8-4.9) por `GolfSportsService.processSwing` → `SwingTrackerService.processSwingData:81-160`:
   posiciones (§4.6) + posiciones anotadas a mano; longitud fija del palo; suavizado por articulación y eje; trackers; permisos;
   indicadores; posiciones compuestas; puntuaciones.
5. Umbral de calidad y «Swing and a miss».

### 4.2 Detección de la persona (y de la bola)
- **Producción**: ML Kit `ObjectDetection` en modo imagen única con clasificación activada; se toma **el primer objeto detectado**
  (sin comprobar que sea una persona) y se amplía la caja 15 % en ancho y 10 % en alto; si no hay objeto, **caja de reserva central**
  (40 %×80 % en horizontal, 60 %×70 % en vertical) (`MlkitBoundingBox:79-85,129-133,196-255`, `BoundingBoxHelperKt:75-96,165-173`).
- **Desarrollo**: NanoDet-Plus (umbral 0,4, NMS 0,4, Vulkan): persona con mayor puntuación y bola con mayor puntuación
  (`NanoDetBoundingBox:36-37,155-236`). Es la única fuente de la caja de la bola.
- **Seguimiento**: no hay tracker de objetos; la caja se deriva de la pose en los fotogramas siguientes.

### 4.3 Pose 2D (`Pose2DProcessor.predict:60-98`, `parseTensorOutput:120-166`, `ImageUtils.box2cs:140-159`)
1. Si hay intrínsecos y distorsión: `undistort` de OpenCV con K y (k1, k2, 0, 0) (`Utils.correctDistortion:255-273`); en la práctica
   la distorsión **siempre es nula** en las llamadas vistas (sin confirmar otras).
2. **Zurdos**: la imagen se **voltea horizontalmente** antes de inferir (`LeftySupport.flipLeftySwingFrame:17-22`); los modelos
   solo conocen diestros.
3. Recorte: centro de la caja; escala = (ancho, alto)/200 ajustada a aspecto 0,75 y ×1,25 de margen; transformación afín a 192×256;
   normalización ImageNet.
4. 38 mapas de calor 64×48: argmax y refino de un cuarto de píxel hacia el vecino mayor; afín inversa a píxeles; confianza =
   valor del pico; **puntuación del fotograma** = media de los 38 picos.
5. Refino del **hosel** por segmentos de línea (§4.4); hosel con confianza < 0,8 → 0; cabeza del palo con confianza < 0,8 → se
   coloca en el hosel.
6. GPU delegate si el dispositivo lo admite; si no, 4 hilos.

### 4.4 El palo: segmentos de línea, interpolación y longitud fija
- **Segmentos de línea** (`LineSegmentHelper.lineSegmentDetectionClub:36-497`): región de interés a lo largo de la dirección mango→manos
  (de 50 px antes del mango a 250 px después, escalada por min(alto, ancho)/720, margen 50); `FastLineDetector` de OpenCV sobre gris;
  se conservan los segmentos casi paralelos a la dirección de referencia (|cos| > 0,95) y alineados con el mango (cos > 0,93); la
  referencia es la dirección de la varilla validada en los 3 fotogramas anteriores si coincide (cos > 0,935), si no la del modelo;
  se ordenan por distancia al punto medio de las manos (≤ 100 px, los 20 más cercanos) y se **encadenan** los colineales (hueco
  < 50 px, o < 100 px si el hueco es menor que el segmento); gana la cadena más larga; se rechaza si mide < 60 % de la longitud del
  palo del primer fotograma; el hosel pasa al extremo de la cadena (si la cadena y el hosel del modelo divergen, cos < 0,94) o al
  pie de la perpendicular del hosel sobre la línea; `checkHosel` exige que mango→manos y mango→hosel coincidan (cos ≥ 0,94) o pone
  la confianza a 0.
- **Interpolación 2D** del hosel y de la cabeza (`GolfSportsService.correctGolfClubKeypoint:286-293`, `InterpolatePose2DKt:231-437`):
  mediana de 5 sobre tramos confiables (> 0,8), se anulan los puntos de borde poco seguros (< 0,86) y las rachas < 3; entre tramos
  confiables de ≥ 10 fotogramas se ajusta un **polinomio cúbico** por mínimos cuadrados (hasta 16 puntos a cada lado) y, si el error
  normalizado por el rango vertical es < 3 %, se rellena con él (eliminando atípicos > 2,5σ); si no, con un **spline**. Solo se
  sustituyen los puntos cuya distancia 2D al punto de referencia (manos para el hosel; hosel para la cabeza) no supera 1,4× o 0,3×
  el percentil 80 de esa distancia. `removeInconsistentAngles:183-229` anula ángulos mango→hosel que se apartan > 3σ de un polinomio
  por tramos.
- **Longitud fija** (`ClubLengthHelperKt.forceFixedClubLength:127-147`): antes y después del suavizado 3D, el hosel se recoloca a la
  longitud nominal del palo por sexo (`sportsbox_shaft_length.csv`: driver 1098,6/1073,2 mm… wedge L 825,5/812,8; 1155,7 mm por
  defecto) a lo largo de la dirección mango→hosel, y la cabeza conserva su desplazamiento respecto al hosel.

### 4.5 Reconstrucción 3D desde una sola cámara (`Pose3DProcessor` en modo «simple» :270-703, `Utils.poseFrameTranslationPNP:95-242`)
1. **Entrada**: para cada fotograma, 38 puntos (x, y en píxeles, confianza) → **normalización con la caja del PRIMER fotograma**
   (caja de los puntos + 15 %; la misma para toda la secuencia): x' = 2·(x − x0)/w − 1, y' = 2·(y − y0)/w − h/w; hosel con confianza
   < 0,8 → 0 (`Utils.normalizeCoordinatesForPose3D:67-93`).
2. **Ventana temporal** de 27 fotogramas centrada (±13) con **replicación de bordes** (el primero y el último se repiten); se quitan
   NOSE y ambas orejas (38 → 35); tensor [1,27,35,3].
3. **Modelo** Face-On o DTL (según el ángulo de la sesión, `ModelConfigsKt.getPose3DModel:64-74`): salida 35 puntos 3D del fotograma
   central, en un marco relativo del modelo (ejes de cámara: x derecha, y abajo, z profundidad [D]); se reasignan a los 38 índices.
4. **Escala métrica**: en el fotograma 0, factor = **ancho de cadera del perfil (mm)** / distancia 3D entre caderas; en los demás,
   factor = longitud total de muslos y piernas del fotograma 0 / la actual (**longitudes óseas fijadas** al primer fotograma).
5. **Traslación absoluta** (PnP de solo traslación): con K (intrínsecos) se calculan los rayos normalizados K⁻¹·[u, v, 1] de los puntos
   2D (sin cabeza ni palo: índices 1-4 y 34-37 excluidos) y se resuelve por **mínimos cuadrados ponderados por la confianza 2D** la
   traslación T que mejor alinea el esqueleto escalado y centrado en la cadera con esos rayos (dos ecuaciones por punto; normales de
   3×3 e inversa). x e y absolutos = (X − centro de caderas)·escala + T.
6. **Profundidad anclada**: la z (profundidad) de cada punto se referencia al **borde exterior del pie izquierdo** y se fija a la
   profundidad que ese pie tenía en el fotograma 0 (= z del pie·escala + T_z del fotograma 0): el pie adelantado no se mueve en
   profundidad, lo que estabiliza la profundidad de todo el cuerpo [C].
7. Cabeza: frente, nariz y orejas se igualan al punto superior de la cabeza.
8. **Intrínsecos**: `CameraIntrinsicHelper.getCameraIntrinsic:26-74` acepta 5 valores (fx, fy, cx, cy, …) del metadato `intrinsic`,
   los reescala al tamaño de análisis y, si faltan, usa **f = 1300 px a 1080p** (≈ 73° de campo horizontal) y el centro de la imagen.
   La app Android **nunca escribe** ese metadato (ninguna lectura de `LENS_INTRINSIC_CALIBRATION` ni `LENS_DISTORTION` en el código
   propio) [C]: en Android todo se calcula con la focal por defecto. Tamaño por defecto 1280×720 si se desconoce.
9. **DTL**: tras el modelo DTL, `convertDownTheLineToFaceOn` **gira −90° alrededor del eje vertical** todos los puntos (salvo nariz y
   orejas) (`CommonServiceImpl:181-184`, `UtilsKt.rotateXZ:133-155`), para que los mismos trackers valgan.
10. Alternativa existente pero no usada en el flujo principal: `scaleSkeleton3D` por **altura mediana** (regresión: altura − 77,5 (+0,118
    si mujer) dividida por una suma ponderada de 12 segmentos) o por **cadera y piernas** (`ScaleSkeletonKt:19-115`).

### 4.6 Segmentación en posiciones (vídeo) (`GolfPositionDetector.detectPosition:90-421`)
1. Coordenadas 3D con y invertida (hacia arriba); hosel suavizado a 8 Hz; **vector del palo** = hosel − punto medio de las manos.
2. **Velocidad angular del palo** = ángulo entre vectores consecutivos × fps, con signo según el producto vectorial (z).
3. **IMP** = fotograma de **máxima velocidad angular** en sentido de downswing con las manos por debajo de 0,35·hombros + 0,65·caderas.
4. Se gira la pose −ángulo de la línea de tobillos en IMP (la línea de los pies pasa a ser el eje X); **ángulo frontal** del palo =
   atan2(x, −y) del vector por fotograma; se **desenvuelve** con referencia en IMP; se suaviza a 8 Hz (y una copia a 5 Hz).
5. Hacia atrás desde IMP: **D45** (−45°), **DCH** (−90°), **DCV** (−180°) = fotograma más cercano a cada ángulo, hasta que las manos
   suben por encima de 0,4·cadera + 0,6·hombro; sigue mientras el ángulo a 5 Hz crezca hacia atrás: **TOP** = fin de esa monotonía
   (reversión del palo); más atrás **BCH** (−90°), **BCV** (−180°), **B45** (−45°) hasta que el ángulo supere −45°, y después mientras
   la x del hosel retroceda: ahí está **ADR** (ajuste: avanza mientras la x no se aleje > 6 mm).
6. Hacia delante desde IMP: **F45** (45°) y **FCH** (90°) hasta superar 90°; **FIN** = mínimo del desplazamiento del hosel entre
   fotogramas dentro de 1,5 s tras FCH (el palo se detiene); **FCV** (180°) entre FCH y FIN.
7. **Impacto refinado**: entre DCH y FCH, el fotograma en que la x del hosel más se acerca a (x en ADR − 10 mm) (la cabeza vuelve a su
   sitio). Exige las 9 posiciones; el swing va de ADR − 0,3 s a FIN + 0,3 s. BAH, DAH y DA30 salen de indicadores (§3.3).

### 4.7 Detección incremental en vivo (`GolfSwingDetector.processFrame:393-566`, `processFrameDtl:584-695`)
- Pose 2D escalada para que cabeza→tobillos mida 1650 unidades (`scalePose:164-175`); alturas de suelo, cadera y cabeza suavizadas
  exponencialmente con pesos adaptados a los fps (`trackFps:142-162`).
- **Confianza**: muñecas, cabeza y tobillos ≥ 0,5 (o menos de la mitad de puntos por debajo); más de 5 fotogramas malos seguidos →
  «tracking perdido» y reinicio.
- **Postura inicial** (`isStartPose:232-320`): tobillos cerca del suelo, muñecas entre cadera y cabeza, distancia entre muñecas
  ≤ 400, pies más anchos que las caderas, hosel por debajo de las rodillas y entre los tobillos (address), ejes tobillos/caderas/hombros
  coherentes (< 30°/40°) si hay 3D; dirección de referencia por la línea de tobillos.
- **Máquina de estados** START → BACK_SWING (las muñecas retroceden a > 1700 unidades/s respecto a la mediana de 10 muestras o
  cruzan la cadera trasera; ADR = 0,3 s antes) → SWING (TOP = muñeca más alta por encima de 0,7·cadera + 0,3·cabeza, al bajar 20) →
  IMP (muñecas de vuelta cerca de la x inicial y por debajo de 0,8·cadera + 0,2·cabeza) → FIN (muñecas altas y descenso de 20; fin
  + 1 s). Posiciones B45/BCH/BCV, D45/DCH/DCV, F45/FCH/FCV por el ángulo de elevación de la varilla (−45°, 0°, 90°). Variante DTL con
  estados de altura de muñeca (abajo/medio/arriba) ponderados por confianza y ventanas de 0,3-1,7 s desde TOP.
- Eventos al grabador (detectado, fin, perdido); cada swing se recorta con ±0,2 s de margen.

### 4.8 Marcos de referencia y trackers (`GolfTrackers.createGolfTrackers:91-177`, `VFrameKt.vFrameFromPose:12-67`)
- Se **aumenta** el esqueleto con puntos medios (caderas, hombros, muñecas, AC, rodillas, orejas, nudillos), el origen global y el
  **centro de masa** (pesos por sexo; `Algorithms.augmentSkeletonPoints:273-300`).
- **GLOBAL**: X = dirección entre tobillos **0,2 s antes de ADR**, Z = vertical (0,1,0 tras invertir y), Y = X × Z.
- **V-frames** por segmento (`vframe_def.csv`): primer eje = p2 − p1; tercer eje = ese × (segundo par); segundo = tercero × primero;
  asignación a X/Y/Z por letras (Chest: X entre AC, Z por el tórax; Pelvis: X entre caderas, Z trocánter→cresta; brazos y piernas: Z
  distal→proximal; manos: nudillos; pies: talón→punta; varilla y palo: hosel→mango y hosel→cabeza; versiones «SP» del antebrazo
  alineadas con la varilla).
- Algoritmos por tracker (§3.2) y **postproceso** (`PostProcess.postProcessCommon:28-47`): U (desenvolver desde ADR), NEG, 90 (plegado
  a ±90°), SMOOTHn, IMPn (suavizado **partido en el impacto**), ADDn, N (signo del backswing).

### 4.9 Suavizado (`Algorithms.smoothSwingFrameCoordinates:107-234`, `LowPassFilter:14-44`, `LinearAlgebraKt.quadraticExtrapolate:230-257`)
- Paso bajo **Butterworth de 2.º orden (biquad) en ida y vuelta** (fase cero) con la frecuencia de corte **corregida por 0,80224**
  (la misma corrección de doble pasada que usa KPKN); relleno de ambos extremos con **extrapolación cuadrática** de 16 muestras
  (máx(0,15·fps, 6) muestras); solo si fc < 0,36·fps.
- Cortes por articulación y eje (`sportsbox_filter_config.csv`): **hosel y cabeza del palo 12 Hz**, manos 5 Hz, tronco y pelvis
  4 Hz, resto 6 Hz. Opción de **partir en el impacto** (ambos tramos se suavizan por separado y se extrapolan) para no aplastar la
  discontinuidad; se usa en los trackers del palo (IMP6).
- Las velocidades se derivan por diferencias finitas tras el suavizado de posiciones y se vuelven a suavizar (SMOOTH6/8).

### 4.10 Clasificador de vista y dirección (`GolfSwingAngleDetector.predictSwingAngle:28-96`, `GolfCameraDirection:29-80`)
- **Vista**: modelo **lineal** sobre los puntos 2D de todos los fotogramas: para cada punto, x e y estandarizados con la media y
  desviación de todos los puntos del clip, más la confianza, multiplicados por coeficientes por punto (`sportsbox_swing_angle_coefficients.csv`,
  intercepto 11,96); la suma sobre los fotogramas ≥ 0 → DTL, si no Face-On. No corrige ángulos intermedios.
- **Dirección** (clase alternativa, uso sin confirmar): puntuación con pesos fijos sobre las x de pelvis y pies y la confianza del pie
  trasero, por voto mayoritario.

### 4.11 Indicadores: lenguaje de fórmulas (`SwingIndicatorService:90-123,375-432`, operadores en `indicator/formula/`)
- Tokens: `TRACKER@POS` (valor en una posición), `TRACKER[P1:P2]` (serie entre posiciones), constantes, aritmética, referencias a otros
  indicadores (orden por **grafo de dependencias**, Kahn), `HEIGHT` del perfil.
- Operadores: Max/Min, MaxTime/MinTime (instante del extremo), **NearestTime(valor, serie)** (instante más cercano a un valor),
  **ReleaseTime(serie, umbral)** (busca hacia atrás el último tramo con velocidad ≥ 0 y, si llega al inicio, hacia delante el primer
  valor ≥ umbral), **PathLen** (suma de pasos 3D), **PLANE_DIR / PLANE_SLOPE** (normal del plano de tres puntos: dirección
  atan2(−nx, nz) o pendiente asin(nz)), **Order(a, b, c, d)** (puesto del primero), **Scan LOCMAX/LOCMIN/ZERO FWD/BWD**.
- Los indicadores con «Position Definition» crean posiciones nuevas (BAH, DAH, DA30 = tiempo × fps).
- Las watchlists pueden definir indicadores a medida en tiempo de ejecución (`Tracker@POS` o fase con MIN/MAX).

### 4.12 Métricas de bola (solo desarrollo) (`BallMetricsProcessor` simple :107-307, `BallCircleDetectorKt:16-36`, `BallProjectionIn3DSpaceKt:15-67`, `BallMetricsCalculatorKt:17-64`)
Del impacto a +10 fotogramas: caja de la bola (NanoDet), **Hough de círculos** (gris, desenfoque 3×3, radio 2-15 px, acumulador 15),
radio fijado en la primera detección; **posición 3D** por los dos rayos K⁻¹ de los extremos horizontales del círculo escalados al
diámetro real (42,67 mm); **salida** = primer fotograma con desplazamiento horizontal > 200 px (120 fps) o 100 px (240 fps);
velocidad = desplazamiento 3D hasta el último fotograma / tiempo; ángulo de lanzamiento = asin(−dy/|d|); dirección = asin(dz/|d_xz|).

### 4.13 fps y cámara lenta
fps de los metadatos del vídeo o detectado por `MediaExtractor` y normalizado a 30/60/120/240 al importar; en el análisis, máximo entre
metadatos y medición y, si queda < 55 en Face-On, el estimador por el ciclo de las muñecas (§4.1). Todo el análisis exige ≥ 60 fps
efectivos; **no hay detección de VFR** ni uso de PTS: los índices de fotograma se convierten a tiempo con un fps constante.

### 4.14 Controles de calidad y avisos
- Antes: guía de instalación, silueta, voz, exposición manual, exigencia de 60 fps.
- Durante el análisis: abandono si falla un fotograma 2D o si ≥ 15 iniciales son malos; «swing not detected» si faltan posiciones;
  «Swing and a miss» (confianza < 0,89 o fps < 60) con consejos (cámara lenta completa, golfista no lejano, fondo limpio) y
  **subida automática del vídeo y los JSON** para «entrenar los modelos» (`UploadWorkerService:1378,1790-1860`).
- Después: ajuste manual de posiciones con recálculo; aviso «DTL Beta: algunos trackers no disponibles»; nivel de plan.
- No hay confianza por tracker, ni abstención por métrica, ni corrección de perspectiva, ni comprobación de intrínsecos.

### 4.15 Comparación de dos swings
- **Tiempo**: en el visor, por **índice de fotograma** de cada swing con deslizadores independientes y «Sync» (misma posición de
  reproducción); las posiciones del swing de cada uno sirven de chips de salto; no hay alineación DTW ni remuestreo [C]. Los indicadores
  comparan **valores en la misma posición** (ADR, TOP, IMP…) o fase, no series.
- **Espacio**: cada swing en su propio marco global (línea de pies en ADR, origen en ADR para desplazamientos), escalado por su cadera;
  los avatares se dibujan en Unity (uno de ellos como referencia, zurdos con bandera aparte); colocación relativa y «ghost» sin confirmar.
- **Estadística**: z-scores contra la base de pros por sexo y palo; promedios por colección y ranking de «outliers» por |z| (`SwingLibraryService:132-158,269-299`).

---

## 5. Exportación y compartir
- **Vídeo con telemetría quemada: no existe** [C]. `Transformer` de Media3 solo recorta; no hay `OverlayEffect`, ni dibujo de trazos
  sobre fotogramas, ni marca de agua en Java (Unity tiene objetos `Watermark`, uso sin confirmar).
- **Grabación de pantalla con voz** (`ScreenRecordService:343,724,832`): `MediaProjection` + micrófono (H.264 30 fps, AAC 128 kb/s),
  ventana flotante para parar, copia a la galería, revisión con recorte, **compartir como post o comentario** en el feed (requiere
  red), guardar. Pensado para que el entrenador narre la corrección sobre el visor 3D.
- **Guardar en el teléfono**: descarga del vídeo original de la sesión (worker).
- **CSV / informes**: no hay exportación de datos al usuario; los JSON por swing (`*_2d.json`, `*_3d.json`, `*_ai.json`) viven en la
  carpeta privada de la app y **se suben a Firebase Storage** tras cada análisis.
- **Nube**: resultados y vídeos (sesiones, incidencias, swings de referencia), feed, chat, AI Highlights y SAMI.

---

## 6. Datos e integraciones
| Tipo | Detalle |
|---|---|
| Room v137 | `poseEstimate` (rutas a los JSON 2D/3D/AI, fps, confianza, swing score), `videos` (metadatos: fps, resolución, ángulo, intrínsecos, datos del golfista: cadera, altura, sexo, palo), `sessions` (clima, cámara, alumno), `referenceModel`, `ballmetrics` (sin uso), `userTrackers`, `aiHighlights`, `downloads`, chat y feed |
| Archivos | `app_FrameData/<nombre>_<id>_{2d,3d,ai}.json` en texto (Gson); vídeos en `app_Swing/` y `filesDir` |
| Preferencias | `cellular_upload`, unidades, `autoSwingDetection`, banderas de desarrollador, tutoriales |
| Nube | Firebase Auth (email, Google), Firestore (chat), Storage (vídeos `/video_upload/android/…`, `/swing_data/{videoId}/`, incidencias), Functions `us-central1-sportsbox-3dgolf.cloudfunctions.net/api/` con token (sesiones, vídeos, alumnos, watchlists, evaluaciones, suscripciones, modelos de referencia, `v1/llm/*` para AI Highlights y SAMI, `utility/decrypt-deeplink`), Remote Config (`SAMIChatAvailability`), App Check, Crashlytics, FCM, Mixpanel (`analysis_complete` con fotogramas, latencia, confianza, dispositivo, ángulo y origen) |
| Subidas | Tras **cada** análisis: `POST analysis` + los tres JSON; si falla: también el vídeo a `report_issue`; sesiones y vídeos al terminar de grabar o al recuperar red; el worker de análisis **ignora** el ajuste de datos móviles |
| Sensores externos, BLE, wearables, Health Connect | No hay. El addon «Foresight» (launch monitor) es solo un nombre |
| Integraciones | Golf Genius Coach360 (deep link de inicio y vuelta), OpenWeatherMap + ubicación (clima de la sesión), Places |

---

## 7. Las tres preguntas del usuario

**1. Perspectiva: ¿exige grabar a 90° o corrige ángulos oblicuos?**
- Exige **dos encuadres canónicos**: Face-On (frente al golfista) o Down-the-Line (detrás, mirando al objetivo), y guía: 1,8-3 m,
  trípode, cuerpo entero. El ángulo se **clasifica** automáticamente (modelo lineal, §4.10) y elige el modelo 3D de esa vista.
- **No corrige** la perspectiva: ni homografía, ni elipse, ni IMU (no hay sensores), ni intrínsecos reales (focal por defecto 1300 px
  a 1080p). Una cámara oblicua cae en uno de los dos modelos y produce un 3D sesgado sin aviso, salvo el umbral global de confianza
  2D (0,89). El DTL se expresa en Face-On girando −90° el 3D.
- **Lo que sí hace** es dar coordenadas métricas absolutas con una sola cámara: ancho de cadera del perfil como escala, longitudes
  óseas fijadas, traslación por rayos de cámara y profundidad anclada al pie (§4.5). Esa cadena es directamente aplicable a los puntos
  «world» de MediaPipe en KPKN (D42).

**2. Tracking híbrido: ¿qué hace sin disco olímpico?**
- No hay seguimiento de objetos: **el palo forma parte de la pose** (4 puntos del palo en el modelo 2D propio) y se refina con un detector
  de segmentos de línea anclado en las manos, interpolación polinómica/spline en los huecos y longitud nominal fija por palo. La bola
  solo existe en desarrollo (NanoDet). No hay relevo entre señales ni soporte de otros implementos (el SDK de béisbol usa los mismos
  índices para el bate).
- Para KPKN: la lección transferible es el **refino de un implemento rectilíneo** (barra, bate, raqueta, jabalina) con segmentos de
  línea a partir de las muñecas, y la **longitud conocida** del implemento como restricción.

**3. ¿Exporta vídeo con telemetría para redes?**
- **No.** Solo grabación de pantalla con voz del visor 3D y compartir en su feed. **D44 sigue siendo diferenciador** de KPKN.

---

## 8. Brechas frente a KPKN

Se leyeron `VIDEO_TECHNIQUE_V1.md`, `DIFERENCIADORES.md`, `CANDIDATAS_Y_METRICAS.md` y `MERCADO_FIABILIDAD.md`. Esfuerzo: S ≤ 2 días,
M ≤ 1 semana, L > 1 semana.

### 8.1 Tabla
| Función o métrica de Sportsbox | ¿La tiene KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| **Coordenadas métricas absolutas con una cámara** (escala por cadera/altura, huesos fijos, traslación por rayos K⁻¹, profundidad anclada al pie) | Parcial: «world» de MediaPipe centrado en cadera, sin traslación ni escala verificada | Sobre los 12 puntos «world» de `PoseTrackV2`: escala = altura o ancho de cadera del perfil / medida del esqueleto; mínimos cuadrados ponderados por visibilidad contra los rayos con `focalLengthPx` de `capture.json` (o focal por defecto con aviso «≈»); anclar la profundidad al pie de apoyo; longitudes óseas fijadas a la mediana de la serie. Da trayectorias de cadera/manos en metros sin disco (máquinas, mancuernas, saltos) | Propia (álgebra 3×3); OpenCV opcional (Apache-2.0) | **Alta** (D42/D43) | M |
| **Ángulos 3D por marcos de segmento (JCS)**: giro, flexión e inclinación de pelvis, tronco y hombros; X-Factor; rotación interna/externa | Parcial: ángulos 2D con aspecto corregido y respaldo 3D | Definir marcos por segmento (pelvis: caderas y vertical; tronco: hombros y cadera-hombro) y ángulos tipo Grood-Suntay (eje flotante) sobre «world»; tabla de definiciones por segmento como la de Sportsbox pero propia; validar con el banco oblicuo (D42.1) | Propia | **Alta** | M |
| **Segmentador de eventos balísticos** (pico de velocidad angular del implemento = impacto/suelte; reversión monótona = top; umbrales de ángulo = posiciones; parada = fin) | No (segmentador cíclico de reps) | `EventSegmenter` en `domain/biomechanics/reps`: señal = velocidad angular del segmento distal (muñeca-mano, pie en patadas, mano en lanzamientos); eventos por pico, reversión y umbral; fases ADR/TOP/IMP/FIN por deporte en perfiles de datos. Base para saltos (despegue/aterrizaje), golpes y lanzamientos (D48/D50) | Propia | **Alta** | M |
| **Secuencia cinemática** (picos de velocidad angular pelvis→tronco→brazo→implemento, orden de transición y de picos, ganancias, contribuciones, separaciones en ms) | No | Velocidades angulares de giro (JCS) suavizadas; `Order` de transiciones (cruce por cero antes del evento) y de picos; ratios; mostrar como «secuencia» con semáforo. Vale para golf, béisbol, tenis, lanzamientos, boxeo | Propia | Media-alta | S-M |
| **Motor de trackers e indicadores dirigido por tablas** (fórmulas `TRACKER@POS`, `[P1:P2]`, Max/MinTime, NearestTime, PathLen, Order) | No (métricas codificadas por perfil) | Un evaluador de expresiones pequeño sobre `SetTechniqueReport`/series; permitiría perfiles por deporte en datos y métricas a medida del usuario | Propia | Media | M |
| **Filtro partido en el evento** (suavizar antes y después del impacto por separado, extrapolación cuadrática de bordes) | Parcial: Butterworth de fase cero con reflexión | Añadir `splitAt` a `ZeroPhaseLowPass` y extrapolación polinómica para eventos de alta aceleración (impacto, aterrizaje) | Propia | Media | S |
| **Refino del implemento por segmentos de línea** (varilla, bate, raqueta, barra) | No (disco por Hough/NCC) | ROI a lo largo de la dirección muñeca→implemento, `FastLineDetector` o LSD, encadenado de colineales, longitud conocida como restricción; respaldo cuando no hay disco (D43.2) | OpenCV ximgproc (Apache-2.0) | Media | M |
| **Interpolación de huecos** con cúbica por tramos y spline, validada por error relativo y atípicos | Parcial (`GapFiller` lineal ≤ 0,6 s) | Ajuste cúbico entre tramos confiables con rechazo por error normalizado; spline si falla; límite por distancia a un punto de referencia | Propia | Media-baja | S |
| **Clasificador de vista por modelo lineal** | Sí (geométrico: anchura de hombros y «world») | No necesario; opcionalmente combinar | — | Baja | — |
| **Rangos de referencia (μ, σ), semáforo 0,85σ/1,29σ, puntos y objetivos ± σ·k** | No (confianza y «≈») | Primero contra el **propio historial** del usuario (mediana y MAD por ejercicio) y, cuando haya banco, poblacional; nunca mostrar como «percentil» una distancia | Propia | Media | S-M |
| **Ajuste manual de eventos** con recálculo automático | Parcial («Usar/Corregir N reps») | Arrastrar el instante de TOP/IMP/FIN de una rep en el reproductor y recalcular métricas | Propia | Media-baja | S |
| **Avatar 3D con punto de vista libre** (ver el 3D desde el objetivo, arriba, atrás) | No | Sin Unity: esqueleto 3D con Canvas/OpenGL desde «world» + traslación (fila 1), rotación por gestos; «fantasma» del mejor intento. Diferenciador visual de D42 | Propia / Filament (Apache-2.0) | Media | L |
| **Guía de voz al encuadrar** (acércate, aléjate, paso a la izquierda) | Chips visuales | TTS del sistema sobre `FramingEvaluator` | Android TTS | Media-baja | S |
| **Detección automática y recorte por intento** en vivo | No (serie completa) | `AutoSwingSession` equivalente con el segmentador de eventos a ≤ 15 fps; recorte por intento con `MediaMuxer` | Media3/MediaMuxer | Media | M |
| **Consistencia entre intentos de la sesión** (σ/factor por indicador) | Parcial (CV del ROM por rep) | Por evento y métrica; umbral «factor» = MAD histórico | Propia | Baja | S |
| **Grabación de pantalla con voz** para entrenadores | No | `MediaProjection`; cubre parte de D44 mientras no esté la exportación | Propia (MIT) | Baja | M |
| **Tempo backswing:downswing** y tiempos por fase | Sí (tempo de reps) | Reutilizar para eventos balísticos (preparación/ejecución) | — | — | — |
| **Centro de masa por pesos segmentarios** | No (pelvis como proxy) | Pesos de Dempster/de Leva sobre «world»; etiquetar «estimado» | Propia | Baja | S |

### 8.2 Lo que KPKN hace mejor
- **Escala por un objeto conocido** (disco calibrado, con elipse, paralaje y escalas por eje) frente a un **prior antropométrico**
  (ancho de cadera por tabla) y una **focal por defecto** sin comprobación.
- **Honestidad**: niveles ALTA/MEDIA/BAJA, banderas, «≈», abstención por métrica y «sin dato no se pinta». Sportsbox tiene un único
  umbral global (0,89 y 60 fps) y, superado, muestra 107 trackers y 300 indicadores sin cualificar.
- **Tiempo**: PTS reales, detección de VFR y de cámara lenta por metadatos; Sportsbox usa índices de fotograma con fps constante y,
  si duda, **adivina el fps por la duración del swing**.
- **Privacidad**: todo en el teléfono; Sportsbox sube JSON y, en los fallos, también el vídeo, sin consultar el ajuste de datos
  móviles.
- **Robustez de la pose**: Hampel, cruces izquierda/derecha, huecos con marca; Sportsbox descarta el swing si un fotograma falla y
  recupera solo los primeros.
- **Barra y VBT**: todo el bloque de disco, MCV, pérdida de velocidad y perfil carga-velocidad no existe en Sportsbox.
- **Vistas**: KPKN clasifica SIDE/OBLIQUE/FRONT/BACK/LYING y degrada con aviso; Sportsbox solo distingue dos y no avisa en oblicuo.

### 8.3 Errores de Sportsbox que KPKN no debe copiar
- Focal por defecto sin aviso y metadato de intrínsecos nunca rellenado.
- «Percentil» calculado como 100 − 25·|z| (castiga estar por encima de la media) y «consistencia» que en realidad es distancia a los
  pros.
- Tomar «el primer objeto» de ML Kit como la persona, sin comprobar la etiqueta.
- fps adivinado por la duración de una fase del movimiento.
- Subida de vídeos de fallos para «entrenar modelos» con un consentimiento único en el registro, que además impide crear la cuenta
  si se rechaza.
- Base de reserva con etiqueta cruzada (`SPORTSBOX_SWING_SCOREDATABASE_MALE_PRO_MIDIRON` → archivo femenino de driver) y unidad
  errónea en la velocidad potencial.
- Delegado Flex de 75 MB sin uso.

---

## 9. Índice de evidencias

**Java/Kotlin.** Rutas bajo `C:\kw\re\sportsbox\jadx\sources\com\sportsbox\` (árbol borrado al terminar; se regenera con
`C:\kw\re\_scripts\sportsbox\run_jadx_full.ps1`; las clases con cuerpos omitidos, con `run_jadx_fallback.ps1`, modo SIMPLE).

| Tema | Evidencia |
|---|---|
| Modelos y versiones | `analysis/model/ModelConfigsKt.java:12-21,49-74` |
| Pose 2D | `analysis/model/Pose2DProcessor.java` predict:60-98, parseTensorOutput:120-166, preProcessImage:195-203, create:211-226; `analysis/utils/ImageUtils.java` box2cs:140-159, getAffineTransform:27-102; `analysis/utils/LeftySupport.java:17-22`; `analysis/utils/Utils.java` correctDistortion:255-273 |
| Palo en 2D | `analysis/utils/LineSegmentHelper.java` lineSegmentDetectionClub:36-497, lineAssociation:499-520, checkHosel:537-554; `sdk/golf/GolfSportsService.java` correctGolfClubKeypoint:286-293; `sdk/helper/InterpolatePose2DKt.java` interpolatePose2D:231-437, removeInconsistentAngles:183-229, findHighConfidenceRanges:104-133; `sdk/helper/ClubLengthHelperKt.java` getClubLength:59-93, forceFixedClubLength:127-147 |
| Caja | `analysis/model/MlkitBoundingBox.java:79-85,129-133,196-255`; `analysis/model/NanoDetBoundingBox.java:36-37,93-103,155-236`; `analysis/utils/BoundingBoxHelperKt.java` increaseBoundingBox:75-96, adjustHumanBoundingBox:98-163, fallbackBoundingBox:165-173; `di/BoundingBoxModule.java`; `common/Configs.java:77-83` |
| 3D | `analysis/model/Pose3DProcessor.java` (modo simple) invokeSuspend:270-703 (caja del primer fotograma :660-682, ventana :395-447, PnP :496, cabeza :501-518), createInputBuffer:163-186, computePose3D:213-250; `analysis/utils/Utils.java` normalizeCoordinatesForPose3D:67-93, poseFrameTranslationPNP:95-242, currentBoneLength:366-388; `analysis/utils/CameraIntrinsicHelper.java:18-20,26-74`; `sdk/common/CommonServiceImpl.java` convertDownTheLineToFaceOn:181-184; `sdk/helper/UtilsKt.java` rotateXZ:118-155, adjustCoordinates3D:188-197; `sdk/helper/ScaleSkeletonKt.java:19-115`; `data/repository/AnalyzerRepository.java` generate3DPose ≈4903-4965 |
| Intrínsecos nunca escritos | grep sin resultados de `LENS_INTRINSIC_CALIBRATION`/`LENS_DISTORTION` en el código propio; `data/model/db/VideoMetaDataKt.java:28,75,152-156`; `data/model/db/VideoKt.java:46` |
| Flujo 2D del repositorio | `data/repository/AnalyzerRepository.java` (modo simple) generate2DPose$2.invokeSuspend:218-583 (caja fotograma 0 :543, pose :562, baja confianza :417-418,438, fps :439-440,521-522, palo :469, guardado :496) |
| Posiciones | `sdk/golf/swing/GolfPositionDetector.java` detectPosition:90-421; `sdk/golf/data/GolfSwingPosition.java:17-32`; `sdk/golf/SwingTrackerService.java` processSwingData:81-160, postProcess3D:68-79 |
| En vivo | `sdk/golf/swing/GolfSwingDetector.java` trackFps:142-162, scalePose:164-175, hasConfidence:178-230, isStartPose:232-320, processFrame:393-566, processFrameDtl:584-695; `sdk/golf/swing/GolfAutoSwingDetector.java:47-65`; `analysis/model/AutoSwingDetectionService.java` stream:157-287; `analysis/model/SwingActionDetector.java:51,111-171` |
| Vista | `sdk/golf/swing/GolfSwingAngleDetector.java:28-96`; `sdk/golf/swing/GolfCameraDirection.java:29-80`; `assets/configs/golf/sportsbox_swing_angle_coefficients.csv` |
| Marcos y trackers | `sdk/golf/swing/GolfTrackers.java` createGolfTrackers:91-177, adjustTopAngles:66-89; `sdk/common/data/VFrameKt.java:12-67`; `sdk/common/algorithms/Algorithms.java` augmentSkeletonPoints:273-300, unwrapAngle:38-66; `sdk/common/algorithms/CenterOfGravityKt.java:14-34`, `CoGCoefficient.java:15`; `sdk/common/algorithms/tracker/` JscAlgorithm:84-96, BendAlgorithm:26-28, SbendAlgorithm:26-28, DispAlgorithm:50-95, DiffAlgorithm:54-112, PangAlgorithm:53-65, VangAlgorithm:32-79, LinvelAlgorithm:54-122, LocturnvelAlgorithm:29-95, VvelAlgorithm:28-59, VveltimeAlgorithm:30-77, TrackerAlgorithms clamp90:68-89; `sdk/common/algorithms/PostProcess.java:28-77`; `assets/configs/golf/vframe_def.csv`, `sportsbox_trackers.csv` |
| Suavizado | `sdk/common/algorithms/Algorithms.java` smoothSwingFrameCoordinates:107-234, splitAndSmooth:236-271, smoothByConfig:82-94; `sdk/helper/LowPassFilter.java:14-44`; `sdk/helper/Biquad.java:28-42`; `sdk/helper/LinearAlgebraKt.java` quadraticExtrapolate:230-257, findQuadraticApproximation:92-144; `assets/configs/golf/sportsbox_filter_config.csv` |
| Indicadores | `sdk/common/indicator/SwingIndicatorService.java` indicatorResults:90-123, generateIndicatorConfigs:303-324, DependencyGraphResolver:375-432; `sdk/common/indicator/formula/` NearestTimeOperator:22-48, ReleaseTimeOperator:22-56, PlaneOperator:46-52, OrderOperator:22-43, PathLenOperator:24-56, ScanOperator:27-81, PhaseSummaryOperator:23-112; `assets/configs/golf/sportsbox_indicators.csv` |
| Rangos y puntuación | `sdk/helper/TrackerRangeScoreService.java:85-236`; `sdk/golf/swingscore/SwingScoreReportFactory.java:56-130`; `factor/SpeedPercentileFactor.java:47-265`, `EfficiencyScoreFactor.java:47-272`, `ConsistencyScoreFactor.java:38-170`, `SwingChecklistFactor.java:48-567`, `SwingProgressScoreBuilder.java:24-56`; `sdk/golf/assessment/SpeedAssessmentService.java:30-127`, `ConstantsKt.java:25-138`; `sdk/golf/goals/SwingGeniusService.java:36-135`; `sdk/golf/swing/library/SwingLibraryService.java:62-299`; `sdk/golf/data/SwingInfoKt.java:16-80`; JSON de rangos y de swing score en `assets/configs/golf/` |
| Bola | `analysis/model/BallMetricsProcessor.java` (simple) :107-307, detectBallCircle:338-354; `analysis/utils/BallCircleDetectorKt.java:16-36`, `BallProjectionIn3DSpaceKt.java:15-67`, `BallMetricsCalculatorKt.java:17-64`; `data/repository/AnalyzerRepository.java:5120,5197-5211` |
| fps | `sdk/helper/VideoUtil.java` detectFps:23-102; `ui/camera/editor/VideoCropViewModel.java` normalizeFps:437; `ui/camera/CameraNavigationHostKt.java` snapToNearestFps:1747 |
| Cámara | `camerax/injection/CameraCapabilityEvaluator.java:35`; `camerax/engine/CameraXCameraEngine.java:396,540,1047`; `Camera2CameraEngine.java:1314,2556,2890`; `CameraEngineViewModel.java:166,2008-2136,2190,2453-2465`; `ExposureManager.java:125,214`; `camerax/tts/SpeechEngine.java:45,80`, `VoiceGuidanceProcessor.java:352-562`; `camerax/ui/screens/CameraSetupGuidelineScreenKt.java:61`; `res/drawable/recording_guid1-4.png`, `measurement_reference.png`, `hip.png` |
| Nube y subida | `data/repository/AnalyzerRepository.java` runAnalysisFlow:4705, umbral :5109, caché :5787-5817; `ui/unity/AnalyzerViewModel.java:104,192,226`; `data/worker/UploadWorkerService.java:415-503,1230-1282,1378,1790-1860`; `data/repository/SessionUploadRepository.java:503,1005-1054`; `data/api/aiHighlights/AiHighlightsApi.java:13-19`; `data/api/AuthTokenInterceptor.java:27-34`; `ui/user/account/createAccount/CreateAccountFragment.java:384-413` |
| Unity | `ui/unity/UnityActivity.java:269-272,1251`; `ui/unity/layout/UnityPlayersContainerView.java:727-728,857,1316-1396`; `ui/unity/data/UnityCommand.java:90-266`; `ui/unity/bridge/UnityBridge.java`; `data/repository/UnityRepository.java:96,227-248,761-878`; `assets/aa/AddressablesLink/link.xml` |
| Exportación | `ui/inbox/recorder/ScreenRecordService.java:343,724,832,863`; `ui/inbox/review/ReviewRecordingFragment.java:270,387,517`; `ui/camera/editor/VideoCropViewModel.java` performVideoExport:455; `utils/VideoUtils.java` trimVideo:204 |
| Béisbol | `sdk/baseball/hitting/BaseballHittingSwingPositionDetector.java:47-273`, `BaseballHittingAutoSwingDetector.java:111-609`; `sdk/baseball/pitching/BaseballPitchingSwingPositionDetector.java:48-313`; `sdk/baseball/trackers/BaseballTrackers.java:56-181`; `assets/configs/baseball/*` |
| Niveles | `sdk/common/data/AccessLevel.java:23`; `common/rules/RuleEngineViewModel.java:137-382`; `ui/subscription/SubscriptionConstants.java:137-195` |

**Nativo.** `libnanodet.so`: símbolos exportados y dependencias en `native-libs.txt` (sin pseudocódigo: no hizo falta Ghidra).

**Pruebas guardadas** en `C:/kw/re/evidencias/sportsbox-re-20261020/` (7,9 MB): `hashes-sha256.json`, `inventario-*.json`,
`AndroidManifest.xml`, `apktool-resources.json`, `res-strings.txt`, `dex-classes.txt`, `own-classes.txt`, `tflite-models.txt`,
`native-libs.txt`, `libs-versions.txt`, `unity-metadata-strings.txt`, `trackers-golf-compact.txt`, `indicators-golf-compact.txt`,
`trackers-baseball-compact.txt`, `vframe-def-golf.txt`, `filter-config-golf.txt`. No se guardó código descompilado ni modelos.

**Sin confirmar:**
- Arquitectura exacta de la red de «lifting» (deducida de los operadores) y origen de sus datos; ejes exactos de su salida.
- Resolución a la que `OpenCVHelper` decodifica los fotogramas; rama exacta de la caja por fotograma tras el primero.
- Que el metadato `intrinsic` llegue alguna vez relleno (p. ej. desde iOS o el servidor) y que la distorsión se pase en algún camino.
- Semántica de «Disable Imp / Disable Auto» en las tablas.
- Interior de Unity: sistema de coordenadas y escala del avatar, órbita libre, «ghost», marca de agua.
- Verificación de la firma, versión exacta de Media3, ncnn y TFLite, otras etiquetas de NanoDet, uso de `GolfCameraDirection`,
  activación del módulo de béisbol en esta app, y la exactitud real de cualquier cifra (nada se ejecutó).

---

## 10. Método, recursos y limpieza
- **REA 6.5.0** con sus variables: `rea doctor` sano (Ghidra 12.1.4 y JADX); `rea decode-android-resources` (apktool 3.0.3, 12 s) para
  manifiesto y cadenas. Para los DEX se usó `jadx-core` del jar de REA con programas propios (`FullDecompile.java`, filtrado a
  `com.sportsbox.*` y el puente Unity: 2.941 archivos en 96 s; `FallbackDecompile.java` en modos SIMPLE y FALLBACK para 4 clases con
  cuerpos omitidos: 30 s cada uno), ambos **bajo el candado** `C:\kw\re\pesado.lock` con ≥ 6 GB libres comprobados. Ghidra no fue
  necesario (los símbolos de `libnanodet.so` con `llvm-nm`/`llvm-readelf` bastaron).
- **Scripts** en `C:\kw\re\_scripts\sportsbox\`: `extract_xapk.py` (extracción segura con hashes), `tflite_info.py`, `dex_classes.py`,
  `dex_strings.py`, `strings_dump.py`, `FullDecompile.java`, `FallbackDecompile.java`, `with_heavy_lock.ps1`, `run_jadx_full.ps1`,
  `run_jadx_fallback.ps1`. Python siempre con `-I`. Nada del APK se ejecutó ni se instaló.
- **Subagentes**: tres `general-purpose` con brief de sala limpia (interfaz/cámara/exportación; datos/nube/Unity; comparación/puntuación/
  diagnóstico/béisbol). Sin consultas web.
- **Limpieza**: se borraron las extracciones (`C:\kw\re\sportsbox\xapk`, `apk`), los árboles JADX (`jadx`, `jadx-simple`, `jadx-fallback`)
  y los registros; en `C:\kw\re\sportsbox\` no queda nada voluminoso. No quedan procesos java ni Ghidra propios (el proceso java de
  ~6,6 GB que había en la máquina es un demonio de Gradle de otra sesión).
