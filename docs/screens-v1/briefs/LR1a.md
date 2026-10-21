# LR1a · Sesión en vivo ORIGINAL: refinado del aspecto de la serie y del descanso (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LR0: la sesión de la PR11 restaurada). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md` (superficies, tipografía, botones): es la «estética nueva».

## El encargo, con las palabras del usuario
Vio el rediseño de la sesión en vivo y lo rechazó. Pidió: «Devuélveme la original. Y a esa misma original le refinas el diseño para
que sea más ad hoc a esta estética nueva». LR0 ya devolvió la original. Este paquete REFINA su aspecto en dos pantallas: la serie y
el descanso. Es una propuesta: el usuario la verá en su teléfono antes de que se extienda al resto.

**La regla de oro:** la persona debe reconocer al instante SU pantalla de siempre. Cambia la piel, no el esqueleto:
- misma disposición;
- mismos elementos en el mismo orden y sitio;
- mismos controles y gestos;
- mismos textos (salvo las erratas de abajo);
- misma conducta.

Si dudas entre «más bonito» y «más parecido al original», elige parecido.

Para ver el original:
- código: `components/SetExecutionCard.kt` (`SetInputCardV2`, `IntegratedLoadInput`, `QuickSuggestionRow`), `components/WorkoutRestOverlay.kt` (`RestTimerOverlay`, `NormalRestContent`, `FeedbackContent`, `RestClockText`) y lo que pinte la cabecera del ejercicio y la tira inferior de la pantalla de la serie (búscalos desde `WorkoutScreen`);
- capturas de la original (salen del vídeo de bienvenida, grabado con ella): `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\antes\f_3.5.png` (serie) y `f_5.png` (descanso).

## Qué cambias (solo la piel)
**Pantalla de la serie:**
1. **Cabecera del ejercicio:**
   - fuera el degradado naranja-marrón: fondo negro (`KpknSurface.canvas`);
   - el nombre del ejercicio en `title` (Syne Bold 24/28), hasta 2 líneas antes de la elipsis;
   - los chips de la cabecera (tiempo, %, etiquetas) como píldoras `inset` con texto Inter 12;
   - mismos botones (historial, voz…) como botones de icono `inset` de 40 dp con zona de 48 dp.
