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

    const BLOCK_COLORS = ['#3B82F6', '#A855F7', '#EAB308', '#10B981', '#F43F5E', '#06B6D4', '#EC4899'];

    return (
        <>
            {/* Backdrop */}
            <div
                className="fixed inset-0 z-[150] bg-black/70 backdrop-blur-sm"
                onClick={onClose}
            />

            {/* Drawer — full-height right panel */}
            <div
                className="fixed top-0 right-0 bottom-0 z-[151] w-full max-w-sm flex flex-col shadow-2xl"
                style={{ backgroundColor: 'var(--md-sys-color-surface-container)' }}
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div
                    className="flex items-center justify-between px-5 py-4 border-b shrink-0"
                    style={{ borderColor: 'var(--md-sys-color-outline-variant)' }}
                >
                    <div>
                        <h2 className="text-label-lg font-black uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                            Plantillas de Estructura
                        </h2>
                        <p className="text-label-sm opacity-50 mt-0.5" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                            {STRUCTURE_TEMPLATES.length} plantillas disponibles
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        className="w-9 h-9 rounded-full flex items-center justify-center transition-colors"
                        style={{ color: 'var(--md-sys-color-on-surface-variant)', backgroundColor: 'var(--md-sys-color-surface-container-high)' }}
                    >
                        <XIcon size={16} />
                    </button>
                </div>

                {/* Search */}
                <div className="px-4 pt-3 pb-2 shrink-0">
                    <div
                        className="flex items-center gap-2 px-3 py-2 rounded-xl border"
                        style={{
                            backgroundColor: 'var(--md-sys-color-surface-container-high)',
                            borderColor: 'var(--md-sys-color-outline-variant)',
                        }}
                    >
                        <SearchIcon size={14} style={{ color: 'var(--md-sys-color-on-surface-variant)' }} />
                        <input
                            type="text"
                            placeholder="Buscar plantilla..."
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                            className="flex-1 bg-transparent text-xs font-medium outline-none placeholder:opacity-40"
                            style={{ color: 'var(--md-sys-color-on-surface)' }}
                        />
                        {search && (
                            <button onClick={() => setSearch('')} style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                                <XIcon size={12} />
                            </button>
                        )}
                    </div>
                </div>

                {/* Tag filters */}
                <div className="px-4 pb-2 shrink-0 overflow-x-auto no-scrollbar">
                    <div className="flex gap-1.5">
                        <button
                            onClick={() => setActiveTag(null)}
                            className={`shrink-0 px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-widest border transition-all ${!activeTag
                                    ? 'bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] border-transparent'
                                    : 'bg-transparent text-[var(--md-sys-color-on-surface-variant)] border-[var(--md-sys-color-outline-variant)]/60'
                                }`}
                        >
                            Todos
                        </button>
                        {ALL_TAGS.map(tag => (
                            <button
                                key={tag}
                                onClick={() => setActiveTag(activeTag === tag ? null : tag)}
                                className={`shrink-0 px-2.5 py-1 rounded-full text-[10px] font-black uppercase tracking-widest border transition-all ${activeTag === tag
                                        ? 'bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] border-transparent'
                                        : 'bg-transparent text-[var(--md-sys-color-on-surface-variant)] border-[var(--md-sys-color-outline-variant)]/60'
                                    }`}
                            >
                                {tag}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Template list */}
                <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar px-4 pb-[max(100px,calc(80px+env(safe-area-inset-bottom)))] space-y-2 pt-1">
                    {filtered.length === 0 && (
                        <div className="text-center py-12 opacity-30">
                            <p className="text-label-lg font-black uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface)' }}>Sin resultados</p>
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
                                className="rounded-2xl border overflow-hidden transition-all"
                                style={{
                                    backgroundColor: isPreview
                                        ? 'var(--md-sys-color-surface-container-highest)'
                                        : 'var(--md-sys-color-surface-container-high)',
                                    borderColor: isPreview
                                        ? 'var(--md-sys-color-primary)'
                                        : 'var(--md-sys-color-outline-variant)',
                                }}
                            >
                                {/* Card header */}
                                <button
                                    className="w-full px-4 py-3.5 text-left flex items-start gap-3"
                                    onClick={() => setPreviewId(isPreview ? null : template.id)}
                                >
                                    <span className="text-2xl shrink-0 mt-0.5">{template.emoji}</span>
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center justify-between gap-2">
                                            <span className="text-label-md font-black uppercase tracking-tight" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                                                {template.name}
                                            </span>
                                            <span className="text-[10px] font-black opacity-40 shrink-0" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                                                {totalWeeks}s
                                            </span>
                                        </div>
                                        <p className="text-[11px] mt-0.5 opacity-60 leading-snug line-clamp-2" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                                            {template.description}
                                        </p>
                                        <div className="flex flex-wrap gap-1 mt-1.5">
                                            {template.tags.slice(0, 3).map(tag => (
                                                <span
                                                    key={tag}
                                                    className="text-[9px] font-black uppercase tracking-widest px-1.5 py-0.5 rounded"
                                                    style={{
                                                        backgroundColor: 'var(--md-sys-color-surface-variant)',
                                                        color: 'var(--md-sys-color-on-surface-variant)',
                                                    }}
                                                >
                                                    {tag}
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                </button>

                                {/* Expanded: visual timeline */}
                                {isPreview && (
                                    <div className="px-4 pb-4 border-t" style={{ borderColor: 'var(--md-sys-color-outline-variant)' }}>
                                        <div className="pt-3 space-y-2">
                                            {/* Mini progress bar */}
                                            <div className="flex h-2 rounded-full overflow-hidden gap-0.5 mb-3">
                                                {template.blocks.map((blk, bi) =>
                                                    blk.mesocycles.map((meso, mi) => {
                                                        const pct = (meso.weeksCount / totalWeeks) * 100;
                                                        const color = BLOCK_COLORS[(bi) % BLOCK_COLORS.length];
                                                        return (
                                                            <div
                                                                key={`${bi}-${mi}`}
                                                                style={{ width: `${pct}%`, backgroundColor: color, opacity: 0.5 + (mi / blk.mesocycles.length) * 0.5 }}
                                                                className="h-full first:rounded-l-full last:rounded-r-full"
                                                                title={`${meso.name} (${meso.weeksCount} sem)`}
                                                            />
                                                        );
                                                    })
                                                )}
                                            </div>
                                            {template.blocks.map((blk, bi) => (
                                                <div key={bi}>
                                                    <div className="flex items-center gap-1.5 mb-1">
                                                        <div className="w-2.5 h-2.5 rounded-sm shrink-0" style={{ backgroundColor: BLOCK_COLORS[bi % BLOCK_COLORS.length] }} />
                                                        <span className="text-[10px] font-black uppercase tracking-widest" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                                                            {blk.name}
                                                        </span>
                                                    </div>
                                                    <div className="pl-4 space-y-0.5">
                                                        {blk.mesocycles.map((meso, mi) => (
                                                            <div key={mi} className="flex items-center justify-between">
                                                                <span className="text-[10px] opacity-70" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                                                                    {meso.name}
                                                                </span>
                                                                <span className="text-[9px] font-black opacity-50" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
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
                                            className="mt-3 w-full py-2.5 rounded-xl font-black text-xs uppercase tracking-widest transition-all"
                                            style={{
                                                backgroundColor: 'var(--md-sys-color-primary)',
                                                color: 'var(--md-sys-color-on-primary)',
                                            }}
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

            {/* Confirm modal */}
            {confirmTemplate && (
                <div className="fixed inset-0 z-[160] flex items-center justify-center p-4" onClick={() => setConfirmId(null)}>
                    <div
                        className="w-full max-w-sm rounded-2xl p-5 shadow-2xl border"
                        style={{
                            backgroundColor: 'var(--md-sys-color-surface-container-highest)',
                            borderColor: 'var(--md-sys-color-outline-variant)',
                        }}
                        onClick={e => e.stopPropagation()}
                    >
                        <div className="text-3xl text-center mb-3">{confirmTemplate.emoji}</div>
                        <h3 className="text-sm font-black text-center uppercase tracking-widest mb-1" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                            {confirmTemplate.name}
                        </h3>
                        <p className="text-xs text-center opacity-60 mb-4 leading-relaxed" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>
                            Se reemplazará la estructura actual de <span className="font-black" style={{ color: 'var(--md-sys-color-on-surface)' }}>{program.name}</span>.{' '}
                            Las sesiones existentes se perderán.
                        </p>
                        <div className="flex gap-2">
                            <button
                                onClick={() => setConfirmId(null)}
                                className="flex-1 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all"
                                style={{
                                    backgroundColor: 'var(--md-sys-color-surface-container-high)',
                                    color: 'var(--md-sys-color-on-surface-variant)',
                                }}
                            >
                                Cancelar
                            </button>
                            <button
                                onClick={() => { onApply(confirmTemplate); setConfirmId(null); onClose(); }}
                                className="flex-1 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all"
                                style={{
                                    backgroundColor: 'var(--md-sys-color-primary)',
                                    color: 'var(--md-sys-color-on-primary)',
                                }}
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
