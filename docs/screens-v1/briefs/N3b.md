# N3b · Nutrición: el registro de comida, parte 2 (alimentos, total, guardar, error y diálogos) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con N0, N3a, I9, D1 e I11a integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/NUTRITION_V1.md`, §3: TU especificación;
- el mapa `C:\kw\analysis\maps\nutrition.md` (§1, orden del registro, y §2 a §7);
- lo que dejó N3a en `screens/nutrition/design/logger/` (hoja, cabecera, Describir y Buscar, análisis): sigue su lenguaje.

`screens/nutrition/components/FoodLoggerDrawer.kt` (unas 2.750 líneas) ya tiene su PARTE DE ARRIBA rediseñada por N3a. Este paquete
rediseña la PARTE DE ABAJO:
- las tarjetas de alimento (`FoodItemCard` o similar, aprox. 2275-2663);
- el total (aprox. 1721);
- el aviso de error de guardado (aprox. 1758-1789);
- el botón principal (aprox. 1792) y el botón redondo que alterna Describir y Buscar (aprox. 1864);
- los diálogos: «Configuración» de medidas (aprox. 1250), olvidar lo aprendido, privacidad (aprox. 1371) y descartar (aprox. 1397, un
  `AlertDialog` de Material).

La lógica (análisis, búsqueda, borrador, guardado, aclaraciones, aprendizaje) NO cambia: cambia la presentación.

## Qué haces
1. **Una fila por alimento interpretado** (`design/logger/LoggerFoodItem.kt`, sustituye a la tarjeta; sin `Card` ni `FilterChip`):
   - arriba: el nombre en `bodyStrong` (hasta 2 líneas) y, a la derecha, las kcal tabulares en `metricM` con «kcal» en `unit`; si el
     alimento es una estimación con rango, «≈» delante;
   - la porción: `KpknSegmentedControl(Compact)` con las porciones que ofrece hoy la tarjeta (si son más de 4 o no caben al 130 %, una
     fila de `KpknChip` que se desliza);
   - los gramos: `GramsEditor` (`components/GramsEditor.kt`) con piel KPKN (sin cambiar su API ni su lógica: colores y tipografía);
   - los macros en una línea tabular en `caption`: «P 32 g · H 4 g · G 12 g», con un punto de 6 dp de su color de `MacroColors`
     delante de cada uno. Fuera los colores propios del registro (`B3261E`, `6750A4`, `006A6A`);
   - el ajuste manual de macros que hoy tenga la tarjeta (los ±) se conserva, con `KpknIconButton(Minus)` y `KpknIconButton(Plus)`;
   - **la aclaración pendiente** (`foodClarificationPrompt`): la pregunta en `body` y sus 2-3 respuestas en `KpknChip`; al elegir, lo de
     hoy;
   - `KpknTextAction("Corregir")`, que abre la búsqueda para cambiar el alimento (lo de hoy);
   - quitar el alimento: `KpknIconButton(Close, "Quitar <alimento>")`;
   - las filas van separadas por `divider` dentro de un `KpknModule` con eyebrow «LO QUE COMISTE».
   - Conserva «Resumen de la comida» (lo busca `FoodLoggerDrawerDraftUiTest`).
2. **Barra inferior fija de la hoja** (`design/logger/LoggerSaveBar.kt`, sustituye al total y al botón principal):
   - a la izquierda, el total: kcal en `metricL` contando (`animateIntAsState`; sin animación con `wizardReducedMotion()`) y «P 64 ·
     H 120 · G 40 g» en `caption`;
   - a la derecha, `KpknPrimaryPill` con la etiqueta de `FoodLoggerPrimaryAction.label` (ya en tipo frase); cuando la acción es
     guardar, «Guardar en <comida>» con el nombre de `MealSlots` («Guardar en Almuerzo»). Si alguna prueba busca «Guardar» exacto,
     consérvalo donde la prueba lo espera y anótalo;
   - mientras analiza o guarda, la píldora dice «Interpretando…» o «Guardando…» (con «…» de un carácter), sin spinner de Material;
   - **el éxito:** un check animado de 600 ms (trazo que se dibuja, en `WizardColors.done`) antes de cerrar; con movimiento reducido,
     el check fijo 300 ms;
   - fuera el botón redondo que alterna Describir y Buscar: los segmentos de N3a ya lo hacen.
