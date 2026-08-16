import type { Plugin } from "@opencode-ai/plugin"
import { existsSync, readFileSync } from "node:fs"
import { join, resolve } from "node:path"

const ZONES = [
  { flag: "voice", path: "services/workout/" },
  { flag: "room", path: "data/db/" },
  { flag: "room", path: "app/schemas/" },
  { flag: "auge", path: "domain/auge/" },
  { flag: "nutrition", path: "domain/nutrition/" },
  { flag: "ios", path: "ios-native/" },
  { flag: "backend", path: "backend/" },
]

const REQUIRED_SECTIONS = ["## rutas", "## impacto", "## pruebas", "## riesgos"]
const TEST_RE = /testBaseDebugUnitTest|gradlew(?:\.bat)?[\s\S]{0,120}\btest\b|run-gradle\.ps1[\s\S]{0,120}\btest\b/i
const EDIT_TOOLS = new Set(["edit", "write", "patch"])

const state = { lastProductEditAt: 0, lastTestPassAt: 0 }

function toPosix(p: string): string {
  return p.replace(/\\/g, "/").toLowerCase()
}

function isMetaPath(p: string): boolean {
  const q = p.startsWith("/") ? p : `/${p}`
  return (
    q.includes("/.opencode/") ||
    q.includes("/docs/") ||
    q.endsWith("/agents.md") ||
    q.endsWith("/readme.md")
  )
}

function readPipeline(directory: string): { stage?: string; plan?: string } | null {
  const p = join(directory, ".opencode", "pipeline.json")
  if (!existsSync(p)) return null
  try {
    const j = JSON.parse(readFileSync(p, "utf8")) as { stage?: string; plan?: string }
    return { stage: j.stage, plan: j.plan }
  } catch {
    return null
  }
}

function planFlags(directory: string, planRel: string | undefined): Set<string> {
  if (!planRel) return new Set()
  const p = join(directory, planRel)
  if (!existsSync(p)) return new Set()
  const txt = readFileSync(p, "utf8")
  const fm = txt.match(/^---\s*([\s\S]*?)^---/m)
  if (!fm) return new Set()
  const flagsLine = fm[1].match(/^flags:\s*(.+)$/mi)
  if (!flagsLine) return new Set()
  const raw = flagsLine[1].replace(/[[\]"']/g, "")
  return new Set(
    raw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean),
  )
}

function checkPlanSections(directory: string, planRel: string | undefined): string[] {
  if (!planRel) return ["plan no definido en pipeline"]
  const p = join(directory, planRel)
  if (!existsSync(p)) return [`plan no existe: ${planRel}`]
  const txt = readFileSync(p, "utf8")
  const lower = txt.toLowerCase()
  const missing = REQUIRED_SECTIONS.filter((s) => !lower.includes(s))
  if (!/^---[\s\S]*?^flags:/m.test(txt)) missing.push("frontmatter flags: requerido")
  return missing
}

const KpknGatePlugin: Plugin = async ({ directory, client }) => {
  const root = directory ?? process.cwd()

  const log = (level: "info" | "warn" | "error", message: string) => {
    try {
      void client.app.log({ body: { service: "kpkn-gate", level, message } })
    } catch {
      // logging nunca debe romper el plugin
    }
  }

  return {
    "tool.execute.before": async (input, output) => {
      const args = (output.args ?? {}) as Record<string, unknown>

      if (EDIT_TOOLS.has(input.tool)) {
        const filePath = args.filePath
        if (typeof filePath !== "string") return
        const posix = toPosix(resolve(root, filePath))
        if (isMetaPath(posix)) return

        const pipe = readPipeline(root)
        if (!pipe) return // sin pipeline: flujo ad-hoc sin compuertas

        if (pipe.stage !== "construction") {
          log("warn", `bloqueado edit de producto en etapa "${pipe.stage}": ${filePath}`)
          throw new Error(
            `kpkn-gate: la etapa "${pipe.stage}" no permite editar código de producto (${filePath}). Solo la etapa "construction" acepta cambios.`,
          )
        }

        const flags = planFlags(root, pipe.plan)
        for (const z of ZONES) {
          if (posix.includes(z.path) && !flags.has(z.flag)) {
            log("warn", `bloqueado edit en zona ${z.flag} sin bandera: ${filePath}`)
            throw new Error(
              `kpkn-gate: ${filePath} está en la zona "${z.flag}" pero el plan no declara la bandera (flags: [${[...flags].join(", ")}]). Añade la bandera al frontmatter del plan.`,
            )
          }
        }
        state.lastProductEditAt = Date.now()
      }

      if (input.tool === "pipeline") {
        const rawAction = typeof args.action === "string" ? args.action : ""
        const action = rawAction.split(".").pop()?.toLowerCase() ?? ""

        if (action === "request_approval") {
          const pipe = readPipeline(root)
          if (!pipe) throw new Error("kpkn-gate: request_approval requiere pipeline iniciado.")
          const missing = checkPlanSections(root, pipe.plan)
          if (missing.length > 0) {
            log("warn", `request_approval bloqueado: plan incompleto (${missing.join(", ")})`)
            throw new Error(
              `kpkn-gate: el plan no es aprobable. Falta: ${missing.join(", ")}.`,
            )
          }
        }

        if (action === "submit_audit") {
          if (state.lastProductEditAt > state.lastTestPassAt) {
            log("warn", "submit_audit bloqueado: sin test exitoso tras el último cambio")
            throw new Error(
              "kpkn-gate: submit_audit requiere al menos un test dirigido exitoso (BUILD SUCCESSFUL) después del último cambio de producto.",
            )
          }
        }
      }
    },

    "tool.execute.after": async (input, output) => {
      if (input.tool !== "bash") return
      const cmd = typeof input.args?.command === "string" ? input.args.command : ""
      if (TEST_RE.test(cmd) && /BUILD SUCCESSFUL/i.test(output.output)) {
        state.lastTestPassAt = Date.now()
      }
    },
  }
}

export default { id: "kpkn-gate", server: KpknGatePlugin }