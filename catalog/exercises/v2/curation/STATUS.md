# Estado de ejecución — catálogo de ejercicios

## Estado vigente — re-curaduría integral (desde 2026-10-01)

Todo lo que sigue a esta sección es el **registro histórico** de las curadurías
anteriores (v3 a v7.2). Nombra archivos y campos que ya no existen:
`editorial_briefs.json`, `muscleNotes`, `techniqueSummary`, `benefits`, etc. Lo
vigente es esto:

- Revisión del catálogo `v2-approved-2026-09-29-a` (sin cambio; los ids tampoco
  cambian, el resolver rechaza ejercicios guardados con otra revisión) y
  ontología `wikilab-v3-2026-08-08`.
- 96 familias, 201 definiciones, 523 configuraciones, 411 pares definición ×
  implemento. SHA-256 canónico compartido
  `6bdb9e599685132d226a9e6bccad96230874ad4e1717e55008c4e22cf33d9ae0`.
- Fuente única de autoría: `curation/fichas/<familyId>.json` (una por familia,
  96). Se copia con `scripts/catalog_v2_apply_fichas.py`; el flujo completo y las
  reglas están en `EDITORIAL_GUIDE.md`. El gate falla si `source/` difiere de lo
  que producen las fichas.
- Estado de las fichas: 178 definiciones `LEGACY` y **23 `CURATED`**. Piloto
  (2026-10-01): `seal_row`, `pull_up` y `glutes_clamshells_banda`. Lote 1, pecho
  (2026-10-02, 20 definiciones, aplicado): `floor_press`, las aperturas
  (`decline_chest_fly`, `flat_chest_fly`, `incline_chest_fly`, `reverse_pec_fly`),
  los presses (`bench_press`, `decline_bench_press`, `incline_bench_press`,
  `paused_bench_press`) y los empujes de `upper_horizontal_push`. El paso a
  `CURATED` se hace por lotes y queda registrado aquí.
- `editorial_briefs.json` y su copia `.bak.2026-08-08` se eliminaron: sus 201
  definiciones eran idénticas al contenido de las fichas esqueleto.
- Los 8 generadores por plantilla (`build_catalog_v2_*`, `curaduria_v3` a `v6`,
  `seed_catalog_editorial_briefs`) viven en `scripts/legacy/` y no corren sin
  una confirmación explícita (`_legacy_guard.py`).
- `quality_lexicon.json`, `quality_allowlist.json`, `anatomy_rules.json` y
  `QUALITY_BASELINE.md` pertenecen a `scripts/catalog_v2_quality_audit.py`
  (auditoría de solo lectura; el baseline es la medición **previa** a retirar los
  campos, por eso sigue midiendo textos que el catálogo ya no contiene).

### Campos retirados del esquema y prueba de no uso (F1)

Retirar significa: no se escribe, no se guarda en `source/`, no se compila en el
asset de runtime y no lo lee Android, iOS ni el backend. La lista única es
`scripts/catalog_v2_retired_fields.py` (24 rutas); el compilador, el gate, el
backend (copia literal, sincronizada por prueba) y la prueba de Android
`ExerciseCatalogRetiredKnowledgeTest` rechazan cualquier carga que las traiga.

Método de la prueba de no uso: (1) las líneas eliminadas por F1 en código de
producción (`git diff HEAD` sobre `android-native/app/src/main`,
`ios-native/KPKNFit/KPKNFit` y `backend/`) se clasificaron en declaraciones del
modelo y lecturas; (2) se buscó cada nombre de campo en el código de producción
que queda. Resultado por grupo:

