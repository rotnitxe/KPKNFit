
// components/PowerliftingDashboard.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { calculatePowerliftingMetrics } from '../services/analysisService';
import Card from './ui/Card';
import { InfoTooltip } from './ui/InfoTooltip';

const PowerliftingDashboard: React.FC = () => {
    const { history, settings, exerciseList } = useAppState();
    const metrics = useMemo(() => calculatePowerliftingMetrics(history, settings, exerciseList), [history, settings, exerciseList]);

    if (!metrics) return null;

    const getRatioFeedback = (ratio: number, min: number, max: number): { text: string, color: string } => {
        if (ratio >= min && ratio <= max) return { text: 'Equilibrado', color: 'text-green-400' };
        if (ratio > max) return { text: 'Alto', color: 'text-yellow-400' };
        return { text: 'Bajo', color: 'text-yellow-400' };
    };
    
    const getVolumeFeedback = (sets: number, min: number, max: number): { text: string, color: string } => {
        if (sets < min) return { text: 'Volumen Bajo', color: 'text-sky-400' };
        if (sets <= max) return { text: 'Volumen Óptimo', color: 'text-green-400' };
        return { text: 'Volumen Alto', color: 'text-red-400' };
    };

    const lifts = [
        { name: 'Sentadilla', data: metrics.weeklyVolume.squat, volumeRange: [10, 18] },
        { name: 'Banca', data: metrics.weeklyVolume.bench, volumeRange: [12, 20] },
        { name: 'Peso Muerto', data: metrics.weeklyVolume.deadlift, volumeRange: [5, 10] },
    ];

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-4">Powerlifting Dashboard Semanal</h3>
            <div className="space-y-6">
                <div>
                    <h4 className="font-semibold text-slate-200 mb-2 flex items-center gap-1">Volumen de Levantamientos Principales</h4>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-center text-sm">
                        {lifts.map(lift => {
                            const volumeFeedback = getVolumeFeedback(lift.data.effectiveSets, lift.volumeRange[0], lift.volumeRange[1]);
                            return (
                                <div key={lift.name} className="bg-slate-800/50 p-3 rounded-lg space-y-3">
                                    <p className="font-bold text-lg text-white">{lift.name}</p>
                                    <div>
                                        <p className="font-bold text-2xl text-white">{lift.data.totalReps}</p>
                                        <p className="text-xs text-slate-400 flex items-center justify-center gap-1">Repeticiones Totales <InfoTooltip term="Repeticiones Totales" /></p>
                                    </div>
                                    <div>
                                        <p className="font-bold text-2xl text-white">{lift.data.effectiveSets}</p>
                                        <p className="text-xs text-slate-400 flex items-center justify-center gap-1">Series Efectivas <InfoTooltip term="Series Efectivas" /></p>
                                        <p className={`text-xs font-semibold mt-1 ${volumeFeedback.color}`}>{volumeFeedback.text}</p>
                                    </div>
                                </div>
                            )
                        })}
                    </div>
                </div>
                <div className="pt-4 border-t border-slate-700/50">
                    <h4 className="font-semibold text-slate-200 mb-2 flex items-center gap-1">Ratios de Balance <InfoTooltip term="Ratios de Balance" /></h4>
                    <div className="space-y-3 text-sm">
                        <div className="bg-slate-800/50 p-3 rounded-lg">
                            <div className="flex justify-between items-center">
                                <div>
                                    <p className="font-semibold">OHP / Press Banca</p>
                                    <p className="text-xs text-slate-400">Ideal: 35-45%</p>
                                </div>
                                <div className="text-right">
                                    <p className="font-bold text-xl">{(metrics.balanceRatios.ohpToBench * 100).toFixed(1)}%</p>
                                    <p className={`text-xs font-semibold ${getRatioFeedback(metrics.balanceRatios.ohpToBench * 100, 35, 45).color}`}>
                                        {getRatioFeedback(metrics.balanceRatios.ohpToBench * 100, 35, 45).text}
                                    </p>
                                </div>
                            </div>
                        </div>
                        <div className="bg-slate-800/50 p-3 rounded-lg">
                             <div className="flex justify-between items-center">
                                <div>
                                    <p className="font-semibold">Sentadilla / Peso Muerto</p>
                                    <p className="text-xs text-slate-400">Ideal: 80-90%</p>
                                </div>
                                 <div className="text-right">
                                    <p className="font-bold text-xl">{(metrics.balanceRatios.squatToDeadlift * 100).toFixed(1)}%</p>
                                     <p className={`text-xs font-semibold ${getRatioFeedback(metrics.balanceRatios.squatToDeadlift * 100, 80, 90).color}`}>
                                        {getRatioFeedback(metrics.balanceRatios.squatToDeadlift * 100, 80, 90).text}
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </Card>
    );
};

export default PowerliftingDashboard;
