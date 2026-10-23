# Guardar plantilla desde D4

QA nativa en APK0c642bff1: Asistente → Plantillas → Guardar sesión, nombre no vacío y descripción vacía, mostró dos veces el error genérico de reintentar. El log de la app no aporta una excepción de SessionTemplateRepository; todavía no se atribuye a Room ni se concluye un fallo general del guardado.

La revisión confirmó que los callbacks Screen→Assistant→Browser llegan al comando durable. Sí había una pérdida de información: buildCurrentSessionTemplate devolvía null tanto por nombre/sesión ausentes como por receta incompleta; la UI convertía ese fracaso de validación en el mismo «Inténtalo de nuevo» usado para almacenamiento. Reintentar no corrige una dosis incompleta.

Ahora el dominio distingue tres motivos de validación, el ViewModel los conserva en Result y el diálogo indica qué completar. El mensaje genérico se mantiene para fallos de almacenamiento y no expone detalles internos. La validación estricta de contenido, el borrador y el orden Room→Flow se conservan.

Regresión preparada en SessionEditorViewModelRulesTest: sesión con dosis vacía → explicación concreta y ninguna plantilla fantasma → completar8reps → guardado durable con descripción vacía. También comprueba que un error interno conserva el mensaje genérico. Falta ejecutar por la cola central y repetir D4 con APK actualizado; se pidió contrastar en el emulador una sesión simple válida para clasificar el incidente original.
