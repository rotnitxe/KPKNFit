# Exportación CSV local · 24-oct-2026

Implementación en mfp/n por depender del contrato de recetas de P2-Na. Ajustes abre una hoja de cuatro dominios. Preparar no escribe: sólo el destino concedido por CreateDocument autoriza la escritura. Cancelar descarta el paquete preparado. No se crean copias en caché ni se cambian consultas o tablas.

RFC 4180, UTF-8 con BOM y CRLF, coma separadora y punto decimal independiente del idioma. Escapa comillas, comas, punto y coma, tabulaciones y saltos. Los textos que podrían ejecutarse como fórmula reciben apóstrofo incluso tras espacios; los números legítimos conservan su signo. Los originales permanecen intactos.

Comidas: sólo consumidas, fecha literal local y hora registrada (12:00 exclusivamente en comidas heredadas sin hora). Las marcas sin comidas tienen una fila identificada sin alimento ni hora inventados; ayuno explícito permite cero kcal, las demás marcas dejan las cantidades vacías. Entrada rápida deja micronutrientes vacíos. La procedencia ausente o estimada se identifica honestamente en lugar de atribuir catálogo.

Cuerpo convierte kg a lb según Ajustes y conserva valor SI; fecha/hora según zona explícita. Entrenos exporta ordinal de plan, carga, reps, RIR, técnicas y notas, con lado, fallo, calentamiento, tiempo y distancia adicionales. Biblioteca incluye datos principales, versión/raciones e ingredientes de recetas; produce dos CSV y por tanto un ZIP incluso siendo la única casilla seleccionada. Esta excepción mantiene ambos archivos completos.

La lectura transaccional aísla filas ilegibles por tabla/id y comunica cuántas se omitieron. No borra ni expone su JSON. El árbol debe superar la ronda agrupada Android antes del cierre; el selector y los archivos reales se revisarán en QA de la integración.

## Validación local cerrada

Ronda agrupada `C:/kw/logs/mfp-CSVE-local-android-3.log`: BUILD SUCCESSFUL; 67 clases, 462 casos declarados, 460 ejecutados, 0 fallos/errores, 2 omitidos identificados en XML. Incluye Room, ViewModels, Compose y el cargador OCR con cancelación/EXIF. 28 PNG en `C:/kw/shots/mfp/CSVE-local-20261024-final3`; revisión visual de formulario parcial/advertencias/base, exportación múltiple/error y nutrientes a 100/130: sin recortes; los campos no reconocidos siguen vacíos. Los errores de importación de estado de las dos compilaciones anteriores se corrigieron; no se contaron como pruebas ejecutadas.

JVM final `C:/kw/logs/mfp-CSVE-local-jvm-3.log`: 33 pruebas, cero fallos/errores/omitidos. Las etiquetas FDA con medida doméstica y las cantidades negativas con espacio/menos Unicode tienen regresión explícita. Galería/SAF y autocomprobación con ML Kit real todavía requieren el lote de dispositivo integrado.

Integración con Myo-reps: se conservan las quince columnas de entrenos y se añaden variante_tecnica, rest_pause_json y drop_sets_json. Las mini-series mantienen cada pausa/repetición y style=myo; no se reduce la exportación a la etiqueta REST_PAUSE ni se pierden las cargas de los drops. Regresión de lectura CSV→JSON añadida para la suite integrada.
