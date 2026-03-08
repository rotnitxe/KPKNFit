// components/home/SessionTodayCard.tsx
import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program, Session, WorkoutLog } from '../../types';
import { PlayIcon, CheckCircleIcon, PauseIcon, ChevronRightIcon, ChevronLeftIcon } from '../icons';
import { CaupolicanIcon } from '../CaupolicanIcon';
import { useAppState } from '../../contexts/AppContext';

export interface TodaySessionItem {
    session: Session;
    program: Program;
    location: { macroIndex: number; mesoIndex: number; weekId: string };
    isCompleted: boolean;
    dayOfWeek?: number;
    log?: WorkoutLog;
}

interface SessionTodayCardProps {
    programName: string;
    programId: string;
    sessions: TodaySessionItem[];
    ongoingWorkout?: { session: Session; programId: string; isPaused?: boolean } | null;
    onStartWorkout: (session: Session, program: Program, _?: unknown, ctx?: { macroIndex: number; mesoIndex: number; weekId: string }) => void;
    onResumeWorkout?: () => void;
    onOpenStartWorkoutModal: () => void;
    currentDayOfWeek: number;
}

export const SessionTodayCard: React.FC<SessionTodayCardProps> = ({
    programName,
    sessions,
    ongoingWorkout,
    onStartWorkout,
    onResumeWorkout,
    onOpenStartWorkoutModal,
    currentDayOfWeek
}) => {
    const [activeIndex, setActiveIndex] = useState(0);

    const hasSessions = sessions.length > 0;

    const handleStart = (ts: TodaySessionItem) => {
        const isToday = ts.dayOfWeek === currentDayOfWeek;
        const isRestDay = !sessions.some(s => s.dayOfWeek === currentDayOfWeek);
        const dayNames = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
        const dayName = dayNames[(ts.dayOfWeek || 1) - 1];

        if (isRestDay) {
            if (window.confirm("¿Estás seguro que quieres entrenar en tu día de descanso? Recuerda que el descanso es esencial para el progreso.")) {
                onStartWorkout(ts.session, ts.program, undefined, ts.location);
            }
            return;
        }

        if (!isToday) {
            if (window.confirm(`Esta sesión está programada para el ${dayName}, ¿estás seguro que quieres iniciarla?`)) {
                onStartWorkout(ts.session, ts.program, undefined, ts.location);
            }
            return;
        }

        onStartWorkout(ts.session, ts.program, undefined, ts.location);
    };

    if (!hasSessions) {
        return (
            <div className="px-6 w-full max-w-md mx-auto">
                <div onClick={onOpenStartWorkoutModal} className="w-full h-48 rounded-[32px] bg-gradient-to-br from-[#ECE6F0] to-[#D0BCFF] flex flex-col items-center justify-center gap-4 cursor-pointer shadow-xl shadow-black/5 active:scale-[0.98] transition-all">
                    <CaupolicanIcon size={48} className="text-[#49454F] opacity-30" />
                    <div className="text-center">
                        <div className="text-[#1D1B20] text-lg font-black uppercase tracking-widest">Día de descanso</div>
                        <div className="text-[#49454F] text-xs font-medium opacity-60">Configura un programa para hoy</div>
                    </div>
                </div>
            </div>
        );
    }

    const currentSession = sessions[activeIndex];
    const isOngoing = ongoingWorkout && ongoingWorkout.programId === currentSession.program.id && ongoingWorkout.session.id === currentSession.session.id;
    const coverImage = currentSession.program.coverImage;

    return (
        <div className="w-full flex flex-col items-center gap-4 overflow-visible px-6">
            <div className="relative w-full max-w-[420px] aspect-[16/9] rounded-[40px] overflow-hidden shadow-2xl shadow-black/10 group">
                <AnimatePresence mode="wait">
                    <motion.div
                        key={currentSession.session.id}
                        initial={{ opacity: 0, scale: 1.05 }}
                        animate={{ opacity: 1, scale: 1 }}
                        exit={{ opacity: 0, scale: 0.95 }}
                        transition={{ duration: 0.4 }}
                        className="absolute inset-0"
                    >
                        {coverImage ? (
                            <img src={coverImage} className="w-full h-full object-cover" alt="Session Cover" />
                        ) : (
                            <div className="absolute inset-0 bg-gradient-to-tr from-[#6750A4] via-[#D0BCFF] to-[#FEF7FF] opacity-80" />
                        )}
                        <div className="absolute inset-0 bg-black/30 backdrop-blur-[2px]" />

                        <div className="absolute inset-0 p-8 flex flex-col justify-end">
                            <div className="flex justify-between items-end">
                                <div className="flex flex-col gap-1 pr-4 max-w-[70%]">
                                    <div className="text-white/60 text-[10px] font-black uppercase tracking-[0.2em]">
                                        {programName}
                                    </div>
                                    <div className="text-white text-2xl font-black leading-tight truncate">
                                        {currentSession.session.name}
                                    </div>
                                </div>

                                <button
                                    onClick={isOngoing ? onResumeWorkout : () => handleStart(currentSession)}
                                    className="w-16 h-16 rounded-full bg-white flex items-center justify-center shadow-2xl active:scale-90 transition-transform"
                                >
                                    {isOngoing && !ongoingWorkout?.isPaused ? (
                                        <div className="flex gap-1.5">
                                            <div className="w-1.5 h-6 bg-black rounded-full" />
                                            <div className="w-1.5 h-6 bg-black rounded-full" />
                                        </div>
                                    ) : (
                                        <PlayIcon size={28} className="text-black ml-1.5" fill="currentColor" />
                                    )}
                                </button>
                            </div>
                        </div>
                    </motion.div>
                </AnimatePresence>

                {/* Badge for Day */}
                <div className="absolute top-6 left-6 px-4 py-2 bg-white/10 backdrop-blur-md rounded-full border border-white/20 text-white text-[10px] font-black uppercase tracking-widest">
                    {currentSession.dayOfWeek === currentDayOfWeek ? 'Sesión de hoy' : 'Próxima sesión'}
                </div>
            </div>

            {/* Pagination / Arrows */}
            {sessions.length > 1 && (
                <div className="flex items-center gap-6">
                    <button
                        onClick={() => setActiveIndex(prev => (prev > 0 ? prev - 1 : sessions.length - 1))}
                        className="w-10 h-10 rounded-full bg-black/[0.03] flex items-center justify-center text-[#49454F] active:scale-90 transition-transform"
                    >
                        <ChevronLeftIcon size={20} />
                    </button>
                    <div className="flex gap-1.5">
                        {sessions.map((_, i) => (
                            <div key={i} className={`h-1.5 rounded-full transition-all duration-300 ${i === activeIndex ? 'w-6 bg-primary' : 'w-1.5 bg-black/10'}`} />
                        ))}
                    </div>
                    <button
                        onClick={() => setActiveIndex(prev => (prev < sessions.length - 1 ? prev + 1 : 0))}
                        className="w-10 h-10 rounded-full bg-black/[0.03] flex items-center justify-center text-[#49454F] active:scale-90 transition-transform"
                    >
                        <ChevronRightIcon size={20} />
                    </button>
                </div>
            )}
        </div>
    );
};
