// components/home/BatteryHeroSection.tsx
import React, { useState, useEffect, useMemo, useRef } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { calculateGlobalBatteriesAsync } from '../../services/computeWorkerService';
import {
    getPerMuscleBatteries,
    getSpinalDrainByExercise,
    ACCORDION_MUSCLES,
    MUSCLE_TO_ARTICULAR_BATTERIES,
    getTendonImbalanceAlerts,
    calculateArticularBatteries,
    getStructuralReadinessForMuscles,
    type SpinalDrainEntry,
    type ArticularBatteryState,
} from '../../services/auge';
import { BrainIcon, ActivityIcon, TargetIcon, ZapIcon, InfoIcon, ChevronDownIcon, ChevronRightIcon, XIcon, LayoutGridIcon, ListIcon } from '../icons';
import { NutritionTooltip } from '../nutrition/NutritionTooltip';
import SkeletonLoader from '../ui/SkeletonLoader';
import { motion, AnimatePresence, LayoutGroup } from 'framer-motion';

type BatteryId = 'muscular' | 'cns' | 'spinal';
type BatteryView = 'entwined' | 'carousel';

const getStatusColor = (value: number): string => {
    if (value >= 70) return 'text-emerald-500';
    if (value >= 40) return 'text-amber-500';
    return 'text-red-500';
};

const BATTERIA_TOOLTIP = 'Sistema que modela tu capacidad de recuperación en 3 dimensiones: Sistema Nervioso Central, músculos y columna vertebral. Basado en AUGE.';
const SNC_TOOLTIP = 'Sistema Nervioso Central: regula la activación muscular y la coordinación. La fatiga del SNC limita tu capacidad de generar fuerza máxima y afecta la técnica bajo carga.';
const COLUMNA_TOOLTIP = 'Fatiga de la columna vertebral y tejidos conectivos. Ejercicios con carga axial (sentadilla, peso muerto, remos pesados) la drenan más. Half-life ~72h.';

const RING_IDENTITY: Record<BatteryId, { stroke: string; glow: string; textClass: string; borderClass: string; bgClass: string; icon: any }> = {
    muscular: {
        stroke: '#FF2D55',
        glow: 'rgba(255,45,85,0.2)',
        textClass: 'text-rose-600',
        borderClass: 'border-rose-100',
        bgClass: 'bg-rose-50/50',
        icon: ActivityIcon
    },
    cns: {
        stroke: '#007AFF',
        glow: 'rgba(0,122,255,0.2)',
        textClass: 'text-blue-600',
        borderClass: 'border-blue-100',
        bgClass: 'bg-blue-50/50',
        icon: BrainIcon
    },
    spinal: {
        stroke: '#FF9500',
        glow: 'rgba(255,149,0,0.2)',
        textClass: 'text-orange-600',
        borderClass: 'border-orange-100',
        bgClass: 'bg-orange-50/50',
        icon: TargetIcon
    },
};

const RING_LABELS: Record<BatteryId, string> = {
    muscular: 'Muscular',
    cns: 'SNC',
    spinal: 'Columna',
};

// ─── CalibrationModal ─────────────────────────────────────────────────────────

