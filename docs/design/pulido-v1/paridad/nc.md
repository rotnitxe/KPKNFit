# Paridad de profundidad y movimiento · `nc` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 10 · vidrio, 3 · degradados 8 · animaciones 12 · transiciones 5 · transformaciones, 7 · movimiento 6 · escenas del paquete: 27

## Sombras (10)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| nc-S01 | `.glass` (base) | box-shadow: rgba(0, 0, 0, 0.6) 0px 10px 30px, rgba(255, 255, 255, 0.07) 0px 1px inset → preset isla-vidrio | nc-d-resultado, nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial |  | [ ] |
| nc-S02 | `.nc-dlg` | box-shadow: rgba(0, 0, 0, 0.867) 0px 20px 60px → preset modal | nc-s-eliminar |  | [ ] |
| nc-S03 | `.nc-os` | box-shadow: rgba(0, 0, 0, 0.867) 0px 16px 60px → preset caida-16-60 | nc-d-sistema |  | [ ] |
| nc-S04 | `.nc-rule .nd` | box-shadow: rgba(242, 238, 230, 0.333) 0px 0px 12px → preset resplandor-crema-12 | nc-s-primera, nc-s-historial, nc-s-atipico, nc-s-extras, nc-s-errores, nc-s-fecha |  | [ ] |
| nc-S05 | `.nc-txt .u` | box-shadow: transparent 0px 1.5px 0px 0px → preset ninguna | nc-d-vivo, nc-d-reposo, nc-d-permiso, nc-d-sistema, nc-d-denegado, nc-d-escuchando-0 |  | [ ] |
| nc-S06 | `.pl-glass` (base) | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset isla-vidrio | nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial, nc-s-atipico |  | [ ] |
| nc-S07 | `.pl-sheet` (base) | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | nc-d-permiso, nc-s-primera, nc-s-historial, nc-s-atipico, nc-s-extras, nc-s-errores |  | [ ] |
| nc-S08 | `.tok.done` (base) | box-shadow: rgba(67, 209, 140, 0.533) 0px -2px inset → preset subrayado-hecho | nc-d-leyendo, nc-d-resultado |  | [ ] |
| nc-S09 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | nc-d-vivo, nc-d-reposo, nc-d-permiso, nc-d-sistema, nc-d-denegado, nc-d-escuchando-0 |  | [ ] |
| nc-S10 | `.topbar .glass` (base) | box-shadow: rgba(0, 0, 0, 0.467) 0px 8px 24px, rgba(255, 255, 255, 0.11) 0px 1px inset → preset barra-vidrio | nc-d-resultado, nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial |  | [ ] |

## Vidrio, desenfoques y filtros (3)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| nc-V01 | `.glass` (base) | backdrop-filter: blur(26px) saturate(1.3) → preset vidrio-hoja | nc-d-resultado, nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial |  | [ ] |
| nc-V02 | `.pl-glass` (base) | backdrop-filter: blur(22px) saturate(1.5) → preset vidrio-isla | nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial, nc-s-atipico |  | [ ] |
| nc-V03 | `.topbar .glass` (base) | backdrop-filter: blur(18px) saturate(1.8) brightness(0.9) → preset vidrio-barra | nc-d-resultado, nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial |  | [ ] |

## Degradados y resplandores (8)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| nc-G01 | `.glass` (base) | background-image: linear-gradient(140deg, rgba(44, 44, 44, 0.72), rgba(22, 22, 22, 0.69)) → preset vidrio-relleno-hoja | nc-d-resultado, nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial |  | [ ] |
| nc-G02 | `.nc-sk` | background-image: linear-gradient(100deg, rgb(15, 15, 15) 30%, rgb(26, 26, 26) 50%, rgb(15, 15, 15) 70%) → preset brillo-esqueleto-3 | nc-d-leyendo |  | [ ] |
| nc-G03 | `.nc-txt .u` | background-image: linear-gradient(rgba(242, 238, 230, 0.35), rgba(242, 238, 230, 0.35)) → preset tinte-crema-35 | nc-d-vivo, nc-d-reposo, nc-d-permiso, nc-d-sistema, nc-d-denegado, nc-d-escuchando-0 |  | [ ] |
| nc-G04 | `.nc-txt .u.rd` | background-image: linear-gradient(rgba(242, 238, 230, 0.7), rgba(242, 238, 230, 0.7)) → preset tinte-crema-70 | nc-d-vivo, nc-d-reposo, nc-d-permiso, nc-d-sistema, nc-d-denegado, nc-d-escuchando-0 |  | [ ] |
| nc-G05 | `.nc-veil` | background-image: linear-gradient(rgb(0, 0, 0) 52%, rgba(0, 0, 0, 0)) → preset velo-negro-sale-2 | nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial, nc-s-atipico |  | [ ] |
| nc-G06 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | nc-d-vivo, nc-d-reposo, nc-d-permiso, nc-d-sistema, nc-d-denegado, nc-d-escuchando-0 |  | [ ] |
| nc-G07 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | nc-d-vivo, nc-d-reposo, nc-d-permiso, nc-d-sistema, nc-d-denegado, nc-d-escuchando-0 |  | [ ] |
| nc-G08 | `.topbar .glass` (base) | background-image: linear-gradient(150deg, rgba(58, 58, 58, 0.4), rgba(20, 20, 20, 0.35)) → preset vidrio-relleno-barra | nc-d-resultado, nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial |  | [ ] |

