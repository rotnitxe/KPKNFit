# Paridad de profundidad y movimiento · `sp` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 9 · vidrio, 2 · degradados 8 · animaciones 3 · transiciones 11 · transformaciones, 4 · movimiento 3 · escenas del paquete: 19

## Sombras (9)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| sp-S01 | `.glass` (base) | box-shadow: rgba(0, 0, 0, 0.6) 0px 10px 30px, rgba(255, 255, 255, 0.07) 0px 1px inset → preset isla-vidrio | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | DESV-1302 | [~] |
| sp-S02 | `.pl-sheet` (base) | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | sp-aviso, sp-ajustar, sp-anadir, sp-baja, sp-creatina, sp-rapida | screens/nutrition/design/NutritionSupplementsSheets.kt:69 | [x] |
| sp-S03 | `.ribbon i.now` (base) | box-shadow: rgb(0, 0, 0) 0px 0px 0px 2px, rgba(242, 238, 230, 0.667) 0px 0px 0px 3px → preset anillo-doble-actual | sp-hora, sp-rapida | DESV-1302 | [~] |
| sp-S04 | `.sp-wk .today i` | box-shadow: rgba(242, 238, 230, 0.333) 0px 0px 0px 1.5px → preset anillo-foco | sp-modulo, sp-hora, sp-completo, sp-techo, sp-aviso, sp-ajustar | screens/nutrition/design/NutritionSupplementsModule.kt:516 | [x] |
| sp-S05 | `.tok.done` (base) | box-shadow: rgba(67, 209, 140, 0.533) 0px -2px inset → preset subrayado-hecho | sp-modulo, sp-hora, sp-completo, sp-techo, sp-aviso, sp-ajustar | DESV-1302 | [~] |
| sp-S06 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | DESV-1302 | [~] |
| sp-S07 | `.topbar .glass` (base) | box-shadow: rgba(0, 0, 0, 0.467) 0px 8px 24px, rgba(255, 255, 255, 0.11) 0px 1px inset → preset barra-vidrio | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | DESV-1302 | [~] |
| sp-S08 | `.vbar .z` (base) | box-shadow: rgba(67, 209, 140, 0.333) 0px 0px 0px 1px inset → preset interior-hecho-1 | sp-detalle | DESV-1301 | [~] |
| sp-S09 | `.wkc.now` (base) | box-shadow: 0 0 0 2px var(--ink) → preset anillo-actual | sp-hora, sp-rapida | DESV-1302 | [~] |

## Vidrio, desenfoques y filtros (2)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| sp-V01 | `.glass` (base) | backdrop-filter: blur(26px) saturate(1.3) → preset vidrio-hoja | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | DESV-1302 | [~] |
| sp-V02 | `.topbar .glass` (base) | backdrop-filter: blur(18px) saturate(1.8) brightness(0.9) → preset vidrio-barra | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | DESV-1302 | [~] |

## Degradados y resplandores (8)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| sp-G01 | `.glass` (base) | background-image: linear-gradient(140deg, rgba(44, 44, 44, 0.72), rgba(22, 22, 22, 0.69)) → preset vidrio-relleno-hoja | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | DESV-1302 | [~] |
| sp-G02 | `.sess.now` (base) | background-image: linear-gradient(160deg, rgba(244, 154, 110, 0.08), rgb(17, 17, 17) 60%) → preset tinte-hacia-modulo-1 | sp-hora, sp-rapida | DESV-1302 | [~] |
| sp-G03 | `.sp-bot` | background-image: linear-gradient(to top, rgb(0, 0, 0) 58%, rgba(0, 0, 0, 0)) → preset velo-negro-sale-3 | sp-alta, sp-propio, sp-edicion, sp-baja, sp-detalle, sp-detalle-cafeina | DESV-1301 | [~] |
| sp-G04 | `.sp-shade` | background-image: linear-gradient(rgb(12, 12, 12), rgb(0, 0, 0)) → preset relleno-modulo | sp-notif | DESV-1301 | [~] |
| sp-G05 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | DESV-1302 | [~] |
| sp-G06 | `.story .bgart.over::after` (base) | background-image: linear-gradient(rgba(0, 0, 0, 0), rgb(0, 0, 0)) → preset velo-negro-1 | sp-techo, sp-aviso | DESV-1302 | [~] |
| sp-G07 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | DESV-1302 | [~] |
| sp-G08 | `.topbar .glass` (base) | background-image: linear-gradient(150deg, rgba(58, 58, 58, 0.4), rgba(20, 20, 20, 0.35)) → preset vidrio-relleno-barra | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | DESV-1302 | [~] |

