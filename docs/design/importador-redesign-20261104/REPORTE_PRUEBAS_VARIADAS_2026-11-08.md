# Informe de pruebas variadas del importador guiado

**Fecha:** 8 de noviembre de 2026  
**Propósito:** entregar evidencia a un agente que prepare un plan para reforzar el reconocimiento de ejercicios, la estructura semanal y la revisión de documentos. Este informe registra hallazgos; no propone reescribir el flujo ni implementa cambios.

## Resumen ejecutivo

En el Z Flip se probaron cuatro entradas con la app existente **KPKN Dev**: texto pegado de seis días, texto de dos semanas, una imagen de cuatro días y un PDF de dos páginas/dos semanas.

- Los dos casos de texto conservaron sus sesiones y su distribución semanal.
- El PDF conservó dos semanas, seis sesiones y diez ejercicios. Sus avisos tuvieron problemas de claridad y trazabilidad.
- La imagen perdió la cabecera «SABADO»: la revisión mostró solo lunes, martes y jueves, con los dos ejercicios del sábado agrupados dentro del jueves. El botón «Corregir» no ofreció restaurar esa sesión en el flujo observado. Es el hallazgo prioritario porque puede dejar un programa estructuralmente distinto del documento.
- Los nombres de ejercicio deliberadamente omitían parte del equipo o de la variante. Quedaron pendientes de confirmación; esto por sí solo no demuestra que falten aliases. La revisión mostró por qué ampliar aliases sin medir ambigüedad sería arriesgado.

No se guardó ningún programa ni se modificó código fuente. Estas fueron pruebas físicas exploratorias, no una suite automatizada, una compilación nueva ni una evaluación de fotos reales o escritura manual.

## Entorno y límites de la evidencia

- Dispositivo: Samsung Galaxy Z Flip, depuración inalámbrica, app de pruebas ya instalada **KPKN Dev** (com.example.kpkn.dbg). No se instaló ni actualizó APK durante estas pruebas.
- Se utilizó el flujo de importación y la pantalla conjunta existente de revisión. No se creó ni se usó un editor paralelo.
- Los artefactos controlados están en [output/import-varied-20261108](../../../output/import-varied-20261108/README.md). Sus transcripciones .expected.txt describen el contenido esperado, no una medida de precisión estadística.
- La imagen es una hoja rasterizada digitalmente; no es una fotografía tomada con el teléfono. Se eligió desde el selector de archivos de la app. El helper de pruebas exige una extensión admitida y el archivo quedó compartido con bytes PNG y sufijo .txt; el importador lo reconoció como imagen por su contenido. No se probó la ruta dedicada Foto → Galería ni la cámara.
- El PDF es un documento vectorial controlado de dos páginas. La ruta actual lo renderizó y procesó con OCR; no se comparó con un PDF escaneado real ni se probó extracción nativa de texto del PDF.
- No se probaron XLSX, CSV, DOCX, URL, tablas complejas, baja iluminación, perspectiva, desenfoque o escritura manual. Tampoco se llegó al guardado en ProgramDetail.
- Al intentar cerrar el borrador y limpiar los archivos del teléfono, phone_run.py informó que el teléfono estaba en uso (pantalla encendida, actividad hace 0 minutos). Se respetó esa guarda y no se repitieron acciones; por tanto, no confirmo la limpieza de las descargas QA ni el descarte del borrador durable de esta sesión.

## Matriz de resultados

| Entrada | Esperado | Observado en la revisión | Resultado |
|---|---|---|---|
| Texto pegado, 1 semana / 6 días | Lunes a sábado, una sesión y un ejercicio por día | 1 semana, 6 sesiones y 6 ejercicios; lunes–sábado en orden | **Correcto** para estructura y días |
| Archivo de texto, 2 semanas | Semana 1: lunes/miércoles/viernes. Semana 2: martes/jueves/sábado | 2 semanas, 6 sesiones y 6 ejercicios; las distribuciones se mantuvieron separadas | **Correcto** para esta estructura |
| PNG, 4 días / 8 ejercicios | Lunes, martes, jueves y sábado; 2 ejercicios cada día | 3 sesiones: lunes 2, martes 2, jueves 4. «SABADO» aparece como fila omitida; sus 2 ejercicios quedaron dentro de jueves | **Fallo estructural P1** |
| PDF, 2 páginas / 2 semanas / 10 ejercicios | Semana 1 lunes/miércoles/viernes; semana 2 martes/jueves/sábado | 2 semanas, 6 sesiones y 10 ejercicios; ambas distribuciones aparecen correctamente | **Correcto** para estructura; avisos por mejorar |

