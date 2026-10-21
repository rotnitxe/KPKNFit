# CaliLens: Calisthenics AI 1.0.4 (Android) · ingeniería inversa estática · informe definitivo

Fecha: 2026-10-21. Encargo: brief `docs/video-technique/briefs/RE_COMUN.md` más la nota del usuario: centrarse en lo NUEVO frente a los
informes ya hechos (Sportsbox, Yogger, Metric, My Lift, My Jump Lab, AscentAI…): criterios de repetición válida, criterios de forma y
cronometraje de los isométricos, cuerpos invertidos y horizontales, progresiones; lo ya visto, en una línea. **Este informe sustituye al
anterior**, que describía por error la app de tienda `co.shopney.calilens` y recogía material de la web. Lo que sale solo de la web va
marcado **[W]** y el Anexo A confirma o corrige con el código cada afirmación del informe anterior.

Sala limpia: nada del APK se ejecutó ni se instaló; no se copió código ni se extrajeron modelos ni recursos. Todo va con palabras
propias; como evidencia, nombres de módulos, funciones y clases, valores y fragmentos de una línea. Lo no verificado lleva «sin
confirmar».

**Método.** La app es Expo/React Native: toda la lógica está en un paquete Hermes (bytecode v96, 37 319 funciones, con los nombres de
función conservados). **No usé JADX ni Ghidra ni tomé `pesado.lock`**: no leen Hermes y la memoria libre (4,4–5,5 GB) estaba por
debajo del mínimo del brief (6 GB); no queda ningún proceso mío vivo. Escribí un lector y un seudodescompilador de Hermes propios a
partir de las cabeceras públicas del formato (MIT) —los 37 319 cuerpos de función se decodifican enteros— y un desensamblador Dalvik
propio para el módulo nativo. Scripts: `C:\kw\re\_scripts\calilens-real\`. Pruebas: `C:/kw/re/evidencias/calilens-real-re-20261021/`
(≈ 260 KB, sin claves ni modelos).

## Lo esencial, por orden de impacto para KPKN

1. **El cronómetro de un isométrico NO suma segundos de forma válida.** Es la duración de la **ventana estable más larga** (mín. 2 s;
   1 s en el «front touch») en la que la pose cumple unos **criterios gruesos por habilidad** (pendiente del cuerpo, codos, cadera,
   muñecas por encima o por debajo de los hombros) **y** el cuerpo está quieto (movimiento medio de 14 puntos ≤ 0,15 alturas de
   fotograma por segundo, medido contra el fotograma de ≥ 0,25 s antes), uniendo huecos de ≤ 0,8 s. La forma fina (cadera, codos,
   rodillas…) **no recorta segundos: resta puntos**. Antes de aceptar el sostén pasa 7 filtros contra falsos sostenes (§4.3). La web
   dice lo contrario [W]; el código lo corrige.
2. **Forma como zonas continuas.** Cada regla es una zona `t0→t1` con paso suave (smoothstep), suelo de 0,1, valor «sostenido» = percentil
   85 de la serie, 0,4 s recortados a cada extremo, ≥ 50 % de cobertura o la regla se **abstiene** («unmeasurable»). La nota es
   100 − Σ(puntos de penalización del servidor × factor). Los umbrales finos de los 12 motores están en el paquete (§3); **los puntos de
   penalización, no** (tabla del servidor).
3. **Repetición válida en tres capas:** (a) ciclo por histéresis del ángulo de codo con umbrales relativos a P90/P10 de la serie y
   topes (134°/120°, 110–124°, 135°/120°); (b) **puertas de postura/geometría** que anulan toda la serie (dominada: esqueleto
   volteado, «quimera», geometría de colgado, subida vertical ≥ 35 % del torso; flexión: descenso de cadera, fondo horizontal,
   extensión «de empuje»; fondos: torso vertical, muñecas plantadas bajo los hombros); (c) «estricta» = sin entrar más de ~20 % en la
   zona de ningún fallo gordo, con **perdón de bloqueo** para series rápidas (§4.5).
4. **Invertido y horizontal: todo es geometría 2D de la imagen**, sin reintento a 180° ni modelo especial. Lo nuevo: filtro de
   **esqueleto volteado** en dominadas, `invertido`/`pino` por fotograma en la flexión en pino, **señal de cara abajo** (nariz frente
   a orejas) para separar palanca frontal de trasera, recuperación con un **landmarker nuevo** cuando desaparece la pose, y **máscara
   de silueta** solo en palanca frontal, trasera y planche (§4.6).
5. **Perspectiva: exige el perfil y no lo corrige** (azimut hombros/caderas por z de MediaPipe ≥ 68° para certificar; ≥ 58° para
   siquiera detectar un sostén de palanca frontal, «front touch» o palanca trasera; la bandera humana está exenta). Solo corrige un
   balanceo de 1° a 15° estimado con líneas rectas de la escena (§7).
6. **Progresión** por dificultad de variante, nota ≥ 60 para «pasar el escalón», 90 = maestría, cartas Raw→Perfect solo con escaneos
   certificados, XP y 6 niveles; **no hay «3 × 8 s»** (eso era del blog [W]) (§4.9).
7. **Exportación:** guardar el vídeo original o «con esqueleto» (esqueleto coloreado por gravedad de la regla y por tiempo). **La
   fusión del esqueleto es solo de iOS; el nativo de Android la rechaza**. Sin CSV, sin informe, sin métricas grabadas (§5, §7).
8. **Cautelas:** la pose es MediaPipe «full» con muestreo de **10 fps (sostenes) y 12 fps (repeticiones)** y mediana de 3 como único
   suavizado; no hay validación publicada; los puntos de penalización no se ven; se **sube a la nube** el vídeo, la miniatura y las
   coordenadas.

---

## 1. Identidad

| Dato | Valor |
|---|---|
| Paquete · versión | `com.calilensapp.app` · 1.0.4 (versionCode 15) · minSdk 24 · targetSdk 36 · compileSdk 36 |
| Nombre | «CaliLens: Calisthenics AI»; proyecto de Expo `calilens`, propietario `xavierfqr` (Xavier Facqueur, XF Studio, París [W]) |
| Archivos | `base.apk` 97 807 565 B, SHA-256 `37f15087c3531005107165fe48468ebb4a5aab1dcf7da94461d0eb5079dc050b`; `split_config.arm64_v8a.apk` 57 077 001 B (`537881c1…4f5d`); `split_config.es.apk` 74 137 B (`caededec…8bd056`); `split_config.xxhdpi.apk` 115 759 B (`f2820008…c35f`). Hashes completos en `00_identidad_apks.json` |
| Firma | v2 y v3 (sin v1); certificado de **Play App Signing** (CN=Android, O=Google Inc., RSA-4096, SHA-256 `bc768b2d…885273`) y sello de origen de Play del 2026-10-13 17:01 UTC. El certificado del desarrollador no consta |
| Arquitectura | **Expo SDK 55.0.0** (React Native, deducido 0.83.x; `runtimeVersion` 1.0.2), JS en **Hermes** (`assets/index.android.bundle` 6 699 160 B, bytecode v96, 2 782 módulos Metro, 55 948 cadenas, sin información de depuración), **módulo nativo propio `PoseDetection`** (Kotlin, 42 clases en `expo.modules.posedetection`), dibujo con Skia, ocho DEX. Actualizaciones OTA activas (`expo-updates`); el paquete embebido es del 2026-10-13 16:43 UTC y puede haberse sustituido (sin confirmar) |
| Nativo (arm64) | `libmediapipe_tasks_jni.so` 10,76 MB, `librnskia.so` 11,30 MB, `libhermesvm.so`, `libreactnative.so`, Reanimated/Worklets, FFmpeg n6.0 vía FFmpegKit (`libavcodec` 8,1 MB…; configuración `--enable-version3` sin `--enable-gpl`), Sentry nativo 0.14.0, Fresco (GIF, WebP) |
| Modelo | `assets/pose_landmarker_full.task` 9 398 198 B: MediaPipe **Pose Landmarker «full»** (dos TFLite fechados 2023-04-28): detector `pose_detector` 2 959 046 B (entrada 1×224×224×3 f32; salidas 1×2254×12 y 1×2254×1) y `pose_landmarks_detector` 6 438 874 B (entrada 1×256×256×3 f32; salidas 195 = 39 puntos × 5, presencia, **máscara de segmentación 256×256**, mapa de calor 64×64×39 y 117 = 39 puntos «world» × 3). Local. No se extrajo |
| Permisos | `CAMERA`, `RECORD_AUDIO`, `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `VIBRATE`, `WAKE_LOCK`, `USE_BIOMETRIC`, `READ_MEDIA_VISUAL_USER_SELECTED`, `WRITE_EXTERNAL_STORAGE` (máx. API 32), `SYSTEM_ALERT_WINDOW`, `RECEIVE_BOOT_COMPLETED`, facturación de Play y permisos de insignias de lanzadores. **Sin sensores de movimiento** |
| Componentes | 14 actividades (principal, vídeo a pantalla completa, recorte de imagen, paywall y centro de cliente de RevenueCat, inicio de sesión de Google, facturación), 9 servicios (FCM, Room, CameraX), 5 receptores, 9 proveedores |
| Bibliotecas | Ver `07_bibliotecas_y_versiones.txt`: CameraX 1.4.2, coroutines 1.10.2, Room 2.6.1, Compose 1.9.0, AndroidX actual (Apache-2.0); MediaPipe Tasks y TFLite (Apache-2.0; versión de MediaPipe sin confirmar); Hermes, React Native, Expo, Reanimated (MIT); react-native-skia (MIT); Sentry (MIT); supabase-js (MIT); RevenueCat (SDK MIT, servicio de pago aparte); FFmpeg n6.0/FFmpegKit (LGPL-3.0 en la configuración visible; uso real sin confirmar: 47 clases en `classes4.dex` y ninguna referencia desde el JS ni desde `PoseDetection`) |

## 2. Inventario de funciones, por pantalla

Plan: lo que se sabe de las cadenas de la app (`Unlimited scans (free: 3 per week)`, `Free shows your last 3 scans. Pro keeps every
one of them.`, `Coaching, live analysis and full history`, `Detailed breakdowns, live mode, progress and calendar.`).

