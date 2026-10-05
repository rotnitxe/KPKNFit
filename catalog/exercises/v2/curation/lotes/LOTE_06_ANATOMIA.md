# Informe de lote: delta anatómico

## Escalar antes de aplicar (principal cambiado en configuraciones protegidas)

| configuración | antes | después | código | tests |
|---|---|---|---|---|
| `quads_sentadilla_cosaca__default` | quadriceps | quadriceps, gluteus_maximus | sí |  |
| `quads_zancada_inversa_maquina_hack__default` | quadriceps | quadriceps, gluteus_maximus | sí |  |

## Cambios de rol muscular

| configuración | músculo | antes → después | dominante cambia | protegida |
|---|---|---|---|---|
| `forward_lunge__barbell` | adductors | - → SECONDARY |  |  |
| `forward_lunge__barbell` | calves | - → SECONDARY |  |  |
| `forward_lunge__barbell` | erector_spinae | - → STABILIZER |  |  |
| `forward_lunge__barbell` | gluteus_medius | - → STABILIZER |  |  |
| `forward_lunge__smith_machine` | adductors | - → SECONDARY |  |  |
| `forward_lunge__smith_machine` | calves | - → SECONDARY |  |  |
| `forward_lunge__smith_machine` | erector_spinae | - → STABILIZER |  |  |
| `forward_lunge__smith_machine` | gluteus_medius | - → STABILIZER |  |  |
| `forward_lunge__dumbbells` | adductors | - → SECONDARY |  | código |
| `forward_lunge__dumbbells` | calves | - → SECONDARY |  | código |
| `forward_lunge__dumbbells` | deltoid | - → STABILIZER |  | código |
| `forward_lunge__dumbbells` | erector_spinae | - → STABILIZER |  | código |
| `forward_lunge__dumbbells` | forearm | - → STABILIZER |  | código |
| `forward_lunge__dumbbells` | gluteus_medius | - → STABILIZER |  | código |
| `forward_lunge__kettlebell` | adductors | - → SECONDARY |  |  |
| `forward_lunge__kettlebell` | biceps | - → STABILIZER |  |  |
| `forward_lunge__kettlebell` | calves | - → SECONDARY |  |  |
| `forward_lunge__kettlebell` | deltoid | - → STABILIZER |  |  |
| `forward_lunge__kettlebell` | erector_spinae | - → STABILIZER |  |  |
| `forward_lunge__kettlebell` | forearm | - → STABILIZER |  |  |
| `forward_lunge__kettlebell` | gluteus_medius | - → STABILIZER |  |  |
| `forward_lunge__cable` | adductors | - → SECONDARY |  |  |
| `forward_lunge__cable` | calves | - → SECONDARY |  |  |
| `forward_lunge__cable` | deltoid | - → STABILIZER |  |  |
| `forward_lunge__cable` | erector_spinae | - → STABILIZER |  |  |
| `forward_lunge__cable` | forearm | - → STABILIZER |  |  |
| `forward_lunge__cable` | gluteus_medius | - → STABILIZER |  |  |
| `quads_sentadilla_cosaca__default` | calves | - → SECONDARY |  | código |
| `quads_sentadilla_cosaca__default` | core | - → STABILIZER |  | código |
| `quads_sentadilla_cosaca__default` | erector_spinae | - → STABILIZER |  | código |
| `quads_sentadilla_cosaca__default` | gluteus_maximus | - → PRIMARY |  | código |
| `quads_sentadilla_cosaca__default` | gluteus_medius | - → STABILIZER |  | código |
| `reverse_lunge__barbell` | adductors | - → SECONDARY |  | tests |
| `reverse_lunge__barbell` | calves | - → SECONDARY |  | tests |
| `reverse_lunge__barbell` | erector_spinae | - → STABILIZER |  | tests |
| `reverse_lunge__barbell` | gluteus_medius | - → STABILIZER |  | tests |
| `reverse_lunge__smith_machine` | adductors | - → SECONDARY |  |  |
| `reverse_lunge__smith_machine` | calves | - → SECONDARY |  |  |
| `reverse_lunge__smith_machine` | erector_spinae | - → STABILIZER |  |  |
| `reverse_lunge__smith_machine` | gluteus_medius | - → STABILIZER |  |  |
| `reverse_lunge__dumbbells` | adductors | - → SECONDARY |  |  |
| `reverse_lunge__dumbbells` | calves | - → SECONDARY |  |  |
| `reverse_lunge__dumbbells` | deltoid | - → STABILIZER |  |  |
| `reverse_lunge__dumbbells` | erector_spinae | - → STABILIZER |  |  |
| `reverse_lunge__dumbbells` | forearm | - → STABILIZER |  |  |
| `reverse_lunge__dumbbells` | gluteus_medius | - → STABILIZER |  |  |
| `reverse_lunge__kettlebell` | adductors | - → SECONDARY |  |  |
| `reverse_lunge__kettlebell` | biceps | - → STABILIZER |  |  |
| `reverse_lunge__kettlebell` | calves | - → SECONDARY |  |  |
| `reverse_lunge__kettlebell` | deltoid | - → STABILIZER |  |  |
| `reverse_lunge__kettlebell` | erector_spinae | - → STABILIZER |  |  |
| `reverse_lunge__kettlebell` | forearm | - → STABILIZER |  |  |
| `reverse_lunge__kettlebell` | gluteus_medius | - → STABILIZER |  |  |
| `reverse_lunge__cable` | adductors | - → SECONDARY |  |  |
| `reverse_lunge__cable` | calves | - → SECONDARY |  |  |
| `reverse_lunge__cable` | deltoid | - → STABILIZER |  |  |
| `reverse_lunge__cable` | erector_spinae | - → STABILIZER |  |  |
| `reverse_lunge__cable` | forearm | - → STABILIZER |  |  |
| `reverse_lunge__cable` | gluteus_medius | - → STABILIZER |  |  |
| `reverse_lunge__bodyweight` | adductors | - → SECONDARY |  | código |
| `reverse_lunge__bodyweight` | calves | - → SECONDARY |  | código |
| `reverse_lunge__bodyweight` | erector_spinae | - → STABILIZER |  | código |
| `reverse_lunge__bodyweight` | gluteus_medius | - → STABILIZER |  | código |
| `step_up__barbell` | adductors | - → SECONDARY |  |  |
| `step_up__barbell` | calves | - → SECONDARY |  |  |
| `step_up__barbell` | erector_spinae | - → STABILIZER |  |  |
| `step_up__barbell` | gluteus_medius | - → STABILIZER |  |  |
| `step_up__smith_machine` | adductors | - → SECONDARY |  |  |
| `step_up__smith_machine` | calves | - → SECONDARY |  |  |
| `step_up__smith_machine` | erector_spinae | - → STABILIZER |  |  |
| `step_up__smith_machine` | gluteus_medius | - → STABILIZER |  |  |
| `step_up__dumbbells` | adductors | - → SECONDARY |  |  |
| `step_up__dumbbells` | calves | - → SECONDARY |  |  |
| `step_up__dumbbells` | deltoid | - → STABILIZER |  |  |
| `step_up__dumbbells` | erector_spinae | - → STABILIZER |  |  |
| `step_up__dumbbells` | forearm | - → STABILIZER |  |  |
| `step_up__dumbbells` | gluteus_medius | - → STABILIZER |  |  |
| `step_up__kettlebell` | adductors | - → SECONDARY |  |  |
| `step_up__kettlebell` | biceps | - → STABILIZER |  |  |
| `step_up__kettlebell` | calves | - → SECONDARY |  |  |
| `step_up__kettlebell` | deltoid | - → STABILIZER |  |  |
| `step_up__kettlebell` | erector_spinae | - → STABILIZER |  |  |
| `step_up__kettlebell` | forearm | - → STABILIZER |  |  |
| `step_up__kettlebell` | gluteus_medius | - → STABILIZER |  |  |
| `step_up__cable` | adductors | - → SECONDARY |  |  |
| `step_up__cable` | calves | - → SECONDARY |  |  |
| `step_up__cable` | deltoid | - → STABILIZER |  |  |
| `step_up__cable` | erector_spinae | - → STABILIZER |  |  |
| `step_up__cable` | forearm | - → STABILIZER |  |  |
| `step_up__cable` | gluteus_medius | - → STABILIZER |  |  |
| `glutes_step_up_gluteo__default` | adductors | - → SECONDARY |  |  |
| `glutes_step_up_gluteo__default` | calves | - → SECONDARY |  |  |
| `glutes_step_up_gluteo__default` | core | - → STABILIZER |  |  |
| `glutes_step_up_gluteo__default` | deltoid | - → STABILIZER |  |  |
| `glutes_step_up_gluteo__default` | erector_spinae | - → STABILIZER |  |  |
| `glutes_step_up_gluteo__default` | forearm | - → STABILIZER |  |  |
| `glutes_step_up_gluteo__default` | gluteus_medius | - → STABILIZER |  |  |
| `glutes_step_up_gluteo__default` | quadriceps | - → PRIMARY |  |  |
| `glutes_zancada_cruzada__default` | adductors | - → SECONDARY |  |  |
| `glutes_zancada_cruzada__default` | calves | - → SECONDARY |  |  |
| `glutes_zancada_cruzada__default` | core | - → STABILIZER |  |  |
| `glutes_zancada_cruzada__default` | deltoid | - → STABILIZER |  |  |
| `glutes_zancada_cruzada__default` | erector_spinae | - → STABILIZER |  |  |
| `glutes_zancada_cruzada__default` | forearm | - → STABILIZER |  |  |
| `glutes_zancada_cruzada__default` | gluteus_medius | - → STABILIZER |  |  |
| `glutes_zancada_cruzada__default` | hamstrings | - → SECONDARY |  |  |
| `glutes_zancada_cruzada__default` | quadriceps | SECONDARY → PRIMARY |  |  |
| `quads_sentadilla_bulgara_somersault__default` | adductors | - → SECONDARY |  |  |
| `quads_sentadilla_bulgara_somersault__default` | calves | - → SECONDARY |  |  |
| `quads_sentadilla_bulgara_somersault__default` | core | - → STABILIZER |  |  |
| `quads_sentadilla_bulgara_somersault__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_sentadilla_bulgara_somersault__default` | forearm | - → STABILIZER |  |  |
| `quads_sentadilla_bulgara_somersault__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_sentadilla_bulgara_somersault__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_sentadilla_bulgara_somersault__default` | hamstrings | - → SECONDARY |  |  |
| `quads_sentadilla_pistola__default` | adductors | - → SECONDARY |  |  |
| `quads_sentadilla_pistola__default` | calves | - → SECONDARY |  |  |
| `quads_sentadilla_pistola__default` | core | SECONDARY → STABILIZER |  |  |
| `quads_sentadilla_pistola__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_sentadilla_pistola__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_sentadilla_pistola__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_sentadilla_pistola__default` | hamstrings | - → SECONDARY |  |  |
| `quads_sentadilla_pistola__default` | hip_flexors | - → STABILIZER |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | adductors | - → SECONDARY |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | biceps | - → SECONDARY |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | calves | - → SECONDARY |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | core | - → STABILIZER |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | deltoid | - → SECONDARY |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | forearm | - → STABILIZER |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | hamstrings | - → SECONDARY |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | hip_flexors | - → STABILIZER |  |  |
| `quads_sentadilla_pistola_asistida_trx__default` | latissimus_dorsi | - → SECONDARY |  |  |
| `quads_step_up_cajon_frontal__default` | adductors | - → SECONDARY |  |  |
| `quads_step_up_cajon_frontal__default` | calves | - → SECONDARY |  |  |
| `quads_step_up_cajon_frontal__default` | core | - → STABILIZER |  |  |
| `quads_step_up_cajon_frontal__default` | deltoid | - → STABILIZER |  |  |
| `quads_step_up_cajon_frontal__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_step_up_cajon_frontal__default` | forearm | - → STABILIZER |  |  |
| `quads_step_up_cajon_frontal__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_step_up_cajon_frontal__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_step_up_cajon_frontal__default` | hamstrings | - → SECONDARY |  |  |
| `quads_step_up_cajon_zercher__default` | adductors | - → SECONDARY |  |  |
| `quads_step_up_cajon_zercher__default` | biceps | - → STABILIZER |  |  |
| `quads_step_up_cajon_zercher__default` | calves | - → SECONDARY |  |  |
| `quads_step_up_cajon_zercher__default` | core | - → STABILIZER |  |  |
| `quads_step_up_cajon_zercher__default` | deltoid | - → STABILIZER |  |  |
| `quads_step_up_cajon_zercher__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_step_up_cajon_zercher__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_step_up_cajon_zercher__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_step_up_cajon_zercher__default` | hamstrings | - → SECONDARY |  |  |
| `quads_zancada_caminando_frontal_barra_recta__default` | adductors | - → SECONDARY |  |  |
| `quads_zancada_caminando_frontal_barra_recta__default` | calves | - → SECONDARY |  |  |
| `quads_zancada_caminando_frontal_barra_recta__default` | core | - → STABILIZER |  |  |
| `quads_zancada_caminando_frontal_barra_recta__default` | deltoid | - → STABILIZER |  |  |
| `quads_zancada_caminando_frontal_barra_recta__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_zancada_caminando_frontal_barra_recta__default` | forearm | - → STABILIZER |  |  |
| `quads_zancada_caminando_frontal_barra_recta__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_zancada_caminando_frontal_barra_recta__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_zancada_caminando_frontal_barra_recta__default` | hamstrings | - → SECONDARY |  |  |
| `quads_zancada_caminando_zercher_barra_recta__default` | adductors | - → SECONDARY |  |  |
| `quads_zancada_caminando_zercher_barra_recta__default` | biceps | - → STABILIZER |  |  |
| `quads_zancada_caminando_zercher_barra_recta__default` | calves | - → SECONDARY |  |  |
| `quads_zancada_caminando_zercher_barra_recta__default` | core | - → STABILIZER |  |  |
| `quads_zancada_caminando_zercher_barra_recta__default` | deltoid | - → STABILIZER |  |  |
| `quads_zancada_caminando_zercher_barra_recta__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_zancada_caminando_zercher_barra_recta__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_zancada_caminando_zercher_barra_recta__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_zancada_caminando_zercher_barra_recta__default` | hamstrings | - → SECONDARY |  |  |
| `quads_zancada_frontal_zercher__default` | adductors | - → SECONDARY |  |  |
| `quads_zancada_frontal_zercher__default` | biceps | - → STABILIZER |  |  |
| `quads_zancada_frontal_zercher__default` | calves | - → SECONDARY |  |  |
| `quads_zancada_frontal_zercher__default` | core | - → STABILIZER |  |  |
| `quads_zancada_frontal_zercher__default` | deltoid | - → STABILIZER |  |  |
| `quads_zancada_frontal_zercher__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_zancada_frontal_zercher__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_zancada_frontal_zercher__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_zancada_frontal_zercher__default` | hamstrings | - → SECONDARY |  |  |
| `quads_zancada_inversa_frontal__default` | adductors | - → SECONDARY |  |  |
| `quads_zancada_inversa_frontal__default` | calves | - → SECONDARY |  |  |
| `quads_zancada_inversa_frontal__default` | core | - → STABILIZER |  |  |
| `quads_zancada_inversa_frontal__default` | deltoid | - → STABILIZER |  |  |
| `quads_zancada_inversa_frontal__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_zancada_inversa_frontal__default` | forearm | - → STABILIZER |  |  |
| `quads_zancada_inversa_frontal__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_zancada_inversa_frontal__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_zancada_inversa_frontal__default` | hamstrings | - → SECONDARY |  |  |
| `quads_zancada_inversa_maquina_hack__default` | adductors | - → SECONDARY |  | código |
| `quads_zancada_inversa_maquina_hack__default` | calves | - → SECONDARY |  | código |
| `quads_zancada_inversa_maquina_hack__default` | gluteus_maximus | SECONDARY → PRIMARY |  | código |
| `quads_zancada_inversa_maquina_hack__default` | gluteus_medius | - → STABILIZER |  | código |
| `quads_zancada_inversa_maquina_hack__default` | hamstrings | - → SECONDARY |  | código |
| `quads_zancada_inversa_maquina_v_squat__default` | adductors | - → SECONDARY |  |  |
| `quads_zancada_inversa_maquina_v_squat__default` | calves | - → SECONDARY |  |  |
| `quads_zancada_inversa_maquina_v_squat__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_zancada_inversa_maquina_v_squat__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_zancada_inversa_maquina_v_squat__default` | hamstrings | - → SECONDARY |  |  |
| `quads_zancada_inversa_zercher__default` | adductors | - → SECONDARY |  |  |
| `quads_zancada_inversa_zercher__default` | biceps | - → STABILIZER |  |  |
| `quads_zancada_inversa_zercher__default` | calves | - → SECONDARY |  |  |
| `quads_zancada_inversa_zercher__default` | core | - → STABILIZER |  |  |
| `quads_zancada_inversa_zercher__default` | deltoid | - → STABILIZER |  |  |
| `quads_zancada_inversa_zercher__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_zancada_inversa_zercher__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_zancada_inversa_zercher__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_zancada_inversa_zercher__default` | hamstrings | - → SECONDARY |  |  |
| `quads_sentadilla_bulgara_jefferson__default` | calves | - → SECONDARY |  |  |
| `quads_sentadilla_bulgara_jefferson__default` | core | - → STABILIZER |  |  |
| `quads_sentadilla_bulgara_jefferson__default` | deltoid | - → STABILIZER |  |  |
| `quads_sentadilla_bulgara_jefferson__default` | erector_spinae | - → STABILIZER |  |  |
| `quads_sentadilla_bulgara_jefferson__default` | forearm | - → STABILIZER |  |  |
| `quads_sentadilla_bulgara_jefferson__default` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `quads_sentadilla_bulgara_jefferson__default` | gluteus_medius | - → STABILIZER |  |  |
| `quads_sentadilla_bulgara_jefferson__default` | hamstrings | - → SECONDARY |  |  |
| `walking_lunge__barbell` | adductors | - → SECONDARY |  | código |
| `walking_lunge__barbell` | calves | - → SECONDARY |  | código |
| `walking_lunge__barbell` | erector_spinae | - → STABILIZER |  | código |
| `walking_lunge__barbell` | gluteus_medius | - → STABILIZER |  | código |
| `walking_lunge__dumbbells` | adductors | - → SECONDARY |  | código |
| `walking_lunge__dumbbells` | calves | - → SECONDARY |  | código |
| `walking_lunge__dumbbells` | deltoid | - → STABILIZER |  | código |
| `walking_lunge__dumbbells` | erector_spinae | - → STABILIZER |  | código |
| `walking_lunge__dumbbells` | forearm | - → STABILIZER |  | código |
| `walking_lunge__dumbbells` | gluteus_medius | - → STABILIZER |  | código |
| `walking_lunge__kettlebell` | adductors | - → SECONDARY |  | código |
| `walking_lunge__kettlebell` | biceps | - → STABILIZER |  | código |
| `walking_lunge__kettlebell` | calves | - → SECONDARY |  | código |
| `walking_lunge__kettlebell` | deltoid | - → STABILIZER |  | código |
| `walking_lunge__kettlebell` | erector_spinae | - → STABILIZER |  | código |
| `walking_lunge__kettlebell` | forearm | - → STABILIZER |  | código |
| `walking_lunge__kettlebell` | gluteus_medius | - → STABILIZER |  | código |
| `bulgarian_split_squat__barbell` | erector_spinae | - → STABILIZER |  | código |
| `bulgarian_split_squat__smith_machine` | erector_spinae | - → STABILIZER |  | código |
| `bulgarian_split_squat__machine` | deltoid | - → STABILIZER |  | tests |
| `bulgarian_split_squat__machine` | erector_spinae | - → STABILIZER |  | tests |
| `bulgarian_split_squat__dumbbells` | deltoid | - → STABILIZER |  | código |
| `bulgarian_split_squat__dumbbells` | erector_spinae | - → STABILIZER |  | código |
| `bulgarian_split_squat__cable` | deltoid | - → STABILIZER |  | tests |
| `bulgarian_split_squat__cable` | erector_spinae | - → STABILIZER |  | tests |
| `bulgarian_split_squat__kettlebell` | biceps | - → STABILIZER |  | tests |
| `bulgarian_split_squat__kettlebell` | deltoid | - → STABILIZER |  | tests |
| `bulgarian_split_squat__kettlebell` | erector_spinae | - → STABILIZER |  | tests |
| `bulgarian_zercher__barbell__zercher` | deltoid | - → STABILIZER |  | tests |
| `bulgarian_zercher__barbell__zercher` | erector_spinae | - → STABILIZER |  | tests |

