
// components/PersonalRecordsView.tsx
import React, { useMemo } from 'react';
import { Program, WorkoutLog, Settings, Exercise } from '../types';
import { calculateBrzycki1RM } from '../utils/calculations';
import Card from './ui/Card';
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { TrophyIcon, TrendingUpIcon } from './icons';

interface PersonalRecordsViewProps {
  programs: Program[];
  history: WorkoutLog[];
  settings: Settings;
}

const PersonalRecordsView: React.FC<PersonalRecordsViewProps> = ({ programs, history, settings }) => {

    const favoriteExercises = useMemo(() => {
        const favs = new Map<string, Exercise>();
        programs.forEach(p => {
            (p.macrocycles || []).forEach(m => {
                (m.blocks || []).forEach(b => {
                    (b.mesocycles || []).forEach(meso => {
                        (meso.weeks || []).forEach(w => {
                            (w.sessions || []).forEach(s => {
                                (s.exercises || []).forEach(ex => {
                                    if (ex.isFavorite) {
                                        const key = ex.exerciseDbId || ex.name.toLowerCase();
                                        if (!favs.has(key)) {
                                            favs.set(key, ex);
                                        }
                                    }
                                });
                            });
                        });
                    });
                });
            });
        });
        return Array.from(favs.values());
    }, [programs]);

    const personalRecords = useMemo(() => {
        return favoriteExercises.map(exercise => {
            const exerciseKey = exercise.exerciseDbId || exercise.name.toLowerCase();
            let best1RM = 0;
            let initial1RM = 0;
            let bestSet = { weight: 0, reps: 0, date: '' };
            const historyData: { date: string, e1RM: number }[] = [];
            let runningMax1RM = 0;

            const sortedHistory = [...history]
                .filter(log => (log.completedExercises || []).some(ce => (ce.exerciseDbId || ce.exerciseName.toLowerCase()) === exerciseKey))
                .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());

            sortedHistory.forEach((log, index) => {
                const completedEx = (log.completedExercises || []).find(ce => (ce.exerciseDbId || ce.exerciseName.toLowerCase()) === exerciseKey);
                if (completedEx) {
                    let sessionMax1RM = 0;
                    completedEx.sets.forEach(set => {
                        if (set.weight && set.completedReps) {
                            const e1RM = calculateBrzycki1RM(set.weight, set.completedReps);
                            if (e1RM > best1RM) {
                                best1RM = e1RM;
                                bestSet = { weight: set.weight, reps: set.completedReps, date: log.date };
                            }
                            if (e1RM > sessionMax1RM) {
                                sessionMax1RM = e1RM;
                            }
                        }
                    });
                    
                    if (index === 0 && sessionMax1RM > 0) {
                        initial1RM = sessionMax1RM;
                    }

                    if (sessionMax1RM > runningMax1RM) {
                        runningMax1RM = sessionMax1RM;
                        historyData.push({
                            date: new Date(log.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' }),
                            e1RM: Math.round(runningMax1RM * 10) / 10
                        });
                    }
                }
            });
            
            let improvementIndex = 0;
            if (initial1RM > 0 && best1RM > initial1RM) {
                improvementIndex = ((best1RM - initial1RM) / initial1RM) * 100;
            }

            if (best1RM > 0) {
                return {
                    exercise,
                    best1RM,
                    bestSet,
                    historyData,
                    improvementIndex
                };
            }
            return null;
        }).filter((record): record is NonNullable<typeof record> => record !== null)
          .sort((a, b) => b.best1RM - a.best1RM); 
    }, [favoriteExercises, history]);

    if (personalRecords.length === 0) {
        return (
            <Card>
                <div className="text-center py-8 text-slate-400">
                    <TrophyIcon size={48} className="mx-auto text-slate-500 mb-4" />
                    <h3 className="text-xl font-semibold text-white">Aún no hay récords para mostrar</h3>
                    <p className="mt-2 text-sm">Marca tus ejercicios principales como <span className="text-cyber-copper font-semibold">favoritos</span> (con la ⭐) en el editor de sesiones para que aparezcan aquí.</p>
                </div>
            </Card>
        );
    }
    
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {personalRecords.map(({ exercise, best1RM, bestSet, historyData, improvementIndex }, index) => (
                <div key={exercise.id} className="animate-fade-in-up" style={{ animationDelay: `${index * 50}ms` }}>
                    <Card className="!p-4 flex flex-col h-full">
                        <div className="flex-grow">
                            <h4 className="font-bold text-cyber-copper truncate">{exercise.name}</h4>
                            <p className="text-5xl font-black text-white my-2">{best1RM.toFixed(1)} <span className="text-2xl text-slate-400">{settings.weightUnit}</span></p>
                            <p className="text-sm text-slate-400">Logrado con {bestSet.weight}{settings.weightUnit} x {bestSet.reps} reps</p>
                            <p className="text-xs text-slate-500">el {new Date(bestSet.date).toLocaleDateString('es-ES')}</p>
                            {improvementIndex > 0 &&
                                <div className="mt-2 text-xs font-bold text-green-400 flex items-center gap-1">
                                    <TrendingUpIcon size={14} /> +{improvementIndex.toFixed(0)}% de mejora
                                </div>
                            }
                        </div>
                        {historyData.length > 1 && (
                            <div className="mt-4 h-24">
                                 <ResponsiveContainer width="100%" height="100%">
                                    <LineChart data={historyData} margin={{ top: 5, right: 5, left: -25, bottom: 0 }}>
                                        <XAxis dataKey="date" stroke="#94a3b8" fontSize={10} tickLine={false} axisLine={false} />
                                        <YAxis stroke="#94a3b8" fontSize={10} tickLine={false} axisLine={false} />
                                        <Tooltip contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', fontSize: '12px', padding: '4px 8px' }}/>
                                        <Line type="monotone" dataKey="e1RM" name="1RM Estimado" stroke="#FF7B00" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
                                    </LineChart>
                                </ResponsiveContainer>
                            </div>
                        )}
                    </Card>
                </div>
            ))}
        </div>
    );
};

export default PersonalRecordsView;
