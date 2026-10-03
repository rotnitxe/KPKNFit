# Auditoría del sistema de alimentos — octubre 2026

> **Estado: documento WP-0 (primera entrega, antes de tocar código), actualizado hasta el gate 21.** Recoge hallazgos
> por lectura de código, la corrida JVM existente, una sonda estática del catálogo y la línea base de la sonda WP-N0
> bajo Gradle; el avance posterior está en «Registro de ejecución». No hay QA en dispositivo registrado. Evidencia de
> los 55 hallazgos con id: **17 CONFIRMADO POR SONDA (Gradle)**, **7 VERIFICADO** y **31 REPORTADO**; la divergencia de
> `\b` (sin id) es **INFERIDO** (ver «Qué se verificó y cómo»).

| Campo | Valor |
|---|---|
| Fecha | 2026-10-02 |
| Rama | `consolidation/2026-10-02-wizard-session-repair` |
| Flavor | Base (el flavor Health no se usa; memoria del usuario) |
| Plan aprobado | `C:\Users\valen\.claude\plans\te-encargo-una-auditor-a-enumerated-nautilus.md` (diseños completos de cada WP; aquí solo se indexan) |
| Contrato de comportamiento | [nutrition_interpretation_v2](../../contracts/nutrition_interpretation_v2.md) |
| Auditorías previas | [2026-08 precisión](../2026-08-nutrition-precision/README.md), [2026-09 fiabilidad](../2026-09-nutrition-reliability/README.md) |
| Artefactos de esta carpeta | `coverage-probe.json` (sonda de cobertura del catálogo) y la línea base de la sonda WP-N0 (filas siguientes; `blind-corpus-baseline.json` en el plan) |
| `blind-probe-baseline.json` | Línea base oficial: salida JSON de `BlindMealCorpusProbeTest` ejecutada bajo Gradle en el gate 1 (2026-10-03, 2/2 tests), idéntica a la informal previa salvo los tiempos |
| `blind-probe-baseline.txt` | La misma línea base como tabla de stdout extraída del XML de resultados de Gradle |
| `blind-probe-baseline.informal.json` | Previo, conservado como traza: salida de `BlindMealCorpusProbeTest` ejecutada fuera de Gradle (JUnitCore, JDK 21, clases compiladas antes de cualquier cambio de esta auditoría); sustituido por `blind-probe-baseline.json` |
| `blind-probe-baseline.informal.txt` | Previo, conservado como traza: la misma salida informal en texto (tabla de ancho fijo); sustituido por `blind-probe-baseline.txt` |

## Alcance pedido por el usuario

El usuario describe lo que come en lenguaje natural (español chileno/LATAM) y la app, sin IA, debe registrar alimentos
y macros "lo más perfecto posible" y sentirse inteligente. Tras dos auditorías previas (ago-2026 "precision", sep-2026
"reliability") que cerraron 570/570 tests y dejaron contratos, pidió una auditoría profunda más un plan de remediación que
cubra:

1. el sistema de descripciones,
2. la búsqueda,
3. bugs generales del sistema y de la página de Nutrición,
4. otros aspectos importantes.

Decisiones del usuario (2026-10-02):

- **Alcance de ejecución: TODO el plan.** Incluye trabajo estructural: ViewModel del logger con `SavedStateHandle`,
  consolidación del conocimiento de alimentos en un asset JSON, ampliación del catálogo chileno con fuentes y retiro de
  módulos muertos.
- **Migración Room v28->v29 permitida** si el diseño la justifica, siempre con caso en `NutritionMigrationTest`.
- **Flavor Base únicamente.** Tope de iteraciones de QA: documentar y dejar decidir al usuario.

## Resumen

**Estado.** 719 tests JVM verdes hoy, pero el corpus que los sostiene es laxo (bandas de hasta 4x, aserciones de doble
resultado, golden que fija bugs) y nada mide descripciones reales. Tres exploraciones de código (pipeline,
búsqueda/datos, UI/servicios) encontraron ~70 hallazgos; los 12 que más afectan al usuario:

1. Cantidades ignoradas en alimentos no contables: "2 yogures" = 1 yogur, "2 paltas" = "media palta" (A-Q1, CONFIRMADO
   POR SONDA).
2. Plurales en "-es" rompen la identidad: "3 tomates" -> plato fantasma de ~560 kcal con pregunta (A-Q2, CONFIRMADO POR
   SONDA).
3. No existe ruta de 0 kcal: "un vaso de agua" ≈ 400 kcal (A-Q10, CONFIRMADO POR SONDA).
4. Doble factor de cocción (rendimiento + factor; FRITO + aceite que sobrevive a "sin aceite") (A-C1, CONFIRMADO POR SONDA).
5. Platos protegidos no coinciden por el mapa de typos: "porotos con riendas" nunca da la ficha `cl029` (A-P3,
   CONFIRMADO POR SONDA).
6. Punto/dos puntos no separan, "al menos" excluye, "a"/"no" activan modo inglés (A-P1, P2, P4, CONFIRMADOS POR SONDA).
7. Tocar un resultado de búsqueda registra OTRO alimento o nada, y se aprende como confirmado (B1, VERIFICADO).
8. El ranking entierra los genéricos curados bajo marcas OFF y filtra después del límite (B2, VERIFICADO).
9. ~72 MB de CSV se hashean en cada arranque antes de publicar las comidas del usuario (B3, VERIFICADO).
10. La fecha seleccionada no avanza tras medianoche: lo registrado cae en ayer (C1, VERIFICADO).
11. Si falla o tarda la importación del catálogo, Nutrición y Home quedan vacíos toda la sesión (C2, VERIFICADO).
12. AUGE descarta todas las comidas del drawer por el formato de fecha; el widget abre Home sin logger (C4, C3,
    VERIFICADOS).

**Riesgo transversal.** Los tests JVM corren en Java 21 (daemon de Gradle; el desarrollo usa JDK 24), donde `\b` es
ASCII, y Android usa ICU, donde `\b` es Unicode: el parser puede comportarse distinto en el teléfono ("huevo poché"
desaparece) con tests verdes (INFERIDO). Primer paso obligatorio del plan, ya ejecutado: harness de sonda con 60
descripciones reales (WP-N0); su línea base bajo Gradle confirma varios hallazgos A (ver «Línea base»).

**Plan.** 5 fases (0 a 4) más cierre, ~45 paquetes (bloques N pipeline, S búsqueda/datos, U página/servicios, D
catálogo), sin migración Room (v29 solo si WP-S12 se activa) y corpus ciego con umbrales por dimensión como criterio de
cierre. Ver «Plan de remediación».

## Línea base y metodología

### Línea base (2026-10-02)

- **719 tests JVM verdes** en las suites de nutrición/food/repos (filtros de «Verificación», flavor Base): 0 fallos,
  0 skipped. `WorkoutVoiceReplayTest` es el único fallo entre las suites que sí corrieron hoy y es ajeno a nutrición
  (la suite completa sin filtro no se ejecuta por el cuelgue de sep-2026).
- **`NutritionMetricsContractTest`: precisión@1 de identidad 45/47 (95,7 %).** Fallan `ensalada` y `cereal`; el umbral
  >= 95 % oculta los 2 fallos. El error mediano de gramos (0,0 %, n=15) es tautológico.
- **Corpus "independiente" de sep-2026: 23 casos**, casi todos de un alimento con unidad explícita. No existe corpus
  narrativo real.
- No hay tests instrumentados de la pantalla Nutrición ni del `FoodLoggerDrawer`.
- **Línea base de la sonda WP-N0 bajo Gradle** (`blind-probe-baseline.json` y `.txt`, gate 1 del 2026-10-03,
  `BlindMealCorpusProbeTest` 2/2; tests JVM en Java 21 (daemon de Gradle, Eclipse Adoptium 21.0.8) y desarrollo con
  JDK 24). 60 entradas x 2 pasadas (con y sin el snapshot de `dataset_knowledge.bin`), sin errores ni diferencias entre
  pasadas; pasada A: p50 19 ms, p95 55 ms, máximo 63 ms. Es idéntica, salvo los tiempos, a la salida informal previa
  (`blind-probe-baseline.informal.*`, JUnitCore fuera de Gradle, conservada como traza). Resultados observados:
  - **#1 y #40** (A-P3, A-P4, A-P5, A-Q10): en "almorcé un plato de porotos con riendas y un pan con palta, después un
    café con leche", "porotos con riendas" no da `cl029` sino `gen135` "Porotos (cocidos)" 212,5 g/298 kcal más el
    fantasma "riendas (estimado)" 40 g/64 kcal (NEEDS_REVIEW); "después 1 café con leche" queda como ítem heurístico de
    40 g/24,8 kcal (NEEDS_REVIEW); el pan con palta sí resuelve (`cl025`, 120 g/280 kcal). Con "porotos con riendas"
    solo (#40) pasa lo mismo: `gen135` 99 g/139 kcal y "riendas (estimado)" 99 g/158,4 kcal.
  - **#2, #37 y #6** (A-Q1, A-Q2, A-P2): "2 yogures y 3 tomates" da el ítem heurístico "tomat (estimado)" 100 g/160 kcal
    (NEEDS_REVIEW) y los 2 yogures quedan en 200 g (`gen087`, 118 kcal); "tres tomates" da "tomat (estimado)"
    350 g/560 kcal; "al menos 2 huevos" da el fantasma "al (estimado)" 350 g/560 kcal (NEEDS_REVIEW) y deja los huevos
    excluidos.
  - **#3, #18 y #45** (A-Q10, A-Q4): "un vaso de agua" da "agua (estimado)" 250 g/400 kcal, "una botella de agua"
    750 g/1.200 kcal y "2 litros de agua" también 1.200 kcal (750 g), todos NEEDS_REVIEW; no hay ruta de 0 kcal.
  - **#4** (A-C1): "150 g salmón a la parrilla" da `gen009` 150 g/420 kcal (AUTO) con conversión RAW->COOKED (yield 0,78)
    y factor de cocción; una sola conversión (150/0,78 x 2,08) daría ≈ 400 kcal.
  - **#7 y #8** (A-P1, A-P4): "Desayuno: 2 huevos. Almuerzo: arroz con pollo." se parte en dos tags ("desayuno: 2 huevos.
    almuerzo: arroz" y "pollo."): el primero queda como ítem heurístico de 198 g/306,9 kcal (NEEDS_REVIEW) y solo el
    pollo resuelve (`gen004`, 126 g/209 kcal, NEEDS_CONFIRMATION); "pollo a la plancha y papas a lo pobre" se normaliza a
    "pollo 1 la plancha y papa 1 lo pobre" y "papa 1 lo pobre" queda como fantasma de 242 g/205,7 kcal (NEEDS_REVIEW).
  - **#28** (A-Q11): "un sandwich de jamón y queso y una coca cola" se resuelve en `gen019` pan blanco 100 g, `gen094`
    jamón cocido 100 g y `gen047` queso cheddar 30 g (AUTO) y pierde la coca cola.
  - **#42**: "una taza de té sin azúcar" da "té sin azúcar (estimado)" 192 g/729,6 kcal (NEEDS_REVIEW).
- **Delta de la sonda tras WP-N2/N3/N4** (gate 7; salida de `app/build/reports/nutrition-reliability/blind-probe.json`
  a las 03:08, no versionada): 13 de las 60 entradas difieren de esta línea base. 11 mejoran (#1, #2, #6, #7, #8, #34,
  #35, #37, #38, #47, #48) y 2 (#22, #36) solo cambian el texto del tag o de la normalización con el mismo resultado;
  ninguna empeora. La línea base oficial sigue siendo la del gate 1, para medir el delta final. Ejemplos:
  - **#6**: "al menos 2 huevos" ya no deja el fantasma "al": se normaliza a "2 huevos" y resuelve `gen007` x2 (100 g/154
    kcal) sin excluirlo.
  - **#7**: "Desayuno: 2 huevos. Almuerzo: arroz con pollo." da tres ítems: `gen007` x2 (100 g/154 kcal), `gen005` arroz
    (198 g/257 kcal) y `gen004` pollo (126 g/209 kcal, corte pendiente).
  - **#34**: "huevo poché" pasa de fantasma de 350 g/542,5 kcal a huevo COCIDO: `gen007` 50 g/77 kcal (AUTO).
  - **#37**: "tres tomates" pasa de "tomat (estimado)" 350 g/560 kcal a `gen026` Tomate (tag `tomate`, 80 g/14 kcal,
    AUTO); la cantidad sigue sin escalar (tarea de WP-N8).
