# KPKN · Programas y Program Detail · v1 (propuesta para aprobar)

27 de octubre de 2026. Rediseño y decisiones de producto de todo lo que cuelga de Entreno › Programas: la lista, crear programa, la
portada del programa, las pestañas Semana, Progreso (antes Volumen) y Estructura, y todos sus menús escondidos.

Abre `prototipos.html` (autocontenido, con las fuentes reales de KPKN). Para recomponerlo: `node build.cjs` (genera también
`artifact.html`, la misma página sin esqueleto, para publicarla). Fuentes en `src/`:

| Archivo | Contenido |
|---|---|
| `src/data.js` | Datos sintéticos de ejemplo y piezas compartidas (portada, cinta de fases, isla, hojas) |
| `src/scenes-decisiones.js` | Documento de decisiones y la asesoría sobre la calibración de volumen |
| `src/scenes-programas.js` | Lista, ⋯ de programa, Nuevo, catálogo, desde cero, importar, TM, archivados, vacío |
| `src/scenes.js` | Portada, Semana y Progreso |
| `src/scenes-estructura.js` | Mapa, revisión del plan, bloque, fechas, versiones, biblioteca |
| `src/scenes-menus.js` | Todos los menús y hojas escondidos con el patrón común |

Enlaces directos por pestaña: `prototipos.html#decisiones`, `#programas`, `#portada`, `#semana`, `#progreso`, `#estructura`, `#menus`.
Selectores de pantalla (360 × 740, 393 × 852 del Flip, 412 × 915) y texto al 100 % o 130 %.

## Leyes de la propuesta

1. La portada dice quién es el programa, en qué semana vas y qué toca hoy. Lo que pide decisión va a un único «Pendiente».
2. La Semana es una agenda: la sesión se ve sin desplegar y un día admite varias sesiones apiladas.
3. Progreso enseña lo que pasó (hecho), no lo planificado; el plan se compara, no sustituye.
4. Nada se elimina sin decirlo: cada elemento actual tiene su fila en «Decisiones» (se queda, se mueve, se fusiona o sale).
5. Sin datos, «—» o «Sin tendencia»; nunca cifras inventadas.

## Lógica nueva que pide

Estado de fuerza (sube, estancado, baja), trabajo por semana frente al plan, RIR medio frente al plan y orden de «Pendiente».
Detalle y umbrales en la pestaña Decisiones.

## Límites

Datos sintéticos. No sustituye la QA nativa (fontScale, TalkBack, insets, coste del desenfoque).
