---
flags: []
---

# F0 — Modelo de receta, materializador y política de composición

## Rutas

- Nuevo: `data/protocols/TrainingPlanRecipe.kt`, `domain/training/PlanMaterializer.kt`, `TrainingMaxResolver.kt`, `CompositionTaxonomy.kt`, `SessionCompositionPolicy.kt`, `ProgramRecipeValidator.kt`
- Nuevo: `data/programs/DaySlotTemplate.kt`, `VolumeLandmarks.kt`, `data/exercises/catalogv2/CatalogCompositionMetadataProvider.kt`
- Modificar: `Program.kt`, `PowerliftingProfile.kt`, `ProgramProtocolEngine.kt`, `ProtocolExerciseLibrary.kt`, `ProtocolLibrary.kt` (campos nuevos con default)

## Impacto

- Receta declarativa única para protocolos y plantillas avanzadas.
- `strict` en materializador: HARD sin exención falla en tests.
- Fix legacy: `PULL` no mapea a banca; no duplicar T1; accesorios fijos por bloque.

## Pruebas

- `PlanMaterializerTest`, `TrainingPlanRecipeJsonCompatTest`, `SessionCompositionPolicyTest`, `CompositionTaxonomyTest`, `ProtocolExerciseLibraryTest`

## Riesgos

- JSON legacy debe decodificar con `recipe = null`.
- Umbrales de política son constantes comentadas, no leyes.
