# Guía Editorial — Catálogo de Ejercicios v2

Documento vinculante para la curaduría del catálogo. Todas las decisiones
fueron aprobadas por el dueño del producto; no se re-debaten ni se "mejoran"
durante la ejecución. Ante una duda no cubierta: detenerse y preguntar, no
improvisar.

## Flujo de trabajo

1. Editar `curation/editorial_briefs.json` para cualquier cambio de copy. Debe
   existir un brief por definición y por configuración; no se acepta fallback
   por patrón, implemento o nombre.
2. `python scripts/curaduria_v6_catalogo_editorial.py` — aplica los briefs a
   `source/families/*.json` sin recalcular músculos ni articulaciones.
3. `python scripts/merge_catalog_v2_families.py` — reconstruye
   `source/catalog_v2.json` (fuente canónica única) con serialización idéntica.
4. `python scripts/compile_exercise_catalog_v2.py --check` — validación estructural.
5. `python scripts/catalog_v2_gate.py --strict` — gate editorial.
6. Tras aprobación: `python scripts/compile_exercise_catalog_v2.py --write` para
   regenerar el asset Android y copiarlo idéntico al runtime iOS.

Para cambios estructurales, `python scripts/split_catalog_v2_source.py` sigue
siendo la superficie de revisión de `source/families/`; no se debe usar para
reemplazar los briefs editoriales.

Nunca editar `source/catalog_v2.json` a mano: el merge lo reconstruye.

## R1 — Identidad del padre

La identidad la definen **patrón + postura/ángulo/dirección/posición de carga +
reparto muscular**. NUNCA el implemento.

Son padres separados (jamás chips entre sí):

- Convencional ≠ Sumo ≠ Rumano ≠ Piernas Rígidas (pesos muertos)
- Pull Over de pie ≠ Pull Over en Banca (en banca el pectoral pasa a principal)
- Curl de Isquiosurales Sentado ≠ Tumbado ≠ De Pie
- Press de Banca Plano ≠ Inclinado ≠ Declinado
- Aperturas Planas ≠ Inclinadas ≠ Declinadas
- Press Militar (de pie) ≠ Press de Hombros Sentado
- Elevaciones Laterales de Pie ≠ Sentado
- Zancada Frontal ≠ Inversa ≠ Caminando
- Sentadilla Trasera Barra Alta ≠ Barra Baja
- Curls de bíceps por setup (de pie, inclinado, predicador, araña, concentrado)

## R2 — Chip de primer nivel: implemento/estación

Cuando el implemento varía libremente, es el chip de primer nivel. Valores:

`barbell` (Barra/Barra Recta/Barra Libre), `ez_bar`, `dumbbells`,
`smith_machine` (Smith SIEMPRE separado), `machine` (Máquina genérica, nunca
sub-tipos), `cable` (Polea SIEMPRE opción propia, jamás dentro de Máquina),
`kettlebell`, `hex_bar`, `t_bar`, `band`, `bodyweight`, `sliders`.

- Si el nombre del ejercicio ya incluye el implemento, el chip solo cubre la
  estación de ese implemento (Sentadilla Trasera con Barra: Barra Libre / Smith).
- Excepción: cuando la máquina ES el ejercicio, se nombra (Sentadilla Belt
  Squat, Sentadilla en Máquina V Squat, Máquina Convergente en presses).

## R3 — Sub-chips

Solo si cambian ejecución o estímulo; nada de relleno.

| Eje | Valores → labels | Sí cuando |
| --- | --- | --- |
| `laterality` | bilateral/unilateral → Bilateral/Unilateral | implementos fijos (jalón, pull over, curls isquios) o apoyo a una pierna (pesos muertos, RDL) |
| `grip_type` (nuevo) | pronated/supinated/neutral → Prono/Supino/Neutro | dominadas (cambia énfasis dorsal/bíceps) |
| `grip_width` (nuevo) | wide/medium/close → Amplio/Medio/Cerrado | remos y dominadas (redistribuye énfasis) |
| `pulley_height` (nuevo, condicional) | high/mid/low → Alta/Media/Baja | SOLO en configs con `implement=cable` |