2. **La tarjeta de la serie:**
   - contenedor `module`: #111111, radio 24 dp, filete blanco al 7 %, sin sombra ni brillo;
   - las cajas interiores siguen donde están, en `inset` (#1A1A1A).
3. **Carga:**
   - la etiqueta «Carga (kg)» como eyebrow, la cifra en `metricXL` (Syne ExtraBold 40/44) con números tabulares y el control de modo
     igual;
   - los chips de «Anterior», sugerencia y «+2,5» como píldoras `inset`, con la cifra en Inter SemiBold y coma decimal;
   - el círculo naranja de la cifra intermedia pasa a anillo `#F49A6E` (acento de músculo) de 1,5 dp, sin relleno.
4. **Repeticiones y RIR** (las filas de números se QUEDAN como filas de números):
   - cada número es un círculo `inset` de 48 dp;
   - el elegido, relleno de tinta (#F2EEE6) con la cifra en negro;
   - los demás, en `textMuted`;
   - las etiquetas «REPS» y «RIR» como eyebrow;
   - el «+» de reps igual.
5. **«Opciones avanzadas» y «Ver ejercicio/Fotos»:** botón secundario (píldora con borde de tinta al 24 %, Inter SemiBold 14),
   mismo sitio.
6. **El ✓ flotante:** mismo tamaño y sitio; círculo de tinta (#F2EEE6) con la marca en negro. El naranja deja de ser el color de la
   acción principal.
7. **Flechas ‹ › entre ejercicios:** botones de icono `inset` de 40 dp, mismo sitio.
8. **Tira inferior** (el cajón con «Preparación / Series efectivas», los pasos y los ejercicios):
   - los rótulos como eyebrow en `textMuted`, sin la caja naranja;
   - los pasos (1/3, MOV, APR, S1, S2, +) como círculos `inset`: el hecho con la marca en `ok` (#43D18C), el actual con anillo de
     tinta y el futuro en `textFaint`;
   - la línea que los une, en `divider`;
   - los ejercicios como píldoras `inset`: el actual con relleno de tinta y texto negro, los demás en `textMuted`;
   - el asa del cajón igual.

**Pantalla del descanso:**
9. **Fondo:** velo negro al 92 % sobre la tarjeta de detrás, para que no se transparente. Misma disposición.
10. **Anillo:**
    - mismo tamaño y sitio;
    - pista blanca al 8 % y progreso `#F49A6E`, con 10 dp de trazo y extremos redondos;
    - la cifra en Syne ExtraBold grande y tabular, sin cero delante («3:00», no «03:00»);
    - «DESCANSO» como eyebrow y el nombre del ejercicio en `body` y `textMuted`.
11. **− «Ajustar tiempo» +:**
    - − y + como botones de icono `inset` de 56 dp;
    - «Ajustar tiempo» como píldora `inset` con Inter 14, mismo sitio.
12. **«Último set»:**
    - módulo de una fila;
    - eyebrow «ÚLTIMA SERIE» (la errata de tilde y la palabra «set» se corrigen) y la cifra «100,5 kg × 6», con coma, signo «×» y
      espacios inseparables.
13. **«Plan de sesión» y «Descanso sugerido»:**
    - siguen las dos, una al lado de la otra, en módulos sin tinte: eyebrow y cifra en `metricM` sin cero delante;
    - el acento solo en el icono de la sugerencia;
    - fuera el relleno naranja translúcido;
    - su ✕ y su toque, iguales.
14. **«Saltar descanso»:** botón principal (píldora de 56 dp, tinta #F2EEE6, texto negro Inter SemiBold 16). Fuera la Syne ancha en
    el botón.
15. **La variante con feedback** (`FeedbackContent`): las mismas reglas en sus piezas.

## Lo que NO cambias
- **La estructura y la lógica:** ni la disposición, ni el orden, ni la conducta. No añades ni quitas elementos, no cambias tamaños de
  zona táctil a la baja y no tocas la lógica (`WorkoutViewModel`, controladores).
- **El reverso de la tarjeta y su giro:** `SetCardExerciseMediaBack`, `cardFace` y el `Box` con `rotationY`. Los usa la ola VT.
- **Los archivos de VT:** `SetCardExerciseMediaBack.kt`, `WorkoutMediaCaptureController.kt` y `WorkoutSessionAlbumSheet.kt`.
- **El resto de pantallas de la sesión** (preparación, calentamiento, cardio, hojas, resumen): van en LR1b, si el usuario aprueba.

## Tokens
Usa los de `ui/design` (`KpknSurface`, `KpknShape`, los roles tipográficos y los botones de `KpknButtons`), no valores sueltos. El
acento de la sesión es `#F49A6E`: solo para progreso y lo «activo». Selección y acción principal, en tinta.

## Pruebas y hojas de render
- **Siguen en verde:**
  - `com.example.kpkn.screens.workout.*` y `com.example.kpkn.domain.workout.*`;
  - `*OngoingWorkout*`, `*FinalizeWorkout*` y `*StartWorkoutConflict*`;
  - la compilación de `androidTest`. Las instrumentadas buscan textos: Grep `src/androidTest` antes de cambiar uno.
- **Hoja de render nueva** `render/LiveSessionRefineRenderSheetTest`:
  - el frente de la tarjeta de la serie (con sugerencia y sin ella) y el descanso (normal y con feedback);
  - a 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/lr1a`;
  - con las piezas REALES de la sesión (los composables de arriba) y datos de ejemplo, no con copias.
  - Mira cada PNG con Read y compáralo con `f_3.5.png` y `f_5.png`: ¿la persona reconoce su pantalla? Máximo 3 rondas.
- El `.dbg` final, para que yo lo pruebe en el teléfono:
  `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-lr1a-dbg-<n>.log 2>&1`.
  NO uses el teléfono ni el emulador.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lr1a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*LiveSessionRefine*" > /c/kw/logs/screens-lr1a-<n>.log 2>&1`

## Commit
- `style(workout): la serie de la sesión original con la piel de la estética nueva (LR1a)`.
- `style(workout): el descanso de la sesión original con la piel de la estética nueva (LR1a)`.
- `test(workout): hoja de render del refinado de la serie y del descanso (LR1a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto (archivo y composable).
- Los PNG, con tu comparación frente a la original.
- Pruebas: número y resultado.
- La ruta del `.dbg`.
