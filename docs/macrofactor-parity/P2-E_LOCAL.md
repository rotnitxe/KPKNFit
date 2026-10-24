# Etiqueta nutricional de galería · 24-oct-2026

Experimental y local. PickVisualMedia sin permisos nuevos. Reutiliza OcrImportSource (EXIF y lado máximo 2.000 px), MlKitTextRecognizer y OcrLayout sin modificarlos. No escribe ni copia imágenes en caché. Espera la tarea nativa antes de reciclar el bitmap, incluso al cancelar; la generación del ViewModel impide publicar una lectura cancelada.

El parser admite español UE/LatAm e inglés FDA, columnas 100 g/100 ml/porción, kJ a kcal y sal a sodio, ruido limitado de cifras/unidades, ceros, trazas y cantidades acotadas. Los campos no leídos permanecen vacíos. Las conversiones usan sólo porciones explícitas compatibles y nunca inventan densidad. Una base desconocida exige confirmación antes del guardado. 100 ml se representa como porción de referencia de 100 ml sin alterar el contrato PER_100G existente.

Nombre/marca/código se conservan; leer no tiene acceso al repositorio ni método de guardado. Los campos llevan icono y texto leído/calculado/dudoso. Deshacer restaura el formulario previo. Cambiar base después de editar escala las cifras corregidas actuales, sin reemplazarlas por la lectura anterior. Guardar mantiene la validación y persistencia de N3. Saturadas es opcional; sodio cero explícito se conserva distinto de vacío.

Primer lote JVM: 19 pruebas, un fallo real en Serving size 1 cup (240 ml). Corregido el reconocimiento de la medida entre paréntesis. Repetición: 19/19 verdes. Android, renders y autocomprobación ML Kit de cinco etiquetas están pendientes de la ronda/QA. No se afirma validación de fotos reales con brillo, ángulo o curvatura.

## Validación local cerrada

Ronda agrupada `C:/kw/logs/mfp-CSVE-local-android-3.log`: BUILD SUCCESSFUL; 67 clases, 462 casos declarados, 460 ejecutados, 0 fallos/errores, 2 omitidos identificados en XML. Incluye Room, ViewModels, Compose y el cargador OCR con cancelación/EXIF. 28 PNG en `C:/kw/shots/mfp/CSVE-local-20261024-final3`; revisión visual de formulario parcial/advertencias/base, exportación múltiple/error y nutrientes a 100/130: sin recortes; los campos no reconocidos siguen vacíos. Los errores de importación de estado de las dos compilaciones anteriores se corrigieron; no se contaron como pruebas ejecutadas.

JVM final `C:/kw/logs/mfp-CSVE-local-jvm-3.log`: 33 pruebas, cero fallos/errores/omitidos. Las etiquetas FDA con medida doméstica y las cantidades negativas con espacio/menos Unicode tienen regresión explícita. Galería/SAF y autocomprobación con ML Kit real todavía requieren el lote de dispositivo integrado.
