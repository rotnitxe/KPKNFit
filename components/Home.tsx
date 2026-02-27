// components/Home.tsx
// Vista Dios resumida: Programa + Plan + Batería (estilo NERD) con drag-and-drop

import React, { useMemo, useState } from 'react';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';
import { Program, Session, SleepLog, View, ProgramWeek, WorkoutLog } from '../types';
import Button from './ui/Button';
import { PlusIcon, TargetIcon } from './icons';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { CaupolicanIcon } from './CaupolicanIcon';
import {
    SessionTodayCard,
    BatteryHeroSection,
    MiniNutritionWidget,
    MiniProgramWidget,
    TopExercisesWidget,
    ReadinessWidget,
    StreakWidget,
    QuickLogWidget,
    SetupChecklistCard,
    VolumeByMuscleWidget,
    RelativeStrengthWidget,
    Star1RMGoalsWidget,
    KeyDatesWidget,
} from './home/index';
import { ExerciseHistoryNerdView } from './ExerciseHistoryNerdView';
import { getLocalDateString } from '../utils/dateUtils';
import { WorkoutShareCard, buildShareCardDataFromLog } from './FinishWorkoutModal';
import { shareElementAsImage } from '../services/shareService';
import { LinkIcon } from './icons';
import Button from './ui/Button';

const DEFAULT_CARD_ORDER = ['battery', 'session', 'nutrition', 'program', 'top5', 'volumeMuscle', 'relativeStrength', 'star1rm', 'readiness', 'streak', 'quicklog'];

interface HomeProps {
    onNavigate: (view: View, program?: Program) => void;
    onResumeWorkout: () => void;
    onEditSleepLog: (log: SleepLog) => void;
}

