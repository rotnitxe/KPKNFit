import React from 'react';
import { Program } from '../../types';
import { ActivityIcon, PlusIcon, TrashIcon } from '../icons';

const GOAL_OPTIONS = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'];

interface StructureSectionProps {
    program: Program;
    onUpdateProgram: (program: Program) => void;
}

const StructureSection: React.FC<StructureSectionProps> = ({ program, onUpdateProgram }) => {
    const update = (fn: (p: Program) => void) => {
        const clone = JSON.parse(JSON.stringify(program));
        fn(clone);
        onUpdateProgram(clone);
    };

    const totalWeeks = program.macrocycles.reduce((acc, m) =>
        acc + (m.blocks || []).reduce((ba, b) =>
            ba + b.mesocycles.reduce((ma, me) => ma + me.weeks.length, 0), 0), 0);

    const blockColors = ['bg-[var(--md-sys-color-primary)]', 'bg-[var(--md-sys-color-secondary)]', 'bg-[var(--md-sys-color-tertiary)]', 'bg-[var(--md-sys-color-error)]', 'bg-[var(--md-sys-color-primary-container)]'];

    return (
        <div className="space-y-4 bg-[#FEF7FF] p-4 rounded-3xl">
            <h3 className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-[0.2em] flex items-center gap-2">
                <ActivityIcon size={16} className="text-[var(--md-sys-color-primary)]" /> Estructura del Programa
            </h3>

            {/* Timeline bar */}
            {totalWeeks > 0 && (
                <div className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-3">
                        <span className="text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest">Timeline</span>
                        <span className="text-label-small text-[var(--md-sys-color-on-surface)] font-black uppercase tracking-wider">{totalWeeks} semanas</span>
                    </div>
                    <div className="flex gap-1 h-6 overflow-hidden rounded-lg bg-[var(--md-sys-color-surface-container-high)]">
                        {program.macrocycles.flatMap((m, mi) =>
                            (m.blocks || []).map((b, bi) => {
                                const weeks = b.mesocycles.reduce((a, me) => a + me.weeks.length, 0);
                                if (weeks === 0) return null;
                                return (
                                    <div
                                        key={`${mi}-${bi}`}
                                        className={`${blockColors[(mi * 10 + bi) % blockColors.length]} relative group cursor-pointer transition-all hover:brightness-110`}
                                        style={{ flex: weeks }}
                                        title={`${b.name}: ${weeks}sem`}
                                    >
                                        <span className="absolute inset-0 flex items-center justify-center text-[8px] font-black text-white opacity-0 group-hover:opacity-100 transition-opacity truncate px-1">
                                            {b.name}
                                        </span>
                                    </div>
                                );
                            })
                        )}
                    </div>
                </div>
            )}

            {/* Macrocycles */}
            {program.macrocycles.map((macro, macroIdx) => (
                <div key={macro.id} className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] rounded-3xl p-6 space-y-4 shadow-sm">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3 flex-1">
                            <span className="bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] text-[10px] font-black px-3 py-1 rounded-full uppercase tracking-widest">Macro {macroIdx + 1}</span>
                            <input
                                className="bg-transparent text-headline-small font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tighter focus:ring-0 border-none p-0 flex-1 placeholder-[var(--md-sys-color-outline-variant)]"
                                value={macro.name}
                                onChange={e => update(p => { p.macrocycles[macroIdx].name = e.target.value; })}
                                placeholder="NOMBRE MACROCICLO"
                            />
                        </div>
                    </div>

                    {/* Blocks */}
                    <div className="space-y-4 pl-4 border-l-2 border-[var(--md-sys-color-outline-variant)]">
                        {(macro.blocks || []).map((block, blockIdx) => (
                            <div key={block.id} className="bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl p-4 space-y-3 shadow-sm">
                                <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-3 flex-1">
                                        <div className={`w-8 h-8 rounded-lg flex items-center justify-center bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface)] font-black text-label-small uppercase shadow-sm`}>
                                            B{blockIdx + 1}
                                        </div>
                                        <input
                                            className="bg-transparent text-body-medium font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tight focus:ring-0 border-none p-0 flex-1 placeholder-[var(--md-sys-color-outline-variant)]"
                                            value={block.name}
                                            onChange={e => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].name = e.target.value; })}
                                            placeholder="NOMBRE BLOQUE"
                                        />
                                    </div>
                                    <button
                                        onClick={() => { if (window.confirm('¿Eliminar bloque?')) update(p => { p.macrocycles[macroIdx].blocks!.splice(blockIdx, 1); }); }}
                                        className="p-2 text-[var(--md-sys-color-on-surface-variant)]/40 hover:text-[var(--md-sys-color-error)] transition-colors"
                                    >
                                        <TrashIcon size={16} />
                                    </button>
                                </div>

                                {/* Mesocycles */}
                                <div className="space-y-2">
                                    {block.mesocycles.map((meso, mesoIdx) => (
                                        <div key={meso.id} className="flex flex-col gap-3 sm:flex-row sm:items-center justify-between bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-xl p-3 shadow-sm">
                                            <div className="flex items-center gap-3 flex-1">
                                                <div className="w-2 h-2 rounded-full bg-[var(--md-sys-color-primary)] shadow-[0_0_8px_rgba(var(--md-sys-color-primary-rgb),0.4)]" />
                                                <input
                                                    className="bg-transparent text-label-large font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-wider focus:ring-0 border-none p-0 flex-1 placeholder-[var(--md-sys-color-outline-variant)]"
                                                    value={meso.name}
                                                    onChange={e => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx].name = e.target.value; })}
                                                    placeholder="NOMBRE MESO"
                                                />
                                            </div>
                                            <div className="flex items-center gap-3 self-end sm:self-auto">
                                                <div className="relative">
                                                    <select
                                                        className="appearance-none bg-[var(--md-sys-color-surface-container)] text-[10px] text-[var(--md-sys-color-on-surface-variant)] border border-[var(--md-sys-color-outline-variant)] pl-3 pr-8 py-1 rounded-lg font-black uppercase tracking-widest focus:ring-2 focus:ring-[var(--md-sys-color-primary)]/20 focus:border-[var(--md-sys-color-primary)] outline-none transition-all"
                                                        value={meso.goal}
                                                        onChange={e => update(p => { p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx].goal = e.target.value as any; })}
                                                    >
                                                        {GOAL_OPTIONS.map(g => <option key={g} value={g}>{g}</option>)}
                                                    </select>
                                                </div>
                                                <span className="text-label-small text-[var(--md-sys-color-on-surface-variant)] font-black uppercase tracking-widest">{meso.weeks.length} sem</span>
                                                <button
                                                    onClick={() => update(p => {
                                                        const m = p.macrocycles[macroIdx].blocks![blockIdx].mesocycles[mesoIdx];
                                                        m.weeks.push({ id: crypto.randomUUID(), name: `Semana ${m.weeks.length + 1}`, sessions: [] });
                                                    })}
                                                    className="w-7 h-7 rounded-lg flex items-center justify-center bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] hover:bg-[var(--md-sys-color-primary)] hover:text-[var(--md-sys-color-on-primary)] transition-all"
                                                >
                                                    <PlusIcon size={14} />
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                </div>

                                <button
                                    onClick={() => update(p => {
                                        p.macrocycles[macroIdx].blocks![blockIdx].mesocycles.push({
                                            id: crypto.randomUUID(), name: 'Nuevo Meso', goal: 'Acumulación', weeks: [],
                                        });
                                    })}
                                    className="w-full py-3 text-label-small font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-[0.2em] border-2 border-dashed border-[var(--md-sys-color-outline-variant)] rounded-xl flex items-center justify-center gap-2 hover:bg-[var(--md-sys-color-surface)] hover:text-[var(--md-sys-color-primary)] transition-all group"
                                >
                                    <PlusIcon size={12} className="group-hover:scale-110 transition-transform" /> Mesociclo
                                </button>
                            </div>
                        ))}

                        <button
                            onClick={() => update(p => {
                                p.macrocycles[macroIdx].blocks!.push({
                                    id: crypto.randomUUID(), name: 'Nuevo Bloque',
                                    mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [] }],
                                });
                            })}
                            className="w-full py-4 text-label-large font-black text-[var(--md-sys-color-on-surface)] bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] rounded-[2rem] flex items-center justify-center gap-2 hover:shadow-lg transition-all active:scale-[0.98] uppercase tracking-widest shadow-sm"
                        >
                            <PlusIcon size={16} /> Añadir Bloque
                        </button>
                    </div>
                </div>
            ))}
        </div>
    );
};

export default StructureSection;
