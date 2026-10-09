# C1c · UI de «evitar», «tu cardio», «estilo de mesa» y «cuántas te salen seguidas» (punto 10, tercera parte)

Worktree indicado en tu encargo, rama `v3/c1c` (sale de `feat/wizard-v3` con C1 y C1b integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md`, del plan «Punto 10» y `docs/wizard-v3/briefs/C1.md` (datos, setters, textos y validación ya existen).

## Lo que ya existe (léelo antes de tocar)
- Enganche: `screens/onboarding/SetupTrainingSteps.kt:53-78`; un archivo `entreno/Entreno<Paso>Step.kt` por paso, firma `(state, vm)`.
- Kit de símbolos: `design/entreno/EntrenoSymbolKit.kt` (`SymbolArt` ~84, `EquipmentArt` 64 × 64 ~103, `SymbolPen`, `SymbolPalette`,
  `SymbolStroke`, `runPose` ~432, `dumbbell` ~467, `seg`/`smooth`/`riseHoldFall`), `design/entreno/EntrenoSymbolRuntime.kt`
  (`rememberSymbolClock`, `SymbolCell` ~244 con `Role.Checkbox` y `symbolDescription`, `onSymbolsVisible`,
  `rememberEntrenoForeground`). Cuadrícula de referencia: `design/entreno/EquipmentSymbols.kt:59-` (3 columnas, 4 si es ancho, un
  reloj que solo corre con algo elegido, visible y en primer plano). Copia ese patrón; no inventes otro.
- Pictogramas de patrón: `design/entreno/plan/ExercisePatterns.kt` (`patternArt(ExercisePattern)` ~101: sentadilla, bisagra, empujes,
  tracción, remo, zancada…; son estáticos).
- Peso corporal: `entreno/EntrenoCapabilitiesStep.kt` + `design/entreno/CapabilitySymbols.kt` (figuras animadas por ejercicio).
- Regla de enteros `design/entreno/CountRuler.kt` (de C1b).
- De C1: `AvoidGroup`, `CardioBase`, `ArmStyle`, `draft.avoidedExercises/cardioBase/armStyle/maxReps` y los setters
  (`toggleAvoided`/`setAvoided`, `setCardioBase`, `setArmStyle`, `setMaxReps`; busca los nombres exactos).

**API real que dejó C1** (integrado): setters `vm.toggleAvoided(group)`, `vm.setAvoided(set)`, `vm.setCardioBase(base)`, `vm.setArmStyle(style)`,
`vm.setMaxReps(skill, reps)`; `draft.capabilitiesBodyweightRoute()` y `draft.capabilitiesRequired()`; `wizardPageCopy(..., bodyweightRoute,
pullUpsRequired)` (por defecto `false`). **Autorizado además:** en `SetupWizardHost.kt` (~379), pasar `bodyweightRoute` y `pullUpsRequired` a
`wizardPageCopy` (sin eso el título de CAPABILITIES no cambia). `JUMPS` y `RUNNING` no tienen definiciones de catálogo: su símbolo existe igual (lo
consume C2 por patrón).

## 1. `AVOIDED_EXERCISES` · `entreno/EntrenoAvoidStep.kt` (Culturismo)
- Cuadrícula de 10 símbolos + «Ninguno» (exclusivo: elegirlo vacía el conjunto; elegir otro lo apaga). Arte por grupo: reutiliza
  `patternArt` donde encaje (sentadilla con barra → SQUAT, peso muerto → HINGE, press banca → PRESS_HORIZONTAL, press militar →
  PRESS_VERTICAL, dominadas → PULL_VERTICAL, zancadas → LUNGE) y dibuja cuatro nuevos en un archivo nuevo
  `design/entreno/StartingPointArts.kt`: fondos (figura en paralelas, codos a 90°), hip thrust (espalda en banco, cadera arriba, barra en
  la cadera), saltos (figura en el aire sobre una línea de suelo con dos marcas de impacto) y correr (`runPose`). «Ninguno»: mano
  abierta o un círculo vacío, tenue.
- Seleccionado = **evitar**: además del acento y la marca de «hecho» de siempre, una diagonal que tacha el pictograma se dibuja de
  arriba‑izquierda a abajo‑derecha en 260 ms (`SymbolStroke.LINE`, `SymbolPalette.musculo`) y se queda. Con movimiento reducido,
  la diagonal ya dibujada. Descripción accesible: «Peso muerto, evitar» / «Peso muerto, sin evitar».
- Nota (`EntrenoStepNote`): «Si un ejercicio no tiene sustituto con tu material, te lo diremos.»

## 2. `CARDIO_BASE` · `entreno/EntrenoCardioBaseStep.kt` (Fuerza y cardio, Funcional)
- Cuatro símbolos de elección única (`Role.RadioButton`), acento `SymbolPalette.energia`, en una fila de 4 (2 × 2 si no caben a 360 dp):
  - «Me cuesta 10 min»: figura caminando (`runPose` a media zancada y ritmo lento) + esfera pequeña con 10 min marcados.
  - «Aguanto 20–30 min»: trote + esfera con 25 min.
  - «Corro 45 min»: carrera + esfera con 45 min.
  - «Hago series rápidas»: sprint inclinado + tres trazos de velocidad que entran y salen a ritmo de series.
  - La esfera: círculo r = 7 u arriba a la derecha con un arco de progreso que se llena en bucle hasta su minuto (no texto).
- Tocar el elegido no lo quita. Nota: «Con esto ajustamos la intensidad de tus sesiones de cardio.»

## 3. `ARM_STYLE` · `entreno/EntrenoArmStyleStep.kt` (Armwrestling)
- Cuatro símbolos de elección única, acento `SymbolPalette.musculo`. Escena común (64 × 64): tabla en y = 50 con el cojín del codo
  en x = 32; el antebrazo propio (trazo `BODY`) sube del codo; la mano propia es un círculo r = 4,5; la mano rival, en tinta tenue.
  - Top roll: el antebrazo se inclina hacia atrás 20° y la mano trepa por encima de la rival (bucle: la muñeca rueda hacia atrás).
  - Hook: la muñeca se curva hacia la persona (mano «en copa») y todo el brazo tira hacia el cuerpo.
  - Press: el hombro y el torso pasan por encima del brazo y empujan la mano rival hacia la tabla, de lado.
  - Aún no lo sé: las dos manos agarradas al centro con un vaivén pequeño.
- Nota: «Así damos más trabajo a lo que pide tu estilo.»

## 4. `CAPABILITIES` con repeticiones (Calistenia o ruta solo de peso corporal)
- En `EntrenoCapabilitiesStep`: si la ruta es la de peso corporal (usa el MISMO criterio que C1 usó para el título «¿Cuántas te salen
  seguidas?»; si es privado, reutilízalo a través de una función `internal`), cada ejercicio de `draft.capabilitySkills()` es una fila:
  su figura animada de `CapabilitySymbols` (72 dp, solo la fila tocada corre) + nombre + `CountRuler(0..30, «repeticiones»)`.
  Dominadas con la etiqueta «Obligatoria» cuando C1 la exige; las demás con «No lo sé» (escribe `null`), como las marcas.
- Fuera de esa ruta, el control de niveles de hoy, sin cambios.
- Escribe SOLO con `vm.setMaxReps(skill, reps)` (C1 deriva el nivel).

## Archivos permitidos
`SetupTrainingSteps.kt` (solo las ramas de estos pasos), los cuatro `entreno/*Step.kt` citados, nuevo `design/entreno/StartingPointArts.kt`,
`design/entreno/CapabilitySymbols.kt` (solo si necesitas exponer la figura) y pruebas. Nada de dominio ni del ViewModel.

## Pruebas
- Nueva `StartingPointStepsTest` (Compose): «Ninguno» es exclusivo; tocar «Saltos» llama al setter con `JUMPS`; cardio y estilo son de
  elección única; la regla de dominadas escribe `setMaxReps(PULL_UP, n)`; fuera de la ruta de peso corporal siguen los niveles.
- Nueva `StartingPointArtsTest`: todos los trazos dentro de 64 × 64 en 8 fases del bucle (como `EntrenoSymbolMathTest`).
- Hoja de render (`KPKN_RENDER_DIR=C:/kw/shots/v3/c1c`, con `SymbolRenderSheet`): los 15 símbolos nuevos en reposo y en 4 fotogramas.
  Revisa a 60 dp: que «Top roll», «Hook» y «Press» se distingan sin leer la etiqueta.
- Filtro: `*StartingPoint*`, `*CapabilitySymbols*`, `*EntrenoStepsSmoke*`, `*EntrenoAccessibility*`. Antes, `:app:compileDebugKotlin`.
