---
flags: []
---

# Endurecimiento de agentes KPKN (2026-08-15)

Implementado directamente en esta sesión (solicitud del usuario): diferenciar Orquestador/Auditor/Constructor frente a los agentes nativos y optimizar coste de tokens.

## Bucle Constructor ↔ Auditor automático (añadido en la misma sesión)

- `.opencode/plugin/audit-loop.ts` — escucha las transiciones del tool `pipeline` y encadena sesiones: `submit_audit` del Constructor lanza sesión del Auditor; `request_corrections` lanza sesión del Constructor (resume_construction); `accept` termina. Tope: 5 rondas por plan.
- Verificado con 8 smoke tests (duplicados, gate bloqueado, sin pipeline, tope de rondas, ciclo nuevo).
- El usuario solo aprueba el plan una vez; el resto del bucle corre sin intervención.

## Rutas

- `.opencode/agent/orquestador.md` — modelo `openai/gpt-5.6-sol`, permisos (edit solo planes+memoria, bash solo git), delegación obligatoria a subagentes, formato de plan con banderas.
- `.opencode/agent/auditor.md` — permisos (edit solo audits+memoria, bash para tests), steps, skill `debug-audit`, catálogo de regresiones.
- `.opencode/agent/constructor_kpkn.md` — permisos (edit denegado en `.opencode/plans/**`), steps, skills por banderas, consulta de catálogo.
- `.opencode/agent/investigador.md`, `.opencode/agent/revisor.md`, `.opencode/agent/mano-extra.md` — modelo `opencode/deepseek-v4-flash-free` (subagentes baratos).
- `.opencode/plugin/kpkn-gate.ts` — nuevo plugin de compuertas.
- `.opencode/memory/MEMORY.md` — sección "Catálogo de regresiones".
- `AGENTS.md` — nota del flujo de agentes.

## Impacto

- Android/iOS/backend: sin cambios de producto; solo infraestructura de agentes.
- Bandera Room/AUGE/voz: n/a (no se toca producto).
- Pruebas: validación de sintaxis del plugin TypeScript; frontmatter conforme al schema de opencode.
- Documentación: AGENTS.md, MEMORY.md, este plan.
- Riesgos: reiniciar opencode para cargar config; el plugin puede bloquear flujos ad-hoc sin pipeline (por diseño solo aplica cuando existe pipeline).

## Pruebas

- `bun --check .opencode/plugin/kpkn-gate.ts` (o tsc/tsx según disponibilidad).
- Revisión manual del frontmatter contra campos permitidos por opencode (model, mode, description, color, temperature, permission, steps).

## Riesgos

- Campos `permission`/`steps` del frontmatter: si opencode rechaza algún patrón, ajustar sin romper la carga.
- El gate de `submit_audit` depende de detectar "BUILD SUCCESSFUL" en la salida de bash; si el wrapper lo devuelve distinto, se puede relajar.