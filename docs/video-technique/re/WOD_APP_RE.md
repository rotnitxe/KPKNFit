# WOD App 18.0.6 · ingeniería inversa estática (20-oct-2026)

Informe del encargo `briefs/RE_COMUN.md`. Análisis solo estático del archivo `C:\Users\valen\Downloads\WOD+App_18.0.6_APKPure.apk`. No se ejecutó ni se instaló nada del APK, no se usó adb, Gradle ni emuladores, y no se descargó ningún archivo (solo se leyeron páginas web públicas, citadas al final). Las pruebas pequeñas están en `C:\Users\valen\Documents\KPKNFit\artifacts\wodapp-re-20261020\` (576 KB) y los scripts en `C:\kw\re\_scripts\wodapp\`.

## 0. Dictamen en diez líneas

1. **Esta WOD App no es lo que se esperaba.** Es el cliente móvil de una plataforma SaaS holandesa de gestión de boxes de CrossFit y estudios de yoga (plataforma Pay n Plan; el paquete es `bothoffmedia` y el certificado, de TelSoft, Putten, Países Bajos). Gestiona clases, créditos, pagos, la «pizarra» del WOD con ranking, el progreso corporal y el acceso por QR.
2. **No tiene nada de análisis por vídeo.** Ni pose, ni conteo de repeticiones, ni trayectoria de barra o disco, ni velocidad, ni 1RM, ni cálculo de carga, ni temporizadores (AMRAP, EMOM, Tabata), ni sensores. La única cámara es el lector de códigos QR del gestor, y el propio APK la delega al navegador del sistema: el botón «Abrir escáner QR» lanza `app.wodapp.nl?page=qr-checkin-scanner…` fuera de la app.
3. **Arquitectura.** Es una cáscara Cordova (WebView) con una interfaz web Vue 3 + Framework7. No hay bibliotecas nativas, ni ABIs, ni modelos de ML. El único permiso es `INTERNET`.
4. **Toda la lógica de negocio vive en el servidor** `https://ws.paynplan.nl` (12 servicios y 219 métodos visibles desde el cliente). El cliente solo pinta; el cálculo de rankings, posiciones, récords y diferencias de medidas no es recuperable y queda «sin confirmar».
5. **Lo único aprovechable para KPKN es de producto, no de algoritmo:** el modelo de puntuación de un WOD (5 tipos de marca, niveles RX / Fundamental / Scaled, categorías, ranking del día y global, benchmark con récord e historial) y la tarjeta de medidas corporales (13 perímetros y 13 pliegues, con diferencia frente a la primera y a la anterior medición).
6. **Hay cadenas de interfaz huérfanas (413 sin uso)** que delatan módulos retirados o fuera del cliente: competición multi-WOD con «ranking final», planes de entrenamiento con «tempo» y «Start timer», y diarios de nutrición. No hay pantallas ni código para ellos en la 18.0.6.
7. **Las tres preguntas del usuario** (perspectiva, seguimiento híbrido, exportar vídeo con telemetría): la respuesta a las tres es «no lo tiene» (§7).
8. **Prioridad para la ola VT: nula.** Nada de esto refuerza la pose ni el disco. KPKN ya está por delante en todo lo que importa (§8.2).
9. **Recomendación:** si el objetivo eran los temporizadores y las métricas de atletas avanzados, analizar otra app. En una búsqueda pública aparecen como candidatas SmartWOD Timer y WOD 365 Timer (Android) y, solo en iOS según su ficha, «WOD App: Timer & AI Workout» (Stellar Apps SL). No se descargó nada de ellas (Anexo B).
10. **Lo que no se pudo confirmar** está marcado «sin confirmar» y listado en el Anexo C.

---

## 1. Identidad

### 1.1 Archivo, versión y firma

| Dato | Valor |
|---|---|
| Archivo | `WOD+App_18.0.6_APKPure.apk`, 11.013.647 B |
| SHA-256 del archivo | `6f3fd81a21b0db32a77d5a6a6a74ac9ee79d26b2f84153ed5efaa1e6e336a6f5` |
| APK internos | **Ninguno.** Es un APK único (no XAPK ni divididos): el SHA-256 del «APK interno» es el mismo del archivo. 507 entradas, 23.317.910 B descomprimidos |
| Paquete | `com.bothoffmedia.wodapp` (nombre «WOD App») |
| Versión | `versionName` 18.0.6, `versionCode` 13009 (coinciden manifiesto y `BuildConfig`; `release`, `DEBUG = false`) |
| SDK | `minSdk` 31 (Android 12), `targetSdk` 34, `compileSdk` 34; solo vertical |
| Compilación | Android Gradle Plugin 7.2.1 (`META-INF/com/android/build/gradle/app-metadata.properties`) |
| Descripción declarada | «Gym and Yoga Studio Management Software» (`config.xml`) |
| Discrepancia menor | `config.xml` declara la versión de widget 18.0.8, distinta del 18.0.6 compilado |
| Firma | Solo esquema v2 (sin v1, v3 ni v4), 1 firmante, RSA-2048. Certificado autofirmado, sujeto «CN=Wouter Bothoff, OU=TelSoft, O=TelSoft, L=Putten, ST=Gelderland, C=NL», válido del 2012-05-18 al 2039-10-04. SHA-256 del certificado `3bae76a38a2e3bf84d480440897c5692c8c715fb574e504cec84e0c4e91bc167` (`apksigner verify`, SDK build-tools 36.1.0) |

Hashes de las piezas clave (todos en `internal_hashes.txt`): `classes.dex` (4.270.528 B) `e85a7cf86a5f9c4a8393af32d28f767064f6e73b52e976c3a97bb7c2f0bb5638`; `AndroidManifest.xml` `80bc0246…a122`; `resources.arsc` `f95f6cfb…68b8`; `assets/www/js/app.3d1010fe.js` (1.168.525 B) `a161cf71…58d4`; `assets/www/js/chunk-vendors.b8b07d6b.js` (2.425.317 B) `f9cb0212…4116`.

### 1.2 Arquitectura

- **Híbrida Cordova (WebView), no Flutter, React Native ni Unity.** Plataforma `cordova-android` 11.0.0 con `cordova-js` 6.1.0.
- **Parte nativa mínima:** 1.466 clases en un solo `classes.dex`, casi todas de Kotlin stdlib, AndroidX y Cordova. La única clase propia es `com.bothoffmedia.wodapp.MainActivity` (extiende `CordovaActivity`; en `onCreate` respeta el extra `cdvStartInBackground` y llama a `loadUrl(launchUrl)`), más `BuildConfig` y `R`.
- **Interfaz web empaquetada con webpack:** Vue 3.2.6 + Framework7 6.3.2 (tema Material), `jQuery` para las llamadas AJAX. Entrada `assets/www/index.html`, que carga `cordova.js`, `chunk-vendors.b8b07d6b.js` y `app.3d1010fe.js`.
- **Código recuperable por source map.** `app.3d1010fe.js.map` trae `sourcesContent`: 498 entradas, 224 fuentes distintas, 222 de ellas propias en `src/` (197 `.vue` y 25 `.js`): 81 páginas, 75 popups, 41 componentes y 25 módulos de apoyo. El mapa del bundle de terceros (`chunk-vendors`) trae 1.043 fuentes.
- **Plataforma multimarca (white label).** `src/js/config.js` define cuatro marcas (Pay n Plan, Personal Trainer App, WOD App y Recurme); este APK fija `currentApp = 'wodapp'`. El servicio, los textos y los módulos cambian por servidor y por empresa («company»).
- **Vistas según rol.** Cliente (atleta) y gestor (entrenador / administrador), decididas por el servidor (`gym.whichPage`) y por niveles de acceso por empresa, componente y función (`hasAccess`).

