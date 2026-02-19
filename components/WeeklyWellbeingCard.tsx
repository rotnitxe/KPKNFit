
// components/WeeklyWellbeingCard.tsx
import React, { useMemo, useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { getWeekId } from '../utils/calculations';
import Card from './ui/Card';
import { InfoTooltip } from './ui/InfoTooltip';
import Button from './ui/Button';
import ReadinessCheckModal from './ReadinessCheckModal';
import { DailyWellbeingLog } from '../types';

const EMOJI_MAPS = {
    sleep: ['😫', '😕', '😐', '🙂', '😄'],
    stress: ['😄', '🙂', '😐', '😕', '🥵'], // Inverted: low is good
    doms: ['😄', '🙂', '😐', '😕', '😫'], // Inverted: low is good
    motivation: ['🧊', '😕', '😐', '🙂', '🔥'],
};

const getEmoji = (type: keyof typeof EMOJI_MAPS, value: number | null) => {
    if (value === null) return '🤔';
    const index = Math.round(value) - 1;
    return EMOJI_MAPS[type][index] || '🤔';
}

const WeeklyWellbeingCard: React.FC = () => {
    const { history, dailyWellbeingLogs, settings } = useAppState();
    const { handleLogDailyWellbeing } = useAppDispatch();
    const [isModalOpen, setIsModalOpen] = useState(false);

    const todayStr = new Date().toISOString().split('T')[0];

    const hasLoggedToday = useMemo(() => {
        // Check if there is a workout log with readiness OR a daily wellbeing log for today
        const hasWorkoutLog = history.some(log => log.date.startsWith(todayStr) && log.readiness);
        const hasDailyLog = dailyWellbeingLogs.some(log => log.date.startsWith(todayStr));
        return hasWorkoutLog || hasDailyLog;
    }, [history, dailyWellbeingLogs, todayStr]);

    const weeklyAverages = useMemo(() => {
        const oneWeekAgo = new Date();
        oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);
        
        // Combine readiness from workout history AND daily logs
        const recentWorkoutLogs = history
            .filter(log => new Date(log.date) >= oneWeekAgo && log.readiness)
            .map(log => log.readiness);
            
        const recentDailyLogs = dailyWellbeingLogs
            .filter(log => new Date(log.date) >= oneWeekAgo)
            .map(log => ({
                sleepQuality: log.sleepQuality,
                stressLevel: log.stressLevel,
                doms: log.doms,
                motivation: log.motivation
            }));

        const combinedLogs = [...recentWorkoutLogs, ...recentDailyLogs];
        
        if (combinedLogs.length === 0) return null;
        
        const totals = combinedLogs.reduce((acc, log) => {
            acc.sleep += log.sleepQuality;
            acc.stress += log.stressLevel;
            acc.doms += log.doms;
            acc.motivation += log.motivation;
            return acc;
        }, { sleep: 0, stress: 0, doms: 0, motivation: 0 });
    
        return {
            sleep: totals.sleep / combinedLogs.length,
            stress: totals.stress / combinedLogs.length,
            doms: totals.doms / combinedLogs.length,
            motivation: totals.motivation / combinedLogs.length,
        };
    }, [history, dailyWellbeingLogs]);

    const handleSaveDailyWellbeing = (data: any) => {
        handleLogDailyWellbeing({
            date: new Date().toISOString(),
            ...data
        });
        setIsModalOpen(false);
    };

    if (!weeklyAverages && !hasLoggedToday) {
         return (
            <div className="bg-slate-900/30 border border-white/5 rounded-2xl p-4 text-center">
                 <p className="text-sm text-slate-400 mb-2">¿Cómo te sientes hoy?</p>
                 <Button onClick={() => setIsModalOpen(true)} variant="secondary" className="w-full !text-xs">Registrar Estado</Button>
                 <ReadinessCheckModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} onContinue={handleSaveDailyWellbeing} />
            </div>
         );
    }

    if (!weeklyAverages) return null;

    return (
        <div className="bg-slate-900/30 border border-white/5 rounded-2xl p-4">
            <ReadinessCheckModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} onContinue={handleSaveDailyWellbeing} />
            
            <div className="flex justify-between items-center mb-3">
                <h3 className="text-xs font-black text-slate-500 uppercase tracking-widest">Bienestar (Promedio 7d)</h3>
                {!hasLoggedToday && (
                    <button onClick={() => setIsModalOpen(true)} className="text-[10px] bg-slate-800 hover:bg-slate-700 px-2 py-1 rounded text-slate-300 transition-colors">
                        + Hoy
                    </button>
                )}
            </div>

            <div className="grid grid-cols-4 gap-2 text-center">
                <div className="bg-white/5 p-2 rounded-xl">
                    <p className="text-2xl">{getEmoji('sleep', weeklyAverages.sleep)}</p>
                    <p className="text-[9px] font-bold text-slate-400 mt-1 uppercase">Sueño</p>
                </div>
                <div className="bg-white/5 p-2 rounded-xl">
                    <p className="text-2xl">{getEmoji('stress', weeklyAverages.stress)}</p>
                    <p className="text-[9px] font-bold text-slate-400 mt-1 uppercase">Estrés</p>
                </div>
                <div className="bg-white/5 p-2 rounded-xl">
                    <p className="text-2xl">{getEmoji('doms', weeklyAverages.doms)}</p>
                    <p className="text-[9px] font-bold text-slate-400 mt-1 uppercase">DOMS</p>
                </div>
                 <div className="bg-white/5 p-2 rounded-xl">
                    <p className="text-2xl">{getEmoji('motivation', weeklyAverages.motivation)}</p>
                    <p className="text-[9px] font-bold text-slate-400 mt-1 uppercase">Ganas</p>
                </div>
            </div>
        </div>
    );
};

export default WeeklyWellbeingCard;
