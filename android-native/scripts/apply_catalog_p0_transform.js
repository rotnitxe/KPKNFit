/**
 * Aplica transforms CURADOS del plan de catálogo (no clustering).
 * Oleada P0: Remo T, Remo barra/smith/máquina/polea/gironda/banda,
 * Press banca, nonsense martillo+barra/EZ.
 */
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const dbPath = path.join(root, "app/src/main/assets/exercise_database.json");
const aliasPath = path.join(root, "app/src/main/assets/exercise_id_aliases.json");
const auditPath = path.join(root, "../docs/EXERCISE_CATALOG_AUDIT.md");

const db = JSON.parse(fs.readFileSync(dbPath, "utf8"));
const aliases = JSON.parse(fs.readFileSync(aliasPath, "utf8"));
const byId = new Map(db.map((e) => [e.id, e]));

const removeIds = new Set();
const aliasAdds = {};
const applied = [];

function gripWidthAspect({ withMedium = true } = {}) {
  const options = [];
  if (withMedium) {
    options.push({
      id: "medium",
      name: "Medio",
      description: "Amplitud estándar.",
      modifiers: [],
    });
  }
  options.push(
    {
      id: "wide",
      name: "Amplio",
      description: "Agarre amplio.",
      modifiers: [],
    },
    {
      id: "close",
      name: "Cerrado",
      description: "Agarre cerrado.",
      modifiers: [],
    },
  );
  return {
    id: "grip_width",
    name: "Amplitud",
    description: "Ancho del agarre.",
    defaultOptionId: withMedium ? "medium" : "wide",
    options,
  };
}

function gripOrientationAspect(defaultId = "prono") {
  return {
    id: "grip_orientation",
    name: "Tipo de agarre",
    description: "Orientación de las manos.",
    defaultOptionId: defaultId,
    options: [
      { id: "prono", name: "Prono", description: "Palmas abajo.", modifiers: [] },
      { id: "supino", name: "Supino", description: "Palmas arriba.", modifiers: [] },
      { id: "neutro", name: "Neutro", description: "Agarre neutro.", modifiers: [] },
    ],
  };
}

function lateralityAspect() {
  return {
    id: "laterality",
    name: "Lateralidad",
    defaultOptionId: "bilateral",
    options: [
      { id: "bilateral", name: "Bilateral", modifiers: [] },
      { id: "unilateral", name: "Unilateral", modifiers: [] },
    ],
  };
}

function benchGripWidthAspect() {
  return {
    id: "grip_width",
    name: "Amplitud",
    description: "Cerrado aumenta estímulo de tríceps.",
    defaultOptionId: "medium",
    options: [
      {
        id: "medium",
        name: "Medio",
        description: "Equilibrio pecho/tríceps.",
        modifiers: [],
      },
      {
        id: "wide",
        name: "Amplio",
        description: "Más pecho.",
        modifiers: [
          { muscle: "Pectorales", role: "primary", type: "set", value: 1.0 },
        ],
      },
      {
        id: "close",
        name: "Cerrado",
        description: "Más tríceps.",
        modifiers: [
          { muscle: "Tríceps", role: "primary", type: "set", value: 1.0 },
          { muscle: "Pectorales", role: "secondary", type: "set", value: 0.5 },
        ],
      },
    ],
  };
}

function mergeInto(oldId, canonicalId, defaultAspects, note) {
  if (!byId.has(oldId) && oldId !== canonicalId) {
    // already gone
    return;
  }
  if (oldId !== canonicalId) {
    removeIds.add(oldId);
    aliasAdds[oldId] = canonicalId;
  }
  applied.push(`${oldId} → ${canonicalId} ${JSON.stringify(defaultAspects)} (${note})`);
}

function stripVariantGroup(ex) {
  const copy = { ...ex };
  delete copy.variantGroupId;
  delete copy.variantGroupName;
  delete copy.variantName;
  delete copy.variantOrder;
  return copy;
}

function take(id) {
  const ex = byId.get(id);
  if (!ex) throw new Error(`Missing exercise ${id}`);
  return JSON.parse(JSON.stringify(ex));
}

