# Roadmap multideporte v1

## Principios de producto

- Todo lo nuevo debe ser visual, animado y amigable sin perder potencia; la fidelidad a KPKN pre release 17 se contrasta con la app nativa antes de promover una maqueta.
- Los prototipos entregables muestran únicamente funciones nuevas. Las reconstrucciones de pantallas existentes son controles internos de fidelidad y quedan fuera de la galería.
- Android nativo, de uso personal, local-first y funcional sin conexión.
- Voz y visión pueden usar modelos locales. Su disponibilidad y calidad se verifican por flujo; esto no afirma que cada función ya esté implementada.
- Cloud complejo y coaching complejo quedan fuera del alcance actual.
- Primer híbrido nativo: fuerza + running. El mismo wizard HTML contempla fuerza + MMA y MMA solo; el contenido de contacto lo define una clase real vinculada por el usuario. Cardio v2 y nutrición se integrarán en el planificador con una sola contabilización de carga, tiempo y energía según contratos explícitos; no se presuponen fórmulas nuevas.
- Ningún reloj o sensor se declara compatible por analogía. Huawei Watch permanece sin modelo ni compatibilidad verificada.

## UI diferida y continuidad de implementación

El usuario rechazó también la galería v2 y pidió continuar la implementación dejando la UI para después. Las fases de motores y persistencia no dependen de esas maquetas. Se preservan las sesiones, el editor y el calendario actuales. El siguiente bloque prepara propuestas semanales sobre sesiones reales y las guarda como propuestas, sin aplicarlas al calendario ni crear vivos nuevos. Las decisiones de captura por disciplina se resolverán reutilizando el flujo actual antes de ampliar sus superficies.

## Etapas

| Etapa | Alcance | Dependencia / resultado que debe quedar demostrable |
|---|---|---|
| **MS00** | Baseline, inventario y dependencias | Identificar el estado real de los prototipos, fuerza, sesiones, cardio v2, nutrición, modelos locales y superficies compartidas. Registrar propietarios, contratos, datos existentes y riesgos de compatibilidad. La etapa queda abierta hasta que ese inventario tenga evidencia del candidato de trabajo. |
| **MS01** | Núcleo, fuerza avanzada y PerformanceLab manual/reglas | Definir los perfiles y modelos de deporte/entrenamiento; guardar planes, sesiones y resultados con compatibilidad; preservar la lógica de fuerza existente y ampliar reglas avanzadas. PerformanceLab empieza con entradas manuales y reglas explícitas, sin prometer análisis automático. Paquetes: MS01A perfiles/modelos; MS01B persistencia y compatibilidad. |
| **MS02** | Planificador fuerza + running | Crear el primer híbrido fuerza/running, integrar la referencia y los componentes validados de Cardio v2, y conectar nutrición sin doble conteo. Paquetes: MS02A planner; MS02B adapter entre fuerza, running, cardio y nutrición. Debe compartir historial y progreso sin perder el flujo de sesión actual. |
| **MS03** | Weightlifting complejo, skills de calistenia, strongman y armwrestling | Modelar complejos, progresiones de skills y eventos/cargas específicos; definir cómo se capturan, corrigen y revisan los datos. |
| **MS04** | Formatos CrossFit y HYROX | Representar formatos y transiciones con sus reglas de tiempo, rondas, estaciones y resultados; evitar convertir formatos distintos en una sesión genérica. |
| **MS05** | Striking, grappling, wrestling, judo y campamento MMA | Definir sesiones, bloques técnicos/físicos, rounds y seguimiento del campamento; especificar captura y calidad de datos antes de métricas derivadas. |
| **MS06** | Cycling, trail, rowing, swim y triathlon | Añadir cargas y métricas específicas de cada disciplina, entradas manuales primero y sensores solo con compatibilidad verificada; preservar los componentes comunes del planificador. |
| **MS07** | Climbing, sprint y jump | Definir intentos, vías/series, recuperación y mediciones válidas por deporte; distinguir rendimiento registrado de estimaciones. |
| **MS08** | Tennis, padel y deportes de equipo con seguimiento individual | Representar sesiones, acciones/sets o periodos, carga individual y contexto de equipo sin exigir cloud/coaching complejo. |

Las etapas se ejecutan en orden. No se declara una etapa compatible por haber dibujado su pantalla ni por compartir estructuras con otro deporte.

## Contrato obligatorio de cada etapa

Antes de implementar, acordar y documentar:

1. **Dominio:** entidades, estados, unidades, reglas, validaciones y qué significa cada resultado.
2. **Persistencia:** formato, migración o compatibilidad, restauración y propiedad local de los datos.
3. **Ejecución/captura:** editor y flujo en vivo, o captura externa cuando esa disciplina no se ejecuta en el teléfono; definir qué ocurre sin conexión y ante datos incompletos.
4. **Historial:** guardar sesiones/resultados y mostrar progreso sin reconstruir datos a partir de texto o de valores ausentes.
5. **Carga y calidad:** definir carga específica, procedencia, unidad, incertidumbre, faltantes y límites; no mostrar estimaciones como mediciones.
6. **UI:** aprobar el HTML del escenario antes de codificar esa pantalla. Un prototipo local es una propuesta visual; no es evidencia de integración.
7. **Verificación:** pruebas del dominio y persistencia, compatibilidad con datos previos, capturas de cada estado aprobado, y revisión de regresiones en los flujos que comparten datos.

Una funcionalidad que dependa de cámara, GPS, reloj u otro hardware añade una verificación concreta del dispositivo y del modelo/versión. Si esa verificación no existe, el estado sigue siendo no verificado y la UI debe mantener una ruta manual apropiada.

## Investigación de referentes

Seguir el orden **app + versión + hash + preguntas → análisis estático → runtime cuando haga falta → especificación propia → pruebas**. El runtime solo se usa para preguntas que no resuelve el código/recursos y cuando el entorno permita obtener evidencia atribuible. Conservar artefactos, comandos, versiones y límites; separar hallazgo, inferencia y resultado medido.

El benchmark cardio de `C:\kw\u5\docs\audits\2026-10-cardio-benchmark\README.md` es estático y marca runtime `NOT_RUN`. La referencia visual aprobada que sí está en este checkout es [`Cardio v2`](../design/multisport-v1/references/cardio-v2/index.html). La revisión de [WLAnalysis](../../artifacts/wl-analysis-re-20261017/INFORME.md) corresponde a un XAPK 2.13.9 analizado estáticamente y no acredita una medición real de KPKN. Los dos informes informan diseño y preguntas; no otorgan soporte a la aplicación.

## Evidencia por separado

En cada corte registrar candidato/commit y hashes de artefactos, y reportar de forma independiente: **código**, **pruebas**, **build**, **instalación**, **emulador/UI**, **dispositivo físico**, **aceptación del HTML/capturas**. `BUILD SUCCESSFUL` no significa instalación o QA; instalación correcta no confirma la pantalla esperada; capturas de un candidato anterior no prueban el actual. El estado de esta entrega está en [`execution-state.json`](execution-state.json).

## Workspace de implementación

`C:\Users\valen\.codex\worktrees\multisport-foundation\KPKNFit`, rama `codex/multisport-foundation`, base `f174a5020`. El worktree fue creado y adjuntado a este chat; recibe únicamente los archivos propios revisados mediante una lista explícita. Los cambios previos de la raíz compartida y los worktrees de otros agentes se preservan. Serializar cambios a superficies comunes como editor, sesión en vivo, navegación y adaptadores de fuerza/cardio.
