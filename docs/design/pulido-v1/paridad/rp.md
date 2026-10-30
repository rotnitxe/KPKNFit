# Paridad de profundidad y movimiento · `rp` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 16 · vidrio, 3 · degradados 7 · animaciones 11 · transiciones 6 · transformaciones, 2 · movimiento 3 · escenas del paquete: 27

## Sombras (16)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| rp-S01 | `.glass` (base) | box-shadow: rgba(0, 0, 0, 0.6) 0px 10px 30px, rgba(255, 255, 255, 0.07) 0px 1px inset → preset isla-vidrio | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-revision | SplitFlowControls.kt:193 · anulada por `.topbar .glass` (rp-S14) en estas escenas; vidrio real al Flip | [x] |
| rp-S02 | `.pl-glass` (base) | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset isla-vidrio | rp-seleccion, rp-comparar, rp-varios, rp-semana, rp-mas-dias, rp-menos-dias | SplitFlowControls.kt:228 · barra de la vista previa; vidrio real al Flip | [x] |
| rp-S03 | `.pl-sheet` (base) | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | rp-hoy-hoja, rp-comparar, rp-varios, rp-propio, rp-decisiones, rp-opciones | DESV-1000 | [~] |
| rp-S04 | `.ribbon i.now` (base) | box-shadow: rgb(0, 0, 0) 0px 0px 0px 2px, rgba(242, 238, 230, 0.667) 0px 0px 0px 3px → preset anillo-doble-actual | rp-opciones-varias | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-S05 | `.rp-ann` | box-shadow: rgba(0, 0, 0, 0.533) 0px 4px 12px → preset nota | rp-hoy, rp-hoy-hoja | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-S06 | `.rp-dlg` | box-shadow: rgba(0, 0, 0, 0.867) 0px 24px 60px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset dialogo-2 | rp-confirmar, rp-autor | SplitFlowSheets.kt:275 | [x] |
| rp-S07 | `.rp-over` | box-shadow: rgba(242, 238, 230, 0.3) 0px 0px 0px 1.5px inset → preset interior-crema-1p5-2 | — | SplitAgenda.kt:166 | [x] |
| rp-S08 | `.rp-ring` | box-shadow: rgba(255, 255, 255, 0.12) 0px 0px 0px 1px inset → preset borde-interior | rp-hoy-hoja, rp-catalogo, rp-buscar, rp-seleccion, rp-comparar, rp-varios | SplitFlowTheme.kt:333 | [x] |
| rp-S09 | `.rp-sess.lift` | box-shadow: rgba(0, 0, 0, 0.8) 0px 18px 40px → preset popover | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-opciones | SplitAgenda.kt:262 · valor exacto `0 18 40 #000c`; aparece en 200 ms suave (rp-T06) | [x] |
| rp-S10 | `.rp-snack` | box-shadow: rgba(0, 0, 0, 0.867) 0px 14px 40px → preset menu | — | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-S11 | `.rp-wc.now` | box-shadow: 0 0 0 2px var(--ink) → preset anillo-actual | rp-opciones-varias |  | [ ] |
| rp-S12 | `.tok.done` (base) | box-shadow: rgba(67, 209, 140, 0.533) 0px -2px inset → preset subrayado-hecho | rp-opciones-varias | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-S13 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | rp-hoy, rp-catalogo, rp-buscar, rp-seleccion, rp-comparar, rp-varios | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-S14 | `.topbar .glass` (base) | box-shadow: rgba(0, 0, 0, 0.467) 0px 8px 24px, rgba(255, 255, 255, 0.11) 0px 1px inset → preset barra-vidrio | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-revision | SplitFlowControls.kt:193 · vidrio real al Flip | [x] |
| rp-S15 | `.wkc.now` (base) | box-shadow: 0 0 0 2px var(--ink) → preset anillo-actual | rp-opciones-varias | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-S16 | `.write.focus` (base) | box-shadow: rgba(242, 238, 230, 0.047) 0px 0px 0px 4px → preset aureola-foco | rp-hoy | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |

