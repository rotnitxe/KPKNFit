
// components/IPFPointsCard.tsx
import React, { useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { calculateBrzycki1RM, calculateIPFGLPoints } from '../utils/calculations';
import Card from './ui/Card';
import { TrophyIcon } from './icons';
import { InfoTooltip } from './ui/InfoTooltip';
import Button from './ui/Button';

const IPFPointsCard: React.FC = () => {
    const { history, settings } = useAppState();
    const { navigateTo } = useAppDispatch();

    const ipfData = useMemo(() => {
        const { weight, gender } = settings.userVitals;
        if (!weight || weight <= 0 || !gender || gender === 'other') return null;

        const lifts = { squat: 0, bench: 0, deadlift: 0 };
        const keywords = { squat: 'sentadilla', bench: 'press de banca', deadlift: 'peso muerto' };

        history.forEach(log => (log.completedExercises || []).forEach(ex => {
            const name = ex.exerciseName.toLowerCase();
            if (name.includes(keywords.squat)) lifts.squat = Math.max(lifts.squat, ...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
            if (name.includes(keywords.bench)) lifts.bench = Math.max(lifts.bench, ...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
            if (name.includes(keywords.deadlift)) lifts.deadlift = Math.max(lifts.deadlift, ...ex.sets.map(s => calculateBrzycki1RM(s.weight || 0, s.completedReps || 0)));
        }));

        const total = lifts.squat + lifts.bench + lifts.deadlift;

        const options = {
            gender,
            equipment: 'classic' as const,
            weightUnit: settings.weightUnit,
        };

        const points = {
            squat: calculateIPFGLPoints(lifts.squat, weight, { ...options, lift: 'squat' }),
            bench: calculateIPFGLPoints(lifts.bench, weight, { ...options, lift: 'bench' }),
            deadlift: calculateIPFGLPoints(lifts.deadlift, weight, { ...options, lift: 'deadlift' }),
            total: calculateIPFGLPoints(total, weight, { ...options, lift: 'total' }),
        };

        return { lifts, points, total };

    }, [history, settings.userVitals, settings.weightUnit]);

    if (!ipfData) {
        return (
             <Card>
                <h3 className="text-xl font-bold mb-2 flex items-center gap-2"><TrophyIcon/> Puntos IPF GL</h3>
                <p className="text-center text-sm text-slate-400 py-4">
                    Registra tu peso corporal y género en "Progreso" {'>'} "Mis Objetivos" para ver tus puntos IPF GL.
                </p>
                <Button onClick={() => navigateTo('progress', { activeTab: 'goals' })} variant="secondary" className="w-full">
                    Ir a Mis Objetivos
                </Button>
            </Card>
        );
    }
    
    if (ipfData.total === 0) return null;

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-3 flex items-center gap-2">
                Puntos IPF GL (Estimados) <InfoTooltip term="IPF GL Points" />
            </h3>
            <div className="space-y-3">
                <div className="flex justify-between items-center bg-slate-800/50 p-3 rounded-lg text-lg">
                    <span className="font-bold text-primary-color">Total</span>
                    <span className="font-bold text-primary-color">{ipfData.points.total}</span>
                </div>
                <div className="grid grid-cols-3 gap-2 text-center">
                    <div className="bg-slate-900/50 p-2 rounded-lg">
                        <p className="text-sm font-semibold">Sentadilla</p>
                        <p className="text-2xl font-bold">{ipfData.points.squat}</p>
                        <p className="text-xs text-slate-500">{ipfData.lifts.squat.toFixed(1)} {settings.weightUnit}</p>
                    </div>
                    <div className="bg-slate-900/50 p-2 rounded-lg">
                        <p className="text-sm font-semibold">Banca</p>
                        <p className="text-2xl font-bold">{ipfData.points.bench}</p>
                        <p className="text-xs text-slate-500">{ipfData.lifts.bench.toFixed(1)} {settings.weightUnit}</p>
                    </div>
                    <div className="bg-slate-900/50 p-2 rounded-lg">
                        <p className="text-sm font-semibold">Peso Muerto</p>
                        <p className="text-2xl font-bold">{ipfData.points.deadlift}</p>
                        <p className="text-xs text-slate-500">{ipfData.lifts.deadlift.toFixed(1)} {settings.weightUnit}</p>
                    </div>
                </div>
            </div>
        </Card>
    );
};

export default IPFPointsCard;
