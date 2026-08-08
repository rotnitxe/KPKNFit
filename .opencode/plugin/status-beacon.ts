import type { TuiPlugin, TuiPluginApi } from "@opencode-ai/plugin/tui"

/**
 * KPKN Fit — Status Beacon
 *
 * Extiende la ventana de OpenCode (Windows Terminal) con:
 *   1. Notificación + sonido al iniciar/terminar un build/test y al terminar
 *      una tarea de chat.
 *   2. Color de pestaña según el estado de la sesión:
 *        - Azul   (libre / sin tareas)
 *        - Amarillo (en proceso)
 *        - Verde  (terminado / build-test completado)
 *
 * Requiere Windows Terminal (WT_SESSION definido) para la pestaña de color.
 * Las notificaciones y sonidos usan la API oficial de "attention" de OpenCode.
 */

const COLORS = {
  free: "#3b88c3", // azul
  working: "#c9a227", // amarillo
  done: "#28a745", // verde
} as const

type BeaconState = "free" | "working" | "done"

// Detección de comandos de build/test (gradle / npm test / pytest / etc.)
const BUILD_TEST_RE =
  /(gradlew?\.bat|gradlew|gradle|assembleDebug|assembleRelease|installDebug|test(?:Base|Debug)?\w*|mvn\b|pytest\b|npm\s+run\s+(?:test|build)|\.*test)/i

// Secuencia OSC para Windows Terminal: fijar color de pestaña
const OSC_TAB = "\x1b]9;4;3;HEX\x1b\\"
const OSC_RESET_TAB = "\x1b]9;4;0\x1b\\"

// Tiempo en ms que la pestaña queda "verde" antes de volver a "azul" (libre)
const DONE_HOLD_MS = 4000

export const StatusBeacon: TuiPlugin = async (api) => {
  const { lifecycle, attention } = api

  let state: BeaconState = "free"
  let doneTimer: ReturnType<typeof setTimeout> | undefined
  let activeBuildPty: Set<string> = new Set()

  function setState(next: BeaconState) {
    if (next === state) return
    state = next
    paintTab()
  }

  function paintTab() {
    const hex = COLORS[state]
    // Pestaña de color (Windows Terminal). Establecer un color "fijo" para
    // que no se sobrescriba con el color por defecto del perfil.
    const seq = OSC_TAB.replace("HEX", hex)
    process.stdout.write(seq)
  }

  function resetToFree() {
    doneTimer = undefined
    setState("free")
  }

  function markDone() {
    setState("done")
    if (doneTimer) clearTimeout(doneTimer)
    // Volver a "libre" tras un breve momento de "verde".
    doneTimer = setTimeout(resetToFree, DONE_HOLD_MS)
  }

  function notify(title: string, message: string, sound: string, notification = true) {
    attention
      .notify({
        title,
        message,
        notification,
        sound: { name: sound as never },
      })
      .catch(() => {
        // El renderer/TUI puede no estar disponible; no bloquear nunca el chat.
      })
  }

  // --- Eventos de estado de sesión -----------------------------------------
  api.event.on("session.status", (event) => {
    const status = event.properties.status
    if (status === "busy" && state !== "done") {
      setState("working")
    }
  })

  api.event.on("session.idle", () => {
    // Solo notificar cuando había una tarea en curso (evita spam en idle).
    if (state !== "working") return
    // Una tarea de chat terminó -> verde + notificación + sonido.
    notify(
      "KPKN · Tarea terminada",
      "El chat terminó y quedó libre.",
      "done",
    )
    markDone()
  })

  // --- Inicio / fin de PTY (build, test, comandos de terminal) -------------
  api.event.on("pty.created", (event) => {
    const command = event.properties.info.command ?? ""
    if (!BUILD_TEST_RE.test(command)) return
    activeBuildPty.add(event.properties.info.id)
    setState("working")
    notify(
      "KPKN · Build/test",
      `Iniciando: ${command.slice(0, 80)}`,
      "permission",
    )
  })

  api.event.on("pty.exited", (event) => {
    if (!activeBuildPty.has(event.properties.id)) return
    activeBuildPty.delete(event.properties.id)
    const ok = event.properties.exitCode === 0
    notify(
      ok ? "KPKN · Build/test OK" : "KPKN · Build/test con errores",
      ok ? "El build/test terminó correctamente." : `Exit code ${event.properties.exitCode}.`,
      ok ? "done" : "error",
    )
    markDone()
  })

  // Limpieza al cerrar la pestaña.
  lifecycle.onDispose(() => {
    if (doneTimer) clearTimeout(doneTimer)
    process.stdout.write(OSC_RESET_TAB)
  })
}

export default { id: "status-beacon", tui: StatusBeacon }