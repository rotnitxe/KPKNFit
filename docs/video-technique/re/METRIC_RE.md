# Metric – Barbell Tracker 2.3.2 · ingeniería inversa (20-oct-2026)

Análisis **estático** del XAPK que aportó el usuario. No se ejecutó ni se instaló la app; no se usaron teléfono, emulador, git ni
Gradle. Sala limpia: las funciones, fórmulas y algoritmos se describen con palabras propias; no se copia código descompilado ni se
extraen modelos o recursos para reutilizarlos. Los umbrales y parámetros se citan como hechos de comportamiento; los nombres de clase
o función aparecen solo como evidencia.

**Archivo.** `Metric+-+Barbell+Tracker_2.3.2_APKPure.xapk`, 24.197.869 B, SHA-256
`48dfb2d023446f7db57ff456f9777a6e59eda8d0d738c8af6ca4eb1f1c51dab8`.

**Ojo con las versiones.** Los estudios publicados (Taber, Trowell, Renner, Puppyn) evaluaron la app de **iOS** (v0.5.4, v0.6.0,
v2.3.1 y v4.8.1). Esta es la app de **Android** de 2026 («Founding Android Member 2026», mensaje de beta). Que comparta el núcleo
C++ con iOS está **sin confirmar**; las relaciones con esos errores son hipótesis.

---

## 0. Lo esencial

| # | Hallazgo | Por qué importa a KPKN |
|---|---|---|
| 1 | **Sin ML.** Detector geométrico de elipses propio en C++ (`libmetricvisionlibrary.so`), acelerado con Vulkan (25 shaders SPIR-V) y con respaldo en CPU con NEON: Canny en GPU, componentes conexas por unión-búsqueda en GPU, arcos por cuadrante de gradiente, candidatos por tríos de arcos con un invariante proyectivo, ajuste ElliFit y refino del aro exterior. | Demuestra que basta un detector clásico, con algoritmos publicados, para **D47** (encontrar el disco en todo el fotograma sin pose), sin Ultralytics ni AGPL. |
| 2 | **Seguimiento por detección.** Detecta en cada fotograma, agrupa elipses por similitud y elige el grupo con más fotogramas. No hay Kalman, KCF ni NCC dentro de la serie. La semilla es automática: no se toca nada. | Recuperar el disco es trivial (se redetecta), pero no hay modelo de movimiento ni puerta estadística: KPKN es más robusto ante oclusiones. |
| 3 | **Vertical 3D por gravedad.** Distancia al disco por fotograma a partir de su tamaño aparente y de los intrínsecos de la cámara; posición 3D; proyección sobre el vector de gravedad del móvil. Corrige cabeceo, alabeo y cambios de profundidad. | Refuerza **D42**. KPKN usa elipse, paralaje y giro; le faltan la profundidad por fotograma y la gravedad en 3D. |
| 4 | **Exposición contra el desenfoque.** Escalera de obturación de 1/360 a 1/100 s con techos de ISO de 800 a 4000, elegida según la red eléctrica de 50 o 60 Hz; medición de la exposición sobre el disco y bloqueo durante la serie. | KPKN calibra solo con fotogramas quietos porque el desenfoque infla los bordes. Esto ataca la causa en el grabador. |
| 5 | **Señal.** Spline cúbico de suavizado (csaps, MIT) sobre tiempos en ms, remuestreo uniforme a 60 Hz y velocidad por diferencias. La velocidad pico se «empuja» un 40–60 % hacia un ajuste menos suavizado. | Esa corrección es una heurística que puede sesgar el pico: no copiarla. |
| 6 | **Repeticiones por plantilla.** Correlación cruzada de la velocidad con una plantilla de giro de 27 muestras (≈ 0,45 s). La fase concéntrica es el tramo con v > 0,1 m/s y la MCV se mide solo en ese tramo. | Explica de forma verosímil el sesgo positivo de MCV publicado (hipótesis). KPKN mide desde el inicio real del movimiento. |
| 7 | **Exportación con telemetría.** Media3 Transformer 1.9.2, recorte a 9:16 y una superposición pintada en Canvas en cada fotograma: trazo de puntos (repetición actual en verde, previas en gris), marcador de posición y UNA tarjeta translúcida con el logo, «carga · rep/total» y el valor grande de la métrica con un conteo animado de 350 ms. Sin gráficos, sin barras, sin otros formatos y sin hoja de compartir. | La brecha **D44** existe, pero el listón es bajo: KPKN puede superarlo con trazo continuo coloreado, barras por repetición, pérdida de velocidad, varios formatos y una tarjeta final. |
| 8 | **En vivo.** Visión en tiempo real en la GPU con voz por repetición (TTS a 1,3×, por el canal de alarma: suena aunque el móvil esté en silencio), campana o zumbador por objetivo, alerta de pérdida de velocidad y metrónomo de 1 s. | Encaja con **D49**. |
| 9 | **Sin pose ni tracking híbrido.** Solo discos circulares: barra vacía, mancuernas, discos de 12 lados y peso corporal no se siguen. | **D42 y D43** siguen siendo diferenciadores de KPKN. |
| 10 | **Nube obligatoria.** Cuenta de Firebase; series en Firestore; vídeo en Storage (Pro); informes de fallos con el vídeo de la última serie. | KPKN gana en privacidad: el vídeo nunca sale del teléfono. |

---

## 1. Identidad

### 1.1 Paquete, archivos y firma
| Elemento | Valor |
|---|---|
| Paquete | `com.coreadvantage.metric`, versión **2.3.2** (código 74), minSdk 32 (Android 12), targetSdk y compileSdk 37 |
| Desarrollador | Core Advantage («© 2026 Core Advantage»); web `metric.coach` |
| Firma | APK Signature Scheme **v3** verificada (apksigner, estático); 1 firmante «CN=Android, O=Google Inc.» (clave de Play App Signing, RSA 4096), SHA-256 del certificado `cfc2833404a873c5367ee7a7bf04453a4c631059d844cdf3e1850387dd8bd099`; sello de origen de Google Play |
| Protección | `com.pairip.application.Application` y `com.pairip.licensecheck` (protección automática de Google Play) |
| XAPK | 4 entradas: APK base, `config.arm64_v8a`, `config.xxhdpi` y `manifest.json` (sin icono) |

| Archivo | Tamaño (B) | SHA-256 |
|---|---:|---|
| `com.coreadvantage.metric.apk` | 21.958.046 | `f543daa9758b532b9ba9076036ab4ca6a70c7784fba1bc616b90a8942095d39c` |
| `config.arm64_v8a.apk` | 2.147.299 | `8ca8cc7eabf89997619531a264c3f6441f3f6b83dae31982b986a4e6a667f2f8` |
| `config.xxhdpi.apk` | 90.453 | `fc55ce6215d4e27b4c6c75317470650e765cb16051afbf6d316ff4418b595cef` |
| `classes.dex` / `classes2.dex` | 10.452.324 / 3.937.924 | `f0b128ea…09fca` / `78445e79…969f5` |
| `lib/arm64-v8a/libmetricvisionlibrary.so` | 2.053.592 | `d4a39615aa616f3089f87f08e018754a8aa53b3eaf36f6a98d66e371abdf0e56` |
| `lib/arm64-v8a/libapp_tests.so` | 2.562.704 | `e7d8a82a02c3854b0eee23d151abd79625b13150ccc318bf8d0e984072a0e7ea` |

Los SHA-256 completos de los DEX, las bibliotecas, los shaders y los sonidos están en
`C:/kw/re/evidencias/metric-re-20261020/hashes-sha256.txt`.

### 1.2 Arquitectura
- **Interfaz en Kotlin y Jetpack Compose:** Material 3 1.5.0-alpha19, Compose UI 1.12.0-alpha02, Navigation 3 (1.1.1), Koin,
  corrutinas, kotlinx.serialization y kotlinx.datetime. Solo vertical (`screenOrientation="portrait"`).
- **R8 agresivo:** 17.344 clases en 2 DEX; 8.178 reempaquetadas en el paquete por defecto; solo 146 conservan
  `com.coreadvantage.*` (modelos serializables, rutas de navegación, puentes JNI, workers y widgets).
- **Motor de visión en C++:** `libmetricvisionlibrary.so`, compilada con el NDK r29-beta4 (clang 21 con LTO, PGO y BOLT).
  Depende de `libvulkan`, `libcamera2ndk`, `libmediandk`, `libaaudio`, `libandroid` y `liblog`. La biblioteca exporta sus símbolos
  C++ en la tabla dinámica (2.937 definidos), lo que hizo muy legible el análisis.
- **Cámara y grabación nativas:** la ruta principal abre la cámara con Camera2 NDK, codifica con MediaCodec NDK y graba el audio con
  AAudio, todo dentro de la biblioteca. Hay una segunda ruta en la que Kotlin entrega los planos YUV (`CvBridge.nativeProcessFrame`):
  CameraX 1.5.0 está incluido.
- **Error de empaquetado:** `libapp_tests.so` es una compilación de pruebas con GoogleTest de la misma biblioteca, enviada en el APK
  de producción (16 pruebas: shaders, validación de luminancia y una de muestra).
- **ABI:** solo `arm64-v8a`.

### 1.3 Bibliotecas nativas
| Biblioteca | Tamaño | Para qué |
|---|---:|---|
| `libmetricvisionlibrary.so` | 2,05 MB | Propia: cámara, grabación, preprocesado en GPU o CPU, detector de elipses, seguimiento, posprocesado, métricas, modo en vivo |
| `libapp_tests.so` | 2,56 MB | La misma biblioteca con GoogleTest (pruebas; no tiene función en producción) |
| `libcrashlytics*.so` (4) | 1,1 MB | Informes de fallos nativos (Crashlytics NDK) |
| `libimage_processing_util_jni.so`, `libsurface_util_jni.so` | 37 KB | CameraX |
| `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so` | 17 KB | Compose y DataStore |

### 1.4 Modelos de ML y shaders
- **No hay ningún modelo de ML:** ni TFLite, ni ONNX, ni ML Kit, ni MediaPipe, ni pose. Todo es geometría clásica.
- **25 módulos SPIR-V** en `assets/shaders/` (23 de cómputo y 2 gráficos), con nombres de depuración intactos. Grupos de trabajo de
  16×16 salvo los de búfer (16×1) y `renderContours` (256).

