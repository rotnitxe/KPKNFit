# Arreglo K4 · píldora superior del editor en vidrio (12) y botón flotante de «Nueva medición» (21)
Lee antes `briefs/impl-COMUN.md`.

## 12. Píldora superior del editor de sesiones: vidrio real, el contenido pasa por debajo
Palabras del usuario: «12. La pildora superior de ir atrás, dia de semana y tres puntos estan cortando el contenido al scroll, cuando deberían tener efecto Blur y glass y dejar que las cosas pasen por abajo.»
Capturas de hoy: `docs/design/pulido-v1/hoy/h13-editor-lista.jpg`, `h12-swipe-eliminar.jpg` (la píldora «‹ · ‹ Jue ▾ › · ⋯» arriba; el contenido se corta debajo de ella y de su fila).
Código: `screens/sessioneditor/SessionEditorScreen.kt`, `screens/sessioneditor/frame/` (cabecera/barra del editor), `SessionEditorScrollRenderer.kt`, `components/SessionHeroParts.kt`,
y las piezas ya existentes de vidrio: `ui/design/KpknTopIsland.kt`, `KpknIslandVisibility.kt`, `ui/components/KpknGlass.kt`, el patrón de `screens/programdetail/design/ProgramFloatingBar.kt`
(barra flotante en vidrio con desenfoque, ya aprobada e implementada en Program Detail) y `screens/nutrition/design/NutritionLogIsland.kt`. Haze `dev.chrisbanes.haze` 1.7.2:
la lista del editor debe registrarse como fuente (`hazeSource`/`Modifier.haze` según la API de 1.7.2 que usen esos archivos) y la píldora aplicar el efecto (`hazeEffect`/`hazeChild`).
Hecho esperado: la fila superior (atrás · selector de día · ⋯) deja de tener un fondo opaco/corte duro; son islas flotantes de vidrio con desenfoque real y el contenido de la lista pasa
por debajo (borroso) sin cortarse; en reposo el primer elemento no queda tapado (padding superior del contenido); sin romper el selector de día ni los menús. No toques la barra
INFERIOR del editor (se rediseñará aparte tras aprobar el prototipo).

## 21 (parte directa). Botón flotante para «Nueva medición» y esquinas negras de la hoja
Palabras del usuario: «21. Para añadir una nueva medición, el sheet abre un slider de peso que se ve con esquinas negras cortadas, haz ese ajuste para que no se vea de esa forma y además, mejora el diseño de como se ve, y no solo eso, sino que el boton para acceder a este debe ser flotante, igual que el de registrar comida en la pantalla de nutrición.»
Código: la pantalla de **Cuerpo** (busca `screens/body*`, «Nueva medición», «Añadir medición», «BodyScreen», «BodyMeasurement») y el botón flotante de «Registrar comida» de Nutrición
(`screens/nutrition/NutritionScreen.kt`, `screens/nutrition/design/NutritionLogIsland.kt`, `ui/design/KpknIslandVisibility.kt`: cómo se coloca, cómo se oculta al hacer scroll, qué
componente usa). Hecho esperado: (1) el acceso a «Nueva medición» pasa a ser un botón flotante **igual en componente, estilo, posición y comportamiento al scroll** que el de Nutrición (reutiliza
el mismo componente/isla con otro icono y etiqueta; si el de Nutrición no es reutilizable tal cual, extrae lo común con el cambio mínimo); lo que ocupaba su sitio antes se elimina;
(2) arregla las **esquinas negras cortadas** de la hoja de la medición (el slider/regla del peso se dibuja con un fondo o un clip que no respeta las esquinas redondeadas de la hoja: busca el
`background`/`clip`/`Surface` mal anidado, el `shape` de la hoja y el contenedor del slider; la hoja y su contenido deben compartir la misma forma y color) — el rediseño completo del slider
vendrá después, aquí solo el defecto.
Archivos permitidos: los de esas pantallas y piezas compartidas mínimas (`ui/design/*`) si hace falta extraer algo; no reformatees nada ajeno. NO toques las pantallas de la sesión en vivo.
