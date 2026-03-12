const fs = require('fs');
const path = require('path');

const MODEL_VERSION = 'kpkn-food-fg270m-v1';
const VALID_EXTENSIONS = new Set(['.task', '.litertlm']);

const repoRoot = path.resolve(__dirname, '..');
const debugTargetDir = path.join(repoRoot, 'android', 'app', 'src', 'main', 'assets', 'models');
const installTargetDir = path.join(repoRoot, 'android', 'kpknLocalAiPack', 'src', 'main', 'assets', 'install-time-models');

function parseArgs(argv) {
    const args = {};
    for (let index = 0; index < argv.length; index += 1) {
        const token = argv[index];
        if (!token.startsWith('--')) continue;
        const [key, inlineValue] = token.slice(2).split('=');
        if (inlineValue !== undefined) {
            args[key] = inlineValue;
            continue;
        }
        const next = argv[index + 1];
        if (!next || next.startsWith('--')) {
            args[key] = true;
            continue;
        }
        args[key] = next;
        index += 1;
    }
    return args;
}

function printUsage() {
    console.log([
        'Usage:',
        '  node scripts/stageLocalAiModel.cjs --src "C:\\path\\to\\model-export"',
        '',
        'Options:',
        '  --src       Directory or file containing the exported .task/.litertlm model.',
        '  --targets   debug | install | both (default: both).',
        '  --clean     Remove old staged .task/.litertlm files before copying.',
        '',
        'The script renames staged files to:',
        `  ${MODEL_VERSION}.task`,
        `  ${MODEL_VERSION}.litertlm`,
    ].join('\n'));
}

function ensureDir(dir) {
    fs.mkdirSync(dir, { recursive: true });
}

function cleanTarget(dir) {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir)) {
        const fullPath = path.join(dir, entry);
        if (VALID_EXTENSIONS.has(path.extname(entry).toLowerCase())) {
            fs.rmSync(fullPath, { force: true });
        }
    }
}

function walk(dir, found = []) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            walk(fullPath, found);
            continue;
        }
        if (VALID_EXTENSIONS.has(path.extname(entry.name).toLowerCase())) {
            found.push(fullPath);
        }
    }
    return found;
}

function collectCandidates(srcPath) {
    const resolved = path.resolve(srcPath);
    if (!fs.existsSync(resolved)) {
        throw new Error(`Source path does not exist: ${resolved}`);
    }

    const stat = fs.statSync(resolved);
    if (stat.isFile()) {
        const ext = path.extname(resolved).toLowerCase();
        if (!VALID_EXTENSIONS.has(ext)) {
            throw new Error(`Unsupported file extension: ${ext}`);
        }
        return [resolved];
    }

    return walk(resolved);
}

function pickBestCandidate(candidates, extension) {
    const matching = candidates.filter((candidate) => path.extname(candidate).toLowerCase() === extension);
    if (!matching.length) return null;

    const exact = matching.find((candidate) => path.basename(candidate, extension).toLowerCase() === MODEL_VERSION.toLowerCase());
    if (exact) return exact;

    matching.sort((left, right) => {
        const leftScore = left.toLowerCase().includes('gemma') ? 1 : 0;
        const rightScore = right.toLowerCase().includes('gemma') ? 1 : 0;
        return rightScore - leftScore;
    });

    return matching[0];
}

function copyIntoTargets(sourcePath, targets) {
    const extension = path.extname(sourcePath).toLowerCase();
    const fileName = `${MODEL_VERSION}${extension}`;
    const size = fs.statSync(sourcePath).size;

    targets.forEach((targetDir) => {
        ensureDir(targetDir);
        const destination = path.join(targetDir, fileName);
        fs.copyFileSync(sourcePath, destination);
        console.log(`[stageLocalAiModel] Copied ${path.basename(sourcePath)} -> ${destination} (${Math.round(size / (1024 * 1024))} MB)`);
    });
}

function main() {
    const args = parseArgs(process.argv.slice(2));
    if (!args.src) {
        printUsage();
        process.exitCode = 1;
        return;
    }

    const targetsMode = String(args.targets || 'both').toLowerCase();
    const targets = [];
    if (targetsMode === 'both' || targetsMode === 'debug') targets.push(debugTargetDir);
    if (targetsMode === 'both' || targetsMode === 'install') targets.push(installTargetDir);
    if (!targets.length) {
        throw new Error(`Invalid --targets value: ${targetsMode}`);
    }

    if (args.clean) {
        targets.forEach(cleanTarget);
    }

    const candidates = collectCandidates(args.src);
    const taskCandidate = pickBestCandidate(candidates, '.task');
    const litertCandidate = pickBestCandidate(candidates, '.litertlm');

    if (!taskCandidate && !litertCandidate) {
        throw new Error(`No .task or .litertlm files found under ${path.resolve(args.src)}`);
    }

    if (taskCandidate) {
        copyIntoTargets(taskCandidate, targets);
    }
    if (litertCandidate) {
        copyIntoTargets(litertCandidate, targets);
    }

    console.log('[stageLocalAiModel] Model staging complete.');
}

try {
    main();
} catch (error) {
    console.error('[stageLocalAiModel] Failed:', error instanceof Error ? error.message : error);
    process.exitCode = 1;
}
