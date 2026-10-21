# Fulltrack AI 4.8.0 (Android) · ingeniería inversa estática

Fecha: 2026-10-20/21. Encargo: brief `docs/video-technique/briefs/RE_COMUN.md`. Trabajo en sala limpia: nada del APK se
ejecutó ni se instaló; no se copió código descompilado; no se extrajeron modelos ni recursos para reutilizarlos (solo se
leyeron sus cabeceras para anotar forma de entrada y salida). Las descripciones son con palabras propias. Como evidencia van
nombres de clase, de método, de símbolo nativo o de endpoint. Lo que no se pudo verificar lleva «sin confirmar».

> **Corrección de premisa.** El encargo describe Fulltrack como app de **béisbol y sóftbol**. La versión 4.8.0 analizada es
> una app de **críquet**. En el bundle hay 0 apariciones de *baseball*, *softball*, *pitcher*, *catcher* y *home plate*.
> Mide la velocidad del lanzamiento del bolo (*delivery*), el bote, el *line & length*, el swing y el spin, y ofrece DRS/LBW
> y etiquetas de bateo. Esto coincide con `CANDIDATAS_Y_METRICAS.md` (§2.13: «actualmente críquet»). La física sirve igual
> para KPKN: una pelota pequeña a 70–150 km/h, con una referencia de geometría conocida de 20,12 m entre palos.

**Lo esencial para KPKN, en una línea cada uno:**
1. **Seguimiento de la pelota en la nube.** En Android no hay ningún detector de pelota. El teléfono graba, calibra la cámara y
   recorta el clip; el servidor detecta y sigue la pelota y devuelve una trayectoria 3D paramétrica (§4.6).
2. **Calibración completa de la cámara en el dispositivo.**
   - Un modelo de puntos clave (U-Net, 12 mapas de calor a resolución completa) localiza los palos (*stumps*).
   - RANSAC + Ceres resuelven intrínsecos y extrínsecos: focal, punto principal, distorsión, rotación y posición.
   - Las comprobaciones geométricas de encuadre (punto de suelta visible, palos lejanos ≥ 30 px, cámara ≥ 3 m detrás) y la
     recalibración cada 15 s con ORB son directamente trasladables (§4.3–4.5).
3. **Velocidades altas con 30–60 fps.**
   - No usa diferencias finitas: ajusta un polinomio por eje a toda la trayectoria y deriva el ajuste.
   - Cuenta con tiempos de subfotograma, rebote con coeficiente de restitución y la geometría de la escena como restricción.
   - Captura hasta 120 fps, pero sube como mucho 60 fps (§4.7–4.8).
4. **Grabación sin manos:** un MobileNet de 3 fotogramas (320×320, 12,5 Hz) dispara la grabación. Un búfer circular de vídeo ya
   codificado aporta 0,6–1 s de pre-roll (§4.2, §4.8).
5. **Sin pose:** no hay estimación de pose. El análisis de bateo son etiquetas que llegan de la nube, con revisión humana de
   pago (§4.10).
6. **Exportación en Android:** solo el vídeo con marca de agua. La interfaz ofrece «exportar con la animación de la trayectoria»,
   pero el código Android no la dibuja. Dentro de la app hay una vista 3D con Filament, con la cámara igualada a la calibración
   (§5).

---

## 1. Identidad

| Dato | Valor |
|---|---|
| Paquete · versión | `ai.fulltrack` · 4.8.0 (versionCode 1786108354) · minSdk 24 · targetSdk 36 · compileSdk 36 |
| Archivo | `Fulltrack+AI_4.8.0_APKPure.xapk`, 192 998 609 B, SHA-256 `0cbb46042206485a2a534f6b252245a62a7f3fa01e50661087e32e8d09ea59fb` |
| APK base | `ai.fulltrack.apk`, 99 474 025 B, `72ddceb546b0b291639a2540d92c0caba8db56d3414b3f215622e12d0e1dfba2` |
| Split ABI | `config.arm64_v8a.apk`, 86 618 006 B, `ce7e4ff4783719a060e07d63aa30025021d4a580576ee12fd2370ccc00fb986a` (única ABI) |
| Split densidad | `config.xxhdpi.apk`, 5 881 177 B, `679bf723f2c42ebbb117add15a6055a3a54cb17ead436021d98a7e5a055ead67` |
| Splits de idioma (17) | ar, de, en, es, fr, hi, in, it, ja, ko, my, pt, ru, th, tr, vi, zh. SHA-256 de cada uno en `inventario_xapk.json` |
| `manifest.json` · `icon.png` | `da294d5a…f9070` · `1693afea…7d3eca` |
| Bundle JS | `assets/index.android.bundle`: 6 764 028 B, **bytecode Hermes v96**, 36 934 funciones, 45 170 cadenas, `461927f9…d327d` |
| DEX | 9 archivos, 62 785 380 B, 50 131 clases |

**Arquitectura: híbrida.**
- **React Native** (React 19.1.0; RN probablemente 0.81.4, por la cadena del bundle, sin confirmar del todo) con Hermes v96 y la
  nueva arquitectura (`libappmodules.so`, `libreact_codegen_*`). Lleva la interfaz: sesiones, listados, analítica, equipos,
  pagos y subidas.
- **SDK nativo Kotlin «maiden»** (`cricketanalytics.maiden`, `SDK_VERSION 1.0.0`, 430 clases contando las internas, en
  `classes8.dex`). Lleva la cámara y el vídeo, la calibración, el disparo automático y el visor nativo con repetición 3D.
- **Puente RN:** `com.reactlibrary.MaidenSdkModule` (nombre JS `MaidenSdk`).
- Del paquete `ai.fulltrack` solo hay `MainActivity` y `MainApplication`.
- **Ofuscación:** las clases propias no están ofuscadas (R8 mantuvo los nombres).

**ABIs y bibliotecas nativas.** Solo `arm64-v8a`: 46 `.so`, 86,2 MB. Filmar exige ABI de 64 bits (`openSession` comprueba
`Build.SUPPORTED_64_BIT_ABIS`).

| Biblioteca | Tamaño | Qué es | Licencia |
|---|---|---|---|
| `libnative-lib.so` | 194 KB | **Propia.** Calibración: espacio `projection::` (modelo de cámara) y RANSAC + Ceres; recalibración con ORB. Depende de `libceres.so` y `libopencv_java4.so` | propietaria |
| `libceres.so` | 5,0 MB | Ceres Solver (Eigen dentro): mínimos cuadrados no lineales | BSD-3 |
| `libopencv_java4.so` | 19,9 MB | **OpenCV 4.8.1**: ORB, BFMatcher, Rodrigues, cvtColor | Apache-2.0 |
| `libtensorflowlite_jni.so`, `libtensorflowlite_gpu_jni.so` | 4,3 + 2,5 MB | **TFLite 2.20.0-dev0** (compilación propia) con delegado GPU | Apache-2.0 |
| `libfilament-jni.so`, `libgltfio-jni.so`, `libfilament-utils-jni.so` | 7,4 MB | **Filament** (motor 3D de Google), mediante **SceneView** (`io.github.sceneview`) | Apache-2.0 |
| `libavcodec/avformat/avfilter/avutil/swscale/swresample/avdevice.so`, `libffmpegkit.so` | ~15 MB | **FFmpeg n6.0** por FFmpegKit (fork «ffmpeg-kit-16KB»). Compilado con `--enable-version3` y **sin** `--enable-gpl`, es decir **LGPL-3.0**; `res/raw/license.txt` = LGPL v3 | LGPL-3.0 |
| `librnskia.so` | 11,3 MB | `@shopify/react-native-skia` (lo usan los gráficos de Victory Native) | BSD-3 / MIT |
| `libreactnative.so`, `libhermes.so`, `libjsi.so`, `libreanimated.so`, `libworklets.so` | ~11 MB | runtime de RN | MIT |
| `libop-sqlite.so` | 1,6 MB | base local SQLite (`op-sqlite`) | MIT |
| `libcrashlytics*.so`, `libavif_android.so`, `libglide-webp.so`, `libanimation-decoder-gif.so` | — | Crashlytics y decodificadores de imagen | Apache/BSD |

**Modelos de ML: 4 en `assets/MLModels/`, todos FLOAT32.** No hay modelo de pelota ni de pose.

| Modelo | Tamaño | Entrada | Salida | Arquitectura (por la gráfica) | Uso |
|---|---|---|---|---|---|
| `calibration.tflite` | 1 093 368 B | `x` [1, 3, 1280, 720] RGB 0–255 (CHW) | [1, 12, 1280, 720] | U-Net ligero (37 ops). Convoluciones separables, 4 max-pool hasta 80×45, 4 convoluciones transpuestas con saltos y sigmoide final. Canales 16→256. «MLIR Converted» | 12 mapas de calor de los puntos clave de los palos (base y punta × 3 palos × 2 extremos) |
| `calibrationLarge.tflite` | 2 417 552 B | ídem | ídem (`Identity`) | la misma red, 1,5 veces más ancha (24→384) | **ningún código la referencia** (activo huérfano) |
| `chunkDetection.tflite` | 590 924 B | `x`, `y`, `z`: 3 × [1, 3, 320, 320] | [1, 1] | estilo MobileNet v1 (39 ops). Concatena los 3 fotogramas en 9 canales (fusión temprana), baja hasta 20×20×128 y termina en FC + sigmoide. Convertido con «Qualcomm AI Hub Workbench aihub-2026.07.17.0» | probabilidad de que empiece una entrega: dispara la grabación |
| `chunkDetectionBatter.tflite` | 590 924 B | ídem | ídem | misma arquitectura, otros pesos (aihub-2026.03.29.0) | lo mismo en **modo máquina de lanzar**: dispara con el golpe del bateador |

