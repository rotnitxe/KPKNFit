# N2b · Check-in semanal, aplicar objetivo y sustitución del motor de calibración

**Prioridad P0 · esfuerzo M-L.** Informe §4.1 (G1–G11), §4.3 (C1–C6), §5.10 («Recomendación»: reglas propias y simples). Depende de **N1** (estimador) y **N2a** (marcas de día y tablas `nutrition_day_marks` / `nutrition_checkins`, ya integradas).

## Por qué
La calibración actual (`NutritionCalibrationEngine`: `-cambioSemanal × 7700 / 7`, tope ±150) es inestable, no usa la ingesta, no distingue días completos y su pantalla solo se abre por enlace profundo (`kpkn://nutrition/calibration`); su propuesta se muestra y **nunca se aplica**. Hay que conectarla de verdad: un **check-in semanal** que usa el estimador de N1, explica la propuesta, y la aplica con confirmación, de forma idempotente y sin tocar el pasado.

## Decisiones ya tomadas (README de la ola)
- Propone y el usuario **confirma** (aceptar / declinar / posponer). Nunca se aplica solo.
- Día del check-in por defecto = **inicio de semana del usuario** (usa lo que KPKN ya tenga como inicio de semana: busca `weekStartDay`/calendario del programa/locale y sé coherente; si no hay, el de la configuración regional); configurable. Disponible **3 días** desde ese día; «posponer» = pasa al día siguiente; **idempotente por semana** (`weekKey`).
- Orden fijo de módulos: introducción (solo la primera vez) · parón de registro · ayuno · días parciales · pesaje · actualización del objetivo. Cada módulo solo aparece si procede y tiene «No volver a mostrar» (preferencia por módulo; guárdala como campo con valor por defecto en el JSON de `Settings`, sin migración de Room).
- Dominio puro y probado; la UI delgada.

## Cambio exacto

### 1. Dominio (`domain/nutrition/checkin/`, Kotlin puro)
- `CheckinPolicy`: `state(today, weekStartDay, record: CheckinRecord?) → NOW | FUTURE | DISMISSED | NONE`, con las reglas de arriba. Sin programa/plan de nutrición activo → `NONE`. Test de tabla: cada día de la semana, ya aplicado, pospuesto, descartado, cambio de semana, cambio de zona horaria.
- `CheckinPlanner.build(estimate: ExpenditureEstimate, series, days, plan, ...) → CheckinPlan`: lista ordenada de módulos con su contenido: 
  - **Parón de registro**: ≥ 7 días sin registro (usa el estado `PAUSED/INSUFFICIENT_LOGGING` de N1) → explica la pausa y NO propone cambios; ofrece reanudar.
  - **Ayuno**: días sin comida recientes, para confirmar `FASTING` (0 kcal) o dejarlos fuera.
  - **Días parciales**: lista de `PartialDaySuggester` + `UNREVIEWED_LOW`; marcar → `PARTIAL`; rechazar → `COMPLETE_CONFIRMED`. Sin borrar comidas.
  - **Pesaje**: invita a pesarse si hay pocos pesajes; muestra la tendencia.
  - **Actualización del objetivo**: `CalorieTargetController.propose(...)` con el gasto estimado, el ritmo del plan (`NutritionPlan.weeklyChangeKg` y su signo según el objetivo `goalType`), el objetivo actual, el suelo y el tope por propuesta. Muestra antes/después, la banda de incertidumbre, días usados/excluidos con motivo (G8), y el estado (iniciando/aprendiendo/estable/pausado).
- Entradas del estimador: `initialKcal` = la EER que KPKN ya calcula para el plan activo (`NutritionEnergyEngine`; no la recalcules); `floorKcal` = suelo propio del estimador: mínimo absoluto razonable y fracción de la tasa metabólica en reposo que KPKN ya estime (elige y documenta con su razón; NO uses `0,85`, `750` ni `598`); `goalChangeDates` = fechas de cambios de objetivo conocidas (snapshots / commits previos).

### 2. Persistencia y aplicación
- `nutrition_checkins` (N2a) guarda la decisión por `weekKey`. Un `NutritionCheckinRepository` fino (propiedad nueva, `data/repository/`) lee/escribe con Room, durable.
- **Aplicar** pasa por `NutritionPlanCommitCoordinator.commit(...)` con `commitId` determinista por semana (`checkin-<weekKey>`): reintento/doble toque aplican UNA vez (recibo de idempotencia existente). El objetivo nuevo vale **desde mañana**; la instantánea de hoy y las pasadas no cambian (el coordinador ya conserva la de hoy si es del mismo plan: compruébalo y escribe el test). Estado `APPLIED` con `appliedPlanId` y `proposalJson` solo si el commit termina bien; si falla, sigue `PENDING` y el usuario ve el aviso.
- **Declinar** conserva el objetivo (`DECLINED`); **posponer** (`POSTPONED`) lo vuelve a ofrecer mañana; «No volver a mostrar» (`DISMISSED`) para el módulo, no para el check-in entero.

