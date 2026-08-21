---
flags: [ios, backend]
---

# Auditoría integral de plantillas (sesión / splits / protocolos) + Programas avanzados por bloques

Sin migración Room: `ProgramEntity` es blob JSON (`dbJson`, v23) y los catálogos system son Kotlin estático. No se toca `data/db/` ni `app/schemas/` → sin flag `room`. No se edita `domain/auge/` (solo se consulta desde `domain/training/`) → sin flag `auge`. No se toca voz ni nutrición.

## Contexto y hallazgos previos (auditoría de experto, nivel 0)

Catálogos localizados por el equipo de investigación:

- **Plantillas de sesión**: `data/sessions/SessionTemplates.kt` (4292 líneas, `SESSION_TEMPLATES_SYSTEM`, revisión `v2-approved-2026-08-12-a`) + modelos en `data/sessions/SessionTemplateModels.kt`. Aplicación vía `domain/templates/SessionTemplateEngine.kt` (REPLACE/APPEND) y sugerencia semanal vía `domain/templates/SessionTemplateSuggestionEngine.kt`.
- **Splits (47)**: `data/splits/SplitTemplates.kt` — 29 generales + 24 tag POWERLIFTING (texas_method, smolov_base, sheiko_3/4day, westside_conjugate, 531_bbb, gzcl_method, calgary_barbell, etc.). Aplicación vía `domain/training/SplitApplicationEngine.kt`.
- **Plantillas de programa (11)**: `data/programs/ProgramTemplates.kt` (simples + power-12-3/16-4/20-5, powerbuild-12-3/16-4, body-12-3/16-4/20-5 con `blockNames/blockWeekCounts/blockGoals`).
- **Protocolos (16)**: `data/protocols/ProtocolLibrary.kt` (GZCL, 5/3/1, Juggernaut, Westside, RTS, Texas Method, Sheiko 3/4, Candito 6wk, Smolov Jr, Coan-Phillipi, nSuns, SBS, PHUL, PHAT, PPL hipertrofia) con `ProtocolBlock(weeks, goal, intensityMin/Max, volumeModifier)` + `data/protocols/ProtocolExerciseLibrary.kt` + `domain/training/PeriodizationEngine.kt`.

Evaluación experta preliminar (muestra extraída, pendiente inventario completo):

1. **Rangos de los protocolos son mayoritariamente correctos** frente a las fuentes publicadas: 5/3/1 (65-85/70-90/75-95% ✓), Candito 6wk (62-75 hipertrofia → 75-88 fuerza → 82-95 pico ✓), Sheiko (65-87% con modificadores de volumen 1.25-1.35 ✓), Coan-Phillipi (pico 90-100% ✓), RTS Emerging Strategies (70-82/82-92/90-100 ✓), Juggernaut (olas 10s/8s/5s/3s, 60-95% ✓), GZCL (65-80/80-90/90-100 ✓).
2. **Sospechas a verificar en la auditoría formal**:
   - `westside-base`: bloque único "ME/DE 50-100%" es demasiado grueso — el trabajo DE real es 50-65% con resistencia acomodante y el ME 90%+; la receta generada debe separar días ME/DE o la plantilla resultante será ridícula.
   - `smolov_base` como split general: Smolov es especialización de sentadilla 4×/sem brutal; ofrecerlo como split sin etiqueta de advertencia fuerte inducirá a error. Debe llevar `ALTA_TOLERANCIA` + descripción disuasoria y quedar fuera de sugerencias por defecto.
   - `bulgarian_lite`: el método búlgaro es autoregulado (máximo del día); si las sesiones generadas queman % fijos, contradice el protocolo.
   - PHUL/PHAT catalogados como "protocolos" cuando son splits con 4 bloques artificiales — riesgo de duplicidad/confusión con el catálogo de splits.
   - `ppl-hypertrophy`: bloque final "Especialización/Pico 78-88%" — un "pico" de intensidad no tiene sentido en hipertrofia pura; debería ser fase de densidad/metabolitos.
   - Plantillas de sesión de muestra vistas (Pecho Day, Legs Short, Deload Activo, PL recuperación): esquemas RPE/descanso razonables. Falta inventario de ~la mitad del archivo (líneas 1544-2300, 3901-4292).
3. **Hueco estructural confirmado**: los %RM de los protocolos se queman en las sesiones al aplicar; no hay recálculo semanal, ni progresión de cargas entre semanas/bloques, ni transición automática de fase. `MesocycleGoal` (ACCUMULATION/INTENSIFICATION/REALIZATION/DELOAD) es solo etiqueta. Esto es exactamente lo que la Fase C viene a cerrar.
4. **Paridad rota**: iOS tiene splits completos pero `SessionTemplates.swift` está VACÍO (`[]`) y su MacrocycleEditor es placeholder. Backend no tiene catálogos.

