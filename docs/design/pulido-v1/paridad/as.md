# Paridad de profundidad y movimiento · `as` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 10 · vidrio, 1 · degradados 5 · animaciones 23 · transiciones 18 · transformaciones, 11 · movimiento 6 · escenas del paquete: 26

## Sombras (10)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-S01 | `.as-cv` | box-shadow: rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset → preset borde-interior | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-S02 | `.as-dec > div` | box-shadow: rgba(0, 0, 0, 0.733) 0px -10px 30px → preset pie-sube | as-pla-vista, as-pla-aplicar, as-pla-aplicando, as-pla-aplicada |  | [ ] |
| as-S03 | `.as-empty .g` | box-shadow: rgba(255, 255, 255, 0.15) 0px 0px 0px 1.5px inset → preset borde-interior | as-met-cardio, as-met-vacia, as-sug-vacio, as-sug-nota, as-pla-vacio |  | [ ] |
| as-S04 | `.as-menu` | box-shadow: rgba(0, 0, 0, 0.8) 0px 14px 40px → preset menu | as-pla-mias |  | [ ] |
| as-S05 | `.as-sheet` | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-S06 | `.as-tlim` | box-shadow: 0 0 0 2px var(--as-mod) → preset corte-hoja | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-sug-lista |  | [ ] |
| as-S07 | `.as-toast` | box-shadow: rgba(0, 0, 0, 0.8) 0px 12px 34px → preset aviso | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-S08 | `.pl-glass` (base) | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset isla-vidrio | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-S09 | `.tok.done` (base) | box-shadow: rgba(67, 209, 140, 0.533) 0px -2px inset → preset subrayado-hecho | as-pla-aplicada |  | [ ] |
| as-S10 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |

## Vidrio, desenfoques y filtros (1)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-V01 | `.pl-glass` (base) | backdrop-filter: blur(22px) saturate(1.5) → preset vidrio-isla | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |

## Degradados y resplandores (5)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-G01 | `.as-foot` | background-image: linear-gradient(rgba(27, 27, 29, 0), rgb(27, 27, 29) 30%) → preset velo-hoja | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-G02 | `.as-sk-l i` | background-image: linear-gradient(90deg, rgba(255, 255, 255, 0.04) 20%, rgba(255, 255, 255, 0.11) 50%, rgba(255, 255, 255, 0.04) 80%) → preset brillo-esqueleto-1 | as-sug-carga |  | [ ] |
| as-G03 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-G04 | `.story .bgart.over::after` (base) | background-image: linear-gradient(rgba(0, 0, 0, 0), rgb(0, 0, 0)) → preset velo-negro-1 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-sug-lista |  | [ ] |
| as-G05 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |

## Animaciones (@keyframes) (23)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-A01 | `.as-ap.done .after` | animation: asIn · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 280 | as-pla-aplicando, as-pla-aplicada |  | [ ] |
| as-A02 | `.as-ap.open` | animation: asScrim · 0.2s · ease · retardo 0s · repite 1 · normal · both → preset fundido-entrada · curva suave · dur 200 | as-met-completa, as-pla-filtros, as-pla-aplicando, as-pla-aplicada, as-pla-mias |  | [ ] |
| as-A03 | `.as-dec > div` | animation: asRise · 0.36s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset hoja-sube · curva estandar · dur 320 | as-pla-vista, as-pla-aplicar, as-pla-aplicando, as-pla-aplicada |  | [ ] |
| as-A04 | `.as-dec.open` | animation: asScrim · 0.2s · ease · retardo 0s · repite 1 · normal · both → preset fundido-entrada · curva suave · dur 200 | as-met-completa, as-pla-filtros, as-pla-vista, as-pla-aplicar, as-pla-aplicando, as-pla-aplicada |  | [ ] |
| as-A05 | `.as-flash` | animation: asFlash · 0.6s · ease · retardo 0s · repite 1 · normal · none → preset destello · curva suave · dur 600 | — |  | [ ] |
| as-A06 | `.as-fold.open .as-fb2` | animation: asIn · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A07 | `.as-fp.open` | animation: asIn · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A08 | `.as-mbar i` | animation: asGrow · 0.7s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset crece-x · curva estandar · dur 700 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-sug-lista |  | [ ] |
| as-A09 | `.as-menu.open` | animation: asIn · 0.18s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 160 | as-met-completa, as-pla-filtros, as-pla-aplicando, as-pla-aplicada, as-pla-mias |  | [ ] |
| as-A10 | `.as-mrow.open .as-mdet` | animation: asIn · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-sug-lista |  | [ ] |
| as-A11 | `.as-pane.on` | animation: asIn · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 240 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A12 | `.as-rg .sp` | animation: asSpin · 1.1s · linear · retardo 0s · repite infinite · normal · none → preset giro · curva lineal · dur 1000 | as-pla-aplicando, as-pla-aplicada |  | [ ] |
| as-A13 | `.as-root.in .as-head, .as-root.in .as-tabs, .as-root.in .as-body` | animation: asContent · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0.16s · repite 1 · normal · both → preset entrada-sube · curva estandar · dur 280 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A14 | `.as-root.in .as-scrim` | animation: asScrim · 0.22s · ease · retardo 0s · repite 1 · normal · both → preset fundido-entrada · curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A15 | `.as-root.in .as-sheet` | animation: asRise · 0.42s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset hoja-sube · curva estandar · dur 400 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A16 | `.as-root[data-mall="1"] .as-un` | animation: asIn · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A17 | `.as-sgw.applied .as-done.ap, .as-sgw.dismissed .as-done.ds` | animation: asIn · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 240 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A18 | `.as-sk-l i` | animation: asSh · 1.4s · linear · retardo 0s · repite infinite · normal · none → preset brillo-barrido · curva lineal · dur 1400 | as-sug-carga |  | [ ] |
| as-A19 | `.as-toast.open` | animation: asRise · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset hoja-sube · curva estandar · dur 280 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A20 | `.draw.go .ln` (base) | animation: drawln · 1.6s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1600 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A21 | `.go .burst .ln, .burst.go .ln` (base) | animation: drawln · 1.1s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva estandar · dur 1000 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A22 | `.plate .ln` (base) | animation: drawln · 1.4s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1400 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-A23 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |

