# LR1g · El relator: ordenado, oportuno y con el espacio bien repartido (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `cbdd0ee8d`). Lee ENTERO
`docs/screens-v1/briefs/LR1_COMUN.md` y lo que pide leer.

El usuario lo vio en su teléfono y lo pidió así: que el relator sea ordenado e inteligente, que dé sus mensajes en el momento
oportuno sin cambiar a cada rato cuando no pasa nada, y que no queden huecos vacíos encima que rompan la simetría.

## El problema, medido en el teléfono (Z Flip5, 360 × 880 dp, letra al 100 %)
Mira con Read `C:\kw\shots\screens\phone-int\relator\relator_r1.jpg`. Son 14 capturas de un descanso de 3:15, una cada 5 s, sin
tocar nada. En 65 s el mensaje cambió 13 veces entre 7 textos:
- el mismo texto con un rótulo distinto cada vez: «Carga: Descanso. Vas en la serie 2 de 4 de …», «Hoy: …», «Plan: …»;
- un «Siguiente» falso: «Siguiente: Descanso. Serie 2. Prepara Peso Muerto Piernas Rígidas · Barra a 71 kg.». Quedaban 3 series de la
  sentadilla;
- frases que no son de producto: «Mantén el recetario: … cuenta tal cual.», «Recobro de 195s como marca el plan de …» y «Ahora:
  Siguiente: … Aprovecha el timer.» (dos rótulos, inglés y «195s»);
- en el cambio, los dos textos se ven a la vez y desplazados (r1_03, r1_04), y un brillo recorre la línea cada 2,2 s iluminando
  palabras sueltas.

El espacio:
- **Serie** (`C:\kw\shots\screens\phone-int\i5_a.png`): la banda del relator mide ~85 dp para dos líneas de 16 sp. Quedan ~48 dp de
  aire encima del texto y ~24 dp debajo.
- **Descanso** (`i7_a.png`): 24 + 10 dp vacíos encima del relator, y una fila entera solo para el botón de minimizar, a la derecha.

## Las causas (verificadas en el código)
1. `WorkoutViewModel.ensureRelatorIdle` (~L4097): `_relatorIdleCycle` sube cada 18 s en el descanso y cada `RELATOR_IDLE_ROTATE_MS`
   (28 s) fuera. Cada ciclo cambia la semilla, y con ella la variante; con `idleCycle > 0` además añade un rótulo.
2. `RelatorComposer.OPENERS` + `pickOpener`: un rótulo al azar («Ahora», «Ojo», «Dato», «Plan», «Siguiente», «Hoy», «Carga»,
   «Recuerda») que no tiene que ver con lo que dice la frase.
3. El flujo del relator (~L4030) vuelve a resolver con cada segundo de `restSeconds` y `sessionRemain` (con un `debounce` de 400 ms).
   Además, `RelatorSelector.select` descarta el tema anterior (`it.topic != state.lastTopic`) y prefiere huellas sin usar. Así, cada
   vez que resuelve tiende a cambiar de mensaje.
4. `RestObserver` usa `progress.nextExerciseName` aunque queden series del ejercicio actual. Sus frases («Aprovecha el timer»,
   «Recobro de …s», «Mismo recobro en overlay o tarjeta…») y la de `PlanObserver` L238 («Mantén el recetario…») no son de producto.
5. `WorkoutLiveRelatorBar.kt`: un `rememberInfiniteTransition` (el brillo cada 2,2 s) y un `AnimatedContent` con deslizamiento y
   expansión horizontal.

## Qué haces

### A. Momentos: el relator solo cambia cuando pasa algo
En `domain/relator/` (puro, sin Android) crea `RelatorMoment` y una función pura que lo saca del contexto:

| Momento | Cuándo | Prioridad |
|---|---|---|
| `EXERCISE_START` | entra un ejercicio nuevo (o su calentamiento) | alta |
| `SET_READY` | una serie pasa a ser la actual: acaba el descanso o la persona navega | alta |
| `SET_LOGGED` | se registra una serie. Es la reacción, lo primero que se ve en el descanso | alta |
| `USER_EDIT` | la persona cambia la carga, las reps o el RIR de la serie actual; cuenta 1,5 s después de la última edición | alta |
| `ASSIST` | aparece una ayuda de estructura (las de `WorkoutLiveRelatorAssist.kt`) | alta |
| `REST_PREP` | quedan ≤ 15 s de descanso; una vez por descanso | media |
| `REST_MID` | la mitad del descanso, solo si dura ≥ 150 s; una vez | baja |
| `MILESTONE` | mitad de la sesión, último ejercicio o tiempo justo; una vez cada uno | baja |
| `IDLE_NUDGE` | en la serie, ≥ 120 s sin registrar ni editar; una vez por serie | baja |

