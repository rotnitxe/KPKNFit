const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '../app/src/main/assets/wikilab')
const musclesPath = path.join(root, 'muscles.json')
const jointsPath = path.join(root, 'joints.json')
const tendonsPath = path.join(root, 'tendons.json')
const patternsPath = path.join(root, 'movement_patterns.json')
const chainsPath = path.join(root, 'kinetic_chains.json')

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'))
}

function add(issues, severity, file, id, message) {
  issues.push({ severity, file, id, message })
}

function checkRequired(issues, file, item, idField, fields) {
  for (const field of fields) {
    if (item[field] == null || item[field] === '' || (Array.isArray(item[field]) && item[field].length === 0)) {
      add(issues, 'warn', file, item[idField], `Missing or empty field: ${field}`)
    }
  }
}

function checkMinLength(issues, file, item, idField, fieldsWithMin) {
  for (const [field, min] of fieldsWithMin) {
    const text = String(item[field] || '').trim()
    if (text.length < min) {
      add(issues, 'warn', file, item[idField], `Field too short (${field}=${text.length}, min=${min})`)
    }
  }
}

function checkDuplicateIds(issues, file, items, idField) {
  const seen = new Set()
  for (const item of items) {
    const id = item[idField]
    if (seen.has(id)) add(issues, 'error', file, id, 'Duplicate id')
    seen.add(id)
  }
}

function mapById(items, idField = 'id') {
  return new Map(items.map((x) => [x[idField], x]))
}

function run() {
  const muscles = readJson(musclesPath)
  const joints = readJson(jointsPath)
  const tendons = readJson(tendonsPath)
  const patterns = readJson(patternsPath)
  const chains = readJson(chainsPath)

  const issues = []

  checkDuplicateIds(issues, 'muscles.json', muscles, 'id')
  checkDuplicateIds(issues, 'joints.json', joints, 'id')
  checkDuplicateIds(issues, 'tendons.json', tendons, 'id')
  checkDuplicateIds(issues, 'movement_patterns.json', patterns, 'id')
  checkDuplicateIds(issues, 'kinetic_chains.json', chains, 'id')

  const muscleIds = new Set(muscles.map((x) => x.id))
  const jointIds = new Set(joints.map((x) => x.id))
  const tendonIds = new Set(tendons.map((x) => x.id))
  const patternIds = new Set(patterns.map((x) => x.id))

  for (const m of muscles) {
    checkRequired(issues, 'muscles.json', m, 'id', ['name', 'description', 'importanceMovement', 'importanceHealth'])
    checkMinLength(issues, 'muscles.json', m, 'id', [
      ['description', 90],
      ['importanceMovement', 55],
      ['importanceHealth', 55]
    ])

    for (const id of m.relatedJoints || []) {
      if (!jointIds.has(id)) add(issues, 'error', 'muscles.json', m.id, `Invalid relatedJoints ref: ${id}`)
    }
    for (const id of m.relatedTendons || []) {
      if (!tendonIds.has(id)) add(issues, 'error', 'muscles.json', m.id, `Invalid relatedTendons ref: ${id}`)
    }
  }

  for (const j of joints) {
    checkRequired(issues, 'joints.json', j, 'id', ['name', 'type', 'description'])
    checkMinLength(issues, 'joints.json', j, 'id', [['description', 95]])

    for (const id of j.musclesCrossing || []) {
      if (!muscleIds.has(id)) add(issues, 'error', 'joints.json', j.id, `Invalid musclesCrossing ref: ${id}`)
    }
    for (const id of j.tendonsRelated || []) {
      if (!tendonIds.has(id)) add(issues, 'error', 'joints.json', j.id, `Invalid tendonsRelated ref: ${id}`)
    }
    for (const id of j.movementPatterns || []) {
      if (!patternIds.has(id)) add(issues, 'error', 'joints.json', j.id, `Invalid movementPatterns ref: ${id}`)
    }
  }

  for (const t of tendons) {
    checkRequired(issues, 'tendons.json', t, 'id', ['name', 'description', 'muscleId', 'jointId'])
    checkMinLength(issues, 'tendons.json', t, 'id', [['description', 85]])
    if (t.muscleId && !muscleIds.has(t.muscleId)) add(issues, 'error', 'tendons.json', t.id, `Invalid muscleId ref: ${t.muscleId}`)
    if (t.jointId && !jointIds.has(t.jointId)) add(issues, 'error', 'tendons.json', t.id, `Invalid jointId ref: ${t.jointId}`)
  }

  for (const p of patterns) {
    checkRequired(issues, 'movement_patterns.json', p, 'id', ['name', 'description', 'forceTypes', 'chainTypes'])
    checkMinLength(issues, 'movement_patterns.json', p, 'id', [['description', 95]])
    for (const id of p.primaryMuscles || []) {
      if (!muscleIds.has(id)) add(issues, 'error', 'movement_patterns.json', p.id, `Invalid primaryMuscles ref: ${id}`)
    }
    for (const id of p.primaryJoints || []) {
      if (!jointIds.has(id)) add(issues, 'error', 'movement_patterns.json', p.id, `Invalid primaryJoints ref: ${id}`)
    }
  }

  for (const c of chains) {
    checkRequired(issues, 'kinetic_chains.json', c, 'id', ['name', 'description', 'importance', 'muscles'])
    checkMinLength(issues, 'kinetic_chains.json', c, 'id', [
      ['description', 90],
      ['importance', 90]
    ])
    for (const muscleName of c.muscles || []) {
      const found = muscles.some((m) => m.name.toLowerCase() === String(muscleName).toLowerCase())
      if (!found) {
        add(issues, 'warn', 'kinetic_chains.json', c.id, `muscles[] entry not found by name: ${muscleName}`)
      }
    }
  }

  const counts = {
    errors: issues.filter((x) => x.severity === 'error').length,
    warnings: issues.filter((x) => x.severity === 'warn').length
  }

  if (issues.length === 0) {
    console.log('WikiLab content lint: OK (no issues)')
    process.exit(0)
  }

  console.log(`WikiLab content lint: ${counts.errors} errors, ${counts.warnings} warnings`)
  for (const issue of issues) {
    console.log(`[${issue.severity.toUpperCase()}] ${issue.file} :: ${issue.id} :: ${issue.message}`)
  }

  process.exit(counts.errors > 0 ? 1 : 0)
}

run()
