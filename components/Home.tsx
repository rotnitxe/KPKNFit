// components/Home.tsx
// Vista Dios resumida: Programa + Plan + Batería (estilo NERD) con drag-and-drop

import React, { useMemo, useState } from 'react';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';
import { Program, Session, SleepLog, View, ProgramWeek } from '../types';
import Button from './ui/Button';
import { PlusIcon, TargetIcon, DragHandleIcon } from './icons';
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
} from './home/index';
import { ExerciseHistoryNerdView } from './ExerciseHistoryNerdView';

const DEFAULT_CARD_ORDER = ['battery', 'session', 'nutrition', 'program', 'top5', 'readiness', 'streak', 'quicklog'];

interface HomeProps {
    onNavigate: (view: View, program?: Program) => void;
    onResumeWorkout: () => void;
    onEditSleepLog: (log: SleepLog) => void;
}

const Home: React.FC<HomeProps> = ({ onNavigate, onResumeWorkout }) => {
    const { programs, history, activeProgramState, settings } = useAppState();
    const { handleStartWorkout, navigateTo, setIsStartWorkoutModalOpen, handleStartProgram, setSettings } = useAppDispatch();
    const [exerciseHistoryModal, setExerciseHistoryModal] = useState<string | null>(null);

    const todayStr = new Date().toISOString().split('T')[0];
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
            .map(session => ({
                session,
                program: activeProgram,
                location: { macroIndex: mIdx, mesoIndex: mesoIdxTotal, weekId: activeWeek!.id },
                isCompleted: history.some(log => log.sessionId === session.id && log.date.startsWith(todayStr)),
            }));
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
                />
            );
            case 'nutrition': return <MiniNutritionWidget key="nutrition" onNavigate={() => navigateTo('nutrition')} />;
            case 'program': return <MiniProgramWidget key="program" onNavigate={() => activeProgram && navigateTo('program-detail', { programId: activeProgram.id })} />;
            case 'top5': return <TopExercisesWidget key="top5" onNavigateToExercise={name => setExerciseHistoryModal(name)} />;
            case 'readiness': return <ReadinessWidget key="readiness" />;
            case 'streak': return <StreakWidget key="streak" />;
            case 'quicklog': return <QuickLogWidget key="quicklog" />;
            default: return null;
        }
    };

    return (
        <div className="relative h-full min-h-screen w-full flex flex-col bg-black pb-32 overflow-y-auto overflow-x-hidden custom-scrollbar">
            <div className="relative z-10 flex flex-col px-6 pt-16 w-full max-w-md mx-auto space-y-4">
                {activeProgram ? (
                    <>
                        {/* Header NERD */}
                        <div className="mb-2">
                            <h1 className="text-[10px] font-black text-zinc-500 uppercase tracking-[0.25em] font-mono mb-0.5">
                                {activeProgram.name}
                            </h1>
                            <p className="text-[9px] font-mono text-zinc-600 tracking-widest">
                                {new Date().toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'short', year: 'numeric' })}
                            </p>
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
                                                        className={`flex items-start gap-2 ${snapshot.isDragging ? 'opacity-80' : ''}`}
                                                    >
                                                        <div
                                                            {...provided.dragHandleProps}
                                                            className="mt-2 shrink-0 w-8 h-8 rounded-lg bg-white/5 flex items-center justify-center text-[#48484A] hover:text-white cursor-grab active:cursor-grabbing touch-none"
                                                        >
                                                            <DragHandleIcon size={14} />
                                                        </div>
                                                        <div className="flex-1 min-w-0">
                                                            {renderCard(id)}
                                                        </div>
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
                                    className="bg-[#111] border border-[#222] p-5 rounded-2xl flex flex-col gap-4 relative overflow-hidden group hover:border-white/20 transition-colors"
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
                                    <div className="absolute -right-4 -bottom-4 opacity-5 group-hover:opacity-10 transition-opacity pointer-events-none">
                                        <TargetIcon size={80} />
                                    </div>
                                </div>
                            ))}
                        </div>

                        <Button
                            onClick={() => navigateTo('program-editor')}
                            variant="secondary"
                            className="w-full !py-4 !text-xs !font-black !rounded-2xl border-dashed border-[#333] text-zinc-400 hover:text-white mt-4 bg-transparent"
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
                            <CaupolicanIcon size={200} color="white" />
                        </div>
                        <h3 className="text-4xl sm:text-5xl font-black text-white uppercase tracking-tighter mb-4 drop-shadow-lg leading-[0.9]">
                            CREA TU PRIMER<br />
                            <span className="text-[var(--text-color)]">PROGRAMA DE</span>
                            <br />
                            ENTRENAMIENTO
                        </h3>
                        <p className="text-zinc-400 text-xs mb-8 font-medium leading-relaxed max-w-xs mx-auto">
                            No olvides presionar en <span className="text-white font-bold">&quot;iniciar programa&quot;</span> para que aparezcan tus sesiones acá.
                        </p>
                        <Button
                            onClick={() => navigateTo('program-editor')}
                            className="w-full max-w-xs mx-auto !py-4 !text-base !font-black !rounded-2xl !bg-white !text-black border-none mb-10 hover:scale-[1.02] transition-transform"
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
        </div>
    );
};

export default Home;
