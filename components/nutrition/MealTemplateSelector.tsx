// components/nutrition/MealTemplateSelector.tsx
// Selector de plantillas de comidas reutilizables

import React from 'react';
import type { MealTemplate, LoggedFood } from '../../types';
import { useMealTemplateStore } from '../../stores/mealTemplateStore';
import { UtensilsIcon } from '../icons';

interface MealTemplateSelectorProps {
    onSelect: (foods: LoggedFood[]) => void;
    onClose?: () => void;
}

export const MealTemplateSelector: React.FC<MealTemplateSelectorProps> = ({ onSelect, onClose }) => {
    const { mealTemplates } = useMealTemplateStore();

    if (mealTemplates.length === 0) {
        return (
            <div className="p-4 text-center">
                <UtensilsIcon size={32} className="mx-auto text-zinc-600 mb-2" />
                <p className="text-sm text-zinc-500">No hay plantillas guardadas.</p>
                <p className="text-xs text-zinc-600 mt-1">Registra una comida y guárdala como plantilla.</p>
            </div>
        );
    }

    return (
        <div className="space-y-2 max-h-64 overflow-y-auto custom-scrollbar">
            {mealTemplates.map((t) => (
                <button
                    key={t.id}
                    onClick={() => {
                        const foodsWithNewIds = t.foods.map(f => ({
                            ...f,
                            id: crypto.randomUUID(),
                        }));
                        onSelect(foodsWithNewIds);
                        onClose?.();
                    }}
                    className="w-full text-left p-3 rounded-xl bg-white/5 border border-white/10 hover:bg-white/10 hover:border-white/20 transition-all"
                >
                    <div className="flex justify-between items-start">
                        <span className="font-bold text-white text-sm">{t.name}</span>
                        <span className="text-[10px] font-mono text-zinc-400">{t.totalCalories} kcal</span>
                    </div>
                    <p className="text-[10px] text-zinc-500 mt-0.5 truncate">{t.description}</p>
                    <p className="text-[10px] text-zinc-600 mt-1">P{t.totalProtein.toFixed(0)} C{t.totalCarbs.toFixed(0)} G{t.totalFats.toFixed(0)}</p>
                </button>
            ))}
        </div>
    );
};
