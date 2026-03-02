// components/NutritionView.tsx
// Vista principal Nutrición — estética Tú: hero con anillos, flujo único, barra fija

import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { NutritionDashboard, RegisterFoodDrawer, NutritionWizard, NutritionSetupModal, useNutritionStats, NutritionPlanEditorModal } from './nutrition/index';
import NutritionHeroBanner from './nutrition/NutritionHeroBanner';
import { getLocalDateString } from '../utils/dateUtils';
import { UtensilsIcon } from './icons';
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
    const [analyticsExpanded, setAnalyticsExpanded] = useState(false);
    const [isGoalModalOpen, setIsGoalModalOpen] = useState(false);

    const { dailyCalories, calorieGoal, hasCalorieGoal, dailyTotals, proteinGoal, carbGoal, fatGoal } = useNutritionStats(selectedDate);
    const needsSetupModal = !settings.hasSeenNutritionWizard && !settings.hasDismissedNutritionSetup;
    const hasDismissed = settings.hasDismissedNutritionSetup && !settings.hasSeenNutritionWizard;
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

    const goalLabel = activePlan
        ? activePlan.goalType === 'weight'
            ? `${activePlan.goalValue} kg`
            : activePlan.goalType === 'bodyFat'
              ? `${activePlan.goalValue}% grasa`
              : `${activePlan.goalValue}% músculo`
        : null;

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
            <div className="flex-1 min-h-0 overflow-y-auto">
                <NutritionHeroBanner
                    selectedDate={selectedDate}
                    onDateChange={setSelectedDate}
                    dailyCalories={dailyCalories}
                    calorieGoal={calorieGoal}
                    hasCalorieGoal={hasCalorieGoal}
                    protein={dailyTotals.protein}
                    proteinGoal={proteinGoal}
                    carbs={dailyTotals.carbs}
                    carbGoal={carbGoal}
                    fats={dailyTotals.fats}
                    fatGoal={fatGoal}
                    onProgresoPress={() => navigateTo('body-progress')}
                    progressPct={progressPct}
                    activePlanName={activePlan?.name}
                    goalLabel={goalLabel}
                    onEditCalories={() => setIsGoalModalOpen(true)}
                />

                <div className="max-w-4xl mx-auto px-4 py-4 tab-bar-safe-area pb-32">
                    <NutritionDashboard
                        selectedDate={selectedDate}
                        onDateChange={setSelectedDate}
                        onOpenDrawer={() => setIsDrawerOpen(true)}
                        showSetupBanner={hasDismissed}
                        onOpenWizard={() => setShowWizard(true)}
                        hideHeader
                        activePlan={activePlan}
                        onUpdateBodyData={() => setIsBodyLogModalOpen(true)}
                        analyticsExpanded={analyticsExpanded}
                        onAnalyticsExpand={() => setAnalyticsExpanded(v => !v)}
                        renderAnalytics={() => (
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
                        )}
                    />
                </div>
            </div>

            <div className="fixed left-0 right-0 bottom-0 z-30 bg-[#0a0a0a] border-t border-white/10 px-4 py-3 flex gap-3" style={{ paddingBottom: 'max(0.75rem, env(safe-area-inset-bottom))' }}>
                <button
                    onClick={() => setIsDrawerOpen(true)}
                    className="flex-1 py-3 rounded-xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 font-bold text-sm uppercase tracking-wide flex items-center justify-center gap-2"
                >
                    <UtensilsIcon size={18} />
                    Añadir comida
                </button>
                <button
                    onClick={() => setIsBodyLogModalOpen(true)}
                    className="flex-1 py-3 rounded-xl bg-white/5 border border-white/10 text-zinc-300 font-bold text-sm uppercase tracking-wide hover:bg-white/10 transition-colors"
                >
                    Actualizar datos
                </button>
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
                }}
            />
            <NutritionPlanEditorModal isOpen={isGoalModalOpen} onClose={() => setIsGoalModalOpen(false)} />
        </div>
    );
};

export default NutritionView;
