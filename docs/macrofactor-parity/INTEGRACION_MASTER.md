# Integración de MFP en master · 24-oct-2026

El usuario autorizó expresamente commit, integración y push de esta entrega. Sustituye su restricción anterior de no master/push. Base master edc237f66; entrega feat/macrofactor-parity61fc0701f; fusión real que conserva ambos historiales. Checkout de master: C:/kw/mfp-master.

El WIP ajeno del directorio principal queda fuera de los commits y la publicación. Se conserva sobre su base anterior en codex/wip-preservado-antes-mfp-20261024, sin checkout de archivos ni commit del WIP. El procedimiento verifica índice, estado y metadatos de archivos antes/después; recibo C:/kw/logs/mfp-master-local-integration-receipt.json.

## Resolución de compatibilidad

- Conservados vídeo, calibración/APRE, referencias TM/1RM, opciones y metadatos de master junto a las funciones MFP. Se mantiene retirado el cockpit antiguo.
- Room31 generado por KSP idéntico al esquema congelado e66961026467b8dc05ef995dc8c01938:29→30 calibración y30→31 nutrición; no32. El test de calibración ahora encadena ambas migraciones y conserva las aserciones de datos/columnas anteriores, añadiendo comprobaciones de tablas nutricionales vacías.
- Backup público de calibración conserva JSON canónico y snapshot opcional de filas opacas, sin duplicar campos ni restauraciones. El test de registro tipado utiliza su serialización pública y verifica igualdad completa al restaurarlo.
- capturedTargetReps coexiste con PlanLoadReferenceMetadata. Flags retirados adaptados a SetPurpose. Regresión nueva: AMRAP mínimo5/RIR planificado4 desde120kg prescribe106,7kg sin añadir reserva.
- Modelo MFP y versión2 de snapshots de capacidad de master compatibles; los snapshots antiguos permanecen almacenados y la capacidad vigente se recalcula cuando corresponde. Sin reescritura histórica.
- El selector conserva sugerencias, elección simple y reemplazo, con una sola barra inferior. La tarjeta conserva vídeo/calibración y añade material/discos/Myo.
- Corregida la doble presentación del vídeo en el cuerpo además del chip de cabecera: desplazaba el esfuerzo y duplicaba las acciones al abrir su menú. Se conserva chip/menú completo; no se cambiaron ni relajaron los tests de vídeo/layout.

## Validación de esta combinación

assembleBaseDebug aprobado; **292 pruebas/35 clases,0 fallos,0 errores,0 omitidas**, mfp-master-integration-4.log. Incluye migraciones completas, backup canónico/opaco, preferencias, carga/esfuerzo/AMRAP, progresión/APRE, selector, vídeo/discos, alimento propio/OCR e identidad. XML preservados en C:/kw/logs/mfp-master-integration-4-xml; recibo C:/kw/logs/mfp-master-tests-receipt.json.

Intentos anteriores preservados: primera compilación detectó cinco referencias antiguas; segunda ejecutó292 pruebas con15 fallos (cadena de migración y14 de presentación duplicada de vídeo). La tercera tanda corrigió la migración, pero quedaron14 fallos por la segunda llamada duplicada del diseño heredado. La cuarta retira esa llamada y ejecuta las mismas35 clases reutilizando su bytecode: los tests y sus firmas de API no cambiaron; main y APK sí se recompilan. Hash de fuentes de tests preservado en mfp-master-test-bytecode-reuse.json. No se presentan los intentos anteriores como verdes.

APK baseDebug ordinario: KPKN Dev/com.example.kpkn.dbg,Pre Release18/versionCode52.87 etiquetas y certificado debug estable verificados. SHA256:916fb5f15d309c5bf3713c4581b709c5f43f4dac1e81d3ff06912c0f6bfd0929. APK en app/build/outputs/apk/base/debug del checkout indicado. El APK anterior de G corresponde a la entrega anterior a esta fusión y no se sobrescribe en esta operación de Git.

Esta tanda es focal: no se afirma haber repetido toda la suite histórica ni corregido sus fallos heredados. QA física/p95 siguen pendientes. No se añadió la propuesta de OCR del nombre→parser→vinculación; se integra la entrega implementada.
