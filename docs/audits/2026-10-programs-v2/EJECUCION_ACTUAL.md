# Ejecución actual: 25-oct-2026

Base `1d399c1fc`, rama `codex/programs-periodization-current`. Sin push ni cambios al WIP de master. El worktree histórico queda intacto; se canceló la verificación Android de ese worktree al recibir la instrucción de adaptar el plan al producto actual, sin resultado válido.

## Recuperación inicial (validación actual pendiente)

| Tarea | Commit recuperado | Adaptación |
|---|---|---|
| T-01 | `33affef65` | Alcance puro |
| T-02 | `6e8ef0571` | Textos conservando modelos vigentes |
| T-03 | `888340da7` | Etiquetas de alcance |
| T-04 | `0fa5cd612` | Alcance en pantallas actuales |
| T-10 | Ya integrado por CAL5 en la base | Conservar `StagedVolume`: etapas por fallo, no las 12 semanas por calendario del plan antiguo |
| T-11 | `bde049549` | Rotación de Cube conservando todos sus AMRAP de registro; contentVersion 4 por partir de v3 actual |
| T-12 | `6e38bcfff` | Rampa RP limitada al accesorio acordado, sin exenciones de KPKN_NATIVE |
| T-13 | `3bcf5203f` | Top sets RPE con referencias de respaldo |
| T-14 | `0f3c8e6b4` | Calgary taper; accesorios principiantes conservan calibradores existentes; power-12-3 pasa a v4 |
| T-50 | `5b7117f9c` | Decisiones del protocolo con origen PROTOCOL, sin introducir AUGE; campos multisport preservados |

Los commits recuperados no se presentan como tareas verificadas sobre esta base. La corrida dirigida actual cubre ProgramScope, ProgramProgressReport y recetas/contratos. Resultado pendiente.

Contrato actual comprobado en código: `RecipeContractInventoryTest` usa ambos techos en 18 hallazgos no exentos. El 100 del relevo antiguo ya no es el límite vigente; no se subirá ninguno de los dos techos. El gate de retirada terminó con `0 active retirement violations; positive/physical-rings control checks passed`.

Primera corrida JVM: 8 clases, 97 tests, 1 fallo, 0 errores, 0 omitidos; 288 s con cola. Log `C:/kw/logs/kpknjvm-KPKNFit-20261025-124644.log`. Único fallo: exención muerta `korte-3x3 / H5b / *`, de la comprobación retirada. Se elimina la exención y se actualizan sus matrices; la receta de ejercicios, series, cargas y repeticiones permanece intacta. Korte contentVersion 2 por el cambio de metadatos. No se debilitan tests ni techos.

Segundo intento aprobado:

```text
kpkn_jvm.sh <worktree> -PtestInclude=<8 clases y 2 fixtures explícitos> --tests <8 clases explícitas>
BUILD SUCCESSFUL in 5m 44s
[kpkn_jvm] RESUMEN: 8 clases, 97 tests, 0 fallos, 0 errores, 0 omitidos; gradle rc=0; 388 s con cola
```

Log: `C:/kw/logs/kpknjvm-KPKNFit-20261025-125239.log`. Clases: ProgramScopeTest (5), ProgramProgressReportTest (5), ProtocolRecipeFidelityTest (70), RecipeContractInventoryTest (6), ProtocolCompositionContractTest (3), ProtocolLibraryTest (3), ExemptionScopeMigrationTest (3), ProtocolExemptionMatrixTest (2). Son pruebas JVM del motor; Android y runtime no quedan acreditados.

## T-51–53: portado, pendiente de validación Android

Se recuperó sólo el diff de ViewModel, pantalla, test de ViewModel y render, más los dos componentes nuevos y la especificación. Se resolvió una importación retirada sin recuperarla. La hoja usa origen PROTOCOL y la documentación consume StrengthModel y los motores descriptivos actuales. No se copiaron archivos completos sobre el ViewModel ni la pantalla actuales. Compilación Android dirigida en curso mediante la cola, un trabajador y heap de compilación de 6 GiB; después se ejecutarán pruebas con heap Gradle reducido y un fork de 3 GiB.

## Pendiente

Validación de recuperación, portado del WIP T-51–53, cierre de Ola A y ejecución secuencial de las olas B–E adaptadas. T-104 sigue aplazada por el propio plan. Validación Android, render, instalación y QA aún no realizadas en esta base.
