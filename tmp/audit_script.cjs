const fs = require('fs');
const path = require('path');

const fileListPath = process.env.TEMP + '\\all_ts_files.txt';
const content = fs.readFileSync(fileListPath, 'utf-8');
const lines = content.split('\n').filter(l => l.trim().length > 0);

const pwaDirs = ['services', 'stores', 'utils', 'hooks', 'contexts', 'workers', 'components', 'routes'];
const rnPrefix = 'apps\\mobile\\src';
const pkgPrefix = 'packages';

const data = {
  pwa: { logic: [], ui: [] },
  rn: { logic: [], ui: [] },
  pkg: { shared: [] }
};

const fileMap = {}; // basename -> { pwa: path, rn: path, pkg: path }

lines.forEach(line => {
  const fullPath = line.trim();
  const relPath = fullPath.split('kpkn-fit-(beta-test)\\')[1];
  if (!relPath) return;

  const parts = relPath.split('\\');
  const basename = path.basename(relPath);

  if (!fileMap[basename]) fileMap[basename] = {};

  if (relPath.startsWith(rnPrefix)) {
    if (parts.includes('components') || parts.includes('screens')) {
      data.rn.ui.push(relPath);
    } else {
      data.rn.logic.push(relPath);
    }
    fileMap[basename].rn = relPath;
  } else if (relPath.startsWith(pkgPrefix)) {
    data.pkg.shared.push(relPath);
    fileMap[basename].pkg = relPath;
  } else {
    // PWA
    if (parts[0] === 'components') {
      data.pwa.ui.push(relPath);
    } else {
      data.pwa.logic.push(relPath);
    }
    fileMap[basename].pwa = relPath;
  }
});

let report = `# Auditoría Maestra PWA -> React Native

## Resumen Ejecutivo
Se analizaron ${lines.length} archivos TypeScript/TSX del proyecto.
El objetivo es determinar la paridad de migración, con foco principal en la lógica y los motores de cálculo.

### Estadísticas Globales
- **PWA Lógica (services, stores, utils, hooks, etc):** ${data.pwa.logic.length} archivos
- **PWA UI (components):** ${data.pwa.ui.length} archivos
- **RN Lógica:** ${data.rn.logic.length} archivos
- **RN UI:** ${data.rn.ui.length} archivos
- **Paquetes Compartidos (domain, types):** ${data.pkg.shared.length} archivos

## Análisis de Paridad Lógica

Se buscó correspondencia directa de nombres de archivos importantes entre PWA y RN/Packages.

### Batería AUGE y Servicios Clave
`;

const importantServices = ['auge.ts', 'aiService.ts', 'backendAIService.ts', 'storageAdapter.ts', 'storageService.ts'];
importantServices.forEach(srv => {
  const map = fileMap[srv] || {};
  report += `- **${srv}**: \n`;
  report += `  - PWA: ${map.pwa ? '✅ (' + map.pwa + ')' : '❌'}\n`;
  report += `  - RN: ${map.rn ? '✅ (' + map.rn + ')' : '❌'}\n`;
  report += `  - Packages: ${map.pkg ? '✅ (' + map.pkg + ')' : '❌'}\n`;
});

report += `\n### Lógica PWA Migrada (Presente en RN o Packages)\n`;
let logicMigrated = 0;
let logicPending = [];
data.pwa.logic.forEach(pwaPath => {
  const base = path.basename(pwaPath);
  if (fileMap[base].rn || fileMap[base].pkg) {
    logicMigrated++;
  } else {
    logicPending.push(pwaPath);
  }
});

const logicParity = Math.round((logicMigrated / data.pwa.logic.length) * 100) || 0;
report += `Paridad Lógica estimada: **${logicParity}%** (${logicMigrated} de ${data.pwa.logic.length} archivos)\n\n`;

report += `### Lógica PWA Faltante (Pendiente de migración o refactor)\n`;
logicPending.slice(0, 50).forEach(p => {
  report += `- ${p}\n`;
});
if (logicPending.length > 50) report += `- ... y ${logicPending.length - 50} más.\n`;

report += `\n## Análisis de Paridad UI (Componentes)\n`;
let uiMigrated = 0;
let uiPending = [];
data.pwa.ui.forEach(pwaPath => {
  const base = path.basename(pwaPath);
  if (fileMap[base].rn) {
    uiMigrated++;
  } else {
    uiPending.push(pwaPath);
  }
});

const uiParity = Math.round((uiMigrated / data.pwa.ui.length) * 100) || 0;
report += `Paridad UI estimada: **${uiParity}%** (${uiMigrated} de ${data.pwa.ui.length} archivos hermanos)\n\n`;

report += `## Hoja de Ruta Sugerida
1. **Consolidar Lógica Core en Packages**: Mover los servicios matemáticos (auge.ts) y cálculos de fatiga a \`packages/shared-domain\` si no lo están, para uso universal.
2. **Revisar Zustand Stores**: Migrar el remanente de stores de \`stores/\` a \`apps/mobile/src/stores/\` (con AsyncStorage/SQLite en vez de IndexedDB).
3. **Servicios AI y Workers**: Adaptar la inferencia en dispositivo (FunctionGemma) usando bridge nativo (según \`MODELOS.md\`).
4. **Contextos y Hooks**: Hacer paridad estricta de hooks que gestionan timers de sesión, volumen y readiness local.
5. **Componentes Visuales Críticos**: Migrar gráficos de métricas y tarjetas de Auge usando los componentes base de RN ya creados.
`;

const dest = 'C:\\Users\\valen\\.gemini\\antigravity\\brain\\53de8e9c-b02b-4205-8dd3-86edd8ca4882\\AUDITORIA_MIGRACION.md';
fs.writeFileSync(dest, report, 'utf-8');
console.log("Auditoría generada en: " + dest);
