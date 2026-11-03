# Paridad de profundidad y movimiento · `as` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 10 · vidrio, 1 · degradados 5 · animaciones 23 · transiciones 18 · transformaciones, 11 · movimiento 6 · escenas del paquete: 26

## Sombras (10)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-S01 | `.as-cv` | box-shadow: rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset → preset borde-interior | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantTemplatesPane.kt:482` preset borde-interior bajo la portada (como el lienzo: el arte la cubre) | [x] |
| as-S02 | `.as-dec > div` | box-shadow: rgba(0, 0, 0, 0.733) 0px -10px 30px → preset pie-sube | as-pla-vista, as-pla-aplicar, as-pla-aplicando, as-pla-aplicada | `AssistantTemplatesPane.kt:599` preset pie-sube | [x] |
| as-S03 | `.as-empty .g` | box-shadow: rgba(255, 255, 255, 0.15) 0px 0px 0px 1.5px inset → preset borde-interior | as-met-cardio, as-met-vacia, as-sug-vacio, as-sug-nota, as-pla-vacio | `AssistantPieces.kt:468` valor exacto 1,5 px / blanco 15 % (borde-interior con spread 1,5) | [x] |
| as-S04 | `.as-menu` | box-shadow: rgba(0, 0, 0, 0.8) 0px 14px 40px → preset menu | as-pla-mias | `AssistantTemplatesPane.kt:813` preset menu | [x] |
| as-S05 | `.as-sheet` | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantMotion.kt:119` preset hoja; la pinta `KpknSheet.kt` (`kpknRing` fuera + `kpknInset` dentro) | [x] |
| as-S06 | `.as-tlim` | box-shadow: 0 0 0 2px var(--as-mod) → preset corte-hoja | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-sug-lista | `AssistantMetricsPane.kt:432` corte-hoja con el color del módulo (`--as-mod`) | [x] |
| as-S07 | `.as-toast` | box-shadow: rgba(0, 0, 0, 0.8) 0px 12px 34px → preset aviso | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantFrame.kt:156` preset aviso | [x] |
| as-S08 | `.pl-glass` (base) | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset isla-vidrio | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `KpknShadowCatalog.kt:35` (base: `.pl-glass` es de la isla del editor, no del asistente; el preset isla-vidrio está en los cimientos) | [x] |
| as-S09 | `.tok.done` (base) | box-shadow: rgba(67, 209, 140, 0.533) 0px -2px inset → preset subrayado-hecho | as-pla-aplicada | `KpknShadowCatalog.kt:42` (base: `.tok.done` es del editor de fondo, no del asistente) | [x] |
| as-S10 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `KpknShadowCatalog.kt:22` (base: `.tok.on` es del editor de fondo, no del asistente) | [x] |

## Vidrio, desenfoques y filtros (1)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-V01 | `.pl-glass` (base) | backdrop-filter: blur(22px) saturate(1.5) → preset vidrio-isla | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `KpknGlassPresets.kt:44` (base: `.pl-glass`, isla del editor de fondo; la hoja del asistente es plana, sin vidrio) | [x] |

## Degradados y resplandores (5)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-G01 | `.as-foot` | background-image: linear-gradient(rgba(27, 27, 29, 0), rgb(27, 27, 29) 30%) → preset velo-hoja | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantRulesPane.kt:212` preset velo-hoja (también en `AssistantTemplatesPane.kt` el pie de la ficha) | [x] |
| as-G02 | `.as-sk-l i` | background-image: linear-gradient(90deg, rgba(255, 255, 255, 0.04) 20%, rgba(255, 255, 255, 0.11) 50%, rgba(255, 255, 255, 0.04) 80%) → preset brillo-esqueleto-1 | as-sug-carga | `AssistantSuggestionsPane.kt:168` preset brillo-esqueleto-1, tamaño 220 %, repetido | [x] |
| as-G03 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `KpknGradientCatalog.kt:26` (base: `.split7`, del editor de fondo) | [x] |
| as-G04 | `.story .bgart.over::after` (base) | background-image: linear-gradient(rgba(0, 0, 0, 0), rgb(0, 0, 0)) → preset velo-negro-1 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-sug-lista | `KpknGradientCatalog.kt:50` (base: `.story .bgart`, de las historias) | [x] |
| as-G05 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `KpknGradientCatalog.kt:18` (base: `.story .st-week`, de las historias) | [x] |

