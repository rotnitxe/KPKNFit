import { create } from 'zustand';
import { generateId } from '../utils/generateId';
import type { FoodItem } from '../types/food';
import type { PantryItem } from '../types/pantry';
import {
  getStoredPantrySource,
  persistStoredPantryItemsRaw,
  readStoredPantryItemsRaw,
  type StoredPantrySource,
} from '../services/mobileDomainStateService';

type PantryStatus = 'idle' | 'ready' | 'empty';

interface PantryStoreState {
  status: PantryStatus;
  source: StoredPantrySource;
  items: PantryItem[];
  notice: string | null;
  hydrateFromStorage: () => Promise<void>;
  addCustomItem: (item: Omit<PantryItem, 'id'>) => void;
  addFoodToPantry: (food: FoodItem) => void;
  updateItem: (id: string, patch: Partial<Omit<PantryItem, 'id'>>) => void;
  removeItem: (id: string) => void;
  clearNotice: () => void;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asNumber(value: unknown, fallback = 0) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function asString(value: unknown, fallback = '') {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : fallback;
}

function sanitizePantryItem(raw: unknown): PantryItem | null {
  if (!isRecord(raw)) return null;

  const id = asString(raw.id);
  const name = asString(raw.name);
  if (!id || !name) return null;

  return {
    id,
    name,
    calories: asNumber(raw.calories),
    protein: asNumber(raw.protein),
    carbs: asNumber(raw.carbs),
    fats: asNumber(raw.fats),
    currentQuantity: asNumber(raw.currentQuantity, 100),
    unit: asString(raw.unit, 'g'),
  };
}

function persistItems(items: PantryItem[]) {
  persistStoredPantryItemsRaw(items);
  return items;
}

export const usePantryStore = create<PantryStoreState>((set, get) => ({
  status: 'idle',
  source: 'empty',
  items: [],
  notice: null,

  hydrateFromStorage: async () => {
    const items = readStoredPantryItemsRaw()
      .map(sanitizePantryItem)
      .filter((value): value is PantryItem => value !== null);

    set({
      items,
      source: getStoredPantrySource(),
      status: items.length > 0 ? 'ready' : 'empty',
    });
  },

  addCustomItem: (item) => {
    const current = get().items;
    const next: PantryItem[] = persistItems([
      {
        id: generateId(),
        ...item,
      },
      ...current,
    ]);

    set({
      items: next,
      status: 'ready',
      source: 'rn-owned',
      notice: `${item.name} añadido a tu despensa.`,
    });
  },

  addFoodToPantry: (food) => {
    const current = get().items;
    const existingIndex = current.findIndex(
      item => item.name.trim().toLowerCase() === food.name.trim().toLowerCase(),
    );

    let next: PantryItem[];
    let notice: string;

    if (existingIndex >= 0) {
      next = [...current];
        next[existingIndex] = {
        ...next[existingIndex],
        currentQuantity: next[existingIndex].currentQuantity + 100,
        calories: food.calories,
        protein: food.protein,
        carbs: food.carbs,
        fats: food.fat ?? 0,
        };
      notice = `${food.name} actualizado en despensa (+100g).`;
    } else {
      next = [
          {
            id: generateId(),
            name: food.name,
            calories: food.calories,
            protein: food.protein,
            carbs: food.carbs,
            fats: food.fat ?? 0,
            currentQuantity: 100,
            unit: 'g',
          },
        ...current,
      ];
      notice = `${food.name} añadido a despensa.`;
    }

    persistItems(next);
    set({
      items: next,
      status: 'ready',
      source: 'rn-owned',
      notice,
    });
  },

  updateItem: (id, patch) => {
    const current = get().items;
    const next = current.map(item =>
      item.id === id
        ? {
            ...item,
            ...patch,
          }
        : item,
    );

    persistItems(next);
    set({
      items: next,
      status: next.length > 0 ? 'ready' : 'empty',
      source: 'rn-owned',
    });
  },

  removeItem: (id) => {
    const current = get().items;
    const removed = current.find(item => item.id === id);
    const next = current.filter(item => item.id !== id);

    persistItems(next);
    set({
      items: next,
      status: next.length > 0 ? 'ready' : 'empty',
      source: 'rn-owned',
      notice: removed ? `${removed.name} eliminado de despensa.` : null,
    });
  },

  clearNotice: () => set({ notice: null }),
}));
