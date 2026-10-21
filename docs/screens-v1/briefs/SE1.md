# SE1 · Editor de sesiones: celda numérica y tabla de series (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3`). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md`, §0, §3 y §9: TU especificación;
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §1.4 y §7 (las 9 familias de campos que esta pieza viene a sustituir);
- el modelo de serie: `data/models/Session.kt` (`ExerciseSet`, `RepRange.format()`, `IntensityMode`).

Es un paquete de PIEZAS: crea dos piezas nuevas sin estado en `ui/design/` y su hoja de render. NO toca el editor ni la sesión en
vivo: las monta SE3 (y la sesión en vivo, en su ola). Por eso las piezas no conocen `ExerciseSet`: reciben textos y lambdas.

## Qué haces
1. **`KpknNumberCell`** (`ui/design/KpknNumberCell.kt`): la celda de una cifra editable.
   - **Firma:**
     ```kotlin
     sealed interface KpknCellInput {
         /** Se escribe con teclado; [onCommit] recibe el texto al pulsar «Hecho» o al perder el foco. */
         data class Text(val keyboardType: KeyboardType = KeyboardType.Decimal, val onCommit: (String) -> Unit) : KpknCellInput
         /** Abre un selector propio (descanso, tiempo): la celda solo avisa. */
         data class Picker(val onOpen: () -> Unit) : KpknCellInput
         data object ReadOnly : KpknCellInput
     }

     @Composable
     fun KpknNumberCell(
         value: String,
         input: KpknCellInput,
         contentDescription: String,
         modifier: Modifier = Modifier,
         placeholder: String = "–",
         onStep: ((Int) -> Unit)? = null,
         error: Boolean = false,
     )
     ```
   - **Aspecto:**
     - relleno `KpknSurface.inset`, radio 12 dp, alto 40 dp dentro de un objetivo táctil de 48 dp;
     - cifra en `KpknType.bodyStrong` con `fontFeatureSettings = "'tnum' 1"`, centrada, en `WizardColors.text`;
     - sin valor: `placeholder` en `textFaint`;
     - editando: filete de 1,5 dp en tinta;
     - con `error`: filete de 1,5 dp en `WizardColors.danger`.
   - **Edición (`Text`):**
     - al tocar pasa a `BasicTextField` en el sitio, con el texto seleccionado entero, el teclado del tipo pedido y la acción «Hecho»;
     - «Hecho» o perder el foco llama a `onCommit(texto)`. La pieza no valida: lo hace quien la llama, que puede pasar `error`.
   - **Paso (`onStep`, si no es nulo):**
     - un arrastre horizontal llama a `onStep(+1)` o `onStep(-1)` cada 24 dp, con un háptico corto en cada paso;
     - con `wizardReducedMotion()`, igual (el gesto no anima nada);
     - para TalkBack, dos `CustomAccessibilityAction`: «Aumentar» y «Disminuir».
   - **Accesibilidad:** `Picker` lleva `Role.Button`; `Text` se anuncia como campo editable con su `contentDescription` («Serie 2,
     kilos, 80»).
2. **`KpknSetTable`** (`ui/design/KpknSetTable.kt`): la tabla de series, sin estado.
   - **Firma:**
     ```kotlin
     data class KpknSetColumn(val key: String, val label: String, val weight: Float = 1f)
     enum class KpknSetKind { WORK, APPROACH, DROP }
     data class KpknSetCell(
         val value: String,
         val input: KpknCellInput,
         val contentDescription: String,
         val onStep: ((Int) -> Unit)? = null,
         val error: Boolean = false,
     )
     data class KpknSetRow(val id: String, val label: String, val kind: KpknSetKind, val cells: Map<String, KpknSetCell>)

     @Composable
     fun KpknSetTable(
         columns: List<KpknSetColumn>,
         rows: List<KpknSetRow>,
         modifier: Modifier = Modifier,
         onAddSet: (() -> Unit)? = null,
         onRowMenu: ((rowId: String) -> Unit)? = null,
         addLabel: String = "Añadir serie",
     )
     ```
   - **Cabecera:** «Serie» y la etiqueta de cada columna en `KpknType.eyebrow` `textFaint`, centradas sobre sus celdas.
   - **Filas** (48 dp, 8 dp entre filas, sin separadores):
     - la primera columna (40 dp fija) es el número de serie en `bodyStrong` tabular;
     - `APPROACH`: «A» en `textMuted`;
     - `DROP`: «D» en `SymbolPalette.musculo`;
     - la pulsación larga en esa columna llama a `onRowMenu(id)` (el menú lo pinta quien llama), con su acción de TalkBack «Opciones
       de la serie N»;
     - el resto, una `KpknNumberCell` por columna con su `weight`, 8 dp entre celdas.
   - **Pie:** `KpknTextAction(addLabel)` con `KpknLineIcons.Plus`, si `onAddSet` no es nulo.
   - Sin `LazyColumn` dentro: la tabla va dentro de la lista del editor y no pasa de unas 12 filas.
