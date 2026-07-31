# Plan Refinado del Catálogo de Ejercicios — Veredicto y Profundización

> Fecha: 2026-07-31  
> Fuente: `android-native/app/src/main/assets/exercise_database.json` (795 filas, post-P0)  
> Principio rector: la taxonomía Pin Press / Spoto / Floor es la regla de agrupación universal. No hay atrapatodos.

---

## 1. Veredicto Ejecutivo

### 1.1 Lo que ya funciona (y está mejor de lo que el plan original suponía)

| Componente | Estado | Notas |
|------------|--------|-------|
| Script P0 (`apply_catalog_p0_transform.js`) | ✅ Ejecutado | 848 → 795 filas. Remo T, Remo barra/smith/máquina/polea/Gironda/banda/invertido, Press banca (barra/DB), Convergente, nonsense martillo+barra ya fusionados. |
| `ExerciseAspectChipsInline` | ✅ Implementado | Ya renderiza chips inline en `ExercisePickerCards.kt` cuando `technicalAspects != null`. |
| `VariantFlowResultCache` | ✅ Funcional | Mantiene `selectedAspects` en memoria entre picker y workout replace. |
| `Exercise.selectedAspects` en `Session.kt` | ✅ Persistido | El modelo de dominio ya soporta `Map<String, String>?` en `Exercise`. |
| `TechnicalAspectEngine` | ✅ Operativo | Calcula `effectiveMuscles` a partir de `selectedOptions` + `modifiers`. |
| Aliases (`exercise_id_aliases.json`) | ✅ Operativo | `resolveExerciseId()` resuelve IDs antiguos → canónicos en runtime. |

### 1.2 Lo que está incompleto o roto

| Problema | Severidad | Evidencia |
|----------|-----------|-----------|
| **Solo ~15 ejercicios tienen `technicalAspects`** | 🔴 Crítico | El 98% del catálogo (780/795) tiene `technicalAspects: null`. Jalón, Dominadas, Hip Thrust, Talones, Press hombros, Tríceps, etc. siguen como filas planas. El picker nunca muestra chips para ellos. |
| **VariantFlowSheet sigue como fallback** | 🟠 Alto | `ExercisePickerCards.kt:170` levanta `VariantFlowSheet` (wizard Tune) si el ejercicio tiene `variantGroupId` pero no `technicalAspects`. Esto perpetúa exactamente el wizard que el plan quiere eliminar. |
| **Modifiers biomecánicos incompletos en P0** | 🟠 Alto | Press banca `bench_angle` (inclinado/declinado) no tiene `modifiers`. El `involvedMuscles` base asume plano. Inclinado debería subir deltoides anterior; declinado bajarlo. Remo barra `implement` (EZ/neutra) no modifica músculos. |
| **`variantGroupId` no deprecado en canónicos** | 🟡 Medio | Los canónicos nuevos (ej. `back_remo_barra_t`) aún llevan `variantGroupId` en los ejercicios absorbidos (que ya fueron eliminados). Pero los canónicos mismos no deberían tener `variantGroupId` porque ya no son variantes de un grupo; son la raíz. |
| **Faltan aliases para búsqueda de nombres viejos** | 🟡 Medio | Si el usuario busca "Press inclinado barra", el `alias` del canónico debe contener ese string para que el scorer lo encuentre. No está garantizado que todos los alias de merge estén indexados. |
| **`RememberAspectCacheSync` solo funciona con aspects inline** | 🟡 Medio | Si un ejercicio aún no tiene `technicalAspects`, el caché no se actualiza; el workout replace pierde defaults. |

### 1.3 Lo que el plan original ignora o subespecifica

1. **Persistencia histórica de sesiones:** Los workouts guardados en SQLite (y backups JSON) contienen `exerciseDbId` con IDs antiguos. `resolveExerciseId()` los redirige al canónico, pero los `selectedAspects` de esos workouts históricos quedan en `null` (porque fueron creados antes de los chips). No hay estrategia para materializar defaults en carga histórica.

2. **Indexación de aliases en búsqueda:** `calculateSearchScore()` probablemente indexa `name` y `alias`, pero no hay garantía de que los strings "Press banca inclinado barra" aparezcan en el alias del canónico fusionado. Sin eso, la búsqueda colapsa.

3. **Mecanismo de versionado del catálogo:** No hay `catalogVersion` ni timestamp. Cuando se actualiza el JSON en una release, los usuarios con workouts antiguos no tienen forma de saber si sus `exerciseDbId` necesitan re-resolución.

4. **Cómo se declara el patrón de chips por familia:** El plan dice "El UX solo muestra los chips de ese patrón", pero no dice dónde vive esa declaración. La solución real es: **cada canónico declara sus `technicalAspects` en el JSON**. No hace falta una tabla externa. Pero se necesita una **validación automática** que grite si dos canónicos de la misma familia declaran aspect IDs incompatibles.

