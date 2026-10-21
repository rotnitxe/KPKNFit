# Yogger – Movement Analysis 6.1.0 (Android) · ingeniería inversa estática

Fecha: 2026-10-20. Encargo: brief `docs/video-technique/briefs/RE_COMUN.md`. Trabajo en sala limpia: nada del APK se ejecutó ni se
instaló; no se copió código ni se extrajeron modelos. Las descripciones son con palabras propias; como evidencia solo van nombres de
clases, métodos y constantes. Lo que no se pudo verificar lleva «sin confirmar».

Herramientas: REA 6.5.0 (`decode-android-resources`, apktool 3.0.3 para manifiesto y recursos), el JAR de JADX 0.7.1 de REA usado por
su API para volcar solo las clases `io.yogger.Yogger.*`, `defpackage.*` (paquete por defecto, donde viven las herramientas de ángulo) y
`com.google.mlkit.vision.pose.*`; 13 clases cuyo método principal no se pudo reconstruir se volvieron a volcar en modo SIMPLE. LLVM del
NDK 27 (`llvm-readelf`, `llvm-strings`) para lo nativo. Scripts propios con `python -I` en `C:\kw\re\_scripts\yogger\`. Las tres pasadas
de JADX se hicieron bajo el candado `C:\kw\re\pesado.lock`, con más de 6 GB libres, y duraron 23–37 s cada una. Pruebas pequeñas en
`C:/kw/re/evidencias/yogger-re-20261020/` (5,3 MB).

**Lo esencial, antes del detalle:**
- **Yogger 6.1.0 para Android no analiza saltos, carrera ni esprint.** No hay tiempo de vuelo, RSI, contacto con el suelo, cadencia ni
  zancada en el código ni en los textos (la «gait analysis» de su web solo aparece como entrada de blog). Tampoco hay barra, disco ni
  VBT. Es un **analizador de movilidad y de cribado funcional**: 14 pruebas guiadas, una «edad de movimiento» y un reproductor con
  herramientas de ángulo, trayectoria, dibujo y medida.
- El análisis es **local** (Google ML Kit Pose Detection «accurate», el BlazePose GHUM full), pero **los vídeos de las evaluaciones,
  los resultados y los perfiles se suben a Firebase**.
- Lo aprovechable para KPKN es el **modo de pruebas protocolizadas**: la máquina de eventos «línea base → movimiento → pico → vuelta a
  la base», la simetría entre lados, la consistencia, el gráfico ángulo–tiempo con salto al máximo y el CSV largo. Lo que no hay que
  copiar: la velocidad en m/s con un muslo fijo de 0,40 m y 21 fps fijos, y la exportación por grabación de pantalla a 512 kbps.

---

## 1. Identidad

| Dato | Valor |
|---|---|
| Paquete · versión | `io.yogger.Yogger` · 6.1.0 (versionCode 56) · minSdk 26 · targetSdk 36 · compileSdk 36 |
| Archivo | `Yogger_+Movement+Analysis+App_6.1.0_APKPure.xapk`, 154 526 247 B, SHA-256 `21d110fd16c8adf707bee1fc75ed9b13e2b19f8d4acf93d045940ed675a42782` |
| Sello de Play | `com.android.stamp.source = https://play.google.com/store`, `STAMP_TYPE_DISTRIBUTION_APK`, splits requeridos (`com.android.vending.splits.required`) |
| Backend | Firebase, proyecto `yogger-ios` (bucket `yogger-ios.appspot.com`); web `app.yogger.io` (pasarela de acceso, SSO, gestión de suscripción) |
| Ofuscación | Ninguna en el código propio: paquetes y clases legibles (`mapping_file_id` a ceros) |

**APK internos** (el XAPK solo trae el split **armeabi-v7a**; el de arm64 no viene, sin confirmar que exista en Play):

| Archivo | Bytes | SHA-256 |
|---|---|---|
| `io.yogger.Yogger.apk` (base) | 135 863 236 | `af48d792641eee048df817ad45141e34e4b0ee0891848256793042430ad5956a` |
| `config.armeabi_v7a.apk` | 16 056 838 | `df218b2e36d0cd7e0ca32b5ee81508e51fb8d3bab7562f84013b9dbc033e4563` |
| `config.mdpi.apk` | 129 941 | `be73b93c2d0cdfa24f8757d1f19a87f156a62484b0a640c4eee0e6487218dec8` |
| `config.en.apk` | 135 577 | `a6c51f1edf238b44e10b5f06408c08b02d04e00aea7c7852c611f4562a274eba` |
| `config.es.apk` | 278 937 | `c13afc2571f969298051fff702b2d96364b80eda1ad11b29bf3ca3905c045b5f` |
| `config.pt.apk` | 266 649 | `267f03fad845047ab42547f4f3dea93a793454f9916b2ea90000466a902139c8` |
| `config.fr.apk` | 262 553 | `e2729d2a2724362b872d95683e4fdbd5d196e07573feaaf74121bf63db16ce8f` |
| `config.de.apk` | 242 073 | `f49ab2aca4e46103be8f650ac40d254f3b52904c39c27a372c3b6946c1b57ec4` |
| `config.it.apk` | 237 977 | `c7e9cdfb443ec15b6d3079a27c9ffc4a58c3c7d346157e4b82744ecc4afbe255` |
| `config.zh.apk` | 102 809 | `4962f06d14942d73a0cc7bcc9c751a0b92bc61aa32ac0bec08b1d62cf9552c90` |
| `config.in.apk` | 70 041 | `272f0de12263ba1e81e5e39f6620e9804410741eea8f8a459cc8527560b5cd0f` |
| `config.hi.apk` | 65 945 | `2a35dca68d340a5fc4c8ec42a5dd35b35219c0abed3f2e82de94c3f386e8715d` |
| `config.my.apk` | 65 945 | `35813feaec9623d154e3cc6ae625bc7d0e524b555e2363ca95f32bf817e05b75` |
| `config.ru.apk` | 65 945 | `be0f09e6c67c7324674a1df27c3e98fd2737a98a77a642500c9ba5a15754e9ce` |
| `config.th.apk` | 65 945 | `8fb4326e1332c5fdd721e89e055517188bb968bee39bf14cc12e8165a24c7e81` |
| `config.ar.apk` | 61 849 | `d6378d6a2baddc8352939751e0dca6d48ab9828b0d6d3653fe364a8889739c65` |
| `config.ja.apk` | 61 849 | `ea776c6a5f14b82b6e918ac2dbdeebc0a5bf15ea2c5f70f77169a5611d88441d` |
| `config.vi.apk` | 61 849 | `935db184978b43ed6ac85e53f4066783c4f65fe5a966ad19a83dde572623f8b6` |
| `config.ko.apk` | 57 753 | `a280cf349fdc6aa16e650acc8fa0e11b2bbf2f5101c79d98839af175a4ff7519` |
| `config.tr.apk` | 57 753 | `e4f2536c4a1a2be8db6e46b13c096a880a7c09eccf106bf7b1f770f339b81d8e` |
| `manifest.json` (APKPure) | 2 205 | `dc480f5ad8ae6122a34dfd9172f70faac467525dd4df380a29240d4f303c87ef` |

**Arquitectura.** App nativa en Kotlin (plugin Kotlin 2.2.0, AGP 8.9.1, Gradle 8.14.4). La mayor parte es Views + DataBinding +
Navigation 2.5.2, con un solo `MainActivity` y fragmentos (`res/navigation/mobile_navigation.xml`); hay Compose 1.7.2 y Material3
1.3.1 en pantallas nuevas y en el paywall de RevenueCat. La cámara es **Camera2 directa** (sin CameraX), con `TextureView` y
`MediaRecorder`. El reproductor es Media3 ExoPlayer **1.0.2**. Siete DEX con 54 480 clases: 2 748 de `io.yogger.Yogger` y 87 en el paquete
por defecto (las herramientas de ángulo: `AnalysisTool`, `KneeFlexion`, `NeckRotation`…).

Por error de empaquetado viajan dentro del APK las herramientas de compilación de Crashlytics: binarios `dump_syms` para Windows, macOS
y Linux (unos 17 MB sin comprimir), Apache Commons y ~3 700 clases `com.google.firebase.crashlytics.buildtools`. No afectan al análisis.

**Nativo** (solo `armeabi-v7a`):

| Biblioteca | Tamaño | Qué es | Licencia |
|---|---|---|---|
| `libxeno_native.so` | 16 008 520 B | runtime nativo de ML Kit: grafo MediaPipe (127 exportaciones JNI `com.google.mediapipe.framework.*`), TFLite con delegados GPU (OpenCL/GL) y NNAPI, y **OpenCV 4.10.0** embebido; contiene `LandmarksSmoothingCalculator` (filtros de velocidad y One Euro) y `MLKitValidatePoseLandmarksCalculator` | binario cerrado de Google (términos de ML Kit); OpenCV es Apache-2.0 |
| `libandroidx.graphics.path.so` | 7 252 B | AndroidX graphics | Apache-2.0 |

**Modelos de ML** (`assets/mlkit_pose/`, no se extrajeron; forma leída en memoria):

