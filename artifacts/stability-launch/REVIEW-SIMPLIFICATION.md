# Simplificación de la revisión de Google Play

## Alcance autorizado el 7 de octubre de 2026

Retirar Health Connect; conservar GPS exterior, voz, cámara, registro manual, nutrición y motores de entrenamiento. Esta decisión reemplaza el paquete D del plan inicial para el lanzamiento. No se elimina información personal ni se modifica el esquema Room para retirarlo.

| Elemento | Decisión y efecto en la experiencia | Evidencia |
|---|---|---|
| Health Connect | Retirados SDK, ocho permisos, fuentes, recursos, consulta al proveedor y acceso desde Ajustes. Ya no importa mediciones de otras apps; los registros previos permanecen. Los enlaces antiguos abren progreso corporal. | `app/build.gradle.kts`, manifiesto principal, navegación y fábrica de cardio |
| GPS de cardio | Conservar por decisión expresa. Solicitar ubicación al iniciar cardio; admite ubicación aproximada. Sin permiso de ubicación en segundo plano. | `CardioGpsForegroundService`, permiso `location`, lanzador de WorkoutScreen |
| Voz | Conservar. Micrófono vinculado a entrenamiento iniciado por el usuario; reconocimiento local Vosk y reconocimiento nativo en el dispositivo. | `WorkoutNativeOnDevicePolicy`, `WorkoutNativeOneShotRecognizer`, servicio `microphone` |
| Cámara y multimedia | Conservar captura/importación voluntaria. Usa almacenamiento privado y selección de archivos; sin acceso amplio a biblioteca o a todos los archivos. | Captura de entrenamiento, `WorkoutMediaStore`, manifiesto |
| Temporizador de descanso | Notificación con cronómetro y alarmas; retirado el servicio foreground `dataSync` y su permiso. | `RestNotificationPublisher`, `WorkoutRestAlertManager`, manifiesto |
| Alarmas exactas | Conservar por ahora: descansos y recordatorios tienen consumidores. Hay alternativa inexacta cuando falta permiso. Retirarlas sin adaptar y medir retrasos cambiaría la experiencia. | Gestores de recordatorios y descanso |
| Escritura externa antigua | Pendiente de adaptar: la exportación a galería en API 26–28 utiliza MediaStore. No se elimina el permiso antes de ofrecer alternativa funcional. | `WorkoutMediaGallerySaver` |
| Analítica/anuncios | No añadir SDK de anuncios ni Firebase Analytics al paquete. Diagnóstico local acotado y redactado. Crashlytics sigue siendo un punto pendiente del plan, a revisar con consentimiento e inventario. | Dependencias actuales, `KpknDiagnosticLogger`, `DiagnosticRedactor` |
| Análisis de vídeo ML Kit | Conservar el análisis de postura solicitado por el usuario. Google documenta procesamiento local de imágenes/resultados y envío de métricas técnicas del SDK. Incluir ese tratamiento en privacidad y Seguridad de los datos; no afirmar que todo el paquete carece de tráfico. | `PoseTrajectoryAnalyzer`, dependencia `pose-detection`, documentación oficial de ML Kit |
| Cuentas y suscripciones | Conservar por la continuidad Android/iOS y la matriz Free/Pro aprobada. Requieren borrado de cuenta, acceso para revisión y ofertas/precios verificados. | Paquetes B/C/F pendientes |
| Lenguaje médico | Revisar texto visible que pueda confundirse con diagnóstico/tratamiento. Mantener estimaciones de entrenamiento y avisos claros; no modificar cálculos ni históricos. Área de programas pendiente de compatibilidad con Entreno. | Tarjeta «Adherencia y diagnóstico» en programas; Home ya distingue sugerencia de diagnóstico |

## Límites de evidencia

