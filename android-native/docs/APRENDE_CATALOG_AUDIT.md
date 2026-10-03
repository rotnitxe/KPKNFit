# Auditoría de información de Aprende

Fecha de corte: 2026-10-01  
Fuente única: `app/src/main/assets/exercise_catalog_v2.json`  
Runtime: `ExerciseCatalogV2Loader` → adaptador de lectura compartido (sin clonar lógica del editor)

## Identidad y cobertura

- Revisión del catálogo: `v2-approved-2026-09-29-a`.
- Revisión de ontología declarada por la fuente: `wikilab-v3-2026-08-08`.
- 96 familias, 201 definiciones y 523 configuraciones aprobadas.
- La portada/listado visible mantiene las 201 definiciones; los enlaces de detalle aceptan además los 523 IDs de configuración.
- Una definición abre su configuración por defecto; un ID de configuración abre esa variante exacta y conserva sus opciones seleccionadas.
- Las 523 configuraciones tienen `richMetadata`, descripción, señales de preparación y ejecución, músculos por rol y participación articular.
- El asset ya no trae los campos editoriales retirados (beneficios, técnica, racional de variante, errores comunes, notas musculares y articulares, y los bloques `editorial`, `coaching` y `safety`): `ExerciseCatalogRetiredKnowledgeTest` lo fija y la lista única vive en `scripts/catalog_v2_retired_fields.py`. Los datos guardados en el teléfono antes de ese cambio (historial e instantáneas de metadatos) se siguen leyendo porque sus decodificadores ignoran claves desconocidas (`ExerciseCatalogV2LegacyPersistedDataTest`).
- 0 descripciones por debajo del umbral editorial (80 caracteres).
- 0 duplicados exactos en descripción.
- 0 desincronizaciones entre los campos planos y el sobre `richMetadata` de identidad,
  anatomía, biomecánica, sustitución, display y métricas.
- 21 IDs musculares, 64 patrones de movimiento y 14 articulaciones aparecen en el runtime actual.
- La ontología conserva las 13 entidades de patrón del atlas; 12 tienen configuraciones v2 enlazadas hoy y `jump` queda explícitamente sin ejercicios aprobados, no como enlace roto.
- SHA-256 observado del asset actual: `d1fde47d67611e87223ec6f48951e8310fbb18d215852c555f991175f39b2847`.

## Decisiones de integración

- Los ejercicios de Aprende se materializan desde la misma selección v2 que usa el editor; no se copian funciones ni se reconstruyen variantes desde nombres.
- Las articulaciones usan sus IDs compartidos directamente.
- Los 21 músculos y 64 patrones tienen decisiones explícitas en `AprendeOntology`; no hay fallback por coincidencia de nombre.
- Las 66 referencias legacy con forma de ID de los assets anatómicos tienen una decisión explícita: 61 apuntan a un ID v2 existente y 5 quedan marcadas como retiradas por no existir una equivalencia segura.
- También se auditaron 19 etiquetas antiguas en lenguaje natural: 14 apuntan explícitamente a un ID v2 y 5 quedan retiradas por ambigüedad. No se usa coincidencia automática por nombre.
- Los artículos anatómicos agregados (`espalda`, `brazos`, `piernas`, `glúteos`, `abdomen`) tienen membresías inversas explícitas para sumar los músculos v2 correspondientes sin heurísticas de nombre.
- Los índices inversos de músculo, articulación y patrón se derivan de cada configuración; la prueba de consistencia recorre las 523 configuraciones y no permite perder una relación.
- `hip_flexors → recto-femoral` y `tensor_fasciae_latae → glúteo-medio` son aproximaciones documentadas por ausencia de entidades específicas en el atlas actual; no se ocultan como equivalencias exactas.

## Gate reproducible

`AprendeCatalogAuditTest` verifica recuentos, cobertura editorial/anatómica, umbrales y duplicados, sincronía del sobre enriquecido, resolución exacta de opciones, integridad de ontología, 85 referencias estáticas con decisión explícita, consistencia de enlaces inversos, lente determinista y ausencia de drenaje/RPE/fatiga en la ficha de ejercicio. El refresco de Room está gobernado por `APRENDE_CONTENT_REVISION` y no requiere migración de esquema.

La similitud se calcula solo con metadatos de la configuración: grupo/intención preservada, patrón, músculos por rol, articulaciones, región, cadena, lateralidad y equipamiento. Se muestran tres bandas (equivalentes, variantes del patrón y transferencia anatómica); no se usa nombre, fatiga, RPE ni orden aleatorio, y se deduplica la misma configuración cuando aparece como definición y como entrada explícita.

En UI se conservaron serif, infobox y divisores editoriales, con fondos y rellenos neutros y un único azul desaturado para enlaces. Los colores semánticos quedan limitados a advertencias anatómicas o gráficos biomecánicos; no hay `BorderStroke` de color en las superficies de Aprende (el componente de escenarios de fatiga sigue existiendo solo para el editor/analítica fuera de esta ficha).
