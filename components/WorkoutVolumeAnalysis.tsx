import React, { useMemo, useState, useEffect, useRef } from 'react';
import { Program, WorkoutLog, Settings, DetailedMuscleVolumeAnalysis, Session, ProgramWeek, MuscleGroupInfo } from '../types';
import { calculateAverageVolumeForWeeks } from '../services/analysisService';
import { getVolumeThresholdsForMuscle, MuscleVolumeThresholds } from '../services/volumeCalculator';
import { BarChartIcon, ChevronRightIcon, LayersIcon, ActivityIcon, DumbbellIcon, InfoIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';
import { useAppState } from '../contexts/AppContext';
import { XIcon } from './icons';
import { MUSCLE_GROUP_DATA } from '../data/muscleGroupDatabase';
import { MaximizeIcon } from './icons';

interface WorkoutVolumeAnalysisProps {
    program?: Program;
    session?: Session;
    sessions?: Session[];
    history: WorkoutLog[];
    isOnline: boolean;
    settings: Settings;
    analysisData?: DetailedMuscleVolumeAnalysis[] | null;
    title?: string;
    onMuscleSelect?: (muscle: string | null) => void;
    selectedMuscleInfo?: { muscle: string, x: number, y: number } | null; // NUEVO
    onCloseMuscle?: () => void; // NUEVO
}

export const REGION_MAPPING: Record<string, string[]> = {
    'Pecho y Core': ['Pectoral', 'Abdomen', 'Oblicuos'],
    'Espalda': ['Dorsales', 'Trapecio', 'Espalda Baja', 'Erectores Espinales', 'Cuadrado Lumbar'],
    'Piernas': ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas', 'Isquio'],
    'Hombros': ['Deltoides', 'Deltoides Anterior', 'Deltoides Lateral', 'Deltoides Posterior'],
    'Brazos': ['Bíceps', 'Tríceps', 'Antebrazos', 'Braquial']
};

interface HierarchyItem {
    id: string;
    label: string;
    type: 'macro' | 'block' | 'meso' | 'week';
    weeks: ProgramWeek[];
    depth: number;
}

// --- CONFIGURACIÓN DE AGRUPACIÓN MUSCULAR ---
// Define qué submúsculos se fusionan en un grupo padre para la visualización
const MUSCLE_AGGREGATION_MAP: Record<string, string[]> = {
    'Cuádriceps': ['cuádriceps', 'vasto-lateral', 'vasto-medial', 'recto-femoral'],
    'Isquiosurales': ['isquiosurales', 'bíceps-femoral', 'semitendinoso', 'semimembranoso'],
    'Glúteos': ['glúteos', 'glúteo-mayor', 'glúteo-medio', 'glúteo-menor'],
    'Pectoral': ['pectoral', 'pectoral-superior', 'pectoral-medio', 'pectoral-inferior'],
    'Bíceps': ['bíceps', 'cabeza-larga-bíceps', 'cabeza-corta-bíceps', 'braquial', 'braquiorradial'],
    'Tríceps': ['tríceps', 'cabeza-larga-tríceps', 'cabeza-lateral-tríceps', 'cabeza-medial-tríceps'],
    'Dorsales': ['espalda', 'dorsales', 'dorsal-ancho', 'redondo-mayor'],
    'Trapecio': ['trapecio', 'trapecio-superior', 'trapecio-medio', 'trapecio-inferior', 'romboides'],
    'Espalda Baja': ['erectores-espinales', 'multífidos', 'cuadrado-lumbar'],
    'Abdomen': ['abdomen', 'recto-abdominal', 'oblicuos', 'transverso-abdominal', 'core'],
    'Pantorrillas': ['pantorrillas', 'gastrocnemio', 'sóleo']
};

// Excepciones que NO se agrupan (se muestran tal cual si existen)
const STANDALONE_MUSCLES = ['deltoides-anterior', 'deltoides-lateral', 'deltoides-posterior'];

// Términos amigables (sin MEV/MAV/MRV)
const STATUS_LABELS = {
    inactive: { label: 'Inactivo', desc: 'Sin actividad' },
    maintenance: { label: 'Mantenimiento', desc: 'Mínimo para mantener' },
    optimal: { label: 'Zona óptima', desc: 'Rango de crecimiento' },
    overreach: { label: 'Riesgo sobreentreno', desc: 'Riesgo de sobreentreno' },
} as const;

// Helper para obtener estado según umbrales dinámicos (términos amigables)
const getStatusFromThresholds = (sets: number, thresholds: MuscleVolumeThresholds, programMode?: string) => {
    const isPowerlifting = programMode === 'powerlifting' || programMode === 'powerbuilding' || programMode === 'strength';
    const { min, max } = thresholds;

    if (isPowerlifting) {
        if (sets === 0) return { ...STATUS_LABELS.inactive, color: 'bg-[var(--md-sys-color-surface-variant)] text-[var(--md-sys-color-on-surface-variant)]', label: 'Min', barColor: 'var(--md-sys-color-surface-variant)' };
        if (sets < 6) return { ...STATUS_LABELS.maintenance, color: 'bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)]', label: 'Bajo', barColor: 'var(--md-sys-color-secondary)' };
        if (sets <= 12) return { ...STATUS_LABELS.optimal, color: 'bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)]', label: 'Óptimo', barColor: 'var(--md-sys-color-primary)' };
        return { ...STATUS_LABELS.overreach, color: 'bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)]', label: 'Alto', barColor: 'var(--md-sys-color-error)' };
    }

    if (sets === 0) return { ...STATUS_LABELS.inactive, color: 'bg-[var(--md-sys-color-surface-variant)] text-[var(--md-sys-color-on-surface-variant)]', label: '---', barColor: 'var(--md-sys-color-surface-variant)' };
    if (sets < min) return { ...STATUS_LABELS.maintenance, color: 'bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)]', label: 'Bajo', barColor: 'var(--md-sys-color-secondary)' };
    if (sets <= max) return { ...STATUS_LABELS.optimal, color: 'bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)]', label: 'Óptimo', barColor: 'var(--md-sys-color-primary)' };
    return { ...STATUS_LABELS.overreach, color: 'bg-[var(--md-sys-color-error-container)] text-[var(--md-sys-color-on-error-container)]', label: 'Alto', barColor: 'var(--md-sys-color-error)' };
};

export const WorkoutVolumeAnalysis: React.FC<WorkoutVolumeAnalysisProps> = ({ program, session, sessions, history, isOnline, settings, analysisData, title, onMuscleSelect, selectedMuscleInfo, onCloseMuscle }) => {
    const { exerciseList, muscleHierarchy } = useAppState();
    const athleteScore = settings?.athleteScore ?? (program as any)?.athleteProfile ?? null;
    const [displayAnalysis, setDisplayAnalysis] = useState<DetailedMuscleVolumeAnalysis[] | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [expandedMuscle, setExpandedMuscle] = useState<string | null>(null);
    const calculationMode = 'complex' as const; // Siempre avanzado (plan: sin switch)
    const [isHierarchyOpen, setIsHierarchyOpen] = useState(false);
    const [selectedItemId, setSelectedItemId] = useState<string>('default');

    const [isExpandedModal, setIsExpandedModal] = useState(false);

    // NUEVO: Referencia y Estado para anclar el tooltip al scroll
    const containerRef = useRef<HTMLDivElement>(null);
    const [tooltipPos, setTooltipPos] = useState({ top: 0, left: 0 });
    // Calculamos la posición exacta dentro del contenedor padre
    useEffect(() => {
        if (selectedMuscleInfo && containerRef.current) {
            setIsExpandedModal(false);
            const rect = containerRef.current.getBoundingClientRect();
            setTooltipPos({
                top: selectedMuscleInfo.y - rect.top,
                left: selectedMuscleInfo.x - rect.left
            });
        }
    }, [selectedMuscleInfo]);

    // Si tocas otro músculo en Caupolicán, se reinicia al mini-tooltip
    useEffect(() => {
        if (selectedMuscleInfo) setIsExpandedModal(false);
    }, [selectedMuscleInfo]);

    // Jerarquía del programa
    const hierarchyItems = useMemo<HierarchyItem[]>(() => {
        if (!program) return [];
        const items: HierarchyItem[] = [];
        program.macrocycles.forEach(macro => {
            const macroWeeks = (macro.blocks || []).flatMap(b => b.mesocycles.flatMap(m => m.weeks));
            items.push({ id: `macro-${macro.id}`, label: `Macro: ${macro.name}`, type: 'macro', weeks: macroWeeks, depth: 0 });
            (macro.blocks || []).forEach(block => {
                const blockWeeks = block.mesocycles.flatMap(m => m.weeks);
                items.push({ id: `block-${block.id}`, label: block.name, type: 'block', weeks: blockWeeks, depth: 1 });
            });
        });
        return items;
    }, [program]);

    const activeItem = useMemo(() =>
        hierarchyItems.find(i => i.id === selectedItemId) || hierarchyItems[0]
        , [hierarchyItems, selectedItemId]);

    // Lógica de Agregación (Fusionar vastos en Cuádriceps, etc.)
    const aggregateData = (rawAnalysis: DetailedMuscleVolumeAnalysis[]) => {
        const aggregated: Record<string, DetailedMuscleVolumeAnalysis> = {};
        const processedMuscles = new Set<string>();

        // 1. Procesar Agrupaciones Definidas
        Object.entries(MUSCLE_AGGREGATION_MAP).forEach(([groupName, subMuscles]) => {
            // Buscar todos los datos raw que coincidan con los submúsculos
            const relevantData = rawAnalysis.filter(d => subMuscles.includes(d.muscleGroup));

            if (relevantData.length > 0) {
                // Fusionar ejercicios directos e indirectos sin duplicar sets
                // (Si hago sentadilla, cuenta para vasto y recto, pero es 1 solo set para Cuádriceps)
                const allDirectExercises = relevantData.flatMap(d => d.directExercises);
                const uniqueDirectExercises = Array.from(new Map(allDirectExercises.map(ex => [ex.name, ex])).values());

                const allIndirectExercises = relevantData.flatMap(d => d.indirectExercises);
                const uniqueIndirectExercises = Array.from(new Map(allIndirectExercises.map(ex => [ex.name, ex])).values());

                const totalSets = uniqueDirectExercises.reduce((acc, ex) => acc + ex.sets, 0);

                if (totalSets > 0 || uniqueIndirectExercises.length > 0) {
                    aggregated[groupName] = {
                        muscleGroup: groupName,
                        displayVolume: totalSets, // Recalculado basado en ejercicios únicos
                        totalSets: totalSets,
                        directExercises: uniqueDirectExercises,
                        indirectExercises: uniqueIndirectExercises,
                        frequency: Math.max(...relevantData.map(d => d.frequency)),
                        avgRestDays: null,
                        avgIFI: null,
                        recoveryStatus: 'N/A'
                    };
                }
                // Marcar como procesados
                relevantData.forEach(d => processedMuscles.add(d.muscleGroup));
            }
        });

        // 2. Procesar Excepciones (Deltoides, etc) y lo que no se agrupó
        rawAnalysis.forEach(item => {
            if (STANDALONE_MUSCLES.includes(item.muscleGroup) || !processedMuscles.has(item.muscleGroup)) {
                // Si ya se procesó en un grupo, saltar (a menos que sea explícitamente standalone)
                if (processedMuscles.has(item.muscleGroup) && !STANDALONE_MUSCLES.includes(item.muscleGroup)) return;

                // Corrección de nombres visuales
                let displayName = item.muscleGroup;
                if (displayName === 'deltoides-anterior') displayName = 'Deltoides Anterior';
                if (displayName === 'deltoides-lateral') displayName = 'Deltoides Lateral';
                if (displayName === 'deltoides-posterior') displayName = 'Deltoides Posterior';

                aggregated[displayName] = { ...item, muscleGroup: displayName };
            }
        });

        return Object.values(aggregated)
            .filter(item => item.muscleGroup !== 'General') // Solo músculos reales, no "General"
            .sort((a, b) => b.displayVolume - a.displayVolume);
    };

    const handleCalculate = () => {
        setIsLoading(true);
        setTimeout(() => { // Small timeout to allow UI update
            try {
                let result: DetailedMuscleVolumeAnalysis[] = [];
                if (session) {
                    result = calculateAverageVolumeForWeeks([{ id: 'temp', name: 'temp', sessions: [session] }], exerciseList, muscleHierarchy, calculationMode);
                } else if (sessions) {
                    result = calculateAverageVolumeForWeeks([{ id: 'temp', name: 'temp', sessions }], exerciseList, muscleHierarchy, calculationMode);
                } else if (activeItem) {
                    result = calculateAverageVolumeForWeeks(activeItem.weeks, exerciseList, muscleHierarchy, calculationMode);
                }

                // Aplicar la agregación visual
                const finalVisualData = aggregateData(result);
                setDisplayAnalysis(finalVisualData);

            } finally {
                setIsLoading(false);
            }
        }, 50);
    };

    useEffect(() => {
        if (analysisData === undefined) {
            handleCalculate();
        } else if (analysisData) {
            setDisplayAnalysis(aggregateData(analysisData));
        } else {
            setDisplayAnalysis([]);
        }
    }, [analysisData, program, session, sessions, calculationMode, selectedItemId]);


    const maxSets = Math.max(1, ...(displayAnalysis?.map(d => d.displayVolume + (d.indirectExercises?.reduce((s, e) => s + e.sets, 0) ?? 0)) ?? [0]));

    return (
        <div className="relative" ref={containerRef}>
            {/* Lista de músculos con barras apiladas (Directo verde / Indirecto azul) */}
            {displayAnalysis && displayAnalysis.length > 0 && (
                <div className="space-y-4 mb-8 p-6 bg-[var(--md-sys-color-surface-container-low)] rounded-3xl border border-[var(--md-sys-color-outline-variant)]/50">
                    <div className="flex items-center gap-4 mb-4 flex-wrap pb-4 border-b border-[var(--md-sys-color-outline-variant)]/30">
                        <div className="flex items-center gap-1.5">
                            <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: 'var(--md-sys-color-primary)' }} title="Directo" />
                            <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-60">Directo</span>
                        </div>
                        <div className="flex items-center gap-1.5">
                            <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: 'var(--md-sys-color-secondary)' }} title="Indirecto" />
                            <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-60">Indirecto</span>
                        </div>
                        <div className="flex items-center gap-1.5 ml-auto">
                            <span className="w-1.5 h-3.5 bg-amber-500/80 rounded-full" title="Umbral mínimo" />
                            <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-60">Mín.</span>
                        </div>
                        <div className="flex items-center gap-1.5">
                            <span className="w-1.5 h-3.5 bg-red-500/80 rounded-full" title="Umbral máximo" />
                            <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest opacity-60">Máx.</span>
                        </div>
                    </div>
                    {displayAnalysis.map((item) => {
                        const directSets = item.directExercises?.reduce((s, e) => s + e.sets, 0) ?? 0;
                        const indirectSets = item.indirectExercises?.reduce((s, e) => s + e.sets, 0) ?? 0;
                        const total = directSets + indirectSets;
                        const thresholds = getVolumeThresholdsForMuscle(item.muscleGroup, { program, settings, athleteScore });
                        const barMax = Math.max(maxSets, thresholds.max * 1.15, total, 1);
                        const directWidthPct = (directSets / barMax) * 100;
                        const indirectWidthPct = (indirectSets / barMax) * 100;
                        const minPct = (thresholds.min / barMax) * 100;
                        const maxPct = (thresholds.max / barMax) * 100;
                        return (
                            <div key={item.muscleGroup} className="flex items-center gap-4 group">
                                <span className="w-28 text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] truncate text-right shrink-0 opacity-70 group-hover:opacity-100 transition-opacity uppercase tracking-wider">{item.muscleGroup}</span>
                                <div className="flex-1 h-5 relative min-w-0" title={`Directo: ${directSets} | Indirecto: ${indirectSets} | Recomendado: ${thresholds.rangeLabel} (min ${thresholds.min} / max ${thresholds.max})`}>
                                    {/* Fondo con zonas de umbral: subentreno | óptimo | sobreentreno */}
                                    <div className="absolute inset-0 flex rounded-full overflow-hidden bg-[var(--md-sys-color-surface-container-high)]">
                                        <div className="h-full bg-[var(--md-sys-color-outline-variant)]/20" style={{ width: `${minPct}%` }} title="Subentreno" />
                                        <div className="h-full bg-[var(--md-sys-color-primary)]/10" style={{ width: `${maxPct - minPct}%` }} title="Zona óptima" />
                                        <div className="h-full bg-[var(--md-sys-color-error)]/10 flex-1" title="Riesgo sobreentreno" />
                                    </div>
                                    {/* Barras de volumen (directo + indirecto) encima */}
                                    <div className="absolute inset-0 flex rounded-full overflow-hidden pointer-events-none">
                                        <div
                                            className="h-full transition-all"
                                            style={{ backgroundColor: 'var(--md-sys-color-primary)', width: `${directWidthPct}%`, minWidth: directSets > 0 ? '4px' : 0 }}
                                        />
                                        <div
                                            className="h-full transition-all"
                                            style={{ backgroundColor: 'var(--md-sys-color-secondary)', width: `${indirectWidthPct}%`, minWidth: indirectSets > 0 ? '4px' : 0 }}
                                        />
                                    </div>
                                    {/* Líneas umbral: min y max */}
                                    {minPct > 2 && minPct < 98 && (
                                        <div
                                            className="absolute top-0 bottom-0 w-0.5 bg-amber-500/80 z-10"
                                            style={{ left: `${minPct}%`, transform: 'translateX(-50%)' }}
                                            title={`Mín. mantener: ${thresholds.min} sets`}
                                        />
                                    )}
                                    {maxPct > 2 && maxPct < 98 && maxPct !== minPct && (
                                        <div
                                            className="absolute top-0 bottom-0 w-0.5 bg-red-500/80 z-10"
                                            style={{ left: `${maxPct}%`, transform: 'translateX(-50%)' }}
                                            title={`Máx. recomendado: ${thresholds.max} sets`}
                                        />
                                    )}
                                </div>
                                <span className="w-28 text-right shrink-0">
                                    <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface)] tabular-nums">{directSets}|{indirectSets}</span>
                                    <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] opacity-40 ml-1">({thresholds.rangeLabel})</span>
                                </span>
                            </div>
                        );
                    })}
                </div>
            )}

            {/* TOOLTIP Y MODAL ANATÓMICO */}
            {selectedMuscleInfo && onCloseMuscle && (
                <>
                    {/* Fondo oscuro SOLO cuando está expandido */}
                    {isExpandedModal && (
                        <div className="fixed inset-0 z-[110] bg-black/70 backdrop-blur-sm animate-fade-in" onClick={onCloseMuscle} />
                    )}

                    {/* Contenedor: En modo tooltip usa 'absolute' para pegarse al scroll */}
                    <div
                        className={`z-[120] transition-all duration-300 ease-out 
                            ${isExpandedModal
                                ? 'fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[95%] sm:max-w-md'
                                : 'absolute'}`}
                        style={!isExpandedModal ? {
                            top: tooltipPos.top - 50,
                            left: tooltipPos.left > 150 ? tooltipPos.left - 155 : tooltipPos.left + 15,
                            width: '160px'
                        } : {}}
                    >
                        <div className={`bg-[var(--md-sys-color-surface-container-highest)] backdrop-blur-2xl border border-[var(--md-sys-color-outline-variant)] shadow-2xl flex flex-col relative overflow-hidden ${isExpandedModal ? 'rounded-[2rem] p-6 gap-4' : 'rounded-2xl p-4'}`}>

                            {/* Brillo de fondo estético */}
                            {isExpandedModal && <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-[var(--md-sys-color-primary)] to-[var(--md-sys-color-tertiary)] opacity-30" />}

                            {(() => {
                                const item = displayAnalysis?.find(d => d.muscleGroup.toLowerCase().includes(selectedMuscleInfo.muscle.toLowerCase())) || {
                                    muscleGroup: selectedMuscleInfo.muscle,
                                    displayVolume: 0,
                                    directExercises: []
                                };
                                const thresholds = getVolumeThresholdsForMuscle(item.muscleGroup, { program, settings, athleteScore });
                                const status = getStatusFromThresholds(item.displayVolume, thresholds, program?.mode);
                                const dbInfo = MUSCLE_GROUP_DATA.find(m => m.id.toLowerCase() === selectedMuscleInfo.muscle.toLowerCase() || m.name.toLowerCase() === selectedMuscleInfo.muscle.toLowerCase());

                                const barMax = Math.max(thresholds.max * 1.2, item.displayVolume, 1);
                                const minPct = (thresholds.min / barMax) * 100;
                                const optimalPct = ((thresholds.max - thresholds.min) / barMax) * 100;
                                const currentPct = Math.min(100, (item.displayVolume / barMax) * 100);

                                return (
                                    <>
                                        {/* CABECERA */}
                                        <div className="flex justify-between items-start">
                                            <div className="pr-4">
                                                <h4 className={`font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-tight leading-none ${isExpandedModal ? 'text-lg' : 'text-label-sm'}`}>
                                                    {item.muscleGroup}
                                                </h4>
                                                {!isExpandedModal && <p className="text-[10px] text-[var(--md-sys-color-on-surface-variant)] font-black uppercase tracking-widest mt-1.5 leading-none opacity-50">{status.label}</p>}
                                            </div>
                                            <button onClick={onCloseMuscle} className={`absolute text-[var(--md-sys-color-on-surface-variant)] hover:text-[var(--md-sys-color-on-surface)] transition-all active:scale-90 ${isExpandedModal ? 'top-5 right-5' : 'top-3 right-3'}`}>
                                                <XIcon size={isExpandedModal ? 18 : 14} />
                                            </button>
                                        </div>

                                        {/* ÁREA CENTRAL: VOLUMEN Y BOTÓN EXPANDIR (Modo Tooltip) */}
                                        {!isExpandedModal ? (
                                            <div className="flex items-end justify-between mt-3">
                                                <div className="flex items-baseline gap-1">
                                                    <p className="text-xl font-black text-[var(--md-sys-color-on-surface)] leading-none">{item.displayVolume}</p>
                                                    <p className="text-label-sm text-[var(--md-sys-color-on-surface-variant)] uppercase font-black tracking-widest opacity-40">sets</p>
                                                </div>
                                                <button
                                                    onClick={() => setIsExpandedModal(true)}
                                                    className="w-8 h-8 flex items-center justify-center rounded-full bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] hover:scale-110 active:scale-90 transition-all shadow-lg"
                                                    title="Ver anatomía y ejercicios"
                                                >
                                                    <MaximizeIcon size={12} />
                                                </button>
                                            </div>
                                        ) : (
                                            /* MODO EXPANDIDO (Modal Completo) */
                                            <>
                                                <div className="flex items-center gap-4 flex-wrap">
                                                    <span className={`text-label-sm font-black px-3 py-1 rounded-lg uppercase ${status.color}`}>
                                                        {status.label}
                                                    </span>
                                                    <div className="flex items-baseline gap-1.5">
                                                        <p className="text-3xl font-black text-[var(--md-sys-color-on-surface)] leading-none">{item.displayVolume}</p>
                                                        <p className="text-label-sm text-[var(--md-sys-color-on-surface-variant)] uppercase font-black tracking-widest opacity-40">sets semanales</p>
                                                    </div>
                                                </div>

                                                {/* Barra de zonas (min → óptimo → max) */}
                                                <div className="space-y-2 py-2">
                                                    <div className="h-3 bg-[var(--md-sys-color-surface-container-high)] rounded-full overflow-hidden flex relative">
                                                        <div className="h-full bg-[var(--md-sys-color-outline-variant)]/30" style={{ width: `${minPct}%` }} title="Mínimo para mantener" />
                                                        <div className="h-full bg-[var(--md-sys-color-primary)]/20" style={{ width: `${optimalPct}%` }} title="Zona óptima" />
                                                        <div className="h-full bg-[var(--md-sys-color-error)]/20 flex-1" title="Riesgo de sobreentreno" />
                                                        <div
                                                            className="absolute top-0 bottom-0 w-1 bg-[var(--md-sys-color-on-surface)] shadow-2xl z-20"
                                                            style={{ left: `${currentPct}%`, transform: 'translateX(-50%)' }}
                                                            title={`Actual: ${item.displayVolume} sets`}
                                                        />
                                                    </div>
                                                    <div className="flex justify-between text-label-sm text-[var(--md-sys-color-on-surface-variant)] font-black uppercase tracking-widest opacity-30">
                                                        <span className="truncate">Mantenimiento</span>
                                                        <span className="px-2 truncate">Óptimo</span>
                                                        <span className="truncate text-right">Exceso</span>
                                                    </div>
                                                </div>

                                                <div className="mt-4 space-y-5 max-h-[50vh] overflow-y-auto custom-scrollbar pr-3 animate-fade-in">
                                                    {/* Información Anatómica (Conectada a la BD) */}
                                                    {dbInfo && (
                                                        <div className="space-y-4 bg-[var(--md-sys-color-surface-container-high)] p-4 rounded-[1.5rem] border border-[var(--md-sys-color-outline-variant)]/30">
                                                            <div>
                                                                <h5 className="text-label-sm font-black text-[var(--md-sys-color-primary)] uppercase tracking-widest mb-1.5 opacity-60">Anatomía</h5>
                                                                <p className="text-body-sm text-[var(--md-sys-color-on-surface-variant)] leading-relaxed italic opacity-80">{dbInfo.description}</p>
                                                            </div>
                                                            <div>
                                                                <h5 className="text-label-sm font-black text-[var(--md-sys-color-tertiary)] uppercase tracking-widest mb-1.5 opacity-60">Biofísica & Función</h5>
                                                                <p className="text-body-sm text-[var(--md-sys-color-on-surface-variant)] leading-relaxed opacity-80">{dbInfo.importance.movement}</p>
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* Ejercicios que aportan a este músculo */}
                                                    {item.displayVolume > 0 && (
                                                        <div className="pt-2">
                                                            <h5 className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest mb-3 opacity-60">Distribución de Carga</h5>
                                                            <div className="space-y-2.5">
                                                                {item.directExercises?.map((ex: any, idx: number) => (
                                                                    <div key={idx} className="flex justify-between items-center bg-[var(--md-sys-color-surface-container-highest)] px-4 py-3 rounded-2xl border border-[var(--md-sys-color-outline-variant)]/20 shadow-sm transition-all hover:border-[var(--md-sys-color-primary)]/30">
                                                                        <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface)] truncate pr-4">{ex.name}</span>
                                                                        <span className="text-label-sm font-black text-[var(--md-sys-color-on-primary-container)] bg-[var(--md-sys-color-primary-container)] px-3 py-1 rounded-full whitespace-nowrap">{ex.sets} sets</span>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        </div>
                                                    )}
                                                </div>
                                            </>
                                        )}
                                    </>
                                );
                            })()}
                        </div>
                    </div>
                </>
            )}
        </div>
    );
};