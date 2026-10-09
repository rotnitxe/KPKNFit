# C3a · Ruta de «Tengo experiencia»: Entreno termina en Movilidad y «¿Cómo quieres tu programa?» (punto 11, primera parte)

Worktree indicado en tu encargo, rama `v3/c3a` (sale de `feat/wizard-v3` con C1 integrado). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`
y, del plan, «Punto 11». La importación (lector, vista previa y constructor) es de C3b y C3c: aquí solo la ruta, el paso de elección y
su símbolo.

## Contexto (verificado)
- Ruta de Entreno en `domain/onboarding/SetupStepGraph.kt:354-376` (`nodes`): tras `VOLUME_MOBILITY` vienen `CAPABILITIES`, `PRIORITIES`,
  (C1: `AVOIDED_EXERCISES`, `ARM_STYLE`, `TRAINING_VARIANTS`), `TRAINING_MAX`, `PLAN`, `WEEK_LAYOUT` y el hito.
- «Lo haré más adelante» ya existe: `SetupProgramRoute.LATER` (`screens/onboarding/SetupWizardModels.kt:82`), `vm.deferProgramUntilLater()`
  (`SetupWizardViewModel.kt:453`) y `hasWeekLayout` falso; el alta sin programa ya funciona. Léelo entero antes de reutilizarlo.
- Experiencia: `SetupExperience.ADVANCED` = «Tengo experiencia».

## 1. Datos y ruta
- `enum class ProgramSource { RECOMMEND, LATER, IMPORT }` (`@Serializable`, junto a los enums de C1 en `EntrenoContracts.kt`) y en el
  borrador `programSource: ProgramSource? = null`.
- Paso nuevo `SetupStepId.PROGRAM_SOURCE` (bloque Entreno), solo con experiencia ADVANCED, justo después de `VOLUME_MOBILITY`.
  `SetupStepContext.asksProgramSource` y `programSource` salen del borrador en `stepContext()`.
- Según la respuesta:
  - RECOMMEND (o sin responder): la ruta de siempre a partir de `CAPABILITIES`.
  - LATER: tras `PROGRAM_SOURCE` va directamente el hito (sin capacidades, prioridades, punto de partida, PLAN ni semana). Confirmar el paso
    con LATER aplica lo mismo que `deferProgramUntilLater()` (reutilízalo, no lo copies).
  - IMPORT: tras `PROGRAM_SOURCE` va `SetupStepId.PROGRAM_IMPORT` (paso nuevo, lo pinta C3c), luego `WEEK_LAYOUT` y el hito.
- Cambiar de LATER o IMPORT a RECOMMEND deja la ruta de siempre con sus respuestas intactas (nada se borra; los pasos que aparecen sin
  respuesta se visitan, lo hace la navegación «volver y regresar» de A1). Volver a RECOMMEND desde LATER deshace el `LATER` de
  `programRoute`.
- No subas `SetupStepGraph.REVISION`: C1 ya la subió a 5 y no ha salido ninguna versión entre medias. Comprueba con una prueba que un
  borrador avanzado viejo (sin `programSource`) sigue en la ruta de siempre.
- Mientras C3c no exista, la opción IMPORT queda oculta con `ProgramSourceOptions.IMPORT_ENABLED = false` (constante interna); C3c la
  enciende. Con la opción oculta, `PROGRAM_IMPORT` nunca entra en la ruta.

## 2. Definición, textos y resumen
- `SetupStepDefinitions`: `PROGRAM_SOURCE` título «¿Cómo quieres tu programa?», subtítulo «Con tu experiencia puedes elegir: te
  recomendamos uno, lo armas tú o traes el tuyo.»; `PROGRAM_IMPORT` título «Importa tu programa», subtítulo «Elige un archivo o pega el
  texto. Revisarás cada ejercicio antes de usarlo.».
- Resúmenes (`SetupStepSummaries`, `when` exhaustivo): «Te recomendamos uno», «Lo armarás más adelante», «Importado: <título>» (C3c pone el
  título; mientras, «Importado»).
- Validación: `PROGRAM_SOURCE` necesita una opción; `PROGRAM_IMPORT` necesita un programa importado (C3c añade el dato; aquí deja la regla
  preparada como `false` con un `TODO(C3c)` de una línea).

## 3. UI del paso `PROGRAM_SOURCE` (`entreno/EntrenoProgramSourceStep.kt` + rama en `SetupTrainingSteps.kt`)
- Tres símbolos de elección única en una fila (`SymbolCell`, `Role.RadioButton`; patrón de `design/entreno/EquipmentSymbols.kt`; reloj compartido
  que solo corre con uno elegido, visible y en primer plano), arte nuevo en `design/entreno/ProgramSourceArts.kt` (lienzo 64 × 64):
  - «Que KPKN me recomiende uno»: la torre de KPKN (tres elipses apiladas: abajo columna `#8FB2FF`, medio músculo `#F49A6E`, arriba energía
    `#F7CF73`; sin resplandor) que se enciende de abajo arriba (un arco de progreso por elipse, 1,8 s) y queda llena.
  - «Lo armaré yo más adelante»: una hoja en blanco inclinada y un lápiz que traza dos renglones y se detiene.
  - «Importar mi programa»: una hoja con cuadrícula de hoja de cálculo que se desliza dentro del contorno de un teléfono (solo si
    `IMPORT_ENABLED`).
- Nota bajo los símbolos (`EntrenoStepNote`) según la elección: RECOMMEND «Seguimos con unas preguntas y te proponemos programas para tu
  disciplina.»; LATER «Podrás crear tu programa desde Programas cuando quieras.»; IMPORT «Excel (.xlsx), CSV o texto pegado.».
- Escribe SOLO con un setter nuevo `vm.setProgramSource(source)` al FINAL de `SetupWizardViewModel.kt` (vía `mutateDraft`).

## Archivos permitidos
`domain/onboarding/{EntrenoContracts, SetupStepGraph, SetupStepDefinitions}.kt`, `screens/onboarding/{SetupWizardModels, SetupStepAnswers,
SetupStepSummaries, SetupTrainingSteps}.kt`, `SetupWizardViewModel.kt` (solo el setter al final y, si hace falta, la llamada a
`deferProgramUntilLater` al confirmar), nuevos `entreno/EntrenoProgramSourceStep.kt` y `design/entreno/ProgramSourceArts.kt`, y pruebas.

## Pruebas
- `SetupStepGraphTest`: avanzado con RECOMMEND → ruta de siempre; con LATER → `VOLUME_MOBILITY, PROGRAM_SOURCE, MILESTONE_TRAINING`; con
  IMPORT (encendido en la prueba) → `PROGRAM_SOURCE, PROGRAM_IMPORT, WEEK_LAYOUT, MILESTONE_TRAINING`; intermedio no ve `PROGRAM_SOURCE`.
- Nueva `ProgramSourceStepTest` (Compose): tres símbolos (dos con IMPORT apagado), elección única, el setter recibe la opción, la nota cambia.
- Robolectric del alta con LATER: se activa sin programa (reutiliza el patrón de la prueba de «lo haré más adelante» que ya exista; búscala).
- Hoja de render (`KPKN_RENDER_DIR=C:/kw/shots/v3/c3a`): los tres símbolos en reposo y en 4 fotogramas.
- Filtro: `*SetupStepGraph*`, `*ProgramSource*`, `*SetupStepDefinitions*`, `*SetupStepSummaries*`, `*SetupStepCopyRules*` y la prueba de LATER.
  Antes, `:app:compileDebugKotlin`.
