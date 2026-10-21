# CU2 · Cuerpo: composición, altura, objetivo y recordatorio (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con CU0, CU1, I9 y D1 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/BODY_V1.md`, §2 puntos 4 a 6 y §3: TU especificación;
- el mapa `C:\kw\analysis\maps\body.md`;
- lo que dejó CU1 en `screens/body/design/`, para usar el mismo estilo.

CU3 trabaja EN PARALELO en el historial y la hoja de registro de la misma pantalla. Toca solo tus secciones de
`BodyProgressScreen.kt` y AÑADE al final de `BodySamples.kt`.

## Qué haces
1. **Módulo «Composición»** (`screens/body/design/BodyCompositionModule.kt`):
   - La barra apilada magra y grasa, animada, con sus kg y su % debajo en `caption` tabular. Solo con composición compatible (misma
     sesión, la regla de CU0).
   - El FFMI en `KpknMetric` (`metricL`) con su categoría en `caption`.
   - El IMC en `KpknMetric` (`metricL`), con la categoría de `bmiCategory` (4 continuas) sobre el valor redondeado.
   - Junto a cada cifra, `KpknIconButton(KpknLineIcons.Info)`, que abre una hoja con la explicación y su fuente. Los textos de
     «Consejos» y del FFMI que ya existen pasan aquí, sin afirmaciones de salud nuevas.
   - **Sin altura:** `KpknEmptyState`-compacto con «Añade tu altura para ver tu IMC y FFMI» y la acción «Añadir altura», que
     navega a `KpknRoute.SettingsProfile` (mira cómo se edita ahí `userVitals.height`). Nunca 170 cm inventados.
2. **Fuera el módulo «Consejos»** (Screen:1651-1701) y las tarjetas viejas de FFMI, composición, KPIs e IMC (Screen:285-319 y sus
   composables 1352-1645, si quedan sin uso). Los KPIs útiles (% de músculo, masa grasa) quedan en Composición.
3. **Módulo «Objetivo»** (`BodyGoalModule.kt`):
   - Muestra la métrica y la meta («Meta: 60 kg» o «Meta: 15 % de grasa»), desde cuándo y «Editar».
   - **Con plan de nutrición activo:** «Editar» abre el editor del plan (la navegación que ya usa la app para
     `onNavigateToNutritionEditor`; búscala) con una nota bajo la meta: «Tu objetivo vive en tu plan de nutrición».
   - **Sin plan:** «Editar» abre una `KpknSheet` con `WizardWeightRule` (peso, en la unidad del usuario) o la regla de % de grasa,
     según la métrica. «Guardar» llama a `upsertGoal` (ya existe en el VM), con `origin = MANUAL`, sin `linkedPlanId` y con fechas.
     «Quitar objetivo» como acción de texto `danger` con confirmación.
   - **Sin objetivo:** «Aún no tienes objetivo» y «Fijar objetivo».
   - `GoalProgressCard` (la vieja) desaparece.
4. **Módulo «Recordatorio de pesaje»** (`BodyReminderModule.kt`):
   - `KpknSwitch` para activarlo.
   - Frecuencia en `KpknSegmentedControl(Compact)`: Cada día, 7 días, 14 días, 30 días y Otra. Con «Otra», un selector de 1 a 365
     días (paso a paso con − y +, o una regla).
   - La HORA, que se puede editar: abre `KpknNativeTimePickerDialog` (ya existe en `ui/components/`).
     - El modelo guarda hoy la hora en `NutModels:529-530`; si no hay campo editable, añádelo AL FINAL del modelo con valor por
       defecto y migra sin romper el JSON (`ignoreUnknownKeys`).
     - Comprueba que el planificador de recordatorios lea la hora nueva.
   - La próxima fecha, en `caption`.
   - La lógica de `updateSchedule` del VM se mantiene.
5. **Cifra de grasa más visible** (comentario de CU1): en `BodyFatModule`, la cifra pasa de `metricM` a `metricL`.

## Pruebas
- **Robolectric:**
  - sin altura se ve «Añadir altura» y navega;
  - con plan, «Editar» del objetivo va al editor;
  - sin plan, la hoja guarda con `upsertGoal` (VM falso o repositorio en memoria);
  - el interruptor del recordatorio llama a `updateSchedule`;
  - «Otra» muestra el selector.
- **Unitaria:** si añades la hora al modelo, la serialización vieja sigue leyéndose.
- **Siguen en verde:** `*Body*`, `*NutritionActionRouting*` y `*SettingsJsonBackup*`.
- **Hoja de render:**
  - añade casos a `render/BodyPageRenderSheetTest` o crea `render/BodyPageLowerRenderSheetTest`: composición completa, sin altura,
    objetivo con plan, objetivo sin plan, sin objetivo, la hoja de objetivo y el recordatorio en sus estados;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/cu2`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/cu2 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*Body*" --tests "*NutritionActionRouting*" --tests "*SettingsJsonBackup*" --tests "*Reminder*" > /c/kw/logs/screens-cu2-<n>.log 2>&1`

## Commit
- Por rutas: `feat(body): composición con IMC y FFMI honestos, añadir altura, objetivo con su hoja y recordatorio con hora (CU2)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Historial y hoja de registro (CU3).
- `MainActivity.kt`, salvo que la navegación a Ajustes no se pueda pasar como lambda. En ese caso, anótalo y propónmelo.
- Push.

## Informe
- Qué hiciste, por punto.
- Firmas nuevas.
- Cambios de modelo.
- Pruebas: número y resultado.
- PNG y crítica.
