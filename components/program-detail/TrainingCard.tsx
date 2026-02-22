import React, { useState, useMemo } from 'react';
import { Program, Session, ProgramWeek, ExerciseMuscleInfo } from '../../types';
import {
    CalendarIcon, ActivityIcon, PlusIcon, SettingsIcon, ChevronDownIcon,
} from '../icons';
import { getOrderedDaysOfWeek } from '../../utils/calculations';
import { getAbsoluteWeekIndex, checkWeekHasEvent } from '../../utils/programHelpers';
import SessionCard from './SessionCard';

interface RoadmapBlock {
    id: string;
    name: string;
    macroIndex: number;
    blockIndex: number;
    totalWeeks: number;
    mesocycles: any[];
}

interface TrainingCardProps {
    program: Program;
    isCyclic: boolean;
    roadmapBlocks: RoadmapBlock[];
    currentWeeks: (ProgramWeek & { mesoIndex: number })[];
    selectedBlockId: string | null;
    selectedWeekId: string | null;
    activeBlockId: string | null;
    settings: any;
    exerciseList: ExerciseMuscleInfo[];
    history: any[];
    onSelectBlock: (id: string) => void;
    onSelectWeek: (id: string) => void;
    onOpenSplitChanger: () => void;
    onStartWorkout: (session: Session, program: Program, _?: any, ctx?: any) => void;
    onEditSession: (session: Session) => void;
    onDeleteSession: (sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    onAddSession?: (programId: string, macroIndex: number, mesoIndex: number, weekId: string, dayOfWeek: number) => void;
    onUpdateProgram?: (program: Program) => void;
    addToast: (msg: string, type: string) => void;
    collapsed?: boolean;
    onToggleCollapse?: () => void;
}

const DAY_NAMES = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

const TrainingCard: React.FC<TrainingCardProps> = ({
    program, isCyclic, roadmapBlocks, currentWeeks,
    selectedBlockId, selectedWeekId, activeBlockId, settings,
    exerciseList, history, onSelectBlock, onSelectWeek,
    onOpenSplitChanger, onStartWorkout, onEditSession, onDeleteSession,
    onAddSession, onUpdateProgram, addToast,
    collapsed = false, onToggleCollapse,
}) => {
    const [showCyclicHistory, setShowCyclicHistory] = useState(false);
    const [selectedEventId, setSelectedEventId] = useState<string | null>(null);

    const selectedBlock = useMemo(() => roadmapBlocks.find(b => b.id === selectedBlockId), [roadmapBlocks, selectedBlockId]);
    const selectedBlockIndex = useMemo(() => roadmapBlocks.findIndex(b => b.id === selectedBlockId), [roadmapBlocks, selectedBlockId]);
    const selectedWeekIndex = useMemo(() => currentWeeks.findIndex(w => w.id === selectedWeekId) + 1, [currentWeeks, selectedWeekId]);

    const programLogs = useMemo(() =>
        history.filter((log: any) => log.programId === program.id).sort((a: any, b: any) => new Date(b.date).getTime() - new Date(a.date).getTime()),
    [history, program.id]);

    const startOn = program.startDay ?? settings?.startWeekOn ?? 1;

    const renderDaySessions = (
        sessionsForWeek: Session[],
        weekData: { mesoIndex: number; id: string },
        macroIndex: number,
    ) => {
        const maxDayAssigned = sessionsForWeek.reduce((max: number, s: any) => Math.max(max, s.dayOfWeek || 0), 0);
        const isStandardWeek = maxDayAssigned <= 6;
        const daysArray = isStandardWeek
            ? getOrderedDaysOfWeek(startOn).map(d => d.value)
            : Array.from({ length: maxDayAssigned + 1 }, (_, i) => i);

        return (
            <div className="space-y-3">
                {daysArray.map(dayNum => {
                    const daySessions = sessionsForWeek.filter((s: any) => s.dayOfWeek === dayNum);
                    const dayTitle = isStandardWeek ? DAY_NAMES[dayNum] : `Día ${dayNum}`;
                    const dayAbbrev = isStandardWeek ? DAY_NAMES[dayNum].substring(0, 3).toUpperCase() : `D${dayNum}`;
                    const daySubtitle = daySessions.length > 0
                        ? (isStandardWeek ? 'Día de Entrenamiento' : 'Entrenamiento')
                        : (isStandardWeek ? 'Descanso / Recuperación' : 'Descanso');

                    return (
                        <div key={`day-${dayNum}`} className="bg-zinc-900/30 border border-white/5 rounded-2xl p-3 relative overflow-hidden">
                            <div className="flex items-center justify-between mb-3">
                                <div className="flex items-center gap-2.5">
                                    <div className={`w-9 h-9 rounded-lg flex items-center justify-center text-[10px] font-black border ${
                                        daySessions.length > 0
                                            ? 'bg-blue-900/20 border-blue-500/30 text-blue-400'
                                            : 'bg-black border-zinc-800 text-zinc-600'
                                    }`}>
                                        {dayAbbrev}
                                    </div>
                                    <div>
                                        <h4 className={`text-xs font-black uppercase tracking-wider ${daySessions.length > 0 ? 'text-white' : 'text-zinc-500'}`}>
                                            {dayTitle}
                                        </h4>
                                        <p className="text-[8px] text-zinc-500 font-bold uppercase tracking-widest">{daySubtitle}</p>
                                    </div>
                                </div>
                                {onAddSession && (
                                    <button
                                        onClick={() => onAddSession(program.id, macroIndex, weekData.mesoIndex, weekData.id, dayNum)}
                                        className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-black border border-white/10 text-[9px] font-black text-zinc-400 uppercase tracking-widest hover:bg-zinc-800 hover:text-white transition-all"
                                    >
                                        <PlusIcon size={12} />
                                    </button>
                                )}
                            </div>

                            {daySessions.length > 0 ? (
                                <div className="space-y-2">
                                    {daySessions.map((session: any, idx: number) => (
                                        <SessionCard
                                            key={session.id}
                                            session={session}
                                            index={idx}
                                            exerciseList={exerciseList}
                                            onStart={() => onStartWorkout(session, program, undefined, { macroIndex, mesoIndex: weekData.mesoIndex, weekId: weekData.id })}
                                            onEdit={() => onEditSession(session)}
                                            onDelete={() => {
                                                if (window.confirm('¿Eliminar esta sesión?')) {
                                                    onDeleteSession(session.id, program.id, macroIndex, weekData.mesoIndex, weekData.id);
                                                }
                                            }}
                                        />
                                    ))}
                                </div>
                            ) : (
                                onAddSession && (
                                    <button
                                        onClick={() => onAddSession(program.id, macroIndex, weekData.mesoIndex, weekData.id, dayNum)}
                                        className="w-full h-12 rounded-xl bg-black/50 border border-dashed border-white/10 flex items-center justify-center hover:bg-white/5 hover:border-white/30 transition-all group"
                                    >
                                        <PlusIcon size={14} className="text-zinc-600 group-hover:text-white mr-1.5 transition-colors" />
                                        <span className="text-[9px] font-bold text-zinc-600 group-hover:text-white uppercase tracking-widest transition-colors">Añadir</span>
                                    </button>
                                )
                            )}
                        </div>
                    );
                })}
            </div>
        );
    };

    return (
        <div className="bg-zinc-900/50 border border-white/5 rounded-2xl overflow-hidden transition-all duration-300">
            {/* Card Header */}
            <button onClick={onToggleCollapse} className="w-full flex items-center justify-between p-4 text-left">
                <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-blue-500/10 border border-blue-500/20 flex items-center justify-center">
                        <CalendarIcon size={16} className="text-blue-400" />
                    </div>
                    <div>
                        <h3 className="text-xs font-black text-white uppercase tracking-widest">Entrenamiento</h3>
                        <p className="text-[9px] text-zinc-500 font-bold">
                            {isCyclic
                                ? `${currentWeeks.length} semana${currentWeeks.length > 1 ? 's' : ''} cíclica${currentWeeks.length > 1 ? 's' : ''}`
                                : `${roadmapBlocks.length} bloques • Semana ${selectedWeekIndex}`
                            }
                        </p>
                    </div>
                </div>
                <ChevronDownIcon size={16} className={`text-zinc-500 transition-transform duration-300 ${collapsed ? '' : 'rotate-180'}`} />
            </button>

            {!collapsed && (
                <div className="px-4 pb-4 space-y-4 animate-fade-in">
                    {/* Actions bar */}
                    <div className="flex items-center justify-between">
                        {isCyclic && (
                            <button
                                onClick={() => setShowCyclicHistory(!showCyclicHistory)}
                                className="px-3 py-1.5 rounded-lg bg-blue-900/20 text-blue-400 text-[9px] font-black uppercase tracking-widest border border-blue-500/30 hover:bg-blue-600/40 transition-colors flex items-center gap-1.5"
                            >
                                <ActivityIcon size={12} />
                                {showCyclicHistory ? 'Rutinas' : 'Historial'}
                            </button>
                        )}
                        <button
                            onClick={onOpenSplitChanger}
                            className="px-3 py-1.5 rounded-lg bg-zinc-800 text-zinc-300 text-[9px] font-black uppercase tracking-widest border border-white/10 hover:bg-zinc-700 hover:text-white transition-colors flex items-center gap-1.5 ml-auto"
                        >
                            <SettingsIcon size={12} /> Split
                        </button>
                    </div>

                    {/* Block selector (non-cyclic) */}
                    {!isCyclic && roadmapBlocks.length > 0 && (
                        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1">
                            {roadmapBlocks.map((block, idx) => {
                                const isSelected = block.id === selectedBlockId;
                                const isCurrent = block.id === activeBlockId;
                                return (
                                    <button
                                        key={block.id}
                                        onClick={() => onSelectBlock(block.id)}
                                        className={`shrink-0 px-4 py-2 rounded-full text-[9px] font-black uppercase tracking-widest transition-all border flex items-center gap-1.5 ${
                                            isSelected
                                                ? 'bg-white text-black border-white shadow-[0_0_12px_rgba(255,255,255,0.15)]'
                                                : 'bg-zinc-900 border-white/10 text-zinc-500 hover:text-white hover:bg-zinc-800'
                                        }`}
                                    >
                                        {isCurrent && <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 shadow-[0_0_6px_rgba(16,185,129,0.8)]" />}
                                        B{idx + 1}
                                    </button>
                                );
                            })}
                        </div>
                    )}

                    {/* Week selector */}
                    {currentWeeks.length > 0 && (
                        <div className="flex gap-1.5 overflow-x-auto no-scrollbar">
                            {currentWeeks.map((week, wIdx) => {
                                const isWeekSelected = week.id === selectedWeekId;
                                const absIdx = selectedBlockId ? getAbsoluteWeekIndex(program, selectedBlockId, week.id) : -1;
                                const hasEvent = absIdx >= 0 && checkWeekHasEvent(program, absIdx);
                                return (
                                    <button
                                        key={week.id}
                                        onClick={() => { onSelectWeek(week.id); setSelectedEventId(null); }}
                                        className={`shrink-0 relative px-4 py-2 rounded-full text-[9px] font-black uppercase tracking-widest transition-all ${
                                            isWeekSelected && !selectedEventId
                                                ? 'bg-blue-600 text-white shadow-[0_0_12px_rgba(37,99,235,0.4)]'
                                                : 'text-zinc-500 hover:text-white hover:bg-zinc-800'
                                        }`}
                                    >
                                        {hasEvent && (
                                            <div className={`absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full ${isWeekSelected ? 'bg-yellow-400' : 'bg-yellow-500/50'}`} />
                                        )}
                                        {week.name || `S${wIdx + 1}`}
                                    </button>
                                );
                            })}
                            {isCyclic && (program.events || []).map(ev => (
                                <button
                                    key={ev.id}
                                    onClick={() => setSelectedEventId(ev.id || null)}
                                    className={`shrink-0 px-4 py-2 rounded-full text-[9px] font-black uppercase tracking-widest transition-all flex items-center gap-1 ${
                                        ev.id === selectedEventId
                                            ? 'bg-yellow-500 text-black shadow-[0_0_12px_rgba(234,179,8,0.5)]'
                                            : 'bg-yellow-900/20 border border-yellow-500/30 text-yellow-500'
                                    }`}
                                >
                                    <ActivityIcon size={10} /> {ev.title}
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Content */}
                    {showCyclicHistory && isCyclic ? (
                        <div className="space-y-3 animate-slide-up">
                            <div className="flex items-center gap-2 mb-3">
                                <div className="h-px bg-white/10 flex-1" />
                                <h4 className="text-[8px] font-black text-zinc-500 uppercase tracking-widest">Historial</h4>
                                <div className="h-px bg-white/10 flex-1" />
                            </div>
                            {programLogs.length > 0 ? programLogs.slice(0, 10).map(log => (
                                <div key={log.id} className="bg-zinc-900/40 border border-white/5 p-4 rounded-2xl relative overflow-hidden">
                                    <div className="absolute top-0 left-0 w-1 h-full bg-emerald-500" />
                                    <div className="flex justify-between items-start mb-2">
                                        <div>
                                            <div className="text-xs font-bold text-white">{log.sessionName || 'Sesión Completa'}</div>
                                            <div className="text-[9px] text-zinc-500">{new Date(log.date).toLocaleDateString()}</div>
                                        </div>
                                    </div>
                                    <div className="space-y-1 border-t border-white/5 pt-2">
                                        {(log.completedExercises || []).slice(0, 3).map((ex: any, i: number) => (
                                            <div key={i} className="flex justify-between items-center text-[10px]">
                                                <span className="text-zinc-400 truncate flex-1">{ex.exerciseName}</span>
                                                <span className="text-zinc-300 font-bold">{ex.sets?.length || 0}s</span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )) : (
                                <div className="text-center py-8 bg-zinc-900/20 rounded-2xl border border-dashed border-white/10">
                                    <p className="text-[10px] text-zinc-500">Sin historial.</p>
                                </div>
                            )}
                        </div>
                    ) : selectedEventId && isCyclic ? (
                        <div className="space-y-3 animate-fade-in">
                            {(() => {
                                const ev = program.events?.find(e => e.id === selectedEventId);
                                if (!ev) return null;
                                return (
                                    <>
                                        <div className="bg-yellow-900/10 border border-yellow-500/20 rounded-2xl p-4 text-center">
                                            <ActivityIcon size={20} className="text-yellow-500 mx-auto mb-1" />
                                            <h4 className="text-sm font-black text-white uppercase">{ev.title}</h4>
                                            <p className="text-[9px] text-zinc-400 mt-0.5">Cada {ev.repeatEveryXCycles} Ciclos</p>
                                        </div>
                                        {(ev.sessions || []).map((session: any, idx: number) => (
                                            <SessionCard
                                                key={session.id}
                                                session={session}
                                                index={idx}
                                                exerciseList={exerciseList}
                                                onStart={() => onStartWorkout(session, program)}
                                                onEdit={() => addToast('Edita sesiones de evento desde el Editor Avanzado', 'suggestion')}
                                                onDelete={() => {
                                                    if (window.confirm('¿Eliminar sesión del evento?')) {
                                                        const updated = JSON.parse(JSON.stringify(program));
                                                        const targetEv = updated.events.find((e: any) => e.id === selectedEventId);
                                                        if (targetEv) {
                                                            targetEv.sessions = targetEv.sessions.filter((s: any) => s.id !== session.id);
                                                            onUpdateProgram?.(updated);
                                                        }
                                                    }
                                                }}
                                            />
                                        ))}
                                        <button
                                            onClick={() => {
                                                const updated = JSON.parse(JSON.stringify(program));
                                                const targetEv = updated.events.find((e: any) => e.id === selectedEventId);
                                                if (targetEv) {
                                                    if (!targetEv.sessions) targetEv.sessions = [];
                                                    targetEv.sessions.push({ id: crypto.randomUUID(), name: 'Sesión del Evento', exercises: [] });
                                                    onUpdateProgram?.(updated);
                                                }
                                            }}
                                            className="w-full h-12 rounded-xl bg-black/50 border border-dashed border-yellow-500/20 flex items-center justify-center hover:bg-yellow-900/10 transition-all group"
                                        >
                                            <PlusIcon size={14} className="text-yellow-600 group-hover:text-yellow-400 mr-1.5" />
                                            <span className="text-[9px] font-bold text-yellow-600 group-hover:text-yellow-400 uppercase tracking-widest">Crear Sesión</span>
                                        </button>
                                    </>
                                );
                            })()}
                        </div>
                    ) : (
                        (() => {
                            const selectedWeek = currentWeeks.find(w => w.id === selectedWeekId) || currentWeeks[0];
                            if (!selectedWeek) return (
                                <div className="py-8 text-center border border-dashed border-white/10 rounded-2xl">
                                    <p className="text-[9px] text-zinc-500 font-black uppercase tracking-widest">Selecciona un bloque y semana</p>
                                </div>
                            );
                            return renderDaySessions(
                                selectedWeek.sessions || [],
                                selectedWeek,
                                selectedBlock?.macroIndex || 0,
                            );
                        })()
                    )}
                </div>
            )}
        </div>
    );
};

export default TrainingCard;