## Cambios articulares

| configuración | articulación | antes → después |
|---|---|---|
| `forward_lunge__barbell` | columna-lumbar | - → STABILIZER |
| `forward_lunge__smith_machine` | columna-lumbar | - → STABILIZER |
| `forward_lunge__dumbbells` | columna-lumbar | - → STABILIZER |
| `forward_lunge__dumbbells` | glenohumeral | - → STABILIZER |
| `forward_lunge__dumbbells` | muñeca | - → STABILIZER |
| `forward_lunge__kettlebell` | codo | - → STABILIZER |
| `forward_lunge__kettlebell` | columna-lumbar | - → STABILIZER |
| `forward_lunge__kettlebell` | glenohumeral | - → STABILIZER |
| `forward_lunge__kettlebell` | muñeca | - → STABILIZER |
| `forward_lunge__cable` | columna-lumbar | - → STABILIZER |
| `forward_lunge__cable` | glenohumeral | - → STABILIZER |
| `forward_lunge__cable` | muñeca | - → STABILIZER |
| `quads_sentadilla_cosaca__default` | columna-lumbar | - → STABILIZER |
| `reverse_lunge__barbell` | columna-lumbar | - → STABILIZER |
| `reverse_lunge__smith_machine` | columna-lumbar | - → STABILIZER |
| `reverse_lunge__dumbbells` | columna-lumbar | - → STABILIZER |
| `reverse_lunge__dumbbells` | glenohumeral | - → STABILIZER |
| `reverse_lunge__dumbbells` | muñeca | - → STABILIZER |
| `reverse_lunge__kettlebell` | codo | - → STABILIZER |
| `reverse_lunge__kettlebell` | columna-lumbar | - → STABILIZER |
| `reverse_lunge__kettlebell` | glenohumeral | - → STABILIZER |
| `reverse_lunge__kettlebell` | muñeca | - → STABILIZER |
| `reverse_lunge__cable` | columna-lumbar | - → STABILIZER |
| `reverse_lunge__cable` | glenohumeral | - → STABILIZER |
| `reverse_lunge__cable` | muñeca | - → STABILIZER |
| `reverse_lunge__bodyweight` | columna-lumbar | - → STABILIZER |
| `step_up__barbell` | columna-lumbar | - → STABILIZER |
| `step_up__smith_machine` | columna-lumbar | - → STABILIZER |
| `step_up__dumbbells` | columna-lumbar | - → STABILIZER |
| `step_up__dumbbells` | glenohumeral | - → STABILIZER |
| `step_up__dumbbells` | muñeca | - → STABILIZER |
| `step_up__kettlebell` | codo | - → STABILIZER |
| `step_up__kettlebell` | columna-lumbar | - → STABILIZER |
| `step_up__kettlebell` | glenohumeral | - → STABILIZER |
| `step_up__kettlebell` | muñeca | - → STABILIZER |
| `step_up__cable` | columna-lumbar | - → STABILIZER |
| `step_up__cable` | glenohumeral | - → STABILIZER |
| `step_up__cable` | muñeca | - → STABILIZER |
| `glutes_step_up_gluteo__default` | glenohumeral | - → STABILIZER |
| `glutes_step_up_gluteo__default` | muñeca | - → STABILIZER |
| `glutes_step_up_gluteo__default` | rodilla | SECONDARY → PRIMARY |
| `glutes_step_up_gluteo__default` | tobillo | - → SECONDARY |
| `glutes_zancada_cruzada__default` | glenohumeral | - → STABILIZER |
| `glutes_zancada_cruzada__default` | muñeca | - → STABILIZER |
| `glutes_zancada_cruzada__default` | rodilla | SECONDARY → PRIMARY |
| `glutes_zancada_cruzada__default` | sacroiliaca | STABILIZER → - |
| `glutes_zancada_cruzada__default` | tobillo | - → SECONDARY |
| `quads_sentadilla_bulgara_somersault__default` | columna-lumbar | - → STABILIZER |
| `quads_sentadilla_bulgara_somersault__default` | muñeca | - → STABILIZER |
| `quads_sentadilla_bulgara_somersault__default` | sacroiliaca | STABILIZER → - |
| `quads_sentadilla_pistola__default` | columna-lumbar | - → STABILIZER |
| `quads_sentadilla_pistola_asistida_trx__default` | codo | - → SECONDARY |
| `quads_sentadilla_pistola_asistida_trx__default` | columna-lumbar | - → STABILIZER |
| `quads_sentadilla_pistola_asistida_trx__default` | glenohumeral | - → SECONDARY |
| `quads_sentadilla_pistola_asistida_trx__default` | muñeca | - → STABILIZER |
| `quads_step_up_cajon_frontal__default` | columna-lumbar | - → STABILIZER |
| `quads_step_up_cajon_frontal__default` | glenohumeral | - → STABILIZER |
| `quads_step_up_cajon_frontal__default` | muñeca | - → STABILIZER |
| `quads_step_up_cajon_zercher__default` | codo | - → STABILIZER |
| `quads_step_up_cajon_zercher__default` | columna-lumbar | - → STABILIZER |
| `quads_step_up_cajon_zercher__default` | glenohumeral | - → STABILIZER |
| `quads_zancada_caminando_frontal_barra_recta__default` | codo | - → STABILIZER |
| `quads_zancada_caminando_frontal_barra_recta__default` | columna-lumbar | - → STABILIZER |
| `quads_zancada_caminando_frontal_barra_recta__default` | glenohumeral | - → STABILIZER |
| `quads_zancada_caminando_frontal_barra_recta__default` | muñeca | - → STABILIZER |
| `quads_zancada_caminando_zercher_barra_recta__default` | codo | - → STABILIZER |
| `quads_zancada_caminando_zercher_barra_recta__default` | columna-lumbar | - → STABILIZER |
| `quads_zancada_caminando_zercher_barra_recta__default` | glenohumeral | - → STABILIZER |
| `quads_zancada_frontal_zercher__default` | codo | - → STABILIZER |
| `quads_zancada_frontal_zercher__default` | columna-lumbar | - → STABILIZER |
| `quads_zancada_frontal_zercher__default` | glenohumeral | - → STABILIZER |
| `quads_zancada_inversa_frontal__default` | codo | - → STABILIZER |
| `quads_zancada_inversa_frontal__default` | columna-lumbar | - → STABILIZER |
| `quads_zancada_inversa_frontal__default` | glenohumeral | - → STABILIZER |
| `quads_zancada_inversa_frontal__default` | muñeca | - → STABILIZER |
| `quads_zancada_inversa_maquina_hack__default` | sacroiliaca | STABILIZER → - |
| `quads_zancada_inversa_maquina_v_squat__default` | sacroiliaca | STABILIZER → - |
| `quads_zancada_inversa_zercher__default` | codo | - → STABILIZER |
| `quads_zancada_inversa_zercher__default` | columna-lumbar | - → STABILIZER |
| `quads_zancada_inversa_zercher__default` | glenohumeral | - → STABILIZER |
| `quads_sentadilla_bulgara_jefferson__default` | columna-lumbar | - → STABILIZER |
| `quads_sentadilla_bulgara_jefferson__default` | glenohumeral | - → STABILIZER |
| `quads_sentadilla_bulgara_jefferson__default` | muñeca | - → STABILIZER |
| `quads_sentadilla_bulgara_jefferson__default` | sacroiliaca | STABILIZER → - |
| `walking_lunge__barbell` | columna-lumbar | - → STABILIZER |
| `walking_lunge__dumbbells` | columna-lumbar | - → STABILIZER |
| `walking_lunge__dumbbells` | glenohumeral | - → STABILIZER |
| `walking_lunge__dumbbells` | muñeca | - → STABILIZER |
| `walking_lunge__kettlebell` | codo | - → STABILIZER |
| `walking_lunge__kettlebell` | columna-lumbar | - → STABILIZER |
| `walking_lunge__kettlebell` | glenohumeral | - → STABILIZER |
| `walking_lunge__kettlebell` | muñeca | - → STABILIZER |
| `bulgarian_split_squat__machine` | glenohumeral | - → STABILIZER |
| `bulgarian_split_squat__dumbbells` | glenohumeral | - → STABILIZER |
| `bulgarian_split_squat__cable` | glenohumeral | - → STABILIZER |
| `bulgarian_split_squat__kettlebell` | glenohumeral | - → STABILIZER |
| `bulgarian_zercher__barbell__zercher` | glenohumeral | - → STABILIZER |

## Topes de palabras (léxico): definiciones CURATED que usan cada palabra

| tope | usadas | máximo | en este lote |
|---|---|---|---|
| budget.claridad | 1 | 3 |  |
| budget.referencia | 1 | 8 |  |
| budget.concentrar | 1 | 15 |  |
| budget.repartir | 2 | 5 |  |
| budget.guiado | 1 | 25 | reverse_lunge |
| budget.medir | 2 | 8 |  |
| budget.distinto | 2 | 8 |  |
