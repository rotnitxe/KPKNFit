# IA local para nutricion

El registro descriptivo ya no depende de Ollama ni del backend para nutricion.

Arquitectura actual:

1. `RegisterFoodDrawer` deja escribir sin lag.
2. El usuario pulsa `Analizar calorias`.
3. `parseFreeFormNutrition()` usa parser por reglas + bridge Android local.
4. `LocalAiPlugin` intenta cargar `kpkn-food-fg270m-v1` desde:
   - asset pack install-time (`android/kpknLocalAiPack/...`)
   - assets debug/sideload (`android/app/src/main/assets/models/...`)
5. Si el modelo real no esta presente, el flujo sigue con fallback heuristico offline.

Documentacion completa:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\docs\local-ai-functiongemma-android.md`

Comandos clave:

```bash
npm run local-ai:stage-model -- --src "C:\ruta\al\export-del-modelo" --clean
npm run local-ai:check-model
npm run cap:sync
cd android && .\gradlew.bat :app:bundleDebug
```

Telemetria de analisis:

- Se guarda localmente en `nutrition-ai-telemetry-runs`
- Correcciones manuales en `nutrition-ai-telemetry-corrections`
- En runtime de desarrollo puedes inspeccionarla con:

```js
await window.__kpknNutritionAiTelemetry.summary()
await window.__kpknNutritionAiTelemetry.runs()
await window.__kpknNutritionAiTelemetry.corrections()
```
