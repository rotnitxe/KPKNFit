const fs = require('fs');

function parseNode(node, depth = 0) {
    const indent = '  '.repeat(depth);
    let str = `${indent}- [${node.type}] ${node.name} (id: ${node.id})\n`;

    if (node.type === 'TEXT') {
        str += `${indent}  TEXT: "${node.characters?.replace(/\n/g, '\\n')}"\n`;
        if (node.style) {
            str += `${indent}  STYLE: font: ${node.style.fontFamily} ${node.style.fontWeight}, size: ${node.style.fontSize}, color: ${JSON.stringify(node.fills?.[0]?.color)}\n`;
        }
    }

    if (node.fills && node.fills.length > 0 && node.type !== 'TEXT') {
        const fill = node.fills[0];
        if (fill.type === 'SOLID') {
            const color = fill.color;
            str += `${indent}  BG: rgba(${Math.round(color.r * 255)}, ${Math.round(color.g * 255)}, ${Math.round(color.b * 255)}, ${color.a})\n`;
        }
    }

    if (node.layoutMode) {
        str += `${indent}  LAYOUT: ${node.layoutMode}, spacing: ${node.itemSpacing}, pad: ${node.paddingTop}t ${node.paddingBottom}b ${node.paddingLeft}l ${node.paddingRight}r\n`;
    }

    if (node.children) {
        for (const child of node.children) {
            str += parseNode(child, depth + 1);
        }
    }

    return str;
}

try {
    const data = JSON.parse(fs.readFileSync('figma_output_program.json', 'utf8'));
    const rootNodeId = Object.keys(data.nodes)[0];
    const rootNode = data.nodes[rootNodeId].document;

    const treeStr = parseNode(rootNode);
    fs.writeFileSync('figma_tree_program.txt', treeStr);
    console.log('Tree saved to figma_tree_program.txt');
} catch (e) {
    console.error(e);
}
