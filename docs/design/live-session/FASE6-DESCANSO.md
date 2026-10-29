# Fase 6 · Descanso y feedback

Objetivo: que el descanso sea un lugar con información real —tiempo, siguiente serie, recuperación y juego— y que el feedback no sea un formulario genérico. Aquí se concentra la honestidad de datos de la sesión.

Fuentes: `RestLiveCard.kt`, `RestControls.kt`, `RestExperience.kt`, `RestRingsClock.kt`, `RestNextSet.kt`, `RestClockMath.kt`, `RestClockSelection.kt`, `RestOverrun.kt`, `RestOverrunLayer.kt`, `RestMiniChip.kt`, `RestMiniGame.kt`, `RestArcade.kt`, `RestGameEngine.kt`, `WorkoutRestRecoveryModel.kt`, `FeedbackTechnique.kt`, `FeedbackDiscomforts.kt`, `FeedbackInsights.kt`, `PostExerciseFeedbackDraft.kt`.

## 1. Tarjeta de descanso

| Propiedad | Valor |
|---|---|
| Tamaño | 345 × 470 dp |
| Superficie | `#111111`, radio 28, filete blanco 7 % |
| Relleno | 14 dp laterales, 10 dp verticales; separación 8 |
| Medio | Scroll interno con fundido de 20 dp; reserva 48 dp para vídeo |
| Tamaño | Nunca cambia; el medio se recorta, no empuja |

### 1.1 Cabecera

| Etiqueta | Cuándo |
|---|---|
| `DESCANSO` | entre series normal |
| `SUPERSERIE` | descanso intra-superserie |
| `SIGUIENTE RONDA` | entre rondas |
| `APROXIMACIÓN` | tras una aproximación |
| `ENTRE LADOS` | cambio de lado |
| `TIEMPO EXTRA` | el reloj está en negativo |

- Si las notificaciones están apagadas: «Sin notificaciones el aviso es solo sonoro.»
- Chevron para expandir el descanso.

### 1.2 Reloj

- Lienzo de 120 × 104 dp, `runnerScale = 1,4`, `strokeBoost = 1,15`.
- Pista `#242424`; tinta `#F2EEE6`; cabeza `#0B0B0B`.
- Halos: columna `#8FB2FF`, músculo `#F49A6E`, energía `#F7CF73`; degradados `[#5E7EC4→#B9CFFF]`, `[#C7704A→#FFC2A3]`, `[#C9A04A→#FFE7A8]`.
- Tiempo máximo 40 sp; prefijo `+` si es tiempo extra.
- Debajo: `de T` + `· manual` o `· sugerido`; si hay plan: `Plan de sesión · T`.
- Voz: «Descanso: quedan 1 minuto y 12 segundos»; en extra, «Tiempo extra, 34 segundos».

### 1.3 Formas del reloj

| Forma | Dibujo | Vuelta |
|---|---|---|
| **Torre** | Tres anillos; el relleno reparte 1/3 por anillo; el activo es el primero incompleto | 4,5 s |
| **Pista** | Recta de 140 + dos curvas de radio 70 | 4,5 s |
| **Espiral** | Seis semivueltas con radio creciente | 9 s |

- La forma se elige por evento: estable por `startedAt` y sobrevive a ±15 s; si no hay elección, rota por ordinal.
- El corredor dibuja cabeza 3,6, cuerpo 1,8 y piernas con oscilación de 26° a 0,34 s.
- Con movimiento reducido: sin vuelta, solo relleno.

### 1.4 Medio

| Elemento | Valor |
|---|---|
| Última serie | `ÚLTIMA SERIE` + `kg × reps` tabular; caja `inset`, padding 12/9 |
| Badges | `Récord` en energía; `Fallo` en peligro; píldora borde blanco 12 %, padding 8/3 |
| Descanso sugerido | `Descanso sugerido` + valor Syne; píldora `inset`, mínimo 48, icono `AutoAwesome` 14 en músculo; solo si hay sugerencia adaptativa pendiente |
| Saltar series | `Saltar series restantes e ir al siguiente ejercicio`; borde tinta 24 %, mínimo 48, máximo 2 líneas |
| Siguiente | `Siguiente [· ejercicio]` + `Serie N · dosis` + `RIR/RPE`; caja `inset`, radio 18, padding 14/10 |
| Vídeo | Hueco de 48 dp para la línea de vídeo si existe |

### 1.5 Controles

- Tres hermanas: `−15 s` (peso 1), `Saltar` (1,6), `+15 s` (1).
- Visual 44 dp en zona de 48; `Saltar` en tinta con texto negro e icono 16; autoajuste de 14 a 11 sp.
- Las mismas acciones existen en la vista expandida: `DECREASE_15`, `INCREASE_15`, `SKIP`, `ADAPTIVE`, `SKIP_EXERCISE`.

## 2. De dónde sale el descanso

| Tipo | Fórmula |
|---|---|
| Entre lados | `restBetweenSides` o 0 |
| Intra-superserie | `roundRestBetween` / `restBetween` / `supersetRestBetween` / 0 |
| Entre rondas | `roundRestAfter` / `restAfter` / `supersetRestAfter` / base |
| Aproximación | `restBetween` o base |
| Normal | `scheduled` o, si no, `base` (90 s) |

