# Integración de programas en master con Room36 — 26-oct-2026

Autorización humana: «Integra los cambios en master y haz push, conservando el WIP ajeno y la compatibilidad con Room 36».

## Árbol integrado

Se reutiliza la copia aislada programs-phone-room36, rama codex/programs-master-room36. Incluye el cierre de programas92e28972e y el master local1db6fd4e4 (incluido el rediseño ya comprometido). La referencia remota inicial era1d5ba0ce7. Fusiones sin conflictos; no se reconstruyen ni se descartan cambios de otras ramas.

Room36 conserva la identidad907a5e4df31b4ead1ee0f8d86ce559cf del APK previamente instalado en Z Flip. Se mantiene la cadena de migraciones publicadas MFP/POST-RINGS y se añade35→36, sin modificar esquemas1–35. Las tablas nuevas separan metadata regenerable de publicación del catálogo y uso personal persistente; se conservan incluso usos huérfanos. El JSON de copia versión8 transporta ese uso personal y mantiene la compatibilidad de copias previas sin la sección. No se incorporan el catálogo externo ni los motores de IA/nutrición del WIP principal.

## Verificación del árbol conjunto

La primera ejecución se detuvo exclusivamente en su trabajador propio por agotamiento del heap4GB durante compilación; no ejecutó tests y no se acredita como corrida verde. Se repite bajo el mismo gradle_slot.sh con heap6GB, sin modificar la cola o sus guardas.

Evidencia final verde por clase (38 clases/292 tests de la corrida ampliada y1/1 enfocado posterior; no nueva corrida conjunta verde): 39 clases/293 tests, cero fallos/errores/omitidos; 3705 fuentes/esquemas sin deriva. assembleBaseDebug aprobado. Firma/paquete KPKN Dev verificados. La corrida anterior ejecutó34 clases/279 tests con3 fallos: ruta Robolectric demasiado larga, ruta absoluta duplicada en un fixture y expectativa FTS35. Se corrigieron sólo esos fixtures y se repitió el conjunto completo, ampliado con settings/backup JSON. Esta repetición dio39 clases/293 tests,292 verdes y un fallo de hilo principal en el test de snapshot. Se cambió únicamente ese test a Dispatchers.IO y su verificación enfocada posterior aprobó1/1. Ningún assert de conservación se elimina. La expectativa de TrainingSnapshotRestoreTest se alineó36 antes de compilar tests en la corrida anterior.

Los3074 tests y la QA física del cierre anterior son evidencia histórica, no una nueva QA del árbol integrado. En esta integración no se accede al teléfono, no se borran datos y no se crean emuladores.

## Preservación del WIP

Antes de integrar se registraron hashes de21 archivos modificados, el índice vacío y el inventario de58161 rutas sin seguimiento del checkout principal. Sólo la compatibilidad Room36 y sus pruebas necesarias pasan al árbol integrado; los originales permanecen con los mismos bytes. El resto de código, herramientas, modelos, recursos y artefactos ajenos quedan fuera.

El avance local de master actualiza sólo rutas limpias de esta integración. Las rutas con WIP modificado o sin seguimiento se protegen de escritura/borrado; luego se cambia la referencia/index a la nueva base y se contrastan los hashes y la existencia de las rutas previas. Los archivos promovidos pueden dejar de aparecer como WIP al coincidir con la nueva base, sin alterar su contenido. No se usa reset, clean, stash ni force push.

Publicación directa autorizada a origin/master, mediante avance sin force push. El commit publicado y la confirmación remota se registran en el recibo local de publicación y en el informe final.

Recibos extensos, logs y huellas locales: C:/Users/valen/.codex/visualizations/2026/10/25/01a173b5-bf3a-7aa1-8c4d-39bc80e76c78/programs-integration. No contienen ni publican copias privadas de DB en Git.

El gate de retirada inicialmente señaló dos coincidencias del nombre WorkoutReadinessSheet procedentes del master local. La revisión semántica confirma que recoge DailyWellbeingLog y sólo persiste observaciones originales. Se permite exclusivamente ese token en dos archivos con SHA256 completo fijado; cualquier cambio del archivo, ruta distinta u otro patrón retirado sigue fallando. Los controles negativos y el gate final aprobaron.
