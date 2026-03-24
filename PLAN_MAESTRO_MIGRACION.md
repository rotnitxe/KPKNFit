# Plan Maestro de Migración PWA → React Native (KPKN Fit)

> Basado en la auditoría de Gemini 3.1 y análisis exhaustivo de ambos codebases.
> **Prioridad**: Lógica, funciones, matemáticas, usabilidad — no solo interfaz.
> **Estado actual**: 82% paridad lógica | 30% paridad UI

---

## Resumen del Gap Detectado

| Área | PWA | RN | Gap |
|------|-----|----|----|
| Services | 49 archivos | 68 archivos (incluye RN-only) | ~5 archivos PWA sin equivalente directo |
| Stores | 12 archivos | 21 archivos (incluye extras RN) | RN tiene más stores; verificar paridad interna |
| Utils | 20 archivos | 13 archivos | 7 archivos faltantes |
| Hooks | 8 archivos | Integrados en screens/components | 8 hooks sin equivalente RN |
| Workers | 1 ([computeWorker.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/workers/computeWorker.ts)) | 0 (usa `computeWorkerService` sync) | Estrategia alternativa necesaria |
| Data | 38 archivos | 16 archivos | 22 archivos de datos no migrados |
| Components | 174 archivos | ~150 archivos | ~70% presente, pero faltan módulos completos |
| Screens | N/A (views) | 50 archivos | Cubren la mayoría pero faltan sub-pantallas |
| Contexts | 2 (`AppContext`, `UIContext`) | 2 (equivalentes) | ✅ cubierto |
| Onboarding | 12 archivos | 0 | Módulo completo faltante |
| Social | 1 archivo | 1 screen (`SocialFeedScreen`) | Verificar paridad de funciones |

---

## PASO 1: Auditoría de Paridad Interna de Services Críticos

> **Meta**: Garantizar que cada función exportada de los services PWA existe y produce los mismos resultados en RN.

### Prompt detallado

Abre los siguientes pares de archivos y compara **función por función** todas las exportaciones. Para cada función, verifica: (1) que existe en RN, (2) que recibe los mismos parámetros, (3) que la fórmula/lógica matemática interna es idéntica, (4) que los valores por defecto y constantes hardcodeadas son iguales.

**Pares críticos a auditar (prioridad descendente):**

1. **Motor AUGE** — [services/auge.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/auge.ts) (PWA, 5715 bytes) vs [apps/mobile/src/services/auge.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/apps/mobile/src/services/auge.ts) (RN, 3005 bytes)
   - ⚠️ El archivo RN tiene **la mitad del tamaño**. Verificar si faltan funciones: `calculateMuscleBattery`, `calculateGlobalBatteries`, `calculateSystemicFatigue`, `calculateDailyReadiness`, `calculatePredictedSessionDrain`, `calculateCompletedSessionStress`.
   - Verificar que las constantes de decaimiento, pesos de fatiga y umbrales de recuperación sean idénticos.

2. **Recovery Service** — [services/recoveryService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/recoveryService.ts) (PWA, 39295 bytes) vs RN (30389 bytes)
   - Diferencia de ~9KB. Buscar funciones faltantes o simplificadas. Verificar fórmulas de tasa de recuperación por grupo muscular, penalizaciones por sueño y estrés, y curvas de supercompensación.

3. **Volume Calculator** — [services/volumeCalculator.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/volumeCalculator.ts) (PWA, 27631 bytes) vs RN (19416 bytes)
   - Diferencia de ~8KB. Verificar: cálculo de sets efectivos, tonnage, volumen relativo por grupo muscular, algoritmo de distribución de volumen, y funciones de calibración MRV/MEV/MAV.

4. **Analysis Service** — [services/analysisService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/analysisService.ts) (PWA, 25257 bytes) vs RN (32393 bytes)
   - RN es más grande. Verificar que no se hayan alterado las funciones core: `calculateACWR`, `calculateAverageVolumeForWeeks`, `calculateWeeklyTonnageComparison`, análisis de correlaciones, dashboards powerlifting.

5. **Fatigue Service** — [services/fatigueService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/fatigueService.ts) (PWA, 7954 bytes) vs RN (7009 bytes)
   - Diferencia menor. Verificar cálculo de fatiga sistémica, fatiga periférica, y el modelo de interacción fatiga-volumen-intensidad.

6. **AUGE Adaptive Service** — [services/augeAdaptiveService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/augeAdaptiveService.ts) (PWA, 16263 bytes) vs RN (5200 bytes)
   - ⚠️ RN tiene **un tercio** del tamaño. Esto es crítico: el motor adaptativo es el que ajusta volumen/intensidad automáticamente. Verificar todas las reglas de adaptación.

