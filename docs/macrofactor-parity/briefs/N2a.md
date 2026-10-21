# N2a · Estado del día (Room v30) y marcado en el diario

**Prioridad P0 · esfuerzo M.** Informe §4.3 (C2–C4), §4.1 (G2, G7, G8). Depende de **H1** (ya integrado) y de **N1** (`domain/nutrition/expenditure/`, ya integrado: `DayMark`, `DayObservation`, `PartialDaySuggester`). Es la base de datos y de interfaz que N2b usa para el check-in.

## Por qué
Hoy un «día completo» es cualquier día con un total > 0 (`NutritionCalibrationViewModel`), no hay forma de declarar un ayuno (un día vacío no puede valer 0 kcal; el selector de fecha del registrador solo llega a hoy) ni de marcar un registro parcial. Sin eso el estimador de N1 no sabe qué días son fiables. El estado del día lo declara **el usuario**; el sistema solo sugiere.

## Cambio exacto

### 1. Room v30 — dos tablas nuevas (UNA sola migración 29 → 30)
Sigue el patrón de `data/db/VideoAnalysisMigration.kt`: función `xxxMigration(from: Int)` en archivo propio, **solo AÑADE** (`CREATE TABLE IF NOT EXISTS`), idempotente, con el DDL idéntico al de `schemas/…/30.json` (lo genera KSP; no se edita a mano), registrada en `addMigrations(...)` de `KpknDatabase` y `version = 30`. Un test que vigile que el DDL de la migración coincide con la entidad (como `VideoAnalysisMigrationValidateTest`).
- `nutrition_day_marks`: `date TEXT PK` (`yyyy-MM-dd`, día local), `mark TEXT NOT NULL` (`PARTIAL` | `COMPLETE_CONFIRMED` | `FASTING`), `source TEXT NOT NULL` (`USER` | `REVIEW_ACCEPTED` | `REVIEW_REJECTED`), `updatedAt TEXT NOT NULL`. Una fila solo existe cuando el usuario (o una revisión aceptada/rechazada) declaró algo; sin fila = `DayMark.NONE`.
- `nutrition_checkins` (la usa N2b; créala ahora para no tener una v31): `weekKey TEXT PK` (fecha ISO del inicio de la semana de check-in), `status TEXT NOT NULL` (`PENDING` | `APPLIED` | `DECLINED` | `POSTPONED` | `DISMISSED`), `decidedAt TEXT`, `proposalJson TEXT` (instantánea de la propuesta: estimación, objetivo anterior, objetivo propuesto, versión del modelo), `appliedPlanId TEXT`, `updatedAt TEXT NOT NULL`.
- `DatabaseBackupHelper`/`BackupTableManifest` (+ `JSON_BACKUP_SCHEMA_VERSION` si procede): ambas tablas son `USER_DATA` y viajan en la copia y se restauran. Mira cómo lo hace `nutrition_logs`/`daily_goal_snapshots` y replícalo.
- **Tests:** migración 29 → 30 con una base v29 completa que conserva TODAS sus filas (usa el patrón de los tests de migración existentes, p. ej. `WorkoutMediaMigration27To28FullRoomTest`), la entidad coincide con el DDL, e ida y vuelta de copia de seguridad con marcas y check-ins.

### 2. Repositorio
En `NutritionRepository` (o un colaborador fino que él posea): `dayMarks: StateFlow<Map<LocalDate, DayMarkEntry>>` de solo lectura, `suspend fun setDayMark(date, mark, source)` y `suspend fun clearDayMark(date)`.
- Durables: esperan el commit de Room antes de publicar (mismo criterio que `saveNutritionLog`); un fallo no publica el cambio y se propaga para que la UI avise.
- Cargan al arrancar con el patrón de cuarentena de H1 (una fila ilegible no oculta las demás).
- Validación pura: no se puede marcar un día futuro; `FASTING` solo en días sin comida consumida (o con confirmación explícita que borra la contradicción: decide y documenta); `COMPLETE_CONFIRMED`/`PARTIAL` requieren al menos un registro ese día.
- Un `DayMark` no borra ni modifica jamás las comidas.

### 3. Dominio: de registros + marcas a observaciones
Función pura en `domain/nutrition/expenditure/` o `domain/nutrition/` (no modifiques archivos de N1 salvo para añadir sin romper): `buildDayObservations(logs, marks, through): List<DayObservation>` que suma por día local las comidas **consumidas** (excluye `NutritionStatus.PLANNED`, como hace hoy el ViewModel de calibración) y adjunta la marca. Mismos criterios de zona/día que usa el resto de la app (`log.date.take(10)` o el resolvedor de fecha vigente: compruébalo y sé coherente). Test con comidas planificadas, varias comidas el mismo día, días sin comidas y marcas.

