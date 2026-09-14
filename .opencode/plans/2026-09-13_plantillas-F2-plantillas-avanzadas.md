---
flags: []
---

# F2 — Plantillas avanzadas KPKN

## Rutas

- `ProgramTemplates.kt` (`recipe` en COMPLEX)
- `ProgramTemplateEngine.applyTemplate` delega en `PlanMaterializer`
- Recetas desde `DaySlotTemplate`, cero exenciones

## Impacto

- Sustituye prescripción sintética de plantillas COMPLEX.
- SIMPLE sigue usando split + prefill.

## Pruebas

- `ProgramTemplateEngineTest`, `ProgramTemplateCompositionContractTest`, `ProgramTemplateClaimsTest`, `ProgramRecipeValidatorTest`

## Riesgos

- Tests previos asumían 3 días SBD genéricos; se actualizan al contrato profesional.
