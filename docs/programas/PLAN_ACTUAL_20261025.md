# Programas y periodización: adaptación vigente

Mandato del usuario del 25-oct-2026: aplicar `lively-squishing-bonbon.md` sin DeepSeek y adaptar el plan a la realidad actual; AUGE/RINGS ya no existen.

## Base y límites

- Base integrada: `1d399c1fc` (MFP + retirada de motores, Room 35). El WIP concurrente del checkout principal no se copia ni modifica.
- Rama de continuación: `codex/programs-periodization-current`. Sin push ni integración en master.
- El worktree histórico y su WIP se conservan completos. Sus resultados del 22-oct son antecedentes, no validación del código actual.
- Ninguna tarea modifica Room, esquemas publicados, originales de entrenamientos, catálogos grandes ni motores retirados.
- Propuestas de prescripción: confirmación explícita. El valor AUTO heredado se trata como propuesta, siguiendo el contrato actual.
- No se usará el nombre de otro modelo como coautor de cambios nuevos que no haya realizado.

## Traducción del alcance original

| Tareas | Aplicación vigente |
|---|---|
| T-01–04, T-10–18 | Recuperar concepto Programa, etiquetas y fidelidad; comprobar contratos sobre la base actual y referencias vigentes del catálogo. CAL5/CAL5b y StrengthModel ya integrados se consumen sin duplicarlos. C11 compara con referencias editoriales de volumen, sin afirmar que se haya medido la capacidad recuperable de la persona. |
| T-20–26 | Onda planificada, respuestas semanales declaradas y cambios propuestos. No inferir recuperación a partir de agujetas, dolor o ausencia de registros. Respetar protocolos de autor y sesiones ejecutadas. |
| T-30–35 | Revisión descriptiva: cumplimiento, series observadas y tendencias comparables. Sin veredicto de falta de recuperación ni puntuaciones fisiológicas. Los cambios del bloque siguiente siempre son propuestas confirmables. |
| T-50–53 | Informe, tarjeta y decisiones sobre ejecución vigente. No etiquetar auditoría de protocolos actual como AUGE; no reintroducir claves o tipos retirados. Usar cálculo de fuerza ya integrado. |
| T-70–74 | Variantes por fase y sustituciones con compatibilidad explícita; conservar procedencia y restricciones de equipo. |
| T-80–84 | Sustituir integración AUGE por resumen descriptivo del plan siguiente: series previstas y criterios de intensidad del propio plan, sin baterías, ACWR, sobreentrenamiento, readiness ni predicción de lesión. No existe un snapshot AUGE que deba reconstruirse. |
| T-90–94 | Mostrar evidencia real y cobertura del historial; conservar respuestas originales y su procedencia. No convertir molestias, vídeo o incoherencias de RIR en diagnóstico de movilidad/técnica ni en capacidad recuperable; no ajustar MRV automáticamente a partir de estas señales. Los objetivos personales de volumen requieren elección explícita. |
| T-100–103 | Sesiones pendientes y opciones explícitas de continuidad. Un período sin registros significa ausencia de registros, no prueba de no entrenar, descanso o pérdida de capacidad. Vuelta suave sólo por decisión de la persona. |
| T-104 | Permanece aplazada en el original; no modificar el editor fuera de las tareas expresas. |
| T-110–117 | Antes/día/después de competición con pesos editables, referencias y confirmación de marcas; sin entrenamientos ficticios ni resultados implícitos. |
| T-120–124 | Contexto independiente de nutrición, cuerpo y fase del programa; sugerencias visibles y confirmables. No alterar contabilidad nutricional ni prescripción por una supuesta recuperación. |
| T-40–43 | Programa a medida primero, catálogo plegado y opción de protocolo exacto con fecha; reutilizar los contratos del wizard actual. |
| T-60–62 | Matriz de tareas, resultados reales, desviaciones y pendientes. No declarar terminada una tarea por trasladar un commit histórico. |

T-19 carece de especificación en el original y no crea trabajo adicional. Los números de tests, programas o firmas de API antiguos se verifican frente al código; no son requisitos de conservar APIs retiradas.

## Identidad y procedencia en los contratos nuevos

Las respuestas, revisiones, sesiones saltadas y decisiones deben distinguir programa, ejecución, ciclo y ocurrencia de semana. Repetir un programa no puede heredar un salto o aplicar dos veces un ajuste de la ejecución anterior. Usar los resolutores de ocurrencias y las referencias por configuración ya existentes; los campos JSON nuevos tienen default. Las sesiones ejecutadas o en curso no se rematerializan.

Los enums guardados conservan valores retirados para compatibilidad; su existencia no significa que haya un motor activo. El informe presenta auditoría vigente de ADJUST_TM/ADJUST_XRM y decisiones originales del programa, sin renombrar ajustes derivados antiguos como decisiones actuales. Ausencia de registros se expresa como ausencia de registros del programa, nunca como prueba de no entrenar.

Las tablas MEV/MAV/MRV existentes son referencias editoriales. Las respuestas de esfuerzo o agujetas son declaraciones; sólo pueden sostener una propuesta explicada que la persona decida aplicar, sin afirmar que midan recuperación, movilidad, técnica o riesgo. Las observaciones originales se conservan con su fuente y unidad.

El guard de propuestas propias excluye AUTHORED_EXACT y procedencia ORIGINAL aunque falte sourceProtocolId. La nueva onda RIR genera recetas propias con contentVersion 2; no cambia snapshots v1 ya guardados. T-90–94 se desarrolla conforme a CALIBRACION_ACTUAL_CONTRATO.md, con objetivos explícitos y resumen descriptivo al cerrar, sin aprendizaje automático de límites fisiológicos.

## Validación

Pruebas dirigidas antes de build. Cola compartida sin matar procesos ajenos, máximo tres intentos por verificación de código. Gate `scripts/verify_post_rings_retirement.py` obligatorio después de recuperar cambios y antes del cierre. Validación JVM, Android, render, instalación y QA son evidencias separadas. La identidad de pruebas sigue siendo KPKN Dev (`com.example.kpkn.dbg`); no se actualiza un dispositivo con un esquema o firma incompatibles.

El arnés JVM actual excluye deliberadamente clases de Room/Android y del wizard: entre ellas ProgramAutoregulationResolutionTest y TrainingPlanRecipeJsonCompatTest. No modificar los scripts compartidos ni contar clases excluidas como ejecutadas. Los motores se verifican con sus clases JVM disponibles; las clases excluidas y la UI se agrupan en el gate Android de su ola, registrando ese pendiente hasta aprobarlo. El cierre de una ola requiere su evidencia por clase y los límites del arnés, no sólo el código de salida del comando.