### 4. Interfaz en el diario (Nutrición)
Un control discreto del **estado del día** en la pantalla Nutrición, SIN rediseñar la pantalla ni quitar nada:
- Bajo los totales del día, una línea de estado: «Día sin marcar · Marcar», «Día completo ✓», «Registro parcial», «Ayuno (0 kcal)». Al tocar se abre una hoja con las opciones: «Día completo», «Registro parcial», «Ayuno (0 kcal)» (solo si no hay comidas) y «Quitar marca». Cada opción con una frase de explicación (qué hace en tu estimación de gasto).
- Si `PartialDaySuggester` (N1) sugiere ese día, la línea lo dice con tacto y ofrece dos acciones: «Marcar como parcial» (`PARTIAL`, `REVIEW_ACCEPTED`) y «Así es mi día» (`COMPLETE_CONFIRMED`, `REVIEW_REJECTED`). Rechazar la sugerencia no la vuelve a mostrar para ese día.
- Los días con marca se distinguen en el selector de fecha si ya existe un lugar natural (no añadas UI de relleno).
- Estilo KPKN v2 (`docs/design/KPKN_UI_V2.md`, `S_COMUN.md`), textos en español, objetivos ≥ 48 dp, accesibilidad real. Hoja de render (PNG a 360 dp, 100 % y 130 %): día sin marcar, completo, parcial, ayuno, y día con sugerencia. Critícala; máx. 3 rondas.

### 5. Vista previa en dispositivo (ver COMUN.md)
`NutritionDayStateHarnessActivity` en `app/src/debug/` con el extra `state` = `unmarked` | `complete` | `partial` | `fasting` | `suggested` (más `fontScale`, `reducedMotion`, `widthDp`): aloja la línea de estado y su hoja con datos en memoria. Commit aparte `debug(mfp): …`.

## Archivos permitidos
- `app/src/debug/java/com/example/kpkn/debug/NutritionDayStateHarnessActivity.kt` y su bloque en `app/src/debug/AndroidManifest.xml`.
- `data/db/Entities.kt` (entidades nuevas), `data/db/Daos.kt` (métodos nuevos en `NutritionDao`), `data/db/KpknDatabase.kt` (versión, entidades, registro de la migración), nuevo `data/db/NutritionDayStateMigration.kt`, `data/db/BackupTableManifest.kt`, `data/db/DatabaseBackupHelper.kt` y el(los) archivo(s) de backup JSON que correspondan.
- `app/schemas/com.example.kpkn.data.db.KpknDatabase/30.json` (generado por KSP; commitéalo).
- `data/models/NutritionModels.kt` (solo tipos nuevos si hacen falta).
- `data/repository/NutritionRepository.kt` (propiedad de H1 en la ola anterior; ahora tuya para esto).
- `domain/nutrition/**` (archivos nuevos; N1 solo para añadir).
- `screens/nutrition/NutritionScreen.kt`, `screens/nutrition/NutritionViewModel.kt` y archivos nuevos en `screens/nutrition/daystate/*`.
- Tests y la hoja de render (`render/NutritionDayStateRenderSheetTest`).

## Pruebas obligatorias
- Migración 29→30 conserva filas; DDL = entidad; backup ida y vuelta (marcas y check-ins).
- Repositorio: `setDayMark` durable (un fallo de Room no publica); día futuro rechazado; fila ilegible en cuarentena; reiniciar el repositorio recarga las marcas.
- `buildDayObservations`: planificadas fuera; varias comidas suman; ayuno marcado = 0 kcal con marca; día vacío sin marca = `intakeKcal == null`.
- ViewModel: marcar/quitar actualiza el estado; sugerencia aceptada/rechazada persiste y no reaparece.
- Compatibilidad: ningún test existente de nutrición se rompe.

## Comandos
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.data.db.*" --tests "com.example.kpkn.data.repository.Nutrition*" --tests "com.example.kpkn.domain.nutrition.*" --tests "com.example.kpkn.screens.nutrition.*"`
3. La hoja: `KPKN_RENDER_DIR=C:/kw/shots/mfp/N2a` con `--tests "com.example.kpkn.render.NutritionDayStateRenderSheetTest"`.

## Fuera de alcance
El check-in, la propuesta de objetivo y la sustitución del motor de calibración son N2b. No toques `NutritionCalibrationEngine` ni su ViewModel/pantalla. No cambies el cálculo de totales ni los snapshots de objetivos.
