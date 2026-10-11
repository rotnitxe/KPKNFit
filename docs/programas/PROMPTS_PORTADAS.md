# Prompts maestros de las portadas de programas

Un prompt por programa de la app: **77 en total**. Son las 47 fichas que se listan en la biblioteca y las 30 variantes del generador (semana simple, en bloques y periodizada). La lista sale de `PlanEditorialTable.kt`, con los ids tal cual están en el código.

Para generar una portada se usan tres piezas: la guía de estilo maestra va delante, después el prompt del programa (de 60 a 110 palabras, sin contar la guía) y, aparte, el prompt negativo común. Cada prompt lleva el acento de su perfil según la tabla de abajo.

## Guía de estilo maestra

```text
Cinematic editorial fitness photograph on a deep black background. Hard warm side light from one direction, fine film grain, high contrast, shallow depth of field, muted natural skin tones. One single athlete, never recognisable: seen from behind, in shadow, or cropped at the neck. Plain training clothes with no logos or brand marks. The athlete is caught mid-gesture of the sport, with real body mechanics and no posing for the camera. One accent colour appears only as a thin cut light along the athlete's edges and the equipment, as set by the profile table. No text, letters, numbers, logos or watermarks anywhere in the image. Vertical 4:5 format, 1600 by 2000 pixels; the subject sits inside the central 60 percent of the frame so it survives vertical cropping and a 16:9 banner.
```

## Acento por perfil

| Perfil | Disciplinas | Acento |
|---|---|---|
| músculo | fuerza y masa, powerbuilding, culturismo, powerlifting | `#F49A6E` |
| columna | strongman, halterofilia | `#8FB2FF` |
| energía | fuerza y cardio, funcional | `#F7CF73` |
| mente | calistenia | `#C9B8FF` |
| ok | armwrestling | `#43D18C` |

Las plantillas y los protocolos sin disciplina propia (semanas en blanco, por ejemplo) usan el acento de músculo.

## Prompt negativo común

```text
text, letters, numbers, watermarks, logos, brand names, sharp recognisable faces, deformed hands, extra fingers, impossible or floating equipment, coloured backgrounds, heavy vignette, more than one person, 3D illustration look, plastic skin, cartoon style, oversaturated colours, cluttered background.
```

---
## Planes propios de KPKN

### Fundamentos de fuerza · `native:strength-foundation-v2`

Archivo: cover_native_strength_foundation_v2.webp

A concrete garage with rubber floor tiles and a steel power rack, one bare bulb casting hard warm light from the side. A single athlete seen from behind, mostly in shadow, a barbell resting across the upper back. The athlete sinks into a deep back squat with the chest tall, knees tracking over the toes and the bar bending slightly under load. Captured at the bottom of the rep, the instant before the drive upward. Thin cut light in #F49A6E along the shoulders and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el garaje de suelo de goma y la sentadilla de fondo.

### Fundamentos de músculo · `native:muscle-foundation-v2`

Archivo: cover_native_muscle_foundation_v2.webp

Inside a university weight room, racks of rubber dumbbells blur into deep black behind. A single athlete in strict profile, face turned away, performs a slow incline dumbbell curl on a padded bench. The elbow stays pinned beside the torso while the forearm lowers with visible control, the biceps tight and the tempo deliberately slow. Captured mid-descent, the dumbbell just past halfway. Hard warm side light, with a thin cut light in #F49A6E tracing the upper arm and the shoulder. Fine film grain, black background. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la sala universitaria y el curl lento de mancuerna.

### Fundamentos de powerbuilding · `native:powerbuilding-foundation-v2`

Archivo: cover_native_powerbuilding_foundation_v2.webp

A basement gym with exposed pipes running along a low ceiling and a wall of steel plates in shadow. Photographed from the foot of a flat bench, the athlete's face hidden in darkness, only torso and forearms lit. The barbell is held an inch above the sternum in a deliberate pause, elbows angled at about forty-five degrees and shoulder blades pinned into the bench before the press begins. Hard warm side light, a thin cut light in #F49A6E along the chest and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el sótano de tuberías y la pausa en el pecho.

### Atleta completo · `native:complete-athlete-v2`

Archivo: cover_native_complete_athlete_v2.webp

An outdoor sports field at dusk, the turf fading into dark shadows and a low wooden plyo box in the foreground. A single athlete in side profile, full body in frame and face turned away, leaps explosively onto the box. Captured at the peak of the jump: knees bent, arms swinging upward for momentum, the body suspended for an instant above the turf. Low warm sun from the side, a thin cut light in #F7CF73 along the legs and arms, fine film grain, black background. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de energía por el campo al atardecer y el salto explosivo sobre cajón.

### Construye músculo con máquinas · `native:machine-muscle`

Archivo: cover_native_machine_muscle.webp

A corner of a 24-hour gym seen through glass doors, a row of guided machines dissolving into darkness. A single athlete, shot from the side with the face in shadow, sits at a seated chest press machine and pushes both handles forward, elbows only slightly bent, the metal guide rods gleaming. Captured near full extension, chest fully contracted and the movement controlled. Hard warm side light and a thin cut light in #F49A6E along the shoulder, the forearm and the steel frame. Black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el gimnasio de madrugada y la prensa de pecho guiada.

### Músculo con tu peso corporal · `native:bodyweight`

Archivo: cover_native_bodyweight.webp

An empty city park at dawn, wet grass and low mist, a steel pull-up bar fixed between two posts. A single athlete seen from behind, face hidden, performs a strict pull-up and reaches the top with the chin level with the bar. Back muscles engaged, shoulder blades drawn down, the body hanging perfectly still with no swing. Cool dawn light mixed with a hard warm side light, and a thin cut light in #C9B8FF along the spine and the bar. Fine film grain, deep black background beyond the trees. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de mente por el parque al amanecer y la dominada estricta, sin impulso.

