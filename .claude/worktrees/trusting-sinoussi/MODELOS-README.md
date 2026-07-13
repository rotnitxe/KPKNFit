# Modelos locales KPKN

Para nutricion descriptiva en Android usamos `FunctionGemma 270M` exportado como:

- `kpkn-food-fg270m-v1.task`
- o `kpkn-food-fg270m-v1.litertlm`

No se usa GGUF en el WebView.

Staging rapido:

```bash
npm run local-ai:stage-model -- --src "C:\ruta\al\modelo"
npm run local-ai:check-model
```

Rutas de destino:

- install-time pack: `android/kpknLocalAiPack/src/main/assets/install-time-models/`
- debug/sideload: `android/app/src/main/assets/models/`

Guia completa:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\docs\local-ai-functiongemma-android.md`