7. **Volume Calibration** — [services/volumeCalibrationService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/volumeCalibrationService.ts) (PWA, 9753 bytes) vs RN (7127 bytes)
   - Verificar que los algoritmos de calibración MEV/MRV personalizados y los ajustes por historial de entrenamiento estén completos.

8. **AI Service** — [services/aiService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/aiService.ts) (PWA, 25253 bytes) vs RN (10290 bytes)
   - ⚠️ RN tiene menos de la mitad. Verificar: multi-provider logic, fallback chain, prompt templates, parsing de respuestas, manejo de vision/imagen, y funciones Gemini-only.

9. **Nutrition Plan Engine** — [services/nutritionPlanEngine.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/nutritionPlanEngine.ts) (PWA, 10161 bytes) vs RN (10551 bytes)
   - Tamaños similares. Verificar paridad de fórmulas de macros, distribución calórica, y planes adaptativos.

10. **Loop Engine** — [services/loopEngine.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/loopEngine.ts) (PWA, 7942 bytes) vs RN (7644 bytes)
    - Verificar el loop principal de cálculo, especialmente los ciclos de auto-regulación.

**Entregable**: Tabla con cada función exportada, indicando ✅/❌/⚠️ y descripción del delta si existe.

---

## PASO 2: Migración de Utils Faltantes (Lógica Pura)

> **Meta**: Portar los 7 archivos de utils que faltan en RN, priorizando los que afectan lógica y cálculos.

### Prompt detallado

Migra los siguientes archivos de `utils/` (PWA) a `apps/mobile/src/utils/` (RN). Son lógica pura, no tienen dependencias de DOM ni Capacitor. Adapta solo los imports para apuntar a los tipos y servicios RN.

**Archivos a migrar (en orden de importancia funcional):**

1. **`programHelpers.ts`** (124 líneas) — Funciones críticas para programas:
   - `getAbsoluteWeekIndex()` — indexación de semanas en estructura macrociclo
   - `generateSessionsForWeek()` — generación de sesiones según patrón de split
   - `isProgramSimple()` / `isProgramComplex()` — determinación de estructura
   - `getRoadmapBlocks()` — extracción de bloques para roadmap visual
   - `getTotalWeeks()` / `countTrainingDays()` — contadores aritméticos
   - `getDayName()` / `DAYS_LABELS` — etiquetas de días en español
   - **Destino**: `apps/mobile/src/utils/programHelpers.ts`
   - **Nota**: Verificar que `crypto.randomUUID()` se reemplace con el generador de IDs de RN (`generateId.ts` ya existe en RN).

2. **`programEditorUtils.ts`** (19 líneas) — Constante de draft key y `isProgramComplex` (duplicada de programHelpers, pero usada independientemente por el editor).
   - **Destino**: `apps/mobile/src/utils/programEditorUtils.ts`
   - **Nota**: Considerar consolidar con `programHelpers.ts` si RN no necesita el split.

3. **`sessionDayLabel.ts`** (81 líneas) — Genera etiquetas inteligentes tipo "Día de pecho y espalda" analizando los músculos primarios de los ejercicios de la sesión.
   - Depende de: `exerciseIndex.ts` (ya existe en RN), tipos `Session`, `Exercise`, `ExerciseMuscleInfo`.
   - **Destino**: `apps/mobile/src/utils/sessionDayLabel.ts`
   - **Nota**: La función `muscleToGroup()` tiene mapeos en español que deben coincidir exactamente con los nombres de músculos usados en RN.

4. **`colorUtils.ts`** (5682 bytes) — Manipulación de colores M3.
   - **Prioridad baja** si RN usa `useColors()` hook y design tokens. Verificar si algún componente RN necesita estas utilidades (ej.: gráficos con colores dinámicos). Si no se usa, omitir.

5. **`theme.ts`** (12957 bytes) — Sistema de temas completo PWA.
   - **Omitir en RN** — RN tiene su propio sistema en `apps/mobile/src/theme/colors.ts` + `packages/design-tokens`. Solo verificar que no haya constantes numéricas de importancia (como breakpoints, spacings, o radios de borde) que falten en RN.

6. **`shapes.ts`** (2174 bytes) y **`typography.ts`** (4443 bytes) — Tokens de diseño PWA.
   - **Omitir en RN** — Cubiertos por `packages/design-tokens` y `StyleSheet.create`.

7. **`inAppBrowser.ts`** (667 bytes) — Apertura de URLs dentro de la app.
   - **Adaptar para RN** usando `Linking.openURL()` de React Native o un navegador in-app nativo como `react-native-inappbrowser-reborn`.
   - **Destino**: `apps/mobile/src/utils/inAppBrowser.ts`

