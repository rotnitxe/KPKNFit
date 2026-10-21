# N3a · Nutrición: el registro de comida, parte 1 (hoja, cabecera, Describir y Buscar, análisis) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con N0, I9, D1 e I11a integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/NUTRITION_V1.md`, §3: TU especificación;
- el mapa `C:\kw\analysis\maps\nutrition.md`, §1 (orden del registro) y §2 a §7.

`screens/nutrition/components/FoodLoggerDrawer.kt` tiene unas 2.750 líneas. Este paquete rediseña su PARTE DE ARRIBA:
- la hoja;
- la cabecera (líneas aprox. 1430-1490);
- las pestañas (1490);
- la descripción y el buscador (1501-1599);
- el panel de análisis y sus avisos (1564-1583 y los ayudantes 1910-2031);
- la lista de resultados de búsqueda (1624).

Las tarjetas de alimento, el total, el error, el botón principal, el conmutador y los diálogos son de N3b, que irá DESPUÉS.

La lógica (análisis, búsqueda, borrador, guardado) NO cambia: cambia la presentación.

## Qué haces
1. **La hoja:** sigue siendo `KpknSheet` (vidrio v2 de hoja), casi a pantalla completa, con su asa.
2. **Cabecera** (`screens/nutrition/design/logger/LoggerHeader.kt`):
   - el título «Registrar comida», o «Editar comida» al editar, en `KpknType.title`;
   - `KpknIconButton(KpknLineIcons.Close, "Cerrar")` a la derecha, con la misma acción de cierre y descarte que hoy.
   - **El tipo de comida:** `KpknSegmentedControl(Compact)` con las cuatro de `MealSlots` (Desayuno, Almuerzo, Merienda y Cena), o
     una fila de 4 chips con su icono de `mealIcon` si no caben al 130 %.
   - **La fecha y la hora:** un `KpknChip` con «Hoy · 13:40» (o la fecha) que abre el selector de fecha de hoy. Si ya existe
     `ui/design/KpknMonthCalendar` (lo crea CU3 en paralelo), úsalo dentro de una `KpknSheet`; si no, deja el selector de hoy y anótalo.
3. **Pestañas «Describir · Buscar»:** `KpknSegmentedControl(Regular)`.
4. **Describir:**
   - `KpknTextField` multilínea (3 a 6 líneas) con la etiqueta «¿Qué comiste?» y el marcador «Dos huevos con tostadas y un café»;
   - debajo, en `caption` `textMuted`, una línea de ayuda: «Escribe como hablas: cantidades, marcas y cómo lo cocinaste»;
   - la acción de interpretar sigue siendo la de N3b (el botón principal de abajo); no la dupliques aquí.
5. **Buscar:**
   - `KpknTextField` de una línea con `leadingIcon` de búsqueda. Añade `Search` al final de `KpknLineIcons` con la regla del set: una
     lupa, círculo de r 6,25 en (10,75; 10,75) y mango hasta (20; 20).
   - Los resultados (1624) en filas KPKN de 56 dp:
     - nombre en `bodyStrong`;
     - marca o fuente en `caption` `textMuted`;
     - kcal por porción, tabular, a la derecha;
     - `KpknIconButton(Plus, "Añadir")`, que hace lo que hace hoy al elegir un resultado;
     - filas separadas por `divider`.
   - **Sin resultados** (hoy no muestra nada, 1173-1199): «Sin resultados para "<texto>"» en `body` `textMuted` y la acción de texto
     «Describirlo en lugar de buscar», que cambia a la pestaña Describir con el texto puesto.
6. **El análisis:**
   - el panel de los tres puntos que respiran y los mensajes que rotan (1962-2031) pasa a piezas KPKN, sin `Card`: tres puntos de 6 dp
     en `energia` y el mensaje en `body` `textMuted`, en el mismo ritmo de 2,6 s;
   - con `wizardReducedMotion()`, un texto fijo «Interpretando…» y sin animación;
   - **los avisos del análisis**, sin el detalle técnico en monoespaciado (2107-2113): un `KpknModule(accent = WizardColors.danger)`
     con un mensaje claro («No pudimos interpretar la comida») y la acción «Reintentar». El detalle técnico se oculta, o va tras
     «Ver detalle» si alguna prueba lo exige.
7. **Colores:** fuera los literales y el verde `0xFF2E7D32` de esta parte. El botón principal es de N3b: no lo toques.

## Pruebas
- **Siguen en verde:** `FoodLoggerViewModelTest`, `FoodLoggerViewModelEditTest`, `FoodClarificationPromptTest`,
  `SystematicModeContractTest`, `NutritionViewModelOpenRequestTest` y la compilación de `androidTest` (`FoodLoggerDrawerDraftUiTest`
  busca «Resumen de la comida», «Cena» y «Descartar»: consérvalos).
- **Robolectric** `LoggerHeaderTest`: elegir comida y cambiar de pestaña llaman a sus lambdas; sin resultados se ve el mensaje y la
  acción cambia de pestaña con el texto.
- **Hoja de render** `render/FoodLoggerTopRenderSheetTest`:
  - Describir vacío;
  - Describir con texto;
  - analizando (un fotograma);
  - error del análisis;
  - Buscar con 5 resultados;
  - Buscar sin resultados;
  - editar una comida;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/n3a`.
  - Mira cada PNG con Read y critica con el listón del usuario (registrar debe ser más rápido y bonito que en MacroFactor).
    Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/n3a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "*FoodLogger*" --tests "*FoodClarification*" --tests "*SystematicMode*" --tests "*NutritionViewModelOpenRequest*" --tests "*LoggerHeader*" --tests "*KpknLineIcons*" > /c/kw/logs/screens-n3a-<n>.log 2>&1`

## Commit
- Por rutas: `feat(nutrition): registro de comida, parte 1: hoja, cabecera con comida y fecha, Describir y Buscar KPKN y análisis sin tecnicismos (N3a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Las tarjetas de alimento, el total, el error de guardado, el botón principal, el conmutador y los diálogos (N3b).
- La lógica de análisis, búsqueda y guardado.
- `NutritionScreen.kt` (N1 trabaja ahí).
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas.
- Pruebas: número y resultado.
- PNG y crítica.
