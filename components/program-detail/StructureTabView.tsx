import React, { useState, useMemo } from 'react';
import { Program, ProgramWeek } from '../../types';
import { PlusIcon, ChevronDownIcon, CalendarIcon, AlertTriangleIcon, TrashIcon, EditIcon } from '../icons';
import EventSessionsManager from './EventSessionsManager';
import StructureGalleryDrawer from './StructureGalleryDrawer';
import { getAbsoluteWeekIndex, checkWeekHasEvent } from '../../utils/programHelpers';
import { StructureTemplate } from '../../data/structureTemplates';

const GOAL_OPTIONS = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'] as const;
const BLOCK_COLORS = ['#3B82F6', '#A855F7', '#EAB308', '#10B981', '#F43F5E', '#06B6D4', '#EC4899'];

type NamingMode = 'bloque' | 'mesociclo';

interface StructureTabViewProps {
    program: Program;
    isCyclic: boolean;
    selectedBlockId: string | null;
    selectedWeekId: string | null;
    onSelectBlock: (id: string) => void;
    onSelectWeek: (id: string) => void;
    onUpdateProgram?: (program: Program) => void;
    onEditWeek: (info: {
        macroIndex: number; blockIndex: number; mesoIndex: number;
        weekIndex: number; week: ProgramWeek; isSimple: boolean;
    }) => void;
    onShowAdvancedTransition: () => void;
    onShowSimpleTransition: () => void;
    onOpenEventModal: (data?: any) => void;
}