**Entregable**: Cada archivo migrado con tests unitarios que verifiquen paridad de outputs con los mismos inputs.

---

## PASO 3: Migración de Hooks Faltantes (Lógica de Estado)

> **Meta**: Portar los 8 hooks PWA que gestionan estado reactivo y cálculos derivados.

### Prompt detallado

Los hooks PWA usan `useAppState()` (Context bridge a Zustand) y `useLocalStorage()`. En RN, toda la data vive en Zustand stores directamente. Cada hook debe ser re-implementado leyendo de los stores RN correspondientes.

**Hooks a migrar (en orden de criticidad funcional):**

1. **`useMuscleRecovery.ts`** — Calcula el estado de recuperación de 11 grupos musculares en paralelo usando `calculateMuscleBatteryAsync`. Es el corazón de los dashboards de recuperación.
   - **En RN**: Debe usar `computeWorkerService.ts` de RN (que ya tiene fallback sync). Leer datos de `workoutStore.history`, `exerciseStore.exerciseList`, `wellbeingStore.sleepLogs`, `settingsStore`, etc.
   - **Destino**: `apps/mobile/src/hooks/useMuscleRecovery.ts` o integrar directamente en los componentes que lo necesiten.
   - **Nota**: El `versionRef` pattern para cancelar computaciones stale es importante — mantenerlo.

2. **`useExerciseDatabase.ts`** — Carga, filtra y busca la base de datos de ejercicios.
   - **En RN**: Verificar si `exerciseStore.ts` de RN ya tiene esta funcionalidad integrada. Si sí, este hook es redundante.
   - Si no, migrar la lógica de búsqueda (fuzzy search, filtros por grupo muscular, tipo de equipo, etc.).

3. **`useAchievements.ts`** — Sistema de logros / gamificación.
   - Depende de `data/achievements.ts` (lista de logros con funciones `check()`).
   - **En RN**: Verificar si `data/achievements.ts` existe en RN. Si no, migrar primero el archivo de datos.
   - Almacena logros desbloqueados en localStorage — en RN usar `settingsStore` o un `achievementsStore` nuevo.

4. **`useSettings.ts`** + **`useSettingsStoreShallow.ts`** — Wrappers convenience sobre settingsStore.
   - **En RN**: Probablemente redundante si los componentes ya importan `useSettingsStore` directamente con selectores Zustand. Verificar si hay lógica derivada (computed values) que falte.

5. **`useGoogleDrive.ts`** — Backup/restore a Google Drive.
   - **En RN**: `services/googleDriveService.ts` existe en RN (548 bytes, probablemente stub). Definir si Google Drive backup es feature deseada para RN. Si sí, implementar con `@react-native-google-signin/google-signin` + Google Drive API.
   - **Prioridad**: Media-baja (no afecta cálculos ni sesión).

6. **`useKeyboardOverlayMode.ts`** — Gestiona teclado en modo overlay para inputs numéricos durante sesión.
   - **En RN**: El teclado se gestiona nativamente con `KeyboardAvoidingView`, `react-native-keyboard-controller`, o el hook de keyboard propio de RN. Verificar si la UX de input numérico durante sesión activa está cubierta.

7. **`useLocalStorage.ts`** — Hook genérico de persistencia key-value.
   - **En RN**: Reemplazado por `AsyncStorage` / el `storageService.ts` de RN. No migrar tal cual; verificar que todos los consumers del hook PWA tengan su equivalente en stores Zustand de RN.

**Entregable**: Cada hook migrado/integrado, con verificación de que los componentes que lo consumen producen el mismo estado derivado.

---

## PASO 4: Estrategia de Compute Worker para RN

> **Meta**: Reemplazar el Web Worker de la PWA con una solución equivalente en RN para offload de cálculos pesados.

### Prompt detallado

La PWA usa `workers/computeWorker.ts` (Web Worker IIFE) para ejecutar 9 funciones pesadas fuera del main thread:
- `calculateMuscleBattery`
- `calculateGlobalBatteries`
- `calculateSystemicFatigue`
- `calculateDailyReadiness`
- `calculatePredictedSessionDrain`
- `calculateCompletedSessionStress`
- `calculateACWR`
- `calculateAverageVolumeForWeeks`
- `calculateWeeklyTonnageComparison`

**Verificar en RN** (`apps/mobile/src/services/computeWorkerService.ts`):
1. ¿Todas las 9 funciones tienen wrapper `Async`?
2. ¿El fallback sync funciona correctamente?
3. ¿Hay congelamiento del UI cuando se ejecutan en main thread con historial grande (>200 sesiones)?

**Opciones para offload en RN (evaluar cuál implementar):**