### 1.3 ABIs, bibliotecas nativas y modelos de ML

| Aspecto | Resultado | Evidencia |
|---|---|---|
| ABIs y `.so` | **Ninguno.** Sin carpeta `lib/`; `extractNativeLibs=false` | listado del zip |
| Modelos de ML | **Ninguno.** Sin `.tflite`, `.onnx`, `.task`, `.pb`, `.bin` | el único contenido de `assets/` es `www/` (63 archivos, 17,8 MB, casi todo JS, mapas y fuentes) |
| WebAssembly / ML en JS | **Ninguno.** 0 coincidencias de `WebAssembly`, `tensorflow`, `tfjs`, `onnx`, `mediapipe`, `posenet`, `movenet` en ambos bundles | búsqueda en `app.js` y `chunk-vendors.js` |
| Grabación / movimiento / radio | **Ninguno.** 0 coincidencias de `MediaRecorder`, `captureStream`, `devicemotion`, `deviceorientation`, `Accelerometer`, `bluetooth`, `geolocation` en `app.js`. `getUserMedia`, `BarcodeDetector` y `ZXing` aparecen solo dentro de html5-qrcode | ídem |

### 1.4 Bibliotecas de terceros (versión y licencia)

| Biblioteca | Versión | Licencia | Uso en la app |
|---|---|---|---|
| Apache Cordova Android (+ cordova-js) | 11.0.0 (+ 6.1.0) | Apache-2.0 | contenedor WebView |
| cordova-plugin-device | 3.0.0 | Apache-2.0 | datos del dispositivo |
| cordova-plugin-inappbrowser | 6.0.0 | Apache-2.0 | abrir enlaces externos |
| cordova-plugin-keyboard | 1.2.0 | Apache-2.0 | teclado (encoge la vista) |
| cordova-custom-config | 5.1.1 | sin confirmar (probable MIT) | solo en compilación |
| AndroidX: appcompat 1.4.2, core 1.7.0, webkit 1.4.0, activity 1.2.4, fragment 1.3.6, lifecycle 2.4.0, core-splashscreen 1.0.0-rc01, emoji2 1.0.0, startup 1.0.0 | las indicadas | Apache-2.0 | base nativa |
| Kotlin stdlib | sin confirmar | Apache-2.0 | base nativa |
| Vue | 3.2.6 | MIT | interfaz |
| Framework7 (lite bundle) y framework7-vue | 6.3.2 | MIT | componentes de interfaz |
| Swiper / Dom7 / skeleton-elements | 6.8.4 / 3.0.0 / sin confirmar | MIT | carruseles y utilidades |
| jQuery | 3.6.0 | MIT | AJAX y DOM |
| Moment.js (con locales) | 2.29.1 | MIT | fechas |
| c3 y d3 | 0.6.14 y 5.16.0 | MIT y BSD-3-Clause / ISC | gráficos del panel de gestión |
| flatpickr, vue-flatpickr-component, shortcut-buttons-flatpickr | sin confirmar | MIT | selector de fecha y hora |
| **html5-qrcode** (con ZXing-js) | sin confirmar (probablemente ≥ 2.2, por el módulo `native-bar-code-detector`) | Apache-2.0 | **lectura de QR con la cámara** |
| **EasyQRCodeJS** | 4.4.13 | MIT | **generación de QR** |
| Bowser, path-to-regexp, lodash.debounce, copy-to-clipboard, toggle-selection, mitt, ssr-window, path-browserify | sin confirmar | MIT | utilidades |

Lo abierto es utilizable en KPKN bajo su licencia. Las únicas piezas con interés potencial son html5-qrcode / ZXing (leer QR) y EasyQRCodeJS (generar QR), solo si algún día KPKN quiere acceso por QR (§8).

### 1.5 Permisos y sensores

| Elemento | Declarado | Uso real |
|---|---|---|
| Permisos | **solo `android.permission.INTERNET`** | llamadas al servidor |
| Cámara | no hay `CAMERA`, ni `uses-feature` | html5-qrcode pide `getUserMedia` en la página del escáner de QR. Esa página es «externa» (`?page=qr-checkin-scanner&external=1`): el botón «Abrir escáner QR» (`manage-qr-access/tab-events.vue`, `toQRScanner`) la abre con `cordova.InAppBrowser.open(url, '_system')`, es decir, **en el navegador del sistema**, pensada para la tableta de la entrada. El APK nunca toca la cámara (y sin permiso `CAMERA` su WebView no podría) |
| Micrófono, ubicación, Bluetooth, actividad física, Health Connect, notificaciones, servicios en primer plano | ninguno | no hay |
| Sensores de movimiento | ninguno | no hay |
| Componentes | una sola `Activity` exportada (`MainActivity`, lanzador) y el `InitializationProvider` de AndroidX | sin deep links ni receptores |

### 1.6 Servidor, protocolo y datos locales

- **Servidor único** `https://ws.paynplan.nl` (en `config.js` quedan comentadas las URL de desarrollo y de la otra marca). Todas las llamadas son un POST a esa raíz (jQuery codifica los parámetros anidados en un objeto `data`) y la respuesta es JSON. Cada petición lleva `service` y `method`, más `app`, `language`, `version` (14.0 del protocolo), `clientUserAgent`, `token`, `idc` (empresa), `id_appuser_li` (usuario) y `companySyncVersions`.
- **Superficie de API visible:** 12 servicios y 219 métodos (`api_surface.txt`): `payment` 51, `gym` 45, `user` 30, `invoiceProduct` 25, `agenda` 19, `WOD` 17, `eventWithScanAccess` 11, `apiConnect` 9, `pushMessage` 5, `booking` 4, `settings` 2, `dashboard` 1.
- **Sincronización de permisos y estilos:** el cliente envía las versiones que tiene de los niveles de acceso y de los estilos de la empresa; el servidor devuelve órdenes de sincronización (`syncCommands`) y puede ordenar parar la app (`stop_app`).
- **Reautenticación para acciones sensibles** (`extraLoginExpired`, `loginExtra`) con una ventana en minutos que fija el servidor.
- **Datos locales:** solo `localStorage` del WebView: `user` (id, nombre, token, niveles de acceso y empresas), `styles` (colores de la empresa, caducidad de 7 días) y `numberNotifications`. **No hay Room, SQLite ni caché de datos de entrenamiento: sin conexión la app no sirve.**
- **Internacionalización:** neerlandés (por defecto) e inglés británico, 1.405 etiquetas por idioma; mercado Países Bajos y Bélgica.

### 1.7 Higiene y seguridad (nota breve, útil como lección)

