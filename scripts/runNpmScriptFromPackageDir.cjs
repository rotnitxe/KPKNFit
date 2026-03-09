const path = require('path');
const { execSync } = require('child_process');

const targetScript = process.argv[2];
if (!targetScript) {
  throw new Error('Missing target script name. Usage: node scripts/runNpmScriptFromPackageDir.cjs <script>');
}

let packageJsonPath = process.env.npm_package_json || path.join(process.cwd(), 'package.json');

// npm can expose package paths through PowerShell provider notation; normalize to a plain Windows path.
if (packageJsonPath.includes('::')) {
  packageJsonPath = packageJsonPath.split('::').pop();
}
if (packageJsonPath.startsWith('\\\\?\\')) {
  packageJsonPath = packageJsonPath.slice(4);
}
if (!path.isAbsolute(packageJsonPath)) {
  packageJsonPath = path.resolve(packageJsonPath);
}

const projectRoot = path.dirname(packageJsonPath);
execSync(`npm run ${targetScript}`, {
  cwd: projectRoot,
  shell: true,
  stdio: 'inherit',
});
