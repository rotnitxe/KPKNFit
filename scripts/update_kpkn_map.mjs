import { readdir, readFile, rename, stat, writeFile } from "node:fs/promises"
import path from "node:path"

const scriptPath = decodeURIComponent(new URL(import.meta.url).pathname).replace(/^\/+([A-Za-z]:)/, "$1")
const normalizedRoot = path.resolve(path.dirname(scriptPath), "..")
const mapPath = path.join(normalizedRoot, ".opencode", "kpkn-map.md")

async function files(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(
    entries
      .filter((entry) => ![".git", "build", "buildSrc", "node_modules"].includes(entry.name))
      .map(async (entry) => {
        const filepath = path.join(directory, entry.name)
        if (entry.isDirectory()) return files(filepath)
        if (entry.isFile() && entry.name.endsWith(".kt")) return [filepath]
        return []
      }),
  )
  return nested.flat()
}

const sourceRoot = path.join(normalizedRoot, "android-native", "app", "src", "main", "java", "com", "example", "kpkn")
const kotlinFiles = await files(sourceRoot).catch(() => [])
const contents = await Promise.all(kotlinFiles.map(async (filepath) => [filepath, await readFile(filepath, "utf8")]))
const relative = (filepath) => path.relative(normalizedRoot, filepath).split(path.sep).join("/")
const names = (expression) =>
  [...new Set(contents.flatMap(([, content]) => [...content.matchAll(expression)].map((match) => match[1])))]
    .filter(Boolean)
    .sort()

const database = contents.find(([filepath]) => filepath.endsWith("KpknDatabase.kt"))?.[1] ?? ""
const version = database.match(/version\s*=\s*(\d+)/)?.[1] ?? "unknown"
const entities = names(/\b(?:data\s+)?class\s+(\w*Entity)\b/g)
const routes = contents
  .flatMap(([, content]) => [...content.matchAll(/\bobject\s+(\w+)\s*:\s*KpknRoute\("([^"]+)"/g)])
  .map((match) => `${match[1]}: ${match[2]}`)
  .sort()
const viewModels = kotlinFiles.filter((filepath) => filepath.endsWith("ViewModel.kt")).map(relative).sort()
const repositories = kotlinFiles
  .filter((filepath) => filepath.includes(`${path.sep}data${path.sep}repository${path.sep}`))
  .map(relative)
  .sort()
const auge = kotlinFiles
  .filter((filepath) => filepath.includes(`${path.sep}domain${path.sep}auge${path.sep}`))
  .map(relative)
  .sort()

const list = (items) => (items.length ? items.map((item) => `- ${item}`).join("\n") : "- None detected")
const generated = [
  "<!-- KAUPOLIKAN_DYNAMIC_MAP_START -->",
  `Generated at: ${new Date().toISOString()}`,
  `Kotlin files: ${kotlinFiles.length}`,
  `Room version detected in KpknDatabase.kt: ${version}`,
  "",
  "### Entities",
  list(entities),
  "",
  "### Routes",
  list(routes),
  "",
  "### ViewModels",
  list(viewModels),
  "",
  "### Repositories",
  list(repositories),
  "",
  "### AUGE files",
  list(auge),
  "<!-- KAUPOLIKAN_DYNAMIC_MAP_END -->",
].join("\n")

const existing = await readFile(mapPath, "utf8").catch(() => "# KPKN Project Map\n")
const next = existing.replace(/<!-- KAUPOLIKAN_DYNAMIC_MAP_START -->[\s\S]*?<!-- KAUPOLIKAN_DYNAMIC_MAP_END -->\n?/u, "").trimEnd() + `\n\n${generated}\n`
const temporary = `${mapPath}.${process.pid}.tmp`
await writeFile(temporary, next, "utf8")
await rename(temporary, mapPath)
await stat(mapPath)
