# SE7 · Editor de sesiones: hojas y diálogos en piezas KPKN (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con SE0, SE1, SE2 y D2 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md`, §6, §8 y §9: TU especificación;
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §1.6, §2 y §7. Las líneas son de ANTES de SE0 y SE2: búscalas por nombre;
- las piezas de SE1 (`KpknNumberCell`) y las hojas ya rediseñadas de Program Detail (`screens/programdetail/design/structure/`) para
  seguir su lenguaje.

Este paquete reviste por dentro las HOJAS del editor que abre el menú «⋯» de SE2 y las de superseries. La lógica de cada hoja NO
cambia: mismos datos, mismas acciones y mismas llamadas al VM. Solo cambia la piel.

## Qué haces
Todas sobre `KpknSheet` (vidrio v2), con título en `KpknType.title`, módulos `KpknModule` cuando hay grupos y la acción principal a todo
el ancho (`KpknPrimaryPill`). Fuera `DarkEditor*`, `KpknSheetTokens` sueltos, `Surface` con color, `FilterChip`, `Switch`, `Button`,
`OutlinedTextField`, `Icons.*` y `Color(0x…)`.
1. **Reglas de la sesión** (`sheets/RulesSheet.kt`):
   - tres módulos: «Ajustes por defecto», «Límites» y «Tiempo»;
   - los interruptores son `KpknSwitch` dentro de `KpknCheckRow` u otra fila de 56 dp;
   - las cifras, `KpknNumberCell` (SE1) con su unidad al lado en `caption`;
   - las reglas guardadas como plantilla («Mis reglas»), en filas con «Aplicar» y «Renombrar»/«Eliminar» en un menú `⋯`; eliminar pide
     confirmación con `KpknAlertDialog(destructive = true)`.
2. **Portada** (`sheets/CoverSheet.kt`):
   - arriba, la vista previa de la franja de 4 dp de SE2 y de un módulo con esa portada;
   - debajo, una rejilla de muestras redondas de 48 dp: degradados y sólidos. La elegida lleva un anillo de tinta y un `Check`;
   - «Sin portada» como primera muestra.
3. **Historial de versiones** (`sheets/HistorySheet.kt`):
   - una fila por versión: la fecha larga («martes 13 de octubre · 18:40») y lo que cambió si el modelo lo dice;
   - «Restaurar» como `KpknTextAction`, con confirmación (`KpknAlertDialog`);
   - si existe una función para guardar la versión actual antes de restaurar, el texto lo dice. Si no existe, no la inventes.
   - Vacío: `KpknEmptyState` «Aún no hay versiones guardadas».
4. **Copiar a otras sesiones** (`sheets/ClonerSaveWarmupSheets.kt`, `SessionClonerSheet`):
   - una `KpknCheckRow` por sesión, con su día;
   - «Copiar a 3 sesiones» como principal, apagado sin elegidas;
   - el resultado, con `showKpknSnackbar`, como hoy.
5. **Guardar como plantilla** (`SaveSheet`): `KpknTextField` para el nombre, una línea de lo que se guarda en `caption` («6 ejercicios ·
   22 series») y «Guardar».
6. **Superseries** (`sheets/SupersetCreatorAndManagerSheets.kt`): crear (elegir miembros con `KpknCheckRow`), gestionar (rondas con
   `KpknNumberCell`, descanso entre rondas, deshacer la superserie en `danger` con confirmación) y la elección de miembros.
7. **Relación entre ejercicios** (`sheets/RelationshipPickerSheet.kt`): filas `KpknRadioRow` con su explicación en `caption`.
8. **Acciones rápidas del ejercicio** (`ExerciseQuickActionsSheet`): filas de 56 dp con icono KPKN Line, nombre y, si borra, en
   `danger`.
9. **Diálogos:** los 3 `AlertDialog` de Material del editor pasan a `KpknAlertDialog` con los mismos textos. Los destructivos llevan
   `destructive = true`.
- Lo que NO es tuyo: el catálogo y su hoja (SE6), el asistente (SE4), calentamiento, movilidad y cardio (SE5).

## Pruebas
- **Siguen en verde:** las 33 clases de `src/test/.../screens/sessioneditor/` (sobre todo `SessionEditorViewModelRulesTest`,
  `SessionEditorRulesEngineTest` y `SessionEditorTransferOutcomeTest`), las de `screens/workout/` que usan piezas del editor
  (`*Workout*`) y la compilación de `androidTest`.
- **Robolectric** `SessionEditorSheetsTest`, con los CUERPOS de las hojas montados sueltos (los toques dentro de `KpknSheet` no llegan
  en Robolectric):
  - reglas: cambiar un interruptor llama con el valor;
  - portada: elegir una muestra llama con su id;
  - copiar: sin elegidas, la principal está apagada;
  - superseries: deshacer pide confirmación.
- **Hoja de render** `render/SessionEditorSheetsRenderSheetTest`: cada hoja con datos de ejemplo, el historial vacío y un diálogo
  destructivo; 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se7`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se7 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" --tests "*SessionEditorSheets*" > /c/kw/logs/screens-se7-<n>.log 2>&1`

## Commit
- `feat(sessioneditor): hojas de reglas, portada, historial, copiar, plantilla y superseries en piezas KPKN (SE7)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- `SessionEditorScreen.kt` fuera de las líneas que abren o cierran estas hojas (SE3 trabaja en la lista en paralelo).
- La lógica del VM y de las hojas.
- Las piezas que usa `screens/workout/` (sus firmas).
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por hoja.
- Piezas nuevas y código que queda sin uso.
- Pruebas: número y resultado.
- PNG y crítica.