5. **Biomecánica de chips:** Un chip no es solo cosmético. `bench_angle: incline` debería modificar `Deltoides` a PRIMARY o subir su `volumeContribution`. En P0 esto es incomplete.

6. **Criterio de cierre de F1:** El plan dice "Sustituir VariantFlowSheet" pero no dice bajo qué condición se puede eliminar del todo: **cuando el 100% de los ejercicios que antes usaban `variantGroupId` ya tengan `technicalAspects` (chips)**. Hasta entonces, debe coexistir como fallback.

7. **Sentadilla / RDL / PM:** El plan dice "fuera de alcance" pero son ~170 ejercicios (más del 20% del catálogo). Ignorarlos por completo deja el catálogo en un estado inconsistente. El plan refinado debe al menos establecer los **principios aplicables** para cuando se llegue a esa fase.

---

## 2. Arquitectura Consolidada

### 2.1 Modelo JSON — `ExerciseMuscleInfo`

Cada ejercicio en `exercise_database.json` es una de tres clases:

| Clase | `technicalAspects` | `variantGroupId` | `variantName` |
|-------|-------------------|------------------|---------------|
| **Canónico** | Declara el patrón completo de chips de su familia | `null` | `null` |
| **KEEP_SPECIALTY** | Declara chips mínimos si aplica (ej. implemento) | `null` | `null` (o nombre propio en `name`) |
| **Legacy absorbido** | Eliminado del JSON; existe solo vía `exercise_id_aliases.json` | N/A | N/A |

**Regla de oro:** Si un ejercicio tiene `technicalAspects`, **no debe tener** `variantGroupId`. Si un ejercicio tiene `variantGroupId`, es porque aún no fue migrado a chips (estado transitorio).

### 2.2 Patrón de chips — ¿dónde vive?

No en una tabla externa. **Vive en cada canónico**, en su campo `technicalAspects`. Ejemplo real ya implementado (`back_remo_barra_t`):

```json
"technicalAspects": [
  { "id": "station", "name": "Estación", "options": [{"id":"libre"...}, {"id":"maquina"...}] },
  { "id": "grip_width", "name": "Amplitud", "options": [...] },
  { "id": "grip_orientation", "name": "Tipo de agarre", "options": [...] }
]
```

**Consistencia por familia** se garantiza con un test de contracto (ver sección 6), no con una tabla central.

### 2.3 Modelo de sesión — `Session.Exercise`

Ya existe en código:

```kotlin
data class Exercise(
    val exerciseDbId: String? = null,       // ID canónico o alias (resuelto en runtime)
    val selectedAspects: Map<String, String>? = null,  // {"grip_width":"close","bench_angle":"incline"}
    val effectiveMuscles: List<InvolvedMuscle>? = null, // Cache computado por TechnicalAspectEngine
    ...
)
```

No requiere cambios de schema de persistencia si la sesión se guarda como JSON (backup) o si la entidad Room tiene un campo `exerciseData: String` serializado. Si Room tiene campos atómicos, verificar que `selectedAspects` se persista.

### 2.4 Aliases y resolución en runtime

Flujo actual (funciona, mantener):

```
Usuario busca "Press banca inclinado"
  → calculateSearchScore() match en alias del canónico
  → Usuario selecciona canónico `tren_superior_press_banca_plano_barra`
  → Picker muestra chips inline; usuario elige bench_angle=incline, grip_width=medium
  → selectedAspects = {"bench_angle":"incline","grip_width":"medium"}
  → En workout, TechnicalAspectEngine computa effectiveMuscles
```

**Falta obrar:** Asegurar que los aliases de búsqueda (`alias` en JSON) incluyan los nombres antiguos de los ejercicios fusionados.

### 2.5 Deprecación gradual de `VariantGroup` / `VariantFlowSheet`

- **Fase actual:** Coexistencia. Si `technicalAspects != null` → chips inline. Si `technicalAspects == null && variantGroupId != null` → `VariantFlowSheet` (fallback).
- **Fase final (cuando 100% de ejercicios tengan chips):** Eliminar `VariantFlowSheet.kt`, `VariantGroupIndex.kt`, `VariantPreferenceStore.kt` y los campos `variantGroupId/Name/Order` de los canónicos.


---

## 3. Taxonomía de Familias Completa

### 3.1 P0 — Estado y correcciones biomecánicas

#### 3.1.1 Press de Banca con Barra (canónico `tren_superior_press_banca_plano_barra`)

Chips actuales: `bar_path`, `bench_angle`, `grip_width`.

