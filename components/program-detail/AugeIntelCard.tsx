import React from 'react';
import { ActivityIcon, ChevronDownIcon } from '../icons';
import { AugeAdaptiveCache } from '../../services/augeAdaptiveService';
import { BanisterTrend, BayesianConfidence, SelfImprovementScore } from '../ui/AugeDeepView';

interface AugeIntelCardProps {
    adaptiveCache: AugeAdaptiveCache;
    collapsed?: boolean;
    onToggleCollapse?: () => void;
}

const AugeIntelCard: React.FC<AugeIntelCardProps> = ({
    adaptiveCache, collapsed = false, onToggleCollapse,
}) => {
    return (
        <div className="bg-zinc-900/50 border border-white/5 rounded-2xl overflow-hidden transition-all duration-300">
            <button onClick={onToggleCollapse} className="w-full flex items-center justify-between p-4 text-left">
                <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-violet-500/10 border border-violet-500/20 flex items-center justify-center">
                        <ActivityIcon size={16} className="text-violet-400" />
                    </div>
                    <div>
                        <h3 className="text-xs font-black text-white uppercase tracking-widest">AUGE Intelligence</h3>
                        <p className="text-[9px] text-zinc-500 font-bold">
                            {adaptiveCache.totalObservations} observaciones
                        </p>
                    </div>
                </div>
                <ChevronDownIcon size={16} className={`text-zinc-500 transition-transform duration-300 ${collapsed ? '' : 'rotate-180'}`} />
            </button>

            {!collapsed && (
                <div className="px-4 pb-4 space-y-4 animate-fade-in">
                    {/* Banister Model */}
                    <div className="bg-zinc-950 border border-emerald-500/20 rounded-xl p-4">
                        <h4 className="text-[10px] font-black uppercase tracking-widest text-emerald-400 mb-3 flex items-center gap-1.5">
                            <ActivityIcon size={12} /> Banister — Fitness vs Fatiga
                        </h4>
                        {adaptiveCache.banister ? (
                            <>
                                {Object.entries(adaptiveCache.banister.systems || {}).map(([sys, data]) => (
                                    <div key={sys} className="mb-3">
                                        <p className="text-[8px] font-black uppercase tracking-widest text-zinc-500 mb-1.5">{sys}</p>
                                        <BanisterTrend systemData={data} />
                                    </div>
                                ))}
                                {adaptiveCache.banister.verdict && (
                                    <div className="mt-2 px-3 py-2 bg-black/40 rounded-lg border border-white/5">
                                        <p className="text-[9px] text-zinc-300 font-medium italic">{adaptiveCache.banister.verdict}</p>
                                    </div>
                                )}
                            </>
                        ) : (
                            <p className="text-[9px] text-zinc-600 text-center py-6">Entrena más sesiones para activar Banister.</p>
                        )}
                    </div>

                    {/* Recovery Map */}
                    <div className="bg-zinc-950 border border-sky-500/20 rounded-xl p-4">
                        <h4 className="text-[10px] font-black uppercase tracking-widest text-sky-400 mb-3">Recuperación Aprendida</h4>
                        <BayesianConfidence
                            totalObservations={adaptiveCache.totalObservations}
                            personalizedRecoveryHours={adaptiveCache.personalizedRecoveryHours}
                        />
                        {Object.keys(adaptiveCache.personalizedRecoveryHours).length > 0 ? (
                            <div className="mt-3 space-y-1.5">
                                {Object.entries(adaptiveCache.personalizedRecoveryHours).map(([muscle, hrs]) => {
                                    const pop = 72;
                                    const diff = hrs - pop;
                                    return (
                                        <div key={muscle} className="flex items-center justify-between bg-black p-2 rounded-lg border border-white/5">
                                            <span className="text-[9px] font-bold text-zinc-300">{muscle}</span>
                                            <div className="flex items-center gap-2 text-[8px] font-mono">
                                                <span className="text-zinc-500">{pop}h</span>
                                                <span className={`font-bold ${diff < 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                                                    {hrs.toFixed(0)}h ({diff > 0 ? '+' : ''}{diff.toFixed(0)})
                                                </span>
                                                <div className="w-10 h-1 bg-black rounded-full overflow-hidden">
                                                    <div className={`h-full rounded-full ${diff < 0 ? 'bg-emerald-400' : 'bg-rose-400'}`} style={{ width: `${Math.min(100, Math.abs(diff) / pop * 100 + 50)}%` }} />
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <p className="text-[9px] text-zinc-600 text-center py-4 mt-2">Necesita más datos.</p>
                        )}
                    </div>

                    {/* Self Improvement */}
                    <div className="bg-zinc-950 border border-yellow-500/20 rounded-xl p-4">
                        <h4 className="text-[10px] font-black uppercase tracking-widest text-yellow-400 mb-3">Auto-Mejora</h4>
                        {adaptiveCache.selfImprovement ? (
                            <>
                                <SelfImprovementScore
                                    score={adaptiveCache.selfImprovement.overall_prediction_score}
                                    trend={adaptiveCache.selfImprovement.improvement_trend}
                                    recommendations={adaptiveCache.selfImprovement.recommendations}
                                />
                                {adaptiveCache.selfImprovement.accuracy_by_system.length > 0 && (
                                    <div className="mt-3 space-y-1.5 border-t border-white/5 pt-3">
                                        <p className="text-[8px] font-black uppercase tracking-widest text-zinc-500 mb-1.5">Precisión por Sistema</p>
                                        {adaptiveCache.selfImprovement.accuracy_by_system.map(sys => (
                                            <div key={sys.system} className="flex items-center justify-between bg-black p-2 rounded-lg">
                                                <span className="text-[9px] font-bold text-zinc-300 uppercase">{sys.system}</span>
                                                <div className="flex items-center gap-2 text-[8px] font-mono">
                                                    <span className="text-zinc-500">R²={sys.r_squared.toFixed(2)}</span>
                                                    <span className={`font-bold ${sys.r_squared > 0.7 ? 'text-emerald-400' : sys.r_squared > 0.4 ? 'text-yellow-400' : 'text-rose-400'}`}>
                                                        {sys.sample_size} obs
                                                    </span>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </>
                        ) : (
                            <p className="text-[9px] text-zinc-600 text-center py-6">Necesita más datos para evaluar precisión.</p>
                        )}
                    </div>

                    {/* Findings */}
                    {(adaptiveCache.selfImprovement?.recommendations?.length ?? 0) > 0 && (
                        <div className="bg-zinc-950 border border-violet-500/20 rounded-xl p-4">
                            <h4 className="text-[10px] font-black uppercase tracking-widest text-violet-400 mb-2">Hallazgos</h4>
                            <div className="space-y-1.5">
                                {adaptiveCache.selfImprovement!.recommendations.map((rec, i) => (
                                    <div key={i} className="flex items-start gap-1.5 bg-black p-2 rounded-lg border border-white/5">
                                        <span className="text-violet-400 mt-0.5 shrink-0 text-[10px]">→</span>
                                        <p className="text-[9px] text-zinc-300 leading-relaxed">{rec}</p>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default AugeIntelCard;