- `config.xml` abre todo: `access origin="*"`, `allow-navigation href="*"`, intents a `http`, `https`, `tel`, `sms`, `whatsapp`, `mailto` y `geo`, y `loglevel=DEBUG`. La política CSP del `index.html` permite todo (`default-src *` con `unsafe-inline` y `unsafe-eval`).
- El token vive sin cifrar en `localStorage`. La copia de seguridad automática no está desactivada en el manifiesto (**sin confirmar** su efecto).
- Se pinta HTML del servidor con `v-html` (descripción de WOD, comentarios de atletas): riesgo de XSS si el servidor no sanea.
- Pushwoosh: los identificadores de aplicación están en `config.js`, pero la llamada de inicialización está comentada y el plugin no está empaquetado: **las notificaciones push no están activas en esta versión** (el servidor sí ofrece mensajería, ver §2.9).

---

## 2. Inventario de funciones, pantalla por pantalla

Convenciones: «cliente» = atleta o socio; «gestor» = entrenador o administrador del box (el servidor decide qué módulos y funciones ve cada rol). Todas las rutas están en `src/js/routes.js`; las pantallas, en `src/pages/` y `src/popups/`. Las cifras entre paréntesis son archivos de origen.

### 2.1 Mapa de rutas

| Ruta | Pantalla | Para qué sirve |
|---|---|---|
| `/` y `/home/idc/:idc` | `pages/home.vue` | decide entre inicio de gestor (`home-admin`) y de cliente (`home-user`) según `gym.whichPage` |
| `/login-new-account/` | `pages/login-new-account.vue` | iniciar sesión o crear cuenta |
| `/create-login/`, `/company-invite/` | `create-login.vue`, `company-invite.vue` | fijar contraseña tras una reserva; aceptar la invitación de un box |
| `/find-company/`, `/company-found/idc/:idc` | `find-company.vue`, `company-found.vue` | buscar un box por nombre, código postal o ciudad (Países Bajos y Bélgica) y unirse |
| `/my-companies/` | `my-companies.vue` | boxes del usuario; fijar uno como página de inicio; quitarlo |
| `/company/idc/:idc` | `pages/company.vue` | inicio del cliente en un box |
| `/agenda-routing/…`, `/agenda-list/…`, `/agenda/…` | `agenda*.vue` | calendario de clases |
| `/customer-agenda-my-appointments/idc/:idc` | `customer-agenda-my-appointments.vue` | mis clases |
| `/booking`, `/booking-confirm` | `booking.vue`, `booking-confirm.vue` | reserva pública incrustable |
| `/customer-invoice_product/idc/:idc` | `customer-shop.vue` | tienda |
| `/customer-my-purchases/idc/:idc` | `customer-my-purchases/*` | productos y facturas del cliente |
| `/customer-wod/idc/:idc` | `customer-wod/*` | **WOD, ranking y mi WOD** |
| `/customer-progress/idc/:idc` | `customer-progress/*` | progreso: resumen, medidas y pliegues |
| `/customer-reporting/idc/:idc` | `customer-reporting/*` | anamnesis y seguimiento del entrenador |
| `/customer-qr-checkin/…` | `customer-qr-checkin.vue` | mostrar mi QR de acceso |
| `/messages/`, `/messages/idm/:idm` | `messages.vue` | bandeja de mensajes del box |
| `/my-account/`, `/profile-image/`, `/change-password/`, `/remove-account/`, `/settings-other/…`, `/faq/…`, `/instruction-videos/…`, `/promote-us/…` | páginas homónimas | cuenta, foto, contraseña, baja, ajustes, ayuda, vídeos de instrucción (solo en neerlandés), programa de referidos |
| 18 rutas `manage-*` (`/manage-company/idc/:idc` es el menú de gestión) | `manage-*.vue` | todo el panel de gestión (§2.8) |
| `(.*)` | `not-found.vue` | no encontrada |

Rutas externas por URL (`?page=…&external=1`): `create-login`, `company-invite`, `agenda`, `booking`, `booking-confirm`, `customer-qr-checkin`, `qr-checkin-scanner`. Sirven para incrustar partes en la web del box (la ayuda explica un `iframe` de la agenda con `mode=week|day|listView`, ancho, alto y pantalla completa).

### 2.2 Cuenta y acceso

- **Inicio de sesión** con usuario o correo y contraseña; recuperar contraseña por correo (`user.lostAccount`); crear cuenta con aceptación de condiciones de uso y de las del box.
- **Cuenta** (`components/account-form.vue`, compartido con la gestión de clientes): usuario, correo, nombre, apellidos, teléfono y móvil, sexo, dirección completa, fecha de nacimiento, **dos contactos de emergencia (nombre y teléfono)**, **comentario temporal (por ejemplo una lesión)**, otros comentarios, e invitación por correo a usar la app.
- Foto de perfil cuadrada (jpg, máximo 200 KB), cambio de contraseña, **baja de la cuenta con borrado de datos**, lista de boxes, idioma (neerlandés o inglés) y ajustes de la agenda (vista semana, día o lista; línea o color completo; avisos por correo).
- Familiares: el cliente puede añadir «familiares» para inscribirlos en clases (`customer-agenda-partner-user.vue`).

### 2.3 Inicio del cliente (`pages/company.vue`)

Cabecera con imagen del box y enlaces a correo y web; aviso de facturas impagadas; botón «Check-in con QR» si el box lo tiene; tarjeta de próximas clases; escaparate de productos (3 o más) u oferta; últimas compras con créditos restantes; y lista de módulos activos del box (WOD, progreso, informes, compras…). La lista de módulos la manda el servidor (`gym.getModulesEnabledGym`).

### 2.4 Agenda y reservas

- **Calendario** en vistas semana, día y lista, varias agendas por box, filtro por entrenador, color por tipo de cita, estado por línea o color completo y desplazamiento a la primera hora ocupada. La disposición de citas solapadas por columnas es lo único algorítmico del cliente (§4).
- **Ficha de la clase** (`customer-agenda-appointment.vue`): inscribirse o darse de baja, lista de espera, familiares, créditos que se descontarán, plazos de inscripción y baja, plazas libres o «completa», entrenadores, ubicación y botón «Añadir a mi calendario» (enlaces para Google, y archivos para Apple y Outlook). Todos los estados llegan como banderas del servidor (cerrada, aún no abierta, máximo diario, sin créditos, sin producto, baja fuera de plazo sin devolución de créditos).
- **Reserva pública** (`booking.vue`): elegir fecha entre las disponibles de un producto (por ejemplo una clase de prueba), iniciar sesión o crear cuenta en el momento, aceptar autorización SEPA y condiciones, y confirmar (`booking.saveBooking`).
- **Gestor:** crear y editar citas (única o repetida a diario, entre semana, semanal, mensual o anual; solo esta o esta y las futuras), tipos de cita (duración, recordatorio en minutos, productos o créditos con los que se puede inscribir, plazas máximas, lista de espera, familiares permitidos, inscripción desde X horas antes, máximo por día, plazo de baja y baja con cobro, visibilidad de inscritos, colores), ajustes por agenda (agenda oculta, entrenadores, correos), inscribir a un cliente, **pase de lista** (presente o ausente, «no-shows», lista de espera y devolución o no de créditos, con porcentaje de presentes), mensajes a los inscritos, y **resumen de horas por entrenador** (citas, inscripciones y horas; módulo de pago).
- En el pase de lista aparece un icono de aviso con el **comentario temporal del atleta (lesión)**: una señal de seguridad deportiva barata.

### 2.5 Tienda y compras