- **Opción A**: `react-native-reanimated` worklets — Solo para cálculos ligeros, no aplica aquí.
- **Opción B**: `react-native-multithreading` con JSI — Permite ejecutar JS en threads separados. Evaluar estabilidad.
- **Opción C**: `InteractionManager.runAfterInteractions()` + `requestAnimationFrame` batching — No es un thread separado pero evita bloquear la navegación.
- **Opción D**: Módulo nativo con Kotlin/Swift para cálculos pesados — Máximo rendimiento, más trabajo de implementación.
- **Opción E (recomendada como primer paso)**: Mantener el fallback sync pero envolver con `InteractionManager` y mostrar skeleton/loading durante el cálculo. Profiling primero para confirmar si hay problemas reales de rendimiento.

**Entregable**: Decisión documentada de la estrategia, con profiling de tiempos de ejecución de las 9 funciones en un dispositivo real con dataset de >100 sesiones.

---

## PASO 5: Paridad de Zustand Stores (Verificación Interna)

> **Meta**: Verificar que cada store RN contiene todos los fields, acciones y computed values de su contraparte PWA.

### Prompt detallado

Compara cada store PWA vs RN, **campo por campo** y **acción por acción**:

| Store | PWA (bytes) | RN (bytes) | Diferencia |
|-------|-------------|------------|------------|
| `settingsStore` | 5210 | 12918 | RN más grande ✅ |
| `programStore` | 1922 | 24530 | RN mucho más grande ✅ |
| `workoutStore` | 2904 | 28950 | RN mucho más grande ✅ |
| `bodyStore` | 2890 | 6014 | RN más grande ✅ |
| `nutritionStore` | 4194 | 9096 | RN más grande ✅ |
| `wellbeingStore` | 4134 | 8206 | RN más grande ✅ |
| `exerciseStore` | 4967 | 10162 | RN más grande ✅ |
| `mealTemplateStore` | 3490 | 5585 | RN más grande ✅ |
| `uiStore` | 19352 | 3452 | ⚠️ RN mucho más chico |
| `authStore` | 2654 | 1513 | ⚠️ RN más chico |

**Foco especial en:**
1. `uiStore` — PWA (19352 bytes) vs RN (3452 bytes). ¿Qué estado de UI se perdió? Verificar: modales activos, drawer states, secciones expandidas, tabs seleccionados, breadcrumb state, toast queue.
2. `authStore` — ¿La autenticación Supabase en RN incluye todos los flujos (register, login, reset password, session refresh)?

**Stores RN-only a documentar** (existen en RN pero no en PWA):
- `bootstrapStore.ts`, `coachStore.ts`, `cutoverStore.ts`, `localAiDiagnosticsStore.ts`, `mealPlannerStore.ts`, `pantryStore.ts`, `nutritionFlowDiagnosticsStore.ts`, `augeRuntimeStore.ts`, `cutoverReadiness.ts`

Verificar que estos stores RN-only no son simplemente la lógica que en PWA vive dentro de `AppContext.tsx` (86509 bytes) o `uiStore.ts` (19352 bytes).

**Entregable**: Tabla con cada field y acción, marcando paridad o delta.

---

## PASO 6: Migración de Data Files (Catálogos y Bases de Datos)

> **Meta**: Asegurar que RN tiene acceso a todos los catálogos estáticos que PWA usa.

### Prompt detallado

La PWA tiene **38 archivos** en `data/`. RN tiene **16 archivos** en `apps/mobile/src/data/`. Además, algunos datos viven en `packages/shared-domain` (comida, nutrición).

**Archivos faltantes en RN que afectan lógica (NO son solo UI):**

1. **`achievements.ts`** — Lista de logros con funciones `check()`. Requerido para `useAchievements`.
2. **`exerciseDatabase.ts`** + **`exerciseDatabaseCentral.ts`** + 3 expansiones — Base de datos completa de ejercicios.
   - **Verificar**: ¿`exerciseStore.ts` de RN carga los ejercicios desde otra fuente? ¿Hay un JSON embedded?
3. **`exerciseDatabaseMerged.ts`** + **`exerciseList.ts`** — Versión consolidada y lista simplificada.
4. **`initialMuscleHierarchy.ts`** + **`muscleGroupDatabase.ts`** + **`muscleHierarchy.ts`** — Jerarquía muscular para cálculos AUGE.
   - ⚠️ `initialMuscleGroupDatabase.ts` existe en RN pero los otros dos no. La jerarquía muscular es **fundamental** para cálculos de batería y recuperación.
