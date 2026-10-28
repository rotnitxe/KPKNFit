# Encargo común: entradas de la enciclopedia «Conceptos clave» de KPKN

KPKN es una app de entrenamiento de fuerza y nutrición. «Conceptos clave» pasa de 31 conceptos de entrenamiento a una enciclopedia con músculos, patrones, nutrición, suplementos, lesiones, recuperación y estudios. Cada grupo de redacción (A1…A8) escribe SOLO sus entradas, listadas en `docs/conceptos-clave/catalogo.json` con su `group`.

## Archivo que entregas

`docs/conceptos-clave/contenido/<GRUPO>.json`: una lista JSON (UTF-8, con tildes reales, sin escapes \u), una entrada por id de tu grupo, en el orden del catálogo. Es el ÚNICO archivo que puedes crear o modificar.

```json
{
  "id": "rir",
  "name": "RIR (repeticiones en reserva)",
  "category": "INTENSITY",
  "short": "Cuántas repeticiones te quedan antes de llegar al fallo",
  "aliases": ["repeticiones en reserva", "reps in reserve"],
  "body": [
    "RIR significa repeticiones en reserva: una estimación de cuántas repeticiones técnicamente válidas podrías haber completado al terminar una serie. RIR 0 representa el [[fallo-muscular|fallo]] concéntrico de esa tarea; RIR 2 indica que probablemente quedaban dos repeticiones. Es una predicción, no una medición directa, y su precisión cambia con el ejercicio, la experiencia y el estado de fatiga.",
    "Usar RIR ayuda a autorregular la carga sin depender de un porcentaje fijo que puede quedar desfasado. Permite mantener un esfuerzo exigente mientras se conserva margen para la técnica, y hace visible cuándo una sesión está saliendo más fácil o más difícil de lo esperado. No conviene tratarlo como una verdad exacta ni compararlo entre ejercicios muy distintos; se parece a la escala [[rpe|RPE]] pero cuenta repeticiones."
  ],
  "practice": "La estimación suele ser más consistente en ejercicios conocidos y con repeticiones estables. En movimientos complejos, la técnica puede fallar antes que el músculo objetivo, por lo que el RIR debe describir la repetición que aún sería válida, no una repetición forzada con compensaciones.",
  "related": ["rpe", "fallo-muscular", "autorregulacion"],
  "sources": [
    {"title": "Feasibility and usefulness of repetitions-in-reserve scales for selecting exercise intensity: a scoping review", "publisher": "Perceptual and Motor Skills · 2024", "url": "https://pubmed.ncbi.nlm.nih.gov/38563729/"}
  ]
}
```

### Reglas de cada campo

- `id`, `category`: exactamente los del catálogo. `name`: exactamente el del catálogo (solo el grupo A8 puede ajustar autor o año si la verificación muestra otro).
- `short`: una frase de 20 a 130 caracteres que define el concepto. Sin punto final, sin enlaces.
- `aliases`: 0 a 6 sinónimos, siglas o nombres en inglés que alguien buscaría (p. ej. «drop set», «DOMS», «whey»). Sin repetir el nombre.
- `body`: 2 o 3 párrafos. Primero qué es; después por qué importa y sus matices. Cada párrafo es un texto de una línea (sin saltos de línea).
- `practice`: un párrafo «En la práctica» (≥ 30 palabras): qué hacer o mirar con esto en el gimnasio o en la cocina.
- Longitud: `body` + `practice` suman **entre 180 y 240 palabras** (los enlaces cuentan por su texto visible).
- Enlaces de wiki dentro de `body` o `practice`: `[[id|texto visible]]`, al menos uno por entrada, solo a ids del catálogo (los `existing` o los `new` de cualquier grupo). Nunca a ti mismo.
- `related`: 2 a 4 ids del catálogo para «Ver también».
- `sources`: 1 a 3 fuentes, **solo artículos de PubMed**: `url` exactamente `https://pubmed.ncbi.nlm.nih.gov/<PMID>/`, `title` = título real del artículo en inglés (puede omitirse el subtítulo tras «:»), `publisher` = «Revista · año». Para anatomía y lesiones sirven los capítulos de StatPearls, que tienen PMID (p. ej. «Anatomy, Shoulder and Upper Limb, Deltoid Muscle»).

## Verificación de fuentes (obligatoria, sin excepciones)

No escribas ninguna fuente de memoria. Para cada una:
1. Búscala: `WebFetch` a `https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=pubmed&retmode=json&term=<palabras del título o autor+año>` → te da PMIDs.
2. Confírmala: `WebFetch` a `https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&retmode=json&id=<PMID>` → copia el `title` y la revista/año de ahí.
3. La fuente debe respaldar lo que dice tu texto. Si no encuentras una fuente verificable para una afirmación, quita la afirmación.
(La web pubmed.ncbi.nlm.nih.gov pide reCAPTCHA; usa siempre la API eutils.)

## Estilo

- Español neutro, tuteo («puedes», «tu entrenamiento»), frases claras, sin emojis, sin listas, sin negritas.
- Tono de divulgación rigurosa: explica el mecanismo y los matices; evita absolutos («siempre», «nunca», «el mejor») y promesas.
- Nada de cifras inventadas. Las cifras que des (g/kg, mg/kg, minutos, porcentajes) deben salir de tus fuentes.
- Salud: no diagnostiques ni prescribas tratamientos. En lesiones y suplementos, recuerda con naturalidad cuándo conviene consultar a un profesional sanitario. No recomiendes fármacos ni sustancias prohibidas.
- No uses el carácter «$» ni comillas triples. Usa comillas latinas « » si citas algo.
- No copies texto de los artículos: resume con tus palabras.
- Lee 2 o 3 entradas existentes para igualar el tono: `android-native/app/src/main/java/com/example/kpkn/data/wikilab/TrainingConceptsData.kt` (las primeras 200 líneas bastan).

## Hecho

Ejecuta (desde la raíz del repo `C:\Users\valen\Documents\KPKNFit`):

```
python -I docs/conceptos-clave/validar.py <GRUPO> --red
```

Hecho = la última línea es `VALIDACIÓN OK: <GRUPO>`. Corrige y repite hasta que lo sea. `--red` comprueba cada PMID contra PubMed: si dice que el título no coincide, la fuente está mal; búscala de nuevo.

## Prohibido

Tocar cualquier archivo que no sea `docs/conceptos-clave/contenido/<GRUPO>.json`; ejecutar Gradle o git; inventar fuentes o PMIDs; cambiar ids o categorías.

## Informe final (exactamente este formato, sin prosa adicional)

- Archivo: docs/conceptos-clave/contenido/<GRUPO>.json
- Entradas: <n>
- Verificación: <la última línea literal del validador>
- Afirmaciones quitadas por falta de fuente / dudas: <lista o «ninguna»>
