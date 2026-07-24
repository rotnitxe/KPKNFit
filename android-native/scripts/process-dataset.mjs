/**
 * Compatibility entry point. The generator is implemented in Python so it can
 * run in Android development environments where Node is not installed.
 */
import { spawnSync } from 'child_process';

const result = spawnSync('python3', ['scripts/process_dataset.py'], {
  cwd: process.cwd(),
  stdio: 'inherit',
});

if (result.error) throw result.error;
process.exit(result.status ?? 1);
