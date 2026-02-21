import React, { useState, useMemo, useEffect } from 'react';
import { Program, Session } from '../types';
import { 
    CalendarIcon, 
    ActivityIcon, 
    PlayIcon, 
    EditIcon, 
    ChevronDownIcon, 
    ChevronUpIcon, 
    DumbbellIcon, 
    ClockIcon, 
    ArrowLeftIcon,
    SettingsIcon,
    PlusIcon,
    UserBadgeIcon,
    TrashIcon // Añadimos TrashIcon para poder eliminar bloques/semanas
} from './icons'; 
import { useAppContext } from '../contexts/AppContext';
import { WorkoutVolumeAnalysis } from './WorkoutVolumeAnalysis';
import { CaupolicanBody } from './CaupolicanBody';
import { calculateAverageVolumeForWeeks } from '../services/analysisService';
import { calculateUnifiedMuscleVolume } from '../services/volumeCalculator';
import { ExerciseMuscleInfo, ProgramWeek } from '../types';
import { MaximizeIcon, XIcon } from './icons'; // Asegúrate de tener estos iconos
import ProgramAdherenceWidget from './ProgramAdherenceWidget'; 
import ExerciseHistoryWidget from './ExerciseHistoryWidget'; 
import { RelativeStrengthAndBasicsWidget } from './RelativeStrengthAndBasicsWidget';
import { getOrderedDaysOfWeek } from '../utils/calculations';
import InteractiveWeekOverlay from './InteractiveWeekOverlay'; // <-- NUEVO OVERLAY

const getAbsoluteWeekIndex = (programData: Program, targetBlockId: string, targetWeekId: string) => {
    let abs = 0;
    for (const macro of programData.macrocycles) {
        for (const block of (macro.blocks || [])) {
            for (const meso of block.mesocycles) {
                for (const week of meso.weeks) {
                    if (block.id === targetBlockId && week.id === targetWeekId) return abs;
                    abs++;
                }
            }
        }
    }
    return abs;
};

const checkWeekHasEvent = (programData: Program, absIndex: number) => {
    return (programData.events || []).some(e => {
        if (e.repeatEveryXCycles) {
            const cycleLength = programData.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length || 1;
            return ((absIndex + 1) % (e.repeatEveryXCycles * cycleLength)) === 0;
        }
        return e.calculatedWeek === absIndex;
    });
};

const getDayName = (dayIndex: number, startWeekOn: number): string => {
    const days = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
    // Ajustamos el índice según cuándo empieza tu semana (0=Domingo, 1=Lunes)
    const realIndex = (startWeekOn + (dayIndex - 1)) % 7;
    return days[realIndex];
};

interface ProgramDetailProps {
    program: Program;
    onEdit?: () => void;
    isActive?: boolean;
    history?: any;
    settings?: any;
    isOnline?: boolean;
    onLogWorkout?: any;
    onEditProgram?: any;
    onEditSession?: any;
    onDeleteSession?: any;
    onAddSession?: any;
    onDeleteProgram?: any;
    onUpdateProgram?: any;
    onStartWorkout?: any; // Añadido para corregir error 2339
}

