// components/home/BatteryHeroSection.tsx
import React, { useState, useEffect, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { calculateGlobalBatteriesAsync } from '../../services/computeWorkerService';
import {
    getPerMuscleBatteries,
    getSpinalDrainByExercise,
    ACCORDION_MUSCLES,
    MUSCLE_TO_ARTICULAR_BATTERIES,
    getTendonImbalanceAlerts,
    type SpinalDrainEntry,
} from '../../services/auge';
import { BrainIcon, ActivityIcon, TargetIcon, ZapIcon, InfoIcon, ChevronDownIcon, ChevronRightIcon } from '../icons';
import { NutritionTooltip } from '../nutrition/NutritionTooltip';
import SkeletonLoader from '../ui/SkeletonLoader';

type BatteryId = 'cns' | 'muscular' | 'spinal';

const getStatusColor = (value: number): string => {
    if (value >= 70) return 'text-emerald-400';
    if (value >= 40) return 'text-amber-400';
    return 'text-red-400';
};

const BATTERIA_TOOLTIP = 'Sistema que modela tu capacidad de recuperación en 3 dimensiones: Sistema Nervioso Central, músculos y columna vertebral. Basado en AUGE.';
const SNC_TOOLTIP = 'Sistema Nervioso Central: regula la activación muscular y la coordinación. La fatiga del SNC limita tu capacidad de generar fuerza máxima y afecta la técnica bajo carga.';
const COLUMNA_TOOLTIP = 'Fatiga de la columna vertebral y tejidos conectivos. Ejercicios con carga axial (sentadilla, peso muerto, remos pesados) la drenan más. Half-life ~72h.';

const RING_IDENTITY: Record<BatteryId, { stroke: string; glow: string; textClass: string; borderClass: string; bgClass: string }> = {
    cns: { stroke: '#38bdf8', glow: 'rgba(56,189,248,0.2)', textClass: 'text-sky-300', borderClass: 'border-sky-400/60', bgClass: 'bg-sky-500/10' },
    muscular: { stroke: '#f472b6', glow: 'rgba(244,114,182,0.2)', textClass: 'text-pink-300', borderClass: 'border-pink-400/60', bgClass: 'bg-pink-500/10' },
    spinal: { stroke: '#fb923c', glow: 'rgba(251,146,60,0.2)', textClass: 'text-orange-300', borderClass: 'border-orange-400/60', bgClass: 'bg-orange-500/10' },
};

const RING_LABELS: Record<BatteryId, string> = {
    cns: 'SNC',
    muscular: 'Muscular',
    spinal: 'Columna',
};

// ─── CalibrationModal ─────────────────────────────────────────────────────────
// Modal plano y profesional. No compite con la TabBar: tiene margen inferior
// calculado con safe-area-inset-bottom + altura de la TabBar (96px).

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
        <div className="fixed inset-0 z-[501] flex items-end justify-center pointer-events-none px-4" onClick={onCancel}>
            {/* Backdrop */}
            <div className="absolute inset-0 bg-black/60 backdrop-blur-sm pointer-events-auto animate-fade-in" />

            {/* Panel Seguro sobre TabBar (120px margin) */}
            <div
                className="relative w-full max-w-sm mb-[120px] bg-zinc-950 border border-white/10 rounded-2xl shadow-[0_8px_40px_rgba(0,0,0,0.6)] overflow-hidden pointer-events-auto animate-slide-up"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="p-6 pb-4 flex justify-between items-start">
                    <div>
                        <p className="text-[10px] font-black text-zinc-500 uppercase tracking-widest mb-1">Calibración Manual</p>
                        <h3 className={`text-xl font-bold uppercase tracking-tight ${identity.textClass}`}>{label}</h3>
                    </div>
                    <div className="text-right">
                        <span className={`text-3xl font-black font-mono tracking-tighter tabular-nums ${identity.textClass}`}>{calibValue}%</span>
                        {diff !== 0 && (
                            <p className={`text-[10px] font-mono font-bold mt-0.5 ${diff > 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                                {diff > 0 ? '+' : ''}{diff}% ajuste
                            </p>
                        )}
                    </div>
                </div>

                <div className="px-6 py-4 space-y-6">
                    <div className="space-y-4">
                        <input
                            type="range" min="0" max="100"
                            value={calibValue}
                            onChange={e => onCalibChange(parseInt(e.target.value))}
                            className="w-full h-1 bg-zinc-800 rounded-full appearance-none cursor-pointer"
                            style={{
                                accentColor: identity.stroke,
                                background: `linear-gradient(to right, ${identity.stroke} ${calibValue}%, #27272a ${calibValue}%)`
                            }}
                        />
                        <div className="flex justify-between text-[8px] text-zinc-600 font-black uppercase tracking-widest">
                            <span>Mínimo (0%)</span>
                            <span>Máximo (100%)</span>
                        </div>
                    </div>

                    <p className="text-[10px] text-zinc-500 italic leading-relaxed opacity-70">
                        Ajusta el valor que mejor refleje tu sensación actual. El sistema aplicará este offset a la telemetría automática.
                    </p>
                </div>

                {/* Botones */}
                <div className="flex gap-3 px-6 pb-6 pt-2">
                    <button
                        onClick={onCancel}
                        className="flex-1 py-3.5 text-[10px] font-black uppercase tracking-widest text-zinc-500 hover:text-white transition-colors"
                    >
                        Cancelar
                    </button>
                    <button
                        onClick={onSave}
                        className="flex-1 py-3.5 rounded-xl text-[10px] font-black uppercase tracking-widest text-black active:scale-95 transition-all"
                        style={{ backgroundColor: identity.stroke }}
                    >
                        Confirmar
                    </button>
                </div>
            </div>
        </div>
    );
};

// ─── BatteryRing ──────────────────────────────────────────────────────────────

const BatteryRing: React.FC<{
    id: BatteryId;
    label: string;
    value: number;
    icon: React.ReactNode;
    tooltip?: string;
    isCalibrating: boolean;
    onPointerDown: () => void;
    onPointerUp: () => void;
    focusedId: BatteryId | null;
    onTap: () => void;
}> = ({ id, label, value, icon, tooltip, isCalibrating, onPointerDown, onPointerUp, focusedId, onTap }) => {
    const isFaded = focusedId !== null && focusedId !== id && !isCalibrating;
    const isFocus = focusedId === id;
    const identity = RING_IDENTITY[id];

    const displayColor = isFaded
        ? 'text-zinc-600'
        : isFocus
            ? `${identity.textClass}`
            : 'text-zinc-200';

    const strokeColor = isFaded ? '#3f3f46' : identity.stroke;
    const trackColor = isFaded ? '#1c1c1f' : '#27272a';

    return (
        <div
            className={`relative flex flex-col items-center justify-center p-4 rounded-xl border transition-all duration-500 ease-in-out cursor-pointer select-none ${isCalibrating
                ? `${identity.borderClass} ${identity.bgClass} scale-[1.04]`
                : isFaded
                    ? 'border-white/5 bg-black/40 opacity-35 scale-95 blur-[0.6px]'
                    : isFocus
                        ? `${identity.borderClass} ${identity.bgClass} scale-[1.06] shadow-[0_0_24px_${identity.glow}]`
                        : 'border-white/10 bg-black/30 hover:bg-white/5'
                }`}
            onPointerDown={onPointerDown}
            onPointerUp={onPointerUp}
            onPointerLeave={onPointerUp}
            onClick={onTap}
        >
            <div className="flex items-center justify-center w-14 h-14 mb-2 relative">
                <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                    <path
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                        fill="none" stroke={trackColor} strokeWidth="2.5"
                        style={{ transition: 'stroke 0.5s ease' }}
                    />
                    <path
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                        fill="none" stroke={strokeColor}
                        strokeWidth={isFocus ? '3.2' : '2.5'}
                        strokeDasharray={`${value}, 100`}
                        strokeLinecap="round"
                        style={{ transition: 'stroke 0.5s ease, stroke-width 0.4s ease, stroke-dasharray 0.5s ease' }}
                        filter={isFocus ? `drop-shadow(0 0 4px ${identity.stroke})` : undefined}
                    />
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">{icon}</div>
            </div>
            <span className={`text-lg font-black font-mono tracking-tighter transition-all duration-500 ${displayColor}`}>
                {value}%
            </span>
            <span className="text-[8px] text-zinc-500 font-black uppercase tracking-[0.2em] mt-1 flex items-center gap-0.5">
                {label}
                {tooltip && <NutritionTooltip content={tooltip} title={label} />}
            </span>
        </div>
    );
};

// ─── Constantes ───────────────────────────────────────────────────────────────

const COLUMNA_TABS: { id: number | null; label: string }[] = [
    { id: 7, label: '7d' },
    { id: 15, label: '15d' },
    { id: 30, label: '30d' },
    { id: null, label: 'Hist.' },
];

// ─── BatteryHeroSection ───────────────────────────────────────────────────────

interface BatteryHeroSectionProps {
    compact?: boolean;
}

export const BatteryHeroSection: React.FC<BatteryHeroSectionProps> = ({ compact = false }) => {
    const { history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, muscleHierarchy, postSessionFeedback, waterLogs, isAppLoading } = useAppState();
    const { setSettings, addToast } = useAppDispatch();

    const [batteries, setBatteries] = useState<Awaited<ReturnType<typeof calculateGlobalBatteriesAsync>> | null>(null);
    const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});
    const [spinalDrain, setSpinalDrain] = useState<SpinalDrainEntry[]>([]);
    const [columnaTab, setColumnaTab] = useState<number | null>(7);
    const [muscularExpanded, setMuscularExpanded] = useState(false);
    const [deltoidsExpanded, setDeltoidsExpanded] = useState(false);
    const [expandedMuscleId, setExpandedMuscleId] = useState<string | null>(null);
    const versionRef = React.useRef(0);

    const [calibratingId, setCalibratingId] = useState<BatteryId | null>(null);
    const [selectedRingId, setSelectedRingId] = useState<BatteryId | null>(null);
    const [calibCns, setCalibCns] = useState(0);
    const [calibMusc, setCalibMusc] = useState(0);
    const [calibSpinal, setCalibSpinal] = useState(0);
    const pressTimerRef = React.useRef<number | null>(null);
    const wasLongPressRef = React.useRef(false);

    const showPrecalibBadge = !settings.hasPrecalibratedBattery;

    useEffect(() => {
        if (isAppLoading) return;
        const version = ++versionRef.current;
        calculateGlobalBatteriesAsync(
            history || [], sleepLogs || [], dailyWellbeingLogs || [],
            nutritionLogs || [], settings, exerciseList || []
        ).then(b => { if (versionRef.current === version) setBatteries(b); })
            .catch(() => { });
    }, [history, sleepLogs, settings, exerciseList, muscleHierarchy, postSessionFeedback, waterLogs, dailyWellbeingLogs, nutritionLogs, isAppLoading]);

    useEffect(() => {
        if (isAppLoading || !history || !exerciseList) return;
        try {
            const pm = getPerMuscleBatteries(
                history, exerciseList, sleepLogs || [],
                settings, muscleHierarchy,
                postSessionFeedback || [], waterLogs || [],
                dailyWellbeingLogs || [], nutritionLogs || []
            );
            setPerMuscle(pm);
        } catch { setPerMuscle({}); }
    }, [history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs, dailyWellbeingLogs, nutritionLogs, isAppLoading]);

    useEffect(() => {
        if (!history || !exerciseList) return;
        setSpinalDrain(getSpinalDrainByExercise(history, exerciseList, columnaTab, settings));
    }, [history, exerciseList, columnaTab, settings]);

    const handlePointerDown = (id: BatteryId) => {
        if (calibratingId) return;
        if (id === 'muscular') {
            if (!settings.hasSeenMuscleFatigueTip) {
                addToast('Ajusta la fatiga muscular deslizando hacia abajo hasta la sección de Músculos', 'suggestion');
                setSettings({ hasSeenMuscleFatigueTip: true });
            }
            setMuscularExpanded(true);
            return;
        }
        wasLongPressRef.current = false;
        pressTimerRef.current = window.setTimeout(() => {
            wasLongPressRef.current = true;
            setCalibratingId(id);
            setCalibCns(batteries?.cns ?? 0);
            setCalibMusc(batteries?.muscular ?? 0);
            setCalibSpinal(batteries?.spinal ?? 0);
            pressTimerRef.current = null;
        }, 500);
    };

    const handlePointerUp = () => {
        if (pressTimerRef.current) {
            clearTimeout(pressTimerRef.current);
            pressTimerRef.current = null;
        }
    };

    const handleRingTap = (id: BatteryId) => {
        if (wasLongPressRef.current) {
            wasLongPressRef.current = false;
            return;
        }
        if (calibratingId) return;
        setSelectedRingId(prev => (prev === id ? null : id));
    };

    const handleSaveCalibration = () => {
        if (!batteries || !calibratingId) return;
        const calib = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0 };
        let cnsDelta = calib.cnsDelta ?? 0;
        let muscularDelta = calib.muscularDelta ?? 0;
        let spinalDelta = calib.spinalDelta ?? 0;
        if (calibratingId === 'cns') cnsDelta = calibCns - (batteries.cns - cnsDelta);
        else if (calibratingId === 'muscular') muscularDelta = calibMusc - (batteries.muscular - muscularDelta);
        else if (calibratingId === 'spinal') spinalDelta = calibSpinal - (batteries.spinal - spinalDelta);
        setSettings({
            batteryCalibration: { cnsDelta, muscularDelta, spinalDelta, lastCalibrated: new Date().toISOString() },
            hasPrecalibratedBattery: true,
        });
        addToast('Calibración guardada', 'success');
        setCalibratingId(null);
    };

    const setCalibValue = (id: BatteryId, v: number) => {
        if (id === 'cns') setCalibCns(v);
        else if (id === 'muscular') setCalibMusc(v);
        else setCalibSpinal(v);
    };

    const muscularValueTotal = useMemo(() => {
        const vals = Object.values(perMuscle);
        if (vals.length === 0) return batteries?.muscular ?? 100;
        return Math.round(vals.reduce((a, b) => a + b, 0) / vals.length);
    }, [perMuscle, batteries]);

    const mainMuscles = ACCORDION_MUSCLES.filter(m => !m.isDeltoidPortion);
    const deltoidMuscles = ACCORDION_MUSCLES.filter(m => m.isDeltoidPortion);

    if (!batteries) return <SkeletonLoader lines={4} />;

    const lowBatteryAlerts = [
        batteries.cns < 40 && { label: 'SNC', val: batteries.cns },
        batteries.muscular < 40 && { label: 'Muscular', val: batteries.muscular },
        batteries.spinal < 40 && { label: 'Columna', val: batteries.spinal },
    ].filter(Boolean) as { label: string; val: number }[];

    const tendonImbalanceAlerts = batteries.articularBatteries
        ? getTendonImbalanceAlerts(perMuscle, batteries.articularBatteries)
        : [];

    const amberAlerts = [
        batteries.cns >= 40 && batteries.cns < 70 && { label: 'SNC', val: batteries.cns },
        batteries.muscular >= 40 && batteries.muscular < 70 && { label: 'Muscular', val: batteries.muscular },
        batteries.spinal >= 40 && batteries.spinal < 70 && { label: 'Columna', val: batteries.spinal },
    ].filter(Boolean) as { label: string; val: number }[];

    const focusedId = calibratingId ?? selectedRingId;

    return (
        <div className={`bg-[var(--md-sys-color-surface-container)] border border-[var(--md-sys-color-outline-variant)] rounded-2xl overflow-visible relative ${compact ? 'rounded-xl' : ''}`}>
            {showPrecalibBadge && !compact && (
                <div className="absolute top-3 right-3 z-10">
                    <span className="px-2 py-0.5 rounded text-[8px] font-black uppercase bg-amber-500/20 text-amber-400 border border-amber-500/40">
                        Calibrar
                    </span>
                </div>
            )}

            <div className={`flex justify-between items-center ${compact ? 'px-4 py-2' : 'px-5 py-3 border-b border-white/5'}`}>
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.25em] flex items-center gap-2">
                    <ZapIcon size={10} className="text-amber-400" /> TÚ BATERÍA
                    {!compact && <NutritionTooltip content={BATTERIA_TOOLTIP} title="Tú Batería" />}
                </span>
                {!compact && <span className="text-[8px] font-mono text-zinc-600">Sistemas biológicos</span>}
            </div>

            <div className={compact ? 'p-4 pt-0' : 'p-4'}>
                {/* Alertas */}
                {(lowBatteryAlerts.length > 0 || amberAlerts.length > 0 || tendonImbalanceAlerts.length > 0) && (
                    <div className="mb-3 space-y-1">
                        {lowBatteryAlerts.map(a => (
                            <div key={a.label} className="text-[9px] font-mono text-red-400 bg-red-950/30 border border-red-500/20 rounded-lg px-3 py-1.5">
                                Batería {a.label} baja: {a.val}%
                            </div>
                        ))}
                        {amberAlerts.map(a => (
                            <div key={a.label} className="text-[9px] font-mono text-amber-400 bg-amber-950/20 border border-amber-500/20 rounded-lg px-3 py-1.5">
                                Batería {a.label}: {a.val}%
                            </div>
                        ))}
                        {tendonImbalanceAlerts.map((a, i) => (
                            <div key={`tendon-${i}`} className={`text-[9px] font-mono rounded-lg px-3 py-1.5 ${a.type === 'danger' ? 'text-red-400 bg-red-950/30 border border-red-500/20' : 'text-amber-400 bg-amber-950/20 border border-amber-500/20'}`}>
                                Desfase: {a.muscleLabel} vs tendones {a.articularLabel}. Evita cargas altas hoy.
                            </div>
                        ))}
                    </div>
                )}

                {/* 3 anillos */}
                <div className={`grid grid-cols-3 ${compact ? 'gap-2' : 'gap-3'}`}>
                    <BatteryRing
                        id="cns"
                        label="SNC"
                        value={batteries.cns}
                        icon={<BrainIcon size={16} className={`transition-all duration-500 ${(focusedId !== null && focusedId !== 'cns') ? 'text-zinc-600' : 'text-sky-300 drop-shadow-[0_0_8px_rgba(56,189,248,0.6)]'
                            }`} />}
                        tooltip={SNC_TOOLTIP}
                        isCalibrating={calibratingId === 'cns'}
                        onPointerDown={() => handlePointerDown('cns')}
                        onPointerUp={handlePointerUp}
                        focusedId={focusedId}
                        onTap={() => handleRingTap('cns')}
                    />
                    <BatteryRing
                        id="muscular"
                        label="Muscular"
                        value={muscularValueTotal}
                        icon={<ActivityIcon size={16} className={`transition-all duration-500 ${(focusedId !== null && focusedId !== 'muscular') ? 'text-zinc-600' : 'text-pink-300 drop-shadow-[0_0_8px_rgba(244,114,182,0.6)]'
                            }`} />}
                        isCalibrating={calibratingId === 'muscular'}
                        onPointerDown={() => handlePointerDown('muscular')}
                        onPointerUp={handlePointerUp}
                        focusedId={focusedId}
                        onTap={() => handleRingTap('muscular')}
                    />
                    <BatteryRing
                        id="spinal"
                        label="Columna"
                        value={batteries.spinal}
                        icon={<TargetIcon size={16} className={`transition-all duration-500 ${(focusedId !== null && focusedId !== 'spinal') ? 'text-zinc-600' : 'text-orange-300 drop-shadow-[0_0_8px_rgba(251,146,60,0.6)]'
                            }`} />}
                        tooltip={COLUMNA_TOOLTIP}
                        isCalibrating={calibratingId === 'spinal'}
                        onPointerDown={() => handlePointerDown('spinal')}
                        onPointerUp={handlePointerUp}
                        focusedId={focusedId}
                        onTap={() => handleRingTap('spinal')}
                    />
                </div>

                {compact && (
                    <p className="text-[7px] text-zinc-600 mt-2 font-mono">Mantén pulsado para calibrar</p>
                )}

                {!compact && (
                    <>
                        {/* Desglose consumo */}
                        <div className="mt-4 pt-4 border-t border-white/5">
                            <p className="text-[9px] font-black text-zinc-500 uppercase tracking-widest mb-2">Consumo reciente</p>
                            <div className="space-y-1 max-h-20 overflow-y-auto custom-scrollbar">
                                {batteries.auditLogs.cns.slice(0, 3).map((log, i) => (
                                    <div key={`cns-${i}`} className="flex justify-between text-[9px] font-mono">
                                        <span className="text-zinc-400">{log.label}</span>
                                        <span className={log.type === 'workout' ? 'text-red-400' : log.type === 'bonus' ? 'text-emerald-400' : 'text-zinc-500'}>{String(log.val)}{typeof log.val === 'number' ? '%' : ''}</span>
                                    </div>
                                ))}
                                {batteries.auditLogs.cns.length === 0 && batteries.auditLogs.muscular.length === 0 && batteries.auditLogs.spinal.length === 0 && (
                                    <p className="text-[9px] text-zinc-600 font-mono">Sin drenaje reciente</p>
                                )}
                            </div>
                        </div>

                        {/* Acordeón Batería Muscular */}
                        <div className="mt-4 pt-4 border-t border-white/5">
                            <button
                                onClick={() => setMuscularExpanded(!muscularExpanded)}
                                className="w-full flex items-center justify-between text-[9px] font-black text-white uppercase tracking-widest py-2 hover:bg-white/5 rounded-lg transition-colors"
                            >
                                <span className="flex items-center gap-2">
                                    <ActivityIcon size={12} className="text-rose-400" /> Batería Muscular
                                    <NutritionTooltip content="Recuperación por grupo muscular. Cada músculo tiene su propio perfil de recuperación (half-life)." title="Batería Muscular" />
                                </span>
                                {muscularExpanded ? <ChevronDownIcon size={12} /> : <ChevronRightIcon size={12} />}
                            </button>
                            {muscularExpanded && (
                                <div className="space-y-1 mt-2 animate-fade-in">
                                    {mainMuscles.map(m => {
                                        const articularIds = MUSCLE_TO_ARTICULAR_BATTERIES[m.id];
                                        const hasSubBatteries = articularIds && articularIds.length > 0 && batteries.articularBatteries;
                                        const isExpanded = expandedMuscleId === m.id;
                                        return (
                                            <div key={m.id} className="rounded-lg bg-black/30 border border-white/5 overflow-hidden">
                                                {hasSubBatteries ? (
                                                    <button
                                                        onClick={() => setExpandedMuscleId(isExpanded ? null : m.id)}
                                                        className="w-full flex justify-between items-center py-2 px-3 hover:bg-white/5 transition-colors"
                                                    >
                                                        <span className="flex items-center gap-2">
                                                            <span className="text-[10px] font-bold text-zinc-300">{m.label}</span>
                                                            {isExpanded ? <ChevronDownIcon size={10} className="text-zinc-500" /> : <ChevronRightIcon size={10} className="text-zinc-500" />}
                                                        </span>
                                                        <span className={`text-[10px] font-mono font-black ${getStatusColor(perMuscle[m.id] ?? 100)}`}>{perMuscle[m.id] ?? 100}%</span>
                                                    </button>
                                                ) : (
                                                    <div className="w-full flex justify-between items-center py-2 px-3">
                                                        <span className="text-[10px] font-bold text-zinc-300">{m.label}</span>
                                                        <span className={`text-[10px] font-mono font-black ${getStatusColor(perMuscle[m.id] ?? 100)}`}>{perMuscle[m.id] ?? 100}%</span>
                                                    </div>
                                                )}
                                                {isExpanded && (
                                                    <div className="px-3 pb-3 pt-2 border-t border-white/5 mt-1 flex flex-col gap-3">
                                                        {/* Controles de Calibración Local */}
                                                        <div className="flex items-center justify-between">
                                                            <span className="text-[8px] font-black text-zinc-500 uppercase tracking-widest">Ajuste Local</span>
                                                            <div className="flex items-center gap-2">
                                                                <button
                                                                    onClick={(e) => {
                                                                        e.stopPropagation();
                                                                        const currentDeltas = settings.batteryCalibration?.muscleDeltas || {};
                                                                        const newDelta = (currentDeltas[m.id] || 0) - 5;
                                                                        setSettings({
                                                                            batteryCalibration: {
                                                                                ...(settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} }),
                                                                                muscleDeltas: { ...currentDeltas, [m.id]: newDelta }
                                                                            }
                                                                        });
                                                                    }}
                                                                    className="w-6 h-6 flex items-center justify-center bg-white/5 rounded-lg text-zinc-400 hover:text-white active:scale-95 transition-all text-xs font-black"
                                                                >-</button>
                                                                <span className={`text-[9px] font-black font-mono w-8 text-center ${(settings.batteryCalibration?.muscleDeltas?.[m.id] || 0) > 0 ? 'text-emerald-400' : (settings.batteryCalibration?.muscleDeltas?.[m.id] || 0) < 0 ? 'text-rose-400' : 'text-zinc-600'}`}>
                                                                    {(settings.batteryCalibration?.muscleDeltas?.[m.id] || 0) > 0 ? '+' : ''}{settings.batteryCalibration?.muscleDeltas?.[m.id] || 0}%
                                                                </span>
                                                                <button
                                                                    onClick={(e) => {
                                                                        e.stopPropagation();
                                                                        const currentDeltas = settings.batteryCalibration?.muscleDeltas || {};
                                                                        const newDelta = (currentDeltas[m.id] || 0) + 5;
                                                                        setSettings({
                                                                            batteryCalibration: {
                                                                                ...(settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} }),
                                                                                muscleDeltas: { ...currentDeltas, [m.id]: newDelta }
                                                                            }
                                                                        });
                                                                    }}
                                                                    className="w-6 h-6 flex items-center justify-center bg-white/5 rounded-lg text-zinc-400 hover:text-white active:scale-95 transition-all text-xs font-black"
                                                                >+</button>
                                                            </div>
                                                        </div>

                                                        {/* Sub-específicos (articulaciones) */}
                                                        {hasSubBatteries && articularIds.map(aid => {
                                                            const ab = batteries.articularBatteries?.[aid];
                                                            if (!ab) return null;
                                                            const cfg = { shoulder: 'Hombro', elbow: 'Codo', knee: 'Rodilla', hip: 'Cadera', ankle: 'Tobillo' }[aid];
                                                            return (
                                                                <div key={aid} className="flex items-center gap-2">
                                                                    <span className="text-[8px] text-zinc-500 w-14 shrink-0">{cfg}</span>
                                                                    <div className="flex-1 h-1.5 bg-zinc-800 rounded-full overflow-hidden">
                                                                        <div className="h-full rounded-full transition-all" style={{ width: `${ab.recoveryScore}%`, backgroundColor: ab.recoveryScore >= 70 ? '#10b981' : ab.recoveryScore >= 40 ? '#f59e0b' : '#ef4444' }} />
                                                                    </div>
                                                                    <span className={`text-[9px] font-mono font-black w-8 text-right ${getStatusColor(ab.recoveryScore)}`}>{ab.recoveryScore}%</span>
                                                                </div>
                                                            );
                                                        })}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                    {/* Deltoides sub-acordeón */}
                                    <div>
                                        <button
                                            onClick={() => setDeltoidsExpanded(!deltoidsExpanded)}
                                            className="w-full flex items-center justify-between text-[10px] font-bold text-zinc-300 py-2 px-3 rounded-lg bg-black/30 border border-white/5 hover:bg-white/5"
                                        >
                                            <span>Deltoides</span>
                                            {deltoidsExpanded ? <ChevronDownIcon size={10} /> : <ChevronRightIcon size={10} />}
                                        </button>
                                        {deltoidsExpanded && (
                                            <div className="ml-4 mt-1 space-y-1 border-l-2 border-rose-500/20 pl-3">
                                                {deltoidMuscles.map(m => (
                                                    <div key={m.id} className="flex justify-between items-center py-1.5">
                                                        <span className="text-[9px] text-zinc-400">{m.label}</span>
                                                        <span className={`text-[9px] font-mono font-black ${getStatusColor(perMuscle[m.id] ?? 100)}`}>{perMuscle[m.id] ?? 100}%</span>
                                                    </div>
                                                ))}
                                                {batteries.articularBatteries?.shoulder != null && (
                                                    <div className="flex justify-between items-center py-1.5 pt-2 mt-1 border-t border-white/5">
                                                        <span className="text-[8px] text-zinc-500">Hombro (tendones)</span>
                                                        <span className={`text-[9px] font-mono font-black ${getStatusColor(batteries.articularBatteries.shoulder.recoveryScore)}`}>{batteries.articularBatteries.shoulder.recoveryScore}%</span>
                                                    </div>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* COLUMNA: Ejercicios que más drenan */}
                        <div className="mt-4 pt-4 border-t border-white/5">
                            <div className="flex items-center gap-2 mb-2">
                                <TargetIcon size={12} className="text-amber-400" />
                                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-widest">COLUMNA</span>
                                <NutritionTooltip content={COLUMNA_TOOLTIP} title="Columna" />
                            </div>
                            <div className="flex gap-1 mb-2">
                                {COLUMNA_TABS.map(t => (
                                    <button
                                        key={t.id ?? 'hist'}
                                        onClick={() => setColumnaTab(t.id)}
                                        className={`px-2 py-1 rounded text-[8px] font-mono font-black uppercase transition-colors ${columnaTab === t.id ? 'bg-amber-500/20 text-amber-400 border border-amber-500/40' : 'bg-white/5 text-zinc-500 border border-white/5 hover:text-zinc-300'}`}
                                    >
                                        {t.label}
                                    </button>
                                ))}
                            </div>
                            <div className="space-y-1 max-h-24 overflow-y-auto custom-scrollbar">
                                {spinalDrain.slice(0, 8).map((e, i) => (
                                    <div key={i} className="flex justify-between text-[9px] font-mono py-1">
                                        <span className="text-zinc-400 truncate max-w-[180px]">{e.exerciseName}</span>
                                        <span className="text-amber-400 shrink-0">{e.totalSpinalDrain.toFixed(1)}%</span>
                                    </div>
                                ))}
                                {spinalDrain.length === 0 && (
                                    <p className="text-[9px] text-zinc-600 font-mono py-2">Sin datos en este período</p>
                                )}
                            </div>
                        </div>

                        {/* Veredicto */}
                        <div className="mt-4 pt-4 border-t border-white/5 flex gap-3 items-start">
                            <InfoIcon size={14} className="text-zinc-500 shrink-0 mt-0.5" />
                            <p className="text-[9px] text-zinc-400 font-medium leading-relaxed italic">"{batteries.verdict}"</p>
                        </div>

                        <p className="text-[7px] text-zinc-600 mt-2 font-mono">CÁLCULO BIOMÉTRICO — Mantén pulsado para calibrar</p>
                    </>
                )}
            </div>

            {/* Modal de calibración flotante — no compite con TabBar */}
            {calibratingId && (
                <CalibrationModal
                    id={calibratingId}
                    currentValue={
                        calibratingId === 'cns' ? batteries.cns
                            : calibratingId === 'spinal' ? batteries.spinal
                                : muscularValueTotal
                    }
                    calibValue={
                        calibratingId === 'cns' ? calibCns
                            : calibratingId === 'spinal' ? calibSpinal
                                : calibMusc
                    }
                    onCalibChange={v => setCalibValue(calibratingId, v)}
                    onSave={handleSaveCalibration}
                    onCancel={() => setCalibratingId(null)}
                />
            )}
        </div>
    );
};
