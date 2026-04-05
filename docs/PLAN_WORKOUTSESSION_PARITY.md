# PLAN_WORKOUTSESSION_PARITY.md

## Objetivo

Completar la migración del componente web `WorkoutSession.tsx` hacia la app Kotlin/Android, priorizando:

1. Paridad funcional crítica.
2. Recuperación de la capa "coach" / inteligencia de sesión.
3. Pulido UX dentro del lenguaje visual actual de Kotlin.

Este plan está pensado para que otro agente pueda ejecutarlo de forma incremental, verificable y sin reinterpretar la auditoría desde cero.

---

## Estado actual

### Fuente web auditada

- `components/WorkoutSession.tsx`

### Implementación Kotlin actual

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutFeedbackModels.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/auge/ReadinessSheet.kt`

### Resumen de migración

La sesión de entrenamiento en Kotlin ya tiene:

- Persistencia y resume de workout en curso.
- Readiness sheet al inicio.
- Timer de descanso con alertas.
- Flujo básico de sets.
- Reordenar / reemplazar / omitir ejercicios.
- Feedback post-ejercicio.
- Hoja de fin de sesión.
- Compartir a Instagram Stories.
- Soporte básico de superseries.

Pero todavía le falta una parte importante de la profundidad del componente web:

- Unilateral real.
- Tags/setup en sesión.
- Ghost/history avanzado.
- Sugerencia de carga contextual.
- Descanso adaptativo basado en ejecución real.
- Detección de desviaciones del plan.
- Finish flow más rico.
- Warm-up inteligente.

---

## Criterios de éxito

Se considera completada la migración cuando:

1. Un ejercicio unilateral se puede registrar por lado y se exporta correctamente al `WorkoutLog`.
2. La sesión permite elegir contexto/tags de máquina o variante y usarlo para sugerencia/ghost/history.
3. El tiempo de descanso deja de ser estático y responde al desempeño real.
4. La hoja final captura suficiente contexto como para alimentar AUGE y análisis post-sesión.
5. La UX de workout en Kotlin se siente equivalente a la PWA en utilidad, aunque no tenga el mismo look exacto.
6. `:app:compileDebugKotlin` pasa al final de cada tanda.

---

## No objetivos

No hacer en esta tanda:

- Rediseño visual agresivo del workout Kotlin.
- Reproducir 1:1 toda la estética liquid glass de la PWA.
- Meter features nuevas no presentes en la web.
- Reescribir por completo el modelo de `WorkoutLog` si se puede extender incrementalmente.

---

## Paridad auditada

### Sí migrado

- Resume / persistencia de sesión.
- Navegación básica por ejercicios y sets.
- Rest timer.
- Warm-up drawer básico.
- Feedback post-ejercicio.
- Finish sheet básica.
- Compartir story.
- Reemplazo / reordenamiento / skip.
- Soporte básico de supersets.

### Parcial o faltante

- Unilateral.
- Tags por ejercicio.
- Setup accordion en sesión.
- PR/history modal.
- Ghost avanzado.
- Sugerencia de peso basada en historial/tag/1RM.
- Rest adaptativo.
- Detección de plan deviation.
- Finish context más rico.
- Warm-up con peso base sugerido.

---

## Estrategia general

Implementar en 3 tandas:

1. `Paridad crítica`
2. `Coach layer`
3. `Pulido UX`

Cada tanda debe:

- Mantener compatibilidad con los datos actuales.
- Evitar refactors arquitectónicos innecesarios.
- Dejar compile verde.
- Incluir verificación manual mínima.

---

# TANDA 1 — Paridad crítica

## Objetivo

Recuperar las capacidades sin las cuales el workout Kotlin pierde datos o contexto importante respecto a la PWA.

## Alcance

### 1. Unilateral real

#### Problema

La web maneja sets unilaterales con inputs `left/right` y los exporta como sets separados. Kotlin hoy registra un solo set por fila.

#### Archivos objetivo

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutFeedbackModels.kt`
- revisar también:
  - `android-native/app/src/main/java/com/example/kpkn/data/models/WorkoutLog.kt`
  - `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt`