| Shader | Qué hace (de sus nombres, entradas y constantes) |
|---|---|
| `grayscale`, `grayscale_r32f` | Luminancia como media de R, G y B |
| `downsample` | Reducción con muestreo bilineal |
| `gaussianBlurX/Y` y `…Small` | Gauss separable: 21 tomas (σ = 5), 13 tomas (σ = 2) y 7 tomas (σ ≈ 1,5) |
| `addWeighted` | Máscara de enfoque: salida = 4·imagen − 3·desenfocada |
| `absoluteDifference` | Diferencia absoluta entre dos fotogramas (movimiento) |
| `sobel`, `sobel_r32f` | Gradientes 3×3 en x e y |
| `nonMaximaSuppression…` | Supresión de no máximos en 4 direcciones (bandas de π/8 con atan2) |
| `initialization` / `…LowContrast` | Histéresis de Canny sobre el módulo normalizado: 0,04/0,16, o 0,03/0,12 para discos de poco contraste |
| `initializationBuffer`, `reduction`, `analysis` | Etiquetado de componentes conexas por unión-búsqueda en la GPU, con operaciones atómicas, en varias pasadas |
| `splitIntoArcGroups` | Reparte los puntos de borde según el cuadrante del gradiente en dos búferes (`edges13`, `edges24`) |
| `gradientHistogram` | Histograma del módulo del gradiente (diagnóstico) |
| `buffer2Image`, `renderContours` | Visualización de depuración |
| `encoderBlit.vert/.frag` | Copia del fotograma YUV de la cámara a la superficie del codificador |
| `ycbcrCanary` | Prueba de conversión YCbCr para validar la GPU del dispositivo |

Los `*.glsl` de la misma carpeta son los shaders de efectos de Media3.

### 1.5 Bibliotecas de terceros (versión · licencia)
Versiones de `META-INF/*.version`, de los `*.properties` y de las cadenas. Licencias según cada proyecto público: verificar antes de
usar.

| Biblioteca | Versión | Licencia | Uso |
|---|---|---|---|
| Eigen (dentro de la biblioteca nativa) | — | MPL-2.0 | Álgebra del spline |
| csaps-cpp («cubic smoothing spline», espdev) | — | MIT (según su repositorio; verificar) | Suavizado de trayectorias |
| ElliFit (Prasad, Leung y Quek, 2013) | — | Método publicado; el origen del código está sin confirmar | Ajuste de elipses |
| Detector por arcos con invariante proyectivo (linaje Fornaciari 2014 y Jia et al. 2017) | — | Implementación propia o derivada, sin confirmar | Candidatos de elipse |
| GoogleTest | — | BSD-3-Clause | Solo en `libapp_tests.so` |
| AndroidX Media3 (ExoPlayer, Transformer, Effect, Muxer) | 1.9.2 | Apache-2.0 | Reproductor y exportación |
| CameraX | 1.5.0 | Apache-2.0 | Ruta de cámara secundaria |
| Compose, Material 3, Navigation 3, Glance 1.1.1, Room 2.6.1, WorkManager 2.10.1, DataStore 1.1.7, Biometric 1.1.0, Credentials 1.6.0-beta01 | — | Apache-2.0 | Interfaz y plataforma (Room solo lo usa WorkManager) |
| Koin, OkHttp 4.12.0, Coil 3, kotlinx.* | — | Apache-2.0 | DI, red, imágenes |
| Firebase: Auth 23.2.0, Firestore (gRPC-OkHttp 1.62.2), Functions, Storage, Crashlytics NDK, App Check, Analytics 22.4.0, Sessions, Remote Config | — | SDK propietarios de Google | Cuenta, datos, nube, telemetría |
| Play Billing 8.3.0, Play Integrity 1.3.0, reCAPTCHA 18.6.1, Google ID 1.2.0 | — | Propietarias | Pagos, integridad, inicio de sesión |
| Fuentes: Poppins Medium (Google Fonts descargable), Roboto Medium (dígitos), Material Symbols | — | OFL-1.1 / Apache-2.0 | Tipografía |

### 1.6 Permisos y sensores
- **Cámara y audio:** `CAMERA` y `RECORD_AUDIO` (el vídeo grabado lleva audio AAC). `camera.any` requerido; Vulkan opcional.
- **Red y cuenta:** `INTERNET`, `ACCESS_NETWORK_STATE`, mensajería de Firebase, `READ_GSERVICES`.
- **Pagos:** `BILLING`, `CHECK_LICENSE`, referidor de instalación.
- **Publicidad y medición:** `AD_ID` y `ACCESS_ADSERVICES_*` (de Analytics; la app no muestra anuncios).
- **Otros:** `POST_NOTIFICATIONS`, `WAKE_LOCK`, `FOREGROUND_SERVICE` (de WorkManager), `RECEIVE_BOOT_COMPLETED`, `USE_BIOMETRIC` y
  `USE_FINGERPRINT` (uso sin confirmar), almacenamiento heredado.
- **Consultas:** `TTS_SERVICE` (voz por repetición).
- **Sensores:** `MotionController` registra **gravedad** y **aceleración lineal** a ritmo de juego, o el acelerómetro con un
  filtro de paso bajo si no hay sensor de gravedad. Envía a la biblioteca la gravedad normalizada y el módulo de la aceleración
  de forma periódica (cada ≈ 100 ms, sin confirmar).
- **Sin** BLE, wearables ni Health Connect.

---

## 2. Inventario completo de funciones, pantalla por pantalla

Rutas de navegación (clases conservadas): `AuthenticationView` (Welcome, SignUp, RoleSelection, UnitSelection, ProfileSetup,
DeviceDiagnostic, BetaMessage, Onboarding), `MVPScreen` (Home, Account, Appearance, AthleteProfile, CreateAthlete,
DefaultTrainingPreferences, ExerciseDetail, ExerciseList, ExercisePreferences, ImportProcessing, Membership, MetricHowTo,
PerformanceWidgets, Profile, ProfileSetup, Programming, Roster, SetHistory, SetReview, SetSetup, Settings, TemplateBuilder,
VideoCapture, VideoPlayback, VideoSelector, WebDoc, WorkoutHistory, WorkoutHistoryList, WorkoutSummary) y `WorkoutScreen`
(LiveWorkout, VideoCapture, VideoSelector, ImportProcessing, SetReview, VideoPlayback, WorkoutSummary). «Pro» = suscripción.

### 2.1 Alta e incorporación
- **Cuenta obligatoria:** correo y contraseña (con indicador de fuerza y recuperación) o Google mediante Credential Manager. Las
  cuentas de iOS con un proveedor no confirmable en Android se borran por correo.
- **Rol:** atleta, entrenador o ambos. **Unidades:** kg o lb. **Perfil:** nombre, apellido, foto, fecha de nacimiento, sexo y peso
  corporal; se puede saltar.
- **Diagnóstico del dispositivo:** abre la cámara un momento para probar la GPU. Resultados: compatible del todo; con respaldo en
  CPU («las grabaciones en vivo pueden necesitar más posprocesado»); limitado; o desconocido. Hay una lista pública de dispositivos
  compatibles.
- **Aviso de beta** con informe de fallos dentro de la app.

### 2.2 Inicio
- Saludo, series y entrenamientos recientes y accesos «Grabar la primera serie», «Importar» y «Empezar un entrenamiento».
- Tarjetas de gráficos («Toca para crear un gráfico»).
- Banners: entrenamiento en curso, mejora a Pro e historial limitado («mostrando los últimos N días»).

### 2.3 Serie rápida (`SetSetup`, «Quick set»)
- **Datos:** ejercicio, carga y tamaño del disco: estándar de **45 cm** o diámetro propio en cm.
- **Grabar** o **importar** un vídeo de la galería: miniatura, fecha, aviso de fps («Buena» o «Baja») y límite de 3 minutos.
- **Consejos de grabación («How to record»):** de lado, a 60 fps, discos circulares y móvil estable (trípode).
- **Aviso de barra vacía** antes de grabar, con «No volver a mostrar».

### 2.4 Grabación en vivo (`VideoCapture`)
- **Vista previa con guía en el centro:**
  - «Buscando discos» → «Discos encontrados» / «Listo para grabar»;
  - «Demasiado cerca» o «Demasiado lejos», «Ajusta el ángulo» (de lado a los discos), disco fuera del encuadre;
  - «Poca luz» (con la cámara frontal, propone pasar a la trasera);
  - «Estabiliza el dispositivo» / «Dispositivo estable», «Mantén el móvil vertical», «Gira el móvil a vertical» (solo graba en
    vertical).
  - Si se graba con avisos activos: «¿Grabar igualmente?», con el motivo.
- **Ajustes de captura:**
  - **Retroalimentación:** Apagada, Leer (dice el valor de cada repetición), Objetivo (campana o zumbador por encima o por debajo del
    objetivo) o Pérdida (anuncia la pérdida en % al llegar al umbral). Métrica: velocidad media o pico. Objetivo y umbral de pérdida
    editables.
  - **Tempo:** metrónomo de 1 s. **Volumen** propio.
  - **Tamaño del disco** y **discos de poco contraste** («para discos negros contra un rack oscuro: Metric busca más el borde»).
  - **Depuración:** forzar la exposición (ISO máximo y obturación).
  - Cambiar de cámara.
- **Grabación:** «Grabando · Empieza a levantar». Durante la serie, voz o sonidos por repetición. Al parar: «Analizando reps… N %»,
  con «No cierres la app».
- **Sin GPU suficiente:** «Grabación en tiempo real no disponible: graba con la cámara del sistema e importa», o graba y procesa
  después.
- **Fallo de arranque:** «La cámara no pudo iniciarse», con un informe que envía diagnósticos de la GPU.

### 2.5 Importación (`VideoSelector`, `ImportProcessing`)
- **Comprobaciones:** fps < 27 → rechazo; < 45 → aviso de precisión; resolución < 720p → aviso; duración ≤ 3 min; Dolby Vision →
  rechazo («exporta en SDR»); vídeos ilegibles de DCIM, con su explicación.
- **Estados:** copiando → «Preparando vídeo…» → «Analizando… N % · tiempo» → «Finalizando…» → «Guardando serie…». Se puede cancelar.
- **Errores:** «No se detectó la barra», «tu dispositivo no admite los gráficos necesarios», y «Informar del dispositivo a Metric».