| Campos retirados | Lectores de producción antes de F1 | Efecto visible | Decisión |
| --- | --- | --- | --- |
| `family.description`, `*.evidence.rationale` | Ninguno (solo validación de formato) | Ninguno | Retirado |
| `profile.benefits`, `techniqueSummary`, `variantRationale` | Validación del loader de Android y métricas de `AprendeCatalogAudit`; armado de `richMetadata.editorial`; validación del backend. Ninguna pantalla los muestra | Ninguno | Retirado |
| `profile.commonMistakes`, `richMetadata.coaching` | Android: `ExerciseCatalogV2LegacyAdapter` los copiaba a `ExerciseMuscleInfo.commonMistakes`, que solo agrega `ExerciseMatchEngine` para sugerir un ejercicio personalizado nuevo y se guarda sin mostrarse. iOS: lo mismo en `ExerciseDatabase` y `ExerciseMatchEngine` | Ninguno: ninguna pantalla muestra «errores comunes» del catálogo | Retirado |
| `profile.muscleNotes`, `jointInvolvement[].note` (y su espejo en `richMetadata.anatomy`) | Solo validadores (loader de Android, backend, gate) | Ninguno | Retirado |
| `richMetadata.editorial`, `richMetadata.safety` | Armado en `toRichMetadata` y validadores | Ninguno | Retirado |
| `anatomy.targetRegions`, `muscleLengthBias`, `stabilizationDemand`; `biomechanics.rangeOfMotion`, `relevantTendons` | Validadores; iOS copiaba `rangeOfMotion` a `peakTensionPoint`, que ningún código lee | Ninguno | Retirado |
| `programming.suitableRepRanges`, `recoveryCost`, `setupTransitionCost` | Solo validadores | Ninguno | Retirado |
| `programming.objectives`, `programming.splitSuitability` | iOS: alimentaban `functionalTransfer` y `sportsRelevance`, que lee `inferTransferLabel` (texto de justificación de ejercicios sustitutos) | **iOS**: sin esos campos la frase cae en la frase por región (antes eran 514 frases de plantilla distintas) | Retirado; cambio visible escalado al dueño del producto |

Campos que **se conservan** porque tienen lector: `programming.indicativeRestSeconds`
(iOS: asistente de sesión y analítica), `programming.requiredEquipment` (Android:
compatibilidad de planes), `programming.role` y `biomechanics.stability` (iOS:
proyección) y `replacement.preservesIntent` (Android: similitud entre ejercicios;
ahora se deriva como `<patrón>:<músculo primario>`).

Campos **sin lector pero conservados** a la espera de decisión del dueño del
producto: `anatomy.volumeContribution`, `programming.fatigueCost` y
`replacement.compatibleEquipmentIds`. Los espejos `anatomy.jointActions` y
`biomechanics.relevantJoints` también se conservan, pero ya se derivan de la
ficha y no se escriben a mano.

Compatibilidad con datos ya guardados en el teléfono: las instantáneas de
historial y el `catalogRichMetadataJson` persistidos antes de F1 contienen
claves retiradas; los decodificadores de Android las ignoran
(`ignoreUnknownKeys`), y `ExerciseCatalogV2LegacyPersistedDataTest` lo fija.

### Piloto de re-curaduría (F2, 2026-10-01)

Tres definiciones pasaron de `LEGACY` a `CURATED`, con ficha completa (`public`,
`technique`, `anatomy`, `visual`, `sources`), aplicadas y compiladas:

| Definición | Qué cambió | Evidencia |
| --- | --- | --- |
| `seal_row` | Se retiran los erectores como músculo de trabajo (el torso apoyado sostiene la columna) y se añade el antebrazo como estabilizador del agarre. La descripción deja de atribuir el tope de la barra a la variante con mancuernas. | Regla `rows.chest-supported-spine` |
| `pull_up` | El bíceps sube de estabilizador a secundario en todos los agarres, también en el supino: no pasa a principal, porque el dorsal sigue por encima. Entran pectoral, erectores y antebrazo como estabilizadores. Salen los romboides (sin medición) y el reparto inventado por ancho de agarre. | Youdas 2010 (PMID 21068680), Dickie 2017 (PMID 28011412). Regla `vertical-pull.elbow-flexors` |
| `glutes_clamshells_banda` | El glúteo medio queda principal por su función abductora, no porque domine el EMG, y el glúteo mayor queda secundario. Entran los flexores de cadera como secundarios (54 % frente a 33 % y 34 %). Sale la sacroilíaca: ninguna fuente la midió en la almeja. | Distefano 2009 (PMID 19574661), McBeth 2012 (PMID 22488226), Willcox 2013 (PMID 23485733). Regla `hip.abductors-gluteus-medius` |

Las 17 fuentes citadas tienen prueba offline en `curation/sources_verified.json`.
Estos tres cambios de criterio están **pendientes de confirmación del usuario**.

---


Fecha de corte: 2026-08-10 (curaduría editorial v7.2 — copy humano editorial)
Revisión: `v2-approved-2026-08-10-c`
Hash canónico compartido: `20ecd23cb4766c341236e09d336bf1c3d3db3041ec6d8b3dd568de124acc0aa5`