// ─── Remo T ───────────────────────────────────────────────
{
  const base = take("back_remo_barra_t_ancho");
  const canonical = {
    ...base,
    id: "back_remo_barra_t",
    name: "Remo en Barra T",
    description:
      "Remo en barra T. Configura estación (libre o máquina apoyada), amplitud y tipo de agarre.",
    equipment: "Barra T",
    alias:
      "Remo T, T-Bar Row, Remo Barra T Libre, Remo Barra T Máquina, Remo T pecho apoyado",
    technicalAspects: [
      {
        id: "station",
        name: "Estación",
        description: "Libre (hinge) vs máquina apoyada/guiada.",
        defaultOptionId: "libre",
        options: [
          {
            id: "libre",
            name: "Barra T Libre",
            description: "Landmine / pivote sin apoyo de pecho.",
            modifiers: [],
          },
          {
            id: "maquina",
            name: "En Máquina",
            description: "Estación guiada con apoyo.",
            modifiers: [],
          },
        ],
      },
      gripWidthAspect(),
      gripOrientationAspect("prono"),
    ],
  };
  delete canonical.variantGroupId;
  delete canonical.variantGroupName;
  delete canonical.variantName;
  delete canonical.variantOrder;

  const tMerges = [
    ["back_remo_barra_t_ancho", { station: "libre", grip_width: "wide", grip_orientation: "prono" }],
    ["back_remo_barra_t_cerrado", { station: "libre", grip_width: "close", grip_orientation: "prono" }],
    ["back_remo_barra_t_pecho_apoyado_ancho", { station: "maquina", grip_width: "wide", grip_orientation: "prono" }],
    ["back_remo_barra_t_pecho_apoyado_cerrado", { station: "maquina", grip_width: "close", grip_orientation: "prono" }],
    ["back_remo_barra_t_maquina_ancho", { station: "maquina", grip_width: "wide", grip_orientation: "prono" }],
    ["back_remo_barra_t_maquina_cerrado", { station: "maquina", grip_width: "close", grip_orientation: "prono" }],
    ["back_remo_barra_t_maquina_pecho_apoyado_ancho", { station: "maquina", grip_width: "wide", grip_orientation: "prono" }],
    ["back_remo_barra_t_maquina_pecho_apoyado_cerrado", { station: "maquina", grip_width: "close", grip_orientation: "prono" }],
  ];
  for (const [id, aspects] of tMerges) mergeInto(id, "back_remo_barra_t", aspects, "Remo T");
  byId.set("back_remo_barra_t", canonical);
}

// ─── Remo con Barra ───────────────────────────────────────
{
  const base = take("back_remo_barra_recta_ancho");
  const canonical = {
    ...base,
    id: "back_remo_barra",
    name: "Remo con Barra",
    description:
      "Remo con barra. Configura amplitud, tipo de agarre e implemento (recta, EZ o neutra).",
    equipment: "Barra",
    alias: "Remo barra recta, Remo EZ, Remo agarre neutro, Bent Over Row",
    technicalAspects: [
      gripWidthAspect(),
      gripOrientationAspect("prono"),
      {
        id: "implement",
        name: "Implemento",
        defaultOptionId: "recta",
        options: [
          { id: "recta", name: "Barra recta", modifiers: [] },
          { id: "ez", name: "Barra EZ", modifiers: [] },
          { id: "neutra", name: "Barra neutra", modifiers: [] },
        ],
      },
    ],
  };
  const merges = [
    ["back_remo_barra_recta_ancho", { grip_width: "wide", grip_orientation: "prono", implement: "recta" }],
    ["back_remo_barra_recta_cerrado", { grip_width: "close", grip_orientation: "prono", implement: "recta" }],
    ["back_remo_barra_ez", { grip_width: "medium", grip_orientation: "prono", implement: "ez" }],
    ["back_remo_barra_neutra", { grip_width: "medium", grip_orientation: "neutro", implement: "neutra" }],
  ];
  for (const [id, a] of merges) mergeInto(id, "back_remo_barra", a, "Remo barra");
  // Pendlay KEEP_SPECIALTY — strip from any group, keep as-is with light chips
  const pendlay = stripVariantGroup(take("back_remo_pendlay_barra_recta"));
  pendlay.technicalAspects = [gripWidthAspect(), gripOrientationAspect("prono")];
  byId.set(pendlay.id, pendlay);
  byId.set("back_remo_barra", canonical);
}

