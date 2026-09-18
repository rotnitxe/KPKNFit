---
flags: []
---
# Cierre: escalado de plantillas al MAV calibrado

## Rutas
- android-native/app/src/main/java/com/example/kpkn/domain/training/TemplateVolumeScaler.kt
- android-native/app/src/test/java/com/example/kpkn/domain/training/TemplateVolumeScaleAuditTest.kt
- Integraciones WIP: ProgramTemplateEngine, ProgramProtocolEngine, SplitApplicationEngine, SessionEditorViewModelTemplates (corregir solo desvíos confirmados).
- Evidencia en .opencode/plans/2026-09-17_volumen-plantillas-cierre-resultados.md.
- No tocar catálogo, datasets, AUGE, Room, iOS/backend ni WIP ajeno. Sin instalación, reset ni commit.

## Impacto
- Diagnóstico estático: déficit directo vs residuo total; máximos globales reemplazan calibración y cuotas; techo elevado al volumen de entrada; chequeo H7 solo del músculo solicitado.
- Objetivo y seguridad en total canónico, misma resolución que VolumeCalculator. Primarios determinan candidatos, no convierten MAV total en MAV directo. No sumar recomendaciones alias del mismo grupo canónico.
- Objetivo efectivo min(MAV calibrado, MRV personalizado/global). Hasta 2 series equivalentes semanales de tolerancia colateral sobre objetivo, nunca por encima de MRV. Músculos ya excedidos al entrar no crecen. No redondear hacia arriba un techo existente.
- Solo clonar sets efectivos existentes con IDs nuevos. H6 30 / H7 12 para TODOS los primarios afectados / 100 minutos intactos. Reportar déficit real cuando no cabe, sin prometer MEV universal.
- Sesión suelta: cuota dividida por frecuencia y tolerancia dividida también; sin reposición del MAV global. Segunda pasada no añade volumen.

## Pruebas
- Localizado wrapper real: desde android-native ejecutar powershell -NoProfile -File ../.opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.TemplateVolumeScaleAuditTest' --tests '*.VolumeCalculatorTest' --tests '*.ProgramHeroWidgetsCalibrationTest' --tests '*.ProgramsViewModelTest' --tests '*.SessionTemplateCatalogTest' --tests '*.ProtocolRecipeFidelityTest'" -TimeoutSec 600.
- Las seis clases existen. Una corrida agrupada tras cambios, proceso con logs y polling acotado si tarda. No declarar BUILD SUCCESSFUL sin evidencia.
- Regresiones sintéticas: total vs directo; indirecto tolerado acotado; exceso inicial congelado; MRV; H6/H7 multiprimario/tiempo; cuota; IDs/prescripción; idempotencia.
- Auditoría real ppl_x6, ul_x6, fullbody_x5: total/directo antes/después, MAV/MRV, residuo y sets/sesión. No saltar fixtures ausentes ni cambiar roles del catálogo para poner verde.

## Riesgos
- Sin añadir ejercicios, puede ser imposible alcanzar MAV/MEV; conservar residuo explícito.
- El catálogo real puede declarar glúteos PRIMARY en una sentadilla concreta: respetar ese dato, informar, no sustituirlo por hipótesis biomecánica.
- Aliases y deltoides colapsan al grupo canónico: máximo, no suma de targets. No cambiar cálculo/calibración general fuera del scaler.
- Protocolos conservan limitaciones de receta y semanas densas preexistentes; no recortar volumen base ni relajar políticas.
- Editor aislado no conoce presupuesto restante completo de la semana; cuota por frecuencia no garantiza MAV semanal exacto.
- Paridad iOS/backend y QA dispositivo no incluidos en este cierre.

## Fase B UI (constructor screens)
- Rutas absolutas bajo C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\java\com\example\kpkn\screens\{programs,home,programdetail,sessioneditor} y sus tests en app\src\test\java\com\example\kpkn\screens.
- Impacto: gates antes de materialización/persistencia, sheet reusable con omisión explícita sin escalar, invitación real en editor y frecuencia split (default 2).
- Pruebas: revisión estática y tests focalizados; este constructor NO ejecuta Gradle, validación reservada al principal.
- Riesgos: preservar WIP y firmas domain; cancelar no debe materializar, omitir no debe fabricar calibración. Sin cambios domain/training, catálogo ni auditoría.

