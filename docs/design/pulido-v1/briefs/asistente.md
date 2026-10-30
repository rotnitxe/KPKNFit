# Paquete ASISTENTE · prefijo `as` · pestaña `asistente` · `maps/asistente.md`
Lee antes `BRIEF_COMUN.md`. Punto del usuario: **4**.

## Palabras del usuario (literal)
> 4. El asistente de sesión se ve asquerosamente simplista y mal diseñado, sobre todo la parte de "contenido" que debería llamarse "métricas", el volumen del plan de los musculos está mal diseñado y parece texto suelto mal hecho, lo mismo para el resto, y quiero que se unifique con las reglas de sesión, por tanto deería existir una cuarta opciión de "reglas" dentro del asistente de sesión. Dame un prototipo de este rediseño para aprobarlo antes.

## Dónde está hoy (léelo)
`screens/sessioneditor/components/sheets/AssistantSheet.kt` (las pestañas «Contenido», «Sugerencias», «Plantillas»), el motor que alimenta
cada pestaña (`domain/training/SessionCompositionPolicy.kt` y lo que use), la UI actual de las **reglas de sesión** (búscala: se abre desde el
botón «Reglas» de la barra inferior del editor), `ui/components/CanonicalMusclePalette.kt` (paleta de músculos).
Capturas: `h04-asistente-contenido` (volumen del plan con barras y números sueltos: «10.\n5» partido en dos líneas), `h05-asistente-plantillas`
(lista de «Pierna · Dosis compacta»… 32 plantillas por categoría, tarjetas con el mismo texto repetido «Dosis compacta para splits de frecuencia alta…»).

## Qué debe contener el prototipo
Hoja inferior (con alturas: media y completa) con **cuatro pestañas: Métricas · Sugerencias · Plantillas · Reglas** (el control de pestañas debe
caber en 360 px y verse elegante, con jerarquía clara):
1. **Métricas** (antes «Contenido»): ejercicios/series/duración como cifras de verdad (no texto suelto), **volumen por músculo** bien diseñado
   (series efectivas por grupo con la paleta canónica, marcas de referencia semanales si el motor las tiene, números alineados y que no se
   partan, orden con sentido, ver todos/solo los trabajados), y lo que el motor ya calcule (equilibrio, frecuencia, fatiga, tiempo…: lee el código;
   no inventes métricas que no existan: si propones una, etiqueta `new` y di qué motor haría falta). Estados: sesión vacía, sesión con cardio, texto 130 %.
2. **Sugerencias**: cómo se muestran las sugerencias reales (cuál es el motivo, qué cambia, aplicar/descartar/deshacer), vacío («Todo en orden»).
3. **Plantillas**: catálogo de plantillas de sesión (búsqueda, categorías plegables o filtros, tarjetas con tiempo/ejercicios/series/dificultad y
   una vista previa del contenido antes de aplicar, «Aplicar» con la elección entre sustituir o añadir si la app la ofrece). Tarjetas distintas entre
   sí (no el mismo párrafo repetido): usa los datos reales (nombre, grupo, duración, series). Reutiliza el lenguaje de «ficha del asistente»
   (`docs/design/arreglos-v1/src/arreglos.css`, clases `pf-*`) y las portadas (`covers.js`) si encaja.
4. **Reglas** (las reglas de sesión de hoy, integradas): lee qué reglas existen, qué configuran y cómo se activan; diséñalas con la misma piel.
   Estado vacío, regla activa, regla que choca con el contenido.
Incluye la transición entre pestañas, la entrada de la hoja y el estado «aplicando plantilla».

Nota de coordinación: el paquete `editor` quita el botón «Reglas» de la barra inferior porque aquí pasa a ser pestaña; el paquete `catalogo`
diseña el catálogo de plantillas y MÉTODOS de la vista Estructura del programa (otra hoja, otros datos): no lo dupliques, pero mantén criterios
visuales compatibles (tarjetas-ficha, metadatos, aplicar).