### 2.6 Revisión de la serie (`SetReview`)
- **Vídeo** con la superposición: trazo de puntos y marcador, igual que la exportación (§5.1). Se puede ampliar.
- **Gráficos:** «Traza de velocidad» y gráfico por repetición con métrica principal y secundaria intercambiables.
- **Tabla por repetición** con columnas configurables; ocultar o mostrar una repetición, lo que recalcula los agregados.
- **Destacados:** mejor repetición, última, más baja y media de la serie. Arriba, «Fatiga» o «Consistencia» según la métrica (§3).
- **Insignia de récords:** nuevo récord, «casi» (≥ 95 % del mejor), referencia inicial y «sin reps detectadas».
- **Tendencia:** comparación con la media de 6 semanas en el mismo ejercicio y la misma carga.
- **Avisos de la serie:** ángulo, disco pequeño o grande, barra fuera de cuadro, sin reps, fps < 50, fotogramas perdidos y proceso
  largo (§4.11). «Toca para consejos».
- **Acciones:**
  - exportar el vídeo;
  - guardar en la biblioteca (Pro);
  - sincronizar con la nube (Pro);
  - favorito;
  - informar de un fallo (adjunta el vídeo y los datos de depuración de la última serie);
  - borrar el vídeo;
  - editar la serie (RPE o RIR; otros campos sin confirmar).
- **«Siguiente carga»** en el flujo del perfil de velocidad.

### 2.7 Entrenamiento (`LiveWorkout`, `WorkoutSummary`)
- **Ejercicios con series:** cada serie se graba, se importa o se marca a mano. Columnas de reps, carga, distancia y unidad; RPE o
  RIR por serie.
- **Superseries; temporizador de descanso** en línea y principal; notas para atletas; cambiar o borrar un ejercicio.
- **Varios atletas:** «Grabando para…», con cambio de atleta.
- **Modo del perfil:** grabación en vivo, importar vídeos o entrada manual («sin vídeo, sin datos de velocidad»).
- **Perfil de velocidad:** se elige ejercicio, se introduce un 1RM estimado y «Calentamiento automático» planifica las cargas de las
  series de calentamiento con las que construye el perfil carga-velocidad y estima el 1RM. Los porcentajes del plan están sin
  confirmar: no se halló el generador en el código descompilado.
- **Resumen al terminar:** series por ejercicio, récords (Pro) y «tendencias de velocidad» (más rápido o más lento; Pro). Guardar como
  programa, repetir el entrenamiento o borrarlo.

### 2.8 Rendimiento
- **Historial** de series (busca por ejercicio o carga) y de entrenamientos. La versión gratuita bloquea lo anterior a 7 días.
- **Gráficos (`PerformanceWidgets`):** una métrica de un ejercicio en las últimas 6 semanas (mejor carga, 1RM estimado…); 2 gráficos
  en la versión gratuita.
- **Análisis del ejercicio:** perfil carga-velocidad con los puntos, la recta, una línea discontinua «1RM Velocity: x m/s» y el 1RM
  estimado.
- **Análisis de tendencia.**

### 2.9 Programas (`Programming`, `TemplateBuilder`)
- Programas de muestra y del entrenador; constructor con ejercicios, series, objetivos y notas.
- Compartir con el equipo, con atletas concretos o por enlace directo; archivar; 2 programas en la versión gratuita.

### 2.10 Entrenador y equipos
- **Modo entrenador:** 2 atletas en el plan básico; los planes Pro Coach se venden en la web.
- **Plantilla del equipo:** atletas, entrenadores, gestores, miembros y pendientes.
  - Alta por correo: si la persona no tiene cuenta, se crea y recibe un correo.
  - Invitaciones a la organización: los entrenadores verán los datos de entrenamiento.
- **Plan familiar. Clasificación del equipo** («el top 10 vive aquí»).
- **Volver a atleta:** borra el equipo; los datos de los atletas se conservan.

### 2.11 Ajustes
- **Vídeo:**
  - guardar en la app (Pro);
  - exportar automáticamente a la galería;
  - recorte automático para la exportación y su margen;
  - borrado automático de vídeos antiguos;
  - nube (Pro): retención, «favoritos para siempre», «conservar vídeos con récord», cuota usada; gestionada por el entrenador si
    pertenece a una organización.
- **Apariencia:** tema, idioma y pantalla siempre encendida.
- **Preferencias por ejercicio:**
  - **modo de análisis:** la métrica principal, por defecto la velocidad media; es la que muestra la exportación;
  - métricas principal y secundaria;
  - columnas de la tabla: por defecto velocidad media, tempo excéntrico y ROM;
  - esfuerzo: RPE, RIR o nada;
  - **velocidad del 1RM** (m/s).
- **Cuenta:** membresía, guía de uso, notas de versión, «Validación de Metric», equipos, borrar la cuenta y cerrar sesión. El muro
  de pago muestra «Validado por investigación revisada por pares» con logos de PLOS, IJSC, Hrčak y PubMed.

### 2.12 Suscripciones (`Membership`, muro de pago)
| Plan | Qué incluye |
|---|---|
| Básico (gratis) | Seguimiento de la barra y series sin límite; historial de 7 días; 2 gráficos y 2 programas; métricas gratuitas: velocidad media, ROM y tempo excéntrico |
| Pro (mensual o anual, 7 días de prueba en el anual) | Retroalimentación en tiempo real, 1RM estimado y perfiles RPE, de velocidad y de potencia, métricas avanzadas y tendencias, «monitorización de la fatiga», avisos de récord, historial completo, biblioteca de vídeo, 5 GB en la nube y programas sin límite |
| Fundador | Pro de por vida para los primeros usuarios de Android |
| Entrenador | En la web (`metric.coach`): más plazas, Pro para el equipo y «Metric Jump» |

Los derechos (`EntitlementMap`) distinguen **vbt, jump y sprint**: hay productos de saltos y esprint fuera de esta app.

### 2.13 Widgets y trabajos en segundo plano
- **Widgets Glance:** inicio rápido (Push, Pull, Legs, Cardio) y estadísticas semanales. El manifiesto los declara desactivados; si se
  activan en tiempo de ejecución está sin confirmar.
- **Workers:** `CloudVideoUploadWorker` (subida a Storage) y `VideoCleanupWorker` (borrado por antigüedad).

### 2.14 Catálogo de ejercicios
≈ 235 ejercicios integrados: barra, mancuerna, polea, máquina, kettlebell, peso corporal, saltos con barra o hexagonal, lanzamientos
de balón, pliometría, carreras, cargadas, arrancadas, cardio (bici, remo, esquí y elíptica) y transporte. Tipos: Barbell, Bodyweight,
Cable, Dumbbell, Kettlebell, Machine u Other. «Track velocity» se activa por ejercicio, pero la velocidad exige siempre un disco
circular.

---

## 3. Catálogo de métricas

**Base:** posición vertical del centro del disco en metros (§4.6), remuestreada a 60 Hz con un spline (§4.7). Cada repetición tiene
índices de concéntrica (`startIndex`–`endIndex`), de ROM (`romStartIndex`–`romEndIndex`) y de excéntrica. Las ocultas no cuentan.
La potencia se guarda **por kg** y se multiplica por la carga de la barra (no por el peso corporal).

| Métrica | Definición (palabras propias) | Unidad | Filtros y umbrales | Cuándo se muestra y cuándo se abstiene | Plan |
|---|---|---|---|---|---|
| ROM | Máximo de la posición en el segundo posterior al fin de la concéntrica menos el mínimo en el segundo anterior a su inicio | m (cm en pantalla) | La rep exige > 0,10 m. Si queda < 60 % de la media de las demás, se elimina como parcial | Siempre que haya rep | Gratis |
| Velocidad media (MCV) | Desplazamiento neto ÷ duración del tramo concéntrico con v > 0,1 m/s | m/s | Duración > 1 ms; \|v\| ≤ 10 | Siempre que haya rep; sin calibración por defecto usa 45 cm | Gratis |
| Velocidad pico | Máximo de la velocidad a 60 Hz en la concéntrica, más un «empuje» hacia un ajuste menos suavizado (§4.7) | m/s | < 10 m/s | Igual | Pro |
| Tiempo al pico | Inicio de la concéntrica → instante del pico | s | Se recalcula con el empuje | Igual | Pro |
| Tempo concéntrico | Duración de la concéntrica | s | — | Igual | Pro |
| Tempo excéntrico | Duración de la excéntrica | s | La excéntrica cuenta si su recorrido difiere < 40 % del concéntrico | Si se encuentra la excéntrica | Gratis |
| Pausa arriba | Fin de la concéntrica anterior → inicio de la excéntrica, alargada mientras \|v\| ≤ 0,05 m/s | s | Tope de 10 s | Si hay rep previa | Pro |
| Pausa abajo | Fin de la excéntrica → inicio de la concéntrica | s | ≥ 0 | Igual | Pro |
| TUT | Concéntrica + excéntrica + pausa arriba + pausa abajo | s | — | Igual | Pro |
| Pausa total | Pausa arriba + abajo | s | — | Igual | Pro |
| Potencia media | 9,807 × MCV × carga: supone fuerza igual al peso; vale si la rep empieza y acaba casi en reposo | W | — | Con carga | Pro |
| Potencia pico | Máximo, en la concéntrica, de la variación de energía cinética más potencial por unidad de tiempo, × carga | W | Δt > 0,1 ms | Con carga | Pro |
| «Potencia excéntrica» | Energía cinética disipada al frenar: (v_mín² − v_fin²)/2 ÷ Δt × carga, con v_fin = última muestra con \|v\| ≤ 0,025 | W | Δt > 0,02 s; no incluye la gravedad | Con carga | Pro |
| Medias y mejor rep de la serie | De MCV, pico, potencias, ROM y tempo excéntrico | — | Solo reps visibles | — | Mixto |
| «Fatiga» (pérdida de velocidad) | (máximo − última)/máximo × 100 en métricas de velocidad o potencia | % | ≥ 2 reps | Cabecera de la revisión | Pro |
| «Consistencia» | (1 − DE/media) × 100 en métricas de tiempo o ROM; también la del tempo excéntrico | % | — | Igual | Pro |
| % del 1RM | La carga respecto al 1RM estimado (fórmula sin confirmar) | % | — | Con 1RM | Pro |
| Tendencia | Velocidad media de la serie frente a la media de 6 semanas, mismo ejercicio y carga | % | ±2 % = estable | Con historial | Pro |
| Esfuerzo | RPE (de «< 5,5, calentamiento» a 10) o RIR (de 0 a «5+»), a mano | — | Opcional | — | — |
| Ritmo y velocidad | Cardio a mano (por km o milla; km/h o mph). También tonelaje | — | — | Ejercicios sin vídeo | — |
| 1RM estimado | Regresión carga-velocidad (§4.12) | kg o lb | Criterios en §4.12 | «Faltan datos» si no se cumplen | Pro |
| Récords | Por tipo: carga, potencia media, potencia pico, reps, velocidad media y velocidad pico | — | «Casi» si ≥ 95 % del mejor | En la revisión y el resumen | Pro |
| Calidad de la serie | Ángulo de visión, holgura con los bordes, tamaño del disco, tipo y gravedad del aviso, fps real, proceso largo | — | §4.11 | Avisos en la revisión | — |

