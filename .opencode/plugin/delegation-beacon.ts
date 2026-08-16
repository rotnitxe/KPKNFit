import type { Plugin } from "@opencode-ai/plugin"
import { appendFileSync, mkdirSync } from "node:fs"
import { join } from "node:path"

/**
 * Delegation Beacon — registra cada invocación del tool `task` (delegación a
 * subagentes) en `.opencode/delegation.log.jsonl`.
 *
 * Sirve para auditar que el Orquestador (modelo caro) realmente delega a los
 * subagentes baratos en vez de hacer él mismo el trabajo de lectura/verificación.
 */

export const DelegationBeacon: Plugin = async ({ directory }) => {
  const root = directory ?? process.cwd()
  const logDir = join(root, ".opencode")
  const logPath = join(logDir, "delegation.log.jsonl")

  const append = (rec: Record<string, unknown>) => {
    try {
      mkdirSync(logDir, { recursive: true })
      appendFileSync(logPath, JSON.stringify(rec) + "\n")
    } catch {
      // nunca romper la sesión por un log
    }
  }

  return {
    "tool.execute.after": async (input) => {
      if (input.tool !== "task") return
      const args = (input.args ?? {}) as Record<string, unknown>
      const subagent = typeof args.subagent_type === "string" ? args.subagent_type : "?"
      const prompt = typeof args.prompt === "string" ? args.prompt.slice(0, 300) : ""
      const description = typeof args.description === "string" ? args.description : ""
      append({
        ts: new Date().toISOString(),
        sessionID: input.sessionID,
        subagent,
        description,
        prompt: prompt.slice(0, 160),
      })
    },
  }
}

export default { id: "delegation-beacon", server: DelegationBeacon }