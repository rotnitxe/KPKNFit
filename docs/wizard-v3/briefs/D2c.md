# D2c · PLAN con los tres programas propios por perfil, carrusel para los generales y fases en el detalle (punto 12, UI)

Worktree indicado en tu encargo, rama `v3/d2c` (sale de `feat/wizard-v3` con D1, D2a y D2b integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md`, del plan «Punto 12», y los briefs `D1.md` y `D2b.md`.

## Contexto (verificado)
- El barrido de PLAN: `SetupWizardViewModel.kt` ~2680-2745; para un perfil con fuentes GENERATED o GENERATED_DISCIPLINE solo entra
  `collectGenerated(profile)` (UNA entrada); con GENERATED_AND_CATALOG, el generado + los de autor (`collectViable`). La rutina base se evalúa con
  `generatedRoutineFor` (~2978).
- La UI: `screens/onboarding/entreno/EntrenoPlanStep.kt` (`GeneralReveal` ~180: una sola portada para los generales; las disciplinas usan el
  carrusel `design/entreno/plan/PlanCarousel.kt`), modelos en `entreno/EntrenoPlanModels.kt` y revelados en `SetupPlanReveals.kt`.
- Las 20 variantes existen y se materializan con el periodizador (D2b); el kicker de estructura es de D1.

## Qué hay que hacer
1. **Barrido**: `collectGenerated(profile)` devuelve las tres entradas del perfil (simple, `:blocks`, `:periodized`), en ese orden, evaluadas con la
   MISMA rutina base (una sola generación; las tres comparten veredicto de material y tiempo).
2. **Orden y oferta**:
   - Generales (Fuerza y masa, Fuerza y cardio, Funcional): carrusel de 3 portadas (el `GeneralReveal` de una portada pasa a usar `PlanCarousel`).
   - Disciplinas con autores (Powerlifting, Powerbuilding, Culturismo): primero sus 3 propios, luego los de autor; si hay más de 6 en total, los
     6 primeros y una acción de texto «Ver más programas (N)».
   - Disciplinas sin autores: sus 3 propios.
   - Recomendado (el que sale centrado y marcado con «Para ti» en la portada, sin cambiar la selección hasta que se toque): principiante y quien
     vuelve → el simple; intermedio → el de bloques; avanzado → el periodizado.
   - Si en tu rama existe `draft.competition == WITH_DATE` (C4a), el simple no se ofrece (no tiene pico); si no existe, deja `// TODO(C4b)` de una
     línea en ese punto.
   - Free: si el ViewModel conoce el nivel de plan (busca cómo llega `PlanLevel` al alta; el coordinador lo recibe como `accessLevel`), las variantes
     de varias semanas se muestran con la etiqueta «Pro» y, al elegirlas, un aviso «Este programa es de KPKN Pro.» en lugar de seleccionarlas. Si
     el ViewModel no lo conoce, no lo inventes: anótalo en el informe.
3. **Detalle con fases** (`design/entreno/plan/PlanDetailOverlay.kt`): para programas de varias semanas, encima de «Tu semana», una franja de fases
   proporcional a sus semanas («Acumulación · 3 sem», «Intensificación · 2 sem», «Descarga · 1 sem»), colores de fase con tinta y acentos de la
   paleta (sin resplandor), y «Tu semana» muestra la primera semana. Datos de las fases: los bloques del programa materializado (`Block.goal` y sus
   semanas). Accesible como lista («Fase 1 de 3: Acumulación, 3 semanas»).
4. La portada de cada variante lleva el kicker de D1 («6 SEMANAS · 2 BLOQUES»…). Nombres de D2b.

## Archivos permitidos
`SetupWizardViewModel.kt` (solo `collectGenerated` y lo imprescindible del barrido), `screens/onboarding/{SetupPlanReveals}.kt`,
`entreno/{EntrenoPlanStep, EntrenoPlanModels}.kt`, `design/entreno/plan/{PlanCarousel, PlanDetailOverlay, PlanCover}.kt`, y pruebas.

## Pruebas
- `EntrenoPlanStepTest` (amplía): un general ve 3 portadas; Powerlifting ve primero sus 3 propios y luego los de autor; «Ver más» aparece con más
  de 6; el recomendado depende del nivel.
- Nueva `PlanPhasesStripTest` (Compose): un programa de 6 semanas muestra 3 fases con sus semanas; uno simple no muestra franja.
- `SetupWizardEntrenoPlanTest` solo si tu cambio la afecta (con T1 ya integrado debería pasar entera).
- Filtro: `*EntrenoPlanStep*`, `*PlanPhasesStrip*`, `*SetupPlanReveals*`, `*PlanCarousel*`. Antes, `:app:compileDebugKotlin`.
