const fs = require('fs');

async function fetchFigma() {
    const token = 'figd_mrYkFgprzoHqxPyJpJCKH03huvGyDjTPyBz8RMjj';
    const fileKey = 'vlc7QqOtOumcCRMq6DxJgN';
    const nodeId = '1:2840';

    console.log('Fetching from Figma API...');
    const res = await fetch(`https://api.figma.com/v1/files/${fileKey}/nodes?ids=${nodeId}`, {
        headers: { 'X-Figma-Token': token }
    });

    if (!res.ok) {
        console.error('Failed to fetch:', res.status, res.statusText);
        const text = await res.text();
        console.error(text);
        process.exit(1);
    }

    const data = await res.json();
    fs.writeFileSync('figma_output_program.json', JSON.stringify(data, null, 2));
    console.log('Saved to figma_output_program.json');
}

fetchFigma();
