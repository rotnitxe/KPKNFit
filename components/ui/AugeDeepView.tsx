// components/ui/AugeDeepView.tsx
// ============================================================================
// AUGE Deep View — Componente reutilizable de visualización adaptativa
// Muestra: curva GP de fatiga, tendencia Banister, confianza bayesiana,
// score de auto-mejora. Cada sección es toggleable. SVG inline sin deps.
// ============================================================================

import React, { useState, useMemo } from 'react';
import {
    AugeAdaptiveCache,
    GPFatiguePrediction,
    BanisterSystemResult,
    getConfidenceLabel,
    getConfidenceColor,
} from '../../services/augeAdaptiveService';
import { ZapIcon, BrainIcon, ActivityIcon, TargetIcon, TrendingUpIcon, InfoIcon, ChevronDownIcon } from '../icons';

// ─── SVG Chart Helpers ──────────────────────────────────────────

function polylinePath(xs: number[], ys: number[], width: number, height: number, padding = 8): string {
    if (xs.length === 0) return '';
    const xMin = Math.min(...xs), xMax = Math.max(...xs);
    const yMin = Math.min(...ys), yMax = Math.max(...ys);
    const xRange = xMax - xMin || 1;
    const yRange = yMax - yMin || 1;
    return xs.map((x, i) => {
        const px = padding + ((x - xMin) / xRange) * (width - 2 * padding);
        const py = padding + (1 - (ys[i] - yMin) / yRange) * (height - 2 * padding);
        return `${i === 0 ? 'M' : 'L'}${px.toFixed(1)},${py.toFixed(1)}`;
    }).join(' ');
}

function areaPath(xs: number[], upper: number[], lower: number[], width: number, height: number, padding = 8): string {
    if (xs.length === 0) return '';
    const xMin = Math.min(...xs), xMax = Math.max(...xs);
    const allY = [...upper, ...lower];
    const yMin = Math.min(...allY), yMax = Math.max(...allY);
    const xRange = xMax - xMin || 1;
    const yRange = yMax - yMin || 1;
    const scale = (x: number, y: number) => ({
        px: padding + ((x - xMin) / xRange) * (width - 2 * padding),
        py: padding + (1 - (y - yMin) / yRange) * (height - 2 * padding),
    });
    const fwd = xs.map((x, i) => { const { px, py } = scale(x, upper[i]); return `${i === 0 ? 'M' : 'L'}${px.toFixed(1)},${py.toFixed(1)}`; }).join(' ');
    const rev = [...xs].reverse().map((x, i) => { const { px, py } = scale(x, lower[xs.length - 1 - i]); return `L${px.toFixed(1)},${py.toFixed(1)}`; }).join(' ');
    return `${fwd} ${rev} Z`;
}

function sparklinePath(ys: number[], width: number, height: number, padding = 4): string {
    if (ys.length < 2) return '';
    const yMin = Math.min(...ys), yMax = Math.max(...ys);
    const yRange = yMax - yMin || 1;
    const step = (width - 2 * padding) / (ys.length - 1);
    return ys.map((y, i) => {
        const px = padding + i * step;
        const py = padding + (1 - (y - yMin) / yRange) * (height - 2 * padding);
        return `${i === 0 ? 'M' : 'L'}${px.toFixed(1)},${py.toFixed(1)}`;
    }).join(' ');
}

// ─── Sub-components ─────────────────────────────────────────────

