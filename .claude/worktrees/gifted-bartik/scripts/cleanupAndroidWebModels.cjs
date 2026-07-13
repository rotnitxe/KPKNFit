const fs = require('fs');
const path = require('path');

const repoRoot = path.join(__dirname, '..');
const androidWebModelsDir = path.join(repoRoot, 'android', 'app', 'src', 'main', 'assets', 'public', 'models');
const wwwModelsDir = path.join(repoRoot, 'www', 'models');
const publicModelsDir = path.join(repoRoot, 'public', 'models');

function removeDirIfPresent(targetDir, label) {
    try {
        fs.rmSync(targetDir, { recursive: true, force: true });
        console.log(`[cleanupAndroidWebModels] Removed ${label}: ${targetDir}`);
    } catch (error) {
        console.warn(`[cleanupAndroidWebModels] Could not remove ${label}:`, error);
    }
}

function removeLegacyPublicModels() {
    if (!fs.existsSync(publicModelsDir)) {
        return;
    }

    try {
        for (const entry of fs.readdirSync(publicModelsDir, { withFileTypes: true })) {
            if (entry.name === '.gitkeep') {
                continue;
            }

            fs.rmSync(path.join(publicModelsDir, entry.name), { recursive: true, force: true });
        }
        console.log(`[cleanupAndroidWebModels] Removed legacy files from ${publicModelsDir}`);
    } catch (error) {
        console.warn('[cleanupAndroidWebModels] Could not prune legacy public/models files:', error);
    }
}

removeDirIfPresent(androidWebModelsDir, 'stale Android WebView model assets');
removeDirIfPresent(wwwModelsDir, 'stale web build model assets');
removeLegacyPublicModels();
