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

**No hay que copiar:** las escalas fijas sacadas de la primera caja; los tiempos por índice de fotograma, sin PTS; los filtros que dejan
la velocidad en 2,7–4,0 Hz, que explican el sesgo de la velocidad pico publicado; la potencia total mal sumada; y el envío de datos a la
nube sin decirlo (My Jump Lab usa Firestore pese a su texto de privacidad).

## Orden propuesto
1. Ronda de interfaz en curso: UI-2, UI-3 y UI-4.
2. **Rondas del motor con el banco:** D42.1 (ángulos 3D) y D43.1 (relevo), que se miden enseguida con las trazas del Flip.
3. **D44, exportar con telemetría:** independiente del motor. Puede ir en paralelo a las rondas cuando acabe la ronda de interfaz.
4. **D42.2, D43.2 y la calibración de un toque:** necesitan interfaz y validación en el teléfono.
