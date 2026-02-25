// components/home/KeyDatesWidget.tsx
// Fechas clave: countdown (meses, días) o próximo evento cíclico

import React, { useMemo } from 'react';
import { useAppState } from '../../contexts/AppContext';
import { CalendarIcon } from '../icons';
import { getLocalDateString, parseDateStringAsLocal } from '../../utils/dateUtils';

const formatCountdown = (days: number): string => {
    if (days < 0) return 'Pasado';
    if (days === 0) return 'Hoy';
    if (days === 1) return '1 día';
    if (days < 7) return `${days} días`;
    const months = Math.floor(days / 30);
    const remainingDays = days % 30;
    if (months === 0) return `${days} días`;
    if (remainingDays === 0) return `${months} ${months === 1 ? 'mes' : 'meses'}`;
    return `${months}m ${remainingDays}d`;
};

export const KeyDatesWidget: React.FC<{
    onNavigate?: () => void;
}> = ({ onNavigate }) => {
    const { programs, activeProgramState, settings } = useAppState();
    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);
    const startDate = activeProgramState?.startDate;
    const targetDate = settings?.userVitals?.targetDate;

    const isCyclic = useMemo(() =>
        activeProgram?.structure === 'simple' ||
        (!activeProgram?.structure && (activeProgram?.macrocycles?.length ?? 0) === 1 &&
            ((activeProgram?.macrocycles?.[0]?.blocks?.length ?? 0) <= 1)),
    [activeProgram]);

    const keyDates = useMemo(() => {
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const items: { title: string; daysLeft: number; dateStr?: string; isCyclic?: boolean }[] = [];

        // 1. targetDate de userVitals (meta peso, etc.)
        if (targetDate && /^\d{4}-\d{2}-\d{2}$/.test(targetDate)) {
            const target = parseDateStringAsLocal(targetDate);
            target.setHours(0, 0, 0, 0);
            const daysLeft = Math.ceil((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
            if (daysLeft >= 0) {
                items.push({ title: 'Meta peso', daysLeft, dateStr: targetDate });
            }
        }

        if (!activeProgram || !startDate) return items;

        if (isCyclic) {
            // 2. Programa cíclico: próximo evento cíclico
            const events = activeProgram.events || [];
            const cycleWeeks = activeProgram.macrocycles?.[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length ?? 4;
            const cycleLengthDays = cycleWeeks * 7;

            const start = new Date(startDate);
            start.setHours(0, 0, 0, 0);
            const daysSinceStart = Math.floor((today.getTime() - start.getTime()) / (1000 * 60 * 60 * 24));
            const currentCycle = Math.floor(daysSinceStart / cycleLengthDays);
            const daysIntoCurrentCycle = daysSinceStart % cycleLengthDays;

            events.forEach((ev: any) => {
                const repeat = ev.repeatEveryXCycles ?? 1;
                const nextEventCycle = Math.ceil((currentCycle + 1) / repeat) * repeat;
                const daysUntilNext = (nextEventCycle - currentCycle) * cycleLengthDays - daysIntoCurrentCycle;

                if (daysUntilNext > 0) {
                    items.push({
                        title: ev.title || 'Evento',
                        daysLeft: daysUntilNext,
                        isCyclic: true,
                    });
                }
            });
        } else {
            // 3. Programa complejo: fechas de eventos
            const events = activeProgram.events || [];
            const start = new Date(startDate);
            start.setHours(0, 0, 0, 0);

            // Construir semanas en orden para calculatedWeek
            let weekOffset = 0;
            const weekMap: number[] = [];
            activeProgram.macrocycles?.forEach(macro => {
                (macro.blocks || []).forEach(block => {
                    (block.mesocycles || []).forEach(meso => {
                        (meso.weeks || []).forEach(() => {
                            weekMap.push(weekOffset++);
                        });
                    });
                });
            });

            events.forEach((ev: any) => {
                let eventDate: Date;
                if (ev.date && /^\d{4}-\d{2}-\d{2}$/.test(ev.date)) {
                    eventDate = parseDateStringAsLocal(ev.date);
                } else {
                    const calcWeek = ev.calculatedWeek ?? 0;
                    eventDate = new Date(start);
                    eventDate.setDate(eventDate.getDate() + calcWeek * 7);
                }
                eventDate.setHours(0, 0, 0, 0);
                const daysLeft = Math.ceil((eventDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
                if (daysLeft >= 0) {
                    items.push({
                        title: ev.title || 'Evento',
                        daysLeft,
                        dateStr: getLocalDateString(eventDate),
                    });
                }
            });
        }

        return items.sort((a, b) => a.daysLeft - b.daysLeft);
    }, [activeProgram, startDate, isCyclic, targetDate]);

    if (keyDates.length === 0) {
        return (
            <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl p-4">
                <div className="flex justify-between items-center mb-3">
                    <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                        <CalendarIcon size={10} className="text-violet-400" /> Fechas Clave
                    </span>
                </div>
                <p className="text-[10px] text-zinc-500 font-mono">
                    {!activeProgram ? 'Activa un programa' : 'Sin fechas clave configuradas'}
                </p>
            </div>
        );
    }

    return (
        <div className="w-full bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden">
            <div className="flex justify-between items-center p-4 pb-2">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.2em] flex items-center gap-2">
                    <CalendarIcon size={10} className="text-violet-400" /> Fechas Clave
                </span>
            </div>
            <div className="max-h-[180px] overflow-y-auto custom-scrollbar px-4 pb-4">
                <div className="space-y-2">
                    {keyDates.slice(0, 5).map((item, i) => (
                        <div
                            key={`${item.title}-${i}`}
                            className="flex items-center justify-between py-2.5 px-3 rounded-lg bg-black/30 border border-white/5"
                        >
                            <div className="min-w-0 flex-1">
                                <span className="text-[10px] font-bold text-white truncate block">{item.title}</span>
                                {item.dateStr && item.daysLeft > 0 && (
                                    <span className="text-[8px] text-zinc-500 font-mono">{(item.dateStr)}</span>
                                )}
                            </div>
                            <div className="shrink-0 ml-2">
                                <span className={`text-xs font-black font-mono ${
                                    item.daysLeft === 0 ? 'text-amber-400' :
                                    item.daysLeft === 1 ? 'text-emerald-400' :
                                    item.daysLeft === 2 ? 'text-emerald-400' :
                                    'text-violet-400'
                                }`}>
                                    {formatCountdown(item.daysLeft)}
                                </span>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};