// ─── Remo Smith ───────────────────────────────────────────
{
  const base = take("back_remo_maquina_smith_ancho");
  const canonical = {
    ...base,
    id: "back_remo_smith",
    name: "Remo en Máquina Smith",
    description: "Remo en Smith. Configura amplitud y tipo de agarre.",
    equipment: "Máquina Smith",
    alias: "Remo Smith agarre ancho, Remo Smith agarre cerrado",
    technicalAspects: [gripWidthAspect(), gripOrientationAspect("prono")],
  };
  mergeInto("back_remo_maquina_smith_ancho", "back_remo_smith", { grip_width: "wide", grip_orientation: "prono" }, "Remo Smith");
  mergeInto("back_remo_maquina_smith_cerrado", "back_remo_smith", { grip_width: "close", grip_orientation: "prono" }, "Remo Smith");
  byId.set("back_remo_smith", canonical);
}

// ─── Remo Máquina (no-T) ──────────────────────────────────
{
  const base = take("back_remo_maquina_ancho");
  const canonical = {
    ...base,
    id: "back_remo_maquina",
    name: "Remo en Máquina",
    description: "Remo en máquina (no Barra T). Amplitud, agarre y lateralidad.",
    equipment: "Máquina",
    alias: "Remo máquina agarre ancho, Remo máquina agarre cerrado",
    technicalAspects: [gripWidthAspect(), gripOrientationAspect("prono"), lateralityAspect()],
  };
  mergeInto("back_remo_maquina_ancho", "back_remo_maquina", { grip_width: "wide", grip_orientation: "prono", laterality: "bilateral" }, "Remo máquina");
  mergeInto("back_remo_maquina_cerrado", "back_remo_maquina", { grip_width: "close", grip_orientation: "prono", laterality: "bilateral" }, "Remo máquina");
  byId.set("back_remo_maquina", canonical);
}

// ─── Remo Polea ───────────────────────────────────────────
{
  const base = take("back_remo_polea_baja_ancho");
  const canonical = {
    ...base,
    id: "back_remo_polea",
    name: "Remo en Polea",
    description: "Remo en polea. Altura, amplitud, tipo de agarre y lateralidad.",
    equipment: "Polea",
    alias:
      "Remo polea baja, Remo polea media, Remo polea alta, Seated Cable Row",
    technicalAspects: [
      {
        id: "cable_height",
        name: "Altura",
        defaultOptionId: "baja",
        options: [
          { id: "baja", name: "Baja", modifiers: [] },
          { id: "media", name: "Media", modifiers: [] },
          { id: "alta", name: "Alta", modifiers: [] },
        ],
      },
      gripWidthAspect(),
      gripOrientationAspect("prono"),
      lateralityAspect(),
    ],
  };
  const poleaMerges = [
    ["back_remo_polea_baja_ancho", { cable_height: "baja", grip_width: "wide", grip_orientation: "prono", laterality: "bilateral" }],
    ["back_remo_polea_baja_cerrado", { cable_height: "baja", grip_width: "close", grip_orientation: "prono", laterality: "bilateral" }],
    ["back_remo_polea_baja_unilateral", { cable_height: "baja", grip_width: "medium", grip_orientation: "prono", laterality: "unilateral" }],
    ["back_remo_polea_media_ancho", { cable_height: "media", grip_width: "wide", grip_orientation: "prono", laterality: "bilateral" }],
    ["back_remo_polea_media_cerrado", { cable_height: "media", grip_width: "close", grip_orientation: "prono", laterality: "bilateral" }],
    ["back_remo_polea_media_unilateral", { cable_height: "media", grip_width: "medium", grip_orientation: "prono", laterality: "unilateral" }],
    ["back_remo_polea_alta_ancho", { cable_height: "alta", grip_width: "wide", grip_orientation: "prono", laterality: "bilateral" }],
    ["back_remo_polea_alta_cerrado", { cable_height: "alta", grip_width: "close", grip_orientation: "prono", laterality: "bilateral" }],
    ["back_remo_polea_alta_unilateral", { cable_height: "alta", grip_width: "medium", grip_orientation: "prono", laterality: "unilateral" }],
  ];
  for (const [id, a] of poleaMerges) mergeInto(id, "back_remo_polea", a, "Remo polea");
  byId.set("back_remo_polea", canonical);
}

