# Diferenciadores frente a la competencia (20-oct)

Origen: una nota que otro agente dejó al usuario y que él trajo a esta ronda. Se completará con lo que encuentre la ingeniería inversa
de WL Analysis, My Jump Lab y WOD App (`docs/video-technique/re/`). Cada punto se valida con el banco completo (D41) o con el teléfono
antes de darlo por bueno.

## D42 · Ángulos de cámara no perpendiculares (perspectiva)
**El problema.** Casi todas las apps exigen grabar exactamente de perfil, a 90°. A 45°, la trigonometría del ROM y del recorrido
falla.

**Lo que KPKN ya tiene:**
- un clasificador de vista (lateral, oblicua o frontal) por la anchura de hombros y caderas y por los puntos «world»;
- la comprobación de la elipse del disco: aviso con a/b < 0,85 y rechazo con < 0,70;
- el ROM con «≈» y confianza baja en oblicuo;
- el giro del móvil por el acelerómetro (`rollForFrameDeg`) en el grabador.

**Lo que se añade:**
1. **Ángulos articulares en 3D** con los puntos «world» de MediaPipe, que son métricos y están centrados en la cadera. Ya se guardan
   12 en `PoseTrack v2`. El ángulo 3D apenas depende del giro de la cámara. Se usa cuando la vista es oblicua, y el ángulo 2D con el
   aspecto corregido cuando es lateral.
   - Se valida con los 108 clips útiles oblicuos del banco (conteo) y con grabaciones en el teléfono a 90° y 45° de la misma serie
     (ROM).
2. **Rectificar la trayectoria de la barra con la elipse del disco.**
   - El disco es un círculo de diámetro conocido en un plano perpendicular a la barra.
   - Su elipse en la imagen da el ángulo entre la cámara y la barra (cos θ = b/a) y la dirección del eje. Con eso se pasa la
     trayectoria del centro al plano del disco: recorrido y m/s reales en oblicuo.
   - Se valida con el teléfono: la misma serie grabada a la vez de perfil y a 45°.
3. **Vertical verdadera:**
   - la gravedad del móvil al grabar, que ya se guarda en `capture.json`;
   - opcionalmente, los montantes del rack (puntos de fuga) en vídeos de galería sin metadatos.

## D43 · Tracking híbrido: disco con barra; esqueleto u objeto sin ella
**El problema.** Metric o WL Analysis son excelentes con barra, pero no sirven en poleas, máquinas o mancuernas.

**Lo que KPKN ya tiene:** el perfil de cada ejercicio elige la señal por su equipamiento.
- Con barra (barra, smith, hexagonal, safety, T), la señal principal es el disco.
- Con mancuernas, poleas o máquinas, es la pose del perfil: muñeca, codo, cadera… (`profiles/`, Anexo A).

**Lo que se añade:**
1. **Relevo automático dentro de la serie.** Si se pierde el disco (tapado o fuera de cuadro), la señal pasa a las muñecas en esos
   tramos, con su confianza, y vuelve al disco al recuperarlo. Hoy se pierde ese tramo.
2. **Seguir un objeto que la persona toca, en máquinas y poleas:** el agarre, el carro o la pila de placas, con una semilla de un
   toque y un tracker de plantilla (NCC/KCF). Así una máquina tiene trayectoria y velocidad propias, no solo las del cuerpo. Encaja con la
   calibración de un toque que se propuso al usuario: tocar al atleta o tocar el disco.

## D44 · Exportar el vídeo con la telemetría quemada (para redes)
**El problema.** Gran parte del crecimiento orgánico de Metric viene de exportar el vídeo con el trazo de la barra y los gráficos de
velocidad y ROM quemados en la imagen, con una estética de telemetría de Fórmula 1, listo para Instagram o TikTok.

**Lo que KPKN ya tiene:** el reproductor con la capa sincronizada (esqueleto, trayectoria por velocidad, guía vertical y marcas de
repetición), alimentada por los sidecars con PTS.

**Lo que se añade:**
- **Exportar a MP4** con Media3 Transformer (ya está Media3 1.5.1) y `OverlayEffect`, en vertical 1080×1920 para historias o reels y en
  el formato original.
- **Qué se dibuja:**
  - la trayectoria coloreada por velocidad;
  - el contador de repeticiones;
  - las barras de velocidad por repetición;
  - el indicador de ROM o profundidad;
  - el tempo;
  - la ficha de la serie: ejercicio, carga, reps y RIR;
  - la marca KPKN.
- **Estética:** pitch-black con neón, el lenguaje de KPKN, y tipografía de telemetría.
- **Honestidad:** solo se quema lo fiable. Con «≈» donde toque y nada inventado: sin m/s si el disco no está calibrado.
- Se valida con hojas de render y un vídeo real del teléfono.

## Lo que confirmó la ingeniería inversa (20-oct)
Informes en `docs/video-technique/re/`: `WL_ANALYSIS_RE_V2.md`, `MY_JUMP_LAB_RE.md` y `WOD_APP_RE.md`. El mapa de apps y métricas por
tipo de atleta está en `CANDIDATAS_Y_METRICAS.md`.