## Animaciones (@keyframes) (3)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| sp-A01 | `.sp-ring.bump` | animation: spbump · 0.7s · cubic-bezier(0.3, 1.6, 0.5, 1) · retardo 0s · repite 1 · normal · both → preset rebote · curva resorte-fuerte · dur 700 | sp-inicio, sp-inicio-hecho, sp-rapida | DESV-1301 | [~] |
| sp-A02 | `.sp-row.pop .sp-ic` | animation: sppop · 0.6s · cubic-bezier(0.3, 1.6, 0.5, 1) · retardo 0s · repite 1 · normal · none → preset rebote · curva resorte-fuerte · dur 600 | sp-modulo, sp-hora, sp-completo, sp-techo, sp-aviso, sp-ajustar | screens/nutrition/design/NutritionSupplementsModule.kt:98,365 | [x] |
| sp-A03 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | DESV-1302 | [~] |

## Transiciones (11)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| sp-T01 | `.sp-addb` | transition: background · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | screens/nutrition/design/NutritionSupplementsModule.kt:227 | [x] |
| sp-T02 | `.sp-bar i` | transition: width · 0.5s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 520 | sp-modulo, sp-vacio, sp-hora, sp-completo, sp-techo, sp-aviso | screens/nutrition/design/NutritionSupplementsModule.kt:261 | [x] |
| sp-T03 | `.sp-ch` | transition: background, color · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | sp-ajustar, sp-anadir, sp-alta, sp-propio, sp-edicion, sp-baja | screens/nutrition/design/NutritionSupplementsSheets.kt:166 | [x] |
| sp-T04 | `.sp-hq` | transition: transform, background · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | sp-inicio, sp-rapida | DESV-1301 | [~] |
| sp-T05 | `.sp-ic` | transition: background, color · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | sp-modulo, sp-hora, sp-completo, sp-techo, sp-aviso, sp-ajustar | screens/nutrition/design/NutritionSupplementsModule.kt:367 | [x] |
| sp-T06 | `.sp-mr i::after` | transition: width · 0.5s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 520 | sp-inicio, sp-inicio-hecho, sp-rapida | DESV-1301 | [~] |
| sp-T07 | `.sp-pl` | transition: background, color · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | sp-anadir | screens/nutrition/design/NutritionSupplementsSheets.kt:383 | [x] |
| sp-T08 | `.sp-sg` | transition: background, color · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | sp-vacio | screens/nutrition/design/NutritionSupplementsModule.kt:557 | [x] |
| sp-T09 | `.sp-sw` | transition: background · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | sp-alta, sp-propio, sp-edicion, sp-baja | DESV-1301 | [~] |
| sp-T10 | `.sp-sw::after` | transition: left, background · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | sp-alta, sp-propio, sp-edicion, sp-baja | DESV-1301 | [~] |
| sp-T11 | `.sp-take` | transition: background, transform · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | sp-modulo, sp-hora, sp-techo, sp-aviso, sp-ajustar, sp-anadir | screens/nutrition/design/NutritionSupplementsModule.kt:430 | [x] |

## Transformaciones, máscaras y mezcla (4)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| sp-X01 | `.sp-hq.hold` | transform: scale(0.96) | sp-inicio, sp-rapida | DESV-1301 | [~] |
| sp-X02 | `.sp-take:active` | transform: scale(0.96) | sp-modulo, sp-hora, sp-techo, sp-aviso, sp-ajustar, sp-anadir | screens/nutrition/design/NutritionSupplementsModule.kt:430 | [x] |
| sp-X03 | `.sp-take.hold` | transform: scale(0.96) | sp-modulo, sp-hora, sp-techo, sp-aviso, sp-ajustar, sp-anadir | screens/nutrition/design/NutritionSupplementsModule.kt:430 | [x] |
| sp-X04 | `.story .bgart.over` (base) | mix-blend-mode: screen | sp-techo, sp-aviso | DESV-1302 | [~] |

## Movimiento y estilos hechos en JavaScript (3)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| sp-J01 | `scenes-sp.js:232` | requestAnimationFrame · const t0=performance.now(),dur=matchMedia('(prefers-reduced-motion: reduce)').matches?0:600;const step=t=>{const k=dur?Math.min(1,(t-t0)/dur):1;el.tex | — | screens/nutrition/design/NutritionSupplementsModule.kt:304,332 | [x] |
| sp-J02 | `scenes-sp.js:233` | setTimeout · const say=(text,undo)=>{st.toast={text,undo};clearTimeout(tt);tt=setTimeout(()=>{st.toast=null;red()},5000)}; | — | DESV-1303 | [~] |
| sp-J03 | `scenes-sp.js:241` | setTimeout · timer=setTimeout(()=>{held=true;delete st.hq[id];const it=itOf(st,id);st.sheet={k:'ajustar',id,d:it.dose,w:0};red()},480)}); | — | screens/nutrition/design/NutritionSupplementsModule.kt:447 | [x] |

## @keyframes usados (3)

```css
@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes sppop { 0% { transform: scale(0.7); } 50% { transform: scale(1.18); } 100% { transform: scale(1); } }
```

```css
@keyframes spbump { 0% { transform: scale(1); } 40% { transform: scale(1.07); } 100% { transform: scale(1); } }
```
