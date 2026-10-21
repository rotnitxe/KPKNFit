# LR1c2 · Sesión en vivo ORIGINAL: refinado de la técnica, el tipo de serie y las acciones de serie (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`, `9ccf4831c`, con LR1a integrado). Lee ENTERO
`docs/screens-v1/briefs/LR1_COMUN.md` y lo que pide leer.

## Tus pantallas y archivos (solo estos)
Todos bajo `screens/workout/components/`.

| Mapa | Lo que ve la persona | Archivo |
|---|---|---|
| C1 | Reverso de técnica de la tarjeta: drop-sets y rest-pause, filas de drops (máx. 5), chip «Añadir» y nota | `SetCardTechniqueBack.kt` (SOLO su contenido: el giro es de la ola VT) |
| C2 | Panel de técnica guiada y «¿Registrar solo la serie principal?» | `GuidedTechniquePanel.kt` |
| C4 | «Ajuste por estado actual» (Conservador / Equilibrado / Agresivo) | `SetAdjustmentOverlay.kt` |
| C5, B9 | Acciones de la serie (convertir a Dropset o Rest-Pause, eliminar, omitir) y alcance «Esta serie / Restantes / Todas», más `GodModePlusCard`, `GodModeBatchBar` y `GodModeRoadmapBadge`, que siguen en naranja | `GodModeChrome.kt` |
| B10 | «Tipo de serie» (Normal, Dropset, Rest-Pause) | `SeriesTypeSheet.kt` |
| B11 | «Modo Ultrarrápido» | `UltraFastSheet.kt` |
| B12 | «Adelanto de volumen» | `VolumeAdvanceModal.kt` (el envoltorio `ProtectedWorkoutBottomSheet` no se toca) |

## Qué cambias (solo la piel)
1. **Reverso de técnica (C1).** Es el reverso de la tarjeta de LR1a: misma familia.
   - El fondo, en `module`.
   - Los conmutadores «Añadir drop-sets» / «Añadir rest-pause» (`SoftToggleChip`), como `KpknChip` o píldora: el activo en tinta.
   - Las filas de drops, con celdas `inset` de cifra (coma decimal).
   - El chip «Añadir» (`AddTechniqueChip`), como píldora con filete.
   - La nota, como `KpknTextField`.
   - `#8B1E1E` y `#2E2E2E`, a tokens (`danger` para quitar).
   - El giro, `cardFace` y `rotationY`, NO se tocan.
2. **Técnica guiada (C2).**
   - El `AlertDialog` CRUDO de Material (L169-183) pasa a `KpknAlertDialog`, con el mismo texto y los mismos botones. Hoy es el
     único diálogo de la sesión fuera del lenguaje de la app.
   - Sus campos, a `KpknTextField`, y sus TextButton, a `KpknTextAction`.
3. **Ajuste por estado (C4).**
   - Lo de dentro del `KpknSheet`: el Slider vestido como en LR1c1 (pista blanca al 8 %, progreso y pulgar en tinta).
   - Las tres zonas, con su nombre en Inter.
   - El semáforo de Material (`#4CAF50`, `#FFC107`, `#FF5252`), a `ok` / acento / `danger`.
   - «Cancelar», a `KpknSecondaryPill`, y «Aplicar», a `KpknPrimaryPill`.
4. **Acciones de serie y GodMode (C5, B9 y lo naranja).**
   - Superficies `module` / `inset` y la acción peligrosa en `danger`.
   - El botón blanco del diálogo de alcance (`KpknSheetWhiteButton`, compartido): NO lo cambies. Si su llamada admite un estilo,
     usa `KpknPrimaryPill` en tu lado.
   - `GodModePlusCard`, `GodModeBatchBar` y `GodModeRoadmapBadge` siguen en el naranja viejo: pásalos a la tinta y al `inset` de la
     tira de LR1a, porque se ven junto a ella.
5. **Tipo de serie (B10), Ultrarrápido (B11) y Adelanto de volumen (B12).** Lo de dentro de sus hojas:
   - FilterChip a `KpknChip`, OutlinedButton a `KpknSecondaryPill` y Button a `KpknPrimaryPill`;
   - los hex sueltos (13 líneas en `UltraFastSheet`, `#66BB6A`, `#26252C`, `#2A2A2A`…), a tokens;
   - las cifras de duración, en Syne tabular.

## Arreglo de lógica (un commit propio, con una prueba que falle sin él)
- **«Añadir» del 4.º y 5.º drop con coma** (`SetCardTechniqueBack.kt` L266): `dropRows.lastOrNull()?.weightText?.toDoubleOrNull()`
  da nulo con «62,5» y el drop nuevo sale sin peso. Patrón: `.replace(',', '.').toDoubleOrNull()`, como en L559 del mismo archivo.
- **Mira también `WorkoutTechniqueDraft.kt` L42** (`dropWeightText?.toDoubleOrNull()`). Si lee texto escrito por la persona, arréglalo
  en el mismo commit; si no, dilo en el informe.
- Commit: `fix(workout): el drop que se añade tras uno con coma hereda su peso (LR1c2)`.

## Hoja de render
`render/LiveSessionTechniqueRenderSheetTest`, a 360 dp, al 100 % y al 130 %. PNG en `C:/kw/shots/screens/lr1c2`. Incluye:
- el reverso de técnica con 3 drops (uno con «62,5»);
- la técnica guiada y su diálogo;
- el ajuste por estado;
- las acciones de serie;
- el tipo de serie;
- el ultrarrápido;
- el adelanto de volumen.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lr1c2 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*LiveSessionTechnique*" > /c/kw/logs/screens-lr1c2-<n>.log 2>&1`

`.dbg`: `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-lr1c2-dbg-<n>.log 2>&1`,
copiado a `C:/kw/shots/lr1c2/app-lr1c2-dbg.apk`.

## Commit
- `fix(workout): …` (arriba).
- `style(workout): reverso de técnica, técnica guiada y ajuste por estado con la piel nueva (LR1c2)`.
- `style(workout): acciones de serie, tipo de serie, ultrarrápido y adelanto de volumen con la piel nueva (LR1c2)`.
- `test(workout): hoja de render de la técnica y el tipo de serie (LR1c2)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto (archivo y composable).
- El arreglo de lógica, con su prueba.
- Los PNG, con tu comparación frente a la original.
- Pruebas: número y resultado.
- La ruta del `.dbg`.
