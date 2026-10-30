# Paridad de profundidad y movimiento · `ct` (2026-10-30)

Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.
Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.
Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.

Resumen: sombras 14 · vidrio, 4 · degradados 8 · animaciones 8 · transiciones 1 · transformaciones, 0 · movimiento 1 · escenas del paquete: 22

## Sombras (14)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ct-S01 | `.ct-back` | box-shadow: rgba(255, 255, 255, 0.165) 0px 0px 0px 1px inset → preset interior-blanco-1-tenue | ct-ficha-plantilla, ct-aplicar-copia, ct-aplicar-sustituir, ct-confirmar, ct-aplicando, ct-vacio |  | [ ] |
| ct-S02 | `.ct-cv` | box-shadow: rgba(255, 255, 255, 0.09) 0px 0px 0px 1px inset → preset borde-interior | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-S03 | `.ct-dlg` | box-shadow: rgba(0, 0, 0, 0.933) 0px 24px 70px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset dialogo-1 | ct-confirmar |  | [ ] |
| ct-S04 | `.ct-fc.open` | box-shadow: rgba(242, 238, 230, 0.533) 0px 0px 0px 1.5px inset → preset interior-crema-1p5-1 | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-S05 | `.ct-grid i.dl` | box-shadow: inset 0 0 0 1.5px var(--c) → preset interior-zona-1p5 | ct-ficha-plantilla, ct-aplicar-copia, ct-aplicar-sustituir, ct-confirmar, ct-aplicando, ct-vacio |  | [ ] |
| ct-S06 | `.ct-grid i.em` | box-shadow: rgba(255, 255, 255, 0.18) 0px 0px 0px 1px inset → preset interior-blanco-1-tenue | ct-ficha-plantilla, ct-aplicar-copia, ct-aplicar-sustituir, ct-confirmar, ct-aplicando, ct-vacio |  | [ ] |
| ct-S07 | `.ct-hc` | box-shadow: rgba(255, 255, 255, 0.08) 0px 0px 0px 1px inset → preset borde-interior | ct-portada, ct-lista |  | [ ] |
| ct-S08 | `.ct-sheet` | box-shadow: rgba(0, 0, 0, 0.8) 0px -14px 44px, rgba(255, 255, 255, 0.07) 0px 1px inset → preset hoja | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-S09 | `.ct-strip i.dl` | box-shadow: inset 0 0 0 1.5px var(--c) → preset interior-zona-1p5 | ct-ficha-plantilla, ct-aplicar-copia, ct-aplicar-sustituir, ct-confirmar, ct-aplicando, ct-vacio |  | [ ] |
| ct-S10 | `.ct-strip i.em` | box-shadow: rgba(255, 255, 255, 0.25) 0px 0px 0px 1px inset → preset borde-interior-fuerte | ct-ficha-plantilla, ct-aplicar-copia, ct-aplicar-sustituir, ct-confirmar, ct-aplicando, ct-vacio |  | [ ] |
| ct-S11 | `.glass` (base) | box-shadow: rgba(0, 0, 0, 0.6) 0px 10px 30px, rgba(255, 255, 255, 0.07) 0px 1px inset → preset isla-vidrio | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-S12 | `.pl-glass` (base) | box-shadow: rgba(0, 0, 0, 0.667) 0px 8px 28px, rgba(255, 255, 255, 0.08) 0px 1px inset → preset isla-vidrio | ct-hecho-sustituir, ct-hecho-copia |  | [ ] |
| ct-S13 | `.tok.on` (base) | box-shadow: rgb(244, 154, 110) 0px -2px inset → preset subrayado-musculo | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-S14 | `.topbar .glass` (base) | box-shadow: rgba(0, 0, 0, 0.467) 0px 8px 24px, rgba(255, 255, 255, 0.11) 0px 1px inset → preset barra-vidrio | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |

## Vidrio, desenfoques y filtros (4)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ct-V01 | `.ct-back` | backdrop-filter: blur(12px) → preset velo-desenfoque | ct-ficha-plantilla, ct-aplicar-copia, ct-aplicar-sustituir, ct-confirmar, ct-aplicando, ct-vacio |  | [ ] |
| ct-V02 | `.glass` (base) | backdrop-filter: blur(26px) saturate(1.3) → preset vidrio-hoja | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-V03 | `.pl-glass` (base) | backdrop-filter: blur(22px) saturate(1.5) → preset vidrio-isla | ct-hecho-sustituir, ct-hecho-copia |  | [ ] |
| ct-V04 | `.topbar .glass` (base) | backdrop-filter: blur(18px) saturate(1.8) brightness(0.9) → preset vidrio-barra | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |

## Degradados y resplandores (8)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ct-G01 | `.ct-foot` | background-image: linear-gradient(rgba(27, 27, 29, 0), rgb(27, 27, 29) 28%) → preset velo-hoja | ct-ficha-plantilla, ct-aplicar-copia, ct-aplicar-sustituir, ct-confirmar, ct-aplicando, ct-vacio |  | [ ] |
| ct-G02 | `.ct-hc::after` | background-image: linear-gradient(rgba(0, 0, 0, 0), rgba(0, 0, 0, 0.85) 72%) → preset velo-negro-4 | ct-portada, ct-lista |  | [ ] |
| ct-G03 | `.ct-hero::after` | background-image: linear-gradient(rgba(27, 27, 29, 0), rgba(27, 27, 29, 0.8) 42%, rgb(27, 27, 29) 82%) → preset velo-hoja-largo | ct-ficha-plantilla, ct-aplicar-copia, ct-aplicar-sustituir, ct-confirmar, ct-aplicando, ct-vacio |  | [ ] |
| ct-G04 | `.ct-vtop` | background-image: linear-gradient(rgb(27, 27, 29) 62%, rgba(27, 27, 29, 0)) → preset velo-hoja-sale | ct-ficha-plantilla, ct-aplicar-copia, ct-aplicar-sustituir, ct-confirmar, ct-aplicando, ct-vacio |  | [ ] |
| ct-G05 | `.glass` (base) | background-image: linear-gradient(140deg, rgba(44, 44, 44, 0.72), rgba(22, 22, 22, 0.69)) → preset vidrio-relleno-hoja | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-G06 | `.split7 i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgb(207, 201, 191)) → preset relleno-crema | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-G07 | `.story .st-week i.on` (base) | background-image: linear-gradient(rgb(242, 238, 230), rgba(242, 238, 230, 0.8)) → preset relleno-crema-suave | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-G08 | `.topbar .glass` (base) | background-image: linear-gradient(150deg, rgba(58, 58, 58, 0.4), rgba(20, 20, 20, 0.35)) → preset vidrio-relleno-barra | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |

## Animaciones (@keyframes) (8)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ct-A01 | `.ct-sheet.ct-nav > .ct-fscroll, .ct-sheet.ct-nav > .ct-body` | animation: ct-in · 0.24s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-derecha · curva estandar · dur 240 | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-A02 | `.ct-sheet.ct-rise` | animation: ct-rise · 0.32s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 320 | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-A03 | `.ct-spin` | animation: ct-sp · 0.8s · linear · retardo 0s · repite infinite · normal · none → preset giro · curva lineal · dur 800 | ct-aplicando |  | [ ] |
| ct-A04 | `.ct-toast.in` | animation: ct-rise · 0.32s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · none → preset entrada-sube · curva estandar · dur 320 | ct-hecho-sustituir, ct-hecho-copia |  | [ ] |
| ct-A05 | `.draw.go .ln` (base) | animation: drawln · 1.6s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1600 | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-A06 | `.go .burst .ln, .burst.go .ln` (base) | animation: drawln · 1.1s · cubic-bezier(0.2, 0.8, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva estandar · dur 1000 | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-A07 | `.plate .ln` (base) | animation: drawln · 1.4s · cubic-bezier(0.3, 0.7, 0.2, 1) · retardo 0s · repite 1 · normal · forwards → preset dibujo-trazo · curva trazo · dur 1400 | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |
| ct-A08 | `.wr-bars i.on::after` (base) | animation: wrbar · 6s · linear · retardo 0s · repite 1 · normal · forwards → preset crece-x · curva lineal · dur 6000 | ct-portada, ct-lista, ct-plantillas, ct-metodos, ct-filtro, ct-busqueda |  | [ ] |

## Transiciones (1)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ct-T01 | `.ct-vtop` | transition: opacity · 0.18s · ease · retardo 0s → preset curva suave · dur 160 | ct-ficha-plantilla, ct-aplicar-copia, ct-aplicar-sustituir, ct-confirmar, ct-aplicando, ct-vacio |  | [ ] |

## Movimiento y estilos hechos en JavaScript (1)

| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |
|---|---|---|---|---|---|
| ct-J01 | `scenes-ct.js:528` | setTimeout · const start=()=>{st.phase='applying';full();clearTimeout(timer);timer=setTimeout(()=>{st.phase='done';full('toast',false)},900)}; | — |  | [ ] |

## @keyframes usados (5)

```css
@keyframes drawln { 100% { stroke-dashoffset: 0; } }
```

```css
@keyframes wrbar { 0% { transform: scaleX(0); } 100% { transform: scaleX(1); } }
```

```css
@keyframes ct-rise { 0% { transform: translateY(40px); opacity: 0.4; } }
```

```css
@keyframes ct-in { 0% { transform: translateX(18px); opacity: 0; } }
```

```css
@keyframes ct-sp { 100% { transform: rotate(360deg); } }
```
