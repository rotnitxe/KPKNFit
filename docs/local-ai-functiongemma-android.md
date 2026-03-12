# Integracion real de FunctionGemma en Android

Esta guia deja el modelo local listo para el flujo descriptivo de nutricion en Android.

## 1. Que espera hoy la app

Nombre canonico del modelo:

- `kpkn-food-fg270m-v1`

Formatos aceptados por el bridge nativo:

- `kpkn-food-fg270m-v1.task`
- `kpkn-food-fg270m-v1.litertlm`

Orden de carga en runtime:

1. `android/kpknLocalAiPack/src/main/assets/install-time-models/`
2. `android/app/src/main/assets/models/`
3. fallback heuristico offline si no encuentra modelo real

## 2. Requisito previo

Antes de esta integracion debes tener un export listo del modelo fine-tuneado para KPKN.

El bridge ya esta preparado para:

- inferencia local Android via `tasks-genai`
- entrega install-time con asset pack
- fallback debug/sideload por assets nativos

## 3. Staging del modelo en el repo

Si tu export ya esta en una carpeta local, corre:

```bash
npm run local-ai:stage-model -- --src "C:\ruta\al\export-del-modelo" --clean
npm run local-ai:check-model
```

Que hace:

- busca `.task` y `.litertlm`
- renombra al nombre canonico `kpkn-food-fg270m-v1`
- copia a:
  - `android/kpknLocalAiPack/src/main/assets/install-time-models/`
  - `android/app/src/main/assets/models/`

Opciones utiles:

```bash
npm run local-ai:stage-model -- --src "C:\ruta\al\modelo" --targets install --clean
npm run local-ai:stage-model -- --src "C:\ruta\al\modelo" --targets debug --clean
npm run local-ai:check-model
```

## 4. Sync del proyecto Android

Despues del staging:

```bash
npm run cap:sync
```

Esto:

- recompila el bundle web
- sincroniza Capacitor
- limpia el viejo camino `android/app/src/main/assets/public/models`

## 5. Build debug y build bundle

Para validar el flujo local:

```bash
cd android
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:bundleDebug
```

Para release bundle:

```bash
cd android
.\gradlew.bat :app:bundleRelease
```

## 6. Como validar en dispositivo

En Android abre Nutricion y entra al registro descriptivo.

Esperado:

- no hay calculo en vivo mientras escribes
- `Analizar calorias` dispara el pipeline
- si el modelo real esta presente, `LocalAiPlugin` reporta `modelReady=true`

Puedes inspeccionar desde el runtime:

```js
await window.__kpknNutritionAiTelemetry.summary()
await window.__kpknNutritionAiTelemetry.runs()
await window.__kpknNutritionAiTelemetry.corrections()
```

Tambien puedes revisar el estado del bridge nativo desde el flujo que usa `getLocalAiStatus()`.

## 7. Donde queda integrado en codigo

- plugin nativo: `android/app/src/main/java/com/yourprime/app/localai/LocalAiPlugin.java`
- registro del plugin: `android/app/src/main/java/com/yourprime/app/MainActivity.java`
- asset pack install-time: `android/kpknLocalAiPack/build.gradle`
- wiring del app bundle: `android/app/build.gradle`
- puente JS: `services/localAiService.ts`
- parser/orquestacion: `services/aiNutritionParser.ts`

## 8. Telemetria que ya queda capturada

Cada analisis guarda:

- latencia total
- duracion por etapa (`interpreting`, `matching`, `estimating`)
- engine (`rules` o `local-ai`)
- modelo solicitado y modelo devuelto
- estado del runtime local
- cantidad de items resueltos / revision / no resueltos
- reparto por fuente (`database`, `user-memory`, `local-ai-estimate`, etc.)
- confianza promedio
- cancelaciones, fallos y correcciones manuales

Claves persistidas:

- `nutrition-ai-telemetry-runs`
- `nutrition-ai-telemetry-corrections`

## 9. Problemas comunes

### El modelo no carga pero el build si compila

Revisa que exista al menos uno de estos archivos:

- `android/kpknLocalAiPack/src/main/assets/install-time-models/kpkn-food-fg270m-v1.task`
- `android/kpknLocalAiPack/src/main/assets/install-time-models/kpkn-food-fg270m-v1.litertlm`
- `android/app/src/main/assets/models/kpkn-food-fg270m-v1.task`
- `android/app/src/main/assets/models/kpkn-food-fg270m-v1.litertlm`

### Sigue apareciendo el comportamiento heuristico

Eso normalmente significa:

- no hay modelo staged con el nombre canonico
- el runtime no pudo abrir el formato exportado
- o el build actual sigue usando solo el fallback debug

### El asset pack bundle falla por colision de assets

El pack install-time debe usar:

- `android/kpknLocalAiPack/src/main/assets/install-time-models/`

El fallback debug debe usar:

- `android/app/src/main/assets/models/`

No mezcles ambos en la misma ruta de assets.
