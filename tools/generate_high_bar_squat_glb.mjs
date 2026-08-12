import fs from "node:fs";
import path from "node:path";

// This is an intentionally deterministic, offline asset generator. It produces
// a stylised but anatomically constrained scene from the approved catalog
// reference. The generated GLB contains no external textures or network URLs.

const outputPath = path.resolve(
  "android-native/app/src/main/assets/models/exercises/high_bar_back_squat_barbell.glb",
);

const bufferParts = [];
let bufferLength = 0;
const bufferViews = [];
const accessors = [];
const meshes = [];
const nodes = [];
const materials = [];
const animationEntries = [];

const SOURCE_IMAGE = "android-native/app/src/main/res/drawable-nodpi/exercise_sentadilla_trasera_barra_alta.png";
const ANIMATION_NAME = "high_bar_squat_loop";

function align4(value) {
  return (value + 3) & ~3;
}

function appendBytes(bytes) {
  const offset = bufferLength;
  const aligned = align4(bufferLength);
  if (aligned > bufferLength) {
    const padding = Buffer.alloc(aligned - bufferLength);
    bufferParts.push(padding);
    bufferLength = aligned;
  }
  bufferParts.push(bytes);
  bufferLength += bytes.length;
  return { byteOffset: aligned, byteLength: bytes.length };
}

function typedBytes(values, kind) {
  const typed = kind === "u16" ? new Uint16Array(values) : new Float32Array(values);
  return Buffer.from(typed.buffer, typed.byteOffset, typed.byteLength);
}

function accessor(values, kind, type, target = undefined) {
  const components = { SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4 }[type];
  const componentType = kind === "u16" ? 5123 : 5126;
  const bytes = appendBytes(typedBytes(values, kind));
  const view = { buffer: 0, byteOffset: bytes.byteOffset, byteLength: bytes.byteLength };
  if (target !== undefined) view.target = target;
  const viewIndex = bufferViews.push(view) - 1;
  const result = {
    bufferView: viewIndex,
    componentType,
    count: values.length / components,
    type,
  };
  if (kind !== "u16" && values.length > 0) {
    const min = Array(components).fill(Number.POSITIVE_INFINITY);
    const max = Array(components).fill(Number.NEGATIVE_INFINITY);
    for (let i = 0; i < values.length; i += components) {
      for (let c = 0; c < components; c += 1) {
        min[c] = Math.min(min[c], values[i + c]);
        max[c] = Math.max(max[c], values[i + c]);
      }
    }
    result.min = min;
    result.max = max;
  }
  return accessors.push(result) - 1;
}

function normalize(v) {
  const length = Math.hypot(v[0], v[1], v[2]) || 1;
  return [v[0] / length, v[1] / length, v[2] / length];
}

