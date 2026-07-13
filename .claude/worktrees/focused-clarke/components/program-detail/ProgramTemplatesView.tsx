import React, { useState, useMemo, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Program, LoopTemplate, Loop, ProgramWeek, Session } from '../../types';
import { XIcon, ChevronDownIcon } from '../icons';

// ─── Built-in Program Templates ───

const PROGRAM_TEMPLATES: LoopTemplate[] = [
    {
        id: 'ppl-4w',
        name: 'Push Pull Legs · 4 semanas',
        emoji: '💪',
        description: 'Split PPL clásico con 4 semanas por ciclo. Descarga automática cada 4 ciclos. Ideal para intermedios.',
        tags: ['PPL', 'Intermedio', '4 semanas', 'Hipertrofia'],
        weeks: 4,
        splitId: 'ppl',
        loops: [
            { id: 'deload', title: 'Descarga', type: 'deload', repeatEveryXLoops: 4, durationType: 'week' },
        ],
        weekConfigs: [
            { weekIndex: 0, focus: 'Acumulación', intensityModifier: 0.7 },
            { weekIndex: 1, focus: 'Acumulación', intensityModifier: 0.75 },
            { weekIndex: 2, focus: 'Intensificación', intensityModifier: 0.85 },
            { weekIndex: 3, focus: 'Realización', intensityModifier: 0.9 },
        ],
    },
    {
        id: 'ul-3w',
        name: 'Upper Lower · 3 semanas',
        emoji: '🔄',
        description: 'Split Upper/Lower con 3 semanas rotativas. Ciclo corto para volumen alto. Test 1RM cada 6 ciclos.',
        tags: ['Upper Lower', 'Intermedio', '3 semanas', 'Fuerza'],
        weeks: 3,
        splitId: 'upper_lower',
        loops: [
            { id: '1rm', title: 'Test 1RM', type: '1rm_test', repeatEveryXLoops: 6, durationType: 'week' },
        ],
        weekConfigs: [
            { weekIndex: 0, focus: 'Volumen', intensityModifier: 0.7 },
            { weekIndex: 1, focus: 'Mixto', intensityModifier: 0.8 },
            { weekIndex: 2, focus: 'Intensidad', intensityModifier: 0.85 },
        ],
    },
    {
        id: 'fb-2w',
        name: 'Full Body · 2 semanas',
        emoji: '🏋️',
        description: 'Full Body con 2 semanas alternantes (A/B). Perfecto para principiantes. Descarga cada 3 ciclos.',
        tags: ['Full Body', 'Principiante', '2 semanas', 'General'],
        weeks: 2,
        splitId: 'full_body',
        loops: [
            { id: 'deload', title: 'Descarga', type: 'deload', repeatEveryXLoops: 3, durationType: 'week' },
        ],
        weekConfigs: [
            { weekIndex: 0, focus: 'Semana A', intensityModifier: 0.75 },
            { weekIndex: 1, focus: 'Semana B', intensityModifier: 0.8 },
        ],
    },
    {
        id: 'powerlifting-5w',
        name: 'Powerlifting · 5 semanas',
        emoji: '🏆',
        description: 'Ciclo de 5 semanas enfocado en SBD. Progresión lineal con pico en semana 5. Test 1RM cada 4 ciclos.',
        tags: ['Powerlifting', 'Avanzado', '5 semanas', 'Fuerza'],
        weeks: 5,
        loops: [
            { id: '1rm', title: 'Test 1RM', type: '1rm_test', repeatEveryXLoops: 4, durationType: 'week' },
            { id: 'deload', title: 'Descarga', type: 'deload', repeatEveryXLoops: 3, durationType: 'week' },
        ],
        weekConfigs: [
            { weekIndex: 0, focus: 'Volumen base', intensityModifier: 0.65 },
            { weekIndex: 1, focus: 'Volumen alto', intensityModifier: 0.7 },
            { weekIndex: 2, focus: 'Transición', intensityModifier: 0.8 },
            { weekIndex: 3, focus: 'Intensificación', intensityModifier: 0.85 },
            { weekIndex: 4, focus: 'Pico / Realización', intensityModifier: 0.95 },
        ],
    },
    {
        id: 'hypertrophy-6w',
        name: 'Hipertrofia · 6 semanas',
        emoji: '💥',
        description: 'Ciclo largo de 6 semanas para máximo crecimiento muscular. Progresión ondulada con descarga integrada.',
        tags: ['PPL', 'Intermedio', '6 semanas', 'Hipertrofia'],
        weeks: 6,
        splitId: 'ppl',
        loops: [
            { id: 'deload', title: 'Descarga', type: 'deload', repeatEveryXLoops: 2, durationType: 'week' },
        ],
        weekConfigs: [
            { weekIndex: 0, focus: 'Volumen moderado', intensityModifier: 0.65 },
            { weekIndex: 1, focus: 'Volumen alto', intensityModifier: 0.7 },
            { weekIndex: 2, focus: 'Volumen máximo', intensityModifier: 0.75 },
            { weekIndex: 3, focus: 'Intensificación', intensityModifier: 0.8 },
            { weekIndex: 4, focus: 'Intensidad alta', intensityModifier: 0.85 },
            { weekIndex: 5, focus: 'Realización', intensityModifier: 0.9 },
        ],
    },
    {
        id: 'minimalist-1w',
        name: 'Minimalista · 1 semana',
        emoji: '⚡',
        description: 'Una sola semana que se repite. Sin variación intra-ciclo. Descarga cada 4 ciclos. Simple y efectivo.',
        tags: ['Full Body', 'Principiante', '1 semana', 'General'],
        weeks: 1,
        splitId: 'full_body',
        loops: [
            { id: 'deload', title: 'Descarga', type: 'deload', repeatEveryXLoops: 4, durationType: 'week' },
        ],
        weekConfigs: [
            { weekIndex: 0, focus: 'Sesión única', intensityModifier: 0.75 },
        ],
    },
];

