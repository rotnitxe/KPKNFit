# Food knowledge asset, version 1

`android-native/app/src/main/assets/food_data/food_knowledge_v1.json` holds the raw food knowledge of the natural-language
pipeline: the lists and tables that used to be spread over Kotlin files. It is maintained by hand (there is no generator). The Kotlin
code keeps the logic and everything derived from the tables (spellings, sets, compiled regexes). This holds increments 1 and 2 of WP-N13
(sections 1 to 3 of the plan); the other sections move in later increments.

```json
{ "version": 1, "sections": { "protectedPhrases": {...}, "typos": {...}, "synonyms": {...}, "householdUnits": {...},
                              "containers": {...}, "utensils": {...}, "densities": {...}, "dishCompositions": {...} } }
```

## Loading

`NutritionRepository` reads the file once, in the start phase that prepares the semantic dataset (`FoodKnowledgeStore.load`), and
calls `FoodKnowledge.install(snapshot)`. Any failure (missing file, malformed JSON, another `version`, an invalid value) is logged
once and the Kotlin default stays in force: a bad asset never installs half a table.

`FoodKnowledge.current()` is what every consumer reads, at the moment it uses a table. What a consumer derives from a table is
rebuilt when an install changes the content (`KnowledgeCache`, memoized by snapshot instance), so an install is valid at any time,
also after the pipeline has been used. Installing a snapshot equal to the one in force changes nothing and rebuilds nothing.

## Sections

| Section | Fields | Read by |
|---|---|---|
| `protectedPhrases` | `entityLiterals` (dishes named with a connector, as written), `fixedCompoundNames`, `filledHeads` (each makes "<head> de jamón y queso" and its mirror), `maizeProducts` (each makes "<product> de maíz"), `flavors` (every ordered pair is a flavour pair), `flavoredHeads`, `catalogExtraPhrases` | `ProtectedPhrases`, so `TextNormalizer`, `FoodParser`, `MassBoundDish` |
| `typos` | wrong spelling -> right one. A key with a space is a multi-word typo, masked before the single-word pass; the longest key goes first and equal lengths keep file order | `TextNormalizer` |
| `synonyms` | regional word -> standard word, never inside a protected phrase | `TextNormalizer` |
| `householdUnits` | `unitGramsByToken` (weight of ONE piece by head noun, accent-free), `notAWholePiece` (qualifiers: "cherry", "seco"), `countableFamilies`, `countableNameMarkers`, `familyDefaultGrams` (default portion by family; the breads are not here: a bread is a piece) | `HouseholdPortions` |
| `containers` | `words` (the ways to name a container, in the order they are tried: `id`, `pattern` = the regex of the words with the count, matched case-insensitively as a whole word, and an optional `fraction` of one container: "media botella" is 0.5), `contentByContainer` (container id -> food class or `default` -> ml for a drink, g for a solid), `liquidContainers`, `foodClasses` (ordered: the first class with a whole word in the accent-free food key wins) | `SubjectivePortionEngine` |
| `utensils` | `defaultMl`: volume of the utensils the logger lets a person edit | `SubjectivePortionEngine.UTENSIL_DEFAULTS` |
| `densities` | `gramsPerMl` (all ten categories), `rules` (first match wins: `contains` are substrings of the lower-case name, `words` whole words), `fallbackCategory` | `SubjectivePortionEngine` |
| `dishCompositions` | `dishes` (the dishes the combination parser knows by name, see below: `name` and `components`, each one a `food`, a `proportion` and a `role`) and `recipeNotes` (the recipes of the RECIPE_ESTIMATE rows of the static catalog: a `foodId` and its `ingredients`, each a `name`, a `profile` and a `percent`) | `FoodCombinationParser` (so `TagResolution` and `MassBoundDish`); `recipeNotes` is read by nothing yet |

Not in the asset yet, on purpose: the other regex tables of `SubjectivePortionEngine` (utensil, body, bread, subjective, scoop and
comparison patterns: they carry the ml and grams of each pattern, and "plato grande" derives from the plate), `STANDARD_PORTIONS`,
the by-name pieces of `HouseholdPortions.unitGrams` (marraqueta, hallulla, ...) and its energy-dense marker lists, `EN_ES_MAP` and
the number words. `FoodCombinationParser` keeps in Kotlin the sandwich grammar (the fillings clause, its separators and limits), the
generic combination patterns ("A con B", "A de B", ...), the confidence of each reading and the role inference: the asset holds its dish
table only. The sections of the plan that follow (cooking yields and factors, aliases, heuristic profiles) arrive one per increment.

## Dish compositions

`FoodCombinationParser.parse` first looks for a known dish in the text. When it finds one, `TagResolution` shares the grams of the plate
among the foods the description names by the proportions of the dish, except the foods whose amount the person said. This is what the
section holds, and how the parser reads it:

