// components/GoalProjection.tsx
import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { generateWeightProjection } from '../services/aiService';
import Card from './ui/Card';
import Button from './ui/Button';
import { SparklesIcon } from './icons';

const GoalProjection: React.FC = () => {
    const { bodyProgress, settings, isOnline, nutritionLogs } = useAppState();
    const { navigateTo } = useAppDispatch();
    const [projection, setProjection] = useState<{ projection: string; summary: string } | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    
    const { targetWeight, startWeight, currentWeight, progressPercentage } = useMemo(() => {
        const target = settings.userVitals.targetWeight;
        if (!target) return { targetWeight: null, startWeight: null, currentWeight: null, progressPercentage: 0 };
        
        const logsWithWeight = bodyProgress.filter(log => log.weight).sort((a,b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        if (logsWithWeight.length < 1) return { targetWeight: target, startWeight: null, currentWeight: null, progressPercentage: 0 };
        
        const start = logsWithWeight[0].weight!;
        const current = logsWithWeight[logsWithWeight.length - 1].weight!;
        
        const totalToChange = start - target;
        const changedSoFar = start - current;
        
        const percentage = totalToChange !== 0 ? (changedSoFar / totalToChange) * 100 : (current === target ? 100 : 0);
        
        return { targetWeight: target, startWeight: start, currentWeight: current, progressPercentage: Math.min(100, Math.max(0, percentage)) };
    }, [bodyProgress, settings.userVitals.targetWeight]);

    const handleGenerateProjection = async () => {
        if(!isOnline || bodyProgress.length < 2 || !targetWeight) return;
        setIsLoading(true);
        setProjection(null);

        const twoWeeksAgo = new Date();
        twoWeeksAgo.setDate(twoWeeksAgo.getDate() - 14);
        const recentNutritionLogs = nutritionLogs.filter(log => new Date(log.date) >= twoWeeksAgo);
        const daysWithLogs = new Set(recentNutritionLogs.map(l => l.date.split('T')[0])).size;
        const totalCals = recentNutritionLogs.reduce((sum, log) => sum + log.foods.reduce((s,f)=>s+f.calories, 0), 0);
        const avgIntake = daysWithLogs > 0 ? totalCals / daysWithLogs : 0;
        
        const { age, weight, height, gender, activityLevel } = settings.userVitals;
        let tdee = 0;
        if(age && weight && height && gender && activityLevel){
            let bmr = (10 * weight) + (6.25 * height) - (5 * age) + (gender === 'male' ? 5 : -161);
            const activityMultipliers = { sedentary: 1.2, light: 1.375, moderate: 1.55, active: 1.725, very_active: 1.9 };
            tdee = bmr * activityMultipliers[activityLevel];
        }

        const logsWithWeight = bodyProgress.filter(log => log.weight).slice(-14);

        try {
            const result = await generateWeightProjection(avgIntake, tdee, logsWithWeight, targetWeight, settings);
            setProjection(result);
        } catch (e) {
            console.error(e);
        } finally {
            setIsLoading(false);
        }
    }

    if (!targetWeight) {
        return (
            <Card>
                <div className="text-center py-4">
                    <p className="text-slate-400">No has definido un peso objetivo.</p>
                    <Button onClick={() => navigateTo('athlete-profile')} variant="secondary" className="mt-2">Definir Objetivo</Button>
                </div>
            </Card>
        );
    }

    return (
        <Card>
            <h3 className="text-xl font-bold mb-4">Proyección de Metas</h3>
            <div className="space-y-3">
                <div className="text-center">
                    <span className="text-sm text-slate-400">Progreso hacia tu meta de {targetWeight}{settings.weightUnit}</span>
                    <p className="text-5xl font-bold text-white my-1">{progressPercentage.toFixed(1)}%</p>
                </div>
                <div className="w-full bg-slate-700 rounded-full h-4">
                    <div className="bg-primary-gradient h-4 rounded-full" style={{ width: `${progressPercentage}%` }}></div>
                </div>
                 <div className="flex justify-between text-xs font-semibold">
                    <span>{startWeight}{settings.weightUnit}</span>
                    <span>{currentWeight}{settings.weightUnit}</span>
                    <span>{targetWeight}{settings.weightUnit}</span>
                </div>
                
                 {projection && !isLoading && (
                    <div className="text-center pt-3 mt-3 border-t border-slate-700">
                        <p className="text-sm text-slate-300 italic">"{projection.summary}"</p>
                        <p className="font-semibold text-white mt-1">Tiempo estimado: {projection.projection}</p>
                    </div>
                )}
                
                <Button onClick={handleGenerateProjection} isLoading={isLoading} disabled={!isOnline || bodyProgress.length < 2} variant="secondary" className="w-full !text-sm">
                    <SparklesIcon/> {projection ? 'Recalcular Proyección' : 'Calcular Proyección IA'}
                </Button>
            </div>
        </Card>
    );
}

export default GoalProjection;
