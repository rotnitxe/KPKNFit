// components/nutrition/CalorieGoalCard.tsx
// Card de objetivo calórico con modal para editar fórmula y parámetros

import React, { useState } from 'react';
import type { CalorieGoalConfig, Settings } from '../../types';
import { calculateDailyCalorieGoal } from '../../utils/calorieFormulas';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import Modal from '../ui/Modal';
import Button from '../ui/Button';

const FORMULA_OPTIONS: { id: CalorieGoalConfig['formula']; label: string }[] = [
    { id: 'mifflin', label: 'Mifflin-St Jeor' },
    { id: 'harris', label: 'Harris-Benedict' },
    { id: 'katch', label: 'Katch-McArdle' },
];

const ACTIVITY_OPTIONS: { id: number; label: string }[] = [
    { id: 1, label: 'Sedentario' },
    { id: 2, label: 'Ligero' },
    { id: 3, label: 'Moderado' },
    { id: 4, label: 'Activo' },
    { id: 5, label: 'Muy activo' },
];

const GOAL_OPTIONS: { id: CalorieGoalConfig['goal']; label: string }[] = [
    { id: 'lose', label: 'Perder peso' },
    { id: 'maintain', label: 'Mantener' },
    { id: 'gain', label: 'Ganar peso' },
];

export const CalorieGoalCard: React.FC<{
    calorieGoal: number;
    onEditClick: () => void;
}> = ({ calorieGoal, onEditClick }) => {
    return (
        <div className="bg-[#0a0a0a] border border-white/10 rounded-2xl p-4 flex justify-between items-center">
            <div>
                <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest">Objetivo</p>
                <p className="text-lg font-black text-white font-mono">{calorieGoal} kcal</p>
            </div>
            <button
                onClick={onEditClick}
                className="text-[9px] font-bold text-zinc-500 uppercase hover:text-white transition-colors"
            >
                Editar plan
            </button>
        </div>
    );
};

interface CalorieGoalModalProps {
    isOpen: boolean;
    onClose: () => void;
}

export const CalorieGoalModal: React.FC<CalorieGoalModalProps> = ({ isOpen, onClose }) => {
    const { settings } = useAppState();
    const { setSettings, addToast } = useAppDispatch();
    const config = settings.calorieGoalConfig || ({} as Partial<CalorieGoalConfig>);

    const [formula, setFormula] = useState<CalorieGoalConfig['formula']>(config.formula || 'mifflin');
    const [activityLevel, setActivityLevel] = useState(config.activityLevel ?? 3);
    const [goal, setGoal] = useState<CalorieGoalConfig['goal']>(config.goal || 'maintain');
    const [weeklyChangeKg, setWeeklyChangeKg] = useState(config.weeklyChangeKg ?? 0.5);
    const [healthMultiplier, setHealthMultiplier] = useState(
        config.healthMultiplier ?? 1
    );

    const previewConfig: CalorieGoalConfig = {
        formula,
        activityLevel,
        goal,
        weeklyChangeKg,
        healthMultiplier,
    };
    const previewCalories = calculateDailyCalorieGoal(settings, previewConfig);

    const handleSave = () => {
        setSettings({
            calorieGoalConfig: {
                formula,
                activityLevel,
                goal,
                weeklyChangeKg,
                healthMultiplier,
            },
        });
        addToast('Objetivo calórico actualizado.', 'success');
        onClose();
    };

    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Objetivo Calórico">
            <div className="space-y-5">
                <div>
                    <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                        Fórmula TMB
                    </label>
                    <div className="flex flex-wrap gap-2">
                        {FORMULA_OPTIONS.map(opt => (
                            <button
                                key={opt.id}
                                onClick={() => setFormula(opt.id)}
                                className={`px-3 py-2 rounded-xl text-xs font-bold transition-all border ${
                                    formula === opt.id
                                        ? 'bg-white text-black border-white'
                                        : 'bg-white/5 border-white/10 text-zinc-400 hover:text-white'
                                }`}
                            >
                                {opt.label}
                            </button>
                        ))}
                    </div>
                </div>

                <div>
                    <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                        Nivel de actividad
                    </label>
                    <div className="flex flex-wrap gap-2">
                        {ACTIVITY_OPTIONS.map(opt => (
                            <button
                                key={opt.id}
                                onClick={() => setActivityLevel(opt.id)}
                                className={`px-3 py-2 rounded-xl text-xs font-bold transition-all border ${
                                    activityLevel === opt.id
                                        ? 'bg-white text-black border-white'
                                        : 'bg-white/5 border-white/10 text-zinc-400 hover:text-white'
                                }`}
                            >
                                {opt.label}
                            </button>
                        ))}
                    </div>
                </div>

                <div>
                    <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                        Objetivo
                    </label>
                    <div className="flex flex-wrap gap-2">
                        {GOAL_OPTIONS.map(opt => (
                            <button
                                key={opt.id}
                                onClick={() => setGoal(opt.id)}
                                className={`px-3 py-2 rounded-xl text-xs font-bold transition-all border ${
                                    goal === opt.id
                                        ? 'bg-white text-black border-white'
                                        : 'bg-white/5 border-white/10 text-zinc-400 hover:text-white'
                                }`}
                            >
                                {opt.label}
                            </button>
                        ))}
                    </div>
                </div>

                {(goal === 'lose' || goal === 'gain') && (
                    <div>
                        <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                            Cambio semanal (kg)
                        </label>
                        <select
                            value={weeklyChangeKg}
                            onChange={e => setWeeklyChangeKg(Number(e.target.value))}
                            className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white outline-none focus:border-white/30"
                        >
                            <option value={0.25}>0.25 kg/sem</option>
                            <option value={0.5}>0.5 kg/sem</option>
                            <option value={0.75}>0.75 kg/sem</option>
                            <option value={1}>1 kg/sem</option>
                            <option value={1.5}>1.5 kg/sem</option>
                            <option value={2}>2 kg/sem</option>
                        </select>
                    </div>
                )}

                <div>
                    <label className="block text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">
                        Multiplicador salud (ej. 0.9 = -10%)
                    </label>
                    <input
                        type="number"
                        min={0.5}
                        max={1.5}
                        step={0.05}
                        value={healthMultiplier}
                        onChange={e => setHealthMultiplier(Number(e.target.value) || 1)}
                        className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white font-mono outline-none focus:border-white/30"
                    />
                </div>

                <div className="bg-white/5 rounded-xl p-4 border border-white/5">
                    <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-1">
                        Vista previa
                    </p>
                    <p className="text-2xl font-black text-white font-mono">{previewCalories} kcal/día</p>
                </div>

                <div className="flex gap-3 pt-2">
                    <Button onClick={onClose} variant="secondary" className="flex-1">
                        Cancelar
                    </Button>
                    <Button onClick={handleSave} className="flex-1">
                        Guardar
                    </Button>
                </div>
            </div>
        </Modal>
    );
};
