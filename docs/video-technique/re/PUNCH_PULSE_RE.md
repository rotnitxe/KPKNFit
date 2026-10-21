# AI Punch Pulse (AI Boxing Coach) 2.4.6 · ingeniería inversa corta (21-oct-2026)

Análisis **estático** del XAPK que aportó el usuario. No se ejecutó ni se instaló la app; no se usaron teléfono, emulador, git ni Gradle.
Sala limpia: funciones y algoritmos con palabras propias; los nombres de archivo, clase y los mensajes de una línea son solo
evidencia; no se extrajeron modelos ni recursos. **[C]** = confirmado en el archivo; **[D]** = deducido; «sin confirmar» = no
verificable en estático. **Por orden del coordinador (21-oct) el trabajo se detuvo tras el inventario:** sin JADX, sin Ghidra y
sin desensamblar el código Dart.

## 1. Veredicto

- **No aporta un método de medición nuevo.** Es una app de *prácticas guiadas*. El usuario elige la técnica (jab, cruzado,
  gancho…) y la app comprueba con reglas geométricas sobre los 33 puntos 2D de ML Kit si la ejecución «cumple». No hay cálculo de
  velocidad, golpes por minuto, retorno a guardia ni potencia [C: ninguna cadena ni columna lo muestra]. Solo cámara en vivo: sin
  análisis de vídeo, sin exportación, sin calibración ni corrección de perspectiva.
- **Única novedad útil:** el catálogo de *comprobaciones de forma por golpe* (§3), como lista de qué mirar en boxeo: guardia de la
  mano libre, codo a la altura del hombro, ángulo del codo en el uppercut, caída de la muñeca en el parry, rejilla de la cabeza en las
  esquivas. **Los umbrales no se recuperaron** (son constantes del código Dart compilado).
- **Recomendación:** no invertir más aquí. La base metodológica para combate en KPKN sigue siendo la segmentación de eventos
  balísticos de `SPORTSBOX_RE.md` §4.6 (pico de velocidad angular + reversión). La ficha de la tienda anuncia «speed» y sensores
  internos (`CANDIDATAS_Y_METRICAS.md` §2.8); en el código los sensores solo sirven de compuerta de estabilidad (§3).

## 2. Identidad

