# Ochy – AI Gait Analysis 19.3.9 (Android) · ingeniería inversa estática

Fecha: 2026-10-20. Encargo: brief `docs/video-technique/briefs/RE_COMUN.md` (D50 de `DIFERENCIADORES.md`). Trabajo en sala limpia:
nada del APK se ejecutó ni se instaló; no se copió código descompilado ni se extrajeron modelos ni recursos. Las descripciones
son con palabras propias; como evidencia solo van nombres de clase, de método, de endpoint o de campo. Lo que no se pudo verificar
lleva «sin confirmar».

Herramientas: REA 6.5.0 (confirmado; `analyze`, `decompile`, `recover-javascript-sources` sirven para JS, pero el bundle es
**bytecode Hermes**, no JS legible, así que se usó un lector propio). Scripts Python con `python -I` en `C:\kw\re\_scripts\ochy\`
(`safe_extract.py`, `list_zip.py`, `dex_classes.py`, `hbc_parse.py` = lector estático del formato HBC v96, `str_grep.py` = búsqueda
con redacción). LLVM del NDK 27 (`llvm-readelf`, `llvm-strings`) para lo nativo. **No se necesitaron herramientas pesadas** (JADX
completo ni Ghidra): la lógica de la app vive en el bundle Hermes, que se analiza con el lector liviano; por eso no se tomó el
candado `pesado.lock`. Pruebas pequeñas (4,4 MB) en `C:\kw\re\evidencias\ochy-re-20261020\`.

---

## 1. Identidad

| Dato | Valor |
|---|---|
| Paquete · versión | `fr.ochy.app` · 19.3.9 (versionCode 210031647) · minSdk 26 · targetSdk 36 · compileSdk 36 |
| Archivo | `Ochy+-+AI+Gait+Analysis_19.3.9_APKPure.xapk`, 138 731 840 B, SHA-256 `b3a582d7a63a11998abe6d0a5fe95e3db29f201f9466738cc7a88454492d8f84` |
| APK base | `fr.ochy.app.apk`, 75 659 768 B, `f51b83e2acf93f3d11381f4754decfa1cd83641e0b61c30d8a8c652a214915d7` |
| Split ABI | `config.arm64_v8a.apk`, 62 538 024 B, `7868fc54586b77757046038d6b9bcc62c9656d5acfd5ddd3c21e350726f16cd8` |
| Split idioma | `config.en.apk`, 135 577 B, `afe7a8ac8557bd99c64fc74d90993f6c5af9b7fb0409701a0384f6e3622b30d7` |
| Split densidad | `config.mdpi.apk`, 167 512 B, `0a4825dce3291f2d3f7037b0a9dca3379507bb86c7899eb8ce51ddcd8db6c3a6` |
| Manifiesto APKPure | `manifest.json`, `279c68ab934059c00190f22bd45a86706c720b6573ce81ad66b736e6f3c02f1e` |
| Icono | `icon.png`, `f89e18da5d34f051c55b7877e82af62c9eb858b0ce807df57a1c4761f059d227` |
| Propietario Expo | `owner: victorochy`, EAS projectId `493d0c42-2153-4f19-8583-32351cc152cc` |

**Arquitectura. React Native 0.81.4 sobre Expo SDK 54.0.0, con la nueva arquitectura (Fabric/TurboModules) y el motor
Hermes.** Todo el código de producto es JavaScript compilado a **bytecode Hermes v96** en `assets/index.android.bundle`
(15 380 896 B: 49 458 funciones, 71 046 cadenas). El lado Java/Kotlin casi no tiene lógica propia: de ~48 581 clases DEX
solo 5 son de `fr.ochy.app` (`MainActivity`, `MainApplication`, un servicio de Intercom y clases anidadas); el resto son
React Native, Expo y SDKs. React renderer 19.1.0.

**ABIs y nativo** (solo `arm64-v8a` en este XAPK; 51 bibliotecas). Lo relevante:

| Biblioteca | Tamaño | Qué es | Licencia |
|---|---|---|---|
| `librnskia.so` | 9,9 MB | Skia (`@shopify/react-native-skia`): dibuja el esqueleto y las capas sobre el vídeo | BSD-3 (Skia) / MIT (wrapper) |
| `libavcodec/avformat/avfilter/avutil/swscale/swresample/avdevice.so` + `libffmpegkit.so` | ~14 MB | **FFmpeg** vía `ffmpeg-kit` (`com.arthenica`): recorte y recodificación del vídeo. La presencia de `libavdevice` apunta a un build **GPL** de ffmpeg-kit — licencia a vigilar | FFmpeg (LGPL/**GPL** según build) |
| `libVisionCamera.so` (+ `libNitroModules/NitroImage.so`) | ~6 MB | `react-native-vision-camera` (mrousavy) con Nitro: **graba** el vídeo. No carga ningún modelo | MIT |
| `libreactnative.so`, `libhermes.so`, `libjsi.so`, `libworklets.so`, `libreanimated.so` | ~12 MB | runtime RN + Hermes + Reanimated/Worklets | MIT |
| `libpdfium.so`, `libpdfiumandroid.so` | ~5 MB | render de PDF (`react-native-pdf`) para ver informes | Apache-2.0/BSD |
| `libcrashlytics*.so`, `libmmkv.so`, `libNitroMmkv.so`, `libavif/gif/webp` | — | Crashlytics, almacenamiento MMKV, decodificadores de imagen | Apache/BSD |

**No hay ninguna biblioteca de visión por computador ni de ML en el dispositivo:** 0 coincidencias de OpenCV, MediaPipe,
TensorFlow/TFLite, LiteRT, MoveNet, BlazePose, ONNX o NCNN, ni como `.so` nativo ni como cadena en el bundle.

**Modelos de ML: NINGUNO en el APK.** No hay `.tflite`, `.task`, `.onnx`, `.pt`, `.mlmodel` ni `.pb`. (El único `.bin` es
`DebugProbesKt.bin` de kotlinx-coroutines.) **La estimación de pose y el cálculo de métricas ocurren en el servidor de Ochy**
(ver §4 y §7).

**Bibliotecas de terceros relevantes y licencia** (versiones de `META-INF/*.version` y del bundle; lista completa en
`evidencias/.../library_versions.txt`): React Native 0.81.4 (MIT); Expo SDK 54 (MIT); `react-native-vision-camera` + Nitro
(MIT); `@shopify/react-native-skia` (MIT/BSD); `react-native-reanimated`/`worklets` (MIT); `react-native-video-trim` +
`ffmpeg-kit` (**GPL a vigilar**); `react-native-view-shot` (MIT); `react-native-pdf`/pdfium (BSD/Apache); CameraX 1.7.0-alpha03,
Compose 1.9.5, Material3 1.4.0, Media3 (Apache-2.0); Firebase (Auth, Firestore, Storage, Functions, Messaging, Crashlytics,
Remote Config); **Purchasely** (paywall/suscripciones); **Intercom** (soporte in-app); **Mixpanel**, **Adjust**, **Facebook SDK**,
Google Sign-In, **Ably** (`io.ably.lib`, tiempo real; rol exacto sin confirmar, probablemente leaderboard del evento ASICS);
AAChart no aparece (los gráficos se dibujan con Skia/Victory). ZXing para QR no consta. Protección Play Integrity `com.pairip`.

**Permisos y sensores.** `CAMERA`, `RECORD_AUDIO` (para «voice reviews», no para pose), `MODIFY_AUDIO_SETTINGS`, `INTERNET`,
`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `READ/WRITE_EXTERNAL_STORAGE`, `DOWNLOAD_WITHOUT_NOTIFICATION`, `POST_NOTIFICATIONS`,
`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (recordatorios de rutina), `VIBRATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
`FOREGROUND_SERVICE`, `BILLING`, C2DM push, `AD_ID` + ACCESS_ADSERVICES_* (atribución), permisos de badge de varios launchers.
**Sin acelerómetro/giroscopio propios, sin BLE, sin GPS con permiso, sin Health Connect.** Deep links `links.ochy.io`,
`ochy.go.link`, `ochy.page.link`, esquemas `ochyapp://` y `ochy://app`.

---

## 2. Inventario completo de funciones

Inventario tomado de los 155 nombres de pantalla y de los eventos de Mixpanel (`evidencias/.../screens.txt`). La app tiene
**cuatro perfiles de usuario**: atleta, coach (entrenador/clínico), tienda minorista (retail, con recomendación de calzado
ASICS) y profesional. Casi todo el valor está tras el paywall de Purchasely.

### 2.1 Onboarding y cuenta
- **Onboarding largo y ramificado**: selección de tipo de usuario (atleta/coach/retail/profesional), género, nivel de carrera
  (principiante→élite), tipología, fecha de carrera objetivo (p. ej. maratón de París), notificaciones, creación de perfil.
  Incluye un **«juego» educativo** (adivina por qué la pisada de Perrine/Khaldon es deficiente: foot landing, back leg, overstride)
  y una pantalla de «cuánto cuesta un análisis de laboratorio» (5/200/400 $) como gancho de precio.
- **Cuenta**: e-mail+contraseña, Google y Apple Sign-In; OTP por e-mail con «dispositivo de confianza»
  (`/auth/email-otp/{send,verify,trust-check,clear}`); recuperación de contraseña; política de privacidad y términos.
- **Perfil del atleta**: altura, peso, género, nivel; foto. **La altura se usa como escala** de las distancias (ver §4).

### 2.2 Grabar / subir vídeo
- **Botón central** con dos vías: grabar en la app (VisionCamera) o importar de galería.
- **Selector de tipo de análisis** (`ANALYSIS_TYPE_SELECTION`): vista lateral o trasera; en cinta o exterior; carrera o marcha.
- **Selector de velocidad** (`SelectRunningPace`, `Upload Speed Selector`): el usuario introduce su ritmo/velocidad y la unidad
  (km/h, mi/h, min/km) — dato necesario para la escala temporal/espacial que la cámara no da.
- **Validación del vídeo** en cliente (`Video Validation Screen`): orientación (vertical/horizontal), formato, **sin cámara lenta**;
  recomienda ≥720p y 60 fps; recorte con `react-native-video-trim` (ffmpeg) a **≤10 s**; tamaño máx. 40 MB.
- **Guía de captura** por cada combinación (best practices treadmill/overground × side/back; walking): «cámara estable,
  perfectamente vertical, a la altura de la cadera, a ~2 m del corredor, sin seguirlo».

### 2.3 Análisis (resultado)
- **Pantalla de espera** (`Analysis Waiting`): «Nuestra IA procesa tu vídeo, te redirigiremos cuando termine»; si tarda, sigue en
  segundo plano. Sondeo de estado (`UPLOAD_JOB_STATUS`, `_pollingLoop`, `pollTick`, `POLL_MS`).
- **Resumen / overview / detalle**: puntuación global (`overall_score`), reproductor del vídeo **ya analizado** (descargado del
  servidor) con capas de esqueleto, ángulos articulares, métricas espacio-temporales, estilo de carrera, pisada y overstride.
- **Reproductor con capas** (Skia): esqueleto superpuesto (lado izquierdo naranja, derecho azul), ángulos sobre el vídeo,
  regla/cuadrícula (`RULER_MARKER`), textos de capa distintos para side/back view, herramienta de **pincel** para que el coach
  anote (`brushArea`); avance por fotograma y velocidad de reproducción (`setPlaybackSpeed`).
- **Joint Angles**: gráfico por articulación (trunk, hip, knee, ankle, arm, head), bilateral, con explicación y marca de pregunta.
- **Comparar** dos análisis lado a lado (`Compare Analysis`).
- **Tags**: etiquetar análisis para comparar y seguir el progreso.
- **Estadísticas** (`Statistics`): historial de métricas en el tiempo.

### 2.4 Entrenamiento y recomendaciones
- **Plan de ejercicios / drills** personalizados según el último análisis (`ExercisePlanScreen`, `DrillsHomeScreen`,
  `PersonalizedExercisesScreen`, `DRILLS_LIST`), con vídeos de ejercicios (Firebase Storage); **rutina de refuerzo** con
  recordatorios programados (alarmas exactas). Drills genéricos hasta el primer análisis.
- **Calendario de carrera** (`Running Calendar`) y recordatorios a cuenta atrás de un objetivo (maratón de París).
- **Recomendación de calzado** (`Shoe Recommendation`, co-marca **ASICS**): a partir del análisis sugiere horma; campos
  `comfort_heel_cushioning`, `comfort_forefoot_cushioning`, `forefoot_flexibility`, `barefoot`, `heel_to_toe_drop`. Consume
  «créditos»; `/shoe-recommendations`, colecciones `SHOE_RECOMMENDATION_*`, `BRANDED_SHOES_COLLECTION`.
- **Noticias / artículos** (Intercom + blog ochy.io), FAQ.

### 2.5 Coach, clínica y retail
- Gestión de atletas/pacientes/clientes, compartir análisis con el atleta (`/share`), logo de marca de la organización,
  **waiver de responsabilidad** firmado (`/organization/liability-waiver/upload-signed`), créditos de organización, contacto con
  ventas, planes personalizados. Retail: flujo de recomendación de calzado en tienda y leaderboard de evento (ASICS PM).

### 2.6 Compartir / exportar
- **PDF** del análisis generado en el servidor (`/analysis/generate_pdf`), visto con pdfium y compartido.
- **Vídeo analizado** descargado (`/output/encoded_analyzed_*.mp4`) y compartido.
- **Tarjeta de compartir** con esqueleto y co-marca ASICS (`AsicsShareSkeletonOverlay`, `ASICS_SHARE_CARD_WIDTH/HEIGHT`);
  captura de pantalla con `react-native-view-shot`; compartir a redes/Facebook.

### 2.7 Suscripción
- Paywall **Purchasely** (prueba gratis de 7 días, planes mensual/anual; códigos promocionales `/redeem`, `/referral`,
  `/promotion-code`). Sin compra, las métricas se muestran **difuminadas** (claves `*_blurred_*`, ver §3).

---

## 3. Catálogo de métricas

**Origen de las métricas: el servidor.** El cliente recibe un objeto de análisis con los campos en `snake_case` que se listan
abajo (confirmados en el bundle, `evidencias/.../metric_fields.txt`), más la pose por fotograma (`keypoints`, `points`,
`positions`, `frames`) para dibujar. Las **unidades van codificadas en el propio nombre del campo** (`_meter`, `_second`,
`_percentage`, `_steps_per_min`). Las fórmulas exactas y los umbrales residen en el backend y **no son recuperables del APK**
(sin confirmar); las definiciones que sí están en el cliente son textos educativos con su referencia científica.

### 3.1 Espacio-temporales (vista lateral, carrera y marcha)
| Métrica (campo) | Definición (texto del cliente) · unidad |
|---|---|
| Cadencia / frecuencia de paso — `step_frequency_steps_per_min` (+ `left_`/`right_`) | pasos por minuto; «cada corredor corre a una frecuencia algo menor que la óptima (Van Oeveren 2021)» |
| Longitud de paso — `step_length_meter` (+ L/R) | distancia entre el contacto de un pie y el del contrario; «≈ mitad de la zancada; alejarse de la longitud natural sube el coste metabólico (Cavanagh 1982)» · m |
| Longitud de zancada — `stride` (derivada), ancho de paso — `step_width` | — · m |
| Tiempo de contacto con el suelo — `ground_contact_time_second` | apoyo del pie · s |
| Tiempo de vuelo — `time_of_flight_second` | ambos pies en el aire; «los corredores de alto nivel (<32 min/10 km) tienen ~11 % más vuelo que los ocasionales (>38 min) (Preece 2019)» · s |
| Apoyo (stance) — `stance_time` / `stance_times_percentage` / `stance_second` (L/R) | fase de apoyo · s y % del ciclo |
| Balanceo (swing) — `swing_time` / `swing_times_percentage` (L/R) | fase de vuelo de la pierna · s y % |
| Soporte simple — `single_support_second` / `single_support_percentage` (L/R) | un pie en el suelo · s y % |
| Soporte doble — `double_support_second` / `double_support_percentage` / `all_double_support_percentage` | ambos pies (marcha) · s y % |
| Duty factor — `duty_factor_percent` | fracción del ciclo en apoyo · % |
| Oscilación vertical — `vertical_oscillation_meter` | sube-baja del cuerpo · m |
| Altura mínima del pie en balanceo — `foot_clearance_meter` (L/R) | «distancia vertical mínima entre la punta y el suelo en la fase de balanceo» · m |
| Velocidad — `speed_meter_per_second` | «calculada con algoritmos basados en la morfología del atleta, sobre todo su altura» · m/s (y km/h, min/km) |
| Pasos individuales — `individual_steps`, `steps` | serie por paso (base de las medias, DE y simetrías L/R) |

### 3.2 Ángulos articulares (vista lateral)
- `joint_angles` con, por lado, **tronco, cadera, rodilla, tobillo, brazo y cabeza**; además `trunk_mean_angle`,
  `arm_mean_angle`, `head_mean_angle`, `back_leg_mean_angle`, `front_leg_mean_angle`, `left/right_mean_angle` (ángulo medio por
  segmento sobre el ciclo). «Naranja = izquierda, azul = derecha» (texto `20028`).
- Eventos nombrados: **Back knee angle at foot landing**, **peak knee flexion**, **toe off**, **heel off/strike**. El cliente
  tiene helpers geométricos (`getAngleInDegrees`, `radiansToDegrees`, Gram-Schmidt/`gramSchmidtAlgorithm`, `VELOCITY_THRESHOLD`,
  `VELOCITY_EPS`) que probablemente usa para dibujar y para localizar eventos en el deslizador, no para la medición principal
  (sin confirmar).
- Aviso de abstención: «No hay suficientes ciclos de carrera por pierna para calcular los ángulos articulares de ese lado»
  (`InsufficientDataJointAngles`).

### 3.3 Vista trasera (back view) — alineación y pisada
- **Caída pélvica** — `pelvic_drop` (`pelvic_left`/`pelvic_right`): «diferencia de altura entre cadera derecha e izquierda
  (Pipkin 2016); por cada 1° más, la probabilidad de lesión sube un 80 % (Bramah 2018); se mide en la fase de apoyo».
- **Aducción de rodilla** — `knee_adduction` (L/R) y **aducción de cadera** — `hip_adduction` (knee valgus/«knee adduction»).
- **Pronación/supinación** — `pronation`/`supination` (L/R), `foot_posture`: movimiento del retropié; «la pronación excesiva se
  asocia con economía de carrera y patologías (Tartaruga 2005; Genova & Gross 2000)». La pronación **solo** se calcula en vista
  trasera (texto `20916`: «sí, pero solo para pronación; selecciona Vista Trasera»).
- **Heel whips** — `heel_whips` (latigazo del talón).

### 3.4 Clasificaciones
- **Patrón de pisada** — `foot_strike_pattern` / `foot_landing`: **rearfoot (talón) / midfoot (medio) / forefoot (antepié)**
  (`getFootStrikePattern`, `normalizeFootStrikePattern`).
- **Overstride** — `overstride`: estados `overstride_okay` / `overstride_bad`; «con el pie 5°–10° por delante del centro de
  gravedad los músculos trabajan más; puede perderse hasta un 10 % de velocidad».
- **Estilo de carrera** — 5 estilos tipo *pinwheel* (frecuencia de paso vs. fase de apoyo, modelo de Van Oeveren 2021):
  (1) hopping — frecuencia alta, apoyo corto, salto vertical; (2) propulsión horizontal — frecuencia baja, apoyo moderado;
  (3) equilibrado — frecuencia media, apoyo/balanceo balanceados, alta flexión de rodilla al contacto; (4) apoyo largo — ideal a
  baja velocidad, menos impacto vertical; (5) redistribución elástica — frecuencia media, apoyo corto, balanceo largo.
- **Heel-to-toe drop** de la zapatilla — `heel_to_toe_drop`: «diferencia de altura talón-antepié de la zapatilla, en mm» (dato de
  calzado para la recomendación, no biomecánica).

### 3.5 Puntuación (scoring)
- **Puntuación global** `overall_score` y por segmento, en **tres estados good/okay/bad** (`evidencias/.../classification_keys.txt`):
  `arms`, `back` (tronco), `backleg`, `frontleg`, `head`, `knee_adduction`, `pelvic`, `pronation`, `overstride`, `range` (ROM del
  ciclo de pierna). Cada uno tiene una variante `*_blurred_*` = la tarjeta difuminada que se muestra **sin suscripción**. Los
  umbrales numéricos que separan good/okay/bad están en el servidor (sin confirmar).

---

## 4. Algoritmos

### 4.1 Dónde corre el análisis
**Flujo (confirmado por endpoints y textos):** el usuario graba o importa → valida y recorta localmente (≤10 s, sin cámara
lenta) → la app pide una URL de subida (`/analysis/upload-url?analysis_type=`) → **sube el vídeo** (a Firebase Storage /
`/analysis/start`) → **el servidor estima la pose y calcula todas las métricas** → la app **sondea** el estado
(`UPLOAD_JOB_STATUS`) → descarga el objeto de análisis (métricas + pose por fotograma) y el **vídeo de salida ya dibujado**
(`/output/encoded_analyzed_*.mp4`). Nada de pose ni de métrica ocurre en el teléfono.

### 4.2 Modelo de pose
**Propietario y en la nube; desconocido desde el APK.** No hay modelo ni runtime de ML en el cliente (§1). El informe propio de
Ochy (E06 en `CANDIDATAS_Y_METRICAS.md`) compara su sistema con Qualisys y con MediaPipe, lo que sugiere que su motor **no es
MediaPipe estándar** sino propio. La app recibe los `keypoints` por fotograma (puntos propios: `LHIP/RHIP`, `LKNEE/RKNEE`,
`LANK/RANK`, `LTHEEL/RTHEEL` y `LBHEEL/RBHEEL` = talón anterior/posterior, `LFM1/RFM1` y `LFM5/RFM5` = cabezas del 1.º y 5.º
metatarsiano, `LCALF/RCALF`, `LFAKEPSIS/RFAKEPSIS` = espinas ilíacas, `HEAD`; grupos `SIDE_VIEW_SEGMENT`, `BACK_VIEW_SEGMENT`,
`BACK_VIEW_STANDALONE_JOINTS`). Son más puntos de pie de los que da MediaPipe (que solo tiene talón y punta), coherente con un
modelo entrenado para carrera.

### 4.3 Calibración y escala
- **Sin referencia física en la imagen.** La escala espacial viene de la **altura del atleta** (perfil) y de la **velocidad que el
  usuario introduce** (`SelectRunningPace`): «desarrollamos algoritmos basados en la morfología del atleta, sobre todo la altura».
  Las longitudes de paso/zancada en metros derivan de ahí, no de homografía ni de marcadores.
- **fps:** recomienda 60 fps; el usuario no introduce fps. Las métricas temporales se calculan en el servidor.

### 4.4 Perspectiva
**No corrige ángulos oblicuos.** Exige el encuadre de cada protocolo (lateral o trasero, cámara vertical a la altura de la cadera,
~2 m, estable, sin seguir al atleta). La vista trasera habilita pronación/caída pélvica/aducción; la lateral, los ángulos
sagitales y los tiempos. No hay homografía, elipse ni corrección por sensores (la app no usa acelerómetro). Ver §7.1.

### 4.5 Detección de eventos, suavizado, cámara lenta
En el servidor (no recuperable). En cliente solo: validación de formato, rechazo de cámara lenta, recorte, y helpers geométricos
para dibujar/ubicar eventos. Umbrales `VELOCITY_THRESHOLD`/`VELOCITY_EPS` presentes pero sin fórmula asociada legible.

### 4.6 Controles de calidad y avisos
- Validación del vídeo (orientación, no slow motion, ≥720p, 60 fps recomendado, ≤10 s, ≤40 MB).
- «El sistema de IA no detectó suficientes pasos: asegúrate de que la cámara está 100 % estable y grabaste pasos suficientes».
- «Lleva ropa ajustada para que la IA detecte bien las partes del cuerpo».
- Abstención por lado si faltan ciclos (`InsufficientDataJointAngles`); «error al calcular el estilo de carrera, reinténtalo».

---

## 5. Exportación y compartir
- **Vídeo con telemetría quemada: SÍ, pero renderizado en el servidor.** La app **no** compone el overlay en el vídeo; descarga el
  `encoded_analyzed_*.mp4` ya dibujado por el backend. En pantalla, el reproductor superpone esqueleto/ángulos/regla con Skia
  (capas activables, lado izq. naranja / der. azul), pero esa composición es en vivo sobre el reproductor, no un export.
- **PDF** del informe generado en el servidor (`/analysis/generate_pdf`), visto con pdfium y compartido.
- **Tarjeta de compartir** con esqueleto y **co-marca ASICS** (`AsicsShareSkeletonOverlay`, tamaño fijo de tarjeta); captura con
  `react-native-view-shot`; compartir a Facebook/redes.
- **No se encontró** export configurable a 9:16 con gráficos tipo telemetría F1, ni CSV de la traza cruda (sin confirmar que no
  exista por otra vía del servidor).

## 6. Datos e integraciones
- **Nube Firebase**: Auth (e-mail/Google/Apple + OTP), **Firestore** (usuarios, análisis, ejercicios, planes, recomendaciones de
  calzado, colecciones `*_COLLECTION`), **Storage** (vídeos de entrada y salida en `users/<uid>/analysis/<id>/...`), Functions,
  Messaging/push, Remote Config, Crashlytics. El emulador remapea a `10.0.2.2` (restos de desarrollo).
- **Backend propio** `https://api.ochy-prod.com` (`RN_API_URL`) con los endpoints de §2.
- **Analítica y marketing**: Mixpanel, Adjust (atribución), Facebook SDK, Ad ID + Privacy Sandbox. **Ably** (tiempo real; rol sin
  confirmar). **Intercom** (soporte). **Purchasely** (suscripciones). El texto de privacidad enumera datos personales recogidos
  (nombre, e-mail, vídeos): **no promete procesamiento local** — al contrario, el vídeo se sube a la nube.
- **Sin BLE, wearables, Health Connect ni sensores del teléfono.**

## 7. Las tres preguntas del usuario
1. **Perspectiva.** No corrige ángulos oblicuos. Obliga a grabar a 90° de perfil (ángulos sagitales y tiempos) o exactamente de
   espaldas (pronación, caída pélvica, aducción), con cámara vertical a la altura de la cadera, a ~2 m, estable. La escala no sale
   de la geometría de la imagen sino de la **altura del atleta** y de la **velocidad que teclea el usuario**. No hay homografía,
   elipse ni uso de sensores.
2. **Tracking híbrido.** No aplica: Ochy no sigue ningún objeto ni barra; es 100 % pose (modelo propio en la nube) para carrera y
   marcha. No hay relevo a otro objetivo.
3. **Vídeo con telemetría para redes.** Existe, pero lo **renderiza el servidor** (`encoded_analyzed_*.mp4`) y se comparte tal cual,
   además de una tarjeta co-marca ASICS y el PDF. La app no compone el overlay en el fichero; solo lo dibuja en su reproductor.

## 8. Contraste con los estudios publicados
- El único respaldo es el **informe propio de Ochy** (E06, `ai.ochy.io/.../Ochy_AI_Validation.pdf`, 9 participantes, 60 Hz, sagital,
  referencia Qualisys): MAE cadencia 1,91 pasos/min, contacto 0,030 s, balanceo 0,032 s; y compara MediaPipe (3,35; 0,036; 0,040).
  **No es revisión por pares** y **«swing time» (balanceo) no es tiempo de vuelo** — ojo al comparar con el `time_of_flight` de la
  app. Las definiciones in-app citan literatura real (Van Oeveren 2021 estilos/cadencia, Cavanagh 1982 longitud de paso, Preece
  2019 vuelo, Pipkin 2016 y Bramah 2018 caída pélvica, Willems/Tartaruga/Genova pronación), pero citar un estudio **no valida** la
  medición concreta de Ochy. Trátese como afirmación comercial salvo validación independiente.

## 9. Brechas frente a KPKN

Leídos `VIDEO_TECHNIQUE_V1.md` (KPKN: MediaPipe 33 puntos + 12 «world» en metros, **local**, «el vídeo nunca sale del teléfono»,
60 fps con PTS, clasificador de vista, elipse del disco), `BANCO_COMPLETO.md`, `INFORME_CONFIANZA_20261020.md`,
`MERCADO_FIABILIDAD.md`, `DIFERENCIADORES.md` (D50) y `CANDIDATAS_Y_METRICAS.md` (M14–M24 carrera). **KPKN no tiene módulo de
carrera; Ochy es la referencia más completa.**

| Función / métrica de Ochy | ¿La tiene KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| **Módulo de carrera lateral** (cadencia, longitud de paso/zancada, contacto, vuelo, apoyo/balanceo %, soporte doble, duty factor, oscilación vertical, foot clearance) | No | Perfil `RUN` nuevo: eventos de contacto/despegue por los pies de la pose (talón/punta 29–32) a 60–240 fps; cadencia, contacto y vuelo por PTS; longitud por geometría de suelo + velocidad introducida | MediaPipe (ya), CameraX high-speed | **Alta** | Media-alta |
| **Clasificación de pisada** rearfoot/midfoot/forefoot | No | Ángulo pie-suelo en el fotograma de contacto; umbrales propios validados con el banco | — (pose ya) | Alta | Media |
| **Overstride** (pie por delante del CoM al contacto) | No | Distancia horizontal pie-proyección de cadera/CoM al contacto, normalizada por talla; marcar «≈» | — | Alta | Media |
| **Ángulos articulares de carrera** (tronco, cadera, rodilla, tobillo, brazo, cabeza; peak knee flexion; back knee at landing) bilaterales | Base (ROM genérico, ángulos 2D/3D ya) | Reusar primitivas de ángulo de KPKN sobre los eventos del ciclo; serie por lado con naranja/azul | — | Alta | Media |
| **Vista trasera: caída pélvica, aducción de rodilla/cadera** | No | Perfil `RUN_BACK`: inclinación entre caderas y ángulo cadera-rodilla-tobillo frontal en apoyo; avisar baja confianza en 2D | — | Media | Media |
| **Pronación/supinación (retropié), heel whips** | No | Honesto: solo proxy 2D del ángulo del talón en vista trasera; **no** llamarlo pronación subtalar real (ver M21) | — | Baja-media | Media |
| **Estilo de carrera (5 estilos, modelo pinwheel)** | No | Clasificar por frecuencia de paso vs. % de apoyo (Van Oeveren 2021); presentar como mapa, no como veredicto | — | Media | Baja |
| **Scoring por segmento good/okay/bad + global** | Parcial (confianza/abstención, FULL/PARTIAL) | Umbrales por métrica validados con el banco; mostrar el fotograma que lo explica; «≈» donde toque | — | Media | Media |
| **Plan de ejercicios/drills personalizados + recordatorios** | No | Mapear cada hallazgo a drills de la biblioteca KPKN; rutina con recordatorios (ya hay servicio de avisos) | — (Android TTS/alarmas ya) | Media | Media |
| **Comparar dos análisis + estadísticas/tags en el tiempo** | Parcial | Historial por métrica y comparación lado a lado reutilizando PoseTrack v2 | — | Media | Baja-media |
| **Recomendación de calzado** | No | Fuera del núcleo; solo con acuerdo de marca | — | Baja | — |
| **Grabación 120–240 fps** (eventos cortos de carrera) | No (60 fps) | Modo high-speed en el grabador (CameraX 1.5/1.7) para carrera/saltos, conservando PTS | CameraX | Alta (carrera) | Bajo-medio |
| **Export de vídeo con overlay** | KPKN lo hará en el teléfono (D44) | KPKN gana: Media3 Transformer local, 9:16, sin subir el vídeo | Media3 (ya) | — | — |

**Lo que KPKN hace MEJOR que Ochy:**
- **Privacidad y coste**: todo en el teléfono, el vídeo **no sale del dispositivo**; Ochy **sube el vídeo** a la nube, estima pose y
  calcula métricas en servidor, y usa Mixpanel/Adjust/Facebook/AdID. Enorme diferenciador de confianza.
- **Sin conexión**: KPKN analiza offline; Ochy no funciona sin servidor.
- **Honestidad y trazabilidad**: KPKN asocia cada número a fotograma/evento, «≈», abstención, versión de algoritmo; Ochy devuelve
  cifras del servidor sin exponer fórmula ni incertidumbre, y su único respaldo es un informe propio.
- **Export local con telemetría** (D44): Media3 Transformer en el teléfono y 9:16; Ochy depende del render del servidor y su
  compartir es co-marca ASICS fija.
- **Cobertura multideporte**: KPKN cubre fuerza/disco, saltos (D48), VBT, etc.; Ochy es **solo carrera/marcha**.
- **Pose 33 puntos + «world» 3D reutilizable** en todos los ejercicios; Ochy tiene un modelo propio de carrera que no expone.

**No conviene copiar:** la dependencia de la nube para una tarea que cabe en el teléfono; pedir la velocidad al usuario como única
escala (asumir error y marcar «≈»); presentar «swing time» como tiempo de vuelo; vender «lab-grade» con un informe propio de 9
sujetos sin revisión por pares; y el build GPL de ffmpeg-kit (KPKN ya usa Media3, sin esa carga de licencia).

## 10. Índice de evidencias
Todo en `C:\kw\re\evidencias\ochy-re-20261020\` (4,4 MB; el árbol extraído en `C:\kw\re\ochy\` se borró al terminar, reproducible
con `C:\kw\re\_scripts\ochy\`: `safe_extract.py`, `list_zip.py`, `dex_classes.py`, `hbc_parse.py`, `str_grep.py`).

| Tema | Evidencia |
|---|---|
| Identidad, hashes XAPK e internos | `xapk_inventory.json`, `base_apk_inventory.json`, `arm64_apk_inventory.json` |
| Arquitectura RN/Hermes | `hbc_header.json` (version 96, 49 458 funciones, 71 046 cadenas), `hbc_functions.tsv`, `dex_classes.txt` (clases `fr.ochy.app.*`, `expo.modules.videoskeletonoverlay.*`, `com.mrousavy.camera.*`) |
| Nativo y licencias | `native_libs.txt` (ffmpeg-kit→avdevice, Skia, VisionCamera/Nitro; sin OpenCV/MediaPipe/TFLite), `library_versions.txt` |
| Esquema de métricas del servidor | `metric_fields.txt` (campos `*_meter/_second/_percentage/_steps_per_min`, L/R) |
| Clasificaciones y scoring | `classification_keys.txt` (good/okay/bad por segmento, variantes `_blurred_`) |
| Endpoints del backend | `api_endpoints.txt` (`/analysis/{start,upload-url,generate_pdf}`, `/output/encoded_analyzed_`, `/shoe-recommendations`, `/auth/email-otp/*`) |
| Definiciones y referencias científicas | `metric_definitions_en.txt` (Van Oeveren 2021, Cavanagh 1982, Preece 2019, Bramah 2018, Pipkin 2016) |
| Pantallas / funciones | `screens.txt` (155 pantallas y eventos) |

**Sin confirmar:** las fórmulas y umbrales exactos de cada métrica (viven en el servidor); el nombre/arquitectura del modelo de
pose propio; el rol concreto de Ably (probable leaderboard del evento ASICS); si el servidor ofrece CSV de la traza cruda; la
licencia exacta del build de ffmpeg-kit (GPL a vigilar); la equivalencia del «swing time» del informe E06 con el `time_of_flight`
de la app.
