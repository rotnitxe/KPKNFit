# Paridad de profundidad y movimiento · `lv` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 31 · vidrio, 0 · degradados 3 · animaciones 22 · transiciones 21 · transformaciones, 16 · movimiento 22 · escenas del paquete: 35

## Sombras (31)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lv-S01 | `.ab.cur` (base) | box-shadow: rgba(242, 238, 230, 0.08) 0px 0px 0px 4px → preset aureola-foco | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | DESV-650 | [~] |
| lv-S02 | `.lv-car-lens` | box-shadow: rgba(0, 0, 0, 0.6) 0px 6px 16px, rgba(255, 255, 255, 0.133) 0px 0px 0px 1px → preset caida-6-16-capas-1 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `RepsCarousel.kt:357` | [x] |
| lv-S03 | `.lv-car-lens.below` | box-shadow: rgba(0, 0, 0, 0.6) 0px 6px 16px, rgba(255, 255, 255, 0.133) 0px 0px 0px 1px, rgb(244, 154, 110) 0px 2px 0px → preset caida-6-16-capas-2 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `RepsCarousel.kt:357` | [x] |
| lv-S04 | `.lv-car-lens.fail` | box-shadow: rgba(255, 155, 146, 0.4) 0px 0px 0px 1px inset → preset interior-peligro-1 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `RepsCarousel.kt:357` | [x] |
| lv-S05 | `.lv-car:focus-visible` | box-shadow: rgba(242, 238, 230, 0.333) 0px 0px 0px 2px → preset anillo-foco | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `RepsCarousel.kt:305` | [x] |
| lv-S06 | `.lv-card` | box-shadow: rgba(0, 0, 0, 0.8) 0px 14px 34px → preset aviso | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `SetExecutionCard.kt:2258` | [x] |
| lv-S07 | `.lv-chip` | box-shadow: rgba(255, 255, 255, 0.07) 0px 0px 0px 1px inset → preset borde-interior | lv-rom-editor |  | [ ] |
| lv-S08 | `.lv-eff:focus-visible` | box-shadow: rgba(242, 238, 230, 0.333) 0px 0px 0px 2px → preset anillo-foco | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-escala | `LiveEffortRange.kt:156` | [x] |
| lv-S09 | `.lv-ethumb` | box-shadow: rgba(255, 255, 255, 0.11) 0px 0px 0px 1px → preset borde-exterior | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-escala | `LiveEffortRange.kt:256` | [x] |
| lv-S10 | `.lv-ghost` | box-shadow: rgba(0, 0, 0, 0.667) 0px 10px 22px → preset caida-10-22 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo |  | [ ] |
| lv-S11 | `.lv-island` | box-shadow: rgba(0, 0, 0, 0.667) 0px 10px 28px → preset flotante | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-escala |  | [ ] |
| lv-S12 | `.lv-pill.isa::after` | box-shadow: rgb(255, 255, 255) 0px 0px 0px 1.5px inset → preset interior-blanco-1p5 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-S13 | `.lv-pill.isr` | box-shadow: rgba(244, 154, 110, 0.333) 0px 0px 0px 1px inset → preset interior-musculo-1 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-S14 | `.lv-pill.next` | box-shadow: rgba(255, 255, 255, 0.11) 0px 0px 0px 1.5px inset → preset borde-interior | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-S15 | `.lv-pill.prep` | box-shadow: rgba(255, 255, 255, 0.173) 0px 0px 0px 1.5px inset → preset interior-blanco-1-tenue | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-S16 | `.lv-road` | box-shadow: rgba(0, 0, 0, 0.733) 0px 10px 28px → preset flotante | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-S17 | `.lv-rom .more i` | box-shadow: rgb(17, 17, 17) 0px 0px 0px 2px → preset corte-hoja | lv-rom-tarjeta, lv-rom-hoja, lv-rom-hoja-on, lv-rom-corporal, lv-rom-tiempo | `RomBand.kt:132` | [x] |
| lv-S18 | `.lv-rsl:focus-visible` | box-shadow: rgba(242, 238, 230, 0.333) 0px 0px 0px 2px → preset anillo-foco | lv-rom-tarjeta, lv-rom-hoja, lv-rom-hoja-on, lv-rom-corporal, lv-rom-tiempo | `RomBand.kt:176` | [x] |
| lv-S19 | `.lv-rthumb` | box-shadow: rgba(0, 0, 0, 0.667) 0px 2px 8px, rgb(17, 17, 17) 0px 0px 0px 4px → preset caida-2-8-capas | lv-rom-tarjeta, lv-rom-hoja, lv-rom-hoja-on, lv-rom-corporal, lv-rom-tiempo | `RomBand.kt:221` | [x] |
| lv-S20 | `.lv-rtip` | box-shadow: rgba(0, 0, 0, 0.8) 0px 8px 20px, rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset → preset caida-8-20-capas | lv-rom-tarjeta, lv-rom-hoja, lv-rom-hoja-on, lv-rom-corporal, lv-rom-tiempo | `RomBand.kt:231` | [x] |
| lv-S21 | `.lv-sh` | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | lv-rom-editor, lv-rom-hoja, lv-rom-hoja-on, lv-rom-historial | DESV-601 | [~] |
| lv-S22 | `.lv-srow .in` | box-shadow: rgba(255, 255, 255, 0.06) 0px 0px 0px 1px inset → preset borde-interior | lv-rom-hoja, lv-rom-hoja-on | `LiveSetDetailSheet.kt:189` | [x] |
| lv-S23 | `.lv-zch button` | box-shadow: rgba(255, 255, 255, 0.07) 0px 0px 0px 1px inset → preset borde-interior | lv-rom-hoja, lv-rom-hoja-on | `LiveSetDetailSheet.kt:243` | [x] |
| lv-S24 | `.lv2` | box-shadow: rgba(0, 0, 0, 0.7) 0px 10px 28px, rgba(0, 0, 0, 0.5) 0px 2px 6px → preset caida-10-28-capas | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveSessionLine.kt:256 | [x] |
| lv-S25 | `.lv2-rec` | box-shadow: rgba(0, 0, 0, 0.35) 0px 4px 14px → preset caida-4-14 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveSessionLine.kt:623 | [x] |
| lv-S26 | `.lv2-seg.cur` | box-shadow: rgba(242, 238, 230, 0.22) 0px 0px 10px → preset resplandor-crema-10 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveSessionLine.kt:555 | [x] |
| lv-S27 | `.lv2-seg.prov` | box-shadow: rgb(242, 238, 230) 0px 0px 0px 1.5px inset, rgba(242, 238, 230, 0.3) 0px 0px 12px → preset resplandor-crema-12-capas | lv-road-b-reposo, lv-road-b-registrada, lv-road-b-ultima, lv-road-b-ultimo, lv-road-b-descanso, lv-road-b-prep | LiveSessionLine.kt:560 | [x] |
| lv-S28 | `.lv2-sheet` | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | `LiveSheetKit.kt:80` (spec) · `LiveSessionSheets.kt:133` | [x] |
| lv-S29 | `.lv2-toast` | box-shadow: rgba(0, 0, 0, 0.8) 0px 10px 30px → preset aviso | — | DESV-652 | [~] |
| lv-S30 | `.tok.done` (base) | box-shadow: rgba(67, 209, 140, 0.533) 0px -2px inset → preset subrayado-hecho | lv-rom-descanso, lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso | DESV-650 | [~] |
| lv-S31 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | lv-rom-hoja-on | DESV-602 | [~] |