| Pantalla (componente) | Qué hace | Plan |
|---|---|---|
| Acceso y bienvenida (`AuthScreen`, `ResetPasswordScreen`, `OnboardingScreen`) | Correo y Google (Supabase), restablecer contraseña, biometría; cuestionario de nivel: flexiones (ninguna / pocas / diez), dominadas (ninguna / una / varias), L-sit (ninguno / tuck / completo) → principiante, intermedio o avanzado | Gratis |
| Inicio (`HomeScreen`, `FirstScanCard`, `LatestScanCard`, `BattleHomeCard`) | Primer escaneo guiado con cuatro figuras de partida (flexión, dominada, L-sit, fondos), último resultado, tarjeta de batalla, camino | Gratis |
| Elegir figura (`FigureSelectionModal`, `ScanChoiceSheet`) | Figura, variante y camino; grabar en vivo o subir un vídeo | Gratis, con cupo |
| Subir vídeo (galería, editor de recorte, compresión) | Editor de recorte de 90 s como máximo y tope de 30 MB (si pesa más, se comprime a 1280 px / 2,5 Mbps; existe un preset de 1920 px / 6 Mbps; si no, se usa el original); el análisis y la nota se calculan **en el teléfono**; luego se sube todo | Gratis: 3 escaneos por semana |
| Cámara en vivo (`LiveCameraScreen`) | Vista previa con esqueleto, avisos de encuadre, cuenta atrás de 3 s con vibración, grabación; al parar, la nota se calcula con los puntos acumulados en vivo (no se relee el vídeo) | **Pro** (`Live analysis with skeleton overlay`) |
| Resultado (`ScoreResultModal`) | Nota 0–100, tiempo de sostén o repeticiones (totales y estrictas), grado de captura y sello «Certified», reglas con motivo y consejo, avisos de calidad (§4.7) | Gratis; desglose completo solo en el primero (Pro: siempre) |
| Detalle e historial (`ScanDetailsModal`, `VideoHistoryScreen`, `ScanThumbnailCard`) | Reproductor con superposición de las reglas, descargar con o sin esqueleto, borrar | Gratis: últimos 3; Pro: todo |
| Estadísticas (`StatsScreen`) | Progreso por figura, récords (mejor sostén, mejor serie), debilidades (reglas más repetidas en los últimos 20 escaneos), «siguiente avance» (variante con mejor nota < 90) | Parte Pro (`stats.pro.*`) |
| Habilidades (`SkillsScreen`, `FigureDetailModal`) | «CaliDex»: carta por variante (Raw → Perfect), guías de cada figura con cómo filmar | Cartas con nota y rango: Pro |
| Calendario (`CalendarScreen`) | Actividad por día | Pro |
| Batallas (`BattleChallengeSheet`, `BattleDetailScreen`, `BattleResultCard`, `BattleStage`, `BattleSubmitSheet`, `BattleReportSheet`, `BattleBlockSheet`, `BattleRulesSheet`, `ContestScoreSheet`) | Reto 1 contra 1 por enlace compartido (único uso de «compartir»); las dos notas se recalculan con las mismas reglas; reproducción lado a lado con la superposición; Elo; reportar y bloquear; el uso exacto de `ContestScoreSheet` sin confirmar | Cupo y Pro: sin confirmar |
| Clasificaciones (RPC `get_xp_leaderboard`, `get_variant_leaderboard`) | Globales y por variante; Pro enseña el puesto exacto y quién va delante | Parte Pro |
| Ajustes (`SettingsScreen`) | Idioma, notificaciones, cuenta, bloqueados, suscripción (RevenueCat) | — |

---

## 3. Catálogo de métricas

Convenciones. «Torso» = distancia hombros-medios → caderas-medias en la imagen (x × relación de aspecto). Salvo que se diga otra cosa, las
longitudes van en **torsos** y las alturas de movimiento en **alturas de fotograma**; los ángulos son **2D en el plano de la imagen** (nunca
3D). Una zona `t0 → t1` significa: la penalización empieza en `t0` y es plena en `t1`; `▲` penaliza cuando el valor **sube** sobre `t0`,
`▼` cuando **baja** por debajo. Un fotograma solo cuenta si `vis_mean` ≥ 0,55 (media de visibilidad de 14 puntos); un punto, si su
visibilidad ≥ 0,5. Todos los valores salen de `02_motor_constantes_umbrales.json`.

### 3.1 Medidas por fotograma (módulos 2143, 2144, 2145)

| Medida | Definición | Notas |
|---|---|---|
| `body_slope` | Ángulo de la recta hombros → tobillos (rodillas si faltan) con la horizontal, plegado a 0–90° | Palancas y planche: criterio de pose; cuerpo vertical = 90° |
| `torso_lean` | Ángulo de la recta hombros → caderas con la **vertical** (0 = erguido, 90 = tumbado) | L-sit ≤ 50° para detectar; planche ≥ 35° |
| `vertical_deviation` | Desvío de la recta muñecas → tobillos respecto de la vertical | Pino: ≤ 45° para detectar |
| `legs_elevation` | (y cadera − y tobillos) / torso; positivo = tobillos más altos | Pino ≥ 0,3 |
| `hip_drop` | (y cadera − y hombros) / torso | L-sit ≥ 0,5; invertido → negativo |
| `wrist_shoulder_gap`, `ear_wrist_gap` | y muñecas − y hombros (u orejas), en torsos | Decide «colgado arriba» (palanca frontal) o «apoyado abajo» (planche); pino: oreja sobre la muñeca |
| `hip_deviation` | Distancia **vertical** de la cadera media a la recta hombros → tobillos, ÷ longitud de esa recta; positivo = cadera por debajo en la imagen | Requiere \|Δx hombro–tobillo\| ≥ 0,05 (cuerpo no vertical). Variante `_extended_leg` con solo el tobillo de la pierna más estirada |
| `ankle_drop` (+ `_visible`, `_strict`, `_extended`) | (y tobillos − y hombros) / torso, con el tobillo más visible, ambos (vis ≥ 0,45) o el de la pierna más estirada | Ángulo corporal de las palancas y L-sit |
| `ankle_drop_line` | Desvío firmado del tobillo respecto de la recta hombro → cadera, en torsos | Piernas altas/bajas en tuck avanzado |
| `leg_line_elevation` | (y cadera − y tobillo) / largo cadera–tobillo = seno del ángulo de las piernas con la horizontal | L-sit |
| `elbow_angle` (+ `_reliable`), `knee_angle`, `hip_angle` (S-H-A), `hip_thigh_angle` (S-H-K), `shoulder_angle` (H-S-E) | Ángulos en el vértice, con x × aspecto y los tres puntos con vis ≥ 0,5. El codo «fiable» exige que brazo y antebrazo midan ≥ 0,25 torsos (descarta el brazo escorzado) | Mediana por lado con `reliableMedian` (§4.2) |
| `shoulder_lean` | (x hombros − x muñecas) / torso, con el signo hacia donde mira el cuerpo | Planche: inclinación hacia delante |
| `shoulder_depression` | \|y hombros − y orejas\| / torso (menor = hombros encogidos) | L-sit, planche |
| `head_offset` | Distancia orejas–hombros / torso | Pino |
| `hip_bow`, `ear_bow` | Desvío de cadera / oreja respecto de la cuerda muñecas → tobillos | Pino: arco (banana) o «pica» |
| `ankle_sep_ratio` / `ankle_sep_torso` | Distancia entre tobillos ÷ ancho de hombros (vis ≥ 0,75) / ÷ torso | Piernas separadas |
| `toe_point_angle` | Ángulo rodilla–tobillo–punta del pie | Pies sin apuntar |
| `motion_norm` | Distancia media recorrida por los 14 puntos respecto del fotograma ≥ 0,25 s anterior (alturas de fotograma, x × aspecto) | Velocidad = `motion_norm` ÷ intervalo; quieto ≤ 0,15 /s |
| `back_arch_ratio_below/above` | Máximo desvío **vertical** de la silueta (máscara de personas) por debajo / por encima de la recta hombros-medios → caderas-medias, ÷ torso, midiendo solo el tramo 20 %–90 % de esa recta; se descarta > 1 y si la recta mide < 10 px | Solo con segmentación (palanca frontal, trasera, planche); §4.2 |
| `view_azimuth` | Ángulo `atan2(\|Δz\|, \|Δx\|)` de la recta de hombros y la de caderas con la **z de MediaPipe**; mediana por línea y mínimo de las dos | ≥ 68° = perfil «fiable» |
| `swing_index` | Desviación típica, sin tendencia lineal, del desvío muñecas → tobillos respecto de la vertical (≥ 8 muestras) | Pino |
| `hold_time` | Último − primer instante de los fotogramas admitidos de la ventana de sostén, redondeado a 0,1 s | §4.3 |

### 3.2 Reglas de los isométricos (cada una con su código `XX_…`; confianza = `vis_mean` del sostén: ≥ 0,75 alta, ≥ 0,6 media, si no baja)

Solo penalizan las reglas de confianza alta o media; las bajas van aparte como «inciertas». Entre `*_HIPS_SAG` y `*_HIPS_PIKE` en
FL/PL/BL/FT solo vale la de mayor confianza.

**Palanca frontal (FL, motor v7)**

| Regla | Mide | Zona | Variantes |
|---|---|---|---|
| `FL_ARM_BEND_VISIBLE` | Codo «fiable» de cada brazo; se juzga el peor | 162° → 145° ▼ (zona estable 150° → 132° si el P10 ≥ 138° y el rango P90–P10 ≤ 28°) | Se omite («unstable_signal») si el rango > 50° o el paso mediano > 39 °/s |
| `FL_HIPS_PIKE` | `hip_deviation` | −0,05 → −0,15 ▼ | Una pierna −0,12 → −0,22; straddle −0,18 → −0,28; tuck y tuck avanzado: no se juzga |
| `FL_HIPS_SAG` | `hip_deviation` | 0,05 → 0,215 ▲ | Una pierna 0,10 → 0,20; straddle 0,12 → 0,22 |
| `FL_KNEES_BENT` | Rodilla (media; una pierna: la más estirada) | 162° → 100° ▼ | Una pierna y straddle 145° → 130°; tuck: no |
| `FL_ANGLE_TOO_HIGH` / `_LOW` | `ankle_drop` sostenido (P15) o, con cadera recta (170°–190°), `body_slope` | tobillos arriba −0,26 → −0,40; cuerpo 12° → 25° ▲ | Una pierna y straddle: −0,6 → −1,05 (alto), +0,6 → +1,05 (bajo) |
| `FL_LEGS_TOO_HIGH` | `ankle_drop_line` (con recorte de asentamiento) | −0,2 → −0,75 ▼ | Solo tuck avanzado |
| `FL_LUMBAR_ARCH` | `back_arch_ratio_below` (silueta) | 0,72 → 0,82 ▲ | Solo piernas rectas |
| `FL_TOES_NOT_POINTED` | `toe_point_angle` | 129° → 108° ▼ | No en tuck ni straddle |
| `FL_UNSTABLE` | Mediana de `motion_norm` | 0,0131 → 0,024 ▲ | — |

**Palanca trasera (BL, v5)**: `BL_ARM_BEND_VISIBLE` 145° → 134°; `BL_HIPS_PIKE` −0,12 → −0,22 y `BL_HIPS_SAG` 0,12 → 0,22 (tuck avanzado: pike
−0,20 → −0,28); `BL_KNEES_BENT` 145° → 130°; `BL_ANGLE_TOO_LOW` («cadera hundida bajo la barra», `hip_below_wrist`) 1,5 → 1,8 torsos ▲ y
`BL_ANGLE_TOO_HIGH` (`torso_drop`) 21° → 38° ▲ cuando no se puede usar la recta; `BL_LEGS_TOO_LOW` (tuck avanzado) 0,65 → 0,95;
`BL_TOES_NOT_POINTED` 139° → 114°; `BL_UNSTABLE` 0,027 → 0,055.