## Rutas

### Fase A — Auditoría formal de contenido (sin tocar producto; produce informes + tests)
- Inventario completo: `android-native/app/src/main/java/com/example/kpkn/data/sessions/SessionTemplates.kt` (líneas pendientes 1544-2300 y 3901-4292).
- Tests nuevos de calidad (Android unit tests, lado `testBase`):
  - `SessionTemplateAuditTest` — reglas duras sobre `SESSION_TEMPLATES_SYSTEM`.
  - `SplitAuditTest` — para cada split de `SPLIT_TEMPLATES`: patrón 7 días válido, frecuencia semanal por grupo muscular ≥1 (≥2 salvo tags BAJA_FRECUENCIA/POWERLIFTING), sin 3+ días consecutivos del mismo grupo, coherencia días↔`difficulty`, y mapeo día→plantilla vía `SessionTemplateSuggestionEngine.suggestWeek` sin días vacíos ni plantillas incompatibles (`SessionTemplateQualityRules`).
  - `ProtocolAuditTest` — invariantes de `PROTOCOL_LIBRARY`: bloques ordenados con intensidad creciente hacia pico y deload final presente (salvo protocolos que históricamente no lo tienen, lista blanca explícita), `intensityMin < intensityMax`, `volumeModifier` en [0.25, 1.6], `defaultSplit` existente en `SPLIT_TEMPLATES`, lifts de `ProtocolExerciseLibrary` resolubles a `configurationId` v2.
- Informe: `docs/audits/2026-08-20-templates-audit/` (hallazgos P0 ridículo/erróneo · P1 mejorable · P2 naming/etiquetas, con tabla por plantilla: volumen semanal por músculo vs MEV/MAV/MRV de `volumeRecommendations`, distribución de intensidad, orden de ejercicios, descansos).

### Fase B — Correcciones de contenido (solo datos, sin esquema)
- `data/sessions/SessionTemplates.kt`, `data/splits/SplitTemplates.kt`, `data/protocols/ProtocolLibrary.kt`, `data/programs/ProgramTemplates.kt`: aplicar fixes P0/P1 aprobados del informe; bump de revisión de catálogo (`TEMPLATE_CATALOG_REVISION` → `v3-...`).
- Criterios de corrección de experto (rúbrica): descansos por objetivo (fuerza ≥180s compuestos pesados, hipertrofia 60-120s), orden compuesto→aislamiento, sin volumen basura (>25 series directas/semana/músculo), deload = reducción de volumen E intensidad, plantillas PL con main lift del día presente y accesorios que ataquen puntos débiles razonables, prohibido sentadilla pesada + peso muerto pesado mismo día salvo plantillas etiquetadas AVANZADO/PEAK.

### Fase C — Programas avanzados por bloques (funcionalidad grande)
- **Modelo** (`data/models/Program.kt`): campos nuevos `@Serializable` con defaults en el JSON de Program — `Block.goal: BlockGoal?` (enum NUEVO, nunca valores nuevos en enums existentes: `dbJson` no usa `coerceInputValues`), `Block.progressionScheme: BlockProgressionScheme?` (enum NUEVO: NONE/LINEAR_LOAD/UNDULATING/PERCENT_RM/RPE_CAP), `ProgramWeek.progressionIndex`, y por sesión los campos de prescripción ya existen (`ExerciseSet.targetPercentageRM/targetRPE/intensityMode`). SIN migración Room (v23).
- **Motores puros nuevos en `domain/training/`** (sin `android.*`):
  - `BlockProgressionEngine.kt` — genera/actualiza la prescripción de las semanas de un bloque a partir de `ProtocolBlock`/objetivo + esquema, reusando `PeriodizationEngine.prescriptionFor/percentageForWeek/scaleSets` y `SessionTemplateEngine.cloneSessionContent` (deepClone con ids nuevos — regresión conocida §B7 de MEMORY).
  - `BlockTransitionEngine.kt` — al completar la última semana de un bloque: evalúa transición (completitud de sesiones, gate AUGE consultando `AugeFatigueEngine.shouldSuggestAutoDeload`, `OvertrainingDetector`, EMA de estrés de mesociclo) y decide: avanzar al bloque siguiente con nueva prescripción, insertar deload, o proponer test de 1RM en bloques REALIZATION. Integración en `ProgramProgressEngine.advanceAfterSessionComplete` (hoy solo avanza cursor en programas SIMPLE — extender a COMPLEX).
  - Tracks soportados: hipertrofia (acumulación volumen → sobrecarga/densidad → deload), powerlifting (acumulación → intensificación → peaking → taper/competición, alineado con power-12-3/16-4/20-5), powerbuilding (híbrido).
