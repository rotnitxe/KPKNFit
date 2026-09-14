---
flags: []
---

# F5 — Splits y plantillas de sesión PL

## Rutas

- Publicar splits PL con `dayDefinitions`
- Plantillas de sesión sistema desde `DaySlotTemplate`
- `SessionTemplateCatalogTest` exige H1-H10

## Impacto

- Ruta SIMPLE + split también recibe sesiones profesionales.

## Pruebas

- `SessionTemplateCatalogTest`, `SplitAuditTest`

## Riesgos

- Plantillas existentes que fallen H1-H10 se corrigen aquí.
