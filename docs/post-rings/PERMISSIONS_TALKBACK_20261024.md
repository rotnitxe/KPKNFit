# POST RINGS — permisos y seguimiento físico, 24-oct-2026

Seguimiento del [QA Android ampliado](ANDROID_QA_20261024.md). Trabajo local, sin commit ni push; la app personal no se modifica.

**Resultado final:** solicitud de permisos durante/después de rotación y lectura inicial completa del detalle con TalkBack aprobadas en el Z Flip. El usuario confirmó físicamente lectura y Cerrar. Accesibilidad, permiso de notificaciones y orientación restaurados exactamente; datos originales conservados. La cancelación de la sesión en curso durante el último recorrido fue confirmada por el usuario como intencional y se respetó.

## Corrección y pruebas

La solicitud inicial de permisos usaba estado Compose no guardable. `InitialPermissionRequestEffect` conserva ahora la marca mediante `rememberSaveable` antes de lanzar la solicitud. MainActivity guarda además su propia marca en el Bundle para proteger la recreación mientras la ventana de permisos está abierta. La recreación y el cambio de rutas no inician otra solicitud en el mismo flujo. No cambia Room ni la política de permisos concedidos por Android.

`compileBaseDebugUnitTestKotlin assembleBaseDebug`: exit 0, 14 min 6 s. Ejecución focalizada posterior: exit 0, 14 min 41 s, **21 pruebas en cinco clases**, XML nuevos comprobados, cero fallos, errores u omisiones:

- `InitialPermissionRequestEffectTest`: 2, restauración con solicitud pendiente/denegada y salida del onboarding.
- `RecommendedPermissionsDialogTest`: 4.
- `TrainingFactChartTest`: 3.
- `TrainingInsightsEngineTest`: 3.
- `TrainingHistoryProjectionTest`: 9.

