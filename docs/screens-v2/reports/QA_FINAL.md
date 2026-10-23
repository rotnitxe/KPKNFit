# QA final de screens-v2

Fecha de preparación: 23-oct-2026. Estado: **pendiente de ejecución en dispositivo**. Esta tabla inventaría los tableros aprobados; todavía no acredita instalación, arranque, interacción ni revisión visual en el APK integrado. Las pruebas JVM/Room/Compose se documentan en INT-4 y se distinguen de esta QA.

## Procedencia y límites

Se completará el SHA de fuente, variante, SHA-256 del APK, serial, tamaño, densidad, escala de fuente y actividad top-resumed observados. QA exclusivamente en `com.example.kpkn.dbg`, emulador ZFlip5_QA puerto5580 y teléfono mediante phone_run.py con regla20min. Roomv30 congelado. Sin push, master ni firma de distribución.

## APK preparado y arranque de entorno

23-oct08:43: BUILD6min6s y wrapperexit0 sobre0c642bff1. APK C:/kw/apk/20261023/screens-v2-qa-0c642bff1-base-debug.apk;485534900bytes; SHA256170a26fabb3930af3e5d06fbfd26b564c33c8dc1ca5ee3473eda4c7f8ab77f14. Paquete.dbg y certificado AndroidDebug verificados mediante aapt2/apksigner. Metadatos en screens-v2-qa-0c642bff1-evidence.json de la misma carpeta. Aún sin acreditar instalación ni flujos: el emulador dedicado5580 acaba de iniciarse y el teléfono se comprobará exclusivamente mediante phone_run.py/candado/20min. Los cambios posteriores de compilaciónCAL7 afectan solo tests.

## Tableros aprobados

La copia local contiene 70 pantallas HTML. canvas.json enumera 71 entradas: la restante es Piece-433s.dc.html, pieza sin marco titulada Div (389,997 × 119,992), sin HTML local; no se inventa una pantalla adicional ni una aprobación visual de esa pieza. La evidencia de dispositivo se enlazará por tablero y se anotarán defectos y repetición. Pendiente significa que aún no se ha comprobado; no se equipara un render Robolectric a una captura del APK.

| Tablero | Resultado en dispositivo | Captura y observación |
|---|---|---|
| A2-Vacia | Pendiente | — |
| A2b-SoloCardio | Pendiente | — |
| A3-Arrastrar | Pendiente | — |
| A4-BorrarDeshacer | Pendiente | — |
| A5-Semana | Pendiente | — |
| A6-Estados | Pendiente | — |
| B1-EjercicioAbierto | Pendiente | — |
| B2-FocoSerie | Pendiente | — |
| B3-FocoRM | Pendiente | — |
| B3b-FocoAMRAP | Pendiente | — |
| B3c-FocoSoloRPE | Pendiente | — |
| B3d-FocoTiempo | Pendiente | — |
| B3e-FocoDistancia | Pendiente | — |
| B3f-FocoPersonalizado | Pendiente | — |
| B4-Modos | Pendiente | — |
| B5-Unilateral | Pendiente | — |
| B6-Preparacion | Pendiente | — |
| B7-Descanso | Pendiente | — |
| B8-OpcionesEjercicio | Pendiente | — |
| B8b-SerieVivoROM | Pendiente | — |
| B9-Dialogos | Pendiente | — |
| C1-Superserie | Pendiente | — |
| C2-CrearSuperserie | Pendiente | — |
| C3-Rondas | Pendiente | — |
| C4-CardioContinuo | Pendiente | — |
| C5-CardioSeries | Pendiente | — |
| C6-CardioIntervalos | Pendiente | — |
| C7-SelectorCardio | Pendiente | — |
| D1-AccionesRapidas | Pendiente | — |
| D2-Impacto | Pendiente | — |
| D3-Sugerencias | Pendiente | — |
| D4-Plantillas | Pendiente | — |
| D5-Reglas | Pendiente | — |
| D6-Tiempo | Pendiente | — |
| D7-Versiones | Pendiente | — |
| D8-Transferir | Pendiente | — |
| D9-Movilidad | Pendiente | — |
| E1-Anadir | Pendiente | — |
| E2-Busqueda | Pendiente | — |
| E3-Seleccion | Pendiente | — |
| E4-SuperserieCatalogo | Pendiente | — |
| E5-Reemplazar | Pendiente | — |
| E6-SinResultados | Pendiente | — |
| E7-CrearEjercicio | Pendiente | — |
| E8-CargandoError | Pendiente | — |
| E9-ElegirTecnica | Pendiente | — |
| F1-FichaCatalogo | Pendiente | — |
| F2-FichaSesion | Pendiente | — |
| G1-EditarSeriesVivo | Pendiente | — |
| G2-EditarCardioVivo | Pendiente | — |
| G3-Importador | Pendiente | — |
| G4-WizardCambiar | Pendiente | — |
| H1-AproxEditor | Pendiente | — |
| H2-MovilidadEditor | Pendiente | — |
| H3-CatalogoMovilidad | Pendiente | — |
| H4-MovilidadDosis | Pendiente | — |
| H5-AproxVivo | Pendiente | — |
| H6-MovilidadVivo | Pendiente | — |
| Main | Pendiente | — |
| V1-PropuestaLinea | Pendiente | — |
| V2-IdeaUsuario | Pendiente | — |
| V3-PantallaCorta | Pendiente | — |
| V4-RutaSesion | Pendiente | — |
| V5-MigracionCockpit | Pendiente | — |
| V6-VivoRM | Pendiente | — |
| V7-VivoAMRAP | Pendiente | — |
| V8-VivoSoloRPE | Pendiente | — |
| V9-VivoTiempo | Pendiente | — |
| V10-VivoDistancia | Pendiente | — |
| V11-VivoPersonalizado | Pendiente | — |

## Recorridos transversales pendientes

- Vivo: modos RM, AMRAP/Calibrador, solo RPE, tiempo, distancia y personalizado; preparar y editar con teclado.
- H5/H6 y marco312×425 en alturas880/740, fuentes100/130; fichas85×76 y acciones48dp.
- CAL3: pasos2–10, incluyendo aplicar/revertir, guardado, reapertura y siguiente sesión; sin contaminar ejercicios contiguos.
- APRE: S3→S4 y XRM futuro por OFF/PROPONER/AUTO, incremento real del material, sin sustituir S3 por Calibrador.
- TM: preparación pide sólo el ausente; confirmar/cancelar, motivo e historial; edición y semanas futuras sin alterar series hechas o fijadas.
- LQA1: cruce del encabezado, descanso, margen inferior, voz130 y teclado; repetir también en teléfono si está disponible según su regla de uso.
- G4 se abre en el asistente de alta, antes de activar y sembrar QA; no en el detalle de un programa ya creado.
- Los errores A6/E8 deben distinguir evidencia de inyección Room/Compose de una reproducción real en dispositivo; no se inventarán fallos espontáneos.

## Resultado final

Pendiente. Todavía no se ha compilado, instalado ni ejecutado el APK de esta continuación.
