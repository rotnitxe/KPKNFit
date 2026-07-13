import React, { useState } from 'react';
import { Program } from '../../types';
import { ChevronDownIcon, PlusIcon, CalendarIcon, ActivityIcon, DumbbellIcon } from '../icons';

interface EditorSidebarProps {
    program: Program;
    activeSectionId: string | null;
    onNavigateToSection: (sectionId: string) => void;
    onAddBlock?: (macroIndex: number) => void;
    onAddWeek?: (macroIndex: number, blockIndex: number, mesoIndex: number) => void;
}

const EditorSidebar: React.FC<EditorSidebarProps> = ({
    program, activeSectionId, onNavigateToSection,
    onAddBlock, onAddWeek,
}) => {
    const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set(['macro-0']));

    const toggleNode = (id: string) => {
        setExpandedNodes(prev => {
            const next = new Set(prev);
            next.has(id) ? next.delete(id) : next.add(id);
            return next;
        });
    };

    const sections = [
        { id: 'details', label: 'Detalles', icon: <DumbbellIcon size={18} /> },
        { id: 'structure', label: 'Estructura', icon: <ActivityIcon size={18} /> },
        { id: 'goals', label: 'Metas', icon: <ActivityIcon size={18} /> },
        { id: 'events', label: 'Eventos', icon: <CalendarIcon size={18} /> },
        { id: 'volume', label: 'Volumen', icon: <ActivityIcon size={18} /> },
        { id: 'export', label: 'Exportar', icon: <ActivityIcon size={18} /> },
    ];

    return (
        <div className="w-full h-full bg-[var(--md-sys-color-surface-container-low)] flex flex-col overflow-hidden">
            {/* Header */}
            <div className="px-6 py-6 border-b border-[var(--md-sys-color-outline-variant)]">
                <span className="text-label-small font-bold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-wider">Editor de Programa</span>
            </div>

            {/* Section links */}
            <div className="p-4 space-y-1">
                {sections.map(s => (
                    <button
                        key={s.id}
                        onClick={() => onNavigateToSection(s.id)}
                        className={`w-full flex items-center gap-4 px-4 py-3 rounded-full text-title-small font-bold transition-all ${activeSectionId === s.id
                            ? 'bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)]'
                            : 'text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-container-high)] hover:text-black'
                            }`}
                    >
                        <div className={activeSectionId === s.id ? 'text-[var(--md-sys-color-primary)]' : 'text-[var(--md-sys-color-on-surface-variant)]'}>
                            {s.icon}
                        </div>
                        {s.label}
                    </button>
                ))}
            </div>

            {/* Program tree */}
            <div className="flex-1 overflow-y-auto custom-scrollbar px-4 py-4 space-y-2">
                <div className="px-4 mb-3">
                    <span className="text-label-small font-bold text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest">Contenido</span>
                </div>
                {program.macrocycles.map((macro, macroIdx) => {
                    const macroId = `macro-${macroIdx}`;
                    const isMacroExpanded = expandedNodes.has(macroId);
                    return (
                        <div key={macro.id} className="space-y-1">
                            <button
                                onClick={() => toggleNode(macroId)}
                                className="w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-body-medium font-bold text-black hover:bg-[var(--md-sys-color-surface-container-high)] transition-all"
                            >
                                <ChevronDownIcon size={16} className={`transition-transform text-[var(--md-sys-color-on-surface-variant)] ${isMacroExpanded ? 'rotate-0' : '-rotate-90'}`} />
                                <span className="truncate">{macro.name || `Macro ${macroIdx + 1}`}</span>
                            </button>
                            {isMacroExpanded && (
                                <div className="pl-6 space-y-1 ml-4 border-l-2 border-[var(--md-sys-color-outline-variant)]">
                                    {(macro.blocks || []).map((block, blockIdx) => {
                                        const blockId = `block-${macroIdx}-${blockIdx}`;
                                        const isBlockExpanded = expandedNodes.has(blockId);
                                        return (
                                            <div key={block.id} className="space-y-1 py-1">
                                                <button
                                                    onClick={() => toggleNode(blockId)}
                                                    className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-body-small font-bold text-[var(--md-sys-color-on-surface-variant)] hover:text-black hover:bg-[var(--md-sys-color-surface-container-high)] transition-all"
                                                >
                                                    <ChevronDownIcon size={14} className={`transition-transform text-[var(--md-sys-color-outline)] ${isBlockExpanded ? 'rotate-0' : '-rotate-90'}`} />
                                                    <span className="truncate">{block.name || `Bloque ${blockIdx + 1}`}</span>
                                                </button>
                                                {isBlockExpanded && (
                                                    <div className="pl-4 space-y-1 ml-3 border-l-2 border-[var(--md-sys-color-outline-variant)]">
                                                        {block.mesocycles.map((meso, mesoIdx) => (
                                                            <div key={meso.id} className="group py-1">
                                                                <div className="text-label-medium font-bold text-black px-3 py-1.5 flex items-center gap-3">
                                                                    <div className="w-2 h-2 rounded-full bg-[var(--md-sys-color-primary)]" />
                                                                    <span className="truncate">{meso.name}</span>
                                                                    <span className="ml-auto text-label-small bg-[var(--md-sys-color-surface-container-highest)] px-2 py-0.5 rounded-full text-[var(--md-sys-color-on-surface-variant)]">
                                                                        {meso.weeks.length}w
                                                                    </span>
                                                                </div>
                                                                {onAddWeek && (
                                                                    <button
                                                                        onClick={() => onAddWeek(macroIdx, blockIdx, mesoIdx)}
                                                                        className="ml-8 mt-1 px-3 py-1 text-label-small font-bold text-[var(--md-sys-color-primary)] hover:bg-[var(--md-sys-color-primary-container)] rounded-lg transition-all flex items-center gap-2"
                                                                    >
                                                                        <PlusIcon size={12} /> Añadir Semana
                                                                    </button>
                                                                )}
                                                            </div>
                                                        ))}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                    {onAddBlock && (
                                        <button
                                            onClick={() => onAddBlock(macroIdx)}
                                            className="w-full flex items-center gap-3 px-6 py-3 text-label-medium font-bold text-[var(--md-sys-color-primary)] hover:bg-[var(--md-sys-color-primary-container)] rounded-xl transition-all"
                                        >
                                            <PlusIcon size={16} /> Nuevo Bloque
                                        </button>
                                    )}
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default EditorSidebar;
