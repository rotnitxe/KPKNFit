# H3f · Importador: reconocer un programa que KPKN ya tiene y ofrecer su versión (2026-10-11)

Worktree y rama: los que diga el mensaje de lanzamiento (sale de `feat/wizard-v3` con todo el importador integrado). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H1.md`, `H2a.md`, `H2c.md` y `H3a.md`.
Tareas Gradle `...Base...`. Español neutro, sin género gramatical.

## Decisión de la persona usuaria
**«Ofrecer la versión de KPKN».** Muchas hojas de la comunidad son calculadoras de programas que KPKN ya tiene, completos y con progresión: nSuns, GZCLP, Smolov, Candito, Sheiko, Texas Method, PHUL, PHAT, PPL, Madcow, Juggernaut… Cuando el archivo importado es uno de ellos, un aviso lo dice y la persona elige:
- **«Usar el de KPKN»**;
- **«Seguir con mi archivo»**, que es lo de hoy.

Nada se cambia sin que la persona lo elija.

## 1. Reconocedor (nuevo `domain/training/importer/KnownProgramRecognizer.kt`, puro)
- **Firma:** `recognize(title: String, sheetNames: List<String>, firstTexts: List<String>): KnownProgramMatch?`.
  - `firstTexts` son las celdas de texto de las primeras 10 filas no vacías de cada hoja.
  - Devuelve `KnownProgramMatch(planId: String, aliasMatched: String)`.
- **Tabla de alias:** frase normalizada → id del catálogo de KPKN. Los ids son los de `data/programs/PlanEditorialTable.kt` (p. ej. `protocol:gzclp`, `protocol:texas-method-3d`).
  - **Busca los ids reales** de, al menos: nSuns 5/3/1, GZCLP, Smolov, Smolov Jr, Candito 6 semanas, Sheiko 29–32, Texas Method 3 y 4 días, PHUL, PHAT, PPL 6 días, Madcow 5×5, Juggernaut 2.0, Calgary Barbell 16, TSA 9 semanas, Cube Method, Westside, Coan-Philippi, GZCL Jacked & Tan 2.0, The Rippler y UHF 9.
  - Solo entran planes que existan y se ofrezcan. Usa `PersonalizedPlanCatalog.find(id)` en la prueba para comprobarlo.
- **Reglas:**
  - las frases cuentan como palabras completas tras normalizar;
  - gana la más específica: «smolov jr» o «smolov junior» antes que «smolov», y «texas method 4 day» o «4 días» antes que el de 3 días;
  - «PPL» solo no basta: hace falta «reddit ppl» o «metallicadpa».
  - **Prioridad de fuentes:** el título manda; luego los nombres de hoja; luego los textos. Con dos planes distintos al mismo nivel, ninguno (null).
- **Pruebas** (`KnownProgramRecognizerTest`):
  - 20 títulos reales de programas (nombres de programa, no contenido), cada uno con su plan;
  - 5 que no deben reconocerse («Mi rutina», «Push Pull Legs», «Plantilla semanal», «Full body 3 días», «PPL»);
  - todos los ids de la tabla existen en el catálogo.

## 2. Estado
- Los dos ViewModels de importación guardan el resultado al leer. El reconocedor recibe el título del archivo, los nombres de hoja y los textos iniciales:
  - en el alta, `SetupWizardDraft.importKnownPlanId: String?` y `importKnownDismissed: Boolean = false`, serializables y con valor por defecto;
  - en Programas → Importar, en `ImportProgramState`.
- `clearImport` y cada lectura nueva lo reinician.

## 3. Aviso (nuevo `ImportKnownProgramNotice.kt`; arriba de la vista previa, en las dos pantallas)
- **Cuándo:** solo si hay plan reconocido y no se descartó.
- **Contenido:**
  - una línea en `controlLabel`: «Parece «<nombre del plan en KPKN>».»;
  - debajo, en `note`: «KPKN ya lo tiene, completo y con su progresión, adaptado a tus datos.»;
  - las acciones «Usar el de KPKN» (`import-known-use`) y «Seguir con mi archivo» (`import-known-dismiss`).
- **Aspecto:** el vidrio tenue del paquete (`importGlassPanel`), con el acento `SymbolPalette.ok` en un trazo fino a la izquierda; sin tarjeta opaca.
- **«Seguir con mi archivo»:** marca el descarte, que se guarda, y el aviso desaparece.

## 4. «Usar el de KPKN»
- **Alta** (`SetupWizardViewModel`, función nueva al final del bloque de importación):
  - `useKnownProgram(planId)`:
    - pone `programSource = ProgramSource.RECOMMEND`;
    - aplica `draft.withPreselectedPlan(planId)`, la misma que usa la preselección de la biblioteca (`applyPreselection`);
    - quita lo importado, igual que `clearImport`;
    - lleva el cursor al paso del plan, con la misma navegación que «Editar» de un paso (`editStep`).
  - Si `withPreselectedPlan` devuelve null (el plan no encaja con el borrador), el aviso cambia a «No podemos ofrecerte «X» con tus respuestas.» y la importación sigue como estaba.
  - **Prueba de VM:** el borrador queda en RECOMMEND con el plan elegido, sin importación, y la ruta llega a PLAN.
- **App** (Programas → Importar):
  - `ImportProgramScreen` gana `onUseCatalogProgram: (planId: String) -> Unit`;
  - en `MainActivity.kt`, solo el cambio mínimo en la llamada que ya existe (`composable(KpknRoute.ImportProgram.route)`): `onUseCatalogProgram = { planId -> openLibraryPlanInWizard(navController, planId) }`. Es la misma función que usa la biblioteca para «Configurar este plan».
  - No toques nada más de `MainActivity.kt`.

## 5. Banco de pruebas (código de prueba)
- `ImportCorpusReporter` gana la columna «Plantilla» (el plan reconocido o «-») y, en el resumen, «Archivos reconocidos como plantilla de KPKN: N», con la lista agrupada por plan.
- Corre el informe de la colección (`KPKN_IMPORT_CORPUS=C:/Users/valen/Desktop/KPKN_Programas_Recoleccion_2026-10-11/01_originales`, `KPKN_IMPORT_REPORT=C:/kw/import-corpus-report-coleccion-h3f`) y pon en tu informe el recuento por plan y 10 ejemplos.

## Pruebas
- `KnownProgramRecognizerTest`.
- **Robolectric** del aviso: sale con plan; «Seguir con mi archivo» lo quita; «Usar el de KPKN» llama a la acción con el id.
- La prueba de VM del alta.
- Las pruebas del importador que ya existían siguen en verde.

## Comando
`bash /c/kw/tools/gradle_slot.sh <tu-worktree>/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*KnownProgram*" --tests "*ImportProgramScreen*" --tests "*ProgramImportStep*" --tests "*ImportActivation*" --tests "*ImportCorpusReporter*" --tests "*ImportWarnings*" --tests "*ImportEdits*"`

## Commit
- Por rutas, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.
- Nada de los corpus en el repo.

## Prohibido
- El analizador, el lector, el emparejador y el editor.
- El catálogo de planes (solo se lee).
- `MainActivity.kt`, más allá de la línea del punto 4.
- Push.

## Informe
- Firmas nuevas.
- La tabla de alias con sus ids.
- Pruebas: número y resultado.
- El recuento de la colección.
