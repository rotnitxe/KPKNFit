
// components/OnThisDayCard.tsx
import React, { useState, useEffect, useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { generateOnThisDayMessage } from '../services/aiService';
import { WorkoutLog, CompletedSet } from '../types';
import { calculateBrzycki1RM } from '../utils/calculations';
import { getLocalDateString } from '../utils/dateUtils';
import Card from './ui/Card';
import { ClockIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';

interface ComparisonData {
  exerciseName: string;
  oneYearAgoSet: { weight: number; reps: number; date: string };
  recentPRSet: { weight: number; reps: number; date: string };
  motivationalMessage: string;
}

const OnThisDayCard: React.FC = () => {
    const { history, settings, isOnline } = useAppState();
    const [comparison, setComparison] = useState<ComparisonData | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    const keyLifts = ['Press de Banca', 'Sentadilla', 'Peso Muerto'];

    useEffect(() => {
        const findComparison = async () => {
            if (history.length < 2) {
                setIsLoading(false);
                return;
            }

            const today = new Date();
            const oneYearAgo = new Date();
            oneYearAgo.setFullYear(today.getFullYear() - 1);
            const oneYearAgoStr = getLocalDateString(oneYearAgo);

            const logFromLastYear = history.find(log => log.date.startsWith(oneYearAgoStr));

            if (!logFromLastYear) {
                setIsLoading(false);
                return;
            }

            const exercises = logFromLastYear.completedExercises || [];
            let keyExercise = exercises.find(ex => keyLifts.some(lift => ex.exerciseName.includes(lift)));
            if (!keyExercise) {
                keyExercise = exercises[0];
            }
            if (!keyExercise) {
                setIsLoading(false);
                return;
            }

            const bestSetLastYear = keyExercise.sets.reduce((best, current) => {
                const bestE1rm = calculateBrzycki1RM(best.weight || 0, best.completedReps || 0);
                const currentE1rm = calculateBrzycki1RM(current.weight || 0, current.completedReps || 0);
                return currentE1rm > bestE1rm ? current : best;
            }, keyExercise.sets[0]);

            if (!bestSetLastYear?.weight || !bestSetLastYear?.completedReps) {
                 setIsLoading(false);
                 return;
            }

            let recentPR = { weight: 0, reps: 0, e1rm: 0, date: ''};
            history.forEach(log => {
                const ex = (log.completedExercises || []).find(e => e.exerciseId === keyExercise!.exerciseId || e.exerciseName === keyExercise!.exerciseName);
                if (ex) {
                    ex.sets.forEach(set => {
                        const e1rm = calculateBrzycki1RM(set.weight || 0, set.completedReps || 0);
                        if (e1rm > recentPR.e1rm) {
                            recentPR = { weight: set.weight!, reps: set.completedReps!, e1rm, date: log.date };
                        }
                    });
                }
            });

            if (recentPR.e1rm <= calculateBrzycki1RM(bestSetLastYear.weight, bestSetLastYear.completedReps)) {
                 setIsLoading(false);
                 return;
            }
            
            let message = "¡Mira cuánto has progresado en un año! Sigue así.";
            if (isOnline) {
                try {
                    const aiResponse = await generateOnThisDayMessage(
                        keyExercise.exerciseName,
                        { weight: bestSetLastYear.weight, reps: bestSetLastYear.completedReps },
                        { weight: recentPR.weight, reps: recentPR.reps },
                        settings
                    );
                    message = aiResponse.message;
                } catch (e) {
                    console.error("Failed to get On This Day message from AI", e);
                }
            }

            setComparison({
                exerciseName: keyExercise.exerciseName,
                oneYearAgoSet: { weight: bestSetLastYear.weight, reps: bestSetLastYear.completedReps, date: logFromLastYear.date },
                recentPRSet: { weight: recentPR.weight, reps: recentPR.reps, date: recentPR.date },
                motivationalMessage: message,
            });
            setIsLoading(false);
        };

        findComparison();
    }, [history, isOnline, settings]);

    if (isLoading) {
        return <Card><SkeletonLoader lines={3} /></Card>;
    }

    if (!comparison) {
        return null; // Don't render if there's no comparison to show
    }

    return (
        <Card className="md:col-span-3 animate-fade-in-up">
            <h3 className="text-xl font-bold mb-4 flex items-center gap-2"><ClockIcon/> En Este Día...</h3>
            <p className="text-sm text-slate-400 mb-4">Un vistazo a tu progreso en el último año para <span className="font-bold text-slate-300">{comparison.exerciseName}</span>.</p>
            <div className="grid grid-cols-2 gap-4 text-center">
                <div className="bg-slate-900/50 p-3 rounded-lg">
                    <p className="text-xs text-slate-500">{new Date(comparison.oneYearAgoSet.date).toLocaleDateString()}</p>
                    <p className="text-3xl font-bold text-white">{comparison.oneYearAgoSet.weight}{settings.weightUnit}</p>
                    <p className="text-slate-300">x {comparison.oneYearAgoSet.reps} reps</p>
                </div>
                 <div className="bg-primary-color/20 p-3 rounded-lg border border-primary-color/50">
                    <p className="text-xs text-cyber-copper/80">{new Date(comparison.recentPRSet.date).toLocaleDateString()}</p>
                    <p className="text-3xl font-bold text-white">{comparison.recentPRSet.weight}{settings.weightUnit}</p>
                    <p className="text-slate-300">x {comparison.recentPRSet.reps} reps</p>
                </div>
            </div>
            <p className="text-center italic text-slate-300 mt-4">"{comparison.motivationalMessage}"</p>
        </Card>
    );
};

export default OnThisDayCard;