> Actualización de inventario — 2026-09-15: se retiraron las configuraciones
> `decline_bench_press__machine`, `decline_bench_press__cable`,
> `decline_chest_fly__machine` y `decline_chest_fly__cable`. La fuente y los
> artefactos runtime actuales contienen 96 familias, 195 definiciones y 511
> configuraciones; su SHA-256
> de artefacto es `d229f99ad5779d881cbf2f22d1d307d10d489a8b3bd747e0342b9d182dd95d6e`.
> El resto de este documento conserva el registro histórico del corte v7.2.

> Corrección de copy — 2026-09-27 (T-041-r4): la configuración
> `tren_superior_press_banda_resistencia__default` es band-only declarada
> (`equipmentId=band`, `requiredEquipment=["band"]`, descripción de definición
> "sin banco ni pesas"), pero su `techniqueSummary`, `variantRationale` y
> `setupCues` describían bandas atadas a una barra. Se alineó ese copy y sus
> espejos exactos con la identidad band-only ya declarada; no se cambió
> implemento, roles, acciones, calificaciones, programación ni identidad, ni se
> añadió o retiró ningún ejercicio. Gate global: 2652 fallos, sin cambios.

## Curaduría v7.2 (2026-08-10): estructura editorial humana aprobada

- Las 196 descripciones de definición se reescribieron a mano con la estructura
  aprobada por el dueño del producto: (1) introducción con el nombre del
  ejercicio, tipo de movimiento, músculos y marco editorial; (2) ejecución
  contada en tono editorial con el implemento principal integrado; (3)
  mención opcional de 1-2 alternativas con su efecto real y transición
  natural (sin listas robóticas de implementos); (4) veredicto dedicado.
- La descripción de definición abre con el nombre del ejercicio (R11 v7.2);
  las configuraciones siguen sin repetirlo y pasan a ser líneas de matiz:
  esencia del movimiento (escrita por ejercicio) + efecto real de los chips.
- Se relajaron dos gates: nombre canónico permitido en definiciones y formas
  reflexivas/descriptivas como "se ejecuta" permitidas (imperativos fuera).
- Verificación anti-reciclaje: 0 frases de 6+ palabras compartidas entre
  definiciones; 0 implementos mencionados que el ejercicio no tenga; 0
  imperativos; aperturas únicas en definiciones y configuraciones.

## Curaduría v7 (2026-08-10): copy humano editorial — descripciones reescritas

- `editorial_briefs.json` reescrito con calidad humana: 196 descripciones de
  definición con estructura de 3 frases (qué es, qué trabaja + implementos
  disponibles, veredicto dedicado) y 518 líneas de matiz por configuración
  (variante corta y dedicada por implemento/agarre). Se elimina el relleno
  "una diferencia concreta para repartir el esfuerzo durante la serie", los
  benefits duplicados y los veredictos reciclados.
- Cada descripción menciona al ejercicio sin repetir el nombre canónico, respeta
  los implementos reales de cada definición (82 con múltiples implementos) y
  aporta un veredicto tipo "gran constructor de..." dedicado y no genérico.
- El gate comprueba cobertura exacta, igualdad con el perfil, ausencia de
  boilerplate y unicidad de la primera frase (196 + 518 aperturas únicas).

## Curaduría v6 (2026-08-08): briefs dedicados por ejercicio y configuración

- `editorial_briefs.json` es la fuente autoral de las 196 definiciones y 518
  configuraciones. Cada opción tiene descripción, beneficios, técnica y
  justificación propios; no se deriva texto visible desde un patrón global.
- Se eliminó del flujo editorial la apertura repetida de los remos y de los
  demás patrones. El gate comprueba cobertura exacta, igualdad con el perfil,
  ausencia de boilerplate y unicidad de la primera frase.
- La pasada solo reemplaza copy y cues editoriales; conserva las fichas
  `muscleNotes` y `jointInvolvement` ya validadas.

## Curaduría v5.1 (2026-08-08): lectura accesible y tarjeta ordenada

- Se reescribieron las 196 descripciones de definición y las 518 fichas de
  configuración con frases más directas, sin repetir el nombre canónico dentro
  del cuerpo y sin presentar un simple cambio de implemento como una variante.
