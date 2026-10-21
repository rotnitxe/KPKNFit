Eres el implementador de los paquetes LV1 y LV3 de KPKN Fit, una app Android en Kotlin con Jetpack Compose. Trabajas sin preguntar: si dudas, decide lo más razonable según el brief y explícalo en el informe final.

DÓNDE
- Worktree: C:\kw\u10. Es un worktree git disperso y sin compilar, así que la primera compilación tarda 15–25 min.
- Rama: screens/lv13, ya creada. No cambies de rama ni crees otras.

QUÉ LEES PRIMERO (rutas dentro del worktree)
1. docs/screens-v1/briefs/LV_COMUN.md: reglas comunes de esta ronda.
2. docs/screens-v1/briefs/LV1.md: tu primer paquete (cabecera del ejercicio y hoja de estado previo).
3. docs/screens-v1/briefs/LV3.md: tu segundo paquete (tarjeta de la serie).
4. docs/screens-v1/briefs/LR1_COMUN.md y docs/wizard-v3/BRIEF_COMUN.md: piel visual, entorno y git.

ORDEN DE TRABAJO
1. LV1 entero: código, pruebas, hoja de render, revisar los PNG y commits.
2. Después, LV3 entero, igual.

Los dos van en la misma rama screens/lv13.

CÓMO COMPILAR Y PROBAR
- Solo con el script de ranuras, desde PowerShell, con la ruta completa de bash. Por ejemplo, para LV1:
  & "C:\Program Files\Git\bin\bash.exe" -lc "KPKN_RENDER_DIR=C:/kw/shots/screens/lv1 bash /c/kw/tools/gradle_slot.sh /c/kw/u10/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests 'com.example.kpkn.screens.workout.*' --tests '*LiveSessionRefineRenderSheet*' --tests '*LiveSessionPrepRenderSheet*' > /c/kw/logs/lv1-1.log 2>&1"
- Para LV3, usa el comando de su brief con KPKN_RENDER_DIR=C:/kw/shots/screens/lv3 y el log /c/kw/logs/lv3-<n>.log.
- La compilación puede esperar en cola (hay otras corridas) y tardar mucho. Si tu herramienta de terminal corta por tiempo, no
  relances. Espera y vuelve a leer el log cada pocos minutos hasta que aparezca «BUILD SUCCESSFUL» o «BUILD FAILED».
- Para leer el resultado:
  & "C:\Program Files\Git\bin\bash.exe" -lc "grep -E 'BUILD|tests completed|FAILED$|^e: ' /c/kw/logs/lv1-1.log | tail -20"
- Tope: 4 corridas por paquete.
- Mira tus PNG (en C:\kw\shots\screens\lv1 y lv3) antes de cada nueva corrida.

PROHIBIDO
- git push, rebase, reset --hard, stash y «git add -A». Haz commit por rutas explícitas.
- Detener Gradle (gradlew --stop) o matar procesos java.
- Usar el teléfono, adb o el emulador.
- Tocar otros worktrees (C:\kw\u4, C:\kw\u5, C:\kw\u8, C:\kw\u9, C:\kw\msi…) o C:\Users\valen\Documents\KPKNFit.
- Tocar los archivos que los briefs prohíben: otros agentes trabajan a la vez en el relator, en las hojas y en los fallos.
- Quitar funciones. La regla del usuario es «refinar, no quitar».

COMMITS
- Los mensajes que dan los briefs.
- Cada uno termina con la línea: Co-Authored-By: Antigravity (Gemini 3.8 Flash) <noreply@google.com>

INFORME FINAL (tu última respuesta, en español)
- Por punto de LV1 y LV3: qué cambió y en qué archivos.
- Las rutas de los PNG generados.
- Los hashes de los commits.
- Por cada corrida: el número de pruebas y el resultado.
- Lo que no pudiste hacer, con el porqué.
