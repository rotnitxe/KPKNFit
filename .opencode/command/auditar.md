---
description: auditar cambios KPKN contra un plan aprobado
agent: auditor
---

Comprueba `pipeline` y audita solo en la etapa `auditing`. Revisa el diff actual contra el plan aprobado: bugs y regresiones, Room/migraciones, AUGE, voz, coroutines, seguridad y frescura de documentación. Usa subagentes `revisor` en paralelo cuando sea útil, escribe el reporte en `.opencode/audits/` y llama `accept` o `request_corrections` según el veredicto.

Plan o alcance:
$ARGUMENTS
