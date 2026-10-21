# LS1 · Sesión en vivo: piezas de la serie (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3`). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md`, §0, §2 (puntos 4 a 6) y §6: TU especificación;
- el mapa `C:\kw\analysis\maps\workout.md` (cómo registra hoy la tarjeta de la serie: `SetInputCardV2`);
- las piezas KPKN de `ui/design/` (`KpknChip`, `KpknIconButton`, `KpknTextAction`, `KpknType`, `KpknSurface`).

Es un paquete de PIEZAS: crea cuatro piezas nuevas sin estado y su hoja de render. NO toca la sesión en vivo de hoy: las monta LS2.
Las piezas no conocen el `WorkoutViewModel`: reciben textos, números y lambdas.

## Qué haces
1. **`KpknStepperMetric`** (`ui/design/KpknStepperMetric.kt`): una cifra grande con ±.
   - **Firma:**
     ```kotlin
     @Composable
     fun KpknStepperMetric(
         label: String,
         value: String,
         onMinus: () -> Unit,
         onPlus: () -> Unit,
         contentDescription: String,
         modifier: Modifier = Modifier,
         onCommit: ((String) -> Unit)? = null,
         keyboardType: KeyboardType = KeyboardType.Decimal,
         minusEnabled: Boolean = true,
         plusEnabled: Boolean = true,
     )
     ```
   - **Aspecto, de arriba abajo y centrado:**
     - `label` («KG», «REPS» o «TIEMPO») en `KpknType.eyebrow` `textMuted`;
     - el valor en `KpknType.metricXL` (Syne 40) tabular, en `WizardColors.text`;
     - debajo, dos botones redondos de 56 dp (relleno `KpknSurface.inset`, iconos `KpknLineIcons.Minus` y `Plus` de 24 dp) con 16 dp
       entre ellos. Si `KpknIconButton` no admite 56 dp, haz el botón dentro de la pieza y anótalo.
   - **Gestos:**
     - tocar ± llama una vez;
     - mantener pulsado repite: el primer paso a los 400 ms y luego cada 90 ms, con un háptico corto en cada uno;
     - tocar la cifra, si `onCommit` no es nulo, la edita en el sitio con el mismo tamaño y el teclado pedido; «Hecho» o perder el
       foco llama a `onCommit(texto)`.
   - **Accesibilidad:**
     - la cifra se lee con `contentDescription` («80 kilos»);
     - ± se leen «Restar» y «Sumar» con el nombre de la cifra («Sumar kilos»);
     - los botones deshabilitados lo anuncian.
2. **`WorkoutIntensityRow`** (`screens/workout/design/WorkoutIntensityRow.kt`, carpeta nueva): la intensidad de la serie.
   - **Firma:**
     ```kotlin
     enum class WorkoutIntensityScale { RIR, RPE }

     @Composable
     fun WorkoutIntensityRow(
         scale: WorkoutIntensityScale,
         selected: Double?,
         failure: Boolean,
         onSelect: (Double) -> Unit,
         onToggleFailure: () -> Unit,
         modifier: Modifier = Modifier,
     )
     ```
   - una fila de `KpknChip`, con desplazamiento horizontal si no cabe:
     - RIR: «0», «1», «2», «3» y «4+» (`4+` vale 4.0);
     - RPE: «6», «6,5», «7», «7,5», «8», «8,5», «9», «9,5» y «10»;
     - delante, la escala («RIR» o «RPE») en `caption` `textMuted`;
   - el elegido, relleno; al abrir, la fila se desplaza para que el elegido se vea;
   - «Fallo» como chip aparte al final, en `WizardColors.danger` cuando está activo. Con fallo activo, el RIR elegido se apaga (lo
     decide quien llama: la pieza solo pinta `selected`).
3. **`WorkoutLoadSuggestion`** (`screens/workout/design/WorkoutLoadSuggestion.kt`): la carga sugerida.
   - **Firma:** `fun WorkoutLoadSuggestion(suggestion: String?, applied: Boolean, onUse: () -> Unit, modifier: Modifier = Modifier,
     reason: String? = null)`.
   - Con `suggestion` nulo no pinta nada (ni hueco).
   - Sin aplicar: «Sugerida 82,5 kg» en `caption` `textMuted` y `KpknTextAction("Usar")`; `reason` debajo, en `caption` `textFaint`
     («Por tu última sesión»).
   - Aplicada: «Sugerida 82,5 kg · en uso», sin acción.