## Animaciones (@keyframes) (12)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| nc-A01 | `.caret` (base) | animation: blink · 1s · steps(1) · retardo 0s · repite infinite · normal · none → preset pulso · curva parpadeo · dur 1000 | nc-d-escuchando-0, nc-d-escuchando, nc-d-ruido |  | [ ] |
| nc-A02 | `.draw.go .ln` (base) | animation: drawln · 1.6s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1600 | nc-d-resultado |  | [ ] |
| nc-A03 | `.go .burst .ln, .burst.go .ln` (base) | animation: drawln · 1.1s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva estandar · dur 1000 | nc-d-resultado |  | [ ] |
| nc-A04 | `.nc-hr.new` | animation: nc-flash · 1.8s · ease-out · retardo 0s · repite 1 · normal · forwards → preset destello · curva salida · dur 1600 | nc-c-pantalla, nc-c-isla, nc-s-historial, nc-s-atipico, nc-s-extras, nc-s-errores |  | [ ] |
| nc-A05 | `.nc-sh` | animation: nc-up · 0.32s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 320 | nc-s-primera, nc-s-historial, nc-s-atipico, nc-s-extras, nc-s-errores, nc-s-fecha |  | [ ] |
| nc-A06 | `.nc-sk` | animation: nc-sh · 1.4s · linear · retardo 0s · repite infinite · normal · none → preset brillo-barrido · curva lineal · dur 1400 | nc-d-leyendo |  | [ ] |
| nc-A07 | `.nc-snack` | animation: nc-up · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 280 | nc-c-guardado |  | [ ] |
| nc-A08 | `.nc-spin` | animation: nc-spin · 0.9s · linear · retardo 0s · repite infinite · normal · none → preset giro · curva lineal · dur 1000 | nc-d-leyendo |  | [ ] |
| nc-A09 | `.nc-status .dot` | animation: nc-pulse · 1.1s · ease-in-out · retardo 0s · repite infinite · normal · none → preset pulso · curva vaiven · dur 1000 | nc-d-escuchando-0, nc-d-escuchando, nc-d-ruido |  | [ ] |
| nc-A10 | `.nc-stop .rg .p.run` | animation: nc-ring · 15s · linear · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva lineal · dur 15000 | nc-d-escuchando-0, nc-d-escuchando, nc-d-ruido |  | [ ] |
| nc-A11 | `.nc-wave i` | animation: nc-bar · 780ms · ease-in-out · retardo var(--d) · repite infinite · alternate · none → preset crece-y · curva vaiven · dur 700 | nc-d-escuchando-0, nc-d-escuchando, nc-d-ruido |  | [ ] |
| nc-A12 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | nc-d-vivo, nc-d-reposo, nc-d-permiso, nc-d-sistema, nc-d-denegado, nc-d-escuchando-0 |  | [ ] |

## Transiciones (5)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| nc-T01 | `.nc-fab` | transition: opacity, transform · 0.2s, 0.22s · ease, cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva suave/estandar · dur 200 | nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-c-guardado |  | [ ] |
| nc-T02 | `.nc-isl` | transition: opacity, transform · 0.22s, 0.22s · ease, cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva suave/estandar · dur 200 | nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial, nc-s-atipico |  | [ ] |
| nc-T03 | `.nc-sv` | transition: background, color · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | nc-s-primera, nc-s-historial, nc-s-atipico, nc-s-extras, nc-s-errores, nc-s-fecha |  | [ ] |
| nc-T04 | `.nc-val .num` | transition: color · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | nc-s-primera, nc-s-historial, nc-s-atipico, nc-s-extras, nc-s-errores, nc-s-fecha |  | [ ] |
| nc-T05 | `.nc-write` | transition: border-color · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | nc-d-vivo, nc-d-reposo, nc-d-permiso, nc-d-sistema, nc-d-denegado, nc-d-escuchando-0 |  | [ ] |

