# Sistema D — Alimentos, nutrientes y funciones de IA (búsqueda, recetas, etiquetas)

**Fuente:** `nu_libapp.so` (Nutrition 5.8.9, arm64). Digests: `re/digests/D_nu.txt` (374 clases), `D_nu_skel.txt`, `D_nu_fns.txt`.
**Prefijos:** `ai_features/*`, `services/food*`, `services/search*`, `services/recipe*`, `services/product_image_import*`, `services/open_food_facts*`, `services/calomeal*`, `models/food*`, `models/nutrient*`, `constants/nutrients*`, `nutrient_ranges*`, `nutrient_calculation*`.

---

## D.0 Enumeraciones (OBSERVADO)

| Enum | Valores (nombre → JSON) |
|---|---|
| `FoodSource` | typesense "t", nutritionix "x", myNetDiary "m", openFoodFacts "o", calomeal "a", custom "c", nan "n", scanner "s" |
| `FoodDbOption` | typesense "ts", calomeal "ca" |
| `FoodType` | generic, brandedInPackage "branded_in_package", brandedNotInPackage, restaurant, notAFood "not_a_food" |
| `NutrientCategory` | caloriesAndMacros, carbBreakdown, proteinBreakdown, fatBreakdown, vitamins, minerals, other, calories, macros, pinned |
| `NutrientUnit` | (índice 0 eliminado), volume "Volume", calories "kcal", ml, cup "Cup", tbsp, tsp, floz "Fl Oz", mcg, iu "IU", mg, (11-12 eliminados), g |
| `NutrientCalculationMethod` | per1000Kcal, flatNumber, percentageOfTotalEnergyAsFat, percentageOfTotalEnergyAsCarbs, perWeightKilo |
| `AiFeature` | foodSearch "ai_food_search", recipeImport "ai_recipe_import", productImageImport "ai_product_image_import", weightStackScan "ai_weight_stack_scan" |
| `RecipeAiImportMode` | link, html, textOnly, imageAndText, imageOnly |
| `Nutrient` | 58 valores con códigos USDA (ver `D_nu_skel.txt`); además `SexPregnancyLactation`, `AgeGroup`, `PerSize` |

## D.1 Modelos de alimento y nutrientes

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| `Food` tiene nombre, fuente, unidad de ración seleccionada y medidas alternativas; se serializa omitiendo nulos y ceros. | OBSERVADO | Getters `foodName`, `source`, `selectedServingUnit`, `getAltMeasures`; closures `processMeasure`, `writeNotNull`, `writeNotNullAndNotZero`; estático `Food.empty`. |
| `AlternativeMeasure` por defecto: 100 ml ↔ 100 g (no por defecto marcada). | OBSERVADO | Instancia const `(100.0, "ml", 100.0, false)`; getter `measure`. |
| Los micronutrientes se construyen desde mapas por código o índice. | OBSERVADO | `fromNumToMicronutrient(Map, int)`, `(Map, String, {double? nutrientValue})`, `(String, double?)`; `Micronutrient.code/name`. |
| Normalización de nutrientes a gramos listados o a ración sin peso. | OBSERVADO (firma) | `getNutrientNormalizedToListedGramsOrServingWithoutWeight(num?, num?) -> double?`. |
| Hay conjuntos de nutrientes «macro fijos», ignorados y un índice por código. | OBSERVADO | Estáticos `_fixedMacroNutrients`, `_ignoredNutrientMapCodes`, `_nutrientsByCode`, `_macroNutrients`; predicados `_isMassNutrientQuantity`, `_isEnergyNutrientQuantity`. |
| Los objetivos de micronutrientes dependen de sexo/embarazo/lactancia y grupo de edad, y se calculan por 1000 kcal, fijos, % de energía (grasa/carbohidrato) o por kg. | OBSERVADO (estructura) / INFERIDO (uso) | `nutrientRanges: Map<Nutrient, Map<SexPregnancyLactation, Map<AgeGroup, NutrientCalculation>>>` (inicializador estático) y `NutrientCalculationMethod`. |
| Entradas de nutrición diarias con origen de datos y fecha. | OBSERVADO | `NutritionEntry` (`source`, `timestamp`, `name`, `dataOrigin`), `MicroNutritionEntry`, `MicroGoalEntry`, `FoodBundle`. |

## D.2 Búsqueda de alimentos (Typesense, OFF, Calomeal)

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| La búsqueda principal usa Typesense con tres colecciones (`branded_foods`, `common_foods`, `off_foods`) y cabecera `x-typesense-api-key`. | OBSERVADO | Cadenas `/collections/{…}/documents/` y la cabecera en el pool; `TypesenseHttpClient` (`typesenseHttpClientProvider`, asíncrono), `TypesenseDocumentFetchRequest`, `TypesenseFoodNameQueryRequest`. |
| La clave y host de Typesense se obtienen en tiempo de ejecución (no están en el binario). | INFERIDO | El proveedor del cliente es `Future` y no hay cadenas de host/clave visibles. |
| Códigos de barras: fuente primaria Typesense, secundaria ninguna (configuración por defecto). | OBSERVADO (JSON) | `{"primary_barcode_source":"typesense","secondary_barcode_source":"none"}` en el pool. |
| Incluir Open Food Facts y marcas en la búsqueda son opciones conmutables; existe una fecha de corte a partir de la cual se prefiere OFF. | OBSERVADO (providers) / INFERIDO (semántica) | `includeOpenFoodFactsSearchProvider`, `includeBrandedSearchProvider` (`Future<bool>`), estático `offPreferredCutOffDate: DateTime`. |
| Integración OFF: repositorio, usuario OFF, ayudante de URL anglo/no-anglo, mapeo de nutrientes a etiquetas OFF. | OBSERVADO | `OffRepository`, `offUserProvider`, `UriProductHelper` (`AngloProductHelper`/`NonAngloProductHelper`), `tagFor(Nutrient, PerSize) -> String`, `_reverseMap`, cadena `https://openfoodfacts.org/`. |
| Base japonesa Calomeal vía función en la nube propia. | OBSERVADO | Cadena `https://calomeal.fn.macrofactor.com`; `CalomealSearchService`, `FirebaseCalomealCallables.call(String, Map<String, Object?>, {Duration timeout})`, `normalizeCalomealQuery`, `_calomealApostrophes`, `getCalomealServingUnitAndWeight`, `CalomealSearchFailure`. |
| Ids de producto bien conocidos mapeados por (código, país). | OBSERVADO (tipo) | `wellKnownProductIds: Map<(String, String?), String>`. |
| Enlace profundo para compartir alimentos personalizados. | OBSERVADO | `https://link.macrofactor.app/custom-food?userId=`. |

