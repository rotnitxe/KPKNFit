# F2 — Parser v2 por léxico + gramática de enlaces (contrato)

Objetivo: entender la descripción con el vocabulario de F1b en vez de una cadena de regex que recorta
el texto. Determinista y explicable, en español e inglés, en **modo sombra** (D4): v1 sigue siendo el
predeterminado hasta que v2 gane en el examen con la regla de `EXAMEN.md` §7 y el usuario lo apruebe.

## 1. Dónde vive

- Dominio puro: `com.example.kpkn.domain.nutrition.v2` (sin `android.*`). Se prueba en el carril JVM.
- Datos: `ConceptCatalogV2` en memoria (conceptos, alias, variantes, porciones y, de cada ficha
  enlazada, su vector por 100 g y su base). En la app lo llena un lector del paquete (F5); en el
  examen y en los tests, `concepts_export.json` (F1b §4). El mismo código de dominio en los dos.

## 2. Tubería

1. **Normalizar con desplazamientos**: minúsculas, sin tildes, números hablados → cifras («dos»,
   «medio», «un cuarto», «a couple», «half»), fracciones (`1/2`, `½`, `1,5`), emojis fuera. Cada token
   guarda su tramo en el texto original (para `text` de la predicción y para la interfaz).
2. **Etiquetar** con un trie de tokens (coincidencia más larga) sobre: alias de conceptos (es, en,
   faltas), unidades de masa y volumen, medidas caseras y envases, tamaños, estados, formas, medios
   («con agua», «con leche»), negación, conectores, correcciones, relleno («me comí», «I had») y
   números. Un tramo puede tener varios candidatos (alias ambiguos de `ambiguous.jsonl`).
   Faltas fuera del léxico: distancia de edición acotada (1 para 4–7 letras, 2 para ≥ 8) contra
   alias del mismo largo ± 2, solo si el candidato es único.
3. **Gramática** sobre la secuencia de etiquetas, por cláusula (se corta en `,` `;` `.`, «y»/«and»
   seguidos de cantidad o alimento, «después», «then»):
   - cantidad → alimento: `N UNIDAD (de) ALIM`, `ALIM N UNIDAD`, `N ALIM TAMAÑO`, `N ALIM`,
     `medio/media ALIM`, `un poco de ALIM`.
   - modificadores (estado, forma, tamaño, medio) al alimento más cercano de su cláusula.
   - «X con Y»: plato conocido (alias de concepto `DISH` con la frase completa) > X + Y como dos
     menciones; Y hereda la cantidad solo si la frase lo dice.
   - «sin X»: forma si el alimento de la cláusula tiene esa variante («café sin azúcar», «yogur sin
     lactosa»); si no, exclusión (`excluded: true`).
   - distribución («dos tostadas, una con palta y otra con queso»), cantidades compartidas
     («2 huevos y 2 tostadas»), correcciones («eran 150 g, no 200»; «perdón, era descremada»).
4. **Resolver**: concepto + estado + forma → variante → ficha; gramos según el tipo de cantidad:
   masa directa; volumen × densidad (concepto, ficha o 1,0 en líquidos); conteo × peso por unidad
   (tamaño o M); medida casera → porción del concepto (si falta, la medida genérica × densidad);
   envase → porción del concepto; fracción × unidad o porción; sin cantidad → `defaultGrams`.
   Estado no dicho → `defaultState`. Polvo con medida de bebida → reconstituido (`reconstitution`).
5. **Salida**: `MealParseV2(mentions)`; cada `MentionV2` con tramo, concepto, ficha, gramos, tipo de
   cantidad, estado, forma, exclusión, confianza por dimensión (identidad, cantidad, estado, forma) y
   preguntas cuando la alternativa razonable difiere de forma material (umbrales del contrato v2:
   50 kcal, 5 g de proteína o grasa).

## 3. Examen

Corredor JVM (`NutritionExamV2RunnerTest`) que lee los casos y `concepts_export.json` y escribe
predicciones con el esquema de `EXAMEN.md` §6 (`text` = tramo original, `foodName` = nombre del
concepto, `foodId` = ficha). Iteración solo sobre DEV; TEST con la regla A/B.

## 4. Paquetes

| Paquete | Qué | Carril |
|---|---|---|
| F2-a | Normalizador con desplazamientos, vocabulario de modificadores es/en, trie y etiquetado | JVM |
| F2-b | Gramática → menciones (cantidad, modificadores, «con», «sin», distribución, correcciones) | JVM |
| F2-c | `ConceptCatalogV2` + cargador JSON, resolutor (variante, gramos, reconstitución, preguntas) | JVM |
| F2-d | Corredor del examen v2 y comparación con master en DEV | JVM |
| F2-e | Adaptador a `ResolvedTag`/`LoggedFood` y modo sombra en el logger | Android (F5) |