// ─── Remo pecho apoyado polea ─────────────────────────────
{
  const base = take("back_remo_pecho_apoyado_polea_baja");
  const canonical = {
    ...base,
    id: "back_remo_pecho_apoyado_polea",
    name: "Remo con Pecho Apoyado en Polea",
    description: "Remo con pecho apoyado en polea. Altura, amplitud, agarre y lateralidad.",
    equipment: "Polea",
    alias: "Chest supported cable row",
    technicalAspects: [
      {
        id: "cable_height",
        name: "Altura",
        defaultOptionId: "baja",
        options: [
          { id: "baja", name: "Baja", modifiers: [] },
          { id: "media", name: "Media", modifiers: [] },
          { id: "alta", name: "Alta", modifiers: [] },
        ],
      },
      gripWidthAspect(),
      gripOrientationAspect("prono"),
      lateralityAspect(),
    ],
  };
  const merges = [
    ["back_remo_pecho_apoyado_polea_baja", { cable_height: "baja", laterality: "bilateral" }],
    ["back_remo_pecho_apoyado_polea_baja_unilateral", { cable_height: "baja", laterality: "unilateral" }],
    ["back_remo_pecho_apoyado_polea_media", { cable_height: "media", laterality: "bilateral" }],
    ["back_remo_pecho_apoyado_polea_media_unilateral", { cable_height: "media", laterality: "unilateral" }],
    ["back_remo_pecho_apoyado_polea_alta", { cable_height: "alta", laterality: "bilateral" }],
    ["back_remo_pecho_apoyado_polea_alta_unilateral", { cable_height: "alta", laterality: "unilateral" }],
  ];
  for (const [id, a] of merges) {
    mergeInto(id, "back_remo_pecho_apoyado_polea", { grip_width: "medium", grip_orientation: "prono", ...a }, "Remo pecho polea");
  }
  byId.set("back_remo_pecho_apoyado_polea", canonical);
}

// ─── Remo Gironda KEEP_SPECIALTY ──────────────────────────
{
  const base = take("back_remo_gironda_ancho");
  const canonical = {
    ...base,
    id: "back_remo_gironda",
    name: "Remo Gironda",
    description: "Remo Gironda en polea. Tipo de agarre, amplitud y lateralidad.",
    equipment: "Polea",
    alias: "Remo Gironda agarre ancho, Remo Gironda cerrado, Remo Gironda unilateral",
    technicalAspects: [gripWidthAspect(), gripOrientationAspect("prono"), lateralityAspect()],
  };
  mergeInto("back_remo_gironda_ancho", "back_remo_gironda", { grip_width: "wide", grip_orientation: "prono", laterality: "bilateral" }, "Gironda");
  mergeInto("back_remo_gironda_cerrado", "back_remo_gironda", { grip_width: "close", grip_orientation: "prono", laterality: "bilateral" }, "Gironda");
  mergeInto("back_remo_gironda_unilateral", "back_remo_gironda", { grip_width: "medium", grip_orientation: "prono", laterality: "unilateral" }, "Gironda");
  byId.set("back_remo_gironda", canonical);
}

// ─── Remo banda ───────────────────────────────────────────
{
  const base = take("back_remo_banda_ancho");
  const canonical = {
    ...base,
    id: "back_remo_banda",
    name: "Remo en Banda Elástica",
    equipment: "Banda Elástica",
    technicalAspects: [gripWidthAspect({ withMedium: false }), gripOrientationAspect("prono")],
  };
  mergeInto("back_remo_banda_ancho", "back_remo_banda", { grip_width: "wide", grip_orientation: "prono" }, "Remo banda");
  mergeInto("back_remo_banda_cerrado", "back_remo_banda", { grip_width: "close", grip_orientation: "prono" }, "Remo banda");
  byId.set("back_remo_banda", canonical);
}

