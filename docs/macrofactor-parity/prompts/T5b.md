Eres el agente que implementa el paquete **T5b** de la ola MFP (paridad con lo mejor de MacroFactor) de **KPKN Fit**, una app Android nativa en Kotlin con Jetpack Compose.
- Tú escribes el código y haces los commits.
- El orquestador (Claude) revisará tu rama y la integrará. No fusiones nada.

**Dónde trabajas:** el worktree `C:\kw\mfp`, rama `feat/macrofactor-parity`, ya en checkout. Trabaja SOLO ahí y no cambies de rama. Fuera de él solo escribes logs en `C:\kw\logs\`.

**Lee primero, enteros y en este orden** (rutas dentro de `C:\kw\mfp`):
1. `docs/macrofactor-parity/briefs/COMUN.md`: reglas comunes, Gradle, Git, clean-room e informe.
2. `docs/macrofactor-parity/briefs/T5b.md`: **tu brief; manda sobre todo lo demás**.
3. `CLAUDE.md` (la cifra de Room está atrasada: es v29) y `AGENTS.md` (sus comandos de Gradle NO aplican).
4. El código que vas a tocar, antes de editarlo.

**Reglas firmes (el detalle está en COMUN.md):**
- Gradle SOLO así, con la salida a un log y un `timeout` de la herramienta ≥ 3 600 000 ms, en primer plano:
  `bash /c/kw/tools/gradle_solo.sh /c/kw/mfp/android-native <tareas> > /c/kw/logs/mfp-T5b-<n>.log 2>&1`
  Si tu terminal es PowerShell: `& 'C:\Program Files\Git\bin\bash.exe' -c 'bash /c/kw/tools/gradle_solo.sh /c/kw/mfp/android-native <tareas> > /c/kw/logs/mfp-T5b-<n>.log 2>&1'`
  Nunca `./gradlew` directo, ni `--stop`, ni `clean`, ni `GRADLE_USER_HOME`, ni `JAVA_TOOL_OPTIONS`, ni mates procesos `java`. Si un candado de Gradle es ajeno y no se libera, PARA y cuéntalo.
- Flavor base solamente. Solo los tests de lo que tocas.
- Git: `git add` por rutas explícitas (nunca `-A`); sin `stash`, `reset --hard`, `rebase`, `merge`, `push`; commits en español con la última línea `Co-Authored-By: Codex <noreply@openai.com>`.
- Solo los archivos de la lista del brief. Si necesitas otro, descríbelo en el informe en vez de tocarlo.
- Sin descargas, sin librerías nuevas, sin teléfono ni emulador. Tope de 3 intentos por problema: luego para y descríbelo.
- Clean-room: no introduzcas los coeficientes prohibidos de COMUN.md.

**Terminado =** los criterios del brief en tests verdes (con la cuenta sacada de los XML), `:app:compileBaseDebugKotlin` verde, commits solo con tus archivos y el informe.

**Informe final:** termina tu respuesta con el informe de COMUN.md («Informe final», ≤ 400 palabras, en español, sin pegar código).
