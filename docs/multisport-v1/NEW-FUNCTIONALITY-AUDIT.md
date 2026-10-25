# Qué es nuevo frente a KPKN pre release 17

Revisión de solo lectura sobre la base `f174a5020`, contrastada con el código Android. Esta distinción aplica al catálogo de prototipos; no declara implementadas las ampliaciones.

| KPKN ya tiene | Incremento que debe demostrar el prototipo |
|---|---|
| Home, Training, Nutrition, Body y navegación | Se conserva su lenguaje visual como contexto. No son escenarios nuevos. |
| Un perfil de objetivo, días, minutos, lugares y material en el wizard | Varios deportes y objetivos simultáneos, experiencia independiente, prioridad explícita y conservación de objetivos secundarios; también un solo deporte y cambio de disciplina. |
| Tablero semanal con movimiento por día y conflictos de material | Ventanas con inicio y fin por día/sede, clases fijas, solapamientos horarios y comparación de alternativas entre deportes. |
| Registro de fuerza, esfuerzo RIR/RPE/fallo y cardio en el runner | Planificación híbrida, relevo explícito entre modalidades, resumen conjunto sin doble conteo y progreso por disciplina. El registro actual no se presenta como novedad ni se restringe a RIR. |
| RM estimado y progreso en el perfil | Controles especializados y decisiones revisables; la estimación existente solo es contexto. Cambiar de fórmula no es una mejora asumida. |
| Cardio con duración, distancia, ritmo, FC, vatios, cadencia, fases, GPS y entrada manual | Ampliaciones deportivas específicas y su relación con un plan híbrido. El catálogo no duplica la línea Cardio v2 de otro agente. |
| Perfiles de Halterofilia, Calistenia, Strongman y Armwrestling | Registro de complejos conectados, progresiones de habilidades e isometrías, medleys e implementos y registros por lado/ángulo. Un nombre de perfil no equivale a estos motores. |

## Evidencia del estado existente

- `domain/onboarding/EntrenoContracts.kt`: perfiles actuales, incluido fuerza/cardio.
- `screens/onboarding/design/entreno/plan/GoalProfileList.kt`: selección de un perfil.
- `screens/onboarding/entreno/EntrenoWeekdaysStep.kt`, `EntrenoSessionTimeStep.kt`, `EntrenoPlacesStep.kt`, `EntrenoMaterialStep.kt`: disponibilidad y recursos actuales.
- `screens/onboarding/entreno/EntrenoWeekLayoutStep.kt`: tablero por día y conflictos de material.
- `screens/workout/components/SetExecutionCard.kt` y `screens/workout/EffortScale.kt`: registro y escalas de esfuerzo existentes.
- `screens/workout/components/ExerciseRmSheet.kt` y `screens/profile/ProfileScreen.kt`: RM estimado y progreso existentes.
- `screens/sessioneditor/components/CardioEditorCard.kt`, `screens/workout/CardioLiveCard.kt` y `screens/workout/WorkoutV2Body.kt`: edición y ejecución actuales de cardio y fuerza.

Rutas relativas a `android-native/app/src/main/java/com/example/kpkn/`. El índice general está en `docs/ANDROID_UI_SCREENS_MAP.md`.

## Casos que impiden aceptar una maqueta

- Seleccionar natación sola y recibir una semana de fuerza y running.
- Continuar con cero disciplinas sin una resolución explícita.
- Pedir powerlifting sin barra y recibir sustitutos presentados como powerlifting completo.
- Editar un parámetro avanzado y que la propuesta siga usando un valor fijo anterior.
- Mostrar una localización de una acción cuando la persona solo introdujo un contador.
- Contar una pantalla existente o una reconstrucción de Home como una ampliación.

Estos casos se revisan en el HTML renderizado antes de entregarlo. La revisión estática de novedad no sustituye las pruebas de interacción ni la aprobación visual del usuario.
