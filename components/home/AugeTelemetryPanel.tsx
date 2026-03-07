// components/home/AugeTelemetryPanel.tsx
// Material 3 — RINGS Display + alternate views (Muscular / SNC)
// Figma exact: rings = 144px diameter, 6px border, black stroke

import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { calculateGlobalBatteriesAsync } from '../../services/computeWorkerService';
import { getPerMuscleBatteries, ACCORDION_MUSCLES } from '../../services/auge';
import SkeletonLoader from '../ui/SkeletonLoader';
import { BatteryShareCard } from './BatteryShareCard';

// ─── Types & Constants ──────────────────────────────────────────────────────

type RingId = 'cns' | 'muscular' | 'spinal';
export type RingsViewMode = 'rings' | 'muscular' | 'snc';

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
const getBarTextColor = (v: number) => v >= 80 ? 'text-emerald-600' : v >= 55 ? 'text-yellow-600' : v >= 35 ? 'text-orange-500' : 'text-red-500';

// ─── Muscle Groups ──────────────────────────────────────────────────────────

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
    'espalda baja': 'Esp. Baja', 'deltoides-anterior': 'Delt. Ant.',
    'deltoides-lateral': 'Delt. Lat.', 'deltoides-posterior': 'Delt. Post.',
    'bíceps': 'Bíceps', 'tríceps': 'Tríceps', 'antebrazo': 'Antebrazo',
    'abdomen': 'Abdomen', 'core': 'Core', 'cuádriceps': 'Cuádriceps',
    'isquiosurales': 'Isquios', 'glúteos': 'Glúteos', 'pantorrillas': 'Pantorrillas',
    'aductores': 'Aductores',
};

// ─── Calibration Modal ──────────────────────────────────────────────────────

const CalibrationModal: React.FC<{
    id: RingId;
    currentValue: number;
    calibValue: number;
    onCalibChange: (v: number) => void;
    onSave: () => void;
    onCancel: () => void;
}> = ({ id, currentValue, calibValue, onCalibChange, onSave, onCancel }) => {
    const color = RING_COLORS[id];
    return (
        <div className="fixed inset-0 z-[501] flex items-end justify-center pointer-events-none" onClick={onCancel}>
            <div className="absolute inset-0 bg-black/40 backdrop-blur-sm pointer-events-auto" />
            <div className="relative w-full max-w-[340px] mb-[120px] bg-white rounded-[28px] shadow-xl overflow-hidden pointer-events-auto" onClick={e => e.stopPropagation()}>
                <div className="h-1 w-full" style={{ backgroundColor: color }} />
                <div className="p-6">
                    <div className="flex justify-between items-start mb-6">
                        <div>
                            <h3 className="text-xs font-medium text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest mb-1 font-['Roboto']">Ajuste de Precisión</h3>
                            <h2 className="text-lg font-normal text-[var(--md-sys-color-on-surface)] font-['Roboto']">
                                {id === 'cns' ? 'Sistema Nervioso Central' : id === 'spinal' ? 'Columna Vertebral' : 'Batería Muscular'}
                            </h2>
                        </div>
                        <span className="text-3xl font-medium font-mono text-[var(--md-sys-color-on-surface)] tabular-nums">{calibValue}%</span>
                    </div>
                    <input type="range" min="0" max="100" value={calibValue}
                        onChange={e => onCalibChange(parseInt(e.target.value))}
                        className="w-full h-2 rounded-full appearance-none cursor-pointer"
                        style={{ background: `linear-gradient(to right, ${color} ${calibValue}%, #E8E0DE ${calibValue}%)` }} />
                    <div className="flex justify-between mt-2 text-xs text-[var(--md-sys-color-on-surface-variant)] font-['Roboto']">
                        <span>0%</span><span>100%</span>
                    </div>
                    <div className="mt-6 flex gap-3">
                        <button onClick={onCancel} className="flex-1 py-3 text-sm font-medium text-[var(--md-sys-color-on-surface-variant)] rounded-2xl font-['Roboto']">Cancelar</button>
                        <button onClick={onSave} className="flex-1 py-3 bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] rounded-2xl text-sm font-medium active:scale-[0.98] transition-transform font-['Roboto']">Confirmar</button>
                    </div>
                </div>
            </div>
        </div>
    );
};

// ─── Main Component ─────────────────────────────────────────────────────────

export interface AugeTelemetryPanelProps {
    compact?: boolean;
    shareable?: boolean;
    variant?: 'card' | 'hero';
    viewMode?: RingsViewMode;
}

