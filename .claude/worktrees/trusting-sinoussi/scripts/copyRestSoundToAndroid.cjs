/**
 * Copia los sonidos al proyecto Android para notificaciones (descanso terminado).
 * Los archivos deben estar en res/raw para que el canal de notificación los use.
 */
const fs = require('fs');
const path = require('path');

const SOUNDS = ['beeper_confirm.wav', 'rest_beep_final.wav'];
const SRC_DIR = path.join(__dirname, '..', 'www', 'assets', 'sounds');
const DEST_DIR = path.join(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'raw');

if (!fs.existsSync(path.join(__dirname, '..', 'android'))) {
  console.log('[copyRestSound] Carpeta android no existe, saltando copia');
  process.exit(0);
}

if (!fs.existsSync(DEST_DIR)) {
  fs.mkdirSync(DEST_DIR, { recursive: true });
}

for (const name of SOUNDS) {
  const src = path.join(SRC_DIR, name);
  if (fs.existsSync(src)) {
    fs.copyFileSync(src, path.join(DEST_DIR, name));
    console.log('[copyRestSound]', name, 'copiado a android res/raw');
  }
}