- **Cliente:** escaparate con filtros, ficha del producto (créditos, validez en semanas o meses, plazos, autorización SEPA, periodicidad), carrito, pago (los métodos los fija el servidor; el sitio público cita iDEAL, Bancontact, SEPA y PayPal) y «mis compras» con dos pestañas: productos (créditos restantes, vigencia, estado, continuos o puntuales) y facturas (pagar, descargar, pagada o no).
- **Gestor** (`manage-products/*`, `manage-product.vue`): tipos de producto (suscripción, tarjeta de créditos, prueba gratuita, otro), importes con IVA, primer periodo distinto de los demás, número de plazos o continuo, créditos y vigencia, categorías de tienda, orden, visibilidad y retirada, gestión de stock, límite por cliente, página de gracias propia, textos y correo de confirmación por idioma, formulario de reserva y enlaces de venta, **códigos de descuento** (importe o porcentaje, máximos por pedido y por cliente, mínimo de productos, vigencia, productos aplicables) y archivo.

### 2.6 WOD: pizarra, resultado, ranking y «mi WOD» (lo más cercano a KPKN)

Pantalla `customer-wod/index.vue` con tres pestañas y un botón flotante «Buscar WOD» (búsqueda por nombre en las publicaciones, `WOD.searchWODWhiteBoard`).

**Pestaña WOD (`tab-wod.vue`)**
- Navegación por fechas (anterior y siguiente) y selector cuando hay varios WOD el mismo día.
- Tarjeta con nombre, descripción en HTML y un vídeo de YouTube incrustado si el WOD lo trae.
- Tu comentario previo, «Hay N resultados…» que salta al ranking, **historial** (todas las publicaciones del mismo WOD, con enlace a cada una) y **benchmark** (solo si el WOD está marcado como tal y tienes resultados: «Tu récord personal» y la lista fecha / marca / nivel).
- **Registrar tu resultado** y **borrar tu resultado**. Reglas observadas: solo sobre la publicación más reciente (`has_next_whiteboard`), mientras no venza el plazo (`logging_expired`, «se puede registrar hasta»), si el WOD permite el registro por el atleta (`athlete_log_not_allowed`) y si el atleta tiene un producto válido (`buy_product` → aviso «compra un producto para participar»).
- Si tu rol tiene el permiso `wod.logResult` aparece «Resultado del atleta» (§2.8).

**Registro de resultado (`popups/customer-log-result.vue`)**
- Los campos dependen del **código de puntuación del WOD** (`wod.code`). Los nombres de tipo de la tabla son una inferencia a partir de los campos; los nombres reales (`score_as`) los da el servidor:

| Código | Tipo de marca | Campos |
|---|---|---|
| 1 | tiempo (For Time) | minutos y segundos |
| 2 | rondas y repeticiones (AMRAP) | un campo «Round/Reps» |
| 3 | carga máxima | peso en kg |
| 4 | distancia | metros |
| 5 | repeticiones | repeticiones |

- Siempre opcionales o condicionales: **sexo** (m / v) si el perfil no lo tiene; **categoría de ranking** (las que el box defina); **nivel «RX o Scaled»** (por defecto Scaled; «Fundamental» y «RX» solo si el WOD las admite); **peso escalado (kg)**; comentarios; **URL de YouTube** (obligatoria si el WOD lo exige como prueba).
- Los campos son de texto libre, **sin validación de rangos ni formato en el cliente** (la validación, si existe, es del servidor: sin confirmar).

**Pestaña Ranking (`tab-ranking.vue`)**
- Navegación por fechas y botón de filtros (`filter-wod.vue`): **grupo** (hombres, mujeres o ambos), **día y/o global**, **nivel** (RX, FDM, Scaled) y **categorías** de ranking. Muestra los filtros activos como «chips».
- Dos bloques: **ranking del día** y **ranking global** (este último solo si el WOD tiene ranking global). Dentro, una tabla por categoría con posición (`number`), avatar, nombre, **marca con peso escalado entre paréntesis si lo hay** y nivel (RX, FDM o Scaled). En el ranking del día el nombre abre los comentarios del atleta (`WOD.getRankingDetails`).
- La **posición, el orden según el tipo de marca y el cálculo del ranking global vienen hechos del servidor**: sin confirmar la fórmula.

**Pestaña MY WOD (`tab-my-wod.vue`):** lista mensual (con flechas de mes) de los WOD en los que el usuario tiene resultado: fecha, nombre y «Mostrar».

### 2.7 Progreso e informes (cliente)

- **Progreso** (`customer-progress/*`): resumen por semana (acordeón con todos los valores de cada registro), tarjeta de **medidas** y tarjeta de **pliegues** (§3.2). Entradas y ediciones solo las hace el gestor (`gym.adminSendProgressUser`); el cliente consulta.
- **Intake + reporting** (`customer-reporting/*`): ficha de **anamnesis** (antecedentes, ayuda o metas, lesiones, tratamientos médicos, enfermedades o condiciones, medicación, programa a seguir, otros) y **informes de seguimiento** (metas, informe, acciones, otros). El gestor puede marcar cada entrada como «no visible para el cliente».

### 2.8 Gestión (entrenador y administrador)

| Módulo | Pantallas | Función |
|---|---|---|
| Resumen | `manage-company.vue`, `components/home-admin.vue` | carrusel de ingresos con porcentaje de variación, resúmenes desplegables, saldo total y retirable, fecha de pago a la cuenta, suscripciones SEPA (número e importe), facturas pendientes, últimas ventas |
| Panel (`dashboard`) | `manage-dashboard/*`, `components/dashboard.vue`, `chart.vue` | tres pestañas (financiero, clientes, agenda), selector de panel, rango de fechas, filtros y gráficos c3 (barras, líneas, sectores, apilados) con bloques de resumen; acciones sobre una selección de clientes (archivar, exportar, enviar mensaje). **Todo lo define el servidor** |
| Clientes | `manage-customers.vue`, `popups/manage-customer/*` | lista con búsqueda, orden, estados (bloqueado, invitado, sin cuenta), alta y edición, ficha con pestañas (datos, productos, citas), **archivo, restaurar, bloquear y borrar**, exportar con columnas y filtros por producto, invitar por enlace, cumpleaños, sincronizar datos |
| Productos y tienda | ver §2.5 | |
| Pagos | `manage-payments.vue`, `manage-transactions.vue`, `manage-sales.vue`, `manage-direct-debits.vue`, `manage-register/*`, `manage-accountancy/*`, `manage-money-withdraw.vue` | transacciones y saldos; ventas con reembolso por líneas y factura descargable; **suscripciones SEPA** (crear, editar, pausar o detener con fechas, cambio planificado de importes en bloque); **caja registradora** (efectivo con calculadora de cambio, TPV con terminal, factura por correo), libro de caja, ingresos y retiradas; contabilidad (ingresos con IVA, facturas impagadas con recordatorios, abonos y reembolsos fallidos, costes de transacción, saldos de fin de año, módulo de cobro a agencia); **retirada de fondos** con calendario, periodicidad y reserva |
| Cuenta de empresa | `manage-company-settings/*` | alta del comerciante: datos de la empresa, banco, firmantes y UBO, condiciones y tarifas; ajustes de aceptación de clientes y correo de bienvenida |
| Acceso | `manage-access-settings/*` | niveles por usuario y por componente o función |
| Estilos | `manage-company-styles/*` | colores de tema, cabecera y botones, logo y fondo |
| Mensajería | `manage-pushmessage.vue`, `components/pushmessage-sender.vue` | enviar a todos o a una selección, por push, correo o ambos, con asunto y enlace |
| API Connect | `manage-api-connect/*` | integración con **ActiveCampaign** (cuenta y clave API) y «triggers» (por ejemplo una venta o una prueba) que añaden al cliente a una lista o le ponen una etiqueta |
| QR | `manage-qr-access/*`, `manage-qr-checkin-scanner.vue` | eventos con acceso por QR (fechas, productos válidos, sonido), historial de check-ins y escáner |
| **WOD** | `manage-wod.vue`, `popups/manage-wod.vue` y 4 popups de categorías y publicaciones | ver abajo |