5. **`movementPatternDatabase.ts`** — Patrones de movimiento (empujar, jalar, sentadilla, bisagra).
6. **`jointDatabase.ts`** + **`tendonDatabase.ts`** — Para el módulo de alertas articulares/tendones.
7. **`warmupExercises.ts`** + **`activationExercises.ts`** — Ejercicios de calentamiento sugeridos.
8. **`supplements.ts`** — Base de datos de suplementos.
9. **`gyms.ts`** — Lista de gimnasios (si se usa para geolocalización/contexto).
10. **`inferMusclesFromName.ts`** — Inferencia de músculos a partir del nombre del ejercicio (heurístico para ejercicios custom).

**Archivos faltantes que SÍ están en `packages/shared-domain` (verificar):**
- `localChileanFoods.ts`, `foodSynonyms.ts`, `foodDatabaseExpansion.ts`, `foodTaxonomy.ts`, etc.

**Archivos que son solo UI/config y se pueden omitir:**
- `structureTemplates.ts`, `terminology.ts` — Solo si no se usan en lógica.

**Entregable**: Cada archivo de datos migrado a `apps/mobile/src/data/` o confirmado como accesible vía `packages/shared-domain`.

---

## PASO 7: Onboarding Wizard Completo

> **Meta**: Implementar el flujo de onboarding/welcome wizard de 12 archivos que no existe en RN.

### Prompt detallado

La PWA tiene un módulo de onboarding completo en `components/onboarding/` con 12 archivos. RN tiene **0 archivos** de onboarding. Este módulo es crítico para nuevos usuarios.

**Archivos a migrar:**

1. **`UnifiedWelcomeWizard.tsx`** — Orquestador principal del flujo de bienvenida.
2. **`GeneralOnboardingWizard.tsx`** — Wizard general alternativo.
3. **`WelcomeWizard.tsx`** — Versión base del wizard.
4. **`AnimatedSvgBackground.tsx`** — Background visual (adaptar a `react-native-svg`).

**Steps del wizard (en `steps/`):**
5. **`AthleteTypeStep.tsx`** — Selección de tipo de atleta (principiante/intermedio/avanzado/powerlifter/etc.)
6. **`PhysicalDataStep.tsx`** — Datos físicos (peso, altura, edad, sexo)
7. **`ProgramNameStep.tsx`** — Nombre del programa
8. **`SplitStep.tsx`** — Selección de split de entrenamiento
9. **`VolumeStep.tsx`** — Configuración de volumen (MEV/MRV targets)
10. **`RecentWorkoutsStep.tsx`** — Importar entrenamientos recientes (si existen)
11. **`BatteryRingsStep.tsx`** — Introducción al sistema de baterías AUGE
12. **`BatteryPrecalibrationStep.tsx`** — Precalibración de baterías basada en historial

**Para cada step, verificar:**
- ¿Qué datos escribe y en qué store?
- ¿Qué validaciones tiene? (rangos, requeridos, etc.)
- ¿Qué efecto tiene en los cálculos iniciales de AUGE? (precalibración es especialmente importante)

**Adaptaciones RN:**
- Navegación: usar `@react-navigation/native-stack` con screens en vez de un único componente con steps internos.
- Inputs: usar componentes RN nativos (`TextInput`, `Picker`, sliders).
- Animaciones: usar `react-native-reanimated` en vez de CSS transitions.
- Background SVG: usar `react-native-svg` o un gradiente con `LinearGradient`.

**Entregable**: Flujo de onboarding completo funcional, con todos los datos persistidos correctamente en stores y precalibración AUGE operativa.

---

## PASO 8: Paridad del Program Wizard y Session Editor

> **Meta**: Asegurar que la creación y edición de programas/sesiones produce exactamente los mismos resultados en RN.

### Prompt detallado

**Program Wizard** (PWA: `components/program-wizard/`, 7 archivos):
- `WizardLayout.tsx`, `WizardStepIndicator.tsx` — Shell y navegación del wizard
- `CalendarStep.tsx` — Selección de calendario/frecuencia
- `SessionsStep.tsx` — Definición de sesiones por día
- `StructureStep.tsx` — Estructura simple vs compleja (macrociclos/bloques)
- `ProgramPreviewPanel.tsx` — Preview del programa antes de guardar

**RN tiene**: `ProgramWizardScreen.tsx` en `screens/Workout/`. Verificar si ese screen contiene toda la lógica de los 7 archivos PWA o si es un stub.

**Puntos de verificación funcional:**
1. ¿Se puede crear un programa simple (1 macrociclo, 1 mesociclo)?
2. ¿Se puede crear un programa complejo (múltiples macrociclos, bloques, mesociclos)?
3. ¿El preview muestra correctamente: semanas totales, distribución de días, tonnage estimado?
4. ¿`generateSessionsForWeek()` se ejecuta con los mismos resultados?
5. ¿Los templates de split (`data/splitTemplates.ts`) están disponibles?

