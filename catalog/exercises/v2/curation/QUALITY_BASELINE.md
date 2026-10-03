# Línea base de calidad editorial del catálogo v2

Generado por `scripts/catalog_v2_quality_audit.py` (solo lectura) sobre la revisión `v2-approved-2026-09-29-a`.
Las cifras se vuelven a medir en cada ejecución; este documento solo congela una medición. Regenerar: `python scripts/catalog_v2_quality_audit.py --markdown catalog/exercises/v2/curation/QUALITY_BASELINE.md`.

## Cobertura medida

- Definiciones auditadas: 201 en 96 familias (corpus: 201; legacy fuera del corpus: 0).
- Configuraciones: 523; pares definición × implemento: 411.
- Unidades de texto: 12547; frases: 15603; palabras: 277442.
- Configuraciones sin ningún estabilizador: 295.
- Distribución de músculos principales por configuración: {1: 426, 2: 95, 3: 2}.
- Hallazgos bloqueantes (ERROR): 23567, de ellos 6793 en los cuatro campos que la app muestra o dicta (`definition.description`, `profile.description`, `profile.setupCues`, `profile.executionCues`). El resto está en campos legacy que la fase F1 retira.
- Avisos (WARN): 220; exenciones aplicadas: 0.

## Texto compartido entre definiciones distintas

- Frases distintas reutilizadas por más de una definición: 309; la más repetida aparece en 201 definiciones.
  - 201 definiciones: «Detener la serie si aparece dolor agudo o pérdida de control; no diagnostica lesiones.»
  - 87 definiciones: «Movilidad de cadera según tolerancia y rango disponible.»
  - 77 definiciones: «La trayectoria permanece estable y la vuelta conserva la tensión en la zona que define esta variante.»
  - 74 definiciones: «Movilidad de rodilla según tolerancia y rango disponible.»
  - 72 definiciones: «Movilidad de hombro según tolerancia y rango disponible.»

| n-grama (palabras) | Mín. palabras de contenido | N-gramas compartidos | Frases afectadas | Definiciones afectadas |
| ---: | ---: | ---: | ---: | ---: |
| 5 | 3 | 4315 | 11422 | 201 |
| 5 | 4 | 797 | 4793 | 201 |
| 6 | 4 | 1812 | 8443 | 201 |
| 8 | 4 | 3393 | 8417 | 201 |

## Hallazgos por chequeo

| Chequeo | Severidad | Hallazgos | En texto visible | Definiciones | Configuraciones |
| --- | --- | ---: | ---: | ---: | ---: |
| `anatomy_rule` | ERROR | 137 | 0 | 31 | 89 |
| `banned_phrase` | ERROR | 480 | 480 | 157 | 264 |
| `grammar` | ERROR | 394 | 2 | 132 | 354 |
| `implement_mismatch` | ERROR | 16 | 16 | 8 | 10 |
| `inheritance` | ERROR | 56 | 0 | 23 | 23 |
| `intra_config_duplicate` | ERROR | 3285 | 1539 | 198 | 519 |
| `muscle_claim` | ERROR | 23 | 23 | 22 | 0 |
| `muscle_claim` | WARN | 19 | 19 | 8 | 19 |
| `primary_unmentioned` | ERROR | 11 | 11 | 11 | 0 |
| `shape` | ERROR | 730 | 730 | 177 | 460 |
| `shared_ngram` | ERROR | 2923 | 389 | 200 | 518 |
| `shared_sentence` | ERROR | 6469 | 881 | 201 | 523 |
| `template_skeleton` | ERROR | 6511 | 960 | 201 | 523 |
| `template_skeleton_in_definition` | ERROR | 1172 | 402 | 89 | 312 |
| `visual_missing` | WARN | 201 | 0 | 201 | 0 |
| `word_budget` | ERROR | 1360 | 1360 | 198 | 452 |

## Variedad por tipo de texto (total / únicos)

| Tipo | Total | Únicos | Únicos / total |
| --- | ---: | ---: | ---: |
| `benefit` | 1046 | 549 | 0.52 |
| `cfg_description` | 523 | 523 | 1.00 |
| `cue_execution` | 523 | 523 | 1.00 |
| `cue_setup` | 523 | 523 | 1.00 |
| `def_description` | 201 | 201 | 1.00 |
| `intent` | 1046 | 62 | 0.06 |
| `joint_note` | 1916 | 1878 | 0.98 |
| `mistake` | 523 | 30 | 0.06 |
| `mobility` | 1034 | 14 | 0.01 |
| `muscle_note` | 1495 | 1474 | 0.99 |
| `objective` | 1046 | 547 | 0.52 |
| `precaution` | 523 | 1 | 0.00 |
| `progression` | 523 | 27 | 0.05 |
| `rationale` | 523 | 523 | 1.00 |
| `regression` | 523 | 27 | 0.05 |
| `risk` | 56 | 14 | 0.25 |
| `technique` | 523 | 523 | 1.00 |

## Qué mide cada chequeo

