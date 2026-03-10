import React, { useMemo } from 'react';
import { motion } from 'framer-motion';
import { Program, Session, ProgramWeek } from '../../types';
import { useAppContext } from '../../contexts/AppContext';
import { DumbbellIcon, PlayIcon, EditIcon, TrashIcon } from '../icons';

interface WeekViewProps {
    program: Program;
    selectedWeekId: string | null;
    onSelectWeek: (weekId: string) => void;
    currentWeekId?: string;
    onEditSession?: (sessionId: string) => void;
    onAddSession?: () => void;
    onDeleteSession?: (sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    onStartWorkout?: (session: Session) => void;
}

const WeekView: React.FC<WeekViewProps> = ({
    program,
    selectedWeekId,
    onSelectWeek,
    currentWeekId,
    onEditSession,
    onAddSession,
    onDeleteSession,
    onStartWorkout,
}) => {
    const { history, activeProgramState } = useAppContext();

    // Encontrar la semana seleccionada
    const selectedWeek: ProgramWeek | null = useMemo(() => {
        if (!selectedWeekId) return null;
        
        for (const macro of program.macrocycles) {
            for (const block of macro.blocks || []) {
                for (const meso of block.mesocycles) {
                    const week = meso.weeks.find(w => w.id === selectedWeekId);
                    if (week) return week;
                }
            }
        }
        return null;
    }, [program, selectedWeekId]);

    // Obtener logs de historial para esta semana
    const weekLogs = useMemo(() => {
        if (!selectedWeek) return [];
        const weekSessionIds = new Set(selectedWeek.sessions.map(s => s.id));
        return history.filter((log: any) => weekSessionIds.has(log.sessionId));
    }, [selectedWeek, history]);

    // Calcular adherencia de la semana
    const weekAdherence = useMemo(() => {
        if (!selectedWeek || selectedWeek.sessions.length === 0) return 0;
        const completedSessions = new Set(weekLogs.map((l: any) => l.sessionId)).size;
        return Math.round((completedSessions / selectedWeek.sessions.length) * 100);
    }, [selectedWeek, weekLogs]);

    if (!selectedWeek) {
        return (
            <div className="px-4 py-8 text-center">
                <p className="text-sm text-zinc-500">Selecciona una semana para ver los detalles</p>
            </div>
        );
    }

    return (
        <div className="pb-6">
            {/* Header de la semana */}
            <div className="mt-0 px-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h3 className="text-lg font-black text-zinc-900">
                            {selectedWeek.name || `Semana ${selectedWeek.id.slice(-2)}`}
                        </h3>
                        <p className="text-[11px] text-zinc-500 mt-0.5">
                            {selectedWeek.sessions.length} sesiones programadas
                        </p>
                    </div>
                </div>
            </div>

            {/* Lista de sesiones */}
            <div className="mt-3 px-4">
                <h4 className="text-[10px] font-black uppercase tracking-[0.25em] text-zinc-400 mb-3">
                    Sesiones
                </h4>
                
                <div className="space-y-2">
                    {selectedWeek.sessions.map((session, idx) => {
                        const isCompleted = weekLogs.some((l: any) => l.sessionId === session.id);
                        const isToday = false; // Could be calculated based on program start date
                        
                        return (
                            <motion.div
                                key={session.id}
                                initial={{ opacity: 0, y: 10 }}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ delay: idx * 0.05 }}
                                className={`
                                    relative bg-white rounded-2xl border p-4 shadow-sm
                                    ${isCompleted ? 'border-emerald-200 bg-emerald-50/30' : 'border-zinc-200'}
                                `}
                            >
                                {isCompleted && (
                                    <div className="absolute top-3 right-3 w-5 h-5 rounded-full bg-emerald-500 flex items-center justify-center">
                                        <svg className="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                                        </svg>
                                    </div>
                                )}
                                
                                <div className="flex items-start gap-3">
                                    {/* Día de la semana */}
                                    <div className={`
                                        w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0
                                        ${isCompleted ? 'bg-emerald-500 text-white' : 'bg-zinc-100 text-zinc-600'}
                                    `}>
                                        <span className="text-[10px] font-black">D{idx + 1}</span>
                                    </div>
                                    
                                    {/* Info de la sesión */}
                                    <div className="flex-1 min-w-0">
                                        <h5 className="text-sm font-bold text-zinc-900 truncate">
                                            {session.name || `Sesión ${idx + 1}`}
                                        </h5>
                                        <p className="text-[11px] text-zinc-500 mt-0.5">
                                            {session.exercises?.length || 0} ejercicios
                                        </p>
                                        
                                        {/* Tags */}
                                        <div className="flex items-center gap-1.5 mt-2">
                                            {session.focus && (
                                                <span className="px-2 py-0.5 rounded-full bg-purple-100 text-purple-700 text-[8px] font-black uppercase tracking-[0.2em]">
                                                    {session.focus}
                                                </span>
                                            )}
                                            {isCompleted && (
                                                <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 text-[8px] font-black uppercase tracking-[0.2em]">
                                                    Completada
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                </div>
                                
                                {/* Acciones */}
                                <div className="flex items-center gap-2 mt-3 pt-3 border-t border-zinc-100">
                                    {!isCompleted && onStartWorkout && (
                                        <button
                                            onClick={() => onStartWorkout(session)}
                                            className="flex-1 h-9 rounded-xl bg-black text-white text-[9px] font-black uppercase tracking-[0.15em] flex items-center justify-center gap-2 hover:bg-zinc-800 transition-colors"
                                        >
                                            <PlayIcon size={14} />
                                            Iniciar
                                        </button>
                                    )}
                                    {onEditSession && (
                                        <button
                                            onClick={() => onEditSession(session.id)}
                                            className="w-9 h-9 rounded-xl bg-zinc-100 text-zinc-600 flex items-center justify-center hover:bg-zinc-200 transition-colors"
                                        >
                                            <EditIcon size={16} />
                                        </button>
                                    )}
                                    {onDeleteSession && (
                                        <button
                                            onClick={() => onDeleteSession(session.id, program.id, 0, 0, selectedWeek.id)}
                                            className="w-9 h-9 rounded-xl bg-red-50 text-red-500 flex items-center justify-center hover:bg-red-100 transition-colors"
                                        >
                                            <TrashIcon size={16} />
                                        </button>
                                    )}
                                </div>
                            </motion.div>
                        );
                    })}
                </div>
                
                {/* Botón añadir sesión */}
                {onAddSession && (
                    <button
                        onClick={onAddSession}
                        className="w-full mt-3 h-12 rounded-2xl border-2 border-dashed border-zinc-300 text-zinc-500 text-[10px] font-black uppercase tracking-[0.2em] hover:border-zinc-400 hover:text-zinc-600 transition-colors flex items-center justify-center gap-2"
                    >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                        </svg>
                        Añadir Sesión
                    </button>
                )}
            </div>
        </div>
    );
};

export default WeekView;
