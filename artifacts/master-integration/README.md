# Integración local de estabilidad en master

El propietario solicita el 8 de octubre de 2026 llevar la entrega a master conservando las implementaciones paralelas. También autoriza corregir AUD06 con «Haz lo que me recomiendes y no dañe nada».

La integración combina master `381123d0a59ed8b3afdafe3f6353f1f9b26a1424` y la entrega `df7a1d8247096547a56546708674fad3638cd3eb`, con base común `f04578d3f9ffe3d7fc7c627d975ca4afd5df6d56`. Se prepara y prueba en `C:\kw\msi`, rama `codex/master-stability-integration`; la entrega auditada en b132 conserva su commit y sus fuentes.

Se preservan Entreno v2, los lotes del catálogo y las aproximaciones/movilidad de master. Las 382 rutas exclusivas protegidas coinciden exactamente por objeto Git. Las tres fixtures nuevas de activación declaran Pro explícitamente y conservan sus assertions. Se revisan los diez archivos compartidos, incluido el materializador: mantiene la aproximación de Entreno y la separación del historial por ejecución.

Los dos conflictos se combinan: minSdk 26/targetSdk 36 con versionCode 36/Pre Release 3; las seis actividades de depuración de Entreno con la configuración de red de estabilidad. Se retira Health Connect conservando GPS, voz, cámara, registros manuales y datos históricos.

AUD06 queda corregido: las marcas HEIC/HEIF/AVIF se distinguen de vídeo, incluidas las marcas compatibles dentro del bloque ftyp. La lectura está limitada a 4 KiB. Android y servidor admiten los mismos MIME y extensiones, manteniendo los controles de tamaño, SHA, cuenta y generación. Se prueban imágenes sintéticas completas de 16×16 y los controles existentes de JPEG/PNG/WebP/MP4. Los fixtures se generaron con [Pillow HEIF](https://github.com/bigcat88/pillow_heif); la clasificación AVIF sigue las marcas de la [especificación AVIF](https://aomediacodec.github.io/av1-avif/). No se convierten los archivos del usuario.

Validación del árbol final: 420 pruebas JVM de 45 suites, 0 fallos/errores/omisiones; 12 contratos Node locales con verificación real del servicio sobre un almacenamiento simulado; build debug y AndroidTest aprobado; tres pruebas de galería en Android API36 con decodificación, MIME y comparación exacta de bytes. Las 2584 huellas de fuentes/recursos/contratos coinciden con lo probado. El APK final contiene cero clases de Health Connect y MainActivity queda top-resumed. La entrada Free llega al inicio con solo el ring de energía y permite crear la semana manual.

APK final SHA256: `cba38c9a3002ec4f534675126537125c1316924dbce76a7adaa826ec049267f4`. APK AndroidTest: `ba6bdd9f7a41235d4e9fff3a0637cb453417c156c3e9bac79fb210bd9e3e7ed8`.

Antes del traslado, master conserva 333 cambios locales tracked, 577 entradas sin seguimiento y un índice vacío. No hay cruces de archivo/directorio con la entrega. Se guardan huellas frescas para verificarlas después de la integración. El resultado exacto del traslado está en el recibo local `integration-receipt.json`, creado después de actualizar master.

La integración local no declara el lanzamiento A–J aceptado. Los resultados históricos de la entrega fuente se conservan identificados; no sustituyen pruebas del árbol combinado. No se ejecutó aquí la suite JVM completa, lint/R8 Release nuevo, firma, publicación, validación de hardware físico/16 KiB, compras ni servicios desplegados. No se hizo push. J08 se actualiza para distinguir la evidencia histórica 54/55 de esta integración.
