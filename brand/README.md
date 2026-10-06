# KPKN · Marca

Identidad visual oficial de KPKN, **«Torre anidada»**, tal como la entregó la persona dueña del producto. Esta carpeta es la fuente de los recursos de marca: lo que vive dentro de la app (`android-native/app/src/main/`) sale de aquí (tabla al final).

## Concepto

Tres anillos de tronco vistos en perspectiva y apilados unos dentro de otros: la carga de Caupolicán (torre, peso, subir) más el crecimiento de los anillos. Cada disco es un ring de la app:

| Disco  | Ring    |
|--------|---------|
| Base   | Columna |
| Medio  | Músculo |
| Arriba | Energía |

## Qué hay aquí

```
brand/
├─ marca/
│  ├─ LEEME.txt            Descripción original del kit.
│  ├─ S1-principal/        Logo principal (tres discos). Es el único que se usa.
│  │  ├─ 01-simbolo/       Símbolo solo. SVG + PNG transparente (256 a 2048): negro, blanco, blanco-puro, color.
│  │  ├─ 02-logotipo/      Símbolo + KPKN, horizontal y vertical (SVG + PNG).
│  │  ├─ 03-app-icon/      android/res (mipmaps + adaptativo con monochrome), play-store-512, ios, web (favicon, PWA).
│  │  ├─ 04-splash/        Pantallas de carga oscuro/claro/color + ícono de la SplashScreen API (Android 12+).
│  │  ├─ 05-redes/         Avatares 1080, banners e imagen OG 1200x630.
│  │  └─ android-vector/   VectorDrawables blancos, listos para teñir con tint.
│  └─ Fuente-Syne/         Syne (SIL OFL): ttf, variable, woff2, res/font de Android, web/syne.css.
├─ animaciones/            MP4 (16x9 y 9x16), demo en HTML y el port a Compose (android/KpknMotion.kt).
└─ wizard/                 Overlay «módulo completado» del wizard de bienvenida: demo HTML, JS/JSX y el port a Compose
                           (android/ModuleCompleteOverlay.kt, solo referencia: lo integra el wizard).
```

La variante S2 (con núcleo sólido) del kit original no se incluye: se usa solo S1.

## Paleta

| Color     | Hex       | Uso |
|-----------|-----------|-----|
| Tinta     | `#121212` | Fondo oscuro de marca, fondo del ícono y del splash. Logo sobre fondos claros. |
| Crema     | `#F2EEE6` | Logo y texto de marca sobre oscuro (el splash dibuja la torre en crema). |
| Ok        | `#43D18C` | Confirmación (check del módulo completado). |
| Columna   | `#8FB2FF` | Ring Columna (disco Base). |
| Músculo   | `#F49A6E` | Ring Músculo (disco Medio). |
| Energía   | `#F7CF73` | Ring Energía (disco Arriba). |
| Mente     | `#C9B8FF` | Mente (el cerebro que acompaña a los rings en la animación de rings). |

Tinta y crema salen de `marca/LEEME.txt`; ok, los colores de ring y mente, de `wizard/kpkn-module-overlay.js` (`COLORS`) y `wizard/android/ModuleCompleteOverlay.kt`. Los tres colores de ring coinciden con los de `animaciones/android/KpknMotion.kt`. En la app: `kpkn_ink` y `kpkn_cream` en `res/values/colors.xml`.

## Reglas de uso

- **Tipografía.** Syne (ExtraBold 800, tracking −1 %) solo para títulos importantes, números grandes y botones de marca. El texto largo, las etiquetas y los cuerpos van en la sans de la app: Syne es muy expresiva y cansa en párrafos.
- **Logo.** Los vectores de `android-vector/` son blancos y se tiñen con `tint`: no hay una copia por color. Crema (`#F2EEE6`) sobre fondos oscuros, tinta (`#121212`) sobre claros. En Compose: `Image(painterResource(R.drawable.kpkn_simbolo), contentDescription = "KPKN", colorFilter = ColorFilter.tint(color))`.
- **Proporción.** El símbolo es apaisado (≈ 1,54 : 1). Déjalo ajustarse dentro de su caja (`ContentScale.Fit`); un VectorDrawable dibujado a mano con `setBounds` se estira, así que calcula los bounds con la proporción del símbolo.
- **Movimiento.** Las animaciones respetan «reducir movimiento» del sistema (muestran el estado final sin animar).
- **Variantes.** Para fondos de color o impresión usa los PNG/SVG de `01-simbolo` y `02-logotipo`. Las versiones `color-*` usan una paleta propuesta (ver «Pendientes»).

## Animaciones

`ui/motion/KpknMotion.kt` (copia de `animaciones/android/KpknMotion.kt` con el paquete de la app) es la librería de marca en Compose: `KpknMotion(KpknAnim.X, ...)`. **No está cableada a ninguna pantalla**; queda disponible para:

