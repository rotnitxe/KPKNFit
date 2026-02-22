// components/home/BatteryCockpitWidget.tsx
// Cockpit-style battery display with long-press calibration

import React, { useState, useEffect, useRef } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { calculateGlobalBatteriesAsync } from '../../services/computeWorkerService';
import { getCachedAdaptiveData, AugeAdaptiveCache } from '../../services/augeAdaptiveService';
import { BrainIcon, ActivityIcon, TargetIcon, ZapIcon, InfoIcon } from '../icons';
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

const BatteryIndicator: React.FC<{
    id: BatteryId;
    label: string;
    value: number;
    icon: React.ReactNode;
    isCalibrating: boolean;
    calibValue: number;
    onCalibChange: (v: number) => void;
    onPointerDown: () => void;
    onPointerUp: () => void;
    onSave: () => void;
    onCancel: () => void;
}> = ({ id, label, value, icon, isCalibrating, calibValue, onCalibChange, onPointerDown, onPointerUp, onSave, onCancel }) => {
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
            <div className="flex items-center justify-center w-16 h-16 mb-2 relative">
                <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                    <path
                        className="text-zinc-800"
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                        fill="none"
                        strokeWidth="2.5"
                    />
                    <path
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                        fill="none"
                        stroke={strokeColor}
                        strokeWidth="2.5"
                        strokeDasharray={`${displayVal}, 100`}
                        strokeLinecap="round"
                        className="transition-all duration-500"
                    />
                </svg>
                <div className="absolute inset-0 flex flex-col items-center justify-center">
                    {icon}
                </div>
            </div>
            <span className={`text-xl font-black font-mono tracking-tighter ${displayColor}`}>{displayVal}%</span>
            <span className="text-[8px] text-zinc-500 font-black uppercase tracking-[0.2em] mt-1">{label}</span>

            {isCalibrating && (
                <div className="absolute inset-0 rounded-xl bg-black/90 flex flex-col items-center justify-center p-3 gap-2" onClick={e => e.stopPropagation()}>
                    <span className="text-[9px] font-black text-white uppercase">Ajustar</span>
                    <input
                        type="range"
                        min="0"
                        max="100"
                        value={calibValue}
                        onChange={e => onCalibChange(parseInt(e.target.value))}
                        className="w-full h-1.5 accent-white"
                        onClick={e => e.stopPropagation()}
                    />
                    <div className="flex gap-1.5 w-full">
                        <button onClick={onCancel} className="flex-1 py-1.5 rounded text-[8px] font-black uppercase bg-zinc-700 text-white">
                            Cancelar
                        </button>
                        <button onClick={onSave} className="flex-1 py-1.5 rounded text-[8px] font-black uppercase bg-white text-black">
                            Aplicar
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export const BatteryCockpitWidget: React.FC = () => {
    const { history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, isAppLoading } = useAppState();
    const { setSettings, addToast } = useAppDispatch();

    const [batteries, setBatteries] = useState<Awaited<ReturnType<typeof calculateGlobalBatteriesAsync>> | null>(null);
    const [adaptiveCache, setAdaptiveCache] = useState<AugeAdaptiveCache | null>(null);
    const versionRef = useRef(0);

    const [calibratingId, setCalibratingId] = useState<BatteryId | null>(null);
    const [calibCns, setCalibCns] = useState(0);
    const [calibMusc, setCalibMusc] = useState(0);
    const [calibSpinal, setCalibSpinal] = useState(0);
    const pressTimerRef = useRef<number | null>(null);

    useEffect(() => {
        if (isAppLoading || !history) {
            setBatteries(null);
            return;
        }
        const v = ++versionRef.current;
        calculateGlobalBatteriesAsync(history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList)
            .then(result => { if (versionRef.current === v) setBatteries(result); })
            .catch(() => {});
    }, [history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, isAppLoading]);

    useEffect(() => {
        setAdaptiveCache(getCachedAdaptiveData());
    }, []);

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
            batteryCalibration: { cnsDelta, muscularDelta, spinalDelta, lastCalibrated: new Date().toISOString() }
        });
        addToast("Sistema recalibrado", "success");
        setCalibratingId(null);
    };

    const handleCancelCalibration = () => {
        setCalibratingId(null);
    };

    const getCalibValue = (id: BatteryId) => {
        if (id === 'cns') return calibCns;
        if (id === 'muscular') return calibMusc;
        return calibSpinal;
    };

    const setCalibValue = (id: BatteryId, v: number) => {
        if (id === 'cns') setCalibCns(v);
        else if (id === 'muscular') setCalibMusc(v);
        else setCalibSpinal(v);
    };

    if (!batteries) return <SkeletonLoader lines={3} />;

    return (
        <div className="bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden">
            <div className="px-5 py-3 border-b border-white/5 flex justify-between items-center">
                <span className="text-[9px] font-black text-zinc-500 uppercase tracking-[0.25em] flex items-center gap-2">
                    <ZapIcon size={10} className="text-amber-400" /> LA BATERÍA
                </span>
                <span className="text-[8px] font-mono text-zinc-600">Sistemas biológicos</span>
            </div>

            <div className="p-4">
                <div className="grid grid-cols-3 gap-3">
                    <BatteryIndicator
                        id="cns"
                        label="SNC"
                        value={batteries.cns}
                        icon={<BrainIcon size={18} className="text-sky-400" />}
                        isCalibrating={calibratingId === 'cns'}
                        calibValue={calibCns}
                        onCalibChange={v => setCalibValue('cns', v)}
                        onPointerDown={() => handlePointerDown('cns')}
                        onPointerUp={handlePointerUp}
                        onSave={handleSaveCalibration}
                        onCancel={handleCancelCalibration}
                    />
                    <BatteryIndicator
                        id="muscular"
                        label="Muscular"
                        value={batteries.muscular}
                        icon={<ActivityIcon size={18} className="text-rose-400" />}
                        isCalibrating={calibratingId === 'muscular'}
                        calibValue={calibMusc}
                        onCalibChange={v => setCalibValue('muscular', v)}
                        onPointerDown={() => handlePointerDown('muscular')}
                        onPointerUp={handlePointerUp}
                        onSave={handleSaveCalibration}
                        onCancel={handleCancelCalibration}
                    />
                    <BatteryIndicator
                        id="spinal"
                        label="Axial"
                        value={batteries.spinal}
                        icon={<TargetIcon size={18} className="text-amber-400" />}
                        isCalibrating={calibratingId === 'spinal'}
                        calibValue={calibSpinal}
                        onCalibChange={v => setCalibValue('spinal', v)}
                        onPointerDown={() => handlePointerDown('spinal')}
                        onPointerUp={handlePointerUp}
                        onSave={handleSaveCalibration}
                        onCancel={handleCancelCalibration}
                    />
                </div>

                <div className="mt-4 pt-4 border-t border-white/5 flex gap-3 items-start">
                    <InfoIcon size={14} className="text-zinc-500 shrink-0 mt-0.5" />
                    <p className="text-[9px] text-zinc-400 font-medium leading-relaxed italic">"{batteries.verdict}"</p>
                </div>

                {adaptiveCache?.banister?.verdict && (
                    <div className="mt-3 pt-3 border-t border-white/5 flex items-start gap-2">
                        <ZapIcon size={10} className="text-amber-400 mt-0.5 shrink-0" />
                        <p className="text-[9px] text-zinc-500 font-medium italic">{adaptiveCache.banister.verdict}</p>
                    </div>
                )}

                <p className="text-[7px] text-zinc-600 mt-2 font-mono">Mantén pulsado para calibrar</p>
            </div>
        </div>
    );
};
