const fs = require('fs');
const path = require('path');

const MODEL_VERSION = 'kpkn-food-fg270m-v1';
const repoRoot = path.resolve(__dirname, '..');

const checks = [
    {
        label: 'install-time pack (.task)',
        file: path.join(repoRoot, 'android', 'kpknLocalAiPack', 'src', 'main', 'assets', 'install-time-models', `${MODEL_VERSION}.task`),
    },
    {
        label: 'install-time pack (.litertlm)',
        file: path.join(repoRoot, 'android', 'kpknLocalAiPack', 'src', 'main', 'assets', 'install-time-models', `${MODEL_VERSION}.litertlm`),
    },
    {
        label: 'debug assets (.task)',
        file: path.join(repoRoot, 'android', 'app', 'src', 'main', 'assets', 'models', `${MODEL_VERSION}.task`),
    },
    {
        label: 'debug assets (.litertlm)',
        file: path.join(repoRoot, 'android', 'app', 'src', 'main', 'assets', 'models', `${MODEL_VERSION}.litertlm`),
    },
    {
        label: 'RN debug assets (.task)',
        file: path.join(repoRoot, 'apps', 'mobile', 'android', 'app', 'src', 'main', 'assets', 'models', `${MODEL_VERSION}.task`),
    },
    {
        label: 'RN debug assets (.litertlm)',
        file: path.join(repoRoot, 'apps', 'mobile', 'android', 'app', 'src', 'main', 'assets', 'models', `${MODEL_VERSION}.litertlm`),
    },
];

let foundAny = false;

checks.forEach((check) => {
    if (!fs.existsSync(check.file)) {
        console.log(`[checkLocalAiModel] Missing ${check.label}: ${check.file}`);
        return;
    }

    foundAny = true;
    const sizeMb = Math.round(fs.statSync(check.file).size / (1024 * 1024));
    console.log(`[checkLocalAiModel] Found ${check.label}: ${check.file} (${sizeMb} MB)`);
});

if (!foundAny) {
    console.error('[checkLocalAiModel] No staged model was found.');
    process.exitCode = 1;
}
