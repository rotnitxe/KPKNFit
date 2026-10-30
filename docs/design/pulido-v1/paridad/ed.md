# Paridad de profundidad y movimiento · `ed` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 9 · vidrio, 2 · degradados 4 · animaciones 18 · transiciones 34 · transformaciones, 9 · movimiento 41 · escenas del paquete: 30

## Sombras (9)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ed-S01 | `.ed-asi em` | box-shadow: rgb(27, 27, 29) 0px 0px 0px 2px → preset corte-hoja | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | SessionEditorFrameBottomBar.kt:422 | [x] |
| ed-S02 | `.ed-glass` | box-shadow: rgba(0, 0, 0, 0.667) 0px 10px 30px, rgba(255, 255, 255, 0.094) 0px 1px inset → preset isla-vidrio | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | EditorGlass.kt:104 | [x] |
| ed-S03 | `.ed-list.drag .ed-ex.lifted` | box-shadow: rgba(0, 0, 0, 0.867) 0px 24px 48px, rgba(255, 255, 255, 0.15) 0px 0px 0px 1px → preset levantado | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorVisuals.kt:249 | [x] |
| ed-S04 | `.ed-list.drag .ed-ex.settle` | box-shadow: rgba(0, 0, 0, 0) 0px 0px 0px 0px → preset ninguna | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorVisuals.kt:249 | [x] |
| ed-S05 | `.ed-pop` | box-shadow: rgba(0, 0, 0, 0.867) 0px 18px 44px → preset popover | — |  | [ ] |
| ed-S06 | `.ed-range::-webkit-slider-thumb` | box-shadow: rgba(0, 0, 0, 0.667) 0px 2px 8px → preset asa-deslizador | ed-serie-rm, ed-serie-solorpe |  | [ ] |
| ed-S07 | `.ed-sheet` | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | — |  | [ ] |
| ed-S08 | `.ed-wd.comp i` | box-shadow: 0 0 0 1.5px var(--ene) → preset anillo-energia-1p5 | — | SessionEditorFrameWeekSheet.kt:520 | [x] |
| ed-S09 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | DESV-401 | [~] |

## Vidrio, desenfoques y filtros (2)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ed-V01 | `.ed-glass` | backdrop-filter: blur(24px) saturate(1.7) → preset vidrio-editor | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | EditorGlass.kt:118 | [x] |
| ed-V02 | `.ed-pop.glassy` | backdrop-filter: blur(22px) saturate(1.5) → preset vidrio-isla | — | EditorPopovers.kt:141 | [x] |

## Degradados y resplandores (4)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ed-G01 | `.ed-range::-webkit-slider-runnable-track` | background-image: linear-gradient(90deg,var(--ink) var(--p,50%),#ffffff1f var(--p,50%)) → preset relleno-horizontal-crema-1 | ed-serie-rm, ed-serie-solorpe |  | [ ] |
| ed-G02 | `.ed-topfade` | background-image: linear-gradient(rgb(0, 0, 0) 20%, rgba(0, 0, 0, 0)) → preset velo-negro-sale-1 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | SessionEditorScreen.kt:877 | [x] |
| ed-G03 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | DESV-401 | [~] |
| ed-G04 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | DESV-401 | [~] |

