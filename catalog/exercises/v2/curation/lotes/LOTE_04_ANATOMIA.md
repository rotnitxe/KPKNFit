# Lote 4: tirones. Anexo de anatomía (antes → después)

Generado con `catalog_v2_lote_report.py` sobre las fichas aplicadas, contra el catálogo previo al land (SHA b2a652bb…). Incluye la hermana `close_grip_lat_pulldown`, alineada en este lote, y el cambio de dominante del remo en banda aprobado por el usuario.


## Escalar antes de aplicar (principal cambiado en configuraciones protegidas)

| configuración | antes | después | código | tests |
|---|---|---|---|---|
| `chest_supported_row__dumbbells__wide` | latissimus_dorsi | latissimus_dorsi, trapezius | sí | sí |
| `t_bar_row__machine__wide` | latissimus_dorsi | latissimus_dorsi, trapezius |  | sí |
| `t_bar_row__t_bar__wide` | latissimus_dorsi | latissimus_dorsi, trapezius | sí |  |
| `back_remo_banda__default` | trapezius, latissimus_dorsi | latissimus_dorsi | sí | sí |
| `back_dominadas_escapulares__default` | latissimus_dorsi | latissimus_dorsi, trapezius |  | sí |
| `back_y_raises__default` | trapezius, latissimus_dorsi | trapezius, deltoid |  | sí |
| `lat_pulldown__bilateral__cable` | latissimus_dorsi, trapezius | latissimus_dorsi | sí | sí |
| `lat_pulldown__unilateral__cable` | latissimus_dorsi, trapezius | latissimus_dorsi | sí |  |
| `lat_pulldown__bilateral__machine` | latissimus_dorsi, trapezius | latissimus_dorsi | sí | sí |
| `lat_pulldown__bilateral__band` | latissimus_dorsi, trapezius | latissimus_dorsi | sí | sí |
| `lat_pulldown__unilateral__band` | latissimus_dorsi, trapezius | latissimus_dorsi |  | sí |

## Cambios de rol muscular

