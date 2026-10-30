# Paridad de profundidad y movimiento · `ce` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 9 · vidrio, 2 · degradados 4 · animaciones 5 · transiciones 5 · transformaciones, 2 · movimiento 6 · escenas del paquete: 26

## Sombras (9)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ce-S01 | `.ab.cur` (base) | box-shadow: rgba(242, 238, 230, 0.08) 0px 0px 0px 4px → preset aureola-foco | ce-cambiar |  | [ ] |
| ce-S02 | `.ce-asi em` | box-shadow: rgb(27, 27, 29) 0px 0px 0px 2px → preset corte-hoja | ce-anadir, ce-cambiar, ce-lista, ce-solo, ce-continuo, ce-continuo-gps |  | [ ] |
| ce-S03 | `.ce-glass` | box-shadow: rgba(0, 0, 0, 0.667) 0px 10px 30px, rgba(255, 255, 255, 0.094) 0px 1px inset → preset isla-vidrio | ce-anadir, ce-cambiar, ce-lista, ce-solo, ce-continuo, ce-continuo-gps |  | [ ] |
| ce-S04 | `.ce-needle` | box-shadow: rgba(242, 238, 230, 0.333) 0px 0px 12px → preset resplandor-crema-12 | ce-duracion, ce-esfuerzo, ce-calentamiento, ce-calentamiento-libre, ce-distancia |  | [ ] |
| ce-S05 | `.ce-tile.cur .ce-win` | box-shadow: inset 0 0 0 1px var(--ink) → preset interior-crema-1 | ce-anadir, ce-cambiar |  | [ ] |
| ce-S06 | `.pl-flat` (base) | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px → preset flotante | ce-menu-eliminado |  | [ ] |
| ce-S07 | `.pl-glass` (base) | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset isla-vidrio | ce-ficha, ce-ficha-exterior |  | [ ] |
| ce-S08 | `.pl-sheet` (base) | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | ce-anadir, ce-cambiar, ce-duracion, ce-duracion-teclado, ce-duracion-error, ce-esfuerzo |  | [ ] |
| ce-S09 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | ce-continuo, ce-continuo-gps, ce-series, ce-intervalos, ce-duracion, ce-duracion-teclado |  | [ ] |

## Vidrio, desenfoques y filtros (2)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ce-V01 | `.ce-glass` | backdrop-filter: blur(24px) saturate(1.7) → preset vidrio-editor | ce-anadir, ce-cambiar, ce-lista, ce-solo, ce-continuo, ce-continuo-gps |  | [ ] |
| ce-V02 | `.pl-glass` (base) | backdrop-filter: blur(22px) saturate(1.5) → preset vidrio-isla | ce-ficha, ce-ficha-exterior |  | [ ] |

## Degradados y resplandores (4)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ce-G01 | `.ce-fi-hero` | background-image: radial-gradient(120% 90% at 50% 55%, rgb(27, 27, 29) 0%, rgb(10, 10, 10) 62%, rgb(0, 0, 0) 100%) → preset vineta | ce-ficha, ce-ficha-exterior |  | [ ] |
| ce-G02 | `.ce-fi-hero::after` | background-image: linear-gradient(rgba(0, 0, 0, 0), rgb(0, 0, 0)) → preset velo-negro-1 | ce-ficha, ce-ficha-exterior |  | [ ] |
| ce-G03 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | ce-continuo, ce-continuo-gps, ce-series, ce-intervalos, ce-duracion, ce-duracion-teclado |  | [ ] |
| ce-G04 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | ce-continuo, ce-continuo-gps, ce-series, ce-intervalos, ce-duracion, ce-duracion-teclado |  | [ ] |

## Animaciones (@keyframes) (5)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ce-A01 | `.ce-bd` | animation: ce-in · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 240 | ce-continuo, ce-continuo-gps, ce-series, ce-intervalos, ce-duracion, ce-duracion-teclado |  | [ ] |
| ce-A02 | `.ce-caret` | animation: ce-blink · 1s · steps(1) · retardo 0s · repite infinite · normal · none → preset pulso · curva parpadeo · dur 1000 | ce-duracion-teclado, ce-duracion-error |  | [ ] |
| ce-A03 | `.ce-item.fresh` | animation: ce-fresh · 1.6s · ease-out · retardo 0s · repite 1 · normal · none → preset destello · curva salida · dur 1600 | ce-anadir, ce-cambiar, ce-lista, ce-continuo, ce-continuo-gps, ce-series |  | [ ] |
| ce-A04 | `.ce-undo, .ce-toast` | animation: ce-in · 0.25s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 240 | ce-menu-eliminado |  | [ ] |
| ce-A05 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | ce-continuo, ce-continuo-gps, ce-series, ce-intervalos, ce-duracion, ce-duracion-teclado |  | [ ] |

