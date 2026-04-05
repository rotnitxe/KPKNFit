const https = require('https');
const fs = require('fs');

const options = {
    hostname: 'api.figma.com',
    path: '/v1/files/u7JsO9AT52NPXVLhmYiMeq/nodes?ids=1-2645',
    method: 'GET',
    headers: {
        'X-Figma-Token': process.env.FIGMA_TOKEN
    }
};

const req = https.request(options, (res) => {
    let data = '';
    res.on('data', (chunk) => { data += chunk; });
    res.on('end', () => {
        try {
            const json = JSON.parse(data);
            function traverse(node, depth = 0) {
                let text = node.name;
                if (node.type === 'TEXT') {
                    text += `: '${node.characters}'`;
                }
                console.log(' '.repeat(depth * 2) + text);
                if (node.children) {
                    node.children.forEach(c => traverse(c, depth + 1));
                }
            }
            traverse(json.nodes['1:2645'].document);
        } catch (e) {
            console.error(e);
        }
    });
});
req.on('error', console.error);
req.end();
