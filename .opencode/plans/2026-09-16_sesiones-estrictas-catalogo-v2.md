---
flags: []
---

# Sesiones estrictamente de catálogo v2: sin duplicados y sin nombres inventados

Plan para el pipeline OpenCode (orquestador → constructor → auditor).

## Contexto

### Diagnóstico (verificado en código)

Síntoma reportado: las sesiones generadas desde protocolos y plantillas contienen ejercicios duplicados y ejercicios con nombres que no existen en el catálogo.

Hechos confirmados por auditoría de código (rutas relativas a `android-native/app/src/main/java/com/example/kpkn/` salvo indicación):

- La identidad v2 ya existe y es correcta; el nombre display es texto libre independiente. `Exercise` (`data/models/Session.kt:269-339`) lleva `catalogRevision`/`catalogDefinitionId`/`catalogConfigurationId`/`performanceProfileId`/`occurrenceId` MÁS un `name` libre. El gate de guardado `SessionCatalogV2Gate` (`data/exercises/catalogv2/SessionCatalogV2Gate.kt`) valida identidad v2 pero jamás valida el nombre.
- ~308 nombres libres hardcodeados en el fixture legacy de `data/sessions/SessionTemplates.kt` (líneas 334-3272), muchos contradictorios con su propio `configurationId` (ej.: "Hip Thrust en Máquina" con `hip_thrust__bilateral__barbell` línea 621; "Curl Martillo de Pie con Mancuernas" con `standing_biceps_curl__barbell` línea 430; "Curl Femoral Sentado" con `curl_isquios_con_sliders__default` ×15 usos).
- El mapa `V3_DISPLAY_NAMES` (`SessionTemplates.kt:3296-3377`, 80 entradas) contiene nombres que no existen en el asset (`exercise_catalog_v2.json` nombra a nivel de definición: "Press de Banca Plano", no "Press de Banca con Barra"). El compilador V3 (`rebuildV3Template`, líneas 3770-3840) reescribe todos los nombres vía `spanishConfigurationDisplayName` (3489-3505): `V3_DISPLAY_NAMES` → nombre de catálogo → tokens → "Ejercicio de catálogo".
- El test `systemTemplateNamesMatchDatabase` (`SessionTemplateCatalogTest.kt:100-117`) solo aserta `isNotBlank` + existencia de entrada canónica; no compara `exercise.name` contra el catálogo. Por eso los nombres inventados pasan el gate.
- Duplicados de configuración en plantillas publicadas (mismo `catalogConfigurationId` dos veces en la misma plantilla): `sys-push-beginner` (líneas 2009+2013, `tren_superior_press_pecho_maquina_convergente__default`), `sys-upper-chest-beginner` (2473+2475), `sys-pull-beginner` (2087+2091, `preacher_curl__barbell` con DOS grafías libres distintas), `sys-upper-arms-beginner` (2809+2813, `preacher_curl__barbell`). Tras el rewrite V3 ambos colapsan al MISMO nombre → duplicado visible. `SessionTemplateQualityRules` solo tiene P0 por `occurrenceId` repetido (174-181), no por `configurationId`.
- `SessionTemplateEngine.applyAppend` (`domain/templates/SessionTemplateEngine.kt:272-281`) concatena `target.parts + cloned.parts` sin filtro → APPEND de plantilla duplica configuraciones ya presentes.
- Ruta receta: `PlanMaterializer.materializeSlot` (`domain/training/PlanMaterializer.kt:279-349`) construye el nombre como `metadata.displayName` + técnica; `CatalogCompositionMetadataProvider` devuelve `definition.canonicalName`; sin metadata el nombre visible es el id crudo. `materializeDay` (235-260) mapea slots 1:1 sin dedup (duplicados deliberados existen: PHAT usa `CatalogIds.BP` dos veces en "Power Upper", `data/protocols/definitions/BodybuildingNativeProtocols.kt:123-124`; Sheiko repite `SQ_LOW` — asertado en `ProtocolRecipeFidelityTest`).
- Colateral: `UltraFastEngine.PROTECTED_CATALOG_IDS` (`domain/sessionassistant/UltraFastConfig.kt:31-48`) contiene ids legacy inexistentes en v2 y el kernel "press banca" no es subcadena de los nombres v2 → la protección de básicos nunca aplica sobre plantillas v2.
- Estructura del catálogo (fuente de la solución): `catalog/exercises/v2/source/families/*.json` → cada definición tiene `canonicalName` específico ("Press de Banca Plano") y cada configuración tiene `displaySummary` ("barbell"). Asset compilado: 96 familias / 195 definiciones / 511 configuraciones, revisión `v2-approved-2026-08-12-a`.
- Restricciones duras: `AprendeCatalogAuditTest` fija el SHA-256 del asset → NO modificar `exercise_catalog_v2.json` ni las fuentes del catálogo. `scripts/catalog_v2_gate.py` (`legacy_consumer_gate`) prohíbe patrones de resolución por nombre (`.name.equals(exerciseName)`) → cualquier código nuevo debe derivar nombre DESDE id, nunca id desde nombre.