const CalibrationModal: React.FC<{
    id: BatteryId;
    currentValue: number;
    calibValue: number;
    onCalibChange: (v: number) => void;
    onSave: () => void;
    onCancel: () => void;
}> = ({ id, currentValue, calibValue, onCalibChange, onSave, onCancel }) => {
    const identity = RING_IDENTITY[id];
    const label = RING_LABELS[id];
    const diff = calibValue - currentValue;

    return (
        <div className="fixed inset-0 z-[501] flex items-end justify-center px-4">
            <motion.div
                initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                className="absolute inset-0 bg-zinc-900/40 backdrop-blur-md pointer-events-auto"
                onClick={onCancel}
            />

            <motion.div
                initial={{ y: '100%' }} animate={{ y: 0 }}
                className="relative w-full max-w-sm mb-[140px] bg-white border border-[#ECE6F0] rounded-[2.5rem] shadow-2xl overflow-hidden pointer-events-auto p-8"
                onClick={e => e.stopPropagation()}
            >
                <div className="flex justify-between items-start mb-8">
                    <div>
                        <p className="text-[10px] font-black text-[#49454F] uppercase tracking-[0.2em] mb-1">Calibración Manual</p>
                        <h3 className={`text-2xl font-black uppercase tracking-tight ${identity.textClass}`}>{label}</h3>
                    </div>
                    <div className="text-right">
                        <span className={`text-4xl font-black tabular-nums tracking-tighter ${identity.textClass}`}>{calibValue}%</span>
                        {diff !== 0 && (
                            <p className={`text-[10px] font-bold mt-1 ${diff > 0 ? 'text-emerald-500' : 'text-rose-500'}`}>
                                {diff > 0 ? '+' : ''}{diff}% ajuste
                            </p>
                        )}
                    </div>
                </div>

                <div className="space-y-8">
                    <div className="space-y-4">
                        <input
                            type="range" min="0" max="100"
                            value={calibValue}
                            onChange={e => onCalibChange(parseInt(e.target.value))}
                            className="w-full h-2 bg-[#E6E0E9] rounded-full appearance-none cursor-pointer accent-current"
                            style={{ color: identity.stroke }}
                        />
                        <div className="flex justify-between text-[9px] text-[#49454F] font-black uppercase tracking-widest px-1">
                            <span>Mínimo</span>
                            <span>Máximo</span>
                        </div>
                    </div>

                    <p className="text-[11px] text-[#49454F] leading-relaxed font-medium">
                        Ajusta el valor que mejor refleje tu sensación actual. El sistema aplicará este offset a la telemetría automática.
                    </p>
                </div>

                <div className="flex gap-4 mt-10">
                    <button
                        onClick={onCancel}
                        className="flex-1 py-4 text-[10px] font-black uppercase tracking-widest text-[#49454F] hover:text-zinc-900 transition-colors"
                    >
                        Cancelar
                    </button>
                    <button
                        onClick={onSave}
                        className="flex-1 py-4 rounded-2xl text-[10px] font-black uppercase tracking-widest text-white shadow-xl active:scale-95 transition-all"
                        style={{ backgroundColor: identity.stroke, boxShadow: `0 8px 24px ${identity.glow}` }}
                    >
                        Confirmar
                    </button>
                </div>
            </motion.div>
        </div>
    );
};

// ─── RefinedRing ──────────────────────────────────────────────────────────────

const RefinedRing: React.FC<{
    id: BatteryId;
    value: number;
    size?: number;
    strokeWidth?: number;
    showLabel?: boolean;
    showPercent?: boolean;
    focused?: boolean;
    faded?: boolean;
    onTap?: () => void;
    onLongPress?: () => void;
    compact?: boolean;
}> = ({ id, value, size = 80, strokeWidth = 8, showLabel = true, showPercent = true, focused, faded, onTap, onLongPress, compact }) => {
    const identity = RING_IDENTITY[id];
    const Icon = identity.icon;
    const radius = (size - strokeWidth) / 2;
    const circumference = 2 * Math.PI * radius;
    const offset = circumference - (value / 100) * circumference;

    const timerRef = useRef<number | null>(null);

    return (
        <motion.div
            layout
            className={`flex flex-col items-center transition-all duration-500 scale-100 ${faded ? 'opacity-30 blur-[1px]' : 'opacity-100'}`}
            onPointerDown={() => {
                timerRef.current = window.setTimeout(() => {
                    onLongPress?.();
                    timerRef.current = null;
                }, 500);
            }}
            onPointerUp={() => {
                if (timerRef.current) {
                    clearTimeout(timerRef.current);
                    timerRef.current = null;
                    onTap?.();
                }
            }}
            onPointerLeave={() => {
                if (timerRef.current) clearTimeout(timerRef.current);
            }}
        >
            {showLabel && (
                <span className="text-[9px] font-black text-[#49454F] uppercase tracking-[0.2em] mb-3">
                    {RING_LABELS[id]}
                </span>
            )}

            <div className="relative" style={{ width: size, height: size }}>
                <svg width={size} height={size} className="transform -rotate-90">
                    <circle
                        cx={size / 2} cy={size / 2} r={radius}
                        fill={`${identity.stroke}08`} stroke="#f1f5f9" strokeWidth={strokeWidth}
                    />
                    <motion.circle
                        initial={{ strokeDashoffset: circumference }}
                        animate={{ strokeDashoffset: offset }}
                        transition={{ duration: 1.5, ease: "easeOut" }}
                        cx={size / 2} cy={size / 2} r={radius}
                        fill="none" stroke={identity.stroke} strokeWidth={strokeWidth}
                        strokeDasharray={circumference}
                        strokeLinecap="round"
                        style={{ filter: focused ? `drop-shadow(0 0 12px ${identity.glow})` : 'none' }}
                    />
                </svg>
                <div className={`absolute inset-0 flex items-center justify-center ${identity.textClass}`}>
                    <Icon size={size * 0.3} strokeWidth={2.5} />
                </div>
            </div>

            {showPercent && (
                <span className={`text-base font-black font-mono tracking-tighter mt-3 ${identity.textClass}`}>
                    {value}%
                </span>
            )}
        </motion.div>
    );
};