**Planche (PL, v4)**: `PL_ARM_BEND_VISIBLE` 155° → 144° (estable 130° → 118°); `PL_HIPS_PIKE` −0,045 → −0,15 y `PL_HIPS_SAG` 0,05 → 0,15;
`PL_KNEES_BENT` 162° → 147°; `PL_ANGLE_*` cuerpo 12° → 25°; **`PL_SHOULDER_PROTRACTION`**: «apoyo de la parte alta de la espalda»
= `back_arch_ratio_above` 0,44 → 0,32 ▼ (silueta) o, de respaldo, `shoulder_depression` 0,13 → 0,04 ▼; `PL_SHOULDER_LEAN_INSUFFICIENT`
0,15 → 0 ▼ (se omite si P85 − P15 > 0,6); `PL_LEGS_SEPARATED_FULL` 1 → 1,6 anchos de hombros (se omite con azimut > 85°);
`PL_LEGS_TOO_LOW` 0,5 → 0,95 (tuck avanzado); `PL_UNSTABLE`: el peor de la **deriva de los pies** (desviación típica de `ankle_drop`,
0,26 → 0,9 torsos) y su **velocidad** mediana (0,14 → 0,30 torsos/s).

**«Front touch» (FT, v4)**: `FT_ARMS_STRAIGHT` codo 155° → 170° ▲ (aquí se penaliza el brazo **recto**: el toque exige tirar); hips y rodillas
como la palanca frontal; `FT_LUMBAR_ARCH` 0,52 → 0,62; **`FT_TOUCH_NOT_REACHED`**: P10 de |y cadera − y muñecas|/torso (solo con las muñecas
sobre los hombros) > 0,8 → gravedad «major» fija.

**Pino (HS, v7)**: `HS_ARM_BEND_VISIBLE` 167° → 125° (estable 142° → 132°); `HS_HIPS_PIKE` (cadera 165° → 150° ▼ o `hip_bow` 0,05 → 0,12; si
`hip_bow` y `ear_bow` tienen el mismo signo es «pica», si no «banana» = `HS_HIPS_ARCH`); `HS_KNEES_BENT` 168° → 150°;
`HS_SHOULDER_ANGLE_CLOSED` 159° → 130°; `HS_NOT_VERTICAL` 10° → 43°; `HS_HEAD_POSITION` 0,30 → 0,55; `HS_LEGS_SEPARATED` 0,25 → 0,5 torsos
(se omite si el tobillo lejano está ocluido en perfil); `HS_UNSTABLE` `swing_index` 2,1 → 4,5; `HS_TOES_NOT_POINTED` 137° → 112°.

**Bandera humana (HF, v6)**: `HF_ARM_BEND_VISIBLE` 140° → 120° (etiqueta «brazo de arriba / de abajo» según la altura mediana de la muñeca);
`HF_KNEES_BENT` 168° → 150° (no en tuck); `HF_ANGLE_LOW` −2,4° → −20° ▼ y `HF_ANGLE_HIGH` 9,5° → 28° ▲ (pendiente **firmada** del cuerpo:
pies por debajo o por encima de la línea del hombro); `HF_HIPS_PIKE/SAG` 0,077 → 0,18 (solo completa); `HF_UNSTABLE` 0,0116 → 0,03;
`HF_LEGS_SEPARATED` 1 → 2 anchos (solo completa).

**L-sit (LS, v5)**: `LS_ARM_BEND_VISIBLE` 155° → 145° (colgado: 120° → 90°); `LS_KNEES_BENT` 170° → 132° (una pierna 145° → 127°; tuck
avanzado 90° → 70°); `LS_COMPRESSION_LOW` ángulo de cadera 120° → 148° ▲ (V-sit 95° → 115°; tuck desde el muslo 85° → 115°; tuck
avanzado 100° → 130°); `LS_SHOULDER_DEPRESSION` 0,08 → 0,02 ▼ (completa 0,315 → 0,05; no se mide colgado); `LS_TORSO_LEAN` 20° → 38°;
`LS_LEGS_TOO_LOW` seno −0,35 → −0,55 (≈ 20° → 33° bajo la horizontal); `LS_LEGS_TOO_HIGH` +0,105 → +0,305 (≈ 6° → 18° sobre
ella); `LS_TOES_NOT_POINTED` 130° → 105°; `LS_UNSTABLE` 0,0145 → 0,03.

### 3.3 Reglas de las repeticiones (cada fallo se mide **por repetición** y se agrega con el factor de serie, §4.5)

| Motor | Regla → medida (zona) |
|---|---|
| Fondos (`dips`, v1) | `DIP_INSUFFICIENT_DEPTH` codo mínimo 100° → 120° ▲; `DIP_INCOMPLETE_LOCKOUT` codo máximo 160° → 140° ▼ (≥ 2 reps y ≥ 25 %); `DIP_SHOULDER_SHRUG` distancia oreja–hombro arriba ÷ su P90 = 0,55 → 0,35 ▼; `DIP_KIPPING` rango del ángulo cadera–muslo 45° → 65° (≥ 2 reps); `DIP_BODY_SWING` rango P10–P90 del péndulo muñecas → tobillos 73° → 219° (≥ 2 reps; la zona pasa de 180°, el máximo posible de ese ángulo, así que el factor pleno es inalcanzable: sin confirmar si es un descuido) |
| Dominadas (`pull_up`, v7) | `PU_PARTIAL_ROM` extensión 146° → 126° ▼ o flexión mínima 115° → 135° ▲; `PU_CHIN_NOT_OVER` margen oreja–muñecas 0,1 → 0,3 torsos ▲; `PU_KIPPING` varianza de cadera 15° → 35° o balanceo de cadera 0,25 → 0,62 (de frente: acortamiento del muslo 0,33 → 0,65); `PU_BODY_SWING` 11° → 32°; `PU_KNEES_BENT` 168° → 150°; `PU_LEGS_SEPARATED` rodillas ÷ caderas 1,3 → 3; `PU_ASYM_PULL` 20° → 40° (**regla definida pero sin alimentar en este paquete**) |
| Flexiones (`push_up`, v6) | `PUSH_INSUFFICIENT_DEPTH` codo mínimo 95° → 125° ▲; `PUSH_INCOMPLETE_LOCKOUT` 160° → 140° ▼; `PUSH_HIP_SAG` desvío sostenido de cadera 0,10 → 0,16 (solo con piernas visibles y sin rodillas en el suelo); `PUSH_HIP_PIKE` −0,12 → −0,20 o, sin piernas visibles o de rodillas, ángulo muslo-cadera 162° → 155° ▼; `PUSH_HEAD_MISALIGN` 0,12 → 0,20; `PUSH_KNEES_ON_FLOOR` rodilla < 150° en el fondo; `PUSH_WORMING` 0,04 → 0,10 (≥ 2 reps) |
| Muscle-up (`muscle_up`, v5) | `MU_NO_TRANSITION` = intentos fallidos ÷ (reps + fallidos); `MU_NO_LOCKOUT` codo arriba 165° → 145° ▼; `MU_PARTIAL_ROM_BOTTOM` codo abajo 160° → 135° ▼; `MU_KIPPING` varianza de rodilla 22° → 35° o rodilla mínima 120° → 90°; `MU_BODY_SWING` balanceo de cadera 0,8 → 1,2 torsos, o ≥ 4 cambios de sentido del péndulo (4 → 6, amplitud 0,4 → 0,6); `MU_CHICKEN_WING` disparidad de hombros 0,18 → 0,36 torsos; `MU_LEGS_SEPARATED` 1,3 → 3 |
| Flexión en pino (`handstand_push_up`, v12) | `HSPU_INSUFFICIENT_DEPTH` codo mínimo 95° → 125° (exento si la cabeza queda a ≤ 0,25 torsos de la muñeca); `HSPU_INCOMPLETE_LOCKOUT` 160° → 140°; `HSPU_HIP_PIKE` cadera mínima 158° → 138° ▼; `HSPU_KIPPING` desviación de rodilla 12° → 30°; `HSPU_NOT_VERTICAL` 8° → 25° en el bloqueo; `HSPU_LUMBAR_ARCH` 0,05 → 0,12; `HSPU_BODY_SWING` 4° → 12°; `HSPU_LEGS_APART` 0,25 → 0,5 torsos |

### 3.4 Cuándo se muestran y cuándo se abstiene

- **Salida:** `score` 0–100 (1 decimal), `hold_time` o `rep_count`/`strict_rep_count`, `applied_rules` (con `penalty_applied`, `severity_factor`, métrica, valor y umbral), `uncertain_rules` (confianza baja), `skipped_rules` con motivo (`unmeasurable` = < 4 muestras o cobertura < 50 %; `unstable_signal`; `view`), `capture_grade` (`verified_eligible`, `counted_degraded` con motivos, `unscorable`) y `engine_version`.
- **No puntuable** (`is_unscorable`): sin métricas o `hold_detected = false`. Motivos: `no_data`, `no_hold`, `no_rep` (el mismo caso en motores dinámicos), `body_out_of_frame` (cobertura < 0,5), `legs_not_visible` (colgado horizontal con ≥ 50 % de fotogramas sin piernas medibles), `multiple_bodies` (la «quimera» de dominadas), `too_dark` (luma media < 0,15 con `no_hold`, `no_data` o `body_out_of_frame`).
- **Avisos** (texto de la app): `camera_angle_oblique` («demasiado oblicuo… este resultado es orientativo y no cuenta en tus estadísticas»), `view_front_sagittal` (de frente no se puede juzgar el balanceo: «gira la cámara 45°»), `compressed_source` (fuente < 640 px o < 1 Mbps) y `low_visibility` (`vis_mean` < 0,65).
- **Sello «Certified»:** `capture_grade = verified_eligible` (≥ 8 fotogramas buenos, los 6 pares articulares visibles en ≥ 80 %, vista fiable) **y** ninguna regla omitida **y** fuente no comprimida **y** versión del motor ≥ mínimo por figura (palanca trasera 5, fondos 1, palanca frontal 5, front touch 4, pino 6, flexión en pino 11, bandera 6, L-sit 5, muscle-up 5, planche 4, dominada 7, flexión 5; los motores del paquete son 5, 1, 7, 4, 7, 12, 6, 5, 5, 4, 7, 6).
- **Figura equivocada:** si el resultado es `no_hold`/`no_rep`, se vuelve a puntuar el mismo vídeo con los otros motores (variante por defecto de cada figura, con su fps) y se propone la mejor si saca ≥ 60 y gana por ≥ 10 puntos.

---

## 4. Algoritmos

### 4.1 Captura y pose (lo ya visto, en una línea cada cosa)

- **Muestreo.** El módulo nativo pide a `MediaMetadataRetriever` el fotograma más cercano a cada instante k/fps (**10 fps** para sostenes,
  **12 fps** para repeticiones) y se lo da a MediaPipe en modo VIDEO; tope de **900 fotogramas** (re-muestreo uniforme). El instante
  guardado es el **pedido**, no el PTS real; no hay tratamiento de fps variable ni de cámara lenta (la web pide no usarlos [W]).
  Resolución temporal: 100 ms en sostenes, 83 ms en repeticiones.
- **Pose.** Pose Landmarker «full» local, hasta 3 personas (confianzas 0,45 / 0,45 / 0,5). Elige la persona de mayor caja con memoria de
  1 s y descarte de saltos (> 0,25 de fotograma en < 0,6 s). **Novedad:** si un fotograma sale sin pose, crea **otro landmarker de una
  sola pose (sin estado de seguimiento)** y repite ese fotograma; así se recupera una persona que el seguimiento de región perdió
  (cuerpo que se invierte o se tumba de golpe). El resultado nativo cuenta esos casos (`framesRecoveredSolo`), junto a `framesExtracted`,
  `framesDecoded` y `framesWithPose`. KPKN ya elige atleta y marca varias personas; la recuperación es nueva.
