# Integración MFP + POST-RINGS en el master principal

El usuario autorizó expresamente integrar y publicar ambas entregas. El checkout principal `C:/Users/valen/Documents/KPKNFit` está en `master`, con el merge `fcb36de87c4763597ce75ff693e47890a8b53615`. Sus padres conservan el checkpoint POST-RINGS `2ba0e901f` y el master público MFP `263c9be49`. `C:/kw/mfp-master` quedó detached; no conserva la rama master.

## Combinación de producto

- MFP conserva nutrición, recetas, estados del día y check-ins, nutrientes, CSV, OCR de etiquetas, códigos de barras, material, fuerza, progresión, descansos y visualización de series.
- POST-RINGS mantiene retirados los predictores y las superficies AUGE. El mapa muscular usa geometría anatómica independiente y conteos de series; no muestra recuperación calculada ni ajuste fisiológico de volumen. Los cambios de protocolos conservan su confirmación explícita.
- Room **35** une los historiales públicos MFP31 y privados POST-RINGS31/32/33/34. Las migraciones directas conservan originales antes de retirar almacenamiento derivado y añaden las tablas nutricionales de forma idempotente.
- El esquema público 31 permanece idéntico a `e66961026467b8dc05ef995dc8c01938`. Los esquemas privados 32/33/34 siguen intactos; el 31 privado se conserva en `schemas-private/31.json`. KSP generó la identidad 35 `7656051b2803f99fdde1e93d68ea13a1`.
- Una respuesta original corrupta o en conflicto aborta la transacción; no se descarta silenciosamente. El respaldo coherente de POST-RINGS admite ahora restaurar Room35 y tiene un caso de restauración conjunta de observaciones y nutrición.

## Validación de esta combinación

- Catálogo: 228 definiciones y 552 configuraciones; comprobación canónica aprobada, sin regeneración manual.
- Scripts: 218 pruebas aprobadas. Backend: 19 pruebas aprobadas y 32 subcasos; una advertencia de deprecación.
- Dominio: **148 pruebas en 16 clases, cero fallos, errores u omisiones**; XML preservados en `C:/kw/logs/postrings-mfp-pure-final-xml`. Incluye fuerza, prescripciones, volumen, Myo, confirmación de protocolos e historial observable.
- Verificador de retirada: cero infracciones activas, con controles positivos ampliados.
- 39 fuentes Swift modificadas analizadas sintácticamente, sin errores. No equivale a una compilación Xcode ni a XCTest.
- Android: compilación y regresión conjunta en curso. Esta línea se sustituye al cerrar los resultados.

Los intentos de compilación y sus fallos se conservan en `C:/kw/logs/postrings-mfp-*`. La primera pasada conjunta ejecutó 458 pruebas: 445 aprobadas y 13 fallidas en cuatro clases. Se corrigieron los datos obligatorios de una fixture, rutas temporales largas de Windows y el modo SQLite de la prueba de reconexión; se portaron las expectativas de reducción automática de carga al contrato de POST-RINGS. No se omiten esas pruebas. Las cancelaciones por presión de memoria no se contabilizan como pruebas aprobadas.

## Aislamiento y conservación

La integración Git está en el checkout principal. Para validar su contenido se congela una copia de sus fuentes seleccionadas y se usa una salida de build independiente: otros trabajos están compilando y editando nutrición en el mismo directorio. La copia contiene también los AIDL, manifiestos, recursos, assets y fixtures comprometidos en Git; el plugin Android registra sus fuentes de forma normal. Esto evita incluir accidentalmente ese WIP en el APK o en la publicación. No se borran, restauran ni publican esos cambios ajenos. Los recibos y hashes están en `C:/kw/logs/postrings-mfp-primary-master-receipt.json`, `postrings-mfp-complete-inputs.json` y `postrings-mfp-source-snapshot-*.json`.

La evolución de Nutrición a Room36 permanece como trabajo concurrente sin publicar. En los tests compartidos, el índice de esta entrega se construye con un parche cerrado desde HEAD hasta las fuentes validadas de Room35; las versiones de trabajo del otro agente se conservan.

El dispositivo físico y la app personal no se modifican durante esta integración. No se entrega un APK nuevo en G:. El QA físico de cámara/latencia, p95 y Xcode conserva sus gates separados; esta validación focal no representa toda la suite histórica.
