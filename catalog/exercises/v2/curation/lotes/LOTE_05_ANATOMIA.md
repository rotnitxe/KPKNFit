# Lote 5: anatomía aprobada y aplicada

21 definiciones / 59 configuraciones. Delta conservado contra el cuerpo previo al land. El buenos días sentado queda LEGACY. Los pesos son aportes por serie, no totales semanales; la tolerancia al extra indirecto no rebaja roles.

# Informe de lote: delta anatómico

## Escalar antes de aplicar (principal cambiado en configuraciones protegidas)

| configuración | antes | después | código | tests |
|---|---|---|---|---|
| `conventional_deadlift__bilateral__barbell` | gluteus_maximus, hamstrings | gluteus_maximus, hamstrings, quadriceps | sí | sí |
| `conventional_deadlift__unilateral__barbell` | gluteus_maximus, hamstrings | gluteus_maximus, hamstrings, quadriceps |  | sí |
| `conventional_deadlift__bilateral__dumbbells` | gluteus_maximus, hamstrings | gluteus_maximus, hamstrings, quadriceps |  | sí |
| `conventional_deadlift__bilateral__hex_bar` | gluteus_maximus, hamstrings | gluteus_maximus, hamstrings, quadriceps | sí |  |
| `good_morning__bilateral__barbell` | hamstrings | hamstrings, gluteus_maximus | sí | sí |
| `good_morning__unilateral__safety_bar` | hamstrings | hamstrings, gluteus_maximus |  | sí |
| `glutes_hiperextension_45__plate` | gluteus_maximus | gluteus_maximus, hamstrings | sí |  |
| `hams_pull_through__default` | hamstrings | hamstrings, gluteus_maximus | sí | sí |
| `hams_peso_muerto_convencional_deficit__default` | hamstrings | gluteus_maximus, hamstrings, quadriceps | sí | sí |
| `hams_peso_muerto_piernas_rigidas_deficit__default` | hamstrings | hamstrings, gluteus_maximus |  | sí |
| `hams_peso_muerto_sumo_deficit__default` | hamstrings | gluteus_maximus, hamstrings, quadriceps |  | sí |
| `hams_swing_kettlebell_dos_manos__default` | hamstrings | hamstrings, gluteus_maximus |  | sí |
| `hams_swing_kettlebell_unilateral__default` | hamstrings | hamstrings, gluteus_maximus |  | sí |
| `hams_peso_muerto_rumano_deficit__default` | hamstrings | hamstrings, gluteus_maximus |  | sí |
| `hams_peso_muerto_rumano_sumo_deficit__default` | hamstrings | hamstrings, gluteus_maximus |  | sí |

## Cambios de rol muscular

