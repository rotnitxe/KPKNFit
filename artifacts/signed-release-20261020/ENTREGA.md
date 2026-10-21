# Release firmado — 20 de octubre de 2026

## Entrega comprobada

- Escritorio: `C:\Users\valen\Desktop\KPKNFit-release-FIRMADO-20261020-c0ca7d72.apk`
- Unidad solicitada: `G:\Mi unidad\KPKNFit-release-FIRMADO-20261020-c0ca7d72.apk`
- Ambas copias: 264852804 bytes y SHA-256 `3dfb1087b42460d2a616c614f4fe23958ecb8b44b4671cfa5f415104be22d3fb`.
- Cada copia tiene un archivo `.verification.json` con las comprobaciones públicas.
- El APK anterior sin firma se archivó en `previous-unsigned-0ba94859.apk`, después de comprobar su identidad exacta.

## Fuentes actuales

Se construyó desde el checkout principal `C:\Users\valen\Documents\KPKNFit`, rama `master`, HEAD `0ba94859a7eabaf43c4d9e5465a93567e16766ba`, incluyendo sus cambios locales actuales y archivos nuevos de Android. La entrega anterior desde un worktree limpio omitía esos cambios locales y carecía de la configuración privada de firma existente en el checkout principal.

Las salidas de esta construcción son nuevas y están dentro de `build/` de esta carpeta de evidencias. Se deshabilitaron las cachés de construcción y configuración de Gradle. La huella de 4299 archivos de fuentes, recursos, schemas y configuración pública se conservó antes de las pruebas, después del build y después de copiar el APK:

`c0ca7d727e87db1f1e545304b5db3a538bffc26364469b4618d610c978fe0477`

El APK firmado contiene `assets/release-build.json` con esa identificación. La captura final es `source-before-04.json`; las comprobaciones están en `source-after.json` y `source-at-delivery.json`.

## Correcciones necesarias para construir las fuentes recientes

- Se corrigió una importación de serialización de Endurance y se completó la conexión de planificación de sesiones restantes con el planificador existente: conserva sesiones protegidas, respeta el tiempo disponible y mantiene desconocidos los datos históricos incompletos.
- Se añadieron tres regresiones del adaptador y se corrigieron fixtures de tipos JSON, nulabilidad, objetivos declarados y rechazo real de escritura en Room.
- Las nueve rutas concretas modificadas durante esta entrega están registradas en `task-source-changes.json`: tres de producción y seis de pruebas. Las demás fuentes conservaron sus hashes iniciales.

## Comprobaciones

- Gradle: `BUILD SUCCESSFUL`, salida 0, 16m22s para la ejecución final de pruebas y `assembleBaseRelease`.
- Pruebas seleccionadas: 36 suites, 337 pruebas, 0 fallos, 0 errores y 0 omitidas. Los intentos anteriores se conservan en sus logs y en `test-results-03-before-fixture-fixes/`.
- Variante `baseRelease`, paquete `com.example.kpkn`, versión 50 / `KPKN Pre Release 17`, ARM64 y ARMv7.
- Firma verificada con `apksigner`; certificado público SHA-256 `9e8bb57415e409a1f107e70c62b85aa65ce73576e6196470808231f256464bc1`, coincidente con el release histórico firmado comprobado.
- Alineación ZIP de 16 KiB verificada; ZIP íntegro; `debuggable=false`; sin actividades de arneses de depuración en el manifiesto ni clases de `com.example.kpkn.debug` en el mapping de R8.
- Este informe acredita la construcción, firma, contenido y pruebas unitarias de este APK. La instalación en dispositivo está registrada como no realizada en `release-verification.json`.

Evidencias principales: `final-tests-and-release.log`, `release-verification.json`, `test-summary-final.json`, `delivery-receipt.json` y las capturas de fuentes. Se mantuvieron los cambios pendientes de otros agentes y el staging vacío.
