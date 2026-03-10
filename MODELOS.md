# Registro de Comidas y IA Local

## Estado actual

El registro de comidas ya no usa WebLLM ni modelos GGUF embebidos en el frontend.

La arquitectura real es esta:

1. `RegisterFoodDrawer` captura la descripcion libre del usuario.
2. `parseFreeFormNutrition()` decide entre parser por reglas o IA local via backend.
3. `searchFoods()` resuelve cada etiqueta usando la base local y catalogos offline/online existentes.
4. El usuario corrige ambiguedades en la UI antes de guardar.
5. `handleSaveNutritionLog()` persiste el `NutritionLog` final en la app.

## IA local opcional

La IA local sigue disponible, pero no dentro del navegador.

Se activa solo si el backend responde en `GET /api/ai/status` y reporta un proveedor local disponible, hoy pensado para Ollama.

Campos relevantes en `Settings`:

- `nutritionDescriptionMode`: `auto`, `rules` o `local-ai`
- `nutritionUseLocalAI`
- `nutritionLocalModel`
- `nutritionUseOnlineApis`

Comportamiento:

- `rules`: usa solo el parser heuristico local.
- `auto`: intenta IA local si el backend esta disponible; si no, vuelve a reglas.
- `local-ai`: prioriza IA local, pero tambien hace fallback a reglas si el backend falla o expira.

## Build

No existe descarga automatica de modelos durante el build.

Comandos vigentes:

```bash
npm run build
npm run build:android
```

Android y web comparten el mismo bundle de la app. No se copian modelos a `www/models/` ni a assets de Capacitor.

## Verificacion rapida

Para validar el flujo base del registro descriptivo:

```bash
npm run test:nutrition-logging
```

Este chequeo cubre:

- parser descriptivo por reglas
- fallback local sin backend
- busqueda real en la base de alimentos

## Operacion recomendada

Si quieres IA local de verdad:

1. Levanta el backend que expone `/api/ai/status`.
2. Configura Ollama con el modelo que quieras usar.
3. Define `nutritionLocalModel` en settings si no quieres el default.

Si no hay backend, el sistema sigue funcionando con parser por reglas y busqueda nutricional local.