Ángulo de banco, postura y dirección NUNCA son chip: separan padres (R1).

## R4 — Especialidades

Lo que cambia patrón/rango/método sale del padre: déficit, Zercher (toda
variante, SIN chips), métodos nombrados (Spoto, cadenas, Somersault, Jefferson,
Super ROM), isometrías. Excepción: Pendlay es padre propio (remo básico libre).

## R5 — Sin chips

Identidades únicas se toman tal cual: crunches, curls de isquios con
sliders/balón, Curl Nórdico, zerchers, planchas, Dragon Flag, Frog Pumps.

## R6 — Nomenclatura

- Title Case español: "Peso Muerto Rumano", nunca "Peso muerto rumano".
- Nombre de EJERCICIO, no de patrón: "Aducciones de Pierna", nunca "Aducción
  de cadera".
- "Curl de Isquiosurales", NUNCA "femoral" en nombres visibles.
- Inglés cuando es el nombre conocido: Hip Thrust, Belt Squat, Pull Over, Press.
- Sin paréntesis de doble nombre, sin taxonomías crudas en canonicalName.
- `searchTerms` conserva términos legacy ("curl femoral", "remo", etc.).

## R7 — Involucramiento muscular (única verdad)

- Cada configuración declara `primaryMuscles` (≥1) / `secondaryMuscles` /
  `stabilizerMuscles` con los 20 IDs de la ontología.
- Equivalencias FIJAS por rol: Principal 1.0 / Secundario 0.5 / Estabilizador
  0.4. NO se guardan números en el JSON; UI y contadores derivan del rol.
- Principal = motor del patrón (RDL: hamstrings, gluteus_maximus).
- Secundario = asiste con contribución real (remo: biceps).
- Estabilizador = isométrico/postural (RDL: erector_spinae, core → 0.4).
- Un músculo no puede estar en dos listas de la misma config.
- Chips que redistribuyen énfasis cambian las listas por config.
- NEUTRALIZER no existe en el catálogo.
- `muscleNotes` (obligatorio): array de `{"muscleId", "note"}` con exactamente
  una nota ≥40 chars por músculo listado (sin huérfanos ni faltantes). Explica
  la función de ESE músculo en ESE ejercicio y justifica su rol.
  Ejemplo RDL erectores: "Estabilizador: trabaja isométricamente para mantener
  la columna neutra durante toda la bisagra; por eso suma 0.4 y no una serie
  completa."

## R8 — Descripciones

- Experto, breve, conciso, amigable; ni técnico en exceso ni coloquial.
- Padre: 2-4 frases (qué es, qué entrena, cuándo elegirlo).
- Cada configuración tiene un microbrief propio: no se construye cambiando
  solamente el nombre del implemento.
- La primera frase de cada descripción debe distinguir el ejercicio o la
  configuración; el gate bloquea aperturas repetidas.
- Todas distintas entre sí y sincronizadas con `editorial_briefs.json`.
- ≥40 chars.
- Sin verbos instruccionales: ejecuta, mantén, configuras, adopta, controla,
  asegura, evita, sigue, selecciona (las pautas van en setupCues/executionCues).
- Sin plantillas ("X trabaja principalmente Y mediante un patrón de Z").
- Sin sufijos técnicos ("La variante se define por: ...").
- Sin dobles nombres.

## R9 — Involucramiento articular (única verdad)

- Cada configuración declara `jointInvolvement` con una entrada por
  articulación realmente implicada: `jointId`, `role`, `actions` y `note`.
- `jointId` usa la ontología canónica de WikiLab; no se crean nombres visibles
  alternativos ni se mezclan articulaciones con músculos o tendones.
- Los roles son `PRIMARY`, `SECONDARY` y `STABILIZER`. Principal = articulación
  que produce la acción dominante; secundaria = acompaña y comparte la
  transferencia de fuerza; estabilizadora = conserva la posición o transmite
  la carga sin ser el motor principal.
