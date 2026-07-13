// components/home/AugeTelemetryPanel.tsx
// Material 3 — RINGS Display + animated transitions
// Figma exact: rings = 144px diameter, 6px border, no text inside rings.

import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { calculateGlobalBatteriesAsync } from '../../services/computeWorkerService';
import { getPerMuscleBatteries, ACCORDION_MUSCLES } from '../../services/auge';
import SkeletonLoader from '../ui/SkeletonLoader';
import { BatteryShareCard } from './BatteryShareCard';
import { motion, AnimatePresence } from 'framer-motion';

// ─── Types & Constants ──────────────────────────────────────────────────────

type RingId = 'muscular' | 'cns' | 'spinal';
export type RingsViewMode = 'rings' | 'individual';

const RING_COLORS = {
    muscular: '#3F51B5', // Indigo M3
    cns: '#00828E',      // Deep Teal M3
    spinal: '#C62828',   // Strong Red M3
} as const;

const RING_LABELS: Record<string, string> = {
    muscular: "Músculos",
    cns: "SNC",
    spinal: "Columna",
};

const RING_LABELS_SHORT: Record<string, string> = {
    muscular: "Músc.",
    cns: "SNC",
    spinal: "Col.",
};

const RING_DESCRIPTIONS: Record<string, string> = {
    cns: "Es tu 'batería' de energía mental y coordinación. Si está baja, puedes sentirte más lento de reflejos o con la mente cansada después de un día intenso o poco sueño.",
    muscular: "Indica qué tan recuperados están tus músculos. Un nivel bajo significa que tus fibras necesitan descansar para evitar sobrecargas y estar listas para tu próximo reto.",
    spinal: "Mide el impacto acumulado en tu espalda y articulaciones. Te ayuda a saber cuándo es mejor bajar la carga para cuidar tu estructura y evitar molestias articulares.",
};

const RING_QUESTIONS: Record<string, string> = {
    cns: "¿Sientes el cuerpo inusualmente pesado o la mente nublada al despertar? Puedes recalibrar tu sistema nervioso deslizando el anillo arriba o abajo.",
    muscular: "Cada zona anatómica se recupera a distinto ritmo pos-esfuerzo. Abre 'Batería por zona' para calibrar con precisión tus músculos y articulaciones.",
    spinal: "¿Notas rigidez acentuada o la espalda 'comprimida' durante el día? Puedes recalibrar tu columna de inmediato deslizando el anillo arriba o abajo.",
};

const getBarColor = (v: number) => v >= 80 ? '#22c55e' : v >= 55 ? '#eab308' : v >= 35 ? '#f97316' : '#ef4444';
const getBarTextColor = (v: number) => v >= 80 ? 'text-emerald-600' : v >= 55 ? 'text-yellow-600' : v >= 35 ? 'text-orange-500' : 'text-red-500';

// ─── Muscle Groups ──────────────────────────────────────────────────────────

interface MuscleGroup { label: string; ids: string[]; }

