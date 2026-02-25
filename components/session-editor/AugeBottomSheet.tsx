import React, { useState, useMemo } from 'react';
import { Session, ExerciseMuscleInfo, Settings } from '../../types';
import { BarChartIcon, ActivityIcon, ZapIcon, TargetIcon, ChevronDownIcon, AlertTriangleIcon } from '../icons';
import { calculatePredictedSessionDrain, getDynamicAugeMetrics, calculatePersonalizedBatteryTanks, HYPERTROPHY_ROLE_MULTIPLIERS, DISPLAY_ROLE_WEIGHTS } from '../../services/auge';
import { calculateUnifiedMuscleVolume, normalizeMuscleGroup } from '../../services/volumeCalculator';

interface AugeBottomSheetProps {
    isOpen: boolean;
    onToggle: () => void;
    session: Session;
    weekSessions: Session[];
    exerciseList: ExerciseMuscleInfo[];
    settings: Settings;
    alertCount: number;
    neuralAlerts: { type: string; message: string; severity: 'warning' | 'critical' }[];
    volumeAlerts: { muscle: string; volume: number; threshold: number; failRatio: number; message?: string }[];
}

type AugeViewMode = 'volume' | 'drain' | 'alerts';

const AugeBottomSheet: React.FC<AugeBottomSheetProps> = ({
    isOpen, onToggle, session, weekSessions, exerciseList, settings, alertCount, neuralAlerts, volumeAlerts
}) => {
    const [viewMode, setViewMode] = useState<AugeViewMode>('volume');

    const allExercises = useMemo(() => {
        const exs = [...(session?.exercises || [])];
        (session?.parts || []).forEach(p => exs.push(...p.exercises));
        return exs;
    }, [session]);

    const volumeData = useMemo(() => {
        return calculateUnifiedMuscleVolume([session], exerciseList);
    }, [session, exerciseList]);

    const drainData = useMemo(() => {
        try {
            const d = calculatePredictedSessionDrain(session, exerciseList, settings);
            return {
                muscularDrainPct: d.muscleBatteryDrain,
                cnsDrainPct: d.cnsDrain,
                spinalDrainPct: d.spinalDrain,
            };
        } catch {
            return { muscularDrainPct: 0, cnsDrainPct: 0, spinalDrainPct: 0 };
        }
    }, [session, exerciseList, settings]);

    const totalAlerts = alertCount + neuralAlerts.length;

    return (
        <>
            {/* Toggle bar */}
            <button
                onClick={onToggle}
                className={`w-full flex items-center justify-between px-5 py-3 border-t transition-all ${
                    isOpen ? 'bg-zinc-900 border-white/10' : 'bg-black border-white/5 hover:bg-zinc-950'
                }`}
            >
                <div className="flex items-center gap-3">
                    <div className="flex items-center gap-1.5">
                        <BarChartIcon size={14} className="text-blue-400" />
                        <span className="text-[10px] font-black text-white uppercase tracking-widest">AUGE</span>
                    </div>

                    {!isOpen && (
                        <div className="flex items-center gap-2 ml-2">
                            <span className="text-[9px] font-bold text-zinc-500">
                                MSC {Math.round(drainData.muscularDrainPct || 0)}%
                            </span>
                            <span className="text-[9px] font-bold text-zinc-500">
                                SNC {Math.round(drainData.cnsDrainPct || 0)}%
                            </span>
                        </div>
                    )}
                </div>

                <div className="flex items-center gap-2">
                    {totalAlerts > 0 && (
                        <span className="px-1.5 py-0.5 rounded-full bg-red-500/20 text-red-400 text-[9px] font-black">
                            {totalAlerts}
                        </span>
                    )}
                    <ChevronDownIcon size={14} className={`text-zinc-500 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
                </div>
            </button>

            {/* Content */}
            {isOpen && (
                <div className="bg-zinc-950 border-t border-white/5 max-h-[50vh] overflow-y-auto custom-scrollbar animate-fade-in">
                    {/* Tabs */}
                    <div className="flex border-b border-white/5 px-2">
                        {([
                            { id: 'volume' as AugeViewMode, label: 'Volumen', icon: <TargetIcon size={12} /> },
                            { id: 'drain' as AugeViewMode, label: 'Fatiga', icon: <ActivityIcon size={12} /> },
                            { id: 'alerts' as AugeViewMode, label: 'Alertas', icon: <AlertTriangleIcon size={12} />, badge: totalAlerts },
                        ]).map(tab => (
                            <button
                                key={tab.id}
                                onClick={() => setViewMode(tab.id)}
                                className={`flex-1 flex items-center justify-center gap-1.5 py-2.5 text-[9px] font-black uppercase tracking-wider border-b-2 transition-colors ${
                                    viewMode === tab.id
                                        ? 'border-white text-white'
                                        : 'border-transparent text-zinc-600 hover:text-zinc-400'
                                }`}
                            >
                                {tab.icon}
                                {tab.label}
                                {tab.badge ? (
                                    <span className="px-1 py-0.5 rounded-full bg-red-500/20 text-red-400 text-[8px] font-black ml-0.5">
                                        {tab.badge}
                                    </span>
                                ) : null}
                            </button>
                        ))}
                    </div>

                    <div className="p-4">
                        {viewMode === 'volume' && (
                            <div className="space-y-2">
                                {volumeData.length === 0 ? (
                                    <p className="text-center text-[10px] text-zinc-600 py-4">Agrega ejercicios para ver el volumen.</p>
                                ) : (
                                    volumeData.sort((a, b) => b.displayVolume - a.displayVolume).map(v => (
                                        <div key={v.muscleGroup} className="flex items-center gap-3">
                                            <span className="text-[10px] font-bold text-zinc-400 w-24 truncate">{v.muscleGroup}</span>
                                            <div className="flex-1 h-2 bg-zinc-900 rounded-full overflow-hidden">
                                                <div
                                                    className={`h-full rounded-full transition-all ${
                                                        v.displayVolume > 5 ? 'bg-red-500' : v.displayVolume > 3 ? 'bg-yellow-500' : 'bg-blue-500'
                                                    }`}
                                                    style={{ width: `${Math.min(100, (v.displayVolume / 6) * 100)}%` }}
                                                />
                                            </div>
                                            <span className="text-[10px] font-black text-zinc-300 w-8 text-right">{v.displayVolume.toFixed(1)}</span>
                                        </div>
                                    ))
                                )}
                            </div>
                        )}

                        {viewMode === 'drain' && (
                            <div className="space-y-4">
                                {[
                                    { label: 'Muscular (MSC)', value: drainData.muscularDrainPct || 0, color: 'blue' },
                                    { label: 'SNC', value: drainData.cnsDrainPct || 0, color: 'purple' },
                                    { label: 'Espinal', value: drainData.spinalDrainPct || 0, color: '#00F0FF' },
                                ].map(metric => (
                                    <div key={metric.label}>
                                        <div className="flex items-center justify-between mb-1">
                                            <span className="text-[10px] font-bold text-zinc-400">{metric.label}</span>
                                            <span className={`text-[10px] font-black ${
                                                metric.value > 80 ? 'text-red-400' : metric.value > 50 ? 'text-yellow-400' : 'text-zinc-300'
                                            }`}>
                                                {Math.round(metric.value)}%
                                            </span>
                                        </div>
                                        <div className="h-2 bg-zinc-900 rounded-full overflow-hidden">
                                            <div
                                                className={`h-full rounded-full transition-all ${
                                                    metric.value > 80 ? 'bg-red-500' : metric.value > 50 ? 'bg-yellow-500' : `bg-${metric.color}-500`
                                                }`}
                                                style={{ width: `${Math.min(100, metric.value)}%` }}
                                            />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}

                        {viewMode === 'alerts' && (
                            <div className="space-y-2">
                                {totalAlerts === 0 ? (
                                    <p className="text-center text-[10px] text-zinc-600 py-4">Sin alertas. Tu sesión se ve equilibrada.</p>
                                ) : (
                                    <>
                                        {volumeAlerts.map((alert, i) => (
                                            <div key={`vol-${i}`} className="flex items-start gap-2 bg-red-950/20 border border-red-500/20 rounded-xl p-3">
                                                <AlertTriangleIcon size={14} className="text-red-400 shrink-0 mt-0.5" />
                                                <div>
                                                    <span className="text-[10px] font-black text-red-400">{alert.muscle}</span>
                                                    <p className="text-[9px] text-zinc-400">
                                                        Volumen: {alert.volume.toFixed(1)} / Límite: {alert.threshold}
                                                    </p>
                                                </div>
                                            </div>
                                        ))}
                                        {neuralAlerts.map((alert, i) => (
                                            <div key={`neural-${i}`} className={`flex items-start gap-2 rounded-xl p-3 border ${
                                                alert.severity === 'critical'
                                                    ? 'bg-red-950/20 border-red-500/20'
                                                    : 'bg-yellow-950/20 border-yellow-500/20'
                                            }`}>
                                                <ZapIcon size={14} className={alert.severity === 'critical' ? 'text-red-400' : 'text-yellow-400'} />
                                                <div>
                                                    <span className={`text-[10px] font-black ${alert.severity === 'critical' ? 'text-red-400' : 'text-yellow-400'}`}>
                                                        {alert.type}
                                                    </span>
                                                    <p className="text-[9px] text-zinc-400">{alert.message}</p>
                                                </div>
                                            </div>
                                        ))}
                                    </>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            )}
        </>
    );
};

export default AugeBottomSheet;
