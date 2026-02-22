import React, { useState, useMemo, useRef, useEffect } from 'react';
import { Program, Session, ProgramWeek, ExerciseMuscleInfo } from '../../types';
import { ChevronDownIcon, PlusIcon, PlayIcon, EditIcon, TrashIcon, DumbbellIcon, CalendarIcon, ActivityIcon } from '../icons';
import { getAbsoluteWeekIndex } from '../../utils/programHelpers';

const DAY_NAMES_SHORT = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

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

const TrainingCalendarGrid: React.FC<TrainingCalendarGridProps> = ({
    program, isCyclic, roadmapBlocks, currentWeeks, selectedBlockId,
    selectedWeekId, activeBlockId, settings, exerciseList, history,
    onSelectBlock, onSelectWeek, onOpenSplitChanger, onOpenStructureDrawer,
    onStartWorkout, onEditSession, onDeleteSession, onAddSession, addToast,
}) => {
    const [showCyclicHistory, setShowCyclicHistory] = useState(false);
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) setDropdownOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

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

    const getWeekLabel = (weekIdx: number) => {
        if (!isCyclic || currentWeeks.length !== 2) return `Semana ${weekIdx + 1}`;
        return weekIdx === 0 ? 'Semana A' : 'Semana B';
    };

    const weekHasEvent = useMemo(() => {
        const events = program.events || [];
        const cycleLength = program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length || 1;
        return (weekIdx: number) => {
            if (!selectedBlock) return false;
            const week = currentWeeks[weekIdx];
            if (!week) return false;
            const absIdx = getAbsoluteWeekIndex(program, selectedBlock.id, week.id);
            return events.some(e => {
                if (e.repeatEveryXCycles) return ((absIdx + 1) % (e.repeatEveryXCycles * cycleLength)) === 0;
                return e.calculatedWeek === absIdx;
            });
        };
    }, [program, selectedBlock, currentWeeks]);

    const navigateWeek = (dir: -1 | 1) => {
        const newIdx = selectedWeekIndex + dir;
        if (newIdx >= 0 && newIdx < currentWeeks.length) {
            onSelectWeek(currentWeeks[newIdx].id);
        }
    };

    const getSessionsByDayForWeek = (week: ProgramWeek & { mesoIndex: number }) => {
        const map = new Map<number, Session[]>();
        orderedDays.forEach(d => map.set(d, []));
        (week.sessions || []).forEach(s => {
            const day = s.dayOfWeek ?? 0;
            if (!map.has(day)) map.set(day, []);
            map.get(day)!.push(s);
        });
        return map;
    };

    return (
        <div className="flex flex-col h-full min-h-0">
            {/* Dropdown: Bloques + Filtros */}
            <div className="relative px-4 py-2.5 border-b border-white/5 shrink-0" ref={dropdownRef}>
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => setDropdownOpen(!dropdownOpen)}
                        className="flex-1 flex items-center justify-between px-3 py-2 rounded-lg bg-[#1a1a1a] border border-white/5 text-left"
                    >
                        <span className="text-[11px] font-bold text-white truncate">
                            {selectedBlock?.name || 'Bloque'}
                            {isCyclic && ` · ${showCyclicHistory ? 'Historial' : 'Rutina'}`}
                        </span>
                        <ChevronDownIcon size={16} className={`text-[#8E8E93] shrink-0 transition-transform ${dropdownOpen ? 'rotate-180' : ''}`} />
                    </button>
                </div>
                {dropdownOpen && (
                    <div className="mt-2 absolute left-4 right-4 z-30 bg-[#1a1a1a] border border-white/10 rounded-xl shadow-xl overflow-hidden py-1">
                        {!isCyclic && roadmapBlocks.length > 1 && (
                            <>
                                {roadmapBlocks.map(block => (
                                    <button
                                        key={block.id}
                                        onClick={() => { onSelectBlock(block.id); setDropdownOpen(false); }}
                                        className={`w-full px-4 py-2.5 text-left text-[11px] font-bold transition-colors ${
                                            block.id === selectedBlockId ? 'bg-[#FC4C02]/20 text-[#FC4C02]' : 'text-[#8E8E93] hover:text-white hover:bg-white/5'
                                        }`}
                                    >
                                        {block.name}
                                    </button>
                                ))}
                                <div className="border-t border-white/5 my-1" />
                            </>
                        )}
                        {isCyclic && (
                            <button
                                onClick={() => { setShowCyclicHistory(!showCyclicHistory); setDropdownOpen(false); }}
                                className={`w-full px-4 py-2.5 text-left text-[11px] font-bold ${showCyclicHistory ? 'bg-[#FC4C02]/20 text-[#FC4C02]' : 'text-[#8E8E93] hover:text-white hover:bg-white/5'}`}
                            >
                                {showCyclicHistory ? 'Rutina' : 'Historial'}
                            </button>
                        )}
                        <button onClick={() => { onOpenSplitChanger(); setDropdownOpen(false); }} className="w-full px-4 py-2.5 text-left text-[11px] font-bold text-[#8E8E93] hover:text-white hover:bg-white/5">
                            Split
                        </button>
                        <button onClick={() => { onOpenStructureDrawer(); setDropdownOpen(false); }} className="w-full px-4 py-2.5 text-left text-[11px] font-bold text-[#8E8E93] hover:text-white hover:bg-white/5">
                            Estructura
                        </button>
                    </div>
                )}
            </div>

            {/* Week nav - carrusel con indicador A/B para cíclicos */}
            <div className="px-4 py-2 border-b border-white/5 shrink-0">
                <div className="flex items-center justify-between gap-2">
                    <button onClick={() => navigateWeek(-1)} disabled={selectedWeekIndex <= 0} className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center text-[#8E8E93] hover:text-white disabled:opacity-20 transition-all shrink-0">
                        <ChevronDownIcon size={14} className="rotate-90" />
                    </button>
                    <div className="flex-1 overflow-x-auto hide-scrollbar flex gap-2 justify-center py-1">
                        {currentWeeks.map((week, weekIdx) => {
                            const isSelected = week.id === selectedWeekId;
                            const hasEv = weekHasEvent(weekIdx);
                            return (
                                <button
                                    key={week.id}
                                    onClick={() => onSelectWeek(week.id)}
                                    className={`shrink-0 px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-1.5 ${
                                        isSelected
                                            ? 'bg-[#FC4C02] text-white'
                                            : 'bg-white/5 text-[#8E8E93] hover:text-white hover:bg-white/10'
                                    }`}
                                >
                                    {getWeekLabel(weekIdx)}
                                    {hasEv && <CalendarIcon size={12} className={isSelected ? 'text-white' : 'text-[#FC4C02]'} />}
                                </button>
                            );
                        })}
                    </div>
                    <button onClick={() => navigateWeek(1)} disabled={selectedWeekIndex >= currentWeeks.length - 1} className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center text-[#8E8E93] hover:text-white disabled:opacity-20 transition-all shrink-0">
                        <ChevronDownIcon size={14} className="-rotate-90" />
                    </button>
                </div>
                <p className="text-[10px] text-[#48484A] text-center mt-1">
                    {isCyclic && currentWeeks.length === 2 ? 'Alternancia A/B' : `${selectedWeekIndex + 1} de ${currentWeeks.length}`}
                </p>
            </div>

            {/* Event banner */}
            {weekEvent && (
                <div className="mx-4 mt-2 px-3 py-2 rounded-lg bg-[#FC4C02]/10 border border-[#FC4C02]/20 flex items-center gap-2">
                    <CalendarIcon size={14} className="text-[#FC4C02] shrink-0" />
                    <span className="text-xs font-bold text-[#FC4C02]">{weekEvent.title}</span>
                </div>
            )}

            {/* Vertical week cards */}
            {!showCyclicHistory ? (
                <div className="flex-1 min-h-0 overflow-y-auto px-4 py-3 pb-[max(80px,env(safe-area-inset-bottom,0px))] space-y-4 custom-scrollbar">
                    {currentWeeks.map((week, weekIdx) => {
                        const weekSessionsByDay = getSessionsByDayForWeek(week);
                        const isSelected = week.id === selectedWeekId;
                        return (
                            <div
                                key={week.id}
                                className={`rounded-xl border overflow-hidden transition-all ${
                                    isSelected ? 'border-[#FC4C02]/40 bg-[#1a1a1a]' : 'border-white/5 bg-[#111]'
                                }`}
                            >
                                <div className="px-3 py-2 border-b border-white/5 flex items-center justify-between">
                                    <span className="text-[11px] font-bold text-white uppercase tracking-wide">{getWeekLabel(weekIdx)}</span>
                                    {week.id === selectedWeekId && (
                                        <span className="text-[9px] font-bold text-[#FC4C02]">Actual</span>
                                    )}
                                    {weekHasEvent(weekIdx) && (
                                        <CalendarIcon size={12} className="text-[#FC4C02] shrink-0" />
                                    )}
                                </div>
                                <div className="divide-y divide-white/5">
                                    {orderedDays.map(dayIdx => {
                                        const sessions = weekSessionsByDay.get(dayIdx) || [];
                                        return (
                                            <div key={dayIdx} className="flex flex-col">
                                                {sessions.length > 0 ? sessions.map((session) => {
                                                    const exerciseCount = (session.exercises || []).length;
                                                    const estimatedMin = exerciseCount * 8;
                                                    const isCompleted = completedSessionIds.has(session.id);
                                                    const block = roadmapBlocks.find(b => b.id === selectedBlockId);
                                                    const weekData = currentWeeks.find(w => w.id === week.id);
                                                    return (
                                                        <div
                                                            key={session.id}
                                                            className="flex items-center gap-3 px-3 py-2.5 hover:bg-white/5 transition-colors"
                                                            onClick={() => onEditSession(session)}
                                                        >
                                                            <span className="w-10 text-[10px] font-bold text-[#8E8E93] shrink-0">{DAY_NAMES_SHORT[dayIdx]}</span>
                                                            <div className="flex-1 min-w-0">
                                                                <span className={`text-xs font-bold block truncate ${isCompleted ? 'text-[#00F19F]' : 'text-white'}`}>
                                                                    {session.name}
                                                                </span>
                                                                <span className="text-[10px] text-[#48484A]">{exerciseCount} ej · {estimatedMin}m</span>
                                                            </div>
                                                            <button
                                                                onClick={(e) => { e.stopPropagation(); onStartWorkout(session, program); }}
                                                                className="w-8 h-8 rounded-lg bg-[#FC4C02] flex items-center justify-center shrink-0 hover:brightness-110 transition-all"
                                                            >
                                                                <PlayIcon size={12} fill="white" />
                                                            </button>
                                                            {week.id === selectedWeekId && block && weekData && (
                                                                <>
                                                                    <button
                                                                        onClick={(e) => { e.stopPropagation(); onEditSession(session); }}
                                                                        className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center shrink-0">
                                                                        <EditIcon size={12} />
                                                                    </button>
                                                                    <button
                                                                        onClick={(e) => { e.stopPropagation(); onDeleteSession(session.id, program.id, block.macroIndex, weekData.mesoIndex, week.id); }}
                                                                        className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center shrink-0 text-red-400 hover:bg-red-500/20">
                                                                        <TrashIcon size={12} />
                                                                    </button>
                                                                </>
                                                            )}
                                                        </div>
                                                    );
                                                }                                                ) : (
                                                    week.id === selectedWeekId && onAddSession && selectedBlock && (
                                                        <button
                                                            onClick={() => onAddSession(program.id, selectedBlock.macroIndex, week.mesoIndex, week.id, dayIdx)}
                                                            className="flex items-center gap-3 px-3 py-2.5 hover:bg-white/5 transition-colors text-[#48484A] hover:text-[#8E8E93]"
                                                        >
                                                            <span className="w-10 text-[10px] font-bold shrink-0">{DAY_NAMES_SHORT[dayIdx]}</span>
                                                            <span className="flex-1 text-left text-[10px]">+ Añadir sesión</span>
                                                            <PlusIcon size={12} />
                                                        </button>
                                                    )
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        );
                    })}
                </div>
            ) : (
                /* Cyclic history view */
                <div className="flex-1 min-h-0 overflow-y-auto px-4 py-3 pb-[max(80px,env(safe-area-inset-bottom,0px))] space-y-2 custom-scrollbar">
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

export default TrainingCalendarGrid;
