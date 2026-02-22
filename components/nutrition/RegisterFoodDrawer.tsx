// components/nutrition/RegisterFoodDrawer.tsx
// Drawer lateral para registrar comida con parser de descripción y tags

import React, { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import type { NutritionLog, LoggedFood, FoodItem, Settings, PortionPreset, CookingMethod } from '../../types';
import { useMealTemplateStore } from '../../stores/mealTemplateStore';
import { PORTION_MULTIPLIERS } from '../../types';
import { parseMealDescription } from '../../utils/nutritionDescriptionParser';
import { getCookingFactor } from '../../data/cookingMethodFactors';
import { searchFoods } from '../../services/foodSearchService';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { XIcon, TrashIcon, InfoIcon, UtensilsIcon } from '../icons';
import { FoodSearchModal } from '../FoodSearchModal';
import { MealTemplateSelector } from './MealTemplateSelector';
import Button from '../ui/Button';

const safeCreateISOStringFromDateInput = (dateString?: string): string => {
    if (dateString && /^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
        return new Date(dateString + 'T12:00:00Z').toISOString();
    }
    return new Date().toISOString();
};

const DEBOUNCE_MS = 300;

interface TagWithFood {
    tag: string;
    portion: PortionPreset;
    quantity: number;
    amountGrams?: number;
    cookingMethod?: CookingMethod;
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

const cookingLabels: Record<CookingMethod, string> = {
    crudo: 'Crudo',
    cocido: 'Cocido',
    plancha: 'Plancha',
    horno: 'Horno',
    frito: 'Frito',
    empanizado_frito: 'Empanizado',
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
    const { addMealTemplate } = useMealTemplateStore();
    const [description, setDescription] = useState('');
    const [mealType, setMealType] = useState<NutritionLog['mealType']>(initialMealType);
    const [logDate, setLogDate] = useState(initialDate || new Date().toISOString().split('T')[0]);
    const [tagItems, setTagItems] = useState<TagWithFood[]>([]);
    const [searchModalForTagIdx, setSearchModalForTagIdx] = useState<number | null>(null);
    const [expandedTagIdx, setExpandedTagIdx] = useState<number | null>(null);
    const [showHelp, setShowHelp] = useState(false);
    const [activeTab, setActiveTab] = useState<'description' | 'templates'>('description');
    const [showSaveTemplate, setShowSaveTemplate] = useState(false);
    const [templateName, setTemplateName] = useState('');
    const skipParseRef = useRef(false);

    const parsed = useMemo(() => parseMealDescription(description), [description]);

    const buildLoggedFromItem = useCallback((item: FoodItem, tag: TagWithFood): LoggedFood => {
        const amount =
            tag.amountGrams != null
                ? tag.amountGrams * tag.quantity
                : item.servingSize * PORTION_MULTIPLIERS[tag.portion] * tag.quantity;
        const ratio = amount / item.servingSize;
        const factor = tag.cookingMethod ? getCookingFactor(tag.cookingMethod) : { caloriesFactor: 1, fatsFactor: 1 };
        const baseCal = (item.calories / item.servingSize) * amount;
        const baseFats = (item.fats / item.servingSize) * amount;
        const logged: LoggedFood = {
            id: crypto.randomUUID(),
            foodName: item.name,
            amount: Math.round(amount * 10) / 10,
            unit: item.unit,
            calories: Math.round(baseCal * factor.caloriesFactor),
            protein: Math.round((item.protein / item.servingSize) * amount * 10) / 10,
            carbs: Math.round((item.carbs / item.servingSize) * amount * 10) / 10,
            fats: Math.round(baseFats * factor.fatsFactor * 10) / 10,
            tags: [tag.tag],
            portionPreset: tag.portion,
            quantity: tag.quantity,
            cookingMethod: tag.cookingMethod,
        };
        if (item.fatBreakdown) {
            logged.fatBreakdown = {
                saturated: Math.round((item.fatBreakdown.saturated || 0) * ratio * factor.fatsFactor * 10) / 10,
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
        return logged;
    }, []);

    const parseAndSetTags = useCallback(() => {
        if (skipParseRef.current) {
            skipParseRef.current = false;
            return;
        }
        if (!description.trim()) {
            setTagItems([]);
            return;
        }
        const { items } = parsed;
        if (items.length === 0) {
            setTagItems([
                {
                    tag: description.trim(),
                    portion: 'medium',
                    quantity: 1,
                    amountGrams: undefined,
                    cookingMethod: undefined,
                    foodItem: null,
                    loggedFood: null,
                },
            ]);
        } else {
            const newItems: TagWithFood[] = items.map(i => ({
                tag: i.tag,
                portion: (typeof i.portion === 'string' ? i.portion : 'medium') as PortionPreset,
                quantity: i.quantity,
                amountGrams: i.amountGrams,
                cookingMethod: i.cookingMethod,
                foodItem: null,
                loggedFood: null,
            }));
            setTagItems(newItems);
            newItems.forEach((tagData, idx) => {
                if (tagData.tag.length >= 2) {
                    searchFoods(tagData.tag, settings).then(results => {
                        const foodItem = results[0] || null;
                        if (foodItem) {
                            setTagItems(prev => {
                                const next = [...prev];
                                if (next[idx]?.tag === tagData.tag && !next[idx].foodItem) {
                                    next[idx] = {
                                        ...next[idx],
                                        foodItem,
                                        loggedFood: buildLoggedFromItem(foodItem, next[idx]),
                                    };
                                }
                                return next;
                            });
                        }
                    });
                }
            });
        }
    }, [description, parsed, settings, buildLoggedFromItem]);

    useEffect(() => {
        const t = setTimeout(() => parseAndSetTags(), DEBOUNCE_MS);
        return () => clearTimeout(t);
    }, [description, parseAndSetTags]);

    const handleDescriptionBlur = () => parseAndSetTags();
    const handleKeyDown = (e: React.KeyboardEvent) => e.key === 'Enter' && parseAndSetTags();

    const handleSearchModalSelect = useCallback((food: FoodItem, logged: LoggedFood, idx: number) => {
        setTagItems(prev => {
            const next = [...prev];
            if (next[idx]) {
                next[idx] = { ...next[idx], foodItem: food, loggedFood: logged };
            }
            return next;
        });
        setSearchModalForTagIdx(null);
    }, []);

    const removeTag = (idx: number) => {
        setTagItems(prev => prev.filter((_, i) => i !== idx));
        if (expandedTagIdx === idx) setExpandedTagIdx(null);
        else if (expandedTagIdx != null && expandedTagIdx > idx) setExpandedTagIdx(expandedTagIdx - 1);
    };

    const setPortionForTag = (idx: number, portion: PortionPreset) => {
        setTagItems(prev => {
            const next = [...prev];
            if (next[idx]?.foodItem) {
                const updated = { ...next[idx], portion };
                next[idx] = { ...updated, loggedFood: buildLoggedFromItem(next[idx].foodItem!, updated) };
            } else {
                next[idx] = { ...next[idx], portion };
            }
            return next;
        });
    };

    const setAmountGramsForTag = (idx: number, amountGrams: number | undefined) => {
        setTagItems(prev => {
            const next = [...prev];
            const updated = { ...next[idx], amountGrams };
            next[idx] = updated;
            if (next[idx]?.foodItem) {
                next[idx] = { ...updated, loggedFood: buildLoggedFromItem(next[idx].foodItem!, updated) };
            }
            return next;
        });
    };

    const setCookingForTag = (idx: number, cookingMethod: CookingMethod | undefined) => {
        setTagItems(prev => {
            const next = [...prev];
            const updated = { ...next[idx], cookingMethod };
            next[idx] = updated;
            if (next[idx]?.foodItem) {
                next[idx] = { ...updated, loggedFood: buildLoggedFromItem(next[idx].foodItem!, updated) };
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

    const handleTemplateSelect = useCallback((foodsFromTemplate: LoggedFood[]) => {
        skipParseRef.current = true;
        const items: TagWithFood[] = foodsFromTemplate.map(f => {
            const qty = f.quantity ?? 1;
            return {
                tag: f.foodName,
                portion: (f.portionPreset || 'medium') as PortionPreset,
                quantity: qty,
                amountGrams: f.amount / qty,
                cookingMethod: f.cookingMethod,
                foodItem: null,
                loggedFood: f,
            };
        });
        setTagItems(items);
        setDescription(foodsFromTemplate.map(f => `${f.quantity ?? 1}x ${f.foodName}`).join(', '));
        setActiveTab('description');
    }, []);

    const handleSaveAsTemplate = () => {
        const name = templateName.trim() || `Comida ${new Date().toLocaleDateString()}`;
        addMealTemplate({ name, description: description.trim() || name, foods });
        addToast(`Plantilla "${name}" guardada.`, 'success');
        setShowSaveTemplate(false);
        setTemplateName('');
    };

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
        setExpandedTagIdx(null);
        onClose();
    };

    const multiFoodWarning = tagItems.length >= 4;

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
                    <div className="flex items-center gap-1">
                        <button
                            onClick={() => setShowHelp(!showHelp)}
                            className="p-1.5 rounded-lg text-zinc-500 hover:text-white hover:bg-white/5 transition-colors"
                            title="Ayuda"
                        >
                            <InfoIcon size={18} />
                        </button>
                        <button onClick={onClose} className="text-zinc-500 hover:text-white transition-colors p-1">
                            <XIcon size={18} />
                        </button>
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto custom-scrollbar p-4 space-y-4">
                    {showHelp && (
                        <div className="bg-white/5 border border-white/10 rounded-xl p-4 space-y-3">
                            <h4 className="text-xs font-bold text-white">Cómo escribir la descripción</h4>
                            <ul className="text-[11px] text-zinc-400 space-y-1.5 list-disc list-inside">
                                <li><strong className="text-zinc-300">Gramos:</strong> 200g arroz, 150g pechuga</li>
                                <li><strong className="text-zinc-300">Cantidad:</strong> 2 huevos, 3 panes</li>
                                <li><strong className="text-zinc-300">Cocción:</strong> cocido, frito, a la plancha, al horno</li>
                                <li><strong className="text-zinc-300">Porción:</strong> grande, mediano, chico</li>
                                <li>Separa con comas o &quot;y&quot;: 200g arroz, 150g pollo</li>
                            </ul>
                            <p className="text-[10px] text-zinc-500">Ejemplos: 300g arroz cocido y 200g pechuga a la plancha</p>
                            <button
                                onClick={() => setShowHelp(false)}
                                className="text-[10px] font-bold text-zinc-500 hover:text-white"
                            >
                                Cerrar
                            </button>
                        </div>
                    )}

                    <div className="flex gap-1 bg-white/5 p-1 rounded-lg">
                        <button
                            onClick={() => setActiveTab('description')}
                            className={`flex-1 flex items-center justify-center gap-1.5 px-3 py-2 rounded-md text-[10px] font-bold uppercase transition-all ${
                                activeTab === 'description' ? 'bg-white text-black' : 'text-zinc-500 hover:text-zinc-300'
                            }`}
                        >
                            Comida
                        </button>
                        <button
                            onClick={() => setActiveTab('templates')}
                            className={`flex-1 flex items-center justify-center gap-1.5 px-3 py-2 rounded-md text-[10px] font-bold uppercase transition-all ${
                                activeTab === 'templates' ? 'bg-white text-black' : 'text-zinc-500 hover:text-zinc-300'
                            }`}
                        >
                            <UtensilsIcon size={14} />
                            Plantillas
                        </button>
                    </div>

                    {activeTab === 'templates' ? (
                        <div>
                            <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                                Plantillas guardadas
                            </label>
                            <MealTemplateSelector
                                onSelect={handleTemplateSelect}
                                onClose={() => setActiveTab('description')}
                            />
                        </div>
                    ) : (
                    <div>
                        <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                            Comida
                        </label>
                        <input
                            type="text"
                            value={description}
                            onChange={e => setDescription(e.target.value)}
                            onBlur={handleDescriptionBlur}
                            onKeyDown={handleKeyDown}
                            placeholder="Ej: 200g arroz cocido, 150g pechuga a la plancha"
                            className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white placeholder-zinc-600 focus:border-white/30 outline-none"
                        />
                        {tagItems.length > 0 && (
                            <div className="flex flex-wrap gap-2 mt-3">
                                {tagItems.map((t, idx) => (
                                    <div key={idx} className="relative">
                                        <button
                                            onClick={() => setExpandedTagIdx(prev => (prev === idx ? null : idx))}
                                            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all border ${
                                                t.loggedFood
                                                    ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
                                                    : 'bg-white/5 border-white/10 text-zinc-400 hover:border-white/30'
                                            }`}
                                        >
                                            {t.quantity > 1 && <span>{t.quantity}x</span>}
                                            {t.amountGrams != null && <span>{t.amountGrams}g</span>}
                                            <span>{t.tag}</span>
                                            {t.loggedFood && (
                                                <span className="font-mono text-[10px] opacity-80">
                                                    {t.loggedFood.calories}kcal
                                                </span>
                                            )}
                                        </button>
                                        {expandedTagIdx === idx && (
                                            <div className="absolute top-full left-0 mt-1 z-20 w-64 p-3 rounded-xl bg-[#111] border border-white/10 shadow-xl space-y-2">
                                                <div>
                                                    <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Gramos</label>
                                                    <input type="number" value={t.amountGrams ?? ''} onChange={e => setAmountGramsForTag(idx, e.target.value ? parseFloat(e.target.value) : undefined)} placeholder="Ej: 200" min={1} className="w-full bg-white/5 border border-white/10 rounded-lg px-2 py-1.5 text-sm text-white" />
                                                </div>
                                                <div>
                                                    <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Porción</label>
                                                    <div className="flex gap-1 flex-wrap">
                                                        {(['small', 'medium', 'large', 'extra'] as PortionPreset[]).map(p => (
                                                            <button key={p} onClick={() => setPortionForTag(idx, p)} className={`px-2 py-1 rounded text-[9px] font-bold ${t.portion === p ? 'bg-white text-black' : 'bg-white/5 text-zinc-500'}`}>{portionLabels[p]}</button>
                                                        ))}
                                                    </div>
                                                </div>
                                                <div>
                                                    <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Cocción</label>
                                                    <div className="flex gap-1 flex-wrap">
                                                        {(['crudo', 'cocido', 'plancha', 'horno', 'frito', 'empanizado_frito'] as CookingMethod[]).map(m => (
                                                            <button key={m} onClick={() => setCookingForTag(idx, t.cookingMethod === m ? undefined : m)} className={`px-2 py-1 rounded text-[9px] font-bold ${t.cookingMethod === m ? 'bg-white text-black' : 'bg-white/5 text-zinc-500'}`}>{cookingLabels[m]}</button>
                                                        ))}
                                                    </div>
                                                </div>
                                                <div className="flex justify-between pt-2 border-t border-white/5">
                                                    <button onClick={() => removeTag(idx)} className="text-[10px] text-red-400 font-bold">Eliminar</button>
                                                    {!t.foodItem && <button onClick={() => setSearchModalForTagIdx(idx)} className="text-[10px] text-cyan-400 font-bold">Elegir manual</button>}
                                                </div>
                                                {t.loggedFood && <p className="text-[10px] text-zinc-500 font-mono">{t.loggedFood.calories} kcal · P{t.loggedFood.protein} C{t.loggedFood.carbs} G{t.loggedFood.fats}</p>}
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}
                        {tagItems.length > 0 && foods.length > 0 && (
                            <div className="mt-3 flex items-center gap-4">
                                <span className="text-lg font-black text-white font-mono">{Math.round(totalMacros.calories)} kcal</span>
                                <span className="text-xs text-zinc-500">P {totalMacros.protein.toFixed(0)} · C {totalMacros.carbs.toFixed(0)} · G {totalMacros.fats.toFixed(0)}</span>
                            </div>
                        )}
                        {foods.length > 0 && !showSaveTemplate && (
                            <button
                                onClick={() => setShowSaveTemplate(true)}
                                className="mt-3 flex items-center gap-2 text-[10px] font-bold text-cyan-400 hover:text-cyan-300"
                            >
                                <UtensilsIcon size={14} />
                                Guardar como plantilla
                            </button>
                        )}
                        {showSaveTemplate && (
                            <div className="mt-3 p-3 rounded-xl bg-white/5 border border-white/10 space-y-2">
                                <input
                                    type="text"
                                    value={templateName}
                                    onChange={e => setTemplateName(e.target.value)}
                                    placeholder="Nombre de la plantilla"
                                    className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-zinc-600"
                                    autoFocus
                                />
                                <div className="flex gap-2">
                                    <button
                                        onClick={handleSaveAsTemplate}
                                        className="flex-1 py-2 rounded-lg bg-cyan-500/20 text-cyan-400 text-xs font-bold"
                                    >
                                        Guardar
                                    </button>
                                    <button
                                        onClick={() => { setShowSaveTemplate(false); setTemplateName(''); }}
                                        className="py-2 px-3 rounded-lg bg-white/5 text-zinc-500 text-xs font-bold"
                                    >
                                        Cancelar
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                    )}

                    {multiFoodWarning && (
                        <div className="flex items-start gap-2 p-3 rounded-xl bg-amber-950/30 border border-amber-500/20">
                            <InfoIcon size={16} className="text-amber-400 shrink-0 mt-0.5" />
                            <p className="text-[11px] text-amber-200/90">
                                Has añadido varios alimentos. Revisa que cada uno tenga la cantidad correcta.
                            </p>
                        </div>
                    )}

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

            {searchModalForTagIdx !== null && (
                <FoodSearchModal
                    isOpen={true}
                    onClose={() => setSearchModalForTagIdx(null)}
                    onSelect={() => {}}
                    onSelectWithFood={(food, logged) => handleSearchModalSelect(food, logged, searchModalForTagIdx!)}
                />
            )}
        </>
    );
};
