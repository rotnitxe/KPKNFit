# Preguntas para el Rediseño WorkoutSession XL

Documento de preguntas para aclarar requisitos antes de implementar. Responde por bloques o por pregunta según prefieras.

---

## BLOQUE 1: Estética y Consistencia Visual

### 1.1 Referencia visual
- ¿Qué apps o interfaces usas como referencia de "profesional"? (ej: Strong, Hevy, TrainHeroic, Apple Fitness+, Whoop, etc.)
- ¿Prefieres mantener el modo oscuro actual o considerar modo claro como opción?
- ¿El estilo debe alinearse con el nuevo SessionEditor (Notion-like, bordes sutiles) o quieres una identidad visual distinta para la sesión en vivo?

### 1.2 Densidad de información
- ¿Prefieres una vista más "zen" (menos elementos visibles, más espacio) o una vista "densa" que muestre más datos sin scroll?
- ¿Los KPIs (1RM estimado, RPE sugerido, tiempo de descanso) deben estar siempre visibles o solo al expandir/focus?

### 1.3 Cards y contenedores
- ¿Las tarjetas de serie actuales (slate-900, bordes) te molestan por el exceso de "card"? ¿Prefieres un diseño más plano tipo spreadsheet/fila?
- ¿El WarmupDashboard y el InlineFeedbackCard deben seguir siendo modales/cards deslizables o integrarse inline en el flujo?

---

## BLOQUE 2: Estabilidad y Bugs

### 2.1 Pantallas negras / congelamiento
- ¿En qué momentos concretos ocurre? (al iniciar sesión, al guardar serie, al cambiar de ejercicio, al abrir modal, etc.)
- ¿Es más frecuente en móvil o desktop?
- ¿Hay algún patrón (ej: siempre al terminar el primer ejercicio, al usar el temporizador, etc.)?

### 2.2 Recuperación
- Si la app se congela a mitad de sesión, ¿qué comportamiento esperas? (auto-guardado en background, botón "Recuperar sesión", toast de "Sesión guardada automáticamente", etc.)

---

## BLOQUE 3: Animaciones (Records, Cargas Sugeridas)

### 3.1 Nuevo récord / meta alcanzada
- El overlay actual (GoalProgressOverlay) dura ~5.5s y tiene barra de progreso. ¿Te parece bien o prefieres algo más breve/discreto?
- ¿Quieres animaciones distintas para: (a) PR absoluto, (b) meta de 1RM alcanzada, (c) mejora respecto a la sesión anterior?
- ¿Debe haber sonido/haptic en todos los casos o solo en PR absoluto?

### 3.2 Cargas sugeridas
- Cuando se sugiere un peso, ¿prefieres que aparezca como: (a) badge estático "Sugerido: X kg", (b) input pre-rellenado editable, (c) botón "Usar sugerencia" que rellena el input?
- ¿La sugerencia debe poder aplicarse con un solo tap o siempre requiere edición manual?

### 3.3 Feedback visual al guardar serie
- Las animaciones actuales son: success (azul), amrap (amarillo), failure (rojo). ¿Quieres mantenerlas, simplificarlas o ampliarlas (ej: confetti sutil, micro-animación de check)?

---

## BLOQUE 4: Repeticiones Completas vs Parciales

### 4.1 Selector actual
- El toggle "Reps Reales" / "Parciales" ¿te resulta confuso? ¿Prefieres otro nombre? (ej: "Completas" vs "Parciales", o "ROM completo" vs "ROM parcial")
- ¿Debe ser posible tener AMBAS en la misma serie? (ej: 8 completas + 2 parciales como repeticiones negativas)

### 4.2 Influencia de las parciales
- Hoy `partialReps` se guarda pero no afecta el cálculo de 1RM ni el volumen efectivo. ¿Qué debería hacer?
  - (a) Descontar del volumen efectivo (ej: 1 parcial = 0.5 reps efectivas)
  - (b) Influir en el 1RM estimado (con factor de corrección)
  - (c) Solo para registro/historial, sin impacto en métricas
  - (d) Otra lógica (especificar)

### 4.3 Tipos de parciales
- ¿Necesitas distinguir tipos? (ej: negativas, parciales altas, parciales bajas, quarter reps)
- ¿O basta con "X reps parciales" como número?

---

## BLOQUE 5: Dropsets y Otras Técnicas de Intensidad