**Bibliotecas de terceros** (versiones y fuentes en `versiones_bibliotecas.txt`):

*Nativas Android*
- Jetpack Compose 1.9.3 / Material3 1.4.0: la usan los SDK, no `maiden`, que usa Views.
- CameraX 1.0–1.1 alfa: presente, pero `maiden` usa Camera2 directamente.
- Media3/ExoPlayer, WorkManager 2.10.5, Room 2.6.1 y Core 1.17.0.
- SceneView y Filament, EJML (álgebra lineal en Kotlin) y FFmpegKit.
- Gson, Retrofit 2, OkHttp, Coil 3, Glide, Fresco y Lottie.
- Kotlin 2.1.20 y coroutines 1.10.2.
- Sceneform (`com.google.ar.sceneform`, Apache-2.0) también está, y el manifiesto declara ARCore como opcional. El código
  propio no usa ninguno de los dos.

*Servicios*
- RevenueCat (más Google Play Billing 8.0.0, Amazon IAP y pagos web con Stripe y Paddle).
- Firebase (Analytics, Crashlytics, Messaging, Dynamic Links).
- Branch, Customer.io (SDK RN 6.13.1), Segment y Datadog (RUM y logs).
- El SDK de Google Sign-In está presente, pero no se vio que se use: el acceso es por teléfono y OTP.

*JS*
- react-navigation, Reanimated, Gesture Handler, react-native-screens, safe-area y react-native-svg.
- Skia y Victory Native (gráficos).
- react-query (TanStack), mathjs, moment y Axios 1.20.0.
- react-native-video, fast-image, rnfs, image-picker, contacts, keychain, webview, background-actions, device-info,
  permissions e in-app-review.

*Licencias:* casi todo MIT o Apache-2.0. Ceres es BSD-3 y FFmpeg, LGPL-3.0. Todo lo anterior se podría usar en KPKN bajo su
licencia, salvo los servicios propietarios.

**Permisos:**
- CAMERA y RECORD_AUDIO (los clips llevan audio AAC).
- READ/WRITE_EXTERNAL_STORAGE (lectura solo hasta SDK 32) y READ_CONTACTS («encontrar compañeros»).
- INTERNET, ACCESS_NETWORK_STATE y ACCESS_WIFI_STATE.
- FOREGROUND_SERVICE y WAKE_LOCK (subidas en segundo plano).
- POST_NOTIFICATIONS, USE_BIOMETRIC/FINGERPRINT (llavero), READ_PHONE_STATE y RECEIVE_BOOT_COMPLETED.
- Anuncios y atribución: AD_ID y ADSERVICES_*. Pagos: BILLING.

**Características y sensores:**
- Acelerómetro **obligatorio**; giroscopio y cámara opcionales; OpenGL ES 3.0 obligatorio.
- Sensores que usa el código: **solo el acelerómetro** (nivel y estabilidad) y la cámara trasera.
- No hay BLE, wearables ni Health Connect.

---

## 2. Inventario de funciones, pantalla por pantalla

### 2.1 Flujo general
1. **Grabación**
   - En React Native: crear o reanudar sesión → `MaidenSdk.openSession(máquina?, yardas, …)`.
   - Abre la actividad nativa `DeliveryRecordingActivity`, que es una máquina de estados:
     `CameraCheckState → DeviceCalibrationState → CameraCalibrationState → PreDeliveryState ⇄ DeliveryState →
     ProcessDeliveryState`.
2. **Al terminar cada entrega**
   - `DeliveryRecordingCallback.onDeliveryCompletion` entrega a RN el vídeo, la miniatura, la calibración y una `BallTrack`
     vacía.
   - RN la guarda en SQLite y la sube a S3; luego pide `run-ball-track-request` y, minutos después, recibe la trayectoria y las
     métricas.
3. **Revisión**
   - Listados y analítica en React Native.
   - El vídeo con la repetición 3D se abre en las actividades nativas `DeliveryViewActivity` (con `DeliveryViewFragment`) o
     `ViewingGameActivity`.

### 2.2 Pantallas React Native (identificadores del bundle)

| Grupo | Pantallas, modales y qué hacen |
|---|---|
| Acceso | `LandingScreen`, `LoginOTPScreen` («Sign in with your number…»: número de teléfono y código OTP por SMS o WhatsApp, `comet.otp.request`; sin contraseña), `SetUpProfileScreen` (usuario de 3 a 20 caracteres, correo, foto), `PrescreeningScreen` («Primary motivation for downloading?»), `AgreeToTermsView`, `ForceUpdateAppView` |
| Inicio y sesiones | `HomeScreen`, `TabsScreen` · `CreateSessionScreen`: nombre, lugar y notas. **Tipo de sesión:** *Solo* o *Team*; privada o de equipo. **Tipo de entrega:** campo obligatorio. **Largo de pista:** «Yards (Stump to Stump)». **Modo máquina:** «Bowling Machine Mode». **Jugador:** estilo y mano de lanzar, mano de bateo. · `SessionSettingsScreen` · `SessionSearchScreen` · `SessionNoteModal`. Si hay una sesión reciente, ofrece reanudarla. «Free sessions are publicly visible» |
| Instrucciones | `FilmingInstructionsScreen`. Imágenes `instructions_tripod`, `instructions_height`, `instructions_position`, `instructions_side`, `instructions_blocking` y `instructions_machine_*`. Indican: trípode, unos 1,5 m de alto, 3 m detrás de los palos del no-bateador, no tapar el campo y, con máquina, cámara junto a los palos del no-bateador |
| Entregas | `DeliveryListingScreen`: lista, filtros, subida pendiente y reintentos · `DeliveryFilterView` y `MultiSelectFilterModal`: por largo, línea, swing, spin, lanzador, bateador, mano del bateador, tipo de golpe y resultado. Filtrado avanzado «e.g. Full Balls to Right Handers», de pago · `MarkDeliveriesScreen` (selección múltiple, borrado y descarga en bloque) · `BulkDownloadModal` («with ball track», ver §5) · `FavoritesScreen` |
| Analítica («Ball Data») | Pestañas **Speed Analysis**, **Swing Analysis** y **Spin Analysis**: líneas por número de entrega, «Bowling speed variation throughout the session», mediana robusta y rangos ajustables (`BallMetricRangeMin_/Max_`) · **Pitchmap** (normal y ancha, con palos superpuestos) · **Beehive** · **Release Points** («Average Release Height») · **Wagon Wheel** · «Shot Data»: tipos de golpe en %, intención agresiva o defensiva, alto o raso, pie delantero, trasero o salida, calidad del contacto y direcciones · `DataView`, `FeatureInfoSheet` («What is swing force») |
| Etiquetado | `ChoosePlayerScreen` («Who was the bowler/batter?»), `PlayerFilterSelectModal` y `AutoTaggingScreen`. El **auto-tagging** agrupa entregas en clústeres en el servidor: «Tag yourself once to get personalized stats», solo cuando ya se procesó el 90 % de las trayectorias. Además, quitar etiquetas en bloque |
| Equipos | `TeamsScreen` y `TeamDetailsScreen`: miembros, administradores, solicitudes y aprobación por el administrador · `AddMembersByUserNameScreen` y `AddMembersFromContactsScreen` · enlaces de equipo y de sesión (Branch) · `SessionJoinRequiredView` y `UnauthorizedAccessView` |
| Juegos | `GameDeliveryScreen` → `ViewingGameActivity` («Umpire Games»): adivinar Out / Not out (§2.4) |
| Suscripción | `SubscriptionSelectionScreen/Modal`, `SubscriptionUsageScreen`, `UpgradeSuccessScreen` y `EntitlementGrid` (tabla de planes). Se paga con RevenueCat (Play o Amazon) o en la web (Stripe o Paddle) |
| Ajustes | `SettingsScreen`. Opciones: «Upload Deliveries» (activa o pausa las subidas), «Speed Units» (km/h o mph, sincronizado con el servidor), «Real-Time Ball Tracking (beta)», «Retry Pending Uploads» y estado de subida, «Delete Cache», perfil, gestión de la suscripción, cerrar sesión, «Delete Account» y contacto |
| Avisos | Bandeja de notificaciones (Customer.io), `CustomPopup`, «Low storage» y avisos de batería baja o temperatura |

### 2.3 Grabación nativa (`DeliveryRecordingActivity`)

**Interfaz**
- Vista previa a pantalla completa en vertical, con pellizco para el zoom digital (40–60° de fovy).
- Cajas guía discontinuas (`CalibrationGuideView`):
  - «Striker Stumps»: del 34 al 54 % de la altura y 30 % del ancho;
  - «Non-Striker Stumps»: del 56 al 85 % de la altura y 60 % del ancho;
  - con máquina: del 34 al 70 % de la altura y 60 % del ancho.
