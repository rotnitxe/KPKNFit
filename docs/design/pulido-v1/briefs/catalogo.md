# Paquete CATÁLOGO · prefijo `ct` · pestaña `catalogo` · `maps/catalogo.md`
Lee antes `BRIEF_COMUN.md`. Punto del usuario: **3**.

## Palabras del usuario (literal)
> 3. Cuando quiero aplicar "una plantilla o método" desde la vista de estructura, me aparece un sheet con una lista mal diseñada de plantillas y métodos de progresión, quiero un rediseño para que se vea como un "catálogo" bonito y profesional, ya tenemos materia prima para aprovechar. Dame un rediseño. Entrega los prototipos para aprobarlos antes.

## Dónde está hoy (léelo)
`screens/programdetail/design/structure/ProgramTemplatesSheet.kt` (la hoja «Aplicar plantilla o método»: «Rellena las semanas vacías. Si ya
tienen sesiones, eliges si sustituirlas.»; sección PLANTILLAS con «PL Principiante 3 días · 12 sem»…, sección MÉTODOS DE PROGRESIÓN con «KPKN SBD · 4 días»…).
Busca de dónde salen los datos (plantillas y métodos: nombres, semanas, sesiones/semana, descripciones, objetivos, nivel) y cómo se aplican
(semanas vacías vs sustituir). Captura de hoy: `h03-plantillas-metodos`. La «materia prima» que menciona el usuario: portadas de programa
(`ProgramCoverImage.kt`, `docs/design/program-detail-v1/src/covers.js`), la ficha visual del asistente (`docs/design/arreglos-v1/src/arreglos.css`
clases `pf-*` y `scenes-programa.js`), el catálogo de programas del wizard (`screens/onboarding/design/entreno/plan/`, `GoalProfileArt.kt`) y la
metadata que tengan las plantillas y los métodos en el código.

## Qué debe contener el prototipo
Una hoja a pantalla casi completa que se lea como **catálogo editorial**, no como lista de ajustes:
1. Portada del catálogo: búsqueda, filtros/segmentos (Plantillas · Métodos; por nivel, días por semana, duración, objetivo si la data lo permite),
   destacados y filas/tarjetas con arte (portadas de líneas), metadatos legibles (semanas, sesiones por semana, nivel, objetivo) y descripción corta.
2. **Ficha de una plantilla**: qué trae (esquema semana × sesión, grupos musculares, volumen), para quién es, qué rellena en tu programa;
   «Aplicar» con la elección clara **semanas vacías vs. sustituir las que ya tienen sesiones** (hoy: «Rellena las semanas vacías. Si ya tienen
   sesiones, eliges si sustituirlas») y vista previa de a qué semanas afectará.
3. **Ficha de un método de progresión** (p. ej. «KPKN SBD · 4 días…»): cómo progresa (gráfico/tabla de progresión por semanas), qué variables
   toca (carga, reps, RPE), requisitos (1RM/TM conocido: lee qué pide el motor), «Aplicar».
4. Estados: sin resultados, cargando, confirmación de sustituir (con consecuencias), aplicado (éxito con resumen de lo que cambió y deshacer si existe),
   programa sin semanas vacías, texto al 130 %, 360 × 740.
Interactivo: filtrar, abrir ficha, cambiar sustituir/rellenar y ver cómo cambia la vista previa, aplicar.
No inventes plantillas ni métodos: los nombres y cifras son los del código; las descripciones breves que falten, escríbelas tú y márcalas `new`.
