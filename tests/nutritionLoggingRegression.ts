import assert from 'node:assert/strict';

import { parseFreeFormNutrition } from '../services/aiNutritionParser';
import { searchFoods } from '../services/foodSearchService';
import { parseMealDescription } from '../utils/nutritionDescriptionParser';

async function main() {
    const parsedMeal = parseMealDescription('2 huevos con pan integral');
    assert.ok(parsedMeal.items.length >= 2, 'La descripcion simple debe separarse en al menos dos items.');

    const parsedTags = parsedMeal.items.map((item) => item.tag.toLowerCase());
    assert.ok(parsedTags.some((tag) => tag.includes('huevo')), 'El parser debe reconocer huevo.');
    assert.ok(parsedTags.some((tag) => tag.includes('pan')), 'El parser debe reconocer pan.');

    const rulesOnly = await parseFreeFormNutrition('150g pollo con arroz', null, { mode: 'rules' });
    assert.ok(rulesOnly.items.length >= 1, 'El modo rules debe producir al menos un item.');
    assert.equal(rulesOnly.rawDescription, '150g pollo con arroz');

    const deterministicDefault = await parseFreeFormNutrition('arroz con pollo');
    assert.equal(deterministicDefault.analysisEngine, 'deterministic');

    const protectedComposite = parseMealDescription('arroz con pollo');
    assert.ok(
        protectedComposite.items.some((item) => item.tag.toLowerCase().includes('arroz con pollo')),
        'Los platos compuestos protegidos no deben romperse por el conector "con".'
    );

    const searchResult = await searchFoods('arroz con pollo');
    assert.ok(searchResult.results.length > 0, 'La busqueda local debe devolver resultados.');
    assert.ok(
        searchResult.results.some((food) => {
            const name = food.name.toLowerCase();
            return name.includes('arroz') && name.includes('pollo');
        }),
        'La busqueda debe encontrar una opcion coherente para "arroz con pollo".'
    );

    const localDishResult = await searchFoods('charquican');
    assert.ok(
        localDishResult.results.some((food) => food.name.toLowerCase().includes('charquic')),
        'La base offline local debe reconocer platos chilenos curados como charquican.'
    );

    const sandwichResult = await searchFoods('pan con palta y jamon');
    assert.ok(
        sandwichResult.results.some((food) => {
            const name = food.name.toLowerCase();
            return name.includes('palta') && name.includes('jam');
        }),
        'La busqueda debe cubrir combinaciones locales curadas como pan con palta y jamon.'
    );

    console.log('Nutrition logging regression checks passed.');
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