| Chequeo | Severidad | Qué detecta |
| --- | --- | --- |
| `intra_config_duplicate` | ERROR | Una frase se repite entre campos de una misma configuración (o con la descripción de su definición); incluye casi-duplicados (Jaccard ≥ 0,85). |
| `shared_sentence` | ERROR | La misma frase aparece en otra definición. Cuenta texto visible, campos legacy y ficha técnica; la ficha anatómica solo avisa y la visual está exenta. |
| `shared_ngram` | ERROR | Fragmento compartido con otra definición aunque la frase completa sea distinta: ≥ 8 palabras con ≥ 4 de contenido, o 5 palabras con ≥ 4 de contenido (frase densa). |
| `template_skeleton` | ERROR | Misma plantilla en ≥ 3 definiciones con solo músculo, implemento, articulación o número cambiados. |
| `template_skeleton_in_definition` | ERROR | Una configuración repite la frase de su hermana cambiando solo el implemento: el delta por implemento tiene que decir algo distinto. |
| `template_opening` | ERROR | La misma apertura de 4 palabras en ≥ 6 configuraciones de ≥ 3 definiciones. |
| `banned_phrase` | ERROR | Cliché o plantilla del léxico prohibido (`curation/quality_lexicon.json`). |
| `word_budget` | ERROR | Palabra comodín usada en más definiciones que su presupuesto por catálogo (`curation/quality_lexicon.json`). |
| `imperative_description` | ERROR | La descripción empieza con un imperativo; las pautas directas van en los cues. |
| `grammar` | ERROR | «de el»/«a el», identificadores snake_case en prosa, espacios dobles, palabra repetida, puntuación defectuosa, preposición seguida de coma o minúscula inicial. |
| `shape` | ERROR | Longitudes: definición 3–6 frases y 35–140 palabras; configuración 1–2 frases y 12–55 palabras; cues ≤ 30 palabras; setup 1–3 cues y execution 1–4. |
| `implement_mismatch` | ERROR | Un cue nombra un implemento distinto al de su configuración, o una descripción nombra uno que ninguna variante usa (las negaciones «sin barra» se toleran en prosa). |
| `muscle_claim` | ERROR/WARN | El texto menciona un músculo que los datos no declaran. ERROR en la descripción de la definición, WARN en la de una configuración. |
| `primary_unmentioned` | ERROR | La descripción de la definición no nombra ningún músculo principal. |
| `anatomy_rule` | ERROR | Incumple una regla de `curation/anatomy_rules.json` (músculos, articulaciones o patrón). |
| `inheritance` | ERROR | Una especialidad difiere anatómicamente de su ejercicio base en la configuración por defecto sin una diferencia declarada. |
| `rules_invalid` | ERROR | El archivo de reglas referencia algo inexistente o incompleto; mientras exista, sus reglas no se aplican. |
| `rule_dead` | WARN | Una regla no selecciona ninguna configuración, o menciona un patrón o implemento sin uso. |
| `allowlist_invalid` | ERROR | Una entrada de `curation/quality_allowlist.json` no declara `check` y `why`. |
| `ficha_shape` | ERROR | Ficha CURATED incompleta (fuentes, bloque técnico o bloque anatómico). |
| `visual_incomplete` | ERROR | Ficha CURATED sin ficha visual completa por implemento, o con `promptCore` inválido. |
| `visual_missing` | WARN | La definición todavía no tiene ficha curada (LEGACY o ausente). |

## Decisiones de calibración

- **Alcance del corpus.** Sin fichas curadas (línea base) se compara todo el catálogo contra sí mismo, incluidos los campos legacy que la fase F1 retira. Con fichas, el corpus es solo texto `CURATED` más las definiciones pedidas con `--definitions`: así el texto nuevo no se culpa por texto legacy que se va a reescribir.
- **N-gramas.** Bloquea un fragmento compartido de 8 palabras con ≥ 4 de contenido, o de 5 palabras con ≥ 4 de contenido (frase densa en sustantivos y verbos). Los 5-gramas con 3 palabras de contenido y los 6-gramas se informan como métrica, no como hallazgo: son colocaciones naturales («la barra cerca del cuerpo», «con los pies al ancho de los hombros») y bloquearlas empujaría a escribir peor.
- **Plantillas.** Se enmascaran músculos, implementos, articulaciones y números antes de comparar; una frase que solo cambia esas palabras es la misma frase. Con ≥ 3 definiciones es plantilla; dentro de una definición, dos configuraciones hermanas no pueden diferir solo en el implemento.
- **Presupuesto léxico.** Verbos y adjetivos comodín («ejecuta», «tensión», «trayectoria», «honesto»…) se reparten por catálogo: una palabra puede usarse en un máximo de definiciones. Es reciclaje de palabras, no solo de frases. Los topes viven en `curation/quality_lexicon.json` y se ajustan con evidencia.
- **Implementos.** En descripciones se permite comparar con otro implemento de la misma definición y negar uno («sin barra»). En los cues, que se leen en voz alta durante la serie, nombrar un implemento distinto al de la configuración es error.
- **Músculos.** Una descripción solo nombra músculos que los datos declaran para esa definición o configuración. Referencias de posición (por ejemplo la barra «sobre los trapecios») se redactan sin nombre de músculo (parte alta de la espalda) o se justifican en `curation/quality_allowlist.json`.
- **Reglas anatómicas.** Se aplican sobre la configuración, no sobre el texto, y solo si el archivo de reglas es válido. Cada excepción exige un `why`.
- **Exenciones.** `quality_allowlist.json` solo acepta entradas con `check`, un selector y `why`; una entrada sin justificación se ignora y se informa.
