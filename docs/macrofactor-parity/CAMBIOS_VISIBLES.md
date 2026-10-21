# Cambios visibles de la ola MFP

Cada paquete añade aquí lo que la persona ya veía y ahora cambia (cifras, rótulos, comportamientos). Es la base de las notas de versión. Nunca se reescriben registros guardados: solo cambia la presentación o el cálculo hacia delante.

| Paquete | Qué cambia | Antes → después | Por qué |
|---|---|---|---|
| H1 | Guardar una comida desde el atajo de creatina o desde `addLog` | Parecía guardada al instante aunque Room fallara (se perdía al reiniciar) → ahora espera a que Room confirme y, si falla, no la publica y avisa «No se pudo guardar la comida. Inténtalo de nuevo.» | Evita el falso éxito |
| H1 | Carga de nutrición con una fila ilegible | Una sola fila rota dejaba vacíos el diario, las plantillas y los planes → ahora cargan las filas sanas y la ilegible queda intacta en cuarentena (no se borra ni se sobrescribe) | No ocultar datos sanos |
| H1 | Creatina: tour de inicio | Se lanzaba antes de guardar → solo tras un guardado confirmado | Coherencia con el falso éxito |
