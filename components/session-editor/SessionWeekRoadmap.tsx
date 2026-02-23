import React from 'react';
import { Session } from '../../types';

interface DayInfo {
    label: string;
    value: number;
}

interface SessionWeekRoadmapProps {
    orderedDays: DayInfo[];
    weekSessions: Session[];
    activeSessionId: string;
    emptyDaySelected: number | null;
    modifiedSessionIds: Set<string>;
    onDayClick: (dayValue: number, daySessions: Session[]) => void;
    onSessionSelect: (sessionId: string) => void;
    session: Session;
}

const SessionWeekRoadmap: React.FC<SessionWeekRoadmapProps> = ({
    orderedDays, weekSessions, activeSessionId, emptyDaySelected,
    modifiedSessionIds, onDayClick, onSessionSelect, session
}) => {
    return (
        <div className="flex flex-col border-b border-white/5 flex-shrink-0 bg-black">
            <div className="px-5 py-4 flex items-center justify-between relative overflow-hidden">
                <div className="absolute top-1/2 left-6 right-6 h-px bg-white/5 -translate-y-1/2 z-0"></div>
                {orderedDays.map(day => {
                    const daySessions = weekSessions.filter(s => s.dayOfWeek === day.value);
                    const isActive = daySessions.some(s => s.id === activeSessionId) || emptyDaySelected === day.value;
                    const hasSession = daySessions.length > 0;
                    const isModified = daySessions.some(s => modifiedSessionIds.has(s.id));

                    return (
                        <button
                            key={day.value}
                            onClick={() => onDayClick(day.value, daySessions)}
                            className="relative z-10 flex flex-col items-center gap-1.5 group outline-none"
                        >
                            <div className={`w-3.5 h-3.5 rounded-full border-[3px] transition-all duration-200 relative ${
                                isActive
                                    ? 'bg-white border-white scale-125 shadow-[0_0_10px_rgba(255,255,255,0.3)]'
                                    : hasSession
                                        ? 'bg-zinc-700 border-zinc-800 hover:bg-zinc-500'
                                        : 'bg-black border-zinc-800 hover:border-zinc-600'
                            }`}>
                                {isModified && !isActive && (
                                    <div className="absolute -top-1.5 -right-1.5 w-1.5 h-1.5 bg-cyber-cyan rounded-full"></div>
                                )}
                            </div>
                            <span className={`text-[8px] font-black uppercase tracking-wider transition-colors ${
                                isActive ? 'text-white' : hasSession ? 'text-zinc-600' : 'text-zinc-800'
                            }`}>
                                {day.label.slice(0, 2)}
                            </span>
                        </button>
                    );
                })}
            </div>

            {activeSessionId !== 'empty' && weekSessions.filter(s => s.dayOfWeek === session.dayOfWeek).length > 1 && (
                <div className="flex px-4 gap-1.5 overflow-x-auto hide-scrollbar pb-2">
                    {weekSessions.filter(s => s.dayOfWeek === session.dayOfWeek).map((s, idx) => (
                        <button
                            key={s.id}
                            onClick={() => onSessionSelect(s.id)}
                            className={`px-3 py-1 rounded-full text-[9px] font-bold uppercase whitespace-nowrap transition-colors border ${
                                activeSessionId === s.id
                                    ? 'bg-white text-black border-white'
                                    : 'bg-transparent text-zinc-600 border-zinc-800 hover:border-zinc-500'
                            }`}
                        >
                            {s.name || `Sesión ${idx + 1}`}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

export default SessionWeekRoadmap;