### 3. Sustituir el uso del motor antiguo
- `NutritionCalibrationViewModel` y `NutritionCalibrationScreen` (la pantalla de calibración existente y su enlace profundo `kpkn://nutrition/calibration`) pasan a mostrar la estimación nueva de N1 (estado, gasto, banda, calidad, tendencia) y a ser la vista de detalle del check-in. **No se quita la pantalla ni el enlace.** `CalibrationStatusModule` (módulo de la pantalla de Nutrición) muestra el estado nuevo.
- `NutritionCalibrationEngine` queda sin llamadores de producción; márcalo `@Deprecated("Sustituido por domain/nutrition/expenditure")` y deja sus tests (si alguno prueba su comportamiento viejo, déjalo; si bloquea, justifícalo en el informe). `NutritionCalibrationRepository`/`NutritionCalibrationProfile` se conservan para no perder datos del usuario.

### 4. Interfaz
- Una **tarjeta de check-in** en la pantalla Nutrición cuando el estado es `NOW` (en `NutritionScreen`; módulo de datos con contenedor, estilo KPKN v2): «Tu check-in semanal está listo» + botón «Empezar». Es lo que hace alcanzable la calibración sin enlace profundo.
- Un flujo por pasos (hoja o pantalla, sigue los patrones de navegación vigentes) con los módulos en orden fijo, barra de progreso, y al final la propuesta con **Aceptar** (primario), **Declinar** y **Posponer**. Explicación breve de qué significa el gasto estimado y qué NO es (una estimación, no una medición). Evidencia visible: «Usé N días; excluí M (por qué)».
- Ajuste del día del check-in dentro del flujo o en la pantalla de calibración.
- Textos en español, tono sereno; accesibilidad real; no inventes cifras: todo sale del estimador. Hoja de render (PNG a 360 dp, 100 % y 130 %): tarjeta, cada módulo, propuesta con cambio, propuesta pausada (sin cambios), error al aplicar. Critícalas (máx. 3 rondas).

## Archivos permitidos
- Nuevos: `domain/nutrition/checkin/*`, `data/repository/NutritionCheckinRepository.kt`, `screens/nutrition/checkin/*`.
- Existentes: `data/db/Daos.kt` (solo métodos de `nutrition_checkins`), `data/repository/NutritionCalibrationRepository.kt`, `data/repository/NutritionPlanCommitCoordinator.kt` (solo si hace falta un parámetro nuevo sin romper llamadores), `screens/nutrition/NutritionCalibrationViewModel.kt`, `NutritionCalibrationScreen.kt`, `screens/nutrition/design/calibration/*`, `screens/nutrition/NutritionScreen.kt` (la tarjeta), `NutritionViewModel.kt` (solo el estado del check-in), `data/models/Settings.kt` (solo el campo con valor por defecto de preferencias del check-in), `domain/nutrition/NutritionCalibrationEngine.kt` (solo `@Deprecated`), rutas en `navigation/Navigation.kt` + `MainActivity.kt` (bloques nuevos).
- Tests y hoja de render.

## Pruebas obligatorias
- **C1:** aparece el día elegido y sigue disponible 3 días; aceptar aplica UNA vez aunque se repita o se toque dos veces (idempotente); declinar conserva el objetivo; las `DailyGoalSnapshot` pasadas (y la de hoy) no cambian; el objetivo nuevo rige desde mañana.
- **C2:** tras ≥ 7 días sin registro el check-in explica la pausa y no propone cambios; al reanudar no hay salto mayor que el paso máximo.
- **C3/C4:** los días bajo el umbral se listan; marcarlos los excluye sin borrar comidas; rechazar la marca los conserva; un ayuno marcado entra con 0 kcal; un día vacío sin marcar se excluye.
- **G7/G8/G11:** estados con motivo; calidad enumerada; la propuesta se puede aceptar desde la UI (ya no hay solo enlace profundo).
- Una comida futura no cambia el gasto de hoy; editar un día pasado solo recalcula desde ese día.
- ViewModel: estados `NOW/FUTURE/NONE`, posponer, descartar módulo.
- Fallo del commit: la decisión queda `PENDING` y el usuario ve el aviso; el objetivo no cambia.

## Comandos
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.*" --tests "com.example.kpkn.data.repository.Nutrition*" --tests "com.example.kpkn.screens.nutrition.*" --tests "com.example.kpkn.data.db.*"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/mfp/N2b` con `--tests "com.example.kpkn.render.NutritionCheckinRenderSheetTest"`.

## Fuera de alcance
No cambies el estimador de N1 (si encuentras un defecto, descríbelo en el informe), la EER, el reparto semanal por días de entrenamiento ni AUGE. No hay modo automático. Nada de ciclo menstrual ni recetas.