**Session Editor** (PWA: `components/session-editor/`, 16 archivos):
- `DrawerSystem.tsx`, `AugeDrawer.tsx`, `AugeBottomSheet.tsx`, `AugeFAB.tsx` — Sistema de drawers AUGE
- `ExerciseCardCompact.tsx`, `ExerciseRow.tsx` — Tarjetas de ejercicio
- `InlineSetTable.tsx`, `SetCardGrid.tsx` — Tablas/grids de sets
- `SessionEditorHeader.tsx`, `ContextualHeader.tsx` — Headers contextuales
- `FatigueIndicators.tsx`, `SessionMetricsBlock.tsx` — Indicadores de fatiga en tiempo real
- `SessionWeekRoadmap.tsx` — Roadmap de la semana
- `PartSection.tsx` — Secciones de la sesión (warm-up, main, etc.)
- `SwipeDeleteHintModal.tsx` — UX helper

**RN tiene**: `SessionEditorScreen.tsx` + `sessionEditorMutations.ts`. Verificar si cubren las 16 funcionalidades del PWA.

**Entregable**: Crear un programa complejo y editar una sesión con los mismos inputs en PWA y RN, verificar que el JSON resultante en stores es idéntico.

---

## PASO 9: Paridad de Módulos AI (Multi-Provider + On-Device)

> **Meta**: Asegurar que las 3 capas de AI funcionan igual en RN.

### Prompt detallado

**Capa 1: AI Service (Router de providers)**
- PWA: `services/aiService.ts` (25253 bytes) — Router multi-provider
- RN: `apps/mobile/src/services/aiService.ts` (10290 bytes) — **Menos de la mitad**

Verificar función por función:
1. ¿Todas las funciones de generación de rutinas están presentes?
2. ¿El fallback chain (Gemini → GPT → DeepSeek) funciona igual?
3. ¿Las funciones Gemini-only (vision, image gen, speech) tienen stubs o implementaciones RN?
4. ¿El parsing de respuestas JSON de los LLMs es idéntico?

**Capa 2: Provider Services individuales**
- `geminiService.ts`: PWA 24050 bytes vs RN 1938 bytes — ⚠️ **93% más chico**
- `gptService.ts`: PWA 28946 bytes vs RN 1923 bytes — ⚠️ **93% más chico**
- `deepseekService.ts`: PWA 21173 bytes vs RN 1948 bytes — ⚠️ **91% más chico**

Esto sugiere que los providers RN son thin wrappers que delegan al backend. Verificar:
1. ¿Todos los prompts están definidos en algún lugar compartido o se perdieron?
2. ¿El backend proxy (`backendAIService.ts`) cubre todos los casos de uso?
3. ¿Qué pasa si el backend no está disponible? ¿Hay fallback client-side?

**Capa 3: AI On-Device (FunctionGemma)**
- `services/localAiService.ts`: PWA 6488 bytes vs RN 4375 bytes
- `services/aiNutritionParser.ts`: PWA 9036 bytes vs RN 5653 bytes
- Native bridge: `LocalAiModule.java` existe en RN

Verificar:
1. ¿El pipeline `RegisterFoodDrawer → parseFreeFormNutrition() → LocalAiPlugin → fallback` funciona end-to-end?
2. ¿El modelo `kpkn-food-fg270m-v1` se carga correctamente?
3. ¿El fallback heurístico offline produce los mismos resultados?

**Entregable**: Reporte de funciones AI disponibles en RN vs PWA, con decisión sobre cuáles son client-side vs backend-proxy.

---

## PASO 10: Migración de Componentes UI Faltantes (Por Módulo)

> **Meta**: Migrar los ~80 componentes UI restantes, priorizando los que contienen lógica de cálculo o interacción compleja.

### Prompt detallado

Migrar por módulos, **empezando por los que tienen más lógica embebida**:

### 10.1 — Módulo Workout/Session (PRIORIDAD MÁXIMA)
Componentes PWA sin equivalente claro en RN:
- `WorkoutSession.tsx` → ¿Cubierto por `ActiveSessionScreen.tsx`?
- `FinishWorkoutModal.tsx` → ¿Cubierto por el screen de RN?
- `WorkoutHistory.tsx`, `WorkoutLogPreview.tsx` — Historial de sesiones
- `SessionTimer.tsx` — Timer de sesión activa (descanso entre sets)
- `SessionOverview.tsx` — Resumen de sesión
- `GhostSetInfo.tsx`, `SetDetails.tsx` — Detalles de sets y sets fantasma
- `RPESelector.tsx`, `RIRSelector.tsx` — Selectores de esfuerzo percibido

