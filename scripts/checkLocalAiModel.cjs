const fs = require('fs');
const path = require('path');

const MODEL_VERSION = 'kpkn-food-fg270m-v1';
const repoRoot = path.resolve(__dirname, '..');

const checks = [
    {
        label: 'install-time pack (.litertlm)',
        file: path.join(repoRoot, 'android', 'kpknLocalAiPack', 'src', 'main', 'assets', 'install-time-models', `${MODEL_VERSION}.litertlm`),
    },
    {
        label: 'debug assets (.litertlm)',
        file: path.join(repoRoot, 'android', 'app', 'src', 'main', 'assets', 'models', `${MODEL_VERSION}.litertlm`),
    },
    {
        label: 'android-native assets (.litertlm)',
        file: path.join(repoRoot, 'android-native', 'app', 'src', 'main', 'assets', 'install time models', `${MODEL_VERSION}.litertlm`),
    },
];

let foundAny = false;
let missingRequired = false;

checks.forEach((check) => {
    if (!fs.existsSync(check.file)) {
        console.log(`[checkLocalAiModel] Missing ${check.label}: ${check.file}`);
        missingRequired = true;
        return;
    }

    foundAny = true;
    const sizeMb = Math.round(fs.statSync(check.file).size / (1024 * 1024));
    console.log(`[checkLocalAiModel] Found ${check.label}: ${check.file} (${sizeMb} MB)`);
});

if (!foundAny) {
    console.error('[checkLocalAiModel] No staged model was found.');
    process.exitCode = 1;
    return;
}

if (missingRequired) {
    console.error('[checkLocalAiModel] Required model artifact(s) missing.');
    process.exitCode = 1;
}
