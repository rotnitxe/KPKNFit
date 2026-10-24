# Entrega local de la ola MFP · 24-oct-2026

Implementación integrada y APK baseDebug entregado. Trabajo directo y local, sin push ni fusión a master. La aceptación física completa continúa pendiente porque el Z Flip no está conectado; no se afirma un 100 % de QA física.

## Hecho

- Nutrición: estimador y tendencia robusta, estados del día/check-in durable, escáner local, alimentos propios, reutilización del diario, recetas/rendimiento/favoritos/hora, nutrientes con cobertura y tabla DRI fija, exportación CSV y OCR experimental de galería con revisión y guardado explícito.
- Entrenamiento: material personal, contabilidad de volumen, modelo de fuerza con esfuerzo, propuesta de peso y repeticiones persistida, progreso/récords, sustituciones, preset Myo como rest-pause, discos por lado, verificación de calentamiento/descarga y ajustes visibles.
- Pulidos: Meta76 al130%, hábitos que cambian de día con la app abierta, clave del planificador EER y aviso/prioridad de códigos duplicados. Cero explícito de fibra/azúcar conserva su presencia al guardar/reabrir y escalar, sin confundirlo con un dato ausente ni cambiar Room.
- Room congelado: calibración29→30, nutrición30→31; esquema31 generado por KSP, identidad e66961026467b8dc05ef995dc8c01938. No versión32. Copias antiguas y decisiones opacas de calibración conservadas.
- Identidad ordinaria baseDebug estable: **KPKN Dev**, `com.example.kpkn.dbg`, firma debug; comprobadas87 etiquetas regionales. No hace falta init para obtener la identidad correcta.

## Verificado y límites de la evidencia

| Comprobación | Resultado |
|---|---|
| JVM integrada | 3.067 pruebas /275 clases, cero fallos |
| Entrenamiento Android focal | 129/129,14 clases,40 PNG |
| N3/N4 Android focal | 257/257,22 clases,38 PNG |
| Recetas/backup/preferences | 44 JVM y73 Android verdes,18 PNG |
| Nutrientes/DRI | 1.317 JVM,12 focales posteriores y330 Android verdes; tabla240 filas intacta |
| CSV/OCR/nutrición | 460 Android ejecutadas,0 fallos,2 omisiones condicionales,28 PNG |
| Último focal identidad/parser/selector | 27/27,3 clases,0 omisiones |
| Último focal alimento propio/ceros/recetas/cobertura/CSV/backup | **88/88,12 clases,0 omisiones**,9m7s; código df1d527764aa9d4a75afa6c9c4571ffd62ca397f |
| Parser:109/39 no se adivinan como10/3 | 18/18 JVM verdes |
| Suite completa con renders | **13.025:12.936 aprobadas,79 fallos,10 omitidas**,1.520 clases,1.307 PNG |
| Control intacto de la misma base | 117:104 aprobadas y13 mismos fallos de render |
| ML Kit nativo en APK final | Barcode8/8; OCR5/5 con cuatro macros/base y ración comprobados; imágenes sintéticas |

Los79 fallos iniciales se clasificaron nominalmente:42 de los51 heredados,13 renders reproducidos en la base intacta,19 de contaminación (las33 pruebas de ambos editores pasan aisladas y en control),5 contratos actualizados y verificados. Se corrigió además la selección asíncrona del nodo (7/7). Los21 sospechosos históricos del check-in pasan en la suite completa. **La suite completa no se presenta como verde** ni se suman tandas distintas como si fueran una sola. Detalle y rutas de XML/logs: [VALIDACION_INTEGRADA_LOCAL.md](cambios/VALIDACION_INTEGRADA_LOCAL.md).

QA nativa en una AVD propia API36 (`KPKN_MFP_Local_QA`,5584), siempre con emu.lock: actualizaciones-r tras verificar certificado, APK instalado y Room31, con snapshots incluyendo WAL/SHM; conserva1 programa y8 entrenos. MainActivity real abre. Ajustes de descanso180s/temporizador apagado/descarga AUGE activada confirmados persistidos. Exportación SAF real: ZIP5CSV con BOM y filas/columnas coherentes. Declinar/posponer comprobados en arnés en memoria.60 aperturas/capturas100/130% son evidencia de apertura, no aceptación funcional completa de cada flujo.

Flujo real de galería: denegar cámara→escribir código inexistente→crear alimento→seleccionar etiqueta→revisar→indicar100g→Guardar. Antes del guardado:0 alimentos propios; después:1 y las mismas2 comidas. La búsqueda posterior por2999999900010 recupera QA_ETIQUETA_MFP con200kcal/P10/C20/G8, fibra2g y sodio200mg. No hubo registro automático. No se afirma una receta registrada por el flujo nativo: el cálculo se cubre en pruebas focales.

## No verificado en físico

Z Flip desconectado en las comprobaciones protegidas. Pendientes aceptación física completa, lente/linterna/vibración, fotografías reales variadas y **p95 caliente de búsqueda**. El banco está implementado:200 consultas,20 calentamiento descartadas,top20,1–3 pasadas y percentiles de rango más cercano. Ver [P2-F_RESULTADO.md](P2-F_RESULTADO.md). No se sustituye el p95 físico por cifras del emulador. No hubo acceso ni instalación en el teléfono, ni cambios en la app personal. Este APK no es una prueba R8/release.

## Decisiones y hallazgos

- No push, no master. S11/H-i, FTS y cambios de catálogo/importador siguen diferidos; sin nueva constante del informe ni multiplicadores de otra app. AUGE mantiene sus decisiones previas. Sin borrado de código huérfano.
- **Snapshot WAL corregido**: el cursor diferido del checkpoint antes no se consumía; ahora se ejecuta y se exige busy0 antes de copiar. Test con la base abierta.
- Primera línea corrupta del .gitignore raíz: detectada y conservada por la restricción explícita.
- Clave del planificador: corregido el acceso al resultado eerKcal.
-51 rojos heredados documentados;13 renders adicionales demostrados en control con las mismas condiciones. El verificador de constantes tiene un falso positivo en la lista sintética `80.0,85.0`, no en un coeficiente.
- T3 cambia cifras visibles de e1RM/PR al incorporar RIR y unificar el modelo; no reescribe registros históricos. Ejemplo100×5 sinRIR112,5kg, conRIR2=120kg. La mini gráfica de Inicio conserva pesos diarios; la cifra principal usa tendencia robusta. Decisión documentada, no una mezcla accidental.

## APK

Código compilado: `df1d527764aa9d4a75afa6c9c4571ffd62ca397f`. SHA256: `f4c53b4cc95f49b179529bacf4316903c325466ab3afe06c72ee5e71f35f3a30`. CertificadoSHA256: `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`. Paquete `com.example.kpkn.dbg`, Room31, versión50/KPKN Pre Release17. APK debug de pruebas; verificar firma y datos antes de cualquier actualización física. No instalar encima de un esquema superior ni borrar datos para hacerlo compatible.

Destino: `G:/Mi unidad/KPKN_MFP_local_2026-10-24/`, APK+LEEME+informe+recibos+evidencia seleccionada. No incluye bases de datos privadas. Logs completos y XML conservados en `C:/kw/logs`; renders en `C:/kw/shots/mfp`.
