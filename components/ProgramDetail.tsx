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
    dayName?: string;
    exerciseList?: ExerciseMuscleInfo[];
}> = ({ session, index, onStart, onEdit, dayName, exerciseList = [] }) => {
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
                            {dayName ? dayName.substring(0, 3).toUpperCase() : index + 1}
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
                            // CRÍTICO: Aseguramos que onEdit apunte al SessionEditor correctamente
                            onEdit(); 
                        }}
                        className="w-12 bg-black/50 border border-white/10 rounded-xl flex items-center justify-center text-zinc-400 hover:text-white hover:border-white/30 transition-colors"
                        title="Editar Sesión"
                    >
                        <EditIcon size={16} />
                    </button>
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

                    <div className="space-y-3">
                        {exercisesToDisplay.length > 0 ? (
                            exercisesToDisplay.map((exercise: any, idx: number) => (
                                <div key={idx}>
                                    <div className="flex justify-between items-center mb-1">
                                        <span className="text-xs font-bold text-zinc-300">{idx + 1}. {exercise.name}</span>
                                        <span className="text-[9px] text-zinc-600 bg-black/50 px-1.5 py-0.5 rounded">{(exercise.sets || []).length} sets</span>
                                    </div>
                                    <div className="pl-3 border-l border-white/10">
                                        {(exercise.sets || []).slice(0, 1).map((set: any, sIdx: number) => (
                                            <p key={sIdx} className="text-[9px] text-zinc-500">
                                                {set.targetReps} reps {set.targetRPE ? `@ RPE ${set.targetRPE}` : ''}
                                                {(exercise.sets || []).length > 1 && <span className="italic ml-1">(+ {(exercise.sets || []).length - 1} más)</span>}
                                            </p>
                                        ))}
                                    </div>
                                </div>
                            ))
                        ) : (
                            <div className="text-center py-4">
                                <p className="text-[10px] text-zinc-500 italic">No hay ejercicios agregados a esta sesión.</p>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

const ProgramDetail: React.FC<ProgramDetailProps> = ({ program, onStartWorkout, onEdit, isActive }) => {
    // Agregamos handleUpdateProgram, addToast y handleStartWorkout
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

    // --- ESTADO ---
    const [showAdvancedTransition, setShowAdvancedTransition] = useState(false);
    const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null);
    const [selectedWeekId, setSelectedWeekId] = useState<string | null>(null);
    const [expandedRoadmapBlocks, setExpandedRoadmapBlocks] = useState<string[]>([]); // Para plegar/desplegar roadmap sin deseleccionar
    const [isEventModalOpen, setIsEventModalOpen] = useState(false);
    const [newEventData, setNewEventData] = useState({ title: '', repeatEveryXCycles: 1, calculatedWeek: 0, type: 'Test 1RM' });
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

    // 2. Preparar datos de Semanas (¡ESTO ERA LO QUE FALTABA!)
    const currentWeeks = useMemo(() => {
        if (!selectedBlockId) return [];
        const block = roadmapBlocks.find(b => b.id === selectedBlockId);
        if (!block) return [];
        
        return block.mesocycles.flatMap((meso, mesoIdx) => 
            meso.weeks.map(w => ({ 
                ...w, 
                mesoGoal: meso.goal,
                mesoIndex: mesoIdx, 
            }))
        );
    }, [selectedBlockId, roadmapBlocks]);

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

    // Inicializar selección
    useEffect(() => {
        if (roadmapBlocks.length > 0 && !selectedBlockId) setSelectedBlockId(roadmapBlocks[0].id);
    }, [roadmapBlocks]);

    useEffect(() => {
        if (currentWeeks.length > 0) setSelectedWeekId(currentWeeks[0].id);
    }, [selectedBlockId, currentWeeks]); 

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
                <div className="absolute bottom-0 left-0 w-full p-6 z-20 space-y-2">
                    <div className="flex items-center gap-2">
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
                    <h1 className="text-4xl font-black text-white uppercase tracking-tighter leading-none text-shadow-lg">
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
                                // VISTA CÍCLICA (Futurista para programas de 1 semana)      
                                // =========================================================
                                <div className="px-4 py-12 flex flex-col items-center">
                                    <div className="relative mb-8">
                                        {/* Anillos concéntricos animados de fondo */}
                                        <div className="absolute inset-[-10px] border-2 border-blue-500/20 rounded-full animate-ping" style={{ animationDuration: '3s' }} />
                                        <div className="absolute inset-2 border-2 border-purple-500/20 rounded-full animate-ping" style={{ animationDuration: '2s', animationDelay: '0.5s' }} />
                                        
                                        {/* Botón Cíclico Interactivo */}
                                        <button 
                                            onClick={() => setShowCyclicHistory(!showCyclicHistory)}
                                            className="relative w-28 h-28 rounded-full bg-gradient-to-tr from-zinc-950 to-zinc-900 border border-white/5 shadow-[0_0_50px_rgba(59,130,246,0.15)] flex flex-col items-center justify-center group hover:scale-105 transition-all z-10"
                                        >
                                            <svg className="text-blue-400 group-hover:rotate-180 transition-transform duration-1000 mb-1" width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                                                <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/>
                                            </svg>
                                            <span className="text-[9px] font-black text-white uppercase tracking-widest mt-1">
                                                {showCyclicHistory ? 'Ver Plan' : 'Historial'}
                                            </span>
                                        </button>
                                    </div>
                                    
                                    <div className="text-center mb-10">
                                        <h3 className="text-xl font-black text-white uppercase tracking-tight">Bucle de Entrenamiento</h3>
                                        <p className="text-[10px] text-zinc-500 font-bold uppercase tracking-widest mt-2">Estructura Cíclica Activa</p>
                                    </div>

                                    <div className="w-full max-w-md">
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
                                                <div className="flex items-center gap-3 mb-6">
                                                    <div className="h-px bg-white/10 flex-1"></div>
                                                    <h4 className="text-[9px] font-black text-zinc-500 uppercase tracking-widest">Plan de Acción</h4>
                                                    <div className="h-px bg-white/10 flex-1"></div>
                                                </div>
                                                
                                                {(() => {
                                                    const cyclicWeek = currentWeeks[0];
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
                                                                                        handleAddSession(program.id, selectedBlock.macroIndex, cyclicWeek.mesoIndex, cyclicWeek.id);
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
                                                                                    />
                                                                                ))}
                                                                            </div>
                                                                        ) : (
                                                                            <button 
                                                                                onClick={() => {
                                                                                    if (selectedBlock && handleAddSession) {
                                                                                        handleAddSession(program.id, selectedBlock.macroIndex, cyclicWeek.mesoIndex, cyclicWeek.id);
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

                                                            <button 
                                                                onClick={() => {
                                                                    if (selectedBlock && cyclicWeek && handleAddSession) {
                                                                        handleAddSession(program.id, selectedBlock.macroIndex, cyclicWeek.mesoIndex, cyclicWeek.id);
                                                                    }
                                                                }}
                                                                className="w-full py-4 mt-4 bg-zinc-900 border border-white/5 rounded-2xl flex items-center justify-center gap-2 text-[10px] font-black uppercase tracking-widest text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors shadow-lg"
                                                            >
                                                                <PlusIcon size={16} /> Añadir Nueva Sesión
                                                            </button>
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
                                                <div className="bg-zinc-950 border border-white/10 px-4 py-2 rounded-2xl flex items-center gap-2 shadow-sm">
                                                    <span className="text-[10px] font-bold text-white uppercase tracking-wider">{selectedBlock?.name || `Bloque ${selectedBlockIndex + 1}`}</span>
                                                    <span className="text-zinc-700 font-black">/</span>
                                                    <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider">Semana {selectedWeekIndex}</span>
                                                </div>
                                            </div>

                                            {/* ROADMAP INTEGRADO (Acordeón Horizontal Minimalista) */}
                                            <div className="relative border-b border-white/5 bg-black">
                                                <div className="w-full overflow-x-auto no-scrollbar flex items-center px-6 pt-6 pb-8 relative z-10 scroll-smooth">
                                                    {roadmapBlocks.map((block, idx) => {
                                                        const isSelected = block.id === selectedBlockId;
                                                        const isCurrent = block.id === activeBlockId;

                                                        return (
                                                            <div key={block.id} className="flex items-center shrink-0">
                                                                {/* 1. BOTÓN DEL BLOQUE */}
                                                                <div className="flex items-center">
                                                                    <button 
                                                                        onClick={(e) => {
                                                                            // Solo selecciona el bloque, NO lo pliega.
                                                                            setSelectedBlockId(block.id);
                                                                            if (!expandedRoadmapBlocks.includes(block.id)) {
                                                                                setExpandedRoadmapBlocks(prev => [...prev, block.id]);
                                                                            }
                                                                            const target = e.currentTarget;
                                                                            setTimeout(() => {
                                                                                target.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
                                                                            }, 250); 
                                                                        }}
                                                                        className="group flex flex-col items-center justify-center flex-shrink-0 focus:outline-none relative"
                                                                    >
                                                                        <div className={`relative w-12 h-12 rounded-full flex items-center justify-center transition-all duration-300 z-20 font-black
                                                                            ${isCurrent 
                                                                                ? 'bg-white text-black shadow-[0_0_15px_rgba(255,255,255,0.4)] scale-110' 
                                                                                : isSelected 
                                                                                    ? 'bg-white text-black shadow-[0_0_15px_rgba(255,255,255,0.4)] scale-110' 
                                                                                    : 'bg-zinc-900 text-zinc-400 border border-white/10 hover:bg-zinc-800 hover:text-white shadow-none' 
                                                                            }`}
                                                                        >
                                                                            <span className="text-base">{idx + 1}</span>
                                                                        </div>
                                                                    </button>
                                                                    
                                                                    {/* BOTÓN CHEVRON INDEPENDIENTE PARA PLEGAR/DESPLEGAR EL TIMELINE */}
                                                                    <button 
                                                                        onClick={(e) => {
                                                                            e.stopPropagation();
                                                                            setExpandedRoadmapBlocks(prev => 
                                                                                prev.includes(block.id) ? prev.filter(id => id !== block.id) : [...prev, block.id]
                                                                            );
                                                                        }}
                                                                        className="w-5 h-8 flex items-center justify-center text-zinc-600 hover:text-white transition-colors ml-1"
                                                                    >
                                                                        {expandedRoadmapBlocks.includes(block.id) || (isSelected && !expandedRoadmapBlocks.includes(block.id) && expandedRoadmapBlocks.push(block.id)) ? <ChevronDownIcon size={12} className="rotate-90"/> : <ChevronDownIcon size={12} className="-rotate-90"/>}
                                                                    </button>
                                                                </div>

                                                                {/* 2. EFECTO ACORDEÓN DE SEMANAS (Plano y monocromático) */}
                                                                <div 
                                                                    className={`flex items-center overflow-hidden transition-all duration-500 ease-[cubic-bezier(0.25,1,0.5,1)] 
                                                                        ${expandedRoadmapBlocks.includes(block.id) ? 'max-w-[1000px] opacity-100 ml-1' : 'max-w-0 opacity-0 ml-0'}`}
                                                                >
                                                                    <div className="w-3 h-[2px] bg-zinc-800 shrink-0 mr-3" />
                                                                    <div className="flex items-center gap-2 bg-zinc-950 p-1.5 rounded-full border border-white/5">
                                                                    {currentWeeks.map((week, wIdx) => {
                                                                            const isWeekSelected = week.id === selectedWeekId;
                                                                            
                                                                            // LÓGICA DE EVENTOS INTEGRADA: 1. Fechas clave específicas | 2. Eventos cíclicos (cada X ciclos)
                                                                            const hasEvent = (program.events || []).some(e => 
                                                                                e.calculatedWeek === wIdx || 
                                                                                (e.repeatEveryXCycles && (wIdx + 1) % (e.repeatEveryXCycles * currentWeeks.length) === 0)
                                                                            );
                                                                            
                                                                            return (
                                                                                <button
                                                                                    key={week.id}
                                                                                    onClick={() => setSelectedWeekId(week.id)}
                                                                                    className={`shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-[10px] font-black transition-all duration-300 relative
                                                                                        ${isWeekSelected 
                                                                                            ? (hasEvent ? 'bg-yellow-400 text-black scale-110 shadow-[0_0_20px_rgba(250,204,21,0.6)]' : 'bg-white text-black scale-110 shadow-sm') 
                                                                                            : (hasEvent ? 'bg-yellow-400/20 text-yellow-500 border border-yellow-400/50 hover:bg-yellow-400 hover:text-black animate-pulse-slow' : 'bg-transparent text-zinc-500 hover:text-white hover:bg-zinc-800')
                                                                                        }`}
                                                                                    title={hasEvent ? 'Día D / Fecha Clave' : ''}
                                                                                >
                                                                                    {hasEvent && !isWeekSelected && <div className="absolute -top-1 -right-1 w-2.5 h-2.5 bg-yellow-400 rounded-full animate-ping"></div>}
                                                                                    {wIdx + 1}
                                                                                </button>
                                                                            );
                                                                        })}
                                                                    </div>
                                                                </div>

                                                                {/* 3. LÍNEA CONECTORA */}
                                                                {idx < roadmapBlocks.length - 1 && (
                                                                    <div className="w-8 h-[2px] bg-zinc-800/80 shrink-0 mx-3" />
                                                                )}
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                                <div className="absolute top-0 bottom-0 left-0 w-8 bg-gradient-to-r from-black to-transparent pointer-events-none z-30" />
                                                <div className="absolute top-0 bottom-0 right-0 w-8 bg-gradient-to-l from-black to-transparent pointer-events-none z-30" />
                                            </div>

                                            {/* CONTENIDO DEL BLOQUE */}
                                            <div className="px-4 pb-10 pt-6 space-y-3">
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
                                                                                            handleAddSession(program.id, selectedBlock.macroIndex, selectedWeek.mesoIndex, selectedWeek.id);
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
                                                                                            dayName={dayTitle} // <-- Esto hace que aparezca el nombre del día en la tarjeta
                                                                                            exerciseList={exerciseList}
                                                                                            onStart={() => {
                                                                                                handleStartWorkout(session, program, undefined, { macroIndex: selectedBlock?.macroIndex || 0, mesoIndex: selectedWeek?.mesoIndex || 0, weekId: selectedWeek?.id || '' });
                                                                                            }}
                                                                                            onEdit={() => onEditSessionClick(session)} 
                                                                                        />
                                                                                    ))}
                                                                                </div>
                                                                            ) : (
                                                                                // ESTADO VACÍO CON BOTÓN PARA AÑADIR SESIÓN AL DÍA
                                                                                <button 
                                                                                    onClick={() => {
                                                                                        if (selectedBlock && handleAddSession) {
                                                                                            handleAddSession(program.id, selectedBlock.macroIndex, selectedWeek.mesoIndex, selectedWeek.id);
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
                                                                
                                                                {/* BOTÓN GENERAL PARA AÑADIR SESIÓN EXTRA */}
                                                                <button 
                                                                    onClick={() => {
                                                                        const selectedWeek = currentWeeks.find(w => w.id === selectedWeekId);
                                                                        if (selectedBlock && selectedWeek && handleAddSession) {
                                                                            handleAddSession(program.id, selectedBlock.macroIndex, selectedWeek.mesoIndex, selectedWeek.id);
                                                                        }
                                                                    }}
                                                                    className="w-full py-4 mt-4 bg-zinc-900 border border-white/5 rounded-2xl flex items-center justify-center gap-2 text-[10px] font-black uppercase tracking-widest text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors shadow-lg"
                                                                >
                                                                    <PlusIcon size={16} /> Añadir Nueva Sesión
                                                                </button>
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
                                            
                                            {/* BARRA DE PROGRESO DE EVENTOS GLOBALES */}
                                            {program.events && program.events.length > 0 && (
                                                <div className="relative w-full h-1.5 bg-zinc-900 rounded-full mb-2 border border-white/5">
                                                    {program.events.map((e, idx) => {
                                                        const totalW = isCyclic ? (e.repeatEveryXCycles || 1) * ((program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.length) || 1) : (program.macrocycles[0]?.blocks?.flatMap(b=>b.mesocycles?.flatMap(m=>m.weeks || []) || [])?.length || 1);
                                                        const pos = isCyclic ? 100 : Math.min(100, ((e.calculatedWeek + 1) / Math.max(1, totalW)) * 100);
                                                        return (
                                                            <div key={`marker-${idx}`} style={{left: `${isCyclic ? 100 : pos}%`}} className={`absolute top-1/2 -translate-y-1/2 w-2.5 h-2.5 rounded-full bg-white shadow-[0_0_10px_white] ${isCyclic ? 'right-0 -translate-x-full' : '-translate-x-1/2'}`}></div>
                                                        );
                                                    })}
                                                </div>
                                            )}

                                            {/* PANEL DE FECHAS CLAVES Y EVENTOS */}
                                            <div className="bg-[#0a0a0a] border border-white/5 rounded-2xl p-5 shadow-lg">
                                                <div className="flex justify-between items-center mb-4">
                                                    <h3 className="text-[10px] font-black text-white uppercase flex items-center gap-2 tracking-widest">
                                                        <CalendarIcon size={14} className="text-white"/> Hoja de Ruta (Eventos)
                                                    </h3>
                                                    <button onClick={() => setIsEventModalOpen(true)} className="text-[9px] font-black uppercase tracking-widest text-black bg-white px-4 py-2 rounded-lg hover:bg-zinc-200 transition-colors">
                                                        Nuevo Evento
                                                    </button>
                                                </div>
                                                <div className="flex gap-3 overflow-x-auto no-scrollbar pb-2">
                                                    {program.events && program.events.length > 0 ? (
                                                        program.events.map((e, idx) => (
                                                            <div key={idx} className="shrink-0 w-44 bg-[#111] border border-white/10 rounded-xl p-4 flex flex-col justify-between group">
                                                                <span className="text-[8px] text-zinc-500 font-black uppercase tracking-widest mb-1">{e.repeatEveryXCycles ? 'Evento Cíclico' : 'Fecha Clave'}</span>
                                                                <span className="text-xs font-bold text-white truncate group-hover:text-blue-400 transition-colors">{e.title}</span>
                                                                <span className="text-[9px] text-zinc-400 mt-2 font-bold bg-white/5 self-start px-2 py-1 rounded">
                                                                    {e.repeatEveryXCycles ? `Cada ${e.repeatEveryXCycles} ciclos` : `Semana ${e.calculatedWeek + 1}`}
                                                                </span>
                                                            </div>
                                                        ))
                                                    ) : (
                                                        <div className="w-full text-center py-6 border border-dashed border-white/10 rounded-xl bg-black/20">
                                                            <p className="text-[10px] text-zinc-500 uppercase font-bold tracking-widest">Aún no hay eventos programados.</p>
                                                        </div>
                                                    )}
                                                </div>
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
                                                                    value={macro.name}
                                                                    onChange={(e) => {
                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                        updated.macrocycles[macroIndex].name = e.target.value;
                                                                        if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                    }}
                                                                />
                                                            </div>
                                                            <div className="text-right hidden sm:block pr-2 shrink-0">
                                                                <span className="text-[10px] text-gray-500 font-black uppercase tracking-widest block">{totalMacroWeeks}</span>
                                                                <span className="text-[8px] text-gray-600 font-bold uppercase tracking-widest">Semanas Totales</span>
                                                            </div>
                                                        </div>

                                                        {/* BOTÓN AÑADIR BLOQUE (Movido arriba para mejor visibilidad y evitar problemas de scroll) */}
                                                        <div className="pl-6 md:pl-10">
                                                            <button 
                                                                onClick={() => {
                                                                    if (isCyclic) {
                                                                        setShowAdvancedTransition(true);
                                                                    } else {
                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                        updated.macrocycles[macroIndex].blocks.push({ id: crypto.randomUUID(), name: 'Nuevo Bloque', mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [] }] });
                                                                        if(handleUpdateProgram) handleUpdateProgram(updated);
                                                                    }
                                                                }}
                                                                className="py-2.5 px-4 bg-zinc-900 border border-dashed border-white/20 rounded-xl text-[9px] font-black uppercase tracking-widest text-zinc-400 hover:text-white hover:border-white transition-all flex items-center gap-2 shadow-sm"
                                                            >
                                                                <PlusIcon size={12}/> {isCyclic ? 'Añadir Bloque (Convertir a Avanzado)' : 'Añadir Nuevo Bloque'}
                                                            </button>
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
                                                                                    value={block.name}
                                                                                    onChange={(e) => {
                                                                                        const updated = JSON.parse(JSON.stringify(program));
                                                                                        updated.macrocycles[macroIndex].blocks[blockIndex].name = e.target.value;
                                                                                        if(handleUpdateProgram) handleUpdateProgram(updated);
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
                                                                                                value={isCyclic ? 'Semanas Cíclicas' : meso.name}
                                                                                                disabled={isCyclic}
                                                                                                onChange={(e) => {
                                                                                                    if (isCyclic) return;
                                                                                                    const updated = JSON.parse(JSON.stringify(program));
                                                                                                    updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].name = e.target.value;
                                                                                                    if(handleUpdateProgram) handleUpdateProgram(updated);
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
                                                                                            const hasEvent = (program.events || []).some(e => 
                                                                                                e.calculatedWeek === weekIndex || 
                                                                                                (e.repeatEveryXCycles && (weekIndex + 1) % (e.repeatEveryXCycles * meso.weeks.length) === 0)
                                                                                            );

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
                                                                                                                value={week.name}
                                                                                                                onClick={(e) => e.stopPropagation()}
                                                                                                                onChange={(e) => {
                                                                                                                    const updated = JSON.parse(JSON.stringify(program));
                                                                                                                    updated.macrocycles[macroIndex].blocks[blockIndex].mesocycles[mesoIndex].weeks[weekIndex].name = e.target.value;
                                                                                                                    if(handleUpdateProgram) handleUpdateProgram(updated);
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
                                            <input type="number" min="1" value={newEventData.repeatEveryXCycles} onChange={e => setNewEventData({...newEventData, repeatEveryXCycles: parseInt(e.target.value) || 1})} className="w-24 bg-[#111] border border-[#222] rounded-xl p-3 text-white text-center text-xs font-bold focus:border-white focus:ring-0" />
                                            <span className="text-[10px] text-zinc-400 font-bold uppercase">Ciclos</span>
                                        </div>
                                        <p className="text-[9px] text-zinc-500 mt-2 leading-relaxed">El evento ocurrirá automáticamente cada vez que completes esta cantidad de ciclos (repetir semanas).</p>
                                    </div>
                                ) : (
                                    <div>
                                        <label className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-1 block">¿En qué semana exacta ocurrirá?</label>
                                        <div className="flex items-center gap-3">
                                            <span className="text-[10px] text-zinc-400 font-bold uppercase">Semana</span>
                                            <input type="number" min="1" value={newEventData.calculatedWeek + 1} onChange={e => setNewEventData({...newEventData, calculatedWeek: (parseInt(e.target.value) || 1) - 1})} className="w-24 bg-[#111] border border-[#222] rounded-xl p-3 text-white text-center text-xs font-bold focus:border-white focus:ring-0" />
                                        </div>
                                    </div>
                                )}
                                <button onClick={() => {
                                    if(!newEventData.title.trim()) { addToast("Ponle un nombre al evento", "danger"); return; }
                                    const updated = JSON.parse(JSON.stringify(program));
                                    if(!updated.events) updated.events = [];
                                    const isCyclic = program.structure === 'simple' || (program.macrocycles.length === 1 && (program.macrocycles[0].blocks || []).length <= 1);
                                    updated.events.push({
                                        id: crypto.randomUUID(),
                                        title: newEventData.title,
                                        type: newEventData.type,
                                        date: new Date().toISOString(),
                                        calculatedWeek: isCyclic ? 0 : newEventData.calculatedWeek,
                                        repeatEveryXCycles: isCyclic ? newEventData.repeatEveryXCycles : undefined
                                    });
                                    if(handleUpdateProgram) handleUpdateProgram(updated);
                                    setIsEventModalOpen(false);
                                    addToast("Evento programado exitosamente", "success");
                                }} className="w-full bg-white text-black font-black uppercase tracking-widest text-[10px] py-3 rounded-xl mt-2 hover:bg-zinc-200 transition-colors shadow-[0_0_15px_rgba(255,255,255,0.2)]">
                                    Guardar Evento
                                </button>
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
                                    updated.macrocycles[0].name = "Macrociclo Principal";
                                    updated.macrocycles[0].blocks[0].name = "Bloque de Inicio";
                                    updated.macrocycles[0].blocks.push({ id: crypto.randomUUID(), name: 'Nuevo Bloque', mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Inicial', goal: 'Acumulación', weeks: [] }] });
                                    if(handleUpdateProgram) handleUpdateProgram(updated);
                                    setShowAdvancedTransition(false);
                                    addToast("Programa actualizado a Periodización Compleja", "success");
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
                                    updated.macrocycles[0].name = "Macrociclo de Fuerza";
                                    updated.macrocycles[0].blocks[0].name = "Bloque de Acumulación";
                                    updated.macrocycles[0].blocks.push({ 
                                        id: crypto.randomUUID(), name: 'Bloque de Intensificación', 
                                        mesocycles: [{ id: crypto.randomUUID(), name: 'Fase Peaking', goal: 'Intensificación', weeks: [{ id: crypto.randomUUID(), name: 'Semana 1', sessions: [] }, { id: crypto.randomUUID(), name: 'Semana 2', sessions: [] }] }] 
                                    });
                                    if(handleUpdateProgram) handleUpdateProgram(updated);
                                    setShowAdvancedTransition(false);
                                    addToast("Programa convertido con plantilla de fuerza", "success");
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
            </div>
        </div>
    );
};

export default ProgramDetail;