const ALL_TAGS = [...new Set(PROGRAM_TEMPLATES.flatMap(t => t.tags))];

interface ProgramTemplatesViewProps {
    program: Program;
    onUpdateProgram: (program: Program) => void;
    addToast: (message: string, type?: 'danger' | 'success' | 'achievement' | 'suggestion', title?: string, duration?: number, why?: string) => void;
}

const ProgramTemplatesView: React.FC<ProgramTemplatesViewProps> = ({ program, onUpdateProgram, addToast }) => {
    const [selectedTag, setSelectedTag] = useState<string | null>(null);
    const [previewTemplate, setPreviewTemplate] = useState<LoopTemplate | null>(null);
    const [showConfirm, setShowConfirm] = useState(false);

    const filteredTemplates = useMemo(() => {
        if (!selectedTag) return PROGRAM_TEMPLATES;
        return PROGRAM_TEMPLATES.filter(t => t.tags.includes(selectedTag));
    }, [selectedTag]);

    const handleApplyTemplate = useCallback((template: LoopTemplate) => {
        const updated = JSON.parse(JSON.stringify(program)) as Program;

        // Build weeks from template
        const weeks: ProgramWeek[] = (template.weekConfigs || []).map((wc, i) => ({
            id: crypto.randomUUID(),
            name: wc.focus || `Semana ${i + 1}`,
            sessions: [] as Session[],
            isLoopWeek: false,
        }));

        // If no weekConfigs, generate blank weeks
        if (weeks.length === 0) {
            for (let i = 0; i < template.weeks; i++) {
                weeks.push({
                    id: crypto.randomUUID(),
                    name: `Semana ${i + 1}`,
                    sessions: [] as Session[],
                });
            }
        }

        // Ensure structure exists
        if (!updated.macrocycles.length) {
            updated.macrocycles = [{ id: crypto.randomUUID(), name: 'Macrociclo 1', blocks: [] }];
        }
        const macro = updated.macrocycles[0];
        if (!macro.blocks || !macro.blocks.length) {
            macro.blocks = [{
                id: crypto.randomUUID(),
                name: 'Bloque 1',
                mesocycles: [{ id: crypto.randomUUID(), name: 'Mesociclo 1', goal: 'Acumulación' as const, weeks: [] }],
            }];
        }

        // Replace weeks in first block's first mesocycle
        macro.blocks![0].mesocycles[0].weeks = weeks;

        // Apply loops
        updated.loops = template.loops.map(l => ({ ...l, id: crypto.randomUUID() }));

        // Set as simple program
        updated.structure = 'simple';

        onUpdateProgram(updated);
        addToast(`Template "${template.name}" aplicado`, 'achievement', '🎯 Template aplicado');
        setPreviewTemplate(null);
        setShowConfirm(false);
    }, [program, onUpdateProgram, addToast]);

    return (
        <div className="space-y-4">
            {/* ── Tag Filter ── */}
            <div className="flex gap-1.5 overflow-x-auto pb-1 custom-scrollbar">
                <button
                    onClick={() => setSelectedTag(null)}
                    className={`flex-shrink-0 px-3 py-1.5 rounded-full text-[9px] font-bold uppercase tracking-wider transition-colors ${
                        !selectedTag
                            ? 'bg-zinc-900 text-white'
                            : 'bg-zinc-100 text-zinc-500 hover:bg-zinc-200'
                    }`}
                >
                    Todos
                </button>
                {ALL_TAGS.map(tag => (
                    <button
                        key={tag}
                        onClick={() => setSelectedTag(tag === selectedTag ? null : tag)}
                        className={`flex-shrink-0 px-3 py-1.5 rounded-full text-[9px] font-bold uppercase tracking-wider transition-colors ${
                            selectedTag === tag
                                ? 'bg-zinc-900 text-white'
                                : 'bg-zinc-100 text-zinc-500 hover:bg-zinc-200'
                        }`}
                    >
                        {tag}
                    </button>
                ))}
            </div>

            {/* ── Templates Grid ── */}
            <div className="grid gap-3">
                {filteredTemplates.map((template) => (
                    <motion.button
                        key={template.id}
                        onClick={() => setPreviewTemplate(template)}
                        className="w-full bg-white rounded-2xl border border-zinc-200 p-4 text-left hover:border-zinc-300 hover:shadow-sm transition-all active:scale-[0.98]"
                        whileTap={{ scale: 0.98 }}
                    >
                        <div className="flex items-start gap-3">
                            <span className="text-2xl">{template.emoji}</span>
                            <div className="flex-1 min-w-0">
                                <h4 className="text-[11px] font-black text-zinc-900 uppercase tracking-wider">
                                    {template.name}
                                </h4>
                                <p className="text-[9px] text-zinc-500 mt-1 leading-relaxed line-clamp-2">
                                    {template.description}
                                </p>

                                {/* Stats Row */}
                                <div className="flex items-center gap-3 mt-2">
                                    <span className="text-[8px] font-bold text-zinc-400 uppercase">
                                        {template.weeks} sem
                                    </span>
                                    <span className="text-[8px] font-bold text-purple-400 uppercase">
                                        {template.loops.length} loop{template.loops.length !== 1 ? 's' : ''}
                                    </span>
                                    {template.splitId && (
                                        <span className="text-[8px] font-bold text-blue-400 uppercase">
                                            {template.splitId.replace('_', '/')}
                                        </span>
                                    )}
                                </div>

                                {/* Tags */}
                                <div className="flex flex-wrap gap-1 mt-2">
                                    {template.tags.map(tag => (
                                        <span key={tag} className="px-1.5 py-0.5 rounded-md bg-zinc-100 text-[7px] font-bold text-zinc-400 uppercase tracking-wider">
                                            {tag}
                                        </span>
                                    ))}
                                </div>
                            </div>
                        </div>
                    </motion.button>
                ))}
            </div>

            {/* ── Template Preview Modal ── */}
            <AnimatePresence>
                {previewTemplate && (
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        className="fixed inset-0 bg-black/50 z-50 flex items-end justify-center"
                        onClick={() => setPreviewTemplate(null)}
                    >
                        <motion.div
                            initial={{ y: '100%' }}
                            animate={{ y: 0 }}
                            exit={{ y: '100%' }}
                            transition={{ type: 'spring', damping: 28, stiffness: 300 }}
                            className="w-full max-w-lg bg-white rounded-t-3xl p-6 max-h-[85vh] overflow-y-auto"
                            onClick={e => e.stopPropagation()}
                        >
                            {/* Header */}
                            <div className="flex items-center justify-between mb-6">
                                <div className="flex items-center gap-3">
                                    <span className="text-3xl">{previewTemplate.emoji}</span>
                                    <div>
                                        <h3 className="text-sm font-black text-zinc-900 uppercase tracking-wider">
                                            {previewTemplate.name}
                                        </h3>
                                        <p className="text-[9px] text-zinc-500 mt-0.5">
                                            {previewTemplate.description}
                                        </p>
                                    </div>
                                </div>
                                <button
                                    onClick={() => setPreviewTemplate(null)}
                                    className="w-8 h-8 rounded-full bg-zinc-100 flex items-center justify-center"
                                >
                                    <XIcon size={14} />
                                </button>
                            </div>

                            {/* Template Diff Preview */}
                            <div className="space-y-4">
                                {/* Weeks Preview */}
                                <div>
                                    <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest block mb-2">
                                        Estructura · {previewTemplate.weeks} semanas por ciclo
                                    </span>
                                    <div className="flex gap-2 overflow-x-auto pb-1">
                                        {(previewTemplate.weekConfigs || []).map((wc, i) => (
                                            <div key={i} className="flex-shrink-0 bg-zinc-50 border border-zinc-200 rounded-xl p-3 min-w-[100px]">
                                                <span className="text-[10px] font-black text-zinc-900 block">S{i + 1}</span>
                                                <span className="text-[8px] text-zinc-500 font-bold block mt-0.5">{wc.focus}</span>
                                                <div className="mt-2 h-1.5 bg-zinc-200 rounded-full overflow-hidden">
                                                    <div
                                                        className="h-full bg-gradient-to-r from-purple-400 to-purple-600 rounded-full"
                                                        style={{ width: `${(wc.intensityModifier || 0.5) * 100}%` }}
                                                    />
                                                </div>
                                                <span className="text-[7px] text-zinc-400 font-bold mt-0.5 block">
                                                    {Math.round((wc.intensityModifier || 0.5) * 100)}% intensidad
                                                </span>
                                            </div>
                                        ))}
                                    </div>
                                </div>

                                {/* Loops Preview */}
                                <div>
                                    <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest block mb-2">
                                        Loops · {previewTemplate.loops.length} configurados
                                    </span>
                                    <div className="space-y-2">
                                        {previewTemplate.loops.map((loop, i) => (
                                            <div key={i} className="flex items-center gap-3 bg-purple-50 border border-purple-200 rounded-xl px-3 py-2">
                                                <span className="text-lg">🔄</span>
                                                <div>
                                                    <span className="text-[10px] font-black text-purple-900">{loop.title}</span>
                                                    <span className="text-[8px] text-purple-600 font-bold block">
                                                        Cada {loop.repeatEveryXLoops} ciclo{loop.repeatEveryXLoops > 1 ? 's' : ''} · {loop.durationType === 'week' ? 'semana completa' : 'un día'}
                                                    </span>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>

                                {/* Change Diff */}
                                {(program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length ?? 0) > 0 && (
                                    <div className="bg-amber-50 border border-amber-200 rounded-xl p-3">
                                        <span className="text-[9px] font-black text-amber-800 uppercase tracking-wider block">
                                            ⚠️ Cambios al aplicar
                                        </span>
                                        <div className="mt-2 space-y-1">
                                            <DiffLine
                                                label="Semanas"
                                                from={program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length || 0}
                                                to={previewTemplate.weeks}
                                            />
                                            <DiffLine
                                                label="Loops"
                                                from={(program.loops || []).length}
                                                to={previewTemplate.loops.length}
                                            />
                                        </div>
                                        <p className="text-[8px] text-amber-700 mt-2">
                                            Las semanas y sesiones actuales serán reemplazadas por la estructura del template.
                                        </p>
                                    </div>
                                )}
                            </div>

                            {/* Apply Button */}
                            <button
                                onClick={() => {
                                    const hasContent = (program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length || 0) > 0;
                                    if (hasContent) {
                                        setShowConfirm(true);
                                    } else {
                                        handleApplyTemplate(previewTemplate);
                                    }
                                }}
                                className="w-full mt-6 py-4 rounded-2xl bg-gradient-to-r from-purple-600 to-purple-700 text-white text-[11px] font-black uppercase tracking-widest hover:from-purple-700 hover:to-purple-800 transition-all shadow-lg shadow-purple-500/20"
                            >
                                Aplicar Template
                            </button>
                        </motion.div>

                        {/* Confirmation Overlay */}
                        <AnimatePresence>
                            {showConfirm && (
                                <motion.div
                                    initial={{ opacity: 0 }}
                                    animate={{ opacity: 1 }}
                                    exit={{ opacity: 0 }}
                                    className="absolute inset-0 bg-black/60 z-[51] flex items-center justify-center p-6"
                                    onClick={() => setShowConfirm(false)}
                                >
                                    <motion.div
                                        initial={{ scale: 0.9, opacity: 0 }}
                                        animate={{ scale: 1, opacity: 1 }}
                                        exit={{ scale: 0.9, opacity: 0 }}
                                        className="bg-white rounded-3xl p-6 max-w-sm w-full"
                                        onClick={e => e.stopPropagation()}
                                    >
                                        <h4 className="text-sm font-black text-zinc-900 text-center uppercase tracking-wider">
                                            ¿Reemplazar estructura?
                                        </h4>
                                        <p className="text-[10px] text-zinc-500 text-center mt-2 leading-relaxed">
                                            Las semanas y sesiones actuales serán reemplazadas. Los loops actuales también se sobreescribirán.
                                        </p>
                                        <div className="flex gap-3 mt-6">
                                            <button
                                                onClick={() => setShowConfirm(false)}
                                                className="flex-1 py-3 rounded-xl bg-zinc-100 text-zinc-600 text-[10px] font-bold uppercase tracking-wider"
                                            >
                                                Cancelar
                                            </button>
                                            <button
                                                onClick={() => handleApplyTemplate(previewTemplate!)}
                                                className="flex-1 py-3 rounded-xl bg-red-500 text-white text-[10px] font-bold uppercase tracking-wider"
                                            >
                                                Sí, reemplazar
                                            </button>
                                        </div>
                                    </motion.div>
                                </motion.div>
                            )}
                        </AnimatePresence>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
};

// Helper component for diff preview
const DiffLine: React.FC<{ label: string; from: number; to: number }> = ({ label, from, to }) => {
    const changed = from !== to;
    return (
        <div className="flex items-center gap-2">
            <span className="text-[8px] font-bold text-amber-700 w-16">{label}</span>
            <span className={`text-[8px] font-bold ${changed ? 'line-through text-amber-400' : 'text-amber-600'}`}>{from}</span>
            {changed && (
                <>
                    <span className="text-[8px] text-amber-500">→</span>
                    <span className="text-[8px] font-black text-amber-800">{to}</span>
                </>
            )}
        </div>
    );
};

export default ProgramTemplatesView;
