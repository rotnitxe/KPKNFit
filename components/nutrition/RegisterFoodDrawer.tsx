// components/nutrition/RegisterFoodDrawer.tsx
// Drawer lateral para registrar comida con parser de descripción y tags

import React, { useState, useMemo, useCallback } from 'react';
import type { NutritionLog, LoggedFood, FoodItem, Settings, PortionPreset } from '../../types';
import { PORTION_MULTIPLIERS } from '../../types';
import { parseNutritionDescription } from '../../utils/nutritionDescriptionParser';
import { searchFoods } from '../../services/foodSearchService';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { XIcon, PlusIcon, TrashIcon } from '../icons';
import Button from '../ui/Button';

const safeCreateISOStringFromDateInput = (dateString?: string): string => {
    if (dateString && /^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
        return new Date(dateString + 'T12:00:00Z').toISOString();
    }
    return new Date().toISOString();
};

interface TagWithFood {
    tag: string;
    portion: PortionPreset;
    foodItem: FoodItem | null;
    loggedFood: LoggedFood | null;
}

interface RegisterFoodDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    onSave: (log: NutritionLog) => void;
    settings: Settings;
    initialDate?: string;
    mealType?: NutritionLog['mealType'];
}

const mealOptions: { id: NutritionLog['mealType']; label: string }[] = [
    { id: 'breakfast', label: 'Desayuno' },
    { id: 'lunch', label: 'Almuerzo' },
    { id: 'dinner', label: 'Cena' },
    { id: 'snack', label: 'Snack' },
];

const portionLabels: Record<PortionPreset, string> = {
    small: 'Pequeño',
    medium: 'Mediano',
    large: 'Grande',
    extra: 'Extra',
};

