# Paquete SUPLEMENTOS · prefijo `sp` · pestaña `suplementos` · `maps/suplementos.md`
Lee antes `BRIEF_COMUN.md`. Punto del usuario: **24**.

## Palabras del usuario (literal)
> 24. En nutrición hay una opción de "suplementos" que solo incluye creatina, cafeina nada más, te encargo un rediseño para  añadir más suplementos y que tengan un sistema de registro rapido, incluso desde la tarjeta de alimentacion de la home.

## Dónde está hoy (léelo)
`screens/nutrition/design/NutritionSupplementsModule.kt`, `screens/nutrition/components/SupplementTrackingComponents.kt`, `NutritionViewModel.kt`,
`screens/nutrition/nutrients/NutrientFootprintModel.kt` y `domain/nutrition/DriTargets.kt` (¿los suplementos suman a vitaminas y minerales?),
el modelo y la tabla de datos de suplementos (Room: busca «supplement»), el registro de comida y su huella de vitaminas (`docs/design/arreglos-v1`,
`registros-v1`), y la tarjeta de alimentación de **Inicio** («Nutrición de hoy»: `screens/home/` + `design/`; el lienzo `arreglos-v1` ya la reorganizó con un
botón que abre el registro). Lee cómo se guardan hoy creatina y cafeína (dosis, hora, unidades, objetivos diarios, recordatorios).

## Qué debe contener el prototipo
1. **Módulo de suplementos en Nutrición** rediseñado con el lenguaje vigente: lo tomado hoy (cápsulas/filas con dosis y hora), lo pendiente según tu
   plan, totales del día (p. ej. cafeína total frente a su techo seguro, creatina vs. dosis diaria) y su historial corto.
2. **Catálogo ampliado** de suplementos (basado en evidencia y relevante para entrenamiento y salud; no marcas): creatina monohidrato, cafeína, proteína
   (whey/vegetal), vitamina D, omega-3, magnesio, zinc, hierro, multivitamínico, electrolitos, beta-alanina, citrulina, ashwagandha, melatonina… decide
   la lista con criterio y justifícala brevemente; cada uno con unidad, dosis típica/segura, momento sugerido y qué aporta a la huella de nutrientes
   (si el motor lo permite). Marca `new` lo que no exista hoy y `ask` lo que requiera decisión del usuario (p. ej. alcance de la lista, avisos de
   seguridad, interacción con DRI). Las advertencias de seguridad deben ser sobrias y no inventar medicina.
3. **Registro rápido**: un toque para registrar tu dosis habitual (con deshacer), mantener para ajustar dosis/hora, favoritos y «mi pila» (los que tomo
   cada día), registro desde el propio módulo, **desde la tarjeta «Nutrición de hoy» de Inicio** (acceso rápido sin entrar a Nutrición: diseña la
   tarjeta de Inicio con ese acceso, coherente con el lienzo arreglos-v1) y, si aplica, desde una hoja de «añadir» general. Feedback al registrar
   (animación breve de confirmación, el anillo/cifra que se actualiza).
4. **Configurar un suplemento** (alta/edición): nombre, dosis, unidad, horario, recordatorio (¿existe hoy? léelo), objetivo diario, notas; baja.
5. Estados: vacío (primera vez), día con todo tomado, techo superado (cafeína), hora de tomarlo, sin conexión no aplica; texto 130 % y 360 × 740.
Interactivo: tocar para registrar, mantener, deshacer, añadir uno del catálogo.
