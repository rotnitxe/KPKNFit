# My Lift 17.0 (`com.trainerfu.cmlf`) · ingeniería inversa estática (informe corto)

Fecha: 2026-10-21. Encargo: brief `docs/video-technique/briefs/RE_COMUN.md`, acortado por la instrucción del usuario («si tu app no aporta nada nuevo, no hagas el análisis profundo»). Sala limpia: nada se ejecutó ni se instaló; solo se leyeron el manifiesto y los recursos (`rea decode-android-resources`, apktool), los metadatos del DEX y las cadenas del paquete de JavaScript. No se usó JADX completo ni Ghidra: no se tomó el candado `pesado.lock` y no quedó ningún proceso vivo.

## Veredicto
- **No es la app de VBT de Balsalobre-Fernández.** Es «MyLift», una app de planes de entrenamiento de un entrenador (ficha de la App Store: Davis Strength Systems LLC, © FitRev), hecha con React Native. El paquete lleva el prefijo `com.trainerfu`, pero el servidor y la pasarela de pago que referencia son de «FitRev» (relación con TrainerFu: sin confirmar). Solo comparte el nombre.
- **No aporta nada al VBT ni a la técnica de KPKN:** sin cámara, sensores, BLE ni modelos de ML; sin velocidad, 1RM, RPE/RIR ni exportación de datos.
- **El My Lift de Balsalobre ya está analizado.** Su ficha pública dice «My Lift is now part of My Jump Lab»; en Android es el módulo VBT de `com.my.jump.lab` (`MY_JUMP_LAB_RE.md` §2.3, §3.2–3.3 y §4.3). No hace falta buscar otro APK.

## 1. Identidad
| Dato | Valor |
|---|---|
| Paquete · versión | `com.trainerfu.cmlf` · 17.0 (versionCode 183) · minSdk 24 · targetSdk 36 · nombre «MyLift» |
| Archivo | `My+Lift+App_17.0_APKPure.xapk`, 52 424 375 B, SHA-256 `a15bb794c980b894153fcf88f7e72e83c0b0dee32dbcb088a40ceec5f705ba4a` |
| APK base | `com.trainerfu.cmlf.apk`, 28 041 142 B, `49f681235c3242c5efcc2b95c8d53ebd1b878d0757eef9243a5f648ced7bc2d9` |
| Split ABI | `config.arm64_v8a.apk`, 23 525 585 B, `6fe10a615df79811544c2813ff5306a4e5c72119fade7d5252c6ee9cd3ad2e7c`. Los otros 18 splits (densidad e idiomas) están en `evidencias/hashes_sha256.txt` |
| Arquitectura | React Native con **Hermes**: un solo `assets/index.android.bundle` (7,6 MB, bytecode HBC v98, 30 175 funciones, 34 757 cadenas). Código nativo propio mínimo (17 clases: actividad, aplicación, token FCM, `R`); los 4 DEX (24 238 clases) son bibliotecas |
| ABIs y nativo | solo `arm64-v8a`, 24 `.so` (React Native, Hermes, Reanimated, screens, SVG, gesture handler, Nitro, uCrop, SQLite, Bugsnag). Ninguna de visión, cámara o ML |
| Modelos de ML | **ninguno** (sin TFLite, MediaPipe, ONNX, ML Kit ni OpenCV) |
| Permisos | `INTERNET`, `POST_NOTIFICATIONS`, red, `WAKE_LOCK`, almacenamiento heredado, FCM, install referrer. **Sin `CAMERA`, micrófono, Bluetooth ni ubicación**; sin sensores. La foto se pide a la cámara del sistema (`IMAGE_CAPTURE`) |
| Terceros (licencia según cada proyecto; verificar) | React Native, Hermes, Reanimated, screens, SVG, gesture handler, react-native-video, image-picker, health-connect, blob-util, Bugsnag y Supabase JS (MIT); Mixpanel, uCrop, Health Connect client 1.1.0, Room 2.8.4 y Media3 (Apache-2.0); Firebase y Play Services (propietarios). Versiones de las bibliotecas de React Native: sin confirmar |

## 2. Qué hace la app (por las cadenas de interfaz; flujos sin ejecutar)
- **Cuenta:** correo y contraseña (Supabase), recuperar y borrar cuenta, foto de perfil con recorte, kg·km o lb·millas, suscripción (gestionar y pausar), aviso de actualización obligatoria.
- **Planes:** elegir programa (la ficha de la tienda cita Davis Split, cuerpo completo, push-pull-piernas y torso-pierna), semana nueva cada lunes, días de descanso, entrenamientos extra, biblioteca de ejercicios con vídeo, filtros por equipo y músculo.
- **Sesión:** deslizar para empezar; series planificadas frente a reales (peso, repeticiones, «hasta el fallo», con tiempo, por mano o pie, con ayuda); notas por serie y ejercicio; sustituir y revertir un ejercicio; pausar, reanudar, abandonar, reiniciar o repetir; temporizador de descanso con aviso.
- **Historial y salud:** entrenamientos y ejercicios anteriores, racha de días, totales de tiempo y ejercicios; peso corporal con peso objetivo, agua con meta diaria, pasos de Health Connect (solo lectura; el texto promete no escribir ni pedir frecuencia cardiaca o sueño) y fotos de progreso.
- **Comunidad:** publicaciones con foto o vídeo, comentarios, reportar y fijar; «Coaching & form check» («publica un vídeo y recibe comentarios de otros levantadores»): revisión humana, no automática.
- **Datos y telemetría:** Supabase (cuenta, datos y sonidos de logro y temporizador), un servidor propio y una pasarela de pago de FitRev (direcciones en el paquete), Firebase FCM, Mixpanel y Bugsnag. Sin BLE, wearables, CSV ni PDF de datos del usuario.

