---
flags: [nutrition]
---

# Nutrición: plato + porción de hogar (pipeline, no parches por comida)

**Fecha:** 2026-09-02  
**Estado:** en construcción (Cursor 2026-09-02) — S7 latencia + S8 motor de contexto inferido añadidos al alcance.  
**Evidencia:** corpus chileno en emulador (50 frases) + dump JVM del resolver (`TagResolver` + `parseMealDescription`) sobre 44 frases de plato compuesto / porción subjetiva / Latam-Europa-Asia + 2 corridas E2E de compuesto en dispositivo (`arroz con pollo y ensalada` 561 kcal, 3 ítems; `yogurt con granola y fruta` 662 kcal, 3 ítems).

Éxito = el diario registra **una comida humana**: identidad de plato o de ingrediente (no SKU), gramos de lo comido, kcal creíbles. Guardar verde no cuenta.

Fuera de alcance de este plan: wizard TDEE, OFF/USDA packs (ya filtrados), añadir 200 platos al mapa `KNOWN_DISHES`, iOS/backend salvo contratos de test.

---

## Por qué no es un parche por alimento

Los fallos de hallulla/queso, porotos granados, avena, tacos, ramen y “un puñado” **no son comidas distintas**. Son los mismos 6 modos del pipeline, reproducibles con comida chilena, mexicana, andina, italiana, asiática e inglés.

| Modo | Qué hace el código hoy | Evidencia (no es la lista de comidas a “arreglar”) |
|---|---|---|
| **S1 Plato vs receta** | `splitByListConnectors` parte en `con`/`y`. `PROTECTED_ENTITY_PHRASES` **excluye** nombres de catálogo que contienen `" con "` salvo un whitelist corto. `FoodCombinationParser.KNOWN_DISHES` trata el plato nombrado como **lista de ingredientes**. | `mote con huesillo` → 2× `cl005` 300 g (440 kcal UI). `porotos con riendas` → `gen135` + `cl029`. `pan con palta` → pan genérico + palta, ignora `cl025`. `sandwich de jamon y queso` → `cl036` Pavita + cheddar. |
| **S2 Densidad ≠ gramos comidos** | `FoodItem.servingSize` (a menudo 100 g de ficha) se usa como porción. `HouseholdPortions.defaultGrams`, `getContextualDefaultServingSize` y el drawer **no coinciden**. | JVM `avena` 120 g secos → 467 kcal; dispositivo 100 g → 389. `gauda` JVM 30 g / 121 kcal vs dispositivo 403 kcal (100 g). `granola` en yogurt 100 g → 471 kcal. `sopaipillas pasadas` 60 g / 112 kcal vs ficha de plato 150 g / 280. `ave palta` 80 g / 156 vs ficha 180 g / 350. |
| **S3 Estado (seco/cocido/método)** | Familia `avena` aplica densidad de hojuela seca a “bowl”. `pollo a la plancha` y `poyo` eligen filas y gramos distintos. | `un bowl de avena` = 120 g `gen011` secos. `arroz frito con pollo` usa arroz blanco cocido (no plato frito). |
| **S4 Subjetivo no acotado por clase** | Motor subjetivo existe (`plato` 250 g, `poco` 0.15×, `montón` 2×, `tajada` 40 g) pero no gana a serving 100 g / default de familia, y los contables están incompletos. | `2 tacos` → 100 g (menos que `taco de pollo` 150 g). `un punado de almendras` → 100 g / 579 kcal (un puñado es ~30 g). `un monton de papas fritas` → 100 g (ignora montón). `bastante pasta` → 160 g default. `media porcion de pizza` → 100 g enteros. |
| **S5 Plato innominado en catálogo / lookup** | Heurística `MIXED_DISH` ~160 kcal/100 g. `findFoodByNormalized` falla si el parser deja el tag singularizado (`poroto granados`) aunque exista `cl007`. | `porotos granados` → Estimado 116 kcal (UI y JVM). `pad thai`, `ramen`, `curry de pollo` → `NO_RESOLVED` 100 g. `arepa con queso` → pan blanco, no arepa. |
| **S6 Dos autoridades de gramos** | JVM `TagResolver` ≠ drawer en vivo para el mismo texto. | `cafe con leche` dispositivo 61 kcal vs JVM 200 g leche / 122 kcal. `hallulla con queso` UI 613 vs JVM 331. |