**Correcciones faltantes:**
- `bench_angle` debe tener modifiers biomecánicos:
  - `incline` → `Deltoides`: subir de SECONDARY (0.5) a PRIMARY-equivalente o ADD 0.3; `Pectorales`: MULT 0.9 (menor activación vs plano).
  - `decline` → `Pectorales`: MULT 1.05; `Deltoides`: MULT 0.7.
- `bar_path: smith` puede no modificar (trayectoria fija reduce estabilizadores → `Bíceps` como estabilizador baja). Opcional para F3.

#### 3.1.2 Remo con Barra (canónico `back_remo_barra`)

Chips actuales: `grip_width`, `grip_orientation`, `implement`.

**Correcciones faltantes:**
- `implement: ez` y `neutra` no tienen modifiers. El agarre EZ disminuye requerimiento de supinación/ pronación del antebrazo; `neutro` cambia activación de bíceps. Añadir modifiers mínimos: `implement: ez` → `Bíceps Braquial` ADD -0.1 (o omitir si no hay datos). Default: mantener vacío hasta tener data, pero declarar el campo para el test de contracto.

### 3.2 P1 — Jalón, Dominadas, Curls, Hip Thrust, Puente/Frog, Face Pull, Encogimientos, Talones

#### 3.2.1 Jalón al Pecho

| id actual | Decisión | Chips destino |
|-----------|----------|---------------|
| `back_jalon_pecho_polea_ancho` | MERGE → `back_jalon_pecho_polea` | `grip_width=wide` |
| `back_jalon_pecho_polea_cerrado` | MERGE | `grip_width=close` |
| `back_jalon_pecho_polea_unilateral` | MERGE | `laterality=unilateral` |
| `back_jalon_neutro_polea` | MERGE | `grip_orientation=neutro` (del canónico polea) |
| `back_jalon_pecho_maquina_ancho` | MERGE → `back_jalon_pecho_maquina` | `grip_width=wide` |
| `back_jalon_pecho_maquina_cerrado` | MERGE | `grip_width=close` |
| `back_jalon_banda_ancho` | MERGE → `back_jalon_banda` | `grip_width=wide` |
| `back_jalon_banda_cerrado` | MERGE | `grip_width=close` |

**Canónicos resultantes:**
1. `back_jalon_pecho_polea` — chips: `grip_orientation` (prono/supino/neutro), `grip_width` (wide/medium/close), `laterality` (bi/uni).
2. `back_jalon_pecho_maquina` — chips: `grip_width`, `grip_orientation` (prono/cerrado; máquina no siempre tiene neutro → omitir si no aplica, o dejar y que el test lo valide).
3. `back_jalon_banda` — chips: `grip_width` (wide/close). No agarre tipo porque banda elástica no lo permite con precisión.

#### 3.2.2 Dominadas

| id actual | Decisión | Chips destino |
|-----------|----------|---------------|
| `back_dominadas_pronas` | CANÓNICO `back_dominadas` | `grip_type=prono`, `load_type=bodyweight` |
| `back_dominadas_supinas` | MERGE | `grip_type=supino` |
| `back_dominadas_neutras` | MERGE | `grip_type=neutro` |
| `back_dominadas_lastradas` | MERGE | `load_type=loaded` |
| `back_dominadas_asistidas_maquina` | MERGE | `load_type=assisted_machine` |
| `back_dominadas_asistidas_banda` | MERGE | `load_type=assisted_band` |
| `back_dominadas_anillas` | KEEP_SPECIALTY o chip? Test mental: usuario busca "Dominadas en anillas" → espera card propia? Es un setup distinto (inestable). **Decisión: KEEP_SPECIALTY** con chip mínimo `grip_type`. |
| `back_dominadas_escapulares` | KEEP_SPECIALTY (es un movimiento distinto; no es dominada completa) | sin chips |
| `biceps_dominadas_supinas_cerradas` | MERGE en `back_dominadas` | `grip_type=supino`, `grip_width=close` |
| `biceps_dominadas_supinas_lastradas` | MERGE en `back_dominadas` | `grip_type=supino`, `load_type=loaded` |

**Nota:** Dominadas supinas también están en familia bíceps (`biceps_dominadas_supinas_*`). Eso es un caso de **ejercicio compartido entre familias**. El plan original no lo trata. Decisión: el canónico único es `back_dominadas` (familia espalda). Los ejercicios `biceps_dominadas_*` se fusionan en él con los mismos chips. Si el usuario quiere un ejercicio para bíceps, puede buscar "dominadas supinas" y el canónico aparece (porque el alias contendrá esos nombres). No necesitamos duplicar la fila.

#### 3.2.3 Curl de Bíceps (matriz completa)

Setup canónicos (cada uno es una fila con sus propios chips):

