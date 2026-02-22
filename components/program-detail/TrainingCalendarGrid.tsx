import React, { useState, useMemo } from 'react';
import { Program, Session, ProgramWeek, ExerciseMuscleInfo } from '../../types';
import { ChevronDownIcon, PlusIcon, PlayIcon, EditIcon, TrashIcon, DumbbellIcon, CalendarIcon, ActivityIcon } from '../icons';
import { getAbsoluteWeekIndex } from '../../utils/programHelpers';

interface RoadmapBlock {
    id: string; name: string; macroIndex: number; blockIndex: number; totalWeeks: number; mesocycles: any[];
}

interface TrainingCalendarGridProps {
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
    onOpenStructureDrawer: () => void;
    onStartWorkout: (session: Session, program: Program, _?: any, ctx?: any) => void;
    onEditSession: (session: Session) => void;
    onDeleteSession: (sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    onAddSession?: (programId: string, macroIndex: number, mesoIndex: number, weekId: string, dayOfWeek: number) => void;
    addToast: (msg: string, type: 'success' | 'danger' | 'achievement' | 'suggestion') => void;
}

const DAY_NAMES_SHORT = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

const TrainingCalendarGrid: React.FC<TrainingCalendarGridProps> = ({
    program, isCyclic, roadmapBlocks, currentWeeks, selectedBlockId,
    selectedWeekId, activeBlockId, settings, exerciseList, history,
    onSelectBlock, onSelectWeek, onOpenSplitChanger, onOpenStructureDrawer,
    onStartWorkout, onEditSession, onDeleteSession, onAddSession, addToast,
}) => {
    const [showCyclicHistory, setShowCyclicHistory] = useState(false);

    const selectedBlock = useMemo(() => roadmapBlocks.find(b => b.id === selectedBlockId), [roadmapBlocks, selectedBlockId]);
    const selectedWeekIndex = useMemo(() => currentWeeks.findIndex(w => w.id === selectedWeekId), [currentWeeks, selectedWeekId]);
    const selectedWeek = useMemo(() => currentWeeks.find(w => w.id === selectedWeekId), [currentWeeks, selectedWeekId]);

    const programLogs = useMemo(() =>
        history.filter((log: any) => log.programId === program.id),
    [history, program.id]);

    const startOn = program.startDay ?? settings?.startWeekOn ?? 1;
    const orderedDays = useMemo(() => {
        const days: number[] = [];
        for (let i = 0; i < 7; i++) days.push((startOn + i) % 7);
        return days;
    }, [startOn]);

    const sessionsByDay = useMemo(() => {
        if (!selectedWeek) return new Map<number, Session[]>();
        const map = new Map<number, Session[]>();
        orderedDays.forEach(d => map.set(d, []));
        selectedWeek.sessions.forEach(s => {
            const day = s.dayOfWeek ?? 0;
            if (!map.has(day)) map.set(day, []);
            map.get(day)!.push(s);
        });
        return map;
    }, [selectedWeek, orderedDays]);

    const completedSessionIds = useMemo(() => {
        const set = new Set<string>();
        programLogs.forEach((l: any) => set.add(l.sessionId));
        return set;
    }, [programLogs]);

    const weekEvent = useMemo(() => {
        if (!selectedBlock || !selectedWeek) return null;
        const absIdx = getAbsoluteWeekIndex(program, selectedBlock.id, selectedWeek.id);
        const events = program.events || [];
        const cycleLength = program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length || 1;
        return events.find(e => {
            if (e.repeatEveryXCycles) return ((absIdx + 1) % (e.repeatEveryXCycles * cycleLength)) === 0;
            return e.calculatedWeek === absIdx;
        }) || null;
    }, [program, selectedBlock, selectedWeek]);

    const navigateWeek = (dir: -1 | 1) => {
        const newIdx = selectedWeekIndex + dir;
        if (newIdx >= 0 && newIdx < currentWeeks.length) {
            onSelectWeek(currentWeeks[newIdx].id);
        }
    };

    return (
        <div className="flex flex-col h-full">
            {/* Header toolbar */}
            <div className="px-4 py-3 border-b border-white/5 space-y-2.5 shrink-0">
                {/* Block selector (non-cyclic) */}
                {!isCyclic && roadmapBlocks.length > 1 && (
                    <div className="flex gap-1.5 overflow-x-auto no-scrollbar">
                        {roadmapBlocks.map(block => (
                            <button
                                key={block.id}
                                onClick={() => onSelectBlock(block.id)}
                                className={`px-3 py-1.5 rounded-lg text-[11px] font-bold whitespace-nowrap transition-all ${
                                    block.id === selectedBlockId
                                        ? 'bg-[#FC4C02] text-white shadow-[0_0_15px_rgba(252,76,2,0.3)]'
                                        : block.id === activeBlockId
                                            ? 'bg-white/10 text-white'
                                            : 'bg-white/5 text-[#8E8E93] hover:text-white'
                                }`}
                            >
                                {block.name}
                            </button>
                        ))}
                    </div>
                )}

                {/* Week nav + actions */}
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <button onClick={() => navigateWeek(-1)} disabled={selectedWeekIndex <= 0} className="w-7 h-7 rounded-lg bg-white/5 flex items-center justify-center text-[#8E8E93] hover:text-white disabled:opacity-20 transition-all">
                            <ChevronDownIcon size={14} className="rotate-90" />
                        </button>
                        <span className="text-sm font-bold text-white">
                            Semana {selectedWeekIndex + 1} <span className="text-[#48484A]">de {currentWeeks.length}</span>
                        </span>
                        <button onClick={() => navigateWeek(1)} disabled={selectedWeekIndex >= currentWeeks.length - 1} className="w-7 h-7 rounded-lg bg-white/5 flex items-center justify-center text-[#8E8E93] hover:text-white disabled:opacity-20 transition-all">
                            <ChevronDownIcon size={14} className="-rotate-90" />
                        </button>
                    </div>
                    <div className="flex items-center gap-2">
                        {isCyclic && (
                            <button onClick={() => setShowCyclicHistory(!showCyclicHistory)} className={`px-2.5 py-1 rounded-lg text-[10px] font-bold transition-all ${showCyclicHistory ? 'bg-[#FC4C02] text-white' : 'bg-white/5 text-[#8E8E93] hover:text-white'}`}>
                                {showCyclicHistory ? 'Historial' : 'Rutina'}
                            </button>
                        )}
                        <button onClick={onOpenSplitChanger} className="px-2.5 py-1 rounded-lg bg-white/5 text-[10px] font-bold text-[#8E8E93] hover:text-white transition-colors">
                            Split
                        </button>
                        <button onClick={onOpenStructureDrawer} className="w-7 h-7 rounded-lg bg-white/5 flex items-center justify-center text-[#8E8E93] hover:text-white transition-colors">
                            <ActivityIcon size={14} />
                        </button>
                    </div>
                </div>
            </div>

            {/* Event banner */}
            {weekEvent && (
                <div className="mx-4 mt-2 px-3 py-2 rounded-lg bg-[#FC4C02]/10 border border-[#FC4C02]/20 flex items-center gap-2">
                    <CalendarIcon size={14} className="text-[#FC4C02] shrink-0" />
                    <span className="text-xs font-bold text-[#FC4C02]">{weekEvent.title}</span>
                </div>
            )}

            {/* Calendar grid */}
            {!showCyclicHistory ? (
                <div className="flex-1 overflow-y-auto px-4 py-3">
                    <div className="grid grid-cols-7 gap-1.5 min-h-0">
                        {/* Day headers */}
                        {orderedDays.map(dayIdx => {
                            const sessions = sessionsByDay.get(dayIdx) || [];
                            const isTrainingDay = sessions.length > 0;
                            return (
                                <div key={`header-${dayIdx}`} className="text-center pb-2">
                                    <span className={`text-[10px] font-bold uppercase tracking-wide ${isTrainingDay ? 'text-white' : 'text-[#48484A]'}`}>
                                        {DAY_NAMES_SHORT[dayIdx]}
                                    </span>
                                    <div className={`w-1.5 h-1.5 rounded-full mx-auto mt-1 ${isTrainingDay ? 'bg-[#FC4C02]' : 'bg-[#1a1a1a]'}`} />
                                </div>
                            );
                        })}

                        {/* Day columns with sessions */}
                        {orderedDays.map(dayIdx => {
                            const sessions = sessionsByDay.get(dayIdx) || [];
                            return (
                                <div key={`col-${dayIdx}`} className="min-h-[120px] flex flex-col gap-1.5">
                                    {sessions.length > 0 ? sessions.map((session, sIdx) => {
                                        const exerciseCount = (session.exercises || []).length;
                                        const estimatedMin = exerciseCount * 8;
                                        const isCompleted = completedSessionIds.has(session.id);
                                        return (
                                            <CalendarSessionBlock
                                                key={session.id}
                                                session={session}
                                                exerciseCount={exerciseCount}
                                                estimatedMin={estimatedMin}
                                                isCompleted={isCompleted}
                                                onStart={() => onStartWorkout(session, program)}
                                                onEdit={() => onEditSession(session)}
                                                onDelete={() => {
                                                    if (selectedBlock && selectedWeek) {
                                                        onDeleteSession(session.id, program.id, selectedBlock.macroIndex, selectedWeek.mesoIndex, selectedWeek.id);
                                                    }
                                                }}
                                            />
                                        );
                                    }) : (
                                        <button
                                            onClick={() => {
                                                if (onAddSession && selectedBlock && selectedWeek) {
                                                    onAddSession(program.id, selectedBlock.macroIndex, selectedWeek.mesoIndex, selectedWeek.id, dayIdx);
                                                }
                                            }}
                                            className="flex-1 rounded-lg border border-dashed border-white/5 flex items-center justify-center text-[#48484A] hover:text-[#8E8E93] hover:border-white/10 transition-all group"
                                        >
                                            <PlusIcon size={14} className="opacity-0 group-hover:opacity-100 transition-opacity" />
                                        </button>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>
            ) : (
                /* Cyclic history view */
                <div className="flex-1 overflow-y-auto px-4 py-3 space-y-2">
                    {programLogs.length > 0 ? programLogs.slice(0, 20).map((log: any, idx: number) => (
                        <div key={idx} className="flex items-center gap-3 px-3 py-2.5 rounded-lg bg-[#111] border border-white/5">
                            <div className="w-8 h-8 rounded-lg bg-[#00F19F]/10 flex items-center justify-center shrink-0">
                                <DumbbellIcon size={14} className="text-[#00F19F]" />
                            </div>
                            <div className="flex-1 min-w-0">
                                <span className="text-xs font-bold text-white block truncate">{log.sessionName || 'Sesión'}</span>
                                <span className="text-[10px] text-[#48484A]">{new Date(log.date).toLocaleDateString('es', { day: 'numeric', month: 'short' })}</span>
                            </div>
                            <span className="text-[10px] font-bold text-[#8E8E93]">{log.exercises?.length || 0} ej.</span>
                        </div>
                    )) : (
                        <div className="text-center py-12">
                            <DumbbellIcon size={24} className="text-[#48484A] mx-auto mb-2" />
                            <p className="text-xs text-[#48484A] font-bold">Sin historial aún</p>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

/* ── Session block inside calendar column ── */
const CalendarSessionBlock: React.FC<{
    session: Session;
    exerciseCount: number;
    estimatedMin: number;
    isCompleted: boolean;
    onStart: () => void;
    onEdit: () => void;
    onDelete: () => void;
}> = ({ session, exerciseCount, estimatedMin, isCompleted, onStart, onEdit, onDelete }) => {
    const [hovered, setHovered] = useState(false);

    return (
        <div
            className={`relative rounded-lg p-2 transition-all cursor-pointer group ${
                isCompleted
                    ? 'bg-[#00F19F]/10 border border-[#00F19F]/20'
                    : 'bg-[#1a1a1a] border border-white/5 hover:border-[#FC4C02]/30'
            }`}
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
            onClick={onEdit}
        >
            {/* Completion bar */}
            {isCompleted && (
                <div className="absolute top-0 left-0 w-full h-0.5 rounded-t-lg bg-[#00F19F]" />
            )}

            <span className="text-[10px] font-bold text-white block truncate leading-tight">
                {session.name}
            </span>
            <span className="text-[9px] text-[#48484A] block mt-0.5">
                {exerciseCount} ej · {estimatedMin}m
            </span>

            {/* Hover actions */}
            {hovered && (
                <div className="absolute inset-0 rounded-lg bg-black/80 flex items-center justify-center gap-1 z-10">
                    <button onClick={e => { e.stopPropagation(); onStart(); }} className="w-6 h-6 rounded bg-[#FC4C02] flex items-center justify-center hover:brightness-110 transition-all" title="Iniciar">
                        <PlayIcon size={10} fill="white" />
                    </button>
                    <button onClick={e => { e.stopPropagation(); onEdit(); }} className="w-6 h-6 rounded bg-white/10 flex items-center justify-center hover:bg-white/20 transition-colors" title="Editar">
                        <EditIcon size={10} />
                    </button>
                    <button onClick={e => { e.stopPropagation(); onDelete(); }} className="w-6 h-6 rounded bg-white/10 flex items-center justify-center hover:bg-red-500/20 hover:text-red-400 transition-colors" title="Eliminar">
                        <TrashIcon size={10} />
                    </button>
                </div>
            )}
        </div>
    );
};

export default TrainingCalendarGrid;
