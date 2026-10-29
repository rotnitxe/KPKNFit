# Fase 4 · Caras y overlays

Objetivo: resolver lo que está detrás de la tarjeta y lo que se le pone encima. Aquí es donde los prototipos anteriores inventaban más; esta fase separa lo real de lo que es una brecha.

Fuentes: `SetCardTechniqueBack.kt`, `SetCardExerciseMediaBack.kt`, `GuidedTechniquePanel.kt`, `GuidedTechniqueFlow.kt`, `LiveUndoPill.kt`, `LiveCalibrationResult.kt`, `GodModeChrome.kt`, `WorkoutBlockedBackHint.kt`, `VisualModeAnalysis.kt`, `WorkoutRestVideoLine.kt`.

## 1. Giro

| Propiedad | Valor |
|---|---|
| Ángulo | 0° frontal / 180° trasera |
| Duración | `tween(420)` si la página está asentada; `snap()` si no |
| Cámara | 18 × densidad |
| Alpha | 1 hasta 90°; 0 después |
| Contenido | La trasera se dibuja con `rotationY = 180°` para no quedar espejada |
| Tamaño | **el mismo**: 470 dp; la trasera no alarga la tarjeta |

## 2. Cara Técnica

Contenedor: `KpknModule` a sangre, padding 12, columna con 8 dp de separación, scroll interno y fundido vertical de 14 dp. El acento es `danger` si la serie es fallida.

### 2.1 Orden

| # | Elemento | Medidas / copy |
|---|---|---|
| 1 | Cabecera | `‹ Serie N` (vuelta) + `PR: foto/video` si aplica + `AVANZADO` 10/14 |
| 2 | Selector | `Normal` · `Drop-set` · `Rest-pause` · `Myo-reps`. Scroll horizontal, separación 4; segmento mín. 92 dp (100 si rest-pause), alto visual 30, táctil 48, radio 15. |
| 3 | Cadena | Título `CADENA DE LA SERIE`; `Principal` `95 × 6`; flechas; `Drop N` / `Pausa N` / `Mini N`; `+` |
| 4 | Eslabón abierto | Título + chip `Sugerido X kg` + marca `Hecho` + dos steppers (kg/reps o reps/segundos) |
| 5 | Explicación | `ASÍ SE VE UN DROP-SET` / `ASÍ SE VE UN REST-PAUSE` / `ASÍ SE VEN LOS MYO-REPS` + bloques |
| 6 | Nota | `NOTA DE LA SERIE` + campo de 2 líneas |
| 7 | Error | `Error de ejecución` (rojo si está activo) |

### 2.2 Medidas

| Elemento | Valor |
|---|---|
| Eslabón de cadena | 72 × 58 dp, radio 16, padding 4; título 10/12, valor 15/18, sub 11/13. Activo: borde 1 dp acento y fondo acento 10 %. |
| `+` de cadena | 44 × 58 dp, radio 16, borde 24 %, icono 16 dp, táctil 48. |
| Eslabón abierto | Caja radio 20, fondo `canvas`, padding 8, separación 4. |
| Stepper | Celda 44 dp, radio 14, fondo `inset`; botón 32 dp, icono −/+; valor 15/18 (12 si 5+ caracteres); unidad 10/12. |
| Explicación | Bloques de 30 dp, píldora, padding 10; el último con borde acento. |
| Nota | Campo KPKN a ancho completo, máximo 2 líneas. |
| Error | Píldora de mínimo 40 dp; táctil 48; texto 11 sp, máximo 2 líneas, centrado. |

### 2.3 Reglas

- Drop-set: máximo **5** tramos; el peso siguiente sugerido es `principal − 5 × i`, redondeado; editable.
- Rest-pause: por defecto 2 pausas; las reps se editan con ±1; los segundos con ±5.
- Myo-reps: exige **3–5 reps** y **10–30 s** para cerrar; el estilo se tiñe al registrar.
- Un tramo solo se guarda si `peso > 0` y `reps > 0`; si falta, no se inventa.
- Al completar todos los tramos, la técnica se autocierra (`all done`).
- Si el ejercicio es de volumen reemplazado, la guía manual no está disponible.
- La coma decimal se normaliza a punto antes de calcular.
- `Volver` no descarta: mantiene el borrador.

## 3. Cara Modo visual

Contenedor: `KpknModule` a sangre, padding 10, `testTag media_face`. Modo inicial **Vídeo**.

### 3.1 Orden

| # | Elemento | Medidas / copy |
|---|---|---|
| 1 | Cabecera | `‹ Serie N`, `Álbum`, `Expandir`; píldoras de 32 dp visual / 48 táctil; círculo de 32 para iconos |
| 2 | Escenario | Radio 22 compacto / 24 expandido; fondo radial `#2A2622 → #121110`; rejilla de tercios al 6 %; esquinas redondeadas |
| 3 | Switch Foto/Vídeo | Píldora radio 16, fondo negro 45 %, padding 3; opción 26 dp, radio 13, texto 12; se deshabilita grabando |
| 4 | Ficha | Píldora negra 45 %, borde tinta 18 %, icono `Info` 14 dp; `Ficha del ejercicio` |
| 5 | Galería | Miniaturas 36 dp (44 expandido), radio 12, táctil 48; badge de análisis de 16 dp con arco y `N/≈N/?/!`; `Ver álbum` |
| 6 | Obturador | Aro 60 dp (68 expandido), interior 44 (50); rojo `#EF6F64` en vídeo/grabando; cuadrado al 55 % grabando |
| 7 | Pie | Ficha técnica 36 dp; banda de 48 dp con `Se guarda en el álbum con la serie N · carga · esfuerzo.` |
| 8 | Análisis | `Analizando el vídeo de la serie N… P %` · `El vídeo cuenta N · ver análisis` · `≈ N` · `Vídeo no concluyente · ver por qué` · `No se pudo analizar · reintentar` |

