# Inventario de datos y servicios — en revisión

Este inventario describe el código del checkout, no una configuración de producción activada ni una declaración ya presentada. Los permisos y dependencias del binario final deberán volver a comprobarse antes de publicar.

| Datos | Almacenamiento y finalidad | Salida del dispositivo |
|---|---|---|
| Rutinas, programas, sesiones, series, marcas e historial | Room del perfil; registro y planificación | Firebase, solo con cuenta y consentimiento de sincronización |
| Perfil, medidas corporales, sueño y bienestar | Room y preferencias del perfil; presentación y estimaciones | Sincronización personal si se habilita |
| Alimentación, planes, despensa y alimentos propios | Room del perfil; registro, búsqueda y estimaciones | Sincronización personal si se habilita; creación/edición de capacidades Pro |
| Coordenadas y trazas GPS | Archivos privados de la sesión; cardio exterior iniciado por el usuario | La portabilidad de rutas por sincronización todavía requiere cierre; el respaldo portátil las incluye |
| Fotografías, vídeo y resultados de pose | Archivos privados y asociaciones Room | Multimedia privada Pro; subida/descarga nueva pendiente de QA completa |
| Identidad y proveedores de cuenta | Firebase Auth; acceso y vinculación de proveedores | Firebase Auth y Google cuando se utiliza ese proveedor |
| Compra y vigencia de Pro | Token validado por el servidor, vínculo de propietario y caché firmada del dispositivo | Google Play y servidor Firebase; precios y ventas sin activar |
| Diagnóstico técnico | Cola limitada y archivos del perfil, con redacción y retención | Exportación elegida por el usuario; Crashlytics mediante consentimiento, pendiente de QA de producción |
| Copias exportadas | Archivo elegido por el usuario | Destino de exportación elegido; fuera del borrado remoto de la cuenta |

Servicios y bibliotecas:

- Firebase Auth identifica la cuenta y conserva sus proveedores. Las credenciales de SDK quedan fuera del respaldo portátil.
- Firebase Storage transporta archivos privados por UID y generación. Las reglas y el servidor validan acceso, tamaño y contenido; una cuenta borrada no puede subir ni leer datos.
- Firestore contiene versiones confirmadas, conflictos y recibos. El cliente no escribe directamente; las funciones verifican identidad y propiedad.
- App Check con Play Integrity se configura para producción. El entorno local no simula una validación real de integridad.
- Google Play Billing obtiene ofertas, moneda y precio y comunica tokens de compra. El servidor consulta y reconoce compras. No hay compras reales verificadas en este trabajo.
- Crashlytics permanece con recopilación automática desactivada; el controlador limita los mensajes e identidades de los informes manuales. Android Vitals necesita la distribución y las herramientas de Play. No existe evidencia de envío a producción.
- ML Kit realiza las funciones de análisis utilizadas por la app. La revisión final debe considerar también sus datos técnicos y descarga de modelos, además de los datos de entrada procesados localmente.
- Vosk usa el modelo español empaquetado; el idioma inglés y el corpus de validación siguen pendientes.
- Los proveedores de análisis remoto opcional y las opciones antiguas de diagnóstico necesitan una comprobación final de consentimiento y de redacción. No asumir que Firebase es el único destinatario posible por estar añadido en este cambio.

Licencias y procedencia comprobadas en el checkout:

- Vosk: `app/src/main/assets/licenses/VOSK_NOTICE.txt` y `APACHE-2.0.txt`.
- Syne e Inter: textos OFL y aviso en `app/src/main/assets/fonts/`.
- La optimización WebP conserva las imágenes originales y verifica sus píxeles; no certifica por sí sola sus derechos de distribución.
- Dataset compilado: origen versionado y proceso reproducible de `android-native/scripts/process_dataset.py`. La revisión de procedencia de fuentes alimentarias y de todas las imágenes aún está abierta.

Antes de publicar faltan: responsable y correo públicos, URLs definitivas, declaración exacta del binario release y todos sus SDK, licencias completas de catálogos/modelos/imágenes, configuración de retención operativa y evidencia de consentimiento real. Retirar Health Connect no cambia la obligación de declarar las funciones reales de actividad, nutrición y tratamiento de datos. [Declaración de salud de Play](https://support.google.com/googleplay/android-developer/answer/14738291), [datos de ML Kit](https://developers.google.com/ml-kit/android-data-disclosure), [control de recopilación de Crashlytics](https://firebase.google.com/docs/crashlytics/customize-crash-reports?platform=android#enable-reporting).