- A dish is its `name` and a list of `components`. The first component is the base: the parser reads its `food` and `proportion`
  (`baseFood`, `baseProportion`); the others are the accompaniments, with their `role`. The role of the base is kept as the table had it
  and nothing reads it: `TagResolution` takes the base as a plain share.
- The `food` of a component is a word of a person ("papa", "jamón", "puré", "huevo frito"): the resolver reads it like any other mention.
- The `proportion` is the share of the plate, greater than 0, and the proportions of a dish add up to 1 within 0.01 (the table holds 1
  exactly). The `role` is one of `SIDE`, `STARCH`, `SAUCE`, `TOPPING`, `FILLING`, `GARNISH` (`FoodCombinationParser.Role`); the resolver
  caps a `SAUCE` at 15 g (an oil, a butter, a mayonnaise) or 30 g.
- A name is matched as a whole phrase, case-insensitively and WITHOUT folding accents, and only where it ends the text or a connector
  follows ("con", "y", "e", "mas", "más", "sin", "a", "al", "de" or a comma): "arroz con leche condensada" is not "arroz con leche". A
  dish that is written with and without an accent is two entries ("pan con jamon", "pan con jamón"). Names are lower case with single spaces.
- The order matters: a text takes the longest dish it holds and, between two dishes of the same length, the one listed first. The names
  that start with "sandwich" or "sándwich" are the known sandwiches: `sandwichMentions` takes the known dish or the "sándwich de ..." clause,
  whichever starts first (the dish when both do).
- `MassBoundDish` reads the dishes through `FoodCombinationParser.parse` and remembers what it found per snapshot, so an install that adds
  or removes a dish takes effect there too.
- `recipeNotes` documents the static catalog rows whose `source` is "RECIPE_ESTIMATE" (gen183-gen188 and gen196) for the UI to show the
  assumption where the row is shown. `profile` is where the nutrient profile of an ingredient comes from, written as the `sourceRecordId` of
  the row writes it (a catalog id, `FDC <id>` or `agua`) and `percent` is its share of the weight; `name` is what the comment of the row
  calls it. `FoodKnowledgeAssetTest` checks every note against the row it describes (the record id is the note, word for word).

## Adding or changing an entry

1. Edit the JSON. Words the code compares with an accent-free key (`unitGramsByToken` keys, `notAWholePiece`,
   `countableNameMarkers`, the words of a `foodClasses` entry) are accent-free lower case; a `foodClasses` word is a-z only.
   Typos, synonyms, protected phrases and `contains` tokens are lower case, accents as a person types them.
2. While the Kotlin default exists, make the same change in `FoodKnowledgeDefaults.kt` (the parity rule below).
3. Run `FoodKnowledgeParityTest`, `FoodKnowledgeAssetTest`, `FoodKnowledgeInstallTest` and the `domain.nutrition` suite. A new
   typo, unit weight or container row needs a regression case in the test that owns the behavior. A container word is a regex: it
   ends in an ASCII letter (the edges are a plain `\b`), and its container needs a row in `contentByContainer`.
4. A new dish goes next to its family, with the proportions adding up to 1 and both spellings when its name has an accent. A dish is not
   a protected phrase: the description parser still cuts "arroz con pollo" at its "con" and the proportions divide the grams afterwards. A
   dish that must stay ONE food (a catalog row such as "pastel de choclo") belongs in `protectedPhrases` as well. A new RECIPE_ESTIMATE row
   of the catalog needs its `recipeNotes` entry.

## The parity rule

While `FoodKnowledgeDefaults` exists, the asset must equal it entry by entry and in the same order (`FoodKnowledgeParityTest` walks
every field by reflection), so that the app behaves the same whichever is in force. A later increment deletes `FoodKnowledgeDefaults`
and its parity test, and the asset becomes the only copy. Before that, move the install to the very start of the app (it runs today
in the phase that prepares the semantic dataset): once the default is gone, nothing covers the time before the install.

## Validation

`parseFoodKnowledge` is strict: the exact set of keys at every level, the right type, no blank string, no repeated list entry, every
number finite and greater than 0, the density categories exactly those of the enum, container rows with a `default` and only known
classes, container words that name a container of the table and hold a regex that compiles, every rule able to match, dish names that
are unique, dishes with at least one component, roles that exist in `FoodCombinationParser.Role` and proportions that add up to 1 within
0.01 for every dish, a share of a recipe that is at most 100.

`FoodKnowledgeAssetTest` adds what only a person gets wrong: a repeated JSON key (the parser would keep the last), densities
between 0.2 and 1.2 g/ml, plausible weights, accent-free keys, every food id the asset names being a row of the static catalog,
dish names and foods in lower case with single spaces, a base and at least one accompaniment in every dish, every proportion at most 1,
recipe notes that say what the catalog rows say, and a size under 300 KB (about 75 KB today).