# Conceptos clave · enciclopedia

Petición del usuario (27-oct-2026), tras aprobar el prototipo `docs/design/profile-concepts-v1`: ampliar Conceptos clave para que parezca una enciclopedia o wiki, con suplementos, alimentación, más conceptos de entrenamiento, patrones de movimiento, músculos y otras estructuras del cuerpo, lesiones y los estudios más relevantes para la comunidad.

## Cómo está hecho

| Pieza | Qué es |
|---|---|
| `catalogo.json` | Lista cerrada de áreas, temas (18) y entradas nuevas (135), cada una con su grupo de redacción A1…A8. Fija ids, nombres y temas. |
| `BRIEF.md` | Encargo común a los redactores: formato JSON, longitud (180-240 palabras), enlaces de wiki `[[id\|texto]]`, estilo y verificación de fuentes. |
| `contenido/A*.json` | El texto de cada grupo (fuente de verdad del contenido). |
| `validar.py` | Comprueba el contrato de cada entrada; con `--red` comprueba cada PMID contra la API E-utilities de NCBI (la web de PubMed pide reCAPTCHA). |
| `generar.py` | Valida y genera `android-native/.../data/wikilab/EncyclopediaConceptsData.kt`. No se edita a mano. |

```bash
python -I docs/conceptos-clave/validar.py todos --red
python -I docs/conceptos-clave/generar.py
```

## Modelo en la app

- `ConceptArea` (Entrenamiento, Cuerpo, Nutrición, Salud, Ciencia) agrupa los `ConceptCategory` en la portada. Los colores de tema son tonos apagados de la identidad y solo se usan en detalles (punto y glifo).
- `TrainingConcept` añade `practice` («En la práctica», va también al final de `description`), `aliases` (los encuentra el buscador) y `related` («Ver también»).
- `TRAINING_CONCEPTS_DATABASE` = los 31 conceptos originales + la enciclopedia generada. `TRAINING_CONCEPT_SOURCES` igual.
- `domain/concepts/ConceptosClaveIndex.kt`: enlaces de wiki (`parseConceptText`), búsqueda con dónde coincidió y fragmento, resaltado sin tildes, concepto del día compartido con Home, relacionados, anterior/siguiente y temas por área.

## Reglas de contenido

- Solo fuentes de PubMed verificadas por PMID; el validador rechaza las que no coinciden.
- Nada de diagnósticos ni pautas de tratamiento; en lesiones y suplementos se recuerda consultar a un profesional sanitario.
- Las 16 referencias de los conceptos originales se comprobaron contra PubMed el 27-oct-2026; se corrigió la revista de 4 (RIR, mecanismos de carga, pliometría y especificidad) y todas pasan al formato «Revista · año».

## Decisión de diseño por la escala

El prototipo aprobado listaba todos los conceptos agrupados por tema al final de la biblioteca. Con ~165 conceptos, la portada agrupa los temas por área y termina con un índice de la A a la Z por letra; el resto (concepto del día, búsqueda con resaltado, página de tema y lectura) es el del prototipo.