## Objetivo

- El `name` de todo ejercicio catalogado en una sesión (generada por protocolo, plantilla o editor) se deriva determinísticamente del catálogo v2 (definición + configuración). Cero texto libre de nombres de ejercicio en código de producto.
- Ninguna sesión generada contiene el mismo `catalogConfigurationId` dos veces, salvo slots deliberados de receta con técnica distinta (fidelidad de protocolo: Sheiko/PHUT se preservan).
- Puertas mecánicas (quality rules P0 + gate de guardado) que impidan regresiones.
- Sesiones históricas persistidas se auto-reparan al cargar/guardar (sin migración Room).

## Decisiones de diseño (opinionadas, no negociables salvo bloqueo técnico)

- D1 — Nombre derivado: `displayName = definition.canonicalName`, con sufijo de desambiguación " · " + `tokenLabel(displaySummary)` SOLO cuando la definición tiene >1 configuración. `tokenLabel` = traducción determinista de tokens del catálogo (reutilizar `V3_CONFIGURATION_TOKEN_LABELS` como tabla de traducción de tokens, NO como tabla de nombres). Si el token no está en la tabla, se usa el token crudo. Prohibido añadir entradas que no correspondan a tokens reales del asset.
- D2 — `V3_DISPLAY_NAMES` se elimina por completo (80 entradas). El fixture legacy deja de pasar nombres: `ex(...)` deja de aceptar nombre libre para ejercicios de catálogo (el parámetro se ignora/elimina y el compilador V3 ya lo reescribía).
- D3 — Fallback de cold-start: si el índice del catálogo aún no está cargado, `spanishConfigurationDisplayName` cae a definition-by-token como hoy, pero una regla de auditoría P1 marca cualquier plantilla que haya necesitado el fallback cuando el catálogo SÍ está instalado (en producción y tests siempre está: MainActivity paso 9 precarga el asset).
- D4 — Duplicados en plantillas = P0 (`DUPLICATE_CATALOG_CONFIGURATION`). Se corrigen las 4 plantillas afectadas sustituyendo la segunda ocurrencia por OTRA configuración aprobada del catálogo (elección editorial que respete el balance muscular y pase H1-H10), nunca borrando series a lo tonto.
- D5 — Recetas no se mergean en silencio: la fidelidad de protocolo es ley (catálogo de regresiones 2026-09-13: `ProtocolRecipeFidelityTest` vigila Sheiko día a día). Se añade hallazgo SOFT `S_duplicate_slot` (misma config + misma técnica en un día) que REPORTA sin bloquear; duplicados con técnica distinta (PHAT speed vs T1) quedan exentos porque producen nombres distintos ("Press de Banca Plano · Velocidad" vs "Press de Banca Plano").
- D6 — Auto-reparación en hidratación/guardado: función pura que re-deriva `name` para todo ejercicio con `catalogConfigurationId` resoluble (y sin prefijo `custom:`). Se aplica en la hidratación de workout y antes del gate de guardado del editor. Sin migración Room (los nombres viven en blobs JSON de `ProgramEntity`/`SessionTemplateEntity`/`OngoingWorkoutEntity`; el esquema no cambia).
- D7 — Ejercicios custom (`custom:`) siguen existiendo SOLO como creación manual del usuario; jamás en generación automática (ya garantizado por `autoGenerationEligible` + gate P0/P1).
- D8 — APPEND no duplica: al aplicar plantilla en modo APPEND, los ejercicios cuyo `catalogConfigurationId` ya exista en la sesión objetivo se omiten (log/audit de lo omitido).

