INV-PROG · Inventario del contrato de receta (ratchets B.S6)

Código en `C:\kw\u13`, rama `sv2/programas`; este informe en el hub de docs de `C:\kw\u11`. Cierra `RecipeContractInventoryTest`, que quedó en rojo tras la integración (108 > 100 y 21 > 18) cuando PROG y apre-mann añadieron hallazgos deliberados. El relevo traía ya el análisis de los 8 hallazgos y la exención de C10 escrita sin commitear; aquí se documenta la decisión del primer techo, el registro en las tablas de gobernanza y las corridas.

## Qué miden los techos

- `CEILING` (primer techo): hallazgos del contrato sobre las recetas publicadas (41: 30 protocolos, 7 plantillas, 4 autoradas). Base histórica: 100 = C1 46 + C4 5 + C5 8 + C8 23 + C10 18.
- `CEILING_AFTER_EXEMPTIONS` (segundo techo, 18): los que siguen vivos TRAS las exenciones declaradas. Hoy solo C10 (semanas idénticas, siempre SOFT) puede quedar vivo.

## Los 8 hallazgos nuevos (deliberados y con fuente)

El crudo pasó de 100 a 108 = C1 50 + C4 5 + C5 9 + C8 23 + C10 21 (medido en `build/reports/recipe-contract.txt`):

- **C1 +4**: el T2 de banca de `nsuns-531-lp-4d` (9 series) marca rango en 4 semanas. Ya lo cubre la exención de rango C1 por slot declarada para ese slot (`Banca/OHP/t2` en la tabla congelada).
- **C5 +1 y C10 +3**: `apre-mann` (CAL6). APRE de Mann no programa descarga (la serie 4 reajusta el XRM cada semana) y los bloques de cuatro semanas repiten estructura por diseño. C5 lo cubre la exención de la receta; C10 necesitaba exención nueva.

## Exención de C10 y registro en las tablas de gobernanza

- `ApreProtocols.kt`: `RecipeCompositionExemption("C10_IDENTICAL_WEEKS", "w*", …, MANN_URL)`, con la justificación de que el XRM de la semana siguiente sale de las repeticiones de la serie 4 con la tabla de Mann, no de cambiar la forma de las semanas. El segundo techo baja de 21 a 18.
- Dos tablas de gobernanza exigen registrar toda exención nueva y fallaban en rojo hasta hacerlo:
  - `ProtocolExemptionMatrixTest` (matriz 2.2): `apre-mann` pasa a permitir `C10_IDENTICAL_WEEKS` además de `C5_DELOAD_REQUIRED`.
  - `ExemptionScopeMigrationTest` (tabla ANTIGUA congelada): se añade la entrada `"C10_IDENTICAL_WEEKS" to "w"` para `apre-mann`, el equivalente en semántica antigua (`contains "w"` cubre w1…w12) del glob anclado `w*`.

## Primer techo: decisión del usuario (2026-10-22)

El crudo no descuenta exenciones: con los 8 hallazgos deliberados ya exentos daba 108 > 100 y el remedio del propio assert («declara una exención justificada») no lo bajaba. Subir el techo crudo a 108 lo prohíbe el ratchet de solo descenso B.S6. **Decisión del usuario: la semántica del primer techo pasa a «crudo menos exentos»** (hallazgos que ninguna exención declarada silencia), que queda en 18 (C10 21 crudos − 3 exentos de APRE). El techo baja de 100 a 18 con el nuevo conteo: sigue siendo descenso, ningún techo se sube. La corrida imprime ambos números («crudo: 108 … · crudo menos exentos: 18 (C10 18)»).

## Corridas del carril JVM (cola v10)

Comando (los fixtures de otros paquetes van POR ARCHIVO, como documenta `kpkn_jvm.sh`):

```
bash /c/kw/tools/kpkn_jvm.sh u13 "-PtestInclude=com/example/kpkn/data/protocols/**,com/example/kpkn/domain/training/**,com/example/kpkn/domain/onboarding/AuthoredPlanFixtures.kt,com/example/kpkn/domain/templates/CatalogV2TestFixture.kt"
```

- **inv-1**: fallo de compilación: `AuthoredPlanFixtures`/`Gear` viven en `domain/onboarding/`, fuera del `-PtestInclude`.
- **inv-2**: al meter `domain/onboarding/**` entro `SetupRingsMergeTest`, que usa `SetupCommitCoordinator` de main (`data/onboarding/SetupPersistence.kt`, fuera del arnés), y `CatalogCompositionTestSupport` quedó sin `CatalogV2TestFixture`.
- **inv-3**: con los dos fixtures por archivo compila todo menos `TrainingPlanRecipeJsonCompatTest`, que importa `dbJson` de `data/db/Entities.kt` (fuera del arnés JVM por Room).
- **inv-4**: 1693 tests corren. Techos en verde (crudo 108, crudo menos exentos 18, tras exenciones 18) y quedan 2 fallos de gobernanza: la exención de C10 aún no estaba registrada en la matriz 2.2 ni en la tabla congelada.
- **inv-5**: **160 clases, 1693 tests, 0 fallos, `BUILD SUCCESSFUL`** (`C:\kw\logs\kpknjvm-u13-20261022-134251.log`).

## Ajuste al arnés JVM (fuera del repo)

`TrainingPlanRecipeJsonCompatTest.kt` se anota en `C:\kw\kpkn-jvm\excluded-test.txt`, en la sección «Dependen de Room (toEntity, dbJson de data/db/Entities.kt, excluido)», como ya están otros tres tests con la misma dependencia. Ese test sigue cubierto por la verificación oficial.

## Estado final y pendiente

- Verde en el carril JVM: `RecipeContractInventoryTest` (5 tests) y el pack completo de `data/protocols` y `domain/training`.
- Commits en `sv2/programas` (u13): la exención con su registro en las tablas, y el cambio de semántica del primer techo. Este informe va en `feat/screens-v2` (u11).
- Pendiente: la verificación oficial `:app:testBaseDebugUnitTest` al integrar u13 en u11 (el carril JVM no cubre Room, Compose ni Robolectric). Después continúa el orden del relevo: ED-B1/B2 → CAT-A → G → ED-D → ED-C → integración → fase 4.
