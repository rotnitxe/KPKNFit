/**
 * Generates minimal WAV beep files for rest timer and UI sounds.
 * Android MediaPlayer supports WAV.
 */
const fs = require('fs');
const path = require('path');

const OUT_DIR = path.join(__dirname, '..', 'www', 'assets', 'sounds');
if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });

function createWav(freq, durationMs, sampleRate = 44100) {
  const numSamples = Math.floor((durationMs / 1000) * sampleRate);
  const dataSize = numSamples * 2; // 16-bit = 2 bytes per sample
  const buffer = Buffer.alloc(44 + dataSize);
  let offset = 0;

  const write = (buf) => { buf.copy(buffer, offset); offset += buf.length; };
  const writeStr = (s) => write(Buffer.from(s, 'ascii'));
  const writeU32 = (n) => { buffer.writeUInt32LE(n, offset); offset += 4; };
  const writeU16 = (n) => { buffer.writeUInt16LE(n, offset); offset += 2; };

  writeStr('RIFF');
  writeU32(36 + dataSize);
  writeStr('WAVE');
  writeStr('fmt ');
  writeU32(16);
  writeU16(1); // PCM
  writeU16(1); // mono
  writeU32(sampleRate);
  writeU32(sampleRate * 2);
  writeU16(2);
  writeU16(16);
  writeStr('data');
  writeU32(dataSize);

  for (let i = 0; i < numSamples; i++) {
    const t = i / sampleRate;
    const sample = Math.sin(2 * Math.PI * freq * t) * 0.3 * 32767;
    buffer.writeInt16LE(Math.max(-32768, Math.min(32767, sample)), 44 + i * 2);
  }
  return buffer;
}

const sounds = {
  'beeper_confirm': () => createWav(880, 150),
  'rest_beep_short': () => createWav(880, 120),
  'rest_beep_final': () => createWav(880, 450),
  'switch_toggle_on': () => createWav(600, 80),
  'ui_tap_forward': () => createWav(400, 50),
  'ui_tap_reverse': () => createWav(350, 50),
  'bugle_tune': () => createWav(523, 200),
  'alarm_clock': () => createWav(800, 300),
};

for (const [name, fn] of Object.entries(sounds)) {
  const filepath = path.join(OUT_DIR, `${name}.wav`);
  fs.writeFileSync(filepath, fn());
  console.log('Created', filepath);
}