3. **Error de guardado:** `KpknModule(accent = WizardColors.danger)` con un mensaje en lenguaje llano («No se pudo guardar la
   comida.») y `KpknTextAction("Reintentar")`. Nada de `e.message` a la vista.
4. **Diálogos** (todos con `KpknAlertDialog` o `KpknGlassDialog`; sin `AlertDialog`, `TextButton`, `Button`, `Switch` ni `Slider` de
   Material):
   - **descartar:** «¿Descartar esta comida?» o «¿Descartar los cambios?» con «Descartar» (destructiva, en `danger`) y «Seguir
     editando». Conserva los textos «Descartar» y «Cena» que busca la prueba instrumentada;
   - **«Configuración»** (las medidas): título «Medidas de casa», una fila por medida con su nombre, su valor en gramos o ml en
     `bodyStrong` tabular y `KpknIconButton(Minus)`/`KpknIconButton(Plus)` (o el deslizador propio del wizard si ya existe uno
     adecuado), «Restablecer» como `KpknTextAction`;
   - **olvidar lo aprendido:** destructivo, con su texto de hoy;
   - **privacidad:** título «Registro privado, sin conexión» y el texto en español neutro, acortado a ≤ 300 caracteres, con la misma
     información (todo en el teléfono, sin enviar comidas, puede equivocarse, se corrige con «Corregir» o a mano).
5. **Textos:** «Hidratos» (nunca «Carbos» ni «Carbohidratos» en esta hoja), «Merienda» (nunca «Snack»), «…» de un carácter, tipo
   frase. «De maíz / huevo / papas» fijo (aprox. 2412): si es un ejemplo de marcador, pasa a «Por ejemplo: de maíz».
6. **Colores:** fuera los literales y el verde `0xFF2E7D32` de esta parte; solo tokens.

## Pruebas
- **Siguen en verde:** `FoodLoggerViewModelTest`, `FoodLoggerViewModelEditTest`, `FoodClarificationPromptTest`,
  `SystematicModeContractTest` (afirma etiquetas de `FoodLoggerPrimaryAction`: no cambies la función, solo cómo se pinta),
  `NutritionViewModelOpenRequestTest`, lo que dejó N3a (`LoggerHeaderTest`) y la compilación de `androidTest`
  (`FoodLoggerDrawerDraftUiTest` busca «Resumen de la comida», «Cena» y «Descartar»).
- **Robolectric** `LoggerFoodItemTest`: elegir una porción llama a su lambda; una respuesta de aclaración llama a la suya; «Corregir»
  llama a la búsqueda; «Quitar» quita. `LoggerSaveBarTest`: la etiqueta «Guardar en Almuerzo»; apagada mientras guarda.
- **Hoja de render** `render/FoodLoggerBottomRenderSheetTest`:
  - tres alimentos interpretados (uno con aclaración pendiente y otro estimado con rango);
  - la barra con el total, guardando y el check de éxito (un fotograma);
  - el error de guardado;
  - los cuatro diálogos;
  - la hoja entera de arriba abajo con lo de N3a (dos o tres capturas);
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/n3b`.
  - Mira cada PNG con Read y critica con el listón del usuario (registrar debe ser más rápido y más bonito que en MacroFactor).
    Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/n3b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "*FoodLogger*" --tests "*FoodClarification*" --tests "*SystematicMode*" --tests "*NutritionViewModelOpenRequest*" --tests "*LoggerHeader*" --tests "*LoggerFoodItem*" --tests "*LoggerSaveBar*" > /c/kw/logs/screens-n3b-<n>.log 2>&1`

## Commit
- Por rutas: `feat(nutrition): registro de comida, parte 2: alimentos en filas KPKN, barra de total y guardar con check, error llano y diálogos KPKN (N3b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La parte de arriba del registro (N3a), salvo encajar la barra inferior.
- La lógica de análisis, búsqueda, aclaraciones, aprendizaje y guardado.
- `NutritionScreen.kt` (N2 trabaja ahí) y `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y código que queda sin uso en `FoodLoggerDrawer.kt`.
- Pruebas: número y resultado.
- PNG y crítica.
