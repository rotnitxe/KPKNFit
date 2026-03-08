import React, { useState, useMemo } from 'react';
import { Program } from '../../types';
import { STRUCTURE_TEMPLATES, StructureTag, StructureTemplate } from '../../data/structureTemplates';
import { XIcon, SearchIcon } from '../icons';

const ALL_TAGS: StructureTag[] = [
    'Recomendado', 'General', 'Powerlifting', 'Culturismo', 'Powerbuilding',
    'GZCL', 'URSS/Bulgaria', 'Clásica', 'Oldschool', 'Atletismo',
];

interface StructureGalleryDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    program: Program;
    onApply: (template: StructureTemplate) => void;
}

const StructureGalleryDrawer: React.FC<StructureGalleryDrawerProps> = ({
    isOpen, onClose, program, onApply,
}) => {
    const [search, setSearch] = useState('');
    const [activeTag, setActiveTag] = useState<StructureTag | null>(null);
    const [previewId, setPreviewId] = useState<string | null>(null);
    const [confirmId, setConfirmId] = useState<string | null>(null);

    const filtered = useMemo(() => {
        let list = STRUCTURE_TEMPLATES;
        if (activeTag) list = list.filter(t => t.tags.includes(activeTag));
        if (search.trim()) {
            const q = search.toLowerCase();
            list = list.filter(t =>
                t.name.toLowerCase().includes(q) ||
                t.description.toLowerCase().includes(q) ||
                t.tags.some(tag => tag.toLowerCase().includes(q))
            );
        }
        return list;
    }, [search, activeTag]);

    const preview = useMemo(() => STRUCTURE_TEMPLATES.find(t => t.id === previewId) || null, [previewId]);
    const confirmTemplate = useMemo(() => STRUCTURE_TEMPLATES.find(t => t.id === confirmId) || null, [confirmId]);

    if (!isOpen) return null;

    const BLOCK_COLORS = [
        'var(--md-sys-color-primary)',
        'var(--md-sys-color-secondary)',
        'var(--md-sys-color-tertiary)',
        'var(--md-sys-color-error)'
    ];

    return (
        <>
            {/* Backdrop M3 */}
            <div
                className="fixed inset-0 z-[150] bg-[var(--md-sys-color-scrim)] opacity-50 transition-opacity"
                onClick={onClose}
            />

            {/* Drawer — full-height right panel M3 */}
            <div
                className="fixed top-0 right-0 bottom-0 z-[151] w-full max-w-sm flex flex-col shadow-2xl bg-[var(--md-sys-color-surface-container)]"
                onClick={e => e.stopPropagation()}
            >
                {/* Header M3 */}
                <div
                    className="flex items-center justify-between px-6 py-5 border-b shrink-0 border-[var(--md-sys-color-outline-variant)]"
                >
                    <div>
                        <h2 className="text-title-md font-bold text-[var(--md-sys-color-on-surface)]">
                            Plantillas
                        </h2>
                        <p className="text-body-sm text-[var(--md-sys-color-on-surface-variant)] mt-1">
                            {STRUCTURE_TEMPLATES.length} disponibles
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        className="w-10 h-10 rounded-full flex items-center justify-center transition-colors text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-container-highest)] hover:text-[var(--md-sys-color-on-surface)]"
                    >
                        <XIcon size={20} />
                    </button>
                </div>

                {/* Search M3 */}
                <div className="px-5 pt-4 pb-3 shrink-0">
                    <div
                        className="flex items-center gap-3 px-4 py-3 rounded-full border border-[var(--md-sys-color-outline-variant)] bg-[var(--md-sys-color-surface-container-high)] text-[var(--md-sys-color-on-surface)]"
                    >
                        <SearchIcon size={18} className="text-[var(--md-sys-color-on-surface-variant)]" />
                        <input
                            type="text"
                            placeholder="Buscar plantilla..."
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                            className="flex-1 bg-transparent text-body-md outline-none placeholder:text-[var(--md-sys-color-on-surface-variant)] placeholder:opacity-70"
                        />
                        {search && (
                            <button onClick={() => setSearch('')} className="text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)]">
                                <XIcon size={16} />
                            </button>
                        )}
                    </div>
                </div>

                {/* Tag filters M3 */}
                <div className="px-5 pb-3 shrink-0 overflow-x-auto no-scrollbar">
                    <div className="flex gap-2">
                        <button
                            onClick={() => setActiveTag(null)}
                            className={`shrink-0 px-4 py-2 rounded-full text-label-md font-bold transition-all ${!activeTag
                                ? 'bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)]'
                                : 'border border-[var(--md-sys-color-outline)] text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-container-highest)]'
                                }`}
                        >
                            Todos
                        </button>
                        {ALL_TAGS.map(tag => (
                            <button
                                key={tag}
                                onClick={() => setActiveTag(activeTag === tag ? null : tag)}
                                className={`shrink-0 px-4 py-2 rounded-full text-label-md font-bold transition-all ${activeTag === tag
                                    ? 'bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)]'
                                    : 'border border-[var(--md-sys-color-outline)] text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-container-highest)]'
                                    }`}
                            >
                                {tag}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Template list M3 */}
                <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar px-5 pb-[max(100px,calc(80px+env(safe-area-inset-bottom)))] space-y-3 pt-2">
                    {filtered.length === 0 && (
                        <div className="text-center py-12 opacity-50">
                            <p className="text-body-lg text-[var(--md-sys-color-on-surface)]">No se encontraron resultados</p>
                        </div>
                    )}
                    {filtered.map(template => {
                        const isPreview = previewId === template.id;
                        const totalBlocks = template.blocks.length;
                        const totalWeeks = template.blocks.reduce((a, b) =>
                            a + b.mesocycles.reduce((ba, m) => ba + m.weeksCount, 0), 0);

                        return (
                            <div
                                key={template.id}
                                className={`rounded-[24px] overflow-hidden transition-all ${isPreview ? 'bg-[var(--md-sys-color-surface-container-highest)] border-2 border-[var(--md-sys-color-primary)]' : 'bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)] hover:bg-[var(--md-sys-color-surface-container)]'}`}
                            >
                                {/* Card header M3 */}
                                <button
                                    className="w-full px-5 py-4 text-left flex items-start gap-4"
                                    onClick={() => setPreviewId(isPreview ? null : template.id)}
                                >
                                    <span className="text-3xl shrink-0 mt-1">{template.emoji}</span>
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center justify-between gap-3 mb-1">
                                            <span className="text-title-sm font-bold text-[var(--md-sys-color-on-surface)]">
                                                {template.name}
                                            </span>
                                            <span className="text-label-sm font-bold text-[var(--md-sys-color-secondary)] bg-[var(--md-sys-color-secondary-container)] px-2 py-0.5 rounded-full shrink-0">
                                                {totalWeeks} sem
                                            </span>
                                        </div>
                                        <p className="text-body-sm text-[var(--md-sys-color-on-surface-variant)] leading-snug line-clamp-2">
                                            {template.description}
                                        </p>
                                        <div className="flex flex-wrap gap-2 mt-3">
                                            {template.tags.slice(0, 3).map(tag => (
                                                <span
                                                    key={tag}
                                                    className="text-label-sm px-2 py-1 rounded-md bg-[var(--md-sys-color-surface-container-high)] text-[var(--md-sys-color-on-surface-variant)]"
                                                >
                                                    {tag}
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                </button>

                                {/* Expanded: visual timeline M3 */}
                                {isPreview && (
                                    <div className="px-5 pb-5 border-t border-[var(--md-sys-color-outline-variant)]">
                                        <div className="pt-4 space-y-3">
                                            {/* Mini progress bar */}
                                            <div className="flex h-2.5 rounded-full overflow-hidden gap-1 mb-4">
                                                {template.blocks.map((blk, bi) =>
                                                    blk.mesocycles.map((meso, mi) => {
                                                        const pct = (meso.weeksCount / totalWeeks) * 100;
                                                        const color = BLOCK_COLORS[(bi) % BLOCK_COLORS.length];
                                                        return (
                                                            <div
                                                                key={`${bi}-${mi}`}
                                                                style={{ width: `${pct}%`, backgroundColor: color, opacity: 0.6 + (mi / blk.mesocycles.length) * 0.4 }}
                                                                className="h-full first:rounded-l-full last:rounded-r-full"
                                                                title={`${meso.name} (${meso.weeksCount} sem)`}
                                                            />
                                                        );
                                                    })
                                                )}
                                            </div>
                                            {template.blocks.map((blk, bi) => (
                                                <div key={bi}>
                                                    <div className="flex items-center gap-2 mb-2">
                                                        <div className="w-3 h-3 rounded-sm shrink-0" style={{ backgroundColor: BLOCK_COLORS[bi % BLOCK_COLORS.length] }} />
                                                        <span className="text-label-md font-bold uppercase tracking-wide text-[var(--md-sys-color-on-surface)]">
                                                            {blk.name}
                                                        </span>
                                                    </div>
                                                    <div className="pl-5 space-y-2 relative before:absolute before:inset-y-1 before:left-1 before:w-[2px] before:bg-[var(--md-sys-color-surface-container-highest)]">
                                                        {blk.mesocycles.map((meso, mi) => (
                                                            <div key={mi} className="flex items-center justify-between relative">
                                                                <div className="absolute -left-5 top-1/2 -translate-y-1/2 w-2 h-[2px]" style={{ backgroundColor: BLOCK_COLORS[bi % BLOCK_COLORS.length] }} />
                                                                <span className="text-body-sm text-[var(--md-sys-color-on-surface-variant)]">
                                                                    {meso.name}
                                                                </span>
                                                                <span className="text-label-sm font-bold text-[var(--md-sys-color-on-surface-variant)]">
                                                                    {meso.weeksCount}s · {meso.goal}
                                                                </span>
                                                            </div>
                                                        ))}
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                        <button
                                            onClick={() => setConfirmId(template.id)}
                                            className="mt-6 w-full py-3 rounded-full text-label-large font-bold bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] hover:brightness-110 shadow-sm transition-all"
                                        >
                                            Aplicar plantilla
                                        </button>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* Confirm modal M3 */}
            {confirmTemplate && (
                <div className="fixed inset-0 z-[160] flex items-center justify-center p-4 bg-[var(--md-sys-color-scrim)] opacity-95 transition-opacity" onClick={() => setConfirmId(null)}>
                    <div
                        className="w-full max-w-sm rounded-[28px] p-6 shadow-xl bg-[var(--md-sys-color-surface-container-highest)]"
                        onClick={e => e.stopPropagation()}
                    >
                        <div className="text-5xl text-center mb-4">{confirmTemplate.emoji}</div>
                        <h3 className="text-title-md font-bold text-center text-[var(--md-sys-color-on-surface)] mb-2">
                            {confirmTemplate.name}
                        </h3>
                        <p className="text-body-md text-center text-[var(--md-sys-color-on-surface-variant)] mb-8 leading-relaxed">
                            Se reemplazará la estructura actual de <span className="font-bold text-[var(--md-sys-color-on-surface)]">{program.name}</span>.{' '}
                            Las sesiones existentes se perderán.
                        </p>
                        <div className="flex justify-end gap-2">
                            <button
                                onClick={() => setConfirmId(null)}
                                className="px-6 py-2.5 rounded-full text-label-large font-bold transition-all text-[var(--md-sys-color-primary)] hover:bg-[var(--md-sys-color-primary-container)]/10"
                            >
                                Cancelar
                            </button>
                            <button
                                onClick={() => { onApply(confirmTemplate); setConfirmId(null); onClose(); }}
                                className="px-6 py-2.5 rounded-full text-label-large font-bold transition-all bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] hover:brightness-110 shadow-sm"
                            >
                                Confirmar
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
};

export default StructureGalleryDrawer;
