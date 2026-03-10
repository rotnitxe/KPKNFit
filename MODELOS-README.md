# IA local para nutricion

El frontend ya no descarga ni empaqueta modelos GGUF.

El flujo actual usa:

- parser por reglas en el cliente
- busqueda sobre la base de alimentos del proyecto
- IA local opcional via backend compatible con Ollama

Comandos utiles:

```bash
npm run build
npm run build:android
npm run test:nutrition-logging
```

Si el backend local no esta disponible, la app hace fallback automatico al parser por reglas.
