# Fase 2 · Esqueleto

Objetivo: fijar el chrome que no cambia mientras se entrena. Si esta fase está bien, cualquier estado de las fases 3–7 se puede colocar sin decidir de nuevo dónde va el título, el tiempo, la ruta o el registro.

Fuentes: `WorkoutHeaderBar.kt`, `LiveSessionLine.kt`, `WorkoutV2Body.kt`, `LiveSessionCardFrame.kt`, `LiveSessionLineSteps.kt`, `components/WorkoutRoadmapBar.kt`.

## 1. Orden de capas

| z | Capa | Origen |
|---|---|---|
| 0 | Fondo `#000` | `KpknSurface.canvas` |
| 1 | Tarjeta en pager (con sus caras) | `HorizontalPager` + `LiveSessionCardFrame` |
| 5 | Raíl de sesión | `WorkoutScreen.kt:1039` |
| 6 | Velo de hoja | `KpknSheet` |
| 8 | Cabecera | `WorkoutHeaderBar.kt:423` |
| 12 | Banda del relator | `WorkoutV2Body.kt:2351` |
| 20 | Snackbar | `WorkoutScreen.kt:886` |
| 30 | Aviso de sesión desaparecida | `WorkoutScreen.kt:855` |

La cabecera se pinta **encima** del pager para que la tarjeta pueda pasar por debajo al hacer scroll horizontal; su degradado inferior la separa. El raíl queda por debajo de las hojas y por encima de la tarjeta.

## 2. Cabecera

### 2.1 Contenedor

| Propiedad | Valor |
|---|---|
| Márgenes | 12 dp a izquierda y derecha |
| Alto de contenido | 10 + 48 + 3 + 27 + 7 = **95 dp** + 24 dp de barra de estado = **119 dp** |
| Fondo | `canvas` + portada del programa al **18 %** + `kpknCoverGlass` |
| Degradado | opaco hasta el 70 % del alto; transparente al 100 % |
| Compacta | si alto < 800 dp: 0 + 44 + 0 + 27 + 0 + 24 = **95 dp**; título 18/20, subtítulo 11/14 |
| Escala | `headerScale = 0,80 × viewportScale × headerMul` en compacta |

### 2.2 Fila 1

| Elemento | Especificación |
|---|---|
| Título | `nombre del ejercicio` en tinta + ` · ` + cada chip de ejercicio en `textMuted`. Una línea, `softWrap=false`. 20/24 normal, 18/20 compacto. |
| Marquesina | solo si desborda y no hay movimiento reducido: 2500 ms quieto, recorrido lineal, 2500 ms quieto. Fundido de 16 dp en ambos bordes. |
| Subtítulo | `protocolo · grupo · sesión`. 13/18 normal, 11/14 compacto. `textMuted`, una línea. |
| Iconos | libro (historia), micrófono (voz), más (opciones). Círculo de 40 dp en layout de 48; compacto 44. Icono de 20 dp. |
| **Sin «atrás»** | La ‹ circular de los prototipos v2–v5 **no existe** en el código. Se retira. Atrás es del sistema y lo intercepta `WorkoutBackNavigation`. |

### 2.3 Rail de chips (orden exacto)

| # | Chip | Condición | Abre |
|---|---|---|---|
| 1 | `M:SS` + anillo + `/ X′` | siempre | `SessionTimeSheet` |
| 2 | `ULTRARRÁPIDO` en verde | si está aplicado | `SessionTimeSheet` |
| 3 | `Superserie` + icono swap en rojo | si el ejercicio está en superserie | nada (informativo) |
| 4 | `{etiqueta} ▾` | por cada etiqueta principal activa | `WorkoutTagManagerModal` |
| 5 | `{sub-etiqueta} ×` | por cada sub-etiqueta activa | quita la sub-etiqueta |
| 6 | `Etiquetas` / `Sin etiquetas` | si no hay etiquetas principales | `WorkoutTagListOverlay` |

- Alto de chip: 27 dp; radio píldora; fondo `inset`; texto 12/16 escalado; separación 6 dp.
- Cronómetro: anillo de 18 dp, trazo 1,6 dp, icono `Timer` 11 dp; el anillo solo si hay objetivo.
- El rail hace scroll horizontal y se funde 24 dp en el borde derecho. No se parte en dos filas.

## 3. Tarjeta

| Propiedad | Valor |
|---|---|
| Relación | 312 / 425 (`LiveCardWidthToHeight`) |
| Ancho | `min(393 − 48, 358) = 345 dp` |
| Alto | `345 / 0,7341 = 470 dp` |
| Radio | 28 dp |
| Fondo | `#111111` |
| Filete | 1 dp blanco 7 % |
| Desenfoque | 0 dp normal; 7 dp si fallida; 6 dp si modo dios |
| Pager | 24 dp de pasarela, 14 dp entre páginas, `beyondViewport = 1` |
| Vecina | alpha `1 − offset × 0,58`, mínimo 0,38 |