| Canónico | Chips | Notas |
|----------|-------|-------|
| `biceps_curl_de_pie` | `grip_type` (supino/martillo/inverso/supinación), `implement`, `laterality` (bi/polea-uni) | Atrapa todas las filas `de_pie_...` excepto martillo+barra_recta/EZ (nonsense). |
| `biceps_curl_sentado_banco_plano` | igual | — |
| `biceps_curl_inclinado` | igual | — |
| `biceps_curl_declinado` | `grip_type`, `implement` (solo mancuernas; declinado no se hace con barra fija) | Restringir implementos en descripción. |
| `biceps_curl_predicador` | `grip_type`, `implement` (barra EZ/recta/mancuernas/polea/maquina) | — |
| `biceps_curl_arana` | `grip_type`, `implement` (banda/barra EZ/recta/mancuernas/polea) | — |
| `biceps_curl_drag` | `grip_type`, `implement` | — |
| `biceps_curl_bayesian` | `grip_type`, `implement` (banda/mancuernas/polea), `laterality` | Siempre polea/tracción; barcode crossing. |
| `biceps_curl_concentrado` | `grip_type`, `implement` (banda/mancuernas/polea), `laterality` | — |
| `biceps_curl_superman` | `grip_type`, `laterality` | Siempre polea; chip lateralidad. |
| `biceps_curl_crucifijo` | `grip_type`, `laterality` | Siempre polea. |
| `biceps_curl_zottman_mancuernas` | KEEP_SPECIALTY — `implement` (mancuernas) | — |
| `biceps_curl_trx` | KEEP_SPECIALTY? o chip de implemento en `de_pie`? Es un implemento inusual. **Decisión: canónico hermano ligero** `biceps_curl_trx` sin chips, o merge en `de_pie` con `implement=trx` si se quiere unificar. Default: canónico propio. |

**Nonsense todavía sin eliminar (verificar en JSON):**
- `biceps_curl_de_pie_martillo_barra_recta` → ya eliminado en P0 (✅)
- Verificar si existe `biceps_curl_declinado_barra_recta` o `biceps_curl_declinado_barra_ez` → si existen: NONSENSE (no se hace curl declinado con barra fija; el banco declinado usa mancuernas).


#### 3.2.4 Hip Thrust / Puente de Glúteos / Frog Pumps

Hip Thrust tiene ~16 filas (8 bilaterales × equipos, 8 unilaterales × equipos).

| id actual | Decisión |
|-----------|----------|
| `glutes_hip_thrust_barra_recta` | CANÓNICO `glutes_hip_thrust` |
| `glutes_hip_thrust_mancuerna` ... `kettlebell`, `disco`, `polea`, `banda`, `maquina`, `maquina_smith` | MERGE → chip `equipment` |
| `glutes_hip_thrust_unilateral_*` (7 equipos) | MERGE → chip `laterality=unilateral` + `equipment` |

**Canónico:** `glutes_hip_thrust`  
**Chips:** `laterality` (bilateral/unilateral), `equipment` (barra/mancuerna/kettlebell/disco/polea/banda/maquina/smith).

Puente de glúteos (suelo) y Frog Pumps:

| Canónico | Chips |
|----------|-------|
| `glutes_puente_gluteos` | `equipment` (peso_corporal/mancuerna/barra/disco), `laterality` (bi/uni) |
| `glutes_frog_pumps` | `equipment` (peso_corporal/disco) |

**Nota:** Puente es distinto de Hip Thrust (suelo vs banco/espalda apoyada). No absorber uno en otro.

#### 3.2.5 Face Pull

| id actual | Decisión |
|-----------|----------|
| `deltoides_face_pull_polea` | CANÓNICO `deltoides_face_pull` |
| `deltoides_face_pull_banda` | MERGE → chip `equipment=banda` |
| `deltoides_face_pull_polea_unilateral` | MERGE → chip `laterality=unilateral` |

**Canónico:** `deltoides_face_pull`  
**Chips:** `equipment` (polea/banda), `laterality` (bilateral/unilateral).

#### 3.2.6 Encogimientos (Shrug + Kelso)

| id actual | Decisión |
|-----------|----------|
| `back_encogimientos_barra_recta` | CANÓNICO `back_encogimientos` |
| `back_encogimientos_mancuernas`, `kettlebell`, `polea`, `maquina`, `maquina_smith`, `maquina_barra_t` | MERGE → chip `equipment` |
| `back_encogimientos_tras_nuca_barra` | KEEP_SPECIALTY (o chip `bar_position=behind_neck` si se quiere unificar en shrug común). **Decisión: chip `bar_position` (front/behind_neck)** en el canónico `back_encogimientos`, porque es el mismo movimiento, solo cambia la posición de la barra. Si el equipo no permite tras nuca (máquina, polea), omitir la opción. |
| `back_encogimientos_kelso_banco_plano_barra_ez` | CANÓNICO `back_encogimientos_kelso` |
| `back_encogimientos_kelso_banco_plano_kettlebell`, `mancuernas`, `banda`, `polea` | MERGE → chip `equipment` |
| `back_encogimientos_kelso_banco_inclinado_barra_ez` | MERGE → chip `bench_angle=incline` |
| `back_encogimientos_kelso_banco_inclinado_kettlebell`, `mancuernas` | MERGE → chips `equipment` + `bench_angle=incline` |