const MUSCLE_GROUPS: MuscleGroup[] = [
    { label: 'Pecho', ids: ['Pectorales'] },
    { label: 'Espalda', ids: ['Dorsales', 'Trapecio', 'Erectores Espinales'] },
    { label: 'Hombros', ids: ['Deltoides Anterior', 'Deltoides Lateral', 'Deltoides Posterior'] },
    { label: 'Brazos', ids: ['Bíceps', 'Tríceps', 'Antebrazo'] },
    { label: 'Core', ids: ['Abdomen', 'Core'] },
    { label: 'Piernas', ids: ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas', 'Aductores'] },
    { label: 'Tendones y Articulaciones', ids: ['Rodillas', 'Manguito Rotador', 'Codos', 'Caderas'] },
];

const LABEL_MAP: Record<string, string> = {
    'pectorales': 'Pectorales', 'dorsales': 'Dorsales', 'trapecio': 'Trapecio',
    'espalda baja': 'Esp. Baja', 'deltoides-anterior': 'Delt. Ant.',
    'deltoides-lateral': 'Delt. Lat.', 'deltoides-posterior': 'Delt. Post.',
    'bíceps': 'Bíceps', 'tríceps': 'Tríceps', 'antebrazo': 'Antebrazo',
    'abdomen': 'Abdomen', 'core': 'Core', 'cuádriceps': 'Cuádriceps',
    'isquiosurales': 'Isquiosurales', 'glúteos': 'Glúteos', 'pantorrillas': 'Pantorrillas',
    'aductores': 'Aductores', 'rodillas': 'Rodillas', 'manguito rotador': 'Manguito Rotador',
    'codos': 'Codos', 'caderas': 'Caderas', 'erectores espinales': 'Eresp. Espinales'
};

// ─── Calibration Modal (Remains the same as requested functionality) ───────

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
        <div className="fixed inset-0 z-[2000] flex items-center justify-center p-6" onClick={onCancel}>
            <div className="absolute inset-0 bg-black/60 backdrop-blur-md pointer-events-auto" />
            <motion.div
                initial={{ opacity: 0, scale: 0.9, y: 30 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.9, y: 30 }}
                className="relative w-full max-w-[360px] bg-white rounded-[32px] shadow-2xl overflow-hidden pointer-events-auto"
                onClick={e => e.stopPropagation()}
            >
                <div className="h-1.5 w-full" style={{ backgroundColor: color }} />
                <div className="p-8">
                    <div className="flex flex-col gap-1 mb-8">
                        <h3 className="text-[10px] font-black text-black/40 uppercase tracking-[0.2em]">Recalibración de Biometría</h3>
                        <h2 className="text-2xl font-black text-black leading-tight">
                            {RING_LABELS[id]}
                        </h2>
                    </div>

                    <div className="flex flex-col items-center gap-6 mb-8">
                        <div className="text-6xl font-black text-black font-mono tabular-nums tracking-tighter">
                            {calibValue}<span className="text-2xl opacity-30">%</span>
                        </div>

                        <div className="w-full px-2">
                            <input
                                type="range"
                                min="0"
                                max="100"
                                value={calibValue}
                                onChange={e => onCalibChange(parseInt(e.target.value))}
                                className="w-full h-3 rounded-full appearance-none cursor-pointer bg-black/5 accent-black"
                                style={{
                                    background: `linear-gradient(to right, ${color} ${calibValue}%, rgba(0,0,0,0.05) ${calibValue}%)`
                                }}
                            />
                        </div>
                    </div>

                    <div className="flex flex-col gap-3">
                        <button
                            onClick={onSave}
                            className="w-full py-4 bg-black text-white rounded-2xl text-sm font-black uppercase tracking-widest active:scale-[0.97] transition-all"
                        >
                            Confirmar Ajuste
                        </button>
                    </div>
                </div>
            </motion.div>
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

    const [batteries, setBatteries] = useState<any | null>(null);
    const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});
    const [focusedRing, setFocusedRing] = useState<RingId | null>(null);
    const [carouselIndex, setCarouselIndex] = useState(0);
    const [isMuscleAccordionOpen, setIsMuscleAccordionOpen] = useState(false);
    const [calibratingId, setCalibratingId] = useState<RingId | null>(null);
    const [calibCns, setCalibCns] = useState(0);
    const [calibMusc, setCalibMusc] = useState(0);
    const [calibSpinal, setCalibSpinal] = useState(0);
    const pressTimerRef = useRef<number | null>(null);
    const wasLongPressRef = useRef(false);
    const [swipeState, setSwipeState] = useState<{ id: RingId, startY: number, startVal: number, currentVal: number } | null>(null);
    const versionRef = useRef(0);

    const ringIds: RingId[] = ['muscular', 'cns', 'spinal'];

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

    const systemValues = { muscular: muscularValue, cns: cnsValue, spinal: spinalValue };

    // Data fetching (simplified)
    useEffect(() => {
        if (isAppLoading || !history) return;
        versionRef.current++;
        calculateGlobalBatteriesAsync(history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList)
            .then(r => setBatteries(r));
    }, [history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, isAppLoading]);

    useEffect(() => {
        if (isAppLoading || !history) return;
        const hierarchy = muscleHierarchy || { bodyPartHierarchy: {}, specialCategories: {}, muscleToBodyPart: {} };
        setPerMuscle(getPerMuscleBatteries(history, exerciseList, sleepLogs || [], settings, hierarchy,
            postSessionFeedback || [], waterLogs || [], dailyWellbeingLogs || [], nutritionLogs || []));
    }, [history, exerciseList, sleepLogs, settings, muscleHierarchy, postSessionFeedback, waterLogs, dailyWellbeingLogs, nutritionLogs, isAppLoading]);

    // Handle Calibration saving
    const handleSaveCalibration = () => {
        if (!calibratingId) return;
        const cur = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} };
        let cnsDelta = cur.cnsDelta || 0;
        let spinalDelta = cur.spinalDelta || 0;
        let muscularDelta = cur.muscularDelta || 0;

        if (calibratingId === 'cns') cnsDelta = calibCns - (cnsValue - cnsDelta);
        if (calibratingId === 'muscular') muscularDelta = calibMusc - (muscularValue - muscularDelta);
        if (calibratingId === 'spinal') spinalDelta = calibSpinal - (spinalValue - spinalDelta);

        setSettings({
            batteryCalibration: { cnsDelta, muscularDelta, spinalDelta, muscleDeltas: cur.muscleDeltas, lastCalibrated: new Date().toISOString() },
            hasPrecalibratedBattery: true
        });
        addToast(`${RING_LABELS[calibratingId]} recalibrado`, 'success');
        setCalibratingId(null);
    };

    const handleLocalMuscleCalibrate = (muscleId: string, currentVal: number, newVal: number) => {
        const cur = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} };
        const deltas = { ...(cur.muscleDeltas || {}) };
        const prevDelta = deltas[muscleId] || 0;
        deltas[muscleId] = newVal - (currentVal - prevDelta);

        setSettings({
            batteryCalibration: { ...cur, muscleDeltas: deltas, lastCalibrated: new Date().toISOString() },
            hasPrecalibratedBattery: true
        });
    };

    const handleRingPointerDown = (e: React.PointerEvent<HTMLDivElement>, id: RingId) => {
        if (viewMode === 'rings') {
            wasLongPressRef.current = false;
            pressTimerRef.current = window.setTimeout(() => { wasLongPressRef.current = true; setCalibratingId(id); }, 600);
            return;
        }
        if (id === 'muscular') return;

        e.currentTarget.setPointerCapture(e.pointerId);
        setSwipeState({ id, startY: e.clientY, startVal: systemValues[id], currentVal: systemValues[id] });
    };

    const handleRingPointerMove = (e: React.PointerEvent<HTMLDivElement>, id: RingId) => {
        if (swipeState && swipeState.id === id) {
            const deltaY = swipeState.startY - e.clientY;
            const newVal = Math.max(0, Math.min(100, Math.round(swipeState.startVal + deltaY / 1.5)));
            setSwipeState({ ...swipeState, currentVal: newVal });
        }
    };

    const handleRingPointerUp = (e: React.PointerEvent<HTMLDivElement>, id: RingId) => {
        if (viewMode === 'rings') {
            if (pressTimerRef.current) clearTimeout(pressTimerRef.current);
            if (!wasLongPressRef.current) setFocusedRing(focusedRing === id ? null : id);
            return;
        }

        if (swipeState && swipeState.id === id) {
            e.currentTarget.releasePointerCapture(e.pointerId);
            if (swipeState.startVal !== swipeState.currentVal) {
                const cur = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '', muscleDeltas: {} };
                let cDelta = cur.cnsDelta || 0;
                let sDelta = cur.spinalDelta || 0;
                if (id === 'cns') cDelta = swipeState.currentVal - (cnsValue - cDelta);
                if (id === 'spinal') sDelta = swipeState.currentVal - (spinalValue - sDelta);

                setSettings({
                    batteryCalibration: { ...cur, cnsDelta: cDelta, spinalDelta: sDelta, lastCalibrated: new Date().toISOString() },
                    hasPrecalibratedBattery: true
                });
                if (Math.abs(swipeState.startVal - swipeState.currentVal) > 3) {
                    addToast(`${RING_LABELS[id]} ajustado visualmente`, 'success');
                }
            }
            setSwipeState(null);
        }
    };

    const renderRing = (id: RingId, value: number, color: string, size: number = 144, strokeWidth: number = 7) => {
        const visualValue = (swipeState && swipeState.id === id) ? swipeState.currentVal : value;
        const radius = (size - strokeWidth) / 2;
        const circumference = 2 * Math.PI * radius;
        const offset = circumference - (visualValue / 100) * circumference;

        return (
            <div className="relative flex flex-col items-center"
                style={{
                    opacity: focusedRing && focusedRing !== id ? 0.35 : 1,
                    transition: 'all 0.5s cubic-bezier(0.16, 1, 0.3, 1)',
                    cursor: 'pointer',
                    zIndex: focusedRing === id ? 20 : focusedRing ? 1 : (id === 'muscular' ? 10 : id === 'cns' ? 9 : 8)
                }}
            >
                <div className="h-4 flex items-center justify-center mb-1">
                    <span className="text-[9px] sm:text-[10px] font-black text-black/40 uppercase tracking-[0.2em] leading-none whitespace-nowrap">
                        {RING_LABELS_SHORT[id]}
                    </span>
                </div>
                <div className="relative" style={{ width: size, height: size }}>
                    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="transform -rotate-90 overflow-visible">
                        {/* ─── LIQUID GLASS FILTERS ─── */}
                        <defs>
                            <filter id={`liquid-glass-${id}`} x="-50%" y="-50%" width="200%" height="200%">
                                {/* Subtle outer bloom */}
                                <feGaussianBlur in="SourceAlpha" stdDeviation="1.5" result="blur" />
                                <feFlood floodColor={color} result="color" />
                                <feComposite in="color" in2="blur" operator="in" result="glow" />
                                <feComponentTransfer in="glow" result="softGlow">
                                    <feFuncA type="linear" slope="0.4" />
                                </feComponentTransfer>
                                <feMerge>
                                    <feMergeNode in="softGlow" />
                                    <feMergeNode in="SourceGraphic" />
                                </feMerge>
                            </filter>
                        </defs>

                        {/* SVG Rings: Value ring and glass overlays */}
                        <circle cx={size / 2} cy={size / 2} r={radius} stroke={color} strokeWidth={strokeWidth} fill="none"
                            strokeDasharray={circumference} strokeDashoffset={offset} strokeLinecap="round" filter={`url(#liquid-glass-${id})`} className="transition-all duration-300 ease-out" />

                        {/* 2. Sub-Glow (Liquid Depth) */}
                        <motion.circle
                            initial={{ strokeDashoffset: circumference }}
                            animate={{ strokeDashoffset: offset }}
                            transition={{ duration: 1.5, ease: "easeOut", delay: 0.3 }}
                            cx={size / 2} cy={size / 2} r={radius}
                            fill="none"
                            stroke={color}
                            strokeWidth={strokeWidth}
                            strokeDasharray={circumference}
                            strokeLinecap="round"
                            filter={`url(#liquid-glass-${id})`}
                            style={{ strokeOpacity: 0.8 }}
                        />

                        {/* 3. The Highlight (Glass Reflection - Top Sheen) */}
                        <motion.circle
                            initial={{ strokeDashoffset: circumference }}
                            animate={{ strokeDashoffset: offset }}
                            transition={{ duration: 1.5, ease: "easeOut", delay: 0.3 }}
                            cx={size / 2} cy={size / 2} r={radius}
                            fill="none"
                            stroke="white"
                            strokeWidth={strokeWidth / 3.5}
                            strokeDasharray={circumference}
                            strokeLinecap="round"
                            style={{
                                strokeOpacity: 0.6,
                                mixBlendMode: 'overlay'
                            }}
                        />

                        {/* 4. Sharp Specular (The "Liquid Glare") */}
                        <motion.circle
                            initial={{ strokeDashoffset: circumference }}
                            animate={{ strokeDashoffset: offset }}
                            transition={{ duration: 1.5, ease: "easeOut", delay: 0.3 }}
                            cx={size / 2} cy={size / 2} r={radius - 1}
                            fill="none"
                            stroke="white"
                            strokeWidth={1}
                            strokeDasharray={circumference}
                            strokeLinecap="round"
                            style={{
                                strokeOpacity: 0.4,
                            }}
                        />
                    </svg>
                </div>
                <div className="h-6 flex items-center justify-center mt-1">
                    <span className="text-sm sm:text-base font-black text-black/60 font-mono tracking-wider leading-none">
                        {value}<span className="text-[8px] sm:text-[10px] opacity-20 ml-0.5">%</span>
                    </span>
                </div>
            </div>
        );
    };

    const currentGlowColor = useMemo(() => {
        if (viewMode === 'rings') return RING_COLORS.muscular; // Default logic for combined
        return RING_COLORS[ringIds[carouselIndex]];
    }, [viewMode, carouselIndex]);

    return (
        <>
            <AnimatePresence>
                {calibratingId && (
                    <CalibrationModal
                        id={calibratingId}
                        currentValue={systemValues[calibratingId]}
                        calibValue={calibratingId === 'cns' ? calibCns : calibratingId === 'muscular' ? calibMusc : calibSpinal}
                        onCalibChange={v => { if (calibratingId === 'cns') setCalibCns(v); if (calibratingId === 'muscular') setCalibMusc(v); if (calibratingId === 'spinal') setCalibSpinal(v); }}
                        onSave={handleSaveCalibration}
                        onCancel={() => setCalibratingId(null)}
                    />
                )}
            </AnimatePresence>

            <div id="auge-telemetry-panel" className="w-full relative bg-transparent">
                {/* ─── DYNAMIC BACKGROUND GLOW ─── */}
                <div className="absolute -inset-x-20 -top-40 -bottom-40 pointer-events-none z-0 flex justify-center items-center overflow-visible">
                    <AnimatePresence>
                        {viewMode === 'rings' ? (
                            <motion.div
                                key="glow-combined"
                                initial={{ opacity: 0 }}
                                animate={{ opacity: 0.5 }}
                                exit={{ opacity: 0 }}
                                transition={{ duration: 1.2 }}
                                className="absolute inset-0 blur-[120px] flex justify-center items-center"
                            >
                                <div className="w-full h-full relative flex justify-center items-center">
                                    <div className="w-[300px] h-[300px] rounded-full opacity-60 absolute" style={{ backgroundColor: RING_COLORS.muscular, transform: 'translateX(-100px)' }} />
                                    <div className="w-[300px] h-[300px] rounded-full opacity-60 absolute" style={{ backgroundColor: RING_COLORS.cns, transform: 'translateY(-50px)' }} />
                                    <div className="w-[300px] h-[300px] rounded-full opacity-60 absolute" style={{ backgroundColor: RING_COLORS.spinal, transform: 'translateX(100px)' }} />
                                </div>
                            </motion.div>
                        ) : (
                            <motion.div
                                key={`glow-${carouselIndex}`}
                                initial={{ opacity: 0, scale: 0.8 }}
                                animate={{ opacity: 0.7, scale: 1.2 }}
                                exit={{ opacity: 0, scale: 0.8 }}
                                transition={{ duration: 0.8 }}
                                className="absolute inset-0 blur-[150px] flex justify-center items-center"
                            >
                                <div className="w-full h-full max-w-xl rounded-full opacity-50" style={{ backgroundColor: currentGlowColor }} />
                            </motion.div>
                        )}
                    </AnimatePresence>
                </div>

                {/* ─── CONTENT ─── */}
                <div className="relative z-10 w-full min-h-[300px] flex flex-col overflow-visible">
                    <AnimatePresence mode="wait">
                        {viewMode === 'rings' ? (
                            <motion.div
                                key="rings-view"
                                initial={{ opacity: 0, scale: 0.98 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0, scale: 0.98, transition: { duration: 0.2 } }}
                                transition={{ type: "tween", ease: "easeInOut", duration: 0.3 }}
                                className="w-full flex justify-center items-center pt-6 pb-2 -space-x-12 overflow-visible"
                            >
                                {ringIds.map(id => (
                                    <div
                                        key={id}
                                        onPointerDown={e => handleRingPointerDown(e, id)}
                                        onPointerMove={e => handleRingPointerMove(e, id)}
                                        onPointerUp={e => handleRingPointerUp(e, id)}
                                        style={{
                                            filter: `drop-shadow(0 0 25px ${RING_COLORS[id]}66) drop-shadow(0 0 8px ${RING_COLORS[id]}44)`
                                        }}
                                        className="touch-none select-none"
                                    >
                                        {renderRing(id, systemValues[id], RING_COLORS[id])}
                                    </div>
                                ))}
                            </motion.div>
                        ) : (
                            <motion.div
                                key={`individual-${carouselIndex}`}
                                initial={{ opacity: 0, y: 10 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, transition: { duration: 0.2 } }}
                                transition={{ type: "tween", ease: "easeInOut", duration: 0.3 }}
                                className="w-full flex flex-col gap-8 pt-6 pb-4 overflow-visible"
                            >
                                <div className="flex items-center gap-6 px-6">
                                    {/* Large Ring */}
                                    <div className="flex flex-col items-center">
                                        <div
                                            style={{ filter: `drop-shadow(0 0 40px ${RING_COLORS[ringIds[carouselIndex]]}55)` }}
                                            onPointerDown={e => handleRingPointerDown(e, ringIds[carouselIndex])}
                                            onPointerMove={e => handleRingPointerMove(e, ringIds[carouselIndex])}
                                            onPointerUp={e => handleRingPointerUp(e, ringIds[carouselIndex])}
                                            className="touch-none select-none active:scale-95 transition-transform"
                                        >
                                            {renderRing(ringIds[carouselIndex], systemValues[ringIds[carouselIndex]], RING_COLORS[ringIds[carouselIndex]], 160, 8)}
                                        </div>
                                        {/* Stepper Dots */}
                                        <div className="flex gap-2.5 mt-6">
                                            {ringIds.map((id, i) => (
                                                <button
                                                    key={id}
                                                    onClick={() => setCarouselIndex(i)}
                                                    className={`w-2.5 h-2.5 rounded-full transition-all duration-300 ${i === carouselIndex ? 'w-6' : 'opacity-30'}`}
                                                    style={{ backgroundColor: RING_COLORS[ringIds[carouselIndex]] }}
                                                />
                                            ))}
                                        </div>
                                    </div>

                                    {/* Description/Info */}
                                    <div className="flex-1 space-y-4 min-w-0">
                                        <div>
                                            <h4 className="text-[9px] sm:text-[10px] font-black text-black/30 uppercase tracking-[0.2em] mb-1">Sistema {RING_LABELS[ringIds[carouselIndex]]}</h4>
                                            <p className="text-[12px] sm:text-[13px] text-black/80 font-medium leading-relaxed pr-4 whitespace-pre-wrap break-words">
                                                {RING_DESCRIPTIONS[ringIds[carouselIndex]]}
                                            </p>
                                        </div>
                                        <div className="bg-white/50 border border-black/[0.03] shadow-sm rounded-2xl p-3 sm:p-4 pr-4 sm:pr-5 backdrop-blur-md">
                                            <p className="text-[10px] sm:text-[11px] text-black/60 font-medium leading-relaxed">
                                                <span className="font-bold text-black/80 block mb-1 text-[11px] sm:text-[12px]">Calibración Sensorial</span>
                                                {RING_QUESTIONS[ringIds[carouselIndex]]}
                                            </p>
                                        </div>
                                        {ringIds[carouselIndex] === 'muscular' && (
                                            <button
                                                onClick={() => setIsMuscleAccordionOpen(!isMuscleAccordionOpen)}
                                                className="flex items-center gap-2 py-2 px-3 sm:px-4 rounded-full bg-black text-white shadow-xl shadow-black/10 text-[9px] sm:text-[10px] font-black uppercase tracking-widest hover:bg-black/80 active:scale-95 transition-all mt-2"
                                            >
                                                {isMuscleAccordionOpen ? 'Cerrar detalle' : 'Batería por zona'}
                                                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" className={`transition-transform duration-300 ${isMuscleAccordionOpen ? 'rotate-180' : ''}`}><path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" /></svg>
                                            </button>
                                        )}
                                    </div>
                                </div>

                                {/* Detailed Muscle Accordion */}
                                <AnimatePresence>
                                    {ringIds[carouselIndex] === 'muscular' && isMuscleAccordionOpen && (
                                        <motion.div
                                            initial={{ height: 0, opacity: 0 }}
                                            animate={{ height: 'auto', opacity: 1 }}
                                            exit={{ height: 0, opacity: 0 }}
                                            className="overflow-hidden px-6"
                                        >
                                            <div className="pt-6 border-t border-black/5 flex flex-col gap-6">
                                                <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-6">
                                                    {MUSCLE_GROUPS.map(group => {
                                                        const muscles = group.ids.map(id => ({ id, value: muscleMap.get(id) ?? 100 }));
                                                        const avg = Math.max(0, Math.min(100, Math.round(muscles.reduce((s, m) => s + m.value, 0) / (muscles.length || 1))));
                                                        return (
                                                            <div key={group.label} className="flex flex-col gap-2">
                                                                <div className="flex justify-between items-end border-b border-black/[0.03] pb-1.5">
                                                                    <span className="text-[9px] sm:text-[10px] font-black text-black/50 uppercase tracking-widest">{group.label}</span>
                                                                    <span className={`text-[9px] sm:text-[10px] font-black tabular-nums ${getBarTextColor(avg)}`}>{avg}% global</span>
                                                                </div>
                                                                <div className="grid grid-cols-1 gap-1.5 pl-2">
                                                                    {muscles.map(m => (
                                                                        <div key={m.id} className="flex flex-col gap-0.5 w-full relative">
                                                                            <div className="flex justify-between items-center text-[10px] sm:text-[11px] font-black text-black/70 tracking-tight">
                                                                                <span className="truncate">{LABEL_MAP[m.id.toLowerCase()] || m.id}</span>
                                                                                <span className={getBarTextColor(m.value)}>{m.value}%</span>
                                                                            </div>
                                                                            <div className="group relative w-full h-[6px] rounded-full bg-black/[0.04] overflow-hidden flex items-center">
                                                                                <div className="absolute left-0 top-0 bottom-0 pointer-events-none rounded-full transition-all duration-300" style={{ width: `${m.value}%`, backgroundColor: getBarColor(m.value) }} />
                                                                                <input
                                                                                    type="range" min="0" max="100"
                                                                                    defaultValue={m.value}
                                                                                    onMouseUp={e => handleLocalMuscleCalibrate(m.id, m.value, parseInt((e.target as HTMLInputElement).value))}
                                                                                    onTouchEnd={e => handleLocalMuscleCalibrate(m.id, m.value, parseInt((e.target as HTMLInputElement).value))}
                                                                                    className="w-full h-full opacity-0 cursor-pointer absolute inset-0 z-10"
                                                                                />
                                                                            </div>
                                                                        </div>
                                                                    ))}
                                                                </div>
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            </div>
                                        </motion.div>
                                    )}
                                </AnimatePresence>
                            </motion.div>
                        )}
                    </AnimatePresence>
                </div>
            </div>
        </>
    );
};

const fakeStructuralGroupIndex = MUSCLE_GROUPS.findIndex((group) => group.label === 'Tendones y Articulaciones');
if (fakeStructuralGroupIndex >= 0) {
    MUSCLE_GROUPS.splice(fakeStructuralGroupIndex, 1, { label: 'Cuello', ids: ['Cuello'] });
}

LABEL_MAP['cuello'] = 'Cuello';
delete LABEL_MAP['rodillas'];
delete LABEL_MAP['manguito rotador'];
delete LABEL_MAP['codos'];
delete LABEL_MAP['caderas'];
