# Informe final · MacroFactor (Workouts 1.4.1 · Nutrition 5.8.9) frente a KPKN Fit

**Fecha:** 20-oct-2026 · **Repositorio:** worktree `kpkn-macrofactor-audit-1344ab`, rama `claude/kpkn-macrofactor-audit-1344ab`, HEAD `8ac57c9d4` (árbol limpio) · **Modo:** solo lectura: no se modificó código de KPKN, no se hicieron commits, no se ejecutaron Gradle ni las apps · **Base:** ingeniería inversa (RE) del coordinador, verificación independiente de este informe (§3.3) y contraste con el código de KPKN (§4).

**Convenciones**

- Rutas de KPKN relativas a `android-native/app/src/main/java/com/example/kpkn/` (salvo `app/…` = `android-native/app/…`). Todas las líneas son de HEAD `8ac57c9d4`.
- Sin marca = línea abierta y leída por el autor de este informe. **‡** = localizada y leída por un subagente de lectura, no reverificada por el autor (§3.5).
- Niveles de evidencia sobre MacroFactor (se conservan los de la RE, sin elevarlos): **OBSERVADO** (leído en el binario o en el desensamblado), **INFERIDO** (por nombres o semántica), **DESCONOCIDO**, y **DOC** (documentación pública citada por el informe del 17-oct; no es evidencia binaria). Las referencias tipo «A.F3» o «C.F9» remiten a las notas de la RE copiadas en `evidencia/sistemas/`.
- Categorías: **1** falta · **2** existe, pero débil, mal implementado o muy básico (incluye lo huérfano o inalcanzable) · **3** equivalente o superior: no tocar · **4** no aplica o no conviene.
- Prioridad: **P0** cifras incorrectas a la vista o valor central bloqueado · **P1** alto valor · **P2** mejora. Esfuerzo en días-agente: **S** ≤ 1 · **M** 2-5 · **L** > 5.
- No hubo mediciones de UX, latencia ni estabilidad de ninguna de las dos apps: este informe no declara ganadores en esos frentes.

---

## 1. Resumen ejecutivo

**Veredicto.** KPKN ya iguala o supera a MacroFactor en generación de programas, periodización, descarga reactiva con AUGE, calentamiento con movilidad, catálogo por configuraciones exactas, registro de comidas por texto sin red y reparto de calorías por días de entrenamiento. MacroFactor es claramente mejor en dos núcleos:

1. **El coaching nutricional:** gasto adaptativo con ventana, elegibilidad de días y pausa, y un check-in semanal que aplica el nuevo objetivo.
2. **Las propuestas de entrenamiento en kilos realmente cargables:** salen de un perfil de material y de una curva de fuerza que usa el RIR.

En KPKN ambas piezas existen a medias. La calibración nutricional es inestable, no usa la ingesta y solo se abre por enlace profundo. La progresión nativa sabe usar el inventario de material, pero ese inventario no se puede introducir desde la interfaz. Se suman errores de contabilidad de volumen que hoy se ven en pantalla.

**Las 11 decisiones de mayor valor**

| # | Decisión | Por qué (evidencia) | Prio. | Esf. |
|---|---|---|---|---|
| 1 | Sustituir la calibración por un **estimador de gasto propio**: ingesta de días elegibles − energía del cambio de tendencia, con ventana, rampa, límites de paso, suelo y pausa, y la EER como punto de partida | La propuesta actual de ±150 kcal sale solo de la pendiente del último intervalo EWMA. Con pesajes diarios, un desvío de 0,21 kg en el último pesaje ya la satura, y su signo puede invertirse de un día a otro (`domain/nutrition/NutritionCalibrationEngine.kt:62-74`; §4.1, nota 1) | P0 | L |
| 2 | **Tendencia de peso con pendiente robusta** (nivel + pendiente + agua transitoria) que alimente el estimador, el ritmo y la fecha estimada | El ritmo actual es (último − primero) / días, sobre 28 días de medianas brutas (`domain/body/BodyWeightSummary.kt:130-138`; `domain/body/BodyProgressCalculations.kt:80-88`) | P0 | M |
| 3 | **Estado del día** (sin registro · parcial · completo · ayuno) y **check-in semanal** visible en Nutrición, que aplique el objetivo con aceptar, declinar o posponer | Hoy «día completo» = cualquier total > 0 (`screens/nutrition/NutritionCalibrationViewModel.kt:77-88`). El ajuste no se aplica y la pantalla solo se abre por enlace profundo (`navigation/DeepLinkRouter.kt:115`) | P0 | M |
| 4 | Pantalla **«Mi material»** (barra, discos por lado, mancuernas, kettlebells, máquinas con mín., máx. e incremento) y un único inventario resuelto | El motor ya calcula la siguiente carga real (`domain/training/NativeWorkoutProgressionRuntime.kt:1612-1671`). Pero el alta dejó de pedir el material (`screens/onboarding/SetupWizardModels.kt:669-673`) y el runtime recibe el inventario crudo (`data/repository/ProgramRepository.kt:994`): las propuestas salen sin kg | P0 | M |
| 5 | **Contrato único de ventana y contabilidad de volumen** | Divisor «sesiones / 3» (`screens/programdetail/components/VolumeView.kt:160`). Sin calibración, «Real» = total acumulado (`VolumeView.kt:159-161`). Rótulo «semanales» sobre el total del programa (`domain/training/ProgramAnalyticsEngine.kt:230`). Máximo por nombre en las porciones (`VolumeView.kt:675-681`) | P0 | M |
| 6 | **Modelo de fuerza único y versionado que use el RIR** (curva propia por región, inspirada en la x-RM) | ~30 llamadas estiman el e1RM solo con reps (`domain/calculations/Calculations.kt:60-71`), mientras la prescripción usa reps + RIR (`Calculations.kt:90-106`). Demostración algebraica: 100 × 5 @ RIR 2 → e1RM 112,5 → prescribe ≈ 93,8 kg para la misma serie | P1 | M |
| 7 | **Objetivo automático (reps, peso)** con los pesos disponibles y un ranking, en vez de «elige una carga un poco mayor» | Referencia OBSERVADA (C.F9). KPKN sube al menor escalón disponible (`NativeWorkoutProgressionRuntime.kt:1673-1685`) y mantiene el rango de reps del plan ‡: no estima cuántas reps corresponden a la nueva carga | P1 | M |
| 8 | **Reutilización del diario**: copiar comidas o días a otras fechas, «repetir ayer», plantillas (ya existen en datos) y entrada rápida de kcal y macros | «Duplicar» solo funciona en el mismo día (`screens/nutrition/NutritionViewModel.kt:463-464`). Las plantillas no tienen UI (`data/repository/NutritionRepository.kt:852-871`). No hay entrada rápida | P1 | S-M |
| 9 | **Escáner de código de barras en el dispositivo** + búsqueda local por código + alta de alimento propio | MacroFactor lo tiene (OBSERVADO). KPKN no (`app/build.gradle.kts:236-249`), y la UI de alimentos propios no existe (`NutritionRepository.kt:342`, sin llamadores) | P1 | M |
| 10 | **Panel de series semanales por músculo** (1, 4 y 12 semanas, con desglose) y **progreso por ejercicio** con récords | MacroFactor: modos de panel y 13 vistas por ejercicio (OBSERVADO). KPKN: piezas sueltas; `ProgressView` y `HistoryView` se borraron (commit `d978c8a4b`) | P1 | M |
| 11 | **Sustituciones inteligentes en la sesión** con el motor que ya existe | El motor puntuado con filtro de material solo se usa en el alta (`screens/onboarding/SetupWizardViewModel.kt:1850`). En la sesión en vivo, la lista se ordena por patrón sin filtro (`screens/sessioneditor/components/ExercisePickerV2Catalog.kt:260-279`) | P1 | S |

**Primer paquete que implementaría: N1, el estimador de gasto con la tendencia robusta, como dominio puro con un banco de simulación.**

- Es la capacidad de MacroFactor que más valor da a un atleta que cuenta macros.
- La versión actual de KPKN no debe conectarse tal cual: sus ajustes tienen signo inestable.
- Es Kotlin puro, sin Android: se valida con tests y series sintéticas, sin teléfono.
- Desbloquea el check-in (N2).
- No comparte archivos con T1 (material), T2 (volumen), T6 (sustituciones) ni H1 (higiene de datos), que pueden ir en paralelo.
- Si solo hay un agente: N1 primero y T1 justo después, que es la mejora rápida de entrenamiento.

**No tocar (categoría 3):** AUGE · catálogo por configuraciones exactas · generador de programas y periodización · descarga programada y reactiva · calentamiento con movilidad y cobertura articular · reparto semanal de calorías por días de entrenamiento · EER DRI 2023 como estimación inicial · registro por texto local y determinista · no sumar el gasto del entrenamiento al objetivo · temporizador en servicio de primer plano · importador genérico con OCR.

**No conviene traer (categoría 4):** base remota Typesense/Calomeal y la búsqueda o descripción con LLM en la nube · escaneo de la pila de pesos con IA en la nube · parsers de programas comerciales · funciones propias de iOS (Siri, Live Activities) · afiliados.

**Correcciones clave al informe del 17-oct (detalle en §2)**

1. **Dart no era opaco.** El parser usado buscaba los símbolos de un formato antiguo. El snapshot no está ofuscado y se recuperaron fórmulas OBSERVADAS, incluida la ponderación 1,0 / 0,5 que el informe previo pidió no afirmar.
2. **AUGE no usa hoy ni el sueño ni la nutrición.** El sueño tiene un multiplicador fijo de 1,0 por diseño. La nutrición está apagada por defecto y no tiene interruptor. El informe previo afirmó lo contrario.
3. **Parte de lo descrito sobre KPKN cambió después de su corte:** Room v29, `ProgressView` borrado, vista de micronutrientes eliminada. Pero los errores de volumen y de calibración siguen vigentes, y la calibración es peor de lo descrito.

---

## 2. Correcciones al informe del 17-oct

El informe previo auditó el *working tree* del checkout principal sobre `a647d0499` (17-oct). Este informe usa `8ac57c9d4`, que contiene `a647d0499` más 805 commits. Los refactors `d978c8a4b`, `0f649f72c` y `6218d4205` no eran ancestros de `a647d0499`: se integraron después. Por eso «desactualizado» significa «era correcto en su corte y cambió después».