**Verificar**: Para cada componente, ¿la lógica de cálculo en tiempo real (1RM estimado, volumen acumulado, fatiga de sesión) se ejecuta correctamente?

### 10.2 — Módulo Analytics/Progress (PRIORIDAD ALTA)
Los siguientes componentes tienen cálculos matemáticos embebidos:
- `CorrelationDashboard.tsx` — Correlaciones entre variables (ya existe en RN ✅)
- `PowerliftingDashboard.tsx` — Wilks/DOTS/IPF GL calculators (ya existe en RN ✅)
- `EffectiveVolumeCard.tsx` — Cálculo de volumen efectivo
- `FeedbackInsights.tsx` — Análisis de feedback post-sesión
- `MuscleHeatmap.tsx` — Mapa de calor muscular
- `PersonalRecordsChart.tsx` — PRs con cálculos de progresión
- `TrendCard.tsx` — Tendencias con regresión lineal
- `VolumeBudgetWidget.tsx` — Presupuesto de volumen semanal

### 10.3 — Módulo Nutrition (PRIORIDAD ALTA)
- `RegisterFoodDrawer.tsx` — Drawer de registro de alimentos (interfaz con AI parser)
- `NutritionMacroChart.tsx`, `CalorieHistoryChart.tsx` — Gráficos de macros
- `MealPlannerView.tsx` — Planificador de comidas
- `PantryView.tsx` — Gestión de despensa
- `FoodDatabaseView.tsx` — Vista de base de datos de alimentos

### 10.4 — Módulo Body/Profile (PRIORIDAD MEDIA)
- `BodyLabView.tsx` — Lab de composición corporal (ya existe en RN ✅)
- `BodyFatChart.tsx`, `FFMIChart.tsx`, `BodyWeightChart.tsx` — Gráficos corporales
- `AthleteIDDashboard.tsx` — Panel de ID atlético
- `AthleteProfilingWizard.tsx` — Wizard de perfil atlético

### 10.5 — Módulo Coach/AI (PRIORIDAD MEDIA-BAJA)
- `CoachView.tsx`, `CoachAnalysis.tsx` — Vista del coach AI
- `CoachChatModal.tsx`, `CoachBriefingDrawer.tsx` — Chat y briefing
- `CarpeDiemCoachCard.tsx` — Card motivacional

### 10.6 — Módulo Wiki/Lab (PRIORIDAD BAJA)
- La mayoría de screens wiki ya existen en RN. Verificar paridad de contenido.

**Para cada componente migrado**, asegurar:
1. Que no recalcula lo que un service ya calcula (DRY con services).
2. Que los gráficos usan la misma librería de charts que RN ya tiene (`react-native-gifted-charts` o similar).
3. Que las animaciones usan `react-native-reanimated` y no Animated API legacy.

**Entregable**: Cada módulo de componentes migrado y verificado visualmente.

---

## PASO 11: Notificaciones y Background Tasks

> **Meta**: Asegurar paridad de notificaciones push/locales y tareas en background.

### Prompt detallado

**PWA**: `services/notificationService.ts` (18937 bytes) — Sistema completo de notificaciones.
**RN**: `services/mobileNotificationService.ts` (12284 bytes) + `services/notificationService.ts` (2131 bytes)

Verificar:
1. **Notificaciones locales**: reminders de sesión, alertas de recuperación, recordatorios de nutrición, alertas de hidratación.
2. **Notificaciones push**: si las usa vía Supabase/FCM.
3. **Background sync**: `backgroundSyncTask.ts` de RN (1913 bytes) — ¿sincroniza data con Supabase en background?
4. **Widget sync**: `widgetSyncService.ts` — PWA 5500 bytes vs RN 4550 bytes. Verificar paridad de datos enviados al widget Android.

**Entregable**: Lista de todos los tipos de notificación con su trigger y verificación de que se disparan correctamente en RN.

---

## PASO 12: Testing de Paridad End-to-End

> **Meta**: Crear test suite que verifica que los mismos inputs producen los mismos outputs en PWA y RN.

### Prompt detallado

**Tests existentes:**
- `npm run test:nutrition-logging` — Tests de regresión del pipeline nutricional (PWA)
- `npm run mobile:test` — Jest tests para RN

**Tests de paridad por crear (lógica pura, sin UI):**

1. **AUGE Parity Test**: Dado un historial fijo de 50 sesiones, verificar que `calculateDailyReadiness()` produce el mismo score en PWA y RN (±0.01 tolerancia).

2. **Volume Parity Test**: Dado un programa con 3 sesiones y 15 ejercicios, verificar que `volumeCalculator` produce las mismas métricas de volumen.

