# P2-E · Etiqueta nutricional por foto de la galería (experimental)

**Prioridad P2 · esfuerzo M.** Informe §4.4, ítem D10 del informe; decisión D17 de la ola. Depende de **N3** (formulario de alimento propio `screens/nutrition/customfood/*` y `saveCustomFood`) y se apoya en el OCR que KPKN ya empaqueta. **Experimental, sin cámara y sin permisos nuevos:** la persona elige una foto de su galería, la app lee la tabla nutricional en el dispositivo y **rellena el formulario de alimento propio para que la persona lo revise**. Nunca guarda sola, nunca muestra una cifra no leída como si lo fuera y nunca sale nada del teléfono.

## Lo que ya existe (léelo antes de editar)
- `data/importer/ocr/MlKitTextRecognizer.kt` (`TextRecognizerPort`): ML Kit `text-recognition` con el modelo latino **dentro del APK**; devuelve `OcrWord(text, left, top, right, bottom)`.
- `domain/training/importer/OcrLayout.kt`: `OcrLayout.reconstruct(pages: List<OcrPage>)` agrupa palabras en **filas con celdas** por su posición. Reutilízalo para la tabla de una etiqueta («Grasas | 10 g | 14 g»); no escribas otro agrupador salvo que demuestres que no sirve.
- El formulario de alimento propio de N3 (`screens/nutrition/customfood/*`): nombre, marca, código, porción, base «por porción» / «por 100 g», kcal, proteína, carbohidratos, grasas, fibra / azúcar / sodio opcionales; validación pura con aviso de kcal incoherentes.

## Cambio exacto
### 1. Dominio puro `domain/nutrition/label/NutritionLabelParser.kt` (sin `android.*`)
`parse(rows: List<List<String>>): LabelParse` con:
- `basis`: `PER_100G`, `PER_100ML`, `PER_SERVING` o `UNKNOWN` (por cabeceras: «por 100 g», «por 100 ml», «por ración / porción / unidad», «per 100 g», «per serving»); `servingSize` (gramos o ml) si la etiqueta lo dice («Ración: 30 g», «Serving size 1 cup (240 ml)»).
- Campos con su **confianza** (`READ` leído con seguridad · `DERIVED` calculado, p. ej. sodio desde sal · `UNSURE` leído con ruido): energía en kcal, proteína, carbohidratos, de los cuales azúcares, grasas, de las cuales saturadas, fibra, sodio (mg).
- Vocabulario **español de la UE** (Reglamento 1169/2011): «Valor energético / Energía», «kJ / kcal», «Grasas», «de las cuales saturadas», «Hidratos de carbono / Carbohidratos», «de los cuales azúcares», «Fibra alimentaria», «Proteínas», «Sal», «Sodio»; e **inglés de EE. UU.** («Calories», «Total Fat», «Saturated Fat», «Total Carbohydrate», «Total Sugars», «Dietary Fiber», «Protein», «Sodium»). Sin tildes y con mayúsculas variables.
- Reglas: coma decimal; «<0,5 g», «trazas» y «0 g» → valor 0 con aviso para «<» y «trazas»; dos columnas «por 100 g | por ración» → se elige según `basis` preferida (por defecto la de 100 g, y se dice cuál); energía solo en kJ → kcal = kJ / 4,184 con aviso; **sal → sodio:** sodio (mg) = sal (g) × 400; confusiones típicas de OCR en la unidad («5 9» por «5 g», «1O,5» por «10,5») corregidas solo en posición de cifra o de unidad y marcando `UNSURE`; ruido que no es una etiqueta → `LabelParse` vacío con el motivo (nunca valores inventados).
- **Coherencia** (avisos, no bloqueos): kcal declaradas frente a 4·P + 4·C + 9·G (aviso si difieren más de un 25 %), saturadas ≤ grasas, azúcares ≤ carbohidratos, valores negativos o absurdos (> 100 g por 100 g) → descartados con motivo.
- Tests de tabla con **al menos 12 etiquetas escritas a mano** (español de España y de Latinoamérica, inglés de EE. UU., una de dos columnas, una en kJ solamente, una con sal en vez de sodio, una con «trazas», una con ruido de OCR, una vacía, una que no es etiqueta) y sus valores esperados escritos a mano.