**Ninguna de las tres corrige la perspectiva ni tiene tracking híbrido.** Solo WL Analysis exporta vídeo con telemetría: D42 y D43
siguen siendo diferenciadores, y D44 es la brecha principal.

| Id | Qué | De dónde | Prioridad | Esfuerzo |
|---|---|---|---|---|
| D44 | **Exportar vídeo con telemetría.** WL Analysis lo hace con Media3 Transformer, dibujando cada fotograma con el código del reproductor: trazo progresivo amarillo→rojo, rejilla en cm, referencias, tabla y franja de gráfico, cámara lenta y dos vídeos lado a lado. NO tiene 9:16: ahí ganamos | WL Analysis | alta | ~1 semana |
| D47 | **Buscar el disco sin depender de la pose.** Búsqueda geométrica en todo el fotograma con un toque para confirmar; más adelante, un detector propio. Hoy exigimos las muñecas quietas, y eso falló en r02. Evitar Ultralytics/YOLO, que es AGPL | WL Analysis | alta | media |
| D48 | **Módulo de saltos:** CMJ, SJ y drop jump con despegue y aterrizaje por los PIES de la pose, a 60–240 fps (CameraX 1.5 permite la cámara lenta), y encima Samozino (F, v y P), el perfil F-V con su desequilibrio, RSI y RSImod, la rigidez (Morin) y las asimetrías. My Jump Lab marca los fotogramas a mano | My Jump Lab | alta | media |
| D49 | **Extras de VBT:** voz por repetición, alerta de pérdida de velocidad, velocidad objetivo, «test rápido» de 1RM con una carga, método de dos puntos, tabla editable de v1RM por ejercicio, potencia media y pico BIEN calculadas a ≥ 50 fps, CSV, rejilla métrica y recorte del vídeo antes de analizar | My Jump Lab y WL Analysis | media-alta | ~2 días cada uno |
| D50 | **Esprint y carrera:** tiempos de contacto y vuelo, frecuencia y longitud de paso, F-V del esprint (Samozino 2016), test 505 de cambio de dirección y kinograma automático por la pose. My Jump Lab y Runmatic lo hacen a toques | My Jump Lab y candidatas | media | media-alta |
| D43.2 | **Marcador impreso** (estilo ArUco, con la escala de su propio lado) para mancuernas y máquinas. WL Analysis usa uno, pero escala mal | WL Analysis | media | media |
| — | **Temporizadores EMOM, AMRAP y For Time,** con el servicio en primer plano y la voz que ya existen | WOD App (carencia de KPKN) | media | baja |

### Segunda tanda (21-oct): Metric, Sportsbox, Ochy, Yogger, Fulltrack, Photo Finish, AscentAI, CaliLens, AI Punch Pulse y My Lift
Informes en `docs/video-technique/re/`.
- **Ninguna corrige la perspectiva con homografía ni elipse, y ninguna tiene tracking híbrido.**
- **Exportan vídeo:** solo Metric (9:16, sencillo) y WL Analysis. Las demás graban la pantalla o lo generan en su servidor.
- **Lo hacen todo en la nube:** Ochy, Fulltrack y parte de CaliLens y Yogger. KPKN lo hace todo en el teléfono, y eso es una ventaja de
  privacidad, coste y uso sin conexión que conviene comunicar.
- **No aportaron método nuevo:** My Lift (era otra app con el mismo nombre; el real está en My Jump Lab) y AI Punch Pulse.

| Id | Qué | De dónde | Prioridad | Esfuerzo |
|---|---|---|---|---|
| D42.1 (refuerzo) | **Coordenadas métricas absolutas** desde los puntos «world»: escala antropométrica, traslación por rayos de cámara y anclaje del pie. Más ángulos articulares por marcos de segmento | Sportsbox | alta | media |
| D47 (confirmado) | Disco por **detección geométrica de elipses**, sin ML (Metric lo hace en GPU), más un toque para confirmar | Metric y WL Analysis | alta | media |
| D51 | **Captura cuidada:** obturación de 1/360 a 1/100 s con ISO acotado, sin parpadeo a 50/60 Hz, exposición medida y bloqueada sobre el disco, y una cadena de fps de respaldo (60 → alta velocidad → 30) con control térmico | Metric y Fulltrack | alta | baja-media |
| D52 | **Segmentador de eventos balísticos** (pico, inversión y umbral, con máquina de estados en vivo) para saltos, lanzamientos, golpes y swings. Y la **secuencia cinemática** | Sportsbox | alta | media |
| D53 | **Motor de isometrías:** la ventana estable más larga con una pose gruesa válida y quietud (≤ 0,15 alturas de fotograma/s), 7 filtros contra falsos sostenes y la forma fina en puntos, no en segundos | CaliLens | alta | ~1,5 semanas |
| D54 | **Puertas de repetición válida** en dominadas, flexiones y fondos: histéresis del codo, esqueleto volteado, colgado, descenso de cadera… Y una clase de vista «invertida» para el pino | CaliLens | alta | baja-media |
| D50 (detallado) | **Carrera:** el catálogo de métricas de Ochy. **Cronometraje:** cruce de línea con instante subfotograma, y el 505 y la lanzadera con una sola cámara (Photo Finish) | Ochy y Photo Finish | alta | media-alta |
| D55 | **Calibración completa de la cámara** con geometría conocida (marcador impreso u objetos de medida estándar) y recalibración si el trípode se mueve. Mide pelotas e implementos y admite vistas oblicuas | Fulltrack | media | alta |
| D56 | **Grabación sin manos:** una red pequeña o un detector de movimiento que dispara y un búfer circular que guarda 0,6–1 s previos, sin recodificar | Fulltrack | media | media |
| D57 | **Movimientos no cíclicos:** fases preparar → alcanzar → estabilizar, centro de masas segmentario, inmovilidad y entropía geométrica (escalada…) | AscentAI | media | media |
| D58 | **Pruebas de movilidad guiadas** (base → pico → vuelta, con simetría), gráfico ángulo–tiempo, CSV y goniómetro manual | Yogger | media | media |
| D59 | **Catálogo de calistenia:** planche, palancas, bandera, L-sit, pino y muscle-up, con progresiones | CaliLens | media | media |
| — | **Combate:** guardia alta y tiempo de vuelta a la guardia | AI Punch Pulse | baja | media |

