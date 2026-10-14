# VT2a · Perfiles de análisis y resolver con cobertura del catálogo (dominio puro)

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt2a`, desde `feat/video-technique` con VT1b integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3.4 y §5 (entera);
  - `kinematics/ViewKind.kt` y `framing/FramingSpec.kt` (de VT1b);
  - `domain/exercises/catalogv2/ExerciseCatalogV2.kt` y `ExerciseCatalogV2Loader.kt` (para leer el catálogo en la prueba);
  - `src/test/.../ExerciseCatalogContractTest.kt` (patrón de carga del JSON real).
- **Logs:** `C:\kw\logs\vt-2a-<n>.log`.

## Archivos permitidos (nuevos)
- `domain/biomechanics/profiles/AnalysisProfile.kt`: enums y `data class` de §3.4.
- `domain/biomechanics/profiles/AnalysisProfiles.kt`: la tabla.
- `domain/biomechanics/profiles/ExerciseRef.kt`
- `domain/biomechanics/profiles/AnalysisProfileResolver.kt`, con `ProfileResolution`.
- Pruebas en `src/test/java/com/example/kpkn/domain/biomechanics/profiles/`.

## Contrato
§3.4, con un único añadido: `val plateMinAmplitude: Double? = null` al final de `AnalysisProfile`. Es la amplitud mínima en BH cuando cuenta el disco.
- `ViewKind` y `FramingSpec` se importan de VT1b; no los dupliques.

## `AnalysisProfiles`: los 23 perfiles de §5
Los datos de cada perfil salen de la tabla de §5. Este brief solo fija lo que falta:

| Perfil | `countViews` | `metricViews` | `minAmplitude` | `plateMinAmplitude` | `perSide` | `framing` |
|---|---|---|---|---|---|---|
| SQUAT | SIDE, FRONT, OBLIQUE | SIDE | 0,08 BH | 0,10 | no | SIDE; pies y cara; disco si `usesPlate` |
| LUNGE | SIDE, OBLIQUE | SIDE | 0,06 BH | 0,08 | no | SIDE |
| STEP_UP | SIDE, OBLIQUE | SIDE | 0,08 BH | — | no | SIDE |
| MACHINE_SQUAT | SIDE | SIDE | 30° | — | no | SIDE; sin pies |
| DEADLIFT | SIDE, OBLIQUE | SIDE | 0,10 BH | 0,10 | no | SIDE; pies; disco |
| HINGE | SIDE, OBLIQUE | SIDE | 30° | 0,08 | no | SIDE; pies |
| HIP_THRUST | SIDE, LYING | SIDE, LYING | 25° | 0,05 | no | SIDE |
| BACK_EXTENSION | SIDE, LYING, OBLIQUE | SIDE, LYING | 25° | — | no | SIDE |
| BENCH | SIDE, LYING | SIDE, LYING | 0,05 BH | 0,05 | no | SIDE; sin pies; disco si `usesPlate` |
| DIP | SIDE, OBLIQUE | SIDE | 0,05 BH | — | no | SIDE |
| PUSH_UP | SIDE, LYING | SIDE, LYING | 0,05 BH | — | no | SIDE; pies |
| OHP | SIDE, FRONT, OBLIQUE | SIDE | 40° | 0,10 | no | SIDE; disco si `usesPlate` |
| PULL_VERTICAL | FRONT, SIDE, OBLIQUE | FRONT, SIDE | 0,25 | — | no | FRONT/SIDE; sin pies |
| ROW | SIDE, OBLIQUE | SIDE | 0,20 | 0,05 | sí | SIDE |
| CURL | SIDE, FRONT, OBLIQUE | SIDE | 40° | — | sí | SIDE; sin pies |
| TRICEPS_EXTENDED_REST | SIDE, OBLIQUE, LYING | SIDE | 35° | — | sí | SIDE; sin pies |
| TRICEPS_FLEXED_REST | SIDE, OBLIQUE | SIDE | 35° | — | sí | SIDE; sin pies |
| RAISE | FRONT, SIDE, OBLIQUE | FRONT, SIDE | 35° | — | sí | FRONT; sin pies |
| LEG_EXTENSION | SIDE | SIDE | 35° | — | no | SIDE; sin pies |
| LEG_CURL | SIDE, LYING | SIDE, LYING | 35° | — | no | SIDE; sin pies |
| CALF | SIDE | SIDE | 0,015 BH | — | no | SIDE; pies |
| SHRUG | FRONT, SIDE | FRONT | 0,02 BH | — | no | FRONT; sin pies |
| FLY | SIDE, LYING | SIDE, LYING | 0,05 BH | — | no | SIDE; sin pies |

## Resolver
**Orden de resolución:**
1. `userProfileOverride`;
2. excepción por `definitionId`;
3. tabla por `movementPatternId`;
4. si `isCustom` (o no hay patrón): `NeedsUserChoice(conjetura)`.
   - La conjetura sale de `ExercisePatternDetector` mapeado con esta misma tabla; puede ser `null`.
   - El nombre NUNCA resuelve solo.

**`usesPlate`** = `equipmentId ∈ {barbell, smith_machine, hex_bar, safety_bar, t_bar}` y `plateSignal != null`. El resolver devuelve el perfil con `copy(usesPlate = …)`.

**`mvtMps`:** solo estas excepciones; en el resto, `null`.
- `high_bar_back_squat`, `low_bar_back_squat` y `paused_back_squat` con barbell o smith_machine → 0,30.
- `bench_press` y `paused_bench_press` con barbell o smith_machine → 0,17.
- `conventional_deadlift` y `sumo_deadlift` con barbell → 0,15.
- `military_press` con barbell → 0,19.
- `seal_row` con barbell → 0,50.

### Tabla por patrón (los 63 del catálogo; comprobado el 13-oct)
| movementPatternId | Perfil por defecto | Excepciones por definitionId |
|---|---|---|
| knee_dominant, knee_hip_dominant | SQUAT | `wall_sit` → UNSUPPORTED (isométrico). `quads_prensa_piernas`, `quads_sentadilla_hack`, `quads_sentadilla_hack_invertida_maquina`, `quads_sentadilla_v_squat`, `quads_sentadilla_v_squat_invertida_maquina`, `pendulum_squat` → MACHINE_SQUAT. `quads_sentadilla_anderson`, `quads_sentadilla_anderson_frontal_barra_recta` → SQUAT con `startPhase = CONCENTRIC_FIRST` |
| knee_dominant_lengthened (`sissy_squat`) | MACHINE_SQUAT | — |
| unilateral_knee_dominant | LUNGE | `step_up`, `quads_step_up_cajon_frontal`, `quads_step_up_cajon_zercher` → STEP_UP. `walking_lunge`, `quads_zancada_caminando_frontal_barra_recta`, `quads_zancada_caminando_zercher_barra_recta` → UNSUPPORTED (camina fuera de cuadro). `belt_squat` → SQUAT. `pendulum_squat`, `quads_zancada_inversa_maquina_hack`, `quads_zancada_inversa_maquina_v_squat` → MACHINE_SQUAT |
| unilateral_hip_dominant | LUNGE | `glutes_step_up_gluteo` → STEP_UP |
| deadlift, hip_hinge_deficit | DEADLIFT | `hams_peso_muerto_piernas_rigidas_deficit` → HINGE |
| romanian_deadlift, romanian_deadlift_deficit, hip_hinge, hip_hinge_lengthened | HINGE | — |
| hip_extension | HIP_THRUST | `glutes_hiperextension_45`, `glutes_hiperextension_45_zercher` → BACK_EXTENSION. `glutes_patada_gluteo`, `reverse_hyper` → UNSUPPORTED |
| hip_extension_external_rotation (`glutes_frog_pumps`) | HIP_THRUST | — |
| spinal_extension | BACK_EXTENSION | `back_superman_suelo`, `zercher_carry` → UNSUPPORTED |
| horizontal_push | BENCH | `push_up`, `knee_push_up`, `diamond_push_up`, `archer_push_up` → PUSH_UP. `flat_chest_fly`, `incline_chest_fly`, `decline_chest_fly` → FLY. `tren_superior_fondos` → DIP. `tren_superior_cruce_poleas` → UNSUPPORTED. Máquinas, cable y banda (`tren_superior_press_inclinado_maquina_convergente`, `tren_superior_press_pecho_maquina_convergente`, `tren_superior_press_unilateral_polea`, `tren_superior_press_banda_resistencia`) → BENCH con `startPhase = CONCENTRIC_FIRST` |
| vertical_push | OHP | `pike_push_up` → PUSH_UP. `push_jerk`, `split_jerk` → UNSUPPORTED |
| diagonal_push (landmine) | UNSUPPORTED | — |
| vertical_pull | PULL_VERTICAL | `lying_pullover`, `pullover`, `seated_machine_pullover`, `negative_pull_up` → UNSUPPORTED (v2) |
| vertical_pull_abduction (`deltoides_remo_menton`) | PULL_VERTICAL | — |
| horizontal_pull | ROW | — (renegado y gorila se cuentan por lado: `perSide`) |
| horizontal_abduction | UNSUPPORTED | `rear_delt_raise` → RAISE |
| elbow_flexion | CURL | — |
| elbow_extension | TRICEPS_EXTENDED_REST | `triceps_pushdown`, `triceps_patada`, `katana_extension`, `crossbody_triceps_extension`, `triceps_press_maquina`, `triceps_flexiones_esfinge` → TRICEPS_FLEXED_REST. `triceps_fondos_entre_bancos` → DIP |
| shoulder_abduction_full_rom, shoulder_abduction_diagonal, shoulder_flexion | RAISE | — |
| knee_extension | LEG_EXTENSION | — |
| knee_flexion | LEG_CURL | `glute_ham_raise` → UNSUPPORTED (v2) |
| plantar_flexion | CALF | — |
| scapular_elevation | SHRUG | — |
| hip_hinge_explosive, wrist_flexion, wrist_extension, wrist_flexion_extension, pinch_grip, isometric_grip, hip_abduction, hip_adduction, hip_adduction_dynamic, hip_abduction_extension, hip_abduction_external_rotation, hip_abduction_stability, hip_extension_abduction, scapular_retraction, scapular_depression, trunk_flexion, trunk_rotation, lateral_trunk_flexion, spinal_flexion, anti_extension_isometric, anti_extension_pelvic_control, anti_extension_trunk, anti_rotation_trunk, hip_flexion, ankle_dorsiflexion, biarticular_lengthened, eccentric_knee_flexion, knee_dominant_asymmetric, lateral_knee_dominant, unilateral_knee_dominant_asymmetric, neck_extension, neck_flexion, neck_lateral_flexion | UNSUPPORTED | — |

**Motivos de `Unsupported(reason)`:** un código estable más un texto en español para la UI.

| Código | Texto |
|---|---|
| `olympic` | «Los levantamientos olímpicos aún no se analizan» |
| `isometric` | «Los ejercicios isométricos aún no se analizan» |
| `core` | (de CORE; texto en la misma línea) |
| `grip` | (de agarre; texto en la misma línea) |
| `neck` | (de cuello; texto en la misma línea) |
| `machine_plane` | «Este movimiento no se ve bien de lado» |
| `walking` | «Graba sin desplazarte para poder contar» |
| `eccentric_only_v2` | (texto en la misma línea) |
| `unknown_pattern` | (texto en la misma línea) |

## Pruebas (JVM)
- **`AnalysisProfileResolverCoverageTest`:** carga el catálogo real (`src/main/assets/exercise_catalog_v2.json`, como `ExerciseCatalogContractTest`) y, para CADA configuración, construye su `ExerciseRef` (definición, configuración, patrón, equipamiento y lateralidad).
  - Ninguna termina en `NeedsUserChoice`.
  - Ningún patrón queda sin entrada en la tabla: si el catálogo crece, la prueba falla con el patrón nuevo.
  - `Supported` ≥ 75 % de las 553 (imprime el porcentaje y la cuenta por perfil).
  - Para cada `Supported` con `romAngle`, la articulación del perfil aparece en `jointInvolvement` (PRIMARY o SECONDARY):
    - KNEE → `rodilla`;
    - HIP → `cadera`;
    - ELBOW → `codo`;
    - ARM_ELEVATION → `glenohumeral`;
    - TRUNK → `cadera` o `columna-lumbar`.
    - Imprime las excepciones; si alguna es razonable, añádela a una lista blanca comentada en la propia prueba.
- **`AnalysisProfileResolverTest`:**
  - unos 40 pares `definitionId` (+ equipamiento) → perfil, escritos a mano: todas las excepciones de la tabla y un caso típico por patrón soportado;
  - `usesPlate` (barbell sí, mancuernas no);
  - `mvtMps` (back squat barbell 0,30; front squat `null`; bench 0,17; conventional 0,15; military 0,19; seal row 0,50; hex `null`);
  - `userProfileOverride` gana;
  - un ejercicio propio da `NeedsUserChoice` con su conjetura.

**Comandos:**
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.biomechanics.profiles.*"`

## Commits
- `feat(technique): perfiles de análisis y resolver por patrón del catálogo`
- `test(technique): cobertura del catálogo y casos del resolver`