// ─── Main Component ──────────────────────────────────────────────────────────

export const BatteryHeroSection: React.FC<{ compact?: boolean }> = ({ compact = false }) => {
    const { history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, muscleGroupData, muscleHierarchy, postSessionFeedback, waterLogs, isAppLoading } = useAppState();
    const { setSettings, addToast } = useAppDispatch();

    const [view, setView] = useState<BatteryView>('entwined');
    const [batteries, setBatteries] = useState<any>(null);
    const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});
    const [articularBatteries, setArticularBatteries] = useState<Record<string, ArticularBatteryState> | null>(null);
    const [spinalDrain, setSpinalDrain] = useState<SpinalDrainEntry[]>([]);

    const [calibratingId, setCalibratingId] = useState<BatteryId | null>(null);
    const [selectedRingId, setSelectedRingId] = useState<BatteryId | null>(null);
    const [calibValues, setCalibValues] = useState<Record<BatteryId, number>>({ muscular: 0, cns: 0, spinal: 0 });

    const [muscularExpanded, setMuscularExpanded] = useState(false);

    useEffect(() => {
        if (isAppLoading) return;
        calculateGlobalBatteriesAsync(
            history || [], sleepLogs || [], dailyWellbeingLogs || [],
            nutritionLogs || [], settings, exerciseList || []
        ).then(setBatteries).catch(() => { });
    }, [history, sleepLogs, settings, exerciseList, dailyWellbeingLogs, nutritionLogs, isAppLoading]);

    useEffect(() => {
        if (isAppLoading || !history || !exerciseList) return;
        const pm = getPerMuscleBatteries(history, exerciseList, sleepLogs || [], settings, muscleHierarchy, postSessionFeedback || [], waterLogs || [], dailyWellbeingLogs || [], nutritionLogs || []);
        setPerMuscle(pm);
    }, [history, exerciseList, settings, isAppLoading]);

    useEffect(() => {
        if (!history || !exerciseList) return;
        setSpinalDrain(getSpinalDrainByExercise(history, exerciseList, 7, settings));
    }, [history, exerciseList, settings]);

    useEffect(() => {
        if (!history || !exerciseList) return;
        setArticularBatteries(calculateArticularBatteries(history, exerciseList, muscleGroupData, settings));
    }, [history, exerciseList, muscleGroupData, settings]);

    const muscularValueTotal = useMemo(() => {
        const vals = Object.values(perMuscle);
        if (vals.length === 0) return batteries?.muscular ?? 100;
        return Math.round(vals.reduce((a, b) => a + b, 0) / vals.length);
    }, [perMuscle, batteries]);

    const structuralReadiness = useMemo(() => {
        if (!articularBatteries || Object.keys(perMuscle).length === 0) return [];
        return getStructuralReadinessForMuscles(perMuscle, articularBatteries as any);
    }, [perMuscle, articularBatteries]);

    const structuralAverages = useMemo(() => {
        if (structuralReadiness.length === 0) {
            return {
                muscle: muscularValueTotal,
                articular: muscularValueTotal,
                combined: muscularValueTotal,
            };
        }

        return {
            muscle: Math.round(structuralReadiness.reduce((sum, item) => sum + item.muscleBattery, 0) / structuralReadiness.length),
            articular: Math.round(structuralReadiness.reduce((sum, item) => sum + item.articularBattery, 0) / structuralReadiness.length),
            combined: Math.round(structuralReadiness.reduce((sum, item) => sum + item.combinedBattery, 0) / structuralReadiness.length),
        };
    }, [structuralReadiness, muscularValueTotal]);

    const lowestStructuralReadiness = useMemo(() => {
        return [...structuralReadiness]
            .sort((a, b) => a.combinedBattery - b.combinedBattery)
            .slice(0, 2);
    }, [structuralReadiness]);

    const tendonImbalanceAlerts = useMemo(() => {
        if (!perMuscle || !articularBatteries) return [];
        return getTendonImbalanceAlerts(perMuscle, articularBatteries as any);
    }, [perMuscle, articularBatteries]);

    const handleSaveCalibration = () => {
        if (!batteries || !calibratingId) return;
        const calib = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0 };
        let { cnsDelta, muscularDelta, spinalDelta } = calib;
        const val = calibValues[calibratingId];

        if (calibratingId === 'cns') cnsDelta = val - (batteries.cns - (calib.cnsDelta || 0));
        else if (calibratingId === 'muscular') muscularDelta = val - (muscularValueTotal - (calib.muscularDelta || 0));
        else if (calibratingId === 'spinal') spinalDelta = val - (batteries.spinal - (calib.spinalDelta || 0));

        setSettings({
            batteryCalibration: { cnsDelta, muscularDelta, spinalDelta, lastCalibrated: new Date().toISOString() },
            hasPrecalibratedBattery: true,
        });
        addToast('Calibración guardada', 'success');
        setCalibratingId(null);
    };

    if (!batteries) return <SkeletonLoader lines={4} />;

    const ids: BatteryId[] = ['muscular', 'cns', 'spinal'];

    return (
        <div className="bg-white border border-[#ECE6F0] rounded-[2.5rem] p-8 shadow-sm relative overflow-hidden">
            {/* Background pattern super sutil */}
            <div className="absolute top-0 right-0 w-64 h-64 bg-[#ECE6F0] rounded-full blur-[100px] -mr-32 -mt-32 opacity-50 pointer-events-none" />

            {/* Toolbar Top */}
            <div className="flex justify-between items-center mb-10 relative z-10">
                <div className="flex items-center gap-3">
                    <div className="flex items-center justify-center w-8 h-8 rounded-full bg-emerald-50 text-emerald-500">
                        <ZapIcon size={14} />
                    </div>
                    <span className="text-xs font-black text-zinc-900 uppercase tracking-[0.2em]">Tus Rings</span>
                    <NutritionTooltip content={BATTERIA_TOOLTIP} title="Tú Batería" />
                </div>

                <div className="flex bg-[#ECE6F0] p-1 rounded-2xl border border-[#ECE6F0]">
                    <button
                        onClick={() => setView('entwined')}
                        className={`p-2.5 rounded-xl transition-all ${view === 'entwined' ? 'bg-white text-zinc-900 shadow-sm' : 'text-[#49454F] hover:text-zinc-600'}`}
                    >
                        <LayoutGridIcon size={16} />
                    </button>
                    <button
                        onClick={() => setView('carousel')}
                        className={`p-2.5 rounded-xl transition-all ${view === 'carousel' ? 'bg-white text-zinc-900 shadow-sm' : 'text-[#49454F] hover:text-zinc-600'}`}
                    >
                        <ListIcon size={16} />
                    </button>
                </div>
            </div>

            <LayoutGroup>
                <div className="relative min-h-[160px]">
                    <AnimatePresence mode="wait">
                        {view === 'entwined' ? (
                            <motion.div
                                key="entwined"
                                initial={{ opacity: 0, scale: 0.95 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0, scale: 0.95 }}
                                className="flex justify-center items-center gap-8 sm:gap-12"
                            >
                                {ids.map(id => (
                                    <RefinedRing
                                        key={id}
                                        id={id}
                                        value={id === 'muscular' ? muscularValueTotal : batteries[id]}
                                        size={96}
                                        strokeWidth={10}
                                        focused={selectedRingId === id}
                                        faded={selectedRingId !== null && selectedRingId !== id}
                                        onTap={() => setSelectedRingId(selectedRingId === id ? null : id)}
                                        onLongPress={() => {
                                            setCalibValues(v => ({ ...v, [id]: id === 'muscular' ? muscularValueTotal : batteries[id] }));
                                            setCalibratingId(id);
                                        }}
                                    />
                                ))}
                            </motion.div>
                        ) : (
                            <motion.div
                                key="carousel"
                                initial={{ opacity: 0, x: 20 }}
                                animate={{ opacity: 1, x: 0 }}
                                exit={{ opacity: 0, x: -20 }}
                                className="space-y-6"
                            >
                                <div className="flex overflow-x-auto gap-4 no-scrollbar pb-2 snap-x">
                                    {ids.map(id => {
                                        const val = id === 'muscular' ? muscularValueTotal : batteries[id];
                                        const identity = RING_IDENTITY[id];
                                        return (
                                            <div
                                                key={id}
                                                className={`snap-center shrink-0 w-80 p-6 rounded-3xl border transition-all flex items-center gap-6 ${selectedRingId === id ? `${identity.borderClass} ${identity.bgClass}` : 'bg-[#ECE6F0] border-[#ECE6F0]'}`}
                                                onClick={() => setSelectedRingId(id)}
                                            >
                                                <RefinedRing
                                                    id={id}
                                                    value={val}
                                                    size={100}
                                                    strokeWidth={12}
                                                    showLabel={false}
                                                    showPercent={false}
                                                    focused={selectedRingId === id}
                                                />
                                                <div className="flex-1">
                                                    <div className="flex justify-between items-baseline mb-2">
                                                        <h4 className={`text-lg font-black uppercase tracking-tight ${identity.textClass}`}>{RING_LABELS[id]}</h4>
                                                        <span className={`text-2xl font-black font-mono tracking-tighter ${identity.textClass}`}>{val}%</span>
                                                    </div>
                                                    {id === 'muscular' && (
                                                        <p className="mb-2 text-[10px] font-bold text-[#49454F]">
                                                            M {structuralAverages.muscle}% | T {structuralAverages.articular}% | C {structuralAverages.combined}%
                                                        </p>
                                                    )}

                                                    <div className="space-y-1.5">
                                                        {id === 'spinal' ? (
                                                            spinalDrain.slice(0, 2).map((e, idx) => (
                                                                <div key={idx} className="flex justify-between items-center text-[10px] font-bold text-[#49454F]">
                                                                    <span className="truncate max-w-[120px]">{e.exerciseName}</span>
                                                                    <span className="text-orange-500">-{e.totalSpinalDrain.toFixed(1)}%</span>
                                                                </div>
                                                            ))
                                                        ) : id === 'muscular' ? (
                                                            (lowestStructuralReadiness.length > 0
                                                                ? lowestStructuralReadiness
                                                                : Object.entries(perMuscle).sort((a, b) => a[1] - b[1]).slice(0, 2).map(([mid, mval]) => ({
                                                                    muscleId: mid,
                                                                    muscleLabel: ACCORDION_MUSCLES.find(m => m.id === mid)?.label || mid,
                                                                    muscleBattery: mval,
                                                                    articularBattery: mval,
                                                                    combinedBattery: mval,
                                                                } as any))
                                                            ).map((item: any) => (
                                                                <div key={item.muscleId} className="flex items-start justify-between gap-3 text-[10px] font-bold text-[#49454F]">
                                                                    <div className="min-w-0">
                                                                        <span className="block truncate">{item.muscleLabel}</span>
                                                                        <span className="block text-[9px] font-medium text-[#6b6472]">
                                                                            M {item.muscleBattery}% | T {item.articularBattery}% | C {item.combinedBattery}%
                                                                        </span>
                                                                    </div>
                                                                    <span className={getStatusColor(item.combinedBattery)}>{item.combinedBattery}%</span>
                                                                </div>
                                                            ))
                                                        ) : (
                                                            batteries.auditLogs.cns.slice(0, 2).map((log: { label: string; val: number }, idx: number) => (
                                                                <div key={idx} className="flex justify-between items-center text-[10px] font-bold text-[#49454F]">
                                                                    <span className="truncate">{log.label}</span>
                                                                    <span className={log.val < 0 ? 'text-red-500' : 'text-emerald-500'}>{log.val}%</span>
                                                                </div>
                                                            ))
                                                        )}
                                                        {((id === 'spinal' && spinalDrain.length === 0) || (id === 'cns' && batteries.auditLogs.cns.length === 0)) && (
                                                            <p className="text-[10px] text-[#49454F] italic">Sin datos recientes</p>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </motion.div>
                        )}
                    </AnimatePresence>
                </div>
            </LayoutGroup>

            {/* Verdict & Info */}
            <div className="mt-8 pt-6 border-t border-[#ECE6F0] flex items-start gap-4">
                <div className="p-2 rounded-xl bg-[#ECE6F0] text-[#49454F]">
                    <InfoIcon size={14} />
                </div>
                <div className="flex-1">
                    <p className="text-[11px] text-[#49454F] leading-relaxed font-medium italic opacity-80">
                        "{batteries.verdict || 'Análisis biométrico en tiempo real completado.'}"
                    </p>
                    <p className="text-[9px] text-[#49454F] font-black uppercase tracking-[0.2em] mt-3 opacity-40">
                        Cálculo Biométrico · Mantén pulsado un anillo para calibrar
                    </p>
                </div>
            </div>

            {/* Alertas Críticas (Tending Imbalances) */}
            {tendonImbalanceAlerts.length > 0 && (
                <motion.div
                    initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }}
                    className="mt-6 p-4 bg-amber-50 rounded-2xl border border-amber-100 flex items-center gap-4 shadow-sm"
                >
                    <div className="w-10 h-10 rounded-full bg-white flex items-center justify-center text-amber-500 shadow-sm shrink-0">
                        <InfoIcon size={20} />
                    </div>
                    <div className="flex-1 min-w-0">
                        <p className="text-[10px] font-black text-amber-700 uppercase tracking-widest mb-0.5">Alerta de Desbalance</p>
                        <p className="text-[11px] text-amber-700 leading-tight font-medium line-clamp-2">
                            {tendonImbalanceAlerts[0].muscleLabel} vs tendones {tendonImbalanceAlerts[0].articularLabel}. Evita cargas máximas hoy.
                        </p>
                    </div>
                </motion.div>
            )}

            {/* Calibración Modal */}
            <AnimatePresence>
                {calibratingId && (
                    <CalibrationModal
                        id={calibratingId}
                        currentValue={calibratingId === 'muscular' ? muscularValueTotal : batteries[calibratingId]}
                        calibValue={calibValues[calibratingId]}
                        onCalibChange={v => setCalibValues(prev => ({ ...prev, [calibratingId]: v }))}
                        onSave={handleSaveCalibration}
                        onCancel={() => setCalibratingId(null)}
                    />
                )}
            </AnimatePresence>
        </div>
    );
};
