# Validación integrada local · 24-oct-2026

La suite completa **no está verde**: hay fallos heredados y contaminación que se distinguen abajo. Tras corregir los cinco contratos de la entrega y el selector asíncrono, no queda un fallo real nuevo sin resolver en lo ejecutado. No se alteró ROJOS_BASE.txt.

| Ejecución | Resultado real |
|---|---|
| Suite completa con renders, `mfp-integration-android-3.log` | 13.025 pruebas, 12.936 aprobadas, 79 fallos, 10 omitidas; 1.520 clases; 34 min 27 s |
| Focal 4, `mfp-integration-focal-4.log` | 190 pruebas, 176 aprobadas, 14 fallos; 16 clases; 10 min 8 s |
| Control de base 8ac57c9d4, `mfp-baseline-render-control-1.log` | 117 pruebas, 104 aprobadas, los mismos 13 fallos de render; 8 clases; 9 min 59 s |
| Focal 5, `mfp-integration-focal-5.log` | 7/7 del selector, cero fallos; 9 min 29 s |

Clasificación nominal de los 79 fallos iniciales:

- 42 ya figuran en la línea base de 51. Los otros nueve de la línea base no fallaron aquí; eso por sí solo no prueba una corrección.
- 13 renders adicionales se reproducen, por nombre y motivo, en una copia intacta de la **misma base 8ac57c9d4**, con renders activados. No se modificaron sus expectativas.
- 19 de AutoSave/ManualOverride corresponden a contaminación de la corrida completa: las 33 pruebas de ambas clases pasan tanto en la tanda focal como en el control de base, sin cambiar el código ni las aserciones de esas clases.
- 5 contratos actualizados y verificados: higiene FTS espera Room31; comparación de vídeo mantiene todas las tablas de v28 y comprueba calibración vacía; una aproximación al100% no se ofrece como trabajo; la lista del catálogo se comprueba fuera de Sugeridos; el preset Myo solicitado ya se ofrece. Sus aserciones vigentes pasan. La apertura de la ficha también excluye Sugeridos al elegir su nodo: 7/7 en focal5.

Los **21 casos históricos sospechosos del check-in pasan todos** en la suite completa. Se produjeron 1.307 PNG nuevos. Se revisaron las funciones MFP al100/130%, incluida la separación de «Meta76kg» respecto a la línea. El ajuste de texto de progreso usa coma decimal y expresa que e1RM es una estimación, con la misma fórmula.

Evidencia fuera del repositorio: `C:/kw/logs/mfp-final-failure-classification.json` contiene cada caso, su categoría y prueba; los XML originales están preservados en `mfp-integration-full-3-xml`, `mfp-integration-focal-4-xml`, `mfp-integration-focal-5-xml` y `mfp-baseline-render-control-1-xml`. Las hojas están en `C:/kw/shots/mfp/integration-local-20261024-3` y las tandas focales. No mezclar estos resultados ni presentarlos como una segunda suite completa verde.

Room31 fue generado por KSP y registrado en286ad8a45: identidad e66961026467b8dc05ef995dc8c01938. Conserva calibración30, añade nutrición31 y no consume32.

QA final: baseDebug ordinario sin init;27/27 identidad/parser/selector y88/88 alimento propio/ceros/recetas/CSV/backup. Barcode8/8 y OCR5/5 con ML Kit en APK final, imágenes sintéticas. Actualización-r guardada con certificado y Room31 compatibles; MainActivity real abre. Recibos mfp-zero-declarations-8-receipt.json, mfp-native-update8-receipt.json y mfp-final8-native-selftests.json en C:/kw/logs. Ver INFORME_FINAL_LOCAL.md para separar los flujos reales de los arneses en memoria. La medición p95 requiere el ZFlip físico y no se reemplaza por un número de emulador.

Cierre: la fuente canónica es C:/kw/mfp. Los worktrees temporales se retiraron tras preservar commits y resultados. Las hojas/rutas de C:/kw/shots/mfp citadas arriba están ahora archivadas con la misma estructura en C:/kw/logs/mfp-evidencia-visual-20261024.zip (2.550 archivos, CRC/hash verificados). Los XML originales permanecen en las carpetas de C:/kw/logs indicadas.
