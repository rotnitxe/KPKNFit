# KPKN · Artefactos de sesión en vivo · Iteración 02

Abre **prototipos.html** o **GALERIA.html**. El prototipo incluye fuentes, ilustración, SVG, estilos y lógica. Funciona sin servidor y no modifica datos reales.

**Tarjetas y Lista están aprobadas como direcciones. Foco está descartada.** Los refinamientos de esta entrega siguen disponibles para revisión.

- 62 escenarios: registro y técnicas, preparación, descanso completo/tarjeta, feedback, juegos, etiquetas, configuración, reemplazos, avisos, analizador, captura y voz.
- Pantallas: 360 × 740, 393 × 852 y 412 × 1007; texto 100 % y 130 %.
- Tarjetas con marco fijo por viewport, sin scroll interno.
- Lista con registro en cada serie; el inspector permite comparar la variante con roadmap.
- Captura y comandos de voz simulados. La guía de ejemplo puede reproducirse mediante síntesis de voz del navegador al pulsarla.
- Minijuegos jugables: tocar/Espacio para saltar o volar; arrastrar/flechas para la nave.
- Cambiar vista conserva el borrador. Cambiar escenario o recargar reinicia el ejemplo.

[Plan y contratos de implementación](PLAN-PROVISIONAL.md) · [Verificación web](VERIFICACION.md) · [Galería](GALERIA.html).

Fuentes: index.template.html, styles.css, app.js, refinements.css, refinements.js y arcade.js. **node build.cjs** recompone el HTML con recursos locales de KPKN. **node serve.cjs** sirve exclusivamente en http://127.0.0.1:8769.

El archivo **KPKN-sesion-iteracion-02.zip** es la entrega actual. Se conservan **KPKN-sesion-prototipos.zip**, **fuentes-iteracion-01.zip** y la carpeta **capturas/** como antecedentes de la primera pasada. Las capturas nuevas están en **capturas-v2/**.