3. **Recovery Parity Test**: Dado historial + logs de sueño + logs de bienestar, verificar que `recoveryService` produce los mismos tiempos de recuperación por músculo.

4. **Nutrition Parity Test**: Dado un input de texto libre "2 huevos con arroz y ensalada", verificar que el parser produce los mismos macros.

5. **Program Structure Test**: Crear un programa complejo via las funciones helper, verificar que la estructura JSON es idéntica.

**Ejecución de tests:**
```bash
# Verificar tipos en ambos targets
npx tsc --noEmit                                    # PWA
npm --workspace @kpkn/mobile run typecheck           # RN

# Tests unitarios existentes
npm run test:nutrition-logging                       # PWA nutrition
npm run mobile:test                                  # RN Jest

# Tests de paridad (por crear)
npm run test:parity                                  # Nuevo script
```

**Entregable**: Test suite de paridad que puede ejecutarse en CI y falla si hay divergencia lógica.

---

## PASO 13: Google Drive Backup y Data Export

> **Meta**: Implementar backup/restore y exportación de datos en RN.

### Prompt detallado

**PWA**: `hooks/useGoogleDrive.ts` (4006 bytes) + `services/googleDriveService.ts` (5995 bytes)
**RN**: `services/googleDriveService.ts` (548 bytes) — **Solo un stub**

**Funcionalidades a implementar:**
1. Backup automático a Google Drive (JSON comprimido con toda la data de stores)
2. Restore manual desde Google Drive
3. Exportación a JSON/CSV local
4. Detección de conflictos entre datos locales y backup

**Dependencias RN:**
- `@react-native-google-signin/google-signin` para autenticación
- Google Drive API REST para lectura/escritura de archivos
- Formato de backup: debe ser el mismo JSON que PWA para migración transparente

**Entregable**: Backup funcional que permite pasar datos de PWA → RN via Google Drive.

---

## PASO 14: Supabase Sync Completa

> **Meta**: Asegurar que la sincronización con Supabase es bidireccional y robusta.

### Prompt detallado

**PWA**: `services/supabaseSyncService.ts` (7804 bytes) — Sync completa
**RN**: `services/supabaseSyncService.ts` (1194 bytes) — **85% más chico**

Verificar:
1. ¿Qué tablas se sincronizan? (workouts, programs, nutrition, body, settings, achievements)
2. ¿Hay resolución de conflictos (last-write-wins, merge, etc.)?
3. ¿La sync funciona offline-first con cola de cambios pendientes?
4. ¿El auth flow con Supabase está completo? (`authStore` RN es más pequeño que PWA)

**Entregable**: Sync bidireccional probada con escenario offline → online → merge.

---

## PASO 15: Verificación Final y Cutover

> **Meta**: Ejecutar checklist final antes de declarar paridad completa.

### Prompt detallado

**Checklist de verificación:**

- [ ] Typecheck limpio en ambos targets (`npx tsc --noEmit` + `npm --workspace @kpkn/mobile run typecheck`)
- [ ] Todos los tests pasan (`npm run test:nutrition-logging` + `npm run mobile:test`)
- [ ] Tests de paridad pasan (nuevo script)
- [ ] App no crashea en cold start (release APK, no debug)
- [ ] Onboarding completo: nuevo usuario puede crear programa desde 0
- [ ] Sesión activa: iniciar, registrar sets, ver fatiga en tiempo real, finalizar, ver resumen
- [ ] Nutrición: registrar alimentos via texto, ver macros, ver historial
- [ ] Baterías AUGE: scores de readiness son correctos vs PWA con mismos datos
- [ ] Gráficos: todos los gráficos de progreso muestran datos correctos
- [ ] Notificaciones: reminder de sesión se dispara
- [ ] Settings: cambiar provider AI, cambiar unidades, toggle features
- [ ] Wiki: navegar ejercicios, músculos, articulaciones, tendones
- [ ] Backup: exportar e importar datos via Google Drive
- [ ] Performance: no hay jank notable en scroll de listas de >100 items
- [ ] Memoria: no hay memory leaks en sesión activa prolongada (>30 min)

**Entregable**: Reporte final de paridad con evidencia (screenshots, console logs, test results).

---

> [!IMPORTANT]
> **Orden de ejecución recomendado**: Pasos 1 → 2 → 3 → 5 → 6 → 4 → 7 → 8 → 9 → 10 → 11 → 12 → 13 → 14 → 15
>
> Los pasos 1-6 son **fundacionales** (lógica pura). No avanzar a UI (pasos 7-10) sin tener la lógica 100% validada.
> El paso 12 (testing) se puede ir ejecutando incrementalmente después de cada paso.