### 5.1 UI actual
- Hoy `dropSets` y `restPauses` existen en el modelo pero NO hay UI visible para añadirlos durante la sesión. ¿Confirmas que quieres poder reportarlos en vivo?

### 5.2 Dropsets
- ¿Cómo quieres reportar un dropset? Opciones:
  - (a) Botón "+ Dropset" que añade una fila extra (peso + reps) debajo de la serie principal
  - (b) Modal/drawer al guardar: "¿Añadiste dropset?" con inputs
  - (c) Toggle en la serie que expande campos para N dropsets
- ¿Múltiples dropsets por serie? (ej: 100kg x 8 → 80kg x 6 → 60kg x 8)

### 5.3 Rest-pause
- ¿Mismo patrón que dropsets? (restTime + reps por cada "mini-serie")
- ¿Cluster sets (varias mini-series con descansos cortos) como variante de rest-pause?

### 5.4 Otras técnicas
- ¿Qué más quieres reportar? (Myo-reps, strip sets, tempo 3-1-2-0, etc.)
- ¿Prefieres un selector de "Técnica avanzada" con lista predefinida o campos libres?

---

## BLOQUE 6: Modales "de juguete"

### 6.1 Modales actuales
- Los modales que identifico: FinishWorkoutModal, ExerciseHistoryModal, FailedSet modal, WarmupDashboard (card), InlineFeedbackCard, GoalProgressOverlay, posiblemente más. ¿Cuáles te molestan más?

### 6.2 Dirección de diseño
- ¿Prefieres convertir modales a drawers (como en SessionEditor) para mantener contexto?
- ¿O modales más grandes pero con mejor jerarquía visual (headers claros, secciones, menos "card stacking")?

### 6.3 FinishWorkoutModal (último modal)
- Mencionas que está "desordenado". ¿Qué te molesta concretamente?
  - Orden de los sliders (RPE, Fatiga, Pump, Claridad)
  - Los tags (Entorno, Adherencia, Alertas Caupolicán)
  - La duración/fecha
  - El flujo de "Recuperación prioritaria" cuando fatiga alta
  - La tarjeta de compartir oculta
- ¿Quieres un flujo por pasos (Paso 1: Resumen → Paso 2: Feedback → Paso 3: Tags) o todo en una sola vista mejor organizada?

---

## BLOQUE 7: Cargas Sugeridas y Matemática Avanzada

### 7.1 Lógica actual
- Hoy `getWeightSuggestionForSet` usa: último peso del set 0, peso del set anterior, o 1RM × % según reps+RPE. ¿Qué te falla?
  - ¿Las sugerencias son muy conservadoras o muy agresivas?
  - ¿No considera suficiente historial (patrón de movimiento, tendencia)?

### 7.2 Matemática más compleja
- ¿Qué factores quieres que influyan?
  - (a) Historial de las últimas N sesiones del mismo ejercicio
  - (b) Tendencia (mejorando/estancado/empeorando)
  - (c) Fatiga acumulada de la sesión (sets previos del mismo músculo)
  - (d) Día de la semana / frecuencia del patrón
  - (e) RPE reportado vs RPE objetivo (si siempre reportas RPE 9, subir carga)
  - (f) Otros (especificar)

### 7.3 Python
- Mencionas "integrar Python si es necesario". ¿Hay restricciones? (ej: solo backend, no en cliente)
- ¿Prefieres un modelo predictivo (ML) o fórmulas deterministas mejoradas (Brzycki + factores de corrección)?
- ¿El backend actual (si existe) puede ejecutar scripts Python o habría que añadir un servicio?

### 7.4 Auto-completado
- ¿Los inputs de peso/reps deben pre-rellenarse siempre que haya sugerencia, o solo la primera vez por ejercicio/set?
- ¿Al cambiar de ejercicio, el peso sugerido debe reemplazar automáticamente el input o solo mostrarse como hint?

---

## BLOQUE 8: Descanso Sugerido

### 8.1 Comportamiento actual
- El descanso se calcula: `exercise.restTime` (base) + `addedRest` según RPE efectivo (fallo, dropsets, rest-pause, parciales). Es bastante lineal (exceso RPE × 25s × factor).

### 8.2 "Humano y no lineal"
- ¿Qué significa para ti? Por ejemplo:
  - (a) Curva: poco exceso → poco extra; mucho exceso → mucho extra (no proporcional lineal)
  - (b) Escalones: 0-30s extra, 30-60s, 60-90s según "bandas" de intensidad
  - (c) Considerar tipo de ejercicio (compuesto vs aislado, espinal vs no)
  - (d) Considerar sets acumulados en el mismo músculo
  - (e) "Sensación" de recuperación: si el usuario suele saltarse el descanso, bajar sugerencia
