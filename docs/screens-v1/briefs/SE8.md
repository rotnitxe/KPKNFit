# SE8 · Editor de sesiones: cierre (pendientes de la ola, accesibilidad, movimiento y hojas de render de la pantalla entera) (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con SE0 a SE7, SE3a, SE3b, SE4b, SE5 y SE6a integrados;
el mensaje dice si ya está SE6b). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md` entero;
- las decisiones 90 a 125 del registro del día (`C:\kw\analysis\decisiones_2026-10-13_dia.md`), que es de donde sale la lista de
  pendientes.

El editor ya está rediseñado por partes. Este paquete lo DEJA TERMINADO: cierra los pendientes que fueron dejando los paquetes, revisa
la pantalla entera junta y documenta. La lógica NO cambia.

## Qué haces
1. **Pendientes de la ola** (cada uno con su prueba si cambia comportamiento):
   - **acciones rápidas** (`ExerciseQuickActionsSheet`, se abre con la pulsación larga en el nombre de la fila): hoy solo muestra
     «Cambiar ejercicio», aunque le llegan los callbacks de calentamiento, movilidad, superserie, ficha y borrar. Devuélvele una fila
     de 56 dp por cada callback que llegue, con su icono KPKN Line; «Eliminar ejercicio» en `danger` con `KpknAlertDialog(destructive
     = true)`;
   - **«Editar» de la superserie** (línea «Superserie · 3 rondas · …» de SE3a): que abra la hoja de gestión que rehízo SE7
     (`SupersetManagerSheet` o su cuerpo), y no el panel viejo, si hacen lo mismo. Si el panel viejo hace algo que la hoja no, dilo
     y no lo cambies;
   - **«Meta del ejercicio» y «Unidad personalizada»** (diálogos de Material en `ExerciseEditorCard`): pasan a `KpknSheet` con
     `KpknTextField` y `KpknPrimaryPill`, mismos datos y mismas llamadas;
   - **cardio (SE5):**
     - «1 ronda(s)» pasa a «1 ronda»/«2 rondas»;
     - las pausas de la gráfica de intervalos van en blanco al 12 %, no en gris azulado (si la gráfica es la de
       `screens/workout/`, NO la toques: anótalo para LS5b);
     - «Calent.» y «Enfr.» se escriben enteros si caben, o con su descripción para TalkBack;
     - los tramos de 30 s de la mini línea miden al menos 4 dp de ancho;
     - el `Slider` de RPE de Material pasa a una regla KPKN;
   - **plantillas (SE4b):**
     - la leyenda «Músculos · Energía · Columna» va una sola vez arriba de la lista, no en cada fila;
     - `KpknModuleHeader` corta su texto al 130 %: arréglalo en la pieza compartida (que baje la acción a otra línea, como hizo
       SE4b en local) y quita la versión local si sobra;
   - **«Sesión origen» (SE7):** el subtítulo «Martes · Semana 1 · Hipertrofia · 2 ejercicios» deja «ejercicios» huérfano. Pasa a
     «Martes · Semana 1 · 2 ejercicios»;
   - **la fila de un ejercicio dentro de una parte** no repite el nombre de la parte («Press banca · Pecho» dentro de «Pecho»);
   - **chips de opciones del desplegado (SE3b):** que no formen tres líneas irregulares al 100 %. Ordénalos por ancho o junta los
     menos usados («Nota», «Cambiar ejercicio») en un «⋯»;
   - **`SessionEditorFocusUiTest`** (androidTest) busca «group-name», «assistant-sheet», «Abrir asistente» y «Cerrar asistente», que
     ya no existen: actualízala a los tags y textos de hoy (SE2, SE4), o pon el tag `assistant-sheet` en la hoja nueva si la prueba lo
     necesita.
2. **Accesibilidad y movimiento de la pantalla entera:**
   - todo lo tocable mide 48 dp como mínimo;
   - cada fila de ejercicio se lee entera («Press banca, 3 series de 6 a 8, RIR 2, descanso 2 minutos 30, plegado»);
   - con `wizardReducedMotion()`, nada se anima salvo fundidos de 120 ms;
   - revisa las piezas de SE2 a SE7 con Grep (`animate`, `AnimatedVisibility`, `tween`) y corrige lo que se escape.
3. **Hojas de render de la pantalla entera** (`render/SessionEditorScreenRenderSheetTest`): la pantalla del editor montada con
   datos de ejemplo y el VM falso que haga falta, o con las piezas compuestas si el VM no se puede montar (anótalo). Casos:
   - una sesión de fuerza con dos partes, una superserie y un ejercicio desplegado;
   - una con cardio al inicio y preparación;
   - la sesión vacía;
   - el asistente abierto en Impacto;
   - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se8`.
   - Mira cada PNG con Read y critica la COHERENCIA entre piezas (márgenes, ritmo, iconos, tipografía y colores): es la última
     mirada antes de la QA en el emulador. Máximo 3 rondas.
4. **Documentación:** al final de `docs/screens-v1/SESSION_EDITOR_V1.md`, una sección «Estado (SE8)» con lo terminado por punto de
   la especificación, lo que no se hizo y su porqué, y las decisiones de los paquetes que cambian lo especificado.

## Pruebas
- **Siguen en verde:**
  - todas las de `src/test/.../screens/sessioneditor/`;
  - las de `screens/workout/` que usan piezas del editor (`*Workout*`);
  - `*KpknSetTable*`, `*KpknNumberCell*` y `*Catalog*`;
  - la compilación de `androidTest`.
- **Robolectric** `SessionEditorClosureTest`:
  - las acciones rápidas muestran una fila por callback y eliminar pide confirmación;
  - «Editar» de la superserie abre la hoja de gestión.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se8 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" --tests "*KpknSetTable*" --tests "*KpknNumberCell*" --tests "*Catalog*" --tests "*SessionEditorScreenRender*" --tests "*SessionEditorClosure*" > /c/kw/logs/screens-se8-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `fix(sessioneditor): pendientes de la ola (acciones rápidas, superserie, meta y unidad, cardio, plantillas y textos) (SE8)`.
- `feat(sessioneditor): accesibilidad, movimiento reducido y hojas de render de la pantalla entera (SE8)`.
- `docs(sessioneditor): estado del editor de sesiones v1 (SE8)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lógica del VM y de los motores. Lo que falte, AL FINAL del VM.
- `screens/workout/` (salvo leer), las firmas de las piezas que usa la sesión en vivo y `MainActivity.kt`.
- El catálogo de ejercicios si SE6b no está integrado (lo dice el mensaje de lanzamiento).
- Push.

## Informe
- Qué hiciste, por punto (y por cada pendiente).
- Pruebas: número y resultado.
- PNG y crítica de coherencia.
