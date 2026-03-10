# 🤖 Modelo Qwen2.5-1.5B - Descarga Automática

## 📦 ¿Cómo Funciona?

El modelo de IA **NO** está incluido en el repositorio de Git (pesa 1.1GB). En su lugar, se descarga automáticamente durante el build.

## 🔄 Flujo de Build

```bash
npm run build
```

Esto ejecuta:

1. `npm run download-model` → Descarga Qwen2.5-1.5B desde HuggingFace
2. Build normal de la app
3. El modelo se copia automáticamente a `www/models/`

## 🌍 Vercel / CI/CD

El script `download-model.cjs` se ejecuta automáticamente en Vercel durante el build.

**No necesitas configurar nada extra.**

## 📱 Android (Capacitor)

```bash
npm run build
npx cap sync android
```

El modelo se incluye en el APK automáticamente.

## 🔧 Descarga Manual (Opcional)

Si querés descargar el modelo manualmente:

```bash
npm run download-model
```

O descargar directamente:

```
URL: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf
Destino: public/models/qwen2.5-1.5b-instruct-q4_k_m.gguf
```

## ⚠️ Consideraciones

### GitHub
- ✅ El modelo NO está en Git (`.gitignore` lo excluye)
- ✅ El repo pesa <100MB (sin el modelo)
- ✅ Sin problemas con límites de GitHub

### Vercel
- ✅ El build descarga el modelo automáticamente
- ✅ Timeout: 5 minutos (suficiente para 1.1GB)
- ✅ Sin costos extra

### Android
- ✅ El modelo se incluye en el APK
- ✅ APK final: ~1.2GB (1GB app + 1.1GB modelo)
- ✅ Considerar usar App Bundle para reducir tamaño

## 📊 Tamaño del Modelo

| Formato | Tamaño | Calidad |
|---------|--------|---------|
| Original (Qwen2.5-1.5B) | 3GB | 100% |
| **Cuantizado (Q4_K_M)** | **1.1GB** | **95%** |

Usamos la versión cuantizada Q4_K_M que mantiene 95% de calidad con 63% menos tamaño.

## 🚨 Solución de Problemas

### "Modelo no encontrado"

El build falló al descargar. Soluciones:

1. **Verificar conexión a internet**
2. **Descargar manualmente:**
   ```bash
   npm run download-model
   ```
3. **Verificar espacio en disco** (necesitas 2GB libres)

### "Download timeout"

La descarga tardó más de 5 minutos. Soluciones:

1. **Mejorar conexión** (se necesita ~50Mbps para descargar en 5 min)
2. **Descargar manualmente** y colocar en `public/models/`
3. **Usar mirror** (ver documentación de HuggingFace)

### "Tamaño inválido"

El archivo descargado está corrupto. Solución:

```bash
# Eliminar y re-descargar
rm public/models/qwen2.5-1.5b-instruct-q4_k_m.gguf
npm run download-model
```

## 📝 Scripts Disponibles

| Comando | Descripción |
|---------|-------------|
| `npm run download-model` | Descarga el modelo desde HuggingFace |
| `npm run build` | Build completo (incluye descarga) |
| `npm run build:android` | Build para Android (incluye descarga) |

---

**Última actualización:** 2026-03-10
**Versión del modelo:** Qwen2.5-1.5B-Instruct-Q4_K_M
