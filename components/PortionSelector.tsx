// components/PortionSelector.tsx
// Selector de porción: gramos, preset, referencias visuales (onzas en Avanzado)

import React, { useState } from 'react';
import type { FoodItem, PortionPreset, PortionInput, PortionUnit, PortionReference } from '../types';
import { PORTION_MULTIPLIERS } from '../types';
import { PORTION_REFERENCES, OZ_TO_GRAMS, getGramsForReference, getFoodTypeForPortion } from '../data/portionReferences';

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

type MeasureStep = 'quantity' | 'size';
type SizeStep = 'preset' | 'reference';

export const PortionSelector: React.FC<PortionSelectorProps> = ({ food, onConfirm, onCancel }) => {
    const [measureStep, setMeasureStep] = useState<MeasureStep>('quantity');
    const [sizeStep, setSizeStep] = useState<SizeStep>('preset');
    const [gramsValue, setGramsValue] = useState(String(food.servingSize));
    const [ozValue, setOzValue] = useState(String(Math.round((food.servingSize / OZ_TO_GRAMS) * 10) / 10));
    const [useOz, setUseOz] = useState(false);
    const [preset, setPreset] = useState<PortionPreset>('medium');
    const [ref, setRef] = useState<PortionReference>('palm');

    const mode: PortionUnit = measureStep === 'quantity' ? (useOz ? 'oz' : 'g') : (sizeStep === 'preset' ? 'preset' : 'reference');

    const getAmountGrams = (): number => {
        if (measureStep === 'quantity') {
            if (useOz) return (parseFloat(ozValue) || 0) * OZ_TO_GRAMS;
            return parseFloat(gramsValue) || food.servingSize;
        }
        if (sizeStep === 'preset') return food.servingSize * PORTION_MULTIPLIERS[preset];
        const ft = getFoodTypeForPortion(food);
        return getGramsForReference(ref, ft);
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
            <div>
                <p className="text-xs text-slate-400 font-medium">{food.name}</p>
                {food.cookingBehavior === 'shrinks' && (
                    <p className="text-[10px] text-zinc-500 mt-0.5">Si pesas cocido, el sistema ajustará los macros automáticamente.</p>
                )}
                {food.cookingBehavior === 'expands' && (
                    <p className="text-[10px] text-zinc-500 mt-0.5">Si pesas hidratado/cocido, el sistema convertirá a peso seco.</p>
                )}
            </div>

            {/* Paso 1: ¿Cómo quieres medir? */}
            <div>
                <label className="block text-[9px] font-bold text-zinc-500 uppercase tracking-widest mb-2">¿Cómo quieres medir?</label>
                <div className="flex gap-2">
                    <button
                        onClick={() => setMeasureStep('quantity')}
                        className={`flex-1 py-2.5 rounded-lg text-xs font-bold transition-all ${measureStep === 'quantity' ? 'bg-white text-black' : 'bg-white/5 text-zinc-500 hover:text-white'}`}
                    >
                        Por cantidad (gramos)
                    </button>
                    <button
                        onClick={() => setMeasureStep('size')}
                        className={`flex-1 py-2.5 rounded-lg text-xs font-bold transition-all ${measureStep === 'size' ? 'bg-white text-black' : 'bg-white/5 text-zinc-500 hover:text-white'}`}
                    >
                        Por tamaño/referencia
                    </button>
                </div>
            </div>

            {/* Paso 2a: Cantidad (gramos) */}
            {measureStep === 'quantity' && (
                <div className="space-y-2">
                    <div>
                        <label className="block text-[9px] font-bold text-zinc-500 uppercase tracking-widest mb-1">Gramos</label>
                        <input
                            type="number"
                            value={gramsValue}
                            onChange={e => { setGramsValue(e.target.value); setUseOz(false); }}
                            onFocus={() => setUseOz(false)}
                            min={1}
                            step={5}
                            className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white"
                        />
                    </div>
                    <details className="group">
                        <summary className="text-[10px] font-bold text-zinc-500 uppercase cursor-pointer hover:text-zinc-300 list-none">
                            Avanzado (onzas)
                        </summary>
                        <div className="mt-2">
                            <label className="block text-[9px] font-bold text-zinc-500 uppercase mb-1">Onzas</label>
                            <input
                                type="number"
                                value={ozValue}
                                onChange={e => { setOzValue(e.target.value); setUseOz(true); }}
                                onFocus={() => setUseOz(true)}
                                min={0.1}
                                step={0.5}
                                className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white"
                            />
                        </div>
                    </details>
                </div>
            )}

            {/* Paso 2b: Tamaño (preset o referencia) */}
            {measureStep === 'size' && (
                <div className="space-y-3">
                    <div className="flex gap-1 p-1 bg-white/5 rounded-lg">
                        <button
                            onClick={() => setSizeStep('preset')}
                            className={`flex-1 py-1.5 rounded text-[10px] font-bold uppercase transition-all ${sizeStep === 'preset' ? 'bg-white text-black' : 'text-zinc-500 hover:text-white'}`}
                        >
                            Tamaño
                        </button>
                        <button
                            onClick={() => setSizeStep('reference')}
                            className={`flex-1 py-1.5 rounded text-[10px] font-bold uppercase transition-all ${sizeStep === 'reference' ? 'bg-white text-black' : 'text-zinc-500 hover:text-white'}`}
                        >
                            Referencia
                        </button>
                    </div>

                    {sizeStep === 'preset' && (
                        <div className="flex gap-1 flex-wrap">
                            {(['small', 'medium', 'large', 'extra'] as PortionPreset[]).map(p => {
                                const estGrams = Math.round(food.servingSize * PORTION_MULTIPLIERS[p]);
                                return (
                                    <button
                                        key={p}
                                        onClick={() => setPreset(p)}
                                        className={`px-3 py-2 rounded-lg text-xs font-bold transition-all flex flex-col items-center ${preset === p ? 'bg-white text-black' : 'bg-white/5 text-zinc-500 hover:text-white'}`}
                                    >
                                        <span>{portionLabels[p]}</span>
                                        <span className="text-[9px] font-normal opacity-80">(~{estGrams}g)</span>
                                    </button>
                                );
                            })}
                        </div>
                    )}

                    {sizeStep === 'reference' && (
                        <div className="space-y-1">
                            <label className="block text-[9px] font-bold text-zinc-500 uppercase tracking-widest">Referencia visual</label>
                            <div className="flex flex-col gap-1">
                                {PORTION_REFERENCES.map(entry => {
                                    const ft = getFoodTypeForPortion(food);
                                    const estGrams = getGramsForReference(entry.key, ft);
                                    return (
                                        <button
                                            key={entry.key}
                                            onClick={() => setRef(entry.key)}
                                            title={entry.description}
                                            className={`text-left px-3 py-2 rounded-lg text-sm transition-all ${ref === entry.key ? 'bg-white text-black' : 'bg-white/5 text-zinc-400 hover:text-white'}`}
                                        >
                                            <span className="block">{entry.label}</span>
                                            <span className="text-[10px] text-zinc-500 font-mono">~{estGrams}g</span>
                                            {entry.description && (
                                                <span className="block text-[9px] text-zinc-500 mt-0.5">{entry.description}</span>
                                            )}
                                        </button>
                                    );
                                })}
                            </div>
                        </div>
                    )}
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
