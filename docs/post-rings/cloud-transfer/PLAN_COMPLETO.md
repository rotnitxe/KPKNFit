# POST RINGS — Plan completo aprobado, con retiro del wizard

Fecha de autorización: 24 de octubre de 2026. Idioma: español.

## Mandato vigente y ejecución

El usuario ordena: «Ok, integra esto al plan completo y ejecútalo de forma autónoma en la nube: elimina AUGE/RINGS y su bloque del wizard, conserva los registros útiles y reutiliza el historial para el nuevo seguimiento».

Este mandato sustituye al objetivo previo de completar Rings v4. NO continuar afinando sus baterías, EKF, constantes, simulaciones o tarjetas de precisión. No importar ni fusionar el bundle viejo de Rings v4 para este trabajo. Preservar su estado recuperable, sin incorporarlo al producto nuevo.

Ejecutar la implementación completa y la validación posible en Codex Cloud. Resolver decisiones rutinarias autónomamente, documentarlas y continuar hasta completar el alcance; no terminar después de crear un plan, una fachada o una primera fase. Conservar commits y resultados PRIVADOS recuperables. No hacer push ni PR al repositorio público rotnitxe/KPKNFit. No fusionar master ni publicar APK firmado. No usar compilaciones ni recursos del PC local. No contactar otros chats sin autorización humana específica para ese destinatario; la autorización presente cubre este chat cloud de ejecución, no chats de VT/MFP/programas.

La base pública observada es master edc237f66d8474c3a9922296b61940e7030b7f8a. Verificar HEAD remoto y estado antes de editar. El paquete adjunto aporta copias explícitas del WIP multideporte y PerformanceLab que todavía no están en esa base. Son trabajo sin validar, no una aprobación de integración ni evidencia de compilación. El manifiesto permite verificar cada archivo. Evitar incluir cambios concurrentes ajenos del árbol local.

## 1. Decisiones cerradas

- Retirar definitivamente el modelo AUGE/Rings en Android, iOS y backend. No esconderlo tras nuevos nombres, flags, procesos de fondo o un laboratorio activo.
- Cobertura: todas las disciplinas registradas, con conclusiones específicas y límites por deporte; no un índice global de recuperación.
- Purgar resultados derivados y conservar datos originales útiles, su precisión, escala, procedencia y contexto.
- Reutilizar Session, WorkoutLog, programas, calendario, historial, resultados deportivos y progresión existentes. No crear una segunda actividad o historial paralelo.
- Registro adicional breve y opcional. Sugerencias con confirmación, sin cambios silenciosos ni nuevos prescriptores clínicos.
- Producto local/offline. No nuevos sensores obligatorios, nueva IA predictiva de lesiones ni nueva biblioteca de rehabilitación/prevención en esta entrega.
- No afirmar que toda aproximación matemática a la fatiga es seudociencia: la decisión de retiro se fundamenta en que nuestras afirmaciones y fórmulas no están validadas y exceden el soporte disponible.

## 2. Evidencia y dossier reproducible

Guardar la revisión en docs/research/training-evidence/, incluyendo estrategias de búsqueda, fecha de corte 2026-10-24, DOI/PMID, diseño, población, intervención/comparador, desenlace, tamaño de efecto e IC cuando se haya verificado, sesgos, aplicabilidad y funciones permitidas. Distinguir texto completo de resumen. No llamar a esta búsqueda una revisión sistemática publicable con dos revisores independientes ni asignar GRADE/AMSTAR formal sin evaluación suficiente. No inferir certeza por el título «metaanálisis». AMSTAR 2 no es una puntuación global: https://www.bmj.com/content/358/bmj.j4008.

Fuentes fundamentales ya consultadas; verificar los detalles al cerrar el dossier, sin inventar resultados:

