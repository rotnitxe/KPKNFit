# Sesiones estrictas de catálogo v2 — auditoría 2026-09-16 (remediación Fix 1-4)

## Estado de validación (remediación)

- `AprendeCatalogAuditTest` **VERDE**: el rojo era EOL (asset LF en git vs
  CRLF en disco). El test ahora normaliza CRLF→LF antes de hashear; la
  constante (`57c2a3…`) no se tocó. `.gitattributes` nuevo (`-text` en los dos
  assets de catálogo) evita futuros rewrites de EOL. El contenido del asset
  quedó intacto en todo momento.
- Suites dirigidas verdes (BUILD SUCCESSFUL): `SessionTemplateCatalogTest`
  (27, igualdad estricta contra canonical verbatim), `SessionTemplateEngineTest`
  (23: 20 + 3 nuevos de auditoría de APPEND), `SplitApplicationEngineTest`
  (22: 21 + 1 nuevo de diversidad fallback), `PlanMaterializerTest` (nombre
  verbatim + técnica como chip), `SessionCompositionPolicyTest`,
  `UltraFastEngineTest` (nombres verbatim + kernels), `CatalogDisplayNamesTest`
  (4, canonical verbatim compartido por configs hermanas),
  `SessionCatalogNameReconcilerTest` (4, incluye strip de sufijo a verbatim),
  `ExerciseDisplayNameTest` (8: 6 + 2 nuevos de técnica como chip),
  `WorkoutExerciseDisplayNameTest`, `ProtocolRecipeFidelityTest` (25, sin
  ediciones), `ProgramTemplateEngineTest`, `AprendeCatalogAuditTest`.
- Suite completa `testBaseDebugUnitTest` (sin filtro, 405 clases): **no
  completable en este entorno** — el proceso de test muere con exit 10
  (MessageIOException “Connection reset by peer” del executor Gradle) siempre
  en el mismo punto. Cobertura acumulada de XMLs: 58 clases / 235 tests /
  **0 fallos / 0 errores**. El crash es de infraestructura (executor/socket),
  no de aserciones: pasa incluso con el árbol limpio de tests nuevos.
- `scripts/catalog_v2_gate.py --strict` da `BLOCKED failures=2900` (mismatch
  editorial/muscle-notes en remos y presses): preexistente y fuera de alcance
  (fuentes de catálogo a medio curar en el WIP). Ningún cambio de este plan
  lo introduce: no se tocó `catalog/`, `ios-native/`, `backend/` ni scripts.

## Nombres verbatim del catálogo (endurecimiento 2026-09-16)

El nombre almacenado en `Exercise.name` es EXACTAMENTE el `canonicalName` de
la definición del catálogo (p.ej. "Press de Banca Plano"), sin sufijos de
implemento ("· barra"), sin técnica ("· Velocidad") y sin composición. La
desambiguación vive en los chips de `exerciseDisplayParts`:

- Chips de configuración = `catalogVariantChips` del adapter (p.ej. "Barra").
- Técnica de receta = `techniqueModifier` (p.ej. "Velocidad") como chip.
- `variantName` legacy solo se lee como chip cuando NO hay identidad v2
  (nunca inventa texto en ejercicios catalogados).

Archivos: `CatalogDisplayNames` (canonical verbatim),
`ExerciseCatalogV2LegacyAdapter` (name = canonicalName),
`ExerciseDisplayName.recipeTechniqueLabel` (técnica → chip),
`PlanMaterializer`/`PlRecipeSessionTemplates` (nombre verbatim),
`SessionCatalogNameReconciler` (strip de sufijos a verbatim),
`SessionCatalogV2Gate.catalog_name_divergence` (verbatim),
`SessionTemplateCatalogTest` (igualdad estricta verbatim en TODAS las
plantillas, incluido recipe-pl). Eliminada la tabla `CATALOG_TOKEN_LABELS`
de composición: no quedaba ningún consumidor.