## Plantillas

### Powerlifting para principiantes · `template:power-12-3`

Archivo: cover_template_power_12_3.webp

A commercial gym's rubber lifting platform at early morning, empty benches and dim racks fading behind. A single athlete in side view, face in shadow, sets up a conventional deadlift: hips low, shoulders just over the bar, chest proud and the back flat, the bar touching the shins. Hands grip the bar and the body tenses for the second before the pull leaves the floor. Hard warm side light from a high window, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la plataforma de goma y el arranque del peso muerto.

### Powerlifting intermedio · `template:power-16-4`

Archivo: cover_template_power_16_4.webp

A warehouse basement with chalk dust hanging in a sunbeam that falls from high windows, a power rack holding a low box in the middle. A single athlete seen from a rear three-quarter angle, face hidden, sits back onto the box in a box squat, hips pushed out and the weight settling onto the box for a deliberate pause. Chalk particles catch the light around the bar. Hard warm side light and a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el almacén con polvo de magnesio y la sentadilla a cajón.

### Powerlifting avanzado · `template:power-20-5`

Archivo: cover_template_power_20_5.webp

A steel-clad factory hall with a bare concrete floor and a bolted lifting rig, cold shafts of light crossing the floor. A single athlete seen from a low side angle, face turned down and hidden, pulls a heavy conventional deadlift as the bar passes the knees. Grip chalked, hands white with powder, chalk dusting the thighs, the back held flat and the hips driving forward. Hard warm side light, a thin cut light in #F49A6E along the bar and the forearm, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la nave de hormigón y el peso muerto con barra en la rodilla.

### Fuerza y músculo avanzado · `template:powerbuild-16-4`

Archivo: cover_template_powerbuild_16_4.webp

A wooden-floored training hall with tall windows, late afternoon light angling across the boards and leaving the rest in black. A single athlete seen from a front three-quarter angle, face hidden in shadow, lowers a heavy barbell on an inclined bench toward the upper chest. Elbows tucked, forearms vertical, lats braced, the bar just touching the chest before the drive. Hard warm side light, a thin cut light in #F49A6E along the shoulders and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la sala de madera con ventanales y la banca inclinada pesada.

### Torso y pierna para ganar músculo · `template:body-12-3`

Archivo: cover_template_body_12_3.webp

A mirrored studio on the top floor of a shopping center, the mirrors reflecting only blackness and a faint strip of light. A single athlete seen in side view, face hidden, sits in a leg press with feet placed high on the platform. At the bottom of the rep, knees bent deeply and driving outward toward the chest, heels pressing and the thighs fully loaded. Hard warm side light and a thin cut light in #F49A6E along the thigh and the calf, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el estudio con espejos y la prensa de pierna con pies altos.

### Empuje, tirón y pierna avanzado · `template:body-16-4`

Archivo: cover_template_body_16_4.webp

A rooftop deck with wooden boards, a low parapet and dark city towers far behind, all dissolving into black. A single athlete in side profile, face turned away, performs a one-arm dumbbell row: one hand braced on a bench, the other arm pulling the dumbbell toward the hip. The elbow drives back, the torso runs parallel to the floor and the shoulder blade squeezes at the top. Hard warm side light, a thin cut light in #F49A6E along the back and the dumbbell, fine film grain, black background. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la azotea con torres lejanas y el remo a una mano.

### Músculo en bloques con énfasis · `template:body-20-5`

Archivo: cover_template_body_20_5.webp

A converted attic with exposed wooden beams and a single skylight throwing one shaft of light across the floor. A single athlete in side profile, face in shadow, performs a Romanian deadlift with a pair of dumbbells: hips pushed back, the dumbbells sliding down the thighs, back flat and the hamstrings stretched at the bottom. Captured at the deepest point of the hinge. Shaft of skylight across the back, hard warm side light, a thin cut light in #F49A6E along the hamstrings, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el desván de vigas y el peso muerto rumano con mancuernas.

### Semana en blanco · `template:simple-1`

Archivo: cover_template_simple_1.webp

A minimalist gym with bare plaster walls falling into shadow and a single flat bench in an otherwise empty room. Shot from the front, from the waist up, the athlete's face out of frame. Both palms clap together to spread white chalk, a small cloud drifting through the light before the hands reach for a barbell. Hard warm side light and a thin cut light in #F49A6E along the hands and forearms, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la sala vacía y el magnesio antes de coger la barra.

### Dos semanas alternas (A/B) · `template:simple-ab`

Archivo: cover_template_simple_ab.webp

A riverside boat shed with wooden shelves holding old dumbbells, river light slipping through slatted walls. Shot low, the athlete cropped at the torso with the face hidden, one hand lifts a dumbbell from the floor while a second, different dumbbell rests beside the foot. The hand closes on the second weight, choosing between two loads. Slatted river light, hard warm side light, a thin cut light in #F49A6E along the forearm and the dumbbell, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el cobertizo junto al río y la alternancia de dos mancuernas.

### Cuatro semanas en blanco · `template:simple-4`

Archivo: cover_template_simple_4.webp

A converted church hall with tall arched windows and a worn wooden floor, the nave dissolving into dark shadow. A close medium shot of an athlete's hands and forearms, face out of frame. The thumb presses a spring collar onto the end of a barbell resting on the rack until the clip snaps shut. Soft light from the arched windows mixed with hard warm side light, a thin cut light in #F49A6E along the knuckles and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la iglesia reconvertida y el collar de resorte al cerrar la barra.

---
## Protocolos de terceros

### Texas Method (3 días) · `protocol:texas-method-3d`

Archivo: cover_protocol_texas_method_3d.webp