- **Salida.** 25 puntos por fotograma (los 14 «agregados» más nariz, talones, puntas, meñiques, índices y pulgares) con x, y (a 1e-4), z y
  visibilidad (a 1e-3); media de luminancia de una copia en grises de 64 px; ángulos 2D calculados con x × aspecto; `world` si existe.
  Segmentación de personas **solo** para palanca frontal, trasera y planche (§4.2).
- **Sin escala, sin calibración, sin perspectiva.** Todo está en fracciones de fotograma o de torso; no hay metros, homografía, plano ni
  disco. Pose local con MediaPipe y puntos «world»: ya visto (My Jump Lab, Metric); aquí no se usan los «world» para las reglas.

### 4.2 Medidas robustas y calidad de captura

- **Lados ocluidos.** Para cada pareja izquierda/derecha (tobillos, rodillas, caderas, hombros, codos, muñecas, orejas) cuenta en
  cuántos fotogramas buenos se ve cada lado. Un lado está **ocluido** si se ve en < 50 % de los fotogramas del otro; y, si ambos se ven
  ≥ 75 %, el que tiene la desviación típica de posición ≥ 3 veces mayor se considera **alucinado**. Los puntos de un lado ocluido
  se descartan en todas las métricas (cuerpo de perfil: el brazo y la pierna lejanos no cuentan).
- **Mediana fiable por pareja.** Un ángulo izquierdo/derecho se descarta si el lado tiene < 50 % de las muestras del otro o si es ≥ 3
  veces más ruidoso; el resto, media recortada del 10 %.
- **Azimut de la vista.** Para el hombro y la cadera, `atan2(|Δz|, |Δx|)` con la z que da MediaPipe a cada punto (ambos con vis ≥ 0,5);
  mediana de cada recta y mínimo de las dos. 90° = perfil puro. KPKN ya calcula algo equivalente con los puntos «world» (§4.1 de
  `VIDEO_TECHNIQUE_V1`, ψ_w); aquí se usa la z de imagen.
- **Clasificador de vista para repeticiones.** Con la anchura de hombros ÷ torso y ese azimut: `front` (azimut < 51° y anchura ≥ 0,5),
  `uncertain` (< 60° y ≥ 0,45), `side` (anchura ≤ 0,22), si no `oblique`. Lo sagital se juzga en `side` y `oblique`; en `front` y
  `uncertain` se abstiene de balanceo y cadera.
- **Silueta (solo FL, BL y PL).** A partir de la máscara de personas (> 0,5), toma la recta hombros-medios → caderas-medias y, para los
  píxeles que se proyectan entre el 20 % y el 90 % de ella, mide cuánto se aleja la silueta **en vertical** por encima y por debajo de esa
  recta, dividido por su largo. Es el proxy del **arco lumbar** (por debajo) y de la **protracción** de la planche (por encima). No
  usa columna ni escápulas, solo contorno; KPKN hoy no usa la máscara que el modelo ya devuelve.
- **Balanceo de la cámara (roll).** Sobre 5 fotogramas (10, 30, 50, 70 y 90 % del vídeo) reducidos a 960 px en grises: desenfoque
  gaussiano (σ 1,4), Sobel, umbral en el percentil 96 de la magnitud, supresión de no máximos (≥ 100 puntos de borde) y Hough con pasos de
  0,5° y 2 px, solo rectas a ≤ 20° de los ejes, picos con ≥ máx(100; 15 % de la dimensión menor) votos. La desviación de cada fotograma
  es la mediana ponderada (votos²) de sus picos. Se exigen ≥ 12 picos y ≥ 4 fotogramas con estimación; si hay ≥ 5 picos casi verticales
  en ≥ 4 fotogramas se usan **solo esos** (más fiables); se descarta la estimación si la mediana de las discrepancias entre fotogramas
  supera 1,5° o si |roll| > 15°. Solo se aplica de 1° a 15°, girando todos los puntos alrededor del centro. No usa el acelerómetro.
- **Grado de captura.** `verified_eligible` si hay ≥ 8 fotogramas buenos, los seis pares articulares se ven (lado con mayor visibilidad
  ≥ 0,5) en ≥ 80 % de ellos y la vista es fiable (azimut ≥ 68°, salvo la bandera humana); si no, `counted_degraded` con motivos
  (`body_out_of_frame`, `view_too_oblique`, `too_few_clean_frames`, `compressed_source`, `rules_not_evaluable`).

### 4.3 Isométricos: detección y cronometraje (lo más nuevo)

**Qué es el tiempo que se muestra.** `hold_time` = (instante del último fotograma admitido − instante del primero) de la **ventana
estable más larga** que sobrevive a los filtros, redondeado a 0,1 s. No hay suma de «segundos válidos», ni mejor racha, ni número de
intentos: **un vídeo = una ventana**. El cronómetro arranca en el **primer fotograma ya estable** (no cuando se alcanza la postura) y
termina en el último; la entrada y la salida solo se recortan para **puntuar** (0,4 s por extremo), no para cronometrar.

**Pasos** (módulos 2145 y 2146):

1. **Admisión.** Fotograma con `vis_mean` ≥ 0,55, ambos hombros y tobillos (o rodillas si faltan). Para decidir si hay sostén, **solo
   palanca frontal, trasera, planche y L-sit corrigen la relación de aspecto** (x × ancho/alto); pino, bandera y «front touch» usan
   las coordenadas normalizadas tal cual, así que a 16:9 sus ángulos de detección salen deformados (las reglas finas sí corrigen).
2. **Pose de la habilidad (criterio grueso).** Un fotograma «está en la pose» si cumple todos los umbrales de su habilidad y variante:

   | Habilidad | Criterios (todos) |
   |---|---|
   | Palanca frontal (completa, straddle) | `body_slope` ≤ 60°; codo medio ≥ 120°; \|muñecas − hombros\| ≥ 0,2 torsos **y muñecas por encima de los hombros**; cadera (S-H-A) ≥ 100°; rodilla media ≥ 140° (si se mide) |
   | Palanca frontal (tuck avanzado / tuck / una pierna) | Igual, con cadera ≥ 60° / ≥ 30° / ≥ 90° y sin exigir rodilla |
   | «Front touch» | Como la frontal sin límite de codo; cadera desde el muslo si falta; duración mínima 1 s |
   | Planche | `body_slope` ≤ 60°; codo ≥ 80°; \|muñecas − hombros\| ≥ 0,2 y **muñecas por debajo**; `torso_lean` ≥ 35°; cadera según variante |
   | Palanca trasera | `body_slope` ≤ 45°; resto como la frontal, y el movimiento se mide solo con puntos visibles |
   | Pino | `vertical_deviation` ≤ 45°; `legs_elevation` ≥ 0,3; (y muñeca − y oreja)/torso ≥ 0,22 |
   | L-sit | `torso_lean` ≤ 50°, cadera ≤ 150°, `hip_drop` ≥ 0,5, y **o** muñecas por debajo de los hombros **o** (colgado) muñecas por encima ≥ 0,5 torsos |
   | Bandera humana | `body_slope` ≤ 70° |

   Una medida no disponible falla el criterio, salvo rodilla, codo máximo, brecha oreja–muñeca y «muñecas arriba/abajo», que se saltan
   (si no se ven las piernas el sostén puede detectarse y luego las reglas se abstienen).
3. **Quietud.** El fotograma debe tener velocidad ≤ **0,15 alturas de fotograma por segundo**: `motion_norm` ÷ (t − t_ref), con t_ref
   el último fotograma al menos 0,25 s anterior. Si ese fotograma de referencia no estaba en la pose se busca otro entre 0,25 s y 0,8 s
   atrás y se recalcula el movimiento; sin referencia (principio del vídeo) basta `motion_norm` ≤ 0,05. En la palanca trasera, si falla,
   se repite solo con los puntos visibles (los miembros ocluidos «tiemblan»). **Umbral absoluto en fracción de fotograma, no relativo al
   máximo del clip y no normalizado por la distancia al atleta**; AscentAI ya medía inmovilidad pero con umbral relativo y sin usarla.
4. **Unión de huecos y duración mínima.** Un tramo no válido de ≤ **0,8 s** entre dos tramos válidos se rellena (no al principio ni al
   final). Se conservan las rachas de ≥ **2 s** (1 s en «front touch» y en la detección agnóstica) y se elige la **más larga**. En las
   variantes de piernas rectas, si la mediana del ángulo del torso de la primera mitad difiere > 30° de la de la segunda mitad (gira
   despacio), la ventana se descarta.
5. **Coherencia del torso.** Entre los fotogramas rellenados (los que no cumplían la pose) se descartan los cuyo largo de torso queda
   fuera de 0,7–1,3 × la mediana: son poses saltadas a otra persona o esqueletos mal puestos. Después se recalcula el movimiento.
6. **Siete filtros contra falsos sostenes** (seis en `filterHoldFrames`, módulo 2145, y uno en el motor de la palanca trasera); si
   salta uno, el vídeo queda como `no_hold`:
   (a) **vista**: palanca frontal, «front touch» y trasera exigen azimut mediano ≥ **58°**;
   (b) **ejercicio apoyado o dinámico** (palanca trasera): mediana de «muñecas − hombros» > −0,2 torsos con velocidad P80 < 0,06 /s
   (pose estática con las manos bajas = ejercicio de apoyo) o deriva lenta del ángulo del cuerpo > 15° con piernas rectas (balanceo
   tipo «skin the cat»);
   (c) **bandera sin barra** (bandera humana): mediana de |muñecas − hombros|/torso > 0,35 (cuelga de una barra, no de un poste);
   (d) **rotación lenta** en la palanca frontal de piernas rectas (deriva del ángulo del cuerpo > 15°);
   (e) **cara arriba o cara abajo**: mediana de (y nariz − y orejas)/torso; la frontal se rechaza si es > 0 (cara abajo) y la trasera
   si es < 0 (cara arriba);
   (f) **pino**: mediana de (y muñeca − y oreja)/torso > 1 (cabeza lejos de las manos: no es un pino apoyado);
   (g) ya en el `analyze` de la palanca trasera (módulo 2190), se descarta además si la oreja queda > 0,12 torsos por encima de la
   muñeca (`ear_drop` < −0,12) o la cadera > 0,9 torsos por encima (`hip_below_wrist` < −0,9).
7. **Colgado horizontal sin piernas.** Si hubo un colgado horizontal (torso ≥ 40° y |muñecas − hombros| ≥ 0,2 torsos) con ≥ 50 % de
   fotogramas sin piernas medibles, el motivo de no puntuación es `legs_not_visible`, no `no_hold`.
8. **Puntuación sobre el interior.** Si la ventana tiene ≥ 4 fotogramas y ≥ 1 s, las reglas se evalúan sin los primeros y últimos 0,4 s.

**Consecuencias para KPKN.** (1) El cronómetro de CaliLens es un **segmentador de estado «quieto + postura gruesa»**, simple y
reproducible; la forma fina es un **segundo eje** (puntos), no el reloj. (2) Los filtros (a)–(g) son barreras de bajo coste que evitan
puntuar movimientos ajenos; todos son replicables con la traza de KPKN. (3) No hay evidencia de validación frente a jueces ni frente a
cronómetro.

### 4.4 Modelo de forma por zonas (módulos 2152, 2162)

