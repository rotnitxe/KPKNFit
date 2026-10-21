# Cali Lens 4.3 (Android) · ingeniería inversa estática · el archivo NO es el analizador de calistenia

Fecha: 2026-10-21. Encargo: brief `docs/video-technique/briefs/RE_COMUN.md` más la nota del usuario (dominadas, fondos, flexiones y
elementos estáticos de calistenia; criterios de repetición válida, forma y cronometraje de los isométricos). Trabajo en sala limpia:
nada del APK se ejecutó ni se instaló; no se copió código ni se extrajeron modelos o recursos. Las descripciones son con palabras
propias; como evidencia solo van nombres de clases y constantes. Lo que no se pudo verificar lleva «sin confirmar».

Herramientas: REA 6.5.0 (`decode-android-resources`, apktool 3.0.3), apktool 3.0.3 directo (solo recursos, sin fuentes), `apksigner`
37.0.0 de las build-tools del SDK (solo lee las firmas) y scripts propios con `python -I` en `C:\kw\re\_scripts\calilens\` que leen
las tablas de cadenas, tipos y clases de los 4 DEX **sin descompilar**. No hizo falta JADX ni Ghidra: no se tomó el candado
`C:\kw\re\pesado.lock` y no quedó ningún proceso vivo. Pruebas pequeñas en `C:/kw/re/evidencias/calilens-re-20261020/` (≈ 85 KB). Las
fuentes web públicas (solo lectura, 2026-10-21) están en el §10; todo lo que sale de ellas va marcado **[W]** y **no es ingeniería
inversa**.

**Lo esencial, antes del detalle:**
- **El archivo analizado no es CaliLens, el analizador de calistenia.** `Cali+Lens_4.3_APKPure.xapk` contiene `co.shopney.calilens`
  4.3 (versionCode 33): la **aplicación de tienda en línea «CALI · Lenses & Lashes»** (el icono dice «Cali – Lenses & Lashes», con
  caligrafía árabe), generada con un **constructor de apps para Shopify** de la familia MageNative / CedCommerce y firmada por
  «Shopney Inc.» (Newark, Delaware). Solo comparte el nombre con la app de calistenia.
- **Prueba, en siete puntos** (detalle en §1 y §3):
  1. 0 bibliotecas nativas (no hay `lib/`) y 0 modelos (`.tflite`, `.task`, `.onnx`…);
  2. 0 clases de pose, MediaPipe, TensorFlow o CameraX; solo hay el etiquetador genérico de imágenes de ML Kit (24 clases);
  3. 0 coincidencias de calistenia o ejercicio en los 136 `strings.xml` (el predeterminado y 135 idiomas) y solo 4 falsos positivos
     en 251 266 cadenas de DEX;
  4. las 2 241 clases propias (`co.shopney.calilens.*`) son de tienda: 650 consultas GraphQL de Shopify, producto, carrito, pedidos,
     recompensas, reseñas, inicio de sesión;
  5. el SDK de Shopify, Yotpo, Judge.me, Flits, Zendesk, Facebook y los dominios del constructor, y ni un dominio de fitness;
  6. no hay grabación ni codificación de vídeo (ni `MediaRecorder` ni `MediaMuxer`) ni carga dinámica de código;
  7. por lo que muestran las clases y los recursos, la cámara solo sirve para escanear códigos de barras, hacer fotos en las reseñas y
     adjuntar imágenes en el chat de soporte.
- **Consecuencia:** de este APK no se puede sacar **nada** para el catálogo de elementos, las reglas de repetición, el cronometraje
  de isométricos, el modelo de pose, las puntuaciones ni la exportación de vídeo. Las tres preguntas del usuario no aplican (§7).
- **La app que el usuario quería es otra:** «CaliLens: Calisthenics AI», de Xavier Facqueur (XF Studio, París), paquete de Play
  `com.calilensapp.app` (así consta en `CANDIDATAS_Y_METRICAS.md`), web `calilens.app`. Su ficha de iOS lista versiones de la 1.0.0
  a la 1.0.4 (la numeración de Android, sin confirmar, pero no es «4.3»). **Hace falta ese APK** para repetir el encargo; aquí no se
  puede descargar (prohibido por el brief).
- **Para no perder la ronda,** el §8 y los Anexos A y B recogen lo que la web pública de esa app declara y proponen un motor de
  isométricos para KPKN. Todo es **[W]** o propuesta: ninguna cifra está confirmada en código.
- **Orden de impacto para KPKN (todo [W] o propuesta):**
  1. cronómetro de isométricos que solo suma los segundos con forma válida, auditable segundo a segundo (hoy `ISOMETRIC` no se
     analiza): Anexo B, ~1,5 semanas;
  2. catálogo de habilidades estáticas y variantes con escaleras y puertas de progresión (el catálogo v2 no trae ninguna, ni el
     muscle-up);
  3. clase de vista de cuerpo invertido y encuadre por habilidad (la bandera humana pide vista frontal); el banco ya trae 44 clips
     de flexión en pino y 5 de plancha para medir la pose boca abajo;
  4. bloqueo de codo en flexiones y fondos, y muscle-up;
  5. **no copiar:** la nota global 0–100, la posición de la cabeza, el sello «Certified» como marca de exactitud, ni los umbrales de
     las guías sin calibrar; la protracción escapular, la pelvis y la espalda baja no se ven con 33 puntos.

---

## 1. Identidad

| Dato | Valor |
|---|---|
| Paquete · versión | `co.shopney.calilens` · 4.3 (versionCode 33) · minSdk 24 · targetSdk 36 · compileSdk 36 |
| Nombre y marca | «Cali lens» (los 135 nombres localizados del `manifest.json` son iguales); icono: «CALI · Lenses & Lashes» |
| Archivo | `Cali+Lens_4.3_APKPure.xapk`, 20 993 919 B, SHA-256 `630d404d938ff88b5eccf929ebf3b76d67191e2b3f3f2fa2b44206b08224f041` |
| Firma | APK Signature Scheme v2 y v3 (la base) y v1+v2+v3 (el split). Titular: CN=Shopney Team, OU=Shopney Mobile App, O=Shopney Inc., L=Newark, ST=Delaware, C=US; RSA 2048; SHA-256 del certificado `c870da63f7f44785a333f2dba4297731bcfcfd9c3f5782b135e8d32dc24ca4d0` |
| Sello de Play | `STAMP_TYPE_DISTRIBUTION_APK`, `com.android.stamp.source = https://play.google.com/store`; sello firmado por «Google Inc.» con fecha 2026-08-06 06:48 UTC; `requiredSplitTypes = base__density` |
| Constructor | Gradle 8.10.2, AGP 8.8.0, plugin de Kotlin 1.8.0, Java 18; revisión git del proyecto `a54a92f1449889e24fbe714648c363aac888b4d2` (`version-control-info.textproto`) |
| Arquitectura | Android nativo (Java y Kotlin), MVVM con DataBinding, Dagger 2.40, RxJava 2, Retrofit/OkHttp, Room 2.4.3 y corrutinas 1.6.4; GraphQL de Shopify con el SDK `com.shopify.buy3`. **No** es Flutter, React Native, Unity ni un WebView empaquetado (hay una `Weblink` y una `CheckoutWeblink` para páginas web). Sin ofuscar en el código propio (clases con nombres legibles) |
| ABIs y nativo | **Ninguno.** No hay `lib/` (`extractNativeLibs = false`); todo es bytecode |
| Modelos de ML | **Ninguno en el APK.** Solo la dependencia de ML Kit *Image Labeling* sin empaquetar (`play-services-mlkit-image-labeling` 16.0.8; metadato `com.google.mlkit.vision.DEPENDENCIES = ica`): el modelo lo descarga Google Play Services, así que su tamaño, entrada y salida quedan **sin confirmar** (la API genérica devuelve etiquetas con confianza). Quién la llama: sin confirmar (probablemente la plantilla de búsqueda por imagen) |
| Recursos | 136 `strings.xml` (el predeterminado y 135 idiomas), 396 layouts, 2 290 cadenas por defecto (≈ 457 propias, el resto de bibliotecas), 488 archivos en `res/raw` (486 husos horarios de Joda, un pitido `.ogg` de ZXing de 6 KB y un `.xml` de Firebase) |
| DEX | 4 archivos: `classes.dex` 9 617 256 B (8 042 clases), `classes2.dex` 9 641 868 (10 000), `classes3.dex` 7 389 704 (7 202) y `classes4.dex` 10 261 020 (8 484); 33 728 clases |

