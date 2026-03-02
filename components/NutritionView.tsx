// components/NutritionView.tsx
// Vista principal Nutrición — tabs Nutrición | Progreso, hero condicional, FAB, barra

import React, { useState, useMemo, useEffect } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { NutritionDashboard, RegisterFoodDrawer, NutritionWizard, NutritionSetupModal, useNutritionStats, NutritionPlanEditorModal } from './nutrition/index';
import NutritionHeroBanner from './nutrition/NutritionHeroBanner';
import { ProgressHeroBanner } from './nutrition/ProgressHeroBanner';
import { getLocalDateString } from '../utils/dateUtils';
import { UtensilsIcon, PencilIcon, TrashIcon } from './icons';
import ErrorBoundary from './ui/ErrorBoundary';
import WeightVsTargetChart from './WeightVsTargetChart';
import BodyFatChart from './BodyFatChart';
import MuscleMassChart from './MuscleMassChart';
import FFMIChart from './FFMIChart';
import { GoalReachedModal } from './nutrition/GoalReachedModal';
import { BodyProgressSheet } from './BodyProgressSheet';

type NutritionTab = 'nutricion' | 'progreso';

interface NutritionViewProps {
    initialTab?: NutritionTab;
}

const NutritionView: React.FC<NutritionViewProps> = ({ initialTab }) => {
    const { settings, bodyProgress, nutritionPlans, activeNutritionPlanId, isNutritionLogModalOpen } = useAppState();
    const { handleSaveNutritionLog, handleSaveBodyLog, setSettings, navigateTo, setIsBodyLogModalOpen, setBodyProgress, setIsNutritionLogModalOpen } = useAppDispatch();
    const [selectedDate, setSelectedDate] = useState(getLocalDateString());
    const [activeTab, setActiveTab] = useState<NutritionTab>(initialTab ?? 'nutricion');
    const [progressMetric, setProgressMetric] = useState<'weight' | 'bodyFat' | 'muscleMass'>('weight');
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [editingBodyLog, setEditingBodyLog] = useState<import('../types').BodyProgressLog | null>(null);
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

    useEffect(() => {
        if (initialTab) setActiveTab(initialTab);
    }, [initialTab]);

    useEffect(() => {
        if (isNutritionLogModalOpen) {
            setIsDrawerOpen(true);
            setActiveTab('nutricion');
            setIsNutritionLogModalOpen(false);
        }
    }, [isNutritionLogModalOpen, setIsNutritionLogModalOpen]);

    const sortedLogs = useMemo(
        () => [...bodyProgress].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()),
        [bodyProgress]
    );

    const height = settings.userVitals?.height ?? '--';
    const weightVal = lastLog?.weight ?? settings.userVitals?.weight ?? '--';
    const bodyFatVal = lastLog?.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage ?? '--';
    const muscleMassVal = lastLog?.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage ?? '--';

    const { estimatedDate, trendStatus } = useMemo(() => {
        const metric = progressMetric;
        const plan = activePlan;
        if (!plan || bodyProgress.length < 2 || plan.goalType !== metric) {
            return { estimatedDate: plan?.estimatedEndDate ?? null, trendStatus: 'on_track' as const };
        }
        const sorted = [...bodyProgress].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        const points = sorted
            .map((log) => {
                let y: number | undefined;
                if (metric === 'weight') y = log.weight;
                else if (metric === 'bodyFat') y = log.bodyFatPercentage;
                else y = log.muscleMassPercentage;
                const x = new Date(log.date).getTime() / (24 * 60 * 60 * 1000);
                return { x, y };
            })
            .filter((p) => p.y != null && !Number.isNaN(p.y)) as { x: number; y: number }[];
        if (points.length < 2) {
            return { estimatedDate: plan?.estimatedEndDate ?? null, trendStatus: 'on_track' as const };
        }
        const n = points.length;
        const sumX = points.reduce((s, p) => s + p.x, 0);
        const sumY = points.reduce((s, p) => s + p.y, 0);
        const sumXY = points.reduce((s, p) => s + p.x * p.y, 0);
        const sumX2 = points.reduce((s, p) => s + p.x * p.x, 0);
        const denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-10) {
            return { estimatedDate: plan?.estimatedEndDate ?? null, trendStatus: 'on_track' as const };
        }
        const slope = (n * sumXY - sumX * sumY) / denom;
        const intercept = sumY / n - slope * (sumX / n);
        const currentY = points[points.length - 1].y;
        const goal = plan.goalValue;
        const diff = goal - currentY;
        let etaDate: string | null = null;
        if (Math.abs(slope) > 1e-10 && (diff > 0 ? slope > 0 : slope < 0)) {
            const daysToGoal = diff / slope;
            const etaMs = points[points.length - 1].x * 24 * 60 * 60 * 1000 + daysToGoal * 24 * 60 * 60 * 1000;
            const d = new Date(etaMs);
            etaDate = d.toISOString().slice(0, 10);
        }
        const planEta = plan?.estimatedEndDate ?? etaDate ?? null;
        let trend: 'on_track' | 'behind' | 'ahead' = 'on_track';
        if (planEta && etaDate) {
            const planDays = new Date(planEta).getTime() / (24 * 60 * 60 * 1000);
            const ourDays = new Date(etaDate).getTime() / (24 * 60 * 60 * 1000);
            const diffDays = ourDays - planDays;
            if (diffDays > 7) trend = 'behind';
            else if (diffDays < -7) trend = 'ahead';
        }
        return { estimatedDate: etaDate ?? planEta, trendStatus: trend };
    }, [activePlan, bodyProgress, progressMetric]);

    if (showWizard) {
        return (
            <NutritionWizard
                onComplete={() => setShowWizard(false)}
            />
        );
    }

    return (
        <div className="flex flex-col min-h-full bg-[#121212]">
            {/* Hero primero — arriba siempre */}
            <div className="shrink-0">
                {activeTab === 'nutricion' ? (
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
                        onProgresoPress={() => setActiveTab('progreso')}
                        progressPct={progressPct}
                        activePlanName={activePlan?.name}
                        goalLabel={goalLabel}
                        onEditCalories={() => setIsGoalModalOpen(true)}
                    />
                ) : (
                    <ProgressHeroBanner
                        weight={weightVal}
                        bodyFat={bodyFatVal}
                        muscleMass={muscleMassVal}
                        activePlan={activePlan}
                        progressPct={progressPct}
                        selectedMetric={progressMetric}
                        onSelectMetric={setProgressMetric}
                        estimatedDate={estimatedDate}
                        trendStatus={trendStatus}
                        onRegisterPress={() => setIsBodyLogModalOpen(true)}
                        weightUnit={settings.weightUnit}
                    />
                )}
            </div>
            {/* Tabs debajo del hero — chips interactivos */}
            <div className="shrink-0 px-4 py-3 flex gap-2 bg-[#121212]">
                <button
                    onClick={() => setActiveTab('nutricion')}
                    className={`flex-1 py-2.5 rounded-xl text-sm font-bold transition-all ${
                        activeTab === 'nutricion'
                            ? 'bg-white text-black shadow-lg'
                            : 'bg-white/5 text-zinc-400 hover:bg-white/10 hover:text-white'
                    }`}
                >
                    Nutrición
                </button>
                <button
                    onClick={() => setActiveTab('progreso')}
                    className={`flex-1 py-2.5 rounded-xl text-sm font-bold transition-all ${
                        activeTab === 'progreso'
                            ? 'bg-white text-black shadow-lg'
                            : 'bg-white/5 text-zinc-400 hover:bg-white/10 hover:text-white'
                    }`}
                >
                    Progreso
                </button>
            </div>

            <div className="flex-1 min-h-0 overflow-y-auto">
                {activeTab === 'nutricion' ? (
                    <>
                        <div className="max-w-4xl mx-auto px-4 py-4 tab-bar-safe-area pb-40">
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
                                        <ErrorBoundary fallbackLabel="WeightVsTargetChart"><WeightVsTargetChart /></ErrorBoundary>
                                        <ErrorBoundary fallbackLabel="BodyFatChart"><BodyFatChart /></ErrorBoundary>
                                        <ErrorBoundary fallbackLabel="MuscleMassChart"><MuscleMassChart /></ErrorBoundary>
                                        <ErrorBoundary fallbackLabel="FFMIChart"><FFMIChart /></ErrorBoundary>
                                    </>
                                )}
                            />
                        </div>
                    </>
                ) : (
                    <>
                        <div className="max-w-4xl mx-auto px-4 pt-2 pb-2">
                            <button
                                onClick={() => setIsBodyLogModalOpen(true)}
                                className="w-full py-3 bg-white text-[#1a1a1a] font-medium text-sm uppercase tracking-wide"
                            >
                                Actualizar datos
                            </button>
                        </div>
                        <div className="max-w-4xl mx-auto px-4 py-4 tab-bar-safe-area pb-40 space-y-6">
                            <section>
                                <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-3">Evolución</p>
                                <ErrorBoundary fallbackLabel="WeightVsTargetChart"><WeightVsTargetChart /></ErrorBoundary>
                            </section>
                            <section>
                                <ErrorBoundary fallbackLabel="BodyFatChart"><BodyFatChart /></ErrorBoundary>
                            </section>
                            <section>
                                <ErrorBoundary fallbackLabel="MuscleMassChart"><MuscleMassChart /></ErrorBoundary>
                            </section>
                            <section>
                                <ErrorBoundary fallbackLabel="FFMIChart"><FFMIChart /></ErrorBoundary>
                            </section>
                            <section>
                                <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-3">Registros</p>
                                {sortedLogs.length === 0 ? (
                                    <p className="text-zinc-500 text-[10px] py-4">No hay registros. Pulsa Registrar avance.</p>
                                ) : (
                                    <div className="divide-y divide-white/5">
                                        {sortedLogs.map(log => (
                                            <div key={log.id} className="py-3 flex justify-between items-start">
                                                <div>
                                                    <p className="text-sm font-bold text-white">
                                                        {log.weight != null ? `${log.weight} ${settings.weightUnit}` : '--'}
                                                        {log.bodyFatPercentage != null && ` · ${log.bodyFatPercentage}% grasa`}
                                                        {log.muscleMassPercentage != null && ` · ${log.muscleMassPercentage}% músculo`}
                                                    </p>
                                                    <p className="text-[10px] text-zinc-500">
                                                        {new Date(log.date).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })}
                                                    </p>
                                                </div>
                                                <div className="flex gap-1">
                                                    <button
                                                        onClick={() => { setEditingBodyLog(log); }}
                                                        className="p-2 text-zinc-500 hover:text-white"
                                                        aria-label="Editar"
                                                    >
                                                        <PencilIcon size={14} />
                                                    </button>
                                                    <button
                                                        onClick={() => window.confirm('¿Eliminar?') && setBodyProgress(prev => prev.filter(l => l.id !== log.id))}
                                                        className="p-2 text-zinc-500 hover:text-rose-400"
                                                        aria-label="Eliminar"
                                                    >
                                                        <TrashIcon size={14} />
                                                    </button>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </section>
                        </div>
                    </>
                )}
            </div>

            {activeTab === 'nutricion' && (
                <button
                    onClick={() => setIsDrawerOpen(true)}
                    className="fixed z-20 w-14 h-14 rounded-2xl bg-white text-black font-semibold flex items-center justify-center shadow-xl active:scale-95 transition-transform"
                    style={{ bottom: 'calc(var(--tab-bar-safe-bottom, 140px) + 0.5rem)', right: '1rem' }}
                    aria-label="Añadir comida"
                >
                    <UtensilsIcon size={22} />
                </button>
            )}

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

            {editingBodyLog && (
                <BodyProgressSheet
                    isOpen
                    onClose={() => setEditingBodyLog(null)}
                    onSave={(log) => {
                        handleSaveBodyLog(log);
                        setEditingBodyLog(null);
                    }}
                    settings={settings}
                    initialLog={editingBodyLog}
                    activePlan={activePlan}
                />
            )}
        </div>
    );
};

export default NutritionView;
