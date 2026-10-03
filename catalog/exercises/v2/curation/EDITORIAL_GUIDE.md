# Guía Editorial — Catálogo de Ejercicios v2

Documento vinculante para la curaduría del catálogo. Todas las decisiones
fueron aprobadas por el dueño del producto; no se re-debaten ni se "mejoran"
durante la ejecución. Ante una duda no cubierta: detenerse y preguntar, no
improvisar.

## Flujo de trabajo

La **ficha** (`curation/fichas/<familyId>.json`) es la única superficie de
autoría: copy público, anatomía y, en las definiciones `CURATED`, la técnica
interna y el brief visual. Nada se escribe a mano en `source/families/*.json`.

1. Editar la ficha de la familia. Cada definición está `LEGACY` (solo `public`,
   copy heredado) o `CURATED` (ficha completa). No se acepta fallback por
   patrón, implemento o nombre.
2. `python scripts/catalog_v2_apply_fichas.py --only-definitions=<id>[,<id>]` —
   copia la ficha a `source/families/*.json`. Solo copia: nunca redacta ni
   adivina. Valida todo antes de escribir (si algo falla no se escribe nada);
   sin `--only-definitions` aplica todas las familias; `--check` verifica sin
   escribir.
3. `python scripts/merge_catalog_v2_families.py` — reconstruye
   `source/catalog_v2.json` (fuente canónica única) con serialización idéntica.
4. `python scripts/compile_exercise_catalog_v2.py --check` — validación estructural.
5. `python scripts/catalog_v2_gate.py --strict` — gate editorial; exige además
   que la fuente coincida byte a byte con lo que producen las fichas.
6. `python scripts/catalog_v2_quality_audit.py --strict --definitions=<id>[,<id>]`
   — auditoría de calidad de lo curado.
7. Tras aprobación: `python scripts/compile_exercise_catalog_v2.py --write` para
   regenerar el asset Android y copiarlo idéntico al runtime iOS.

Para cambios estructurales, `python scripts/split_catalog_v2_source.py` sigue
siendo la superficie de revisión de `source/families/`; no se debe usar para
reemplazar las fichas.

Nunca editar `source/catalog_v2.json` ni `source/families/*.json` a mano: el
aplicador y el merge los reconstruyen, y el gate falla si divergen de las fichas.

Los generadores por plantilla de las curadurías v3–v6 viven en
`scripts/legacy/` y están retirados: piden una confirmación explícita para
correr porque reciclan frases y sobrescribirían fichas curadas.

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
  `stabilizerMuscles` con los 21 IDs de la ontología.
- Equivalencias FIJAS por rol: Principal 1.0 / Secundario 0.5 / Estabilizador
  0.4. NO se guardan números en el JSON; UI y contadores derivan del rol.
- Principal = motor del patrón (RDL: hamstrings, gluteus_maximus).
- Secundario = asiste con contribución real (remo: biceps).
- Estabilizador = isométrico/postural (RDL: erector_spinae, core → 0.4).
- El rol lo decide la evidencia, no la costumbre. Donde una ficha corrige el
  criterio anterior, la regla vive en `curation/anatomy_rules.json` con su
  evidencia y el gate la exige. Criterios del piloto **confirmados por el
  usuario (2026-10-02)**: el glúteo medio es el principal en abducción y
  rotación externa de cadera (`hip.abductors-gluteus-medius`; el mayor asiste),
  el antebrazo es al menos estabilizador cuando el agarre sostiene el peso del
  cuerpo (`grip.hanging-forearm`) y, con el torso apoyado, los erectores no son
  motor (`rows.chest-supported-spine`).
- Un músculo no puede estar en dos listas de la misma config.
- Chips que redistribuyen énfasis cambian las listas por config.
- NEUTRALIZER no existe en el catálogo.
- El porqué de cada rol vive en la ficha (`anatomy`, campo `why` de cada
  músculo), no en el catálogo runtime: explica la función de ESE músculo en ESE
  ejercicio y justifica su rol. Ejemplo RDL erectores: "Estabilizador: trabaja
  isométricamente para mantener la columna neutra durante toda la bisagra; por
  eso suma 0.4 y no una serie completa."