**Gestión del WOD.** Lista con búsqueda y orden (nombre o fecha de publicación), acordeón con descripción y **enlace público para compartir fuera de la app** (`siteURL/wod/<nombre>/<id>`, requiere al menos una publicación), y acciones Publicar, Editar y Eliminar según permisos (`wod.publish`, `wod.save`, `wod.remove`). Crear o editar: nombre, descripción, «registro por el atleta no permitido», «URL de YouTube del atleta obligatoria», «puntuar como» (los tipos vienen del servidor en `wodOptions`), categorías de ranking (se editan solo las no usadas), «ranking global», «benchmark», «RX», «Fundamental», «desactivar compartir», y publicar con fecha del WOD, «se puede registrar hasta» y vídeo de YouTube. **Un WOD ya publicado no se puede editar:** solo se edita o borra cada publicación. Registrar o borrar resultados en nombre de un atleta (buscar por nombre; borrar «el día» o «todo, incluido el ranking global»).

### 2.9 Mensajes y otros

- **Mensajes:** bandeja de avisos del box (`pushMessage.getMessages`, leer y borrar) con contador de no leídos que llega en cada respuesta del servidor.
- **QR del cliente:** el cliente elige evento y producto y la app dibuja un código con una cadena de consulta (`idc`, `ide`, `ids`, `t`); el escáner del gestor lo valida contra el servidor y responde con verde o rojo y un sonido (§4).
- **Ayuda:** FAQ del box, vídeos de instrucción y programa «gana dinero con…» (referidos).

### 2.10 Funciones de pago y módulos

- **Producto completo, de pago para el box:** según la web pública `wodapp.nl` (consultada el 20-oct-2026), 89,95 euros al mes el primer año y 110,95 después, con permanencia mínima de un año, más 0,50 euros por transacción del pago integrado. Incluye planificador de clases, tarjeta de créditos, administración de socios, facturación con domiciliación, gestión y pizarra del WOD, ranking global, notificaciones y redes sociales. Los precios pueden haber cambiado; **sin confirmar**.
- **Módulos activables desde la app** (`Gym.activateModule`, ventana `call-to-action.vue`): el servidor fija nombre, texto y precio mensual. Confirmados en el cliente: el módulo de **entrenadores / horas** (la oferta aparece en los ajustes de agenda), el módulo de **cobro a agencia** (activar y desactivar con `toggleCollectionAgencyStatus`) y el aviso de **módulo de pagos desactivado** en el resumen del gestor. Probables, sin confirmar: el **TPV con terminal** (hay una cadena huérfana «módulo PIN no activado, contáctanos para un terminal») y **API Connect**. El catálogo completo de módulos y precios es del servidor: **sin confirmar**.
- Para el atleta todo es gratuito dentro de lo que contrate su box; los pagos son compras de productos del box.

### 2.11 Módulos retirados o latentes (cadenas de interfaz sin pantalla)

`latent_ui_strings_unused.txt` lista las 413 etiquetas definidas y no usadas. Muestran la historia del producto y sirven como pista de lo que existe en el servidor o en la web:

- **Competición multi-WOD:** «WOD único / Competición», «Gestionar competiciones», «Posición por WOD», «Ranking final», «Restablecer ranking final», «Estos atletas tienen los mismos puntos», y reglas de edición de puntos y marcas. Es la única pista del esquema de puntos por posición y de desempates marcados. **Sin pantalla en la 18.0.6; el algoritmo exacto no se puede confirmar.**
- **Planes de entrenamiento:** día, ejercicios, «Realizado», series, **tempo**, repeticiones, duración, pausa, explicación y botones «Start timer / Reset timer». Módulo heredado de Personal Trainer App; `manage-company.vue` lo oculta (`nutrition` y `transfer` en una lista de módulos ignorados).
- **Nutrición:** esquemas y diarios (desayuno, comida, merienda, cena; kcal, proteína, carbohidratos y grasa).
- **Otros:** gráfico de progreso, IMC y estatura, estadísticas de clientes (activos, productos caducados, nunca inscritos), confirmación de condiciones por cita, y «pausa o activación planificada» de suscripciones.

---

## 3. Catálogo de métricas

### 3.1 Pose, barra y disco: no existe ninguna

| Métrica pedida | ¿Existe? | Evidencia de ausencia |
|---|---|---|
| Ángulos articulares, ROM, fases, tempo, TUT, simetría, verticalidad | no | sin cámara de análisis ni modelo; sin librería de pose |
| Trayectoria, desplazamiento, desvío de la barra o del disco | no | sin visión por computador (ni WebAssembly ni ML en los bundles) |
| Velocidad media y pico, potencia, pérdida de velocidad, perfil carga-velocidad, 1RM | no | 0 coincidencias de `1RM`, `one rep`, `rep_max`, `barbell`; la carga solo aparece como campo de entrada en los WOD de código 3 |
| Conteo automático de repeticiones | no | las repeticiones las teclea el atleta (código 5) |
| Porcentajes de carga, cálculo de discos, calculadora de series | no | «percent» solo aparece en IVA, descuentos, grasa corporal declarada, UBO y el % de asistentes a una clase |
| Sensores, BLE, pulsómetros, Health Connect | no | sin permisos ni APIs en el JS |

### 3.2 Valores que la app sí muestra o recoge

Ninguno se calcula en el cliente salvo los indicados.

