# E · Un solo resolutor de material: símbolos → llaves → configuraciones del catálogo

Lee antes `docs/entreno-v2/BRIEF_COMUN.md`, `docs/WIZARD_ENTRENO_V2.md` (§1b y el contrato de material) y, en el código, `domain/training/EffectiveEquipmentCatalog.kt`, `TrainingOptions.resolveEffectiveEquipment`, `domain/onboarding/EquipmentSymbols.kt`,
`domain/training/generator/GeneratorEquipment.kt` y `SimpleCyclePersonalizer.equipmentAllows` (privada, ~línea 2018). Tu worktree parte de la rama de integración con U1, S-A, D1 y los paquetes que ya aterrizaron.

## Problema (verificado)
1. Hay **dos** filtros por configuración con la misma lógica: `SimpleCyclePersonalizer.equipmentAllows` (planes de autor y candidatos del planificador) y `DayEquipment.allows` (generador de rutinas, que la **replica**). Pueden divergir.
2. El resolutor compartido (`resolveWithAvailability`) solo acredita las 20 llaves del subpanel §13.2. Quedan **inalcanzables desde cualquier símbolo** las configuraciones cuyo `equipmentId` es `trx` (3), `plate` (9), `hex_bar` (8), `safety_bar` (4), `t_bar` (3), `ab_wheel`, `h_bar`, `sliders`, `ghd`, `wrist_roller` (1 cada una): 32 de 521, y todas las que dependan de un cajón de salto o una cuerda de saltar.
   El generador lo parchea a mano (`DayEquipment.tokens` añade `trx`, `plyo_box`, `jump_rope`); los planes de autor no.
3. El símbolo «Barra de dominadas» de un parque no acredita `low_bar_support` aunque casi todas traen una barra baja (el remo invertido y el rack chin dependen de ella): hoy un parque «sin barra baja» no puede hacer tracción horizontal.

## Qué entregas
1. **Un único filtro compartido**: extrae la lógica de `equipmentAllows` a una función `internal` pura en `domain/training` (por ejemplo `ConfigurationEquipmentFilter`), con la misma semántica que hoy (familias `machine-muscle`/`bodyweight`/`home-training`, configuración exacta de máquina, `general_gym` legacy, `supportRequirementsFor`).
   `SimpleCyclePersonalizer` y `DayEquipment.allows` llaman a ESA función; no queda ninguna réplica. Comportamiento idéntico para la ruta legacy: las pruebas `SimpleCyclePersonalizer*`, `PersonalizedPlan*` y `FixedRecipeEquipment*` siguen verdes **sin cambiar sus cifras**.
2. **Acreditación en el resolutor** (no en el generador): añade al vocabulario curado, sin tocar la lista que pinta el subpanel antiguo (usa una lista aparte consumida por `resolveWithAvailability` si hace falta), las llaves que ya viajan desde `EquipmentSymbols`:
   - `rings` → tokens `trx` y `rings`; `plyo_box` → `plyo_box`; `jump_rope` → `jump_rope`.
   - `low_bar_support` también cuando hay `PUBLIC` entre los lugares y `PULL_UP_BAR` está elegido (en `EquipmentSymbols.availabilityOf`, con el mismo mecanismo que `gymSupports`; mantén exacto el ida y vuelta `selectedFrom(availabilityOf(S, lugares)) == S`).
   - Extras habituales de un gimnasio, solo con `GYM` entre los lugares y su símbolo madre elegido: `plate` (con `BARBELL`, en cualquier lugar), `hex_bar` y `t_bar` (con `BARBELL`), `ghd` y `ab_wheel` (con `MACHINES`). **No** acredites `safety_bar`, `h_bar`, `sliders` ni `wrist_roller` (son raros): quedan inalcanzables y los reportas.
   Después borra de `DayEquipment` los parches de `trx`/`plyo_box`/`jump_rope` (el generador usa solo el resolutor) y ajusta sus pruebas.
   Regla STOP: nunca inventes ids de catálogo ni toques los JSON; solo mapeas tokens que el catálogo ya declara como `equipmentId` o requisito.
3. **Pruebas**:
   - Diferencial: para todas las configuraciones del catálogo × ≥ 6 disponibilidades (solo cuerpo, parque, casa con mancuernas y banco, casa con anillas y cajón, gimnasio completo, gimnasio sin rack), el filtro compartido decide igual desde el planificador y desde el generador.
   - Alcanzabilidad: informe (`app/build/reports/equipment-reach/reach.txt`) de cuántas configuraciones abre cada símbolo y cada lugar, y de las que no abre ninguno, con motivo; y una prueba que falla si una configuración con `equipmentId` en tokens conocidos queda inalcanzable sin estar en una lista de excepciones comentada.
   - `EquipmentSymbolsTest`/propiedad del ida y vuelta, `EffectiveEquipment*`, `FixedRecipeEquipment*`, `domain.training.generator.*`, `SetupWizardViewModel*` afectados: en verde, o diagnóstico por fallo.

## Archivos permitidos
`domain/training/{EffectiveEquipmentCatalog,TrainingOptions,SimpleCyclePersonalizer}.kt` (solo lo imprescindible en SCP: sustituir la función privada por la compartida), nuevo `domain/training/ConfigurationEquipmentFilter.kt`, `domain/onboarding/EquipmentSymbols.kt`,
`domain/training/generator/GeneratorEquipment.kt` y pruebas asociadas. **No** toques `SetupWizardViewModel.kt`, pantallas, ni `catalog/`.

## Informe
Tabla antes/después de configuraciones alcanzables por símbolo/lugar, lista de las que siguen inalcanzables y por qué, riesgos, y qué cifras de pruebas cambian (con motivo).
