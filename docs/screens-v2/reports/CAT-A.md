# CAT-A · Catálogo de ejercicios

Trabajo en `C:\kw\u15`, rama `sv2/catalogo`, sobre `373729ae0`. Implementación y comprobaciones automáticas terminadas: 66 pruebas verdes en 10 clases. La QA visual en emulador sigue pendiente de la fase final.

Se leyeron `CLAUDE.md`, el brief, las leyes del plan, las decisiones del usuario, el mapa del catálogo, AUD2 y los ocho tableros aprobados. Los informes R1/R2 están en el directorio `kpkn_editor_design`, padre de `project`. El scratchpad se consultó en modo de solo lectura.

| Tablero | Implementación |
| --- | --- |
| E1 · Añadir | Aporte por una serie efectiva con el cálculo existente de EMI, sin porcentajes. Se respeta el aporte explícito; los pesos por rol son 1 / 0,5 / 0,4 / 0,1. |
| E2 · Búsqueda | Altura de polea con un raíl visual y agarre dentro del resultado. Solo se muestran valores materializados para el implemento. La fila y su imagen usan la configuración mostrada. |
| E3 · Selección | Orden numerado y accesible, reordenamiento, quitar y entrega ordenada. Se conservan las configuraciones distintas de una misma definición. |
| E4 · Superserie | Límite de cuatro en la entrada y la confirmación; rondas y descansos guardables. La entrada desde una superserie existente tiene título propio y añade directamente. |
| E5 · Reemplazar | Contexto y dosis del plan, candidato pendiente, cambios de configuración y confirmación explícita. Acceso directo a la ficha. |
| E6 · Vacíos | Diferencia entre ausencia global y un filtro sin resultados. «Ver en Todos» conserva la búsqueda; se puede borrar la consulta. |
| E8 · Carga y error | Cabecera, búsqueda y filtros estables, esqueleto con estado accesible y reintento real del repositorio. |
| E9 · Técnica | «Elegir ejercicio», un candidato pendiente restaurable y botón «Elegir» antes de entregar el resultado. |

Se mantienen la paleta y la tipografía KPKN y los objetivos táctiles de 48 dp en los controles incorporados. Las opciones proceden del catálogo v2; los aportes se calculan con `oneSeriesVolumeContributions` y `resolveMuscleVolumeContribution`.

La única adaptación de aserciones existentes es el texto «Agrega ejercicios para…» → «Añade ejercicios para…», para coincidir con el tablero y la terminología aprobada. Las comprobaciones de contenido y comportamiento se conservan.

Se materializaron los tres manifiestos de imágenes ausentes por el checkout parcial desde sus blobs exactos de `HEAD`. Sus 623 imágenes están presentes; no se modificó el contenido versionado ni las aserciones de cobertura. Los hashes y los blobs constan en `artifacts/cat-a-20261021/fixtures.json`.

Se añadieron pruebas Robolectric/Compose de los ocho tableros y un arnés de render de la pantalla real con imágenes de 360 × 880 px al 100 % y al 130 % de letra. La ejecución se realiza exclusivamente mediante `bash /c/kw/tools/gradle_slot.sh`, en primer plano, con `-Xmx8g`, las dos tareas de compilación autorizadas y pruebas filtradas del catálogo y del editor afectado. Logs y evidencia local: `artifacts/cat-a-20261021/`.

No se usaron teléfono ni adb, no se hizo push y no se escribieron tableros ni otros worktrees.


## Continuación Codex · 22-oct-2026 (verificada)

Se conserva todo el diff recibido. Para E4, el estado de `CatalogReadyContent` queda dentro de un `rememberSaveableStateHolder` anterior al cambio de estado del repositorio. Así la selección, la hoja abierta y su borrador sobreviven a la salida temporal de Ready durante una recreación/carga. La prueba `e4CreadorConservaRondasDescansosYOrdenAlRecrear` mantiene sus expectativas: cuatro rondas, descansos 0/120 y orden de ejercicios.

La clase de recorridos usa `GraphicsMode.NATIVE`, como los renders. En ED-B este ajuste resolvió cinco falsos fallos de clic sin cambiar callbacks ni aserciones; La corrida propia de CAT-A confirmó los recorridos y sus aserciones. Todavía no se ha integrado en u11.


## Resultado de la continuación

`C:/kw/logs/cat-a-2.log`: **BUILD SUCCESSFUL, 7 min 43 s; 66 pruebas, 0 fallos**, diez clases concretas. Incluye las 19 pruebas de los tableros, seis de cabecera de reemplazo, seis de fila y las regresiones de búsqueda, etiquetas, reemplazo, contratos y estado del catálogo. La prueba E4 conserva tras recrear cuatro rondas, descansos 0/120 y el orden de los dos ejercicios. No se eliminaron sus comprobaciones.

Los nueve métodos de render generaron **22 PNG** a 360 × 880 y 100/130 % en `C:/kw/shots/cata`, con dos estados para E4, E6 y E8. Se revisaron añadir, búsqueda, selección, superserie, reemplazo, vacíos, carga/error y técnica. Algunas capturas de Canvas muestran recortes de texto/cabecera, como el arnés de ED-B; el botón de confirmar al 130 % exige comprobar el scroll tras aumentar la letra. Estos PNG no acreditan una QA visual definitiva. Verificar expresamente cabeceras y acciones inferiores en el emulador antes de la entrega.

La prueba de selección confirma los IDs materializados de polea baja y agarre amplio y el resultado entregado al añadir; no se inventan configuraciones. Los 623 recursos de imágenes siguen procediendo de los blobs originales, sin cambios versionados de assets.

Sin cambios de Room, push, emulador, APK ni merge en este paquete. Se conserva el artefacto local `artifacts/cat-a-20261021/` fuera de los commits.