## Vidrio, desenfoques y filtros (3)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| rp-V01 | `.glass` (base) | backdrop-filter: blur(26px) saturate(1.3) → preset vidrio-hoja | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-revision | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-V02 | `.pl-glass` (base) | backdrop-filter: blur(22px) saturate(1.5) → preset vidrio-isla | rp-seleccion, rp-comparar, rp-varios, rp-semana, rp-mas-dias, rp-menos-dias | SplitFlowControls.kt:228 · el desenfoque real no se mide en Robolectric: al Flip | [x] |
| rp-V03 | `.topbar .glass` (base) | backdrop-filter: blur(18px) saturate(1.8) brightness(0.9) → preset vidrio-barra | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-revision | SplitFlowControls.kt:193 · el desenfoque real no se mide en Robolectric: al Flip | [x] |

## Degradados y resplandores (7)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| rp-G01 | `.glass` (base) | background-image: linear-gradient(140deg, rgba(44, 44, 44, 0.72), rgba(22, 22, 22, 0.69)) → preset vidrio-relleno-hoja | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-revision | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-G02 | `.rp-sk` | background-image: linear-gradient(90deg, rgb(22, 22, 22), rgb(34, 34, 34) 50%, rgb(22, 22, 22)) → preset brillo-esqueleto-4 | rp-calculando | SplitFlowMotion.kt:70 | [x] |
| rp-G03 | `.rp-stk` | background-image: linear-gradient(rgba(27, 27, 29, 0), rgb(27, 27, 29) 22%) → preset velo-hoja | rp-varios, rp-propio, rp-decisiones, rp-opciones, rp-opciones-varias, rp-opciones-bloques | SplitFlowSheets.kt:88 | [x] |
| rp-G04 | `.sess.now` (base) | background-image: linear-gradient(160deg, rgba(244, 154, 110, 0.08), rgb(17, 17, 17) 60%) → preset tinte-hacia-modulo-1 | rp-opciones-varias, rp-aplicado, rp-deshecho | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-G05 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | rp-hoy, rp-catalogo, rp-buscar, rp-seleccion, rp-comparar, rp-varios | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-G06 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | rp-hoy, rp-catalogo, rp-buscar, rp-seleccion, rp-comparar, rp-varios | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-G07 | `.topbar .glass` (base) | background-image: linear-gradient(150deg, rgba(58, 58, 58, 0.4), rgba(20, 20, 20, 0.35)) → preset vidrio-relleno-barra | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-revision | SplitFlowControls.kt:193 | [x] |

## Animaciones (@keyframes) (11)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| rp-A01 | `.draw.go .ln` (base) | animation: drawln · 1.6s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1600 | rp-aplicado, rp-deshecho | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-A02 | `.go .burst .ln, .burst.go .ln` (base) | animation: drawln · 1.1s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva estandar · dur 1000 | rp-aplicado, rp-deshecho | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-A03 | `.plate .ln` (base) | animation: drawln · 1.4s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1400 | rp-aplicado, rp-deshecho | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-A04 | `.rp-anim .rp-day, .rp-anim .rp-vr, .rp-anim .rp-rv` | animation: rp-in · 0.32s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-sube · curva estandar · dur 320 | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-revision | SplitAgenda.kt:136 · al recalcular, no al abrir; 40 ms de retardo por fila; movimiento reducido: sin entrada | [x] |
| rp-A05 | `.rp-cd .opts` | animation: rp-in · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 200 | rp-propio |  | [ ] |
| rp-A06 | `.rp-detail` | animation: rp-in · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 240 | — | SplitAgenda.kt:363 · valores de rp-in con 8 dp (la familia entrada-sube sube 14) | [x] |
| rp-A07 | `.rp-ok svg` | animation: rp-pop · 0.5s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset aparece-escala · curva estandar · dur 520 | rp-aplicado, rp-deshecho | SplitFlowSheets.kt:248 · rp-pop con escala 0,4 (la familia aparece-escala parte de 0,86) | [x] |
| rp-A08 | `.rp-sk` | animation: rp-sk · 1.4s · linear · retardo 0s · repite infinite · normal · none → preset brillo-barrido · curva lineal · dur 1400 | rp-calculando | SplitFlowMotion.kt:67 · periodo 1400 ms lineal; quieto con movimiento reducido | [x] |
| rp-A09 | `.rp-snack` | animation: rp-in · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 280 | — | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |
| rp-A10 | `.rp-spin` | animation: rp-rot · 0.8s · linear · retardo 0s · repite infinite · normal · none → preset giro · curva lineal · dur 800 | rp-calculando | SplitFlowMotion.kt:86 · familia giro, 800 ms lineal | [x] |
| rp-A11 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | rp-hoy, rp-catalogo, rp-buscar, rp-seleccion, rp-comparar, rp-varios | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |

## Transiciones (6)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| rp-T01 | `.rp-bar i` | transition: width · 0.5s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 520 | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-opciones, rp-opciones-varias | SplitPreviewScreen.kt:354 | [x] |
| rp-T02 | `.rp-card` | transition: border-color, background · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | rp-catalogo, rp-buscar, rp-seleccion, rp-comparar, rp-varios, rp-propio | SplitCatalogCard.kt:70 | [x] |
| rp-T03 | `.rp-dot` | transition: background · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 240 | rp-hoy-hoja, rp-catalogo, rp-buscar, rp-seleccion, rp-comparar, rp-varios | SplitFlowTheme.kt:336 | [x] |
| rp-T04 | `.rp-pk` | transition: border-color · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-opciones | SplitPreviewScreen.kt:286 | [x] |
| rp-T05 | `.rp-sess` | transition: transform, box-shadow, border-color · 0.22s, 0.2s, 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1), ease, ease · retardo 0s, 0s, 0s → preset curva estandar/suave · dur 200 | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-opciones | SplitAgenda.kt:249 · borde 200 ms suave; el transform del levantado no lleva transicion (`.lift` la anula) | [x] |
| rp-T06 | `.rp-sess.lift` | transition: box-shadow · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-opciones | SplitAgenda.kt:249 · sombra 200 ms suave | [x] |

## Transformaciones, máscaras y mezcla (2)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| rp-X01 | `.rp-chips` | mask-image: linear-gradient(90deg, rgba(0, 0, 0, 0), rgb(0, 0, 0) 16px, rgb(0, 0, 0) calc(100% - 28px), rgba(0, 0, 0, 0)) | rp-catalogo, rp-buscar, rp-seleccion, rp-comparar, rp-varios, rp-propio-vacio | SplitFlowControls.kt:162 | [x] |
| rp-X02 | `.rp-sess.lift` | transform: scale(1.025) | rp-semana, rp-mas-dias, rp-menos-dias, rp-en-curso, rp-vacia, rp-opciones | SplitAgenda.kt:262 | [x] |

## Movimiento y estilos hechos en JavaScript (3)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| rp-J01 | `scenes-rp.js:497` | estilo en línea desde JS · const mv=ev=>{card.style.transform=`translateY(${ev.clientY-y0}px) scale(1.025)`; | — | SplitAgenda.kt:262 · translationY del dedo en coordenadas de ventana | [x] |
| rp-J02 | `scenes-rp.js:784` | declaración CSS dentro del marcado · return `<div class="scroll rp-page" style="filter:brightness(.55)"><div class="rp-eye">Motor y fuerza · semana 5</div><h1 class="rp-h1">Semana 5</h1>< | — | ProgramDetailScreen.kt:605 · brillo 0,55 de la semana tras la hoja de resumen | [x] |
| rp-J03 | `scenes-rp.js:847` | declaración CSS dentro del marcado · scene({tab:'reparto',id:'rp-hoy-hoja',title:'Hoy · la hoja «Aplicar reparto»',html:()=>`<div class="scroll" style="padding-top:52px;filter:brightness( | — | SplitFlowTheme.kt:58 · no aplica: la regla es de la hoja base y las escenas del reparto no la usan (ver el bloque «NO APLICA») | [x] |

## @keyframes usados (6)

```css
@keyframes drawln { 100% { stroke-dashoffset: 0; } }
```

```css
@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes rp-in { 0% { opacity: 0; transform: translateY(8px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes rp-pop { 0% { transform: scale(0.4); opacity: 0; } }
```

```css
@keyframes rp-sk { 100% { background-position: -200% 0px; } }
```

```css
@keyframes rp-rot { 100% { transform: rotate(360deg); } }
```