**Canónicos resultantes:**
1. `back_encogimientos` — chips: `equipment` (barra/mancuerna/kettlebell/polea/maquina/smith/barra_t), `bar_position` (front/behind_neck donde aplica).
2. `back_encogimientos_kelso` — chips: `equipment`, `bench_angle` (flat/incline).

#### 3.2.7 Elevación de Talones

Hay ~45 ejercicios de talones. Es la familia con mayor caos cartesiano.

**Principio de reducción:**
- Setup principal determina canónico (De pie, Sentado, Donkey, Prensa).
- Lateralidad (bi/uni) es chip.
- Equipo/carga es chip dentro del setup.
- Host/machine-type (Hack, V-Squat, Belt, Smith) es chip de `station` solo para setups donde tenga sentido (De pie).

| Canónico | Absorbe | Chips |
|----------|---------|-------|
| `calves_elevacion_talones_de_pie` | Todos los `de_pie` bilaterales y unilaterales sobre el suelo / plataforma libre / máquina genérica. | `laterality`, `equipment` (peso_corporal/barra/mancuerna/kettlebell/polea/maquina), `station` (libre/smith/hack/v_squat/belt) |
| `calves_elevacion_talones_sentado` | Todos los `sentado` bi/uni. | `laterality`, `equipment` (barra/disco/mancuerna/kettlebell/polea/maquina) |
| `calves_elevacion_talones_donkey` | Todos los donkey (bi/uni). | `laterality` |
| `calves_elevacion_talones_prensa` | Todos los en prensa (45/horizontal/vertical × bi/uni). | `press_angle` (45/horizontal/vertical), `laterality` |

**Nonsense a verificar:**
- Talones en prensa con barra recta? No, la prensa ya es la máquina. Si existe `calves_elevacion_talones_prensa_barra_recta` → NONSENSE o merge con `equipment=bodyweight` (los talones en prensa se hacen con la plataforma de la máquina).

**Tibial anterior:** Mantener separado o unificar como chip de `muscle=anterior`? El tibial anterior no es gemelo. **Decisión:** Canónico separado `calves_elevacion_tibial_anterior` con chips `laterality` y `equipment`.


### 3.3 P2 — Press hombros, Laterales, Tríceps

#### 3.3.1 Press de Hombros / Militar / Arnold

No un único "Press hombros" atrapatodo. Los setups cambian la biomecánica.

| Canónico | Absorbe | Chips | KEEP_SPECIALTY |
|----------|---------|-------|----------------|
| `deltoides_press_militar_de_pie` | `barra_recta`, `barra_ez`, `barra_neutra` | `implement` (recta/ez/neutra) + `laterality` (bi) | — |
| `deltoides_press_hombros_de_pie` | `mancuernas`, `kettlebell`, `banda`, `polea` | `equipment`, `laterality` (bi/uni si polea) | — |
| `deltoides_press_hombros_sentado` | `barra_recta`, `barra_ez`, `barra_neutra`, `mancuernas`, `kettlebell`, `maquina`, `smith`, `polea` | `equipment`, `laterality` | — |
| `deltoides_press_arnold` | `mancuernas`, `kettlebell`, `polea` | `equipment` | KEEP_SPECIALTY por identidad propia (rotación característica) con mínimos chips de implemento. |
| `deltoides_push_press` | `barra_recta`, `mancuernas`, `kettlebell` | `equipment` | KEEP_SPECIALTY (uso de impulso de piernas = identidad distinta) |
| `deltoides_press_landmine_unilateral` | — | — | KEEP_SPECIALTY (setup landmine = identidad propia) |
| `deltoides_press_z` | `barra_ez`, `barra_recta`, `mancuernas`, `kettlebell` | `equipment` | KEEP_SPECIALTY o canónico? Test mental: usuario busca "Press Z" → sí, espera card propia. KEEP_SPECIALTY. |

#### 3.3.2 Elevaciones Laterales