## Animaciones (@keyframes) (23)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-A01 | `.as-ap.done .after` | animation: asIn · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 280 | as-pla-aplicando, as-pla-aplicada | `AssistantTemplatesPane.kt:733` asIn 0,3 s (`AsAnim.ApplyAfterIn`, dx 0) | [x] |
| as-A02 | `.as-ap.open` | animation: asScrim · 0.2s · ease · retardo 0s · repite 1 · normal · both → preset fundido-entrada · curva suave · dur 200 | as-met-completa, as-pla-filtros, as-pla-aplicando, as-pla-aplicada, as-pla-mias | `AssistantTemplatesPane.kt:708` asScrim 0,2 s ease (`AsAnim.OverlayFade`) | [x] |
| as-A03 | `.as-dec > div` | animation: asRise · 0.36s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset hoja-sube · curva estandar · dur 320 | as-pla-vista, as-pla-aplicar, as-pla-aplicando, as-pla-aplicada | `AssistantTemplatesPane.kt:599` asRise 0,36 s (`AsAnim.DecisionRise`) | [x] |
| as-A04 | `.as-dec.open` | animation: asScrim · 0.2s · ease · retardo 0s · repite 1 · normal · both → preset fundido-entrada · curva suave · dur 200 | as-met-completa, as-pla-filtros, as-pla-vista, as-pla-aplicar, as-pla-aplicando, as-pla-aplicada | `AssistantTemplatesPane.kt:595` asScrim 0,2 s ease | [x] |
| as-A05 | `.as-flash` | animation: asFlash · 0.6s · ease · retardo 0s · repite 1 · normal · none → preset destello · curva suave · dur 600 | — | `AssistantMotion.kt:166` asFlash 0,6 s ease (familia `destello`); lo usan `AsStepper` y `AsPicker` en `AssistantPieces.kt` | [x] |
| as-A06 | `.as-fold.open .as-fb2` | animation: asIn · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantRulesPane.kt:471` asIn 0,22 s (dx 0) | [x] |
| as-A07 | `.as-fp.open` | animation: asIn · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantTemplatesPane.kt:401` asIn 0,22 s (dx 0) | [x] |
| as-A08 | `.as-mbar i` | animation: asGrow · 0.7s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset crece-x · curva estandar · dur 700 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-sug-lista | `AssistantMetricsPane.kt:336` asGrow 0,7 s estándar, origen izquierda, relleno both | [x] |
| as-A09 | `.as-menu.open` | animation: asIn · 0.18s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 160 | as-met-completa, as-pla-filtros, as-pla-aplicando, as-pla-aplicada, as-pla-mias | `AssistantTemplatesPane.kt:812` asIn 0,18 s (dx 0) | [x] |
| as-A10 | `.as-mrow.open .as-mdet` | animation: asIn · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-sug-lista | `AssistantMetricsPane.kt:359` asIn 0,22 s (dx 0) | [x] |
| as-A11 | `.as-pane.on` | animation: asIn · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 240 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantFrame.kt:129` asIn 0,26 s con el desplazamiento por defecto (10 px) | [x] |
| as-A12 | `.as-rg .sp` | animation: asSpin · 1.1s · linear · retardo 0s · repite infinite · normal · none → preset giro · curva lineal · dur 1000 | as-pla-aplicando, as-pla-aplicada | `AssistantTemplatesPane.kt:722` asSpin 1,1 s lineal, infinito; quieto con movimiento reducido | [x] |
| as-A13 | `.as-root.in .as-head, .as-root.in .as-tabs, .as-root.in .as-body` | animation: asContent · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0.16s · repite 1 · normal · both → preset entrada-sube · curva estandar · dur 280 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantMotion.kt:124` contenido 0,3 s con 0,16 s de retardo, 10 px (`KpknSheet.kt`) | [x] |
| as-A14 | `.as-root.in .as-scrim` | animation: asScrim · 0.22s · ease · retardo 0s · repite 1 · normal · both → preset fundido-entrada · curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantMotion.kt:120` velo 0,22 s ease | [x] |
| as-A15 | `.as-root.in .as-sheet` | animation: asRise · 0.42s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset hoja-sube · curva estandar · dur 400 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantMotion.kt:122` hoja 0,42 s estándar | [x] |
| as-A16 | `.as-root[data-mall="1"] .as-un` | animation: asIn · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantMetricsPane.kt:272` asIn 0,22 s (dx 0) | [x] |
| as-A17 | `.as-sgw.applied .as-done.ap, .as-sgw.dismissed .as-done.ds` | animation: asIn · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 240 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantSuggestionsPane.kt:368` asIn 0,26 s (dx 0) | [x] |
| as-A18 | `.as-sk-l i` | animation: asSh · 1.4s · linear · retardo 0s · repite infinite · normal · none → preset brillo-barrido · curva lineal · dur 1400 | as-sug-carga | `AssistantSuggestionsPane.kt:166` asSh 1,4 s lineal, infinito; `#ffffff10` liso con movimiento reducido | [x] |
| as-A19 | `.as-toast.open` | animation: asRise · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset hoja-sube · curva estandar · dur 280 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantFrame.kt:156` asRise 0,3 s | [x] |
| as-A20 | `.draw.go .ln` (base) | animation: drawln · 1.6s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1600 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `KpknFamilies.kt:71` (base: `.draw.go .ln` no se usa en el asistente; la familia dibujo-trazo está calibrada en los cimientos) | [x] |
| as-A21 | `.go .burst .ln, .burst.go .ln` (base) | animation: drawln · 1.1s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva estandar · dur 1000 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `KpknFamilies.kt:71` (base: `.burst .ln`, de otros paquetes; familia dibujo-trazo) | [x] |
| as-A22 | `.plate .ln` (base) | animation: drawln · 1.4s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1400 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `KpknFamilies.kt:71` (base: `.plate .ln`, de otros paquetes; familia dibujo-trazo) | [x] |
| as-A23 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `KpknFamilies.kt:81` (base: `.wr-bars`, de otros paquetes; familia crece-x) | [x] |

## Transiciones (18)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-T01 | `.as-ap.done .ok` | transition: all · 0.35s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 320 | as-pla-aplicando, as-pla-aplicada | `AssistantTemplatesPane.kt:705` all 0,35 s estándar (opacidad y escala de la marca) | [x] |
| as-T02 | `.as-chip` | transition: background, color · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantPieces.kt:264` fondo y color 0,2 s ease | [x] |
| as-T03 | `.as-ck` | transition: background, border-color, color · 0.18s, 0.18s, 0.18s · ease, ease, ease · retardo 0s, 0s, 0s → preset curva suave · dur 160 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantSuggestionsPane.kt:336` fondo, borde y color 0,18 s ease | [x] |
| as-T04 | `.as-fd .c` | transition: transform · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantRulesPane.kt:511` chevron 0,22 s estándar | [x] |
| as-T05 | `.as-fx .bar i` | transition: width · 0.32s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 320 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantSuggestionsPane.kt:305` ancho 0,32 s estándar | [x] |
| as-T06 | `.as-grab i` | transition: background · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantFrame.kt:74` fondo 0,2 s ease (con el puntero encima o al pulsar) | [x] |
| as-T07 | `.as-mtop .c` | transition: transform · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantMetricsPane.kt:302` chevron 0,22 s estándar | [x] |
| as-T08 | `.as-op` | transition: border-color, background · 0.18s, 0.18s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | as-pla-vista, as-pla-aplicar, as-pla-aplicando, as-pla-aplicada | `AssistantTemplatesPane.kt:640` borde y fondo 0,18 s ease | [x] |
| as-T09 | `.as-scrim` | transition: opacity · 0.22s · ease · retardo 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantMotion.kt:131` opacidad del velo 0,22 s ease (entra y sale; `KpknSheet.kt` `scrimAlpha`) | [x] |
| as-T10 | `.as-seg button` | transition: background, color · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantPieces.kt:223` fondo y color 0,2 s ease | [x] |
| as-T11 | `.as-sg` | transition: opacity, transform · 0.26s, 0.26s · ease, cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva suave/estandar · dur 240 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantSuggestionsPane.kt:266` en el lienzo `display:none` impide que se vea; ver comentario | [x] |
| as-T12 | `.as-sh .c` | transition: transform · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantTemplatesPane.kt:326` chevron 0,22 s estándar | [x] |
| as-T13 | `.as-sheet` | transition: height, transform · 0.32s, 0.42s · cubic-bezier(0.2, 0.8, 0.2, 1), cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva estandar · dur 320/400 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantMotion.kt:132` altura 0,32 s estándar (`animateDpAsState` en `KpknSheet.kt`); transform 0,42 s = `outMs`/`sheetInMs` | [x] |
| as-T14 | `.as-sw` | transition: background · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantPieces.kt:374` fondo 0,2 s ease | [x] |
| as-T15 | `.as-sw::after` | transition: left · 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantPieces.kt:375` posición 0,2 s estándar | [x] |
| as-T16 | `.as-tabs button` | transition: color · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantFrame.kt:100` color 0,2 s ease | [x] |
| as-T17 | `.as-tabs button::after` | transition: transform · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 240 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantFrame.kt:101` scaleX 0,26 s estándar | [x] |
| as-T18 | `.as-tc` | transition: background, transform · 0.18s, 0.18s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantTemplatesPane.kt:430` fondo 0,18 s ease al pulsar (:active #161618) | [x] |

## Transformaciones, máscaras y mezcla (11)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-X01 | `.as-ap .ok` | transform: scale(0.6) | as-pla-aplicando, as-pla-aplicada | `AssistantTemplatesPane.kt:706` scale 0,6 → 1 | [x] |
| as-X02 | `.as-chips` | mask-image: linear-gradient(90deg, rgba(0, 0, 0, 0), rgb(0, 0, 0) 20px, rgb(0, 0, 0) calc(100% - 28px), rgba(0, 0, 0, 0)) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantTemplatesPane.kt:289` máscara DstIn 20 / 28 (`AssistantMotion.kt` `asEdgeMask`) | [x] |
| as-X03 | `.as-fold.open .as-fd .c` | transform: rotate(180deg) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantRulesPane.kt:512` rotate 180° | [x] |
| as-X04 | `.as-mrow.open .as-mtop .c` | transform: rotate(180deg) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantMetricsPane.kt:319` rotate 180° | [x] |
| as-X05 | `.as-root .as-reopen` | transform: translateX(-50%) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | DESV-300 | [~] |
| as-X06 | `.as-root.closed .as-sheet` | transform: translateY(102%) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantMotion.kt:130` la hoja baja el 102 % al cerrar | [x] |
| as-X07 | `.as-sc` | mask-image: linear-gradient(rgb(0, 0, 0) calc(100% - 26px), transparent) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantFrame.kt:144` máscara DstIn de 26 (`AssistantMotion.kt` `asBottomMask`) | [x] |
| as-X08 | `.as-sec.shut .as-sh .c` | transform: rotate(-90deg) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantTemplatesPane.kt:335` rotate −90° al plegar | [x] |
| as-X09 | `.as-tabs button::after` | transform: scaleX(0) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantFrame.kt:101` scaleX(0) en las pestañas no activas | [x] |
| as-X10 | `.as-tabs button.on::after` | transform: scaleX(1) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia | `AssistantFrame.kt:106` scaleX(1) en la activa | [x] |
| as-X11 | `.story .bgart.over` (base) | mix-blend-mode: screen | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-sug-lista | `StoryShell.kt:383` (base: `.story .bgart.over`, de las historias; no existe en el asistente) | [x] |

