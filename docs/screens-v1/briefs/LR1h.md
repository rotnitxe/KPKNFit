# LR1h · Relator: sin frases de relleno y con algo útil en cada momento del descanso (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `cad9f1c16`). Lee antes:
- `docs/screens-v1/briefs/LR1g.md` (los momentos y las reglas de frase);
- `docs/screens-v1/briefs/LR1_COMUN.md`;
- `docs/wizard-v3/BRIEF_COMUN.md`.

## Lo que vimos en el teléfono (15-oct, sentadilla, descanso de 4:07)
LR1g funciona en el ritmo: un mensaje por momento y nada de cambios sin motivo (0 cambios en 62 s con la serie delante; 3
mensajes en el descanso). Lo que falla es **qué dice**:

| Momento | Lo que dijo | Problema |
|---|---|---|
| Serie registrada (inicio del descanso) | «No adelantes el plan de Sentadilla Trasera con Barra Alta; esta serie tiene trabajo.» | Relleno: la serie ya está hecha. No se entiende. |
| Mitad del descanso | «Si dudas, baja 2,5 kg en Sentadilla Trasera con Barra Alta y conserva el gesto.» | Relleno genérico; no habla de la serie que acaba de hacer. |
| Últimos 15 s | «Prepárate: serie 2 de 4, 95 kg × 6.» | Bien: es el modelo a seguir. |
| Serie lista | «La carga axial comprime en vertical. No dejes que el tronco se rompa. Sentadilla Trasera con Barra Alta.» | El nombre del ejercicio pegado al final como frase suelta. |

### Causa (verificada en el código)
- **`padVariants`** (`domain/relator/PlanObserver.kt` ~L230) completa hasta 6 variantes con frases genéricas («No adelantes el
  plan…», «Si dudas, baja 2,5 kg…», «{ex} va como está escrito en la receta.»…).
  - Lo usan TODOS los observadores: Plan, Rest, Technique, Coach, History, Media, Milestone, Readiness y SessionProgress.
  - Cuando un observador tiene una o dos frases reales, la rotación por momento acaba eligiendo el relleno. En el descanso, el
    `RestObserver` tenía su frase de «Prepárate» y poco más.
- **`TechniqueObserver`** (~L14) hace `if (line.contains("{ex}")) line else "$line {ex}."`. Por eso la frase de técnica termina con
  el nombre del ejercicio suelto.

## Qué haces
1. **Fuera el relleno.**
   - `padVariants` no añade frases genéricas. Un observador con pocas frases reales tiene pocas variantes.
   - Comprueba que el selector y el compositor funcionan con 1 o 2 variantes: rotación por módulo y sin repetir de inmediato si hay
     otra. Ajústalos si hace falta.
   - Borra las frases genéricas de `padVariants` y de `fallbackLines` del Plan que no digan nada concreto de la serie.
2. **Técnica sin el nombre al final.** La frase de técnica va tal cual. El nombre ya está en la cabecera.
3. **El descanso dice algo útil en cada momento.** Con los datos que ya trae el contexto: `enteredWeightKg`, `targetReps`,
   `targetRir`, `targetRpe`, `prescribedWeightKg`, `progress`, `rest` e `history`.
   - **Serie registrada:** cómo fue la serie frente a lo previsto, en una frase corta.
     - En lo previsto: «95 kg × 6 a RIR 2: lo previsto.»
     - Por encima (más reps que el máximo del rango, o más RIR del previsto): «Te sobró margen: la próxima puede subir 2,5 kg.»
     - Por debajo: «Una rep menos de lo previsto: mantén la carga.»
     - Marca personal: la de MilestoneObserver, que ya existe, con prioridad.
     - Si el contexto no sabe lo que hizo la persona (sin reps registradas), di qué toca después, como en «Prepárate».
   - **Mitad del descanso** (descansos de 150 s o más): lo que ya promete el comentario de `RestObserver`, cómo fue esta serie la
     última vez («La última vez: 92,5 kg × 6.»).
     - Sin historial, el siguiente objetivo y el tiempo que queda: «Quedan 2:00. Después, serie 2 de 4: 95 kg × 6.»
   - **Últimos 15 s:** como hoy.
   - **Formato:** `KpknSetFormat`, con espacio inseparable antes de «kg», coma decimal y «×» con espacios.
   - **Estilo:** sin muletillas de apertura y sin signos de exclamación.
4. **Prueba de frases.**
   - Simula una sesión de un básico: serie lista, registrada (en lo previsto, por encima, por debajo y sin datos), mitad del
     descanso (con y sin historial) y últimos 15 s.
   - Falla si alguna frase:
     - está en la lista de relleno prohibido;
     - termina con el nombre del ejercicio como frase suelta;
     - en un momento del descanso, no menciona la serie, la carga, las reps o el tiempo.
   - Usa el mismo estilo que la prueba de frases de LR1g.

## Pruebas
- Siguen en verde: `com.example.kpkn.domain.relator.*` y `com.example.kpkn.screens.workout.*`.
- Nuevas: la de frases y una por momento del descanso.
- **Hoja de render:** `render/LiveSessionRelatorRenderSheetTest` (descanso en página, al 100 % y al 130 %) con la frase de serie
  registrada y la de mitad de descanso. Que quepan en dos líneas.

## Permitido y prohibido
- **Permitido:** `domain/relator/`, `domain/concepts/` (solo los textos), el `RelatorContext` que construye `screens/workout/` (solo
  si falta un dato) y pruebas.
- **Prohibido:**
  - la UI de la sesión en vivo (la barra del relator y las tarjetas);
  - `screens/sessioneditor/`;
  - push;
  - el teléfono y el emulador.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lr1h bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.relator.*" --tests "com.example.kpkn.screens.workout.*" --tests "*LiveSessionRelator*" > /c/kw/logs/screens-lr1h-<n>.log 2>&1`

- Mira los PNG antes de lanzar otra corrida.
- Tope: 4 corridas.
- Sin `.dbg`.

## Commit
- `fix(relator): fuera las frases de relleno y el nombre del ejercicio suelto al final de la técnica (LR1h)`.
- `feat(relator): el descanso dice cómo fue la serie, cómo fue la última vez y qué toca (LR1h)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Las frases nuevas, por momento.
- Lo que cambió en el selector o el compositor.
- Los PNG.
- Pruebas: número y resultado.