function sub(a, b) {
  return [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
}

function add(a, b) {
  return [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
}

function mul(v, scalar) {
  return [v[0] * scalar, v[1] * scalar, v[2] * scalar];
}

function midpoint(a, b) {
  return mul(add(a, b), 0.5);
}

function quatFromTo(from, to) {
  const a = normalize(from);
  const b = normalize(to);
  const dot = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
  if (dot < -0.999999) {
    const axis = Math.abs(a[0]) < 0.9 ? [1, 0, 0] : [0, 0, 1];
    const cross = [
      a[1] * axis[2] - a[2] * axis[1],
      a[2] * axis[0] - a[0] * axis[2],
      a[0] * axis[1] - a[1] * axis[0],
    ];
    const n = normalize(cross);
    return [n[0], n[1], n[2], 0];
  }
  const cross = [
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0],
  ];
  const s = Math.sqrt((1 + dot) * 2);
  return [cross[0] / s, cross[1] / s, cross[2] / s, s / 2];
}

function ringMesh(rings, segments = 16) {
  const positions = [];
  const normals = [];
  const indices = [];
  for (let i = 0; i < rings.length; i += 1) {
    const ring = rings[i];
    for (let j = 0; j < segments; j += 1) {
      const angle = (j / segments) * Math.PI * 2;
      const cos = Math.cos(angle);
      const sin = Math.sin(angle);
      positions.push(ring.x * cos, ring.y, ring.z * sin);
      normals.push(cos, ring.normalY ?? 0, sin);
    }
  }
  for (let i = 0; i < rings.length - 1; i += 1) {
    for (let j = 0; j < segments; j += 1) {
      const next = (j + 1) % segments;
      const a = i * segments + j;
      const b = i * segments + next;
      const c = (i + 1) * segments + next;
      const d = (i + 1) * segments + j;
      // Counter-clockwise winding as seen from the outside. Filament culls
      // back faces by default, so the anatomical surfaces must point out.
      indices.push(a, d, b, b, d, c);
    }
  }
  return { positions, normals: normals.map((_, i) => normals[i]), indices };
}

function capsuleMesh() {
  return ringMesh([
    { y: -0.5, x: 0.025, z: 0.025, normalY: -0.95 },
    { y: -0.43, x: 0.72, z: 0.72, normalY: -0.45 },
    { y: -0.31, x: 1.0, z: 1.0, normalY: -0.15 },
    { y: 0.31, x: 1.0, z: 1.0, normalY: 0.15 },
    { y: 0.43, x: 0.72, z: 0.72, normalY: 0.45 },
    { y: 0.5, x: 0.025, z: 0.025, normalY: 0.95 },
  ]);
}

function sphereMesh() {
  const rings = [];
  const count = 12;
  for (let i = 0; i <= count; i += 1) {
    const phi = -Math.PI / 2 + (i / count) * Math.PI;
    const cos = Math.cos(phi);
    rings.push({
      y: Math.sin(phi),
      x: Math.max(0.025, cos),
      z: Math.max(0.025, cos),
      normalY: Math.sin(phi),
    });
  }
  return ringMesh(rings, 18);
}

function torsoMesh() {
  return ringMesh([
    { y: -0.5, x: 0.19, z: 0.13, normalY: -0.2 },
    { y: -0.38, x: 0.25, z: 0.15, normalY: -0.1 },
    { y: -0.05, x: 0.29, z: 0.17, normalY: 0 },
    { y: 0.28, x: 0.24, z: 0.14, normalY: 0.1 },
    { y: 0.46, x: 0.19, z: 0.12, normalY: 0.2 },
    { y: 0.5, x: 0.16, z: 0.1, normalY: 0.4 },
  ], 18);
}

function boxMesh() {
  const faces = [
    [[-1, -1, 1], [0, 0, 1]], [[1, -1, 1], [0, 0, 1]], [[1, 1, 1], [0, 0, 1]], [[-1, 1, 1], [0, 0, 1]],
    [[1, -1, -1], [0, 0, -1]], [[-1, -1, -1], [0, 0, -1]], [[-1, 1, -1], [0, 0, -1]], [[1, 1, -1], [0, 0, -1]],
    [[-1, -1, -1], [-1, 0, 0]], [[-1, -1, 1], [-1, 0, 0]], [[-1, 1, 1], [-1, 0, 0]], [[-1, 1, -1], [-1, 0, 0]],
    [[1, -1, 1], [1, 0, 0]], [[1, -1, -1], [1, 0, 0]], [[1, 1, -1], [1, 0, 0]], [[1, 1, 1], [1, 0, 0]],
    [[-1, 1, 1], [0, 1, 0]], [[1, 1, 1], [0, 1, 0]], [[1, 1, -1], [0, 1, 0]], [[-1, 1, -1], [0, 1, 0]],
    [[-1, -1, -1], [0, -1, 0]], [[1, -1, -1], [0, -1, 0]], [[1, -1, 1], [0, -1, 0]], [[-1, -1, 1], [0, -1, 0]],
  ];
  const positions = [];
  const normals = [];
  const indices = [];
  for (let i = 0; i < faces.length; i += 1) {
    positions.push(...faces[i][0]);
    normals.push(...faces[i][1]);
  }
  for (let face = 0; face < 6; face += 1) {
    const base = face * 4;
    indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
  }
  return { positions, normals, indices };
}

function cylinderMesh(segments = 24) {
  const positions = [];
  const normals = [];
  const indices = [];
  for (let j = 0; j < segments; j += 1) {
    const angle = (j / segments) * Math.PI * 2;
    const c = Math.cos(angle);
    const s = Math.sin(angle);
    positions.push(c, -0.5, s, c, 0.5, s);
    normals.push(c, 0, s, c, 0, s);
  }
  const bottomCenter = positions.length / 3;
  positions.push(0, -0.5, 0);
  normals.push(0, -1, 0);
  const topCenter = positions.length / 3;
  positions.push(0, 0.5, 0);
  normals.push(0, 1, 0);
  for (let j = 0; j < segments; j += 1) {
    const next = (j + 1) % segments;
    const b0 = j * 2;
    const t0 = b0 + 1;
    const b1 = next * 2;
    const t1 = b1 + 1;
    indices.push(b0, t0, b1, b1, t0, t1);
    indices.push(bottomCenter, b0, b1);
    indices.push(topCenter, t1, t0);
  }
  return { positions, normals, indices };
}

function addMesh(name, geometry, material) {
  const positionAccessor = accessor(geometry.positions, "f32", "VEC3", 34962);
  const normalAccessor = accessor(geometry.normals, "f32", "VEC3", 34962);
  const indexAccessor = accessor(geometry.indices, "u16", "SCALAR", 34963);
  return meshes.push({
    name,
    primitives: [{
      attributes: { POSITION: positionAccessor, NORMAL: normalAccessor },
      indices: indexAccessor,
      material,
    }],
  }) - 1;
}

function addMaterial(name, color, metallic = 0, roughness = 0.55) {
  return materials.push({
    name,
    pbrMetallicRoughness: {
      baseColorFactor: [...color, 1],
      metallicFactor: metallic,
      roughnessFactor: roughness,
    },
  }) - 1;
}

const mat = {
  skin: addMaterial("skin_warm_brown", [0.52, 0.23, 0.11], 0, 0.5),
  skinHighlight: addMaterial("skin_highlight", [0.68, 0.33, 0.16], 0, 0.48),
  shirt: addMaterial("black_tank_top", [0.018, 0.022, 0.028], 0, 0.42),
  shorts: addMaterial("black_shorts", [0.012, 0.015, 0.02], 0, 0.48),
  hair: addMaterial("dark_curly_hair", [0.025, 0.009, 0.004], 0, 0.62),
  shoes: addMaterial("black_training_shoes", [0.008, 0.01, 0.013], 0, 0.35),
  rack: addMaterial("rack_powder_coat", [0.018, 0.023, 0.032], 0.25, 0.4),
  steel: addMaterial("barbell_chrome", [0.5, 0.53, 0.57], 0.85, 0.22),
  plates: addMaterial("plates_black", [0.025, 0.03, 0.04], 0.32, 0.38),
  hub: addMaterial("plate_hubs", [0.32, 0.35, 0.4], 0.72, 0.2),
  floor: addMaterial("matte_floor", [0.045, 0.052, 0.065], 0.05, 0.82),
  eye: addMaterial("eyes", [0.004, 0.003, 0.002], 0, 0.3),
};

const mesh = {
  capsule: addMesh("anatomical_capsule", capsuleMesh(), mat.skin),
  capsuleHighlight: addMesh("anatomical_highlight_capsule", capsuleMesh(), mat.skinHighlight),
  sphere: addMesh("anatomical_sphere", sphereMesh(), mat.skin),
  hairSphere: addMesh("hair_volume", sphereMesh(), mat.hair),
  torso: addMesh("tank_top_torso", torsoMesh(), mat.shirt),
  pelvis: addMesh("shorts_pelvis", torsoMesh(), mat.shorts),
  box: addMesh("equipment_box", boxMesh(), mat.rack),
  shoe: addMesh("training_shoe", boxMesh(), mat.shoes),
  bar: addMesh("barbell", cylinderMesh(32), mat.steel),
  plate: addMesh("weight_plate", cylinderMesh(32), mat.plates),
  hub: addMesh("weight_plate_hub", cylinderMesh(24), mat.hub),
  floor: addMesh("floor", boxMesh(), mat.floor),
  eye: addMesh("eye", sphereMesh(), mat.eye),
};

function addNode(name, meshIndex, transform = {}) {
  const node = { name };
  if (meshIndex !== undefined) node.mesh = meshIndex;
  if (transform.translation) node.translation = transform.translation;
  if (transform.rotation) node.rotation = transform.rotation;
  if (transform.scale) node.scale = transform.scale;
  return nodes.push(node) - 1;
}

function addFixedEquipment() {
  addNode("Floor", mesh.floor, { translation: [0, -0.06, 0], scale: [1.45, 0.06, 0.9] });
  for (const side of [-1, 1]) {
    addNode(`Rack_Post_${side < 0 ? "L" : "R"}`, mesh.box, {
      translation: [side * 0.83, 1.02, 0.28],
      scale: [0.085, 1.02, 0.085],
    });
    addNode(`Rack_Base_${side < 0 ? "L" : "R"}`, mesh.box, {
      translation: [side * 0.83, 0.05, 0.28],
      scale: [0.24, 0.05, 0.42],
    });
    addNode(`Rack_Foot_${side < 0 ? "L" : "R"}`, mesh.box, {
      translation: [side * 0.83, 0.05, 0.02],
      scale: [0.42, 0.05, 0.07],
    });
    addNode(`Rack_Hook_${side < 0 ? "L" : "R"}`, mesh.box, {
      translation: [side * 0.72, 1.58, 0.12],
      scale: [0.16, 0.06, 0.10],
    });
    addNode(`Rack_Safety_${side < 0 ? "L" : "R"}`, mesh.box, {
      translation: [side * 0.63, 0.58, 0.12],
      scale: [0.25, 0.045, 0.045],
    });
    for (const y of [0.38, 0.62, 0.86, 1.1, 1.34, 1.58]) {
      addNode(`Rack_Hole_${side < 0 ? "L" : "R"}_${y}`, mesh.hub, {
        translation: [side * 0.83, y, 0.188],
        rotation: [0.7071, 0, 0, 0.7071],
        scale: [0.027, 0.027, 0.012],
      });
    }
  }
  addNode("Rack_Top_Crossbar", mesh.box, {
    translation: [0, 1.97, 0.28],
    scale: [0.83, 0.045, 0.045],
  });
  addNode("Rack_Back_Brace", mesh.box, {
    translation: [0, 0.92, 0.38],
    scale: [0.83, 0.045, 0.045],
  });
}

addFixedEquipment();

const capsuleMeshFor = (material) => material === mat.skinHighlight ? mesh.capsuleHighlight : mesh.capsule;

function createAnimatedPart(name, meshIndex, frames) {
  const initial = frames[0];
  const nodeIndex = addNode(name, meshIndex, initial);
  animationEntries.push({ nodeIndex, frames });
  return nodeIndex;
}

function createAnimatedPosition(name, meshIndex, positions, rotations = undefined, scales = undefined) {
  const frames = positions.map((translation, i) => ({
    translation,
    ...(rotations ? { rotation: rotations[i] } : {}),
    ...(scales ? { scale: scales[i] } : {}),
  }));
  return createAnimatedPart(name, meshIndex, frames);
}

function makePose(depth) {
  const pelvis = [0, 0.98 - 0.24 * depth, 0.015 * depth];
  const shoulder = [0, 1.53 - 0.26 * depth, 0.055 + 0.13 * depth];
  const torsoDirection = normalize(sub(shoulder, pelvis));
  const torsoRotation = quatFromTo([0, 1, 0], torsoDirection);
  const torsoCenter = midpoint(pelvis, shoulder);
  const neck = add(shoulder, mul(torsoDirection, 0.16));
  const head = add(neck, [0, 0.18, 0.008]);
  const headUp = normalize([0, 1, 0.02]);
  const front = [0, 0, 1];
  const barY = shoulder[1] + 0.025;
  const barZ = shoulder[2] - 0.095;
  const legs = {};
  const arms = {};
  for (const side of [-1, 1]) {
    const key = side < 0 ? "L" : "R";
    const hip = [side * 0.18, pelvis[1] - 0.04, pelvis[2]];
    const knee = [side * (0.20 + 0.12 * depth), 0.54 - 0.15 * depth, 0.025 + 0.055 * depth];
    const ankle = [side * 0.18, 0.105, 0.0];
    const foot = [side * 0.18, 0.075, 0.075 + 0.015 * depth];
    legs[key] = { hip, knee, ankle, foot };

    const shoulderPoint = [side * 0.235, shoulder[1] - 0.01, shoulder[2]];
    const elbow = [side * 0.39, shoulder[1] - 0.16, shoulder[2] - 0.035];
    const wrist = [side * 0.54, barY, barZ];
    arms[key] = { shoulder: shoulderPoint, elbow, wrist };
  }
  return { pelvis, shoulder, torsoDirection, torsoRotation, torsoCenter, neck, head, headUp, front, barY, barZ, legs, arms };
}

const times = Array.from({ length: 16 }, (_, i) => (3.5 * i) / 15);
function smoothstep(value) {
  const t = Math.max(0, Math.min(1, value));
  return t * t * (3 - 2 * t);
}
function depthAt(time) {
  if (time <= 1.18) return smoothstep(time / 1.18);
  if (time <= 1.48) return 1;
  if (time <= 3.25) return 1 - smoothstep((time - 1.48) / 1.77);
  return 0;
}
const poses = times.map((time) => makePose(depthAt(time)));
const identity = [0, 0, 0, 1];
const barRotation = [0, 0, 0.7071068, 0.7071068];

createAnimatedPart("Athlete_Torso_TankTop", mesh.torso, poses.map((p) => ({
  translation: p.torsoCenter,
  rotation: p.torsoRotation,
  scale: [1, Math.max(0.8, Math.hypot(...sub(p.shoulder, p.pelvis))), 1],
})));
createAnimatedPosition("Athlete_Pelvis_Shorts", mesh.pelvis, poses.map((p) => p.pelvis), poses.map(() => identity), poses.map(() => [1.08, 0.46, 0.86]));
createAnimatedPart("Athlete_Neck", capsuleMeshFor(mat.skinHighlight), poses.map((p) => ({
  translation: midpoint(p.shoulder, p.neck),
  rotation: quatFromTo([0, 1, 0], sub(p.neck, p.shoulder)),
  scale: [0.72, Math.max(0.12, Math.hypot(...sub(p.neck, p.shoulder))), 0.72],
})));
createAnimatedPosition("Athlete_Head", mesh.sphere, poses.map((p) => p.head), poses.map(() => identity), poses.map(() => [0.17, 0.19, 0.16]));
createAnimatedPosition("Athlete_Hair_Volume", mesh.hairSphere, poses.map((p) => add(p.head, [0, 0.085, -0.008])), poses.map(() => identity), poses.map(() => [0.18, 0.105, 0.17]));

for (const [index, offset] of [
  [-0.12, 0.085, 0.015], [-0.06, 0.125, 0.005], [0.02, 0.13, -0.002], [0.09, 0.105, 0.005],
  [-0.15, 0.025, -0.01], [0.14, 0.035, -0.005],
].entries()) {
  createAnimatedPosition(`Athlete_Hair_Curl_${index}`, mesh.hairSphere, poses.map((p) => add(p.head, offset)), poses.map(() => identity), poses.map(() => [0.055, 0.06, 0.055]));
}

for (const side of [-1, 1]) {
  const key = side < 0 ? "L" : "R";
  createAnimatedPosition(`Athlete_Eye_${key}`, mesh.eye, poses.map((p) => add(add(p.head, [side * 0.052, 0.015, 0.135]), [0, 0, 0])), poses.map(() => identity), poses.map(() => [0.016, 0.016, 0.012]));
}
createAnimatedPosition("Athlete_Nose", mesh.sphere, poses.map((p) => add(p.head, [0, -0.005, 0.145])), poses.map(() => identity), poses.map(() => [0.022, 0.026, 0.03]));

for (const side of [-1, 1]) {
  const key = side < 0 ? "L" : "R";
  const leg = poses.map((p) => p.legs[key]);
  const thighFrames = leg.map((l) => ({ translation: midpoint(l.hip, l.knee), rotation: quatFromTo([0, 1, 0], sub(l.knee, l.hip)), scale: [0.13, Math.hypot(...sub(l.knee, l.hip)), 0.13] }));
  const shinFrames = leg.map((l) => ({ translation: midpoint(l.knee, l.ankle), rotation: quatFromTo([0, 1, 0], sub(l.ankle, l.knee)), scale: [0.105, Math.hypot(...sub(l.ankle, l.knee)), 0.105] }));
  createAnimatedPart(`Athlete_Thigh_${key}`, capsuleMeshFor(mat.skinHighlight), thighFrames);
  createAnimatedPart(`Athlete_Shin_${key}`, mesh.capsule, shinFrames);
  createAnimatedPosition(`Athlete_Shoe_${key}`, mesh.shoe, leg.map((l) => l.foot), leg.map(() => identity), leg.map(() => [0.18, 0.11, 0.34]));
  createAnimatedPosition(`Athlete_Knee_${key}`, mesh.sphere, leg.map((l) => l.knee), leg.map(() => identity), leg.map(() => [0.12, 0.1, 0.11]));

  const arm = poses.map((p) => p.arms[key]);
  createAnimatedPart(`Athlete_UpperArm_${key}`, mesh.capsuleHighlight, arm.map((a) => ({ translation: midpoint(a.shoulder, a.elbow), rotation: quatFromTo([0, 1, 0], sub(a.elbow, a.shoulder)), scale: [0.082, Math.hypot(...sub(a.elbow, a.shoulder)), 0.082] })));
  createAnimatedPart(`Athlete_Forearm_${key}`, mesh.capsuleHighlight, arm.map((a) => ({ translation: midpoint(a.elbow, a.wrist), rotation: quatFromTo([0, 1, 0], sub(a.wrist, a.elbow)), scale: [0.07, Math.hypot(...sub(a.wrist, a.elbow)), 0.07] })));
  createAnimatedPosition(`Athlete_Hand_Grip_${key}`, mesh.sphere, arm.map((a) => a.wrist), arm.map(() => identity), arm.map(() => [0.085, 0.095, 0.075]));
  createAnimatedPosition(`Athlete_Shoulder_${key}`, mesh.sphere, arm.map((a) => a.shoulder), arm.map(() => identity), arm.map(() => [0.105, 0.095, 0.095]));
}

const barPositions = poses.map((p) => [0, p.barY, p.barZ]);
createAnimatedPosition("Barbell_Olympic_Bar", mesh.bar, barPositions, poses.map(() => barRotation), poses.map(() => [1.15, 0.026, 1.15]));
for (const side of [-1, 1]) {
  const key = side < 0 ? "L" : "R";
  for (const [index, x] of [0.77, 0.91].entries()) {
    createAnimatedPosition(`Barbell_Plate_${key}_${index}`, mesh.plate, poses.map((p) => [side * x, p.barY, p.barZ]), poses.map(() => barRotation), poses.map(() => [0.22, 0.07, 0.22]));
  }
  createAnimatedPosition(`Barbell_Hub_${key}`, mesh.hub, poses.map((p) => [side * 0.70, p.barY, p.barZ]), poses.map(() => barRotation), poses.map(() => [0.095, 0.045, 0.095]));
  createAnimatedPosition(`Barbell_Collar_${key}`, mesh.plate, poses.map((p) => [side * 0.63, p.barY, p.barZ]), poses.map(() => barRotation), poses.map(() => [0.07, 0.035, 0.07]));
}

function addAnimation() {
  const samplers = [];
  const channels = [];
  for (const entry of animationEntries) {
    const translations = [];
    const rotations = [];
    const scales = [];
    let hasRotation = false;
    let hasScale = false;
    for (const frame of entry.frames) {
      translations.push(...frame.translation);
      if (frame.rotation) {
        rotations.push(...frame.rotation);
        hasRotation = true;
      }
      if (frame.scale) {
        scales.push(...frame.scale);
        hasScale = true;
      }
    }
    const input = accessor(times, "f32", "SCALAR");
    const translationOutput = accessor(translations, "f32", "VEC3");
    const translationSampler = samplers.push({ input, output: translationOutput, interpolation: "LINEAR" }) - 1;
    channels.push({ sampler: translationSampler, target: { node: entry.nodeIndex, path: "translation" } });
    if (hasRotation) {
      const rotationOutput = accessor(rotations, "f32", "VEC4");
      const rotationSampler = samplers.push({ input, output: rotationOutput, interpolation: "LINEAR" }) - 1;
      channels.push({ sampler: rotationSampler, target: { node: entry.nodeIndex, path: "rotation" } });
    }
    if (hasScale) {
      const scaleOutput = accessor(scales, "f32", "VEC3");
      const scaleSampler = samplers.push({ input, output: scaleOutput, interpolation: "LINEAR" }) - 1;
      channels.push({ sampler: scaleSampler, target: { node: entry.nodeIndex, path: "scale" } });
    }
  }
  return { name: ANIMATION_NAME, samplers, channels, extras: { loop: true, durationSeconds: 3.5 } };
}

const animation = addAnimation();
const rootNodes = nodes.map((_, index) => index);
const binary = Buffer.concat(bufferParts);
const json = {
  asset: {
    version: "2.0",
    generator: "KPKN deterministic high-bar squat pilot generator",
    extras: {
      sourceImage: SOURCE_IMAGE,
      visualContract: "Use only the catalog athlete: black tank top, warm brown skin, black rack, Olympic barbell and plates.",
      movementContract: "High-bar back squat: standing start, controlled descent, stable depth, controlled ascent, seamless loop.",
      anatomicalConstraints: [
        "feet remain planted and symmetric",
        "knees track over toes without valgus",
        "hands remain at fixed grip points on the bar",
        "bar remains level and supported across the upper traps",
        "rack and floor remain static",
      ],
    },
  },
  scene: 0,
  scenes: [{ name: "HighBarBackSquatCatalogScene", nodes: rootNodes }],
  nodes,
  meshes,
  materials,
  accessors,
  bufferViews,
  buffers: [{ byteLength: binary.length }],
  animations: [animation],
};
const jsonBytes = Buffer.from(JSON.stringify(json));
const jsonPaddedLength = align4(jsonBytes.length);
const binPaddedLength = align4(binary.length);
const glb = Buffer.alloc(12 + 8 + jsonPaddedLength + 8 + binPaddedLength);
glb.writeUInt32LE(0x46546c67, 0);
glb.writeUInt32LE(2, 4);
glb.writeUInt32LE(glb.length, 8);
let cursor = 12;
glb.writeUInt32LE(jsonPaddedLength, cursor);
glb.writeUInt32LE(0x4e4f534a, cursor + 4);
jsonBytes.copy(glb, cursor + 8);
for (let i = cursor + 8 + jsonBytes.length; i < cursor + 8 + jsonPaddedLength; i += 1) glb[i] = 0x20;
cursor += 8 + jsonPaddedLength;
glb.writeUInt32LE(binPaddedLength, cursor);
glb.writeUInt32LE(0x004e4942, cursor + 4);
binary.copy(glb, cursor + 8);

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, glb);
console.log(`Generated ${outputPath}`);
console.log(`bytes=${glb.length} nodes=${nodes.length} meshes=${meshes.length} animationChannels=${animation.channels.length}`);
