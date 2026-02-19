
// components/CorrelationWidget.tsx
import React, { useMemo } from 'react';
import { useAppState } from '../contexts/AppContext';
import { getWeekId } from '../utils/calculations';
import { calculateACWR, calculateSessionVolume } from '../services/analysisService';
import { calculateCompletedSessionStress } from '../services/fatigueService';
import { ComposedChart, Line, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, ReferenceArea } from 'recharts';
import Card from './ui/Card';
import { WorkoutLog, SleepLog, Session, Exercise } from '../types';

interface WeeklyCorrelationData {
    weekName: string;
    effectiveVolume: number;
    topMuscle: string;
    avgSessionStress: number;
    acwr: number;
    avgSleep: number | null;
}

const CustomTooltipWithMuscle = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
        const topMuscle = payload[0]?.payload?.topMuscle;
        return (
          <div className="bg-slate-800/80 backdrop-blur-sm p-3 rounded-lg border border-slate-700 text-sm">
            <p className="font-bold text-white">{label}</p>
            {payload.map((p: any) => (
              <p key={p.dataKey} style={{ color: p.color }}>
                {p.dataKey === 'effectiveVolume' && topMuscle !== 'N/A' ?
                  `Volumen (${topMuscle}): ${p.value.toFixed(1)}` :
                  `${p.name}: ${p.value.toFixed(2)}`
                }
              </p>
            ))}
          </div>
        );
    }
    return null;
};

const CorrelationWidget: React.FC = () => {
    const { history, settings, exerciseList, sleepLogs, muscleHierarchy, programs } = useAppState();

    const weeklyData = useMemo<WeeklyCorrelationData[]>(() => {
        if (history.length < 1) return [];

        const dataByWeek = new Map<string, { logs: WorkoutLog[], sleeps: SleepLog[] }>();
        
        history.forEach(log => {
            const weekId = getWeekId(new Date(log.date), settings.startWeekOn);
            if (!dataByWeek.has(weekId)) dataByWeek.set(weekId, { logs: [], sleeps: [] });
            dataByWeek.get(weekId)!.logs.push(log);
        });
        sleepLogs.forEach(log => {
            const weekId = getWeekId(new Date(log.endTime), settings.startWeekOn);
            if (dataByWeek.has(weekId)) {
                dataByWeek.get(weekId)!.sleeps.push(log);
            }
        });

        const sortedWeeks = Array.from(dataByWeek.keys()).sort((a, b) => new Date(a).getTime() - new Date(b).getTime());

        return sortedWeeks.map(weekId => {
            const weekData = dataByWeek.get(weekId)!;

            const weeklyVolumeByMuscle = new Map<string, number>();
            weekData.logs.forEach(log => {
                const tempSession: Session = {
                    id: log.sessionId,
                    name: log.sessionName,
                    description: '',
                    exercises: (log.completedExercises || []).map(ce => ({
                        id: ce.exerciseId,
                        name: ce.exerciseName,
                        exerciseDbId: ce.exerciseDbId,
                        restTime: 90,
                        trainingMode: 'reps',
                        isFavorite: false,
                        sets: ce.sets as any, 
                    })) as Exercise[],
                };
                const sessionVolume = calculateSessionVolume(tempSession, exerciseList, muscleHierarchy);
                sessionVolume.forEach(muscleVol => {
                    const currentVol = weeklyVolumeByMuscle.get(muscleVol.muscleGroup) || 0;
                    weeklyVolumeByMuscle.set(muscleVol.muscleGroup, currentVol + muscleVol.displayVolume);
                });
            });

            let topMuscle = 'N/A';
            let topVolume = 0;
            weeklyVolumeByMuscle.forEach((volume, muscle) => {
                if (volume > topVolume) {
                    topVolume = volume;
                    topMuscle = muscle;
                }
            });
            
            const totalStress = weekData.logs.reduce((sum, log) => sum + (log.sessionStressScore || calculateCompletedSessionStress(log.completedExercises || [], exerciseList)), 0);
            const avgSessionStress = weekData.logs.length > 0 ? totalStress / weekData.logs.length : 0;
            
            const endOfWeek = new Date(weekId);
            endOfWeek.setDate(endOfWeek.getDate() + 6);
            const historyUpToEndOfWeek = history.filter(log => new Date(log.date) <= endOfWeek);
            const acwr = calculateACWR(historyUpToEndOfWeek, settings, exerciseList).acwr;
            
            const avgSleep = weekData.sleeps.length > 0 ? weekData.sleeps.reduce((sum, log) => sum + log.duration, 0) / weekData.sleeps.length : null;

            return {
                weekName: new Date(weekId).toLocaleDateString('es-ES', { month: 'short', day: 'numeric' }),
                effectiveVolume: topVolume,
                topMuscle: topMuscle,
                avgSessionStress,
                acwr,
                avgSleep
            };
        }).slice(-12);
    }, [history, settings, exerciseList, sleepLogs, muscleHierarchy, programs]);

    if (weeklyData.length < 2) {
        return null;
    }

    return (
        <Card>
            <h3 className="font-bold text-lg text-white mb-4">Panel de Correlación</h3>
            <ResponsiveContainer width="100%" height={300}>
                <ComposedChart data={weeklyData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                    <XAxis dataKey="weekName" stroke="#94a3b8" fontSize={10} />
                    <YAxis yAxisId="left" stroke="#3b82f6" fontSize={10} label={{ value: 'Series', angle: -90, position: 'insideLeft', fill: '#3b82f6' }}/>
                    <YAxis yAxisId="right" orientation="right" stroke="#a855f7" fontSize={10} domain={[0.5, 2]} />
                    <Tooltip content={<CustomTooltipWithMuscle />} />
                    <Legend wrapperStyle={{fontSize: "12px"}}/>
                    <ReferenceArea yAxisId="right" y1={0.8} y2={1.3} fill="rgba(74, 222, 128, 0.1)" stroke="transparent" />

                    <Bar yAxisId="left" dataKey="effectiveVolume" name="Volumen Efectivo" fill="#3b82f6" barSize={20} />
                    <Line yAxisId="right" type="monotone" dataKey="acwr" name="Ratio ACWR" stroke="#2dd4bf" strokeWidth={2} />
                    <Line yAxisId="left" type="monotone" dataKey="avgSessionStress" name="Estrés x Sesión (Prom.)" stroke="#a855f7" strokeWidth={2} connectNulls />
                    <Line yAxisId="left" type="monotone" dataKey="avgSleep" name="Sueño (Prom. h)" stroke="#facc15" strokeDasharray="3 3" connectNulls />
                </ComposedChart>
            </ResponsiveContainer>
        </Card>
    );
};

export default CorrelationWidget;
