# Ola «Programas y periodización» · ESTADO (2026-10-22)

> Rama: `claude/kpkn-programs-audit-periodization-7abb9c` (worktree `.claude/worktrees/kpkn-programs-audit-periodization-7abb9c`).
> Base: master `8ac57c9d4`. Sin push. Plan: `.claude/plans/lively-squishing-bonbon.md` (o `~/.claude/plans/lively-squishing-bonbon.md`).
> Cierre manual pedido por el usuario el 22-oct: se ejecutó hasta T-14 y se entrega el relevo.

## 1. Resumen

**Ejecutado y commiteado: 9 tareas** — P0 completo (T-01…T-04) y P1a completo (T-10…T-14).
Todo verificado en verde por el carril JVM (`kpkn_jvm.sh`); las tareas con UI se verificaron con la ranura Android (`gradle_slot.sh`).
Última corrida: 7 clases, **91 tests, 0 fallos, 0 errores** (ProtocolRecipeFidelityTest 71, RecipeContractInventoryTest 6, ProtocolCompositionContractTest 3, ProtocolLibraryTest 3, CatalogIdsExistInCatalogTest 3, ExemptionScopeMigrationTest 3, ProtocolExemptionMatrixTest 2).

## 2. Tareas → commit → prueba

| Tarea | Commit | Pruebas nuevas / actualizadas | Estado |
|---|---|---|---|
| T-01 ProgramScope | `7f7f618f0` | `ProgramScopeTest` (5 casos) | hecho |
| T-02 fuera «simple/avanzado» | `ce49b8fce` | ninguna nueva; `ProgramDetailFormatTest` 20/20 y `PlanLabelsTest` 13/13 en verde | hecho |
| T-03 etiqueta de alcance | `95893a0f9` | casos en `PlanLabelsTest` y `ProgramHeaderModelTest` | hecho (con fallback, ver §3) |
| T-04 alcance en UI | `5e4aa879f` | `PlanInfoModelTest` 52/52, `ProgramPlanDisplaySummaryTest` 29/29 | hecho |
| T-10 GZCLP 3 etapas | `c1a47d6db` | `gzclp_has_three_stages_of_four_weeks` (nueva); `gzclp_description_matches_implementation` reescrita | hecho (con extras, ver §3) |
| T-11 Cube con rotación | `e0a56e3a1` | `cube_rotates_methods_each_week` (nueva); 2 tests del Cube actualizadas | hecho (con extras, ver §3) |
| T-12 RP-style rampa + Torso B | `f9482984a` | `rp_style_ramps_accessory_sets_and_torso_b_pulls` (nueva) | hecho (rampa acotada, ver §3) |
| T-13 RTS top sets RPE | `0ff7837d3` | `rts_style_uses_rpe_top_sets_on_all_three_lifts` (nueva) | hecho (con anclas %, ver §3) |
| T-14 Calgary/Smolov Jr/power-12-3 | `58ae30509` | `calgary_weeks_12_to_15_are_taper`, `power_12_3_uses_beginner_accessories` (nuevas) | hecho |

También se actualizó, en cada receta tocada, el test `the_recipes_changed_in_b_s6_carry_content_version_2…` (gzclp, cube-method, kpkn-rp-style, kpkn-rts-style y calgary-16 pasan a `contentVersion 3`; power-12-3, también).

## 3. Desviaciones y decisiones (para revisión)

1. **T-03 (aprobada por el plan):** `PlanLabels.subtitle` queda sin tocar porque `PlanCatalogEditorialContractTest` fija `subtitle(entry) == technicalSubtitle` editorial; se expuso `scopeLabel` para T-04. `PlanLabels.weeksOf` pasó `private → internal`.
2. **T-10 (extras necesarios):** el 10×1+ de la etapa 3 excede C1 (2–8 series/slot) y el techo crudo del inventario (100/100). Corrección de dato: `authoredSetRange(10,10)` en el T1 de esa etapa + `compositionProfile = AUTHORED_EXACT`. Además, exenciones `W2` en w9–w12 (el T3 conservado supera MEV en pico PL). `smolov-jr` legado → `smolov-jr-legacy`.
3. **T-11 (extras necesarios):** exenciones `W2` en w7–w9 (la ola 3 conserva accesorios por diseño del Cube).
4. **T-12 (DECISIÓN DEL USUARIO 22-oct):** la rampa literal a todos los T3 desborda H6/H7 (sesiones de hasta 46 series) y un framework KPKN_NATIVE **no puede declarar exenciones**. Regla del usuario: **protocolo de autor = fidelidad al pie de la letra; sello KPKN = reglas de volumen de KPKN**. Al ser rp-style sello KPKN, la rampa quedó acotada al **primer accesorio de cada día** (la prueba del plan pasa igual: +4 con tope 8). Guardada en taste.
5. **T-13 (DECISIÓN DEL USUARIO 22-oct):** los top set RPE no expresan %1RM y el contrato exige un T1 ≥ 85 % medible en pico (regla BLOCK y suelo de 1RM). Opción elegida: **las series de respaldo declaran el %1RM implícito** con la tabla de la propia app (`Prescriber.percentOfOneRm`), siguiendo la convención L-05 (%TM en fases previas, %1RM en pico). El top set sigue en RPE puro. Los tres T1 quedan con descanso 240 s (H9: zona pesada).
6. **T-14:** al declarar el taper de Calgary (w12–15), C5 se cumple de origen y su exención quedó muerta → retirada (y actualizadas las dos matrices de exenciones que la congelaban). `LEG_CURL`/`PLANK` del plan se llaman en el catálogo `CURL_H`/`PLANCHA`. En `plBeginner12` se usa `includeBench = false` en el día de banca (guardia C2 del plan).

