// components/nutrition/RegisterFoodDrawer.tsx
// Drawer lateral para registrar comida — Rediseño Premium
import React, { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import type { NutritionLog, LoggedFood, FoodItem, Settings, PortionInput, CookingMethod, PortionPreset, PortionReference, ParsedMealItem } from '../../types';
import { useMealTemplateStore } from '../../stores/mealTemplateStore';
import { PORTION_MULTIPLIERS } from '../../types';
import { PORTION_REFERENCES, getGramsForReference, getFoodTypeForPortion } from '../../data/portionReferences';
import { parseMealDescription } from '../../utils/nutritionDescriptionParser';
import { getCookingFactor, getEffectiveAmountForMacros } from '../../data/cookingMethodFactors';
import { searchFoods } from '../../services/foodSearchService';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { XIcon, TrashIcon, InfoIcon, UtensilsIcon, SearchIcon, ChevronDownIcon, FlameIcon, ZapIcon, DropletsIcon, TargetIcon, PlusIcon } from '../icons';
import { MealTemplateSelector } from './MealTemplateSelector';
import { PortionSelector } from '../PortionSelector';
import { getLocalDateString, dateStringToISOString } from '../../utils/dateUtils';
import { motion, AnimatePresence } from 'framer-motion';

// ─── Helpers ────────────────────────────────────────────────────────────────

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
    };
    if (portionInput) logged.portionInput = portionInput;
    return logged;
}

const mealOptions: { id: NutritionLog['mealType']; label: string; icon: any }[] = [
    { id: 'breakfast', label: 'Desayuno', icon: ZapIcon },
    { id: 'lunch', label: 'Almuerzo', icon: UtensilsIcon },
    { id: 'dinner', label: 'Cena', icon: FlameIcon },
    { id: 'snack', label: 'Snack', icon: DropletsIcon },
];

const DEBOUNCE_MS = 250;

interface TagWithFood {
    tag: string;
    portion: PortionPreset;
    quantity: number;
    amountGrams?: number;
    cookingMethod?: CookingMethod;
    brandHint?: string;
    macroOverrides?: { calories?: number; protein?: number; carbs?: number; fats?: number };
    anatomicalModifiers?: ('sin_miga' | 'sin_yema' | 'solo_claras' | 'sin_piel')[];
    heuristicModifiers?: ('descremado' | 'light' | 'integral')[];
    dimensionalMultiplier?: number;
    subItems?: ParsedMealItem[];
    isGroup?: boolean;
    foodItem: FoodItem | null;
    loggedFood: LoggedFood | null;
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
    const [expandedTagIdx, setExpandedTagIdx] = useState<number | null>(null);
    const [activeTab, setActiveTab] = useState<'description' | 'search' | 'templates'>('description');
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState<FoodItem[]>([]);
    const [searchLoading, setSearchLoading] = useState(false);
    const [selectedFoodForPortion, setSelectedFoodForPortion] = useState<FoodItem | null>(null);
    const [saveState, setSaveState] = useState<'idle' | 'success'>('idle');
    const skipParseRef = useRef(false);

    const parsed = useMemo(() => parseMealDescription(description), [description]);