## Animaciones (@keyframes) (18)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ed-A01 | `.ed-daylbl` | animation: ed-lbl-r · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-derecha · curva estandar · dur 200 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorFrameTopBar.kt:142 | [x] |
| ed-A02 | `.ed-daylbl.back` | animation: ed-lbl-l ·  ·  · retardo 0s · repite 1 · normal · none → preset entrada-izquierda | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorFrameTopBar.kt:142 | [x] |
| ed-A03 | `.ed-delpill.pop` | animation: ed-pop · 0.26s · cubic-bezier(0.3, 1.5, 0.5, 1) · retardo 0s · repite 1 · normal · none → preset rebote · curva resorte · dur 240 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorSwipeDelete.kt:259 | [x] |
| ed-A04 | `.ed-delta` | animation: ed-delta · 1.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset aviso · curva estandar · dur 1300 | — | SessionEditorFrameBottomBar.kt:317 | [x] |
| ed-A05 | `.ed-ex.fresh` | animation: ed-fresh · 0.5s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset despliegue-fila · curva estandar · dur 520 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | DESV-402 | [~] |
| ed-A06 | `.ed-focus.anim, .ed-presets.anim, .ed-tcfg.anim` | animation: ed-in · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 240 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-A07 | `.ed-list.drag .ed-ex.lifted` | animation: ed-lift · 0.34s · cubic-bezier(0.3, 1.5, 0.5, 1) · retardo 0s · repite 1 · normal · forwards → preset rebote · curva resorte · dur 320 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorVisuals.kt:211 | [x] |
| ed-A08 | `.ed-pop` | animation: ed-popin · 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-baja · curva estandar · dur 200 | — |  | [ ] |
| ed-A09 | `.ed-pulse` | animation: ed-ring · 0.46s · ease-out · retardo 0s · repite 1 · normal · forwards → preset onda-anillo · curva salida · dur 460 | — | SessionEditorVisuals.kt:210 | [x] |
| ed-A10 | `.ed-sc.flash` | animation: ed-flash · 0.6s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset destello · curva estandar · dur 600 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-A11 | `.ed-scrim` | animation: ed-fadein · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset fundido-entrada · curva estandar · dur 200 | — |  | [ ] |
| ed-A12 | `.ed-scrim.out` | animation: ed-fadeout · 0.2s · ease · retardo 0s · repite 1 · normal · forwards → preset fundido-salida · curva suave · dur 200 | — |  | [ ] |
| ed-A13 | `.ed-sheet` | animation: ed-up · 0.34s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset hoja-sube · curva estandar · dur 320 | — |  | [ ] |
| ed-A14 | `.ed-sheet.out` | animation: ed-down · 0.24s · cubic-bezier(0.5, 0, 0.9, 0.5) · retardo 0s · repite 1 · normal · forwards → preset hoja-baja · curva cae · dur 240 | — |  | [ ] |
| ed-A15 | `.ed-stv.bump b` | animation: ed-bump · 0.24s · cubic-bezier(0.3, 1.5, 0.5, 1) · retardo 0s · repite 1 · normal · none → preset aparece-escala · curva resorte · dur 240 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-A16 | `.ed-time.tick b` | animation: ed-bump · 0.36s · cubic-bezier(0.3, 1.5, 0.5, 1) · retardo 0s · repite 1 · normal · none → preset aparece-escala · curva resorte · dur 320 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | SessionEditorFrameBottomBar.kt:288 | [x] |
| ed-A17 | `.ed-undo` | animation: ed-fadein · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset fundido-entrada · curva estandar · dur 200 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorSwipeDelete.kt:242 | [x] |
| ed-A18 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | DESV-401 | [~] |

