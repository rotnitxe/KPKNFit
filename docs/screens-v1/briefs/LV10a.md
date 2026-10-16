# LV10a · RINGS luminosos en toda la app e Inicio vivo (I1)

Rama `screens/lv10a`, worktree y commit base: los del mensaje de lanzamiento (salen de `feat/sessions-v1`). Lee antes
`LV_COMUN.md`, `LV_COMUN2.md` (sección «Anillos luminosos»), el mapa `docs/screens-v1/maps/MAPA_anillos_estado_fichas.md` y los
prototipos aprobados `G1-EstadoPrevio.dc.html` (el aspecto del anillo) e `I1-HomeRings.dc.html` (Inicio).

Decisión del usuario (16-oct): «los rings luminosos (degradado + destello) son el diseño DEFINITIVO de los anillos en todas
partes. En Inicio iría más allá: cosas dando vueltas o un personaje saltando entre los rings».

## 1. Un solo estilo: LUMINOUS, bien dibujado
`ui/components/rings/KpknTowerRings.kt`:
- **LUMINOUS pasa a ser el estilo por defecto.** Ningún sitio de la app debe seguir con CLASSIC (si una prueba lo exige, actualízala).
  Mantén el enum por compatibilidad solo si borrarlo complica; no lo uses.
- **Arregla las costuras** que se ven hoy en el estado previo (cortes rectos verticales donde empieza cada arco y el halo
  desalineado): el arco empieza en el extremo derecho de la elipse y avanza en sentido horario; extremos redondeados; el halo
  (mismo color al 22 %, desenfoque ~5 dp) y el arco comparten inicio y fin; el degradado es horizontal por anillo (Columna
  `#5E7EC4 → #B9CFFF`, Músculos `#C7704A → #FFC2A3`, Energía `#C9A04A → #FFE7A8`); pista `#151515`; destello blanco al 55 %,
  corto, que recorre cada anillo cada 6 s con 1,2 s de desfase. Compara tus PNG con G1 al lado.
- Se conserva: `null` = sin calibrar (pista discontinua, sin arco, nunca 100), `from` (animación antes → después del final del
  entreno), `visible`/`highlighted`, `towerRingCenter`, `towerRingBandFractions`, las zonas táctiles de 48 dp, la semántica.
- Desenfoque del halo: `RenderEffect`/`BlurMaskFilter` en API ≥ 31 y un halo de trazo ancho y alfa bajo en API < 31 (minSdk 24),
  sin coste por fotograma (cachea los pinceles).
- Las torres de marca dibujadas a mano (`WelcomeRingsScene`, `PlanPreparingOverlay.BrandTower`,
  `WizardPrologueOverlay.prologueMiniTower`) y los mini-anillos de 16 dp (`MuscleRing`) toman el mismo degradado y halo si es
  directo; si no, anótalo (no reescribas escenas animadas del wizard).
- Quita con cuidado el código muerto de anillos que señala el mapa (`SingleRingCanvas`, `drawAugeRing*` y su import muerto,
  `SessionEstimatedRings`/`EstimatedRingsRow`, el import muerto de `AdjustableRingCompact` en `WorkoutScreen.kt`). No toques
  `RingColors` (lista por índice) ni `augeRingsHostHeightDp`.

## 2. Inicio vivo (I1)
`screens/home/HomeRingsSection.kt` con el aspecto de I1 y el contenido de la app:
- La torre crece y manda en la sección (I1: ~330 × 253 px de referencia → a lo ancho de la pantalla menos los márgenes, con la
  proporción 300:230). Encima «TUS RINGS» con el (i) de hoy (`RingsIntroOverlay(replay = true)`) y el titular del motor; debajo,
  la leyenda de tres columnas «MÚSCULOS 89 % · ENERGÍA 88 % · COLUMNA 82 %» en el orden de la torre visto en G1/I1, cada una con
  su etiqueta pequeña de la app («Sin calibrar», cobertura o `RecoveryBands.labelForScore`), tocable. Mantén los tags
  `home-ring-*`/`home-rings-row-*` en los elementos equivalentes y actualiza las pruebas del orden si cambia.
- «Confianza … · Toca un anillo para ver por qué» se conserva.
- **Vida:**
  - **brillos que orbitan**: dos o tres puntos luminosos por anillo (blanco con halo del color del anillo) que recorren cada
    elipse a distinta velocidad (Energía la más rápida), como I1;
  - **un personaje que salta de anillo en anillo**: figura de trazo fino (cabeza con un arco del color del anillo donde está) que
    salta abajo → medio → arriba y vuelve con un gran salto, con aplastamiento al aterrizar y estiramiento en el aire (keyframes de
    I1, ciclo de 7,5 s) y el anillo donde aterriza destella; saluda con el brazo arriba.
  - **tocar un anillo**: el personaje salta a ese anillo (0,55 s) y DESPUÉS se abre `RingDetailSheet` como hoy
    (`onSelectedChannelChange`). La hoja de detalle no cambia salvo el estilo de su torre.
- Sin programa activo (50 %) o anillos sin calibrar: el personaje sigue, pero no aterriza en un anillo sin calibrar (lo salta).
- «Reducir movimiento», app en segundo plano o sección fuera de pantalla (scroll de Inicio): todo quieto; personaje de pie en el
  anillo de abajo; sin órbitas. Ningún bucle corre fuera de pantalla (usa la visibilidad del elemento en la lista y el ciclo de
  vida).
- `tourAnchor(TourAnchors.HOME_RINGS)` se conserva en la torre.

## Pruebas
- Siguen en verde: `KpknTowerRingsTest`, `KpknTowerRingsSemanticsTest`, `KpknTowerRingsRenderSheetTest`, `HomeRingsSectionTest`,
  `RingDetailSheetTest` (actualiza lo que fije CLASSIC o el orden de filas, explicándolo) y `com.example.kpkn.screens.home.*`.
- Nuevas: que nadie usa CLASSIC; que sin calibrar no hay arco; que el personaje no aterriza en un anillo sin calibrar (lógica
  pura de la secuencia de saltos); que con «reducir movimiento» no hay animación.
- Hoja de render: la torre luminosa a 3 tamaños (Inicio, estado previo, final) con valores altos, bajos y `null`; Inicio a 360 dp,
  100 % y 130 %, con dos fotogramas del personaje (en el aire y aterrizando) y las órbitas.

## Permitido y prohibido
- **Permitido:** `ui/components/rings/*`, `screens/home/HomeRingsSection.kt`, `screens/home/design/*` de anillos, el estilo de la
  torre en `RingDetailSheet.kt`, borrar el código muerto citado, archivos nuevos, pruebas.
- **Prohibido:** el estado previo por dentro (`WorkoutReadinessSheet.kt`: paquete LV10b), el ajuste manual (LV10b), la hoja de
  cierre (`WorkoutFinishHost.kt`: LV13; si el estilo por defecto la cambia sola, bien), el descanso, la tarjeta, el wizard por
  dentro.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lv10a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*KpknTowerRings*" --tests "com.example.kpkn.screens.home.*" --tests "*Rings*RenderSheet*" > /c/kw/logs/lv10a-<n>.log 2>&1`

Tope: 5 corridas. Mira tus PNG antes de cada corrida nueva.

## Commits
- `style(rings): los RINGS luminosos son el único estilo y sin costuras (LV10a)`.
- `feat(home): RINGS vivos en Inicio con brillos que orbitan y un personaje que salta entre anillos (LV10a)`.