## 5. Tipos compartidos (contrato entre F2-b y F2-c; no se cambian sin el líder)

Archivo `domain/nutrition/v2/ModelV2.kt`. Lo crea el primero que lo necesite **copiando este bloque tal
cual**; nadie lo modifica (si hace falta un campo, se pide al líder).

```kotlin
package com.example.kpkn.domain.nutrition.v2

// ---- Catálogo: concepts_export.json en el examen y los tests; el paquete SQLite en la app ----

enum class ConceptKindV2 { FOOD, DISH, DRINK, SUPPLEMENT, CONDIMENT }

/** Ficha de nutrientes por 100 g; claves = NutrientId.key («energy», «protein»…); ausente = sin dato. */
data class RecordV2(
    val id: String,
    val name: String,
    val per100g: Map<String, Double>,
    val portions: List<RecordPortionV2> = emptyList(),
    val densityGPerMl: Double? = null,
)

/** Porción de la ficha tal como viene de la fuente («cup», «1 slice», «can»). */
data class RecordPortionV2(val label: String, val grams: Double)

/** Variante de un concepto: [state] null = NA; [recordId] null = sin ficha (noMatch). */
data class ConceptVariantV2(
    val state: FoodStateV2?,
    val form: FoodFormV2,
    val recordId: String?,
    val proxy: Boolean = false,
)

data class CountUnitV2(val names: List<String>, val grams: Map<SizeV2, Double>)

data class ConceptPortionV2(val label: String, val grams: Double, val state: FoodStateV2? = null)

data class ReconstitutionV2(val gramsPer100ml: Double, val medium: MediumV2)

data class DishComponentV2(val recordId: String?, val grams: Double, val note: String = "")

data class ConceptV2(
    val id: String,
    val name: String,
    val kind: ConceptKindV2,
    val category: String,
    val aliasesEs: List<String>,
    val aliasesEn: List<String>,
    val typos: List<String>,
    val defaultState: FoodStateV2?,
    val defaultForm: FoodFormV2,
    val variants: List<ConceptVariantV2>,
    val countable: Boolean,
    val unit: CountUnitV2?,
    val portions: List<ConceptPortionV2>,
    val defaultGrams: Double,
    val densityGPerMl: Double?,
    val reconstitution: ReconstitutionV2?,
    val components: List<DishComponentV2>?,
)

class ConceptCatalogV2(
    val generation: String,
    conceptList: List<ConceptV2>,
    val records: Map<String, RecordV2>,
) {
    val concepts: Map<String, ConceptV2> = conceptList.associateBy { it.id }

    fun concept(id: String): ConceptV2? = concepts[id]

    fun record(id: String?): RecordV2? = id?.let { records[it] }

    fun hasForm(conceptId: String, form: FoodFormV2): Boolean =
        concepts[conceptId]?.variants?.any { it.form == form } == true

    fun hasState(conceptId: String, state: FoodStateV2): Boolean =
        concepts[conceptId]?.variants?.any { it.state == state } == true
}

// ---- Gramática (F2-b) → resolutor (F2-c) ----

sealed interface QuantityV2 {
    /** «150 g», «medio kilo». */
    data class Mass(val grams: Double) : QuantityV2

    /** «200 ml», «un litro». */
    data class Volume(val ml: Double) : QuantityV2

    /** «2 huevos», «una manzana grande», «medio pan» (n = 0.5). */
    data class Count(val n: Double, val size: SizeV2? = null) : QuantityV2

    /** «2 cucharadas», «una taza», «un plato»: [label] = etiqueta canónica de LexTag.Household. */
    data class Household(val n: Double, val label: String, val size: SizeV2? = null) : QuantityV2

    /** «una lata», «media botella», «medio yogur de un envase de 125 g» (contenido neto si el texto lo dice). */
    data class Container(val n: Double, val label: String, val netGrams: Double? = null, val netMl: Double? = null) : QuantityV2

    /** «un poco de», «algo de», «a bit of». */
    data object Vague : QuantityV2
}

/** Mención que arma la gramática; los tramos son desplazamientos en el texto original. */
data class MentionDraftV2(
    val start: Int,
    val end: Int,
    val foodStart: Int,
    val foodEnd: Int,
    val conceptIds: List<String>,
    val quantity: QuantityV2?,
    val state: FoodStateV2? = null,
    val form: FoodFormV2? = null,
    val medium: MediumV2? = null,
    val excluded: Boolean = false,
    val fuzzy: Boolean = false,
    val attachedTo: Int? = null,
)

// ---- Resolutor (F2-c) ----

enum class QuantityKindV2 { MASS, VOLUME, COUNT, HOUSEHOLD, CONTAINER, VAGUE, DEFAULT }

enum class QuestionDimensionV2 { IDENTITY, QUANTITY, STATE, FORM }

/** Pregunta material: [options] = ids de concepto (IDENTITY) o nombres de estado/forma/porción. */
data class QuestionV2(val dimension: QuestionDimensionV2, val options: List<String>)

data class MentionV2(
    val draft: MentionDraftV2,
    val text: String,
    val conceptId: String?,
    val conceptName: String?,
    val recordId: String?,
    val grams: Double?,
    val quantityKind: QuantityKindV2,
    val state: FoodStateV2?,
    val form: FoodFormV2,
    val per100g: Map<String, Double>,
    val eatenKcal: Double?,
    val eatenProtein: Double?,
    val eatenFat: Double?,
    val eatenCarbs: Double?,
    val excluded: Boolean,
    val confidence: Double,
    val questions: List<QuestionV2>,
    val autoAccepted: Boolean,
)

data class MealParseV2(val text: String, val mentions: List<MentionV2>)
```