- **Factor de una muestra.** `x` es el valor medido; con `t = clamp((x − t0)/(t1 − t0), 0, 1)` (invertido si la zona es `▼`), el factor
  es `t²(3 − 2t)` (smoothstep); si es > 0 se sube a un **suelo de 0,1** (cualquier entrada en la zona cuesta al menos 10 % de la
  penalización).
- **Valor «sostenido» de una regla.** Serie por fotograma (con la mediana de 3 para quitar picos) → recorte de rampas de entrada y
  salida (desde cada extremo, mientras la serie mejore > 5 % del ancho de la zona por paso y haya mejorado en total ≥ 50 % de ese ancho;
  hasta el 30 % de las muestras por lado) o, en las reglas de piernas, **recorte hasta que la serie se asienta** (una ventana de ≥ 3
  muestras con rango ≤ 15 % del ancho de la zona; hasta el 60 % por lado) → factor de cada muestra → **percentil 85**.
  Con < 4 muestras se usa el valor del sostén entero; con cobertura < 50 %, la regla se **abstiene** (`unmeasurable`). Un brazo o una
  rodilla con rango P90–P10 > 50° o paso mediano > 39 °/s se omite como `unstable_signal`.
- **Gravedad y confianza.** Factor ≥ 0,75 «major», ≥ 0,4 «moderate», si no «minor». Confianza por `vis_mean` del sostén (§3.2).
- **Nota.** `100 − min(100, Σ penalty_points_servidor × factor)`; sin factor numérico, 0,5 / 0,75 / 1 según la gravedad. Entre sag y
  pike de la misma figura solo penaliza la de mayor confianza.
- **Intervalos de fallo (para dibujar).** Por regla de confianza alta o media, una bandera por fotograma contra el umbral; los
  huecos de ≤ 0,4 s se unen y solo se guardan intervalos de ≥ 0,4 s, por lado (`left`, `right`, `both`). En repeticiones, el tramo de
  cada repetición con fallo.
- **Qué hay de nuevo respecto a KPKN.** La mecánica de «valor sostenido con recorte y abstención por cobertura» es reutilizable; la
  nota global 0–100 con puntos escondidos en el servidor, no (política de KPKN: hechos con cifra y «≈»).

### 4.5 Repetición válida, por motor (módulos 2171, 2175, 2197, 2199, 2201, 2203)

**Capa común.** Solo fotogramas con `vis_mean` ≥ 0,55; filtro de mediana de 3 que no cruza huecos de tiempo > 1,5 × la mediana;
fotogramas con torso/cuerpo ±20 % fuera de la mediana (otra persona) descartados; extremos de cada repetición refinados con una
parábola de 3 puntos (tope de 8°); un fallo solo cuenta si se **juzga ≥ 80 %** de las repeticiones; el factor de la serie es
`min(1, media(factor de las faltosas) × √(faltosas / juzgadas))`; muchas reglas exigen ≥ 2 repeticiones faltosas (o ≥ 25–30 %).
**Perdón de bloqueo:** si la mediana de duración de las repeticiones es < 1,4 s y ≥ 15 % llegan al umbral de bloqueo, se acepta cualquier
repetición que llegue a `umbral − 12°`. **«Estricta»**: ningún fallo de sus reglas de puerta entra más allá del `ENTRY_FLOOR` (factor ≤ 0,1,
es decir, hasta ~20 % del ancho de la zona).

| Motor | Qué cuenta como repetición | Puertas que anulan la serie o la repetición | Estricta |
|---|---|---|---|
| **Fondos** | Codo suavizado; rango P90–P10 ≥ 30°; alto = mín(134°, P90 − 15°), bajo = mín(120°, alto − 15°); baja por debajo de «bajo» y vuelve por encima de «alto»; recorrido ≥ 30° y el hombro sube/baja ≥ 0,1 torsos. La última (sin vuelta) solo si llegó a ≤ 100° | **Postura** (mediana en las repeticiones): torso ≤ 35° de la vertical; \|muñeca − cadera\| ≤ 0,8 torsos; muñecas **por debajo** de los hombros; muñecas **plantadas** (deriva ≤ 0,5 torsos si se ven en ≥ 70 % de los fotogramas); cadera a ≤ 0,5 torsos de las manos en x. Si falla: no es un fondo (`no_rep`) | Profundidad ≤ 100° y (extensión ≥ 160° o perdonada) |
| **Dominadas** | Alternancia colgado/tirón del codo medio. Con P90 ≥ 110°: tirón = máx(110°, P10 + 15°), colgado = mín(124°, máx(tirón + 12°, P75 − 8°)); si no, 25 % y 55 % del rango. Rango ≥ 25°; recorrido ≥ 20° y ≥ 0,25 s; flexión ≤ 125°; ≥ 35 % de la repetición con las muñecas sobre los hombros; la subida de la cabeza ≥ máx(0,12; 25 % de la mediana) | **Esqueleto volteado** (caderas sobre hombros y tobillos sobre muñecas): se quitan esas rachas; **quimera** (> 50 % de los fotogramas con caderas sobre hombros) → `multiple_bodies`; **geometría de colgado** (oreja a > 0,8 torsos sobre las muñecas, o manos a alturas distintas > 0,6 torsos); **piernas fijas** (rango de tobillos < 0,12 torsos); **subida vertical** del hombro < 0,35 torsos en todas las repeticiones; **fantasma de suelta**: última con flexión > mediana + 25° | Margen oreja–muñecas ≤ ~0,14 torsos, extensión ≥ ~142° (o subida de cabeza ≥ 0,65 torsos) y ningún indicio de patada |
| **Flexiones** | Como fondos, con rango P90–P10 ≥ 45°; recorrido ≥ 30° | Cadera baja ≥ 7,5 % del cuerpo; **fondo horizontal** (pendiente ≤ 45°); **extensión «de empuje»** (muñecas no más de 0,12 torsos sobre los hombros; el hombro recorre ≤ 0,55 cuerpos en el descenso); vista oblicua (anchura de hombro–tobillo < 0,5) con rango < 45° → sin repeticiones; rodillas (< 150° en el fondo) | Flexión ≤ 100°, extensión ≥ 160° (o perdonada), no de rodillas (salvo variante «knee») y cadera entre −0,12 y +0,10 de la recta |
| **Muscle-up** | Señal `lift` = (y muñecas − y hombros)/(torso en y): colgado ≤ −0,5; transición cuando llega a ≥ 0,4 (hombros sobre la barra) y vuelve a colgado; fuera 0,3–8 s. Un tirón que llega a la barra sin transición (≥ 0,8 s con `lift` ≥ 0) es un **intento fallido** | Fotogramas con torso en y < 0,5 × mediana (escorzo) y saltos de hombro > 0,25 en < 0,6 s se descartan; se anula si la muñeca «baja» > 0,85 torsos (no hay barra fija) o la cadera se desplaza > 3 torsos | Transición, codo arriba ≥ 165°, abajo ≥ 160°, varianza de rodilla ≤ 22°, rodilla ≥ 120°, balanceo ≤ 0,8 y disparidad de hombros sana |
| **Flexión en pino** | Codo suavizado; extendido ≥ 135°, flexionado ≤ 120° (o 130° en el segundo intento); recorrido ≥ 30° | **Prueba de bloqueo**: debe existir ≥ 0,35 s con codo ≥ 150° **sin estar** claramente no invertido; **fondo invertido** (hombros bajo las caderas ≥ 0,5 torsos); **cierre en pino** (tobillos sobre caderas en los 5 fotogramas siguientes) | Profundidad, bloqueo y sin patada, con la cadera mínima > ~145° (pica que no llegue a «major») |

### 4.6 Cuerpos invertidos y horizontales

- **Horizontales (palancas, planche, bandera, L-sit).** No se gira la imagen ni se normaliza la orientación. Cada medida se define
  **respecto de las rectas del propio cuerpo** (la cadera frente a la recta hombro–tobillo, el ángulo del cuerpo frente a la horizontal
  plegado a 0–90°, tobillos frente a la línea de los hombros) y con **lados ocluidos descartados**; para una pierna se usa solo la más
  estirada. La dirección «cara arriba / cara abajo» no la dan los puntos del cuerpo sino la **nariz frente a las orejas** (filtro (e)) y
  la posición de las muñecas respecto de los hombros (arriba = palanca frontal, abajo = planche).
- **Invertidos (pino, flexión en pino).** `legs_elevation` ≥ 0,3, brecha oreja–muñeca, `vertical_deviation` (recta muñecas → tobillos
  contra la vertical, hasta 45° para detectar) y, en la flexión en pino, `invertido` (hombros bajo las caderas ≥ 0,5 torsos) y
  `pino` (además, tobillos sobre caderas) **por fotograma**. Arco y pica se distinguen por el signo conjunto de `hip_bow` y `ear_bow`.
  El contacto de la cabeza con el suelo se mide con (y muñecas − y orejas)/torso ≤ 0,25.
- **Esqueleto «volteado».** El modelo a veces coloca el esqueleto cabeza abajo en un colgado; las dominadas detectan ese caso
  (caderas sobre hombros **y** tobillos sobre muñecas, en una racha) y la borran; si más de la mitad de los fotogramas (≥ 20) tiene las
  caderas sobre los hombros, descartan todo como «quimera» (`multiple_bodies`).
- **No hay reintento girando el fotograma 180°** ni modelo para cuerpos invertidos. El modelo de pose es el mismo; lo único especial
  es el landmarker de recuperación (§4.1). Cuánto falla MediaPipe boca abajo: **no se mide** en el código.
- **Supone la gravedad = abajo de la imagen**; no lee el acelerómetro: un vídeo girado 90° desde la galería no se corrige (solo el
  balanceo de 1° a 15°).

### 4.7 fps, cámara lenta y controles de calidad

- **fps:** 10 en sostenes, 12 en repeticiones, 900 fotogramas como máximo; sin PTS, sin VFR, sin cámara lenta.
- **Fuente:** `compressed_source` si el lado corto < 640 px o la tasa < 1 Mbps (tamaño ÷ duración); degrada el sello.
- **Luz:** `too_dark` si la luma media < 0,15 (solo se usa para explicar un `no_hold`/`no_data`).
- **Varias personas:** hasta 3 poses y selección por caja y continuidad; en dominadas la «quimera» es `multiple_bodies`. En el resto no
  hay veto.
- **Encuadre:** `body_out_of_frame` si algún par articular se ve en < 80 % (certificado) o < 50 % (no puntuable). La flexión decide
  con «piernas visibles» (tobillo dentro de cuadro en ≥ 50 % de los fotogramas) si juzga la cadera, y las dominadas miran los tobillos
  para el balanceo y la geometría de colgado.
- **Resumen de abstenciones:** cada regla que no puede medirse se anota con su motivo; si queda alguna, no hay «Certified».

### 4.8 Modo en vivo

