# LS4 · Sesión en vivo: la ruta de la sesión (hoja con reordenar, superseries y modo dios) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LS0, LS1, LS2a y LS2b integrados; el mensaje dice
si ya está LS3). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md`, §0, §2 (puntos 2 y 7), §4 y §6: TU especificación;
- el mapa `C:\kw\analysis\maps\workout.md`, §1.2, §2.1 («Roadmap»), §5 (gestos) y §6. Las líneas son de ANTES de LS0 y LS2: búscalas
  por nombre;
- `components/WorkoutRoadmapBar.kt` (modo `EXPANDED`, `GodModeBatchBar`, `GodModePlusCard`, la pila de deshacer) y cómo lo abre
  `WorkoutScreen.kt` (`roadmapMode`);
- la barra de superserie del editor (SE3a, `screens/sessioneditor/design/`), para usar el mismo lenguaje.

Hoy la ruta es el roadmap expandido: una fila horizontal de tarjetas con el modo dios (selección por lote y deshacer). LS2a dejó la
barra de progreso y la isla abriendo ese roadmap. Este paquete lo convierte en una HOJA vertical. La lógica NO cambia: mismo
reordenar, mismas superseries, mismas acciones por lote y misma pila de deshacer.

## Qué haces
1. **La hoja «Ruta de la sesión»** (`screens/workout/design/WorkoutRouteSheet.kt`, cuerpo sin estado, en una `KpknSheet` casi a
   pantalla completa):
   - el título «Ruta de la sesión» en `KpknType.title` y, debajo, «Ejercicio 3 de 6 · 14 de 22 series» en `caption`;
   - **una fila por ejercicio** (64 dp):
     - el estado a la izquierda: `Check` en tinta si está hecho, un punto de 10 dp en `SymbolPalette.musculo` si es el actual y un
       anillo `divider` si está pendiente;
     - el nombre en `bodyStrong` (una línea con «…»);
     - «2 de 4 series» en `caption` tabular;
     - a la derecha, el asa de arrastre (`KpknLineIcons.More` girado o un glifo de asa nuevo en `KpknLineIcons` con el trazo de los
       demás);
     - tocar la fila lleva a ese ejercicio y cierra la hoja (lo que hoy hace tocar una tarjeta);
     - la pulsación larga en el asa arrastra para reordenar, con el mismo mecanismo y las mismas llamadas de hoy;
   - **superseries:** sus ejercicios seguidos y unidos por una barra vertical de 3 dp con el color de la sesión, y encima
     «Superserie A · ronda 2 de 3» en `caption`, como en el editor;
   - **acciones** al pie, como `KpknTextAction` con su icono delante: «Añadir ejercicio» (`Plus`), «Crear superserie» y «Saltar
     ejercicio». Hacen lo mismo que hoy;
   - **modo dios:** `KpknTextAction("Seleccionar")` arriba a la derecha entra en selección; cada fila muestra el círculo de selección
     del catálogo y aparece una isla de vidrio con las acciones por lote de hoy (`GodModeBatchBar`) como píldoras, más «Deshacer» si
     la pila no está vacía. «Listo» sale del modo.
2. **Se abre** desde la barra de progreso (LS2a) y desde la isla inferior (el centro, «Sigue: …»). Sustituye al modo `EXPANDED` del
   roadmap y a su `AnimatedVisibility` en `WorkoutScreen.kt`. Atrás y tocar fuera la cierran.
3. **Lo que queda sin uso** (`WorkoutRoadmapBar` entero o en parte, `GodModePlusCard`, las tarjetas del roadmap…): bórralo con Grep en
   todo `app/src`. Lo que siga en uso, se queda.
4. **Textos:** tipo frase, sin MAYÚSCULAS escritas a mano, «…» de un carácter. Antes de cambiar un texto, Grep en `src/androidTest`
   («Siguiente punto», «Sigue con Press inclinado», «Press inclinado», «Sentadilla frontal», «Quick actions» y otros).
5. **Movimiento:** al soltar una fila, se asienta con resorte; con `wizardReducedMotion()`, sin animación.
6. **Marco compacto** (revisión de LS2b; piezas de LS2a: `WorkoutTopRow`, `WorkoutProgressBar`, `WorkoutExerciseHeader` y
   `WorkoutLiveFrameHeader`, sin cambiar sus firmas). A 360 × 740 dp la pantalla de una serie no cabe: la fila superior, la barra y
   la cabecera ocupan unos 245 dp, con huecos de 30 a 45 dp entre ellas. Ajusta:
   - la barra de progreso, a 8 dp de la fila superior;
   - el eyebrow del ejercicio, a 12 dp de la barra;
   - el chip del protocolo («Drop-set») deja de ocupar su propia línea: va al final de la línea de detalle («Mancuernas · Agarre
     neutro» y el chip a su derecha), o pasa a texto en esa línea si no cabe;
   - el eyebrow de la serie («Serie 2 de 4»), a 12 dp de la cabecera.
   - Objetivo: a 360 × 740 dp al 100 %, con el pie fijo (tira, «Registrar serie» e isla), se ven sin desplazar las cifras, sus ±,
     la fila de RIR y la sugerencia. Compruébalo con la captura de la pantalla entera de la hoja de LS2b
     (`WorkoutSetPanelRenderSheetTest`, `ls2b_pantalla_360x740`), montando el pie FIJO abajo como en la app.
7. **El «Info» abre los medios primero:** hoy la hoja «Técnica» que abre el «Info» de la cabecera empieza en drop-sets, descanso y
   notas (`SetCardTechniqueBack`). Quien toca la «i» junto al nombre espera ver cómo se hace el ejercicio. Haz que el «Info» abra
   la hoja directamente en los medios (`SetCardExerciseMediaBack`), con el nombre del ejercicio como título. «Drop-sets, descanso y
   notas» (en «Opciones avanzadas») sigue abriendo la parte de técnica. Solo cambia qué se ve primero: el interior de las dos piezas
   NO se toca (la ola VT trabaja en `SetCardExerciseMediaBack`).
   - En la captura de la hoja de LS2b la cabecera sale sin el «Info»: pasa `onTechnique` en la muestra para que se vea.

## Pruebas
- **Siguen en verde:**
  - las JVM de `screens/workout/` y `domain/workout/` (en especial las del roadmap, las superseries y el modo dios);
  - `*OngoingWorkout*`, `*FinalizeWorkout*`, `*StartWorkoutConflict*`, `*WorkoutFrame*` y `*WorkoutSetPanel*`;
  - la compilación de `androidTest`.
- **Robolectric** `WorkoutRouteSheetTest`, con el CUERPO de la hoja montado suelto:
  - tocar una fila llama a ir a ese ejercicio;
  - «Añadir ejercicio» y «Saltar ejercicio» llaman a lo suyo;
  - «Seleccionar» muestra los círculos y la isla; elegir dos y una acción por lote llama con sus ids;
  - «Deshacer» llama con el índice.
- **Hoja de render** `render/WorkoutRouteRenderSheetTest`, con el cuerpo montado con datos de ejemplo:
  - una sesión de 6 ejercicios con 2 hechos, el actual y una superserie de 2;
  - el modo dios con 2 elegidos y «Deshacer»;
  - una fila levantada mientras se arrastra;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/ls4`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls4 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*WorkoutRoute*" --tests "*WorkoutSetPanel*" --tests "*WorkoutFrame*" > /c/kw/logs/screens-ls4-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(workout): ruta de la sesión en hoja, con reordenar, superseries y modo dios (LS4)`.
- `fix(workout): marco compacto para pantallas bajas y el «Info» abre los medios del ejercicio (LS4)`.
- Si borras piezas viejas, aparte: `refactor(workout): quita el roadmap expandido (LS4)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lógica de reordenar, superseries, modo dios y deshacer (`WorkoutViewModel`, `WorkoutStructuralPersistenceController`). Lo que
  falte, AL FINAL del VM.
- La serie (LS2b), el descanso (LS3), la preparación y el cardio (LS5), el resumen y el cockpit (LS6) y las hojas de estructura y de
  contexto por dentro (LS7).
- Las firmas de las piezas de LS1, LS2a, LS2b y LS3: solo puedes añadir parámetros opcionales al final.
- `MainActivity.kt` y `screens/sessioneditor/`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Textos cambiados y pruebas actualizadas.
- Pruebas: número y resultado.
- PNG y crítica.
