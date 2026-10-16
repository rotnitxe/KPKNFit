# LV2 · El relator: simetría, una sola frase con brillo y acciones con animación (puntos 2 y 3)

Rama `screens/lv2`, worktree `C:\kw\u9`, desde `feat/sessions-v1` `41f8e6653`. Lee antes:
- `LV_COMUN.md` y `LV_COMUN2.md` (las leyes del usuario y cómo se usan los prototipos);
- `docs/screens-v1/briefs/LR1g.md`: los momentos del relator y su espaciado;
- `docs/screens-v1/briefs/LR1h.md`: las frases;
- **los prototipos aprobados** (mandan sobre el aspecto):
  - `docs/screens-v1/proposals/sesion-en-vivo/H2-RelatorAcciones.dc.html`: la acción dentro de la frase y su animación;
  - `docs/screens-v1/proposals/sesion-en-vivo/H5-RelatorMomentos.dc.html`: el relator solo con animación, sin texto.

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

## 3. Una sola frase, brillo continuo y acciones que «trabajan» (H2)
Leyes del usuario (16-oct): el prototipo anterior, con caja blanca y «APLÍCALO», fue rechazado. Ahora:

**3a. La frase es UNA.** La acción es parte de la oración, no un botón:
- mismo tamaño, misma línea base e interlineado que el texto, y el párrafo sigue centrado;
- se distingue SOLO por: peso SemiBold (600), tinta un poco más clara (`#EDE6DA`) y un subrayado punteado de acento
  (`SymbolPalette.musculo` al 90 %, 2 dp de grosor, 5 dp por debajo de la línea base);
- **prohibido**: fondo o caja, borde, píldora, «Aplícalo», mayúsculas, iconos;
- objetivo táctil de 48 dp con un área invisible que no mueve el texto (`pointerInput` sobre los límites del tramo con margen).

**3b. Brillo continuo sobre TODA la frase, acción incluida.**
- Un único barrido de luz que recorre el párrafo entero como si fuera una sola superficie (en H2 es
  `background-attachment: fixed`): el tramo de acción NO tiene su propio barrido desfasado.
- Tinta base `#F2EEE6` al 58 %, cresta `#FFFFFF`; ancho de la cresta ≈ 180 dp; ciclo de 5,4 s (barrido ≈ 3 s y pausa).
- En Compose: `Brush.linearGradient` animado sobre el `TextStyle` (`brush =`) con el mismo desplazamiento para todos los tramos
  del `AnnotatedString`, en coordenadas del párrafo.
- Con «reducir movimiento», fuera de pantalla o en segundo plano: tinta fija al 80 % (la acción al 100 %), sin barrido.

**3c. Al pulsar, la acción «trabaja» con personajes.** Tres fases, como H2:
1. **En reposo:** la frase con su acción.
2. **Trabajando** (≈ 1,9 s, una sola vez): la frase cambia a la que describe lo que pasa («El equipo está cargando los discos de
   95 kg…») con el barrido más rápido (1,4 s), y en la franja de abajo del relator (46 dp de alto, a todo el ancho) corre una
   animación de personajes de trazo fino (1,8 dp, tinta y acento), dibujada en `Canvas`:
   - cargas (`APPLY_SUGGESTED_LOAD`, `ADJUST_LOAD`): dos figuras que cruzan andando y llevan un disco entre ambas, y otra que aprieta
     con una llave al final (H2);
   - descanso (`START_REST`, `EXTEND_REST`): una figura que gira un reloj de arena;
   - saltar calentamiento (`SKIP_REMAINING_WARMUPS`): una figura que salta una valla;
   - las acciones que solo abren algo (`OPEN_*`, `CAPTURE_MEDIA`) NO tienen fase de trabajo: abren su hoja al instante.
3. **Hecho:** la frase de resultado («Listo: la serie 1 va con 95 kg. Si te arrepientes, vuelve a 92,5.»), un destello breve
   (anillo que se expande y rayos verdes `#43D18C`, 0,9 s) y, en la tarjeta, la cifra cambiada con un «pop» (escala 1 → 1,18 → 1,
   0,5 s).