No se “arregla porotos granados”. Se cambia la regla: **frase de plato del catálogo no se parte; 100 g de ficha no es un plato; un contable se multiplica; un plato desconocido es un plato, no 100 g**.

---

## Pipeline objetivo (una autoridad)

Orden inmutable, el mismo en parser, resolver y drawer:

1. **Integridad de frase** — ¿El texto (o un span) es un plato/preparación del catálogo local o un compuesto protegido? → **un tag**, una ficha. No partir por `con`/`y`/`de` dentro de ese span.
2. **Identidad** — ficha LOCAL de hogar. OFF/USDA unbranded sigue prohibido.
3. **Estado / densidad** — crudo vs cocido vs hidratado vs seco (avena, arroz, pasta, carnes). La densidad kcal/100 g vive en la ficha; **no es la porción**.
4. **Gramos comidos** — `AmountIntent` explícito > utensilio/subjetivo **× clase** > contable × unidad > default de clase de hogar (lonja, bowl cocido, plato, vaso). Nunca `servingSize` 100 g como “comí 100 g” salvo que el usuario lo pida.
5. **Macros** — escalar densidad × gramos comidos. Aceite/método solo si la ficha no es ya esa preparación.
6. **Combinación** — solo si el usuario listó **ingredientes** y **no** hay ficha de plato para el span. Redistribuir gramos no puede pisar gramos de hogar ya resueltos (regla actual incompleta).

`FoodCombinationParser.KNOWN_DISHES` deja de ser un recetario que pisa fichas `cl*`. Como máximo, es pista de roles cuando **no** hay plato.

---

## Fases

### F0 — Contrato por modo (tests primero, deben fallar por la razón del modo)

Corpus **por modo**, no “añadir hallulla otra vez”:

- **S1:** frase = nombre de ficha `preparacion`/`chileno` con `con`/`y` → `tags.size == 1` y `foodItem.id` de esa ficha (`cl005`, `cl025`, `cl029`, `cl007`, `cl023`, `cl037`, `cl022`…). Contraste: `arroz con pollo y ensalada` **sí** puede ser 3 tags (ingredientes listados, sin ficha de plato único).
- **S2:** `gauda` / `queso` sin gramos → lonja 25–40 g, no 100 g. `avena` / `un bowl de avena` → no 100–120 g secos (467 kcal). `granola` como topping ≠ 100 g.
- **S3:** bowl/avena usa densidad de gacha o gramos secos de desayuno (~35–45 g). `pollo a la plancha` y `poyo` misma familia, gramos de hogar coherentes.
- **S4:** `2 tacos` ≥ 2 × unidad taco. `un puñado` de frutos secos ~25–40 g. `un plato de lentejas/arroz` ~200–350 g cocidos. `media porción de pizza` < porción entera de ficha.
- **S5:** `porotos granados` → `cl007` (lookup tras normalizar). Plato desconocido (`pad thai`, `ramen`) → estimación de **plato** (gramos de plato, no 100 g MIXED_DISH) o `NO_RESOLVED` saveable sin fingir 100 g.
- **S6:** el mismo `resolveAll` alimenta el drawer; un test de puerto que el staticFood del logger no pueda elegir 100 g si el resolver dijo 30 g.

Compuerta: tests nuevos **rojos por el modo citado**, no por “falta pad thai en el mapa”.

### F1 — Integridad de frase (S1)

- Dejar de filtrar `" con "` fuera de `PROTECTED_ENTITY_PHRASES`. Proteger **nombres y alias de fichas de preparación** (tag `preparacion` / multi-palabra de catálogo), no un whitelist eterno.
- Partir `con`/`y` solo fuera de spans protegidos.
- Si `householdStaticFood(frase completa)` o match exacto de plato existe, **un ítem**; no ejecutar redistribución `KNOWN_DISHES` sobre ese ítem.
- `KNOWN_DISHES` no gana a ficha `cl*`. El recetario no duplica `cl005` en dos líneas.