- Cada nota tiene ≥40 caracteres y explica qué movimiento, transmisión o
  estabilidad aporta ESA articulación en ESA configuración, incluyendo el
  efecto de agarre, implemento, apoyo, lateralidad o altura de polea cuando
  corresponda.
- `richMetadata.anatomy.jointInvolvement` y
  `richMetadata.biomechanics.relevantJoints` deben replicar exactamente la
  ficha de perfil. No se aceptan articulaciones huérfanas, duplicadas o
  genéricas.

## R10 — Ficha editorial de cada configuración

- `benefits` contiene ≥2 beneficios concretos; `techniqueSummary` resume la
  técnica de esa opción y `variantRationale` explica cuándo la elección cambia
  la demanda o el beneficio.
- Los cuatro campos visibles se escriben en el brief de la configuración y se
  copian sin transformación genérica al perfil y a `richMetadata.editorial`.
- La descripción visible, los beneficios, la técnica, `muscleNotes` y
  `jointInvolvement` deben describir la misma configuración exacta. Cambiar
  solo el nombre del implemento no constituye curaduría.

## R11 — Lectura y presentación para la persona que entrena

- El nombre canónico aparece una sola vez en la tarjeta: en el título. Ninguna
  descripción de definición o configuración lo repite, aunque coincida con el
  nombre de un implemento o de un músculo.
- La primera capa usa frases claras, directas y específicas para el ejercicio;
  la anatomía profunda puede conservar precisión, pero no debe depender de
  palabras como torque, vector, palanca o centro de masa para explicar el
  beneficio.
- Cada párrafo y cada viñeta comienza con mayúscula. Los nombres compactos de
  implementos, agarres, acciones y secciones usan una capitalización uniforme.
- En la tarjeta expandida, las opciones de implemento, estación, agarre,
  amplitud y lateralidad aparecen primero y permanecen visibles. Después se
  muestran, cerradas por defecto, las secciones plegables Descripción,
  Técnica, Involucramiento Muscular e Involucramiento Articular.
- El orden editorial es estable: opciones → descripción → técnica → músculos
  → articulaciones. Abrir una sección no abre las demás.

## L1-L12 — Reglas extraídas de la curaduría v3 y v5

Reglas derivadas de las decisiones del dueño del producto durante la revisión
v3. Son vinculantes para la siguiente pasada editorial.

1. **L1 — Title Case siempre**: mayúscula en cada palabra salvo conectores
   ("de", "en", "con", "y", "a"). Términos ingleses capitalizados: JM Press,
   Kelso Shrugs, Curl Drag, Floor Press, Dragon Flag, Flexiones Esfinge,
   Sentadilla "Belt Squat", "V-Squat".
2. **L2 — Nombres sin relleno**: lo simple manda. "Curl Araña", no "Curl de
   Bíceps Araña"; "Dragon Flag", no "Dragon Flag en Banco Plano"; "Crunch
   Abdominal en Banco Declinado", no "...Lastrado con Disco".
3. **L3 — Un ejercicio, una identidad**: eliminar duplicados funcionales
   (Curl Inclinado ≈ Bayesian; Press de Hombros de Pie ≈ Press Militar;
   Super ROM duplicada; Plancha Copenhagen Isométrica ≈ base; hiperextensiones
   redundantes).
4. **L4 — Ejes solo si cambian estímulo**: fuera Estación en Aperturas
   Inversas, fuera carga en Glute Ham Raise, fuera posturas arbitrarias en
   Elevaciones Posteriores; la altura de polea SÍ es eje cuando cambia el
   enfoque (Cruce de Poleas, Extensión de Tríceps).
5. **L5 — Implementos populares completos y default = el más popular**:
   completar set (Barra de Seguridad en Buenos Días y sentadillas traseras,
   Kettlebell en elevaciones, Barra recta en JM Press) y fijar el default en la
   variante más usada (Máquina Pec Deck + Bilateral, Polea Alta + Bilateral,
   Hack en Máquina, Mancuernas + Supino en Curl Araña/Bayesian).