## Transiciones (5)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ce-T01 | `.ce-b` | transition: opacity, background · 0.15s, 0.15s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | ce-anadir, ce-cambiar, ce-lista, ce-continuo, ce-series, ce-intervalos |  | [ ] |
| ce-T02 | `.ce-meter i` | transition: background · 0.15s · ease · retardo 0s → preset curva suave · dur 160 | ce-continuo, ce-continuo-gps, ce-series, ce-duracion, ce-duracion-teclado, ce-duracion-error |  | [ ] |
| ce-T03 | `.ce-rt` | transition: transform · 0.24s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 240 | ce-duracion, ce-esfuerzo, ce-calentamiento, ce-calentamiento-libre, ce-distancia |  | [ ] |
| ce-T04 | `.ce-sw` | transition: background · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | ce-continuo-gps, ce-distancia, ce-avisos, ce-avisos-continuo |  | [ ] |
| ce-T05 | `.ce-sw i` | transition: left, background · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | ce-continuo-gps, ce-distancia, ce-avisos, ce-avisos-continuo |  | [ ] |

## Transformaciones, máscaras y mezcla (2)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ce-X01 | `.ce-ruler` | mask-image: linear-gradient(90deg, transparent, rgb(0, 0, 0) 16%, rgb(0, 0, 0) 84%, transparent) | ce-duracion, ce-esfuerzo, ce-calentamiento, ce-calentamiento-libre, ce-distancia |  | [ ] |
| ce-X02 | `.ce-tl` | transform: translateX(-50%) | ce-duracion, ce-esfuerzo, ce-calentamiento, ce-calentamiento-libre, ce-distancia |  | [ ] |

## Movimiento y estilos hechos en JavaScript (6)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ce-J01 | `scenes-ce.js:444` | setTimeout · const say=t=>{st.toast=t;clearTimeout(tm);tm=setTimeout(()=>{st.toast='';draw()},2400)}; | — |  | [ ] |
| ce-J02 | `scenes-ce.js:504` | setTimeout · mdel:()=>{st.undo={c:st.cards.splice(st.mi,1)[0],i:st.mi};st.layer=null;st.open=null;clearTimeout(tm);tm=setTimeout(()=>{st.undo=null;draw()},4200)}, | — |  | [ ] |
| ce-J03 | `scenes-ce.js:512` | setTimeout · const lpEl=ev.target.closest('[data-lp]');if(lpEl){clearTimeout(timer);timer=setTimeout(()=>{lp=Date.now();st.mi=+lpEl.dataset.i;st.layer='menu';draw( | — |  | [ ] |
| ce-J04 | `scenes-ce.js:516` | estilo en línea desde JS · const rl=ev.target.closest('.ce-ruler');if(rl&&st.sel){const cfg=CE_CFG[st.sel.w],px=cfg.px,rt=rl.querySelector('.ce-rt'),x0=ev.clientX,n0=st.sel.n;le | — |  | [ ] |
| ce-J05 | `scenes-ce.js:518` | estilo en línea desde JS · const mv=e2=>{const sc=ph.getBoundingClientRect().width/ph.offsetWidth\|\|1;raw=Math.max(cfg.min,Math.min(cfg.max,n0-(e2.clientX-x0)/sc/px));rt.style.tr | — |  | [ ] |
| ce-J06 | `scenes-ce.js:519` | estilo en línea desde JS · const up=()=>{rl.removeEventListener('pointermove',mv);rl.removeEventListener('pointerup',up);rt.style.transition='';draw()};rl.addEventListener('poin | — |  | [ ] |

## @keyframes usados (4)

```css
@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes ce-fresh { 0% { background: rgba(143, 178, 255, 0.15); } 100% { background: transparent; } }
```

```css
@keyframes ce-in { 0% { opacity: 0; transform: translateY(8px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes ce-blink { 50% { opacity: 0; } }
```