## D.3 Pipeline de IA («describe» / búsqueda con LLM)

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Cuatro funciones de IA con contador de uso y límites. | OBSERVADO | `AiFeature`; `aiUsageRepositoryProvider` (`UsageRepository<AiFeature>`), `aiUsageAggregateStreamProvider`; resultados `OverUsageLimitAiFeatureResult`, `IntegrityCheckErrorAiFeatureResult`. INFERIDO: la «comprobación de integridad» es Firebase App Check. |
| El LLM se invoca a través de plantillas de Firebase AI (`TemplateGenerativeModel`), por lo que los *prompts* no están en el binario. | OBSERVADO (clase) / INFERIDO (ubicación de prompts) | `AiExecutor.templatedGenerativeModel`, `TemplateModelRequest`, `TemplateLLMModelParameters` / `LLMParameters` (`toJson`). Nombre de modelo presente como cadena: `gemini-3.1-flash-lite-preview`. |
| La búsqueda IA es un pipeline de etapas: generar alimentos → consultar Typesense → seleccionar resultados → analizar alimento. | OBSERVADO (clases) / INFERIDO (orden) | Familias `GenerateFoodStageFamily`, `QueryFoodsStageFamily` (`TypesenseQueryFoods`, `StageConfig` "query_foods" con 10 s), `SelectedResultsStageFamily`, `AnalyzeFoodStageFamily`, `LlmSearchServiceFamily`, todas parametrizadas por `{AiFeature feature}`; `LLMSearchService.search({String sessionId, LLMSearchInput data}) -> Stream<AiFoodSearchResult>`. |
| Las respuestas del LLM se parsean como texto delimitado (DSV) y no como JSON libre. | INFERIDO (fuerte) | `deserializeDsvTextResponseSelectStrategy(GenerateContentResponse, SearchResults) -> SelectedResults`, `NutritionLabelFoodDsvParser`, `DsvServingSizeParser`, regex `_delimiter`, `stripPipeEndsRegex`, `delimeter`, `_parseListEntry(String) -> Iterable<ServingSize>`. |
| Si no hay coincidencia en base de datos, el LLM «analiza» el alimento y produce nutrientes (`AnalyzedFood(s)`), con raciones anidadas escalables. | OBSERVADO (clases) / INFERIDO (flujo) | `deserializeStrategy(GenerateContentResponse) -> AnalyzedFoods?`, `NestedFoodResponse.*(int)`, `ServingDescription.*(int)`, `FoodCandidate` → `convertFoodCandidates`, `selectedResultsConverter(Food, SelectedResult, SearchResult) -> Food`, `_addPseudoMassPatch(Food) -> Food?`. |
| Hay elegibilidad por fecha para la función «describe». | OBSERVADO (providers) | `isEligibleForAiDescribeProvider`, `eligibilityDateProvider`; `effectiveAiSearchConfigProvider` (`AiSearchConfig`). |
| Importación de recetas: parsers estructurados (RDFa, Microdata, LD-JSON) y, como alternativa, LLM (texto o imagen). | OBSERVADO (registro) / INFERIDO (orden de preferencia) | `recipeParserRegistryProvider -> List<RecipeParser>`: `RdfaSchemaRecipeParser`, `MicrodataSchemaRecipeParser`, `LdJsonSchemaRecipeParser`, `FirebaseRecipeParser`; `FirebaseRecipeImageParser`; `CrawlerClient`; `RecipeImportService.importRecipe({sessionId, RecipeImportDataType data}) -> Stream<AiFeatureResult>`; `RecipeAiImportMode`. |
| Importación por foto de etiqueta/producto con etapa de unidad de ración y mapas de unidades de volumen/masa. | OBSERVADO | `ProductImageImportService`, `foodServingUnitStageProvider`, `_volumeMap`, `_massMap`, `_fluidMeasurementSynonyms`, `AiPhotoUploadService`. |
| Las imágenes se envían al modelo en línea o por referencia remota. | OBSERVADO | `PipelineImageData` → `FirebaseInlineImage`, `FirebaseRemoteImage`, `XFilePipelineImageData` (`_fileContents: Uint8List`). |

## D.4 Lo que NO se pudo determinar

- Prompts y esquemas de salida exactos (viven en plantillas servidor).
- Host/clave de Typesense y el resto de la configuración remota.
- La tabla completa `nutrientRanges` (valores numéricos) — existe como inicializador estático ejecutable, no como datos; recuperarla exigiría emular el inicializador o ejecutar la app.