#### Implementación propuesta

- Extender el estado de input de set en Kotlin para soportar:
  - bilateral simple
  - unilateral `left/right`
- Permitir en `SetInputCard`:
  - peso/reps/RPE por lado
  - usar ghost por lado si existe
- Al registrar:
  - si el ejercicio es unilateral, persistir ambos lados
  - si solo uno fue completado, decidir política explícita:
    - recomendado: permitir guardado parcial si al menos un lado tiene datos válidos
- Al exportar al log:
  - generar `CompletedSet` por lado, igual que la PWA

#### Criterios de aceptación

- Un ejercicio unilateral muestra campos de lado izquierdo y derecho.
- El workout puede continuar normalmente después de registrar un set unilateral.
- El `WorkoutLog` final contiene ambos lados como sets separados.

---

### 2. Tags y setup por ejercicio dentro de la sesión

#### Problema

La web permite elegir etiqueta/contexto (`Base`, máquina, sentado, parado, unilateral, etc.) y editar `setupDetails`. Kotlin hoy conserva parte de esos datos al reemplazar un ejercicio, pero no expone UI de sesión para trabajarlos.

#### Archivos objetivo

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt`
- potencial helper nuevo:
  - `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSessionSupport.kt`

#### Implementación propuesta

- Añadir un bloque/accordion compacto por ejercicio activo con:
  - `Tag actual`
  - selector de tags existentes
  - crear tag local si no existe
  - lectura/edición básica de:
    - `seatPosition`
    - `pinPosition`
    - `equipmentNotes`
- Persistir el tag seleccionado dentro del workout en curso.
- Reutilizar `setupCues` y `executionCues` cuando existan.

#### Requisitos UX

- No abrir un sistema nuevo gigante.
- Mantenerlo como sheet/accordion compacto.
- Debe sentirse útil, no recargado.

#### Criterios de aceptación

- Se puede asignar un tag a un ejercicio durante la sesión.
- El tag permanece si la app se cierra y reabre la sesión.
- Se pueden leer o editar setup details básicos.

---

### 3. Ghost/history más útil

#### Problema

Kotlin hoy solo muestra “Última vez”. La PWA usa ghost + mejor PR/history context.

#### Archivos objetivo

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt`
- revisar:
  - `android-native/app/src/main/java/com/example/kpkn/data/repository/ProgramRepository.kt`

#### Implementación propuesta

- Mejorar `getGhostForSet()` para:
  - usar `exerciseDbId` además de `exerciseId`
  - considerar tag/contexto si ya existe en la tanda 1
- Añadir una sheet/modal compacta de historial:
  - últimas N sesiones de ese ejercicio
  - peso x reps
  - e1RM estimado si aplica
- Mostrar PR contextual pequeño en el card del ejercicio/set cuando sea posible

#### Criterios de aceptación

- El ghost deja de depender solo del mismo `exerciseId`.
- El usuario puede abrir historial del ejercicio.
- La UI muestra mejor referencia de rendimiento anterior.

---

## Verificación de la Tanda 1

- `./gradlew.bat :app:compileDebugKotlin`
- Prueba manual:
  - abrir sesión con ejercicio bilateral
  - abrir sesión con ejercicio unilateral
  - editar tag/setup
  - cerrar y reabrir sesión
  - finalizar workout y revisar log generado

---

# TANDA 2 — Coach layer

## Objetivo

Recuperar la inteligencia de sesión que en la PWA adapta peso, descanso y análisis según la ejecución real.

## Alcance

### 1. Sugerencia de carga contextual

#### Problema

La web usa `getWeightSuggestionForSet()` con historial, tag y 1RM. Kotlin hoy solo tiene ghost y edición manual.