const Home: React.FC<HomeProps> = ({ onNavigate, onResumeWorkout }) => {
    const { programs, history, activeProgramState, settings } = useAppState();
    const { handleStartWorkout, navigateTo, setIsStartWorkoutModalOpen, handleStartProgram, setSettings } = useAppDispatch();
    const [exerciseHistoryModal, setExerciseHistoryModal] = useState<string | null>(null);
    const [shareLog, setShareLog] = useState<WorkoutLog | null>(null);
    const [isSharing, setIsSharing] = useState(false);

    const todayStr = getLocalDateString();
    const currentDayOfWeek = new Date().getDay();

    const activeProgram = programs.find(p => p.id === activeProgramState?.programId);

    const todaySessions = useMemo(() => {
        if (!activeProgram || !activeProgramState) return [];
        const mIdx = activeProgramState.currentMacrocycleIndex || 0;
        const macro = activeProgram.macrocycles?.[mIdx];
        if (!macro) return [];

        let activeWeek: ProgramWeek | null = null;
        let mesoIdxTotal = 0;

        for (const block of (macro.blocks || [])) {
            for (const meso of (block.mesocycles || [])) {
                const week = (meso.weeks || []).find(w => w.id === activeProgramState.currentWeekId);
                if (week) {
                    activeWeek = week;
                    break;
                }
                mesoIdxTotal++;
            }
            if (activeWeek) break;
        }

        if (!activeWeek || !activeWeek.sessions) return [];

        return activeWeek.sessions
            .filter(s => s.dayOfWeek === currentDayOfWeek)
            .map(session => {
                const log = history.find(l => l.sessionId === session.id && l.date.startsWith(todayStr));
                return {
                    session,
                    program: activeProgram,
                    location: { macroIndex: mIdx, mesoIndex: mesoIdxTotal, weekId: activeWeek!.id },
                    isCompleted: !!log,
                    log: log ?? undefined,
                };
            });
    }, [activeProgram, activeProgramState, currentDayOfWeek, history, todayStr]);

    const cardOrder = useMemo(() => {
        const order = settings.homeCardOrder || DEFAULT_CARD_ORDER;
        const valid = order.filter(id => DEFAULT_CARD_ORDER.includes(id));
        const missing = DEFAULT_CARD_ORDER.filter(id => !valid.includes(id));
        return [...valid, ...missing];
    }, [settings.homeCardOrder]);

    const hasProgram = programs.length > 0 && !!activeProgramState;
    const hasNutrition = !!settings.hasSeenNutritionWizard;
    const showSetupChecklist = settings.hasSeenGeneralWizard && (!hasProgram || !hasNutrition);

    const handleDragEnd = (result: { destination?: { index: number }; source: { index: number } }) => {
        if (!result.destination || result.destination.index === result.source.index) return;
        const newOrder = [...cardOrder];
        const [removed] = newOrder.splice(result.source.index, 1);
        newOrder.splice(result.destination.index, 0, removed);
        setSettings({ homeCardOrder: newOrder });
    };

    const renderCard = (id: string) => {
        switch (id) {
            case 'battery': return <BatteryHeroSection key="battery" />;
            case 'session': return (
                <SessionTodayCard
                    key="session"
                    programName={activeProgram!.name}
                    programId={activeProgram!.id}
                    todaySessions={todaySessions}
                    onStartWorkout={handleStartWorkout}
                    onEditSession={(programId, macroIndex, mesoIndex, weekId, sessionId) =>
                        navigateTo('session-editor', { programId, macroIndex, mesoIndex, weekId, sessionId })
                    }
                    onViewProgram={(programId) => navigateTo('program-detail', { programId })}
                    onOpenStartWorkoutModal={() => setIsStartWorkoutModalOpen(true)}
                    onShareLog={(log) => setShareLog(log)}
                />
            );
            case 'nutrition': return <MiniNutritionWidget key="nutrition" onNavigate={() => navigateTo('nutrition')} />;
            case 'program': return <MiniProgramWidget key="program" onNavigate={() => activeProgram && navigateTo('program-detail', { programId: activeProgram.id })} />;
            case 'top5': return <TopExercisesWidget key="top5" onNavigateToExercise={name => setExerciseHistoryModal(name)} />;
            case 'volumeMuscle': return <VolumeByMuscleWidget key="volumeMuscle" onNavigate={() => activeProgram && navigateTo('program-detail', { programId: activeProgram.id })} />;
            case 'relativeStrength': return <RelativeStrengthWidget key="relativeStrength" onNavigate={() => activeProgram && navigateTo('program-detail', { programId: activeProgram.id })} />;
            case 'star1rm': return <Star1RMGoalsWidget key="star1rm" onNavigate={() => activeProgram && navigateTo('program-detail', { programId: activeProgram.id })} />;
            case 'keyDates': return <KeyDatesWidget key="keyDates" onNavigate={() => activeProgram && navigateTo('program-detail', { programId: activeProgram.id })} />;
            case 'readiness': return <ReadinessWidget key="readiness" />;
            case 'streak': return <StreakWidget key="streak" />;
            case 'quicklog': return <QuickLogWidget key="quicklog" />;
            default: return null;
        }
    };

    return (
        <div className="relative min-h-full w-full flex flex-col bg-black tab-bar-safe-area">
            {/* Subtle gradient orbs for depth */}
            <div className="fixed inset-0 pointer-events-none overflow-hidden -z-10">
                <div className="absolute -top-40 -right-20 w-80 h-80 rounded-full bg-cyan-500/5 blur-3xl" />
                <div className="absolute top-1/3 -left-20 w-60 h-60 rounded-full bg-violet-500/5 blur-3xl" />
            </div>

            <div className="relative z-10 flex flex-col px-6 pt-16 w-full max-w-md mx-auto space-y-4 pb-6">
                {activeProgram ? (
                    <>
                        {/* Header con más presencia */}
                        <div className="mb-5">
                            <h1 className="text-xl font-black text-white tracking-tight mb-1.5 drop-shadow-sm">
                                {activeProgram.name}
                            </h1>
                            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/5 border border-white/10">
                                <span className="text-[10px] font-bold text-cyan-400/90 uppercase tracking-wider">
                                    {new Date().toLocaleDateString('es-ES', { weekday: 'short' })}
                                </span>
                                <span className="text-zinc-500 text-[10px]">·</span>
                                <span className="text-[10px] font-mono text-zinc-400">
                                    {new Date().toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })}
                                </span>
                            </div>
                        </div>

                        {showSetupChecklist && (
                            <div className="mb-4">
                                <SetupChecklistCard
                                    hasProgram={hasProgram}
                                    hasNutrition={hasNutrition}
                                    onProgramPress={() => navigateTo('program-editor')}
                                    onNutritionPress={() => navigateTo('nutrition')}
                                />
                            </div>
                        )}

                        <DragDropContext onDragEnd={handleDragEnd}>
                            <Droppable droppableId="home-cards">
                                {(provided) => (
                                    <div
                                        ref={provided.innerRef}
                                        {...provided.droppableProps}
                                        className="space-y-4"
                                    >
                                        {cardOrder.map((id, index) => (
                                            <Draggable key={id} draggableId={id} index={index}>
                                                {(provided, snapshot) => (
                                                    <div
                                                        ref={provided.innerRef}
                                                        {...provided.draggableProps}
                                                        {...provided.dragHandleProps}
                                                        className={`transition-shadow duration-200 ${snapshot.isDragging ? 'opacity-90 scale-[0.98] shadow-xl shadow-cyan-500/10' : 'hover:shadow-lg hover:shadow-cyan-500/5'}`}
                                                    >
                                                        {renderCard(id)}
                                                    </div>
                                                )}
                                            </Draggable>
                                        ))}
                                        {provided.placeholder}
                                    </div>
                                )}
                            </Droppable>
                        </DragDropContext>
                    </>
                ) : programs.length > 0 ? (
                    <div className="w-full pt-4 pb-10 animate-fade-in space-y-6">
                        {showSetupChecklist && (
                            <SetupChecklistCard
                                hasProgram={hasProgram}
                                hasNutrition={hasNutrition}
                                onProgramPress={() => navigateTo('program-editor')}
                                onNutritionPress={() => navigateTo('nutrition')}
                            />
                        )}
                        <div className="text-center mb-6">
                            <h2 className="text-2xl sm:text-3xl font-black text-white uppercase tracking-tighter drop-shadow-lg">Selecciona un Programa</h2>
                            <p className="text-zinc-400 text-xs mt-2 font-medium">Tienes programas creados. Activa uno para comenzar.</p>
                        </div>

                        <div className="space-y-4">
                            {programs.map(prog => (
                                <div
                                    key={prog.id}
                                    className="bg-gradient-to-b from-white/[0.06] to-transparent border border-white/10 p-5 rounded-2xl flex flex-col gap-4 relative overflow-hidden group hover:border-cyan-500/30 hover:shadow-lg hover:shadow-cyan-500/5 transition-all duration-300"
                                >
                                    <div className="relative z-10 flex justify-between items-center">
                                        <div>
                                            <h3 className="text-lg font-black text-white uppercase tracking-tight truncate max-w-[200px]">{prog.name}</h3>
                                            <p className="text-[10px] text-zinc-500 font-bold uppercase tracking-widest mt-1">
                                                {prog.macrocycles?.length || 0} Fases • {prog.mode || 'Estándar'}
                                            </p>
                                        </div>
                                        <Button
                                            onClick={() => {
                                                handleStartProgram(prog.id);
                                                navigateTo('program-detail', { programId: prog.id });
                                            }}
                                            className="!py-2 !px-4 !text-[10px] !bg-white !text-black hover:scale-105 transition-transform shrink-0"
                                        >
                                            Activar
                                        </Button>
                                    </div>
                                    <div className="absolute -right-4 -bottom-4 opacity-10 group-hover:opacity-20 transition-opacity pointer-events-none text-cyan-400/50">
                                        <TargetIcon size={80} />
                                    </div>
                                </div>
                            ))}
                        </div>

                        <Button
                            onClick={() => navigateTo('program-editor')}
                            variant="secondary"
                            className="w-full !py-4 !text-xs !font-black !rounded-2xl border-dashed border-white/10 text-zinc-400 hover:text-cyan-400 hover:border-cyan-500/30 hover:bg-cyan-500/5 mt-4 bg-transparent transition-all duration-300"
                        >
                            <PlusIcon className="mr-2" size={16} /> CREAR OTRO PROGRAMA
                        </Button>
                    </div>
                ) : (
                    <div className="text-center w-full pt-2 pb-10">
                        {showSetupChecklist && (
                            <div className="mb-6">
                                <SetupChecklistCard
                                    hasProgram={hasProgram}
                                    hasNutrition={hasNutrition}
                                    onProgramPress={() => navigateTo('program-editor')}
                                    onNutritionPress={() => navigateTo('nutrition')}
                                />
                            </div>
                        )}
                        <div className="w-full flex justify-center mb-6 animate-fade-in">
                            <div className="relative">
                                <div className="absolute inset-0 bg-cyan-500/10 rounded-full blur-2xl scale-150" />
                                <CaupolicanIcon size={200} color="white" />
                            </div>
                        </div>
                        <h3 className="text-4xl sm:text-5xl font-black text-white uppercase tracking-tighter mb-4 drop-shadow-lg leading-[0.9]">
                            CREA TU PRIMER<br />
                            <span className="bg-gradient-to-r from-cyan-400 to-violet-400 bg-clip-text text-transparent">PROGRAMA DE</span>
                            <br />
                            ENTRENAMIENTO
                        </h3>
                        <p className="text-zinc-400 text-xs mb-8 font-medium leading-relaxed max-w-xs mx-auto">
                            No olvides presionar en <span className="text-cyan-400 font-bold">&quot;iniciar programa&quot;</span> para que aparezcan tus sesiones acá.
                        </p>
                        <Button
                            onClick={() => navigateTo('program-editor')}
                            className="w-full max-w-xs mx-auto !py-4 !text-base !font-black !rounded-2xl !bg-white !text-black border-none mb-10 hover:scale-[1.02] hover:shadow-xl hover:shadow-cyan-500/20 transition-all duration-300"
                        >
                            <PlusIcon className="mr-2" /> CREAR PROGRAMA
                        </Button>
                    </div>
                )}
            </div>

            {exerciseHistoryModal && (
                <ExerciseHistoryNerdView
                    exerciseName={exerciseHistoryModal}
                    history={history}
                    settings={settings}
                    onClose={() => setExerciseHistoryModal(null)}
                />
            )}

            {shareLog && (() => {
                const cardData = buildShareCardDataFromLog(shareLog, settings?.weightUnit ?? 'kg');
                return (
                <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/80">
                    <div className="bg-zinc-900 border border-white/10 rounded-xl p-4 w-full max-w-[260px] flex flex-col items-center">
                        <h3 className="text-xs font-black text-white mb-2 uppercase">Compartir</h3>
                        <div className="rounded border border-white/10 mb-2 overflow-hidden" style={{ width: 108, height: 192 }}>
                            <div className="w-[540px] h-[960px] origin-top-left" style={{ transform: 'scale(0.2)' }}>
                                <WorkoutShareCard {...cardData} preview />
                            </div>
                        </div>
                        <WorkoutShareCard {...cardData} />
                        <div className="flex gap-2 w-full mt-2">
                            <Button variant="secondary" className="flex-1 !py-2.5 !text-xs" onClick={() => setShareLog(null)}>
                                Cancelar
                            </Button>
                            <Button
                                className="flex-1 !py-2.5 !text-xs flex items-center justify-center gap-2"
                                onClick={async () => {
                                    setIsSharing(true);
                                    await shareElementAsImage('workout-summary-share-card', '¡Entrenamiento Terminado!', 'Registra tus entrenamientos con KPKN. #KPKN #Fitness');
                                    setIsSharing(false);
                                    setShareLog(null);
                                }}
                                disabled={isSharing}
                            >
                                {isSharing ? <span className="animate-pulse">Compartiendo...</span> : <><LinkIcon size={14} /> Compartir</>}
                            </Button>
                        </div>
                    </div>
                </div>
                );
            })()}
        </div>
    );
};

export default Home;
