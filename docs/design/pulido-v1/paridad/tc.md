# Paridad de profundidad y movimiento · `tc` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 20 · vidrio, 6 · degradados 12 · animaciones 9 · transiciones 20 · transformaciones, 13 · movimiento 20 · escenas del paquete: 33

## Sombras (20)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| tc-S01 | `.tc-band.sel` | box-shadow: 0 0 0 1.5px var(--ink) → preset anillo-actual | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-S02 | `.tc-circ` | box-shadow: rgba(0, 0, 0, 0.667) 0px 0px 0px 1px, rgba(247, 207, 115, 0.333) 0px 0px 22px, rgba(0, 0, 0, 0.467) 0px 0px 0px 1px inset → preset resplandor-energia-22-capas | tc-marcar, tc-marcar-otro |  | [ ] |
| tc-S03 | `.tc-dlg` | box-shadow: rgba(0, 0, 0, 0.933) 0px 24px 70px → preset caida-24-70 | tc-marcar-otro |  | [ ] |
| tc-S04 | `.tc-fs` | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.063) 0px 1px inset → preset hoja | tc-perfil-importar, tc-perfil-importar-vacio, tc-perfil-guardando, tc-ayuda, tc-elegir-movimiento |  | [ ] |
| tc-S05 | `.tc-glass` | box-shadow: rgba(0, 0, 0, 0.667) 0px 10px 30px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset isla-vidrio | tc-grabar, tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal |  | [ ] |
| tc-S06 | `.tc-head` | box-shadow: rgba(242, 238, 230, 0.6) 0px 0px 8px → preset resplandor-crema-8 | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito, tc-cola |  | [ ] |
| tc-S07 | `.tc-loupe` | box-shadow: rgba(0, 0, 0, 0.8) 0px 8px 28px → preset caida-8-28 | tc-marcar, tc-marcar-otro |  | [ ] |
| tc-S08 | `.tc-menu` | box-shadow: rgba(0, 0, 0, 0.867) 0px 18px 50px → preset caida-18-50 | tc-menu |  | [ ] |
| tc-S09 | `.tc-ob` | box-shadow: rgba(247, 207, 115, 0.19) 0px 0px 0px 3px → preset anillo-aviso | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-S10 | `.tc-q .tc-qd` | box-shadow: rgba(247, 207, 115, 0.15) 0px 0px 0px 3px → preset anillo-aviso | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-S11 | `.tc-q.bad .tc-qd` | box-shadow: rgba(255, 155, 146, 0.15) 0px 0px 0px 3px → preset anillo-error | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-S12 | `.tc-q.ok .tc-qd` | box-shadow: rgba(67, 209, 140, 0.15) 0px 0px 0px 3px → preset anillo-hecho | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-S13 | `.tc-root.tc-mini .tc-vid` | box-shadow: rgba(255, 255, 255, 0.15) 0px 0px 0px 1px → preset borde-exterior | tc-perfil-importar, tc-perfil-importar-vacio, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito |  | [ ] |
| tc-S14 | `.tc-sd` | box-shadow: rgba(242, 238, 230, 0.2) 0px 0px 0px 4px → preset anillo-crema-4-suave | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |
| tc-S15 | `.tc-thumb` | box-shadow: rgba(255, 255, 255, 0.11) 0px 0px 0px 1px → preset borde-exterior | tc-perfil-importar, tc-perfil-guardando |  | [ ] |
| tc-S16 | `.tc-toast` | box-shadow: rgba(0, 0, 0, 0.667) 0px 10px 30px → preset flotante | tc-aviso |  | [ ] |
| tc-S17 | `.tc-vfi` | box-shadow: rgba(255, 255, 255, 0.11) 0px 0px 0px 1px, rgb(0, 0, 0) 0px 20px 60px → preset dialogo-vidrio | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |
| tc-S18 | `.tc-vfi .scan` | box-shadow: rgba(242, 238, 230, 0.333) 0px 0px 18px 2px → preset resplandor-crema-18 | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |
| tc-S19 | `.tok.done` (base) | box-shadow: rgba(67, 209, 140, 0.533) 0px -2px inset → preset subrayado-hecho | tc-exito, tc-actualizando |  | [ ] |
| tc-S20 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-resultado-vivo, tc-resultado-perfil, tc-repeticion |  | [ ] |

