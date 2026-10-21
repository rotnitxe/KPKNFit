# ER2b · Editor: remates de la prueba en el teléfono (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `cad9f1c16`). Lee antes:
- `docs/screens-v1/SESSION_EDITOR_R1.md`;
- `docs/screens-v1/briefs/LR1_COMUN.md` (la piel);
- `docs/wizard-v3/BRIEF_COMUN.md`.

**Regla de oro (del usuario): refinar, no quitar.** Nada de lo que hay desaparece ni se esconde. Cada punto es un remate visible en
el teléfono (Z Flip5, 360 dp, con la letra de la persona algo más grande que la de serie).

## Qué haces

### 1. Los ejercicios sueltos se nombran cuando hay grupos
- **Lo que pasa:** una sesión del generador tiene sus ejercicios sueltos, fuera de `parts`, en un módulo sin cabecera. Al tocar
  «Nuevo grupo», el grupo («Grupo 1») sale con su cabecera, pero el módulo de arriba sigue sin rótulo y parece un grupo huérfano.
- **Arreglo:** con al menos un grupo, encima del módulo de los sueltos va el rótulo de sección «SIN GRUPO». Usa el mismo estilo que
  el rótulo «CARDIO» de la sección de cardio: mayúsculas espaciadas y `textFaint`.
- Sin grupos, como hoy (sin rótulo).

### 2. Borrar un grupo deslizando, sin estado roto
- **Lo que pasa:** al deslizar la cabecera de un grupo hacia la izquierda (`SwipeToDeleteCard` en `components/GroupEditorCard.kt`),
  el nombre desaparece. Queda una píldora negra vacía con una baldosa de papelera, sin diálogo. Hay que tocar la papelera para que
  salga «Eliminar grupo».
- **Arreglo, igual que deslizar un ejercicio (ER1b):**
  - el contenido se desliza y deja ver a su derecha el fondo `danger` con la papelera;
  - al pasar el umbral se abre directamente el diálogo «Eliminar grupo» (o el de las dos opciones si tiene ejercicios);
  - al cancelar, la cabecera vuelve a su sitio con su nombre.
- La prueba `SessionListBehaviorTest.borrarDesdeLaCabeceraAbreSuDialogo` sigue en verde. Añade la de «cancelar devuelve la
  cabecera».

### 3. La duración del asistente suma lo mismo que Reglas › Tiempo
- **Lo que pasa:** el asistente (pestaña Impacto) dice «61 min · Preparación 7 · Ejecución 20 · Descansos 26», que suman 53. Reglas
  › Tiempo dice «Preparación 7 min · Ejecución 20 min · Descansos 26 min · Calentamiento 8 min», que suman 61.
- **Arreglo:**
  - una sola función de desglose, usada por los dos;
  - el asistente incluye el calentamiento;
  - formato `KpknSetFormat`, con «min».

### 4. Los minutos de la barra inferior
- **Lo que pasa:** bajo «Reglas» se lee `60'` (apóstrofo). Es un formato que la app no usa.
- **Arreglo:** «60 min», con espacio inseparable. Si no cabe al 130 %, la cifra sola, con «min» más pequeño debajo o al lado. Que no
  se corte nunca.

### 5. Crear superserie: la misma miniatura que la lista
- **Lo que pasa:** en `SupersetMemberPickerSheet`, los ejercicios sin foto salen con una letra («Z», «E»). En la lista de la sesión
  tienen un glifo de línea.
- **Arreglo:** usar la misma pieza de miniatura que la fila de la lista.

### 6. Cardio
- **Lo que pasa:**
  - la Air Bike muestra la celda «GPS · No» (`components/CardioEditorCard.kt` ~L691);
  - en «Añadir cardio» se repiten iconos: Elíptica, Remo ergómetro y SkiErg comparten uno; Escaladora y Trineo comparten otro.
- **Arreglo:**
  - la celda GPS solo aparece en los cardios al aire libre (los que admiten GPS);
  - cada tipo de cardio con su propio icono, de los que ya existen en `KpknLineIcons` o de los glifos de patrón;
  - si para alguno no hay icono, no dibujes uno nuevo: deja el genérico y dímelo en el informe;
  - el carrusel de plantillas del cardio (Tabata, 30/30…) se corta en seco por la derecha: el mismo fundido de borde que ER1f puso en
    las filas de chips.

### 7. «Ajustar descansos justo hasta el límite» (Reglas › Tiempo)
- **Lo que pasa:** con 61 min estimados y 60 de límite (+1), la propuesta dice «Ajustar descansos justo hasta el límite» y «−6 min».
- **Arreglo:**
  - averigua si recorta 6 min de verdad;
  - si es así, el título dice lo que hace («Recortar descansos») y no promete «justo hasta el límite»;
  - si el recorte debería ser de 1 min, corrige el cálculo, con una prueba.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.sessioneditor.*`.
- Nuevas: una por punto: rótulo «SIN GRUPO» con y sin grupos; el diálogo al deslizar y cancelar; el desglose único; «60 min»; la
  miniatura del selector; GPS solo al aire libre; la propuesta de descansos.
- **Hoja de render:** escenas nuevas en las hojas que ya existen, al 100 % y al 130 %:
  - la lista con un grupo y los sueltos;
  - la cabecera de grupo a medio deslizar;
  - la barra inferior;
  - el selector de superserie;
  - el cardio de Air Bike;
  - «Añadir cardio».

## Permitido y prohibido
- **Permitido:** `screens/sessioneditor/` (salvo lo de ER2a), `domain/sessionassistant/` solo para el punto 7 y pruebas.
- **Prohibido** (lo toca ER2a a la vez):
  - `SessionEditorSessionHelpers.kt`, `SessionEditorViewModelSets.kt`, `components/ExerciseSetList.kt`, `components/InlineSetRow.kt`
    y `SessionExerciseDose.kt`;
  - `screens/workout/`;
  - el catálogo;
  - `ui/design` y `ui/components`, salvo usarlos;
  - push;
  - el teléfono y el emulador.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/er2b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.domain.sessionassistant.*" --tests "*SessionEditor*RenderSheet*" --tests "*SessionSuperset*" > /c/kw/logs/screens-er2b-<n>.log 2>&1`

- Mira los PNG antes de lanzar otra corrida.
- Tope: 5 corridas.
- Sin `.dbg`.

## Commit
- `fix(sessioneditor): los ejercicios sueltos se nombran cuando hay grupos y borrar un grupo deslizando abre su diálogo (ER2b)`.
- `fix(sessioneditor): la duración del asistente suma como Reglas y la barra dice «min» (ER2b)`.
- `style(sessioneditor): la miniatura del selector de superserie y el cardio sin GPS bajo techo, con sus iconos (ER2b)`.
- `fix(sessioneditor): la propuesta de descansos dice lo que recorta (ER2b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Por punto: qué cambió y dónde.
- Los PNG.
- Pruebas: número y resultado.
