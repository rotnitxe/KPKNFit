// components/home/AugeTelemetryPanel.tsx
// Componente estrella pantalla "Tú" — Estética seria, densidad informativa
// DESIGN SYSTEM "TÚ" — Define la estética de toda la app.
//   · Hero bg: #1a1a1a  |  Page bg: #121212
//   · Sin tarjetas a menos que se pida explícitamente
//   · Accent rings: sky (#38bdf8), pink (#f472b6), orange (#fb923c)
//   · Barras status: emerald ≥80%, yellow ≥55%, orange ≥35%, red <35%
//   · Tipografía: font-black uppercase tracking-[0.2em] para labels

import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { calculateGlobalBatteriesAsync } from '../../services/computeWorkerService';
import { getPerMuscleBatteries, ACCORDION_MUSCLES } from '../../services/auge';
import SkeletonLoader from '../ui/SkeletonLoader';
import { shareElementAsImage } from '../../services/shareService';
import { XIcon, LinkIcon, ChevronDownIcon, SearchIcon } from '../icons';
import { BatteryShareCard } from './BatteryShareCard';

// ─── CONSTANTES ─────────────────────────────────────────────────────────────

const RING_RADIUS = 30;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

const RING_COLORS = {
    cns: '#38bdf8',
    muscular: '#f472b6',
    spinal: '#fb923c',
} as const;

const RING_GLOW: Record<string, string> = {
    cns: 'drop-shadow(0 0 5px rgba(56,189,248,0.4))',
    muscular: 'drop-shadow(0 0 5px rgba(244,114,182,0.4))',
    spinal: 'drop-shadow(0 0 5px rgba(251,146,60,0.4))',
};

const RING_DESCRIPTIONS = {
    cns: "Tu procesador central. Fatiga por esfuerzo mental, falta de sueño y carga pesada (1-5 reps). Si está bajo, tu fuerza máxima cae significativamente.",
    muscular: "Estado local de tus tejidos. Daño por microrroturas y agotamiento de glucógeno. Si está bajo, sentirás pesadez, menos 'pump' y menos tolerancia al volumen.",
    spinal: "Integridad de tu eje axial y tejido conectivo (tendones). Cargas de compresión y cizalla acumuladas. Si está bajo, el riesgo de molestia articular aumenta.",
};

type RingId = 'cns' | 'muscular' | 'spinal';

const RING_LAYOUTS = {
    olympic: {
        cns: { x: 45, y: 38 },
        muscular: { x: 115, y: 38 },
        spinal: { x: 80, y: 78 }
    },
    row: {
        cns: { x: 28, y: 55 },
        muscular: { x: 80, y: 55 },
        spinal: { x: 132, y: 55 }
    }
};

// ─── COLORES DE ESTADO ──────────────────────────────────────────────────────

const getBarColor = (v: number) => v >= 80 ? '#22c55e' : v >= 55 ? '#eab308' : v >= 35 ? '#f97316' : '#ef4444';
const getBarText = (v: number) => v >= 80 ? 'text-emerald-400' : v >= 55 ? 'text-yellow-400' : v >= 35 ? 'text-orange-400' : 'text-red-400';
const getStatusStroke = (v: number) => v >= 80 ? '#10b981' : v >= 40 ? '#f59e0b' : '#f43f5e';
const getTextColor = (v: number) => v >= 80 ? 'text-emerald-400' : v >= 40 ? 'text-amber-400' : 'text-rose-400';

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

// ─── MOCK ───────────────────────────────────────────────────────────────────

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

// ─── ICONOS TOGGLE ─────────────────────────────────────────────────────────

const OlympicIcon: React.FC<{ size?: number; className?: string }> = ({ size = 16, className }) => (
    <svg width={size} height={size} viewBox="0 0 20 16" fill="none" className={className}>
        <circle cx="5.5" cy="6" r="4" stroke="currentColor" strokeWidth="1.5" />
        <circle cx="14.5" cy="6" r="4" stroke="currentColor" strokeWidth="1.5" />
        <circle cx="10" cy="12" r="4" stroke="currentColor" strokeWidth="1.5" />
    </svg>
);

const RowIcon: React.FC<{ size?: number; className?: string }> = ({ size = 16, className }) => (
    <svg width={size} height={size} viewBox="0 0 22 10" fill="none" className={className}>
        <circle cx="4" cy="5" r="3.5" stroke="currentColor" strokeWidth="1.3" />
        <circle cx="11" cy="5" r="3.5" stroke="currentColor" strokeWidth="1.3" />
        <circle cx="18" cy="5" r="3.5" stroke="currentColor" strokeWidth="1.3" />
    </svg>
);