| Canónico | Absorbe | Chips |
|----------|---------|-------|
| `deltoides_elevaciones_laterales_de_pie` | `mancuernas`, `kettlebell`, `banda`, `polea`, `maquina`, `barra_ez`, `barra_recta` | `equipment`, `laterality` (bi/uni para polea) |
| `deltoides_elevaciones_laterales_sentado` | `mancuernas`, `kettlebell`, `polea`, `maquina` | `equipment`, `laterality` |
| `deltoides_elevaciones_laterales_inclinadas` | `mancuerna` | chip `laterality` si hay uni |
| `deltoides_elevaciones_laterales_acostado` | `mancuernas`, `polea_cruzada`, `polea_unilateral` | chips `equipment`, `laterality` |
| `deltoides_elevaciones_laterales_super_rom` | `mancuernas`, `polea`, `polea_unilateral` | `equipment`, `laterality` | KEEP_SPECIALTY por identidad propia? Test: busca "Super ROM" → sí. KEEP_SPECIALTY con chips mínimos. |

#### 3.3.3 Tríceps

Misma lógica: canónico por patrón de movimiento real (press francés, pushdown, overhead, patada, crossbody, JM, Tate, California, PJR, Katana...).

| Canónico / KEEP_SPECIALTY | Chips | Notas |
|---------------------------|-------|-------|
| Tríceps Pushdown | `equipment` (barra recta/EZ/cuerda), `grip` (prono/supino), `laterality` | Polea unilateral absorbe en bi con chip lateralidad. |
| Tríceps Overhead (extensión por encima) | `equipment` (mancuerna/banda/polea), `laterality` | — |
| Tríceps Patada | `equipment` (polea/banda), `laterality` | — |
| Tríceps Press Francés | `bench_angle` (flat/incline/decline/floor), `equipment` (barra EZ/recta/neutra/mancuerna/kettlebell/polea/banda), `laterality` (para polea) | No absorber en "Press de pecho". Es familia tríceps. |
| Tríceps JM Press | `equipment` (barra EZ/mancuernas) | KEEP_SPECIALTY por técnica propia. |
| Tríceps Tate Press | `equipment` (mancuernas/polea) | KEEP_SPECIALTY. |
| Tríceps Press California | `equipment` (barra EZ/recta/mancuernas) | KEEP_SPECIALTY. |
| Tríceps PJR | `equipment` | KEEP_SPECIALTY. |
| Tríceps Katana | `laterality` (uni) | KEEP_SPECIALTY. |
| Tríceps Crossbody | `laterality` (uni) | KEEP_SPECIALTY o canónico hermano de patada. |

**Nota:** `triceps_press_maquina` es canónico de máquina selectorizada separado (no se absorbe en pushdown ni en press francés).

### 3.4 P3 — Sentadilla, RDL, Peso Muerto, Zancada (Principios)

**Declaración de alcance:** No se mergearán en atrapatodos. Se aplican los principios universales:

1. **Canónico por patrón de movimiento real:**
   - Sentadilla trasera (back squat) ≠ Sentadilla frontal ≠ Sentadilla sumo ≠ Sissy ≠ Hack ≠ V-Squat.
   - Cada uno es un canónico propio porque el patrón motor cambia.

2. **Chips dentro de un canónico:**
   - Sentadilla trasera: `equipment` (barra alta/baja/SSB/mancuernas/kettlebell/smith), `stance` (normal/sumo — solo si biomecánicamente se mantiene back squat).
   - Sentadilla frontal: `equipment` (barra/mancuernas/kettlebell/smith), `racked_position` (frontal/crossedstrap).
   - RDL Rumano: `equipment` (barra/mancuernas/kettlebell/smith/polea), `stance` (convencional/sumo/B-stance), `laterality` (bi/uni B-stance).
   - Peso Muerto Convencional: `equipment` (barra/mancuernas/kettlebell/smith), `deficit` (sí/no — sí = ROM extendido).
   - Zancada caminando/estática/inversa: `equipment`, `load_position` (frontal/trasero/zercher/overhead), `laterality`.

3. **KEEP_SPECIALTY (test mental de búsqueda):**
   - Anderson Squat (parada en el fondo)
   - Zercher Squat / Zercher RDL
   - Sissy Squat
   - Jefferson Squat
   - Búlgara (es una zancada trasera elevada; ya es canónico propio)
   - Cosack Squat
   - Pistol Squat
   - Jefferson Curl (familia espalda, no pierna)

4. **Nonsense a eliminar:**
   - Cualquier combinación que no sea biomecánicamente coherente (ej. "Sentadilla pistol con barra recta" — imposible; "RDL con barra EZ en sumo" — dudoso).


---

## 4. Especificación UX

### 4.1 Picker — Chips inline (ya implementado)

`ExerciseAspectChipsInline` ya existe. Comportamiento esperado:

```kotlin
if (isSelected && hasAspects && onAspectsChange != null) {
    ExerciseAspectChipsInline(
        exercise = info,
        selectedAspects = selectedAspects.ifEmpty { defaultAspectSelection(info) },
        onAspectsChange = onAspectsChange,
    )
}
```