Superficies verificadas (grep `\.name` en `screens/`): todas las que
muestran ejercicios de protocolo/sesión usan composición de display, no
`Exercise.name` crudo — `SessionCard`/`VolumeView` (`exerciseDisplayName`),
`ExerciseEditorCard` (`exerciseDisplayParts`), `TemplateCatalogCards`
(`exerciseDisplayParts` + chips), Home (`displayNameWithSelectedChips`),
workout vivo (`displayWorkoutExerciseName`). Los `exercise.name` restantes
son analítica/logs/voz/diagnóstico, no UI de ejercicios.

## Plan 2026-09-17 — duplicados suelto+grupo, verbatim total, chips UI

- **Fase 1 (D3)**: `collapseRedundantLooseExercises` pasa de todo-o-nada a
  eliminación por-id; `normalizeSessionStructure` corre en hydrator, guardado,
  `loadSessionInternal` del editor y lectura de ProgramDetail
  (`Program.normalizedSessionStructures`). Tests: espejo total/parcial,
  sueltos genuinos, cardio excluido, B/C/D+backup; `PlanMaterializerTest`
  itera TODAS las recetas visibles (intersección suelto↔parts vacía).
- **Fase 3**: `reconcilePublishedTemplateNames` corre para TODAS las SYSTEM
  (fin del carve-out recipe-pl); `TemplateCatalogCards` usa `exerciseDisplayParts`
  + chips solo de catálogo (eliminado `fallbackTechnicalVariantChips` que
  parseaba el id); test verbatim endurecido (mayúscula inicial, sin `__`).
- **Fase 4**: hero `weight(1f, fill=false)` + `widthIn(min=96.dp)` en
  `CompactHeroPill`; `testTag("macrocycle_history_button")` + test
  instrumentado `MacrocycleToolbarUiTest` (320dp, visible/habilitado/dentro
  de bounds, geometría legible, click abre historial).

## Remediación aplicada (plan 2026-09-16_remediacion)

- **Fix 1 (SHA/EOL)**: `AprendeCatalogAuditTest` normaliza EOL; `.gitattributes`
  con `-text` en ambos assets de catálogo.
- **Fix 2 (APPEND auditable)**: `SessionTemplateEngine.applyAppend` devuelve
  `TemplateApplyOutcome(session, omittedAppendExercises)` con configurationId,
  nombre y part de cada omitido; el part sobrevive si tiene
  `mobilitySeries`/`mobilityConfig` propios; `applyTemplate` delega
  (compat); el editor (`SessionEditorViewModelTemplates`) usa
  `applyTemplateAudited` e informa en el snackbar cuántos no se duplicaron.
  3 tests nuevos en `SessionTemplateEngineTest`.
- **Fix 3 (diversidad fallback)**: `suggestForDay` acepta `excludeIds` +
  `usedTemplates` (−60 focus-muscle, −40 focus-category, mismos pesos que
  `scoreCandidate`); `SplitApplicationEngine` acumula ambos a lo largo del
  loop (incluido el último recurso). Test nuevo en
  `SplitApplicationEngineTest` (primera llamada chest-b, segunda con
  diversidad back; sin diversidad repetiría).
- **Fix 4 (D2 cerrada)**: el parámetro `name` desaparece de `ex(...)` y de
  las 308 llamadas del fixture; `canonicalTemplateExerciseName` deriva del
  id. OJO técnico: el nombre del fixture en memoria NO puede resolverse
  desde `CATALOG_CONFIGURATION_NAMES`/`V3_CONFIGURATION_TOKEN_LABELS`
  durante `<clinit>` (NPE circular) → el fixture lleva un placeholder
  derivado del id y `reconcilePublishedTemplateNames` asigna el nombre final
  del catálogo en publicación. Grep de nombres inventados viejos
  ("Press de Banca con Barra", "Sentadilla Trasera Barra Alta",
  "Hip Thrust en Máquina", "Curl Predicador en Máquina") en `app/src/main`:
  0 matches.

## Estado de validación (corrida original del plan)

Corrida única `testBaseDebugUnitTest` (12 clases, 150 tests): **149/150 verdes**.
El único rojo es `AprendeCatalogAuditTest.approved_catalog_has_complete_aprende_ontology_and_editorial_coverage`
por SHA-256: esperado `57c2a3…` (blob HEAD) vs actual `943eb9…` (working tree).