- Se normalizó la capitalización de descripciones, beneficios, técnica,
  músculos, articulaciones, acciones y etiquetas compactas.
- La tarjeta expandida ahora coloca primero los chips de opciones. Descripción,
  Técnica, Involucramiento Muscular e Involucramiento Articular son secciones
  independientes, cerradas por defecto y abiertas solo al tocarlas.
- El gate estricto bloquea nombres repetidos, textos visibles que comienzan en
  minúscula y revisiones de perfil desincronizadas.

## Curaduría v5 (2026-08-08): ficha específica por variante e involucramiento articular

- Las 518 configuraciones tienen una descripción editorial propia que combina
  el movimiento, el implemento, la posición o agarre seleccionado, el beneficio
  de esa variante y una técnica breve; ya no se describe la opción como un mero
  cambio de implemento.
- Cada configuración incorpora `benefits`, `techniqueSummary` y
  `variantRationale`, además de cues de preparación, ejecución y errores
  frecuentes adaptados a sus ejes técnicos.
- `muscleNotes` se reescribió con el rol, la acción muscular y la consecuencia
  concreta de la variante. El nuevo `jointInvolvement` registra articulación,
  rol (principal, secundaria o estabilizadora), acciones y explicación
  biomecánica; usa los IDs canónicos de WikiLab y se replica en metadata rica.
- La revisión v6 queda protegida por compilador, gate editorial, backend y
  loader Android; el runtime Android y la copia de datos iOS comparten el hash
  canónico indicado arriba.

## Curaduría v4 (2026-08-03): descripciones amigables, involucramiento adaptativo y ejercicios nuevos

- **Descripciones reescritas para el usuario final** en las 196 definiciones y
  518 configuraciones: texto cercano, con carácter y que invita a probar el
  ejercicio. Fuera la jerga biomecánica (bisagra, patrón, cadena) y las
  plantillas genéricas. Regla L10.
- **Involucramiento muscular adaptativo real por chips**: remos con agarre
  amplio → trapecio/espalda alta, cerrado → dorsal/bíceps; dominadas supinas →
  bíceps protagonista, pronadas/neutras → estabilizador. Regla L11.
- **Ejercicios nuevos**: Curl Martillo y Curl Invertido (Barra H, Mancuernas,
  Polea, Máquina, Banda) y la familia Rotaciones de Antebrazo (Supinaciones y
  Pronaciones con Mancuerna y Polea). Implemento nuevo `h_bar` ("Barra H") con
  label en Android.
- Conteos nuevos: **96 familias / 196 definiciones / 518 configuraciones**.
- Gate: la palabra "todo" ya no se trata como placeholder (falso positivo del
  español en las descripciones nuevas).

## Correcciones posteriores al corte (-c, 2026-08-03)

- Eliminado "Curl de Bíceps Declinado" (duplicado funcional del Curl Bayesian).
- Title Case corregido: "Flexiones de Brazos", "Curl de Bíceps en TRX",
  "Tate Press".
- Peso Muerto Rumano y Peso Muerto Rumano Sumo: mismo set de implementos que el
  Convencional (se quitó `machine`; regla L9).
- UI picker v2: la descripción queda solo arriba de los chips (se eliminó la
  duplicada debajo) y cuando hay ≤7 chips en total, todos los ejes comparten una
  sola fila horizontal en vez de una fila vacía por eje.
- Conteos nuevos: 192 definiciones / 504 configuraciones (antes de la v4).

## Resultado del corte

El catálogo quedó generado desde fuente editorial determinista y es el único
catálogo que se empaqueta como runtime Android. El corte contiene:

- **96 familias, 196 definiciones y 518 configuraciones** enumeradas.
- Curaduría integral v2 aplicada: reestructura de bisagras (Convencional, Sumo,
  Rumano, Rumano Sumo, Piernas Rígidas, Buenos Días), remos (Convencional,
  Pendlay, Barra T, Gironda, Pecho Apoyado), curls de isquiosurales (Sentado,
  Tumbado, De Pie + sliders/balón/nórdico), presses de banca por ángulo,
  pullovers (de pie ≠ en banca), jalón con lateralidad, dominadas con agarre y
  amplitud, sentadillas (barra alta/baja, frontal, sumo, búlgara, sissy, hack),
  zancadas por dirección, presses de hombro por postura, bíceps por postura
  (Curl Araña, Bayesian, Concentrado, Sentado, Predicador, Crucifijo, Superman,
  Drag), aperturas por ángulo, Hip Thrust, Reverse Hyper único,
  Aducciones/Abducciones de Pierna.