A metal shed with a concrete slab, a roll-up door half open and one cold shaft of daylight across the floor. A single athlete in side profile, face tight in shadow, drives a heavy back squat for a five-rep attempt. The bar bows under the load, the torso braced and the hands gripping wide, the ascent halfway up. Cold daylight mixed with hard warm side light, a thin cut light in #F49A6E along the bar and the back, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el cobertizo de chapa y la sentadilla de cinco repeticiones.

### Texas Method (4 días) · `protocol:texas-method-4d`

Archivo: cover_protocol_texas_method_4d.webp

A seaside pavilion with open sides, salt wind moving through the air and a grey sea blurred behind. A single athlete seen from a low front angle, face in shadow, performs a standing barbell overhead press to lockout: arms straight above the head, the torso braced and the bar stacked over the midfoot. Low sun flares at the pavilion edge. Hard warm side light, a thin cut light in #F49A6E along the shoulders and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el pabellón junto al mar y el press militar de pie.

### 5/3/1 Boring But Big · `protocol:wendler-531-bbb`

Archivo: cover_protocol_wendler_531_bbb.webp

A stone cellar with an arched brick ceiling and a single lamp swinging slightly overhead. A single athlete in side view, face hidden, performs a back squat set of ten with a lighter barbell across the upper back. Captured on the fifth rep: knees driving out, chest up, breath held and the bar gripped firm. The volume set is the point, the rhythm steady. Swinging lamp light and hard warm side light, a thin cut light in #F49A6E along the shoulders and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el sótano abovedado de ladrillo y la serie de diez sentadillas.

### 5/3/1 First Set Last · `protocol:wendler-531-fsl`

Archivo: cover_protocol_wendler_531_fsl.webp

A mountain cabin deck among pine trees, weathered wooden planks and soft dusk light. A single athlete seen from a side angle at the foot of a bench, face hidden in shadow, performs a close-grip bench press. Elbows tucked close to the body, the bar lowered to the lower chest in a controlled pause, forearms vertical. Blurred pines behind, dusk light mixed with hard warm side light, a thin cut light in #F49A6E along the forearms and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el porche de cabaña entre pinos y la banca de agarre cerrado.

### Madcow 5×5 · `protocol:madcow-5x5`

Archivo: cover_protocol_madcow_5x5.webp

A steel-beam loft with plate racks along brick walls and a floor scuffed by years of lifting. Shot low and close, the athlete's face out of frame and only hands, forearms and the plate lit. The athlete slides a heavy plate onto the sleeve of a barbell to build the next step of the ramp, guiding it with one hand while leaning in. The plate meets the sleeve, the collar not yet clipped. Hard warm side light, a thin cut light in #F49A6E along the plate edge and the forearm, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el loft de acero y el disco que se desliza a la barra.

### GZCLP · `protocol:gzclp`

Archivo: cover_protocol_gzclp.webp

A community center gym with plain painted walls fading into shadow and a cable station under a soft fluorescent glow. A single athlete, shot from a front side angle with the face hidden, pulls a cable lat pulldown bar down to the upper chest. Shoulders pulled down and back, elbows driving toward the ribs, the grip held wide and the back muscles tight. Captured at the end of the pull. Soft fluorescent glow with hard warm side light, a thin cut light in #F49A6E along the lats and the cable, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el centro comunitario y el jalón al pecho como accesorio.

### Candito 6 semanas · `protocol:candito-6`

Archivo: cover_protocol_candito_6.webp

An outdoor swimming-pool deck with a tiled edge, the pool's still water reflecting dim light and the surroundings dissolving into night. A single athlete seen from the side, face hidden, pulls a heavy single conventional deadlift near the maximum. Grip chalked and braced, the bar close to the shins, the hips rising as the bar leaves the tiles. The lifter's focus is total and the torso rigid. Reflected water light, hard warm side light, a thin cut light in #F49A6E along the thighs and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el borde de la piscina y el peso muerto de una sola repetición.

### Calgary Barbell 16 semanas · `protocol:calgary-16`

Archivo: cover_protocol_calgary_16.webp

An old cinema lobby with dark velvet curtains, a brass rail and a dim chandelier far above, its light falling in a soft cone. A single athlete photographed from the foot of a bench, face hidden, lowers and presses a pair of dumbbells on a flat bench. Dumbbells rising over the chest, arms straightening at the top, elbows locked and the wrists stacked over the elbows. Warm chandelier light mixed with hard warm side light, a thin cut light in #F49A6E along the arms and the dumbbells, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el vestíbulo de cine y la banca con mancuernas.

### TSA 9 semanas (intermedio) · `protocol:tsa-9`

Archivo: cover_protocol_tsa_9.webp

A train platform after hours under a steel canopy, amber sodium lamps glowing through falling rain. A single athlete seen from a side angle, face hidden, lies on a padded volume bench and lowers a barbell to the chest in a set of eight, shoulders pinned to the bench and the breath held. Wet steel and falling rain catch the light. Amber sodium glow mixed with hard warm side light, a thin cut light in #F49A6E along the arms and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el andén bajo la marquesina y la banca de volumen.

### Korte 3×3 · `protocol:korte-3x3`

Archivo: cover_protocol_korte_3x3.webp

A sandy beach at dawn with firm wet sand and a dark sky over the water, a portable steel rack set up at the waterline. A single athlete seen from behind, face hidden, sets up a heavy back squat single near the maximum: bar on the upper back, the body braced and still for a long moment before the ascent. Wet sand reflects the faint dawn light. Faint dawn light mixed with hard warm side light, a thin cut light in #F49A6E along the shoulders and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la arena de la playa y la sentadilla de una sola repetición.

### Lilliebridge · `protocol:lilliebridge`

Archivo: cover_protocol_lilliebridge.webp