## Degradados y resplandores (3)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lv-G01 | `.lv-car-rail` | background-image: linear-gradient(90deg, rgba(242, 238, 230, 0.753), rgb(242, 238, 230)) → preset relleno-horizontal-crema-2 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `RepsCarousel.kt:338` | [x] |
| lv-G02 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | lv-rom-hoja-on | DESV-602 | [~] |
| lv-G03 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | lv-rom-hoja-on | DESV-602 | [~] |

## Animaciones (@keyframes) (22)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lv-A01 | `.draw.go .ln` (base) | animation: drawln · 1.6s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1600 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | DESV-650 | [~] |
| lv-A02 | `.go .burst .ln, .burst.go .ln` (base) | animation: drawln · 1.1s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva estandar · dur 1000 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | DESV-650 | [~] |
| lv-A03 | `.lv-car-lens.pulse` | animation: lv-snap · 0.24s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset rebote · curva estandar · dur 240 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `RepsCarousel.kt:105` | [x] |
| lv-A04 | `.lv-edrow .tx small .rm` | animation: lv-pin · 0.34s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset aparece-escala · curva estandar · dur 320 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-600 | [~] |
| lv-A05 | `.lv-ethumb.pop` | animation: lv-pop · 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset rebote · curva estandar · dur 200 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-escala | `LiveEffortRange.kt:119` | [x] |
| lv-A06 | `.lv-pill.in` | animation: lv-pin · 0.38s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset aparece-escala · curva estandar · dur 400 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-A07 | `.lv-pill.isa::after` | animation: lv-breathe · 2.2s · ease-in-out · retardo 0s · repite infinite · normal · none → preset pulso · curva vaiven · dur 2400 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-A08 | `.lv-pill.just .ck` | animation: lv-ck · 0.46s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0.06s · repite 1 · normal · backwards → preset dibujo-trazo · curva estandar · dur 460 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-A09 | `.lv-rec.lvgo .wipe` | animation: lv-wipe · 0.55s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset barrido-ancho · curva estandar · dur 520 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-A10 | `.lv-road .swap` | animation: lv-swap · 0.26s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 240 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-A11 | `.lv-rthumb.pop` | animation: lv-pop · 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset rebote · curva estandar · dur 200 | lv-rom-tarjeta, lv-rom-hoja, lv-rom-hoja-on, lv-rom-corporal, lv-rom-tiempo | `RomBand.kt:160` | [x] |
| lv-A12 | `.lv2-arc.an` | animation: lv2-arc · 700ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset dibujo-trazo · curva estandar · dur 700 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveRoadmapMotion.kt:90; LiveSessionLine.kt:362 | [x] |
| lv-A13 | `.lv2-num span.roll` | animation: lv2-roll · 260ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 240 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveRoadmapMotion.kt:43; LiveSessionLine.kt:452 | [x] |
| lv-A14 | `.lv2-rec.lv2go .ck` | animation: lv2-ck · 360ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 60ms · repite 1 · normal · backwards → preset dibujo-trazo · curva estandar · dur 320 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | LiveRoadmapMotion.kt:51; LiveSessionLine.kt:620 | [x] |
| lv-A15 | `.lv2-rec.lv2go .wipe` | animation: lv2-wipe · 550ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset barrido-ancho · curva estandar · dur 520 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | LiveRoadmapMotion.kt:60; LiveSessionLine.kt:619 | [x] |
| lv-A16 | `.lv2-ruta.open .lv2-chs span` | animation: lv2-chip · 220ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo calc(160ms + var(--i)*40ms) · repite 1 · normal · forwards → preset entrada-implicita · curva estandar · dur 200 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveRoadmapMotion.kt:81; LiveRouteSheet.kt:283 | [x] |
| lv-A17 | `.lv2-seg > i.an` | animation: lv2-fill · 420ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset barrido-ancho · curva estandar · dur 400 | lv-road-b-reposo, lv-road-b-registrada, lv-road-b-ultima, lv-road-b-ultimo, lv-road-b-descanso, lv-road-b-prep | LiveRoadmapMotion.kt:96; LiveSessionLine.kt:550 | [x] |
| lv-A18 | `.lv2-seg.prov.tick` | animation: lv2-tick · 80ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset asentar · curva estandar · dur 80 | lv-road-b-reposo, lv-road-b-registrada, lv-road-b-ultima, lv-road-b-ultimo, lv-road-b-descanso, lv-road-b-prep | LiveRoadmapMotion.kt:70; LiveSessionLine.kt:558 | [x] |
| lv-A19 | `.lv2-t.sl` | animation: lv2-sl · 220ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-izquierda · curva estandar · dur 200 | lv-rom-historial, lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso | LiveRoadmapMotion.kt:19; LiveSessionLine.kt:365 | [x] |
| lv-A20 | `.lv2-t.sr` | animation: lv2-sr · 220ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-derecha · curva estandar · dur 200 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveRoadmapMotion.kt:27; LiveSessionLine.kt:366 | [x] |
| lv-A21 | `.lv2-t.su` | animation: lv2-su · 220ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 200 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveRoadmapMotion.kt:35; LiveSessionLine.kt:367 | [x] |
| lv-A22 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | lv-rom-hoja-on | DESV-650 | [~] |