| Modelo | Tamaño | Entrada | Salida | Uso |
|---|---|---|---|---|
| `pose_person_detector_f16.tflite` | 2 962 288 B | FLOAT32 `[1,224,224,3]` | `[1,2254,12]` (cajas y puntos de anclaje) y `[1,2254,1]` (puntuación) | detector de persona BlazePose, FP16 |
| `pose_landmark_detector_lite_f16_inf.tflite` | 2 813 968 B | FLOAT32 `[1,256,256,3]` | `[1,195]` = 39 puntos × 5; `[1,1]` presencia; `[1,128,128,1]` máscara; `[1,64,64,39]` mapas de calor; `[1,117]` = 39 × 3 «world» | BlazePose GHUM lite (modo base de ML Kit; no lo usa la app) |
| `pose_landmark_detector_full_f16_inf.tflite` | 6 434 928 B | igual | igual | BlazePose GHUM **full**: el modo «accurate», el único que usa la app |
| `pose_tracking_graph{,_gpu,_nnapi}.binarypb` y `pose_non_tracking_graph*` | 374–898 B | — | — | grafos MediaPipe: modo flujo (ROI desde el fotograma anterior y suavizado) y modo imagen |
| `benchmark_{breaking1,breaking2,halfbody,jump,plant}.data`, `mobile_acceleration/gpu_compatibility.bin` | 15–49 KB | — | — | datos del mini-benchmark de ML Kit Acceleration para elegir delegado (no son pruebas de la app; uso exacto sin confirmar) |

Son los mismos modelos que el Pose Landmarker de MediaPipe Tasks que ya usa KPKN.

**Bibliotecas de terceros relevantes:**

| Biblioteca | Versión | Licencia |
|---|---|---|
| ML Kit Pose Detection + Accurate · mediapipe-internal · vision-common · acceleration | 18.0.0-beta5 · 17.0.0-beta10 · 17.3.0 · 16.0.0-beta2 | términos de ML Kit (gratis, cerrado) |
| AndroidX Media3 ExoPlayer | 1.0.2 | Apache-2.0 |
| MPAndroidChart (gráficos de la app y de los PDF) | sin confirmar | Apache-2.0 |
| Rajawali 3D (vista 3D del esqueleto) | sin confirmar | Apache-2.0 |
| ZoomLayout + opengl de Otalia Studios (zoom y paneo del vídeo) | sin confirmar | Apache-2.0 |
| mp4parser/isoparser · commonmark-java · AndroidSVG · Glide | sin confirmar | Apache-2.0 · BSD-2 · Apache-2.0 · BSD/MIT/Apache |
| Firebase: Auth 23.0.0, Analytics 22.1.0, Firestore, Storage, Functions, App Check, Crashlytics, Sessions, Remote Config | — | SDK Apache-2.0 (servicio de Google) |
| RevenueCat (compras y paywalls) · Google Play Billing | sin confirmar · 9.1.0 | MIT · Android SDK License |
| Facebook Android SDK (login, compartir, App Events) | sin confirmar | Facebook Platform License |
| Jetpack: Compose 1.7.2, Material3 1.3.1, Fragment 1.6.1, Lifecycle 2.8.3, WorkManager 2.7.0, DataStore 1.0.0, Credentials 1.2.0-rc01, Core 1.15.0, Material 1.12.0, coroutines 1.7.3 | según `META-INF/*.version` | Apache-2.0 |

**Permisos y sensores.** `CAMERA`, `RECORD_AUDIO` (voz en off de la grabación de pantalla y audio del vídeo), `FOREGROUND_SERVICE` +
`FOREGROUND_SERVICE_MEDIA_PROJECTION` + `MEDIA_PROJECTION` (`ScreenRecordService`), `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`,
`RECEIVE_BOOT_COMPLETED` (WorkManager), `BILLING`, `AD_ID` y los de AdServices (atribución, temas, audiencias), `c2dm RECEIVE`, referrer de
Play. `android.hardware.camera` va como no obligatoria y `camera.any` como obligatoria. **Sin** acelerómetro ni giroscopio (solo `OrientationEventListener` para girar la interfaz),
sin BLE, sin GPS, sin Health Connect. Consulta a `com.facebook.katana`.

---

## 2. Inventario completo de funciones

### 2.1 Planes y bloqueos
- **Tipos de suscripción** (`SubscriptionModels`): STARTER, BASIC, TEAMS, PROFESSIONAL y CLINIC, con niveles de acceso FREE, BASIC y
  BUSINESS; estados SUBSCRIBED, CANCELLED, TRIAL y TRIAL_EXPIRED. El servidor devuelve contadores de uso (`Usage.analysisRemaining`,
  `assessmentRemaining`) y bloqueos (`BlockReason`: no suscrito, límite del plan, límite de uso, restricción de la organización).
- **Funciones bloqueables** (`FeatureGate`): ASSESSMENTS, REPORTS, ATHLETE_MANAGEMENT, VIDEO_ANALYSIS, SCREEN_RECORDING y ASSESSMENT_FLOW.
- **Ofertas** (textos):
  - Basic: aislar articulaciones y ROM, trayectoria y velocidad, grabación de pantalla y voz en off ilimitadas, lado a lado y 50 GB en
    la nube.
  - Business/Teams: prueba de 7 días; evaluaciones, clientes, progreso, informes con marca, almacenamiento de equipo.
  - Lifetime (`yogger_video_analysis_lifetime`): pago único; vídeos ilimitados en el dispositivo, dibujo, fotograma a fotograma y
    cámara lenta.
  - Además: códigos promocionales, oferta del 30 % en el plan anual, «un mes gratis» por publicar en redes un vídeo analizado
    etiquetando a @yogger.io, y 10 análisis gratis por completar tareas de introducción.

### 2.2 Acceso e introducción
- `LoadingActivity`: comprueba la versión mínima («Update Required») y canjea sesiones web (`redeemMobileAuthSession`).
- `SignUpLoginActivity`: correo y contraseña, Google (Credential Manager), Facebook y **SSO SAML** por pasarela web. Reglas de
  contraseña: 8 caracteres, dígito, minúscula, carácter especial y carácter único.
- Introducción:
  - rol (entrenador deportivo, quiropráctico, entrenador, médico, entrenador personal, fisioterapeuta, preparador físico, estudiante,
    otro), deporte (19, de béisbol a halterofilia), identidad de género, edad, organización y equipo;
  - elección entre «Analizar un vídeo» y «Hacer una evaluación»;
  - lista de tareas de la prueba gratuita: medir un ángulo, comparar lado a lado, completar una evaluación, ver el informe… y, para
    entrenadores, crear atleta, poner la marca, crear un flujo, completar los flujos de cuello, hombro y tronco, y exportar un informe.

### 2.3 Inicio (`HomeFragment`)
- **Entrenador** (`CoachFragment`): atletas activos y pendientes, tareas pendientes, vencidas y completadas, añadir atleta, asignar
  tarea, marca e invitar a otro entrenador.
- **Atleta** (`AthleteHomeFragment`): tareas con fecha («vence hoy», «X días de retraso»), resumen y equipo.
- **Básico**: medios recientes, blog, boletín (SendGrid) y tarjetas de oferta.

### 2.4 Cámara (`CameraViewFragment`, pestaña «Record/Analyze»)
- **Modos:**
  - **Grabar**: cámara trasera, 1280×720 o 1920×1080, con selector de 30/60/120/240 fps según los rangos AE del sensor (ver §4.7);
  - **Subir**: vídeo de la galería o de la biblioteca;
  - **Evaluación**: cámara **frontal** por defecto, con la pantalla hacia el usuario o con alguien grabando.
- Tutorial interactivo de la cámara, girar cámara y miniatura del último vídeo.
- **Ajustes de evaluación** (`CameraViewSettings`, guardados en el perfil): instrucciones por voz y vídeo de demostración en bucle.

### 2.5 Reproductor de análisis (`PlaybackViewFragment` + `VideoViewFragment`)
- **Reproducción:** a 1×, 0,5× y 0,25×; paso de fotograma de **±44 ms fijos**; zoom y paneo; silencio.
- **Esqueleto:** cuatro modos de vista: vídeo con esqueleto, **solo esqueleto sobre fondo negro**, **esqueleto 3D** (Rajawali, con
  órbita y pellizco) y vídeo solo.
- **Aislar articulaciones**, por región (`AnalysisToolManager.bodyPartBiomechanicsSelectionTools`):
  - cuello: rotación, flexión lateral y flexión AP izquierda y derecha;
  - hombro: flexión, abducción/aducción, rotación y simetría;
  - codo: flexión;
  - tronco: rotación superior, flexión lateral y flexión AP;
  - cadera: rotación pélvica, flexión, abducción/aducción, rotación y caída de cadera;
  - rodilla: flexión y valgo/varo;
  - tobillo: ángulo de tibia y dorsiflexión.

  Cada herramienta dibuja su geometría (referencias discontinuas, sector relleno) y el valor en la tabla de medidas.
- **Trayectoria articular:** traza 2D de las articulaciones elegidas, coloreada por velocidad de verde a amarillo y rojo, con una
  etiqueta «X,X m/s» en el punto de velocidad máxima (§3.7).
- **Dibujo** (`DrawingView`): trazo libre con color y grosor; ángulos de tres puntos, horizontal y vertical arrastrables; línea de
  medida; deshacer y borrar.
- **Medir distancias** (`DistanceSettingsFragment`): por altura del sujeto o por una línea de referencia de longitud conocida, en m,
  pies-pulgadas o yardas.