**Cambio necesario:** Si el usuario **no** está en modo multiselect y toca un canónico, el picker debe:
1. Seleccionar el canónico.
2. Expandir chips inline **inmediatamente** (no requerir segundo tap).
3. Usar `defaultAspectSelection(info)` como default.
4. Guardar `selectedAspects` en el mapa local del picker.

### 4.2 ExerciseEditorCard — Mostrar chips seleccionados

Actualmente `ExerciseEditorCard` no muestra los aspectos seleccionados en la card colapsada. Debería mostrar una fila de chips compactos (solo los valores seleccionados, no todas las opciones) debajo del nombre del ejercicio.

Ejemplo visual:
```
[Press de Banca con Barra]        [≡]
   ┌─────────┬──────────┬────────┐
   │  Libre  │ Inclinado│ Amplio │
   └─────────┴──────────┴────────┘
```

### 4.3 Eliminación de VariantFlowSheet

**Condición de cierre:** Cuando el número de ejercicios en el catálogo con `variantGroupId != null && technicalAspects == null` sea **cero**.

Hasta entonces:
- Mantener `VariantFlowSheet` como fallback.
- No invertir tiempo en mejorar `VariantFlowSheet`; solo mantenerlo vivo.
- Cada oleada de chips reduce la dependencia.

Después de la última oleada:
- Borrar `VariantFlowSheet.kt`
- Borrar `VariantGroupIndex.kt`
- Borrar `VariantPreferenceStore.kt`
- Deprecar `variantGroupId`, `variantGroupName`, `variantName`, `variantOrder` de `ExerciseMuscleInfo` (dejarlos como `null` obligatorio en canónicos; eliminar de la clase en refactor posterior).

---

## 5. Persistencia y Migración

### 5.1 Datos históricos

Los workouts existentes en SQLite/backup JSON tienen `Exercise` con:
- `exerciseDbId: "tren_superior_press_banca_inclinado_barra"` (alias → canónico)
- `selectedAspects: null` (creado antes de chips)
- `variantGroupId: "vg_bench_press"` (legacy)

**Estrategia de resolución al cargar workout:**

```kotlin
fun Exercise.normalizeOnLoad(catalog: ExerciseDatabase): Exercise {
    val resolvedId = resolveExerciseId(this.exerciseDbId) ?: return this
    val info = catalog.byId(resolvedId) ?: return this
    val aspects = this.selectedAspects
        ?: defaultAspectSelection(info)   // ← NUEVO: materializar defaults para datos antiguos
    val effective = TechnicalAspectEngine.computeEffectiveMuscles(
        baseMuscles = info.involvedMuscles,
        selectedOptions = aspects.mapNotNull { (a,o) -> info.findOption(a,o) }
    )
    return this.copy(
        exerciseDbId = resolvedId,
        selectedAspects = aspects,
        effectiveMuscles = effective.effectiveMuscles,
        name = info.name,  // o nombre compuesto con aspectos
    )
}
```

**Nota:** Esto debe ejecutarse en el ViewModel al hidratar una sesión, NO en la entidad Room (para no mutar el backup original).

### 5.2 Catalog Versioning

Añadir a `exercise_database.json` (primer elemento meta o campo en cada ejercicio):

```json
{
  "_meta": { "catalogVersion": 3, "lastModified": "2026-07-31" }
}
```

O, más sencillo, un archivo separado `catalog_version.txt` en assets. La app al iniciar compara la versión del catálogo cargado con la de los workouts. Si difiere, ejecuta `normalizeOnLoad` en todas las sesiones del programa activo.

### 5.3 Aliases y búsqueda

Cada vez que se ejecuta un script de merge, debe actualizar el campo `alias` del canónico para incluir los nombres de los ejercicios absorbidos:

Ejemplo:
```json
"alias": "Press banca plano, Press banca inclinado, Press banca declinado, Press pecho Smith, ... Bench Press"
```

El test de contracto debe verificar que todos los IDs eliminados aparezcan como substring en el `alias` o en una tabla de search-aliases.


---

## 6. Validación y Tests

### 6.1 Test de contracto del catálogo (`ExerciseCatalogContractTest`)

Añadir validaciones:

```kotlin
@Test
fun canonicalsDoNotHaveVariantGroupId() {
    val canonicalsWithVG = catalog.filter {
        !it.technicalAspects.isNullOrEmpty() && !it.variantGroupId.isNullOrBlank()
    }
    assertTrue("Canónicos con chips no deben tener variantGroupId", canonicalsWithVG.isEmpty())
}

@Test
fun everyRemovedIdHasAliasOrSearchAlias() {
    // lista de ids eliminados via script → verificar que están en aliases.json
    // o que su nombre aparece como substring en el alias del canónico target
}

@Test
fun nonsenseDoNotExist() {
    val nonsensePatterns = listOf("martillo_barra_recta", "martillo_barra_ez")
    nonsensePatterns.forEach { pattern ->
        assertTrue("No debe existir $pattern", catalog.none { it.id.contains(pattern) })
    }
}

@Test
fun allTechnicalAspectsHaveValidDefaults() {
    catalog.forEach { ex ->
        ex.technicalAspects?.forEach { aspect ->
            assertNotNull("defaultOptionId requerido", aspect.defaultOptionId)
            assertTrue("defaultOptionId debe existir en options",
                aspect.options.any { it.id == aspect.defaultOptionId })
        }
    }
}
```

### 6.2 Test de consistencia de familia

Para cada familia declarada, verificar que todos los canónicos de esa familia declaren exactamente el mismo conjunto de `aspect.id` (opcional pero recomendado para mantener UX consistente):

```kotlin
@Test
fun remoCanonicosHaveConsistentAspectIds() {
    val remoIds = listOf("back_remo_barra", "back_remo_smith", "back_remo_maquina", ...)
    val expectedAspectIds = setOf("grip_width", "grip_orientation")
    remoIds.forEach { id ->
        val ex = catalogById[id] ?: return@forEach
        val actual = ex.technicalAspects?.map { it.id }?.toSet() ?: emptySet()
        assertEquals("Aspectos consistentes en familia Remo", expectedAspectIds, actual)
    }
}
```

**Nota:** Se permite que un canónico tenga un subset (ej. Remo Smith no tiene `implement`). El test debe reflejar el subset esperado, no un set global rígido.

### 6.3 Test de modifiers biomecánicos

Verificar que los modifiers de un chip no generen `volumeContribution` fuera de rango [0,1] y que no eliminen músculos primarios:

```kotlin
@Test
fun gripWidthCloseDoesNotRemovePectoralesFromBenchPress() {
    val press = catalogById["tren_superior_press_banca_plano_barra"]!!
    val result = TechnicalAspectEngine.computeEffectiveMuscles(
        press.involvedMuscles,
        selectedOptions = listOf(press.findOption("grip_width", "close")!!)
    )
    assertTrue("Pectorales deben seguir presente",
        result.effectiveMuscles.any { it.muscle == "Pectorales" })
}
```

---

## 7. Orden de Ejecución Revisado

| Fase | Entregable | Bloqueante |
|------|------------|------------|
| **P0-fix** | Correcciones biomecánicas en Press banca y Remo barra (modifiers faltantes). | — |
| **P1a** | Script de transformación: Jalón, Dominadas, Face Pull, Encogimientos. | P0-fix |
| **P1b** | Script de transformación: Hip Thrust / Puente / Frog. | P1a |
| **P1c** | Script de transformación: Curl de bíceps completo (~100 filas → ~12 canónicos). | P1b |
| **P1d** | Script de transformación: Talones (~45 filas → ~5 canónicos). | P1c |
| **P2a** | Script: Press hombros / Laterales / Tríceps. | P1d |
| **UX-r1** | Integrar `ExerciseAspectChipsInline` en `ExerciseEditorCard` (mostrar selección). | P1a |
| **UX-r2** | Eliminar `VariantFlowSheet` cuando conteo de ejercicios sin `technicalAspects` sea 0. | P2a |
| **P3** | Aplicar principios a Sentadilla / RDL / PM / Zancada (sin merges agresivos; solo cartesianos obvios y nonsense). | P2a |
| **Tests** | Validar todas las reglas de la sección 6. | Continuo |

**Principio de paralelización:** P1a-P1d pueden hacerse en paralelo por distintas personas porque cada familia es independiente. El único punto de integración es el campo `technicalAspects` del JSON.

---

## 8. Checklist de Condición de Cierre (Definición de "Hecho")

- [ ] Todas las familias del plan (excepto P3 si se decide postergar) tienen sus canónicos y chips definidos en `exercise_database.json`.
- [ ] El script `apply_catalog_p0_transform.js` (o sucesor) cubre P1-P2.
- [ ] Todos los IDs eliminados existen en `exercise_id_aliases.json`.
- [ ] Todos los aliases de búsqueda están indexados en el campo `alias` de los canónicos.
- [ ] `ExerciseCatalogContractTest` valida: no nonsense, no canónico con variantGroupId, defaults válidos.
- [ ] `ExerciseEditorCard` muestra chips seleccionados.
- [ ] `VariantFlowSheet` está eliminado (o solo queda para ejercicios custom no migrados).
- [ ] Workouts históricos cargan con `selectedAspects` materializados desde defaults.

---

*Fin del plan refinado.*