An underpass beneath a highway at night, concrete pillars and a single sodium lamp throwing hard shadows. A single athlete seen from behind, face hidden, pulls a heavy deadlift from the floor: hips and shoulders rising together, the bar scraping close to the shins, the back flat and the grip locked. Captured in the middle of the pull. Sodium light mixed with hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el paso bajo la autovía y el peso muerto de la semana par.

### Sheiko (programas 29 a 32) · `protocol:sheiko-29-32`

Archivo: cover_protocol_sheiko_29_32.webp

An empty theater stage with a single spotlight cutting through dust, the rows of seats vanishing into black. A single athlete in side profile, face hidden, works a back squat set of low repetitions: bar high on the traps, the hips rising and the torso steady through every rep of the set. Dust hangs in the spotlight. Hard warm side light from the spotlight, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el escenario vacío y la sentadilla de muchas series bajas.

### Cube Method · `protocol:cube-method`

Archivo: cover_protocol_cube_method.webp

A hangar with a painted concrete floor and one shaft of light from an open door, the vast space falling into black. A single athlete seen from the side, face hidden, drives a fast bench press: the bar kicking up off the chest with explosive speed, arms extending, elbows flared just enough. A cloud of dust rises from the floor. Hard warm side light, a thin cut light in #F49A6E along the chest and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el hangar de suelo pintado y la banca rápida que sale del pecho.

### Westside (método conjugado) · `protocol:westside-conjugate`

Archivo: cover_protocol_westside_conjugate.webp

A tennis court with a steel rack at one end, the net a faint line in the dark and the court surface reflecting a little faint light. A single athlete seen from behind, face hidden, performs a max-effort double with a rotating variant: a pin press, the bar pushed off the safety pins at the bottom of the rack with a hard, fast drive. Hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la pista de tenis y el press desde los pernos de la jaula.

### Juggernaut Method 2.0 · `protocol:juggernaut-2`

Archivo: cover_protocol_juggernaut_2.webp

A bus depot maintenance bay with yellow floor lines and a row of steel lifts, the bay sinking into darkness. A single athlete seen in side profile, face hidden, presses a barbell overhead in a wave of five reps, the final rep fast and locked out. Core tight, feet planted on a yellow line, the bar stacked over the midfoot. Hard warm side light, a thin cut light in #F49A6E along the shoulders and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la nave de autobuses y el press militar de una ola de cinco.

### nSuns 5/3/1 · `protocol:nsuns-531-lp-4d`

Archivo: cover_protocol_nsuns_531_lp_4d.webp

A rooftop parking garage at dusk, concrete ramps and sodium light fading into shadow. A single athlete seen from a front three-quarter angle, face in shadow, bench presses a heavy barbell on a near-maximal set: the bar touching the sternum, the chest tight and the arms driving the final rep to its limit. Sodium light and hard warm side light, a thin cut light in #F49A6E along the forearms and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el garaje de azotea y la banca de la tercera serie.

### GZCL Jacked & Tan 2.0 · `protocol:gzcl-jt-2`

Archivo: cover_protocol_gzcl_jt_2.webp

A museum atrium after closing, a white marble floor reflecting a single overhead light and the gallery walls falling into darkness. A single athlete seen from behind, face hidden, pulls a deadlift for maximum repetitions at a moderate weight: straps around the wrists, the back flat, the bar rising steadily with the hips. The empty atrium echoes around the lifter. Single overhead light mixed with hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el atrio de museo cerrado y el peso muerto por repeticiones.

### GZCL The Rippler · `protocol:gzcl-rippler`

Archivo: cover_protocol_gzcl_rippler.webp

A snowy field at dawn with fresh footprints and a portable steel rack standing in the white snow. A single athlete seen from the side, face hidden, performs a heavy double squat: bar on the upper back, the first rep driven up from the bottom and the second rep pushed to the top. Snow reflects the faint dawn light. Faint dawn light mixed with hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el campo nevado y los dobles pesados de sentadilla.

### GZCL UHF 9 · `protocol:gzcl-uhf-9`

Archivo: cover_protocol_gzcl_uhf_9.webp

An underground parking garage with yellow pillars and a painted concrete floor fading into shadow. A single athlete seen from the side, face hidden, lies on a flat bench for a lighter bench session: the bar lowered to the chest with a steady, technical press, the elbows at a clean angle and the grip relaxed. Strip light overhead, the garage's painted lines glowing faintly. Hard warm side light, a thin cut light in #F49A6E along the chest and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el garaje subterráneo y la banca ligera de la segunda sesión.

### Smolov (solo sentadilla) · `protocol:smolov`

Archivo: cover_protocol_smolov.webp

A vineyard terrace with stone retaining walls and rows of bare vines fading into the dusk. A single athlete seen from behind, face hidden, sits back onto a low box in a box squat, the hips pushed out and the chest up, the bar on the upper back. Terraced stone steps lead away into the dark. Dusk light mixed with hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el viñedo con muros de piedra y la sentadilla a cajón.

### Smolov Jr (solo sentadilla) · `protocol:smolov-jr`

Archivo: cover_protocol_smolov_jr.webp

A greenhouse with humid glass panes and broad leaves blurred in the background, the morning light filtered soft and white. A single athlete seen from the side, face hidden, performs a squat in the tenth set of a series of three: the bar on the upper back, the lifter breathing hard and the descent controlled. Water beads on the glass. Filtered morning light mixed with hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el invernadero y la décima serie de tres sentadillas.

### Complemento de peso muerto (Coan y Philippi) · `protocol:coan-phillipi-dl`

Archivo: cover_protocol_coan_phillipi_dl.webp

A forest clearing beside a fallen log, dappled light falling through the trees and the undergrowth dissolving into shadow. A single athlete seen from a low side angle, face hidden, pulls a deadlift off the floor with straps wrapped around the wrists: back flat, the bar close to the shins, the hips and shoulders rising together. Leaves and bark sit in the foreground. Dappled light with hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el claro de bosque y el peso muerto con straps, un complemento semanal.