| # | Afirmación previa (dónde) | Veredicto | Evidencia actual |
|---|---|---|---|
| 1 | Dart «permanece opaco»: el parser no recuperó estructuras tipadas porque «faltan los símbolos dinámicos esperados» (README, «Qué permanece opaco en Dart») | **Corregido** | Dart 3.13.3 escribe un snapshot unificado con `_kDartSnapshotData`, `_kDartSnapshotText` y `_kDartSnapshotBuildId`; la herramienta buscaba los cuatro símbolos antiguos. No se usó `--obfuscate`: 1.530 / 1.138 rutas `package:macrofactor/src/…`, 13.073 clases, 16.271 funciones, parseo completo validado (`evidencia/00_formato_snapshot.md` §1-3). Límite real: ~75 % de los nombres de método se perdieron por `--split-debug-info`, y dos cuerpos grandes quedaron sin leer (V3 y el bucle del Kalman). |
| 2 | «No se atribuyen a los binarios coeficientes de series secundarias»; «no afirmar 0,5» (README; reference-product.md, ambigüedad 1) | **Corregido** | Ponderación por grupo muscular 1,0 / 0,5 / 0,0 según la pertenencia a dos listas del ejercicio: OBSERVADO (@0xb78440, reverificado en §3.3). Que esas listas sean primarios y secundarios es INFERIDO. La ventana del panel sigue DESCONOCIDA. |
| 3 | «Tampoco se recuperó el algoritmo completo compilado en Dart» (README) | **Parcialmente corregido** | OBSERVADOS: piso de gasto, TDEE inicial, núcleo V1/V2, EMA, constantes del Kalman, curva x-RM, reps fatigantes, ranking, objetivo automático, calentamiento y descarga. DESCONOCIDOS: fórmula de V3, bucle y suavizador del Kalman, umbrales de los módulos de coaching. |
| 4 | La tendencia se describía como interpolación + media móvil ponderada (reference-product.md #17, DOC) | **Complementado** | V1 = EMA con α = 0,1 (ema20) o 0,22222 (ema9). V2 = Kalman de 3 estados con ruido escalado por peso y robustez MAD (A.F4-A.F5; constantes OBSERVADAS). |
| 5 | V3 simetriza el coste energético de ganar y de perder peso (reference-product.md #20, DOC) | **Confirmado de forma independiente** | V1/V2 usan 7.700 kcal/kg al perder y 4.400 al ganar (OBSERVADO, §3.3). El núcleo V3 contiene 7.700 y no contiene 4.400 (OBSERVADO como presencia de constantes); su fórmula sigue DESCONOCIDA. |
| 6 | «Compartir un recurso no demuestra una función de entrenamiento habilitada en Nutrition» (README) | **Matizado** | Nutrition 5.8.9 conserva tras el *tree shaking* 153 librerías `models/workout`, 39 `services/workout` y 19 `ui/workout`, entre ellas `workout_session_view`, `gym_profiles_view` y `create_workout_program_view`. Hay código alcanzable, no solo datos. Que el usuario lo vea no está demostrado (INFERIDO: función integrada o detrás de un *flag*). |
| 7 | Nota del coordinador: la RE «contradice» la frase «no se encontró flujo productivo de escáner» | **Aclaración** | Esa frase (README:164; kpkn-nutrition.md:33 y :122) hablaba de **KPKN**, y sigue siendo cierta (§4.4 D7). Lo nuevo es la confirmación binaria de que Nutrition tiene escáner (`custom_food_barcode_scanner_view`, `manual_barcode_dialog`, `plate_barcode/*`), con fuente primaria Typesense (OBSERVADO). |
| 8 | Índice del catálogo: 23 grupos, 150 acciones articulares, 43 patrones (README) | **No contradictorio** | Los enums de Dart dan 22 / 148 / 44 (C.0). Son fuentes distintas (índice del JSON frente a enums); no se reconciliaron. |
| 9 | Ecuaciones públicas de BMR de MacroFactor (reference-product.md, DOC) | **Sin contradicción ni confirmación binaria** | El binario expone `BmrVersion {macrofactor, cunningham}`. Solo se localizó Cunningham (500 + 22·LBM, con LBM ≤ 100) dentro del TDEE inicial (A.F2). La ecuación «macrofactor» no se leyó (DESCONOCIDO). |
| 10 | Room v28 (README; kpkn-training.md K23) | **Desactualizado** | `data/db/KpknDatabase.kt:69`: `version = 29`. |
| 11 | Catálogo 96 / 228 / 552, SHA-256 `3607…` (kpkn-training.md) | **Desactualizado** | 96 / 228 / **553**, SHA-256 `81DB1F95…`, con el **mismo** `catalogRevision` `v2-approved-2026-09-29-a` (recuento propio, §8). El identificador de revisión no cambió aunque cambió el contenido. |
| 12 | El volumen completado estima las semanas como sesiones / 3 (README; `VolumeView.kt:168`) | **Vigente y agravado** | `VolumeView.kt:160` y `screens/programdetail/ProgramDetailViewModel.kt:343`. Sin calibración el divisor es 1, y «Real N series» muestra el total de todo el programa (`VolumeView.kt:159-161`; `screens/programdetail/design/ProgramVolumeRow.kt:135-141`). |
| 13 | El informe del programa aplana todas las semanas con vocabulario semanal (`ProgramAnalyticsEngine.kt:613`) | **Vigente** | `domain/training/ProgramAnalyticsEngine.kt:613-623` (aplanado) y `:230` («series semanales planificadas»). |
| 14 | Desglose por porción con el máximo por nombre (`VolumeView.kt:1475`) | **Vigente (archivo reescrito)** | `VolumeView.kt:675-681` (701 líneas tras `6218d4205`). |
| 15 | La barra ajusta por RPE y el detalle por ejercicio no (`VolumeView.kt:1224`) | **Vigente** | ‡ `VolumeView.kt:521-562`, frente a `:603-631`. |
| 16 | `ProgressView` y `HistoryView` existen sin llamadas | **Desactualizado** | Se borraron en `d978c8a4b`. No hay vista de progreso por ejercicio (§4.6 V5). |
| 17 | e1RM 112,5 (Brzycki) frente a 116,7 (Epley en `ProgressView`) | **Actualizado** | La divergencia persiste en otros consumidores: Epley en `domain/training/ProfileProgress.kt:102-105` (Perfil); híbrida en `domain/calculations/Calculations.kt:60-71` (sesión, PR, cierre); Brzycki ‡ en `ProgramAnalyticsEngine.kt:640-644`; variante ‡ en `screens/workout/components/SetExecutionCard.kt:911`. `Settings.oneRMFormula` no se lee (`data/models/Settings.kt:84`). Nuevo: **ninguna usa el RIR** (§4.5 P1). |
| 18 | Pseudo-readiness sin tiempo, consumida por `ProgressView` | **Actualizado** | Se sigue calculando (`ProgramAnalyticsEngine.kt:377-378`) en cada composición (`screens/programdetail/ProgramDetailScreen.kt:952-958`), sin ningún consumidor. |
| 19 | «La recuperación de AUGE sí considera historia, tiempo, capacidad, sueño, nutrición y feedback» (README; kpkn-training.md K11) | **Corregido** | El sueño no modifica τ por diseño: `systemicRecoveryMultiplier(...) = 1.0` (`domain/auge/AugeRecoveryEngine.kt:164-172`, aplicado en `:505-506`). La nutrición solo actúa con `augeEnableNutritionTracking`, que es falso por defecto (`data/models/Settings.kt:300`; `AugeRecoveryEngine.kt:1571`) y no tiene interruptor en la UI. Historia, tiempo y feedback: sí ‡. |
| 20 | Calibración: «completos» = ingesta positiva; ajuste −Δ·7700/7 limitado a ±150; no se aplica (README; kpkn-nutrition.md, hallazgos 1-2) | **Vigente y peor de lo descrito** | `NutritionCalibrationViewModel.kt:77-88`; `NutritionCalibrationEngine.kt:36` y `:73`. Además: (a) la pendiente es la del último intervalo EWMA, unas 0,66 × (último pesaje − tendencia) por semana con pesajes diarios, así que el signo lo decide el último pesaje (§4.1, nota 1); (b) la pantalla solo se abre por enlace profundo (`navigation/DeepLinkRouter.kt:115`; ruta en `MainActivity.kt:1400`, sin navegación interna). |
| 21 | Plantillas y duplicado «parcialmente conectados» | **Actualizado** | «Duplicar» conectado solo al mismo día (`screens/nutrition/NutritionScreen.kt:383`; `NutritionViewModel.kt:463-464`). Plantillas sin UI (`data/repository/NutritionRepository.kt:852-871`, sin llamadores). Alimentos propios sin UI (`NutritionRepository.kt:342`, sin llamadores). |
| 22 | Micronutrientes: los faltantes pasan a 0; UI de progreso con 9 filas fijas | **Vigente y agravado** | Faltantes → 0 en `data/food/FoodImporter.kt:445-449`. La vista de progreso de micronutrientes se eliminó en `0f649f72c` ‡: el día muestra kcal y macros, y cada alimento como mucho fibra, azúcar y sodio ‡. |
| 23 | FTS existe, pero la búsqueda productiva usa LIKE | **Vigente** | `NutritionRepository.kt:400-418`. |
| 24 | El atajo de creatina publica en memoria antes de escribir en Room | **Vigente** | `NutritionRepository.kt:203-207` (`addNutritionLog`). |
| 25 | La carga conjunta no recupera fila a fila | **Vigente** | ‡ `NutritionRepository.kt:1085`; ‡ `data/db/Entities.kt:243`. Un test fija hoy ese comportamiento ‡ (`NutritionRepositoryStartupTest.kt:209-228`). |
| 26 | Las preferencias de utensilios y el aprendizaje de consultas quedan fuera del backup | **Vigente** | ‡ (§4.9). |
| 27 | Sustituciones estructuradas «presentes» (`ExerciseSwapOptions.kt:39`) | **Matizado** | El motor puntuado con filtro de material solo se usa en el alta (`SetupWizardViewModel.kt:1850`). En la sesión en vivo, la lista se ordena sin filtro (`ExercisePickerV2Catalog.kt:260-279`). |
| 28 | Progresión nativa con incrementos reales | **Matizado** | El cálculo por inventario existe, pero no hay UI de inventario y el runtime recibe el inventario crudo (§4.5 P3): las propuestas salen sin kg (`domain/training/NativeProgressionText.kt:66-69`). |
| 29 | Siluetas originales de KPKN (`MuscleArt.kt:11`) como base del mapa | **Matizado** | `MuscleArt` son símbolos del asistente ‡. La silueta corporal coloreada está en `ui/components/rings/RingAdjustBody.kt` y solo pinta la recuperación AUGE, en dos tonos (`:330`). |

---

## 3. Método, niveles de evidencia y límites

### 3.1 Fuentes

- **Base RE** (carpeta temporal de sesión; ver §8). Leídas completas: `00_formato_snapshot.md`, `99_limites_y_peticiones.md` y las siete notas de `sistemas/`. Consultados: `out/nu_paths.txt`, `out/wk_paths.txt`, `re/out_nu/pool.json`, `re/out_wk/pool.json`, `re/dis_nu/*.txt` y `re/dis_wk/*.txt`.
- **Notas de ronda 1:** `A_gasto_y_tendencia.md` y `C_entrenamiento.md` son de la ronda 1. Donde dicen «DESCONOCIDO» (alphas, piso, forma de la curva, magnitud de la descarga), manda la ronda 2 (`A_formulas_gasto_tendencia.md`, `C_formulas_entrenamiento.md`).
- **Informe previo:** `C:\Users\valen\Documents\KPKNFit\artifacts\macrofactor-reference-review-20261017\README.md` y `research/{kpkn-nutrition,kpkn-training,reference-product}.md`.
- **KPKN:** código fuente en HEAD `8ac57c9d4`.

### 3.2 Niveles

Se usan los definidos en las convenciones. Una clase o función de KPKN sin llamada desde la UI no cuenta como capacidad disponible: se clasifica en la categoría 2.

### 3.3 Verificación independiente de la RE (Fase A)

Las constantes del *object pool* se releyeron con scripts propios de solo lectura (`evidencia/verificacion_fase_a/`):

- **arm64 (Nutrition):** palabras de 64 bits.
- **arm32 (Workouts):** pares de palabras de 32 bits.

La aritmética se siguió en los listados `dis_*`. El tope anti-sumidero fue de unas pocas comprobaciones por fórmula.

| Fórmula (RE) | Qué se comprobó | Resultado | Nivel final |
|---|---|---|---|
| Piso de gasto `max(598, 48,1·W^0,783)` (A.F1) | Pool nu [32088] = 598,0 (`0x4082B000…`), [32107] = 598,0, [32108] = 0,783 (`0x3FE90E5604189375`), [32109] = 48,1. En `dis_142be70`: `pow` (ruta rápida + LibcPow), producto por 48,1 y `max` con 598. Recalculado: 60 / 80 / 100 kg → 1.187 / 1.487 / 1.771 kcal | Cuadra | OBSERVADO (sin cambios). Unidad kcal/día: INFERIDA |
| TDEE inicial: Cunningham × (actividad + bono) (A.F2) | `dis_12c0dac` l.36-118: LBM = W − W·bf/100; `min(LBM, 100)`; 22·LBM + 500; (actividad + bono)·BMR. Pool: 100,0 / 500,0 / 0,1 / 0,2 / 0,3. Ejemplo: 80 kg con 20 % → 1.908 × 1,6 = 3.053 | Cuadra, con dos matices: la cadena de actividad pasa por una función sin nombre (@0x67a960; que sea `double.parse` es INFERIDO), y la unidad de «1-3 / 4-6 / 7+» (sesiones u horas por semana) es INFERIDA | OBSERVADO con matices INFERIDOS |
| Núcleo de gasto V1/V2 (A.F3) | Pool: 0,33 / 0,66 / 0,85 / 750 / 4.400 / 7.700 / 50.000. `dis_12c2e8c`: pendiente < 0 → ×7.700; > 0 → ×4.400; = 0 → 0. `dis_12c2174`: candidato = suma / días presentes − ΔE; rampa por día (< 6 → 0,33·Δ; < 13 → 0,66·Δ); ±paso; suelo `max(750, 0,85·X)`. `dis_12c1364`: llamadas con (paso 75, ventana 10) y (paso 30, ventana 25). `dis_12c3038`: umbral 0,85 y mínimo 7 días. Ejemplo: 2.500 kcal y −0,05 kg/día → 2.885 | Cuadra, con una **discrepancia menor**: el día 13 exacto toma el candidato sin límite de paso; el límite rige desde el día 14 (la nota de la RE dice «≥ 13»). No se reverificó que X sea la BMR del día | OBSERVADO, con la corrección del día 13 |
| EMA (A.F4) | `dis_ab252c`: ema9 → 0,22222; en otro caso → 0,1. `dis_ab294c`: cálculo de (1 − α) y suma | Cuadra parcialmente (los productos van por despacho dinámico y no se siguieron). α = 0,22222 literal: «α = 2/N» es una aproximación | OBSERVADO (verificación parcial) |
| Kalman V2: constantes (A.F5) | 0,0008 con límites [0,04; 0,12]; 0,01 con [0,6; 1,4]; 0,007 con [0,45; 0,85]; 0,0025; ln 2/30 y ln 2/2; 1,4826; 0,02; `observationNoiseScale` | Todas presentes. Las matrices F y Q no se reconstruyeron a mano | OBSERVADO (constantes). Estructura según la RE, no reverificada |
| Curva x-RM (C.F1) | Pares arm32: [31639] = 0,965, [31641] = 0,975, [31649] = 0,8, [31651] = 0,2. `dis_b6b3d8` l.150-200: (0,8/e)·eⁿ + 0,2 y `Weight./` | Cuadra. 100 × 5 @ RIR 0 → 111,9 kg (tren superior) y 108,3 kg (resto) | OBSERVADO |
| Reps fatigantes (C.F2) | [31710] = 0,582, [31628] = −0,327, [31718] = 1,718213058 (= 1/0,582). `dis_c811c0`: LibcPow → × (−0,327·rir) → LibcExp → LibcPow → + c | Cuadra. 10 reps: RIR 1 → 5,70; RIR 2 → 3,25; RIR 3 → 1,85 | OBSERVADO |
| Calentamiento (C.F5) | [31861] = [0,4; 0,6; 0,8; 0,95]; [31862] = 0,6. `dis_c8c454`: sale cuando la constante ≥ ratio (l.44); siguiente escalón = anterior + 0,6·(ratio − anterior) (l.110-112). Ratio 0,85 → [0,40; 0,67; 0,778] | Cuadra la aritmética. **Ambigüedad:** los escalones convergen hacia `oneRepMaxRatio`, lo que encaja mejor con fracciones del e1RM que con «× peso de trabajo» (la lectura de la RE). La unidad queda sin resolver | Aritmética OBSERVADA. Unidad INFERIDA |
| Descarga (C.F6) | `dis_c1654c`: x·0,8 → LibcRound → entero. `dis_c15ffc`: 0,8 × series | Cuadra. No se distingue el modo de redondeo de las series | OBSERVADO |
| Ponderación muscular (C.F7) | `dis_b78440`: si el grupo está en la lista w8 → 1,0; si está en w9 → 0,5; si no → pool [5539] = 0,0 | Cuadra | OBSERVADO. w8/w9 = primarios/secundarios: INFERIDO |

**Rutas de las apps (hallazgo 2 del coordinador).** Recontadas en `out/nu_paths.txt` y `out/wk_paths.txt`:

| Rutas | Nutrition | Workouts |
|---|---|---|
| `models/workout` | 153 | 172 |
| `services/workout` | 39 | 77 |
| `ui/workout` | 19 | 118 |
| `program_import` | 0 | 28 |
| `plan_delta` / `plan_diff` | 0 / 0 | 1 / 2 |
| `program_creation` | 0 | 7 |
| `weight_stack_scan` | 1 | 3 |
| `live_activit` | 2 | 4 |
| `recipe_parser` | 25 | 0 |
| `coaching_modules` | 137 | 3 |

Coinciden con lo afirmado por el coordinador.

### 3.4 Lo que no se puede saber con este material (DESCONOCIDO)

- Fórmula de `ExpenditureAlgorithmV3` y de `GoalAdjustmentBudget` (núcleo de 6,3 KB sin leer).
- Paso de actualización del Kalman: si R se usa como σ o como σ², y el horizonte del suavizador.
- Semántica de `crossover`; pesos de la media ponderada de V1; ecuación BMR «macrofactor».
- Umbrales de los módulos de registro parcial, parón de registro y ayuno; reglas de disparo de cada módulo.
- Ventana del panel de grupos musculares; `getProgressionSet`; tabla de series, reps y RIR por objetivo en la creación de programas.
- Valores de `nutrientRanges`; prompts y plantillas de IA (viven en el servidor); host y clave de Typesense.
- Comportamiento real en el teléfono: nada se ejecutó. Cerrar algunos de estos huecos exigiría ejecutar las apps (logcat, capturas), lo que requiere un permiso explícito que no estaba en este encargo.

### 3.5 Cómo se contrastó KPKN

1. Tres subagentes de solo lectura localizaron capacidades con archivo:línea: diario y alimentos; progresión, calentamiento y programas; volumen, mapa, progreso y catálogo.
2. El autor reabrió personalmente todas las líneas que sostienen una decisión P0/P1 o una corrección (las que van sin marca). El resto lleva **‡**.
3. «Conectado» exige un llamador en `screens/`, `navigation/` o en un ViewModel alcanzable. Lo que solo tiene definición, o solo llega por enlace profundo, cuenta como débil (categoría 2).

---

## 4. Contraste por área

### 4.1 Gasto energético y calibración

| # | Capacidad de MacroFactor | Evidencia RE (nivel) | KPKN hoy | Cat. | Recomendación | Prio. | Esf. | Criterio de aceptación verificable |
|---|---|---|---|---|---|---|---|---|
| G1 | Gasto adaptativo = ingesta media de días elegibles − energía del cambio de tendencia | A.F3, núcleo V1/V2 (OBSERVADO); V3 (DESCONOCIDO) | El ajuste es −pendiente del último intervalo EWMA × 7.700/7, limitado a ±150 (`NutritionCalibrationEngine.kt:62-74`). La ingesta solo se guarda (`:75-83`) | 2 | Estimador propio N1 (§5.1) con la EER como punto de partida | P0 | L | Banco sintético (§5.1) con umbrales fijados antes de ajustar. Dos historiales con el mismo peso y +300 kcal/día de ingesta deben dar gastos que difieran ≈ 300 kcal/día (±10 %) |
| G2 | Ventana y elegibilidad: 10 / 25 días, ≥ 85 % de días presentes; día presente = 0 < kcal ≤ 50.000 | A.F3 (OBSERVADO; indicador de ayuno INFERIDO) | Usa todo el historial; «día completo» = cualquier total > 0 (`NutritionCalibrationViewModel.kt:77-88`). Umbrales por conteo (`NutritionCalibrationEngine.kt:53-58`) | 2 | Ventana móvil + % de días elegibles + estado del día (C3, C4) | P0 | M | 14 días con una colación de 150 kcal no habilitan ajuste. Los datos fuera de la ventana no influyen |
| G3 | Rampa inicial (0,33 / 0,66) y límite de cambio diario (±75 y ±30 kcal/día en las series corta y larga) | A.F3 (OBSERVADO) | Sin rampa; tope de ±150 por evaluación (`NutritionCalibrationEngine.kt:36`, `:73`) | 2 | Límites propios calibrados por simulación | P0 | S | Ante un escalón real de −300 kcal/día, llega al 90 % en ≤ N días (N fijado en el banco) sin sobreoscilar |
| G4 | Densidad energética del cambio de peso: 7.700 al perder y 4.400 al ganar en V1/V2; solo 7.700 en V3 | A.F3 y A.F6 (OBSERVADO); simetrización de V3 (DOC) | 7.700 simétrico (`NutritionCalibrationEngine.kt:37`; `domain/nutrition/NutritionEnergyEngine.kt:82`) | 3 | Mantenerlo simétrico, parametrizado y versionado. No copiar la asimetría (sesgo, §5.1) | — | — | Con oscilación simétrica de peso sin cambio de energía, sesgo < 10 kcal/día |
| G5 | Suelos del estimador: `max(598, 48,1·W^0,783)` y `max(750, 0,85·BMR)` | A.F1 y A.F3 (OBSERVADO) | Solo hay suelo del objetivo: 1.200 kcal (`NutritionEnergyEngine.kt:202`) y avisos de 1.000-1.500 por sexo (`domain/nutrition/NutritionPlanTuning.kt:94-97`) | 2 | Suelo propio del estimador, independiente del objetivo | P1 | S | Ninguna estimación por debajo del suelo. El suelo actúa en < 1 % de los días de las series normales |
| G6 | TDEE inicial: Cunningham (LBM ≤ 100) × (actividad 1,2 / 1,4 / 1,6 + bono 0,1 / 0,2 / 0,3) | A.F2 (OBSERVADO; unidades INFERIDAS) | EER DRI 2023 por categoría de actividad (`NutritionEnergyEngine.kt:93-144`) | 3 | Mantener la EER como punto de partida. Opcional: un candidato con masa magra si hay % graso medido (validar) | P2 | S | Con un error inicial de ±500 kcal/día, el estimador entra en ±100 en ≤ N días |
| G7 | Pausa y reanudación; «sin solución» | A.2 (OBSERVADO nombres; INFERIDO mecánica) | Solo espera 14 días desde el `createdAt` del plan (`NutritionCalibrationEngine.kt:59-61`; `NutritionCalibrationViewModel.kt:97-99`): las ediciones no cuentan | 2 | Estados iniciando / aprendiendo / estable / pausado, con motivo. Segmentar por revisión del plan | P0 | S | 7 días sin registros → «pausado» y gasto congelado. Al reanudar, ningún salto mayor que el paso máximo |
| G8 | Calidad por estimación (`missingDays`, `missingWeightEntries`) | A.F0 y A.F3 (OBSERVADO) | Contadores globales en el perfil (`NutritionCalibrationEngine.kt:75-94`) | 2 | Calidad por ventana, visible en el check-in | P1 | S | El check-in enumera los días usados y los excluidos, con su motivo |
| G9 | Banda mín./máx. (`ExpenditureMinMaxProvider`; V3 con 3 escenarios ×1,0 / ×1,1 / ×0,9) | A.0 y A.F6 (OBSERVADO estructura; INFERIDO semántica) | Rango fijo de ±150 alrededor del objetivo (`NutritionEnergyEngine.kt:244`) | 2 | Banda de incertidumbre propia del estimador (no copiar ×1,1 / ×0,9) | P1 | S | La banda cubre el valor real del simulador en la fracción declarada |
| G10 | Ajuste predictivo tras un cambio de objetivo (`GoalAdjustmentBudget`, 1-14 días, ±150/7) | A.F6 (OBSERVADO parámetros; DESCONOCIDO fórmula) | Ver G7 | 2 | Tras un cambio de objetivo, ampliar la incertidumbre unas 2 semanas. No inventar fórmula | P1 | S | Al pasar de déficit a superávit, la recomendación no sobrerreacciona al rebote de agua de la primera semana |
| G11 | Aplicar el ajuste al plan (módulo `programUpdate`) | B.1 (OBSERVADO) | Se muestra y no se aplica (`screens/nutrition/design/calibration/CalibrationStatusModule.kt:56-66`). La pantalla solo se abre por enlace profundo (`DeepLinkRouter.kt:115`; ruta en `MainActivity.kt:1400`) | 2 | Check-in N2 con aplicar, declinar y posponer | P0 | M | Ver C1 |
| G12 | No sumar el gasto del entrenamiento al objetivo | DOC (artículo «Expenditure») | Se informa aparte y no se resta (`domain/energy/TrainingEnergyEngine.kt:735-738`) | 3 | No tocar | — | — | — |
| G13 | Contexto histórico (`LifetimeWeight`, `WeightChange`) | A.1 enums (OBSERVADO); multiplicadores 0,95 / 0,97 / 0,92 (DOC) | El alta pregunta la tendencia y el peso máximo, pero no se usan (`screens/onboarding/SetupActivationPayload.kt:148`) | 2 | Usarlos solo como ajuste del punto de partida, validado | P2 | S | Si con el ajuste el error inicial no baja en el banco, se descarta |

**Nota 1: inestabilidad de la calibración actual (demostración algebraica, no ejecutada).**

- Con pesajes diarios, α = 1 − e^(−ln 2/7) = 0,0943.
- La EWMA da s_n − s_{n−1} = α·(x_n − s_{n−1}), así que el cambio semanal = 7·α·(x_n − s_{n−1}) = 0,66·(x_n − s_{n−1}).
- El ajuste = −1.100 × cambio semanal. Se satura en ±150 en cuanto el último pesaje se aparta 0,21 kg de la tendencia previa.
- Ejemplo: tendencia en 80,00 kg y pesaje de 80,40 → −290 → **−150**. Al día siguiente, pesaje de 79,70 → +245 → **+150**.

Hoy el impacto es bajo, porque la pantalla está oculta y la evaluación es manual. Conectarla tal cual sería dañino.

**Nota 2: lo que KPKN hace bien y se conserva.**

- EWMA por días de calendario y medianas diarias.
- Snapshots versionados del cálculo (`NutritionEnergyEngine.kt:210-238`).
- Reparto semanal por días de entrenamiento, que conserva el presupuesto del periodo (`domain/nutrition/NutritionWeeklyForecastPlanner.kt:7-22`). No tiene equivalente observado en MacroFactor.
- Zonas de ritmo del plan (`NutritionPlanTuning.kt:28-36`).

### 4.2 Tendencia de peso

| # | Capacidad de MacroFactor | Evidencia RE (nivel) | KPKN hoy | Cat. | Recomendación | Prio. | Esf. | Criterio de aceptación verificable |
|---|---|---|---|---|---|---|---|---|
| T1 | Pesajes agrupados por día natural | A.3 (OBSERVADO constante de día; INFERIDO ponderación) | Mediana diaria (`domain/body/BodyProgressCalculations.kt:46-60`) | 3 | No tocar | — | — | — |
| T2 | Tendencia V1: EMA diaria con α = 0,1 (ema20) o 0,22222 (ema9) | A.F4 (OBSERVADO) | EWMA con semivida de 7 días por calendario (`BodyProgressCalculations.kt:63-74`; α ≈ 0,094 por día) | 3 | Mantener como vista simple | — | — | — |
| T3 | Tendencia V2: Kalman de 3 estados (nivel, pendiente, transitorio), ruido escalado por peso, robustez MAD (1,4826; ventana de 21 días) | A.F5 (OBSERVADO constantes; INFERIDO estados; bucle DESCONOCIDO) | No hay pendiente ni componente transitorio. El ritmo es (último − primero) / días sobre medianas brutas de 28 días (`domain/body/BodyWeightSummary.kt:21`, `:130-138`; `BodyProgressCalculations.kt:80-88`) | 2 | Modelo propio de tendencia lineal local con agua transitoria y ponderación robusta, que alimente N1 (§5.3) | P0 | M | Con ruido diario de σ = 0,6 kg y 3 pesajes por semana, el error de la pendiente a 21 días queda bajo el umbral del banco. Un pico de +1,5 kg durante 2 días no cambia el signo de la pendiente |
| T4 | Predicción por objetivo, por datos o mezcla | A.3 (OBSERVADO enum) | Fecha estimada con el ritmo de extremos (`BodyWeightSummary.kt:153-160`) | 2 | Fecha con pendiente robusta e intervalo; al inicio, mezclar con el ritmo objetivo | P1 | S | La fecha no cambia de mes por un único pesaje atípico |
| T5 | Cifra principal = tendencia | A.3 (OBSERVADO *providers*) | «Actual» = último pesaje bruto (`BodyWeightSummary.kt:218-219`); la línea de tendencia sí se dibuja (`screens/body/design/BodyTrendChart.kt:74`) | 2 | Mostrar la tendencia como cifra principal y el pesaje como dato | P2 | S | — |

### 4.3 Coaching y check-in

| # | Capacidad de MacroFactor | Evidencia RE (nivel) | KPKN hoy | Cat. | Recomendación | Prio. | Esf. | Criterio de aceptación verificable |
|---|---|---|---|---|---|---|---|---|
| C1 | Check-in semanal. Estados now / future / dismissed / none; día configurable (lunes por defecto); más de 2 días → now; cadencia de 7 días; orden de módulos [introduction, loggingBreak, fasting, partialLogging, weighIn, programUpdate] | B.3 (OBSERVADO; semántica de fechas INFERIDA) | No existe; la calibración es manual y está oculta (G11) | 1 | Check-in propio (N2) en la pantalla Nutrición | P0 | M | Aparece el día elegido y sigue disponible N días. Aceptar aplica una sola vez (idempotente con reintento). Declinar conserva el objetivo. Los `DailyGoalSnapshot` pasados no cambian |
| C2 | Parón de registro → pausa y reanudación del gasto | B.1 (OBSERVADO nombres; INFERIDO mecánica) | No | 1 | Módulo del check-in sobre G7 | P0 | S | Tras ≥ 7 días sin registro, el check-in explica la pausa y no propone cambios |
| C3 | Revisión de días parciales | B.1 y B.3 (OBSERVADO nombres; umbral DESCONOCIDO) | No; cualquier total > 0 cuenta como día completo | 1 | Estado del día + revisión con umbral propio | P0 | M | Los días bajo el umbral se listan. Marcarlos los excluye sin borrar comidas; rechazar la marca los conserva |
| C4 | Ayuno confirmado | B.1 (OBSERVADO nombres) | No: un día vacío no se puede declarar como 0 kcal. No hay estado de día, y el registrador solo admite fechas hasta hoy (`screens/nutrition/design/logger/LoggerDateSheet.kt:40`) | 1 | Estado «ayuno», que cuenta como 0 kcal | P0 | S | Un ayuno marcado entra con 0 kcal; un día vacío sin marcar se excluye |
| C5 | Pesaje dentro del check-in | B.1 (OBSERVADO) | Recordatorio de medición programable (`screens/nutrition/BodyProgressScreen.kt:356`; `services/nutrition/NutritionNotificationManager.kt:43-52`) | 3 / 2 | Integrar el pesaje en el check-in y mantener el recordatorio | P1 | S | — |
| C6 | «No volver a mostrar» por módulo (`skip`, `lastSeen`) | B.1 y B.3 (OBSERVADO) | No | 1 | Preferencia por módulo | P1 | S | — |
| C7 | Hábitos (registro, pesaje, entrenamiento) con calendario anual | Rutas `ui/dashboard/experiences/habits/*` (OBSERVADO rutas; contenido DESCONOCIDO) | Solo una racha de creatina ‡ | 1 | Rachas propias de registro, pesaje y entreno | P2 | S | — |

### 4.4 Diario y alimentos

| # | Capacidad de MacroFactor | Evidencia RE (nivel) | KPKN hoy | Cat. | Recomendación | Prio. | Esf. | Criterio de aceptación verificable |
|---|---|---|---|---|---|---|---|---|
| D1 | Copiar y pegar alimentos, horas o días; pegar en varias fechas | DOC; rutas `ui/plate_view/*` (OBSERVADO rutas) | Solo «Duplicar» en el mismo día (`NutritionViewModel.kt:463-464`; `NutritionScreen.kt:383`) y «Mover a…» otra comida ‡ | 2 | Copiar a otra fecha o a varias, y «repetir ayer» | P1 | S | Copiar un día a 3 fechas en ≤ 3 toques. Todo pasa por `saveNutritionLog`, sin duplicados por doble toque |
| D2 | Multi-add (bandeja) | DOC; `plate_view` (OBSERVADO rutas) | Bandeja conectada ‡ (`screens/nutrition/components/FoodLoggerDrawer.kt:1543-1588`) | 3 | No tocar | — | — | — |
| D3 | Plantillas o comidas guardadas | DOC | Modelo, DAO y repositorio sin UI: `NutritionRepository.kt:852-871`, sin llamadores | 2 | Conectarlas: guardar desde un registro y aplicar con edición | P1 | S | Guardar una comida y aplicarla otro día con cantidades editables |
| D4 | Entrada rápida de kcal y macros | DOC | No existe (no hay `quickAdd` salvo el de creatina) | 1 | Entrada rápida, marcada como tal | P1 | S | Un registro de 500 kcal sin alimento cuenta para el día y se distingue en el diario |
| D5 | Recetas con rendimiento (peso final, porciones); importación por URL (RDFa / Microdata / LD-JSON + LLM) | D.3 (OBSERVADO registro de parsers) | No hay recetas de usuario; solo estimaciones de platos del catálogo ‡ | 1 | Recetas versionadas con peso final. Opcional: importación LD-JSON local, sin LLM | P2 | M | Lote de 800 kcal en 4 porciones → 200 kcal por porción. Con peso final de 600 g, 150 g → 200 kcal. Editar la receta no cambia los registros pasados |
| D6 | Alimentos propios | Rutas `ui/add_custom_food/*` (OBSERVADO) | Entidad y búsqueda sí; sin UI de alta ni de edición (`NutritionRepository.kt:342`, sin llamadores) | 2 | UI de alta y edición, que sirva también de salida del escáner | P1 | S | Crear, editar y borrar; el alimento aparece en la búsqueda |
| D7 | Escáner de códigos de barras: vista de escáner, código manual, `plate_barcode`; fuente primaria Typesense | D.2 (OBSERVADO configuración y rutas) | No hay escáner: las dependencias solo traen CameraX, `text-recognition` y pose (`app/build.gradle.kts:236-249`). Los códigos OFF solo existen como datos ‡ | 1 | Escáner en el dispositivo + búsqueda local por código + alta propia | P1 | M | Un EAN de OFF Chile abre su producto sin red. Uno desconocido abre el alta propia con el código. Sin permiso de cámara, entrada manual |
| D8 | Búsqueda: Typesense remoto (3 colecciones), OFF y Calomeal | D.2 (OBSERVADO) | Búsqueda local: LIKE por términos (`NutritionRepository.kt:400-418`); FTS4 sin uso productivo ‡ | 3 / 4 | El servicio remoto no conviene (KPKN es local-first). FTS: evaluar con medición | P2 | M | p95 medido en el dispositivo, con el mismo top-k |
| D9 | Descripción por IA: generar → consultar → seleccionar → analizar; respuesta DSV; plantillas Firebase | D.3 (OBSERVADO clases; INFERIDO orden) | Pipeline local y determinista por etapas, con preguntas de aclaración ‡ (`FoodLoggerDrawer.kt:464-655`; `domain/nutrition/FoodInterpretationV2.kt:49-55`) | 3 | No traer un LLM en la nube | — | — | — |
| D10 | Foto de etiqueta → alimento | D.3 (OBSERVADO) | Hay OCR en el dispositivo, pero solo para importar programas ‡ (`data/importer/ocr/*`) | 1 | Lector local de etiquetas con el OCR ya incluido (experimental) | P2 | M | — |
| D11 | Completitud de nutrientes (desconocido ≠ 0) y alimentos que más contribuyen | DOC; `ui/dashboard/experiences/nutrient_explorer` (OBSERVADO rutas) | Los faltantes pasan a 0 (`FoodImporter.kt:445-458`). La vista diaria de micronutrientes se eliminó ‡ | 2 | Estado por nutriente + cobertura + contribuyentes | P2 | L | Un alimento sin dato de sodio no aporta 0. El total indica su cobertura |
| D12 | Objetivos de micronutrientes por sexo, embarazo, lactancia y edad (por 1.000 kcal, fijo, % de energía o por kg) | D.1 (OBSERVADO estructura; valores DESCONOCIDOS) | No; los campos de metas de `MacroGoals` están huérfanos ‡ | 1 | Tablas DRI públicas propias | P2 | M | — |
| D13 | Medidas favoritas, hora de la comida, ajustes del registrador | Rutas `ui/more/food_log/*` (OBSERVADO) | Ranking implícito por uso ‡; todo se guarda a las 12:00 ‡ | 2 | Favoritos y recientes visibles; hora opcional | P2 | S | — |

### 4.5 Progresión y carga

| # | Capacidad de MacroFactor | Evidencia RE (nivel) | KPKN hoy | Cat. | Recomendación | Prio. | Esf. | Criterio de aceptación verificable |
|---|---|---|---|---|---|---|---|---|
| P1 | Modelo de fuerza: curva x-RM por región (e = 0,965 tren superior; 0,975 resto), con n = reps + RIR | C.F1 (OBSERVADO) | 4-5 fórmulas en paralelo (`Calculations.kt:27-71`; `ProfileProgress.kt:102-105`; Brzycki ‡ en `ProgramAnalyticsEngine.kt:640-644`; variante ‡ en `SetExecutionCard.kt:911`). Ninguna usa el RIR: ~30 llamadas a `calculateHybrid1RM(peso, reps)`, incluida `domain/workout/WorkoutPerformanceHomologationEngine.kt:550`. La prescripción sí usa reps + RIR (`Calculations.kt:90-106`). `Settings.oneRMFormula` no se lee (`Settings.kt:84`) | 2 | Modelo único y versionado (T3), con RIR y curva propia validada | P1 | M | Ida y vuelta: estimar con 100 × 5 @ RIR 2 y prescribir 5 @ RIR 2 devuelve 100 ± el redondeo del material. Hoy, con `calculateHybrid1RM` y `calculateWeightFrom1RMAndIntensity`, devuelve ≈ 93,8 |
| P2 | Objetivo automático (reps, peso) con los pesos disponibles: atajo «mantener peso», dos pasadas de candidatos, filtro de rango y ranking abs(n − T)·(abs(W(n) − offset − w) + 1) | C.F9 y C.F4 (OBSERVADO estructura; INFERIDO nombres) | Doble progresión: propone subir tras 2 exposiciones en el tope del rango ‡ (`data/protocols/definitions/NativeProfileSpec.kt:768-793`), al menor escalón disponible (`NativeWorkoutProgressionRuntime.kt:1673-1685`), y conserva el rango de reps del plan ‡. No estima las reps que corresponden a la nueva carga | 2 | T4: elegir (reps, peso) con el modelo P1 y el inventario | P1 | M | Mancuernas de 2 en 2 kg, rango 8-12 @ RIR 2, hoy 20 kg × 12 → propone 22 kg con las reps que mantienen el esfuerzo. Si el escalón no cabe en el rango, más reps con el mismo peso. Nunca un peso no cargable, y sin oscilar entre sesiones |
| P3 | Perfiles de gimnasio: rangos de máquina, listas de pesos, microajustes (dial, pines, discos externos) y etiquetas de bandas | C.1 y C.2.6 (OBSERVADO) | Modelo completo ‡ (`data/models/Settings.kt`: `PlateStock`, `DumbbellPairStock`, `KettlebellStock`, `MachineLoadRange`), pero sin UI: el alta ya no pregunta el material (`SetupWizardModels.kt:669-673`). Con la disponibilidad declarada, el inventario resuelto queda vacío (`Settings.kt:180-188`). El runtime nativo recibe el crudo (`ProgramRepository.kt:994`, frente a `:973`) → propuestas sin kg (`NativeProgressionText.kt:66-69`) | 2 | T1: pantalla «Mi material» + un único inventario | P0 | M | Con barra de 20 kg y discos de 1,25 a 25, un press a 60 kg propone en kg la siguiente carga formable. Los calentamientos con barra dejan de quedar «pendiente». El runtime y el motor de autor reciben el mismo inventario (test) |
| P4 | Fijar el objetivo a un grupo de resistencia concreto (`fixResistanceGroupIds`) | C.2.2 (OBSERVADO firma) | — | 2 | Incluirlo en T4 (elegir una máquina o implemento concreto) | P2 | S | — |
| P5 | Calculadora de discos: búsqueda que minimiza la diferencia; por lado o de un solo lado | C.2.6 (OBSERVADO nombres; INFERIDO criterio) | Algoritmo exacto por programación dinámica ‡ (`domain/calculations/PlateCalculator.kt:113-140`), usado solo por los calentamientos (`domain/training/WarmupFeasibility.kt:159`); sin UI | 2 | Mostrar los discos por lado en la tarjeta de la serie | P2 | S | La carga mostrada coincide con los discos listados |
| P6 | Preferencias: relleno inicial (smart / previous); referencia (anyWorkout / withinProgram) | C.2.2 (OBSERVADO) | Prellenado plan → historial ‡ (`screens/workout/WorkoutLoadSuggestionController.kt:646-652`) | 3 | No tocar | — | — | — |
| P7 | Reps fatigantes e^(−0,5619·RIR) y fatiga acumulada | C.F2 y C.F3 (OBSERVADO) | AUGE: RPE efectivo, series efectivas y reps efectivas por tejido ‡ (`domain/auge/AugeFatigueEngine.kt:249-283`, `:437-444`) | 3 / 4 | No sustituir AUGE. Usarlo solo como hipótesis para «reps estimulantes», si se valida | P2 | S | — |
| P8 | Objetivos por serie dentro de la sesión (`FixableAutoTarget`) | C.1 y C.2.2 (OBSERVADO clases) | La sugerencia contextual de la 2.ª serie del mismo ejercicio parte de 0,8 × la base (`domain/workout/LoadSuggestionEngine.kt:38-44`, `:227`, `:248-253`; `WorkoutLoadSuggestionController.kt:666-678`) | 2 | Verificar en el dispositivo si es intencional. En series rectas, mantener la carga salvo que haya señal de fatiga | P1 | S | Tres series rectas a 100 kg sin fallo: la sugerencia de la 2.ª es 100 (± el ajuste por rendimiento), no 80 |

### 4.6 Volumen, mapa y progreso

| # | Capacidad de MacroFactor | Evidencia RE (nivel) | KPKN hoy | Cat. | Recomendación | Prio. | Esf. | Criterio de aceptación verificable |
|---|---|---|---|---|---|---|---|---|
| V1 | Ponderación muscular 1,0 / 0,5 / 0,0 | C.F7 (OBSERVADO; INFERIDO primario/secundario) | Roles 1,0 / 0,5 / 0,4 / 0,1 (`data/models/ExerciseMuscleInfo.kt:230-235`); en v2 siempre los valores por defecto (`data/exercises/catalogv2/ExerciseCatalogV2LegacyAdapter.kt:52`); modo directo por defecto (`VolumeView.kt:128`) | 3 | No tocar la ponderación | — | — | — |
| V2 | Panel de series (o volumen) por grupo, con ventana y promedio o total | C.2.1 (OBSERVADO modos y rótulos; ventana DESCONOCIDA); DOC: 1 semana, 1 mes, 3 meses | No hay un panel por ventana ‡; la pestaña Volumen trabaja sobre el plan | 1 | T5: panel por ventana con desglose | P1 | M | Para 1, 4 y 12 semanas, la suma del desglose por ejercicio = el total mostrado |
| V3 | Contabilidad coherente (base de V2) | — | Errores vigentes (ver la nota tras la tabla) | 2 | T2: contrato único de ventana y contabilidad | P0 | M | Ver la lista de comprobaciones tras la tabla |
| V4 | Mapa corporal (vista de niveles, SVG coloreado) | C.2.1 (OBSERVADO rutas; contenido DESCONOCIDO); DOC Levels | Silueta de frente y espalda que solo pinta la recuperación AUGE, en 2 tonos y sin contribuyentes (`ui/components/rings/RingAdjustBody.kt:330`) | 2 | Modo «volumen de la ventana» sobre la misma silueta, después de T2 | P1 | M | Tocar una región abre los ejercicios y series que la explican. Cero ≠ sin datos |
| V5 | Progreso por ejercicio: 13 vistas (1RM / 3RM / 10RM, volumen total y de la mejor serie, peso máximo, reps, duración, distancia, series) y récords (volumen, reps, 1RM) | C.0 `ExerciseViewMode` y `WorkoutRecordType` (OBSERVADO) | Piezas sueltas: historial de 10 sesiones, tabla de RM, mini-gráfica al cerrar, «Ejercicios estrella» con Epley ‡. `ProgressView` y `HistoryView` se borraron (commit `d978c8a4b`) | 2 | T5: vista de progreso por ejercicio con el modelo P1 | P1 | M | Se llega desde la ficha y desde Perfil. El mismo e1RM en todos los consumidores |
| V6 | Contribución del peso corporal al volumen (resistencia + peso corporal escalado) | C.2.1 (OBSERVADO tipos y cadenas; INFERIDO escalado) | La homologación distingue los modos de carga ‡ (`WorkoutPerformanceHomologationEngine.kt:539-551`); en el volumen, no verificado | 2 | Incluirlo en T5 si se muestra volumen en kg | P2 | S | — |

**Errores vigentes de contabilidad (fila V3):**

- Divisor sesiones / 3 (`VolumeView.kt:160`; `ProgramDetailViewModel.kt:343`).
- Sin calibración, «Real» = total acumulado (`VolumeView.kt:159-161`; `ProgramVolumeRow.kt:135-141`).
- «Real» suma también el trabajo indirecto: toma por músculo la mayor contribución entre roles (`domain/training/VolumeCalculator.kt:302-336`, llamado en `:537`). La barra, por defecto, solo cuenta series directas (`VolumeView.kt:128`).
- «Semanales» sobre el total del programa (`ProgramAnalyticsEngine.kt:230`, `:613-623`).
- Máximo por nombre en las porciones (`VolumeView.kt:675-681`).
- Desglose sin intensidad ‡ (`VolumeView.kt:521-562`).
- Otra ventana distinta en `domain/auge/OvertrainingDetector.kt:39`.

**Comprobaciones que debe pasar T2 (criterio de V3):**

- 6 sesiones en 1 semana × 2 series = 12 por semana.
- 6 sesiones en 3 semanas = 4 por semana.
- Sin calibración, «Real» = promedio semanal, no el total.
- 3 + 3 series del mismo ejercicio = 6 en la porción.
- El desglose suma lo mismo que la barra, con y sin ajuste por intensidad.
- Detalle del programa e Inicio usan la misma ventana.

### 4.7 Programas, calentamiento, descarga y sesión

| # | Capacidad de MacroFactor | Evidencia RE (nivel) | KPKN hoy | Cat. | Recomendación | Prio. | Esf. | Criterio de aceptación verificable |
|---|---|---|---|---|---|---|---|---|
| S1 | Creación de programas: 13 splits, objetivo, 4 periodizaciones, autoevaluaciones, reparto con `spillover` (1,25; 100 iteraciones), tiempos de 90 / 180 s + 120 s | C.2.5 y C.F8 (OBSERVADO constantes; INFERIDO reglas) | Generador con 7 repartos por días, 36 plantillas visibles, periodización por fases, asignación por coste, ajuste al tiempo (1,10) y escaleras de calistenia con autoevaluación ‡ | 3 | No tocar | — | — | — |
| S2 | Calentamiento inteligente: escalones [0,4; 0,6; 0,8; 0,95] según `oneRepMaxRatio`, con interpolación de 0,6 | C.F5 (OBSERVADO aritmética; INFERIDO unidad) | Rampas por nivel y carga planificada (40 / 60 / 80 % del peso de trabajo, + 90 % si la carga planificada es ≥ 90 %), con movilidad y cobertura articular (`domain/training/approach/ApproachRamp.kt:18-56`; `ApproachPlanner.kt:71-128`); redondeo al material ‡ | 3 | Mantener. Refinamiento opcional según la intensidad real (§5.7) | P2 | S | — |
| S3 | Recorte del calentamiento si ya se trabajó el agonista o el mismo ejercicio | C.1, campos (INFERIDO) | Cobertura articular en los 3 primeros ejercicios (`ApproachPlanner.kt:77-127`) | 3 | No tocar | — | — | — |
| S4 | Descarga: series × 0,8 (mín. 1), reps × 0,8 redondeadas, fallo → estándar; primer o último ciclo | C.F6 (OBSERVADO) | Programada (nativa: series / 2 y RIR 4 ‡; periodizador × 0,55 ‡) y reactiva con AUGE, aceptable o rechazable ‡ | 3 | No tocar. Revisar la carga en la descarga nativa (§4.9) | — | — | — |
| S5 | Revisión de los cambios de la sesión antes de pasarlos al programa (`PlanDelta` / `PlanDiff`) | C.1 (OBSERVADO clases; INFERIDO uso) | Diálogo por cambio con alcance (esta vez / semana / bloque / programa) ‡. `diffSessionPlan` solo lo usa `revertPlanAspect`, que no tiene llamadores (`screens/workout/WorkoutViewModel.kt:4403-4407`) | 3 | No tocar el flujo. Decidir si el código huérfano se borra o se conecta | P2 | S | — |
| S6 | Sustituciones inteligentes en la sesión, condicionadas por el gimnasio | DOC; C.2 | El motor con puntuación y filtro de material solo se usa en el alta (`SetupWizardViewModel.kt:1850`). En vivo, la lista se ordena por definición, patrón y músculo, sin filtro (`ExercisePickerV2Catalog.kt:260-279`) | 2 | T6: «Sugeridos» del motor arriba y la lista completa debajo | P1 | S | Hasta 6 sugeridos compatibles con el material del lugar. No se oculta nada |
| S7 | Tipos de serie: estándar, calentamiento, drop, myo y fallo; parciales; izquierda / derecha | C.0 (OBSERVADO) | Drop, rest-pause, parciales, izquierda / derecha, fallo, AMRAP y top set; sin myo-reps (`screens/workout/components/SetCardTechniqueBack.kt:96-97`; `data/models/Session.kt:545`) | 2 | Añadir myo-reps | P2 | M | — |
| S8 | Temporizador (120 s por defecto) y actividad en vivo | C.2.7 (OBSERVADO) | Servicio en primer plano con acciones ‡. Descanso por defecto de 90 s, no editable (`Settings.kt:54`) | 3 | No tocar; exponer el valor por defecto | P2 | S | — |
| S9 | Escaneo de la pila de pesos con IA en la nube | E.1 (OBSERVADO clases; INFERIDO formato) | No existe | 4 | No traer (nube, coste, privacidad). Reconsiderar solo con OCR local | — | — | — |
| S10 | Importador con parsers de programas comerciales | C.2.7 (OBSERVADO) | Importador genérico (xlsx, ods, docx, csv, pdf y foto con OCR) y reconocimiento por título que ofrece la versión de KPKN ‡ | 4 (parsers) / 3 | No copiar parsers ni listas | — | — | — |
| S11 | Taxonomía: 22 grupos, 44 patrones, 148 acciones articulares (enums) | C.0 (OBSERVADO) | 21 músculos, 63 patrones, 15 articulaciones y 409 acciones en texto libre (recuento propio del catálogo) | 2 (acciones) | Normalizar las acciones articulares a un vocabulario controlado | P2 | M | Ninguna acción fuera del vocabulario; búsqueda y filtro por acción |
| S12 | Búsqueda de ejercicios con sinónimos (inglés / japonés) | C.2.7 (OBSERVADO) | Español e inglés, con sinónimos y tolerancia a errores ‡ | 3 | No tocar | — | — | — |

### 4.8 Otras capacidades del árbol Dart

| # | Capacidad de MacroFactor | Evidencia RE (nivel) | KPKN hoy | Cat. | Recomendación | Prio. | Esf. | Criterio de aceptación verificable |
|---|---|---|---|---|---|---|---|---|
| O1 | Exportación de datos (comida, métricas, entrenos, biblioteca) | Rutas `ui/more/data_export/*` (OBSERVADO) | Solo la copia JSON completa ‡ | 1 | Exportación CSV por dominio | P2 | S | Un CSV por dominio con fechas locales y unidades; se reimporta sin pérdida en una hoja de cálculo |
| O2 | Borrado por métrica y visibilidad de datos | Rutas `ui/more/data_delete/*`, `data_visibility/*` (OBSERVADO) | Borrado total ‡ | 2 / 4 | Baja prioridad | — | — | — |
| O3 | Ciclo menstrual (periodo) y su visibilidad | Rutas `experiences/period`, `period_editing` (OBSERVADO; INFERIDO propósito) | No (solo una pregunta del alta ‡) | 1 | Anotación opcional para interpretar el agua retenida. Es un dato sensible: decide el usuario | P2 | M | Opt-in explícito; los días anotados amplían la incertidumbre de la tendencia sin alterar los pesajes |
| O4 | Balance energético, progreso del objetivo, comparar métricas | Rutas `experiences/energy_balance`, `goal_progress`, `full_body` (OBSERVADO) | Balance diario (`NutritionViewModel.kt:481-517`), progreso y fecha estimada del objetivo (`BodyWeightSummary.kt:211-243`) | 3 | No tocar | — | — | — |
| O5 | Integraciones, Siri y atajos, Live Activities | Rutas (OBSERVADO) | Health Connect (flavor `health`) ‡, widget de nutrición ‡, notificación de descanso ‡ | 3 / 4 | Lo propio de iOS no aplica | — | — | — |
| O6 | Nutrition incluye entrenamiento (153 modelos, 39 servicios, 19 vistas) | §3.3 (OBSERVADO rutas) | KPKN integra ambos en una sola app (AUGE, reparto por entrenamiento) | 3 | Ventaja de KPKN: mantener | — | — | — |

### 4.9 Hallazgos laterales vigentes (no derivados de MacroFactor)

| Id | Hallazgo | Evidencia | Prio. | Esf. | Nota |
|---|---|---|---|---|---|
| H-a | El atajo de creatina publica en memoria antes de escribir en Room, sin manejo de error | `NutritionRepository.kt:203-207`; ‡ `NutritionViewModel.kt:330-348`, `:366-369` | P0 | S | Pasar por `saveNutritionLog` |
| H-b | Una fila de log ilegible deja vacía toda la carga de nutrición | ‡ `NutritionRepository.kt:1085`; ‡ `Entities.kt:243`; un test fija hoy el comportamiento ‡ (`NutritionRepositoryStartupTest.kt:209-228`) | P0 | S | Cuarentena por fila; cambiar ese test es una decisión (§7) |
| H-c | Las preferencias `nutrition_utensils` y `food_query_learning_v2` quedan fuera del backup | ‡ `NutritionRepository.kt:136-164`, `:1322-1335`; ‡ `data/settings/SettingsJsonBackup.kt` | P2 | S | — |
| H-d | AUGE: sueño fijo en 1,0 por diseño; nutrición apagada sin interruptor | `AugeRecoveryEngine.kt:164-172`, `:1571`; `Settings.kt:300` | — | — | Decisión de producto (§7) |
| H-e | `augeAutoDeload`, `restTimerDefaultSeconds` y `restTimerAutoStart` no tienen UI; `restTimerAutoStart` no tiene lector | `Settings.kt:54-55`; ‡ `Settings.kt:305` | P2 | S | — |
| H-f | Sugerencia contextual de 0,8× en la 2.ª serie | §4.5 P8 | P1 | S | Verificar en el dispositivo |
| H-g | Descarga nativa: la semana 6 recibe la misma carga capturada, con RIR 4 y las mismas reps | ‡ `NativeWorkoutProgressionRuntime.kt:1199-1217`; `NativeProfileSpec.kt:218-219` | P2 | S | Revisar la coherencia |
| H-h | `ProgramAnalyticsEngine` resuelve los ejercicios contra el catálogo por definición (`exerciseCatalogSnapshot()`, `data/exercises/ExerciseDatabase.kt:63`, `:114`), mientras el índice de VolumeView incluye las configuraciones v2 (`:72`, `:117`). Posible tarjeta «Balance y cobertura» vacía en programas v2 | `ProgramAnalyticsEngine.kt:625-633` | P0 (dentro de T2) | S | INFERIDO por lectura; verificar en el dispositivo |
| H-i | `catalogRevision` no cambia aunque cambie el contenido | §2 #11 | P2 | S | Gobernanza del catálogo |
| H-j | APK de terceros sin seguimiento ni regla de ignore en el checkout principal (`artifacts/macrofactor-reference-review-20261017/inputs/`) | `git status`: `??` | P0 (higiene) | S | Riesgo de commit accidental (§7) |

---

## 5. Fórmulas recuperadas como referencia de diseño

> **Aviso.** Lo que sigue describe el comportamiento observado de un producto de terceros. Sirve para entender qué problema resuelve cada pieza y para fijar órdenes de magnitud plausibles. **KPKN debe implementar versiones propias:** con métodos públicos (balance energético, Cunningham, EER, filtros de tendencia lineal local, curvas de %1RM de la literatura) y con parámetros calibrados y validados en simulación y con datos propios. No se deben copiar coeficientes ajustados (p. ej. 0,965 / 0,975; 0,0008 / 0,01 / 0,007; 0,582 / −0,327; 48,1 / 0,783) ni el código.

### 5.1 Estimador de gasto por ventana (núcleo V1/V2) — OBSERVADO

**Pasos observados**

1. **Ventana.** Se toma una ventana de N días (10 en la serie corta, 25 en la larga). Si menos del 85 % de los días está presente, la ventana se acorta de uno en uno, con un mínimo de 7; si ni así se cumple, queda «sin solución».
2. **Ingesta.** Ingesta media de los días presentes.
3. **Pendiente.** Mínimos cuadrados de la tendencia de peso frente al índice del día.
4. **Energía del cambio.** ΔE = pendiente × 7.700 si la pendiente es < 0, y × 4.400 si es > 0.
5. **Candidato.** Candidato = ingesta media − ΔE.
6. **Rampa.** Δ = candidato − valor anterior. Día < 6: anterior + 0,33·Δ; días 6-12: anterior + 0,66·Δ; día 13: el candidato; día ≥ 14: el candidato limitado a anterior ± paso (75 kcal en la serie corta, 30 en la larga).
7. **Suelo.** `max(750, 0,85·X)`, con X = BMR del día según la RE.
8. **Combinación de series.** (corta + larga) / 2, o (corta + 2·larga) / 3 cuando hay *crossover* (semántica INFERIDA).
9. **Alisado.** Tres EMA con α = 0,1.

**Ejemplos**

- Ingesta de 2.500 kcal y pendiente de −0,05 kg/día → 2.885 kcal.
- Con pendiente de +0,05 kg/día → 2.280 kcal.

**Por qué no copiar la asimetría.** Si el peso oscila de forma simétrica con pendientes ±a, sin cambio real de energía, el candidato medio queda en ingesta + 1.650·a. Con a = 0,03 kg/día, el gasto se sobreestima en ≈ 50 kcal/día. Es el sesgo que la documentación pública dice haber corregido en V3 (DOC), y el núcleo V3 ya no contiene 4.400 (OBSERVADO).

**KPKN hoy.** Ver §4.1, nota 1.

**Qué tomar.** La estructura: ventana con elegibilidad, pendiente por regresión sobre la tendencia (no extremos), densidad simétrica parametrizada, dos horizontes, rampa inicial, límite de cambio, suelo, pausa y calidad por estimación.

**Validación propuesta (banco de simulación, semillas fijas, 84-112 días por serie)**

| Escenario | Qué mide |
|---|---|
| S1 · Gasto constante + ruido de agua (σ 0,3-1,0 kg) con pesaje diario, 3 por semana e irregular | Sesgo y MAE en el tramo estable |
| S2 · Escalón de −300 y +250 kcal/día | Días hasta el 90 %; sobreoscilación |
| S3 · Oscilación simétrica sin cambio de energía | Sesgo por asimetría (debe ser < 10 kcal/día) |
| S4 · Días parciales (50 % registrado) marcados y sin marcar | Sesgo con y sin el estado del día |
| S5 · Huecos de 7-14 días | Fracción de pausas correctas; salto al reanudar |
| S6 · Cambio de objetivo con rebote de agua de 1-1,5 kg | Falsos ajustes en las 2 semanas siguientes |
| S7 · Corrección retroactiva de un día | Solo cambian las estimaciones posteriores a ese día |

**Umbrales iniciales sugeridos** (a fijar antes de ajustar): |sesgo| ≤ 50 kcal/día; MAE ≤ 150 kcal/día con pesaje diario; 0 inversiones de signo de la recomendación semanal causadas por un único pesaje de ±0,5 kg; ≥ 95 % de abstención correcta con datos insuficientes. Tope anti-sumidero: 3 rondas de ajuste de parámetros; si no se alcanzan, se documenta y decide el usuario.

### 5.2 Suelos y TDEE inicial — OBSERVADO

- **Piso de gasto:** `max(598, 48,1·W^0,783)`, con W = tendencia en kg. 60 / 80 / 100 kg → 1.187 / 1.487 / 1.771 kcal/día.
- **Suelo del núcleo:** `max(750, 0,85·BMR_día)`.
- **TDEE inicial:** (500 + 22·min(LBM, 100)) × (actividad + bono). Ejemplo: 80 kg con 20 % de grasa, actividad 1,4 y bono de 0,2 → 3.053 kcal/día.
- **KPKN** usa la EER DRI 2023, una ecuación pública y moderna, y un suelo del objetivo de 1.200 kcal.

**Recomendación:**

- Mantener la EER como punto de partida, con incertidumbre explícita.
- Añadir al estimador un suelo propio. Una opción es una fracción de la BMR con un mínimo absoluto, con parámetros elegidos por simulación.
- Usar Cunningham solo como candidato cuando haya un % graso medido.

**Validación:**

- Frecuencia de activación del suelo en series normales (< 1 %) y en casos límite (40-50 kg, déficits agresivos).
- Sensibilidad del punto de partida a ±5 puntos de error en el % graso.
- Días de convergencia desde puntos de partida erróneos en ±500 kcal.

### 5.3 Tendencia de peso: EMA y Kalman de 3 estados — OBSERVADO (constantes)

**EMA:** y = (1 − α)·y₋₁ + α·x, con α = 0,1 o 0,22222.

**Kalman V2:**

- Estado [nivel, pendiente, transitorio] (INFERIDO).
- φₛ = e^(−ln 2/30) = 0,97716; φ_f = e^(−ln 2/2) = 0,70711; c = (1 − φₛ)/(ln 2/30) = 0,98855.
- F = [[1, c, 0], [0, φₛ, 0], [0, 0, φ_f]].
- Q = diag(0,0025; q₂²(1 − φₛ²); q₃²(1 − φ_f²)), con q₂ = clamp(0,0008·W; 0,04; 0,12) y q₃ = clamp(0,01·W; 0,6; 1,4).
- σ_obs = clamp(0,007·W; 0,45; 0,85).
- Nivel inicial = mediana de los primeros 7 días (con ≥ 3 pesajes).
- Robustez: MAD × 1,4826 en una ventana de 21 días.
- Para W = 80: Q₁₁ = 1,85·10⁻⁴, Q₂₂ = 0,32, σ_obs = 0,56 kg.
- El paso de actualización y el suavizador siguen DESCONOCIDOS.

**Qué tomar.** La idea, que es estándar: un modelo de tendencia lineal local, que separa la pendiente (grasa y tejido) de un componente transitorio que decae en días (agua), con ruido proporcional al peso y pesajes atípicos amortiguados.

**Validación:**

- Retardo en detectar un cambio de pendiente.
- RMSE de la pendiente según la frecuencia de pesaje (diaria, 3 por semana, irregular).
- Robustez a atípicos (±2 kg en un día).
- Agua periódica de 28 días con amplitud de 0,5-1 kg.
- Huecos.
- Comparadores: la EWMA actual de KPKN, una regresión robusta y el candidato.

### 5.4 Curva x-RM — OBSERVADO

**Fórmulas**

- e1RM = W / (0,2 + 0,8·e^(n−1)), con n = reps + RIR.
- W(n) = e1RM·(0,2 + 0,8·e^(n−1)).
- e = 0,965 para el tren superior y 0,975 para el resto.

**%1RM por número de reps hasta el fallo**

| n | Superior (0,965) | Resto (0,975) | Brzycki | Epley |
|---|---|---|---|---|
| 5 | 89,4 % | 92,3 % | 88,9 % | 85,7 % |
| 10 | 78,1 % | 83,7 % | 75,0 % | 75,0 % |
| 15 | 68,6 % | 76,1 % | 61,1 % | 66,7 % |

**Ejemplo con RIR.** 100 × 5 @ RIR 2 (n = 7): e1RM de 118,2 kg (superior) o 112,7 kg (resto). KPKN calcula 112,5 kg para ambos, porque ignora el RIR. Luego su prescripción para 5 @ RIR 2 da 93,75 kg (93,8 tras redondear a 0,1 kg), un 6 % menos de lo que se acaba de hacer. Es un sesgo conservador que se acumula (demostración algebraica sobre `Calculations.kt:60-106`, no observada en ejecución).

**Qué tomar:**

- Un único modelo en el que la estimación y la prescripción sean inversas exactas.
- Uso del RIR, con una política declarada para el RIR ausente y para el fallo (= RIR 0).
- Curvas por región o por patrón, ajustadas con los registros propios.

**Validación:**

- Predecir las reps de una serie real a partir del e1RM de sesiones anteriores, dejando una sesión fuera; MAE de reps por región y por RIR, frente a la híbrida actual.
- Test de ida y vuelta.
- Fiabilidad del RIR declarado (series marcadas como fallo frente a RIR 0).

### 5.5 Objetivo automático y ranking (C.F9, C.F4) — OBSERVADO (estructura)

**Algoritmo**

1. Ordenar la lista de pesos disponibles.
2. Atajo: mantener el peso anterior si sus reps caen dentro del rango.
3. Peso «ideal» = el menor peso disponible ≥ W(T) − offset.
4. Primera pasada: candidatos (n, peso) para n entre el RIR y T, uno por peso, filtrados por el rango de reps.
5. Segunda pasada: n entre T y RIR + 20, con pesos distintos del ideal.
6. Desempate: puntuación = abs(n − T)·(abs(W(n) − offset − peso) + 1).

**Ejemplos (RE)**

- Discos de 2,5 kg, e1RM de 111,9 y T = 7 → (7, 95 kg).
- Con solo 90 / 100 / 110 kg disponibles → (5 reps totales, 100 kg) = 3 reps @ RIR 2.

**KPKN hoy.** Doble progresión: sube al menor escalón disponible.

**Qué tomar.** Generar candidatos sobre los pesos disponibles y puntuar la distancia al esfuerzo objetivo. Así un escalón grande se compensa con las reps, en vez de quedar en «elige una carga un poco mayor».

**Validación.** Simular un levantador con una curva verdadera y progreso conocido, con material discreto (discos de 1,25 kg, mancuernas de 2 en 2 y de 2,5 en 2,5 kg, máquina de 5 en 5 kg) durante 12 semanas. Medir:

- 100 % de propuestas cargables.
- % de propuestas dentro del rango de reps.
- Error de esfuerzo (RIR real − RIR objetivo).
- Oscilaciones entre opciones.
- Semanas de estancamiento frente a la regla actual.

### 5.6 Reps fatigantes y fatiga acumulada — OBSERVADO

**Fórmulas**

- f = c + (reps^0,582·e^(−0,327·RIR))^(1/0,582) = c + reps·e^(−0,5619·RIR).
- Fatiga acumulada = −2,481·x^0,582 / 100.

**KPKN** ya modela el esfuerzo con AUGE: RPE efectivo, series efectivas y reps efectivas por tejido.

**Recomendación.** No sustituir AUGE. A lo sumo, contrastar la forma «reps × e^(−k·RIR)» como hipótesis para un indicador de reps estimulantes. Solo admite tests de consistencia, porque no hay verdad de referencia.

### 5.7 Calentamiento — OBSERVADO (aritmética); INFERIDO (unidad)

**Reglas**

- Se recorren [0,4; 0,6; 0,8; 0,95] mientras sean menores que `oneRepMaxRatio`.
- El primer escalón es 0,4; cada siguiente = anterior + 0,6·(ratio − anterior).

**Ejemplos.** Ratio 0,85 → [0,40; 0,67; 0,778]. Ratio 0,97 → [0,40; 0,742; 0,879; 0,934]. Ratio 0,5 → [0,40].

**Dos lecturas posibles de la unidad (sin resolver):**

- Si son fracciones del e1RM, con una serie de trabajo al 85 % del e1RM, los escalones equivalen al 47 / 79 / 92 % del peso de trabajo.
- Si son fracciones del peso de trabajo, son el 40 / 67 / 78 %.

**KPKN** ya resuelve el problema con rampas por nivel, movilidad y cobertura articular (categoría 3).

**Refinamiento opcional:** recortar escalones en la sesión cuando el peso real quede lejos del e1RM.

**Validación:** tiempo total de calentamiento, que ningún escalón supere el peso de trabajo, y que todos sean cargables con el inventario.

### 5.8 Descarga — OBSERVADO

**Reglas**

- Series = round(0,8·n), mínimo 1.
- Reps mín./máx. × 0,8, redondeadas.
- Fallo → serie estándar.
- El ciclo base es el primero o el último, según `WorkoutDeload`.
- Ejemplo: 5 series × 8-10 reps → 4 series × 6-8 reps.

**KPKN** (categoría 3) ya tiene descarga programada y reactiva con AUGE. Solo conviene revisar H-g.

### 5.9 Ponderación muscular — OBSERVADO

1,0 si el grupo está en la primera lista del ejercicio, 0,5 si está en la segunda y 0 en otro caso. KPKN usa 1,0 / 0,5 / 0,4 / 0,1 por rol, con modos Directo / +Secundario / +Estabilizador.

**Recomendación.** Conservar la ponderación de KPKN. El trabajo está en la contabilidad (T2): tests de reconciliación, de modo que la suma de contribuciones = el total mostrado en cada modo.

### 5.10 Reglas de fecha del check-in — OBSERVADO; semántica INFERIDA

**Reglas**

- Sin programa → none; bandera de descartado → dismissed; estilo manual → none.
- En otro caso, se toma un ancla en el día de la semana elegido (lunes por defecto): si han pasado más de 2 días → now; si no → future.
- Cadencia de 7 días, con una regla «< 3 días» para la siguiente fecha.

**Recomendación.** Reglas propias y simples:

- Día configurable, con el inicio de semana del usuario por defecto.
- Disponible hasta N días después.
- Posponer = mover al día siguiente.
- Idempotencia por semana.

---

## 6. Hoja de ruta

### 6.1 Orden de dependencias

```
Ola 0 (en paralelo, archivos disjuntos):  H1 · N1 · T1 · T2 · T6
Ola 1:  N2 (necesita N1 y H1) · T3 (necesita T2)
Ola 2:  T4 (necesita T1 y T3) · T5 (necesita T2 y T3) · N3 (necesita N2)
Ola 3:  N4 (necesita H1, N2 y N3) · paquetes P2 (§6.3)
```

### 6.2 Paquetes repartibles a subagentes

Cada paquete es dueño exclusivo de sus archivos dentro de su ola. Los archivos «calientes» (rutas, base de datos, repositorio y pantallas de nutrición) tienen un único dueño por ola.

| Paquete | Contenido | Archivos en propiedad (rutas KPKN) | Depende de | Cierre (criterio verificable) | Prio. | Esf. |
|---|---|---|---|---|---|---|
| **H1** Higiene de datos de nutrición | Creatina por `saveNutritionLog`; decodificación fila a fila con cuarentena | `data/repository/NutritionRepository.kt` (`addNutritionLog`, `publishUserState`), `data/db/Entities.kt` (conversores `toNutritionLog` y afines), `screens/nutrition/NutritionViewModel.kt` (`quickAddCreatine`, `addLog`) + tests | — | Un fallo de Room al marcar creatina no muestra éxito. Una fila corrupta no oculta las sanas (se reescribe `NutritionRepositoryStartupTest.kt:209-228` tras decidirlo, §7) | P0 | S |
| **N1** Estimador de gasto + tendencia robusta | Dominio puro: estado del día (modelo), elegibilidad, tendencia (nivel, pendiente, transitorio), estimador, banda, estados y calidad; banco de simulación | Archivos nuevos en `domain/nutrition/expenditure/` y `src/test/.../domain/nutrition/expenditure/`. No toca archivos existentes | — | Banco de §5.1 en verde con los umbrales fijados. Invariantes: una comida futura no cambia el gasto de hoy; editar un día pasado solo recalcula desde ese día | P0 | L |
| **T1** «Mi material» | Pantalla de inventario (barra, discos con cantidad por lado, mancuernas, kettlebells, máquinas por configuración); usar el inventario resuelto también en el runtime nativo | Nuevos `screens/settings/equipment/*`; `data/repository/ProgramRepository.kt:994` (una línea); registro de ruta en `navigation/Navigation.kt` y `MainActivity.kt` (dueño de las rutas en la Ola 0) | — | Criterio de §4.5 P3. El backup conserva el inventario | P0 | M |
| **T2** Ventana y contabilidad de volumen | Objeto de ventana común (calendario, últimos 7 / 28 días, promedio frente a total); corrige V3 y H-h | `domain/training/VolumeCalculator.kt`, nuevo `domain/training/TrainingWindow.kt`, `screens/programdetail/components/VolumeView.kt`, `screens/programdetail/design/ProgramVolumeRow.kt`, `screens/programdetail/ProgramDetailViewModel.kt` (`muscleCdbsStatus`), `domain/training/ProgramAnalyticsEngine.kt`, `domain/auge/OvertrainingDetector.kt`, `screens/home/HomeViewModel.kt` (consumidor) + tests | — | Comprobaciones de §4.6 (V3) | P0 | M |
| **T6** Sustituciones en vivo | «Sugeridos» con `AprendeSimilarityEngine` y filtro de material del lugar; la lista completa debajo | `screens/sessioneditor/components/ExercisePickerV2Catalog.kt` (orden), `screens/workout/WorkoutStructureSheetsHost.kt` | — | §4.7 S6 | P1 | S |
| **N2** Estado del día + check-in semanal | Migración de Room a v30 (estado por fecha); tarjeta de check-in en Nutrición; módulos de pausa, parciales, ayuno, pesaje y actualización; aplicar con `NutritionPlanCommitCoordinator`; sustituye el uso del motor actual | `data/db/Entities.kt`, `data/db/Daos.kt`, `data/db/KpknDatabase.kt` (+ esquema 30), `data/models/NutritionModels.kt`, `screens/nutrition/NutritionScreen.kt` (tarjeta), nuevos `screens/nutrition/checkin/*`, `screens/nutrition/NutritionCalibrationViewModel.kt` y su Screen, `data/repository/NutritionCalibrationRepository.kt`, `data/repository/NutritionPlanCommitCoordinator.kt` | N1, H1 | Criterios de C1-C4 y G7, G8 y G11. Test de migración 29 → 30 y del backup | P0 | M-L |
| **T3** Modelo de fuerza | `StrengthModel` versionado (reps + RIR, curvas propias); adaptador; migración de los ~30 consumidores; decisión sobre `oneRMFormula`; verificación de H-f | Nuevo `domain/performance/StrengthModel.kt`, `domain/calculations/Calculations.kt`, `domain/workout/LoadSuggestionEngine.kt`, `screens/workout/WorkoutLoadSuggestionController.kt`, y los llamadores de `calculateHybrid1RM` que no toque T2 | T2 | Test de ida y vuelta (§4.5 P1). Test de arquitectura: ninguna llamada a `calculateHybrid1RM` fuera del adaptador | P1 | M |
| **T4** Objetivo automático | Candidatos (reps, peso) sobre el inventario + puntuación propia; texto con kg | `domain/training/NativeWorkoutProgressionRuntime.kt`, `domain/training/NativeProgressionText.kt` + tests | T1, T3 | §4.5 P2 | P1 | M |
| **T5** Panel muscular y progreso por ejercicio | Panel por ventana con desglose; modo volumen en la silueta; vista de progreso con récords | Nuevos `screens/progress/*`; rutas (dueño de las rutas en la Ola 2); enlaces desde la ficha y desde Perfil | T2, T3 | §4.6 V2, V4 y V5 | P1 | M |
| **N3** Escáner + alimentos propios | Dependencia de escaneo en el dispositivo; vista de escáner; búsqueda local por código; alta y edición de alimentos propios | `app/build.gradle.kts`, nuevos `screens/nutrition/scan/*` y `screens/nutrition/customfood/*`, `data/db/Daos.kt` (consulta por código), `data/repository/NutritionRepository.kt` (búsqueda), `screens/nutrition/components/FoodLoggerDrawer.kt` (botón) | N2 (Daos), H1 | §4.4 D6 y D7 | P1 | M |
| **N4** Reutilización del diario | Copiar a otra fecha o a varias; «repetir ayer»; plantillas; entrada rápida | `screens/nutrition/NutritionScreen.kt`, `NutritionViewModel.kt`, `screens/nutrition/design/NutritionFoodMenu.kt`, `domain/nutrition/MacroCalculator.kt` (`duplicateLog` → copia con fecha), `FoodLoggerDrawer.kt` (entrada rápida) | H1, N2, N3 | §4.4 D1, D3 y D4 | P1 | S-M |

### 6.3 Paquetes P2 (Ola 3, según capacidad)

- Recetas con rendimiento (D5).
- Estado de nutrientes, contribuyentes y DRI (D11, D12).
- FTS medido (D8).
- Hábitos (C7).
- Exportación CSV (O1).
- Ciclo menstrual (O3, si el usuario lo decide).
- Myo-reps (S7).
- Discos por lado en la tarjeta (P5).
- Refinamiento del calentamiento (S2).
- Vocabulario de acciones articulares (S11).
- Tendencia como cifra principal (T5 de §4.2).
- Hora de la comida y favoritos visibles (D13).
- Contexto histórico en el punto de partida (G13).
- Copia de seguridad de preferencias (H-c).
- Interruptores sin UI (H-e).
- Gobernanza de `catalogRevision` (H-i).

### 6.4 Reglas para los subagentes

- Clean Architecture del repo: `domain/` sin `android.*`; `StateFlow` de solo lectura; I/O en `Dispatchers.IO`.
- Tests espejo en `android-native/app/src/test/...`. Tareas Gradle del flavor `base`: comprobar los nombres en `app/build.gradle.kts` antes de elegir tareas.
- «Refinar, no quitar»: ningún paquete elimina ni esconde funciones existentes. Lo huérfano se conecta o se documenta su retirada como decisión del usuario.
- Validación funcional en el teléfono del usuario, según la práctica del proyecto. Una compilación no cierra la aceptación.

---

## 7. Riesgos, límites legales y éticos, y decisiones del usuario

**Límites legales y éticos**

- **No copiar** código, textos, imágenes, catálogos, prompts ni los parsers o listas de programas comerciales. Las notas de la RE describen comportamiento; la implementación debe partir de este informe y de fuentes públicas, no de los desensamblados.
- Los coeficientes ajustados de MacroFactor son, como mucho, una comprobación de plausibilidad. Los valores que se envíen deben salir de la validación propia.
- Los términos de uso de MacroFactor pueden restringir la ingeniería inversa. La RE fue de solo lectura y no se redistribuye: `evidencia/` contiene solo notas y herramientas propias. La Base RE (con APK, `.so` y desensamblados) es una carpeta temporal de sesión y **no debe copiarse al repositorio**. Usar estos hallazgos es una decisión del usuario.
- No usar marcas ni nombres de MacroFactor en la app.
- Privacidad: todo en local; nada de IA en la nube por defecto. El ciclo menstrual es un dato sensible.

**Riesgos y mitigación**

| Riesgo | Mitigación |
|---|---|
| Un estimador mal calibrado propone objetivos dañinos | Suelos y topes; confirmación del usuario; estado «pausado» ante falta de datos; banco de simulación con umbrales fijados antes de ajustar |
| Cambian cifras visibles (volumen, e1RM, récords) | Versionar el cálculo, rotular la ventana y la unidad, avisar en las notas de versión; nunca reescribir registros |
| Migración de Room v30 | Test de migración y de copia de seguridad antes de integrar |
| Sumidero de tiempo en la calibración | Máximo 3 rondas de ajuste; si no se llega, documentar y decidir |
| Choques entre subagentes en archivos calientes | Matriz de propiedad por ola (§6.2) |
| APK de terceros sin seguimiento en el checkout principal (H-j) | Añadir una regla de ignore o moverlos fuera del repo antes de cualquier `git add -A` |

**Decisiones que son del usuario (con la recomendación del autor)**

1. Nueva dependencia de escaneo de códigos de barras en el dispositivo. Recomendación: un modelo empaquetado en la app, que funcione sin red ni servicios de Google, aceptando el aumento de tamaño del APK.
2. Aplicar el check-in: con confirmación (recomendado) o automático como opción.
3. Día del check-in por defecto. Recomendación: el inicio de semana del usuario. MacroFactor usa el lunes (OBSERVADO).
4. AUGE: ¿mantener el sueño fuera de τ y activar o no el multiplicador nutricional (hoy apagado y sin interruptor)?
5. Nuevo modelo de e1RM. Recomendación: versionarlo y recalcular solo la presentación, sin tocar los registros.
6. Ciclo menstrual (opcional y sensible) e importación de recetas por URL (implica red iniciada por el usuario).
7. Umbrales del banco de simulación (§5.1): aprobarlos antes de ajustar.
8. Cambiar el test que hoy fija la carga vacía ante una fila corrupta (H-b).
9. Código huérfano (`diffSessionPlan` / `revertPlanAspect`, cálculo de readiness sin consumidor): conectar o retirar.

---

## 8. Apéndice de evidencia

**Base RE (temporal de sesión; puede desaparecer):**
`C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit--claude-worktrees-kpkn-macrofactor-audit-1344ab\c9399af0-2874-4a5d-a3d2-1d801554de64\scratchpad`

| Material | Ruta dentro de la Base RE | ¿Copiado a `evidencia/`? |
|---|---|---|
| Formato del snapshot y método | `re\00_formato_snapshot.md` | Sí |
| Límites y peticiones | `re\99_limites_y_peticiones.md` | Sí |
| Notas por sistema (A, A-fórmulas, B, C, C-fórmulas, D, E) | `re\sistemas\*.md` | Sí (`evidencia/sistemas/`) |
| Herramientas de la RE (`dartsnap`, `dump_model`, `codescan`, `report`, `digest_text`, `disasm`) | `re\tools\*.py` | Sí (`evidencia/tools/`) |
| Scripts propios de la Fase A (`pool_peek`, `pool_pairs32`, `catalog_counts`) | `verif_audit\*.py` | Sí (`evidencia/verificacion_fase_a/`; añadido por el autor, solo biblioteca estándar y lectura) |
| Modelos completos | `re\out_nu\`, `re\out_wk\` (incl. `pool.json`, `classes.json`) | No (grandes) |
| Clases por app | `re\clases_nu.json`, `re\clases_wk.json` | No (grandes) |
| Listados desensamblados | `re\dis_nu\dis_<addr>.txt`, `re\dis_wk\dis_<addr>.txt` | No |
| Rutas Dart y cadenas | `out\nu_paths.txt`, `out\wk_paths.txt`, `lib\*.strings.txt` | No |
| Binarios (APK, `libapp.so`, `libflutter.so`) | `nu\`, `wk\`, `lib\`, `nu.zip`, `wk.zip` | **No (no redistribuir)** |

**Listados usados en la Fase A** (`re\dis_nu\` y `re\dis_wk\`):

| App | Listados |
|---|---|
| Nutrition | `dis_142be70` (piso), `dis_12c0dac` (TDEE inicial), `dis_12c2174`, `dis_12c2e8c`, `dis_12c3038`, `dis_12c0a30` y `dis_12c1364` (núcleo V2), `dis_ab252c` y `dis_ab294c` (EMA), `dis_ab0fbc`, `dis_ab1788`, `dis_ab16f4`, `dis_aafb0c`, `dis_ab19b4`, `dis_ab1470` y `dis_aa6614` (Kalman), `dis_12c46ac` (constantes de V3) |
| Workouts | `dis_b6b3d8` (x-RM), `dis_c811c0` (reps fatigantes), `dis_c8c454` (calentamiento), `dis_c15ffc` y `dis_c1654c` (descarga), `dis_b78440` (ponderación) |

**Uso de los scripts** (Python con `-I`, desde una carpeta distinta de la Base RE):

- `python -I pool_peek.py <out_nu\pool.json> 32108 32109`
- `python -I pool_pairs32.py <out_wk\pool.json> 31639 31641`
- `python -I catalog_counts.py <android-native\app\src\main\assets\exercise_catalog_v2.json>`

**Informe previo:** `C:\Users\valen\Documents\KPKNFit\artifacts\macrofactor-reference-review-20261017\` (README y `research\*.md`).

**Datos de KPKN citados**

- HEAD `8ac57c9d4`; `a647d0499` es ancestro (805 commits de diferencia).
- Refactors posteriores al corte previo: `d978c8a4b` (programdetail: código muerto fuera, incluidos `ProgressView` y `HistoryView`), `0f649f72c` (nutrición: código muerto fuera, incluida la vista de progreso de micronutrientes), `6218d4205` (nueva pestaña Volumen).
- Catálogo `android-native/app/src/main/assets/exercise_catalog_v2.json`: SHA-256 `81DB1F950148A7769B7D1D1DD8F066746439EFE726CA00D35D87D52119BCF91C`; 2.533.748 bytes; `catalogRevision` `v2-approved-2026-09-29-a`.

| Elemento del catálogo | Total |
|---|---|
| Familias | 96 |
| Definiciones | 228 |
| Configuraciones | 553 |
| Músculos | 21 |
| Patrones de movimiento | 63 |
| Articulaciones | 15 |
| Acciones articulares (texto libre) | 409 |
| Equipos | 19 |
| `performanceProfileId` distintos | 516 |
