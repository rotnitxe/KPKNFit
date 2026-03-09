import React, { useState, useMemo } from 'react';
import { Program, ProgramWeek, Block, Mesocycle } from '../../types';
import {
    XIcon, PlusIcon, ChevronDownIcon, CalendarIcon,
    AlertTriangleIcon, EditIcon, TrashIcon, GridIcon
} from '../icons';
import { getAbsoluteWeekIndex, checkWeekHasEvent } from '../../utils/programHelpers';
import StructureGalleryDrawer from './StructureGalleryDrawer';

interface StructureDrawerProps {
    isOpen: boolean;
    onClose: () => void;
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
    inline?: boolean;
}

const GOAL_OPTIONS = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'];
const BLOCK_COLORS = ['#3B82F6', '#A855F7', '#EAB308', '#10B981', '#F43F5E', '#06B6D4', '#EC4899'];

const StructureDrawer: React.FC<StructureDrawerProps> = ({
    isOpen, onClose, program, isCyclic, selectedBlockId, selectedWeekId,
    onSelectBlock, onSelectWeek, onUpdateProgram, onEditWeek,
    onShowAdvancedTransition, onShowSimpleTransition, onOpenEventModal,
    inline = false,
}) => {
    const [isGalleryOpen, setIsGalleryOpen] = useState(false);
    const [showAddBlockConfirm, setShowAddBlockConfirm] = useState(false);
    const [pendingAddBlockMacroIdx, setPendingAddBlockMacroIdx] = useState<number | null>(null);

    const blockLabel = program.blockLabel || 'bloque';
    const blockCount = program.macrocycles.reduce((acc, m) => acc + (m.blocks || []).length, 0);

    const update = (fn: (p: Program) => void) => {
        if (!onUpdateProgram) return;
        const clone = JSON.parse(JSON.stringify(program));
        fn(clone);
        onUpdateProgram(clone);
    };

    const handleApplyTemplate = (template: any) => {
        update(p => {
            p.macrocycles = template.macrocycles.map((m: any) => ({
                id: crypto.randomUUID(),
                name: m.name,
                blocks: template.blocks.map((b: any) => ({
                    id: crypto.randomUUID(),
                    name: b.name,
                    mesocycles: b.mesocycles.map((meso: any) => ({
                        id: crypto.randomUUID(),
                        name: meso.name,
                        goal: meso.goal,
                        weeks: Array.from({ length: meso.weeksCount }).map((_, i) => ({
                            id: crypto.randomUUID(),
                            name: `Semana ${i + 1}`,
                            sessions: []
                        }))
                    }))
                }))
            }));
        });
    };

    const totalWeeks = useMemo(() => program.macrocycles.reduce((acc, m) =>
        acc + (m.blocks || []).reduce((ba, b) =>
            ba + b.mesocycles.reduce((ma, me) => ma + me.weeks.length, 0), 0), 0), [program]);

    if (!isOpen) return null;

    const containerStyle = inline
        ? "flex-1 flex flex-col p-4 pt-2 bg-transparent"
        : "fixed inset-0 z-[110] bg-[var(--md-sys-color-surface)] overflow-y-auto p-6 animate-fade-in";

    return (
        <div className={containerStyle}>
            {/* ── Toolbar Superior ── */}
            <div className="flex items-center justify-between mb-8 gap-4 px-2">
                <div className="flex flex-col gap-1">
                    <h2 className="text-[10px] font-black uppercase tracking-widest text-[var(--md-sys-color-on-surface-variant)] opacity-60">Estructura</h2>
                    <div className="flex items-center p-1 bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl shadow-sm">
                        <button
                            onClick={() => update(p => { p.blockLabel = 'bloque'; })}
                            className={`px-4 py-2 text-[10px] font-black uppercase tracking-widest rounded-xl transition-all ${blockLabel === 'bloque' ? 'bg-white text-[var(--md-sys-color-primary)] shadow-md' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}
                        >
                            Bloques
                        </button>
                        <button
                            onClick={() => update(p => { p.blockLabel = 'mesociclo'; })}
                            className={`px-4 py-2 text-[10px] font-black uppercase tracking-widest rounded-xl transition-all ${blockLabel === 'mesociclo' ? 'bg-white text-[var(--md-sys-color-primary)] shadow-md' : 'text-[var(--md-sys-color-on-surface-variant)]'}`}
                        >
                            Mesociclos
                        </button>
                    </div>
                </div>

                <div className="flex items-center gap-2">
                    <button
                        onClick={() => setIsGalleryOpen(true)}
                        className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] text-[10px] font-black uppercase tracking-widest hover:brightness-110 active:scale-95 transition-all shadow-lg"
                    >
                        <GridIcon size={14} /> Plantillas
                    </button>
                    {!inline && (
                        <button onClick={onClose} className="w-12 h-12 rounded-full bg-white border border-[var(--md-sys-color-outline-variant)] flex items-center justify-center text-[var(--md-sys-color-on-surface)] shadow-lg active:scale-90 transition-transform">
                            <XIcon size={24} />
                        </button>
                    )}
                </div>
            </div>

            {/* ── Listado de Macrociclos (usualmente 1) ── */}
            <div className="flex flex-col gap-10 pb-40">
                {program.macrocycles.map((macro, macroIdx) => (
                    <div key={macro.id} className="flex flex-col gap-4">
                        {/* Macrocycle Header */}
                        <div className="flex items-center gap-3 px-4 py-1">
                            <h3 className="text-title-sm font-black uppercase tracking-[0.2em] text-[var(--md-sys-color-primary)]">{macro.name}</h3>
                            <div className="h-px flex-1 bg-gradient-to-r from-[var(--md-sys-color-primary)]/30 to-transparent" />
                            <span className="text-[10px] font-black text-[var(--md-sys-color-on-surface-variant)] opacity-60 uppercase tracking-widest">{totalWeeks} Semanas totales</span>
                        </div>

                        {/* Blocks as Cards */}
                        <div className="flex flex-col gap-6">
                            {(macro.blocks || []).map((block, blockIdx) => {
                                const color = BLOCK_COLORS[(macroIdx * 10 + blockIdx) % BLOCK_COLORS.length];
                                const isSelected = block.id === selectedBlockId;
                                const blockWeeks = block.mesocycles.reduce((a, m) => a + m.weeks.length, 0);

                                return (
                                    <div
                                        key={block.id}
                                        className={`group relative flex flex-col rounded-[2.5rem] border transition-all duration-300 shadow-xl overflow-hidden ${isSelected ? 'border-[var(--md-sys-color-primary)]/50 bg-[var(--md-sys-color-surface-container-high)] ring-4 ring-[var(--md-sys-color-primary)]/5' : 'border-[var(--md-sys-color-outline-variant)] bg-white hover:border-[var(--md-sys-color-primary)]/30'}`}
                                    >
                                        {/* Left color bar */}
                                        <div className="absolute left-0 top-0 bottom-0 w-2.5" style={{ backgroundColor: color }} />

                                        {/* Block Card Header */}
                                        <div className="p-6 pb-3 flex items-start justify-between gap-4 ml-2">
                                            <div className="flex-1 min-w-0">
                                                <input
                                                    type="text"
                                                    value={block.name}
                                                    onChange={(e) => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].name = e.target.value; })}
                                                    className="bg-transparent text-xl font-black uppercase tracking-tight text-[var(--md-sys-color-on-surface)] w-full border-none p-0 focus:ring-0 outline-none placeholder:opacity-20"
                                                    placeholder="Sin nombre"
                                                />
                                                <div className="flex items-center gap-3 mt-2">
                                                    <span className="text-[10px] font-black uppercase tracking-widest text-[var(--md-sys-color-on-surface-variant)]">{blockWeeks} SEMANAS</span>
                                                    <span className="text-[10px] font-black uppercase tracking-widest text-[var(--md-sys-color-on-surface-variant)] opacity-30 select-none">ID: {block.id.slice(0, 4)}</span>
                                                </div>
                                            </div>
                                            <div className="flex items-center gap-1 opacity-40 group-hover:opacity-100 transition-opacity">
                                                <button
                                                    onClick={() => update(p => {
                                                        const target = p.macrocycles[macroIdx].blocks;
                                                        if (target) {
                                                            const idx = target.findIndex(b => b.id === block.id);
                                                            target.splice(idx, 1);
                                                        }
                                                    })}
                                                    className="w-10 h-10 rounded-xl flex items-center justify-center text-[var(--md-sys-color-error)] hover:bg-[var(--md-sys-color-error-container)]/50 transition-colors"
                                                >
                                                    <TrashIcon size={18} />
                                                </button>
                                            </div>
                                        </div>

                                        {/* Mesocycles / Phases List */}
                                        <div className="px-6 pb-6 flex flex-col gap-5 mt-2 ml-2">
                                            {block.mesocycles.map((meso, mesoIdx) => {
                                                const filledWeeks = meso.weeks.filter(w => w.sessions.length > 0).length;
                                                const pctFilled = (filledWeeks / meso.weeks.length) * 100;

                                                return (
                                                    <div key={meso.id} className="relative pl-6 border-l-2 border-[var(--md-sys-color-outline-variant)] py-1">
                                                        <div className="flex items-center justify-between gap-4 mb-3">
                                                            <div className="flex-1 min-w-0 flex items-center gap-3">
                                                                <input
                                                                    type="text"
                                                                    value={meso.name}
                                                                    onChange={(e) => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx].name = e.target.value; })}
                                                                    className="bg-transparent text-xs font-black uppercase tracking-wider text-[var(--md-sys-color-on-surface)] w-full border-none p-0 focus:ring-0 outline-none"
                                                                />
                                                                <select
                                                                    value={meso.goal}
                                                                    onChange={(e) => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx].goal = e.target.value as any; })}
                                                                    className="bg-[var(--md-sys-color-surface-container-high)] border-none text-[9px] font-black uppercase tracking-widest text-[var(--md-sys-color-primary)] rounded-lg px-2 py-1.5 focus:ring-0 outline-none ring-1 ring-[var(--md-sys-color-outline-variant)]"
                                                                >
                                                                    {GOAL_OPTIONS.map(g => <option key={g} value={g} className="bg-white text-black font-bold uppercase">{g}</option>)}
                                                                </select>
                                                            </div>
                                                            <button
                                                                onClick={() => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].mesocycles.splice(mesoIdx, 1); })}
                                                                className="text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-error)] transition-colors p-1.5"
                                                            >
                                                                <TrashIcon size={14} />
                                                            </button>
                                                        </div>

                                                        {/* Week Management & Markers */}
                                                        <div className="flex flex-wrap items-center gap-2">
                                                            {meso.weeks.map((week, weekIdx) => {
                                                                const absIdx = getAbsoluteWeekIndex(program, block.id, week.id);
                                                                const hasEvent = checkWeekHasEvent(program, absIdx);
                                                                const isSelectedWeek = week.id === selectedWeekId;

                                                                return (
                                                                    <div key={week.id} className="relative group/week">
                                                                        <button
                                                                            onClick={() => { onSelectBlock(block.id); onSelectWeek(week.id); }}
                                                                            className={`px-3 py-1.5 rounded-xl border text-[10px] font-black uppercase tracking-widest transition-all ${isSelectedWeek ? 'bg-[var(--md-sys-color-primary)] text-white border-transparent shadow-lg scale-105' : 'bg-white border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-container-high)] hover:text-black hover:border-[var(--md-sys-color-primary)]/30'}`}
                                                                        >
                                                                            W{weekIdx + 1}
                                                                            {hasEvent && <CalendarIcon size={10} className="inline ml-1 text-inherit" />}
                                                                        </button>
                                                                        {/* Remove week button on hover */}
                                                                        {meso.weeks.length > 1 && (
                                                                            <button
                                                                                onClick={(e) => { e.stopPropagation(); update(p => { p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx].weeks.splice(weekIdx, 1); }); }}
                                                                                className="absolute -top-1.5 -right-1.5 w-4.5 h-4.5 rounded-full bg-[var(--md-sys-color-error)] text-white opacity-0 group-hover/week:opacity-100 flex items-center justify-center transition-all scale-75 hover:scale-100 shadow-md"
                                                                            >
                                                                                <XIcon size={10} />
                                                                            </button>
                                                                        )}
                                                                    </div>
                                                                );
                                                            })}
                                                            <button
                                                                onClick={() => update(p => {
                                                                    const m = p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx];
                                                                    m.weeks.push({ id: crypto.randomUUID(), name: `Semana ${m.weeks.length + 1}`, sessions: [] });
                                                                })}
                                                                className="w-10 h-8 rounded-xl border border-dashed border-[var(--md-sys-color-outline-variant)] flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-primary)] hover:text-white hover:border-transparent transition-all shadow-sm"
                                                            >
                                                                <PlusIcon size={14} />
                                                            </button>
                                                        </div>

                                                        {/* Phase Progress Bar */}
                                                        <div className="mt-5 h-1.5 w-full bg-[var(--md-sys-color-surface-container)] rounded-full overflow-hidden">
                                                            <div
                                                                className="h-full bg-[var(--md-sys-color-primary)] transition-all duration-700 rounded-full shadow-[0_0_8px_rgba(var(--md-sys-color-primary-rgb),0.4)]"
                                                                style={{ width: `${pctFilled}%` }}
                                                            />
                                                        </div>
                                                    </div>
                                                );
                                            })}

                                            <button
                                                onClick={() => update(p => {
                                                    p.macrocycles[macroIdx].blocks![blockIdx].mesocycles.push({
                                                        id: crypto.randomUUID(),
                                                        name: 'Nueva Fase',
                                                        goal: 'Custom',
                                                        weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }]
                                                    });
                                                })}
                                                className="w-full py-5 rounded-[2rem] border-2 border-dashed border-[var(--md-sys-color-outline-variant)] text-[10px] font-black uppercase tracking-widest text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-primary-container)] hover:text-[var(--md-sys-color-on-primary-container)] hover:border-transparent transition-all flex items-center justify-center gap-2 shadow-sm"
                                            >
                                                <PlusIcon size={16} /> Añadir Fase de {blockLabel}
                                            </button>
                                        </div>
                                    </div>
                                );
                            })}

                            <button
                                onClick={() => {
                                    if (isCyclic && blockCount === 1) {
                                        setPendingAddBlockMacroIdx(macroIdx);
                                        setShowAddBlockConfirm(true);
                                    } else {
                                        update(p => {
                                            p.macrocycles[macroIdx].blocks!.push({
                                                id: crypto.randomUUID(),
                                                name: 'Nuevo Bloque',
                                                mesocycles: [{
                                                    id: crypto.randomUUID(),
                                                    name: 'Base',
                                                    goal: 'Acumulación',
                                                    weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }]
                                                }]
                                            });
                                        });
                                    }
                                }}
                                className="w-full py-10 rounded-[3.5rem] border-4 border-dashed border-[var(--md-sys-color-outline-variant)] group hover:bg-[var(--md-sys-color-primary-container)] hover:border-transparent transition-all flex flex-col items-center justify-center gap-4 bg-white/40"
                            >
                                <div className="w-14 h-14 rounded-full bg-[var(--md-sys-color-surface-container-high)] flex items-center justify-center text-[var(--md-sys-color-on-surface-variant)] group-hover:bg-[var(--md-sys-color-primary)] group-hover:text-white transition-all shadow-md">
                                    <PlusIcon size={28} />
                                </div>
                                <span className="text-[11px] font-black uppercase tracking-[0.4em] text-[var(--md-sys-color-on-surface-variant)] group-hover:text-[var(--md-sys-color-primary)]">Crear {blockLabel}</span>
                            </button>
                        </div>
                    </div>
                ))}
            </div>

            {/* ── Secciones Secundarias ── */}
            <div className="flex flex-col gap-12 mt-20 px-4 border-t border-[var(--md-sys-color-outline-variant)] pt-16 mb-40">
                {/* Events Section */}
                <div>
                    <div className="flex items-center justify-between mb-6">
                        <div className="flex flex-col gap-1">
                            <h3 className="text-title-small font-black uppercase tracking-[0.2em] text-[var(--md-sys-color-on-surface)]">Eventos & Hitos</h3>
                            <p className="text-[10px] font-bold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-60">Competiciones, evaluaciones y fechas clave</p>
                        </div>
                        <button onClick={() => onOpenEventModal()} className="px-5 py-2.5 rounded-2xl bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] text-[10px] font-black uppercase tracking-widest hover:brightness-110 active:scale-95 transition-all shadow-lg">
                            + Añadir Evento
                        </button>
                    </div>
                    {program.events?.length ? (
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            {program.events.map(ev => (
                                <div
                                    key={ev.id}
                                    onClick={() => onOpenEventModal(ev)}
                                    className="p-5 rounded-[2rem] bg-white border border-[var(--md-sys-color-outline-variant)] hover:border-[var(--md-sys-color-primary)]/50 hover:shadow-xl transition-all flex items-center justify-between cursor-pointer group"
                                >
                                    <div className="flex items-center gap-4">
                                        <div className="w-10 h-10 rounded-2xl bg-[var(--md-sys-color-primary-container)] flex items-center justify-center text-[var(--md-sys-color-primary)] shadow-sm">
                                            <CalendarIcon size={18} />
                                        </div>
                                        <div>
                                            <p className="text-xs font-black uppercase tracking-tight text-[var(--md-sys-color-on-surface)]">{ev.title}</p>
                                            <p className="text-[9px] text-[var(--md-sys-color-on-surface-variant)] font-black uppercase tracking-[0.15em] mt-1">{ev.type} · Semana {(ev.calculatedWeek || 0) + 1}</p>
                                        </div>
                                    </div>
                                    <ChevronDownIcon size={14} className="-rotate-90 text-[var(--md-sys-color-outline-variant)] group-hover:text-[var(--md-sys-color-primary)] transition-colors" />
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="py-16 text-center rounded-[3rem] bg-[var(--md-sys-color-surface-container-lowest)] border-2 border-dashed border-[var(--md-sys-color-outline-variant)]">
                            <CalendarIcon size={32} className="mx-auto text-[var(--md-sys-color-outline-variant)] mb-4" />
                            <p className="text-[10px] font-black uppercase tracking-[0.25em] text-[var(--md-sys-color-on-surface-variant)] opacity-40">No hay eventos programados</p>
                        </div>
                    )}
                </div>

                {/* Conversion Section */}
                <div className="flex items-center justify-between p-8 rounded-[2.5rem] bg-[var(--md-sys-color-tertiary-container)] border border-[var(--md-sys-color-tertiary)]/20 shadow-sm">
                    <div className="flex-1 pr-6">
                        <h3 className="text-title-small font-black uppercase tracking-tight text-[var(--md-sys-color-on-tertiary-container)]">¿Cambiar modo de programa?</h3>
                        <p className="text-[10px] font-bold text-[var(--md-sys-color-on-tertiary-container)]/70 mt-2 uppercase tracking-wide leading-relaxed">Los programas simples son cíclicos; los avanzados permiten estructuras asimétricas y periodización compleja.</p>
                    </div>
                    {isCyclic ? (
                        <button onClick={onShowAdvancedTransition} className="px-6 py-4 rounded-2xl bg-[var(--md-sys-color-tertiary)] text-[var(--md-sys-color-on-tertiary)] text-[10px] font-black uppercase tracking-widest shadow-xl active:scale-95 transition-all">
                            Pasar a Avanzado
                        </button>
                    ) : (
                        <button onClick={onShowSimpleTransition} className="px-6 py-4 rounded-2xl bg-white border border-[var(--md-sys-color-outline-variant)] text-[10px] font-black uppercase tracking-widest text-[var(--md-sys-color-on-surface)] hover:bg-[var(--md-sys-color-surface-container-high)] transition-all shadow-md">
                            Simplificar
                        </button>
                    )}
                </div>
            </div>

            {/* Gallery Overlay */}
            <StructureGalleryDrawer
                isOpen={isGalleryOpen}
                onClose={() => setIsGalleryOpen(false)}
                program={program}
                onApply={handleApplyTemplate}
            />

            {/* Add block confirmation modal */}
            {showAddBlockConfirm && (
                <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-[var(--md-sys-color-scrim)]/80 backdrop-blur-md animate-fade-in" onClick={() => setShowAddBlockConfirm(false)}>
                    <div className="bg-[var(--md-sys-color-surface-container-highest)] border border-[var(--md-sys-color-outline-variant)] rounded-[3rem] p-10 max-w-sm w-full shadow-2xl" onClick={e => e.stopPropagation()}>
                        <div className="w-16 h-16 rounded-3xl bg-[var(--md-sys-color-error-container)] flex items-center justify-center mb-8 mx-auto shadow-lg text-[var(--md-sys-color-error)]">
                            <AlertTriangleIcon size={36} />
                        </div>
                        <h3 className="text-xl font-black text-center text-[var(--md-sys-color-on-surface)] uppercase tracking-tight">Evolución de Programa</h3>
                        <p className="text-[11px] font-bold text-[var(--md-sys-color-on-surface-variant)] text-center mt-4 leading-relaxed uppercase tracking-wide">
                            Añadir un bloque adicional convertirá este programa a <span className="text-[var(--md-sys-color-primary)] font-black">Modo Avanzado</span>. Se perderá la repetición cíclica automática de eventos. ¿Deseas evolucionar?
                        </p>
                        <div className="flex flex-col gap-3 mt-10">
                            <button
                                onClick={() => {
                                    if (pendingAddBlockMacroIdx !== null) {
                                        update(p => {
                                            p.structure = 'complex';
                                            p.macrocycles[pendingAddBlockMacroIdx].blocks!.push({
                                                id: crypto.randomUUID(), name: 'Bloque II',
                                                mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] }]
                                            });
                                        });
                                        setShowAddBlockConfirm(false);
                                    }
                                }}
                                className="w-full py-4 rounded-2xl bg-[var(--md-sys-color-primary)] text-white text-xs font-black uppercase tracking-widest shadow-xl active:scale-95 transition-all"
                            >
                                Sí, evolucionar
                            </button>
                            <button
                                onClick={() => setShowAddBlockConfirm(false)}
                                className="w-full py-4 rounded-2xl bg-white text-[var(--md-sys-color-on-surface-variant)] text-xs font-black uppercase tracking-widest hover:text-black border border-[var(--md-sys-color-outline-variant)] transition-all"
                            >
                                Mantener simple
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default StructureDrawer;

