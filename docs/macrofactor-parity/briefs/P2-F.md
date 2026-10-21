# P2-F · Medición de la búsqueda de alimentos en el dispositivo

**Prioridad P2 · esfuerzo S.** Informe §4.4, ítem D8 del informe (criterio: «p95 medido en el dispositivo, con el mismo top-k»); decisión D16 de la ola. Hoy la búsqueda es local: `NutritionRepository` busca con `LIKE` por términos sobre ~el catálogo completo, y las tablas FTS4 existen sin uso productivo. El informe NO pide cambiar la búsqueda: pide **medir** y decidir con el dato. Este paquete solo añade el banco de medición; la decisión (y un FTS, si procede) la toma la persona después, con permiso, porque tocaría el esquema de Room.

## Cambio exacto
1. **Banco en depuración** `app/src/debug/…/FoodSearchBenchActivity.kt` (+ manifiesto): sobre el catálogo real ya importado en la base de la app, ejecuta la **misma función de búsqueda que usa el cajón de alimentos** (`NutritionRepository`, ≈ l. 400-418; no una copia) con un conjunto fijo de **200 consultas** representativas, escritas a mano y en el código: palabras sueltas de 3 a 10 letras («pollo», «arroz», «yogur»), de dos y tres términos («pan integral», «atún en aceite», «leche semidesnatada»), con tildes y sin ellas («jamon» / «jamón»), con marcas («hacendado», «danone»), con faltas leves, y 20 sin resultados. Parámetro `warm` (descartar las primeras 20) y `repeat` (1 por defecto, 3 admitido).
2. Registra en logcat (`MfpSearchBench`) y en pantalla: tamaño del catálogo (filas de `global_foods`), top-k usado, **p50, p95 y p99** en ms de toda la búsqueda hasta tener la lista devuelta, las cinco consultas más lentas con su tiempo, y la memoria dedicada al proceso antes y después. Un resumen final: `BENCH p50=… p95=… p99=… n=… catalog=…`.
3. **Regla de decisión (documentada, no automática):** con el top-20, p95 ≤ 120 ms en caliente → no hace falta FTS; p95 > 120 ms → proponer FTS a la persona. Escríbela en `docs/macrofactor-parity/P2-F_RESULTADO.md` (dejando vacíos los números: los llena el orquestador tras medir en el Flip).
4. **No cambies** la búsqueda, ni los índices, ni el esquema, ni el importador. Si para medirla hace falta un punto de entrada sin `ViewModel`, añade solo un acceso interno de lectura sin cambiar el comportamiento.

## Archivos permitidos
`app/src/debug/…/FoodSearchBenchActivity.kt` + manifiesto de depuración; un archivo de conjunto de consultas en `app/src/debug/…`; tests del conjunto (200 consultas, sin duplicados, 20 sin resultados esperados contra una base falsa pequeña); `docs/macrofactor-parity/P2-F_RESULTADO.md`.

## Pruebas obligatorias
El conjunto de consultas (cuentas y categorías); el cálculo de percentiles con valores escritos a mano (método de rango más cercano, documentado); la actividad compila y se lanza en Robolectric sin tocar el catálogo real.

## Comandos
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.debug.*" --tests "com.example.kpkn.data.repository.Nutrition*"`

## Fuera de alcance
Cualquier cambio de la búsqueda, FTS, índices o Room; medir en el emulador (la medición se hace en el Flip5, lo ejecuta el orquestador).
