# Paridad de profundidad y movimiento · `aj` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 4 · vidrio, 2 · degradados 3 · animaciones 6 · transiciones 6 · transformaciones, 5 · movimiento 3 · escenas del paquete: 20

## Sombras (4)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| aj-S01 | `.aj-sys` | box-shadow: rgba(0, 0, 0, 0.8) 0px 20px 60px → preset modal | aj-permiso-dialogo |  | [ ] |
| aj-S02 | `.glass` (base) | box-shadow: rgba(0, 0, 0, 0.6) 0px 10px 30px, rgba(255, 255, 255, 0.07) 0px 1px inset → preset isla-vidrio | aj-main, aj-unidades, aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas |  | [ ] |
| aj-S03 | `.pl-sheet` (base) | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | aj-unidades, aj-hora, aj-guardar, aj-guardar-error, aj-restaurar, aj-csv |  | [ ] |
| aj-S04 | `.topbar .glass` (base) | box-shadow: rgba(0, 0, 0, 0.467) 0px 8px 24px, rgba(255, 255, 255, 0.11) 0px 1px inset → preset barra-vidrio | aj-main, aj-unidades, aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas |  | [ ] |

## Vidrio, desenfoques y filtros (2)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| aj-V01 | `.glass` (base) | backdrop-filter: blur(26px) saturate(1.3) → preset vidrio-hoja | aj-main, aj-unidades, aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas |  | [ ] |
| aj-V02 | `.topbar .glass` (base) | backdrop-filter: blur(18px) saturate(1.8) brightness(0.9) → preset vidrio-barra | aj-main, aj-unidades, aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas |  | [ ] |

## Degradados y resplandores (3)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| aj-G01 | `.food.ask` (base) | background-image: linear-gradient(170deg, rgba(247, 207, 115, 0.05), rgb(15, 15, 15) 55%) → preset tinte-hacia-modulo-2 | aj-veredictos |  | [ ] |
| aj-G02 | `.glass` (base) | background-image: linear-gradient(140deg, rgba(44, 44, 44, 0.72), rgba(22, 22, 22, 0.69)) → preset vidrio-relleno-hoja | aj-main, aj-unidades, aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas |  | [ ] |
| aj-G03 | `.topbar .glass` (base) | background-image: linear-gradient(150deg, rgba(58, 58, 58, 0.4), rgba(20, 20, 20, 0.35)) → preset vidrio-relleno-barra | aj-main, aj-unidades, aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas |  | [ ] |

## Animaciones (@keyframes) (6)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| aj-A01 | `.aj-in-pop .aj-page` | animation: aj-pop · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-izquierda · curva estandar · dur 280 | aj-main, aj-unidades, aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas |  | [ ] |
| aj-A02 | `.aj-in-push .aj-page` | animation: aj-push · 0.3s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-derecha · curva estandar · dur 280 | aj-main, aj-unidades, aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas |  | [ ] |
| aj-A03 | `.aj-in-scrim` | animation: aj-fade · 0.22s · ease-out · retardo 0s · repite 1 · normal · both → preset fundido-entrada · curva salida · dur 200 | — |  | [ ] |
| aj-A04 | `.aj-in-sheet.pl-sheet` | animation: aj-sheet · 0.36s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-sube · curva estandar · dur 320 | aj-unidades, aj-hora, aj-guardar, aj-guardar-error, aj-restaurar, aj-csv |  | [ ] |
| aj-A05 | `.aj-in-toast` | animation: aj-toast · 0.28s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset entrada-sube · curva estandar · dur 280 | — |  | [ ] |
| aj-A06 | `.aj-spin` | animation: aj-spin · 0.8s · linear · retardo 0s · repite infinite · normal · none → preset giro · curva lineal · dur 800 | — |  | [ ] |

## Transiciones (6)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| aj-T01 | `.aj-hold b` | transition: transform · 0.2s · ease-out · retardo 0s → preset curva salida · dur 200 | aj-borrar |  | [ ] |
| aj-T02 | `.aj-hold.aj-holding b` | transition: transform · 1.2s · linear · retardo 0s → preset curva lineal · dur 1200 | aj-borrar |  | [ ] |
| aj-T03 | `.aj-rad` | transition: border-color · 0.16s · ease · retardo 0s → preset curva suave · dur 160 | aj-unidades, aj-guardar, aj-guardar-error, aj-restaurar |  | [ ] |
| aj-T04 | `.aj-rad i` | transition: transform · 0.18s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 160 | aj-unidades, aj-guardar, aj-guardar-error, aj-restaurar |  | [ ] |
| aj-T05 | `.aj-sw` | transition: background · 0.18s · ease · retardo 0s → preset curva suave · dur 160 | aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas, aj-recordatorios-permiso |  | [ ] |
| aj-T06 | `.aj-sw i` | transition: transform, background · 0.22s, 0.18s · cubic-bezier(0.2, 0.8, 0.2, 1), ease · retardo 0s, 0s → preset curva estandar/suave · dur 200/160 | aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas, aj-recordatorios-permiso |  | [ ] |

## Transformaciones, máscaras y mezcla (5)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| aj-X01 | `.aj-hold b` | transform: scaleX(0) | aj-borrar |  | [ ] |
| aj-X02 | `.aj-hold.aj-holding b` | transform: scaleX(1) | aj-borrar |  | [ ] |
| aj-X03 | `.aj-opt.aj-on .aj-rad i` | transform: scale(1) | aj-unidades, aj-recordatorios, aj-hora, aj-recordatorios-alarmas, aj-recordatorios-permiso, aj-guardar |  | [ ] |
| aj-X04 | `.aj-rad i` | transform: scale(0) | aj-unidades, aj-guardar, aj-guardar-error, aj-restaurar |  | [ ] |
| aj-X05 | `.aj-sw.aj-on i` | transform: translateX(20px) | aj-unidades, aj-recordatorios, aj-recordatorios-vacio, aj-hora, aj-recordatorios-alarmas, aj-recordatorios-permiso |  | [ ] |

## Movimiento y estilos hechos en JavaScript (3)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| aj-J01 | `scenes-aj.js:220` | setTimeout · const flash=(msg,kind)=>{S.toast={msg,kind};S.toastNew=true;clearTimeout(tid);tid=setTimeout(()=>{S.toast=null;draw()},2900)}; | — |  | [ ] |
| aj-J02 | `scenes-aj.js:221` | setTimeout · const later=(ms,fn)=>setTimeout(()=>{fn();draw()},ms); | — |  | [ ] |
| aj-J03 | `scenes-aj.js:284` | setTimeout · hold=setTimeout(()=>{hold=null;act(t.dataset.a,t)},1200); | — |  | [ ] |

## @keyframes usados (6)

```css
@keyframes aj-spin { 100% { transform: rotate(360deg); } }
```

```css
@keyframes aj-sheet { 0% { transform: translateY(40px); opacity: 0; } 100% { transform: none; opacity: 1; } }
```

```css
@keyframes aj-fade { 0% { opacity: 0; } 100% { opacity: 1; } }
```

```css
@keyframes aj-push { 0% { transform: translateX(24px); opacity: 0; } 100% { transform: none; opacity: 1; } }
```

```css
@keyframes aj-pop { 0% { transform: translateX(-24px); opacity: 0; } 100% { transform: none; opacity: 1; } }
```

```css
@keyframes aj-toast { 0% { transform: translateY(16px); opacity: 0; } 100% { transform: none; opacity: 1; } }
```