## 3. Contraste con los temas pedidos
| Tema | En esta app | Dónde está lo real |
|---|---|---|
| Otros modos de medida | Ninguno: no mide movimiento | `MY_JUMP_LAB_RE.md` §2.3 y §4.3 (manual por fotogramas, automático con disco, en vivo o tras la serie) |
| Perfiles carga-velocidad y 1RM | Ni velocidad ni 1RM (0 cadenas de 1RM, Epley o Brzycki) | `MY_JUMP_LAB_RE.md` §3.2 y §3.3; `METRIC_RE.md` §4.12 |
| Zonas y autorregulación | Sin RPE, RIR ni zonas; solo «hasta el fallo» y notas libres | `MY_JUMP_LAB_RE.md` §2.1 y §2.5 (umbral de pérdida, velocidad objetivo, semáforo de readiness); `METRIC_RE.md` §2.7 (RPE o RIR por serie) |
| Encoders y sensores | Ninguno («velocity», «accelerometer» y «gyroscope» del paquete son internos de Reanimated y de los gestos táctiles) | `MY_JUMP_LAB_RE.md` §2.5 (acelerómetro) |
| Historial y fatiga | Historial de sesiones y racha; sin pérdida de velocidad | `METRIC_RE.md` §3 («Fatiga», «Consistencia») |
| Exportación | Ninguna (solo publicar en la comunidad) | `METRIC_RE.md` §5.1 (vídeo 9:16 con telemetría) |

## 4. Las tres preguntas del usuario
1. **Perspectiva:** no aplica; la app no analiza vídeo.
2. **Tracking híbrido:** no aplica; no sigue ni disco ni cuerpo.
3. **Vídeo con telemetría:** no. Solo reproduce vídeos de ejercicio y adjunta fotos o vídeos a una publicación.

## 5. Brechas frente a KPKN
| Función | ¿La tiene KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| Revisión de forma por personas («Coaching & form check») | No | No recomendable: choca con «el vídeo no sale del teléfono». Si algún día se quiere, enviar solo un clip elegido por el usuario y con permiso explícito | — | Ninguna (decisión de producto) | Alto |
| Health Connect de solo lectura con promesa de permisos mínimos | Sí, en el flavor `health` | Copiar solo el texto de transparencia en el aviso de permisos | Health Connect client (Apache-2.0) | Baja | Bajo |
| Programas semanales, registro de series, agua, peso, fotos, comunidad | Fuera del alcance de la ola de técnica en vídeo | — | — | — | — |

**Única novedad:** la revisión de forma por personas, y no vale para este motor. Nada de esta app supera lo ya recogido en `MY_JUMP_LAB_RE.md` y `METRIC_RE.md`. KPKN mantiene su ventaja: pose completa, disco con elipse y paralaje, confianza y abstención, y privacidad del vídeo.

## 6. Evidencias y sin confirmar
Carpeta `C:/kw/re/evidencias/mylift-re-20261020/` (0,8 MB): `xapk_inventory.json`, `hashes_sha256.txt`, `apk_inventory.json`, `manifest_decoded_redacted.xml`, `resource_strings_redacted.txt` (claves tapadas), `dex_packages.json`, `dex_app_classes.txt`, `hbc_header.json`, `hbc_named_functions.tsv` (solo nombres), `hbc_ui_copy_candidates.txt` y `keyword_scan.json`. Scripts en `C:/kw/re/_scripts/mylift/` (`hbc_strings.py` lee la tabla de cadenas de Hermes sin ejecutar nada; `keyword_scan.py` repite las búsquedas).

Pruebas de identidad: `app_name` «MyLift»; el recurso de Health Connect la define como app de entrenamiento con programas; la política de privacidad y el paquete apuntan a `myliftapp.com` y a servidores `fitrev-*`; la ficha pública de la App Store del mismo nombre coincide (entrenamiento, comunidad y feedback de entrenadores, sin velocidad ni 1RM); manifiesto sin cámara ni sensores.

Sin confirmar: el comportamiento en ejecución; si el permiso de pasos de Health Connect funciona (no aparece declarado en el manifiesto decodificado); el proveedor exacto del feed y el chat; las versiones de las bibliotecas de React Native; el firmante del APK (no se verificó la firma).

Fuentes públicas leídas: fichas de la App Store de [My Lift App](https://apps.apple.com/app/id1557588510) y de [My Lift: Measure your strength](https://apps.apple.com/us/app/-/id1104432787).
