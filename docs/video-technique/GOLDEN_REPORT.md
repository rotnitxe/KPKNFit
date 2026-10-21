# VT10 · evaluación final de reserva compatible

> **Estado de aceptación final: ambos gates FALLARON, conteo y tiempos de giro.** La reserva compatible se evaluó una sola vez. No se retoca el motor, los umbrales, las etiquetas ni el reparto para mejorar este resultado, y no se vuelve a ejecutar esta reserva como una nueva aceptación ciega. La validación funcional Android posterior no cambia este veredicto.

Motor 1; manifiesto congelado SHA-256 `fd4b2f72d43bd59ada8b8b0af7d0ebbab129629c17e089e75aa46eec33b86c3b`.
Extracción: 97/97. Evaluados en esta corrida: 37. Evaluación final de reserva; el veredicto de conteo está en el gate.
Exacto FULL compara el punto de la etiqueta; correcto compara acceptableRange; ±1 usa el extremo más cercano. Parciales y fallidas se informan por separado. Sin MetricKey.REPS = sin salida, nunca acierto.

| Perfil | Nivel | n | Salidas/cobertura | FULL exacto | FULL ±1 | Rango | Rango ±1 | Error máx. | Parciales exactas | Fallidas exactas | ALTA correctas/ALTA | Conclusión |
|---|---|---:|---|---|---|---|---|---|---|---|---|---|
| BENCH | B | 4 | 4/4 (100.0 %) | 3/4 (75.0 %) | 4/4 (100.0 %) | 4/4 (100.0 %) | 4/4 (100.0 %) | 0 | 4/4 (100.0 %) | 4/4 (100.0 %) | 0/0 (sin salida) | ver gate |
| CURL | B | 4 | 4/4 (100.0 %) | 4/4 (100.0 %) | 4/4 (100.0 %) | 4/4 (100.0 %) | 4/4 (100.0 %) | 0 | 4/4 (100.0 %) | 4/4 (100.0 %) | 0/0 (sin salida) | ver gate |
| DEADLIFT | A | 2 | 2/2 (100.0 %) | 2/2 (100.0 %) | 2/2 (100.0 %) | 2/2 (100.0 %) | 2/2 (100.0 %) | 0 | 2/2 (100.0 %) | 2/2 (100.0 %) | 0/0 (sin salida) | n < 3: sin conclusión |
| HINGE | A | 1 | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 0 | 1/1 (100.0 %) | 1/1 (100.0 %) | 0/0 (sin salida) | n < 3: sin conclusión |
| LEG_CURL | B | 1 | 0/1 (0.0 %) | 0/0 (sin salida) | 0/0 (sin salida) | 0/0 (sin salida) | 0/0 (sin salida) | — | 0/0 (sin salida) | 0/0 (sin salida) | 0/0 (sin salida) | n < 3: sin conclusión |
| LEG_EXTENSION | B | 1 | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 0 | 1/1 (100.0 %) | 1/1 (100.0 %) | 0/0 (sin salida) | n < 3: sin conclusión |
| LUNGE | B | 2 | 2/2 (100.0 %) | 1/2 (50.0 %) | 2/2 (100.0 %) | 1/2 (50.0 %) | 2/2 (100.0 %) | 1 | 2/2 (100.0 %) | 2/2 (100.0 %) | 0/0 (sin salida) | n < 3: sin conclusión |
| MACHINE_SQUAT | B | 1 | 1/1 (100.0 %) | 0/1 (0.0 %) | 1/1 (100.0 %) | 0/1 (0.0 %) | 1/1 (100.0 %) | 1 | 1/1 (100.0 %) | 1/1 (100.0 %) | 0/0 (sin salida) | n < 3: sin conclusión |
| PULL_VERTICAL | A | 1 | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 0 | 1/1 (100.0 %) | 1/1 (100.0 %) | 0/0 (sin salida) | n < 3: sin conclusión |
| RAISE | B | 2 | 2/2 (100.0 %) | 2/2 (100.0 %) | 2/2 (100.0 %) | 2/2 (100.0 %) | 2/2 (100.0 %) | 0 | 2/2 (100.0 %) | 2/2 (100.0 %) | 0/0 (sin salida) | n < 3: sin conclusión |
| ROW | B | 5 | 5/5 (100.0 %) | 3/5 (60.0 %) | 4/5 (80.0 %) | 3/5 (60.0 %) | 4/5 (80.0 %) | 7 | 3/5 (60.0 %) | 4/5 (80.0 %) | 0/0 (sin salida) | ver gate |
| SQUAT | A | 4 | 4/4 (100.0 %) | 2/4 (50.0 %) | 4/4 (100.0 %) | 2/4 (50.0 %) | 4/4 (100.0 %) | 1 | 2/4 (50.0 %) | 4/4 (100.0 %) | 0/0 (sin salida) | ver gate |
| TRICEPS_EXTENDED_REST | B | 1 | 1/1 (100.0 %) | 0/1 (0.0 %) | 0/1 (0.0 %) | 0/1 (0.0 %) | 1/1 (100.0 %) | 1 | 1/1 (100.0 %) | 1/1 (100.0 %) | 0/0 (sin salida) | n < 3: sin conclusión |
| TRICEPS_FLEXED_REST | B | 1 | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 1/1 (100.0 %) | 0 | 1/1 (100.0 %) | 1/1 (100.0 %) | 0/0 (sin salida) | n < 3: sin conclusión |