Archivos: `FoodParser.kt` (`PROTECTED_ENTITY_PHRASES`, `splitByListConnectors`), `TagResolution.kt` (bloque `combination` / `isSingleTagPlate`), `FoodCombinationParser.kt` (precedencia plato vs receta).

### F2 — Dos escalas: densidad vs comido (S2 + S6)

- Contrato en `FoodItem` o wrapper: `basisGrams` (casi siempre 100) vs `householdDefaultGrams(clase)`.
- Una función `eatenGrams` (ya es `HouseholdPortions.resolveEatenGrams`) como **única** autoridad; drawer/templates/buscar no leen `servingSize` crudo.
- Clases mínimas: contable, lonja/queso, grasa/utensilio, vaso/leche, cereal seco vs gacha, grano cocido, plato/preparación, puñado/frutos secos, topping.
- Alinear `defaultGrams` y `getContextualDefaultServingSize` (hoy avena 120 vs 40).

Archivos: `HouseholdPortions.kt`, `MacroCalculator.kt`, `FoodLoggerDrawer.kt`, `TagResolution.kt`.

### F3 — Subjetivo × clase y contables (S4)

- `2 tacos` / `una arepa` / `un sushi` entran en contables por **clase**, no por lista de nombres chilenos.
- Utensilio (`taza`, `plato`, `bowl`, `puñado`, `tajada`) gana a serving 100 g.
- Intensificadores (`bastante`, `montón`, `poco`) multiplican el default de **clase**, no un 100 g genérico.
- `media porción` = 0.5 × default de esa ficha/clase.

Archivos: `SubjectivePortionEngine.kt`, `HouseholdPortions.kt` (`COUNTABLE_*`), `FoodParser.kt` (intents).

### F4 — Plato desconocido y lookup (S5)

- Normalización (singular, alias) **no debe perder** el match `cl007` / nombres de preparación.
- `findFoodByNormalized`: preferir match de frase completa de catálogo antes que “todas las palabras ⊂ nombre”.
- Fallback heurístico de plato mixto: gramos de plato (~300–450 g) y fuente `HEURISTIC_ESTIMATE`, no 100 g × 160 kcal fingiendo ficha.
- No rellenar huecos con 200 entradas `KNOWN_DISHES` (arepa, pad thai, ramen). Identidad de plato o estimación de plato.

Archivos: `FoodDatabase.kt` (`findFoodByNormalized`), `TextNormalizer.kt` / parser de tag, `NutritionHeuristicEstimator.kt`.

### F5 — Dispositivo

- Corpus emulador: compuestos (`X con Y y Z`), subjetivos, Latam/EU/Asia, inglés corto (`chicken and rice` ya resuelve en JVM).
- No guardar 1 kg ni packs al diario.
- Misma tabla de modos S1–S6, no “pasar 50 Guardar”.

---

## Rutas

- Parser / protección: `android-native/app/src/main/java/com/example/kpkn/domain/nutrition/FoodParser.kt`
- Combinaciones: `.../FoodCombinationParser.kt`
- Resolución: `.../TagResolution.kt`, `.../HouseholdPortions.kt`, `.../SubjectivePortionEngine.kt`, `.../FoodIdentity.kt`
- Lookup: `android-native/app/src/main/java/com/example/kpkn/data/food/FoodDatabase.kt`
- Escalado: `.../MacroCalculator.kt`
- UI: `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/components/FoodLoggerDrawer.kt`
- Tests: `android-native/app/src/test/java/com/example/kpkn/domain/nutrition/` (`EverydayMealCorpusTest.kt` se **extiende por modo**, no se sustituye por una whitelist de 200 golden strings)
- Sin Room / sin `flags: [room]` salvo que F2 exija campo persistido (no es necesario: defaults son de dominio)

---

## Impacto

