# Aprobación del usuario — movimientos de peso corporal y notas de las dos familias

Fecha de registro: 2026-09-26. Tarea: `T-041`. Revisión del catálogo:
`v2-approved-2026-08-12-a` (se mantiene sin cambios).

Este documento registra lo que realmente se aprobó y de dónde sale cada piece de
copy. No es una revisión técnica ni científica del texto, y no debe leerse como
tal.

## 1. Qué aprobó la persona usuaria

Aprobó explícitamente la incorporación de dos movimientos de peso corporal y,
después, amplió el encargo a corregir los errores del catálogo:

- **Flexiones con Rodillas Apoyadas** (modified push-up), empuje horizontal en
  el suelo con el apoyo de las rodillas.
- **Sentadilla Sin Carga** (bodyweight squat), sentadilla bilateral sin carga
  externa.

La aprobación es **de alcance**: qué movimientos entran y en qué familias. No
es una revisión humana ni científica del copy, ni una aprobación de las
puntuaciones de dificultad técnica.

## 2. Procedencia de los dos registros nuevos

| Registro | Familia | Origen del copy | Referencias de evidencia |
|---|---|---|---|
| `knee_push_up` / `knee_push_up__default` | `upper_horizontal_push` | `.opencode/orchestration/wizard-feedback-resume-20260926-ses_f2efb28a7ffe/T-037-r1/proposal.json` (ronda r1, sin aplicar hasta esta tarea) | `proposal:…/T-037-r1/proposal.json#knee_push_up` y `reference:nasm.org/resource-center/exercise-library/modified-push-up` |
| `quads_sentadilla_sin_carga` / `quads_sentadilla_sin_carga__default` | `lower_knee_dominant` | el mismo artefacto de propuesta | `proposal:…/T-037-r1/proposal.json#quads_sentadilla_sin_carga` y `reference:pmc.ncbi.nlm.nih.gov/articles/PMC10987311` |

Los dos registros son **modelos editoriales asistidos por máquina**, no
mediciones. Ninguno ha sido medido con dinamómetro, EMG ni ningún laboratorio, y
no se afirma ninguna cifra de activación ni de momento articular en ninguno de
los dos.

La referencia NASM del modified push-up y la revisión de Straub & Powers
(2024, PMC10987311) sobre la sentadilla se descargaron y leyeron como página real
en la ronda T-037 r1. Se usaron para existencia, ejecución, reparto muscular
general y límites de profundidad. **No** se usaron para ninguna puntuación
numérica.

### Puntuaciones modeladas, no medidas

`technicalDifficulty = 4.0` en los dos registros es una **puntuación editorial
modelada** por decisión del orquestador, colocada entre anclas aprobadas y sin
cambiar. No es una medición, no está verificada por ningún ensayo y no se cita
estudio ni muestra alguna. Los campos `efc`, `cnc`, `ssc` y `ttc` se heredan sin cambio del
perfil aprobado más cercano porque su significado no está documentado en este
repositorio; ver `fieldSemanticsGap` en la propuesta.

`axialLoadFactor = 0.0` es una elección de modelo: cero ponderación de carga
axial **externa**, igual que en cualquier otro registro de peso corporal del
catálogo. No afirma que la columna no cargue peso.

### Atribución editorial

Los dos registros **no** llevan la referencia
`editorial:catalog-v7.2-human-editorial-2026-08-10`. Ninguno de los dos
movimientos recibió esa revisión humana histórica y no existe fila legacy para
ninguno en `candidate_inventory.json`. El campo `evidence.rationale` de los dos
dice, en texto, que `APPROVED` es el estado propuesto para el corte, que la
aprobación del usuario es de alcance y que el copy es una propuesta editorial
asistida por máquina.

## 3. Corrección de notas de las dos familias

Además de añadir los dos movimientos, se corrigieron los errores de
involucramiento de las 40 configuraciones ya existentes de estas dos familias
(`upper_horizontal_push`: 13; `lower_knee_dominant`: 27). Antes de esta tarea
esas 40 configuraciones producían 240 fallos del gate estricto: 160
`short_joint_note`, 40 `missing_muscle_note` y 40 `muscle_notes_mismatch`.

Qué se escribió, y sólo esto:

- `note` en cada entrada `jointInvolvement` ya declarada, con el movimiento, la
  transmisión de fuerza o la estabilidad que aporta **esa** articulación en
  **esa** configuración, incluyendo el efecto del apoyo, el implemento, la
  altura de polea o la lateralidad cuando existe (R9, L12).
- `muscleNotes` con exactamente una nota por músculo ya listado, en el orden
  `primaryMuscles` → `secondaryMuscles` → `stabilizerMuscles`, justificando el
  rol declarado (R7).
- Espejo exacto de `jointInvolvement` en `richMetadata.anatomy.jointInvolvement`
  (R9).

Qué **no** se cambió en ninguna de las 40 configuraciones: identificadores,
`kind`, `optionAxes`, `selectedOptions`, roles_primary/secundario/estabilizador,
`actions` articulares, `equipmentId`, `loadMode`, `primaryMuscles`,
`secondaryMuscles`, `stabilizerMuscles`, `technicalDifficulty`, `efc`, `cnc`,
`ssc`, `ttc`, `axialLoadFactor`, `replacementGroup`, `replacementPriority`, ni
ninguno de los seis campos editoriales de copy
(`description`, `benefits`, `techniqueSummary`, `variantRationale`,
`setupCues`, `executionCues`).

