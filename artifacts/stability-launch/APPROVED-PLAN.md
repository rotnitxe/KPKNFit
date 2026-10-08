# KPKN: plan integral de estabilidad y lanzamiento

## 1. Objetivo y decisiones fijadas

Preparar KPKN para lanzar en Google Play durante **noviembre de 2026**, conservando los datos de los usuarios y dejando preparada su continuidad en iOS.

**La versión auditada todavía no está lista para producción.** Hay defectos concretos de persistencia, sincronización y funcionamiento, además de requisitos de publicación pendientes.

El plan incorpora lo acordado:

- Una sola aplicación Android completa, sin separación Base/Health; Android 8 como mínimo.
- **Firebase Auth, Firestore y Storage**, conservando **Room para trabajar sin conexión**.
- Cuenta KPKN opcional para uso gratuito local; necesaria para sincronizar y suscribirse.
- Suscripción mensual y anual, con prueba de 14 días.
- Público adulto, lanzamiento internacional y experiencia completa en español e inglés.
- Cuenta de Google Play como **organización con D‑U‑N‑S**.
- La distribución de funciones Free/Pro y sus precios se definirán por separado. Son entradas necesarias antes de activar ventas.
- Este trabajo incluye infraestructura y sincronización de Health Connect. La integración restante de sus métricas y pantallas corresponde al trabajo separado que acordamos.

Noviembre es el objetivo de calendario. La publicación dependerá del cumplimiento de los criterios de aceptación.

## 2. Resultado de la auditoría realizada

Base revisada: commit `f04578d3`, Room v28. No se modificaron archivos de código.

| Comprobación | Resultado |
|---|---|
| Pruebas dirigidas de persistencia, nutrición, onboarding y migración | **50 aprobadas; 2 de voz omitidas** por falta de corpus |
| Suite completa | Inconclusa; se observó un fallo de rendimiento en `NutritionMetricsContractTest` |
| Catálogo de ejercicios y contratos de protocolos | Comprobaciones aprobadas |
| Coherencia del dataset generado | **Falló:** `dataset_knowledge.bin` está desactualizado |
| AAB BaseRelease | Generado, aproximadamente **359 MB**; firma verificada |
| Lint BaseRelease | Inconcluso: alcanzó el límite de 600 segundos |
| Compilación HealthRelease | Inconclusa: alcanzó el límite de 300 segundos |
| Pruebas funcionales del nuevo paquete en dispositivos | Pendientes |
| Compatibilidad de 16 KB | Alineación ELF revisada favorablemente; ejecución pendiente |

Los tiempos agotados representan verificaciones pendientes. El fallo de rendimiento requiere una ejecución controlada para separar una regresión de la carga concurrente del equipo.

Los siguientes paquetes cubren los hallazgos y las capacidades nuevas acordadas.

## 3. Implementación de todas las mejoras

### A. Recuperación y conservación de datos — prioridad crítica

**Hallazgos:** la restauración cierra Room y después reutiliza repositorios que mantienen la instancia cerrada; las copias se realizan sin coordinar todos los escritores; el respaldo JSON omite las asociaciones entre sesiones y archivos multimedia. La sustitución del archivo principal carece de validación y recuperación atómica suficientes. Evidencia principal: [DatabaseBackupHelper](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/main/java/com/example/kpkn/data/db/DatabaseBackupHelper.kt:13).

Implementar:

- Un `DataSessionCoordinator` que coordine repositorios, servicios y trabajadores durante restauración, borrado y cambio de cuenta.
- Copias consistentes, importación a un archivo temporal, validación de integridad y versión, sustitución atómica y recuperación ante interrupciones.
- Reconstrucción del conjunto de repositorios después de restaurar; la pantalla solo anunciará éxito cuando los datos restaurados puedan leerse.
- Respaldo portátil versionado que incluya todos los datos personales, relaciones, avatar y multimedia mediante identificadores portátiles.
- Compatibilidad de lectura con respaldos anteriores y migraciones no destructivas desde las versiones soportadas.
- Una comprobación automática que clasifique cada tabla y archivo como dato personal, catálogo regenerable o caché. Una tabla nueva sin clasificación bloqueará la validación.
- Límites de lectura antes de cargar importaciones e imágenes en memoria. Actualmente el avatar se lee completo antes de comprobar su tamaño.