- Puntos detectados en vivo (`OverlayView`) y una animación Lottie de «escaneo».
- Palos y pista virtuales en 3D sobre la vista previa, con la calibración vigente (`setupDeliveryView` /
  `DeliveryViewFragment`, probable). Sirven para comprobar el mapeo y, en modo manual, para alinear a mano.
- Botones: Continue, Redetect, Recalibrate y cerrar. Contador de entregas y acceso a la última (`ib_delivery_view`).

**Estados**
1. **CameraCheck.** «Checking your camera capabilities. This may take a minute.» Elige la estrategia de captura y hace la prueba
   de humo de CHSS. Corta a los 60 s como máximo.
2. **DeviceCalibration**
   - Pide el teléfono vertical: «Current Angle: N°. It should be less than 10°».
   - «Fit the stumps completely in the boxes then press Continue. Pinch to zoom in or out!»
   - Con máquina: «Place camera near non-striker stumps. Zoom in to batter».
3. **CameraCalibration.** «Please wait while we map the stumps. Make sure the non-striker stumps are fully visible». Una vez por
   segundo intenta calibrar y valida el encuadre (§4.3). Si falla, muestra un diálogo con la corrección: mover, inclinar, zoom
   o apuntar.
4. **PreDelivery**
   - «Setup Complete. Press "Redetect"… Start bowling to trigger the recording!»
   - Con calibración manual: «Please line up the virtual stumps with your stumps. You should stand 3 meters back… place the
     phone 1.5 m high».
   - Con 1 a 5 entregas no deja salir al listado: «Bowl 1 over before checking analysis».
5. **Delivery.** «Delivery Detected!» Corta el clip a los 3 s (2,5 s con máquina).
6. **ProcessDelivery.** «Processing Delivery»: genera la miniatura y lo entrega a RN.

### 2.4 Visor nativo (`DeliveryViewActivity` y `ViewingGameActivity`)

**Visor de entregas (`DeliveryViewActivity`)**
- Reproductor ExoPlayer con barra de desplazamiento (`VideoScrubberScrollView`), «Playback Speed» y botones de anterior,
  siguiente y repetir.
- Al terminar el vídeo, o al llegar al fotograma de impacto si hay DRS, pasa a la **vista 3D**:
  - se ve el tubo rojo de la trayectoria con una esfera animada durante 1 s;
  - aparecen las cajas «Speed», «Swing» y «Spin», o «Pitching / Impact / Wickets / Final Decision» con DRS;
  - la cámara se puede orbitar con gestos.
- Más botones:
  - estadio / modo emisión (`ib_broadcast`, `ib_switch_mode`) y mapa de pique 3D de todas las entregas (`ib_pitch_map`);
  - panel de bateo (`BattingFeaturesView`): Shot Type, Outcome, Aerial, Intent, Footwork y Wagon Wheel;
  - comentarios por entrega (`CommentView`) y favorito;
  - etiquetar o quitar lanzador y bateador;
  - descargar y compartir.
- **Plan gratuito:** límite de reproducciones. El servidor responde HTTP 402 y aparece `UpgradeView`: «Your video limit
  refreshes every 24 hours».

**Juego «Out / Not out» (`ViewingGameActivity`, `OutNotOutGame`)**
1. Cuenta atrás y vídeo de una entrega con DRS.
2. El usuario elige Out o Not out.
3. Se reproduce la revisión DRS y se muestra el acierto, con sonidos `correct_sound`, `wrong_sound`, `crowd_cheering` y
   `out_notout`. Se lleva una puntuación.

### 2.5 Funciones de pago

**Planes**
- Según el bundle: gratuito, «Coach», «Coach+», «ELITE», «Enterprise», «Pro Team», «Pro Environment Licence» y
  «Match Environment Licence».
- Los nombres finales y los precios llegan del servidor (`product-entitlement`, `comet.entitlements.fetch`); sin confirmar.

**Derechos por plan (claves `delivery.view.*` y otras):**
- ver speed, swing y spin (`shouldRedactDeliveryPreviewSpinSwing`; el centinela −1,2 se muestra como «Upgrade»);
- release point, foot landing, bat data y shot analytics;
- pitchmap, beehive y filtro de entregas;
- exportar datos (CSV) y vídeos: «Get video and data exports»;
- auto-tagging;
- «Highest Accuracy with QA» (`delivery.qa.high_accuracy`: el equipo de Fulltrack revisa los datos de golpe);
- almacenamiento en la nube (`storage.cloud_gb`), sesiones al mes, entregas al mes y jugadores distintos filmados;
- «Match mode, umpire cam, DRS reviews» («Umpire Cam is required for live DRS in matches. Contact us for match pricing»).

---

## 3. Catálogo de métricas

**Dónde se calcula.** Casi todo se calcula **en el servidor**: el APK solo da formato. Cuando el cálculo es local, se indica.
**Pose y barra.** No hay métricas de pose (ángulos, ROM, fases, tempo, simetría) ni de barra o disco (VBT): Fulltrack no
estima pose y no trata implementos de gimnasio.

| Métrica | Definición · fórmula | Unidades y formato | Dónde | Cuándo se muestra · cuándo se abstiene |
|---|---|---|---|---|
| **Velocidad** (`speed`) | La da el servidor a partir de la trayectoria ajustada. Por la muestra incluida (§4.6), cuadra con la norma de la derivada del ajuste al **inicio del vuelo** a ≈30 fps, no con la media hasta el bote (estimación propia; sin confirmar) | km/h `%.0f`; mph = km/h ÷ 1,609344 (`speedKmhToDisplay`, `toggleSpeedUnit`) | nube | Se abstiene con 0 («n/a»), −1,2 (oculto por el plan: «Upgrade») o `maxFrameIdx` = −2 (sin procesar: «Analytics are available within a few minutes after video upload») |
| **Swing / swing force** (`swing`) | Movimiento lateral en el aire. El texto de ayuda: «sf stands for swing force… measure of the horizontal force on the ball… Top bowlers… 6sf to 10sf». sf = \|swing\| ÷ 0,065 (`SWING_FORCE_SCALE_FACTOR`). Una comprobación de depuración espera swing ≈ 1000 × coeficiente cuadrático lateral, es decir, una aceleración lateral por fotograma²; en la muestra no se cumple, así que la fórmula exacta queda sin confirmar | «sf» `%.1f`; el signo indica izquierda o derecha | nube; conversión local | ídem centinelas |
| **Spin** (`spin`) | Desvío del rumbo lateral **tras el bote**. En el modelo de trayectoria es `deviationArray[0]`, en radianes; `spin` lo daría en grados (en la muestra ambos valen 0; la equivalencia queda sin confirmar) | grados `%.1f°`; el signo indica izquierda o derecha | nube | ídem |
| **Bote: línea y largo** (`pitchX`, `pitchY`) | Punto del suelo donde Y = radio de la pelota (0,0357 m). La x es la línea lateral y la z, la posición a lo largo de la pista; el largo es la distancia a los palos del bateador (z = −10,06) | m | nube | Mapa de pique 2D/3D y filtros. **Zonas de largo** del visor nativo: Yorker 0–1,5 m, Full 1,5–4,5 m, Good 4,5–7,5 m, Short > 7,5 m, más «Full toss» (sin bote, RN). Filtro de línea Left / Off-left / Right / Off-right (umbrales sin confirmar) |
| **Punto y altura de suelta** (`ReleasePoint`, «Average Release Height») | Probablemente la posición de la trayectoria en `minFrameIdx` (sin confirmar). Muestra la media de la altura y la dispersión `stdDeviationX/Y` | m | local (RN), sobre datos de la nube | Derecho de plan `delivery.view.release_point` |
| **Beehive** | Posición de la pelota al cruzar el plano de los palos del bateador (sin confirmar si se calcula en local o en la nube) | m (x, y) | ? | Derecho de plan |
| **Trayectoria 3D** | X(t), Y(t), Z(t) cuadráticos hasta el bote; luego restitución vertical *e*, retención horizontal *k* y giro lateral (`BallTrack.getPositionAtTime`, `getBounceTime`) | m, t en fotogramas | nube; render local | Tubo rojo de 2,5 cm de radio, muestreado cada 2 fotogramas |
| **DRS / LBW** (`DRSReview`) | **Pitching:** In Line / Outside Off / Outside Leg. **Impact:** In Line / Outside / Umpire's Call. **Wickets:** Hitting / Missing / Umpire's Call. Decisión original y final: Out / Not Out / Umpire's Call. `impactFrame` | categorías | nube («Please wait 60 seconds for your DRS result»; usa «DRS homographies») | El vídeo se pausa en `impactFrame`/60 s y salen las cajas DRS. Banda de palo a palo de 22,9 cm en 3D |
| **Golpe del bateador** (`Stroke`) | 22 tipos: For/Back Def, Off/On Drive, Sweep, Rev/Slog Sweep, Cut, Late/Up Cut, Pull, Hook, Scoop, Leg Glance, Lap, Flick, Slog, Switch, Drop Run, No Shot, Pad Away, Obstructed. 7 resultados: Hit, Leave, Miss, Mishit, Edge, Middled, Average. Agresivo o defensivo, alto o raso, pie (Front/Back/Step Out), `wagonWheel` (°) | categorías, ° `%.1f` | nube: IA y **revisión humana** («Our team is reviewing this session…») | «N/A» si falta. Paquete de pago «high-accuracy» |
| **Extras** | No Ball, Wide, Bye, Leg Bye, Penalty | categorías | manual o nube | — |
| **Agregados de sesión** | Mediana de la sesión → si hay «suficientes» entregas, se quitan las que se apartan más de 2σ de la mediana → nueva mediana. Igual con \|spin\| y con la swing force. El color del punto indica la dirección | — | local (RN) | El umbral de «suficientes» no está a la vista (bytecode; sin confirmar) |
| **Uso** | Entregas y sesiones por mes, jugadores distintos filmados, GB en la nube y conteo de partidas «Umpire Games» | — | servidor | límites del plan |
| **Internas** | Puntuación de calibración (*inliers*), probabilidad del disparador e inliers de la recalibración | — | local | Solo se registran en Datadog |