## Transiciones (34)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ed-T01 | `.ed-act` | transition: background · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-T02 | `.ed-addw .chv svg` | transition: transform · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | SessionEditorFrameBottomBar.kt:341 | [x] |
| ed-T03 | `.ed-addw button` | transition: background · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | SessionEditorFrameBottomBar.kt:355 | [x] |
| ed-T04 | `.ed-arrow` | transition: color, transform · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorFrameTopBar.kt:235 | [x] |
| ed-T05 | `.ed-asi` | transition: background · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | SessionEditorFrameBottomBar.kt:410 | [x] |
| ed-T06 | `.ed-bar` | transition: opacity, transform · 0.22s, 0.3s · ease, cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva suave/estandar · dur 200/280 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | SessionEditorFrameBottomBar.kt:119 | [x] |
| ed-T07 | `.ed-cardw` | transition: grid-template-rows · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 280 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | DESV-404 | [~] |
| ed-T08 | `.ed-chip` | transition: border-color, background · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-T09 | `.ed-dash` | transition: border-color, color · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-T10 | `.ed-daybtn svg` | transition: transform · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorFrameTopBar.kt:121 | [x] |
| ed-T11 | `.ed-delpill .lb` | transition: opacity, max-width · 0.16s, 0.2s · ease, cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva suave/estandar · dur 160/200 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorSwipeDelete.kt:162 | [x] |
| ed-T12 | `.ed-delpill svg` | transition: transform · 0.18s · cubic-bezier(0.3, 1.5, 0.5, 1) · retardo 0s → preset curva resorte · dur 160 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorSwipeDelete.kt:124 | [x] |
| ed-T13 | `.ed-ex.gone` | transition: height, opacity · 0.26s, 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1), ease · retardo 0s, 0s → preset curva estandar/suave · dur 240/200 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorScreen.kt:825 | [x] |
| ed-T14 | `.ed-ib` | transition: background · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorGlass.kt:208 | [x] |
| ed-T15 | `.ed-ib2 svg.chev` | transition: transform · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 240 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorExerciseRow.kt:146 | [x] |
| ed-T16 | `.ed-list.drag .ed-ex` | transition: transform · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 240 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorScrollRenderer.kt:647 | [x] |
| ed-T17 | `.ed-list.drag .ed-ex.lifted` | transition: box-shadow, background · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | DESV-403 | [~] |
| ed-T18 | `.ed-list.drag .ed-ex.settle` | transition: transform, box-shadow, background · 0.34s, 0.3s, 0.3s · cubic-bezier(0.3, 1.5, 0.5, 1), ease, ease · retardo 0s, 0s, 0s → preset curva resorte/suave · dur 320/280 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | DESV-403 | [~] |
| ed-T19 | `.ed-mi` | transition: background · 0.14s · ease · retardo 0s → preset curva suave · dur 140 | — |  | [ ] |
| ed-T20 | `.ed-presets button` | transition: background, color · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | ed-serie-descanso |  | [ ] |
| ed-T21 | `.ed-row.out` | transition: transform · 0.22s · cubic-bezier(0.5, 0, 0.9, 0.5) · retardo 0s → preset curva cae · dur 200 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorSwipeDelete.kt:235 | [x] |
| ed-T22 | `.ed-row.snap` | transition: transform · 0.34s · cubic-bezier(0.3, 1.5, 0.5, 1) · retardo 0s → preset curva resorte · dur 320 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorSwipeDelete.kt:251 | [x] |
| ed-T23 | `.ed-sc` | transition: border-color, background, transform · 0.18s, 0.18s, 0.18s · ease, ease, cubic-bezier(0.3, 1.5, 0.5, 1) · retardo 0s, 0s, 0s → preset curva suave/resorte · dur 160 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-T24 | `.ed-seg button` | transition: background, color · 0.2s, 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1), ease · retardo 0s, 0s → preset curva estandar/suave · dur 200 | — |  | [ ] |
| ed-T25 | `.ed-slot` | transition: transform · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 240 | — | SessionEditorVisuals.kt:280 | [x] |
| ed-T26 | `.ed-sm button` | transition: background, color · 0.18s, 0.18s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-T27 | `.ed-st` | transition: border-color · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-T28 | `.ed-stb` | transition: background, color · 0.12s, 0.12s · ease, ease · retardo 0s, 0s → preset curva suave · dur 120 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-T29 | `.ed-time` | transition: background · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | SessionEditorFrameBottomBar.kt:248 | [x] |
| ed-T30 | `.ed-tp` | transition: border-color · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-T31 | `.ed-tp svg` | transition: transform · 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-T32 | `.ed-tq` | transition: border-color, background · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | ed-serie-fallo |  | [ ] |
| ed-T33 | `.ed-unit` | transition: background · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-T34 | `.ed-unit svg` | transition: transform · 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |

## Transformaciones, máscaras y mezcla (9)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ed-X01 | `.ed-addw .chv[aria-expanded="true"] svg` | transform: rotate(180deg) | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-barra | SessionEditorFrameBottomBar.kt:390 | [x] |
| ed-X02 | `.ed-daybtn[aria-expanded="true"] svg` | transform: rotate(180deg) | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorFrameTopBar.kt:158 | [x] |
| ed-X03 | `.ed-delpill.armed svg` | transform: scale(1.22) | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorSwipeDelete.kt:155 | [x] |
| ed-X04 | `.ed-ex.open .ed-ib2 svg.chev` | transform: rotate(180deg) | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorExerciseRow.kt:231 | [x] |
| ed-X05 | `.ed-list.drag .ed-ex` | will-change: transform | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionEditorScrollRenderer.kt:659 | [x] |
| ed-X06 | `.ed-row` | will-change: transform | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | EditorSwipeDelete.kt:98 | [x] |
| ed-X07 | `.ed-tp[aria-expanded="true"] svg` | transform: rotate(180deg) | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |
| ed-X08 | `.ed-undo .cd i` | transform: scaleX(var(--cd,1)) | ed-lista, ed-lista-scroll, ed-dia, ed-pildora-mas, ed-anadir, ed-borrar | SessionExerciseUndoNotice.kt:124 | [x] |
| ed-X09 | `.ed-unit[aria-expanded="true"] svg` | transform: rotate(180deg) | ed-serie, ed-serie-carga, ed-serie-rango, ed-serie-intensidad, ed-serie-fallo, ed-serie-descanso |  | [ ] |

