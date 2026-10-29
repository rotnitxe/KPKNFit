# Fase 5 · Preparación

Objetivo: que movilidad, aproximación y cardio se vean como parte de la misma sesión, no como pantallas pegadas. Comparten el marco de 470 dp y el mismo lenguaje de dosis, check y descanso.

Fuentes: `WarmupMobilityLiveCards.kt`, `PrepPhaseLiveLogic.kt`, `domain/training/approach/ApproachRamp.kt`, `ApproachPlanner.kt`, `WarmupRules.kt`, `CardioLiveCard.kt`, `CardioIntervalChart.kt`.

## 1. Marco común

| Propiedad | Valor |
|---|---|
| Tamaño | 345 × 470 dp (misma proporción 312/425) |
| Superficie | `#111111`, radio 28, filete blanco 7 % |
| Relleno | 14 dp laterales, 12 dp verticales; separación 6 |
| Táctil | 48 dp |
| Título | 20/26 |
| Meta | 11/16 |
| Micro | 10/14 |
| Botón | píldora de mínimo 48 dp, icono 16, separación 6; primario tinta, tonal `inset`, quieto transparente |
| Check | zona 48, cuadro 22, radio 6, anillo 1,75, check 15 |
| Reloj | `m:ss` |

La preparación **no** usa el héroe de carga ni el esfuerzo: su cifra protagonista es el tiempo o la dosis.

## 2. Movilidad

### 2.1 Cabecera

`Movilidad` (máx. 1 línea) + chip de reloj global + botón de lista + botón de estado. Descripciones: «Abrir reloj global», «Ver lista», «Cerrar lista».

### 2.2 Vista Movimiento

| Elemento | Valor |
|---|---|
| Imagen | 40 dp, radio 10, `inset`, solo si hay imagen revisada |
| Título | 13/19, `Movimiento N de T [· ejercicio]` 10/15 |
| Dosis | 34/40, `m:ss` si es por tiempo; `— reps` + `A tu ritmo` si es por reps |
| Controles | `Iniciar` / `Pausar`; `+30 s`; `Reiniciar` |
| Nota | `caption` en `textMuted`; la dosis resumida en `#F7CF73` |
| Reloj | propio por movimiento; al llegar a 0 se detiene y avisa «Tiempo cumplido. Marca el movimiento como hecho.» |
| Avance | **no automático**; `Hecho` o `Continuar` |
| Copy | `Movimiento N hecho.` / `Movimiento pendiente` |

### 2.3 Vista Lista

- `N/M movimientos hechos` 12/18 `textMuted`.
- Filas de mínimo 68 dp con separador; nombre 12/17; dosis en `#F7CF73` + `· en pantalla` si es el actual.
- Zona del nombre: mínimo 64 dp, radio 10, padding 6; descripción «Seleccionar …».
- Check marca hecho/pendiente con aviso.

### 2.4 Vista Global

- `Objetivo global · M min` 12/18.
- Tiempo 48/54.
- `Iniciar` / `Pausar` a ancho completo.
- `+30 s` / `Reiniciar`.
- `Volver al movimiento` (quieto).

### 2.5 Fin y descanso

| Estado | Copy |
|---|---|
| Completada | `Movilidad completada` + `Puedes continuar con las aproximaciones.` / `Puedes continuar con tus series.` + `N de M movimientos hechos.` |
| Omitida | `Fase omitida` |
| Continuar | `Continuar con aproximación` si la siguiente fase es aproximación; si no, `Continuar con series` |
| Sin terminar | `Saltar fase` + `Hecho` |
| Descanso | `Descanso` + tiempo 22/28 + `Saltar` + barra de 5 dp radio 999, relleno acento 85 % |

## 3. Aproximación

### 3.1 Cabecera y ancla

- `Aproximación` + `N/M` tabular + `+` («Añadir aproximación», oculto si la fase está omitida).
- `Primera serie efectiva · X kg` si hay carga de trabajo.

### 3.2 Escalera real

`ApproachRamp` selecciona el plan según el ejercicio:

| Nivel | Larga | Corta |
|---|---|---|
| Novato | 40 % × 8 (30 s) · 60 % × 5 (45 s) · 80 % × 3 (60 s) | 50 % × 6 (30 s) · 75 % × 3 (45 s) |
| Intermedio | 40 % × 8 · 60 % × 5 · 80 % × 3 | 50 % × 5 · 75 % × 3 |
| Avanzado | 40 % × 6 · 60 % × 4 · 80 % × 2 | 70 % × 3 (45 s) |
| Muy pesado | + 90 % × 1 (60 s) en avanzado | + 90 % × 1 en avanzado |

- Se usa larga en el primer ejercicio pesado/moderado; corta en el segundo/tercero solo si es pesado y articulación nueva; los ligeros no llevan aproximación.
- Máximo 3 posiciones.

### 3.3 Fila

| Elemento | Valor |
|---|---|
| Alto | mínimo 60 dp + separador |
| Título | `A1 · 50 %`; el porcentaje se normaliza (`0,5` → `50 %`) |
| Caja de kg | `inset`, radio 12, padding 8; campo 60 dp × escala; 23/28; sufijo `kg` |
| Reps | `× N` 19/24 |
| Descanso | `m:ss`, por defecto 60 s |
| Check | marca hecha/pendiente; aviso `Aproximación N hecha · X kg × R` |
| Coma | se acepta y se normaliza |

### 3.4 Reglas de honestidad

- Al añadir: máximo recomendado `min(2 × series efectivas, 4)`. Si se supera: «Demasiadas aproximaciones: máximo recomendado M para E series efectivas.»
- Si la última es ≥85 %: «Una aproximación al 85 % o más puede comportarse como una serie efectiva; revisa su carga y repeticiones.»
- Omitida: `Fase omitida` + `Las series efectivas siguen pendientes.`; el botón pasa a `Saltar fase`.
- Al terminar todas: `Continuar con series`.

## 4. Cardio

| Elemento | Valor |
|---|---|
| Marco | `KpknModule` padding 0; columna con scroll, padding 20/18, separación 12 |
| Cabecera | tipo en 11/16 uppercase + frase 15/21 |
| Fase | banner de módulo teñido: `Ahora · X` + `Siguiente · Y`; `WORK` acento, `RECOVER` columna, `WARMUP/COOLDOWN` ok |
| Aro | caja mínimo 184, radio 18, `inset`; círculo 132; trazo 8; pista blanca 8 % |
| Tiempo | `mm:ss`, 28 sp, tabular, se encoge hasta 12 |
| Intervalos | gráfico de 56 dp (96 completo); barras de 4–12 dp, separación 2–3; `WORK` acento con borde blanco 95 %, resto blanco 12 % |
| Etiquetas | `km/h` · `W` · `RPM` · `N` · `CAL`; pie `Ahora: …` / `Siguiente: …` / `¡Circuito completo!` / `Bloque i/N` |
| Estado | `En curso · X realizados · Y restantes` · `Pausado · X realizados` · `Listo para confirmar…` · `Cardio registrado · X` |
| GPS | `Señal GPS activa · D · T` · `Señal GPS perdida…` · `GPS pausado…` · `Solicitando permiso…` · `Permiso denegado…` · `Ubicación desactivada…` |
| Métricas | distancia `0,00 km`; ritmo `MM:SS/km` o `--/km`; splits `km N MM:SS` |
| Acciones | `Pausar GPS` / `Reanudar GPS` / `Iniciar GPS` / `Pausar` / `Reanudar` / `Iniciar`; `Completar bloque`; `Reintentar permiso de ubicación / GPS` |
| Registro | `Finalizar y registrar` / `Actualizar cardio`; habilitado si hay datos o está en marcha |

### 4.1 Honestidad del cardio

- Sin intervalos y sin objetivo, el plan es 20 min; se muestra como plan, no como dato medido.
- Sin FC, la píldora dice `—`; sin GPS, `—`.
- La confirmación de registro explica qué se guarda y advierte si no se pudo guardar.
- El RPE del cardio no se inventa: si no hay objetivo, se muestra solo el ancla disponible.

## 5. Criterio de aceptación

- [ ] Movilidad, aproximación y cardio usan el mismo marco de 470 dp.
- [ ] La movilidad no avanza sola al terminar el reloj.
- [ ] La escalera de aproximación coincide con el nivel y el tipo de ejercicio.
- [ ] Nunca se superan las 4 aproximaciones recomendadas sin aviso.
- [ ] El cardio sin GPS o sin FC muestra «—».
- [ ] La fase omitida se ve como omitida, no como completada.
