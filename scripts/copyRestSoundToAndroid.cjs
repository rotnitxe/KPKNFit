/**
 * Copia el sonido beeper_confirm.wav al proyecto Android para notificaciones.
 * El archivo debe estar en res/raw para que el canal de notificación lo use.
 */
const fs = require('fs');
const path = require('path');

const SRC = path.join(__dirname, '..', 'www', 'assets', 'sounds', 'beeper_confirm.wav');
const DEST_DIR = path.join(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'raw');
const DEST = path.join(DEST_DIR, 'beeper_confirm.wav');

if (!fs.existsSync(SRC)) {
  console.warn('[copyRestSound] beeper_confirm.wav no encontrado en www/assets/sounds - generando sonidos primero');
  process.exit(0);
}

if (!fs.existsSync(path.join(__dirname, '..', 'android'))) {
  console.log('[copyRestSound] Carpeta android no existe, saltando copia');
  process.exit(0);
}

if (!fs.existsSync(DEST_DIR)) {
  fs.mkdirSync(DEST_DIR, { recursive: true });
}

fs.copyFileSync(SRC, DEST);
console.log('[copyRestSound] beeper_confirm.wav copiado a android res/raw');
