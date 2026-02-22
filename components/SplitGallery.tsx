import React, { useState, useMemo } from 'react';
import { SPLIT_TEMPLATES, SplitTemplate, SplitTag } from '../data/splitTemplates';
import { SearchIcon } from './icons';

const FILTER_TAGS: (SplitTag | 'Todos')[] = [
    'Todos', 'Recomendado por KPKN', 'Alta Frecuencia', 'Baja Frecuencia',
    'Balanceado', 'Alto Volumen', 'Powerlifting', 'Personalizado',
];

interface SplitGalleryProps {
    onSelect: (split: SplitTemplate) => void;
    currentSplitId?: string;
    excludeCustom?: boolean;
    compact?: boolean;
}

const SplitGallery: React.FC<SplitGalleryProps> = ({
    onSelect, currentSplitId, excludeCustom = true, compact = false,
}) => {
    const [filter, setFilter] = useState<SplitTag | 'Todos'>('Todos');
    const [search, setSearch] = useState('');

    const filteredSplits = useMemo(() => {
        return SPLIT_TEMPLATES.filter(s => {
            if (excludeCustom && s.id === 'custom') return false;
            const matchesTag = filter === 'Todos' || s.tags.includes(filter);
            const q = search.toLowerCase();
            const matchesSearch = !q || s.name.toLowerCase().includes(q) || s.description.toLowerCase().includes(q);
            return matchesTag && matchesSearch;
        });
    }, [filter, search, excludeCustom]);

    return (
        <div className="flex flex-col flex-1 overflow-hidden">
            <div className={`${compact ? 'px-3 py-2' : 'px-5 py-3'} border-b border-white/5`}>
                <div className="relative">
                    <SearchIcon size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
                    <input
                        type="text"
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Buscar split..."
                        className="w-full bg-zinc-900 border border-white/10 rounded-xl pl-9 pr-3 py-2.5 text-xs text-white placeholder-zinc-600 focus:ring-1 focus:ring-white/30 focus:border-white/30"
                    />
                </div>
            </div>

            <div className={`${compact ? 'px-3 py-1.5' : 'px-5 py-2'} flex gap-2 overflow-x-auto hide-scrollbar border-b border-white/5`}>
                {FILTER_TAGS.map(tag => (
                    <button
                        key={tag}
                        onClick={() => setFilter(tag)}
                        className={`px-3 py-1.5 rounded-full text-[9px] font-black uppercase tracking-wider whitespace-nowrap transition-all border ${
                            filter === tag
                                ? 'bg-white text-black border-white'
                                : 'bg-transparent text-zinc-500 border-zinc-800 hover:border-zinc-500'
                        }`}
                    >
                        {tag}
                    </button>
                ))}
            </div>

            <div className="flex-1 overflow-y-auto custom-scrollbar p-3 space-y-2">
                {filteredSplits.map(split => {
                    const isCurrent = split.id === currentSplitId;
                    const trainingDays = split.pattern.filter(d => d.toLowerCase() !== 'descanso').length;
                    return (
                        <button
                            key={split.id}
                            onClick={() => onSelect(split)}
                            className={`w-full text-left p-4 rounded-2xl border transition-all group ${
                                isCurrent
                                    ? 'bg-white/5 border-white/20'
                                    : 'bg-zinc-900/50 border-white/5 hover:border-white/20 hover:bg-zinc-900'
                            }`}
                        >
                            <div className="flex items-start justify-between mb-2">
                                <div className="flex-1">
                                    <div className="flex items-center gap-2">
                                        <span className="text-sm font-black text-white">{split.name}</span>
                                        {isCurrent && (
                                            <span className="text-[8px] font-black uppercase px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-400 border border-blue-500/30">
                                                ACTUAL
                                            </span>
                                        )}
                                    </div>
                                    <p className="text-[10px] text-zinc-500 mt-0.5">{split.description}</p>
                                </div>
                                <span className="text-[10px] font-black text-zinc-500">{trainingDays}d</span>
                            </div>
                            <div className="flex gap-1 mt-2">
                                {split.pattern.map((day, i) => (
                                    <div
                                        key={i}
                                        className={`flex-1 h-1.5 rounded-full ${
                                            day.toLowerCase() === 'descanso' ? 'bg-zinc-800' : 'bg-white/40'
                                        }`}
                                    />
                                ))}
                            </div>
                        </button>
                    );
                })}
                {filteredSplits.length === 0 && (
                    <div className="py-12 text-center text-zinc-600 text-xs font-bold">
                        Sin resultados
                    </div>
                )}
            </div>
        </div>
    );
};

export default SplitGallery;
