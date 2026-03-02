// components/home/AugeTelemetryPanel.tsx
// Componente estrella pantalla "Tú" — Estética seria, densidad informativa

import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useAppState, useAppDispatch } from '../../contexts/AppContext';
import { calculateGlobalBatteriesAsync } from '../../services/computeWorkerService';
import { getPerMuscleBatteries, ACCORDION_MUSCLES } from '../../services/auge';
import SkeletonLoader from '../ui/SkeletonLoader';
import { shareElementAsImage } from '../../services/shareService';
import { XIcon, LinkIcon } from '../icons';
import { BatteryShareCard } from './BatteryShareCard';

const RING_RADIUS = 30;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

// Colores serios, cohesivos — paleta slate/stone, con algo más de presencia
const RING_COLORS = {
    cns: '#7c8aa1',
    muscular: '#9c8b7a',
    spinal: '#5c6b7d',
} as const;

// Color de barra muscular = color anillo muscular, variado por %
const getMuscleBarColor = (value: number): string => {
    if (value >= 80) return RING_COLORS.muscular;
    if (value >= 40) return '#78716c';
    return '#57534e';
};

const MOCK_MUSCLES: { id: string; label: string; value: number }[] = [
    { id: 'pectorales', label: 'Pectoral', value: 100 },
    { id: 'deltoides-lateral', label: 'Deltoides lateral', value: 90 },
    { id: 'dorsales', label: 'Lats', value: 75 },
    { id: 'isquiosurales', label: 'Isquios', value: 55 },
    { id: 'cuádriceps', label: 'Cuádriceps', value: 30 },
    { id: 'bíceps', label: 'Bíceps', value: 88 },
    { id: 'tríceps', label: 'Tríceps', value: 72 },
    { id: 'glúteos', label: 'Glúteos', value: 65 },
];

// Paleta suave: emerald, amber, rose
const getStatusStroke = (value: number): string => {
    if (value >= 80) return '#10b981';
    if (value >= 40) return '#f59e0b';
    return '#f43f5e';
};

const getTextColor = (value: number): string => {
    if (value >= 80) return 'text-emerald-500';
    if (value >= 40) return 'text-amber-500';
    return 'text-rose-500';
};

const getChipBg = (value: number): string => {
    if (value >= 80) return 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400';
    if (value >= 40) return 'bg-amber-500/15 text-amber-600 dark:text-amber-400';
    return 'bg-rose-500/15 text-rose-600 dark:text-rose-400';
};

const getDotBg = (value: number): string => {
    if (value >= 80) return 'bg-emerald-500';
    if (value >= 40) return 'bg-amber-500';
    return 'bg-rose-500';
};

interface MuscleDetailModalProps {
    label: string;
    value: number;
    onClose: () => void;
    isDemo?: boolean;
}

const MuscleDetailModal: React.FC<MuscleDetailModalProps> = ({ label, value, onClose, isDemo }) => (
    <div className="fixed inset-0 z-[200] bg-black/80 flex items-end sm:items-center justify-center p-0 sm:p-4" onClick={onClose}>
        <div
            className="w-full max-w-lg bg-[#0a0a0a] border border-white/10 rounded-2xl animate-slide-up sm:animate-fade-in overflow-hidden"
            onClick={e => e.stopPropagation()}
        >
            <div className="p-4 border-b border-white/5 flex justify-between items-center">
                <h3 className="text-base font-semibold text-white">{label}</h3>
                <button onClick={onClose} aria-label="Cerrar" className="p-1.5 text-zinc-500 hover:text-white rounded-full hover:bg-white/5">
                    <XIcon size={18} />
                </button>
            </div>
            <div className="p-6 flex flex-col items-center">
                <div className="relative w-24 h-24 mb-4">
                    <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                        <path className="text-zinc-800" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" strokeWidth="2" />
                        <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke={getStatusStroke(value)} strokeWidth="2" strokeDasharray={`${value}, 100`} strokeLinecap="round" className="transition-all duration-500" />
                    </svg>
                    <div className="absolute inset-0 flex items-center justify-center">
                        <span className={`text-2xl font-bold tabular-nums ${getTextColor(value)}`}>{value}%</span>
                    </div>
                </div>
                {isDemo ? (
                    <p className="text-sm text-zinc-500 text-center">Completa entrenamientos para ver desglose y evolución.</p>
                ) : (
                    <p className="text-sm text-zinc-500 text-center">Desglose y evolución próximamente.</p>
                )}
            </div>
        </div>
    </div>
);

