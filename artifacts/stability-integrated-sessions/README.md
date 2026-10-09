# Mantenimiento coordinado y salida explícita de entrenamiento

El bloque amplía la retirada de Health Connect y las correcciones multimedia ya integradas en `f28668678`. Se trabaja desde un checkout aislado para preservar las implementaciones paralelas del checkout principal.

Se reprodujo una escritura durable desde una referencia antigua de competición. La nueva generación de sesión rechaza referencias retiradas; los trabajos ya admitidos y sus dependencias pueden terminar antes de restaurar, borrar o cambiar el perfil. También se coordinan borradores, preferencias, mediciones, catálogos, AUGE, recordatorios y tareas de sincronización. El contexto personal queda capturado, sin sustituir los contextos suministrados para inyección.

Las operaciones de Ajustes usan el mismo coordinador. Ante un entrenamiento activo, el usuario puede volver, finalizar y guardar, o descartar con confirmación explícita. La operación de datos debe confirmarse de nuevo. La tarjeta flotante de sesión se ocultó en Ajustes después de reproducir que interceptaba el tap de borrado. Un cálculo AUGE tardío después de cambiar el invitado se vinculó al cierre del ViewModel; sólo la denegación de una sesión cerrada se convierte en cancelación esperada. Un error real de disco conserva su manejador.

Evidencia local del árbol:

- **113 pruebas JVM / 20 suites aprobadas, sin omisiones, del árbol final**, con la programación inmediata del recordatorio y el fixture de renombrado de Windows corregidos. XML completos en `directed-final-17-unit/`.
- 27 pruebas JVM / 8 suites aprobadas, sin omisiones, antes de las dos correcciones nativas de UI y AUGE. XML completos en `integrated-native-7-unit/`.
- 6 pruebas JVM / 3 suites aprobadas tras el ajuste AUGE, sin omisiones. Incluye el bloqueo de una escritura SQLite real hasta que termina. XML completos en `auge-owner-12-unit/`.
- 2 casos de manejo de errores aprobados: cierre esperado cancela sin excepción no observada; un error ordinario de disco llega intacto al manejador. XML en `failure-boundary-13-unit/`.
- **Tres fases Android API36 aprobadas del APK final** en procesos distintos (10343, 10558 y 10685), en `native-final-17/`: salida explícita desde Ajustes, conservación del historial, exportación/restauración mediante AppDataSessions, comprobación tras nuevo proceso, borrado desde Ajustes, comprobación del borrado tras otro proceso y cambio local de invitado con recreación de MainActivity. La ronda anterior `native-integrated-12/` queda como evidencia histórica.
- **Una prueba Android adicional aprobada** de `AtomicFile` real, en `native-final-17/registry-platform.txt`, recuperó el propietario comprometido al reemplazar un registro parcial. Ese archivo de QA estaba separado del registro de identidad instalado.
- APK Debug y AndroidTest compilados; instalación limitada a `com.example.kpkn.stability` en `emulator-5554`. MainActivity reanudada y captura/árbol de la pantalla final en `native-final-visible-17/`.

La base existente sigue en Room v30. Los motores y los activos de datos no se modificaron. Voz y GPS se conservan. Las rondas históricas de 83 y 39 pruebas están separadas; no se suman como pruebas únicas del árbol final. También se conservan el fallo inicial del escritor antiguo, el fallo de inyección de contexto y las reproducciones nativas previas.

La suite completa quedó inconclusa después de 3 h 10 min: se conservan los 463 XML emitidos, con 4.045 casos, 2 fallos y 1 omitido. Dos capturas de hilos muestran el worker ejecutando la matriz exhaustiva de `PlanGenerationCoverageT006Test.Q2_calibrated_pass_changes_no_viable_row`, cuyos motores no se modificaron. Se detuvo sólo ese worker identificado; lint y R8 no se ejecutaron en esa invocación. Este resultado no equivale a aprobación de la suite completa.

El fallo del recordatorio reveló una regresión de esta ampliación: programar la alarma dentro de una tarea asíncrona cambiaba el comportamiento inmediato. Se restauró la programación inmediata y se conserva el seguimiento de la cola de escritura de preferencias. La repetición de la ronda 15 ejecutó 32 casos: 31 aprobados y el caso de identidad API34 fallido; el recordatorio y los escritores pasaron.

El diagnóstico de identidad de la ronda 16 ejecutó 10 casos (9 aprobados, 1 fallido) y confirmó `Failed to rename`: en Windows, `File.renameTo` no reemplaza el archivo parcial existente como el syscall de Android. Se ajustó exclusivamente esa operación en el fixture JVM mediante `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`, manteniendo el decodificador, la validación, el cierre seguro y todas las aserciones. `ProfileStorage` de producción permanece idéntico al padre. Una prueba Android adicional usa un registro en una carpeta QA aislada para verificar la recuperación real, sin modificar el registro del perfil instalado.

Las pruebas dirigidas y la QA del APK actualizado están aprobadas. Lint Release terminó con 0 errores, 614 advertencias y 39 avisos informativos; el XML completo está en `lint-results-release-18.xml`. R8 terminó correctamente en la misma invocación, en `gradle-release-checks-18.log`. No se empaquetó ni publicó una release firmada. Las 2.604 huellas de fuentes y los blobs del índice se verifican antes de integrar; el recibo posterior confirma la preservación de los cambios locales, el índice y los HEAD de los otros worktrees en ese instante.

La comparación de lint con la ronda 13, ignorando números de línea movidos, añadió tres advertencias: recomendación de `apply()` para un `commit()` ejecutado en el propietario de IO del tour (persistencia intencional antes de drenarlo), un recurso de error legado sin uso y una sugerencia de KTX. Son advertencias de estilo/limpieza; el detalle está en `lint-delta-18.json`.

Este bloque no cierra el plan de lanzamiento A–J. Quedan sin verificación integral todos los callbacks SDK, cuentas Firebase reales, compras Play, interrupción del proceso dentro de la restauración, disco físicamente lleno y la matriz completa de versiones/dispositivos. El inventario estático de 814 candidatos en 131 archivos orienta la revisión; no demuestra por sí solo el cierre de todos los escritores. No hay publicación, release firmado ni push.

Huellas del APK instalado en la QA final:

- Debug: `4ba6038edb7e8f6cbbb68bfecc1232d0c80fd3b3e5d9bdea0e5ab2d6d3d10d31`.
- AndroidTest: `fafca1b3bb30f283f70124ae1442c1a350f313d708c1c82290ee777e344a779a`.

El avance parcial por requisito está en `requirement-progress.json`. Después del avance de master, `integration-receipt.json` documenta el commit y las comprobaciones de preservación.