Reglas: `conceptIds` nunca vacío (lo que no está en el léxico no es mención); `attachedTo` = índice
en la lista de borradores de la mención base de «X con Y»; `MentionV2.text` = `text.substring(start, end)`.

## 6. `concepts_export.json` (F1b → carril JVM)

```json
{"generation": "<generación del paquete>", "conceptsSha256": "<sha de concepts.jsonl>",
 "concepts": [<concepto de concepts.jsonl con `ref` en variantes y componentes>],
 "records": {"usda_168878": {"name": "Rice, white, cooked, no added fat", "per100g": {"energy": 130, "protein": 2.7, …},
                             "portions": [{"label": "1 cup", "grams": 158}], "densityGPerMl": null}},
 "ambiguous": [{"alias": "churrasco", "concepts": ["churrasco_corte", "churrasco"]}]}
```

- `records` trae solo las fichas enlazadas (variantes y componentes), con el vector completo de 38
  nutrientes por clave de `NutrientId` y sus porciones del paquete.
- Los platos por componentes reciben una ficha derivada `kpkn_<concepto>` (suma de los componentes con
  ficha ÷ gramos totales × 100; los componentes `noMatch` no suman nutrientes y se anotan) y una variante
  `{"state": null, "form": "NA", "ref": "kpkn_<concepto>"}` si no tenían otra con ficha.

## 7. Gramática (F2-b): casos que deben salir bien

| Texto | Borradores esperados |
|---|---|
| «150 g de pollo» / «pollo 150g» / «150 grs pollo» | pollo, Mass(150) |
| «2 huevos revueltos» | huevo, Count(2), state=… del léxico (revuelto es estado o alias del concepto) |
| «una manzana grande» / «1 big apple» | manzana, Count(1, L) |
| «2 cucharadas de mantequilla de maní» | mantequilla de maní, Household(2, cucharada) |
| «media lata de atún en aceite» | atún, Container(0.5, lata), form=CANNED_* según el léxico |
| «medio yogur de un envase de 125 g» | yogur, Container(0.5, envase, netGrams=125) |
| «un vaso de leche en polvo» | leche en polvo, Household(1, vaso), form=POWDER_DRY (el resolutor reconstituye) |
| «café sin azúcar» | café, form=SUGAR_FREE (no es exclusión) |
| «hamburguesa sin pan» | hamburguesa + pan con excluded=true |
| «pan con palta y tomate» (sin plato en el léxico) | pan; palta attachedTo=0; tomate attachedTo=0 |
| «dos tostadas, una con palta y otra con queso» | tostada Count(2); palta attachedTo=0; queso attachedTo=0 (sin tostadas nuevas) |
| «2 huevos y 2 tostadas» | huevo Count(2); tostada Count(2) |
| «eran 150 g, no 200» (tras una mención) | la mención anterior queda con Mass(150) |
| «un poco de arroz» | arroz, Vague |
| «I had 2 slices of toast and a coffee» | tostada Household(2, rebanada); café Count(1) o sin cantidad |
| «almorcé arroz con pollo» con «arroz con pollo» en el léxico | un solo borrador (plato) |
