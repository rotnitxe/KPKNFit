# Fase 3 · Tarjeta de serie (cara frontal)

Objetivo: que la tarjeta se pueda implementar sin preguntar «¿y dónde va esto?». El orden es fijo; lo que cambia entre modos es qué bloques aparecen y qué cifra manda.

Fuentes: `SetExecutionCard.kt` (3693 líneas), `LiveModeSetContent.kt`, `WorkoutHorizontalWheelPicker.kt`, `EffortFireSlider.kt`, `EffortScale.kt`, `LiveSetMode.kt`, `LiveSetPrPolicy.kt`, `LoadSuggestionDisplayPolicy.kt`, `WorkoutRecordingEffort.kt`, `CompletedSetPatch.kt`.

## 1. Contenedor

| Propiedad | Valor |
|---|---|
| Tamaño | 345 × 470 dp (máx. 358 de ancho) |
| Radio | 28 dp |
| Fondo | `#111111` |
| Filete | 1 dp blanco 7 % |
| Desenfoque | 7 dp fallida · 6 dp modo dios · 0 resto |
| Giro | `tween(420)`; frontal alpha 0 a 90° |
| Scroll | Solo en el layout legacy (REPS/AMRAP): `padding(bottom=64)` + scroll interno. El resto usa peso 1. |
| Padding | No hay padding exterior único: cada bloque aplica 16 dp laterales; el héroe es a sangre. |

## 2. Orden vertical canónico (REPS)

| # | Bloque | Condición | Medidas |
|---|---|---|---|
| 0 | `Cluster set: próximamente` | técnica `CLUSTER_SET` | 12 sp bold, blanco 70 % |
| 1 | Selector `Izquierda (L)` / `Derecha (R)` | unilateral, sin bloqueo de lado, sin guía | superficie `inset`, radio 20, relleno 6, dos botones 50 %; alto ~44 |
| 2 | Badge AMRAP + explicación | AMRAP o calibrador | badge 12 sp bold `#F49A6E`, padding 16/4; explicación 11/15 blanco 65 % |
| 3 | Banda de volumen reemplazado o pausa técnica | `volumeReplacedLabel != null` o pausa > 0 | líneas 1 dp `#E53935` 85 %; texto 9 sp 0,8 sp bold; o línea centrada 9 sp |
| 4 | Aviso de carga requerida | carga manual obligatoria y no solo RPE | 11 sp bold acento, centrado, padding 10 |
| 5 | **Héroe de carga** | REPS/AMRAP/RM oculto; no en RM | `#1B1B1B`, radio superior 24, mínimo 132 dp (96 con ROM), padding 16/12 |
| 6 | División | solo legacy | 1 dp blanco 8 % |
| 7 | Cuerpo | REPS: rueda; otros: cantidad | peso 1, `SpaceBetween` |
| 8 | Esfuerzo | siempre en fuerza; en SOLO RPE va arriba | pista 18 dp en zona táctil 44; pulgar 26 |
| 9 | ROM | si el ejercicio lo mide | fila 44 dp; slider 10–100 %, valor chip tabular |
| 10 | Resultado (AMRAP/calibrador) | si hay resultado | panel sobre la tarjeta, no la alarga |
| 11 | Estado | siempre | 11/14 `textMuted`; rojo `#EF6F64` si error |
| 12 | Acciones | siempre, al fondo | 44 dp; `Avanzado` / `Modo visual` / `Historial` |

### 2.1 Héroe de carga

- Etiqueta `CARGA (kg)` en 10/14 `textMuted`; si el campo es estrecho, `CARGA`.
- Valor 34/44 Syne Bold, tabular, centrado; baja a 26 sp si tiene 7 o más caracteres.
- Placeholder: `Carga a elegir` si es manual; si no, `Peso corporal`, `Ej: 10`, `Ej: 20` o vacío según el modo de carga.
- Chips: tres de 34 dp de alto, radio 11, fondo `#111111`, borde blanco 10 %. Etiqueta 9/12; valor 12/15. `Sugerida`/`Sugerido` + `—` si no hay dato.
- Con ROM: dos chips de mínimo 56 dp + campo de mínimo 64 dp.
- Botón de modo: 20 dp de alto; `Externa / Corporal / Lastre / Asistida` + chevron; abre `Carga / Peso corporal / Lastre / Asistido`.

### 2.2 Rueda de reps