- **Gráfico** (`GraphFragment`): ángulos de las herramientas activas frente al tiempo de reproducción.
  - Tocar el gráfico salta el vídeo a ese instante.
  - Hay botones para «ir al máximo» y «al mínimo».
  - **Exporta CSV** (requiere el plan con informes).
- **Instantánea:** fotograma + capa en PNG, con la tabla de medidas y una puntuación por herramienta (§3.8). Se guarda en una colección.
- Grabación de pantalla con voz en off (§5) y paso a «lado a lado».

### 2.6 Lado a lado, grabaciones y biblioteca
- **Lado a lado** (`SplitScreenPlaybackFragment`): dos vídeos con su propia pose, división vertical u horizontal, candado para mover
  los controles juntos, etiquetas de medida por lado, grabación de pantalla e instantánea combinada.
- **Vista de grabación** (`ScreenRecordingViewFragment`): previsualización, guardar en la galería (MediaStore) o compartir.
- **Biblioteca** (`LibraryFragment`): vídeos e instantáneas por colecciones y fechas, filtros y orden, borrado múltiple y selección para
  lado a lado. Visibilidad por elemento: «Solo yo» o «Yo y los entrenadores».

### 2.7 Evaluaciones (pago: ASSESSMENTS)
- **Catálogo** (`AssessmentType`, 14 pruebas, detalle en §3):
  - funcionales: sentadilla overhead (OHS), zancada y postura;
  - rendimiento: sentadilla a una pierna (SLS);
  - movilidad: flexión-extensión de hombro, abducción-aducción de hombro, rotación de hombro, rotación de cadera sentado, rotación de
    cuello, flexión-extensión de cuello, flexión lateral de cuello, rotación torácica sentado, rotación de tronco de pie y
    flexión-extensión de tronco de pie.
- **Elegir:** filtros (amplitud de movimiento, funcional, rendimiento, cuello, hombro, tronco, tren inferior, flujos). Cada prueba
  tiene ficha: introducción, componentes, por qué importa y cómo se puntúa, más instrucciones paso a paso con vídeo.
- **Cámara guiada:**
  1. calibración de encuadre y comprobación de orientación;
  2. cuenta atrás de 3 s con pitidos y voz;
  3. ventana de 12 s por etapa, con aviso «siguiente etapa en N»;
  4. sonidos de repetición, de serie y de fin.
- **Resultados:**
  - puntuación total (0–100) y por componente, con rúbrica en cuatro bandas;
  - simetría y consistencia;
  - en cada componente, el gráfico de la variable por lado y un salto al vídeo en el instante del pico;
  - aviso de «retest recomendado».
- **Guardar:** con atleta y visibilidad.
- **Comparar** dos fechas (`AssessmentCompareFragment`): diferencia en grados, en % o en años, con orden por fecha o por puntuación.
- **Flujos** (`AssessmentFlowObject`): secuencias de pruebas con portada y nombre. Al terminar, pantalla «¿Repetir alguna?». La puntuación
  del flujo es la media de las pruebas. Las plantillas vienen de la organización en Firestore; en los textos aparecen cuello, hombro,
  tronco y cadera.

### 2.8 Edad de movimiento (Movement Age)
Siete pasos (`MovementAgeStepNavigation`):
1. datos del atleta: edad, sexo, si está en rehabilitación y zonas lesionadas;
2. a 5. cribados de movilidad de cuello, hombro, tronco y cadera (flujos);
6. rendimiento: flexiones con cronómetro y **conteo tecleado** (selector de 0 a 200) y plancha con cronómetro;
7. cuestionario de cinco hábitos: horas sentado, fuerza, movilidad, sueño y rigidez matinal.

El resultado da la «edad de movimiento» frente a la edad real, en tres bloques: movilidad, fuerza y resistencia, y hábitos. Permite
comparar fechas y sacar un PDF. Fórmula en §3.6.

### 2.9 Progreso, informes y datos
- **Progreso** (`ProgressFragment`): filtros por región y tipo (prueba suelta, flujo, edad de movimiento), detalle por atleta y
  tendencia, comparar y crear informe.
- **Informe PDF** (`ReportGenerator`, `android.graphics.pdf.PdfDocument`):
  - componentes: encabezado, separador, instantánea, evaluación, flujo, edad de movimiento y texto con títulos;
  - ajustes: resumen de la evaluación, resumen de componentes, imágenes del atleta y resumen del flujo;
  - tabla MÉTRICA / PUNTUACIÓN / RANGO OBJETIVO, gráfico de datos (MPAndroidChart a mapa de bits), notas y logotipo y colores de la
    organización;
  - vista previa, guardar en Storage y compartir.
- **CSV largo** desde el gráfico (§5).

### 2.10 Entrenadores y organización (pago: ATHLETE_MANAGEMENT, REPORTS)
- Organización con marca (logotipos y colores) e invitación de entrenadores (`inviteProviderToOrganization`, `TEAMINVITES`).
- Alta de atletas por función en la nube (`createAthlete`) con invitación a la app.
- **Tareas** (`AssignTaskFragment`, `AthleteTaskManager`):
  - evaluaciones o flujos con fecha límite y seguimiento de cumplimiento en los últimos 7 días;
  - al guardar un medio que cumple una tarea pendiente, la app propone «¿Completar tarea?» (`completeFirstEligibleTask`);
  - **evaluaciones remotas**: el atleta las hace en su app y el entrenador las ve.
- **Envíos del atleta** (`ATHLETEUPLOADS`) con notificaciones al entrenador: coaching remoto de vídeo.

### 2.11 Perfil y soporte
Cuenta (nombre, foto, contraseña, borrar cuenta con función en la nube `deleteUserAccount`), suscripción, restaurar compras, código
promocional, ayuda y FAQ en WebView, tickets de soporte (`SUPPORT_TICKETS`), sugerencias (`APP_FEEDBACK`), valorar la app y cerrar
sesión.

### 2.12 Presente en recursos pero sin código en esta versión
- **Programas de ejercicios en casa** («hep_*»): constructor, dosis, check-in de esfuerzo y dolor… 126 cadenas, ninguna referencia
  fuera de `R.java`.
- **Metas**: el grafo de navegación apunta a `io.yogger.Yogger.goals.views.CreateGoalFragment`, que no existe en los DEX.
- La cadena «Score %d · Low confidence» no se usa en ninguna parte.

Puede tratarse de funciones de otra plataforma o futuras (sin confirmar).

---

## 3. Catálogo de métricas

### 3.0 Lo que NO hay
Comprobado por búsqueda en el código descompilado, en las cadenas del DEX y en los recursos:
- ningún salto: ni altura, ni tiempo de vuelo, ni RSI, ni rigidez;
- nada de carrera ni esprint: ni contacto, ni vuelo, ni cadencia, ni zancada, ni parciales;
- ni VBT ni barra;
- ni equilibrio cronometrado ni oscilación postural;
- ningún conteo automático de repeticiones: las flexiones se teclean y las evaluaciones son de 1 repetición por lado o vista.

«Gait» solo aparece en `BlogRepository`, y «jumprope», como ilustración de portada.

### 3.1 Herramientas de ángulo del análisis libre (`defpackage/*`)
- **Coordenadas:** en **píxeles de la vista** (el `TextureView` en pantalla), sin corrección de perspectiva.
- **Ángulos:**
  - `angle180` da el ángulo incluido en el vértice, de 0 a 180°;
  - `angle360` da el ángulo orientado entre dos rayos, de 0 a 360°;
  - las rotaciones usan `unsignedAngleBetweenXZ` sobre puntos 3D construidos como explica §4.2.
- **Rango «normal»:** cada herramienta tiene un mínimo y un máximo que solo sirve para colorear y puntuar instantáneas.

| Herramienta | Definición (con palabras propias) | Plano / vista | Rango «normal» |
|---|---|---|---|
| Flexión de codo | 180 − ángulo muñeca-codo-hombro | sagital, lateral | 0–150 |
| Flexión de rodilla | 180 − ángulo tobillo-rodilla-cadera | sagital | 0–140 |
| Flexión de cadera | 180 − ángulo hombro-cadera-rodilla | sagital | 0–140 |
| Flexión de hombro | ángulo codo-hombro-cadera (en las evaluaciones, versión con signo: §3.5) | sagital | −50–180 |
| Abducción-aducción de hombro | mismo ángulo codo-hombro-cadera, etiquetado como frontal | frontal | −75–180 |
| Rotación de hombro (90/90) | ángulo orientado entre una horizontal desde el codo y el antebrazo | transversal, vista lateral | 90 / −70 |
| Simetría de hombros | inclinación de la línea de hombros respecto a la horizontal | frontal | 0–20 |
| Dorsiflexión de tobillo | 90 − ángulo entre la rodilla y la punta, con vértice en el corte de la recta tibia (rodilla-tobillo) con la recta del pie (talón-punta) | sagital | −55–40 |
| Ángulo de tibia | ángulo entre la horizontal hacia la punta y la tibia | sagital | 0–90 |
| Valgo-varo de rodilla | 180 − ángulo cadera-rodilla-tobillo, sin signo | frontal | −5–15 |
| Abducción de cadera | ángulo del muslo respecto a la vertical descendente desde la cadera | frontal | 0–45 |
| Rotación de cadera (sentado) | ángulo de la tibia respecto a la vertical descendente desde la rodilla | frontal | 0–40 |
| Caída de cadera | inclinación de la línea de caderas respecto a la horizontal | frontal | 20/20 (sic) |
| Inclinación AP del tronco | ángulo de la línea cadera→hombro respecto a la vertical | sagital | 0–90 |
| Flexión lateral del tronco | ángulo entre la vertical por el punto medio de las caderas y el punto medio de los hombros | frontal | 0–60 |
| Flexión AP del cuello | ángulo orientado oreja→nariz respecto a una horizontal desde la oreja, con signo según el lado | sagital | 0–25 |
| Flexión lateral del cuello | ángulo entre la vertical por el punto medio de los hombros y el punto «medio de orejas a la altura de la nariz» | frontal | 0–45 |
| Rotación de cuello, de tronco superior y pélvica | ángulo en el plano XZ de la línea oreja-oreja, hombro-hombro o cadera-cadera respecto al eje X (0–90°), con la profundidad de ML Kit | transversal, frontal | 0–80 / 0–60 / 0–60 |