| configuración | músculo | antes → después | dominante cambia | protegida |
|---|---|---|---|---|
| `chest_supported_row__dumbbells__wide` | biceps | STABILIZER → SECONDARY |  | código |
| `chest_supported_row__dumbbells__wide` | forearm | - → STABILIZER |  | código |
| `chest_supported_row__dumbbells__wide` | rhomboids | STABILIZER → SECONDARY |  | código |
| `chest_supported_row__dumbbells__wide` | trapezius | SECONDARY → PRIMARY |  | código |
| `chest_supported_row__kettlebell__wide` | biceps | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__kettlebell__wide` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__kettlebell__wide` | rhomboids | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__kettlebell__wide` | trapezius | SECONDARY → PRIMARY |  |  |
| `chest_supported_row__machine__wide` | biceps | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__machine__wide` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__machine__wide` | rhomboids | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__machine__wide` | trapezius | SECONDARY → PRIMARY |  |  |
| `chest_supported_row__cable__high__wide` | biceps | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__high__wide` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__cable__high__wide` | rhomboids | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__high__wide` | trapezius | SECONDARY → PRIMARY |  |  |
| `chest_supported_row__cable__mid__wide` | biceps | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__mid__wide` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__cable__mid__wide` | rhomboids | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__mid__wide` | trapezius | SECONDARY → PRIMARY |  |  |
| `chest_supported_row__cable__low__wide` | biceps | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__low__wide` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__cable__low__wide` | rhomboids | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__low__wide` | trapezius | SECONDARY → PRIMARY |  |  |
| `chest_supported_row__dumbbells__medium` | deltoid | STABILIZER → SECONDARY |  | código |
| `chest_supported_row__dumbbells__medium` | forearm | - → STABILIZER |  | código |
| `chest_supported_row__kettlebell__medium` | deltoid | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__kettlebell__medium` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__machine__medium` | deltoid | STABILIZER → SECONDARY |  | código |
| `chest_supported_row__machine__medium` | forearm | - → STABILIZER |  | código |
| `chest_supported_row__cable__high__medium` | deltoid | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__high__medium` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__cable__mid__medium` | deltoid | STABILIZER → SECONDARY |  | tests |
| `chest_supported_row__cable__mid__medium` | forearm | - → STABILIZER |  | tests |
| `chest_supported_row__cable__low__medium` | deltoid | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__low__medium` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__dumbbells__close` | deltoid | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__dumbbells__close` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__dumbbells__close` | trapezius | - → SECONDARY |  |  |
| `chest_supported_row__kettlebell__close` | deltoid | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__kettlebell__close` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__kettlebell__close` | trapezius | - → SECONDARY |  |  |
| `chest_supported_row__machine__close` | deltoid | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__machine__close` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__machine__close` | trapezius | - → SECONDARY |  |  |
| `chest_supported_row__cable__high__close` | deltoid | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__high__close` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__cable__high__close` | trapezius | - → SECONDARY |  |  |
| `chest_supported_row__cable__mid__close` | deltoid | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__mid__close` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__cable__mid__close` | trapezius | - → SECONDARY |  |  |
| `chest_supported_row__cable__low__close` | deltoid | STABILIZER → SECONDARY |  |  |
| `chest_supported_row__cable__low__close` | forearm | - → STABILIZER |  |  |
| `chest_supported_row__cable__low__close` | trapezius | - → SECONDARY |  |  |
| `conventional_row__barbell` | forearm | - → STABILIZER |  | código |
| `conventional_row__barbell` | gluteus_maximus | - → STABILIZER |  | código |
| `conventional_row__barbell` | hamstrings | - → STABILIZER |  | código |
| `conventional_row__dumbbells` | forearm | - → STABILIZER |  | código |
| `conventional_row__dumbbells` | gluteus_maximus | - → STABILIZER |  | código |
| `conventional_row__dumbbells` | hamstrings | - → STABILIZER |  | código |
| `conventional_row__machine` | forearm | - → STABILIZER |  | código |
| `conventional_row__machine` | gluteus_maximus | - → STABILIZER |  | código |
| `conventional_row__machine` | hamstrings | - → STABILIZER |  | código |
| `conventional_row__smith_machine` | forearm | - → STABILIZER |  | código |
| `conventional_row__smith_machine` | gluteus_maximus | - → STABILIZER |  | código |
| `conventional_row__smith_machine` | hamstrings | - → STABILIZER |  | código |
| `conventional_row__kettlebell` | forearm | - → STABILIZER |  | código |
| `conventional_row__kettlebell` | gluteus_maximus | - → STABILIZER |  | código |
| `conventional_row__kettlebell` | hamstrings | - → STABILIZER |  | código |
| `conventional_row__cable` | forearm | - → STABILIZER |  | código |
| `conventional_row__cable` | gluteus_maximus | - → STABILIZER |  | código |
| `conventional_row__cable` | hamstrings | - → STABILIZER |  | código |
| `gironda_row__wide` | biceps | STABILIZER → SECONDARY |  |  |
| `gironda_row__wide` | erector_spinae | - → STABILIZER |  |  |
| `gironda_row__wide` | forearm | - → STABILIZER |  |  |
| `gironda_row__wide` | rhomboids | STABILIZER → SECONDARY |  |  |
| `gironda_row__wide` | trapezius | SECONDARY → PRIMARY |  |  |
| `gironda_row__medium` | deltoid | STABILIZER → SECONDARY |  | código |
| `gironda_row__medium` | erector_spinae | - → STABILIZER |  | código |
| `gironda_row__medium` | forearm | - → STABILIZER |  | código |
| `gironda_row__close` | deltoid | STABILIZER → SECONDARY |  |  |
| `gironda_row__close` | erector_spinae | - → STABILIZER |  |  |
| `gironda_row__close` | forearm | - → STABILIZER |  |  |
| `gironda_row__close` | trapezius | - → SECONDARY |  |  |
| `pendlay_row__barbell` | forearm | - → STABILIZER |  | código |
| `pendlay_row__barbell` | gluteus_maximus | - → STABILIZER |  | código |
| `pendlay_row__barbell` | hamstrings | - → STABILIZER |  | código |
| `pendlay_row__dumbbells` | forearm | - → STABILIZER |  | tests |
| `pendlay_row__dumbbells` | gluteus_maximus | - → STABILIZER |  | tests |
| `pendlay_row__dumbbells` | hamstrings | - → STABILIZER |  | tests |
| `pendlay_row__machine` | forearm | - → STABILIZER |  | tests |
| `pendlay_row__machine` | gluteus_maximus | - → STABILIZER |  | tests |
| `pendlay_row__machine` | hamstrings | - → STABILIZER |  | tests |
| `pendlay_row__smith_machine` | forearm | - → STABILIZER |  | tests |
| `pendlay_row__smith_machine` | gluteus_maximus | - → STABILIZER |  | tests |
| `pendlay_row__smith_machine` | hamstrings | - → STABILIZER |  | tests |
| `pendlay_row__kettlebell` | forearm | - → STABILIZER |  | tests |
| `pendlay_row__kettlebell` | gluteus_maximus | - → STABILIZER |  | tests |
| `pendlay_row__kettlebell` | hamstrings | - → STABILIZER |  | tests |
| `pendlay_row__cable` | forearm | - → STABILIZER |  | tests |
| `pendlay_row__cable` | gluteus_maximus | - → STABILIZER |  | tests |
| `pendlay_row__cable` | hamstrings | - → STABILIZER |  | tests |
| `t_bar_row__machine__wide` | biceps | STABILIZER → SECONDARY |  | tests |
| `t_bar_row__machine__wide` | forearm | - → STABILIZER |  | tests |
| `t_bar_row__machine__wide` | rhomboids | STABILIZER → SECONDARY |  | tests |
| `t_bar_row__machine__wide` | trapezius | SECONDARY → PRIMARY |  | tests |
| `t_bar_row__t_bar__wide` | biceps | STABILIZER → SECONDARY |  | código |
| `t_bar_row__t_bar__wide` | core | - → STABILIZER |  | código |
| `t_bar_row__t_bar__wide` | erector_spinae | - → STABILIZER |  | código |
| `t_bar_row__t_bar__wide` | forearm | - → STABILIZER |  | código |
| `t_bar_row__t_bar__wide` | gluteus_maximus | - → STABILIZER |  | código |
| `t_bar_row__t_bar__wide` | hamstrings | - → STABILIZER |  | código |
| `t_bar_row__t_bar__wide` | rhomboids | STABILIZER → SECONDARY |  | código |
| `t_bar_row__t_bar__wide` | trapezius | SECONDARY → PRIMARY |  | código |
| `t_bar_row__machine__medium` | deltoid | STABILIZER → SECONDARY |  | tests |
| `t_bar_row__machine__medium` | forearm | - → STABILIZER |  | tests |
| `t_bar_row__t_bar__medium` | core | - → STABILIZER |  | código |
| `t_bar_row__t_bar__medium` | deltoid | STABILIZER → SECONDARY |  | código |
| `t_bar_row__t_bar__medium` | erector_spinae | - → STABILIZER |  | código |
| `t_bar_row__t_bar__medium` | forearm | - → STABILIZER |  | código |
| `t_bar_row__t_bar__medium` | gluteus_maximus | - → STABILIZER |  | código |
| `t_bar_row__t_bar__medium` | hamstrings | - → STABILIZER |  | código |
| `t_bar_row__machine__close` | deltoid | STABILIZER → SECONDARY |  |  |
| `t_bar_row__machine__close` | forearm | - → STABILIZER |  |  |
| `t_bar_row__machine__close` | trapezius | - → SECONDARY |  |  |
| `t_bar_row__t_bar__close` | core | - → STABILIZER |  |  |
| `t_bar_row__t_bar__close` | deltoid | STABILIZER → SECONDARY |  |  |
| `t_bar_row__t_bar__close` | erector_spinae | - → STABILIZER |  |  |
| `t_bar_row__t_bar__close` | forearm | - → STABILIZER |  |  |
| `t_bar_row__t_bar__close` | gluteus_maximus | - → STABILIZER |  |  |
| `t_bar_row__t_bar__close` | hamstrings | - → STABILIZER |  |  |
| `t_bar_row__t_bar__close` | trapezius | - → SECONDARY |  |  |
| `back_band_pull_apart__default` | forearm | - → STABILIZER |  | código |
| `back_band_pull_apart__default` | rhomboids | - → SECONDARY |  | código |
| `back_remo_banda__default` | erector_spinae | SECONDARY → STABILIZER | sí | código |
| `back_remo_banda__default` | forearm | - → STABILIZER | sí | código |
| `back_remo_banda__default` | rhomboids | - → SECONDARY | sí | código |
| `back_remo_banda__default` | trapezius | PRIMARY → SECONDARY | sí | código |
| `back_remo_gorilla_mancuernas__dumbbells` | core | - → STABILIZER |  | código |
| `back_remo_gorilla_mancuernas__dumbbells` | deltoid | - → SECONDARY |  | código |
| `back_remo_gorilla_mancuernas__dumbbells` | erector_spinae | - → STABILIZER |  | código |
| `back_remo_gorilla_mancuernas__dumbbells` | forearm | - → STABILIZER |  | código |
| `back_remo_gorilla_mancuernas__dumbbells` | gluteus_maximus | - → STABILIZER |  | código |
| `back_remo_gorilla_mancuernas__dumbbells` | hamstrings | - → STABILIZER |  | código |
| `back_remo_gorilla_mancuernas__dumbbells` | trapezius | - → SECONDARY |  | código |
| `back_remo_gorilla_mancuernas__kettlebell` | core | - → STABILIZER |  | tests |
| `back_remo_gorilla_mancuernas__kettlebell` | deltoid | - → SECONDARY |  | tests |
| `back_remo_gorilla_mancuernas__kettlebell` | erector_spinae | - → STABILIZER |  | tests |
| `back_remo_gorilla_mancuernas__kettlebell` | forearm | - → STABILIZER |  | tests |
| `back_remo_gorilla_mancuernas__kettlebell` | gluteus_maximus | - → STABILIZER |  | tests |
| `back_remo_gorilla_mancuernas__kettlebell` | hamstrings | - → STABILIZER |  | tests |
| `back_remo_gorilla_mancuernas__kettlebell` | trapezius | - → SECONDARY |  | tests |
| `back_remo_gorilla_mancuernas__cable` | core | - → STABILIZER |  | tests |
| `back_remo_gorilla_mancuernas__cable` | deltoid | - → SECONDARY |  | tests |
| `back_remo_gorilla_mancuernas__cable` | erector_spinae | - → STABILIZER |  | tests |
| `back_remo_gorilla_mancuernas__cable` | forearm | - → STABILIZER |  | tests |
| `back_remo_gorilla_mancuernas__cable` | gluteus_maximus | - → STABILIZER |  | tests |
| `back_remo_gorilla_mancuernas__cable` | hamstrings | - → STABILIZER |  | tests |
| `back_remo_gorilla_mancuernas__cable` | trapezius | - → SECONDARY |  | tests |
| `back_remo_invertido__default` | core | - → STABILIZER |  | código |
| `back_remo_invertido__default` | erector_spinae | SECONDARY → STABILIZER |  | código |
| `back_remo_invertido__default` | forearm | - → STABILIZER |  | código |
| `back_remo_invertido__default` | gluteus_maximus | - → STABILIZER |  | código |
| `back_remo_invertido__default` | hamstrings | - → STABILIZER |  | código |
| `back_remo_invertido__default` | rhomboids | - → SECONDARY |  | código |
| `back_remo_renegado_mancuernas__dumbbells` | core | STABILIZER → SECONDARY |  | tests |
| `back_remo_renegado_mancuernas__dumbbells` | deltoid | - → SECONDARY |  | tests |
| `back_remo_renegado_mancuernas__dumbbells` | forearm | - → STABILIZER |  | tests |
| `back_remo_renegado_mancuernas__dumbbells` | pectoralis | - → STABILIZER |  | tests |
| `back_remo_renegado_mancuernas__dumbbells` | trapezius | - → SECONDARY |  | tests |
| `back_remo_renegado_mancuernas__dumbbells` | triceps | - → STABILIZER |  | tests |
| `back_remo_renegado_mancuernas__kettlebell` | core | STABILIZER → SECONDARY |  | tests |
| `back_remo_renegado_mancuernas__kettlebell` | deltoid | - → SECONDARY |  | tests |
| `back_remo_renegado_mancuernas__kettlebell` | forearm | - → STABILIZER |  | tests |
| `back_remo_renegado_mancuernas__kettlebell` | pectoralis | - → STABILIZER |  | tests |
| `back_remo_renegado_mancuernas__kettlebell` | trapezius | - → SECONDARY |  | tests |
| `back_remo_renegado_mancuernas__kettlebell` | triceps | - → STABILIZER |  | tests |
| `deltoides_face_pull__default` | biceps | - → SECONDARY |  | código |
| `deltoides_face_pull__default` | forearm | - → STABILIZER |  | código |
| `deltoides_face_pull__default` | latissimus_dorsi | SECONDARY → - |  | código |
| `deltoides_face_pull__default` | rhomboids | - → SECONDARY |  | código |
| `back_dominadas_escapulares__default` | biceps | SECONDARY → - |  | tests |
| `back_dominadas_escapulares__default` | deltoid | SECONDARY → - |  | tests |
| `back_dominadas_escapulares__default` | forearm | - → STABILIZER |  | tests |
| `back_dominadas_escapulares__default` | pectoralis | - → STABILIZER |  | tests |
| `back_dominadas_escapulares__default` | rhomboids | - → SECONDARY |  | tests |
| `back_dominadas_escapulares__default` | trapezius | SECONDARY → PRIMARY |  | tests |
| `back_encogimientos__dumbbells` | forearm | - → STABILIZER |  | código |
| `back_encogimientos__smith_machine` | forearm | - → STABILIZER |  |  |
| `back_encogimientos__cable` | forearm | - → STABILIZER |  |  |
| `back_encogimientos__kettlebell` | forearm | - → STABILIZER |  |  |
| `back_encogimientos__barbell` | forearm | - → STABILIZER |  | código |
| `back_encogimientos_kelso__barbell` | erector_spinae | - → STABILIZER |  |  |
| `back_encogimientos_kelso__barbell` | forearm | - → STABILIZER |  |  |
| `back_encogimientos_kelso__barbell` | gluteus_maximus | - → STABILIZER |  |  |
| `back_encogimientos_kelso__barbell` | hamstrings | - → STABILIZER |  |  |
| `back_encogimientos_kelso__machine` | forearm | - → STABILIZER |  |  |
| `back_encogimientos_kelso__cable` | erector_spinae | - → STABILIZER |  |  |
| `back_encogimientos_kelso__cable` | forearm | - → STABILIZER |  |  |
| `back_encogimientos_kelso__kettlebell` | forearm | - → STABILIZER |  |  |
| `back_encogimientos_kelso__smith_machine` | forearm | - → STABILIZER |  |  |
| `back_y_raises__default` | biceps | SECONDARY → - |  | tests |
| `back_y_raises__default` | deltoid | SECONDARY → PRIMARY |  | tests |
| `back_y_raises__default` | erector_spinae | SECONDARY → STABILIZER |  | tests |
| `back_y_raises__default` | gluteus_maximus | - → STABILIZER |  | tests |
| `back_y_raises__default` | hamstrings | - → STABILIZER |  | tests |
| `back_y_raises__default` | latissimus_dorsi | PRIMARY → - |  | tests |
| `lat_pulldown__bilateral__cable` | core | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__cable` | forearm | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__cable` | pectoralis | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__cable` | rhomboids | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__cable` | trapezius | PRIMARY → SECONDARY |  | código |
| `lat_pulldown__unilateral__cable` | core | - → STABILIZER |  | código |
| `lat_pulldown__unilateral__cable` | forearm | - → STABILIZER |  | código |
| `lat_pulldown__unilateral__cable` | pectoralis | - → STABILIZER |  | código |
| `lat_pulldown__unilateral__cable` | rhomboids | - → STABILIZER |  | código |
| `lat_pulldown__unilateral__cable` | trapezius | PRIMARY → SECONDARY |  | código |
| `lat_pulldown__bilateral__machine` | core | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__machine` | forearm | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__machine` | pectoralis | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__machine` | rhomboids | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__machine` | trapezius | PRIMARY → SECONDARY |  | código |
| `lat_pulldown__unilateral__machine` | core | - → STABILIZER |  |  |
| `lat_pulldown__unilateral__machine` | forearm | - → STABILIZER |  |  |
| `lat_pulldown__unilateral__machine` | pectoralis | - → STABILIZER |  |  |
| `lat_pulldown__unilateral__machine` | rhomboids | - → STABILIZER |  |  |
| `lat_pulldown__unilateral__machine` | trapezius | PRIMARY → SECONDARY |  |  |
| `lat_pulldown__bilateral__band` | core | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__band` | forearm | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__band` | pectoralis | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__band` | rhomboids | - → STABILIZER |  | código |
| `lat_pulldown__bilateral__band` | trapezius | PRIMARY → SECONDARY |  | código |
| `lat_pulldown__unilateral__band` | core | - → STABILIZER |  | tests |
| `lat_pulldown__unilateral__band` | forearm | - → STABILIZER |  | tests |
| `lat_pulldown__unilateral__band` | pectoralis | - → STABILIZER |  | tests |
| `lat_pulldown__unilateral__band` | rhomboids | - → STABILIZER |  | tests |
| `lat_pulldown__unilateral__band` | trapezius | PRIMARY → SECONDARY |  | tests |
| `close_grip_lat_pulldown__cable` | core | - → STABILIZER |  | código |
| `close_grip_lat_pulldown__cable` | forearm | - → STABILIZER |  | código |
| `close_grip_lat_pulldown__cable` | pectoralis | - → STABILIZER |  | código |
| `close_grip_lat_pulldown__cable` | rhomboids | - → STABILIZER |  | código |
| `rack_chin__default` | biceps | STABILIZER → SECONDARY |  | código |
| `rack_chin__default` | deltoid | - → SECONDARY |  | código |
| `rack_chin__default` | erector_spinae | - → STABILIZER |  | código |
| `rack_chin__default` | forearm | - → STABILIZER |  | código |
| `rack_chin__default` | pectoralis | - → STABILIZER |  | código |
| `rack_chin__default` | trapezius | - → SECONDARY |  | código |
| `lying_pullover__dumbbells` | deltoid | - → SECONDARY |  | código |
| `lying_pullover__dumbbells` | forearm | - → STABILIZER |  | código |
| `lying_pullover__barbell` | deltoid | - → SECONDARY |  | tests |
| `lying_pullover__barbell` | forearm | - → STABILIZER |  | tests |
| `lying_pullover__kettlebell` | deltoid | - → SECONDARY |  | tests |
| `lying_pullover__kettlebell` | forearm | - → STABILIZER |  | tests |
| `lying_pullover__cable` | deltoid | - → SECONDARY |  | tests |
| `lying_pullover__cable` | forearm | - → STABILIZER |  | tests |
| `pullover__bilateral__cable` | deltoid | - → SECONDARY |  | código |
| `pullover__bilateral__cable` | erector_spinae | - → STABILIZER |  | código |
| `pullover__bilateral__cable` | forearm | - → STABILIZER |  | código |
| `pullover__unilateral__cable` | deltoid | - → SECONDARY |  | tests |
| `pullover__unilateral__cable` | erector_spinae | - → STABILIZER |  | tests |
| `pullover__unilateral__cable` | forearm | - → STABILIZER |  | tests |
| `seated_machine_pullover__machine` | core | STABILIZER → - |  | código |
| `seated_machine_pullover__machine` | deltoid | - → SECONDARY |  | código |
| `seated_machine_pullover__machine` | triceps | SECONDARY → STABILIZER |  | código |

