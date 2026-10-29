# Fase 8 · Mesa y contrato de implementación

Objetivo: cerrar el programa con la única vista que no existe en la app (la mesa) y con el contrato que permite llevarlo a `android-native/` sin abrir una segunda arquitectura.

Fuentes: `PHASE0-MAPA.md` §Mesa, `WorkoutSetPager.kt`, `WorkoutV2Body.kt`, `CompletedSetPatch.kt`, `WorkoutCompletedExercises.kt`, `ExerciseOptionsSheet.kt`, `SetCardExerciseMediaBack.kt`, `WorkoutTagDisplay.kt`, `SeriesOptionsModel.kt`, `WorkoutEditingRules.kt`.

## 1. Mesa

### 1.1 Qué es

Una **vista derivada** de la sesión, no una pantalla nueva. Se abre desde la ruta o desde el menú y sirve para escanear, comparar y corregir sin pasar tarjeta por tarjeta.

- No crea datos.
- No crea acciones.
- No crea navegación nueva.
- No sustituye la tarjeta: la complementa.

### 1.2 Modelo de fila

| Campo | Origen |
|---|---|
| Ejercicio | `CompletedExercise` / `visibleExercises` |
| Etiqueta principal | `activeMainTags` → `workoutTagDisplayTitle` |
| Serie | `CompletedSet` o plan |
| Índice | `A1…An` para aproximación; `1…n` para efectivas; `S1 L/R` si unilateral |
| Carga | `weight` / `suggestedLoad` / `—` |
| Reps o dosis | `reps`, rango, tiempo, distancia o cantidad |
| Esfuerzo | `RIR`, `RPE`, `Fallo` o `—` |
| Estado | hecha, en curso, pendiente, omitida, fallida, PR |
| Técnica | normal, drop, rest-pause, myo, volumen reemplazado |
| Media | número de fotos/vídeos y estado de análisis |
| Molestia | reportada en la serie o en el feedback |

### 1.3 Estructura

| Zona | Contenido |
|---|---|
| Cabecera del ejercicio | Nombre + etiqueta principal + `kg · N series` + `⋯` (abre `ExerciseOptionsSheet`) |
| Filas | 5 columnas: índice (22), ejercicio/dosis (1fr), carga × reps (68), esfuerzo (40), estado (14) |
| Fila activa | Fondo tinta 5,5 %, borde izquierdo de 2 dp, texto en tinta |
| Editor en línea | Bajo la fila activa: carga grande, ±2,5, RIR 0–4, `e1RM`, acciones |
| Acciones de fila | Las mismas del pie de la tarjeta: `Técnica`, `ROM`, `Molestia`, `Nota`, `Vídeo`, `Foto`, `Hist.` |
| Expansión de media | Switch Foto/Vídeo + ficha + álbum + miniaturas (la cara de media reducida) |
| Expansión de técnica | Segmentos + cadena (la cara de técnica reducida) |
| Molestia | Banda con la molestia reportada y acceso a la ficha |
| Pie | `Registrar serie` / `Actualizar serie` y raíl de pasos |

### 1.4 Lo que la mesa no puede hacer

- Añadir una acción que no exista en la tarjeta o en `ExerciseOptionsSheet`.
- Cambiar el orden de una serie hecha.
- Borrar sin confirmación o sin deshacer.
- Inventar la etiqueta de un ejercicio sin etiqueta.
- Mostrar un `e1RM` que no venga de `StrengthModel`.
- Convertirse en la pantalla principal.
- Duplicar la lógica de validación: usa la misma que la tarjeta.

### 1.5 Por qué es aprobable

- Reutiliza `CompletedSet`, `activeMainTags`, `ExercisesOptionsSheet` y las caras existentes.
- No necesita migración de Room.
- No necesita nuevos endpoints.
- Se puede apagar con un flag y comparar contra la tarjeta.

## 2. Contrato de implementación

### 2.1 Archivos y responsabilidades