## Vidrio, desenfoques y filtros (6)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| tc-V01 | `.tc-cam .tc-svg` | filter: brightness(0.85) → preset miniatura-apagada | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito, tc-cola |  | [ ] |
| tc-V02 | `.tc-glass` | backdrop-filter: blur(24px) saturate(1.5) → preset vidrio-isla | tc-grabar, tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal |  | [ ] |
| tc-V03 | `.tc-mkf .tc-svg` | filter: brightness(0.9) → preset miniatura-apagada | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito, tc-cola |  | [ ] |
| tc-V04 | `.tc-still .tc-svg` | filter: brightness(0.55) saturate(0.7) → preset video-apagado | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito, tc-cola |  | [ ] |
| tc-V05 | `.tc-thumb .tc-svg` | filter: brightness(0.8) → preset miniatura-apagada | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito, tc-cola |  | [ ] |
| tc-V06 | `.tc-vfi .tc-svg` | filter: brightness(0.62) saturate(0.8) → preset video-apagado | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito, tc-cola |  | [ ] |

## Degradados y resplandores (12)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| tc-G01 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-resultado-vivo, tc-resultado-perfil, tc-repeticion |  | [ ] |
| tc-G02 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-resultado-vivo, tc-resultado-perfil, tc-repeticion |  | [ ] |
| tc-G03 | `.tc-band` | background-image: linear-gradient(rgb(143, 178, 255), rgb(110, 143, 208)) → preset relleno-cardio | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-G04 | `.tc-band.part` | background-image: repeating-linear-gradient(135deg, rgb(247, 207, 115) 0px, rgb(247, 207, 115) 3px, rgba(247, 207, 115, 0.333) 3px, rgba(247, 207, 115, 0.333) 6px) → preset rayas-energia | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-G05 | `.tc-bar.part i` | background-image: repeating-linear-gradient(135deg, rgba(255, 255, 255, 0.19) 0px, rgba(255, 255, 255, 0.19) 3px, rgba(255, 255, 255, 0.063) 3px, rgba(255, 255, 255, 0.063) 6px) → preset rayas-blanco | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-G06 | `.tc-bar.part.sel i` | background-image: repeating-linear-gradient(135deg, rgb(242, 238, 230) 0px, rgb(242, 238, 230) 3px, rgba(242, 238, 230, 0.667) 3px, rgba(242, 238, 230, 0.667) 6px) → preset rayas-crema | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-G07 | `.tc-glass` | background-image: linear-gradient(160deg, rgba(42, 42, 45, 0.77), rgba(20, 20, 22, 0.84)) → preset vidrio-relleno-tecnica | tc-grabar, tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal |  | [ ] |
| tc-G08 | `.tc-pb i` | background-image: linear-gradient(90deg,var(--col),var(--ene)) → preset relleno-horizontal-cardio | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |
| tc-G09 | `.tc-sk` | background-image: linear-gradient(100deg, rgba(255, 255, 255, 0.05) 30%, rgba(255, 255, 255, 0.11) 50%, rgba(255, 255, 255, 0.05) 70%) → preset brillo-esqueleto-5 | tc-cargando |  | [ ] |
| tc-G10 | `.tc-sweep` | background-image: linear-gradient(transparent, rgba(242, 238, 230, 0.2) 70%, rgba(242, 238, 230, 0.5)) → preset velo-crema-largo | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |
| tc-G11 | `.tc-toast` | background-image: linear-gradient(160deg, rgba(58, 36, 35, 0.847), rgba(28, 18, 18, 0.88)) → preset aviso-relleno-error | tc-aviso |  | [ ] |
| tc-G12 | `.tc-vfi .scan` | background-image: linear-gradient(transparent, rgb(242, 238, 230), transparent) → preset franja-crema | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |

## Animaciones (@keyframes) (9)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| tc-A01 | `.tc-an.queued .tc-sig path.live` | animation: tcq · 2.4s · ease-in-out · retardo 0s · repite infinite · normal · none → preset pulso · curva vaiven · dur 2400 | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |
| tc-A02 | `.tc-phs div.act i` | animation: tcpulse · 1.2s · ease-in-out · retardo 0s · repite infinite · normal · none → preset pulso-anillo · curva vaiven · dur 1200 | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |
| tc-A03 | `.tc-root.tc-enter .tc-hud > *` | animation: tcpop · 0.5s · ease · retardo 0s · repite 1 · normal · both → preset entrada-sube · curva suave · dur 520 | tc-perfil-importar, tc-perfil-importar-vacio, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito |  | [ ] |
| tc-A04 | `.tc-root.tc-enter .tc-isl` | animation: tcfade · 0.5s · ease · retardo 0.1s · repite 1 · normal · both → preset entrada-baja · curva suave · dur 520 | tc-perfil-importar, tc-perfil-importar-vacio, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito |  | [ ] |
| tc-A05 | `.tc-root.tc-enter .tc-sheet` | animation: tcsheet · 0.7s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0.25s · repite 1 · normal · both → preset hoja-sube · curva estandar · dur 700 | tc-perfil-importar, tc-perfil-importar-vacio, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito |  | [ ] |
| tc-A06 | `.tc-root.tc-enter .tc-vid` | animation: tcvid · 1.1s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · both → preset revelado · curva estandar · dur 1000 | tc-perfil-importar, tc-perfil-importar-vacio, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito |  | [ ] |
| tc-A07 | `.tc-sk` | animation: tcsk · 1.4s · linear · retardo 0s · repite infinite · normal · none → preset brillo-barrido · curva lineal · dur 1400 | tc-cargando |  | [ ] |
| tc-A08 | `.tc-sweep` | animation: tcsweep · 2.6s · linear · retardo 0s · repite infinite · normal · none → preset barrido-linea · curva lineal · dur 2400 | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |
| tc-A09 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-resultado-vivo, tc-resultado-perfil, tc-repeticion |  | [ ] |

## Transiciones (20)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| tc-T01 | `.tc-an` | transition: opacity, transform · 0.5s, 0.5s · ease, cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva suave/estandar · dur 520 | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |
| tc-T02 | `.tc-band` | transition: opacity · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T03 | `.tc-bar i` | transition: height, background · 0.3s, 0.2s · cubic-bezier(0.2, 0.8, 0.2, 1), ease · retardo 0s, 0s → preset curva estandar/suave · dur 280/200 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T04 | `.tc-circ` | transition: box-shadow · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | tc-marcar, tc-marcar-otro |  | [ ] |
| tc-T05 | `.tc-grab i` | transition: background, width · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T06 | `.tc-hdr` | transition: max-height, opacity, margin · 0.3s, 0.25s, 0.3s · ease, ease, ease · retardo 0s, 0s, 0s → preset curva suave · dur 280/240 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T07 | `.tc-hud` | transition: height · 0.34s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 320 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T08 | `.tc-loupe` | transition: opacity · 0.15s · ease · retardo 0s → preset curva suave · dur 160 | tc-marcar, tc-marcar-otro |  | [ ] |
| tc-T09 | `.tc-modes button` | transition: background, color · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T10 | `.tc-modetip` | transition: opacity, transform · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T11 | `.tc-obs` | transition: opacity, transform · 0.25s, 0.25s · ease, ease · retardo 0s, 0s → preset curva suave · dur 240 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T12 | `.tc-qcard` | transition: opacity, transform · 0.2s, 0.26s · ease, cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s, 0s → preset curva suave/estandar · dur 200/240 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T13 | `.tc-repchip, .tc-stat, .tc-q` | transition: opacity, transform · 0.2s, 0.2s · ease, ease · retardo 0s, 0s → preset curva suave · dur 200 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T14 | `.tc-seg3 button` | transition: background, color · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T15 | `.tc-sheet` | transition: transform · 0.5s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 520 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T16 | `.tc-speed button` | transition: background, color · 0.16s, 0.16s · ease, ease · retardo 0s, 0s → preset curva suave · dur 160 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T17 | `.tc-strip` | transition: max-height, opacity, margin · 0.3s, 0.25s, 0.3s · ease, ease, ease · retardo 0s, 0s, 0s → preset curva suave · dur 280/240 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T18 | `.tc-vid` | transition: transform, filter · 0.34s, 0.5s · cubic-bezier(0.2, 0.8, 0.2, 1), ease · retardo 0s, 0s → preset curva estandar/suave · dur 320/520 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T19 | `.tc-vtag` | transition: opacity · 0.2s · ease · retardo 0s → preset curva suave · dur 200 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-T20 | `.tc-x` | transition: height · 0.34s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s → preset curva estandar · dur 320 | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |

## Transformaciones, máscaras y mezcla (13)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| tc-X01 | `.tc-an.out` | transform: scale(1.04) | tc-analizando, tc-exito, tc-cola, tc-actualizando |  | [ ] |
| tc-X02 | `.tc-bar b` | transform: translate(-50%, 0px) | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-X03 | `.tc-bar.sel:first-child b` | transform: translate(-18%, 0px) | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-X04 | `.tc-bar.sel:last-child b` | transform: translate(-82%, 0px) | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-X05 | `.tc-cam .tc-svg` | transform: translateX(-50%) | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito, tc-cola |  | [ ] |
| tc-X06 | `.tc-dlg` | transform: translateY(-62%) | tc-marcar-otro |  | [ ] |
| tc-X07 | `.tc-mkf .tc-svg` | transform: translate(-50%, -50%) | tc-perfil-importar, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito, tc-cola |  | [ ] |
| tc-X08 | `.tc-modetip` | transform: translateY(-4px) | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-X09 | `.tc-obs` | transform: translateY(8px) | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-X10 | `.tc-qcard` | transform: translateY(-8px) scale(0.98) | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-X11 | `.tc-root.tc-m-tray .tc-repchip, .tc-root.tc-m-tray .tc-stat, .tc-root.tc-m-tray .tc-vtag, .tc-root.tc-m-tray .tc-obs, .tc-root.tc-m-limpio .tc-repchip, .tc-root.tc-m-limpio .tc-stat, .tc-root.tc-m-limpio .tc-vtag, .tc-root.tc-m-limpio .tc-q, .tc-root.tc-m-limpio .tc-obs` | transform: scale(0.96) | tc-perfil-importar, tc-perfil-importar-vacio, tc-perfil-guardando, tc-grabar, tc-analizando, tc-exito |  | [ ] |
| tc-X12 | `.tc-vid` | transform: translate(var(--vx),var(--vy)) scale(var(--vs)) | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |
| tc-X13 | `.tc-vtag` | transform: translate(-50%, -50%) | tc-resultado-vivo, tc-resultado-perfil, tc-repeticion, tc-detalle, tc-resultado-ideal, tc-solo-trayectoria |  | [ ] |

