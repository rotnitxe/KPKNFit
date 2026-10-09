# D2b · Catálogo: 20 programas nuevos (en bloques y periodizados) y su construcción con el periodizador (punto 12; §3‑bis A)

Worktree indicado en tu encargo, rama `v3/d2b` (sale de `feat/wizard-v3` con D1 y D2a integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md`, del plan «Punto 12» y «§3‑bis A», y los briefs `D1.md` (nombres y kicker) y `D2a.md` (periodizador).
Aquí NO se ofrecen todavía en PLAN (eso es D2c): solo existen, se resuelven por id, tienen ficha y se construyen bien.

## Contexto (verificado)
- Los 10 programas del generador son entradas NATIVE no listadas `generated:<sourceId>` (`data/programs/PersonalizedPlanCatalog.kt:255-325`:
  `GENERATED_PREFIX`, `GeneratedSpec`, `generatedSpecs`, `GENERATED_IDS`, `generatedEntry`) con su ficha en
  `data/programs/PlanEditorialTable.kt:224-330` (helper `generated(...)` ~1020).
- `domain/onboarding/GeneratedPlans.kt:34-91`: `entryIdFor(profile)`, `modeOf(entryId)` (hoy compara el sufijo entero), `isGenerated`.
- El programa se arma en el ViewModel: `generatedRoutineFor` (~2978, barrido de candidatos) y `materializeGenerated` (~2994, vista previa
  y activación), que llaman a `RoutineGenerator.generate(draft.routineRequest(mode, catalog))` y luego a `generatedProgramOf`
  (`screens/onboarding/SetupGeneratedPlans.kt:112-145`, que asigna el lugar de cada sesión por `session.id`).
- Periodizador (D2a, en tu rama): `domain/training/periodization/` (`RoutinePeriodizer.build(request, base, variant, horizon)` →
  `PeriodizedRoutine(program, phases, notes)`, `ProgramVariant`, `Horizon.Weeks`). Lee sus firmas reales antes de usarlo; las sesiones de
  cada semana llevan el sufijo `-w<n>`, el id del programa el sufijo `-blocks`/`-periodized` y los bloques
  `sourceDefinitionId = "generated:<modo>:<variante>"` (misma convención que el generador). Sin fecha, `PeriodLayout` lanza
  `IllegalArgumentException` fuera de 4–8 semanas (bloques) o de 10/12/16 (periodizado): usa solo 6 y 12.

## 1. Ids y traducción (`GeneratedPlans.kt`)
- Ids nuevos: `generated:<sourceId>:blocks` y `generated:<sourceId>:periodized` para los 10 `sourceId` de hoy. `generated:<sourceId>` sigue
  siendo el simple (es el `structureTemplateId` que guardan los programas ya activos: no cambia).
- `modeOf(entryId)` acepta el sufijo de variante; `variantOf(entryId): ProgramVariant?` (SIMPLE sin sufijo); `entryIdFor(profile,
  variant = ProgramVariant.SIMPLE)`. `"generated:"`, `"generated:foo"` y `"generated:strength-muscle:otra"` devuelven null.
- Busca con Grep todos los usos de `GENERATED_PREFIX`, `GENERATED_IDS`, `isGenerated` y `modeOf` y comprueba que cada uno hace lo correcto
  con un id de variante. Si algo depende de que `GENERATED_IDS` tenga 10 elementos, no la cambies: añade `GENERATED_VARIANT_IDS` (20).

## 2. Entradas del catálogo (`PersonalizedPlanCatalog.kt`)
- Una entrada por variante, NATIVE, no listada, como `generatedEntry`: `duration = FINITE_CYCLE`, `durationWeeks` 6 (bloques) o 12
  (periodizado), `technicalSubtitle` con `PlanLabels.subtitle(...)` de esa duración, mismas `references`/`capabilities` que su simple.

## 3. Fichas (`PlanEditorialTable.kt`) · nombres a validar con el usuario al cerrar
| `sourceId` | `:blocks` (6 semanas) | `:periodized` (12 semanas) |
|---|---|---|
| strength-muscle | Escalera de carga | Temporada de fuerza y masa |
| hybrid | Motor y fuerza | Temporada híbrida |
| functional | Fuerza para la vida | Temporada funcional |
| powerbuilding | Pesado y bombeado | Temporada powerbuilding |
| calisthenics | Progresiones en bloques | Ruta de habilidades |
| bodybuilding | Bloques de hipertrofia | Temporada de culturismo |
| weightlifting-base | Ondas olímpicas | Ciclo de halterofilia |
| armwrestling | Bloques de mesa | Camino a la mesa |
| strongman | Carga y acarreo | Temporada de strongman |
| powerlifting | Ondas de fuerza | Camino al pico |

- Resumen (≤ 300 caracteres, español neutro, honesto, sin «KPKN» ni «a medida») que diga la estructura real que arma el periodizador para
  ese perfil. Ejemplos de tono:
  - «Escalera de carga»: «Seis semanas en dos bloques: tres de volumen para acumular trabajo, dos de intensidad con más peso y una de descarga
    para llegar fresco. Se arma con tu material, tus días y tu tiempo.»
  - «Camino al pico»: «Doce semanas hacia tu mejor sentadilla, banca y peso muerto: volumen, intensidad, dos semanas de pico, descarga y una
    semana de test. Si declaraste tus marcas, las cargas salen de ellas.»
- Culturismo y Funcional no tienen realización (D2a): su resumen no habla de pico. Calistenia y Armwrestling solo cambian series: no prometas
  más peso.
- Término de glosario nuevo `PlanTerm.BLOCKS` («Bloques: tramos de semanas con un objetivo, como acumular volumen o subir la intensidad»;
  busca dónde viven los textos de los demás términos y sigue su estilo), en las fichas `:blocks` y `:periodized` (estas, además, con `DELOAD`).
- `rank` únicos (710–729). Ninguno repite nombre con ningún otro programa (la prueba de D1 `PlanNamingUniquenessTest` debe seguir pasando:
  amplíala con estos 20).

## 4. Construcción con el periodizador
- Función pura nueva en `SetupGeneratedPlans.kt`: `generatedVariantProgram(routine, entry, draft, request): Program`. Con
  `variantOf(entry.id) == SIMPLE`, lo de hoy (`generatedProgramOf`). Con BLOCKS o PERIODIZED: `RoutinePeriodizer.build(request,
  routine.program, variant, Horizon.Weeks(6 o 12))` y, sobre ese programa, lo mismo que hace `generatedProgramOf` (opciones del alta, nombre,
  modo, `structureTemplateId = entry.id`, perfil de marcas, prioridades). El lugar de cada sesión se busca por el id BASE de la sesión
  (sin el sufijo `-w<n>`).
- `materializeGenerated` usa esa función (es el único cambio en el ViewModel). El barrido de candidatos (`generatedRoutineFor`) sigue
  evaluando la semana base: la variante se aplica solo al materializar.
- Si el periodizador devuelve notas (p. ej. una sesión que no cabe), pásalas por donde ya viajan las notas del generador; si no hay camino,
  anótalo en el informe en lugar de inventarlo.

## Archivos permitidos
`domain/onboarding/GeneratedPlans.kt`, `data/programs/{PersonalizedPlanCatalog, PlanEditorialTable, PlanEditorial}.kt`,
`screens/onboarding/SetupGeneratedPlans.kt`, `screens/onboarding/SetupWizardViewModel.kt` (solo `materializeGenerated`) y pruebas.

## Pruebas
- Nueva `GeneratedVariantsCatalogTest` (JVM): los 30 ids se resuelven con `PersonalizedPlanCatalog.find`; `modeOf`/`variantOf` de cada uno; los
  ids malformados dan null; ninguna de las 20 se lista; nombres únicos.
- Nueva `GeneratedVariantProgramTest` (JVM, construye el pedido y el catálogo como `RoutineGeneratorBehaviorTest`): `strength-muscle:blocks` da un
  programa COMPLEX de 6 semanas con el `structureTemplateId` de la variante; `:periodized` 12 semanas; el simple sale idéntico al de hoy; los
  lugares por sesión se conservan en todas las semanas.
- Actualiza las fijadas que cambien por el conteo (explica cada una): `GeneratedPlansTest`, `PersonalizedPlanCatalogTest`,
  `PlanCatalogEditorialContractTest`, `ProgramNamingTest`, `PlanNamingUniquenessTest`.
- Filtro: esas clases + `*RoutinePeriodizer*`. Antes, `:app:compileDebugKotlin`.