CameraX (vista previa + análisis con «solo el último fotograma» + grabación HD a 2,5 Mbps en 720×1280), MediaPipe en modo LIVE_STREAM
(1 persona, mismas confianzas) y un evento `onFrameProcessed` con los puntos de cada fotograma. Antes de grabar, cada 200 ms se guarda la visibilidad
media en una ventana de 10 muestras (2 s); el aviso «listo» exige ≥ 5 puntos, tobillos y hombros > 0,3 (si no: «aléjate / retrocede»),
media ≥ 0,55 y, en estáticos, que `isLiveHoldPose` dé «en la pose» en ≥ 3 de las últimas 5 comprobaciones («ponte en la posición»).
Cuenta atrás de 3 s con vibración. Al parar se convierte lo acumulado al mismo JSON (marcado `live_recorded`, con plataforma y cámara
frontal) y se puntúa con el mismo motor. El esqueleto del vídeo se desplaza **0,15 s** (retardo de pose) al dibujar. KPKN lo tiene en
la fase 2 del contador en vivo.

### 4.9 Progresión y juego

- **Catálogo.** Figura → variantes con `difficulty_level` (orden de dificultad) y `base_xp`; hitos de repeticiones por variante
  (`variant_milestones`). Las variantes (tuck, tuck avanzado, una pierna, straddle, completa, V-sit, rodillas…) se detectan **por el
  texto del nombre en minúsculas**; el catálogo vive en el servidor. Nombres que el código conoce: Tuck, Advanced Tuck, One Leg, Straddle,
  Full, Full Touch, V-Sit, Knee, Standard, Bar y Freestanding; la variante por defecto para re-puntuar con otra figura es Full (palanca
  trasera, frontal, bandera, L-sit), Full Touch, Freestanding (pino), Tuck (planche), Bar (muscle-up) o Standard (el resto).
- **Camino.** El cuestionario inicial produce un nivel. Los **escalones base** son flexión (de rodillas o estándar), dominada estándar
  (si ya hace alguna) y L-sit (tuck o completo); después, las variantes de la figura objetivo ordenadas por dificultad. Un escalón está
  **pasado** si existe un escaneo puntuado en esa variante o en otra **más difícil** de la misma figura; el primero no pasado es
  «aquí» y el siguiente, «siguiente».
- **Nota de paso y maestría.** 60 = «Propre» (escalón), **90 = maestría** (`MASTERY_THRESHOLD`); «siguiente avance» = variante con mejor
  nota < 90 y menor hueco. Cartas por mejor escaneo **certificado**: Brut (0) · Propre (60) · Solide (80) · Maîtrise (90) · Parfait (100).
- **XP.** Por variante, `round(base_xp × nota/100)` del mejor escaneo; hitos de repeticiones **estrictas**: `round(xp_hito × nota/100)`.
  Niveles: Rookie 0–384, Apprentice 385–934, Confirmed 935–2 254, Elite 2 255–4 459, Master 4 460–5 229, Legend ≥ 5 230.
- **Debilidades.** Las reglas disparadas con más frecuencia en los últimos 20 escaneos (máx. 5, con su gravedad máxima), y cuáles siguen
  activas en el último.
- **Batallas.** El servidor guarda los puntos; cada dispositivo **recalcula la nota** del rival con el motor actual (por eso hay
  versiones mínimas de motor); Elo y clasificaciones por XP y variante en el servidor.

---

## 5. Exportación y compartir

- **Guardar en la galería** (`ScanDetailsModal`): dos botones, «sin esqueleto» (baja el vídeo original de la nube a un archivo temporal y
  lo guarda con `MediaLibrary`, tras pedir el permiso) y «con esqueleto» (baja el vídeo, construye el **plan de superposición** y llama a
  `burnPoseOverlay(videoPath, overlayPlanJson)`; después lo guarda). **El nativo de Android responde `UNSUPPORTED`: «Skeleton burn-in is
  iOS-only for now»**. El código JS que ofrece los botones no comprueba la plataforma; si la interfaz de Android oculta la opción, es
  código que no he visto (sin confirmar).
- **Plan de superposición** (`buildOverlayPlan`, módulo 2141): lista de fotogramas `{t, segments[], points[]}` con coordenadas
  normalizadas 0–1 (≤ 600 fotogramas, ≤ 64 formas por fotograma, grosor de hueso 0,0042 y radio de punto 0,0125 del fotograma). Dibuja 14
  puntos y 16 huesos; cada hueso y punto se **colorea por la gravedad de las reglas activas en ese instante** (gris sin datos; bueno;
  menor; moderado; mayor), con `connectionsForRule` (qué huesos toca cada regla) y los intervalos de fallo de §4.4; los puntos inciertos
  (visibilidad 0,25–0,5) van con transparencia (0,35 y 0,25). Opciones: recortar al rango del sostén, espejo si el vídeo viene de cámara
  frontal, giro por balanceo y retardo de 0,15 s para grabaciones en vivo.
- **Qué no hay:** ni números, ni gráficas, ni cronómetro grabados en el vídeo; ni CSV ni informe; ni formato vertical 9:16 ni plantilla
  social. **Compartir** solo existe para el **enlace del reto de batalla** (hoja de compartir del sistema). La visualización lado a lado
  de dos vídeos con su superposición es de la app (`BattleStage`), no se exporta.
- **Lo que sí sale de la app** hacia su nube: el vídeo (comprimido solo si pesa más de 30 MB), la miniatura y los puntos de pose (§6).

## 6. Datos e integraciones

(Detalle en `06_servicios_y_datos.txt`; sin claves ni URL de proyecto.)

- **Nube propia (Supabase):** autenticación (correo y Google), cubo `user-uploads` (vídeo y miniatura), tabla `user_scans` (nota, variante,
  `capture_grade`, `engine_version`, `ai_feedback` con métricas, reglas disparadas, consejos y la **pose completa** `_pose_result_json`),
  `figures`, `figure_variants` (`difficulty_level`, `base_xp`), `figure_coaching`, `variant_milestones`, `scoring_rules` (puntos de
  penalización y textos), `profiles`, `app_events` y al menos 19 RPC (récords, clasificaciones, batallas, bloqueos).
- **Reglas de puntuación en el servidor:** se descargan al analizar; el APK solo trae los umbrales finos. La versión mínima del motor
  para «Certified» obliga a actualizar la app para seguir puntuando en el ranking.
- **Otros servicios:** RevenueCat (compra y paywall de Play Billing), Sentry (errores), Expo Updates (OTA), FCM (notificaciones),
  biometría, galería, cámara propia sobre CameraX. **Sin** BLE, wearables, Health Connect ni sensores.
- **Ya visto:** pose local y subida de resultados a una nube propia (Fulltrack, Yogger); lo nuevo aquí es que **el servidor guarda los puntos
  para recalcular la nota de otros** (batallas) con el mismo motor.

## 7. Las tres preguntas del usuario

**1. Perspectiva: ¿exige 90° de perfil o corrige las oblicuas?** Exige el perfil y **no corrige** la perspectiva.

| Qué | Valor en el código |
|---|---|
| Medida de la vista | Azimut = mínimo de la mediana de `atan2(\|Δz\|, \|Δx\|)` de la recta de hombros y de la de caderas con la **z de MediaPipe** (90° = perfil puro) |
| Para certificar (sostenes y flexión en pino) | Azimut ≥ **68°** (hasta ±22° de desvío del perfil); la **bandera humana** está exenta |
| Para detectar siquiera el sostén | Palanca frontal, «front touch» y palanca trasera: azimut mediano ≥ **58°**; si no, `no_hold` |
| Repeticiones | Clasificador `front` (azimut < 51° y hombros anchos) / `uncertain` (< 60°) / `side` / `oblique`. **Dominadas**: de frente se abstiene del balanceo (`view_front_sagittal`, «gira la cámara 45°») y juzga la patada por el acortamiento del muslo. **Fondos**: `front`/`uncertain` ⇒ `camera_angle_ok = false` (nota orientativa). **Flexiones**: si hombros–tobillos no son horizontales en la imagen (anchura mediana < 0,5), sin repeticiones con rango < 45° y `camera_angle_ok = false`. **Muscle-up**: solo `side` u `oblique` |
| Texto al usuario | «Film from the side at hip height, whole body in frame, in good light»; «hold the position still for at least 2 seconds»; «Film from the side… go through at least one full rep»; «Film with nobody else walking through the shot» |
| Corrección | **Ninguna de perspectiva** (sin homografía, sin plano, sin escala, sin ángulos 3D). Solo (a) x × relación de aspecto, (b) balanceo de 1° a 15° estimado con líneas rectas de la escena y (c) descarte de lados ocluidos |
| Efecto de una vista mala | `camera_angle_oblique`: aviso «orientativo y no cuenta en tus estadísticas» y `counted_degraded` (sin carta, sin «Certified») |

**2. Seguimiento híbrido, sin disco olímpico.** **No hay seguimiento de objetos**: ni barra, ni disco, ni anillas. La «barra» es la posición
de las **muñecas** (el mentón sobre la barra es la oreja frente a las muñecas; la transición del muscle-up es la altura de las muñecas
frente a los hombros). Lo único que no es esqueleto es (a) la **máscara de silueta** (3 figuras, §4.2) y (b) las **líneas de la escena**
para el balanceo. El «híbrido» de la app es esqueleto + recuperación con un segundo landmarker. Cuando falta la pose, el fotograma se
descarta (no se interpola).

**3. Vídeo con telemetría para redes.** Existe **a medias**: un botón para guardar el vídeo **con el esqueleto coloreado por la gravedad
de las reglas** (§5), implementado en iOS y **rechazado por el nativo de Android**. No graba números ni gráficas, no es 9:16 y no hay
botón de compartir el vídeo (solo el enlace del reto). La versión de calidad de redes —9:16, telemetría y cronómetro— **sigue siendo un
diferenciador de KPKN (D44)** y además funciona en Android.

---

## 8. Brechas frente a KPKN

Leí `VIDEO_TECHNIQUE_V1.md` (perfiles, vista y encuadre, segmentador, confianza, observaciones y su lista «NUNCA se emite»),
`BANCO_COMPLETO.md` (1 076 clips; G1 513 útiles, G2 278, G3 285 «no soportado»), `INFORME_CONFIANZA_20261020.md` (ALTA exige
`n_amb = 0`, ≥ 20 Hz, ≥ 3 FULL, sin varias personas) y `MERCADO_FIABILIDAD.md` (listón: series exactas ≥ 95 % en básicos, ≥ 99 % entre
las «seguras»). **Estado de KPKN hoy:** 23 perfiles de fuerza con segmentador cíclico; `ISOMETRIC`, `CORE`, `GRIP` y `NECK` sin soporte;
sin habilidades estáticas de calistenia ni muscle-up; vistas `FRONT/SIDE/OBLIQUE/BACK/LYING` (no hay «invertido»); `numPoses = 2`,
gravedad del móvil, PTS, filtro de fase cero, Hampel, Viterbi izquierda/derecha; confianza ALTA/MEDIA/BAJA calibrada; observaciones
como hechos con cifra, sin nota global. El banco trae 44 clips de flexión en pino (UCF101) y 5 de plancha (Kaggle) entre los G3.