| Métrica o valor | Definición observable | Unidad | Quién lo calcula | Cuándo se muestra y cuándo se abstiene |
|---|---|---|---|---|
| Marca de WOD por tipo (5) | ver tabla de §2.6 | s, rondas + repeticiones, kg, m, repeticiones | la teclea el atleta; el servidor la normaliza (**sin confirmar** el formato de «Round/Reps»; sin tope de tiempo en el cliente) | se pide solo el campo del tipo del WOD |
| Nivel del resultado | RX, Fundamental (FDM) o Scaled | etiqueta | atleta | RX y FDM solo si el WOD los admite; Scaled por defecto |
| Peso escalado | carga usada cuando se escala | kg | atleta | se muestra entre paréntesis si es mayor que 0 |
| Posición | orden dentro de la categoría y del bloque (día o global) | n.º | servidor | por categoría de ranking; sin confirmar el criterio de orden por tipo y los desempates |
| N.º de resultados de un WOD | contador de resultados registrados | n.º | servidor | si es mayor que 0 |
| Récord personal («PR») de benchmark | mejor marca propia en las publicaciones del mismo WOD | igual que la marca | servidor | solo con WOD marcado «benchmark» y con resultados |
| Historial del WOD | publicaciones del mismo WOD por fecha | fecha | servidor | si hay más de una |
| Composición corporal | peso, % graso, nivel de grasa visceral, masa libre de grasa, masa muscular, masa ósea, líquidos corporales, glucosa en sangre | kg, %, nivel, mg/dl (sin confirmar) | entrada manual del gestor | solo si hay valor; la app **no deriva** % graso a partir de los pliegues (sin confirmar en el servidor) |
| Perímetros (13) | pecho, abdomen (estómago), cintura, vientre, cadera, muslo, rodilla, gemelo y brazo (izquierdo y derecho en los cuatro últimos), más total | cm | entrada manual; **diferencias y total del servidor** | tabla con primera, anterior y última medición, diferencia con la anterior y con la primera; ventana de cinco registros si no hay tarjeta por defecto |
| Pliegues cutáneos (13) | barbilla, mejilla, pecho, bíceps, tríceps, escápula, costillas, hueso de la cadera, ombligo, rodilla, gemelo, cuádriceps y isquiotibiales, más total | mm | entrada manual; total del servidor | igual que arriba |
| Asistencia a una clase | presentes sobre inscritos | n.º y % | **cliente**: redondeo del cociente multiplicado por 100 | solo si hay al menos un presente |
| Inscritos, lista de espera, no-shows | contadores de la clase | n.º | servidor | por pestaña del pase de lista |
| Créditos restantes y vigencia | saldo de una tarjeta de créditos | n.º, fecha | servidor | en inicio, compras y ficha de la clase |
| Horas de entrenadores | citas, inscripciones y horas por periodo | n.º y h | servidor | solo con el módulo activo |
| Check-ins | total por evento y producto | n.º | servidor | pestaña de historial |
| Finanzas | ingresos con IVA, saldo, facturas pendientes, costes de transacción, importes en circulación de suscripciones | euros | servidor | según permisos de pagos |
| Cambio de caja | importe recibido menos total | euros | **cliente** | solo en pago en efectivo |

---

## 4. Algoritmos

Detección y seguimiento, calibración, escala, corrección de perspectiva, suavizado, derivadas, detección de repeticiones y fases, manejo de fps y cámara lenta, y controles de calidad de captura: **ninguno existe en esta app.** Lo único algorítmico que corre en el dispositivo:

1. **Lectura de QR** (`manage-qr-checkin-scanner.vue`, con html5-qrcode y ZXing-js; corre en la página externa que el APK abre en el navegador del sistema). Pide la lista de cámaras y deja elegir si hay varias. Analiza **10 fotogramas por segundo** dentro de un recuadro cuadrado del **70 % del lado menor** del visor, sin corrección de perspectiva propia. Al leer, pausa el lector, comprueba que la cadena traiga los cuatro parámetros (`idc`, `ide`, `ids`, `t`) y, si faltan, responde «lectura fallida» sin llamar al servidor; si están, el servidor valida (`scanQRCode`). Muestra verde o rojo y un sonido (dos MP3 de 27.360 B y 6.144 B) y **reanuda a los 2 s**.
2. **Generación de QR** (`customer-qr-checkin.vue`, con EasyQRCodeJS): dibuja la cadena de consulta del evento.
3. **Disposición de citas solapadas** (`appointmentTableHelper.js`): ordena por inicio, fin e id; asigna cada cita a la primera columna libre; calcula cuántas coinciden a la vez y reparte el ancho (como un calendario de escritorio). Pasa la altura y la posición a porcentaje de las 24 h.
4. **Consulta periódica del estado del pago con terminal** cada 4 s hasta aprobado, error, caducado o cancelado.
5. **Cambio en efectivo** (recibido menos total) y **suma de reembolsos** (importe por unidades).
6. **Sincronización incremental** de permisos y estilos por número de versión (§1.6).

---

## 5. Exportación y compartir

| Qué | Cómo | Contenido |
|---|---|---|
| **Vídeo con telemetría** | **no existe** | — |
| Compartir un WOD | enlace público `siteURL/wod/<nombre>/<id>` que copia el gestor al portapapeles | página web del WOD; con opción «desactivar compartir». Los botones de Facebook, LinkedIn y X están en el código pero comentados |
| Prueba de marca | URL de YouTube que el atleta pega al registrar; el WOD puede exigirla | vídeo externo, subido a mano por el atleta; sin análisis |
| Exportar clientes | `user.exportUsersForAdmin` (archivo generado por el servidor; su formato no se ve desde el cliente) | columnas elegidas (correo, nombre, móvil, dirección, nacimiento, sexo, cliente desde) y filtros por producto |
| Exportar contabilidad | período elegido; descarga de facturas; exportar transacciones y libro de caja | archivos del servidor |
| Facturas y enlaces de pedido | descarga y copiar enlace con «token de seguridad» | PDF / página |
| Calendario | «Añadir a mi calendario»: enlace de Google y archivos para Apple y Outlook (los genera el servidor) | cita |
| CSV o informe de entrenamiento | **no hay** | — |
| Nube | todo vive en el servidor del proveedor | — |

---

## 6. Datos e integraciones

- **Almacenamiento:** servidor del proveedor (fuente de verdad) y `localStorage` (sesión y estilos). Sin base local ni modo sin conexión.
- **Cuentas:** correo o usuario y contraseña propios; roles por empresa. Una cuenta puede pertenecer a varios boxes.
- **Integraciones reales:** pagos (SEPA, PayPal y TPV con terminal en el cliente; según el sitio público, también iDEAL y Bancontact) a través del procesador del proveedor; **ActiveCampaign** (API Connect); YouTube y reproductores de vídeo incrustados; enlaces de calendario; WhatsApp (formateo del móvil a prefijo internacional para el intent `whatsapp:`); correo y mensajería push (el servidor; el cliente no registra push en esta versión).
- **No hay:** sensores BLE, pulsómetros, relojes, Strava, Garmin, Fitbit, Apple Health ni Health Connect (0 coincidencias).

---

## 7. Las tres preguntas del usuario (nota del 20-oct)

1. **Perspectiva: ¿exige grabar a 90° de perfil o corrige ángulos oblicuos?** No aplica: la app no analiza vídeo. No hay homografía, ni elipse del disco, ni ejes del rack. Lo más parecido es el lector de QR, que no corrige perspectiva y deja al decodificador (ZXing) tolerar la inclinación (**sin confirmar** hasta qué ángulo).
2. **Seguimiento híbrido: ¿qué hace sin disco olímpico?** No aplica: la app no sigue ni disco ni cuerpo. Para ejercicios sin disco no ofrece nada automático: el atleta teclea la marca.
3. **Exportar vídeo con telemetría para redes: ¿existe?** No. Solo existen un enlace público al WOD y la opción de pegar la URL de un vídeo de YouTube como prueba. No hay trazo, gráficos ni «stickers», y la app no graba ni procesa vídeo.

---

## 8. Brechas frente a KPKN

Referencias leídas: `VIDEO_TECHNIQUE_V1.md`, `BANCO_COMPLETO.md`, `INFORME_CONFIANZA_20261020.md` y `MERCADO_FIABILIDAD.md` (en el mismo directorio), y `ARCHITECTURE.md` y `ANDROID_UI_SCREENS_MAP.md`. Para saber lo que KPKN ya tiene también se consultó el código en modo lectura (`data/models/Session.kt`, `CompetitionModels.kt`, `BodyProgressModels.kt`, `NutritionModels.kt`, `CardioHiitTemplates.kt`).

### 8.1 Tabla de brechas

Prioridad según el objetivo declarado (exactitud de pose y disco, y métricas para atletas avanzados). Esfuerzo orientativo: S = 1–2 días, M = 3–6, L = más de una semana.

