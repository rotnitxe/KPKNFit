const { spawn, spawnSync } = require('child_process');

const processes = [];
let shuttingDown = false;

const run = (name, cmd, args) => {
  const child = spawn(cmd, args, {
    stdio: 'inherit',
    shell: true,
    env: process.env,
  });

  child.on('exit', (code, signal) => {
    if (shuttingDown) return;
    if (code !== 0) {
      console.error(`[dev-live] ${name} exited with code ${code}${signal ? ` (${signal})` : ''}`);
      shutdown(code || 1);
    }
  });

  processes.push(child);
};

const shutdown = (exitCode = 0) => {
  if (shuttingDown) return;
  shuttingDown = true;
  for (const child of processes) {
    if (!child.killed) child.kill('SIGTERM');
  }
  setTimeout(() => process.exit(exitCode), 150);
};

process.on('SIGINT', () => shutdown(0));
process.on('SIGTERM', () => shutdown(0));

console.log('[dev-live] Running initial build...');
const init = spawnSync('npm', ['run', 'build:inner'], {
  stdio: 'inherit',
  shell: true,
  env: process.env,
});

if (init.status !== 0) {
  process.exit(init.status || 1);
}

console.log('[dev-live] Starting watchers on http://localhost:5500');
run('tailwind-watch', 'npx', ['tailwindcss', '-i', 'input.css', '-o', 'www/output.css', '--watch']);
run('esbuild-main-watch', 'npx', ['esbuild', './index.tsx', '--bundle', '--outfile=www/index.js', '--sourcemap', '--jsx=automatic', '--loader:.js=jsx', '--loader:.ts=tsx', '--format=esm', '--watch']);
run('esbuild-worker-watch', 'npx', ['esbuild', './workers/computeWorker.ts', '--bundle', '--outfile=www/computeWorker.js', '--format=iife', '--loader:.ts=tsx', '--watch']);
run('static-serve', 'npx', ['serve', 'www', '-l', '5500']);