// ─── Remo mancuerna: add chips; KB as implement chip via merge ─
{
  const base = take("back_remo_mancuerna");
  base.name = "Remo con Mancuernas";
  base.technicalAspects = [
    gripWidthAspect(),
    gripOrientationAspect("prono"),
    lateralityAspect(),
    {
      id: "implement",
      name: "Implemento",
      defaultOptionId: "mancuerna",
      options: [
        { id: "mancuerna", name: "Mancuernas", modifiers: [] },
        { id: "kettlebell", name: "Kettlebell", modifiers: [] },
      ],
    },
  ];
  base.alias = "Remo mancuerna, Remo kettlebell";
  mergeInto("back_remo_kettlebell", "back_remo_mancuerna", { implement: "kettlebell", grip_width: "medium", grip_orientation: "prono", laterality: "bilateral" }, "Remo DB/KB");
  byId.set("back_remo_mancuerna", base);

  const chest = take("back_remo_pecho_apoyado_mancuernas");
  chest.technicalAspects = [
    gripWidthAspect(),
    gripOrientationAspect("prono"),
    {
      id: "implement",
      name: "Implemento",
      defaultOptionId: "mancuerna",
      options: [
        { id: "mancuerna", name: "Mancuernas", modifiers: [] },
        { id: "kettlebell", name: "Kettlebell", modifiers: [] },
      ],
    },
  ];
  mergeInto("back_remo_pecho_apoyado_kettlebell", "back_remo_pecho_apoyado_mancuernas", { implement: "kettlebell" }, "Remo pecho DB");
  byId.set("back_remo_pecho_apoyado_mancuernas", chest);
}

// Seal / Gorilla / Renegado — implement chips
for (const [canon, kb] of [
  ["back_remo_seal_barra_recta", null],
  ["back_remo_seal_mancuernas", "back_remo_seal_kettlebell"],
  ["back_remo_gorilla_mancuernas", "back_remo_gorilla_kettlebell"],
  ["back_remo_renegado_mancuernas", "back_remo_renegado_kettlebell"],
]) {
  const ex = take(canon);
  if (kb) {
    ex.technicalAspects = [
      {
        id: "implement",
        name: "Implemento",
        defaultOptionId: "mancuerna",
        options: [
          { id: "mancuerna", name: "Mancuernas", modifiers: [] },
          { id: "kettlebell", name: "Kettlebell", modifiers: [] },
        ],
      },
    ];
    mergeInto(kb, canon, { implement: "kettlebell" }, "specialty implement");
  }
  byId.set(canon, stripVariantGroup(ex));
}

// Invertido — keep both or merge TRX as equipment chip
{
  const base = take("back_remo_invertido_barra_fija");
  base.id = "back_remo_invertido";
  base.name = "Remo Invertido";
  base.technicalAspects = [
    {
      id: "equipment",
      name: "Equipo",
      defaultOptionId: "barra_fija",
      options: [
        { id: "barra_fija", name: "Barra fija", modifiers: [] },
        { id: "trx", name: "TRX", modifiers: [] },
      ],
    },
  ];
  mergeInto("back_remo_invertido_barra_fija", "back_remo_invertido", { equipment: "barra_fija" }, "Invertido");
  mergeInto("back_remo_invertido_trx", "back_remo_invertido", { equipment: "trx" }, "Invertido");
  byId.set("back_remo_invertido", base);
}

// ─── Press Banca con Barra ────────────────────────────────
{
  const base = take("tren_superior_press_banca_plano_barra");
  const canonical = {
    ...stripVariantGroup(base),
    id: "tren_superior_press_banca_plano_barra",
    name: "Press de Banca con Barra",
    description:
      "Press de banca con barra. Libre o Smith, ángulo (plano/inclinado/declinado) y amplitud de agarre.",
    alias:
      "Press banca plano, Press banca inclinado barra, Press banca declinado, Press pecho Smith, Bench Press",
    technicalAspects: [
      {
        id: "bar_path",
        name: "Trayectoria",
        defaultOptionId: "libre",
        options: [
          { id: "libre", name: "Libre", modifiers: [] },
          { id: "smith", name: "Smith", modifiers: [] },
        ],
      },
      {
        id: "bench_angle",
        name: "Ángulo",
        defaultOptionId: "flat",
        options: [
          { id: "flat", name: "Plano", modifiers: [] },
          { id: "incline", name: "Inclinado", modifiers: [] },
          { id: "decline", name: "Declinado", modifiers: [] },
        ],
      },
      benchGripWidthAspect(),
    ],
  };
  const merges = [
    ["tren_superior_press_banca_plano_barra", { bar_path: "libre", bench_angle: "flat", grip_width: "medium" }],
    ["tren_superior_press_banca_inclinado_barra", { bar_path: "libre", bench_angle: "incline", grip_width: "medium" }],
    ["tren_superior_press_banca_declinado_barra", { bar_path: "libre", bench_angle: "decline", grip_width: "medium" }],
    ["tren_superior_press_pecho_maquina_smith", { bar_path: "smith", bench_angle: "flat", grip_width: "medium" }],
    ["tren_superior_press_inclinado_smith", { bar_path: "smith", bench_angle: "incline", grip_width: "medium" }],
  ];
  for (const [id, a] of merges) {
    if (id === "tren_superior_press_banca_plano_barra") {
      applied.push(`${id} CANONICAL ${JSON.stringify(a)}`);
    } else {
      mergeInto(id, "tren_superior_press_banca_plano_barra", a, "Press barra");
    }
  }
  byId.set(canonical.id, canonical);
}

