
// components/ProgressGoals.tsx
import React, { useState, useEffect, useMemo } from 'react';
import { Settings } from '../types';
import Card from './ui/Card';
import Button from './ui/Button';
import { SaveIcon, TargetIcon } from './icons';

interface ProgressGoalsProps {
    settings: Settings;
    onSettingsChange: (newSettings: Partial<Settings>) => void;
}

const ProgressGoals: React.FC<ProgressGoalsProps> = ({ settings, onSettingsChange }) => {
    const [localSettings, setLocalSettings] = useState(settings);
    
    const initialManualGoals = {
        calories: settings.dailyCalorieGoal || 0,
        protein: settings.dailyProteinGoal || 0,
        carbs: settings.dailyCarbGoal || 0,
        fats: settings.dailyFatGoal || 0,
    };
    const [manualGoals, setManualGoals] = useState(initialManualGoals);

    useEffect(() => {
        setLocalSettings(settings);
    }, [settings]);

    const calculatedGoals = useMemo(() => {
        const { age, weight, height, gender, activityLevel } = localSettings.userVitals;
        if (!age || !weight || !height || !gender || !activityLevel) {
            return null;
        }

        let bmr: number;
        if (gender === 'male') {
            bmr = (10 * weight) + (6.25 * height) - (5 * age) + 5;
        } else {
            bmr = (10 * weight) + (6.25 * height) - (5 * age) - 161;
        }

        const activityMultipliers = {
            sedentary: 1.2, light: 1.375, moderate: 1.55, active: 1.725, very_active: 1.9,
        };
        const tdee = bmr * activityMultipliers[activityLevel];

        let calorieGoal: number;
        switch (localSettings.calorieGoalObjective) {
            case 'deficit': calorieGoal = tdee - 500; break;
            case 'surplus': calorieGoal = tdee + 300; break;
            default: calorieGoal = tdee; break;
        }
        
        const proteinGrams = Math.round((calorieGoal * 0.40) / 4);
        const carbGrams = Math.round((calorieGoal * 0.40) / 4);
        const fatGrams = Math.round((calorieGoal * 0.20) / 9);

        return {
            bmr: Math.round(bmr), tdee: Math.round(tdee), calorieGoal: Math.round(calorieGoal),
            proteinGoal: proteinGrams, carbGoal: carbGrams, fatGoal: fatGrams,
        };
    }, [localSettings.userVitals, localSettings.calorieGoalObjective]);
    
    useEffect(() => {
        if (calculatedGoals) {
            setManualGoals({
                calories: calculatedGoals.calorieGoal, protein: calculatedGoals.proteinGoal,
                carbs: calculatedGoals.carbGoal, fats: calculatedGoals.fatGoal,
            });
        }
    }, [calculatedGoals]);

    const handleVitalsChange = <K extends keyof Settings['userVitals']>(key: K, value: Settings['userVitals'][K]) => {
        setLocalSettings(prev => ({ ...prev, userVitals: { ...prev.userVitals, [key]: value } }));
    };

    const handleMacroChange = (macro: 'protein' | 'carbs' | 'fats', value: string) => {
        const numValue = parseInt(value, 10) || 0;
        setManualGoals(prevGoals => {
            const newMacros = { ...prevGoals, [macro]: numValue };
            const newCalories = (newMacros.protein * 4) + (newMacros.carbs * 4) + (newMacros.fats * 9);
            return { ...newMacros, calories: Math.round(newCalories) };
        });
    };

    const handleCalorieChange = (value: string) => {
        const numValue = parseInt(value, 10) || 0;
        setManualGoals(prev => ({ ...prev, calories: numValue }));
    };

    const handleSave = () => {
        const newSettings = {
            ...localSettings,
            dailyCalorieGoal: manualGoals.calories,
            dailyProteinGoal: manualGoals.protein,
            dailyCarbGoal: manualGoals.carbs,
            dailyFatGoal: manualGoals.fats,
        };
        onSettingsChange(newSettings);
    };
    
    const hasChanges = useMemo(() => {
        return JSON.stringify(settings) !== JSON.stringify(localSettings) ||
               settings.dailyCalorieGoal !== manualGoals.calories ||
               settings.dailyProteinGoal !== manualGoals.protein ||
               settings.dailyCarbGoal !== manualGoals.carbs ||
               settings.dailyFatGoal !== manualGoals.fats;
    }, [settings, localSettings, manualGoals]);


    return (
        <div className="space-y-8">
            <Card>
                <h3 className="text-xl font-bold mb-4">Tus Datos</h3>
                <div className="grid grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm text-slate-300 mb-1">Edad</label>
                        <input type="number" value={localSettings.userVitals.age || ''} onChange={e => handleVitalsChange('age', parseInt(e.target.value))} className="w-full bg-slate-800 border-slate-700 rounded-md p-2"/>
                    </div>
                    <div>
                        <label className="block text-sm text-slate-300 mb-1">Altura (cm)</label>
                        <input type="number" value={localSettings.userVitals.height || ''} onChange={e => handleVitalsChange('height', parseInt(e.target.value))} className="w-full bg-slate-800 border-slate-700 rounded-md p-2"/>
                    </div>
                    <div>
                        <label className="block text-sm text-slate-300 mb-1">Peso Actual ({settings.weightUnit})</label>
                        <input type="number" step="0.1" value={localSettings.userVitals.weight || ''} onChange={e => handleVitalsChange('weight', parseFloat(e.target.value))} className="w-full bg-slate-800 border-slate-700 rounded-md p-2"/>
                    </div>
                     <div>
                        <label className="block text-sm text-slate-300 mb-1">Género</label>
                        <select value={localSettings.userVitals.gender || ''} onChange={e => handleVitalsChange('gender', e.target.value as any)} className="w-full bg-slate-800 border-slate-700 rounded-md p-2">
                            <option value="">Seleccionar...</option>
                            <option value="male">Masculino</option>
                            <option value="female">Femenino</option>
                        </select>
                    </div>
                    <div className="col-span-2">
                         <label className="block text-sm text-slate-300 mb-1">Nivel de Actividad</label>
                         <select value={localSettings.userVitals.activityLevel || ''} onChange={e => handleVitalsChange('activityLevel', e.target.value as any)} className="w-full bg-slate-800 border-slate-700 rounded-md p-2">
                             <option value="">Seleccionar...</option>
                             <option value="sedentary">Sedentario (poco o nada de ejercicio)</option>
                             <option value="light">Ligero (1-3 días/semana)</option>
                             <option value="moderate">Moderado (3-5 días/semana)</option>
                             <option value="active">Activo (6-7 días/semana)</option>
                             <option value="very_active">Muy Activo (trabajo físico + ejercicio)</option>
                         </select>
                    </div>
                </div>
            </Card>
            
             <Card>
                <h3 className="text-xl font-bold mb-4 flex items-center gap-2"><TargetIcon /> Tu Objetivo</h3>
                 <div className="space-y-4">
                    <div>
                         <label className="block text-sm text-slate-300 mb-1">Mi meta es...</label>
                        <div className="flex bg-slate-800 p-1 rounded-full">
                            <button onClick={() => setLocalSettings(p => ({...p, calorieGoalObjective: 'deficit'}))} className={`w-full py-1 rounded-full text-sm ${localSettings.calorieGoalObjective === 'deficit' ? 'bg-red-600' : ''}`}>Perder Peso</button>
                            <button onClick={() => setLocalSettings(p => ({...p, calorieGoalObjective: 'maintenance'}))} className={`w-full py-1 rounded-full text-sm ${localSettings.calorieGoalObjective === 'maintenance' ? 'bg-sky-600' : ''}`}>Mantener</button>
                            <button onClick={() => setLocalSettings(p => ({...p, calorieGoalObjective: 'surplus'}))} className={`w-full py-1 rounded-full text-sm ${localSettings.calorieGoalObjective === 'surplus' ? 'bg-green-600' : ''}`}>Ganar Músculo</button>
                        </div>
                    </div>
                    <div>
                        <label className="block text-sm text-slate-300 mb-1">Peso Objetivo ({settings.weightUnit})</label>
                        <input type="number" step="0.1" value={localSettings.userVitals.targetWeight || ''} onChange={e => handleVitalsChange('targetWeight', parseFloat(e.target.value))} className="w-full bg-slate-800 border-slate-700 rounded-md p-2"/>
                    </div>
                 </div>
            </Card>

            {(calculatedGoals || settings.dailyCalorieGoal) && (
                <Card className="animate-fade-in">
                    <h3 className="text-xl font-bold mb-4">Metas Diarias</h3>
                    <div className="bg-slate-900/50 p-4 rounded-lg space-y-4">
                        <div className="text-center">
                            <label className="block text-xs text-slate-400">Objetivo de Calorías Diarias</label>
                             <input type="number" value={manualGoals.calories} onChange={e => handleCalorieChange(e.target.value)} className="w-48 mx-auto text-4xl font-bold text-primary-color bg-transparent text-center focus:outline-none"/>
                        </div>
                        <div className="grid grid-cols-3 text-center pt-3 border-t border-slate-700">
                             <div>
                                <input type="number" value={manualGoals.protein} onChange={e => handleMacroChange('protein', e.target.value)} className="w-24 mx-auto font-bold text-lg bg-transparent text-center focus:outline-none"/>
                                <p className="text-xs text-blue-400">Proteína (g)</p>
                             </div>
                             <div>
                                <input type="number" value={manualGoals.carbs} onChange={e => handleMacroChange('carbs', e.target.value)} className="w-24 mx-auto font-bold text-lg bg-transparent text-center focus:outline-none"/>
                                <p className="text-xs text-orange-400">Carbs (g)</p>
                             </div>
                             <div>
                                <input type="number" value={manualGoals.fats} onChange={e => handleMacroChange('fats', e.target.value)} className="w-24 mx-auto font-bold text-lg bg-transparent text-center focus:outline-none"/>
                                <p className="text-xs text-yellow-400">Grasas (g)</p>
                             </div>
                        </div>
                         {calculatedGoals && <p className="text-xs text-slate-500 text-center pt-2">Sugerencia IA basada en TMB: {calculatedGoals.bmr} kcal, TDEE: {calculatedGoals.tdee} kcal</p>}
                    </div>
                </Card>
            )}

            <div className="flex justify-end mt-8">
                <Button onClick={handleSave} disabled={!hasChanges}>
                    <SaveIcon size={16}/> Guardar Objetivos
                </Button>
            </div>
        </div>
    );
};

export default ProgressGoals;