Reglas:
- El motor resuelve SOLO cuando cambia el momento: la clave es la serie, la fase, el tipo de momento y su versión. Los segundos del
  reloj (descanso y sesión) ya no hacen resolver; solo sirven para detectar `REST_PREP`, `REST_MID` y `MILESTONE`.
- **Permanencia mínima de 12 s.** Un momento de prioridad alta sustituye al instante; uno de prioridad media o baja espera a que se
  cumplan los 12 s.
- **Elección determinista dentro de un momento:** prioridad y relevancia, sin `jitter` y sin «evitar el tema anterior». La variedad de
  frases queda ENTRE series (`cooldownSets` y huellas), nunca dentro del mismo momento.
- **Fuera la rotación:** el bucle de `_relatorIdleCycle` (18 s / 28 s) y `RELATOR_IDLE_ROTATE_MS`. Lo sustituye `IDLE_NUDGE`.
- **Fuera los rótulos:** `OPENERS` y `pickOpener`, nunca más un prefijo. Si una frase necesita contexto, va dentro de la frase.
- **Voz:** si el relator habla, lo hace con los mismos momentos. Sin momento no se dice nada nuevo.

### B. Lo que dice, correcto y en español de producto
- **«Siguiente»:**
  - es la próxima serie del MISMO ejercicio mientras queden («serie 2 de 4, 95 kg × 6»);
  - el ejercicio siguiente solo sale en el descanso tras su última serie;
  - en superserie, la pareja.
- **Reescribe las frases** de todos los observadores (`domain/relator/*Observer.kt`) y de `WorkoutLiveRelatorCatalog.kt`:
  - tipo frase, sin inglés («timer» pasa a «descanso»), y sin «recobro», «recetario», «tal cual», «overlay» ni «tarjeta» como jerga
    interna;
  - tiempos con `KpknSetFormat.doseTime` («3:15», «45 s»); series como «95 kg × 6» (espacio duro antes de «kg» y «×» con espacios);
    coma decimal;
  - una idea por frase, como mucho 90 caracteres, y lo útil primero.
- **Frases de referencia por momento** (las apruebo; varía sobre ellas sin perder el tono):
  - `SET_LOGGED`: «Serie 1 hecha: 95 kg × 6, igual que la última vez.» · «2,5 kg más que la última vez.» · con récord: «Récord: 100 kg × 5.»
  - `REST_MID`: «La última vez, en la serie 2: 95 kg × 6.»
  - `REST_PREP`: «Prepárate: serie 2 de 4, 95 kg × 6.» · tras la última serie: «Prepárate: peso muerto rumano, 71 kg.»
  - `SET_READY`: «Serie 2 de 4: 6 reps a RIR 2.»
  - `USER_EDIT`: «97,5 kg: 2,5 kg más que la última vez.»
  - `EXERCISE_START`: «Peso muerto rumano: 3 series de 8. La última vez, 70 kg × 8.»
- `formatRelatorSetMark` (`WorkoutLiveRelatorMemory.kt` L176) pasa a «95 kg × 6».
- **Prueba de frases:** recorre todas las plantillas (observadores y catálogo) y falla si alguna tiene:
  - «timer», «recobro», «recetario», «tal cual» u «overlay»;
  - una cifra pegada a la «s» (`\d+s\b`);
  - «x» o «×» sin espacios entre cifras;
  - dos «: » en la misma frase.

### C. Cómo se ve (`WorkoutLiveRelatorBar.kt`)
- Sin el brillo infinito: texto fijo en tinta al 72 %. Los enlaces siguen en tinta y subrayados.
- Al cambiar de mensaje, solo fundido: sale en 150 ms y entra en 220 ms (`FastOutSlowIn`), sin deslizamiento ni expansión. El texto,
  siempre centrado.
- Con «reducir movimiento» (escala de animación 0), sin animación.
- TalkBack anuncia solo cuando cambia el momento, como hace hoy `phaseKey`.

### D. El espacio, igual arriba y abajo
- **Serie** (`WorkoutV2Body.kt` ~L236 y ~L2281, y `WorkoutUiTokens.liveRelatorBandHeight`):
  - la banda del relator mide 12 dp + dos líneas (2 × 16 sp de interlineado) + 12 dp, con el texto centrado en vertical;
  - con una sola línea, el texto queda centrado en la misma banda: la tarjeta no salta;
  - la tarjeta sube lo que se ahorra.
  - Comprueba en la hoja a 360 × 880 dp:
    - el hueco entre el borde inferior de la fila de chips y la primera línea es igual al hueco entre la última línea y el borde
      superior de la tarjeta (±2 dp);
    - el borde inferior de la tarjeta sigue ≥ 12 dp por encima del cajón plegado (LR1a2);
    - el ✓ no pisa el texto de la píldora derecha.
