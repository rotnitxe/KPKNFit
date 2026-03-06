// components/home/AugeTelemetryPanel.tsx
// Material 3 — RINGS Dashboard — Diseño premium con toolbar de íconos
// 3 vistas: Anillos horizontales | Desglose muscular | SNC y Columna

import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { calculateGlobalBatteriesAsync } from '../../services/computeWorkerService';
import { getPerMuscleBatteries, ACCORDION_MUSCLES } from '../../services/auge';
import SkeletonLoader from '../ui/SkeletonLoader';
import { shareElementAsImage } from '../../services/shareService';
import { ChevronDownIcon } from '../icons';
import { BatteryShareCard } from './BatteryShareCard';

// ─── CONSTANTES ─────────────────────────────────────────────────────────────

type RingId = 'cns' | 'muscular' | 'spinal';
type TabView = 'rings' | 'muscular' | 'snc';

const RING_COLORS = {
    cns: '#38bdf8',
    muscular: '#f472b6',
    spinal: '#fb923c',
} as const;

const RING_DESCRIPTIONS: Record<string, string> = {
    cns: "Tu procesador central. Fatiga por esfuerzo mental, falta de sueño y carga pesada.",
    muscular: "Estado de tus tejidos. Daño por microrroturas y agotamiento de glucógeno.",
    spinal: "Integridad de tu eje axial y tejido conectivo. Cargas de compresión acumuladas.",
};

const getBarColor = (v: number) => v >= 80 ? '#22c55e' : v >= 55 ? '#eab308' : v >= 35 ? '#f97316' : '#ef4444';
const getBarTextLight = (v: number) => v >= 80 ? 'text-emerald-600' : v >= 55 ? 'text-yellow-600' : v >= 35 ? 'text-orange-500' : 'text-red-500';

// ─── TOOLBAR ICONS (SVG inline — premium, visual) ───────────────────────────

const RingsIcon: React.FC<{ active: boolean }> = ({ active }) => (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
        <circle cx="7.5" cy="12" r="5" stroke={active ? '#1C1B1F' : '#9E9E9E'} strokeWidth="1.5" />
        <circle cx="12" cy="12" r="5" stroke={active ? '#1C1B1F' : '#9E9E9E'} strokeWidth="1.5" />
        <circle cx="16.5" cy="12" r="5" stroke={active ? '#1C1B1F' : '#9E9E9E'} strokeWidth="1.5" />
    </svg>
);

const MuscleIcon: React.FC<{ active: boolean }> = ({ active }) => (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke={active ? '#1C1B1F' : '#9E9E9E'} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M6.5 6C4 8.5 3 14 5 18c1 2 3 2.5 4 2s2-2 3-2 2 1.5 3 2 3 0 4-2c2-4 1-9.5-1.5-12" />
        <path d="M9 11c0 1.5.5 3 1.5 4.5M15 11c0 1.5-.5 3-1.5 4.5" />
        <path d="M12 7v4" />
    </svg>
);

const BrainSpineIcon: React.FC<{ active: boolean }> = ({ active }) => (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke={active ? '#1C1B1F' : '#9E9E9E'} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 3c-1.5 0-3 .8-3.5 2-.8-.3-2 0-2.5 1-.7 1.2-.2 2.5.5 3.2-.5.8-.5 2 .2 2.8.5.6 1.3 1 2.3 1h.5" />
        <path d="M12 3c1.5 0 3 .8 3.5 2 .8-.3 2 0 2.5 1 .7 1.2.2 2.5-.5 3.2.5.8.5 2-.2 2.8-.5.6-1.3 1-2.3 1h-.5" />
        <path d="M12 13v2m0 2v2m0-6c-1 0-2 .5-2 1.5s1 1.5 2 1.5 2-.5 2-1.5-1-1.5-2-1.5z" />
        <circle cx="12" cy="21" r="1" fill={active ? '#1C1B1F' : '#9E9E9E'} stroke="none" />
    </svg>
);

// ─── AGRUPACIÓN ANATÓMICA ───────────────────────────────────────────────────

interface MuscleGroup { label: string; ids: string[]; }