## Transformaciones, máscaras y mezcla (7)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| nc-X01 | `.nc-dlg` | transform: translateY(-50%) | nc-s-eliminar |  | [ ] |
| nc-X02 | `.nc-fab` | transform: translateX(-50%) | nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-c-guardado |  | [ ] |
| nc-X03 | `.nc-fab.gone` | transform: translateX(-50%) translateY(8px) | nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial, nc-s-atipico |  | [ ] |
| nc-X04 | `.nc-isl.gone` | transform: translateY(-8px) | nc-c-pantalla, nc-c-isla, nc-c-vacio, nc-s-primera, nc-s-historial, nc-s-atipico |  | [ ] |
| nc-X05 | `.nc-mini .nc-rule` | mask-image: linear-gradient(90deg, transparent, rgb(0, 0, 0) 22%, rgb(0, 0, 0) 78%, transparent) | nc-s-primera, nc-s-historial, nc-s-atipico, nc-s-extras, nc-s-errores, nc-s-fecha |  | [ ] |
| nc-X06 | `.nc-rule` | mask-image: linear-gradient(90deg, transparent, rgb(0, 0, 0) 16%, rgb(0, 0, 0) 84%, transparent) | nc-s-primera, nc-s-historial, nc-s-atipico, nc-s-extras, nc-s-errores, nc-s-fecha |  | [ ] |
| nc-X07 | `.nc-stop svg.rg` | transform: rotate(-90deg) | nc-d-escuchando-0, nc-d-escuchando, nc-d-ruido |  | [ ] |

## Movimiento y estilos hechos en JavaScript (6)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| nc-J01 | `scenes-nc.js:188` | requestAnimationFrame · const step=n=>{const k=Math.min(1,(n-t0)/140),e=1-Math.pow(1-k,3);cur=a+(tgt-a)*e;draw();emit(false);if(k<1)raf=requestAnimationFrame(step);else emit( | — |  | [ ] |
| nc-J02 | `scenes-nc.js:192` | requestAnimationFrame · draw();emit(false);if(Math.abs(vel)>.0045)raf=requestAnimationFrame(step);else snap()};raf=requestAnimationFrame(step)}; | — |  | [ ] |
| nc-J03 | `scenes-nc.js:306` | setTimeout · ncSheetWire(host,o,{close:close,save:v=>{close();mount({newRow:v,snack:true,scrollTo:0});setTimeout(()=>{const s=host.querySelector('.nc-snack');if(s) | — |  | [ ] |
| nc-J04 | `scenes-nc.js:316` | setTimeout · const process=()=>{stopAll();if(!text){show('nohear');return}show('proc');setTimeout(()=>show('result'),1700)}; | — |  | [ ] |
| nc-J05 | `scenes-nc.js:318` | setInterval · tick=setInterval(()=>{t++;const e=host.querySelector('[data-nc=time]');if(e)e.textContent=ncTime(t)},1000); | — |  | [ ] |
| nc-J06 | `scenes-nc.js:319` | setInterval · timer=setInterval(()=>{if(i<NC_WORDS.length){i++;text=NC_WORDS.slice(0,i).join(' ');const x=host.querySelector('[data-nc=txt]'); | — |  | [ ] |

## @keyframes usados (10)

```css
@keyframes drawln { 100% { stroke-dashoffset: 0; } }
```

```css
@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes blink { 50% { opacity: 0; } }
```

```css
@keyframes nc-ring { 0% { stroke-dashoffset: 138.2; } 100% { stroke-dashoffset: 0; } }
```

```css
@keyframes nc-bar { 0% { transform: scaleY(0.35); } 100% { transform: scaleY(1); } }
```

```css
@keyframes nc-pulse { 50% { opacity: 0.35; transform: scale(0.8); } }
```

```css
@keyframes nc-spin { 100% { transform: rotate(360deg); } }
```

```css
@keyframes nc-sh { 100% { background-position: -220% 0px; } }
```

```css
@keyframes nc-flash { 0% { background: rgba(242, 238, 230, 0.18); } 100% { background: rgba(242, 238, 230, 0); } }
```

```css
@keyframes nc-up { 0% { transform: translateY(40px); opacity: 0; } 100% { transform: none; opacity: 1; } }
```