## Movimiento y estilos hechos en JavaScript (20)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| tc-J01 | `scenes-tc.js:285` | estilo en línea desde JS · tag.style.opacity=(inRep>=0&&S.mode==='datos'&&tcRel(S.t)>.05)?1:0} | — |  | [ ] |
| tc-J02 | `scenes-tc.js:299` | setTimeout · paint();raf=setTimeout(()=>tick(performance.now()),16)} | — |  | [ ] |
| tc-J03 | `scenes-tc.js:308` | setTimeout · if(tip){const t=q('[data-modetip]');t.textContent={datos:'Con datos',tray:'Solo trayectoria',limpio:'Limpio'}[m];t.classList.add('show');clearTimeout( | — |  | [ ] |
| tc-J04 | `scenes-tc.js:352` | setTimeout · if(c.enter){setTimeout(()=>root.classList.remove('tc-enter'),2600)} | — |  | [ ] |
| tc-J05 | `scenes-tc.js:353` | setTimeout · if(!frozen)raf=setTimeout(()=>tick(performance.now()),16); | — |  | [ ] |
| tc-J06 | `scenes-tc.js:393` | estilo en línea desde JS · tcPaint(R,t);R.segs.forEach(sg=>{const r=+sg.dataset.r;sg.style.opacity=(queued&&pct===0)?0:(+sg.dataset.t<=t\|\|upd)?.92:0}); | — |  | [ ] |
| tc-J07 | `scenes-tc.js:409` | setTimeout · if(pct>=100){setTimeout(finish,650);return}setTimeout(()=>step(performance.now()),16)} | — |  | [ ] |
| tc-J08 | `scenes-tc.js:412` | setTimeout · old.insertAdjacentHTML('afterend',tcResultHtml(cfg));const nr=old.nextElementSibling;tcBind(phone,cfg,nr);setTimeout(()=>old.remove(),900)} | — |  | [ ] |
| tc-J09 | `scenes-tc.js:413` | setTimeout · setTimeout(()=>step(performance.now()),16); | — |  | [ ] |
| tc-J10 | `scenes-tc.js:421` | estilo en línea desde JS · function tcStillBind(phone){const root=phone.querySelector('.tc-root');const R=tcRefs(root);tcPaint(R,3.3);R.segs.forEach(s=>s.style.opacity=0); | — |  | [ ] |
| tc-J11 | `scenes-tc.js:439` | estilo en línea desde JS · const svg=f.querySelector('.tc-svg'),R=tcRefs(svg);tcPaint(R,1.0);R.segs.forEach(s=>s.style.opacity=0);svg.querySelectorAll('.tc-skel,.tc-dotg').forEa | — |  | [ ] |
| tc-J12 | `scenes-tc.js:445` | declaración CSS dentro del marcado · const loupe=on=>{lp.classList.toggle('show',on);if(!on)return;if(!lp.firstChild){const cl=svg.cloneNode(true);cl.style.cssText='position:absolute;left | — |  | [ ] |
| tc-J13 | `scenes-tc.js:459` | estilo en línea desde JS · root.querySelector('[data-err]').textContent=bad?'Entre 10 y 70 cm':'';root.querySelector('[data-fld]').classList.toggle('err',bad);root.querySelector | — |  | [ ] |
| tc-J14 | `scenes-tc.js:475` | estilo en línea desde JS · const root=phone.querySelector('.tc-root'),R=tcRefs(root);tcPaint(R,3.3);R.segs.forEach(s=>s.style.opacity=0);root.querySelectorAll('.tc-skel,.tc-dotg | — |  | [ ] |
| tc-J15 | `scenes-tc.js:504` | estilo en línea desde JS · const root=phone.querySelector('.tc-root'),R=tcRefs(root);tcPaint(R,1.0);R.segs.forEach(s=>s.style.opacity=0);root.querySelectorAll('.tc-skel,.tc-dotg | — |  | [ ] |
| tc-J16 | `scenes-tc.js:506` | estilo en línea desde JS · tip.style.transition='opacity .25s,transform .3s'; | — |  | [ ] |
| tc-J17 | `scenes-tc.js:507` | estilo en línea desde JS · const set=o=>{tip.style.opacity=o?1:0;tip.style.pointerEvents=o?'auto':'none';tip.style.transform=o?'none':'translateY(14px)';chip.style.display=o?'no | — |  | [ ] |
| tc-J18 | `scenes-tc.js:526` | estilo en línea desde JS · const root=phone.querySelector('.tc-root'),R=tcRefs(root);tcPaint(R,3.3);R.segs.forEach(s=>s.style.opacity=0);root.querySelectorAll('.tc-skel,.tc-dotg | — |  | [ ] |
| tc-J19 | `scenes-tc.js:74` | declaración CSS dentro del marcado · s+='<g class="tc-trail" style="filter:drop-shadow(0 0 3px #000c)">'; | — |  | [ ] |
| tc-J20 | `scenes-tc.js:98` | estilo en línea desde JS · function tcPaintTrail(R,sel,t){R.segs.forEach(sg=>{const r=+sg.dataset.r;let o=.15;if(r===sel)o=(+sg.dataset.t)<=t?1:.22;sg.style.opacity=o})} | — |  | [ ] |

## @keyframes usados (9)

```css
@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes tcpulse { 0%, 100% { box-shadow: rgba(242, 238, 230, 0.333) 0px 0px 0px 0px; } 50% { box-shadow: rgba(242, 238, 230, 0) 0px 0px 0px 5px; } }
```

```css
@keyframes tcq { 0%, 100% { opacity: 0.35; } 50% { opacity: 1; } }
```

```css
@keyframes tcvid { 0% { filter: brightness(0.5) blur(6px); transform: translate(var(--vx),var(--vy)) scale(calc(var(--vs)*1.06)); } 100% { filter: none; } }
```

```css
@keyframes tcsheet { 0% { transform: translateY(105%); } 100% { transform: none; } }
```

```css
@keyframes tcfade { 0% { opacity: 0; transform: translateY(-8px); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes tcpop { 0% { opacity: 0; transform: translateY(8px) scale(0.96); } 100% { opacity: 1; transform: none; } }
```

```css
@keyframes tcsweep { 0% { transform: translateY(0px); } 100% { transform: translateY(calc(var(--vfh,640px) + 46px)); } }
```

```css
@keyframes tcsk { 0% { background-position: 120% 0px; } 100% { background-position: -120% 0px; } }
```
