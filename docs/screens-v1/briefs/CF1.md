# CF1 · La ficha del ejercicio: los implementos sobre la foto y los músculos a la vista (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/screens-v1/briefs/LR1_COMUN.md` (la piel nueva).

**Lo que dijo el usuario.** El catálogo de ejercicios está bastante bien. La ficha de cada ejercicio tiene dos problemas:
- los chips para cambiar de implemento están escondidos: hay que bajar, pasada la descripción, para cambiarlo. Pueden ir encima de
  la foto, como píldoras de vidrio flotantes con un indicador que sigue el toque;
- el involucramiento muscular está demasiado escondido (hoy va plegado al final).

**Regla de oro (la aprendimos a golpes):** REFINAR, no quitar. Todo lo que tiene la ficha se queda: la foto, el título, la línea de
músculo/material/patrón, la descripción, la configuración, los músculos, las articulaciones y «Añadir/Quitar». Solo cambia DÓNDE y
CÓMO se ven estas dos cosas.

Archivo: `screens/sessioneditor/components/ExerciseCatalogInfoDialog.kt`:
- `CatalogDetailContent`, ~L149;
- `CatalogExerciseImageCarousel`, ~L278;
- `CatalogInvolvementAccordions`, ~L346;
- `MuscleInvolvementSection`, ~L457.

Mira con Read `C:\kw\shots\screens\se9-qa\s30_y2_ver_ficha.png` (la ficha hoy en el teléfono).

## 1. Los implementos, sobre la foto
- **Qué eje:** el de la configuración que cambia el implemento, el mismo que mueven las páginas de la foto
  (`imageVariants[].implementation`).
- **Cómo se ve:** una fila de píldoras de VIDRIO flotando dentro de la foto, abajo, a 12 dp del borde:
  - vidrio con `kpknGlassStyle` de `ui/components/KpknGlass.kt`: la foto es la fuente del desenfoque y la fila, el efecto. Si el
    vidrio no se puede, usa el respaldo opaco que ya tenga esa pieza;
  - filete blanco al 12 %;
  - texto Inter 13 sp en tinta;
  - la opción elegida, en negro sobre un indicador de tinta (#F2EEE6) que se DESLIZA hasta la píldora tocada (resorte de unos
    300 ms). Mira cómo lo hace `ui/design/KpknSegmentedControl.kt` y reutilízalo si encaja;
  - alto visual de 36–40 dp, zona táctil ≥ 48 dp;
  - si no caben, la fila se desplaza en horizontal con un fundido en el borde;
  - con un solo implemento, no se pinta.
- **Sincronía en los dos sentidos:**
  - tocar una píldora lleva la foto a su página (animado) y elige ese implemento, por los mismos caminos de hoy (`onSelectOption`
    y `onImplementationSettled`);
  - deslizar la foto mueve el indicador.
  - Con «reducir movimiento», sin animación.
- **Lo demás:**
  - los puntos de página (`PagerDots`) sobran: las píldoras ya dicen dónde estás;
  - en el módulo «Configuración» quedan los OTROS ejes (agarre, ángulo…) y la línea de configuración. El eje del implemento no se
    repite ahí;
  - si un implemento no tiene foto, sale igual como píldora y la foto muestra lo que muestra hoy sin imagen.

## 2. Los músculos, a la vista
- Justo debajo del título y su línea, ANTES de la descripción, un módulo «Músculos» siempre abierto:
  - los músculos que trabajan, de más a menos, cada uno con su barra horizontal y su aporte por serie en % (los mismos datos que
    `MuscleInvolvementSection`);
  - la barra en el color de Músculos (`SymbolPalette.musculo`), sobre una pista blanca al 8 %;
  - los 4 primeros, con «Ver todos» (`KpknTextAction`) si hay más, que despliega el resto en el sitio;
  - cifras con espacio duro antes de «%».
- La descripción queda debajo, recortada a 4 líneas, con «Leer más» / «Leer menos».
- Las articulaciones siguen como hoy, en su módulo plegable, al final.

## Pruebas
- `CatalogDetailContentTest` y `com.example.kpkn.screens.sessioneditor.*` siguen en verde.
- **Nuevas** (Compose, Robolectric):
  - tocar la píldora de otro implemento llama a `onSelectOption` con ese implemento y lleva la foto a su página;
  - con un implemento no hay fila de píldoras;
  - el módulo de músculos sale antes que la descripción y abierto;
  - «Ver todos» despliega el resto.
- **Hoja de render** `render/CatalogDetailRenderSheetTest` (ya existe: añade escenas), a 360 dp, al 100 % y al 130 %. PNG en
  `C:/kw/shots/screens/cf1`:
  - la ficha con 3 implementos (el del medio elegido);
  - con 1 implemento;
  - con 7 músculos (para ver «Ver todos»);
  - y la de hoy, para comparar.

## Permitido y prohibido
- **Permitido:**
  - `screens/sessioneditor/components/ExerciseCatalogInfoDialog.kt`;
  - `ExerciseCatalogV2Info.kt`, solo si el modelo necesita marcar qué eje es el del implemento;
  - las pruebas y la hoja de render.
- **Prohibido:**
  - el resto de `screens/sessioneditor/`: el editor va a restaurarse a la PR11 en otro paquete, y el catálogo (la lista) se queda
    como está;
  - `ui/design` y `ui/components`, salvo usarlos;
  - push;
  - el teléfono y el emulador.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/cf1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*CatalogDetail*" > /c/kw/logs/screens-cf1-<n>.log 2>&1`

- Las corridas intermedias pueden ir sin `:app:compileBaseDebugAndroidTestKotlin` y solo con `--tests "*CatalogDetail*"`.
- Mira los PNG antes de lanzar otra corrida. Tope: 4 corridas.
- Sin `.dbg`.

## Commit
- `style(catalog): los implementos van sobre la foto como píldoras de vidrio con indicador (CF1)`.
- `style(catalog): los músculos, a la vista antes de la descripción (CF1)`.
- `test(catalog): escenas nuevas de la ficha (CF1)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió y dónde.
- Los PNG, con tu comparación con la ficha de hoy.
- Pruebas: número y resultado.