const StructureTabView: React.FC<StructureTabViewProps> = ({
    program, isCyclic, selectedBlockId, selectedWeekId,
    onSelectBlock, onSelectWeek, onUpdateProgram, onEditWeek,
    onShowAdvancedTransition, onShowSimpleTransition, onOpenEventModal,
}) => {
    const [namingMode, setNamingMode] = useState<NamingMode>('bloque');
    const [showGallery, setShowGallery] = useState(false);
    const [showAddBlockConfirm, setShowAddBlockConfirm] = useState(false);
    const [pendingAddBlockMacroIdx, setPendingAddBlockMacroIdx] = useState<number | null>(null);
    const [expandedMesoIds, setExpandedMesoIds] = useState<Set<string>>(new Set());
    const [renamingId, setRenamingId] = useState<string | null>(null);
    const [renameValue, setRenameValue] = useState('');

    const blockLabel = namingMode === 'bloque' ? 'Bloque' : 'Mesociclo';
    const mesoLabel = namingMode === 'bloque' ? 'Fase' : 'Submesociclo';

    const update = (fn: (p: Program) => void) => {
        if (!onUpdateProgram) return;
        const clone = JSON.parse(JSON.stringify(program));
        fn(clone);
        onUpdateProgram(clone);
    };

    const events = useMemo(() => program.events || [], [program.events]);
    const cycleLength = useMemo(() =>
        program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length || 1,
        [program.macrocycles]);

    const totalWeeks = useMemo(() =>
        program.macrocycles.reduce((acc, m) =>
            acc + (m.blocks || []).reduce((ba, b) =>
                ba + b.mesocycles.reduce((ma, me) => ma + me.weeks.length, 0), 0), 0),
        [program.macrocycles]);

    const blockCount = useMemo(() =>
        program.macrocycles.reduce((acc, m) => acc + (m.blocks || []).length, 0),
        [program.macrocycles]);

    // Get absolute week index for event checking
    const getWeekHasEvent = (blockId: string, weekId: string) => {
        const absIdx = getAbsoluteWeekIndex(program, blockId, weekId);
        return checkWeekHasEvent(program, absIdx);
    };

    // Get next event relative to current week count in a block
    const getEventForWeekAbsIdx = (absIdx: number) => {
        return events.find(e => {
            if (e.repeatEveryXCycles) return ((absIdx + 1) % (e.repeatEveryXCycles * cycleLength)) === 0;
            return e.calculatedWeek === absIdx;
        }) || null;
    };

    const handleAddBlockClick = (macroIdx: number) => {
        if (isCyclic && blockCount === 1) {
            setPendingAddBlockMacroIdx(macroIdx);
            setShowAddBlockConfirm(true);
        } else {
            doAddBlock(macroIdx);
        }
    };

    const doAddBlock = (macroIdx: number) => {
        update(p => {
            const m = p.macrocycles[macroIdx];
            if (m?.blocks) m.blocks.push({
                id: crypto.randomUUID(), name: 'Nuevo ' + blockLabel,
                mesocycles: [{
                    id: crypto.randomUUID(), name: 'Fase Inicial',
                    goal: 'Acumulación' as any,
                    weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }],
                }],
            });
        });
        setShowAddBlockConfirm(false);
        setPendingAddBlockMacroIdx(null);
    };

    const handleApplyTemplate = (template: StructureTemplate) => {
        update(p => {
            p.structure = 'complex';
            p.macrocycles = template.blocks.length > 0
                ? [{
                    id: crypto.randomUUID(),
                    name: template.macrocycleName,
                    blocks: template.blocks.map(blk => ({
                        id: crypto.randomUUID(),
                        name: blk.name,
                        mesocycles: blk.mesocycles.map(meso => ({
                            id: crypto.randomUUID(),
                            name: meso.name,
                            goal: meso.goal as any,
                            weeks: Array.from({ length: meso.weeksCount }, (_, i) => ({
                                id: crypto.randomUUID(),
                                name: `Semana ${i + 1}`,
                                sessions: [],
                            })),
                        })),
                    })),
                }]
                : p.macrocycles;
            p.events = [];
        });
    };

    const startRename = (id: string, currentName: string) => {
        setRenamingId(id);
        setRenameValue(currentName);
    };

    const commitRename = (fn: (p: Program, val: string) => void) => {
        if (!renameValue.trim()) { setRenamingId(null); return; }
        update(p => fn(p, renameValue.trim()));
        setRenamingId(null);
    };

    // Flatten all blocks across macrocycles for summary progress bar
    const allBlocks = useMemo(() =>
        program.macrocycles.flatMap((m, mi) =>
            (m.blocks || []).map((b, bi) => ({
                ...b, macroIdx: mi, blockIdx: bi,
                colorIdx: (mi * 10 + bi) % BLOCK_COLORS.length,
                totalWeeks: b.mesocycles.reduce((a, me) => a + me.weeks.length, 0),
            }))
        ), [program.macrocycles]);

    return (
        <div className="flex-1 flex flex-col min-h-0 overflow-hidden" style={{ backgroundColor: 'var(--md-sys-color-background)' }}>

            {/* ── Top toolbar ── */}
            <div className="shrink-0 px-4 pt-4 pb-3 flex items-center justify-between gap-2">
                {/* Naming mode toggle */}
                <div
                    className="flex items-center rounded-full p-0.5 border gap-0.5"
                    style={{
                        backgroundColor: 'var(--md-sys-color-surface-container)',
                        borderColor: 'var(--md-sys-color-outline-variant)',
                    }}
                >
                    <button
                        onClick={() => setNamingMode('bloque')}
                        className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-widest transition-all ${namingMode === 'bloque'
                                ? 'bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)]'
                                : 'text-[var(--md-sys-color-on-surface-variant)] opacity-50'
                            }`}
                    >
                        Bloques
                    </button>
                    <button
                        onClick={() => setNamingMode('mesociclo')}
                        className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-widest transition-all ${namingMode === 'mesociclo'
                                ? 'bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)]'
                                : 'text-[var(--md-sys-color-on-surface-variant)] opacity-50'
                            }`}
                    >
                        Mesociclos
                    </button>
                </div>
                {/* Plantillas button */}
                {onUpdateProgram && (
                    <button
                        onClick={() => setShowGallery(true)}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[10px] font-black uppercase tracking-widest border transition-all"
                        style={{
                            borderColor: 'var(--md-sys-color-outline-variant)',
                            color: 'var(--md-sys-color-on-surface-variant)',
                            backgroundColor: 'var(--md-sys-color-surface-container)',
                        }}
                    >
                        <span>📋</span> Plantillas
                    </button>
                )}
            </div>

            {/* ── Global progress bar (advanced only) ── */}
            {!isCyclic && totalWeeks > 0 && (
                <div className="shrink-0 px-4 pb-3">
                    <div className="flex h-2.5 rounded-full overflow-hidden gap-px">
                        {allBlocks.map(block => {
                            const pct = totalWeeks > 0 ? (block.totalWeeks / totalWeeks) * 100 : 0;
                            return (
                                <button
                                    key={block.id}
                                    className="h-full transition-all hover:brightness-125"
                                    style={{
                                        width: `${pct}%`,
                                        backgroundColor: BLOCK_COLORS[block.colorIdx],
                                        minWidth: '2px',
                                    }}
                                    onClick={() => onSelectBlock(block.id)}
                                    title={`${block.name} · ${block.totalWeeks} sem`}
                                />
                            );
                        })}
                    </div>
                    <div className="flex items-center justify-between mt-1.5">
                        <span className="text-[10px] font-black opacity-40" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                            {totalWeeks} semanas totales
                        </span>
                        <div className="flex items-center gap-2">
                            {events.map(ev => (
                                <button
                                    key={ev.id}
                                    onClick={() => onOpenEventModal(ev)}
                                    className="flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-black border"
                                    style={{
                                        backgroundColor: 'var(--md-sys-color-tertiary-container)',
                                        borderColor: 'var(--md-sys-color-tertiary)',
                                        color: 'var(--md-sys-color-on-tertiary-container)',
                                    }}
                                >
                                    <CalendarIcon size={9} />
                                    {ev.title}
                                </button>
                            ))}
                            <button
                                onClick={() => onOpenEventModal()}
                                className="text-[9px] font-black opacity-50 hover:opacity-100 transition-opacity"
                                style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                            >
                                + evento
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ── Scroll area ── */}
            <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar px-4 pb-[max(100px,calc(80px+env(safe-area-inset-bottom)))] space-y-3">

                {program.macrocycles.map((macro, macroIdx) => (
                    <div key={macro.id}>
                        {/* Macrocycle name (editable) */}
                        {program.macrocycles.length > 1 && (
                            <div className="flex items-center gap-2 mb-2 px-1">
                                {renamingId === macro.id ? (
                                    <input
                                        autoFocus
                                        value={renameValue}
                                        onChange={e => setRenameValue(e.target.value)}
                                        onBlur={() => commitRename((p, v) => { const m = p.macrocycles[macroIdx]; if (m) m.name = v; })}
                                        onKeyDown={e => { if (e.key === 'Enter') commitRename((p, v) => { const m = p.macrocycles[macroIdx]; if (m) m.name = v; }); if (e.key === 'Escape') setRenamingId(null); }}
                                        className="flex-1 bg-transparent text-[11px] font-black uppercase tracking-widest border-b border-[var(--md-sys-color-primary)] outline-none"
                                        style={{ color: 'var(--md-sys-color-on-surface)' }}
                                    />
                                ) : (
                                    <button onClick={() => startRename(macro.id, macro.name)} className="text-[11px] font-black uppercase tracking-widest opacity-40 hover:opacity-80 transition-opacity" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                                        {macro.name}
                                    </button>
                                )}
                            </div>
                        )}

                        {/* Blocks / Mesocycles */}
                        {(macro.blocks || []).map((block, blockIdx) => {
                            const colorIdx = (macroIdx * 10 + blockIdx) % BLOCK_COLORS.length;
                            const color = BLOCK_COLORS[colorIdx];
                            const isBlockSelected = block.id === selectedBlockId;
                            const blockWeeks = block.mesocycles.reduce((a, me) => a + me.weeks.length, 0);

                            // Compute cyclic event weeks for simple programs
                            let cumWeeks = 0;
                            program.macrocycles.slice(0, macroIdx).forEach(m2 =>
                                (m2.blocks || []).forEach(b2 =>
                                    b2.mesocycles.forEach(me2 => { cumWeeks += me2.weeks.length; })
                                )
                            );
                            (macro.blocks || []).slice(0, blockIdx).forEach(b2 =>
                                b2.mesocycles.forEach(me2 => { cumWeeks += me2.weeks.length; })
                            );

                            return (
                                <div
                                    key={block.id}
                                    className="rounded-2xl border overflow-hidden transition-all"
                                    style={{
                                        borderColor: isBlockSelected ? color : 'var(--md-sys-color-outline-variant)',
                                        backgroundColor: 'var(--md-sys-color-surface-container)',
                                    }}
                                >
                                    {/* Block header */}
                                    <div
                                        className="flex items-center gap-3 px-4 py-3 border-b"
                                        style={{ borderColor: 'var(--md-sys-color-outline-variant)', borderLeftWidth: '3px', borderLeftColor: color }}
                                    >
                                        <button
                                            onClick={() => onSelectBlock(block.id)}
                                            className="flex-1 text-left flex items-center gap-2 min-w-0"
                                        >
                                            <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: color }} />
                                            {renamingId === block.id ? (
                                                <input
                                                    autoFocus
                                                    value={renameValue}
                                                    onChange={e => setRenameValue(e.target.value)}
                                                    onBlur={() => commitRename((p, v) => { const b = p.macrocycles[macroIdx]?.blocks?.[blockIdx]; if (b) b.name = v; })}
                                                    onKeyDown={e => {
                                                        if (e.key === 'Enter') commitRename((p, v) => { const b = p.macrocycles[macroIdx]?.blocks?.[blockIdx]; if (b) b.name = v; });
                                                        if (e.key === 'Escape') setRenamingId(null);
                                                    }}
                                                    className="flex-1 bg-transparent text-label-sm font-black uppercase tracking-tight border-b border-[var(--md-sys-color-primary)] outline-none"
                                                    style={{ color: 'var(--md-sys-color-on-surface)' }}
                                                    onClick={e => e.stopPropagation()}
                                                />
                                            ) : (
                                                <span className="text-label-sm font-black uppercase tracking-tight truncate" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                                                    {block.name}
                                                </span>
                                            )}
                                        </button>
                                        <span className="text-[10px] font-black opacity-40 shrink-0" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                                            {blockWeeks}s
                                        </span>
                                        {onUpdateProgram && (
                                            <button
                                                onClick={() => startRename(block.id, block.name)}
                                                className="p-1 opacity-40 hover:opacity-100 transition-opacity shrink-0"
                                                style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                                            >
                                                <EditIcon size={12} />
                                            </button>
                                        )}
                                        {onUpdateProgram && !isCyclic && blockCount > 1 && (
                                            <button
                                                onClick={() => {
                                                    if (window.confirm(`¿Eliminar "${block.name}"? Se perderán todas sus semanas y sesiones.`)) {
                                                        update(p => {
                                                            const m = p.macrocycles[macroIdx];
                                                            if (m?.blocks) m.blocks.splice(blockIdx, 1);
                                                        });
                                                    }
                                                }}
                                                className="p-1 opacity-30 hover:opacity-80 transition-opacity shrink-0"
                                                style={{ color: 'var(--md-sys-color-error)' }}
                                            >
                                                <TrashIcon size={12} />
                                            </button>
                                        )}
                                    </div>

                                    {/* Block content: mesocycles */}
                                    <div className="p-3 space-y-2">
                                        {block.mesocycles.map((meso, mesoIdx) => {
                                            const mesoKey = meso.id;
                                            const mesoExpanded = expandedMesoIds.has(mesoKey);
                                            let mesoAbsStart = cumWeeks;
                                            block.mesocycles.slice(0, mesoIdx).forEach(m2 => { mesoAbsStart += m2.weeks.length; });

                                            // Progress bar for this meso
                                            const nextEventIdx = events
                                                .filter(ev => !ev.repeatEveryXCycles && ev.calculatedWeek !== undefined)
                                                .map(ev => ev.calculatedWeek as number)
                                                .filter(w => w >= mesoAbsStart && w < mesoAbsStart + meso.weeks.length)
                                                .sort()[0];
                                            const hasLocalEvents = nextEventIdx !== undefined;

                                            return (
                                                <div
                                                    key={meso.id}
                                                    className="rounded-xl border overflow-hidden"
                                                    style={{
                                                        borderColor: 'var(--md-sys-color-outline-variant)',
                                                        backgroundColor: 'var(--md-sys-color-surface-container-high)',
                                                    }}
                                                >
                                                    {/* Meso header */}
                                                    <div className="flex items-center gap-2 px-3 py-2.5">
                                                        <button
                                                            onClick={() => setExpandedMesoIds(prev => {
                                                                const next = new Set(prev);
                                                                next.has(mesoKey) ? next.delete(mesoKey) : next.add(mesoKey);
                                                                return next;
                                                            })}
                                                            className="flex items-center gap-2 flex-1 min-w-0 text-left"
                                                        >
                                                            <ChevronDownIcon
                                                                size={12}
                                                                className={`shrink-0 transition-transform ${mesoExpanded ? 'rotate-0' : '-rotate-90'}`}
                                                                style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                                                            />
                                                            {renamingId === meso.id ? (
                                                                <input
                                                                    autoFocus
                                                                    value={renameValue}
                                                                    onChange={e => setRenameValue(e.target.value)}
                                                                    onBlur={() => commitRename((p, v) => {
                                                                        const t = p.macrocycles[macroIdx]?.blocks?.[blockIdx]?.mesocycles?.[mesoIdx];
                                                                        if (t) t.name = v;
                                                                    })}
                                                                    onKeyDown={e => {
                                                                        if (e.key === 'Enter') commitRename((p, v) => { const t = p.macrocycles[macroIdx]?.blocks?.[blockIdx]?.mesocycles?.[mesoIdx]; if (t) t.name = v; });
                                                                        if (e.key === 'Escape') setRenamingId(null);
                                                                    }}
                                                                    className="flex-1 bg-transparent text-[11px] font-black border-b border-[var(--md-sys-color-primary)] outline-none"
                                                                    style={{ color: 'var(--md-sys-color-on-surface)' }}
                                                                    onClick={e => e.stopPropagation()}
                                                                />
                                                            ) : (
                                                                <span className="text-[11px] font-black truncate" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                                                                    {meso.name}
                                                                </span>
                                                            )}
                                                        </button>
                                                        <select
                                                            value={meso.goal || 'Custom'}
                                                            onChange={e => update(p => {
                                                                const t = p.macrocycles[macroIdx]?.blocks?.[blockIdx]?.mesocycles?.[mesoIdx];
                                                                if (t) t.goal = e.target.value as any;
                                                            })}
                                                            className="bg-transparent text-[10px] font-black border-none p-0 focus:ring-0 cursor-pointer"
                                                            style={{ color: `${color}cc` }}
                                                            onClick={e => e.stopPropagation()}
                                                        >
                                                            {GOAL_OPTIONS.map(g => (
                                                                <option key={g} value={g} className="bg-black text-white">{g}</option>
                                                            ))}
                                                        </select>
                                                        {onUpdateProgram && (
                                                            <button
                                                                onClick={() => startRename(meso.id, meso.name)}
                                                                className="p-1 opacity-30 hover:opacity-80 transition-opacity shrink-0"
                                                                style={{ color: 'var(--md-sys-color-on-surface-variant)' }}
                                                            >
                                                                <EditIcon size={10} />
                                                            </button>
                                                        )}
                                                        {onUpdateProgram && block.mesocycles.length > 1 && (
                                                            <button
                                                                onClick={() => update(p => {
                                                                    const b = p.macrocycles[macroIdx]?.blocks?.[blockIdx];
                                                                    if (b) b.mesocycles.splice(mesoIdx, 1);
                                                                })}
                                                                className="p-1 opacity-20 hover:opacity-60 transition-opacity shrink-0"
                                                                style={{ color: 'var(--md-sys-color-error)' }}
                                                            >
                                                                <TrashIcon size={10} />
                                                            </button>
                                                        )}
                                                    </div>

                                                    {/* Mini progress bar for this meso */}
                                                    {meso.weeks.length > 0 && (
                                                        <div className="px-3 pb-2">
                                                            <div className="relative h-1.5 rounded-full overflow-visible" style={{ backgroundColor: 'var(--md-sys-color-surface-variant)' }}>
                                                                <div
                                                                    className="absolute left-0 top-0 h-full rounded-full"
                                                                    style={{ width: '100%', backgroundColor: color, opacity: 0.25 }}
                                                                />
                                                                {/* Event markers */}
                                                                {events.map(ev => {
                                                                    if (ev.repeatEveryXCycles) return null;
                                                                    const evWeek = ev.calculatedWeek ?? -1;
                                                                    if (evWeek < mesoAbsStart || evWeek >= mesoAbsStart + meso.weeks.length) return null;
                                                                    const pct = ((evWeek - mesoAbsStart) / Math.max(1, meso.weeks.length - 1)) * 100;
                                                                    return (
                                                                        <div
                                                                            key={ev.id}
                                                                            className="absolute top-1/2 -translate-y-1/2 w-2 h-2 rounded-full border border-[var(--md-sys-color-background)] cursor-pointer"
                                                                            style={{
                                                                                left: `${Math.min(pct, 96)}%`,
                                                                                backgroundColor: 'var(--md-sys-color-tertiary)',
                                                                            }}
                                                                            title={`Evento: ${ev.title}`}
                                                                            onClick={() => onOpenEventModal(ev)}
                                                                        />
                                                                    );
                                                                })}
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* Weeks chips */}
                                                    {mesoExpanded && (
                                                        <div className="px-3 pb-3 flex flex-wrap gap-1.5">
                                                            {meso.weeks.map((week, weekIdx) => {
                                                                const absIdx = getAbsoluteWeekIndex(program, block.id, week.id);
                                                                const hasEvent = getWeekHasEvent(block.id, week.id);
                                                                const eventForWeek = getEventForWeekAbsIdx(absIdx);
                                                                const isWeekSelected = week.id === selectedWeekId;

                                                                // Check if this is a cyclic event week (for simple programs)
                                                                const isCyclicEventWeek = isCyclic && events.some(ev =>
                                                                    ev.repeatEveryXCycles &&
                                                                    ((absIdx + 1) % (ev.repeatEveryXCycles * cycleLength)) === 0
                                                                );

                                                                return (
                                                                    <button
                                                                        key={week.id}
                                                                        onClick={() => { onSelectBlock(block.id); onSelectWeek(week.id); }}
                                                                        className={`relative flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-widest border transition-all ${isCyclicEventWeek
                                                                                ? 'border-dashed'
                                                                                : hasEvent
                                                                                    ? ''
                                                                                    : ''
                                                                            }`}
                                                                        style={{
                                                                            backgroundColor: isWeekSelected
                                                                                ? color
                                                                                : isCyclicEventWeek
                                                                                    ? 'var(--md-sys-color-tertiary-container)'
                                                                                    : hasEvent
                                                                                        ? 'var(--md-sys-color-secondary-container)'
                                                                                        : 'var(--md-sys-color-surface-container)',
                                                                            borderColor: isWeekSelected
                                                                                ? color
                                                                                : isCyclicEventWeek
                                                                                    ? 'var(--md-sys-color-tertiary)'
                                                                                    : hasEvent
                                                                                        ? 'var(--md-sys-color-secondary)'
                                                                                        : 'var(--md-sys-color-outline-variant)',
                                                                            color: isWeekSelected
                                                                                ? 'white'
                                                                                : isCyclicEventWeek
                                                                                    ? 'var(--md-sys-color-on-tertiary-container)'
                                                                                    : hasEvent
                                                                                        ? 'var(--md-sys-color-on-secondary-container)'
                                                                                        : 'var(--md-sys-color-on-surface-variant)',
                                                                        }}
                                                                        title={eventForWeek ? `Evento: ${eventForWeek.title}` : isCyclicEventWeek ? 'Semana evento cíclico' : week.name}
                                                                    >
                                                                        {isCyclicEventWeek ? '★' : `S${weekIdx + 1}`}
                                                                        {(hasEvent || isCyclicEventWeek) && (
                                                                            <CalendarIcon size={8} className="shrink-0" />
                                                                        )}
                                                                        {week.sessions.length > 0 && (
                                                                            <span className="opacity-60">·{week.sessions.length}</span>
                                                                        )}
                                                                    </button>
                                                                );
                                                            })}

                                                            {/* Add cyclic event week chip (simple programs) */}
                                                            {isCyclic && onUpdateProgram && (
                                                                <button
                                                                    onClick={() => onOpenEventModal()}
                                                                    className="px-2.5 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-widest border border-dashed transition-all"
                                                                    style={{
                                                                        borderColor: 'var(--md-sys-color-tertiary)',
                                                                        color: 'var(--md-sys-color-tertiary)',
                                                                        backgroundColor: 'transparent',
                                                                    }}
                                                                >
                                                                    + Evento
                                                                </button>
                                                            )}

                                                            {/* Add week button */}
                                                            {onUpdateProgram && (
                                                                <button
                                                                    onClick={() => update(p => {
                                                                        const target = p.macrocycles[macroIdx]?.blocks?.[blockIdx]?.mesocycles?.[mesoIdx];
                                                                        if (target) target.weeks.push({
                                                                            id: crypto.randomUUID(),
                                                                            name: `Semana ${target.weeks.length + 1}`,
                                                                            sessions: [],
                                                                        });
                                                                    })}
                                                                    className="px-2.5 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-widest border border-dashed transition-all"
                                                                    style={{
                                                                        borderColor: 'var(--md-sys-color-outline-variant)',
                                                                        color: 'var(--md-sys-color-on-surface-variant)',
                                                                        backgroundColor: 'transparent',
                                                                    }}
                                                                >
                                                                    <PlusIcon size={10} className="inline" /> Sem
                                                                </button>
                                                            )}

                                                            {/* Remove last week button */}
                                                            {onUpdateProgram && meso.weeks.length > 1 && (
                                                                <button
                                                                    onClick={() => update(p => {
                                                                        const target = p.macrocycles[macroIdx]?.blocks?.[blockIdx]?.mesocycles?.[mesoIdx];
                                                                        if (target && target.weeks.length > 1) target.weeks.pop();
                                                                    })}
                                                                    className="px-2 py-1.5 rounded-lg text-[10px] font-black border border-dashed transition-all opacity-40 hover:opacity-80"
                                                                    style={{
                                                                        borderColor: 'var(--md-sys-color-error)',
                                                                        color: 'var(--md-sys-color-error)',
                                                                        backgroundColor: 'transparent',
                                                                    }}
                                                                    title="Eliminar última semana"
                                                                >
                                                                    <TrashIcon size={10} className="inline" />
                                                                </button>
                                                            )}
                                                        </div>
                                                    )}
                                                </div>
                                            );
                                        })}

                                        {/* Add meso/fase button */}
                                        {onUpdateProgram && (
                                            <button
                                                onClick={() => update(p => {
                                                    const blk = p.macrocycles[macroIdx]?.blocks?.[blockIdx];
                                                    if (!blk) return;
                                                    blk.mesocycles.push({
                                                        id: crypto.randomUUID(),
                                                        name: `Nueva ${mesoLabel}`,
                                                        goal: 'Custom' as any,
                                                        weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }],
                                                    });
                                                })}
                                                className="w-full flex items-center gap-1.5 px-3 py-2 rounded-xl border border-dashed text-[10px] font-black uppercase tracking-widest transition-all"
                                                style={{
                                                    borderColor: 'var(--md-sys-color-outline-variant)',
                                                    color: 'var(--md-sys-color-on-surface-variant)',
                                                }}
                                            >
                                                <PlusIcon size={12} /> {mesoLabel}
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })}

                        {/* Add block button */}
                        {onUpdateProgram && (
                            <button
                                onClick={() => handleAddBlockClick(macroIdx)}
                                className="w-full flex items-center gap-2 px-4 py-2.5 rounded-2xl border border-dashed text-[10px] font-black uppercase tracking-widest transition-all mt-2"
                                style={{
                                    borderColor: 'var(--md-sys-color-outline-variant)',
                                    color: 'var(--md-sys-color-on-surface-variant)',
                                    backgroundColor: 'transparent',
                                }}
                            >
                                <PlusIcon size={14} /> {blockLabel}
                            </button>
                        )}
                    </div>
                ))}

                {/* ── EventSessionsManager ── */}
                {onUpdateProgram && (
                    <div className="mt-4 pt-4 border-t" style={{ borderColor: 'var(--md-sys-color-outline-variant)' }}>
                        <EventSessionsManager program={program} isCyclic={isCyclic} onUpdateProgram={onUpdateProgram} />
                    </div>
                )}

                {/* ── Footer: Transition + Events ── */}
                <div className="mt-4 pt-4 border-t space-y-3" style={{ borderColor: 'var(--md-sys-color-outline-variant)' }}>
                    {/* Cyclic events (simple only) */}
                    {isCyclic && events.length > 0 && (
                        <div>
                            <div className="flex items-center justify-between mb-2">
                                <span className="text-[10px] font-black uppercase tracking-widest opacity-50" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Eventos cíclicos</span>
                                <button onClick={() => onOpenEventModal()} className="text-[10px] font-black" style={{ color: 'var(--md-sys-color-tertiary)' }}>+ Nuevo</button>
                            </div>
                            <div className="flex flex-wrap gap-1.5">
                                {events.map((ev: any) => (
                                    <button
                                        key={ev.id}
                                        onClick={() => onOpenEventModal(ev)}
                                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border text-[10px] font-black transition-all"
                                        style={{
                                            backgroundColor: 'var(--md-sys-color-tertiary-container)',
                                            borderColor: 'var(--md-sys-color-tertiary)',
                                            color: 'var(--md-sys-color-on-tertiary-container)',
                                        }}
                                    >
                                        <CalendarIcon size={10} />
                                        {ev.title}
                                        <span className="opacity-60">c/{ev.repeatEveryXCycles}</span>
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Transition button */}
                    {isCyclic ? (
                        <button
                            onClick={onShowAdvancedTransition}
                            className="w-full py-2.5 rounded-xl text-[10px] font-black uppercase tracking-widest border transition-all"
                            style={{
                                backgroundColor: 'var(--md-sys-color-surface-container-high)',
                                borderColor: 'var(--md-sys-color-outline-variant)',
                                color: 'var(--md-sys-color-on-surface-variant)',
                            }}
                        >
                            Convertir a Avanzado
                        </button>
                    ) : (
                        <button
                            onClick={onShowSimpleTransition}
                            className="w-full py-2.5 rounded-xl text-[10px] font-black uppercase tracking-widest border transition-all"
                            style={{
                                backgroundColor: 'var(--md-sys-color-surface-container-high)',
                                borderColor: 'var(--md-sys-color-outline-variant)',
                                color: 'var(--md-sys-color-on-surface-variant)',
                            }}
                        >
                            Simplificar Programa
                        </button>
                    )}
                </div>
            </div>

            {/* ── Add block confirm modal ── */}
            {showAddBlockConfirm && (
                <>
                    <div className="fixed inset-0 z-[200] bg-black/60" onClick={() => { setShowAddBlockConfirm(false); setPendingAddBlockMacroIdx(null); }} />
                    <div className="fixed inset-0 z-[201] flex items-center justify-center p-4">
                        <div
                            className="rounded-2xl p-5 max-w-sm w-full shadow-xl border"
                            style={{
                                backgroundColor: 'var(--md-sys-color-surface-container-highest)',
                                borderColor: 'var(--md-sys-color-outline-variant)',
                            }}
                            onClick={e => e.stopPropagation()}
                        >
                            <div className="flex items-start gap-3 mb-4">
                                <AlertTriangleIcon size={24} className="text-amber-400 shrink-0" />
                                <div>
                                    <h3 className="text-sm font-black" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                                        Convertir a programa avanzado
                                    </h3>
                                    <p className="text-xs mt-1 leading-relaxed" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                                        Al añadir un {blockLabel.toLowerCase()}, el programa se convierte en <span className="font-black" style={{ color: 'var(--md-sys-color-on-surface)' }}>avanzado</span> y los eventos cíclicos se eliminarán. ¿Continuar?
                                    </p>
                                </div>
                            </div>
                            <div className="flex gap-2">
                                <button
                                    onClick={() => { setShowAddBlockConfirm(false); setPendingAddBlockMacroIdx(null); }}
                                    className="flex-1 py-2.5 rounded-xl text-[10px] font-black uppercase tracking-widest border transition-all"
                                    style={{
                                        backgroundColor: 'var(--md-sys-color-surface-container-high)',
                                        borderColor: 'var(--md-sys-color-outline-variant)',
                                        color: 'var(--md-sys-color-on-surface-variant)',
                                    }}
                                >
                                    Cancelar
                                </button>
                                <button
                                    onClick={() => pendingAddBlockMacroIdx !== null && doAddBlock(pendingAddBlockMacroIdx)}
                                    className="flex-1 py-2.5 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all"
                                    style={{
                                        backgroundColor: 'var(--md-sys-color-primary)',
                                        color: 'var(--md-sys-color-on-primary)',
                                    }}
                                >
                                    Continuar
                                </button>
                            </div>
                        </div>
                    </div>
                </>
            )}

            {/* ── Structure Gallery Drawer ── */}
            {onUpdateProgram && (
                <StructureGalleryDrawer
                    isOpen={showGallery}
                    onClose={() => setShowGallery(false)}
                    program={program}
                    onApply={handleApplyTemplate}
                />
            )}
        </div>
    );
};

export default StructureTabView;
