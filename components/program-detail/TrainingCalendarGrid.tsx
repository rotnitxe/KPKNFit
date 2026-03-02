import React, { useState, useMemo, useRef, useEffect } from 'react';
import ReactDOM from 'react-dom';
import { Program, Session, ProgramWeek, ExerciseMuscleInfo } from '../../types';
import { DragDropContext, Droppable, Draggable, DropResult } from '@hello-pangea/dnd';
import { ChevronDownIcon, PlusIcon, PlayIcon, EditIcon, TrashIcon, DumbbellIcon, CalendarIcon, ActivityIcon, DragHandleIcon, GridIcon, XIcon } from '../icons';
import { getAbsoluteWeekIndex } from '../../utils/programHelpers';
import StructureDrawer from './StructureDrawer';

const DAY_NAMES_SHORT = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

type ReorderScope = 'cyclic' | 'block' | 'same-split';

interface RoadmapBlock {
    id: string; name: string; macroIndex: number; blockIndex: number; totalWeeks: number; mesocycles: any[];
}

interface PendingReorder {
    weekId: string; sessionId: string; sessionName: string; fromDayOfWeek: number; toDayOfWeek: number;
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
    onUpdateProgram?: (program: Program) => void;
    onEditWeek?: (info: { macroIndex: number; blockIndex: number; mesoIndex: number; weekIndex: number; week: ProgramWeek; isSimple: boolean }) => void;
    onShowAdvancedTransition?: () => void;
    onShowSimpleTransition?: () => void;
    onOpenEventModal?: (data?: any) => void;
    onStartWorkout: (session: Session, program: Program, _?: any, ctx?: any) => void;
    onEditSession: (session: Session) => void;
    onDeleteSession: (sessionId: string, programId: string, macroIndex: number, mesoIndex: number, weekId: string) => void;
    onReorderSessions?: (programId: string, targetBlockId: string, targetWeekId: string, sessionId: string, sessionName: string, fromDayOfWeek: number, toDayOfWeek: number, scope: ReorderScope) => void;
    onAddSession?: (programId: string, macroIndex: number, mesoIndex: number, weekId: string, dayOfWeek: number) => void;
    addToast: (msg: string, type: 'success' | 'danger' | 'achievement' | 'suggestion') => void;
}

