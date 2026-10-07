# D1b · El generador de rutinas aprovecha el catálogo ampliado y el material acreditado

Lee antes `docs/entreno-v2/BRIEF_COMUN.md`, `docs/WIZARD_ENTRENO_V2.md` (§1b), `docs/entreno-v2/briefs/D1.md` (el contrato original del generador) y, **enteros**, `docs/entreno-v2/lote-bw1-report.md` (en particular la tabla «patrón del generador → ids nuevos» de la sección 2 y la alcanzabilidad de la sección 9)
y `docs/entreno-v2/matrix-d1.txt` (matriz de cobertura regenerada tras E y E2). Tu worktree parte de la rama de integración, donde ya aterrizaron el generador (D1), el lote de peso corporal BW-1 (D4: 17 definiciones y configuraciones nuevas), el resolutor único de material y la sala de máquinas por categoría (E y E2).
Eres el dueño de `domain/training/generator/**` en esta fase.

## Por qué
D1 escribió las reservas (pools por patrón y escaleras de peso corporal) cuando el catálogo no tenía muchos ejercicios sin material y cuando el material acreditado era menor. Ahora:
- hay 17 ejercicios nuevos de peso corporal (pica, dominada negativa, diamante, arquero, hollow body, dead bug, plancha lateral, bird dog, sentada en pared, zancadas, step-up, búlgara, sumo, sissy, rumano a una pierna, buenos días sin carga),
- las anillas/TRX, el cajón y la cuerda de saltar acreditan sus fichas, la barra de dominadas de un parque acredita la barra baja (remo invertido, rack chin) y el gimnasio acredita discos, barra hexagonal, barra T, GHD y rueda,
- el símbolo «Máquinas» abre las 73 configuraciones `machine` (antes 19),
y las reservas no lo saben. Resultado visible: los programas sin material o de parque siguen delgados (3 ejercicios por sesión) y los de gimnasio no usan la mayor parte de las máquinas.

## Qué entregas
1. **Pools y escaleras actualizados** (`MovementPools.kt`, `BodyweightLadders.kt`, `GeneratorCatalog.kt` y lo que haga falta): usa los ids nuevos donde la tabla de D4 los recomienda; las escaleras de peso corporal progresan sin saltos (p. ej. empuje vertical: pica plana → pica con pies elevados; tracción vertical: dominada negativa antes de `pull_up__*`; flexiones: rodillas → estándar → diamante/arquero),
   respetan la dificultad técnica del catálogo (D1 no arranca a un novato por encima de 5,2; la pica, aunque puntúa 4,8/5,0, no se ofrece a novatos ni la elevada sin la plana; el arquero y el diamante no son de novatos) y prescriben los isométricos (sentada en pared, hollow body, plancha lateral) por tiempo, como `core_plancha__default`.
   Los ejercicios con requisito de `support` (pica con pies elevados, step-up, búlgara) se ofrecen solo si el material lo acredita: usa el filtro único ya existente (`ConfigurationEquipmentFilter` vía `DayEquipment`), nunca otra comprobación.
2. **Gimnasio y máquinas:** con «Máquinas» (sala completa) y con la barra hexagonal, la barra T, el GHD, la rueda y los discos, amplía las reservas de gimnasio con las configuraciones que ahora son alcanzables y tienen sentido (remos y press en máquina, prensas, curl femoral, extensiones, gemelos, hexagonal, etc.), sin romper el contrato de tiempo ni la regla «de sesión principal a accesorios».
   Si una configuración alcanzable no encaja con ningún patrón de `RoutinePattern`, no inventes patrones: déjala fuera y lístala en el informe.
3. **Matriz y barrido:** regenera `build/reports/routine-generator/{matrix,samples,sweep-problems}.txt` y copia la matriz a `docs/entreno-v2/matrix-d1.txt`. Los huecos «siempre/a veces» de los perfiles solo cuerpo y parque deben bajar de forma medible (di cuántos antes y después); el barrido de 1–7 días × 20–180 min × perfiles de material × objetivos sigue sin fallos y con minutos dentro de ±1.
4. **Pruebas:** actualiza `domain.training.generator.*` (las que fijen cifras de ejercicios por sesión o ids concretos cambian con motivo comentado); añade pruebas de que un novato solo cuerpo nunca recibe pica elevada, arquero ni diamante; de que `support` se respeta (sin banco/cajón no hay step-up ni búlgara); y de que los isométricos salen por tiempo. Pasan también `domain.training.split.*`, `domain.onboarding.*` y las de equipo (`EffectiveEquipment*`, `FixedRecipeEquipment*`, `EquipmentReachTest`).

## Reglas
- `domain/` sin `android.*`; generador puro y determinista (mismo `RoutineRequest` → mismo `GeneratedRoutine`); contrato de «nunca falla»: si un patrón no tiene candidato, nota honesta, no excepción.
- Solo ids que existan en el catálogo compilado (`CatalogIdsExistInCatalogTest`); nada de inventar ids.
- No toques `SetupWizardViewModel.kt` ni pantallas (otro paquete). Si el generador necesita un cambio en un archivo compartido, descríbelo en el informe.

## Informe
Qué cambió por patrón (tabla antes → después de huecos de la matriz), cifras de ejercicios por sesión en los perfiles solo cuerpo, parque, casa y gimnasio, pruebas que cambian de cifras (con motivo) y qué quedó fuera.
