# KPKN · ampliaciones multideporte v2

Abrir [la galería](./gallery.html) o [el recorrido nuevo](./index.html#wizard-sports). Funciona localmente, sin red, con fuentes y recursos del paquete.

El catálogo presenta **qué se agrega**: perfil deportivo múltiple, ventanas por hora/sede, clases fijas editables, restricciones de material y alternativas; reglas nuevas de fuerza y registros especializados de cada disciplina. La selección del wizard se conserva y condiciona el reparto. El caso fuerza + running es un preset de revisión explícito.

- [Controles y cambios esperados](./QA-INVENTORY.md)
- [Fuentes nativas, medidas y mapa de ampliaciones](./DESIGN-SOURCE-MAP.md)
- [Resultado de comprobaciones locales](./QA-LOCAL.json)
- [Infraestructura de previews y regresiones de controles](./INFRASTRUCTURE-QA.md)
- [Resultado de comprobaciones suplementarias](./QA-INFRASTRUCTURE.json)
- [Manifiesto del candidato y hashes](./QA-CANDIDATE.json)

Home, Training, Nutrición, Cuerpo y vivo actuales no son propuestas de esta galería. `baseline.html` y `comparison.html` quedan como controles visuales internos separados.

Los estados se guardan en el navegador. **Reiniciar estados locales** restablece el caso inicial; en móvil puede abrirse `index.html?reset=1#wizard-sports`.

Las miniaturas de la galería usan estados independientes de ese recorrido editable: no leen ni escriben sus decisiones guardadas. Se preparan al entrar en la zona visible, con un máximo de dos inicios simultáneos. Un estado de error ofrece reintento explícito.

Para revisar **fuerza + MMA en el mismo wizard**, selecciona esos dos deportes, elige experiencia/meta/prioridad de MMA y abre el compromiso fijo desde disponibilidad. Declara nombre, día, inicio/fin y selecciona **Vincular a disciplina → MMA**. El nombre inicial «Grappling» nunca se renombra ni se atribuye a MMA por su texto.

Sin esa vinculación, la revisión explica qué clase falta y permite definirla; la propuesta queda bloqueada. Con la clase vinculada, se conserva una vez y solo se colocan los apoyos de fuerza. Por ejemplo, fuerza 65 + 70 min y una clase declarada de 90 min suman 225 min. No se genera el contenido de contacto de MMA.

El wizard es una muestra de planificación local; los motores nuevos siguen siendo propuestas.

Prototipo HTML revisable. No se afirma implementación Android, instalación, aceptación visual, compatibilidad de sensores o prueba física.