const TrainingCalendarGrid: React.FC<TrainingCalendarGridProps> = ({
    program, isCyclic, roadmapBlocks, currentWeeks, selectedBlockId,
    selectedWeekId, activeBlockId, settings, exerciseList, history,
    onSelectBlock, onSelectWeek, onOpenSplitChanger, onUpdateProgram,
    onEditWeek, onShowAdvancedTransition, onShowSimpleTransition, onOpenEventModal,
    onStartWorkout, onEditSession, onDeleteSession, onReorderSessions, onAddSession, addToast,
}) => {
    const [showCyclicHistory, setShowCyclicHistory] = useState(false);
    const [showStructureView, setShowStructureView] = useState(false);
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const [startDayDropdownOpen, setStartDayDropdownOpen] = useState(false);
    const [pendingReorder, setPendingReorder] = useState<PendingReorder | null>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const startDayRef = useRef<HTMLDivElement>(null);

    const isAdvanced = !isCyclic;

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) setDropdownOpen(false);
            if (startDayRef.current && !startDayRef.current.contains(e.target as Node)) setStartDayDropdownOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    useEffect(() => {
        if (!pendingReorder) return;
        const handleEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') setPendingReorder(null); };
        window.addEventListener('keydown', handleEsc);
        return () => window.removeEventListener('keydown', handleEsc);
    }, [pendingReorder]);

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

    const hasMultipleSplitsInBlock = useMemo(() => {
        if (!selectedBlock || currentWeeks.length < 2) return false;
        const getSig = (w: ProgramWeek) => [...(w.sessions || [])].map(s => s.name).sort().join('|');
        const first = getSig(currentWeeks[0]);
        return currentWeeks.some(w => getSig(w) !== first);
    }, [selectedBlock, currentWeeks]);

    const cycleLength = useMemo(() => {
        if (!selectedBlock) return 1;
        const block = roadmapBlocks.find(b => b.id === selectedBlock.id);
        const firstMeso = block?.mesocycles?.[0];
        if (!firstMeso) return 1;
        return program.structure === 'simple'
            ? Math.min(2, firstMeso.weeks?.length || 2)
            : Math.max(1, firstMeso.weeks?.length || currentWeeks.length);
    }, [program.structure, selectedBlock, roadmapBlocks, currentWeeks.length]);

    const targetWeekIdx = pendingReorder ? currentWeeks.findIndex(w => w.id === pendingReorder.weekId) : -1;
    const coincidentLabel = useMemo(() => {
        if (cycleLength === 1) return 'esta semana';
        const idx = targetWeekIdx >= 0 ? targetWeekIdx % cycleLength : 0;
        if (isCyclic && cycleLength === 2) return idx === 0 ? 'todas las Semana A' : 'todas las Semana B';
        return `todas las Semana ${idx + 1} coincidentes`;
    }, [cycleLength, targetWeekIdx, isCyclic]);

    const navigateWeek = (dir: -1 | 1) => {
        const newIdx = selectedWeekIndex + dir;
        if (newIdx >= 0 && newIdx < currentWeeks.length) {
            onSelectWeek(currentWeeks[newIdx].id);
        }
    };

    const handleSessionDragEnd = (result: DropResult) => {
        const { source, destination, draggableId } = result;
        if (!destination || !onReorderSessions || !selectedBlockId) return;
        if (source.droppableId === destination.droppableId && source.index === destination.index) return;

        const [weekId, sessionId] = draggableId.includes('::') ? draggableId.split('::') : ['', ''];
        const [, fromDayStr] = source.droppableId.includes('::') ? source.droppableId.split('::') : ['', '0'];
        const [, toDayStr] = destination.droppableId.includes('::') ? destination.droppableId.split('::') : ['', '0'];
        const fromDay = parseInt(fromDayStr, 10);
        const toDay = parseInt(toDayStr, 10);

        const week = currentWeeks.find(w => w.id === weekId);
        const session = week?.sessions?.find((s: Session) => s.id === sessionId);
        if (!session) return;

        const needsScopeChoice = currentWeeks.length > 1;
        if (needsScopeChoice) {
            setPendingReorder({ weekId, sessionId, sessionName: session.name, fromDayOfWeek: fromDay, toDayOfWeek: toDay });
        } else {
            onReorderSessions(program.id, selectedBlockId, weekId, sessionId, session.name, fromDay, toDay, 'block');
        }
    };

    const applyReorderWithScope = (scope: ReorderScope) => {
        if (!pendingReorder || !onReorderSessions || !selectedBlockId) return;
        onReorderSessions(program.id, selectedBlockId, pendingReorder.weekId, pendingReorder.sessionId, pendingReorder.sessionName, pendingReorder.fromDayOfWeek, pendingReorder.toDayOfWeek, scope);
        setPendingReorder(null);
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
            {/* Modal: ¿Aplicar reorden a qué semanas? — se muestra al soltar, no como switch estático */}
            {pendingReorder && ReactDOM.createPortal(
                <div className="fixed inset-0 z-[200] flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="reorder-scope-title">
                    <div className="absolute inset-0 bg-black/80 backdrop-blur-sm" onClick={() => setPendingReorder(null)} aria-hidden />
                    <div className="relative z-10 w-full max-w-sm bg-[#0a0a0a] border border-white/10 rounded-2xl shadow-xl overflow-hidden" onClick={e => e.stopPropagation()}>
                        <div className="px-5 py-4 border-b border-white/5 flex justify-between items-center">
                            <h2 id="reorder-scope-title" className="text-sm font-bold text-white">¿Aplicar reorden a…?</h2>
                            <button onClick={() => setPendingReorder(null)} aria-label="Cerrar" className="p-1.5 text-zinc-500 hover:text-white rounded-full hover:bg-white/5">
                                <XIcon size={16} />
                            </button>
                        </div>
                        <div className="p-4 space-y-2">
                            {isAdvanced ? (
                                <>
                                    <button onClick={() => applyReorderWithScope('cyclic')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-white/10 hover:border-white/20 text-white transition-all">
                                        Solo semanas coincidentes ({coincidentLabel})
                                    </button>
                                    <button onClick={() => applyReorderWithScope('block')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-white/10 hover:border-white/20 text-white transition-all">
                                        Todo el bloque
                                    </button>
                                    {hasMultipleSplitsInBlock && (
                                        <button onClick={() => applyReorderWithScope('same-split')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-white/10 hover:border-white/20 text-white transition-all">
                                            Solo semanas con el mismo split
                                        </button>
                                    )}
                                </>
                            ) : (
                                <>
                                    <button onClick={() => applyReorderWithScope('cyclic')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-white/10 hover:border-white/20 text-white transition-all">
                                        Solo semanas coincidentes ({coincidentLabel})
                                    </button>
                                    <button onClick={() => applyReorderWithScope('block')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-white/10 hover:border-white/20 text-white transition-all">
                                        Todas las semanas del bloque
                                    </button>
                                    {hasMultipleSplitsInBlock && (
                                        <button onClick={() => applyReorderWithScope('same-split')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-white/10 hover:border-white/20 text-white transition-all">
                                            Solo semanas con el mismo split
                                        </button>
                                    )}
                                </>
                            )}
                        </div>
                    </div>
                </div>,
                document.body
            )}
            {/* Dropdown: Bloques + Filtros - estilo Tú */}
            <div className="relative w-full px-6 py-2.5 border-b border-white/10 shrink-0" ref={dropdownRef}>
                <button
                    onClick={() => setDropdownOpen(!dropdownOpen)}
                    className="w-full flex items-center justify-between px-3 py-2 rounded-xl bg-black/40 border border-white/5 text-left"
                >
                    <span className="text-[11px] font-bold text-white truncate">
                        {selectedBlock?.name || 'Bloque'}
                        {isCyclic && ` · ${showCyclicHistory ? 'Historial' : 'Rutina'}`}
                    </span>
                    <ChevronDownIcon size={16} className={`text-zinc-500 shrink-0 transition-transform ${dropdownOpen ? 'rotate-180' : ''}`} />
                </button>
                {dropdownOpen && (
                    <div className="mt-2 absolute left-6 right-6 z-30 bg-black/60 backdrop-blur-sm border border-white/5 rounded-xl overflow-hidden py-1">
                        {!isCyclic && roadmapBlocks.length > 1 && (
                            <>
                                {roadmapBlocks.map(block => (
                                    <button
                                        key={block.id}
                                        onClick={() => { onSelectBlock(block.id); setDropdownOpen(false); }}
                                        className={`w-full px-4 py-2.5 text-left text-[11px] font-bold transition-colors ${
                                            block.id === selectedBlockId ? 'bg-white/10 text-white' : 'text-zinc-500 hover:text-white hover:bg-white/5'
                                        }`}
                                    >
                                        {block.name}
                                    </button>
                                ))}
                                <div className="border-t border-white/10 my-1" />
                            </>
                        )}
                        {isCyclic && (
                            <button
                                onClick={() => { setShowCyclicHistory(!showCyclicHistory); setShowStructureView(false); setDropdownOpen(false); }}
                                className={`w-full px-4 py-2.5 text-left text-[11px] font-bold ${showCyclicHistory ? 'bg-white/10 text-white' : 'text-zinc-500 hover:text-white hover:bg-white/5'}`}
                            >
                                {showCyclicHistory ? 'Rutina' : 'Historial'}
                            </button>
                        )}
                        <button onClick={() => { setShowStructureView(!showStructureView); setShowCyclicHistory(false); setDropdownOpen(false); }} className={`w-full px-4 py-2.5 text-left text-[11px] font-bold ${showStructureView ? 'bg-white/10 text-white' : 'text-zinc-500 hover:text-white hover:bg-white/5'}`}>
                            Estructura
                        </button>
                    </div>
                )}
            </div>

            {/* Week nav - texto plano estilo Tú */}
            <div className="px-6 py-3 border-b border-white/10 shrink-0">
                <div className="flex items-center justify-center gap-4">
                    <button onClick={() => navigateWeek(-1)} disabled={selectedWeekIndex <= 0} className="p-1.5 text-zinc-500 hover:text-white disabled:opacity-20 transition-colors shrink-0" aria-label="Semana anterior">
                        <ChevronDownIcon size={18} className="rotate-90" />
                    </button>
                    <span className="text-sm font-bold text-white">
                        {selectedWeekId && currentWeeks.find(w => w.id === selectedWeekId)
                            ? getWeekLabel(currentWeeks.findIndex(w => w.id === selectedWeekId))
                            : getWeekLabel(0)}
                    </span>
                    <button onClick={() => navigateWeek(1)} disabled={selectedWeekIndex >= currentWeeks.length - 1} className="p-1.5 text-zinc-500 hover:text-white disabled:opacity-20 transition-colors shrink-0" aria-label="Semana siguiente">
                        <ChevronDownIcon size={18} className="-rotate-90" />
                    </button>
                </div>
                <div className="flex items-center justify-center gap-3 mt-1 flex-wrap">
                    <span className="text-[10px] text-zinc-500 font-black uppercase tracking-widest">
                        {isCyclic && currentWeeks.length === 2 ? 'Alternancia A/B' : `${selectedWeekIndex + 1} de ${currentWeeks.length}`}
                    </span>
                    <span className="text-zinc-700">·</span>
                    <div className="relative" ref={startDayRef}>
                        <button
                            onClick={() => setStartDayDropdownOpen(!startDayDropdownOpen)}
                            className="text-[10px] font-bold text-zinc-500 hover:text-white transition-colors uppercase tracking-widest"
                        >
                            Inicio: {DAY_NAMES_SHORT[startOn]}
                        </button>
                        {startDayDropdownOpen && onUpdateProgram && (
                            <div className="absolute top-full left-1/2 -translate-x-1/2 mt-1 z-30 py-1 rounded-xl bg-[#0a0a0a] border border-white/10 shadow-xl min-w-[120px]">
                                {DAY_NAMES_SHORT.map((d, i) => (
                                    <button
                                        key={i}
                                        onClick={() => {
                                            const updated = JSON.parse(JSON.stringify(program));
                                            updated.startDay = i;
                                            onUpdateProgram(updated);
                                            setStartDayDropdownOpen(false);
                                        }}
                                        className={`w-full px-3 py-2 text-left text-[10px] font-bold transition-colors ${startOn === i ? 'bg-white/10 text-white' : 'text-zinc-500 hover:text-white hover:bg-white/5'}`}
                                    >
                                        {d}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Event banner - estilo Tú */}
            {weekEvent && (
                <div className="mx-6 mt-2 px-3 py-2 rounded-xl bg-white/5 border border-white/10 flex items-center gap-2">
                    <CalendarIcon size={14} className="text-zinc-400 shrink-0" />
                    <span className="text-xs font-bold text-white">{weekEvent.title}</span>
                </div>
            )}

            {/* Contenido principal: Estructura inline | Rutina | Historial */}
            {showStructureView && onUpdateProgram && onEditWeek && onShowAdvancedTransition && onShowSimpleTransition && onOpenEventModal ? (
                <div className="flex-1 min-h-0 overflow-hidden flex flex-col px-4 py-3">
                    <StructureDrawer
                        isOpen={true}
                        onClose={() => setShowStructureView(false)}
                        program={program}
                        isCyclic={isCyclic}
                        selectedBlockId={selectedBlockId}
                        selectedWeekId={selectedWeekId}
                        onSelectBlock={onSelectBlock}
                        onSelectWeek={(id) => { onSelectWeek(id); setShowStructureView(false); }}
                        onUpdateProgram={onUpdateProgram}
                        onEditWeek={onEditWeek}
                        onShowAdvancedTransition={() => { setShowStructureView(false); onShowAdvancedTransition(); }}
                        onShowSimpleTransition={() => { setShowStructureView(false); onShowSimpleTransition(); }}
                        onOpenEventModal={(data) => { setShowStructureView(false); onOpenEventModal?.(data); }}
                        inline
                    />
                </div>
            ) : !showCyclicHistory ? (
                <DragDropContext onDragEnd={handleSessionDragEnd}>
                <div className="flex-1 min-h-0 overflow-y-auto px-6 py-3 pb-[max(95px,calc(80px+env(safe-area-inset-bottom,0px)+12px))] space-y-6 custom-scrollbar">
                    {currentWeeks.map((week, weekIdx) => {
                        const weekSessionsByDay = getSessionsByDayForWeek(week);
                        const isSelected = week.id === selectedWeekId;
                        return (
                            <div
                                key={week.id}
                                className={`w-full transition-all ${isSelected ? 'border-l-2 border-white/20 pl-2' : 'border-l-2 border-transparent pl-2'}`}
                            >
                                <div className="flex items-center justify-between gap-2 py-1.5 mb-1">
                                    <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest">{getWeekLabel(weekIdx)}</span>
                                    <div className="flex items-center gap-1">
                                        <button
                                            onClick={(e) => { e.stopPropagation(); onOpenSplitChanger(); }}
                                            className="w-7 h-7 rounded-lg flex items-center justify-center text-zinc-500 hover:text-white transition-colors"
                                            title="Cambiar split"
                                            aria-label="Cambiar split"
                                        >
                                            <GridIcon size={12} />
                                        </button>
                                        {week.id === selectedWeekId && (
                                            <span className="text-[9px] font-bold text-zinc-500">Actual</span>
                                        )}
                                        {weekHasEvent(weekIdx) && (
                                            <CalendarIcon size={12} className="text-zinc-500 shrink-0" />
                                        )}
                                    </div>
                                </div>
                                <div className="divide-y divide-white/5">
                                    {orderedDays.map(dayIdx => {
                                        const sessions = weekSessionsByDay.get(dayIdx) || [];
                                        const dropId = `${week.id}::${dayIdx}`;
                                        return (
                                            <Droppable key={dayIdx} droppableId={dropId}>
                                                {(provided: any, snapshot: any) => (
                                            <div
                                                ref={provided.innerRef}
                                                {...provided.droppableProps}
                                                className={`flex flex-col min-h-[44px] ${snapshot.isDraggingOver ? 'bg-white/5' : ''}`}
                                            >
                                                {sessions.length > 0 ? sessions.map((session, sIdx) => {
                                                    const exerciseCount = (session.parts && session.parts.length > 0)
                                                        ? session.parts.reduce((a, p) => a + (p.exercises?.length || 0), 0)
                                                        : (session.exercises || []).length;
                                                    const estimatedMin = exerciseCount * 8;
                                                    const isCompleted = completedSessionIds.has(session.id);
                                                    const block = roadmapBlocks.find(b => b.id === selectedBlockId);
                                                    const weekData = currentWeeks.find(w => w.id === week.id);
                                                    return (
                                                        <Draggable key={session.id} draggableId={`${week.id}::${session.id}`} index={sIdx}>
                                                            {(dragProvided: any, dragSnapshot: any) => (
                                                        <div
                                                            ref={dragProvided.innerRef}
                                                            {...dragProvided.draggableProps}
                                                            data-testid="calendar-session-row"
                                                            role="button"
                                                            tabIndex={0}
                                                            aria-label={`Editar sesión ${session.name}`}
                                                            className={`flex items-center gap-3 px-3 py-2.5 hover:bg-white/5 transition-colors cursor-pointer ${dragSnapshot.isDragging ? 'opacity-80 shadow-lg bg-[#0a0a0a]' : ''}`}
                                                            onClick={() => onEditSession(session)}
                                                            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onEditSession(session); } }}
                                                        >
                                                            <div {...dragProvided.dragHandleProps} className="touch-none shrink-0 cursor-grab active:cursor-grabbing text-zinc-500 hover:text-zinc-400" onClick={(e) => e.stopPropagation()}>
                                                                <DragHandleIcon size={14} />
                                                            </div>
                                                            <span className="w-8 text-[10px] font-bold text-zinc-500 shrink-0">{DAY_NAMES_SHORT[dayIdx]}</span>
                                                            <div className="flex-1 min-w-0">
                                                                <span className={`text-xs font-bold block truncate ${isCompleted ? 'text-white/80' : 'text-white'}`}>
                                                                    {session.name}
                                                                </span>
                                                                <span className="text-[10px] text-zinc-500">{exerciseCount} ej · {estimatedMin}m</span>
                                                            </div>
                                                            <button
                                                                data-testid="calendar-session-start"
                                                                aria-label="Iniciar entrenamiento"
                                                                onClick={(e) => { e.stopPropagation(); onStartWorkout(session, program); }}
                                                                className="w-8 h-8 rounded-lg bg-white/5 border border-white/20 flex items-center justify-center shrink-0 text-white hover:bg-white/10 transition-all"
                                                            >
                                                                <PlayIcon size={12} fill="currentColor" />
                                                            </button>
                                                            {week.id === selectedWeekId && block && weekData && (
                                                                <>
                                                                    <button
                                                                        data-testid="calendar-session-edit"
                                                                        aria-label="Editar sesión"
                                                                        onClick={(e) => { e.stopPropagation(); onEditSession(session); }}
                                                                        className="w-8 h-8 rounded-lg bg-white/5 border border-white/10 flex items-center justify-center shrink-0 text-zinc-500 hover:text-white">
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
                                                            )}
                                                        </Draggable>
                                                    );
                                                }                                                ) : (
                                                    week.id === selectedWeekId && onAddSession && selectedBlock && (
                                                        <button
                                                            onClick={() => onAddSession(program.id, selectedBlock.macroIndex, week.mesoIndex, week.id, dayIdx)}
                                                            className="flex items-center gap-3 px-3 py-2.5 hover:bg-white/5 transition-colors text-zinc-500 hover:text-zinc-400"
                                                        >
                                                            <span className="w-10 text-[10px] font-bold shrink-0">{DAY_NAMES_SHORT[dayIdx]}</span>
                                                            <span className="flex-1 text-left text-[10px]">+ Añadir sesión</span>
                                                            <PlusIcon size={12} />
                                                        </button>
                                                    )
                                                    )}
                                                {provided.placeholder}
                                            </div>
                                                )}
                                            </Droppable>
                                        );
                                    })}
                                </div>
                            </div>
                        );
                    })}
                </div>
                </DragDropContext>
            ) : (
                /* Cyclic history view - estilo Tú */
                <div className="flex-1 min-h-0 overflow-y-auto px-6 py-3 pb-[max(95px,calc(80px+env(safe-area-inset-bottom,0px)+12px))] space-y-2 custom-scrollbar">
                    {programLogs.length > 0 ? programLogs.slice(0, 20).map((log: any, idx: number) => (
                        <div key={idx} className="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-[#0a0a0a] border border-white/10">
                            <div className="w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center shrink-0">
                                <DumbbellIcon size={14} className="text-zinc-400" />
                            </div>
                            <div className="flex-1 min-w-0">
                                <span className="text-xs font-bold text-white block truncate">{log.sessionName || 'Sesión'}</span>
                                <span className="text-[10px] text-zinc-500">{new Date(log.date).toLocaleDateString('es', { day: 'numeric', month: 'short' })}</span>
                            </div>
                            <span className="text-[10px] font-bold text-zinc-500">{log.exercises?.length || 0} ej.</span>
                        </div>
                    )) : (
                        <div className="text-center py-12">
                            <DumbbellIcon size={24} className="text-zinc-500 mx-auto mb-2" />
                            <p className="text-xs text-zinc-500 font-bold">Sin historial aún</p>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default TrainingCalendarGrid;
