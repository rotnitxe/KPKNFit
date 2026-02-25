// components/home/ReadinessWidget.tsx
// Readiness: from PostSessionFeedback when sessions exist, or DailyWellbeingLog.readiness for rest days

import React, { useMemo, useState } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { ActivityIcon } from '../icons';
import { getLocalDateString } from '../../utils/dateUtils';

export const ReadinessWidget: React.FC = () => {
    const { postSessionFeedback, dailyWellbeingLogs, history } = useAppState();
    const { handleLogDailyWellbeing } = useAppDispatch();

    const todayStr = getLocalDateString();

    const readinessFromFeedback = useMemo(() => {
        const sevenDaysAgo = new Date();
        sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
        const recent = postSessionFeedback.filter(
            f => new Date(f.date) >= sevenDaysAgo
        );
        if (recent.length === 0) return null;
        const scores: number[] = [];
        recent.forEach(f => {
            scores.push(f.cnsRecovery);
            Object.values(f.feedback || {}).forEach(m => {
                if (typeof m.strengthCapacity === 'number') scores.push(m.strengthCapacity);
            });
        });
        if (scores.length === 0) return null;
        return Math.round(scores.reduce((a, b) => a + b, 0) / scores.length * 10) / 10;
    }, [postSessionFeedback]);

    const todayWellbeing = useMemo(() =>
        dailyWellbeingLogs.find(l => l.date.startsWith(todayStr)),
    [dailyWellbeingLogs, todayStr]);

    const hasWorkoutToday = useMemo(() =>
        history.some(log => log.date.startsWith(todayStr)),
    [history, todayStr]);

    const displayReadiness = useMemo(() => {
        if (todayWellbeing?.readiness != null) return todayWellbeing.readiness;
        if (readinessFromFeedback != null && hasWorkoutToday) return readinessFromFeedback;
        if (todayWellbeing) {
            const s = todayWellbeing.sleepQuality || 3;
            const st = todayWellbeing.stressLevel || 3;
            const d = todayWellbeing.doms || 1;
            const m = todayWellbeing.motivation || 3;
            return Math.round(((s + (5 - st) + (5 - d) + m) / 4) * 2 * 10) / 10;
        }
        return null;
    }, [todayWellbeing, readinessFromFeedback, hasWorkoutToday]);

    const [isEditing, setIsEditing] = useState(false);
    const [editValue, setEditValue] = useState(displayReadiness ?? 7);

    const handleSaveReadiness = () => {
        handleLogDailyWellbeing({
            date: todayStr,
            sleepQuality: todayWellbeing?.sleepQuality ?? 3,
            stressLevel: todayWellbeing?.stressLevel ?? 3,
            doms: todayWellbeing?.doms ?? 1,
            motivation: todayWellbeing?.motivation ?? 3,
            readiness: editValue,
        });
        setIsEditing(false);
    };

    return (
        <div className="bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden">
            <div className="px-5 py-3 border-b border-white/5 flex justify-between items-center">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.25em]">
                    Disponibilidad
                </span>
                <span className="text-[8px] font-mono text-zinc-600">Readiness</span>
            </div>
            <div className="p-5 flex flex-col items-center">
                {displayReadiness != null && !isEditing ? (
                    <>
                        <div className="relative w-20 h-20 mb-3">
                            <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                                <path
                                    className="text-zinc-800"
                                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                                    fill="none"
                                    strokeWidth="2"
                                />
                                <path
                                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                                    fill="none"
                                    stroke={displayReadiness >= 7 ? '#10b981' : displayReadiness >= 4 ? '#f59e0b' : '#ef4444'}
                                    strokeWidth="2"
                                    strokeDasharray={`${displayReadiness * 10}, 100`}
                                    strokeLinecap="round"
                                />
                            </svg>
                            <div className="absolute inset-0 flex items-center justify-center">
                                <span className="text-2xl font-black font-mono text-white">{displayReadiness.toFixed(1)}</span>
                            </div>
                        </div>
                        <span className="text-[8px] text-zinc-500 font-black uppercase tracking-widest">/ 10</span>
                        <button
                            onClick={() => { setEditValue(displayReadiness); setIsEditing(true); }}
                            className="mt-3 text-[8px] font-black text-zinc-500 uppercase tracking-widest hover:text-white transition-colors"
                        >
                            Ajustar
                        </button>
                    </>
                ) : isEditing ? (
                    <div className="w-full space-y-3">
                        <input
                            type="range"
                            min="1"
                            max="10"
                            step="0.5"
                            value={editValue}
                            onChange={e => setEditValue(parseFloat(e.target.value))}
                            className="w-full accent-white"
                        />
                        <div className="flex justify-between text-[10px] font-mono text-zinc-400">
                            <span>1</span>
                            <span className="text-white font-black">{editValue.toFixed(1)}</span>
                            <span>10</span>
                        </div>
                        <div className="flex gap-2">
                            <button onClick={() => setIsEditing(false)} className="flex-1 py-2 rounded-lg text-[9px] font-black uppercase bg-zinc-800 text-white">
                                Cancelar
                            </button>
                            <button onClick={handleSaveReadiness} className="flex-1 py-2 rounded-lg text-[9px] font-black uppercase bg-white text-black">
                                Guardar
                            </button>
                        </div>
                    </div>
                ) : (
                    <div className="text-center py-4">
                        <ActivityIcon size={24} className="text-zinc-600 mx-auto mb-2" />
                        <p className="text-[9px] text-zinc-500 font-bold uppercase">Sin datos</p>
                        <button
                            onClick={() => { setEditValue(7); setIsEditing(true); }}
                            className="mt-3 text-[8px] font-black text-zinc-500 uppercase tracking-widest hover:text-white transition-colors"
                        >
                            Registrar
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
};