**El drift es preexistente y ajeno a este plan**: el working tree ya traía
modificadas las fuentes `catalog/exercises/v2/source/families/chest_press.json`
(−554), `chest_fly.json` (+5/−553) y el `manifest.json` antes de empezar, y el
asset compilado `android-native/app/src/main/assets/exercise_catalog_v2.json`
está **intacto** (`git status` vacío en esa ruta; el `checkout` solo revirtió un
toque de line-endings). Verificado: blob HEAD = 515 configs / SHA `57c2a3…`;
working tree = 515 configs / SHA `943eb9…`. Los otros asserts del mismo test
(96 familias / 195 definiciones / 515 configs / cobertura editorial) pasan.

`scripts/catalog_v2_gate.py --strict` da `BLOCKED failures=2900` (mismatch
editorial/muscle-notes en remos y presses): también preexistente, misma causa
(fuentes de catálogo a medio curar en el working tree). Ningún cambio de este
plan lo introduce: no se tocó `catalog/`, `ios-native/`, `backend/` ni scripts.

## Decisiones editoriales (sustituciones de duplicados)

Las 4 plantillas con `DUPLICATE_CATALOG_CONFIGURATION` se corrigieron
sustituyendo la **segunda** ocurrencia (nunca borrando series):

| Plantilla | Duplicado | Sustituto | Por qué |
|---|---|---|---|
| `sys-push-beginner` (pb1-ex3) | `tren_superior_press_pecho_maquina_convergente__default` ×2 | `bench_press__machine` ("Press de Banca Plano · máquina") | Mismo patrón horizontal_push, máquina, principiante; H4 limpio (convergente no tiene replacementGroup) |
| `sys-upper-chest-beginner` (ucb1-ex2) | mismo convergente ×2 | `tren_superior_fondos__default` (fondos, bodyweight guiado) + ucb1-ex3 pasó a `standing_lateral_raise__machine` | Fondos: pectoralis MULTIARTICULAR sin replacementGroup; el lateral de pie evita apilar otro press y mantiene deltoides |
| `sys-pull-beginner` (plb1-ex5) | `preacher_curl__barbell` ×2 (dos grafías) | `standing_biceps_curl__dumbbells` 2×10 (era 2×12) | Bíceps AISLADO con mancuernas, estable para principiante; se bajó a 10 reps para no romper DIRECT_VOLUME_CAP (6) |
| `sys-upper-arms-beginner` (uab1-ex3) | mismo predicador ×2 | `standing_biceps_curl__dumbbells` 2×10 | Igual que arriba; alternancia bíceps/tríceps intacta |

## Fase 2 (plan 2026-09-17) — técnicas → configuraciones reales

Barrido de `technique =` en `data/protocols/definitions/*.kt` + `DayArchetypes.kt`
contra `catalog/exercises/v2/source/families/` (solo IDs existentes, D2):

| Modifier | Uso en recetas | Config real | Decisión |
|---|---|---|---|
| `PAUSE_2S` en `BP` | histórico (dato viejo) | `paused_bench_press__barbell` ("Press de Banca con Pausa") | **Sustituido**: reconciler remapea + `plSquat/bp-pause` ya usa `BP_PAUSE` sin técnica |
| `PAUSE_2S` en `SQ_HIGH` | `ClassicPlProtocols.withSquatTech` | NO existe paused-squat en catálogo | **Chip** "Pausa 2s", nombre verbatim de `SQ_HIGH` |
| `CLOSE_GRIP` en `BP` | `MadcowNsunsGzcl:146`, `TexasWendlerProtocols:150` | NO existe `bench_press__close*` (solo barbell/dumbbells/smith/machine/cable/kettlebell + paused def) | **Chip** "Agarre cerrado", nombre verbatim |
| `SPEED` | Westside/PHAT/Cube/recipe-pl + `TexasWendler:73` (Pendlay) | método/olas, sin config equivalente | **Chip** "Velocidad"; el rol SPEED ya distingue |
| `TO_KNEES` en `DL` | `JuggernautSheikoSmolov:175` | sin equivalente | **Chip** "Hasta rodillas" |
| `BOX` sobre `SQ_BOX` | `JuggernautSheikoSmolov:428` | `quads_sentadilla_cajon__default` YA es cajón | **Quitado** (redundante) |
| `DEFICIT` sobre `DL_DEF` | histórico | `hams_peso_muerto_convencional_deficit__default` YA es déficit | **Quitado** si solo duplica (no hay uso con modifier hoy) |