> **Parcialmente suplantado — ver §5.** Esa frase era exacta para la pasada
> original de este documento (sólo notas) y dejó de serlo en las rondas `r4` y
> `r5`, donde se corrigieron campos de copy y un clasificador que ya existían.
> El texto anterior se conserva como registro histórico de la pasada original;
> lo vigente está en §5.

No se añadió ningún campo numérico de equivalencia muscular al JSON (R7): las
equivalencias las derivan la UI y los contadores a partir del rol. No se
inventó ninguna cifra de torque, EMG o activación. No se añadió ningún apoyo,
implemento ni junta que la configuración no declare ya.

## 4. Pipeline y estado

Los dos registros nuevos se aplicaron con el comando ya existente del pipeline
editorial, en modo acotado:

```
python scripts/curaduria_v6_catalogo_editorial.py --only-definitions knee_push_up,quads_sentadilla_sin_carga
python scripts/merge_catalog_v2_families.py
```

El modo acotado no reescribe la revisión, no reintroduce la atribución humana
histórica y no toca las otras 94 familias. La transformación completa se validó
primero sobre una copia temporal de las 96 familias antes de escribir nada en el
árbol real.

Estado tras esta tarea: definiciones 199, configuraciones 512, familias 96,
`compile_exercise_catalog_v2.py --check` en verde. El gate estricto **sigue en
rojo** por la deuda de las otras familias, y no se ha perdonado ni una sola
regla. Los assets de runtime (Android, recursos e iOS) siguen sin regenerar y
conservan su hash `76966a5369cef9efc8d094f35efb13ca9bb7f82987ec87d20bf4971384ddf1cc`,
porque la regeneración de assets corresponde a la integración final, cuando el
gate global esté en verde.

## 5. Suplantación versionada de la sección 3 (rondas `r4` y `r5`)

La frase de §3 «ni ninguno de los seis campos editoriales de copy» era exacta para
la pasada original de este documento (sólo notas). Dos rondas posteriores la
modificaron, cada una con autorización explícita del encargo, y sin borrar este
historial:

| Ronda | Configuración | Campos de copy y metadato ya existentes que sí cambiaron | Motivo |
|---|---|---|---|
| `r4` | `tren_superior_press_banda_resistencia__default` | `techniqueSummary`, `variantRationale`, `setupCues`, `executionCues`, sus espejos exactos en `richMetadata` (`editorial.technique`, `editorial.variantRationale`, `coaching.cues`, `coaching.execution`, `coaching.setup`) y las mismas claves en `editorial_briefs.json` | La configuración es band-only declarada (`equipmentId=band`, `requiredEquipment=["band"]`, descripción de definición «sin banco ni pesas») y ese copy describía bandas atadas a una barra. Se alineó al implemento que ya declaraba, sin cambiar el implemento. |
| `r5` | `quads_sentadilla_hack__barbell` | `description`, `benefits[0]`, sus espejos exactos en `richMetadata.editorial`, y `richMetadata.biomechanics.stability` (`guided` → `self_stabilized`) | Una barra libre no tiene respaldo ni guía, pero el copy afirmaba «con la espalda apoyada», «el recorrido guiado» y «la carga sube al máximo». `stability` pasa a `self_stabilized`, valor ya presente en 431 de las 512 configuraciones del catálogo. |
| `r5` | `quads_sentadilla_hack__smith_machine` | `description` y su espejo exacto | Un soporte Smith guía la barra pero no tiene respaldo; el copy afirmaba «con la espalda apoyada». Ahora dice que la guía fija la trayectoria de la barra sin sostener el torso. |
| `r5` | `quads_sentadilla_hack__smith_machine` | `muscleNotes[quadriceps].note`, `jointInvolvement[tobillo].note` y su espejo exacto en `richMetadata.anatomy` | Se retiraron dos afirmaciones sin respaldo: «carga intermedia entre la barra libre y la máquina hack» (las tres configuraciones declaran idénticos `efc`, `cnc`, `ssc`, `ttc`, `axialLoadFactor` y `technicalDifficulty`, así que las puntuaciones no codifican esa jerarquía) y «el recorrido guiado de la Smith definiendo el ritmo de la flexión» (la guía limita la trayectoria de la barra, no marca el tempo). |

Ninguna de esas rondas cambió identificadores, `kind`, `optionAxes`,
`selectedOptions`, roles ni `actions` articulares, MuscleLists, puntuaciones
numéricas, `equipmentId`, `loadMode`, programación, `replacement`, `identity`,
`display`, `safety`, `fatigue` ni la descripción a nivel de definición. La
configuración hermana `quads_sentadilla_hack__machine` —que sí tiene respaldo y
recorrido guiado reales— quedó byte a byte intacta. El cambio de `stability` en la
barra libre es la única excepción de metadato autorizad en `r5`.

`notes` y `fatigue` siguen siendo copy editorial, no datos empíricos: no se añadió
ni se reformuló ninguna cifra de carga, activación, EMG o equivalencia muscular.
Lo que se corrigió fue texto que afirmaba un implemento, un apoyo o una jerarquía
de carga que esa configuración no declara.

Este documento no declara cierre de tarea. El estado del gate global y de los
assets de runtime sigue siendo el de §4: gate estricto en rojo por deuda ajena a
estas dos familias y assets sin regenerar.

Referencia cruzada: la corrección de copy de banda (`r4`) se résumé también en un
bloque aditivo de 8 líneas en `STATUS.md`, con el historial previo intacto.
