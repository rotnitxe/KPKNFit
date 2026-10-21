# CU0 · Cuerpo: datos correctos y honestos (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3`). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`,
`docs/screens-v1/briefs/S_COMUN.md`, `docs/screens-v1/BODY_V1.md` y el mapa `C:\kw\analysis\maps\body.md` (rutas y líneas).

Este paquete NO rediseña. Corrige los cálculos y deja la pantalla de hoy mostrando cifras correctas. El rediseño (CU1 a CU3) se
apoyará en lo que hagas aquí.

## Qué haces
1. **Resumen puro del peso**, en `domain/body/BodyWeightSummary.kt` (nuevo, sin Android):
   `summarizeWeight(observations, range, goal: ResolvedBodyGoal?, zone: ZoneId, today: LocalDate): BodyWeightSummary`, con:
   - **`current` y `currentDate`:** el ÚLTIMO pesaje válido (`BodyMetric.WEIGHT`, con `latestValidByMetric`), esté o no dentro del
     rango. Hoy se toma el peso de la última sesión con % de grasa (Screen:150-151).
   - **`rangeStart` y `rangeStartDate`:** el primer punto de la serie diaria (`dailyMedianSeries`) dentro del rango.
     - `change = current - rangeStart`, y null si falta alguno.
     - La fecha va escrita para que la interfaz diga «desde el 12 de agosto».
   - **`measuredWeeklyRate`:** kg por semana con `weeklyRate` sobre la serie diaria de los ÚLTIMOS 28 días, sea cual sea el rango.
     Si `weeklyRate` no tiene datos suficientes (sus reglas actuales), es null.
   - **Objetivo** (si hay `goal` de peso):
     - `remaining = target - current`;
     - `progressPercent` con `goalProgressPercent(baseline, current, target)`. La línea de base es el primer pesaje desde
       `goal.createdAtEpochMs` o, si no hay, el primero de todos;
     - `eta: YearMonth?`, SOLO si `measuredWeeklyRate` va hacia la meta y su valor absoluto es ≥ 0,05 kg por semana. Si no, null.
       Nunca una fecha sin ritmo medido.
2. **Objetivo resuelto sin cruces de métrica:** `resolveBodyGoal(activePlan, goals): ResolvedBodyGoal?` (puro, mismo archivo).
   - Prioridad:
     1. `typedBodyGoal` del plan activo;
     2. `goalType` + `goalValue > 0` del plan;
     3. el objetivo propio más reciente (`updatedAtEpochMs`) de `body_goals`, CON SU métrica.
   - Nunca se usa `GoalMetric.WEIGHT` por defecto con un valor que es un %. Corrige Screen:159-176.
3. **IMC:** la pantalla usa `bmiCategory` del dominio (4 categorías continuas) sobre el valor REDONDEADO a un decimal, el mismo que
   se muestra. Fuera la tabla de 5 categorías con huecos de Screen:1589-1595. Con 24,995 se muestra «25,0» y su categoría es la de
   25,0.
4. **Fecha y hora locales** de las medidas manuales (VM:183, VM:159):
   - Se guardan con `ZoneId.systemDefault()` y su `zoneId`.
   - Si la fecha es hoy, la hora actual; si es otro día, las 12:00 locales.
   - Prueba: en `America/Santiago`, una medida del 13 de octubre se lee como del 13 de octubre.
5. **Sin pérdida de precisión al editar:** al precargar y al guardar, los valores van tal cual. El redondeo es solo de presentación
   (Screen:1893-1900 y 2098-2100).
6. **Repositorio con exclusión mutua:** `BodyProgressRepository.load()` y la migración de lo heredado corren bajo `writeMutex`
   (Repo:235-274).
7. **La pantalla de hoy, correcta y sin inventos:**
   - **Cifras del resumen:** el héroe y el objetivo usan `BodyWeightSummary` del VM: peso actual, cambio con fecha, ritmo medido o
     «Sin ritmo medido aún» y fecha estimada o «Sin fecha estimada aún».
   - **Fuera los inventos:** la barra al 45 % sin dato (`BodyHeroDetailRow`, Screen:895 con `progress` nulo), «Sin fecha» y
     «Calibrando» fijos (Screen:1312-1313), y «0.0 kg/sem» sin plan.
   - **Una sola fuente de series:** el VM expone la serie diaria y la tendencia del peso y del % de grasa para el rango, y la pantalla
     deja de recalcularlas (Screen:195-212 y 2105-2116).
   - **«Sin registros»** solo cuando de verdad no hay ningún pesaje.
   - **Los textos nuevos:**
     - «desde el 12 de agosto»;
     - «Sin ritmo medido aún»;
     - «Hacen falta 2 semanas de pesajes para medir tu ritmo»;
     - «Sin fecha estimada aún».

## Pruebas
- **Unitarias** `BodyWeightSummaryTest`:
  - el último pesaje gana a una sesión de composición más antigua y a otra más reciente sin peso;
  - el cambio usa el primer punto del rango y devuelve su fecha;
  - el ritmo es null con pocos datos;
  - `eta` es null si te alejas de la meta o si el ritmo es menor de 0,05;
  - con ritmo hacia la meta, `eta` es el mes esperado;
  - la línea de base es el primer pesaje desde `createdAt`.
- **Unitarias** `ResolveBodyGoalTest`:
  - plan con `typedBodyGoal`;
  - plan sin tipo y `goalValue` 0 con un objetivo propio de % de grasa: métrica `BODY_FAT`, no `WEIGHT`;
  - sin nada: null.
- **Unitaria** de IMC con 24,995: se muestra «25,0» y la categoría es la de 25.
- **Unitaria** de la fecha local.
- Siguen en verde: `BodyProgressCalculationsTest` y `NutritionActionRoutingTest`.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*BodyWeightSummary*" --tests "*ResolveBodyGoal*" --tests "*BodyProgress*" --tests "*NutritionActionRouting*" > /c/kw/logs/screens-cu0-<n>.log 2>&1`

## Commit
- Por rutas: `fix(body): peso actual, cambio, ritmo medido, objetivo e IMC correctos y sin cifras inventadas (CU0)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Rediseñar la pantalla: solo cambian los valores y se quitan los inventos.
- Tocar Nutrición, salvo leer el plan activo.
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Firmas públicas nuevas.
- Pruebas: número y resultado.
- Riesgos.
