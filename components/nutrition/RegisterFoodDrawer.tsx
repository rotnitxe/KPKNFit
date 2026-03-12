// components/nutrition/RegisterFoodDrawer.tsx
// Drawer lateral para registrar comida con resolucion guiada.

import React, { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import type { NutritionLog, LoggedFood, FoodItem, Settings, CookingMethod, PortionPreset, ParsedMealItem } from '../../types';
import { AlchemyEngine } from '../../services/alchemyEngine';
import { parseFreeFormNutrition } from '../../services/aiNutritionParser';
import {
    cancelCurrentLocalAiAnalysis,
    getLocalAiStatus,
} from '../../services/localAiService';
import {
    recordNutritionAiManualCorrection,
    startNutritionAiTrace,
    type NutritionAiTelemetryTagSnapshot,
} from '../../services/nutritionAiTelemetryService';
import {
    preloadFoodDatabases,
    searchFoods,
    searchFoodsExtended,
    rememberFoodResolution,
    type SearchConfidence,
    type SearchFoodCandidate,
} from '../../services/foodSearchService';
import { useAppDispatch } from '../../contexts/AppContext';
import { XIcon, TrashIcon, UtensilsIcon, SearchIcon, ChevronDownIcon, PlusIcon, AlertTriangleIcon } from '../icons';
import { MealTemplateSelector } from './MealTemplateSelector';
import { getLocalDateString, dateStringToISOString } from '../../utils/dateUtils';
import { motion, AnimatePresence } from 'framer-motion';

const mealOptions: { id: NutritionLog['mealType']; label: string; }[] = [
    { id: 'breakfast', label: 'Desayuno' },
    { id: 'lunch', label: 'Almuerzo' },
    { id: 'dinner', label: 'Cena' },
    { id: 'snack', label: 'Snack' },
];

const SEARCH_DEBOUNCE_MS = 350;

type TagOrigin = 'description' | 'manual' | 'template';
type TagResolutionStatus = 'pending' | 'resolved' | 'needs_review' | 'unresolved';
type TagAnalysisSource = 'rules' | 'database' | 'user-memory' | 'local-ai-estimate' | 'local-heuristic' | 'review';
type DescriptionFlowState = 'idle' | 'analyzing' | 'ready' | 'failed';
type SaveMode = 'confirmed' | 'estimated' | 'failed';

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
    analysisSource?: TagAnalysisSource;
    analysisConfidence?: number;
    reviewRequired?: boolean;
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

function hasManualMacroOverrides(tag: TagWithFood): boolean {
    return Boolean(tag.macroOverrides && tag.analysisSource !== 'local-ai-estimate');
}

function confidenceLabel(confidence: SearchConfidence): string {
    if (confidence === 'high') return 'Alta';
    if (confidence === 'medium') return 'Media';
    return 'Baja';
}

function confidenceFromScore(score?: number): SearchConfidence {
    if ((score ?? 0) >= 0.8) return 'high';
    if ((score ?? 0) >= 0.55) return 'medium';
    return 'low';
}

function isEstimatedTag(tag: TagWithFood): boolean {
    return Boolean(
        tag.analysisSource === 'local-ai-estimate'
        || tag.isFuzzyMatch
        || tag.resolutionConfidence !== 'high'
        || tag.reviewRequired,
    );
}

function isAssistedResolutionEnabled(settings: Settings): boolean {
    return Boolean(
        (settings.nutritionResolutionMode === 'assisted'
            || settings.nutritionDescriptionMode === 'assisted'
            || settings.nutritionDescriptionMode === 'local-ai')
        && settings.nutritionUseLocalAI,
    );
}

function detailHintLabel(tag: TagWithFood): string {
    if (tag.analysisSource === 'local-ai-estimate') return 'Estimación automática';
    if (tag.analysisSource === 'user-memory') return 'Ajustado según tus elecciones anteriores';
    if (tag.subItems?.length) return 'Calculado desde una preparación compuesta';
    if (tag.isFuzzyMatch || tag.resolutionConfidence !== 'high') return 'Referencia aproximada';
    return 'Resultado listo';
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
        analysisSource: item.analysisSource,
        analysisConfidence: item.analysisConfidence,
        reviewRequired: item.reviewRequired,
    };
}

