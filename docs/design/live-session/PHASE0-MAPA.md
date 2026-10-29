# Fase 0 · Mapa de la sesión en vivo

Fuente: código en `.worktrees/post-rings/android-native/app/src/main/java/com/example/kpkn/`.
Regla: cada elemento de los prototipos debe apuntar a una línea de esta tabla. Lo que no esté aquí no se dibuja.

## Geometría

| Zona | Origen | Medida / regla |
|---|---|---|
| Pantalla de referencia | `WorkoutHeaderBar.kt` + `SetExecutionCard.kt` | 393 × 852 dp |
| Cabecera | `WorkoutHeaderBar.kt:513-520` | a sangre, 2 filas + rail de chips, con degradado y portada al 18 % |
| Tarjeta | `SetExecutionCard.kt:1907` | `stableCardHeight`, publicado en `:1926`; **nunca cambia de tamaño** (`:3412`) |
| Radio / color / borde | `:1938-1942` | 28 dp · `#111111` · `KpknSurface.moduleBorder` |
| Desenfoque | `:1930-1935` | 7 dp si fallida, 6 dp en god mode |
| Rail inferior | `WorkoutSetPager.kt` + `LiveSessionLine.kt` | línea de sesión, píldoras y FAB de registro |
| Banda del relator | `WorkoutV2Body.kt:2351-2368` + `WorkoutLiveRelatorBar.kt` | resto entre tarjeta y raíl; 107 dp en 393 × 852. El copy lo produce el motor del relator. |

## Cabecera (`WorkoutHeaderBar.kt`)

| Elemento | Línea |
|---|---|
| Título `ejercicio · chip · chip`, una línea, marquesina si desborda | `:532-630` |
| Subtítulo `protocolo · grupo · sesión` | `:633-643` |
| Iconos: libro, micrófono, más (44 dp en compacto) | `:649-665` |
| Rail de chips con scroll: cronómetro, ULTRARRÁPIDO, Superserie, tags (▾), sub-tags (×), «Etiquetas»/«Sin etiquetas» | `:669-827` |
| Cronómetro con anillo y `/ objetivo` | `:165-355` |

## Tarjeta de serie (`SetExecutionCard.kt`)

| Orden | Elemento | Línea |
|---|---|---|
| 1 | «Cluster set: próximamente» | `:1957-1964` |
| 2 | Selector `Izquierda (L)` / `Derecha (R)` | `:1966-2017` |
| 3 | Badge AMRAP `Mínimo N · AMRAP/Calibrador` + explicación | `:2020-2026` |
| 4 | Banda de volumen reemplazado / pausa técnica | `:2028-2047` |
| 5 | Aviso «Elige una carga para N reps dejando R en reserva» | `:2049-2071` |
| 6 | Héroe de carga (superficie `#1A1A1A`, 24 dp) | `:2073-2148` |
| 7 | Rueda de reps + parciales/ayuda + vídeo | `:2157-2411` |
| 8 | Esfuerzo (`EffortFireSlider`) | `:2418-2430` |
| 9 | Vídeo / ROM / acciones | `:2433-2477` |
| 10 | Acciones `Avanzado · Modo visual · Historial` dentro de la Surface, abajo | `:3402-3408` |

## Caras y overlays

| Elemento | Origen |
|---|---|
| Técnica: Normal/Drop/Pausa/Myo, cadena, tramos, nota, error | `SetCardTechniqueBack.kt:117-142`, `SetExecutionCard.kt:352-630, 723-946` |
| Media: switch Foto/Vídeo, ficha, álbum, miniaturas, obturador, análisis | `SetCardExerciseMediaBack.kt:159-185` |
| Fallida: overlay rojo con causa | `SetExecutionCard.kt:3596-3650` |
| God mode | `:3500-3510` |
| Resultado AMRAP | `LiveCalibrationResult.kt` |
| Técnica guiada | `:3414-3443` |
| Peso corporal asistido | `:3446-3497` |
| Undo | `LiveUndoPill.kt` |

## Preparación

| Elemento | Origen |
|---|---|
| Movilidad: movimiento / lista / global / fin / descanso | `WarmupMobilityLiveCards.kt:117-371, 374-568` |
| Aproximación: escalera, ancla, añadir, omitida, descanso | `:575-775` |
| Cardio | `CardioLiveCard.kt`, `CardioIntervalChart.kt` |

## Descanso

| Elemento | Origen |
|---|---|
| Tarjeta, sugerido, ±15 s, saltar | `RestLiveCard.kt:81-432` |
| Tiempo extra | `RestOverrunCard` |
| Mini chip | `RestMiniChip.kt:61-142` |
| Juego | `RestMiniGame.kt` |
| Feedback post-ejercicio | `WorkoutPostExerciseFeedbackCard` |

## Hojas

Menú, ruta, temporizador, RM, calorías, notas, hitos, fotos (`LiveSessionSheets.kt:60-135`), opciones de ejercicio (`ExerciseOptionsSheet.kt`), etiquetas (`WorkoutExerciseSheets.kt:99-106`), config AMRAP (`SetExecutionCard.kt:150-182`), molestias (`DiscomfortSheet.kt`), álbum (`ExerciseAlbumSheet.kt`), RM (`ExerciseRmSheet.kt`), ultra-rápido (`UltraFastRecordSheet.kt`), reordenar (`LiveReorderSheet.kt`), historial (`WorkoutHistorySheetContent.kt`).

## Mesa (no existe en la app)

Vista derivada, no una pantalla nueva. Usa estas piezas existentes:
- fila = `CompletedSet` / `CompletedExercise`;
- etiqueta en la cabecera del ejercicio (`activeMainTags`);
- acciones de serie = las mismas del pie de la tarjeta;
- acciones de ejercicio = `ExerciseOptionsSheet`;
- media en la fila = `SetCardExerciseMediaBack` reducido.

Queda prohibido introducir acciones que no existan en las tablas anteriores.
