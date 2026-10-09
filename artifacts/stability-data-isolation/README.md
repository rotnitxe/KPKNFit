# Barrera multimedia y datos entre procesos

Se corrigió una regresión reproducida sobre c10: tras cerrar el repositorio multimedia y borrar su directorio, una referencia antigua podía volver a crearlo. La prueba original falló; la misma prueba ahora pasa.

La barrera cierra la admisión antes de drenar. Las operaciones ya aceptadas y su ingestión anidada terminan; las nuevas se rechazan. Al reabrir se invalida la generación anterior, también para callbacks de progreso de sincronización. Un mantenimiento cancelado no puede reabrir mientras quede un escritor activo. Los errores de callbacks tardíos llegan por Deferred al observador normal, sin crear un journal nuevo.

La restauración actual de Room conserva la conexión y copia las tablas dentro de una transacción. No se reprodujo el antiguo diagnóstico de reutilización de una conexión cerrada y no se modificó ese mecanismo.

Se reprodujo otro fallo real en API36: SQLite necesitaba escribir para validar el índice FTS4, y abrir la copia temporal en modo lectura impedía tanto restaurar como recuperar el rollback. La copia aislada se abre ahora con escritura y mantiene integrity_check, foreign_key_check y validación de versión. El respaldo original queda intacto; las pruebas comparan sus bytes antes y después. El diagnóstico nativo exacto está en `native-integrity-probe/`.

- **42 pruebas JVM / 9 suites del árbol final: PASS, sin omisiones.** Todos sus XML están en `fts-final-jvm/`; la ronda anterior está en `media-directed-2/` y el XML fallido de la reproducción está en `media-baseline/`.
- **17 pruebas nativas en API36: PASS.** Incluyen ingestión URI, cámara externa, reintentos y cuatro etapas en procesos distintos. El runner fuerza el cierre exclusivamente de `com.example.kpkn.stability` entre etapas y verifica cuatro PID distintos.
- Con Room en archivo y una PNG completa: exportación, restauración, lectura y exportación tras nuevo proceso, borrado, verificación del borrado tras otro proceso y conservación del perfil B. La vuelta a invitado se verifica en el núcleo de almacenamiento, sin acceso a Firebase real.
- `assembleDebug` y `assembleDebugAndroidTest`: PASS. Instalación aislada, captura/UI/logcat y MainActivity reanudada: evidencia en `smoke-final/` y `native-final/`.
- Fingerprint de los 2.593 archivos fuente/fixtures utilizados y SHA-256 de APK/evidencia: `tested-source-fingerprint.json` y `DELIVERY.json`.

El alcance de producción es la barrera nueva, AppDataSessions, WorkoutMediaRepository, el ejecutor de progreso de MediaTransfer y el modo de apertura de la copia aislada en DatabaseBackupHelper. Se conservaron las pantallas de Entreno, voz, GPS, nutrición y el trabajo paralelo. La retirada de Health Connect y AUD06 permanecen en los commits anteriores.

Esta entrega verifica la barrera multimedia y controles de almacenamiento. No cierra la auditoría de todos los escritores ni las pantallas de cambio de cuenta; quedan pendientes la salida explícita de un entrenamiento activo, interrupciones reales a mitad de restauración, disco físicamente lleno y la matriz de migraciones. No se ejecutó la suite completa ni lint/R8 Release del árbol nuevo. No hay release firmado ni push.

El lint y R8 favorables de c10 son evidencia de aquel árbol: no se trasladan a esta corrección. Su suite completa original tuvo 8.061 pruebas, cuatro fallos de fixture Pro y seis omisiones. El fixture se corrigió y sus 13 pruebas pasaron después; no se repitió toda la suite. Los resultados actuales son las 42 dirigidas y las 17 nativas anteriores.

La integración usa fast-forward sólo tras comprobar que el índice esté vacío, que no haya solapamientos con WIP y que el estado/hash de los cambios locales y los HEAD de los worktrees protegidos permanezcan iguales. El recibo posterior queda en `integration-receipt.json`.
