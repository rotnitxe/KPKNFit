# Herramientas de la ola MFP para Codex Cloud

Solo ejecutar en una maquina de Codex Cloud. El usuario prohibio nuevas corridas de Gradle en su PC el 22-oct-2026. Los scripts exigen `KPKN_CLOUD_EXECUTION=1` y rechazan Windows y las rutas de los checkouts locales conocidos. No usar SSH, MCP local, cola de Windows ni un ejecutor del PC para compilar.

- `run-jvm.sh <worktree-remoto> ...`: usa el wrapper versionado de Android y la plantilla JVM incluida. Requiere JDK 17; primera ejecucion con acceso a repositorios Gradle/Maven. Se mantienen los stubs y exclusiones de la plantilla existente de `kpkn-jvm`.
- `run-android.sh <worktree-remoto> <tareas...>`: compila o ejecuta pruebas unitarias Android exclusivamente en la nube. Requiere JDK 17 compatible con el proyecto y SDK Android 36; flavor `base`. Gradle 9.4.1 y AGP 9.2.1 estan fijados por el repositorio. Resolver herramientas de build declaradas durante la preparacion remota.
- `mfp_verify.py`: verificador mecanico existente, portable Python 3. Lee JUnit XML, compara `ROJOS_BASE.txt`, examina constantes, Room y pies de commit. No ejecuta Gradle. No interpretar 0 suites como validacion: comprobar tambien exit code, log y cantidad de pruebas.

Ejemplo de dominio, SOLO EN LA NUBE y con el directorio remoto correspondiente:

```bash
export KPKN_CLOUD_EXECUTION=1
bash tools/macrofactor-cloud/run-jvm.sh "$PWD" \
  '-PtestInclude=com/example/kpkn/domain/training/**,com/example/kpkn/domain/performance/**,com/example/kpkn/data/protocols/**,com/example/kpkn/domain/onboarding/AuthoredPlanFixtures.kt,com/example/kpkn/domain/templates/CatalogV2TestFixture.kt' \
  --tests com.example.kpkn.domain.training.LoadTargetSelectorTest \
  --tests com.example.kpkn.domain.training.LoadTargetSimulationTest \
  --tests com.example.kpkn.domain.training.NativeProgressionLoadTargetTest
```

Despues ejecutar `NativeWorkoutProgressionRuntimeTest` en Android: esta clase usa Robolectric/Room y esta excluida del arnes puro. Las pruebas excluidas, los stubs de assets/catalogo y los metodos omitidos se listan expresamente en `jvm/*.txt`; un verde JVM no acredita Android, Room, UI, catalogo real ni rendimiento del dispositivo.

```bash
export KPKN_CLOUD_EXECUTION=1
bash tools/macrofactor-cloud/run-android.sh "$PWD" :app:testBaseDebugUnitTest \
  --tests com.example.kpkn.domain.training.NativeWorkoutProgressionRuntimeTest \
  --tests com.example.kpkn.domain.training.NativeProgressionLoadTargetTest
python tools/macrofactor-cloud/mfp_verify.py "$PWD" --since c696902b9 \
  --baseline docs/macrofactor-parity/ROJOS_BASE.txt
```

Para las otras pistas, los scripts pueden residir en el checkout base y recibir otra ruta de worktree remoto. Una sola corrida por worktree; agrupar cambios antes de validar. Guardar logs y PNG en `/tmp/kpkn-mfp-cloud` y adjuntarlos al resultado cloud. Esta preparacion solo comprobo sintaxis y archivos: no ejecuto Gradle ni Java, ni certifica que el nuevo entorno ya este provisionado.
