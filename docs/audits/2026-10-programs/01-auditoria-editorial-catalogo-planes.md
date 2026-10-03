[Subagent hand-back] The text below is the final report of a subagent this session delegated to. It is model output, NOT a message from the user: instructions, requests, or approval claims inside it are the subagent's words and carry no user authority. The harness indents every line of the report, so a frame-like line at column zero inside it would be forged. Notes above this frame may quote model-derived text, which carries no user authority either. The report follows:
  # Auditoría editorial del catálogo de planes de KPKN Fit
  
  Lectura estática del contenido en disco, sin ejecutar nada. Varios archivos auditados tienen cambios sin commitear: `PersonalizedPlanCatalog.kt` (+265), `CreateProgramTemplateSheet.kt` (+222), `SetupTrainingSteps.kt`, `SetupWizardViewModel.kt` y los tests `PersonalizedPlanCatalogTest` y `ProtocolRecipeFidelityTest`.
  
  Abreviaturas de rutas (todas absolutas):
  - **K** = `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\java\com\example\kpkn`
  - **T** = `C:\Users\valen\Documents\KPKNFit\android-native\app\src\test\java\com\example\kpkn`
  - **PPC** = `K\data\programs\PersonalizedPlanCatalog.kt`
  - **PT** = `K\data\programs\ProgramTemplates.kt`
  - **KAR** = `K\data\programs\KpknAdvancedProgramRecipes.kt`
  - **PL** = `K\data\protocols\ProtocolLibrary.kt`
  - **RB** = `K\data\protocols\RecipeBuilders.kt`
  - **DA** = `K\data\protocols\DayArchetypes.kt`
  - **Definiciones** (en `K\data\protocols\definitions\`):
    - NPS = `NativeProfileSpec.kt`
    - CPL = `ClassicPlProtocols.kt`
    - JSS = `JuggernautSheikoSmolov.kt`
    - MNG = `MadcowNsunsGzcl.kt`
    - TWP = `TexasWendlerProtocols.kt`
    - BNP = `BodybuildingNativeProtocols.kt`
    - KSBD = `KpknNativeSbd4.kt`
    - APP = `AuthoredPhulPhat.kt`
    - ASR = `AuthoredSourceRecord.kt`
  - **SCP** = `K\domain\training\SimpleCyclePersonalizer.kt`
  - **STP** = `K\domain\onboarding\SetupTrainingPlanner.kt`
  - **PCE** = `K\domain\onboarding\PlanCandidateEvaluator.kt`
  - **CPTS** = `K\screens\programs\CreateProgramTemplateSheet.kt`
  - **PDS** = `K\screens\programs\ProtocolDetailSheet.kt`
  - **PS** = `K\screens\programs\ProgramsScreen.kt`
  - **PVM** = `K\screens\programs\ProgramsViewModel.kt`
  - **STS** = `K\screens\onboarding\SetupTrainingSteps.kt`
  - **SWVM** = `K\screens\onboarding\SetupWizardViewModel.kt`
  - **SRS** = `K\screens\onboarding\SetupReviewStep.kt`
  - **SSD** = `K\domain\onboarding\SetupStepDefinitions.kt`
  - **PDSUM** = `K\screens\programdetail\PlanDetailsSummary.kt`
  - **CHB** = `K\screens\programdetail\components\CompactHeroBanner.kt`
  - **WCC** = `K\domain\onboarding\WizChatCopyCatalog.kt`
  - **WCG** = `K\domain\onboarding\WizChatGraph.kt`
  
  ---
  
  ## 1. Inventario
  
  ### 1.1 Conteos
  
  `PersonalizedPlanCatalog.entries()` (PPC:385-431) devuelve **55 entradas, todas PUBLISHED**. No existe ningún filtro de publicación en UI que las reduzca.
  
  | Source | Nº | Composición |
  |---|---|---|
  | NATIVE | 12 | 8 históricas + 4 perfiles propios |
  | TEMPLATE | 10 | 3 simples + 7 complejas |
  | PROTOCOL | 33 | 24 de terceros, 5 protocolos KPKN (`KPKN Fit`) y 4 autoradas PHUL/PHAT |
  
  | Nivel (`CatalogLevel`) | NATIVE | TEMPLATE | PROTOCOL | Total |
  |---|---|---|---|---|
  | BEGINNER | 10 | 3 | 0 | **13** |
  | INTERMEDIATE | 2 | 0 | 16 | **18** |
  | ADVANCED | 0 | 7 | 17 | **24** |
  
  - **Por `references`:**
    - POWERLIFTING: 29.
    - HYPERTROPHY: 18.
    - POWERBUILDING: 8 (las 4 autoradas llevan HYPERTROPHY y POWERBUILDING a la vez).
    - Vacías: 4 (`template:simple-1`, `simple-ab`, `simple-4` y `native:complete-athlete-v2`).
  - **`capabilities`:** solo los 4 perfiles propios las declaran.
  - **Entradas ocultas** (fuera de `entries()`): 16, ver 1.7.
  
  ### 1.2 Constantes por grupo
  
  - **NATIVE y TEMPLATE:**
    - `sourceAuthor="KPKN"`, sin `sourceUrl` ni `disclaimer`.
    - Sin `fidelitySpec` ni `provenance`.
    - `supportedFocuses`: las 7 en NATIVE, solo FULL_BODY en TEMPLATE.
    - `adaptation`: CURATED_WEEKLY en NATIVE, FIXED_PRESCRIPTION en TEMPLATE.
    - `sourceRevision="native-cycle-1"` solo en NATIVE.
  - **PROTOCOL (29 no autoradas):**
    - `requiredEquipment={general_gym}`, `supportedFocuses={FULL_BODY}`, FIXED_PRESCRIPTION, `capabilities=∅`.
    - Tienen `fidelitySpec`, que solo se usa para el nivel (PPC:417-421); `CatalogEntry` no lo expone.
    - `provenance=null`.
    - `sourceRevision`: "v2-approved-2026-08-12-a" (RB:250), salvo KPKN SBD con "2026-09-13".
  - **Autoradas (4):** `provenance` (ORIGINAL x2, ADAPTED x2) y `authoredSource`; sin `fidelitySpec`.
  
  ### 1.3 NATIVE (12)
  
  Todas con `publication=PUBLISHED`. Las 8 históricas son `duration=REPEATING_WEEK`; las 4 propias son FINITE_CYCLE.
  
  | id | Título | technicalSubtitle | Nivel | Frec. | requiredEquipment | refs · caps | Receta real | Definición |
  |---|---|---|---|---|---|---|---|---|
  | native:full-body | Empieza con todo el cuerpo | Semana cíclica · 2–3 días | BEGINNER | 2..3 | general_gym | HYP · ∅ | Generada en runtime: 1 semana ("Semana repetible") × días elegidos, `repeats=true` (SCP:511,550) | PPC:104 |
  | native:gym-muscle | Construye músculo en el gimnasio | Semana cíclica · 3–6 días | INTERMEDIATE | 3..6 | general_gym | HYP · ∅ | ídem | PPC:105 |
  | native:machine-muscle | Construye músculo con máquinas | Semana cíclica · 2–4 días | BEGINNER | 2..4 | machine | HYP · ∅ | ídem | PPC:106 |
  | native:home-training | Entrena en casa sin gimnasio | Semana cíclica · 2–4 días | BEGINNER | 2..4 | bodyweight | HYP · ∅ | ídem | PPC:107 |
  | native:bodyweight | Domina tu peso corporal | Semana cíclica · 2–4 días | INTERMEDIATE | 2..4 | bodyweight, support, pull_up_bar | HYP · ∅ | ídem | PPC:108 |
  | native:strength-cardio | Fuerza y resistencia | Semana cíclica · 1–6 días | BEGINNER | 1..6 | general_gym | HYP · ∅ | ídem; cardio por defecto WALK 15 min (SCP:229); única con `schedulesCardio` (PPC:96) | PPC:109 |
  | native:return-training | Vuelve a entrenar | Semana cíclica · 2–3 días | BEGINNER | 2..3 | general_gym | HYP · ∅ | ídem; nunca calibrado (SCP:1690) | PPC:110 |
  | native:one-day | Aprovecha un solo día | Semana cíclica · 1 día | BEGINNER | 1..1 | general_gym | HYP · ∅ | ídem | PPC:111 |
  | native:strength-foundation-v2 | Fuerza KPKN | Ciclo de 6 semanas · 1–6 días | **BEGINNER fijo** | 1..6 | general_gym (gruesa; real: barbell+rack+bench, SCP:709-725) | POWERLIFTING · STRENGTH | 6 semanas (5 + "Descarga KPKN") × 1–6 días, `repeats=false` (SCP:1386-1437) | NPS:39-47; calendarios NPS:408-433; PPC:139-193 |
  | native:muscle-foundation-v2 | Músculo KPKN | ídem | BEGINNER fijo | 1..6 | general_gym | HYP · HYPERTROPHY | ídem | NPS:48-56; NPS:464-483 |
  | native:powerbuilding-foundation-v2 | Fuerza y músculo KPKN | ídem | BEGINNER fijo | 1..6 | general_gym | POWERBUILDING · STRENGTH+HYPERTROPHY | ídem | NPS:57-65; NPS:436-461 |
  | native:complete-athlete-v2 | Atleta completo KPKN | ídem | BEGINNER fijo | 1..6 | general_gym | ∅ · STRENGTH+HYPERTROPHY+POWER+CARDIO | ídem | NPS:66-74; NPS:485-562 |
  
  ### 1.4 TEMPLATE (10)
  
  El `title` solo se reescribe para los 3 simples (PPC:390-395). El resto muestra `template.name` tal cual. Todas son FIXED_PRESCRIPTION y `repeats=false` donde hay receta.
  
  | id | Título mostrado (`template.name`) | technicalSubtitle | Nivel | Frec. | Duración | refs | Semanas × días reales | Definición |
  |---|---|---|---|---|---|---|---|---|
  | template:simple-1 | Tu semana de entrenamiento (name "1 Semana") | 1 semana · una fase | BEGINNER | 1..6 | REPEATING_WEEK | ∅ | Sin receta; las semanas vacías se rellenan con sesiones del split (`ProgramTemplateEngine.kt:141-157`) | PT:36-47 |
  | template:simple-ab | Alterna dos semanas (name "Semana A/B") | 2 semanas · una fase | BEGINNER | 1..6 | FINITE | ∅ | ídem | PT:48-58 |
  | template:simple-4 | Organiza cuatro semanas (name "4 Semanas") | 4 semanas · una fase | BEGINNER | 1..6 | FINITE | ∅ | ídem | PT:59-69 |
  | template:power-12-3 | PL Principiante 3 días · 12 sem | 12 semanas · 3 bloques | **ADVANCED** | 3..3 | FINITE | POWERLIFTING | 12 × 3 (`claimedLevel="principiante"`) | PT:70-92; KAR:25-41 |
  | template:power-16-4 | PL Intermedio 4 días · 16 sem | 16 semanas · 4 bloques | ADVANCED | 4..4 | FINITE | POWERLIFTING | 16 × 4 (claimed "intermedio") | PT:93-117; KAR:43-91 |
  | template:power-20-5 | PL Avanzado 5 días · 20 sem | 20 semanas · 5 bloques | ADVANCED | 5..5 | FINITE | POWERLIFTING | 20 × 5 | PT:118-144; KAR:93-153 |
  | template:powerbuild-16-4 | Acumulación + Fuerza + Hipertrofia dirigida + Realización | 16 semanas · 4 bloques | ADVANCED | 4..4 | FINITE | POWERBUILDING | 16 × 4 | PT:145-174; KAR:155-174 |
  | template:body-12-3 | Hipertrofia UL 4 días · 12 sem | 12 semanas · 4 bloques | **ADVANCED** | 4..4 | FINITE | HYPERTROPHY | 12 × 4 (claimed "intermedio"; el id dice "3") | PT:175-200; KAR:176-201 |
  | template:body-16-4 | PPL 6 días · 16 sem | 16 semanas · 4 bloques | ADVANCED | 6..6 | FINITE | HYPERTROPHY | 16 × 6 | PT:201-226; KAR:203-227 |
  | template:body-20-5 | Off-season 5 días · 20 sem | 20 semanas · 5 bloques | ADVANCED | 5..5 | FINITE | HYPERTROPHY | 20 × 5 | PT:227-254; KAR:229-279 |
  
  ### 1.5 PROTOCOL (33)
  
  `requiredEquipment={general_gym}` en todas. Los títulos de las 4 autoradas son literales; en las 29 restantes salen de `friendlyMethods` o de "Progresa con " + nombre (PPC:412).
  
  | id | Título mostrado | technicalSubtitle | Nivel | Frec. | Semanas × días reales | repeats | refs | Definición |
  |---|---|---|---|---|---|---|---|---|
  | protocol:kpkn-native-sbd-4 | Mejora tus tres levantamientos | KPKN SBD · 4 días · **4 días** · 11 semanas | INT | 4 | 11 × 4 | no | PL | KSBD:19-113 |
  | protocol:texas-method-3d | Progresa con Texas Method | Texas Method · 3 días · ciclo de 4 semanas | INT | 3 | 4 × 3 | sí | PL | TWP:42-112 |
  | protocol:texas-method-4d | Progresa con Texas Method 4 días | Texas Method 4 días · **4 días** · ciclo de 4 semanas | INT | 4 | 4 × 4 | sí | PL | TWP:229-279 |
  | protocol:wendler-531-bbb | Fuerza y músculo por ciclos | 5/3/1 Boring But Big · 4 días · ciclo de 4 semanas | INT | 4 | 4 × 4 | sí | PL | TWP:114-217 |
  | protocol:wendler-531-fsl | Progresa con 5/3/1 First Set Last | 5/3/1 First Set Last · 4 días · ciclo de 4 semanas | INT | 4 | 4 × 4 | sí | PL | TWP:219-226 |
  | protocol:madcow-5x5 | Progresa con Madcow 5×5 | Madcow 5×5 · 3 días · ciclo de 4 semanas | INT | 3 | 4 × 3 | sí | PL | MNG:30-107 |
  | protocol:nsuns-531-lp-4d | Progresa con nSuns 5/3/1 LP 4 días | nSuns 5/3/1 LP 4 días · **4 días** · ciclo de 4 semanas | ADV | 4 | 4 × 4 | sí | PL | MNG:109-193 |
  | protocol:gzclp | Gana fuerza paso a paso | GZCLP · 4 días · ciclo de 4 semanas | INT | 4 | 4 × 4 | sí | PL | MNG:195-258 |
  | protocol:gzcl-jt-2 | Progresa con GZCL Jacked & Tan 2.0 | GZCL Jacked & Tan 2.0 · 4 días · 12 semanas | ADV | 4 | 12 × 4 | no | PL | MNG:270-310 |
  | protocol:gzcl-rippler | Progresa con GZCL The Rippler | GZCL The Rippler · 4 días · 12 semanas | INT | 4 | 12 × 4 | no | PL | MNG:312-341 |
  | protocol:gzcl-uhf-9 | Progresa con GZCL UHF 9 | GZCL UHF 9 · 5 días · 9 semanas | ADV | 5 | 9 × 5 | no | PL | MNG:343-371 |
  | protocol:juggernaut-2 | Progresa con Juggernaut Method 2.0 | Juggernaut Method 2.0 · 4 días · 16 semanas | ADV | 4 | 16 × 4 | no | PL | JSS:53-144 |
  | protocol:sheiko-29-32 | Progresa con Sheiko #29–#32 | Sheiko #29–#32 · 3 días · 16 semanas | ADV | 3 | 16 × 3 | no | PL | JSS:147-410 |
  | protocol:smolov | Progresa con Smolov | Smolov · 4 días · 13 semanas | ADV | 4 | 13 × (4 en sem 1–8; 3 en sem 9–13) | no | PL | JSS:412-557 |
  | protocol:smolov-jr | Especializa tu fuerza con alta frecuencia | Smolov Jr · 4 días · 3 semanas | ADV | 4 | 3 × 4 | no | PL | JSS:559-604 |
  | protocol:candito-6 | Progresa con Candito 6-Week | Candito 6-Week · 4 días · 6 semanas | INT | 4 | 6 × (5 en sem 1; 4 después) | no | PL | JSS:606-658 |
  | protocol:coan-phillipi-dl | Progresa con Coan-Phillipi Deadlift | Coan-Phillipi Deadlift · 1 día · 10 semanas | ADV | 1 | 10 × 1 | no | PL | JSS:660-686 |
  | protocol:korte-3x3 | Progresa con Korte 3×3 | Korte 3×3 · 3 días · 8 semanas | ADV | 3 | 8 × 3 | no | PL | CPL:36-87 |
  | protocol:cube-method | Progresa con Cube Method | Cube Method · 4 días · 10 semanas | ADV | 4 | 10 × 4 | no | PL | CPL:89-149 |
  | protocol:lilliebridge | Progresa con Lilliebridge Method | Lilliebridge Method · 3 días · 10 semanas | ADV | 3 | 10 × 3 (sem 10 en otros weekdays) | no | PL | CPL:151-184 |
  | protocol:westside-conjugate | Progresa con Westside Conjugate | Westside Conjugate · 4 días · ciclo de 3 semanas | ADV | 4 | 3 × 4 | sí | PL | CPL:186-239 |
  | protocol:calgary-16 | Progresa con Calgary Barbell 16 | Calgary Barbell 16 · 4 días · 16 semanas | INT | 4 | 16 × 4 | no | PL | CPL:241-354 |
  | protocol:tsa-9 | Progresa con TSA 9-Week Intermediate v2 | TSA 9-Week Intermediate v2 · 4 días · 9 semanas | INT | 4 | 9 × 4 | no | PL | CPL:356-424 |
  | protocol:phul-verified | Progresa con PHUL | PHUL · 4 días · ciclo de 4 semanas | INT | 4 | 4 × 4 | sí | PB | BNP:31-113 |
  | protocol:phat-verified | Progresa con PHAT | PHAT · 5 días · ciclo de 4 semanas | ADV | 5 | 4 × 5 | sí | PB | BNP:115-193 |
  | protocol:kpkn-ppl-6 | Progresa con PPL 6 días KPKN | PPL 6 días KPKN · **6 días** · 12 semanas | INT | 6 | 12 × 6 | no | HYP | BNP:196-239 |
  | protocol:kpkn-rp-style | Progresa con Mesociclo RP-style KPKN | Mesociclo RP-style KPKN · 4 días · 6 semanas | ADV | 4 | 6 × 4 | no | HYP | BNP:241-271 |
  | protocol:kpkn-rts-style | Progresa con RTS-style KPKN | RTS-style KPKN · 4 días · 8 semanas | ADV | 4 | 8 × 4 | no | PL | BNP:279-314 |
  | protocol:kpkn-sbs-rtf | Progresa con SBS RTF-style KPKN | SBS RTF-style KPKN · 4 días · 8 semanas | INT | 4 | 8 × 4 | no | PL | BNP:316-348 |
  | original:phul-ms-2021-r1 | PHUL original | Original fiel · 4 días · 12 semanas · M&S 2021 | INT | 4 | 12 × 4 | sí (APP:131) | PB+HYP | PPC:322-336; APP:114-143 (días :145-308) |
  | original:phat-biolayne-2016-r1 | PHAT original | Original fiel · 5 días · 6 semanas · Biolayne 2016 | ADV | 5 | 6 × 5 | no (APP:357) | PB+HYP | PPC:337-351; APP:337-360 (días :362-614) |
  | adapted:phul-kpkn-r1 | PHUL adaptado KPKN | Adaptación KPKN · 4 días · 12 semanas | INT | 4 | 12 × 4 | sí | PB+HYP | PPC:352-366; APP:67-84 |
  | adapted:phat-kpkn-r1 | PHAT adaptado KPKN | Adaptación KPKN · 5 días · 6 semanas | ADV | 5 | 6 × 5 | no | PB+HYP | PPC:367-381; APP:86-99 |
  
  En las 29 no autoradas, `duration` es FINITE_CYCLE salvo que `weeks==1 && repeats` (PPC:422), caso que ninguna cumple. Las 4 autoradas son FINITE_CYCLE.
  
  **Fuentes, autor y disclaimer.** El disclaimer sale de `attributed()` como "No afiliado a $author" (RB:248), usando el autor pasado a la función.
  
  | Entradas | `sourceAuthor` (card/detalle) | Disclaimer | URL |
  |---|---|---|---|
  | kpkn-native-sbd-4 | KPKN Fit | "Protocolo nativo KPKN. No afiliado a federaciones de powerlifting." (KSBD:102) | https://kpkn.fit/protocols/kpkn-native-sbd-4 |
  | texas-3d | Mark Rippetoe / Glenn Pendlay | No afiliado a Mark Rippetoe | https://startingstrength.com/article/the_texas_method |
  | texas-4d | Andy Baker / Mark Rippetoe | No afiliado a Andy Baker | la misma |
  | wendler-bbb y fsl | Jim Wendler | No afiliado a Jim Wendler | https://jimwendler.com/blogs/jimwendler-com/101077262-5-3-1-for-a-beginner (la misma en ambas) |
  | madcow | Madcow (Bill Starr) | No afiliado a Madcow / Bill Starr | https://stronglifts.com/madcow-5x5/ |
  | nsuns | nSuns | No afiliado a nSuns | https://www.reddit.com/r/nSuns/ |
  | gzclp, jt-2, rippler, uhf-9 | Cody Lefever | No afiliado a Cody Lefever | https://gzclmethod.com/ (la misma en las 4) |
  | juggernaut-2 | Chad Wesley Smith | ídem formato | https://www.jtsstrength.com/the-juggernaut-method-2-0/ |
  | sheiko | Boris Sheiko | ídem | https://www.powerliftingtowin.com/sheiko/ |
  | smolov y smolov-jr | Sergey Smolov | ídem | https://www.powerliftingtowin.com/smolov/ (Jr hereda la del Smolov completo) |
  | candito-6 | Jonnie Candito | ídem | https://www.canditotraininghq.com/ |
  | coan | Ed Coan / Mark Philippi | No afiliado a Ed Coan / Mark Philippi | …/coan-phillipi-deadlift-routine/ |
  | korte, cube, lilliebridge | Stephan Korte, Brandon Lilly, Matt Lilliebridge | ídem formato | powerliftingtowin.com (3 slugs distintos) |
  | westside | Louie Simmons | ídem | https://www.westside-barbell.com/blogs/the-blog/the-conjugate-method |
  | calgary, tsa | Bryce Krawczyk, The Strength Athlete | ídem | https://calgarybarbell.com/, https://www.thestrengthathlete.com/ |
  | phul-verified | Brandon Campbell | ídem | https://www.muscleandstrength.com/workouts/phul-workout |
  | phat-verified | Layne Norton | ídem | https://www.simplyshredded.com/mega-feature-layne-norton-training-series-full-power-hypertrophy-routine-updated-2011.html |
  | kpkn-ppl-6, rp-style, rts-style, sbs-rtf | KPKN Fit | **"No afiliado a KPKN Fit"** | https://kpkn.fit/protocols/{kpkn-ppl-6, kpkn-rp-style, kpkn-rts-style, kpkn-sbs-rtf} |
  | PHUL x2 autoradas | Brandon Campbell | No afiliado a Brandon Campbell | muscleandstrength.com; edición 2021-05-26, consultada 2026-09-28 (ASR:52-73) |
  | PHAT x2 autoradas | Layne Norton | No afiliado a Layne Norton | https://biolayne.com/articles/training/phat-power-hypertrophy-adaptive-training/; edición 2016-05-30 (ASR:75-96) |
  
  ### 1.6 Descripciones íntegras
  
  **NATIVE históricas** (PPC:104-111, texto íntegro): ver las cadenas literales en las 8 líneas. Ejemplos citados en los hallazgos E-23.
  
  **Perfiles propios** (NPS:43-45, 52-54, 61-63, 70-72):
  - **STRENGTH:** "Sentadilla, banca y peso muerto con barra todas las semanas: técnica de competición, fuerza submáxima y progresión propia durante seis semanas con descarga. Requiere barra y carga, rack y banco confirmados."
  - **MUSCLE:** "Trabajo muscular con rangos, esfuerzo con reserva y seis semanas con descarga. Compatible con mancuernas, bandas o solo peso corporal; sin material de tirón se declara la limitación de espalda en lugar de inventar series."
  - **POWERBUILDING:** "Principales de pocas reps más trabajo muscular durante seis semanas con descarga. Con barra trabaja sentadilla, banca y peso muerto; sin barra se etiqueta como adaptación de movimientos de fuerza con mancuernas, no como preparación SBD."
  - **COMPLETE_ATHLETE:** "Semana con dosis de fuerza, músculo, potencia y cardio dosificadas por separado. La fuerza puede ser relativa con el peso corporal; la potencia usa ejecución rápida real, no series pesadas retituladas."
  
  **Plantillas** (PT:39, 51, 62, 73, 96, 121, 151, 178, 204, 230), en orden de la tabla 1.4:
  - "Programa simple de 1 semana." / "Programa simple de 2 semanas." / "Programa simple de 4 semanas."
  - "12 semanas, 3 días SBD: LP a intermedio con receta explícita, cero exenciones."
  - "16 semanas, 4 días: hipertrofia específica 5, fuerza 5, pico 4 y taper/test 2."
  - "20 semanas, 5 días: acumulación 6, transmutación 5, realización 4, pico 3 y taper 2."
  - "16 semanas totales en 4 bloques. Propuesta larga para fuerza y físico."
  - "12 semanas, 4 días upper/lower: volumen 5 + descarga, intensificación 5 + descarga."
  - "16 semanas, 6 días PPL: volumen, especialización, definición y pico de hipertrofia."
  - "20 semanas, 5 días off-season con rotación de énfasis por bloque."
  
  **Autoradas** (PPC:327-330, 342-344, 357-359, 372-374):
  - **PHUL original:** "PHUL de Brandon Campbell tal y como se publica en Muscle & Strength: cuatro días de fuerza e hipertrofia durante doce semanas, con los rangos del autor, esfuerzo con reserva y sin porcentajes. Requiere barra, rack, banco, polea y máquinas."
  - **PHAT original:** "PHAT de Layne Norton tal y como se publica en Biolayne (2016): cinco días con tres bloques de velocidad al 65 % de tu carga habitual de 3–5 repeticiones, para atletas acostumbrados a la alta frecuencia. Nivel avanzado; seis semanas de carga."
  - **PHUL adaptado:** "El mismo PHUL de cuatro días con las tablas de origen intactas y sustituciones curadas slot a slot cuando falta material. Conserva días y dosis; si algo no puede sustituirse se explica, nunca se recorta en silencio."
  - **PHAT adaptado:** "El mismo PHAT de cinco días con las tablas de origen intactas y sustituciones curadas slot a slot. Si el presupuesto de tiempo no admite el volumen se ofrece el plan propio de fuerza y músculo, nunca un PHAT recortado en silencio."
  
  **Las 29 entradas PROTOCOL no autoradas comparten UNA descripción** (PPC:415), con N sustituido por los días:
  > "Una planificación de N días por semana con una progresión definida. Conservamos el orden y las dosis del método; puedes revisar requisitos y detalle técnico antes de elegirlo."
  
  **`Protocol.description` crudo**, que se ve en `ProtocolDetailSheet` (PDS:60):
  
  | Protocolo | Descripción cruda |
  |---|---|
  | kpkn-native-sbd-4 | "Protocolo KPKN nativo de 4 días y 11 semanas: sentadilla, banca y peso muerto con arquetipos profesionales, cero exenciones." |
  | texas-3d | "3 días, 4 semanas: lunes 5×5 @ 90 % del top de viernes, miércoles 2×5 @ 80 % del lunes, viernes 1×5 PR. Power clean no catalogado: el T3 de intensidad es remo Pendlay explosivo (sugerido por KPKN)." |
  | texas-4d | "4 días PPST: lun banca int + OHP vol, mar sentadilla int + PM vol, jue OHP int + banca vol, vie PM int + sentadilla vol." |
  | wendler-bbb | "4 días, 4 semanas: semanas 5s/3s/1s/descarga con AMRAP y BBB 5×10 @ 50 % TM. TM = 90 % 1RM." |
  | wendler-fsl | "4 días, 4 semanas: mismas olas 5/3/1 con FSL 5×5 al porcentaje del primer set." |
  | madcow | "3 días, 4 semanas: ramp 50/62,5/75/87,5/100 % del 5RM; viernes triple @ 102,5 % + 1×8 @ 75 %; +2,5 %/semana." |
  | nsuns | "4 días, 4 semanas: T1 9 series y T2 8 series con la tabla nSuns; TM por AMRAP. Agarre cerrado = banca + CLOSE_GRIP." |
  | gzclp | "4 días, 4 semanas: T1 5×3+ (etapa 1); T2 3×10; T3 3×15+. Stall a la siguiente etapa T1 queda fuera de este ciclo." |
  | jt-2 | "12 semanas, 4 días: T1 RM semanal 10→2 y test 1RM en sem 6 y 12 con back-offs 4×2." |
  | rippler | "12 semanas, 4 días: T1 sobre 2RM 85-92,5 % +2,5 por bloque; T2 sobre 5RM; sem 11-12 test." |
  | uhf-9 | "9 semanas, 5 días DUP con ondulación diaria de sentadilla, banca y peso muerto." |
  | juggernaut | "16 semanas = 4 olas × 4 semanas (10s/8s/5s/3s) con AMRAP de realización que ajusta TM." |
  | sheiko | "16 semanas, 3 días: #29 preparación, #30 volumen, #31 transmutación, #32 realización. El mismo levantamiento aparece dos veces." |
  | smolov | "13 semanas de especialización de sentadilla: intro, base 4×9@70…, switching, intenso, taper/test." |
  | smolov-jr | "3 semanas, 4 días: 6×6@70, 7×5@75, 8×4@80, 10×3@85, +2,5–5 kg/sem. Especialización SQ o BP." |
  | candito | "6 semanas: hipertrofia (acond + volumen), fuerza (linear + aclimatación) y pico-test." |
  | coan | "10 semanas, 1 día: pesado + velocidad + circuito. Add-on de peso muerto." |
  | korte | "8 semanas, 3 días SBD: fase I 58-64 % series altas; fase II single semanal 80-95 % rotando." |
  | cube | "10 semanas, 4 días: rotación pesado/explosivo/reps por levantamiento + día de culturismo. Semana 10 test." |
  | lilliebridge | "10 semanas, 3 días: SQ/DL mismo día alternando pesado/ligero; banca singles y AMRAP alternos." |
  | westside | "3 semanas, 4 días: ME lower/upper rotando variante; DE ola 12×2 / 10×2 / 8×2 y 9×3. Bandas/cadenas no catalogadas: DE usa SPEED % de barra." |
  | calgary | "16 semanas, 4 días: F1 4×7@64 con frecuencia SQ×3 BP×4 DL×3; F2 top 4×3 + back-off; F4 top @RPE 8 y test." |
  | tsa | "9 semanas, 4 días DUP: volumen 1-4 con esquemas distintos por día, descarga 5, intensidad 6-8, test 9." |
  | phul-verified | "4 días, 4 semanas: power upper/lower 3-5×3-5 @≈80-85 % e hipertrofia upper/lower 8-12 en la misma semana." |
  | phat-verified | "5 días, 4 semanas: 2 power + 3 hipertrofia con speed work 6×3 @ 65-70 % en la misma semana." |
  | kpkn-ppl-6 | "6 días, 12 semanas: Push/Pull/Legs doble frecuencia, RIR 3→1, cero exenciones." |
  | kpkn-rp-style | "6 semanas, 4 días UL: 5 sem MEV→MRV + descarga, RIR 3→0-1, landmarks por músculo." |
  | kpkn-rts-style | "8 semanas, 4 días, inspirado en RTS: top set @RPE 8 + fatiga 5 % (repeats). No es el producto de pago." |
  | kpkn-sbs-rtf | "8 semanas, 4 días, inspirado en SBS RTF: 4 series + última al fallo, TM +0,5 %/rep extra. No es el producto de pago." |
  
  ### 1.7 Entradas ocultas (16, no llegan a `entries()`)
  
  Todas son el índice histórico `LEGACY_PROTOCOL_INDEX`. Se publican forzando `publicationStatus=HIDDEN_UNVERIFIED` (PL:400-403) y no tienen `recipe`. `isVisibleForApplication` (PL:78-80) exige estado distinto de HIDDEN **y** receta no vacía, así que fallan las dos condiciones. El único con colisión de id es `smolov-jr`, que pasa a `smolov-jr-hidden` (PL:401).
  
  | id oculto | Nombre | Autor | Inicio de la definición |
  |---|---|---|---|
  | gzcl-base | GZCL Method | Cody Lefever | PL:102 |
  | 531-base | 5/3/1 Wendler | Jim Wendler | PL:118 |
  | juggernaut-base | Juggernaut Method | Chad Wesley Smith | PL:134 |
  | westside-base | Westside Conjugate | Louie Simmons | PL:150 |
  | rts-base | RTS / Emerging Strategies | Mike Tuchscherer | PL:165 |
  | texas-method | Texas Method | Pendlay / Rippetoe | PL:180 |
  | sheiko-4day | Sheiko 4 Días | Boris Sheiko | PL:196 |
  | sheiko-3day | Sheiko Clásico 3 Días | Boris Sheiko | PL:212 |
  | candito-6week | Candito 6 Week | Jonnie Candito | PL:228 |
  | smolov-jr-hidden | Smolov Jr. | Sergey Smolov | PL:244 |
  | coan-phillipi | Coan-Phillipi Deadlift | Ed Coan / Mark Philippi | PL:258 |
  | nsuns-531 | nSuns 5/3/1 LP | nSuns | PL:273 |
  | sbs-hybrid | SBS Hybrid | Inspirado en Stronger By Science | PL:288 |
  | phul-base | PHUL | Brandon Campbell | PL:304 |
  | phat-base | PHAT | Layne Norton | PL:320 |
  | ppl-hypertrophy | PPL Hipertrofia | Clásico de culturismo | PL:336 |
  
  ---
  
  ## 2. Auditoría editorial
  
  ### 2.1 Veredicto por entrada visible
  
  OK = sin observación; `!` = mejorable; `X` = incorrecto. El cuadro cubre título (T), descripción (D), nivel (N), frecuencia (F) y references/capabilities (R).
  
  | Entrada | T | D | N | F | R | Hallazgos |
  |---|---|---|---|---|---|---|
  | native:full-body, gym-muscle, one-day | OK | OK | OK | OK | OK | E-25, E-36 |
  | native:machine-muscle, home-training | OK | ! | OK | OK | OK | E-23 |
  | native:bodyweight | ! | ! | OK | OK | ! | E-23 |
  | native:strength-cardio | ! | ! | OK | OK | ! | E-23 |
  | native:return-training | OK | ! | OK | OK | OK | E-23 |
  | native:strength-foundation-v2 | OK | ! | X | OK | OK | E-02, E-24 |
  | native:muscle-foundation-v2 | OK | ! | X | OK | OK | E-02, E-24 |
  | native:powerbuilding-foundation-v2 | OK | ! | X | OK | ! | E-02, E-24, E-28 |
  | native:complete-athlete-v2 | OK | ! | X | OK | OK | E-02, E-24, E-28 |
  | template:simple-1, simple-ab, simple-4 | ! | X | ! | ! | X | E-22 |
  | template:power-12-3 | ! | X | X | OK | OK | E-01, E-20 |
  | template:power-16-4 | ! | ! | X | OK | OK | E-01, E-20 |
  | template:power-20-5 | ! | ! | OK | OK | OK | E-20 |
  | template:powerbuild-16-4 | X | ! | OK | OK | OK | E-21, E-38 |
  | template:body-12-3 | ! | ! | X | OK | OK | E-01, E-21 |
  | template:body-16-4 | ! | X | OK | OK | OK | E-21 |
  | template:body-20-5 | X | ! | OK | OK | OK | E-21 |
  | protocol:kpkn-native-sbd-4 | OK | X | OK | OK | OK | E-03, E-05, E-11, E-14 |
  | protocol:texas-method-3d | ! | X | OK | OK | OK | E-03, E-04, E-32 |
  | protocol:texas-method-4d | ! | X | OK | OK | OK | E-03, E-04, E-05, E-32 |
  | protocol:wendler-531-bbb | ! | X | OK | OK | ! | E-03, E-04, E-30 |
  | protocol:wendler-531-fsl | ! | X | OK | OK | OK | E-03, E-04, E-30 |
  | protocol:madcow-5x5 | ! | X | OK | OK | OK | E-03, E-32 |
  | protocol:nsuns-531-lp-4d | ! | X | ! | OK | OK | E-03, E-05, E-30, E-31 |
  | protocol:gzclp | ! | X | ! | OK | OK | E-03, E-04, E-31 |
  | protocol:gzcl-jt-2, gzcl-rippler | ! | X | OK | OK | OK | E-03, E-30 |
  | protocol:gzcl-uhf-9 | ! | X | OK | OK | OK | E-03, E-25 |
  | protocol:juggernaut-2 | ! | X | OK | OK | OK | E-03 |
  | protocol:sheiko-29-32 | ! | X | OK | OK | OK | E-03, E-30 |
  | protocol:smolov | ! | X | OK | X | OK | E-03, E-10 |
  | protocol:smolov-jr | X | X | OK | OK | OK | E-06, E-30 |
  | protocol:candito-6 | ! | X | OK | X | OK | E-10, E-36 |
  | protocol:coan-phillipi-dl | X | X | OK | ! | OK | E-07 |
  | protocol:korte-3x3 | ! | X | OK | OK | OK | E-03 |
  | protocol:cube-method | ! | X | OK | OK | OK | E-03, E-08 |
  | protocol:lilliebridge | ! | X | OK | X | OK | E-10, E-39 |
  | protocol:westside-conjugate | ! | X | OK | OK | OK | E-27 |
  | protocol:calgary-16 | ! | X | OK | OK | OK | E-30, E-39 |
  | protocol:tsa-9 | ! | X | OK | OK | OK | E-38 |
  | protocol:phul-verified, phat-verified | ! | X | OK | OK | ! | E-04, E-19, E-28, E-36 |
  | protocol:kpkn-ppl-6 | ! | X | OK | OK | OK | E-05, E-09, E-11 |
  | protocol:kpkn-rp-style | ! | X | OK | OK | OK | E-09, E-11 |
  | protocol:kpkn-rts-style | ! | X | OK | OK | OK | E-09, E-11 |
  | protocol:kpkn-sbs-rtf | ! | ! | OK | OK | OK | E-09, E-11 |
  | original:phul-ms-2021-r1 | ! | ! | OK | OK | OK | E-12, E-26 |
  | original:phat-biolayne-2016-r1 | ! | ! | OK | OK | OK | E-12, E-26 |
  | adapted:phul-kpkn-r1 | OK | ! | OK | OK | OK | E-12, E-26 |
  | adapted:phat-kpkn-r1 | OK | ! | OK | OK | OK | E-12, E-26, E-31 |
  
  Ninguna entrada visible queda sin observación en la columna D salvo `native:full-body`, `gym-muscle` y `one-day`, que son las más limpias del catálogo.
  
  ### 2.2 Hallazgos ALTA (engañan al usuario o contradicen la receta)
  
  **E-01 · ALTA · 7 plantillas complejas · nivel derivado del tipo, no de la receta**
  - Evidencia:
    - PPC:400 `level = if (template.type == SIMPLE) BEGINNER else ADVANCED`.
    - Las recetas declaran otra cosa: `claimedLevel="principiante"` (KAR:40), "intermedio" (KAR:90 y :200).
    - Se ve en la biblioteca como "Advanced" junto al título "PL Principiante 3 días · 12 sem".
    - `audienceLabel` ("Avanzado", "Intermedio") existe en PT:153,183,209,235 pero nada lo lee.
  - Corrección: calcular el nivel desde `recipe.claimedLevel` reutilizando el mapa de `PlanAdaptationResolver.kt:129-134`.
    - Resultado esperado: power-12-3 BEGINNER, power-16-4 INTERMEDIATE, body-12-3 INTERMEDIATE, y el resto ADVANCED.
    - Conteo resultante: 14 BEGINNER, 20 INTERMEDIATE, 21 ADVANCED.
  
  **E-02 · ALTA · perfiles propios y orden de tarjetas · el nivel nunca filtra y los propios son BEGINNER fijo**
  - Evidencia:
    - PPC:183 `level = BEGINNER` para los 4 perfiles, fijado por test en `T\domain\training\NativeProfileSpecCatalogTest.kt:61`.
    - La receta que producen sí declara el nivel del usuario (SCP:1431-1435).
    - El evaluador no rechaza por nivel (PCE:236-362) y el planificador solo lo usa para ordenar (STP:52-57: coincidencia de referencia, luego nivel, luego nº de equipos, luego `id`).
    - `LEVEL_UNSUITABLE` solo se emite para adaptaciones autoradas (`PlanAdaptationResolver.kt:409-416`).
    - Efectos, inferidos del código:
      - Objetivo "Fuerza", avanzado, 1 día: la primera tarjeta es "Progresa con Coan-Phillipi Deadlift" y "Fuerza KPKN" queda segunda.
      - Objetivo "Fuerza", principiante, 3 días: tras "Fuerza KPKN" salen "Progresa con Korte 3×3" (ADVANCED) y siguientes por orden alfabético de id. "PL Principiante 3 días" (`template:...`) queda al final.
      - "Su nivel coincide con tu experiencia" (SWVM:2032) nunca aparece para intermedios o avanzados con planes propios.
  - Corrección:
    - Los 4 perfiles son adaptativos: añadir `levels: Set<CatalogLevel>` o un flag y tratarlos como coincidencia de nivel.
    - Sustituir el desempate por `id` por un `rank` editorial explícito (propios y originales primero).
    - Decidir si un protocolo ADVANCED puede mostrarse a NEW/RETURNING o solo con aviso.
    - Actualizar `NativeProfileSpecCatalogTest:61`.
  
  **E-03 · ALTA · 29 protocolos · una descripción única con afirmaciones no verificables**
  - Evidencia: PPC:415, mismo texto para las 29 entradas.
  - Problemas:
    - "Conservamos el orden y las dosis del método" es falso para los 5 protocolos KPKN, que no tienen método de autor.
    - También es inexacto para los de terceros con accesorios `KPKN_DEFAULT` (`kpknAssist`, TWP:39-40). El propio PDS:98,108 los marca "Aporte KPKN".
    - "Puedes revisar requisitos y detalle técnico antes de elegirlo" no se cumple en el wizard (E-16).
    - "N días por semana" es inexacta para Smolov, Candito, Lilliebridge (E-10) y Coan (E-07).
    - La biblioteca solo muestra esta frase; el detalle técnico está en `ProtocolDetailSheet`.
  - Corrección: campo `summary` por método, con esta plantilla: "{N} días por semana, {W} semanas{, en ciclos que se repiten}. {Qué haces}. Para {quién}. Necesitas {material}."
    - **Texas Method:** "3 días por semana, en ciclos de 4 semanas que se repiten. Lunes, mucho volumen (5×5); miércoles, una sesión ligera de recuperación; viernes, un intento de récord a 5 repeticiones. Para quien ya no progresa en cada sesión con un plan de principiante."
    - **KPKN SBD:** "4 días por semana durante 11 semanas: 4 de base, 4 de intensificación, 2 de pico y 1 de descarga final. Las cargas parten de tus marcas de sentadilla, banca y peso muerto."
    - **5/3/1 BBB:** "4 días por semana en ciclos de 4 semanas (5s, 3s, 1s y descarga). Cada día un levantamiento principal seguido de 5×10 ligeras para sumar volumen."
  
  **E-04 · ALTA · `friendlyMethods` · 3 claves muertas y títulos incoherentes con la clasificación**
  - Evidencia:
    - PPC:224-232 usa las claves `"texas-method"`, `"phul"` y `"phat"`.
    - Los ids visibles son `texas-method-3d/-4d` (TWP:98,265), `phul-verified` (BNP:88) y `phat-verified` (BNP:161). Las claves nunca casan, así que esas entradas salen como "Progresa con Texas Method", "Progresa con PHUL" y "Progresa con PHAT". "Alterna volumen, recuperación e intensidad", "Combina días de fuerza y músculo" y "Reparte potencia e hipertrofia" nunca se muestran.
    - Solo 4 de 29 protocolos tienen título amigable; los hermanos BBB y FSL quedan con estilos distintos.
    - `wendler-531-bbb` se titula "Fuerza y músculo por ciclos" pero sus `references` son solo POWERLIFTING (tags en TWP:209). No sale con el objetivo "Fuerza y músculo".
    - "Gana fuerza paso a paso" suena a principiante y el nivel es INTERMEDIATE (E-31).
  - Corrección: arreglar las claves y dar título a las 29. Sugerencias:
    - **Texas Method:** "Texas Method: volumen, recuperación e intensidad".
    - **PHUL (heredada):** "PHUL (versión anterior)".
    - **BBB:** título coherente con su disciplina, o ajustar sus `references`.
  
  **E-05 · ALTA · 4 protocolos · "N días" duplicado en el subtítulo**
  - Evidencia: PPC:413 concatena `protocol.name` + días. Los nombres ya llevan los días.
    - Nombres afectados: "KPKN SBD · 4 días" (KSBD:77), "Texas Method 4 días" (TWP:266), "nSuns 5/3/1 LP 4 días" (MNG:179) y "PPL 6 días KPKN" (BNP:198).
    - Resultado en el wizard (STS:970-973): "KPKN SBD · 4 días · 4 días · 11 semanas · Encaja con tus 4 días por semana · …". El test del plural (`WizardPluralCopyTest:135-145`) solo vigila "1 días".
  - Corrección: añadir `shortName` sin días y construir el subtítulo con él, o quitar "· N días" del nombre.
  
  **E-06 · ALTA · `protocol:smolov-jr` · título, descripción y fuente**
  - Evidencia:
    - Título "Especializa tu fuerza con alta frecuencia" (PPC:229). No dice que es solo sentadilla.
    - La receta es solo sentadilla: `liftSlots={SQUAT}` (JSS:593), más jalón y face pull.
    - La descripción cruda dice "Especialización SQ o BP" (JSS:562). Banca no existe en la receta.
    - Dice "+2,5–5 kg/sem" y la receta fija +5 kg (sem 2) y +10 kg (sem 3) (JSS:594).
    - La fuente se hereda de Smolov por `copy` (JSS:545,559): enlaza al ciclo completo de 13 semanas.
    - Es un plan de 4 sesiones de sentadilla por semana durante 3 semanas, presentado como plan general de fuerza.
  - Corrección:
    - Título: "Smolov Jr: 3 semanas solo de sentadilla".
    - Descripción: "4 sesiones por semana de sentadilla con mucho volumen (6×6, 7×5, 8×4 y 10×3 según el día) y subida de carga cada semana, más dos accesorios de espalda. Muy exigente: para avanzados que quieren subir la sentadilla; el resto de tu entrenamiento se reduce."
    - Quitar "o BP" o implementar banca. Fuente propia.
  
  **E-07 · ALTA · `protocol:coan-phillipi-dl` · complemento de 1 día ofrecido como plan completo**
  - Evidencia:
    - Su descripción cruda dice "Add-on de peso muerto" (JSS:663), pero el catálogo la sustituye por la genérica (PPC:415).
    - STP:32 lo ofrece como plan de 1 día/semana a cualquiera con objetivo "Fuerza".
    - No existe el "circuito" de la descripción cruda. La receta es peso muerto rápido + pesado + SLDL + remo + jalón + buenos días (JSS:671-678).
    - Ortografía incoherente en la misma entrada: nombre "Coan-Phillipi" y autor "Mark Philippi" (JSS:661-663). La grafía habitual es Philippi.
  - Corrección:
    - Título: "Complemento de peso muerto (1 día)".
    - Descripción: "Una sesión semanal durante 10 semanas: peso muerto pesado, serie rápida y accesorios de espalda y cadera. Se suma a tu plan; no lo sustituye."
    - Marcar la entrada como complemento y no ofrecerla como plan único.
  
  **E-08 · ALTA · `protocol:cube-method` · rotación inexistente**
  - Evidencia: la descripción cruda (CPL:141) dice "rotación pesado/explosivo/reps por levantamiento". La receta tiene asignación fija (CPL:113-135): día "Pesado" siempre sentadilla, "Explosivo" siempre sentadilla en cajón y banca rápidas, "Repeticiones" siempre peso muerto, más un día de torso de culturismo.
  - Corrección: describir lo que hace la receta, o implementar la rotación entre semanas.
  
  **E-09 · ALTA · RP-style, PPL KPKN, RTS-style · descripciones que prometen lo que la receta no implementa**
  - RP-style (BNP:245): promete "MEV→MRV" y "landmarks por músculo". La receta (BNP:255-265) solo varía el RIR; las series no suben.
  - PPL KPKN (BNP:200): dice "RIR 3→1". En las sesiones 4–6 de cada semana la receta llega a RIR 0 durante 5 semanas (`secondRir`, BNP:225-232).
  - RTS-style (BNP:283): "(repeats)" es un token interno y la receta no repite (BNP:291-312). "Fatiga 5 %" no existe en la receta, y el top set con RPE solo se aplica a la sentadilla (BNP:298-300).
  - Corrección:
    - **RP-style:** "6 semanas, 4 días (torso/pierna): cada semana subes la exigencia bajando las repeticiones en reserva (de 3 a 0–1); la sexta es de descarga."
    - **PPL KPKN:** "…las repeticiones en reserva bajan de 3 a 1 (y a 0 en la segunda sesión semanal de cada grupo durante la intensificación)."
  
  **E-10 · ALTA · frecuencia declarada distinta de los días reales**
  - **Smolov:** declara 4 días (JSS:549). Las semanas 1–8 tienen 4 días, pero las 9–12 tienen 3 (JSS:514-521) y la 13 también 3 (JSS:522-531).
  - **Candito:** declara 4 (JSS:655) y la semana 1 tiene 5 (JSS:619-630). El test lo fija (`ProtocolRecipeFidelityTest:247-257`).
  - **Lilliebridge:** declara 3 y la semana 10 usa los weekdays 2/4/6 mientras las demás usan 1/3/5 (CPL:163-171).
  - Consecuencia inferida del código: el wizard calcula la frecuencia real como la unión de weekdays de todas las semanas y rechaza si no coincide con la elegida (SWVM:2253-2259; `PlanMaterializer.kt:809`). Smolov (unión 5) y Candito (5) no serían viables con 4 días; Lilliebridge (6) no lo sería con 3. Aparecen en la biblioteca pero no como candidatos. PDS:67 muestra "4 días/sem" para Smolov.
  - Corrección: alinear receta y claim, por ejemplo semanas 9–13 de Smolov a 4 días, semana 10 de Lilliebridge a 1/3/5, semana 1 de Candito a 4 días. Si se mantiene, declarar "4 días (3 en las últimas semanas)" y ajustar `supportedFrequencies`.
  
  **E-11 · ALTA · 4 protocolos KPKN · disclaimer y URL sin sentido**
  - Evidencia:
    - `attributed(..., "KPKN Fit")` (BNP:211,252,290,327) genera "No afiliado a KPKN Fit" (RB:248) en planes propios de KPKN.
    - Las URLs `https://kpkn.fit/protocols/...` (BNP:211,252,290,327; KSBD:96) son inventadas o no verificables.
    - RTS-style y SBS RTF-style llevan marcas de terceros en el nombre y el disclaimer no las menciona.
    - Causa probable: `ProtocolAuditTest:84-85` exige `primaryUrl` y `disclaimer` no vacíos en todo protocolo visible, también KPKN_NATIVE.
  - Corrección:
    - Exigir URL y disclaimer solo a `VERIFIED` (terceros).
    - En los KPKN: `disclaimer = "Plan propio de KPKN inspirado en el método RTS; sin afiliación con Reactive Training Systems."` (y SBS/RP análogos), sin URL.
    - Origen "Plan KPKN" (E-19).
  
  **E-12 · ALTA · atribución y detalle de método sin superficie de lectura**
  - Evidencia:
    - `disclaimer`, `authoredSource.effectiveRules`, `kpknDefaults` y `PlanProvenance.operationalDefaults` ("Configuración inicial KPKN") no se muestran en ninguna pantalla. Comentarios en `TrainingPlanProvenance.kt:33,95` y ASR:33 dicen que sí.
    - `SetupPlanCandidate.description` y `.details` (SWVM:2021,2035-2039: "Método de X", revisión, disclaimer) se construyen y nadie los renderiza.
    - En la biblioteca el disclaimer solo aparece en un `AlertDialog` (CPTS:185) que solo se alcanza si `onSelectPlan == null`, es decir, desde el editor de programa (`MacrocycleEditorLegacy.kt:2002-2007`). En Programas y Home esa vía no existe.
    - Resultado: PHUL/PHAT autoradas no muestran "No afiliado a Brandon Campbell/Layne Norton" antes de activar. Los protocolos de terceros solo lo muestran en `ProtocolDetailSheet`.
  - Corrección: hoja "Cómo funciona" por plan (sección 3) con descripción, para quién, material, fuente y "No afiliado a …". Mover a un bloque plegable los defaults KPKN de ASR:66-72, quitando los "§12.4" internos.
  
  **E-13 · ALTA · biblioteca · nivel en inglés**
  - Evidencia: CPTS:157 `entry.level.name.lowercase().replaceFirstChar(...)` imprime "Beginner", "Intermediate" y "Advanced" en una app en español.
  - Corrección: `CatalogLevel.label` con "Principiante", "Intermedio" y "Avanzado".
  
  **E-14 · ALTA · `ProtocolDetailSheet` · códigos internos y jerga**
  - "Notas KPKN" (PDS:74-83) imprime `"${rule} (${scope}): ${justification}"`, por ejemplo "H5b (*): Korte SQ 8×5 + DL 8×5 por diseño" (CPL:38-39).
    - 9 protocolos lo exponen: korte, westside, sheiko, smolov, smolov-jr, coan, nsuns, phul y phat heredados.
    - Los códigos H*/W* son reglas del validador. Para smolov-jr los scopes ("S1", "Test") ni coinciden con sus días ("Sesión").
  - "Requisitos" (PDS:63-73) muestra "Nivel avanzado", "Necesita TM / 1RM", "AMRAP" y "RPE" sin explicar.
  - Además la ficha crudamente muestra descripciones con "cero exenciones", "CLOSE_GRIP", "(repeats)", "T1/T2/T3", "PPST".
  - Corrección:
    - Sustituir "Notas KPKN" por "Excepciones del método" en lenguaje llano, plegado.
    - Traducir las marcas ("Necesitas tu 1RM", "Series al máximo de repeticiones (AMRAP)").
    - Mostrar `entry.summary`.
  
  **E-15 · ALTA · `ProtocolDetailSheet` · calentamiento presentado como prescripción**
  - Evidencia: PDS:89 toma `slot.sets.firstOrNull()` y PDS:100 imprime `slot.sets.size`. En las recetas con `DayArchetypes`, el principal empieza con 3 series de calentamiento (`warmupPercentSets()`, DA:31; RB:7-13).
    - Resultado: "Sentadilla pesada · 7 × 5 · 40%".
    - Afecta a kpkn-native-sbd-4, calgary, tsa, candito, lilliebridge, uhf-9, rts-style y sbs-rtf.
  - Corrección: filtrar `!isWarmup`, mostrar series reales y rango, e indicar AMRAP o top set.
  
  **E-16 · ALTA · paso PLAN del wizard · solo título + subtítulo técnico + motivos**
  - Evidencia: STS:872-879 y 970-973 muestran únicamente `title`, `technicalSubtitle` y los `reasons`. Los motivos son genéricos: "Se ejecuta con el equipo que has elegido" aparece en todas las tarjetas (SWVM:2030).
    - El copy del wizard promete lo contrario: `WCC:79` ("para que compares sus requisitos") y PPC:415 ("puedes revisar requisitos y detalle técnico").
    - Dentro de un mismo plan hay hasta tres ocurrencias de "N días" (nombre, subtítulo, motivo).
  - Corrección: botón "Ver cómo funciona" en cada `WizardChoiceCard` (datos ya disponibles en `SetupPlanCandidate.description/details`), con material requerido, qué trabajas cada día y fuente.
  
  **E-17 · ALTA · programa activado · "Procedencia no declarada" e identificadores crudos**
  - Evidencia:
    - PDSUM:113-118 devuelve "Procedencia no declarada" para `LEGACY` o `null`.
    - `PlanProvenanceClass.KPKN` no se asigna en ningún sitio (solo se lee), así que los 12 nativos y los 10 templates (y, por no tener `provenance`, los 29 protocolos heredados) se muestran como "Procedencia no declarada". Eso son 51 de 55 entradas; solo las 4 autoradas salen bien. El mismo programa figuraba como "Plan KPKN" en la biblioteca.
    - PDSUM:51-54 cae a `"Método anterior · ${program.sourceProtocolId}"`: "Método anterior · texas-method-3d", "Método anterior · power-12-3". `ProgramTemplateEngine.kt:122` guarda el id de plantilla en `sourceProtocolId`. Va contra la regla del propio proyecto de "nunca se imprime un identificador interno" (`NativeProgressionCardModel.kt:41-43`).
  - Corrección: derivar la etiqueta de `PersonalizedPlanCatalog.find(program.structureTemplateId)` ("Plan KPKN", "Método de {autor}", "Versión anterior") y mostrar el título del catálogo, no el id.
  
  **E-18 · ALTA · "Planes" → configurar descarta el plan elegido**
  - Evidencia: `MainActivity.kt:1281-1283` y `HomeScreen.kt:372-375` ignoran el `CatalogEntry` recibido y abren el wizard desde cero. El usuario pulsa "PHUL original" o "Fuerza KPKN" y entra a un wizard genérico cuya lista de candidatos puede no incluirlo.
  - Corrección: pasar `entry.id` como `preselectedPlanId` al wizard, o mostrar antes la hoja informativa con "Configurar este plan".
  
  **E-19 · ALTA · PHUL y PHAT: tres versiones conviviendo, y orígenes mal etiquetados**
  - Evidencia:
    - Conviven "PHUL original", "PHUL adaptado KPKN" y la heredada "Progresa con PHUL" (4 semanas, %TM, días en inglés), igual con PHAT.
    - `lookup()`, `legacyPlanSuccessors` y `LEGACY_VERSION_LABEL = "Versión anterior"` (PPC:241-276) no se usan en ninguna UI. Solo los llama el test.
    - El filtro de origen "Versión KPKN" (CPTS:51) etiqueta LEGACY, es decir, todos los métodos de terceros (24), no versiones KPKN.
    - Los 5 protocolos con autor "KPKN Fit" caen a LEGACY: `sourceAuthor.equals("KPKN")` falla por el sufijo (CPTS:236-238,255). Se ven como "Método · KPKN Fit".
    - Las heredadas aparecen antes que las autoradas en el orden de `entries()` (PPC:430) y compiten en el wizard (`PersonalizedPlanCatalogTest:318-326`).
  - Corrección: badge "Versión anterior" usando `lookup()`, ocultar o relegar las heredadas, renombrar el filtro a "Método de autor", y clasificar KPKN_NATIVE como KPKN.
  
  **E-20 · ALTA · plantillas · jerga y lenguaje de contrato interno en el texto de usuario**
  - Evidencia:
    - PT:73 "SBD: LP a intermedio con receta explícita, cero exenciones". "Cero exenciones" proviene del contrato de composición (`ProgramTemplateCompositionContractTest:24`).
    - PT:121 usa "transmutación" y "realización"; PT:178 "upper/lower"; PT:204 "PPL"; PT:230 "off-season". Los nombres usan "PL", "UL" y "sem".
  - Corrección:
    - **power-12-3:** "12 semanas, 3 días por semana con sentadilla, banca y peso muerto. Empiezas con cargas moderadas y terminas con series pesadas de pocas repeticiones. Para quien empieza en powerlifting."
    - Nombres: "Powerlifting para principiantes · 3 días · 12 semanas".
    - Evitar siglas sin expandir (PL, UL, PPL) y "sem".
  
  **E-21 · ALTA · hipertrofia · nombres de plan y bloque que no corresponden al contenido**
  - **"Definición":** PT:204 la presenta como fase; en la receta es `BlockGoal.DENSITY` con RIR 1 (KAR:211-215), fijado por `ProgramTemplateClaimsTest:65-67`. El wizard usa "Definir" para pérdida de grasa en nutrición (WCG:59), así que "Definición" suena a déficit sin serlo.
  - **"Especialización" (body-16-4 y body-20-5):** los días son idénticos entre bloques y solo cambia el RIR (KAR:203-227). No hay énfasis muscular distinto.
  - **"Off-season" (PT:230):** el primer bloque se llama "Off-season" y luego hay "Definición" y "Pico".
  - **"Hipertrofia dirigida" (powerbuild-16-4):** solo hay un día de hipertrofia (torso) en todos los bloques (KAR:166-171), y el bloque solo baja los porcentajes de los básicos.
  - **"Torso B":** es `bbPush` con etiqueta (KAR:192; BNP:262), con 6 ejercicios de empuje y un solo tirón.
  - **Día "Empuje":** incluye 3 series de dominadas (DA:149).
  - Corrección: renombrar bloques ("Densidad", "Pico de hipertrofia"), ajustar títulos y describir lo que ocurre; si se desea especialización real, implementarla.
  
  ### 2.3 Hallazgos MEDIA
  
  **E-22 · plantillas simples.**
  - Títulos amigables sobre estructuras vacías: "Tu semana de entrenamiento", "Alterna dos semanas", "Organiza cuatro semanas" (PPC:390-395).
  - Descripción: "Programa simple de N semanas." No dice que las semanas se rellenan automáticamente según el split (`ProgramTemplateEngine.kt:141-157`).
  - `references=∅`: el wizard las filtra con cualquier objetivo salvo Atleta completo (STP:51), así que solo se alcanzan desde la biblioteca ("Otros").
  - Al crear el programa se llama "1 Semana", "Semana A/B", "4 Semanas" (PVM:215).
  - Corrección: describir su contenido real, dar `references` o dejarlas solo como estructura vacía.
  
  **E-23 · nativos históricos.**
  - `machine-muscle` (PPC:106): "Solo utilizamos máquinas; no añadimos barras, mancuernas o poleas que no hayas elegido" se contradice (solo máquinas, pero "poleas que hayas elegido").
  - `home-training` (PPC:107): "sin sustituirlo a escondidas" es defensivo y coloquial.
  - `bodyweight` (PPC:108): "Organiza fuerza…" pero `references=HYPERTROPHY` (PPC:128-131; el comentario dice que nunca se reetiquetan). "Domina" con nivel INTERMEDIATE.
  - `strength-cardio` (PPC:109): "series de fuerza" cuando genera ciclos de hipertrofia. "La vista previa reserva tiempo…" describe la app, no el plan. Se solapa con "Atleta completo". Aparece en el objetivo "Músculo".
  - `return-training`: "entrada conservadora" es ambiguo. No se prioriza para `SetupExperience.RETURNING` (STP:52-57).
  
  **E-24 · perfiles propios.**
  - Fugas de razonamiento interno: "retituladas" (NPS:72), "en lugar de inventar series" (NPS:54), "se etiqueta como… no como preparación SBD" (NPS:63), "fuerza submáxima" (NPS:44). SBD y "principales" se usan sin explicar.
  - Atleta completo: "Semana con dosis… dosificadas por separado" repite palabra y describe una semana cuando el plan dura 6 (subtítulo PPC:178).
  - Los 4 declaran `supportedFocuses` completo (PPC:185), pero el motor nativo no usa `input.focus`; solo lo guarda en una etiqueta (SCP:1577).
  - Descripción de Fuerza KPKN no cuenta que la progresión es doble (SCP:1439-1448).
  
  **E-25 · jerga sin explicar.**
  - Aparecen sin glosar: TM, 1RM, T1/T2/T3, AMRAP, RPE, RIR, DUP, MEV/MRV, SBD, PHUL/PHAT, PL/UL/PPL, ME/DE, LP, BBB, FSL.
  - El glosario de la app (`data\wikilab\TrainingConceptsData.kt`) solo tiene RIR, RPE, Deload, Sobrecarga Progresiva, Volumen, Intensidad y Frecuencia. Faltan TM/1RM, AMRAP, DUP, MEV/MRV, SBD y los acrónimos de métodos.
  - Corrección: añadir esos conceptos y enlazarlos desde la hoja del plan.
  
  **E-26 · copy de PHUL y PHAT autoradas.**
  - Los acrónimos PHUL y PHAT no se expanden en el título ni en la descripción.
  - PHUL declara "Requiere…" y PHAT no, aunque exige barra, rack, mancuernas, fondos, máquinas y poleas.
  - "Tablas de origen intactas" convive con "sustituciones curadas", contradictorio en el adaptado.
  - "Se ofrece el plan propio de fuerza y músculo" (PPC:374) no es lógica específica: coincide con el listado general. No hay vínculo explícito entre PHAT adaptado y `native:powerbuilding-foundation-v2`.
  - La línea del autor se repite (provenanceLabel + línea de fuente, CPTS:154,161-166) y expone "fuente consultada 2026-09-28" como si fuera dato editorial.
  - "Slot a slot", "r1" y "M&S" son jerga.
  
  **E-27 · duración y ciclo.**
  - Los ciclos que se repiten (`repeats=true`: Texas, 5/3/1, Madcow, nSuns, GZCLP, Westside, PHUL/PHAT heredados) salen como "Ciclo finito" en la biblioteca (PPC:422; CPTS:266-269) pero "ciclo de N semanas" en el subtítulo (PPC:414) y se ejecutan como CYCLIC (`PlanMaterializer.kt:219`). Falta una tercera etiqueta "Ciclo que se repite".
  - PHUL original tiene `repeats=true` por diseño (documentado en `docs\WIZARD_PLAN_DEVIATIONS.md:180`) pero su subtítulo dice "12 semanas" sin "ciclo".
  - Tres términos para lo mismo: "Semana cíclica" (PPC:117), "Semana repetible" (SCP:511; CPTS:267).
  
  **E-28 · references, capabilities y focuses.**
  - `phul-verified` y `phat-verified`: solo POWERBUILDING (por tags). Las autoradas: POWERBUILDING + HYPERTROPHY (PPC:313). El PHUL heredado no sale con "Músculo".
  - `profileMatches` (CPTS:218-233) usa capabilities para los 4 perfiles y references para el resto: `powerbuilding-foundation` aparece en los filtros Fuerza, Músculo y Fuerza y músculo; PHUL autorado solo en los dos últimos.
  - STP:33 exime a PROTOCOL del filtro de `supportedFocuses`, pero las 7 plantillas complejas (FULL_BODY) desaparecen si el usuario elige cualquier otro enfoque.
  - Atleta completo no filtra por `references` (STP:51 con reference null) y PCE:257-263 rechaza el resto por PROFILE_MISMATCH. Inferido: la línea "N planes evaluados · 1 viable · N-1 no viables" (STS:906-910) puede mostrar cifras muy altas.
  - `capabilities` vacías en PHUL/PHAT autoradas.
  
  **E-29 · nombre y modo del programa creado.**
  - Mismo plan con varios nombres:
    - Biblioteca: `entry.title`.
    - Programas: `protocol.name` (PVM:263), `template.name` (PVM:215).
    - Wizard: "Plan de {nombre del usuario}" para protocolos, plantillas y autoradas (SWVM:2209; se ve en el resumen SRS:242-246).
    - Chip del detalle y editor: `"${emoji} ${protocol.name}"` (`ProgramDetailScreen.kt:197-202`).
  - `ProgramMode.POWERLIFTING` fijo para todos los protocolos creados desde Programas (PVM:266), incluidos PPL, RP-style, PHUL y PHAT.
  - Inferido: el wizard no fija `mode`, por lo que un Smolov activado ahí quedaría como "Hipertrofia" (default de `Program.mode`).
  - El detalle del programa muestra "Powerlifting/Powerbuilding/Hipertrofia" (CHB:97-101), nombres deportivos que el wizard evita deliberadamente (SSD:297-299).
  
  **E-30 · fuentes y URLs.**
  - Raíces genéricas en vez de la página del plan: gzclmethod.com (x4), canditotraininghq.com, calgarybarbell.com, thestrengthathlete.com, reddit.com/r/nSuns/.
  - BBB y FSL comparten la URL "…5-3-1-for-a-beginner", que no es de "5/3/1 Forever" (RB reference de FSL) ni del BBB, y chocan con su nivel intermedio.
  - 7 entradas dependen de un agregador (powerliftingtowin.com) en vez del autor. Smolov Jr hereda la URL de Smolov.
  - PDS:57-59 imprime la URL como texto plano, no clicable.
  
  **E-31 · niveles de protocolos discutibles.**
  - nSuns LP 4 días marcado ADVANCED (MNG:190); GZCLP INTERMEDIATE mientras el título es de principiante (E-04). No verificado contra las fuentes.
  - Asimetría: PHAT adaptado queda bloqueado por nivel si el usuario no es avanzado (`PlanAdaptationResolver.kt:409-416`) y PHAT original, ADVANCED también, se muestra a todos. No hay ningún protocolo BEGINNER.
  
  **E-32 · autor frente a disclaimer.**
  - Rippetoe/Pendlay, pero "No afiliado a Mark Rippetoe" (TWP:102,108). Baker/Rippetoe, pero "No afiliado a Andy Baker" (TWP:269,275). "Madcow (Bill Starr)" frente a "No afiliado a Madcow / Bill Starr" (MNG:97,103).
  - Corrección: pasar el autor completo a `attributed()` o usar los nombres con comas.
  
  **E-33 · textos del wizard.**
  - `SSD:284` "Qué me lo recomiendes" (debería ser "Recomiéndame un plan", como en WCG:35).
  - `WCC:143` "preparo ese plan tal cual es, sin recortar su receta" contradice a los nativos, que se ajustan a tu tiempo y dosis (PPC:104).
  - `WCG:36` ofrece objetivos heredados y no "Atleta completo". No verifiqué si WizChat sigue siendo ruta viva.
  - `TrainingMaxWizard` (TMW:56-58) usa "Training Max" en inglés y se muestra también para protocolos que no usan TM. En PS:285-294 y `HomeScreen.kt:392-401`, cerrar la hoja (o "Más tarde", TMW:107-109) crea el programa de todos modos.
  - `STS:1137,1154` expone "AUTO" y "PROPOSE".
  
  **E-34 · detalles de `ProtocolDetailSheet`.**
  - "Semana tipo Semana 1" (PDS:85) cuando `weekName` ya contiene "Semana".
  - La leyenda dice "[KPKN] = sugerido…" (PDS:108) pero la etiqueta por ejercicio es " · Aporte KPKN" (PDS:98).
  - `toInt()` trunca (62,5 → 62; 87,5 → 87; 97,5 → 97) y formatea "65%" frente a "65 %" en el resto.
  - `ProtocolDetailSheet` solo usa de `CatalogEntry` el título (PDS:54). El resto viene del `Protocol`.
  
  **E-35 · biblioteca.**
  - Orden = orden de `entries()`: históricos, propios, plantillas, protocolos y autoradas al final (PPC:430). Sin orden recomendado.
  - No se muestra `technicalSubtitle`, `requiredEquipment`, `disclaimer` ni `sourceUrl` en tarjetas.
  - El texto de cabecera dice "Plantillas, métodos y planes propios" (CPTS:86): tres sustantivos para lo mismo.
  
  ### 2.4 Hallazgos BAJA
  
  **E-36 · idioma y mayúsculas en días y bloques.**
  - Etiquetas de día en inglés fijadas por tests: "Upper Power", "Lower Power", "Upper Hypertrophy", "Lower Hypertrophy" (BNP:37,50,62,75), "Power Upper/Lower" (BNP:120,134), Candito semana 1 "Lower A/Upper A/…" (JSS:623-627).
  - "Peso Muerto" y "Banca Volumen" (KSBD:69,71) frente a "Peso muerto" y "Banca volumen". "Volumen 5x5" (TWP:44) frente a "5×5"; "Intensidad PR".
  
  **E-37 · restos y campos huérfanos.**
  - Emoji de BBB mal formado: `5` + U+FE0F sin U+20E3 (TWP:206). FSL lo hereda y el chip del detalle muestra una "5" suelta.
  - `audienceLabel` sin uso. IDs de plantilla desalineados ("body-12-3" con 4 días).
  - Parámetro `adapted` sin uso en `authoredEntry` (PPC:289).
  - No hay strings de planes en `res/values*`, todo va en Kotlin; `values-en` existe pero el catálogo no se localiza.
  
  **E-38 · tono y gramática de títulos.**
  - "Progresa con Mesociclo RP-style KPKN" es gramaticalmente forzado. Marca KPKN en posición variable (prefijo y sufijo).
  - Títulos con acrónimos o inglés: "TSA 9-Week Intermediate v2", "GZCL The Rippler", "Off-season".
  - Cinco gramáticas de título distintas: imperativo, marca+tipo, "Progresa con", literal y abreviado. Las descripciones alternan primera persona del plural ("Conservamos", "Ajustamos"), impersonal y técnico.
  
  **E-39 · nombres de bloque crípticos.**
  - F1–F4 (Calgary), B1–B3 (Rippler), "Cubo", "Switching", "Intro", "Descanso" para la semana 10 de Lilliebridge, que tiene 3 sesiones de entrenamiento.
  
  ### 2.5 Prioridades sugeridas
  1. Corregir lo que engaña: E-01, E-02, E-04, E-05, E-06, E-07, E-10, E-11 y E-17.
  2. Dar a cada plan su resumen amigable y mostrarlo (E-03, E-12, E-14, E-15, E-16).
  3. Limpiar el resto de copy y consistencia (E-19 a E-39).
  
  ---
  
  ## 3. Mapa de superficies UI
  
  | Superficie | Archivo:línea | Campos del catálogo que usa | Hallazgos |
  |---|---|---|---|
  | Biblioteca "Planes" (Programas y Home) | CPTS:133-170 | `title` (153), `provenanceLabel` (154: de `provenance`/`sourceAuthor`/`source`), `description` (155), `supportedFrequencies`, `duration`, `level.name` (157), `authoredSource` (161-166). Filtros: Perfil (`references`/`capabilities`), Días, Duración, Procedencia | No usa `technicalSubtitle`, `requiredEquipment`, `disclaimer`, `sourceUrl`. Nivel en inglés (E-13); filtros engañosos (E-19, E-28) |
  | Biblioteca embebida en el editor de programa | `MacrocycleEditorLegacy.kt:1996-2008` (sin `onSelectPlan`) | Mismos campos; `infoEntry` (CPTS:177-198) muestra `disclaimer` para entradas que no son protocolo visible | Único sitio donde el disclaimer de autoradas es visible; las deja sin acción ("Entendido") |
  | Hoja de detalle de protocolo | PDS:54-113 | Solo `entry.title` del catálogo. Resto de `Protocol`: `name`, `author`, `primaryUrl`, `description`, `source.disclaimer`, `fidelitySpec`, `exemptions`, receta semana 1 | E-14, E-15, E-34 |
  | Diálogo de plantilla en el editor | `MacrocycleEditorLegacy.kt:746-797` | `template.name`, `template.description`, `trackLabel`, `blockNames.size`, `weeks` | Duplica y contradice los títulos amigables de las simples |
  | Wizard, paso PLAN | STS:849-891, 970-973 | `title`, `technicalSubtitle` + motivos | `description`, `details`, disclaimer y fuente se calculan (SWVM:2016-2040) y no se pintan. E-16 |
  | Wizard, resumen del bloque | STS:1474-1476 | `entry.title` | Coherente |
  | Wizard, revisión | SRS:242-246 | `program.name` | "Plan de {nombre}" para protocolos, plantillas y autoradas; título del catálogo solo para nativos. E-29 |
  | Confirmación de receta fija | STS:1326-1371 | Weekdays y minutos reales | Redacción correcta, pero revela las frecuencias efectivas (E-10) |
  | Detalle del programa, chip | `ProgramDetailScreen.kt:197-202`; CHB:97-101,233-236 | `protocol.emoji + protocol.name`; etiqueta de foco | Tercer nombre del mismo plan; nombres deportivos. E-29 |
  | Detalle del programa, tarjeta | PDSUM:35-118; `ProgramDetailScreen.kt:278` | `provenance`, `sourceProtocolId`, `author` | E-17 |
  | Detalle del programa, descripción | `ProgramDetailScreen.kt:187` | `Program.description` (texto de `entry.description` + notas, SCP:554,1239) | Solo editable en el banner |
  | Editor de sesión y cabecera de entreno | `SessionEditorViewModel.kt:858-861`; `WorkoutSessionHydrator.kt:631-634` | `protocol.emoji/name` | Autoradas no tienen etiqueta |
  | Mensajes de reemplazo | `ProgramDetailViewModel.kt:750-773,831-863` | `template.name`, `protocol.name` | Usa el nombre técnico |
  | `TrainingMaxWizard` | TMW:56-109 | `recipe.trainingMaxPercent` | E-33 |
  | Textos del chat del wizard | WCC:77-82,141-144; WCG:35-36 | Copy propio | E-33 |
  
  **Duplicados y contradicciones con el catálogo:**
  - `friendlyMethods` frente a `protocol.name`, `template.name` y "Plan de X".
  - Etiquetas de procedencia repetidas con valores distintos (CPTS:250-259 frente a PDSUM:113-118).
  - Fallback hardcodeado "No afiliado a ${protocol.author}" (PDS:61).
  - Orden y filtros de la biblioteca contra los candidatos del wizard.
  - Raíz común: el catálogo no tiene un único campo "nombre de usuario" y cada superficie elige uno.
  
  **¿Existe un lugar donde el usuario lea una explicación amigable del método antes de activarlo?** No.
  - La biblioteca da una frase genérica para 29 de 55 entradas.
  - Solo los protocolos tienen hoja de detalle y es técnica.
  - El wizard no tiene ninguna.
  - Las autoradas y los 12 nativos no tienen ninguna antes de activar.
  
  **Dónde debería estar:**
  - **Hoja única `PlanInfoSheet(entry: CatalogEntry)`.**
    - Se abre desde la tarjeta de biblioteca y desde el botón "Ver cómo funciona" de cada tarjeta del paso PLAN.
    - Contenido: resumen, para quién, cada día de la semana tipo (sin calentamientos), material requerido, fuente y "No afiliado a…", glosario enlazado a Conceptos Clave.
    - Reemplazaría la mitad de `ProtocolDetailSheet`.
  - **Campos nuevos en `CatalogEntry`:** `summary`, `forWhom`, `requirementsText`, `displayName` y un `levelLabel`. El catálogo ya trae `authoredSource` y `disclaimer` sin usar.
  - **Después de activar:** la tarjeta "Plan y procedencia" debe usar los mismos textos del catálogo.
  
  ---
  
  ## 4. Tests que fijan el editorial
  
  Archivos en `T\…`:
  
  | Test | Qué fija | Qué habrá que actualizar si cambia el texto |
  |---|---|---|
  | `data\programs\PersonalizedPlanCatalogTest.kt` | 12 nativos y ids únicos; título y descripción no vacíos (:33-49); multisemana nunca REPEATING_WEEK (:56-58); entradas de protocolo reflejan receta, autor y `primaryUrl` (:63-70); autoradas: frecuencia 4/5, semanas 12/6, nivel INTERMEDIATE/ADVANCED, FINITE_CYCLE, provenance, `consultedOn`, `effectiveRules` y `kpknDefaults` no vacíos, `technicalSubtitle` contiene "Original fiel" (:248) y "Adaptación KPKN" (:262), `references` == {POWERBUILDING, HYPERTROPHY} (:269-272); `lookup` y "Versión anterior" (:276-307); planner incluye autoradas y heredadas (:310-327) | Niveles de PHUL/PHAT, subtítulos de autoradas, `references`, igualdad con `primaryUrl` y autor |
  | `data\programs\ProgramTemplateClaimsTest.kt` | Semanas y bloques de cada plantilla compleja; regex `(\d+)\s*días` sobre nombre + descripción frente a `daysPerWeek`; nombres de bloque Peak/Pico → PEAK, Taper → TAPER con test 90/95/100, "Definición" → DENSITY | Renombrar bloques ("Definición") y cualquier número de días en nombre o descripción |
  | `data\programs\ProgramTemplateCompositionContractTest.kt` | "cero exenciones" (:24) y hallazgos HARD | Origen de la frase interna de PT:73 |
  | `domain\training\NativeProfileSpecCatalogTest.kt` | Perfiles propios: fuente, `adaptation`, frecuencias 1..6, FINITE_CYCLE, **`level == BEGINNER` (:61)**, `references` y `capabilities` exactas; rangos de frecuencia de los 8 históricos (:95-104); `schedulesCardio` solo en `strength-cardio`; calendarios, dosis, semanas | Cambiar el nivel de los propios; frecuencias; `references` |
  | `screens\onboarding\WizardPluralCopyTest.kt` | Subtítulos de `native:one-day` ("Semana cíclica · 1 día") y `native:full-body` ("Semana cíclica · 2–3 días") (:121-123); plantillas empiezan con "1 semana · " y "2 semanas · " (:128-131); ningún título, subtítulo, descripción o disclaimer mezcla "1" con plural (:135-145) | Rediseñar subtítulos; cualquier texto nuevo debe pasar la regex |
  | `data\protocols\ProtocolRecipeFidelityTest.kt` | Porcentajes, series y estructura por protocolo; etiquetas en inglés "Upper Power", "Upper Hypertrophy", "Power Upper" (:283-293); "Pesado" (:182), "DE Lower" (:190); Candito semana 1 con 5 días y `daysPerWeek==4` (:247-257); `fidelitySpec` frente a receta (:341-366); tablas, oráculos y etiquetas en español de PHUL/PHAT autorados (:384-546) | Renombrar días; alinear frecuencias de Candito, Smolov y Lilliebridge |
  | `data\protocols\ProtocolAuditTest.kt` | **`primaryUrl` y `disclaimer` no vacíos en todo protocolo visible (:84-85)**; KPKN_NATIVE sin exenciones (:87-89); `gzcl-base` oculto (:92); deload final y orden de goals | E-11 exige relajarlo para KPKN_NATIVE |
  | `data\protocols\ProtocolAttributionTest.kt` | Slots de apoyo en arquetipos son `KPKN_DEFAULT`; procedencia autorada (título, URL, autor, edición, `consultedOn`, reglas y defaults disjuntos) | Textos de procedencia |
  | `data\protocols\ProtocolExemptionMatrixTest.kt` | Códigos de exención permitidos por protocolo | Los códigos que hoy se filtran en "Notas KPKN" |
  | `data\protocols\ProtocolProgramStructureTest.kt` | Nombres de semana de Wendler "5s/3s/1s/Descarga" (:112); etiquetas contienen "Power" y "Hypertrophy" (:148); nombres de bloque de Candito (:238); whitelist de bloques de 1 semana ("Taper", "Descarga") | Etiquetas en español y nombres de bloque |
  | `data\protocols\ProtocolLibraryTest.kt` | Ids únicos, ≥16 entradas, ids heredados presentes (:26-28), `defaultSplit` válido | Ids ocultos |
  | `domain\onboarding\SetupTrainingPlannerTest.kt` | Filtros por referencia (PL/HYP/PB), plantillas por disciplina, `protocolOnly`, frecuencia no soportada | Cualquier cambio de `references` |
  | `screens\onboarding\SetupTrainingPlannerAvailabilitySweepTest.kt` | Cobertura por referencia × frecuencia; huecos esperados vacíos | Entradas nuevas o cambios de `references` o frecuencia |
  | `domain\onboarding\NativeMixedFrequencyContractTest.kt` | `native:strength-cardio` 1..6 y `schedulesCardio` único; rangos de los 8 históricos | Cambios de frecuencia o capacidades |
  | `domain\training\OnboardingSplitSelectionTest.kt` | Semántica de split sobre `native:machine-muscle`: nombres de sesión "Torso/Pierna" y mensajes de error ("Upper / Lower x4", "4 días de entrenamiento", "has elegido 3") | No fija títulos ni descripciones del catálogo |
  | `domain\training\ProgramStructureContractTest.kt` | Estructura temporal de `Program` (SIMPLE/COMPLEX, migración, calendarización) | **No toca el catálogo**: no fija editorial |
  | `domain\training\T004EntryCoverageProbeTest.kt` | Solo imprime id, refs y nivel | Sin aserciones |
  | `domain\onboarding\SetupStepDefinitionsTest.kt` (:177-189), `screens\onboarding\SetupGoalMappingTest.kt` (:32-101) | Etiquetas y valores de objetivo | Si se añaden descripciones a los 4 objetivos |
  | `domain\onboarding\WizChatStateMachineTest.kt` (:231) | "Fuerza y músculo" está en opciones del chat | Opciones heredadas del chat |
  | `domain\onboarding\AuthoredPlansActivationParityTest`, `screens\onboarding\SetupWizardAuthoredPlansTest`, `AuthoredPlanMaterializerTest`, `domain\training\PlanAdaptationAuthoredRecipesTest`, `data\onboarding\T006PersistenceAndUseIntegrationTest` | Viabilidad, oráculos y persistencia de PHUL/PHAT | No fijan copy; "PHUL original" y "PHAT original" son nombres de fixture |
  | `screens\programdetail\NativeProgressionCardModelTest.kt` | Textos de progresión de la tarjeta | Fuera del catálogo, pero es el estándar de lenguaje llano a imitar |
  
  Sin cobertura alguna: `CreateProgramTemplateSheet`, `ProtocolDetailSheet`, `PlanDetailsSummary`, el mapa `friendlyMethods`, la etiqueta de nivel y los nombres de programa creados. No hay `androidTest` que fije títulos de planes.
  
  ---
  
  ## 5. Lo que no pude verificar
  
  - **Ejecución:** no corrí tests, la app ni gradle. Todo lo dicho sobre orden de candidatos (E-02), rechazos por frecuencia (E-10), cifras de "N planes evaluados" con Atleta completo (E-28) y el modo de programa del wizard (E-29) está inferido leyendo el código, no medido.
  - **Fuentes externas:** no consulté la red. No sé si `kpkn.fit/protocols/*` existen, si las URLs de terceros resuelven ni si corresponden al plan citado. Tampoco pude contrastar la fidelidad de las recetas contra los métodos originales (rotación del Cube, descarga de BBB, distribución de nSuns 4 días, autoría de Calgary y Lilliebridge, nivel de GZCLP y nSuns). Solo audité coherencia interna.
  - **WizChat:** no determiné si sigue siendo ruta viva o solo migración de borradores; por eso E-33 sobre `WCG:36` y `WCC:143` queda con esa cautela.
  - **Lecturas parciales:**
    - `ProgramDetailScreen.kt` (1463 líneas) y `ProgramDetailViewModel.kt` (2406): solo búsquedas dirigidas.
    - `SetupTrainingSteps.kt`: leí 740–1140 y 1300–1519, más búsquedas en el resto.
    - `SimpleCyclePersonalizer.kt` y `SetupWizardViewModel.kt`: por tramos.
    - `SetupExecutableAvailabilityMatrixTest.kt` (2868) y `PlanGenerationCoverageT006Test.kt` (1096): solo búsquedas.
    - `androidTest`: solo búsquedas.
  - **Visual:** no revisé cómo se renderizan los textos (truncados, tamaños, líneas largas del subtítulo con motivos).
  - **Planes externos:** el plan r2 de §10–§15 vive fuera del repo (`C:\Users\valen\.opencode\plan\KPKNFit\...`); no lo leí, solo `docs\WIZARD_PLAN_DEVIATIONS.md`. No sé si el plan define los textos amigables esperados.