### 2. Datos (`data/…/label/*`)
Un caso de uso `ReadNutritionLabel(uri)` que decodifica la imagen **reducida** (lado largo ≤ 2.048 px, rotación EXIF aplicada), llama a `MlKitTextRecognizer`, arma `OcrPage`, usa `OcrLayout.reconstruct` y `NutritionLabelParser`. En `Dispatchers.IO`; **no guarda la imagen** (sin copia en disco ni en caché; el `Bitmap` se libera al terminar); errores (imagen ilegible, sin texto) como resultados tipados, no excepciones sueltas.

### 3. UI
- En el formulario de alimento propio de N3: botón **«Leer etiqueta»** con una insignia «Experimental» (arriba o junto a «Código de barras»; refina el formulario, no lo rediseñes). Abre el **selector de fotos del sistema** (`ActivityResultContracts.PickVisualMedia`, sin permiso de almacenamiento ni de cámara).
- Resultado: rellena los campos que se leyeron y marca cada uno con una señal doble (icono + texto «leído de la foto — revisa»; los `UNSURE` con «dudoso»); los no leídos quedan vacíos. Muestra la base elegida («por 100 g» / «por ración de 30 g») y deja cambiarla; muestra los avisos de coherencia. Un botón «Deshacer lectura» devuelve el formulario a como estaba. **No se guarda nada hasta que la persona pulsa Guardar** (el guardado es el de N3, `saveCustomFood`).
- Estados: leyendo (con cancelación), sin texto («No he encontrado una tabla nutricional en esta foto»), error de imagen, lectura parcial. Español, accesibilidad real, objetivos ≥ 48 dp, 360 dp al 130 %, estilo KPKN v2.
- Hoja de render (PNG a 360 dp, 100 % y 130 %): formulario antes y después de leer, con avisos y con la lectura parcial.

### 4. Autocomprobación en el teléfono SIN fotos (solo depuración)
`app/src/debug/…/LabelOcrSelfTestActivity.kt`: dibuja en memoria con `Canvas` cinco etiquetas sintéticas (tabla de cuatro filas con la tipografía del sistema a tamaños distintos: UE por 100 g, UE por 100 g y por ración en dos columnas, EE. UU., con sal, con «trazas»), las pasa por la MISMA ruta real (`MlKitTextRecognizer` → `OcrLayout` → `NutritionLabelParser`) y registra una línea por etiqueta y `SELFTEST OK n/n` o `SELFTEST FAIL …` en logcat con la etiqueta `MfpLabelSelfTest`, y lo muestra en pantalla. El orquestador lo lanza en el Flip. Lo que NO prueba (fotos reales con brillo, curvatura o ángulo) se anota como «no verificado en dispositivo».

## Archivos permitidos
- Nuevos: `domain/nutrition/label/*`, `data/…/label/*` (o `data/importer/ocr/` si encaja mejor; no cambies `MlKitTextRecognizer` ni `OcrLayout`, solo úsalos), tests y hoja de render; `app/src/debug/…/LabelOcrSelfTestActivity.kt` + manifiesto.
- `screens/nutrition/customfood/*` (botón, estados y marcas de campo), `NutritionModels.kt` solo si hace falta un tipo auxiliar sin cambiar `FoodItem`.
- `docs/macrofactor-parity/CAMBIOS_VISIBLES.md`.

## Pruebas obligatorias
- Las 12+ etiquetas de tabla del parser y las reglas de arriba (sodio desde sal, kJ → kcal, «<», «trazas», dos columnas, coherencia).
- Caso de uso con un reconocedor falso que devuelve palabras con cajas (columnas a distintas alturas, una etiqueta girada ligeramente): produce las filas y los valores esperados; imagen ilegible y sin texto → resultados tipados; no se escribe ningún archivo (verifica con un directorio temporal vigilado).
- ViewModel del formulario: leer rellena solo lo leído y lo marca; «Deshacer lectura» restaura; no se guarda sin pulsar Guardar; cambiar la base recalcula la presentación sin perder lo leído.
- Regresión: `screens.nutrition.customfood.*` y `domain.nutrition.*` sin cambiar expectativas ajenas.

## Comandos
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.*" --tests "com.example.kpkn.data.importer.ocr.*" --tests "com.example.kpkn.screens.nutrition.*"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/mfp/P2E` con `--tests "com.example.kpkn.render.LabelReadRenderSheetTest"`.

## Fuera de alcance
Cámara en vivo, enviar la imagen a ningún servicio, IA generativa, leer ingredientes o alérgenos, lista de micronutrientes de la etiqueta (solo los ocho campos de arriba), cambiar el OCR de los programas.