## Cambios articulares

| configuración | articulación | antes → después |
|---|---|---|
| `conventional_row__barbell` | cadera | - → STABILIZER |
| `conventional_row__barbell` | columna-lumbar | - → STABILIZER |
| `conventional_row__dumbbells` | cadera | - → STABILIZER |
| `conventional_row__dumbbells` | columna-lumbar | - → STABILIZER |
| `conventional_row__machine` | cadera | - → STABILIZER |
| `conventional_row__machine` | columna-lumbar | - → STABILIZER |
| `conventional_row__smith_machine` | cadera | - → STABILIZER |
| `conventional_row__smith_machine` | columna-lumbar | - → STABILIZER |
| `conventional_row__kettlebell` | cadera | - → STABILIZER |
| `conventional_row__kettlebell` | columna-lumbar | - → STABILIZER |
| `conventional_row__cable` | cadera | - → STABILIZER |
| `conventional_row__cable` | columna-lumbar | - → STABILIZER |
| `gironda_row__wide` | columna-lumbar | - → STABILIZER |
| `gironda_row__medium` | columna-lumbar | - → STABILIZER |
| `gironda_row__close` | columna-lumbar | - → STABILIZER |
| `pendlay_row__barbell` | cadera | - → STABILIZER |
| `pendlay_row__barbell` | columna-lumbar | - → STABILIZER |
| `pendlay_row__dumbbells` | cadera | - → STABILIZER |
| `pendlay_row__dumbbells` | columna-lumbar | - → STABILIZER |
| `pendlay_row__machine` | cadera | - → STABILIZER |
| `pendlay_row__machine` | columna-lumbar | - → STABILIZER |
| `pendlay_row__smith_machine` | cadera | - → STABILIZER |
| `pendlay_row__smith_machine` | columna-lumbar | - → STABILIZER |
| `pendlay_row__kettlebell` | cadera | - → STABILIZER |
| `pendlay_row__kettlebell` | columna-lumbar | - → STABILIZER |
| `pendlay_row__cable` | cadera | - → STABILIZER |
| `pendlay_row__cable` | columna-lumbar | - → STABILIZER |
| `t_bar_row__t_bar__wide` | cadera | - → STABILIZER |
| `t_bar_row__t_bar__wide` | columna-lumbar | - → STABILIZER |
| `t_bar_row__t_bar__medium` | cadera | - → STABILIZER |
| `t_bar_row__t_bar__medium` | columna-lumbar | - → STABILIZER |
| `t_bar_row__t_bar__close` | cadera | - → STABILIZER |
| `t_bar_row__t_bar__close` | columna-lumbar | - → STABILIZER |
| `back_band_pull_apart__default` | codo | SECONDARY → STABILIZER |
| `back_remo_banda__default` | columna-lumbar | - → STABILIZER |
| `back_remo_gorilla_mancuernas__dumbbells` | cadera | - → STABILIZER |
| `back_remo_gorilla_mancuernas__dumbbells` | columna-lumbar | - → STABILIZER |
| `back_remo_gorilla_mancuernas__kettlebell` | cadera | - → STABILIZER |
| `back_remo_gorilla_mancuernas__kettlebell` | columna-lumbar | - → STABILIZER |
| `back_remo_gorilla_mancuernas__cable` | cadera | - → STABILIZER |
| `back_remo_gorilla_mancuernas__cable` | columna-lumbar | - → STABILIZER |
| `back_remo_invertido__default` | cadera | - → STABILIZER |
| `back_remo_invertido__default` | columna-lumbar | - → STABILIZER |
| `back_remo_renegado_mancuernas__dumbbells` | columna-lumbar | - → STABILIZER |
| `back_remo_renegado_mancuernas__kettlebell` | columna-lumbar | - → STABILIZER |
| `back_dominadas_escapulares__default` | codo | - → STABILIZER |
| `back_dominadas_escapulares__default` | columna-cervical | STABILIZER → - |
| `back_dominadas_escapulares__default` | muñeca | - → STABILIZER |
| `back_encogimientos__dumbbells` | muñeca | - → STABILIZER |
| `back_encogimientos__smith_machine` | muñeca | - → STABILIZER |
| `back_encogimientos__cable` | muñeca | - → STABILIZER |
| `back_encogimientos__kettlebell` | muñeca | - → STABILIZER |
| `back_encogimientos__barbell` | muñeca | - → STABILIZER |
| `back_encogimientos_kelso__barbell` | cadera | - → STABILIZER |
| `back_encogimientos_kelso__barbell` | columna-cervical | STABILIZER → - |
| `back_encogimientos_kelso__barbell` | columna-lumbar | - → STABILIZER |
| `back_encogimientos_kelso__barbell` | esternoclavicular | - → SECONDARY |
| `back_encogimientos_kelso__barbell` | muñeca | - → STABILIZER |
| `back_encogimientos_kelso__machine` | columna-cervical | STABILIZER → - |
| `back_encogimientos_kelso__machine` | esternoclavicular | - → SECONDARY |
| `back_encogimientos_kelso__machine` | muñeca | - → STABILIZER |
| `back_encogimientos_kelso__cable` | columna-cervical | STABILIZER → - |
| `back_encogimientos_kelso__cable` | columna-lumbar | - → STABILIZER |
| `back_encogimientos_kelso__cable` | esternoclavicular | - → SECONDARY |
| `back_encogimientos_kelso__cable` | muñeca | - → STABILIZER |
| `back_encogimientos_kelso__kettlebell` | columna-cervical | STABILIZER → - |
| `back_encogimientos_kelso__kettlebell` | esternoclavicular | - → SECONDARY |
| `back_encogimientos_kelso__kettlebell` | muñeca | - → STABILIZER |
| `back_encogimientos_kelso__smith_machine` | columna-cervical | STABILIZER → - |
| `back_encogimientos_kelso__smith_machine` | esternoclavicular | - → SECONDARY |
| `back_encogimientos_kelso__smith_machine` | muñeca | - → STABILIZER |
| `back_y_raises__default` | cadera | - → STABILIZER |
| `back_y_raises__default` | columna-cervical | STABILIZER → - |
| `back_y_raises__default` | columna-lumbar | - → STABILIZER |
| `back_y_raises__default` | glenohumeral | STABILIZER → PRIMARY |
| `lat_pulldown__unilateral__cable` | columna-toracica | - → STABILIZER |
| `lat_pulldown__unilateral__machine` | columna-toracica | - → STABILIZER |
| `lat_pulldown__unilateral__band` | columna-toracica | - → STABILIZER |
| `rack_chin__default` | cadera | - → STABILIZER |
| `lying_pullover__dumbbells` | codo | SECONDARY → STABILIZER |
| `lying_pullover__dumbbells` | columna-lumbar | - → STABILIZER |
| `lying_pullover__dumbbells` | escapulotoracica | PRIMARY → SECONDARY |
| `lying_pullover__barbell` | codo | SECONDARY → STABILIZER |
| `lying_pullover__barbell` | columna-lumbar | - → STABILIZER |
| `lying_pullover__barbell` | escapulotoracica | PRIMARY → SECONDARY |
| `lying_pullover__kettlebell` | codo | SECONDARY → STABILIZER |
| `lying_pullover__kettlebell` | columna-lumbar | - → STABILIZER |
| `lying_pullover__kettlebell` | escapulotoracica | PRIMARY → SECONDARY |
| `lying_pullover__cable` | codo | SECONDARY → STABILIZER |
| `lying_pullover__cable` | columna-lumbar | - → STABILIZER |
| `lying_pullover__cable` | escapulotoracica | PRIMARY → SECONDARY |
| `pullover__bilateral__cable` | cadera | - → STABILIZER |
| `pullover__bilateral__cable` | codo | SECONDARY → STABILIZER |
| `pullover__bilateral__cable` | escapulotoracica | PRIMARY → SECONDARY |
| `pullover__unilateral__cable` | cadera | - → STABILIZER |
| `pullover__unilateral__cable` | codo | SECONDARY → STABILIZER |
| `pullover__unilateral__cable` | columna-toracica | - → STABILIZER |
| `pullover__unilateral__cable` | escapulotoracica | PRIMARY → SECONDARY |
| `seated_machine_pullover__machine` | codo | SECONDARY → STABILIZER |
| `seated_machine_pullover__machine` | escapulotoracica | PRIMARY → SECONDARY |
| `seated_machine_pullover__machine` | muñeca | STABILIZER → - |

## Cambios de patrón

- `back_band_pull_apart__default`: horizontal_pull → horizontal_abduction
- `back_encogimientos_kelso__barbell`: scapular_elevation → scapular_retraction
- `back_encogimientos_kelso__machine`: scapular_elevation → scapular_retraction
- `back_encogimientos_kelso__cable`: scapular_elevation → scapular_retraction
- `back_encogimientos_kelso__kettlebell`: scapular_elevation → scapular_retraction
- `back_encogimientos_kelso__smith_machine`: scapular_elevation → scapular_retraction
- `back_y_raises__default`: scapular_elevation → shoulder_abduction_diagonal

## Topes de palabras (léxico): definiciones CURATED que usan cada palabra

| tope | usadas | máximo | en este lote |
|---|---|---|---|
| budget.claridad | 1 | 3 |  |
| budget.referencia | 1 | 8 |  |
| budget.concentrar | 1 | 15 |  |
| budget.repartir | 2 | 5 |  |
| budget.medir | 2 | 8 |  |
| budget.distinto | 2 | 8 |  |