## Implementación por fases

### Fase 1 — Derivación canónica de nombres (núcleo)

- Nuevo archivo `domain/exercises/catalogv2/CatalogDisplayNames.kt` (puro, sin `android.*`):
  - `fun configurationDisplayName(catalog: ExerciseCatalogV2, configurationId: String): String?` — implementa D1.
  - `fun buildDisplayNameIndex(catalog: ExerciseCatalogV2): Map<String, String>` — `configurationId → nombre derivado`.
  - KDoc explícito: "el nombre visible siempre se deriva del id de configuración; nunca al revés" (compatibilidad con `catalog_v2_gate.py`).
- `data/exercises/catalogv2/ExerciseCatalogV2LegacyAdapter.kt`: exponer `toConfigurationDisplayNameLookup(): Map<String, String>` delegando en `CatalogDisplayNames`.
- `data/exercises/ExerciseDatabase.kt`: cachear el lookup junto a `v2ConfigurationLookupCache` (mismo patrón de process-cache, ver `CatalogV2ProcessCache`); accessor `catalogConfigurationDisplayName(configurationId): String?`.
- `data/sessions/SessionTemplates.kt`:
  - Eliminar `V3_DISPLAY_NAMES` (3296-3377).
  - `spanishConfigurationDisplayName` (3489-3505): orden nuevo = lookup derivado del catálogo → (cold-start) tokens → "Ejercicio de catálogo". Sin mapa de nombres.
  - `V3_CONFIGURATION_TOKEN_LABELS` se mantiene SOLO como traducción de tokens de `displaySummary`/id (documentar el cambio de rol).
- `domain/training/CompositionMetadataHolder.kt` + `CatalogCompositionMetadataProvider` (`data/exercises/catalogv2/CatalogCompositionMetadataProvider.kt`): `displayName` pasa de `definition.canonicalName` a `CatalogDisplayNames.configurationDisplayName(...)` (con disambiguación por configuración), de modo que `PlanMaterializer` sigue consumiendo metadata sin acoplarse a `ExerciseDatabase`.
- `domain/training/PlanMaterializer.kt` `materializeSlot` (279-349): `name = derivedName + técnica.displayName()`; eliminar el fallback `ifBlank { configurationId }` del nombre visible (si el id no resuelve en catálogo debe ser hard failure de validación, no un nombre feo silencioso).
- `data/sessions/PlRecipeSessionTemplates.kt` `recipeExercise` (304-334): ya delega en `systemTemplateDisplayName` — verificar que hereda la nueva cadena.

### Fase 2 — Dedup de configuraciones

- `domain/templates/SessionTemplateQualityRules.kt`: nueva regla P0 `DUPLICATE_CATALOG_CONFIGURATION` — dos ejercicios en la misma plantilla con igual `catalogConfigurationId` (aplica a SYSTEM; USER ya queda fuera del generador si tiene P0). Documentar severidad y mensaje.
- Corregir las 4 plantillas del fixture (líneas de `SessionTemplates.kt`): `sys-push-beginner` (2009/2013), `sys-upper-chest-beginner` (2473/2475), `sys-pull-beginner` (2087/2091), `sys-upper-arms-beginner` (2809/2813). Sustituir la configuración duplicada por otra aprobada del catálogo del mismo grupo muscular/patrón (usar `replacementGroup` del profile v2 como guía); listar la elección editorial en `docs/audits/2026-09-16-catalog-strict-sessions/README.md`.
- `domain/templates/SessionTemplateEngine.kt` `applyAppend` (272-281): filtrar ejercicios clonados cuyo `catalogConfigurationId` ya exista en la sesión objetivo (D8); los `custom:` no se filtran.
- `domain/training/SessionCompositionPolicy.kt`: hallazgo SOFT `S_duplicate_slot` — mismo `configurationId` + mismo `TechniqueModifier` en slots del mismo día (D5). Actualizar `SessionCompositionPolicyTest`.
- (Secundario) `domain/training/SplitApplicationEngine.kt` fallback per-día (415-437): enrutar el fallback por el sugeridor (`SessionTemplateSuggestionEngine`) para heredar la penalización de diversidad entre días del mismo arquetipo, en vez de elegir plantilla independientemente por día.

