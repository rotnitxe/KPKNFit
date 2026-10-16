# LV2 · El relator: simetría, brillo pensante y acciones como botones (puntos 2 y 3)

Rama y worktree: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `f6da01f52`). Lee antes:
- `LV_COMUN.md`;
- `docs/screens-v1/briefs/LR1g.md`: los momentos del relator y su espaciado;
- `docs/screens-v1/briefs/LR1h.md`: las frases.

Archivos: `screens/workout/WorkoutLiveRelatorBar.kt`, `WorkoutLiveRelatorHost.kt`, `WorkoutLiveRelator*.kt`; la acción la despacha
`WorkoutViewModel`.

## 2. El relator, centrado de verdad
- **Lo que pasa:** en el teléfono (360 × 880 dp), el hueco entre los chips de la cabecera (tiempo y etiquetas) y el texto del relator
  es mayor que el hueco entre el texto y el borde de arriba VISIBLE de la tarjeta de la serie.
- **Probable causa:** la medida de LR1g usa los límites del diseño y no el borde dibujado. La tarjeta del carrusel (pager) tiene
  relleno o escala, y las tarjetas vecinas van con «peek».
- **Arreglo:** los dos huecos iguales (±2 dp), medidos en lo dibujado, en estos casos:
  - serie con 1 y con 2 líneas;
  - descanso en página;
  - letra al 100 % y al 130 %.
- **Prueba:** la medida va en la hoja de render, como hizo LR1g (`medidas_*.txt` y aserción).

## 3. Brillo «pensante» y acciones como botones
**3a. Brillo (shimmer).**
- **Qué es:** un barrido de luz muy sutil sobre el TEXTO del relator, para que se sienta que hay algo pensando detrás.
- **Ritmo:** cada barrido dura ~2,4 s y después hay ~3 s de pausa.
- **Cómo se ve:** contraste bajo. La tinta va de #F2EEE6 al 70 % y la cresta, al 100 %.
- **Dónde no va:** ni sobre las acciones ni con «reducir movimiento». Tampoco fuera de pantalla ni en segundo plano.
- **Ojo:** LR1g quitó un brillo que cansaba. Este tiene que ser más tenue y con pausa.

**3b. Acciones como botones blancos.**
- **Lo que pasa:** hoy son tramos de texto pulsables mezclados con el texto, y no casan.
- **Cómo se ven ahora:**
  - píldoras blancas: fondo `#F2EEE6`, texto `#0B0B0B` en Inter SemiBold 13 sp;
  - 28–32 dp de alto, 12 dp de relleno horizontal y radio completo;
  - el objetivo táctil llega a 48 dp con el relleno.
- **Cómo se colocan:**
  - en la misma línea que el texto si caben; si no, en una línea propia debajo;
  - centradas, con 8 dp entre ellas y el texto;
  - el conjunto, simétrico: alineado al centro vertical y con los mismos márgenes que el texto.

**3c. El proceso de la acción.**
- **Al pulsar:**
  - la píldora pasa a «en curso»: un barrido o un indicador pequeño dentro, y la etiqueta atenuada;
  - no admite un segundo toque;
  - al terminar, un ✓ breve (~600 ms) y desaparece o vuelve a su estado.
- **Si la acción falla:** vuelve a su estado sin ✓ y el relator no cambia.

**3d. Que cada acción funcione.**
- Revisa una a una las de `RelatorActionKind`: `APPLY_SUGGESTED_LOAD`, `ADJUST_LOAD`, `START_REST`, `EXTEND_REST`,
  `SKIP_REMAINING_WARMUPS`, `OPEN_REPLACE`, `OPEN_READINESS`, `OPEN_TECHNIQUE`, `OPEN_HISTORY`, `CAPTURE_MEDIA` y `OPEN_ALBUM`.
- Para cada una:
  - una prueba que pulsa la píldora y comprueba el efecto real (la carga aplicada en la serie, +15 s en el descanso, la hoja abierta,
    etc.) y el paso por «en curso» y ✓;
  - si alguna no hace lo que dice, arréglala en su origen y anótalo en el informe.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.workout.*` y `com.example.kpkn.domain.relator.*`.
- **Nuevas:** la de simetría (en la hoja de render), una por acción y las del brillo (no corre con «reducir movimiento»).
- **Hoja de render** `render/LiveSessionRelatorRenderSheetTest`, al 100 % y al 130 %:
  - texto solo;
  - texto con una acción en la misma línea;
  - texto con dos acciones debajo;
  - la píldora en curso y con ✓;
  - dos fotogramas del brillo.

## Permitido y prohibido
- **Permitido:**
  - `WorkoutLiveRelator*.kt`;
  - en `WorkoutViewModel`, solo el despacho de las acciones del relator;
  - en `WorkoutV2Body.kt`, solo el hueco del relator;
  - archivos nuevos y pruebas.
- **Prohibido** (otros paquetes trabajan a la vez):
  - la cabecera y la hoja de estado previo (LV1);
  - la tarjeta de la serie y `WorkoutSetPager.kt` (LV3);
  - la barra inferior, las hojas de estructura y las de cierre (LV4);
  - las frases de `domain/relator` (LR1h ya terminó).

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lv2 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.relator.*" --tests "*LiveSessionRelatorRenderSheet*" > /c/kw/logs/lv2-<n>.log 2>&1`

Tope: 5 corridas.

## Commits
- `fix(workout): el relator a la misma distancia de la cabecera y de la tarjeta (LV2)`.
- `feat(workout): el relator con brillo pensante y sus acciones como botones con estado en curso (LV2)`.
- `fix(workout): <cada acción arreglada> (LV2)`.