**No hay que copiar:**
- de Metric: descartar los tramos lentos de la velocidad media e inventar repeticiones por cualquier bajada de 10 cm;
- de Sportsbox: la focal supuesta sin avisar y el «percentil» = 100 − 25 |z|;
- de Yogger: el muslo fijo de 0,40 m y los 21 fps fijos;
- de CaliLens: los 10 fps sin PTS;
- de todas: la subida de vídeos o datos sin decirlo.

**No hay que copiar:** las escalas fijas sacadas de la primera caja; los tiempos por índice de fotograma, sin PTS; los filtros que dejan
la velocidad en 2,7–4,0 Hz, que explican el sesgo de la velocidad pico publicado; la potencia total mal sumada; y el envío de datos a la
nube sin decirlo (My Jump Lab usa Firestore pese a su texto de privacidad).

## Defectos encontrados al probar en el Z Flip5 (21-oct)
- **B1 · Veto de GPU por cierres del sistema (alta, esfuerzo bajo).**
  - `GpuProbe` marca la GPU como «en prueba» y, si el proceso muere con la marca puesta, la veta para toda la versión.
  - En el teléfono, Android mató el servicio por el límite de 6 h (`FGS timed out`) en plena inferencia. La app vetó la GPU, y el
    análisis pasó de unos 15 ms a 214 ms por fotograma en la CPU, unas 14 veces más lento, hasta la siguiente versión.
  - En la app real puede pasar igual si el sistema cierra el análisis por memoria, por tiempo o porque la persona cierra la app.
  - Arreglo: vetar solo si el cierre anterior fue un fallo real. `ActivityManager.getHistoricalProcessExitReasons` da el motivo:
    `REASON_CRASH_NATIVE`, `REASON_CRASH` o `REASON_ANR`. Con cualquier otro motivo (memoria, sistema, cierre por la persona), se
    borra la marca sin vetar. Y que el veto caduque tras N arranques correctos.
  - **Arreglado en B12** (`ed1df0085`, 21-oct):
    - solo un `NATIVE_CRASH` veta al momento;
    - un fallo Java (como el corte del servicio) o un cierre del sistema no veta;
    - con causa desconocida (API < 30, ANR o una marca sin pid) se veta al 2.º aviso seguido.
    - El veto no caduca por arranques, sino con la `versionCode`: un fallo nativo de la GPU se repite siempre, y reintentar
      supondría un cierre más de la app.
    - Falta probarlo en el Flip.
- **B2 · Servicio de análisis largo (media).** El trabajador real (`TechniqueAnalysisWorker`) usa también un servicio en primer plano.
  Hay que comprobar que su tipo y su `onTimeout` no dejan el análisis a medias sin aviso en Android 15+.
  - **Arreglado en B12** (`ed1df0085`, 21-oct):
    - WorkManager 2.11.2 ya atiende el límite: `SystemForegroundService.onTimeout` → `SystemForegroundDispatcher.onTimeout` para
      los trabajadores con el motivo −128;
    - en un paro del sistema la fila vuelve a `QUEUED` y el análisis sigue desde su punto de control, en vez de quedarse en
      «Analizando… 42 %».

## Orden propuesto
1. Ronda de interfaz en curso: UI-2, UI-3 y UI-4.
2. **Rondas del motor con el banco:** D42.1 (ángulos 3D) y D43.1 (relevo), que se miden enseguida con las trazas del Flip.
3. **D44, exportar con telemetría:** independiente del motor. Puede ir en paralelo a las rondas cuando acabe la ronda de interfaz.
4. **D42.2, D43.2 y la calibración de un toque:** necesitan interfaz y validación en el teléfono.
