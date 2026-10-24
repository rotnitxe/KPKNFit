# Retiro de coeficientes del catálogo

Migración editorial reproducible: `python scripts/retire_post_rings_catalog.py`,
seguida de `python scripts/compile_exercise_catalog_v2.py --write` y `--check`.
El script opera en rutas explícitas del agregado y los 96 archivos de familias.
No borra ejercicios ni modifica identificadores, dificultad técnica, instrucciones,
selecciones, ontología o curación. `rings` como aparato sigue presente.

Se retiran profile.{efc,cnc,ssc,ttc,axialLoadFactor,fatigueTier},
richMetadata.fatigue y richMetadata.programming.fatigueCost. La revisión de
selección del catálogo se conserva para no invalidar las identidades históricas;
la distribución nueva tiene SHA-256 canónico
`ce1ed8f4ec0c7476e66a353e567717b86cc896fda797d33ee99bb5c788584ce7`.
Los tres artefactos se generan juntos; ningún asset se edita a mano.

Los contratos Python prueban cada ruta retirada por separado, la paridad del
validador desplegable y que la dificultad técnica permanece. Los DTO y puentes
Android/iOS dejan de exigir o materializar coeficientes. Se retiran los presupuestos
espinales H5/W3 basados en esos coeficientes y la penalización de fatiga del
redistribuidor: conserva el equilibrio de tiempo y las restricciones estructurales.
No se reemplazan coeficientes desconocidos por ceros o promedios.

Validación actual: compilador --write/--check válido, 228 definiciones / 553
configuraciones; 7 tests del contrato y backend 19 tests + 32 subtests correctos.
Compilación Android de esta fase y build iOS todavía pendientes.
