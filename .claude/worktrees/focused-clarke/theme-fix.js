const fs = require('fs');
const filepath = 'components/Home.tsx';
let code = fs.readFileSync(filepath, 'utf8');

const replacements = [
    { p: /bg-\[#FEF7FF\]/g, r: 'bg-[var(--md-sys-color-background)]' },
    { p: /text-\[#1D1B20\]/g, r: 'text-[var(--md-sys-color-on-background)]' },
    { p: /text-\[#49454F\]/g, r: 'text-[var(--md-sys-color-on-surface-variant)]' },
    { p: /stroke=\"#49454F\"/g, r: 'stroke=\"currentColor\"' },
    { p: /bg-\[#ECE6F0\]/g, r: 'bg-[var(--md-sys-color-surface-container-highest)]' }
];

replacements.forEach(({ p, r }) => {
    code = code.replace(p, r);
});

fs.writeFileSync(filepath, code);
console.log('Fixed themes');
