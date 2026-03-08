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
    const [mainTab, setMainTab] = useState<'semanas' | 'estructura'>('semanas');
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const [startDayDropdownOpen, setStartDayDropdownOpen] = useState(false);
    const [actionMenuOpen, setActionMenuOpen] = useState(false);
    const [pendingReorder, setPendingReorder] = useState<PendingReorder | null>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const startDayRef = useRef<HTMLDivElement>(null);
    const actionMenuRef = useRef<HTMLDivElement>(null);
    // Swipe support
    const touchStartX = useRef<number | null>(null);
    const touchStartY = useRef<number | null>(null);

    const isAdvanced = !isCyclic;

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) setDropdownOpen(false);
            if (startDayRef.current && !startDayRef.current.contains(e.target as Node)) setStartDayDropdownOpen(false);
            if (actionMenuRef.current && !actionMenuRef.current.contains(e.target as Node)) setActionMenuOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // ── Quick Programming Tools ──
    const handleCopyWeek = () => {
        if (!onUpdateProgram || !selectedBlockId || !selectedWeekId) return;
        const clone = JSON.parse(JSON.stringify(program));
        const macro = clone.macrocycles.find((m: any) => m.blocks?.some((b: any) => b.id === selectedBlockId));
        if (!macro) return;
        const srcBlock = macro.blocks.find((b: any) => b.id === selectedBlockId);
        const srcMeso = srcBlock?.mesocycles.find((m: any) => m.weeks.some((w: any) => w.id === selectedWeekId));
        const srcWeek = srcMeso?.weeks.find((w: any) => w.id === selectedWeekId);
        if (!srcWeek) return;

        const newWeek = JSON.parse(JSON.stringify(srcWeek));
        newWeek.id = crypto.randomUUID();
        newWeek.name = `Semana ${srcMeso.weeks.length + 1}`;
        newWeek.sessions.forEach((s: any) => { s.id = crypto.randomUUID(); });

        srcMeso.weeks.push(newWeek);
        onUpdateProgram(clone);
        setActionMenuOpen(false);
        addToast('Semana duplicada al final del mesociclo', 'success');
    };

    const handleDuplicateBlock = () => {
        if (!onUpdateProgram || !selectedBlockId) return;
        const clone = JSON.parse(JSON.stringify(program));
        const macro = clone.macrocycles.find((m: any) => m.blocks?.some((b: any) => b.id === selectedBlockId));
        if (!macro) return;
        const srcBlock = macro.blocks.find((b: any) => b.id === selectedBlockId);
        if (!srcBlock) return;

        const newBlock = JSON.parse(JSON.stringify(srcBlock));
        newBlock.id = crypto.randomUUID();
        newBlock.name = newBlock.name + " (Copia)";
        newBlock.mesocycles.forEach((m: any) => {
            m.id = crypto.randomUUID();
            m.weeks.forEach((w: any) => {
                w.id = crypto.randomUUID();
                w.sessions.forEach((s: any) => { s.id = crypto.randomUUID(); });
            });
        });

        const bIndex = macro.blocks.findIndex((b: any) => b.id === selectedBlockId);
        macro.blocks.splice(bIndex + 1, 0, newBlock);
        onUpdateProgram(clone);
        setActionMenuOpen(false);
        addToast('Bloque duplicado con éxito', 'success');
    };

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
        <div className="flex flex-col h-full min-h-0" style={{ backgroundColor: 'var(--md-sys-color-background)' }}>
            {/* Modal: ¿Aplicar reorden a qué semanas? — se muestra al soltar, no como switch estático */}
            {pendingReorder && ReactDOM.createPortal(
                <div className="fixed inset-0 z-[200] flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="reorder-scope-title">
                    <div className="absolute inset-0 bg-black/80 backdrop-blur-sm" onClick={() => setPendingReorder(null)} aria-hidden />
                    <div className="relative z-10 w-full max-w-sm bg-[var(--md-sys-color-surface-container-highest)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl shadow-2xl overflow-hidden" onClick={e => e.stopPropagation()}>
                        <div className="px-5 py-4 border-b border-[var(--md-sys-color-outline-variant)] flex justify-between items-center">
                            <h2 id="reorder-scope-title" className="text-label-lg font-black uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface)' }}>¿Aplicar reorden a…?</h2>
                            <button onClick={() => setPendingReorder(null)} aria-label="Cerrar" className="p-1.5 text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] rounded-full hover:bg-[var(--md-sys-color-surface-variant)]">
                                <XIcon size={16} />
                            </button>
                        </div>
                        <div className="p-4 space-y-2">
                            {isAdvanced ? (
                                <>
                                    <button onClick={() => applyReorderWithScope('cyclic')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-[#E6E0E9] hover:border-white/20 text-white transition-all">
                                        Solo semanas coincidentes ({coincidentLabel})
                                    </button>
                                    <button onClick={() => applyReorderWithScope('block')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-[#E6E0E9] hover:border-white/20 text-white transition-all">
                                        Todo el bloque
                                    </button>
                                    {hasMultipleSplitsInBlock && (
                                        <button onClick={() => applyReorderWithScope('same-split')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-[#E6E0E9] hover:border-white/20 text-white transition-all">
                                            Solo semanas con el mismo split
                                        </button>
                                    )}
                                </>
                            ) : (
                                <>
                                    <button onClick={() => applyReorderWithScope('cyclic')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-[#E6E0E9] hover:border-white/20 text-white transition-all">
                                        Solo semanas coincidentes ({coincidentLabel})
                                    </button>
                                    <button onClick={() => applyReorderWithScope('block')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-[#E6E0E9] hover:border-white/20 text-white transition-all">
                                        Todas las semanas del bloque
                                    </button>
                                    {hasMultipleSplitsInBlock && (
                                        <button onClick={() => applyReorderWithScope('same-split')} className="w-full px-4 py-3 rounded-xl text-left text-xs font-medium bg-white/5 hover:bg-white/10 border border-[#E6E0E9] hover:border-white/20 text-white transition-all">
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

            {/* ── Block selector: chip row for advanced, hidden for simple ── */}
            {!isCyclic && roadmapBlocks.length > 1 && (
                <div className="px-4 py-2 border-b border-[var(--md-sys-color-outline-variant)]/50 shrink-0 overflow-x-auto no-scrollbar">
                    <div className="flex gap-2">
                        {roadmapBlocks.map(block => (
                            <button
                                key={block.id}
                                onClick={() => { onSelectBlock(block.id); }}
                                className={`shrink-0 px-3 py-1.5 rounded-full text-[10px] font-black uppercase tracking-widest border transition-all ${block.id === selectedBlockId
                                    ? 'bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] border-transparent shadow-md'
                                    : 'bg-transparent text-[var(--md-sys-color-on-surface-variant)] border-[var(--md-sys-color-outline-variant)]/60 hover:border-[var(--md-sys-color-outline)]'
                                    }`}
                            >
                                {block.name}
                                {block.id === activeBlockId && (
                                    <span className="ml-1.5 inline-block w-1.5 h-1.5 rounded-full bg-[var(--md-sys-color-primary)] align-middle" />
                                )}
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {/* ── Sub-tabs Header ── */}
            <div className="px-4 py-3 bg-[var(--md-sys-color-surface-container)] shrink-0 flex justify-center border-b border-[var(--md-sys-color-outline-variant)]">
                <div className="flex items-center p-1 bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] rounded-xl w-full max-w-[240px]">
                    <button
                        onClick={() => setMainTab('semanas')}
                        className={`flex-1 py-1.5 text-[10px] font-black uppercase tracking-[0.1em] text-center rounded-lg transition-all ${mainTab === 'semanas'
                            ? 'bg-white text-black shadow-sm'
                            : 'text-[var(--md-sys-color-on-surface-variant)] hover:text-white'
                            }`}
                    >
                        Semanas
                    </button>
                    <button
                        onClick={() => setMainTab('estructura')}
                        className={`flex-1 py-1.5 text-[10px] font-black uppercase tracking-[0.1em] text-center rounded-lg transition-all ${mainTab === 'estructura'
                            ? 'bg-white text-black shadow-sm'
                            : 'text-[var(--md-sys-color-on-surface-variant)] hover:text-white'
                            }`}
                    >
                        Estructura
                    </button>
                </div>
            </div>

            {/* ── Week nav: stepper dots + arrows (only in Semanas tab) ── */}
            {mainTab === 'semanas' && (
                <div className="px-4 py-2.5 border-b border-[var(--md-sys-color-outline-variant)]/40 shrink-0 select-none">
                    {/* Stepper: dots or compact label */}
                    {currentWeeks.length > 1 && currentWeeks.length <= 8 ? (
                        <div className="flex items-center justify-center gap-2 mb-2">
                            {currentWeeks.map((w, idx) => {
                                const isSelected = w.id === selectedWeekId;
                                const hasEv = weekHasEvent(idx);
                                return (
                                    <button
                                        key={w.id}
                                        onClick={() => onSelectWeek(w.id)}
                                        className={`relative transition-all rounded-full ${isSelected
                                            ? 'w-6 h-2 bg-[var(--md-sys-color-primary)]'
                                            : 'w-2 h-2 bg-[var(--md-sys-color-outline-variant)] hover:bg-[var(--md-sys-color-on-surface-variant)]'
                                            }`}
                                        aria-label={`Ir a semana ${idx + 1}`}
                                    >
                                        {hasEv && (
                                            <span className="absolute -top-1 -right-0.5 w-1.5 h-1.5 rounded-full bg-[var(--md-sys-color-tertiary)] border border-[var(--md-sys-color-background)]" />
                                        )}
                                    </button>
                                );
                            })}
                        </div>
                    ) : currentWeeks.length > 8 ? (
                        <div className="text-center mb-2">
                            <span className="text-label-sm font-black uppercase tracking-widest opacity-60" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                                Sem {selectedWeekIndex + 1} / {currentWeeks.length}
                            </span>
                        </div>
                    ) : null}
                    {/* Label row + arrows */}
                    <div className="flex items-center justify-between gap-2">
                        <button
                            onClick={() => navigateWeek(-1)}
                            disabled={selectedWeekIndex <= 0}
                            className="p-1.5 text-[var(--md-sys-color-on-surface-variant)] disabled:opacity-20 transition-colors shrink-0"
                            aria-label="Semana anterior"
                        >
                            <ChevronDownIcon size={16} className="rotate-90" />
                        </button>
                        <div className="flex flex-col items-center gap-0.5 flex-1">
                            <span className="text-title-sm font-black uppercase tracking-[0.1em]" style={{ color: 'var(--md-sys-color-on-background)' }}>
                                {selectedWeekId && currentWeeks.find(w => w.id === selectedWeekId)
                                    ? getWeekLabel(currentWeeks.findIndex(w => w.id === selectedWeekId))
                                    : getWeekLabel(0)}
                            </span>
                            <div className="relative" ref={startDayRef}>
                                <button
                                    onClick={() => setStartDayDropdownOpen(!startDayDropdownOpen)}
                                    className="text-label-sm font-black uppercase tracking-widest opacity-40 hover:opacity-80 transition-opacity"
                                    style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                                >
                                    Inicio: {DAY_NAMES_SHORT[startOn]}
                                </button>
                                {startDayDropdownOpen && onUpdateProgram && (
                                    <div className="absolute top-full left-1/2 -translate-x-1/2 mt-1 z-30 py-1 rounded-xl border border-[var(--md-sys-color-outline-variant)] shadow-xl min-w-[120px]" style={{ backgroundColor: 'var(--md-sys-color-surface-container-highest)' }}>
                                        {DAY_NAMES_SHORT.map((d, i) => (
                                            <button
                                                key={i}
                                                onClick={() => {
                                                    const updated = JSON.parse(JSON.stringify(program));
                                                    updated.startDay = i;
                                                    onUpdateProgram(updated);
                                                    setStartDayDropdownOpen(false);
                                                }}
                                                className={`w-full px-3 py-2 text-left text-[10px] font-bold transition-colors ${startOn === i
                                                    ? 'bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)]'
                                                    : 'text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] hover:bg-[var(--md-sys-color-surface-variant)]'
                                                    }`}
                                            >
                                                {d}
                                            </button>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </div>
                        <button
                            onClick={() => navigateWeek(1)}
                            disabled={selectedWeekIndex >= currentWeeks.length - 1}
                            className="p-1.5 text-[var(--md-sys-color-on-surface-variant)] disabled:opacity-20 transition-colors shrink-0"
                            aria-label="Semana siguiente"
                        >
                            <ChevronDownIcon size={16} className="-rotate-90" />
                        </button>

                        {/* Action menu (Kebab) */}
                        <div className="relative" ref={actionMenuRef}>
                            <button
                                onClick={() => setActionMenuOpen(!actionMenuOpen)}
                                className="w-8 h-8 rounded-full hover:bg-[var(--md-sys-color-surface-variant)] flex items-center justify-center transition-colors"
                                style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                            >
                                {/* Kebab explicit rendering */}
                                <div className="flex flex-col gap-[3px]">
                                    <div className="w-[3px] h-[3px] rounded-full bg-current" />
                                    <div className="w-[3px] h-[3px] rounded-full bg-current" />
                                    <div className="w-[3px] h-[3px] rounded-full bg-current" />
                                </div>
                            </button>
                            {actionMenuOpen && (
                                <div className="absolute right-0 top-full mt-1 w-48 py-1 rounded-xl border border-[var(--md-sys-color-outline-variant)] shadow-xl z-[100]" style={{ backgroundColor: 'var(--md-sys-color-surface-container-highest)' }}>
                                    <button
                                        onClick={() => { setActionMenuOpen(false); onOpenSplitChanger(); }}
                                        className="w-full px-4 py-2.5 text-left text-xs font-black uppercase tracking-widest hover:bg-[var(--md-sys-color-surface-variant)] transition-colors"
                                        style={{ color: 'var(--md-sys-color-primary)' }}
                                    >
                                        Cambiar Split
                                    </button>
                                    <div className="h-px bg-white/10 my-1" />
                                    <button
                                        onClick={handleCopyWeek}
                                        className="w-full px-4 py-2 text-left text-xs font-bold hover:bg-[var(--md-sys-color-surface-variant)] transition-colors"
                                        style={{ color: 'var(--md-sys-color-on-surface)' }}
                                    >
                                        Copiar semana actual
                                    </button>
                                    {isAdvanced && (
                                        <button
                                            onClick={handleDuplicateBlock}
                                            className="w-full px-4 py-2 text-left text-xs font-bold hover:bg-[var(--md-sys-color-surface-variant)] transition-colors"
                                            style={{ color: 'var(--md-sys-color-on-surface)' }}
                                        >
                                            Duplicar bloque act.
                                        </button>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* Event banner - estilo Tú */}
            {weekEvent && (
                <div className="mx-6 mt-2 px-3 py-2.5 rounded-xl border border-[var(--md-sys-color-outline-variant)] flex items-center gap-2" style={{ backgroundColor: 'var(--md-sys-color-surface-container-high)' }}>
                    <CalendarIcon size={14} className="text-[#49454F] shrink-0" />
                    <span className="text-xs font-bold text-white">{weekEvent.title}</span>
                </div>
            )}

            {/* Contenido principal: Estructura | Semanas */}
            {mainTab === 'estructura' && onUpdateProgram && onEditWeek && onShowAdvancedTransition && onShowSimpleTransition && onOpenEventModal ? (
                <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                    <StructureDrawer
                        isOpen={true}
                        onClose={() => setMainTab('semanas')}
                        program={program}
                        isCyclic={isCyclic}
                        selectedBlockId={selectedBlockId}
                        selectedWeekId={selectedWeekId}
                        onSelectBlock={onSelectBlock}
                        onSelectWeek={(id) => { onSelectWeek(id); setMainTab('semanas'); }}
                        onUpdateProgram={onUpdateProgram}
                        onEditWeek={onEditWeek}
                        onShowAdvancedTransition={() => { setMainTab('semanas'); onShowAdvancedTransition(); }}
                        onShowSimpleTransition={() => { setMainTab('semanas'); onShowSimpleTransition(); }}
                        onOpenEventModal={(data) => { onOpenEventModal?.(data); }}
                        inline
                    />
                </div>
            ) : mainTab === 'semanas' && selectedWeek ? (
                <DragDropContext onDragEnd={handleSessionDragEnd}>
                    <div
                        className="flex-1 min-h-0 overflow-x-auto overflow-y-hidden snap-x snap-mandatory flex gap-4 px-4 py-6 pb-[max(95px,calc(80px+env(safe-area-inset-bottom,0px)+12px))] custom-scrollbar"
                        onTouchStart={e => {
                            touchStartX.current = e.touches[0].clientX;
                            touchStartY.current = e.touches[0].clientY;
                        }}
                        onTouchEnd={e => {
                            if (touchStartX.current === null || touchStartY.current === null) return;
                            const dx = e.changedTouches[0].clientX - touchStartX.current;
                            const dy = Math.abs(e.changedTouches[0].clientY - touchStartY.current);
                            if (Math.abs(dx) > 60 && dy < 40) {
                                if (dx < 0) navigateWeek(1);
                                else navigateWeek(-1);
                            }
                            touchStartX.current = null;
                            touchStartY.current = null;
                        }}
                    >
                        {orderedDays.map(dayIdx => {
                            const sessions = sessionsByDay.get(dayIdx) || [];
                            const dropId = `${selectedWeek.id}::${dayIdx}`;
                            return (
                                <div key={dayIdx} className="min-w-[280px] w-[85vw] max-w-[340px] shrink-0 snap-center flex flex-col gap-3">
                                    <div className="flex items-center justify-between pb-2 border-b border-[var(--md-sys-color-outline-variant)]/30">
                                        <span className="text-sm font-black uppercase tracking-[0.1em]" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                                            {DAY_NAMES_SHORT[dayIdx]}
                                        </span>
                                        {onAddSession && selectedBlock && (
                                            <button
                                                onClick={() => onAddSession(program.id, selectedBlock.macroIndex, selectedWeek.mesoIndex, selectedWeek.id, dayIdx)}
                                                className="w-8 h-8 rounded-lg bg-[var(--md-sys-color-primary-container)] flex items-center justify-center text-[var(--md-sys-color-on-primary-container)] hover:brightness-110 active:scale-95 transition-all"
                                                aria-label={`Añadir sesión al ${DAY_NAMES_SHORT[dayIdx]}`}
                                            >
                                                <PlusIcon size={16} />
                                            </button>
                                        )}
                                    </div>
                                    <Droppable droppableId={dropId}>
                                        {(provided: any, snapshot: any) => (
                                            <div
                                                ref={provided.innerRef}
                                                {...provided.droppableProps}
                                                className={`flex-1 flex flex-col gap-3 min-h-[120px] rounded-2xl p-2 transition-colors ${snapshot.isDraggingOver ? 'bg-[var(--md-sys-color-surface-variant)]/40' : 'bg-[var(--md-sys-color-surface-container)]'}`}
                                            >
                                                {sessions.length > 0 ? sessions.map((session, sIdx) => {
                                                    const exerciseCount = (session.parts && session.parts.length > 0)
                                                        ? session.parts.reduce((a, p) => a + (p.exercises?.length || 0), 0)
                                                        : (session.exercises || []).length;
                                                    const estimatedMin = exerciseCount * 8;
                                                    const isCompleted = completedSessionIds.has(session.id);
                                                    const block = roadmapBlocks.find(b => b.id === selectedBlockId);
                                                    return (
                                                        <Draggable key={session.id} draggableId={`${selectedWeek.id}::${session.id}`} index={sIdx}>
                                                            {(dragProvided: any, dragSnapshot: any) => (
                                                                <div
                                                                    ref={dragProvided.innerRef}
                                                                    {...dragProvided.draggableProps}
                                                                    className={`flex flex-col gap-3 p-3.5 rounded-xl border transition-all ${dragSnapshot.isDragging ? 'shadow-2xl border-[var(--md-sys-color-primary)] bg-[var(--md-sys-color-surface-container-highest)] ring-4 ring-[var(--md-sys-color-primary)]/20 z-50' : 'border-[var(--md-sys-color-outline-variant)]/50 bg-[#0d0d0d] hover:border-[var(--md-sys-color-outline)]/50'}`}
                                                                >
                                                                    <div className="flex items-start gap-3">
                                                                        <div {...dragProvided.dragHandleProps} className="mt-1 touch-none shrink-0 cursor-grab active:cursor-grabbing opacity-40 hover:opacity-100 transition-opacity" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                                                                            <DragHandleIcon size={16} />
                                                                        </div>
                                                                        <div className="flex-1 min-w-0" role="button" onClick={() => onEditSession(session)}>
                                                                            <span className="text-sm font-black uppercase tracking-tight block truncate" style={{ color: isCompleted ? 'var(--md-sys-color-on-surface-variant)' : 'var(--md-sys-color-on-surface)' }}>
                                                                                {session.name}
                                                                            </span>
                                                                            <span className="text-xs flex items-center gap-2 mt-1 opacity-70" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                                                                                <DumbbellIcon size={12} /> {exerciseCount} ej <span className="opacity-30">|</span> <ActivityIcon size={12} /> {estimatedMin}m
                                                                            </span>
                                                                        </div>
                                                                    </div>

                                                                    <div className="flex items-center gap-2 mt-1">
                                                                        <button
                                                                            onClick={(e) => { e.stopPropagation(); onStartWorkout(session, program); }}
                                                                            className="flex-1 h-9 rounded-lg bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] flex items-center justify-center font-black uppercase tracking-widest text-[10px] hover:brightness-110 active:scale-95 transition-all shadow-[0_4px_12px_rgba(var(--md-sys-color-primary-rgb),0.2)]"
                                                                        >
                                                                            <PlayIcon size={12} fill="currentColor" className="mr-1.5" /> Iniciar
                                                                        </button>
                                                                        <button
                                                                            onClick={(e) => { e.stopPropagation(); onEditSession(session); }}
                                                                            className="w-10 h-9 rounded-lg bg-[var(--md-sys-color-surface-container-high)] flex items-center justify-center hover:bg-[var(--md-sys-color-surface-variant)] transition-all"
                                                                            style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                                                                        >
                                                                            <EditIcon size={14} />
                                                                        </button>
                                                                        <button
                                                                            onClick={(e) => { e.stopPropagation(); onDeleteSession(session.id, program.id, block!.macroIndex, selectedWeek.mesoIndex, selectedWeek.id); }}
                                                                            className="w-10 h-9 rounded-lg bg-[var(--md-sys-color-error-container)]/10 text-[var(--md-sys-color-error)] flex items-center justify-center hover:bg-[var(--md-sys-color-error-container)]/20 transition-all border border-[var(--md-sys-color-error-container)]/20"
                                                                        >
                                                                            <TrashIcon size={14} />
                                                                        </button>
                                                                    </div>
                                                                </div>
                                                            )}
                                                        </Draggable>
                                                    );
                                                }) : (
                                                    <div className="flex-1 flex flex-col items-center justify-center text-center px-4 py-8 opacity-40">
                                                        <DumbbellIcon size={24} className="mb-2" style={{ color: 'var(--md-sys-color-on-surface-variant)' }} />
                                                        <p className="text-[10px] uppercase tracking-widest font-bold" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Descanso</p>
                                                    </div>
                                                )}
                                                {provided.placeholder}
                                            </div>
                                        )}
                                    </Droppable>
                                </div>
                            );
                        })}
                    </div>
                </DragDropContext>
            ) : null}
        </div>
    );
};

export default TrainingCalendarGrid;
