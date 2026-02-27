// components/BodyProgressView.tsx
// Vista de progreso corporal tipo ProgramDetail: Hero + tabs Registros | Analytics

import React, { useState, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { BodyProgressLog } from '../types';
import { ArrowLeftIcon, PlusIcon, PencilIcon, TrashIcon } from './icons';
import AddBodyLogModal from './AddBodyLogModal';
import BodyFatChart from './BodyFatChart';
import WeightVsTargetChart from './WeightVsTargetChart';
import FFMIChart from './FFMIChart';
import MuscleMassChart from './MuscleMassChart';
import ErrorBoundary from './ui/ErrorBoundary';
import Card from './ui/Card';
import { GoalReachedModal } from './nutrition/GoalReachedModal';

const BodyProgressView: React.FC = () => {
    const { bodyProgress, settings, nutritionPlans, activeNutritionPlanId } = useAppState();
    const { handleBack, handleSaveBodyLog, setBodyProgress, navigateTo } = useAppDispatch();
    const [activeTab, setActiveTab] = useState<'registros' | 'analytics'>('registros');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingLog, setEditingLog] = useState<BodyProgressLog | null>(null);
    const [goalReachedModalOpen, setGoalReachedModalOpen] = useState(false);

    const activePlan = useMemo(
        () => nutritionPlans.find((p) => p.id === activeNutritionPlanId) ?? null,
        [nutritionPlans, activeNutritionPlanId]
    );

    const lastLog = useMemo(
        () => bodyProgress.length > 0 ? bodyProgress[bodyProgress.length - 1] : null,
        [bodyProgress]
    );

    const sortedLogs = useMemo(
        () => [...bodyProgress].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()),
        [bodyProgress]
    );

    const progressToGoal = useMemo(() => {
        if (!activePlan || !lastLog) return null;
        const { goalType, goalValue } = activePlan;
        const sorted = [...bodyProgress].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        const first = sorted[0];
        let current: number | undefined;
        let start: number | undefined;
        if (goalType === 'weight') {
            current = lastLog.weight ?? settings.userVitals?.weight;
            start = first?.weight ?? current;
        } else if (goalType === 'bodyFat') {
            current = lastLog.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage;
            start = first?.bodyFatPercentage ?? current;
        } else if (goalType === 'muscleMass') {
            current = lastLog.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage;
            start = first?.muscleMassPercentage ?? current;
        }
        if (current == null || start == null) return null;
        const total = Math.abs(goalValue - start);
        if (total === 0) return 100;
        const remaining = Math.abs(current - goalValue);
        return Math.min(100, Math.max(0, Math.round((1 - remaining / total) * 100)));
    }, [activePlan, lastLog, bodyProgress, settings.userVitals]);

    const showGoalReachedCheck = useMemo(() => {
        if (!activePlan?.estimatedEndDate) return false;
        const today = new Date().toISOString().slice(0, 10);
        return activePlan.estimatedEndDate <= today;
    }, [activePlan?.estimatedEndDate]);

    React.useEffect(() => {
        if (showGoalReachedCheck && activePlan) setGoalReachedModalOpen(true);
    }, [showGoalReachedCheck, activePlan?.id]);

    const handleOpenAdd = () => {
        setEditingLog(null);
        setIsModalOpen(true);
    };

    const handleOpenEdit = (log: BodyProgressLog) => {
        setEditingLog(log);
        setIsModalOpen(true);
    };

    const handleSave = (log: BodyProgressLog) => {
        handleSaveBodyLog(log);
        setIsModalOpen(false);
        setEditingLog(null);
    };

    const handleDelete = (log: BodyProgressLog) => {
        if (window.confirm('¿Eliminar este registro?')) {
            setBodyProgress((prev) => prev.filter((l) => l.id !== log.id));
        }
    };

    const height = settings.userVitals?.height ?? '--';
    const weight = lastLog?.weight ?? settings.userVitals?.weight ?? '--';
    const bodyFat = lastLog?.bodyFatPercentage ?? settings.userVitals?.bodyFatPercentage ?? '--';
    const muscleMass = lastLog?.muscleMassPercentage ?? settings.userVitals?.muscleMassPercentage ?? '--';

    return (
        <div className="fixed inset-0 z-[100] bg-black text-white flex flex-col safe-area-root">
            {/* Hero */}
            <div className="shrink-0 bg-gradient-to-b from-slate-900/80 to-black border-b border-white/5 px-4 pt-4 pb-6">
                <div className="flex items-center justify-between mb-4">
                    <button
                        onClick={handleBack}
                        className="w-10 h-10 rounded-full border border-white/20 flex items-center justify-center text-slate-400 hover:text-white hover:bg-white/5"
                        aria-label="Volver"
                    >
                        <ArrowLeftIcon size={20} />
                    </button>
                    <h1 className="text-lg font-black uppercase tracking-tight">Progreso Corporal</h1>
                    <div className="w-10" />
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    <div className="bg-white/5 rounded-xl p-3 border border-white/5">
                        <p className="text-[10px] font-bold text-slate-500 uppercase">Estatura</p>
                        <p className="text-xl font-black text-white">{height}<span className="text-xs text-slate-500 ml-0.5">cm</span></p>
                    </div>
                    <div className="bg-white/5 rounded-xl p-3 border border-white/5">
                        <p className="text-[10px] font-bold text-slate-500 uppercase">Peso</p>
                        <p className="text-xl font-black text-white">{weight}<span className="text-xs text-slate-500 ml-0.5">{settings.weightUnit}</span></p>
                    </div>
                    <div className="bg-white/5 rounded-xl p-3 border border-white/5">
                        <p className="text-[10px] font-bold text-slate-500 uppercase">% Grasa</p>
                        <p className="text-xl font-black text-white">{bodyFat}<span className="text-xs text-slate-500 ml-0.5">%</span></p>
                    </div>
                    <div className="bg-white/5 rounded-xl p-3 border border-white/5">
                        <p className="text-[10px] font-bold text-slate-500 uppercase">% Músculo</p>
                        <p className="text-xl font-black text-white">{muscleMass}<span className="text-xs text-slate-500 ml-0.5">%</span></p>
                    </div>
                </div>
                {activePlan && progressToGoal != null && (
                    <div className="mt-4">
                        <div className="flex justify-between text-xs text-slate-400 mb-1">
                            <span>Progreso hacia meta</span>
                            <span>{progressToGoal}%</span>
                        </div>
                        <div className="h-2 bg-white/10 rounded-full overflow-hidden">
                            <div
                                className="h-full bg-cyber-copper rounded-full transition-all"
                                style={{ width: `${progressToGoal}%` }}
                            />
                        </div>
                    </div>
                )}
            </div>

            {/* Tabs */}
            <div className="flex border-b border-white/5 shrink-0">
                <button
                    onClick={() => setActiveTab('registros')}
                    className={`flex-1 py-2.5 text-xs font-bold uppercase tracking-wide text-center transition-colors ${activeTab === 'registros' ? 'text-cyber-copper border-b-2 border-cyber-copper' : 'text-[#48484A]'}`}
                >
                    Registros
                </button>
                <button
                    onClick={() => setActiveTab('analytics')}
                    className={`flex-1 py-2.5 text-xs font-bold uppercase tracking-wide text-center transition-colors ${activeTab === 'analytics' ? 'text-cyber-copper border-b-2 border-cyber-copper' : 'text-[#48484A]'}`}
                >
                    Analytics
                </button>
            </div>

            {/* Content */}
            <div className="flex-1 min-h-0 overflow-y-auto">
                {activeTab === 'registros' && (
                    <div className="max-w-4xl mx-auto px-4 py-4 tab-bar-safe-area pb-24">
                        <button
                            onClick={handleOpenAdd}
                            className="w-full py-4 rounded-xl border border-cyber-copper/50 bg-cyber-copper/20 text-cyber-copper font-bold flex items-center justify-center gap-2 mb-6"
                        >
                            <PlusIcon size={18} /> Añadir registro
                        </button>
                        {sortedLogs.length === 0 ? (
                            <p className="text-slate-500 text-center py-8">No hay registros. Añade el primero.</p>
                        ) : (
                            <div className="space-y-3">
                                {sortedLogs.map((log) => (
                                    <Card key={log.id} className="p-4">
                                        <div className="flex items-center justify-between">
                                            <div>
                                                <p className="font-bold text-white">
                                                    {log.weight != null ? `${log.weight} ${settings.weightUnit}` : '--'}
                                                    {log.bodyFatPercentage != null && ` · ${log.bodyFatPercentage}% grasa`}
                                                    {log.muscleMassPercentage != null && ` · ${log.muscleMassPercentage}% músculo`}
                                                </p>
                                                <p className="text-xs text-slate-500">
                                                    {new Date(log.date).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })}
                                                </p>
                                            </div>
                                            <div className="flex gap-2">
                                                <button
                                                    onClick={() => handleOpenEdit(log)}
                                                    className="w-8 h-8 rounded-lg border border-white/10 flex items-center justify-center text-slate-400 hover:text-white"
                                                    aria-label="Editar"
                                                >
                                                    <PencilIcon size={14} />
                                                </button>
                                                <button
                                                    onClick={() => handleDelete(log)}
                                                    className="w-8 h-8 rounded-lg border border-white/10 flex items-center justify-center text-slate-400 hover:text-red-400"
                                                    aria-label="Eliminar"
                                                >
                                                    <TrashIcon size={14} />
                                                </button>
                                            </div>
                                        </div>
                                    </Card>
                                ))}
                            </div>
                        )}
                    </div>
                )}
                {activeTab === 'analytics' && (
                    <div className="max-w-4xl mx-auto px-4 py-4 tab-bar-safe-area space-y-6 pb-24">
                        <ErrorBoundary><WeightVsTargetChart /></ErrorBoundary>
                        <ErrorBoundary><BodyFatChart /></ErrorBoundary>
                        <ErrorBoundary><MuscleMassChart /></ErrorBoundary>
                        <ErrorBoundary><FFMIChart /></ErrorBoundary>
                    </div>
                )}
            </div>

            {/* FAB */}
            <button
                onClick={handleOpenAdd}
                className="fixed bottom-24 right-6 z-20 w-14 h-14 rounded-full border border-cyber-copper/50 bg-cyber-copper/20 text-cyber-copper flex items-center justify-center shadow-lg"
                aria-label="Añadir registro"
            >
                <PlusIcon size={24} />
            </button>

            <AddBodyLogModal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); setEditingLog(null); }}
                onSave={handleSave}
                settings={settings}
                isOnline={true}
                initialLog={editingLog}
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
                    setIsModalOpen(true);
                }}
                onCelebrate={() => {
                    setGoalReachedModalOpen(false);
                    try { sessionStorage.setItem('kpkn_open_nutrition_wizard', '1'); } catch (_) {}
                    navigateTo('nutrition');
                }}
                onAdjustPlan={() => {
                    setGoalReachedModalOpen(false);
                    navigateTo('nutrition');
                }}
            />
        </div>
    );
};

export default BodyProgressView;