3. **Columnas de cada modo** (`ui/design/KpknSetColumns.kt`): un `object KpknSetColumns` con las listas que usarán el editor y la
   sesión en vivo. Las etiquetas son exactas:
   - `rir`: «kg», «Reps» y «RIR», más «Descanso»;
   - `rpe`: «kg», «Reps» y «RPE», más «Descanso»;
   - `percent`: «% 1RM», «Reps» y «Descanso»;
   - `time`: «kg», «Tiempo» y «Descanso»;
   - `distance`: «Distancia», «Tiempo» y «Descanso».

   «Reps» cabe en la cabecera de una columna estrecha y es el término de los gimnasios. En TalkBack se lee «repeticiones».
4. **Formato de cifras:** busca con Grep los formateadores que ya existen (descanso «2:30», carga con coma decimal «82,5» y rangos
   con raya «6–8», que hace `RepRange.format()`).
   - Reutiliza lo que haya.
   - Si falta alguno, añádelo en `ui/design/KpknSetFormat.kt` con su prueba unitaria. Usa `Locale.forLanguageTag("es")` y nunca
     `Locale.getDefault()`.

## Pruebas
- **Robolectric** `KpknNumberCellTest`:
  - tocar una celda `Text` entra en edición y «Hecho» llama a `onCommit` con el texto;
  - una celda `Picker` llama a `onOpen`;
  - un arrastre de 60 dp a la derecha llama dos veces a `onStep(+1)`;
  - las acciones de TalkBack «Aumentar» y «Disminuir» existen y llaman a `onStep`;
  - con `error` se pinta (comprueba el estado por semántica o por una etiqueta de prueba).
- **Robolectric** `KpknSetTableTest`:
  - pinta la cabecera y las etiquetas «1», «A» y «D»;
  - la pulsación larga en el número llama a `onRowMenu` con su id;
  - «Añadir serie» llama a `onAddSet`, y sin `onAddSet` no aparece.
- **Hoja de render** `render/KpknSetTableRenderSheetTest`:
  - modo RIR con «A», «1», «2», «3» y «D», con rangos («6–8») y descansos («2:30»);
  - modo RPE, modo % y modo tiempo;
  - una tabla vacía (solo «Añadir serie»);
  - una celda editando y una con error;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se1`.
  - Mira cada PNG con Read y critica con el listón del usuario: la tabla de series de Hevy es la referencia de claridad. La nuestra
    tiene que leerse igual de rápido y verse más cuidada (cifras alineadas, ritmo exacto, nada cortado al 130 %). Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "*KpknNumberCell*" --tests "*KpknSetTable*" --tests "*KpknSetFormat*" > /c/kw/logs/screens-se1-<n>.log 2>&1`

## Commit
- `feat(ui): celda numérica y tabla de series KPKN (SE1)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- `screens/sessioneditor/` y `screens/workout/` (las piezas se montan en SE3 y en la ola LS).
- Cambiar piezas existentes de `ui/design/` (si te falta algo de una, anótalo en el informe).
- `MainActivity.kt`.
- Push.

## Informe
- Firmas finales de las piezas.
- Formateadores reutilizados o nuevos.
- Pruebas: número y resultado.
- PNG y crítica.
