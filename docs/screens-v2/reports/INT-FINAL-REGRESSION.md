# Regresión final de la ola screens-v2

## Evidencia cerrada · 23-oct-2026 10:30

| Corrida | Fuente | Clases / pruebas | Resultado real |
|---|---|---:|---|
| int-screen-final-codex-20261023-1 | 9b106d664f86a64142b8baf1fe6ac0cc07c263ba | 399 / 3135 | 26 fallos, 0 errores, 0 omitidas, 0 clases ausentes; BUILD31min45s; wrapper exit1 |
| int-phase4-final-jvm-codex-20261023-5 | 8890750dbe109c8aa5a42bc4b898abb0e0e69de8 | 207 / 2062 | 0 fallos, errores u omitidas; BUILD6min56s; 673s con cola/resumen; wrapper exit0 |

Logs en `C:/kw/logs/` con nombre de corrida y extensión .log. Los XML nuevos se archivaron bajo `C:/kw/logs/screens-v2-evidence/<corrida>/`, con `summary.json`. Los207 XML puros tienen marcas 10:17:09–10:17:10, posteriores al inicio propio 10:09:14. El barrido Android generó381 PNG y no reutilizó XML/PNG de compilaciones anteriores; el ejecutor fija HEAD, manifiesto y herramientas por SHA, comprueba fuente limpia al principio y final, y exige todas las clases solicitadas.

El alcance Android incluye las clases concretas de workout/sessioneditor, tres runners con contratos heredados y dependencias seleccionadas de render/programdetail/training. No es toda la suite del repositorio. Un worker, reciclado cada15clases, failFast=false e ignoreFailures=false: todos los fallos se recogen y Gradle falla. No se añadieron exclusiones ni se subieron techos INV. ExerciseCatalogImageCoverageTest pasó sus dos casos; los tres manifiestos originales de imágenes recuperados de Git siguen sin diff.

## Hallazgos del barrido Android y estado de los arreglos

| Clase | Fallos | Causa / corrección preparada |
|---|---:|---|
| ProgramAutoregulationResolutionTest | 1 | Defecto: una propuesta de intensidad sin porcentajes rematerializaba una receta RIR. 262b30bd15 filtra sólo esa operación no aplicable; conserva volumen, auditoría y otros cambios. Integrado e incluido en JVM5 verde. |
| ExerciseInfoRenderSheetTest | 2 | Ruta de imagen antigua del render. 08de2ff3e actualiza el arnés, sin cambiar el producto. Pendiente de integrar u23 y repetir. |
| ImportWarningsRenderTest | 2 | El render buscaba acciones dentro de un panel plegado. 08de2ff3e recorre la UI actual. Pendiente de integrar u23 y repetir. |
| Cal7FreeSessionRoomTest | 1 | Timeout RINGS del segundo recorrido. 3d503ab6d añade diagnóstico sin ampliar timeout; 94294ac34 reinicia AUGE antes de cada fixture que recrea Room. Causa inferida: DAO conservado de la base cerrada; pendiente de confirmación en runtime. Ambos integrados. |
| Cal7ManagedProgramRoomJourneyTest | 6 | Peso muerto esperaba203 en vez de202,5 con incremento2,5. f65fc5d7f corrige la expectativa; mantiene OFF/PROPONER/AUTO de531 y GZCLP. Pendiente de integrar u23 y repetir. |
| Cal7ManualApreRoomJourneyTest | 4 | Snapshot anterior a la normalización canónica persistida; la aplicación y carga ya pasaban. b0e7514a9 obtiene la precondición durable, sin relajar igualdad ni IDs. Pendiente de integrar u23 y repetir. |
| LiveSetModesUiTest | 2 | Copia TM vigente con espacio no separable y porcentaje explícito. e2223b649 conserva los valores numéricos y actualiza texto esperado. Integrado; pendiente de repetir. |
| LiveFichaHeadingRegressionTest | 5 | Fixture sin portada real y descripción plegada impedía alcanzar cabecera/cruce. b41af8019,96a251007,1390eb054 usan ficha real y fuerzan cruce visible, manteniendo aserciones de separación y visibilidad. Pendiente de integrar u23 y repetir. |
| WorkoutCalibrationVariantTest | 1 | Nueva regresión S3 esperaba150;145×1,025=148,625 y redondeo2,5 da147,5. e2223b649 corrige sólo expectativa. Otros casos de variantes B/C/D pasaron. Integrado; pendiente de repetir. |
| WorkoutReplacementPlanSummaryTest | 2 | Espacio no separable RIR correcto en producto. e2223b649 alinea las dos expectativas sin cambiar dosis/descansos. Integrado; pendiente de repetir. |

Total26 fallos. No se atribuyen los arreglos posteriores a la fuente del barrido ni se presenta como verde una repetición todavía no ejecutada. Selección preparada de29clases: las diez fallidas más las afectadas por RM, H3, APRE, calibración, catálogo y referencias. Se ampliará con la regresión específica del nuevo defecto G1 antes de congelar el próximo HEAD.

## QA real y APK

El emulador ejecuta APK.dbg de fuente0c642bff1, SHA256 `170a26fabb3930af3e5d06fbfd26b564c33c8dc1ca5ee3473eda4c7f8ab77f14`. F1 confirmó solape al desplazar; a11a83624 corrige reserva de cabecera y está integrado. La etiqueta RM se truncaba a740/130;18b15ec96 usa dos líneas completas sin reducir fuente ni objetivo táctil, aún por integrar desde u23. H3 encontró una búsqueda sin resultados; f5957e3d4/e42aa81b0 añaden estado vacío y regresión con una movilidad real, sin cambiar altura arbitrariamente. Estos arreglos requieren APK nuevo y repetición de sus tableros.

G1 detectó una pérdida real: el editor confirmaba CUSTOM/pasos o Reps, pero el cockpit conservaba Tiempo45s; también se perdió una dosis S2 cambiada con botón, descartando que sólo fuese IME. Se investiga/corrige en u23 y no se da por resuelto. G2 sí conservó intervalos3rondas/60min después de confirmar y reabrir.

CAL3 nativo probó registrar→proponer→reiniciar→recuperar→aplicar→revertir→corregir registro→conservar, con contador estable. Faltan protecciones/alcances, cierre/historia/siguiente sesión y el resto de la matriz. No se activó voz ni se acreditó dictado/audio.

Teléfono: se instaló fuente d24370583/version51, SHA256 `7dd56f54fbb9a21948e91cf0291788a842c69f283d1eb6e5611e949ace8a81c7`, certificado AndroidDebug. Captura verificó1080×2640/480dpi/letra1.0; no acredita los recorridos G1/G2/TM/LQA1. Otra QA de técnica de vídeo usó la misma .dbg y se preservó su sesión. Operar sólo mediante phone_run.py y sus guardas/candado/20min.

Roomv30 congelado. Ningún push, integración master ni firma de distribución. Un APK debug no es el release; el release unsigned y su verificación siguen pendientes.