## Transiciones (21)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lv-T01 | `.lv-car-lens` | transition: transform · 0.09s · ease · retardo 0s → preset curva suave · dur 90 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `RepsCarousel.kt:183` | [x] |
| lv-T02 | `.lv-car-rail` | transition: opacity · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `RepsCarousel.kt:338` | [x] |
| lv-T03 | `.lv-nav b` | transition: background · 0.15s · ease · retardo 0s → preset curva suave · dur 160 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-T04 | `.lv-pill` | transition: flex-grow, background, color · 0.32s, 0.2s, 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1), ease, ease · retardo 0s, 0s, 0s → preset curva estandar/suave · dur 320/200 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-T05 | `.lv-pill.isr .dr` | transition: width · 0.25s · linear · retardo 0s → preset curva lineal · dur 240 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-T06 | `.lv-prog .arc` | transition: stroke-dashoffset · 0.7s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 700 | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | LiveSessionLine.kt:362 | [x] |
| lv-T07 | `.lv-rowrap` | transition: height, opacity · 0.26s, 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1), ease · retardo 0s, 0s → preset curva estandar/suave · dur 240/200 | lv-rom-hoja, lv-rom-hoja-on | `LiveSetDetailSheet.kt:148` | [x] |
| lv-T08 | `.lv-rtip` | transition: opacity, transform · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | lv-rom-tarjeta, lv-rom-hoja, lv-rom-hoja-on, lv-rom-corporal, lv-rom-tiempo | `RomBand.kt:162` | [x] |
| lv-T09 | `.lv-sw` | transition: background · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | lv-rom-editor, lv-rom-hoja, lv-rom-hoja-on | `RomBand.kt:256` | [x] |
| lv-T10 | `.lv-sw::after` | transition: transform, background · 0.22s, 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1), ease · retardo 0s, 0s → preset curva estandar/suave · dur 200 | lv-rom-editor, lv-rom-hoja, lv-rom-hoja-on | `RomBand.kt:256` | [x] |
| lv-T11 | `.lv-zch button` | transition: background, color · 0.18s, 0.18s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | lv-rom-hoja, lv-rom-hoja-on | `LiveSetDetailSheet.kt:243` | [x] |
| lv-T12 | `.lv2-b1` | transition: background · 90ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 90 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveSessionLine.kt:358 | [x] |
| lv-T13 | `.lv2-drain` | transition: width · 250ms · linear · retardo 0s → preset curva lineal · dur 240 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveSessionLine.kt:360 | [x] |
| lv-T14 | `.lv2-nav b` | transition: background · 90ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 90 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveSessionLine.kt:668 | [x] |
| lv-T15 | `.lv2-rec` | transition: transform, background · 160ms, 160ms · cubic-bezier(0.2, 0.8, 0.2, 1), cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva estandar · dur 160 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveSessionLine.kt:624 | [x] |
| lv-T16 | `.lv2-ruta.open .lv2-scrim` | transition: opacity · 240ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 240 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | `LiveSheetKit.kt:80` (spec) · `LiveSessionSheets.kt:133` | [x] |
| lv-T17 | `.lv2-ruta.open .lv2-sheet` | transition: transform · 420ms · cubic-bezier(0.22, 1.12, 0.36, 1) · retardo 0s → preset curva resorte-suave · dur 400 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | `LiveSheetKit.kt:80` (spec) · `LiveSessionSheets.kt:133` | [x] |
| lv-T18 | `.lv2-scrim` | transition: opacity · 200ms · cubic-bezier(0.4, 0, 1, 1) · retardo 0s → preset curva acelera · dur 200 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | `LiveSheetKit.kt:80` (spec) · `LiveSessionSheets.kt:133` | [x] |
| lv-T19 | `.lv2-seg` | transition: height, background, box-shadow · 280ms, 280ms, 280ms · cubic-bezier(0.2, 0.8, 0.2, 1), cubic-bezier(0.2, 0.8, 0.2, 1), cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s, 0s → preset curva estandar · dur 280 | lv-road-b-reposo, lv-road-b-registrada, lv-road-b-ultima, lv-road-b-ultimo, lv-road-b-descanso, lv-road-b-prep | LiveSessionLine.kt:548 | [x] |
| lv-T20 | `.lv2-seg > i` | transition: width · 420ms · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 400 | lv-road-b-reposo, lv-road-b-registrada, lv-road-b-ultima, lv-road-b-ultimo, lv-road-b-descanso, lv-road-b-prep | LiveSessionLine.kt:550 | [x] |
| lv-T21 | `.lv2-sheet` | transition: transform · 260ms · cubic-bezier(0.4, 0, 1, 1) · retardo 0s → preset curva acelera · dur 240 | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | `LiveSheetKit.kt:80` (spec) · `LiveSessionSheets.kt:133` | [x] |

