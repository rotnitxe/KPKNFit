# B3a · Fuera «Trapecio» de los músculos y figura de press banca corregida (puntos 9 y 10)

Worktree indicado en tu encargo, rama `v3/b3a`. Lee antes `docs/wizard-v3/BRIEF_COMUN.md` y, del plan, «Punto 9» y «Punto 10».

## 1. Quitar «Trapecio» de «¿Qué músculos priorizas?»
- Hoy hay 12 opciones (`domain/onboarding/EntrenoContracts.kt:83-96`, enum `MuscleSymbol`, `@Serializable`), con `TRAPS` «Trapecio».
  El usuario pide quitarla porque «va incluido en Espalda».
- Referencias a `TRAPS` como OPCIÓN (no confundir con `RoutinePattern.TRAPS` ni `Muscles.TRAPS` del generador, ni con AUGE, que no se tocan):
  `domain/onboarding/MuscleSymbols.kt:29` (nombre canónico), `:72` y `:75` (sugerencias de Halterofilia y Strongman),
  `screens/onboarding/design/entreno/MuscleSymbols.kt:111` (orden de la cuadrícula), `MuscleArt.kt:77-80` (spec), `MuscleArtPaths.kt:178-200`
  (trazos), `domain/training/generator/GeneratorTypes.kt:166-227` (`when` sobre `MuscleSymbol`; `PriorityTargets.of` 222-226), y las pruebas
  `MuscleSymbolsTest`, `MuscleArtTest`, `RoutineGeneratorBehaviorTest:135`. Búscalas todas con Grep antes de empezar.
- Cambios:
  1. Retira `TRAPS` del enum de opciones. **Compatibilidad**: los borradores guardados pueden traer `"TRAPS"`. Sigue el precedente de la
     comba en `screens/onboarding/SetupDraftCompatibility.kt` (`RETIRED_JUMP_ROPE_KEY`, ~156): al abrir un borrador, `TRAPS` en
     `priorityMuscles`/`orderPriorities` se convierte en `BACK` (sin duplicar si ya estaba). Asegúrate de que el decodificador no reviente con
     el valor viejo antes de esa migración (si el enum se decodifica directo, usa un serializador tolerante o la misma técnica que la comba:
     léelo y compruébalo con una prueba que decodifique un JSON real con `"TRAPS"`).
  2. En el generador, la prioridad `BACK` pasa a cubrir también el trapecio (`PriorityTargets.of(BACK)` incluye los objetivos que tenía
     `TRAPS`), para que Halterofilia y Strongman no pierdan ese trabajo.
  3. Sugerencias de Halterofilia y Strongman: donde estaba `TRAPS`, `BACK` (sin duplicar).
  4. La cuadrícula queda en 11 símbolos (3 columnas: 3+3+3+2; la última fila centrada si el código de la cuadrícula ya lo permite; si no,
     alineada como hoy). Borra el spec y los trazos de `TRAPS` que queden sin uso.
## 2. Figura de press banca (paso de marcas)
- `screens/onboarding/design/entreno/LiftPoses.kt:152-160` (`bench`) pone la cadera en (30; BENCH_Y − 2,2) y los pies en x = 16 y 18,5 sobre
  el suelo, y el banco se dibuja de x = 20 a 68 en `LiftMarksPicker.kt:659-662`: los muslos bajan verticales y **atraviesan el banco** (se lee
  como si la persona estuviera sentada o «acostada de frente»).
- Corrección: el banco termina en la cadera (extremo izquierdo en x ≈ cadera − 2) y los pies van delante del extremo, en el suelo, a
  x ≈ cadera − 15 (el cercano) y ≈ cadera − 13 (el lejano), con la rodilla **por encima** de la línea cadera‑pie (elige el signo de la
  cinemática inversa que la deje arriba). El muslo sale casi horizontal por encima del extremo del banco y la espinilla baja al suelo.
  Ajusta el dibujo del banco en `LiftMarksPicker.kt` para que coincida (el banco va de cadera − 2 hacia la cabeza; los postes, dentro).
- Pruebas: actualiza `FigurePosesTest` (`:101-107` exige la cadera 0–4 u sobre el banco: debe seguir) y añade: ningún segmento de pierna
  (cadera‑rodilla y rodilla‑pie) cruza el segmento de la tabla del banco en ninguna fase `u` de 0 a 1; los pies siguen en el suelo.
- Hoja de render: usa la utilidad `src/test/java/com/example/kpkn/render/SymbolRenderSheet.kt` si ya existe en tu rama; si no existe,
  dibuja tu propia tira con el mismo patrón de `WizardRenderSheetTest` (solo para revisar, en `KPKN_RENDER_DIR=C:/kw/shots/v3/b3a`). Mira el
  PNG de la banca (fases 0; 0,4; 1) y de la cuadrícula de músculos.

## Archivos permitidos
Los citados arriba (opción de músculos, sugerencias, cuadrícula, specs/trazos de músculos, `GeneratorTypes.kt` solo `PriorityTargets`/`when`
de `MuscleSymbol`, `SetupDraftCompatibility.kt` solo la migración, `LiftPoses.kt` solo `bench`, `LiftMarksPicker.kt` solo el dibujo del banco)
y sus pruebas.

## Pruebas
Filtro: `*MuscleSymbols*`, `*MuscleArt*`, `*MuscleGrid*`, `*FigurePoses*`, `*SetupDraftCompatibility*`, `*RoutineGeneratorBehavior*` y las nuevas.
Antes, `:app:compileBaseDebugKotlin`.
