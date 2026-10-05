# QA visual L7 — 21 PNG abiertos

Fecha: 2026-10-05. Se abrieron los 21 PNG únicos del inventario `rebase_actual/inventory.json`. Los 21 hashes del disco coinciden con el inventario. Un PNG de implemento no aprueba la lateralidad contraria. No se generó ninguna imagen y no se retocó un PNG para que pasara.

## Hashes

Los 21 coinciden. Lista en `review_close/png_unique.json`.

## Veredictos por archivo

| PNG | Lo que se ve | Veredicto |
|---|---|---|
| `exercise_calf_raise_machine_batch9.png` | Máquina de pie, cojines de hombro, ambas manos en asas, dos pies en la plataforma. Los talones no están claramente altos. | Bilateral de pie: FALLA de fase alta. Unilateral: FALLA, los dos pies apoyan. Sentada, burro y prensa: FALLA, no es esa estación. |
| `exercise_calf_raise_barbell_batch9.png` | Barra sobre la espalda alta y dos manos. Dos pies planos en el suelo, sin bloque y sin talones altos. | Bilateral: FALLA de fase y de bloque. Unilateral: FALLA, los dos pies apoyan. |
| `exercise_calf_raise_smith_machine_batch9.png` | Smith con rieles, barra en hombros, bloque bajo dos pies. Talones no altos. | Bilateral: FALLA de fase alta; la estación sí se reconoce. Unilateral: FALLA. |
| `exercise_calf_raise_cable_batch9.png` | Polea baja, barra corta delante de los muslos, dos pies planos, sin bloque. | Bilateral: FALLA de fase y de bloque. El cable sí está conectado. Unilateral: FALLA. |
| `exercise_hip_thrust.png` | Espalda alta en banco, barra acolchada en la pelvis, dos pies, manos en el eje, cadera alta. | Bilateral barra: PASA. Unilateral: FALLA, los dos pies apoyan. |
| `exercise_hip_thrust_smith.png` | Smith, banco, barra guiada en la pelvis, dos pies, cadera alta. | Bilateral Smith: PASA. Unilateral: FALLA. |
| `exercise_hip_thrust_maquina.png` | Respaldo, plataforma y rodillo, pero también una barra libre con disco. | Bilateral y unilateral máquina: FALLA. Mezcla barra libre y rodillo. |
| `exercise_hip_thrust_banda.png` | Banda alrededor de los muslos, no anclada a dos puntos bajos sobre la pelvis. | Bilateral y unilateral banda: FALLA. |
| `exercise_glutes_frog_pumps_batch8.png` | Plantas juntas y rodillas abiertas, pero espalda, codos y pies no apoyan: el cuerpo flota. | FALLA. |
| `exercise_glutes_monster_walk_banda_batch8.png` | Banda en muslos, postura abierta estática. La ficha pide lazo en tobillos y un paso lateral. | FALLA QA de tobillos y de pie líder. |
| `exercise_glutes_puente_gluteos_barbell_batch10.png` | Hombros en el suelo, barra acolchada en la pelvis, dos manos, dos pies, pelvis alta. | Bilateral barra: PASA. Unilateral: FALLA. |
| `exercise_glutes_puente_gluteos_dumbbells_batch10.png` | Hombros en el suelo, una mancuerna sujeta sobre la pelvis, dos pies. | Bilateral mancuerna: PASA. Unilateral: FALLA. |
| `exercise_glutes_puente_gluteos_smith_machine_batch10.png` | Vista frontal, torso que se lee erguido, barra en los rieles a la altura de la cadera. No se ven los hombros en el suelo. | Bilateral y unilateral Smith: FALLA. |
| `exercise_copenhagen_plank_bodyweight_batch10.png` | Apoyo de puño, no de antebrazo. El contacto con el banco se lee en el pie, no en la cara interna del tobillo. | FALLA. |
| `exercise_copenhagen_plank_dynamic_bodyweight_batch10.png` | Mismo puño. El pie inferior llega al suelo. | FALLA del punto alto y del antebrazo. |
| `exercise_hip_abduction_machine_batch10.png` | Máquina sentada, respaldo, almohadillas en muslos, dos piernas. | Sentada bilateral: PASA de estación. De pie y unilateral: FALLA. |
| `exercise_hip_abduction_cable_batch10.png` | De pie, un tobillo con cincha, cable a la polea baja, mano en la torre, la otra pierna apoyada. | Unilateral de pie: PASA. Bilateral de pie: FALLA y sigue sin montaje humano. |
| `exercise_hip_abduction_band_batch10.png` | Sentada en banco con banda en muslos. | De pie, bilateral o unilateral: FALLA. No responde el montaje bilateral de pie. |
| `exercise_hip_adduction_machine_batch10.png` | Máquina sentada, respaldo, almohadillas entre las rodillas, dos piernas, torre de discos. | Sentada bilateral: PASA de estación. De pie y unilateral: FALLA. |
| `exercise_hip_adduction_cable_batch10.png` | De pie, un tobillo con cincha que cruza hacia la polea, la otra planta apoyada, mano en la torre. | Unilateral de pie: PASA. Bilateral de pie: FALLA y sigue sin montaje humano. |
| `exercise_hip_adduction_band_batch10.png` | De pie, banda del tobillo de trabajo al rack, la otra planta apoyada, mano en el poste. | Unilateral de pie: PASA. Bilateral de pie: FALLA y sigue sin montaje humano. |

## Sin PNG

No hay archivo para puente corporal, Reverse Hyper, talón corporal, sentada, burro, prensa, tibial, ni para las patadas posterior y diagonal. Esas QA quedan SIN_IMAGEN. No se convierten en PASA.

## Cola que sale de esta revisión

PASA solo en la lateralidad vista: Hip Thrust barra bilateral, Hip Thrust Smith bilateral, puente barra bilateral, puente mancuerna bilateral, abducción sentada bilateral, abducción de pie en polea unilateral, aducción sentada bilateral, aducción de pie en polea unilateral y aducción de pie con banda unilateral.

El resto de filas de la tabla es FALLA del contrato de esa configuración. Crear, sin copiar la máquina de pie: sentada, burro, prensa, tibial, Reverse Hyper, puente corporal y las dos patadas. No usar un PNG bilateral como prueba del chip unilateral.