### Evidencia física

- Seis días y calendario: [captura de distribución de seis días](../../../output/import-varied-20261108/zflip/varied_sixday-weekly.png).
- Dos semanas en texto: [captura de distribución de dos semanas](../../../output/import-varied-20261108/zflip/varied_two-week-weekly.png).
- Pérdida del sábado en imagen: [sesiones resultantes](../../../output/import-varied-20261108/zflip/varied_photo-days.png) y [aviso de fila omitida](../../../output/import-varied-20261108/zflip/varied_photo-warnings.png).
- Acción ofrecida al corregir «SABADO»: [diálogo observado](../../../output/import-varied-20261108/zflip/varied_photo-correct-sabado.png).
- PDF: [semana 2 y calendario](../../../output/import-varied-20261108/zflip/varied_pdf-week2-calendar.png), [avisos](../../../output/import-varied-20261108/zflip/varied_pdf-warnings.png) y [detalle de aviso](../../../output/import-varied-20261108/zflip/varied_pdf-warning-detail.png).

## Hallazgos priorizados

### P1 — Una cabecera de día perdida cambia la distribución de ejercicios

La hoja de referencia tiene cuatro cabeceras y dos ejercicios por día. En la app, «SABADO» se notificó como fila omitida y los dos ejercicios que le seguían se incorporaron al jueves: la sesión de jueves terminó con cuatro ejercicios y no existió sesión de sábado. La acción «Corregir» abrió una edición dirigida a los días presentes (lunes, martes y jueves); no permitió recuperar sábado en ese diálogo.

El código sí contempla sabado en WEEKDAY_LABEL y lo convierte al día 6 en ProgramImportParser.kt. Por eso, **la causa aún no está localizada**: hay que inspeccionar las palabras/cajas/confianzas OCR, la reconstrucción de filas de OcrLayout y el enlace entre filas omitidas y semanas/días. No asumir que basta con añadir el nombre del día al vocabulario.

**Riesgo:** una persona puede corregir nombres y series creyendo que solo hay tres sesiones, o ignorar el aviso y continuar con ejercicios en un día equivocado. No se guardó el programa, así que el riesgo de persistencia no se ejercitó.

**Evidencia:** varied_photo-warnings.png, varied_photo-days.png, varied_photo-correct-sabado.png; contenido fuente y esperado en rutina-foto-4-dias.png.expected.txt.

### P2 — Los avisos no identifican con precisión el tipo de documento ni las filas omitidas

En la revisión del PDF aparece «Lo leímos de una imagen», aunque el usuario eligió un PDF. OcrImportSource.readPages comparte reviewNotes() con la ruta de imágenes y añade IMAGE_NOTE también al PDF. La advertencia podría decir que se procesó mediante OCR/renderizado y nombrar la página o el tipo real de origen.

El PDF también informó «Se omitieron 2 líneas de texto». Al tocar «Ver», la acción cambió a «Ocultar», pero en pantalla no aparecieron las líneas ni su ubicación para decidir si eran datos útiles. El agregador de ruido del parser genera una nota con sourceText = null; el plan debe contemplar conservar muestras acotadas del texto omitido y su página/fila, con límites de tamaño.

**Evidencia:** varied_pdf-warnings.png y varied_pdf-warning-detail.png.

### P2 — La sugerencia de reparto puede contradecir la distribución importada

La semana 2 del PDF tiene martes/jueves/sábado seleccionados correctamente, pero la tarjeta también muestra «Sugerencia: Lun, Mié, Vie». La sugerencia no cambió la selección; es una señal visual confusa que parece ignorar la fuente. Ocultarla cuando ya existe una distribución explícita o recalcularla a partir de esa semana sería más claro.