- Alto 48 dp (52 si unilateral o superserie).
- Celda = ancho / 5; círculo 36 dp; centro en tinta con cifra negra 19 sp.
- Vecinas 15 sp `textMuted` sobre `inset`; 13 sp si 5+ caracteres; 11 sp si 7+.
- Fundido de 24 dp en los bordes; ajuste al centro.
- Cabecera `REPS` 10/14 + `+` de 16 dp que abre «Detalles de reps» (`Con ayuda`, `Parciales`; máx. 20).
- En tiempo: cabecera `TIEMPO` + `Restan / Registrado / Objetivo`; campo 60 dp + botón de reloj 48 dp.

## 3. Modos

| Modo | Cómo se reconoce | Qué manda | Qué se oculta |
|---|---|---|---|
| **REPS** | `Unit=REPS` sin AMRAP/RM/solo RPE | héroe de carga + rueda + esfuerzo | — |
| **AMRAP** | `purpose.isAmrap` | héroe + rueda + badge «Mínimo N · AMRAP» | nada; añade resultado |
| **Calibrador** | `purpose=CALIBRADOR` | igual que AMRAP, badge «Calibrador» | la exigencia de esfuerzo; el cero es válido con esfuerzo |
| **RM** | `Training=RM` o `%RM` con `LOAD/SOLO_RM` | `LiveRmFocus`: carga usada, % del 1RM/TM, reps estimadas, referencia | héroe grande y carga compacta |
| **Solo RPE** | `Training=SOLO_RPE` | esfuerzo prominente (22/28) + rueda + carga compacta | sugerencia de carga y aviso de carga |
| **Tiempo** | `Unit=TIME` | reloj de serie + cantidad min:s | héroe de carga; la carga va compacta |
| **Distancia** | `Unit=DISTANCE` | metros + objetivo + barra | héroe de carga |
| **Personalizado** | `Unit=CUSTOM` | cantidad + unidad propia | héroe de carga |
| **Unilateral** | eje, no modo | selector L/R + fantasmas por lado | — |

### 3.1 RM en detalle

- `CARGA UTILIZADA · kg` 38 sp + `{p} %` en `#F49A6E` 23 sp + `del 1RM/TM`.
- `Calculado: {peso} kg · {reps} reps est.`; referencia `Tu referencia` / `Sin referencia` / `PR: {peso} kg × {reps}` / `TM`.
- Edición: `Volver`, `REPS EST.`, `%RM / %TM`, `PESO · kg` 22 sp, slider de energía 44 dp, `REFERENCIA 1RM · kg`.
- El porcentaje del RM se acota a 40–100; con TM o peso real, solo exige ≥0,01.

### 3.2 Solo RPE

- Carga compacta: `PESO CORPORAL` + campo 80 dp 23 sp + botón de modo.
- Esfuerzo arriba, prominente; el segundo slider se oculta.
- Estado: «Sin carga fija. Elige el peso al hacer la serie.»
- El RPE por defecto es 8,0 y se acota a 1–10; no se inventa si el plan no lo trae.

## 4. Esfuerzo

| Concepto | Regla |
|---|---|
| Escala | RIR 10…0 + `FALLO`; RPE = 10 − RIR; Fallo = 10; sin marcar = `—`. |
| Palabras | cómodo · firme · exigente · muy duro · al límite · todo |
| Fuego | niveles 0–5; partículas 0, 0, 2, 6, 11, 16; periodo 2,4 → 0,8 s. |
| Marcador de plan | barra de 2 × 32 dp blanca al 25 %. No preselecciona. |
| Obligatorio | fuerza no cardio: sin esfuerzo no se registra. |
| Mensaje | «Marca tu esfuerzo RIR o RPE antes de registrar la serie.» |
| AMRAP | guarda reserva o fallo; nunca un RPE suelto. |
| Accesibilidad | «RIR 2 · firme»; el fallo se anuncia «al fallo». |

## 5. Estados

| Estado | Qué se ve | Qué no se hace |
|---|---|---|
| **Completada** | Campos rellenados con lo registrado; el botón pasa a «Actualizar serie»; no duplica. | No se crea otra serie. |
| **PR** | Sin insignia en la frontal: el código dispara un háptico al registrar. El rastro visible es el atajo `PR: foto/video` de la trasera. | No se inventa el PR: sale del historial. |
| **Fallida** | Desenfoque 7 dp + overlay rojo `#8B1E1E` 96 %; copy completo; chips de causa; `Revertir`. El velo va nítido y el título en tinta. | No es «fallo muscular»: es incomodidad, molestia o técnica. |
| **Carga requerida** | Aviso en acento + toast «Carga inválida. Revisa el peso antes de registrar.» | No se registra con carga vacía. |
| **Volumen reemplazado** | Banda `#E53935`; peso automático; valor `3` si es por reps. | No suma volumen normal. |
| **Pausa técnica** | Banda o línea con cuenta atrás de segundos. | No se puede registrar hasta que termine. |
| **Saltada** | **No es un estado de la tarjeta.** Vive en el raíl (alpha 0,4) y en `SeriesOptionsSheet` (`Saltar` + `Deshacer`). | No se dibuja una tarjeta «saltada» inventada. |
| **En curso (otra página)** | Sin overlays; la tarjeta se atenúa si es vecina. | No se muestran avisos de una serie que no está asentada. |

