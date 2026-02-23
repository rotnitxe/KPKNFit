// components/FoodSearchModal.tsx
// Buscador de alimentos tipo ExerciseRow: input + lista + selector de porción

import React, { useState, useEffect, useCallback } from 'react';
import type { FoodItem, PortionInput, LoggedFood, Settings } from '../types';
import { searchFoods } from '../services/foodSearchService';
import { useAppState } from '../contexts/AppContext';
import { TacticalModal } from './ui/TacticalOverlays';
import { SearchIcon, UtensilsIcon } from './icons';
import { PortionSelector } from './PortionSelector';

interface FoodSearchModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSelect: (loggedFood: LoggedFood) => void;
    /** Si se provee, se llama con food+logged para casos que necesitan el FoodItem (ej. editar porción) */
    onSelectWithFood?: (food: FoodItem, loggedFood: LoggedFood) => void;
}

function foodToLoggedFood(food: FoodItem, amountGrams: number): LoggedFood {
    const ratio = amountGrams / food.servingSize;
    return {
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
}

export const FoodSearchModal: React.FC<FoodSearchModalProps> = ({ isOpen, onClose, onSelect, onSelectWithFood }) => {
    const { settings } = useAppState();
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<FoodItem[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [selectedFood, setSelectedFood] = useState<FoodItem | null>(null);

    const doSearch = useCallback(async (q: string) => {
        if (!q.trim()) {
            setResults([]);
            return;
        }
        setIsLoading(true);
        try {
            const items = await searchFoods(q, settings);
            setResults(items);
        } finally {
            setIsLoading(false);
        }
    }, [settings]);

    useEffect(() => {
        const t = setTimeout(() => doSearch(query), 300);
        return () => clearTimeout(t);
    }, [query, doSearch]);

    const handleSelectFood = (food: FoodItem) => {
        setSelectedFood(food);
    };

    const handlePortionConfirm = (portion: PortionInput, amountGrams: number) => {
        if (!selectedFood) return;
        const logged = foodToLoggedFood(selectedFood, amountGrams);
        logged.portionInput = portion;
        if (onSelectWithFood) {
            onSelectWithFood(selectedFood, logged);
        } else {
            onSelect(logged);
        }
        setSelectedFood(null);
        setQuery('');
        onClose();
    };

    const handlePortionCancel = () => {
        setSelectedFood(null);
    };

    return (
        <TacticalModal isOpen={isOpen} onClose={onClose} title="Buscar alimento">
            <div className="space-y-4 max-h-[70vh] flex flex-col">
                {!selectedFood ? (
                    <>
                        <div className="relative">
                            <SearchIcon size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
                            <input
                                type="text"
                                value={query}
                                onChange={e => setQuery(e.target.value)}
                                placeholder="Ej: pechuga de pollo, arroz..."
                                autoFocus
                                className="w-full bg-white/5 border border-white/10 rounded-xl pl-10 pr-4 py-3 text-sm text-white placeholder-zinc-600 focus:border-white/30 outline-none"
                            />
                        </div>

                        {isLoading && <p className="text-xs text-zinc-500">Buscando...</p>}

                        <div className="flex-grow overflow-y-auto max-h-64 custom-scrollbar space-y-0.5">
                            {results.map(food => (
                                <button
                                    key={food.id}
                                    onClick={() => handleSelectFood(food)}
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
                            {!isLoading && query && results.length === 0 && (
                                <p className="text-center text-sm text-zinc-500 py-6">No se encontraron resultados</p>
                            )}
                        </div>
                    </>
                ) : (
                    <PortionSelector
                        food={selectedFood}
                        onConfirm={handlePortionConfirm}
                        onCancel={handlePortionCancel}
                    />
                )}
            </div>
        </TacticalModal>
    );
};