#### Archivos objetivo

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt`
- revisar utilidades de cálculo ya existentes en Kotlin

#### Implementación propuesta

- Crear helper Kotlin equivalente a la heurística usada en web:
  - inputs:
    - set actual
    - historial del ejercicio
    - tag seleccionado
    - 1RM base / calculado
  - outputs:
    - peso sugerido
    - razón breve de la sugerencia
- Mostrar sugerencia en el set card:
  - como hint, nunca como overwrite automático

#### Criterios de aceptación

- El usuario ve un peso sugerido por set.
- La sugerencia responde al historial y al contexto/tag.

---

### 2. Descanso adaptativo

#### Problema

La PWA ajusta descanso según fatiga, fallos, dropsets, rest-pause y desvíos. Kotlin usa solo `restTime`.

#### Archivos objetivo

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt`
- helper recomendado nuevo:
  - `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutAdaptiveRest.kt`

#### Implementación propuesta

- Calcular `adaptiveRestTime` usando:
  - AUGE del ejercicio
  - si hubo fallo
  - si hubo partials
  - si hubo rest-pause
  - si hubo dropset
  - si el set se alejó del objetivo programado
- Mantener reglas simples y explícitas.
- No sobreajustar al inicio; priorizar robustez.

#### Criterios de aceptación

- Un set “más caro” produce más descanso que uno normal.
- Un cambio de compañero en superset sigue evitando descanso intermedio cuando corresponde.

---

### 3. Detección de desviaciones del plan

#### Problema

La PWA registra desvíos del plan. Kotlin hoy no conserva esa lectura.

#### Archivos objetivo

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/models/WorkoutLog.kt`
- helper nuevo recomendado:
  - `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutPlanDeviationSupport.kt`

#### Implementación propuesta

- Definir desvíos detectables:
  - peso muy por encima/por debajo del sugerido
  - set al fallo no programado
  - dropset no programado
  - rest-pause no programado
  - reps muy alejadas del target
- Guardarlos en el workout final.

#### Criterios de aceptación

- El log final expone una lista razonable de deviations.
- Esas deviations pueden alimentar análisis posteriores.

---

### 4. Finish sheet enriquecida

#### Problema

La hoja de fin de Kotlin ya mejoró, pero sigue por debajo de la PWA.

#### Archivos objetivo

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutFeedbackModels.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt`

#### Implementación propuesta

- Añadir, al menos:
  - claridad / frescura mental
  - adherence/deviation resumen
  - tags de entorno opcionales
  - baterías musculares relevantes si ya hay datos
- Mantener el finish sheet corto, pero más útil.

#### Criterios de aceptación

- El cierre recoge suficiente contexto para análisis post-sesión.
- No se vuelve una pantalla interminable.

---

## Verificación de la Tanda 2

- `./gradlew.bat :app:compileDebugKotlin`
- Prueba manual:
  - registrar set normal
  - registrar fallo
  - registrar dropset
  - registrar rest-pause
  - verificar que el descanso cambie
  - finalizar sesión y comprobar deviations en el log

---

# TANDA 3 — Pulido UX

## Objetivo

Hacer que la experiencia Kotlin se sienta tan útil como la PWA, sin copiarla visualmente ni romper el lenguaje de la app.

## Alcance

### 1. Warm-up inteligente

#### Problema

La web calcula warmups según peso base/sugerido. Kotlin hoy solo muestra porcentaje y reps.

