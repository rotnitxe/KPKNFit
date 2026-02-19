import { Settings } from '../types';

export const KG_PLATES = [25, 20, 15, 10, 5, 2.5, 1.25, 0.5, 0.25];
export const LBS_PLATES = [45, 35, 25, 10, 5, 2.5, 1];

export interface PlateCombination {
    platesPerSide: { plate: number, count: number }[];
    remainder: number;
}

export const calculatePlates = (
    totalWeight: number,
    settings: Settings
): PlateCombination => {
    const { weightUnit, barbellWeight } = settings;
    const availablePlates = weightUnit === 'kg' ? KG_PLATES : LBS_PLATES;
    
    if (totalWeight <= barbellWeight) {
        return { platesPerSide: [], remainder: 0 };
    }

    const weightOnBar = totalWeight - barbellWeight;
    let weightPerSide = weightOnBar / 2;
    
    const platesPerSide: { plate: number, count: number }[] = [];

    for (const plate of availablePlates) {
        if (weightPerSide >= plate) {
            const count = Math.floor(weightPerSide / plate);
            platesPerSide.push({ plate, count });
            weightPerSide -= count * plate;
        }
    }

    return {
        platesPerSide,
        remainder: Math.round(weightPerSide * 2 * 100) / 100, // Total remaining weight
    };
};