### 5.1 Overlay de fallo (copy literal)

- Título: «Marcaste esta serie como fallida».
- Cuerpo: «Quiere decir que tuviste una incomodidad, molestia o deformación de la técnica que te impidió completarla. Ten cuidado y siempre procura usar cargas que puedas manejar.»
- «Causa o motivo del fallo (opcional):»
- Placeholder: «Ej. Molestia en hombro, fallo técnico, carga excesiva…»
- Chips: `Molestia articular`, `Fallo técnico`, `Carga excesiva`, `Pérdida de agarre`.
- Botón: `Revertir`.

## 6. Validación y bloqueos

| Caso | Mensaje |
|---|---|
| Carga vacía/0 | «Carga inválida. Revisa el peso antes de registrar.» |
| Asistido sin peso corporal | «Ingresa tu peso corporal antes de registrar» |
| Esfuerzo sin marcar | «Marca tu esfuerzo RIR o RPE antes de registrar la serie.» |
| Tiempo inválido | «Introduce un tiempo positivo en min:s (segundos de 0 a 59).» |
| Cantidad inválida | «Introduce una cantidad positiva.» |
| Pausa técnica | «Espera la pausa técnica antes de continuar.» |
| Objetivo de tiempo cumplido | «Objetivo cumplido · Confirma con ✓ para registrar» |
| Drop incompleto | «Completa peso y reps del drop.» |
| Rest-pause incompleto | «Indica las reps del rest-pause.» |

## 7. Números y límites

| Dato | Regla |
|---|---|
| Reps | Carrusel hasta `max(50, actual + 10, fantasma)`; e1RM solo cuenta hasta 50. |
| RIR | 0–10 (carrusel hasta `max(10, objetivo + 3)`). |
| RPE | 0–10 en pasos de 0,5; por defecto 8,0 si el modo lo exige. |
| Peso | coma decimal visible; sugerencia redondeada a 0,25 y mostrada a 2 decimales. |
| e1RM | 0,1 kg; fórmula Epley ajustada (`StrengthModel`). Sin dato, «—». |
| %RM | 2 decimales, `HALF_UP`; rango 40–100. |
| Descanso | ≥0; por defecto 90 s. |
| Tiempo | `SS` o `M:SS` con segundos 0–59; el reloj se detiene en el objetivo. |

## 8. Copy de la tarjeta

`Cluster set: próximamente` · `Izquierda (L)` · `Derecha (R)` · `Mínimo N · AMRAP` · `Sin mínimo · AMRAP` · `Mínimo N · Calibrador` · `Haz al menos N. Cuenta todas las que completes.` · `Cuenta todas las repeticiones que completes.` · `Pausa técnica · Ns` · `Elige una carga para N reps dejando R en reserva y registra la serie.` · `CARGA (kg)` · `Carga a elegir` · `Peso corporal` · `Sugerida` / `Sugerido` · `Externa` / `Corporal` / `Lastre` / `Asistida` · `REPS` · `TIEMPO` · `Restan` / `Registrado` / `Objetivo` · `Detalles de reps` · `Con ayuda` · `Parciales` · `ESFUERZO` · `Sin marcar` · `RIR` / `RPE` / `Fallo` · `cómodo` / `firme` / `exigente` / `muy duro` / `al límite` / `todo` · `ROM` · `Avanzado` · `Modo visual` · `Historial del ejercicio` · `Registra el resultado realizado con ✓` · `Objetivo cumplido · Confirma con ✓ para registrar` · `Sin carga fija. Elige el peso al hacer la serie.` · `Unidad del ejercicio: {u}` · `Actualizar serie`.

## 9. Criterio de aceptación

- [ ] Los 8 modos se distinguen por su cifra principal sin leer el subtítulo.
- [ ] La tarjeta sigue midiendo 470 dp en todos los modos y estados.
- [ ] El esfuerzo no viene preseleccionado y bloquea el registro.
- [ ] La serie completada se actualiza, no se duplica.
- [ ] La serie saltada solo existe en el raíl y en `SeriesOptionsSheet`.
- [ ] El fallo explica que no es fallo muscular.
- [ ] Ningún dato ausente se muestra como 0.
