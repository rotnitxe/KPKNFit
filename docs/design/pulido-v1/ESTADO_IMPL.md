# Estado de la implementación (se actualiza a mano; la última línea manda)

Reglas: `PROTOCOLO_1A1.md`, `briefs/impl-COMUN-v2.md`, `briefs/impl-pasadas.md`, `DECISIONES_APROBADAS.md`. Rama de integración `feat/pulido-v1` en `C:\kw\pulido`; cada paquete en `C:\kw\pu-<prefijo>` (rama `pulido/<prefijo>`).

| Paquete / fase | Estado | Agente | Notas |
|---|---|---|---|
| Base (arreglos directos) | commit `49dc7af76`; compila; tests de sus clases en verde (4 min 55 s) | — | |
| Fase 0.1 catálogo de cimientos (`cm`) | **hecho** (36 escenas, 0 con problemas; 65 sombras, 7 vidrios, 14 curvas, 23 duraciones, 33 familias de `@keyframes`; los 20 pares casi iguales NO se unifican) | a598a59734f96e63c | `tools/tokens-group.cjs`, pestaña `cimientos`, `paridad/cimientos.json`, `paridad/CIMIENTOS_GRUPOS.md`; el inventario ahora ve los atajos con `var()` (+32 animaciones, +25 transiciones, +5 degradados) |
| Fase 0.2 profundidad Compose | **hecho y fusionado** en `feat/pulido-v1` (`e1a8013a0`): sombras de caída 0,65–0,82/255, interiores ≤ 0,04, resplandores 1,05–1,5, degradados 0,07–0,55; vidrio real y `blur()` solo se miden en el Flip (DESV-100/101/102) | a6a724b7cd6ba3ca2 | `ui/design/depth/*`, `DepthSheetRenderTest`, `paridad/CALIBRACION_DEPTH.md`, comparaciones en `paridad/capturas/DEPTH-*.png` |
| Fase 0.3 movimiento Compose | **hecho y fusionado** (`pulido/mo` en `feat/pulido-v1`): 274 fotogramas contra el lienzo: curvas y duraciones ≤ 0,04 dp, 32 de 33 familias ≤ 1,94/255, ondas ≤ 2,0/255 salvo «sobre la zona» 5,6; DESV-200…203 (revelado/desenfoque, cuenta de cifras por Inter 800, suelo de texto, ondas); `kpknPress`/`kpknEnter/Exit` solo compilan, sin hoja de fotogramas; sombras de ignición/onda-anillo/pulso-anillo se conectan con profundidad en B/C | a37c6c945962ad62f | `ui/design/motion/*`, `MotionFrameRenderTest`, `KpknMotionEngineTest`, `paridad/CALIBRACION_MOTION.md`, tiras en `paridad/capturas/tira_*.png`; ojo: `tools/shots.cjs --t` no congela bien las hojas con 19 instantes (usar `?t=`) |
| `as` pasada A | en marcha | ade6f00a56298ed82 | worktree `C:\kw\pu-as` |
| `ed` tanda A1 (series) | **hecho y fusionado** (`3a9f17068`): compila, 14 clases / 189 pruebas en verde (verificado en los XML); diferencia con el lienzo 4,5–13,5 % de píxeles sin sombras ni movimiento; filas S/V/G/A/T/X/J sin cerrar (marcas `// pulido:ed-…`) | a182c3198f44f8a89 | informe `maps/editor-impl-a1.md`; riesgo: la piel nueva de `InlineSetRow`, `RmSetFocus`, `AmrapSetFocus`, `RestSheet`… la heredan el cajón «Editar series» y las rondas de superserie |
| `ed` tanda A2 (lista, islas, barra, gestos) | en marcha (02:00) | a9eb6ec38047e3cf1 | worktree `C:\kw\pu-ed` (al día con `feat/pulido-v1`); después B/C de A1 y A2 con el movimiento |
| `lv` fase 1 (tarjeta: reps, esfuerzo, ROM) | en marcha (lanzada a las 01:10 desde `f7a64dccc`; la fase 2 llega con los cimientos) | a8390daeec1bd8111 | worktree `C:\kw\pu-lv`, rama `pulido/lv`; el roadmap (`lv-road-*`) es otra tanda |
| `rp` motor y datos | **hecho y fusionado** (`95e43db90`): R0–R15, 188 pruebas JVM en verde (verificadas en los XML), planificador `SplitMigrationPlanner`, deshacer, 35 descripciones; no compilado con Android todavía | ac3a6e40fea743c17 | informe `maps/reparto-impl-motor.md` |
| `rp` interfaz tanda 1 (catálogo, vista previa, revisión, resumen, deshacer) | en marcha (01:45; la tanda 2 —hojas «Cómo aplicar», comparar, propio— la pido yo después) | a1d1eccbbd983b10d | worktree `C:\kw\pu-rpu`, rama `pulido/rpu`; movimiento al llegar `ui/design/motion` |
| `ce` fase 1 (cardio del editor) | en marcha (02:30) | a3729b182cae97783 | worktree `C:\kw\pu-ce`, rama `pulido/ce`; conflictos esperados con A2 y asistente en `SessionEditorScreen.kt`/`SessionListItems.kt` (los resuelve el agente al fusionar) |
| `lx`, `cv`, `tc`, `ct`, `nc`, `sp`, `aj`, roadmap, `rp` tanda 2, B/C del editor-A1 | pendientes | — | se lanzan según la cuota: la semanal subía a ~3,6 puntos/hora con 5 agentes (76 % a las 02:25); al 94 % se para y se deja el relevo |

Orden previsto: oleada 1 (`ed` A1→A2, `as`, luego `ce`), oleada 2 (`lv` con roadmap B, `lx`, `cv`), oleada 3 (`ct`, `rp`, `tc`), oleada 4 (`nc`, `sp`, `aj`); pasadas B/C y D de cada paquete cuando los cimientos estén en `feat/pulido-v1`.

Convenciones: los documentos se escriben SOLO en la copia canónica `C:\kw\pulido\docs\design\pulido-v1` (las copias de los worktrees son de lectura); el Kotlin vive en el worktree de cada paquete; `paridad-check` se corre con `--repo=<worktree del paquete>`. Rangos de DESV en `paridad/DESVIACIONES.md`.

Última línea: 30-oct, catálogo de cimientos listo (`f7a64dccc`); en marcha profundidad (`pu-dp`), movimiento (`pu-mo`), `as`-A y `ed`-A1.