#### Archivos objetivo

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt`

#### Implementación propuesta

- Calcular peso base para warm-up a partir de:
  - peso sugerido del primer set efectivo
  - o peso consolidado/manual si existe
- Mostrar:
  - porcentaje
  - reps
  - kg sugeridos

#### Criterios de aceptación

- El warm-up sheet muestra kilos útiles, no solo porcentajes.

---

### 2. Context menu / quick actions mejores

#### Problema

Kotlin tiene quick actions funcionales, pero más toscas que la web.

#### Implementación propuesta

- Consolidar acciones por ejercicio en una hoja más clara:
  - mover arriba
  - mover abajo
  - reemplazar
  - omitir
  - ver historial
  - ver setup/tag
- Mantener una sola entrada contextual, no múltiples puntos dispersos.

---

### 3. Header y dock inferior

#### Problema

La PWA tiene un shell más afinado para workout. Kotlin cumple, pero todavía no transmite la misma jerarquía.

#### Implementación propuesta

- Mantener estructura actual.
- Mejorar:
  - énfasis del ejercicio activo
  - claridad del progreso
  - accesos rápidos más relevantes
  - feedback visual de descanso y transición

#### No hacer

- No reconstruir todo el shell de workout desde cero.

---

### 4. Historial y PR context

#### Implementación propuesta

- Añadir CTA visible pero discreto a historial.
- Mostrar PR reciente/contextual donde sume:
  - en header del ejercicio
  - o arriba del set card

---

## Verificación de la Tanda 3

- `./gradlew.bat :app:compileDebugKotlin`
- Revisión manual visual en emulador:
  - header
  - transición entre ejercicios
  - warm-up
  - feedback post-ejercicio
  - finish sheet

---

# Orden recomendado de implementación

## Opción recomendada

1. Unilateral real.
2. Tags/setup.
3. Ghost/history.
4. Sugerencia de carga.
5. Descanso adaptativo.
6. Plan deviations.
7. Finish sheet enriquecida.
8. Warm-up inteligente.
9. Pulido UX final.

## Por qué este orden

- Primero se protege integridad de datos.
- Después se recupera la capa coach.
- Al final se pule UX con mejor información ya disponible.

---

# Riesgos

## Riesgo 1: romper `WorkoutLog`

### Mitigación

- Extender de forma backward-compatible.
- Evitar cambiar nombres existentes si no es imprescindible.

## Riesgo 2: sobrecargar la UI

### Mitigación

- Usar drawers/sheets/accordions compactos.
- No intentar mostrar todo siempre.

## Riesgo 3: heurísticas demasiado agresivas

### Mitigación

- Mantener descansos adaptativos con caps mínimos/máximos.
- Mostrar sugerencias como ayuda, no como automatismo duro.

## Riesgo 4: conflicto con AUGE

### Mitigación

- Reutilizar motores existentes.
- No duplicar cálculos de fatiga inline si ya existe una fuente de verdad.

---

# Checklist de ejecución

## Antes de empezar

- [ ] Leer `components/WorkoutSession.tsx`
- [ ] Leer `WorkoutScreen.kt`
- [ ] Leer `WorkoutViewModel.kt`
- [ ] Confirmar que `:app:compileDebugKotlin` pasa en baseline

## Tanda 1

- [ ] Implementar unilateral
- [ ] Implementar tags/setup
- [ ] Mejorar ghost/history
- [ ] Compilar
- [ ] Probar manualmente

## Tanda 2

- [ ] Implementar sugerencia de carga
- [ ] Implementar descanso adaptativo
- [ ] Implementar deviations
- [ ] Enriquecer finish sheet
- [ ] Compilar
- [ ] Probar manualmente

## Tanda 3

- [ ] Mejorar warm-up
- [ ] Afinar quick actions
- [ ] Afinar header/dock
- [ ] Afinar PR/history context
- [ ] Compilar
- [ ] Revisar visualmente

---

# Comandos de verificación

## Compilación

```powershell
cd android-native
.\gradlew.bat :app:compileDebugKotlin
```

## Tests

```powershell
cd android-native
.\gradlew.bat :app:testDebugUnitTest
```

Nota: al momento de redactar este plan, los tests unitarios del módulo siguen fallando por problemas previos ajenos al workout. No usar ese fallo como señal automática de regresión de esta migración sin revisar el detalle.

---

# Definición de terminado

La migración del `WorkoutSession` puede considerarse cerrada cuando:

- El workout Kotlin deja de perder capacidades críticas respecto a la web.
- El usuario puede entrenar una sesión compleja sin sentir que la app nativa es una versión reducida.
- La lógica AUGE y de coaching vuelve a influir de forma visible y útil.
- La UI sigue sintiéndose Kotlin nativa, no una copia forzada de la PWA.

