---
flags: []
---

# F4 — UI creación, detalle y Home

## Rutas

- `CreateProgramTemplateSheet` chip PROTOCOLOS
- `ProtocolDetailSheet`, `TrainingMaxWizard`
- Home / MainActivity abren la sheet
- ProgramDetail: card AUGE, panel TM, re-materializar
- HomeSessionSection muestra receta de hoy

## Impacto

- Superficie visible del diferenciador. No ampliar `MacrocycleEditorLegacy`.

## Pruebas

- ViewModel unit tests + `installBaseDebug` y relanzado en emulador

## Riesgos

- Sheets Compose nuevas; no tocar el editor monolítico.