- **Fiabilidad (manda):** el EFECTO se aplica en el ViewModel en el momento del toque, no al acabar la animación. La animación es
  solo narración: si la serie cambia, se registra o la pantalla se va, se corta sin dejar nada a medias. No admite un segundo
  toque mientras trabaja.
- **Deshacer:** solo para las acciones de carga, con el valor anterior guardado en el momento del toque. «vuelve a 92,5» deja la
  carga exacta de antes (el mismo tipo de carga y la misma unidad). Caduca cuando la serie se registra o cambia de serie.
- **Si la acción falla:** vuelve a la frase en reposo, sin destello.

**3d. El relator solo con animación, sin texto (H5).** En estos momentos la franja muestra una animación y ninguna frase:
- descanso sin nada que decir: la onda de respiración;
- récord recién registrado: la celebración con confeti (una vez, ≈ 1,5 s, después vuelve la frase);
- preparación antes de la primera serie: la figura que se agacha con los puntos.
Mira cuándo existen hoy esos momentos en `domain/relator` (sin tocar las frases): si un momento no existe, no lo inventes y
anótalo. La altura del relator NO cambia entre texto y animación. TalkBack lee la frase que habría (o «Descansando»,
«Nuevo récord», «Preparándote»).

**3e. Que cada acción funcione.**
- Revisa una a una las de `RelatorActionKind`: `APPLY_SUGGESTED_LOAD`, `ADJUST_LOAD`, `START_REST`, `EXTEND_REST`,
  `SKIP_REMAINING_WARMUPS`, `OPEN_REPLACE`, `OPEN_READINESS`, `OPEN_TECHNIQUE`, `OPEN_HISTORY`, `CAPTURE_MEDIA` y `OPEN_ALBUM`.
- Para cada una, una prueba que pulsa el tramo y comprueba el efecto real (la carga aplicada en la serie, +15 s en el descanso, la
  hoja abierta, etc.) y el paso por «trabajando» y «hecho» cuando corresponda. La de carga prueba también el deshacer.
- Si alguna no hace lo que dice, arréglala en su origen y anótalo en el informe.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.workout.*` y `com.example.kpkn.domain.relator.*`.
- **Nuevas:** la de simetría (en la hoja de render), una por acción, el deshacer, y las del brillo (no corre con «reducir
  movimiento»).
- **Hoja de render** `render/LiveSessionRelatorRenderSheetTest`, al 100 % y al 130 %:
  - texto solo y texto con una acción dentro de la frase (dos fotogramas del brillo, que se vea que cruza la acción);
  - texto de dos líneas con dos acciones;
  - la fase «trabajando» (dos fotogramas de los personajes) y la fase «hecho» con el destello;
  - los tres momentos solo con animación.

## Permitido y prohibido
- **Permitido:**
  - `WorkoutLiveRelator*.kt` y archivos nuevos del relator (por ejemplo `WorkoutLiveRelatorScenes.kt` para los personajes);
  - en `WorkoutViewModel`, solo el despacho de las acciones del relator y el deshacer de la carga;
  - en `WorkoutV2Body.kt`, solo el hueco del relator;
  - en la tarjeta (`SetExecutionCard.kt`), SOLO el «pop» de la cifra al cambiar la carga desde el relator (un parámetro nuevo
    opcional); nada más de la tarjeta;
  - archivos nuevos y pruebas.
- **Prohibido** (otros paquetes trabajan a la vez): la cabecera y la hoja de estado previo, `WorkoutSetPager.kt`, el descanso, la
  barra inferior y sus hojas, las hojas de cierre, y las frases de `domain/relator`.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lv2 bash /c/kw/tools/gradle_slot.sh /c/kw/u9/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.relator.*" --tests "*LiveSessionRelatorRenderSheet*" > /c/kw/logs/lv2-<n>.log 2>&1`

Tope: 5 corridas. Mira tus PNG antes de cada nueva corrida.

## Commits
- `fix(workout): el relator a la misma distancia de la cabecera y de la tarjeta (LV2)`.
- `feat(workout): el relator es una sola frase con brillo continuo y sus acciones trabajan con personajes (LV2)`.
- `feat(workout): el relator solo con animación en el descanso, el récord y la preparación (LV2)`.
- `fix(workout): <cada acción arreglada> (LV2)`.