- ¿El descanso debe ser editable siempre (el usuario puede poner 60s aunque se sugieran 90s)?

### 8.3 Temporizador
- ¿El temporizador actual (InCardTimer, SetTimerButton, restTimer global) te parece bien ubicado y funcional?
- ¿Quieres un temporizador más prominente (ej: barra fija inferior que no se pierde al scroll)?

---

## BLOQUE 9: Series de Aproximación (Warmup)

### 9.1 Gestión actual
- WarmupDashboard muestra series con % del peso de trabajo y reps. El usuario marca check al completar. ¿Qué mejorar?

### 9.2 Flujo
- ¿Las series de aproximación deben bloquear el paso a la serie de trabajo hasta marcarlas, o ser opcionales?
- ¿Quieres poder editar el peso de cada serie de aproximación en vivo (hoy se calcula por %) o está bien automático?

### 9.3 Generación
- ¿Las series de aproximación se generan en SessionEditor (ya existe) o también quieres un "generador rápido" durante la sesión? (ej: "3 series: 50%, 70%, 85%")

---

## BLOQUE 10: AMRAP y AMRAP Calibrador

### 10.1 AMRAP aislado vs calibrador
- Hoy: AMRAP Aislado = serie al fallo sin impacto; AMRAP Calibrador = la IA ajusta pesos de ejercicios siguientes del mismo músculo según el rendimiento.
- ¿El flujo de "elegir al activar AMRAP" (modal con dos opciones) te parece bien o prefieres que sea un toggle en la serie sin modal?

### 10.2 Potencial del calibrador
- ¿Qué quieres que haga el calibrador exactamente?
  - (a) Ajustar peso de los siguientes sets del mismo ejercicio
  - (b) Ajustar peso de ejercicios del mismo grupo muscular en la sesión
  - (c) Actualizar el 1RM almacenado del ejercicio
  - (d) Sugerir cambios en el programa (próxima semana)
  - (e) Combinación de varias
- ¿El ajuste debe ser automático (aplicar sin preguntar) o con confirmación ("Sugerimos subir Bench a 85kg. ¿Aplicar?")?

### 10.3 Validación AMRAP
- Si el usuario pone menos reps que el mínimo (ej: target 8+, pone 6), hoy hay un warning "¿Fallo anticipado?". ¿Mantener, quitar o cambiar el mensaje?

---

## BLOQUE 11: Layout y Navegación

### 11.1 Estructura actual
- Ejercicios en parts, scroll horizontal de sets, card de set actual centrada. ¿Prefieres:
  - (a) Mantener scroll horizontal de sets
  - (b) Lista vertical de sets (como SessionEditor) con el set activo expandido
  - (c) Vista tipo "wizard" (un set a la vez, flechas para siguiente/anterior)

### 11.2 Header
- ¿Header fijo con nombre de sesión, tiempo transcurrido, botón pausar/finalizar?
- ¿Debe compactarse al scroll (como ContextualHeader del SessionEditor)?

### 11.3 Navegación entre ejercicios
- ¿Swipe entre ejercicios, lista lateral, o solo scroll vertical?

---

## BLOQUE 12: Otros

### 12.1 LogWorkoutView
- La vista de "registrar entrenamiento" (log manual, no en vivo) ¿debe rediseñarse en paralelo para consistencia o es prioridad menor?

### 12.2 Variantes de sesión (A/B/C/D)
- Para sesiones con variantes, el selector actual ¿te parece claro? ¿Algún cambio?

### 12.3 Ejercicios de competición (luces de juez)
- El bloque de "Luces de Jueceo" para powerlifting, ¿se usa? ¿Mantener, mejorar o simplificar?

### 12.4 Prioridad de implementación
- Si hubiera que hacer el rediseño en fases, ¿cuál sería tu orden?
  1. Estabilidad y bugs
  2. Estética y consistencia
  3. Matemática de cargas y descanso
  4. Parciales, dropsets, técnicas
  5. AMRAP Calibrador
  6. Modales → Drawers
  7. FinishWorkoutModal
  8. Otro orden (especificar)

---

Responde por bloques o por número de pregunta. Con tus respuestas se armará un plan de implementación detallado.