### Fase 3 — Puertas de guardado y auto-reparación de sesiones existentes

- `data/exercises/catalogv2/SessionCatalogV2Gate.kt`: nueva condición `catalog_name_divergence` — ejercicio no-custom con `catalogConfigurationId` resoluble cuyo `name ≠ nombre derivado`. Mensaje accionable ("El nombre no coincide con el catálogo; se corrigió al guardar").
- Nueva función pura `Session.reconcileCatalogExerciseNames(displayNameIndex)` (en `domain/workout/` o extensión en `data/models/`): para cada ejercicio (planos y en parts) con identidad v2 resoluble y sin prefijo `custom:`, reescribe `name` (y solo `name`) con el derivado + `variantName`/técnica existente. Tests unitarios propios.
- Puntos de aplicación de la reconciliación:
  - `screens/workout/WorkoutSessionHydrator.kt` dentro de `normalizedIdentityFields()` (~311 y ~445).
  - `screens/sessioneditor/SessionEditorViewModelNavigation.kt` antes del gate (275-286): reconciliar ANTES de evaluar `catalogV2SelectionIssues()` para que el guardado auto-cure en vez de bloquear al usuario por datos viejos.
- `domain/sessionassistant/UltraFastConfig.kt` (31-48): reemplazar `PROTECTED_CATALOG_IDS` legacy por ids v2 reales (`high_bar_back_squat__barbell`, `bench_press__barbell`, `conventional_deadlift__bilateral__barbell`, etc.) y afinar kernels para que matcheen nombres derivados v2 ("press de banca plano" contiene "banca"). Test dirigido.

### Fase 4 — Variantes de receta = configuraciones del catálogo (no inventar)

- Auditar `data/protocols/definitions/*.kt` + `data/protocols/DayArchetypes.kt`: localizar todo `technique = <Modifier>` donde el catálogo apruebe una configuración equivalente (p.ej. agarre cerrado: si existe `bench_press__close_grip__*` aprobado, el slot debe usar esa `configurationId` vía `CatalogIds`, no `technique = CLOSE_GRIP` sobre `bench_press__barbell`).
- Sustituir SOLO donde exista equivalente aprobado; donde no exista, el `TechniqueModifier` se mantiene (está permitido: es semántica de receta, no identidad). El listado de decisiones va al doc de auditoría de la Fase 2.
- `data/protocols/CatalogIds.kt`: añadir los ids nuevos que se necesiten (solo ids existentes en el asset; "Nunca inventar IDs" ya es la regla del archivo).
- `ProtocolRecipeFidelityTest` DEBE seguir verde sin ediciones de sus aserciones (si una sustitución rompe fidelidad editorial, revertir esa sustitución y documentarla como excepción).

### Fase 5 — Tests

Nuevos/extendidos (bajo `android-native/app/src/test/java/com/example/kpkn/`):

- `domain/templates/SessionTemplateCatalogTest.kt`:
  - `systemTemplateNamesMatchDatabase`: fortalecer → `exercise.name == displayNameIndex[configurationId]` para TODAS las plantillas SYSTEM (este es el test que hoy no compara y dejó pasar el bug).
  - Nuevo: `systemTemplatesHaveNoDuplicateCatalogConfigurations` (misma config dos veces en una plantilla = fallo).
  - Nuevo: `v3CompilerNeverUsesColdStartFallback` con índice instalado (cero nombres tokenizados/"Ejercicio de catálogo").