### 3.2 Reglas

- Sin cámara: «Activa la cámara para grabar desde esta tarjeta».
- Error: `<error> · reintentar` en `danger`.
- Grabar vídeo abre la vista expandida; no graba desde la miniatura.
- Al salir de la página, cambiar de ficha o abrir el álbum, la grabación se detiene.
- El análisis nunca inventa reps: con confianza baja muestra `?` y no toca los parciales; no soportado se oculta el chip.
- La frase de análisis es la única acción de texto subrayada de forma sutil; su zona táctil es de 48 dp.

### 3.3 Vista expandida

- Portal a pantalla completa, 320 ms, sin animación con movimiento reducido.
- Margen 8 dp lateral, 16 dp vertical + safe drawing.
- Velo negro 60 % que traga toques.
- Título `Modo visual · serie N` + subtítulo de detalles.
- Botón `Plegar a la tarjeta` de 40 dp.
- No cambia el estado de la serie: es la misma cara, más grande.

## 4. Técnica guiada

| Fase | Copy | Control |
|---|---|---|
| Drop-set | `DROP-SET i/total` · `Sin descanso. Baja el peso y completa ~N reps.` | Dos campos: `Peso sugerido`, `Repeticiones`; `Sugerido: X kg para ~N reps` |
| Cuenta atrás | `MYO-REPS/REST-PAUSE i/total` · `Pausa fija de P s. Luego harás N reps.` | Reloj 22 dp + `S s` 28/32 + barra de progreso 6 dp |
| Reps | `Confirma las N reps de esta mini-serie.` | Campo `Repeticiones` |
| Salida | `Saltar técnica y registrar solo la serie` | Diálogo: `¿Registrar solo la serie principal?` · `Las sub-series de drop o rest-pause quedarán descartadas.` · `Sí, solo la serie` (danger) · `Seguir con la técnica` |

- Panel anclado abajo, `heightIn(max = alto − 112)`, mínimo 160 dp, fondo `module`, scroll, padding 10.
- La tarjeta no se alarga: el panel se superpone a la parte baja.
- La cuenta atrás avanza 1 s; si cambia la página, se aborta.
- Al terminar la pausa, pasa a pedir reps.
- El texto «Saltar técnica…» es de 48 dp de alto y máximo 2 líneas.

## 5. Overlays

| Overlay | Cuándo | Qué se dibuja |
|---|---|---|
| **Fallo** | serie marcada fallida | Velo rojo `#8B1E1E` al 96 %, copy, causa, chips, `Revertir`. La tarjeta queda desenfocada 7 dp; el velo va nítido encima. El título va en tinta, no en negro: sobre ese rojo el negro no alcanza contraste. |
| **Resultado AMRAP** | al registrar AMRAP | Panel sobre la tarjeta; no la alarga; editable. |
| **Peso corporal asistido** | `ASSISTED` sin peso | Panel inferior `#222222`, campo `Peso corporal (kg)` + `Guardar`. |
| **Undo** | tras registrar u omitir | Píldora de 40 dp visual / 48 táctil, tinta, texto `Deshacer {acción}`; 8 s; también en el raíl en formato compacto de 44 dp. |
| **Bloqueo de atrás** | intención de salir con serie en curso | Aviso del sistema (`WorkoutBlockedBackHint`), no una tarjeta inventada. |
| **God mode** | **no existe** | `GodModeSetActionsOverlay` es un stub (`GodModeChrome.kt:6-25`); sus acciones viven en `SeriesOptionsSheet`. Se documenta como brecha y **no se dibuja**. |

### 5.1 Undo

- Solo si la pila creció con la pantalla abierta y lo registrado no cambió.
- Si cambió la serie, no se ofrece.
- Al reanudar una sesión con pila previa, no se ofrece.
- Animación: fundido 200 ms + deslizamiento 260/200; movimiento reducido: solo fundido.

## 6. Brechas declaradas

| Brecha | Evidencia | Sustituto real |
|---|---|---|
| Modo dios | `GodModeChrome.kt:6-25` devuelve `Unit`; la tarjeta lo llama con `visible=false` | `SeriesOptionsSheet`: `Saltar`, `Borrar`, `Tipo de serie`, `Solo S{n}`, `Desde S{n}` |
| Cluster set | Solo un texto «próximamente» | No se dibuja un flujo de cluster |
| Acciones de serie en la tarjeta | Viven en `SeriesOptionsSheet`, no en la cara | Abrir la hoja con long-press del paso o desde la tarjeta si se expone |
| «Saltada» como tarjeta | No existe | Raíl atenuado + hoja de opciones |

## 7. Criterio de aceptación

- [ ] La trasera mide lo mismo que la frontal y no la alarga.
- [ ] El giro dura 420 ms y respeta movimiento reducido.
- [ ] La cadena de técnica se puede recorrer sin perder el borrador.
- [ ] Myo-reps no cierra con reps o pausas fuera de rango.
- [ ] El modo visual no inventa reps cuando la confianza es baja.
- [ ] El overlay de fallo explica la diferencia con el fallo muscular.
- [ ] El modo dios está marcado como brecha y no se dibuja.
- [ ] Undo no aparece si lo registrado cambió.
