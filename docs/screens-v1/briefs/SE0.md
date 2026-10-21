# SE0 · Editor de sesiones: limpieza antes del rediseño (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3`). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md` (§1, §8 y §9);
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §7 entero (es tu lista de trabajo).

Es un paquete de LIMPIEZA: no cambia ningún diseño ni ninguna lógica. Deja el editor listo para rediseñarlo sin arrastrar código
muerto ni textos rotos.

Código del editor: `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/`. La sesión en vivo
(`screens/workout/`) importa 29 piezas del editor: antes de borrar o renombrar algo, Grep en TODO `app/src` (main, test y
androidTest).

## Qué haces
1. **Código muerto** (el mapa, §7.1): las 73 funciones y el objeto `SessionListItem.AddActions` con su rama `-> Unit` en
   `SessionEditorScrollRenderer.kt`.
   - Confirma CADA uno con Grep sobre `app/src` (nombre exacto, `-w`) antes de borrarlo. Si algo tiene uso, no se borra: anótalo.
   - Si un archivo queda vacío, bórralo (`components/SetEditorUnilateralAndSuperset.kt`, `components/sheets/AugeSheetParts.kt` si
     se queda sin nada vivo…).
   - Los imports que queden sin uso, fuera.
2. **Duplicados triviales:**
   - los imports duplicados de `SessionEditorScreen.kt:98-102`;
   - los envoltorios de arrastre de `SessionEditorScreen.kt` que solo reenvían al controlador: déjalos si quitarlos obliga a tocar
     la lógica; anótalos.
3. **Textos** (el mapa, §7.3):
   - «Defaults …» pasa a «Ajustes por defecto …» (`RulesSheet.kt:370`, `SessionEditorScreen.kt:1022`);
   - «RINGS DE SESIÓN» pasa a «RINGS de la sesión»;
   - «...» en ASCII pasa a «…»: «Calculando análisis…», «Buscar plantilla…»;
   - «vinculo» pasa a «vínculo»;
   - los emojis ⚡ y 💨 de la descripción HIIT generada (`CardioHiitEditor.kt:563-565`) salen del texto;
   - ✓, ★, ✎ y ✕ como etiquetas (`SessionContextNavigator.kt:623, 721`; `RulesSheet.kt:803, 808`) pasan a palabras o a iconos
     KPKN Line (`Check`, `Edit`, `Close`). La estrella pasa a «Principal»;
   - `error.message` en pantalla (`TemplateCatalogBrowser.kt:107, 115`) pasa a «No se pudo cargar las plantillas. Inténtalo de nuevo.»
     o el texto llano equivalente;
   - las MAYÚSCULAS literales de la lista del mapa NO las toques aquí: se van con el rediseño (son eyebrows).
4. **Logs:** las 11 llamadas a `android.util.Log`/`println` del editor. Las de depuración pura (sin valor en producción) fuera; las
   de errores reales, se quedan.
5. **Pruebas instrumentadas rotas que ya lo estaban:**
   - `SessionEditorFocusUiTest` busca tags («group-name», «assistant-sheet») y textos («Abrir asistente», «Cerrar asistente») que no
     existen en producción;
   - `ExercisePickerCardsUiTest` busca «Cerrar definición», que tampoco existe.
   - NO las arregles poniendo los textos en la UI: anota en el informe qué buscan y qué hay hoy, para que el rediseño (SE2, SE6) las
     deje bien. Solo comprueba que compilan.

## Pruebas
- **Siguen en verde:** todas las de `src/test/.../screens/sessioneditor/` (33 clases) y las de `screens/workout/` que usen piezas
  del editor (Grep de los imports), y la compilación de `androidTest`.
- Si borras una función que una prueba usa SOLO para probarla a ella misma, borra esa prueba también y anótalo.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" > /c/kw/logs/screens-se0-<n>.log 2>&1`

## Commit
- `refactor(sessioneditor): quita el código muerto del editor de sesiones (SE0)`.
- `fix(sessioneditor): textos en español, sin emojis ni «...» y errores en lenguaje llano (SE0)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Cambiar diseño, lógica o firmas de las piezas que usa `screens/workout/`.
- `MainActivity.kt`.
- Push.

## Informe
- Líneas quitadas, por archivo.
- Lo que NO borraste porque tenía uso.
- Textos cambiados.
- Lo que buscan las dos pruebas instrumentadas rotas.
- Pruebas: número y resultado.