interface CalibrationModalProps {
    cns: number;
    muscular: number;
    spinal: number;
    onCnsChange: (v: number) => void;
    onMuscularChange: (v: number) => void;
    onSpinalChange: (v: number) => void;
    onSave: () => void;
    onClose: () => void;
}

const CalibrationModal: React.FC<CalibrationModalProps> = ({ cns, muscular, spinal, onCnsChange, onMuscularChange, onSpinalChange, onSave, onClose }) => (
    <div className="fixed inset-0 z-[200] bg-black/80 flex items-end sm:items-center justify-center p-0 sm:p-4" onClick={onClose}>
        <div className="w-full max-w-md bg-[#0a0a0a] border border-white/10 rounded-2xl animate-slide-up sm:animate-fade-in overflow-hidden" onClick={e => e.stopPropagation()}>
            <div className="p-4 border-b border-white/5 flex justify-between items-center">
                <h3 className="text-base font-semibold text-white">Ajustar batería</h3>
                <button onClick={onClose} aria-label="Cerrar" className="p-1.5 text-zinc-500 hover:text-white rounded-full hover:bg-white/5">
                    <XIcon size={18} />
                </button>
            </div>
            <div className="p-5 space-y-4">
                <div>
                    <label className="block text-sm text-zinc-400 mb-2">Sistema nervioso central</label>
                    <input type="range" min="0" max="100" value={cns} onChange={e => onCnsChange(parseInt(e.target.value))} className="w-full h-2 accent-emerald-500 rounded-full" />
                    <span className="text-sm font-medium text-white ml-2">{cns}%</span>
                </div>
                <div>
                    <label className="block text-sm text-zinc-400 mb-2">Muscular</label>
                    <input type="range" min="0" max="100" value={muscular} onChange={e => onMuscularChange(parseInt(e.target.value))} className="w-full h-2 accent-amber-500 rounded-full" />
                    <span className="text-sm font-medium text-white ml-2">{muscular}%</span>
                </div>
                <div>
                    <label className="block text-sm text-zinc-400 mb-2">Columna</label>
                    <input type="range" min="0" max="100" value={spinal} onChange={e => onSpinalChange(parseInt(e.target.value))} className="w-full h-2 accent-rose-500 rounded-full" />
                    <span className="text-sm font-medium text-white ml-2">{spinal}%</span>
                </div>
                <div className="flex gap-2 pt-2">
                    <button onClick={onClose} className="flex-1 py-2.5 rounded-xl bg-white/5 text-zinc-400 text-sm font-medium hover:bg-white/10">Cancelar</button>
                    <button onClick={onSave} className="flex-1 py-2.5 rounded-xl bg-emerald-500 text-white text-sm font-medium hover:bg-emerald-400">Aplicar</button>
                </div>
            </div>
        </div>
    </div>
);

export interface AugeTelemetryPanelProps {
    compact?: boolean;
    shareable?: boolean;
    /** Hero mode: full-bleed, sin tarjeta, domina la pantalla */
    variant?: 'card' | 'hero';
}

