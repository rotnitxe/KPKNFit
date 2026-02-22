import React from 'react';
import { XIcon, ActivityIcon } from '../icons';

interface VolumeAlert {
    muscle: string;
    volume: number;
    threshold: number;
    failRatio: number;
    message?: string;
}

interface NeuralAlert {
    type: string;
    message: string;
    severity: 'warning' | 'critical';
}

interface AugeSuggestion {
    id: string;
    message: string;
    exerciseName?: string;
    action?: () => void;
}

interface AugeDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    volumeAlerts: VolumeAlert[];
    neuralAlerts: NeuralAlert[];
    suggestions: AugeSuggestion[];
    totalSets: number;
    estimatedDuration: number;
    drainMsc: number;
    drainSnc: number;
    drainSpinal: number;
    onAlertClick?: (exerciseName: string) => void;
}

const AugeDrawer: React.FC<AugeDrawerProps> = ({
    isOpen, onClose, volumeAlerts, neuralAlerts, suggestions,
    totalSets, estimatedDuration, drainMsc, drainSnc, drainSpinal, onAlertClick,
}) => {
    if (!isOpen) return null;

    const allAlerts = [
        ...neuralAlerts.filter(a => a.severity === 'critical').map(a => ({ ...a, kind: 'neural' as const })),
        ...volumeAlerts.map(a => ({ ...a, severity: 'warning' as const, kind: 'volume' as const, message: a.message || `${a.muscle}: ${a.volume.toFixed(1)} sets (límite: ${a.threshold})` })),
        ...neuralAlerts.filter(a => a.severity === 'warning').map(a => ({ ...a, kind: 'neural' as const })),
    ];

    return (
        <>
            <div className="fixed inset-0 z-[110] bg-black/40" onClick={onClose} />
            <div className="fixed top-0 right-0 bottom-0 z-[111] w-[320px] max-w-[85vw] bg-[#111] border-l border-white/[0.08] flex flex-col animate-slide-left">
                {/* Header */}
                <div className="flex items-center justify-between px-4 py-3 border-b border-white/[0.08]">
                    <div className="flex items-center gap-2">
                        <ActivityIcon size={16} className="text-[#FC4C02]" />
                        <span className="text-sm font-semibold text-white">AUGE</span>
                    </div>
                    <button onClick={onClose} className="text-[#555] hover:text-white transition-colors">
                        <XIcon size={16} />
                    </button>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-y-auto custom-scrollbar p-4 space-y-6">
                    {/* Alerts feed */}
                    {allAlerts.length > 0 && (
                        <section>
                            <h3 className="text-xs font-bold uppercase tracking-wide text-[#999] mb-3">
                                Alertas ({allAlerts.length})
                            </h3>
                            <div className="space-y-2">
                                {allAlerts.map((alert, i) => (
                                    <button
                                        key={i}
                                        onClick={() => {
                                            if (alert.kind === 'volume' && 'muscle' in alert && onAlertClick) {
                                                onAlertClick(alert.muscle);
                                            }
                                        }}
                                        className={`w-full text-left p-3 rounded-lg border transition-colors ${
                                            alert.severity === 'critical'
                                                ? 'bg-[#FF3B30]/5 border-[#FF3B30]/15 hover:bg-[#FF3B30]/10'
                                                : 'bg-[#FFD60A]/5 border-[#FFD60A]/10 hover:bg-[#FFD60A]/10'
                                        }`}
                                    >
                                        <div className="flex items-start gap-2">
                                            <div className={`w-1.5 h-1.5 rounded-full mt-1.5 shrink-0 ${alert.severity === 'critical' ? 'bg-[#FF3B30]' : 'bg-[#FFD60A]'}`} />
                                            <div className="flex-1 min-w-0">
                                                <p className="text-xs text-white leading-relaxed">{alert.message}</p>
                                                {alert.kind === 'neural' && (
                                                    <span className="text-[10px] text-[#555] mt-0.5 block">{alert.type}</span>
                                                )}
                                            </div>
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </section>
                    )}

                    {/* AI Suggestions */}
                    {suggestions.length > 0 && (
                        <section>
                            <h3 className="text-xs font-bold uppercase tracking-wide text-[#999] mb-3">
                                Sugerencias
                            </h3>
                            <div className="space-y-2">
                                {suggestions.map(sug => (
                                    <div key={sug.id} className="p-3 rounded-lg bg-[#FC4C02]/5 border border-[#FC4C02]/10">
                                        <p className="text-xs text-white leading-relaxed mb-2">{sug.message}</p>
                                        {sug.exerciseName && (
                                            <span className="text-[10px] text-[#FC4C02] font-medium">{sug.exerciseName}</span>
                                        )}
                                        {sug.action && (
                                            <button
                                                onClick={sug.action}
                                                className="mt-2 px-3 py-1.5 rounded-lg bg-[#FC4C02] text-white text-[10px] font-bold hover:brightness-110 transition-all block"
                                            >
                                                Aplicar
                                            </button>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </section>
                    )}

                    {/* No alerts state */}
                    {allAlerts.length === 0 && suggestions.length === 0 && (
                        <div className="text-center py-8">
                            <ActivityIcon size={24} className="text-[#00F19F] mx-auto mb-2" />
                            <p className="text-xs text-[#999]">Sin alertas. Todo en orden.</p>
                        </div>
                    )}

                    {/* Quick summary */}
                    <section>
                        <h3 className="text-xs font-bold uppercase tracking-wide text-[#999] mb-3">Resumen</h3>
                        <div className="space-y-3">
                            <div className="flex items-center justify-between">
                                <span className="text-xs text-[#999]">Sets efectivos</span>
                                <span className="text-sm font-mono font-bold text-white">{totalSets}</span>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-xs text-[#999]">Duración est.</span>
                                <span className="text-sm font-mono font-bold text-white">{estimatedDuration}min</span>
                            </div>

                            {/* Drain bars */}
                            <div className="space-y-2 pt-2">
                                <DrainBar label="MSC" value={drainMsc} />
                                <DrainBar label="SNC" value={drainSnc} />
                                <DrainBar label="Espinal" value={drainSpinal} />
                            </div>
                        </div>
                    </section>
                </div>
            </div>
        </>
    );
};

const DrainBar: React.FC<{ label: string; value: number }> = ({ label, value }) => {
    const pct = Math.min(100, Math.max(0, value));
    const color = pct > 80 ? '#FF3B30' : pct > 50 ? '#FFD60A' : '#00F19F';
    return (
        <div className="flex items-center gap-2">
            <span className="w-14 text-[10px] font-bold text-[#555] text-right">{label}</span>
            <div className="flex-1 h-1.5 bg-white/5 rounded-full overflow-hidden">
                <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, backgroundColor: color }} />
            </div>
            <span className="w-8 text-[10px] font-mono text-[#999] text-right">{Math.round(pct)}%</span>
        </div>
    );
};

export default AugeDrawer;