APK `baseDebug`, paquete `com.example.kpkn.dbg`, etiqueta KPKN Dev, 489.482.000 bytes, SHA-256 `9fd5acac6c3710bef7b3c3ec15d00bf4468294495030f1062fb96e5e31015bf5`. Certificado SHA-256 `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.

## Emulador sintético

En `KPKN_PostRings_QA`, tras reiniciar los flags de notificaciones únicamente de su instalación sintética, se denegó una vez, se eligió «Ahora no» y se giró a horizontal y de vuelta. No reapareció la solicitud ni la recomendación. Android conservó `USER_SET` sin `USER_FIXED`. El recorrido previo que giró mientras el permiso seguía abierto registró dos denegaciones; ese caso no se considera aprobado por estas pruebas. Los intentos de entrada inyectada con TalkBack tampoco acreditan navegación del lector.

El AVD propio se cerró limpiamente al trasladar el QA al teléfono. No se alteró el AVD original del usuario.

## Z Flip: respaldo, actualización y datos

El usuario solicitó trasladar el QA al Z Flip y confirmó que estaba conectado y listo. Se usó la sesión supervisada de `phone_run`, con `phone.lock`, guardas de plegado/foco y sin contactar otros chats. La KPKN Dev anterior era R8, no depurable; `run-as` no permitía respaldar su base. Se exportó una **copia completa .db desde Ajustes** antes de actualizar, sin borrar datos, y se conservó el APK anterior.

Respaldo: Room 30, identidad `2585e03ca4e41ef52546f83ddd1bfdcd` coincidente con `30.json`, integridad SQLite `ok`, 4.120.576 bytes, SHA-256 `2b00cf7c11a3d1d594d77e076d34f73675728f197754837dbd0bf49087c261d3`. Se verificó firma compatible y la ruta 30→34 previamente validada. La instalación `-r` terminó con Success; MainActivity abrió con Room 34, identidad `5af27d74f402ec169ea8037559264738`, integridad `ok`.

Comparación de la copia anterior y la primera base posterior a la migración:

- Idénticos: programa, programa activo, seis medios, seis análisis de vídeo, plan/estado de nutrición, dos observaciones corporales, objetivo corporal y recibo del setup.
- Se mantienen el registro de entrenamiento y la sesión en curso. Las diferencias de JSON se limitan a los campos derivados retirados: `resolvedProfileSnapshotJson`, `energySummary`, `muscularImpactV2`, `ringStartSnapshot`, `sessionStressScore` y los cuatro `readiness*Override(s)`. Los restantes campos originales son iguales.
- Home conserva programa y sesión en curso y muestra el historial nuevo de POST RINGS. No se crean fixtures sobre los datos del teléfono.

La verificación del APK final, antes del último recorrido manual, conserva todos los valores de esa base Room34. La reserialización posterior materializa únicamente valores vacíos/null de los modelos y `captureMode=IN_APP`; no cambia los valores previamente registrados. Después del recorrido final se mantienen los demás registros y medios, con integridad `ok`; `ongoing_workout` queda vacío porque el usuario confirmó expresamente haber cancelado esa sesión a propósito. No se recuperó sobre su decisión.

## TalkBack físico

El usuario autorizó explícitamente activarlo y pidió restaurarlo al terminar. Se guardaron los ajustes previos de accesibilidad, se habilitó el servicio Samsung y se mantuvo AnyDesk. Servicio enlazado y exploración táctil activa comprobados. Los gestos ADB desplazan contenido sin demostrar avance de foco, por lo que no se contabilizan como navegación del lector.

El primer recorrido físico permitió navegar y cerrar, pero el usuario detectó que la información solo se leía al tocar cada párrafo. Se añadió agrupación semántica y `LiveRegionMode.Polite` al contenido desplazable, con Cerrar separado, y una regresión Compose sobre el bloque completo. En el APK final el usuario repitió el recorrido y confirmó: «Sí, ahora lo lee completo y puedo cerrar». Se acredita este recorrido físico del punto y el detalle, sin atribuir a los gestos ADB una activación accesible que no demostraron.

La primera sesión de lector terminó con restauración verificada: `enabled_accessibility_services` vuelve a AnyDesk, `accessibility_enabled=1` y `touch_exploration_enabled=0`. Se conservan `ACCESSIBILITY_BEFORE.json` y `ACCESSIBILITY_RESTORED.json`. El teléfono se dejó en Inicio durante la nueva compilación.

Las dos observaciones históricas de bienestar de Room30 también se compararon contra sus payloads originales en `training_observations`: ambas idénticas. La base Room34 contiene cuatro observaciones conservadas.

## Binario final y rotación física

La segunda ejecución `testBaseDebugUnitTest assembleBaseDebug` terminó con exit0 en **24 min 58 s**. Seis pruebas en dos clases, XML nuevos y cero fallos, errores u omisiones: cuatro de `TrainingFactChartTest` y dos de `InitialPermissionRequestEffectTest`. No se suman como seis casos distintos a los 21 anteriores: incluyen regresiones repetidas y una nueva prueba del bloque accesible.

APK final SHA-256 `655f2eb4111f12266c03e9f45ab8c48169660d4cbbce230f8e5d63d808d60cc7`, 489.482.000 bytes, misma identidad KPKN Dev, firma y Room34 verificadas. Actualización en Z Flip mediante `phone_run`, `-r`, Success y arranque de MainActivity aprobados. El APK `9fd5acac…` corresponde al checkpoint físico anterior a la corrección de lectura agrupada.

**Rotación física aprobada en el APK final:** se respaldaron concesión/flags de notificaciones y orientación; se revocó temporalmente el permiso solo de KPKN Dev, se abrió la solicitud, se giró con ella pendiente y se denegó una sola vez. Al volver a vertical apareció únicamente la recomendación propia. Tras «Ahora no», giros a horizontal y vertical no mostraron otra solicitud ni recomendación. Los flags finales conservaron `USER_SET` sin `USER_FIXED`. Se restauraron exactamente permiso concedido, flags y orientación previos, con aserción de igualdad en `PERMISSIONS_RESTORED.json`.

El lector se activó de nuevo para la repetición física y se restauró al recibir la confirmación del usuario. `ACCESSIBILITY_BEFORE.json` y `ACCESSIBILITY_RESTORED.json` coinciden exactamente: solo AnyDesk habilitado, `accessibility_enabled=1`, exploración táctil apagada. El teléfono quedó en Inicio. El proceso KPKN Dev sigue vivo; la lectura acotada de 1.500 líneas de su PID no encuentra `FATAL EXCEPTION` ni `ANR in`.

## Evidencia privada final

`C:\Users\valen\.codex\visualizations\2026\10\24\01a16ee1-907f-7643-b0a2-45f779d8f023\post-rings\qa-permissions-talkback`:

- `build.log`, `android-tests.log`, `ANDROID_RESULTS.json`, `android-xml/`, `CORRECTED_APK.json`.
- `clean-denied-*` y `clean-denied-flags.txt`, capturas e intentos de TalkBack del emulador.
- `phone/pre-update-export.db`, APK anterior, `BACKUP.json`, `post-update/`, `PRESERVATION.json`, `PRESERVATION_DIFF_PATHS.json` y capturas del teléfono.
- `phone/ACCESSIBILITY_BEFORE.json`, `ACCESSIBILITY_RESTORED.json`, `PERMISSIONS_BEFORE.json`, `PERMISSIONS_RESTORED.json`, `FINAL_HEALTH.json` y `FINAL_APP_LOG.txt`.
- `final/build-and-tests.log`, `APK_VERIFICATION.json`, `ANDROID_RESULTS.json`, `android-xml/`, `SOURCE_MANIFEST.json` y `PHYSICAL_QA.json`; `phone/FINAL_ORIGINAL_VALUES.json`, `after-qa/` y `PRESERVATION-after-qa.json`.

Los respaldos y capturas permanecen fuera del repositorio. No constituyen evidencia de QA de sensores, todos los flujos multideporte, iOS ni publicación.