- `domain/exercises/catalogv2/CatalogDisplayNamesTest` (nuevo): derivación determinista, disambiguación solo con >1 config, sin nombres fuera del catálogo, 511/511 ids resolubles.
- `domain/training/PlanMaterializerTest`: nombres materializados = derivados (con y sin técnica); ningún nombre = id crudo.
- Gate: test de `catalog_name_divergence` + reconciliación (bloquea sin reconciliar, pasa después de `reconcileCatalogExerciseNames`).
- `domain/templates/SessionTemplateEngineTest`: APPEND no duplica `configurationId`.
- `domain/training/SessionCompositionPolicyTest`: `S_duplicate_slot` (positivo PHUT speed-vs-main exento, Sheiko reportado como SOFT sin bloquear).
- `domain/sessionassistant/UltraFastEngineTest` (o test dirigido de config): protección aplica con ids v2.
- Suites existentes que DEBEN seguir verdes (regresiones vigiladas): `ProtocolRecipeFidelityTest`, `ProtocolCompositionContractTest`, `ProtocolClaimsTest`, `ProgramProtocolEngineTest`, `ProgramTemplateEngineTest`, `SessionCompositionPolicyTest` (H1-H11), `ExerciseCatalogContractTest`, `ExerciseCatalogAuditTest`, `AprendeCatalogAuditTest`, `ProgramAutoregulationEngineTest`, `SessionTemplateCatalogTest` completo.

Antes de tocar código: grep de tests que asuman nombres viejos de plantilla (p.ej. asserts con "Press de Banca con Barra") y actualizarlos a nombres derivados en el MISMO commit.

## Rutas

Creación:

- `android-native/app/src/main/java/com/example/kpkn/domain/exercises/catalogv2/CatalogDisplayNames.kt`
- `android-native/app/src/test/java/com/example/kpkn/domain/exercises/catalogv2/CatalogDisplayNamesTest.kt`
- `docs/audits/2026-09-16-catalog-strict-sessions/README.md`

Modificación (orden de trabajo sugerido):

- `data/exercises/catalogv2/ExerciseCatalogV2LegacyAdapter.kt`
- `data/exercises/ExerciseDatabase.kt`
- `data/sessions/SessionTemplates.kt` (borrar `V3_DISPLAY_NAMES`, reescribir `spanishConfigurationDisplayName`, corregir 4 plantillas)
- `data/sessions/PlRecipeSessionTemplates.kt`
- `data/exercises/catalogv2/CatalogCompositionMetadataProvider.kt`
- `domain/training/CompositionMetadataHolder.kt` (si firma cambia)
- `domain/training/PlanMaterializer.kt`
- `domain/templates/SessionTemplateQualityRules.kt`
- `domain/templates/SessionTemplateEngine.kt`
- `domain/training/SessionCompositionPolicy.kt`
- `domain/training/SplitApplicationEngine.kt` (fallback por sugeridor)
- `data/exercises/catalogv2/SessionCatalogV2Gate.kt`
- `data/models/Session.kt` o `domain/workout/` (función reconcile — sin tocar esquema)
- `screens/workout/WorkoutSessionHydrator.kt`
- `screens/sessioneditor/SessionEditorViewModelNavigation.kt`
- `domain/sessionassistant/UltraFastConfig.kt`
- `data/protocols/CatalogIds.kt` + `data/protocols/definitions/*.kt` + `data/protocols/DayArchetypes.kt` (Fase 4)
- Tests: `SessionTemplateCatalogTest`, `PlanMaterializerTest`, `SessionCompositionPolicyTest`, `SessionTemplateEngineTest`, nuevos listados.

Zonas sensibles kpkn-gate: NINGUNA (no se toca `services/workout/`, `data/db/`, `domain/auge/`, `domain/nutrition/`, `ios-native/`, `backend/`). No hay cambio de esquema Room: los nombres viven en los blobs JSON existentes y la reparación es en carga/guardado.

## Impacto

