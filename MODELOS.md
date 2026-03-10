# 🧠 Modelo de IA Local - Food Logger Unified

## 📦 Modelo Incluido

Este proyecto utiliza **Qwen2.5-1.5B-Instruct** en formato GGUF cuantizado (Q4_K_M) para análisis nutricional inteligente de alimentos.

### Especificaciones

| Propiedad | Valor |
|-----------|-------|
| **Modelo** | Qwen2.5-1.5B-Instruct |
| **Formato** | GGUF (Q4_K_M) |
| **Tamaño** | ~1.1 GB |
| **Idiomas** | Multilingüe (español nativo) |
| **Contexto** | 512-1024 tokens (optimizado para móvil) |
| **RAM requerida** | ~2GB durante inferencia |

---

## 📁 Ubicación del Archivo

El modelo debe estar en:

```
public/models/qwen2.5-1.5b-instruct-q4_k_m.gguf
```

**Importante:** El archivo ya está incluido en el repositorio. No es necesario descargarlo manualmente.

---

## 🔍 Verificación de Integridad

El sistema verifica automáticamente que el modelo:

1. **Exista** en la ruta esperada
2. **Tenga tamaño válido** (entre 1GB y 1.3GB)
3. **Sea accesible** vía fetch

Si alguna verificación falla, el sistema usa fallback heurístico sin IA.

---

## 🚀 Cómo Funciona

### Cascada Inteligente

```
┌─────────────────────────────────────────────────────────┐
│  Usuario: "2 huevos con pan integral"                   │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│  1. Caché (0ms) → ¿Ya analizado antes?                  │
│     ✅ 60-70% de consultas resueltas aquí               │
└─────────────────────────────────────────────────────────┘
                          ↓ NO
┌─────────────────────────────────────────────────────────┐
│  2. BD Local (50ms) → ¿Coincide con USDA/OFF?           │
│     ✅ 20-25% de consultas resueltas aquí               │
└─────────────────────────────────────────────────────────┘
                          ↓ NO
┌─────────────────────────────────────────────────────────┐
│  3. Heurísticas (5ms) → ¿Alimento simple conocido?      │
│     ✅ 5-10% de consultas resueltas aquí                │
└─────────────────────────────────────────────────────────┘
                          ↓ NO
┌─────────────────────────────────────────────────────────┐
│  4. IA Qwen2.5 (2-3s) → Último recurso                  │
│     ✅ 3-5% de consultas resueltas aquí                 │
└─────────────────────────────────────────────────────────┘
```

### Lazy Loading

La IA **solo se carga cuando es necesario**:

- **No se carga** al inicio de la app
- **Se carga** cuando una consulta no puede ser resuelta por caché/BD/heurística
- **Se descarga** después de 3 minutos de inactividad (libera RAM)

**Resultado:** La IA usa RAM solo el **3-5% del tiempo**.

---

## 📱 Rendimiento en Dispositivos

### Detección Automática de Capacidad

El sistema detecta automáticamente las capacidades del dispositivo:

| Tier | RAM | WebGPU | Cores | ¿Usa IA? |
|------|-----|--------|-------|----------|
| **High** | 8GB+ | ✅ | 6+ | ✅ Sí, sin límites |
| **Medium** | 6GB+ | ✅ | 4+ | ✅ Sí, con contexto reducido |
| **Low** | <6GB | ❌ | <4 | ❌ No, solo fallback |

### Tiempos Esperados

| Nivel | Tiempo | % Consultas |
|-------|--------|-------------|
| Caché | <10ms | 60-70% |
| BD Local | <100ms | 20-25% |
| Heurística | <10ms | 5-10% |
| IA (High) | 1-2s | 2-3% |
| IA (Medium) | 2-4s | 1-2% |

---

## 🔧 Configuración del Build

El build copia automáticamente el modelo a `www/models/`:

```json
{
  "scripts": {
    "build": "node scripts/runNpmScriptFromPackageDir.cjs build:inner",
    "build:inner": "... && npx cpx2 \"public/models/**/*\" www/models/ --flat=false"
  }
}
```

**Para Android (Capacitor):**

```bash
npm run build
npx cap sync android
```

El modelo se incluye en `android/app/src/main/assets/public/models/`.

---

## 🧪 Testing

### Dataset de Pruebas

El archivo `tests/datasets/food-descriptions.cl.txt` contiene **150+ descripciones** de comidas reales para testing:

- Comidas simples
- Platos compuestos
- Cantidades ambiguas
- Modismos chilenos
- Marcas comerciales
- Errores de tipeo
- Descripciones largas

### Ejecutar Tests

```bash
# Tests unitarios
npm test -- food-logger

# Tests E2E con dataset
npm test -- food-descriptions
```

---

## ⚠️ Solución de Problemas

### "Modelo no encontrado"

**Causa:** El archivo no está en `public/models/`

**Solución:**
```bash
# Verificar que existe
ls public/models/qwen2.5-1.5b-instruct-q4_k_m.gguf

# Si no existe, descargar
# URL: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf
```

### "Timeout en inferencia"

**Causa:** Dispositivo lento o contexto muy largo

**Solución:**
- Reducir `contextLength` en `MODEL_CONFIGS`
- Aumentar timeout en `analyzeFood()`
- El fallback automático ya está activado

### "Out of memory"

**Causa:** Dispositivo con <6GB RAM

**Solución:**
- El sistema ya detecta gama baja y desactiva IA automáticamente
- Verificar que `detectDeviceTier()` funciona correctamente

---

## 📊 Métricas de Monitoreo

Trackear en analytics:

```typescript
const metrics = {
    // Eficiencia de cascada
    cacheHitRate: '(caché hits) / (total consultas)',
    target: '>60%',
    
    // Uso de IA
    aiUsageRate: '(consultas con IA) / (total consultas)',
    target: '<5%',
    
    // Rendimiento
    avgResponseTime: 'suma(tiempo) / total consultas',
    target: '<500ms',
    
    // Calidad
    successRate: '(consultas sin edición manual) / (total consultas)',
    target: '>80%',
};
```

---

## 🎯 Criterios de Aceptación

- [ ] El modelo está incluido en el bundle (1.1GB)
- [ ] Verificación de integridad funciona al inicio
- [ ] Lazy loading carga IA solo cuando es necesario
- [ ] Descarga automática después de 3 min de inactividad
- [ ] Fallback automático si IA falla o tarda >5s
- [ ] Detección de gama baja desactiva IA automáticamente
- [ ] 80%+ de comidas resueltas sin edición manual
- [ ] <5% de consultas usan IA (caché/BD/heurística predominan)

---

## 📚 Recursos

- **Modelo:** https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF
- **WebLLM:** https://github.com/mlc-ai/web-llm
- **Documentación Qwen:** https://qwen.readthedocs.io/

---

## 🚨 Importante

**Los macros NUNCA son generados por IA.** 

La IA solo ayuda a desambiguar qué quiso decir el usuario. Los valores nutricionales (calorías, proteína, carbs, grasas) siempre vienen de:

1. Base de datos local (USDA/OpenFoodFacts)
2. Heurísticas validadas
3. Fallback conservador

Esto previene alucinaciones y garantiza precisión nutricional.