### 3.2 Puntuación común de las evaluaciones
- **Por componente y lado** (`AssessmentObject.calculateScores`): % = (x − malo) / (bueno − malo), recortado a [0, 1], × 100. Si
  «bueno» < «malo» se invierte; pasar del valor bueno da 100 %.
- **Total de la prueba:** Σ(puntos × %) / Σ puntos, con 5, 10, 15 o 20 puntos por componente (`PointsPerRep`). **Total del flujo:** media
  de las pruebas.
- **Rúbrica** de cuatro bandas por componente (excelente, buena, regular, mala). Colores: verde ≥ 100 %, amarillo ≥ 50 %, naranja ≥ 1 %,
  rojo.
- **Simetría** (`ComponentScoringEvaluator.evaluateSymmetry`), sobre el valor de cada lado en grados:
  - |izquierda − derecha| < 0,1° es «excelente»;
  - > 5° es «notable»;
  - en medio, «ligera»;
  - «a favor» del lado con el valor mayor.
- **Consistencia** (`evaluateQuality`): desviación media absoluta de las diferencias entre fotogramas consecutivos de la serie del
  componente. < 0,1 es «consistente», < 5 «ligera variación» y el resto «notable».
- **Calidad:** el único aviso es `SignalQualityFlag.IncompleteMotionSequence`. Lo levanta el tracker que no vuelve a la base en
  180 fotogramas y se muestra como «Error al registrar: se recomienda repetir». Sin avisos, «Grabado correctamente».

### 3.3 Sentadilla, zancada y sentadilla a una pierna
«Malo → bueno» es el valor que da 0 % y el que da 100 %. Rúbrica: excelente / buena / regular / mala.

**Sentadilla overhead** (vistas: lateral derecha, frontal y lateral izquierda; 1 repetición por vista):

| Componente | Medida | Malo → bueno | Puntos | Rúbrica |
|---|---|---|---|---|
| Profundidad | flexión de rodilla máxima en la repetición lateral | 90 → 120 | 20 | > 120 / 105–120 / 90–105 / < 90 |
| Movilidad de hombro | ángulo codo-hombro-cadera **en el fotograma de máxima flexión de rodilla** | 150 → 180 | 10 | > 180 / 165–180 / 150–165 / < 150 |
| Postura | \|ángulo tronco-horizontal − ángulo tibia-horizontal\| en el fondo (tronco paralelo a la tibia) | 15 → 5 | 10 | < 5 / 5–10 / 10–15 / > 15 |
| Estabilidad («oscillation») | pico de flexión lateral del tronco en la repetición frontal | 15 → 5 | 5 | < 5 / 5–10 / 10–15 / > 15 |

**Zancada** (lateral derecha, frontal con dos zancadas y lateral izquierda):

| Componente | Medida | Malo → bueno | Puntos | Rúbrica |
|---|---|---|---|---|
| Profundidad | flexión de rodilla de la pierna delantera **en el pico de flexión de cadera** | 60 → 90 | 15 | > 90 / 75–90 / 60–75 / < 60 |
| Movilidad de cadera | flexión de cadera máxima | 60 → 90 | 15 | igual |
| Postura | inclinación del tronco respecto a la vertical en el pico | 15 → 5 (el texto dice 25) | 10 | < 5 / 5–15 / 15–25 / > 25 |
| Movilidad de tobillo | herramienta de dorsiflexión en el pico; «menos es mejor» (convención sin confirmar) | 30 → 10 | 5 | < 10 / 10–20 / 20–30 / > 30 |
| Estabilidad | pico de flexión lateral del tronco en la zancada frontal | 15 → 5 | 10 | < 5 / 5–10 / 10–15 / > 15 |
| Valgo | pico de inclinación frontal de la **tibia** respecto a la vertical, por pierna (`LungeHelpersKt.calcKneeValgus`) | 15 → 5 | 10 | igual |

**Sentadilla a una pierna** (lateral derecha, frontal pierna derecha, frontal pierna izquierda y lateral izquierda):

| Componente | Medida | Malo → bueno | Puntos | Rúbrica |
|---|---|---|---|---|
| Flexión de rodilla | máxima | 60 → 90 (el texto dice 30 → 60) | 5 | > 90 / 75–90 / 60–75 / < 60 |
| Flexión de tronco | inclinación del tronco respecto a la vertical en el fondo (el texto habla de diferencia tronco-tibia) | 15 → 5 | 5 | < 5 / 5–10 / 10–15 / > 15 |
| Valgo | pico de inclinación frontal de la tibia | 10 → 5 (el texto dice 15) | 15 | < 5 / 5–7 / 7–10 / > 10 |
| Flexión lateral del tronco | pico en la vista frontal | 15 → 5 | 10 | < 5 / 5–10 / 10–15 / > 15 |
| Inclinación pélvica | pico de la caída de cadera | 15 → 5 | 10 | igual |

**Bisagra:** no hay análisis de peso muerto ni de bisagra. Lo más cercano es la flexión de tronco de pie (§3.5).

**Valgo:** se mide de dos formas, ambas en 2D y sin signo:
- en zancada y SLS, como inclinación de la tibia en la vista frontal;
- en la herramienta libre y en la postura, como 180 − ángulo cadera-rodilla-tobillo.

No distingue valgo de varo ni corrige el giro de la pierna.

### 3.4 Postura estática (5 s por vista; se promedian los fotogramas)
| Componente | Vista y medida | Malo → bueno | Puntos |
|---|---|---|---|
| Inclinación AP de la cabeza | lateral: \|flexión AP del cuello\| | 25 → 5 | 5 |
| Cabeza adelantada | lateral: \|ángulo de la cabeza respecto a la vertical por los hombros\| | 25 → 5 | 15 |
| Inclinación lateral de la cabeza | frontal | 15 → 5 | 15 |
| Rotación cervical | frontal: giro de las orejas (3D) | 15 → 5 | 15 |
| Alineación de hombros | frontal: inclinación de la línea de hombros | 8 → 2 | 10 |
| Alineación del tronco | lateral: AP; frontal: lateral | 15 → 5 | 15 |
| Alineación de caderas | lateral: flexión de cadera; frontal: caída de cadera | 8 → 2 | 10 |
| Alineación de rodillas | lateral: flexión; frontal: valgo-varo por pierna | 8 → 2 | 5 |
| Alineación de pies | frontal: ángulo de la línea rodilla→punta del pie respecto a la vertical (`PostureHelpersKt.calcFeetInversion`) | 25 → 5 | 10 |

### 3.5 Movilidad (amplitud articular)
El valor que se guarda es el **pico** dentro de la ventana de 12 s.

| Prueba (vista) | Componente: medida | Malo → bueno | Puntos | Rúbrica |
|---|---|---|---|---|
| Flexión-extensión de hombro (lateral, cada lado) | ángulo **con signo** del brazo (hombro→muñeca) respecto a la vertical descendente; flexión = máximo | 150 → 180 | 5 | > 180 / 165–180 / 150–165 / < 150 |
| | extensión = mínimo (negativo) | −40 → −60 | 5 | > 60 / 50–60 / 40–50 / < 40 |
| Abducción-aducción (frontal, cada brazo) | ángulo con signo del brazo en el plano frontal; abducción | 150 → 180 | 5 | > 180 / 165–180 / 150–165 / < 150 |
| | aducción cruzando el cuerpo (negativo) | −20 → −50 | 5 | > 50 / 35–50 / 20–35 / < 20 |
| Rotación de hombro (lateral, brazo a 90/90) | ángulo con signo del antebrazo respecto a la horizontal desde el codo; externa | 60 → 90 (el texto dice 70 → 100) | 5 | > 90 / 75–90 / 60–75 / < 60 |
| | interna (negativo) | −40 → −70 (el texto dice 60 → 90) | 5 | > 70 / 55–70 / 40–55 / < 40 |
| Rotación de cadera (sentado, frontal, cada pierna) | ángulo **sin signo** de la tibia respecto a la vertical; el primer movimiento cuenta como interna y el segundo como externa | interna 20 → 40; externa 30 → 50 | 5 + 5 | > 40… / > 50… |
| Flexión-extensión de cuello (lateral derecha) | flexión AP del cuello con signo | flexión 20 → 40; extensión −20 → −40 | 10 + 10 | > 40 / 30–40 / 20–30 / … |
| Flexión lateral de cuello (frontal; derecha y luego izquierda) | flexión lateral, valor absoluto | 20 → 40 | 10 | > 40 / 30–40 / 20–30 / < 20 |
| Rotación de cuello (frontal; derecha y luego izquierda) | \|giro de la línea de orejas en XZ\| | 30 → 60 | 5 | > 60 / 45–60 / 30–45 / < 30 |
| Rotación torácica (sentado, frontal) | \|giro de la línea de hombros en XZ\| (no relativo a las caderas, aunque el texto lo diga) | 25 → 45 (el texto dice 30 → 50) | 5 | > 45 / 35–45 / 25–35 / < 25 |
| Rotación de tronco de pie (frontal) | \|giro de hombros\| y \|giro de caderas\| en XZ | 60 → 80; 25 → 45 | 5 + 5 | > 80…; > 45… |
| Flexión-extensión de tronco de pie (lateral) | inclinación del tronco respecto a la vertical: flexión, extensión (sin signo; el orden decide) y flexión de rodilla (que no se doble) | 45 → 90; 10 → 30; 30 → 10 | 10 + 10 + 5 | > 90 / 67,5–90 / 45–67,5 / < 45… |