## Transformaciones, máscaras y mezcla (16)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lv-X01 | `.lv-car` | mask-image: linear-gradient(90deg, transparent 0px, rgb(0, 0, 0) 15%, rgb(0, 0, 0) 85%, transparent 100%) | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `RepsCarousel.kt:321` | [x] |
| lv-X02 | `.lv-car-it` | will-change: transform | — | `RepsCarousel.kt:377` | [x] |
| lv-X03 | `.lv-car-it span` | will-change: transform, opacity | — | `RepsCarousel.kt:377` | [x] |
| lv-X04 | `.lv-car-lens.tk` | transform: scale(0.965) | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `RepsCarousel.kt:183` | [x] |
| lv-X05 | `.lv-car-rail` | will-change: transform | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | `LiveEffortFx.kt:36` | [x] |
| lv-X06 | `.lv-ethumb` | will-change: transform | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-escala | `LiveEffortRange.kt:156` | [x] |
| lv-X07 | `.lv-rail.fl` | mask-image: linear-gradient(90deg, transparent 0px, rgb(0, 0, 0) 12%) | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-X08 | `.lv-rail.fl.fr` | mask-image: linear-gradient(90deg, transparent 0px, rgb(0, 0, 0) 12%, rgb(0, 0, 0) 88%, transparent 100%) | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-X09 | `.lv-rail.fr` | mask-image: linear-gradient(90deg, rgb(0, 0, 0) 88%, transparent 100%) | lv-reps-rango, lv-reps-fijo, lv-reps-amrap, lv-reps-fallo, lv-esf-vivo, lv-esf-fallo | DESV-651 | [~] |
| lv-X10 | `.lv-rest .eclipse-l svg` | transform: rotate(-90deg) | lv-rom-descanso | `RestRef02.kt:189` | [x] |
| lv-X11 | `.lv-rtip` | transform: translateX(-50%) translateY(4px) | lv-rom-tarjeta, lv-rom-hoja, lv-rom-hoja-on, lv-rom-corporal, lv-rom-tiempo | `RomBand.kt:162` | [x] |
| lv-X12 | `.lv-rtip.on` | transform: translateX(-50%) translateY(0px) | lv-rom-tarjeta, lv-rom-hoja, lv-rom-hoja-on, lv-rom-corporal, lv-rom-tiempo | `RomBand.kt:162` | [x] |
| lv-X13 | `.lv-sw.on::after` | transform: translateX(22px) | lv-rom-editor, lv-rom-hoja, lv-rom-hoja-on | `RomBand.kt:256` | [x] |
| lv-X14 | `.lv2-chs span` | transform: translateY(6px) | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveRouteSheet.kt:283 | [x] |
| lv-X15 | `.lv2-rec:active` | transform: scale(0.97) | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | LiveSessionLine.kt:624 | [x] |
| lv-X16 | `.lv2-sheet` | transform: translateY(100%) | lv-road-a-reposo, lv-road-a-registrada, lv-road-a-ultima, lv-road-a-ultimo, lv-road-a-descanso, lv-road-a-prep | `LiveSheetKit.kt:80` (spec) · `LiveSessionSheets.kt:133` | [x] |