- Las listas por rol y todos sus espejos en `richMetadata` los escribe el
  aplicador desde la ficha con `scripts/catalog_v2_derived.py`; nunca se editan
  a mano.

## R8 — Descripciones (estructura v7.2, aprobada por el dueño del producto)

- Experto, breve, conciso, amigable; ni técnico en exceso ni coloquial.
- **Definición — estructura de 3 bloques**:
  1. **Introducción**: nombre del ejercicio (+ alias en inglés si es el nombre
     conocido) + tipo de movimiento + qué trabaja + marco editorial (rango,
     tensión, qué lo hace especial). NUNCA abrir con la mecánica.
  2. **Ejecución editorial**: cómo se hace, contado de forma cercana y
     sensorial; el implemento principal aparece aquí de forma natural.
  3. **Alternativas (opcional, máx. 1-2)**: solo si aportan algo distintivo a
     ESE ejercicio, introducidas con una frase puente y con su efecto real
     (Smith = estabilidad/fallo seguro; mancuernas = trabajo unilateral, etc.).
     No se enumeran todos los implementos disponibles: la lista completa se
     siente robótica.
  4. **Veredicto**: valoración dedicada y específica del ejercicio.
- Cada configuración tiene una línea de matiz propia: esencia del movimiento
  del ejercicio + el efecto real de sus chips (implemento/agarre/lateralidad).
- La primera frase de cada descripción debe distinguir el ejercicio o la
  configuración; el gate bloquea aperturas repetidas.
- Todas distintas entre sí. La ficha (`public`) es su única fuente y el gate
  exige que la fuente coincida con ella.
- ≥40 chars.
- Sin verbos instruccionales en imperativo: mantén, configura, adopta,
  controla, asegura, evita, sigue, selecciona. Formas descriptivas/reflexivas
  como "se ejecuta" o "se practica" son válidas (las pautas directas van en
  setupCues/executionCues).
- Sin plantillas ("X trabaja principalmente Y mediante un patrón de Z").
- Sin sufijos técnicos ("La variante se define por: ...").
- Sin dobles nombres.

## R9 — Involucramiento articular (única verdad)

- Cada configuración declara `jointInvolvement` con una entrada por
  articulación realmente implicada: `jointId`, `role` y `actions`.
- `jointId` usa la ontología canónica de WikiLab; no se crean nombres visibles
  alternativos ni se mezclan articulaciones con músculos o tendones.
- Los roles son `PRIMARY`, `SECONDARY` y `STABILIZER`. Principal = articulación
  que produce la acción dominante; secundaria = acompaña y comparte la
  transferencia de fuerza; estabilizadora = conserva la posición o transmite
  la carga sin ser el motor principal.
- El porqué de cada articulación vive en la ficha (`anatomy`, campo `why` de
  cada articulación): explica qué movimiento, transmisión o estabilidad aporta
  ESA articulación en ESA configuración, incluyendo el efecto de agarre,
  implemento, apoyo, lateralidad o altura de polea cuando corresponda.
- `richMetadata.anatomy.jointInvolvement`, `richMetadata.anatomy.jointActions` y
  `richMetadata.biomechanics.relevantJoints` se derivan de la ficha con las
  mismas funciones que usan el compilador, el gate y la app; no se editan a
  mano. No se aceptan articulaciones huérfanas, duplicadas o genéricas.

## R10 — La ficha como fuente única de autoría

- Un archivo por familia: `curation/fichas/<familyId>.json`, con
  `{schemaVersion, familyId, definitions: {<definitionId>: {status, public, ...}}}`.
- `status: LEGACY` conserva el copy heredado (solo `public`). `status: CURATED`
  exige además `technique`, `anatomy`, `visual` y `sources`, y es lo único que
  puede sobrescribir anatomía y patrón de movimiento.
- `public` es lo que ve la persona: la descripción de la definición y, por
  configuración, `description`, `setupCues` y `executionCues`. Se copia sin
  transformación genérica al perfil.
- `anatomy` declara músculos y articulaciones por definición, con `overrides`
  por configuración (`role: NONE` quita una entrada). Todo lo que es función
  pura de la anatomía se deriva; nada de eso se escribe a mano.
