// components/NutritionView.tsx
// Vista principal de Nutrición: estructura tipo ProgramDetail (hero + tabs + cards)

import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { calculateDailyCalorieGoal } from '../utils/calorieFormulas';
import { NutritionDashboard, RegisterFoodDrawer, NutritionWizard, NutritionSetupModal, useNutritionStats, CalorieGoalCard, NutritionPlanEditorModal } from './nutrition/index';
import NutritionHeroBanner from './nutrition/NutritionHeroBanner';
import { getLocalDateString } from '../utils/dateUtils';
import { PlusIcon } from './icons';
import ErrorBoundary from './ui/ErrorBoundary';
import WeightVsTargetChart from './WeightVsTargetChart';
import BodyFatChart from './BodyFatChart';
import MuscleMassChart from './MuscleMassChart';
import FFMIChart from './FFMIChart';
import { GoalReachedModal } from './nutrition/GoalReachedModal';

const NutritionView: React.FC = () => {
    const { settings, bodyProgress, nutritionPlans, activeNutritionPlanId } = useAppState();
    const { handleSaveNutritionLog, setSettings, navigateTo, setIsBodyLogModalOpen } = useAppDispatch();
    const [selectedDate, setSelectedDate] = useState(getLocalDateString());
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [showWizard, setShowWizard] = useState(false);
    const [activeTab, setActiveTab] = useState<'hoy' | 'plan'>('hoy');

    const { dailyCalories, calorieGoal, hasCalorieGoal } = useNutritionStats(selectedDate);
    const needsSetupModal = !settings.hasSeenNutritionWizard && !settings.hasDismissedNutritionSetup;
    const hasDismissed = settings.hasDismissedNutritionSetup && !settings.hasSeenNutritionWizard;
    const [fabOpen, setFabOpen] = useState(false);
    const [goalReachedModalOpen, setGoalReachedModalOpen] = useState(false);

    const activePlan = useMemo(
        () => nutritionPlans.find((p) => p.id === activeNutritionPlanId) ?? null,
        [nutritionPlans, activeNutritionPlanId]
    );
    const lastLog = useMemo(
        () => (bodyProgress.length > 0 ? bodyProgress[bodyProgress.length - 1] : null),
        [bodyProgress]
    );
    const progressPct = useMemo(() => {
        if (!activePlan || !lastLog) return null;
        const sorted = [...bodyProgress].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        const first = sorted[0];
        let current: number | undefined;
        let start: number | undefined;
        if (activePlan.goalType === 'weight') {
            current = lastLog.weight ?? settings.userVitals?.weight;
            start = first?.weight ?? current;
        } else if (activePlan.goalType === 'bodyFat') {
            current = lastLog.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage;
            start = first?.bodyFatPercentage ?? current;
        } else {
            current = lastLog.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage;
            start = first?.muscleMassPercentage ?? current;
        }
        if (current == null || start == null) return null;
        const total = Math.abs(activePlan.goalValue - start);
        if (total === 0) return 100;
        const remaining = Math.abs(current - activePlan.goalValue);
        return Math.min(100, Math.max(0, Math.round((1 - remaining / total) * 100)));
    }, [activePlan, lastLog, bodyProgress, settings.userVitals]);

    const showGoalReachedCheck = useMemo(() => {
        if (!activePlan?.estimatedEndDate) return false;
        const today = new Date().toISOString().slice(0, 10);
        return activePlan.estimatedEndDate <= today;
    }, [activePlan?.estimatedEndDate]);

    React.useEffect(() => {
        if (showGoalReachedCheck && activePlan) {
            setGoalReachedModalOpen(true);
        }
    }, [showGoalReachedCheck, activePlan?.id]);

    React.useEffect(() => {
        try {
            if (sessionStorage.getItem('kpkn_open_nutrition_wizard')) {
                sessionStorage.removeItem('kpkn_open_nutrition_wizard');
                setShowWizard(true);
            }
        } catch (_) {}
    }, []);

    if (showWizard) {
        return (
            <NutritionWizard
                onComplete={() => setShowWizard(false)}
            />
        );
    }

    return (
        <div className="flex flex-col min-h-full bg-black">
            <NutritionHeroBanner
                selectedDate={selectedDate}
                onDateChange={setSelectedDate}
                dailyCalories={dailyCalories}
                calorieGoal={calorieGoal}
                hasCalorieGoal={hasCalorieGoal}
                onProgresoPress={() => navigateTo('body-progress')}
            />

            <div className="flex border-b border-white/5 shrink-0">
                <button
                    onClick={() => setActiveTab('hoy')}
                    className={`flex-1 py-2.5 text-xs font-bold uppercase tracking-wide text-center transition-colors ${activeTab === 'hoy' ? 'text-cyber-copper border-b-2 border-cyber-copper' : 'text-[#48484A]'}`}
                >
                    Hoy
                </button>
                <button
                    onClick={() => setActiveTab('plan')}
                    className={`flex-1 py-2.5 text-xs font-bold uppercase tracking-wide text-center transition-colors ${activeTab === 'plan' ? 'text-cyber-copper border-b-2 border-cyber-copper' : 'text-[#48484A]'}`}
                >
                    Plan
                </button>
            </div>

            <div className="flex-1 min-h-0 overflow-y-auto">
                {activeTab === 'hoy' && (
                    <div className="max-w-4xl mx-auto px-4 py-4 tab-bar-safe-area pb-24">
                        {activePlan && progressPct != null && (
                            <div
                                onClick={() => navigateTo('body-progress')}
                                className="mb-4 p-4 rounded-xl bg-white/5 border border-white/10 cursor-pointer hover:bg-white/10 transition-colors"
                            >
                                <div className="flex justify-between text-xs text-slate-400 mb-1">
                                    <span>Progreso hacia meta</span>
                                    <span>{progressPct}%</span>
                                </div>
                                <div className="h-2 bg-white/10 rounded-full overflow-hidden">
                                    <div
                                        className="h-full bg-cyber-copper rounded-full transition-all"
                                        style={{ width: `${progressPct}%` }}
                                    />
                                </div>
                            </div>
                        )}
                        <NutritionDashboard
                            selectedDate={selectedDate}
                            onDateChange={setSelectedDate}
                            onOpenDrawer={() => setIsDrawerOpen(true)}
                            showSetupBanner={hasDismissed}
                            onOpenWizard={() => setShowWizard(true)}
                            hideHeader
                        />
                    </div>
                )}
                {activeTab === 'plan' && (
                    <div className="max-w-4xl mx-auto px-4 py-4 tab-bar-safe-area">
                        <NutritionPlanTab
                            activePlan={activePlan}
                            selectedDate={selectedDate}
                            onDateChange={setSelectedDate}
                            onOpenDrawer={() => setIsDrawerOpen(true)}
                            onOpenWizard={() => setShowWizard(true)}
                            onProgresoPress={() => navigateTo('body-progress')}
                            onUpdateBodyData={() => setIsBodyLogModalOpen(true)}
                            progressPct={progressPct}
                            hasDismissed={hasDismissed}
                        />
                    </div>
                )}
            </div>

            <RegisterFoodDrawer
                isOpen={isDrawerOpen}
                onClose={() => setIsDrawerOpen(false)}
                onSave={handleSaveNutritionLog}
                settings={settings}
                initialDate={selectedDate}
            />
            <NutritionSetupModal
                isOpen={needsSetupModal}
                onConfigurarAhora={() => setShowWizard(true)}
                onConfigurarDespues={() => setSettings({ hasDismissedNutritionSetup: true })}
            />
            <GoalReachedModal
                isOpen={goalReachedModalOpen}
                onClose={() => setGoalReachedModalOpen(false)}
                plan={activePlan}
                currentWeight={lastLog?.weight ?? settings.userVitals?.weight}
                currentBodyFat={lastLog?.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage}
                currentMuscle={lastLog?.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage}
                onUpdateData={() => {
                    setGoalReachedModalOpen(false);
                    setIsBodyLogModalOpen(true);
                }}
                onCelebrate={() => {
                    setGoalReachedModalOpen(false);
                    setShowWizard(true);
                }}
                onAdjustPlan={() => {
                    setGoalReachedModalOpen(false);
                    setActiveTab('plan');
                }}
            />

            {activeTab === 'hoy' && (
                <div className="fixed bottom-24 right-6 z-20 flex flex-col items-end gap-2">
                    {fabOpen && (
                        <div className="flex flex-col gap-2 animate-fade-in">
                            <button
                                onClick={() => {
                                    setIsBodyLogModalOpen(true);
                                    setFabOpen(false);
                                }}
                                className="px-4 py-2 rounded-xl bg-slate-800 border border-white/10 text-sm font-bold text-white shadow-lg"
                            >
                                Actualizar datos
                            </button>
                            <button
                                onClick={() => {
                                    setIsDrawerOpen(true);
                                    setFabOpen(false);
                                }}
                                className="px-4 py-2 rounded-xl bg-slate-800 border border-white/10 text-sm font-bold text-white shadow-lg"
                            >
                                Registrar comida
                            </button>
                        </div>
                    )}
                    <button
                        onClick={() => setFabOpen((v) => !v)}
                        className="w-14 h-14 rounded-full border border-cyber-copper/50 bg-cyber-copper/20 text-cyber-copper flex items-center justify-center shadow-lg"
                        aria-label="Añadir"
                    >
                        <PlusIcon size={24} />
                    </button>
                </div>
            )}
        </div>
    );
};