## Transiciones (18)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-T01 | `.as-ap.done .ok` | transition: all · 0.35s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 320 | as-pla-aplicando, as-pla-aplicada |  | [ ] |
| as-T02 | `.as-chip` | transition: background, color · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T03 | `.as-ck` | transition: background, border-color, color · 0.18s, 0.18s, 0.18s · ease, ease, ease · retardo 0s, 0s, 0s → preset curva suave · dur 160 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T04 | `.as-fd .c` | transition: transform · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T05 | `.as-fx .bar i` | transition: width · 0.32s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 320 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T06 | `.as-grab i` | transition: background · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T07 | `.as-mtop .c` | transition: transform · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T08 | `.as-op` | transition: border-color, background · 0.18s, 0.18s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | as-pla-vista, as-pla-aplicar, as-pla-aplicando, as-pla-aplicada |  | [ ] |
| as-T09 | `.as-scrim` | transition: opacity · 0.22s · ease · retardo 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T10 | `.as-seg button` | transition: background, color · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T11 | `.as-sg` | transition: opacity, transform · 0.26s, 0.26s · ease, cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva suave/estandar · dur 240 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T12 | `.as-sh .c` | transition: transform · 0.22s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T13 | `.as-sheet` | transition: height, transform · 0.32s, 0.42s · cubic-bezier(0.2, 0.8, 0.2, 1), cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva estandar · dur 320/400 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T14 | `.as-sw` | transition: background · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T15 | `.as-sw::after` | transition: left · 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T16 | `.as-tabs button` | transition: color · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T17 | `.as-tabs button::after` | transition: transform · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 240 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-T18 | `.as-tc` | transition: background, transform · 0.18s, 0.18s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |

## Transformaciones, máscaras y mezcla (11)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-X01 | `.as-ap .ok` | transform: scale(0.6) | as-pla-aplicando, as-pla-aplicada |  | [ ] |
| as-X02 | `.as-chips` | mask-image: linear-gradient(90deg, rgba(0, 0, 0, 0), rgb(0, 0, 0) 20px, rgb(0, 0, 0) calc(100% - 28px), rgba(0, 0, 0, 0)) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-X03 | `.as-fold.open .as-fd .c` | transform: rotate(180deg) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-X04 | `.as-mrow.open .as-mtop .c` | transform: rotate(180deg) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-X05 | `.as-root .as-reopen` | transform: translateX(-50%) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-X06 | `.as-root.closed .as-sheet` | transform: translateY(102%) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-X07 | `.as-sc` | mask-image: linear-gradient(rgb(0, 0, 0) calc(100% - 26px), transparent) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-X08 | `.as-sec.shut .as-sh .c` | transform: rotate(-90deg) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-X09 | `.as-tabs button::after` | transform: scaleX(0) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-X10 | `.as-tabs button.on::after` | transform: scaleX(1) | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-met-vacia |  | [ ] |
| as-X11 | `.story .bgart.over` (base) | mix-blend-mode: screen | as-entrada, as-met-media, as-met-completa, as-met-todos, as-met-cardio, as-sug-lista |  | [ ] |

## Movimiento y estilos hechos en JavaScript (6)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| as-J01 | `scenes-as.js:146` | declaración CSS dentro del marcado · if(v==='ok')return `<div class="as-sc"><div class="as-empty" style="padding-top:18px"><span class="g" style="color:var(--done);box-shadow:inset 0 0 0  | — |  | [ ] |
| as-J02 | `scenes-as.js:318` | setTimeout · const toast=(t,sub,ms=2200)=>{const el=$q('[data-r=toast]');$q('[data-r=toast-t]').innerHTML=t+(sub?`<span>${sub}</span>`:'');el.classList.add('open') | — |  | [ ] |
| as-J03 | `scenes-as.js:360` | setTimeout · 'dec-go':()=>{const pane=$q('.as-pane[data-p=pla]');$q('[data-r=dec]').classList.remove('open');pane.querySelectorAll('.as-ap').forEach(e=>e.remove()) | — |  | [ ] |
| as-J04 | `scenes-as.js:384` | setTimeout · 'reg-apply':()=>{if(applied){toast('Sin cambios','Las series ya tenían estos valores.');return}applied=true;toast('Reglas aplicadas a 6 ejercicios','S | — |  | [ ] |
| as-J05 | `scenes-as.js:386` | estilo en línea desde JS · 'coach-ok':t=>{const c=t.closest('.as-tcm');c.style.opacity='.4';c.style.pointerEvents='none';toast('Sugerencia aplicada','Se ahorran '+c.querySelecto | — |  | [ ] |
| as-J06 | `scenes-as.js:395` | setTimeout · if(cfg.apTimer===false){}else if(cfg.ap==='run'){timer=setTimeout(()=>{const a=$q('[data-r=ap]');a&&a.classList.add('done')},cfg.apHold?1e9:1400)} | — |  | [ ] |

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