| Fuente | Hallazgo y límite para producto |
|---|---|
| Hickmott 2022, 15 estudios, DOI 10.1186/s40798-021-00404-9; https://link.springer.com/article/10.1186/s40798-021-00404-9 | Autorregulación de carga frente a prescripción estandarizada: diferencia 1RM 2,07 kg (IC95 % -0,32 a 4,46), sin superioridad significativa. Pérdida de velocidad y objetivos de fuerza/hipertrofia requieren contexto. |
| Huang 2025, 19 publicaciones/428 participantes, DOI 10.1016/j.jesf.2025.07.006; https://pmc.ncbi.nlm.nih.gov/articles/PMC12336695/ | Resultados favorables a métodos autorregulados, sobre todo APRE; red pequeña, comparaciones indirectas, sentadilla y banca. SUCRA no es probabilidad individual de éxito ni valida recuperación o prevención. |
| Halperin 2022, PMID 34542869; https://pubmed.ncbi.nlm.nih.gov/34542869/ | Metaanálisis exploratorio de 12 estudios/414 participantes: error de predicción de repeticiones cercano a una repetición, I2 97,9 %. RIR es autoinforme imperfecto. |
| Liu 2023, DOI 10.1186/s13102-023-00712-5; https://link.springer.com/article/10.1186/s13102-023-00712-5 | 16 estudios/278 adolescentes, asociación sRPE con FC r≈0,74; mayoría varones. Validez de carga percibida, no recuperación ni lesión. |
| Haddad 2017, PMID 29163016; https://pubmed.ncbi.nlm.nih.gov/29163016/ | Revisión de validez/utilidad sRPE en distintas edades y deportes; complemento contextual, no metaanálisis de recuperación. |
| Saw 2016, PMID 26423706; https://pubmed.ncbi.nlm.nih.gov/26423706/ | 56 estudios: bienestar subjetivo sensible a cambios; asociación inconsistente con medidas objetivas. Respaldo a instrumentos establecidos, no validación automática de sliders propios. |
| Chalchat 2022, DOI 10.1371/journal.pone.0271233; https://pmc.ncbi.nlm.nih.gov/articles/PMC9282447/ | 141 estudios/3089 participantes: marcadores de daño y recuperación varían con momento, ejercicio y población. Predomina miembro inferior y varones. No batería universal ni reloj por músculo. |
| Claudino 2017, PMID 27663764; https://pubmed.ncbi.nlm.nih.gov/27663764/ | 151 artículos: CMJ medio más sensible que el mejor para ciertos cambios. Requiere protocolo e instrumento comparables; no estimar mediciones ausentes. |
| Düking 2021, PMID 34489178; https://pubmed.ncbi.nlm.nih.gov/34489178/ y Manresa-Rocamora 2021, PMID 34639599; https://pubmed.ncbi.nlm.nih.gov/34639599/ | HRV guiada: posibles beneficios limitados, sin superioridad clara en rendimiento agrupado. No medidor general de recuperación multideporte. |
| Qin 2025, DOI 10.1186/s13102-025-01332-x; https://link.springer.com/article/10.1186/s13102-025-01332-x | 22 cohortes por categorías ACWR: agrupar incidencias no establece causalidad, seguridad ni probabilidades personales. Eliminar semáforos de riesgo basados en ratios. |
| Zhang 2026, DOI 10.1186/s12891-026-10503-x; https://link.springer.com/article/10.1186/s12891-026-10503-x | 11 estudios/508 atletas; certeza GRADE muy baja según autores. No umbrales fiables de prevención por carga inercial. |
| Leckey 2025, PMID 39613453; https://pubmed.ncbi.nlm.nih.gov/39613453/ y Lou 2026, PMID 42342948; https://pubmed.ncbi.nlm.nih.gov/42342948/ | Revisión de 38 estudios y metaanálisis de diez modelos: predicción prometedora con problemas de utilidad/generalización. No trasladar rendimientos de otros modelos a KPKN. |
| Lauersen 2018, PMID 30131332, DOI 10.1136/bjsports-2018-099078; https://ostrc.no/globalassets/publications/lauersen_2018_bjsm_strength-training-as-a-superior-dose-dependent-and-safe-prevention-of-sports-injuries.pdf | Seis ensayos/7738 participantes, RR 0,338 (0,238-0,480) en intervenciones de fuerza específicas. No garantiza protección personal ni valida anillos. |
| Wu 2024, DOI 10.1007/s40279-024-01993-7; https://link.springer.com/article/10.1007/s40279-024-01993-7 | Nueve estudios/1904 corredores, resultado preventivo global no significativo; siete estudios de baja calidad y subgrupo supervisado post hoc. No extrapolar prevención entre deportes. |
| Schumann 2022, PMID 34757594; https://pubmed.ncbi.nlm.nih.gov/34757594/ | 43 estudios concurrentes: sin perjuicio global claro fuerza máxima/hipertrofia; posible atenuación fuerza explosiva, especialmente misma sesión. Contexto de objetivos, no penalización universal. |
| Craven 2022, DOI 10.1007/s40279-022-01706-y; https://link.springer.com/article/10.1007/s40279-022-01706-y | Pérdida de sueño puede perjudicar rendimiento con heterogeneidad por tarea/condición. Mostrar sueño registrado; no convertir horas en puntos de batería. |
| Frandsen 2025, PMID 40623829; https://pubmed.ncbi.nlm.nih.gov/40623829/ | Cohorte prospectiva de 5205 corredores: asociación de aumentos de distancia de sesión respecto al máximo de 30 días con sobreuso. No ensayo preventivo, ni regla de 10 % seguro. Comparación descriptiva permitida sin riesgo personal calculado. |

