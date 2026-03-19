# Paso 2 - Paridad de surface area y navegacion

Fecha: 2026-03-18

## Veredicto

- Estado: aprobado con reservas
- Objetivo cumplido: ya existe un mapa claro de la superficie navegable PWA vs RN.
- Objetivo no cumplido: la navegacion RN todavia no cubre toda la superficie 1:1 de la PWA.

## Lo que confirme

### Base RN existente

- Navegacion RN centralizada en `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\navigation\AppNavigator.tsx`
- Tipos de navegacion en `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\navigation\types.ts`
- Entry point externo limitado en `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\navigation\navigationRef.ts`
- Deep linking RN ya presente, pero parcial

### Superficie PWA existente

- La fuente de verdad de rutas sigue siendo:
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\routes\navigation.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\routes\router.tsx`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\types.ts`

## Lectura ejecutiva

- La PWA tiene una capa de navegacion basada en `View` + `viewToPath()` + TanStack Router.
- RN ya tiene tabs, stacks y linking.
- El problema no es "no hay navegacion RN".
- El problema es que la navegacion RN todavia comprime demasiadas superficies de la PWA dentro de menos entrypoints.

## Cobertura navegable actual

### Bien cubierto o razonablemente cubierto

- `home` -> `Home`
- `my-rings` -> `Rings`
- `programs` -> `Workout / ProgramsList`
- `program-detail` -> `Workout / ProgramDetail`
- `session-editor` -> `Workout / SessionEditor`
- `workout` -> `Workout / WorkoutMain`
- `session-detail` -> `Workout / SessionDetail`
- `nutrition` -> `Nutrition / NutritionDashboard`
- `smart-meal-planner` -> `Nutrition / MealPlanner`
- `food-database` -> `Nutrition / FoodDatabase`
- `food-detail` -> `Nutrition / FoodDetail`
- `progress` -> `Profile / ProgressOverview`
- `body-progress` -> `Profile / BodyProgress`
- `settings` -> `Settings`
- `coach` -> `Coach`
- `exercise-database` -> `Workout/Wiki -> ExerciseDatabase`
- `exercise-detail` -> `Workout/Wiki -> ExerciseDetail`
- `wiki-home` -> `WikiHome`
- `wikilab-biomechanics` -> `WikiBiomechanics`
- `mobility-lab` -> `WikiMobility`
- `muscle-group-detail` -> `WikiMuscleDetail`
- `joint-detail` -> `WikiJointDetail`
- `tendon-detail` -> `WikiTendonDetail`
- `movement-pattern-detail` -> `WikiPatternDetail`
- `chain-detail` -> `WikiChainDetail`

### Parcial o comprimido

- `program-editor` -> repartido entre:
  - `ProgramWizard`
  - `MacrocycleEditor`
  - `SplitEditor`
- `kpkn` -> absorbido de facto por `WikiHome`, no como landing 1:1
- `athlete-id` -> absorbido dentro de `ProfileScreen`, no como ruta propia
- `athlete-profile` -> absorbido dentro de `ProfileScreen`
- `body-lab` -> existe como componente RN, no como ruta RN propia
- `body-part-detail` -> existe como componente RN, no como screen/ruta expuesta
- `program-metric-*` -> absorbido por `ProgramDetail`, sin rutas metricas 1:1

### Faltante como superficie navegable real

- `auth`
- `recovery`
- `sleep`
- `tasks`
- `log-hub`
- `achievements`
- `log-workout`
- `ai-art-studio`
- `training-purpose`
- `social-feed`
- `home-card-page`
- `muscle-category`

## Hallazgos principales

1. La PWA tiene una capa canonica de rutas por `View` y RN no tiene un mapa equivalente 1:1.
   - PWA: `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\routes\navigation.ts`
   - RN: `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\navigation\AppNavigator.tsx`

2. RN ya soporta deep links, pero el coverage no replica toda la PWA.
   - Faltan rutas para varios `View` canonicos.

3. El entrypoint externo RN es demasiado corto.
   - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\navigation\navigationRef.ts`
   - Solo contempla: `home`, `rings`, `workout`, `nutrition`, `profile`, `wiki`, `settings`, `coach`, `progress`
   - No abre destinos finos como:
     - `program-detail`
     - `session-detail`
     - `food-detail`
     - `exercise-detail`
     - `body-progress`
     - `wiki detail views`

4. La navegacion secundaria de la PWA no tiene espejo RN claro.
   - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\TabBar.tsx`
   - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\SubTabBar.tsx`
   - `StickyMiniNav.tsx` no existe como archivo PWA actual, asi que no lo trato como gap directo, pero RN tampoco expone una capa equivalente de subnavegacion persistente.

5. Workout sigue teniendo superficie funcional sin landing RN equivalente.
   - PWA existe:
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\StartWorkoutModal.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\StartWorkoutDrawer.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\FinishWorkoutModal.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\ReadinessDrawer.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\SessionDetailView.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\LogHub.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\LogWorkoutView.tsx`
   - RN confirmado:
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\components\workout\FinishSessionModal.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\SessionDetailScreen.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\screens\Workout\ActiveSessionScreen.tsx`
   - Pero no aparecen entrypoints RN equivalentes para start/log hub/log workout.

6. Wiki esta mejor encaminado que otros dominios.
   - PWA confirmada:
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\WikiHomeView.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\WikiLabBiomechanicsView.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\MobilityLabView.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\MuscleGroupDetailView.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\JointDetailView.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\TendonDetailView.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\MovementPatternDetailView.tsx`
     - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\components\ChainDetailView.tsx`
   - RN ya tiene rutas/screen reales para casi todo ese bloque.

## Backlog oficial que deja este paso

### Critico

1. Crear un mapa formal PWA `View` -> RN route/stack/screen.
2. Extender `navigationRef.ts` para soportar destinos finos externos.
3. Decidir la superficie RN real para:
   - `log-hub`
   - `log-workout`
   - `recovery`
   - `sleep`
   - `tasks`
   - `achievements`
   - `training-purpose`
   - `ai-art-studio`

### Alto

4. Definir si `athlete-id`, `athlete-profile`, `body-lab`, `kpkn` y `program-metric-*` tendran rutas propias o seguiran absorbidos por screens mayores.
5. Reforzar navegacion secundaria equivalente a `TabBar` / `SubTabBar`.
6. Abrir entrypoints RN reales para flujo de start workout y log workout.

### Medio

7. Exponer `body-part-detail` como screen navegable o documentarlo como absorbido.
8. Definir tratamiento de `muscle-category`.
9. Unificar criterio de deep links entre widgets/notificaciones y stacks RN.

## Decision

Paso 2 cerrado como mapa operativo de superficie y navegacion.

- No prueba paridad 1:1 total.
- Si prueba donde la navegacion RN ya existe, donde esta comprimida y donde directamente falta.
- El siguiente paso puede trabajar sobre logica/matematicas sin volver a abrir este paso.
