# Guardar plantilla desde D4

QA nativa en APK0c642bff1: Asistente → Plantillas → Guardar sesión, nombre no vacío y descripción vacía, mostró dos veces el error genérico de reintentar. El log de la app no aporta una excepción de SessionTemplateRepository; todavía no se atribuye a Room ni se concluye un fallo general del guardado.

La revisión confirmó que los callbacks Screen→Assistant→Browser llegan al comando durable. Sí había una pérdida de información: buildCurrentSessionTemplate devolvía null tanto por nombre/sesión ausentes como por receta incompleta; la UI convertía ese fracaso de validación en el mismo «Inténtalo de nuevo» usado para almacenamiento. Reintentar no corrige una dosis incompleta.

Ahora el dominio distingue tres motivos de validación, el ViewModel los conserva en Result y el diálogo indica qué completar. El mensaje genérico se mantiene para fallos de almacenamiento y no expone detalles internos. La validación estricta de contenido, el borrador y el orden Room→Flow se conservan.

Regresión preparada en SessionEditorViewModelRulesTest: sesión con dosis vacía → explicación concreta y ninguna plantilla fantasma → completar8reps → guardado durable con descripción vacía. También comprueba que un error interno conserva el mensaje genérico. Falta ejecutar por la cola central y repetir D4 con APK actualizado; se pidió contrastar en el emulador una sesión simple válida para clasificar el incidente original.

## Contraste y confirmación de aplicar

C3 guardó una sesión nueva sólo de Cinta20min/RPE6 con nombreQA y descripción vacía: el formulario cerró y Mis plantillas pasó0→1. El incidente original se acota al contenido de TorsoB, sin atribuirlo a un guardado general roto. PNG D4-cardio-simple-save-result-1792763054011707800 en C:/kw/shots/screens-v2-final-emu-20261023-c3.

Al aplicar esa plantilla sobre una sesión ocupada, la confirmación recortaba «Reemplazar» a «…» a740/130 (D4-template-apply-confirm-1792763171240202300). Ambas superficies de plantillas usan ahora el mismo diálogo con decisiones verticales de ancho completo. Se mantienen los callbacks REPLACE/APPEND y no se aplica nada al abrir. TemplateApplyPhysicalViewportTest verifica etiquetas completas, viewport y objetivos48dp a740/880 y100/130, más ambos callbacks y cuatro PNG. Pendiente de ejecución central y retest del APK.

## Ejecución central · 23-oct-2026

La tercera completa Android, fuente b7910feaf, dejó verdes SessionEditorViewModelRulesTest y los cuatro casos de TemplateApplyPhysicalViewportTest. El wrapper global cerró exit1 por seis fallos de otras clases; no se declara verde el barrido completo. El retest nativo con APK nuevo sigue pendiente. Sólo ese contraste puede confirmar si el fallo original de TorsoB era validación de dosis o un error de almacenamiento distinto.