---

## 4. Algoritmos

### 4.1 Reparto cliente / nube

**En el teléfono**
1. El disparo automático: el modelo `chunkDetection` lanza `Event.StartDelivery`.
2. La calibración de la cámara (`calibration.tflite` y `libnative-lib.so`) y la recalibración.
3. La validación del encuadre.
4. La captura a 30, 60 o 120 fps y el H.264 en un búfer circular de NAL ya codificados.
5. El clip del lanzamiento: pre-roll de 0,6 s más 3 s. Al final se le añade una **cola de 1 fotograma calibrado**
   (`CalibratedFrameEncoder.encodeOneShot` + concat de FFmpeg), para que el servidor vea la imagen exacta con que se calibró.
6. La transcodificación a ≤ 60 fps cuando la captura es de alta velocidad.
7. La miniatura.

**En la nube**
1. Subida: `create-delivery` (exige `cameraCalibration` y la miniatura) → `presign-video-upload` (URL firmada de S3, con S3
   Transfer Acceleration y respaldo al endpoint estándar).
2. Cierre: `confirm-video-upload` → `run-ball-track-request` (`RUN_BALL_ON_CLOUD_EVENT`).
3. El servidor detecta y sigue la pelota y ajusta la trayectoria. También calcula el DRS, las etiquetas de golpe y el auto-tagging.
4. Avisa con una notificación y la app actualiza con `UPDATE_BALL_TRACK_EVENT` o `patch-delivery`.

**«Real-Time Ball Tracking (beta)»** existe como ajuste. En Android, `MaidenSdkModule.setBallTracking`, `setNewChunking`,
`setDrsHomographies`, `setAccelerometerStatus` y `setFreeTierStatus` están **vacíos**. La API admite subir una `ballTrack`
desde el cliente (`patch-delivery requires a complete ballTrack`, columna `balltrackUploaded`) y la cadena aparece junto a una
lista de iPhone 11–17 Pro. Todo indica un seguimiento en el dispositivo **solo en iOS**; sin confirmar, porque no tenemos el IPA.

### 4.2 Disparo automático (`MobilenetChunkingService`, `CameraSource$startChunkingDriver`)

**Bucle (cada 80 ms, 12,5 Hz, mientras no hay entrega en curso)**
1. `GlRenderPipeline.requestFrameBurst` pide a la GPU 3 fotogramas RGBA de 320×320 desde la textura de la cámara.
2. El fotograma entero se reescala a 320×320 **sin conservar la proporción**. El `croppingRect` de la caja del bateador se
   calcula, pero no se usa (sin confirmar; se deduce del código).
3. Los 3 fotogramas van espaciados como a 30 fps: paso = fps / 30, mínimo 1.
4. Se convierten a planos float 0–255 y se ejecuta la red; si falla el delegado GPU, se usa la CPU con 4 hilos.
5. Si la probabilidad supera 0,6 (0,7 con máquina), se emite `StartDelivery`.

**Detalles**
- Cada disparo se registra con su latencia y con la separación real entre fotogramas (`frameSpacing01Ms/12Ms`).
- Si una ráfaga tarda más de 1 s, se cancela.
- La fusión temprana de 3 fotogramas hace que la red **vea el movimiento** (la carrera y el brazo), no solo la pose.

### 4.3 Calibración de la cámara y escala

**Mundo conocido** (m; x lateral, y altura, z a lo largo de la pista; centro en la mitad de la pista)
- 3 palos por extremo, en x = −0,0954 / 0 / +0,0954; base y punta a 0 y 0,711 m; z = ±10,059 (22 yardas).
- El largo de pista elegido por el usuario («yards stump to stump») no cambia este modelo; corrige el previo de la focal:
  fovy_previo = 2·atan((largo/22)·tan(fovy_hardware/2)).

**Detección de puntos clave** (`CameraCalibrationService.runCalibrationFromInputArray`, `getMaxima`, `nmsCalibration`)
- `calibration.tflite` produce 12 mapas de calor a 720×1280.
- Se toman como candidatos los píxeles con valor > 0,1. Se aplica NMS de 100 px por canal y se dejan como mucho 4 candidatos
  por canal.
- Los canales del extremo lejano (bateador) deben caer en el 40 % central del ancho.
- «Probar ambos lados» duplica cada detección en el canal del extremo opuesto, para los casos en que la red confunde los
  extremos.
- Con máquina solo se usan los 6 canales del bateador.

**Solución** (`ransacCameraCalibration` → `RansacCalibrationBase` → `RansacCalibration`, en `libnative-lib.so`)
- Cada correspondencia lleva 7 números: punto 3D, punto 2D, puntuación y canal. Hacen falta al menos 5.
- **RANSAC**:
  - 50 rondas en 5 hilos (`RansacWorker`);
  - muestreo ponderado por densidad o puntuación (`DensitySampler`, distribución constante a trozos), sin repetir canal en
    una muestra;
  - hipótesis iniciales aleatorias (`GetInitialEstimate`): cámara a 13–20 m del centro y 1,5 m de alto, mirando a la pista;
    fovy de 40 a 70° si no hay previo.
- **Refinado con Ceres** (`ceres::BuildProblem`, `MatchOptimization`):
  - residuo de **reproyección 2D** (`MatchReprojectionError`, autodiferenciado, 11 parámetros): punto principal (2),
    distorsión (2), posición (3), focal (1) y rotación eje-ángulo (3);
  - la distorsión es un modelo radial de tipo «campo de visión» (arcotangente o tangente del radio);
  - pérdida robusta de Huber y cotas: posición lateral ±20 m, altura 0,3–20 m, distancia 10–100 m; focal ±10 % del previo o,
    sin previo, fovy hasta 80–95°. La asignación de cada cota a cada eje es una inferencia.
- **Inliers.** `CountInliersNMS` cuenta los inliers fusionando las detecciones casi coincidentes. `Reoptimize` reajusta con los
  inliers (error < 6 px). `FinalCost`, `CheckCalibration` y `CheckRotationMatrix` controlan la verosimilitud: la cámara mira a
  la pista y la rotación queda dentro de una tolerancia de 5° por defecto.
- **Salida:** dimensiones, distorsión, fovy, posición, punto principal, rotación de Euler y **score**.
- **Aceptación y respaldo**
  - Se acepta con score > 3.
  - Si fallan más de 5 intentos, aparece un aviso en rojo.
  - Si no hay solución, queda la **calibración manual por defecto**: fovy 40°, cámara en (0; 1,7; 15) m e inclinación 6,21°.
    Con máquina: fovy 26° y (0; 1,5; 10,06). El usuario «alinea los palos virtuales».

**Validación del encuadre** (`CheckCalibrationServiceKt.checkUserCalibration`). Se hace con la geometría ya calibrada, no con
heurísticas de imagen:
- La cámara está a z ≥ 11,43 m, al menos 1,4 m detrás de los palos del no-bateador; el mensaje pide 3 m.
- El **punto de suelta** (x −0,4; y 2,0; z +10,06) se proyecta dentro de la imagen. Si se sale por arriba: «Tilt the camera up»,
  retroceder o alejar el zoom (si fovy < 30°). Si se sale por la izquierda: retroceder, alejar el zoom (si fovy < 40°) o
  «Point the camera left».
- Los **palos del bateador** miden ≥ 30 px en la imagen (≥ 50 px con máquina); si no, «Zoom in» o «Move back and zoom in».
  A esa distancia salen unos 2,4 cm/px, así que la pelota de 7,1 cm ocupa **≈ 3 px** en el extremo lejano.
- Si todo va bien, se guarda el «snapshot»: calibración, RGBA del fotograma y MP4 de un fotograma (`CalibrationSnapshot`). Se
  bloquea el enfoque y se pasa a esperar el lanzamiento.

### 4.4 Recalibración (`RecalibrationService.recalibrateBase`, `Recalibrate`, `RecalibrateBase`)

**Cuándo.** Cada 15 s mientras se espera un lanzamiento, solo si la calibración no es la manual.

**Cómo**
1. Compara el fotograma calibrado guardado con uno actual.
2. Usa ORB con 750 puntos, escala 1,2, 8 niveles, puntuación Harris y FAST 20.
3. Empareja con `BFMatcher` (Hamming, k = 2) y la prueba de Lowe a 0,75.
4. Ajusta con RANSAC + Ceres, con la cámara «en movimiento» (`ConvertParamsToCalibrationMoving`).