4. **`WorkoutSetStrip`** (`screens/workout/design/WorkoutSetStrip.kt`): la tira de series.
   - **Firma:**
     ```kotlin
     enum class WorkoutSetDotState { DONE, CURRENT, FUTURE }
     data class WorkoutSetDot(val id: String, val label: String, val state: WorkoutSetDotState, val caption: String?)

     @Composable
     fun WorkoutSetStrip(
         dots: List<WorkoutSetDot>,
         onOpen: (id: String) -> Unit,
         modifier: Modifier = Modifier,
         onAdd: (() -> Unit)? = null,
     )
     ```
   - **Círculos de 44 dp con 10 dp entre ellos:**
     - `DONE`: relleno `WizardColors.done` al 16 % con `KpknLineIcons.Check` en `done`, y la `caption` («80 × 8») debajo en `caption`
       `textMuted`;
     - `CURRENT`: anillo de 2 dp en tinta con la etiqueta en `bodyStrong`;
     - `FUTURE`: anillo de 1,5 dp en `divider` con la etiqueta en `textFaint`;
     - al final, si `onAdd` no es nulo, un círculo con `Plus` en `textMuted` («Añadir serie»).
   - En un `LazyRow`: con más series de las que caben, la actual queda a la vista (`animateScrollToItem`; sin animación con
     `wizardReducedMotion()`).
   - Tocar un círculo llama a `onOpen(id)`.
   - TalkBack: «Serie 1, hecha, 80 kilos por 8», «Serie 2, la actual», «Serie 3, pendiente».

## Pruebas
- **Robolectric** `KpknStepperMetricTest`:
  - ± llaman una vez al tocar;
  - mantener pulsado 1 s repite al menos 5 veces (reloj de la regla de prueba);
  - tocar la cifra la edita y «Hecho» llama a `onCommit`;
  - deshabilitado no llama.
- **Robolectric** `WorkoutSetPiecesTest`:
  - la fila de intensidad llama a `onSelect` con 4.0 en «4+» y a `onToggleFailure`;
  - la sugerencia sin valor no pinta nada; «Usar» llama a `onUse`;
  - la tira llama a `onOpen` con el id y a `onAdd`.
- **Hoja de render** `render/WorkoutSetPiecesRenderSheetTest`:
  - dos `KpknStepperMetric` lado a lado (KG 80 y REPS 8) y uno de tiempo («0:45»), uno editando y uno con «−» deshabilitado;
  - la fila de RIR con 2, la de RPE con 8,5 y «Fallo» activo;
  - la sugerencia sin aplicar, con su motivo, y aplicada;
  - la tira con 2 hechas, la actual y 2 futuras, y otra con 10 series y la actual en la 7;
  - todo junto como se verá en LS2 (eyebrow «SERIE 2 DE 4», las dos cifras, la intensidad, la sugerencia y la tira) sobre negro;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/ls1`.
  - Mira cada PNG con Read y critica con el listón del usuario: esta es LA pantalla de la app. Tiene que leerse a un metro con el
    pulso alto, mejor que el foco de MacroFactor Workouts y que las tablas de Hevy. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "*KpknStepperMetric*" --tests "*WorkoutSetPieces*" > /c/kw/logs/screens-ls1-<n>.log 2>&1`

## Commit
- `feat(workout): piezas de la serie en vivo (cifra con ±, intensidad, sugerencia y tira de series) (LS1)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Los archivos que ya existen en `screens/workout/` (LS0 limpia ahí en paralelo; LS2 monta las piezas).
- `screens/sessioneditor/` y las piezas existentes de `ui/design/` (si te falta algo de una, anótalo).
- `MainActivity.kt`.
- Push.

## Informe
- Firmas finales de las piezas.
- Pruebas: número y resultado.
- PNG y crítica.