- `technique` y `visual` son internos (no viajan a la app). `visual` es el brief
  exacto con el que se generan las imágenes de demostración.
- La descripción visible, las señales y la anatomía deben describir la misma
  configuración exacta. Cambiar solo el nombre del implemento no constituye
  curaduría.
- Los campos que antes se escribían a mano y ningún código de producción leía
  (`benefits`, `techniqueSummary`, `variantRationale`, `commonMistakes`,
  `muscleNotes`, la nota de cada articulación, `richMetadata.editorial`,
  `coaching` y `safety`, entre otros) están retirados del esquema.
  `scripts/catalog_v2_retired_fields.py` es su lista única: el compilador, el
  gate, el backend y las pruebas de Android los rechazan.
- El formato interno de `technique`, `anatomy` y `visual` quedó **congelado** con
  el piloto (Remo Seal, Dominadas y Clamshells, 2026-10-01). Lo define
  `curation/AUTHORING_FICHA.md`, que es el manual de autoría: cualquier campo
  nuevo pasa por ahí y por el gate antes de usarse. El gate rechaza una ficha
  CURATED incompleta (`ficha_shape`, `visual_incomplete`), una fuente sin prueba
  offline (`source_unverified`) y el texto que recicla frases o plantillas
  (`shared_sentence`, `template_skeleton`, `banned_phrase`).

## R11 — Lectura y presentación para la persona que entrena

- **v7.2**: la descripción de DEFINICIÓN abre con el nombre del ejercicio a
  propósito (introducción editorial, con alias en inglés cuando es el nombre
  conocido). Las descripciones de CONFIGURACIÓN siguen sin repetir el nombre
  canónico: son líneas de matiz de variante bajo la descripción principal.
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
    cercano, con carácter y que invite a probar el ejercicio. **v7.2**: los
    términos técnicos precisos del entrenamiento (bisagra, palanca, rango de
    movimiento) sí son válidos cuando son la palabra editorial correcta;
    sigue prohibido el argot hueco, las plantillas ("X trabaja principalmente
    Y mediante un patrón de Z") y los verbos instruccionales en imperativo
    (mantén, configura, adopta, controla, asegura, evita, sigue, selecciona).
    Cada configuración menciona el matiz real de sus chips.
    **Anti-reciclaje (piloto):** una configuración no puede repetir la frase de
    una hermana cambiando solo el implemento, el agarre o un número
    (`template_skeleton_in_definition`), ni una definición puede reutilizar una
    frase o un fragmento denso de otra (`shared_sentence`, `shared_ngram`). Si
    dos variantes se describen igual, falta el dato que las distingue.
11. **L11 — El involucramiento cambia de verdad con los chips**: si el agarre,
    la altura o la postura alteran el estímulo, las listas musculares y la
    descripción deben reflejarlo por configuración. Ejemplos: remos con agarre
    amplio → trapecio y espalda alta; agarre cerrado → dorsal y bíceps.
    **Corrección (piloto, pendiente de confirmación del usuario):** en la
    dominada el bíceps flexiona el codo contra carga con cualquier agarre, así
    que su suelo es secundario. No es solo estabilizador y tampoco pasa a
    principal: el dorsal sigue por encima (117-130 % frente a 78-96 % en el
    conjunto de las condiciones; Youdas et al. 2010). El supino lo activa más
    que el prono, sin igualarlo. Dickie et al. 2017 vieron el complejo
    hombro-brazo parecido entre prono, supino y neutro. Lo fija la regla
    `vertical-pull.elbow-flexors` de `curation/anatomy_rules.json`.
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
7. No tocar iOS salvo la copia de datos y el contrato del esquema que decidió
   el dueño del producto (retiro de campos sin consumidor), `.env`, keystores,
   telegramBot.js.
8. No descripciones idénticas ni instruccionales.
9. No commits sin permiso explícito.
10. No regenerar assets a mano: solo scripts documentados.
11. No chips en zerchers, crunches, curls sliders/balón/nórdico.
12. No ejecutar los generadores por plantilla de `scripts/legacy/`: reciclan
    frases y sobrescribirían fichas curadas.
