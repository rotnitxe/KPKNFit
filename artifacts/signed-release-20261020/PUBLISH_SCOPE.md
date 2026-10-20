# Alcance del commit y push

## Código del analizador

El analizador y sus correcciones están integrados en `master`: merge `2fc6f38a5`, corrección temporal final `f2a1c9e4e`, ajustes de integración `06510ac22` y `0ba94859a`. La revisión anterior al commit no encontró modificaciones pendientes en sus rutas de producción ni pruebas.

El remoto configurado es `https://github.com/rotnitxe/KPKNFit`, rama `master`. Al preparar esta publicación estaba en `381123d0a`; el master local integrado tenía 1242 commits adicionales y cero commits remotos divergentes. Su publicación normal transmite también esa ascendencia ya integrada. Se inspeccionaron metadatos de los objetos pendientes: 7746 blobs, 280963730 bytes sin comprimir, sin candidatos por ruta a archivos de credenciales ni blobs mayores de 100 MiB.

La revisión automática rechazó el intento de push completo a `origin/master`: exige autorización humana explícita para publicar los 1242 commits históricos pendientes además de esta entrega. Se solicitó esa autorización. Como alternativa de alcance menor, se prepara una rama `codex/signed-release-publish-20261020`, basada directamente en el master remoto, con un único commit de esta entrega y su parche. Esa rama no integra el código del analizador ni los otros commits históricos pendientes; permite publicar las evidencias propias sin actualizar la rama por defecto. El estado del analizador local y el de esa publicación acotada son distintos.

## Correcciones sobre trabajo concurrente

Las nueve rutas de `task-source-changes.json` pertenecen a archivos nuevos del módulo multideporte de otro agente, todavía sin versionar. Incorporarlas como archivos completos requeriría incorporar también el módulo y sus cambios de modelos, repositorio y calendario. El usuario pidió limitar este commit al trabajo de esta tarea.

Por eso se guarda `scoped-source-fixes.patch`: contiene exclusivamente nuestras correcciones, ya aplicadas a las fuentes del APK entregado. `scoped-source-fixes.json` identifica los hashes originales y finales de las nueve rutas. El script `capture_scoped_release_fixes.py` reconstruye los originales sin modificar las fuentes y exige que coincidan, byte por byte, con la captura inicial. Se comprobó también `git apply --reverse --check --ignore-space-change` sobre las fuentes actuales.

La integración como archivos de producción versionados de esas nueve correcciones queda vinculada a la integración del módulo multideporte. El parche permite incorporarlas sin perderlas ni publicar el resto de ese trabajo por accidente. Los archivos locales conservan las correcciones utilizadas por el APK.

## Evidencia y entrega

Este commit guarda herramientas, identificación de fuentes, informe de entrega, verificación de firma, recibo de copias, log final y los 36 XML de las 337 pruebas aprobadas. Los APK están entregados en escritorio y `G:\Mi unidad`, con hashes verificados. Las salidas de compilación, APK, claves privadas y datos del teléfono quedan fuera del conjunto versionado mediante la lista explícita de `.gitignore`.

El APK incluye cambios locales preexistentes ajenos a esta tarea, como indica `ENTREGA.md`. Sus hashes están en la captura de fuentes; esos cambios no se incorporan como código mediante este commit. La reproducción exacta del binario requiere disponer también de esos mismos inputs locales.

## Estado de QA del analizador

La evidencia focal debug de disponibilidad temporal tiene estado `PASS_FOCAL_UI_TEMPORAL_AVAILABILITY`; se conserva su proof en `qa/`. El proof R8-05 de QA acredita build y firma. La ruta histórica `candidate-launch-engine6-r8-05-01/installed-proof.json` sigue ausente al preparar esta publicación: el cierre físico de ese candidato no queda acreditado por este commit. Las métricas generales de precisión y el corpus completo mantienen sus gates independientes descritos en el relevo.

El release de usuario entregado el 20 de octubre tiene su propia evidencia de build/firma y 337 pruebas, y registra instalación de dispositivo no realizada. Esos estados no se intercambian con los del paquete QA.
