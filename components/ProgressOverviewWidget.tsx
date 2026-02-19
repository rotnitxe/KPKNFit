// components/ProgressOverviewWidget.tsx
import React, { useMemo } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { calculateACWR, calculateWeeklyTonnageComparison, calculateFFMIProgress } from '../services/analysisService';
import Card from './ui/Card';
import { DumbbellIcon, ActivityIcon, BrainIcon, TrendingUpIcon, ArrowUpIcon, ArrowDownIcon } from './icons';
import Button from './ui/Button';

const ProgressBar: React.FC<{ value: number; max: number; color: string; }> = ({ value, max, color }) => {
    const percentage = max > 0 ? (value / max) * 100 : 0;
    return (
        <div className="w-full bg-slate-700 rounded-full h-2.5">
            <div className="h-2.5 rounded-full" style={{ width: `${Math.min(100, percentage)}%`, backgroundColor: color }}></div>
        </div>
    );
};

const ProgressOverviewWidget: React.FC = () => {
    const { history, settings, bodyProgress, programs, exerciseList, muscleHierarchy } = useAppState();
    const { navigateTo } = useAppDispatch();
    
    const weeklyTonnage = useMemo(() => calculateWeeklyTonnageComparison(history, settings), [history, settings]);
    const acwrData = useMemo(() => calculateACWR(history, settings, exerciseList), [history, settings, exerciseList]);
    const ffmiProgress = useMemo(() => calculateFFMIProgress(bodyProgress, settings), [bodyProgress, settings]);

    const weightGoalProgress = useMemo(() => {
        const target = settings.userVitals.targetWeight;
        if (!target) return null;
        const logsWithWeight = bodyProgress.filter(log => log.weight).sort((a,b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        if (logsWithWeight.length < 1) return { target, start: null, current: null, percentage: 0 };
        const start = logsWithWeight[0].weight!;
        const current = logsWithWeight[logsWithWeight.length - 1].weight!;
        const totalToChange = start - target;
        const changedSoFar = start - current;
        const percentage = totalToChange !== 0 ? (changedSoFar / totalToChange) * 100 : (current === target ? 100 : 0);
        return { target, start, current, percentage: Math.min(100, Math.max(0, percentage)) };
    }, [bodyProgress, settings.userVitals.targetWeight]);

    const noProgressData = weeklyTonnage.current === 0 && weeklyTonnage.previous === 0 && acwrData.acwr === 0 && !ffmiProgress.current && !weightGoalProgress;

    if (noProgressData) {
        return (
            <Card>
                <div className="flex justify-between items-center mb-3">
                    <h3 className="text-xl font-bold text-white">Resumen de Progreso</h3>
                    <Button onClick={() => navigateTo('progress')} variant="secondary" className="!text-xs !py-1 !px-3">Ver Más</Button>
                </div>
                 <div className="text-center py-4">
                    <p className="text-sm text-slate-400">Completa entrenamientos y registra tu peso para ver tus métricas de progreso.</p>
                </div>
            </Card>
        );
    }

    return (
        <div>
            <div className="flex justify-between items-center mb-3">
                <h3 className="text-xl font-bold text-white">Resumen de Progreso</h3>
                <Button onClick={() => navigateTo('progress')} variant="secondary" className="!text-xs !py-1 !px-3">Ver Más</Button>
            </div>
            
            <div className="horizontal-scroll-container hide-scrollbar -mx-4 px-4">
                <Card onClick={() => navigateTo('progress')} className="cursor-pointer">
                    <h4 className="font-bold text-white flex items-center gap-2 text-sm"><DumbbellIcon size={16}/> Sobrecarga Progresiva</h4>
                    <p className="text-xs text-slate-400 mt-1 mb-2">Tonelaje: semana actual vs. anterior.</p>
                    <ProgressBar value={weeklyTonnage.current} max={weeklyTonnage.previous || 1} color="#8B5CF6" />
                </Card>

                <Card onClick={() => navigateTo('progress')} className="cursor-pointer">
                    <h4 className="font-bold text-white flex items-center gap-2 text-sm"><ActivityIcon size={16}/> Fatiga y Estrés</h4>
                    <div className="text-center pt-2">
                        <p className={`text-5xl font-black ${acwrData.color}`}>{acwrData.acwr.toFixed(2)}</p>
                        <p className={`text-xs font-semibold mt-1 ${acwrData.color}`}>{acwrData.interpretation}</p>
                        <p className="text-[10px] text-slate-500 mt-1">Ratio ACWR</p>
                    </div>
                </Card>
                
                <Card onClick={() => ffmiProgress.current ? navigateTo('progress') : navigateTo('athlete-profile')} className="cursor-pointer">
                    <h4 className="font-bold text-white flex items-center gap-2 text-sm"><BrainIcon size={16}/> Progreso de FFMI</h4>
                    {ffmiProgress.current ? (
                        <div className="text-center pt-2">
                            <div className="flex items-center justify-center gap-2">
                                <p className="text-5xl font-black text-primary-color">{ffmiProgress.current.toFixed(1)}</p>
                                {ffmiProgress.trend === 'up' && <ArrowUpIcon className="text-green-400" />}
                                {ffmiProgress.trend === 'down' && <ArrowDownIcon className="text-red-400" />}
                            </div>
                            <p className="text-xs font-semibold mt-1 text-primary-color">FFMI Normalizado</p>
                            {ffmiProgress.initial && <p className="text-[10px] text-slate-500 mt-1">Inicial: {ffmiProgress.initial.toFixed(1)}</p>}
                        </div>
                    ) : (
                        <div className="text-center pt-4">
                            <p className="text-xs text-slate-400">Añade tu altura, peso y % de grasa en tu perfil para ver esta métrica.</p>
                        </div>
                    )}
                </Card>
                
                <Card onClick={() => weightGoalProgress ? navigateTo('progress') : navigateTo('athlete-profile')} className="cursor-pointer">
                    <h4 className="font-bold text-white flex items-center gap-2 text-sm"><TrendingUpIcon size={16}/> Meta de Peso</h4>
                    {weightGoalProgress ? (
                        <>
                            <p className="text-xs text-slate-400 mt-1 mb-2">Progreso hacia tu objetivo de {weightGoalProgress.target}{settings.weightUnit}.</p>
                            <div className="w-full bg-slate-700 rounded-full h-4">
                                <div className="bg-green-500 h-4 rounded-full" style={{ width: `${weightGoalProgress.percentage}%` }}></div>
                            </div>
                            <div className="flex justify-between text-xs font-semibold text-slate-500 mt-1">
                                <span>{weightGoalProgress.start?.toFixed(1)}</span>
                                <span>{weightGoalProgress.current?.toFixed(1)}</span>
                                <span>{weightGoalProgress.target.toFixed(1)}</span>
                            </div>
                        </>
                    ) : (
                        <div className="text-center pt-4">
                            <p className="text-xs text-slate-400">Define un peso objetivo en tu perfil para activar esta tarjeta.</p>
                        </div>
                    )}
                </Card>
            </div>
        </div>
    );
};

export default ProgressOverviewWidget;
