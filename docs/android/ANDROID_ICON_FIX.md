# Solución: Icono de la app no se actualiza en Android

## Diagnóstico

Tu proyecto solo tiene **iconos adaptativos** (API 26+) en `mipmap-anydpi-v26/`. Faltan los **iconos legacy** (PNG) que muchos launchers y dispositivos usan como respaldo. Además, Android cachea los iconos de forma agresiva.

## Solución 1: Image Asset Studio (recomendado)

### Pasos en Android Studio

1. **Abre el proyecto Android** en Android Studio (`npx cap open android`).

2. **Image Asset Studio**  
   - Clic derecho en `app` → **New** → **Image Asset**  
   - O: **File** → **New** → **Image Asset**

3. **Configuración del icono**
   - **Icon Type**: `Launcher Icons (Adaptive and Legacy)` ← importante
   - **Name**: `ic_launcher` (por defecto)
   - **Foreground Layer**:
     - **Source Asset**: `Image` (o `Clip Art` si prefieres)
     - Si usas SVG: selecciona tu archivo `.svg`
     - Si usas PNG: selecciona una imagen de al menos 512×512 px
   - **Background Layer**:
     - Color o imagen de fondo (ej. `#FFFFFF` o `#0a0a0a`)

4. **Ruta de salida**
   - Debe ser: `android/app/src/main/res`
   - Si aparece otra ruta, cámbiala manualmente.

5. **Finish**
   - Haz clic en **Next** y luego **Finish**.
   - Esto generará:
     - `mipmap-anydpi-v26/` (adaptativo)
     - `mipmap-mdpi/`, `mipmap-hdpi/`, `mipmap-xhdpi/`, `mipmap-xxhdpi/`, `mipmap-xxxhdpi/` (legacy PNG)

### Si no se guarda

- **File** → **Invalidate Caches** → **Invalidate and Restart**
- Comprueba que el módulo seleccionado sea `app` (no otro módulo).
- Cierra y vuelve a abrir Android Studio.

---

## Solución 2: Limpiar caché y reinstalar

1. **En Android Studio**
   - **Build** → **Clean Project**
   - **Build** → **Rebuild Project**

2. **En el dispositivo**
   - Desinstala la app por completo.
   - Reinicia el teléfono (opcional pero recomendado).
   - Instala de nuevo desde Android Studio (Run ▶️).

---

## Solución 3: Verificar que Capacitor no sobrescriba

`npm run cap:sync` solo copia `www/` a `android/app/src/main/assets/public`.  
**No modifica** `res/` ni los iconos. Los cambios en `res/` se mantienen.

---

## Solución 4: Iconos legacy manuales (si Image Asset falla)

Si Image Asset Studio no genera los PNG legacy, puedes crearlos con:

```bash
# Requiere ImageMagick instalado
# Genera desde favicon.svg o tu icono base (512x512)

mkdir -p android/app/src/main/res/mipmap-mdpi
mkdir -p android/app/src/main/res/mipmap-hdpi
mkdir -p android/app/src/main/res/mipmap-xhdpi
mkdir -p android/app/src/main/res/mipmap-xxhdpi
mkdir -p android/app/src/main/res/mipmap-xxxhdpi

# Tamaños: 48, 72, 96, 144, 192 px
convert icon-base.png -resize 48x48 android/app/src/main/res/mipmap-mdpi/ic_launcher.png
convert icon-base.png -resize 72x72 android/app/src/main/res/mipmap-hdpi/ic_launcher.png
convert icon-base.png -resize 96x96 android/app/src/main/res/mipmap-xhdpi/ic_launcher.png
convert icon-base.png -resize 144x144 android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png
convert icon-base.png -resize 192x192 android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
```

---

## SVG: posibles problemas

Si usas un SVG y el icono no se ve bien o no se guarda:

- **Simplifica el SVG**: evita gradientes complejos, máscaras o efectos que Android no soporte bien.
- **Tamaño razonable**: viewBox muy grande (ej. 14998×14998) puede dar problemas. Prueba con 512×512 o 1024×1024.
- **Alternativa**: exporta el SVG a PNG (512×512) y usa ese PNG como fuente en Image Asset Studio.

---

## Resumen rápido

| Problema | Acción |
|----------|--------|
| Icono genérico en el dispositivo | Usar **Launcher Icons (Adaptive and Legacy)** en Image Asset Studio |
| Cambios no se ven | Desinstalar app, reiniciar dispositivo, reinstalar |
| Image Asset no guarda | Invalidate Caches / Restart en Android Studio |
| Solo hay `mipmap-anydpi-v26` | Añadir carpetas legacy con PNG (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi) |
