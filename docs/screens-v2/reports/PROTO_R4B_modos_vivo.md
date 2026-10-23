R4-B completado. Los diez tableros están renderizados y verificados; todos quedan por debajo de 30.000 bytes.

| Tablero | Cambio, propuesta y base del código | Bytes |
|---|---|---:|
| B8b-SerieVivoROM | Fuego aprobado, degradado, plan tenue y chispas; pista propuesta de 18 px con área táctil de 44 px. ROM conserva la tarjeta de 358 × 430. `EffortScale.kt:171`. | 15.219 |
| V1-PropuestaLinea | Mismo deslizador, esfuerzo inicialmente sin marcar y marco V1 conservado. `EffortScale.kt:171`. | 29.300 |
| V2-IdeaUsuario | Mismo deslizador, conservando navegación y registro de V2. `EffortScale.kt:171`. | 29.298 |
| V3-PantallaCorta | Deslizador en ambas variantes; tarjetas de 312 × 425 y barras inferiores completas en altura corta. `LiveSessionCardFrame.kt:26`. | 29.804 |
| V6-VivoRM | Nuevo foco para porcentaje, referencia y resultado real. Ejemplo enlazado: 80 % de 125 kg → 100 kg y 7 reps estimadas. `Calculations.kt:132,288`. | 29.748 |
| V7-VivoAMRAP | Nuevo contador; conserva el resultado aunque quede bajo el mínimo. RPE 8 registra 2 reps de reserva; sugerencias según 1,10/0,90 del histórico. `EffortScale.kt:166`; `WorkoutSetRecorder.kt:991`. | 28.990 |
| V8-VivoSoloRPE | Escala protagonista; carga elegida al ejecutar y registro de kg, reps y esfuerzo. Sin sugerencia de carga fija. `Calculations.kt:290`. | 28.324 |
| V9-VivoTiempo | Reloj con inicio/detención, tiempo realizado y entrada min:s. Completar el objetivo espera el registro mediante FAB. `SetExecutionCard.kt:1764,2153`. | 29.207 |
| V10-VivoDistancia | Nuevo contador en metros, progreso y carga utilizada; registro con unidad DISTANCE. `ExerciseSetList.kt:407`; `SetExecutionCard.kt:3008`. | 28.480 |
| V11-VivoPersonalizado | Nuevo contador con «brazadas»; conserva la unidad definida por ejercicio y registra la cantidad real. `ExerciseEditorDialogs.kt:368`; `SetExecutionCard.kt:3017`. | 28.511 |

Las propuestas nuevas son la distribución por modo, los contadores/progreso, la edición enlazada de RM y la confirmación visible antes de avanzar. Los números son ejemplos; el histórico AMRAP simula una referencia de 160.

Verificado en Chromium con el mini runtime: registro completo de los seis modos, reloj hasta completar 60 segundos, reinicio del esfuerzo por serie, movimiento reducido, objetivos táctiles y enlaces. Solo se modificaron los diez HTML de R4-B.