| Función o métrica de la competencia | ¿La tiene KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta posible | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| Cualquier análisis por vídeo, pose, conteo o trayectoria | **Sí, y muchísimo más** (§8.2) | no hay nada que copiar | — | — | — |
| **Tipos de marca de WOD** (tiempo, rondas + reps, carga máxima, distancia, repeticiones) con su orden de comparación | Parcial: `CompetitionAttempt` guarda `weightKg`, `reps`, `distance` y `timeSeconds`, y existe la plantilla `CompetitionTemplateType.CROSSFIT`, pero sin «tipo de puntuación», sin rondas + reps ni comparador | Modelo `ScoreKind` {FOR_TIME, ROUNDS_REPS, MAX_LOAD, DISTANCE, MAX_REPS} con formateador y analizador de texto (mm:ss, «rondas+reps») y comparador (menor es mejor solo en tiempo). El tope de tiempo y su penalización se toman de la convención pública de CrossFit, no de esta app. Se guarda en el JSON del bloque técnico (sin migración de Room) | ninguna | media | S–M |
| **Niveles RX / Fundamental / Scaled** y carga escalada por resultado | No | etiqueta por resultado más `scaledLoadKg`; rankings separados por nivel (la app los filtra, no los mezcla) | ninguna | baja | S |
| **Benchmark con récord e historial** del mismo WOD a lo largo del tiempo | Parcial: hay PR de levantamientos (e1RM, `WorkoutMediaPrFlags`), no de WOD con nombre | biblioteca local de WOD con nombre (texto propio) y mejor marca por tipo y nivel; historial por WOD | ninguna | media | M |
| Ranking del día y global entre atletas, con filtros por sexo, categoría y nivel | No (sin servidor) | KPKN es local primero: ofrecer «mi ranking personal» de mejores marcas; un ranking de club exigiría backend | — | baja | L si hay backend |
| Competición multi-WOD con puntos por posición, ranking final y desempates marcados (**solo cadenas latentes**) | Parcial: `CompetitionRecord` con bloques técnicos e intentos; no hay puntos por posición entre pruebas | modelo de «competición» con N pruebas, puntos por puesto (esquema configurable), suma y desempate visible. El algoritmo de esta app no se conoce | — | baja | M |
| **Vídeo como prueba de la marca** (URL de YouTube obligatoria opcional) | Mejor: KPKN graba y analiza dentro de la serie, sin salir del teléfono | asociar un `WorkoutMedia` a la marca de competición como evidencia | — | baja | S |
| **Tarjeta de medidas bilateral** (13 sitios) con tabla «primera / anterior / última» y diferencias | Parcial: 9 `BodyMetric` con un solo lado (brazo, muslo) y sin gemelo, rodilla, abdomen ni vientre; no hay tabla comparativa | añadir sitios izquierdo y derecho y la tabla de diferencias (valor menos referencia) | ninguna | media-baja | S |
| **Pliegues cutáneos** (13 sitios, mm) y composición (grasa visceral, masa libre de grasa, masa ósea, agua, glucosa) | No | entrada de pliegues y ecuaciones **publicadas** de densidad y grasa (Jackson y Pollock, Durnin y Womersley, Siri); la app analizada no calcula nada | ninguna (fórmulas de la literatura) | baja | M |
| **Temporizadores AMRAP, EMOM, Tabata y For Time con tope** | Parcial: bloques HIIT / Tabata de cardio (trabajo, descanso, rondas) y AMRAP solo como modo de serie; no hay EMOM ni AMRAP / For Time por reloj. **Esta app tampoco los tiene** (solo cadenas de un módulo retirado) | construir sobre el servicio en primer plano, la voz (TTS) y las plantillas HIIT que ya existen; al terminar, pedir la marca (fila 2) | ninguna | media | M |
| Porcentajes de 1RM y cálculo de discos | **Sí** (e1RM en `Calculations.kt`, `PlateCalculator`) | — | — | — | — |
| Sensores, BLE, Health Connect | **Health Connect, sí** (flavor `health`). BLE de sensores (pulsómetro, barra con IMU), no: el manifiesto solo pide `BLUETOOTH_CONNECT`, para audio y voz. La app analizada tampoco los tiene | — | — | — | — |
| Anamnesis y seguimiento del entrenador con visibilidad al cliente; contactos de emergencia; aviso de lesión en el pase de lista | No (no hay rol de entrenador); hay catálogo de molestias (`DiscomfortCatalog`) | fuera de alcance personal; opcional: ficha de lesión y contacto de emergencia en el perfil | ninguna | baja | S |
| QR de acceso (generar y leer) | No | solo si algún día KPKN gestiona gimnasios | html5-qrcode / ZXing (Apache-2.0) y EasyQRCodeJS (MIT) | baja | M |
| Añadir a calendario (Google, Apple, Outlook) | No (no hay exportación `.ics` ni `CalendarContract`) | exportar competiciones y sesiones como `.ics` | ninguna | baja | S |
| Reservas con créditos, lista de espera y no-shows; pagos, SEPA, TPV y contabilidad; mensajería a socios; marca blanca; panel de gestión; API Connect | No y fuera de alcance | no aplicable a una app personal | — | — | — |

### 8.2 Lo que KPKN hace mejor

- **Todo el análisis por vídeo:** pose con MediaPipe, seguimiento del disco con velocidad calibrada, conteo de repeticiones con nivel de confianza (ALTA, MEDIA, BAJA) y abstención, fases, TUT, tempo, ROM, profundidad y bloqueo, trayectoria, pérdida de velocidad y perfil carga-velocidad. WOD App no mide nada.
- **Local primero y privacidad:** el vídeo no sale del teléfono. WOD App no funciona sin servidor (219 métodos remotos, sin base local) y la «prueba» es un enlace externo.
- **Marcas verificables:** en WOD App los resultados son texto libre autodeclarado, sin rangos ni validación en el cliente y sin sensores. KPKN puede respaldar una marca con vídeo y medición.
- **Experiencia nativa:** voz manos libres en sesión, recuperación (AUGE y anillos), programación y periodización, nutrición con intérprete local y Health Connect. Los módulos de entrenamiento y nutrición de WOD App están retirados.
- **Superficie de seguridad:** WOD App abre todo el WebView (acceso a cualquier origen, CSP abierta, token en `localStorage`, copia de seguridad no desactivada). KPKN es nativo, no usa `WebView` (comprobado por búsqueda en el código) y declara `allowBackup="false"`.
- **Ojo, a favor de WOD App:** pide un solo permiso. KPKN pide más porque hace más; conviene mantener la política de «el vídeo nunca sale del teléfono».

---

## 9. Índice de evidencias

Rutas dentro del APK (la fila «Mapa» significa `webpack:///./src/…` dentro de `assets/www/js/app.3d1010fe.js.map`).

