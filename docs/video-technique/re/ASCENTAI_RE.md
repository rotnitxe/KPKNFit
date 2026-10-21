# AscentAI – Climbing Analysis 1.7.1 (Android) · ingeniería inversa estática (informe corto)

Fecha: 2026-10-21. Brief `docs/video-technique/briefs/RE_COMUN.md`, reducido por una instrucción posterior del usuario: si la app no aporta
nada nuevo, solo identidad, por qué no aporta y la única novedad. Sala limpia: nada se ejecutó ni se instaló; sin código ni modelos copiados;
todo con palabras propias y, como evidencia, nombres de clases y constantes. Herramientas: REA 6.5.0 (apktool), JADX de REA solo sobre el
paquete propio (bajo `pesado.lock`) y scripts propios en `C:\kw\re\_scripts\ascentai\`.

**Veredicto.** Casi nada aporta: pose genérica, sin calibración ni perspectiva y exportación por grabación de pantalla. Lo único nuevo son las
**métricas de escalada**: centro de masas, entropía geométrica, fases de movimiento y cinco reglas heurísticas. **No hay jerk real, ni
detección de presas, ni tiempo por presa, ni distancia cadera–pared.**

## 1. Identidad

| Dato | Valor |
|---|---|
| Paquete · versión | `com.jonasdeuchler.ascendai` · 1.7.1 (versionCode 27) · minSdk 29 · targetSdk 35 |
| XAPK | `AscentAI_+Climbing+Analysis_1.7.1_APKPure.xapk`, 163 600 065 B, SHA-256 `d61465b29790216afec56000e62807b468115ebc04900a109e3ca8657479f3dd` |
| APK internos | base 159 271 241 B, `ff1ed37c1e5981d7449cf3d100d51f556be97a8098892ab5c437bbbda9d6ea48`; `config.armeabi_v7a` 3 359 604 B; 17 splits de idioma y `mdpi` (los de idioma solo traen cadenas de bibliotecas, comprobado en `es`). Los hashes de todos, en `inventario_xapk.json` |
| Arquitectura | Kotlin 2.0.21, una Activity, Jetpack Compose 1.7.6 con Material3, Hilt 2.52. Sin ofuscar: 909 clases propias |
| Nativo | La base no trae `lib/`. El split v7a trae TFLite 2.15.0 (2,18 MB) con su delegado GPU (1,06 MB) y utilidades de CameraX. El split arm64 no viene en el XAPK (sin confirmar) |
| Modelo ML | `assets/movenet_thunder.tflite`, 12 584 128 B: MoveNet Thunder con pesos float16. Entrada `uint8 [1,256,256,3]`, salida `float32 [1,1,17,3]` (y, x, puntuación). **Local**: GPU si el aparato la admite; si no, 4 hilos de CPU |
| Bibliotecas | TFLite y support (Apache-2.0); jDSP 0.4.0 (MIT, solo Savitzky–Golay); commons-math3 3.6.1; CameraX y Media3 (Apache-2.0); Play Billing 7.1.1; Firebase Analytics 22.1.2 (ASDKL) y Crashlytics 19.3.0. XChart, PDFBox y VectorGraphics2D (LGPL) vienen, probablemente, con jDSP y el código propio no las llama |
| Permisos y sensores | Cámara, micrófono, `READ_MEDIA_VIDEO`, servicio en primer plano de proyección de pantalla, vibración, facturación, internet. No lee acelerómetro ni giroscopio |
| Nube y datos | Solo Firebase Analytics y Crashlytics. Análisis y datos locales (serialización de Java en `files/`). No encontré cuentas ni parte social |

## 2. Por qué casi no aporta

- **Pose:** el ejemplo de TensorFlow Lite con MoveNet y su recorte por seguimiento. Una persona, 17 puntos, sin manos ni pies detallados.
  KPKN ya usa MediaPipe de 33 puntos con puntos «world».
- **Tiempo:** índice ÷ fps medio (fotogramas del contenedor ÷ duración). Sin PTS, VFR ni cámara lenta.
- **Calibración, escala y perspectiva: no hay.** Todo en píxeles de un fotograma de lado largo ≤ 1500: sin homografía, plano de pared, gravedad
  ni corrección de inclinación. Pide cámara fija, una sola persona y «perspectiva plana».
- **Exportación:** grabación de pantalla (§5). Sin tracking de objetos ni híbrido.
- **De pago** (mensual, 7 días gratis): comparar con un segundo vídeo (pantalla dividida y solape al 50 %) y dibujo.

## 3. La única novedad: métricas de escalada

**Centro de masas (CoM).** Suma ponderada de los 17 puntos: nariz 0,08; hombros 0,125 c/u; caderas 0,17 c/u; codos 0,032; muñecas 0,024;
rodillas 0,085; tobillos 0,024; ojos y orejas 0 (suma 1). La nariz pasa al punto medio de las orejas si ambas superan 0,2. **Solo se calcula
si los 17 puntos superan 0,2**; los huecos se rellenan por interpolación lineal sin extrapolar y se suaviza con Savitzky–Golay de 25
fotogramas y orden 4 (fijo, no escala con los fps). En su clip de ejemplo (493 fotogramas, 30 fps) el 19 % del CoM es interpolado, con rachas
de hasta 14 fotogramas, y los 7 finales quedan sin CoM. Las coordenadas son píxeles, sin metros.

**Fluidez.**
- **Jerk: no está en la interfaz.** Existe una función y un dibujo, pero nadie los llama. La función promedia bloques de 10 fotogramas y aplica
  segundas diferencias **sobre los mismos datos**, reutilizando valores ya convertidos: en mi simulación sobre su clip acaba en ±infinito.
- **«Factor de caos» (entropía geométrica):** GE = ln(2L/C), con L la longitud de la trayectoria del CoM y C el perímetro de su casco convexo
  (mín. 4 puntos; sin unidades; solo comparable con la misma cámara). Es la definición de Watts et al. (Sports Biomechanics 2021;20(8):1031),
  que la relaciona con el coste energético (r = 0,67, según el resumen). Su clip da 0,51. Al comparar dos vídeos marca el menor como mejor; una
  diferencia < 0,05 es empate (también con el tiempo).
- **Mapa de velocidad:** paso por fotograma de la parte elegida (CoM, muñecas o tobillos), media recortada (sin el 30 % más bajo ni el 30 % más
  alto) en ±20·fps/30 fotogramas, normalizada por el percentil 75 del paso del CoM (mínimo 1 px/fotograma). Rosa = rápido, azul = lento.
- **Inmovilidad:** se calcula (ventana de ±15·fps/30 fotogramas con paso medio del CoM < 0,55 × torso ÷ 240, más una cota de desplazamiento),
  pero en 1.7.1 ninguna pantalla lee el valor ni activa su dibujo; el registro de cambios de la 1.7 dice «Removed stillness parameter».

**Presas y movimientos.** No hay presas, tiempo por presa, 3D ni clasificación dinámico/estático. Lo único cercano es una máquina de tres
estados: se suaviza el paso por fotograma del CoM, las muñecas y los tobillos (Savitzky–Golay 25, orden 3) y una parte «se mueve» si su
z-score supera la cuarta parte del máximo del clip durante ≥ 10 fotogramas. Empieza en PREPARACIÓN; pasa a ALCANCE si se mueve una muñeca,
a ESTABILIZACIÓN cuando ninguna se mueve y vuelve a PREPARACIÓN si se mueve un tobillo sin muñecas. Cinco reglas «experimentales»: codo de la
mano más alta ≤ 150° en más de la mitad del tramo central (en preparación y en estabilización: dos reglas); esa muñeca en movimiento > 0,8 s en el
alcance; tobillo en movimiento > 10 % del alcance; CoM poco alineado con la mano de apoyo (razón < 0,2). Se inspiran en Beltrán et al.
(Sensors 2023;23(19):8216: mitad del máximo, ≥ 30 fotogramas a 60 fps, codo < 130°, apoyo > 1 s, con LiDAR; precisión 0,70–0,84 y
exhaustividad 0,75–0,85; cifras de un resumen en PMC, sin revisar el texto completo). **La versión 2D de AscentAI no está validada.**

## 4. Qué tomar para KPKN

| Función o métrica | ¿La tiene KPKN? | Cómo, en sala limpia | Prioridad · esfuerzo |
|---|---|---|---|
| CoM por segmentos (M55) | No (hay trayectoria de disco y de muñeca) | Fracciones de masa publicadas (p. ej. de Leva) sobre los 33 puntos, solo segmentos visibles y renormalizado; huecos ≤ 0,25 s con `GapFiller`; «≈ CoM» en unidades FH | Alta · 3–5 días |
| Inmovilidad y tiempo quieto (M54) | No | Velocidad del CoM y las manos en torsos/s (escala S ya existe), PTS y fase cero; histéresis y duración mínima en segundos | Alta · 3–5 días |
| Segmentador no cíclico: preparar, alcanzar, estabilizar | No (el segmentador es cíclico) | Ráfagas de muñecas y tobillos con umbral absoluto e histéresis (no relativo al máximo del clip); nº de movimientos, tiempos de alcance y estabilización, % estático; validar con clips etiquetados | Alta · 1–2 semanas |
| Entropía geométrica y comparar dos intentos | Parcial (compara series y trayectorias) | Casco convexo y longitud del CoM, «orientativo», misma cámara y pared; inicio y fin elegidos, solape al 50 %, curvas de velocidad en un eje | Media · ~1 semana |
| Observaciones de escalada | No | Máx. 3, «experimentales», umbrales propios medidos con escaladores | Media · semanas + validación |

Lo que AscentAI no hace y serían diferenciadores para KPKN: distancia cadera–pared con vista lateral y gravedad, tiempo de contacto por
mano, mapa de presas con toques del usuario y cámara que sigue al escalador. **No copiar:** CoM todo o nada, umbral relativo al máximo del clip (no
comparable entre vídeos), ventanas fijas en fotogramas, tiempo por índice, el jerk y la grabación de pantalla.

## 5. Las tres preguntas del usuario

1. **Perspectiva:** exige cámara fija y **frontal a la pared**, con una persona; no corrige oblicuos.
2. **Tracking híbrido:** no. Solo esqueleto: sin presas, cuerda ni objetos; si el modelo falla, interpola.
3. **Vídeo con telemetría:** sí, como **grabación de pantalla** del reproductor (trazo, esqueleto, gráficas y dibujos, con micrófono):
   MediaProjection → MediaRecorder (H.264), resolución de la pantalla, 10 Mbit/s, 30 fps, en `DCIM/AscentAI/` con botón de compartir
   (gratis desde la 1.4). Sin 9:16 ni render por fotograma: D44 lo supera.

## 6. Evidencias y sin confirmar

`C:/kw/re/evidencias/ascentai-re-20261020/`: inventarios, manifiesto decodificado, bibliotecas, forma del modelo, `constantes_y_umbrales.json`,
`ejemplo_estadisticas.json`, `ejemplo_fases.json`, listas de clases y símbolos. Clases clave: `ml.MoveNet`, `ml.PoseProcessing`,
`data.constructs.BodyPartWeight`, `utils.calculations.{Smoothing,Interpolation,Velocity,Jerk}Kt`, `utils.GeometricEntropyUtils`,
`ui.states.{VelocityStates,SuggestionStates,InformationStates}`, `recording.RecordService`. Sin confirmar: el split arm64, si la
inmovilidad volvió en otra versión, el reparto exacto del bloqueo de pago (su texto cita también el «factor de caos») y todo lo que
requiera ejecutar la app.
