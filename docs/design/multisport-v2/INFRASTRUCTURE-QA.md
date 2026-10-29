# Infraestructura y regresiones dirigidas

El orquestador observó un `ReferenceError: SCENARIOS is not defined` al cargar la galería completa con muchos iframes. La causa original no quedó demostrada. La carga simultánea de las 46 vistas y una dependencia que no llegue a inicializarse son situaciones que ahora se gestionan explícitamente; no se atribuye el error observado a una de ellas sin evidencia adicional.

## Cambio concreto

- `index.html` inicia `boot.js`, nuevo archivo runtime que debe estar incluido en el paquete. Carga y comprueba `kpkn.js`, `scenarios.js`, `screens.js`, `sports.js`, `new-core.js` y `app.js` en orden. Antes del aviso de disponibilidad verifica contenido del renderer, ruta, las dos hojas de estilo y fuentes locales Inter/Syne.
- `gallery.js` crea las vistas cercanas a la zona visible con límite de **dos inicios simultáneos**. Solo un mensaje del renderer con iframe, origen, ruta y token coincidentes confirma disponibilidad. El evento `load` por sí solo no basta. Los previews alejados se descargan; volver a ellos inicia una vista independiente.
- Carga, error y reintento son visibles. Los nombres de archivos y detalles técnicos permanecen en metadatos de QA. Los errores globales solo afectan al renderer si se puede asociar su fuente a los archivos propios; un error ajeno sin esa asociación no reemplaza el producto.
- El modo preview no lee ni escribe el `localStorage` del recorrido editable. El estado de cada miniatura parte del caso inicial y no cambia decisiones del usuario.
- `#gallery-grid` publica `data-active`, `data-max-active-seen`, `data-limit` y `data-frames`. Cada preview publica `data-state`, `data-attempts` y detalles de error/etapa para el arnés de revisión. Estos son metadatos de revisión, sin texto técnico dentro del producto.

`app.css`, `kpkn.css` y los componentes visuales especializados de `sports.js` se conservan en esta tanda. El catálogo sigue teniendo **46 escenas**.

## Colocación y controles

La validación `placementIssue` se comparte entre el reparto del wizard, mover una sesión y confirmar una alternativa. Comprueba ventana, inicio/fin, sede activa, Exterior con ruta para running, piscina para natación y barra/discos/rack para powerlifting. Una preparación transferible u otro enfoque declarados mantienen su excepción explícita. Cada alternativa muestra la sede efectiva y si añade o actualiza la ventana; un cambio de sede se explica junto a esa consecuencia. Usa el material de la sede de destino. La hoja de movimiento obtiene el nombre y horario del compromiso declarado; no conserva un texto fijo del piloto.

El handler de campos numéricos aplica únicamente atributos `min`/`max` presentes, no vacíos y finitos. Los `select` numéricos no tienen esos atributos y conservan el valor elegido. Al leer estado anterior, solo se recuperan `windowDay`, `classDay` y `homeDay` si no son enteros entre 0 y 6; esto cubre el `null` producido por serializar un `NaN` previo sin borrar objetivos válidos.

| Ruta / preparación | Control | Cambio observable / reinicio |
|---|---|---|
| `index.html#wizard-availability` | Seleccionar Viernes en «Ventana que quieres editar» | Los campos editan `windows.4`, el selector conserva Viernes y no aparece error. Reiniciar desde el panel de revisión. |
| Disponibilidad → editar compromiso fijo | Seleccionar Martes, conservar nombre e inicio/fin | El compromiso queda en Martes al cerrar y al regresar. Reinicio de estados locales. |
| `index.html#week` | Mover hacia una ventana con sede desactivada | Se explica que la sede no está activa y la sesión mantiene su día. Reactivar sede o reiniciar. |
| Semana / alternativa | Intentar running en Casa, natación sin piscina o powerlifting sin material en destino | La restricción identifica el recurso/lugar; no se confirma una colocación inválida. Declarar material adecuado o reiniciar. |
| Powerlifting → preparación transferible | Mover ese apoyo a una ventana de Casa sin barra, activa y con tiempo suficiente | La preparación declarada puede moverse y mantiene su naturaleza; no se afirma práctica de powerlifting con sustitutos. Reiniciar para retirar la elección. |
| `gallery.html` | Entrar en categorías y desplazar inicio/fin | Vistas cercanas pasan por preparación a disponible; el máximo de inicios medido en DOM no supera dos. Recargar reinicia la cola. |
| Galería con dependencia ausente / inicio no confirmado | Reintentar | Aparece error explícito y el botón inicia un nuevo intento; restablecer el recurso y recargar. Esta condición se simula en el chequeo local, no es una escena de producto. |

## Evidencia y límites

- `check-prototype.mjs`: render local de 46 escenas y **36 contratos deportivos originales**; resultado en `QA-LOCAL.json` y controles por escenario en `QA-INVENTORY.md`.
- `check-infrastructure.mjs`: **36 comprobaciones suplementarias** de arranque/handshake, cola, aislamiento, restricciones de colocación y selectores. Ejecuta los listeners reales `input`/`change` para `windowDay=4` y `classDay=1`, además de la carga de un estado con días inválidos. Usa VM y DOM simulado: no sustituye interacción real de navegador. Resultado en `QA-INFRASTRUCTURE.json`.
- El orquestador informó una revisión real de las nueve categorías, con **46 previews distintos disponibles**, `data-limit=2` y `data-max-active-seen=2`. También retestó recuperación de `windowDay=null` conservando powerlifting, selección de Viernes 4, clase en Martes 1 con nombre y horario declarados, hoja de movimiento dinámica y bloqueo de preparación PL al mover hacia un gimnasio inactivo. Son resultados de sus recorridos de navegador. Las etiquetas finales de sede en alternativas se entregan para su revisión dirigida.
- Los `.js` y `.mjs` propios se comprueban con `node --check`; número y hashes están en `QA-CANDIDATE.json`. El arnés `review-qa.html` es del orquestador y queda fuera de ese manifiesto.

Nuevo runtime obligatorio para la whitelist: **`boot.js`**. `check-infrastructure.mjs` es un chequeo de desarrollo y no participa en el arranque del producto.

Sin build o instalación Android, prueba de hardware ni aprobación visual humana en esta evidencia.