## 3. Producto: Entrenamiento y respuesta

No crear otra pantalla central de preparación corporal. Integrar información en Home, antes/durante/después de sesión e historial. Gráficos interactivos de datos y unidades reales, con periodo, disciplina, origen y detalle de cada punto.

- Home: próxima sesión, actividad reciente por disciplina y acceso a comparaciones pertinentes.
- Antes: sesiones comparables, objetivos del programa y molestias registradas. Opcionales: cansancio actual y molestias que afecten al entrenamiento.
- Durante: referencia equivalente y contraste objetivo/ejecución (p. ej. RIR previsto frente a declarado).
- Cierre: resultados, desviaciones, molestias y esfuerzo global opcional de la sesión.
- Historial: carga, rendimiento y sensaciones; periodos 1/4/12 semanas, defecto 4.

Familias: fuerza/carga-repeticiones-RIR; resistencia/duración-distancia-desnivel-ritmo-potencia-FC cuando existan; funcional/bloques-rondas-tiempos; combate/equipo/raqueta/exposición-tipo-esfuerzo-molestias; sprint/salto/marcas con protocolo y método comparables. No extrapolar modelos de una disciplina a otra. Reutilizar todos los SportId soportados y mantener un fallback descriptivo honesto cuando no haya métricas especializadas.

Reglas: distinguir observación, autoinforme, cálculo descriptivo y estimación. Sin registro no es descanso ni fatiga cero. No inferir RPE de sesión del RPE de series, fatigueLevel o antiguas baterías. Índice esfuerzo×duración solo con datos explícitos y protocolo identificado, en unidades arbitrarias, sin fisiología o riesgo. Escalas propias no se llaman instrumentos validados ni se copian instrumentos sujetos a permisos sin verificarlos. Conservar n, cobertura y contexto; no convertir dispersión en intervalo de precisión fisiológica.

Comparabilidad: deporte, ejercicio/variante/perfil, unidades, método y protocolo; contexto desconocido no equivale a idéntico. Referencias por defecto: seis observaciones anteriores comparables dentro de 90 días; desde tres, resumen descriptivo. Son opciones de presentación, no umbrales clínicos. Reloj explícito y ninguna lectura futura. Valores de duración conservan precisión original; no fabricar segundos desde minutos redondeados. No doble conteo de actividades, documentos o energía.

Sugerencias numéricas solo desde reglas explícitas de programas/protocolos activos con requisitos satisfechos, reutilizando propuestas y auditorías APRE/TM/calibración válidas. Confirmar aplicación, registrar razón/datos/cambio. Fuera de ellos, comparaciones y opciones para revisar, sin reducción porcentual inventada. No cambiar silenciosamente calendario, pesos, volumen o programa. No índice de lesión, batería, tiempo hasta recuperado, supercompensación, HRV sintética o IA predictiva nueva.

## 4. Wizard y bienvenida — integración obligatoria

Eliminar COMPLETAMENTE el bloque RINGS como etapa, no renombrarlo «recuperación» ni sustituirlo por otra calibración obligatoria.

Nuevo recorrido: Perfil → Entrenamiento → Nutrición opcional → Revisión y activación. Preservar modos parciales vigentes de entrenamiento/nutrición, su progreso y protocolos.