El lado lo decide el **orden** de las etapas («primero a la derecha, luego a la izquierda»), no el signo medido.

### 3.6 Edad de movimiento (`MovementAgeScoring`)
- **Cribado:** media ponderada de las puntuaciones de los flujos (cuello 0,1, hombro 0,1, tronco 0,2, cadera 0,3; normalizada).
- **Flexiones:** x = (reps − mín) / (máx − mín), con 15–29 en hombres y 8–19 en mujeres. **Plancha:** 45–120 s en hombres y 30–90 s en
  mujeres. Cada una se puntúa como 100 · ln(1 + 5x) / ln 6, y el bloque de rendimiento es la media de las dos.
- **Edad de movimiento** = round(clamp(120 − (cribado + rendimiento) / 2, 20, 70)). Una puntuación perfecta da 20 «años» y una de 50, 70.
  **No hay normas por edad**, aunque el texto diga «relativa a normas por edad».
- **Cuestionario:** la suma de 5 respuestas, recortada a 15, se pasa a %; no entra en la edad.
- **Tablas informativas:** flexiones < 15 / 15–21 / 22–29 / ≥ 30; plancha < 45 / 45–84 / 85–119 / ≥ 120 s.

### 3.7 Trayectoria, velocidad y distancia (análisis libre)
- **Trayectoria:** lista de puntos 2D ya suavizados (§4.2) de cada articulación elegida, un punto por pose procesada.
- **Velocidad** (`AnnotationView.drawSmoothLine`):
  - v = |p(i+4) − p(i)| / (4/21 s), en px/s, con **21 fps constantes** (`AnalysisToolManager.frameRate = 21`), no con el tiempo real
    entre fotogramas;
  - el color normaliza v entre el mínimo y el máximo de la traza;
  - la etiqueta «m/s» muestra solo el máximo, convertido con **0,40 m / largo medio del muslo en píxeles** (cadera→rodilla, el mayor de
    los dos lados, media de los 3 últimos fotogramas).

  La altura del atleta no interviene. No hay derivadas filtradas.
- **Distancia** (`PoseHistory.calculateDistance`):
  - **método altura** (por defecto, 1,76 m): d = px × (altura × 0,244) / muslo_px, es decir, el muslo como 24,4 % de la estatura;
  - **método línea**: la primera línea de medida que se dibuja es la referencia y lleva un «*»; el usuario teclea su longitud.

### 3.8 Instantánea y CSV
- **Puntuación de la instantánea** (`SnapshotScoring`): posición del valor dentro del rango «normal» de la herramienta, en %; la de la
  sección es la media.
- **CSV largo** (`GraphFragment.exportDataToCsvLongFormat`):
  - columnas `time_s, frame, side, joint, metric, value, units`, con todo en grados;
  - nombre de archivo `Nombre_Apellido_yyyyMMdd_HHmmss.csv`;
  - el tiempo sale del reproductor, no de los PTS.

### 3.9 Incoherencias entre textos y código
Ocho componentes puntúan con umbrales distintos de los que explica su propia ayuda:
- valgo de SLS (10 frente a 15);
- flexión de rodilla de SLS (60–90 frente a 30–60);
- tronco de SLS (vertical frente a diferencia tronco-tibia);
- rotación externa e interna de hombro;
- rotación torácica;
- postura de la zancada;
- rotación torácica «relativa a la cadera».

No hay citas bibliográficas de los umbrales en el código. Es señal de calibración a mano, sin validación.

---

## 4. Algoritmos

### 4.1 Modelo de pose y dónde se ejecuta
- **Cliente** (`camera/PoseDetectionClient.createAccurateStreamDetector`): ML Kit `AccuratePoseDetectorOptions` en **STREAM_MODE**
  (seguimiento del ROI y suavizado internos de ML Kit) con hardware preferido **solo CPU**. Lo usan la cámara en vivo, el reproductor y
  el lado a lado.
- **Salida:** 33 puntos con x, y en píxeles, z de profundidad relativa a la cadera en escala parecida a x, y una probabilidad de estar
  dentro del cuadro. Dentro de `libxeno_native` actúan un `LandmarksSmoothingCalculator` (filtro de velocidad relativa y One Euro) y un
  validador de puntos de ML Kit. Sus parámetros no son visibles.
- **Fotogramas:** no se decodifica el vídeo. Se hace **captura del `TextureView`** (`getBitmap()`), tanto de la vista previa de la
  cámara (búfer de 1920×1080) como del vídeo que reproduce ExoPlayer. Con la cámara frontal en evaluaciones, la imagen se espeja. Si ML
  Kit lanza un error, se reintenta con la mitad izquierda y luego con la derecha (`PoseDetectorLoop.detectPose(cropOption)`).
- **Cadencia:** el bucle pide la pose siguiente en cuanto termina la anterior, así que la cadencia es la de la inferencia. Los 21 fps
  fijos de §3.7 podrían ser la cadencia típica medida (sin confirmar).
- **Análisis 100 % en el dispositivo:** no hay funciones en la nube de análisis. Las funciones de Firebase son de cuentas, invitaciones,
  boletín, eventos de marketing y borrado.
- **Umbral sin uso:** `PoseConstants.MIN_CONFIDENCE = 0.9` no se usa; ninguna métrica se abstiene por la confianza de los puntos.

### 4.2 Postprocesado propio
- **`AnatomicalPoseProcessor`** (por vídeo, índice 0/1 en lado a lado):
  - **suavizado exponencial** por punto con α = 0,45; 0,25 si la confianza es < 0,5 (se fía del anterior); 0,82 si el salto supera
    máx(1,4 × escala corporal, 60 px), lo que lo marca ESTIMATED;
  - α no depende del tiempo entre fotogramas;
  - **calibración de segmentos:** mediana de las últimas 30 longitudes 2D de 11 segmentos (fémures, tibias, brazos, antebrazos, anchos de
    hombros y caderas, tronco) con confianza ≥ 0,5. La escala corporal es el fémur, o 0,75 × tronco, o 100 px;
  - **restricción de longitudes:** si un segmento se desvía > 45 %, el punto distal se mueve un 20 % hacia la longitud calibrada. Existe,
    pero está **desactivada** (`segmentConstraintsEnabled` nunca pasa a true);
  - **sistema de coordenadas del cuerpo** (`AnatomicalPoseGeometry`): origen en el punto medio de las caderas, eje derecho de cadera
    izquierda a derecha, eje adelante como producto vectorial con la dirección cadera→hombros. Se calcula en cada fotograma, pero ninguna
    métrica lo lee (`point3D()` sin uso).
- **Puntos 3D para las rotaciones** (`AnnotationView.set3DPoints`):
  - x e y salen de los puntos 2D suavizados, centrados y divididos por 10;
  - z es la de ML Kit **reescalada para que la extensión del cuerpo en profundidad valga 2/3 de su extensión horizontal**.

  El giro del eje oreja-oreja, hombro-hombro o cadera-cadera depende de esa normalización ad hoc, no de una geometría métrica.
- **`PoseHistory`:**
  - promedio ponderado de las 5 últimas posiciones si un punto salta más de 2 muslos;
  - muslo = máximo de cadera→rodilla de los dos lados, media de 3 fotogramas;
  - el grosor del esqueleto y de los puntos se escala con el muslo; las líneas se colorean por profundidad (z).

### 4.3 Calibración de inicio y orientación (`Assessment`)
1. **Encuadre** (`calibrateAssessment`, tras la primera instrucción de voz «muévete al centro»):
   - el progreso sube 1/30 por cada fotograma que cumple `isUserStillAndFacingCamera`;
   - la condición combina: puntos medios de ojos, orejas, hombros y caderas alineados en horizontal; hombros y caderas nivelados, con
     tolerancia de medio muslo; y los 8 puntos dentro del fotograma;
   - pese al nombre, no comprueba quietud en el tiempo; la lógica exacta está mal descompilada (sin confirmar);
   - se dibuja un borde animado con «Prepárate» por encima del 30 %.
