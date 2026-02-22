// components/PortionSelector.tsx
// Selector de porción: gramos, onzas, preset, referencias visuales

import React, { useState } from 'react';
import type { FoodItem, PortionPreset, PortionInput, PortionUnit, PortionReference } from '../types';
import { PORTION_MULTIPLIERS } from '../types';
import { PORTION_REFERENCES, OZ_TO_GRAMS, getGramsForReference, getFoodTypeFromMacros } from '../data/portionReferences';

const portionLabels: Record<PortionPreset, string> = {
    small: 'Pequeño',
    medium: 'Mediano',
    large: 'Grande',
    extra: 'Extra',
};

interface PortionSelectorProps {
    food: FoodItem;
    onConfirm: (portion: PortionInput, amountGrams: number) => void;
    onCancel?: () => void;
}

export const PortionSelector: React.FC<PortionSelectorProps> = ({ food, onConfirm, onCancel }) => {
    const [mode, setMode] = useState<PortionUnit>('g');
    const [gramsValue, setGramsValue] = useState(String(food.servingSize));
    const [ozValue, setOzValue] = useState(String(Math.round((food.servingSize / OZ_TO_GRAMS) * 10) / 10));
    const [preset, setPreset] = useState<PortionPreset>('medium');
    const [ref, setRef] = useState<PortionReference>('palm');

    const getAmountGrams = (): number => {
        if (mode === 'g') return parseFloat(gramsValue) || food.servingSize;
        if (mode === 'oz') return (parseFloat(ozValue) || 0) * OZ_TO_GRAMS;
        if (mode === 'preset') return food.servingSize * PORTION_MULTIPLIERS[preset];
        if (mode === 'reference') {
            const ft = getFoodTypeFromMacros(food.protein, food.carbs, food.fats);
            return getGramsForReference(ref, ft);
        }
        return food.servingSize;
    };

    const handleConfirm = () => {
        const amountGrams = getAmountGrams();
        const portion: PortionInput = {
            type: mode,
            value: mode === 'g' ? amountGrams : mode === 'oz' ? amountGrams / OZ_TO_GRAMS : mode === 'preset' ? PORTION_MULTIPLIERS[preset] : 1,
            reference: mode === 'reference' ? ref : undefined,
        };
        onConfirm(portion, amountGrams);
    };

    return (
        <div className="space-y-4">
            <p className="text-xs text-slate-400 font-medium">{food.name}</p>
            <div className="flex gap-1 p-1 bg-white/5 rounded-lg">
                {(['g', 'oz', 'preset', 'reference'] as PortionUnit[]).map(m => (
                    <button
                        key={m}
                        onClick={() => setMode(m)}
                        className={`flex-1 py-1.5 rounded text-[10px] font-bold uppercase transition-all ${mode === m ? 'bg-white text-black' : 'text-slate-500 hover:text-white'}`}
                    >
                        {m === 'g' ? 'Gramos' : m === 'oz' ? 'Onzas' : m === 'preset' ? 'Tamaño' : 'Referencia'}
                    </button>
                ))}
            </div>

            {mode === 'g' && (
                <div>
                    <label className="block text-[9px] font-bold text-zinc-500 uppercase tracking-widest mb-1">Gramos</label>
                    <input
                        type="number"
                        value={gramsValue}
                        onChange={e => setGramsValue(e.target.value)}
                        min={1}
                        step={5}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white"
                    />
                </div>
            )}

            {mode === 'oz' && (
                <div>
                    <label className="block text-[9px] font-bold text-zinc-500 uppercase tracking-widest mb-1">Onzas</label>
                    <input
                        type="number"
                        value={ozValue}
                        onChange={e => setOzValue(e.target.value)}
                        min={0.1}
                        step={0.5}
                        className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white"
                    />
                </div>
            )}

            {mode === 'preset' && (
                <div className="flex gap-1 flex-wrap">
                    {(['small', 'medium', 'large', 'extra'] as PortionPreset[]).map(p => (
                        <button
                            key={p}
                            onClick={() => setPreset(p)}
                            className={`px-3 py-2 rounded-lg text-xs font-bold transition-all ${preset === p ? 'bg-white text-black' : 'bg-white/5 text-zinc-500 hover:text-white'}`}
                        >
                            {portionLabels[p]}
                        </button>
                    ))}
                </div>
            )}

            {mode === 'reference' && (
                <div className="space-y-2">
                    <label className="block text-[9px] font-bold text-zinc-500 uppercase tracking-widest">Referencia visual</label>
                    <div className="flex flex-col gap-1">
                        {PORTION_REFERENCES.map(entry => (
                            <button
                                key={entry.key}
                                onClick={() => setRef(entry.key)}
                                className={`text-left px-3 py-2 rounded-lg text-sm transition-all ${ref === entry.key ? 'bg-white text-black' : 'bg-white/5 text-zinc-400 hover:text-white'}`}
                            >
                                {entry.label}
                            </button>
                        ))}
                    </div>
                </div>
            )}

            <div className="flex gap-2 pt-2">
                {onCancel && (
                    <button onClick={onCancel} className="flex-1 py-2 rounded-lg text-sm font-bold text-zinc-400 hover:text-white bg-white/5">
                        Cancelar
                    </button>
                )}
                <button
                    onClick={handleConfirm}
                    className="flex-1 py-2 rounded-lg text-sm font-bold bg-white text-black hover:bg-zinc-200"
                >
                    Añadir ({Math.round(getAmountGrams())}g)
                </button>
            </div>
        </div>
    );
};
