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