2. **Orientación** (`calibrateFacing`):
   - mide el giro con signo de hombros y caderas en XZ;
   - acepta lado derecho entre 230° y 310°, izquierdo entre 50° y 130° y frente entre 140° y 220°;
   - +1/60 por fotograma correcto y −0,1 por fotograma incorrecto, con un tiempo máximo de 7 s, tras el que **sigue igualmente**;
   - con su fórmula el giro solo puede caer en [0°, 90°] ∪ [270°, 360°), así que **la condición «de frente» nunca se cumple** y esa
     etapa siempre espera los 7 s (análisis estático, sin confirmar en ejecución).
3. **Cuenta atrás** (`handleTimer`): tres pitidos a un segundo de distancia y luego «go» con la orden hablada.

   Mientras tanto se promedia la posición inicial (`averageStartingPosition`) y, en vistas frontales, la altura de caderas y hombros
   y la **estatura en píxeles** (\|media de tobillos − nariz\|, `accumulateUserHeight`).

### 4.4 Detección de eventos: inicio, pico y fin de la repetición
- **`DirectionalTracker`** (pruebas laterales y de movilidad). Sobre el valor de la herramienta, con dirección ±1:
  - pasa a «en movimiento» cuando |valor| supera la línea base;
  - guarda el pico de valor × dirección y su instante;
  - la «entrada» se cumple con valor ≥ umbral de entrada durante 3 fotogramas;
  - termina cuando |valor| vuelve a ≤ base durante 3 fotogramas, con ≥ 12 fotogramas en movimiento, entrada cumplida y pico ≥ mínimo;
  - con 180 fotogramas sin volver, se reinicia y levanta `IncompleteMotionSequence`.
- **`FrontalRepTracker`** (repeticiones frontales):
  - la base es la Y media de caderas y de hombros;
  - empieza cuando una de las dos baja ≥ 6 % de la estatura en píxeles (o la «puerta», la flexión de rodilla máxima, supera 15°)
    durante 3 fotogramas;
  - termina cuando ambas vuelven a ±5 % durante 3 fotogramas, con ≥ 12 fotogramas y un descenso máximo ≥ 10 %;
  - aborta a los 180 fotogramas.
- **`PeakTracker`:** máximo o mínimo con su instante. Las métricas «acompañantes» se toman en el fotograma del pico: el tronco y el hombro
  en la sentadilla, y la rodilla, el tronco y el tobillo en la zancada.
- **Ventanas:** 12 s por etapa (al acabar, se guarda lo medido), 7 s de orientación y «siguiente etapa en N» a partir del segundo 7.

| Prueba | Señal del tracker | Base (°) | Entrada (°) | Pico mín. (°) |
|---|---|---|---|---|
| OHS lateral / SLS lateral | flexión de rodilla | 20 | 20 | 40 |
| Zancada lateral | flexión de cadera | 25 | 30 | 50 |
| OHS / zancada frontal · SLS frontal | Y de caderas y hombros (5 % / 6 % / 10 % de la estatura) | — | puerta: rodilla 15° (no en SLS) | — |
| Flexión / extensión de hombro | brazo con signo, +1 / −1 | 20 | 30 / 5 | 60 / 10 |
| Abducción / aducción de hombro | +1 / −1 | 20 | 40 / 5 | 90 / 10 |
| Rotación de hombro | +1 / −1 | 20 | 5 / 15 | 15 / 30 |
| Rotación de cadera | +1 | 20 | 8 o 5 | 15 o 12 |
| Flexión / extensión de cuello | +1 / −1 | 7,5 | 8 / 10 | 15 / 20 |
| Flexión lateral de cuello | \|valor\| | 5 | 6 | 12 |
| Rotación de cuello | \|valor\| | 5 | 12 | 25 |
| Rotación torácica | \|valor\| | 5 | 10 | 20 |
| Rotación de tronco de pie | \|hombros\| | 5 | 15 | 30 |
| Flexión / extensión de tronco | inclinación AP | 20 | 15 / 5 | 20 / 10 |

**No hay detección de despegue, aterrizaje ni contacto con el suelo.**

El tiempo de cada evento es (instante de lectura del fotograma − inicio) en µs, por el reloj de pared, y se guarda por lado
(`repTimesArray`) para saltar al vídeo.

### 4.5 Calibración y escala
| Qué | Cómo |
|---|---|
| Altura del atleta | solo en «medir distancias», por el muslo: altura × 0,244 / muslo_px (1,76 m por defecto, configurable); la edad y el sexo solo se usan en la edad de movimiento |
| Referencia conocida | primera línea de medida, cuya longitud teclea el usuario (en m, pies-pulgadas o yardas) |
| Velocidad en m/s | muslo fijo de 0,40 m y 21 fps fijos (§3.7): no es una medida |
| Umbrales frontales | en % de la estatura en píxeles medida en la cuenta atrás |
| Distancia a la cámara | ninguna |

### 4.6 Perspectiva
- **Ninguna corrección.** Cada prueba exige su vista (de lado o de frente, de pie o sentado) por voz, texto y vídeo de demostración, y se
  comprueba el giro como en §4.3.
- Todos los ángulos son 2D en píxeles de pantalla. Las rotaciones salen de la z de ML Kit normalizada a mano.
- **No hay:**
  - gravedad ni acelerómetro (solo un `OrientationEventListener` por cuadrantes, para girar la interfaz y la pista del grabador);
  - homografía, ni referencias del entorno, ni corrección de inclinación de la cámara;
  - ningún aviso de vista oblicua durante la medición.

### 4.7 fps y cámara lenta
- **Selector de fps** (`CameraSetup.setupCamera`): ofrece 30/60/120/240 si algún rango de `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` los
  contiene, solo a 720p o 1080p.
- **Lo que no hace:** no fija `CONTROL_AE_TARGET_FPS_RANGE` en la petición ni abre una sesión de alta velocidad; solo pasa los fps a
  `MediaRecorder.setVideoFrameRate`. Lo más probable es que **los 120/240 fps no se graben de verdad** (sin confirmar en ejecución).
- **Reproductor:** 1×, 0,5× y 0,25×, con paso de ±44 ms fijos (≈ 22,7 fps, no por PTS). No lee los fps ni la velocidad de captura de los
  vídeos importados.
- **Tiempos:** de la posición del reproductor o del reloj de pared, nunca de los PTS. El análisis de vídeo exige reproducirlo en tiempo
  real.

### 4.8 Controles de calidad y avisos
- Calibración de encuadre con progreso y orientación con progreso.
- Instrucciones por voz (TTS en inglés, español, francés, italiano y portugués) y sonidos.
- Grabación mínima de 1 s.
- Aviso de movimiento incompleto con «retest recomendado» y pantalla de confirmación del flujo para repetir pruebas.
- **No hay** abstención por la confianza de los puntos, ni aviso de varias personas, ni de baja cadencia, ni de vista oblicua. La
  puntuación siempre sale.

### 4.9 Contraste con estudios publicados
- No se localizó ninguna validación independiente de Yogger (búsqueda del 20-oct). Su documentación pública no hace afirmaciones de
  precisión.
- **El modelo de base sí tiene evidencia:** el ROM de rodilla con MediaPipe tiene un MAE de 4,2–4,6° frente a IMU (M03 de
  `CANDIDATAS_Y_METRICAS.md`), y los ángulos 2D, 4–7° (Stenum, en `VIDEO_TECHNIQUE_V1.md`). No es la medida de Yogger.
- Sus rotaciones por profundidad entran en lo que M57 desaconseja automatizar sin un modelo validado: rotaciones que la imagen no ve.

---

## 5. Exportación y compartir
- **Vídeo con telemetría, por grabación de pantalla** (`PlaybackViewFragment` y `SplitScreenPlaybackFragment` con
  `MediaProjection` + `MediaRecorder`; servicio en primer plano `ScreenRecordService`):
  - captura **toda la ventana** al tamaño de la pantalla, con su densidad, en H.264 a **30 fps y 512 kbps**, con audio del **micrófono**
    en AAC: la voz en off del entrenador;
  - se ve lo que haya en pantalla: esqueleto, sectores de ángulo, trazos por velocidad con la etiqueta de m/s, dibujos, tabla, gráfico y
    los dos vídeos en lado a lado;
  - después, vista previa y guardado en la galería (MediaStore de vídeo, `recorded_<ms>.mp4`) o compartir con `ACTION_SEND`;
  - **no hay** composición por fotograma, ni plantilla 9:16, ni marca de agua, ni selección de tramo, ni resolución de origen. 512 kbps
    a resolución de pantalla da una calidad baja.
- **Instantáneas:** PNG del fotograma con la capa (`drawImageHierarchy`) y, en lado a lado, las dos imágenes apiladas. Se guardan en la
  biblioteca y en Storage, con sus medidas.
- **PDF:** informes con marca (§2.9), compartidos con `application/pdf` y guardados en Storage.
- **CSV largo** del gráfico (§3.8), compartido por FileProvider.
- **Nube:**
  - los vídeos de evaluación (`ASSESSMENTS/<id>/<id>`), los registros de vídeo (`VIDEOLOGS/`) y las instantáneas (`SNAPSHOTS/`) se suben
    a Firebase Storage con su miniatura;
  - los resultados van a Firestore;
  - el plan Lifetime promete «guardado en el dispositivo» (implementación sin confirmar).
- **Incentivo social:** un mes gratis por publicar en redes la grabación de pantalla etiquetando @yogger.io, tras verificación.

