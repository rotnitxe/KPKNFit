# Sistema E — Escaneo de torre de pesos (weight stack scan)

**Fuente:** `wk_libapp.so` (Workouts 1.4.1). Digest: `re/digests/E_wk.txt` (33 clases, prefijo `ai_features/weight_stack_scan/*` + interfaces de `ai_features/`). El enum `AiFeature` de Nutrition también lista `weightStackScan` (OBSERVADO en `D_nu_skel.txt`), pero las clases del servicio sólo se han inventariado en Workouts.

## E.0 Clases (OBSERVADO)

| Clase | Papel |
|---|---|
| `WeightStackScanStreamService` (`weightStackScanStreamServiceProvider`, nullable) | servicio en *stream* (resultados progresivos) |
| `WeightStackScanService` (`weightStackScanServiceProvider`) | orquestación |
| `FirebaseWeightStackImageParser` (`weightStackScanParserProvider`; mixin `AiStageMixin`) | etapa que llama al modelo y parsea |
| `WeightStackScanInput` | entrada (imagen + contexto) |
| `WeightStackScanAiResult` → `WeightStackScanSuccessResult`, `NoWeightStackDetectedResult`, `WeightStackScanErrorResult`; `WeightStackScanDraft` | resultados |
| `PipelineImageData` → `FirebaseInlineImage`, `FirebaseRemoteImage`, `XFilePipelineImageData` | imagen |
| `AiExecutor` (`templatedGenerativeModel: TemplateGenerativeModel`), `AiStageDelegate`, `TemplateModelRequest`, `StageConfig`, `TemplateLLMModelParameters`, `LLMParameters` | infraestructura común de IA |
| `AiFeature.weightStackScan` ("ai_weight_stack_scan"), `aiUsageRepositoryProvider`, `aiUsageAggregateStreamProvider`, `OverUsageLimitAiFeatureResult`, `IntegrityCheckErrorAiFeatureResult` | uso y límites |

## E.1 Afirmaciones

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| El modelo devuelve una respuesta «compacta» en texto, no JSON. | INFERIDO (fuerte) | Cuatro `RegExp` estáticas: `_compactResponseComponentDelimiter` = `\s*\|\s*`, `_compactResponseUnitValueDelimiter` = `\s*[:=]\s*`, `_compactResponseListValueDelimiter` = `\s*[,;/]\s*`, `_compactResponseRangeValueDelimiter` = `\s*-\s*` (pool de sus inicializadores, `E_wk.txt` l.100-161). |
| Formato inferido: componentes separados por `\|`; cada componente `clave: valor` (o `=`); listas separadas por `, ; /`; rangos `a-b`; unidad de peso reconocida por `_parseCompactWeightUnitComponent(String) -> WeightUnit?`; números con `_tryCoerceDouble(String) -> double?`. | INFERIDO | Nombres de las funciones y de las regex; orden de aplicación no verificado. |
| Las imágenes no soportadas producen el error 'Unsupported weight stack image data: '. | OBSERVADO | Cadena en el pool (l.175). |
| El resultado se convierte en una configuración de pesos de máquina (lista de pesos o rango con incremento, más micro-ajustes). | INFERIDO | Las clases destino existen en `models/workout/gym/equipment_weight_config.dart` (`RangesEquipmentWeightConfig`, `WeightsEquipmentWeightConfig`, `EquipmentWeightRange`, `EquipmentMicroAdjustmentConfig`, `MicroAdjustmentType {dial, selectorizedPins, externalPlates}`); el enlace directo parser → modelo no se ve porque los métodos normales carecen de nombre. |
| El usuario puede revisar un borrador antes de guardar. | INFERIDO | `WeightStackScanDraft` como resultado intermedio. |
| El *prompt* y la plantilla viven en el servidor (Firebase AI «templated generative model»); el nombre del modelo no aparece junto a este servicio. | OBSERVADO (clase) / INFERIDO (ubicación) | `AiExecutor.templatedGenerativeModel`; no hay cadenas de *prompt* en el pool de estas clases. |
| La función está sujeta a límite de uso y a comprobación de integridad. | OBSERVADO | Resultados `OverUsageLimitAiFeatureResult`, `IntegrityCheckErrorAiFeatureResult` compartidos con el resto de `AiFeature`. |

## E.2 Lo que NO se pudo determinar

- Prompt, campos exactos y unidades admitidas (sólo se ve el gramático de delimitadores).
- Cómo se decide `NoWeightStackDetectedResult` (criterio del modelo o del parser).
