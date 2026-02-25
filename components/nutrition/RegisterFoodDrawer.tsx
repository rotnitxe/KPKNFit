// components/nutrition/RegisterFoodDrawer.tsx
// Drawer lateral para registrar comida con parser de descripción y tags

import React, { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import type { NutritionLog, LoggedFood, FoodItem, Settings, PortionPreset, CookingMethod, PortionInput, PortionReference } from '../../types';
import type { SearchMatchType } from '../../services/foodSearchService';
import { useMealTemplateStore } from '../../stores/mealTemplateStore';
import { PORTION_MULTIPLIERS } from '../../types';
import { PORTION_REFERENCES, getGramsForReference, getFoodTypeForPortion } from '../../data/portionReferences';
import { parseMealDescription } from '../../utils/nutritionDescriptionParser';
import { getCookingFactor, getEffectiveAmountForMacros } from '../../data/cookingMethodFactors';
import { searchFoods } from '../../services/foodSearchService';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { XIcon, TrashIcon, InfoIcon, UtensilsIcon, SearchIcon } from '../icons';
import { FoodSearchModal } from '../FoodSearchModal';
import { MealTemplateSelector } from './MealTemplateSelector';
import { PortionSelector } from '../PortionSelector';
import Button from '../ui/Button';
import { getLocalDateString, dateStringToISOString } from '../../utils/dateUtils';

function foodToLoggedFood(food: FoodItem, amountGrams: number, portionInput?: PortionInput): LoggedFood {
    const ratio = amountGrams / food.servingSize;
    const logged: LoggedFood = {
        id: crypto.randomUUID(),
        foodName: food.name,
        amount: Math.round(amountGrams * 10) / 10,
        unit: food.unit || 'g',
        calories: Math.round((food.calories / food.servingSize) * amountGrams),
        protein: Math.round((food.protein / food.servingSize) * amountGrams * 10) / 10,
        carbs: Math.round((food.carbs / food.servingSize) * amountGrams * 10) / 10,
        fats: Math.round((food.fats / food.servingSize) * amountGrams * 10) / 10,
        fatBreakdown: food.fatBreakdown ? {
            saturated: Math.round((food.fatBreakdown.saturated || 0) * ratio * 10) / 10,
            monounsaturated: Math.round((food.fatBreakdown.monounsaturated || 0) * ratio * 10) / 10,
            polyunsaturated: Math.round((food.fatBreakdown.polyunsaturated || 0) * ratio * 10) / 10,
            trans: Math.round((food.fatBreakdown.trans || 0) * ratio * 10) / 10,
        } : undefined,
    };
    if (portionInput) logged.portionInput = portionInput;
    return logged;
}

const safeCreateISOStringFromDateInput = (dateString?: string): string => {
    if (dateString && /^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
        return dateStringToISOString(dateString);
    }
    return new Date().toISOString();
};

const DEBOUNCE_MS = 200;

interface TagWithFood {
    tag: string;
    portion: PortionPreset;
    quantity: number;
    amountGrams?: number;
    cookingMethod?: CookingMethod;
    foodItem: FoodItem | null;
    loggedFood: LoggedFood | null;
    /** true cuando el foodItem provino de búsqueda fuzzy/parcial */
    isFuzzyMatch?: boolean;
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
    const [logDate, setLogDate] = useState(initialDate || getLocalDateString());
    const [tagItems, setTagItems] = useState<TagWithFood[]>([]);
    const [searchModalForTagIdx, setSearchModalForTagIdx] = useState<number | null>(null);
    const [expandedTagIdx, setExpandedTagIdx] = useState<number | null>(null);
    const [showHelp, setShowHelp] = useState(false);
    const [activeTab, setActiveTab] = useState<'description' | 'search' | 'hybrid' | 'templates'>('description');
    const [showSaveTemplate, setShowSaveTemplate] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState<FoodItem[]>([]);
    const [searchMatchType, setSearchMatchType] = useState<SearchMatchType>('exact');
    const [searchLoading, setSearchLoading] = useState(false);
    const [selectedFoodForPortion, setSelectedFoodForPortion] = useState<FoodItem | null>(null);
    const [hybridSuggestions, setHybridSuggestions] = useState<FoodItem[]>([]);
    const [hybridMatchType, setHybridMatchType] = useState<SearchMatchType>('exact');
    const [hybridSuggestionsLoading, setHybridSuggestionsLoading] = useState(false);
    const [templateName, setTemplateName] = useState('');
    const skipParseRef = useRef(false);

    const parsed = useMemo(() => parseMealDescription(description), [description]);

    const hasStructuredContent = useCallback((text: string): boolean => {
        const t = text.trim();
        if (!t || t.length < 2) return false;
        return (
            /\d{1,4}\s*(g|gr|gramos?|kg)/i.test(t) ||
            /^(dos|tres|cuatro|cinco|un|una|medio|media|doble|triple)\s+\w+/i.test(t) ||
            /^\d+\s+(x\s*)?\w+/.test(t) ||
            /(cocido|cocida|frito|frita|plancha|horno|crudo|empanizado)/i.test(t) ||
            t.includes(',') ||
            /\s+y\s+/.test(t) ||
            /\s+con\s+/.test(t)
        );
    }, []);

    const buildLoggedFromItem = useCallback((item: FoodItem, tag: TagWithFood): LoggedFood => {
        const amount =
            tag.amountGrams != null
                ? tag.amountGrams * tag.quantity
                : item.servingSize * PORTION_MULTIPLIERS[tag.portion] * tag.quantity;
        const effectiveAmount = getEffectiveAmountForMacros(amount, item, tag.cookingMethod);
        const ratio = effectiveAmount / item.servingSize;
        const factor = tag.cookingMethod ? getCookingFactor(tag.cookingMethod) : { caloriesFactor: 1, fatsFactor: 1 };
        const baseCal = (item.calories / item.servingSize) * effectiveAmount;
        const baseFats = (item.fats / item.servingSize) * effectiveAmount;
        const logged: LoggedFood = {
            id: crypto.randomUUID(),
            foodName: item.name,
            amount: Math.round(amount * 10) / 10,
            unit: item.unit,
            calories: Math.round(baseCal * factor.caloriesFactor),
            protein: Math.round((item.protein / item.servingSize) * effectiveAmount * 10) / 10,
            carbs: Math.round((item.carbs / item.servingSize) * effectiveAmount * 10) / 10,
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
                isFuzzyMatch: i.isFuzzyMatch,
            }));
            setTagItems(newItems);
            newItems.forEach((tagData, idx) => {
                if (tagData.tag.length >= 2) {
                    searchFoods(tagData.tag, settings).then(({ results, matchType }) => {
                        const foodItem = results[0] || null;
                        if (foodItem) {
                            setTagItems(prev => {
                                const next = [...prev];
                                if (next[idx]?.tag === tagData.tag && !next[idx].foodItem) {
                                    next[idx] = {
                                        ...next[idx],
                                        foodItem,
                                        loggedFood: buildLoggedFromItem(foodItem, next[idx]),
                                        isFuzzyMatch: tagData.isFuzzyMatch || matchType === 'fuzzy',
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
        if (activeTab !== 'description' && activeTab !== 'hybrid') return;
        if (activeTab === 'hybrid' && description.trim() && !hasStructuredContent(description)) {
            return;
        }
        const t = setTimeout(() => parseAndSetTags(), DEBOUNCE_MS);
        return () => clearTimeout(t);
    }, [description, parseAndSetTags, activeTab, hasStructuredContent]);

    const doSearch = useCallback(async (q: string) => {
        if (!q.trim()) {
            setSearchResults([]);
            return;
        }
        setSearchLoading(true);
        try {
            const { results, matchType } = await searchFoods(q, settings);
            setSearchResults(results);
            setSearchMatchType(matchType);
        } finally {
            setSearchLoading(false);
        }
    }, [settings]);

    useEffect(() => {
        if (activeTab !== 'search') return;
        const t = setTimeout(() => doSearch(searchQuery), DEBOUNCE_MS);
        return () => clearTimeout(t);
    }, [searchQuery, activeTab, doSearch]);

    const handleSearchPortionConfirm = useCallback((portion: PortionInput, amountGrams: number) => {
        if (!selectedFoodForPortion) return;
        const logged = foodToLoggedFood(selectedFoodForPortion, amountGrams, portion);
        const tag: TagWithFood = {
            tag: selectedFoodForPortion.name,
            portion: 'medium',
            quantity: 1,
            amountGrams,
            cookingMethod: undefined,
            foodItem: selectedFoodForPortion,
            loggedFood: logged,
            isFuzzyMatch: searchMatchType === 'fuzzy',
        };
        setTagItems(prev => [...prev, tag]);
        setSelectedFoodForPortion(null);
        setSearchQuery('');
    }, [selectedFoodForPortion, searchMatchType]);

    const addTagFromFoodItem = useCallback((food: FoodItem, amountGrams?: number, isFuzzy?: boolean) => {
        const grams = amountGrams ?? food.servingSize * PORTION_MULTIPLIERS['medium'];
        const logged = foodToLoggedFood(food, grams);
        const tag: TagWithFood = {
            tag: food.name,
            portion: 'medium',
            quantity: 1,
            amountGrams: grams,
            cookingMethod: undefined,
            foodItem: food,
            loggedFood: logged,
            isFuzzyMatch: isFuzzy,
        };
        setTagItems(prev => [...prev, tag]);
    }, []);

    useEffect(() => {
        if (activeTab !== 'hybrid' || !description.trim()) {
            setHybridSuggestions([]);
            return;
        }
        if (hasStructuredContent(description)) {
            setHybridSuggestions([]);
            return;
        }
        const t = setTimeout(async () => {
            setHybridSuggestionsLoading(true);
            try {
                const { results, matchType } = await searchFoods(description.trim(), settings);
                setHybridSuggestions(results);
                setHybridMatchType(matchType);
            } finally {
                setHybridSuggestionsLoading(false);
            }
        }, DEBOUNCE_MS);
        return () => clearTimeout(t);
    }, [description, activeTab, settings, hasStructuredContent]);

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

    const setPortionReferenceForTag = (idx: number, ref: PortionReference) => {
        const tag = tagItems[idx];
        const food = tag?.foodItem;
        const foodType = food ? getFoodTypeForPortion(food) : 'mixed';
        const grams = getGramsForReference(ref, foodType);
        setAmountGramsForTag(idx, grams);
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
        setSelectedFoodForPortion(null);
        setSearchQuery('');
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
                                <li><strong className="text-zinc-300">Referencias:</strong> 1 cucharada de aceite, una taza de arroz, un toque de sal, una palma de pollo</li>
                                <li><strong className="text-zinc-300">Cantidad:</strong> 2 huevos, 3 panes</li>
                                <li><strong className="text-zinc-300">Cocción:</strong> cocido, frito, a la plancha, al horno. Si pesas cocido (pollo, carnes) o hidratado (soya), los macros se ajustan automáticamente.</li>
                                <li><strong className="text-zinc-300">Porción:</strong> grande, mediano, chico</li>
                                <li>Separa con comas o &quot;y&quot;: 200g arroz, 150g pollo</li>
                            </ul>
                            <p className="text-[10px] text-zinc-500">Ejemplos: 1 cucharada de aceite, una taza de arroz cocido y una palma de pechuga a la plancha</p>
                            <button
                                onClick={() => setShowHelp(false)}
                                className="text-[10px] font-bold text-zinc-500 hover:text-white"
                            >
                                Cerrar
                            </button>
                        </div>
                    )}

                    <div className="flex gap-1 bg-white/5 p-1 rounded-lg overflow-x-auto">
                        <button
                            onClick={() => setActiveTab('description')}
                            className={`shrink-0 flex items-center justify-center gap-1 px-2 py-2 rounded-md text-[10px] font-bold uppercase transition-all ${
                                activeTab === 'description' ? 'bg-white text-black' : 'text-zinc-500 hover:text-zinc-300'
                            }`}
                        >
                            Comida
                        </button>
                        <button
                            onClick={() => setActiveTab('search')}
                            className={`shrink-0 flex items-center justify-center gap-1 px-2 py-2 rounded-md text-[10px] font-bold uppercase transition-all ${
                                activeTab === 'search' ? 'bg-white text-black' : 'text-zinc-500 hover:text-zinc-300'
                            }`}
                        >
                            <SearchIcon size={12} />
                            Buscar
                        </button>
                        <button
                            onClick={() => setActiveTab('hybrid')}
                            className={`shrink-0 flex items-center justify-center gap-1 px-2 py-2 rounded-md text-[10px] font-bold uppercase transition-all ${
                                activeTab === 'hybrid' ? 'bg-white text-black' : 'text-zinc-500 hover:text-zinc-300'
                            }`}
                        >
                            Híbrido
                        </button>
                        <button
                            onClick={() => setActiveTab('templates')}
                            className={`shrink-0 flex items-center justify-center gap-1 px-2 py-2 rounded-md text-[10px] font-bold uppercase transition-all ${
                                activeTab === 'templates' ? 'bg-white text-black' : 'text-zinc-500 hover:text-zinc-300'
                            }`}
                        >
                            <UtensilsIcon size={12} />
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
                    ) : activeTab === 'search' ? (
                        <div className="space-y-4 animate-fade-in">
                            <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                                Buscar alimento
                            </label>
                            {!selectedFoodForPortion ? (
                                <>
                                    <div className="relative">
                                        <SearchIcon size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
                                        <input
                                            type="text"
                                            value={searchQuery}
                                            onChange={e => setSearchQuery(e.target.value)}
                                            placeholder="Ej: pechuga de pollo, arroz..."
                                            className="w-full bg-white/5 border border-white/10 rounded-xl pl-10 pr-4 py-3 text-sm text-white placeholder-zinc-600 focus:border-white/30 outline-none"
                                        />
                                    </div>
                                    {searchLoading && <p className="text-xs text-zinc-500">Buscando...</p>}
                                    {!searchLoading && searchMatchType === 'fuzzy' && searchResults.length > 0 && (
                                        <p className="text-xs text-amber-400/90 bg-amber-500/10 rounded-lg px-3 py-2">
                                            No hay coincidencia exacta. Mostrando los resultados más cercanos.
                                        </p>
                                    )}
                                    <div className="overflow-y-auto max-h-48 custom-scrollbar space-y-0.5">
                                        {searchResults.map(food => (
                                            <button
                                                key={food.id}
                                                onClick={() => setSelectedFoodForPortion(food)}
                                                className="w-full text-left px-3 py-2.5 rounded-lg hover:bg-white/5 transition-colors flex items-center gap-3"
                                            >
                                                <div className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center shrink-0">
                                                    {food.image ? (
                                                        <img src={food.image} alt="" className="w-8 h-8 rounded-lg object-cover" />
                                                    ) : (
                                                        <UtensilsIcon size={14} className="text-zinc-500" />
                                                    )}
                                                </div>
                                                <div className="min-w-0 flex-1">
                                                    <p className="text-sm font-medium text-white truncate">{food.name}</p>
                                                    <p className="text-[10px] text-zinc-500 font-mono">
                                                        {Math.round(food.calories)} kcal · P:{food.protein} C:{food.carbs} F:{food.fats}
                                                    </p>
                                                </div>
                                            </button>
                                        ))}
                                        {!searchLoading && searchQuery && searchResults.length === 0 && (
                                            <p className="text-center text-sm text-zinc-500 py-6">No se encontraron resultados</p>
                                        )}
                                    </div>
                                </>
                            ) : (
                                <PortionSelector
                                    food={selectedFoodForPortion}
                                    onConfirm={handleSearchPortionConfirm}
                                    onCancel={() => setSelectedFoodForPortion(null)}
                                />
                            )}
                            {tagItems.length > 0 && (
                                <div className="pt-2 border-t border-white/10">
                                    <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                                        Añadidos
                                    </label>
                                    <div className="flex flex-wrap gap-2">
                                        {tagItems.map((t, idx) => (
                                            <div key={idx} className="relative">
                                                <button
                                                    onClick={() => setExpandedTagIdx(prev => (prev === idx ? null : idx))}
                                                    className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all border ${
                                                        t.loggedFood
                                                            ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
                                                            : 'bg-white/5 border-white/10 text-zinc-400 hover:border-white/30'
                                                    }`}
                                                    title={t.isFuzzyMatch ? `No encontramos exactamente "${t.tag}"; usamos el alimento más parecido.` : undefined}
                                                >
                                                    {t.quantity > 1 && <span>{t.quantity}x</span>}
                                                    {t.amountGrams != null && <span>{t.amountGrams}g</span>}
                                                    <span>{t.tag}</span>
                                                    {t.isFuzzyMatch && (
                                                        <span className="text-[9px] font-normal px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300" title="Coincidencia aproximada">Aprox.</span>
                                                    )}
                                                    {t.loggedFood && (
                                                        <span className="font-mono text-[10px] opacity-80">{t.loggedFood.calories}kcal</span>
                                                    )}
                                                </button>
                                                {expandedTagIdx === idx && (
                                                    <div className="absolute top-full left-0 mt-1 z-20 w-64 p-3 rounded-xl bg-[#111] border border-white/10 shadow-xl space-y-2">
                                                        <div>
                                                            <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Gramos</label>
                                                            <input type="number" value={t.amountGrams ?? ''} onChange={e => setAmountGramsForTag(idx, e.target.value ? parseFloat(e.target.value) : undefined)} placeholder="Ej: 200" min={1} className="w-full bg-white/5 border border-white/10 rounded-lg px-2 py-1.5 text-sm text-white" />
                                                        </div>
                                                        <div>
                                                            <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Referencia (auto-rellena gramos)</label>
                                                            <div className="flex gap-1 flex-wrap">
                                                                {PORTION_REFERENCES.map(ref => (
                                                                    <button key={ref.key} onClick={() => setPortionReferenceForTag(idx, ref.key)} className="px-2 py-1 rounded text-[9px] font-bold bg-white/5 text-zinc-400 hover:text-white hover:bg-white/10" title={`≈${getGramsForReference(ref.key, t.foodItem ? getFoodTypeForPortion(t.foodItem) : 'mixed')}g`}>{ref.label}</button>
                                                                ))}
                                                            </div>
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
                                                        {t.foodItem?.micronutrients && t.foodItem.micronutrients.length > 0 && (
                                                            <div>
                                                                <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Micronutrientes</label>
                                                                <p className="text-[10px] text-zinc-400 font-mono">
                                                                    {t.foodItem.micronutrients.map(m => `${m.name} ${m.amount}${m.unit}`).join(', ')}
                                                                </p>
                                                            </div>
                                                        )}
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
                                    <div className="mt-2 flex items-center gap-4">
                                        <span className="text-lg font-black text-white font-mono">{Math.round(totalMacros.calories)} kcal</span>
                                        <span className="text-xs text-zinc-500">P {totalMacros.protein.toFixed(0)} · C {totalMacros.carbs.toFixed(0)} · G {totalMacros.fats.toFixed(0)}</span>
                                    </div>
                                    {!showSaveTemplate && (
                                        <button
                                            onClick={() => setShowSaveTemplate(true)}
                                            className="mt-2 flex items-center gap-2 text-[10px] font-bold text-cyan-400 hover:text-cyan-300"
                                        >
                                            <UtensilsIcon size={14} />
                                            Guardar como plantilla
                                        </button>
                                    )}
                                    {showSaveTemplate && (
                                        <div className="mt-2 p-3 rounded-xl bg-white/5 border border-white/10 space-y-2">
                                            <input
                                                type="text"
                                                value={templateName}
                                                onChange={e => setTemplateName(e.target.value)}
                                                placeholder="Nombre de la plantilla"
                                                className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder-zinc-600"
                                                autoFocus
                                            />
                                            <div className="flex gap-2">
                                                <button onClick={handleSaveAsTemplate} className="flex-1 py-2 rounded-lg bg-cyan-500/20 text-cyan-400 text-xs font-bold">Guardar</button>
                                                <button onClick={() => { setShowSaveTemplate(false); setTemplateName(''); }} className="py-2 px-3 rounded-lg bg-white/5 text-zinc-500 text-xs font-bold">Cancelar</button>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    ) : activeTab === 'hybrid' ? (
                        <div className="animate-fade-in">
                            <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                                Escribe o busca
                            </label>
                            <div className="relative">
                                <input
                                    type="text"
                                    value={description}
                                    onChange={e => setDescription(e.target.value)}
                                    onBlur={handleDescriptionBlur}
                                    onKeyDown={e => {
                                        if (e.key === 'Enter') {
                                            if (hasStructuredContent(description)) {
                                                parseAndSetTags();
                                            } else if (hybridSuggestions.length > 0) {
                                                addTagFromFoodItem(hybridSuggestions[0], undefined, hybridMatchType === 'fuzzy');
                                                setDescription('');
                                            }
                                        }
                                    }}
                                    placeholder="200g arroz cocido, 150g pechuga... o escribe 'pechuga' para buscar"
                                    className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white placeholder-zinc-600 focus:border-white/30 outline-none"
                                />
                                {activeTab === 'hybrid' && description.trim() && !hasStructuredContent(description) && (
                                    <div className="absolute top-full left-0 right-0 mt-1 z-20 max-h-48 overflow-y-auto rounded-xl bg-[#111] border border-white/10 shadow-xl custom-scrollbar">
                                        {hybridSuggestionsLoading ? (
                                            <p className="px-4 py-3 text-xs text-zinc-500">Buscando...</p>
                                        ) : hybridSuggestions.length > 0 ? (
                                            hybridSuggestions.map(food => (
                                                <button
                                                    key={food.id}
                                                    onClick={() => {
                                                        addTagFromFoodItem(food, undefined, hybridMatchType === 'fuzzy');
                                                        setDescription('');
                                                    }}
                                                    className="w-full text-left px-4 py-2.5 hover:bg-white/5 flex items-center gap-3 border-b border-white/5 last:border-0"
                                                >
                                                    <div className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center shrink-0">
                                                        {food.image ? (
                                                            <img src={food.image} alt="" className="w-8 h-8 rounded-lg object-cover" />
                                                        ) : (
                                                            <UtensilsIcon size={14} className="text-zinc-500" />
                                                        )}
                                                    </div>
                                                    <div className="min-w-0 flex-1">
                                                        <p className="text-sm font-medium text-white truncate">{food.name}</p>
                                                        <p className="text-[10px] text-zinc-500 font-mono">
                                                            {Math.round(food.calories)} kcal · P:{food.protein} C:{food.carbs} F:{food.fats}
                                                        </p>
                                                    </div>
                                                </button>
                                            ))
                                        ) : (
                                            <p className="px-4 py-3 text-xs text-zinc-500">No hay sugerencias</p>
                                        )}
                                    </div>
                                )}
                            </div>
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
                                                title={t.isFuzzyMatch ? `No encontramos exactamente "${t.tag}"; usamos el alimento más parecido.` : undefined}
                                            >
                                                {t.quantity > 1 && <span>{t.quantity}x</span>}
                                                {t.amountGrams != null && <span>{t.amountGrams}g</span>}
                                                <span>{t.tag}</span>
                                                {t.isFuzzyMatch && (
                                                    <span className="text-[9px] font-normal px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300" title="Coincidencia aproximada">Aprox.</span>
                                                )}
                                                {t.loggedFood && (
                                                    <span className="font-mono text-[10px] opacity-80">{t.loggedFood.calories}kcal</span>
                                                )}
                                            </button>
                                            {expandedTagIdx === idx && (
                                                <div className="absolute top-full left-0 mt-1 z-20 w-64 p-3 rounded-xl bg-[#111] border border-white/10 shadow-xl space-y-2">
                                                    <div>
                                                        <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Gramos</label>
                                                        <input type="number" value={t.amountGrams ?? ''} onChange={e => setAmountGramsForTag(idx, e.target.value ? parseFloat(e.target.value) : undefined)} placeholder="Ej: 200" min={1} className="w-full bg-white/5 border border-white/10 rounded-lg px-2 py-1.5 text-sm text-white" />
                                                    </div>
                                                    <div>
                                                        <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Referencia (auto-rellena gramos)</label>
                                                        <div className="flex gap-1 flex-wrap">
                                                            {PORTION_REFERENCES.map(ref => (
                                                                <button key={ref.key} onClick={() => setPortionReferenceForTag(idx, ref.key)} className="px-2 py-1 rounded text-[9px] font-bold bg-white/5 text-zinc-400 hover:text-white hover:bg-white/10" title={`≈${getGramsForReference(ref.key, t.foodItem ? getFoodTypeForPortion(t.foodItem) : 'mixed')}g`}>{ref.label}</button>
                                                            ))}
                                                        </div>
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
                                                    {t.foodItem?.micronutrients && t.foodItem.micronutrients.length > 0 && (
                                                        <div>
                                                            <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Micronutrientes</label>
                                                            <p className="text-[10px] text-zinc-400 font-mono">
                                                                {t.foodItem.micronutrients.map(m => `${m.name} ${m.amount}${m.unit}`).join(', ')}
                                                            </p>
                                                        </div>
                                                    )}
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
                                            title={t.isFuzzyMatch ? `No encontramos exactamente "${t.tag}"; usamos el alimento más parecido.` : undefined}
                                        >
                                            {t.quantity > 1 && <span>{t.quantity}x</span>}
                                            {t.amountGrams != null && <span>{t.amountGrams}g</span>}
                                            <span>{t.tag}</span>
                                            {t.isFuzzyMatch && (
                                                <span className="text-[9px] font-normal px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300" title="Coincidencia aproximada">Aprox.</span>
                                            )}
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
                                                {t.foodItem?.micronutrients && t.foodItem.micronutrients.length > 0 && (
                                                    <div>
                                                        <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Micronutrientes</label>
                                                        <p className="text-[10px] text-zinc-400 font-mono">
                                                            {t.foodItem.micronutrients.map(m => `${m.name} ${m.amount}${m.unit}`).join(', ')}
                                                        </p>
                                                    </div>
                                                )}
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