---
## Protocolos propios de KPKN y versiones de PHUL y PHAT

### Bloque de los tres básicos · `protocol:kpkn-native-sbd-4`

Archivo: cover_protocol_kpkn_native_sbd_4.webp

An old mill with a stone floor and heavy wooden beams overhead, rough walls falling into deep shadow. A single athlete seen from the side, face hidden, performs a heavy single back squat near ninety percent of the training max during the peak week. Braced and still, the athlete takes a deep breath before the ascent, the bar bending slightly on the upper back. Hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el molino de piedra y la sentadilla de una repetición del pico.

### Empuje, tirón y pierna en 12 semanas · `protocol:kpkn-ppl-6`

Archivo: cover_protocol_kpkn_ppl_6.webp

A hotel fitness room late at night, a faint night light across a mirror wall and the room's corners dissolving into black. A single athlete seen from behind, face hidden, sits on a bench and presses a pair of dumbbells overhead in a seated shoulder press, palms facing forward and the elbows just below the shoulders at the bottom. The dumbbells rise together in a steady rhythm. Hard warm side light, a thin cut light in #F49A6E along the shoulders and the dumbbells, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el gimnasio de hotel de noche y el press sentado con mancuernas.

### Mesociclo de músculo (estilo RP) · `protocol:kpkn-rp-style`

Archivo: cover_protocol_kpkn_rp_style.webp

A seaside boardwalk with iron railings, fog rolling in from the sea and the wooden planks wet with dew. A single athlete seen in side profile, face hidden, stands at a low cable pulley and raises a cable lateral: the arm lifting out to shoulder height, the elbow slightly bent, the torso steady and the motion controlled. Fog drifts through the dim light around the cable. Hard warm side light, a thin cut light in #F49A6E along the shoulder and the cable, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el paseo marítimo y la elevación lateral en polea.

### Fuerza por esfuerzo (estilo RTS) · `protocol:kpkn-rts-style`

Archivo: cover_protocol_kpkn_rts_style.webp

A desert plateau of dark rock formations under a dim sky, the stone falling into shadow at the edges. A single athlete seen from behind, face hidden, performs a heavy back squat set at about eight out of ten effort: the bar on the upper back, the torso braced, the bar bending under the load and the feet set wide. The effort is visible in the shoulders. Hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la meseta de roca oscura y la sentadilla a esfuerzo ocho de diez.

### Fuerza por repeticiones al máximo (estilo SBS) · `protocol:kpkn-sbs-rtf`

Archivo: cover_protocol_kpkn_sbs_rtf.webp

A rural tractor shed with dusty light falling through the gaps in the corrugated roof and old machinery in shadow. A single athlete in side profile, face hidden, performs a bench press on the final set taken to maximum repetitions: the bar sinking to the chest, the lifter pushing for one more rep, the elbows tucked and the face tight. Dust hangs in the shafts of light. Dusty light mixed with hard warm side light, a thin cut light in #F49A6E along the chest and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el cobertizo de tractores y la última serie de banca hasta el máximo.

### PHUL · `original:phul-ms-2021-r1`

Archivo: cover_original_phul_ms_2021_r1.webp

A school gymnasium with wooden bleachers folded against the wall and tall windows letting in a thin band of light. A single athlete seen from behind, face hidden, performs a barbell bent-over row for a set of five: torso near forty-five degrees, the bar pulled to the lower ribs, the back flat and the elbows driving back. The bar sits low at the start of each rep. Thin band of window light, hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el gimnasio escolar y el remo con barra de cinco repeticiones.

### PHAT · `original:phat-biolayne-2016-r1`

Archivo: cover_original_phat_biolayne_2016_r1.webp

A ferry deck in sea wind, white spray and a rope railing against a dark horizon, the deck lights glowing dimly. A single athlete seen from the side, face hidden, performs a fast, light set of barbell squats: the bar moving quickly up out of the bottom with a loose, rhythmic breath and the knees tracking. The sea wind lifts a corner of the athlete's shirt. Dim deck lights mixed with hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la cubierta de ferry y la serie rápida y ligera de sentadillas.

### PHUL adaptado · `adapted:phul-kpkn-r1`

Archivo: cover_adapted_phul_kpkn_r1.webp

A cobblestone town square with an outdoor fitness corner of old iron frames, the stones glistening after rain and the surroundings dissolving into shadow. A single athlete seen from behind, face hidden, sits at a seated cable row in place of a barbell row: leaning back with the handle pulled toward the lower ribs, the cable taut and the back muscles engaged. Wet stones reflect a faint light. Hard warm side light, a thin cut light in #F49A6E along the back and the cable, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la plaza de adoquines y el remo sentado en lugar de barra.

### PHAT adaptado · `adapted:phat-kpkn-r1`

Archivo: cover_adapted_phat_kpkn_r1.webp

A river pier at dusk with worn wooden planks and calm water reflecting the last light, the far bank dissolving into darkness. A single athlete seen from the side, face hidden, presses one arm of a landmine: the barbell end driven upward at an angle while the other hand rests on the hip, the torso braced against the lift. Still water mirrors the light. Hard warm side light, a thin cut light in #F49A6E along the shoulder and the barbell end, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el muelle del río y el press con landmine a una mano.

---
## Generador de rutinas: semana simple

### Base sólida · `generated:strength-muscle`

Archivo: cover_generated_strength_muscle.webp

A shipping-container gym with corrugated steel walls and a single hanging lamp swinging above a rack. A single athlete seen in side profile, face hidden, drives a heavy overhead press with a barbell after finishing the basic lifts: the bar rising from the shoulders to lockout, the core braced hard and the feet planted. Metal ribs catch the lamp light. Hard warm side light, a thin cut light in #F49A6E along the shoulders and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el contenedor reconvertido y el press militar pesado de los básicos.