// --- COMPONENTE TARJETA DE SESIÓN (movido antes del principal) ---
const SessionCard: React.FC<{ 
    session: Session; 
    index: number; 
    onStart: () => void;
    onEdit: () => void;
    onDelete?: () => void;          // <-- NUEVA PROPIEDAD
    dayName?: string;
    exerciseList?: ExerciseMuscleInfo[];
}> = ({ session, index, onStart, onEdit, onDelete, dayName, exerciseList = [] }) => {
    const [isExpanded, setIsExpanded] = useState(false);
    const exercisesToDisplay: any[] = session.parts && session.parts.length > 0 
        ? session.parts.flatMap(p => p.exercises || []) 
        : (session.exercises || []);

    const totalSets = exercisesToDisplay.reduce((acc, ex) => acc + (ex.sets?.length || 0), 0);

    // Algoritmo de Fatiga Intrínseca (1-10)
    const calculateIntrinsicFatigue = (exInfo: ExerciseMuscleInfo) => {
        let score = 5;
        const isMultiJoint = exInfo.involvedMuscles.filter(m => m.role === 'primary').length > 1 || exInfo.involvedMuscles.length > 2;
        const equip = exInfo.equipment?.toLowerCase() || '';
        const isMachine = equip.includes('máquina') || equip.includes('maquina') || equip.includes('polea');
        const isFreeWeight = equip.includes('barra') || equip.includes('mancuerna');

        if (isMultiJoint) score += 3;
        else score -= 1;
        if (isMachine) score += 2;
        if (isFreeWeight) score -= 1;

        return Math.max(1, Math.min(10, score));
    };

        // Calcular Promedio de Fatiga de la Sesión
        const averageFatigue = useMemo(() => {
            if (exercisesToDisplay.length === 0 || exerciseList.length === 0) return 0;
            let totalFatigue = 0;
            let validExercises = 0;
            
            exercisesToDisplay.forEach((ex: any) => {
                const info = exerciseList.find((e: any) => e.id === ex.exerciseDbId || e.name === ex.name);
                if (info) {
                    totalFatigue += calculateIntrinsicFatigue(info);
                    validExercises += 1;
                }
            });
            
            return validExercises > 0 ? Math.round((totalFatigue / validExercises) * 10) / 10 : 0;
        }, [exercisesToDisplay, exerciseList]);

    const getFatigueColor = (score: number) => {
        if (score === 0) return 'bg-zinc-800 text-zinc-500';
        if (score <= 3) return 'bg-emerald-500 text-emerald-950 shadow-[0_0_10px_rgba(16,185,129,0.5)]';
        if (score <= 7) return 'bg-yellow-400 text-yellow-950 shadow-[0_0_10px_rgba(250,204,21,0.5)]';
        return 'bg-red-500 text-red-950 shadow-[0_0_10px_rgba(239,68,68,0.5)]';
    };

    const getFatigueLabel = (score: number) => {
        if (score === 0) return 'Sin Datos';
        if (score <= 3) return 'Fatiga Baja';
        if (score <= 7) return 'Fatiga Moderada';
        return 'Fatiga Alta';
    };

    return (
        <div className="bg-zinc-900/40 border border-white/5 rounded-2xl overflow-hidden mb-3 hover:bg-zinc-900/60 transition-colors">
            <div className="p-4">
                <div className="flex justify-between items-start mb-4">
                <div className="flex gap-4 items-center">
                        <div className="w-10 h-10 rounded-xl bg-black/50 border border-white/5 flex items-center justify-center text-xs font-black text-white">
                            {index + 1}
                        </div>
                        <div>
                            <h4 className="text-sm font-black text-white uppercase tracking-tight leading-none mb-1">{session.name}</h4>
                            <div className="flex items-center gap-3 text-[10px] text-zinc-500 font-medium">
                                <span className="flex items-center gap-1"><DumbbellIcon size={10}/> {exercisesToDisplay.length} Ejercicios</span>
                                <span className="flex items-center gap-1"><ClockIcon size={10}/> ~{totalSets * 3} min</span>
                            </div>
                        </div>
                    </div>
                    <button 
                        onClick={() => setIsExpanded(!isExpanded)}
                        className={`p-2 rounded-full transition-colors ${isExpanded ? 'bg-white/10 text-white' : 'text-zinc-600 hover:text-white'}`}
                    >
                        {isExpanded ? <ChevronUpIcon size={18} /> : <ChevronDownIcon size={18} />}
                    </button>
                </div>

                <div className="flex gap-2">
                    <button 
                        onClick={onStart}
                        className="flex-1 bg-white text-black text-[10px] font-black uppercase tracking-widest py-3 rounded-xl flex items-center justify-center gap-2 hover:bg-zinc-200 transition-colors shadow-lg"
                    >
                        <PlayIcon size={12} fill="black" /> INICIAR
                    </button>
                    <button 
                        onClick={(e) => {
                            e.stopPropagation();
                            onEdit(); 
                        }}
                        className="w-12 bg-black/50 border border-white/10 rounded-xl flex items-center justify-center text-zinc-400 hover:text-white hover:border-white/30 transition-colors"
                        title="Editar Sesión"
                    >
                        <EditIcon size={16} />
                    </button>
                    {onDelete && (
                        <button 
                            onClick={(e) => {
                                e.stopPropagation();
                                onDelete();
                            }}
                            className="w-12 bg-black/50 border border-white/10 rounded-xl flex items-center justify-center text-zinc-400 hover:text-red-500 hover:border-red-500/30 transition-colors"
                            title="Eliminar Sesión"
                        >
                            <TrashIcon size={16} />
                        </button>
                    )}
                </div>
            </div>

            {isExpanded && (
                <div className="bg-black/20 border-t border-white/5 p-4 space-y-4 animate-slide-down">
                    
                    {/* BARRA DE FATIGA PROMEDIO */}
                    {averageFatigue > 0 && (
                        <div className="flex items-center justify-between bg-zinc-950 border border-white/5 rounded-xl p-3">
                            <div className="flex items-center gap-2">
                                <ActivityIcon size={16} className={averageFatigue > 7 ? 'text-red-500' : averageFatigue > 3 ? 'text-yellow-400' : 'text-emerald-500'} />
                                <div className="flex flex-col">
                                    <span className="text-[9px] font-black uppercase text-zinc-500 tracking-widest">Impacto Estimado</span>
                                    <span className="text-xs font-bold text-white">{getFatigueLabel(averageFatigue)}</span>
                                </div>
                            </div>
                            <div className={`flex items-center justify-center w-8 h-8 rounded-full ${getFatigueColor(averageFatigue)} font-black text-sm`}>
                                {Math.round(averageFatigue)}
                            </div>
                        </div>
                    )}

<div className="overflow-x-auto no-scrollbar">
                        <div className="flex gap-3 pb-2">
                            {exercisesToDisplay.length > 0 ? (
                                exercisesToDisplay.map((exercise: any, idx: number) => (
                                    <div key={idx} className="shrink-0 w-56 bg-zinc-800/40 border border-white/10 rounded-xl p-3">
                                        <div className="flex justify-between items-center mb-1">
                                            <span className="text-xs font-bold text-zinc-300 truncate max-w-[120px]">{exercise.name}</span>
                                            <span className="text-[9px] text-zinc-400 bg-black/50 px-1.5 py-0.5 rounded whitespace-nowrap">{(exercise.sets || []).length} sets</span>
                                        </div>
                                        <div className="border-l border-white/10 pl-2">
                                            {(exercise.sets || []).slice(0, 1).map((set: any, sIdx: number) => (
                                                <p key={sIdx} className="text-[9px] text-zinc-400">
                                                    {set.targetReps} reps {set.targetRPE ? `@ RPE ${set.targetRPE}` : ''}
                                                </p>
                            ))}
                                            {(exercise.sets || []).length > 1 && (
                                                <p className="text-[8px] text-zinc-600 italic mt-1">+ {(exercise.sets || []).length - 1} series más</p>
                                            )}
                                        </div>
                                    </div>
                                ))
                            ) : (
                                <div className="shrink-0 w-full text-center py-4">
                                    <p className="text-[10px] text-zinc-500 italic">No hay ejercicios</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

const ProgramDetail: React.FC<ProgramDetailProps> = ({ program, onStartWorkout, onEdit, isActive, onDeleteSession }) => {
    const { history, settings, handleEditSession, handleBack, handleAddSession, isOnline, exerciseList, muscleHierarchy, handleStartProgram, handlePauseProgram, handleEditProgram, handleUpdateProgram, addToast, handleStartWorkout } = useAppContext();    
    const [activeTab, setActiveTab] = useState<'training' | 'metrics'>('training');
    const [subView, setSubView] = useState<'weekly' | 'macrocycle'>('weekly'); 
    const [editingWeekInfo, setEditingWeekInfo] = useState<{ 
        macroIndex: number; 
        blockIndex: number; 
        mesoIndex: number; 
        weekIndex: number; 
        week: ProgramWeek;
        isSimple: boolean;
    } | null>(null); 

    const [focusedMuscle, setFocusedMuscle] = useState<string | null>(null);
    const [tourStep, setTourStep] = useState(0); // 0 = Tour inactivo

    // --- ESTADO ---
    const [showAdvancedTransition, setShowAdvancedTransition] = useState(false);
    const [showSimpleTransition, setShowSimpleTransition] = useState(false);
    const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null);
    const [selectedWeekId, setSelectedWeekId] = useState<string | null>(null);
    const [selectedEventId, setSelectedEventId] = useState<string | null>(null);
    const [expandedRoadmapBlocks, setExpandedRoadmapBlocks] = useState<string[]>([]); // Para plegar/desplegar roadmap sin deseleccionar
    const [isEventModalOpen, setIsEventModalOpen] = useState(false);
    const [newEventData, setNewEventData] = useState({ id: '', title: '', repeatEveryXCycles: 1, calculatedWeek: 0, type: 'Test 1RM' });
    const [showCyclicHistory, setShowCyclicHistory] = useState(false);
    const [selectedMusclePos, setSelectedMusclePos] = useState<{muscle: string, x: number, y: number} | null>(null);


    // Calculamos las molestias históricas de este programa
    const programDiscomforts = useMemo(() => {
        const discomfortMap = new Map<string, number>();
        history.filter(log => log.programId === program.id).forEach(log => {
            (log.discomforts || []).forEach(d => {
                discomfortMap.set(d, (discomfortMap.get(d) || 0) + 1);
            });
        });
        return Array.from(discomfortMap.entries())
            .map(([name, count]) => ({ name, count }))
            .sort((a, b) => b.count - a.count);
    }, [history, program.id]);

    // 1. Preparar datos del Roadmap (Bloques)
    const roadmapBlocks = useMemo(() => {
        return program.macrocycles.flatMap((macro, macroIdx) => 
            (macro.blocks || []).map((block, blockIdx) => ({
                ...block,
                macroIndex: macroIdx,
                blockIndex: blockIdx, 
                totalWeeks: block.mesocycles.reduce((acc, m) => acc + m.weeks.length, 0)
            }))
        );
    }, [program]);

    // --- LÓGICA DE COLORES ---
    const { activeProgramState } = useAppContext();
    
    const activeBlockId = useMemo(() => {
        if (!activeProgramState || activeProgramState.programId !== program.id) return null;
        const current = roadmapBlocks.find(b => 
            b.macroIndex === activeProgramState.currentMacrocycleIndex && 
            b.blockIndex === activeProgramState.currentBlockIndex
        );
        return current ? current.id : null;
    }, [activeProgramState, program.id, roadmapBlocks]);

    // 2. Preparar datos de Semanas con mesoIndex ABSOLUTO dentro del macrociclo
    const currentWeeks = useMemo(() => {
        if (!selectedBlockId) return [];
        const block = roadmapBlocks.find(b => b.id === selectedBlockId);
        if (!block) return [];
        
        // Calcular offset: contar mesociclos de bloques anteriores en el mismo macrociclo
        const macro = program.macrocycles[block.macroIndex];
        let mesoOffset = 0;
        if (macro) {
            for (const b of (macro.blocks || [])) {
                if (b.id === block.id) break;
                mesoOffset += b.mesocycles.length;
            }
        }
        
        return block.mesocycles.flatMap((meso, localMesoIdx) => 
            meso.weeks.map(w => ({ 
                ...w, 
                mesoGoal: meso.goal,
                mesoIndex: mesoOffset + localMesoIdx, 
            }))
        );
    }, [selectedBlockId, roadmapBlocks, program.macrocycles]);

    // CÁLCULO DE DATOS PARA CAUPOLICÁN (NUEVO ALGORITMO UNIFICADO AVANZADO)
    const visualizerData = useMemo(() => {
        if (currentWeeks.length === 0) return [];
        const allSessions = currentWeeks.flatMap(w => w.sessions);
        const totalVol = calculateUnifiedMuscleVolume(allSessions, exerciseList);
        
        // Promediar por la cantidad de semanas para Caupolicán
        return totalVol.map(v => ({
            ...v,
            displayVolume: Math.round((v.displayVolume / currentWeeks.length) * 10) / 10
        }));
    }, [currentWeeks, exerciseList]);

    // Inicializar y validar selección: si el ID seleccionado ya no existe, resetear al primero disponible
    useEffect(() => {
        if (roadmapBlocks.length > 0) {
            if (!selectedBlockId || !roadmapBlocks.find(b => b.id === selectedBlockId)) {
                setSelectedBlockId(roadmapBlocks[0].id);
            }
        } else {
            setSelectedBlockId(null);
        }
    }, [roadmapBlocks]);

    useEffect(() => {
        if (currentWeeks.length > 0) {
            if (!selectedWeekId || !currentWeeks.find(w => w.id === selectedWeekId)) {
                setSelectedWeekId(currentWeeks[0].id);
            }
        } else {
            setSelectedWeekId(null);
        }
    }, [selectedBlockId, currentWeeks]);

    // Disparador del Tour de Onboarding
    useEffect(() => {
        const tourSeen = localStorage.getItem(`kpkn_tour_seen_${program.id}`);
        if (!tourSeen && program.id) {
            // Pequeño retraso para que la vista cargue antes de lanzar el tour
            const timer = setTimeout(() => setTourStep(1), 500);
            return () => clearTimeout(timer);
        }
    }, [program.id]);

    // 3. Filtrar sesiones a mostrar
    const displayedSessions = useMemo(() => {
        if (!selectedWeekId) return [];
        const week = currentWeeks.find(w => w.id === selectedWeekId);
        return week ? week.sessions : [];
    }, [selectedWeekId, currentWeeks]);

    // Handler para el botón de editar programa
    const handleProgramEdit = (e: React.MouseEvent) => {
        e.stopPropagation();
        handleEditProgram(program.id);
    };

    // Handler para editar sesión específica
    const onEditSessionClick = (session: Session) => {
        const block = roadmapBlocks.find(b => b.id === selectedBlockId);
        const week = currentWeeks.find(w => w.id === selectedWeekId);
        if (block && week) handleEditSession(program.id, block.macroIndex, week.mesoIndex, week.id, session.id);
    };

    // --- LÓGICA DEL ORBE DE ADHERENCIA ---
    const [showAdherenceModal, setShowAdherenceModal] = useState(false);
    const [metricFilter, setMetricFilter] = useState<'todos' | 'volumen' | 'fuerza' | 'historial'>('todos'); // <--- NUEVA LÍNEA

    const programLogs = useMemo(() => history.filter(log => log.programId === program.id).sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()), [history, program.id]);

    const totalAdherence = useMemo(() => {
        const completedIds = new Set(programLogs.map(l => l.sessionId));
        const allSessions = program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles.flatMap(meso => meso.weeks.flatMap(w => w.sessions))));
        if (allSessions.length === 0) return 0;
        const completedCount = allSessions.filter(s => completedIds.has(s.id)).length;
        return Math.round((completedCount / allSessions.length) * 100);
    }, [programLogs, program]);

    const weeklyAdherence = useMemo(() => {
        return currentWeeks.map((week, idx) => {
            // Corrección Error 2339: Filtramos logs buscando si el sessionId del log está en la semana actual
            const weekSessionIds = new Set(week.sessions.map(s => s.id));
            const logs = programLogs.filter(l => weekSessionIds.has(l.sessionId));
            
            const completed = new Set(logs.map(l => l.sessionId)).size;
            const planned = week.sessions.length;
            const pct = planned > 0 ? Math.round((completed / planned) * 100) : 0;
            return { weekName: `Semana ${idx + 1}`, pct, completed, planned };
        });
    }, [currentWeeks, programLogs]);

    return (
        <div className="fixed inset-0 z-[100] bg-black text-white overflow-y-auto custom-scrollbar">
            {/* --- 1. PORTADA / HEADER --- */}
            <div className="relative h-80 w-full shrink-0">
                {/* Imagen de Fondo */}
                {program.coverImage ? (
                    <img 
                        src={program.coverImage} 
                        alt={program.name} 
                        className="absolute inset-0 w-full h-full object-cover"
                    />
                ) : (
                    <div className="absolute inset-0 bg-black flex items-center justify-center">
                        <DumbbellIcon size={64} className="text-zinc-900" />
                    </div>
                )}
                
                {/* Degradados para legibilidad */}
                <div className="absolute inset-0 bg-gradient-to-t from-black via-black/20 to-transparent" />
                <div className="absolute inset-0 bg-gradient-to-b from-black/60 to-transparent h-32" />

                {/* Botón Atrás (CORREGIDO: Usa handleBack) */}
                <button 
                    onClick={handleBack}
                    className="absolute top-4 left-4 z-50 w-10 h-10 rounded-full bg-black/40 backdrop-blur-md border border-white/10 flex items-center justify-center text-white hover:bg-white/20 transition-colors"
                >
                    <ChevronDownIcon size={20} className="rotate-90" />
                </button>

                {/* Título e Info del Programa */}
                <div className="absolute bottom-0 left-0 w-full p-6 pr-[120px] z-20 space-y-2">
                    <div className="flex items-center gap-2 flex-wrap">
                        <span className="bg-white/10 backdrop-blur-md border border-white/20 text-[9px] font-black uppercase tracking-widest px-3 py-1 rounded-full text-white">
                            {program.mode === 'powerlifting' ? 'Powerlifting' : 'Hipertrofia'}
                        </span>
                        {activeProgramState?.programId === program.id && activeProgramState?.status === 'active' && (
                            <span className="bg-emerald-500 text-black text-[9px] font-black uppercase tracking-widest px-3 py-1 rounded-full shadow-[0_0_15px_rgba(16,185,129,0.6)]">
                                Activo
                            </span>
                        )}
                        {activeProgramState?.programId === program.id && activeProgramState?.status === 'paused' && (
                            <span className="bg-yellow-400 text-black text-[9px] font-black uppercase tracking-widest px-3 py-1 rounded-full shadow-[0_0_15px_rgba(250,204,21,0.6)]">
                                Pausado
                            </span>
                        )}
                    </div>
                    <h1 className="text-3xl sm:text-4xl font-black text-white uppercase tracking-tighter leading-none text-shadow-lg line-clamp-2 break-words">
                        {program.name}
                    </h1>
                    <div className="flex items-center gap-4 text-xs text-zinc-300 font-medium">
                        {program.author && program.author.trim() !== '' && (
                            <>
                                <span className="flex items-center gap-1"><UserBadgeIcon size={12}/> {program.author}</span>
                                <span>•</span>
                            </>
                        )}
                        <span className="uppercase font-bold tracking-widest text-[10px] text-zinc-400">
                            {program.structure === 'simple' || (program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1)
                                ? ((program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length || 0) > 1 
                                    ? `${program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length} Semanas` 
                                    : 'Simple')
                                : `${program.macrocycles.length} ${program.macrocycles.length === 1 ? 'Macrociclo' : 'Macrociclos'}`}
                        </span>
                    </div>
                </div>

                {/* BOTONES FLOTANTES: INICIAR, PAUSAR Y EDITAR PROGRAMA */}
                <div className="absolute bottom-6 right-6 z-50 flex items-center gap-3">
                    <button 
                        onClick={handleProgramEdit}
                        className="w-12 h-12 rounded-full bg-black/40 backdrop-blur-md border border-white/20 flex items-center justify-center text-white hover:bg-white/20 transition-colors shadow-lg"
                        title="Editar Programa (Avanzado)"
                    >
                        <EditIcon size={20} />
                    </button>
                    
                    {/* Si está activo, muestra PAUSA. Si no (o está pausado), muestra PLAY */}
                    {activeProgramState?.programId === program.id && activeProgramState?.status === 'active' ? (
                        <button 
                            onClick={(e) => { e.stopPropagation(); handlePauseProgram(); }}
                            className="w-14 h-14 rounded-full bg-yellow-400 flex items-center justify-center text-black hover:scale-105 transition-transform shadow-[0_0_30px_rgba(250,204,21,0.4)]"
                            title="Pausar Programa"
                        >
                            {/* Icono de Pausa hecho con divs */}
                            <div className="flex gap-1.5">
                                <div className="w-1.5 h-4 bg-black rounded-sm"></div>
                                <div className="w-1.5 h-4 bg-black rounded-sm"></div>
                            </div>
                        </button>
                    ) : (
                        <button 
                            onClick={(e) => { e.stopPropagation(); handleStartProgram(program.id); }}
                            className="w-14 h-14 rounded-full bg-white flex items-center justify-center text-black hover:scale-105 transition-transform shadow-[0_0_30px_rgba(255,255,255,0.4)]"
                            title={activeProgramState?.programId === program.id && activeProgramState?.status === 'paused' ? "Reanudar Programa" : "Iniciar Programa"}
                        >
                            <PlayIcon size={24} fill="black" className="ml-1" />
                        </button>
                    )}
                </div>
            </div>

            {/* --- 2. TABS DE NAVEGACIÓN (STICKY) --- */}
            <div className="sticky top-0 z-50 bg-[#050505]/95 backdrop-blur-3xl border-b border-white/10 flex justify-center shadow-[0_15px_30px_rgba(0,0,0,0.9)]">
                <button 
                    onClick={() => setActiveTab('training')}
                    className={`py-4 px-8 text-[10px] font-black uppercase tracking-[0.2em] transition-all relative
                        ${activeTab === 'training' ? 'text-white scale-105' : 'text-zinc-500 hover:text-zinc-300'}`}
                >
                    Entrenamiento
                    {activeTab === 'training' && <div className="absolute bottom-0 left-1/2 -translate-x-1/2 w-8 h-0.5 bg-gradient-to-r from-blue-500 to-cyan-500 rounded-full shadow-[0_0_10px_rgba(59,130,246,0.8)]" />}
                </button>
                <button 
                    onClick={() => setActiveTab('metrics')}
                    className={`py-4 px-8 text-[10px] font-black uppercase tracking-[0.2em] transition-all relative
                        ${activeTab === 'metrics' ? 'text-white scale-105' : 'text-zinc-500 hover:text-zinc-300'}`}
                >
                    Métricas
                    {activeTab === 'metrics' && <div className="absolute bottom-0 left-1/2 -translate-x-1/2 w-8 h-0.5 bg-gradient-to-r from-purple-500 to-pink-500 rounded-full shadow-[0_0_10px_rgba(168,85,247,0.8)]" />}
                </button>
            </div>

            {/* --- 3. CONTENIDO PRINCIPAL --- */}
            <div className="relative min-h-[500px]"> 
                
                {/* A) PESTAÑA ENTRENAMIENTO */}
                {activeTab === 'training' && (() => {
                    // --- LÓGICA DERIVADA PARA ESTA VISTA ---
                    // 1. Detectar si es un programa cíclico simple (Estructura Simple)
                    const isCyclic = program.structure === 'simple' || (!program.structure && program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1);
                    
                    const selectedBlock = roadmapBlocks.find(b => b.id === selectedBlockId);
                    const selectedBlockIndex = roadmapBlocks.findIndex(b => b.id === selectedBlockId);
                    const selectedWeekIndex = currentWeeks.findIndex(w => w.id === selectedWeekId) + 1;
                    
                    // 2. Detectar si el bloque seleccionado ya es del pasado
                    let isPastBlock = false;
                    if (activeProgramState && activeProgramState.programId === program.id && activeProgramState.status === 'active') {
                        const activeBlock = roadmapBlocks.find(b => b.macroIndex === activeProgramState.currentMacrocycleIndex && b.blockIndex === activeProgramState.currentBlockIndex);
                        if (activeBlock && selectedBlock) {
                            if (selectedBlock.macroIndex < activeBlock.macroIndex) isPastBlock = true;
                            if (selectedBlock.macroIndex === activeBlock.macroIndex && selectedBlock.blockIndex < activeBlock.blockIndex) isPastBlock = true;
                        }
                    }

                    // 3. Obtener el historial de este programa ordenado por fecha
                    const programLogs = history.filter(log => log.programId === program.id).sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

                    return (
                        <div className="animate-fade-in space-y-0">
                            
                            {/* TOGGLE VISTA SEMANAL / MACROCICLO (No sticky, minimalista) */}
                            <div className="flex justify-center pt-8 pb-4 relative z-40 bg-black border-b border-white/5">
                                <div className="flex bg-zinc-900/60 border border-white/5 p-1 rounded-full shadow-inner">
                                    <button onClick={() => setSubView('weekly')} className={`px-6 py-2 text-[9px] font-black uppercase tracking-widest rounded-full transition-all ${subView === 'weekly' ? 'bg-white text-black shadow-sm' : 'text-zinc-500 hover:text-white'}`}>Semanal</button>
                                    <button onClick={() => setSubView('macrocycle')} className={`px-6 py-2 text-[9px] font-black uppercase tracking-widest rounded-full transition-all ${subView === 'macrocycle' ? 'bg-white text-black shadow-sm' : 'text-zinc-500 hover:text-white'}`}>Macrociclo</button>
                                </div>
                            </div>

                            {subView === 'weekly' && isCyclic ? (
                                // =========================================================
                                // VISTA CÍCLICA COMPACTA      
                                // =========================================================
                                <div className="px-4 py-6 flex flex-col items-center">
                                    <div className="flex items-center justify-between w-full max-w-md mb-6 bg-zinc-900/40 p-4 rounded-2xl border border-white/5">
                                        <div>
                                            <h3 className="text-lg font-black text-white uppercase tracking-tight">Bucle Base</h3>
                                            <p className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest">Estructura Cíclica</p>
                                        </div>
                                        <button 
                                            onClick={() => setShowCyclicHistory(!showCyclicHistory)}
                                            className="px-4 py-2 rounded-xl bg-blue-900/20 text-blue-400 text-[9px] font-black uppercase tracking-widest border border-blue-500/30 hover:bg-blue-600/40 transition-colors flex items-center gap-1.5"
                                        >
                                            <ActivityIcon size={14} />
                                            {showCyclicHistory ? 'Ver Rutinas' : 'Ver Historial'}
                                        </button>
                                    </div>

                                    <div className="w-full max-w-md pb-24">
                                        {showCyclicHistory ? (
                                            // HISTORIAL CÍCLICO
                                            <div className="space-y-4 animate-slide-up">
                                                <div className="flex items-center gap-3 mb-6">
                                                    <div className="h-px bg-white/10 flex-1"></div>
                                                    <h4 className="text-[9px] font-black text-zinc-500 uppercase tracking-widest">Ciclos Completados</h4>
                                                    <div className="h-px bg-white/10 flex-1"></div>
                                                </div>
                                                
                                                {programLogs.length > 0 ? programLogs.map(log => (
                                                    <div key={log.id} className="bg-zinc-900/40 border border-white/5 p-5 rounded-3xl shadow-lg relative overflow-hidden">
                                                        <div className="absolute top-0 left-0 w-1 h-full bg-emerald-500"></div>
                                                        <div className="flex justify-between items-start mb-4">
                                                            <div>
                                                                <div className="text-sm font-bold text-white mb-0.5">{log.sessionName || 'Sesión Completa'}</div>
                                                                <div className="text-[10px] text-zinc-500">{new Date(log.date).toLocaleDateString()}</div>
                                                            </div>
                                                            <ActivityIcon size={16} className="text-emerald-500" />
                                                        </div>
                                                        <div className="space-y-2 border-t border-white/5 pt-3">
                                                            {log.completedExercises.map((ex, i) => (
                                                                <div key={i} className="flex justify-between items-center text-[11px]">
                                                                    <span className="text-zinc-400 truncate pr-2 flex-1">{ex.exerciseName}</span>
                                                                    <span className="text-zinc-300 font-bold bg-black/50 px-2 py-1 rounded-md shadow-inner">{ex.sets.length} {ex.sets.length === 1 ? 'set' : 'sets'}</span>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>
                                                )) : (
                                                    <div className="text-center py-10 bg-zinc-900/20 rounded-3xl border border-dashed border-white/10">
                                                        <p className="text-xs text-zinc-500 font-medium">Aún no hay historial registrado.</p>
                                                    </div>
                                                )}
                                            </div>
                                        ) : (
                                            // PLAN CÍCLICO (Avanzado con Días de la Semana y Edición)
                                            <div className="space-y-3 animate-slide-up">
                                                
                                                {/* SELECTOR DE SEMANAS CÍCLICAS Y EVENTOS PARA PROGRAMAS SIMPLES */}
                                                {(currentWeeks.length > 1 || (program.events && program.events.length > 0)) && (
                                                    <div className="w-full overflow-x-auto no-scrollbar mb-6 pb-2">
                                                        <div className="flex gap-2 w-max items-center">
                                                            {currentWeeks.map((w, idx) => (
                                                                <button 
                                                                    key={w.id} 
                                                                    onClick={(e) => { 
                                                                        setSelectedWeekId(w.id); 
                                                                        setSelectedEventId(null);
                                                                        e.currentTarget.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
                                                                    }}
                                                                    className={`shrink-0 px-5 py-2 rounded-full text-[10px] font-black uppercase tracking-widest transition-all ${w.id === selectedWeekId && !selectedEventId ? 'bg-blue-600 text-white shadow-[0_0_15px_rgba(37,99,235,0.5)] scale-105' : 'bg-zinc-900 border border-white/10 text-zinc-500 hover:bg-zinc-800 hover:text-white'}`}
                                                                >
                                                                    {w.name || `Semana ${idx + 1}`}
                                                                </button>
                                                            ))}
                                                            {(program.events || []).map((ev) => (
                                                                <button 
                                                                    key={ev.id} 
                                                                    onClick={() => setSelectedEventId(ev.id || null)}
                                                                    className={`px-5 py-2 rounded-full text-[10px] font-black uppercase tracking-widest transition-all flex items-center gap-1.5 ${ev.id === selectedEventId ? 'bg-yellow-500 text-black shadow-[0_0_15px_rgba(234,179,8,0.5)] scale-105' : 'bg-yellow-900/20 border border-yellow-500/30 text-yellow-500 hover:bg-yellow-900/40'}`}
                                                                >
                                                                    <ActivityIcon size={12}/> {ev.title}
                                                                </button>
                                                            ))}
                                                        </div>
                                                    </div>
                                                )}

                                                {(() => {
                                                    // RENDERIZADO DE SEMANA DE EVENTO ESPECIAL
                                                    if (selectedEventId) {
                                                        const ev = program.events?.find(e => e.id === selectedEventId);
                                                        if (!ev) return null;
                                                        const sessionsForEvent = ev.sessions || [];
                                                        
                                                        return (
                                                            <div className="space-y-5 text-left w-full animate-fade-in">
                                                                <div className="bg-yellow-900/10 border border-yellow-500/20 rounded-[2rem] p-6 text-center">
                                                                    <ActivityIcon size={24} className="text-yellow-500 mx-auto mb-2" />
                                                                    <h4 className="text-lg font-black text-white uppercase">{ev.title}</h4>
                                                                    <p className="text-[10px] text-zinc-400 mt-1">Semana Especial • Se activa cada {ev.repeatEveryXCycles} Ciclos</p>
                                                                </div>
                                                                
                                                                <div className="bg-zinc-900/30 border border-white/5 rounded-[2rem] p-4 relative overflow-hidden">
                                                                    <div className="flex items-center justify-between mb-4">
                                                                        <div>
                                                                            <h4 className="text-sm font-black uppercase tracking-wider text-white">Sesiones del Evento</h4>
                                                                            <p className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest">Planificación Exclusiva</p>
                                                                        </div>
                                                                        <button 
                                                                            onClick={() => {
                                                                                const updated = JSON.parse(JSON.stringify(program));
                                                                                const targetEv = updated.events.find((e: any) => e.id === selectedEventId);
                                                                                if(targetEv) {
                                                                                    if(!targetEv.sessions) targetEv.sessions = [];
                                                                                    targetEv.sessions.push({ id: crypto.randomUUID(), name: 'Sesión del Evento', exercises: [] });
                                                                                    if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                }
                                                                            }}
                                                                            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-black border border-white/10 text-[10px] font-black text-zinc-400 uppercase tracking-widest hover:bg-zinc-800 hover:text-white hover:border-white/30 transition-all shadow-sm"
                                                                        >
                                                                            <PlusIcon size={14} /> Crear
                                                                        </button>
                                                                    </div>
                                                                    {sessionsForEvent.length > 0 ? (
                                                                        <div className="space-y-3">
                                                                            {sessionsForEvent.map((session: any, idx: number) => (
                                                                                <SessionCard 
                                                                                    key={session.id} 
                                                                                    session={session} 
                                                                                    index={idx} 
                                                                                    dayName="Día Específico"
                                                                                    exerciseList={exerciseList}
                                                                                    onStart={() => handleStartWorkout(session, program)}
                                                                                    onEdit={() => {
                                                                                         // Para editar usa la lógica genérica o avisa que es especial
                                                                                         addToast("Para editar sesiones de evento usa el Editor Avanzado de momento", "suggestion");
                                                                                    }} 
                                                                                    onDelete={() => {
                                                                                        if(window.confirm('¿Eliminar sesión del evento?')) {
                                                                                            const updated = JSON.parse(JSON.stringify(program));
                                                                                            const targetEv = updated.events.find((e: any) => e.id === selectedEventId);
                                                                                            if(targetEv) {
                                                                                                targetEv.sessions = targetEv.sessions.filter((s:any) => s.id !== session.id);
                                                                                                if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                            }
                                                                                        }
                                                                                    }}
                                                                                />
                                                                            ))}
                                                                        </div>
                                                                    ) : (
                                                                        <div className="text-center py-8">
                                                                            <p className="text-xs text-zinc-500 font-medium">No hay sesiones creadas para este evento especial.</p>
                                                                        </div>
                                                                    )}
                                                                </div>
                                                            </div>
                                                        );
                                                    }

                                                    // Lógica original de semanas cíclicas
                                                    const cyclicWeek = currentWeeks.find(w => w.id === selectedWeekId) || currentWeeks[0];
                                                    if (!cyclicWeek) return null;
                                                    const sessionsForWeek = cyclicWeek.sessions || [];
                                                    
                                                    const maxDayAssigned = sessionsForWeek.reduce((max: number, s: any) => Math.max(max, s.dayOfWeek || 0), 0);
                                                    const isStandardWeek = maxDayAssigned <= 6;
                                                    const startOn = settings?.startWeekOn ?? 1;
                                                    
                                                    const daysArray = isStandardWeek 
                                                        ? getOrderedDaysOfWeek(startOn).map(d => d.value) 
                                                        : Array.from({ length: maxDayAssigned + 1 }, (_, i) => i);

                                                    return (
                                                        <div className="space-y-5 text-left w-full">
                                                            {daysArray.map(dayNum => {
                                                                const daySessions = sessionsForWeek.filter((s: any) => s.dayOfWeek === dayNum);
                                                                let dayTitle = '';
                                                                let daySubtitle = '';
                                                                let dayAbbrev = '';
                                                    
                                                                if (isStandardWeek) {
                                                                    const daysNames = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
                                                                    const dayName = daysNames[dayNum]; 
                                                                    dayTitle = dayName;
                                                                    dayAbbrev = dayName.substring(0, 3).toUpperCase();
                                                                    daySubtitle = daySessions.length > 0 ? 'Día de Entrenamiento' : 'Descanso / Recuperación';
                                                                } else {
                                                                    dayTitle = `Día ${dayNum}`;
                                                                    dayAbbrev = `D${dayNum}`;
                                                                    daySubtitle = daySessions.length > 0 ? 'Entrenamiento' : 'Descanso';
                                                                }

                                                                return (
                                                                    <div key={`day-group-${dayNum}`} className="bg-zinc-900/30 border border-white/5 rounded-[2rem] p-4 relative overflow-hidden">
                                                                        <div className="flex items-center justify-between mb-4">
                                                                            <div className="flex items-center gap-3">
                                                                                <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-xs font-black border ${daySessions.length > 0 ? 'bg-blue-900/20 border-blue-500/30 text-blue-400 shadow-[0_0_15px_rgba(59,130,246,0.2)]' : 'bg-black border-zinc-800 text-zinc-600'}`}>
                                                                                    {dayAbbrev}
                                                                                </div>
                                                                                <div>
                                                                                    <h4 className={`text-sm font-black uppercase tracking-wider ${daySessions.length > 0 ? 'text-white' : 'text-zinc-500'}`}>
                                                                                        {dayTitle}
                                                                                    </h4>
                                                                                    <p className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest">{daySubtitle}</p>
                                                                                </div>
                                                                            </div>

                                                                            <button 
                                                                                onClick={() => {
                                                                                    if (selectedBlock && handleAddSession) {
                                                                                        handleAddSession(program.id, selectedBlock.macroIndex, cyclicWeek.mesoIndex, cyclicWeek.id, dayNum);
                                                                                    }
                                                                                }}
                                                                                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-black border border-white/10 text-[10px] font-black text-zinc-400 uppercase tracking-widest hover:bg-zinc-800 hover:text-white hover:border-white/30 transition-all shadow-sm"
                                                                            >
                                                                                <PlusIcon size={14} />
                                                                                <span className="hidden sm:inline">Crear</span>
                                                                            </button>
                                                                        </div>

                                                                        {daySessions.length > 0 ? (
                                                                            <div className="space-y-3">
                                                                                {daySessions.map((session: any, idx: number) => (
                                                                                    <SessionCard 
                                                                                        key={session.id} 
                                                                                        session={session} 
                                                                                        index={idx} 
                                                                                        dayName={dayTitle}
                                                                                        exerciseList={exerciseList}
                                                                                        onStart={() => {
                                                                                            handleStartWorkout(session, program, undefined, { macroIndex: selectedBlock?.macroIndex || 0, mesoIndex: cyclicWeek?.mesoIndex || 0, weekId: cyclicWeek?.id || '' });
                                                                                        }}
                                                                                        onEdit={() => onEditSessionClick(session)}
                                                                                        onDelete={() => {
                                                                                            if (window.confirm('¿Eliminar esta sesión?')) {
                                                                                                onDeleteSession(session.id, program.id, selectedBlock!.macroIndex, cyclicWeek!.mesoIndex, cyclicWeek!.id);
                                                                                            }
                                                                                        }}
                                                                                    />
                                                                                ))}
                                                                            </div>
                                                                        ) : (
                                                                            <button 
                                                                                onClick={() => {
                                                                                    if (selectedBlock && handleAddSession) {
                                                                                        handleAddSession(program.id, selectedBlock.macroIndex, cyclicWeek.mesoIndex, cyclicWeek.id, dayNum);
                                                                                    }
                                                                                }}
                                                                                className="w-full h-14 rounded-2xl bg-black/50 border border-dashed border-white/10 flex items-center justify-center hover:bg-white/5 hover:border-white/30 transition-all group"
                                                                            >
                                                                                <PlusIcon size={16} className="text-zinc-600 group-hover:text-white mr-2 transition-colors" />
                                                                                <span className="text-[10px] font-bold text-zinc-600 group-hover:text-white uppercase tracking-widest transition-colors">Añadir sesión a {dayTitle}</span>
                                                                            </button>
                                                                        )}
                                                                    </div>
                                                                );
                                                            })}

                                                        </div>
                                                    );
                                                })()}
                                            </div>
                                        )}
                                    </div>
                                </div>

                            ) : (
                                // =========================================================
                                // VISTA POR BLOQUES (Roadmap Moderno con resplandores)      
                                // =========================================================
                                <>
                                    {subView === 'weekly' ? (
                                        <div className="animate-fade-in">
                                            {/* TÍTULO DINÁMICO (Píldora Minimalista y Plana) */}
                                            <div className="pt-6 pb-2 bg-black flex justify-center items-center z-40 relative">
                                                {(() => {
                                                    const absIdx = selectedWeekId && selectedBlock ? getAbsoluteWeekIndex(program, selectedBlock.id, selectedWeekId) : -1;
                                                    const isEventWeek = absIdx >= 0 && checkWeekHasEvent(program, absIdx);
                                                    return (
                                                        <div className={`px-4 py-2 rounded-2xl flex items-center gap-2 shadow-sm border ${isEventWeek ? 'bg-yellow-900/20 border-yellow-500/50 shadow-[0_0_15px_rgba(250,204,21,0.2)]' : 'bg-zinc-950 border-white/10'}`}>
                                                            <span className={`text-[10px] font-bold uppercase tracking-wider ${isEventWeek ? 'text-yellow-400' : 'text-white'}`}>{selectedBlock?.name || `Bloque ${selectedBlockIndex + 1}`}</span>
                                                            <span className="text-zinc-700 font-black">/</span>
                                                            <span className={`text-[10px] font-bold uppercase tracking-wider ${isEventWeek ? 'text-yellow-200' : 'text-zinc-400'}`}>Semana {selectedWeekIndex}</span>
                                                            {isEventWeek && <span className="ml-2 bg-yellow-400 text-black text-[8px] px-2 py-0.5 rounded-full font-black tracking-widest uppercase shadow-md">Evento</span>}
                                                        </div>
                                                    )
                                                })()}
                                            </div>

                                            {/* NUEVO ROADMAP AVANZADO (Pestañas Sólidas de Bloques y Semanas) */}
                                            <div className="bg-[#050505] border-b border-white/5 pb-5 pt-3 relative">
                                                
                                                {/* 1. Selector de Bloques */}
                                                <div className="w-full overflow-x-auto no-scrollbar px-4 mb-4">
                                                    <div className="flex gap-2 w-max pb-1">
                                                        {roadmapBlocks.map((block, idx) => {
                                                            const isSelected = block.id === selectedBlockId;
                                                            const isCurrent = block.id === activeBlockId;
                                                            return (
                                                                <button 
                                                                    key={block.id}
                                                                    onClick={(e) => {
                                                                        setSelectedBlockId(block.id);
                                                                        e.currentTarget.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
                                                                    }}
                                                                    className={`px-6 py-3 rounded-full text-[10px] font-black uppercase tracking-[0.2em] transition-all duration-300 flex items-center gap-2 border
                                                                        ${isSelected 
                                                                            ? 'bg-white text-black border-white shadow-[0_0_15px_rgba(255,255,255,0.2)] scale-105' 
                                                                            : 'bg-zinc-900 border-white/10 text-zinc-500 hover:text-white hover:bg-zinc-800 hover:border-white/30'
                                                                        }`}
                                                                >
                                                                    {isCurrent && <div className="w-2 h-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.8)]"></div>}
                                                                    Bloque {idx + 1}
                                                                </button>
                                                            );
                                                        })}
                                                    </div>
                                                </div>

                                                {/* 2. Selector de Semanas del Bloque Activo */}
                                                <div className="w-full overflow-x-auto no-scrollbar px-4">
                                                    <div className="flex gap-2 w-max items-center">
                                                        <span className="text-[9px] font-black text-zinc-600 uppercase tracking-widest mr-2 flex items-center gap-1">
                                                            <CalendarIcon size={12}/> Semanas:
                                                        </span>
                                                        <div className="flex gap-2 bg-zinc-900/50 p-1.5 rounded-full border border-white/5">
                                                            {currentWeeks.map((week, wIdx) => {
                                                                const isWeekSelected = week.id === selectedWeekId;
                                                                const absIdx = selectedBlockId ? getAbsoluteWeekIndex(program, selectedBlockId, week.id) : -1;
                                                                const hasEvent = checkWeekHasEvent(program, absIdx);

                                                                return (
                                                                    <button
                                                                        key={week.id}
                                                                        onClick={(e) => {
                                                                            setSelectedWeekId(week.id);
                                                                            e.currentTarget.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
                                                                        }}
                                                                        className={`shrink-0 relative px-5 py-2.5 rounded-full text-[10px] font-black uppercase tracking-widest transition-all duration-300 flex items-center justify-center min-w-[3rem]
                                                                            ${isWeekSelected
                                                                                ? 'bg-blue-600 text-white shadow-[0_0_15px_rgba(37,99,235,0.4)]'
                                                                                : 'bg-transparent text-zinc-500 hover:text-white hover:bg-zinc-800'
                                                                            }`}
                                                                    >
                                                                        {hasEvent && <div className={`absolute top-0 right-0 w-2 h-2 rounded-full ${isWeekSelected ? 'bg-yellow-400 shadow-[0_0_8px_rgba(250,204,21,0.8)]' : 'bg-yellow-500/50'}`}></div>}
                                                                        S{wIdx + 1}
                                                                    </button>
                                                                );
                                                            })}
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>

                                            {/* CONTENIDO DEL BLOQUE */}
                                            <div className="px-4 pb-24 pt-6 space-y-3">
                                                {isPastBlock ? (
                                                    // ------------------------------------------------
                                                    // HISTORIAL DEL BLOQUE PASADO (Diseño Acordeón)    
                                                    // ------------------------------------------------
                                                    <div className="animate-fade-in">
                                                        <div className="bg-emerald-950/20 border border-emerald-500/10 rounded-3xl p-6 text-center mb-8 relative overflow-hidden shadow-2xl">
                                                            <div className="absolute inset-0 bg-gradient-to-b from-emerald-500/5 to-transparent pointer-events-none" />
                                                            <ActivityIcon size={32} className="text-emerald-500 mx-auto mb-3" />
                                                            <h3 className="text-lg font-black text-white uppercase tracking-tight">Bloque Superado</h3>
                                                            <p className="text-xs text-zinc-400 mt-2">Este bloque ya pertenece a tu historial de progreso.</p>
                                                        </div>
                                                        
                                                        <div className="space-y-6">
                                                            {currentWeeks.map((week, wIdx) => {
                                                                const logsForWeek = programLogs.filter((log: any) => log.weekId === week.id || week.sessions.some(s => s.id === log.sessionId));
                                                                if (logsForWeek.length === 0) return null;
                                                                
                                                                return (
                                                                    <div key={week.id} className="bg-zinc-900/30 rounded-[2rem] p-5 border border-white/5">
                                                                        <h4 className="text-[10px] font-black text-blue-400 uppercase tracking-widest mb-4 flex items-center gap-2">
                                                                            <div className="w-1.5 h-1.5 rounded-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.8)]" />
                                                                            Semana {wIdx + 1}
                                                                        </h4>
                                                                        <div className="space-y-3">
                                                                            {logsForWeek.map((log: any) => (
                                                                                <div key={log.id} className="bg-black/50 p-4 rounded-2xl border border-white/5 shadow-inner">
                                                                                    <div className="flex justify-between items-center mb-3 pb-3 border-b border-white/5">
                                                                                        <div>
                                                                                            <div className="text-xs font-black text-white uppercase tracking-tight">{log.sessionName || 'Sesión General'}</div>
                                                                                            <div className="text-[9px] text-zinc-500 font-bold tracking-widest mt-1">{new Date(log.date).toLocaleDateString()}</div>
                                                                                        </div>
                                                                                    </div>
                                                                                    <div className="space-y-2">
                                                                                        {(log.completedExercises || []).map((ex: any, exIdx: number) => {
                                                                                            const maxWeight = Math.max(...(ex.sets?.map((s: any) => s.weight || 0) || [0]));
                                                                                            return (
                                                                                                <div key={exIdx} className="flex justify-between items-center text-[11px]">
                                                                                                    <span className="text-zinc-400 truncate pr-2 flex-1">{ex.exerciseName || ex.name}</span>
                                                                                                    <div className="flex items-center gap-2">
                                                                                                        <span className="text-zinc-500 font-bold">{ex.sets?.length || 0} sets</span>
                                                                                                        {maxWeight > 0 && (
                                                                                                            <span className="text-emerald-400 font-black bg-emerald-950/50 px-2 py-0.5 rounded shadow-sm">
                                                                                                                {maxWeight}kg max
                                                                                                            </span>
                                                                                                        )}
                                                                                                    </div>
                                                                                                </div>
                                                                                            );
                                                                                        })}
                                                                                    </div>
                                                                                </div>
                                                                            ))}
                                                                        </div>
                                                                    </div>
                                                                );
                                                            })}
                                                        </div>
                                                    </div>
                                                ) : (
                                                    // ------------------------------------------------
                                                    // LISTA DE SESIONES NORMALES (Futuras / Actuales)  
                                                    // ------------------------------------------------
                                                    (() => {
                                                        const selectedWeek = currentWeeks.find(w => w.id === selectedWeekId);
                                                        if (!selectedWeekId || !selectedWeek) return (
                                                            <div className="py-12 text-center border border-dashed border-white/10 rounded-3xl opacity-50">
                                                                <p className="text-[10px] text-zinc-500 font-black uppercase tracking-widest">Selecciona un bloque y semana</p>
                                                            </div>
                                                        );

                                                        const sessionsForWeek = selectedWeek.sessions || [];
                                                        
                                                        // 1. Determinar el número total de días a renderizar
                                                        const maxDayAssigned = sessionsForWeek.reduce((max: number, s: any) => Math.max(max, s.dayOfWeek || 0), 0);
                                                        
                                                        const isStandardWeek = maxDayAssigned <= 6; // JS usa 0 a 6 para los días de la semana
                                                        const startOn = settings?.startWeekOn ?? 1; // 1 = Lunes
                                                        
                                                        // Utilizamos la función utilitaria para reordenar los días según preferencia extrayendo solo el valor numérico
                                                        const daysArray = isStandardWeek 
                                                            ? getOrderedDaysOfWeek(startOn).map(d => d.value) 
                                                            : Array.from({ length: maxDayAssigned + 1 }, (_, i) => i);

                                                        return (
                                                            <div className="space-y-5">
                                                                {daysArray.map(dayNum => {
                                                                    // Agrupamos TODAS las sesiones correspondientes a este número de día
                                                                    const daySessions = sessionsForWeek.filter((s: any) => s.dayOfWeek === dayNum);
                                                                    
                                                                    let dayTitle = '';
                                                                    let daySubtitle = '';
                                                                    let dayAbbrev = '';
                                                        
                                                                    // 2. Lógica para nombrar el día
                                                                    if (isStandardWeek) {
                                                                        // dayNum ahora es directamente el índice nativo de JS (0-6)
                                                                        const daysNames = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
                                                                        const dayName = daysNames[dayNum]; 
                                                                        dayTitle = dayName;
                                                                        dayAbbrev = dayName.substring(0, 3).toUpperCase();
                                                                        daySubtitle = daySessions.length > 0 ? 'Día de Entrenamiento' : 'Descanso / Recuperación';
                                                                    } else {
                                                                        dayTitle = `Día ${dayNum}`;
                                                                        dayAbbrev = `D${dayNum}`;
                                                                        daySubtitle = daySessions.length > 0 ? 'Entrenamiento' : 'Descanso';
                                                                    }

                                                                    return (
                                                                        <div key={`day-group-${dayNum}`} className="bg-zinc-900/30 border border-white/5 rounded-[2rem] p-4 relative overflow-hidden">
                                                                            {/* ENCABEZADO DEL DÍA */}
                                                                            <div className="flex items-center justify-between mb-4">
                                                                                <div className="flex items-center gap-3">
                                                                                    <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-xs font-black border ${daySessions.length > 0 ? 'bg-blue-900/20 border-blue-500/30 text-blue-400 shadow-[0_0_15px_rgba(59,130,246,0.2)]' : 'bg-black border-zinc-800 text-zinc-600'}`}>
                                                                                        {dayAbbrev}
                                                                                    </div>
                                                                                    <div>
                                                                                        <h4 className={`text-sm font-black uppercase tracking-wider ${daySessions.length > 0 ? 'text-white' : 'text-zinc-500'}`}>
                                                                                            {dayTitle}
                                                                                        </h4>
                                                                                        <p className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest">{daySubtitle}</p>
                                                                                    </div>
                                                                                </div>

                                                                                {/* BOTÓN CREAR SESIÓN */}
                                                                                <button 
                                                                                    onClick={() => {
                                                                                        if (selectedBlock && handleAddSession) {
                                                                                            handleAddSession(program.id, selectedBlock.macroIndex, selectedWeek.mesoIndex, selectedWeek.id, dayNum);
                                                                                        }
                                                                                    }}
                                                                                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-black border border-white/10 text-[10px] font-black text-zinc-400 uppercase tracking-widest hover:bg-zinc-800 hover:text-white hover:border-white/30 transition-all shadow-sm"
                                                                                >
                                                                                    <PlusIcon size={14} />
                                                                                    <span className="hidden sm:inline">Crear</span>
                                                                                </button>
                                                                            </div>

                                                                            {/* TARJETAS DE SESIONES ASIGNADAS A ESTE DÍA */}
                                                                            {daySessions.length > 0 ? (
                                                                                <div className="space-y-3">
                                                                                    {daySessions.map((session: any, idx: number) => (
                                                                                        <SessionCard 
                                                                                            key={session.id} 
                                                                                            session={session} 
                                                                                            index={idx} 
                                                                                            dayName={dayTitle}
                                                                                            exerciseList={exerciseList}
                                                                                            onStart={() => {
                                                                                                handleStartWorkout(session, program, undefined, { macroIndex: selectedBlock?.macroIndex || 0, mesoIndex: selectedWeek?.mesoIndex || 0, weekId: selectedWeek?.id || '' });
                                                                                            }}
                                                                                            onEdit={() => onEditSessionClick(session)} 
                                                                                            onDelete={() => {
                                                                                                if (window.confirm('¿Eliminar esta sesión?')) {
                                                                                                    onDeleteSession(session.id, program.id, selectedBlock!.macroIndex, selectedWeek!.mesoIndex, selectedWeek!.id);
                                                                                                }
                                                                                            }}
                                                                                        />
                                                                                    ))}
                                                                                </div>
                                                                            ) : (
                                                                                <button 
                                                                                    onClick={() => {
                                                                                        if (selectedBlock && handleAddSession) {
                                                                                            handleAddSession(program.id, selectedBlock.macroIndex, selectedWeek.mesoIndex, selectedWeek.id, dayNum);
                                                                                        }
                                                                                    }}
                                                                                    className="w-full h-14 rounded-2xl bg-black/50 border border-dashed border-white/10 flex items-center justify-center hover:bg-white/5 hover:border-white/30 transition-all group"
                                                                                >
                                                                                    <PlusIcon size={16} className="text-zinc-600 group-hover:text-white mr-2 transition-colors" />
                                                                                    <span className="text-[10px] font-bold text-zinc-600 group-hover:text-white uppercase tracking-widest transition-colors">Añadir sesión a {dayTitle}</span>
                                                                                </button>
                                                                            )}
                                                                        </div>
                                                                    );
                                                                })}
                                                                
                                                                </div>
                                                        );
                                                    })()
                                                )}
                                            </div>
                                        </div>
                                    ) : (
                                        // =========================================================
                                        // VISTA DE MACROCICLO (JERÁRQUICA Y COMPACTA)        
                                        // =========================================================
                                        <div className="px-2 sm:px-4 py-6 w-full max-w-5xl mx-auto animate-slide-up space-y-8 pb-32">
                                            
                                            {/* BARRA VISUAL (LINEAL O CIRCULAR) DEPENDIENDO DEL TIPO DE PROGRAMA */}
                                            <div className="bg-[#0a0a0a] border border-white/5 rounded-2xl p-5 shadow-lg">
                                                <div className="flex justify-between items-center mb-4">
                                                    <h3 className="text-[10px] font-black text-white uppercase flex items-center gap-2 tracking-widest">
                                                        <CalendarIcon size={14} className="text-white"/> {isCyclic ? 'Roadmap Cíclico' : 'Fechas Claves y Eventos'}
                                                    </h3>
                                                    {(!isCyclic || !(program.events && program.events.length >= 1)) && (
                                                        <button onClick={() => {
                                                            setNewEventData({ id: '', title: '', repeatEveryXCycles: 4, calculatedWeek: 0, type: '1rm_test' });
                                                            setIsEventModalOpen(true);
                                                        }} className="text-[9px] font-black uppercase tracking-widest text-black bg-white px-4 py-2 rounded-lg hover:bg-zinc-200 transition-colors">
                                                            Nuevo {isCyclic ? 'Evento' : 'Hito'}
                                                        </button>
                                                    )}
                                                </div>
                                                
                                                {program.events && program.events.length > 0 ? (
                                                    isCyclic ? (
                                                        // DIAGRAMA CIRCULAR PARA PROGRAMAS SIMPLES
                                                        <div className="relative w-full flex justify-center py-6">
                                                            <div className="relative w-56 h-56 flex items-center justify-center">
                                                                {/* Círculo estático representativo del bucle */}
                                                                <svg className="absolute inset-0 w-full h-full text-zinc-800" viewBox="0 0 100 100">
                                                                    <defs>
                                                                        <marker id="arrowHead" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                                                                            <path d="M 0 0 L 10 5 L 0 10 z" className="fill-zinc-700" />
                                                                        </marker>
                                                                    </defs>
                                                                    {/* Dibujamos un anillo estático con flechas */}
                                                                    <path d="M 50 5 A 45 45 0 0 1 95 50" fill="none" stroke="currentColor" strokeWidth="2" markerEnd="url(#arrowHead)" strokeLinecap="round"/>
                                                                    <path d="M 95 50 A 45 45 0 0 1 50 95" fill="none" stroke="currentColor" strokeWidth="2" markerEnd="url(#arrowHead)" strokeLinecap="round"/>
                                                                    <path d="M 50 95 A 45 45 0 0 1 5 50" fill="none" stroke="currentColor" strokeWidth="2" markerEnd="url(#arrowHead)" strokeLinecap="round"/>
                                                                    <path d="M 5 50 A 45 45 0 0 1 50 5" fill="none" stroke="currentColor" strokeWidth="2" markerEnd="url(#arrowHead)" strokeLinecap="round"/>
                                                                </svg>
                                                                
                                                                {/* Centro */}
                                                                <div className="absolute inset-0 flex items-center justify-center flex-col z-0">
                                                                    <ActivityIcon size={20} className="text-zinc-700 mb-1" />
                                                                    <span className="text-[8px] font-black uppercase tracking-widest text-zinc-600 text-center">Bucle<br/>Eterno</span>
                                                                </div>

                                                                {/* Nodos de Eventos */}
                                                                {(program.events || []).map((ev, i) => {
                                                                    const safeEvents = program.events || [];
                                                                    // Posicionamos los eventos en el círculo
                                                                    const angle = (i * (360 / safeEvents.length) - 90) * (Math.PI / 180);
                                                                    const radius = 90;
                                                                    const x = Math.cos(angle) * radius;
                                                                    const y = Math.sin(angle) * radius;

                                                                    return (
                                                                        <div 
                                                                            key={ev.id || i} 
                                                                            onClick={() => {
                                                                                setNewEventData({ id: ev.id || '', title: ev.title, repeatEveryXCycles: ev.repeatEveryXCycles || 1, calculatedWeek: ev.calculatedWeek || 0, type: ev.type || '1rm_test' });
                                                                                setIsEventModalOpen(true);
                                                                            }}
                                                                            className="absolute z-10 flex flex-col items-center cursor-pointer group"
                                                                            style={{ transform: `translate(${x}px, ${y}px)` }}
                                                                        >
                                                                            <div className="w-12 h-12 rounded-full bg-zinc-950 border border-yellow-500/50 flex items-center justify-center shadow-[0_0_15px_rgba(250,204,21,0.2)] group-hover:scale-110 group-hover:border-yellow-400 transition-all">
                                                                                <div className="text-center">
                                                                                    <span className="block text-[8px] text-zinc-500 font-black uppercase leading-none">Cada</span>
                                                                                    <span className="block text-xs font-black text-yellow-400 leading-none mt-0.5">{ev.repeatEveryXCycles}</span>
                                                                                </div>
                                                                            </div>
                                                                            <div className="absolute top-14 bg-black px-3 py-1.5 rounded-lg border border-white/10 flex flex-col items-center min-w-max shadow-xl pointer-events-none">
                                                                                <span className="text-[9px] font-black text-white uppercase">{ev.title}</span>
                                                                            </div>
                                                                        </div>
                                                                    )
                                                                })}
                                                            </div>
                                                        </div>
                                                    ) : (
                                                        // BARRA LINEAL PARA PROGRAMAS AVANZADOS
                                                        <div className="mt-4">
                                                            <div className="relative w-full h-1.5 bg-zinc-900 rounded-full mb-6 border border-white/5">
                                                                {program.events.map((e, idx) => {
                                                                    const totalW = (program.macrocycles[0]?.blocks?.flatMap(b=>b.mesocycles?.flatMap(m=>m.weeks || []) || [])?.length || 1);
                                                                    const pos = Math.min(100, ((e.calculatedWeek + 1) / Math.max(1, totalW)) * 100);
                                                                    return (
                                                                        <div 
                                                                            key={`marker-${idx}`} 
                                                                            style={{left: `${pos}%`}} 
                                                                            className="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-yellow-400 shadow-[0_0_10px_rgba(250,204,21,0.6)] -translate-x-1/2 cursor-pointer hover:scale-150 transition-transform"
                                                                            onClick={() => {
                                                                                setNewEventData({ id: e.id || '', title: e.title, repeatEveryXCycles: e.repeatEveryXCycles || 1, calculatedWeek: e.calculatedWeek || 0, type: e.type || '1rm_test' });
                                                                                setIsEventModalOpen(true);
                                                                            }}
                                                                        >
                                                                            <span className="absolute -top-6 left-1/2 -translate-x-1/2 text-[8px] font-black text-white bg-black/80 px-2 py-0.5 rounded border border-white/20 whitespace-nowrap opacity-0 hover:opacity-100 transition-opacity">{e.title}</span>
                                                                        </div>
                                                                    );
                                                                })}
                                                            </div>
                                                            <div className="flex gap-3 overflow-x-auto no-scrollbar pb-2">
                                                                {(program.events || []).map((e, idx) => (
                                                                    <div key={idx} className="relative shrink-0 w-44 bg-[#111] border border-white/10 rounded-xl p-4 flex flex-col justify-between group hover:border-white/30 transition-colors">
                                                                        <button 
                                                                            onClick={(ev) => {
                                                                                ev.stopPropagation();
                                                                                if(window.confirm(`¿Eliminar la fecha clave: ${e.title}?`)) {
                                                                                    const updated = JSON.parse(JSON.stringify(program));
                                                                                    updated.events = updated.events.filter((evnt: any) => evnt.id !== e.id);
                                                                                    if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                }
                                                                            }}
                                                                            className="absolute top-3 right-3 text-zinc-600 hover:text-red-500 transition-colors opacity-0 group-hover:opacity-100 p-1 bg-black/50 rounded-full"
                                                                            title="Eliminar Fecha Clave"
                                                                        >
                                                                            <TrashIcon size={12}/>
                                                                        </button>
                                                                        <span className="text-[8px] text-zinc-500 font-black uppercase tracking-widest mb-1">Fecha Clave</span>
                                                                        <span className="text-xs font-bold text-white truncate group-hover:text-blue-400 transition-colors pr-6">{e.title}</span>
                                                                        <span className="text-[9px] text-zinc-400 mt-2 font-bold bg-white/5 self-start px-2 py-1 rounded">
                                                                            Semana {e.calculatedWeek + 1}
                                                                        </span>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        </div>
                                                    )
                                                ) : (
                                                    <div className="w-full text-center py-6 border border-dashed border-white/10 rounded-xl bg-black/20">
                                                        <p className="text-[10px] text-zinc-500 uppercase font-bold tracking-widest">Aún no hay eventos programados.</p>
                                                    </div>
                                                )}
                                            </div>

                                            {(program.macrocycles || []).map((macro, macroIndex) => {
                                                const macroWeeks = (macro.blocks || []).flatMap(b => b.mesocycles.flatMap(me => me.weeks));
                                                const totalMacroWeeks = macroWeeks.length;
                                                
                                                return (
                                                    <div key={macro.id} className="relative space-y-4">
                                                        {/* Cabecera del Macrociclo (Compacta) */}
                                                        <div className="flex justify-between items-center bg-[#0a0a0a] p-4 rounded-2xl border border-white/10 shadow-lg relative z-10">
                                                        <div className="flex items-center gap-3 flex-1">
                                                                <span className="bg-white/10 text-white text-[9px] font-black px-2 py-1 rounded uppercase tracking-widest shrink-0">Macro {macroIndex + 1}</span>
                                                                <input 
                                                                    className="bg-transparent border-none p-0 text-lg md:text-xl font-black text-white uppercase tracking-tight focus:ring-0 w-full"
                                                                    defaultValue={macro.name}
                                                                    onBlur={(e) => {
                                                                        if (e.target.value !== macro.name) {
                                                                            const updated = JSON.parse(JSON.stringify(program));
                                                                            updated.macrocycles[macroIndex].name = e.target.value || `Macrociclo ${macroIndex + 1}`;
                                                                            if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                        }
                                                                    }}
                                                                />
                                                            </div>
                                                            <div className="text-right hidden sm:block pr-2 shrink-0">
                                                                <span className="text-[10px] text-gray-500 font-black uppercase tracking-widest block">{totalMacroWeeks}</span>
                                                                <span className="text-[8px] text-gray-600 font-bold uppercase tracking-widest">Semanas Totales</span>
                                                            </div>
                                                        </div>

                                                        {/* BOTONES DE BLOQUE Y TRANSICIÓN DE ESTRUCTURA */}
                                                        <div className="pl-6 md:pl-10 flex flex-wrap gap-3">
                                                            {isCyclic ? (
                                                                <button 
                                                                    onClick={() => setShowAdvancedTransition(true)}
                                                                    className="py-2.5 px-4 bg-zinc-900 border border-dashed border-white/20 rounded-xl text-[9px] font-black uppercase tracking-widest text-zinc-400 hover:text-white hover:border-white transition-all flex items-center gap-2 shadow-sm"
                                                                >
                                                                    <PlusIcon size={12}/> Añadir Bloque (Convertir a Avanzado)
                                                                </button>
                                                            ) : (
                                                                <>
                                                                    <button 
                                                                        onClick={() => {
                                                                            const updated = JSON.parse(JSON.stringify(program));
                                                                            updated.macrocycles[macroIndex].blocks.push({ id: crypto.randomUUID(), name: 'Nuevo Bloque', mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [] }] });
                                                                            if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                        }}
                                                                        className="py-2.5 px-4 bg-zinc-900 border border-dashed border-white/20 rounded-xl text-[9px] font-black uppercase tracking-widest text-zinc-400 hover:text-white hover:border-white transition-all flex items-center gap-2 shadow-sm"
                                                                    >
                                                                        <PlusIcon size={12}/> Añadir Nuevo Bloque
                                                                    </button>
                                                                    {macroIndex === 0 && (
                                                                        <button 
                                                                            onClick={() => setShowSimpleTransition(true)}
                                                                            className="py-2.5 px-4 bg-black border border-white/10 rounded-xl text-[9px] font-black uppercase tracking-widest text-zinc-500 hover:text-white hover:border-white/30 transition-all flex items-center gap-2 shadow-sm"
                                                                            title="Volver a un formato cíclico de 1 semana"
                                                                        >
                                                                            <ActivityIcon size={12}/> Pasar a Simple
                                                                        </button>
                                                                    )}
                                                                </>
                                                            )}
                                                        </div>
                                                        
                                                        {/* Bloques */}
                                                        <div className="space-y-6 pl-6 md:pl-10 border-l-2 border-white/10 relative pb-4">
                                                            {(macro.blocks || []).map((block, blockIndex) => (
                                                                <div key={block.id} className="relative group">
                                                                    <div className="absolute -left-[29px] md:-left-[45px] top-6 w-3 h-3 rounded-full border-2 border-white/30 bg-[#050505] group-hover:border-blue-500 transition-colors"></div>
                                                                    <div className="bg-[#111] border border-white/10 rounded-2xl overflow-hidden hover:border-white/30 transition-all shadow-md">
                                                                        
                                                                        {/* Header del Bloque */}
                                                                        <div className="p-3 border-b border-white/5 bg-white/[0.02] flex justify-between items-start">
                                                                        <div className="flex-1">
                                                                                <span className="bg-white/10 text-white text-[8px] font-black px-2 py-0.5 rounded uppercase tracking-wider mb-1 inline-block">Bloque {blockIndex + 1}</span>
                                                                                <input 
                                                                                    className="bg-transparent border-none p-0 text-base font-black text-white uppercase tracking-tight focus:ring-0 w-full block"
                                                                                    defaultValue={block.name}
                                                                                    onBlur={(e) => {
                                                                                        if (e.target.value !== block.name) {
                                                                                            const updated = JSON.parse(JSON.stringify(program));
                                                                                            updated.macrocycles[macroIndex].blocks[blockIndex].name = e.target.value || `Bloque ${blockIndex + 1}`;
                                                                                            if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                        }
                                                                                    }}
                                                                                />
                                                                            </div>
                                                                            <button 
                                                                                onClick={() => {
                                                                                    if(window.confirm('¿Eliminar este bloque y todo su contenido?')) {
                                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                                        updated.macrocycles[macroIndex].blocks.splice(blockIndex, 1);
                                                                                        if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                    }
                                                                                }}
                                                                                className="p-1.5 text-zinc-600 hover:text-red-500 transition-colors"
                                                                            >
                                                                                <TrashIcon size={12}/>
                                                                            </button>
                                                                        </div>

                                                                        {/* Contenido del Bloque (Mesociclos/Ciclos) */}
                                                                        <div className="p-3 bg-[#0a0a0a] space-y-5">
                                                                            {(block.mesocycles || []).map((meso, mesoIndex) => (
                                                                                <div key={meso.id} className="space-y-3 bg-black/40 p-3 rounded-xl border border-white/5">
                                                                                    {/* Header del Ciclo / Mesociclo */}
                                                                                    <div className="flex flex-col sm:flex-row sm:items-center gap-3 border-b border-white/5 pb-2">
                                                                                    <div className="flex items-center gap-2 flex-1">
                                                                                            <div className="w-1.5 h-1.5 rounded-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.8)] shrink-0"></div>
                                                                                            <input 
                                                                                                className="bg-transparent border-none p-0 text-[11px] font-black text-white uppercase truncate flex-1 focus:ring-0"
                                                                                                defaultValue={isCyclic ? 'Semanas Cíclicas' : meso.name}
                                                                                                disabled={isCyclic}
                                                                                                onBlur={(e) => {
                                                                                                    if (isCyclic) return;
                                                                                                    if (e.target.value !== meso.name) {
                                                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].name = e.target.value || `Ciclo ${mesoIndex + 1}`;
                                                                                                        if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                                    }
                                                                                                }}
                                                                                            />
                                                                                        </div>
                                                                                        <div className="flex items-center gap-2 shrink-0">
                                                                                            {!isCyclic && (
                                                                                                <select 
                                                                                                    className="bg-black text-[8px] text-gray-400 border border-white/10 rounded md:px-2 md:py-1 p-1 uppercase font-bold outline-none h-6"
                                                                                                    value={meso.goal}
                                                                                                    onChange={(e) => {
                                                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].goal = e.target.value;
                                                                                                        if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                                    }}
                                                                                                >
                                                                                                    {['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'].map(g => <option key={g} value={g}>{g}</option>)}
                                                                                                </select>
                                                                                            )}
                                                                                            <button 
                                                                                                onClick={() => {
                                                                                                    const updated = JSON.parse(JSON.stringify(program));
                                                                                                    const m = updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex];
                                                                                                    if(!m.weeks) m.weeks = [];
                                                                                                    m.weeks.push({ id: crypto.randomUUID(), name: `Semana ${m.weeks.length + 1}`, sessions: [] });
                                                                                                    if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                                }}
                                                                                                className="h-6 px-2 bg-zinc-900 border border-white/10 rounded flex items-center justify-center text-zinc-400 hover:text-white transition-colors"
                                                                                                title="Añadir Semana a este Ciclo"
                                                                                            >
                                                                                                <PlusIcon size={10}/>
                                                                                            </button>
                                                                                        </div>
                                                                                    </div>
                                                                                    
                                                                                    {isCyclic && (
                                                                                        <div className="text-[9px] text-zinc-500 italic px-2 mb-2 leading-relaxed">
                                                                                            ℹ️ Un "Ciclo" contiene semanas que se repiten indeterminadamente. Agrega semanas aquí si quieres que el bucle sea más largo antes de volver a empezar.
                                                                                        </div>
                                                                                    )}
                                                                                    
                                                                                    {/* Carrusel de Semanas (Compacto) */}
                                                                                    <div className="flex gap-2 overflow-x-auto no-scrollbar pb-2 min-w-full">
                                                                                        {(meso.weeks || []).map((week, weekIndex) => {
                                                                                            const weekPattern = Array(7).fill('Descanso');
                                                                                            week.sessions.forEach(s => {
                                                                                                if (s.dayOfWeek !== undefined && s.dayOfWeek >= 0 && s.dayOfWeek < 7) weekPattern[s.dayOfWeek] = s.name;
                                                                                            });
                                                                                            
                                                                                            // Lógica de Eventos
                                                                                            const absIdx = getAbsoluteWeekIndex(program, block.id, week.id);
                                                                                            const hasEvent = checkWeekHasEvent(program, absIdx);

                                                                                            return (
                                                                                                <div key={week.id} className="shrink-0 w-32 h-[5.5rem] relative group/week">
                                                                                                    <div 
                                                                                                        onClick={() => setEditingWeekInfo({ macroIndex, blockIndex, mesoIndex, weekIndex, week, isSimple: isCyclic })} 
                                                                                                        className={`w-full h-full bg-[#161616] border ${hasEvent ? 'border-yellow-500/50 shadow-[0_0_10px_rgba(250,204,21,0.1)]' : 'border-white/5'} rounded-lg p-2 flex flex-col justify-between hover:border-blue-500 hover:bg-[#1a1a1a] transition-all text-left cursor-pointer shadow-sm`}
                                                                                                    >
                                                                                                        <div className="z-10 w-full relative">
                                                                                                            {hasEvent && <div className="absolute -top-1 -right-1 w-2 h-2 bg-yellow-400 rounded-full animate-pulse"></div>}
                                                                                                            <input 
                                                                                                                className={`bg-transparent border-none p-0 text-[9px] font-black uppercase truncate w-[85%] focus:ring-0 ${hasEvent ? 'text-yellow-400' : 'text-gray-400 focus:text-white'}`}
                                                                                                                defaultValue={week.name}
                                                                                                                onClick={(e) => e.stopPropagation()}
                                                                                                                onBlur={(e) => {
                                                                                                                    if (e.target.value !== week.name) {
                                                                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks[weekIndex].name = e.target.value || `Semana ${weekIndex + 1}`;
                                                                                                                        if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                                                    }
                                                                                                                }}
                                                                                                            />
                                                                                                        </div>
                                                                                                        <div className="flex gap-[1px] h-1 w-full mt-auto z-10">
                                                                                                            {weekPattern.map((d, dIdx) => (
                                                                                                                <div key={dIdx} className={`flex-1 rounded-[1px] ${d.toLowerCase() === 'descanso' ? 'bg-white/5' : hasEvent ? 'bg-yellow-400' : 'bg-white group-hover/week:bg-blue-500 transition-colors'}`}></div>
                                                                                                            ))}
                                                                                                        </div>
                                                                                                    </div>
                                                                                                    <button 
                                                                                                        onClick={(e) => {
                                                                                                            e.stopPropagation();
                                                                                                            if(window.confirm('¿Borrar semana?')) {
                                                                                                                const updated = JSON.parse(JSON.stringify(program));
                                                                                                                updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks.splice(weekIndex, 1);
                                                                                                                if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                                            }
                                                                                                        }}
                                                                                                        className="absolute -top-1.5 -right-1.5 bg-red-500 text-white rounded-full p-0.5 opacity-0 group-hover/week:opacity-100 transition-opacity z-20 shadow-md"
                                                                                                    >
                                                                                                        <XIcon size={8}/>
                                                                                                    </button>
                                                                                                </div>
                                                                                            );
                                                                                        })}
                                                                                    </div>
                                                                                </div>
                                                                            ))}
                                                                            
                                                                            {/* Botón Añadir Ciclo (Oculto en Programas Simples) */}
                                                                            {!isCyclic && (
                                                                                <button 
                                                                                    onClick={() => {
                                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles.push({ id: crypto.randomUUID(), name: 'Nuevo Mesociclo', goal: 'Acumulación', weeks: [] });
                                                                                        if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                                    }}
                                                                                    className="w-full py-2.5 bg-zinc-900/50 border border-dashed border-white/10 rounded-xl text-[9px] font-black uppercase text-zinc-500 hover:text-white hover:border-white/30 transition-all flex items-center justify-center gap-2"
                                                                                >
                                                                                    <PlusIcon size={12}/> Añadir Mesociclo
                                                                                </button>
                                                                            )}
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    )}
                                </>
                            )}
                        </div>
                    );
                })()}

                {/* B) PESTAÑA MÉTRICAS */}
                {activeTab === 'metrics' && (
                    <div className="animate-fade-in px-4 pt-6 space-y-6 pb-20 relative">
                        
                        {/* AVISO DE PROGRAMA INACTIVO */}
                        {activeProgramState?.programId !== program.id && (
                            <div className="absolute inset-0 z-50 backdrop-blur-md bg-black/60 rounded-3xl mt-4 flex justify-center items-start pt-20">
                                <div className="flex flex-col items-center text-center p-6 max-w-xs bg-black/40 rounded-3xl border border-white/5 shadow-2xl">
                                    <DumbbellIcon size={48} className="text-zinc-600 mb-4 animate-pulse" />
                                    <h3 className="text-xl font-black text-white uppercase mb-2">Programa Inactivo</h3>
                                    <p className="text-xs text-zinc-400">Inicia este programa desde la portada para comenzar a analizar tu volumen de entrenamiento semana a semana.</p>
                                </div>
                            </div>
                        )}

                        <div className={`${activeProgramState?.programId !== program.id ? 'opacity-20 pointer-events-none' : ''}`}>
                            
                            {/* SELECTOR DE SEMANA */}
                            {currentWeeks.length > 0 && (
                                <div className="mb-6">
                                    <div className="flex flex-col items-center gap-2 relative z-40">
                                        <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest">Semana Analizada</span>
                                        <div className="flex gap-2 overflow-x-auto no-scrollbar items-center justify-center px-4 w-full pb-2">
                                            {currentWeeks.map((week, idx) => (
                                                <button key={week.id} onClick={() => setSelectedWeekId(week.id)} className={`shrink-0 w-10 h-10 rounded-full flex items-center justify-center text-[10px] font-bold border transition-all duration-200 ${week.id === selectedWeekId ? 'bg-blue-600 border-blue-400 text-white shadow-[0_0_15px_rgba(37,99,235,0.5)] scale-110' : 'border-zinc-800 bg-zinc-900/50 text-zinc-500 hover:bg-zinc-800'}`}>
                                                    {idx + 1}
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* FILTROS TIPO ETIQUETA (NUEVO) */}
                            <div className="flex gap-2 overflow-x-auto no-scrollbar pb-4 border-b border-white/5 mb-6">
                                {[
                                    { id: 'todos', label: 'Todos' },
                                    { id: 'volumen', label: 'Fatiga & Volumen' },
                                    { id: 'fuerza', label: 'Fuerza Relativa' },
                                    { id: 'historial', label: 'Historial' }
                                ].map(f => (
                                    <button 
                                        key={f.id} 
                                        onClick={() => setMetricFilter(f.id as any)}
                                        className={`shrink-0 px-4 py-2 rounded-full text-[10px] font-black uppercase tracking-widest transition-colors border ${metricFilter === f.id ? 'bg-white text-black border-white shadow-[0_0_15px_rgba(255,255,255,0.3)]' : 'bg-zinc-900/50 text-zinc-500 border-white/10 hover:text-white'}`}
                                    >
                                        {f.label}
                                    </button>
                                ))}
                            </div>

                            {/* SECCIÓN 1: CAUPOLICÁN Y VOLUMEN */}
                            {(metricFilter === 'todos' || metricFilter === 'volumen') && (
                                <div className="relative pt-2 mb-12 animate-slide-up">
                                    <div className="relative flex flex-col items-center mt-2">
                                        {/* ORBE NEÓN DE ADHERENCIA */}
                                        <div className="absolute top-0 right-2 z-40">
                                            <button onClick={() => setShowAdherenceModal(true)} className="relative w-16 h-16 flex items-center justify-center group hover:scale-105 transition-transform">
                                                <div className="absolute inset-0 bg-black rounded-full shadow-[0_0_20px_rgba(52,211,153,0.25)]"></div>
                                                <svg viewBox="0 0 36 36" className="w-full h-full transform -rotate-90 relative z-10">
                                                    <path className="text-zinc-900" strokeWidth="3" stroke="currentColor" fill="none" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                                                    <path className="text-emerald-400 drop-shadow-[0_0_8px_rgba(52,211,153,0.8)] transition-all duration-1000 ease-out" strokeWidth="3" strokeDasharray={`${totalAdherence}, 100`} strokeLinecap="round" stroke="currentColor" fill="none" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                                                </svg>
                                                <div className="absolute flex flex-col items-center justify-center z-20">
                                                    <span className="text-white text-[12px] font-black leading-none">{totalAdherence}%</span>
                                                    <span className="text-emerald-500 text-[6px] font-bold uppercase tracking-widest mt-0.5">Adh</span>
                                                </div>
                                            </button>
                                        </div>

                                        <CaupolicanBody data={visualizerData as any} isPowerlifting={program.mode === 'powerlifting'} focusedMuscle={focusedMuscle} discomforts={programDiscomforts} onMuscleClick={(muscle, x, y) => setSelectedMusclePos({ muscle, x, y })} />

                                        {programDiscomforts.length > 0 && (
                                            <div className="mt-6 w-full max-w-[280px]">
                                                <h4 className="text-[9px] font-black text-red-400 uppercase tracking-widest mb-3 flex items-center justify-center gap-2">
                                                    <ActivityIcon size={12} /> Foco de Tensión Histórico
                                                </h4>
                                                <div className="flex flex-wrap justify-center gap-2">
                                                    {programDiscomforts.map((disc, idx) => (
                                                        <div key={idx} className="bg-red-950/40 border border-red-500/20 pl-1.5 pr-3 py-1 rounded-full flex items-center gap-2 shadow-sm">
                                                            <div className="w-4 h-4 rounded-full bg-red-900 border border-red-500 flex items-center justify-center shrink-0">
                                                                <div className="w-1.5 h-1.5 rounded-full bg-red-400 animate-pulse" />
                                                            </div>
                                                            <span className="text-xs font-bold text-red-200">{disc.name}</span>
                                                            <span className="text-[9px] bg-red-500/20 text-red-300 px-1.5 py-0.5 rounded-full font-black ml-1">{disc.count}x</span>
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                    <div className="mt-8 relative z-50">
                                        <WorkoutVolumeAnalysis sessions={displayedSessions} history={history} isOnline={isOnline} settings={settings} selectedMuscleInfo={selectedMusclePos} onCloseMuscle={() => setSelectedMusclePos(null)} />
                                    </div>
                                </div>
                            )}

                            {/* SECCIÓN 2: FUERZA RELATIVA */}
                            {(metricFilter === 'todos' || metricFilter === 'fuerza') && (
                                <div className="mb-12 animate-slide-up">
                                    <RelativeStrengthAndBasicsWidget displayedSessions={displayedSessions} />
                                </div>
                            )}
                            
                            {/* SECCIÓN 3: HISTORIAL */}
                            {(metricFilter === 'todos' || metricFilter === 'historial') && (
                                <div className="mb-12 animate-slide-up">
                                    <ExerciseHistoryWidget program={program} history={history} />
                                </div>
                            )}

                            {/* 5. OVERLAY EXPANSIBLE DE ADHERENCIA */}
                            {showAdherenceModal && (
                                <div className="fixed inset-0 z-[120] bg-black/80 backdrop-blur-md flex items-center justify-center p-4 animate-fade-in" onClick={() => setShowAdherenceModal(false)}>
                                    <div className="bg-zinc-950 border border-white/10 w-full max-w-sm rounded-[2rem] p-6 shadow-2xl relative overflow-hidden" onClick={e => e.stopPropagation()}>
                                        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-emerald-400 to-cyan-500 opacity-50" />
                                        
                                        <div className="flex justify-between items-start mb-6">
                                            <div>
                                                <h3 className="text-xl font-black text-white uppercase tracking-tight">Adherencia</h3>
                                                <p className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest mt-1">Avance del Programa</p>
                                            </div>
                                            <button onClick={() => setShowAdherenceModal(false)} className="p-2 bg-zinc-900 rounded-full text-zinc-500 hover:text-white transition-colors">
                                                <XIcon size={16} />
                                            </button>
                                        </div>

                                        <div className="space-y-4 max-h-[50vh] overflow-y-auto pr-2 custom-scrollbar">
                                            {weeklyAdherence.map((week, i) => (
                                                <div key={i} className="flex items-center gap-3">
                                                    <div className="w-16 shrink-0 text-right">
                                                        <span className="text-[10px] font-black text-zinc-400 uppercase tracking-widest">{week.weekName}</span>
                                                    </div>
                                                    <div className="flex-1 h-3 bg-black border border-white/5 rounded-full overflow-hidden relative shadow-inner">
                                                        <div 
                                                            className={`absolute top-0 left-0 h-full rounded-full transition-all duration-1000 ${week.pct === 100 ? 'bg-emerald-400 shadow-[0_0_10px_rgba(52,211,153,0.8)]' : week.pct > 50 ? 'bg-yellow-400' : 'bg-red-500'}`}
                                                            style={{ width: `${week.pct}%` }}
                                                        />
                                                    </div>
                                                    <div className="w-8 shrink-0">
                                                        <span className="text-[10px] font-bold text-white">{week.pct}%</span>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>

                                        <div className="mt-8 p-4 bg-zinc-900/50 rounded-2xl border border-white/5 text-center">
                                            <p className="text-3xl font-black text-white leading-none">{totalAdherence}%</p>
                                            <p className="text-[9px] text-zinc-500 font-bold uppercase tracking-widest mt-1">Cumplimiento Total</p>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {/* OVERLAY INTERACTIVO DE EDICIÓN SEMANAL */}
                {editingWeekInfo && (
                    <InteractiveWeekOverlay 
                        week={editingWeekInfo.week}
                        weekTitle={`Semana ${editingWeekInfo.weekIndex + 1}`}
                        onClose={() => setEditingWeekInfo(null)}
                        onSave={(updatedWeek) => {
                            const updatedProgram = JSON.parse(JSON.stringify(program));
                            
                            if (editingWeekInfo.isSimple) {
                                if (updatedProgram.macrocycles[0]?.blocks[0]?.mesocycles[0]) {
                                    updatedProgram.macrocycles[0].blocks[0].mesocycles[0].weeks[editingWeekInfo.weekIndex] = updatedWeek;
                                }
                            } else {
                                updatedProgram.macrocycles[editingWeekInfo.macroIndex].blocks[editingWeekInfo.blockIndex].mesocycles[editingWeekInfo.mesoIndex].weeks[editingWeekInfo.weekIndex] = updatedWeek;
                            }
                            
                            if (handleUpdateProgram) {
                                handleUpdateProgram(updatedProgram);
                                addToast("Split semanal guardado en el programa.", "success");
                            }
                            setEditingWeekInfo(null);
                        }}
                        onEditSession={(sessionId, intermediateWeek) => {
                            // Guarda el estado intermedio y luego redirige a la edición de la sesión
                            const updatedProgram = JSON.parse(JSON.stringify(program));
                            if (editingWeekInfo.isSimple) {
                                if (updatedProgram.macrocycles[0]?.blocks[0]?.mesocycles[0]) {
                                    updatedProgram.macrocycles[0].blocks[0].mesocycles[0].weeks[editingWeekInfo.weekIndex] = intermediateWeek;
                                }
                            } else {
                                updatedProgram.macrocycles[editingWeekInfo.macroIndex].blocks[editingWeekInfo.blockIndex].mesocycles[editingWeekInfo.mesoIndex].weeks[editingWeekInfo.weekIndex] = intermediateWeek;
                            }
                            if (handleUpdateProgram) handleUpdateProgram(updatedProgram);
                            setEditingWeekInfo(null);
                            
                            setTimeout(() => {
                                if (handleEditSession) {
                                    handleEditSession(
                                        program.id, 
                                        editingWeekInfo.isSimple ? 0 : editingWeekInfo.macroIndex, 
                                        editingWeekInfo.isSimple ? 0 : editingWeekInfo.mesoIndex, 
                                        editingWeekInfo.week.id, 
                                        sessionId
                                    );
                                }
                            }, 100);
                        }}
                    />
                )}

                {/* MODAL DE EVENTOS */}
                {isEventModalOpen && (
                    <div className="fixed inset-0 z-[200] bg-black/90 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-300" onClick={() => setIsEventModalOpen(false)}>
                        <div className="bg-[#0a0a0a] border border-[#222] w-full max-w-sm rounded-3xl p-6 shadow-2xl relative animate-in zoom-in-95 duration-300" onClick={e => e.stopPropagation()}>
                            <button onClick={() => setIsEventModalOpen(false)} className="absolute top-4 right-4 text-zinc-500 hover:text-white transition-colors bg-zinc-900 rounded-full p-1.5"><XIcon size={14}/></button>
                            <h2 className="text-lg font-black text-white uppercase mb-4 tracking-tight flex items-center gap-2"><CalendarIcon size={18} className="text-white"/> Programar Evento</h2>
                            
                            <div className="space-y-4">
                                <div>
                                    <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-1 block">Nombre del Evento</label>
                                    <input type="text" value={newEventData.title} onChange={e => setNewEventData({...newEventData, title: e.target.value})} placeholder="Ej: Prueba 1RM" className="w-full bg-[#111] border border-[#222] rounded-xl p-3 text-white text-xs font-bold focus:border-white focus:ring-0" />
                                </div>
                                {program.structure === 'simple' || (program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1) ? (
                                    <div>
                                        <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-1 block">¿Cada cuántos ciclos ocurrirá?</label>
                                        <div className="flex items-center gap-3">
                                            <input type="text" inputMode="numeric" pattern="[0-9]*" value={newEventData.repeatEveryXCycles} onChange={e => setNewEventData({...newEventData, repeatEveryXCycles: e.target.value === '' ? '' as any : parseInt(e.target.value)})} className="w-24 bg-[#111] border border-[#222] rounded-xl p-3 text-white text-center text-xs font-bold focus:border-white focus:ring-0" />
                                            <span className="text-[10px] text-zinc-400 font-bold uppercase">Ciclos</span>
                                        </div>
                                        <p className="text-[9px] text-zinc-500 mt-2 leading-relaxed">El evento ocurrirá automáticamente cada vez que completes esta cantidad de ciclos (repetir semanas).</p>
                                    </div>
                                ) : (
                                    <div>
                                        <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-1 block">¿En qué semana exacta ocurrirá?</label>
                                        <div className="flex items-center gap-3">
                                            <span className="text-[10px] text-zinc-400 font-bold uppercase">Semana</span>
                                            <input type="text" inputMode="numeric" pattern="[0-9]*" value={newEventData.calculatedWeek === -1 ? '' : newEventData.calculatedWeek + 1} onChange={e => setNewEventData({...newEventData, calculatedWeek: e.target.value === '' ? -1 : (parseInt(e.target.value) || 1) - 1})} className="w-24 bg-[#111] border border-[#222] rounded-xl p-3 text-white text-center text-xs font-bold focus:border-white focus:ring-0" />
                                        </div>
                                    </div>
                                )}
                                <div className="flex gap-2 mt-2">
                                    {newEventData.id && (
                                        <button 
                                            onClick={() => {
                                                if(window.confirm('¿Eliminar este evento?')) {
                                                    const updated = JSON.parse(JSON.stringify(program));
                                                    updated.events = updated.events.filter((e: any) => e.id !== newEventData.id);
                                                    if(handleUpdateProgram) handleUpdateProgram(updated);
                                                    setIsEventModalOpen(false);
                                                }
                                            }}
                                            className="w-12 bg-red-500/10 border border-red-500/30 text-red-500 font-black uppercase tracking-widest text-[10px] py-3 rounded-xl hover:bg-red-500 hover:text-white transition-colors flex items-center justify-center shrink-0"
                                            title="Eliminar Evento"
                                        >
                                            <TrashIcon size={14}/>
                                        </button>
                                    )}
                                    <button onClick={() => {
                                        if(!newEventData.title.trim()) { addToast("Ponle un nombre al evento", "danger"); return; }
                                        const updated = JSON.parse(JSON.stringify(program));
                                        if(!updated.events) updated.events = [];
                                        const isCyclic = program.structure === 'simple' || (program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1);
                                        
                                        const eventPayload = {
                                            id: newEventData.id || crypto.randomUUID(),
                                            title: newEventData.title,
                                            type: newEventData.type,
                                            date: new Date().toISOString(),
                                            calculatedWeek: isCyclic ? 0 : (newEventData.calculatedWeek === -1 ? 0 : newEventData.calculatedWeek),
                                            repeatEveryXCycles: isCyclic ? (parseInt(newEventData.repeatEveryXCycles as any) || 1) : undefined
                                        };

                                        if (newEventData.id) {
                                            const index = updated.events.findIndex((e: any) => e.id === newEventData.id);
                                            if (index !== -1) updated.events[index] = eventPayload;
                                        } else {
                                            updated.events.push(eventPayload);
                                        }

                                        if(handleUpdateProgram) handleUpdateProgram(updated);
                                        setIsEventModalOpen(false);
                                        addToast(newEventData.id ? "Evento actualizado" : "Evento programado exitosamente", "success");
                                    }} className="flex-1 bg-white text-black font-black uppercase tracking-widest text-[10px] py-3 rounded-xl hover:bg-zinc-200 transition-colors shadow-[0_0_15px_rgba(255,255,255,0.2)]">
                                        Guardar Evento
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* MODAL TRANSICIÓN A PROGRAMA AVANZADO (DISEÑO SOBRIO CENTRADO) */}
                {showAdvancedTransition && (
                    <div className="fixed inset-0 z-[200] bg-black/90 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-300" onClick={() => setShowAdvancedTransition(false)}>
                        <div className="bg-[#0a0a0a] border border-[#222] w-full max-w-md rounded-3xl p-8 shadow-2xl relative animate-in zoom-in-95 duration-300" onClick={e => e.stopPropagation()}>
                            <button onClick={() => setShowAdvancedTransition(false)} className="absolute top-5 right-5 text-zinc-500 hover:text-white transition-colors bg-zinc-900 rounded-full p-2"><XIcon size={14}/></button>
                            
                            <h2 className="text-xl font-black text-white uppercase mb-2 flex items-center gap-2"><ActivityIcon className="text-white"/> Transición a Avanzado</h2>
                            <p className="text-[10px] text-zinc-400 mb-8 font-bold leading-relaxed pr-4">
                                Estás a punto de convertir tu bucle cíclico en una <span className="text-white">Periodización por Bloques</span>. Podrás agregar múltiples bloques (Acumulación, Intensificación, etc.) y eventos con fechas clave específicas.
                                <br/><br/>
                                <span className="text-yellow-500">⚠️ Nota:</span> Si tenías eventos cíclicos, se mantendrán pero ahora su lógica pasará a calcularse en base a todo el macrociclo lineal.
                            </p>

                            <div className="space-y-3">
                            <button onClick={() => {
                                    const updated = JSON.parse(JSON.stringify(program));
                                    updated.structure = 'complex';
                                    updated.events = []; // ELIMINAR EVENTOS AL CAMBIAR DE MODO
                                    updated.macrocycles[0].name = "Macrociclo Principal";
                                    updated.macrocycles[0].blocks[0].name = "Bloque de Inicio";
                                    updated.macrocycles[0].blocks.push({ 
                                        id: crypto.randomUUID(), 
                                        name: 'Nuevo Bloque', 
                                        mesocycles: [{ 
                                            id: crypto.randomUUID(), 
                                            name: 'Fase Inicial', 
                                            goal: 'Acumulación', 
                                            weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }] 
                                        }] 
                                    });
                                    if(handleUpdateProgram) handleUpdateProgram(updated);
                                    setShowAdvancedTransition(false);
                                    addToast("Programa actualizado. Los eventos cíclicos se han borrado.", "success");
                                }} className="w-full text-left p-4 rounded-2xl bg-zinc-900/50 border border-white/10 hover:border-blue-500 hover:bg-blue-900/10 transition-all group">
                                    <div className="flex items-center gap-3">
                                        <div className="w-8 h-8 rounded-full bg-blue-500/20 text-blue-400 flex items-center justify-center shrink-0"><PlusIcon size={14}/></div>
                                        <div>
                                            <h4 className="text-xs font-black text-white uppercase tracking-wider group-hover:text-blue-400 transition-colors">Añadir Bloque en Blanco</h4>
                                            <p className="text-[9px] text-zinc-500 mt-1">Crea un bloque vacío listo para que tú lo armes.</p>
                                        </div>
                                    </div>
                                </button>
                                
                                <button onClick={() => {
                                    const updated = JSON.parse(JSON.stringify(program));
                                    updated.structure = 'complex';
                                    updated.events = []; // ELIMINAR EVENTOS AL CAMBIAR DE MODO
                                    updated.macrocycles[0].name = "Macrociclo de Fuerza";
                                    updated.macrocycles[0].blocks[0].name = "Bloque de Acumulación";
                                    updated.macrocycles[0].blocks.push({ 
                                        id: crypto.randomUUID(), name: 'Bloque de Intensificación', 
                                        mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Peaking', goal: 'Intensificación', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }, { id: crypto.randomUUID(), name: 'Semana 2', sessions: [] }] }] 
                                    });
                                    if(handleUpdateProgram) handleUpdateProgram(updated);
                                    setShowAdvancedTransition(false);
                                    addToast("Programa convertido con plantilla de fuerza. Eventos borrados.", "success");
                                }} className="w-full text-left p-4 rounded-2xl bg-zinc-900/50 border border-white/10 hover:border-yellow-500 hover:bg-yellow-900/10 transition-all group opacity-80 hover:opacity-100">
                                    <div className="flex items-center gap-3">
                                        <div className="w-8 h-8 rounded-full bg-yellow-500/20 text-yellow-400 flex items-center justify-center shrink-0"><DumbbellIcon size={14}/></div>
                                        <div>
                                            <h4 className="text-xs font-black text-white uppercase tracking-wider group-hover:text-yellow-400 transition-colors">Usar Plantilla de Fuerza</h4>
                                            <p className="text-[9px] text-zinc-500 mt-1">Añade automáticamente un bloque de intensificación y peaking.</p>
                                        </div>
                                    </div>
                                    </button>
                            </div>
                        </div>
                    </div>
                )}

                {/* MODAL TRANSICIÓN A PROGRAMA SIMPLE (INVERSO) */}
                {showSimpleTransition && (
                    <div className="fixed inset-0 z-[200] bg-black/90 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-300" onClick={() => setShowSimpleTransition(false)}>
                        <div className="bg-[#0a0a0a] border border-[#222] w-full max-w-md max-h-[85vh] flex flex-col rounded-3xl p-8 shadow-2xl relative animate-in zoom-in-95 duration-300" onClick={e => e.stopPropagation()}>
                            <button onClick={() => setShowSimpleTransition(false)} className="absolute top-5 right-5 text-zinc-500 hover:text-white transition-colors bg-zinc-900 rounded-full p-2"><XIcon size={14}/></button>
                            
                            <h2 className="text-xl font-black text-white uppercase mb-2 flex items-center gap-2"><ActivityIcon className="text-white"/> Volver a Simple</h2>
                            <p className="text-[10px] text-zinc-400 mb-6 font-bold leading-relaxed pr-4">
                                Estás a punto de convertir este programa en un <span className="text-white">Bucle Cíclico Simple</span>. ¿Qué semana actual deseas conservar como tu ciclo base? Las demás semanas se eliminarán.
                                <br/><br/>
                                <span className="text-yellow-500">⚠️ Nota:</span> Los eventos y fechas claves se borrarán en la transición.
                            </p>

                            <div className="overflow-y-auto custom-scrollbar space-y-3 pr-2">
                                <button onClick={() => {
                                    const updated = JSON.parse(JSON.stringify(program));
                                    updated.structure = 'simple';
                                    updated.events = []; 
                                    updated.macrocycles = [{ id: crypto.randomUUID(), name: "Macrociclo", blocks: [{ id: crypto.randomUUID(), name: "BLOQUE CÍCLICO", mesocycles: [{ id: crypto.randomUUID(), name: "Ciclo Base", goal: "Custom", weeks: [{ id: crypto.randomUUID(), name: "Semana 1", sessions: [] }] }] }] }];
                                    if(handleUpdateProgram) handleUpdateProgram(updated);
                                    setShowSimpleTransition(false);
                                    addToast("Programa simplificado. Nueva semana en blanco.", "success");
                                }} className="w-full text-left p-4 rounded-2xl bg-zinc-900/50 border border-dashed border-white/20 hover:border-white hover:bg-white/5 transition-all group shrink-0">
                                    <div className="flex items-center gap-3">
                                        <div className="w-8 h-8 rounded-full bg-zinc-800 text-zinc-400 flex items-center justify-center shrink-0"><PlusIcon size={14}/></div>
                                        <div>
                                            <h4 className="text-xs font-black text-white uppercase tracking-wider group-hover:text-zinc-300 transition-colors">Semana en Blanco</h4>
                                            <p className="text-[9px] text-zinc-500 mt-1">Dejar todo en blanco y empezar el ciclo desde cero.</p>
                                        </div>
                                    </div>
                                </button>
                                
                                {/* Mapear y listar todas las semanas existentes del programa avanzado para escoger cuál conservar */}
                                {program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => (b.mesocycles || []).flatMap(me => (me.weeks || []).map(w => ({ ...w, label: `${b.name} - ${w.name}` }))))).map((week, idx) => (
                                    <button key={idx} onClick={() => {
                                        const updated = JSON.parse(JSON.stringify(program));
                                        updated.structure = 'simple';
                                        updated.events = []; 
                                        updated.macrocycles = [{ id: crypto.randomUUID(), name: "Macrociclo", blocks: [{ id: crypto.randomUUID(), name: "BLOQUE CÍCLICO", mesocycles: [{ id: crypto.randomUUID(), name: "Ciclo Base", goal: "Custom", weeks: [{ ...week, id: crypto.randomUUID(), name: "Semana 1" }] }] }] }];
                                        if(handleUpdateProgram) handleUpdateProgram(updated);
                                        setShowSimpleTransition(false);
                                        addToast(`Programa simplificado usando: ${week.label}`, "success");
                                    }} className="w-full text-left p-4 rounded-2xl bg-[#111] border border-white/5 hover:border-blue-500 hover:bg-blue-900/10 transition-all group shrink-0">
                                        <div className="flex items-center gap-3">
                                            <div className="w-8 h-8 rounded-full bg-blue-500/20 text-blue-400 flex items-center justify-center shrink-0"><DumbbellIcon size={14}/></div>
                                            <div className="flex-1 min-w-0">
                                                <h4 className="text-xs font-black text-white uppercase tracking-wider group-hover:text-blue-400 transition-colors truncate">Conservar: {week.label}</h4>
                                                <p className="text-[9px] text-zinc-500 mt-1">{(week.sessions || []).length} sesiones programadas</p>
                                            </div>
                                        </div>
                                        </button>
                                ))}
                            </div>
                        </div>
                    </div>
                )}
            </div>

            {/* --- TOUR DE ONBOARDING --- */}
            {tourStep > 0 && (
                <div className="fixed inset-0 z-[9999] pointer-events-auto flex items-center justify-center bg-black/80 backdrop-blur-md animate-fade-in px-4">
                    <div className="bg-zinc-950 border border-white/10 rounded-[2rem] p-6 max-w-sm w-full shadow-[0_0_50px_rgba(0,0,0,0.8)] relative text-center overflow-hidden">
                        <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-blue-500 to-cyan-400"></div>
                        
                        <div className="w-20 h-20 mx-auto bg-blue-900/20 rounded-full flex items-center justify-center mb-6 border border-blue-500/30 relative">
                            {tourStep === 1 && <DumbbellIcon size={36} className="text-blue-400 animate-bounce-short" />}
                            {tourStep === 2 && <CalendarIcon size={36} className="text-blue-400 animate-bounce-short" />}
                            {tourStep === 3 && <ActivityIcon size={36} className="text-blue-400 animate-bounce-short" />}
                        </div>
                        
                        <h3 className="text-xl font-black text-white uppercase tracking-tight mb-3">
                            {tourStep === 1 ? 'Bienvenido a tu Programa' : tourStep === 2 ? 'Gestión del Roadmap' : 'Métricas y Progreso'}
                        </h3>
                        
                        <p className="text-xs text-zinc-400 leading-relaxed mb-8 font-medium h-[4.5rem]">
                            {tourStep === 1 ? 'Este es el centro de mando de tu entrenamiento. Aquí podrás ver, iniciar y editar todas las sesiones programadas del día a día.' : 
                             tourStep === 2 ? 'Usa la vista "Macrociclo" para editar la estructura y agregar hitos. La vista "Semanal" te permite navegar ágilmente por tus bloques y semanas.' : 
                             'Revisa el impacto de fatiga corporal, el volumen acumulado y tus récords históricos en la pestaña "Métricas" arriba.'}
                        </p>
                        
                        <div className="flex justify-between items-center w-full gap-4 border-t border-white/5 pt-5">
                            <div className="flex gap-2">
                                {[1,2,3].map(step => (
                                    <div key={step} className={`w-2.5 h-2.5 rounded-full transition-colors ${tourStep === step ? 'bg-blue-500' : 'bg-white/10'}`}></div>
                                ))}
                            </div>
                            <button 
                                onClick={() => {
                                    if (tourStep < 3) {
                                        setTourStep(prev => prev + 1);
                                    } else {
                                        setTourStep(0);
                                        localStorage.setItem(`kpkn_tour_seen_${program.id}`, 'true');
                                    }
                                }}
                                className="bg-white text-black px-6 py-3 rounded-full text-[10px] font-black uppercase tracking-widest hover:scale-105 transition-transform shadow-[0_0_15px_rgba(255,255,255,0.2)]"
                            >
                                {tourStep < 3 ? 'Siguiente' : '¡A Entrenar!'}
                            </button>
                        </div>
                        
                        <button 
                            onClick={() => {
                                setTourStep(0);
                                localStorage.setItem(`kpkn_tour_seen_${program.id}`, 'true');
                            }}
                            className="absolute top-4 right-4 p-2 text-zinc-600 hover:text-white transition-colors bg-zinc-900 rounded-full"
                        >
                            <XIcon size={14} />
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ProgramDetail;