**APK internos:**

| Archivo | Bytes | SHA-256 |
|---|---|---|
| `co.shopney.calilens.apk` (base) | 20 714 167 | `062599488322251d30d695410c25767a1102c066b4f3e0b2ef26dffa7372dda8` |
| `config.xxxhdpi.apk` (densidad) | 188 611 | `84ec5f439f2bd4705afa152bd2b5cf06ad0aa158096118e8e1ddc4c6c805a656` |
| `icon.png` · `manifest.json` | 86 895 · 3 688 | (no son APK) |

**Permisos:** `CAMERA` (con `uses-feature` opcional para cámara, frontal, enfoque automático y flash), `INTERNET`,
`ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VIBRATE`, `FOREGROUND_SERVICE`,
`READ_EXTERNAL_STORAGE` y `WRITE_EXTERNAL_STORAGE`, `REORDER_TASKS`, `c2dm.permission.RECEIVE` (FCM), instalación referida de Play,
`AD_ID` y las cuatro `ACCESS_ADSERVICES_*`, más un permiso propio de firma (`…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`). También
`usesCleartextTraffic = true` y `allowBackup = false`. **Sensores: ninguno declarado** (ni acelerómetro ni giroscopio).

**Bibliotecas de terceros** (versión por los `META-INF/*.version` y los `*.properties`; la licencia es la habitual del proyecto, no
se verificó en el archivo salvo donde se indica):

| Biblioteca | Versión | Licencia |
|---|---|---|
| AndroidX: activity 1.7.0, appcompat 1.7.0, core 1.15.0, fragment 1.5.4, recyclerview 1.2.1, room 2.4.3, work 2.7.1, databinding 8.8.0, media 1.4.3, browser 1.4.0, startup 1.1.1, profileinstaller 1.3.1, viewpager2 1.0.0, transition 1.2.0 | las indicadas (lifecycle y constraintlayout: sin confirmar) | Apache-2.0 |
| Material Components | 1.6.1 | Apache-2.0 |
| Dagger | 2.40 | Apache-2.0 |
| kotlinx-coroutines | 1.6.4 | Apache-2.0 |
| Firebase: auth 21.0.8, database 20.0.0, analytics 19.0.0, crashlytics 18.2.13, in-app messaging y display 20.1.3, common-ktx 20.1.2, datatransport 18.1.7, transport-runtime 3.1.8 | las indicadas | Apache-2.0 el cliente; el servicio, términos de Firebase |
| Google Play services: base y basement 18.1.0, auth 20.3.0, auth-api-phone 18.0.1, maps 18.1.0, measurement 19.0.0, ads-identifier 17.0.0, cloud-messaging 17.0.1, tasks 18.0.2 | las indicadas | propia de Google (licencia del SDK de Android) |
| Play Core: app-update 2.1.0, review 2.0.1, feature-delivery 2.1.0 | las indicadas | licencia del SDK de Android |
| ML Kit: image-labeling-common 18.1.0, image-labeling-default-common 17.0.0, vision-common 17.2.0, vision-interfaces 16.1.0, common 18.5.0 | las indicadas | términos de ML Kit |
| Shopify Mobile Buy SDK (`com.shopify.buy3`, 1 257 clases) | sin confirmar | MIT |
| ExoPlayer 2.x (`com.google.android.exoplayer2`, 2 188 clases), Glide, Picasso, RxJava 2, Retrofit 2, OkHttp 3, Gson, Joda-Time (con zonas en `res/raw`), ZXing y zxing-android-embedded, Dexter (Karumi), MaterialDateTimePicker (wdullaer), ViewPagerDotsIndicator, CircleIndicator, SpinKit (`com.github.ybq`), Flexbox, Guava, gRPC y Protobuf | sin confirmar | Apache-2.0 (Glide: BSD, MIT y Apache-2.0; SpinKit: MIT; Protobuf: BSD-3) |
| Zendesk Chat, Messaging y Belvedere | sin confirmar | licencia propia de Zendesk |
| SDK de Facebook (login, eventos, compartir) | sin confirmar | licencia de la plataforma de Facebook |
| JUnit 4 y AndroidX Test/Espresso **empaquetados en la versión de producción** (`LICENSE-junit.txt`, actividades `InstrumentationActivityInvoker$*`) | sin confirmar | EPL-1.0 (JUnit) y Apache-2.0 (AndroidX Test) |

**Higiene del constructor (sin copiar valores):** el APK trae en claro una clave de API de Google, el token de cliente de Facebook y
credenciales de Instagram de la plantilla del constructor, y el dominio de tienda por defecto de los recursos (`shop`) es el de la
tienda demo de MageNative, no el de «Cali». La configuración real de la tienda se descarga al primer arranque (cadenas
`downloadingconfig` y `downloadcomplete`): **la tienda concreta, sin confirmar**.

---

## 2. Inventario de funciones (de la app de tienda)

Inferido del manifiesto, los nombres de clases, los recursos, los dominios y las cadenas; **sin descompilar** (no hacía falta para
decidir que no es la app de calistenia). Es una tienda Shopify estándar del constructor. **No hay funciones de pago de la propia app**
(suscripciones ni niveles premium): lo único que se paga son los productos de la tienda.