// ─── Press Banca Mancuernas ───────────────────────────────
{
  const base = take("tren_superior_press_banca_plano_mancuernas");
  const canonical = {
    ...stripVariantGroup(base),
    name: "Press de Banca con Mancuernas",
    description: "Press de banca con mancuernas. Ángulo y amplitud.",
    alias: "Press banca plano mancuernas, Press inclinado mancuernas, Press declinado mancuernas",
    technicalAspects: [
      {
        id: "bench_angle",
        name: "Ángulo",
        defaultOptionId: "flat",
        options: [
          { id: "flat", name: "Plano", modifiers: [] },
          { id: "incline", name: "Inclinado", modifiers: [] },
          { id: "decline", name: "Declinado", modifiers: [] },
        ],
      },
      benchGripWidthAspect(),
    ],
  };
  mergeInto("tren_superior_press_banca_inclinado_mancuernas", canonical.id, { bench_angle: "incline", grip_width: "medium" }, "Press DB");
  mergeInto("tren_superior_press_banca_declinado_mancuernas", canonical.id, { bench_angle: "decline", grip_width: "medium" }, "Press DB");
  applied.push(`${canonical.id} CANONICAL`);
  byId.set(canonical.id, canonical);
}

// Specialties: strip VG + remove chest_pause
for (const id of [
  "tren_superior_press_spoto_barra",
  "tren_superior_press_banca_cadenas",
  "tren_superior_floor_press_barra",
  "tren_superior_floor_press_mancuernas",
]) {
  if (!byId.has(id)) continue;
  const ex = stripVariantGroup(take(id));
  if (ex.technicalAspects) {
    ex.technicalAspects = ex.technicalAspects.filter((a) => a.id !== "chest_pause");
    if (ex.technicalAspects.length === 0) delete ex.technicalAspects;
    else {
      // keep only grip_width simplified if present
      const gw = ex.technicalAspects.find((a) => a.id === "grip_width");
      ex.technicalAspects = gw ? [benchGripWidthAspect()] : [];
      if (ex.technicalAspects.length === 0) delete ex.technicalAspects;
    }
  }
  byId.set(id, ex);
  applied.push(`${id} KEEP_SPECIALTY`);
}

// Máquina convergente: merge incline into base with angle chip
{
  const base = take("tren_superior_press_pecho_maquina_convergente");
  base.name = "Press de Pecho en Máquina Convergente";
  base.technicalAspects = [
    {
      id: "bench_angle",
      name: "Ángulo",
      defaultOptionId: "flat",
      options: [
        { id: "flat", name: "Plano", modifiers: [] },
        { id: "incline", name: "Inclinado", modifiers: [] },
      ],
    },
  ];
  if (byId.has("tren_superior_press_inclinado_maquina_convergente")) {
    mergeInto(
      "tren_superior_press_inclinado_maquina_convergente",
      base.id,
      { bench_angle: "incline" },
      "Convergente",
    );
  }
  byId.set(base.id, stripVariantGroup(base));
}

