# Estado de la implementación (se actualiza a mano; la última línea manda)

Reglas: `PROTOCOLO_1A1.md`, `briefs/impl-COMUN-v2.md`, `briefs/impl-pasadas.md`, `DECISIONES_APROBADAS.md`. Rama de integración `feat/pulido-v1` en `C:\kw\pulido`; cada paquete en `C:\kw\pu-<prefijo>` (rama `pulido/<prefijo>`).

| Paquete / fase | Estado | Agente | Notas |
|---|---|---|---|
| Base (arreglos directos) | commit `49dc7af76`; compila; tests de sus clases en verde (4 min 55 s) | — | |
| Fase 0.1 catálogo de cimientos (`cm`) | **hecho** (36 escenas, 0 con problemas; 65 sombras, 7 vidrios, 14 curvas, 23 duraciones, 33 familias de `@keyframes`; los 20 pares casi iguales NO se unifican) | a598a59734f96e63c | `tools/tokens-group.cjs`, pestaña `cimientos`, `paridad/cimientos.json`, `paridad/CIMIENTOS_GRUPOS.md`; el inventario ahora ve los atajos con `var()` (+32 animaciones, +25 transiciones, +5 degradados) |
| Fase 0.2 profundidad Compose | lanzada | (ver abajo) | worktree `C:\kw\pu-dp`, rama `pulido/dp`; brief `fase0-profundidad.md` |
| Fase 0.3 movimiento Compose | lanzada | (ver abajo) | worktree `C:\kw\pu-mo`, rama `pulido/mo`; brief `fase0-movimiento.md` |
| `as` pasada A | en marcha | ade6f00a56298ed82 | worktree `C:\kw\pu-as` |
| `ed` tanda A1 (series) | en marcha | a182c3198f44f8a89 | worktree `C:\kw\pu-ed`; A2 (lista, islas, dock, gestos) después |
| `ce`, `lv`, `lx`, `cv`, `tc`, `ct`, `rp`, `nc`, `sp`, `aj` | pendientes | — | oleadas 1b–4 |

Orden previsto: oleada 1 (`ed` A1→A2, `as`, luego `ce`), oleada 2 (`lv` con roadmap B, `lx`, `cv`), oleada 3 (`ct`, `rp`, `tc`), oleada 4 (`nc`, `sp`, `aj`); pasadas B/C y D de cada paquete cuando los cimientos estén en `feat/pulido-v1`.

Última línea: 30-oct, base compila y pasa sus tests; arrancan Fase 0.1, `as`-A y `ed`-A1.