const MUSCLE_GROUPS: MuscleGroup[] = [
    { label: 'Pecho', ids: ['pectorales'] },
    { label: 'Espalda', ids: ['dorsales', 'trapecio', 'espalda baja'] },
    { label: 'Hombros', ids: ['deltoides-anterior', 'deltoides-lateral', 'deltoides-posterior'] },
    { label: 'Brazos', ids: ['bíceps', 'tríceps', 'antebrazo'] },
    { label: 'Core', ids: ['abdomen', 'core'] },
    { label: 'Piernas', ids: ['cuádriceps', 'isquiosurales', 'glúteos', 'pantorrillas', 'aductores'] },
];

const LABEL_MAP: Record<string, string> = {
    'pectorales': 'Pectorales', 'dorsales': 'Dorsales', 'trapecio': 'Trapecio',
    'espalda baja': 'Esp. Baja', 'deltoides-anterior': 'Delt. Anterior',
    'deltoides-lateral': 'Delt. Lateral', 'deltoides-posterior': 'Delt. Posterior',
    'bíceps': 'Bíceps', 'tríceps': 'Tríceps', 'antebrazo': 'Antebrazo',
    'abdomen': 'Abdomen', 'core': 'Core', 'cuádriceps': 'Cuádriceps',
    'isquiosurales': 'Isquios', 'glúteos': 'Glúteos', 'pantorrillas': 'Pantorrillas',
    'aductores': 'Aductores',
};

const MOCK_MUSCLES: { id: string; value: number }[] = [
    { id: 'pectorales', value: 100 }, { id: 'deltoides-lateral', value: 100 },
    { id: 'dorsales', value: 100 }, { id: 'isquiosurales', value: 100 },
    { id: 'cuádriceps', value: 100 }, { id: 'bíceps', value: 100 },
    { id: 'tríceps', value: 100 }, { id: 'glúteos', value: 100 },
    { id: 'trapecio', value: 100 }, { id: 'abdomen', value: 100 },
    { id: 'antebrazo', value: 100 }, { id: 'deltoides-anterior', value: 100 },
    { id: 'deltoides-posterior', value: 100 }, { id: 'pantorrillas', value: 100 },
    { id: 'core', value: 100 }, { id: 'espalda baja', value: 100 },
    { id: 'aductores', value: 100 },
];

// ─── Modal de Calibración ───────────────────────────────────────────────────

const CalibrationModal: React.FC<{
    id: RingId;
    currentValue: number;
    calibValue: number;
    onCalibChange: (v: number) => void;
    onSave: () => void;
    onCancel: () => void;
}> = ({ id, currentValue, calibValue, onCalibChange, onSave, onCancel }) => {
    const color = RING_COLORS[id];
    const diff = calibValue - currentValue;
    return (
        <div className="fixed inset-0 z-[501] flex items-end justify-center pointer-events-none" onClick={onCancel}>
            <div className="absolute inset-0 bg-black/40 backdrop-blur-sm pointer-events-auto animate-fade-in" />
            <div className="relative w-full max-w-[340px] mb-[120px] bg-white rounded-[28px] shadow-[0_8px_32px_rgba(0,0,0,0.12)] overflow-hidden pointer-events-auto animate-slide-up" onClick={e => e.stopPropagation()}>
                <div className="h-1 w-full" style={{ backgroundColor: color }} />
                <div className="p-6">
                    <div className="flex justify-between items-start mb-6">
                        <div>
                            <h3 className="text-[10px] font-bold text-[#79747E] uppercase tracking-[0.15em] mb-1">Ajuste de Precisión</h3>
                            <h2 className="text-lg font-bold text-[#1C1B1F]">
                                {id === 'cns' ? 'Sistema Nervioso Central' : id === 'spinal' ? 'Columna Vertebral' : 'Batería Muscular'}
                            </h2>
                        </div>
                        <span className="text-3xl font-black font-mono text-[#1C1B1F] tabular-nums">{calibValue}%</span>
                    </div>
                    <input type="range" min="0" max="100" value={calibValue}
                        onChange={e => onCalibChange(parseInt(e.target.value))}
                        className="w-full h-1.5 bg-[#E8E0DE] rounded-full appearance-none cursor-pointer"
                        style={{ background: `linear-gradient(to right, ${color} ${calibValue}%, #E8E0DE ${calibValue}%)` }} />
                    <div className="flex justify-between mt-2 text-[9px] text-[#79747E] font-medium">
                        <span>0%</span><span>100%</span>
                    </div>
                    <div className="mt-6 flex gap-3">
                        <button onClick={onCancel} className="flex-1 py-3 text-[12px] font-bold text-[#79747E] hover:text-[#1C1B1F] transition-colors rounded-2xl">Cancelar</button>
                        <button onClick={onSave} className="flex-1 py-3 bg-[#1C1B1F] text-white rounded-2xl text-[12px] font-bold active:scale-[0.98] transition-all">Confirmar</button>
                    </div>
                </div>
            </div>
        </div>
    );
};