| Tema | Ruta o símbolo | Qué demuestra |
|---|---|---|
| Identidad | `AndroidManifest.xml` (decodificado con apktool 3.0.3 vía `rea decode-android-resources`); `BuildConfig` (`APPLICATION_ID`, `VERSION_NAME`, `VERSION_CODE`) | paquete, versión, SDK, un solo permiso |
| Firma | `apksigner verify --print-certs` (SDK 36.1.0); bloque de firma v2 (script `sigblock.py`) | firmante, esquemas |
| Cordova | `res/xml/config.xml` (copia en `cordova_config.xml`); `assets/www/cordova.js` (cabecera) y `cordova_plugins.js` | plugins, versiones, permisividad |
| Clases | `com.bothoffmedia.wodapp.MainActivity.onCreate`; `org.apache.cordova.CordovaActivity`, `...device.Device`, `...inappbrowser.InAppBrowser`, `...labs.keyboard.Keyboard`, `...engine.SystemWebChromeClient.onPermissionRequest`; inventario completo en `rea_classes_all.json` (1.466) | parte nativa sin lógica propia |
| Bundle | `assets/www/js/app.3d1010fe.js`, `.map`; `chunk-vendors.b8b07d6b.js`, `.map`; `app_src_index.txt` (224 fuentes), `vendors_src_index.txt` (1.043) | árbol de fuentes recuperable |
| WOD (cliente) | Mapa: `src/pages/customer-wod/{index,tab-wod,tab-ranking,tab-my-wod}.vue`; `src/popups/{customer-log-result,wod-score-details,filter-wod,customer-search-wod}.vue` | tipos de marca por `wod.code`, RX / FDM / Scaled, rankings, benchmark |
| WOD (gestión) | Mapa: `src/pages/manage-wod.vue`; `src/popups/{manage-wod,manage-categories-wod,manage-add-category-wod,manage-edit-category-wod,manage-edit-publication-wod}.vue` | opciones de WOD y publicación |
| Progreso | Mapa: `src/components/customer-progress-{list,measurements,skinfold}.vue`; `src/popups/manage-customer-progress-item.vue`; `src/pages/customer-progress/*`; `src/pages/customer-reporting/*` | 8 valores generales, 13 perímetros, 13 pliegues |
| QR | Mapa: `src/pages/manage-qr-checkin-scanner.vue`, `customer-qr-checkin.vue`, `src/popups/{customer-qr-code-fetcher,manage-qr-camera-devices,manage-qr-event,manage-qr-event-archive}.vue`, `src/pages/manage-qr-access/*`; `assets/www/static/sounds/checkin-{valid,invalid}.mp3` | única cámara del producto, y corre en el navegador del sistema, no en el APK |
| Algoritmos | Mapa: `src/js/helpers/appointmentTableHelper.js`; `src/pages/manage-register/tab-register.vue` (`pinPollStatus`, `calculateAmount`); `src/components/agenda-manage-subscribers-list.vue` | lo único calculado en cliente |
| Transporte | Mapa: `src/js/helpers/{ajaxHelper,userHelper,syncHelper,stylesHelper}.js`, `src/js/config.js`, `src/js/routes.js`; `api_surface.txt` (12 servicios, 219 métodos) | protocolo, permisos, marcas |
| Cadenas latentes | Mapa: `src/js/language/languageTags.js`; `latent_ui_strings_unused.txt` (413) | módulos retirados |
| Bibliotecas | `vendors_map_sources.json`; cabeceras de `framework7-lite-bundle.js`, `jquery.js`, `moment.js`, `c3.js`, `package.js` de d3, `runtime-core.esm-bundler.js`, `easy.qrcode.min.js` | versiones y licencias |
| Ausencias | búsquedas en `app.js` y `chunk-vendors.js` (§1.3); `zip_listing.json` (sin `lib/`) | sin ML, sin sensores |
| Web pública | wodapp.nl (funciones y precio); ficha de App Store id6449405897 | contraste de producto (§Anexo B) |

---

## Anexo A. Método y reproducción

- **Herramientas:** REA 6.5.0 (`decode-android-resources`, con apktool 3.0.3, y `search-android-classes` para inventariar clases; esta última carga JADX en modo de solo lectura unos segundos), `apktool d --no-src` para `config.xml`, y del SDK de Android 36.1.0 `apksigner verify` y `dexdump` para comprobar la firma y leer `MainActivity`. **No se hizo descompilación completa con JADX ni se usó Ghidra** (no hay código nativo), por lo que no se tomó el candado `C:\kw\re\pesado.lock`. Los procesos Java vivos durante el trabajo eran de Gradle de otras sesiones.
- **Scripts** (`C:\kw\re\_scripts\wodapp\`): `list_zip.py` (inventario del zip), `safe_extract.py` (extracción con protección de rutas), `map_inventory.py` y `dump_sources.py` (leer los source maps), `find_versions.py`, `feature_summary.py` (API, permisos y etiquetas por archivo), `api_surface.py`, `unused_tags.py`, `lang_dump.py`, `dex_strings.py`, `sigblock.py`. Todos se lanzan con `python -I`.
- **Pasos:** listar el zip, extraer en `C:\kw\re\wodapp\` y decodificar recursos; recuperar las fuentes desde el source map; inventariar clases; leer las pantallas de WOD, progreso, QR y los ayudantes de red; cruzar las etiquetas de idioma con su uso; verificar firma y `MainActivity`.
- **Sala limpia:** el informe describe funciones con palabras propias; no copia bloques de código ni recursos; no se extrajo ningún modelo (no hay). Los scripts y las listas de `artifacts/` contienen solo nombres, cifras y etiquetas cortas de interfaz.

## Anexo B. Qué app era probablemente la buscada, y candidatos

- La búsqueda pública distingue dos productos llamados «WOD App»: la analizada (`wodapp.nl`, gestión de boxes) y **«WOD App: Timer & AI Workout»** (Stellar Apps SL), una app de entrenamiento con IA, temporizadores (Tabata, EMOM, AMRAP, For Time), programas de 12 semanas, biblioteca de ejercicios con vídeos y registro de marcas y PR. Según su ficha de App Store es solo para iPhone, iPad y Mac, sin versión Android; la ficha tampoco habla de análisis automático de vídeo ni de HealthKit.
- Candidatas Android con temporizadores AMRAP, EMOM, Tabata y For Time, según listados públicos: **SmartWOD Timer** (contador de rondas y registro de entrenamientos) y **WOD 365 Timer** (`org.slingersoft.wodTimer`). Para marcas, benchmarks y percentiles: **WODStrat** (apareció en la búsqueda; ofrece percentiles frente a la comunidad y al box) y, por conocimiento general y sin comprobarlos ahora, SugarWOD y Beyond the Whiteboard. **No se descargó nada**; la elección del siguiente objetivo de RE es del usuario.

## Anexo C. Lo que no se pudo confirmar

- Toda la lógica del servidor: cálculo del ranking del día y del global, orden por tipo de marca, desempates, formato de «Round/Reps», existencia de un tope de tiempo, valores de `score_as`, modo competición y precios de módulos.
- Versión exacta de html5-qrcode, flatpickr, Bowser, skeleton-elements y Kotlin stdlib; licencia de cordova-custom-config.
- Estado actual de los precios publicados y de los módulos de pago; efecto de la copia de seguridad automática sobre el token.
- Comportamiento en ejecución, rendimiento, y cualquier función que solo exista en la web o en iOS.

### Fuentes web consultadas (solo lectura, 20-oct-2026)

- Web del producto: https://www.wodapp.nl (funciones y precios)
- «WOD App: Timer & AI Workout» (otra app): https://apps.apple.com/app/id6449405897
- Listados de temporizadores Android: resultados de búsqueda pública (SmartWOD Timer, WOD 365 Timer); sin visitar tiendas ni descargar APK.