// ─── Nonsense: martillo + barra/EZ ────────────────────────
const hammerNonsense = [
  ["biceps_curl_de_pie_martillo_barra_recta", "biceps_curl_de_pie_martillo_mancuernas"],
  ["biceps_curl_de_pie_martillo_barra_ez", "biceps_curl_de_pie_martillo_mancuernas"],
  ["biceps_curl_sentado_banco_plano_martillo_barra_recta", "biceps_curl_sentado_banco_plano_martillo_mancuernas"],
  ["biceps_curl_sentado_banco_plano_martillo_barra_ez", "biceps_curl_sentado_banco_plano_martillo_mancuernas"],
  ["biceps_curl_inclinado_martillo_barra_recta", "biceps_curl_inclinado_martillo_mancuernas"],
  ["biceps_curl_inclinado_martillo_barra_ez", "biceps_curl_inclinado_martillo_mancuernas"],
  ["biceps_curl_predicador_martillo_barra_recta", "biceps_curl_predicador_martillo_mancuernas"],
  ["biceps_curl_predicador_martillo_barra_ez", "biceps_curl_predicador_martillo_mancuernas"],
  ["biceps_curl_arana_martillo_barra_recta", "biceps_curl_arana_martillo_mancuernas"],
  ["biceps_curl_arana_martillo_barra_ez", "biceps_curl_arana_martillo_mancuernas"],
  ["biceps_curl_drag_martillo_barra_recta", "biceps_curl_drag_martillo_mancuernas"],
  ["biceps_curl_drag_martillo_barra_ez", "biceps_curl_drag_martillo_mancuernas"],
];
for (const [bad, good] of hammerNonsense) {
  if (byId.has(bad)) {
    removeIds.add(bad);
    aliasAdds[bad] = good;
    applied.push(`NONSENSE ${bad} → ${good}`);
  }
}

// Rebuild catalog preserving relative order: filter removed, insert new ids near first removed sibling
const originalOrder = db.map((e) => e.id);
const newIds = [...byId.keys()].filter((id) => !originalOrder.includes(id) && !removeIds.has(id));

// Place new canonicals where their first merged source was
const insertAt = new Map();
function placeNew(newId, afterOldId) {
  insertAt.set(newId, afterOldId);
}
placeNew("back_remo_barra_t", "back_remo_barra_t_ancho");
placeNew("back_remo_barra", "back_remo_barra_recta_ancho");
placeNew("back_remo_smith", "back_remo_maquina_smith_ancho");
placeNew("back_remo_maquina", "back_remo_maquina_ancho");
placeNew("back_remo_polea", "back_remo_polea_baja_ancho");
placeNew("back_remo_pecho_apoyado_polea", "back_remo_pecho_apoyado_polea_baja");
placeNew("back_remo_gironda", "back_remo_gironda_ancho");
placeNew("back_remo_banda", "back_remo_banda_ancho");
placeNew("back_remo_invertido", "back_remo_invertido_barra_fija");

const out = [];
const emitted = new Set();
for (const id of originalOrder) {
  // emit any new canonical scheduled at this position
  for (const [newId, anchor] of insertAt) {
    if (anchor === id && !emitted.has(newId) && byId.has(newId)) {
      out.push(byId.get(newId));
      emitted.add(newId);
    }
  }
  if (removeIds.has(id)) continue;
  if (!byId.has(id)) continue;
  if (emitted.has(id)) continue;
  out.push(byId.get(id));
  emitted.add(id);
}
for (const id of byId.keys()) {
  if (!emitted.has(id) && !removeIds.has(id)) {
    out.push(byId.get(id));
    emitted.add(id);
  }
}

// Update aliases: redirect chains that pointed to removed ids
Object.assign(aliases, aliasAdds);
for (const [k, v] of Object.entries(aliases)) {
  let cur = v;
  const seen = new Set();
  while (aliasAdds[cur] && !seen.has(cur)) {
    seen.add(cur);
    cur = aliasAdds[cur];
  }
  // also if target was removed and now aliases elsewhere
  if (removeIds.has(cur) && aliasAdds[cur]) cur = aliasAdds[cur];
  aliases[k] = cur;
}
// Don't alias a live id to itself as key if key is still live
for (const k of Object.keys(aliases)) {
  if (byId.has(k) && !removeIds.has(k)) {
    // live id should not be an alias key
    delete aliases[k];
  }
}

fs.writeFileSync(dbPath, JSON.stringify(out, null, 2) + "\n", "utf8");
fs.writeFileSync(aliasPath, JSON.stringify(aliases, null, 2) + "\n", "utf8");

const report = [
  "",
  "## Aplicado — oleada P0 (2026-07-31)",
  "",
  `- Filas antes: ${db.length}`,
  `- Filas después: ${out.length}`,
  `- Eliminadas/fusionadas: ${removeIds.size}`,
  `- Aliases nuevos: ${Object.keys(aliasAdds).length}`,
  "",
  "### Detalle",
  "",
  ...applied.map((l) => `- ${l}`),
  "",
].join("\n");

fs.appendFileSync(auditPath, report, "utf8");
console.log(`Done. ${db.length} → ${out.length}. Removed ${removeIds.size}. Aliases +${Object.keys(aliasAdds).length}`);