    const buildLoggedFromItem = useCallback((item: FoodItem, tag: TagWithFood): LoggedFood => {
        const amount = tag.amountGrams != null ? tag.amountGrams * tag.quantity : item.servingSize * PORTION_MULTIPLIERS[tag.portion] * tag.quantity;
        const effectiveAmount = getEffectiveAmountForMacros(amount, item, tag.cookingMethod);
        const factor = tag.cookingMethod ? getCookingFactor(tag.cookingMethod) : { caloriesFactor: 1, fatsFactor: 1 };

        let calories = Math.round((item.calories / item.servingSize) * effectiveAmount * factor.caloriesFactor);
        let protein = Math.round((item.protein / item.servingSize) * effectiveAmount * 10) / 10;
        let carbs = Math.round((item.carbs / item.servingSize) * effectiveAmount * 10) / 10;
        let fats = Math.round((item.fats / item.servingSize) * effectiveAmount * factor.fatsFactor * 10) / 10;
        let finalAmount = Math.round(amount * 10) / 10;

        // --- ALCHEMY ENGINE: ANATOMICAL MODIFIERS ---
        if (tag.anatomicalModifiers?.includes('sin_miga')) {
            carbs *= 0.6;
            finalAmount *= 0.6;
            calories = (protein * 4) + (carbs * 4) + (fats * 9);
        }
        if (tag.anatomicalModifiers?.includes('sin_yema') || tag.anatomicalModifiers?.includes('solo_claras')) {
            fats *= 0.05;
            calories = (protein * 4) + (fats * 9) + (carbs * 4);
        }
        if (tag.anatomicalModifiers?.includes('sin_piel')) {
            fats *= 0.2;
            calories = (protein * 4) + (fats * 9) + (carbs * 4);
        }

        // --- ALCHEMY ENGINE: HEURISTIC MODIFIERS ---
        if (tag.heuristicModifiers?.includes('descremado')) {
            fats = 0;
            calories = (protein * 4) + (carbs * 4);
        }
        if (tag.heuristicModifiers?.includes('light')) {
            calories *= 0.7;
            fats *= 0.7;
        }

        return {
            id: crypto.randomUUID(),
            foodName: item.name,
            amount: finalAmount,
            unit: item.unit,
            calories: Math.round(tag.macroOverrides?.calories ?? calories),
            protein: Math.round((tag.macroOverrides?.protein ?? protein) * 10) / 10,
            carbs: Math.round((tag.macroOverrides?.carbs ?? carbs) * 10) / 10,
            fats: Math.round((tag.macroOverrides?.fats ?? fats) * 10) / 10,
            portionPreset: tag.portion,
            quantity: tag.quantity,
            cookingMethod: tag.cookingMethod,
        };
    }, []);

    const parseAndSetTags = useCallback(() => {
        if (skipParseRef.current || !description.trim()) return;
        const { items } = parsed;
        const newItems: TagWithFood[] = items.length === 0 ? [{ tag: description.trim(), portion: 'medium', quantity: 1, foodItem: null, loggedFood: null }] :
            items.map(i => ({
                tag: i.tag,
                portion: (typeof i.portion === 'string' ? i.portion : 'medium') as PortionPreset,
                quantity: i.quantity,
                amountGrams: i.amountGrams,
                cookingMethod: i.cookingMethod,
                brandHint: i.brandHint,
                macroOverrides: i.macroOverrides,
                anatomicalModifiers: i.anatomicalModifiers,
                heuristicModifiers: i.heuristicModifiers,
                isGroup: i.isGroup,
                subItems: i.subItems,
                foodItem: null,
                loggedFood: null,
                isFuzzyMatch: i.isFuzzyMatch
            }));

        setTagItems(newItems);
        newItems.forEach((tag, idx) => {
            if (tag.isGroup && tag.subItems) {
                // Additive Grouping: Recursive result resolution
                const subPromises = tag.subItems.map(si => {
                    const siTag: TagWithFood = {
                        tag: si.tag,
                        portion: (typeof si.portion === 'string' ? si.portion : 'medium') as PortionPreset,
                        quantity: si.quantity,
                        amountGrams: si.amountGrams,
                        cookingMethod: si.cookingMethod,
                        brandHint: si.brandHint,
                        macroOverrides: si.macroOverrides,
                        anatomicalModifiers: si.anatomicalModifiers,
                        heuristicModifiers: si.heuristicModifiers,
                        foodItem: null,
                        loggedFood: null
                    };
                    return searchFoods(si.tag, settings, si.brandHint).then(({ results }) => {
                        const food = results[0];
                        return food ? buildLoggedFromItem(food, siTag) : null;
                    });
                });

                Promise.all(subPromises).then(subLoggeds => {
                    const valid = subLoggeds.filter((l): l is LoggedFood => l !== null);
                    if (valid.length > 0) {
                        const sumMacros = valid.reduce((acc, l) => {
                            acc.calories += l.calories;
                            acc.protein += l.protein;
                            acc.carbs += l.carbs;
                            acc.fats += l.fats;
                            acc.amount += l.amount;
                            return acc;
                        }, { calories: 0, protein: 0, carbs: 0, fats: 0, amount: 0 });

                        setTagItems(prev => {
                            const next = [...prev];
                            if (next[idx]) {
                                next[idx].loggedFood = {
                                    id: crypto.randomUUID(),
                                    foodName: tag.tag,
                                    amount: Math.round(sumMacros.amount * 10) / 10,
                                    unit: 'g',
                                    calories: Math.round(sumMacros.calories),
                                    protein: Math.round(sumMacros.protein * 10) / 10,
                                    carbs: Math.round(sumMacros.carbs * 10) / 10,
                                    fats: Math.round(sumMacros.fats * 10) / 10,
                                    portionPreset: 'medium',
                                    quantity: 1,
                                };
                            }
                            return next;
                        });
                    }
                });
                return;
            }

            if (tag.tag.length >= 2) {
                // @ts-ignore - Adding 3rd param later
                searchFoods(tag.tag, settings, tag.brandHint).then(({ results, matchType }) => {
                    let foodItem = results[0];
                    if (!foodItem && tag.macroOverrides) {
                        foodItem = {
                            id: crypto.randomUUID(),
                            name: tag.tag,
                            servingSize: 100,
                            unit: 'g',
                            calories: tag.macroOverrides.calories || 0,
                            protein: tag.macroOverrides.protein || 0,
                            carbs: tag.macroOverrides.carbs || 0,
                            fats: tag.macroOverrides.fats || 0,
                            isCustom: true
                        };
                    }
                    if (foodItem) {
                        setTagItems(prev => {
                            const next = [...prev];
                            if (next[idx]?.tag === tag.tag) {
                                next[idx] = { ...next[idx], foodItem, loggedFood: buildLoggedFromItem(foodItem, next[idx]), isFuzzyMatch: tag.isFuzzyMatch || matchType === 'fuzzy' };
                            }
                            return next;
                        });
                    }
                });
            }
        });
    }, [description, parsed, settings, buildLoggedFromItem]);