### Semana híbrida · `generated:hybrid`

Archivo: cover_generated_hybrid.webp

A gravel track beside a still lake at dusk, the water flat and dark and the gravel catching the last light. A single athlete seen in side profile, face hidden, pushes a loaded sled forward across the gravel: body leaning in, legs driving, arms straight on the handles and stones spraying from each step. A thin strip of distant shore glows faintly. Hard warm side light, a thin cut light in #F7CF73 along the legs and the sled handles, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de energía por el camino de grava junto al lago y el trineo cargado.

### Semana en movimiento · `generated:functional`

Archivo: cover_generated_functional.webp

The stone base of a lighthouse on a windy cliff, the sea far below in the dark and the lamp high above unlit. A single athlete seen from the side, face in shadow, rises through a Turkish get-up: a kettlebell held overhead on a straight arm while the body lifts from the ground, one elbow planted and the other leg swept under. Sea spray catches the light. Hard warm side light, a thin cut light in #F7CF73 along the arm and the kettlebell, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de energía por la base de un faro en el acantilado y la levantada turca.

### Dominio del cuerpo · `generated:calisthenics`

Archivo: cover_generated_calisthenics.webp

An old playground with a steel frame, parallel bars and a sand pit, the metal rusting into darkness at the edges. A single athlete seen from the side, face hidden, performs a parallel-bar dip: arms locked at the top, then lowering until the elbows bend at the bottom, shoulders below the bars and the body upright. Sand is scattered under the bars. Hard warm side light, a thin cut light in #C9B8FF along the arms and the bars, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de mente por el parque infantil y los fondos en paralelas.

### Mesa y antebrazo · `generated:armwrestling`

Archivo: cover_generated_armwrestling.webp

A shipyard storage loft with coiled ropes hanging from beams and a wooden bench under a single window's light. Shot from the side, the athlete's face out of frame, only the forearm and hand are lit. The forearm rests on the bench while the wrist rolls a weighted rope roller upward, fingers tight and the forearm muscles defined. Rope coils and dust hang in the air. Window light with hard warm side light, a thin cut light in #43D18C along the forearm and the roller, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de ok por el desván del astillero y el rodillo de muñeca con cuerda.

### Fuerza de evento · `generated:strongman`

Archivo: cover_generated_strongman.webp

A muddy farm field after rain, puddles reflecting the dark sky and furrows leading into a dim horizon. A single athlete seen from behind, face hidden, walks forward with a farmer's carry: two heavy kettlebells held at the sides, the torso braced, the grip locked and each step deliberate through the mud. Water splashes in the furrows. Hard warm side light, a thin cut light in #8FB2FF along the arms and the kettlebells, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de columna por el campo embarrado y el paseo con carga en ambas manos.

### Técnica y potencia · `generated:weightlifting-base`

Archivo: cover_generated_weightlifting_base.webp

A rail-yard maintenance shed with a steel gantry overhead and oil-stained concrete, the far end dissolving into dark. A single athlete seen from the side, face hidden, catches a snatch at the bottom of the lift: arms locked overhead, the bar stable above the head, knees bent in a low squat and the torso upright. Hard light from the shed's open door. Hard warm side light and a thin cut light in #8FB2FF along the arms and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de columna por el cobertizo del patio ferroviario y el arranque con brazos bloqueados.

### Semana de los tres básicos · `generated:powerlifting`

Archivo: cover_generated_powerlifting.webp

A living room with a rolled rug pushed aside and a folding bench against the wall, a single floor lamp casting a warm pool of light. A single athlete seen from behind, face hidden, stands in a folding squat rack, the barbell resting on the upper back at the top of a back squat, feet flat and hips locked out. Plain furniture fades into shadow. Warm lamp light mixed with hard warm side light, a thin cut light in #F49A6E along the shoulders and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el salón con alfombra enrollada y el rack plegable.

### Fuerza y estética · `generated:powerbuilding`

Archivo: cover_generated_powerbuilding.webp

A stadium tunnel with concrete walls and a row of bare bulbs overhead, the tunnel's far end falling into black. A single athlete seen from the side, face hidden, performs a bent-over barbell row with heavy plates: torso near forty-five degrees, the bar pulled to the lower ribs and the elbows driving back. Concrete dust drifts in the bulb light. Hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el túnel de estadio y el remo pesado con discos.

### Volumen esencial · `generated:bodybuilding`

Archivo: cover_generated_bodybuilding.webp

An empty ballroom with chandeliers dimmed to a faint glow, polished floorboards reflecting the light and the walls falling into darkness. A single athlete seen in side profile, face hidden, performs a preacher curl on a padded bench: upper arms locked against the incline, the forearm lowering under control until the biceps is fully stretched. The chandelier glow sits high above. Faint chandelier light mixed with hard warm side light, a thin cut light in #F49A6E along the arm and the dumbbell, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el salón de baile vacío y el curl predicador de volumen.

---
## Generador de rutinas: variantes en bloques

### Escalera de carga · `generated:strength-muscle:blocks`

Archivo: cover_generated_strength_muscle_blocks.webp

A fire station training bay with a concrete floor in deep shadow, a single hanging lamp and tool lockers falling into darkness at the edges. A single athlete seen from behind, face hidden, performs a Zercher squat: the barbell cradled in the crooks of the elbows, the torso upright and the knees driving out as the body sinks slowly. Lamp light catches the bar. Hard warm side light, a thin cut light in #F49A6E along the forearms and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la base de bomberos y la sentadilla con la barra en el codo.

### Motor y fuerza · `generated:hybrid:blocks`

Archivo: cover_generated_hybrid_blocks.webp