| Elemento | Valor |
|---|---|
| App | «AI Boxing Coach»; paquete `com.boxingtrainer.app`; versión 2.4.6 (código 18); minSdk 24, targetSdk 36; protegida con PairIP (comprobación de licencia de Play) |
| XAPK | APKPure, 70.241.014 B, SHA-256 `5ea3a192ad86aba337c3f86a68954f35cedc00d2da6ce58e6ea22afff498d767` |
| `com.boxingtrainer.app.apk` | 46.582.318 B, `ed01dfc2ffb7c7c114e901f8666853eb511885c45ba57763420bf4f8f1b5a358` |
| `config.armeabi_v7a.apk` | 23.327.386 B, `751c77d1b3a0897faaaf85ea39e9e5b1a7f2e5d37112db8f236938a88a60e587` |
| `config.en.apk` · `config.mdpi.apk` | 45.465 B `006388870b299609035ab5365fce8e8a5a8b6deff34d9c6ba961b9dc5a07c097` · 75.761 B `9e1918be3c88fc996840f9f914817611e5cd52aca998e76f6e20b4af00be2680` |
| Arquitectura | **Flutter** (Dart 3.12.2 estable, SDK compilado el 9-jun-2026; snapshot AOT ARM 32 bits, hash `ace654289f5abc240509fc941453ebc5`). 64 archivos Dart propios bajo `package:ml_kitfeb6/…`. El DEX (7 archivos, 27.795 clases) es casi todo bibliotecas y plugins; Kotlin 2.0.21, AGP 8.7.0 |
| Nativo (solo ARMv7 en este XAPK; arm64 sin confirmar) | `libapp.so` 8,1 MB (todo el código de la app, sha `a26f672a…877496a`), `libflutter.so` 8,45 MB, `libxeno_native.so` 6,7 MB (tiempo de ejecución MediaPipe de ML Kit: 132 símbolos JNI `Java_com_google_mediapipe_framework_*`) |
| Modelos de ML (todos de Google ML Kit, en `assets/mlkit_pose/`) | Detector de persona `pose_person_detector_f16.tflite` 2,96 MB: `[1,224,224,3]` → `[1,2254,12]` + `[1,2254,1]`. Marcadores `pose_landmark_detector_full_f16_inf.tflite` 6,43 MB y `…lite…` 2,81 MB: `[1,256,256,3]` → 195 (39 puntos × 5), presencia, máscara 128×128, mapas 64×64×39 y 117 (39 × 3, «world»). Dependencia `pose-detection-accurate` 18.0.0-beta5. Cuál usa: sin confirmar. **No hay modelo de acción ni de golpes** |
| Terceros (licencia) | ML Kit (términos de Google); Flutter y `camera`, `sensors_plus`, `go_router`, `shared_preferences` (BSD-3); `google_mlkit_pose_detection`, `google_mlkit_commons`, `supabase_flutter`, `provider`, `flutter_3d_controller` (MIT); `just_audio` (Apache-2.0/MIT); `flutter_inappwebview`, `rxdart` (Apache-2.0); Firebase Auth 23.2.1, Analytics 22.5.0, Play Billing 7.1.1 (propietarios). Versiones de los paquetes Dart: sin confirmar |
| Permisos y sensores | CAMERA, RECORD_AUDIO (sin uso visible), INTERNET, WAKE_LOCK, VIBRATE, notificaciones, FCM, BILLING, AD_ID. Acelerómetro y giroscopio (`sensors_plus`) sin permiso |
| Nube | **Supabase** (dirección y clave anónima en `assets/flutter_assets/lib/.env`; valores no copiados). Firebase en Dart: core, Crashlytics y Messaging (FCM); Analytics, Auth y Firestore solo como dependencias nativas. ID de AdMob en el manifiesto pero sin SDK de anuncios; plugin de compras registrado, sin paquete de compras ni cadenas de «Premium» en Dart |
| Recursos | Avatar 3D `apr22_screen_addition.glb` (7,96 MB, malla `Boxermesh`, 98 huesos, 22 animaciones: jab, cross, `hook_lead/rear`, `uppercut_lead/rear`, `body_jab`, `body_cross`, `jab_cross`, `jab_jab_cross`, `uppercut_lead_cross`, `parry_lead/rear/right`, `slip_lead/rear`, `bob`, `bobing_and_weaving_left/right`), mostrado en un WebView con `model-viewer`; un sonido de acierto (mp3) |

Pantallas [C]: onboarding y tutorial; cuentas (correo, Google, anónimo); inicio con plan diario y racha; niveles Beginner /
Intermediate / Advanced; módulos Jab & Cross, Uppercut & Hook, Slip & Parry, Bob & Weave y Advanced Combos; ejercicio con cuenta
atrás, temporizador, cámara con superposición y entrenador 3D; ajustes (minutos de ronda, segundos de descanso, rondas); progreso
(golpes, precisión por tipo, tiempo, resumen semanal, racha); recordatorios; perfil (borrar cuenta).

## 3. Detección, clasificación y guardia

**Flujo** [piezas C; orden D]:
1. `CameraView` recibe el flujo de imágenes (NV21/YUV420/BGRA → `InputImage` con rotación). Un `ProcessingOptimizer` sube o baja el
   estrangulamiento según la tasa de fotogramas y un `MemoryManager` limpia el historial si falta memoria. fps de análisis: sin confirmar.
