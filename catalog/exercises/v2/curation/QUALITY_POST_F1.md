# Calidad editorial del catálogo v2 tras F1 (campos retirados)

Generado por `scripts/catalog_v2_quality_audit.py` (solo lectura) sobre la revisión `v2-approved-2026-09-29-a`.
Las cifras se vuelven a medir en cada ejecución; este documento solo congela una medición. Regenerar: `python scripts/catalog_v2_quality_audit.py --markdown catalog/exercises/v2/curation/QUALITY_BASELINE.md`.

## Cobertura medida

- Definiciones auditadas: 201 en 96 familias (corpus: 201; legacy fuera del corpus: 0).
- Configuraciones: 523; pares definición × implemento: 411.
- Unidades de texto: 1950; frases: 3796; palabras: 68762.
- Configuraciones sin ningún estabilizador: 293.
- Distribución de músculos principales por configuración: {1: 423, 2: 98, 3: 2}.
- Hallazgos bloqueantes (ERROR): 5744, de ellos 5569 en los cuatro campos que la app muestra o dicta (`definition.description`, `profile.description`, `profile.setupCues`, `profile.executionCues`). El resto está en campos legacy que la fase F1 retira.
- Avisos (WARN): 217; exenciones aplicadas: 0.

## Texto compartido entre definiciones distintas

- Frases distintas reutilizadas por más de una definición: 136; la más repetida aparece en 76 definiciones.
  - 76 definiciones: «La trayectoria permanece estable y la vuelta conserva la tensión en la zona que define esta variante.»
  - 20 definiciones: «La pierna de trabajo recibe la bajada y la subida; el apoyo libre acompaña sin robar fuerza ni dejar que la pelvis se incline.»
  - 16 definiciones: «El pie permanece completo en contacto con la base y la rodilla sigue la dirección de los dedos mientras el cuerpo baja y vuelve.»
  - 15 definiciones: «El brazo superior queda como referencia y el codo se abre hasta completar la extensión; el hombro no roba el recorrido.»
  - 12 definiciones: «Con la barra, la carga sube al máximo y la progresión del cuádriceps se mide sin trampa.»

| n-grama (palabras) | Mín. palabras de contenido | N-gramas compartidos | Frases afectadas | Definiciones afectadas |
| ---: | ---: | ---: | ---: | ---: |
| 5 | 3 | 836 | 1706 | 199 |
| 5 | 4 | 112 | 519 | 136 |
| 6 | 4 | 307 | 1047 | 196 |
| 8 | 4 | 756 | 1091 | 189 |

## Hallazgos por chequeo

| Chequeo | Severidad | Hallazgos | En texto visible | Definiciones | Configuraciones |
| --- | --- | ---: | ---: | ---: | ---: |
| `anatomy_rule` | ERROR | 117 | 0 | 28 | 77 |
| `banned_phrase` | ERROR | 469 | 469 | 154 | 260 |
| `grammar` | ERROR | 2 | 2 | 1 | 1 |
| `implement_mismatch` | ERROR | 16 | 16 | 8 | 10 |
| `inheritance` | ERROR | 56 | 0 | 23 | 23 |
| `intra_config_duplicate` | ERROR | 501 | 501 | 193 | 501 |
| `muscle_claim` | ERROR | 21 | 21 | 20 | 0 |
| `muscle_claim` | WARN | 19 | 19 | 8 | 19 |
| `primary_unmentioned` | ERROR | 11 | 11 | 11 | 0 |
| `shape` | ERROR | 700 | 700 | 174 | 448 |
| `shared_ngram` | ERROR | 380 | 378 | 102 | 205 |
| `shared_sentence` | ERROR | 841 | 841 | 186 | 459 |
| `template_skeleton` | ERROR | 899 | 899 | 189 | 486 |
| `template_skeleton_in_definition` | ERROR | 388 | 388 | 81 | 278 |
| `visual_missing` | WARN | 198 | 0 | 198 | 0 |
| `word_budget` | ERROR | 1343 | 1343 | 196 | 443 |

## Variedad por tipo de texto (total / únicos)

| Tipo | Total | Únicos | Únicos / total |
| --- | ---: | ---: | ---: |
| `cfg_description` | 523 | 523 | 1.00 |
| `cue_execution` | 538 | 538 | 1.00 |
| `cue_setup` | 537 | 536 | 1.00 |
| `def_description` | 201 | 201 | 1.00 |
| `ficha_anatomy` | 32 | 30 | 0.94 |
| `ficha_technique` | 55 | 54 | 0.98 |
| `ficha_visual` | 64 | 64 | 1.00 |

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
