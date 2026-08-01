import { mkdir, readFile, writeFile } from "node:fs/promises"
import path from "node:path"
import type { Part } from "@opencode-ai/sdk"
import type { Plugin } from "@opencode-ai/plugin"

const PRIMARY_AGENTS = new Set(["orquestador", "constructor_kpkn", "auditor", "plan", "build"])
const NUDGE_INTERVAL = 10

type State = {
  turns: number
  updatedAt: string
}

type SkillUsage = Record<string, { useCount: number; lastUsedAt: string }>

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

async function readState(filepath: string): Promise<State> {
  const raw = await readFile(filepath, "utf8").catch(() => "")
  if (!raw) return { turns: 0, updatedAt: new Date(0).toISOString() }
  try {
    const parsed: unknown = JSON.parse(raw)
    if (object(parsed) && typeof parsed.turns === "number") {
      return { turns: parsed.turns, updatedAt: typeof parsed.updatedAt === "string" ? parsed.updatedAt : "" }
    }
  } catch {
    // Recreate a corrupt local learning counter rather than blocking a coding turn.
  }
  return { turns: 0, updatedAt: new Date(0).toISOString() }
}

const LearningLoopPlugin: Plugin = async (input) => ({
  "chat.message": async (event, output) => {
    if (!event.messageID || !event.agent || !PRIMARY_AGENTS.has(event.agent)) return

    const statePath = path.join(input.worktree, ".opencode", "learning-state.json")
    const state = await readState(statePath)
    const next: State = { turns: state.turns + 1, updatedAt: new Date().toISOString() }
    await mkdir(path.dirname(statePath), { recursive: true })
    await writeFile(statePath, `${JSON.stringify(next, null, 2)}\n`, "utf8")

    if (next.turns % NUDGE_INTERVAL !== 0) return

    const checkpoint: Part = {
      id: crypto.randomUUID(),
      sessionID: event.sessionID,
      messageID: event.messageID,
      type: "text",
      synthetic: true,
      text: [
        "KAUPOLIKAN self-improvement checkpoint.",
        "Review this session for durable corrections, project facts, or reusable KPKN procedures.",
        "Use memory for concise durable knowledge and skill_manage to create or patch a reusable SKILL.md.",
        "Do not save secrets, transient details, or generic advice. Keep the project memory within its character limit.",
      ].join(" "),
    }
    output.parts.push(checkpoint)
  },

  "tool.execute.after": async (event) => {
    if (event.tool !== "skill" || !object(event.args) || typeof event.args.name !== "string") return
    const usagePath = path.join(input.worktree, ".opencode", "skills", ".usage.json")
    const raw = await readFile(usagePath, "utf8").catch(() => "")
    let usage: SkillUsage = {}
    try {
      const parsed: unknown = raw ? JSON.parse(raw) : {}
      if (object(parsed)) {
        usage = Object.fromEntries(
          Object.entries(parsed).flatMap(([name, value]) => {
            if (!object(value) || typeof value.useCount !== "number") return []
            return [[name, { useCount: value.useCount, lastUsedAt: typeof value.lastUsedAt === "string" ? value.lastUsedAt : "" }]]
          }),
        )
      }
    } catch {
      usage = {}
    }

    const previous = usage[event.args.name] ?? { useCount: 0, lastUsedAt: "" }
    usage[event.args.name] = { useCount: previous.useCount + 1, lastUsedAt: new Date().toISOString() }
    await mkdir(path.dirname(usagePath), { recursive: true })
    await writeFile(usagePath, `${JSON.stringify(usage, null, 2)}\n`, "utf8")
  },
})

export default { id: "learning-loop", server: LearningLoopPlugin }
