
// components/PhysicalProgress.tsx
import React, { useMemo, Suspense } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { BrainIcon, BodyIcon, PlusIcon } from './icons';
import { calculateFFMI } from '../utils/calculations';
import Card from './ui/Card';
import Button from './ui/Button';
import ErrorBoundary from './ui/ErrorBoundary';

// Import sub-components
import WeeklyFatigueCard from './WeeklyFatigueCard';
import PersonalRecordsView from './PersonalRecordsView';
import IPFPointsCard from './IPFPointsCard';
import PowerliftingDashboard from './PowerliftingDashboard';
import WeeklyProgressAnalysis from './WeeklyProgressAnalysis';
import InjuryRiskAlerts from './InjuryRiskAlerts';
import GoalProjection from './GoalProjection';
import WeightVsTargetChart from './WeightVsTargetChart';
import BodyFatChart from './BodyFatChart';
import FFMIChart from './FFMIChart';
import CalorieHistoryChart from './CalorieHistoryChart';
import MacroDistributionSummary from './MacroDistributionSummary';
import ProgressPhotoStudio from './ProgressPhotoStudio';
import { CorrelationDashboard } from './CorrelationDashboard';
import PerformanceHighlightsWidget from './PerformanceHighlightsWidget';
import SleepAnalysisSection from './SleepAnalysisSection';

type ProgressTab = 'coach' | 'fuerza' | 'cuerpo' | 'correlaciones';

const PhysicalProgress: React.FC = () => {
    const { programs, history, settings, bodyProgress, activeSubTabs } = useAppState();
    const { setIsBodyLogModalOpen, navigateTo } = useAppDispatch();
    const activeTab = useMemo(() => (activeSubTabs['progress'] as ProgressTab) || 'cuerpo', [activeSubTabs]);

    const ffmiData = useMemo(() => {
        const { height, weight, bodyFatPercentage } = settings.userVitals;
        const lastLog = bodyProgress[bodyProgress.length - 1];
        const lastWeight = lastLog?.weight || weight;
        const lastBfp = lastLog?.bodyFatPercentage || bodyFatPercentage;
        
        if (!height || !lastWeight || !lastBfp) return null;
        
        return calculateFFMI(height, lastWeight, lastBfp);
    }, [settings.userVitals, bodyProgress]);

    return (
        <div className="space-y-8 animate-fade-in tab-bar-safe-area pt-4">
            {activeTab === 'coach' && (
                <div className="space-y-6">
                    <ErrorBoundary>
                        <WeeklyProgressAnalysis />
                    </ErrorBoundary>
                </div>
            )}
            

            {activeTab === 'cuerpo' && (
                <div className="space-y-6">
                     <div className="bg-gradient-to-br from-slate-900 to-black border border-white/10 rounded-3xl p-6 relative overflow-hidden">
                        <div className="absolute top-0 right-0 w-32 h-32 bg-sky-500/10 blur-[50px] rounded-full pointer-events-none"></div>
                        <h3 className="text-lg font-bold text-white mb-4 relative z-10">Métricas Actuales</h3>
                        <div className="grid grid-cols-3 gap-2 text-center my-4 relative z-10">
                            <div className="bg-white/5 p-3 rounded-2xl border border-white/5">
                                <p className="text-2xl font-black text-white">{settings.userVitals.weight || '--'}<span className="text-xs text-slate-500 font-bold ml-0.5">{settings.weightUnit}</span></p>
                                <p className="text-[9px] uppercase font-bold text-slate-500 tracking-wider">Peso</p>
                            </div>
                            <div className="bg-white/5 p-3 rounded-2xl border border-white/5">
                                <p className="text-2xl font-black text-white">{settings.userVitals.bodyFatPercentage || '--'}<span className="text-xs text-slate-500 font-bold ml-0.5">%</span></p>
                                <p className="text-[9px] uppercase font-bold text-slate-500 tracking-wider">Grasa</p>
                            </div>
                            <div className="bg-white/5 p-3 rounded-2xl border border-white/5">
                                <p className="text-2xl font-black text-sky-400">{ffmiData?.normalizedFfmi || '--'}</p>
                                <p className="text-[9px] uppercase font-bold text-slate-500 tracking-wider">FFMI</p>
                            </div>
                        </div>
                        <Button onClick={() => navigateTo('athlete-profile')} className="w-full !py-3 !text-xs uppercase font-black !bg-white/10 hover:!bg-white/20 border-white/5 relative z-10">
                            <BodyIcon size={16} /> Ver Perfil Completo
                        </Button>
                    </div>
                    
                    <Button onClick={() => setIsBodyLogModalOpen(true)} className="w-full !py-4 shadow-lg shadow-sky-900/20" variant="secondary">
                        <PlusIcon size={16} /> Registrar Progreso
                    </Button>
                    
                    <h2 className="text-xs font-black text-slate-500 uppercase tracking-[0.2em] mb-2 mt-8">Recuperación</h2>
                    <ErrorBoundary><SleepAnalysisSection /></ErrorBoundary>

                    <h2 className="text-xs font-black text-slate-500 uppercase tracking-[0.2em] mb-2 mt-8">Evolución</h2>
                    <ErrorBoundary><GoalProjection /></ErrorBoundary>
                    <ErrorBoundary><WeightVsTargetChart /></ErrorBoundary>
                    <ErrorBoundary><BodyFatChart /></ErrorBoundary>
                    <ErrorBoundary><FFMIChart /></ErrorBoundary>
                    <ErrorBoundary><CalorieHistoryChart /></ErrorBoundary>
                    <ErrorBoundary><MacroDistributionSummary /></ErrorBoundary>
                    <ErrorBoundary><ProgressPhotoStudio /></ErrorBoundary>
                </div>
            )}
            
            {activeTab === 'correlaciones' && (
                <div className="space-y-6">
                    <ErrorBoundary><CorrelationDashboard /></ErrorBoundary>
                </div>
            )}
        </div>
    );
};

export default PhysicalProgress;
