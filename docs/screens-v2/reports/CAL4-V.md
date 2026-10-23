# CAL4-V · APRE en vivo · en curso

Worktree u20, codex/sv2-cal4v, base provisional acc75872c. El usuario autoriza autonomía y paralelismo. Para aprovechar la validación central de fase3 se adelanta implementación aislada: antes de validar o integrar fase4 se fusionará la fase3 completa y verde. No se cambia el orden de integración, Room, datos personales ni la cola única propia.

Primera parte: identidad pura del bloque por sus cuatro IDs y variante. Un marcador editado inválido no vuelve a identificarse por parecido. El respaldo legado exige el protocolo apre-mann, su regla ApreXrm y las cuatro prescripciones reales. S3 y S4 son AMRAP, nunca CALIBRATOR. Series ajenas añadidas fuera del bloque no se convierten en S4.

Siete pruebas de identidad preparadas, sin ejecutar aún. La ruta en vivo, persistencia, decisiones S3 y propuestas S4 siguen pendientes. Este informe no acredita compilación, QA ni APK.


S3 preparada: usa Apre.set4LoadKg sobre carga externa y reps completas de CAL1, sin descontar otra vez partialReps. El registro serializa sólo metadatos APRE en el JSON actual y mantiene capacityKg=null: nunca se acepta como1RM. La decisión apunta exclusivamente a S4 de ese contexto/lado, con conservación de hechas/fijadas y asignación absoluta. Correcciones y undo reconstruyen/sustituyen la decisión; un marcador modificado no habilita la propuesta antigua. V7 distingue Mann de una capacidad estimada y tiene objetivos48. Ocho pruebas Android preparadas sobre tablas, parcialidad, idempotencia, lados, protección, corrección y compatibilidad JSON. No ejecutadas aún.

Se corrige también una lectura de carga completada al revertir: calibrationLoadForSet devolvía la base previa aunque el CompletedSet conservaba la carga real. El consumidor ahora devuelve la carga externa registrada, y sólo restaura pendientes; la prueba APRE exige155kg ya hechos tras revertir. Se repetirán las regresiones CAL3 para comprobar este contrato compartido.

Pendientes: propuestas de XRM al cerrarS4 porOFF/PROPONER/AUTO, persistencia real con recorder/Room, renders físicos312x425, actualización de la base a fase3 verde y validación centralizada.
