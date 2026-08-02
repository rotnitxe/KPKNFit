# Estado de ejecución — catálogo v2

Fecha de corte: 2026-08-02
Revisión: `v2-approved-2026-08-02`
Hash canónico compartido: `58abd25a9a4e600d3c2e1347d91b43fb76144c3eac9d7411a415904a80b80a06`

## Resultado del corte

El catálogo v2 quedó generado desde fuente editorial determinista y es el único
catálogo que se empaqueta como runtime Android. El corte contiene:

- 257/257 candidatos con decisión editorial explícita y racional documentada.
- 73 familias, 232 definiciones padre y 259 configuraciones enumeradas.
- 100% de definiciones aprobadas, con configuración por defecto válida.
- Metadata rica para anatomía, AUGE, involucramiento muscular, biomecánica,
  equipamiento, agarre, lateralidad, setup, ejecución, errores y cues.
- Chips limitados a ejes declarados por cada padre; no se generan productos
  cartesianos ni se mezclan revisiones, definiciones o configuraciones.
- Variantes que cambian el patrón o la demanda (por ejemplo déficit, Zercher,
  B-stance y Copenhagen isométrica) permanecen como especialidades separadas.
- El Peso Muerto Rumano es un padre con configuraciones explícitas bilateral y
  sumo; no existen copias sueltas para esas dos elecciones técnicas.

## Artefactos y paridad

- Fuente agregada: `source/catalog_v2.json`.
- Runtime Android: `android-native/app/src/main/assets/exercise_catalog_v2.json`.
- Runtime iOS: `ios-native/KPKNFit/KPKNFit/exercise_catalog_v2.json`.
- Backend: `backend/exercises_catalog_v2.py` valida revisión, hash, estructura,
  metadata e identidad exacta.
- El compilador y el verificador comparan los tres artefactos mediante el mismo
  hash canónico; cualquier divergencia hace fallar el proceso.
- Los antiguos `exercise_database.json` y `exercise_id_aliases.json` fueron
  retirados del bundle Android y conservados únicamente en
  `curation/evidence/legacy/` como evidencia editorial. No son fallback ni
  fuente de runtime.

## Gates ejecutados

- `python scripts/catalog_v2_gate.py --strict` → `status=READY`.
- `python scripts/compile_exercise_catalog_v2_cli.py --check` → 232 definiciones,
  259 configuraciones, hash canónico coincidente.
- Backend: 4 pruebas Python → `OK`.
- Android: `:app:testBaseDebugUnitTest --offline` → 936 tests, 0 failures, 0
  errors (`BUILD SUCCESSFUL`).
- Android: `clean :app:assembleBaseDebug --offline` → `BUILD SUCCESSFUL`.
- Inspección del APK limpio: contiene `assets/exercise_catalog_v2.json` y no
  contiene ningún asset v1 ni archivo de aliases.
- Android Health: `:app:testHealthDebugUnitTest --offline` → 936 tests,
  0 failures, 0 errors (`BUILD SUCCESSFUL`). La prueba de canales usa API 26,
  el mínimo real del flavor Health; el guard de producción para API <26 sigue
  intacto en `WorkoutReminderManager`.

## Validación de instalación

El APK BaseDebug se instaló y se relanzó en `emulator-5554` con el paquete
`com.example.kpkn` y la actividad explícita `.MainActivity`. Se comprobó proceso
vivo, ausencia de `FATAL EXCEPTION`, carga del catálogo con 232 ejercicios y
búsqueda de `Bulgaria en Máquina`, que abre `Sentadilla búlgara` con su
configuración de máquina. La evidencia de esta corrida se guarda en:

- `C:\tmp\kpkn_catalog_v2_final.xml`
- `C:\tmp\kpkn_catalog_v2_final.png`
- `C:\tmp\kpkn_catalog_v2_final.log`

La ficha verificada muestra descripción contextual en español, equipamiento,
fuerza y roles musculares sin el sufijo técnico crudo del compilador
(`machine · guided`), sin `N/A`, sin Tier ficticio y sin texto de `parent`
interno. También se verificaron los flujos integrados que consumen la misma
identidad v2:

- selector: cambio de implemento/estación/ángulo, chips envueltos sin overflow,
  sólo configuraciones materializadas y confirmación persistente;
- editor: apertura con la configuración exacta, reemplazo por otra configuración
  y reapertura conservando `definitionId`/`configurationId`;
- entrenamiento en vivo: reemplazo conservando la selección inicial, edición de
  series y vuelta al entrenamiento;
- Home → preparación → entrenamiento, historial, Split/Info y Analíticas/AUGE.

En los flujos muestreados no apareció `FATAL EXCEPTION`, `NullPointerException` ni
`IllegalStateException`. La auditoría de consumidores tampoco encontró referencias
de los assets v1 dentro del runtime; las coincidencias restantes están limitadas
a documentación histórica y evidencias editoriales.

La corrección de presentación y persistencia que acompaña este corte está en
`ExerciseCatalogV2Labels.kt`, `ExercisePickerV2Catalog.kt`,
`ExercisePickerSheet.kt`, `SessionEditorSheets.kt` y
`WorkoutStructureSheetsHost.kt`: labels controlados en español, `FlowRow` para
los chips, normalización de selecciones multi-eje y propagación de la identidad
exacta al editar/reemplazar.

## iOS

El repositorio v2, el asset, el adapter de compatibilidad y los tests fueron
integrados en el proyecto Xcode. La máquina de ejecución es Windows y no tiene
`xcodebuild`/toolchain Apple, así que no se declara una compilación nativa iOS
como evidencia de esta sesión. La paridad estructural sí queda protegida por
la comparación determinista de hash y por el contrato Codable estricto.

## Regla de mantenimiento

Toda modificación futura debe regenerar fuente, runtime y hash en un solo corte,
ejecutar el gate estricto, las pruebas Android/backend y la inspección del APK.
No se admite reintroducir v1, aliases globales, resolución por nombre o chips
implícitos. Una definición sin metadata completa, configuración por defecto o
decisión editorial explícita debe bloquear el build.