**Evidencia:** varied_pdf-week2-calendar.png.

### Observación para investigar — Confirmación de nombres y aliases

En las cuatro importaciones varios o todos los nombres quedaron pendientes de confirmación: 6/6 en los dos casos de texto, 8/8 en la imagen y 10/10 en el PDF. Los nombres de estos fixtures omiten en varios casos la variante o el equipo y no son suficientes para concluir que el catálogo carezca de aliases.

La revisión de «Sentadilla» mostró candidata principal de sentadilla trasera con barra alta (95 %) y alternativas con Smith/sin material (75 %). El matcher ya prohíbe la resolución automática de términos genéricos como sentadilla, squat, remo, press y peso muerto, y exige unicidad de configuración. Esa cautela es deseable: no convertir «Sentadilla» en una elección automática solo para subir la cobertura. El trabajo debe medir qué nombres completos, variantes y expresiones usuales siguen sin resolverse y distinguir **alias ausente** de **variante ambigua**.

La lista curada existente está en domain/training/importer/ImportExerciseAliases.kt; la política de confianza/equipo está en ExerciseNameMatcher.kt. Antes de añadir entradas, construir una muestra oro ligada a definitionId y configurationId del catálogo actual.

## Recomendaciones para el agente que diseñe el plan

1. **Diagnosticar el fallo de estructura antes de ampliar vocabulario.** Reproducir la imagen con salida intermedia de palabras, cajas, confianza, filas reconstruidas, filas omitidas y asignación de día. Determinar exactamente en qué etapa desaparece «SABADO» y por qué sus ejercicios heredan jueves.
2. **Tratar semana, sesión y ejercicio como señales diferentes.** La confianza OCR de una palabra, la clasificación de una fila y la confianza de catálogo no deben colapsarse en una sola puntuación. Mantener texto de origen, ubicación/página y relación con las filas siguientes para que una cabecera débil no reubique silenciosamente una sesión completa.
3. **Dar una reparación que resuelva estructura.** Desde la advertencia de una fila que parece cabecera, permitir clasificarla como día, crear/renombrar la sesión en la semana correcta y asociar las filas siguientes; si no se puede resolver, dejar visible qué ejercicios están sin día y evitar guardar una distribución que contradice el documento. No limitar «Corregir» a editar una sesión ya existente.
4. **Crear un benchmark de nombres antes de agregar aliases.** Muestrear nombres reales anonimizados de importaciones y nombres del catálogo: denominación canónica, alias español/inglés, abreviaturas, tildes, plurales, errores OCR frecuentes, equipo y variantes. Etiquetar nombre genérico ambiguo como «requiere decisión», no como acierto fallido.
5. **Medir precisión y cobertura por separado.** Reportar top-1 correcto, cobertura de sugerencias, cobertura de auto-resolución, falsos positivos y conflictos de equipo/configuración. Priorizar precisión de auto-resolución; mantener en revisión los casos ambiguos. Cada alias debe apuntar a la variante/configuración que la frase realmente identifica, no solo al movimiento general.
6. **Probar la discriminación por contenido y contexto.** Incluir títulos, notas, series/repeticiones, descansos, deload/descarga, filas incompletas, cabeceras repetidas, tablas con columnas, nombres de semana, días con foco (Monday Pull) y cabeceras sin ejercicios. Comparar 1, 6 y 7 sesiones por semana, días discontinuos y ciclos de 2–4 semanas con cambios reales de días y prescripción.
7. **Hacer los avisos accionables y fieles al origen.** Diferenciar texto, imagen y PDF; identificar página/hoja/fila; mostrar las líneas descartadas relevantes y permitir su reclasificación. Separar «palabra OCR dudosa» de «ejercicio no encontrado» y de «variante no determinada».
8. **Mantener la arquitectura de flujo acordada.** La revisión conjunta debe resolver ejercicios y distribución semanal y, al guardar, navegar al ProgramDetail existente para ajustar semanas. No proponer entidades, vistas o editores paralelos para las mismas sesiones; reutilizar el catálogo y editor de sesión ya existentes. La decisión está documentada en [README.md](README.md).
9. **Organizar la validación en capas pequeñas.** Primero casos deterministas de parser/matcher/OCR-layout con resultados oro; después pruebas integradas focalizadas del importador; finalmente una pasada física corta con KPKN Dev para los errores visuales/críticos. Reservar una suite completa para cuando un cambio transversal lo justifique.