| Pantalla (clase) | Qué hace |
|---|---|
| Arranque (`basesection.activities.Splash`) | Descarga la configuración de la tienda al primer arranque («Downloading configuration…»); comprueba modo mantenimiento (`MaintenenceActivity`) y la prueba del constructor (`TrialExpired`, `LivePreview` «Live Preview Of Your Store») |
| Contenedor (`NewBaseActivity`, `LeftMenu`) | Menú lateral, barra superior, contadores de bolsa y de favoritos, selector de idioma, de moneda y de país |
| Inicio (`homesection.activities.HomePage`) | Portada dinámica: banners y deslizadores, categorías circulares y cuadradas, colecciones, más vendidos, tendencias, vistos recientemente, destacados, feed de Instagram (`com.shopify.instafeeds`) |
| Colecciones y listado (`CollectionList`, `ProductList`, `FilterPage`) | Cuadrícula o lista, filtros por precio y atributos, orden A–Z, Z–A, precio, más vendidos, destacados y fecha |
| Producto (`ProductView`, `VariationsActivity`, `ZoomActivity`, `VideoPlayerActivity`) | Variantes y muestras de color, cantidad, bolsa, comprar ya, favoritos, compartir, aviso de reposición («notify me»), disponibilidad por código postal y recogida en tienda (Zapiet), guía de tallas (Kiwi Sizing), vista 360 y AR (visor de escenas de Google, `ArImagesAdapter`), vídeo del producto (ExoPlayer), zoom, productos similares y «comprados juntos» (recomendaciones de Loop Club), precios mayoristas (Wholesale Helper) |
| Reseñas (`AllReviewListActivity`, `AllJudgeMeReviews`, `JudgeMeCreateReview`, `AllAliReviewsListActivity`, `WriteAReview`, Yotpo) | Lectura y escritura de reseñas con foto (Judge.me, Ali Reviews, Yotpo y formulario nativo) |
| Búsqueda (`AutoSearch`, `SearchByScanner`, módulo Algolia) | Búsqueda predictiva con historial reciente; escáner de códigos de barras y QR (ZXing, `CaptureActivity`) |
| Bolsa (`CartList`, `SubscribeCartList`, `quickadd_section`) | Líneas, cantidades, cupones, tarjetas de regalo, créditos de tienda (Flits), puntos canjeables, planes de suscripción (selling plans), añadir rápido |
| Pago (`NativeCheckoutAddressPage`, `ShippingMethod`, `CardDataActivity`, `CheckoutWeblink`, `OrderSuccessActivity`) | Dirección, método de envío, datos de tarjeta nativos o pago web, Google Pay habilitado (metadato de Wallet), confirmación |
| Pedidos (`OrderList`, `OrderDetails`, ReturnPrime, Shipway) | Historial, detalle, seguimiento, repetir pedido, devolución o cambio |
| Favoritos (`WishList`) | Lista local sincronizada con Flits |
| Cuenta (`dashboard.AccountActivity`, `UserProfile`, `AddressList`) | Perfil, cambio de contraseña, borrar cuenta, libreta de direcciones con país, estado y ciudad (`country_list.json`) |
| Acceso (`LoginActivity`, `RegistrationActivity`) | Correo y contraseña, Facebook, Google; recuperar contraseña por enlace o código de 6 dígitos |
| Recompensas (`yotporewards.*`, Rewardify) | Panel, ganar puntos por acciones, canjear por descuentos, historial, niveles VIP, invitar amigos, preguntas frecuentes, puntos sin sesión |
| Soporte (`zendesk.messaging.MessagingActivity`) | Chat con agente (Zendesk), preguntas frecuentes, enlace a WhatsApp |
| Notificaciones | FCM, mensajes dentro de la app (Firebase In-App Messaging), arranque tras reinicio y trabajos con WorkManager |

**Ajustes y flujos:** idioma, moneda, país del teléfono, notificaciones del sistema. **Sin** pantalla de cámara propia, de vídeo
grabado, de entrenamiento, de métricas ni de resultados corporales.

---

## 3. Catálogo de métricas

**No hay ninguna.** La aplicación no mide cuerpo, movimiento, repeticiones, tiempos de sostén, ángulos ni velocidad. Sus únicas
cifras son comerciales: precios, impuestos, envío, descuentos, puntos de fidelidad y valoraciones de producto.