export const RegisterFoodDrawer: React.FC<RegisterFoodDrawerProps> = ({
    isOpen,
    onClose,
    onSave,
    settings,
    initialDate,
    mealType: initialMealType = 'lunch',
}) => {
    const { addToast } = useAppDispatch();
    const [description, setDescription] = useState('');
    const [mealType, setMealType] = useState<NutritionLog['mealType']>(initialMealType);
    const [logDate, setLogDate] = useState(initialDate || new Date().toISOString().split('T')[0]);
    const [tagItems, setTagItems] = useState<TagWithFood[]>([]);
    const [isSearching, setIsSearching] = useState<Record<string, boolean>>({});

    const parsed = useMemo(() => parseNutritionDescription(description), [description]);

    const parseAndSetTags = useCallback(() => {
        if (!description.trim()) return;
        const { tags, portion } = parsed;
        if (tags.length === 0) {
            setTagItems([{ tag: description.trim(), portion, foodItem: null, loggedFood: null }]);
        } else {
            setTagItems(tags.map(tag => ({ tag, portion, foodItem: null, loggedFood: null })));
        }
    }, [description, parsed]);

    const handleDescriptionBlur = () => parseAndSetTags();
    const handleKeyDown = (e: React.KeyboardEvent) => e.key === 'Enter' && parseAndSetTags();

    const fetchFoodForTag = useCallback(async (tag: string, idx: number) => {
        setIsSearching(s => ({ ...s, [idx]: true }));
        try {
            const results = await searchFoods(tag, settings);
            const item = results[0] || null;
            setTagItems(prev => {
                const next = [...prev];
                if (next[idx]) {
                    next[idx] = { ...next[idx], foodItem: item };
                    if (item) {
                        const mult = PORTION_MULTIPLIERS[next[idx].portion];
                        const amount = item.servingSize * mult;
                        const ratio = amount / item.servingSize;
                        const logged: LoggedFood = {
                            id: crypto.randomUUID(),
                            foodName: item.name,
                            amount: Math.round(amount * 10) / 10,
                            unit: item.unit,
                            calories: Math.round((item.calories / item.servingSize) * amount),
                            protein: Math.round((item.protein / item.servingSize) * amount * 10) / 10,
                            carbs: Math.round((item.carbs / item.servingSize) * amount * 10) / 10,
                            fats: Math.round((item.fats / item.servingSize) * amount * 10) / 10,
                            tags: [tag],
                            portionPreset: next[idx].portion,
                        };
                        if (item.fatBreakdown) {
                            logged.fatBreakdown = {
                                saturated: Math.round((item.fatBreakdown.saturated || 0) * ratio * 10) / 10,
                                monounsaturated: Math.round((item.fatBreakdown.monounsaturated || 0) * ratio * 10) / 10,
                                polyunsaturated: Math.round((item.fatBreakdown.polyunsaturated || 0) * ratio * 10) / 10,
                                trans: Math.round((item.fatBreakdown.trans || 0) * ratio * 10) / 10,
                            };
                        }
                        if (item.micronutrients?.length) {
                            logged.micronutrients = item.micronutrients.map(m => ({
                                name: m.name,
                                amount: Math.round(m.amount * ratio * 10) / 10,
                                unit: m.unit,
                            }));
                        }
                        next[idx].loggedFood = logged;
                    }
                }
                return next;
            });
        } finally {
            setIsSearching(s => ({ ...s, [idx]: false }));
        }
    }, [settings]);

    const removeTag = (idx: number) => {
        setTagItems(prev => prev.filter((_, i) => i !== idx));
    };

    const setPortionForTag = (idx: number, portion: PortionPreset) => {
        setTagItems(prev => {
            const next = [...prev];
            if (next[idx]?.foodItem) {
                const item = next[idx].foodItem!;
                const mult = PORTION_MULTIPLIERS[portion];
                const amount = item.servingSize * mult;
                const ratio = amount / item.servingSize;
                const base = next[idx].loggedFood!;
                const logged: LoggedFood = {
                    ...base,
                    amount: Math.round(amount * 10) / 10,
                    calories: Math.round((item.calories / item.servingSize) * amount),
                    protein: Math.round((item.protein / item.servingSize) * amount * 10) / 10,
                    carbs: Math.round((item.carbs / item.servingSize) * amount * 10) / 10,
                    fats: Math.round((item.fats / item.servingSize) * amount * 10) / 10,
                    portionPreset: portion,
                };
                if (item.fatBreakdown) {
                    logged.fatBreakdown = {
                        saturated: Math.round((item.fatBreakdown.saturated || 0) * ratio * 10) / 10,
                        monounsaturated: Math.round((item.fatBreakdown.monounsaturated || 0) * ratio * 10) / 10,
                        polyunsaturated: Math.round((item.fatBreakdown.polyunsaturated || 0) * ratio * 10) / 10,
                        trans: Math.round((item.fatBreakdown.trans || 0) * ratio * 10) / 10,
                    };
                }
                if (item.micronutrients?.length) {
                    logged.micronutrients = item.micronutrients.map(m => ({
                        name: m.name,
                        amount: Math.round(m.amount * ratio * 10) / 10,
                        unit: m.unit,
                    }));
                }
                next[idx] = { ...next[idx], portion, loggedFood: logged };
            } else {
                next[idx] = { ...next[idx], portion };
            }
            return next;
        });
    };

    const totalMacros = useMemo(() => {
        return tagItems.reduce(
            (acc, t) => {
                if (t.loggedFood) {
                    acc.calories += t.loggedFood.calories;
                    acc.protein += t.loggedFood.protein;
                    acc.carbs += t.loggedFood.carbs;
                    acc.fats += t.loggedFood.fats;
                }
                return acc;
            },
            { calories: 0, protein: 0, carbs: 0, fats: 0 }
        );
    }, [tagItems]);

    const foods = useMemo(() => tagItems.filter(t => t.loggedFood).map(t => t.loggedFood!) as LoggedFood[], [tagItems]);

    const handleSave = () => {
        if (foods.length === 0) {
            addToast('Añade al menos un alimento o busca coincidencias.', 'danger');
            return;
        }
        const newLog: NutritionLog = {
            id: crypto.randomUUID(),
            date: safeCreateISOStringFromDateInput(logDate),
            mealType,
            foods,
            description: description.trim() || undefined,
            status: 'consumed',
        };
        onSave(newLog);
        addToast('Comida registrada.', 'success');
        setDescription('');
        setTagItems([]);
        onClose();
    };

    if (!isOpen) return null;

    return (
        <>
            <div className="fixed inset-0 z-[110] bg-black/40" onClick={onClose} />
            <div
                className="fixed top-0 right-0 bottom-0 w-full max-w-[400px] z-[111] bg-[#0a0a0a] border-l border-white/10 flex flex-col animate-slide-left"
                style={{ width: 'min(400px, 90vw)' }}
            >
                <div className="flex items-center justify-between px-4 py-3 border-b border-white/5 shrink-0">
                    <h3 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.2em]">Registrar Comida</h3>
                    <button onClick={onClose} className="text-zinc-500 hover:text-white transition-colors p-1">
                        <XIcon size={18} />
                    </button>
                </div>

                <div className="flex-1 overflow-y-auto custom-scrollbar p-4 space-y-4">
                    <div>
                        <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                            Descripción
                        </label>
                        <input
                            type="text"
                            value={description}
                            onChange={e => setDescription(e.target.value)}
                            onBlur={handleDescriptionBlur}
                            onKeyDown={handleKeyDown}
                            placeholder="Ej: plato grande de fideos con salsa y carne molida"
                            className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white placeholder-zinc-600 focus:border-white/30 outline-none"
                        />
                    </div>

                    <div>
                        <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                            Fecha y tipo
                        </label>
                        <div className="flex gap-2">
                            <input
                                type="date"
                                value={logDate}
                                onChange={e => setLogDate(e.target.value)}
                                className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-xs text-white"
                            />
                            <div className="flex gap-1 bg-white/5 p-1 rounded-lg">
                                {mealOptions.map(opt => (
                                    <button
                                        key={opt.id}
                                        onClick={() => setMealType(opt.id)}
                                        className={`px-2 py-1 rounded text-[9px] font-black uppercase transition-all ${
                                            mealType === opt.id ? 'bg-white text-black' : 'text-zinc-500 hover:text-zinc-300'
                                        }`}
                                    >
                                        {opt.label.substring(0, 3)}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>

                    {tagItems.length > 0 && (
                        <div>
                            <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                                Tags detectados
                            </label>
                            <div className="space-y-3">
                                {tagItems.map((t, idx) => (
                                    <div
                                        key={idx}
                                        className="bg-white/5 border border-white/10 rounded-xl p-3 flex flex-col gap-2"
                                    >
                                        <div className="flex justify-between items-center">
                                            <span className="text-sm font-bold text-white">{t.tag}</span>
                                            <button
                                                onClick={() => removeTag(idx)}
                                                className="text-zinc-500 hover:text-red-400 p-1"
                                            >
                                                <TrashIcon size={14} />
                                            </button>
                                        </div>
                                        <div className="flex gap-1">
                                            {(['small', 'medium', 'large', 'extra'] as PortionPreset[]).map(p => (
                                                <button
                                                    key={p}
                                                    onClick={() => setPortionForTag(idx, p)}
                                                    className={`px-2 py-1 rounded text-[9px] font-bold transition-all ${
                                                        t.portion === p
                                                            ? 'bg-white text-black'
                                                            : 'bg-white/5 text-zinc-500 hover:text-white'
                                                    }`}
                                                >
                                                    {portionLabels[p]}
                                                </button>
                                            ))}
                                        </div>
                                        {!t.foodItem && (
                                            <button
                                                onClick={() => fetchFoodForTag(t.tag, idx)}
                                                disabled={isSearching[idx]}
                                                className="flex items-center justify-center gap-2 py-2 text-[10px] font-bold text-zinc-400 hover:text-white bg-white/5 rounded-lg"
                                            >
                                                {isSearching[idx] ? 'Buscando...' : 'Buscar en base de datos'}
                                                <PlusIcon size={12} />
                                            </button>
                                        )}
                                        {t.loggedFood && (
                                            <p className="text-[10px] text-zinc-400 font-mono">
                                                {t.loggedFood.calories} kcal · {t.loggedFood.protein}g P
                                            </p>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {foods.length > 0 && (
                        <div className="bg-white/5 border border-white/10 rounded-xl p-4">
                            <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                                Resumen
                            </p>
                            <p className="text-2xl font-black text-white">
                                {Math.round(totalMacros.calories)} <span className="text-sm text-zinc-500">kcal</span>
                            </p>
                            <p className="text-xs text-zinc-400 mt-1">
                                P {totalMacros.protein.toFixed(0)}g · C {totalMacros.carbs.toFixed(0)}g · G{' '}
                                {totalMacros.fats.toFixed(0)}g
                            </p>
                        </div>
                    )}
                </div>

                <div className="p-4 border-t border-white/5 shrink-0">
                    <Button
                        onClick={handleSave}
                        disabled={foods.length === 0}
                        className={`w-full !py-3.5 !rounded-xl !text-sm font-black uppercase ${
                            foods.length > 0 ? 'bg-white text-black hover:bg-zinc-200' : 'bg-white/10 text-zinc-600 cursor-not-allowed'
                        }`}
                    >
                        {foods.length > 0 ? `Guardar (${Math.round(totalMacros.calories)} kcal)` : 'Busca coincidencias para guardar'}
                    </Button>
                </div>
            </div>
        </>
    );
};