**Resultado**
- Acepta la nueva calibración con más de 28 inliers y la vuelve a codificar como snapshot.
- Si empieza una entrega, se aborta al momento (`requestAbortNative`, que el bucle de Ceres comprueba con
  `RansacAbortCallback`).
- Así se corrige un trípode golpeado o una deriva lenta sin pedir nada al usuario.

### 4.5 Modelo de cámara y corrección de perspectiva

**No exige vista de perfil.** Exige una vista **de frente a lo largo de la pista**, desde detrás de los palos del no-bateador, y
modela la perspectiva por completo con una cámara estenopeica:
- el punto de la cámara es R·(X − C), con R a partir de Euler (`EulerToRotationMatrix`);
- u = f·x/z + cx y v = f·y/z + cy;
- f = H / (2·tan(fovy/2)) (`CameraCalibration.project`, `projection::FovyToFocal`).

**Operaciones de la biblioteca C++ `projection::`**
- `Project`, `Unproject` y `Undistort`.
- `RaycastToPlane`, `ImageCoordsToPlane` e `ImageCoordsToZPlane`: de un píxel a un plano conocido, por ejemplo el suelo.
- `Compute2DRadius`: radio en píxeles de una esfera 3D (el tamaño esperado de la pelota).
- `ToCroppedCalibration`: corrige la calibración para un recorte.
- `ToUndistortedCalibration`, `WarpMap` (de una cámara a otra) y `FloorCalibration`.

Además, `getBoundingBox` proyecta cajas 3D (zona de suelta, bateador, miniatura) para recortar.

**Probable reparto con la nube** (sin confirmar). Lo propio de la nube (`LoadMatches`, `LoadFlows`, `Compute2DRadius`, que
ningún JNI usa) indica que es la misma biblioteca que corre en el servidor. Allí probablemente sirve para fijar el tamaño
esperado de la pelota y para pasar del píxel al suelo en el bote.

### 4.6 Detección y seguimiento de la pelota, y trayectoria

**Detección y seguimiento: en la nube; no está en el APK.** No hay modelo de pelota, tracker ni semillas en Android. De la nube
solo se conoce el **formato de salida**, `BallTrack`, con estos campos:
- `parameterXArray`, `parameterYArray` y `parameterZArray`: 3 coeficientes cada uno, de un polinomio de 2.º grado en el
  **índice de fotograma t**, que es fraccionario (subfotograma);
- `minFrameIdx` y `maxFrameIdx`, también fraccionarios;
- `pitchX` y `pitchY` (bote);
- `deviationArray` = [giro lateral tras el bote en rad, restitución vertical *e*, retención horizontal *k*];
- `speed`, `spin` y `swing`.

**Modelo del bote, reconstruido del código de dibujo** (`BallTrack.getPositionAtTime`)
- Antes del bote, las tres parábolas.
- El bote ocurre en t_b, cuando Y(t_b) = 0,0357 m.
- Después del bote:
  - la vertical conserva la curvatura y su velocidad cambia de signo, multiplicada por *e*;
  - la longitudinal sigue recta con su velocidad multiplicada por *k*;
  - la lateral sigue recta con el rumbo girado por `deviationArray[0]`.
- El tiempo se mide en fotogramas del vídeo procesado. El visor convierte `impactFrame` en ms con ÷ 60, lo que apunta a un eje de
  60 fps para los clips de alta velocidad (sin confirmar con clips de 30 fps).

**Ejemplo con la muestra incluida.** `assets/deliveryInfo.json` es una entrega de prueba que ningún código usa. Cálculos propios:

| Momento | t (fotograma) | Posición o dato |
|---|---|---|
| Suelta | 11,3 | x −0,50 m; y 2,32 m; z +8,80 m, junto a la línea de lanzamiento (*popping crease*) |
| Bote | ≈ 37,3 | (x −0,21; z −6,66): 3,40 m antes de los palos → zona **Full** |
| Fin | 44,5 | z −10,06, en los palos |

- La cámara de la muestra: f = 3240 px (fovy 22,3°), 1,15 m de altura, 18,3 m del centro, score 7,75.
- `speed` = 82,6 km/h. Encaja con la norma de la derivada del ajuste al inicio del vuelo a ≈ 30 fps (≈ 0,77–0,78 m/fotograma).
  La media hasta el bote sale en unos 65 km/h.
- La curvatura vertical de la muestra equivale a −7,8 m/s² a 30 fps. Parece un **ajuste libre por eje**, que no impone g.
- Todos estos cálculos son nuestros y quedan sin confirmar.

### 4.7 Cómo mide cientos de km/h con 30–60 fps (análisis)
- **Ajuste global, no diferencias finitas.** Un polinomio por eje con 25–35 fotogramas promedia el error de localización de cada
  fotograma. La velocidad sale de la derivada analítica, y los tiempos de suelta y de bote quedan en subfotograma.
- **Profundidad sin estéreo.** La cámara mira a lo largo de la pista, de modo que casi todo el desplazamiento va en profundidad.
  La calibración completa, el plano del suelo conocido y el bote, un punto 3D exacto en Y = r, más el modelo físico, fijan la
  escala a lo largo del eje óptico. `Compute2DRadius` sugiere que el tamaño aparente de la pelota también sirve de pista (sin
  confirmar).
- **Escala por geometría conocida.** Los palos a 20,12 m y los inliers en ambos extremos dan una escala métrica muy rígida.
- **Resolución mínima garantizada.** Los palos lejanos deben medir ≥ 30 px.
- **Precisión declarada: ninguna en la app.** El estudio externo E15 (iOS, 1081 entregas, citado en `CANDIDATAS_Y_METRICAS.md`)
  frente a radar halló sobreestimación sistemática de 0,72–0,77 m/s en lanzadores rápidos y de 1,09–1,18 m/s en spin, con ICC de
  0,87–0,90 y 0,72–0,76.

### 4.8 fps, captura y vídeo (`CaptureStrategyPicker`, `CameraSource`, `DeliveryWriter`, `CameraTimebase`)

**Estrategia de captura**
- Escalera, por orden de preferencia: **Native 60** → **CHSS 60** → **CHSS 120** → **Native 30**. CHSS es la sesión de alta
  velocidad restringida de Camera2: vista previa a 30 fps y codificador rotado 90°.
- Filtros para subir de peldaño:
  - el rango de fps de exposición automática de la cámara;
  - un tamaño de alta velocidad 16:9;
  - el códec H.264 que pueda sostener ese ritmo (*performance points* y presupuesto de macrobloques; `EncoderGateResult`);
  - una «prueba de humo» que transcodifica un clip corto y lo descarta si es lento.
- Degradación en marcha (`demoteStrategy`):
  - cuando detecta subejecución de HFR (`hfrPerformanceCheck`);
  - cuando el HAL rechaza el recorte del zoom (`CaptureQuirks.isChssCropUnsupported`);
  - por procesamiento lento.
- Vigilancia: térmica (`OnThermalStatusChangedListener` y lectura de `/sys/class/thermal`) y un vigilante que reemite la
  petición si pasan 3 s sin fotogramas.

**Exposición y enfoque**
- No hay obturador manual: la exposición automática queda acotada por el rango de fps (60/60 o 30–60).
- El zoom es digital, por `SCALER_CROP_REGION`, y se guarda el fovy efectivo: fovy = 2·atan(tan(fovy_max/2)/zoom).
- Tras calibrar se **bloquea el enfoque**: AF desactivado a la distancia medida, o `AF_TRIGGER` en CHSS.

**Codificación y búfer**
- H.264 por superficie a 720×1280: 6 Mbps (×0,6 en Native 30), keyframe cada 1 s, `operating-rate` = fps y prioridad de tiempo
  real.
- AAC a 44,1 kHz, mono, 64 kbps.
- **Búfer circular de NAL ya codificados:** 650 de vídeo, unos 10,8 s a 60 fps, y 250 de audio. El clip se corta desde el
  keyframe anterior a (inicio − pre-roll) hasta el final, sin volver a codificar.
- Los clips CHSS de 120 fps se **transcodifican a ≤ 60 fps** a 8 Mbps y se rotan a vertical (`Mp4RotatorTranscoder`). Al servidor
  nunca le llegan más de 60 fps.

**Base de tiempo** (`CameraTimebase`). Lee `SENSOR_INFO_TIMESTAMP_SOURCE` (REALTIME o UNKNOWN) y mide en el primer fotograma el
desfase con los dos relojes del sistema para alinear audio, vídeo y eventos.

**Cámara lenta de la galería:** no aplica. La app no importa vídeos; solo filma en vivo.

### 4.9 Controles de calidad y avisos al usuario
- **Cámara**
  - Comprobación de capacidades, con 60 s como máximo.
  - Códecs H.264 y AAC obligatorios: «Your device does not meet the requirements for filming».
- **Teléfono**
  - Vertical, con una inclinación lateral < 10° (acelerómetro a 10 Hz).
  - Quieto: \| \|a\|/g − 1 \| < 0,6.
- **Calibración**
  - Score > 3, más los 5 mensajes de encuadre geométrico de §4.3. «Redetect» y calibración manual como respaldo.
  - Recalibración silenciosa cada 15 s.