- Splash / intro (una vez): `APILAR`, `RESPIRAR`, `PULSO`, `CARGA`, `DEFORMAR`.
- Cargando (bucle sin corte): `APILANDO`, `OLA`, `GIRANDO`.
- Momentos (con dato real): `META_CUMPLIDA`, `NUEVO_RECORD`, `RACHA`, `RECUPERADO`, `SIN_CONEXION`.

Los MP4 (`16x9` para presentaciones, `9x16` para historias/Reels) y `kpkn-animaciones.html` (ábrelo en el navegador, funciona sin internet) son la referencia visual de cada una.

## Recurso de marca → dónde vive en la app

Rutas de la app relativas a `android-native/app/src/main/`.

| Recurso de marca | Dónde vive en la app | Quién lo usa |
|------------------|----------------------|--------------|
| `marca/S1-principal/android-vector/drawable/kpkn_simbolo.xml` | `res/drawable/kpkn_simbolo.xml` | Barra superior de Home (`screens/home/HomeScreen.kt`, caja de 43 dp), widget de nutrición (`widgets/NutritionQuickActionWidget.kt`) e imágenes de «compartir entrenamiento» (`screens/workout/WorkoutShareService.kt`). |
| `marca/S1-principal/android-vector/drawable/kpkn_logo_horizontal.xml` | `res/drawable/kpkn_logo_horizontal.xml` | Disponible; sin uso todavía. |
| `marca/S1-principal/android-vector/drawable/kpkn_logo_vertical.xml` | `res/drawable/kpkn_logo_vertical.xml` | Disponible; sin uso todavía. |
| `marca/S1-principal/03-app-icon/android/res/mipmap-*dpi/` | `res/mipmap-mdpi … xxxhdpi/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`, `ic_launcher_monochrome.png` | `android:icon` / `android:roundIcon` del manifest y las notificaciones (`R.mipmap.ic_launcher`). |
| `marca/S1-principal/03-app-icon/android/res/mipmap-anydpi-v26/` | `res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Ícono adaptativo (Android 8+) con capa `monochrome` para íconos temáticos (Android 13+). |
| `marca/S1-principal/03-app-icon/android/res/values/ic_launcher_background.xml` | `res/values/ic_launcher_background.xml` | Fondo (tinta) del ícono adaptativo. |
| `marca/S1-principal/04-splash/android-splash-icon.svg` | `res/drawable/ic_splash_kpkn.xml` (mismas formas y transformaciones, en vector) | `windowSplashScreenAnimatedIcon` del tema `Theme.KPKN.Starting`. |
| (tinta `#121212` de la marca) | `res/values/themes.xml` (`Theme.KPKN.Starting`) y `res/values/colors.xml` (`kpkn_ink`, `kpkn_cream`) | Tema de arranque de `MainActivity` en `AndroidManifest.xml`; `installSplashScreen()` en `MainActivity.kt`; dependencia `androidx.core:core-splashscreen:1.2.0` (`android-native/gradle/libs.versions.toml` y `android-native/app/build.gradle.kts`). |
| `marca/Fuente-Syne/android/res/font/` y `android/compose/KpknType.kt` | `res/font/syne_*.ttf`, `ui/theme/KpknType.kt` (`Syne`) y `ui/theme/Type.kt` | Títulos de la app y del wizard (integrada aparte). |
| `animaciones/android/KpknMotion.kt` | `java/com/example/kpkn/ui/motion/KpknMotion.kt` | Librería de marca, sin cablear. |
| `wizard/android/ModuleCompleteOverlay.kt` | Solo referencia aquí | La integra el wizard de bienvenida. |
| Resto: `play-store-512.png`, `ios/`, `web/`, `05-redes/`, SVG/PNG de símbolo y logotipo, splash en PNG, MP4, HTML | Solo en `brand/` | Play Console, iOS, web y redes. |

## Actualizar el kit

1. Reemplaza el contenido de `brand/` con el kit nuevo (sin transformar).
2. Ícono: copia `03-app-icon/android/res/` sobre `res/` y borra los `ic_launcher*.webp` o PNG que queden de más (el mismo nombre con dos extensiones en una carpeta `mipmap-*` rompe el build).
3. Logo: copia `android-vector/drawable/*.xml` a `res/drawable/`.
4. Splash: si cambia `04-splash/android-splash-icon.svg`, rehaz `res/drawable/ic_splash_kpkn.xml` (los arcos `a` valen tal cual en `pathData`; las transformaciones del SVG van en `<group>`).

## Pendientes

- Las versiones `color-*` del logo usan la paleta **propuesta** del `LEEME.txt` de marca (Columna `#3D7BFF`, Músculo `#E8572A`, Energía `#F5B82E`), que no coincide con los colores de ring de la app (`#8FB2FF`, `#F49A6E`, `#F7CF73`). Confirmar cuál es la oficial antes de usar las `color-*` en producción.
- Los íconos pequeños de las notificaciones siguen siendo `R.mipmap.ic_launcher` (el ícono completo, no una silueta). Un ícono monocromo propio de notificación se puede hacer con `kpkn_simbolo`.