function toTelemetryTagSnapshot(tag: TagWithFood): NutritionAiTelemetryTagSnapshot {
    return {
        resolutionStatus: tag.resolutionStatus,
        analysisSource: tag.analysisSource,
        analysisConfidence: tag.analysisConfidence,
        reviewRequired: tag.reviewRequired,
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
        analysisSource: item?.analysisSource || 'rules',
        analysisConfidence: item?.analysisConfidence,
        reviewRequired: item?.reviewRequired,
    };
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
    const [saveState, setSaveState] = useState<'idle' | 'success'>('idle');
    const [manualResolveTagKey, setManualResolveTagKey] = useState<string | null>(null);
    const [descriptionState, setDescriptionState] = useState<DescriptionFlowState>('idle');
    const [lastAnalyzedDescription, setLastAnalyzedDescription] = useState('');
    const [showResultDetails, setShowResultDetails] = useState(false);

    const resolutionRequestRef = useRef(0);
    const searchRequestRef = useRef(0);
    const tagItemsRef = useRef<TagWithFood[]>([]);
    const analysisTraceRef = useRef<ReturnType<typeof startNutritionAiTrace> | null>(null);

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
        setSaveState('idle');
        setManualResolveTagKey(null);
        setDescriptionState('idle');
        setLastAnalyzedDescription('');
        setShowResultDetails(false);
    }, [isOpen, initialDate, initialMealType]);

    useEffect(() => {
        tagItemsRef.current = tagItems;
    }, [tagItems]);

    useEffect(() => {
        if (!isOpen) return;
        preloadFoodDatabases('hot');
    }, [isOpen]);

    const buildLoggedFromItem = useCallback((item: FoodItem, tag: TagWithFood): LoggedFood => {
        return AlchemyEngine.calculateLoggedFood(item, tag);
    }, []);

    const createResolvedTag = useCallback((
        food: FoodItem,
        origin: TagOrigin,
        overrides: Partial<TagWithFood> = {},
    ): TagWithFood => {
        const baseTag: TagWithFood = {
            key: crypto.randomUUID(),
            origin,
            tag: overrides.tag ?? food.name,
            portion: overrides.portion ?? 'medium',
            quantity: overrides.quantity ?? 1,
            amountGrams: overrides.amountGrams,
            cookingMethod: overrides.cookingMethod,
            brandHint: overrides.brandHint,
            macroOverrides: overrides.macroOverrides,
            anatomicalModifiers: overrides.anatomicalModifiers,
            heuristicModifiers: overrides.heuristicModifiers,
            preparationModifiers: overrides.preparationModifiers,
            stateModifiers: overrides.stateModifiers,
            compositionModifiers: overrides.compositionModifiers,
            dimensionalMultiplier: overrides.dimensionalMultiplier,
            subItems: overrides.subItems,
            isGroup: overrides.isGroup,
            foodItem: food,
            loggedFood: null,
            isFuzzyMatch: overrides.isFuzzyMatch,
            resolutionStatus: 'resolved',
            resolutionConfidence: overrides.resolutionConfidence ?? 'high',
            resolutionScore: overrides.resolutionScore,
            resolutionReason: overrides.resolutionReason,
            candidateFoods: overrides.candidateFoods ?? [],
            sourceTrace: overrides.sourceTrace,
            analysisSource: overrides.analysisSource ?? 'database',
            analysisConfidence: overrides.analysisConfidence,
            reviewRequired: overrides.reviewRequired,
        };

        return {
            ...baseTag,
            loggedFood: buildLoggedFromItem(food, baseTag),
        };
    }, [buildLoggedFromItem]);

    const updateTagItem = useCallback((tagKey: string, updater: (current: TagWithFood) => TagWithFood) => {
        setTagItems(prev => prev.map(tag => tag.key === tagKey ? updater(tag) : tag));
    }, []);

    const resolveWithAiEstimate = useCallback((
        tag: TagWithFood,
        requestId: number,
        candidateFoods: SearchFoodCandidate[] = [],
        reason = 'Resultado estimado automáticamente.',
    ) => {
        if (requestId !== resolutionRequestRef.current || !tag.macroOverrides) return;

        const customFood = createCustomFoodFromOverrides(tag);
        updateTagItem(tag.key, current => ({
            ...current,
            foodItem: customFood,
            loggedFood: buildLoggedFromItem(customFood, current),
            resolutionStatus: 'resolved',
            resolutionConfidence: confidenceFromScore(current.analysisConfidence),
            resolutionReason: reason,
            candidateFoods,
            sourceTrace: ['local_ai_estimate'],
            analysisSource: 'local-ai-estimate',
        }));
    }, [buildLoggedFromItem, updateTagItem]);

    const resolveDescriptionTag = useCallback(async (tag: TagWithFood, requestId: number) => {
        if (tag.isGroup && tag.subItems?.length) {
            const subResults = [];
            for (const subItem of tag.subItems) {
                const subResult = await searchFoods(subItem.tag, settings, subItem.brandHint);
                if (requestId !== resolutionRequestRef.current) return;
                subResults.push(subResult);
            }
            if (requestId !== resolutionRequestRef.current) return;

            const subItemCount = tag.subItems.length;
            const chosenCandidates = subResults
                .map(result => result.candidates[0] ?? null)
                .filter((candidate): candidate is SearchFoodCandidate => Boolean(candidate));

            if (chosenCandidates.length === 0) {
                if (tag.macroOverrides) {
                    resolveWithAiEstimate(tag, requestId, [], 'Resultado estimado para una preparación compuesta.');
                    return;
                }

                updateTagItem(tag.key, current => ({
                    ...current,
                    foodItem: null,
                    loggedFood: null,
                    resolutionStatus: 'unresolved',
                    resolutionConfidence: 'low',
                    resolutionReason: 'No pudimos estimar esta preparación todavía.',
                    candidateFoods: [],
                    analysisSource: current.analysisSource === 'local-ai-estimate' ? 'review' : current.analysisSource,
                }));
                return;
            }

            const subLoggedFoods = tag.subItems
                .map((subItem, index) => {
                    const candidate = subResults[index].candidates[0];
                    if (!candidate) return null;

                    const subTag = createResolvedTag(candidate.food, 'description', {
                        ...currentLikeTag(subItem),
                        resolutionConfidence: subResults[index].bestConfidence,
                        resolutionScore: candidate.score,
                        candidateFoods: subResults[index].candidates,
                        analysisSource: candidate.learned ? 'user-memory' : 'database',
                        isFuzzyMatch: candidate.confidence !== 'high',
                    });

                    return subTag.loggedFood;
                })
                .filter((loggedFood): loggedFood is LoggedFood => Boolean(loggedFood));

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
                resolutionStatus: chosenCandidates.length === subItemCount && subResults.every(result => result.canAutoSelect)
                    ? 'resolved'
                    : 'needs_review',
                resolutionConfidence: chosenCandidates.length === subItemCount && subResults.every(result => result.canAutoSelect)
                    ? 'high'
                    : 'medium',
                resolutionReason: chosenCandidates.length === subItemCount && subResults.every(result => result.canAutoSelect)
                    ? 'Calculado usando los ingredientes detectados.'
                    : 'Calculamos una referencia aproximada para esta preparación y conviene revisarla.',
                candidateFoods: chosenCandidates.slice(0, 4),
                analysisSource: chosenCandidates.some(candidate => candidate.learned) ? 'user-memory' : 'database',
                reviewRequired: !subResults.every(result => result.canAutoSelect),
            }));
            return;
        }

        const result = await searchFoods(tag.tag, settings, tag.brandHint);
        if (requestId !== resolutionRequestRef.current) return;

        if (!result.results[0] && tag.macroOverrides) {
            if (hasManualMacroOverrides(tag)) {
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
                    analysisSource: 'rules',
                }));
                return;
            }

            resolveWithAiEstimate(tag, requestId, [], 'Estimado por IA local y macros offline.');
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
                analysisSource: current.analysisSource === 'local-ai-estimate' ? 'review' : current.analysisSource,
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
                analysisSource: bestCandidate.learned ? 'user-memory' : 'database',
                reviewRequired: false,
            }));
            return;
        }

        if (tag.macroOverrides && tag.analysisSource === 'local-ai-estimate') {
            resolveWithAiEstimate(tag, requestId, result.candidates.slice(0, 4), 'Estimado por IA local; la base local quedó ambigua.');
            return;
        }

        const bestCandidate = result.candidates[0];
        if (bestCandidate) {
            updateTagItem(tag.key, current => ({
                ...current,
                foodItem: null,
                loggedFood: null,
                resolutionStatus: 'needs_review',
                resolutionConfidence: result.bestConfidence,
                resolutionScore: bestCandidate.score,
                resolutionReason: result.decisionReason || 'Selecciona una de las sugerencias para confirmar.',
                candidateFoods: result.candidates.slice(0, 4),
                sourceTrace: bestCandidate.trace,
                isFuzzyMatch: true,
                analysisSource: bestCandidate.learned ? 'user-memory' : 'review',
                analysisConfidence: bestCandidate.score,
                reviewRequired: true,
            }));
            setExpandedTagKey(prev => prev ?? tag.key);
            return;
        }

        updateTagItem(tag.key, current => ({
            ...current,
            foodItem: null,
            loggedFood: null,
            resolutionStatus: 'unresolved',
            resolutionConfidence: result.bestConfidence,
            resolutionScore: result.bestScore,
            resolutionReason: result.decisionReason || 'No pudimos estimar esta comida todavía.',
            candidateFoods: result.candidates.slice(0, 4),
            sourceTrace: result.candidates[0]?.trace,
            isFuzzyMatch: true,
            analysisSource: current.analysisSource === 'local-ai-estimate' ? 'review' : current.analysisSource,
            reviewRequired: true,
        }));
        setExpandedTagKey(prev => prev ?? tag.key);
    }, [createResolvedTag, buildLoggedFromItem, resolveWithAiEstimate, settings, updateTagItem]);

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
            searchFoodsExtended(trimmed, settings)
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

    const cancelAnalysis = useCallback(() => {
        if (descriptionState === 'analyzing') {
            analysisTraceRef.current?.cancel({
                parsed: null,
                tags: tagItems
                    .filter(tag => tag.origin === 'description')
                    .map(toTelemetryTagSnapshot),
            });
            analysisTraceRef.current = null;
        }

        resolutionRequestRef.current += 1;
        const preserved = tagItems.filter(tag => tag.origin !== 'description');
        setTagItems(preserved);
        setExpandedTagKey(null);
        setDescriptionState(description.trim() ? 'idle' : 'idle');
        setLastAnalyzedDescription('');
        setShowResultDetails(false);
        if (isAssistedResolutionEnabled(settings)) {
            cancelCurrentLocalAiAnalysis().catch(() => { });
        }
    }, [description, descriptionState, settings, tagItems]);

    const analyzeDescription = useCallback(async () => {
        const trimmedDescription = description.trim();
        const preserved = tagItems.filter(tag => tag.origin !== 'description');

        if (!trimmedDescription) {
            setTagItems(preserved);
            addToast('Escribe una descripción antes de analizar.', 'danger');
            return;
        }

        const requestId = ++resolutionRequestRef.current;
        const assistedMode = isAssistedResolutionEnabled(settings);
        const trace = startNutritionAiTrace({
            descriptionLength: trimmedDescription.length,
            localAiEnabled: assistedMode,
            requestedModel: settings?.nutritionLocalModel ?? null,
            reanalysis: trimmedDescription === lastAnalyzedDescription,
        });
        analysisTraceRef.current = trace;
        trace.markStage('interpreting');
        if (assistedMode) {
            getLocalAiStatus().then((status) => {
                trace.setRuntimeStatus(status);
            }).catch(() => {
                trace.setRuntimeStatus(null);
            });
        } else {
            trace.setRuntimeStatus(null);
        }

        setDescriptionState('analyzing');
        setExpandedTagKey(null);
        setManualResolveTagKey(null);
        setTagItems(preserved);

        try {
            const parsed = await parseFreeFormNutrition(trimmedDescription, settings, {
                onStageChange: (stage) => {
                    if (requestId !== resolutionRequestRef.current) return;
                    trace.markStage(stage);
                },
            });

            if (requestId !== resolutionRequestRef.current) return;

            const descriptionTags = parsed.items.length > 0
                ? parsed.items.map(item => makeDescriptionTag(item))
                : [makeDescriptionTag(null, trimmedDescription)];

            setTagItems([...descriptionTags, ...preserved]);
            trace.markStage('matching');
            for (const tag of descriptionTags) {
                await resolveDescriptionTag(tag, requestId);
                if (requestId !== resolutionRequestRef.current) return;
            }
            if (requestId !== resolutionRequestRef.current) return;

            const descriptionTagKeys = new Set(descriptionTags.map(tag => tag.key));
            const finalDescriptionTags = tagItemsRef.current
                .filter(tag => descriptionTagKeys.has(tag.key))
                .map(toTelemetryTagSnapshot);
            const hasUsableResult = tagItemsRef.current
                .some(tag => descriptionTagKeys.has(tag.key) && Boolean(tag.loggedFood));

            trace.complete({
                parsed,
                tags: finalDescriptionTags,
            });
            analysisTraceRef.current = null;
            setLastAnalyzedDescription(trimmedDescription);
            setDescriptionState(hasUsableResult ? 'ready' : 'failed');
            setShowResultDetails(false);
        } catch (error) {
            console.error('[RegisterFoodDrawer] analyzeDescription failed', error);
            if (requestId !== resolutionRequestRef.current) return;
            trace.fail(error instanceof Error ? error.message : 'No se pudo analizar la descripción.', {
                parsed: null,
                tags: tagItemsRef.current
                    .filter(tag => tag.origin === 'description')
                    .map(toTelemetryTagSnapshot),
            });
            analysisTraceRef.current = null;
            setDescriptionState('failed');
            addToast('No se pudo analizar la descripción.', 'danger');
        }
    }, [addToast, description, lastAnalyzedDescription, resolveDescriptionTag, settings, tagItems]);

    const handleDescriptionChange = useCallback((nextValue: string) => {
        setDescription(nextValue);
        const preserved = tagItems.filter(tag => tag.origin !== 'description');
        if (preserved.length !== tagItems.length) {
            setTagItems(preserved);
            setExpandedTagKey(null);
        }
        if (manualResolveTagKey) {
            setManualResolveTagKey(null);
        }
        if (nextValue.trim() !== lastAnalyzedDescription) {
            setDescriptionState(nextValue.trim() ? 'idle' : 'idle');
            setShowResultDetails(false);
        }
    }, [lastAnalyzedDescription, manualResolveTagKey, tagItems]);

    useEffect(() => {
        if (descriptionState === 'analyzing') return;

        const trimmedDescription = description.trim();
        const descriptionTags = tagItems.filter(tag => tag.origin === 'description');
        const hasLoggedFoods = tagItems.some(tag => Boolean(tag.loggedFood));
        const hasDescriptionResults = descriptionTags.some(tag => Boolean(tag.loggedFood));

        let nextState: DescriptionFlowState = descriptionState;
        if (trimmedDescription && trimmedDescription !== lastAnalyzedDescription) {
            nextState = 'idle';
        } else if (trimmedDescription && descriptionTags.length > 0) {
            nextState = hasDescriptionResults ? 'ready' : 'failed';
        } else if (!trimmedDescription && hasLoggedFoods) {
            nextState = 'ready';
        } else if (!trimmedDescription) {
            nextState = 'idle';
        }

        if (nextState !== descriptionState) {
            setDescriptionState(nextState);
        }
    }, [description, descriptionState, lastAnalyzedDescription, tagItems]);

    const handleCandidateSelect = useCallback((tagKey: string, candidate: SearchFoodCandidate, rememberQuery = true) => {
        const currentTag = tagItems.find(tag => tag.key === tagKey) ?? null;
        if (currentTag?.origin === 'description') {
            void recordNutritionAiManualCorrection({
                originalTag: currentTag.tag,
                selectedFoodName: candidate.food.name,
                fromSource: currentTag.analysisSource ?? null,
                confidence: currentTag.analysisConfidence ?? null,
                rememberQuery,
                reviewRequired: Boolean(currentTag.reviewRequired || currentTag.resolutionStatus === 'needs_review'),
            });
        }

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
                analysisSource: candidate.learned ? 'user-memory' : 'database',
            };
        }));
        setManualResolveTagKey(null);
        addToast('Etiqueta resuelta manualmente.', 'success');
    }, [addToast, buildLoggedFromItem, tagItems]);

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
                foodId: food.id,
                displayName: food.name,
                brand: food.brand,
                food,
                score: 1,
                confidence: 'high',
                canonicalId: food.name,
                source: 'local',
                matchedAlias: food.name,
                why: 'manual_search_resolution',
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

        addToast(`"${food.name}" quedó agregado.`, 'success');
        setTagItems(prev => [
            ...prev,
            createResolvedTag(food, 'manual', {
                resolutionReason: 'Agregado manualmente.',
                analysisSource: 'database',
            }),
        ]);
        setSearchQuery('');
        setSearchResults([]);
        setActiveTab('description');
    }, [addToast, createResolvedTag, handleCandidateSelect, manualResolveTagKey]);

    const handleSave = useCallback(() => {
        if (descriptionState === 'analyzing') {
            addToast('Espera a que termine el análisis.', 'danger');
            return;
        }

        if (description.trim() && description.trim() !== lastAnalyzedDescription) {
            addToast('Pulsa "Analizar calorías" antes de guardar la descripción.', 'danger');
            return;
        }

        const foods = tagItems.filter(tag => tag.loggedFood).map(tag => tag.loggedFood!);
        if (foods.length === 0) {
            addToast('No pudimos estimar esta comida. Prueba con otra descripción o ajústala manualmente.', 'danger');
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
                setDescriptionState('idle');
                setLastAnalyzedDescription('');
                setShowResultDetails(false);
            }, 400);
        }, 1800);
    }, [addToast, description, descriptionState, lastAnalyzedDescription, logDate, mealType, onClose, onSave, tagItems]);

    const { totalMacros, caloriesStdDev } = useMemo(() => {
        const result = tagItems.reduce((acc, tag) => {
            if (!tag.loggedFood) return acc;
            acc.macros.calories += tag.loggedFood.calories;
            acc.macros.protein += tag.loggedFood.protein;
            acc.macros.carbs += tag.loggedFood.carbs;
            acc.macros.fats += tag.loggedFood.fats;

            // Compute varying margins of error depending on truthfulness of the data source
            let marginPct = 0.05; // 5% baseline for known database items
            if (tag.analysisSource === 'local-ai-estimate') marginPct = 0.22; // 22% SD for pure AI mathematical estimates
            else if (tag.analysisSource === 'user-memory') marginPct = 0.10;
            else if (tag.analysisSource === 'local-heuristic') marginPct = 0.08;
            else if (tag.isFuzzyMatch || tag.resolutionConfidence !== 'high') marginPct = 0.15;

            const itemStdDev = tag.loggedFood.calories * marginPct;
            acc.variance += itemStdDev * itemStdDev;

            return acc;
        }, { macros: { calories: 0, protein: 0, carbs: 0, fats: 0 }, variance: 0 });

        return {
            totalMacros: result.macros,
            caloriesStdDev: Math.round(Math.sqrt(result.variance))
        };
    }, [tagItems]);

    const trimmedDescription = description.trim();
    const isAnalyzing = descriptionState === 'analyzing';
    const needsExplicitAnalysis = Boolean(trimmedDescription && trimmedDescription !== lastAnalyzedDescription);
    const hasLoggedFoods = tagItems.some(tag => Boolean(tag.loggedFood));
    const hasEstimatedFoods = tagItems.some(isEstimatedTag);
    const saveMode: SaveMode = !hasLoggedFoods
        ? 'failed'
        : hasEstimatedFoods
            ? 'estimated'
            : 'confirmed';
    const canSave = !isAnalyzing && !needsExplicitAnalysis && hasLoggedFoods;
    const caloriesLabel = hasLoggedFoods ? Math.round(totalMacros.calories).toString() : '--';
    const estimatedPercentage = caloriesStdDev > 0 && totalMacros.calories > 0
        ? Math.round((caloriesStdDev / totalMacros.calories) * 100)
        : 0;
    const caloriesDeviationLabel = hasLoggedFoods && caloriesStdDev > 0
        ? `±${caloriesStdDev} (${estimatedPercentage}%)`
        : '';
    const resultTone = descriptionState === 'failed'
        ? 'No pudimos estimar esta comida'
        : isAnalyzing
            ? 'Analizando...'
            : saveMode === 'estimated'
                ? 'Resultado estimado'
                : hasLoggedFoods
                    ? 'Resultado listo'
                    : 'Escribe tu comida';
    const resultSupport = descriptionState === 'failed'
        ? 'Prueba con otra descripción o ajusta el resultado manualmente.'
        : isAnalyzing
            ? 'Estamos preparando una referencia útil para guardar.'
            : needsExplicitAnalysis
                ? 'Escribe con libertad y analiza solo cuando termines.'
                : hasLoggedFoods
                    ? 'Puedes guardar tal cual o ajustar solo si quieres.'
                    : 'Las calorías son una referencia práctica, no una medición exacta.';
    const macroSummary = hasLoggedFoods
        ? `Prot ${Math.round(totalMacros.protein)} g · Carb ${Math.round(totalMacros.carbs)} g · Grasas ${Math.round(totalMacros.fats)} g`
        : 'Te damos una referencia práctica para registrar sin fricción.';
    const detailCount = tagItems.length;

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
                            <motion.div layout className={`${displayMode === 'appendix' ? 'bg-white/60 backdrop-blur-xl border-white/40' : 'bg-white border-black/[0.03]'} rounded-[32px] p-6 shadow-sm border flex flex-col gap-3 mt-2 relative overflow-hidden`}>
                                <div className="space-y-2">
                                    <p className="text-[10px] font-black uppercase tracking-[0.25em] text-[#49454F]/40">Calorías</p>
                                    <div className="flex items-baseline gap-2">
                                        <div className="text-[48px] font-black text-[#1C1B1F] font-['Roboto'] tracking-tighter leading-none">{caloriesLabel}</div>
                                        {caloriesDeviationLabel && (
                                            <div
                                                className="text-[12px] font-black text-amber-600 bg-amber-50 px-2 py-1 rounded-[8px] font-['Roboto'] tracking-tight flex items-center gap-1"
                                                title={`Índice de desviación estándar: los valores reales de tus alimentos podrían desviar un ${estimatedPercentage}% aprox respecto del total`}
                                            >
                                                <span>{caloriesDeviationLabel}</span>
                                            </div>
                                        )}
                                    </div>
                                    <p className="text-[11px] font-black uppercase tracking-[0.22em] text-[#49454F]/45">{resultTone}</p>
                                    <p className="text-[12px] leading-relaxed text-[#49454F]/70">{resultSupport}</p>
                                </div>
                                <div className="rounded-[24px] bg-black/[0.03] px-4 py-3">
                                    <p className="text-[10px] font-bold uppercase tracking-[0.24em] text-[#49454F]/35">Resumen</p>
                                    <p className="mt-1 text-[12px] font-medium text-[#49454F]/70">{macroSummary}</p>
                                </div>
                                {isAnalyzing && (
                                    <div className="flex items-center gap-2 rounded-[24px] border border-primary/10 bg-primary/5 px-4 py-3 text-[11px] font-bold text-primary/80">
                                        <span className="h-2.5 w-2.5 rounded-full bg-primary animate-pulse" />
                                        Analizando...
                                    </div>
                                )}
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

                            {displayMode === 'appendix' && (
                                <div className="flex justify-end">
                                    <button
                                        onClick={handleSave}
                                        disabled={!canSave}
                                        className={`py-2 px-5 text-[11px] font-black uppercase tracking-[0.3em] rounded-2xl transition-all ${canSave ? 'bg-[#1C1B1F] text-white shadow-lg shadow-black/20' : 'bg-slate-300 text-slate-500 cursor-not-allowed'}`}
                                    >
                                        Guardar
                                    </button>
                                </div>
                            )}

                            {/* Content Area */}
                            <AnimatePresence mode="wait">
                                {activeTab === 'description' && (
                                    <motion.div key="desc" initial={{ opacity: 0, scale: 0.98 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.98 }} className="space-y-4">
                                        <div className="relative group">
                                            <textarea
                                                className={`w-full h-36 rounded-[32px] p-8 text-base font-medium text-[#1C1B1F] border shadow-sm outline-none transition-all placeholder-[#49454F]/30 resize-none font-['Roboto'] leading-relaxed ${displayMode === 'appendix' ? 'bg-white/60 backdrop-blur-xl border-white/40 focus:border-white/60' : 'bg-white border-black/[0.03] focus:border-primary/30'}`}
                                                placeholder="Describe tu comida... ej: 200g arroz, 150g pollo"
                                                value={description}
                                                onChange={event => handleDescriptionChange(event.target.value)}
                                            />
                                            <div className="absolute top-6 right-8 flex gap-2">
                                                <div className={`w-2 h-2 rounded-full ${isAnalyzing ? 'bg-primary animate-pulse' : 'bg-primary/20'}`} />
                                            </div>
                                        </div>
                                        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                            <p className="text-[11px] font-bold text-[#49454F]/60 leading-relaxed">
                                                {isAnalyzing
                                                    ? 'Estamos preparando tu referencia...'
                                                    : needsExplicitAnalysis
                                                        ? 'Nada corre mientras escribes. Analiza solo cuando termines.'
                                                        : descriptionState === 'failed'
                                                            ? 'No pudimos estimar esta comida todavía.'
                                                            : hasLoggedFoods
                                                                ? 'Puedes guardar ahora o ajustar solo si quieres.'
                                                                : 'Escribe una comida para empezar.'}
                                            </p>
                                            <div className="flex items-center gap-2 shrink-0">
                                                <button
                                                    type="button"
                                                    onClick={analyzeDescription}
                                                    disabled={isAnalyzing || !trimmedDescription}
                                                    className={`rounded-[22px] px-5 py-3 text-[10px] font-black uppercase tracking-[0.28em] transition-all ${isAnalyzing || !trimmedDescription ? 'bg-slate-300 text-slate-500 cursor-not-allowed' : 'bg-primary text-[#1C1B1F] shadow-lg shadow-primary/20'}`}
                                                >
                                                    {isAnalyzing ? 'Analizando...' : lastAnalyzedDescription && trimmedDescription === lastAnalyzedDescription ? 'Reanalizar calorías' : 'Analizar calorías'}
                                                </button>
                                                {isAnalyzing && (
                                                    <button
                                                        type="button"
                                                        onClick={cancelAnalysis}
                                                        className="rounded-[20px] border border-black/[0.08] bg-white px-4 py-3 text-[10px] font-black uppercase tracking-[0.25em] text-[#49454F] shadow-sm"
                                                    >
                                                        Cancelar
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                        <div className="flex flex-wrap gap-2">
                                            {detailCount > 0 && (
                                                <button
                                                    type="button"
                                                    onClick={() => setShowResultDetails(prev => !prev)}
                                                    className="rounded-[20px] border border-black/[0.06] bg-white px-4 py-2.5 text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F] shadow-sm"
                                                >
                                                    {showResultDetails ? 'Ocultar detalle' : 'Ver detalle'}
                                                </button>
                                            )}
                                            {(detailCount > 0 || descriptionState === 'failed') && (
                                                <button
                                                    type="button"
                                                    onClick={() => setActiveTab('search')}
                                                    className="rounded-[20px] border border-black/[0.06] bg-white px-4 py-2.5 text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F] shadow-sm"
                                                >
                                                    Ajustar resultado
                                                </button>
                                            )}
                                            {detailCount === 0 && (
                                                <button
                                                    type="button"
                                                    onClick={() => setActiveTab('templates')}
                                                    className="rounded-[20px] border border-black/[0.06] bg-white px-4 py-2.5 text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F] shadow-sm"
                                                >
                                                    Usar plantilla
                                                </button>
                                            )}
                                        </div>
                                    </motion.div>
                                )}

                                {activeTab === 'search' && (
                                    <motion.div key="search" initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} className="space-y-4">
                                        <div className="flex items-center justify-between gap-3">
                                            <div>
                                                <p className="text-[10px] font-black uppercase tracking-[0.22em] text-[#49454F]/35">Ajustar resultado</p>
                                                <p className="text-[12px] text-[#49454F]/65">Busca un alimento solo si quieres afinar la referencia.</p>
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => setActiveTab('description')}
                                                className="rounded-[18px] border border-black/[0.06] bg-white px-4 py-2 text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F] shadow-sm"
                                            >
                                                Volver
                                            </button>
                                        </div>
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
                                    <motion.div key="templates" initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-4">
                                        <div className="flex items-center justify-between gap-3">
                                            <div>
                                                <p className="text-[10px] font-black uppercase tracking-[0.22em] text-[#49454F]/35">Plantillas</p>
                                                <p className="text-[12px] text-[#49454F]/65">Usa una base rápida y vuelve al registro en un toque.</p>
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => setActiveTab('description')}
                                                className="rounded-[18px] border border-black/[0.06] bg-white px-4 py-2 text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F] shadow-sm"
                                            >
                                                Volver
                                            </button>
                                        </div>
                                        <div className="rounded-[32px] overflow-hidden bg-white border border-black/[0.03] p-6 shadow-sm">
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
                                                    setShowResultDetails(false);
                                                    setActiveTab('description');
                                                }}
                                                onClose={() => { /* noop */ }}
                                            />
                                        </div>
                                    </motion.div>
                                )}
                            </AnimatePresence>

                            {/* Food List Section */}
                            {showResultDetails && tagItems.length > 0 && activeTab === 'description' && (
                                <div className="space-y-4">
                                    <div className="flex items-center justify-between px-1">
                                        <h4 className="text-[10px] font-black text-[#49454F]/30 uppercase tracking-[0.2em]">Tu comida</h4>
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
                                                                    <StatusBadge status={tag.resolutionStatus} confidence={tag.resolutionConfidence} source={tag.analysisSource} />
                                                                </div>
                                                                <p className="text-[11px] font-bold text-[#49454F]/40 tracking-tight">
                                                                    {tag.loggedFood
                                                                        ? `${Math.round(tag.loggedFood.calories)} kcal · ${tag.loggedFood.amount}${tag.loggedFood.unit}`
                                                                        : tag.resolutionStatus === 'pending'
                                                                            ? 'Buscando resultado...'
                                                                            : tag.resolutionReason || 'Sin resultado todavía'}
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
                                                                    {tag.analysisSource === 'local-ai-estimate' ? (
                                                                        <div className="rounded-[16px] bg-amber-50 border border-amber-200/50 px-4 py-3 text-[11px] font-medium text-amber-800 flex items-start gap-2">
                                                                            <AlertTriangleIcon size={14} className="mt-0.5 shrink-0 text-amber-500" />
                                                                            <p className="leading-snug">
                                                                                <strong className="block text-amber-900 mb-0.5 font-bold">Estimación generada por IA</strong>
                                                                                Los macronutrientes y calorías de este alimento fueron calculados matemáticamente por IA y podrían no ser exactos. Revisa y ajusta si notas diferencias.
                                                                            </p>
                                                                        </div>
                                                                    ) : (tag.analysisSource && tag.analysisSource !== 'database') || tag.subItems?.length ? (
                                                                        <div className="rounded-[16px] bg-black/[0.02] px-4 py-3 text-[11px] font-medium text-[#49454F]/60">
                                                                            {detailHintLabel(tag)}
                                                                        </div>
                                                                    ) : null}

                                                                    {tag.subItems?.length ? (
                                                                        <div className="rounded-[20px] bg-black/[0.02] px-4 py-3 text-[11px] font-medium text-[#49454F]/60">
                                                                            Ingredientes: {tag.subItems.map(item => item.tag).join(', ')}
                                                                        </div>
                                                                    ) : null}

                                                                    {tag.candidateFoods.length > 0 && (
                                                                        <div className="space-y-3">
                                                                            <p className="text-[9px] font-black text-[#49454F]/20 uppercase tracking-[0.2em] px-1">Opciones parecidas</p>
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
                                                                                            <div className="text-[9px] font-black uppercase tracking-widest text-[#1C1B1F]/20 group-hover:text-primary transition-colors">Usar</div>
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
                                                                            Ajustar manualmente
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
                                        disabled={!canSave}
                                        className={`flex-[2] py-5 rounded-[32px] text-[11px] font-black uppercase tracking-[0.2em] active:scale-95 transition-all flex items-center justify-center gap-3 font-['Roboto'] ${canSave ? 'bg-[#1C1B1F] text-white shadow-2xl' : 'bg-slate-300 text-slate-500 cursor-not-allowed shadow-none'}`}
                                    >
                                        Guardar
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

const StatusBadge: React.FC<{ status: TagResolutionStatus; confidence: SearchConfidence; source?: TagAnalysisSource }> = ({ status, source }) => {
    if (status === 'resolved' && (source === 'local-ai-estimate' || source === 'local-heuristic')) {
        return <span className="inline-flex items-center rounded-full bg-amber-100 px-2.5 py-1 text-[8px] font-black uppercase tracking-wider text-amber-700">Estimado</span>;
    }

    if (status === 'resolved') {
        return <span className="inline-flex items-center rounded-full bg-primary/10 px-2.5 py-1 text-[8px] font-black uppercase tracking-wider text-primary">Listo</span>;
    }

    if (status === 'pending') {
        return <span className="inline-flex items-center rounded-full bg-black/5 px-2.5 py-1 text-[8px] font-black uppercase tracking-wider text-black/20 animate-pulse">Analizando</span>;
    }

    return (
        <span className="inline-flex items-center rounded-full bg-rose-100 px-2.5 py-1 text-[8px] font-black uppercase tracking-wider text-rose-500">
            Ajustar
        </span>
    );
};