- Eliminar introducción, reconstrucción detallada de días/ejercicios/series/intensidad para inicializar recuperación, preview, ajustes 0-100, resultado y hito de RINGS.
- Experiencia/hábitos/ejercicios solo van a Entrenamiento si sirven para elegir programa y no se preguntan ya. Los patrones principales recordados NO son marcas medidas ni tests de fuerza.
- Molestias como declaración opcional en Entrenamiento usando DiscomfortCatalog canónico. Cansancio/energía de hoy al inicio opcional de una sesión, no característica permanente del perfil.
- No pedir reconstruir una semana para alimentar una inferencia; el historial importado/registrado sigue siendo fuente, sin fabricar sesiones por respuestas del wizard.
- Retirar RINGS_ONLY, rutas específicas, branches, validaciones, errores, reintentos, jobs y snapshots. La activación no llama a AugeRepository ni necesita calibración, preview o número mínimo de observaciones.
- Migrar borradores y scopes antiguos: preservar Perfil/Entrenamiento/Nutrición y respuestas originales útiles; cursor al primer paso vigente pendiente o Revisión. Progreso y total de pasos coherentes; no reiniciar el wizard, no bloquear con campos retirados, no duplicar commit/activación. Mantener setup_drafts y setup_commit_receipts, no tablas paralelas.
- Eliminar la escena WelcomeRingsScene que baja/sube baterías y promete «Hoy puedes entrenar fuerte». Reutilizar su espacio con una comparación ilustrativa de sesiones/resultados con unidades, marcada como Ejemplo. Ninguna predicción ni permiso de seguridad. No alargar el onboarding para llenar el hueco.
- Antiguos deep links exclusivos se resuelven hacia la sección vigente de entrenamiento/historial o resumen pertinente, sin reactivar lógica retirada.

## 5. Datos, arquitectura y retiro global

Mantener Clean Architecture/MVVM, StateFlow de lectura, inyección manual, domain sin android.*, reloj explícito y trabajo bloqueante en IO. Nuevo TrainingInsightsEngine puro y modelos de hechos/comparaciones/cobertura/explicaciones. Reusar estructuras de historial y propuestas; no crear modelos deportivos paralelos.

Room observado v30 → v32 (versiones nuevas libres y reserva ≥32); comprobar en base efectiva antes de fijar identidad. JSON backup v6 → v7. Si la base hubiera avanzado, portar la migración como versión nueva sin sobrescribir otra; documentar el ajuste de numeración por concurrencia, nunca hacer downgrade.

Crear almacenamiento neutral de observaciones solo para fuentes originales que hoy están ligadas a AUGE (bienestar, sueño, molestias, feedback y contexto). Preservar identidades, logId/sessionId, fechas, escala, contexto, notas y precisión; deduplicar por identidad de fuente, no por valores. Separar defaults de respuestas explícitas. Respuestas antiguas ambiguas quedan como declaración histórica con procedencia/escala original, sin convertir «SNC» a medición neurológica ni equipararlas automáticamente a escalas nuevas.

Antes de purgar cachés, revisar si contienen alguna declaración original única y preservarla como tal, sin rescatar resultados inferidos. Eliminar baterías calculadas, drenajes, tau/perfiles, aprendizaje, anclas calculadas, snapshots de anillos, acciones pendientes exclusivamente AUGE y cuestionarios pendientes retirados. Sanear Settings, WorkoutLog/CompletedSet, sesión en curso, borradores y backup JSON por rutas conocidas. No borrar notas, sesiones, medios, nutrición, marcas, programas o propuestas de protocolos independientes.

Migración transaccional sin destructive fallback. Si no puede preservar originales, abortar y ofrecer recuperación/exportación; no marcar purga exitosa. Importador aislado de respaldos v1-v6 y snapshots de DB viejas extrae originales, descarta derivados y nunca reactiva AUGE. Exportaciones nuevas sin resultados AUGE. No borrar ni reescribir backups externos del usuario.

Retirar código/ejecución/UI/controles/ajustes/widgets/notificaciones/voz/onboarding/prompts/schedulers, referencias en IA, etiquetas, catálogos y cálculos. Extraer solo utilidades generales válidas (fechas, normalización, datos descriptivos), no fórmulas disfrazadas. Retirar coeficientes dedicados del catálogo usando scripts documentados; no regenerar a mano assets grandes ni pisar curación concurrente. No borrar ejercicios de anillas por coincidir con la palabra inglesa rings.