- **Grabación robusta**
  - Degradación de fps, prueba de humo, vigilancia térmica y del flujo, y reintentos al abrir la cámara (700 ms).
  - Si falta el audio, el clip se guarda sin él. Si falla la concatenación de FFmpeg, se envía solo el clip principal.
- **Subidas**
  - Se pausan con el dispositivo caliente («Uploading: Stopping, thermal state»), con el modo de ahorro y sin red.
  - Hay reintentos con contador y la entrega para DRS tiene prioridad («Aborting current video upload to prioritize DRS
    delivery»).
- **Telemetría de campo.** Datadog registra la estrategia elegida, la temperatura, los tiempos de calibración y disparo y la
  calidad de la escritura (fps efectivos del clip). RUM al 5 %, con consentimiento GRANTED por defecto.

### 4.10 Swing y mecánica con pose
- **No existe.** No hay modelo de pose (ni MediaPipe, ni MoveNet, ni BlazePose; ninguna cadena), ni fases, ángulos o tempo del
  lanzador o del bateador.
- Lo más cercano son las etiquetas de bateo de la nube: tipo de golpe, resultado, intención, aire o raso, juego de pies y wagon
  wheel. Las produce la IA del servidor más una **revisión humana de pago**. También hay, como derechos de plan sin detalle, el
  aterrizaje del pie (`delivery.view.foot_landing`) y `bat_data` (sin confirmar qué miden).
- No hay comparación entre atletas ni vídeo lado a lado.

---

## 5. Exportación y compartir

**Vídeo a la galería (Android)**
- Se hace con `MaidenSdk.saveDeliveryVideoToGallery` → `GalleryExportService` → `copyVideoToCameraRoll` y
  `addWatermarkToVideo`, de una en una o en bloque («You can download up to N videos at a time»).
- FFmpeg superpone `filmed_on_fulltrack.png`: un cuadrado del 45 % del ancho, en x = 6 % del ancho e y = 60 % del alto.
  Recodifica a 5 Mbps con AAC a 128 kbps y lo guarda con MediaStore en un álbum.
- El comando no fija el códec de vídeo, y el build de FFmpeg no trae libx264 (sí `mpeg4` y `h264_mediacodec`). Por eso
  probablemente sale en **MPEG-4 Part 2**, el códec por defecto de MP4 sin x264 (sin confirmar).
- **No dibuja la telemetría en el archivo.**
  - La interfaz pregunta «Export video with ball track animation?», avisa de que «Exports with ball track may take longer» y
    tiene `kBulkDownloadWithBallTrack` y `shouldIncludeBallTrack`.
  - Pero el lado nativo Android solo lee `albumName` y aplica la marca de agua, y en JS no hay módulo de FFmpeg.
  - La exportación con trayectoria es probablemente de iOS o del servidor (sin confirmar).

**Vista con telemetría dentro de la app** (`DeliveryViewFragment`)
- Una escena **Filament/SceneView** translúcida (`BlendMode.TRANSLUCENT`) sobre el reproductor, con el estadio oculto y la
  cámara virtual ajustada a la calibración real (`setCameraCalibration`: posición, rotación y fovy).
- Al acabar el vídeo, o en el fotograma de impacto, la trayectoria 3D se dibuja encima del fotograma real. Que encaje píxel a
  píxel queda sin confirmar, porque el modelo de Kotlin ignora la distorsión.
- Elementos: tubo rojo de 2,5 cm de radio y 34 segmentos, pelota animada en 1 s, cajas de Speed, Swing y Spin y de DRS, y banda
  de palo a palo.
- Cámara orbital con gestos y estadio en glTF (`assets/Stadium`) con iluminación IBL.
- Mapa de pique 3D con marcas de 2, 4, 6 y 8 m, «HALFWAY» y las zonas Yorker, Full, Good y Short en color.
- Los materiales incluyen `video_texture` y `video_texture_chroma_key`: el vídeo se puede usar como textura en 3D. No se vio
  ningún uso de chroma (sin confirmar).

**Datos y social**
- **CSV o «Export Data»:** el servidor manda al correo un enlace de descarga (`/data-export`). Es de pago.
- **Enlaces:** de sesión y de equipo por Branch y Firebase Dynamic Links, más un panel web: `www.fulltrack.ai?session=` y
  `?teamId=`.
- **Social:** «Share to Global Feed», comentarios por entrega y favoritos.
- **Visibilidad:** las sesiones gratuitas son **públicas**.
- **Nube:** S3 (`fulltrack-ai-production`), CloudFront, `api.fulltrack.ai/api/v…` y `comet.fulltrack.ai` (autenticación,
  derechos y metadatos).

---

## 6. Datos e integraciones

**Almacenamiento local**
- SQLite (`op-sqlite`) con tres tablas:
  - `deliveries`: id, sesión, vídeo, miniatura, equipo, `isDeliveryCreated`, `videoUploaded`, `balltrackUploaded`, `retryCount`
    y orden;
  - `camera_calibrations`: dimensiones, distorsión, punto principal, posición, rotación y fovy;
  - `ball_tracks`: todos los campos de `BallTrack`.
- Una tabla `storage` (clave/valor), AsyncStorage y el llavero (tokens), con limpieza de las sesiones huérfanas al reinstalar.

**Cuentas y datos externos**
- Cuentas: número de teléfono y código OTP por SMS o WhatsApp. El SDK de Google Sign-In está en el manifiesto, pero no se vio
  que se use.
- Contactos: solo para encontrar usuarios.

**Pagos y servicios**
- RevenueCat (Play y Amazon), web con Stripe y Paddle, y aviso de cobro doble entre tiendas.
- Notificaciones: FCM y Customer.io (push, mensajes en la app y bandeja).
- Analítica: Firebase, Segment y Datadog. Atribución: Branch.

**Sin integrar:** BLE, wearables, Health Connect y radar.

---

## 7. Las tres preguntas del usuario

**1. Perspectiva: ¿exige grabar a 90° de perfil o corrige ángulos oblicuos?**
- No graba de perfil: **exige una vista de frente**, a lo largo de la pista, desde detrás de los palos del no-bateador.
- La perspectiva la **resuelve por completo** con un modelo 3D de la cámara:
  - focal, punto principal, distorsión radial, rotación y posición;
  - estimado con RANSAC y Ceres a partir de 12 puntos clave de una geometría conocida (los palos);
  - con validación geométrica del encuadre y recalibración ORB si la cámara se mueve (§4.3–4.5).
- **No usa homografía del suelo ni elipses.** Proyecta y retroproyecta rayos a planos conocidos (`RaycastToPlane`). Solo en el
  DRS aparecen unas «homografías» que sirve el servidor.
- **Lección para KPKN.** Con una referencia de geometría conocida en la escena, el ángulo de la cámara deja de importar. Con un
  solo objeto (el disco) solo se corrige un plano; con 4 o más puntos no coplanares o un marcador se resuelve la cámara entera.

**2. Tracking híbrido: ¿qué hace sin el objetivo habitual?**
- No hay pose ni objetivos alternativos: solo pelota (en la nube) y palos (para calibrar).
- La «hibridación» es de modos:
  - calibración automática ↔ **manual**: palos virtuales que el usuario alinea a mano;
  - modo lanzador ↔ **modo máquina**: modelo disparador del bateador, calibración solo con los palos del bateador y cámara junto
    a los palos del no-bateador, con su propio previo de 26°;
  - pista de 22 yardas ↔ **largo a medida**, que corrige el previo de la focal.
- Si no ve los palos, no mide mejor: usa la calibración por defecto.

**3. Exportar vídeo con telemetría para redes: ¿existe y cómo es?**
- **En Android, no con telemetría.** Exporta el clip original con la marca de agua «Filmed on Fulltrack»: cuadrado del 45 % del
  ancho, en el tercio inferior izquierdo, a 5 Mbps.
- La interfaz anuncia una exportación «con la animación de la trayectoria», pero el código Android no la implementa;
  probablemente es de iOS o del servidor (sin confirmar).
- **La telemetría vive solo dentro de la app:** repetición 3D superpuesta con la cámara calibrada, cajas de velocidad, swing y
  spin, y DRS.
- Se comparte por enlace o panel web, en el «Global Feed» y en CSV por correo.
- No hay plantillas para redes ni «stickers». El clip ya sale vertical (720×1280, 9:16), pero sin datos encima.

---

## 8. Brechas frente a KPKN

Contexto de KPKN, según el resumen de `VIDEO_TECHNIQUE_V1.md`, `DIFERENCIADORES.md`, `CANDIDATAS_Y_METRICAS.md`,
`BANCO_COMPLETO.md`, `INFORME_CONFIANZA_20261020.md` y `MERCADO_FIABILIDAD.md`:
- pose MediaPipe local;
- disco con Hough, Kalman y NCC, escalado por el diámetro (`PlateScale`);
- `ViewClassifier`, `FramingEvaluator` y corrección de la elipse del disco;
- grabación a 60 o 30 fps con REC manual, sin 120/240;
- sin exportación MP4 (D44) ni CSV (D49);
- sin calibración geométrica (PnP/RANSAC);
- M35–M42 (pelota e implementos) sin implementar.