| Función o métrica de CaliLens | ¿La tiene KPKN? | Cómo implementarla en sala limpia | Biblioteca abierta | Prioridad | Esfuerzo |
|---|---|---|---|---|---|
| **Segmentador de sostén** (postura gruesa por habilidad + quietud absoluta + ≥ 2 s + unión ≤ 0,8 s) | **No** (`ISOMETRIC` sin soporte; el TUT no sirve) | `HoldSegmenter` puro sobre la traza v2 ya filtrada: predicado de postura (tabla §4.3 como semilla), predicado de quietud con velocidad de los 14 puntos en **torsos/s** (escala S de KPKN, no alturas de fotograma) sobre ≥ 0,25 s con PTS reales, histéresis, unión de huecos ≤ 0,8 s; salida con **todos** los intentos, mejor racha y motivo de cada descarte | MediaPipe Tasks (ya); lo demás, Kotlin puro | **Alta** | ~1–1,5 semanas; hay que grabar sostenes con el teléfono |
| **Siete filtros contra falsos sostenes** (vista, ejercicio apoyado, deriva de rotación, bandera sin poste, cara arriba/abajo, cabeza lejos de las manos, torso incoherente) | No | Predicados con nombre y motivo «no es un sostén» (abstención), cada uno con prueba sintética | — | **Alta** | Bajo-medio |
| **Puertas de repetición en dominadas** (esqueleto volteado, quimera, geometría de colgado, piernas fijas, subida vertical ≥ 0,35 torsos, fantasma de suelta, cabeza que sube) | Parcial (`PULL_VERTICAL`: mentón, balanceo) | Añadir las puertas como vetos con motivo a `RepSegmenter`; miden precisión exacta (objetivo ALTA ≥ 98 %) | — | **Alta** | Bajo (2–3 días con el banco) |
| **Puertas de flexión y fondos** (descenso de cadera ≥ 7,5 %, fondo horizontal, extensión «de empuje», rodillas en el suelo; en fondos, torso ≤ 35°, muñecas bajo los hombros y plantadas) | Parcial (`PUSH_UP`, `DIP`) | Vetos con motivo; distingue fondos de flexiones, de planchas y de «subir un escalón» | — | **Alta** | Bajo |
| **Repetición «estricta» con tolerancia y perdón de bloqueo** (entrar ≤ 20 % en la zona; series rápidas aceptan umbral − 12°) | Parcial (FULL por amplitud ≥ 0,70) | Nivel adicional «completa con bloqueo» como **hecho con cifra** («5 de 8 llegaron a 160°»), no como «correcta»; perdón solo para series < 1,4 s por repetición | — | Media | Bajo |
| **Clase de vista «invertido» y estado `invertido/pino` por fotograma** (hombros bajo las caderas ≥ 0,5 torsos; tobillos sobre caderas) | No (`LYING` sí) | `ViewKind.INVERTED` y banderas por fotograma; la reparación de cruces y la elección de lado no deben suponer un cuerpo erguido; **medir antes** la tasa de pose boca abajo con los 44 clips de flexión en pino | MediaPipe (ya) | **Alta** | Medio |
| **Señal «cara arriba/abajo»** (nariz frente a orejas) | No | Mediana de (y nariz − y orejas)/torso para separar palanca frontal de trasera y planche de flexión en pica | — | Media | Bajo |
| **Recuperación con landmarker nuevo cuando no hay pose** | No consta | Tras N fotogramas sin pose, recrear el `PoseLandmarker` (o reiniciar el seguimiento) y reintentar el fotograma; contar los reintentos como bandera | MediaPipe (ya) | Media | Bajo |
| **Zonas continuas con valor sostenido** (smoothstep, suelo 0,1, P85, recorte de 0,4 s y rampas, cobertura ≥ 50 %, abstención por regla) | Parcial (observaciones con umbral duro) | Reutilizar la **mecánica** para que cada hecho lleve un «valor sostenido» y un «factor 0–1» **sin** nota global; los umbrales de §3.2–3.3 son **semillas** a calibrar con el banco y con jueces | — | Media | Medio |
| **Intervalos de fallo por regla** (`left/right/both`, hueco ≤ 0,4 s, ≥ 0,4 s) y **superposición coloreada** | Parcial (D10 compara trayectorias; D44 exporta) | Modelo de datos `(regla, lado, gravedad, [t0, t1])` para la línea de tiempo del isométrico y para D44 | Media3 Transformer (ya) | Media | Bajo |
| **Motivos de degradación de captura** (`body_out_of_frame`, `view_too_oblique`, `too_few_clean_frames`, `compressed_source`, `rules_not_evaluable`) | Parcial (flags, BAJA forzada) | Lista legible por la interfaz junto a ALTA/MEDIA/BAJA | — | Media | Bajo |
| **Comprobar la fuente** (lado corto < 640 px o < 1 Mbps) y **la luz** (luma de 64 px < 0,15) | No consta | Dos comprobaciones baratas al importar un vídeo | — | Media | Bajo |
| **Muscle-up** (señal `lift`, transición ≥ 0,4, intento fallido, bloqueo 165°, ala de pollo por disparidad de hombros) | **No** | Perfil nuevo con fases tirón → transición → empuje; se apoya en las puertas anteriores | — | Media | Medio |
| **Flexión en pino** (prueba de bloqueo ≥ 0,35 s, fondo invertido, cierre en pino, cabeza a ≤ 0,25 torsos de las manos) | **No** | Perfil nuevo tras tener la clase invertida | — | Media | Medio |
| **Habilidades estáticas** (FL, BL, PL, FT, HS, HF, LS) y sus variantes | **No** | Catálogo nuevo con `HOLD_*` y escalera de progresión; reglas de §3.2 como **semillas**; lo que el esqueleto no ve (columna, escápulas) sigue «no medible» salvo el proxy de silueta | — | Alta | Medio-alto |
| **Proxy de silueta** (arco lumbar y protracción de planche con la máscara) | No (KPKN no pide la máscara) | Pedir `outputSegmentationMasks` solo en las habilidades que lo usan, con «≈» y sin afirmar «flexión lumbar» (política de KPKN) | MediaPipe (ya) | Baja | Medio-alto (coste por fotograma) |
| **Balanceo por líneas de la escena** (Sobel + Hough sobre 5 fotogramas, 1°–15°) | Parcial (gravedad del móvil) | Útil sobre todo para **vídeos importados** (sin gravedad); código propio sobre 64 px | OpenCV (Apache-2.0) opcional | Baja | Medio |
| **Camino de progresión** (escalones por dificultad, 60 = pasar, 90 = maestría, siguiente avance, debilidades) | No (hay Programas) | «Sugerencia de progresión» en Programas a partir de las notas/hechos de los análisis; los umbrales (60/90) son suyos, no estándares | — | Media | Medio |
| **Modo en vivo con lista de comprobación** (visibilidad en 2 s, tobillos y hombros > 0,3, pose en ≥ 3 de 5, cuenta atrás de 3 s) | No (contador en vivo en la fase 2) | Reutilizar el diseño para el aviso previo a grabar en la fase 2 | MediaPipe LIVE_STREAM | Media | Medio |
| **Detección de figura equivocada** (re-puntuar con otros motores; ≥ 60 y +10) | No | Con `resolver` y perfiles ya hay candidatos; solo como sugerencia | — | Baja | Medio |
| **Cartas, XP, niveles, clasificaciones, batallas con recálculo** | No | **No hacer** ahora: exigen servidor y una medición validada | — | No hacer | — |
| **Nota global 0–100 con penalizaciones ocultas y sello «Certified»** | No (y la política lo prohíbe) | **No copiar**: sin validación frente a jueces ni a cronómetro, y con puntos que el usuario no puede ver | — | No hacer | — |

**Lo que KPKN hace mejor, o puede hacer mejor, que CaliLens** (por el código):
- **Tiempo:** PTS reales, fps variable, cámara lenta, filtro de fase cero y huecos acotados; CaliLens pide instantes a 10/12 fps, sin PTS,
  con mediana de 3 como único suavizado, y sus umbrales de quietud están en **fracción de fotograma** (dependen de la distancia al
  atleta), no en torsos.
- **Escala y orientación:** escala corporal S, gravedad del móvil, reparación de cruces izquierda/derecha por Viterbi, Hampel; CaliLens
  solo descarta lados ocluidos y corrige un balanceo pequeño.
- **Honestidad:** confianza calibrada con un banco y examen ciego, abstención explícita, hechos con cifra y «≈»; CaliLens da una nota
  cuyos puntos solo conoce el servidor y no publica validación. Una zona de balanceo de fondos que supera el máximo posible del ángulo
  (§3.3) y una regla de dominadas (`PU_ASYM_PULL`) sin alimentar sugieren umbrales sin comprobar.
- **Privacidad:** KPKN no sube vídeo ni puntos; CaliLens sube vídeo, miniatura y pose completa.
- **Alcance:** 23 perfiles de fuerza, disco y VBT, comparación de dos vídeos, tendencias.
- **Exportación:** D44 funciona en Android y lleva telemetría; el esqueleto de CaliLens es solo de iOS.
- **Auditabilidad:** cada número ligado a un fotograma y corrección manual; en isométricos podrá ofrecerse la línea de tiempo con la regla
  que se rompió.

**Lo que CaliLens hace mejor que KPKN hoy:** sostenes, cuerpos invertidos y horizontales, puertas de repetición baratas y bien pensadas,
silueta para el arco lumbar, recuperación del seguimiento, motivos legibles de degradación, y una progresión por escalones.

---

## 9. Índice de evidencias

Todo en `C:/kw/re/evidencias/calilens-real-re-20261021/` (253 KB): `00_*` identidad, hashes y listados; `01_*` manifiesto decodificado y
cadenas de recursos (credenciales **redactadas**); `02_motor_constantes_umbrales.json` (todos los umbrales de este informe, con id de
función); `03_indice_modulos_motor.tsv` (228 módulos Metro → función → papel); `04_codigos_de_reglas.txt` (108 códigos);
`05_modelo_tflite_formas.txt`; `06_servicios_y_datos.txt`; `07_bibliotecas_y_versiones.txt`; `08_metodologia_y_limites.txt`;
`09_clases_nativas_posedetection.txt`. Scripts: `C:\kw\re\_scripts\calilens-real\` (01–14).

| Tema | Módulo Metro · función (`fn#`) |
|---|---|
| Registro de motores | 2170 · `fn#31561` (`getEngine`, `supportedFigureKeys`) |
| Constantes y medidas comunes, `assessCapture`, lados ocluidos, azimut | 2143 · `fn#31162` (`assessCapture` `fn#31188`, `detectOccludedSides` `fn#31208`, `computeCommonMetrics` `fn#31215`) |
| Medidas por fotograma para reglas | 2144 · `fn#31221` |
| Criterios de pose, ventana estable, filtros de falsos sostenes | 2145 · `fn#31233` (`extractStableWindows` `fn#31265`, `filterHoldFrames` `fn#31321`, `faceDownSignal` `fn#31305`, `dropTorsoIncoherentFrames` `fn#31312`) |
| Movimiento 0,25 s | 2146 · `fn#31325` |
| Roll de la cámara | 2149 · `fn#31356` |
| Zonas (matemática) | 2152 · `fn#31393`; calidad de sostén 2162 · `fn#31488`; intervalos 2184 · `fn#31740` |
| Zonas por figura | 2154 (BL) · 2156 (FL) · 2157 (FT) · 2158 (HS) · 2159 (HF) · 2167 (LS) · 2168 (PL) · 2155 (comunes) |
| Motores de sostén | 2179–2196 (FL `fn#31696`, FT `fn#31753`, PL `fn#31775`, BL `fn#31813`, LS `fn#31836`, HS `fn#31840`, HF `fn#31883`); runner 2183 · `fn#31737` |
| Reglas por figura | 2180 · 2187 · 2189 · 2191 · 2194 · 2160 · 2165 |
| Fondos / dominadas / flexiones / muscle-up / flexión en pino | 2171 · `fn#31569`; 2197 · `fn#31888`; 2199 · `fn#32006`; 2201 · `fn#32102`; 2203 · `fn#32155` |
| Factor de serie, perdón de bloqueo, clasificador de vista | 2175 · `fn#31657`; 2173 · `fn#31647`; 2177 · `fn#31677` |
| Pipeline, muestreo, calidad de fuente, puntuación, figura equivocada | 2589 · `fn#35896`; 2586/2587; 2588; 2583/2585 · `fn#35862`/`fn#35868`; 2582 · `fn#35853` |
| Conversión del resultado nativo | 2148 · `fn#31341` |
| Certificado y versiones mínimas | 2136 · `fn#31051` |
| Progresión, XP, niveles, cartas, camino | 2613 · `fn#36235`; 2615 · `fn#36306`; 2255 · `fn#32900`; 2259 · `fn#32997`; 2034 · `fn#29368` |
| Cámara en vivo | 2618 · `fn#36324`; fotograma en vivo 2147 · `fn#31335` |
| Superposición y descarga | 2141 · `fn#31110`; 2153 · `fn#31422`; 2205 · `fn#32219`; 2761 · `fn#37018`; puente nativo 2597 · `fn#36005` |
| Nativo | `expo.modules.posedetection.{PoseDetectionModule, PoseCameraView, PoseSelector, SceneRollAnalyzer, BackArchAnalyzer}`; `burnPoseOverlay` → `UNSUPPORTED` en Android |