export const AugeTelemetryPanel: React.FC<AugeTelemetryPanelProps> = ({ compact = false, shareable = true, variant = 'card' }) => {
    const { history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, muscleHierarchy, postSessionFeedback, waterLogs, isAppLoading } = useAppState();
    const { setSettings, addToast } = useAppDispatch();

    const [batteries, setBatteries] = useState<Awaited<ReturnType<typeof calculateGlobalBatteriesAsync>> | null>(null);
    const [perMuscle, setPerMuscle] = useState<Record<string, number>>({});
    const [selectedMuscle, setSelectedMuscle] = useState<{ id: string; label: string; value: number } | null>(null);
    const [focusedRing, setFocusedRing] = useState<'cns' | 'muscular' | 'spinal' | null>(null);
    const [calibrating, setCalibrating] = useState(false);
    const [calibCns, setCalibCns] = useState(0);
    const [calibMusc, setCalibMusc] = useState(0);
    const [calibSpinal, setCalibSpinal] = useState(0);
    const pressTimerRef = useRef<number | null>(null);
    const versionRef = useRef(0);

    const isDemo = useMemo(() => {
        if (!batteries || !perMuscle) return true;
        const hasHistory = history && history.length > 0;
        const hasMuscleData = Object.keys(perMuscle).some(k => (perMuscle[k] ?? 100) !== 100);
        return !hasHistory && !hasMuscleData;
    }, [batteries, perMuscle, history]);

    const globalValue = useMemo(() => {
        if (!batteries) return 82;
        if (calibrating) {
            const avg = (calibCns + calibMusc + calibSpinal) / 3;
            return Math.round(avg);
        }
        const weighted = (batteries.cns + batteries.muscular + batteries.spinal) / 3;
        return Math.round(weighted);
    }, [batteries, calibrating, calibCns, calibMusc, calibSpinal]);

    const muscleData = useMemo(() => {
        const map = new Map(ACCORDION_MUSCLES.map(m => [m.id, m.label]));
        if (isDemo) {
            return MOCK_MUSCLES.sort((a, b) => b.value - a.value);
        }
        return ACCORDION_MUSCLES.map(m => ({
            id: m.id,
            label: m.label,
            value: Math.round(perMuscle[m.id] ?? 100),
        })).sort((a, b) => b.value - a.value);
    }, [perMuscle, isDemo]);

    const cnsValue = useMemo(() => {
        if (!batteries) return 82;
        return calibrating ? calibCns : Math.round(batteries.cns);
    }, [batteries, calibrating, calibCns]);

    const muscularValue = useMemo(() => {
        if (calibrating) return calibMusc;
        if (muscleData.length > 0) {
            const avg = muscleData.reduce((s, m) => s + m.value, 0) / muscleData.length;
            return Math.round(avg);
        }
        if (batteries) return Math.round(batteries.muscular);
        return 75;
    }, [batteries, calibrating, calibMusc, muscleData]);

    const spinalValue = useMemo(() => {
        if (!batteries) return 88;
        return calibrating ? calibSpinal : Math.round(batteries.spinal);
    }, [batteries, calibrating, calibSpinal]);

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
        if (batteries && calibrating) {
            setCalibCns(batteries.cns);
            setCalibMusc(batteries.muscular);
            setCalibSpinal(batteries.spinal);
        }
    }, [batteries, calibrating]);

    const handleGlobalPointerDown = () => {
        if (calibrating) return;
        pressTimerRef.current = window.setTimeout(() => {
            if (batteries) {
                setCalibCns(batteries.cns);
                setCalibMusc(batteries.muscular);
                setCalibSpinal(batteries.spinal);
            }
            setCalibrating(true);
            pressTimerRef.current = null;
        }, 500);
    };

    const handleGlobalPointerUp = () => {
        if (pressTimerRef.current) {
            clearTimeout(pressTimerRef.current);
            pressTimerRef.current = null;
        }
    };

    const handleSaveCalibration = () => {
        if (!batteries) return;
        const calib = settings.batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0 };
        const cnsDelta = calibCns - (batteries.cns - (calib.cnsDelta ?? 0));
        const muscularDelta = calibMusc - (batteries.muscular - (calib.muscularDelta ?? 0));
        const spinalDelta = calibSpinal - (batteries.spinal - (calib.spinalDelta ?? 0));
        setSettings({
            batteryCalibration: { cnsDelta, muscularDelta, spinalDelta, lastCalibrated: new Date().toISOString() },
            hasPrecalibratedBattery: true,
        });
        addToast('Sistema recalibrado', 'success');
        setCalibrating(false);
    };

    const handleShare = async () => {
        try {
            await shareElementAsImage('battery-share-card', 'Mi Batería AUGE', 'ENTRENA CON KPKN');
        } catch (e) {
            addToast('No se pudo compartir', 'danger');
        }
    };

    if (!batteries && !isDemo) return <SkeletonLoader lines={4} className="rounded-xl" />;

    const isHero = variant === 'hero';

    return (
        <>
            {/* Tarjeta dedicada para compartir — off-screen, captura html2canvas */}
            <div className="fixed -left-[9999px] top-0 w-[540px] h-[960px] pointer-events-none overflow-hidden z-0">
                <BatteryShareCard cns={cnsValue} muscular={muscularValue} spinal={spinalValue} />
            </div>

        <div
            id="auge-telemetry-panel"
            data-testid="auge-telemetry-panel"
            className={
                isHero
                    ? 'w-full relative'
                    : 'bg-[#0a0a0a] border border-white/10 rounded-2xl overflow-hidden relative'
            }
        >
            {(!isHero || shareable) && (
                <div className={`flex justify-between items-center ${isHero ? 'absolute top-2 right-4 left-4 z-10' : 'px-4 py-2 border-b border-white/5'}`}>
                    {!isHero && <span className="text-sm font-medium text-zinc-400">Tu Batería</span>}
                    {shareable && (
                        <button onClick={handleShare} aria-label="Compartir" className={`p-2 -m-2 text-zinc-500 hover:text-white rounded-full hover:bg-white/5 transition-all active:scale-95 ${isHero ? 'ml-auto' : ''}`}>
                            <LinkIcon size={18} />
                        </button>
                    )}
                </div>
            )}

            <div className={isHero ? 'px-4 sm:px-6 py-4 sm:py-5' : 'p-4'}>
                {/* Anillos entrelazados estilo JJOO: 2 arriba (SNC, Muscular), 1 abajo (Spinal) — más grandes */}
                <div className="relative flex items-center justify-center mb-2 min-h-[185px]">
                    {focusedRing && (
                        <button
                            type="button"
                            className="absolute inset-0 z-0 cursor-default"
                            onClick={() => setFocusedRing(null)}
                            aria-label="Cerrar detalle"
                        />
                    )}
                    <div className="relative w-full max-w-[260px]" style={{ aspectRatio: '16/11', overflow: 'visible' }}>
                        <svg viewBox="0 0 160 110" className="block w-full h-full" preserveAspectRatio="xMidYMid meet" style={{ overflow: 'visible' }}>
                            {[
                                { id: 'cns' as const, cx: 45, cy: 38, value: cnsValue, label: 'SNC' },
                                { id: 'muscular' as const, cx: 115, cy: 38, value: muscularValue, label: 'Muscular' },
                                { id: 'spinal' as const, cx: 80, cy: 78, value: spinalValue, label: 'Columna' },
                            ].map(({ id, cx, cy, value, label }) => {
                                const isFocused = focusedRing === id;
                                const color = RING_COLORS[id];
                                return (
                                    <g
                                        key={id}
                                        transform={`translate(${cx}, ${cy}) rotate(-90) ${isFocused ? 'scale(1.25)' : ''}`}
                                        style={{ opacity: focusedRing && !isFocused ? 0.35 : 1, cursor: 'pointer', transition: 'opacity 0.2s' }}
                                        onClick={(e) => { e.stopPropagation(); setFocusedRing(isFocused ? null : id); }}
                                        onPointerDown={handleGlobalPointerDown}
                                        onPointerUp={handleGlobalPointerUp}
                                        onPointerLeave={handleGlobalPointerUp}
                                    >
                                        <circle r={RING_RADIUS} fill="none" stroke="#27272a" strokeWidth="4" style={{ filter: 'blur(1.3px)' }} />
                                        <circle r={RING_RADIUS} fill="none" stroke={color} strokeWidth="4" strokeDasharray={`${(value / 100) * RING_CIRCUMFERENCE} ${RING_CIRCUMFERENCE}`} strokeLinecap="round" style={{ transition: 'stroke-dasharray 0.5s', filter: 'blur(1.3px)' }} />
                                        <g transform="rotate(90)">
                                            <foreignObject x={-28} y={-12} width={56} height={24} style={{ overflow: 'visible' }}>
                                                <div xmlns="http://www.w3.org/1999/xhtml" style={{
                                                    width: '100%', height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                                                    fontFamily: 'system-ui, sans-serif', fontWeight: 500, fontSize: 9, color: 'rgba(161,161,170,0.95)', letterSpacing: '0.02em',
                                                }}>
                                                    <span style={{ lineHeight: 1 }}>{value}%</span>
                                                    <span style={{ fontSize: 5, color: 'rgba(113,113,122,0.9)', lineHeight: 1.2, marginTop: 1 }}>{label}</span>
                                                </div>
                                            </foreignObject>
                                        </g>
                                    </g>
                                );
                            })}
                        </svg>
                    </div>
                </div>
                {isDemo && (
                    <p className="text-[9px] text-zinc-600 text-center mb-1">Completa entrenamientos para datos reales</p>
                )}

                {/* Desglose muscular: 2x2 por página, carrusel con fade discreto */}
                <div className="relative w-full -mx-2 sm:-mx-4">
                    <div className="overflow-x-auto overflow-y-hidden no-scrollbar px-2 sm:px-4" style={{ scrollSnapType: 'x mandatory', WebkitOverflowScrolling: 'touch' }}>
                        <div className="flex gap-1">
                            {(() => {
                                const chunk = (arr: typeof muscleData, n: number) => {
                                    const out = [];
                                    for (let i = 0; i < arr.length; i += n) out.push(arr.slice(i, i + n));
                                    return out;
                                };
                                return chunk(muscleData, 4).map((page, pageIdx) => (
                                    <div key={pageIdx} className="grid grid-cols-2 gap-x-2 gap-y-2 shrink-0 snap-start flex-[0_0_94%]">
                                        {page.map(({ id, label, value }) => {
                                            const barColor = getMuscleBarColor(value);
                                            return (
                                                <button
                                                    key={id}
                                                    onClick={() => setSelectedMuscle({ id, label, value })}
                                                    className="flex items-center gap-0.5 py-1.5 hover:bg-white/[0.02] active:bg-white/[0.03] rounded transition-colors min-w-0"
                                                    data-testid={`muscle-${id}`}
                                                    aria-label={`${label} ${value}%`}
                                                >
                                                    <span className="text-[9px] text-zinc-500 truncate shrink-0 text-left w-12">{label}</span>
                                                    <div className="w-14 h-1.5 rounded-full overflow-hidden bg-zinc-800/80 shrink-0">
                                                        <div className="h-full rounded-full transition-all duration-500" style={{ width: `${value}%`, backgroundColor: barColor }} />
                                                    </div>
                                                    <span className="text-[9px] font-medium tabular-nums shrink-0" style={{ color: barColor }}>{value}%</span>
                                                </button>
                                            );
                                        })}
                                    </div>
                                ));
                            })()}
                        </div>
                    </div>
                    {/* Fade discreto en bordes — márgenes mínimos para más visibilidad */}
                    <div className="pointer-events-none absolute inset-y-0 left-0 w-2 sm:w-3" style={{ background: `linear-gradient(to right, ${isHero ? '#1a1a1a' : '#0a0a0a'}, transparent)` }} aria-hidden />
                    <div className="pointer-events-none absolute inset-y-0 right-0 w-2 sm:w-3" style={{ background: `linear-gradient(to left, ${isHero ? '#1a1a1a' : '#0a0a0a'}, transparent)` }} aria-hidden />
                </div>
            </div>

            {calibrating && batteries && (
                <CalibrationModal
                    cns={calibCns}
                    muscular={calibMusc}
                    spinal={calibSpinal}
                    onCnsChange={setCalibCns}
                    onMuscularChange={setCalibMusc}
                    onSpinalChange={setCalibSpinal}
                    onSave={handleSaveCalibration}
                    onClose={() => setCalibrating(false)}
                />
            )}

            {selectedMuscle && (
                <MuscleDetailModal
                    label={selectedMuscle.label}
                    value={selectedMuscle.value}
                    onClose={() => setSelectedMuscle(null)}
                    isDemo={isDemo}
                />
            )}
        </div>
        </>
    );
};
