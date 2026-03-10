import type { ParsedMealDescription, ParsedMealItem, Settings } from '../types';
import { parseMealDescription as fallbackParse } from '../utils/nutritionDescriptionParser';
import { generateContent } from './backendAIService';
import { getNutritionConnectivity } from './nutritionConnectivityService';

const DEFAULT_LOCAL_MODEL = 'gemma3:4b';
const AI_TIMEOUT_MS = 4_500;

const NUTRITION_SYSTEM_PROMPT = `
Eres un asistente nutricional avanzado.
Debes convertir una descripcion libre de comida a JSON estructurado.
Extrae cada alimento con su mejor etiqueta canonica, cantidad, gramos estimados, marca, coccion y modificadores.
Si hay una receta compuesta, usa "subItems" y marca "isGroup": true.
Usa "portion" solo con estos valores: "small", "medium", "large", "extra".
Si no sabes un campo, omite el campo o usa null.

FORMATO DE SALIDA:
{
  "items": [
    {
      "tag": "pollo a la plancha",
      "quantity": 1,
      "amountGrams": 150,
      "portion": "medium",
      "brandHint": "marca si existe",
      "cookingMethod": "plancha",
      "anatomicalModifiers": ["sin_piel"],
      "heuristicModifiers": ["light"],
      "preparationModifiers": ["picado"],
      "stateModifiers": ["en_polvo"],
      "compositionModifiers": ["bajo_sodio"],
      "dimensionalMultiplier": 1,
      "isGroup": false,
      "subItems": []
    }
  ],
  "rawDescription": "texto original"
}
`;

export interface FreeFormNutritionOptions {
    mode?: 'auto' | 'rules' | 'local-ai';
    provider?: 'ollama' | 'gemini' | 'gpt';
}

function withTimeout<T>(promise: Promise<T>, timeoutMs = AI_TIMEOUT_MS): Promise<T> {
    return Promise.race([
        promise,
        new Promise<T>((_, reject) => setTimeout(() => reject(new Error('timeout')), timeoutMs)),
    ]);
}

function sanitizeItem(item: any): ParsedMealItem {
    return {
        tag: item?.tag || 'Desconocido',
        quantity: typeof item?.quantity === 'number' && item.quantity > 0 ? item.quantity : 1,
        amountGrams: typeof item?.amountGrams === 'number' && item.amountGrams > 0 ? item.amountGrams : undefined,
        portion: typeof item?.portion === 'string' ? item.portion : 'medium',
        brandHint: item?.brandHint || undefined,
        cookingMethod: item?.cookingMethod || undefined,
        anatomicalModifiers: Array.isArray(item?.anatomicalModifiers) && item.anatomicalModifiers.length ? item.anatomicalModifiers : undefined,
        heuristicModifiers: Array.isArray(item?.heuristicModifiers) && item.heuristicModifiers.length ? item.heuristicModifiers : undefined,
        preparationModifiers: Array.isArray(item?.preparationModifiers) && item.preparationModifiers.length ? item.preparationModifiers : undefined,
        stateModifiers: Array.isArray(item?.stateModifiers) && item.stateModifiers.length ? item.stateModifiers : undefined,
        compositionModifiers: Array.isArray(item?.compositionModifiers) && item.compositionModifiers.length ? item.compositionModifiers : undefined,
        dimensionalMultiplier: typeof item?.dimensionalMultiplier === 'number' ? item.dimensionalMultiplier : undefined,
        isGroup: Boolean(item?.isGroup),
        subItems: Array.isArray(item?.subItems) ? item.subItems.map(sanitizeItem) : undefined,
    };
}

function sanitizeParsedMeal(data: any, description: string): ParsedMealDescription | null {
    if (!data || !Array.isArray(data.items)) return null;

    const items = data.items
        .map(sanitizeItem)
        .filter((item: ParsedMealItem) => item.tag && item.tag.trim().length > 0);

    if (items.length === 0) return null;
    return {
        items,
        rawDescription: data.rawDescription || description,
    };
}

export async function parseFreeFormNutrition(
    description: string,
    settings?: Settings | null,
    options: FreeFormNutritionOptions = {}
): Promise<ParsedMealDescription> {
    const trimmed = description.trim();
    if (!trimmed) {
        return { items: [], rawDescription: '' };
    }

    const mode = options.mode ?? settings?.nutritionDescriptionMode ?? 'auto';
    if (mode === 'rules') {
        return fallbackParse(trimmed);
    }

    const connectivity = await getNutritionConnectivity(settings);
    const shouldUseLocalAi = (settings?.nutritionUseLocalAI ?? true)
        && (mode === 'local-ai' || mode === 'auto')
        && connectivity.localAiAvailable;

    if (!shouldUseLocalAi) {
        return fallbackParse(trimmed);
    }

    try {
        const payload = await withTimeout(generateContent({
            provider: options.provider ?? 'ollama',
            prompt: `Analiza esta comida: "${trimmed}"`,
            systemInstruction: NUTRITION_SYSTEM_PROMPT,
            jsonMode: true,
            temperature: 0.15,
            maxTokens: 1200,
            model: settings?.nutritionLocalModel || connectivity.localAiModel || DEFAULT_LOCAL_MODEL,
        }));

        const parsed = sanitizeParsedMeal(payload, trimmed);
        return parsed ?? fallbackParse(trimmed);
    } catch (error) {
        console.warn('[aiNutritionParser] falling back to rules parser:', error);
        return fallbackParse(trimmed);
    }
}