Comprobación actual del APK38 (8 de octubre): 68.497 clases inspeccionadas, cero clases Health Connect; GPS, micrófono y cámara conservados. Sin ubicación en segundo plano, acceso amplio a fotos/vídeos, acceso a todos los archivos ni foreground `dataSync`. El empaquetado ELF/ZIP pasó los criterios de 16 KB; la ejecución en un sistema de 16 KB continúa inconclusa. Ajustes API36 mantiene ubicación aproximada y no ofrece Health Connect. Estas comprobaciones están en `review-apk-verification-38.json`, `native-packaging-38.json` y `emulator-ui-38/settings/`.

Google incluye expresamente actividad/forma física y nutrición/control del peso en su declaración de funciones de salud. Retirar Health Connect reduce permisos e integración, pero no autoriza declarar que KPKN carece de esas funciones. La clasificación de cuenta y el eventual D‑U‑N‑S se revisan por separado; no se condiciona el trabajo técnico a crear una empresa. [Formulario oficial de Play](https://support.google.com/googleplay/android-developer/answer/14738291?hl=es).

Verificación propia de este cambio, el 7 de octubre de 2026:

- Compilación debug aprobada (`gradle-snapshot-release-manifest.log`).
- Manifiestos fusionados debug/release y dependencias release aprobados: sin Health Connect ni servicio `dataSync` (`review-apk-verification.json`, `review-release-verification.json`).
- Inspección de 62.619 clases del APK: cero clases del SDK o de la integración retirada. Se conservaron permisos de GPS, micrófono y cámara.
- APK instalado en la identidad aislada `com.example.kpkn.stability`, Android 16/API 36. MainActivity quedó `topResumedActivity`; captura y árbol UI muestran la bienvenida de KPKN (`emulator-health-removal/`). El emulador temporal utiliza el AVD en modo de solo lectura.
- Las cuatro pruebas de snapshots/restauración aprobaron después de ajustar ejecución IO y nombres de archivos temporales para SQLite en Windows. Las otras 17 pruebas dirigidas de mantenimiento, límites, diagnóstico, políticas y temporizador aprobaron en la ejecución anterior. Esto no representa la suite completa.

La QA registrada comprueba instalación y arranque. Los flujos de Ajustes, enlaces antiguos, registros históricos y GPS sobre recorrido real quedan por ensayar; no se presentan como pruebas ejecutadas. No se realizó publicación, revisión de Play ni aceptación del plan completo de lanzamiento.

Actualización del 8 de octubre: APK 18 verificado con 68.420 clases, cero SDK/permisos HC y GPS/micrófono/cámara conservados (`review-apk-verification-18.json`). Ajustes se inspeccionó en inglés en API36, con ubicación aproximada y sin HC (`emulator-current-18/settings-en/`). Los 14 enlaces del resolver aprobaron en build16 y las pruebas de conservación histórica/migración multimedia también aprobaron. GPS en un recorrido real sigue pendiente. La pantalla de admisión de adultos presentó contraste incorrecto y su corrección está en build21, todavía sin evidencia visual del nuevo APK.

La suite JVM completa 19 alcanzó 6.808 pruebas, con un fallo de hidratación asíncrona y dos omisiones de replay de voz. Lint20 completó y señaló 45 errores; se corrigen en las rondas21/22. Estos resultados no son aceptación del lanzamiento. El entorno16KB informó PAGE_SIZE=16384 pero sufrió fallos de system_server/GMS, por lo que esa prueba de ejecución queda inconclusa.

LeakCanary agrega `READ_EXTERNAL_STORAGE` en debug. El manifiesto release confirma que ese permiso no está en producción.

Retirar el proveedor no elimina la necesidad de declarar las funciones reales de fitness/nutrición y el manejo de datos personales. El tipo de cuenta de Play para KPKN queda pendiente de confirmación específica, como se registra en `OWNER-DECISIONS.md`.

Fuentes oficiales: [Permisos y APIs sensibles](https://support.google.com/googleplay/android-developer/answer/16558241), [salud y privacidad](https://support.google.com/googleplay/android-developer/answer/16679511), [eliminación de cuentas](https://support.google.com/googleplay/android-developer/answer/13327111), [privacidad de ML Kit](https://developers.google.com/ml-kit/terms), [declaración de datos de ML Kit](https://developers.google.com/ml-kit/android-data-disclosure).

Actualización verificada del 8 de octubre (rondas23–25): APK23 arrancó con MainActivity visible en API26 y API36; el contraste de la puerta de adultos se comprobó en captura. Se inspeccionaron 68.424 clases del APK23 sin SDK/permisos HC. Lint22 finalizó con cero errores, 612 advertencias y 39 recomendaciones. Suite24: 6.813 pruebas, 6.811 aprobadas, cero fallos y dos omisiones de voz por falta de corpus real autorizado. Ronda25 posterior: 106 pruebas dirigidas aprobadas y assembleDebug, incluidas cinco de barrera del proceso de voz y cantidades explícitas de alimentos; nueva QA de exportación en curso.

Los límites actuales de Play se calculan sobre descarga comprimida, y la documentación de Play Console indica 500 MB para el módulo base. Superar 200 MB produce un aviso no bloqueante en conexiones móviles. Esto evita introducir una descarga separada de modelos o ilustraciones por asumir que 200 MB es un rechazo automático. Hace falta medir la entrega del AAB real por dispositivo; el APK debug universal no es esa medida. [Límites oficiales de tamaño](https://support.google.com/googleplay/android-developer/answer/9859372?hl=en).

QA del APK28 en API36: instalación y MainActivity visible aprobadas. El recorrido completo de exportación creó una copia portátil v2 de 843.326 bytes; integridad ZIP, database.db y manifest.json comprobados. El proceso :voice estaba ausente al terminar. Captura returned.png inspeccionada: regreso a la puerta de adultos, texto con contraste correcto. No se verificó un aviso visible de éxito; no atribuir este recorrido a grabación activa, restauración, cambio de cuenta o borrado. Resultado compacto: emulator-export-28/flow/result.json. Se cerró únicamente el AVD propio58716 tras la QA. La copia de prueba y los archivos del selector se mantienen fuera del commit.

Validación del fuente final29: suite completa 6.833 pruebas, 6.831 aprobadas, cero fallos/errores y dos omisiones reales de voz; assembleDebug aprobado. APK SHA256 9ada9e8b60e59d546828b7b90223255aaab89828197dc18a0f4c00bb1b8752fd: 68.452 clases, cero SDK/integración/permisos HC; GPS, micrófono y cámara presentes. Alineación LOAD/RELRO/ZIP favorable; no es prueba de ejecución16KB. Lint30 y comprobación optimizada siguen en curso.

El alcance A–J permanece parcial: integración completa de puertas Free/Pro y del trabajo de programas, traducción/voz inglesa y validadores18+ internos, fuentes invitado inmutables de archivos/preferencias, conservación de extensiones de blobs privados, elección nativa de mantener ambas versiones, pruebas de interrupción/restauración/cambio de cuenta en dispositivos, búsqueda/performance en Android, compilación iOS, corpus real, firma/entrega por dispositivo, SDK/URLs/licencias finales y beta. Estos puntos no se consideran resueltos por la retirada de Health Connect ni por la suite JVM.

APK29 final también verificado en Android8/API26: MainActivity visible y exportación portátil aprobada (843.841 bytes, ZIPv2 y cierre del proceso :voice). Resultado emulator-api26-final-29/export-retry/result.json. Contraste comprobado visualmente en la captura returned.png. El primer intento del helper se detuvo antes de escribir porque Android8 usa el encabezado Downloads; se adaptó a ese texto observado. El emulador propio se cerró tras terminar.

Checks finales del código29: lint30 aprobado con cero errores, 610 advertencias y 39 recomendaciones; R8 de release aprobado (r8-31-result.json). El manifiesto combinado release31 no contiene HC, permisos de lectura amplia multimedia ni servicio dataSync; mantiene GPS, voz y cámara. R8 es compilación/optimización, no AAB/APK firmado ni instalación release. El replay real sigue con dos omisiones y bloquea la aceptación de CI. No hubo publicación, push, compras, gasto ni cambios al teléfono.