| configuración | músculo | antes → después | dominante cambia | protegida |
|---|---|---|---|---|
| `conventional_deadlift__bilateral__barbell` | adductors | - → SECONDARY |  | código |
| `conventional_deadlift__bilateral__barbell` | forearm | - → STABILIZER |  | código |
| `conventional_deadlift__bilateral__barbell` | quadriceps | SECONDARY → PRIMARY |  | código |
| `conventional_deadlift__unilateral__barbell` | adductors | - → SECONDARY |  | tests |
| `conventional_deadlift__unilateral__barbell` | forearm | - → STABILIZER |  | tests |
| `conventional_deadlift__unilateral__barbell` | gluteus_medius | - → STABILIZER |  | tests |
| `conventional_deadlift__unilateral__barbell` | quadriceps | SECONDARY → PRIMARY |  | tests |
| `conventional_deadlift__bilateral__smith_machine` | adductors | - → SECONDARY |  |  |
| `conventional_deadlift__bilateral__smith_machine` | erector_spinae | - → STABILIZER |  |  |
| `conventional_deadlift__bilateral__smith_machine` | forearm | - → STABILIZER |  |  |
| `conventional_deadlift__bilateral__smith_machine` | quadriceps | SECONDARY → PRIMARY |  |  |
| `conventional_deadlift__unilateral__smith_machine` | adductors | - → SECONDARY |  |  |
| `conventional_deadlift__unilateral__smith_machine` | erector_spinae | - → STABILIZER |  |  |
| `conventional_deadlift__unilateral__smith_machine` | forearm | - → STABILIZER |  |  |
| `conventional_deadlift__unilateral__smith_machine` | gluteus_medius | - → STABILIZER |  |  |
| `conventional_deadlift__unilateral__smith_machine` | quadriceps | SECONDARY → PRIMARY |  |  |
| `conventional_deadlift__bilateral__dumbbells` | adductors | - → SECONDARY |  | tests |
| `conventional_deadlift__bilateral__dumbbells` | forearm | - → STABILIZER |  | tests |
| `conventional_deadlift__bilateral__dumbbells` | quadriceps | SECONDARY → PRIMARY |  | tests |
| `conventional_deadlift__unilateral__dumbbells` | adductors | - → SECONDARY |  |  |
| `conventional_deadlift__unilateral__dumbbells` | forearm | - → STABILIZER |  |  |
| `conventional_deadlift__unilateral__dumbbells` | gluteus_medius | - → STABILIZER |  |  |
| `conventional_deadlift__unilateral__dumbbells` | quadriceps | SECONDARY → PRIMARY |  |  |
| `conventional_deadlift__bilateral__hex_bar` | adductors | - → SECONDARY |  | código |
| `conventional_deadlift__bilateral__hex_bar` | forearm | - → STABILIZER |  | código |
| `conventional_deadlift__bilateral__hex_bar` | quadriceps | SECONDARY → PRIMARY |  | código |
| `conventional_deadlift__unilateral__hex_bar` | adductors | - → SECONDARY |  |  |
| `conventional_deadlift__unilateral__hex_bar` | forearm | - → STABILIZER |  |  |
| `conventional_deadlift__unilateral__hex_bar` | gluteus_medius | - → STABILIZER |  |  |
| `conventional_deadlift__unilateral__hex_bar` | quadriceps | SECONDARY → PRIMARY |  |  |
| `sumo_deadlift__barbell` | erector_spinae | - → STABILIZER |  | código |
| `sumo_deadlift__barbell` | forearm | - → STABILIZER |  | código |
| `sumo_deadlift__dumbbells` | erector_spinae | - → STABILIZER |  | tests |
| `sumo_deadlift__dumbbells` | forearm | - → STABILIZER |  | tests |
| `good_morning__bilateral__barbell` | gluteus_maximus | SECONDARY → PRIMARY |  | código |
| `good_morning__unilateral__barbell` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `good_morning__unilateral__barbell` | gluteus_medius | - → STABILIZER |  |  |
| `good_morning__bilateral__safety_bar` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `good_morning__unilateral__safety_bar` | gluteus_maximus | SECONDARY → PRIMARY |  | tests |
| `good_morning__unilateral__safety_bar` | gluteus_medius | - → STABILIZER |  | tests |
| `good_morning__bilateral__smith_machine` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `good_morning__unilateral__smith_machine` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `good_morning__unilateral__smith_machine` | gluteus_medius | - → STABILIZER |  |  |
| `good_morning__bilateral__machine` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `good_morning__unilateral__machine` | gluteus_maximus | SECONDARY → PRIMARY |  |  |
| `good_morning__unilateral__machine` | gluteus_medius | - → STABILIZER |  |  |
| `good_morning_zercher__default` | biceps | - → STABILIZER |  | tests |
| `romanian_deadlift__bilateral__barbell` | adductors | - → SECONDARY |  | código |
| `romanian_deadlift__bilateral__barbell` | forearm | - → STABILIZER |  | código |
| `romanian_deadlift__unilateral__barbell` | adductors | - → SECONDARY |  | código |
| `romanian_deadlift__unilateral__barbell` | forearm | - → STABILIZER |  | código |
| `romanian_deadlift__unilateral__barbell` | gluteus_medius | - → STABILIZER |  | código |
| `romanian_deadlift__bilateral__smith_machine` | adductors | - → SECONDARY |  | código |
| `romanian_deadlift__bilateral__smith_machine` | forearm | - → STABILIZER |  | código |
| `romanian_deadlift__unilateral__smith_machine` | adductors | - → SECONDARY |  | código |
| `romanian_deadlift__unilateral__smith_machine` | forearm | - → STABILIZER |  | código |
| `romanian_deadlift__unilateral__smith_machine` | gluteus_medius | - → STABILIZER |  | código |
| `romanian_deadlift__bilateral__dumbbells` | adductors | - → SECONDARY |  | código |
| `romanian_deadlift__bilateral__dumbbells` | forearm | - → STABILIZER |  | código |
| `romanian_deadlift__unilateral__dumbbells` | adductors | - → SECONDARY |  | código |
| `romanian_deadlift__unilateral__dumbbells` | forearm | - → STABILIZER |  | código |
| `romanian_deadlift__unilateral__dumbbells` | gluteus_medius | - → STABILIZER |  | código |
| `romanian_deadlift__bilateral__hex_bar` | adductors | - → SECONDARY |  |  |
| `romanian_deadlift__bilateral__hex_bar` | forearm | - → STABILIZER |  |  |
| `romanian_deadlift__unilateral__hex_bar` | adductors | - → SECONDARY |  | código |
| `romanian_deadlift__unilateral__hex_bar` | forearm | - → STABILIZER |  | código |
| `romanian_deadlift__unilateral__hex_bar` | gluteus_medius | - → STABILIZER |  | código |
| `romanian_sumo_deadlift__bilateral__barbell` | adductors | - → SECONDARY |  | tests |
| `romanian_sumo_deadlift__bilateral__barbell` | forearm | - → STABILIZER |  | tests |
| `romanian_sumo_deadlift__bilateral__smith_machine` | adductors | - → SECONDARY |  |  |
| `romanian_sumo_deadlift__bilateral__smith_machine` | forearm | - → STABILIZER |  |  |
| `romanian_sumo_deadlift__bilateral__dumbbells` | adductors | - → SECONDARY |  |  |
| `romanian_sumo_deadlift__bilateral__dumbbells` | forearm | - → STABILIZER |  |  |
| `romanian_sumo_deadlift__bilateral__hex_bar` | adductors | - → SECONDARY |  |  |
| `romanian_sumo_deadlift__bilateral__hex_bar` | forearm | - → STABILIZER |  |  |
| `glutes_hiperextension_45__dumbbells` | core | - → STABILIZER |  |  |
| `glutes_hiperextension_45__dumbbells` | forearm | - → STABILIZER |  |  |
| `glutes_hiperextension_45__dumbbells` | hamstrings | SECONDARY → PRIMARY |  |  |
| `glutes_hiperextension_45__barbell` | core | - → STABILIZER |  |  |
| `glutes_hiperextension_45__barbell` | hamstrings | SECONDARY → PRIMARY |  |  |
| `glutes_hiperextension_45__plate` | biceps | - → STABILIZER |  | código |
| `glutes_hiperextension_45__plate` | core | - → STABILIZER |  | código |
| `glutes_hiperextension_45__plate` | forearm | - → STABILIZER |  | código |
| `glutes_hiperextension_45__plate` | hamstrings | SECONDARY → PRIMARY |  | código |
| `glutes_hiperextension_45__smith_machine` | core | - → STABILIZER |  |  |
| `glutes_hiperextension_45__smith_machine` | hamstrings | SECONDARY → PRIMARY |  |  |
| `glutes_hiperextension_45_zercher__default` | biceps | - → STABILIZER |  |  |
| `glutes_hiperextension_45_zercher__default` | core | - → STABILIZER |  |  |
| `glutes_hiperextension_45_zercher__default` | hamstrings | SECONDARY → PRIMARY |  |  |
| `hams_pull_through__default` | core | - → STABILIZER |  | código |
| `hams_pull_through__default` | erector_spinae | - → STABILIZER |  | código |
| `hams_pull_through__default` | forearm | - → STABILIZER |  | código |
| `hams_pull_through__default` | gluteus_maximus | - → PRIMARY |  | código |
| `hams_peso_muerto_convencional_deficit__default` | adductors | - → SECONDARY | sí | código |
| `hams_peso_muerto_convencional_deficit__default` | core | - → STABILIZER | sí | código |
| `hams_peso_muerto_convencional_deficit__default` | erector_spinae | SECONDARY → STABILIZER | sí | código |
| `hams_peso_muerto_convencional_deficit__default` | forearm | - → STABILIZER | sí | código |
| `hams_peso_muerto_convencional_deficit__default` | gluteus_maximus | SECONDARY → PRIMARY | sí | código |
| `hams_peso_muerto_convencional_deficit__default` | quadriceps | - → PRIMARY | sí | código |
| `hams_peso_muerto_piernas_rigidas_deficit__default` | adductors | - → SECONDARY |  | tests |
| `hams_peso_muerto_piernas_rigidas_deficit__default` | core | - → STABILIZER |  | tests |
| `hams_peso_muerto_piernas_rigidas_deficit__default` | erector_spinae | SECONDARY → STABILIZER |  | tests |
| `hams_peso_muerto_piernas_rigidas_deficit__default` | forearm | - → STABILIZER |  | tests |
| `hams_peso_muerto_piernas_rigidas_deficit__default` | gluteus_maximus | SECONDARY → PRIMARY |  | tests |
| `hams_peso_muerto_sumo_deficit__default` | core | - → STABILIZER | sí | tests |
| `hams_peso_muerto_sumo_deficit__default` | erector_spinae | - → STABILIZER | sí | tests |
| `hams_peso_muerto_sumo_deficit__default` | forearm | - → STABILIZER | sí | tests |
| `hams_peso_muerto_sumo_deficit__default` | gluteus_maximus | SECONDARY → PRIMARY | sí | tests |
| `hams_peso_muerto_sumo_deficit__default` | quadriceps | - → PRIMARY | sí | tests |
| `hams_swing_kettlebell_dos_manos__default` | core | - → STABILIZER |  | tests |
| `hams_swing_kettlebell_dos_manos__default` | deltoid | - → SECONDARY |  | tests |
| `hams_swing_kettlebell_dos_manos__default` | erector_spinae | SECONDARY → STABILIZER |  | tests |
| `hams_swing_kettlebell_dos_manos__default` | forearm | - → STABILIZER |  | tests |
| `hams_swing_kettlebell_dos_manos__default` | gluteus_maximus | SECONDARY → PRIMARY |  | tests |
| `hams_swing_kettlebell_dos_manos__default` | gluteus_medius | - → STABILIZER |  | tests |
| `hams_swing_kettlebell_dos_manos__default` | quadriceps | - → SECONDARY |  | tests |
| `hams_swing_kettlebell_unilateral__default` | core | SECONDARY → STABILIZER |  | tests |
| `hams_swing_kettlebell_unilateral__default` | deltoid | - → SECONDARY |  | tests |
| `hams_swing_kettlebell_unilateral__default` | erector_spinae | - → STABILIZER |  | tests |
| `hams_swing_kettlebell_unilateral__default` | forearm | - → STABILIZER |  | tests |
| `hams_swing_kettlebell_unilateral__default` | gluteus_maximus | SECONDARY → PRIMARY |  | tests |
| `hams_swing_kettlebell_unilateral__default` | gluteus_medius | - → STABILIZER |  | tests |
| `hams_swing_kettlebell_unilateral__default` | quadriceps | - → SECONDARY |  | tests |
| `stiff_leg_deadlift__bilateral__barbell` | adductors | - → SECONDARY |  | código |
| `stiff_leg_deadlift__bilateral__barbell` | forearm | - → STABILIZER |  | código |
| `stiff_leg_deadlift__unilateral__barbell` | adductors | - → SECONDARY |  |  |
| `stiff_leg_deadlift__unilateral__barbell` | forearm | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__barbell` | gluteus_medius | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__barbell` | tensor_fasciae_latae | - → STABILIZER |  |  |
| `stiff_leg_deadlift__bilateral__smith_machine` | adductors | - → SECONDARY |  |  |
| `stiff_leg_deadlift__bilateral__smith_machine` | forearm | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__smith_machine` | adductors | - → SECONDARY |  |  |
| `stiff_leg_deadlift__unilateral__smith_machine` | forearm | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__smith_machine` | gluteus_medius | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__smith_machine` | tensor_fasciae_latae | - → STABILIZER |  |  |
| `stiff_leg_deadlift__bilateral__dumbbells` | adductors | - → SECONDARY |  |  |
| `stiff_leg_deadlift__bilateral__dumbbells` | forearm | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__dumbbells` | adductors | - → SECONDARY |  |  |
| `stiff_leg_deadlift__unilateral__dumbbells` | forearm | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__dumbbells` | gluteus_medius | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__dumbbells` | tensor_fasciae_latae | - → STABILIZER |  |  |
| `stiff_leg_deadlift__bilateral__hex_bar` | adductors | - → SECONDARY |  |  |
| `stiff_leg_deadlift__bilateral__hex_bar` | forearm | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__hex_bar` | adductors | - → SECONDARY |  |  |
| `stiff_leg_deadlift__unilateral__hex_bar` | forearm | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__hex_bar` | gluteus_medius | - → STABILIZER |  |  |
| `stiff_leg_deadlift__unilateral__hex_bar` | tensor_fasciae_latae | - → STABILIZER |  |  |
| `hams_peso_muerto_rumano_deficit__default` | adductors | - → SECONDARY |  | tests |
| `hams_peso_muerto_rumano_deficit__default` | core | - → STABILIZER |  | tests |
| `hams_peso_muerto_rumano_deficit__default` | erector_spinae | SECONDARY → STABILIZER |  | tests |
| `hams_peso_muerto_rumano_deficit__default` | forearm | - → STABILIZER |  | tests |
| `hams_peso_muerto_rumano_deficit__default` | gluteus_maximus | SECONDARY → PRIMARY |  | tests |
| `hams_peso_muerto_rumano_sumo_deficit__default` | core | - → STABILIZER |  | tests |
| `hams_peso_muerto_rumano_sumo_deficit__default` | erector_spinae | - → STABILIZER |  | tests |
| `hams_peso_muerto_rumano_sumo_deficit__default` | forearm | - → STABILIZER |  | tests |
| `hams_peso_muerto_rumano_sumo_deficit__default` | gluteus_maximus | SECONDARY → PRIMARY |  | tests |
| `back_extension_lumbar__default` | gluteus_maximus | SECONDARY → STABILIZER |  | código |
| `back_extension_lumbar__default` | hamstrings | - → STABILIZER |  | código |
| `back_superman_suelo__default` | deltoid | - → STABILIZER |  | código |
| `back_hiperextension_45_zercher_espalda_baja__default` | biceps | - → STABILIZER |  |  |
| `back_hiperextension_45_zercher_espalda_baja__default` | gluteus_maximus | - → STABILIZER |  |  |
| `back_hiperextension_45_zercher_espalda_baja__default` | hamstrings | - → STABILIZER |  |  |
| `back_jefferson_curl__barbell` | forearm | - → STABILIZER |  |  |
| `back_jefferson_curl__barbell` | gluteus_maximus | - → SECONDARY |  |  |
| `back_jefferson_curl__dumbbells` | forearm | - → STABILIZER |  |  |
| `back_jefferson_curl__dumbbells` | gluteus_maximus | - → SECONDARY |  |  |
| `back_jefferson_curl__smith_machine` | forearm | - → STABILIZER |  |  |
| `back_jefferson_curl__smith_machine` | gluteus_maximus | - → SECONDARY |  |  |
| `back_jefferson_curl__cable` | forearm | - → STABILIZER |  |  |
| `back_jefferson_curl__cable` | gluteus_maximus | - → SECONDARY |  |  |