- `planned` viene de `postExerciseTarget.restTime` o 90 s.
- `adaptive` ajusta por ritmo; si está activo y no es manual: `· sugerido`.
- Si una técnica sigue abierta (drop/rest-pause sin registrar), el descanso efectivo es 0 y no arranca.
- `totalSeconds = (ends − start + 500) / 1000`, mínimo 1; incluye los ±15 s.
- `progress = (total − remaining) / total`, acotado 0–1.

### 2.1 Recuperación

`difficulty` 1–5: fallo = 5; RPE ≥9,5 → 5; ≥8,8 → 4; ≥7,8 → 3; ≥6,8 → 2; resto 1; +1 por drop/rest-pause/parciales. Sin dato, RPE 7,5.

`demand = 18 − (5 − tier) × 3`; `recovery = (21 − demand × e^(−0,7 × min^0,8)) / 21`, acotado 0–1,05; listo si ≥0,75.

## 3. Tiempo extra

| Propiedad | Valor |
|---|---|
| Nace | Solo si el descanso terminó de forma natural, no era aproximación y la serie es normal |
| Duración | 5 min; se puede extender 30 s |
| Copy | `Descanso cumplido. Serie 2 de 4: …` / `TIEMPO EXTRA` / `+M:SS` / `sobre T` |
| Acciones | `Empezar la serie N` + `Necesito +30 s` |
| Vista | Velo `canvas` 92 % + haze; anillo 250 dp que respira 1,8 s; o tarjeta con el mismo contenido |
| Cierre | 5 min, nuevo descanso, cambio de cursor, hoja abierta, sesión completada o cancelada |

## 4. Mini chip

- Píldora de 40 dp visual en 48; fondo `module`, borde blanco 12 %, padding izquierdo 4, separación 10.
- Aro de 32 dp, trazo 4, pista `#2A2A2A`, progreso músculo desde −90°.
- `Descanso` 11/14 + tiempo `metricM`; `+15 s` en caja de 56 × 40, pastilla 28, `inset`.

## 5. Juego

- Vive dentro de la superficie fija del descanso; no lo toca.
- Se cierra al llegar a 0 o con `Saltar`.
- `RestGameArena`: radio 28, borde blanco 8 %; toque = saltar; 60 fps; récord persistente; movimiento reducido sin bucle.
- HUD: `Saltos`, `récord B · ♥×L`, `Salta potente` / `Velocidad`, `Toca para saltar`, `Fin de partida`.
- Física: salto 0,77 s / 70 dp; gravedad 940; impulso 362; velocidad 150→260 (+3,5/s); paso máximo 0,05 s; 3 vidas (máx. 5), invulnerabilidad 1,3 s, destello 0,25 s; power-ups 7 s (salto ×1,25, velocidad ×1,3, vida +1).
- Arcade: `RUNNER` («Salta las mancuernas»), `FLAPPY` («Entre las pesas»), `SHOOTER` («Escuadrón retro»); teselas de mínimo 110/128 dp; reloj del descanso siempre visible.

## 6. Feedback post-ejercicio

### 6.1 Estructura

- Título `¿Cómo te salió?` 22/26.
- Tarjeta(s) de técnica + molestias + acciones.
- En la tarjeta de descanso: variante compacta con franja de tiempo, medio con scroll y botones fijos.

### 6.2 Calidad técnica

- Escala 1–10, por defecto 8.
- Palabras: `muy irregular` ×2, `irregular` ×2, `aceptable` ×2, `buena`, `limpia`, `muy limpia`, `perfecta`.
- Color: rojo `#EF6F64` → ámbar `#F7CF73` → verde `#43D18C`.
- Slider de 6 dp (5 compacto), relleno degradado `#F49A6E → #8FB2FF`, pulgar 24 (22) con halo.
- Voz: «Calidad técnica, N de 10, palabra».

### 6.3 Molestias

- Buscador: píldora 42 dp (38), fondo `#0B0B0B`, borde blanco 10 %, placeholder `Rodilla, lumbar, hombro…`.
- Chips: `FlowRow` con 8 dp (fila con scroll de 6 en compacto); chip 34 (30) en 48, punto de 22/8 (18/6).
- Elegido: tinta con texto negro; reportado antes: borde músculo 45 % + icono historial.
- Vacío: `No se encontraron resultados para “Q”`.
- Ficha: título de conocimiento, zona, explicación, causas comunes, registros anteriores, qué puedes hacer, animación.
- Relacionadas: `Molestias previas relacionadas` + `Reportaste… Comparten…` + `Sigue` / `Resuelta`.
- Aviso motor solo si hay evidencia.
- Acciones: `Saltar descanso` / `Saltar` (inset) y `Guardar` (tinta); `Guardar` guarda técnica + molestias (`none` si vacío) y conserva RPE/fallo/notas.

## 7. Criterio de aceptación

- [ ] El descanso muestra el tiempo real y el siguiente set real.
- [ ] El origen del descanso (manual/sugerido) se distingue.
- [ ] Las tres formas del reloj son estables por evento y sobreviven a ±15 s.
- [ ] El tiempo extra caduca a los 5 min y se puede extender 30 s.
- [ ] El mini chip no tapa la acción principal.
- [ ] El juego no altera el descanso ni el registro.
- [ ] El feedback no obliga a puntuar: se puede saltar.
- [ ] Sin datos, la recuperación y las molestias muestran «—» o vacío, nunca un valor inventado.
