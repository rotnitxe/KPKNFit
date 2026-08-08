import { spawn } from "node:child_process"
import type { AgentPlugin } from "@cline/sdk"

/**
 * Cline — Status Beacon
 *
 * Extiende la ventana de Cline (Windows Terminal) con:
 *   1. Notificación + sonido al iniciar/terminar un build/test y al terminar
 *      una tarea (run).
 *   2. Color de pestaña según el estado de la sesión:
 *        - Azul   (libre / sin tareas)
 *        - Amarillo (en proceso)
 *        - Verde  (terminado / build-test completado)
 *
 * La pestaña de color usa la secuencia OSC 9;4 de Windows Terminal. Las
 * notificaciones y sonidos se delegan a un helper PowerShell reutilizable
 * (Send-KpknNotify del módulo kpkn-launcher).
 */

const COLORS = {
  free: "#3b88c3", // azul
  working: "#c9a227", // amarillo
  done: "#28a745", // verde
} as const

// Secuencia OSC para Windows Terminal: fijar color de pestaña
const OSC_TAB = "\x1b]9;4;3;HEX\x1b\\"
const OSC_RESET_TAB = "\x1b]9;4;0\x1b\\"

// Tiempo en ms que la pestaña queda "verde" antes de volver a "azul" (libre)
const DONE_HOLD_MS = 4000

// Detección de comandos de build/test (gradle / npm test / pytest / etc.)
const BUILD_TEST_RE =
  /(gradlew?\.bat|gradlew|gradle|assembleDebug|assembleRelease|installDebug|test(?:Base|Debug)?\w*|mvn\b|pytest\b|npm\s+run\s+(?:test|build)|\.*test)/i

type BeaconState = "free" | "working" | "done"

function paintTab(state: BeaconState) {
  const hex = COLORS[state]
  process.stdout.write(OSC_TAB.replace("HEX", hex))
}

function notify(title: string, message: string, sound: string) {
  // Delegar a PowerShell para la notificación + sonido (no bloquea el run).
  try {
    const ps = spawn(
      "powershell.exe",
      [
        "-NoProfile",
        "-NonInteractive",
        "-Command",
        `Import-Module 'C:\\Users\\valen\\Documents\\KPKNFit\\.opencode\\scripts\\kpkn-launcher.psm1' -Force; Send-KpknNotify -Title 'KPKN · Cline' -Message '${message.replace(/'/g, "''")}' -Sound ${sound}`,
      ],
      { stdio: "ignore", detached: true },
    )
    ps.unref()
  } catch {
    // Si no se puede lanzar el helper, no bloquear el run.
  }
}

const plugin: AgentPlugin = {
  name: "kpkn-status-beacon",
  manifest: {
    capabilities: ["hooks"],
  },

  hooks: {
    beforeRun() {
      // Una tarea (run) comenzó -> amarillo.
      paintTab("working")
    },

    afterRun(context) {
      // La tarea terminó -> verde + notificación + sonido.
      const status = context.result?.status
      const ok = status !== "failed" && status !== "aborted"
      notify(
        ok ? "KPKN · Tarea terminada" : "KPKN · Tarea con errores",
        ok ? "El chat terminó y quedó libre." : `Estado final: ${status ?? "unknown"}.`,
        ok ? "done" : "error",
      )
      paintTab("done")
      setTimeout(() => paintTab("free"), DONE_HOLD_MS)
    },

    beforeTool(context) {
      const toolName = context.toolCall?.toolName
      const input = context.input as { command?: string } | string | undefined
      const command =
        (typeof input === "string" ? input : input?.command) ?? ""
      if (toolName === "bash" && BUILD_TEST_RE.test(command)) {
        paintTab("working")
        notify(
          "KPKN · Build/test",
          `Iniciando: ${command.slice(0, 80)}`,
          "permission",
        )
      }
    },

    afterTool(context) {
      const toolName = context.toolCall?.toolName
      const input = context.input as { command?: string } | string | undefined
      const command =
        (typeof input === "string" ? input : input?.command) ?? ""
      if (toolName !== "bash" || !BUILD_TEST_RE.test(command)) return
      const ok = !context.result?.isError
      notify(
        ok ? "KPKN · Build/test OK" : "KPKN · Build/test con errores",
        ok
          ? "El build/test terminó correctamente."
          : "El build/test terminó con errores.",
        ok ? "done" : "error",
      )
      paintTab("done")
      setTimeout(() => paintTab("free"), DONE_HOLD_MS)
    },
  },
}

export default plugin