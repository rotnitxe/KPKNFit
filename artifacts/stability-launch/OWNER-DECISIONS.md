# Decisiones del propietario

Fuente: respuestas humanas del 7 de octubre de 2026 en este chat.

- Free: entrenamiento; una semana reutilizable; máximo cinco sesiones creadas por semana; ring de energía visible.
- Free: rings muscular y de columna fuera del nivel gratuito; sin creación de programas avanzados.
- Pro: demás capacidades avanzadas de entrenamiento y alimentación.
- Free: voz y sincronización sin multimedia, aprobadas expresamente.
- Firebase: proyecto `kpkn-47b03`; configuración de aplicaciones y servicios de pruebas pendiente.
- Play: KPKN aún no tiene empresa. El tipo de cuenta aplicable está pendiente de confirmar; no se convierte constituir una empresa u obtener D-U-N-S en un requisito técnico de esta implementación.
- Aclaración posterior del propietario: cuestiona D-U-N-S para fitness. El tipo de cuenta aún debe confirmarse para las funciones restantes de KPKN. D-U-N-S corresponde a la verificación de una cuenta de organización; su necesidad no se deduce de integrar o retirar Health Connect. No existe una resolución individual de Google para esta app y no se usa esa cuestión para bloquear la implementación técnica.

Fuentes revisadas el 7 de octubre de 2026: [Requisitos de Play Console](https://support.google.com/googleplay/android-developer/answer/10788890), [categorías de salud que incluyen fitness y nutrición](https://support.google.com/googleplay/android-developer/answer/13996367), [tipos de cuenta y D-U-N-S para organización](https://support.google.com/googleplay/android-developer/answer/13634885).
Verificación del 8 de octubre: [formulario que incluye actividad física y nutrición](https://support.google.com/googleplay/android-developer/answer/14738291?hl=es) y [tipos de cuenta](https://support.google.com/googleplay/android-developer/answer/13634885?hl=en). La declaración de esas funciones es independiente del SDK retirado y del número D-U-N-S.
- Precios mensual y anual: PENDIENTES. Prueba aprobada: 14 días para usuarios elegibles según Google Play.

Propuestas pendientes de aprobación (no se convierten en restricciones por inferencia): límites de almacenamiento de multimedia; qué constituye exactamente el límite semanal al mantener varias rutinas. El acceso a datos existentes, su exportación y su eliminación permanece disponible, según el plan aprobado.

Ninguna de estas decisiones autoriza activar gasto, configurar una cuenta de producción, publicar, hacer push o modificar el trabajo de Entreno.

## Cambio de alcance para simplificar revisión

El propietario autoriza expresamente retirar Health Connect y conservar GPS de cardio exterior. Se excluyen del lanzamiento el SDK, los ocho permisos, la solicitud de acceso al proveedor y sus pantallas de conexión. El paquete D original queda sustituido por esta retirada; la integración de métricas/pantallas externa deja de ser requisito de este lanzamiento.

Se conservan entrenamiento, nutrición, mediciones manuales, AUGE, voz, cámara, GPS y datos históricos (incluidas mediciones que antes procedieran de Health Connect). La retirada no cambia el esquema Room ni elimina filas. Los enlaces antiguos se redirigen al progreso corporal. Se retiran las fuentes, recursos y manifiesto de `src/health`, la dependencia y el stub antiguo de `src/base`.

Eliminar Health Connect reduce el alcance de permisos y revisión del proveedor. La clasificación de fitness/nutrición, las declaraciones de datos y el tipo de cuenta de Play se revisan con las funcionalidades restantes; esta retirada no se registra como una exención concedida por Google.

## Integración local autorizada el 8 de octubre de 2026

El propietario solicita: «necesito que lleves estos cambios al master, pero cuidando las implementaciones paralelas que se dieron».

Esta instrucción autoriza combinar la entrega `df7a1d8247096547a56546708674fad3638cd3eb` con el master actual, resolver los cruces y adaptar las fixtures del asistente completo a Pro explícito conservando sus assertions. Se preservan el asistente Entreno v2, el catálogo y los cambios locales ajenos. La limitación anterior de no integrar ni modificar los cruces con Entreno queda sustituida para esta integración. No autoriza push, publicación, despliegue, activación de compras ni acceso a cuentas externas.

El propietario autoriza la corrección adicional AUD06 y sus pruebas con su respuesta: «Haz lo que me recomiendes y no dañe nada». El arreglo preserva los bytes de las fotos HEIC/HEIF/AVIF y armoniza su clasificación, extensiones, galería y referencias multimedia del servidor. La validación usa medios sintéticos, sin publicar ni acceder a servicios externos.
