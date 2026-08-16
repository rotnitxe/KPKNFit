import type { Plugin } from "@opencode-ai/plugin"
import { existsSync, readFileSync } from "node:fs"
import { join } from "node:path"

/**
 * Audit Loop — replicación de "Goal mode" para KPKN sin intervención manual.
 *
 * Escucha las transiciones del pipeline (tool `pipeline`) y encadena sesiones:
 *
 *   constructor_kpkn ──submit_audit──▶ auditor (sesión nueva)
 *        ▲                                    │ request_corrections
 *        └────────────── reanudar ◀───────────┘ (constructor corregido)
 *                                   │ accept
 *                                   ▼
 *                            bucle terminado
 *
 * El usuario solo interviene una vez (aprobar el plan). Todo lo demás corre solo.
 */

const MAX_LOOP_ITERATIONS = 5

export const AuditLoop: Plugin = async ({ directory, client }) => {
  const root = directory ?? process.cwd()
  const pipelinePath = join(root, ".opencode", "pipeline.json")
  const state = {
    auditRuns: 0,
    lastHandled: "",
    currentPlan: "",
    spawned: new Set<string>(),
  }

  const log = (level: "info" | "warn" | "error", message: string) => {
    try {
      void client.app.log({ body: { service: "audit-loop", level, message } })
    } catch {
      // el log nunca debe romper el flujo
    }
  }

  const pipeline = (): {
    stage?: string
    plan?: string
    transitions: { action?: string; actor?: string; at?: string }[]
  } | null => {
    if (!existsSync(pipelinePath)) return null
    try {
      const j = JSON.parse(readFileSync(pipelinePath, "utf8")) as {
        stage?: string
        plan?: string
        transitions?: { action?: string; actor?: string; at?: string }[]
      }
      return { stage: j.stage, plan: j.plan, transitions: j.transitions ?? [] }
    } catch {
      return null
    }
  }

  const spawn = async (agent: string, prompt: string): Promise<string | null> => {
    try {
      const created = await client.session.create({
        query: { directory: root },
        body: { title: `KPKN · bucle: ${agent}` },
      })
      const id = (created as { data?: { id?: string } })?.data?.id
      if (!id) throw new Error("la sesión no devolvió id")
      await client.session.promptAsync({
        path: { id },
        body: { agent, parts: [{ type: "text", text: prompt }] },
      })
      state.spawned.add(id)
      return id
    } catch (e) {
      log("error", `no se pudo lanzar ${agent}: ${String(e)}`)
      return null
    }
  }

  return {
    "tool.execute.after": async (input) => {
      if (input.tool !== "pipeline") return
      const args = (input.args ?? {}) as Record<string, unknown>
      const rawAction = typeof args.action === "string" ? args.action : ""
      const action = rawAction.split(".").pop()?.toLowerCase() ?? ""
      if (!["submit_audit", "request_corrections", "accept"].includes(action)) return

      // Verificar que la transición quedó realmente registrada (si el gate
      // bloqueó la llamada, el pipeline no cambió y no encadenamos nada).
      const pipe = pipeline()
      const last = pipe?.transitions?.at(-1)
      const actor = last?.actor ?? ""
      const lastAction = last?.action?.split(".").pop()?.toLowerCase() ?? ""
      if (lastAction !== action) return

      const signature = `${action}|${actor}|${last?.at ?? ""}`
      if (signature === state.lastHandled) return
      state.lastHandled = signature

      if (action === "submit_audit" && actor === "constructor_kpkn") {
        // ciclo nuevo (otro plan): reiniciar el contador de iteraciones
        const plan = pipe?.plan ?? ""
        if (plan !== state.currentPlan) {
          state.currentPlan = plan
          state.auditRuns = 0
        }
        if (state.auditRuns >= MAX_LOOP_ITERATIONS) {
          log("warn", "límite de iteraciones alcanzado; bucle detenido, revisa manualmente")
          return
        }
        state.auditRuns++
        const id = await spawn(
          "auditor",
          [
            "Loop KPKN (automático): el Constructor hizo submit_audit del pipeline actual.",
            "Revisa el diff contra el plan aprobado y ejecuta tu flujo normal de auditor.",
            "Escribe el reporte en .opencode/audits/ y devuelve el veredicto con el tool pipeline:",
            "  - accept si todo cumple,",
            "  - request_corrections si hay hallazgos.",
          ].join("\n"),
        )
        log("info", `auditor lanzado: ${id ?? "falló"} (ronda ${state.auditRuns})`)
      } else if (action === "request_corrections") {
        const id = await spawn(
          "constructor_kpkn",
          [
            "Loop KPKN (automático): el Auditor pidió correcciones.",
            "Lee el reporte más reciente en .opencode/audits/ y el estado del pipeline.",
            "Usa resume_construction, corrige los hallazgos, valida con tests y vuelve a submit_audit cuando termines.",
          ].join("\n"),
        )
        log("info", `constructor reanudado: ${id ?? "falló"}`)
      } else if (action === "accept") {
        log("info", "el auditor aprobó; bucle terminado")
      }
    },
  }
}

export default { id: "audit-loop", server: AuditLoop }