const SectionToggle: React.FC<{
    title: string;
    icon: React.ReactNode;
    color: string;
    isOpen: boolean;
    onToggle: () => void;
    children: React.ReactNode;
}> = ({ title, icon, color, isOpen, onToggle, children }) => (
    <div className="border border-white/5 rounded-xl overflow-hidden bg-[#0a0a0a]">
        <button onClick={onToggle} className="w-full flex justify-between items-center px-4 py-3 text-left hover:bg-white/[0.02] transition-colors">
            <span className={`flex items-center gap-2 text-[10px] font-black uppercase tracking-widest ${color}`}>
                {icon} {title}
            </span>
            <ChevronDownIcon size={14} className={`text-zinc-500 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
        </button>
        {isOpen && <div className="px-4 pb-4 animate-fade-in">{children}</div>}
    </div>
);

// ─── GP Fatigue Curve ───────────────────────────────────────────

export const GPFatigueCurve: React.FC<{
    data: GPFatiguePrediction | null;
    currentHour?: number;
    predictedLine?: { hours: number[]; values: number[] } | null;
    actualLine?: { hours: number[]; values: number[] } | null;
    compact?: boolean;
}> = ({ data, currentHour, predictedLine, actualLine, compact }) => {
    const w = compact ? 200 : 320;
    const h = compact ? 80 : 120;

    if (!data) return (
        <div className="flex items-center justify-center py-6">
            <p className="text-[9px] text-zinc-600 font-bold uppercase tracking-widest">Sin datos GP — Completa más sesiones</p>
        </div>
    );

    const { hours, mean_fatigue, upper_bound, lower_bound, peak_fatigue_hour, supercompensation_hour, full_recovery_hour } = data;

    return (
        <div>
            <svg viewBox={`0 0 ${w} ${h}`} className="w-full" preserveAspectRatio="xMidYMid meet">
                <path d={areaPath(hours, upper_bound, lower_bound, w, h)} fill="rgba(139,92,246,0.12)" />
                <path d={polylinePath(hours, mean_fatigue, w, h)} fill="none" stroke="#8b5cf6" strokeWidth="1.5" strokeLinecap="round" />
                {predictedLine && (
                    <path d={polylinePath(predictedLine.hours, predictedLine.values, w, h)} fill="none" stroke="#555" strokeWidth="1" strokeDasharray="3,3" />
                )}
                {actualLine && (
                    <path d={polylinePath(actualLine.hours, actualLine.values, w, h)} fill="none" stroke="#f43f5e" strokeWidth="1.5" />
                )}
                {currentHour !== undefined && (() => {
                    const xMin = Math.min(...hours), xMax = Math.max(...hours);
                    const xRange = xMax - xMin || 1;
                    const px = 8 + ((currentHour - xMin) / xRange) * (w - 16);
                    return <line x1={px} y1="4" x2={px} y2={h - 4} stroke="#facc15" strokeWidth="1" strokeDasharray="2,2" />;
                })()}
            </svg>
            {!compact && (
                <div className="flex justify-between text-[8px] text-zinc-500 font-bold mt-1 px-1">
                    <span>Pico: {peak_fatigue_hour}h</span>
                    {supercompensation_hour && <span className="text-emerald-500">Super: {supercompensation_hour}h</span>}
                    <span className="text-sky-400">Recup: {full_recovery_hour}h</span>
                </div>
            )}
        </div>
    );
};

// ─── Bayesian Confidence Indicator ──────────────────────────────

export const BayesianConfidence: React.FC<{
    totalObservations: number;
    personalizedRecoveryHours?: Record<string, number>;
    compact?: boolean;
}> = ({ totalObservations, personalizedRecoveryHours, compact }) => {
    const label = getConfidenceLabel(totalObservations);
    const color = getConfidenceColor(totalObservations);
    const pct = Math.min(100, (totalObservations / 25) * 100);
    const labelMap: Record<string, string> = { poblacional: 'Poblacional', baja: 'Aprendiendo', media: 'Personalizado', alta: 'Alta Precisión' };

    return (
        <div>
            <div className="flex justify-between items-center mb-2">
                <span className={`text-[9px] font-black uppercase tracking-widest ${color}`}>{labelMap[label]}</span>
                <span className="text-[9px] text-zinc-500 font-mono">{totalObservations} obs</span>
            </div>
            <div className="h-1.5 bg-black rounded-full overflow-hidden">
                <div className={`h-full rounded-full transition-all duration-700 ${
                    label === 'alta' ? 'bg-yellow-400' : label === 'media' ? 'bg-green-400' : label === 'baja' ? 'bg-blue-400' : 'bg-zinc-600'
                }`} style={{ width: `${pct}%` }} />
            </div>
            {!compact && personalizedRecoveryHours && Object.keys(personalizedRecoveryHours).length > 0 && (
                <div className="mt-3 space-y-1">
                    {Object.entries(personalizedRecoveryHours).slice(0, 5).map(([muscle, hrs]) => (
                        <div key={muscle} className="flex justify-between text-[9px]">
                            <span className="text-zinc-400">{muscle}</span>
                            <span className={`font-mono font-bold ${color}`}>{hrs.toFixed(0)}h</span>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

// ─── Banister Trend Chart ───────────────────────────────────────

export const BanisterTrend: React.FC<{
    systemData: BanisterSystemResult | null;
    compact?: boolean;
}> = ({ systemData, compact }) => {
    const w = compact ? 200 : 320;
    const h = compact ? 80 : 120;

    if (!systemData) return (
        <div className="flex items-center justify-center py-6">
            <p className="text-[9px] text-zinc-600 font-bold uppercase tracking-widest">Sin datos Banister — Entrena más</p>
        </div>
    );

    const { timeline_hours, fitness, fatigue, performance, next_optimal_session_hour } = systemData;

    return (
        <div>
            <svg viewBox={`0 0 ${w} ${h}`} className="w-full" preserveAspectRatio="xMidYMid meet">
                <path d={polylinePath(timeline_hours, fitness, w, h)} fill="none" stroke="#22c55e" strokeWidth="1.5" opacity="0.8" />
                <path d={polylinePath(timeline_hours, fatigue, w, h)} fill="none" stroke="#ef4444" strokeWidth="1.5" opacity="0.8" />
                <path d={polylinePath(timeline_hours, performance, w, h)} fill="none" stroke="#facc15" strokeWidth="2" />
            </svg>
            <div className="flex items-center justify-between mt-1 px-1">
                <div className="flex gap-3 text-[8px] font-bold">
                    <span className="flex items-center gap-1"><span className="w-2 h-0.5 bg-green-500 rounded-full inline-block"></span> Fitness</span>
                    <span className="flex items-center gap-1"><span className="w-2 h-0.5 bg-red-500 rounded-full inline-block"></span> Fatiga</span>
                    <span className="flex items-center gap-1"><span className="w-2 h-0.5 bg-yellow-400 rounded-full inline-block"></span> Performance</span>
                </div>
                {!compact && next_optimal_session_hour !== null && (
                    <span className="text-[8px] text-emerald-400 font-bold">Próxima sesión: {next_optimal_session_hour}h</span>
                )}
            </div>
        </div>
    );
};

// ─── Self-Improvement Score ─────────────────────────────────────

export const SelfImprovementScore: React.FC<{
    score: number;
    trend: number[];
    recommendations?: string[];
    compact?: boolean;
}> = ({ score, trend, recommendations, compact }) => {
    const trendColor = trend.length >= 2 && trend[trend.length - 1] >= trend[trend.length - 2] ? 'text-emerald-400' : 'text-rose-400';

    return (
        <div>
            <div className="flex items-center gap-3">
                <span className="text-2xl font-black text-white">{score}</span>
                <span className="text-[9px] text-zinc-500 font-bold">/100</span>
                {trend.length >= 2 && (
                    <svg viewBox="0 0 60 24" className="w-12 h-5 ml-auto">
                        <path d={sparklinePath(trend, 60, 24)} fill="none" className={`stroke-current ${trendColor}`} strokeWidth="1.5" />
                    </svg>
                )}
            </div>
            {!compact && recommendations && recommendations.length > 0 && (
                <div className="mt-3 space-y-1">
                    {recommendations.slice(0, 3).map((rec, i) => (
                        <div key={i} className="flex items-start gap-2 text-[9px] text-zinc-400">
                            <span className="text-yellow-400 mt-0.5 shrink-0">→</span>
                            <span>{rec}</span>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

// ─── Banister Verdict ───────────────────────────────────────────

export const BanisterVerdict: React.FC<{ banisterData: AugeAdaptiveCache['banister'] }> = ({ banisterData }) => {
    if (!banisterData) return null;
    return (
        <div className="flex items-start gap-2 py-2">
            <ZapIcon size={12} className="text-yellow-400 mt-0.5 shrink-0" />
            <p className="text-[9px] text-zinc-300 font-medium italic leading-relaxed">{banisterData.verdict || 'Datos insuficientes para veredicto Banister.'}</p>
        </div>
    );
};

// ─── Main Composite Component ───────────────────────────────────

interface AugeDeepViewProps {
    cache: AugeAdaptiveCache;
    showSections?: ('gp' | 'bayesian' | 'banister' | 'selfImprovement')[];
    compact?: boolean;
    defaultOpen?: boolean;
}

const AugeDeepView: React.FC<AugeDeepViewProps> = ({
    cache,
    showSections = ['gp', 'bayesian', 'banister', 'selfImprovement'],
    compact = false,
    defaultOpen = false,
}) => {
    const [isOpen, setIsOpen] = useState(defaultOpen);
    const [openSections, setOpenSections] = useState<Set<string>>(
        new Set(defaultOpen ? showSections : [showSections[0]])
    );

    const toggleSection = (s: string) => {
        setOpenSections(prev => {
            const next = new Set(prev);
            next.has(s) ? next.delete(s) : next.add(s);
            return next;
        });
    };

    if (!isOpen) {
        return (
            <button
                onClick={() => setIsOpen(true)}
                className="w-full py-2 flex items-center justify-center gap-2 text-[9px] font-black uppercase tracking-widest text-violet-400/70 hover:text-violet-400 transition-colors border border-white/5 rounded-xl hover:border-violet-500/20 bg-[#0a0a0a]"
            >
                <BrainIcon size={12} /> AUGE Deep View
                <ChevronDownIcon size={12} />
            </button>
        );
    }

    return (
        <div className="space-y-2 animate-fade-in">
            <button
                onClick={() => setIsOpen(false)}
                className="w-full py-1 flex items-center justify-center gap-2 text-[9px] font-black uppercase tracking-widest text-violet-400 hover:text-violet-300 transition-colors"
            >
                <BrainIcon size={12} /> Cerrar Deep View
                <ChevronDownIcon size={12} className="rotate-180" />
            </button>

            {showSections.includes('gp') && (
                <SectionToggle
                    title="Curva GP Fatiga"
                    icon={<ActivityIcon size={12} />}
                    color="text-violet-400"
                    isOpen={openSections.has('gp')}
                    onToggle={() => toggleSection('gp')}
                >
                    <GPFatigueCurve data={cache.gpCurve} compact={compact} />
                </SectionToggle>
            )}

            {showSections.includes('bayesian') && (
                <SectionToggle
                    title="Confianza Bayesiana"
                    icon={<TargetIcon size={12} />}
                    color="text-sky-400"
                    isOpen={openSections.has('bayesian')}
                    onToggle={() => toggleSection('bayesian')}
                >
                    <BayesianConfidence
                        totalObservations={cache.totalObservations}
                        personalizedRecoveryHours={cache.personalizedRecoveryHours}
                        compact={compact}
                    />
                </SectionToggle>
            )}

            {showSections.includes('banister') && (
                <SectionToggle
                    title="Tendencia Banister"
                    icon={<TrendingUpIcon size={12} />}
                    color="text-emerald-400"
                    isOpen={openSections.has('banister')}
                    onToggle={() => toggleSection('banister')}
                >
                    {cache.banister?.systems?.muscular ? (
                        <BanisterTrend systemData={cache.banister.systems.muscular} compact={compact} />
                    ) : (
                        <BanisterTrend systemData={null} compact={compact} />
                    )}
                    <BanisterVerdict banisterData={cache.banister} />
                </SectionToggle>
            )}

            {showSections.includes('selfImprovement') && cache.selfImprovement && (
                <SectionToggle
                    title="Auto-Mejora AUGE"
                    icon={<ZapIcon size={12} />}
                    color="text-yellow-400"
                    isOpen={openSections.has('selfImprovement')}
                    onToggle={() => toggleSection('selfImprovement')}
                >
                    <SelfImprovementScore
                        score={cache.selfImprovement.overall_prediction_score}
                        trend={cache.selfImprovement.improvement_trend}
                        recommendations={cache.selfImprovement.recommendations}
                        compact={compact}
                    />
                </SectionToggle>
            )}
        </div>
    );
};

export default AugeDeepView;