**Sin confirmar:** (1) filas de `figures`, `figure_variants` y `scoring_rules` (catálogo exacto de variantes y puntos de penalización);
(2) si el paquete OTA publicado difiere del embebido; (3) versión de MediaPipe Tasks y de FFmpegKit, y para qué se incluye FFmpegKit;
(4) si la interfaz de Android oculta «con esqueleto»; (5) pertenencia de las batallas al plan gratuito o a Pro; (6) uso exacto de
`ContestScoreSheet`; (7) políticas de seguridad del servidor; (8) la exactitud de la pose boca abajo, que el código no mide; (9) el
rendimiento real en un teléfono (no se ejecutó nada). Las constantes de `PoseSelector` y de la estimación de balanceo se leyeron de los
valores estáticos del DEX (`15_dex_static_values.py`).

---

## Anexo A · Confirmaciones y correcciones del informe anterior (material de la web [W] contra el código)

CONFIRMADO = el código dice lo mismo (con el valor exacto); PARCIAL = lo dicho es compatible pero incompleto o con otro matiz;
CORREGIDO = el código dice otra cosa; NO CONSTA = el código no lo implementa o no lo he visto.

| Afirmación del informe anterior (origen [W]) | Veredicto | Lo que dice el código |
|---|---|---|
| El APK analizado era la app de tienda `co.shopney.calilens` | CORREGIDO (error de archivo) | El análisis es de `com.calilensapp.app` 1.0.4 |
| 11 habilidades y 32 variantes | CORREGIDO | 12 motores: fondos, palanca frontal, «front touch», planche, palanca trasera, L-sit, pino, bandera humana, dominadas, flexiones, muscle-up y flexión en pino. Las variantes viven en el servidor (sin confirmar el número) |
| «Fondos no figuran entre las habilidades puntuadas» | CORREGIDO | Hay motor `dips` (v1) y es una de las cuatro figuras de partida |
| Palanca frontal: cadera ≈ 5 % ya es caída, ≈ 20 % grave (completa), ≈ 10 % una pierna, ≈ 12 % straddle | CONFIRMADO | Hundida 0,05 → 0,215 (completa); una pierna 0,10 → 0,20; straddle 0,12 → 0,22. Cadera alta (pike): −0,05 → −0,15; −0,12 → −0,22; −0,18 → −0,28 |
| Tuck y tuck avanzado no se juzgan por la línea | CONFIRMADO | Sin reglas de cadera ni de rodilla en esas variantes (en tuck avanzado hay `FL_LEGS_TOO_HIGH`) |
| Se ignoran los primeros y últimos instantes | CONFIRMADO | Recorte de 0,4 s por extremo (si ≥ 1 s y ≥ 4 fotogramas) más recorte de rampas |
| «Arranca al quedar quieto… cuentan los segundos con la línea en su sitio» | **CORREGIDO** | Arranca al primer fotograma de la ventana estable; el reloj **no** descuenta segundos por fallos de forma fina: mide la ventana más larga con pose gruesa + quietud (§4.3). La forma resta **puntos** |
| «El balanceo es un sostén “atrapado”» | NO CONSTA | Existe la regla `*_UNSTABLE` (mediana de movimiento) y los filtros de rotación lenta y de ejercicio apoyado; no hay «sostén atrapado» |
| Palanca trasera: espalda sin arco; cadera sobre la línea | PARCIAL | `BL_HIPS_PIKE/SAG`, `BL_ANGLE_TOO_LOW/HIGH` (cadera hundida bajo la barra y torso), `BL_LEGS_TOO_LOW` (tuck avanzado). No hay regla de «espinillas al suelo» |
| Planche: espalda inclinada ≥ ≈ 12° cuenta como cadera baja | PARCIAL | 12° → 25° es la zona del **ángulo del cuerpo** (hombros–tobillos), no de la espalda |
| Planche: la protracción escapular «no es observable con 33 puntos» (mi afirmación) | **CORREGIDO** | `PL_SHOULDER_PROTRACTION` la aproxima con la **silueta** (máscara de personas) y, de respaldo, con la distancia oreja–hombro; no con la columna ni las escápulas |
| Bandera humana: vista frontal, tobillos a la altura del hombro | PARCIAL | La bandera está **exenta** del azimut (compatible con una vista frontal); pies por debajo de la línea del hombro penalizan desde −2,4°, por encima desde 9,5° |
| Bandera: brazo inferior recto; manos en una vertical | PARCIAL / NO CONSTA | Codo del brazo de arriba y de abajo desde 140°; no hay regla de manos en vertical |
| L-sit: piernas ≥ ≈ 20° bajo la horizontal no cuentan; por encima, se convierte en V | CONFIRMADO | `LS_LEGS_TOO_LOW` seno −0,35 → −0,55 (≈ 20° → 33°); `LS_LEGS_TOO_HIGH` +0,105 → +0,305 (≈ 6° → 18°); V-sit con otra zona de cadera |
| L-sit: hombros deprimidos, tronco vertical, temblor | CONFIRMADO | `LS_SHOULDER_DEPRESSION`, `LS_TORSO_LEAN` (20° → 38°), `LS_UNSTABLE` |
| Pino: arco («banana») es fallo; codos rectos; piernas juntas | CONFIRMADO | `HS_HIPS_ARCH`, `HS_ARM_BEND_VISIBLE`, `HS_LEGS_SEPARATED` |
| Pino: «para en la primera corrección grande» | PARCIAL | La ventana acaba cuando fallan la pose o la quietud más de 0,8 s; las correcciones breves (≤ 0,8 s) se unen |
| Flexión: codo ≈ 90°; un fondo de 95°–100° es corto | CONFIRMADO con matiz | Penaliza desde 95° (plena a 125°); la repetición «estricta» admite hasta 100° |
| Flexión: más de ≈ 20° de recto arriba es corta | CONFIRMADO | 160° → 140° |
| Flexión: cadera dentro de ≈ 10 % | CONFIRMADO | Caída 0,10 → 0,16; subida −0,12 → −0,20; estricta entre −0,12 y +0,10 |
| Flexión: «worming» en ≥ 2 repeticiones | CONFIRMADO | 0,04 → 0,10, exige ≥ 2 repeticiones |
| Flexión: cabeza (oreja sobre la línea hombro–cadera), solo arriba | CONFIRMADO | `avg_head_offset` con codo ≥ 150°, zona 0,12 → 0,20 |
| «Solo cuentan las limpias» | CONFIRMADO | Se guardan `rep_count` y `strict_rep_count` |
| Dominada: mentón claramente sobre la barra | PARCIAL | Se usa la **oreja** frente a la altura media de las **muñecas** (≤ ~0,14 torsos) |
| Dominada: inicio escapular, bajada controlada | NO CONSTA | No hay regla de escápulas ni de velocidad de bajada |
| Dominada: colgado con codos rectos, sin patada ni péndulo, piernas rectas | CONFIRMADO | Extensión 146° → 126° (con exención por subida de cabeza), patada, balanceo, rodillas 168° → 150°, piernas separadas |
| Muscle-up: pecho sobre la barra, codos y muñecas a la vez, bloqueo, sin balanceo | CONFIRMADO | Transición `lift` ≥ 0,4, bloqueo 165°, ala de pollo (disparidad de hombros), balanceo y patada |
| Hollow body, colgado activo y pino en pared: guías sin puntuar | CONFIRMADO | No hay motor para ellos |
| Vista: lateral a la altura de la cadera, 3–4 m, sin zoom ni cámara lenta | PARCIAL | El código exige perfil (azimut), cuerpo entero y luz; **no comprueba** distancia, zoom ni cámara lenta |
| No corrige la perspectiva y no usa la gravedad | CONFIRMADO + matiz | Tampoco usa gravedad, pero estima un balanceo de 1°–15° con líneas de la escena |
| Sello «Certified» solo si se juzgaron todas las reglas | CONFIRMADO con condiciones | `verified_eligible` (≥ 8 fotogramas, pares ≥ 80 %, vista fiable) **y** sin reglas omitidas **y** fuente no comprimida **y** versión del motor ≥ mínimo |
| La pose se calcula en el teléfono y se suben vídeo, miniatura y puntos | CONFIRMADO | MediaPipe local; `ai_feedback._pose_result_json` va a la nube |
| El modelo de pose no se nombra | CORREGIDO | MediaPipe Pose Landmarker «full» con máscara de segmentación |
| El vídeo no sale con el esqueleto | **CORREGIDO** | Existe «descargar con esqueleto» (plan de superposición); solo iOS, Android lo rechaza |
| Modo en vivo con esqueleto (Pro) | CONFIRMADO | `LiveCameraScreen` + módulo nativo; cadena «Live analysis with skeleton overlay» |
| Gratis: 3 análisis cada 7 días, desglose completo solo en el primero | CONFIRMADO / PARCIAL | «free: 3 per week», historial de los últimos 3; la regla «solo el primero» no la he comprobado |
| Batallas 1 contra 1 con las mismas reglas, lado a lado | CONFIRMADO | La nota del rival se **recalcula** en el dispositivo con el motor actual; Elo en el servidor |
| Clasificaciones general y por figura | CONFIRMADO | `get_xp_leaderboard`, `get_variant_leaderboard` |
| CaliDex: tarjetas por variante; XP y rangos Rookie → Legend | CONFIRMADO | Cinco niveles de carta (Brut → Parfait) y seis de XP; umbrales exactos en §4.9 |
| «Subir de nivel con 3 × 8 s limpios» | **NO CONSTA** | La app usa notas: 60 pasa el escalón, 90 es maestría; el 3 × 8 s era un estándar del blog |
| Ejemplo de portada «97,2 con −2,8 por pies sobre la línea del hombro» | COMPATIBLE | Equivale a `*_ANGLE_TOO_HIGH` (`ankle_drop`) con factor pequeño |