const NutritionPlanTab: React.FC<{
    activePlan: { id: string; name: string; goalType: string; goalValue: number; estimatedEndDate?: string } | null;
    selectedDate: string;
    onDateChange: (d: string) => void;
    onOpenDrawer: () => void;
    onOpenWizard: () => void;
    onProgresoPress: () => void;
    onUpdateBodyData: () => void;
    progressPct: number | null;
    hasDismissed: boolean;
}> = ({
    activePlan,
    selectedDate,
    onDateChange,
    onOpenDrawer,
    onOpenWizard,
    onProgresoPress,
    onUpdateBodyData,
    progressPct,
    hasDismissed,
}) => {
    const { settings } = useAppState();
    const [isGoalModalOpen, setIsGoalModalOpen] = useState(false);
    const [planSubTab, setPlanSubTab] = useState<'hoy' | 'analytics'>('hoy');
    const calorieGoal = useMemo(
        () => calculateDailyCalorieGoal(settings, settings.calorieGoalConfig),
        [settings]
    );

    if (!activePlan) {
        return (
            <div className="space-y-4">
                <div className="p-8 rounded-2xl bg-white/5 border border-white/10 text-center">
                    <p className="text-slate-400 mb-4">No tienes un plan de nutrición activo.</p>
                    <button
                        onClick={onOpenWizard}
                        className="px-6 py-3 rounded-xl bg-cyber-copper/20 border border-cyber-copper/50 text-cyber-copper font-bold"
                    >
                        Crear plan
                    </button>
                </div>
                <div className="bg-[#0a0a0a] border border-white/5 rounded-2xl p-5">
                    <h3 className="text-xs font-bold text-[#8E8E93] uppercase tracking-wide mb-3">Objetivo calórico</h3>
                    <CalorieGoalCard calorieGoal={calorieGoal} onEditClick={() => setIsGoalModalOpen(true)} />
                </div>
                <NutritionPlanEditorModal isOpen={isGoalModalOpen} onClose={() => setIsGoalModalOpen(false)} />
            </div>
        );
    }

    const goalLabel =
        activePlan.goalType === 'weight'
            ? `${activePlan.goalValue} kg`
            : activePlan.goalType === 'bodyFat'
              ? `${activePlan.goalValue}% grasa`
              : `${activePlan.goalValue}% músculo`;

    return (
        <div className="space-y-4">
            <div className="p-4 rounded-2xl bg-white/5 border border-white/10">
                <h3 className="text-lg font-black text-white uppercase tracking-tight">{activePlan.name}</h3>
                <p className="text-sm text-slate-400 mt-1">Objetivo: {goalLabel}</p>
                {activePlan.estimatedEndDate && (
                    <p className="text-xs text-cyber-copper font-mono mt-1">
                        Fecha estimada: {activePlan.estimatedEndDate}
                    </p>
                )}
                <button
                    onClick={() => setIsGoalModalOpen(true)}
                    className="mt-3 text-xs font-bold text-cyber-copper uppercase"
                >
                    Editar calorías
                </button>
            </div>

            <div className="flex border-b border-white/5">
                <button
                    onClick={() => setPlanSubTab('hoy')}
                    className={`flex-1 py-2 text-xs font-bold uppercase tracking-wide text-center ${planSubTab === 'hoy' ? 'text-cyber-copper border-b-2 border-cyber-copper' : 'text-slate-500'}`}
                >
                    Hoy
                </button>
                <button
                    onClick={() => setPlanSubTab('analytics')}
                    className={`flex-1 py-2 text-xs font-bold uppercase tracking-wide text-center ${planSubTab === 'analytics' ? 'text-cyber-copper border-b-2 border-cyber-copper' : 'text-slate-500'}`}
                >
                    Analytics
                </button>
            </div>

            {planSubTab === 'hoy' && (
                <div className="space-y-4 pb-24">
                    {progressPct != null && (
                        <div
                            onClick={onProgresoPress}
                            className="p-4 rounded-xl bg-white/5 border border-white/10 cursor-pointer hover:bg-white/10"
                        >
                            <div className="flex justify-between text-xs text-slate-400 mb-1">
                                <span>Progreso hacia meta</span>
                                <span>{progressPct}%</span>
                            </div>
                            <div className="h-2 bg-white/10 rounded-full overflow-hidden">
                                <div
                                    className="h-full bg-cyber-copper rounded-full transition-all"
                                    style={{ width: `${progressPct}%` }}
                                />
                            </div>
                        </div>
                    )}
                    <NutritionDashboard
                        selectedDate={selectedDate}
                        onDateChange={onDateChange}
                        onOpenDrawer={onOpenDrawer}
                        showSetupBanner={hasDismissed}
                        onOpenWizard={onOpenWizard}
                        hideHeader
                    />
                    <button
                        onClick={onUpdateBodyData}
                        className="w-full py-3 rounded-xl border border-cyber-copper/30 bg-cyber-copper/10 text-cyber-copper font-bold"
                    >
                        Actualizar datos corporales
                    </button>
                </div>
            )}

            {planSubTab === 'analytics' && (
                <div className="space-y-6 pb-24">
                    <NutritionPlanAnalytics />
                </div>
            )}

            <NutritionPlanEditorModal isOpen={isGoalModalOpen} onClose={() => setIsGoalModalOpen(false)} />
        </div>
    );
};

const NutritionPlanAnalytics: React.FC = () => {
    return (
        <>
            <ErrorBoundary fallbackLabel="WeightVsTargetChart">
                <WeightVsTargetChart />
            </ErrorBoundary>
            <ErrorBoundary fallbackLabel="BodyFatChart">
                <BodyFatChart />
            </ErrorBoundary>
            <ErrorBoundary fallbackLabel="MuscleMassChart">
                <MuscleMassChart />
            </ErrorBoundary>
            <ErrorBoundary fallbackLabel="FFMIChart">
                <FFMIChart />
            </ErrorBoundary>
        </>
    );
};

export default NutritionView;