6. **L6 — Involucramiento honesto**: sin músculos de agarre en máquinas de
   piernas (antebrazo fuera de Extensión de Cuádriceps), sin core con soporte
   de banco (aperturas), glúteo medio ≠ glúteo mayor (abducciones: el medio
   abduce, el mayor extiende). Cabeza de glúteo (gluteus_medius) se agrupa con
   "Glúteos" en el cálculo de volumen.
7. **L7 — Comillas para nombres propios de máquina**: "Belt Squat", "V-Squat".
8. **L8 — Perfiles adaptativos por chip**: si el agarre/altura/amplitud cambia
   el estímulo, las listas musculares y la descripción lo reflejan por config
   (dominadas: cerrado → dorsal, abierto → trapecio/espalda alta, supino →
   bíceps).
9. **L9 — Las variantes del mismo patrón heredan el set de implementos del
   padre**: Peso Muerto Piernas Rígidas y Peso Muerto Rumano usan las mismas
   opciones que el Peso Muerto Convencional (barra, Smith, mancuernas, hex); el
   Peso Muerto Rumano Sumo usa las del Rumano. No se inventan implementos por
   variante ni se quitan opciones que el patrón base ya ofrece.
10. **L10 — Descripciones para el usuario, no para biomecánicos**: texto
    cercano, con carácter y que invite a probar el ejercicio. Prohibido el
    argot técnico (bisagra, patrón, cadena cinética, ROM, excéntrica), las
    plantillas ("X trabaja principalmente Y mediante un patrón de Z") y los
    verbos instruccionales (ejecuta, mantén, configura, adopta, controla,
    asegura, evita, sigue, selecciona). Cada configuración menciona el matiz
    real de sus chips.
11. **L11 — El involucramiento cambia de verdad con los chips**: si el agarre,
    la altura o la postura alteran el estímulo, las listas musculares, las
    notas y la descripción deben reflejarlo por configuración. Ejemplos reales
    del catálogo: remos con agarre amplio → trapecio y espalda alta; agarre
    cerrado → dorsal y bíceps; dominada supina → bíceps protagonista;
    pronada/neutra → bíceps solo estabilizador.
12. **L12 — La ficha articular cambia con la variante**: el implemento, el
    agarre, la altura de polea, la lateralidad o el apoyo deben modificar la
    explicación articular cuando cambian la trayectoria, la estabilidad o la
    transferencia de fuerza; no se copia una lista articular indiferenciada.

## Ontología de músculos (21 IDs)

`abdominals, adductors, biceps, calves, core, deltoid, erector_spinae, forearm,
gluteus_maximus, gluteus_medius, hamstrings, hip_flexors, latissimus_dorsi,
neck, pectoralis, quadriceps, rhomboids, tensor_fasciae_latae,
tibialis_anterior, trapezius, triceps`

Notas:
- `gluteus_medius` ("Glúteo Medio") se agrupa con `gluteus_maximus` en el
  cálculo de volumen (misma normalización que las cabezas del deltoides).
- `safety_bar` ("Barra de Seguridad") es un implemento válido del eje
  `implement` (Buenos Días, Buenos Días Sentado, sentadillas traseras).
- `h_bar` ("Barra H") es un implemento válido del eje `implement` para los
  curls con agarre neutro (Curl Martillo, Curl Invertido).

## Prohibiciones operativas

1. No fusionar patrones/posturas/ángulos distintos.
2. No ejes de relleno.
3. No meter Polea en "Máquina", ni Smith en "Máquina", ni sub-tipos dentro de
   "Máquina".
4. No "femoral", minúsculas, paréntesis dobles ni taxonomías crudas.
5. No guardar equivalencias numéricas en el JSON ni fuentes paralelas.
6. No NEUTRALIZER en el catálogo.
7. No tocar iOS (salvo copia de datos), `.env`, keystores, telegramBot.js.
8. No descripciones idénticas ni instruccionales.
9. No commits sin permiso explícito.
10. No regenerar assets a mano: solo scripts documentados.
11. No chips en zerchers, crunches, curls sliders/balón/nórdico.