## Criterios de aceptación que debería exigir el plan

- La imagen de cuatro días produce exactamente lunes (2), martes (2), jueves (2) y sábado (2); ningún ejercicio pasa a jueves por perderse la cabecera. Si el OCR no puede leer un encabezado, la revisión conserva la fila y el usuario puede restaurar el día antes de guardar.
- El texto de seis días y las dos entradas de dos semanas conservan el número de semanas, sesiones, orden y asignación de días. Ningún ejercicio cruza de semana al corregir o sugerir un reparto.
- El PDF mantiene las dos semanas y diez ejercicios, y sus avisos no lo describen como una imagen elegida. Las líneas omitidas relevantes muestran el fragmento de origen y la página o fila.
- Los aliases se evalúan contra el catálogo y sus configuraciones actuales. Ningún caso ambiguo de la muestra oro se autoasigna a un equipo/variante incorrectos; los reportes desglosan precisión y cobertura y conservan ejemplos que requieren confirmación.
- Una advertencia de encabezado perdido puede repararse estructuralmente, no solo marcarse leída. Antes de guardar, la distribución visible coincide con las decisiones confirmadas del usuario.
- Al completar la revisión se sigue usando el ProgramDetail y el editor de sesiones actuales, sin persistir una nueva copia paralela del programa.

## Artefactos reproducibles

- Generador y corpus: [generate-fixtures.ps1](../../../output/import-varied-20261108/generate-fixtures.ps1) y [README.md](../../../output/import-varied-20261108/README.md).
- Referencias esperadas: [texto-6-dias.txt](../../../output/import-varied-20261108/texto-6-dias.txt), [texto-2-semanas.txt](../../../output/import-varied-20261108/texto-2-semanas.txt), [rutina-foto-4-dias.png.expected.txt](../../../output/import-varied-20261108/rutina-foto-4-dias.png.expected.txt) y [rutina-documento-2-semanas.pdf.expected.txt](../../../output/import-varied-20261108/rutina-documento-2-semanas.pdf.expected.txt).
- Fuentes de código para el diagnóstico: [ProgramImportParser.kt](../../../android-native/app/src/main/java/com/example/kpkn/domain/training/importer/ProgramImportParser.kt), [OcrLayout.kt](../../../android-native/app/src/main/java/com/example/kpkn/domain/training/importer/OcrLayout.kt), [OcrImportSource.kt](../../../android-native/app/src/main/java/com/example/kpkn/data/importer/ocr/OcrImportSource.kt), [ImportExerciseAliases.kt](../../../android-native/app/src/main/java/com/example/kpkn/domain/training/importer/ImportExerciseAliases.kt), [ExerciseNameMatcher.kt](../../../android-native/app/src/main/java/com/example/kpkn/domain/training/importer/ExerciseNameMatcher.kt) y [ImportWarningsPanel.kt](../../../android-native/app/src/main/java/com/example/kpkn/screens/programs/import/ImportWarningsPanel.kt).

## Encargo listo para el agente de planificación

Prepara un plan técnico priorizado a partir de este informe. Empieza por localizar la pérdida de la cabecera SABADO y el traslado de sus ejercicios al jueves; después aborda la cobertura/precisión de aliases con una muestra oro ligada a las variantes del catálogo, y la claridad y reparación de avisos OCR/PDF. Para cada fase especifica diagnóstico, archivos/módulos implicados, pruebas focalizadas, riesgos y evidencia de aceptación. Conserva la revisión conjunta existente y el salto al ProgramDetail; no diseñes pantallas, editores ni entidades paralelas. Entrega solo el plan: no implementes cambios hasta que se apruebe.