Prueba negativa (reproducible con `05_busqueda_negativa.json` y los scripts de `C:\kw\re\_scripts\calilens\`):

| Búsqueda | Resultado |
|---|---|
| `lib/*.so` en el APK base y en el split | 0 |
| Modelos `.tflite`, `.task`, `.onnx`, `.pb`, `.ptl`, `.mlmodel`, `.binarypb` | 0 |
| Clases `com.google.mlkit.vision.pose`, `com.google.mediapipe`, `org.tensorflow`, `androidx.camera` | 0 de cada |
| Clases `com.google.mlkit.vision.label` (etiquetado genérico de imágenes) | 24 |
| `MediaRecorder`, `MediaMuxer`, `DexClassLoader`, `InMemoryDexClassLoader` en las cadenas | 0, 0, 0 y 0 |
| 18 patrones (calistenia, dominada, flexión, planche, pino, front/back lever, muscle-up, bandera, L-sit, MediaPipe, MoveNet, BlazePose, landmarks, esqueleto, keypoints, isométrico, conteo) en 33 728 nombres de clase y 251 266 cadenas de DEX | solo 4 falsos positivos: `MEDIAPIPE_ERROR` (constante de errores de ML Kit, sin clases de MediaPipe), `getInstanceForSkeleton`, `missingCallSite` («lsit» por azar) y los nombres `SellingPlanCheckout…` («planche» por azar) |
| Los 136 `strings.xml` (predeterminado y 135 idiomas) | 0 coincidencias de calistenia, pose o ejercicio |
| Clases propias (2 241) por sección | 650 consultas de Shopify; 269 de DataBinding; 256 de producto; 224 de bolsa; 150 base; 114 inicio; 72 recompensas; 59 favoritos; 55 búsqueda; 50 base de datos local; 37 acceso; el resto, pedidos, direcciones, colecciones, cuenta, Flits, pago… |

---

## 4. Algoritmos

**No hay detección, seguimiento, calibración, corrección de perspectiva, suavizado, derivadas, detección de repeticiones ni de fases,
manejo de fps o cámara lenta ni controles de calidad de captura.** Lo único cercano a la visión por computador:
- escaneo de códigos de barras y QR con ZXing (`SearchByScanner` → `com.journeyapps.barcodescanner.CaptureActivity`);
- el etiquetador genérico de imágenes de ML Kit, con el modelo descargado por Play Services; quién lo usa, sin confirmar;
- el visor de AR delegado en Google (Scene Viewer), sin código propio.

---

## 5. Exportación y compartir

- **Vídeo con telemetría o con superposición: no existe.** No hay grabación (`MediaRecorder`), codificación (`MediaMuxer`) ni dibujo
  sobre fotogramas. `VideoPlayerActivity` solo reproduce vídeos de producto con ExoPlayer.
- Compartir: enlace al producto («Hey! Check This Out…»), invitar a la app por Play y a amigos por correo (Yotpo).
- **CSV o informes: no.** Los datos locales son la bolsa (`CartItemData` en Room) y preferencias (`MagePrefs`).

---

## 6. Datos e integraciones

- **Tienda:** API de Storefront de Shopify (`com.shopify.buy3`), más el backend del constructor (`shopifymobileapp.cedcommerce.com`,
  `shopmobileapp.cedcommerce.com`, `mobileappbuilder.magenative.com`).
- **Cuentas:** Shopify Customer, Firebase Auth (correo), Facebook, Google; teléfono (biblioteca `auth-api-phone`, uso sin confirmar).
- **Servicios de terceros:** Yotpo (reseñas y lealtad), Judge.me, Ali Reviews, Flits (créditos y favoritos), Rewardify, Kiwi Sizing,
  Zapiet (recogida y entrega local), Wholesale Helper, Loop Club (recomendaciones), Algolia (módulo), ReturnPrime y Shipway,
  Zendesk Chat, Instagram Graph, WhatsApp, traducción de Google, Google Maps (mapa de tiendas, sin confirmar).
- **Google y Firebase:** FCM, Analytics, Crashlytics, mensajes dentro de la app, identificador de publicidad y Realtime Database. El
  proyecto de Firebase es del constructor, no de la tienda: los recursos dicen `new-app---2` y la base de datos en tiempo real apunta
  a `live-shopify-project.firebaseio.com`.
- **Sin** BLE, wearables, Health Connect ni sensores.

---

## 7. Las tres preguntas del usuario

**Para este APK, las tres no aplican** (no hay pose, ni barra, ni vídeo):
1. **Perspectiva:** no hay nada que exigir ni corregir.
2. **Tracking híbrido:** no hay seguimiento de nada.
3. **Vídeo con telemetría:** no existe.

**Para la app real (CaliLens, solo por su web pública [W]; sin confirmar en código):**
1. **Perspectiva: exige la vista y no corrige.** Pide una cámara fija y una vista por habilidad: lateral a la altura de la cadera
   (palancas, planche, L-sit, fondos), lateral a ras de suelo (flexiones), de tres cuartos a la altura del pecho (dominadas),
   lateral a la altura de la barra (muscle-up) y **frontal** (bandera humana), a 2–4 m, sin zoom ni paneo ni cámara lenta [W6]. No
   anuncia homografía, elipse ni gravedad. Dice que un mal ángulo no puede inflar la nota: da una calificación de calidad de captura y
   concede el sello «Certified» solo si se pudieron juzgar todas las reglas [W1]. Es un diseño de «exigir y abstenerse», como el de
   KPKN, no de «corregir».
2. **Tracking híbrido: no aplica.** Todo es esqueleto; no hay barra ni disco ni objetos.
3. **Vídeo con telemetría para redes: no consta.** La web y la ficha de iOS no mencionan exportar ni compartir el vídeo con el
   esqueleto: el vídeo se guarda en la cuenta para el historial y la comparación [W1, W5]. Tiene un modo en vivo con esqueleto en
   tiempo real [W5] y una herramienta interna («CaliLens Publisher») que deja borradores en la cuenta de TikTok de la empresa [W4]:
   es marketing propio, no una función del usuario. **D44 sigue siendo un diferenciador de KPKN.**

---

## 8. Brechas frente a KPKN

Leídos `VIDEO_TECHNIQUE_V1.md` (perfiles DIP, PUSH_UP, PULL_VERTICAL, observaciones y encuadre), `DIFERENCIADORES.md`,
`re/CANDIDATAS_Y_METRICAS.md` (M11–M13), `DECISIONES.md` y `MODELS.md`; además, el catálogo `exercise_catalog_v2.json` y los
resolvedores de perfiles del repositorio (solo lectura). **De este APK no sale ninguna brecha.** La tabla usa lo que declara la web
de la app real **[W]**: son afirmaciones comerciales, no código; sus umbrales son heurísticas de las guías del proveedor y la propia
web no dice que sean las reglas del motor.

**Estado de KPKN, comprobado en el repositorio:**
- el motor cuenta series periódicas en 23 perfiles y marca `ISOMETRIC`, `CORE`, `GRIP` y `NECK` como no soportados («Los ejercicios
  isométricos aún no se analizan»);
- el catálogo v2 trae plancha, *hollow body hold*, *wall sit*, *dragon flag* y dominadas escapulares, pero **ninguna** de las
  habilidades estáticas de calistenia (planche, front lever, back lever, bandera humana, L-sit, pino) **ni el muscle-up**;
- hay clase de vista `LYING` (tumbado) pero **no** una de cuerpo invertido; la elección de lado y la reparación de cruces izquierda
  y derecha suponen un cuerpo erguido;
- sí hay: pose MediaPipe full (BlazePose GHUM, Apache-2.0) con `numPoses = 2`, 12 puntos «world», `FramingEvaluator` con
  `preferredViews`, gravedad del móvil, PTS, filtro de fase cero, Hampel, relleno de huecos, confianza ALTA/MEDIA/BAJA y una lista
  de cosas que NUNCA se emiten (cabeza, cuello, mirada, «válido/nulo»).

| Función o métrica de CaliLens [W] | ¿La tiene KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| **Cronómetro de isométrico con forma válida:** arranca cuando el cuerpo queda quieto; el balanceo es un sostén «atrapado»; solo cuentan los segundos con las reglas cumplidas | **No** (`ISOMETRIC` no soportado; el TUT no sirve: el segmentador espera ciclos) | Máquina de estados `IDLE → QUIETO → SOSTÉN → FIN` sobre la traza v2 filtrada, con reglas por habilidad, histéresis y duración mínima de violación. Los huecos de seguimiento cortan y nunca suman. Salida: segundos válidos, mejor racha, intentos y fotograma de cada ruptura (Anexo B) | MediaPipe tasks-vision 1.1.0 (ya, Apache-2.0); lo demás es Kotlin puro | **Alta** | ~1,5 semanas con el banco |
| **Catálogo de elementos y de variantes:** palanca frontal (5), planche (4), palanca trasera (5), bandera (3), L-sit (5), pino (1), «front lever touch», más flexión, dominada, muscle-up y flexión en pino | **No** (catálogo sin ellas) | Definiciones nuevas con perfil `HOLD_*`/dinámico y una escalera de progresión por habilidad; cada intento queda ligado a su variante. Pasa por el proceso de revisión del catálogo | — | **Alta** | Medio (catálogo + perfil + UI) |
| **Reglas de forma de los estáticos:** cadera sobre la línea hombro–tobillo con tolerancia relativa a su longitud; inclinación del cuerpo; codo bloqueado; pies frente a la línea del hombro; piernas respecto de la horizontal; vertical muñeca–tobillo en el pino | **Parcial:** primitivas (`Angles`, tronco τ, `θ(S,H,A)` de las flexiones), sin reglas por habilidad | Módulo `StaticSkillGeometry` con medidas normalizadas y aspecto corregido (Anexo B). Los umbrales públicos son **semillas**, no verdad: se calibran con el banco y con clips etiquetados por dos jueces. Lo que no tiene puntos (protracción escapular, pelvis, costillas, espalda baja, cabeza) queda «no medible» | — | **Alta** | Medio-alto |
| **Encuadre por habilidad** (altura de cadera, pecho, barra o suelo; frontal para la bandera; 2–4 m; sin zoom) | **Parcial:** `FramingSpec` y `FramingEvaluator` (`WRONG_VIEW`, `TOO_SMALL`, `FEET_OUT`, `TILTED`) | `FramingSpec` nuevos para los perfiles estáticos, con el aviso previo al grabar y «vista incorrecta → no medible» | — | **Alta** | Bajo |
| **Cuerpo invertido y horizontal** (pino, planche, palancas) | **Parcial:** `LYING` sí; invertido no | Clase `INVERTED` (τ ≈ ±90° con la cabeza abajo y la gravedad del móvil como vertical); reparación de cruces sin suponer cuerpo erguido; reintento con el fotograma girado 180° cuando la presencia cae (**a medir**: no se sabe cuánto falla MediaPipe boca abajo) | MediaPipe (ya) | **Alta** | Medio |
| **Puntuación 0–100 con penalizaciones** (cada una con señal, motivo y ejercicio correctivo), nota de calidad de captura y sello «Certified» cuando se juzgaron todas las reglas | **Parcial y distinto:** confianza ALTA/MEDIA/BAJA, observaciones como hechos, sin nota global | **No copiar la nota global** (sin validación frente a jueces). Sí un «sello de medición completa» = confianza ALTA y todas las reglas del perfil evaluables, y penalizaciones como hechos con cifra y fotograma | — | Media | Bajo-medio |
| **Repetición estricta en flexiones:** profundidad (codo ≈ 90°; un fondo de 95–100° es corta), bloqueo (más de ≈ 20° de recto es corta), cadera dentro de ≈ 10 % de la longitud hombro–tobillo, solo cuentan las limpias | **Parcial:** PUSH_UP con θE,mín ≤ 90° (completa), 90–110° (≈) y > 110° (corta), línea θ(S,H,A) < 165° | Añadir el bloqueo (θE ≥ 160°) como criterio de `FULL`, y la separación «cadera cae» frente a «cadera sube» como hechos con cifra; mantener la política de no decir «incorrecto» | — | Media | Bajo |
| **«Worming»** (el pecho sube antes que la cadera, repetido en ≥ 2 repeticiones) | No | Retraso entre la subida del hombro y la de la cadera en el primer 30 % de la concéntrica, como hecho con su cifra | — | Baja | Bajo |
| **Posición de la cabeza** (oreja sobre la línea hombro–cadera) | No, y la política de KPKN lo prohíbe (cabeza, cuello y mirada NUNCA se emiten) | **No hacer** | — | No hacer | — |
| **Dominada estricta:** colgado con codos rectos, inicio escapular, mentón claramente sobre la barra, sin patada ni péndulo | **Sí, en parte:** `chinOverBar` (≈), `fullHang` θE ≥ 155°, balanceo por X de cadera o rodilla | Sin cambios de fondo; documentar que el mentón es ≈ (se estima de la nariz y la boca) | — | Baja | — |
| **Muscle-up estricto:** barra al pecho bajo, pecho y cabeza sobre la barra, ambos codos a la vez, bloqueo arriba, sin balanceo | **No** | Perfil nuevo con fases «tirón → transición → empuje», altura de la muñeca frente al pecho, asimetría de codos («chicken wing») y bloqueo | — | Media | Medio |
| **Fondos** (hombro al nivel del codo, ≈ 90°; bloqueo arriba) | **Sí** (`asin((Y_S − Y_E)/L̂_brazo) ≥ 0`) | Añadir bloqueo arriba. CaliLens **no los lista** entre sus habilidades puntuadas [W1], así que aquí KPKN ya iguala o supera | — | Baja | Bajo |
| **Progresión por puertas:** subir de variante tras 3 series × 8 s limpias (isométricos) o tras 3 × 8 dominadas estrictas, 15–20 fondos limpios…; trabajar los sostenidos al 60–70 % del mejor tiempo limpio [W9, W11, W18] | No | «Sugerencia de progresión» en Programas a partir de los segundos válidos y las repeticiones estrictas del análisis; prescripción de series de sostén al 60–70 % del mejor sostén válido. Los umbrales son estándares de un blog: editables por el usuario o el entrenador | — | Media | Medio |
| **Análisis en vivo** (Pro): esqueleto en tiempo real y análisis al terminar | **No** (el contador en vivo con voz es fase 2) | Reutilizar la pose en modo LIVE_STREAM con el cronómetro de sostén, los avisos de encuadre y la voz de la sesión; sin nota global | MediaPipe (ya) | Media | Medio-alto |
| **Colección y juego:** CaliDex (tarjetas por variante), XP y rangos (de Rookie a Legend), insignias por hitos, guías ilustradas, cuestionario de nivel | No (hay un motor de fatiga y ajustes, sin esto) | Tarjetas de habilidad **locales** ligadas a las variantes, rachas y marcas personales. Sin servidor | — | Baja | Medio |
| **Clasificaciones y batallas 1 contra 1** (ambos sostenidos puntuados con las mismas reglas, lado a lado) | No | Exigen backend, cuentas y antifraude; sin una medición validada se fomentaría el engaño. Aplazar | — | Baja | Alto |
| **Historial con vídeo y comparación de intentos** | **Sí** (D10: deslizador sincronizado y trayectorias superpuestas) | — | — | — | — |
| **Exportar el vídeo con esqueleto** | No consta en CaliLens; en KPKN, D44 | Sigue D44 (Media3 Transformer, 9:16) | Media3 1.5.1 (ya) | Alta (ya planificada) | ~1 semana |

**Lo que KPKN hace mejor, o puede hacer mejor, que CaliLens (por lo que declara [W]):**
- **Privacidad:** KPKN no sube el vídeo ni los puntos del cuerpo. CaliLens analiza en el teléfono, pero **sube el vídeo, la miniatura y las
  coordenadas de las articulaciones** a su nube para el historial [W3].
- **Tiempo y fiabilidad:** PTS reales, filtro de fase cero, relleno acotado de huecos y confianza calibrada con un banco (el objetivo
  de la ola es P(conteo exacto | ALTA) ≥ 98 %). CaliLens no publica nada de su método y sus propios términos admiten que las
  notas pueden fallar con poca luz, cuerpo parcial o ángulos raros [W4].
- **Honestidad:** KPKN da hechos con cifra y fotograma, con «≈» y abstención; no una nota global sin validar.
- **Auditabilidad:** cada número ligado a un fotograma y corrección manual de reps. En los isométricos podrá ofrecerse la línea de
  tiempo segundo a segundo con la regla que se rompió.
- **Alcance:** barra y disco con VBT, 23 perfiles de fuerza, comparación de dos vídeos y tendencias; CaliLens no sale de la
  calistenia.
- **Fondos:** CaliLens no los lista entre sus habilidades puntuadas; KPKN ya los cuenta.

---

## 9. Índice de evidencias

No hay árbol descompilado: **no se usó JADX ni Ghidra**. Las pruebas salen del manifiesto y los recursos decodificados con apktool,
de las tablas de cadenas, tipos y clases de los DEX (script propio) y de la lectura de las firmas. Todo se reproduce con
`C:\kw\re\_scripts\calilens\` (`01_extract_xapk.py`, `02_strings_search.py`, `03_apk_inventory.py`, `04_dex_strings.py`,
`05_dex_packages.py`, `06_dex_urls.py`, `07_lib_versions.py`, `08_strings_full.py`, `09_make_evidence.py`); la extracción
(`C:\kw\re\calilens\xapk\`) se conservó y lo voluminoso (apktool y JSON de DEX, ≈ 60 MB) se borró.

| Tema | Evidencia |
|---|---|
| Identidad, hashes y manifiesto del XAPK | `C:/kw/re/evidencias/calilens-re-20261020/00_identidad.json` |
| Manifiesto decodificado (permisos, 64 actividades de las que 43 son propias, 15 servicios, 15 receptores, 7 proveedores, metadatos) | `01_AndroidManifest_decoded.xml` |
| Inventario del APK (extensiones, 0 `lib/`, 0 modelos, `assets/` completo, `res/raw`) | `02_apk_inventario.txt` |
| Paquetes del DEX y secciones propias (2 241 clases) | `03_dex_paquetes.txt` |
| Dominios de las cadenas del DEX (64) | `04_dex_dominios.txt` |
| Prueba negativa (18 patrones, 136 `strings.xml`, clases de pose, CameraX, MediaMuxer, carga dinámica) | `05_busqueda_negativa.json` |
| Cadenas propias de la app (sin credenciales) | `06_cadenas_app_sin_credenciales.json` |
| Firma (titular, huellas, sello de Play) | `07_apksigner_certs.txt` |
| Fuentes web consultadas | `08_fuentes_web.txt` |
| Clases clave | `co.shopney.calilens.MyApplication`; `basesection.activities.Splash` y `NewBaseActivity`; `homesection.activities.HomePage`; `productsection.activities.ProductView`, `WriteAReview`, `VideoPlayerActivity` y `ArImagesAdapter`; `searchsection.activities.AutoSearch` y `SearchByScanner`; `cartsection.activities.CartList`; `checkoutsection.activities.*`; `ordersection.activities.*`; `loginsection.activity.*`; `yotporewards.*`; `customviews.MageNativeTextView`; `sharedprefsection.MagePrefs`; `dbconnection.entities.CartItemData`; `trialsection.activities.TrialExpired`; `livepreviewsection.LivePreview`; `com.shopify.buy3.Storefront` |
| Recursos | `res/values/strings.xml` (`app_name`, `shop`, `project_id`, `Livepreview`, `downloadingconfig`), `assets/country_list.json`, `res/raw/joda_*` |

**Sin confirmar:**
- quién usa el etiquetador de imágenes de ML Kit y qué modelo descarga;
- la tienda real («Cali Lenses & Lashes») y su dominio de Shopify;
- las versiones del SDK de Shopify, ExoPlayer, Glide, Retrofit, Zendesk y Facebook;
- el uso real de la autenticación por teléfono y de Google Maps;
- que el nombre «Cali lens» de APKPure no esconda un segundo paquete de calistenia (el XAPK solo trae el base y un split de densidad; no hay módulos dinámicos).

---

## 10. Fuentes web públicas (solo lectura, 2026-10-21)

Todas son afirmaciones del proveedor o de sus guías; **[W]** no equivale a código.

- [W1] `https://calilens.app/` (portada, preguntas frecuentes, habilidades, puntuación, «Certified», CaliDex, batallas, precios).
- [W2] `https://calilens.app/llms.txt` (índice, 31 guías, enlaces de tiendas) y `https://calilens.app/sitemap.xml`.
- [W3] `https://calilens.app/privacy` (pose en el dispositivo, vídeo y puntos del cuerpo subidos a la nube, Sentry, RevenueCat, inicio de
  sesión con Google y Apple, herramienta interna con TikTok; titular: Xavier Facqueur, empresario individual, XF Studio, París).
- [W4] `https://calilens.app/terms` (no nombra el modelo de pose; las notas pueden fallar con poca luz, cuerpo parcial o ángulos
  raros; «CaliLens Publisher» deja borradores en la cuenta de TikTok de la empresa).
- [W5] `https://apps.apple.com/app/id6760661846` (ficha de iOS: 1.0.0 del 13-jul a 1.0.4; modo en vivo con esqueleto; XP y rangos;
  clasificaciones; 110,4 MB; compras integradas). La ficha de Google Play no se pudo leer desde aquí.
- [W6] `https://calilens.app/blog/how-to-film-calisthenics-form-check`.
- [W7] `…/blog/are-my-push-ups-clean` · [W8] `…/blog/how-deep-should-you-dip` · [W9] `…/blog/front-lever-hips-sagging` ·
  [W10] `…/blog/tuck-planche-hips-too-low` · [W11] `…/blog/human-flag-progression` · [W12] `…/blog/why-is-the-l-sit-so-hard` ·
  [W13] `…/blog/how-to-balance-a-handstand` · [W14] `…/blog/back-lever-progression` ·
  [W15] `…/blog/dead-hang-active-vs-passive` · [W16] `…/blog/cant-do-a-pull-up` · [W17] `…/blog/muscle-up-transition` ·
  [W18] `…/blog/when-to-progress-calisthenics` · [W19] `…/blog/pike-push-up-to-handstand-push-up` · [W20] `…/blog/hollow-body-hold` ·
  [W21] `…/blog/chest-to-wall-handstand`.

**Cautelas:** la web describe precios contradictorios (ilimitado y gratis frente a 3 análisis cada 7 días en la versión gratuita;
la ficha de iOS lista dos precios sin periodo); los porcentajes («34 % con cadera baja», «mediana de 8,3 s») no traen tamaño de
muestra; y los umbrales de las guías son **heurísticas del blog, no reglas confirmadas del motor**.

---

## Anexo A · Lo que la web pública de CaliLens declara sobre su catálogo [W]

Catálogo (11 habilidades y 32 variantes según la portada [W1]). Las variantes estáticas son tuck, tuck avanzado, una pierna, straddle y
completa (según la habilidad); las dinámicas llevan «conteo estricto» e insignias por hitos de repeticiones. La app puntúa sobre
100, indica el tiempo de sostén o el número de repeticiones, una nota de calidad de captura y la falta principal (con la señal, el motivo
y un ejercicio); solo la primera exploración trae el desglose completo en el plan gratuito.

| Habilidad (variantes) | Vista que pide | Criterios de forma declarados | Cronometraje y repetición válida |
|---|---|---|---|
| **Palanca frontal** (tuck, tuck avanzado, una pierna, straddle, completa; «front lever touch» aparte) [W1, W9] | Lateral, cámara a la altura de la cadera, apaisada, 3–4 m | **Portada:** el codo más doblado visto de lado (brazos bloqueados = una recta); cadera bajo la línea hombro–tobillo; pies sobre la línea del hombro («falsa palanca»); movimiento de la línea durante el sostén; rodillas bloqueadas; cuerpo horizontal. **Guía:** caída de la cadera como fracción de la longitud hombro–tobillo: ≈ 5 % ya es caída, ≈ 20 % es grave (completa), ≈ 10 % una pierna, ≈ 12 % straddle; tuck y tuck avanzado no se juzgan por la línea; se ignoran los primeros y últimos instantes y las caídas instantáneas | Arranca al quedar el cuerpo quieto; el balanceo es un sostén «atrapado»; cuentan los segundos con la línea en su sitio; subir de nivel con 3 series × 8 s limpias (estándar del blog) |
| **Palanca trasera** (5) [W14] | Lateral, cadera, 3–4 m | Cuerpo boca abajo y horizontal, espalda sin arco, cadera sobre la línea (si cae es fallo; si sube, acorta la palanca y no cuenta como forma completa), codos bloqueados, rodillas rectas en completa y straddle, hombros adelante y abajo; en el tuck avanzado las espinillas no deben caer al suelo | Cuenta solo una vez quieto, no desde la entrada; un balanceo = sostén «atrapado»; solo los segundos con codos, espalda, cadera y línea cumplidos; la guía dice que la app comprueba esos mismos puntos |
| **Planche** (tuck, tuck avanzado, straddle, completa; las guías solo cubren las dos primeras) [W10] | Lateral, cadera, 2–3 m | Caderas a la altura de los hombros; una espalda inclinada ≥ ≈ 12° hacia abajo durante «una parte real de la serie» cuenta como cadera baja; hombros bien por delante de las manos (inclinación); protracción de escápulas (**no observable con 33 puntos**); codos bloqueados; pies a una altura estable. De sus escaneos: 34 % con caderas bajas, 26 % con poca protracción y cerca de la mitad con los pies subiendo y bajando | 3 × 8 s con cadera nivelada, codos bloqueados y pies firmes; mediana de sus escaneos 8,3 s |
| **Bandera humana** (tuck, una pierna, completa) [W11] | **Frontal**, cámara a la altura de las manos, 3–4 m, apaisada | Tobillos a la altura del hombro; pies unos pocos grados por debajo = bandera «baja»; cadera sin caer ni subir; brazo inferior recto (cualquier flexión visible cuenta como brazo doblado); manos en una vertical | Empieza al quedar quieto a la altura entrenada; para al caer la cadera, doblarse el codo o derivar o girar el cuerpo (la guía no lo da como regla del motor) |
| **L-sit** (tuck, tuck avanzado, una pierna, completa, V-sit) [W12] | Lateral, cadera, 2–3 m | Codos rectos; hombros deprimidos (36 % los encoge); tronco cerca de la vertical (19 % inclinado); piernas rectas y a la altura de la cadera, ≥ ≈ 20° bajo la horizontal no cuenta y por encima de la horizontal mucho rato se convierte en V; talones a la altura de la cadera; sin temblor (31 % inestables) | Cuentan solo los segundos en que todo se cumple; mediana de sus escaneos 6,7 s; barra de 10 s |
| **Pino libre** [W13] | Lateral, cadera, 2–3 m | Muñecas, hombros, caderas y tobillos en una vertical; el arco («banana») es fallo; codos rectos; piernas juntas; correcciones solo con los dedos | Cuenta desde que los pies llegan sobre las manos (no durante la patada) y para en la primera corrección grande (cadera doblada, codos, mano que camina, piernas que se abren); mediana de sus escaneos 7,5 s con 46 % de sostenidos inestables; objetivo de 10 s |
| **Flexión en pino** [W1, W6, W19] | Lateral estricta, a ras de suelo, cuerpo entero (evitar el tres cuartos, que hace parecer corta una repetición profunda) | **Guía:** fondo = la cabeza toca el suelo (o el déficit fijado) en cada repetición; bloqueo de codos arriba; sin patada (rodillas quietas); caderas sobre los hombros, piernas juntas, sin arco ni balanceo (esto último, en libres, no en pared) | Conteo estricto e insignias; la guía no dice cómo lo cuenta la app |
| **Flexión** (estándar y de rodillas) [W7] | Lateral a ras de suelo, cuerpo entero de la cabeza a los talones | Cadera: caída o subida de más de ≈ una décima de la longitud hombro–tobillo; profundidad: codo ≈ 90° o menos, un fondo de 95–100° es corto; bloqueo: más de ≈ 20° de recto arriba es corto; cabeza: oreja sobre la línea hombro–cadera, solo arriba; «worming»: el pecho sube antes que la cadera, en ≥ 2 repeticiones | Solo cuentan las limpias (si la 14 cede, la serie es 13); estricta = profundidad, bloqueo y cadera en línea; de sus series: mediana de 8 (6 estrictas), 73 % con al menos un fallo, 52 % de cabeza, ≈ 25 % cortas y 18 % sin bloqueo |
| **Dominada** [W16, W1] | Tres cuartos, a la altura del pecho, ambos pies en cuadro | Colgado muerto con codos rectos antes de cada repetición; inicio escapular; mentón claramente sobre la barra (no inclinando la cabeza); piernas rectas y juntas, sin patada ni péndulo; bajada controlada | «Conteo estricto» (sin detalle) e insignias por hitos |
| **Muscle-up** [W17] | Lateral, a la altura de la barra | Colgado quieto con piernas juntas; barra al pecho bajo o al estómago; pecho y cabeza sobre la barra; codos y muñecas pasan a la vez; bloqueo arriba; sin balanceo | La guía no describe cómo lo cuenta la app |
| **Fondos** [W8] | Lateral, cadera (guía de cámara) | Hombro al nivel del codo, codo ≈ 90°; bloqueo con hombro, codo y muñeca alineados; fallos: media repetición, hombros hacia delante para ganar fondo, encogimiento, patada. **No figura entre las habilidades puntuadas** | — |
| **Colgado** (activo o pasivo) [W15] | — | Activo = brazos rectos con los hombros bajados y lejos de las orejas | Arranca al quedar quieto; con vídeo, cuentan los segundos con codos rectos, piernas juntas y quietas y costillas abajo; no es una habilidad puntuada |
| **Hollow body hold** [W20] (no puntuada por la app; **está en el catálogo de KPKN**) | Lateral, cámara a ras de suelo, 2–3 m, apaisada | Espalda baja pegada al suelo (sin hueco), pelvis en retroversión y costillas abajo, piernas lo más bajas que permita la espalda plana (≥ 45° es demasiado alto), brazos sobre la cabeza con los bíceps junto a las orejas, escápulas elevadas | Cuentan solo los segundos con la espalda baja pegada; la serie acaba cuando se despega; 60 s limpios en una serie para pasar al paso siguiente |
| **Pino contra la pared** [W21] (la web dice que puntúa el pino **libre**) | Lateral, cadera, 2–3 m | Línea vertical de muñecas a dedos de los pies, sin arco ni caída de cadera, hombros abiertos, codos bloqueados, solo los dedos de los pies tocan la pared | Cuentan los segundos con la línea recta; termina al volver el arco o doblarse los codos; «listo» con 3 × 30 s |

**Cómo se produce la nota (lo único que afirma la web sobre su motor) [W1, W4, W5]:** la pose se calcula en el teléfono; cada fotograma
se compara con un «libro de reglas» por habilidad; cada falta resta puntos y trae señal, motivo y ejercicio; el sello «Certified» solo
se concede si se juzgaron todas las reglas (con una mala cámara no se infla la nota) y solo las exploraciones certificadas puntúan en
la colección; hay una nota de calidad de captura por exploración. Ejemplos de portada: palanca frontal completa, 9,4 s, 97,2 con −2,8
por pies sobre la línea del hombro; otra, 15,5 s, 91,5 con el límite en el «ángulo de hombro» cerrado, −3,5. **Cómo detecta cada falta, qué
modelo de pose usa y cuáles son sus umbrales exactos: sin confirmar** (ni la política de privacidad ni los términos lo dicen).

**Social y progreso [W1, W5]:** batallas 1 contra 1 sobre la misma habilidad (ambos sostenidos puntuados con las mismas reglas y
mostrados lado a lado); CaliDex (tarjetas ilustradas con rareza, una por variante); XP por variante y rangos (de Rookie a Legend);
insignias por hitos de repeticiones; clasificaciones general y por figura (1.0.4); cuestionario de nivel; guías ilustradas de las 12
figuras; historial con vídeo y comparación de intentos; modo en vivo con esqueleto (Pro). Planes: gratuito con 3 análisis cada 7 días
y desglose completo solo en el primero; Pro con análisis ilimitados, desglose siempre, historial completo y análisis en vivo.

---

## Anexo B · Propuesta de motor de isométricos para KPKN (sala limpia; no sale del APK)

**Objetivo:** cronometrar un sostén **solo mientras la forma es válida**, con auditoría por fotograma y sin una nota global.

**1. Medidas por fotograma** (sobre la traza v2 ya filtrada con fase cero a 5 Hz; coordenadas con el aspecto corregido y giradas por
la gravedad del móvil; `L` = distancia hombro–tobillo, `S` hombros medios, `H` caderas medias, `A` tobillos medios o el lado cercano si el
otro está tapado):

| Medida | Definición | Se usa en |
|---|---|---|
| Caída de la cadera | distancia con signo de `H` a la recta `S–A`, dividida por `L` (signo por la gravedad) | palancas, planche completo, flexiones |
| Inclinación del cuerpo | ángulo de `S→A` con la horizontal (o la vertical en el pino) | palancas, planche, bandera, pino |
| Bloqueo de codo | `θ(S,E,W)` del brazo relevante (el inferior en la bandera) ≥ 160° | todas |
| Pies frente al hombro | `(Y_A − Y_S)/L` (positivo = pies más bajos) | bandera, palanca frontal («falsa palanca») |
| Nivel de las piernas | ángulo de `H→A` con la horizontal y `θ(H,K,A)` | L-sit |
| Tronco vertical | ángulo de `H→S` con la vertical | L-sit |
| Inclinación hacia delante y cadera frente al hombro | `(X_S − X_W)·f / largo del brazo` y `(Y_H − Y_S)/L` | planche tuck |
| Vertical del pino | máximo de los desvíos horizontales (X) de `S`, `H` y `A` respecto de la muñeca `W`, dividido por `L`, más la desviación firmada de `H` (arco) | pino |
| Quietud | percentil 90 de la rapidez de `S` y `H` en 0,5 s, normalizada por `L` | todas |

**2. Máquina de estados:** `IDLE` (vista y cuerpo completos, visibilidad ≥ 0,5 en los puntos exigidos) → `QUIETO` (quietud bajo el umbral
durante T_q ≈ 0,5–1,0 s) → `SOSTÉN` (todas las reglas dentro de tolerancia; el reloj corre) → `FIN` (una regla fuera de tolerancia durante T_v ≈
0,3–0,5 s, o un hueco de seguimiento > 0,25 s). Con histéresis de entrada y salida para no parpadear. Los segundos con seguimiento
perdido **no se suman**. Salida: segundos válidos, mejor racha continua, número de intentos, tiempo total del intento, tiempo fuera de
tolerancia **por regla** y el fotograma de cada ruptura. Se muestra como línea de tiempo segundo a segundo, con corrección manual de
inicio y fin.

**3. Semillas de umbral (de las guías públicas; a calibrar, no a creer):** cadera dentro del 5 %, 10 % y 12 % de `L` (palanca completa, una
pierna, straddle) y 10 % en flexiones; piernas del L-sit a ≤ 20° bajo la horizontal; espalda del planche tuck a < 12° de inclinación
hacia abajo; codos a ≥ 160°; pies de la bandera a ≤ unos pocos grados bajo la línea del hombro; sostenidos de práctica al 60–70 % del
mejor tiempo limpio; «subir de nivel» con 3 × 8 s limpios. **Ningún valor está validado:** se calibran con el banco y con clips
etiquetados por dos jueces, y se mide el error en segundos válidos (objetivo de partida: mediana ≤ 0,5 s) antes de mostrar nada.

**4. Pose en posturas difíciles (riesgos y pruebas):**
- *Cuerpo horizontal* (palancas, planche): ya existe `LYING`; hay que comprobar que la reparación de cruces izquierda y derecha y la
  elección de lado no suponen un cuerpo erguido.
- *Invertido* (pino): clase `INVERTED`, la gravedad del móvil como vertical y reintento con el fotograma girado 180° cuando la presencia
  cae. **Se desconoce el fallo real de MediaPipe boca abajo; hay que medirlo.** El banco de KPKN ya trae 44 clips de flexión en pino
  (UCF101, etiquetados «ejercicio no soportado en v1») y 5 de plancha (Kaggle) en `C:\kw\vt-bench\labels\`: sirven para medir la tasa
  de fotogramas con pose y la visibilidad boca abajo antes de grabar nada nuevo; los de palancas, planche y L-sit hay que grabarlos
  con el teléfono.
- *Oclusiones:* de lado, el brazo y la pierna lejanos quedan tapados; usar solo el lado cercano y exigir visibilidad; las coordenadas
  «world» no sirven aquí como profundidad.
- *Aparatos* (barra, paralelas, pared, poste de la bandera) pueden confundir el detector; medir la tasa de fotogramas con pose por
  habilidad antes de prometerla.
- *Lo que 33 puntos no ven:* las guías de CaliLens piden protracción escapular (planche), retroversión pélvica y costillas abajo
  (hollow body, palancas), contacto de la espalda baja con el suelo (hollow body) y posición de la cabeza (flexiones, pino). **No hay
  puntos de columna, escápula ni costillas**, así que son «no medible» (la política de KPKN ya lo recoge). Sí se pueden aproximar con
  proxies explícitos y marcados «≈»: ángulo de las piernas, brazos junto a las orejas (hollow), elevación del hombro respecto del
  codo y de la oreja (encogimiento en L-sit y colgado) y la inclinación del tronco.
- Todo lo que falte a la vista exigida o a la visibilidad devuelve **«no medible»**, no un valor rellenado.

**5. Qué no hacer:** una nota 0–100 sin validar frente a jueces; el sello «Certified» como marca de exactitud; la posición de la cabeza (la
política de KPKN lo prohíbe); y confundir segundos de intento con segundos válidos.
