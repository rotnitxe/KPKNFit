# My Jump Lab 4.0.3 (Android) · ingeniería inversa estática

Fecha: 2026-10-20. Encargo: brief `docs/video-technique/briefs/RE_COMUN.md`. Trabajo en sala limpia: nada del APK se ejecutó ni se
instaló; no se copió código ni se extrajeron modelos. Las descripciones son con palabras propias; como evidencia solo van nombres de
clases, métodos y constantes. Lo que no se pudo verificar lleva «sin confirmar».

Herramientas: REA 6.5.0 (apktool 3.0.3 para recursos; el JAR de JADX 0.7.1 de REA, usado por su API para volcar únicamente las clases
`com.my.jump.lab.*` y `defpackage.*`), LLVM del NDK 27 (`llvm-strings`, `llvm-nm`, `llvm-readelf`) para lo nativo y scripts Python
propios con `-I` para el XAPK, los DEX y los modelos TFLite (en `C:\kw\re\_scripts\myjumplab\`). Pruebas pequeñas en
`C:/kw/re/evidencias/myjumplab-re-20261020/` (inventarios JSON, manifiesto y cadenas decodificadas, lista de clases DEX, forma de los modelos,
símbolos nativos; 3,9 MB).

---

## 1. Identidad

| Dato | Valor |
|---|---|
| Paquete · versión | `com.my.jump.lab` · 4.0.3 (versionCode 92) · minSdk 26 · targetSdk 36 · compileSdk 36 |
| Archivo | `My+Jump+Lab_4.0.3_APKPure.xapk`, 156 393 521 B, SHA-256 `aa0d400f6fd93edc9151391eb67c64a3e55a4fc40cab3de3f149d2b71d362728` |
| APK base | `com.my.jump.lab.apk`, 119 507 257 B, `83eee9ca13e2abef87df78458fbc7f26ed4d489b0cd60df5eab20e463baa5ee0` |
| Split ABI | `config.arm64_v8a.apk`, 36 590 507 B, `b765761e7afb0da993545ee3f5ae6dfc0e6a4caa79340370ea6b97385c159b6c` |
| Split idioma | `config.en.apk`, 127 385 B, `e584fd2b99cd51cc219fcfd42faddd0b4205bbeae8c834a89c0728f61a6d471f` |
| Split densidad | `config.mdpi.apk`, 94 502 B, `c7e621ff3e724c5421dc35c39315773229d48a30d9bf0bc2f6770a9a049da04c` |
| Manifiesto APKPure | `manifest.json`, `a2f380e56518bdb696edef551b182c1b26063082543974bbb6a8246e05050d22` |
| Firma Play | sello `com.android.stamp.source = play.google.com`, splits requeridos; aplicación `com.pairip.application.Application` (protección de licencia de Play Integrity: solo `LicenseActivity`/`LicenseClient`, sin virtualización de código) |

**Arquitectura.** App nativa Kotlin 2.2.0 (AGP 8.13.0, Gradle 8.14.5; revisión git `5ef603f9…` en `version-control-info`). Mezcla de
Views + DataBinding (los tests heredados) y Jetpack Compose 1.10 + Material 3 1.4 (inicio, onboarding, «What's new»). Hilt 2.57.2,
Room 2.8.4 (auto-migraciones hasta la v23), WorkManager 2.11, DataStore 1.1.7, CameraX 1.5.2 (incluida `camera-video` y
`featurecombinationquery`), ExoPlayer 2 para el visor (versión sin confirmar), Firebase (Firestore, Crashlytics, Sessions,
Installations), Play Billing 8.3.0, Play In-App Review. 7 DEX, 50 499 clases, 2 559 propias (331 de primer nivel sin generadas).

**ABIs y nativo** (solo `arm64-v8a` en este XAPK):

| Biblioteca | Tamaño | Qué es | Licencia |
|---|---|---|---|
| `libopencv_java4.so` | 24,7 MB | OpenCV **4.14.0** (`General configuration for OpenCV 4.14.0`); módulos core, imgproc, video (KalmanFilter, trackers MIL/GOTURN/DaSiamRPN/Nano/Vit, flujo óptico DIS/Farneback, MOG2/KNN), objdetect, features2d, calib3d, dnn, ml, photo, videoio, imgcodecs | Apache-2.0 |
| `libmediapipe_tasks_jni.so` | 10,5 MB | MediaPipe Tasks Vision (grafos PoseLandmarker, ObjectDetector, HandLandmarker, FaceLandmarker, FaceDetector, ImageSegmenter, GestureRecognizer) con TFLite/LiteRT, delegados XNNPACK, GPU (EGL/GLES2) y NNAPI; versión de MediaPipe sin confirmar (no hay cadena de versión) | Apache-2.0 |
| `libc++_shared.so`, `libimage_processing_util_jni.so` (CameraX), `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`, `libsurface_util_jni.so` | < 1,3 MB | runtime y AndroidX | Apache-2.0 |

De OpenCV la app solo usa, en código propio, `Imgproc.matchTemplate` (TM_CCOEFF_NORMED), `cvtColor`, `Core.rotate`, `minMaxLoc` y
`Utils.bitmapToMat`; el resto del binario va sin usar.

**Modelos de ML** (`assets/`, no se extrajeron):

| Modelo | Tamaño | Entrada | Salida | Clases |
|---|---|---|---|---|
| `plate_efficientdet_lite3.tflite` | 11 728 028 B | UINT8 `[1,512,512,3]` cuantizada (escala 0,0078125, cero 127) | 4 tensores de `TFLite_Detection_PostProcess` (cajas, clases, puntuaciones, número) | 1 clase, `plate` (etiquetas en el zip de metadatos: `plate`, `???`). Es un EfficientDet-Lite3 de Model Maker entrenado por el autor para discos de barra |
| `pose_landmarker_lite.task` | 5 777 746 B | zip con `pose_detector.tflite` (2,96 MB, FLOAT32 `[1,224,224,3]` → 2 254 anclas × 12 + puntuación) y `pose_landmarks_detector.tflite` (2,82 MB, FLOAT32 `[1,256,256,3]` → 195 = 39 puntos × 5, presencia, máscara 256×256, mapas 64×64×39, 117 = 39 × 3 «world») | Pose Landmarker **lite** de Google, de serie |

**Bibliotecas de terceros relevantes y licencia:** OpenCV 4.14.0 (Apache-2.0); MediaPipe Tasks (Apache-2.0); ZXing core + ZXing
Android Embedded de JourneyApps (Apache-2.0; se usa para el QR de migración); AAChartCore-Kotlin (gráficos; MIT) que embebe
**Highcharts** (`assets/AAHighcharts.js`, licencia comercial, no reutilizable sin licencia); Glide (BSD/MIT/Apache); Gson, kotlinx.serialization,
gRPC + protobuf (Firestore). Las versiones de AndroidX exactas están en `C:/kw/re/evidencias/myjumplab-re-20261020/apk_inventory.json` y en el informe de
`META-INF/*.version`.

**Permisos y sensores.** `CAMERA`, `INTERNET`, `ACCESS_NETWORK_STATE`, `READ/WRITE_EXTERNAL_STORAGE` (solo hasta API 32/28),
`BILLING`, `CHECK_LICENSE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (WorkManager). Sensores: acelerómetro,
magnetómetro y gravedad (My ROM y «Force data»). Sin BLE, sin GPS (play-services-location va como dependencia, sin permiso), sin
Health Connect, sin micrófono. Consulta a `com.twitter.android` y `com.google.android.youtube` (enlaces).

**Pantallas declaradas:** `OnboardingActivity`, `WhatsNewActivity`, `MainActivity` (Compose), `MediaStoreActivity` (videoteca),
`TestActivity`, `ReadinessActivity`, `MyRomActivity`, `ForceDataActivity`, `NewNordicsActivityRefactored` (y `NordicsActivity`
heredada), `MyJumpActivity`, `MyJumpAutoActivity` (IA), `MyJumpHorizontalActivity`, `MyLiftActivity` (manual), `MyLiftAutoActivity`
(automático), `RunningActivity`, `SprintingActivity`, `RedirectionActivity` (COD), `RunningMarkersActivity`, `SprintingMarkersActivity`
(kinograma), `ExoPlayerActivity`, `ParallaxCalculatorActivity`, `SyncDiagnosticActivity`, `SlowMotionCameraActivity`.
`MyLiftTrackingActivity` existe pero es una demo sin ruta (pasa el detector por seis imágenes de `drawable`).

---

## 2. Inventario completo de funciones

El catálogo de pruebas está en `assets/tests_configuration.json` (categorías → subcategorías → `finalTests` con un
`calculationType` 1–24). Toda la suite es de pago (planes mensual, anual y vitalicio por Play Billing; prueba de 7 días con código
promocional); sin compra solo se abre el diálogo de compra (`Test.hasAnyPurchase()`).

### 2.1 Común
- **Onboarding**: unidades (métrico/imperial), primer equipo y primer atleta (nombre; altura y peso opcionales, «se usan en cálculos
  biomecánicos»). **What's new**: «AI Jump», «cámara lenta nueva», «importar vídeos al VBT», sincronización en la nube, planes.
- **Equipos y atletas**: foto, masa, estatura, longitud de pierna, altura a 90° (para hp0 = pierna − altura a 90°), «lever»
  (nórdicos), ROM por ejercicio (sentadilla, banca, peso muerto, militar, dominada, hip thrust) y v1RM por ejercicio.
- **Pantalla de cada prueba**: tutorial deslizable, «Scientific background» (lista de artículos con enlace), resultados con el gráfico
  de la variable elegida (AAChart), notas, borrado por pulsación larga.
- **Ajustes**: unidades; **auto-fps** (usar los fps máximos del dispositivo; si se desactiva, al importar un vídeo pide los fps reales);
  guardar vídeos automáticamente; temporizador de cuenta atrás; **diámetro del disco** (0,45 m por defecto); umbral de pérdida de
  velocidad (10 %) y velocidad objetivo (1,0 m/s, opcional); umbrales de readiness (naranja 1 DE, rojo 1,5 DE); método de dos
  puntos para los perfiles; reactivar tutoriales.
- **Exportar datos**: CSV por tipo de prueba (cabeceras en las cadenas `EXPORT_*`). **Sincronización**: Firestore con «sync
  inteligente» (solo diferencias), migración entre dispositivos por QR (ZXing) y pantalla de diagnóstico.
- **Videoteca** (`MediaStoreActivity`) y visor con avance por fotograma (`ExoPlayerActivity`).

### 2.2 My Jump 3 (saltos)
| Prueba | Entrada | Qué pide |
|---|---|---|
| Vertical rápido: CMJ, SJ, CMJ con brazos libres, DJ, «RSI» (RSI modificado) | vídeo (cámara lenta propia, cámara nativa o galería) y **selección manual** de fotogramas: despegue = primera imagen con los dos pies en el aire; aterrizaje = primera con un pie en el suelo; en DJ y RSImod además el contacto inicial | carga externa (kg), altura del cajón en DJ |
| **AI Jump** (CMJ y SJ) | cámara en vivo a 30 fps o vídeo importado; pose + silueta; sin marcar nada | estatura del atleta en su perfil (0,8–2,6 m) |
| Perfil fuerza-velocidad (CMJ o SJ) | 4 saltos con 4 cargas (o 2 con el método de dos puntos), cada uno con sus fotogramas | masa, hp0, cargas |
| Salto horizontal | 4 marcadores: cabeza y pies (escala con la estatura), punta del pie al despegar y talón al aterrizar | estatura |
| Asimetría (CMJ o DJ unilateral) | un vídeo por pierna con despegue/aterrizaje (y contacto en DJ) | — |
| Saltos repetidos «10-5» | CMJ + 10 saltos reactivos en 15 s; despegue y contacto de cada uno | — |
| Calculadora manual | alturas y cargas tecleadas (1, 2 o 4) | — |

### 2.3 My Lift (VBT)
- **Método**: manual (avance fotograma a fotograma; «funciona con cualquier ejercicio»; requiere el ROM del ejercicio) o automático
  (seguimiento del disco; «solo con barra»; sin ROM).
- **Feedback**: tiempo real (cámara, sin importar vídeo; precisión «según el dispositivo») o tras la serie (vídeo grabado o importado;
  «la precisión no depende del procesador»).
- **Pruebas**: *Full* (4 cargas → perfil carga-velocidad → 1RM), *Quick* (1 carga → 1RM con el perfil guardado), *Free mode*
  (velocidad y potencia media por repetición, pérdida de velocidad, «trayectoria»).
- **Ejercicios**: seis plantillas con v1RM por defecto (banca 0,17; sentadilla 0,32; dominada 0,30; peso muerto 0,30; militar 0,18;
  hip thrust 0,34 m/s; `ReadMyLiftTemplates`) y ROM a rellenar; ejercicios propios con nombre, ROM y v1RM.
- **Tiempo real**: voz (TTS) con la velocidad de cada repetición, pérdida de velocidad en pantalla con color verde/rojo y sonido
  `velocity_loss.ogg` al superar el umbral o bajar de la velocidad objetivo.
- Los textos «Augmented Reality Mode» (ARKit) son herencia de iOS: en Android no hay actividad de AR.

### 2.4 Runmatic (carrera y esprint)
- **Carrera**: asimetría (contacto y despegue de 9 pasos seguidos, grabado desde atrás, a velocidad constante); técnica (marcadores:
  patrón de apoyo y «overstriding» de lado; pronación y caída pélvica desde atrás); biomecánica (contacto, vuelo, frecuencia,
  oscilación vertical, fuerza máxima relativa, rigidez de pierna). Pide velocidad de carrera, masa y longitud de pierna.
- **Esprint**: lineal con parciales (toques «start/split/OK»; perfil F-V horizontal); esprint curvo de 17 m (arco del área);
  tiempos de contacto y vuelo; **kinograma** (5 fases × 6 marcadores + cuestionario S-MAS de 12 preguntas); **deceleración**
  (10 m + frenada con 8 marcas).
- **Cambio de dirección** («timing gates» por toques): 5-0-5, 5+5, V-Cut (25 m) e Illinois (60,62 m).
- **Calculadora de paralaje**: posiciones corregidas de los conos para un esprint grabado desde el punto medio.

### 2.5 Readiness, Nordics, My ROM y «More tools»
- **Readiness**: 4 preguntas (sueño, agujetas, fatiga, estrés) en 1–10; semáforo frente a la media de los dos últimos meses.
- **Nordics**: bilateral y unilateral; en el fotograma de «breakpoint» se ponen 3 marcadores (maléolo, cóndilo, trocánter).
- **My ROM**: flexión/extensión de cuello, rotación y flexión de hombro, rotación y flexión de cadera, dorsiflexión, pruebas propias
  (cero vertical u horizontal); cuenta atrás de 5 s con pitido; el móvil apoyado en el segmento.
- **Force data**: curvas fuerza-tiempo con el acelerómetro del móvil (25–200 Hz, masa del sistema), en vivo.
- **Sin equivalente en Android** aunque sus textos existen: My Mocap (16 articulaciones, valgo de rodilla, profundidad de
  sentadilla en vivo), My Balance (giroscopio) y el modo AR.

---

## 3. Catálogo de métricas

### 3.1 Saltos (`MyJumpActivity.calculateResults`, `MyJumpAutoActivity.computeAndShowResults`, `MyJumpSummaryFragment`)
| Métrica | Definición en el código | Unidades · cuándo |
|---|---|---|
| Tiempo de vuelo | diferencia entre los instantes de aterrizaje y despegue elegidos; el instante = posición del reproductor ÷ (fps reales ÷ fps del archivo) para corregir la cámara lenta | ms |
| Altura | h = g·t²/8, escrito como t² × 1,22625 (m); en cm en pantalla | siempre |
| Fuerza media | m·g·(h/hp0 + 1) [Samozino]; con carga: (m + carga)·g·(…) | N y N/kg |
| Velocidad media | √(g·h/2) | m/s |
| Potencia media | F·v | W y W/kg |
| Velocidad de despegue | √(2·g·h) | m/s |
| «Impulso» | v_despegue·m, y además ×10 salvo en RSI | kg·m/s (el factor 10 no está explicado; sin confirmar) |
| Tiempo de contacto | despegue − contacto inicial | ms · DJ, RSI, unilateral DJ |
| RSI | t_vuelo / t_contacto | DJ y asimetría |
| RSImod | h / (0,90043·t_contacto + 0,14319) → corrección lineal del tiempo hasta el despegue medido a mano, en la línea de Bishop 2022 (que publicó 0,8947 y 0,1507) | prueba «RSI» |
| Rigidez vertical (DJ) | F_max = m·g·(π/2)·(t_v/t_c + 1); Δz = \|−F_max/m·t_c²/π² + g·t_c²/8\|; k = F_max/Δz, en kN/m | DJ |
| Asimetría | \|100 − 100·L/R\| de tiempos de contacto y de vuelo entre piernas; más fuerza, velocidad, potencia e impulso por pierna | unilateral |
| Saltos repetidos | alturas de cada salto por tiempo de vuelo; RSI_i = h_i / t_contacto_i; **RSI 10-5** = media de los 5 mejores; CMJ inicial | RJ |
| Perfil F-V | por carga: F_i = (m + carga_i)·g·(h_i/hp0 + 1), v_i = √(g·h_i/2); regresión lineal F = a + b·v → F0 = a/m (N/kg), pendiente/m, V0 = −a/b, Pmax = F0·V0/4 (W/kg); pendiente óptima por la fórmula cerrada de Samozino 2012 (cúbica en hp0 y g); desequilibrio = \|1 − pendiente/óptima\|·100; F0 y V0 óptimos; aviso si R² < 0,90; déficit de fuerza o de velocidad y programa de entrenamiento sugerido (Jiménez-Reyes 2017) | FV |
| Salto horizontal | distancia = píxeles entre punta (despegue) y talón (aterrizaje) × estatura / píxeles cabeza-pies | cm |
| Altura óptima de cajón, altura máxima teórica | textos presentes; cálculo a partir del perfil óptimo (`MyJumpSummaryFragment`, ecuación de h con pendiente óptima y Pmax) | FV |

**AI Jump**: altura = desplazamiento máximo del punto medio de las orejas respecto a la posición inicial, convertido a metros con la
estatura (ver §4.2), y después corregido linealmente: h = 0,9987·d − 0,0634 m; mínimo 0,09 m. El tiempo de vuelo mostrado se
**deriva** de la altura (t = √(h/1,22625)), no se mide. Fuerza, velocidad, potencia, despegue e impulso salen de las mismas fórmulas.

### 3.2 VBT manual (`MyLiftActivity.calculateResults`, `MyLiftSummaryFragment`)
| Métrica | Definición |
|---|---|
| Velocidad media | ROM del ejercicio (introducido en cm) ÷ (fotograma final − inicial, en s corregidos por cámara lenta) |
| Fuerza media | (0,114·m_corporal + carga)·a·(throw/ROM + 1), con a = 2·g − «fricción» y throw = 2·v²/fricción; es decir, se modela el empuje como un lanzamiento, con masa del miembro superior = 11,4 % de la masa corporal; «fricción» es un parámetro del ViewModel (origen sin confirmar) |
| Potencia media | v × F |
| 1RM (Full) | regresión carga (kg) frente a velocidad de las 4 cargas; 1RM = carga en v1RM del ejercicio; en dominadas la carga suma la masa corporal; R² con aviso si < 0,90. Además se ajusta %1RM frente a velocidad y se guardan pendiente e intersección |
| 1RM (Quick) | con el último Full: %1RM = pendiente·v + intersección; 1RM = 100·carga/%1RM; sugiere la carga del «siguiente %». Sin Full previo solo muestra la velocidad |
| Pérdida de velocidad (modo fatiga, manual) | se miden la primera y la última repetición; VL = 100·(1 − v_última/v_primera) (textos; cálculo en `MyLiftSummaryFragment`, sin confirmar la fórmula exacta) |

### 3.3 VBT automático (`MyLiftAutoFragment`, `MyLiftAutoActivity.calculateResults`, `IosRepDetector`)
| Métrica | Definición |
|---|---|
| Desplazamiento vertical | centro de la caja del disco, en cm: (y_inicial − y)·factor, con factor = diámetro (cm) / diámetro en píxeles de la **primera** detección (en vivo: mín(ancho, alto) de la caja; en vídeo: media de ancho y alto) |
| Velocidad media por repetición | ROM / Δt entre el mínimo y el máximo de posición de cada repetición (no hay derivada ni filtro en el cálculo del valor mostrado) |
| ROM por repetición | máximo − mínimo del desplazamiento |
| Potencia media | carga·g·ROM / (ROM/v) = carga·g·v (ignora la aceleración) |
| Pérdida de velocidad | VL = 100 − 100·v_última/v_máxima de la serie; alerta si supera el umbral (10 % por defecto) o si v < velocidad objetivo |
| 1RM Full/Quick | como en manual, con la **mejor** velocidad de la serie como velocidad de la carga |
| Series derivadas | velocidad, «aceleración», «fuerza» y «potencia» = derivadas sucesivas por diferencias finitas de la posición (etiquetas engañosas: son d³x/dt³ y d⁴x/dt⁴); se guardan `lowPassYArray`, `velocityArray`, `lowPassVArray` para los gráficos |
| Trayectoria | solo vertical; existen arreglos para X (`xCorrectedArray`, `lowPassXArray`) pero ningún código los rellena en este APK |

### 3.4 Runmatic (`RunningActivity.calculateResults`, `SprintingActivity`, `RedirectionActivity`, `*MarkersActivity`)
| Métrica | Definición |
|---|---|
| Contacto y vuelo | por paso, de los instantes marcados (4 pasos por pierna); medias, DE y CV por pierna |
| Frecuencia | 1/(t_c + t_v) |
| F_max | 1,57·m·g·(t_v/t_c + 1) (Morin 2005, con π/2 ≈ 1,57); también en pesos corporales |
| Oscilación vertical Δz | \|−F_max/m·t_c²/π² + g·t_c²/8\| (π² escrito 9,8596) |
| Compresión de pierna ΔL | L − √(L² − (v·t_c/2)²) + Δz, con L = longitud de pierna y v la velocidad introducida |
| Rigidez de pierna | F_max/ΔL en kN/m (también vertical F_max/Δz) |
| Duty factor | media de t_c/(2·(t_c + t_v)) de ambas piernas, en % |
| Asimetrías | \|100 − 100·izq/der\| de contacto, vuelo, frecuencia, Δz, F_max/PC y rigidez; aviso si > 10 % |
| Técnica (marcadores) | ángulo de apoyo (suela-punta frente a la horizontal; antepié/retropié), ángulo de tibia respecto a la vertical (ideal < 10°), pronación (línea del talón, ideal < 5°), caída pélvica (línea entre espinas ilíacas; «< 5 % seguro»); cuenta de «factores de riesgo» (de 6 u 8) |
| Esprint lineal | tiempos por parcial (toques), velocidad media por tramo, aceleración por tramo; perfil F-V horizontal con `MotionModel` (ver §4.6): F0, V0, Pmax, RFmax, DRF, curvas v(t), F(t), P(t); velocidad máxima tomada a 30 m |
| Esprint curvo | solo el tiempo total |
| Contactos en esprint | asimetría de contacto y vuelo entre piernas (toques alternos) |
| Deceleración | sprint de 10 m y frenada: velocidades por tramo (5, 10, 11, 12… m nominales; los conos van en posiciones corregidas de paralaje: 5,24; 10; 10,95; 11,90; 12,86; 13,81; 14,76; 15,71 m), deceleración máxima = aceleración mínima, tiempo hasta parar, velocidad a 10 m del modelo, «fuerza de frenado» = m·\|a_mín\| + 0,266·0,2025·h^0,725·m^0,425 (término de área frontal de Du Bois, sin la velocidad; sin confirmar la intención) |
| COD | tiempo entre toques; velocidad media = distancia del test/tiempo (10 m en 5-0-5 y 5+5; 25 m V-Cut; 60,62 m Illinois) ×3,6; **déficit COD = tiempo − 2·t_10**, donde t_10 es el tiempo del primer tramo marcado; tiempo de contacto del giro = despegue − contacto |
| Kinograma | en cada fase (toe-off, MVP, strike, touchdown, full support) con 6 marcadores: ángulo del tronco (oreja-cadera frente a la vertical), flexión/extensión de cadera, ángulos de rodilla delantera y trasera (ley del coseno); S-MAS 0–12 con categorías |

### 3.5 Nordics, Readiness, My ROM, Force data
- **Nordics** (`BiomechanicsCalculator`): ángulo de breakpoint = ángulo muslo − ángulo pierna (atan2 de los tres marcadores, según el
  lado al que mira); **torque** = (0,65·m + carga)·g·lever·sin(90° − (180° − ángulo del muslo)); asimetría \|100 − 100·mín/máx\|.
  La actividad heredada usaba la masa corporal completa.
- **Readiness** (`ReadinessActivity`): puntuación = media de las cuatro respuestas (fatiga y estrés invertidos: 10 − valor); semáforo
  comparando con la media de los últimos dos meses: verde si la caída < 1 DE, naranja entre 1 y 1,5 DE, rojo por encima (umbrales
  editables); misma lógica de semáforo para el CMJ diario (texto `TRAFFIC_MESSAGE`).
- **My ROM** (`MyRomCountdownFragment`): matriz de rotación de acelerómetro + magnetómetro → orientación; ángulo = \|pitch\| o
  \|roll\| según el test (\|90° + pitch\| con cero vertical); asimetría \|100 − 100·mín/máx\|; aviso si dorsiflexión < 40°.
- **Force data**: aceleración = \|a\|·g del acelerómetro, con filtro exponencial α = dt/(RC + dt); fuerza = masa del sistema·(a + g);
  muestreo configurable 25–200 Hz. No hay validación publicada; es un visor.

---

## 4. Algoritmos

### 4.1 Pose y detección (clases `presentation/utils`)
- **`PoseLandmarkerHelper`**: Pose Landmarker lite, modo VIDEO, `numPoses = 1`, confianzas 0,5, sin máscara; delegado GPU por
  defecto con **respaldo a CPU** si falla la creación o aparece «GPU» en el error, y se marca MediaPipe como «no disponible» ante un
  `LinkageError`. Un segundo landmarker en modo IMAGE y CPU **con máscara de segmentación** sirve solo para la calibración: de la
  máscara se extrae la **silueta** (primera y última fila con ≥ 3 píxeles > 0,5, `Silhouette`), y se avisa si toca el borde superior o
  inferior («el atleta no cabe»). Tras 10 fallos de lectura de máscara se reintenta en CPU.
- **`EarMidpointMetricsProvider`**: punto seguido = media de las orejas (7, 8); «estatura en píxeles» = media de tobillos (27, 28)
  menos orejas; exige visibilidad ≥ 0,5 en los cuatro puntos.
- **`ObjectDetectorHelper`**: ObjectDetector de MediaPipe con `plate_efficientdet_lite3.tflite`, umbral 0,5, 1 resultado, CPU por
  defecto, modo IMAGE (cada fotograma independiente); gira el bitmap según la rotación del sensor antes de inferir.
- **`PlateTracker`** (solo en el análisis de vídeo): plantilla en gris de la caja detectada; búsqueda con `matchTemplate`
  (TM_CCOEFF_NORMED) en una ventana de ±½ caja alrededor de la última posición; se acepta si la puntuación ≥ 0,6; si no, se vuelve a
  pasar el detector y se **relocaliza** la plantilla cerca de la nueva caja (margen 1/10). Sin Kalman, sin cambio de escala, sin
  subpíxel; la plantilla no se renueva nunca.

### 4.2 AI Jump (`MyJumpAutoActivity`, `JumpTracker`, `CalibrationProfile`)
1. Cámara trasera, `ImageAnalysis` RGBA 640×480 a 4:3 con rango de fps fijado a 30–30; o lectura de un vídeo con `VideoFrameReader`
   (MediaCodec → YUV → ARGB, reducido a ≤ ~640 px de lado largo; fps estimados por los PTS).
2. **Estabilización**: con la silueta completa, se guardan 10 alturas de silueta; cuando (máx − mín)/media < 2 % se «bloquea la
   referencia»: píxeles de estatura = media de la ventana, y a partir de ahí metros/píxel = estatura del atleta / silueta. Pitido.
3. **Seguimiento**: desplazamiento = (y_ref − y_orejas)·m/px, con y_ref el primer valor tras bloquear; calibración lineal
   0,9987·d − 0,0634 (compensa que la cabeza sube más que el centro de masas: extensión de tobillo, elevación de hombros).
4. **Detección de saltos**: un máximo local con retardo = `neighborhood` + 1 fotogramas y vecindad `neighborhood` a cada lado (14 a
   30 fps; max(4, 0,4667·fps) en vídeo) por encima de 0,09 m. Cada pico es un salto; se muestra el mejor y la lista.
5. **Calidad**: mide los fps reales en ventanas de 1 s; si tras 5 s de grabación están por debajo de 25 fps, se marca el resultado
   como «imprecisión posible por rendimiento». `DevicePerformanceSingleton` consulta la *media performance class* (≥ 34 = alto).
6. El resultado se vuelca en las fórmulas de §3.1 con la carga a 0.

### 4.3 VBT automático
- **En vivo** (`MyLiftAutoFragment.onResults`): cada detección da (t desde la primera detección, y del centro). Factor de escala fijo
  con la primera caja. Detección de repeticiones por **máquina de estados con extremos móviles**: en bajada se sigue el mínimo; se
  confirma mínimo cuando la señal sube > 3 cm sobre él (`REST_BAND_CM`) y la amplitud frente al máximo anterior supera
  `MIN_REP_AMPLITUDE_CM = 5`; después se sigue el máximo y se confirma cuando la señal cae; la repetición se acepta si su velocidad
  media (ROM/Δt) está entre 0,08 y 2 m/s, si no se descarta. Al confirmar: voz, pérdida de velocidad y colores.
- **Vídeo** (`analyzeVideo`): `VideoFrameReader` + detector al inicio y cuando el tracker se pierde + `PlateTracker`; el tiempo de cada
  muestra es índice·1000/fps (fps del archivo o tecleados), **no los PTS**. Al terminar, `IosRepDetector.detect` (puerto del algoritmo
  de iOS) sobre la serie: escala la señal por (1 − 5/fps); exige ≥ 21 muestras; recorre de 10 a n−11 buscando mínimos (menor que las 6
  muestras siguientes, caída > 2 cm en 3 muestras, estable 3 muestras antes, por debajo del 50 % del rango) y luego máximos (mayor que
  ±10 vecinos y por encima del 40 % del máximo global); velocidad = ROM/Δt; conserva 0 < v < 2 m/s.
- **Tras la serie** (`MyLiftAutoActivity.calculateResults`): filtro exponencial de factor 5/fps (por su construcción equivale a
  multiplicar el desplazamiento por 1 − 5/fps), derivadas por diferencias, potencia carga·g·v y VL.
- Nota de diseño: el mismo factor (1 − 5/fps) aparece en ambos caminos (0,83 a 30 fps, 0,92 a 60 fps); parece una corrección
  empírica del sobreestimado de la caja, no está documentada (sin confirmar).

### 4.4 Calibración y escala
| Qué | Cómo |
|---|---|
| Estatura, masa, pierna, altura a 90° (hp0), lever | del perfil del atleta; hp0 = pierna − altura a 90° (texto `ROM_DESC_2`) |
| Diámetro del disco | ajuste global (0,45 m por defecto, editable al vuelo); escala = diámetro / tamaño de la **primera** caja del detector, constante toda la serie |
| Distancia (horizontal) | dos marcadores cabeza-pies con la estatura; en esprint, distancias de los parciales tecleadas |
| fps y cámara lenta | fps del contenedor por `MediaExtractor` («frame-rate»); con auto-fps los eventos se convierten con fps_reales/fps_archivo; sin auto-fps y vídeo de galería, pide los fps reales. Avance por fotograma = ±1000/fps ms en ExoPlayer |
| Cámara lenta propia | `SlowMotionCameraActivity`: CameraX 1.5 `HighSpeedVideoSessionConfig`, elige el mayor fps constante ≤ 240 entre los rangos soportados; si no hay, cámara nativa (`ACTION_VIDEO_CAPTURE`) |

### 4.5 Perspectiva, suavizado, calidad
- No hay corrección de perspectiva ni de inclinación (ni homografía, ni elipse, ni acelerómetro); las instrucciones piden grabar de
  lado (barra, nórdicos, esprint), de frente mirando a los pies (saltos manuales), de frente de cuerpo entero (AI Jump), desde atrás
  (carrera). El único paralaje tratado es el de los conos del esprint (`ParallaxCalculatorActivity`: d' = d + (M − d)·c/(c + M), con
  M = mitad del esprint y c = 0,5 m de separación lateral de los conos).
- Suavizado: solo el exponencial de primer orden (`LowPassFilterSignal`, factor «lower = smoother» editable en iOS; aquí fijo 5/fps).
  Sin Butterworth, sin Savitzky-Golay, sin Kalman en el código propio.
- Controles de calidad: visibilidad ≥ 0,5 de orejas y tobillos, silueta completa y estable, fps medidos, rendimiento del dispositivo,
  R² < 0,90 en perfiles, valores no finitos reemplazados (`NonFiniteValuesKt`), «no se detectaron repeticiones/saltos».

### 4.6 Perfil F-V de esprint (`MotionModel`, paquete por defecto)
Entrada: masa, estatura (m), temperatura fija 21 °C, presión fija 760 mmHg, tiempos acumulados de los parciales y sus distancias
(0, d, 2d…; en deceleración solo los dos primeros, 5 y 10 m).
1. **Modelo de posición** (Samozino 2016, monoexponencial): x(t) = v_hmax·(t + τ·e^(−t/τ)) − v_hmax·τ. Se ajustan v_hmax y τ por
   mínimos cuadrados sobre los parciales con un descenso de gradiente rudimentario (`optimizeGRG`: gradiente numérico con paso
   1e-8, tasa 0,001, hasta 1 000 iteraciones, tolerancia 1e-6, arranque v_hmax = 8 m/s y τ = 1 s, parámetros acotados > 0). Se guarda
   la suma de residuos al cuadrado, pero no se muestra ni se usa para avisar.
2. **Muestreo** cada 0,01 s hasta el último parcial: v(t) = v_hmax·(1 − e^(−t/τ)); a(t) = v_hmax/τ·e^(−t/τ); posición por suma
   acumulada.
3. **Rozamiento del aire**: F_aero = k·v², con ρ = 1,293·(P/760)·273/(273 + T) y k = 0,5·ρ·(0,266·0,2025·h^0,725·m^0,425)²·0,9.
   El área frontal (Du Bois × 0,266) aparece **elevada al cuadrado**, cosa que no está en la formulación publicada (k = 0,5·ρ·Af·Cd);
   sin confirmar si es intencional. Con 1,75 m y 75 kg el término queda en torno a la mitad del publicado.
4. **Fuerza horizontal** F_h = m·a + F_aero; regresión lineal de F_h/m frente a v → F0 (N/kg, intersección), pendiente S_FV,
   V0 = −F0/S_FV, Pmax = F0·V0/4 (W/kg).
5. **Eficacia**: RF(t) = F_h/√(F_h² + (m·g)²)·100; RFmax = máximo con t > 0,5 s; DRF = pendiente de RF frente a v.
6. Salidas: F0, pendiente, V0, Pmax, RFmax, tiempos, posiciones, velocidades, aceleraciones, F_h/m, P/m y DRF (`calculateProfile`);
   `SprintingActivity` toma además la velocidad modelada a 30 m como «velocidad máxima» y, en deceleración, la modelada a 10 m.

---

## 5. Exportación y compartir
- **CSV** por prueba (`ExportDataFragment`): columnas definidas en las cadenas `EXPORT_VARIABLES_*` (por ejemplo, salto: fecha, equipo,
  nombre, masa, hp0, tipo, cajón, carga, altura, RSImod, tiempo hasta despegue, vuelo, contacto, fuerza, velocidad, potencia, RSI,
  rigidez, color de readiness; carrera: contacto, vuelo, frecuencia, oscilación, F_max/PC, rigidez, asimetría; VBT: 1RM, cargas y
  velocidades medias). Se comparte con `ACTION_SEND` por FileProvider.
- **Kinograma**: cinco JPEG (una por fase, con los marcadores y líneas dibujados sobre el fotograma) por `ACTION_SEND_MULTIPLE`
  (`BitmapUtils.shareRunImages`); imágenes sueltas con `shareImage`.
- **Gráficos** en la app con AAChart/Highcharts (barras de velocidad por repetición con VL en el subtítulo, líneas de posición y
  velocidad, historial por variable).
- **No existe** exportación de vídeo con telemetría quemada, ni superposición de trayectoria sobre el vídeo (`OverlayView` solo
  calcula el rectángulo del disco y no llega a dibujarlo), ni CSV de la traza cruda en Android (la cabecera `EXPORT_TRACKING` existe
  como texto; no se encontró código que la use; sin confirmar).
- Nube: Firestore (equipos, atletas y todas las pruebas; DTO por tipo), sin vídeos.

## 6. Datos e integraciones
- Room (`RoomDB`, tablas Team, User, MyJump, MyLift, MyLiftAuto, MyLiftTemplate, MyRom, Nordics, Readiness, Run, SyncOperation,
  DeletedEntity). Los vídeos se guardan opcionalmente en MediaStore («auto-save»).
- Firestore con cola de operaciones (`SyncOperationDAO`, `SyncWorker`), id de dispositivo, migración por QR; Crashlytics con registro de
  excepciones. El texto de privacidad dice que la app no recoge datos; en la práctica sincroniza a Firebase y envía informes de fallo
  (sin datos de salud explícitos).
- Sin BLE, wearables, Health Connect ni cuentas propias (la compra va por Google Play).

## 7. Las tres preguntas del usuario
1. **Perspectiva.** Exige el encuadre de las instrucciones y no corrige ángulos oblicuos. La escala del disco se toma de una caja
   (axis-aligned) del detector una sola vez, sin elipse ni actualización; la cámara inclinada o no perpendicular pasa inadvertida. En
   saltos y carrera todo son tiempos (vuelo, contacto) o ángulos 2D sobre marcadores manuales. Lo único «de perspectiva» es la
   calculadora de paralaje de conos.
2. **Tracking híbrido.** No. El automático es solo disco de barra (una clase). Sin barra hay que usar el modo manual (fotograma a
   fotograma con el ROM medido con cinta métrica). Si el disco se pierde en vídeo, plantilla → redetección; en vivo simplemente no hay
   muestra. La pose solo se usa en AI Jump, y en ella solo orejas, tobillos y la silueta.
3. **Vídeo con telemetría.** No existe en Android (ni en los textos de iOS, salvo el «vídeo generado» del modo AR). Se comparten CSV
   y los JPEG del kinograma; los gráficos viven dentro de la app.

## 8. Contraste con los estudios publicados
| Módulo | Lo que valida la literatura | Lo que hace este APK | Lectura |
|---|---|---|---|
| Altura de salto por tiempo de vuelo | Balsalobre-Fernández et al. 2015, J Sports Sci (My Jump vs plataforma, ICC ≈ 0,997); Gallardo-Fuentes 2016 (fiabilidad) | misma fórmula g·t²/8 con fotogramas elegidos a mano, cámara lenta hasta 240 fps | coincide |
| RSImod y tiempo hasta el despegue | Bishop et al. 2022, J Hum Kinet 83: sesgo de altura 0,001 m, LoA −0,031..0,033 m; TTTO corregido con y = 0,8947x + 0,1507 (iPad 240 fps, 3 m) | corrección 0,90043x + 0,14319 (coeficientes distintos; origen sin confirmar) | coincide en método |
| Perfil F-V de salto | Samozino 2008/2012/2014 (método simple, perfil óptimo); PeerJ 2023 (Jumpo 2 y MyJump 2 en Android): altura válida, velocidad y potencia «poco válidas» | fuerza y velocidad medias de Samozino, pendiente óptima cerrada, desequilibrio, dos puntos (García-Ramos) | coincide; la validez de las derivadas es la discutida |
| AI Jump | Balsalobre-Fernández y Varela-Olalla 2024, Sensors 24(24):7897 (iOS, iPhone 14 Pro, 60 Hz 1080p, frontal a 1,2 m): r 0,984 altura, sesgo +0,87 cm, LoA −2,66..+4,39 cm, CV 5,8 % | Android a 30 fps y 640×480, altura por desplazamiento de la cabeza escalado con la estatura, vuelo derivado | el artículo no describe el método; la versión Android es de menor resolución temporal (sin confirmar equivalencia) |
| My Lift manual | Balsalobre-Fernández 2018 (PowerLift, banca: r 0,94 vs transductor, SEE 0,028 m/s); EJHM (peso muerto y sentadilla, 1RM R² 0,99) | v = ROM/Δt con fotogramas manuales y ROM medido | coincide |
| My Lift automático | Balsalobre-Fernández 2023, JSCR (tiempo real, iPhone 12 Pro, banca 50/75 %: r 0,90/0,92, CV 8–14 %); Renner 2024, PLOS ONE (My Jump Lab 3.2.9: 175 omisiones, 16 % de detección en banca) | detector de una clase a 0,5 + plantilla 0,6 + amplitud ≥ 5 cm, banda 3 cm, 0,08–2 m/s; factor (1 − 5/fps) | explica las omisiones: sin disco visible no hay muestra y la plantilla no se adapta |
| Runmatic | Balsalobre-Fernández, Agopyan y Morin 2017, J Appl Biomech 33(3): r 0,94–0,99, ICC 0,965–0,991 vs optoelectrónico en cinta | ecuaciones de Morin 2005 (F_max, Δz, ΔL, rigidez) sobre contactos marcados a mano | coincide |
| MySprint | Romero-Franco et al. 2017, Eur J Sport Sci 17(4): parciales r 0,989–0,999, SEE 0,007–0,015 s; F0/V0/Pmax/DRF r 0,974–0,999 vs radar; PLOS ONE 2025 (fútbol): F0, V0 y pendiente difieren del radar | toques en el fotograma del paso por cada marca + `MotionModel` (Samozino 2016 con rozamiento del aire) | coincide en método; la precisión depende del toque y de los fps |
| Nordics | Soga 2023 (IJSPT, «Nordic Angle»); Dias 2025 (My Jump Lab 4.0 vs isocinético; resultados no recuperados) | ángulo de 3 marcadores y torque con masa efectiva 0,65·m | coincide |
| S-MAS | Bramah 2024, AJSM (fiabilidad inter/intra evaluador, 136 jugadores); BJSM 2025 (prospectivo) | 12 preguntas sí/no, 0–12 | coincide |
| Deceleración | Harper et al. (tests ADA de 5/10/20 m; fiabilidad buena-excelente con radar) | 8 marcas con paralaje corregido, deceleración máxima por tramos | adaptación con cámara, sin validación localizada |
| My ROM | validación citada en la tienda (J Sports Sci) no localizada en esta búsqueda | orientación por acelerómetro + magnetómetro | sin confirmar |

Fuentes consultadas (solo lectura): PMC9465756 (Bishop 2022), PMC11679296 (Sensors 2024), abacus.universidadeuropea.com (Runmatic 2017,
MySprint 2017), PMC12136470 (MySprint vs GPS/radar 2025), PubMed 38015739 (JSCR 2023), PubMed 28097928 (PowerLift 2018),
eurjhm.com/599 (My Lift 1RM), ijspt.scholasticahq.com/83936 (Nordic Angle 2023), salford-repository 2454268 (S-MAS 2024), PMC11812162
(deceleración 2024), peerj.com/14558 (Jumpo 2 / MyJump 2), journals.plos.org 10.1371/journal.pone.0313919 (Renner 2024, ya en
`MERCADO_FIABILIDAD.md`).

## 9. Brechas frente a KPKN

Leídos `VIDEO_TECHNIQUE_V1.md`, `BANCO_COMPLETO.md`, `INFORME_CONFIANZA_20261020.md`, `MERCADO_FIABILIDAD.md` y `DIFERENCIADORES.md`.

| Función o métrica de My Jump Lab | ¿La tiene KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| Salto vertical automático: tiempo de vuelo, altura g·t²/8, fuerza/velocidad/potencia de Samozino, RSI, RSImod, rigidez, asimetrías unilaterales, saltos repetidos | No (KPKN no analiza saltos) | Perfil `JUMP` nuevo: despegue y aterrizaje por los puntos de pies (talón/punta, 29–32) con histéresis y la aceleración de la cadera como respaldo; grabación a 60–240 fps; carga y hp0 del perfil del atleta; ecuaciones de Samozino para F, v, P y el perfil F-V con pendiente óptima. Con cuerpo entero se puede dar además el desplazamiento de la cabeza como contraste (lo que hace AI Jump) | MediaPipe (ya), CameraX high-speed | Alta | Medio |
| Grabación en cámara lenta (120–240 fps, `HighSpeedVideoSessionConfig`) | No (60 fps) | Añadir el modo high-speed al grabador (CameraX 1.5.3 ya previsto) solo para pruebas de saltos y esprint; conservar PTS reales y `captureFps` | CameraX 1.5 | Alta (para saltos) | Bajo-medio |
| Feedback en vivo: voz con la velocidad de cada repetición, alerta sonora de pérdida de velocidad, velocidad objetivo | Fase 2 (contador con voz) | En el contador en vivo, al cerrar cada repetición con disco calibrado, hablar MCV; umbral de VL y velocidad objetivo en ajustes; sonido corto | Android TTS (ya en KPKN) | Media | Medio |
| 1RM diario con una carga («Quick») y método de dos puntos | Parcial (perfil carga-velocidad con ≥ 3 cargas) | Guardar pendiente e intersección del último perfil por configuración; estimar 1RM de una serie con la MCV mejor y marcar «≈»; permitir 2 cargas (ligera y pesada) con aviso de fiabilidad | — | Media | Bajo |
| Tabla de v1RM por ejercicio editable (banca 0,17; sentadilla 0,32; peso muerto 0,30; militar 0,18; dominada 0,30; hip thrust 0,34) | Parcial (MVT fijos: 0,30/0,15/0,17/0,19/0,50) | Exponer la MVT por configuración en ajustes avanzados, con valores por defecto bibliográficos y override del usuario | — | Media | Bajo |
| Detector de disco por CNN (EfficientDet-Lite, 512 px) que evita la semilla manual | No (Hough + NCC + Kalman con confirmación del usuario) | Entrenar un detector propio de una clase con los fotogramas del banco (etiquetas de disco de VT10), exportarlo a TFLite y usarlo solo para **proponer** la semilla; mantener el Hough/Kåsa para el radio y la escala | MediaPipe Model Maker, LiteRT | Media | Alto |
| Modo manual universal: marcar inicio y fin de la fase concéntrica y teclear el ROM | No | «Modo manual» en la pantalla de análisis para ejercicios `Unsupported`: dos marcas de tiempo + ROM → MCV, con «≈» y sin perfil; útil para poleas y máquinas | — | Media | Bajo |
| Esprint: parciales, perfil F-V horizontal (F0, V0, Pmax, RFmax, DRF), curvo, deceleración, COD | No | Módulo aparte: detección automática del paso de la cadera por marcas (líneas verticales en la imagen) con la pose; modelo de Samozino 2016 (ver §4.6); corrección de paralaje de conos | — | Media | Alto |
| Mecánica de carrera (contacto, vuelo, frecuencia, oscilación, F_max, rigidez, asimetrías) | No | Contactos y despegues por los pies de la pose a 60 fps + ecuaciones de Morin 2005 con longitud de pierna y velocidad | — | Media | Medio |
| Kinograma automático y S-MAS | No | Elegir las 5 fases por eventos de la pose (toe-off, rodilla alta, contacto) y dibujar segmentos; el S-MAS como lista de comprobación | — | Baja-media | Medio |
| Ángulo de breakpoint y torque en nórdicos | No | Perfil `NORDIC`: ángulo cadera-rodilla-tobillo mínimo antes de la caída (velocidad del tronco), torque con masa efectiva | — | Baja | Bajo |
| Semáforo de readiness por z-score del histórico (1 DE, 1,5 DE) | Parcial (AUGE) | Aplicar la misma regla a la altura de salto y a la MCV con carga de referencia | — | Baja | Bajo |
| Medición de fps reales y aviso de dispositivo lento; *media performance class* | Parcial (sonda GPU, térmica, stride) | Medir fps efectivos por ventana y marcar `LOW_FPS` también en vivo; consultar `DevicePerformance` para elegir modelo lite/full | androidx.core-performance | Baja | Bajo |
| Calculadora de paralaje de conos | No | Trivial, solo si se hace el módulo de esprint | — | Baja | Bajo |
| My ROM por sensores, Force data por acelerómetro | No | No recomendable (fuera del núcleo de vídeo; ruido) | — | Baja | — |

**Lo que KPKN hace mejor que My Jump Lab:**
- Pose completa (33 puntos + «world») en todos los ejercicios con perfiles, frente a pose solo en AI Jump (orejas, tobillos, silueta).
- Segmentación de repeticiones con confianza, abstención y clases FULL/PARTIAL/FAILED, fases y TUT; My Jump Lab siempre devuelve
  número, sin confianza y con reglas de amplitud fijas en centímetros.
- Disco: Hough de gradiente + Kåsa + Kalman + NCC con radio mediano de calibración, elipse (a/b) y paralaje tratados; My Jump Lab usa
  una caja del detector una vez y plantilla fija.
- Tiempo: PTS reales, VFR, cámara lenta y 60 fps en sesión; My Jump Lab asume fps constante y, en el análisis de vídeo del VBT,
  tiempos = índice/fps.
- Perspectiva: clasificador de vista, aviso de inclinación por gravedad, elipse del disco; My Jump Lab nada.
- Honestidad: «≈», m/s solo con disco calibrado, 1RM solo en tendencias; privacidad: el vídeo no sale del teléfono (My Jump Lab
  sincroniza con Firestore y usa Crashlytics).
- Decodificación en GL a luma reducida y análisis en segundo plano con WorkManager; My Jump Lab convierte cada fotograma a Bitmap
  ARGB en CPU y analiza en primer plano.

## 10. Índice de evidencias
Rutas dentro del árbol descompilado `C:\kw\re\myjumplab\jadx\com\my\jump\lab\` (borrado al terminar, reproducible con los scripts de
`C:\kw\re\_scripts\myjumplab\`: `extract_xapk.py`, `apk_inventory.py`, `extract_selected.py`, `dump_resources.py`, `dex_classes.py`,
`dex_strings.py`, `tflite_info.py`, `DumpApp.java` + `run_dump.ps1`).

| Tema | Evidencia |
|---|---|
| Catálogo de pruebas | `assets/tests_configuration.json`; `refactor/domain/model/types/CalculationType` |
| Pose, silueta, métricas de persona | `presentation/utils/PoseLandmarkerHelper.java` (`setupPoseLandmarker`, `fallbackToCpu`, `runCalibrationPass`, `extractSilhouette`, constantes `MASK_THRESHOLD = 0.5f`, `MIN_ROW_PIXELS = 3`, `MASK_FAILURE_LIMIT = 10`); `EarMidpointMetricsProvider.metrics`; `PoseLandmarkIndex`; `Silhouette.isComplete` |
| AI Jump | `presentation/ui/tests/myjump/MyJumpAutoActivity.java` (`startCamera` con `CONTROL_AE_TARGET_FPS_RANGE 30–30`, `tryLockReference` con `stabilizationFrames = 10`/`stabilizationTolerance = 0.02`, `computeAndShowResults`, `analyzeVideo`, `minAcceptableFps = 25`); `presentation/utils/JumpTracker.java` (`addFrame`, `checkForPeak`, `neighborhoodForFps`); `CalibrationProfile` (0,9987; −0,0634; 0,09) |
| Detector y tracker del disco | `presentation/utils/ObjectDetectorHelper.java` (`MODEL_NAME`, umbral 0,5, `maxResults 1`); `PlateTracker.java` (`MIN_SCORE = 0.6d`, `search`, `relocate`, `toUprightGray`); `PlateDetection` |
| VBT automático | `presentation/ui/tests/mylift/MyLiftAutoFragment.java` (`onResults$lambda$31`, `MIN_REP_AMPLITUDE_CM = 5.0d`, `REST_BAND_CM = 3.0d`, `iosReps`, `analyzeVideo$lambda$47`, `speakVelocity`, `playVelocityLossSound`); `MyLiftAutoActivity.java` (`calculateResults`, `updateLoadResults`, diálogo de fps y de diámetro); `presentation/utils/IosRepDetector.java` (`detect`); `LowPassFilterSignal.update`; `MyLiftAutoViewModel` (arreglos `xCorrectedArray`/`lowPassXArray` sin uso) |
| VBT manual y 1RM | `MyLiftActivity.calculateResults` (`limbMass = 0.114·m`, `finalPushoffAccel`, `throw`); `MyLiftSummaryFragment.updateResult` (`slopeRM`, `interceptRM`); `domain/usecases/ReadMyLiftTemplates.java` (v1RM por defecto); `presentation/utils/Utils.calculateLinearRegression` |
| Saltos manuales y F-V | `MyJumpActivity.calculateResults` (1,22625; 0,90043/0,14319; rigidez con 1,5707963 y 9,8696; pendiente óptima; `RSI10_5`); `MyJumpSummaryFragment` (altura teórica, déficits); `MyJumpHorizontalActivity.calculateResults`; `retrieveFrameRate` («frame-rate» de `MediaExtractor`); `SlowMotionCameraActivity` (`MAX_FPS = 240`) |
| Runmatic | `run/RunningActivity.calculateResults` (1,57; 9,8596; ΔL; rigidez; duty factor); `RunningMarkersActivity` (ángulos de apoyo, tibia, pronación, pelvis); `SprintingActivity.calculateResults` (deceleración, lineal, curvo; `velocityAtDesiredDistance`); `SprintingMarkersActivity.nextStep$lambda$43$lambda$42` (ángulos del kinograma); `RedirectionActivity.calculateResults` (`deficit = time − 2·ten`); `domain/model/tests/Test.getTestDistance` (25; 60,62; 10) |
| Modelo de esprint | `defpackage/MotionModel.java` (`positionModel`, `objectiveFunction`, `optimizeGRG`, `airDensity`, `airFrictionCoefficient` con `Math.pow(…, 2)`, `linearRegression`, `calculateProfile`; arranque 8,0 y 1,0; paso 0,01 s; RFmax con t > 0,5) |
| Nordics | `nordics/BiomechanicsCalculator.java` (0,65; `calculateFacingRight/Left`, `calculateAsymmetry`); `NordicsActivity` (versión heredada) |
| Readiness, ROM, fuerza | `readiness/ReadinessActivity` (media de 4), `ReadinessSummaryFragment.showAdviseAlert` (umbrales `orangeT`/`redT`); `myrom/MyRomCountdownFragment.updateOrientationAngles`; `forcedata/ForceDataActivity` (sensores 1 y 9, `alpha`, `systemMass`) |
| Paralaje | `parallaxcalculator/ParallaxCalculatorActivity.calculate` (0,5 m / 0,546805 yd) |
| Exportar y compartir | `menu/ExportDataFragment` (`.csv`, cabeceras `EXPORT_*`); `presentation/utils/BitmapUtils` (`shareImage`, `shareRunImages`, `mergeBitmaps`, `takeScreenshotWithPixelCopy`); `OverlayView.draw` (sin dibujo efectivo) |
| Ajustes | `data/GlobalSettings.java` (`plateSize 0.45f`, `defaultV1 10f`, `defaultTargetVelocity 1f`, `orangeT 1f`, `redT 1.5f`, `hz 200`, `countdown`) |
| Datos y nube | `data/RoomDB_Impl` (tablas), `data/firebase/FirebaseDataSource`, `data/sync/SmartSyncManager`, `SyncWorker`, `DataRestoreManager`; `whatsnew/WhatsNewCatalog` |
| Nativo | `C:/kw/re/evidencias/myjumplab-re-20261020/native_libs.txt` (versión de OpenCV, módulos JNI, grafos de MediaPipe, NEEDED); `mediapipe_tasks_jni_exports.txt` |
| Modelos | `C:/kw/re/evidencias/myjumplab-re-20261020/models_info.txt` |
| Manifiesto y cadenas | `C:/kw/re/evidencias/myjumplab-re-20261020/resources/manifest.json`, `strings.txt` |

Sin confirmar: la justificación del factor (1 − 5/fps); el origen de los coeficientes 0,90043/0,14319; el «×10» del impulso; el
parámetro «fricción» del modo manual; el área frontal al cuadrado en el rozamiento del esprint; la versión exacta de MediaPipe y de
ExoPlayer; si `EXPORT_TRACKING` se usa en alguna ruta; la equivalencia entre el AI Jump de Android (30 fps) y el validado en iOS
(60 Hz).