- Usuario: todas las sesiones generadas muestran nombres canónicos del catálogo ("Press de Banca Plano · Barra" en vez de grafías libres); desaparecen los duplicados aparentes (misma config con dos grafías) y los reales (4 plantillas); las sesiones históricas se corrigen al abrirlas/guardarlas.
- Datos: sin migración Room; `ProgramEntity`/`SessionTemplateEntity`/`OngoingWorkoutEntity` siguen con el mismo esquema v26; el JSON de sesión cambia el campo `name` de ejercicios catalogados (auto-reparable en ambos sentidos: derivable del id).
- Agregación histórica: el historial y las sugerencias se keyean por `canonicalExerciseKey`/ids (`resolvedCanonicalExerciseId`), no por nombre visible; `WorkoutTagResolver` aísla por UUID (regresión 2026-09-12) → el renombrado NO fragmenta historial. Verificar con test dirigido.
- Activos: `exercise_catalog_v2.json` INTOCADO (SHA-256 fijado por `AprendeCatalogAuditTest`); fuentes de `catalog/exercises/v2/` intactas.
- iOS: paridad de nombre derivado queda como deuda documentada (el port Swift ya arrastra la deuda de export de `SessionTemplates`); no se toca `ios-native/` aquí.
- Voz/AUGE: sin cambios de fórmulas; la resolución de métricas sigue siendo por id (mejora: menos fallbacks por nombre al normalizar).

## Pruebas

Desde `android-native/` (wrapper anti-hang; nunca `gradlew.bat` sin `--no-daemon --console=plain`):

```powershell
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.SessionTemplateCatalogTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.CatalogDisplayNamesTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.PlanMaterializerTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.SessionCompositionPolicyTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.ProtocolRecipeFidelityTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.AprendeCatalogAuditTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "compileBaseDebugKotlin"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest"
```

Además (gate del repo, desde la raíz):

```powershell
python scripts/catalog_v2_gate.py --strict
```

QA manual mínimo en emulador (APK con `--no-incremental`, respaldando perfil antes de instalar sobre uno poblado):

- Crear programa desde una plantilla → verificar nombres canónicos y ausencia de duplicados por sesión y entre sesiones del mismo día-etiqueta.
- Aplicar protocolo (PHUT y uno de powerlifting) → nombres derivados, variantes de técnica visibles, sin ids crudos.
- Abrir una sesión vieja con nombres inventados → se muestra corregida; guardar → gate pasa.
- Editor: APPEND de plantilla sobre sesión con solapes → no hay duplicados.

## Riesgos

- Cambios de nombres visibles en masa → usuarios ven nombres distintos de golpe. Mitigación: son los nombres reales del catálogo; sesiones viejas se reconcilian al vuelo. Alternativa (banner de "nombres actualizados") descartada por ruido.
- Tests que asumen nombres viejos → puede haber asserts literales en suites no listadas. Mitigación: grep previo de strings de nombres en `app/src/test` y corrección en el mismo commit.
- Fidelidad de protocolos (regresión vigilada 2026-09-13) → cualquier sustitución de Fase 4 que altere la receta publicada debe revertirse; `ProtocolRecipeFidelityTest` sin ediciones es el árbitro.
- `catalog_v2_gate.py` prohíbe resolución por nombre → todo el código nuevo deriva nombre DESDE id; jamás buscar id por nombre. Revisar el reporte del gate en CI.
- Fallback de cold-start si alguna ruta construye plantillas antes de `initializeExerciseDatabase` (p.ej. tests JVM sin soporte de catálogo) → mantener `CatalogCompositionTestSupport` instalando el provider; el nuevo test `v3CompilerNeverUsesColdStartFallback` lo vigila.
- H1-H11 → las 4 plantillas corregidas deben re-pasar `systemTemplatesPassSessionCompositionH1ToH10`; elegir sustitutos con `replacementGroup` coherente.
- Paridad iOS/backend desalineada temporalmente en nombre derivado (solo display, la identidad v2 ya viaja igual). Deuda documentada, no bloqueante.
- Tamaño → Fases 1-3 son el núcleo (duplicados + nombres); la Fase 4 (técnicas → configs) puede ejecutarse como ronda separada si el constructor la ve crecer; NO recortar las puertas de tests.