`isCompetitionLift` es flag del mismo `Exercise`, nunca un ejercicio aparte (D4).
Tests nuevos: `no_visible_recipe_repeats_variant_config_as_technique` y
`every_visible_recipe_configuration_exists_in_catalog` (fidelidad intacta).
Gate `catalog_v2_gate.py --strict`: BLOCKED 2910 (base preexistente 2900 +
10 editoriales de `paused_bench_press` en fuentes a medio curar; fuera de
alcance, no introducido por este plan).

## Fase 4 — técnicas vs configuraciones (auditoría original 2026-09-16)

Barrido de `technique =` en `data/protocols/definitions/*.kt` + `DayArchetypes.kt`:

- `SPEED` (Westside/PHAT/Cube): semántica de receta (olas al 60-68 %), sin
  equivalente aprobado → **se mantiene**. Exento de `S_duplicate_slot` porque el
  nombre materializado difiere ("· Velocidad").
- `PAUSE_2S` (`DayArchetypes.plBenchVolume`, ClassicPL): tempo de receta, sin
  config aprobada equivalente → **se mantiene**.
- `CLOSE_GRIP` (nSuns T2, 5/3/1 assistance): **no existe** `bench_press__close_grip__*`
  aprobado en el asset → **se mantiene** como TechniqueModifier. Si curaduría
  aprueba esa config, el slot debe migrar a `CatalogIds` (regla "Nunca inventar IDs").
- `TO_KNEES` (Sheiko), `BOX` (Sheiko/Westside → `SQ_BOX` ya es config propia),
  `DEFICIT` (`DL_DEF` ya es config propia): receta o ya migrados → **sin cambios**.
- `ProtocolRecipeFidelityTest` verde **sin editar sus aserciones**.

## Paridad iOS/backend

Deuda documentada: el nombre derivado vive en `domain/exercises/catalogv2/`
(Android). La identidad v2 viaja igual en ambas plataformas; solo el display
difiere hasta portar `CatalogDisplayNames` a Swift. No se tocó `ios-native/`.

## Archivos

- Núcleo: `domain/exercises/catalogv2/CatalogDisplayNames.kt`,
  `SessionCatalogNameReconciler.kt`
- Puertas: `SessionTemplateQualityRules.kt` (P0 `DUPLICATE_CATALOG_CONFIGURATION`),
  `SessionCatalogV2Gate.kt` (`catalog_name_divergence`),
  `SessionCompositionPolicy.kt` (SOFT `S_duplicate_slot`)
- Dedup: `SessionTemplateEngine.kt` (APPEND filtra), `SessionTemplateSuggestionEngine.kt`
  (`suggestForDay`), `SplitApplicationEngine.kt` (fallback por sugeridor)
- Nombres: `CatalogCompositionMetadataProvider.kt`, `ExerciseCatalogV2LegacyAdapter.kt`
  (`derivedLegacyName` + `toConfigurationDisplayNameLookup`), `ExerciseDatabase.kt`
  (cache + accessors), `SessionTemplates.kt` (lookup del asset + `reconcilePublishedTemplateNames`,
  `V3_DISPLAY_NAMES` eliminado), `PlRecipeSessionTemplates.kt` (derivado + técnica),
  `PlanMaterializer.kt` (hard failure sin metadata, sin fallback a id crudo)
- Auto-reparación: `WorkoutSessionHydrator.kt`, `SessionEditorViewModelNavigation.kt`
- Colateral: `UltraFastConfig.kt` (ids v2 reales + kernels "banca"/"squat")