**No existen:** pose o ángulos articulares; MPV; perfil fuerza-velocidad (solo carga-velocidad); desvío horizontal o deriva de la
trayectoria como métrica (la trayectoria solo se dibuja); simetría; confianza por repetición; CSV en la app de Android (en la web,
sin confirmar).

---

## 4. Algoritmos

### 4.1 Esquema del motor
1. **Captura** con Camera2 NDK (o planos YUV desde Kotlin) e importación en la GPU vía `AHardwareBuffer`, con conversión YCbCr de
   Vulkan.
2. **Preprocesado** en la GPU (`FrameProcessor`) o en la CPU (`CpuPreprocessor`, NEON): puntos de borde etiquetados.
3. **Detector de elipses** (`EllipseDetector`): elipses por fotograma.
4. **En vivo** (`RealTimeSetProcessor`): ventana móvil y retroalimentación por repetición.
5. **Posprocesado de la serie** (`SetProcessor::processRecordedSet`): grupos, posiciones, splines, repeticiones y métricas.

### 4.2 Preprocesado y bordes
- **Etapas registradas:** desenfoque → enfoque → diferencia → desenfoque de bordes → Sobel → NMS → componentes conexas → arcos.
  - El enfoque es una máscara de enfoque fuerte: 4·imagen − 3·desenfocada.
  - La **diferencia absoluta entre fotogramas consecutivos** forma parte del camino (`detectMovedEllipses` recibe el fotograma actual
    y el anterior). Su papel exacto (máscara de movimiento o mezcla) está sin confirmar.
- **Canny con histéresis:** fuertes ≥ 0,16 y débiles ≥ 0,04 del módulo normalizado, o 0,12 y 0,03 en modo de poco contraste. En CPU,
  18 y 8, o 14 y 6, en unidades de 8 bits.
- **Componentes conexas** en la GPU por unión-búsqueda con atómicos. Solo sobreviven las que tienen algún píxel fuerte.
- **Arcos:** cada punto va a uno de dos búferes según el cuadrante de su gradiente (I y III, o II y IV). Así se separan los cuatro
  tipos de arco de una elipse.
- **Diagnósticos:** histograma de gradiente, distribución de bordes y contadores de rechazo. Viajan en el JSON de los informes de
  fallos.

### 4.3 Detección de elipses
- **Contornos:** grupos de contornos → arcos por cuadrante.
- **Candidatos:** combinaciones de 3 de los 4 cuadrantes (`getEllipseCandidates123/124/134/234`). Se podan con un **número
  característico** proyectivo (`conicCharacteristicNumber`): se acepta si |1 − CN| ≤ 0,4, porque los arcos de una misma cónica dan
  ≈ 1. Es el método de Jia et al. (2017), sobre la base de Fornaciari et al. (2014).
- **Ajuste ElliFit** con todos los puntos de los tres arcos (`ElliFit::ellipseFit`).
- **Evaluación** (`evaluateEllipse`):
  - cobertura = suma de las cuerdas de los arcos ÷ (3·(a + b)), ≥ 0,30 (≥ 0,25 en CPU);
  - residuo ≤ 0,02 (≤ 0,035 en CPU);
  - relación de ejes a/b ≤ 3;
  - centro dentro del fotograma.
- **Refino del aro exterior** (`refineOuterEllipse`; mínimo 6 px de radio):
  - 72 rayos radiales entre 0,6·r y 2,6·r, en bandas de max(0,06·r; 3 px);
  - se busca el anillo más externo, se reajusta con ElliFit y se acepta si el semieje mayor crece > 5 %, b/a ≥ 0,7 y el residuo es
    ≤ 0,04.
  - Así gana el borde del disco y no el aro interior ni el buje: es la misma idea que el «pico más externo» de KPKN.
- **Contornos concéntricos:** se quitan los duplicados.

### 4.4 Seguimiento, semilla y recuperación
**Seguimiento por detección:** cada fotograma se detecta de nuevo y las elipses se encadenan en grupos.
- **Similitud** (`areEllipsesSimilar`):
  - centro dentro de una tolerancia relativa a W y H;
  - ejes con diferencia relativa acotada;
  - misma clase de circularidad: b/a ≥ 0,92 «círculo», < 0,88 «elipse» y la franja intermedia es ambigua;
  - orientación compatible.
  - Las tolerancias son del 2,5 % dentro de un fotograma y del 10 % para rellenar huecos. La de asociación entre fotogramas está
    sin confirmar (va en una lambda paralela).
- **Grupo nuevo:** solo si el semieje menor es ≥ 6 % del lado del fotograma. Si el disco mide menos de 45 cm, el umbral se escala por
  D/0,45.
- **Limpieza:**
  - detecciones duplicadas del mismo fotograma (≤ 30 px; si un eje es > 1,2× el otro, gana la exterior);
  - grupos concéntricos o duplicados (compara pares de grupos con umbrales de 50 y 100, de unidad sin confirmar; tamaños parecidos,
    < 5 % → se queda el de más detecciones; > 1,2× → se queda el exterior);
  - un fotograma por grupo (gana la detección más cercana a sus vecinas).
- **Objetivo:** el grupo con **más fotogramas** (`getMostRelevantEllipseGroup`). No se elige a mano.
- **Huecos:** entre 20 ms y 1 s, si las elipses de los extremos se parecen (10 %), se insertan detecciones interpoladas al paso de
  los fps. Los huecos > 1 s quedan sin rellenar.
- **Atípicos locales:** desviación > 15× la mediana local (`detectLocalOutliers`).
- **No hay Kalman, KCF ni NCC** en la serie. La «recuperación» tras una oclusión es volver a detectar; el tramo perdido se rellena
  solo si dura < 1 s.

**Vista previa, antes de grabar:**
- `PlateTrackerV2` es un filtro α–β (α = 0,4, β = 0,1) en coordenadas normalizadas:
  - puerta de 0,06 + 1,5·|v|·dt, con tope de 0,18;
  - tamaño a ±35 %;
  - la velocidad decae con e^(−dt/150 ms); sigue sin detección 250 ms y se pierde a 1 s.
- `PreviewPlateLocker` puntúa cada candidato con tamaño × (1 − distancia al centro), así que prefiere discos grandes y centrados:
  - bloquea con 3 aciertos y 400 ms;
  - suaviza con EMA 0,6/0,4;
  - asocia a ±6 % de posición y ±30 % de tamaño;
  - libera tras 1,5 s.
- Al iniciar la grabación, la **medición de exposición y el enfoque** se centran en el disco bloqueado
  (`setMeteringRegionToPlate`).

### 4.5 Calibración y escala
- **Escala por fotograma:** D/(2·a) m/px, con a = semieje mayor de la elipse de ese fotograma y D = 0,45 m o el diámetro del usuario.
  El registro guarda también la escala media de la serie.
- **Intrínsecos de la cámara:** `LENS_INTRINSIC_CALIBRATION` si existe; si no, focal y tamaño del sensor; ambos girados a vertical.
  Por fotograma si el `CaptureResult` los trae. Sin ellos: «el cálculo de posición quedará degradado».
- No usa la altura del usuario, ni marcadores, ni comprobaciones de cordura del tamaño.

### 4.6 Perspectiva y ángulo de cámara
- **Vertical 3D por gravedad** (`getEllipsePosition`, con focal entre 100 y 5000 px y gravedad no nula):
  1. desplazamientos laterales X e Y del centro de cada elipse respecto al **centro medio** del grupo, en metros, con la escala de
     ese fotograma;
  2. distancia del disco R = f·D/(2a), por el modelo estenopeico;
  3. profundidad Z = √(R² − X² − Y²);
  4. se proyecta (X, Y, Z) sobre una base alineada con el vector de gravedad y se toma la componente vertical.
  - **Resultado:** la altura del disco a lo largo de la vertical verdadera. Compensa el cabeceo y el alabeo del móvil y los cambios de
    distancia a la cámara.
- **Sin intrínsecos o sin gravedad** (vídeo importado): desplazamiento vertical 2D × escala por fotograma.
  - Si no hay datos de movimiento en toda la serie, estima la inclinación con la orientación mediana de las elipses (restando en
    cuadratura ≈ 13°). Si supera 8°, multiplica las posiciones y velocidades verticales por 1/cos, con un tope de **×1,4**.
- **Ángulo de visión de la serie:** acos(b/a) de la elipse media. Aviso «Ángulo incorrecto» > 30°; error > 45°. En la vista previa
  se avisa con b/a < 0,866 (30°).
- La **escala usa el semieje mayor**, que en una vista girada (guiñada) conserva el diámetro real. Por eso las métricas verticales
  siguen siendo válidas con un ángulo moderado.
- **No hay** homografía, ejes del rack, rectificación del plano del disco ni del recorrido horizontal: la trayectoria se dibuja en
  píxeles.

### 4.7 Suavizado y derivadas
1. Se agrupan los tiempos duplicados (< 0,1 ms).
2. **Spline cúbico de suavizado** (csaps) sobre el tiempo en ms: p = 5·10⁻⁶ para la vertical y 5·10⁻⁷ para las demás series. La
   trayectoria dibujada usa p = 2,5·10⁻⁶.