## 6. Datos e integraciones
- **Cuentas:** Firebase Auth (correo, Google, Facebook, SAML por pasarela web), App Check y Play Integrity, reCAPTCHA.
- **Firestore:**
  - `ORGANIZATIONS`, `PROFILES`, `USERS`, `SUBSCRIPTIONS`, `PROVIDERS`, `ATHLETEUPLOADS`, `ASSESSMENT_FLOWS`, `MOVEMENTAGE` (por
    atleta), `TEAMINVITES`, `SETTINGS`, `REPORTS`, `SUPPORT_TICKETS`, `FEATURED_MEDIA`, `APP_FEEDBACK` y `AFFILIATES`;
  - además, una colección **global** `MOVEMENT_AGE_DATA` con edad, sexo, lesiones, puntuaciones y la ruta del atleta
    (`MovementAgeUtilities.saveMovementAgeData`).
- **Funciones en la nube:** `createAthlete`, `inviteProviderToOrganization`, `setCustomClaims`, `redeemMobileAuthSession`,
  `createWebLoginToken`, `addSendGridContactV2`, `metaConversionEvent` y `deleteUserAccount`.
- **Seguimiento y marketing:**
  - Firebase Analytics, Crashlytics, Facebook App Events, identificador de publicidad y AdServices;
  - **eventos de conversión de Meta enviados con el correo del usuario** a la función `metaConversionEvent`. Si se cifra en el servidor
    no consta.
- **Almacenamiento local:**
  - no hay base de datos ni preferencias propias: Room llega con WorkManager y DataStore con otras bibliotecas, y todo el estado vive
    en Firestore;
  - copias locales de vídeo en `filesDir` y PDF y CSV temporales en caché.
- **Sin** BLE, wearables, Health Connect ni sensores.

## 7. Las tres preguntas del usuario
1. **Perspectiva: exige la vista y no corrige.**
   - Cada prueba pide una vista concreta: lateral derecha o izquierda, frontal, o sentado de frente.
   - Comprueba la orientación por el giro 3D de hombros y caderas: ±40° alrededor de 90° o 270° para estar de lado. La condición «de
     frente» no se puede cumplir con su fórmula y pasa por el tiempo máximo de 7 s.
   - No hay homografía, ni elipse, ni gravedad, ni corrección de inclinación.
   - Los ángulos son 2D en píxeles de pantalla. Las rotaciones usan la z de ML Kit reescalada para que el cuerpo «tenga» 2/3 de su
     anchura en profundidad: una normalización sin base geométrica.
   - En oblicuo no avisa: puntúa igual.
2. **Tracking híbrido: no existe.**
   - Yogger no sigue objetos: ni disco, ni barra, ni agarre, ni carro.
   - Todo es esqueleto de ML Kit, con o sin material. Sin barra no cambia nada, porque la barra tampoco existe.
   - La «trayectoria» es la de articulaciones del cuerpo (muñeca, cadera…) en 2D, con m/s estimados por el muslo.
3. **Vídeo con telemetría: sí, pero como grabación de pantalla.**
   - MediaProjection a 30 fps y 512 kbps del reproductor tal como se ve, con la voz en off del micrófono.
   - Incluye esqueleto, ángulos, trazos coloreados por velocidad con la etiqueta de m/s, dibujos, tablas, gráfico y lado a lado.
   - Se guarda en la galería o se comparte. Lo empuja un incentivo de «un mes gratis» por publicarlo etiquetando la marca.
   - No hay render por fotograma, ni formato vertical limpio, ni marca, ni control de calidad.
   - Conclusión para D44: el render por fotograma con Media3 que propone KPKN es claramente superior. De Yogger conviene tomar la pista
     de voz en off opcional y la idea del incentivo social.

## 8. Brechas frente a KPKN

Leídos `VIDEO_TECHNIQUE_V1.md`, `DIFERENCIADORES.md`, `BANCO_COMPLETO.md`, `INFORME_CONFIANZA_20261020.md`, `MERCADO_FIABILIDAD.md`,
`re/CANDIDATAS_Y_METRICAS.md` y `re/MY_JUMP_LAB_RE.md`. **Para D48 (saltos) y D50 (esprint) Yogger no aporta nada:** siguen basándose en
My Jump Lab.

| Función o métrica de Yogger | ¿La tiene KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| **Modo «pruebas de movilidad» protocolizadas:** hombro (flexión-extensión, abducción-aducción, 90/90), cadera sentado, cuello ×3, torácica, tronco ×2, más OHS, SLS y zancada como cribado | No: KPKN mide el ROM por repetición dentro de series de fuerza | Paquete puro nuevo, por ejemplo `domain/biomechanics/mobility/`, que cubra M57 y M58 de `CANDIDATAS_Y_METRICAS.md`. Cada prueba se define de forma declarativa (vista, señal, lado, ventana y umbrales de evento) y usa un detector «base → movimiento → pico → vuelta» sobre las señales ya filtradas y con PTS. Rotaciones con los puntos «world» métricos de MediaPipe, no con la z normalizada. Resultado por lado con «≈», confianza y abstención. Rangos de referencia con cita, nunca «riesgo». Se valida con goniómetro | MediaPipe (ya) | Media-alta | 2–3 semanas |
| Simetría entre lados (\|I−D\| en grados y en %) y consistencia entre repeticiones | Parcial: SYMMETRY solo en elevaciones; existe `romCvPct` | Índices por métrica con convención estable (M58) y umbrales propios medidos en el banco y en el teléfono; mostrarlos en el informe y en las tendencias | — | Media | Bajo (días) |
| **Gráfico ángulo–tiempo sincronizado** (tocar para saltar, ir al máximo o al mínimo) y **CSV largo** (`time_s, frame, side, joint, metric, value, units`) | No: hay barras por repetición y sidecars, sin gráfico continuo ni CSV | Canvas de Compose sobre la traza v2 con PTS reales; marcas de repetición y de fase; exportar desde los sidecars con tiempos de PTS | — | Media | Bajo (2–3 días) |
| Herramientas manuales sobre el fotograma: goniómetro de 3 puntos, referencia horizontal y vertical, línea de medida con longitud conocida, dibujo | No | Capa del reproductor con puntos arrastrables y lupa (reutiliza la interacción de calibración del disco); escala del disco calibrado o de una línea conocida; guardado en sidecar. Sirve para ejercicios sin perfil y complementa el «modo manual» de My Jump Lab | — | Media | Medio |
| Métricas tomadas **en el instante del pico** (tronco y hombro en la máxima profundidad) | Parcial: τ en el giro, profundidad en el fondo | Generalizar en `RepReport` las «métricas en el evento» por perfil | — | Baja-media | Bajo |
| Guía por voz, clip de demostración en bucle y cuenta atrás con pitidos para las pruebas | Parcial: voz de la sesión y chips de encuadre | Reutilizar el motor de TTS; clips propios grabados por KPKN | Android TTS (ya) | Baja-media | Bajo |
| Isometrías cronometradas (plancha y similares) | No: no hay isometrías | M11: el cronómetro corre solo mientras la alineación está en tolerancia (cadera en la línea hombro–tobillo) y se detiene al perder la forma. Yogger lo hace a mano y KPKN puede automatizarlo | — | Media | Bajo-medio |
| Vista 3D del esqueleto con órbita | No | Proyección ortográfica con giro por gesto en Canvas a partir de los «world» guardados (12, o 33 si se amplía la traza) | Compose; Filament (Apache-2.0) si se quiere sombreado | Baja | Medio |
| Modo «solo esqueleto» sobre fondo negro | No | Alternar la capa sobre negro: privacidad y estética pitch-black | — | Baja | Muy bajo |
| Voz en off en el vídeo exportado | No: D44 no tiene audio | En D44, pista de micrófono opcional mezclada con Media3 Transformer | Media3 1.5.1 (ya) | Baja-media | Bajo-medio |
| Informe PDF para compartir con el entrenador | No | `PdfDocument` con el resumen por ejercicio y las tendencias, sin datos inventados | SDK de Android | Baja | Medio |
| Orientación con progreso («gírate de lado») | Sí, y mejor (`ViewClassifier` con 2D + world) | Añadir anillo de progreso y voz al chip «Gírate de lado» | — | Baja | Muy bajo |
| Gestión de entrenador: organización, atletas, tareas con fecha, evaluaciones remotas, marca | No (app personal) | Fuera del alcance actual | — | Baja | Alto |
| «Edad de movimiento» | No | **No hacer:** fórmula arbitraria, 120 − media de puntuaciones, sin normas por edad | — | No hacer | — |
| m/s de una articulación por longitud de muslo | No (y es correcto) | **No copiar** el muslo fijo de 0,40 m ni los 21 fps. Si algún día se ofrece, solo con la escala del disco o con altura + «world», y con «≈» | — | No hacer | — |

**Lo que KPKN hace mejor que Yogger:**
- **Tiempo:** PTS reales, VFR, cámara lenta de galería y 60 fps verificados en el grabador. Yogger usa el reloj de pared o la posición
  del reproductor, pasos de 44 ms y 21 fps supuestos, y ofrece 120/240 fps sin fijar el rango AE.