- **Descanso** (`components/WorkoutRestOverlay.kt`, `NormalContent` ~L337 y `FeedbackContent` ~L180):
  - quita el `Spacer(10.dp)` y deja el relleno superior en 8 dp;
  - el botón «Minimizar» sube, CENTRADO, a su propia fila arriba, como un asa;
  - debajo, a 12 dp, el relator; el aro, a 16 dp del relator;
  - en `FeedbackContent`, el mismo orden (sin el botón si no lo tiene).
- **Descanso en página** (`components/RestLiveCard.kt`): el relator con 12 dp arriba y 12 dp abajo.
- Esos tres archivos también los tocará la ola VT (VT8): cambia SOLO esos bloques.

## Permitido y prohibido
- **Permitido:**
  - `domain/relator/`;
  - `screens/workout/WorkoutLiveRelator*.kt` y `WorkoutRelatorEngine.kt`;
  - `WorkoutViewModel.kt`, solo el flujo del relator (~L4000–4130) y sus campos;
  - los bloques citados de `WorkoutV2Body.kt`, `WorkoutRestOverlay.kt`, `RestLiveCard.kt` y `WorkoutUiTokens.kt`.
- **Prohibido:**
  - cambiar lo que HACEN las ayudas (`performRelatorAssist`) o el motor de voz;
  - el resto de `WorkoutV2Body.kt` y `WorkoutRestOverlay.kt`;
  - push;
  - el teléfono y el emulador: la prueba en el teléfono la hago yo.

## Pruebas
- **Nuevas y puras** (`domain/relator` y `screens/workout`):
  1. 195 s de descanso con un tic por segundo y sin eventos: como mucho 3 textos distintos (`SET_LOGGED`, `REST_MID`, `REST_PREP`), en
     ese orden.
  2. 180 s en la serie sin eventos: como mucho 2 textos (`SET_READY` y un `IDLE_NUDGE`).
  3. Una edición cambia el texto en ≤ 1,5 s; dos ediciones en menos de 1 s dan un solo cambio.
  4. Un `REST_PREP` que llega antes de cumplir los 12 s espera.
  5. «Siguiente» con 4 series:
     - tras S1, S2 del mismo ejercicio;
     - tras S4, el ejercicio siguiente;
     - en superserie, la pareja.
  6. Ninguna frase lleva un rótulo de la lista vieja.
  7. La prueba de frases del punto B.
- **Existentes:**
  - las de `domain/relator` (`RelatorComposerTest`, `RelatorCorpusTest`, `RelatorSelectorTest`, `RelatorMemoryTest`,
    `RelatorPlanAwareTest` y `RelatorPerformanceTest`);
  - y en `screens/workout`: `RelatorSpeechVarietyTest`, `WorkoutLiveRelatorTest`, `WorkoutLiveRelatorMemoryTest`,
    `RelatorActionMappingTest` y `RelatorLoadAnchorTest`.

  Cambia SOLO lo que exige la regla nueva: variedad entre series y estabilidad dentro de un momento. En el informe, di cuáles y por qué.
- **Hoja de render** `render/LiveSessionRelatorRenderSheetTest`, a 360 dp, al 100 % y al 130 %. PNG en `C:/kw/shots/screens/lr1g`.
  Incluye:
  - la serie con el relator en dos líneas y en una;
  - el descanso con el relator y el botón centrado;
  - el descanso en página.
  En el informe, los huecos medidos.

## Comando
`KPKN_SLOT_PRIORITY=1 KPKN_RENDER_DIR=C:/kw/shots/screens/lr1g bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.relator.*" --tests "com.example.kpkn.screens.workout.*" --tests "*LiveSessionRelator*" --tests "*OngoingWorkout*" > /c/kw/logs/screens-lr1g-<n>.log 2>&1`

- Las corridas intermedias pueden ir sin `:app:compileBaseDebugAndroidTestKotlin` y con menos filtros.
- Mira los PNG antes de lanzar otra corrida.
- Sin `.dbg`: lo compilo yo con todo integrado.

## Commit
- `fix(relator): solo cambia de mensaje cuando pasa algo, sin rotación ni rótulos al azar (LR1g)`.
- `fix(relator): «Siguiente» es la serie que toca y las frases, en español de producto (LR1g)`.
- `style(relator): sin brillo, fundido limpio y el mismo hueco arriba y abajo (LR1g)`.
- `test(relator): hoja de render del relator (LR1g)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Los momentos y dónde viven.
- Antes y después: cuántos cambios hay en un descanso simulado de 195 s.
- Las frases: lista de antes → después.
- Los huecos medidos (serie y descanso), con sus PNG.
- Pruebas: número y resultado, y qué pruebas existentes cambiaron y por qué.