2. ML Kit devuelve 33 puntos 2D con probabilidad. Los puntos z y «world» no se ven usados [sin confirmar]. No hay filtrado temporal visible [sin confirmar].
3. `PoseValidator` tiene tres salidas: válido, advertencia («Low confidence for: …») e inválido («Missing critical landmarks: …»).
   Se abstiene antes de juzgar. Qué puntos son «críticos» y con qué umbral de probabilidad: sin confirmar.
4. `BoxingAnalyzer` reparte a `PunchAnalyzer` o `DefensiveAnalyzer` según el movimiento de la pantalla. Una memoria de poses
   (`isPoseHistoryFull`, `previousPose`, `_cleanupPoseHistory`) permite comparar con fotogramas anteriores.
5. `AnalysisResult`: validez, confianza, mensaje de corrección y un indicador «Guard Up».
6. `SensorService`: acelerómetro y giroscopio dan «estable» y «vertical». Si no, el golpe se detecta pero **no se registra**
   («Punch detected but NOT recorded») y el temporizador sigue. Si sí, `_onPunchDetected` lo inserta en Supabase y suena un aviso
   con vibración. Columnas vistas en las cadenas: `punch_type`, `move_type`, `confidence_score`, `power_rating`, `analysis_type`,
   `device_stable`, `device_vertical` (a qué tabla pertenece cada una: sin confirmar).

**La clasificación es verificación dirigida** [D]. No reconoce un golpe libre entre N clases: el usuario elige («Ready for jab /
cross / uppercut…») y las reglas de *ese* movimiento lo aceptan o corrigen (`analyzeJab`, `analyzeCross`, `analyzeLeftHook`,
`analyzeRightHook`, `analyzeLeftUppercut`, `analyzeRightUppercut`, `analyzeBodyJab`, `analyzeBodyCross`, `analyzeWeave`…). Los
combos (Jab-Cross, Double/Triple Jab-Cross, Jab-Cross-Hook, Lead Uppercut Cross, Body-Head Combo) parecen listas de pasos; cómo se
encadenan: sin confirmar. Distinción golpe/amago: sin evidencia.

| Movimiento | Magnitudes que registra [C, etiquetas de depuración] | Correcciones [C] |
|---|---|---|
| Jab, cross | longitud hombro→codo y codo→mano (proyección 2D: pista de extensión y escorzo [D]) | «Adjust your shoulder to elbow length for the jab», «…elbow to hand length for the cross» |
| Body jab, body cross | altura de guardia; zona de la cadera como objetivo | «Aim your jab towards the right hip area» |
| Ganchos (izq./der.) | altura del codo sobre el hombro; «codo a la altura del hombro» sí/no; altura de guardia | «Raise / Lower your elbow to shoulder level», «…hook to face level» |
| Uppercuts (izq./der.) | ángulo actual del brazo (el codo: D); altura de guardia | «Bend your arm more…», «Keep your uppercut tighter – current angle», «Drive your uppercut upward» |
| Parry (adelante/atrás) | Y previa de la mano (la muñeca: D); umbral mínimo y máximo de movimiento; altura de guardia | «Ready for parry – move your right hand downward» |
| Slip, bob, weave | posición de la nariz en una rejilla 3×3 (nueve celdas con nombre) | «Move head to left line», «Return to center to complete the weave» |