- **Pose:** MediaPipe full sobre fotogramas decodificados (GL), con `numPoses = 2` y elección del atleta, reparación de cruces
  izquierda/derecha, Hampel, Butterworth de fase cero y relleno de huecos. Yogger captura el `TextureView` en pantalla y suaviza con un
  exponencial que depende de los fps.
- **Geometría:** aspecto corregido, clasificador de vista (lateral, oblicua, frontal), giro del móvil por gravedad y ROM «≈» en
  oblicuo. Yogger mide en 2D en cualquier vista y saca rotaciones de una z reescalada.
- **Confianza:** ALTA, MEDIA o BAJA, abstención y banderas. Yogger siempre da una puntuación; la única bandera es «movimiento
  incompleto».
- **Conteo y fases:** segmentación de series completas en 23 perfiles con parciales, fallidas, TUT y tempo. Yogger mide una repetición
  por vista y teclea las flexiones.
- **Barra y VBT:** Hough + Kåsa + Kalman + NCC y m/s calibrados. Yogger no tiene nada.
- **Honestidad:** sin m/s inventados y sin puntuaciones de «riesgo». Yogger muestra m/s con un muslo supuesto y tiene umbrales que
  contradicen su propia ayuda.
- **Privacidad:** el vídeo no sale del teléfono. Yogger sube vídeos y resultados a Firebase, guarda una colección global de la edad de
  movimiento y envía el correo en eventos de conversión de Meta.
- **Exportación (plan D44):** render por fotograma en 9:16 o en el formato original. Yogger graba la pantalla a 512 kbps.
- **Proceso:** análisis en segundo plano con WorkManager. Yogger obliga a reproducir el vídeo en tiempo real.

## 9. Índice de evidencias
Árbol descompilado en `C:\kw\re\yogger\jadx\` y modo SIMPLE en `C:\kw\re\yogger\jadx_simple\`. Ambos se borraron al terminar y se
reproducen con los scripts de `C:\kw\re\_scripts\yogger\`: `safe_extract.py`, `split_decode.py`, `dex_tool.py`, `YgDump.java` +
`run_dump.ps1`, `YgDumpMode.java` + `run_dump_mode.ps1`, `tflite_shapes.py`, `method_index.py` y `redact_keys.py`.

| Tema | Evidencia |
|---|---|
| Identidad e inventario | `C:/kw/re/evidencias/yogger-re-20261020/xapk_inventory.json`, `base_apk_listing.txt`, `library_versions.txt`, `dex_classes.txt` |
| Manifiesto, cadenas y navegación | `resources/AndroidManifest.decoded.xml`, `resources/strings_en.xml` (claves de cliente redactadas), `resources/app_strings_filtered.tsv`, `resources/mobile_navigation.xml` |
| Modelos y nativo | `models_info.txt`, `mlkit_graphs_and_versions.txt`, `libxeno_native_jni_exports.txt`, `libxeno_native_readelf.txt` |
| Catálogo de métricas extraído | `metrics_catalog_extracted.json` (umbrales, puntos, rúbricas, trackers y fórmulas, descritos con palabras propias) |
| Índices de clases y métodos | `jadx_app_class_index.txt` (892 archivos con número de líneas), `key_method_signatures.txt` (3 844 firmas), `key_method_signatures_simple_mode.txt` |
| Cliente de pose y bucle | `io/yogger/Yogger/camera/PoseDetectionClient.createAccurateStreamDetector`; `PoseDetectorLoop.detectPose(cropOption)`; `PoseDetectorLoop$detectPose$1$1$1$1$1` (tiempo = lectura − inicio, en µs) |
| Herramientas de ángulo | `defpackage/AnalysisTool` (`angle180`, `angle360`, `distanceBetweenPoints`); `KneeFlexion`, `HipFlexion`, `ElbowFlexion`, `ShoulderFlexion`, `ShoulderAdduction`, `ShoulderRotation`, `HipAdduction`, `HipRotation`, `HipDrop`, `KneeValgus`, `AnkleDorsiflexion.calculateReferencePoint`, `NeckAPFlexion`, `NeckLateralFlexion`, `NeckRotation`, `PelvicRotation`, `UpperTrunkRotation`, `TrunkAPFlexion`, `TrunkLateralFlexion`; `analysistools/Ankle/ShinAngle`, `analysistools/Shoulder/ShoulderSymmetry`; `defpackage/unsignedAngleBetweenXZ` (`signedAngleBetweenXZ`); `AnalysisToolManager` (`frameRate = 21`, selección por región, `set3DLandmark`) |
| Postprocesado | `utilities/AnatomicalPoseProcessor` (`SMOOTHING_ALPHA 0.45`, `LOW_CONFIDENCE_ALPHA 0.25`, `FAST_MOTION_ALPHA 0.82`, `FAST_MOTION_BODY_SCALE_RATIO 1.4`, `MAX_SEGMENT_HISTORY 30`, `SEGMENT_ERROR_TOLERANCE 0.45`, `SEGMENT_CORRECTION_STRENGTH 0.2`); `AnatomicalCalibration.getBodyScale`; `AnatomicalPoseGeometry.makeBodyCoordinateSystem`; `BodySegment`; `AnnotationView.set3DPoints`, `drawSmoothLine`, `calculateThighLength`; `ui/video_analysis/PoseHistory` (`calculateDistance` con 0,244; `updateThighLength`; `validateAndGetCurrentPosition`) |
| Calibración, orientación y eventos | `assessments/Assessment` (`calibrateAssessment`, `isUserStillAndFacingCamera`, `calibrateFacing`, `checkFacingOrientation`, `handleTimer`, `startStateTimer`, `accumulateUserHeight`, `baselineThreshold 20`); `DirectionalTracker`; `FrontalRepTracker`; `PeakTracker`; `SignalQualityFlag`; `AssessmentObjectKt.signalSeverity` |
| Puntuación | `AssessmentObject.calculateScores`, `getSaveAssessmentObject`; `ComponentScoringEvaluator.evaluateSymmetry` y `evaluateQuality`; `PointsPerRep`; `RubricScoreColor`; `ScoreColor`; `media/Snapshots/SnapshotScoring` |
| Pruebas | `assessments/functional_movements/{overhead_squat,lunge,posture}/models/*` y `*HelpersKt` (`calcPosture`, `calcKneeValgus`, `calcFeetInversion`); `assessments/performance/single_leg_squat/*`; `assessments/range_of_motion/*` (`calcShoulderFlexion`, `calcShoulderAdduction`, `calcShoulderRotation`); los `run()` en `jadx_simple/...*.SIMPLE.java`; `AssessmentUtilities.createAssessmentObject` (14 tipos) |
| Edad de movimiento | `assessments/movement_age/models/MovementAgeScoring` (`calculatePushup`, `calculatePlank`, `logarithmicScore`, `calculateMovementAge`, `calculateQuestionnaireScore`); `MovementAgeConstants.MOVEMENT_SCREEN_WEIGHTS`; `movement_age/utils/MovementAgeUtilities` (`MOVEMENT_AGE_DATA`); `performance/PushUpTestFragment` (selector 0–200) |
| Cámara y grabación | `camera/CameraSetup.setupCamera` (30/60/120/240 sin rango AE), `CaptureSession.createPreview` (1920×1080), `RecordingManager.prepareVideoRecorder` (`setVideoFrameRate`), `OrientationController` |
| Reproductor y herramientas | `ui/video_analysis/VideoViewFragment` (`frameRateMillis = 44`, `toggleTracking`, `detectPose`, `setMeasuresForGraph`, `drawImageHierarchy`); `PlaybackViewFragment` (velocidades 1/0,5/0,25; grabación de pantalla a 512 kbps y 30 fps); `SplitScreenPlaybackFragment` (`setPlaybackLocked`); `GraphFragment` (`snapToMaxGraph`, `snapToMinGraph`, `exportDataToCsvLongFormat`); `ThreeDRenderer`; `utilities/DrawingView` (`handleMeasureLine`, `drawAngleLabels`); `DrawAngleType`; `DistanceMethod`; `DistanceUnit` |
| Informes | `reports/ReportGenerator` (`PdfDocument`), `AssessmentPDFGenerator.drawPdfChartBitmap`, `ReportComponentType`, `AssessmentReportExporter.loadOrgBranding` |
| Datos, cuentas y marketing | `utilities/FirebaseUtilities`; `defpackage/MediaUtilities` (rutas `SNAPSHOTS/`, `VIDEOLOGS/`, `ASSESSMENTS/`); `analytics/MetaConversionTracker.track` (campo `email`); `subscription/*`; `upgrade/models/FeatureGate`; `tasks/AthleteTaskManager` |

**Sin confirmar:**
- si los modos de 120/240 fps graban de verdad a esa cadencia;
- la convención de signo de la movilidad de tobillo en la zancada;
- la lógica exacta de `isUserStillAndFacingCamera` (descompilación defectuosa);
- que la orientación «de frente» siempre agote los 7 s (análisis estático, no ejecutado);
- el origen de las constantes 0,244, 0,40 m y 21 fps;
- si los programas en casa y las metas existen en otras plataformas o detrás de configuración remota;
- las versiones de MPAndroidChart, Rajawali, ZoomLayout, mp4parser, commonmark, AndroidSVG, RevenueCat y del SDK de Facebook;
- si el correo enviado a `metaConversionEvent` se cifra en el servidor;
- si existe un split arm64 en Play;
- cómo guarda en el dispositivo el plan Lifetime.
