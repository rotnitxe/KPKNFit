# SE6b · Catálogo de ejercicios: la ficha, crear ejercicio, superseries y el asistente de variantes (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con SE6a integrado). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md`, §0, §4, §8 y §9: TU especificación;
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §1.1 (catálogo), §1.6, §2.1 («Ficha de información», «Asistente de selección»,
  «Catálogo V2»), §6 y §7. Las líneas son de ANTES de SE0: búscalas por nombre;
- lo que dejó SE6a (la lista del catálogo, `CatalogExerciseRow` y la isla de selección).

SE6a revistió la LISTA del catálogo. Este paquete reviste lo que se abre desde ella. La lógica NO cambia: mismos datos, mismas
llamadas, mismo guardado del ejercicio propio y misma configuración de superseries.

## Qué haces
1. **La ficha del ejercicio** (`ExerciseCatalogInfoDialog` y lo que pinta: `CatalogExerciseImageCarousel`, `CatalogDescription`,
   `CatalogInvolvementAccordions`, `JointInvolvementSection`, `MuscleInvolvementSection`), en una `KpknSheet` casi a pantalla
   completa:
   - arriba, las imágenes del ejercicio a todo el ancho con radio 20 y `PagerDots` si hay varias;
   - el nombre en `KpknType.title` y «Pecho · Barra · Empuje horizontal» en `caption`;
   - la descripción en `body`;
   - «Músculos · aporte por 1 serie» y «Articulaciones» como módulos plegables (`KpknModule` con su cabecera tocable y
     `ChevronDown`), con las barras de aporte en `SymbolPalette.musculo` y sus cifras tabulares;
   - al pie, `KpknPrimaryPill("Añadir")` o «Quitar» si ya está elegido, que hace lo mismo que el círculo de la fila.
   - Las pruebas instrumentadas (`ExercisePickerCardsUiTest`) buscan «Información del ejercicio», «Músculos · aporte por 1 serie»,
     «Cerrado», «Mostrar/Ocultar información de…» y «Cerrar definición» (que hoy NO existe: la prueba ya falla). Conserva los
     textos que existen y añade «Cerrar definición» como descripción del botón de cerrar de la ficha, para que la prueba vuelva a
     tener sentido.
2. **Crear ejercicio** (`SmartExerciseEditorPage`, `SmartExerciseForm`, `SmartCreateSuggestion`, `PatternBanner`,
   `ManualMuscleEditor`, `CustomExerciseCard`, `OptionChipRow`):
   - `KpknTextField` para el nombre;
   - `KpknSegmentedControl` o `KpknChip` para el patrón y el material;
   - el editor de músculos con `KpknChip` y la regla de aporte de la ficha;
   - el aviso del patrón detectado en un `KpknModule` compacto;
   - `KpknPrimaryPill("Guardar ejercicio")`.
   - Fuera `FilterChip`, `Button`, `IconButton`, `OutlinedTextField`, `Icons.*`, `Color(0x…)` y tamaños sueltos.
3. **Configurar la superserie** (`CatalogSupersetConfiguratorDialog`): pasa a `KpknSheet` con:
   - las rondas en `KpknNumberCell` (SE1);
   - el descanso entre rondas con el selector de hoy;
   - el orden de los ejercicios;
   - `KpknPrimaryPill("Crear superserie")`.
4. **Asistente de variantes y aspectos técnicos** (`CatalogSelectionWizard.kt`):
   - hojas con pasos numerados: el eyebrow «Paso 1 de 3» y el título en `KpknType.title`;
   - opciones en `KpknOptionRow`;
   - «Siguiente» y «Atrás» como píldoras.
   - Fuera `Card`.
5. **Lo que queda sin uso** (incluido el `SupersetCreatorSheet` muerto, si sigue sin uso): bórralo con Grep en todo `app/src`.
   `ExercisePickerSheet` se queda (lo usan el editor y la sesión en vivo).
6. **Textos:** tipo frase, sin MAYÚSCULAS escritas a mano, «…» de un carácter. Antes de cambiar un texto, Grep en `src/androidTest`.

## Pruebas
- **Siguen en verde:**
  - las 33 clases de `src/test/.../screens/sessioneditor/`, sobre todo `ExercisePickerCatalogLogicTest` (8);
  - las de SE6a (`*CatalogExerciseRow*`, `*CatalogList*`);
  - las de `screens/workout/` que usan el catálogo (`*Workout*`);
  - la compilación de `androidTest`.
- **Robolectric** `CatalogDetailContentTest`, con los cuerpos montados sueltos:
  - la ficha muestra nombre, músculo y material, y «Añadir» llama a seleccionar;
  - los módulos de músculos y articulaciones se pliegan y despliegan;
  - crear ejercicio con nombre vacío no guarda;
  - la superserie llama con sus rondas;
  - el asistente de variantes avanza y vuelve.
- **Hoja de render** `render/CatalogDetailRenderSheetTest`, con los cuerpos montados con datos de ejemplo:
  - la ficha con imagen y sin ella;
  - los músculos desplegados;
  - crear ejercicio;
  - configurar una superserie;
  - un paso del asistente de variantes;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se6b`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se6b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" --tests "*Catalog*" > /c/kw/logs/screens-se6b-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(sessioneditor): ficha del ejercicio, crear ejercicio, superseries y asistente de variantes en piezas KPKN (SE6b)`.
- Si borras piezas viejas, aparte: `refactor(sessioneditor): quita piezas viejas del catálogo (SE6b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lista del catálogo (SE6a, ya integrada).
- La lógica del catálogo, del guardado del ejercicio propio y de las superseries.
- Las firmas de `ExercisePickerV2Catalog`, `ExercisePickerSheet` y `ExerciseCatalogScreen`.
- `MainActivity.kt`, `navigation/` y cualquier archivo de `screens/workout/`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Pruebas: número y resultado.
- PNG y crítica.
