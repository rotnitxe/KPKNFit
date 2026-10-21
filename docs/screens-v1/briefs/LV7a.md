# LV7a · Motor de molestias y su conocimiento (dominio puro y datos)

Rama `screens/lv7a`, worktree `C:\kw\u8` (ya compilado), desde `feat/sessions-v1` (el commit del mensaje de lanzamiento). Lee
`LV_COMUN.md`, `LV_COMUN2.md` y el mapa `docs/screens-v1/maps/MAPA_descanso_molestias_rir.md` (sección 3 y 4). Prototipos de
referencia del contenido: `F2-DescansoCompletoUltima.dc.html` y `F7-Molestia.dc.html`.

Petición del usuario (16-oct): «molestias: búsqueda amable, chips destacados (reportes anteriores y probabilidad de las comunes),
ficha por molestia con animación curada, explicación y causas comunes; un MOTOR que detecte posibles causas en la sesión y a nivel
de semana, y aconseje». Este paquete es el motor y el contenido; la interfaz la hace LV6 (fase B).

## 1. `data/models/DiscomfortKnowledge.kt` (contenido)
Para CADA entrada de `DISCOMFORT_CATALOG` (salvo `"none"`), un registro:
- `zoneLabel` («RODILLA»), `title` («Molestia delante de la rodilla»), `explanation` (≤ 220 caracteres, qué es y dónde se nota),
- `commonCauses` (3–4 frases cortas, ≤ 70 caracteres cada una),
- `whatYouCanDo` (≤ 220 caracteres, práctico, de entrenamiento: bajar carga, cambiar variante, movilidad…),
- `animation`: la sección/escena de la zona para la animación de LV6 (`DiscomfortSection`),
- `searchAliases`: sinónimos y formas coloquiales sin tildes ni mayúsculas («rodila», «espalda baja», «lumbago», «codo de
  tenista»…).
Reglas del contenido: español neutro sin género gramatical, sin diagnosticar ni nombrar lesiones como certeza («suele
relacionarse con», «puede venir de»), sin medicamentos, y SIEMPRE el cierre común `PROFESSIONAL_ADVICE = "Si el dolor es fuerte o
no mejora en una semana, consulta a un profesional."` (no lo repitas dentro de cada texto). Usa como modelo F7 (rodilla delante).
Prueba: todas las entradas del catálogo tienen conocimiento, límites de longitud, sin «!», sin «debes».

## 2. `domain/discomfort/DiscomfortCauseEngine.kt` (motor puro, sin Android)
Entradas (modelos de dominio, sin Room): la molestia (`DiscomfortCatalogEntry`), los ejercicios de la sesión en curso con sus
series hechas (carga, reps, RIR/RPE, técnica 1–10 del feedback si existe, músculos y articulaciones de cada ejercicio vía su
`ExerciseMuscleInfo`/perfil), los `WorkoutLog` de las últimas 4–6 semanas y `now`.
Salidas:
- `rankLikely(session, history, limit = 3): List<DiscomfortId>` — las molestias más probables para el ejercicio/sesión
  (chips «Frecuentes en <ejercicio>»): por articulaciones y músculos cargados hoy (`relatedArticular`/`relatedMuscles`),
  carga axial, y molestias que la persona ya reportó con esos ejercicios. Determinista.
- `explain(discomfort, session, history): DiscomfortInsight` con `today: List<Evidence>` y `week: List<Evidence>` y un
  `headline` opcional (la línea del aviso de F2). Evidencias posibles (solo si los datos lo demuestran; cada una con su número
  real y su texto en español):
  - **Hoy**: series pesadas sobre la zona (≥ 80 % del e1RM o RIR ≤ 1) y cuántas; subida de carga frente a la última sesión del
    mismo ejercicio (≥ 7,5 %); técnica ≤ 6/10 en un ejercicio que carga la zona; series al fallo; ejercicios de impacto o
    unilaterales seguidos.
  - **Semana**: series semanales de los músculos relacionados frente a la media de las 4 semanas anteriores (aviso desde +25 %);
    frecuencia (≥ 3 sesiones que cargan la zona en 7 días); la misma molestia reportada en sesiones recientes (persistente: usa la
    misma regla que `WorkoutViewModel.persistentDiscomfortForExercise` para no contradecirla); semana sin descarga tras ≥ 5
    semanas de subida.
  - `advice`: una o dos acciones concretas derivadas de la evidencia más fuerte (p. ej. «Baja un 10 % la carga en la próxima
    sesión de pierna») + `PROFESSIONAL_ADVICE`.
- Sin evidencia suficiente: `headline = null` y listas vacías (la UI no inventa nada).
Reglas: puro y determinista (fecha inyectada), sin heurísticas mágicas sin nombre: cada umbral es una constante con comentario;
el volumen semanal se cuenta con la misma regla de series efectivas que ya usa la app (`VolumeCalculator` o el que use
`ProgramDetailHelpers`): reutiliza, no reimplementes.

## 3. Búsqueda amable
`domain/discomfort/DiscomfortSearch.kt`: búsqueda sobre etiqueta + `searchAliases`, sin tildes ni mayúsculas, tolerante a una
falta (distancia de edición 1 en palabras ≥ 5 letras), con orden: coincidencia al inicio > palabra completa > alias > aproximada.

## 4. Arreglo pequeño detectado en el mapa
`TrainingEnergyEngine` usa `technicalQuality.coerceIn(1, 5)` sobre una escala 1–10: confirma con una prueba y corrige con
`technicalQuality10ToPenaltyScale` (o la conversión que ya use el resto). Anótalo.

## Pruebas
Unitarias puras para todo (casos con evidencia y sin evidencia; umbrales en el límite; semana con +35 % de cuádriceps → la línea
de F2; misma molestia 3 sesiones → persistente; búsqueda «rodila», «espalda baja»). Siguen en verde `com.example.kpkn.domain.*`
y `com.example.kpkn.data.*` que toques.

## Permitido y prohibido
- **Permitido:** `data/models/DiscomfortKnowledge.kt` (nuevo), `domain/discomfort/*` (nuevo), `TrainingEnergyEngine.kt` (solo la
  escala), pruebas.
- **Prohibido:** toda la interfaz (`screens/*`), `DiscomfortCatalog.kt` (no cambies ids ni etiquetas), Room y el modelo del log.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/u8/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.discomfort.*" --tests "com.example.kpkn.data.models.*" --tests "*TrainingEnergyEngine*" > /c/kw/logs/lv7a-<n>.log 2>&1`

Tope: 4 corridas.

## Commits
- `feat(discomfort): el conocimiento de cada molestia y una búsqueda amable (LV7a)`.
- `feat(discomfort): motor que explica una molestia con lo que pasó hoy y esta semana (LV7a)`.
- `fix(energy): la calidad técnica se lee en su escala de 1 a 10 (LV7a)`.
