const fs = require('fs');
const files = ['components/Home.tsx', 'components/home/HomeCardsSection.tsx'];

const replacements = [
    { p: /bg-\[#FEF7FF\]/g, r: 'bg-[color:var(--md-sys-color-background)]' },
    { p: /text-\[#1D1B20\]/g, r: 'text-[color:var(--md-sys-color-on-background)]' },
    { p: /text-\[#49454F\]/g, r: 'text-[color:var(--md-sys-color-on-surface-variant)]' },
    { p: /bg-\[#ECE6F0\]/g, r: 'bg-[color:var(--md-sys-color-surface-container-highest)]' }
];

files.forEach(filepath => {
    if (fs.existsSync(filepath)) {
        let code = fs.readFileSync(filepath, 'utf8');
        replacements.forEach(({ p, r }) => {
            code = code.replace(p, r);
        });
        fs.writeFileSync(filepath, code);
    }
});
console.log('Fixed themes');
