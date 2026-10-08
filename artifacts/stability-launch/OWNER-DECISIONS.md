# Decisiones del propietario

Fuente: respuestas humanas del 7 de octubre de 2026 en este chat.

- Free: entrenamiento; una semana reutilizable; máximo cinco sesiones creadas por semana; ring de energía visible.
- Free: rings muscular y de columna fuera del nivel gratuito; sin creación de programas avanzados.
- Pro: demás capacidades avanzadas de entrenamiento y alimentación.
- Free: voz y sincronización sin multimedia, aprobadas expresamente.
- Firebase: proyecto `kpkn-47b03`; configuración de aplicaciones y servicios de pruebas pendiente.
- Play: KPKN aún no tiene empresa. El tipo de cuenta aplicable está pendiente de confirmar; no se convierte constituir una empresa u obtener D-U-N-S en un requisito técnico de esta implementación.
- Aclaración posterior del propietario: cuestiona D-U-N-S para fitness. La ruta de organización del plan original se mantiene como interpretación de la política general, PENDIENTE de confirmación específica de Play para KPKN; no se presenta como una resolución individual de Google ni como bloqueo de la implementación técnica.

Fuentes revisadas el 7 de octubre de 2026: [Requisitos de Play Console](https://support.google.com/googleplay/android-developer/answer/10788890), [categorías de salud que incluyen fitness y nutrición](https://support.google.com/googleplay/android-developer/answer/13996367), [tipos de cuenta y D-U-N-S para organización](https://support.google.com/googleplay/android-developer/answer/13634885).
- Precios mensual y anual: PENDIENTES. Prueba aprobada: 14 días para usuarios elegibles según Google Play.

Propuestas pendientes de aprobación (no se convierten en restricciones por inferencia): límites de almacenamiento de multimedia; qué constituye exactamente el límite semanal al mantener varias rutinas. El acceso a datos existentes, su exportación y su eliminación permanece disponible, según el plan aprobado.

Ninguna de estas decisiones autoriza activar gasto, configurar una cuenta de producción, publicar, hacer push o modificar el trabajo de Entreno.

## Cambio de alcance para simplificar revisión

El propietario autoriza expresamente retirar Health Connect y conservar GPS de cardio exterior. Se excluyen del lanzamiento el SDK, los ocho permisos, la solicitud de acceso al proveedor y sus pantallas de conexión. El paquete D original queda sustituido por esta retirada; la integración de métricas/pantallas externa deja de ser requisito de este lanzamiento.

Se conservan entrenamiento, nutrición, mediciones manuales, AUGE, voz, cámara, GPS y datos históricos (incluidas mediciones que antes procedieran de Health Connect). La retirada no cambia el esquema Room ni elimina filas. Los enlaces antiguos se redirigen al progreso corporal. Se retiran las fuentes, recursos y manifiesto de `src/health`, la dependencia y el stub antiguo de `src/base`.

Eliminar Health Connect reduce el alcance de permisos y revisión del proveedor. La clasificación de fitness/nutrición, las declaraciones de datos y el tipo de cuenta de Play se revisan con las funcionalidades restantes; esta retirada no se registra como una exención concedida por Google.