La tarjeta se alinea arriba del escenario. **No se centra verticalmente**: el espacio que sobra abajo es la banda del relator.

## 4. Banda del relator

| Propiedad | Valor |
|---|---|
| Alto | `viewport − cabecera − tarjeta − reserva inferior − 12` = **107 dp** en 393 × 852 con inset de navegación de 24 dp |
| Reserva | barra de navegación + 120 dp del raíl + 12 dp |
| Contenido | carril de personajes 32 dp + 6 dp + dos líneas de 16 sp + 12 dp de relleno |
| Regla | si no hay relator, el hueco se queda; la tarjeta no crece. |

### 4.1 Contenido

- El carril de personajes (32 dp) y la línea de 13/16 los pinta `WorkoutLiveRelatorBar`.
- El copy lo produce el motor del relator (`WorkoutLiveRelatorScenes`); **este programa no lo rediseña**.
- El prototipo usa una línea factual de ejemplo («Serie 3 de 4 · 100 kg × 8 · RIR 2») para que la banda se lea; no inventa diálogo.
- En preparación, descanso y cardio la línea cambia de asunto, pero la altura no.

## 5. Raíl inferior

### 5.1 Caja

| Propiedad | Valor |
|---|---|
| Alto | 112 dp |
| Márgenes | 16 dp laterales; 8 dp sobre el inset de navegación |
| Radio | 26 dp |
| Fondo | `#E6161616` |
| Filete | 1 dp tinta al 10 % |
| Relleno | 6 dp |

### 5.2 Fila superior (44 dp)

| Elemento | Valor |
|---|---|
| Ruta | icono 20 dp + título `KpknType.chip` 13/16 + subtítulo 11 dp `{hecho}/{total} · {n} de {m} bloques` + chevron 16 dp. Pulsación corta abre Ruta; larga abre opciones del ejercicio. |
| Undo | píldora compacta de 44 dp, solo si hay algo que deshacer. |
| Acciones pendientes | píldoras de 88 dp, multilínea. |
| Añadir serie | píldora `+` si se puede. |

### 5.3 Fila de pasos (56 dp)

- Línea base de 1 dp tinta al 14 %, centrada.
- Cada paso: caja táctil de **48 × 56 dp**; píldora interior 44 dp de ancho × 34 dp de alto (44 si activo o calibrado); radio 18 (12 si calibrado).
- Activo: tinta con cifra negra. Completado: `ok`. Omitido: alpha 0,4. Volumen reemplazado: borde `#E53935`.
- Etiquetas: `MOV`, `APR`, `S1…Sn`, `S1 L/R`, `R1`, `Rest`.
- Si hay más de 9 pasos, se comprime conservando preparación, primero, último y activo ±2; aparece `…N` → «Ver N pasos lejanos».
- A la derecha, botón de registro de **52 dp**, círculo, acento `#F49A6E`, icono check 25 dp; deshabilitado al 38 %.

### 5.4 Arrastre

- Gesto horizontal sobre el carril: mueve pasos; al llegar al borde con ≥56 dp, salta al ejercicio adyacente.
- El paso activo se centra con animación; con movimiento reducido, salto directo.

## 6. Roadmap bar (variante cockpit)

`components/WorkoutRoadmapBar.kt` **no es el raíl de esta pantalla**. Es la barra de cockpit: 78 dp de alto, radio 26 arriba, fondo `#0D0D0D`, asa de 36 × 4, carrusel de 46 dp y tarjetas de 190–280 dp. Se documenta para no confundirla con `LiveSessionLine`; el prototipo de la sesión en vivo usa `LiveSessionLine`.

## 7. Lo que se retira de los prototipos anteriores

| Se retira | Motivo |
|---|---|
| Botón ‹ de la cabecera | No existe en `WorkoutHeaderBar`. |
| Píldora de descanso dentro de la cabecera | El descanso vive en el raíl/hoja, no en la cabecera. |
| Título de dos líneas | El código fuerza una línea con marquesina. |
| Tarjeta flotando centrada | El pager alinea arriba; el hueco es del relator. |
| Fondo gris del teléfono | El canvas es `#000000` puro. |

## 8. Criterio de aceptación

- [ ] La cabecera mide 119 dp en 393 × 852 y 95 dp en compacta.
- [ ] El título está en una línea y solo se mueve si desborda.
- [ ] El orden de chips es exactamente el de §2.3.
- [ ] No hay botón «atrás» en la cabecera.
- [ ] La tarjeta mide 470 × 345 y está alineada arriba.
- [ ] El raíl mide 112 dp y el botón de registro 52 dp.
- [ ] El paso activo se centra y los pasos lejanos se comprimen.
- [ ] La banda del relator no cambia el tamaño de la tarjeta.