A boxing gym with heavy bags and wrapped tape hanging from a steel rail, the ring ropes fading into the dark. A single athlete seen in side profile, face hidden, drives a rowing machine stroke: legs extended, the handle pulled into the ribs, the chest tall and the breath controlled. The flywheel's blur sits in the dark. Hard warm side light, a thin cut light in #F7CF73 along the arms and the handle, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de energía por el gimnasio de boxeo y el remo de potencia.

### Fuerza para la vida · `generated:functional:blocks`

Archivo: cover_generated_functional_blocks.webp

A warehouse loading dock in morning light, a roll-up shutter half raised and a concrete floor scuffed by pallets. A single athlete seen from the side, face hidden, lifts a heavy crate from the floor with a flat back: hips hinging, legs driving, arms close to the body and the grip firm. The crate's edge catches the light. Hard warm side light, a thin cut light in #F7CF73 along the back and the crate's edge, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de energía por el muelle de carga y el levantamiento de una caja del suelo.

### Progresiones en bloques · `generated:calisthenics:blocks`

Archivo: cover_generated_calisthenics_blocks.webp

A wooden pergola in a quiet garden at dusk, vines hanging overhead and the garden beyond falling into shadow. A single athlete seen from behind, face hidden, starts at the top of a pull-up and lowers slowly in a negative: elbows tracking down, shoulders engaged and the legs crossed behind. Each inch of the descent is controlled. Dusk light mixed with hard warm side light, a thin cut light in #C9B8FF along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de mente por la pérgola del jardín y la dominada negativa.

### Bloques de mesa · `generated:armwrestling:blocks`

Archivo: cover_generated_armwrestling_blocks.webp

A tavern back room with a worn wooden table and an old lamp hanging low, the walls falling into shadow. Shot from the side, the athlete's face out of frame, only the arm, hand and table edge lit. The athlete pulls with a hook grip on a padded table, the forearm locked to the surface, wrist tense and fingers hooked tight over the edge. Lamp light glints off the worn wood. Hard warm side light, a thin cut light in #43D18C along the forearm and the hand, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de ok por la trastienda de taberna y el gancho de agarre sobre la mesa.

### Carga y acarreo · `generated:strongman:blocks`

Archivo: cover_generated_strongman_blocks.webp

A rocky quarry edge covered in loose gravel and scrub, the stone walls falling into deep shadow. A single athlete seen from the side, face hidden, carries a sandbag on one shoulder up a slope: body leaning into the climb, the free arm swinging, the boots crunching the gravel. Dust lifts from each step. Hard warm side light, a thin cut light in #8FB2FF along the shoulder and the sandbag edge, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de columna por la cantera de grava y el saco de arena cuesta arriba.

### Ondas olímpicas · `generated:weightlifting-base:blocks`

Archivo: cover_generated_weightlifting_base_blocks.webp

An abandoned airport terminal hall with broken windows and dust hanging in the light, the high ceiling falling into darkness. A single athlete seen in side profile, face hidden, catches the bottom of a jerk dip under a loaded barbell held overhead: knees bent, torso upright and the bar stable above the head as the body prepares to drive. Dust floats in the shafts of light. Hard warm side light, a thin cut light in #8FB2FF along the arms and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de columna por la terminal abandonada y la sacudida con barra sobre la cabeza.

### Ondas de fuerza · `generated:powerlifting:blocks`

Archivo: cover_generated_powerlifting_blocks.webp

A woodworking workshop with sawdust in the air and stacked timber behind, a single work lamp throwing hard light. A single athlete seen from the side, face hidden, presses a barbell to lockout on a flat bench: arms straight above the chest, the back arched into the bench and the shoulder blades pinned. Sawdust drifts through the light. Hard warm side light, a thin cut light in #F49A6E along the chest and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el taller de carpintería y la banca hasta el bloqueo.

### Pesado y bombeado · `generated:powerbuilding:blocks`

Archivo: cover_generated_powerbuilding_blocks.webp

A glass-roofed courtyard of a university with ferns and a long bench, the glass overhead reflecting only darkness and a few stars. A single athlete seen from the side, face hidden, performs a hammer curl with dumbbells: forearms neutral, the biceps tight and the arm held close to the side as the weight rises. The dumbbell gleams. Faint starlight mixed with hard warm side light, a thin cut light in #F49A6E along the upper arm and the dumbbell, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el patio acristalado y el curl martillo con mancuernas.

### Bloques de hipertrofia · `generated:bodybuilding:blocks`

Archivo: cover_generated_bodybuilding_blocks.webp

A theater backstage corridor with dressing-room mirrors, bare bulbs and a worn carpet, the corridor vanishing into shadow. A single athlete seen from the side, face hidden, presses dumbbells up on an incline bench: the chest flexed at the top, the dumbbells held above the upper chest and the forearms stacked. Mirror frames catch faint light. Hard warm side light from a bare bulb, a thin cut light in #F49A6E along the chest and the dumbbells, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el pasillo de camerinos y el press inclinado con mancuernas.

---
## Generador de rutinas: variantes periodizadas

### Temporada de fuerza y masa · `generated:strength-muscle:periodized`

Archivo: cover_generated_strength_muscle_periodized.webp

A village sports hall with wall bars and a varnished wooden floor, the high windows dim and the far corners in black. A single athlete seen from the side, face hidden, performs a heavy barbell Romanian deadlift: the bar sliding down the thighs, the hips pushed back and the back flat under the load. The varnished floor reflects a thin line of light. Hard warm side light, a thin cut light in #F49A6E along the hamstrings and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el pabellón de pueblo y el peso muerto rumano con barra pesada.

### Temporada híbrida · `generated:hybrid:periodized`

Archivo: cover_generated_hybrid_periodized.webp