- Curaduría v3 (revisión -c) añade y corrige: barra de seguridad (`safety_bar`)
  en Buenos Días y sentadillas traseras; **glúteo medio** (`gluteus_medius`)
  como músculo diferenciado (se agrupa con "Glúteos" en el cálculo de volumen);
  Cruce de Poleas con altura de polea; Aperturas Inversas con Máquina Pec Deck,
  Polea y Mancuernas; elevaciones laterales/posteriores/frontales reorganizadas;
  Super ROM unificada; Extensión de Tríceps (polea alta/máquina/banda);
  Extensiones/Flexiones de Cuello fusionadas; elevación de talones
  (máquina/barra/smith/polea × lateralidad); dominadas con perfiles musculares
  adaptativos por agarre y amplitud; eliminación de duplicados (curl inclinado,
  press de hombros de pie, plancha Copenhagen isométrica, hiperextensiones
  redundantes, Super ROM duplicada) y renombres Title Case sin relleno.
- `muscleNotes` y `jointInvolvement` completas en las 518 configuraciones: una
  explicación por músculo y articulación, sin huérfanos ni faltantes, validada
  por compilador, gate, backend y loader Android.
- Equivalencias fijas por rol: Principal 1.0 / Secundario 0.5 / Estabilizador
  0.4; no se guardan números en el JSON, UI y contadores derivan del rol.
- Eje condicional `pulley_height` soportado en compilador, gate, backend y
  loader Android (obligatorio donde `implement=cable`; prohibido en el resto;
  en definiciones de polea fija como Cruce de Poleas el eje `implement` es
  implícito y queda exento del chequeo de singleton).
- Chips limitados a ejes declarados por cada padre; solo aparece el siguiente
  nivel compatible. No se generan productos cartesianos ni se mezclan
  revisiones, definiciones o configuraciones.
- Variantes que cambian el patrón o la demanda (déficit, Zercher, métodos
  nombrados, isometrías) permanecen como especialidades separadas.
- Cada configuración materializada aporta su propia descripción contextual
  (≥40 chars, no instruccional, distinta entre configs del mismo padre).

## Artefactos y paridad

- Fuente agregada: `source/catalog_v2.json` (canónica, reconstruida por
  `scripts/merge_catalog_v2_families.py` desde `source/families/`).
- Guía editorial: `curation/EDITORIAL_GUIDE.md` (reglas R1-R11 y L1-L12).
- Runtime Android: `android-native/app/src/main/assets/exercise_catalog_v2.json`.
- Runtime iOS: `ios-native/KPKNFit/KPKNFit/exercise_catalog_v2.json` (copia de
  datos idéntica; la paridad de código iOS queda pendiente por falta de
  toolchain Apple en esta máquina).
- Backend: `backend/exercises_catalog_v2.py` valida revisión, hash, estructura,
  metadata, identidad exacta, `muscleNotes`, `jointInvolvement` y la ficha
  editorial.
- El compilador y el verificador comparan los tres artefactos mediante el mismo
  hash canónico; cualquier divergencia hace fallar el proceso.

## Gates ejecutados (corte curado)

- `python scripts/catalog_v2_gate.py --strict` → `status=READY`.
- `python scripts/compile_exercise_catalog_v2_cli.py --check` → 196 definiciones,
  518 configuraciones, hash canónico coincidente.
- `python scripts/compile_exercise_catalog_v2_cli.py --write` → asset Android
  regenerado (y copia idéntica a iOS).
- Backend: pruebas Python del catálogo → `OK`.
- Android: `testBaseDebugUnitTest` y `testHealthDebugUnitTest` → 0 failures.

## Regla de mantenimiento

Toda modificación futura debe regenerar fuente, runtime y hash en un solo corte,
ejecutar el gate estricto, las pruebas Android/backend y la inspección del APK.
No se admite reintroducir v1, aliases globales, resolución por nombre o chips
implícitos. Una definición sin metadata completa, configuración por defecto,
`muscleNotes`, `jointInvolvement` y la ficha editorial completas o decisión
editorial explícita debe bloquear el build.
