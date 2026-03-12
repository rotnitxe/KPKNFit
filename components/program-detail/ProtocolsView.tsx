import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program, Protocol, ProtocolId } from '../../types';
import { PlusIcon, XIcon } from '../icons';

// ─── Protocolos Base ───
const PROTOCOL_LIBRARY: Protocol[] = [
    {
        id: 'gzcl-base',
        protocolId: 'gzcl',
        name: 'GZCL Method',
        emoji: '🏗️',
        description: 'Sistema de 3 tiers (T1, T2, T3) con progresión por volumen e intensidad. Ideal para intermedios.',
        author: 'Cody Lefever',
        tags: ['powerlifting', 'powerbuilding', 'intermedio'],
        sessionCategories: ['Tier 1 (Comp)', 'Tier 2 (Supl)', 'Tier 3 (Acc)'],
        blocks: [
            { name: 'Acumulación', weeks: 4, goal: 'Acumulación', intensityRange: [65, 80], volumeModifier: 1.2 },
            { name: 'Intensificación', weeks: 3, goal: 'Intensificación', intensityRange: [80, 90], volumeModifier: 0.8 },
            { name: 'Pico', weeks: 2, goal: 'Realización', intensityRange: [90, 100], volumeModifier: 0.5 },
            { name: 'Descarga', weeks: 1, goal: 'Descarga', intensityRange: [50, 65], volumeModifier: 0.4 },
        ],
        defaultSplit: 'upper-lower',
    },
    {
        id: '531-base',
        protocolId: '531',
        name: '5/3/1 Wendler',
        emoji: '5️⃣',
        description: 'Progresión mensual simple: 5+, 3+, 1+, descarga. Comprobado y sostenible a largo plazo.',
        author: 'Jim Wendler',
        tags: ['powerlifting', 'principiante', 'intermedio'],
        sessionCategories: ['Movimiento principal', 'Suplementario (BBB/FSL)', 'Asistencia'],
        blocks: [
            { name: 'Semana 5s', weeks: 1, goal: 'Acumulación', intensityRange: [65, 85] },
            { name: 'Semana 3s', weeks: 1, goal: 'Intensificación', intensityRange: [70, 90] },
            { name: 'Semana 1s', weeks: 1, goal: 'Realización', intensityRange: [75, 95] },
            { name: 'Descarga', weeks: 1, goal: 'Descarga', intensityRange: [40, 60] },
        ],
        defaultSplit: '4-day',
    },
    {
        id: 'juggernaut-base',
        protocolId: 'juggernaut',
        name: 'Juggernaut Method',
        emoji: '🦍',
        description: 'Ondulación por bloques con fases de 10s, 8s, 5s y 3s. Combina volumen con fuerza máxima.',
        author: 'Chad Wesley Smith',
        tags: ['powerlifting', 'powerbuilding', 'avanzado'],
        sessionCategories: ['Movimiento Juggernaut', 'Suplementario', 'Accesorios'],
        blocks: [
            { name: 'Fase 10s', weeks: 4, goal: 'Acumulación', intensityRange: [60, 75], volumeModifier: 1.4 },
            { name: 'Fase 8s', weeks: 4, goal: 'Acumulación', intensityRange: [65, 80], volumeModifier: 1.2 },
            { name: 'Fase 5s', weeks: 4, goal: 'Intensificación', intensityRange: [75, 87], volumeModifier: 0.9 },
            { name: 'Fase 3s', weeks: 4, goal: 'Realización', intensityRange: [85, 95], volumeModifier: 0.6 },
        ],
    },
    {
        id: 'westside-base',
        protocolId: 'westside',
        name: 'Westside Conjugate',
        emoji: '⚡',
        description: 'Sistema conjugado: día de esfuerzo máximo + día dinámico. Para atletas avanzados.',
        author: 'Louie Simmons',
        tags: ['powerlifting', 'avanzado'],
        sessionCategories: ['Max Effort', 'Dynamic Effort', 'Repetition'],
        blocks: [
            { name: 'Rotación ME/DE', weeks: 3, goal: 'Custom', intensityRange: [50, 100] },
            { name: 'Descarga', weeks: 1, goal: 'Descarga', intensityRange: [40, 60] },
        ],
        defaultSplit: '4-day',
    },
    {
        id: 'rts-base',
        protocolId: 'rts',
        name: 'RTS / Emerging Strategies',
        emoji: '📊',
        description: 'Programación autoregulada basada en RPE. Ajuste de volumen e intensidad basado en fatiga.',
        author: 'Mike Tuchscherer',
        tags: ['powerlifting', 'avanzado'],
        sessionCategories: ['Competitivo', 'Suplementario', 'Desarrollo General'],
        blocks: [
            { name: 'Desarrollo', weeks: 4, goal: 'Acumulación', intensityRange: [70, 82] },
            { name: 'Pivote', weeks: 2, goal: 'Intensificación', intensityRange: [82, 92] },
            { name: 'Pico', weeks: 2, goal: 'Realización', intensityRange: [90, 100] },
        ],
    },
];