| Pieza | Archivo existente | Cambio |
|---|---|---|
| Tokens y geometría | `components/WorkoutUiTokens.kt`, `ui/design/KpknSurface.kt`, `KpknType.kt` | Sin cambios; se usan. |
| Marco de tarjeta | `LiveSessionCardFrame.kt` | Sin cambios. |
| Cabecera | `WorkoutHeaderBar.kt` | Sin cambios; se retira cualquier ‹ inventada. |
| Raíl | `LiveSessionLine.kt`, `LiveSessionLineSteps.kt` | Sin cambios; se documenta la compresión >9. |
| Tarjeta frontal | `components/SetExecutionCard.kt`, `components/LiveModeSetContent.kt` | Ajustes puntuales; no reescribir. |
| Técnica | `components/SetCardTechniqueBack.kt` | Sin cambios. |
| Media | `components/SetCardExerciseMediaBack.kt` | Sin cambios. |
| Preparación | `components/WarmupMobilityLiveCards.kt`, `CardioLiveCard.kt` | Sin cambios. |
| Descanso | `components/RestLiveCard.kt`, `RestOverrunLayer.kt`, `RestMiniChip.kt`, `RestMiniGame.kt` | Sin cambios. |
| Feedback | `WorkoutPostExerciseFeedbackHost.kt`, `RestFeedbackCard.kt`, `FeedbackTechnique.kt`, `FeedbackDiscomforts.kt` | Sin cambios. |
| Hojas | `LiveSessionSheets.kt`, `WorkoutStructureSheetsHost.kt`, `WorkoutOverlayHost.kt` | Sin cambios. |
| Mesa | **nuevo** `components/LiveSessionTable.kt` + `WorkoutTableModels.kt` | Solo lectura + edición que delega en los controladores existentes. |
| Flag | `Settings` / feature flag existente | `liveTableEnabled = false` por defecto. |

La mesa es el único archivo nuevo de la entrega. Todo lo demás son ajustes o documentación.

### 2.2 Estado

- La sesión vive en `WorkoutViewModel` y `ActiveWorkoutHolder`; la mesa **no** crea estado de sesión.
- El editor en línea reutiliza `CompletedSetPatch` y `WorkoutEditingRules`.
- Las acciones de ejercicio delegan en `ExerciseOptionsSheet`.
- La media delega en `WorkoutMediaCaptureController`; la mesa no abre cámara propia.
- El descanso no se toca desde la mesa: se sigue gobernando por `RestTimerController`.

### 2.3 Datos

| Dato | Fuente única | Regla |
|---|---|---|
| e1RM | `StrengthModel.estimate1RM` | 0,1 kg; sin dato, `—`. |
| PR | `LiveSetPrPolicy` | Solo si el historial lo confirma. |
| Volumen | `CockpitSessionMetrics` | Excluye warmup, omitidas y fallidas del volumen normal; el tonelaje suma las fallidas. |
| RIR medio | `CockpitSessionMetrics` | Fallo = 0; RIR; RPE = 10 − RIR. |
| Etiqueta | `WorkoutTagDisplay` | Vacía = sin etiqueta; nunca «Default» visible. |
| Descanso | `RestNextSet` | Sale del cursor real, no de una copia. |

### 2.4 Pruebas

| Nivel | Qué se prueba |
|---|---|
| Unidad | `StrengthModel`, `LoadSuggestionEngine`, `EffortScale`, `RestClockMath`, `RestNextSet`, `CompletedSetPatch`, `WorkoutEditingRules`. |
| Unidad de mesa | Fila = `CompletedSet`; sin series inventadas; acciones = lista permitida. |
| Compose UI | Zona táctil ≥48; tarjeta 470; cabecera 119/95; raíl 112; paso activo centrado; sin botón atrás. |
| Instrumentación | Registrar, actualizar, saltar, omitir, undo, descanso, tiempo extra, hoja de salir. |
| Regresión visual | Captura de las 8 escenas clave (REPS, AMRAP, fallo, técnica, media, movilidad, descanso, mesa). |