## Cambios articulares

| configuración | articulación | antes → después |
|---|---|---|
| `conventional_deadlift__bilateral__barbell` | muñeca | - → STABILIZER |
| `conventional_deadlift__bilateral__barbell` | rodilla | SECONDARY → PRIMARY |
| `conventional_deadlift__bilateral__barbell` | tobillo | - → SECONDARY |
| `conventional_deadlift__unilateral__barbell` | muñeca | - → STABILIZER |
| `conventional_deadlift__unilateral__barbell` | rodilla | SECONDARY → PRIMARY |
| `conventional_deadlift__unilateral__barbell` | tobillo | - → SECONDARY |
| `conventional_deadlift__bilateral__smith_machine` | muñeca | - → STABILIZER |
| `conventional_deadlift__bilateral__smith_machine` | rodilla | SECONDARY → PRIMARY |
| `conventional_deadlift__bilateral__smith_machine` | tobillo | - → SECONDARY |
| `conventional_deadlift__unilateral__smith_machine` | muñeca | - → STABILIZER |
| `conventional_deadlift__unilateral__smith_machine` | rodilla | SECONDARY → PRIMARY |
| `conventional_deadlift__unilateral__smith_machine` | tobillo | - → SECONDARY |
| `conventional_deadlift__bilateral__dumbbells` | muñeca | - → STABILIZER |
| `conventional_deadlift__bilateral__dumbbells` | rodilla | SECONDARY → PRIMARY |
| `conventional_deadlift__bilateral__dumbbells` | tobillo | - → SECONDARY |
| `conventional_deadlift__unilateral__dumbbells` | muñeca | - → STABILIZER |
| `conventional_deadlift__unilateral__dumbbells` | rodilla | SECONDARY → PRIMARY |
| `conventional_deadlift__unilateral__dumbbells` | tobillo | - → SECONDARY |
| `conventional_deadlift__bilateral__hex_bar` | muñeca | - → STABILIZER |
| `conventional_deadlift__bilateral__hex_bar` | rodilla | SECONDARY → PRIMARY |
| `conventional_deadlift__bilateral__hex_bar` | tobillo | - → SECONDARY |
| `conventional_deadlift__unilateral__hex_bar` | muñeca | - → STABILIZER |
| `conventional_deadlift__unilateral__hex_bar` | rodilla | SECONDARY → PRIMARY |
| `conventional_deadlift__unilateral__hex_bar` | tobillo | - → SECONDARY |
| `sumo_deadlift__barbell` | muñeca | - → STABILIZER |
| `sumo_deadlift__barbell` | rodilla | SECONDARY → PRIMARY |
| `sumo_deadlift__barbell` | tobillo | - → SECONDARY |
| `sumo_deadlift__dumbbells` | muñeca | - → STABILIZER |
| `sumo_deadlift__dumbbells` | rodilla | SECONDARY → PRIMARY |
| `sumo_deadlift__dumbbells` | tobillo | - → SECONDARY |
| `good_morning__bilateral__barbell` | rodilla | SECONDARY → STABILIZER |
| `good_morning__unilateral__barbell` | rodilla | SECONDARY → STABILIZER |
| `good_morning__bilateral__safety_bar` | rodilla | SECONDARY → STABILIZER |
| `good_morning__unilateral__safety_bar` | rodilla | SECONDARY → STABILIZER |
| `good_morning__bilateral__smith_machine` | rodilla | SECONDARY → STABILIZER |
| `good_morning__unilateral__smith_machine` | rodilla | SECONDARY → STABILIZER |
| `good_morning__bilateral__machine` | rodilla | SECONDARY → STABILIZER |
| `good_morning__unilateral__machine` | rodilla | SECONDARY → STABILIZER |
| `good_morning_zercher__default` | codo | - → STABILIZER |
| `good_morning_zercher__default` | rodilla | SECONDARY → STABILIZER |
| `romanian_deadlift__bilateral__barbell` | muñeca | - → STABILIZER |
| `romanian_deadlift__bilateral__barbell` | rodilla | SECONDARY → STABILIZER |
| `romanian_deadlift__unilateral__barbell` | muñeca | - → STABILIZER |
| `romanian_deadlift__unilateral__barbell` | rodilla | SECONDARY → STABILIZER |
| `romanian_deadlift__bilateral__smith_machine` | muñeca | - → STABILIZER |
| `romanian_deadlift__bilateral__smith_machine` | rodilla | SECONDARY → STABILIZER |
| `romanian_deadlift__unilateral__smith_machine` | muñeca | - → STABILIZER |
| `romanian_deadlift__unilateral__smith_machine` | rodilla | SECONDARY → STABILIZER |
| `romanian_deadlift__bilateral__dumbbells` | muñeca | - → STABILIZER |
| `romanian_deadlift__bilateral__dumbbells` | rodilla | SECONDARY → STABILIZER |
| `romanian_deadlift__unilateral__dumbbells` | muñeca | - → STABILIZER |
| `romanian_deadlift__unilateral__dumbbells` | rodilla | SECONDARY → STABILIZER |
| `romanian_deadlift__bilateral__hex_bar` | muñeca | - → STABILIZER |
| `romanian_deadlift__bilateral__hex_bar` | rodilla | SECONDARY → STABILIZER |
| `romanian_deadlift__unilateral__hex_bar` | muñeca | - → STABILIZER |
| `romanian_deadlift__unilateral__hex_bar` | rodilla | SECONDARY → STABILIZER |
| `romanian_sumo_deadlift__bilateral__barbell` | muñeca | - → STABILIZER |
| `romanian_sumo_deadlift__bilateral__barbell` | rodilla | SECONDARY → STABILIZER |
| `romanian_sumo_deadlift__bilateral__smith_machine` | muñeca | - → STABILIZER |
| `romanian_sumo_deadlift__bilateral__smith_machine` | rodilla | SECONDARY → STABILIZER |
| `romanian_sumo_deadlift__bilateral__dumbbells` | muñeca | - → STABILIZER |
| `romanian_sumo_deadlift__bilateral__dumbbells` | rodilla | SECONDARY → STABILIZER |
| `romanian_sumo_deadlift__bilateral__hex_bar` | muñeca | - → STABILIZER |
| `romanian_sumo_deadlift__bilateral__hex_bar` | rodilla | SECONDARY → STABILIZER |
| `glutes_hiperextension_45__dumbbells` | muñeca | - → STABILIZER |
| `glutes_hiperextension_45__dumbbells` | rodilla | SECONDARY → STABILIZER |
| `glutes_hiperextension_45__barbell` | rodilla | SECONDARY → STABILIZER |
| `glutes_hiperextension_45__plate` | codo | - → STABILIZER |
| `glutes_hiperextension_45__plate` | muñeca | - → STABILIZER |
| `glutes_hiperextension_45__plate` | rodilla | SECONDARY → STABILIZER |
| `glutes_hiperextension_45__smith_machine` | rodilla | SECONDARY → STABILIZER |
| `glutes_hiperextension_45_zercher__default` | codo | - → STABILIZER |
| `glutes_hiperextension_45_zercher__default` | rodilla | SECONDARY → STABILIZER |
| `hams_pull_through__default` | glenohumeral | - → STABILIZER |
| `hams_pull_through__default` | muñeca | - → STABILIZER |
| `hams_pull_through__default` | rodilla | SECONDARY → STABILIZER |
| `hams_peso_muerto_convencional_deficit__default` | muñeca | - → STABILIZER |
| `hams_peso_muerto_convencional_deficit__default` | rodilla | SECONDARY → PRIMARY |
| `hams_peso_muerto_convencional_deficit__default` | tobillo | - → SECONDARY |
| `hams_peso_muerto_piernas_rigidas_deficit__default` | muñeca | - → STABILIZER |
| `hams_peso_muerto_piernas_rigidas_deficit__default` | rodilla | SECONDARY → STABILIZER |
| `hams_peso_muerto_sumo_deficit__default` | muñeca | - → STABILIZER |
| `hams_peso_muerto_sumo_deficit__default` | rodilla | SECONDARY → PRIMARY |
| `hams_peso_muerto_sumo_deficit__default` | tobillo | - → SECONDARY |
| `hams_swing_kettlebell_dos_manos__default` | glenohumeral | - → SECONDARY |
| `hams_swing_kettlebell_dos_manos__default` | muñeca | - → STABILIZER |
| `hams_swing_kettlebell_dos_manos__default` | sacroiliaca | - → STABILIZER |
| `hams_swing_kettlebell_unilateral__default` | glenohumeral | - → SECONDARY |
| `hams_swing_kettlebell_unilateral__default` | muñeca | - → STABILIZER |
| `hams_swing_kettlebell_unilateral__default` | sacroiliaca | - → STABILIZER |
| `stiff_leg_deadlift__bilateral__barbell` | muñeca | - → STABILIZER |
| `stiff_leg_deadlift__bilateral__barbell` | rodilla | SECONDARY → STABILIZER |
| `stiff_leg_deadlift__unilateral__barbell` | muñeca | - → STABILIZER |
| `stiff_leg_deadlift__unilateral__barbell` | rodilla | SECONDARY → STABILIZER |
| `stiff_leg_deadlift__bilateral__smith_machine` | muñeca | - → STABILIZER |
| `stiff_leg_deadlift__bilateral__smith_machine` | rodilla | SECONDARY → STABILIZER |
| `stiff_leg_deadlift__unilateral__smith_machine` | muñeca | - → STABILIZER |
| `stiff_leg_deadlift__unilateral__smith_machine` | rodilla | SECONDARY → STABILIZER |
| `stiff_leg_deadlift__bilateral__dumbbells` | muñeca | - → STABILIZER |
| `stiff_leg_deadlift__bilateral__dumbbells` | rodilla | SECONDARY → STABILIZER |
| `stiff_leg_deadlift__unilateral__dumbbells` | muñeca | - → STABILIZER |
| `stiff_leg_deadlift__unilateral__dumbbells` | rodilla | SECONDARY → STABILIZER |
| `stiff_leg_deadlift__bilateral__hex_bar` | muñeca | - → STABILIZER |
| `stiff_leg_deadlift__bilateral__hex_bar` | rodilla | SECONDARY → STABILIZER |
| `stiff_leg_deadlift__unilateral__hex_bar` | muñeca | - → STABILIZER |
| `stiff_leg_deadlift__unilateral__hex_bar` | rodilla | SECONDARY → STABILIZER |
| `hams_peso_muerto_rumano_deficit__default` | muñeca | - → STABILIZER |
| `hams_peso_muerto_rumano_deficit__default` | rodilla | SECONDARY → STABILIZER |
| `hams_peso_muerto_rumano_sumo_deficit__default` | muñeca | - → STABILIZER |
| `hams_peso_muerto_rumano_sumo_deficit__default` | rodilla | SECONDARY → STABILIZER |
| `back_extension_lumbar__default` | cadera | SECONDARY → STABILIZER |
| `back_extension_lumbar__default` | columna-toracica | - → SECONDARY |
| `back_superman_suelo__default` | columna-toracica | - → SECONDARY |
| `back_superman_suelo__default` | glenohumeral | - → STABILIZER |
| `back_superman_suelo__default` | rodilla | - → STABILIZER |
| `back_superman_suelo__default` | sacroiliaca | STABILIZER → - |
| `back_hiperextension_45_zercher_espalda_baja__default` | cadera | SECONDARY → STABILIZER |
| `back_hiperextension_45_zercher_espalda_baja__default` | codo | - → STABILIZER |
| `back_hiperextension_45_zercher_espalda_baja__default` | columna-toracica | - → SECONDARY |
| `back_jefferson_curl__barbell` | cadera | STABILIZER → SECONDARY |
| `back_jefferson_curl__barbell` | columna-cervical | - → SECONDARY |
| `back_jefferson_curl__barbell` | columna-toracica | SECONDARY → PRIMARY |
| `back_jefferson_curl__barbell` | muñeca | - → STABILIZER |
| `back_jefferson_curl__barbell` | rodilla | - → STABILIZER |
| `back_jefferson_curl__dumbbells` | cadera | STABILIZER → SECONDARY |
| `back_jefferson_curl__dumbbells` | columna-cervical | - → SECONDARY |
| `back_jefferson_curl__dumbbells` | columna-toracica | SECONDARY → PRIMARY |
| `back_jefferson_curl__dumbbells` | muñeca | - → STABILIZER |
| `back_jefferson_curl__dumbbells` | rodilla | - → STABILIZER |
| `back_jefferson_curl__smith_machine` | cadera | STABILIZER → SECONDARY |
| `back_jefferson_curl__smith_machine` | columna-cervical | - → SECONDARY |
| `back_jefferson_curl__smith_machine` | columna-toracica | SECONDARY → PRIMARY |
| `back_jefferson_curl__smith_machine` | muñeca | - → STABILIZER |
| `back_jefferson_curl__smith_machine` | rodilla | - → STABILIZER |
| `back_jefferson_curl__cable` | cadera | STABILIZER → SECONDARY |
| `back_jefferson_curl__cable` | columna-cervical | - → SECONDARY |
| `back_jefferson_curl__cable` | columna-toracica | SECONDARY → PRIMARY |
| `back_jefferson_curl__cable` | muñeca | - → STABILIZER |
| `back_jefferson_curl__cable` | rodilla | - → STABILIZER |

## Topes de palabras (léxico): definiciones CURATED que usan cada palabra

| tope | usadas | máximo | en este lote |
|---|---|---|---|
| budget.claridad | 1 | 3 |  |
| budget.referencia | 1 | 8 |  |
| budget.concentrar | 1 | 15 |  |
| budget.repartir | 2 | 5 |  |
| budget.medir | 2 | 8 |  |
| budget.distinto | 2 | 8 |  |
