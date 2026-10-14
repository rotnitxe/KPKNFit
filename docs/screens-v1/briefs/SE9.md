# SE9 · Editor de sesiones: QA en el emulador como una persona, y arreglos (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con el editor terminado, SE0 a SE8, y la sesión en vivo
integrada). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md` entero, sobre todo «Estado (SE8)»;
- `C:\kw\tools\emu_run.py` y `C:\kw\tools\emu_start.py` (uso en su cabecera).

El editor está rediseñado por piezas y probado con hojas de render y Robolectric. Pero **nadie lo ha visto montado con su ViewModel y
datos reales** (SE8: «El ViewModel no se puede montar en Robolectric sin repositorios»). Este paquete lo usa en el emulador como lo haría una
persona, arregla lo que se rompa y documenta. La lógica NO cambia.

## Qué haces
0. **Base:** una corrida de `:app:compileBaseDebugKotlin :app:compileBaseDebugUnitTestKotlin` sobre la rama recién sacada. Si falla en
   archivos que no son tuyos, arréglalo de forma mínima en un commit aparte y dilo en el informe.
1. **El `.dbg` y los datos:**
   - `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-se9-dbg-<n>.log 2>&1`;
   - siembra como en la QA de la PR11: `--clear-dbg` con `WizardHarnessActivity` y `--es persona qa --es scope full --es start REVIEW_ACTIVATE --ez pro true`; ~35 s y «Activar» en (672, 2805); después `DemoSeedActivity` con `--ez pro true`; «Don't allow» al permiso del sistema y «Ahora no» al aviso de la app.
2. **Recorrido** (captura cada paso en `C:/kw/shots/screens/se9-qa/`; mira cada captura con Read):
   1. desde Program Detail, abrir una sesión en el editor (busca la entrada real: «Editar sesión» o la que sea);
   2. el editor en reposo y entero (desplaza hasta abajo): fila superior, cabecera, impacto, preparación, partes y ejercicios, isla;
   3. desplegar un ejercicio: tabla de series, chips, menú «⋯»; cambiar series, repeticiones y RPE (la regla KPKN);
   4. añadir un ejercicio desde el catálogo (página completa) y abrir su ficha;
   5. crear una superserie y abrir su «Editar»;
   6. añadir cardio por intervalos y mirar su gráfica y su mini línea;
   7. el asistente de la sesión (AUGE);
   8. «Meta del ejercicio» y «Unidad personalizada»;
   9. reordenar arrastrando;
   10. una sesión vacía (crea una nueva);
   11. guardar, volver y comprobar que Program Detail muestra el cambio; volver a entrar y comprobar que se conservó;
   12. lo mismo a 360 × 740 dp: añade `--wm 1080x2220@480` a la llamada de `emu_run.py` (la pantalla vuelve sola a la normal al
       terminar esa llamada; en ella los toques van en 1080 × 2220).
   - Mira cada captura con el listón del usuario (por encima de Hevy, Lyfta y MacroFactor): nada cortado ni solapado, ritmo de 16 dp,
     tipo frase, «…» de un carácter, coma decimal, espacio inseparable antes de unidades y %, vocabulario de la app («Superserie»,
     «Repeticiones», «Músculos · Energía · Columna»), nada en inglés, ningún control de Material sin vestir, objetivos de 48 dp.
   - Lo que se rompa o se vea fuera del lenguaje: arréglalo si es del editor (`screens/sessioneditor/`) o de una pieza compartida que solo
     usa el editor; anótalo si no. Regla de los 20 minutos por tanda: si algo se atasca, para, anota y sigue.
   - Si un fallo es de lógica (no guarda, guarda mal, se cuelga): NO lo arregles en el VM; descríbelo con pasos, captura y `--crash-log`.
2b. **Un resto de Program Detail que vio la QA de LS8:** en la lista de ejercicios de la tarjeta de una sesión, el descanso sale «descanso
   195s», «120s» o «45s». Pasa a «descanso 3:15» desde 60 s y a «descanso 45 s» por debajo, con espacio inseparable. Igual en «3×40s»
   («3 × 40 s»). Busca el formateador en `screens/programdetail/` y usa el del proyecto si ya existe uno (Grep `formatRest`,
   `formatSeconds`, `m:ss`). Que el editor diga lo mismo.
3. **Pruebas** de lo que arregles (Robolectric con las piezas sueltas, como hicieron SE1 a SE8).
4. **Documentación:** al final de `docs/screens-v1/SESSION_EDITOR_V1.md`, una sección «QA en el emulador (SE9)»: el recorrido con la ruta
   de cada captura, lo arreglado y lo anotado.

## Pruebas
- **Siguen en verde:** `com.example.kpkn.screens.sessioneditor.*`, `com.example.kpkn.screens.programdetail.*`, `com.example.kpkn.ui.*` y
  la compilación de `androidTest`.
- Las tuyas nuevas.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.screens.programdetail.*" --tests "com.example.kpkn.ui.*" > /c/kw/logs/screens-se9-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `fix(sessioneditor): lo que la QA en el emulador encontró (SE9)`.
- `docs(sessioneditor): QA del editor en el emulador (SE9)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lógica del editor (`SessionEditorViewModel`, sus controladores y los motores). Lo que falte, AL FINAL del VM.
- `screens/workout/` (sesión en vivo), `MainActivity.kt`.
- Usar adb por tu cuenta, matar el servidor adb, apagar el emulador o dejarlo con otro tamaño.
- Push.

## Informe
- La QA paso a paso, con la ruta de cada captura y lo que encontraste.
- Qué arreglaste (archivo y por qué) y qué anotaste para otros.
- Pruebas: número y resultado.
