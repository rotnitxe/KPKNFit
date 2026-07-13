
// components/PerformanceHighlightsWidget.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import Card from './ui/Card';
import { TrendingUpIcon, AlertTriangleIcon } from './icons';

const PerformanceHighlightsWidget: React.FC = () => {
    const { history, exerciseList } = useAppState();

    const highlights = useMemo(() => {
        const exerciseStats: Record<string, {
            name: string;
            goodScores: number[];
            badScores: number[];
            count: number;
        }> = {};

        history.forEach(log => {
            (log.completedExercises || []).forEach(ex => {
                const exInfo = exerciseList.find(e => e.id === ex.exerciseDbId);
                if (!exInfo || exInfo.type === 'Aislamiento') return; // Ignore isolation exercises for this analysis

                const key = ex.exerciseDbId || ex.exerciseName;
                if (!exerciseStats[key]) {
                    exerciseStats[key] = {
                        name: ex.exerciseName,
                        goodScores: [],
                        badScores: [],
                        count: 0
                    };
                }

                const techQuality = ex.technicalQuality || 8; // Default to good quality
                const jointLoad = ex.jointLoad || 5; // Default to medium load
                const fatigue = ex.perceivedFatigue || 5; // Default to medium fatigue
                
                // Good score: high tech quality, low joint load & fatigue
                const goodScore = (techQuality * 2) - jointLoad - fatigue;
                
                // Bad score: high joint load & fatigue, low tech quality
                const badScore = jointLoad + fatigue - techQuality;

                exerciseStats[key].goodScores.push(goodScore);
                exerciseStats[key].badScores.push(badScore);
                exerciseStats[key].count++;
            });
        });
        
        const averagedStats = Object.values(exerciseStats)
            .filter(stat => stat.count > 1) // Only consider exercises performed more than once
            .map(stat => ({
                name: stat.name,
                avgGood: stat.goodScores.reduce((a, b) => a + b, 0) / stat.count,
                avgBad: stat.badScores.reduce((a, b) => a + b, 0) / stat.count,
            }));
        
        const bestPerformers = [...averagedStats].sort((a, b) => b.avgGood - a.avgGood).slice(0, 3);
        const mostChallenging = [...averagedStats].sort((a, b) => b.avgBad - a.avgBad).slice(0, 3);

        return { bestPerformers, mostChallenging };

    }, [history, exerciseList]);

    if (highlights.bestPerformers.length === 0 && highlights.mostChallenging.length === 0) {
        return (
            <Card>
                <h3 className="text-xl font-bold text-white mb-2">Resumen de Sensaciones</h3>
                <p className="text-center text-sm text-slate-400 py-4">
                    Registra el feedback de tus ejercicios (carga articular, calidad técnica, etc.) al final de cada uno para ver este análisis.
                </p>
            </Card>
        );
    }

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-4">Resumen de Sensaciones</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {highlights.bestPerformers.length > 0 && (
                    <div>
                        <h4 className="font-semibold text-green-400 mb-2 flex items-center gap-2"><TrendingUpIcon /> Mejores Sensaciones</h4>
                        <ul className="space-y-2">
                            {highlights.bestPerformers.map(ex => (
                                <li key={ex.name} className="bg-slate-800/50 p-3 rounded-lg">
                                    <p className="font-semibold text-white">{ex.name}</p>
                                </li>
                            ))}
                        </ul>
                    </div>
                )}
                {highlights.mostChallenging.length > 0 && (
                    <div>
                        <h4 className="font-semibold text-yellow-400 mb-2 flex items-center gap-2"><AlertTriangleIcon /> Mayor Dificultad</h4>
                        <ul className="space-y-2">
                            {highlights.mostChallenging.map(ex => (
                                <li key={ex.name} className="bg-slate-800/50 p-3 rounded-lg">
                                    <p className="font-semibold text-white">{ex.name}</p>
                                </li>
                            ))}
                        </ul>
                    </div>
                )}
            </div>
        </Card>
    );
};

export default PerformanceHighlightsWidget;
