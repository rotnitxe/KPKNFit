# Contrato personal v1

El sobre distingue identidad, revisión del servidor, zona horaria, preferencias de visualización y contenido semántico. Los nombres de entidad son públicos; no se envían nombres de tablas ni la versión de Room. Los catálogos regenerables no son entidades personales.

`entityId` es estable y admite caracteres ASCII de la especificación. `sourceId` conserva claves compuestas originales, aunque se utilice un hash seguro como identificador del documento. `accountGeneration` se obtiene del servidor y cambia al pedir el borrado: un cliente con una generación anterior no puede restaurar datos de una cuenta eliminada. `operationId` se conserva hasta recibir un comprobante y no puede reutilizarse con otro contenido.

Si `sourceId` está presente, `entityId` coincide con ella cuando cumple el formato ASCII, o con `r_` seguido de su SHA256 UTF-8. Esta relación se verifica en el servidor y se incluye en los fixtures Swift/Android. Una identidad pública no puede escribir en otra clave local al aplicarse.

`retirePendingOperation` cancela la entrega de una operación obsoleta que todavía no fue aceptada. Si ya se aceptó, devuelve su comprobante original. Retiro y escritura compiten en una transacción; una entrega atrasada no modifica la entidad después del retiro. Esto permite conservar cambios Pro locales pendientes y enviar un borrado sin depender de renovar la suscripción. El retiro no borra la versión confirmada del servidor; el cliente envía el tombstone correspondiente por el journal habitual.

`unitSystem` conserva la preferencia de presentación. Las magnitudes con unidad explícita (`weightKg`, gramos, segundos) permanecen canónicas; una preferencia Imperial nunca convierte silenciosamente un número almacenado. Las fechas civiles se preservan como fechas, las marcas temporales como milisegundos UTC y `zoneId` como zona IANA u offset válido. Los valores de procedencia, incluidos orígenes históricos, se conservan.

Las eliminaciones llevan `deleted=true,payload=null`. Un contenido grande usa `payloadRef` con tamaño y SHA-256 de sus bytes UTF-8; el servidor comprueba el archivo privado antes de aceptarlo y conserva una referencia inmutable. Los datos estructurados se sincronizan en Free. Los archivos multimedia usan una transferencia separada con derechos Pro, manteniendo su lectura y eliminación tras vencer Pro.

Los campos adicionales se conservan en el contrato; los clientes que no conocen una clase de entidad deben detener esa importación, mostrar un error y conservar su cursor. Una edición incompatible conserva la versión entrante en `conflicts`, junto a la versión confirmada. El cliente debe mostrar ambas y pedir una decisión. Una sesión en curso no se mueve a otro dispositivo por vencer un latido: se necesita finalizarla o cancelarla expresamente, con conservación de su versión anterior.

`fixtures.json` contiene casos comunes para Android, Swift y Node. Las verificaciones de fixtures no equivalen a pruebas de red con dos dispositivos ni a la compilación de iOS.