## Movimiento y estilos hechos en JavaScript (41)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ed-J01 | `scenes-ed.js:106` | declaración CSS dentro del marcado · parts.push(`<div class="ed-focus" style="animation:none"><div class="ed-fh"><b>Aproximación ${ex.warmSel+1}</b><div class="nav"><button data-a="wnav"  | — |  | [ ] |
| ed-J02 | `scenes-ed.js:245` | declaración CSS dentro del marcado · return `<button class="ed-time" data-a="time" aria-label="${desc}"><svg class="ed-rg" width="32" height="32" viewBox="0 0 32 32" aria-hidden="true"><c | — | SessionEditorFrameBottomBar.kt:239 | [x] |
| ed-J03 | `scenes-ed.js:324` | declaración CSS dentro del marcado · const sec=(lbl,val,presets,key)=>`<div class="ed-sec"><span class="ed-eyebrow">${lbl}</span><div class="ed-st ed-restf" data-fld="${key}"><button clas | — |  | [ ] |
| ed-J04 | `scenes-ed.js:380` | setTimeout · if(delta){const d=document.createElement('span');d.className='ed-delta'+(delta<0?' neg':'');d.textContent=(delta>0?'+':'−')+Math.abs(delta)+' min';t.a | — | SessionEditorFrameBottomBar.kt:131 | [x] |
| ed-J05 | `scenes-ed.js:386` | setTimeout · if(['day','modo','exmenu','restex','apply'].includes(l.kind)){l.out=true;drawLayer();setTimeout(()=>{if(st.layer===l){st.layer=null;drawLayer();if(cb) | — |  | [ ] |
| ed-J06 | `scenes-ed.js:425` | setTimeout · const sv=root.querySelector(`[data-card="${ex.id}"] [data-scrub="${f}"]`);if(sv){sv.classList.add('bump');setTimeout(()=>sv.classList.remove('bump'),2 | — |  | [ ] |
| ed-J07 | `scenes-ed.js:438` | setTimeout · if(!ex.open){const c=root.querySelector(`[data-card="${ex.id}"]`);setTimeout(()=>{if(!ex.open&&c)c.innerHTML=''},320)} | — |  | [ ] |
| ed-J08 | `scenes-ed.js:452` | WAAPI .animate() · case 'smart':{t.animate([{opacity:.4},{opacity:1}],{duration:240});return} | — |  | [ ] |
| ed-J09 | `scenes-ed.js:467` | setInterval · st.timing.iv=setInterval(()=>{if(!root.isConnected\|\|!st.timing){clearInterval(st.timing&&st.timing.iv);return}st.timing.left--;if(st.timing.left<=0){c | — |  | [ ] |
| ed-J10 | `scenes-ed.js:493` | WAAPI .animate() · case 'back':t.animate([{transform:'scale(.9)'},{transform:'none'}],{duration:200});return; | — | SessionEditorFrameTopBar.kt:105 | [x] |
| ed-J11 | `scenes-ed.js:498` | WAAPI .animate() · sc.animate([{opacity:.0,transform:`translateX(${d*40}px)`},{opacity:1,transform:'none'}],{duration:240,easing:'cubic-bezier(.2,.8,.2,1)'}); | — | SessionEditorScreen.kt:773 | [x] |
| ed-J12 | `scenes-ed.js:517` | WAAPI .animate() · case 'time':case 'asistente':{t.animate([{transform:'scale(.94)'},{transform:'none'}],{duration:220});return} | — | SessionEditorFrameBottomBar.kt:248 | [x] |
| ed-J13 | `scenes-ed.js:546` | estilo en línea desde JS · pill.style.opacity=a>4?1:0;pill.style.width=Math.max(0,a-10)+'px'; | — | EditorSwipeDelete.kt:323 | [x] |
| ed-J14 | `scenes-ed.js:548` | setTimeout · if(armed&&!pill.classList.contains('armed')){pill.classList.add('armed','pop');setTimeout(()=>pill.classList.remove('pop'),280)} | — | EditorSwipeDelete.kt:311 | [x] |
| ed-J15 | `scenes-ed.js:551` | estilo en línea desde JS · const swipeTo=(exEl,dx)=>{const row=exEl.querySelector('[data-row]'),wrap=exEl.querySelector('.ed-swwrap');const a=rub(Math.max(0,-dx));row.classList. | — | EditorSwipeDelete.kt:313 | [x] |
| ed-J16 | `scenes-ed.js:553` | estilo en línea desde JS · clearTimeout(ex._to);const h=exEl.offsetHeight;exEl.style.height=h+'px';exEl.classList.add('gone');exEl.getBoundingClientRect();exEl.style.height='0px | — | SessionEditorScreen.kt:824 | [x] |
| ed-J17 | `scenes-ed.js:554` | setTimeout · setTimeout(()=>{const i=st.exs.indexOf(ex);if(i>=0)st.exs.splice(i,1);const ci=st.cardio.indexOf(ex);if(ci>=0)st.cardio.splice(ci,1);exEl.remove();if( | — | SessionEditorScreen.kt:822 | [x] |
| ed-J18 | `scenes-ed.js:562` | estilo en línea desde JS · row.classList.remove('snap');row.classList.add('out');row.style.transform=`translateX(-${wrap.offsetWidth+8}px)`; | — | EditorSwipeDelete.kt:235 | [x] |
| ed-J19 | `scenes-ed.js:563` | estilo en línea desde JS · pill.style.width=(wrap.offsetWidth-16)+'px';pill.style.transition='width .22s cubic-bezier(.2,.8,.2,1)'; | — | EditorSwipeDelete.kt:236 | [x] |
| ed-J20 | `scenes-ed.js:564` | setTimeout · setTimeout(()=>{ | — | EditorSwipeDelete.kt:237 | [x] |
| ed-J21 | `scenes-ed.js:565` | estilo en línea desde JS · const u=wrap.querySelector('.ed-undo');u.classList.add('show');pill.style.opacity=0; | — | EditorSwipeDelete.kt:229 | [x] |
| ed-J22 | `scenes-ed.js:566` | estilo en línea desde JS · const i=u.querySelector('.cd i');i.style.transition='none';i.style.setProperty('--cd',1);i.getBoundingClientRect();i.style.transition='transform 5s li | — | SessionExerciseUndoNotice.kt:70 | [x] |
| ed-J23 | `scenes-ed.js:569` | setTimeout · ex._to=setTimeout(()=>{if(st.pending&&st.pending.id===ex.id){st.pending.done=true;finalize(ex,exEl)}},5230); | — | DESV-405 | [~] |
| ed-J24 | `scenes-ed.js:574` | estilo en línea desde JS · u.classList.remove('show');pill.style.transition='none';pill.style.width='0px';pill.style.opacity=0;pill.classList.remove('armed'); | — | EditorSwipeDelete.kt:249 | [x] |
| ed-J25 | `scenes-ed.js:575` | estilo en línea desde JS · row.classList.remove('out');row.classList.add('snap');row.style.transform='translateX(0)'; | — | EditorSwipeDelete.kt:246 | [x] |
| ed-J26 | `scenes-ed.js:581` | estilo en línea desde JS · row.classList.add('snap');row.style.transform='translateX(0)'; | — | EditorSwipeDelete.kt:289 | [x] |
| ed-J27 | `scenes-ed.js:582` | setTimeout · const pill=wrap.querySelector('.ed-delpill');pill.style.transition='width .3s var(--ed-ease),opacity .2s';pillSet(wrap,0);setTimeout(()=>{pill.style.t | — | DESV-406 | [~] |
| ed-J28 | `scenes-ed.js:617` | estilo en línea desde JS · items.forEach((c,i)=>{c.style.transition='none';c.style.height=hs[i]+'px';c.style.transform=`translateY(${tops[i]}px)`}); | — | SessionEditorScrollRenderer.kt:699 | [x] |
| ed-J29 | `scenes-ed.js:618` | estilo en línea desde JS · list.getBoundingClientRect();items.forEach(c=>c.style.transition=''); | — | SessionEditorScrollRenderer.kt:800 | [x] |
| ed-J30 | `scenes-ed.js:619` | estilo en línea desde JS · const idx=items.indexOf(exEl),slot=document.createElement('div');slot.className='ed-slot';slot.style.height=hs[idx]+'px';slot.style.transform=`transla | — | SessionEditorVisuals.kt:270 | [x] |
| ed-J31 | `scenes-ed.js:624` | setTimeout · dr.raf=setTimeout(dragLoop,16); | — | SessionEditorScreen.kt:665 | [x] |
| ed-J32 | `scenes-ed.js:631` | estilo en línea desde JS · dr.exEl.style.transform=`translateY(${top}px)`; | — | SessionEditorScreen.kt:519 | [x] |
| ed-J33 | `scenes-ed.js:636` | estilo en línea desde JS · others.forEach((c,n)=>{if(n===j){slotTop=a;a+=hd}c.style.transform=`translateY(${a}px)`;a+=dr.hs[dr.items.indexOf(c)]}); | — | SessionEditorScrollRenderer.kt:636 | [x] |
| ed-J34 | `scenes-ed.js:638` | estilo en línea desde JS · dr.slot.style.transform=`translateY(${slotTop}px)`;dr.slotTop=slotTop;dr.curTop=top; | — | SessionEditorVisuals.kt:280 | [x] |
| ed-J35 | `scenes-ed.js:646` | setTimeout · dr.raf=setTimeout(dragLoop,16); | — | SessionEditorScreen.kt:665 | [x] |
| ed-J36 | `scenes-ed.js:650` | estilo en línea desde JS · d.exEl.classList.remove('lifted');d.exEl.classList.add('settle');d.exEl.style.transform=`translateY(${d.slotTop}px)`; | — | SessionEditorScreen.kt:519 | [x] |
| ed-J37 | `scenes-ed.js:653` | setTimeout · setTimeout(()=>{ | — | SessionEditorScreen.kt:524 | [x] |
| ed-J38 | `scenes-ed.js:664` | setTimeout · const timer=setTimeout(()=>{started=true;root.__gest=Date.now();dragBegin(exEl,sy)},210); | — | EditorExerciseRow.kt:77 | [x] |
| ed-J39 | `scenes-ed.js:675` | setTimeout · const sleep=ms=>new Promise(r=>setTimeout(r,ms)); | — | DESV-407 | [~] |
| ed-J40 | `scenes-ed.js:676` | setTimeout · const raf=()=>new Promise(r=>setTimeout(r,16)); | — | DESV-407 | [~] |
| ed-J41 | `scenes-ed.js:699` | setTimeout · freezeUndo(){const exEl=root.querySelectorAll('.ed-ex[data-kind=str]')[2];commitDelete(exEl);setTimeout(()=>{const i=exEl.querySelector('.cd i');i.sty | — | DESV-407 | [~] |

## @keyframes usados (16)

```css
@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes ed-lbl-r { 0% { transform: translateX(26%); opacity: 0; } 100% { transform: none; opacity: 1; } }
```

```css
@keyframes ed-lbl-l { 0% { transform: translateX(-26%); opacity: 0; } 100% { transform: none; opacity: 1; } }
```

```css
@keyframes ed-pop { 0% { transform: scale(0.96); } 50% { transform: scale(1.04); } 100% { transform: none; } }
```

```css
@keyframes ed-fadein { 0% { opacity: 0; } 100% { opacity: 1; } }
```

```css
@keyframes ed-fresh { 0% { background: rgba(242, 238, 230, 0.12); max-height: 0px; opacity: 0; } 30% { max-height: 140px; } 100% { background: var(--module); max-height: 200px; opacity: 1; } }
```

```css
@keyframes ed-lift { 0% { scale: 1; } 35% { scale: 1.06; } 100% { scale: 1.025; } }
```

```css
@keyframes ed-ring { 0% { box-shadow: rgba(242, 238, 230, 0.467) 0px 0px 0px 0px; } 100% { box-shadow: rgba(242, 238, 230, 0) 0px 0px 0px 14px; } }
```

```css
@keyframes ed-flash { 0% { background: rgba(242, 238, 230, 0.2); transform: scale(1.04); } 100% { transform: none; } }
```

```css
@keyframes ed-in { 0% { opacity: 0; transform: translateY(6px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes ed-bump { 0% { transform: scale(0.86); } 100% { transform: none; } }
```

```css
@keyframes ed-delta { 0% { opacity: 0; transform: translateY(8px); } 18% { opacity: 1; transform: none; } 75% { opacity: 1; } 100% { opacity: 0; transform: translateY(-8px); } }
```

```css
@keyframes ed-fadeout { 100% { opacity: 0; } }
```

```css
@keyframes ed-up { 0% { transform: translateY(100%); } 100% { transform: none; } }
```

```css
@keyframes ed-down { 100% { transform: translateY(100%); } }
```

```css
@keyframes ed-popin { 0% { opacity: 0; transform: scale(.92) translateY(var(--dy,-6px)); } 100% { opacity: 1; transform: none; } }
```
