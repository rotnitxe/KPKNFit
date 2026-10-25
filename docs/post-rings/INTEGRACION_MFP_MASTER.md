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
- Android: `assembleBaseDebug` aprobado, con compilación principal normal en la pasada 7. Regresión final: **459 casos en 54 clases, cero fallos pendientes, errores u omisiones**. Las 52 clases aprobadas de la pasada completa 7 conservan sus resultados; las dos fixtures corregidas repitieron sus tres pruebas y aprobaron en la pasada 12. XML finales en `C:/kw/logs/postrings-mfp-android-final-xml`; recibo `postrings-mfp-android-final-receipt.json`.
- AVD propia `KPKN_MFP_Local_QA` / `emulator-5584`: actualización `-r` de KPKN Dev, con certificado compatible, de Room31 a Room35. Integridad SQLite, filas originales y hechos de entrenamiento conservados; permanecen un programa, ocho registros de entrenamiento, dos registros nutricionales y un alimento propio. Arranque frío y actividad `MainActivity` reanudada con su pantalla de entrada visible. Recibos y capturas: `C:/kw/logs/postrings-mfp-native35-final`.
- Sin conexión y con imágenes sintéticas: **códigos de barras 8/8 y OCR 5/5**, a través del ML Kit empaquetado y del parser/buscador reales. La búsqueda utiliza tres productos de ejemplo en una base en memoria; no acredita cobertura universal de productos ni precisión de cámara física.
- APK baseDebug: `KPKN Dev` / `com.example.kpkn.dbg`, certificado estable verificado y definiciones DEX de BuildConfig y AIDL presentes. SHA-256 `e7069e91f0c6a79976fa198bc719aebb882590c3b5d8f526d1639e1487a061d3`. Artefacto de QA en `C:/kw/logs/postrings-mfp-native-build/app/outputs/apk/base/debug/app-base-debug.apk`.

Los intentos de compilación y sus fallos se conservan en `C:/kw/logs/postrings-mfp-*`. La primera pasada conjunta ejecutó 458 pruebas: 445 aprobadas y 13 fallidas en cuatro clases. Se corrigieron los datos obligatorios de una fixture, rutas temporales largas de Windows y el modo SQLite de la prueba de reconexión; se portaron las expectativas de reducción automática de carga al contrato de POST-RINGS. No se omiten esas pruebas. Las cancelaciones por presión de memoria no se contabilizan como pruebas aprobadas.

## Aislamiento y conservación

La integración Git está en el checkout principal. Para validar su contenido se congela una copia de sus fuentes seleccionadas y se usa una salida de build independiente: otros trabajos están compilando y editando nutrición en el mismo directorio. Se contrastan las **4.708 entradas Android**, entre ellas 3.574 fuentes Kotlin/Java; la copia contiene AIDL, manifiestos, recursos, los 65 assets originales y fixtures comprometidos en Git. El plugin Android registra sus fuentes de forma normal. Esto evita incluir accidentalmente ese WIP en el APK o en la publicación. No se borran, restauran ni publican esos cambios ajenos. Los recibos y hashes están en `C:/kw/logs/postrings-mfp-primary-master-receipt.json`, `postrings-mfp-complete-inputs.json`, `postrings-mfp-head-source-equivalence-final.json` y `postrings-mfp-source-snapshot-*.json`.

La evolución de Nutrición a Room36 permanece como trabajo concurrente sin publicar. En los tests compartidos, el índice de esta entrega se construye con un parche cerrado desde HEAD hasta las fuentes validadas de Room35; las versiones de trabajo del otro agente se conservan.

El dispositivo físico y la app personal no se modifican durante esta integración. No se entrega un APK nuevo en G:. El QA físico de cámara/latencia, p95 y Xcode conserva sus gates separados; esta validación focal no representa toda la suite histórica.