No hay infraestructura de screenshot tests en el repo; la regresión visual se hace con capturas manuales en el emulador y se adjunta al cierre.

### 2.5 Plan de entrada

| Paso | Contenido | Criterio de salida |
|---|---|---|
| P0 | Flags y tags de prueba; ninguna UI nueva | Build verde |
| P1 | Tokens y geometría: confirmar 470/119/112 | Tests de tamaño |
| P2 | Tarjeta frontal: 8 modos y estados | Tests de modo y validación |
| P3 | Caras técnica y media | Tests de borrador y captura |
| P4 | Preparación | Tests de escalera y movilidad |
| P5 | Descanso y feedback | Tests de reloj, overrun y feedback |
| P6 | Hojas | Tests de cierre, confirmación y persistencia |
| P7 | Mesa tras flag | Tests de fila y paridad de acciones |

Cada paso se puede revertir por separado. La mesa es el último y no bloquea a los anteriores.

## 3. Definición de «hecho»

- [ ] Las 8 fases están aprobadas por el dueño.
- [ ] El prototipo no tiene ninguna escena sin insignia de fidelidad.
- [ ] Toda brecha tiene sustituto real documentado.
- [ ] La mesa reutiliza datos y acciones; no añade ninguna.
- [ ] Los tests de unidad y Compose pasan.
- [ ] La build `assembleDebug` pasa.
- [ ] La instalación en KPKN Dev conserva datos y no cambia la identidad.
- [ ] Se probó en 393 × 852 y en 360 × 740 con letra al 130 %.
- [ ] Se probó con movimiento reducido.
- [ ] El registro no duplica series y el undo no aparece si lo registrado cambió.

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| La tarjeta se alarga al añadir un aviso | Layout fijo de 470; avisos superpuestos. |
| La mesa duplica la lógica de la tarjeta | Delegar en los controladores y en `CompletedSetPatch`. |
| El modo dios se cuela como pantalla | Está marcado como brecha; sus acciones viven en `SeriesOptionsSheet`. |
| El relator no cabe al crecer la letra | Banda reservada; si no cabe, se recorta el texto, no la tarjeta. |
| El descanso adaptativo parece un dato inventado | Se etiqueta `· sugerido` y se explica en la hoja. |
| Las etiquetas sub-tag saturan la cabecera | Límite visible y scroll; se decide en §6. |
| La mesa cambia el orden de lo hecho | Filas hechas bloqueadas. |

## 5. Lo que no entra

- Nuevos ejercicios, protocolos o datos.
- Cambios de Room.
- Cambios de backend.
- Un rediseño de Home, Nutrición o Cuerpo.
- Un nuevo sistema de iconos.
- Animaciones decorativas.
- Un modo dios real.

## 6. Decisiones que necesita el dueño

1. **Mesa**: ¿entra en esta entrega o en una segunda?
2. **Relator**: ¿se queda con 107 dp o se recoloca al aprobar la mesa?
3. **Modo dios**: ¿se retira del todo o se implementa de verdad?
4. **Cluster set**: ¿se oculta el «próximamente» hasta que exista?
5. **Sub-etiquetas**: ¿límite de 3 visibles + «+N»?
6. **Descanso adaptativo**: ¿una línea o una hoja explicativa?
7. **Cardio**: ¿el objetivo de 20 min por defecto se muestra como plan o se oculta?
8. **Mesa y tarjeta**: ¿qué vista es la predeterminada al abrir una sesión?

## 7. Cierre

La Fase 0 dijo de dónde sale cada cosa. Las fases 1–7 dicen cómo se ve y cómo se comporta. La Fase 8 dice cómo entra sin romper lo que ya funciona.

Si las ocho fases se aprueban, la implementación no empieza con una pregunta de diseño: empieza con P0 y un flag apagado.