3. **Remuestreo uniforme a 60 Hz** (paso de 16,67 ms). Velocidad = diferencias de primer orden.
4. Se recortan por el final los valores con |v| > 10 m/s.
5. **«Empuje» de la velocidad pico** (`calculateSetDataFromPositions`):
   - k = mediana del intervalo entre fotogramas × 0,06, acotada a [1; 4] (1 a 60 fps, 2 a 30 fps);
   - se reajusta el spline con p·k³·0,4, menos suavizado;
   - el pico nuevo se acota a min(pico_nuevo; 1,8·pico + 0,2; 10);
   - si supera al anterior, el pico se mueve hacia él un 60 % a k = 1 y un 40 % a k ≥ 2, y se recalcula el tiempo al pico.
   - Es una corrección empírica del aplastamiento del pico por el suavizado.

### 4.8 Repeticiones y fases
1. Hacen falta > 30 muestras.
2. **Plantilla:** 27 valores (≈ 0,45 s a 60 Hz). Velocidad negativa que se frena hasta cero y luego sube en rampa lineal: el «giro»
   abajo. Correlación cruzada completa (sin normalizar) de la velocidad con la plantilla (`calculateCrossCorrelation`).
3. **Picos:** uno por lóbulo positivo de la respuesta (`findPeaks`). Se descartan los ≤ 50 % del pico máximo («FILTERED-LOW»).
4. **Final de la serie:** si la media de las 5 últimas velocidades es ≤ 0,1 m/s, la serie acaba en reposo. Si no, los picos que
   desbordan se recortan al último índice («la serie acaba a mitad de rep»).
5. **Límites concéntricos** (`shiftRepEndPoints`): el inicio y el fin se desplazan al tramo con **v > 0,1 m/s**. Hacia atrás, el
   inicio salta hasta 70 muestras que no bajen de −0,05 m/s; hacia delante, el fin busca hasta 200 muestras.
6. **Excéntrica:** segunda pasada con la señal invertida y umbral de 0,05 m/s.
7. **Filtro de posición:** se quitan las reps cuyo inicio queda por encima de inicio_medio + 0,6·ROM_medio o cuyo fin queda por debajo
   de fin_medio − 0,6·ROM_medio.
8. **ROM:** mínimo y máximo de la posición a ±1 s de la concéntrica. La excéntrica se empareja si su recorrido difiere < 40 %.
9. **Filtro final:** ROM > 0,10 m, 0 < MCV < 10, 0 < pico < 10 y > 5 muestras. Después, **parciales:** ROM < 0,6 × la media de las
   demás.
10. **Holgura:** se quitan las reps cuyo centro pasa a < 0,5 radios de un borde del fotograma («CLEARANCE»).
11. **Sin reps:** mensaje «no se detectaron reps».

Consecuencias:
- funciona con y sin pausa (no depende de quietudes, a diferencia de WL Analysis);
- las fases son concéntrica, excéntrica y pausas;
- no hay parciales ni fallidas marcadas: se eliminan.

### 4.9 Modo en vivo
- **Ventana móvil** de los últimos 8 s de detecciones.
- **Comprobación de rep:** 150 ms después de que la velocidad baje a ≤ 0,1 m/s tras una concéntrica. Se recalcula la serie sobre los
  últimos 7 s.
- **Rep nueva:** ≥ 500 ms después de la anterior y con ROM entre 0,1 y 2,5 m.
- **Retroalimentación:**
  - **Leer:** formatea el valor con 0 a 2 decimales según su magnitud y lo dice por TTS a 1,3×, por el canal de alarma y con el
    volumen propio; descarta lo que llegue con la voz ocupada.
  - **Objetivo:** campana (`target_achieved.wav`) si se supera, zumbador (`target_missed.wav`) si no. Para una de las métricas
    (índice 5, sin confirmar cuál) el criterio se invierte.
  - **Pérdida:** 1 − valor/mejor; anuncia el % al superar el umbral (con un margen de 2 puntos, sin confirmar).
- **Aviso en la app:** la retroalimentación puede variar ligeramente respecto al posprocesado.
- **Al parar:** si la visión en vivo perdió fotogramas, se decodifican desde el vídeo grabado y se procesan antes del análisis final
  (`fillDroppedFrames`).

### 4.10 Captura, fps y cámara lenta
- **Grabación nativa:**
  - **1280×720** (vertical, 720×1280) a **60 fps** fijos si la cámara los ofrece; si no, 30; si no, la mayor tasa fija;
  - **HEVC** con respaldo H.264, a 5 Mbps (lado corto ≤ 720) u 8 Mbps; fotograma clave cada 1 s; BT.709 SDR; audio AAC;
  - sesión de 3 superficies (vista previa, lector de imágenes para la visión y codificador);
  - el muxer espera a la pista de audio.
- **Exposición:**
  - **niveles:** AE_PRIORITY (Android 16+), AE continuo o bloqueo manual;
  - **escalera:** 1/360, 1/300, 1/240, 1/200, 1/120 y 1/100 s, con techos de ISO 800, 1000, 1300, 1600, 3000 y 4000. Solo se usan
    los pasos seguros para la red local: múltiplos de 1/120 s con 60 Hz y de 1/100 s con 50 Hz, según el parpadeo detectado o la
    región;
  - medición sobre el disco, transición suave entre pasos y bloqueo durante la serie;
  - detección de poca luz por obturación e ISO, y un umbral de «ISO de grano» que la app fija como % del ISO máximo (su efecto en
    el detector está sin confirmar).
- **Visión en vivo:** se registra qué % de fotogramas procesa. Si la GPU no da abasto, se rellenan después (§4.9). En dispositivos
  sin GPU válida: modo solo grabar y procesar al terminar.
- **Validación de la GPU:** fotogramas de prueba con luminancia suficiente y el «canario» YCbCr; cola de reintentos; caída a CPU.
- **Importación:**
  - AMediaExtractor y AMediaCodec; con HDR se pide el mapeo a SDR; Dolby Vision se rechaza;
  - el lado corto se reduce a **≤ 900 px**;
  - el tiempo sale de las marcas de presentación del vídeo;
  - los fps del aviso salen de los metadatos de captura o de fotogramas ÷ duración.
  - **Cámara lenta:** no se vio reescalado del tiempo en la ruta nativa (sin confirmar).
- **Aviso de la serie:** fps < 50.

### 4.11 Controles de calidad y avisos
- **Vista previa** (`applyGeometryFeedback`), con el tamaño del disco relativo al fotograma:
  - < 0,09 o 0,09–0,12 → «demasiado lejos»;
  - 0,12–0,30 → correcto;
  - 0,30–0,40 o > 0,40 → «demasiado cerca»;
  - b/a < 0,866 → ángulo;
  - bordes a < 2 % → fuera de cuadro.
  - Los cambios de estado tienen histéresis de 500 ms.
- **Serie:** tamaño medio del disco = 2·eje ÷ lado del fotograma (probablemente el ancho), corregido por el ángulo; umbrales escalados
  por D/0,45.
  - Pequeño: aviso < 0,28, error < 0,21. Grande: aviso > 0,6, error > 0,8.
  - Holgura mínima con los bordes: aviso < 0,02, error < −0,02 («la barra salió del cuadro»).
  - Ángulo: aviso > 30°, error > 45°.
  - Sin reps: error.
  - Cada aviso tiene tipo y gravedad (naranja o rojo). La prioridad cuando coinciden varios está sin confirmar: el pseudocódigo
    pierde parte de la cadena de condiciones.
- **Otros avisos:** fps < 50, fotogramas perdidos, proceso largo, dispositivo no compatible, poca luz con la cámara frontal y barra
  vacía.
- **Sin** confianza numérica ni abstención: con avisos, las cifras se muestran igual.

### 4.12 Perfil carga-velocidad y 1RM
- **Puntos:** carga y la mejor MCV de cada carga, con las reps visibles de las series del ejercicio.
- **Requisitos:** ≥ 3 cargas distintas, y que (máx − mín)/mín ≥ 0,6 **o** v(carga mínima) − v(carga máxima) > 0,25 m/s.
- **Regresión:** mínimos cuadrados ordinarios v = a·carga + b, con pendiente negativa.
- **1RM = (v_1RM − b)/a.** Se acepta si es finito, > 0 y ≤ 2 × la carga máxima. **No hay** R², intervalo de predicción ni límite
  respecto a la velocidad del punto más pesado.
- **v_1RM por defecto** (editable por ejercicio): press de banca 0,25; sentadilla 0,35; «trap bar» 0,40; remo 0,50; peso muerto 0,25;
  otros 0,35 m/s.
  - **Fallo:** el texto busca «trap bar» con espacio, pero el catálogo dice «Trapbar». El peso muerto con barra hexagonal cae en
    0,25.

### 4.13 Recorte automático por el gesto de recoger el móvil
Con gravedad registrada y una serie ≥ 4 s (`trimMotionAffectedFramesFromSet`):
1. gravedad de referencia = mediana de la parte inicial;
2. inclinación de cada fotograma respecto a la referencia;
3. umbral = max(3 × el P95 de referencia; 12°);
4. si en los últimos 3 s la inclinación lo supera ≥ 150 ms, se corta desde ahí;
5. sin cortar más del 35 % de la serie.

### 4.14 Comparación con los errores publicados (hipótesis)
- **Omisiones (Renner 2024, iOS v2.3.1: 52 en total, 7, 12 y 33 por ejercicio, en el orden de `MERCADO_FIABILIDAD.md`; la mayor cifra
  aparece en el peso muerto).** Mecanismos del código que eliminan reps válidas:
  1. **picos de respuesta ≤ 50 % del máximo.** En un peso muerto que arranca del suelo no hay bajada antes del tirón, y la plantilla
     espera una velocidad negativa que se frena;
  2. el **filtro de holgura**: el disco cerca del borde inferior al estar en el suelo;
  3. el **filtro de parciales** (< 60 % del ROM medio);
  4. el **filtro de posición**;
  5. los huecos > 1 s sin relleno.
- **Fantasmas (16 en Renner).** Cualquier bajada y subida del disco > 10 cm que supere esos filtros cuenta: ajustes de la barra en el
  rack, una recolocación al final o el gesto de soltar la barra. Solo el recorte por recoger el móvil y la holgura los atenúan.
- **Sesgo de MCV (Trowell 2024, v0.6.0: +0,10 m/s en sentadilla y +0,08 en banca; Puppyn 2025, v4.8.1: +0,007 y +0,043).** Medir la
  MCV en el tramo con v > 0,1 m/s quita las colas lentas: la duración baja más que el desplazamiento y la media sube, sobre todo en
  reps lentas y de recorrido corto como la banca. El «empuje» del pico también tiende a subirlo.
