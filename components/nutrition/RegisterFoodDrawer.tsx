// components/nutrition/RegisterFoodDrawer.tsx
// Drawer lateral para registrar comida con resolucion guiada.

import React, { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import type { NutritionLog, LoggedFood, FoodItem, Settings, PortionInput, CookingMethod, PortionPreset, ParsedMealItem, ParsedMealDescription } from '../../types';
import { parseMealDescription } from '../../utils/nutritionDescriptionParser';
import { AlchemyEngine } from '../../services/alchemyEngine';
import { parseFreeFormNutrition } from '../../services/aiNutritionParser';
import {
    searchFoods,
    rememberFoodResolution,
    type SearchConfidence,
    type SearchFoodCandidate,
} from '../../services/foodSearchService';
import { useAppDispatch } from '../../contexts/AppContext';
import { XIcon, TrashIcon, UtensilsIcon, SearchIcon, ChevronDownIcon, PlusIcon } from '../icons';
import { MealTemplateSelector } from './MealTemplateSelector';
import { PortionSelector } from '../PortionSelector';
import { getLocalDateString, dateStringToISOString } from '../../utils/dateUtils';
import { motion, AnimatePresence } from 'framer-motion';

function foodToLoggedFood(food: FoodItem, amountGrams: number, portionInput?: PortionInput): LoggedFood {
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

type MacroKey = 'protein' | 'carbs' | 'fats';

const mealOptions: { id: NutritionLog['mealType']; label: string; }[] = [
    { id: 'breakfast', label: 'Desayuno' },
    { id: 'lunch', label: 'Almuerzo' },
    { id: 'dinner', label: 'Cena' },
    { id: 'snack', label: 'Snack' },
];

const CALORIES_VISUAL_TARGET = 700;
const MACRO_GOALS: { key: MacroKey; label: string; color: string; target: number }[] = [
    { key: 'protein', label: 'PROT', color: 'bg-rose-500', target: 60 },
    { key: 'carbs', label: 'CARB', color: 'bg-emerald-500', target: 180 },
    { key: 'fats', label: 'FATS', color: 'bg-amber-500', target: 70 },
];

const DEBOUNCE_MS = 250;
const SEARCH_DEBOUNCE_MS = 350;

type TagOrigin = 'description' | 'manual' | 'template';
type TagResolutionStatus = 'pending' | 'resolved' | 'needs_review' | 'unresolved';

export interface TagWithFood {
    key: string;
    origin: TagOrigin;
    tag: string;
    portion: PortionPreset;
    quantity: number;
    amountGrams?: number;
    cookingMethod?: CookingMethod;
    brandHint?: string;
    macroOverrides?: { calories?: number; protein?: number; carbs?: number; fats?: number };
    anatomicalModifiers?: ('sin_miga' | 'sin_yema' | 'solo_claras' | 'sin_piel')[];
    heuristicModifiers?: ('descremado' | 'light' | 'integral')[];
    preparationModifiers?: ('pelado' | 'picado' | 'deshuesado' | 'con_hueso' | 'rayado')[];
    stateModifiers?: ('en_almibar' | 'al_agua' | 'en_polvo' | 'concentrado' | 'deshidratado')[];
    compositionModifiers?: ('extra_tierno' | 'con_grasa' | 'sin_grasa' | 'bajo_sodio')[];
    dimensionalMultiplier?: number;
    subItems?: ParsedMealItem[];
    isGroup?: boolean;
    foodItem: FoodItem | null;
    loggedFood: LoggedFood | null;
    isFuzzyMatch?: boolean;
    resolutionStatus: TagResolutionStatus;
    resolutionConfidence: SearchConfidence;
    resolutionScore?: number;
    resolutionReason?: string;
    candidateFoods: SearchFoodCandidate[];
    sourceTrace?: string[];
}

interface RegisterFoodDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    onSave: (log: NutritionLog) => void;
    settings: Settings;
    initialDate?: string;
    mealType?: NutritionLog['mealType'];
    displayMode?: 'drawer' | 'appendix';
    showCloseButton?: boolean;
}

interface SelectedFoodForPortionState {
    food: FoodItem;
    query: string;
}

function createCustomFoodFromOverrides(tag: TagWithFood): FoodItem {
    return {
        id: crypto.randomUUID(),
        name: tag.tag,
        servingSize: 100,
        unit: 'g',
        calories: tag.macroOverrides?.calories || 0,
        protein: tag.macroOverrides?.protein || 0,
        carbs: tag.macroOverrides?.carbs || 0,
        fats: tag.macroOverrides?.fats || 0,
        isCustom: true,
    };
}

function isBlockingResolution(tag: TagWithFood): boolean {
    return tag.origin === 'description' && tag.resolutionStatus !== 'resolved';
}

function confidenceLabel(confidence: SearchConfidence): string {
    if (confidence === 'high') return 'Alta';
    if (confidence === 'medium') return 'Media';
    return 'Baja';
}

function currentLikeTag(item: ParsedMealItem): Partial<TagWithFood> {
    return {
        tag: item.tag,
        portion: (typeof item.portion === 'string' ? item.portion : 'medium') as PortionPreset,
        quantity: item.quantity,
        amountGrams: item.amountGrams,
        cookingMethod: item.cookingMethod,
        brandHint: item.brandHint,
        macroOverrides: item.macroOverrides,
        anatomicalModifiers: item.anatomicalModifiers,
        heuristicModifiers: item.heuristicModifiers,
        preparationModifiers: item.preparationModifiers,
        stateModifiers: item.stateModifiers,
        compositionModifiers: item.compositionModifiers,
        dimensionalMultiplier: item.dimensionalMultiplier,
        subItems: item.subItems,
        isGroup: item.isGroup,
    };
}

function makeDescriptionTag(item: ParsedMealItem | null, fallbackText?: string): TagWithFood {
    return {
        key: crypto.randomUUID(),
        origin: 'description',
        tag: item?.tag || fallbackText || '',
        portion: (typeof item?.portion === 'string' ? item.portion : 'medium') as PortionPreset,
        quantity: item?.quantity ?? 1,
        amountGrams: item?.amountGrams,
        cookingMethod: item?.cookingMethod,
        brandHint: item?.brandHint,
        macroOverrides: item?.macroOverrides,
        anatomicalModifiers: item?.anatomicalModifiers,
        heuristicModifiers: item?.heuristicModifiers,
        preparationModifiers: item?.preparationModifiers,
        stateModifiers: item?.stateModifiers,
        compositionModifiers: item?.compositionModifiers,
        dimensionalMultiplier: item?.dimensionalMultiplier,
        subItems: item?.subItems,
        isGroup: item?.isGroup,
        foodItem: null,
        loggedFood: null,
        isFuzzyMatch: item?.isFuzzyMatch,
        resolutionStatus: 'pending',
        resolutionConfidence: 'low',
        candidateFoods: [],
    };
}

function buildParsedSignature(parsed: ParsedMealDescription): string {
    return parsed.items
        .map(item => JSON.stringify({
            tag: item.tag,
            quantity: item.quantity,
            amountGrams: item.amountGrams ?? null,
            portion: item.portion ?? null,
            brandHint: item.brandHint ?? null,
            cookingMethod: item.cookingMethod ?? null,
            subItems: item.subItems?.map(subItem => subItem.tag) ?? [],
        }))
        .sort()
        .join('|');
}

export const RegisterFoodDrawer: React.FC<RegisterFoodDrawerProps> = ({
    isOpen,
    onClose,
    onSave,
    settings,
    initialDate,
    mealType: initialMealType = 'lunch',
    displayMode = 'drawer',
    showCloseButton = displayMode === 'drawer',
}) => {
    const { addToast } = useAppDispatch();
    const [description, setDescription] = useState('');
    const [mealType, setMealType] = useState<NutritionLog['mealType']>(initialMealType);
    const [logDate, setLogDate] = useState(initialDate || getLocalDateString());
    const [tagItems, setTagItems] = useState<TagWithFood[]>([]);
    const [expandedTagKey, setExpandedTagKey] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<'description' | 'search' | 'templates'>('description');
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState<FoodItem[]>([]);
    const [searchLoading, setSearchLoading] = useState(false);
    const [selectedFoodForPortion, setSelectedFoodForPortion] = useState<SelectedFoodForPortionState | null>(null);
    const [saveState, setSaveState] = useState<'idle' | 'success'>('idle');
    const [manualResolveTagKey, setManualResolveTagKey] = useState<string | null>(null);

    const resolutionRequestRef = useRef(0);
    const searchRequestRef = useRef(0);

    const isAppendixMode = displayMode === 'appendix';
    const sheetContainerClass = isAppendixMode
        ? 'relative mx-auto w-full max-w-[380px] rounded-[32px] border border-white/40 bg-white/95 shadow-2xl backdrop-blur flex flex-col overflow-hidden'
        : 'fixed inset-x-0 bottom-0 z-[2001] bg-[#F7F7F7] rounded-t-[40px] shadow-2xl h-[90vh] flex flex-col';
    const scrollAreaClass = `flex-1 overflow-y-auto space-y-5 ${isAppendixMode ? 'px-4 py-5 max-h-[66vh]' : 'px-6 pb-48 pt-4'} custom-scrollbar`;

    useEffect(() => {
        if (!isOpen) return;
        resolutionRequestRef.current += 1;
        searchRequestRef.current += 1;
        setDescription('');
        setMealType(initialMealType);
        setLogDate(initialDate || getLocalDateString());
        setTagItems([]);
        setExpandedTagKey(null);
        setActiveTab('description');
        setSearchQuery('');
        setSearchResults([]);
        setSearchLoading(false);
        setSelectedFoodForPortion(null);
        setSaveState('idle');
        setManualResolveTagKey(null);
    }, [isOpen, initialDate, initialMealType]);

    const buildLoggedFromItem = useCallback((item: FoodItem, tag: TagWithFood): LoggedFood => {
        return AlchemyEngine.calculateLoggedFood(item, tag);
    }, []);

    const updateTagItem = useCallback((tagKey: string, updater: (current: TagWithFood) => TagWithFood) => {
        setTagItems(prev => prev.map(tag => tag.key === tagKey ? updater(tag) : tag));
    }, []);

    const resolveDescriptionTag = useCallback(async (tag: TagWithFood, requestId: number) => {
        if (tag.isGroup && tag.subItems?.length) {
            const subResults = await Promise.all(tag.subItems.map(subItem => searchFoods(subItem.tag, settings, subItem.brandHint)));
            if (requestId !== resolutionRequestRef.current) return;

            const canResolveGroup = subResults.every(result => result.canAutoSelect && result.results[0]);
            if (!canResolveGroup) {
                updateTagItem(tag.key, current => ({
                    ...current,
                    resolutionStatus: 'needs_review',
                    resolutionConfidence: 'low',
                    resolutionReason: 'La receta compuesta requiere confirmar sus ingredientes.',
                    candidateFoods: [],
                }));
                setExpandedTagKey(prev => prev ?? tag.key);
                return;
            }

            const subLoggedFoods = tag.subItems.map((subItem, index) => {
                const food = subResults[index].results[0]!;
                const subTag: TagWithFood = {
                    ...(currentLikeTag(subItem) as TagWithFood),
                    key: crypto.randomUUID(),
                    origin: 'description',
                    foodItem: food,
                    loggedFood: null,
                    resolutionStatus: 'resolved',
                    resolutionConfidence: subResults[index].bestConfidence,
                    candidateFoods: subResults[index].candidates,
                };
                return buildLoggedFromItem(food, subTag);
            });

            const aggregate = subLoggedFoods.reduce((acc, logged) => {
                acc.amount += logged.amount;
                acc.calories += logged.calories;
                acc.protein += logged.protein;
                acc.carbs += logged.carbs;
                acc.fats += logged.fats;
                return acc;
            }, { amount: 0, calories: 0, protein: 0, carbs: 0, fats: 0 });

            updateTagItem(tag.key, current => ({
                ...current,
                loggedFood: {
                    id: crypto.randomUUID(),
                    foodName: current.tag,
                    amount: Math.round(aggregate.amount * 10) / 10,
                    unit: 'g',
                    calories: Math.round(aggregate.calories),
                    protein: Math.round(aggregate.protein * 10) / 10,
                    carbs: Math.round(aggregate.carbs * 10) / 10,
                    fats: Math.round(aggregate.fats * 10) / 10,
                    portionPreset: 'medium',
                    quantity: 1,
                },
                resolutionStatus: 'resolved',
                resolutionConfidence: 'high',
                resolutionReason: 'Receta compuesta resuelta por ingredientes.',
                candidateFoods: [],
            }));
            return;
        }

        const result = await searchFoods(tag.tag, settings, tag.brandHint);
        if (requestId !== resolutionRequestRef.current) return;

        if (!result.results[0] && tag.macroOverrides) {
            const customFood = createCustomFoodFromOverrides(tag);
            updateTagItem(tag.key, current => ({
                ...current,
                foodItem: customFood,
                loggedFood: buildLoggedFromItem(customFood, current),
                resolutionStatus: 'resolved',
                resolutionConfidence: 'high',
                resolutionScore: 1,
                resolutionReason: 'Macros manuales aplicados como alimento personalizado.',
                candidateFoods: [],
                sourceTrace: ['macro_override'],
            }));
            return;
        }

        if (!result.results[0]) {
            updateTagItem(tag.key, current => ({
                ...current,
                foodItem: null,
                loggedFood: null,
                resolutionStatus: 'unresolved',
                resolutionConfidence: result.bestConfidence,
                resolutionScore: result.bestScore,
                resolutionReason: result.decisionReason || 'No se encontraron coincidencias.',
                candidateFoods: result.candidates.slice(0, 4),
                sourceTrace: result.candidates[0]?.trace,
                isFuzzyMatch: true,
            }));
            setExpandedTagKey(prev => prev ?? tag.key);
            return;
        }

        if (result.canAutoSelect) {
            const bestCandidate = result.candidates[0];
            updateTagItem(tag.key, current => ({
                ...current,
                foodItem: bestCandidate.food,
                loggedFood: buildLoggedFromItem(bestCandidate.food, current),
                resolutionStatus: 'resolved',
                resolutionConfidence: result.bestConfidence,
                resolutionScore: bestCandidate.score,
                resolutionReason: result.decisionReason,
                candidateFoods: result.candidates.slice(0, 4),
                sourceTrace: bestCandidate.trace,
                isFuzzyMatch: result.matchType === 'fuzzy',
            }));
            return;
        }

        updateTagItem(tag.key, current => ({
            ...current,
            foodItem: null,
            loggedFood: null,
            resolutionStatus: result.candidates.length > 0 ? 'needs_review' : 'unresolved',
            resolutionConfidence: result.bestConfidence,
            resolutionScore: result.bestScore,
            resolutionReason: result.decisionReason,
            candidateFoods: result.candidates.slice(0, 4),
            sourceTrace: result.candidates[0]?.trace,
            isFuzzyMatch: true,
        }));
        setExpandedTagKey(prev => prev ?? tag.key);
    }, [buildLoggedFromItem, settings, updateTagItem]);

    const parseAndSetTags = useCallback(async () => {
        const requestId = ++resolutionRequestRef.current;
        const preserved = tagItems.filter(tag => tag.origin !== 'description');
        const trimmedDescription = description.trim();

        if (!trimmedDescription) {
            setTagItems(preserved);
            return;
        }

        const fastParsed = parseMealDescription(trimmedDescription);
        const fastSignature = buildParsedSignature(fastParsed);
        const descriptionTags = fastParsed.items.length > 0
            ? fastParsed.items.map(item => makeDescriptionTag(item))
            : [makeDescriptionTag(null, trimmedDescription)];

        setExpandedTagKey(null);
        setTagItems([...descriptionTags, ...preserved]);

        await Promise.all(descriptionTags.map(tag => resolveDescriptionTag(tag, requestId)));

        const refinedParsed = await parseFreeFormNutrition(trimmedDescription, settings);
        if (requestId !== resolutionRequestRef.current) return;

        const refinedSignature = buildParsedSignature(refinedParsed);
        if (refinedSignature === fastSignature) return;

        const refinedTags = refinedParsed.items.length > 0
            ? refinedParsed.items.map(item => makeDescriptionTag(item))
            : [makeDescriptionTag(null, trimmedDescription)];

        setExpandedTagKey(null);
        setTagItems([...refinedTags, ...preserved]);
        await Promise.all(refinedTags.map(tag => resolveDescriptionTag(tag, requestId)));
    }, [description, resolveDescriptionTag, settings, tagItems]);

    useEffect(() => {
        if (!isOpen || activeTab === 'templates') return;
        const timeout = setTimeout(() => {
            parseAndSetTags().catch(() => {
                addToast('No se pudo procesar la descripcion.', 'danger');
            });
        }, DEBOUNCE_MS);

        return () => clearTimeout(timeout);
    }, [activeTab, addToast, isOpen, parseAndSetTags]);

    useEffect(() => {
        if (!isOpen || activeTab !== 'search') return;

        const trimmed = searchQuery.trim();
        if (trimmed.length < 2) {
            setSearchResults([]);
            setSearchLoading(false);
            return;
        }

        const requestId = ++searchRequestRef.current;
        setSearchLoading(true);

        const timeout = setTimeout(() => {
            searchFoods(trimmed, settings)
                .then(result => {
                    if (requestId !== searchRequestRef.current) return;
                    setSearchResults(result.results);
                    setSearchLoading(false);
                })
                .catch(() => {
                    if (requestId !== searchRequestRef.current) return;
                    setSearchResults([]);
                    setSearchLoading(false);
                });
        }, SEARCH_DEBOUNCE_MS);

        return () => clearTimeout(timeout);
    }, [activeTab, isOpen, searchQuery, settings]);

    const handleCandidateSelect = useCallback((tagKey: string, candidate: SearchFoodCandidate, rememberQuery = true) => {
        setTagItems(prev => prev.map(tag => {
            if (tag.key !== tagKey) return tag;
            if (rememberQuery) rememberFoodResolution(tag.tag, candidate.food, tag.brandHint);

            return {
                ...tag,
                foodItem: candidate.food,
                loggedFood: buildLoggedFromItem(candidate.food, tag),
                resolutionStatus: 'resolved',
                resolutionConfidence: candidate.confidence,
                resolutionScore: candidate.score,
                resolutionReason: 'Confirmado manualmente.',
                sourceTrace: candidate.trace,
                isFuzzyMatch: candidate.confidence !== 'high',
            };
        }));
        setManualResolveTagKey(null);
        addToast('Etiqueta resuelta manualmente.', 'success');
    }, [addToast, buildLoggedFromItem]);

    const handleOpenManualSearch = useCallback((tag: TagWithFood) => {
        setActiveTab('search');
        setSearchQuery(tag.tag);
        setManualResolveTagKey(tag.key);
        setExpandedTagKey(tag.key);
        addToast('Selecciona el alimento correcto para resolver la etiqueta.', 'suggestion');
    }, [addToast]);

    const handleSearchFoodSelect = useCallback((food: FoodItem) => {
        if (manualResolveTagKey) {
            handleCandidateSelect(manualResolveTagKey, {
                food,
                score: 1,
                confidence: 'high',
                canonicalId: food.name,
                source: 'local',
                trace: ['manual_search_resolution'],
                queryCoverage: 1,
                tokenPrecision: 1,
                brandMatched: false,
                learned: false,
            });
            setSearchQuery('');
            setSearchResults([]);
            setActiveTab('description');
            return;
        }

        setSelectedFoodForPortion({
            food,
            query: searchQuery.trim(),
        });
    }, [handleCandidateSelect, manualResolveTagKey, searchQuery]);

    const handleSave = useCallback(() => {
        const blockingTag = tagItems.find(isBlockingResolution);
        if (blockingTag) {
            setExpandedTagKey(blockingTag.key);
            addToast('Revisa los alimentos ambiguos antes de guardar.', 'danger');
            return;
        }

        const foods = tagItems.filter(tag => tag.loggedFood).map(tag => tag.loggedFood!);
        if (foods.length === 0) {
            addToast('Anade comida para registrar.', 'danger');
            return;
        }

        onSave({
            id: crypto.randomUUID(),
            date: dateStringToISOString(logDate),
            mealType,
            foods,
            description: description.trim() || undefined,
            status: 'consumed',
        });

        setSaveState('success');
        setTimeout(() => {
            onClose();
            setTimeout(() => {
                setDescription('');
                setTagItems([]);
                setSaveState('idle');
                setManualResolveTagKey(null);
            }, 400);
        }, 1800);
    }, [addToast, description, logDate, mealType, onClose, onSave, tagItems]);

    const totalMacros = useMemo(() => tagItems.reduce((acc, tag) => {
        if (!tag.loggedFood) return acc;
        acc.calories += tag.loggedFood.calories;
        acc.protein += tag.loggedFood.protein;
        acc.carbs += tag.loggedFood.carbs;
        acc.fats += tag.loggedFood.fats;
        return acc;
    }, { calories: 0, protein: 0, carbs: 0, fats: 0 }), [tagItems]);

    const blockingCount = tagItems.filter(isBlockingResolution).length;
    const macroCaloriesFill = Math.min(100, (totalMacros.calories / CALORIES_VISUAL_TARGET) * 100);
    const macroBarSegments = MACRO_GOALS.map(goal => {
        const value = totalMacros[goal.key];
        const fill = goal.target > 0 ? Math.min(100, (value / goal.target) * 100) : 0;
        return {
            label: goal.label,
            value,
            fill,
            color: goal.color,
        };
    });

    if (!isOpen) return null;

    return (
        <AnimatePresence>
            {isOpen && (
                <>
                    {displayMode === 'drawer' && <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-[2000] bg-black/40 backdrop-blur-sm" onClick={onClose} />}
                    <motion.div
                        initial={displayMode === 'appendix' ? { opacity: 0, scale: 0.95 } : { y: '100%' }}
                        animate={displayMode === 'appendix' ? { opacity: 1, scale: 1 } : { y: 0 }}
                        exit={displayMode === 'appendix' ? { opacity: 0, scale: 0.95 } : { y: '100%' }}
                        transition={{ type: 'spring', damping: 25, stiffness: 200 }}
                        className={`${sheetContainerClass} overflow-hidden flex flex-col`}
                    >
                        {displayMode === 'drawer' ? (
                            <div className="px-6 pt-8 pb-4 flex justify-between items-center">
                                <div>
                                    <h3 className="text-[10px] font-black text-[#49454F]/40 uppercase tracking-[0.2em] mb-1 font-['Roboto']">Registro rapido</h3>
                                    <h2 className="text-[28px] font-black text-[#1C1B1F] tracking-tighter leading-none font-['Roboto']">Registra tu comida</h2>
                                </div>
                                {showCloseButton && (
                                    <button onClick={onClose} className="w-10 h-10 bg-black/5 rounded-full hover:bg-black/10 transition-colors flex items-center justify-center">
                                        <XIcon size={20} className="text-[#49454F]" />
                                    </button>
                                )}
                            </div>
                        ) : (
                            <div className="px-4 pt-5 pb-3 flex items-center justify-between">
                                <div>
                                    <p className="text-[9px] font-bold uppercase tracking-[0.3em] text-[#49454F]/60">Registro rápido</p>
                                    <p className="text-[16px] font-black text-[#1C1B1F] uppercase tracking-[0.25em]">Comida</p>
                                </div>
                                {showCloseButton && (
                                    <button onClick={onClose} className="w-8 h-8 rounded-full bg-slate-100 text-[#49454F]/70 flex items-center justify-center transition hover:bg-slate-200">
                                        <XIcon size={16} />
                                    </button>
                                )}
                            </div>
                        )}

                        <div className={scrollAreaClass}>
                            {/* Macros Summary Panel */}
                            <motion.div layout className={`${displayMode === 'appendix' ? 'bg-white/60 backdrop-blur-xl border-white/40' : 'bg-white border-black/[0.03]'} rounded-[32px] p-6 shadow-sm border flex flex-col gap-4 mt-2 relative overflow-hidden`}>
                                <div className="flex flex-col gap-2">
                                    <div>
                                        <div className="text-[48px] font-black text-[#1C1B1F] font-['Roboto'] tracking-tighter leading-none">{Math.round(totalMacros.calories)}</div>
                                        <div className="text-[10px] uppercase font-black text-[#49454F]/40 tracking-[0.25em]">Calorias totales</div>
                                    </div>
                                    <div className="h-2 rounded-full bg-slate-100 overflow-hidden">
                                        <div className="h-full rounded-full bg-gradient-to-r from-[#FF8A00] to-[#FF3F5A]" style={{ width: `${macroCaloriesFill}%` }} />
                                    </div>
                                </div>
                                <div className="grid grid-cols-3 gap-3">
                                    {macroBarSegments.map(segment => (
                                        <div key={segment.label} className="flex flex-col gap-1">
                                            <div className="h-1.5 rounded-full bg-slate-100 overflow-hidden">
                                                <div className={`h-full rounded-full ${segment.color}`} style={{ width: `${segment.fill}%` }} />
                                            </div>
                                            <div className="text-[10px] font-black uppercase tracking-[0.3em] text-[#49454F]/50">{segment.label}</div>
                                            <div className="text-[10px] font-black text-[#1C1B1F]">{Math.round(segment.value)}</div>
                                        </div>
                                    ))}
                                </div>
                                <div className="flex gap-4">
                                    <MacroBadge label="PROT" val={totalMacros.protein} color="bg-rose-500" />
                                    <MacroBadge label="CARB" val={totalMacros.carbs} color="bg-emerald-500" />
                                    <MacroBadge label="FATS" val={totalMacros.fats} color="bg-amber-500" />
                                </div>
                            </motion.div>

                            {/* Selectors Row */}
                            <div className="space-y-3">
                                <div className={`rounded-[32px] border p-4 flex flex-col gap-2 ${displayMode === 'appendix' ? 'bg-white/70 border-white/50 backdrop-blur-xl' : 'bg-white border-black/[0.03]'}`}>
                                    <label className="text-[8px] uppercase tracking-[0.5em] text-[#49454F]/50">Fecha del registro</label>
                                    <div className="flex items-center gap-2">
                                        <SearchIcon size={16} className="text-[#49454F]/30" />
                                        <input
                                            type="date"
                                            value={logDate}
                                            onChange={event => setLogDate(event.target.value)}
                                            className="w-full bg-transparent text-[11px] font-black text-[#1C1B1F] outline-none uppercase tracking-[0.3em] font-['Roboto']"
                                        />
                                    </div>
                                </div>
                                <div className={`grid grid-cols-2 gap-2 rounded-[28px] border p-2 ${displayMode === 'appendix' ? 'bg-white/60 border-white/40 backdrop-blur-xl' : 'bg-black/[0.03] border-black/[0.03]'}`}>
                                    {mealOptions.map(option => (
                                        <button
                                            key={option.id}
                                            onClick={() => setMealType(option.id)}
                                            className={`rounded-[20px] px-4 py-2 text-[10px] font-black uppercase tracking-[0.4em] transition-all ${mealType === option.id ? 'bg-[#1C1B1F] text-white shadow-lg shadow-black/20' : 'bg-white text-[#49454F]/60 hover:bg-black/5'}`}
                                        >
                                            {option.label}
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {/* Tabs */}
                            <div className={`flex p-1.5 rounded-[32px] overflow-x-auto hide-scrollbar border ${displayMode === 'appendix' ? 'bg-white/40 backdrop-blur-xl border-white/40' : 'bg-black/[0.03] border-black/[0.02]'}`}>
                                <TabBtn active={activeTab === 'description'} displayMode={displayMode} onClick={() => setActiveTab('description')}>Descriptivo</TabBtn>
                                <TabBtn active={activeTab === 'search'} displayMode={displayMode} onClick={() => setActiveTab('search')}>Buscador</TabBtn>
                                <TabBtn active={activeTab === 'templates'} displayMode={displayMode} onClick={() => setActiveTab('templates')}>Plantillas</TabBtn>
                            </div>

                            {displayMode === 'appendix' && (
                                <div className="flex justify-end">
                                    <button
                                        onClick={handleSave}
                                        disabled={blockingCount > 0}
                                        className={`py-2 px-5 text-[11px] font-black uppercase tracking-[0.3em] rounded-2xl transition-all ${blockingCount > 0 ? 'bg-slate-300 text-slate-500 cursor-not-allowed' : 'bg-[#1C1B1F] text-white shadow-lg shadow-black/20'}`}
                                    >
                                        Confirmar
                                    </button>
                                </div>
                            )}

                            {blockingCount > 0 && (
                                <motion.div initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} className="rounded-[28px] border border-rose-200/50 bg-rose-50/50 px-6 py-4 text-[12px] font-black uppercase tracking-wider text-rose-600 shadow-sm backdrop-blur-sm text-center">
                                    Revisar {blockingCount} alimento{blockingCount > 1 ? 's' : ''}
                                </motion.div>
                            )}

                            {/* Content Area */}
                            <AnimatePresence mode="wait">
                                {activeTab === 'description' && (
                                    <motion.div key="desc" initial={{ opacity: 0, scale: 0.98 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.98 }}>
                                        <div className="relative group">
                                            <textarea
                                                className={`w-full h-36 rounded-[32px] p-8 text-base font-medium text-[#1C1B1F] border shadow-sm outline-none transition-all placeholder-[#49454F]/30 resize-none font-['Roboto'] leading-relaxed ${displayMode === 'appendix' ? 'bg-white/60 backdrop-blur-xl border-white/40 focus:border-white/60' : 'bg-white border-black/[0.03] focus:border-primary/30'}`}
                                                placeholder="Describe tu comida... ej: 200g arroz, 150g pollo"
                                                value={description}
                                                onChange={event => setDescription(event.target.value)}
                                            />
                                            <div className="absolute top-6 right-8 flex gap-2">
                                                <div className="w-2 h-2 rounded-full bg-primary/20 animate-pulse" />
                                            </div>
                                        </div>
                                    </motion.div>
                                )}

                                {activeTab === 'search' && (
                                    <motion.div key="search" initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} className="space-y-4">
                                        <div className="relative">
                                            <SearchIcon size={18} className="absolute left-6 top-1/2 -translate-y-1/2 text-[#49454F]/20" />
                                            <input
                                                className="w-full bg-white rounded-[28px] pl-14 pr-6 py-4 text-base font-medium border border-black/[0.03] shadow-sm outline-none focus:ring-1 ring-primary/20 transition-all font-['Roboto']"
                                                placeholder="Buscar alimento..."
                                                value={searchQuery}
                                                onChange={event => setSearchQuery(event.target.value)}
                                            />
                                        </div>

                                        <div className="space-y-3 max-h-80 overflow-y-auto pr-1">
                                            {searchLoading && <div className="py-8 text-center text-[10px] font-black uppercase tracking-widest text-[#49454F]/20">Buscando...</div>}
                                            {searchResults.map(food => (
                                                <button
                                                    key={food.id}
                                                    onClick={() => handleSearchFoodSelect(food)}
                                                    className="w-full bg-white p-4 rounded-[28px] border border-black/[0.03] flex items-center gap-4 hover:translate-x-1 transition-all shadow-sm group"
                                                >
                                                    <div className="w-12 h-12 bg-black/5 rounded-[22px] flex items-center justify-center shrink-0 group-hover:bg-primary/10 transition-colors">
                                                        <UtensilsIcon size={20} className="text-primary" />
                                                    </div>
                                                    <div className="text-left min-w-0 flex-1">
                                                        <p className="text-[15px] font-black text-[#1C1B1F] truncate font-['Roboto'] tracking-tight">{food.name}</p>
                                                        <p className="text-[10px] font-bold text-[#49454F]/30 uppercase tracking-tight mt-1">{food.calories} kcal · p:{food.protein} c:{food.carbs} f:{food.fats}</p>
                                                    </div>
                                                    <div className="w-8 h-8 rounded-full bg-black/5 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all">
                                                        <PlusIcon size={16} className="text-[#1C1B1F]" />
                                                    </div>
                                                </button>
                                            ))}
                                        </div>
                                    </motion.div>
                                )}

                                {activeTab === 'templates' && (
                                    <motion.div key="templates" initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="rounded-[32px] overflow-hidden bg-white border border-black/[0.03] p-6 shadow-sm">
                                        <MealTemplateSelector
                                            onSelect={foods => {
                                                const templateTags = foods.map(food => ({
                                                    key: crypto.randomUUID(),
                                                    origin: 'template' as const,
                                                    tag: food.foodName,
                                                    portion: 'medium' as PortionPreset,
                                                    quantity: 1,
                                                    foodItem: null,
                                                    loggedFood: food,
                                                    resolutionStatus: 'resolved' as TagResolutionStatus,
                                                    resolutionConfidence: 'high' as SearchConfidence,
                                                    resolutionReason: 'Agregado desde plantilla.',
                                                    candidateFoods: [],
                                                }));
                                                setTagItems(prev => [...prev.filter(tag => tag.origin !== 'template'), ...templateTags]);
                                                setActiveTab('description');
                                            }}
                                            onClose={() => { /* noop */ }}
                                        />
                                    </motion.div>
                                )}
                            </AnimatePresence>

                            {/* Food List Section */}
                            {tagItems.length > 0 && (
                                <div className="space-y-4">
                                    <div className="flex items-center justify-between px-1">
                                        <h4 className="text-[10px] font-black text-[#49454F]/30 uppercase tracking-[0.2em]">Detectados</h4>
                                        <span className="text-[10px] font-black text-primary px-3 py-1 bg-primary/10 rounded-full">{tagItems.length}</span>
                                    </div>
                                    <div className="space-y-3">
                                        {tagItems.map(tag => {
                                            const isExpanded = expandedTagKey === tag.key;
                                            const canExpand = tag.candidateFoods.length > 0 || Boolean(tag.subItems?.length) || tag.resolutionStatus !== 'resolved';
                                            return (
                                                <div key={tag.key} className="bg-white rounded-[32px] p-5 shadow-sm border border-black/[0.03] transition-all">
                                                    <div className="flex items-start gap-4">
                                                        <button
                                                            type="button"
                                                            onClick={() => {
                                                                if (!canExpand) return;
                                                                setExpandedTagKey(prev => prev === tag.key ? null : tag.key);
                                                            }}
                                                            className="flex items-start gap-4 flex-1 text-left group"
                                                        >
                                                            <div className={`w-12 h-12 rounded-[22px] flex items-center justify-center shrink-0 transition-colors ${tag.resolutionStatus === 'resolved' ? 'bg-primary/10' : tag.resolutionStatus === 'pending' ? 'bg-black/5' : 'bg-rose-50'}`}>
                                                                <UtensilsIcon size={20} className={tag.resolutionStatus === 'resolved' ? 'text-primary' : tag.resolutionStatus === 'pending' ? 'text-black/10' : 'text-rose-500'} />
                                                            </div>
                                                            <div className="flex-1 min-w-0 pt-1">
                                                                <div className="flex items-center gap-2 flex-wrap mb-1">
                                                                    <p className="text-[15px] font-black text-[#1C1B1F] truncate leading-none font-['Roboto'] tracking-tight group-hover:text-primary transition-colors">{tag.tag}</p>
                                                                    <StatusBadge status={tag.resolutionStatus} confidence={tag.resolutionConfidence} />
                                                                </div>
                                                                <p className="text-[11px] font-bold text-[#49454F]/40 tracking-tight">
                                                                    {tag.loggedFood
                                                                        ? `${Math.round(tag.loggedFood.calories)} kcal · ${tag.loggedFood.amount}${tag.loggedFood.unit}`
                                                                        : tag.resolutionStatus === 'pending'
                                                                            ? 'Buscando coincidencia...'
                                                                            : tag.resolutionReason || 'Sin resolver'}
                                                                </p>
                                                            </div>
                                                        </button>
                                                        <div className="flex items-center gap-1 shrink-0 pt-1">
                                                            {canExpand && (
                                                                <button
                                                                    onClick={() => setExpandedTagKey(prev => prev === tag.key ? null : tag.key)}
                                                                    className="w-10 h-10 flex items-center justify-center text-[#49454F]/20 hover:text-[#1C1B1F] transition-colors"
                                                                >
                                                                    <ChevronDownIcon size={18} className={`transition-transform duration-300 ${isExpanded ? 'rotate-180' : ''}`} />
                                                                </button>
                                                            )}
                                                            <button
                                                                onClick={() => setTagItems(prev => prev.filter(item => item.key !== tag.key))}
                                                                className="w-10 h-10 flex items-center justify-center text-[#49454F]/10 hover:text-rose-500 hover:bg-rose-50 rounded-full transition-all"
                                                            >
                                                                <TrashIcon size={18} />
                                                            </button>
                                                        </div>
                                                    </div>

                                                    <AnimatePresence>
                                                        {isExpanded && (
                                                            <motion.div
                                                                initial={{ height: 0, opacity: 0 }}
                                                                animate={{ height: 'auto', opacity: 1 }}
                                                                exit={{ height: 0, opacity: 0 }}
                                                                className="overflow-hidden"
                                                            >
                                                                <div className="mt-5 pt-5 border-t border-black/[0.03] space-y-4">
                                                                    {tag.subItems?.length ? (
                                                                        <div className="rounded-[20px] bg-black/[0.02] px-4 py-3 text-[11px] font-medium text-[#49454F]/60">
                                                                            Ingredientes: {tag.subItems.map(item => item.tag).join(', ')}
                                                                        </div>
                                                                    ) : null}

                                                                    {tag.candidateFoods.length > 0 && (
                                                                        <div className="space-y-3">
                                                                            <p className="text-[9px] font-black text-[#49454F]/20 uppercase tracking-[0.2em] px-1">Mejores coincidencias</p>
                                                                            <div className="grid gap-2">
                                                                                {tag.candidateFoods.map(candidate => (
                                                                                    <button
                                                                                        key={`${tag.key}-${candidate.food.id}`}
                                                                                        onClick={() => handleCandidateSelect(tag.key, candidate)}
                                                                                        className="w-full rounded-[24px] border border-black/[0.03] bg-black/[0.01] px-4 py-3 text-left hover:border-primary/30 hover:bg-primary/5 transition-all group"
                                                                                    >
                                                                                        <div className="flex items-center justify-between gap-4">
                                                                                            <div className="min-w-0">
                                                                                                <p className="text-[13px] font-black text-[#1C1B1F] truncate font-['Roboto']">{candidate.food.name}</p>
                                                                                                <p className="text-[9px] font-bold text-[#49454F]/30 uppercase mt-0.5">
                                                                                                    {candidate.food.calories} kcal · {confidenceLabel(candidate.confidence)}
                                                                                                </p>
                                                                                            </div>
                                                                                            <div className="text-[9px] font-black uppercase tracking-widest text-[#1C1B1F]/20 group-hover:text-primary transition-colors">Select</div>
                                                                                        </div>
                                                                                    </button>
                                                                                ))}
                                                                            </div>
                                                                        </div>
                                                                    )}

                                                                    {tag.resolutionStatus !== 'resolved' && (
                                                                        <button
                                                                            onClick={() => handleOpenManualSearch(tag)}
                                                                            className="w-full rounded-[24px] border border-black/[0.05] bg-white py-4 text-[10px] font-black uppercase tracking-widest text-[#1C1B1F] hover:bg-black/5 transition-all shadow-sm"
                                                                        >
                                                                            Búsqueda Manual
                                                                        </button>
                                                                    )}
                                                                </div>
                                                            </motion.div>
                                                        )}
                                                    </AnimatePresence>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Footer - Actions */}
                        {displayMode === 'drawer' && (
                            <div className="absolute bottom-0 inset-x-0 px-8 pt-12 pb-28 bg-gradient-to-t from-[#F7F7F7] via-[#F7F7F7] 70% to-transparent pointer-events-none">
                                <div className="pointer-events-auto flex gap-4">
                                    <button
                                        onClick={onClose}
                                        className="flex-1 py-5 bg-white border border-black/[0.05] rounded-[32px] text-[#49454F] text-[10px] font-black uppercase tracking-widest active:scale-95 transition-all shadow-sm font-['Roboto']"
                                    >
                                        Cancelar
                                    </button>
                                    <button
                                        onClick={handleSave}
                                        className="flex-[2] py-5 bg-[#1C1B1F] text-white rounded-[32px] text-[11px] font-black uppercase tracking-[0.2em] shadow-2xl active:scale-95 transition-all flex items-center justify-center gap-3 font-['Roboto']"
                                    >
                                        Confirmar Registro
                                        <PlusIcon size={16} />
                                    </button>
                                </div>
                            </div>
                        )}

                        {/* Success Overlay */}
                        <AnimatePresence>
                            {saveState === 'success' && (
                                <motion.div
                                    initial={{ opacity: 0, scale: 0.95 }}
                                    animate={{ opacity: 1, scale: 1 }}
                                    exit={{ opacity: 0, scale: 0.95 }}
                                    className="absolute inset-0 z-[2002] bg-[#F7F7F7] flex flex-col items-center justify-center p-8 text-center"
                                >
                                    <div className="w-24 h-24 bg-primary rounded-full flex items-center justify-center mb-6 shadow-2xl shadow-primary/30">
                                        <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ delay: 0.2 }}>
                                            <UtensilsIcon size={40} className="text-white" />
                                        </motion.div>
                                    </div>
                                    <h2 className="text-3xl font-black text-[#1C1B1F] font-['Roboto'] tracking-tighter uppercase mb-2">¡Registrado!</h2>
                                    <p className="text-[#49454F]/60 font-medium">Tu progreso ha sido actualizado.</p>
                                </motion.div>
                            )}
                        </AnimatePresence>
                    </motion.div>
                </>
            )}
        </AnimatePresence>
    );
};

// --- Helper Components ---

const MacroBadge: React.FC<{ label: string; val: number; color: string }> = ({ label, val, color }) => (
    <div className="flex flex-col items-center gap-1.5 min-w-[50px]">
        <div className={`w-1.5 h-1.5 rounded-full ${color} shadow-[0_0_8px] ${color.replace('bg-', 'shadow-')}/40`} />
        <span className="text-[14px] font-black text-[#1C1B1F] tracking-tighter font-['Roboto'] leading-none">{Math.round(val)}</span>
        <span className="text-[8px] font-black text-[#49454F]/20 uppercase tracking-widest">{label}</span>
    </div>
);

const TabBtn: React.FC<{ active: boolean; displayMode?: 'drawer' | 'appendix'; onClick: () => void; children: React.ReactNode }> = ({ active, displayMode, onClick, children }) => {
    const activeClass = displayMode === 'appendix'
        ? 'bg-[#1C1B1F] text-white shadow-lg shadow-black/30'
        : 'bg-[#1C1B1F] text-white shadow-xl shadow-black/20';
    const inactiveClass = displayMode === 'appendix'
        ? 'bg-white/80 text-[#49454F]/70 border border-white/40 hover:bg-white'
        : 'bg-white/90 text-[#49454F]/70 border border-black/[0.05] hover:bg-white';

    return (
        <button
            onClick={onClick}
            className={`flex-1 py-3 px-4 rounded-[28px] text-[11px] font-black uppercase tracking-[0.35em] transition-all duration-200 ${active ? activeClass : inactiveClass}`}
        >
            {children}
        </button>
    );
};

const StatusBadge: React.FC<{ status: TagResolutionStatus; confidence: SearchConfidence }> = ({ status, confidence }) => {
    if (status === 'resolved') {
        return <span className="inline-flex items-center rounded-full bg-primary/10 px-2.5 py-1 text-[8px] font-black uppercase tracking-wider text-primary">Confirmado</span>;
    }

    if (status === 'pending') {
        return <span className="inline-flex items-center rounded-full bg-black/5 px-2.5 py-1 text-[8px] font-black uppercase tracking-wider text-black/20 animate-pulse">Analizando</span>;
    }

    return (
        <span className="inline-flex items-center rounded-full bg-rose-100 px-2.5 py-1 text-[8px] font-black uppercase tracking-wider text-rose-500">
            Revisión necesaria
        </span>
    );
};