A dunes trail with sea grass and sand under a dim dusk sky, the dune ridges falling into shadow. A single athlete seen in side profile, face hidden, works battle ropes in waves: low stance, arms whipping the heavy ropes in alternating strikes, the torso powerful and the rhythm steady. Sand kicks up around the feet. Hard warm side light, a thin cut light in #F7CF73 along the arms and the ropes, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de energía por las dunas y las cuerdas de batalla en ondas.

### Temporada funcional · `generated:functional:periodized`

Archivo: cover_generated_functional_periodized.webp

A meadow with wildflowers in early morning light and tall grass swaying at the edges, the field fading into dark. A single athlete seen from the side, face hidden, throws a medicine ball overhead and slams it toward the ground: full body extension, the heels lifting and the arms driving down through the ball. Grass blades catch the light. Early light mixed with hard warm side light, a thin cut light in #F7CF73 along the arms and the ball, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de energía por el prado de flores y el lanzamiento del balón medicinal contra el suelo.

### Ruta de habilidades · `generated:calisthenics:periodized`

Archivo: cover_generated_calisthenics_periodized.webp

A fountain plaza in a city square at dusk, the water jets glowing faintly in the dark and the stone steps falling into shadow. A single athlete seen from the side, face hidden, holds a handstand close to a plain stone wall: arms locked, shoulders stacked over the wrists, fingers spread and the body balanced without support. Spray drifts around the legs. Dusk light mixed with hard warm side light, a thin cut light in #C9B8FF along the legs and the arms, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de mente por la fuente de la plaza y el pino de manos contra la pared.

### Camino a la mesa · `generated:armwrestling:periodized`

Archivo: cover_generated_armwrestling_periodized.webp

An old gymnastics hall with a wooden vaulting horse at the back and tall windows that let in a thin shaft of light. A single athlete seen from behind, face hidden, hangs from a bar by a towel wrapped around the grip: fingers locked into the cloth, shoulders relaxed and the body still. The towel's folds catch the light. Thin window light with hard warm side light, a thin cut light in #43D18C along the forearms and the towel, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de ok por el gimnasio antiguo y el colgarse de una toalla.

### Temporada de strongman · `generated:strongman:periodized`

Archivo: cover_generated_strongman_periodized.webp

A stone courtyard of a fortress with iron rings set into the walls and a single torch throwing a warm flicker across the flagstones. A single athlete seen from the front-side, face hidden, presses a heavy log overhead: the log rising from the shoulders to full lockout, legs braced wide and the torso taut. The iron rings glint in the shadows. Torch flicker mixed with hard warm side light, a thin cut light in #8FB2FF along the arms and the log, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de columna por el patio de la fortaleza y el press de tronco sobre la cabeza.

### Ciclo de halterofilia · `generated:weightlifting-base:periodized`

Archivo: cover_generated_weightlifting_base_periodized.webp

A monastery courtyard with arched cloisters of worn stone and a single stone path leading into shadow. A single athlete seen from behind, face hidden, performs an overhead squat with a wide grip: the barbell held straight over the head, the arms locked and the athlete sinking into a deep squat with the torso upright. Rain-darkened stone reflects a faint light. Hard warm side light, a thin cut light in #8FB2FF along the shoulders and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de columna por el claustro monástico y la sentadilla por encima de la cabeza.

### Camino a la gloria · `generated:powerlifting:periodized`

Archivo: cover_generated_powerlifting_periodized.webp

A convention hall with a competition platform under dim spotlights, a judges' table blurred far behind in the dark. A single athlete seen from the side, face hidden, sits into a heavy back squat: bar on the upper back, a deep and controlled bottom position with the hips below the knees and the chest up. The judges' table is only a soft shape. Spotlight mixed with hard warm side light, a thin cut light in #F49A6E along the back and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por la plataforma de competición con jueces al fondo.

### Temporada powerbuilding · `generated:powerbuilding:periodized`

Archivo: cover_generated_powerbuilding_periodized.webp

A seaside bathhouse with a tiled pool and steam drifting through a shaft of light, the tiles falling into shadow. A single athlete seen from behind, face hidden, performs a paused front squat: the barbell resting on the front shoulders, elbows high and the athlete holding a tight pause at the bottom. Steam curls around the legs. Steam light mixed with hard warm side light, a thin cut light in #F49A6E along the shoulders and the bar, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el baño de mar cubierto y la sentadilla frontal con pausa.

### Temporada de culturismo · `generated:bodybuilding:periodized`

Archivo: cover_generated_bodybuilding_periodized.webp

A mountain resort lodge gym with timber walls and a cable station, snow glowing beyond the glass in the dark. A single athlete seen from behind, face hidden, performs a rope triceps pushdown: elbows pinned at the sides, arms extending fully and the rope spread at the bottom. The muscles of the arm stand out in the light. Snow glow mixed with hard warm side light, a thin cut light in #F49A6E along the upper arm and the rope, black background, fine film grain. Vertical 4:5 framing, subject inside the central 60 percent.

Por qué: Distinta de las demás de músculo por el refugio de montaña y la extensión de tríceps en cuerda.

---
## Cómo añadir una portada

La app leerá las portadas de `android-native/app/src/main/assets/program_covers/`. La carga, el recorte y el degradado inferior los implementa D3b; este documento solo fija el nombre, el formato y el tamaño.

- **Nombre:** `cover_<id_saneado>.webp`. El id va en minúsculas y los `:` y los `-` se cambian por `_`. Ejemplo: `native:strength-foundation-v2` → `cover_native_strength_foundation_v2.webp`. Cada archivo de esta lista trae su nombre en la línea `Archivo:`.
- **Formato:** WebP de 1600 × 2000 píxeles (4:5), como máximo 200 KB.
- **Sin archivo:** el programa sigue viendo el arte procedural de hoy. Añadir portadas por partes no rompe nada.
- **Antes de subir una imagen:** comprueba la guía (un solo atleta, sin texto ni logos, sujeto en el 60 % central) y que el acento sea el de su perfil.
