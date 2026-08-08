import type { Plugin } from "@opencode-ai/plugin"

const GRADLE_RE = /(gradlew\.bat|gradlew|gradle)(?!\.ps1)/i
const WRAPPER_MARKER = "run-gradle.ps1"
const NO_DAEMON_FLAGS = "--no-daemon --console=plain --warning-mode=summary -Dorg.gradle.daemon=false"

function needsFlags(cmd: string): boolean {
  return !cmd.includes("--no-daemon")
}

function injectFlags(cmd: string): string {
  // Inject after every gradlew(.bat) occurrence, preserve following args
  // 1) gradlew.bat <args>  -> gradlew.bat --no-daemon ... <args>
  // 2) gradlew test (sin .bat)
  let out = cmd
  // Caso con .bat seguido de espacio
  out = out.replace(/(gradlew\.bat)(\s+)/gi, `$1 ${NO_DAEMON_FLAGS}$2`)
  // Caso .bat al final o antes de pipes/semicolon
  out = out.replace(/(gradlew\.bat)(?=\s*(?:\||;|&&|$|"))/gi, `$1 ${NO_DAEMON_FLAGS}`)
  // Caso gradlew sin .bat (Linux/mac fallback)
  out = out.replace(/(?<!\.bat)(\bgradlew\b)(\s+)/gi, `$1 ${NO_DAEMON_FLAGS}$2`)
  out = out.replace(/(?<!\.bat)(\bgradlew\b)(?=\s*(?:\||;|&&|$|"))/gi, `$1 ${NO_DAEMON_FLAGS}`)
  // Evitar duplicar si ya contenía flags
  out = out.replace(new RegExp(`(\\s${NO_DAEMON_FLAGS.replace(/-/g, "\\-").replace(/\s+/g, "\\s+")}){2,}`, "g"), ` ${NO_DAEMON_FLAGS}`)
  return out
}

const GradleGuardPlugin: Plugin = async () => ({
  "tool.execute.before": async (input, output) => {
    if (input.tool !== "bash") return
    const args = output.args as Record<string, unknown> | undefined
    if (!args || typeof args.command !== "string") return
    const cmd: string = args.command

    if (!GRADLE_RE.test(cmd)) return
    if (cmd.includes(WRAPPER_MARKER)) return

    // No tocar comandos que ya piden stop/status
    if (cmd.includes("--stop") || cmd.includes("--status")) return

    let mutated = cmd
    if (needsFlags(mutated)) {
      mutated = injectFlags(mutated)
    }

    // Asegurar timeout suficiente: builds largos necesitan 10min, tests 5min
    const isLong = /assemble|build|bundle|install/i.test(mutated)
    const desiredTimeout = isLong ? 600_000 : 300_000
    const currentTimeout = typeof args.timeout === "number" ? args.timeout : 120_000
    if (currentTimeout < desiredTimeout) {
      ;(args as Record<string, unknown>).timeout = desiredTimeout
    }

    // Si el comando es un simple "gradlew.bat <task>" sin redirecciones complejas,
    // reescribir al wrapper anti-hang que usa Start-Process + cmd /c + log a archivo.
    // Esto evita el bug de pwsh que espera handles del daemon para siempre.
    const simpleGradle = /^\s*(?:\.\\gradlew\.bat|gradlew\.bat|gradlew)\s+[\w:.-]+(?:\s+[\w:.-]+)*\s*$/i.test(cmd.trim())
    const hasComplexPipe = /[|&;]/.test(cmd)
    if (simpleGradle && !hasComplexPipe) {
      // Extraer tasks después de gradlew
      const m = cmd.trim().match(/gradlew(?:\.bat)?\s+(.+)$/i)
      const tasks = m?.[1]?.replace(NO_DAEMON_FLAGS, "").trim() ?? cmd.trim()
      const workdir = typeof args.workdir === "string" ? args.workdir : "android-native"
      // workdir puede ser absoluto; normalizar para pwsh
      mutated = `powershell -NoProfile -ExecutionPolicy Bypass -File ".opencode/scripts/run-gradle.ps1" -Tasks "${tasks.replace(/"/g, '`"')}" -WorkDir "${workdir}" -TimeoutSec ${Math.ceil(desiredTimeout / 1000)}`
    } else if (mutated !== cmd) {
      // Para comandos complejos (con pipes/grep), solo inyectamos flags
    }

    if (mutated !== cmd) {
      args.command = mutated
    }
  },

  "shell.env": async (_input, output) => {
    // Redundancia: forzar daemon off también vía env para cualquier invocación que escape al guard
    // GRADLE_OPTS se respeta por el launcher
    output.env["GRADLE_OPTS"] = `${output.env["GRADLE_OPTS"] ?? ""} -Dorg.gradle.daemon=false`.trim()
    // Evitar que Kotlin daemon quede colgado con handles
    output.env["KOTLIN_DAEMON_JVM_OPTIONS"] = output.env["KOTLIN_DAEMON_JVM_OPTIONS"] ?? "-Xmx512m"
  },
})

export default { id: "gradle-guard", server: GradleGuardPlugin }
