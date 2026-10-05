# Prompts específicos L7 — no usar el `brief_for` crudo

`scripts/catalog_v2_visual_brief.py` arma PROMPT y GEOMETRIA por `equipmentId`. En talones, sentada, burro y prensa comparten `machine` con la estación de pie. El `byVariant` solo se imprime como contexto. Estos textos sustituyen ese prompt crudo antes de cualquier generación. No se generó ninguna imagen.

Cabecera de casa, obligatoria y ya incluida:

`Semi-realistic 3D catalog illustration, not a comic, not a gym photo. No thick black outlines. No cel-shade. Isolated athlete and equipment on a black background. No logos, no text, blank plates, dark clothes. One scene only.`

## Estaciones que no pueden heredar la máquina de pie

### `calf_raise__bilateral__seated_machine`

GEOMETRIA: Atleta sentado, pelvis en el asiento, muslos horizontales, rodillas cerca de noventa grados. Ambos antepiés en la plataforma pequeña y talones altos. Cojines sobre los muslos distales. Ambas manos en las asas del brazo de carga. Sin cojines de hombro y sin respaldo inventado. El brazo sigue unido al bastidor y separado del tope inferior.

PROMPT: Side oblique view, frozen at the TOP of a bilateral seated calf raise. The athlete sits on the machine seat, thighs horizontal and knees bent near ninety degrees. Both forefeet press the small platform and both heels are raised. Thigh pads press the distal thighs. Both hands grip the load-arm handles. There are no shoulder pads and no invented backrest. The load arm stays attached to the frame and clear of the lower stop.

### `calf_raise__bilateral__donkey_machine`

GEOMETRIA: Cadera flexionada cerca de noventa grados, torso inclinado, rodillas casi extendidas. El cojín de la palanca descansa en la pelvis posterior, no en la espalda alta. Antebrazos y codos reposan en el apoyo frontal y las manos rodean sus asas. Ambos antepiés en el borde de la plataforma y talones altos. Sin apoyo de pecho.

PROMPT: Side view, frozen at the TOP of a bilateral donkey calf raise. The hips are flexed near ninety degrees, the torso is inclined and the knees are nearly straight. A lever pad rests on the back of the pelvis, not on the upper back. Both forearms and elbows rest on the front support and both hands hold its handles. Both forefeet contact the platform edge and both heels are raised. There is no chest pad.

### `calf_raise__bilateral__leg_press_machine`

GEOMETRIA: Prensa inclinada. Espalda y pelvis apoyadas en el asiento, manos en asas laterales, rodillas casi extendidas con una flexión pequeña fija. Ambos antepiés en la franja inferior de la plataforma, talones libres y altos. El carro sigue en sus rieles, separado de los topes. Sin cojines de hombro y sin ascenso del torso.

PROMPT: Side view, frozen at the TOP of bilateral calf raises on a forty-five-degree leg press. The back and pelvis rest on the seat, both hands hold the side handles, and the knees stay nearly straight with a small fixed bend. Both forefeet contact the lower strip of the platform and both heels are raised and free. The carriage stays on its rails, clear of the stops. There are no shoulder pads and the torso does not rise.

## Unilaterales: el prompt bilateral no acredita el pie libre

Añadir esta frase al prompt del implemento correspondiente y sustituir la geometría de dos pies por un solo antepié de trabajo:

`Only the working forefoot contacts the block or platform. The free leg is bent and its foot is clear of that surface. Both hands keep the same grip as the bilateral setup.`

Aplica a:

- `calf_raise__unilateral__barbell`
- `calf_raise__unilateral__cable`
- `calf_raise__unilateral__machine`
- `calf_raise__unilateral__smith_machine`
- `hip_thrust__unilateral__barbell`
- `hip_thrust__unilateral__smith_machine`
- `hip_thrust__unilateral__machine`
- `hip_thrust__unilateral__band`
- `glutes_puente_gluteos` unilaterales que compartan PNG bilateral

La barra unilateral de talones sigue sin demostración primaria exacta. El prompt describe la extensión geométrica heredada; no la declara validada ni autoriza retirarla.