// ─── COMPONENTE PRINCIPAL ───────────────────────────────────────────────────

export interface AugeTelemetryPanelProps {
    compact?: boolean;
    shareable?: boolean;
    variant?: 'card' | 'hero';
}

export const AugeTelemetryPanel: React.FC<AugeTelemetryPanelProps> = ({ compact = false, shareable = true, variant = 'card' }) => {
    const { history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, muscleHierarchy, postSessionFeedback, waterLogs, isAppLoading } = useAppState();
    const { setSettings, addToast } = useAppDispatch();

    const [batteries, setBatteries] = useState<Awaited<ReturnType<typeof calculateGlobalBatteriesAsync>> | null>(null);
    const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});
    const [activeTab, setActiveTab] = useState<TabView>('rings');
    const [focusedRing, setFocusedRing] = useState<RingId | null>(null);
    const [openGroups, setOpenGroups] = useState<Set<string>>(new Set());
    const [expandedMuscleId, setExpandedMuscleId] = useState<string | null>(null);
    const [calibratingId, setCalibratingId] = useState<RingId | null>(null);
    const [calibCns, setCalibCns] = useState(0);
    const [calibMusc, setCalibMusc] = useState(0);
    const [calibSpinal, setCalibSpinal] = useState(0);
    const pressTimerRef = useRef<number | null>(null);
    const wasLongPressRef = useRef(false);
    const versionRef = useRef(0);

    const isDemo = useMemo(() => {
        if (!batteries || !perMuscle) return true;
        return !(history && history.length > 0) && !Object.keys(perMuscle).some(k => (perMuscle[k] ?? 100) !== 100);
    }, [batteries, perMuscle, history]);

    const muscleData = useMemo(() => {
        if (isDemo) return MOCK_MUSCLES;
        return ACCORDION_MUSCLES.map(m => ({ id: m.id, value: Math.round(perMuscle[m.id] ?? 100) }));
    }, [perMuscle, isDemo]);
    const muscleMap = useMemo(() => new Map(muscleData.map(m => [m.id, m.value])), [muscleData]);

    const cnsValue = useMemo(() => !batteries ? 82 : Math.round(batteries.cns), [batteries]);
    const muscularValue = useMemo(() => {
        if (isDemo) return 100;
        const vals = Object.values(perMuscle);
        return vals.length === 0 ? 100 : Math.round(vals.reduce((a, b) => a + b, 0) / vals.length);
    }, [perMuscle, isDemo]);
    const spinalValue = useMemo(() => !batteries ? 88 : Math.round(batteries.spinal), [batteries]);

    useEffect(() => {
        if (isAppLoading || !history) { setBatteries(null); setPerMuscle({}); return; }
        const v = ++versionRef.current;
        calculateGlobalBatteriesAsync(history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList)
            .then(r => { if (versionRef.current === v) setBatteries(r); }).catch(() => { });
    }, [history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, isAppLoading]);

    useEffect(() => {
        if (isAppLoading || !history) return;
        try {
            const hierarchy = muscleHierarchy || { bodyPartHierarchy: {}, specialCategories: {}, muscleToBodyPart: {} };
            setPerMuscle(getPerMuscleBatteries(history, exerciseList, sleepLogs || [], settings, hierarchy,
                postSessionFeedback || [], waterLogs || [], dailyWellbeingLogs || [], nutritionLogs || []));
        } catch { setPerMuscle({}); }
    }, [history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs, dailyWellbeingLogs, nutritionLogs, isAppLoading]);

    useEffect(() => {
        if (batteries && calibratingId) {
            if (calibratingId === 'cns') setCalibCns(batteries.cns);
            if (calibratingId === 'muscular') setCalibMusc(muscularValue);
            if (calibratingId === 'spinal') setCalibSpinal(batteries.spinal);
        }
    }, [batteries, calibratingId, muscularValue]);

    const startLongPress = (id: RingId) => {
        if (calibratingId) return;
        wasLongPressRef.current = false;
        pressTimerRef.current = window.setTimeout(() => { wasLongPressRef.current = true; setCalibratingId(id); pressTimerRef.current = null; }, 500);
    };
    const cancelLongPress = () => { if (pressTimerRef.current) { clearTimeout(pressTimerRef.current); pressTimerRef.current = null; } };

    const handleSaveCalibration = () => {
        if (!batteries || !calibratingId) return;
        const cur = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} };
        let cnsDelta = cur.cnsDelta || 0, spinalDelta = cur.spinalDelta || 0;
        if (calibratingId === 'cns') cnsDelta = calibCns - (cnsValue - cnsDelta);
        if (calibratingId === 'spinal') spinalDelta = calibSpinal - (spinalValue - spinalDelta);
        setSettings({ batteryCalibration: { cnsDelta, muscularDelta: cur.muscularDelta || 0, spinalDelta, muscleDeltas: cur.muscleDeltas, lastCalibrated: new Date().toISOString() }, hasPrecalibratedBattery: true });
        addToast(`${calibratingId.toUpperCase()} recalibrado`, 'success');
        setCalibratingId(null);
    };

    const toggleGroup = (e: React.MouseEvent, label: string) => {
        e.stopPropagation();
        setOpenGroups(prev => { const n = new Set(prev); n.has(label) ? n.delete(label) : n.add(label); return n; });
    };

    if (!batteries && !isDemo) return <SkeletonLoader lines={4} className="rounded-xl" />;

    const ringItems = [
        { id: 'cns' as RingId, value: cnsValue, label: 'SNC', color: RING_COLORS.cns },
        { id: 'muscular' as RingId, value: muscularValue, label: 'Muscular', color: RING_COLORS.muscular },
        { id: 'spinal' as RingId, value: spinalValue, label: 'Columna', color: RING_COLORS.spinal },
    ];

    // ─── Rings View: Anillos elegantes entrelazados (estilo Figma) ───────────
    const renderRingsView = () => {
        // Radii y posiciones para 3 anillos entrelazados horizontalmente
        const R = 44; // radio de cada anillo
        const C = 2 * Math.PI * R;
        const overlap = 18; // cuánto se superponen
        const cx = [R + 2, R + 2 + (2 * R - overlap), R + 2 + 2 * (2 * R - overlap)];
        const cy = R + 8;
        const svgW = cx[2] + R + 2;
        const svgH = cy + R + 8;

        return (
            <div className="flex flex-col items-center py-4">
                <svg viewBox={`0 0 ${svgW} ${svgH}`} className="w-full max-w-[320px]" style={{ overflow: 'visible' }}>
                    {ringItems.map(({ id, value, color }, i) => {
                        const isFocused = focusedRing === id;
                        const isFaded = focusedRing !== null && !isFocused;
                        const strokeW = isFocused ? 2.8 : 2;
                        const progress = (value / 100) * C;

                        return (
                            <g key={id} style={{
                                opacity: isFaded ? 0.2 : 1, cursor: 'pointer',
                                transition: 'opacity 0.5s ease, filter 0.5s ease',
                                filter: isFocused ? `drop-shadow(0 0 8px ${color}40)` : 'none',
                            }}
                                onClick={e => { e.stopPropagation(); if (wasLongPressRef.current) return; setFocusedRing(isFocused ? null : id); }}
                                onPointerDown={() => startLongPress(id)} onPointerUp={cancelLongPress} onPointerLeave={cancelLongPress}
                            >
                                {/* Track */}
                                <circle cx={cx[i]} cy={cy} r={R} fill="none" stroke="#E0D8D5" strokeWidth={strokeW}
                                    transform={`rotate(-90, ${cx[i]}, ${cy})`} />
                                {/* Progress arc */}
                                <circle cx={cx[i]} cy={cy} r={R} fill="none"
                                    stroke={isFocused ? color : '#1C1B1F'}
                                    strokeWidth={strokeW}
                                    strokeDasharray={`${progress} ${C}`}
                                    strokeLinecap="round"
                                    transform={`rotate(-90, ${cx[i]}, ${cy})`}
                                    style={{ transition: 'stroke-dasharray 0.8s ease-out, stroke 0.4s ease, stroke-width 0.3s ease' }}
                                />
                            </g>
                        );
                    })}
                </svg>

                {/* Leyenda debajo de los anillos */}
                <div className="flex items-center justify-center gap-6 mt-3">
                    {ringItems.map(({ id, value, label, color }) => {
                        const isFocused = focusedRing === id;
                        return (
                            <button key={id}
                                onClick={() => setFocusedRing(isFocused ? null : id)}
                                className="flex flex-col items-center gap-0.5 transition-opacity duration-300"
                                style={{ opacity: focusedRing && !isFocused ? 0.3 : 1 }}
                            >
                                <span className="text-[14px] font-black text-[#1C1B1F] tabular-nums" style={{ color: isFocused ? color : '#1C1B1F' }}>{value}%</span>
                                <span className="text-[10px] font-medium text-[#79747E]">{label}</span>
                            </button>
                        );
                    })}
                </div>

                {/* Descripción del ring enfocado */}
                <div className={`overflow-hidden transition-all duration-400 ${focusedRing ? 'max-h-24 opacity-100 mt-3' : 'max-h-0 opacity-0 mt-0'}`}>
                    <div className="bg-[#F5F0EE] rounded-2xl px-4 py-3">
                        <p className="text-[11px] text-[#49454F] leading-relaxed text-center">
                            {focusedRing && RING_DESCRIPTIONS[focusedRing]}
                        </p>
                    </div>
                </div>
            </div>
        );
    };

    // ─── Muscular View ──────────────────────────────────────────────────────
    const renderMuscularView = () => (
        <div className="py-3">
            <div className="flex items-center gap-3 mb-4">
                <div className="relative w-11 h-11 flex-shrink-0">
                    <svg viewBox="0 0 44 44" className="w-full h-full">
                        <circle cx="22" cy="22" r="18" fill="none" stroke="#E0D8D5" strokeWidth="2" />
                        <circle cx="22" cy="22" r="18" fill="none" stroke={RING_COLORS.muscular} strokeWidth="2"
                            strokeDasharray={`${(muscularValue / 100) * 2 * Math.PI * 18} ${2 * Math.PI * 18}`}
                            strokeLinecap="round" transform="rotate(-90 22 22)"
                            style={{ transition: 'stroke-dasharray 0.7s ease-out' }} />
                    </svg>
                    <span className="absolute inset-0 flex items-center justify-center text-[9px] font-black text-[#1C1B1F]">{muscularValue}%</span>
                </div>
                <div>
                    <p className="text-[14px] font-bold text-[#1C1B1F]">Batería Muscular</p>
                    <p className="text-[11px] text-[#79747E]">Promedio de todos los grupos</p>
                </div>
            </div>
            <div className="space-y-0.5">
                {MUSCLE_GROUPS.map(group => {
                    const muscles = group.ids.map(id => { const v = muscleMap.get(id); return v !== undefined ? { id, value: v } : null; }).filter(Boolean) as { id: string; value: number }[];
                    if (!muscles.length) return null;
                    const avgVal = Math.round(muscles.reduce((s, m) => s + m.value, 0) / muscles.length);
                    const isOpen = openGroups.has(group.label);
                    return (
                        <div key={group.label}>
                            <button onClick={e => toggleGroup(e, group.label)}
                                className="w-full flex items-center justify-between py-2.5 px-1 hover:bg-black/[0.02] transition-colors rounded-xl">
                                <div className="flex flex-col gap-1 w-full">
                                    <div className="flex items-center justify-between">
                                        <span className="text-[13px] font-semibold text-[#1C1B1F]">{group.label}</span>
                                        <span className={`text-[12px] font-bold font-mono ${getBarTextLight(avgVal)}`}>{avgVal}%</span>
                                    </div>
                                    <div className="w-full h-[2.5px] rounded-full bg-[#E0D8D5]">
                                        <div className="h-full rounded-full transition-all duration-700" style={{ width: `${avgVal}%`, backgroundColor: getBarColor(avgVal) }} />
                                    </div>
                                </div>
                            </button>
                            <div className={`transition-all duration-300 overflow-hidden ${isOpen ? 'max-h-[600px] pb-2 pl-3' : 'max-h-0'}`}>
                                {muscles.map(({ id, value }) => {
                                    const isExpanded = expandedMuscleId === id;
                                    return (
                                        <div key={id} className="py-1.5">
                                            <button onClick={() => setExpandedMuscleId(isExpanded ? null : id)} className="flex flex-col gap-1 text-left w-full">
                                                <div className="flex items-center justify-between w-full">
                                                    <span className="text-[11px] text-[#49454F]">{LABEL_MAP[id] || id}</span>
                                                    <span className={`text-[10px] font-bold font-mono ${getBarTextLight(value)}`}>{value}%</span>
                                                </div>
                                                <div className="w-full h-[2px] rounded-full bg-[#E0D8D5]">
                                                    <div className="h-full rounded-full transition-all duration-700" style={{ width: `${value}%`, backgroundColor: getBarColor(value) }} />
                                                </div>
                                            </button>
                                            {isExpanded && (
                                                <div className="mt-1.5 p-2.5 bg-[#F5F0EE] rounded-xl animate-fade-in">
                                                    <div className="flex items-center justify-between">
                                                        <span className="text-[9px] font-bold text-[#79747E] uppercase tracking-wider">Ajuste</span>
                                                        <div className="flex items-center gap-2">
                                                            <button onClick={() => { const d = settings.batteryCalibration?.muscleDeltas || {}; setSettings({ batteryCalibration: { ...(settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} }), muscleDeltas: { ...d, [id]: (d[id] || 0) - 5 } } }); }}
                                                                className="w-6 h-6 flex items-center justify-center bg-white rounded-lg text-[#49454F] text-xs font-bold shadow-sm active:scale-95">−</button>
                                                            <span className={`text-[10px] font-bold font-mono w-7 text-center ${(settings.batteryCalibration?.muscleDeltas?.[id] || 0) > 0 ? 'text-emerald-600' : (settings.batteryCalibration?.muscleDeltas?.[id] || 0) < 0 ? 'text-rose-500' : 'text-[#79747E]'}`}>
                                                                {(settings.batteryCalibration?.muscleDeltas?.[id] || 0) > 0 ? '+' : ''}{settings.batteryCalibration?.muscleDeltas?.[id] || 0}
                                                            </span>
                                                            <button onClick={() => { const d = settings.batteryCalibration?.muscleDeltas || {}; setSettings({ batteryCalibration: { ...(settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} }), muscleDeltas: { ...d, [id]: (d[id] || 0) + 5 } } }); }}
                                                                className="w-6 h-6 flex items-center justify-center bg-white rounded-lg text-[#49454F] text-xs font-bold shadow-sm active:scale-95">+</button>
                                                        </div>
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    );
                })}
            </div>
            {isDemo && <p className="text-[10px] text-[#79747E] text-center mt-3 italic">Completa entrenamientos para datos reales</p>}
        </div>
    );

    // ─── SNC / Columna View ─────────────────────────────────────────────────
    const renderSNCView = () => {
        const items = [
            { id: 'cns' as RingId, value: cnsValue, label: 'Sistema Nervioso Central', color: RING_COLORS.cns, desc: RING_DESCRIPTIONS.cns },
            { id: 'spinal' as RingId, value: spinalValue, label: 'Columna Vertebral', color: RING_COLORS.spinal, desc: RING_DESCRIPTIONS.spinal },
        ];
        return (
            <div className="py-3 space-y-3">
                {items.map(({ id, value, label, color, desc }) => (
                    <div key={id} className="bg-[#F5F0EE] rounded-2xl p-4"
                        onPointerDown={() => startLongPress(id)} onPointerUp={cancelLongPress} onPointerLeave={cancelLongPress}>
                        <div className="flex items-center gap-3 mb-2.5">
                            <div className="relative w-14 h-14 flex-shrink-0">
                                <svg viewBox="0 0 56 56" className="w-full h-full">
                                    <circle cx="28" cy="28" r="23" fill="none" stroke="#E0D8D5" strokeWidth="2.5" />
                                    <circle cx="28" cy="28" r="23" fill="none" stroke={color} strokeWidth="2.5"
                                        strokeDasharray={`${(value / 100) * 2 * Math.PI * 23} ${2 * Math.PI * 23}`}
                                        strokeLinecap="round" transform="rotate(-90 28 28)"
                                        style={{ transition: 'stroke-dasharray 0.7s ease-out' }} />
                                </svg>
                                <span className="absolute inset-0 flex items-center justify-center text-[13px] font-black text-[#1C1B1F]">{value}%</span>
                            </div>
                            <div>
                                <p className="text-[14px] font-bold text-[#1C1B1F]">{label}</p>
                                <p className={`text-[12px] font-semibold ${getBarTextLight(value)}`}>
                                    {value >= 80 ? 'Óptimo' : value >= 55 ? 'Moderado' : value >= 35 ? 'Fatigado' : 'Agotado'}
                                </p>
                            </div>
                        </div>
                        <p className="text-[11px] text-[#49454F] leading-relaxed">{desc}</p>
                    </div>
                ))}
            </div>
        );
    };

    return (
        <>
            <div className="fixed -left-[9999px] top-0 w-[540px] h-[960px] pointer-events-none overflow-hidden z-0">
                <BatteryShareCard cns={cnsValue} muscular={muscularValue} spinal={spinalValue} />
            </div>

            <div id="auge-telemetry-panel" className="w-full relative">
                {/* ── Icon Toolbar — 3 vistas ── */}
                <div className="flex items-center justify-center gap-1 mb-2">
                    {([
                        { id: 'rings' as TabView, Icon: RingsIcon, tip: 'Rings' },
                        { id: 'muscular' as TabView, Icon: MuscleIcon, tip: 'Muscular' },
                        { id: 'snc' as TabView, Icon: BrainSpineIcon, tip: 'SNC / Columna' },
                    ]).map(({ id, Icon, tip }) => {
                        const isActive = activeTab === id;
                        return (
                            <button key={id}
                                onClick={() => { setActiveTab(id); setFocusedRing(null); }}
                                className={`
                                    relative w-11 h-11 flex items-center justify-center rounded-full transition-all duration-300
                                    ${isActive
                                        ? 'bg-[#1C1B1F] shadow-[0_2px_8px_rgba(0,0,0,0.15)]'
                                        : 'bg-transparent hover:bg-[#E8E0DE]'
                                    }
                                `}
                                title={tip}
                            >
                                <Icon active={!isActive} />
                                {isActive && (
                                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" className="absolute">
                                        {id === 'rings' && <>
                                            <circle cx="7.5" cy="12" r="5" stroke="white" strokeWidth="1.5" />
                                            <circle cx="12" cy="12" r="5" stroke="white" strokeWidth="1.5" />
                                            <circle cx="16.5" cy="12" r="5" stroke="white" strokeWidth="1.5" />
                                        </>}
                                        {id === 'muscular' && <path d="M6.5 6C4 8.5 3 14 5 18c1 2 3 2.5 4 2s2-2 3-2 2 1.5 3 2 3 0 4-2c2-4 1-9.5-1.5-12M9 11c0 1.5.5 3 1.5 4.5M15 11c0 1.5-.5 3-1.5 4.5M12 7v4" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />}
                                        {id === 'snc' && <>
                                            <path d="M12 3c-1.5 0-3 .8-3.5 2-.8-.3-2 0-2.5 1-.7 1.2-.2 2.5.5 3.2-.5.8-.5 2 .2 2.8.5.6 1.3 1 2.3 1h.5M12 3c1.5 0 3 .8 3.5 2 .8-.3 2 0 2.5 1 .7 1.2.2 2.5-.5 3.2.5.8.5 2-.2 2.8-.5.6-1.3 1-2.3 1h-.5M12 13v2m0 2v2m0-6c-1 0-2 .5-2 1.5s1 1.5 2 1.5 2-.5 2-1.5-1-1.5-2-1.5z" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                                            <circle cx="12" cy="21" r="1" fill="white" />
                                        </>}
                                    </svg>
                                )}
                            </button>
                        );
                    })}
                </div>

                {/* ── Content ── */}
                {activeTab === 'rings' && renderRingsView()}
                {activeTab === 'muscular' && renderMuscularView()}
                {activeTab === 'snc' && renderSNCView()}

                {/* Calibration Modal */}
                {calibratingId && batteries && (
                    <CalibrationModal id={calibratingId}
                        currentValue={calibratingId === 'cns' ? cnsValue : calibratingId === 'muscular' ? muscularValue : spinalValue}
                        calibValue={calibratingId === 'cns' ? calibCns : calibratingId === 'muscular' ? calibMusc : calibSpinal}
                        onCalibChange={v => { if (calibratingId === 'cns') setCalibCns(v); if (calibratingId === 'muscular') setCalibMusc(v); if (calibratingId === 'spinal') setCalibSpinal(v); }}
                        onSave={handleSaveCalibration} onCancel={() => setCalibratingId(null)} />
                )}
            </div>
        </>
    );
};
