# LS5a · Sesión en vivo: la preparación antes de empezar (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LS0, LS1, LS2a y LS2b integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md`, §0, §5 («Preparación») y §6: TU especificación;
- el mapa `C:\kw\analysis\maps\workout.md`, §1.1 (preparación), §2.1 («Preparación») y §6. Las líneas son de ANTES: búscalas por
  nombre;
- `components/WorkoutReadinessSheet.kt` entero (unas 1.240 líneas) y `ui/components/rings/` (`KpknTowerRings`, `KpknRingColors`).

La hoja de preparación sale antes de empezar la sesión: «¿Cómo llegas a entrenar hoy?», los RINGS ajustables, las molestias y la
voz. Hoy mezcla `Slider`, `Switch` y `Card` de Material con colores fijos. Este paquete la reviste. La lógica NO cambia: mismos
ajustes, mismas anclas, mismas molestias y la misma voz.

## Qué haces
1. **La hoja** (`WorkoutReadinessSheet`), en `KpknSheet` casi a pantalla completa:
   - el título «¿Cómo llegas a entrenar hoy?» en `KpknType.title` (texto de G1, se conserva);
   - **la torre de los RINGS grande** (`KpknTowerRings`, 160 dp) con los tres valores, y debajo una fila por canal (Músculos,
     Energía y Columna) con su color de `KpknRingColors`, su cifra en `metricM` tabular con «%» y su ajuste;
   - **el ajuste por canal:** `AdjustableRingCompact` se queda (lo usa el asistente), revestido por dentro sin colores fijos, o se
     sustituye por una regla horizontal como `WizardWeightRule` de 0 a 100. Elige lo que no cambie lo que se guarda;
   - el ajuste por músculo (`MinimalMuscleSlider` dentro de `ReadinessAdjustCard`) plegado en un `KpknModule` con su cabecera
     «Ajustar músculos» y `ChevronDown`;
   - **las molestias previas** como `KpknChip` (multi-selección), como hoy;
   - **la voz:** una fila de 56 dp con `KpknSwitch` («Voz durante la sesión») y su modo en `caption`. La info del modo
     (`VoiceModeInfoCard`) pasa a `KpknModule` compacto, sin `Card`;
   - al pie, `KpknPrimaryPill` a todo el ancho con la descripción «Confirmar y entrenar» (la buscan las pruebas) y el texto
     «Empezar»;
   - fuera `Slider`, `Switch`, `Card`, `Surface` con color, `Icons.*`, `Color(0x…)` y tamaños sueltos.
2. **Textos:** tipo frase, «…» de un carácter, «%» con espacio fino o como lo pinten las piezas de RINGS. Antes de cambiar un texto,
   Grep en `src/androidTest` y en `src/test`.
3. **Lo que queda sin uso:** bórralo con Grep en todo `app/src`. `AdjustableRingCompact` y `MinimalMuscleSlider` se quedan si los
   usa alguien más (el asistente del alta los usa).

## Pruebas
- **Siguen en verde:**
  - las JVM de `screens/workout/` y `domain/workout/`;
  - las de la preparación y las anclas de RINGS (`*Readiness*`, `*Wellbeing*`, `*Auge*`);
  - las del asistente que usan `AdjustableRingCompact` (`*SetupRings*`);
  - la compilación de `androidTest`.
- **Robolectric** `WorkoutReadinessContentTest`, con el CUERPO de la hoja montado suelto:
  - ajustar Energía llama con el valor;
  - marcar una molestia llama con ella;
  - el interruptor de voz llama con el valor;
  - «Empezar» (descripción «Confirmar y entrenar») llama a empezar.
- **Hoja de render** `render/WorkoutReadinessRenderSheetTest`:
  - la hoja con los tres canales;
  - con los músculos desplegados;
  - con dos molestias y la voz activa;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/ls5a`.
  - Mira cada PNG con Read y critica con el listón del usuario: es lo primero que se ve al entrenar. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls5a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*Readiness*" --tests "*Wellbeing*" --tests "*SetupRings*" > /c/kw/logs/screens-ls5a-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(workout): preparación antes de entrenar con la torre de los RINGS, molestias y voz en piezas KPKN (LS5a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lógica de la preparación (anclas V2, `mergeWellbeingForSave`, molestias, voz) y del VM. Lo que falte, AL FINAL del VM.
- La serie, el descanso, la ruta, el resumen y las hojas (LS2b, LS3, LS4, LS6 y LS7), y el calentamiento, la movilidad y el cardio
  en vivo (LS5b).
- Los medios y el grabador de la técnica (`SetCardExerciseMediaBack`, ola VT).
- `MainActivity.kt` y `screens/sessioneditor/`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Textos cambiados y pruebas actualizadas.
- Pruebas: número y resultado.
- PNG y crítica.
