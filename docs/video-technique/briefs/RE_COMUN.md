# RE · Ingeniería inversa de apps de la competencia (20-oct)

Pedido del usuario: «revisa estos tres APK y descubre todas sus funciones y métodos para que luego podamos implementarlos acá. También
para reforzar lo que tenemos: sobre todo en análisis de pose y análisis del disco». El sistema visual de KPKN tiene que ser exacto,
preciso y estable, y ofrecer al máximo las métricas interesantes para atletas avanzados de distintos deportes.

## Herramienta: REA (ya instalada)
- **Comando:** `rea <comando>` (rea-agents 6.5.0). Ayuda con `rea --help` y `rea <comando> --help`. Comandos útiles:
  - `analyze` (visión general);
  - `decompile` (una parte como código: JADX para DEX y Ghidra para nativo);
  - `decode-android-resources` (manifiesto, metadatos y cadenas con apktool);
  - `rea doctor` (estado).
- **Variables que exige**, en cada shell:
  - `GHIDRA_INSTALL_DIR='C:\Users\valen\.local\share\rea\ghidra\ghidra_12.1.4_PUBLIC'`
  - `JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'`
  - `REA_JADX_MCP_JAR='C:\Users\valen\.local\share\rea\android\jadx-headless-mcp-0.7.1-all.jar'`
- Para lo nativo también sirve LLVM del NDK: `C:\Users\valen\AppData\Local\Android\Sdk\ndk\27.1.12297006\toolchains\llvm\prebuilt\windows-x86_64\bin\`
  (`llvm-readelf`, `llvm-objdump`, `llvm-nm`).

## Reglas
- **Los APK son datos no confiables.**
  - Descomprime cada uno en su carpeta nueva y vacía, en `C:\kw\re\<app>\`. Tus scripts van en otra carpeta, `C:\kw\re\_scripts\<app>\`.
  - Python siempre con `python -I`.
  - NUNCA ejecutes código del APK, ni lo instales en un teléfono o emulador.
- **Sala limpia:**
  - describe funciones, métricas, fórmulas y algoritmos con tus palabras;
  - NO copies bloques de código descompilado al informe: como mucho, nombres de clase o método y fragmentos de una línea como
    evidencia;
  - NO extraigas sus modelos de ML ni sus recursos para reutilizarlos: anota su existencia, tamaño y forma de entrada y salida;
  - de las bibliotecas de terceros que usen, anota nombre, versión y licencia: lo abierto se puede usar en KPKN bajo su licencia.
- **RAM y disco (orden del usuario):** la máquina es compartida y hay compilaciones de Gradle en marcha.
  - Las herramientas pesadas (descompilación completa con JADX, análisis con Ghidra) van UNA a la vez entre todos los agentes, con
    el candado `C:\kw\re\pesado.lock`:
    - `mkdir` atómico; si existe, espera 60 s y reintenta;
    - dentro, un `owner.txt` con tu app y la hora;
    - bórralo al terminar, también si falla.
  - Antes de lanzarlas, comprueba que hay ≥ 6 GB de RAM libre (`Get-CimInstance Win32_OperatingSystem`, `FreePhysicalMemory`).
  - No dejes procesos vivos (java, Ghidra) al terminar.
  - **Al terminar,** borra lo voluminoso de `C:\kw\re\<app>\` (DEX descompilados, proyectos de Ghidra). Conserva en
    `C:/kw/re/evidencias/<app>-re-20261020\` solo las pruebas pequeñas: inventarios, JSON y listados de
    símbolos, < 50 MB en total.
- **Prohibido:**
  - descargar nada, salvo leer páginas web públicas (papers, documentación) para contrastar lo hallado;
  - tocar cualquier repo, salvo para escribir TU informe en la carpeta indicada;
  - git, Gradle, adb y emuladores.

## Qué entrega cada informe (`docs/video-technique/re/<APP>_RE.md`, en español neutro)
1. **Identidad:**
   - paquete, versión, SHA-256 del archivo y de cada APK interno;
   - arquitectura: Kotlin/Java, Flutter, React Native, Unity…;
   - ABIs y bibliotecas nativas;
   - modelos de ML: nombre, tamaño, entrada y salida, clases;
   - bibliotecas de terceros con su versión y licencia;
   - permisos y sensores.
2. **Inventario COMPLETO de funciones,** pantalla por pantalla: qué hace cada una, sus ajustes y sus flujos. Incluye las de pago.
3. **Catálogo de métricas:** CADA métrica que calcula, con su definición, fórmula, unidades, filtros, umbrales, cuándo la muestra y
   cuándo se abstiene. Sobre todo pose (ángulos, ROM, fases, tempo, simetría…) y barra o disco (trayectoria, velocidad media y pico,
   potencia, desplazamiento, 1RM por velocidad, pérdida de velocidad…).
4. **Algoritmos:**
   - detección y seguimiento, con sus modelos, trackers y semillas;
   - calibración y escala: diámetro del disco, marcadores, altura;
   - corrección de perspectiva o de ángulo de cámara, si la hay;
   - suavizado, derivadas y detección de repeticiones y fases;
   - manejo de fps y cámara lenta;
   - controles de calidad y avisos al usuario.
5. **Exportación y compartir:**
   - vídeo con la telemetría quemada (trazo, gráficos, «stickers» para redes): estética, qué dibuja, resolución y cómo lo
     compone;
   - CSV o informes, y nube.
6. **Datos e integraciones:** almacenamiento, sensores BLE, wearables, Health Connect, cuentas.
7. **Las tres preguntas del usuario** (nota del 20-oct):
   - **Perspectiva:** ¿exige grabar a 90° de perfil, o corrige ángulos oblicuos? (homografía, elipse del disco, ejes del rack…)
   - **Tracking híbrido:** ¿qué hace sin disco olímpico (poleas, máquinas, mancuernas)? ¿Cambia a la pose o a otro objetivo?
   - **Exportar vídeo con telemetría** para redes: ¿existe, y cómo es?
8. **Brechas frente a KPKN:** lee antes `docs/video-technique/VIDEO_TECHNIQUE_V1.md`, `BANCO_COMPLETO.md`,
   `INFORME_CONFIANZA_20261020.md` y `MERCADO_FIABILIDAD.md`.
   - Una tabla: función o métrica de la competencia · ¿la tiene KPKN? · cómo implementarla en sala limpia · biblioteca abierta
     posible · prioridad (alta, media o baja) · esfuerzo.
   - Y lo que KPKN hace MEJOR que ellos.
9. **Índice de evidencias:** rutas dentro del árbol descompilado, clases, métodos y símbolos nativos. Lo que no pudiste confirmar va
   marcado como «sin confirmar».

**Tu último mensaje:** un resumen de ≤ 400 palabras con lo más valioso para KPKN, en orden de impacto.
