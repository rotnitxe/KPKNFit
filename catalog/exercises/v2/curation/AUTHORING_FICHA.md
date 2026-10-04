# Manual de autoría — ficha CURATED

Cómo se escribe, se comprueba y se entrega la ficha de una definición. Las reglas
editoriales (nombres, roles, estructura de la descripción) viven en
`EDITORIAL_GUIDE.md`; este documento es el procedimiento y el nivel de calidad.
La ficha de referencia es `fichas/back_seal_row.json` (definición `seal_row`):
léela entera antes de escribir la primera.

## 1. Qué se espera

Una ficha CURATED describe **un ejercicio real**, no una plantilla con el nombre
cambiado. Cada texto debe ser verdadero para ESA definición y para ESA
configuración, y debe servir para tres cosas a la vez:

1. **Informar** a quien entrena (`public`): qué es, qué trabaja, qué lo distingue
   de sus hermanos y qué cambia con cada implemento o agarre.
2. **Fijar la verdad anatómica** (`anatomy`): qué músculos y articulaciones
   participan, con qué rol y por qué, apoyado en fuentes que existen.
3. **Describir cómo se ve** (`technique` + `visual`): la explicación interna a
   partir de la cual se genera la imagen de demostración, de modo que la imagen
   salga exactamente como el ejercicio.

Los tres defectos que esta re-curaduría corrige, y que ningún texto nuevo puede
reintroducir:

- **Genérico**: frases que valen para cualquier ejercicio ("trabaja el músculo con
  control", "ideal para ganar fuerza"). Si la frase sirve para otro ejercicio,
  sobra o hay que hacerla específica.
- **Reciclado**: la misma frase o el mismo esqueleto con otro músculo u otro
  implemento. El auditor lo mide entre todas las definiciones CURATED.
- **Erróneo**: describe otro ejercicio, un implemento que no es, un músculo que no
  trabaja o un rol que no corresponde. Es el más grave: si el texto heredado
  (`LEGACY`) dice algo que no se sostiene, se corrige; nunca se hereda por inercia.

## 2. Procedimiento por definición

1. **Ver el estado actual**:
   `python scripts/catalog_v2_show.py <definitionId> --public` (o `--family <familyId>`).
   Lo que muestra de una definición `LEGACY` es contenido heredado **a revisar, no
   a creer**: puede ser genérico, estar reciclado o ser falso.
2. **Entender el ejercicio real** antes de escribir: qué hace el cuerpo (postura,
   recorrido, dónde está la carga), qué lo separa de sus definiciones hermanas
   (otro ángulo, otra posición de carga, otro patrón) y qué cambia de verdad con
   cada configuración (implemento, agarre, lateralidad, altura de polea).
3. **Buscar evidencia**: `python scripts/catalog_v2_sources.py lookup "<consulta PubMed>"`
   (ver §5). Mínimo 2 fuentes por definición; cada músculo y cada articulación cita
   las suyas.
4. **Escribir** `public`, `anatomy`, `technique`, `visual`, `sources` y poner
   `status: "CURATED"` (formato en §3).
5. **Verificar las fuentes**:
   `python scripts/catalog_v2_sources.py verify --definitions <id>[,<id>]` (usa la
   red; es seguro ejecutarlo varios a la vez). Siempre con `--definitions`: sin él,
   `verify` borra la prueba de las fuentes que no cita ninguna ficha del directorio
   leído. Nunca con `--refresh`.
6. **Comprobar** hasta que dé OK:
   `python scripts/catalog_v2_ficha_lint.py --definitions <id>[,<id>] --examples 100`.
   No escribe nada en el repo: previsualiza las fichas en memoria y corre el gate y la
   auditoría de calidad. Un `RESULT: OK` sin errores ni avisos es la condición
   mínima, no el objetivo (el lint acepta avisos; tú no): después **relee tu texto
   como lo leería quien entrena** y compáralo con las definiciones hermanas.
7. **No ejecutes** `catalog_v2_apply_fichas.py`, `merge_catalog_v2_families.py` ni
   `compile_exercise_catalog_v2.py`: reescriben archivos compartidos y los corre
   quien coordina, después de revisar. Tampoco hay commits.

Un archivo de ficha es de **una familia** (`fichas/<familyId>.json`); quien edita
una familia la edita entera y no toca las demás.

**Varios autores a la vez.** Cada autor trabaja sobre una **copia privada** de las
fichas, para que lo que otro tiene a medias no contamine su lint:

1. `python scripts/catalog_v2_splice_fichas.py snapshot --to <dir>` copia las fichas.
2. Se edita `<dir>/<familyId>.json` y se pasa `--fichas-dir <dir>` al lint, a
   `catalog_v2_sources.py verify` y `check`, al brief visual y a
   `catalog_v2_lote_report.py`.
3. Quien coordina copia las definiciones terminadas a las fichas compartidas con
   `catalog_v2_splice_fichas.py splice --from <dir> --definitions <ids>`. El splice se
   niega si alguien tocó esa definición después de la copia.

## 3. Formato de la ficha

```
{ "schemaVersion": 1, "familyId": "<familyId>",
  "definitions": { "<definitionId>": {
      "status": "CURATED",
      "public":    { "description", "configurations": { "<configId>": { "description", "setupCues", "executionCues" } } },
      "technique": { "identity", "setup", "keyPositions", "mistakes", "phases" },
      "anatomy":   { "muscles", "joints", "overrides" },
      "visual":    { "base", "byImplement", "forbidden", "qa", "promptCore", "byVariant" },
      "sources":   [ { "id", "url", "title", "claim" } ] } } }
```

Una `LEGACY` solo trae `public`; al pasar a `CURATED` el resto es obligatorio
(`byVariant` y `overrides` son opcionales). `public.configurations` lleva **todas**
las configuraciones de la definición, ni una más ni una menos.

### 3.1 `public` — lo que ve la persona

- **Definición**: 3–6 frases, 35–140 palabras. Estructura de `EDITORIAL_GUIDE` R8:
  abre con el nombre del ejercicio (y el alias inglés si es el conocido), el tipo de
  movimiento y qué trabaja; luego cómo se ejecuta contado de forma cercana; solo si
  aporta, una o dos alternativas con su efecto real; cierra con una valoración
  **concreta de ese ejercicio**. Cada afirmación muscular debe estar declarada en
  `anatomy` (el auditor lo cruza). Cuando la definición tiene varios implementos, el
  texto de la definición **no se casa con uno**: cuenta el ejercicio común y deja lo
  propio de cada implemento a las configuraciones.
- **Configuración**: 1–2 frases, 12–55 palabras. No repite el nombre del ejercicio.
  Dice qué cambia **en este ejercicio** con ese implemento/agarre/lateralidad:
  recorrido libre o guiado, estabilidad, carga unilateral, tensión constante o
  creciente, rango, comodidad articular. Si lo único que puedes escribir es "con
  mancuernas se usan mancuernas", no hay nada que decir: busca la diferencia real o
  describe el rasgo propio de esa configuración.
- **`setupCues`** (1–3) y **`executionCues`** (1–4): una indicación por línea, ≤30
  palabras, en imperativo de "tú" (aquí el imperativo sí es lo correcto). Concretas
  y comprobables ("toca con la barra la parte inferior del banco"), no consejos
  vacíos ("mantén el control"). Son la base del ejercicio más, solo si cambia la
  preparación, lo propio de ese implemento. Se ven durante el entrenamiento y el
  relator las **lee en voz alta**: frases cortas, sin paréntesis, símbolos ni
  abreviaturas, y con cifras solo si son imprescindibles.
- Las **descripciones** no empiezan frases con verbos instruccionales (mantén,
  controla, asegura…). Mayúscula inicial en cada texto. Sin dobles nombres,
  paréntesis de taxonomía ni palabras de `quality_lexicon.json` (léelo).
- En la descripción de la **definición** no aparece en ninguna parte, ni a mitad de
  frase, mantén, mantener, configura, adopta, controla ni selecciona: el test del
  backend lo exige y el gate lo comprueba.
- Cada descripción de **configuración** tiene al menos 80 caracteres (la app falla
  por debajo) y es distinta de todas las demás del catálogo.
- Ningún texto usa la palabra «pendiente»: el gate la toma por un marcador sin
  completar.

### 3.2 `anatomy` — verdad muscular y articular

- `muscles`: `{id, role, why, sources[]}` con ids de la ontología (21) y rol
  `PRIMARY` / `SECONDARY` / `STABILIZER` (pesos 1.0 / 0.5 / 0.4 en el volumen). Al
  menos un `PRIMARY`.
- `joints`: `{id, role, actions[], why, sources[]}`; `actions` empiezan con
  mayúscula ("Flexión del codo"). Una entrada por articulación realmente implicada.
- **Orden de los músculos.** Dentro de cada rol se respeta el orden en que se
  escriben, y el **primer PRIMARY es el músculo dominante** de la configuración: la
  composición de sesiones (`SessionCompositionPolicy`, `CompositionTaxonomy`) lee
  `primaryMuscles.first()`, y cada PRIMARY suma una serie completa al volumen semanal.
  Los PRIMARY van primero y en orden de dominancia. El dominante heredado no se cambia
  sin evidencia; si cambia, se reporta (§6). En `overrides`, un PRIMARY nuevo se agrega
  al final (nunca queda dominante) y `role: NONE` sobre el primero promueve al
  siguiente.
- **Exceso de volumen (criterio del usuario, 2026-10-04).** El exceso aportado por
  ejercicios en los que ESE músculo es SECONDARY o STABILIZER se permite y se
  distingue del volumen directo en el informe. Si ESE EXTRA lo aporta un ejercicio
  en el que ESE músculo es PRIMARY, se revisa y ajusta la prescripción antes de
  darla por aceptada. La ficha conserva los roles que respalda la evidencia: no
  se rebaja un PRIMARY para hacer que un plan quepa en su tope. La tolerancia al
  aporte indirecto no autoriza más series principales.
- `why` (≥40 caracteres): la función concreta de ESE músculo o articulación en ESE
  ejercicio y por qué merece ese rol. No es una definición de libro del músculo.
- `sources`: ids declarados en `sources` (cada `why` se apoya en lo que el estudio
  dice, no en lo que "se sabe").
- `overrides.<configId>`: solo cuando el rol o el `why` cambian de verdad en esa
  configuración. Una entrada reemplaza a la de igual id, una nueva se añade y
  `role: "NONE"` la quita. Cada excepción lleva `why` (≥40) y, si no es `NONE`,
  `sources`.
- Reglas y herencia en `curation/anatomy_rules.json`. La evidencia manda sobre la
  regla (decisión del producto): si tu evidencia contradice una regla o una frase de
  la guía, **escribe lo que dice la evidencia, no adaptes la ficha a la regla**, y
  repórtalo (§6). No edites `anatomy_rules.json`.
- La ontología tiene 21 músculos y 15 articulaciones, y **no incluye** oblicuos,
  braquial, manguito rotador, serrato, sóleo ni rotadores profundos de cadera. Usa
  el id más cercano y explica en el `why` qué estructura real hace el trabajo; no
  inventes ids ni los fuerces a un papel que no tienen.
- No cambies `movementPatternId` ni agregues `pattern` a la ficha: si crees que el
  patrón está mal, reportarlo (§6).

### 3.3 `technique` — interna, no viaja a la app

`identity` (≥40 caracteres: qué es el ejercicio, en una o dos frases, con la
posición del cuerpo y de la carga, **y qué lo separa de sus definiciones hermanas**),
`setup[]` (≥1), `keyPositions[]` (≥1: apoyos, ángulos aproximados, trayectoria de la
carga en el inicio y en el punto clave), `mistakes[]` (≥2, errores reales de ese
ejercicio) y `phases[]` (≥2, cada una `{name, description ≥20}`, en el orden del
movimiento). Es lo que lee quien genera la imagen: debe poder dibujarse sin ver el
catálogo (`catalog_v2_visual_brief.py` lo imprime como contexto técnico, fuera del
prompt). No nombres en `technique` implementos ajenos a la definición ni contrastes
con hermanas («a diferencia de la versión con barra»): el auditor lo marca, y esos
contrastes van en `visual.forbidden`.

### 3.4 `visual` — el brief con el que se hace la imagen

- `base`: `camera`, `phase` (el fotograma que se dibuja, normalmente el punto clave),
  `orientation`, `contacts` (qué apoya dónde), `posture`, `load` — cada uno ≥8
  caracteres. Es el marco **común a todos los implementos** de la definición: no
  nombres un implemento concreto aquí (eso va en `byImplement` y `promptCore`), para
  que el brief de cada implemento no arrastre datos de otro.
- `byImplement.<equipmentId>.geometry` (≥20): dónde está el implemento respecto del
  cuerpo. **Una entrada por cada implemento que tiene la definición**, ninguna más
  (`catalog_v2_show.py` los lista).
- `promptCore.<equipmentId>`: la frase central en **inglés ASCII de 60 a 700
  caracteres**, sin texto, logos ni marcas, que describe el fotograma exacto. Una por
  implemento. Lo que enseñó la prueba de imagen del Remo Seal (tres generaciones, un
  inspector limpio por imagen): el generador dibuja por defecto la **posición de
  partida** y deja la carga **flotando** si el prompt no dice quién la sujeta. Por eso
  `promptCore` (1) nombra el fotograma con una palabra de congelado ("frozen at the TOP
  of ...", "at the bottom of ..."); (2) dice **qué mano agarra qué** y cómo ("both hands
  grip the barbell with an overhand grip"); (3) fija cada contacto relevante (pecho, pies,
  carga contra el banco) como hecho presente, no como movimiento ("is pressed against",
  no "is pulled up until"); y (4) dice dónde queda la carga respecto del suelo. Un
  contacto milimétrico (barra rozando la cara inferior del tablero) no lo garantiza el
  generador: ponlo en `geometry` y en la descripción pública, y no lo conviertas en el
  único criterio de `qa`.
- `forbidden[]` (≥2): errores concretos que harían falsa la imagen, sobre todo
  confundirla con una definición hermana ("de pie e inclinado: eso es un remo con
  barra, no un remo seal").
- `qa[]` (≥3): preguntas de sí/no que alguien que no conoce el ejercicio puede
  contestar mirando la imagen ("¿El atleta está tumbado boca abajo con el pecho
  sobre el banco?"). Incluye siempre una de **sujeción** ("¿las manos agarran la barra
  y la carga no flota suelta?") y una del **fotograma** (posición de los codos o de la
  carga en el punto clave), porque son los dos fallos que el generador comete más.
- `byVariant.<configId>`: opcional; `{ "difference": "<qué se ve distinto>" }` (≥20)
  cuando una configuración se ve distinta de la geometría de su implemento (agarre
  supino, otro ángulo, apoyo unilateral).

### 3.5 `sources`

`{id, url, title, claim}`: `id` corto y único en la definición; `url` de PubMed
(`https://pubmed.ncbi.nlm.nih.gov/<pmid>/`) o `https://doi.org/...`; `title` **tal
como figura en la fuente**, en su idioma original (≥10 caracteres); `claim` (≥20): lo
que esa fuente respalda **aquí**, fiel a lo que dice (no inflar un resultado).

## 4. Estilo

- Español neutro y cercano; experto sin jerga hueca. Un término técnico preciso
  (bisagra, retracción escapular) vale cuando es la palabra correcta.
- Frases con información. Quita adjetivos de marketing, superlativos y veredictos de
  cliché (la lista está en `quality_lexicon.json`).
- **Varía la forma además del contenido.** Si tu segunda definición abre con el mismo
  esqueleto que la primera ("X es un ejercicio de Y que trabaja Z"), reescríbela:
  dos definiciones distintas no se leen como la misma plantilla.
- No inventes datos para llenar un campo. Si no puedes afirmar algo con la fuente o
  con biomecánica sólida, di menos.
- Las afirmaciones de **riesgo o seguridad** también necesitan respaldo; en la duda,
  se omiten (esos campos ya no existen en el catálogo).

## 5. Evidencia

- Busca con `python scripts/catalog_v2_sources.py lookup "<consulta>"`: devuelve
  `url | año | revista | autores | título`, listo para copiar. Consultas útiles:
  `"<músculo> electromyography <ejercicio>"`, `"<ejercicio> EMG"`,
  `"<articulación> kinematics <ejercicio>"`, `"<músculo> anatomy"` (los capítulos
  de StatPearls de anatomía están en PubMed y sirven para la función muscular).
- **Solo** fuentes que existan y cuyo título coincida: `verify` lo comprueba contra
  PubMed/Crossref y el gate lo exige sin red. No se citan páginas de divulgación
  generadas por IA ni resultados "de memoria".
- Un estudio de EMG respalda "este músculo se activa" en esa tarea; no respalda un
  rol en otro ejercicio parecido. Si extrapolas, dilo en el `why`.
- Al menos 2 fuentes por definición; más cuando haya músculos o variantes
  discutibles. Una misma fuente puede respaldar varias entradas.
- Si la URL ya figura en `curation/sources_verified.json`, copia su `title` exacto: la
  prueba se guarda por URL y exige el mismo título, así que dos grafías distintas se
  pisan entre autores.
- Para juzgar si un `claim` es fiel, lee el resumen:
  `python scripts/catalog_v2_sources.py abstract <pmid>`.

## 6. Qué se reporta (y no se decide solo)

Al terminar, devuelve un informe breve con:

- **Definiciones** entregadas y el resultado del lint (OK / qué falta).
- **Cambios de anatomía respecto del heredado**: definición/configuración, entrada,
  rol antes → rol después, y la evidencia en una línea. La tabla la genera
  `python scripts/catalog_v2_lote_report.py --definitions <ids> --fichas-dir <dir>`,
  que además marca los cambios de músculo dominante en configuraciones que usa el
  código de la app (planes, plantillas, protocolos) o que fijan sus tests.
- **Conflictos con reglas**: regla de `anatomy_rules.json` o de la guía que tu
  evidencia contradice (id de la regla, qué dice la evidencia).
- **Errores heredados** que corregiste (qué decía el texto viejo y por qué era falso).
- **Variantes dudosas**: configuraciones que no son un ejercicio distinto o que no
  existen como se describen. **No las quites**; solo repórtalas.
- **Patrón o identidad**: sospechas de `movementPatternId` mal asignado o de
  definiciones que deberían fusionarse/separarse. Solo reporte.
- **Pendiente o no resuelto**, con la causa.

Se reporta y no se decide solo: retirar variantes, cambiar el patrón de movimiento,
tocar `anatomy_rules.json`, `quality_lexicon.json`, `quality_allowlist.json` o la
ontología, y editar cualquier archivo fuera de la familia asignada.
