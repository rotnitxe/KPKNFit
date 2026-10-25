# Contrato visual para las ampliaciones de KPKN

Regla directa del usuario: **todo lo nuevo debe apostar por lo visual, animado y amigable sin perder potencia**.

**Estado vigente:** v1 y v2 rechazadas; diseño de UI diferido por el usuario. No trasladar esos prototipos a Kotlin. El wizard conserva su navegación sin tabbar. Se reutiliza el flujo de sesión existente; los vivos deportivos inventados en las maquetas no son requisitos de producto. El trabajo actual de motores y datos continúa sin cambios visuales.

## Alcance de los prototipos

Corrección explícita del usuario: **los prototipos son para lo nuevo que se va a integrar**. La galería presenta incrementos funcionales y explica qué se agrega en cada caso. No incluye como entregables reconstrucciones del Home, diario nutricional, Body, perfil ni sesión actuales. La interfaz existente puede aportar el contexto de navegación; las capturas y comparaciones de fidelidad quedan como controles internos separados de la galería.

## Identidad y continuidad

La referencia es la app Android pre release 17: composición, fuentes locales, iconos, superficies, RINGS y navegación. Las extensiones deben partir de sus componentes reales. Cada traducción HTML mantiene un mapa hacia la fuente nativa y una comparación visible. Una apariencia nueva y atractiva no sustituye la fidelidad a KPKN.

Las galerías `docs/design/multisport-v1/` y `docs/design/multisport-v2/` fueron rechazadas por el usuario. Se conservan como historial. Ninguna pantalla nueva es referencia estricta para Kotlin antes de su aprobación visual por el usuario.

## Criterios por pantalla

| Criterio | Evidencia exigida |
|---|---|
| Entender primero con la vista | Una representación propia de la tarea: cadena de un complejo, recorrido de estaciones, línea de rounds, mapa de posiciones, trazado de intervalos o marcador. No basta cambiar el nombre de una tarjeta de gimnasio. |
| Editar directamente | La persona puede tocar el elemento que quiere cambiar y ve el resultado en su contexto. Las acciones principales tienen texto o nombre accesible. |
| Movimiento con propósito | La transición explica selección, avance, reorganización, recuperación o cambio de estado. No demora el registro ni oculta información. |
| Conservar potencia | Métricas, parámetros y reglas especializadas siguen accesibles en una expansión o una hoja clara. Simplificar la primera vista no elimina el control avanzado. |
| Accesibilidad | Movimiento reducido sin pérdida de información; contraste legible, foco de teclado, objetivos táctiles suficientes y significado que no depende solo del color o de la animación. |
| Estados honestos | Ausente, antiguo, perdido, pendiente y medido se distinguen de estimado. El prototipo no simula una integración de hardware verificada. |
| Coherencia | Cambiar de deporte conserva navegación y patrones comunes. Los datos y resultados del ejemplo cambian de forma coherente y se pueden reiniciar. |
| Calidad verificable | Revisión en anchos móviles, sin desbordamientos, y al menos una interacción significativa comprobada por escenario. |

## Separación de evidencias

Una captura del HTML prueba su apariencia renderizada. Una interacción del HTML prueba ese prototipo. Ninguna prueba por sí sola los motores de Kotlin, una instalación Android ni la compatibilidad de un Huawei Watch.

Para promover una fase se registran por separado código, pruebas, compilación, instalación, actividad reanudada, recorrido nativo y aprobación visual. Una revisión del orquestador puede exigir correcciones; no reemplaza la aprobación del usuario.