- **ROM (Trowell: −0,08 cm y −1,15 cm de sesgo):** coherente con medir el ROM en una ventana de ±1 s fuera del tramo concéntrico.
- **Recuento:** no depende de pausas, así que debería funcionar con series continuas. Que las precisiones de Trowell (1,000/1,000 y
  0,959/0,985) se mantengan en esta versión de Android está sin confirmar.

---

## 5. Exportación y compartir

### 5.1 Vídeo con la telemetría quemada (`ra7` → `n20`)
**Motor:** Media3 Transformer **1.9.2**, con una `Composition` de un solo `EditedMediaItem`:
- **recorte** opcional (`ClippingConfiguration`): con «Recorte automático para la exportación», del inicio de la primera rep visible
  menos el margen al fin de la última más el margen;
- **efectos:**
  1. `Presentation` con relación de aspecto **9:16** y escalado «llenar y recortar»;
  2. `OverlayEffect` con una superposición propia de textura (`n20`, que el shader de superposición de Media3 trata como
     `TextureOverlay`). En cada fotograma pinta un `Bitmap` ARGB con `android.graphics.Canvas` y lo sube como textura solo si cambió;
- **HDR:** modo 2 de la composición (mapeo HDR → SDR con OpenGL).
- **Si el proceso de fotogramas falla:** guarda una **copia sin superposición**.

**Salida:**
- **tamaño:** 9:16 a la resolución nativa, en pares. Un vídeo vertical de 1080×1920 sale igual; uno de 720×1280 sale a 720×1280; uno
  horizontal de 1920×1080 se recorta a ≈ 606×1080;
- **destino:** MediaStore, `Movies/Metric/Metric_<ejercicio>_<marca de tiempo>.mp4`, con el aviso «Vídeo guardado en la galería».
  Opción de exportar automáticamente;
- el códec, la tasa de bits y el audio son los de Media3 por defecto (sin confirmar).

**Qué dibuja** (diseño a 720 px de ancho, escalado por s = ancho/720; el vídeo se encaja en el lienzo con recorte centrado):
1. **Trayectoria progresiva de puntos:**
   - solo las muestras con tiempo ≤ t y dentro de alguna repetición: entre reps no se dibuja nada;
   - círculos rellenos de radio 5·s;
   - **repetición actual en verde puro `#00FF00`**; las **previas en gris `#808080` al 60 %**;
   - no hay línea continua ni color por velocidad.
