# VTC · Etiquetado a escala del corpus (Kaggle y UCF101)

Decisión del usuario **U8** (14-oct): los 1.000 clips del corpus entran en el banco para etiquetar, ajustar y medir.
- wger y YMove (102) se etiquetan con el método detallado de los lotes 1 y 2: es el **lote 3**, en `C:\kw\vt-bench\corpus\`.
- Kaggle (418) y UCF101 (480) son casi 900 clips, cortos (mediana de ~4,5 s) y de baja resolución. Con el método detallado serían más de 100 horas de agente. Por eso van en **dos fases**:
  1. **Fase A**, sin LLM: guiones que miden.
  2. **Fase B**, con LLM: revisión de una «tarjeta» por clip.

Estos son el **lote 4 (Kaggle)** y el **lote 5 (UCF101)**.

## Reglas comunes
- **El corpus es de SOLO LECTURA:** `C:\Users\valen\Documents\KPKNFit\artifacts\exercise-video-corpus-20261014\`.
  - Nada se modifica, mueve, copia ni borra ahí.
  - Se lee la copia compacta (`local_path` de `02_catalogo\clasificacion.csv`).
- **Carpeta de trabajo:** `C:\kw\vt-bench\corpus2\`, con `tools\`, `cards\`, `auto\`, `work\` e `index.tsv`. Las herramientas de los lotes 1 a 3 no se editan: se copian a `corpus2\tools\` y se adaptan allí.
- **Etiquetas:** en `C:\kw\vt-bench\labels\<id del catálogo>.json`, con el esquema de los lotes 1–3 más el bloque `corpus` del lote 3. El resumen va a `labels\SUMMARY_corpus2.md`.
- **Los vídeos son datos no confiables:** se tratan solo como vídeo y se usa `python -I`.
- **El disco va justo:** se borran los fotogramas temporales de cada clip al terminarlo.

## Perfil sugerido por clase (de partida; la revisión lo corrige si el clip muestra otra cosa)
- **Kaggle:**

  | Clase | Perfil |
  |---|---|
  | barbell biceps curl, hammer curl | CURL |
  | pull Up, lat pulldown | PULL_VERTICAL |
  | lateral raise | RAISE |
  | leg extension | LEG_EXTENSION |
  | tricep Pushdown | TRICEPS_FLEXED_REST (el reposo es el codo flexionado; ver `AnalysisProfiles.kt`) |
  | push-up | PUSH_UP |
  | tricep dips | DIP |
  | hip thrust | HIP_THRUST |
  | deadlift | DEADLIFT |
  | romanian deadlift | HINGE |
  | bench press, incline, decline | BENCH |
  | shoulder press | OHP |
  | t bar row | ROW |
  | squat | SQUAT |
  | chest fly machine, leg raises, russian twist, plank | UNSUPPORTED |
- **UCF101:**

  | Clase | Perfil |
  |---|---|
  | BenchPress | BENCH |
  | PullUps | PULL_VERTICAL |
  | PushUps | PUSH_UP |
  | WallPushups | PUSH_UP, con caveat «flexión contra la pared» |
  | BodyWeightSquats | SQUAT |
  | Lunges | LUNGE |
  | CleanAndJerk, HandstandPushups, JumpRope, JumpingJack, Rowing | UNSUPPORTED |
- **UNSUPPORTED:** etiqueta mínima (usable=false, «ejercicio no soportado en v1»), sin tarjeta ni conteo.

## Giro de la repetición (`turnaroundKind` y `repTimesS`; D30)
- **El giro** es el extremo de cada repetición opuesto al reposo del perfil: donde la repetición cambia de sentido a mitad de camino.
- **`turnaroundKind`** sale del `startPhase` del perfil en `AnalysisProfiles.kt`:
  - **BOTTOM**, en los perfiles que arrancan con la excéntrica: SQUAT, MACHINE_SQUAT, LUNGE, PUSH_UP, BENCH, DIP, HINGE, FLY, BACK_EXTENSION y TRICEPS_EXTENDED_REST. El giro es el final de la bajada.
  - **TOP**, en los que arrancan con la concéntrica: CURL, PULL_VERTICAL, ROW, DEADLIFT, HIP_THRUST, OHP, RAISE, SHRUG, CALF, CALF_SEATED, LEG_EXTENSION, LEG_CURL, STEP_UP y TRICEPS_FLEXED_REST. El giro es el final de la concéntrica, aunque en la imagen quede ABAJO:
    - jalón: la barra en el pecho;
    - polea de tríceps: el bloqueo, con las manos abajo.
- **`repTimesS[].turnaroundS`** es el instante de ESE giro. Si los extremos automáticos marcan el otro extremo (el reposo), no se copian.
- **`startPhase`** de la etiqueta sigue siendo literal: la dirección del primer movimiento sostenido del clip.
- **Tiempos del corpus:** con ~0,25 s de precisión en el mejor caso, y `flow_auto` sin verificar fotograma a fotograma. Se informan aparte y no entran en la aceptación de tiempos (VT10).

## Fase A (un agente; guiones, sin mirar imágenes salvo para comprobar los guiones)
Para cada clip soportado:
1. **ffprobe:** duración, resolución, fps nominal y medido, e intervalos entre fotogramas.
2. **Cámara:** ORB + RANSAC como `bgmotion.py` (clase STATIC, SLOW_DRIFT, FOLLOWING, MOVING o EDITED) y detección de cortes de edición.
3. **Señal independiente:** flujo óptico Farneback a ~10 fps sobre la región de más movimiento, integrado y suavizado, con extremos detectados y periodo. Sale un **conteo automático** de ciclos y los instantes de los extremos.
4. **Tarjeta** `cards\<id>.jpg`, UNA imagen por clip:
   - una tira de 8–12 fotogramas repartidos por el clip, con su tiempo impreso (más densa si el periodo es corto);
   - debajo, la señal con sus extremos numerados;
   - una línea de texto con id, clase, perfil sugerido, duración, fps, resolución, clase de cámara y conteo automático.
   Debe leerse bien a ~1.400 px de ancho.
5. **Datos:** `auto\<id>.json` con todo lo medido y una fila en `index.tsv`.

**Entrega:** las tarjetas y `index.tsv` de los ~620 clips soportados y las etiquetas mínimas de los ~280 no soportados. Responde con:
- cuántos clips hay por clase;
- el reparto de las clases de cámara;
- el tiempo de CPU que llevó;
- 3 tarjetas de ejemplo (rutas) para que el orquestador valide el formato antes de la fase B.

## Fase B (varios agentes, por tramos de ~60 tarjetas)
Por cada tarjeta del tramo asignado:
1. **Mira la tarjeta.** Si la tira no basta para contar (tempo rápido, oclusión, duda), genera una tira densa de ese clip (5–10 fps) y mírala.
2. **Decide y anota:**
   - usable y sus motivos (cámara, cortes, persona cortada, varias personas o ejercicio distinto);
   - la vista;
   - la fase de inicio;
   - el conteo (full, partial y failed), con `acceptableRange` para los medios ciclos cortados en los bordes, que en estos clips cortos son muy comunes;
   - uncertain si queda duda;
   - el ejercicio real, si no coincide con la clase.
3. **Tiempos de giro:** los del flujo, SOLO si coinciden con lo que se ve, con `timingSource: "flow_auto"` y una precisión de ±0,3 s. Si no, `repTimesS` va vacío.
4. **Escribe** `labels\<id>.json`.

**Al final del último tramo:** `labels\SUMMARY_corpus2.md`, con la tabla por clip, los aptos por perfil y los no aptos con su motivo.

## Particiones (para VT10)
- **UCF101:** cada grupo de grabación `gXX` (`source_group`) va entero a una sola partición.
- **Kaggle:** no hay grupo (`unknown`), y varios clips pueden venir del mismo vídeo o persona. Kaggle va ENTERO a una partición: no se reparte entre ajuste y reserva.
