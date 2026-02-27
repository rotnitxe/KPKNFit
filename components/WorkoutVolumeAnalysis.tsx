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
    selectedMuscleInfo?: {muscle: string, x: number, y: number} | null; // NUEVO
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
    'Dorsales': ['espalda', 'dorsal-ancho', 'redondo-mayor'], // "Espalda" general se asume como dorsal mayormente
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
    const isPowerlifting = programMode === 'powerlifting' || programMode === 'strength';
    const { min, optimal, max } = thresholds;

    if (isPowerlifting) {
        if (sets === 0) return { ...STATUS_LABELS.inactive, color: 'bg-zinc-700 text-zinc-400', label: 'Min' };
        if (sets < 6) return { ...STATUS_LABELS.maintenance, color: 'bg-blue-900/50 text-blue-200', label: 'Bajo' };
        if (sets <= 12) return { ...STATUS_LABELS.optimal, color: 'bg-emerald-600 text-white', label: 'Óptimo' };
        return { ...STATUS_LABELS.overreach, color: 'bg-cyber-warning text-white', label: 'Alto' };
    }

    if (sets === 0) return { ...STATUS_LABELS.inactive, color: 'bg-zinc-800 text-zinc-500', label: '---' };
    if (sets < min) return { ...STATUS_LABELS.maintenance, color: 'bg-blue-900/50 text-blue-200', label: 'Bajo' };
    if (sets <= max) return { ...STATUS_LABELS.optimal, color: 'bg-emerald-600 text-white', label: 'Óptimo' };
    return { ...STATUS_LABELS.overreach, color: 'bg-red-600 text-white', label: 'Alto' };
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
                <div className="space-y-2 mb-4">
                    <div className="flex items-center gap-2 mb-2">
                        <span className="w-2 h-2 rounded-full bg-emerald-500" title="Directo" />
                        <span className="text-[9px] font-bold text-[#8E8E93] uppercase">Directo</span>
                        <span className="w-2 h-2 rounded-full bg-blue-500 ml-2" title="Indirecto" />
                        <span className="text-[9px] font-bold text-[#8E8E93] uppercase">Indirecto</span>
                    </div>
                    {displayAnalysis.map((item) => {
                        const directSets = item.directExercises?.reduce((s, e) => s + e.sets, 0) ?? 0;
                        const indirectSets = item.indirectExercises?.reduce((s, e) => s + e.sets, 0) ?? 0;
                        const total = directSets + indirectSets;
                        const directWidthPct = maxSets > 0 ? (directSets / maxSets) * 100 : 0;
                        const indirectWidthPct = maxSets > 0 ? (indirectSets / maxSets) * 100 : 0;
                        const thresholds = getVolumeThresholdsForMuscle(item.muscleGroup, { program, settings, athleteScore });
                        return (
                            <div key={item.muscleGroup} className="flex items-center gap-2 group">
                                <span className="w-24 text-[10px] font-bold text-[#8E8E93] truncate text-right shrink-0">{item.muscleGroup}</span>
                                <div className="flex-1 h-3 bg-[#1a1a1a] rounded-full overflow-hidden flex min-w-0" title={`Directo: ${directSets} | Indirecto: ${indirectSets} | Recomendado: ${thresholds.rangeLabel}`}>
                                    <div
                                        className="h-full bg-emerald-500 transition-all"
                                        style={{ width: `${directWidthPct}%`, minWidth: directSets > 0 ? '2px' : 0 }}
                                    />
                                    <div
                                        className="h-full bg-blue-500 transition-all"
                                        style={{ width: `${indirectWidthPct}%`, minWidth: indirectSets > 0 ? '2px' : 0 }}
                                    />
                                </div>
                                <span className="w-24 text-[10px] text-right shrink-0">
                                    <span className="font-bold text-white tabular-nums">{directSets}|{indirectSets}</span>
                                    <span className="text-[8px] text-zinc-500 font-mono ml-1">({thresholds.rangeLabel})</span>
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
                                ? 'fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[90%] sm:max-w-md' 
                                : 'absolute'}`}
                        style={!isExpandedModal ? {
                            // Sin bloqueos matemáticos: permitimos números negativos para que suba hasta Caupolicán
                            top: tooltipPos.top - 50,
                            // Si tocas muy a la derecha, el globo se abre hacia la izquierda para no salirse de la pantalla
                            left: tooltipPos.left > 150 ? tooltipPos.left - 155 : tooltipPos.left + 15,
                            width: '140px'
                        } : {}}
                    >
                        <div className={`bg-zinc-950/95 backdrop-blur-md border border-white/10 shadow-2xl flex flex-col relative overflow-hidden ${isExpandedModal ? 'rounded-[1.5rem] p-5 gap-3' : 'rounded-2xl p-3'}`}>
                            
                            {/* Brillo de fondo estético (Solo en modo expandido para mayor impacto) */}
                            {isExpandedModal && <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-blue-500 to-purple-500 opacity-50" />}

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
                                                <h4 className={`font-black text-white uppercase tracking-tight leading-none ${isExpandedModal ? 'text-base' : 'text-[11px]'}`}>
                                                    {item.muscleGroup}
                                                </h4>
                                                {!isExpandedModal && <p className="text-[8px] text-zinc-500 font-bold uppercase tracking-widest mt-1 leading-none">{status.desc}</p>}
                                            </div>
                                            <button onClick={onCloseMuscle} className={`absolute text-zinc-500 hover:text-white transition-colors ${isExpandedModal ? 'top-4 right-4' : 'top-2 right-2'}`}>
                                                <XIcon size={isExpandedModal ? 16 : 12} />
                                            </button>
                                        </div>

                                        {/* ÁREA CENTRAL: VOLUMEN Y BOTÓN EXPANDIR (Modo Tooltip) */}
                                        {!isExpandedModal ? (
                                            <div className="flex items-end justify-between mt-2">
                                                <div className="flex items-baseline gap-1">
                                                    <p className="text-lg font-black text-white leading-none">{item.displayVolume}</p>
                                                    <p className="text-[8px] text-zinc-500 uppercase font-bold tracking-widest">sets</p>
                                                    <span className="text-[8px] text-zinc-600 font-mono ml-1">({thresholds.rangeLabel})</span>
                                                </div>
                                                <button 
                                                    onClick={() => setIsExpandedModal(true)}
                                                    className="w-6 h-6 flex items-center justify-center rounded-full bg-cyan-500/20 border border-cyan-500/50 text-cyan-400 hover:bg-cyan-500/30 transition-colors shadow-sm"
                                                    title="Ver anatomía y ejercicios"
                                                >
                                                    <MaximizeIcon size={10} />
                                                </button>
                                            </div>
                                        ) : (
                                            /* MODO EXPANDIDO (Modal Completo) */
                                            <>
                                                <div className="flex items-center gap-3 flex-wrap">
                                                    <span className={`text-[9px] font-black px-2 py-1 rounded-md uppercase ${status.color}`}>
                                                        {status.label}
                                                    </span>
                                                    <div className="flex items-baseline gap-1">
                                                        <p className="text-2xl font-black text-white leading-none">{item.displayVolume}</p>
                                                        <p className="text-[9px] text-zinc-500 uppercase font-bold tracking-widest">sets</p>
                                                        <span className="text-[9px] text-zinc-500 font-mono ml-1">objetivo {thresholds.rangeLabel}</span>
                                                    </div>
                                                </div>

                                                {/* Barra de zonas (min → óptimo → max) */}
                                                <div className="space-y-1">
                                                    <div className="h-2 bg-[#1a1a1a] rounded-full overflow-hidden flex relative">
                                                        <div className="h-full bg-zinc-600" style={{ width: `${minPct}%` }} title="Mínimo para mantener" />
                                                        <div className="h-full bg-emerald-600/60" style={{ width: `${optimalPct}%` }} title="Zona óptima" />
                                                        <div className="h-full bg-red-600/40 flex-1" title="Riesgo de sobreentreno" />
                                                        <div
                                                            className="absolute top-0 bottom-0 w-0.5 bg-white shadow-lg"
                                                            style={{ left: `${currentPct}%`, transform: 'translateX(-50%)' }}
                                                            title={`Actual: ${item.displayVolume} sets`}
                                                        />
                                                    </div>
                                                    <div className="flex justify-between text-[8px] text-zinc-500 font-mono">
                                                        <span>Mín. mantener</span>
                                                        <span>Zona óptima</span>
                                                        <span>Riesgo sobreentreno</span>
                                                    </div>
                                                </div>

                                                <div className="mt-3 space-y-4 max-h-[50vh] overflow-y-auto custom-scrollbar pr-2 animate-fade-in">
                                                    {/* Información Anatómica (Conectada a la BD) */}
                                                    {dbInfo && (
                                                        <div className="space-y-3 bg-zinc-900/40 p-3 rounded-2xl border border-white/5">
                                                            <div>
                                                                <h5 className="text-[9px] font-black text-blue-400 uppercase tracking-widest mb-1">Anatomía</h5>
                                                                <p className="text-[11px] text-zinc-300 leading-relaxed">{dbInfo.description}</p>
                                                            </div>
                                                            <div>
                                                                <h5 className="text-[9px] font-black text-emerald-400 uppercase tracking-widest mb-1">Función Biomecánica</h5>
                                                                <p className="text-[11px] text-zinc-300 leading-relaxed">{dbInfo.importance.movement}</p>
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* Ejercicios que aportan a este músculo */}
                                                    {item.displayVolume > 0 && (
                                                        <div>
                                                            <h5 className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">Ejercicios Activos</h5>
                                                            <div className="space-y-2">
                                                                {item.directExercises?.map((ex: any, idx: number) => (
                                                                    <div key={idx} className="flex justify-between items-center bg-black/40 p-2.5 rounded-xl border border-white/5">
                                                                        <span className="text-[11px] text-zinc-300 font-medium truncate pr-2">{ex.name}</span>
                                                                        <span className="text-[10px] font-bold text-white whitespace-nowrap bg-zinc-800 px-2 py-1 rounded-md">{ex.sets} sets</span>
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