2. **Marcador de posición:** círculo verde de radio 14·s (con otro de 7·s del mismo color), desde que empieza la primera rep.
3. **Tarjeta de datos** (el «sticker»): rectángulo redondeado (radio 32·s) relleno `#082333` al 75 % de opacidad, a 24·s del borde
   izquierdo, con su base al 20 % del alto desde abajo. Ancho = max(176·s; textos) + 48·s. Contenido centrado:
   - **logo de Metric** completo (176·s × 32·s);
   - «*carga unidad* *rep*/*total*» en blanco, 30·s;
   - el **valor de la métrica** de la repetición en curso en verde `#00FF00`, 75·s y espaciado de letras 0,00625. Muestra «--»
     antes de la primera rep;
   - la etiqueta «*Métrica* (*unidad*)» en gris `#AAAAAA`, 24·s.
   - La métrica es la del **modo de análisis** del ejercicio (por defecto, la velocidad media).
   - **Animación:** al cambiar de repetición, el número cuenta desde el valor anterior hasta el nuevo en **350 ms con una curva de
     salida cúbica** (1 − (1 − t)³). Si el tiempo salta atrás > 100 ms, cuenta desde 0.
4. **Sin trayectoria pero con carga y reps:** solo una tarjeta con el logo y «*carga unidad* x *N*».
5. **Tipografía:** Poppins Medium.

**Lo que NO tiene:**
- gráficos de velocidad, barras por repetición, ROM, tempo, pérdida de velocidad ni varias métricas a la vez;
- otros formatos (1:1, 4:5, horizontal u original);
- plantillas o estilos a elegir, ni tarjeta final;
- quitar la marca: el logo va siempre en la tarjeta;
- cámara lenta, comparación ni rejilla;
- hoja de compartir (ACTION_SEND): se comparte desde la galería.
- La reproducción dentro de la app dibuja la misma trayectoria y el mismo marcador (`u20`, mismos tamaños); si también pinta la
  tarjeta está sin confirmar.

### 5.2 Biblioteca de vídeo y nube
- **Biblioteca dentro de la app (Pro):** favoritos, borrado automático por antigüedad y conservar los vídeos con récord.
- **Nube (Pro, 5 GB):** `CloudVideoUploadWorker` sube el vídeo y un **JSON de telemetría** (trayectoria, tiempos, velocidades,
  ángulo de visión, holgura y tamaño del disco) a Firebase Storage para verlo en la web. Retención configurable; cuota consultada
  con la función `getVideoStorageStats`.
- **Informes de fallos:** suben a Storage (`bugReports/`) la descripción, capturas, el vídeo y los datos de depuración de la última
  serie (JSON con diagnósticos del detector, la cámara y la GPU).

### 5.3 CSV o informes
No hay CSV ni PDF en la app de Android. La web de entrenadores está sin analizar.

---

## 6. Datos e integraciones
| Tipo | Detalle |
|---|---|
| Cuenta | Obligatoria. Firebase Auth (correo o Google). Funciones: `createCoachAccount`, `revertToAthlete`, `getVideoStorageStats` y `registerClientCaps` (envía las capacidades del dispositivo) |
| Base de datos | **Firestore** (`users`, `sets`, `workouts`, `organizations`, `templates`, `assets`, `bugReports`), con caché local. Room solo para WorkManager; preferencias en SharedPreferences y DataStore |
| Serie (`SetData`) | Ejercicio, carga y unidad, reps introducidas, duración y distancia, reps con métricas, RPE, usuario y organización, ruta del vídeo, trayectoria (`BarPathPoint`: tiempo, x, y en píxeles), ancho y alto, fps y fps real, velocidades y tiempos a 60 Hz, desfase del inicio de la visión, importado, ángulo de visión, holgura, tamaño medio del disco, avisos, proceso largo, recursos (telemetría, vídeo, miniatura) y favorito |
| Telemetría | Crashlytics (incluido el nativo), Analytics, Sessions, App Check (Play Integrity) y un JSON de diagnóstico propio por serie en los informes |
| Sensores externos | No hay BLE, wearables ni Health Connect |
| Privacidad | Los datos de las series viven en la nube; el vídeo, solo si se activa la nube (Pro) o se envía un informe |

---

## 7. Las tres preguntas del usuario

**1. Perspectiva: ¿exige grabar a 90° de perfil, o corrige ángulos oblicuos?**
- **Pide grabar de lado, pero tolera hasta 30°** (aviso entre 30° y 45°, error por encima) y **corrige más que WL Analysis o My Jump
  Lab:**
  - la escala sale del **semieje mayor de cada fotograma**, que conserva el diámetro real si la cámara está girada hacia un lado;
  - la **vertical se mide en 3D:** distancia por el tamaño aparente del disco y los intrínsecos de la cámara, y proyección sobre la
    **gravedad** del móvil. Eso corrige el móvil inclinado o girado y los acercamientos y alejamientos del disco;
  - en un vídeo importado, sin gravedad, hay una corrección heurística de inclinación (1/cos, tope ×1,4 a partir de 8°).
- **No hay** homografía, ejes del rack ni rectificación del plano del disco; el recorrido horizontal no se corrige (solo se dibuja).
- **Frente a KPKN:** KPKN ya mide la elipse con escalas horizontal y vertical, el paralaje y el giro. Le faltan la profundidad por
  fotograma y la gravedad 3D (§8, D42.2).

**2. Tracking híbrido: ¿qué hace sin disco olímpico?**
- **Nada.** La app lo dice: «Metric solo sigue discos circulares. Barras vacías, mancuernas, discos de 12 lados y movimientos de peso
  corporal no se siguen».
- Los ejercicios de polea, máquina o mancuerna del catálogo son solo para **registro manual**, sin velocidad.
- **No hay** pose, marcadores impresos, objetivo tocado por el usuario ni cambio automático de señal.
- **Lo único flexible:**
  - el **diámetro propio** del disco (escala los umbrales por D/0,45);
  - el **modo de poco contraste** para discos negros.
- **D43 sigue siendo diferenciador de KPKN.**

**3. ¿Exporta vídeo con telemetría para redes?**
- **Sí, en 9:16** (§5.1): trazo de puntos (verde la rep actual, gris las previas), marcador de posición y una tarjeta translúcida con
  el logo, «carga · rep/total» y el valor grande de una métrica con conteo animado.
- Recorte automático alrededor de las reps; guardado en la galería; mapeo de HDR.
- **No tiene** gráficos, barras, varias métricas, otros formatos, tarjeta final ni botón de compartir. Lleva siempre el logo.

---

## 8. Brechas frente a KPKN

Se leyeron `VIDEO_TECHNIQUE_V1.md` (§7 y §8), `DIFERENCIADORES.md`, `MERCADO_FIABILIDAD.md`, `INFORME_CONFIANZA_20261020.md`,
`BANCO_COMPLETO.md`, `CANDIDATAS_Y_METRICAS.md` y los informes de WL Analysis y My Jump Lab. Esfuerzo: S ≤ 2 días, M ≤ 1 semana,
L > 1 semana.

### 8.1 Tabla
| Función o métrica de Metric | ¿La tiene KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| **Exportar vídeo con telemetría en 9:16** (trazo, marcador, tarjeta con valor animado, recorte automático, HDR → SDR, galería, copia simple si falla) | No | Media3 Transformer: `Composition`, `Presentation` 9:16 con recorte y `OverlayEffect` con un `BitmapOverlay` o `TextureOverlay` que reutiliza el pintor de `TechniqueOverlay` sobre `Canvas`. **Superarlo:** trazo continuo coloreado por velocidad; barras de MCV por rep con la pérdida; contador; ROM o profundidad; tempo; la ficha (ejercicio, carga, reps, RIR); tarjeta final de la serie; formatos 9:16, 4:5, 1:1 y original con márgenes seguros para IG y TikTok; «≈» y nada sin calibrar; marca KPKN; estética negra con neón. Conteo animado de ~350 ms con curva de salida | androidx.media3 transformer y effect (Apache-2.0; KPKN ya usa Media3) | **Alta** (D44) | M |
| **Detector de discos en todo el fotograma, sin pose:** Canny + arcos por cuadrante + candidatos con invariante proyectivo + ajuste de elipse + refino del aro exterior; objetivo = grupo con más fotogramas; bloqueo previo con puntuación tamaño × centrado | Parcial: Hough cerca de las muñecas (falló en r02) | Opción A: Hough de gradiente de KPKN en todo el fotograma a baja resolución, con preferencia por el aro externo, «movimiento» por diferencia entre fotogramas y agrupación temporal por similitud. Opción B: detector por arcos propio en Kotlin a partir de los artículos (Fornaciari 2014; Jia 2017) y ajuste directo de elipse (Fitzgibbon) en lugar de ElliFit. Después, toque para confirmar si hay varios grupos | Algoritmos publicados; OpenCV contrib `ximgproc::findEllipses` (Apache-2.0, verificar que implementa Jia 2017) si algún día se admite OpenCV | **Alta** (D47) | M (A) / L (B) |
| **Vertical 3D por gravedad:** profundidad por el tamaño aparente del disco, intrínsecos y proyección sobre la gravedad | Parcial: elipse h/v, paralaje y giro | Guardar en `capture.json` `LENS_INTRINSIC_CALIBRATION` o la focal y el sensor (Camera2Interop de CameraX) y la gravedad por fotograma (ya se guarda). Z = f·D/(2a); posición 3D; altura = proyección sobre la gravedad. Si faltan datos, cae al 2D actual y lo marca. Validar con la misma serie de perfil y a 30–45° | Propia | **Alta** (D42.2) | M |
| **Exposición contra el desenfoque:** escalera 1/360–1/100 s con techo de ISO, segura frente al parpadeo de 50 o 60 Hz; medición en la región del disco; bloqueo durante la serie | No consta (sin confirmar) | Camera2Interop: `CONTROL_AE_MODE_OFF` con tiempo de exposición e ISO, o la prioridad de obturación de Android 16; `CONTROL_AE_REGIONS` sobre el disco de la vista previa; elegir 1/120 o 1/100 según `CONTROL_AE_ANTIBANDING_MODE` o la región; tope de ISO y paso a uno más lento con poca luz | CameraX / Camera2 (Apache-2.0) | **Alta** | M |
| **Bloqueo del disco en la vista previa** con avisos: demasiado cerca o lejos, ángulo, fuera de cuadro y poca luz | Parcial: chips de encuadre | Filtro α–β en la vista previa y estados con histéresis de 500 ms. Tamaño del disco 0,12–0,30 del lado como correcto; b/a < 0,866 → ángulo | Propia | Media-alta | M |
| **Voz y sonidos por repetición en vivo:** leer la MCV, objetivo con campana o zumbador, alerta de pérdida, metrónomo de 1 s, canal de alarma | No (análisis al terminar; voz en la sesión) | Necesita el contador en vivo (fase 2): disco en la vista previa con `ImageAnalysis`, comprobación 150 ms después de que v ≤ umbral y recálculo sobre los últimos segundos. Voz con el motor de KPKN; sonidos cortos | Propia | Media-alta (D49) | M–L |
| **Potencia media y pico bien calculadas;** «potencia» de frenado excéntrico | No | Media = m·g·MCV solo si la rep empieza y acaba casi en reposo; si no, la media de m·(a + g)·v. Pico = máximo de m·(a + g)·v con la señal de fase cero a ≥ 50 fps. Abstenerse sin carga, sin calibración o a < 50 fps | Propia | Media | S |
| **Tiempo al pico, TUT con pausas arriba y abajo, consistencia (1 − CV)** | Parcial: fases, TUT y tempo | Añadir el tiempo al pico (≥ 50 fps), las pausas y la consistencia sobre las reps FULL | Propia | Media | S |
| **Recorte automático por recoger el móvil** (gravedad: max(3·P95; 12°) durante ≥ 150 ms en los últimos 3 s, ≤ 35 %) | No | Con la gravedad de `capture.json`, cortar la cola antes de segmentar. Evita fantasmas al final | Propia | Media | S |
| **Calidad de la serie:** ángulo acos(b/a), holgura con los bordes, tamaño del disco escalado por D/0,45; quitar reps cuyo centro pasa a < 0,5 radios del borde | Parcial: elipse y chips | Calcular las tres medidas y la holgura por rep. Una rep cortada por el borde pasa a «sin velocidad», no se borra | Propia | Media | S |
| **Flujo «perfil de velocidad»:** 1RM estimado → cargas de calentamiento → perfil carga-velocidad → 1RM | Parcial: perfil con criterios estrictos | Asistente de test en el hub: cargas progresivas (porcentajes propios, publicados en la literatura), «máxima intención» y la estimación con los criterios actuales de KPKN | Propia | Media | M |
| **Récords** («casi» ≥ 95 %) y **tendencia** frente a la media de 6 semanas a la misma carga (±2 %) | Parcial: tendencias por ejercicio (VT9a) | Insignias en el informe y en el resumen | Propia | Baja-media | S |
| **Modo de poco contraste** (umbrales de Canny más bajos para discos negros) | No | Interruptor o automático: si la cobertura del candidato es baja y la luminancia de la ROI también, bajar el percentil P85 de bordes | Propia | Baja-media | S |
| **Spline de suavizado y remuestreo a 60 Hz** | No (Butterworth de fase cero sobre PTS) | Solo para tiempos muy irregulares (VFR); el Butterworth actual es adecuado | csaps-cpp (MIT) o propia | Baja | S |
| **Diagnóstico del dispositivo** (prueba de GPU y cámara, lista de compatibles) | No | Prueba breve en el primer uso: fps alcanzables, lo que tarda la visión por fotograma, y degradar con aviso | Propia | Baja | S |
| **Biblioteca de vídeo y nube para entrenadores** | No | Choca con «El vídeo nunca sale del teléfono»: decisión del usuario | — | Baja (decisión) | L |

### 8.2 Lo que KPKN hace mejor
- **Pose completa** (33 puntos): ángulos, profundidad, bloqueo, tronco, trayectoria respecto al medio pie, fases y observaciones.
  Metric **no tiene pose**.
- **Tracking híbrido por perfil de ejercicio:** pose en mancuernas, poleas y máquinas. Metric solo ve discos circulares.
- **Seguimiento robusto:** Kalman con puerta de Mahalanobis, NCC de respaldo, readquisición y seguimiento hacia atrás. Metric
  redetecta en cada fotograma sin modelo de movimiento y solo rellena huecos < 1 s.
- **Honestidad:** niveles ALTA, MEDIA y BAJA, «≈», abstención y m/s solo con calibración. Metric siempre da cifras, aunque haya avisos.
- **Definición de la MCV:** KPKN mide la concéntrica completa. Metric la mide en el tramo con v > 0,1 m/s, lo que tiende a subirla.
- **Velocidad pico:** KPKN la exige a ≥ 50 fps y sin heurísticas. Metric la «empuja» un 40–60 %.
- **1RM estricto:** KPKN pide R², rango de cargas y de velocidades, velocidad del punto pesado e intervalo de predicción. Metric
  acepta tres cargas y un 1RM de hasta 2× la carga máxima, sin medida de ajuste.
- **Cámara lenta y VFR:** KPKN detecta `captureFps` y VFR. En Metric, el reescalado en la importación está sin confirmar.
- **Privacidad y uso sin cuenta.** Metric exige cuenta y nube para las series.

### 8.3 Errores de Metric que KPKN debe evitar al copiar ideas
- Medir la MCV en un tramo cortado por un umbral de velocidad.
- Corregir el pico hacia un ajuste menos suavizado con pesos fijos.
- Aceptar un 1RM sin medida de ajuste ni límites físicos.
- Borrar reps en vez de marcarlas: holgura, parciales y picos débiles.
- El emparejamiento de nombres por texto («trap bar» frente a «Trapbar»).
- La exportación limitada a 9:16 por recorte: en un vídeo horizontal queda a ≈ 606 px de ancho.

---

## 9. Índice de evidencias

**Java.** Rutas bajo `C:\kw\re\metric\jadx\sources\`. El árbol se borró al terminar; se regenera con
`C:\kw\re\_scripts\metric\run_jadx_full.ps1`.
| Tema | Evidencia |
|---|---|
| Exportación | `defpackage/ra7.java` (corrutina «VideoExportOverlay»: 9:16 = 0,5625, recorte, `Presentation` `p15(0.5625, -1, -1, 1)`, `OverlayEffect` `uo4`, modo HDR 2, copia simple); `defpackage/n20.java` (pintor por fotograma: colores, tamaños, tarjeta, animación de 350 ms); `defpackage/tf2.java` (parámetros, recorte automático, nombre `Metric_…`); `defpackage/kp2.java` (MediaStore `Movies/Metric`); `defpackage/uf2.java` método `j` (Transformer); `defpackage/xo4.java` (programa de superposición de Media3); `defpackage/u20.java` y `r20.java` (superposición del reproductor) |
| Modelos | `com/coreadvantage/metric/models/workout/RepData`, `RepMetricData` (nombres serializados), `SetData` (`toString`), `TelemetryPayload`, `BarPathPoint`, `SetAssets`; `defpackage/fn6.java` (JSON de telemetría) |
| Métricas y estadísticos | `defpackage/xe5.java` (13 métricas; gratuitas: MCV, ROM y tempo excéntrico); `RepData.b(xe5, carga)` (TUT, pausa total, potencia × carga); `defpackage/hy0.java` y `l73.java` (~5019: fatiga = (máx − última)/máx; consistencia); `defpackage/ud2.java` método `y` (1 − CV); `defpackage/sy5.java` (métricas de la serie); `defpackage/sa5.java` (tipos de récord); `defpackage/ir.java` («casi» ≥ 95 %) |
| 1RM | `defpackage/tc2.java`: `H` (mínimos cuadrados), `m` (mejor velocidad por carga), `z` (requisitos y 1RM); `defpackage/ek4.java` (v_1RM por defecto); `defpackage/c20.java:888`; `defpackage/uf2.java:358` (gráfico del perfil) |
| Preferencias | `defpackage/x02.java` (`_analysisMode`, `_primaryMetric`, `_secondaryMetric`, `_tableMetrics`, `_exertionMeasure`); `defpackage/ek4.java` (`_oneRMVelocity`) |
| Audio | `com/coreadvantage/metricvisionlibrary/AudioFeedbackManager.java` (TTS a 1,3×, canal de alarma, SoundPool) |
| Sensores y cámara | `MotionController.java` (gravedad, aceleración lineal y respaldo del acelerómetro); `VideoCamera.java`; `CvBridge.java`; `VideoProcessorBridge.java`; `SetProcessorBridge.java` |
| Importación | `defpackage/za1.java:3000–3040` (fps de los metadatos); `defpackage/at3.java`, `sv0.java`, `vv0.java` (avisos de fps, resolución y duración); `defpackage/m35.java` (estados) |
| Nube | `defpackage/cn4.java` (`createCoachAccount`, `revertToAthlete`); `defpackage/nq0.java` (`getVideoStorageStats`, retención); `workers/CloudVideoUploadWorker.java` |
| Calentamiento | `defpackage/pf7.java` (`WarmUpSet`), `defpackage/jw5.java` (uso). Generador sin confirmar |

**Nativo.** `libmetricvisionlibrary.so`; direcciones de Ghidra (base de imagen 0x100000; restar 0x100000 para la dirección ELF).
| Símbolo | Dirección | Qué prueba |
|---|---|---|
| `SetProcessor::processRecordedSet` / `postProcessSet` | 0x20a7f4 / 0x20a5e4 | Orquestación del posprocesado |
| `SetProcessor::calculateSetDataForTargetGroup` | 0x20da60 | Inclinación sin gravedad (8°, ×1,4), ángulo acos(b/a) frente a 30°, holgura, tamaño, clasificación de avisos (0,21/0,28/0,6/0,8/±0,02/45°) |
| `SetProcessor::calculateSetDataFromPositions` | 0x212a70 | Splines, umbral de 0,1 m/s, > 30 muestras, parciales (0,6), «empuje» del pico |
| `SetProcessor::fitSplines` / `calculateVelocities` | 0x218d4c / 0x219b0c | csaps, remuestreo a 60 Hz, diferencias |
| `SetProcessor::splitSetIntoReps` | 0x219cf4 | Correlación con la plantilla, filtro de posición, ventana de ±1 s, criterios finales |
| `SetProcessor::getRepTerminationPointsFromRepResponse` / `findPeaks` / `shiftRepEndPoints` / `calculateCrossCorrelation` | 0x21b158 / 0x21c0e8 / 0x21c328 / 0x2182d0 | Picos > 50 %, final de la serie, tramo con v > umbral |
| `_INIT_16` | 0x22b128 | La plantilla de 27 valores (forma descrita, valores no copiados) |
| `SetProcessor::calculateRepMetrics` (dos sobrecargas) / `calculateRepMetricsFromData` | 0x21bd90, 0x20a348 / 0x20a0f0 | ROM, MCV, pico, tiempo al pico, potencias, tempos y pausas |
| `SetProcessor::convertEllipsesToPositions` / `getEllipsePosition` | 0x211d48 / 0x21850c | Escala por fotograma, R = f·D/(2a), proyección sobre la gravedad |
| `SetProcessor::processFrame` / `getMostRelevantEllipseGroup` / `removeConcentricEllipseGroups` / `getIntraFrameGroups` / `collapseConcentricIntraFrameGroups` / `padDetections` / `detectLocalOutliers` | 0x215430 / 0x20c368 / 0x216fbc / 0x216650 / 0x215f34 / 0x211284 / 0x2186dc | Agrupación (6 %), objetivo, duplicados, relleno (20 ms a 1 s), atípicos (15×) |
| `SetProcessor::trimMotionAffectedFramesFromSet` / `getBarPath` / `calculateFrameClearance` | 0x2105d8 / 0x214410 / 0x214cf4 | Recorte por recogida, trayectoria en píxeles, holgura |
| `EllipseDetector::detectEllipses` / `getEllipseCandidates123` / `conicCharacteristicNumber` / `evaluateEllipse` / `refineOuterEllipse` / `areEllipsesSimilar` | 0x24b1a8 / 0x24f128 / 0x24e0c8 / 0x251f90 / 0x2515f0 / 0x24da68 | Detector por arcos, invariante, umbrales de cobertura y residuo, aro exterior, similitud |
| `EllipseDetector::fillDroppedFrames` | 0x2522f8 | Relleno de fotogramas perdidos desde el vídeo |
| `RealTimeSetProcessor::processDetections` / `shouldTriggerRepCheck` / `formatValueString` | 0x22b6dc / 0x22d64c / 0x22d92c | Ventana de 8 s, comprobación a los 150 ms, 500 ms, ROM 0,1–2,5 m, formato de la voz |
| `SetProcessor::PlateTrackerV2::tick` / `PreviewPlateLocker::update` / `applyGeometryFeedback` / `PreviewTracker::update` | 0x2016b0 / 0x201ac4 / 0x202518 / 0x1d0418 | Vista previa: α–β, bloqueo y avisos de geometría |
| `FrameProcessor::detectMovedEllipses` / `detectPlatesForPreview` | 0x1fda48 / 0x200d00 | Ruta en la GPU con fotograma actual y anterior; detección de la vista previa |
| `CpuPreprocessor::initialize` / `processFrame` | 0x204864 / 0x205290 | Respaldo en CPU: umbrales 8/18 y 6/14 y orden de las etapas |
| `VulkanShaders::VulkanShaders` | 0x1cbedc | Carga de los shaders; «canny thresholds 0.04/0.16 o 0.03/0.12» |
| `VideoCameraWrapper::applyExposureShift` / `findBestStep` / `detectExposureTier` / `setMeteringRegionToPlate` / `detectFlickerHz` / `initializeEncoder`; `FUN_001d2170` | 0x1d5638 / 0x1d52fc / 0x1d3044 / 0x1cf87c / 0x1d51ec / 0x1d4040; 0x1d2170 | Escalera de exposición (tablas en .rodata 0x8bb10–0x8bb60 ELF), niveles, medición sobre el disco, HEVC 5 u 8 Mbps, elección de 60/30 fps |
| `VideoProcessor::processVideo` | 0x1f8234 | Importación: lado corto ≤ 900 px, HDR, Dolby Vision |
| Globales `VideoCameraWrapper::frameWidth/frameHeight` | .data | 1280×720 por defecto |

**Pruebas guardadas** en `C:/kw/re/evidencias/metric-re-20261020\` (≈ 0,8 MB):
- `findings-summary.json`: síntesis estructurada propia;
- `hashes-sha256.txt`, `xapk-inventory.json`, `base-apk-inventory.json`, `arm64-apk-inventory.json`, `xxhdpi-apk-inventory.json`;
- `manifest-summary.txt`, `signature.txt`, `library-versions.txt`;
- `ui-strings-en.txt`: cadenas de interfaz en inglés, sin las reseñas del muro de pago;
- `native-vision-symbols.txt`, `native-tests-gtest-names.txt`, `native-elf-header.txt`;
- `native-functions-index.tsv`: dirección, nombre y tamaño de las 640 funciones exportadas de Ghidra, sin pseudocódigo;
- `native-strings-xref.tsv` (cadena → función) y `native-log-strings.txt`;
- `spirv-shaders-info.json`: nombres, enlaces, tamaño de grupo y constantes de los 25 shaders.

No se guardó pseudocódigo ni código descompilado, ni los valores de la plantilla de repeticiones.

**Sin confirmar:**
- el comportamiento en ejecución (no se ejecutó nada);
- el papel exacto de la diferencia entre fotogramas en el preprocesado;
- el reescalado del tiempo para vídeos a cámara lenta en la importación;
- los porcentajes del calentamiento automático;
- la resolución de proceso en vivo;
- los umbrales de inclinación y movimiento de la guía de captura;
- el margen exacto del aviso de pérdida;
- la tolerancia de asociación entre fotogramas y la prioridad entre avisos simultáneos;
- la fórmula del % del 1RM;
- el códec, la tasa de bits y el audio de la exportación;
- si los widgets se activan;
- el origen del código de ElliFit y del detector por arcos;
- si la app de iOS comparte este núcleo C++;
- la exactitud real de cualquier cifra.

---

## 10. Método, recursos y limpieza
- **REA 6.5.0** con sus variables: `decode-android-resources` (apktool 3.0.3) para el manifiesto y las cadenas.
- **JADX:** `jadx-core` del jar de REA con un programa propio (`FullDecompile.java`) que omite las bibliotecas con paquete intacto.
  7.737 archivos en 53 s, bajo el candado.
- **Ghidra 12.1.4 headless directo:** `run_ghidra_headless.ps1` y `ghidra_scripts/ExportMetricDecomp.java`. Exporta las funciones de
  la app, las que referencian cadenas clave y sus llamadas hasta profundidad 2: 640 funciones. 229 s, `MAXMEM` 3 GB,
  `-deleteProject`, bajo el candado.
- **LLVM del NDK:** `llvm-readelf`, `llvm-nm` y `llvm-strings` para cabeceras, símbolos y cadenas. **apksigner** para la firma
  (verificación estática).
- **Scripts propios** en `C:\kw\re\_scripts\metric\`, todos con `python -I`: `safe_extract.py`, `dex_inventory.py`,
  `spirv_info.py`, `spirv_composites.py` y `spirv_dump.py` (lectura de SPIR-V), `elf_read_globals.py` (valores iniciales de globales
  y tablas) y `condense_c.py` (lectura del pseudocódigo).
- **Candado** `C:\kw\re\pesado.lock`: mkdir atómico, `owner.txt` y liberación en `finally`. JADX esperó dos turnos de otros agentes.
  Comprobado ≥ 6 GB libres (10,7–17,8 GB) antes de cada herramienta pesada.
- **Web:** no se consultó; las referencias a artículos salen de los documentos de KPKN y de nombres de métodos conocidos.
- **Limpieza:**
  - se borraron el árbol de JADX (52 MB), la salida de Ghidra, las extracciones del XAPK y los listados intermedios;
  - en `C:\kw\re\metric\` quedan solo los dos registros (≈ 11 KB);
  - no quedan procesos java ni Ghidra míos (los java vivos son daemons de Gradle y de otros agentes).
