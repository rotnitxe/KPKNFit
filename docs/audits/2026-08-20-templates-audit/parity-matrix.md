# Matriz de paridad — plantillas y programas por bloques (2026-08-20)

| Superficie | Android | iOS | Backend |
|---|---|---|---|
| Session templates catalog | `SESSION_TEMPLATES_SYSTEM` + `SESSION_TEMPLATE_PACKAGE_REVISION=v4` (ejercicios siguen `TEMPLATE_CATALOG_REVISION=v2` del asset) | `SESSION_TEMPLATE_PACKAGE_REVISION=v4` | N/A |
| Splits / protocols | `SPLIT_TEMPLATES` / `PROTOCOL_LIBRARY` (fixes B) | Splits ya existían; protocolos N/A UI | N/A |
| `Block.goal` / `progressionScheme` | Enums NUEVOS opcionales | Paridad en `Program.swift` | `Optional[str]` en `common.py` |
| `ProgramWeek.progressionIndex` | Opcional | Paridad | Opcional |
| `BlockProgressionEngine` | Completo + tests | MVP Swift (diffs + índices) | N/A |
| `BlockTransitionEngine` | Completo + integración ProgressEngine | MVP decisión (sin mutar Program) | N/A |
| UI roadmap / banner / hero | `BlockRoadmap` + banner + `CompactHeroBanner` | `MacrocycleEditor` MVP | N/A |
| Deuda | `tab=` muerto / sub-tab SPLIT | Rellenar SessionTemplates auditadas | — |