| Función o métrica de Fulltrack | ¿KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| **Validación del encuadre con la geometría calibrada**: proyectar los puntos críticos y exigir tamaños mínimos en px | Parcial (`FramingEvaluator`, por heurísticas de pose) | Tras fijar la escala (disco, barra o marcador), proyectar la trayectoria esperada y el objeto. Exigir, por ejemplo, el disco ≥ N px y el recorrido completo dentro del cuadro, con mensajes de acción: «aléjate», «baja el zoom», «gira a la izquierda» | — (geometría propia) | **Alta** | Bajo (2–3 días) |
| **Calibración de la cámara por puntos de referencia** + RANSAC + refinado con distorsión | No | Para gimnasio: **marcador impreso ChArUco/ArUco (D43.2)** o 4 o más puntos conocidos (bordes del disco, extremos de la barra, montantes del rack). `solvePnPRansac` + Levenberg–Marquardt con la focal como prior del zoom. Para pelota o implemento: líneas de pista o cancha conocidas | OpenCV (Apache-2.0: `aruco`, `calib3d`) · Ceres (BSD-3) o EJML (Apache-2.0) | **Alta** para M35, M36 y M42 · media para VBT | Medio-alto (1–2 semanas) |
| **Disparo automático por clasificador de 3 fotogramas** (fusión temprana, 12,5 Hz) | No (REC manual) | Primero sin ML: energía de movimiento de la pose o del flujo óptico, más un umbral con histéresis. Luego, un MobileNet minúsculo entrenado con clips propios (inicio de salto, lanzamiento, levantamiento) | TFLite / LiteRT (Apache-2.0) | Media-alta (manos libres en D48 y D50) | Medio |
| **Pre-roll con búfer circular de vídeo ya codificado** (NAL; recorte en el keyframe anterior) | No (solo el búfer de luma de VT4c) | MediaCodec por superficie con keyframe cada 1 s; anillo de NAL de ~10 s; MediaMuxer al disparar o al pulsar REC, sin recodificar | AOSP MediaCodec/MediaMuxer | Media-alta | Medio |
| **Escalera de captura 60 → 120 (alta velocidad) → 30**, filtrada por el códec, prueba de humo y temperatura | Parcial (60 → 30) | Sesión de alta velocidad de Camera2 o CameraX 1.5. Validar con `VideoCapabilities.areSizeAndRateSupported` y los PerformancePoints; ver si un clip de 1 s se transcodifica a tiempo; vigilar la temperatura y bajar de peldaño | AOSP Camera2 / CameraX (Apache-2.0) | **Alta** (D48 a 120–240; M35 y M39) | Medio (1 semana) |
| **Captura a 120 y bajada a 60 al guardar** | No | En KPKN conviene lo contrario: **analizar a 120/240** y guardar a 60 solo para mostrar. La exposición acotada por los fps reduce el desenfoque | Media3 Transformer | Media | Bajo |
| **Bloqueo de enfoque tras calibrar**, zoom digital con fovy registrado | Parcial (`focalLengthPx` para el paralaje) | Bloquear AF y AE tras la colocación; guardar el zoom y la focal efectiva en `capture.json`; avisar si cambian a mitad de serie | Camera2 | Media | Bajo |
| **Recalibración por ORB frente al fotograma de referencia** (teléfono golpeado) | No | Emparejar ORB entre el fotograma de inicio y los posteriores y estimar la homografía o la traslación del fondo. Si se mueve más de X px: marcar «cámara movida» y compensar la trayectoria o rebajar la confianza (`RepConfidence`). Es lo que MERCADO_FIABILIDAD critica a Metric | OpenCV (Apache-2.0) | **Media-alta** | Medio |
| **Estabilidad y nivel por acelerómetro antes de grabar** | Parcial (roll por gravedad) | Puerta \| \|a\|/g − 1 \| < umbral más inclinación < 10° antes de empezar; aviso «teléfono inestable» | AOSP SensorManager | Baja-media | Bajo |
| **Base de tiempo de la cámara** (fuente REALTIME y desfase) | Parcial (PTS reales) | Registrar `SENSOR_INFO_TIMESTAMP_SOURCE` y el desfase en `capture.json`; necesario para fusionar con IMU o audio | — | Baja-media | Bajo |
| **Trayectoria paramétrica ajustada a todas las detecciones** (polinomio por eje, tiempos de subfotograma, derivada analítica) | No (solo derivadas filtradas de VBT) | Para M35, M36 y M42: tras la suelta, ajustar x(t) lineal + cuadrático pequeño e y(t) con **g fija** como restricción. v0 y el ángulo salen del ajuste, y el instante de suelta, de la intersección con la mano (pose). Con vista lateral y escala, una **idea propia de KPKN** (no es de Fulltrack): sin referencia de escala, la curvatura vertical frente a g = 9,81 da los m/px | Mínimos cuadrados propios · EJML / commons-math (Apache-2.0) | **Alta** | Medio (1 semana) |
| **Bote o aterrizaje como ancla 3D** (plano del suelo conocido) | No | Detectar el contacto (pelota o implemento al tocar el suelo, pie en el salto) y retroproyectar al plano del suelo calibrado (`RaycastToPlane` equivalente) | — | Media (saltos y lanzamientos) | Medio |
| **Velocidad de pelota o implemento** (M35, M42) | No | Requiere detector del objeto (D47, geométrico; sin YOLO por la AGPL), tracker y calibración. Fulltrack deja todo esto en la nube: KPKN lo haría en local a 120 fps o más | TFLite + OpenCV | **Alta** (M35: prioridad 7) | Alto (> 2 semanas) |
| **Punto y altura de suelta, dispersión** (Release Points) | No | Posición del objeto en la suelta, desde la pose (muñeca) y el primer punto libre; dispersión entre repeticiones | — | Media-alta (M36) | Bajo, si existe M35 |
| **Curvatura lateral («swing force»)** como coeficiente del ajuste | No | Aceleración lateral = 2·coef. cuadrático × fps² × escala; útil en lanzamientos con efecto | — | Baja | Bajo |
| **Agregados robustos de sesión**: mediana → quitar > 2σ → mediana, con texto explicativo | Parcial | Mismo esquema para MCV, ROM o tempo de la serie; el texto de «cómo lo calculamos» junto a la cifra | — | Media | Bajo |
| **Repetición 3D con la cámara igualada a la calibración**, mapa de pique 3D | No (capa 2D sobre el vídeo) | Proyectar la trayectoria 3D con la calibración sobre el vídeo en un Canvas de Compose; luego una vista orbital opcional | Filament / SceneView (Apache-2.0) | Baja-media | Alto |
| **Exportación**: vídeo con marca de agua | No (D44 plantea más: telemetría quemada) | D44 ya supera a Fulltrack Android. Copiar solo lo útil: marca discreta en una esquina y recodificación única | Media3 Transformer / FFmpegKit LGPL | **Alta** (D44) | ~1 semana (DIF) |
| **CSV por correo, enlaces y panel web** | No (D49 plantea el CSV local) | CSV local con compartir de Android; sin nube | — | Media | Bajo |
| **Modo «máquina»**: cambia el disparador y la calibración | — | Equivalente en KPKN: perfiles de captura por equipo (barra, polea o máquina) que cambien el objetivo y la puerta de calidad | — | Baja | — |
| **Juego «Out / Not out»** | No | Gamificación: «adivina la velocidad o el RPE de la serie» antes de ver el dato | — | Baja | Bajo |
| **Equipos, etiquetado, auto-tagging por clústeres, comentarios y feed** | No | Fuera del alcance (KPKN es individual y local) | — | Baja | Alto |
| **Revisión humana de pago** («high-accuracy QA») | No | Contrario a la filosofía local de KPKN; su equivalente es «Usar / Corregir» del usuario | — | Baja | — |
| **Telemetría de campo** (Datadog: estrategia, temperatura, latencias) | No (solo local) | Diagnóstico local exportable para el banco (D41): estrategia elegida, fps efectivos, temperatura y descartes | — | Baja-media | Bajo |

**Lo que KPKN hace mejor que Fulltrack**

*Privacidad y disponibilidad*
- **Todo en el teléfono:** sin red, sin esperar «a few minutes» y sin colas de subida.
- Fulltrack necesita la nube para cualquier métrica, y sus sesiones gratuitas son públicas.

*Mecánica*
- **Pose y mecánica**: fases, ROM, tempo, simetría y profundidad. Fulltrack no estima pose; su bateo son etiquetas de nube con
  revisión humana.

*Honestidad del dato*
- **Contrato de abstención y confianza** (ALTA/MEDIA/BAJA, «≈», «nunca aplica solo»). Fulltrack muestra la cifra sin
  incertidumbre y solo se abstiene con «n/a», «Upgrade» o «sin procesar».
- Además, el estudio E15 documenta un **sesgo sistemático** de su velocidad.

*Tiempo y fps*
- **PTS reales, VFR y cámara lenta de la galería** (`fs` = 1/mediana(ΔPTS)).
- El visor de Fulltrack convierte el fotograma de impacto suponiendo 60 fps (`impactFrame` / 60) y no importa vídeos.

*Alcance*
- **Multideporte y multiejercicio**: 23 perfiles. Fulltrack es solo críquet y solo funciona con 22 yardas y palos estándar.

*Plan de exportación*
- **D44 dibujará la telemetría en el MP4.** Fulltrack Android exporta solo la marca de agua.

