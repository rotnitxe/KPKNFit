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

## Orden propuesto
1. Ronda de interfaz en curso: UI-2, UI-3 y UI-4.
2. **Rondas del motor con el banco:** D42.1 (ángulos 3D) y D43.1 (relevo), que se miden enseguida con las trazas del Flip.
3. **D44, exportar con telemetría:** independiente del motor. Puede ir en paralelo a las rondas cuando acabe la ronda de interfaz.
4. **D42.2, D43.2 y la calibración de un toque:** necesitan interfaz y validación en el teléfono.