La restauración durante un entrenamiento activo exigirá finalizarlo o cancelarlo expresamente antes de sustituir sus datos.

### B. Borrado completo y separación entre usuarios — prioridad crítica

**Hallazgos:** el borrado actual deja archivos de entrenamiento y snapshots; escritores activos pueden volver a generar información.

Implementar:

- Borrado coordinado de base local, preferencias personales, multimedia, snapshots, registros, archivos temporales y trabajos pendientes.
- Almacenes y trabajos separados por perfil. Cerrar sesión o cambiar de cuenta nunca mostrará información del usuario anterior.
- Diferenciar claramente «borrar datos de este dispositivo» y «eliminar cuenta KPKN».
- Eliminación de cuenta con reautenticación, revocación de sesiones, retirada de datos de Firestore y Storage y tratamiento explícito de suscripciones.
- Bloqueo de cargas desde dispositivos antiguos después de eliminar la cuenta.
- Política de retención y caducidad de copias operativas; los archivos exportados voluntariamente por el usuario se identificarán como copias bajo su control.

Habrá eliminación desde la app y una vía web funcional, accesible también después de desinstalarla. [Requisito de Google Play](https://support.google.com/googleplay/android-developer/answer/13327111).

### C. Cuentas KPKN y sincronización Firebase — capacidad nueva

Implementar autenticación inicial mediante correo y contraseña, recuperación de acceso y Google. La vinculación de proveedores requerirá demostrar control de las cuentas; coincidir en el correo no bastará para fusionarlas.

La arquitectura será:

- **Room:** almacenamiento utilizado por la interfaz y las operaciones sin conexión.
- **Firebase:** identidad y estado compartido confirmado entre dispositivos.
- **Cola persistente de operaciones:** cada cambio local y su operación pendiente se guardarán en la misma transacción.
- **Servidor:** validación de propiedad, versión e idempotencia antes de aceptar cambios.

Interfaces principales: `AccountRepository`, `SyncRepository`, `SyncStatus` y contratos de datos versionados independientes del esquema interno de Room.

Reglas de sincronización:

- Identificadores estables, revisiones del servidor y marcas de eliminación.
- Reintentos sin duplicados y recuperación después de cerrar el proceso.
- Registros independientes se combinan por identidad. Ediciones incompatibles conservan ambas versiones y requieren resolución explícita.
- Un entrenamiento activo pertenece a un dispositivo; no se mezclarán silenciosamente dos sesiones en curso.
- Incorporar datos de invitado a una cuenta será una operación verificable e idempotente.
- Multimedia privada con transferencias reanudables; mostrar archivos pendientes y última sincronización confirmada.
- Preservar unidades, fechas, zona horaria, procedencia y relaciones para el futuro cliente iOS.
- Catálogos regenerables, permisos del dispositivo y cachés quedan fuera de la sincronización personal.

Firestore utiliza *last write wins* para cambios sobre un mismo documento; por eso su funcionamiento sin conexión no sustituye este mecanismo de conservación de datos. [Documentación de Firebase](https://firebase.google.com/docs/firestore/manage-data/enable-offline).

Añadir reglas de acceso por usuario, pruebas de aislamiento, validación del servidor, App Check, límites de solicitudes, alertas de consumo y un ensayo de recuperación de copias. Los servicios nuevos no dependerán del backend experimental de análisis actual.

### D. Health Connect unificado — prioridad alta

**Hallazgos:** el flujo actual abre ajustes en lugar de solicitar permisos mediante el contrato correspondiente; faltan paginación y seguimiento completo de cambios; varias excepciones se convierten en listas vacías o resultados aparentemente satisfactorios. Evidencia: [HealthConnectManager](/C:/Users/valen/Documents/KPKNFit/android-native/app/src/health/java/com/example/kpkn/features/healthconnect/HealthConnectManager.kt:97).

Implementar:

- Integración en la aplicación única y actualización a la versión estable 1.1.0.
- Solicitud oficial de permisos, actualización al regresar a la app y explicación de privacidad accesible desde Health Connect.
- Estados diferenciados: no disponible, necesita actualización, permiso pendiente, sincronizando, completado y error.
- Lectura de todas las páginas accesibles, cambios incrementales, eliminaciones y recuperación de cursores caducados.
- Identificadores y versiones estables para escrituras; prevención de duplicados y ciclos de reimportación.
- Conservación del origen y la zona horaria de cada dato.
- Éxito comunicado únicamente después de persistir los resultados.
- Mantener los seis tipos de lectura y dos de escritura existentes, vinculados a funciones visibles y consentimiento.
- Funcionamiento local cuando Health Connect no esté disponible o se revoquen permisos.

Entregar al trabajo separado de interfaz un contrato `HealthConnectGateway`, estados observables y datos de prueba. Su integración completa será requisito de lanzamiento.

### E. Inicio, entrenamiento y servicios Android — prioridad alta

**Hallazgos:** existen operaciones bloqueantes en rutas de repositorios, inicialización con riesgo de estado parcial y un servicio de descanso declarado como `dataSync`.

Implementar:

- Inicialización asíncrona con estados de preparación, recuperación y error; los datos personales estarán disponibles sin esperar la preparación completa de catálogos.
- Sustituir operaciones bloqueantes de eliminación y mantenimiento por funciones suspendidas con confirmación durable y errores visibles.
- Temporizador basado en tiempo monotónico persistido, notificación con cronómetro y alarmas según permisos. Eliminar el uso de `dataSync` para mantener ese reloj.
- Mantener servicios de micrófono y ubicación únicamente durante funciones iniciadas por el usuario, con tipos y permisos adecuados.
- Solicitar ubicación, cámara, micrófono y notificaciones cuando se utilicen.
- Corregir la petición de ubicación precisa aislada y soportar ubicación aproximada.
- Recuperación coherente ante proceso terminado, bloqueo de pantalla, reinicio, interrupciones de audio, desconexión Bluetooth y revocación de permisos.

### F. Suscripciones y derechos de acceso — capacidad nueva

Implementar Google Play Billing 9.1.0 con un producto Pro, planes mensual/anual y oferta de prueba de 14 días para usuarios elegibles.

- Mostrar precios, moneda, duración y renovación obtenidos de Google Play.
- Exigir cuenta KPKN antes de contratar.
- Validar y reconocer compras en el servidor.
- Procesar notificaciones de Google de forma idempotente, incluyendo renovación, cancelación, reembolso, compra pendiente, suspensión y recuperación.
- Vincular cada compra a una única cuenta KPKN.
- Exponer `EntitlementRepository` y un catálogo de capacidades que recibirá la futura matriz Free/Pro.
- Permitir restaurar compras y mantener derechos confirmados sin conexión hasta su vigencia verificada.
- Una caída del servidor o una suscripción vencida nunca borrará datos ni impedirá leerlos, exportarlos o eliminar la cuenta.

La activación de ventas quedará bloqueada hasta incorporar precios y matriz Free/Pro. [Validación segura de compras](https://developer.android.com/google/play/billing/security).

### G. Nutrición, catálogos y progresión — prioridad alta

**Hallazgos:** dataset generado desactualizado, búsqueda con múltiples barridos `LIKE`, cantidades explícitas mal resueltas en ciertos casos, ambigüedades de identidad y pendientes documentados del sistema alimentario. La rematerialización también conserva marcas de progresión nativa capaces de interferir con una nueva ejecución.

Implementar:

- Regenerar el dataset mediante su proceso documentado y convertir la comprobación de coherencia en requisito de publicación.
- Resolver cantidades explícitas en gramos, kilogramos y litros, considerando densidades cuando corresponda.
- Corregir negaciones, variantes preparadas, porciones, rendimientos y aceite de cocción.
- Presentar cantidades ambiguas como estimaciones que requieren confirmación; no inventar cantidades para hacer coincidir un corpus.
- Revisar los casos alimentarios pendientes con fuentes y recetas trazables, preservando las diferencias regionales válidas.
- Completar el contrato de conocimiento y su disponibilidad desde el inicio; retirar cálculos y canales sin consumidores conservando datos históricos.
- Actualizar el índice FTS existente para la recuperación normalizada utilizada por la búsqueda real, conservando ranking, alias e identidad.
- Asociar las marcas de progresión a cada ejecución del programa, manteniendo el historial y evitando bloquear progresiones nuevas.
- Versionar recetas de programas; actualizar planes existentes mediante una acción explícita que preserve sesiones entrenadas.
- Mantener pruebas de paridad Android/iOS/backend cuando cambien motores compartidos.

Cerrar el rendimiento con mediciones en Android y una ejecución controlada de E16, conservando sus criterios de precisión.

### H. Español, inglés, adultos y accesibilidad — prioridad alta

**Hallazgos:** hay numerosos textos españoles incrustados, cobertura incompleta en inglés, voz ligada al español y validación de edades desde 13 años.

Implementar:

- Recursos localizados para pantallas, errores, notificaciones, catálogos, permisos y compras.
- Gramáticas, reconocimiento y síntesis de voz adecuados para español e inglés.
- Formatos de fechas, números y unidades consistentes sin alterar los valores almacenados.
- Elegibilidad de 18 años en onboarding y contratos de validación.
- Tratamiento explícito de perfiles anteriores fuera del público admitido, permitiendo exportar y borrar sus datos.
- Accesibilidad con TalkBack, texto ampliado, objetivos táctiles adecuados, orientación, pantallas plegables y presentación correcta al subir el SDK objetivo.

### I. Privacidad y diagnóstico — prioridad alta

**Hallazgos:** la cola de diagnóstico no tiene límite efectivo, se registran transcripciones y `setCrashlyticsEnabled` solamente escribe un evento local.

Implementar:

- Cola acotada con política de descarte y contadores; nunca bloquear la interfaz para registrar eventos.
- Retirada de transcripciones y datos sensibles del diagnóstico de producción.
- Redacción recursiva, retención limitada y exportación de diagnóstico controlada por el usuario.
- Integración real de Crashlytics con control de recopilación, sin contenido de salud ni texto dictado; Android Vitals como fuente adicional.
- Inventario de datos propios y de SDK, incluyendo ML Kit, Firebase y facturación.
- Política de privacidad, condiciones, consentimiento de sincronización y avisos de salud coherentes con el comportamiento efectivo.
- Revisar licencias y procedencia de datasets, modelos e imágenes.

La ficha y la app deberán cumplir las declaraciones de salud y privacidad correspondientes. [Política de salud de Google Play](https://support.google.com/googleplay/android-developer/answer/16679511).

### J. Compilación, tamaño y publicación — prioridad alta

**Hallazgos:** `targetSdk` sigue en 35; Base y Health están separados; debug puede utilizar firma de release; falta una canalización Android de validación. Los recursos visuales aportan aproximadamente 250 MB comprimidos al AAB.

Implementar:

- `targetSdk 36`, `minSdk 26` y un único conjunto funcional con variantes debug/release. Conservar aliases `BaseRelease` para los scripts que los necesiten.
- Identidad y firma separadas para debug; release fallará si falta su configuración de firma.
- Play App Signing, protección de la clave de subida y registro del identificador de aplicación.
- Optimización de imágenes mediante scripts reproducibles, reducción de duplicaciones y reglas R8 ajustadas con pruebas.
- Medición del tamaño entregado por dispositivo mediante bundletool y Play Console.
- Pruebas de bibliotecas nativas, empaquetado y ejecución con páginas de 16 KB.
- CI con pruebas, migraciones, coherencia de catálogos, lint completo y compilación release; conservar informes y hashes por commit.
- Resolver el tiempo de ejecución de lint y completar la compilación unificada antes de aceptar el paquete.

API 36 es un requisito vigente para nuevas publicaciones. El tamaño del AAB por sí solo no equivale al tamaño descargado: Google calcula sus límites sobre la entrega comprimida. [SDK objetivo](https://developer.android.com/google/play/requirements/target-sdk), [límites de tamaño](https://support.google.com/googleplay/android-developer/answer/9859372).

## 4. Pruebas y criterios de aceptación

| Área | Evidencia exigida |
|---|---|
| Persistencia | Migraciones, restauración interrumpida, archivo inválido, almacenamiento lleno y conservación de relaciones y multimedia |
| Sincronización | Dos dispositivos, cambios desconectados, conflictos, reintentos, reinstalación y ausencia de duplicados |
| Identidad | Invitado a cuenta, recuperación, vinculación, cambio A/B y rechazo de acceso a datos ajenos |
| Eliminación | Borrado local y remoto completo; un dispositivo antiguo no puede resucitar datos |
| Portabilidad iOS | Lectura y escritura del contrato compartido con fixtures equivalentes, preservando unidades, fechas y relaciones |
| Health Connect | Permisos parciales/revocados, paginación, cambios, eliminaciones, escrituras repetidas y proveedor ausente |
| Suscripciones | Prueba, compra pendiente, renovación, cancelación, reembolso, restauración y notificaciones repetidas |
| Funciones principales | Onboarding, programas, entrenamiento, voz, nutrición, progreso, cámara, GPS y notificaciones en ambos idiomas |
| Rendimiento | Inicio, búsqueda, resolución nutricional, memoria y sesiones prolongadas medidos en dispositivo |
| Distribución | Release con R8 instalado y ejecutado, `MainActivity` visible, registros limpios y pruebas de 16 KB |

La matriz incluirá versiones representativas desde API 26 hasta 36, un dispositivo ARM64 físico y un entorno de 16 KB.

Condiciones para publicar:

- Cero defectos abiertos con pérdida de datos, exposición entre cuentas, cobros incorrectos o cierres reproducibles en funciones principales.
- Todos los paquetes anteriores cerrados con evidencia.
- Suite completa, lint y release aprobados; las pruebas de voz disponen de corpus y se ejecutan.
- Búsqueda de alimentos por debajo del presupuesto existente de 150 ms y E16 conforme a su contrato en un entorno controlado.
- Beta cerrada de 14 días con al menos 12 participantes como criterio propio de calidad, con seguimiento de incidencias.
- Declaraciones, URLs públicas, cuenta de revisión, permisos y ficha de Play completos.

## 5. Secuencia y calendario objetivo

1. **7–14 de octubre:** iniciar organización, D‑U‑N‑S y verificaciones; asegurar recuperación y borrado; establecer validaciones automáticas y configuración Android unificada.
2. **15–24 de octubre:** cuentas y sincronización, suscripciones, Health Connect, correcciones de motores, privacidad y localización.
3. **25 de octubre–8 de noviembre:** candidato completo, pruebas en dispositivos, recuperación de datos y beta cerrada.
4. **9–22 de noviembre:** corregir incidencias, cerrar rendimiento y accesibilidad, completar revisión de Google.
5. **23–30 de noviembre:** publicación cuando se cumplan los criterios; monitorización de fallos, sincronización y compras.

Dependencias externas: verificación de la organización, D‑U‑N‑S, configuración de Firebase y Play, matriz Free/Pro y precios, integración pendiente de Health Connect y aprobación de Google.

Cada hallazgo mantendrá trazabilidad entre evidencia, cambio, prueba y resultado. Si estas dependencias o las pruebas no permiten cerrar todo durante noviembre, se ajustará la fecha conservando el alcance acordado.