- **UI** (`screens/programdetail/`):
  - `BlockRoadmap.kt`: marcar bloque activo (el VM ya deriva `activeBlockId`) y progreso "semana X/Y del bloque".
  - Banner de transición de bloque en `ProgramDetailScreen.kt` (evento → ViewModel → `StateFlow`; prohibida lógica inline tipo `SplitView`).
  - `components/editor/MacrocycleEditorLegacy.kt` (+ diálogos): edición de `goal`/`progressionScheme` por bloque, preview de "qué cambia al superar el bloque" (diff de prescripción semana N vs N+1).
  - `CompactHeroBanner.kt`/`ProgramHeroWidgets.kt`: indicador "Bloque 2/4 · Intensificación · quedan 3 semanas".
- **Navegación**: NO se añaden rutas nuevas en Fase C (se opera dentro de ProgramDetail). El parámetro `tab=` muerto y el sub-tab `SPLIT` huérfano quedan documentados como deuda, no se tocan.

### Fase D — Paridad y docs
- `ios-native/KPKNFit/KPKNFit/Data/Models/Program.swift` (campos de bloque), `Data/Sessions/SessionTemplates.swift` (rellenar catálogo — hoy vacío; alcance acotado a portar las plantillas ya auditadas de Fase B), `Domain/Training/` (engines de progresión/transición), MacrocycleEditor placeholder mínimo viable.
- `backend/models/common.py:273-295` — reflejar campos nuevos de solo-lectura.
- Docs: `docs/ARCHITECTURE.md`, `docs/program-management-baseline.md`, matriz de paridad, y corregir `AGENTS.md`/docs que dicen Room v20 (real: v23).

## Impacto

- **Android**: datos de catálogos (B), `data/models/Program.kt`, `domain/training/` (engines nuevos + ProgramProgressEngine), `screens/programdetail/` (C). El editor de sesiones solo se ve afectado por la calidad del catálogo (B), no por cambios de código.
- **Room**: sin migración; DB permanece v23. Todo viaja en el JSON de `ProgramEntity`.
- **iOS**: Fase D (modelo + catálogo de sesiones + engines).
- **Backend**: Fase D (modelo común).
- **AUGE**: solo lectura/consulta desde `domain/training/`; ningún archivo de `domain/auge/` se modifica.
- **Voz**: sin impacto.

## Pruebas

1. Nuevos: `SessionTemplateAuditTest`, `SplitAuditTest`, `ProtocolAuditTest` (Fase A), `BlockProgressionEngineTest`, `BlockTransitionEngineTest` (Fase C — incluye: progresión de % semana a semana, deload forzado por gate AUGE, transición con sesiones incompletas, deepClone sin ids duplicados).
2. Regresión dirigida obligatoria por zonas vecinas: `SessionTemplateCatalogTest`, `SessionEditorRulesEngineTest`, tests de `SupersetRules`/`UnilateralRules` (catálogo de regresiones §C2/§F0-F1), `CardioHiitTemplatesTest` (patrón de catálogo estático análogo).
3. Comandos: `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '<filtro>'"` y al final de cada fase `... -Tasks "compileBaseDebugKotlin"` + `assembleDebug`.
4. iOS: suite de tests Swift existente en `ios-native/` tras Fase D.
5. Manual (Fase C): aplicar programa power-16-4 en emulador, completar semanas de un bloque, verificar banner de transición y nueva prescripción.

## Riesgos

- **Serialización**: `dbJson` sin `coerceInputValues` → enums NUEVOS exclusivamente; un valor nuevo en enum existente rompe la deserialización de programas guardados (riesgo P0 de pérdida de datos de usuario).
- **ProgramProgressEngine** hoy solo soporta programas SIMPLE; extenderlo a COMPLEX puede dejar programas existentes en estados de cursor inesperados — requiere reconciliación defensiva y tests con programas reales.
- **MacrocycleEditorLegacy.kt (~3035 líneas)** es monolítico y stateful; alto riesgo de regresión UI al añadir edición de bloques — cambios mínimos y acotados, siguiendo el patrón de fachada `MacrocycleEditor.kt`.
- **Catálogo de 4292 líneas**: los fixes de contenido (Fase B) deben ser data-only y revisados contra el catálogo de regresiones de MEMORY antes de tocar cada zona (supersets rounds≥sets, unilateral, deepClone).
- **Subjetividad de la auditoría**: la rúbrica de experto queda escrita en el informe para que las correcciones sean defendibles y no arbitrarias.
- **iOS SessionTemplates vacío**: rellenarlo es superficie grande; se acota a portar solo plantillas auditadas, el resto queda como deuda documentada.
- **Pipeline**: MEMORY registra un bloqueo previo (pipeline.json en stage "construction" del plan 2026-08-17); hay que reasignar el pipeline a este plan antes de construir.
