const fs = require('fs');
const data = JSON.parse(fs.readFileSync('figma_output.json', 'utf8'));

function traverse(node, depth = 0) {
    const indent = '  '.repeat(depth);
    let str = `${indent}- ${node.name} (${node.type})`;
    if (node.type === 'TEXT') str += `: '${node.characters || ''}'`;
    console.log(str);
    if (node.children) {
        node.children.forEach(child => traverse(child, depth + 1));
    }
}

const root = data.nodes['1:2645'].document;
traverse(root);
