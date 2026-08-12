import fs from "node:fs";
import path from "node:path";

const assetPath = path.resolve(
  "android-native/app/src/main/assets/models/exercises/high_bar_back_squat_barbell.glb",
);
const bytes = fs.readFileSync(assetPath);
if (bytes.readUInt32LE(0) !== 0x46546c67) throw new Error("Invalid GLB magic");
if (bytes.readUInt32LE(4) !== 2) throw new Error("GLB must use version 2");
const jsonLength = bytes.readUInt32LE(12);
const jsonType = bytes.readUInt32LE(16);
if (jsonType !== 0x4e4f534a) throw new Error("First GLB chunk is not JSON");
const json = JSON.parse(bytes.subarray(20, 20 + jsonLength).toString("utf8").trim());
const names = new Set((json.nodes ?? []).map((node) => node.name));
const animation = (json.animations ?? []).find((candidate) => candidate.name === "high_bar_squat_loop");
const requiredNames = [
  "Athlete_Torso_TankTop",
  "Athlete_Pelvis_Shorts",
  "Athlete_Hand_Grip_L",
  "Athlete_Hand_Grip_R",
  "Barbell_Olympic_Bar",
  "Rack_Post_L",
  "Rack_Post_R",
  "Floor",
];
for (const required of requiredNames) {
  if (!names.has(required)) throw new Error(`Missing required node: ${required}`);
}
if (!animation) throw new Error("Missing high_bar_squat_loop animation");
if (animation.channels.length < 30) throw new Error(`Animation has too few channels: ${animation.channels.length}`);
const sourceImage = json.asset?.extras?.sourceImage;
if (sourceImage !== "android-native/app/src/main/res/drawable-nodpi/exercise_sentadilla_trasera_barra_alta.png") {
  throw new Error(`Unexpected source image: ${sourceImage}`);
}
const serialized = JSON.stringify(json).toLowerCase();
if (serialized.includes("caupolican") || serialized.includes("mapuche")) {
  throw new Error("Forbidden character/asset reference found in GLB metadata");
}
if (!json.meshes?.length || !json.materials?.length || !json.buffers?.[0]?.byteLength) {
  throw new Error("GLB has incomplete scene data");
}
console.log(`Validated ${path.relative(process.cwd(), assetPath)}`);
console.log(`nodes=${json.nodes.length} meshes=${json.meshes.length} materials=${json.materials.length} channels=${animation.channels.length}`);