Perfiles sin n suficiente no entran en el porcentaje de su nivel.
- Nivel A, perfiles con n >= 3: 4 etiquetas; 4 salidas; rango 2/4 (50.0 %); cobertura 100.0 %; ALTA 0/0 (sin salida) (cobertura 0.0 %).
- Nivel B, perfiles con n >= 3: 13 etiquetas; 13 salidas; rango 11/13 (84.6 %); cobertura 100.0 %; ALTA 0/0 (sin salida) (cobertura 0.0 %).
- Nivel C, perfiles con n >= 3: 0 etiquetas; 0 salidas; rango 0/0 (sin salida); cobertura sin salida; ALTA 0/0 (sin salida) (cobertura sin salida).

| Subconjunto separado | n | Salidas | FULL exacto | Rango | ±1 | ALTA correctas/ALTA |
|---|---:|---:|---|---|---|---|
| negative_back_view | 5 | 5 | 5/5 (100.0 %) | 5/5 (100.0 %) | 5/5 (100.0 %) | 0/0 (sin salida) |
| uncertain | 2 | 2 | 0/2 (0.0 %) | 0/2 (0.0 %) | 1/2 (50.0 %) | 0/0 (sin salida) |
| softWarnings (no excluyen por sí solos) | 17 | 16 | 11/16 (68.8 %) | 12/16 (75.0 %) | 14/16 (87.5 %) | 0/0 (sin salida) |
| corte mencionado en softWarnings (no excluye por sí solo) | 0 | 0 | 0/0 (sin salida) | 0/0 (sin salida) | 0/0 (sin salida) | 0/0 (sin salida) |

| Vista etiqueta → motor | n | Salidas |
|---|---:|---:|
| BACK → OBLIQUE | 5 | 5 |
| FRONT → FRONT | 1 | 1 |
| FRONT → OBLIQUE | 3 | 3 |
| LYING → FRONT | 1 | 1 |
| LYING → LYING | 3 | 2 |
| OBLIQUE → FRONT | 2 | 2 |
| OBLIQUE → LYING | 1 | 1 |
| OBLIQUE → OBLIQUE | 12 | 12 |
| SIDE → OBLIQUE | 2 | 2 |
| SIDE → SIDE | 7 | 7 |