    useEffect(() => {
        if (activeTab === 'templates') return;
        const t = setTimeout(() => parseAndSetTags(), DEBOUNCE_MS);
        return () => clearTimeout(t);
    }, [description, parseAndSetTags, activeTab]);

    const handleSave = () => {
        const foods = tagItems.filter(t => t.loggedFood).map(t => t.loggedFood!);
        if (foods.length === 0) return addToast('Añade comida para registrar.', 'danger');

        onSave({ id: crypto.randomUUID(), date: dateStringToISOString(logDate), mealType, foods, description: description.trim() || undefined, status: 'consumed' });

        setSaveState('success');

        setTimeout(() => {
            onClose();
            setTimeout(() => {
                setDescription(''); setTagItems([]); setSaveState('idle');
            }, 400);
        }, 2000);
    };

    const totalMacros = useMemo(() => tagItems.reduce((acc, t) => {
        if (t.loggedFood) {
            acc.calories += t.loggedFood.calories;
            acc.protein += t.loggedFood.protein;
            acc.carbs += t.loggedFood.carbs;
            acc.fats += t.loggedFood.fats;
        }
        return acc;
    }, { calories: 0, protein: 0, carbs: 0, fats: 0 }), [tagItems]);

    if (!isOpen) return null;

    return (
        <AnimatePresence>
            {isOpen && (
                <>
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-[2000] bg-black/40 backdrop-blur-sm" onClick={onClose} />
                    <motion.div
                        initial={{ y: "100%" }}
                        animate={{ y: 0 }}
                        exit={{ y: "100%" }}
                        transition={{ type: "spring", damping: 25, stiffness: 200 }}
                        className="fixed inset-x-0 bottom-0 z-[2001] bg-[#F7F7F7] rounded-t-[40px] shadow-2xl overflow-hidden flex flex-col h-[90vh]"
                    >
                        {/* ─── HEADER ─── */}
                        <div className="px-6 pt-8 pb-4 flex justify-between items-center">
                            <div>
                                <h3 className="text-[10px] font-black text-slate-500 uppercase tracking-[0.2em] mb-1">Registro Rápido</h3>
                                <h2 className="text-2xl font-black text-slate-900 tracking-tight">Registra tus comidas</h2>
                            </div>
                            <button onClick={onClose} className="p-2 bg-slate-200/50 rounded-full hover:bg-slate-300 transition-colors">
                                <XIcon size={20} className="text-slate-600" />
                            </button>
                        </div>

                        {/* ─── MAIN CONTENT ─── */}
                        <div className="flex-1 overflow-y-auto px-6 space-y-6 pb-48 custom-scrollbar">

                            {/* Summary Card */}
                            <motion.div layout className="bg-white rounded-[32px] p-6 shadow-xl shadow-slate-200/50 border border-slate-100 flex items-center justify-between">
                                <div className="flex flex-col">
                                    <span className="text-4xl font-black text-slate-900 font-mono tracking-tighter">{Math.round(totalMacros.calories)}</span>
                                    <span className="text-[10px] uppercase font-black text-slate-400 tracking-widest -mt-1">Calorías Totales</span>
                                </div>
                                <div className="flex gap-4">
                                    <MacroBadge label="PROT" val={totalMacros.protein} color="bg-rose-500" />
                                    <MacroBadge label="CARB" val={totalMacros.carbs} color="bg-emerald-500" />
                                    <MacroBadge label="FATS" val={totalMacros.fats} color="bg-amber-500" />
                                </div>
                            </motion.div>

                            {/* Meal Type & Date Selector */}
                            <div className="flex gap-2">
                                <div className="flex-1 bg-white rounded-2xl p-2 flex gap-1 shadow-sm border border-slate-100 items-center">
                                    <SearchIcon size={16} className="text-slate-400 ml-2" />
                                    <input
                                        type="date"
                                        value={logDate}
                                        onChange={e => setLogDate(e.target.value)}
                                        className="w-full bg-transparent text-xs font-black text-slate-700 outline-none uppercase"
                                    />
                                </div>
                                <div className="flex-[1.5] bg-white rounded-2xl p-1 flex shadow-sm border border-slate-100">
                                    {mealOptions.map(opt => (
                                        <button
                                            key={opt.id}
                                            onClick={() => setMealType(opt.id)}
                                            className={`flex-1 flex flex-col items-center justify-center p-2 rounded-xl transition-all ${mealType === opt.id ? 'bg-amber-500 text-white shadow-md' : 'text-slate-400'}`}
                                        >
                                            <opt.icon size={16} />
                                            <span className="text-[8px] font-black uppercase mt-1">{opt.label}</span>
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {/* Input Tabs */}
                            <div className="flex bg-slate-200/50 p-1 rounded-2xl overflow-x-auto hide-scrollbar">
                                <TabBtn active={activeTab === 'description'} onClick={() => setActiveTab('description')}>Búsqueda descriptiva</TabBtn>
                                <TabBtn active={activeTab === 'search'} onClick={() => setActiveTab('search')}>Buscador</TabBtn>
                                <TabBtn active={activeTab === 'templates'} onClick={() => setActiveTab('templates')}>Plantillas</TabBtn>
                            </div>

                            <AnimatePresence mode="wait">
                                {activeTab === 'description' && (
                                    <motion.div key="desc" initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 10 }}>
                                        <div className="relative group">
                                            <textarea
                                                className="w-full h-32 bg-white rounded-[32px] p-6 text-sm font-medium text-slate-800 border-2 border-transparent focus:border-amber-400 shadow-lg outline-none transition-all placeholder-slate-300 resize-none"
                                                placeholder="Describe tu comida... ej: 200g arroz cocido, 150g pechuga a la plancha"
                                                value={description}
                                                onChange={e => setDescription(e.target.value)}
                                            />
                                            <div className="absolute top-4 right-6 flex gap-2">
                                                <div className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
                                            </div>
                                        </div>
                                    </motion.div>
                                )}
                                {activeTab === 'search' && (
                                    <motion.div key="search" initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }}>
                                        <div className="relative mb-4">
                                            <SearchIcon size={18} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
                                            <input
                                                className="w-full bg-white rounded-2xl pl-12 pr-4 py-4 text-sm font-medium border border-slate-100 shadow-sm outline-none focus:ring-2 ring-amber-400 transition-all"
                                                placeholder="Buscar en la base de datos de KPKN..."
                                                value={searchQuery}
                                                onChange={e => { setSearchQuery(e.target.value); if (e.target.value.length > 2) searchFoods(e.target.value, settings).then(r => setSearchResults(r.results)); }}
                                            />
                                        </div>
                                        <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                                            {searchResults.map(food => (
                                                <button key={food.id} onClick={() => { setSelectedFoodForPortion(food); }} className="w-full bg-white p-3 rounded-2xl border border-slate-100 flex items-center gap-3 hover:translate-x-1 transition-transform">
                                                    <div className="w-10 h-10 bg-slate-50 rounded-xl flex items-center justify-center shrink-0">
                                                        <UtensilsIcon size={18} className="text-amber-500" />
                                                    </div>
                                                    <div className="text-left">
                                                        <p className="text-sm font-black text-slate-900">{food.name}</p>
                                                        <p className="text-[10px] font-bold text-slate-400 uppercase">{food.calories} kcal · p:{food.protein} c:{food.carbs} f:{food.fats}</p>
                                                    </div>
                                                </button>
                                            ))}
                                        </div>
                                    </motion.div>
                                )}
                                {activeTab === 'templates' && (
                                    <motion.div key="templates" initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }}>
                                        <MealTemplateSelector onSelect={foods => {
                                            setTagItems(foods.map(f => ({ tag: f.foodName, portion: 'medium', quantity: 1, foodItem: null, loggedFood: f })));
                                            setActiveTab('description');
                                        }} onClose={() => { }} />
                                    </motion.div>
                                )}
                            </AnimatePresence>