## 4. Datos congelados que hay que mantener al tocar recetas

- **Techos del contrato** (`RecipeContractInventoryTest`): `CEILING = 100` (hallazgos C1–C10 crudos) y `CEILING_AFTER_EXEMPTIONS = 18`. No subirlos: corregir el dato o declarar exención justificada con fuente.
- **Solo C10** puede quedar sin cubrir tras exenciones (además, C10 es siempre SOFT).
- **KPKN_NATIVE no puede declarar exenciones** (protocolos y plantillas propios): corrigen el dato.
- **Exenciones**: toda exención declarada debe silenciar algo («exenciones muertas» = rojo) y las matrices congeladas `ProtocolExemptionMatrixTest` / `ExemptionScopeMigrationTest` deben actualizarse cuando cambian las exenciones de un protocolo.
- **`contentVersion`**: subir en 1 al cambiar una receta y actualizar el test que los congela.
- **Reglas que muerden**: C1 (2–8 series/slot; SPEED 12), C2 (config duplicada por día), C5 (descarga desde 8 semanas si no se repite), H6 (≤30 series y ≤100 min por sesión), H7 (≤12 series directas/músculo/sesión), H9 (descanso 240 s si la serie es pesada ≥85 %1RM), H11/H11b (no exentables: %1RM ≤100, triples ≤92 %, Epley), W2 (MEV/MRV y picos PL), BLOCK (pico con T1 ≥85 % medible), y las pruebas «las fases previas mantienen %TM» / «el pico usa %1RM».

## 5. Archivos calientes tocados (avisar antes de fusionar)

- `data/protocols/definitions/{MadcowNsunsGzcl,ClassicPlProtocols,BodybuildingNativeProtocols}.kt`, `data/protocols/{RecipeBuilders,ProtocolLibrary}.kt`
- `data/programs/{PlanEditorialTable,KpknAdvancedProgramRecipes,PlanLabels}.kt`
- `data/models/Program.kt` (solo textos de `validateTemporalStructure`)
- `screens/programdetail/**` (ProgramHeader, ProgramHeaderModel, ProgramDetailScreen), `screens/programs/{PlanInfoModel,PlanInfoSheet}.kt`
- `res/values/strings.xml`, `screens/sessioneditor/components/sheets/ClonerSaveWarmupSheets.kt` (1 cadena)
- Tests: `data/protocols/{ProtocolRecipeFidelityTest,ProtocolExemptionMatrixTest,ExemptionScopeMigrationTest}.kt`, `data/programs/PlanLabelsTest.kt`, `screens/programdetail/design/ProgramHeaderModelTest.kt`
- **Sin tocar** (como manda el plan): `data/db/**`, `domain/auge/**`, `NativeWorkoutProgressionRuntime`, `Calculations.kt`, `LoadSuggestionEngine`, `VolumeCalculator`, `VolumeView`, etc.

## 6. Pendientes (orden del plan)

- **Ola A (resto):** T-50 `ProgramProgressReport` → T-51 flow VM [ANDROID] → T-52 tarjeta Progreso + hoja Decisiones [ANDROID] → T-53 docs de métricas.
- **Ola B:** T-15 `RpeTopSetEngine` + hook RPE_CAP · T-16 docs fidelidad · T-17 regla C11 · T-18 `MeetRecipeFitter keepExact` · T-20 onda RIR · T-21 modelos check-in · T-22 `WeeklyAdjustmentEngine` · T-23 hook en `ProgramProgressEngine` · T-24 hoja check-in [ANDROID] · T-25 aviso descarga · T-26 docs.
- **Ola C:** T-30…T-35 (revisión de bloque y continuidad) · T-80…T-84 (AUGE) · T-70…T-74 (rotación de variantes).
- **Ola D:** T-100…T-104 (sesiones no hechas; T-104 aplazada por screens-v2) · T-110…T-117 (competición) · T-120…T-124 (temporada).
- **Ola E:** T-40…T-43 (wizard) · T-60…T-62 (documentación y cierre; este documento cubre parte de T-62).
- Sin olvidar la lista de cierre de la §II.14 del plan al terminar cada ola.
