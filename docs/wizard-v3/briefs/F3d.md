# F3d · Integrar los vídeos grabados de la bienvenida (punto 1)

Worktree `C:\kw\u1`, rama `v3/f3d` (sale de `feat/wizard-v3`). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`.

## Qué hay (verificado por el orquestador)
- Los vídeos ya están grabados y montados. Los grabó el orquestador en el teléfono real, con la app `.dbg` y datos de demostración, y están en `C:\kw\shots\v3\videos\final\`:

  | Archivo | Duración | Tamaño |
  |---|---|---|
  | `welcome_entreno.mp4` | 9,3 s | 0,5 MB |
  | `welcome_nutricion.mp4` | 14,9 s | 0,9 MB |
  | `welcome_rings.mp4` | 11,0 s | 0,5 MB |

  - Los tres: 720 × 1488 (la proporción del lienzo de 300 × 620 dp de las escenas), H.264 High a 30 fps y sin audio.
  - Pósteres: `welcome_entreno_poster.webp`, `welcome_nutricion_poster.webp` y `welcome_rings_poster.webp`, de 720 × 1488 y ≤ 42 KB.
- `screens/onboarding/welcome/WelcomeVideoPlayer.kt` (F3a) ya usa el vídeo cuando existen el `.mp4` y su póster. Los busca por nombre con `getIdentifier` (`welcomeVideoAssetsOf`); si falta alguno, se ve la escena Canvas.

## Qué hacer
1. **Recursos.** Copia los archivos tal cual (sin recodificar y con esos nombres exactos):
   - los 3 `.mp4` a `android-native/app/src/main/res/raw/`;
   - los 3 `.webp` a `android-native/app/src/main/res/drawable-nodpi/`.
2. **`res/raw/keep.xml` (nuevo).** Como los recursos se buscan por nombre, este archivo evita que el recorte de recursos del release los quite. El build no lo empaqueta:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <resources xmlns:tools="http://schemas.android.com/tools"
       tools:keep="@raw/welcome_entreno,@raw/welcome_nutricion,@raw/welcome_rings,@drawable/welcome_entreno_poster,@drawable/welcome_nutricion_poster,@drawable/welcome_rings_poster" />
   ```
3. **`WelcomeShellCopy.kt`.** Cambia SOLO `demoDescription` de las tres páginas. TalkBack lo lee como descripción del teléfono, así que tiene que decir lo que pasa en cada vídeo. Textos exactos:
   - Entreno: «Vídeo de demostración: retomas la sesión de hoy, registras una serie con kilos y repeticiones y ves el descanso.»
   - Nutrición: «Vídeo de demostración: escribes una comida con tus palabras, KPKN la entiende y los anillos del día suben.»
   - Recuperación: «Vídeo de demostración: ajustas tu energía con el deslizador y en Inicio ves el anillo actualizado con su motivo.»

   `label`, `title` y `message` no cambian.
4. **Pruebas** (filtro `*Welcome*`):
   - **Sin vídeo, la escena Canvas.** Ahora existen los recursos reales, así que cualquier prueba que lo daba por hecho con los recursos de la app tiene que pasar a usar `welcomeVideoAssetsOf(pageId) { _, _ -> 0 }`: es una función pura y no depende de los recursos.
   - **Con los recursos.** Añade una prueba Robolectric: con `context.resources.getIdentifier`, `welcomeVideoAssetsOf` devuelve un valor no nulo para las tres páginas.
   - **Textos.** Las pruebas que comparan los textos `demoDescription` antiguos se actualizan a los nuevos.
   - **Etiquetas.** Las `welcome-*` siguen.
5. **Comando:**
   `bash /c/kw/tools/gradle_slot.sh /c/kw/u1/android-native :app:compileDebugKotlin :app:testDebugUnitTest --tests "*Welcome*"`
6. **Commit** en `v3/f3d`, por rutas (los 6 recursos, `keep.xml`, `WelcomeShellCopy.kt` y las pruebas), con mensaje en español y la línea `Co-Authored-By` del BRIEF_COMUN.

## Prohibido
- Tocar cualquier otro archivo.
- En particular, `WelcomeVideoPlayer.kt` y `WelcomeShell.kt` no se tocan. Si una prueba demuestra un fallo real en ellos, para y repórtalo con la salida.
- Recodificar o recortar los vídeos.

## Informe
- Archivos y tamaños.
- Pruebas que corrieron y su resultado.
- Cualquier prueba que tuviste que cambiar y por qué.