**Guardia:** solo una comprobación puntual por intento (altura de la mano libre, indicador «Guard Up»; «Keep your left/right hand up
for guard!», «Don't drop your right hand too low»). No hay tiempo de retorno a la guardia ni guardia baja sostenida. El punto de
referencia de la altura: sin confirmar.

**Velocidad, ritmo, rondas e intensidad:** sin cálculo ni unidad física [C]. El texto de progreso promete «speed, power, and
accuracy» y la tabla tiene `power_rating`; cómo se rellena: sin confirmar. Las rondas son solo temporizador.

**Umbrales: sin confirmar.** Son constantes del Dart AOT (ARM 32 bits). Recuperarlas exigiría deserializar el snapshot de Dart 3.12.2
(estilo Blutter) y desensamblar `analyzers/*.dart`. No se hizo: las reglas no están validadas (NL) y su valor para KPKN es bajo.

## 4. Las tres preguntas

- **Perspectiva:** no corrige. Es 2D, con el móvil **vertical, quieto y apoyado en una pared**, tren superior visible («Place your
  phone on a stable surface, leaning against a wall…»); la compuerta del IMU lo hace cumplir. Sin homografía ni elipses.
- **Tracking híbrido:** no existe. Solo pose; sin seguimiento de guante, saco ni objeto.
- **Vídeo con telemetría:** no. No graba ni exporta ni comparte (sin `video_player`, galería ni compartir en el código Dart); la
  superposición (esqueleto, rejilla) solo se ve en vivo. Voz: ninguna (sin TTS); solo sonido, vibración y el avatar 3D.

## 5. Brechas frente a KPKN

| Función o métrica de la competencia | ¿KPKN? | Sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| Guardia alta de la mano libre (indicador por intento) | No (M44 solo propuesto) | Mano a la altura de mejilla/cabeza, normalizada por ancho de hombros; mide también el **tiempo de retorno** | MediaPipe Pose (Apache-2.0) | alta para combate | baja-media |
| Comprobaciones de forma por golpe (codo–hombro en ganchos, ángulo del uppercut, caída de muñeca en parry) | No | Reglas sobre ángulos 3D «world»; umbrales ajustados con el corpus propio, no copiados | MediaPipe Pose | media (tras el segmentador balístico) | media |
| Compuerta de estabilidad del móvil por IMU | Parcial (gravedad y roll en `capture.json`) | Avisar o rechazar si la desviación del acelerómetro/giroscopio supera un umbral durante la captura | `SensorManager` (AOSP) | baja | baja |
| Rejilla 3×3 de cabeza para esquivas | No | Nariz normalizada, con reposo como origen | MediaPipe Pose | baja | baja |
| Entrenador 3D con animaciones nombradas | No | Producto aparte; no reutilizar el recurso | — | baja | alta |

**KPKN hace mejor:** puntos «world» 3D y clasificador de vista; abstención por confianza (ALTA/MEDIA/BAJA) medida con un banco de
1.076 clips; filtro de fase cero con PTS; análisis de vídeo grabado y de galería; exportación con telemetría planificada (D44).
Punch Pulse es 2D, solo en vivo y sin validación publicada.

## 6. Índice de evidencias

- Evidencias pequeñas (2 MB): `C:/kw/re/evidencias/punchpulse-re-20261020/` — `inventario-*.json` (hashes), `flutter-build.json`,
  `AndroidManifest.xml`, `res-strings.txt` y `apktool-resources.json` (claves redactadas), `libs-versions.txt`,
  `dex-classes.txt`, `dart-app-source-files.txt` (64 archivos), `dart-app-strings-punch-guard.txt`,
  `dart-private-members-by-library-key.txt`, `dart-packages-licenses.tsv`, `tflite-models.txt`, `mlkit-graphs.txt`, `glb-summary.txt`.
- Desplazamientos en `lib/armeabi-v7a/libapp.so` (de `config.armeabi_v7a.apk`): `punch_analyzer.dart` 2685648; `defensive_analyzer.dart`
  2021632; `pose_validator.dart` 1896584; `sensor_service.dart` 1975136; «Jab - Shoulder to Elbow Length» 2687888; «, Guard Up: »
  2544456; «Lead Hand Parry - Max Movement Threshold» 2095984; «Missing critical landmarks» 2084920; «Punch detected but NOT
  recorded» 2293984; «analyzeWeave: Raw Nose Coordinates» 2098648.
- Sin confirmar: umbrales, fps, modelo ML Kit elegido (completo o ligero), modo de flujo o de imagen única, encadenado de combos,
  llenado de `power_rating`, ABI arm64, versiones de los paquetes Dart.