export const AugeTelemetryPanel: React.FC<AugeTelemetryPanelProps> = ({
    compact = false,
    shareable = true,
    variant = 'card',
    viewMode = 'rings',
}) => {
    const { history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, muscleHierarchy, postSessionFeedback, waterLogs, isAppLoading } = useAppState();
    const { setSettings, addToast } = useAppDispatch();

    const [batteries, setBatteries] = useState<Awaited<ReturnType<typeof calculateGlobalBatteriesAsync>> | null>(null);
    const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});
    const [focusedRing, setFocusedRing] = useState<RingId | null>(null);
    const [openGroups, setOpenGroups] = useState<Set<string>>(new Set());
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
        if (isDemo) return ACCORDION_MUSCLES.map(m => ({ id: m.id, value: 100 }));
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

    // Data fetching
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

    // Long press calibration
    const startLP = (id: RingId) => {
        if (calibratingId) return;
        wasLongPressRef.current = false;
        pressTimerRef.current = window.setTimeout(() => { wasLongPressRef.current = true; setCalibratingId(id); pressTimerRef.current = null; }, 500);
    };
    const cancelLP = () => { if (pressTimerRef.current) { clearTimeout(pressTimerRef.current); pressTimerRef.current = null; } };

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

    const toggleGroup = (label: string) => {
        setOpenGroups(prev => { const n = new Set(prev); n.has(label) ? n.delete(label) : n.add(label); return n; });
    };

    if (!batteries && !isDemo) return <SkeletonLoader lines={4} className="rounded-xl" />;

    const ringItems = [
        { id: 'cns' as RingId, value: cnsValue, color: RING_COLORS.cns },
        { id: 'muscular' as RingId, value: muscularValue, color: RING_COLORS.muscular },
        { id: 'spinal' as RingId, value: spinalValue, color: RING_COLORS.spinal },
    ];

    // ─── RINGS VIEW: Figma-exact = 144px circles, 6px border, heavy overlap ─
    const renderRingsView = () => {
        // Figma: w-36 h-36 = 144px, border-[6px] border-black
        // Middle ring container is w-2 (8px) = massive overlap
        return (
            <div className="self-stretch pl-4 inline-flex justify-start items-start gap-2 overflow-hidden">
                <div className="w-44 h-44 inline-flex flex-col justify-center items-center gap-2"
                    onClick={() => { if (!wasLongPressRef.current) setFocusedRing(focusedRing === 'cns' ? null : 'cns'); }}
                    onPointerDown={() => startLP('cns')} onPointerUp={cancelLP} onPointerLeave={cancelLP}
                    style={{ opacity: focusedRing && focusedRing !== 'cns' ? 0.15 : 1, transition: 'opacity 0.4s ease', cursor: 'pointer' }}
                >
                    <div className={`w-36 h-36 bg-white/0 rounded-full`}
                        style={{
                            borderWidth: '6px',
                            borderStyle: 'solid',
                            borderColor: focusedRing === 'cns' ? RING_COLORS.cns : 'black',
                            transition: 'border-color 0.3s ease',
                        }}
                    />
                </div>
                <div className="w-2 h-44 inline-flex flex-col justify-center items-center gap-2"
                    onClick={() => { if (!wasLongPressRef.current) setFocusedRing(focusedRing === 'muscular' ? null : 'muscular'); }}
                    onPointerDown={() => startLP('muscular')} onPointerUp={cancelLP} onPointerLeave={cancelLP}
                    style={{ opacity: focusedRing && focusedRing !== 'muscular' ? 0.15 : 1, transition: 'opacity 0.4s ease', cursor: 'pointer' }}
                >
                    <div className={`w-36 h-36 bg-white/0 rounded-full`}
                        style={{
                            borderWidth: '6px',
                            borderStyle: 'solid',
                            borderColor: focusedRing === 'muscular' ? RING_COLORS.muscular : 'black',
                            transition: 'border-color 0.3s ease',
                        }}
                    />
                </div>
                <div className="w-48 h-44 inline-flex flex-col justify-center items-center gap-2"
                    onClick={() => { if (!wasLongPressRef.current) setFocusedRing(focusedRing === 'spinal' ? null : 'spinal'); }}
                    onPointerDown={() => startLP('spinal')} onPointerUp={cancelLP} onPointerLeave={cancelLP}
                    style={{ opacity: focusedRing && focusedRing !== 'spinal' ? 0.15 : 1, transition: 'opacity 0.4s ease', cursor: 'pointer' }}
                >
                    <div className={`w-36 h-36 bg-white/0 rounded-full`}
                        style={{
                            borderWidth: '6px',
                            borderStyle: 'solid',
                            borderColor: focusedRing === 'spinal' ? RING_COLORS.spinal : 'black',
                            transition: 'border-color 0.3s ease',
                        }}
                    />
                </div>
            </div>
        );
    };

    // ─── MUSCULAR VIEW ──────────────────────────────────────────────────────
    const renderMuscularView = () => (
        <div className="py-2">
            <div className="flex items-center gap-3 mb-4">
                <div className="relative w-11 h-11 flex-shrink-0">
                    <svg viewBox="0 0 44 44" className="w-full h-full">
                        <circle cx="22" cy="22" r="18" fill="none" stroke="#E0D8D5" strokeWidth="2" />
                        <circle cx="22" cy="22" r="18" fill="none" stroke={RING_COLORS.muscular} strokeWidth="2"
                            strokeDasharray={`${(muscularValue / 100) * 2 * Math.PI * 18} ${2 * Math.PI * 18}`}
                            strokeLinecap="round" transform="rotate(-90 22 22)" />
                    </svg>
                    <span className="absolute inset-0 flex items-center justify-center text-[9px] font-medium text-[var(--md-sys-color-on-surface)] font-['Roboto']">{muscularValue}%</span>
                </div>
                <div>
                    <p className="text-base font-normal text-[var(--md-sys-color-on-surface)] font-['Roboto'] leading-6 tracking-wide">Batería Muscular</p>
                    <p className="text-sm font-normal text-[var(--md-sys-color-on-surface-variant)] font-['Roboto'] leading-5 tracking-tight">Promedio de todos los grupos</p>
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
                            <button onClick={() => toggleGroup(group.label)} className="w-full flex items-center justify-between py-2.5 px-1 rounded-xl hover:bg-black/[0.02] transition-colors">
                                <div className="flex flex-col gap-1 w-full">
                                    <div className="flex items-center justify-between">
                                        <span className="text-sm font-medium text-[var(--md-sys-color-on-surface)] font-['Roboto'] leading-5 tracking-tight">{group.label}</span>
                                        <span className={`text-sm font-medium tabular-nums ${getBarTextColor(avgVal)} font-['Roboto']`}>{avgVal}%</span>
                                    </div>
                                    <div className="w-full h-[3px] rounded-full bg-[#E0D8D5]">
                                        <div className="h-full rounded-full transition-all duration-700" style={{ width: `${avgVal}%`, backgroundColor: getBarColor(avgVal) }} />
                                    </div>
                                </div>
                            </button>
                            <div className={`transition-all duration-300 overflow-hidden ${isOpen ? 'max-h-[600px] pb-2 pl-3' : 'max-h-0'}`}>
                                {muscles.map(({ id, value }) => (
                                    <div key={id} className="py-1.5">
                                        <div className="flex items-center justify-between">
                                            <span className="text-xs font-normal text-[var(--md-sys-color-on-surface-variant)] font-['Roboto'] tracking-wide">{LABEL_MAP[id] || id}</span>
                                            <span className={`text-xs font-medium tabular-nums ${getBarTextColor(value)} font-['Roboto']`}>{value}%</span>
                                        </div>
                                        <div className="w-full h-[2px] rounded-full bg-[#E0D8D5] mt-0.5">
                                            <div className="h-full rounded-full transition-all duration-700" style={{ width: `${value}%`, backgroundColor: getBarColor(value) }} />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    );
                })}
            </div>
            {isDemo && <p className="text-xs font-normal text-[var(--md-sys-color-on-surface-variant)] text-center mt-3 italic font-['Roboto'] tracking-wide">Completa entrenamientos para datos reales</p>}
        </div>
    );

    // ─── SNC / COLUMNA VIEW ─────────────────────────────────────────────────
    const renderSNCView = () => {
        const items = [
            { id: 'cns' as RingId, value: cnsValue, label: 'Sistema Nervioso Central', color: RING_COLORS.cns, desc: RING_DESCRIPTIONS.cns },
            { id: 'spinal' as RingId, value: spinalValue, label: 'Columna Vertebral', color: RING_COLORS.spinal, desc: RING_DESCRIPTIONS.spinal },
        ];
        return (
            <div className="py-2 space-y-3">
                {items.map(({ id, value, label, color, desc }) => (
                    <div key={id} className="bg-[var(--md-sys-color-surface-container,#F5F0EE)] rounded-2xl p-4"
                        onPointerDown={() => startLP(id)} onPointerUp={cancelLP} onPointerLeave={cancelLP}>
                        <div className="flex items-center gap-3 mb-2">
                            <div className="relative w-12 h-12 flex-shrink-0">
                                <svg viewBox="0 0 48 48" className="w-full h-full">
                                    <circle cx="24" cy="24" r="20" fill="none" stroke="#E0D8D5" strokeWidth="2" />
                                    <circle cx="24" cy="24" r="20" fill="none" stroke={color} strokeWidth="2"
                                        strokeDasharray={`${(value / 100) * 2 * Math.PI * 20} ${2 * Math.PI * 20}`}
                                        strokeLinecap="round" transform="rotate(-90 24 24)" />
                                </svg>
                                <span className="absolute inset-0 flex items-center justify-center text-xs font-medium text-[var(--md-sys-color-on-surface)] font-['Roboto']">{value}%</span>
                            </div>
                            <div>
                                <p className="text-base font-normal text-[var(--md-sys-color-on-surface)] font-['Roboto'] leading-6 tracking-wide">{label}</p>
                                <p className={`text-sm font-medium ${getBarTextColor(value)} font-['Roboto'] tracking-tight`}>
                                    {value >= 80 ? 'Óptimo' : value >= 55 ? 'Moderado' : value >= 35 ? 'Fatigado' : 'Agotado'}
                                </p>
                            </div>
                        </div>
                        <p className="text-sm font-normal text-[var(--md-sys-color-on-surface-variant)] leading-5 tracking-tight font-['Roboto']">{desc}</p>
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
            <div id="auge-telemetry-panel" className="w-full">
                {viewMode === 'rings' && renderRingsView()}
                {viewMode === 'muscular' && renderMuscularView()}
                {viewMode === 'snc' && renderSNCView()}

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