// ─── SUB-COMPONENTES ────────────────────────────────────────────────────────

// ─── Modal de Calibración Profesional ─────────────────────────────────────────

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
            {/* Backdrop con desenfoque suave */}
            <div className="absolute inset-0 bg-black/60 backdrop-blur-sm pointer-events-auto animate-fade-in" />

            {/* Panel Plano y Profesional */}
            <div
                className="relative w-full max-w-[340px] mb-[120px] bg-zinc-900 border border-white/10 rounded-2xl shadow-[0_8px_32px_rgba(0,0,0,0.5)] overflow-hidden pointer-events-auto animate-slide-up"
                onClick={e => e.stopPropagation()}
            >
                {/* Indicador de Tipo */}
                <div className="h-1 w-full" style={{ backgroundColor: color }} />

                <div className="p-6">
                    <div className="flex justify-between items-start mb-6">
                        <div>
                            <h3 className="text-[10px] font-black text-white uppercase tracking-[0.2em] opacity-40 mb-1">Ajuste de Precisión</h3>
                            <h2 className="text-xl font-bold text-white uppercase tracking-tight">
                                {id === 'cns' ? 'Nervioso Central' : id === 'spinal' ? 'Carga Axial' : 'Batería Muscular'}
                            </h2>
                        </div>
                        <div className="text-right">
                            <span className="text-3xl font-black font-mono text-white tracking-tighter tabular-nums">{calibValue}%</span>
                            {diff !== 0 && (
                                <p className={`text-[10px] font-bold mt-0.5 ${diff > 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                                    {diff > 0 ? '+' : ''}{diff}% ajuste
                                </p>
                            )}
                        </div>
                    </div>

                    <div className="space-y-6">
                        {/* Control Plano */}
                        <div className="relative pt-2">
                            <input
                                type="range" min="0" max="100"
                                value={calibValue}
                                onChange={e => onCalibChange(parseInt(e.target.value))}
                                className="w-full h-1 bg-zinc-800 rounded-full appearance-none cursor-pointer accent-white"
                                style={{
                                    background: `linear-gradient(to right, ${color} ${calibValue}%, #27272a ${calibValue}%)`,
                                }}
                            />
                            <div className="flex justify-between mt-3 text-[9px] text-zinc-600 font-bold uppercase tracking-widest">
                                <span>Agotado (0%)</span>
                                <span>Óptimo (100%)</span>
                            </div>
                        </div>

                        <div className="bg-white/5 p-4 rounded-xl border border-white/5">
                            <p className="text-[10px] text-zinc-400 leading-relaxed italic">
                                {id === 'cns' ? "Ajusta tu frescura mental y coordinación motriz." :
                                    id === 'spinal' ? "Considera la compresión vertebral y rigidez de ligamentos." :
                                        "Calibra el agotamiento local de tus tejidos musculares."}
                            </p>
                        </div>
                    </div>

                    {/* Botones Planos */}
                    <div className="mt-8 flex gap-3">
                        <button
                            onClick={onCancel}
                            className="flex-1 py-3.5 text-[10px] font-black uppercase tracking-[0.15em] text-zinc-500 hover:text-white transition-colors"
                        >
                            Cancelar
                        </button>
                        <button
                            onClick={onSave}
                            className="flex-1 py-3.5 bg-white text-black rounded-xl text-[10px] font-black uppercase tracking-[0.15em] active:scale-[0.98] transition-all"
                        >
                            Confirmar
                        </button>
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

    // View state
    const [viewMode, setViewMode] = useState<'olympic' | 'row'>('olympic');
    const [focusedRing, setFocusedRing] = useState<RingId | null>(null);
    const [isMuscleSectionOpen, setIsMuscleSectionOpen] = useState(false);
    const [openGroups, setOpenGroups] = useState<Set<string>>(new Set());
    const [expandedMuscleId, setExpandedMuscleId] = useState<string | null>(null);

    // Calibration state
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
        const muscleVals = Object.values(perMuscle);
        if (muscleVals.length === 0) return 100; // Si no hay datos aún, 100
        const avg = muscleVals.reduce((acc, val) => acc + val, 0) / muscleVals.length;
        return Math.round(avg);
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

    // Handlers
    const startLongPress = (id: RingId) => {
        if (calibratingId) return;
        if (id === 'muscular') {
            addToast('Ajusta la fatiga muscular directamente en la sección "Batería Muscular" abajo', 'suggestion');
            setIsMuscleSectionOpen(true);
            return;
        }
        wasLongPressRef.current = false;
        pressTimerRef.current = window.setTimeout(() => {
            wasLongPressRef.current = true;
            setCalibratingId(id);
            pressTimerRef.current = null;
        }, 500);
    };
    const cancelLongPress = () => { if (pressTimerRef.current) { clearTimeout(pressTimerRef.current); pressTimerRef.current = null; } };

    const handleSaveCalibration = () => {
        if (!batteries || !calibratingId) return;

        const currentCalib = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} };

        let cnsDelta = currentCalib.cnsDelta || 0;
        let spinalDelta = currentCalib.spinalDelta || 0;

        if (calibratingId === 'cns') cnsDelta = calibCns - (cnsValue - cnsDelta);
        if (calibratingId === 'spinal') spinalDelta = calibSpinal - (spinalValue - spinalDelta);

        setSettings({
            batteryCalibration: {
                cnsDelta,
                muscularDelta: currentCalib.muscularDelta || 0,
                spinalDelta,
                muscleDeltas: currentCalib.muscleDeltas,
                lastCalibrated: new Date().toISOString(),
            },
            hasPrecalibratedBattery: true,
        });

        addToast(`${calibratingId.toUpperCase()} recalibrado`, 'success');
        setCalibratingId(null);
    };

    const toggleGroup = (e: React.MouseEvent, label: string) => {
        e.stopPropagation();
        setOpenGroups(prev => {
            const next = new Set(prev);
            next.has(label) ? next.delete(label) : next.add(label);
            return next;
        });
    };

    if (!batteries && !isDemo) return <SkeletonLoader lines={4} className="rounded-xl" />;

    const isHero = variant === 'hero';
    const ringData = [
        { id: 'cns' as RingId, value: cnsValue, label: 'SNC' },
        { id: 'muscular' as RingId, value: muscularValue, label: 'Muscular' },
        { id: 'spinal' as RingId, value: spinalValue, label: 'Columna' },
    ];

    return (
        <>
            {/* Share card off-screen */}
            <div className="fixed -left-[9999px] top-0 w-[540px] h-[960px] pointer-events-none overflow-hidden z-0">
                <BatteryShareCard cns={cnsValue} muscular={muscularValue} spinal={spinalValue} />
            </div>

            <div id="auge-telemetry-panel" className={isHero ? 'w-full relative' : 'relative'}>

                {/* ── Header ── */}
                <div className="flex items-center justify-between mb-1">
                    <button
                        onClick={() => { setViewMode(v => v === 'olympic' ? 'row' : 'olympic'); setFocusedRing(null); }}
                        className="p-2 -ml-2 text-zinc-700 hover:text-zinc-300 rounded-lg hover:bg-white/5 transition-all active:scale-95"
                    >
                        {viewMode === 'olympic' ? <RowIcon size={18} /> : <OlympicIcon size={18} />}
                    </button>
                    {shareable && (
                        <button onClick={async () => {
                            try { await shareElementAsImage('battery-share-card', 'Mi Batería AUGE', 'KPKN AUGE SYSTEM'); }
                            catch { addToast('Error al compartir', 'danger'); }
                        }} className="p-2 -mr-2 text-zinc-700 hover:text-zinc-300 rounded-lg hover:bg-white/5 transition-all">
                            <LinkIcon size={15} />
                        </button>
                    )}
                </div>

                {/* ── ANILLOS ── */}
                <div className="relative flex flex-col items-center justify-center mb-1 min-h-[195px]">
                    {/* Efecto de fondo M16: Difuminado tenue de los colores de los anillos */}
                    <div className="absolute inset-0 flex items-center justify-center pointer-events-none overflow-hidden opacity-20 blur-[60px]">
                        <div className="absolute w-[180px] h-[180px] rounded-full bg-sky-500/30 -translate-x-16 -translate-y-8 animate-pulse" style={{ animationDuration: '8s' }} />
                        <div className="absolute w-[160px] h-[160px] rounded-full bg-pink-500/30 translate-x-16 -translate-y-6 animate-pulse" style={{ animationDuration: '10s' }} />
                        <div className="absolute w-[140px] h-[140px] rounded-full bg-orange-500/30 translate-y-12 animate-pulse" style={{ animationDuration: '7s' }} />
                    </div>

                    <div className="relative w-full max-w-[280px]" style={{ aspectRatio: '16/11', overflow: 'visible' }}>
                        <svg viewBox="0 0 160 110" className="block w-full h-full" style={{ overflow: 'visible' }}>
                            <defs>
                                {Object.entries(RING_COLORS).map(([id, color]) => (
                                    <linearGradient key={`grad-${id}`} id={`grad-${id}`} x1="0%" y1="0%" x2="0%" y2="100%">
                                        <stop offset="0%" stopColor={color} stopOpacity="1" />
                                        <stop offset="100%" stopColor={color} stopOpacity="0.75" />
                                    </linearGradient>
                                ))}
                            </defs>
                            {ringData.map(({ id, value, label }) => {
                                const pos = RING_LAYOUTS[viewMode][id];
                                const isFocused = focusedRing === id;
                                const isFaded = focusedRing !== null && !isFocused;
                                const scale = isFocused ? 1.25 : isFaded ? 0.9 : 1;

                                return (
                                    <g key={id}
                                        transform={`translate(${pos.x}, ${pos.y}) rotate(-90) scale(${scale})`}
                                        style={{
                                            opacity: isFaded ? 0.2 : 1, cursor: 'pointer',
                                            transition: 'transform 0.6s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.4s ease, filter 0.4s ease',
                                            filter: isFaded ? 'blur(1px)' : isFocused ? RING_GLOW[id] : 'none',
                                        }}
                                        onClick={e => {
                                            e.stopPropagation();
                                            if (wasLongPressRef.current) return;
                                            setFocusedRing(isFocused ? null : id);
                                        }}
                                        onPointerDown={() => startLongPress(id)} onPointerUp={cancelLongPress} onPointerLeave={cancelLongPress}
                                    >
                                        <circle r={RING_RADIUS} fill="none" stroke={isFaded ? '#141416' : '#1a1a1e'} strokeWidth={4} />
                                        <circle r={RING_RADIUS} fill="none" stroke={`url(#grad-${id})`}
                                            strokeWidth={isFocused ? 4.5 : 4}
                                            strokeDasharray={`${(value / 100) * RING_CIRCUMFERENCE} ${RING_CIRCUMFERENCE}`}
                                            strokeLinecap="round"
                                            className="transition-all duration-700"
                                            style={{ transition: 'stroke-dasharray 0.7s ease-out, stroke-width 0.3s ease' }}
                                        />
                                        <g transform="rotate(90)">
                                            <foreignObject x={-28} y={-12} width={56} height={24} style={{ overflow: 'visible' }}>
                                                <div {...{ xmlns: 'http://www.w3.org/1999/xhtml' } as any} style={{
                                                    width: '100%', height: '100%', display: 'flex', flexDirection: 'column',
                                                    alignItems: 'center', justifyContent: 'center', pointerEvents: 'none'
                                                }}>
                                                    <span style={{
                                                        lineHeight: 1,
                                                        color: isFaded ? 'rgba(63,63,70,0.5)' : isFocused ? RING_COLORS[id] : 'rgba(244,244,245,0.95)',
                                                        fontSize: isFocused ? '11px' : '9px', fontWeight: 'bold'
                                                    }}>{value}%</span>
                                                    <span style={{
                                                        fontSize: isFocused ? '6.5px' : '5.5px',
                                                        color: isFaded ? 'rgba(39,39,42,0.6)' : 'rgba(113,113,122,0.85)',
                                                        lineHeight: 1.2, marginTop: 1, fontWeight: 'bold'
                                                    }}>{label}</span>
                                                </div>
                                            </foreignObject>
                                        </g>
                                    </g>
                                );
                            })}
                        </svg>

                        {/* Texto Descriptivo Flotante */}
                        <div className={`absolute left-0 right-0 -bottom-8 pointer-events-none transition-all duration-500 overflow-hidden ${focusedRing ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'}`}>
                            <div className="bg-black/40 backdrop-blur-md rounded-xl p-3 border border-white/5 mx-auto max-w-[260px]">
                                <p className="text-[10px] text-zinc-100 font-medium leading-relaxed italic text-center">
                                    {focusedRing && RING_DESCRIPTIONS[focusedRing]}
                                </p>
                            </div>
                        </div>
                    </div>
                </div>

                {/* ── SEPARADOR ── */}
                <div className="border-t border-white/5 my-4" />

                {/* ── BATERÍA MUSCULAR (Acordeón Principal) ── */}
                <div className="pb-4">
                    <button
                        onClick={() => setIsMuscleSectionOpen(!isMuscleSectionOpen)}
                        className="w-full flex items-center justify-between py-2.5 px-3 -mx-3 bg-white/[0.02] hover:bg-white/[0.04] transition-all group rounded-xl border border-white/5 active:scale-[0.99]"
                    >
                        <div className="flex items-center gap-2.5">
                            <p className="text-[10px] font-black text-zinc-400 uppercase tracking-[0.22em] group-hover:text-zinc-200 transition-colors">Batería Muscular</p>
                            {!isMuscleSectionOpen && (
                                <div className="flex gap-1">
                                    {['#22c55e', '#eab308', '#f97316'].slice(0, 3).map((c, i) => (
                                        <div key={i} className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: c, opacity: 0.6 }} />
                                    ))}
                                </div>
                            )}
                        </div>
                        <div className={`text-zinc-500 transition-transform duration-500 ${isMuscleSectionOpen ? 'rotate-180' : ''}`}>
                            <ChevronDownIcon size={14} />
                        </div>
                    </button>

                    <div className={`transition-all duration-500 overflow-hidden ${isMuscleSectionOpen ? 'max-h-[2000px] mt-4 opacity-100' : 'max-h-0 opacity-0'}`}>
                        {/* Grid 2 columnas para grupos anatómicos */}
                        <div className="grid grid-cols-2 gap-x-6 gap-y-0">
                            {MUSCLE_GROUPS.map(group => {
                                const muscles = group.ids.map(id => {
                                    const val = muscleMap.get(id);
                                    return val !== undefined ? { id, value: val } : null;
                                }).filter(Boolean) as { id: string; value: number }[];
                                if (muscles.length === 0) return null;

                                const avgVal = Math.round(muscles.reduce((s, m) => s + m.value, 0) / muscles.length);
                                const isOpen = openGroups.has(group.label);

                                return (
                                    <div key={group.label} className="flex flex-col border-white/[0.03]">
                                        <button
                                            onClick={(e) => toggleGroup(e, group.label)}
                                            className="w-full flex items-center justify-between py-3 hover:bg-white/[0.03] transition-colors rounded-lg px-2 -mx-2 group/btn"
                                        >
                                            <div className="flex flex-col gap-1.5 w-full">
                                                <div className="flex items-center justify-between gap-2">
                                                    <span className="text-[10px] font-bold text-zinc-400 group-hover/btn:text-zinc-100 uppercase tracking-tight">{group.label}</span>
                                                    <span className={`text-[9px] font-black font-mono ${getBarText(avgVal)}`}>{avgVal}%</span>
                                                </div>
                                                <div className="w-full h-[2px] rounded-full bg-white/5 overflow-hidden">
                                                    <div className="h-full rounded-full transition-all duration-700"
                                                        style={{ width: `${avgVal}%`, backgroundColor: getBarColor(avgVal) }} />
                                                </div>
                                            </div>
                                        </button>

                                        {/* Músculos dentro del grupo */}
                                        <div className={`space-y-3 transition-all duration-300 overflow-hidden ${isOpen ? 'max-h-[500px] pb-5 pl-2 pt-1' : 'max-h-0'}`}>
                                            {muscles.map(({ id, value }) => {
                                                const isExpanded = expandedMuscleId === id;
                                                return (
                                                    <div key={id} className="flex flex-col gap-1.5">
                                                        <button
                                                            onClick={() => setExpandedMuscleId(isExpanded ? null : id)}
                                                            className="flex flex-col gap-1 text-left w-full active:scale-[0.98] transition-transform"
                                                        >
                                                            <div className="flex items-center justify-between w-full">
                                                                <span className="text-[9px] text-zinc-500 font-medium">{LABEL_MAP[id] || id}</span>
                                                                <span className={`text-[9px] font-black font-mono tabular-nums ${getBarText(value)}`}>{value}%</span>
                                                            </div>
                                                            <div className="w-full h-[4px] rounded-full bg-white/[0.04] overflow-hidden">
                                                                <div className="h-full rounded-full transition-all duration-700"
                                                                    style={{ width: `${value}%`, backgroundColor: getBarColor(value) }} />
                                                            </div>
                                                        </button>

                                                        {/* Calibración Local */}
                                                        {isExpanded && (
                                                            <div className="mt-2 p-2.5 bg-white/[0.03] rounded-xl border border-white/5 animate-fade-in">
                                                                <div className="flex items-center justify-between mb-3">
                                                                    <span className="text-[8px] font-black text-zinc-500 uppercase tracking-widest">Ajuste Residual</span>
                                                                    <div className="flex items-center gap-2">
                                                                        <button
                                                                            onClick={() => {
                                                                                const currentDeltas = settings.batteryCalibration?.muscleDeltas || {};
                                                                                const newDelta = (currentDeltas[id] || 0) - 5;
                                                                                setSettings({
                                                                                    batteryCalibration: {
                                                                                        ...(settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} }),
                                                                                        muscleDeltas: { ...currentDeltas, [id]: newDelta }
                                                                                    }
                                                                                });
                                                                            }}
                                                                            className="w-6 h-6 flex items-center justify-center bg-white/5 rounded-lg text-zinc-400 hover:text-white active:scale-95 transition-all text-xs font-black"
                                                                        >-</button>
                                                                        <span className={`text-[10px] font-black font-mono w-8 text-center ${(settings.batteryCalibration?.muscleDeltas?.[id] || 0) > 0 ? 'text-emerald-400' : (settings.batteryCalibration?.muscleDeltas?.[id] || 0) < 0 ? 'text-rose-400' : 'text-zinc-600'}`}>
                                                                            {(settings.batteryCalibration?.muscleDeltas?.[id] || 0) > 0 ? '+' : ''}{settings.batteryCalibration?.muscleDeltas?.[id] || 0}%
                                                                        </span>
                                                                        <button
                                                                            onClick={() => {
                                                                                const currentDeltas = settings.batteryCalibration?.muscleDeltas || {};
                                                                                const newDelta = (currentDeltas[id] || 0) + 5;
                                                                                setSettings({
                                                                                    batteryCalibration: {
                                                                                        ...(settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} }),
                                                                                        muscleDeltas: { ...currentDeltas, [id]: newDelta }
                                                                                    }
                                                                                });
                                                                            }}
                                                                            className="w-6 h-6 flex items-center justify-center bg-white/5 rounded-lg text-zinc-400 hover:text-white active:scale-95 transition-all text-xs font-black"
                                                                        >+</button>
                                                                    </div>
                                                                </div>
                                                                <div className="grid grid-cols-2 gap-2 text-[8px] uppercase font-black text-zinc-600">
                                                                    <div className="flex flex-col">
                                                                        <span>Recuperación</span>
                                                                        <span className="text-zinc-400">Automatizada</span>
                                                                    </div>
                                                                    <div className="flex flex-col text-right">
                                                                        <span>Offset Manual</span>
                                                                        <span className="text-zinc-400">Persistente</span>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        )}
                                                    </div>
                                                );
                                            })}
                                        </div>
                                        <div className="h-px bg-white/[0.03] w-full" />
                                    </div>
                                );
                            })}
                        </div>
                        {isDemo && <p className="text-[9px] text-zinc-700 font-medium text-center mt-6 uppercase tracking-wider italic">Completa entrenamientos para desbloquear biometría real</p>}
                    </div>
                </div>

                <p className="text-[7px] text-zinc-800 mt-2 font-mono uppercase tracking-widest text-center">BIOMETRÍA PERSONAL — Mantén pulsado para recalibrar</p>

                {/* Modal de Calibración Individual */}
                {calibratingId && batteries && (
                    <CalibrationModal
                        id={calibratingId}
                        currentValue={
                            calibratingId === 'cns' ? cnsValue :
                                calibratingId === 'muscular' ? muscularValue :
                                    spinalValue
                        }
                        calibValue={
                            calibratingId === 'cns' ? calibCns :
                                calibratingId === 'muscular' ? calibMusc :
                                    calibSpinal
                        }
                        onCalibChange={v => {
                            if (calibratingId === 'cns') setCalibCns(v);
                            if (calibratingId === 'muscular') setCalibMusc(v);
                            if (calibratingId === 'spinal') setCalibSpinal(v);
                        }}
                        onSave={handleSaveCalibration}
                        onCancel={() => setCalibratingId(null)}
                    />
                )}
            </div>
        </>
    );
};