Android e iOS: retirar motor y persistencia derivada, paridad de hechos/contratos/contenido. Backend: retirar motores AUGE/fatiga/recuperación/adaptive y semáforos ACWR. Endpoints antiguos → 410 Gone sin cálculos; conservar volumen/historial independientes y limpiar /health/OpenAPI/promesas. No añadir un backend obligatorio para consultar seguimiento.

«Sin rastros» = fuera del producto activo y sus resultados derivados. Únicas excepciones: migradores/importadores aislados, rutas 410 y esquemas históricos necesarios. No reescribir Git ni destruir WIP recuperable o backups externos. Actualizar README, ayuda, arquitectura, mapas, atribuciones y documentación científica.

## 6. Ejecución y aceptación

1. Guardar este plan y dossier reproducible en la rama privada. Revisar manifest y aplicar WIP seleccionado en base limpia, con resolución conservadora. No trasladar parches de nutrición/catálogo ajenos.
2. Preservación y migraciones con fixtures sintéticos; capa neutral y pruebas puras.
3. Integrar hechos/contexto/confirmación en sesión, cierre, historial y Home.
4. Eliminar wizard RINGS y bienvenida antigua con migración de borradores y activación.
5. Retiro completo Android/iOS/backend y saneamiento de restauraciones/datos.
6. Verificación integrada, docs y resultados privados recuperables. Cada fase tiene commits y evidencia; no concluir tras la primera.

Pruebas obligatorias:
- DB poblada v28/v29/v30 → nueva versión y backup JSON v1-v6 → v7; conservar IDs/valores no derivados/notas/medios/unidades/fechas y eliminar realmente derivados/tablas AUGE activas.
- Backup viejo no reactiva AUGE; importación repetida/idempotencia, operación transaccional, cancelación/fallo y fuentes ambiguas.
- Sesión en curso, borrador antiguo situado en cada paso eliminado, modos RINGS_ONLY/full/resume/training-only/nutrition-only; cursor/progreso correctos y activación única sin preview AUGE.
- Omisiones/defaults/desconocidos, cero real frente a ausencia, datos incompatibles, escalas históricas y todos los SportId; sin sensores no se inventan mediciones.
- Sin lecturas futuras, doble conteo, reescritura silenciosa ni recomendaciones sin datos/confirmación. Reglas de progresión/APRE y cierre/voz se conservan con pruebas focalizadas.
- Escaneo semántico de imports, llamadas, schedulers y modelos: motor retirado no se ejecuta ni revive por rutas profundas/restauración. Allowlist acotada de compatibilidad/esquemas, no comodín de exclusión.
- Backend con 410 y cálculos independientes correctos; fixtures compartidos entre plataformas. iOS en macOS: si no hay host, registrar límite exacto, continuar todo lo ejecutable, no fingir build/QA.
- Android: tests focalizados y regresión pertinente + assembleBaseDebug, desde android-native con --no-daemon --console=plain. Verificar XML con recuento >0 y exit code; no confundir arnés puro con Android/Compose/Room.
- QA de pantallas sin datos/con datos, texto grande, accesibilidad, offline, cambios de configuración/reinicio y escenarios multideporte. Usar capturas donde la infraestructura permita.

Z Flip: solo app personal com.example.kpkn y UNA KPKN Dev con identidad com.example.kpkn.dbg; no paquetes/flavors adicionales. baseDebug, misma identidad/firma incluso R8. Antes de -r verificar certificado y compatibilidad Room/datos. Nada de borrar datos/desinstalar desconocidos/tocar app personal. Acceso físico solo phone_run + phone.lock, un dueño, guardas intactas. En cloud sin dispositivo: dejar QA físico pendiente con pasos y artefactos exactos, no reconectar el PC ni crear validación ficticia.

Seguridad: no leer/imprimir/commitear .env, credenciales, keystores o tokens; no ejecutar telegramBot.js; no publicar WIP en GitHub público. No reescribir trabajo concurrente, hacer clean/reset/stash o reemplazar master. Conservar bundle/parche privado de la rama y manifiesto de resultados.

Cierre: retiro global comprobado, originales preservados, seguimiento usable en historial/sesión, wizard corto sin RINGS y evidencia de validación honesta. No prometer que KPKN mide recuperación fisiológica o previene lesiones por disponer de estos datos. No queda predictor experimental en segundo plano.
