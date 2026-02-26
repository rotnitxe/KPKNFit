// components/home/BatteryHeroSection.tsx
// Hero section TÚ BATERÍA: acordeón muscular, SNC, COLUMNA con tabs

import React, { useState, useEffect, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import {
    calculateGlobalBatteriesAsync,
} from '../../services/computeWorkerService';
import {
    getPerMuscleBatteries,
    getSpinalDrainByExercise,
    ACCORDION_MUSCLES,
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

const getStatusStroke = (value: number): string => {
    if (value >= 70) return '#10b981';
    if (value >= 40) return '#f59e0b';
    return '#ef4444';
};

const BATTERIA_TOOLTIP = 'Sistema que modela tu capacidad de recuperación en 3 dimensiones: Sistema Nervioso Central, músculos y columna vertebral. Basado en AUGE.';
const SNC_TOOLTIP = 'Sistema Nervioso Central: regula la activación muscular y la coordinación. La fatiga del SNC limita tu capacidad de generar fuerza máxima y afecta la técnica bajo carga.';
const COLUMNA_TOOLTIP = 'Fatiga de la columna vertebral y tejidos conectivos. Ejercicios con carga axial (sentadilla, peso muerto, remos pesados) la drenan más. Half-life ~72h.';

const BatteryRing: React.FC<{
    id: BatteryId;
    label: string;
    value: number;
    icon: React.ReactNode;
    tooltip?: string;
    isCalibrating: boolean;
    calibValue: number;
    onCalibChange: (v: number) => void;
    onPointerDown: () => void;
    onPointerUp: () => void;
    onSave: () => void;
    onCancel: () => void;
}> = ({ id, label, value, icon, tooltip, isCalibrating, calibValue, onCalibChange, onPointerDown, onPointerUp, onSave, onCancel }) => {
    const displayVal = isCalibrating ? calibValue : value;
    const displayColor = getStatusColor(displayVal);
    const strokeColor = getStatusStroke(displayVal);

    return (
        <div
            className={`relative flex flex-col items-center justify-center p-4 rounded-xl border transition-all ${
                isCalibrating ? 'border-white/30 bg-white/5' : 'border-white/10 bg-black/30'
            }`}
            onPointerDown={onPointerDown}
            onPointerUp={onPointerUp}
            onPointerLeave={onPointerUp}
        >
            <div className="flex items-center justify-center w-14 h-14 mb-2 relative">
                <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                    <path className="text-zinc-800" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" strokeWidth="2.5" />
                    <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke={strokeColor} strokeWidth="2.5" strokeDasharray={`${displayVal}, 100`} strokeLinecap="round" className="transition-all duration-500" />
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">{icon}</div>
            </div>
            <span className={`text-lg font-black font-mono tracking-tighter ${displayColor}`}>{displayVal}%</span>
            <span className="text-[8px] text-zinc-500 font-black uppercase tracking-[0.2em] mt-1 flex items-center gap-0.5">
                {label}
                {tooltip && <NutritionTooltip content={tooltip} title={label} />}
            </span>
            {isCalibrating && (
                <div className="absolute inset-0 rounded-xl bg-black/90 flex flex-col items-center justify-center p-3 gap-2" onClick={e => e.stopPropagation()}>
                    <input type="range" min="0" max="100" value={calibValue} onChange={e => onCalibChange(parseInt(e.target.value))} className="w-full h-1.5 accent-white" onClick={e => e.stopPropagation()} />
                    <div className="flex gap-1.5 w-full">
                        <button onClick={onCancel} className="flex-1 py-1.5 rounded text-[8px] font-black uppercase bg-zinc-700 text-white">Cancelar</button>
                        <button onClick={onSave} className="flex-1 py-1.5 rounded text-[8px] font-black uppercase bg-white text-black">Aplicar</button>
                    </div>
                </div>
            )}
        </div>
    );
};

const COLUMNA_TABS: { id: number | null; label: string }[] = [
    { id: 7, label: '7d' },
    { id: 15, label: '15d' },
    { id: 30, label: '30d' },
    { id: null, label: 'Hist.' },
];

export const BatteryHeroSection: React.FC = () => {
    const { history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, muscleHierarchy, postSessionFeedback, waterLogs, isAppLoading } = useAppState();
    const { setSettings, addToast } = useAppDispatch();

    const [batteries, setBatteries] = useState<Awaited<ReturnType<typeof calculateGlobalBatteriesAsync>> | null>(null);
    const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});
    const [spinalDrain, setSpinalDrain] = useState<SpinalDrainEntry[]>([]);
    const [columnaTab, setColumnaTab] = useState<number | null>(7);
    const [muscularExpanded, setMuscularExpanded] = useState(false);
    const [deltoidsExpanded, setDeltoidsExpanded] = useState(false);
    const versionRef = React.useRef(0);

    const [calibratingId, setCalibratingId] = useState<BatteryId | null>(null);
    const [calibCns, setCalibCns] = useState(0);
    const [calibMusc, setCalibMusc] = useState(0);
    const [calibSpinal, setCalibSpinal] = useState(0);
    const pressTimerRef = React.useRef<number | null>(null);

    const showPrecalibBadge = settings.precalibrationDismissed && !settings.hasPrecalibratedBattery;

    useEffect(() => {
        if (isAppLoading || !history) {
            setBatteries(null);
            setPerMuscle({});
            return;
        }
        const v = ++versionRef.current;
        calculateGlobalBatteriesAsync(history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList)
            .then(result => { if (versionRef.current === v) setBatteries(result); })
            .catch(() => {});
    }, [history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, isAppLoading]);

    useEffect(() => {
        if (isAppLoading || !history) return;
        try {
            const hierarchy = muscleHierarchy || { bodyPartHierarchy: {}, specialCategories: {}, muscleToBodyPart: {} };
            const pm = getPerMuscleBatteries(
                history,
                exerciseList,
                sleepLogs || [],
                settings,
                hierarchy,
                postSessionFeedback || [],
                waterLogs || [],
                dailyWellbeingLogs || [],
                nutritionLogs || []
            );
            setPerMuscle(pm);
        } catch {
            setPerMuscle({});
        }
    }, [history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs, dailyWellbeingLogs, nutritionLogs, isAppLoading]);

    useEffect(() => {
        if (!history || !exerciseList) return;
        const sd = getSpinalDrainByExercise(history, exerciseList, columnaTab, settings);
        setSpinalDrain(sd);
    }, [history, exerciseList, columnaTab, settings]);

    const handlePointerDown = (id: BatteryId) => {
        if (calibratingId) return;
        pressTimerRef.current = window.setTimeout(() => {
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
        addToast('Sistema recalibrado', 'success');
        setCalibratingId(null);
    };

    const setCalibValue = (id: BatteryId, v: number) => {
        if (id === 'cns') setCalibCns(v);
        else if (id === 'muscular') setCalibMusc(v);
        else setCalibSpinal(v);
    };

    const mainMuscles = ACCORDION_MUSCLES.filter(m => !m.isDeltoidPortion);
    const deltoidMuscles = ACCORDION_MUSCLES.filter(m => m.isDeltoidPortion);

    if (!batteries) return <SkeletonLoader lines={4} />;

    return (
        <div className="bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden relative">
            {showPrecalibBadge && (
                <div className="absolute top-3 right-3 z-10">
                    <span className="px-2 py-0.5 rounded text-[8px] font-black uppercase bg-amber-500/20 text-amber-400 border border-amber-500/40">
                        Calibrar
                    </span>
                </div>
            )}

            <div className="px-5 py-3 border-b border-white/5 flex justify-between items-center">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.25em] flex items-center gap-2">
                    <ZapIcon size={10} className="text-amber-400" /> TÚ BATERÍA
                    <NutritionTooltip content={BATTERIA_TOOLTIP} title="Tú Batería" />
                </span>
                <span className="text-[8px] font-mono text-zinc-600">Sistemas biológicos</span>
            </div>

            <div className="p-4">
                {/* 3 anillos principales */}
                <div className="grid grid-cols-3 gap-3">
                    <BatteryRing
                        id="cns"
                        label="SNC"
                        value={batteries.cns}
                        icon={<BrainIcon size={16} className="text-sky-400" />}
                        tooltip={SNC_TOOLTIP}
                        isCalibrating={calibratingId === 'cns'}
                        calibValue={calibCns}
                        onCalibChange={v => setCalibValue('cns', v)}
                        onPointerDown={() => handlePointerDown('cns')}
                        onPointerUp={handlePointerUp}
                        onSave={handleSaveCalibration}
                        onCancel={() => setCalibratingId(null)}
                    />
                    <BatteryRing
                        id="muscular"
                        label="Muscular"
                        value={batteries.muscular}
                        icon={<ActivityIcon size={16} className="text-rose-400" />}
                        isCalibrating={calibratingId === 'muscular'}
                        calibValue={calibMusc}
                        onCalibChange={v => setCalibValue('muscular', v)}
                        onPointerDown={() => handlePointerDown('muscular')}
                        onPointerUp={handlePointerUp}
                        onSave={handleSaveCalibration}
                        onCancel={() => setCalibratingId(null)}
                    />
                    <BatteryRing
                        id="spinal"
                        label="COLUMNA"
                        value={batteries.spinal}
                        icon={<TargetIcon size={16} className="text-amber-400" />}
                        tooltip={COLUMNA_TOOLTIP}
                        isCalibrating={calibratingId === 'spinal'}
                        calibValue={calibSpinal}
                        onCalibChange={v => setCalibValue('spinal', v)}
                        onPointerDown={() => handlePointerDown('spinal')}
                        onPointerUp={handlePointerUp}
                        onSave={handleSaveCalibration}
                        onCancel={() => setCalibratingId(null)}
                    />
                </div>

                {/* Desglose consumo (auditLogs) */}
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
                            {mainMuscles.map(m => (
                                <div key={m.id} className="flex justify-between items-center py-2 px-3 rounded-lg bg-black/30 border border-white/5">
                                    <span className="text-[10px] font-bold text-zinc-300">{m.label}</span>
                                    <span className={`text-[10px] font-mono font-black ${getStatusColor(perMuscle[m.id] ?? 100)}`}>{perMuscle[m.id] ?? 100}%</span>
                                </div>
                            ))}
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

                <p className="text-[7px] text-zinc-600 mt-2 font-mono">Mantén pulsado un anillo para calibrar</p>
            </div>
        </div>
    );
};
