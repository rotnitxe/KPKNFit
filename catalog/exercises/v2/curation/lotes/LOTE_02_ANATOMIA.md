# Informe de lote: delta anatómico

## Escalar antes de aplicar (principal cambiado en configuraciones protegidas)

| configuración | antes | después | código | tests |
|---|---|---|---|---|
| `high_bar_back_squat__safety_bar` | quadriceps | quadriceps, gluteus_maximus | sí |  |
| `quads_sentadilla_anderson__default` | quadriceps | quadriceps, gluteus_maximus | sí | sí |
| `quads_sentadilla_anderson_frontal_barra_recta__default` | quadriceps | quadriceps, gluteus_maximus |  | sí |
| `quads_sentadilla_bazuca__default` | quadriceps | quadriceps, gluteus_maximus |  | sí |
| `quads_sentadilla_copa__default` | quadriceps | quadriceps, gluteus_maximus | sí | sí |
| `quads_sentadilla_cajon__default` | quadriceps | quadriceps, gluteus_maximus | sí |  |
| `bulgarian_zercher__barbell__zercher` | quadriceps | quadriceps, gluteus_maximus |  | sí |

## Cambios de rol muscular

| configuración | músculo | antes → después | dominante cambia | protegida |
|---|---|---|---|---|
| `front_squat__barbell` | adductors | - → SECONDARY |  | código |
| `front_squat__smith_machine` | adductors | - → SECONDARY |  | código |
| `front_squat__dumbbells` | adductors | - → SECONDARY |  | tests |
| `front_squat__kettlebell` | adductors | - → SECONDARY |  | tests |
| `front_squat__cable` | adductors | - → SECONDARY |  | tests |
| `high_bar_back_squat__barbell` | adductors | - → SECONDARY |  | código |
| `high_bar_back_squat__smith_machine` | adductors | - → SECONDARY |  | código |
| `high_bar_back_squat__safety_bar` | adductors | - → SECONDARY |  | código |
| `high_bar_back_squat__safety_bar` | erector_spinae | - → STABILIZER |  | código |
| `high_bar_back_squat__safety_bar` | gluteus_maximus | SECONDARY → PRIMARY |  | código |
| `high_bar_back_squat__safety_bar` | trapezius | - → STABILIZER |  | código |
| `low_bar_back_squat__barbell` | adductors | - → SECONDARY |  | código |
| `low_bar_back_squat__barbell` | hamstrings | - → STABILIZER |  | código |
| `low_bar_back_squat__smith_machine` | adductors | - → SECONDARY |  | código |
| `low_bar_back_squat__smith_machine` | hamstrings | - → STABILIZER |  | código |
| `quads_prensa_piernas__bilateral` | adductors | - → SECONDARY |  | código |
| `quads_prensa_piernas__unilateral` | adductors | - → SECONDARY |  | código |
| `quads_sentadilla_anderson__default` | adductors | - → SECONDARY |  | código |
| `quads_sentadilla_anderson__default` | core | SECONDARY → STABILIZER |  | código |
| `quads_sentadilla_anderson__default` | erector_spinae | - → STABILIZER |  | código |
| `quads_sentadilla_anderson__default` | gluteus_maximus | SECONDARY → PRIMARY |  | código |
| `quads_sentadilla_anderson_frontal_barra_recta__default` | adductors | - → SECONDARY |  | tests |
| `quads_sentadilla_anderson_frontal_barra_recta__default` | core | SECONDARY → STABILIZER |  | tests |
| `quads_sentadilla_anderson_frontal_barra_recta__default` | erector_spinae | - → STABILIZER |  | tests |
| `quads_sentadilla_anderson_frontal_barra_recta__default` | gluteus_maximus | - → PRIMARY |  | tests |
| `quads_sentadilla_bazuca__default` | adductors | - → SECONDARY |  | tests |
| `quads_sentadilla_bazuca__default` | core | SECONDARY → STABILIZER |  | tests |
| `quads_sentadilla_bazuca__default` | erector_spinae | - → STABILIZER |  | tests |
| `quads_sentadilla_bazuca__default` | gluteus_maximus | SECONDARY → PRIMARY |  | tests |
| `quads_sentadilla_bazuca__default` | trapezius | - → STABILIZER |  | tests |
| `quads_sentadilla_copa__default` | adductors | - → SECONDARY |  | código |
| `quads_sentadilla_copa__default` | core | - → STABILIZER |  | código |
| `quads_sentadilla_copa__default` | erector_spinae | - → STABILIZER |  | código |
| `quads_sentadilla_copa__default` | gluteus_maximus | SECONDARY → PRIMARY |  | código |
| `quads_sentadilla_hack__machine` | adductors | - → SECONDARY |  | código |
| `quads_sentadilla_hack__machine` | core | - → STABILIZER |  | código |
| `quads_sentadilla_hack__barbell` | adductors | - → SECONDARY |  | tests |
| `quads_sentadilla_hack__barbell` | core | - → STABILIZER |  | tests |
| `quads_sentadilla_hack__barbell` | erector_spinae | - → STABILIZER |  | tests |
| `quads_sentadilla_hack__barbell` | forearm | - → STABILIZER |  | tests |
| `quads_sentadilla_hack__smith_machine` | adductors | - → SECONDARY |  | tests |
| `quads_sentadilla_hack__smith_machine` | core | - → STABILIZER |  | tests |
| `quads_sentadilla_hack__smith_machine` | erector_spinae | - → STABILIZER |  | tests |
| `quads_sentadilla_hack__smith_machine` | forearm | - → STABILIZER |  | tests |
| `quads_sentadilla_hack_invertida_maquina__default` | adductors | - → SECONDARY |  | código |
| `quads_sentadilla_hack_invertida_maquina__default` | core | - → STABILIZER |  | código |
| `quads_sentadilla_hack_invertida_maquina__default` | erector_spinae | - → STABILIZER |  | código |
| `quads_sentadilla_somersault__default` | erector_spinae | - → STABILIZER |  | tests |
| `quads_sentadilla_somersault__default` | gluteus_maximus | SECONDARY → STABILIZER |  | tests |
| `quads_sentadilla_v_squat__default` | adductors | - → SECONDARY |  | tests |
| `quads_sentadilla_v_squat__default` | core | - → STABILIZER |  | tests |
| `quads_sentadilla_v_squat_invertida_maquina__default` | adductors | - → SECONDARY |  | tests |
| `quads_sentadilla_v_squat_invertida_maquina__default` | core | - → STABILIZER |  | tests |
| `quads_sentadilla_v_squat_invertida_maquina__default` | erector_spinae | - → STABILIZER |  | tests |
| `quads_sentadilla_zercher_barra_recta__default` | adductors | - → SECONDARY |  |  |
| `quads_sentadilla_zercher_barra_recta__default` | biceps | - → STABILIZER |  |  |
| `quads_sentadilla_zercher_barra_recta__default` | core | SECONDARY → STABILIZER |  |  |
| `quads_sentadilla_zercher_barra_recta__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_sentadilla_zercher_barra_recta__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `sumo_squat__barbell` | erector_spinae | - → STABILIZER |  | tests |
| `sumo_squat__dumbbells` | erector_spinae | - → STABILIZER |  | tests |
| `sumo_squat__dumbbells` | forearm | - → STABILIZER |  | tests |
| `sumo_squat__kettlebell` | erector_spinae | - → STABILIZER |  | tests |
| `sumo_squat__kettlebell` | forearm | - → STABILIZER |  | tests |
| `quads_sentadilla_jefferson__default` | core | - → STABILIZER |  |  |
| `quads_sentadilla_jefferson__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_sentadilla_jefferson__default` | forearm | - → STABILIZER |  |  |
| `quads_sentadilla_jefferson__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `belt_squat__bilateral` | adductors | - → SECONDARY |  | código |
| `belt_squat__unilateral` | adductors | - → SECONDARY |  | código |
| `belt_squat__unilateral` | gluteus_medius | - → STABILIZER |  | código |
| `pendulum_squat__bilateral` | adductors | - → SECONDARY |  | código |
| `pendulum_squat__bilateral` | core | - → STABILIZER |  | código |
| `pendulum_squat__unilateral` | adductors | - → SECONDARY |  | código |
| `pendulum_squat__unilateral` | core | - → STABILIZER |  | código |
| `quads_sentadilla_cajon__default` | adductors | - → SECONDARY |  | código |
| `quads_sentadilla_cajon__default` | core | - → STABILIZER |  | código |
| `quads_sentadilla_cajon__default` | erector_spinae | - → STABILIZER |  | código |
| `quads_sentadilla_cajon__default` | gluteus_maximus | SECONDARY → PRIMARY |  | código |
| `quads_sentadilla_cajon__default` | hamstrings | SECONDARY → STABILIZER |  | código |
| `quads_sentadilla_sumo_frontal__default` | core | SECONDARY → STABILIZER |  |  |
| `quads_sentadilla_sumo_frontal__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_sentadilla_sumo_frontal__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_sentadilla_sumo_zercher__default` | biceps | - → STABILIZER |  |  |
| `quads_sentadilla_sumo_zercher__default` | core | SECONDARY → STABILIZER |  |  |
| `quads_sentadilla_sumo_zercher__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_sentadilla_sumo_zercher__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `sissy_squat__machine` | core | - → STABILIZER |  | código |
| `sissy_squat__machine` | hip_flexors | - → STABILIZER |  | código |
| `sissy_squat__smith_machine` | calves | - → STABILIZER |  | código |
| `sissy_squat__smith_machine` | core | - → STABILIZER |  | código |
| `sissy_squat__dumbbells` | calves | - → STABILIZER |  | tests |
| `sissy_squat__dumbbells` | core | - → STABILIZER |  | tests |
| `sissy_squat__dumbbells` | hip_flexors | - → STABILIZER |  | tests |
| `sissy_squat__plate` | calves | - → STABILIZER |  | tests |
| `sissy_squat__plate` | core | - → STABILIZER |  | tests |
| `sissy_squat__plate` | hip_flexors | - → STABILIZER |  | tests |
| `bulgarian_split_squat__barbell` | adductors | - → SECONDARY |  | código |
| `bulgarian_split_squat__barbell` | calves | - → SECONDARY |  | código |
| `bulgarian_split_squat__barbell` | gluteus_medius | - → STABILIZER |  | código |
| `bulgarian_split_squat__barbell` | hamstrings | - → SECONDARY |  | código |
| `bulgarian_split_squat__smith_machine` | adductors | - → SECONDARY |  | código |
| `bulgarian_split_squat__smith_machine` | calves | - → SECONDARY |  | código |
| `bulgarian_split_squat__smith_machine` | gluteus_medius | - → STABILIZER |  | código |
| `bulgarian_split_squat__smith_machine` | hamstrings | - → SECONDARY |  | código |
| `bulgarian_split_squat__machine` | adductors | - → SECONDARY |  | tests |
| `bulgarian_split_squat__machine` | calves | - → SECONDARY |  | tests |
| `bulgarian_split_squat__machine` | forearm | - → STABILIZER |  | tests |
| `bulgarian_split_squat__machine` | gluteus_medius | - → STABILIZER |  | tests |
| `bulgarian_split_squat__machine` | hamstrings | - → SECONDARY |  | tests |
| `bulgarian_split_squat__dumbbells` | adductors | - → SECONDARY |  | código |
| `bulgarian_split_squat__dumbbells` | calves | - → SECONDARY |  | código |
| `bulgarian_split_squat__dumbbells` | forearm | - → STABILIZER |  | código |
| `bulgarian_split_squat__dumbbells` | gluteus_medius | - → STABILIZER |  | código |
| `bulgarian_split_squat__dumbbells` | hamstrings | - → SECONDARY |  | código |
| `bulgarian_split_squat__cable` | adductors | - → SECONDARY |  | tests |
| `bulgarian_split_squat__cable` | calves | - → SECONDARY |  | tests |
| `bulgarian_split_squat__cable` | forearm | - → STABILIZER |  | tests |
| `bulgarian_split_squat__cable` | gluteus_medius | - → STABILIZER |  | tests |
| `bulgarian_split_squat__cable` | hamstrings | - → SECONDARY |  | tests |
| `bulgarian_split_squat__kettlebell` | adductors | - → SECONDARY |  | tests |
| `bulgarian_split_squat__kettlebell` | calves | - → SECONDARY |  | tests |
| `bulgarian_split_squat__kettlebell` | forearm | - → STABILIZER |  | tests |
| `bulgarian_split_squat__kettlebell` | gluteus_medius | - → STABILIZER |  | tests |
| `bulgarian_split_squat__kettlebell` | hamstrings | - → SECONDARY |  | tests |
| `bulgarian_zercher__barbell__zercher` | adductors | - → SECONDARY |  | tests |
| `bulgarian_zercher__barbell__zercher` | biceps | - → STABILIZER |  | tests |
| `bulgarian_zercher__barbell__zercher` | calves | - → SECONDARY |  | tests |
| `bulgarian_zercher__barbell__zercher` | core | SECONDARY → STABILIZER |  | tests |
| `bulgarian_zercher__barbell__zercher` | gluteus_maximus | SECONDARY → PRIMARY |  | tests |
| `bulgarian_zercher__barbell__zercher` | gluteus_medius | - → STABILIZER |  | tests |
| `bulgarian_zercher__barbell__zercher` | hamstrings | - → SECONDARY |  | tests |

## Cambios articulares

| configuración | articulación | antes → después |
|---|---|---|
| `front_squat__barbell` | cadera | SECONDARY → PRIMARY |
| `front_squat__barbell` | columna-toracica | - → STABILIZER |
| `front_squat__barbell` | glenohumeral | - → STABILIZER |
| `front_squat__smith_machine` | cadera | SECONDARY → PRIMARY |
| `front_squat__smith_machine` | columna-toracica | - → STABILIZER |
| `front_squat__smith_machine` | glenohumeral | - → STABILIZER |
| `front_squat__dumbbells` | cadera | SECONDARY → PRIMARY |
| `front_squat__dumbbells` | columna-toracica | - → STABILIZER |
| `front_squat__dumbbells` | glenohumeral | - → STABILIZER |
| `front_squat__kettlebell` | cadera | SECONDARY → PRIMARY |
| `front_squat__kettlebell` | columna-toracica | - → STABILIZER |
| `front_squat__kettlebell` | glenohumeral | - → STABILIZER |
| `front_squat__cable` | cadera | SECONDARY → PRIMARY |
| `front_squat__cable` | columna-toracica | - → STABILIZER |
| `front_squat__cable` | glenohumeral | - → STABILIZER |
| `high_bar_back_squat__barbell` | cadera | SECONDARY → PRIMARY |
| `high_bar_back_squat__smith_machine` | cadera | SECONDARY → PRIMARY |
| `high_bar_back_squat__safety_bar` | cadera | SECONDARY → PRIMARY |
| `high_bar_back_squat__safety_bar` | escapulotoracica | - → STABILIZER |
| `low_bar_back_squat__barbell` | cadera | SECONDARY → PRIMARY |
| `low_bar_back_squat__smith_machine` | cadera | SECONDARY → PRIMARY |
| `quads_prensa_piernas__bilateral` | cadera | SECONDARY → PRIMARY |
| `quads_prensa_piernas__unilateral` | cadera | SECONDARY → PRIMARY |
| `quads_sentadilla_anderson__default` | cadera | SECONDARY → PRIMARY |
| `quads_sentadilla_anderson_frontal_barra_recta__default` | cadera | SECONDARY → PRIMARY |
| `quads_sentadilla_anderson_frontal_barra_recta__default` | columna-toracica | - → STABILIZER |
| `quads_sentadilla_anderson_frontal_barra_recta__default` | glenohumeral | - → STABILIZER |
| `quads_sentadilla_bazuca__default` | cadera | SECONDARY → PRIMARY |
| `quads_sentadilla_bazuca__default` | escapulotoracica | - → STABILIZER |
| `quads_sentadilla_copa__default` | cadera | SECONDARY → PRIMARY |
| `quads_sentadilla_hack__barbell` | muñeca | - → STABILIZER |
| `quads_sentadilla_hack__smith_machine` | muñeca | - → STABILIZER |
| `quads_sentadilla_somersault__default` | cadera | SECONDARY → STABILIZER |
| `quads_sentadilla_zercher_barra_recta__default` | cadera | SECONDARY → PRIMARY |
| `quads_sentadilla_zercher_barra_recta__default` | codo | - → STABILIZER |
| `quads_sentadilla_zercher_barra_recta__default` | columna-toracica | - → STABILIZER |
| `sumo_squat__barbell` | cadera | SECONDARY → PRIMARY |
| `sumo_squat__dumbbells` | cadera | SECONDARY → PRIMARY |
| `sumo_squat__dumbbells` | muñeca | - → STABILIZER |
| `sumo_squat__kettlebell` | cadera | SECONDARY → PRIMARY |
| `sumo_squat__kettlebell` | muñeca | - → STABILIZER |
| `quads_sentadilla_sin_carga__default` | cadera | SECONDARY → PRIMARY |
| `quads_sentadilla_jefferson__default` | columna-lumbar | - → STABILIZER |
| `quads_sentadilla_jefferson__default` | muñeca | - → STABILIZER |
| `quads_sentadilla_jefferson__default` | sacroiliaca | STABILIZER → - |
| `quads_sentadilla_jefferson__default` | tobillo | SECONDARY → STABILIZER |
| `belt_squat__unilateral` | cadera | PRIMARY → SECONDARY |
| `belt_squat__unilateral` | columna-lumbar | - → STABILIZER |
| `belt_squat__unilateral` | sacroiliaca | STABILIZER → - |
| `belt_squat__unilateral` | tobillo | SECONDARY → STABILIZER |
| `pendulum_squat__unilateral` | cadera | PRIMARY → SECONDARY |
| `pendulum_squat__unilateral` | columna-lumbar | - → STABILIZER |
| `pendulum_squat__unilateral` | sacroiliaca | STABILIZER → - |
| `pendulum_squat__unilateral` | tobillo | SECONDARY → STABILIZER |
| `quads_sentadilla_cajon__default` | cadera | SECONDARY → PRIMARY |
| `quads_sentadilla_sumo_frontal__default` | cadera | SECONDARY → PRIMARY |
| `quads_sentadilla_sumo_frontal__default` | columna-toracica | - → STABILIZER |
| `quads_sentadilla_sumo_frontal__default` | glenohumeral | - → STABILIZER |
| `quads_sentadilla_sumo_zercher__default` | cadera | SECONDARY → PRIMARY |
| `quads_sentadilla_sumo_zercher__default` | codo | - → STABILIZER |
| `quads_sentadilla_sumo_zercher__default` | columna-toracica | - → STABILIZER |
| `sissy_squat__machine` | cadera | SECONDARY → STABILIZER |
| `sissy_squat__smith_machine` | cadera | SECONDARY → STABILIZER |
| `sissy_squat__dumbbells` | cadera | SECONDARY → STABILIZER |
| `sissy_squat__plate` | cadera | SECONDARY → STABILIZER |
| `bulgarian_split_squat__barbell` | columna-lumbar | - → STABILIZER |
| `bulgarian_split_squat__barbell` | sacroiliaca | STABILIZER → - |
| `bulgarian_split_squat__smith_machine` | columna-lumbar | - → STABILIZER |
| `bulgarian_split_squat__smith_machine` | sacroiliaca | STABILIZER → - |
| `bulgarian_split_squat__machine` | columna-lumbar | - → STABILIZER |
| `bulgarian_split_squat__machine` | muñeca | - → STABILIZER |
| `bulgarian_split_squat__machine` | sacroiliaca | STABILIZER → - |
| `bulgarian_split_squat__dumbbells` | columna-lumbar | - → STABILIZER |
| `bulgarian_split_squat__dumbbells` | muñeca | - → STABILIZER |
| `bulgarian_split_squat__dumbbells` | sacroiliaca | STABILIZER → - |
| `bulgarian_split_squat__cable` | columna-lumbar | - → STABILIZER |
| `bulgarian_split_squat__cable` | muñeca | - → STABILIZER |
| `bulgarian_split_squat__cable` | sacroiliaca | STABILIZER → - |
| `bulgarian_split_squat__kettlebell` | codo | - → STABILIZER |
| `bulgarian_split_squat__kettlebell` | columna-lumbar | - → STABILIZER |
| `bulgarian_split_squat__kettlebell` | muñeca | - → STABILIZER |
| `bulgarian_split_squat__kettlebell` | sacroiliaca | STABILIZER → - |
| `bulgarian_zercher__barbell__zercher` | codo | - → STABILIZER |
| `bulgarian_zercher__barbell__zercher` | columna-lumbar | - → STABILIZER |
| `bulgarian_zercher__barbell__zercher` | sacroiliaca | STABILIZER → - |

## Topes de palabras (léxico): definiciones CURATED que usan cada palabra

| tope | usadas | máximo | en este lote |
|---|---|---|---|
| budget.claridad | 1 | 3 |  |
| budget.referencia | 1 | 8 |  |
| budget.concentrar | 1 | 15 |  |
| budget.repartir | 2 | 5 |  |
| budget.medir | 2 | 8 |  |
| budget.distinto | 2 | 8 |  |