## Movimiento y estilos hechos en JavaScript (6)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-J01 | `scenes-as.js:146` | declaración CSS dentro del marcado · if(v==='ok')return `<div class="as-sc"><div class="as-empty" style="padding-top:18px"><span class="g" style="color:var(--done);box-shadow:inset 0 0 0  | — | `AssistantSuggestionsPane.kt:203` icono y anillo en verde en «todo en orden» | [x] |
| as-J02 | `scenes-as.js:318` | setTimeout · const toast=(t,sub,ms=2200)=>{const el=$q('[data-r=toast]');$q('[data-r=toast-t]').innerHTML=t+(sub?`<span>${sub}</span>`:'');el.classList.add('open') | — | `AssistantSheetContent.kt:101` el aviso se cierra solo a los 2,2 s | [x] |
| as-J03 | `scenes-as.js:360` | setTimeout · 'dec-go':()=>{const pane=$q('.as-pane[data-p=pla]');$q('[data-r=dec]').classList.remove('open');pane.querySelectorAll('.as-ap').forEach(e=>e.remove()) | — | `AssistantTemplatesPane.kt:703` al confirmar, la pregunta se cierra y entra «aplicando»; el estado lo da el motor, no un temporizador | [x] |
| as-J04 | `scenes-as.js:384` | setTimeout · 'reg-apply':()=>{if(applied){toast('Sin cambios','Las series ya tenían estos valores.');return}applied=true;toast('Reglas aplicadas a 6 ejercicios','S | — | `AssistantSheetContent.kt:164` aviso de 1,5 s y cierre de la hoja a los 1,7 s | [x] |
| as-J05 | `scenes-as.js:386` | estilo en línea desde JS · 'coach-ok':t=>{const c=t.closest('.as-tcm');c.style.opacity='.4';c.style.pointerEvents='none';toast('Sugerencia aplicada','Se ahorran '+c.querySelecto | — | `AssistantRulesPane.kt:728` la tarjeta queda al 40 % y deja de responder | [x] |
| as-J06 | `scenes-as.js:395` | setTimeout · if(cfg.apTimer===false){}else if(cfg.ap==='run'){timer=setTimeout(()=>{const a=$q('[data-r=ap]');a&&a.classList.add('done')},cfg.apHold?1e9:1400)} | — | `AssistantTemplatesPane.kt:705` el lienzo simula el final a los 1,4 s; en la app pasa a «aplicada» cuando el motor termina (`done`) | [x] |

## @keyframes usados (10)

```css
@keyframes drawln { 100% { stroke-dashoffset: 0; } }
```

```css
@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes asIn { 0% { opacity: 0; transform: translateX(var(--dx,10px)); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes asScrim { 0% { opacity: 0; } 100% { opacity: 1; } }
```

```css
@keyframes asRise { 0% { transform: translateY(100%); } 100% { transform: none; } }
```

```css
@keyframes asContent { 0% { opacity: 0; transform: translateY(10px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes asGrow { 0% { transform: scaleX(0); } 100% { transform: none; } }
```

```css
@keyframes asSh { 0% { background-position: 120% 0px; } 100% { background-position: -120% 0px; } }
```

```css
@keyframes asSpin { 100% { transform: rotate(360deg); } }
```

```css
@keyframes asFlash { 0% { color: var(--mus); } 100% { color: inherit; } }
```