| Clip | Reparto | Perfil | Etiqueta F/P/f | Motor F/P/f | Disponible REPS | Confianza | Vista motor | Exclusión |
|---|---|---|---|---|---|---|---|---|
| g07-pixabay-39379 | holdout | OHP | 6/0/0 | 6/0/0 | true | BAJA | OBLIQUE | negative_back_view |
| g24-pixabay-44001 | holdout | CURL | 12/0/0 | 12/0/0 | true | BAJA | FRONT |  |
| g26-pixabay-44005 | holdout | SQUAT | 6/0/0 | 6/0/0 | true | BAJA | SIDE |  |
| r02-pixabay-67000 | holdout | ROW | 12/0/0 | 5/1/0 | true | BAJA | OBLIQUE |  |
| r03-pixabay-66367 | holdout | HIP_THRUST | 8/2/0 | 5/2/0 | true | BAJA | OBLIQUE | uncertain |
| r08-pixabay-66368 | holdout | LUNGE | 10/0/0 | 9/0/0 | true | BAJA | SIDE |  |
| r09-pixabay-66999 | holdout | BENCH | 7/0/0 | 7/0/0 | true | BAJA | LYING |  |
| r16-pixabay-30417 | holdout | DEADLIFT | 5/0/0 | 5/0/0 | true | BAJA | OBLIQUE |  |
| r17-pixabay-72242 | holdout | ROW | 7/6/0 | 14/0/0 | true | BAJA | SIDE | uncertain |
| r18-pixabay-29832 | holdout | CURL | 5/0/0 | 5/0/0 | true | BAJA | FRONT |  |
| r24-commons-bent-over-row | holdout | ROW | 2/0/0 | 2/0/0 | true | BAJA | OBLIQUE |  |
| r25-commons-shoulder-press | holdout | OHP | 2/0/0 | 2/0/0 | true | BAJA | OBLIQUE | negative_back_view |
| x06-pixabay-72241 | holdout | ROW | 10/0/0 | 10/0/0 | true | BAJA | SIDE |  |
| x07-pixabay-67789 | holdout | LUNGE | 8/0/0 | 8/0/0 | true | BAJA | OBLIQUE |  |
| x08-pixabay-66372 | holdout | LUNGE | 6/0/0 | 6/0/0 | true | BAJA | OBLIQUE | negative_back_view |
| x15-commons-deadlift | holdout | DEADLIFT | 2/0/0 | 2/0/0 | true | BAJA | OBLIQUE | negative_back_view |
| x16-commons-bench | holdout | BENCH | 2/0/0 | 2/0/0 | true | BAJA | LYING |  |
| x17-commons-pullups | holdout | PULL_VERTICAL | 1/0/0 | 1/0/0 | true | BAJA | OBLIQUE | negative_back_view |
| x18-commons-incline | holdout | BENCH | 2/0/0 | 1/0/0 | true | BAJA | OBLIQUE |  |
| ymove_001 | holdout | SQUAT | 4/0/0 | 4/0/0 | true | BAJA | OBLIQUE |  |
| ymove_002 | holdout | BENCH | 3/0/0 | 3/0/0 | true | BAJA | FRONT |  |
| ymove_003 | holdout | DEADLIFT | 5/0/0 | 5/0/0 | true | BAJA | OBLIQUE |  |
| ymove_004 | holdout | RAISE | 5/0/0 | 5/0/0 | true | BAJA | OBLIQUE |  |
| ymove_005 | holdout | CURL | 4/0/0 | 4/0/0 | true | BAJA | OBLIQUE |  |
| ymove_006 | holdout | ROW | 4/0/0 | 3/3/1 | true | BAJA | LYING |  |
| ymove_007 | holdout | SQUAT | 5/0/0 | 4/1/0 | true | BAJA | OBLIQUE |  |
| ymove_010 | holdout | HINGE | 5/0/0 | 5/0/0 | true | BAJA | OBLIQUE |  |
| ymove_011 | holdout | SQUAT | 5/0/0 | 4/1/0 | true | BAJA | OBLIQUE |  |
| ymove_012 | holdout | PULL_VERTICAL | 5/0/0 | 5/0/0 | true | BAJA | SIDE |  |
| ymove_013 | holdout | ROW | 2/0/0 | 2/0/0 | true | BAJA | SIDE |  |
| ymove_016 | holdout | RAISE | 6/0/0 | 6/0/0 | true | BAJA | OBLIQUE |  |
| ymove_018 | holdout | CURL | 5/0/0 | 5/0/0 | true | BAJA | OBLIQUE |  |
| ymove_019 | holdout | TRICEPS_FLEXED_REST | 4/0/0 | 4/0/0 | true | BAJA | SIDE |  |
| ymove_020 | holdout | TRICEPS_EXTENDED_REST | 4/0/0 | 2/0/0 | true | BAJA | OBLIQUE |  |
| ymove_021 | holdout | LEG_EXTENSION | 4/0/0 | 4/0/0 | true | BAJA | FRONT |  |
| ymove_022 | holdout | MACHINE_SQUAT | 3/0/0 | 2/0/0 | true | BAJA | OBLIQUE |  |
| ymove_023 | holdout | LEG_CURL | 5/0/0 | 0/0/0 | false | BAJA | LYING |  |

| Giro fiable lote 1 | Etiquetas | Detectados | Pares | MAE ms | Máx ms | Sin pareja etiqueta/motor |
|---|---:|---:|---:|---|---|---|
| r02-pixabay-67000 | 12 | 6 | 6 | 206.7 | 263.321 | 6/0 |
| r03-pixabay-66367 | 10 | 7 | 6 | 788.9 | 1266.735 | 4/1 |
| r08-pixabay-66368 | 10 | 9 | 9 | 29.6 | 66.691 | 1/0 |
| r09-pixabay-66999 | 7 | 7 | 7 | 128.6 | 266.718 | 0/0 |
| r16-pixabay-30417 | 5 | 5 | 5 | 66.7 | 133.364 | 0/0 |
| r17-pixabay-72242 | 13 | 14 | 13 | 56.4 | 133.308 | 0/1 |
| r18-pixabay-29832 | 5 | 5 | 5 | 382.8 | 446.799 | 0/0 |
| x06-pixabay-72241 | 10 | 10 | 10 | 66.6 | 183.267 | 0/0 |
| x07-pixabay-67789 | 8 | 8 | 8 | 29.1 | 49.962 | 0/0 |
| x08-pixabay-66372 | 6 | 6 | 0 | — | — | 6/6 |

Sin etiquetas de fases a precisión de fotograma: MAE 50 ms e ICC CON/EXC no validados. Profundidad κ y error del círculo/disco no validados: faltan etiquetas. m/s, fps reales, calor y latencia en teléfono pendientes. El diámetro del banco es supuesto, por lo que PLATE_SCALE_MISMATCH no permite medir falsos positivos con diámetro verificado.
La pérdida de velocidad publicada no acredita fatiga real; durationError y la ausencia de fatiga visible necesitan una medida/etiqueta adicional. No se ajustaron el dominio ni los umbrales.

Gate de conteo: FAIL.
- SQUAT: 2/4 dentro del rango; requiere 95.0 %.
- ROW: 3/5 dentro del rango; requiere 90.0 %.
- r02-pixabay-67000: error 7 contra el extremo más cercano del rango.
- Sin salidas ALTA: precisión ALTA no validada (cobertura 0).
