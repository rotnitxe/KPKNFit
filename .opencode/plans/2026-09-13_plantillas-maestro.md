---
flags: []
---

# Plantillas, protocolos y autorregulación AUGE — plan maestro

Fuente de verdad de producto: plan de auditoría aprobado. Alcance Android. iOS escalado.

## Rutas

- Modelos: `data/models/Program.kt`, `ProgramScheduleModels.kt`, `PowerliftingProfile.kt`, `data/protocols/*`, `data/programs/*`, `data/splits/*`, `data/sessions/*`
- Motores: `domain/training/PlanMaterializer.kt`, `ProgramAutoregulationEngine.kt`, `ProgramProtocolEngine.kt`, `ProgramTemplateEngine.kt`, `ProgramProgressEngine.kt`, `BlockTransitionEngine.kt`
- UI: `screens/programs/*`, `screens/programdetail/*`, `screens/home/*`, `MainActivity.createProgramAndOpen`

## Impacto

- Sin bump Room. Recetas y TM viven en JSON de `ProgramEntity` con defaults.
- Contenido visible exige receta + `SessionCompositionPolicy`.
- AUGE propone ajustes semanales; el usuario confirma (toggle AUTO).

## Pruebas

- `testBaseDebugUnitTest --tests '*Protocol*' --tests '*PlanMaterializer*' --tests '*ProgramTemplate*' --tests '*Composition*' --tests '*Claims*' --tests '*Autoregulation*' --tests '*BlockTransition*' --tests '*ProgramProgress*' --tests '*SessionTemplateCatalog*'`
- `assembleBaseDebug` + `installBaseDebug` al cierre de F4.

## Riesgos

- Fidelidad vs licencias: atribución + disclaimer; RTS/SBS como frameworks KPKN.
- Catálogo incompleto: `TechniqueModifier`, no inventar IDs.
- Paridad iOS escalada.