## Movimiento y estilos hechos en JavaScript (22)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| lv-J01 | `scenes-lv.js:183` | estilo en línea desde JS · it.style.transform='translate3d('+(c+d*CELL-CELL/2).toFixed(1)+'px,0,0)'; | — | `RepsCarousel.kt:377` | [x] |
| lv-J02 | `scenes-lv.js:185` | estilo en línea desde JS · sp.style.transform='scale('+s.toFixed(3)+')';sp.style.opacity=clamp(a,0,1).toFixed(3);sp.style.color=col; | — | `RepsCarousel.kt:377` | [x] |
| lv-J03 | `scenes-lv.js:190` | estilo en línea desde JS · rail.style.transform='translateX('+x0.toFixed(1)+'px)';rail.style.width=w.toFixed(1)+'px'; | — | `RepsCarousel.kt:338` | [x] |
| lv-J04 | `scenes-lv.js:194` | setTimeout · function setVal(v){if(v!==val){val=v;el.setAttribute('aria-valuenow',v);head_();lens.classList.add('tk');clearTimeout(lens._t);lens._t=setTimeout(()=> | — | `RepsCarousel.kt:183` | [x] |
| lv-J05 | `scenes-lv.js:201` | requestAnimationFrame · const step=t=>{const k=Math.min(1,(t-t0)/ms),e=1-Math.pow(1-k,3);pos=lvLerp(from,target,e);setVal(Math.round(pos));draw();if(k<1)anim=requestAnimation | — | `RepsCarousel.kt:200` | [x] |
| lv-J06 | `scenes-lv.js:202` | requestAnimationFrame · anim=requestAnimationFrame(step); | — | `RepsCarousel.kt:200` | [x] |
| lv-J07 | `scenes-lv.js:225` | setTimeout · el.addEventListener('wheel',e=>{if(Math.abs(e.deltaX)<=Math.abs(e.deltaY))return;e.preventDefault();cancelAnimationFrame(anim);pos=clamp(pos+e.deltaX/ | — | `RepsCarousel.kt:280` | [x] |
| lv-J08 | `scenes-lv.js:258` | estilo en línea desde JS · if(idx==null){fill.style.width='0';fill.style.background='';fill.style.boxShadow='none'} | — | `LiveEffortRange.kt:89` | [x] |
| lv-J09 | `scenes-lv.js:261` | estilo en línea desde JS · if(f){const a=Math.max(0,(1-f.tail)*100);fill.style.background=`linear-gradient(90deg,#e6e1d5 0,#e6e1d5 ${a.toFixed(1)}%,${f.mid} ${(a+(100-a)*.55).to | — | `LiveEffortRange.kt:89` | [x] |
| lv-J10 | `scenes-lv.js:262` | estilo en línea desde JS · else{fill.style.background='';fill.style.boxShadow='none'} | — | `LiveEffortRange.kt:89` | [x] |
| lv-J11 | `scenes-lv.js:266` | estilo en línea desde JS · thumb.style.boxShadow=f?`0 0 0 1px #ffffff1c,0 0 ${(6+L*3.2).toFixed(0)}px rgba(244,154,110,${(.2+hot*.55).toFixed(2)})`:'0 0 0 1px #ffffff1c'; | — | `LiveEffortRange.kt:89` | [x] |
| lv-J12 | `scenes-lv.js:281` | requestAnimationFrame · raf=requestAnimationFrame(frame); | — | `LiveEffortFx.kt:36` | [x] |
| lv-J13 | `scenes-lv.js:355` | requestAnimationFrame · if(still){draw(0.016)}else{for(let k=0;k<110;k++){clock+=1/60;draw(1/60)}raf=requestAnimationFrame(frame)} | — | `LiveEffortFx.kt:36` | [x] |
| lv-J14 | `scenes-lv.js:389` | setTimeout · const up=()=>{drag=false;setTimeout(()=>tip.classList.remove('on'),550)};sl.addEventListener('pointerup',up);sl.addEventListener('pointercancel',up); | — | `RomBand.kt:164` | [x] |
| lv-J15 | `scenes-lv.js:400` | estilo en línea desde JS · if(tg==='rom'){ph.querySelector('.lv').classList.toggle('lv-rom-on',on);ph.querySelectorAll('.lv-rowrap[data-rom]').forEach(r=>{r.style.height=on?r.sc | — | `LiveSetDetailSheet.kt:148` | [x] |
| lv-J16 | `scenes-lv.js:437` | declaración CSS dentro del marcado · toast.style.cssText='position:absolute;left:24px;right:24px;bottom:150px;z-index:90;padding:12px 14px;border-radius:16px;background:#242426;box-shadow | — | DESV-651 | [~] |
| lv-J17 | `scenes-lv.js:438` | setTimeout · toast.textContent=t;ph.querySelector('.lv').appendChild(toast);setTimeout(()=>{if(toast){toast.remove();toast=null}},3800); | — | DESV-651 | [~] |
| lv-J18 | `scenes-lv.js:444` | setInterval · timer=setInterval(()=>{ | — | DESV-651 | [~] |
| lv-J19 | `scenes-lv.js:468` | setTimeout · setTimeout(()=>{S.just=null;startRest(S.cur);sync(model(),true)},lvReduce()?0:520); | — | DESV-651 | [~] |
| lv-J20 | `scenes-lv.js:737` | setTimeout · const say=t=>{if(toast)toast.remove();toast=document.createElement('div');toast.className='lv2-toast';toast.textContent=t;lv.appendChild(toast);setTim | — | DESV-652 | [~] |
| lv-J21 | `scenes-lv.js:764` | setInterval · timer=setInterval(()=>{ | — | DESV-652 | [~] |
| lv-J22 | `scenes-lv.js:782` | setTimeout · setTimeout(()=>{if(root.isConnected)startRest()},550); | — | DESV-654 | [~] |

## @keyframes usados (19)

```css
@keyframes drawln { 100% { stroke-dashoffset: 0; } }
```

```css
@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes lv-snap { 0% { transform: scale(0.93); } 55% { transform: scale(1.045); } 100% { transform: scale(1); } }
```

```css
@keyframes lv-pop { 0% { transform: scale(0.88); } 60% { transform: scale(1.14); } 100% { transform: scale(1); } }
```

```css
@keyframes lv-breathe { 0%, 100% { opacity: 0; } 50% { opacity: 0.55; } }
```

```css
@keyframes lv-pin { 0% { transform: scale(0.86); opacity: 0.2; } 100% { transform: scale(1); opacity: 1; } }
```

```css
@keyframes lv-ck { 0% { stroke-dashoffset: 24; } 100% { stroke-dashoffset: 0; } }
```

```css
@keyframes lv-wipe { 0% { width: 0px; opacity: 0.9; } 70% { width: 100%; opacity: 0.9; } 100% { width: 100%; opacity: 0; } }
```

```css
@keyframes lv-swap { 0% { opacity: 0; transform: translateY(5px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes lv2-wipe { 0% { width: 0px; opacity: 0.9; } 70% { width: 100%; opacity: 0.9; } 100% { width: 100%; opacity: 0; } }
```

```css
@keyframes lv2-ck { 0% { stroke-dashoffset: 24; } 100% { stroke-dashoffset: 0; } }
```

```css
@keyframes lv2-sl { 0% { opacity: 0; transform: translateX(-14px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes lv2-sr { 0% { opacity: 0; transform: translateX(14px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes lv2-su { 0% { opacity: 0; transform: translateY(8px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes lv2-roll { 0% { opacity: 0; transform: translateY(60%); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes lv2-arc { 0% { stroke-dashoffset: var(--f); } 100% { stroke-dashoffset: var(--t); } }
```

```css
@keyframes lv2-fill { 0% { width: var(--pf); } 100% { width: var(--p); } }
```

```css
@keyframes lv2-chip { 100% { opacity: 1; transform: none; } }
```

```css
@keyframes lv2-tick { 0% { transform: scale(1.12, 1.3); } 100% { transform: none; } }
```