                            {/* Added Foods List */}
                            {tagItems.length > 0 && (
                                <div className="space-y-3">
                                    <h4 className="text-[10px] font-black text-slate-400 uppercase tracking-widest pl-1">Lista de Alimentos</h4>
                                    <div className="space-y-2">
                                        {tagItems.map((tag, idx) => (
                                            <div key={idx} className="bg-white rounded-2xl p-4 shadow-sm border border-slate-100 flex items-center gap-4">
                                                <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${tag.loggedFood ? 'bg-amber-100' : 'bg-slate-100'}`}>
                                                    <UtensilsIcon size={14} className={tag.loggedFood ? 'text-amber-600' : 'text-slate-400'} />
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                    <p className="text-sm font-black text-slate-900 truncate leading-tight">{tag.tag}</p>
                                                    <p className="text-[10px] font-bold text-slate-400 tracking-tight">
                                                        {tag.loggedFood ? `${Math.round(tag.loggedFood.calories)} kcal · ${tag.loggedFood.amount}${tag.loggedFood.unit}` : 'Buscando coincidencia...'}
                                                    </p>
                                                </div>
                                                <button
                                                    onClick={() => setTagItems(prev => prev.filter((_, i) => i !== idx))}
                                                    className="p-2 text-slate-300 hover:text-rose-500 transition-colors"
                                                >
                                                    <TrashIcon size={16} />
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* ─── FOOTER ─── */}
                        <div className="absolute bottom-0 inset-x-0 px-6 pt-6 pb-28 bg-gradient-to-t from-[#F7F7F7] via-[#F7F7F7] 80% to-transparent pointer-events-none">
                            <div className="pointer-events-auto flex gap-3 shadow-2xl rounded-2xl">
                                <button
                                    onClick={onClose}
                                    className="flex-1 py-4 bg-white border border-slate-200 rounded-2xl text-slate-500 text-xs font-black uppercase tracking-widest active:scale-95 transition-all"
                                >
                                    Cancelar
                                </button>
                                <button
                                    onClick={handleSave}
                                    className="flex-[2] py-4 bg-slate-900 text-white rounded-2xl text-xs font-black uppercase tracking-[0.2em] shadow-xl shadow-slate-900/20 active:scale-95 transition-all flex items-center justify-center gap-2"
                                >
                                    Confirmar Registro
                                    <PlusIcon size={16} />
                                </button>
                            </div>
                        </div>

                        {/* Success Animation Overlay */}
                        <AnimatePresence>
                            {saveState === 'success' && (
                                <motion.div
                                    initial={{ opacity: 0, scale: 0.95 }}
                                    animate={{ opacity: 1, scale: 1 }}
                                    exit={{ opacity: 0, y: 20 }}
                                    transition={{ type: "spring", stiffness: 300, damping: 25 }}
                                    className="absolute inset-0 z-[2500] bg-[#F7F7F7]/95 backdrop-blur-md px-6 py-12 flex flex-col items-center justify-center pointer-events-auto"
                                >
                                    <div className="w-20 h-20 bg-emerald-500 rounded-[32px] flex items-center justify-center shadow-lg shadow-emerald-500/30 mb-8 animate-bounce">
                                        <svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" className="text-white">
                                            <polyline points="20 6 9 17 4 12"></polyline>
                                        </svg>
                                    </div>
                                    <h2 className="text-3xl font-black text-slate-900 mb-2">¡Listo!</h2>
                                    <p className="text-sm font-bold text-slate-500 mb-8 text-center bg-slate-200/50 px-4 py-2 rounded-full">
                                        Has sumado <span className="text-slate-900 font-black">{Math.round(totalMacros.calories)} kcal</span> a tu plan.
                                    </p>

                                    <div className="w-full bg-white p-6 rounded-[32px] border border-slate-100 shadow-xl space-y-4">
                                        {tagItems.filter(t => t.loggedFood).map((t, i) => (
                                            <div key={i} className="flex justify-between items-center bg-slate-50 p-3 rounded-2xl">
                                                <span className="text-sm font-black text-slate-900 truncate pr-4">{t.loggedFood?.foodName}</span>
                                                <span className="text-xs font-bold text-slate-400 shrink-0">{t.loggedFood?.amount}{t.loggedFood?.unit}</span>
                                            </div>
                                        ))}
                                    </div>
                                </motion.div>
                            )}
                        </AnimatePresence>

                        {/* Search Modal Bridge */}
                        {selectedFoodForPortion && (
                            <div className="fixed inset-0 z-[3000] bg-white rounded-t-[40px] shadow-2xl overflow-hidden p-6 animate-slide-up">
                                <PortionSelector
                                    food={selectedFoodForPortion}
                                    onConfirm={(portion, grams) => {
                                        const logged = foodToLoggedFood(selectedFoodForPortion, grams, portion);
                                        setTagItems(prev => [...prev, { tag: selectedFoodForPortion.name, portion: 'medium', quantity: 1, foodItem: selectedFoodForPortion, loggedFood: logged }]);
                                        setSelectedFoodForPortion(null);
                                        setSearchQuery('');
                                    }}
                                    onCancel={() => setSelectedFoodForPortion(null)}
                                />
                            </div>
                        )}
                    </motion.div>
                </>
            )}
        </AnimatePresence>
    );
};

const TabBtn: React.FC<{ active: boolean; onClick: () => void; children: React.ReactNode }> = ({ active, onClick, children }) => (
    <button
        onClick={onClick}
        className={`flex-1 py-2 text-[10px] font-black uppercase transition-all rounded-xl ${active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-400'}`}
    >
        {children}
    </button>
);

const MacroBadge: React.FC<{ label: string; val: number; color: string }> = ({ label, val, color }) => (
    <div className="flex flex-col items-center">
        <div className={`w-8 h-1 rounded-full ${color} mb-1 opacity-60`} />
        <span className="text-[14px] font-black text-slate-900 font-mono leading-none">{Math.round(val)}</span>
        <span className="text-[8px] font-black text-slate-400 uppercase tracking-tighter">{label}</span>
    </div>
);