---

## 9. Índice de evidencias

### 9.1 Rutas del árbol descompilado
El árbol estaba en `C:\kw\re\fulltrack\jadx\sources\`. Se borró al terminar y se regenera con
`C:\kw\re\_scripts\fulltrack\run_jadx_app.ps1` y `run_jadx_bridge.ps1`.

| Tema | Clase · método |
|---|---|
| Disparo automático | `cricketanalytics.maiden.Services.MobilenetChunkingService` · `initialize`, `pushRgbaFrame`, `processFrameMobilenet`, `fillChunkInput`; `Helpers.CameraSource$startChunkingDriver$driver$1.run` |
| Calibración | `Services.CameraCalibrationService` · `calibrateImageRgba`, `runCalibrationFromInputArray`, `getMaxima`, `nmsCalibration`, nativo `ransacCameraCalibration`; `Services.StumpDetection` |
| Validación del encuadre | `Services.CheckCalibrationServiceKt` · `checkUserCalibration`, `checkUserCalibrationStrikerOnly` |
| Recalibración | `Services.RecalibrationService` · `recalibrate`, `recalibrateBase`, `onDeliveryStart`, nativos `ransacRecalibrate`, `requestAbortNative`, `clearAbortNative` |
| Estados | `States.CameraCheckState`, `DeviceCalibrationState`, `CameraCalibrationState` (`onCalibrationReceived`, `checkSetup`), `PreDeliveryState` (`checkAndClose`), `DeliveryState`, `ProcessDeliveryState`; `States.Event` |
| Sensores | `Services.MotionSensor.getRotatedAngle`; `Services.DeviceRotation` |
| Captura | `Helpers.camera.CaptureStrategy` (`Native`, `Chss`, `chunkingFrameStride`, `chunkingBurstStride`, `getBitrateMultiplier`), `CaptureStrategyPicker` (`pickStrategyLadder`, `strategyRank`, `EncoderGateResult`), `Helpers.CameraSource` (`demoteStrategy`, `armHfrPerformanceCheck`, `runSmokeTranscodeProbe`, `lockFocus`, `adjustZoom`, `fovyForZoom`, `startStreamStallWatchdog`), `Helpers.CameraTimebase`, `Helpers.CaptureQuirks` |
| Vídeo | `Helpers.DeliveryWriter` (`markDeliveryStart`, `finaliseDelivery`, `runFinalise`), `Helpers.encoding.EncodedRingBuffer.snapshotForDelivery`, `encoding.VideoEncoder`, `Helpers.Mp4RotatorTranscoder.transcode`, `Helpers.CalibratedFrameEncoder.encodeOneShot`, `Helpers.VideoUtilsKt` (`mergeVideos`, `tryConcatDemuxerCopy`, `imageFromVideo`, `addWatermarkToVideo`, `copyVideoToCameraRoll`) |
| Modelos de datos | `models.BallTrack` (`getBounceTime`, `getPositionAtTime`, `formattedSpeed`, `formattedSwing`, `formattedSpin`), `models.BallTrackKt` (`swingScaleFactor`, `REDACTED_SSS_VALUE`), `models.CameraCalibration` (`project`, `getBoundingBox`), `models.Stroke`, `models.DRSReview`, `DRSDecision`, `DRSLocation`, `models.DeliveryDetails` |
| Visor y 3D | `Activities.DeliveryViewActivity` (`switchTo3DView`, `setVideo`, `toggle3DPitchMap`), `Fragments.DeliveryViewFragment` (`setupBallTrack`, `drawTube`, `buildTubeMesh`, `setCameraCalibration`, `show3DPitchMap`, `pitchLengthMarkers`, `DeliveryLengthRegion`), `CustomNodes.OrbitingCameraNode`, `CustomViews.CalibrationGuideView`, `BattingFeaturesView`, `DRSView`, `UpgradeView` |
| Juego | `games.OutNotOutGame`, `Activities.ViewingGameActivity` |
| Constantes | `Helpers.Constants` (resumen en `constantes_sdk_maiden.txt`) |
| Puente RN | `com.reactlibrary.MaidenSdkModule` (`openSession`, `showDelivery`, `saveDeliveryVideoToGallery`, `setBallTracking`, vacío, …), `DeliveryRecordingCallback`, `DeliveryViewCallback` |

### 9.2 Nativo
`libnative-lib.so`, arm64. Las direcciones son de Ghidra (+0x100000 sobre el ELF): `RansacCalibration` @00119304,
`RansacCalibrationBase` @00119e60, `GetInitialEstimate` @001151d0, `CountInliersNMS` @00115418, `Reoptimize` @00118d78,
`FinalCost` @00118650, `CheckCalibration` @00118ad4, `ceres::BuildProblem` @00111108, `ceres::MatchOptimization` @001114fc,
`ceres::MatchReprojectionError::operator()<double>` @00112074, `Recalibrate` @00116510, `RecalibrateBase` @001171d0 y
`projection::*` entre @0011cf90 y @00120850. Imports: `cv::ORB::create`, `cv::BFMatcher::create`,
`cv::DescriptorMatcher::knnMatch`, `cv::Rodrigues`, `ceres::Solve`, `ceres::HuberLoss`, `SetParameterLower/UpperBound` y
`SetParameterBlockConstant`.

### 9.3 Bundle Hermes
Identificadores y cadenas: `MaidenSdk`, `RUN_BALL_ON_CLOUD_EVENT`, `UPDATE_BALL_TRACK_EVENT`, `FETCH_DRS_HOMOGRAPHIES_EVENT`,
`rawSwingToSwingForce`, `SWING_FORCE_SCALE_FACTOR`, `shouldClientClassifyLengthFilter`, `withBallMetricRanges`,
`useWagonWheelDerivedState`, `kBulkDownloadWithBallTrack`, `shouldIncludeBallTrack`, `delivery.view.*` y
`delivery.qa.high_accuracy`. También las tablas SQL `deliveries`, `camera_calibrations` y `ball_tracks`.

### 9.4 Sin confirmar
- Los umbrales de línea y el n mínimo de los agregados robustos (en bytecode Hermes, sin desensamblar).
- La fórmula exacta de `speed` y de `swing` en el servidor.
- El significado de `foot_landing` y `bat_data`.
- La exportación con trayectoria (iOS o servidor).
- El seguimiento en el dispositivo en iOS.
- La asignación exacta de cada cota de Ceres a cada eje.

### 9.5 Archivos en `C:\kw\re\evidencias\fulltrack-re-20261020\` (≈ 0,5 MB)
- `inventario_xapk.json` (tamaños y SHA-256), `base_apk_resumen.txt`, `arm64_bibliotecas_nativas.txt` (46 `.so` con SHA-256) y
  `versiones_bibliotecas.txt`.
- `modelos_tflite.txt` (formas, histograma de operaciones y SHA-256 de los 4 modelos).
- `libnative-lib_simbolos.txt`, `libnative-lib_ghidra_indice.tsv` y `libnative-lib_constantes.txt` (constantes numéricas por
  función).
- `dex_clases_app.txt`, `dex_paquetes_resumen.txt` e `indice_clases_metodos.txt` (firmas, sin cuerpos).
- `hbc_cabecera.json`, `hbc_funciones_app.txt`, `hbc_cadenas_app_filtradas.txt` (redactadas) y `hbc_claves_i18n.txt`.
- `AndroidManifest.decodificado.xml`, `api_rutas_y_eventos.txt` (sin claves) y `constantes_sdk_maiden.txt`.

### 9.6 Herramientas y proceso
- **REA 6.5.0** con sus variables: `decode-android-resources`, con apktool 3.0.3, para el manifiesto y las cadenas.
- **JADX:** `jadx-core` del JAR `jadx-headless-mcp-0.7.1` de REA, con un programa propio (`AppDecompile.java`) que filtra por
  paquete.
  - Primera pasada: `cricketanalytics.*` y `ai.fulltrack.*`, 117 archivos, 36 s.
  - Segunda pasada: `com.reactlibrary.*`, 32 s.
- **Ghidra 12.1.4** en modo *headless* directo (`run_ghidra_native.ps1` y `ghidra_scripts/ExportFtDecomp.java`):
  `-deleteProject`, 164 funciones exportadas, 51 s.
- Cada herramienta pesada se lanzó **con el candado** `C:\kw\re\pesado.lock` (tres veces, tomado y liberado) y con ≥ 6 GB de RAM
  libre comprobados en cada lanzamiento (entre 8,1 y 11,2 GB).
- **Scripts propios** con `python -I` en `C:\kw\re\_scripts\fulltrack\`: `unpack.py`, `ziplist.py`, `dex_classes.py`,
  `dex_static_ints.py` (verificó GmsVersion), `hbc_parse.py` (lector HBC v96), `str_grep.py`, `app_strings.py`,
  `tflite_shapes.py`, `tflite_ops.py`, `hexconst.py` y `sig_index.py`.
- **LLVM del NDK 27:** `llvm-nm`, `llvm-readelf` y `llvm-strings`.
- **Limpieza:** se borró `C:\kw\re\fulltrack\` (APK extraídos, nativos, árbol JADX, salida de Ghidra, volcados Hermes y el JSON
  de recursos con claves). No quedan procesos java ni Ghidra propios.