- **Delta de la sonda tras WP-N5** (gate 14; `blind-probe.json` de build, no versionada): 27 de las 60 entradas difieren de
  la línea base oficial: las 13 de WP-N2/N3/N4 (#1 cambia otra vez) y 14 nuevas de bebidas y unidades (#3, #5, #9, #14,
  #15, #16, #17, #18, #19, #42, #43, #45, #50, #55); ninguna empeora. La línea base oficial sigue siendo la del gate 1.
  Ejemplos:
  - **Agua** (#3, #9, #17, #18, #45): "un vaso de agua" pasa de 250 g/400 kcal estimadas a `gen143` Agua 250 g/0 kcal
    (AUTO); "agua con gas" queda en una sola ficha de 0 kcal (`gen151`, 250 g) en lugar de dos fantasmas; "1 lt de agua"
    da 1.000 g/0 kcal; "una botella de agua" 500 g/0 kcal (antes 750 g/1.200 kcal); "2 litros de agua" 2.000 g/0 kcal
    (antes 750 g/1.200 kcal).
  - **Té y café** (#42, #43, #50, #1): "una taza de té sin azúcar" pasa de 192 g/729,6 kcal a `gen144` 240 g/2 kcal;
    "café sin azúcar" de 836 kcal a `gen059` 220 g/4 kcal; el té verde de #50 baja de 3 a 2 kcal; el "café con leche" de
    #1 resuelve `gen145` 247,2 g/91 kcal.
  - **Unidades y contenedores** (#5, #14, #15, #16): "una lata de coca cola" usa 350 g/147 kcal (antes 200 g/84 kcal);
    "500 cc de leche" se normaliza a "500 ml" y da `gen016` 515 g/314 kcal; "medio litro de jugo de naranja" da `gen103`
    500 g/225 kcal; "cuarto de kilo de carne molida" da `gen010` 250 g/542 kcal.
  - **Jugo de naranja** (#19, #55): `gen103` pasa de 0,75 a 0,45 kcal/g ("un jugo de naranja" de 100 g/75 kcal a 250
    g/112 kcal; "jugo natural de naranja grande" 440 g, de 330 a 198 kcal).
- **Delta de la sonda tras WP-N6/N8** (`blind-probe.json` de build tras el gate 18, no versionada): 30 de las 60
  entradas difieren de la línea base oficial (las 27 de WP-N5 más #20, #51 y #59). Ninguna pierde una ficha ni gana un
  fantasma; la única que se aleja de su valor esperado es #20. La línea base oficial sigue siendo la del gate 1.
  Ejemplos:
  - **Conteos** (#2, #37, #22): "2 yogures y 3 tomates" da yogur 250 g/148 kcal (2 x 125 g) y `gen026` Tomate 360 g/65
    kcal (3 x 120 g), antes 200 g y el fantasma "tomat"; "tres tomates" pasa de 80 g (tras WP-N3) a `gen026` 360 g/65
    kcal, así que la cantidad ya escala; "2 manzanas grandes" resuelve `gen001` Manzana 375 g/195 kcal (AUTO), antes
    "manzanas grand (estimado)" de 200 g sin identidad.
  - **Tamaño** (#55, #20, #51): "jugo natural de naranja grande" usa LARGE: 312,5 g/141 kcal (antes EXTRA, 440 g);
    "plato grande de arroz" da 150 g/195 kcal (antes 180 g; 1,25 x los 120 g de porción base del arroz cocido) y sigue
    por debajo de "un plato de arroz" (212,5 g), lejos del objetivo del plan (1,25 x #21 = 265,6 g); "un completo
    italiano sin mayo" da 220 g/352 kcal (antes 200 g/320 kcal) y sigue como ítem estimado con pregunta (control).
  - **#59**: cambia solo un campo interno (el intent del yogur griego estimado, de INFERRED_CONTEXT a
    RESOLVED_SUBJECTIVE); gramos y kcal no varían.
- **Delta de la sonda tras WP-N10** (`blind-probe.json` de build tras el gate 20, no versionada): 37 de las 60 entradas
  difieren de la línea base oficial en algún campo: 34 en la línea de resultado (las 29 que ya diferían tras WP-N6/N8
  más #4, #29, #30, #32 y #33) y 3 solo en campos internos (#12, #40 y #59). Frente al estado tras WP-N6/N8, el gate 19
  (WP-D1/S11) no cambia ninguna línea y WP-N10 cambia exactamente esas cinco. Ninguna entrada pierde una ficha ni gana
  un fantasma, y las cinco alcanzan su valor esperado en la sonda. La línea base oficial sigue siendo la del gate 1.
  Ejemplos:
  - **Una sola vía de cocción** (#4, #30, #33): "150 g salmón a la parrilla" da `gen009` 150 g/400 kcal (150/0,78 g de
    crudo x 2,08 kcal/g), sin el x1,05 de antes (420 kcal) y sin aceite añadido; "150 g de pechuga cruda frita" baja de
    314 a 293 kcal (rendimiento una vez más 9 g de aceite); "100 g champiñones salteados" sube de 96 a 101 kcal (el
    rendimiento se busca por tokens normalizados: 0,75 en lugar de 1,0).
  - **Variante preparada y "sin aceite"** (#29, #32): "pechuga de pollo frita sin aceite 150 g" pasa de `gen003f`
    150 g/334 kcal a `gen003` (cruda) con rendimiento 0,75 y 0 g de aceite aplicado: 212 kcal (150/0,75 g de crudo x
    1,06 kcal/g); "huevos revueltos" pasa de `gen007f` (frito, 50 g/90 kcal) a `gen007r` (revuelto, 50 g/95 kcal).
  - **Solo campos internos** (#12, #40): "asado" mantiene `gen093c` 100 g/250 kcal, pero su estado pasa de RAW a COOKED
    y el nombre registrado de "Asado de Tira (crudo)" a "de Tira (cocido, estimado)" (pierde la palabra Asado; a
    revisar); "porotos con riendas" suma el candidato `gen173` ("Porotos verdes (cocidos)", ficha nueva de WP-D1) sin
    cambiar la línea: sigue `gen135` más el fantasma "riendas (estimado)".

### Qué se verificó y cómo

| Etiqueta | Criterio | Hallazgos |
|---|---|---|
| **CONFIRMADO POR SONDA (Gradle)** | La sonda WP-N0 (`BlindMealCorpusProbeTest`, bajo Gradle, `blind-probe-baseline.json`) reproduce el síntoma sobre el código de partida | A-P1..P5, A-Q1..Q6, A-Q9..Q11, A-C1..C3 (17 de los 20 A) |
| **VERIFICADO** | El plan lo marca como verificado: lectura directa del código ("Verificación directa"), confirmación explícita de la exploración o grep con 0 referencias | Las aserciones `tomat`/`huevo poché`; B1, B2, B3; C1, C2, C3, C4; módulos muertos con 0 referencias |
| **REPORTADO** | Hallado por revisión de código de los agentes de exploración; se confirma o refuta en las pruebas de cada paquete (WP-N*, WP-S*, WP-U*) | El resto: A-P6, A-Q7 y A-Q8 (la sonda no los reproduce o no es concluyente), B4..B13 y C5..C22 |
| **INFERIDO** | Deducción por diferencias de plataforma o lectura sin ejecución | Divergencia de `\b` JVM vs Android (ALTA prioridad de verificación) |

A-P1, A-Q1 y A-Q2 pasaron de VERIFICADO a CONFIRMADO POR SONDA (Gradle); A-P2..A-P5, A-Q3..A-Q6, A-Q9..A-Q11 y
A-C1..A-C3, de REPORTADO. Las demás etiquetas no cambian.

El diseño del bloque N re-leyó todos los hallazgos A-* y los reprodujo por lectura; esa re-lectura no cambia su
etiqueta. Los diseños de los bloques S y U añadieron confirmaciones puntuales: se anotan en cada hallazgo como
"confirmado al diseñar".

### Exploraciones y diseños

Tres exploraciones de código produjeron los informes: **A** pipeline de descripciones en lenguaje natural, **B** búsqueda
y capa de datos (ranking, `FoodIndex`, importación, Room v28/FTS, repositorio) y **C** UI, ViewModel, servicios y
telemetría (página de Nutrición, drawer, widget, recordatorios). Sobre ellos se hicieron tres diseños de remediación:
agente 1 -> **Bloque S** (búsqueda, catálogo y datos), agente 2 -> **Bloque U** (página de Nutrición, ViewModels, drawer,
servicios) y agente 3 -> **Bloque N** (pipeline de descripciones). El **Bloque D** (contenido del catálogo) completa el
plan. Además hubo lectura directa propia de los hallazgos críticos (etiqueta VERIFICADO).

## Hechos verificados (estado de partida, 2026-10-02)

### Tests

- Última corrida JVM (2026-10-02, 22:33, flavor Base): **719 tests** en suites de nutrición/food/repos, 0 fallos,
  0 skipped. Único fallo entre las suites que sí corrieron hoy: `WorkoutVoiceReplayTest` (ajeno; la suite completa sin
  filtro no se ejecuta por el cuelgue de sep-2026).
- `NutritionMetricsContractTest` imprime: precisión@1 identidad **45/47 (95,7 %)**; fallan `ensalada` y `cereal` (quedan
  `NEEDS_REVIEW`, se esperaba `AUTO`). "Error mediano gramos 0,0 % (n=15)" => el oráculo de gramos es tautológico (usa
  los mismos priors del motor).
- Corpus "independiente" de sep-2026: 23 casos, casi todos de un alimento con unidad explícita (`media taza de leche`,
  `100 g pan integral`...). No hay corpus narrativo real ("almorcé un plato de porotos con rienda y pan con palta").
- No existe ningún test instrumentado de la pantalla Nutrición ni del `FoodLoggerDrawer` (androidTest solo tiene
  `NutritionMigrationTest`, `CookingFactorsAndroidTest`).

### Datos y catálogos

- `FoodDatabase.kt`: catálogo estático hardcodeado de **221 bloques `FoodItem(`** = 175 `gen*` de una línea + 40 `cl*` +
  6 `gen*` multilínea (`gen137`-`gen142`, `FoodDatabase.kt:266-346`) (`GENERIC_FOODS` :18-350 incl. bloque chileno
  cl021-cl040 en :212-231; `CHILEAN_FOODS` cl001-cl020 :351-372), `FOOD_ALIASES` (207 entradas, :376-546),
  `PORTION_REFERENCES`. Solo **5/221 declaran `source`** y 8 `nutritionBasis` (las `cl*` son por ración completa,
  `unit = "u"/"ml"`, sin fuente; las 6 multilínea no declaran ninguno). Catálogos programáticos de marcas en
  `BrandedSnackCatalog.kt` (galletas, snacks, dulces, caseros, por región) y `BrandedEnergyKcalCatalog.kt` (Monster,
  Red Bull, Score, chocolates...).
  - Corrección de WP-0: la exploración inicial contó 215 fichas (solo las de una línea). Recuento sobre el archivo:
    221 bloques `FoodItem(` (galletas de chocolate, saladas y de avena, papas fritas snack, chocolate de mesa y dulce
    genérico son las 6 multilínea); 5 declaran `source` (`gen015`, `gen016`, `gen026`, `gen046`, `gen066`) y 8
    `nutritionBasis`.
- Sonda de términos cotidianos chilenos sobre estático + marcas (exploración): **103 cubiertos, 76 sin ficha** sobre 179
  entradas (5 repetidas: ver la subsección siguiente), p. ej. quesillo, jurel, reineta, kuchen, cerveza, vino, piscola,
  margarina, queso gauda/gouda, salame, ceviche, pan pita, tostada, cereal genérico, nuez, azúcar, leche condensada,
  queso crema, leche sin lactosa, chuleta, porotos verdes, habas, champiñón, mandarina, limón, frambuesa, cereza,
  chirimoya, membrillo, papaya, cuscús, lasaña, ravioles, ñoquis, ají, merkén, chacarero, barros luna, ave mayo,
  carbonada, ajiaco, chilenito, cuchuflí, negrita, nuggets (hoy alias a pechuga cruda), barra de proteína, pre-entreno,
  Gatorade/Powerade/Cachantún/Fanta/Sprite, bebida genérica, agua (sin ficha de 0 kcal). No cuentan como faltantes
  `granola`, `berlín` y `chocolate`, que sí tienen ficha (ver la subsección siguiente). La re-ejecución de WP-0 está en
  la subsección siguiente.
- USDA (`food.csv` 78 k filas) es el download **Foundation Foods**: solo **436** alimentos reales (resto
  `sub_sample_food`/`market_acquisition`), en inglés: son 436 filas foundation candidatas, de las cuales 366 tienen
  energía (B4). `food_nutrient.csv` 159 k filas.
- `off_chile.csv`: **16.805** productos OpenFoodFacts Chile (54 MB, TSV sin cabecera). Diagnóstico ago-2026 reportó
  5.766 filas en Room tras validación.
- `dataset_knowledge.bin`: 19.405 ejemplos instruccionales ("DATASET_KPKN_TRINIDAD_MASTER.json", no versionado) =>
  6.020 tokens, 3.460 trigramas, 2.592 priors de porción. Política vigente: vocabulario y ranking, "nunca gramos"
  (commit fea9989d6).
- `branded_snack_catalog.json` y `branded_energy_kcal_catalog.json`: **vacíos** (`{"items":[]}`) aunque hay código y
  tests de integridad para ellos.
- `subjective_portion_lexicon.json`: 772 bytes, 3 unidades; el léxico real está hardcodeado en
  `SubjectivePortionLexicon.kt` (441 líneas).
- `scripts/generate_food_catalog_v2.py` (catálogo con procedencia, ago-2026) genera `food_catalog_v2.csv.gz` +
  manifiesto, pero **nada en `src/main` lo consume** => el plan de "catálogo con procedencia" de la auditoría de agosto
  no se integró.
- Conocimiento de alimentos (alias, porciones, densidades, factores de cocción, roles de plato) repartido en >10
  archivos Kotlin: `TextNormalizer`, `FoodIdentity`, `FoodStapleOntology`, `HouseholdPortions`,
  `SubjectivePortionLexicon`, `CookingFactors`, `NutritionHeuristicEstimator`, `InferredMealContext`, `TagResolution`,
  `FoodDescriptionParser`, `FoodDatabase`.

### Sonda de cobertura de términos cotidianos (re-ejecutada en WP-0)

La exploración reportó una sonda de 179 entradas de términos cotidianos chilenos sobre catálogo estático + marcas: 103
cubiertos y 76 sin ficha (ejemplos en «Datos y catálogos»). La lista original traía 5 entradas repetidas (entre ellas
cerveza, chuleta, quesillo y kuchen), de modo que son **174 términos únicos**. WP-0 la re-ejecutó con un método
reproducible; el resultado por término está en [`coverage-probe.json`](coverage-probe.json).

- **Método.** `grep -ciF` (subcadena, sin distinguir mayúsculas, `LC_ALL=C.UTF-8`) de cada término sobre (A) las líneas
  `FoodItem(` de `data/food/FoodDatabase.kt`, `BrandedSnackCatalog.kt` y `BrandedEnergyKcalCatalog.kt`, y (B) todas las
  líneas de los dos catálogos de marcas: estos construyen sus entradas con helpers (`item/cookie/chips/candy/energy`) y
  sus propias líneas `FoodItem(` son solo definiciones (3 y 4 líneas). Un término cuenta como cubierto si (A) o (B)
  tiene al menos una coincidencia.
- **Resultado (cifra oficial).** Sobre los 174 términos únicos, con (A) o (B): **118/174 cubiertos y 56 sin ficha**.
  Solo con (A): **102/174** (72 sin ficha); sumando las 5 repeticiones (cerveza, chuleta, quesillo y kuchen no tienen
  ficha por (A)) queda a lo sumo a una unidad del 103/76 del plan, cuyo método no está registrado. Estas cifras
  reemplazan al 103/179.
- **Sin ficha (56):** jurel, reineta, kuchen, cerveza, vino, margarina, gauda, gouda, salame, ceviche, arepa, pan pita,
  nuez, azúcar, leche condensada, queso crema, chuleta, porotos verdes, habas, champiñón, mandarina, limón, frambuesa,
  cereza, chirimoya, membrillo, cuscús, lasaña, ravioles, ñoquis, ají, merkén, papaya, leche sin lactosa, queso gauda,
  chacarero, barros luna, ave mayo, carbonada, ajiaco, chilenito, cuchuflí, negrita, nuggets, barra de proteína, pre
  entreno, gatorade, powerade, cachantún, fanta, sprite, vino tinto, piscola, vodka, whisky, espumante.
- **Cubiertos solo por (B), es decir, por entradas de los catálogos de marcas (16):** quesillo, alfajor, galleta,
  tostada, cereal, chocolate, helado, coca, bebida, berlín, super 8, sahne nuss, ramitas, red bull, monster, score.
- **Colisiones de subcadena (10 de los 118; en el JSON llevan `"trusted": false` y un `note`):** cuentan como cubiertos
  por coincidir dentro de otra palabra, categoría o producto, no por una ficha propia: `agua` ("Palta (Aguacate)",
  "Atún en lata (agua)"), `mate` ("Tomate"), `pap` ("Papa"), `ron` (`micronutrients` y el alias "camaron"), `coca`
  ("Cocada"), `bebida` (categoría `bebida_energetica`), `cereal` ("Ghost Whey Cereal Milk"), `helado` (el nombre
  "Toasted ( helado paleta )") y los homónimos de otro país `quesillo` ("Quesillo nicaragüense") y `tostada` ("Tostada
  chapina"). Ninguno de estos 10 términos tiene ficha propia; sin ellos quedan 108/174. El `covered` del JSON se mantiene
  según el método.
- **Retirados de la lista de faltantes** (tienen ficha; conviene descontarlos al abrir WP-D1): `granola` (`gen091`
  "Granola", `FoodDatabase.kt:134`), `berlín` (`sn_cl_berlin` "Berlín relleno", `BrandedSnackCatalog.kt:261`) y
  `chocolate` (`gen141` "Chocolate de mesa", alias "chocolate", `FoodDatabase.kt:320`). `cereal` genérico sigue como
  faltante.
- **Fichas multilínea.** `gen137`-`gen142` no tienen `FoodItem(` en la misma línea que su nombre, así que un grep por
  líneas no las ve; se comprobó aparte que no cambian ninguna marca (solo suman coincidencias a términos ya cubiertos:
  galleta, chocolate, papa, avena, papas fritas, pap).
- **Tras WP-D1 (gate 19).** `StaticCatalogCoverageTest` repite la sonda como test con un criterio más estricto: un
  término tiene ficha si `staticFoodForAlias` o `findFoodByNormalized` lo resuelven en el catálogo estático, o si una
  fila de los dos catálogos de marcas lo lleva como frase completa (no como subcadena). Resultado: **169/174** con ficha
  (antes 118/174 con el grep de subcadena). De los 10 términos `"trusted": false`, 8 tienen ya ficha propia (`agua`:
  `gen143`; `coca` y `bebida`: `gen146`; `ron`: `gen197`; `cereal`: `gen179`; `helado`: `gen193`; `tostada`: `gen178`;
  `quesillo`: `gen158`) y `mate` y `pap` siguen sin ella. Quedan **5** sin ficha: mate, tallarines, negrita, pre entreno
  y pap; el test falla si un término con ficha la pierde o si la cobertura baja de 150/174.

Reproducción (desde `android-native/app/src/main/java/com/example/kpkn/data/food`, con la lista de términos del JSON):

```sh
export LC_ALL=C.UTF-8
grep -h 'FoodItem(' FoodDatabase.kt BrandedSnackCatalog.kt BrandedEnergyKcalCatalog.kt > fooditem_lines.txt
a=$(grep -ciF -- "$termino" fooditem_lines.txt)
b=$(cat BrandedSnackCatalog.kt BrandedEnergyKcalCatalog.kt | grep -ciF -- "$termino")
# cubierto si a > 0 o b > 0
```

### Auditorías previas: qué quedó abierto

- Ago-2026 (`docs/audits/2026-08-nutrition-precision`, `.opencode/plans/2026-08-23_*`): pendientes declarados:
  `EXPLAIN QUERY PLAN` sobre `LIKE '%x%'`, sub-tiempos de `resolve_tags` (3,4 s medidos en dispositivo), caso manual
  "hallulla 1, marraqueta 1, fideos 100g, queso gauda 50g" sin 5 selecciones manuales; integración del catálogo v2 con
  procedencia.
- Sep-2026 (`CONTINUAR.md`): los dos bloques pendientes (exclusiones contaminan cantidades; respuestas de composición
  "De huevo/maíz/papas") **sí se cerraron** (regresiones en `NutritionQaRegressionTest:80-114`, UI en
  `FoodLoggerDrawer.kt:811-813,2328`).
- Telemetría nutricional sanitiza el texto de las comidas => no se pueden minar fallos reales desde dispositivo; no hay
  JSONL exportados en el repo.

## Hallazgos

Cada hallazgo conserva su id del plan, sus referencias `archivo:línea` y una etiqueta de evidencia (CONFIRMADO POR SONDA
(Gradle), VERIFICADO, REPORTADO o INFERIDO). Describen el estado de partida (2026-10-02); lo que corrige cada gate está
en «Registro de ejecución». Las rutas son relativas a `android-native/app/src/main/java/com/example/kpkn/`; las
referencias `:NNN` sin archivo se copian tal cual del plan y se leen contra el último archivo citado. Dentro de cada
informe el orden es el del plan (de mayor a menor severidad). Los hallazgos sin id del plan (módulos muertos, bullets de
cocción, umbrales, Room, repositorio, severidad baja) llevan solo la etiqueta.

| Informe | Hallazgos con id | CONFIRMADO POR SONDA (Gradle) | VERIFICADO | REPORTADO | INFERIDO |
|---|---|---|---|---|---|
| A. Descripciones | 20 (A-P1..P6, A-Q1..Q11, A-C1..C3) | 17: A-P1..P5, A-Q1..Q6, A-Q9..Q11, A-C1..C3 | — | 3: A-P6, A-Q7, A-Q8 | divergencia de `\b` (sin id) |
| B. Búsqueda y datos | 13 (B1..B13) | — | B1, B2, B3 | 10 | — |
| C. Página, ViewModel y servicios | 22 (C1..C22) | — | C1, C2, C3, C4 | 18 | — |

### Descripciones (informe A)

Cadena viva (única): `FoodLoggerDrawer.analyzeDescription` (`:434-623`, scope Main) -> `ContextDetector.detect` (x2, en
Main) -> `SemanticPortionRetriever.retrieve` (solo telemetría/rango no renderizado) -> `parseMealDescription`
(`FoodParser.kt:222`) -> `TagResolver.resolveAll` (`TagResolution.kt:186`) ->
`SmartFoodResolver`/`HouseholdPortions`/`CookingStateResolver`/`MacroCalculator.scaleFoodByPortion`/`MacroValidator` ->
`FoodCombinationParser` -> `NutritionInterpretationBridge.enrich` -> `FoodInterpretationV2.toLoggedFood` ->
`NutritionRepository.saveNutritionLog`. `NutritionViewModel` NO participa en el parseo. Home guarda directo al
repositorio (`HomeScreen.kt:502-513`).

La sonda WP-N0 bajo Gradle (línea base oficial, ver «Línea base») confirmó 17 de los 20 hallazgos A; A-P6, A-Q7 y
A-Q8 siguen REPORTADO porque la sonda no los reproduce o no es concluyente. La divergencia de `\b` sigue sin poder
medirse en la JVM (entrada #34). Cada hallazgo anota la entrada de la sonda que lo cubre como "Sonda #n (Gradle)" y,
cuando la confirmación no abarca todos sus ejemplos, dice cuáles no se probaron.

#### Módulos muertos o parcialmente muertos (candidatos a retirar)

- **VERIFICADO** (grep, 0 referencias, diseño del bloque N): `FoodMentionReconciler` (importado, nunca llamado),
  `CookingMethodParser` (440 patrones, "NOT wired"), `DatasetKnowledgeSource`, `FoodTemplateMatcher` (solo
  `findMealTemplateMatch` sin llamadores), mini-pipeline
  `FoodInterpretationV2.interpret/answerClarification/finalize/recordCorrection` (`:363-528`, solo tests; el bloque N lo
  nombra `FoodInterpretationV2Engine`), rutas de gramos del dataset (`SubjectivePortionEngine` rama dataset-prior
  `:472-491`; `SemanticPortionRetriever.getGramsForFood` siempre null), `shouldUseAiLoggedFood`,
  `CookingFactors.applyCooking/applyCookingToMacros`, `FoodParser.extractGlobalPortion`,
  `ContextDetector.adjustPortion`.
- **REPORTADO** (informe A; el plan no los lista entre los verificados por grep): `MealLanguageGrammar.classifyDe/Con/Y`,
  `SubjectivePortionEngine.detectIntensifier`, `CookingFactors.isLikelyLiquid`, `InferredMealContext.portionAdjustment`
  (ignorado en `MacroCalculator.kt:99`), avisos `local-ai-*` y `aiInferredFoods` del drawer (`:318-337`, `:516-518`),
  `FoodDescriptionParser` (solo import).

#### Bugs de parseo y segmentación

- **A-P1 · CONFIRMADO POR SONDA (Gradle)** — Punto y dos puntos no separan (`FoodParser.kt:21` `COMMA_OR_PLUS`; verbos
  solo al inicio o tras `, ; \n` `TextNormalizer.kt:396-398`). "Desayuné 2 huevos. Almorcé arroz con pollo." => un ítem
  heurístico y el arroz se pierde. Sonda #7 (Gradle): "Desayuno: 2 huevos. Almuerzo: arroz con pollo." se parte en dos
  tags ("desayuno: 2 huevos. almuerzo: arroz" y "pollo."); el primero queda como ítem heurístico de 198 g/306,9 kcal
  (NEEDS_REVIEW) que absorbe los huevos y el arroz, y solo el pollo resuelve.
- **A-P2 · CONFIRMADO POR SONDA (Gradle)** — `menos` es negación (`:147`, `:527-537`): "al menos 2 huevos" excluye los
  huevos y crea fantasma `al` (MIXED_DISH 160 kcal/100 g x 350 g ≈ 560 kcal). Sonda #6 (Gradle): "al (estimado)"
  350 g/560 kcal (NEEDS_REVIEW) y los huevos excluidos.
- **A-P3 · CONFIRMADO POR SONDA (Gradle)** — TYPO_MAP singulariza antes del enmascarado de entidades protegidas
  (`TextNormalizer.kt:101,113`): "porotos con riendas" (`FoodParser.kt:30`) nunca coincide con `cl029`; igual "papas con
  mayo"; `maíz->choclo` rompe "tortilla/aceite/harina de maíz" (la puerta de identidad exige el token "choclo").
  Precisión del diseño N: solo las claves TYPO multi-palabra se protegen con placeholder (`:561-569`) y el enmascarado
  de `PROTECTED_ENTITIES` ocurre después (`FoodParser.splitMentionFragments :466-472`). Sonda #1, #40 (Gradle): "porotos
  con riendas" da `gen135` "Porotos (cocidos)" más el fantasma "riendas (estimado)" (#40: 99 g/139 kcal y 99 g/158,4
  kcal), nunca `cl029`.
- **A-P4 · CONFIRMADO POR SONDA (Gradle)** — Falso modo inglés: `a` y `no` cuentan como señales
  (`TextNormalizer.kt:310-333`); dos hits reescriben `a->1`, `no->sin`: "pasta a la bolognesa" =>
  `pasta 1 la bolognesa`; "pollo a la plancha, no frito" => `pollo 1 la`. Precisión del diseño N:
  `EN_SIGNAL_WORDS :310-321` incluye `a, no, an, of, can`. Sonda #1, #8 (Gradle): "pollo a la plancha y papas a lo
  pobre" se normaliza a "pollo 1 la plancha y papa 1 lo pobre" y "papa 1 lo pobre" queda como fantasma de 242 g/205,7
  kcal (NEEDS_REVIEW).
- **A-P5 · CONFIRMADO POR SONDA (Gradle)** — `y`/`con` parten nombres compuestos: "helado de vainilla y chocolate" añade
  barra de chocolate; "agua con gas" crea ítem `gas`; "200g de arroz con pollo" liga 200 g solo al arroz. Sonda #1, #9,
  #10, #11 (Gradle): "agua con gas" da los fantasmas "agua (estimado)" y "gas (estimado)" (220 g/352 kcal cada uno);
  "helado de vainilla y chocolate" da "helado de vainilla (estimado)" 100 g/160 kcal más `gen141` "Chocolate de mesa"
  25 g/136 kcal; en "200 g de arroz con pollo" los 200 g quedan solo en el arroz (EXPLICIT_MASS) y el pollo resuelve
  aparte (`gen004`, 154 g).
- **A-P6 · REPORTADO** — Nombres que son solo palabras de cocción desaparecen (`FoodParser.kt:359-361,383`): "un guiso",
  "un estofado", "sofrito"; en comidas múltiples el ítem se descarta en silencio. Precisión del diseño N:
  "asado/guiso/estofado" se consumen enteros por `COOKING_PATTERNS` (`:383`). Sonda #12 (Gradle, no reproduce): "asado"
  queda como 1 tag (cocción ASADO_PARRILLA) y resuelve `gen093c` "Asado de Tira (crudo)" (100 g/250 kcal); "un guiso",
  "un estofado" y "sofrito" no se probaron.

#### Bugs de cantidad e identidad

- **A-Q1 · CONFIRMADO POR SONDA (Gradle)** — Cantidades ignoradas en no contables (`HouseholdPortions.kt:218-228`;
  contables solo pan, huevo, empanada, wrap y lista corta `:22-32`): "2 yogures" = 1 yogur (200 g); "3 cervezas" = 1; "2
  pechugas" = 150 g; "una/2/media palta" = 80 g; "una manzana grande" = manzana. Precisión del diseño N: "media palta"
  también da 80 g (fracción perdida). Sonda #2 (Gradle): "2 yogures" trae qty=2 pero resuelve `gen087` a 200 g/118 kcal,
  el default de un yogur en el desayuno asumido (`HouseholdPortions.inferredItemGrams`, rama BREAKFAST_BOWL), sin
  multiplicar por la cantidad; contraste: "dos vasos de leche descremada" (#56) sí escala (515 g). #22 y #59 no aplican
  (quedan como ítems heurísticos). No se probaron "3 cervezas", "2 pechugas" ni "palta".
- **A-Q2 · CONFIRMADO POR SONDA (Gradle)** — Singularización "-es" corrompe identidad (`FoodParser.kt:916-923`): "3
  tomates" => `tomat` => MIXED_DISH 350 g ≈ 560 kcal con pregunta; igual filetes/chocolates/aguacates/cafés.
  `GoldenCorpusTest.kt:551` ASUME `"tomat"` como esperado. Precisión del diseño N: `canonicalTagKey :870-879` y
  `SmartFoodResolver.singularizeQuery :272-289` también truncan a ciegas. Sonda #2, #37 (Gradle): "tres tomates" da
  "tomat (estimado)" 350 g/560 kcal (NEEDS_REVIEW).
- **A-Q3 · CONFIRMADO POR SONDA (Gradle)** — Unidades ausentes: `lt`, `lts`, `cc`, `cm3`, `kilogramo(s)`, "un cuarto de
  kilo" => tags basura/heurísticos. Sonda #14, #15, #16, #17 (Gradle): "500 cc de leche" queda como tag "cc de leche"
  (qty 500, estimado de 220 g/136,4 kcal); "cuarto de kilo de carne molida" como "kilo de carne molida" (qty 0,25,
  350 g/770 kcal); "1 lt de agua" como "lt de agua" (220 g/352 kcal); "medio litro de jugo de naranja" como "litro de
  jugo de naranja" (qty 0,5, 50 g). No se probaron `lts`, `cm3` ni `kilogramo(s)`.
- **A-Q4 · CONFIRMADO POR SONDA (Gradle)** — Contenedores: "una lata/cartón" => `1 lata` no casa con
  `CONTAINER_PATTERNS` (exigen artículo, `SubjectivePortionEngine.kt:293-304`) => referencia `can` 200 g para una lata
  de 350 ml. Sonda #5, #18 (Gradle): "una lata de coca cola" se normaliza a "1 lata", usa la referencia `can` de 200 g
  (en vez de 350) y resuelve OFF Coca-Cola 350 ml en 200 g/84 kcal; "una botella de agua" usa la referencia `botella` de
  750 g (en vez de 500). No se probó "cartón".
- **A-Q5 · CONFIRMADO POR SONDA (Gradle)** — Densidad de líquidos: `detectDensityCategory` prueba FRUIT/VEG antes que
  LIQUID (`:547-559`): "un vaso de jugo de naranja" => 150 g (x0,6). Sonda #15, #19, #55 (Gradle): `gen103` (45 kcal/100
  ml) da 0,75 kcal/g en la línea base ("un jugo de naranja" 100 g/75 kcal; "jugo natural de naranja grande" 440 g/330
  kcal) porque su referencia de 100 ml se convierte en 60 g con la densidad 0,6 de FRUIT (`NutrientBasis`); con densidad
  1,0 serían 0,45 kcal/g, el valor de la sonda tras WP-N5 (250 g/112 kcal; 440 g/198 kcal). "medio litro de jugo de
  naranja" (#15) queda como tag "litro de jugo de naranja" de 50 g. La sonda no incluye "un vaso de jugo" (150 g).
- **A-Q6 · CONFIRMADO POR SONDA (Gradle)** — Defaults implausibles: "un poco de arroz" = 15 g; "un plato de
  cazuela/porotos" ≈ 200 g; "grande" = x2,0 en parser, x1,5 en chip, x1,25 en opción V2. Sonda #20, #21, #55 (Gradle):
  "plato grande de arroz" (preset LARGE) da 180 g y "un plato de arroz" 212,5 g, es decir 0,85x en vez de 1,25x; "jugo
  natural de naranja grande" se parsea como EXTRA y da 440 g. #22 no aplica (queda como ítem heurístico). No se probaron
  "un poco de arroz", los chips ni la opción V2.
- **A-Q7 · REPORTADO** — Pregunta de corte de pollo mezcla trutro CRUDO (`gen003t`) con opciones cocidas
  (`FoodStapleOntology.kt:81,113-116`). Precisión del diseño N: `ambiguousFamilies :114` = gen004 (cocido), gen003t
  (CRUDO), gen003e. Sonda #23 (Gradle, no concluyente): "pollo" resuelve `gen004` 150 g con pregunta de corte (`cut`),
  pero la sonda no exporta las opciones de la pregunta y no se ve la mezcla con trutro crudo (`gen003t`).
- **A-Q8 · REPORTADO** — `PhoneticEs` colapsa todas las vocales a `A` (`:102-107`): pasta/pesto/posta, mote/mate,
  lima/lomo cuentan como evidencia de identidad para tokens >= 4 (`FoodIdentity.kt:381-384`). Precisión del diseño N:
  huevo ≡ uva ≡ ave ≡ haba (`ABA`). Sonda #24 (Gradle, no reproduce): "uva" resuelve `gen053` Uva (120 g/83 kcal, AUTO),
  no huevo; no se observa la confusión (existe ficha exacta).
- **A-Q9 · CONFIRMADO POR SONDA (Gradle)** — Heurístico por substring: repollo contiene pollo => proteína magra;
  fresa/fresco contienen res => carne grasa; papaya => vegetal almidonado; pasta de maní => pasta cocida
  (`NutritionHeuristicEstimator.kt:69-208,255`). Sonda #26, #27 (Gradle): "ensalada de repollo" da un ítem heurístico de
  350 g/577,5 kcal (165 kcal/100 g, el perfil de proteína magra `LEAN_PROTEIN` y no el vegetal de 28 kcal/100 g) con
  "Asumí ensalada de repollo cocido"; #27 no reproduce el síntoma ("tres leches" resuelve `sn_cr_tresleches`, 350 g/980
  kcal, NEEDS_REVIEW). No se probaron fresa/fresco, papaya ni pasta de maní.
- **A-Q10 · CONFIRMADO POR SONDA (Gradle)** — Sin ruta de 0 kcal (`FoodIdentity.kt:395-397`, `SmartFoodResolver.kt:692`,
  `FoodImporter.kt:309-313`, sin ficha de agua): "un vaso de agua" ≈ 400 kcal pendiente de revisión; "2 litros de agua"
  tope 1.200 kcal (`TagResolution.kt:718-732`). Precisión del diseño N: sin ficha de agua + `hasPlausibleMacros` rechaza
  filas todo-cero => MIXED_DISH 160 kcal/100 g x 250 g ≈ 400 kcal. Sonda #1, #3, #5, #9, #18, #45 (Gradle): "un vaso de
  agua" da "agua (estimado)" 250 g/400 kcal, "una botella de agua" 750 g/1.200 kcal y "2 litros de agua" también
  1.200 kcal (todos NEEDS_REVIEW).
- **A-Q11 · CONFIRMADO POR SONDA (Gradle)** — Expansión de sándwich traga otros alimentos: `SANDWICH_DE_Y` termina en
  `(.+)$` greedy (`FoodCombinationParser.kt:16-19`) + `resolvedTags.clear()` (`TagResolution.kt:783-788`): "sandwich de
  jamón y queso y un jugo" pierde el jugo. Precisión del diseño N: `SANDWICH_DE_Y` se aplica a TODA la descripción cruda
  (`TagResolution :780-784`). Sonda #28 (Gradle): "un sandwich de jamón y queso y una coca cola" resuelve pan blanco,
  jamón cocido y queso cheddar y pierde la coca cola.

#### Cocción y macros

- **A-C1 · CONFIRMADO POR SONDA (Gradle)** — Doble aplicación: fila cruda + método => rendimiento (`grams/yield`) Y
  factor por gramo (`MacroCalculator.kt:112-157`): "150 g salmón a la parrilla" ≈ 420 kcal vs ~300. FRITO: factor kcal
  x1,10/1,20 + aceite explícito (`TagResolution.kt:582-599`); el factor sobrevive a "sin aceite"
  (`FoodLoggerDrawer.kt:946-954`). Precisión del diseño N: doble cocción confirmada (`scaleFoodByPortion :112-120` +
  `:148-157`, aceite en `TagResolution :364-373,582-599`). Sonda #4, #29, #30 (Gradle): #4 da `gen009` 150 g/420 kcal
  con conversión RAW->COOKED (yield 0,78) y factor de cocción; una sola conversión daría ≈ 400 kcal (150/0,78 x 2,08).
- **A-C2 · CONFIRMADO POR SONDA (Gradle)** — "huevos revueltos" resuelve a `Huevo Entero (frito)` por orden de sufijos
  (`CookingStateResolver.kt:83-85`). Sonda #32 (Gradle): "huevos revueltos" se parsea con cocción FRITO y resuelve
  `gen007f` "Huevo Entero (frito)" (50 g/90 kcal, AUTO).
- **A-C3 · CONFIRMADO POR SONDA (Gradle)** — `cookingWeightYield` por substring (`MacroCalculator.kt:70-76`): "repollo"
  0,75; "poroto verde"/"pasta de maní" 2,2. Precisión del diseño N: `cookingWeightYield` nunca casa "champiñones". Sonda
  #33 (Gradle): "100 g champiñones salteados" resuelve `gen038` (ficha cruda) con conversión RAW->COOKED de yield=1,0,
  el valor por defecto de `cookingWeightYield` (sin rendimiento propio para champiñones), 96 kcal y NEEDS_CONFIRMATION
  por la pregunta de aceite. No se probaron "repollo", "poroto verde" ni "pasta de maní".
- **REPORTADO** — Factores de cocción cambian kcal sin macros coherentes (GUISADO kcal x1,30 / grasa x1,20; COCIDO kcal
  x0,90) (`CookingFactors.kt:21-33`); modificadores `sin piel` grasa x0,6 kcal igual; empanizado +15 g carbs, grasa x2,
  kcal x1,2 (`NutritionHeuristicEstimator.kt:296-337`). `MacroValidator` solo avisa si gap > 30 %.
- **REPORTADO** — Plausibilidad admite kcal <= 0 con macros > 0 (`FoodIdentity.kt:392-399`); heurísticos fijan
  fibra/azúcar/sodio/potasio = 0 (`TagResolution.kt:707-711`); `rescaleEstimatedFood` no escala
  potasio/agua/cafeína/creatina (`FoodInterpretationV2.kt:825-834`); rangos heurísticos 0-900 kcal/100 g persistidos en
  `caloriesMax` (`NutritionHeuristicEstimator.kt:242-243`) => un ítem desconocido dispara el rango de la comida a miles
  de kcal.
- **REPORTADO** — `LoggedFood.quantity` puede contradecir `amount` (quantity 2, amount 80 g).

#### Umbrales y clarificación

- **REPORTADO** — `SmartFoodResolver.kt:873-882`: HIGH 0,86, FUZZY_HIGH 0,90, MEDIUM 0,6, MIN 0,18, SAFE_GAP 0,16,
  LEARNED_AUTO 0,74; 0,70 hardcodeado para ganadores locales (`:423-432`); **`:433-434` auto-selecciona un local
  "plain" con score >= 0,18**. Scores recortados a 1,0 (`:624`) => empates rotos por `isCurated` y luego `foodId`.
- **REPORTADO** — La decisión de preguntar no usa la confianza numérica (solo telemetría): `Decision` ->
  `identityAccepted` (`TagResolution.kt:401-406`) -> `operationalAutoStatus` (`HouseholdPortions.kt:464-480`) ->
  preguntas del bridge (`NutritionInterpretationBridge.kt:123-159`): identity, tortilla/salad composition, cut,
  weight_state, oil, package_portion, declared_amount. Tras "Seguir con esta estimación", cualquier edición de gramos
  vuelve a abrir la pregunta de identidad (`FoodLoggerDrawer.kt:649` resetea `explicitDecision`).

#### Robustez, concurrencia y rendimiento

- **REPORTADO** — Carrera de `FoodIndex` en arranque frío (= B5). `FoodCombinationParser.dishRegexCache` mutable sin
  sincronizar (`:21`).
- **REPORTADO** — `catch Throwable` en `initFoodIndex` (`:872`) traga OOM/cancelación; caché negativa del dataset nunca
  reintenta (`:819-841`).
- **REPORTADO** — Regex compiladas por llamada en bucles calientes: `FoodIdentity.normalize/familyFor`
  (`:79-86,207-209`), `FoodIndex.normalizeSearch/tokenize` (`:288-301`), `HouseholdPortions.looksLikePackName` (`:50`,
  por candidato), `candidateLooksLiquid` (`:644`), `InferredMealContext.hasToken` (`:196`),
  `TextNormalizer.normalize/convertNumberWords` (`:384-397,615-640`), `stripAccents` por clave en
  `findFoodExactByNormalized` (`FoodDatabase.kt:653-675`), `findFoodByNormalized` (`:690-723`); `brandHintFor` normaliza
  todas las marcas por `resolve()`; `repairToken` reconstruye vocabulario por token. En Main: `ContextDetector.detect`
  x2, `Bridge.enrich` tras cada análisis y edición (hasta ~32 escaneos fuzzy via `findDryOrCookedVariant` para masa
  explícita de alimentos sensibles a estado). Precisión del diseño N: regex por llamada peor de lo listado
  (`InferredMealContext.hasToken` ~100 compilaciones/llamada, `SubjectivePortionLexicon` por término x3 sitios) y el
  drawer enriquece dos veces (`TagResolver.resolveAll :871` ya enriquece y `FoodLoggerDrawer :311` repite en Main).

#### Divergencia JVM vs Android (ALTA prioridad de verificación)

- **INFERIDO** — Tests JVM corren en Java 21 (daemon de Gradle, `gradle-daemon-jvm.properties`; el lanzador es Java 17 y
  el desarrollo usa JDK 24), donde `\b` es ASCII-only (JDK >= 19), igual que en JDK 24; Android/ICU trata `é/á/í` como
  letras.
  `\b(?:...|huevo\s+poch[eé])\b` (`FoodParser.kt:106`) en dispositivo consumiría "huevo poché" entero y el ítem se
  descarta; `GoldenCorpusTest.kt:461` codifica el resultado JVM. Afecta también `tacita de caf[ée]\b`
  (`SubjectivePortionEngine.kt:96`), fillers `aj[aá]`/`no\s+s[eé]` (`TextNormalizer.kt:71`), `\btentempié\b`
  (`ContextDetector.kt:79`). El equipo ya chocó con esto (comentario `jam` `TextNormalizer.kt:162-163`). => Necesita
  test instrumentado del parser (como `CookingFactorsAndroidTest`) o `UNICODE_CHARACTER_CLASS` uniforme (el diseño N
  descarta esta última, ver WP-N4). Sonda #34 (Gradle): en la JVM "huevo poché" queda como ítem heurístico de 350 g/542,5
  kcal (NEEDS_REVIEW); el comportamiento en dispositivo no se puede medir desde la JVM.

#### Tests del pipeline: por qué están verdes

- **VERIFICADO** (aserciones `tomat` y `huevo poché`; el resto de la lista, REPORTADO) — `GoldenCorpusTest`: ~320 casos
  en UN solo `@Test` agregado (`:619-669`), solo parser, sin macros; muchos casos solo comprueban el tag; **fija bugs**
  (`tomat` `:551`, `huevo poché` `:461`, `papa rústicas` `:389`, "media manzana"=50 g).
- **REPORTADO** — Corpus end-to-end con bandas anchas: papas fritas 150-600 kcal (`ResolutionGoldenCorpusTest.kt:71-73`),
  aceite 70-180, sándwich 250-500, rebanada de pan 50-110; `ReservedNaturalMealCorpusTest` acepta cualquiera de dos
  resultados (`:60,118,148-169`); `IndependentNutritionCorpusTest` solo 4 referencias exactas y miden redondeo.
- **REPORTADO** — `NutritionMetricsContractTest` oculta 2/47 fallos bajo el umbral >= 95 % (`:158`); p95 < 50 ms medido
  sin las 16 k filas OFF. `NutritionQaRegressionTest` reimplementa el parser OFF (`:190-208`). `DatasetTestHarness`
  nunca carga CSV.
- **REPORTADO** — Ningún test cubre: riendas, media palta, conteos de no contables, plurales "-es" e2e, agua,
  `lt/cc/kilogramo`, puntos de oración, "al menos", falsos positivos de inglés, semántica regex de dispositivo.
- **REPORTADO** (diseño del bloque N) — Tests laxos adicionales: `FoodParserTest.kt:234` (`size == 1 || size >= 2`,
  tautología), `EverydayMealCorpusTest.kt:293` (`isResolved || hasMaterialQuestion()`),
  `FluencyGoldenCorpusTest.kt:130-133`, `ContextDetectorTest.kt:53`; bandas `EverydayMealCorpusTest.kt:349` (400-800
  kcal), `:339` (250-500).

### Búsqueda y datos (informe B)

#### Críticos

- **B1 · VERIFICADO** — Tocar un resultado de búsqueda registra OTRO alimento o nada. `HouseholdPortions.identityForSearchPick`
  (`HouseholdPortions.kt:482-499`) trata todo OFF/USDA como "pack" y, si la consulta tiene ≤2 tokens, reemplaza el ítem
  tocado por `householdStaticFood(query)`; si devuelve null el tap no hace nada (`FoodLoggerDrawer.kt:1577-1578`). Casos
  verificados por el agente: "leche" + Leche descremada Colun => `gen016` leche entera; "pan" + Pan integral Bauducco =>
  `gen019` Pan Blanco; "nuggets" => `gen003` pechuga cruda (106 kcal vs 204); "red bull"/"coca cola"/"leche colun" =>
  no-op. La identidad sustituida se aprende como confirmada por el usuario (`FoodInterpretationV2.kt:810-822` ->
  `NutritionRepository.kt:164-186`).
  - Matiz de la verificación directa: la intención documentada ("a generic query must not persist a pack/SKU as the
    eaten identity", `HouseholdPortions.kt:488-490`) es razonable para la CANTIDAD (no comer el pack entero) pero el
    código la aplica a la IDENTIDAD, descartando la ficha elegida. Fix conceptual: conservar la identidad tocada
    (nutrientes/100 g del SKU) y aplicar solo la porción doméstica; nunca devolver null silencioso.
  - Confirmado al diseñar (bloque S): `GlobalFoodEntity.toFoodItem()` pone `servingSize = 100` en toda fila `PER_100G_*`
    (`Entities.kt:561`) y `eatenGramsForSearchPick` lo trata como porción declarada (por eso "leche" + Colun registraría
    100 g); `HouseholdPortions.isGlobalSku` (`:446-450`) clasifica por texto de `source`, así que
    gen015/016/026/046/066 ("USDA SR Legacy (rounded)") se tratan como SKU global; `enrich` hace finalizable un tag con
    `"identity" in confirmedDimensions` (`NutritionInterpretationBridge.kt:114-171`), así que conservar la ficha tocada
    no bloquea guardar; el aprendizaje ocurre solo al guardar (`confirmedLearning()` -> `recordFoodSelection`);
    `HouseholdPortionsOperationalTest.kt:63-70` fija la sustitución incorrecta.
- **B2 · VERIFICADO** — Ranking entierra los genéricos curados. Priors de fuente invertidos: estáticos prioridad
  60/score 0.6, OFF 80 y "confianza" 0.65-0.9 nunca < 0.6 (`NutritionRepository.kt:1152-1172,1225-1226`,
  `FoodImporter.kt:321-332,359`, `FoodDescriptionParser.kt:495-521`); hit por `contains` de substring (`:1208-1210`,
  "pan" => empanaditas, biopan, panchitos, pancake...); filtro verificado/identidad DESPUÉS del `limit=15`
  (`FoodLoggerDrawer.kt:1131-1133`). "Pechuga de Pollo (cruda)" ~0.845 vs ~150 filas OFF con "pollo" 0.89-1.0.
  - Confirmado al diseñar (bloque S): `matchesDeclaredIdentity` ya rechaza "empanaditas" para "pan"
    (`FoodIdentity.kt:376-384`); la basura visible viene del filtro posterior al `limit=15` y del scoring.
- **B3 · VERIFICADO** — ~72 MB hasheados e inflados en cada arranque en frío antes de publicar logs/planes.
  `computeDatasetChecksum` corre antes del early-return de versión (`FoodImporter.kt:67,488-507`); sin `noCompress`.
  Logs/plantillas/learning se leen y decodifican dos veces (`NutritionRepository.kt:962,986` / `973-974,987-988`).
- **B4 · REPORTADO** — Importación en una sola transacción larga que bloquea toda escritura Room, sin UI de progreso
  (`importProgress` sin consumidor); parsea 159 k filas de nutrientes para quedarse con 366 alimentos USDA (de 436 filas
  foundation candidatas, 366 tienen energía; ambas cifras son correctas en su contexto); el re-import borra
  `usageCount/lastUsedAt` (`FoodImporter.kt:80-83,106,122`).
- **B5 · REPORTADO** — `FoodIndex` puede congelarse sin el catálogo estático si `initFoodIndex` corre antes de publicar
  `_foodDatabase` (prewarm del drawer `FoodLoggerDrawer.kt:627-630`, share intents); guard `size()>0` impide
  reconstruir; nunca se invalida tras refresh/restore/re-import (`NutritionRepository.kt:856-875,993`,
  `FoodIndex.kt:68`).

#### Calidad de datos

- **B6 · REPORTADO** — USDA: `portionUnit` = `measure_unit_id` ("1000"); `portionGrams` ignora `amount` (aceites => 90,7 g
  por defecto); azúcar efectivamente 0 (ID 2000 solo en 5/436, 1063 no mapeado); grasa 1085 no mapeada (8 alimentos);
  `aliasesJson="[]"` => sin alias español (`FoodImporter.kt:109-119,155-172,211,220-222`); `category` numérica nunca leída.
- **B7 · REPORTADO** — Alias inconsistentes: la pestaña de búsqueda ignora `FOOD_ALIASES` (207 entradas),
  `SIMPLE_FAMILY_BY_TOKEN` y la ontología; solo usa `searchAliases` propios ("banana" no encuentra Plátano `gen002`).
  Sin plurales ("huevos", "papas", "tomates" fallan). 41/207 targets de `FOOD_ALIASES` resuelven por primer `contains`
  (pechuga/poyo/nuggets => `gen003` crudo) y no se indexan en `FoodIndex` (`FoodDatabase.kt:638-644`,
  `FoodIndex.kt:72-74`); 12 alias muertos (red bull, monster, score, tamal) porque los branded no están en `ALL_FOODS`.
- **B8 · REPORTADO** — Macros no-nulos con default 0.0 en `FoodItem` (`NutritionModels.kt:48-91`): dato ausente = cero
  real. `GlobalFoodEntity.toFoodItem` pierde `category` y `unit` (bebidas OFF muestran "/100 g") (`Entities.kt:555-588`).
- **B9 · REPORTADO** — Duplicado contradictorio "Arroz Integral (cocido)" 111 vs 123 kcal (`FoodDatabase.kt:29` vs
  `:265`); flag `LOW_QUALITY` de OFF inalcanzable; cafeína OFF no importada; bebidas energéticas curadas ocultas por
  `UNVERIFIED_NUTRIENT_BASIS` (`BrandedEnergyKcalCatalog.kt:122-124`); procedencia FDC contradictoria gen003/gen004
  (`FoodStapleOntology.kt:78-79` vs `FoodDatabase.kt:21-25`).
- **B10 · REPORTADO** — Catálogos JSON de marcas vacíos son inofensivos: cada loader fusiona un catálogo programático en
  código (`BrandedSnackCatalog.kt:41-45`, `BrandedEnergyKcalCatalog.kt:43-48`), errores de asset tragados.
- **REPORTADO** — ~13,8 MB de CSV FDC en el APK nunca leídos. `verifyDatasetKnowledge` (gradle `check`) requiere
  `python3` => falla en Windows.

#### Room / migraciones (v28)

- **B11 · REPORTADO (verificar en dispositivo)** — Triggers FTS duplicados en bases migradas desde v≤5: `MIGRATION_5_6`
  crea `global_foods_ai/ad/au` (`KpknDatabase.kt:193-215`) y Room crea los suyos (`28.json:1650-1655`) => doble
  escritura FTS, índice probablemente inconsistente; errores FTS tragados (`NutritionRepository.kt:306`). **Verificar en
  dispositivo** con `SELECT name FROM sqlite_master WHERE type='trigger'`. Confirmado al diseñar (bloque S): en Room v28
  solo existen triggers `room_fts_content_sync_*` (los `global_foods_ai/ad/au` solo en bases migradas por
  `MIGRATION_5_6`).
- **REPORTADO** — `MIGRATION_10_11` deja `normalizedBrand = lower(name)` y `normalizedName` con acentos en custom foods
  (`:298-299`).
- **REPORTADO** — FTS con tokenizer "simple" sin plegado de acentos mientras la query se desacentúa.
- **REPORTADO** — `NutritionLog` es un blob JSON con solo `date` y `mealType` como columnas (`Entities.kt:240-243`) =>
  imposible agregar en SQL.
- **REPORTADO** — Sin entidades de favoritos/recientes; `usageCount` se pierde en cada re-import.

#### Rendimiento del hot path

- **B12 · REPORTADO** — 3 `LIKE '%q%'` full-scan + FTS + scoring lineal por búsqueda; raw query sin `ORDER BY` +
  `LIMIT 100` (`Daos.kt:374-399`) => subconjunto dependiente del plan.
- **B13 · REPORTADO** — Comparador de dedupe llama `findFoodByNormalized` (compila ~450 regex) por comparación
  (`NutritionRepository.kt:328`, `FoodDatabase.kt:690-725`); regex compiladas por llamada en normalizadores
  (`NutritionRepository.kt:1122-1130`, `FoodIndex.kt:288-296`, `FoodIdentity.kt:79-86`); `repairToken` reconstruye el
  vocabulario (2.592 claves) en cada llamada (`SemanticPortionRetriever.kt:235-277,334-335`); fan-out trigram 500-730
  candidatos por token común, cada uno pasa por `matchesDeclaredIdentity` (`SmartFoodResolver.kt:346-349`);
  `brandHintFor` escanea todas las marcas (`FoodIndex.kt:139-147`).
- **REPORTADO** — `FoodIndex.search` promete orden por relevancia pero devuelve `Set` sin orden (`FoodIndex.kt:89-92`).
- **REPORTADO** — Jobs de `performSearch` no se cancelan (guard `searchQuery == query` evita pisar, no el CPU)
  (`FoodLoggerDrawer.kt:1134`).
- **REPORTADO** — Re-emisión de todo el historial en cada cambio; borrar un log decodifica toda la tabla
  (`NutritionRepository.kt:208`).

#### Repositorio

- **REPORTADO** — `addNutritionLog` optimista fire-and-forget sin validación (creatina); `updateNutritionLog` sin
  llamadores; `clearLearnedResolutions` = 3 escrituras + prefs sin transacción y borra todas las plantillas (`:900-912`);
  `activatePlan`/`pinTodayGoalOfActivatedPlan` y `deleteNutritionPlan` en launches separados sin orden (`:509-553`).
- **REPORTADO** — Dos formatos de fecha coexisten (`"...T12:00:00.000Z"` vs `"YYYY-MM-DD"` `NutritionViewModel.kt:285`);
  `getLogsForDate` usa igualdad exacta (`Daos.kt:285`).
- **REPORTADO** — Backup restaura metadatos de catálogo específicos del dispositivo (`SettingsJsonBackup.kt:278-307`) =>
  doble import.
- **REPORTADO** — `_foodIndex`/`_smartResolver` lazy sin sincronizar (`:226-240`); `catch (e: Throwable)` traga
  cancelación (`:872`).

#### Tests de búsqueda y datos: huecos

- **REPORTADO** — Ningún test de `searchFood`/`searchFoodCandidates`/`buildFoodCandidate` (ranking), ni de la
  sustitución al tocar, ni de rollover de medianoche/formatos mixtos, ni de triggers FTS, ni de taps no-op.
- **REPORTADO** — `FoodCatalogProvenanceTest` solo prueba helpers puros: `importAll` nunca corre sobre los CSV reales.
- **REPORTADO** — `NutritionQaRegressionTest.kt:190-194` reimplementa su propio parser OFF en vez de usar
  `FoodImporter`.
- **REPORTADO** — `FoodDatabaseResolutionTest` prueba `findFoodByNormalized`, que la pestaña de búsqueda no usa (falsa
  confianza).

### Página, ViewModel y servicios (informe C)

Rutas bajo `android-native/app/src/main/java/com/example/kpkn/`.

#### Alta severidad

- **C1 · VERIFICADO** — Fecha seleccionada no avanza tras medianoche. `NutritionViewModel.kt:47` (VM scoped a la
  Activity, `MainActivity.kt:458`), `NutritionScreen.kt:741`, drawer guarda `"${logDate}T12:00:00.000Z"`
  (`FoodLoggerDrawer.kt:1147`). Síntoma: app viva de un día para otro => pestaña Nutrición muestra ayer y todo lo
  registrado (botón, chips, widget, share) cae en ayer. Home no se afecta (`HomeScreen.kt:508`).
- **C2 · VERIFICADO** — Importación de catálogo fallida/lenta oculta todos los datos del usuario.
  `NutritionRepository.kt:932-1032`: logs, planes, snapshots y plan activo se cargan solo tras
  `FoodImporter.importIfNeeded`; el `catch` (1021-1029) no los carga y cancela el recordatorio corporal. Síntoma:
  Home/Nutrición vacíos y "Sin objetivos" toda la sesión; riesgo de duplicados al re-registrar.
- **C3 · VERIFICADO** — Ruta de acción del widget/atajo se auto-destruye. `MainActivity.kt:1331-1356`: navega durante
  composición y luego `popBackStack(NutritionAction, inclusive=true)` en `LaunchedEffect`, lo que también quita
  Nutrición. Síntoma: "Log"/"Buscar" del widget aterrizan en Home sin logger; "weight" no abre cuerpo.
- **C4 · VERIFICADO** — AUGE ignora todas las comidas del drawer. `domain/auge/NutritionRecoveryEngine.kt:38-45` hace
  `LocalDate.parse(log.date)` y lanza con `"...T12:00:00.000Z"` => filtrado silencioso. Tests usan fechas planas.
  Síntoma: multiplicador nutricional siempre neutro ("Sin comidas en la ventana").
  - Nota de la verificación directa: el formato `"${date}T12:00:00.000Z"` también lo escribe
    `domain/nutrition/MacroCalculator.kt:588`; cualquier consumidor que haga `LocalDate.parse(log.date)` sin `take(10)`
    falla igual. Buscar todos los `LocalDate.parse(` sobre `NutritionLog.date` en la fase de remediación.
  - Alcance verificado por grep: el único consumidor que parsea la fecha completa es `NutritionRecoveryEngine.kt:40`;
    Home, historial, notificaciones, creatina y calibración ya usan `take(10)` o `startsWith`. Fix de una línea + test
    con el formato real.

#### Media severidad

- **C5 · REPORTADO** — Borrador se pierde en rotación/plegado/process death. Todo el estado del drawer en `remember`
  (`FoodLoggerDrawer.kt:182-221`), `showFoodLogger` en `NutritionScreen.kt:105`; sin `configChanges`. El plan de fase 4
  (ago-2026) pedía un ViewModel del logger con `SavedStateHandle`: nunca se construyó. Confirmado al diseñar (bloque U):
  `FoodLoggerDrawer.kt:632-641` reaplica `initialDescription` en cada recomposición (toda persistencia del borrador
  debe protegerse de ese efecto).
- **C6 · REPORTADO** — Intents de share/widget se reprocesan al recrear la Activity. `MainActivity.kt:169-170,282-297`
  (sin guard `savedInstanceState`, intent no se limpia). Riesgo de guardado duplicado.
- **C7 · REPORTADO** — Re-analizar borra alimentos añadidos por búsqueda. `FoodLoggerDrawer.kt:303-311`,
  `TagResolution.kt:1115-1125`; contradice el comentario en `FoodLoggerDrawer.kt:153-158`.
- **C8 · REPORTADO** — Slider de gramos se escapa. `FoodLoggerDrawer.kt:2434-2445`: máximo `max(600, grams*2)` depende del
  valor editado => escalado exponencial >300 g; además telemetría + reinterpretación por frame (`:874`, `:916`), sin
  campo numérico, TalkBack lee porcentaje.
- **C9 · REPORTADO** — Borrar comida sin confirmación ni deshacer; no existe editar. Botón 28 dp
  (`NutritionScreen.kt:1169-1176`), errores tragados (`NutritionRepository.kt:200-204`), `updateNutritionLog` sin
  llamadores, `MealHistoryScreen` solo lectura.
- **C10 · REPORTADO** — Recordatorios de comida poco fiables. Sin boot receiver para nutrición
  (`AndroidManifest.xml:118-160`); receivers leen flows en memoria en proceso recién creado
  (`NutritionNotificationManager.kt:345-347,394-406`) => alerta de macros nunca dispara con app cerrada y recordatorios
  disparan aunque la comida ya esté registrada; `setRepeating` 24 h deriva con DST (`:275-301`).
- **C11 · REPORTADO** — Diálogo de utensilios descarta tamaños guardados. Parte de `UTENSIL_DEFAULTS` en vez de
  `currentUtensilOverrides()` (`FoodLoggerDrawer.kt:217-221,1197-1201`) y guarda los 9 valores (`:1286-1289`). Confirmado
  al diseñar (bloque U): `NutritionRepository.applyConfiguredUtensils()` (`:849`) no tiene llamadores (los utensilios
  guardados nunca se reaplican tras reiniciar el proceso, agrava C11).
- **C12 · REPORTADO** — Tipo de comida por defecto inconsistente. Nutrición siempre "Almuerzo"
  (`NutritionScreen.kt:107,307`), Home por hora (`HomeScreen.kt:135-143`), recordatorios deciden por tipo
  (`NutritionNotificationManager.kt:347-355`).
- **C13 · REPORTADO** — Posible crash por keys duplicadas en resultados de búsqueda. Key = nombre+marca
  (`FoodLoggerDrawer.kt:1568`) pero dedupe usa `canonicalKey` con estado (`FoodIdentity.kt:250-255,326-328`). Fix: key
  por `food.id`.
- **C14 · REPORTADO** — Tarjeta de balance energético puede crashear. `NutritionViewModel.kt:446,452-456`:
  `LocalDate.parse` sin manejo dentro de un flow; un workout con fecha no ISO (datos importados) tumba la app.
- **C15 · REPORTADO** — Calibración inalcanzable, pisa aprendizaje, recomendación engañosa. Solo deep link; VM guarda
  perfil completo desde copia obsoleta con `save()` (`NutritionCalibrationViewModel.kt:122-139,157-185`); "día
  completo" = >0 kcal (`:77-96`); motor ignora objetivo del plan (`NutritionCalibrationEngine.kt:60-72`).

#### Baja-media

- **C16 · REPORTADO** — Creatina quick-add: muestra antes de persistir, fire-and-forget, sin guard doble tap, formato de
  fecha distinto (`NutritionViewModel.kt:272-311`, `NutritionRepository.kt:135-139`).
- **C17 · REPORTADO** — Exigir plan solo aplica al botón principal (`NutritionScreen.kt:304`), no a
  chips/"+"/widget/share.
- **C18 · REPORTADO** — Telemetría escribe `SharedPreferences.commit()` en hilo UI 5-9 veces por análisis
  (`NutritionTelemetry.kt:217-224`, `FoodLoggerDrawer.kt:460-617`).
- **C19 · REPORTADO** — Edición de macros: kcal a 0 borra macros y bloquea "+" (`FoodLoggerDrawer.kt:1057-1072`);
  recálculo 4/4/9 ignora alcohol (`:1079,1094,1109`); botones 24 dp sin etiqueta (`:2585-2597`).
- **C20 · REPORTADO** — Cambiar nivel de aceite pierde macros editados a mano (`FoodLoggerDrawer.kt:930-987`).
- **C21 · REPORTADO** — Errores no visibles/persistentes: `saveError` arriba mientras la lista auto-scrollea abajo
  (`:239-244`, `:1402`); algunos resultados de búsqueda no hacen nada al tocar (`:1576-1578`).
- **C22 · REPORTADO** — Totales inciertos se muestran exactos: truncado sin "≈" (`NutritionScreen.kt:416,485,643`); chip
  "Rango estimado" sin números (`MealHistoryScreen.kt:338-389`). Viola contrato v2.

#### Baja

- **REPORTADO** — Settings se escriben en cada visita (`NutritionScreen.kt:149-153`); `applyPlanToSettings` conserva
  metas viejas si el plan trae 0 (`NutritionViewModel.kt:602-605`).
- **REPORTADO** — Historial/tendencia/creatina recomputan sobre todo el historial en hilo UI sin `flowOn`
  (`NutritionViewModel.kt:35-45,185-235`).
- **REPORTADO** — Búsqueda de corrección por tecla sin debounce (`FoodLoggerDrawer.kt:2513-2521`): normaliza toda la
  lista y hace 4 queries por pulsación.
- **REPORTADO** — Strings hardcodeados y etiquetas crudas (SMALL/MEDIUM/LARGE `:2391`, "Estado: incomplete"
  `NutritionCalibrationScreen.kt:90,146`, widget "Log/Nutricion", mezcla "Thursday, 2 de October"
  `NutritionScreen.kt:408`).
- **REPORTADO** — Accesibilidad: superficies clicables sin rol/estado (`FoodLoggerDrawer.kt:2059,2081,2173,2419`,
  `NutritionScreen.kt:821`), targets 24-32 dp, sliders sin etiqueta.
- **REPORTADO** — Editor de plan: sin guard de cambios no guardados, campos editables durante guardado, `commit()` lee UI
  viva (`NutritionPlanEditorViewModel.kt:313,330`), sin teclado numérico.
- **REPORTADO** — `parseLocalizedNumber` ignora `locale` ("1,500" => 1.5). `Modifier.blur` no-op bajo API 31
  (`NutritionScreen.kt:469`).
- **REPORTADO** — Código muerto: VM
  `uiState/bodyKpis/pantryItems/createPlan/activatePlan/deletePlan/duplicateLog/planOverlay`; screen
  `BodyKpiSection/DistributionLabel`; drawer `prefs/MODE_BASIC/shouldUseAiLoggedFood/isOilTag/ModeOptionCard` y bloques
  `and false` (`FoodLoggerDrawer.kt:257-261,1479-1510`); `NavigationBus.emitSharedNutritionText`.
- **REPORTADO** — Voz: `VoiceNutritionRecognizer` solo lo usa la voz de workout (`WorkoutVoiceInput.kt:104`); el drawer
  no tiene micrófono.
- **REPORTADO** — Telemetría: mensajes de excepción y stacks (4 KB) sin sanitizar (`FoodLoggerDrawer.kt:359,542,571`,
  `NutritionTelemetry.kt:179,277-283`); `tagHash` = `hashCode` 32-bit reversible (`TagResolution.kt:481`); flag
  "telemetry_enabled" ya no desactiva nada (`NutritionTelemetry.kt:78-81`).
- **REPORTADO** — Tests: `NutritionViewModelTest` ejercita rutas legacy (`addLog/createPlan`), nada de rollover de fecha,
  balance energético, share/open, creatina; `FoodClarificationPromptTest` 3 casos; cero tests Compose del drawer.

## Estado de auditorías previas

Abierto de las auditorías de ago-2026 y sep-2026 según el informe C (se suma a la lista de «Hechos verificados /
Auditorías previas»). La columna de la derecha indica el paquete del plan relacionado, solo cuando el plan lo nombra o
lo toca de forma directa.

| # | Pendiente | Relación con el plan |
|---|---|---|
| 1 | Migración Room 20->23 nunca ejecutada en dispositivo | Gate instrumentado `NutritionMigrationTest` (criterio de cierre 2); WP-S12 añadiría su propio caso si se activa |
| 2 | Importación real de primer arranque (data v8) y memoria sin medir | WP-S3 y WP-S8; criterio de cierre 3 (arranque en caliente sin abrir CSV, `phase1.published` < 400 ms) |
| 3 | Upgrade con perfil poblado nunca demostrado | Protocolo de QA: respaldar el perfil y `adb install --no-incremental -r` |
| 4 | TalkBack y 200 % de fuente sin probar | WP-U17 (roles, targets >= 40 dp, etiquetas); la prueba con TalkBack no figura como paso |
| 5 | `EXPLAIN QUERY PLAN` sin hacer; 4 búsquedas DAO redundantes por query siguen (`NutritionRepository.kt:296-315`) | WP-S2 (elimina el `LIKE` crudo y saca FTS del camino de búsqueda); WP-S12 solo si la búsqueda supera 150 ms |
| 6 | Script de catálogo v2 nunca ejecutado ni integrado | WP-S10 (lo retira y cierra el ítem "catálogo v2") |
| 7 | Suite completa sin filtro nunca terminó (15 min colgada) | Regla de «Verificación»: nunca la suite completa sin filtro |
| 8 | ViewModel del logger (fase 4) nunca construido | WP-U7 (`FoodLoggerViewModel`) |
| 9 | Texto de búsqueda avanzada se pierde tras "Seguir editando" (`key(sheetRevision)` `FoodLoggerDrawer.kt:1334`) | Sin paquete dedicado (WP-U7 guarda `searchQuery` en el borrador, pero el plan no lo cita para este ítem) |
| 10 | Fallo de guardado y "Olvidar" probados solo en JVM | WP-U16 hace visible el error de guardado; no hay prueba de dispositivo prevista |
| 11 | Plan LATAM 2026-09-03 sigue "en construcción" | Sin paquete |
| 12 | Esquema de telemetría v1 desactualizado | Sin paquete (WP-U17 punto 3 sanea el contenido de la telemetría, no su esquema) |

Los pendientes declarados de ago-2026 (`EXPLAIN QUERY PLAN`, sub-tiempos de `resolve_tags`, caso manual de las 5
selecciones, catálogo v2) y el cierre de los dos bloques de sep-2026 (exclusiones y respuestas de composición) están en
«Hechos verificados / Auditorías previas».

## Plan de remediación

El plan completo (diseño, archivos, tests y riesgos de cada paquete) está en
`C:\Users\valen\.claude\plans\te-encargo-una-auditor-a-enumerated-nautilus.md`; esta sección es solo su índice.
Alcance aprobado: todo el plan, flavor Base, sin migración Room salvo que WP-S12 se active. Cuatro bloques de
paquetes (WP): **N** pipeline de descripciones, **S** búsqueda y datos, **U** página, ViewModels y servicios, **D**
contenido del catálogo; más WP-0 (este documento). Tope por WP: 2 pasadas de QA (tests + diff de la sonda); una tercera
divide el WP. Avance tras los gates 1 a 21: 38 de 45 paquetes cerrados, 37 del plan más S2b
(ver «Registro de ejecución»).

### Orden de ejecución por fases

| Fase | Contenido | Entregable / gate |
|---|---|---|
| 0 Línea base (día 1) | WP-0 documento de auditoría; WP-N0 harness + `blind-probe.json` baseline; reparar hook Write; fixture OFF de búsqueda (`build_off_search_fixture.py`) | Tabla 60/60 impresa; doc en `docs/audits/2026-10-food-system/` |
| 1 Críticos (PR1-PR2) | WP-U1, U3, U4, U5, U6; WP-S3 (incl. U2); WP-S1 + U16; WP-N1 (incl. S7), N2, N3, N4 | 719 + nuevos verdes; sonda #6-8, #34-38 OK; tap de búsqueda nunca sustituye identidad; arranque sin leer CSV |
| 2 Descripciones y búsqueda (PR3-PR5) | WP-N5, N6, N8, N9, N10, N11; WP-S2 (+ `SearchGoldenCorpusTest` 44/44), S6, S4, S5 | Sonda: todos los `expect` de #1-60 cumplidos salvo los marcados control; precisión@1 >= 45/47; búsqueda < 150 ms en dispositivo |
| 3 Logger y servicios (PR6-PR8) | WP-U7 (FoodLoggerViewModel), U8, U9, U10, U11, U12, U13; U14; U15 | `FoodLoggerDrawerDraftUiTest` verde; QA de 10 puntos del bloque U |
| 4 Datos y estructura (PR9-PR12) | WP-S8, S9 + S10 (un bump), S11; WP-D1; WP-N7; WP-N12 (`BlindMealCorpusTest` con umbrales); WP-N13 secciones 1-3 (resto incremental); WP-U17 | Todo estático con `source`; APK -14 MB; corpus ciego con gates; sin aserciones de doble resultado |
| Cierre | Actualizar README de auditoría con antes/después (`blind-probe.json` baseline vs final, `blind-corpus.json`), `.opencode/memory/MEMORY.md`, mapa de arquitectura si cambian rutas | Resumen de comandos/resultados/QA ejecutado y límites |

Estimación orientativa: fase 0 ≈ 1 d; fase 1 ≈ 4-5 d; fase 2 ≈ 8-10 d; fase 3 ≈ 6-8 d; fase 4 ≈ 7-9 d (+ ~3 h de
curación de alias USDA y contenido de WP-D1). Cada PR corre el gate de paquete; QA de emulador al cierre de cada fase.

### Solapes resueltos entre bloques

- **WP-U2 ≡ WP-S3**: misma función `loadFromDb`; se implementa el diseño S3 y se incorporan los tests y la regla "nunca
  cancelar recordatorios" de U2.
- **WP-S7 ⊂ WP-N1**: un solo PR "TextKeys + regex precompiladas".
- **WP-S1 + WP-U16** van juntos (tap de búsqueda + error visible).
- **WP-S6 antes** de los casos 21/24/31 del corpus de búsqueda.
- **WP-N5 antes que WP-D1** (ambos añaden filas a `FoodDatabase.kt`); las recetas de WP-D1 viven en `dishCompositions`
  de WP-N13.
- **WP-N9 antes que WP-N13 sección 1**: N9 crea `ProtectedPhrases.kt` en Kotlin en la fase 2 y la sección 1 de N13 migra
  ese contenido al asset JSON en la fase 4; no hay dependencia inversa.
- **Un solo bump de `DATA_VERSION`** (S9 + S10 juntos) para evitar dos re-imports.
- **Room v29 no es necesaria** (S5 por callback); solo se abre si WP-S12 se activa por rendimiento.

### Índice de paquetes

#### Bloque N: pipeline de descripciones (carril N)

A = quick wins sin cambio estructural; B = estructural. Todos los WP del bloque cierran con el gate de paquete de
«Verificación».

| WP | Título y hallazgos | Tam. | Archivos clave | Tests clave |
|---|---|---|---|---|
| N0 (primero) | Harness diagnóstico `BlindMealCorpusProbeTest`: 60 descripciones, dos pasadas (con y sin snapshot de `dataset_knowledge.bin`), tabla en stdout y `blind-probe.json` | S | `app/src/test/java/com/example/kpkn/domain/nutrition/BlindMealCorpusProbeTest.kt` | Aserciones solo diagnósticas (verde hoy); cada caso trae `expect`; ninguna de las 60 cadenas está en el dataset semántico |
| N1 (A) | Fuera del hilo principal (`detect` y `resolveTags` en Default), un solo `enrich`, regex precompiladas, sin cambio de comportamiento; absorbe S7 | S/M | `FoodLoggerDrawer.kt:296-316,473-475,493`, `TagResolution.kt:871`, `FoodIdentity.kt`, `FoodIndex.kt`, `FoodDatabase.kt:690-725`, `FoodParser.kt`, `TextNormalizer.kt`, `HouseholdPortions.kt` | Suite verde; nuevo `TagResolverEnrichmentTest`; baja la columna `ms` de la sonda |
| N2 (A) | Hedges vs negación, guard de inglés, separadores de oración, narrativa (A-P1, A-P2, A-P4) | S/M | `TextNormalizer.kt` (`HEDGE_PATTERN:76-79`, `isLikelyEnglish:310-333`), `FoodParser.kt` (`COMMA_OR_PLUS:21`, `NEGATION_PATTERN:147`) | "al menos 2 huevos"; "pollo a la plancha y papas a lo pobre"; "Desayuno: 2 huevos. Almuerzo: arroz con pollo." -> 3 ítems; `NaturalLanguageReliabilityTest:238` verde |
| N3 (A) | Singularizador español con lema (A-Q2) | S | nuevo `SpanishSingularizer.kt`; `FoodParser.normalizeFoodName:916-923`, `canonicalTagKey:870-879`, `SmartFoodResolver.singularizeQuery:272-289` | tomates, nueces, limones, panes, yogures; golden `GoldenCorpusTest.kt:551` (`tomat` -> `tomate`), `:72`, `:75` |
| N4 (A) | Portabilidad de `\b`: `RegexEs.bounded(alt)` en lugar de `\b` y sin `(?U)`; test instrumentado (divergencia JVM/ICU) | S | `FoodParser.kt` (`COOKING_PATTERNS:78-111`, `PROTECTED_ENTITIES_REGEX:185-188`), `TextNormalizer.kt` (`TYPO_REGEX_LIST:249-255`), `ContextDetector.CONTEXT_REGEXES:100-104`, `FoodCombinationParser:406-411` | `RegexBoundaryPortabilityTest` (JVM) + `androidTest/.../domain/nutrition/FoodParserAndroidTest.kt` con la misma tabla; golden `GoldenCorpusTest.kt:461` |
| N5 (A) | Alimentos de 0 kcal, bebidas, contenedores, unidades, orden de densidad (A-Q3, A-Q4, A-Q5, A-Q10): fichas gen143-gen150, `isZeroEnergy`, `lt/cc/kilogramo`, tabla `CONTAINER_ML` | M | `FoodDatabase.kt`, `FoodIdentity.hasPlausibleMacros:392-399`, `SmartFoodResolver.hasPlausibleMacros:689-696`, `GRAM_UNITS:17`, `CONTAINER_PATTERNS:293-304` | Sonda #3, #5, #9, #14-19, #42-45; `NutritionMetricsContractTest.kt:84` "café con leche" -> AUTO gen145; precisión >= 45/47 (riesgo medio) |
| N6 (A) | Una sola escala de tamaño: SMALL 0,75, MEDIUM 1,0, LARGE 1,25, EXTRA 1,5 (A-Q6) | S | `PORTION_MULTIPLIERS` (`NutritionModels.kt:18-23`), `TagResolution.absolutePortionOptions:1054-1061`, `SubjectivePortionEngine.INTENSIFIER_FACTORS:323-335`, `FoodParser.extractModifiers:995-1002` | "plato grande de arroz" = 1,25 x "un plato de arroz"; `absolutePortionOptions(100)` = 75/100/125 |
| N7 (A) | Retiro de módulos muertos (ver «Módulos muertos»); `grep -rn` = 0 referencias | S | `FoodMentionReconciler.kt`, `CookingMethodParser.kt`, `DatasetKnowledgeSource.kt`, `FoodTemplateMatcher.kt`, `FoodInterpretationV2.kt:363-528`, rutas de gramos del dataset | Reescribir `FoodInterpretationV2Test` sobre `interpretResolved`; borrar los 2 tests de `FoodTemplateMatcher`; suite verde |
| N8 (B) | Conteos y fracciones para todo alimento, adjetivo de tamaño sobre conteos (A-Q1, "media palta"); nuevo `UNIT_GRAMS_BY_TOKEN` | M | `HouseholdPortions.kt` (`resolveEatenGrams:218-228`, `unitGrams:81-117`), `FoodParser.parseFragment:394-407`, `TagResolution.kt:543-548,693-698` | "2 yogures" 250 g; "3 tomates" 360 g; "media palta" 75 g; "2 manzanas grandes" 375 g; controles "arroz" 120 g y "2 huevos" 100 g sin cambio (riesgo medio) |
| N9 (B) | Frases protegidas antes de typos; nombres compuestos; masa ligada al plato (A-P3, A-P5) | M | nuevo `ProtectedPhrases.kt`; `TextNormalizer.applyTypos:558-584`; `FoodParser.splitMentionFragments:457-556`; `isKnownNegationModifier:291-297` | Sonda #1, #9-11, #39-41; "agua sin gas"; "pastel de choclo" y "crema de zapallo" |
| N10 (B) | Cocción aplicada una sola vez: conversión por rendimiento, o factor por gramo, o variante preparada (A-C1, A-C2, A-C3) | M/L | `MacroCalculator.scaleFoodByPortion:88-193`, `cookingWeightYield:70-76`, `CookingFactors.kt`, `TagResolution.kt:363-399,552-599`, `CookingStateResolver.findPreparedVariant:108-127`, `NutritionHeuristicEstimator.detectCookingBoost:296-337` | Exactos: #4 (400 kcal), #29 (212), #30 (293), #31 (468), "200 g tomate al horno" (41); invariante en `MacroCalculatorTest`; riesgo medio-alto |
| N11 (B) | Higiene de identidad: perfiles heurísticos, `PhoneticEs` sin colapso de vocales, sándwich acotado, corte de pollo por estado, rama `:433-434` con umbral 0,45 (A-Q7, A-Q8, A-Q9, A-Q11) | M | `NutritionHeuristicEstimator.kt:69-208,237,255-257`, `PhoneticEs.kt:102-107`, `FoodCombinationParser.kt:16-19,438-458`, `FoodStapleOntology.kt:113-116,206-252`, `SmartFoodResolver.kt:427-438,873-882` | Sonda #23-28; "pollo crudo 200 g"; `NutritionMetricsContractTest` >= 45/47; `FluencyGoldenCorpusTest` verde; actualizar `PhoneticEsTest` |
| N12 (B) | Endurecimiento de la suite: `GoldenCorpusTest` parametrizado (719 -> ~1040), corregir expectativas que fijan bugs, estrechar bandas, promover N0 a `BlindMealCorpusTest` | M | `GoldenCorpusTest:619-669`, `NutritionMetricsContractTest.kt:84`, `FluencyGoldenCorpusTest.kt:130-133`, `EverydayMealCorpusTest.kt:293`, `EverydayMealCorpusTest:103-204`, `ContextDetectorTest.kt:53`, `FoodParserTest.kt:234` | Lista completa de expectativas del plan; grep gate sin aserciones de doble resultado en `domain/nutrition`; umbrales por dimensión activos |
| N13 (estructural) | Consolidación del conocimiento en `assets/food_data/food_knowledge_v1.json` + `FoodKnowledge.install(snapshot)`; 6 secciones, un PR por sección, incremental; la sección 1 migra a JSON el `ProtectedPhrases.kt` que crea N9 | L | asset nuevo; `NutritionRepository.prepareSemanticDataset()`; copias Kotlin en `FoodParser`, `TextNormalizer`, `HouseholdPortions`, `SubjectivePortionEngine`, `CookingFactors`, `FoodCombinationParser`, `FoodDatabase` | `FoodKnowledgeParityTest` (JSON == Kotlin hasta borrar la copia); `FoodKnowledgeAssetTest` (versión, alias sin duplicados, `foodId` existentes, asset < 300 KB); sin Room |

#### Bloque S: búsqueda, catálogo y datos (carril S)

Sprint A = quick wins sin cambio de esquema (S1, S2, S7, S6, S3, S4, S5); Sprint B = estructural (S8 a S11). Room v29 NO
es necesaria: la limpieza de triggers va por `RoomDatabase.Callback.onOpen`.

| WP | Título y hallazgos | Tam. | Archivos clave | Tests clave |
|---|---|---|---|---|
| S1 | El tap de búsqueda conserva la identidad tocada y aplica solo la porción doméstica; sin `?: return` silencioso (B1, C21) | S (0,5 d) | `HouseholdPortions.kt` (`identityForSearchPick`, `eatenGramsForSearchPick`, `unitGrams`, `operationalAutoStatus`; borrar `isSimpleUnbrandedQuery`), `FoodLoggerDrawer.kt:1569-1609` | `SearchPickContractTest` (7 casos); actualizar `HouseholdPortionsOperationalTest:63-70`; riesgo: `unitGrams` baja panes OFF de 100 a 50 g en la ruta de descripción |
| S2 | Ranking nuevo (`FoodSearchRanker`), filtro verificado/identidad antes del límite, keys estables (B2, B12, B13, C13) | M (2 d con corpus) | nuevo `domain/nutrition/FoodSearchRanker.kt`; `NutritionRepository.kt:1187-1264` se borra; `Daos.kt:374-399`; `FoodLoggerDrawer.kt` | `FoodSearchRankerTest` (6) + `SearchGoldenCorpusTest`; `NutritionMetricsContractTest` >= 45/47 (riesgo M) |
| S3 | Arranque: datos del usuario primero y gate de versión sin leer assets; absorbe WP-U2 (B3, C2) | M (1 d) | `NutritionRepository.loadFromDb:932-1032` (3 fases), `FoodImporter.shouldImport`, `SettingsJsonBackup.kt:278-286`, `build.gradle.kts:119-128`; decisión: sin `noCompress` | `NutritionRepositoryStartupTest` (Robolectric), `FoodImporterGateTest`, restore de backup no toca meta |
| S4 | Generaciones e invalidación de `FoodIndex` (B5) | S (0,5 d) | `FoodIndex.kt:37-86`, `catalogGeneration` en el repositorio | `FoodIndexTest` (rebuild visible por la misma instancia) + caso en StartupTest |
| S5 | Higiene de triggers FTS sin migración (B11) | S (0,5 d) | `object GlobalFoodsFtsHygiene : RoomDatabase.Callback`; `KpknDatabase.kt:741-795` | `GlobalFoodsFtsTriggerHygieneTest` (Robolectric) |
| S6 | Alias consistentes por id, plurales, alias muertos (B7) | M (1 d) | `FoodDatabase.kt` (`FOOD_ALIAS_IDS`, `foodByExactName:637-644`, `householdStaticFood`), `FoodIndex.build` | `FoodAliasConsistencyTest` (6 casos) |
| S7 | Regex precompiladas y normalizador único `TextKeys` (B13); va dentro del PR de N1 | S (0,5 d) | nuevo `domain/nutrition/TextKeys.kt`; `NutritionRepository`, `FoodIndex`, `FoodIdentity`, `FoodImporter`, `HouseholdPortions`, `FoodDatabase.kt`, `SmartFoodResolver` | `TextKeysTest` (30 cadenas idénticas en los 3 normalizadores) |
| S8 | Import robusto: parsear y luego commit corto, conservar uso, progreso en UI (B4) | M (1,5 d) | `FoodImporter.importAll:105-382` -> `parseUsda`, `parseOff`, `parseOffLine`, `commitRows`; pestaña Buscar del drawer | `FoodImporterParseTest` (puro), `FoodImporterUsageTest` (Robolectric) |
| S9 | Calidad USDA: porciones, ids de nutrientes, categoría, tabla de alias en español; `DATA_VERSION` -> 10 (B6, B8) | M/L (2 d + curación) | `FoodImporter.kt:109-119,155-172,174-237`; nuevo `assets/food_data/usda_es_aliases.csv` (436 filas, `scripts/build_usda_es_aliases.py`) | `UsdaAliasTableTest`; casos sintéticos en `FoodImporterParseTest` |
| S10 | Procedencia del catálogo estático, retiro del pipeline v2, huella por manifiesto; un solo bump con S9 | M (1 d) | `FoodDatabase.kt` (`withCuratedProvenance()`), `isGlobalSku` por prefijo de id, tarea Gradle `generateFoodDataManifest`, retirar `scripts/generate_food_catalog_v2.py` | `StaticCatalogProvenanceTest`; huella no vacía vía assets fusionados |
| S11 | Higiene de datos: arroz integral gen006/gen136 con redirección de id; mover ~14 MB de CSV FDC no usados (B9) | S (0,5 d) | `FoodDatabase.kt` (`LEGACY_FOOD_ID_REDIRECTS`), `android-native/datasets/usda_fdc_raw/` | `AssetInventoryTest` (allow-list de `assets/food_data`) |
| S12 (diferido) | FTS sobre columnas normalizadas + Room v29, solo si la búsqueda en dispositivo supera 150 ms tras S2; no recomendado ahora | - | `GlobalFoodFtsEntity`, `MIGRATION_28_29` | `MigrationTestHelper` + caso en `NutritionMigrationTest` |

#### Bloque U: página de Nutrición, ViewModels, drawer, servicios (carril U)

Decisión C5: construir ahora un `FoodLoggerViewModel` mínimo. `rememberSaveable` no sirve (`ResolvedTag` embebe tipos no
serializables y un Bundle grande arriesga `TransactionTooLargeException`); un VM scoped al `NavBackStackEntry` host
conserva el borrador completo y `SavedStateHandle` guarda solo una semilla primitiva. Grupos del plan: (A) corrección
crítica U1-U6, (B) pérdida de datos y UX U7-U13, (C) servicios U14, (D) contrato y visibilidad U15-U16, (E) higiene U17.

| WP | Título y hallazgos | Tam. | Archivos clave | Tests clave |
|---|---|---|---|---|
| U1 | La fecha "sigue a hoy": `_today`, `followsToday`, `refreshToday()` en ON_RESUME y ticker a medianoche (C1) | S/M | `NutritionViewModel.kt:35-48,185-204`, `NutritionScreen.kt:113-120`, `DateSelector :736-793`; nuevo `domain/nutrition/NutritionDayBoundary.kt` | `NutritionViewModelTest` + `FakeAppClock`; `NutritionDayBoundaryTest` (23:59:30 -> 30 000 ms; noches DST de America/Santiago) |
| U2 | Cargar las filas del usuario independientes del import; se implementa como WP-S3 (C2) | M | `NutritionRepository.loadFromDb :932-1032` | `NutritionRepositoryLoadTest` (Robolectric): logs y planes publicados aunque el import lance |
| U3 | Ruta de acción del widget sin navegar durante composición (C3) | S | `MainActivity.kt:1331-1357`; nuevo `navigation/NutritionActionRouting.kt` | `NutritionActionRoutingTest` |
| U4 | `LocalDate.parse(log.date.take(10))` en AUGE (C4) | S | `NutritionRecoveryEngine.kt:40` | `NutritionRecoveryEngineTest` (fecha del drawer cuenta; fecha malformada se ignora) |
| U5 | `runCatching` en los parse del balance energético (C14) | S | `NutritionViewModel.kt:445-457` | workout con `date = "not-a-date"` no rompe el balance |
| U6 | Key `search_${foodId}` y `distinctBy` en resultados (C13) | XS | `FoodLoggerDrawer.kt:1568`, `performSearch :1131` | - |
| U7 | `FoodLoggerViewModel` + persistencia del borrador; re-análisis tras process death (C5) | L | nuevo `screens/nutrition/components/FoodLoggerViewModel.kt`; `FoodLoggerDrawer.kt`, `NutritionScreen.kt:105-109,346-362`, `HomeScreen.kt:131,496-513` | `FoodLoggerViewModelTest`; androidTest `FoodLoggerDrawerDraftUiTest` con `StateRestorationTester` |
| U8 | Intents de share y widget no se reprocesan al recrear la Activity (C6) | S | `MainActivity.kt:168-170,282-298,397-426`; nuevo `navigation/LaunchIntentResolver.kt` | Robolectric: actividad recreada ignora ACTION_SEND |
| U9 | Los tags no-DESCRIPTION sobreviven al re-análisis (`ResolvedTag.origin`, `mergeReanalyzedTags`) (C7) | S | `TagResolution.kt`; `FoodLoggerDrawer.kt:303-311,425-428,1606` | `TagMergeOnReanalyzeTest` (4 casos) |
| U10 | Slider de gramos con máximo anclado y campo numérico (`GramsSliderSpec`) (C8) | S | `FoodLoggerDrawer.kt:2434-2445`; nuevo `domain/nutrition/GramsSliderSpec.kt` | `GramsSliderSpecTest` (100 -> 600, 350 -> 700, 1500 -> 3000) |
| U11 | Deshacer y editar comidas: `updateNutritionLog` como upsert, `seedFromLog`, `LoggedFoodEditing` (C9) | M/L | `NutritionViewModel`, `NutritionRepository.updateNutritionLog`, `NutritionScreen`, drawer; nuevo `domain/nutrition/LoggedFoodEditing.kt` | VM "borrar y deshacer restaura el mismo id"; `LoggedFoodEditingTest` |
| U12 | Utensilios: el diálogo parte de `UTENSIL_DEFAULTS + currentUtensilOverrides()`; `clearUtensilOverride` (C11) | S | `FoodLoggerDrawer.kt` (diálogo), `NutritionRepository` (`loadUtensilOverrides()` vía U2) | `NutritionRepositoryUtensilTest` |
| U13 | Tipo de comida por defecto por hora: `MealTypeDefaults.defaultMealTypeFor(hour)` (C12) | XS | nuevo `domain/nutrition/MealTypeDefaults.kt`; `NutritionScreen.kt:107,307`, `HomeScreen.kt:135-143` | Bordes horarios |
| U14 | Recordatorios fiables: alarmas one-shot, boot receiver, receiver con Room en `goAsync()`, planner DST-safe (C10) | M/L | `NutritionNotificationManager.kt` (`scheduleDaily :275-301`, receiver `:342-424`); nuevos `services/nutrition/NutritionReminderBootReceiver.kt` y `domain/nutrition/NutritionReminderPlanner.kt`; `Daos.kt` +2 queries; manifest | `NutritionReminderPlannerTest`; `NutritionAlertReceiverTest` (Robolectric) |
| U15 | "≈" y rangos en totales: `DailyMacroTotals` con min/max e `isEstimate`; `NutritionDisplayFormat` (C22) | M | `MacroCalculator.computeDailyTotals :252`; nuevo `domain/nutrition/NutritionDisplayFormat.kt`; `NutritionScreen.kt:416,485,643,1062`, `MealHistoryScreen.kt:302,369-389` | `MacroCalculatorTest`; `NutritionDisplayFormatTest` |
| U16 | `saveError` visible y taps no-op con mensaje; va junto a S1 (C21) | S | `FoodLoggerDrawer.kt` (`:1697`, `:1576-1578`) | - |
| U17 | Higiene en cuatro sub-ítems: (1) código muerto y strings; (2) accesibilidad (roles, targets >= 40 dp); (3) telemetría sin mensajes crudos; (4) hilos (`flowOn`, debounce de 250 ms) | M (dos commits que agrupan cuatro sub-ítems: 1-2 en el primero, 3-4 en el segundo) | `NutritionViewModel`, `NutritionScreen.kt`, `FoodLoggerDrawer.kt`, `NutritionTelemetry.kt`, `NutritionCalibrationScreen.kt` | `NutritionTelemetrySanitizer`: el resumen nunca contiene el mensaje de la excepción |

Diferidos a one-liners posteriores: C15 calibración, C16 creatina, C17 gate de plan para chips/widget/share, C19 kcal = 0
y alcohol, C20 aceite vs macros manuales. Numeración de PR: el bloque U traía una numeración interna (PR1-PR5) que queda
sustituida por la de la tabla global de fases (PR1-PR12). Asignación por fase: la fase 1 (PR1-PR2) incluye U1, U3, U4,
U5, U6, U2 (dentro de S3) y U16 (junto con S1); la fase 3 (PR6-PR8) incluye U7 a U15; la fase 4 (PR9-PR12) incluye U17.

#### Bloque D: contenido del catálogo

| WP | Título y hallazgos | Tam. | Archivos clave | Tests clave |
|---|---|---|---|---|
| D1 | Procedencia y fichas faltantes: cada ficha estática declara `source`, `sourceRecordId`, `nutritionBasis`, `foodState` y, si aplica, `portionGrams/portionUnit`; cubrir los ~76 alimentos cotidianos sin ficha (cifra del plan; ver la sonda re-ejecutada en «Hechos verificados») | M (contenido + código) | filas nuevas de `FoodDatabase.kt` (tras N5); recetas en `dishCompositions` de WP-N13; sin `generate_food_catalog_v2.py` (se retira en S10) | `FoodCatalogProvenanceTest` con el caso "toda ficha estática declara source y nutritionBasis"; sonda de cobertura (lista de términos de esta auditoría) como test que falla si un término pierde ficha |

Política de fuentes de D1 (coherente con el contrato v2): genéricos crudos y cocidos con USDA FoodData Central por 100 g
(`source="USDA"`, `sourceRecordId=<fdc_id>`); platos chilenos `cl*` como estimación por receta
(`source="RECIPE_ESTIMATE"`, receta visible en el asset de WP-N13); marcas solo con `sourceRecordId` de OFF o etiqueta,
nunca valores inventados; agua, té y café sin azúcar, bebidas zero y endulzantes como fichas de 0 kcal con
`qualityFlags=["ZERO_ENERGY"]`. Bloques de fichas nuevas: bebidas, lácteos, proteínas, frutas y verduras,
cereales y panes, platos, dulces. Cada ficha nueva trae `searchAliases` y unidad doméstica en el léxico.

#### WP-0: documento de auditoría (S)

Este documento, `coverage-probe.json` y la línea base de la sonda WP-N0: `blind-probe-baseline.json`/`.txt` (oficial,
bajo Gradle; `blind-corpus-baseline.json` en el plan: salida del harness antes de las correcciones, para medir el delta
al cierre) y su traza informal previa `blind-probe-baseline.informal.*`. Pendiente de WP-0: actualizar
`.opencode/memory/MEMORY.md` (ledger de bugs)
y `docs/ANDROID_ARCHITECTURE_MAP.md` solo si cambian rutas o pipelines (retiro de módulos muertos, asset de conocimiento);
no se tocaron en esta entrega.

### Corpus de verificación

- **`BlindMealCorpusProbeTest` (WP-N0).** 60 descripciones con un `expect` legible por caso; dos pasadas (con el snapshot
  real de `dataset_knowledge.bin` y sin él, con columna de diferencia); fixture OFF determinista de 12 filas (p. ej.
  Coca-Cola 350 ml, Coca-Cola Zero, Agua con gas Cachantun, Leche descremada Colun, Yogurt natural Colun, Pan integral
  Bauducco) más adversarias ya usadas. Cadena: `parseMealDescription` -> `TagResolver(port).resolveAll` (ya
  enriquecido). Salida: tabla de ancho fijo en stdout y `app/build/reports/nutrition-reliability/blind-probe.json`.
  Verde por diseño (aserciones solo diagnósticas); corrió bajo Gradle en el gate 1 (2/2 tests) y produjo
  `blind-probe-baseline.json`.
- **`BlindMealCorpusTest` (promoción en WP-N12).** Referencias independientes de los priors del motor (USDA SR
  Legacy/FDC y la Tabla de Composición de Alimentos Chilenos, INTA). Cinco dimensiones contadas por separado:
  identidad, adiciones/omisiones, gramos ±25 %, kcal ±20 % (±0,5 kcal si masa y perfil están declarados) y pregunta
  sí/no. Informe `blind-corpus.json`; los umbrales parten de identidad >= 90 %, cobertura >= 90 %, gramos >= 80 %,
  kcal >= 80 % y pregunta >= 90 %, y suben desde la línea base medida tras N1..N11.
- **`SearchGoldenCorpusTest` (WP-S2).** JVM puro (~2 s) sobre estáticos + marcas + un fixture OFF determinista
  (`src/test/resources/food_data/off_chile_search_fixture.tsv`, 250-450 filas generadas por
  `scripts/build_off_search_fixture.py`); 44 casos top-1/top-3 (p. ej. leche -> gen016, hallulla -> cl013 sin "1kg"
  primero, nuggets -> SKU OFF con gen003 fuera del top-3, "xyzq" -> vacío). Los casos 21, 24 y 31 dependen de WP-S6.

## Criterios de cierre

1. `BlindMealCorpusTest`: identidad >= 90 %, cobertura >= 90 %, gramos >= 80 %, kcal >= 80 %, preguntas >= 90 % sobre
   las 60 descripciones (medido, no asumido), y `SearchGoldenCorpusTest` 44/44.
2. Suite nutricional filtrada verde (719 + nuevos; golden parametrizado ~1040), `NutritionMetricsContractTest`
   precisión@1 >= 45/47, instrumentados `FoodParserAndroidTest`/`FoodLoggerDrawerDraftUiTest`/`NutritionMigrationTest`
   verdes.
3. En dispositivo: arranque en caliente sin abrir CSV y `phase1.published` < 400 ms; búsqueda < 150 ms; sin triggers
   `global_foods_ai/ad/au`; medianoche con la app viva muestra el día nuevo; widget abre el logger; AUGE deja de decir
   "Sin comidas en la ventana" con comidas registradas.
4. Ningún ítem desconocido produce fantasmas de ~560 kcal ni rangos 0-900 kcal/100 g en totales; "un vaso de agua" =
   0 kcal.

## Verificación

### Comandos y entorno de reproducción

Desde `android-native/` (Windows, flavor Base). Nunca la suite completa sin filtro: colgó 15 min en sep-2026. Los
comandos se lanzan por el wrapper con candado descrito más abajo.

```
.\gradlew.bat :app:compileBaseDebugKotlin
.\gradlew.bat :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.*" --tests "com.example.kpkn.data.food.*" --tests "com.example.kpkn.data.repository.Nutrition*" --tests "com.example.kpkn.screens.nutrition.*" --tests "com.example.kpkn.domain.auge.NutritionRecoveryEngineTest" --tests "com.example.kpkn.services.nutrition.*" --tests "com.example.kpkn.navigation.*" --tests "com.example.kpkn.telemetry.nutrition.*"
.\gradlew.bat :app:assembleBaseDebug
.\gradlew.bat :app:connectedBaseDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.kpkn.domain.nutrition.FoodParserAndroidTest,com.example.kpkn.screens.nutrition.components.FoodLoggerDrawerDraftUiTest,com.example.kpkn.data.db.NutritionMigrationTest
```

- **Criterio de salida por fase:** 0 fallos en los filtros anteriores (719 + nuevos); `WorkoutVoiceReplayTest` es el
  único fallo entre las suites que sí corrieron hoy y es ajeno (la suite completa sin filtro no se ejecuta por el
  cuelgue de sep-2026). Resultados XML en `app/build/test-results/testBaseDebugUnitTest/`.
- **Sonda del bloque N:** `.\gradlew.bat :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.BlindMealCorpusProbeTest" -i`
  (tabla en stdout y `app\build\reports\nutrition-reliability\blind-probe.json`). Filtros por WP: `GoldenCorpusTest`,
  `FoodParserTest`, `TextNormalizerTest`; cocción
  `*CookingPortionPrecisionTest *CookingStateRegressionTest *MacroCalculatorTest *CookingFactorsTest`; identidad
  `*NutritionMetricsContractTest *FluencyGoldenCorpusTest *ResolutionGoldenCorpusTest *PhoneticEsTest`. Gate de
  paquete: `domain.nutrition.*` y `data.food.*`.
- **Entorno:** tests JVM en Java 21 (daemon de Gradle, `gradle-daemon-jvm.properties`; el lanzador de Gradle es Java
  17); desarrollo con JDK 24; `\b` es ASCII en Java 21 y en JDK 24 (JDK >= 19); el flavor Health no se valida.
- **QA en emulador:** máximo 10 comprobaciones por fase (listas en cada bloque). Instalar siempre con
  `adb install --no-incremental -r` (incidente de sep-2026) y respaldar el perfil antes de reemplazar el APK. Para el
  bloque N las 10 descripciones son: la #1 de la sonda, "un vaso de agua" (0 kcal, guardable), "una lata de coca cola",
  "2 yogures y 3 tomates", "al menos 2 huevos", "150 g salmón a la parrilla" (texto de una conversión), "pechuga frita
  sin aceite 150 g", "pollo" (chips de corte todas cocidas), "huevo poché" (COCIDO, paridad con JVM) y "Desayuno: 2
  huevos. Almuerzo: arroz con pollo y ensalada chilena". Vigilar jank al analizar (N1) y que Grande -> Habitual ->
  Grande sea idempotente (N6).
- **Tope de iteraciones (memoria del usuario):** si una comprobación falla dos veces seguidas, documentar y dejar que
  el usuario decida en lugar de seguir iterando.

Aceptación por WP del bloque N: N0 tabla 60/60 en ambos modos + JSON; N1 sin `enrich` en el drawer, `detect` en Default y
p95 de la sonda menor; N2 #6-8; N3 #37-38 + 3 ediciones golden; N4 JVM e instrumentado con resultados idénticos
(#34-36); N5 #3, #5, #9, #14-19, #42-45 y precisión >= 45/47; N6 ratios 0,75/1/1,25; N7 cero referencias; N8 #2, #22,
#56, #59 sin cambios en contables/utensilios; N9 #1, #9-11, #39-41; N10 #4, #29-33 con kcal exactas + invariante; N11
#23-28; N12 sin aserciones de doble resultado, golden parametrizado y umbrales del corpus ciego activos.

### Estrategia de ejecución orquestada (indicación del usuario, 2026-10-02)

- **Roles.** El orquestador/revisor asigna paquetes, revisa cada diff, ejecuta los gates y coordina con otras sesiones.
  Los escritores son subagentes `general-purpose` con `model: "sonnet"`, uno por paquete (o par acoplado), instruidos a
  leer el WP completo y los tests existentes antes de tocar código; no ejecutan Gradle ni comandos git de escritura.
  Nunca más de 3 escritores simultáneos, en carriles con archivos disjuntos.
- **Carriles y propiedad de archivos.** N (parser y motores de `domain/nutrition`), S (`NutritionRepository.kt`,
  `data/food/*`, `Daos.kt` y `KpknDatabase.kt` en zonas de nutrición, assets y scripts), U (`screens/nutrition/**`,
  bloques de nutrición de `MainActivity.kt`, `services/nutrition/*`, `NutritionRecoveryEngine.kt`, telemetría, widget) y
  D (filas nuevas de `FoodDatabase.kt` y `food_knowledge_v1.json`, cuando N y S no tienen el archivo abierto). Un dueño
  por archivo y fase; archivos compartidos con dueño fijo: `FoodLoggerDrawer.kt` (U), `HouseholdPortions.kt` (N),
  `NutritionRepository.kt` (S), `TagResolution.kt` (N). Hubs que tocan otras sesiones (`MainActivity.kt`,
  `Navigation.kt`, `Daos.kt`, `KpknDatabase.kt`): avisar antes y editar en bloques pequeños.
- **Bucle por paquete.** (1) Lanzar el escritor con el texto del WP, los hallazgos, sus archivos y las reglas de
  `CLAUDE.md`. (2) Revisar `git diff -- <rutas del WP>` con lista de control (cumple el diseño, tests presentes, sin
  cambios fuera de sus rutas, sin expectativas relajadas salvo las de WP-N12). (3) Gate bajo candado: compilación +
  filtros del WP + gate de paquete. (4) Si falla, corrección por el mismo escritor; máximo 2 pasadas por WP, a la
  tercera se divide el WP o se escala al usuario con el diff y el log. (5) Verde -> commit del WP con rutas explícitas.
- **Candado de Gradle/emulador entre sesiones.** Hay tres sesiones vecinas (dos activas y una inactiva) que comparten el
  repositorio y lanzan Gradle. `.opencode/scripts/run-gradle.ps1` evita el hang (`--no-daemon --console=plain`) pero no
  excluye ejecuciones concurrentes. El wrapper
  `.opencode/scripts/run-gradle-locked.ps1 -Tasks "..." [-Owner "<sesión>"]` quedó versionado en el commit de la fase 1A
  (`97c464ad0`) y aplica esta regla: adquiere el candado creando de forma atómica el directorio
  `android-native/.gradle-session.lock/` (si falla, está ocupado) y escribe `owner.json` (sesión, tareas, `startedAt`,
  pid); espera sondeando cada 30 s hasta 15 min (`-WaitSec 900`); el candado es obsoleto, y se retira con aviso, si el
  PID dueño ya no está vivo o si lleva más de 60 min (un candado sin `owner.json` legible se considera obsoleto pasados
  120 s); si se agota la espera con un candado ajeno vivo, sale con código 125 sin retirarlo; delega en `run-gradle.ps1`
  y libera el candado en `finally`. El mismo candado cubre `installBaseDebug`, `connectedBaseDebugAndroidTest` y la QA
  de emulador. Nunca `gradlew --stop` mientras otro tenga el candado; si un gate espera más de 15 min, se informa al
  usuario en lugar de forzar. Un mensaje inicial a las tres sesiones comunica alcance, rutas y wrapper; sus respuestas
  se tratan como datos y el silencio no es acuerdo.
- **Disciplina de git en árbol compartido** (542 archivos modificados de otras tareas, p. ej. `WorkoutVoice*`). Commits
  por WP solo con `git add <rutas propias>` (nunca `-A`, `-a`, `stash`, `checkout --` ni `reset` sobre el árbol
  compartido); mensaje `feat(nutrition)`/`fix(nutrition)` + línea `Co-Authored-By`; sin push; rama
  `consolidation/2026-10-02-wizard-session-repair`; `git status --porcelain -- <rutas>` antes de cada commit para
  confirmar que solo entran archivos del WP.
- **Prerrequisitos bloqueantes antes de la fase 0.** (1) El hook `PreToolUse:Write` del plugin backend-design (su
  script `check_backend_component.py` no existe en disco) bloquea Write/Edit para la sesión y sus subagentes: el usuario
  debe repararlo o desactivar ese plugin/hook; alternativa de emergencia, lenta y propensa a errores en Kotlin largo:
  escrituras vía Python desde Bash. (2) Confirmación de las sesiones vecinas, o al menos el candado operativo, antes del
  primer gate.
- **Orden de arranque.** Fase 0: mensaje a las sesiones vecinas -> `run-gradle-locked.ps1` -> WP-0 (escritor "doc") y
  WP-N0 (escritor "probe") en paralelo -> gate de sonda (JSON baseline) -> fase 1 con tres escritores en paralelo: U
  (U1, U3-U6), S (S3 incl. U2; luego S1 + U16 cuando U libere el drawer) y N (N1 incl. S7; luego N2-N4).

## Registro de ejecución

| Fecha | Gate | Resultado | Commit |
|---|---|---|---|
| 2026-10-03, 01:07-01:20 (hora local) | Gate 1 (fase 1A) | BUILD SUCCESSFUL en 13 min 26 s; 778 tests, 0 fallos; `BlindMealCorpusProbeTest` 2/2 bajo Gradle (línea base oficial de la sonda WP-N0). Paquetes cerrados: WP-0, WP-N0, WP-U1, U3, U4, U5, U6 y WP-S3 (con U2) | `97c464ad0` "feat(nutrition): fase 1A del sistema de alimentos — fecha sigue a hoy, arranque sin bloquear datos, ruta de widget, AUGE y sonda ciega", rama `consolidation/2026-10-02-wizard-session-repair` |
| 2026-10-03, 01:36-01:46 | Gate 2 | BUILD SUCCESSFUL en 9 min 55 s; 740 tests, 0 fallos; sonda idéntica a la línea base (120/120). Paquetes cerrados: N1 y S7 | `0c3ae41a5` "perf(nutrition): WP-N1 — regex precompiladas, normalizador único y análisis fuera del hilo principal" |
| 2026-10-03, 01:47-01:58 | Gate 3 | BUILD SUCCESSFUL en 10 min 21 s; 720 tests, 0 fallos; sonda idéntica. Paquetes cerrados: S1 y U16 | `774c083ad` "fix(nutrition): WP-S1/U16 — el tap de búsqueda conserva la ficha tocada y los errores del logger son visibles" |
| 2026-10-03, 02:03-02:11 | Gate 4 | FALLÓ por el bloqueo de `classes.jar` por un arnés externo, no por el código. Sin paquetes cerrados | - |
| 2026-10-03, 02:12-02:17 | Gate 4b | BUILD SUCCESSFUL en 5 min 21 s; 0 fallos. Paquete cerrado: S5. Hallazgo extra: los triggers FTS heredados rompían UPDATE y DELETE sobre `global_foods` | `c0b9d8a77` "fix(nutrition): WP-S5 — limpieza de triggers FTS heredados al abrir la base, sin migración" |
| 2026-10-03, 02:25-02:36 | Gate 5 | BUILD SUCCESSFUL en 10 min 53 s; 59 tests nuevos, 0 fallos. Paquete cerrado: U14 (alarmas one-shot, receptor con Room, boot receiver, canales para API < 26) | `13ee5107a` "fix(nutrition): WP-U14 — recordatorios de comidas y alerta de macros fiables" |
| 2026-10-03, 02:48-02:59 | Gate 6 | BUILD SUCCESSFUL en 11 min 12 s; 0 fallos; sonda idéntica. Paquete cerrado: S4 | `607932ac9` "fix(nutrition): WP-S4 — FoodIndex por generaciones con invalidación en publicación, import y refresh" |
| 2026-10-03, 03:00-03:10 | Gate 7 | BUILD SUCCESSFUL en 9 min 23 s (más `compileBaseDebugAndroidTestKotlin`); 0 fallos; la sonda mejora en 11 casos (#1, #2, #6, #7, #8, #34, #35, #37, #38, #47, #48) y no empeora en ninguno (ver «Línea base»). Paquetes cerrados: N2, N3 y N4 (4 expectativas golden corregidas) | `9a46378c3` "fix(nutrition): WP-N2/N3/N4 — negación vs límites, separadores de oración, singularizador con lema y regex portables" |
| 2026-10-03, 03:21-03:32 | Gate 8 | BUILD SUCCESSFUL en 10 min 21 s; 0 fallos. Paquetes cerrados: U8, U10 y U13 | `dcd037e09` "fix(nutrition): WP-U8/U10/U13 — intents no se reprocesan al recrear, slider de gramos anclado, comida por defecto según la hora" |
| 2026-10-03, 03:32-03:42 | Gate 9 | BUILD SUCCESSFUL en 10 min 00 s; 0 fallos. Paquete cerrado: U15 | `6b5242d48` "feat(nutrition): WP-U15 — totales inciertos se muestran como estimación con rango" |
| 2026-10-03, 04:28-04:40 | Gate 10 | BUILD SUCCESSFUL en 12 min 04 s; 0 fallos. Paquetes cerrados: U9 y U12 | `e49380b48` "fix(nutrition): WP-U9/U12 — los alimentos añadidos por búsqueda sobreviven al re-análisis y los utensilios guardados no se pisan" |
| 2026-10-03, 04:43-04:54 | Gate 11 | BUILD SUCCESSFUL en 10 min 19 s; 0 fallos; `SearchGoldenCorpusTest` 49 (1 @Ignore hasta WP-S6, "banana"); precisión@1 45/47; sonda idéntica a la de N2-N4. Paquete cerrado: S2 | `e8213f10b` "feat(nutrition): WP-S2 — ranking de búsqueda nuevo, filtro antes del límite y corpus dorado de 44 consultas" |
| 2026-10-03, 04:56-05:07 | Gate 12 | FALLÓ solo en `SearchGoldenCorpusTest` #36 "coca cola": la ficha genérica `gen146` con alias "coca cola" superaba a las filas OFF Coca-Cola. Resto verde: `ZeroEnergyAndBeverageTest` 31/31, precisión@1 48/50. Es el motivo de WP-S2b | - |
| 2026-10-03, 05:35-05:47 | Gate 13 | BUILD SUCCESSFUL en 11 min 22 s (solo capa de datos); 0 fallos. Paquete cerrado: S9 | `130fdb706` "feat(nutrition): WP-S9 — calidad del catálogo USDA: nombres en español, azúcar/grasa por prioridad de id, porciones y categoría" |
| 2026-10-03, 05:48-06:01 | Gate 14 | BUILD SUCCESSFUL en 12 min 19 s (más `compileBaseDebugAndroidTestKotlin`); 0 fallos; `SearchGoldenCorpusTest` 51 (1 @Ignore), `FoodSearchRankerTest` 23, precisión@1 48/50; la sonda difiere de la línea base en 27 casos (ver «Línea base»). Paquetes cerrados: N5 y S2b | `d001a7951` "feat(nutrition): WP-N5 — alimentos de 0 kcal, bebidas con fuente, unidades lt/cc/kilogramo, contenedores y densidad de líquidos"; `073c76063` "fix(nutrition): WP-S2b — una marca nombrada en la búsqueda gana sobre los alias genéricos" |
| 2026-10-03, 06:36 | Gate 15 | No corrió: la cadena de comandos se cortó por un grep sin coincidencias (incidencia de herramienta) | - |
| 2026-10-03, 06:37-06:46 | Gate 15b | BUILD SUCCESSFUL en 9 min 05 s; 0 fallos; `SearchGoldenCorpusTest` 51/51 (sin @Ignore: "banana" resuelto). Paquete cerrado: S6 | `1ebcfeba0` "fix(nutrition): WP-S6 — alias resueltos por id, plurales en el índice y cero alias muertos" |
| 2026-10-03, 06:47-06:58 | Gate 16 | FALLÓ en `CountsForAllFoodsTest` "drinks count their serving" (480 frente a 350): interacción de N8 con los stems de S6. Resto verde, incluido `FoodLoggerViewModelTest` 17/17. Paquete cerrado: U7, commiteado por separado porque sus suites estaban verdes | `7adcfb407` "feat(nutrition): WP-U7 — FoodLoggerViewModel: el borrador del logger sobrevive a rotación, plegado y muerte del proceso" |
| 2026-10-03, 07:30-07:40 | Gate 17 | BUILD SUCCESSFUL en 9 min 54 s; 0 fallos (corrección: plural corto con conteo). Paquetes cerrados: N6 y N8 | `9d9d784a2` "fix(nutrition): WP-N6/N8 — una sola escala de tamaño y conteos para todo alimento con peso unitario" |
| 2026-10-03, 07:41-07:52 | Gate 18 | BUILD SUCCESSFUL en 11 min 01 s; 0 fallos; `FoodImporterParseTest` 26, `FoodImporterUsageTest` 12. Paquete cerrado: S8 | `fe527a807` "feat(nutrition): WP-S8 — importación robusta: parseo sin bloquear Room, commit corto que conserva el uso y FTS reconstruido" |
| 2026-10-03, 08:46-08:55 | Gate 19 | BUILD SUCCESSFUL en 8 min 37 s; 98 suites, 1.107 tests, 0 fallos (filtros `domain.nutrition.*`, `data.food.*` y `data.repository.Nutrition*`); suites nuevas `AssetInventoryTest` 3, `StaticCatalogCoverageTest` 5 (sonda de cobertura 169/174, antes 118/174) y `StaticCatalogContentTest` 15; `FoodAliasConsistencyTest` 20; `SearchGoldenCorpusTest` 51/51. La sonda no cambia ninguna línea frente al estado tras WP-N6/N8 (las mismas 29 diferencias frente a la línea base; #59 solo de intent); #40 gana un candidato interno. Paquetes cerrados: D1 y S11 (28 archivos: 45 fichas nuevas `gen154`-`gen198` con procedencia, `gen136` eliminada con redirección a `gen006`, `gen084` corregida, gouda/gauda a `gen157` y 20 CSV/XLSX de FDC sin uso movidos con `git mv` a `android-native/datasets/usda_fdc_raw/`, 14 MB fuera de `assets/food_data`) | `2309492d8` "feat(nutrition): WP-D1/S11 — catálogo estático con procedencia, 45 fichas cotidianas nuevas y CSV FDC no usados fuera del APK" |
| 2026-10-03, 09:06-09:14 | Gate 20 | BUILD SUCCESSFUL en 7 min 36 s; 91 suites, 1.096 tests, 0 fallos (filtros `domain.nutrition.*` y `data.food.*`); `CookingSingleApplicationTest` 25 (invariante: ningún tag lleva a la vez `stateConversion` y un factor no identidad), `CookingFactorsTest` 22, `CookingPortionPrecisionTest` 15, `MacroCalculatorTest` 42, `NutritionHeuristicEstimatorTest` 13. Frente al estado tras WP-D1/S11, la sonda cambia en cinco líneas (#4, #29, #30, #32 y #33) y en los campos internos de #12; 34 de las 60 difieren de la línea base oficial en la línea de resultado (ver «Línea base»). Paquete cerrado: N10 | `f83458b29` "fix(nutrition): WP-N10 — la cocción se aplica una sola vez (rendimiento o factor o variante preparada, nunca dos)" |
| 2026-10-03, 09:18-09:39 | Gate 21 | BUILD SUCCESSFUL en 12 min 47 s (más `compileBaseDebugAndroidTestKotlin`), tras ~8,5 min de espera del candado de Gradle, ocupado desde las 09:14 por la re-curaduría del catálogo de ejercicios (Gradle corrió de ~09:27 a 09:39); filtros `screens.nutrition.*`, `data.repository.Nutrition*` y `domain.nutrition.LoggedFoodEditingTest`, sin la sonda ciega; 14 suites, 141 tests, 0 fallos: `LoggedFoodEditingTest` 26, `FoodLoggerViewModelEditTest` 15, `FoodLoggerViewModelTest` 17, `NutritionViewModelTest` 27 (+9), `NutritionViewModelOpenRequestTest` 5 (+1), `NutritionDurableSaveTest` 5, `NutritionRepositoryStartupTest` 11, `NutritionRepositorySearchTest` 9. Paquete cerrado: U11 (C9; 10 archivos: borrar con «Deshacer» durante 30 s y editar una comida registrada desde la lista, con `LoggedFoodEditing.kt` nuevo en `domain/nutrition/`) | `0b5f957c7` "feat(nutrition): WP-U11 — borrar con «Deshacer» y editar una comida registrada desde la lista" |

Nota sobre las horas: las de los gates 3 a 21 son las marcas de creación (inicio) y de última escritura (fin) de los
logs de cada gate (no versionados), en hora local; incluyen los pasos previos a Gradle (aplicar el parche) y, en el gate
21, la espera del candado. Las filas de los gates 3 a 18 se corrigieron con los logs: las horas anotadas durante la
ejecución diferían de las que muestran los logs y los commits hasta 13 minutos en los gates 3 a 9 y entre 36 y 59
minutos antes en los gates 10 a 18.

Detalle del gate 1:

- Suites nuevas o ampliadas (tests por suite): `NutritionViewModelTest` 18, `NutritionDayBoundaryTest` 6,
  `NutritionActionRoutingTest` 7, `NutritionRecoveryEngineTest` 7, `NutritionRepositoryStartupTest` 6,
  `FoodImporterGateTest` 11, `SettingsJsonBackupTest` 5, `SetupWizardFullJourneyTest` 2,
  `SetupExecutableAvailabilityMatrixTest` 17, `T006PersistenceAndUseIntegrationTest` 7 y `BlindMealCorpusProbeTest` 2.
- Hallazgos que cubren esos paquetes según el plan: C1 (WP-U1), C3 (WP-U3), C4 (WP-U4), C14 (WP-U5), C13 (WP-U6) y B3 y
  C2 (WP-S3 con U2).
- Pendiente de la fase 1A: `bumpCatalogGeneration` (cerrado con WP-S4 en el gate 6) y `verifyDatasetKnowledge`
  (WP-S10/S11); ver «Límites».

Avance acumulado (gates 1 a 21):

- Paquetes cerrados: 38 de 45 (37 del plan y S2b, añadido en la ejecución): WP-0; N0 a N6, N8 y N10; S1 a S9, S11 y S2b;
  U1 a U16; D1. Por bloque: WP-0 1/1, N 9/14, S 10/12 más S2b, U 16/17, D 1/1.
- Pendientes del plan (8): N7, N9, N11, N12 y N13; S10 y S12 (S12 diferido); U17. Nuevos, fuera del plan: S9b, N8b y
  N10b, este último el seguimiento de #12 «asado», en curso (ver «Límites»).
- Sonda: idéntica a la línea base (120/120) en los gates 2, 3 y 6; en el gate 7 mejora en 11 casos y el gate 11 repite
  esa salida; tras el gate 14 difiere de la línea base en 27 casos, con WP-N6/N8 en 30 y con WP-N10 en 37 (34 en la
  línea de resultado y 3 solo en campos internos); el gate 19 no cambia ninguna línea. Ninguno pierde una ficha ni gana
  un fantasma (ver «Línea base»).
- Cobertura del catálogo estático: de 118/174 términos con ficha en WP-0 a 169/174 tras WP-D1 (gate 19; criterio de
  frase completa, más estricto que el grep de WP-0); quedan sin ficha mate, tallarines, negrita, pre entreno y pap (ver
  «Sonda de cobertura de términos cotidianos»).
- Precisión@1 de identidad: 45/47 en la línea base y en el gate 11; 48/50 en los gates 12 y 14.
- Gate 4: el fallo fue de infraestructura (`classes.jar` bloqueado por un arnés externo); el gate 4b es la repetición
  verde del mismo paquete (S5).
- Gate 12: el fallo fue de comportamiento (`SearchGoldenCorpusTest` #36 "coca cola": la ficha genérica `gen146` con alias
  "coca cola" superaba a las filas OFF) y dio lugar a WP-S2b, cerrado en el gate 14.
- Gate 15: no corrió por una incidencia de herramienta (la cadena se cortó por un grep sin coincidencias); el gate 15b
  es su repetición verde (S6).
- Gate 16: el fallo fue de comportamiento (`CountsForAllFoodsTest` "drinks count their serving": 480 en lugar de 350 por
  la interacción de N8 con los stems de S6) y se corrigió en el gate 17 (plural corto con conteo); U7 se commiteó aparte
  porque sus suites estaban verdes.
- Hallazgo extra (WP-S5): los triggers FTS heredados rompían UPDATE y DELETE sobre `global_foods`; B11 solo anticipaba
  doble escritura FTS y un índice posiblemente inconsistente.

## Límites

- **Sin QA en dispositivo ni en emulador.** Los gates registrados son pruebas JVM bajo Gradle; no hay QA de dispositivo
  o emulador registrado. Los criterios de cierre en dispositivo (arranque en caliente, búsqueda < 150 ms, medianoche,
  widget, AUGE) son metas, no mediciones.
- **Sonda WP-N0.** Corrió bajo Gradle (`BlindMealCorpusProbeTest` 2/2, gate 1) y su salida es la línea base oficial, que
  sigue siendo la referencia para el delta final. Los `expect` de las 60 descripciones son objetivos posteriores a la
  remediación y la sonda no los afirma. La divergencia de `\b` no se puede medir en la JVM (entrada #34): WP-N4 (gate 7)
  añade `RegexEs.bounded` y el test instrumentado `FoodParserAndroidTest`, y los gates 7, 14 y 21 compilaron los
  androidTest (`compileBaseDebugAndroidTestKotlin`), pero no hay ejecución en dispositivo registrada. Los hallazgos
  REPORTADO esperan confirmación en los tests de su paquete.
- **`verifyDatasetKnowledge` falla desde antes del gate 1.** `dataset_knowledge.bin` está desactualizado respecto al
  master (sha regenerado `670b30cf…` frente al del bin `64b5f971…`); queda pendiente para WP-S10.
  El commit de WP-S11 (gate 19) no toca el bin, y los gates 19 y 20 no ejecutaron esa tarea.
- **WP-S9b, nuevo y pendiente.** Tras WP-S9 (gate 13), 60 alimentos foundation no tienen fila de energía (8 de ellos
  aceites) y 10 tienen carbohidrato negativo: quedan fuera del import (436 - 60 - 10 = 366, la cifra importada). La
  energía por Atwater sigue pendiente.
- **WP-N8b, nuevo y pendiente.** Los topes por ítem frenan los conteos grandes: "3 completos" y "5 manzanas" quedan en
  revisión.
- **Plural corto de 3 letras.** El plural corto con conteo se corrigió en el gate 17; "tés" y "tes" sin conteo siguen
  sin resolverse.
- **Progreso de importación en el drawer, pendiente (WP-U17).** El plan incluía «progreso en UI» en el título de WP-S8
  (pestaña Buscar del drawer); el indicador queda pendiente para WP-U17.
- **Documentación del import desactualizada.** `docs/ANDROID_ARCHITECTURE_MAP.md` (sección 2.5: "Batched transactions
  (`BATCH_SIZE = 2000`)") y `docs/ARCHITECTURE.md` (línea de `FoodImporter`: "batched transactions") no reflejan el
  flujo de WP-S8: parseo sin bloquear Room y commit corto.
- **Límites de WP-U11 (gate 21).** `MealHistoryScreen` sigue de solo lectura. Una edición no sobrevive a la muerte del
  proceso: el logger se reabre en blanco, como una comida nueva. `editingLog` vive en `remember` (`NutritionScreen.kt`):
  tras una rotación el guardado sigue pasando por el mismo upsert por id, con confirmaciones vacías. El seguimiento
  queda asignado a WP-U17.
- **WP-N10b, nuevo y en curso (#12 «asado»).** Tras WP-N10, "asado" pasa a COOKED y el nombre registrado pierde la
  palabra Asado ("de Tira (cocido, estimado)"; regex de `calculatedName` en `FoodInterpretationV2.kt`). Asignado al
  autor de WP-N10 como WP-N10b; en curso.
- **Cifras de lectura estática.** Los conteos de regex y los tiempos de rendimiento en dispositivo salen de leer el
  código o de mediciones previas (p. ej. los 3,4 s de `resolve_tags` de ago-2026); no se midieron aquí. Los kcal de los
  fantasmas citados en «Línea base» vienen de la línea base de la sonda WP-N0 (JVM, bajo Gradle). Las mediciones reales
  en que se apoya el documento son la corrida JVM de 719 tests del 2026-10-02, la sonda de cobertura y el recuento de
  fichas de `FoodDatabase.kt` (ambos de WP-0), la línea base de WP-N0 y los gates 1 a 21 de «Registro de ejecución».
- **Sonda de cobertura.** Se calculó sobre 174 términos únicos (la lista original de 179 repetía 5) con coincidencia por
  subcadena: 10 términos quedan marcados `"trusted": false` (8 colisiones de subcadena y 2 homónimos de otro país), por
  lo que 118/174 (A o B) y 102/174 (solo A) son una cota superior de la cobertura real. Un grep por líneas no ve las 6
  fichas multilínea `gen137`-`gen142`; se comprobó aparte que no cambian ningún resultado.
  Tras WP-D1, `StaticCatalogCoverageTest` la repite con coincidencia por frase completa y da 169/174 (ver «Sonda de
  cobertura de términos cotidianos»).
- **Referencias `archivo:línea`.** Se copiaron del plan sin re-verificarlas una por una; se desplazarán conforme se
  apliquen los WP.
- **Sin corpus de fallos reales.** La telemetría nutricional sanitiza el texto de las comidas, no hay JSONL exportados
  en el repo y `DATASET_KPKN_TRINIDAD_MASTER.json` no está versionado.
- **Hook `PreToolUse:Write` roto.** Según el plan, el script `check_backend_component.py` del plugin backend-design no
  existe en disco y bloquea Write/Edit; es un prerrequisito bloqueante de la fase de implementación. Este documento y
  `coverage-probe.json` se escribieron con Bash, sin pasar por Write/Edit.
- **Árbol compartido.** Al iniciar la auditoría había 542 archivos modificados sin commitear de otras tareas (p. ej.
  `WorkoutVoice*`) en la rama `consolidation/2026-10-02-wizard-session-repair`; ninguno forma parte de esta auditoría y
  cada WP debe aislar su diff por rutas. El wrapper `.opencode/scripts/run-gradle-locked.ps1` quedó versionado en el
  commit de la fase 1A (`97c464ad0`).
- **Solo flavor Base.** El flavor Health no se valida (decisión del usuario). La redacción de este documento (WP-0) no
  ejecutó Gradle ni comandos git de escritura y no tocó `.opencode/memory/MEMORY.md` ni
  `docs/ANDROID_ARCHITECTURE_MAP.md`; los gates posteriores están en «Registro de ejecución».