- Deja de partir platos `cl*` y de usar 100 g de ficha como almuerzo.
- Usuarios Latam/Europa/Asia se benefician **sin** un catálogo infinito: la regla es plato vs ingredientes vs clase de porción.
- Riesgo de regresión: `arroz con pollo` (2 ítems) y `2 huevos y pan` deben seguir siendo listas de ingredientes. F0 incluye esos contrastes.
- `KNOWN_DISHES` enorme deja de crecer como “producto”.

---

## Pruebas

Línea base a no degradar: corpus cotidiano actual (hallulla, 200 g pechuga, anti-pack OFF).

Nuevas (F0, por modo):

1. Integridad: `mote con huesillo`, `porotos granados`, `porotos con riendas`, `pan con palta`, `arroz con leche`, `ave palta`, `sopaipillas pasadas` → un id de ficha + gramos de plato.
2. Contraste ingredientes: `arroz con pollo y ensalada`, `pan con tomate y aceite`, `yogurt con granola y fruta` (topping granola ≠ 100 g).
3. Clase: `gauda`, `un poco de queso`, `una tajada de queso`, `avena` / `un bowl de avena`, `un punado de almendras`.
4. Contable/subjetivo: `2 tacos`, `un plato de lentejas`, `dos tazas de arroz`, `media porcion de pizza`.
5. Innominado: `pad thai`, `ramen con huevo` no son 100 g MIXED_DISH silenciosos.
6. Locale: `chicken and rice` no debe romper el contraste de ingredientes; no se exige NLP completo EN/PT en esta fase.
7. Emulador F5: reabrir logger por Home → REGISTRO DE HOY → Agregar comida (no tab Nutrition/wizard, no Reanudar sesión).

Comandos:

- `powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.EverydayMealCorpusTest'"`
- Suite nutrición + `installBaseDebug` en `emulator-5554` al cerrar cada fase de producto.

---

## Riesgos

- **Proteger demasiado:** “arroz con pollo” no debe colapsar a un plato inventado. Mitigar con: ficha de catálogo o usuario listó ≥2 alimentos comunes sin ficha única.
- **Regex de protección:** `staticFoodPhrases()` es grande; no reintroducir el filtro `" con "` “por rendimiento” sin medirlo.
- **Doble conversión** crudo/cocido (plan 2026-08-16): F2/F3 no reactivan yield sobre fichas ya cocidas.
- **Emulador:** overlay de bienvenida, sesión en curso y wizard de Nutrition tab rompen probes; F5 documenta la ruta Home.
- **No inflar `KNOWN_DISHES`:** cualquier PR que añada platos uno a uno sin pasar por F1/F2 se rechaza en auditoría de alineación.

### F6 — S7 latencia: un retrieve por análisis

- `parseMealDescription` reutiliza el snapshot del análisis; no llama `retrieve` por fragmento.
- `TagResolver` no hace `retrieve(item.tag)`. Co-ocurrencia tokeniza el texto.
- Fallback heurístico de plato no dispara retrieve.
- Contador `SemanticPortionRetriever.retrieveCount` ≤ 2 en un análisis de 3 ítems.

### F7 — S8 contexto inferido (no “siempre almuerzo”)

- `InferredMealContext` + `AmountIntent.INFERRED_CONTEXT` cuando no hay gramos ni utensilio.
- Señales: léxico `ContextDetector` > slot `MealType` > forma (MAIN_PLATE / BREAKFAST_BOWL / SANDWICH / BEVERAGE / WRAP / SNACK_ITEM).
- UI: “Asumí comida/desayuno/colación/bebida”. `queso` suelto no es comida. `café con leche` sigue bebida aunque el slot sea LUNCH.
- Presupuesto MAIN_PLATE usa `MealContext.portionFactor` (SNACK 0.5 vs LUNCH 1.1), no medianas del dataset.

---

## Definición de terminado

Los modos S1–S8 tienen test verde **por la regla**, no por un alimento. Dispositivo: un compuesto, un subjetivo y un plato `con` coinciden con JVM ± tolerancia de gramos. Guardar sigue exigiendo identidad de hogar + gramos plausibles. Este archivo de plan no se edita en la construcción salvo frontmatter de flags si Room se volviera inevitable (no previsto).