interface ProtocolsViewProps {
    program: Program;
    onUpdateProgram: (program: Program) => void;
    addToast: (message: string, type?: 'danger' | 'success' | 'achievement' | 'suggestion', title?: string, duration?: number, why?: string) => void;
}

const ProtocolsView: React.FC<ProtocolsViewProps> = ({ program, onUpdateProgram, addToast }) => {
    const [selectedProtocol, setSelectedProtocol] = useState<Protocol | null>(null);
    const [filterTag, setFilterTag] = useState<string | null>(null);

    const allTags = useMemo(() => {
        const tags = new Set<string>();
        PROTOCOL_LIBRARY.forEach(p => p.tags.forEach(t => tags.add(t)));
        return Array.from(tags);
    }, []);

    const filteredProtocols = useMemo(() =>
        filterTag ? PROTOCOL_LIBRARY.filter(p => p.tags.includes(filterTag)) : PROTOCOL_LIBRARY,
        [filterTag]);

    const handleApplyProtocol = (protocol: Protocol) => {
        const updated = JSON.parse(JSON.stringify(program)) as Program;

        // Build structure from protocol
        const newBlocks = protocol.blocks.map((blockDef, i) => ({
            id: crypto.randomUUID(),
            name: blockDef.name,
            mesocycles: [{
                id: crypto.randomUUID(),
                name: blockDef.name,
                goal: blockDef.goal as any,
                weeks: Array.from({ length: blockDef.weeks }, (_, j) => ({
                    id: crypto.randomUUID(),
                    name: `Semana ${j + 1}`,
                    sessions: [],
                })),
            }],
        }));

        // Apply to first macrocycle
        if (updated.macrocycles.length === 0) {
            updated.macrocycles = [{ id: crypto.randomUUID(), name: 'Macrociclo Principal', blocks: newBlocks }];
        } else {
            updated.macrocycles[0].blocks = newBlocks;
        }

        updated.structure = 'complex';
        onUpdateProgram(updated);
        addToast(`Protocolo "${protocol.name}" aplicado`, 'achievement', '🎯 Protocolo Activo');
        setSelectedProtocol(null);
    };

    return (
        <div className="px-4 pb-6 space-y-4">
            {/* Header */}
            <div>
                <h3 className="text-sm font-black text-zinc-900 uppercase tracking-widest">
                    🧬 Protocolos
                </h3>
                <p className="text-[9px] text-zinc-500 uppercase tracking-wider mt-0.5">
                    Sistemas de periodización avanzada
                </p>
            </div>

            {/* Filtros */}
            <div className="flex gap-1.5 overflow-x-auto pb-1" style={{ scrollbarWidth: 'none' }}>
                <button
                    onClick={() => setFilterTag(null)}
                    className={`px-3 py-1.5 rounded-lg text-[8px] font-black uppercase tracking-wider whitespace-nowrap transition-all ${
                        !filterTag ? 'bg-zinc-900 text-white' : 'bg-zinc-100 text-zinc-500 hover:bg-zinc-200'
                    }`}
                >
                    Todos
                </button>
                {allTags.map(tag => (
                    <button
                        key={tag}
                        onClick={() => setFilterTag(tag)}
                        className={`px-3 py-1.5 rounded-lg text-[8px] font-black uppercase tracking-wider whitespace-nowrap transition-all ${
                            filterTag === tag ? 'bg-zinc-900 text-white' : 'bg-zinc-100 text-zinc-500 hover:bg-zinc-200'
                        }`}
                    >
                        {tag}
                    </button>
                ))}
            </div>

            {/* Protocol Cards */}
            <div className="space-y-3">
                {filteredProtocols.map(protocol => (
                    <motion.div
                        key={protocol.id}
                        layout
                        className="bg-white rounded-2xl border border-zinc-200 overflow-hidden hover:border-purple-300 transition-colors"
                    >
                        <button
                            onClick={() => setSelectedProtocol(protocol)}
                            className="w-full text-left p-4"
                        >
                            <div className="flex items-start gap-3">
                                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-purple-100 to-purple-200 flex items-center justify-center text-xl flex-shrink-0">
                                    {protocol.emoji}
                                </div>
                                <div className="flex-1 min-w-0">
                                    <h4 className="text-xs font-black text-zinc-900 uppercase tracking-wider">
                                        {protocol.name}
                                    </h4>
                                    {protocol.author && (
                                        <p className="text-[8px] text-purple-500 font-bold uppercase tracking-wider">
                                            por {protocol.author}
                                        </p>
                                    )}
                                    <p className="text-[9px] text-zinc-500 leading-relaxed mt-1">
                                        {protocol.description}
                                    </p>

                                    {/* Block preview */}
                                    <div className="flex gap-1 mt-2 flex-wrap">
                                        {protocol.blocks.map((b, i) => (
                                            <span
                                                key={i}
                                                className="text-[7px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-md bg-zinc-100 text-zinc-500"
                                            >
                                                {b.name} · {b.weeks}s
                                            </span>
                                        ))}
                                    </div>

                                    {/* Categories */}
                                    <div className="flex gap-1 mt-1.5 flex-wrap">
                                        {protocol.sessionCategories.map((cat, i) => (
                                            <span
                                                key={i}
                                                className="text-[7px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-md bg-purple-50 text-purple-500"
                                            >
                                                {cat}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        </button>
                    </motion.div>
                ))}
            </div>

            {/* Detail / Apply Modal */}
            <AnimatePresence>
                {selectedProtocol && (
                    <div className="fixed inset-0 z-[300] bg-black/40 backdrop-blur-sm flex items-end justify-center" onClick={() => setSelectedProtocol(null)}>
                        <motion.div
                            initial={{ y: '100%' }}
                            animate={{ y: 0 }}
                            exit={{ y: '100%' }}
                            transition={{ type: 'spring', damping: 28, stiffness: 300 }}
                            className="bg-white rounded-t-[2rem] w-full max-w-md p-6 pb-10"
                            onClick={e => e.stopPropagation()}
                        >
                            <div className="flex items-center justify-between mb-4">
                                <div className="flex items-center gap-3">
                                    <span className="text-2xl">{selectedProtocol.emoji}</span>
                                    <div>
                                        <h3 className="text-sm font-black text-zinc-900 uppercase tracking-widest">{selectedProtocol.name}</h3>
                                        {selectedProtocol.author && (
                                            <p className="text-[8px] text-purple-500 font-bold uppercase">por {selectedProtocol.author}</p>
                                        )}
                                    </div>
                                </div>
                                <button onClick={() => setSelectedProtocol(null)} className="text-zinc-400 hover:text-zinc-600">
                                    <XIcon size={16} />
                                </button>
                            </div>

                            <p className="text-[10px] text-zinc-600 leading-relaxed mb-4">{selectedProtocol.description}</p>

                            {/* Structure Preview */}
                            <div className="bg-zinc-50 rounded-xl p-3 mb-4">
                                <h4 className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">Estructura</h4>
                                <div className="space-y-1.5">
                                    {selectedProtocol.blocks.map((block, i) => (
                                        <div key={i} className="flex items-center justify-between bg-white rounded-lg px-3 py-2">
                                            <div>
                                                <span className="text-[9px] font-black text-zinc-900 uppercase">{block.name}</span>
                                                <span className="text-[8px] text-zinc-400 ml-2">{block.weeks} semanas</span>
                                            </div>
                                            {block.intensityRange && (
                                                <span className="text-[8px] text-purple-500 font-bold">
                                                    {block.intensityRange[0]}-{block.intensityRange[1]}%
                                                </span>
                                            )}
                                        </div>
                                    ))}
                                </div>
                                <div className="text-[9px] text-zinc-500 font-bold mt-2 text-center">
                                    Total: {selectedProtocol.blocks.reduce((a, b) => a + b.weeks, 0)} semanas
                                </div>
                            </div>

                            {/* Session Categories */}
                            <div className="mb-6">
                                <h4 className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">Categorías de Sesión</h4>
                                <div className="flex gap-1.5 flex-wrap">
                                    {selectedProtocol.sessionCategories.map((cat, i) => (
                                        <span key={i} className="text-[9px] font-bold px-3 py-1.5 rounded-lg bg-purple-100 text-purple-700">
                                            {cat}
                                        </span>
                                    ))}
                                </div>
                            </div>

                            {/* Warning */}
                            <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 mb-4">
                                <p className="text-[9px] text-amber-700 font-bold leading-relaxed">
                                    ⚠️ Aplicar este protocolo reemplazará la estructura actual de bloques y mesociclos. Las sesiones existentes no se modificarán pero podrían quedar desasignadas.
                                </p>
                            </div>

                            <div className="flex gap-2">
                                <button
                                    onClick={() => setSelectedProtocol(null)}
                                    className="flex-1 py-3 rounded-xl bg-zinc-100 text-zinc-600 text-[10px] font-black uppercase tracking-widest"
                                >
                                    Cancelar
                                </button>
                                <button
                                    onClick={() => handleApplyProtocol(selectedProtocol)}
                                    className="flex-1 py-3 rounded-xl bg-purple-600 text-white text-[10px] font-black uppercase tracking-widest hover:bg-purple-700 transition-colors"
                                >
                                    Aplicar Protocolo
                                </button>
                            </div>
                        </motion.div>
                    </div>
                )}
            </AnimatePresence>
        </div>
    );
};

export default ProtocolsView;
