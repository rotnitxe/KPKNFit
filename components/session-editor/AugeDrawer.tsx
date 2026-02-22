import React, { useState, useRef } from 'react';
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

export type AvisoKind = 'volume' | 'neural' | 'suggestion';
export type CorrectionType = 'reduce_series' | 'reduce_rpe' | 'change_to_machine' | 'reduce_volume_rpe';

export interface UnifiedAviso {
    id: string;
    kind: AvisoKind;
    message: string;
    severity: 'warning' | 'critical';
    type?: string;
    muscle?: string;
    exerciseName?: string;
    correctionType?: CorrectionType;
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
    onApplyCorrection?: (aviso: UnifiedAviso) => void;
    onDismissAviso?: (id: string) => void;
    dismissedAvisoIds?: Set<string>;
}

const buildUnifiedAvisos = (
    volumeAlerts: VolumeAlert[],
    neuralAlerts: NeuralAlert[],
    suggestions: AugeSuggestion[]
): UnifiedAviso[] => {
    const result: UnifiedAviso[] = [];
    neuralAlerts.filter(a => a.severity === 'critical').forEach((a, i) => {
        result.push({
            id: `neural_critical_${a.type}_${i}`,
            kind: 'neural',
            message: a.message,
            severity: 'critical',
            type: a.type,
            correctionType: a.type === 'Espinal' ? 'change_to_machine' : a.type === 'Déficit' ? 'reduce_volume_rpe' : 'reduce_rpe',
        });
    });
    volumeAlerts.forEach((a, i) => {
        result.push({
            id: `volume_${a.muscle}_${i}`,
            kind: 'volume',
            message: a.message || `${a.muscle}: ${a.volume.toFixed(1)} sets (límite: ${a.threshold})`,
            severity: 'warning',
            muscle: a.muscle,
            correctionType: 'reduce_series',
        });
    });
    neuralAlerts.filter(a => a.severity === 'warning').forEach((a, i) => {
        result.push({
            id: `neural_warning_${a.type}_${i}`,
            kind: 'neural',
            message: a.message,
            severity: 'warning',
            type: a.type,
            correctionType: a.type === 'Espinal' ? 'change_to_machine' : a.type === 'Déficit' ? 'reduce_volume_rpe' : 'reduce_rpe',
        });
    });
    suggestions.forEach(sug => {
        result.push({
            id: sug.id,
            kind: 'suggestion',
            message: sug.message,
            severity: 'warning',
            exerciseName: sug.exerciseName,
            correctionType: 'reduce_rpe',
        });
    });
    return result;
};

const getCorrectionLabel = (aviso: UnifiedAviso): string => {
    switch (aviso.correctionType) {
        case 'reduce_series':
            return aviso.muscle ? `Reducir 1 serie en ${aviso.muscle}` : 'Reducir 1 serie';
        case 'reduce_rpe':
            return 'Reducir RPE en series pesadas';
        case 'change_to_machine':
            return 'Cambiar a variante máquina/cable';
        case 'reduce_volume_rpe':
            return 'Reducir volumen/RPE';
        default:
            return 'Aplicar corrección';
    }
};

const AvisoRow: React.FC<{
    aviso: UnifiedAviso;
    onDismiss: () => void;
    onApplyCorrection?: (aviso: UnifiedAviso) => void;
    onAlertClick?: (muscle: string) => void;
}> = ({ aviso, onDismiss, onApplyCorrection, onAlertClick }) => {
    const [expanded, setExpanded] = useState(false);
    const [swipeX, setSwipeX] = useState(0);
    const touchStartX = useRef(0);

    const handleTouchStart = (e: React.TouchEvent) => {
        touchStartX.current = e.touches[0].clientX;
    };
    const handleTouchMove = (e: React.TouchEvent) => {
        const dx = e.touches[0].clientX - touchStartX.current;
        if (dx < 0) setSwipeX(Math.max(-120, dx));
    };
    const handleTouchEnd = () => {
        if (swipeX < -80) {
            onDismiss();
        } else {
            setSwipeX(0);
        }
    };

    const handleClick = () => {
        if (aviso.kind === 'volume' && aviso.muscle && onAlertClick) {
            onAlertClick(aviso.muscle);
        }
        setExpanded(!expanded);
    };

    const handleApply = (e: React.MouseEvent) => {
        e.stopPropagation();
        onApplyCorrection?.(aviso);
        setExpanded(false);
    };

    return (
        <div
            className="transition-transform duration-150"
            style={{ transform: `translateX(${swipeX}px)` }}
            onTouchStart={handleTouchStart}
            onTouchMove={handleTouchMove}
            onTouchEnd={handleTouchEnd}
        >
            <div
                onClick={handleClick}
                className={`w-full text-left p-3 rounded-lg border transition-colors cursor-pointer ${
                    aviso.severity === 'critical'
                        ? 'bg-[#FF3B30]/5 border-[#FF3B30]/15 hover:bg-[#FF3B30]/10'
                        : 'bg-[#FFD60A]/5 border-[#FFD60A]/10 hover:bg-[#FFD60A]/10'
                }`}
            >
                <div className="flex items-start gap-2">
                    <div className={`w-1.5 h-1.5 rounded-full mt-1.5 shrink-0 ${aviso.severity === 'critical' ? 'bg-[#FF3B30]' : 'bg-[#FFD60A]'}`} />
                    <div className="flex-1 min-w-0">
                        <p className="text-xs text-white leading-relaxed">{aviso.message}</p>
                        {aviso.kind === 'neural' && aviso.type && (
                            <span className="text-[10px] text-[#555] mt-0.5 block">{aviso.type}</span>
                        )}
                        {expanded && onApplyCorrection && (
                            <div className="mt-3 pt-3 border-t border-white/10">
                                <button
                                    onClick={handleApply}
                                    className="px-3 py-1.5 rounded-lg bg-[#FC4C02] text-white text-[10px] font-bold hover:brightness-110 transition-all"
                                >
                                    {getCorrectionLabel(aviso)}
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

const AugeDrawer: React.FC<AugeDrawerProps> = ({
    isOpen, onClose, volumeAlerts, neuralAlerts, suggestions,
    totalSets, estimatedDuration, drainMsc, drainSnc, drainSpinal,
    onAlertClick, onApplyCorrection, onDismissAviso, dismissedAvisoIds = new Set(),
}) => {
    if (!isOpen) return null;

    const allAvisos = buildUnifiedAvisos(volumeAlerts, neuralAlerts, suggestions);
    const visibleAvisos = allAvisos.filter(a => !dismissedAvisoIds.has(a.id));

    return (
        <>
            <div className="fixed inset-0 z-[110] bg-black/40" onClick={onClose} />
            <div className="fixed top-0 right-0 bottom-0 z-[111] w-[320px] max-w-[85vw] bg-[#111] border-l border-white/[0.08] flex flex-col animate-slide-left">
                <div className="flex items-center justify-between px-4 py-3 border-b border-white/[0.08]">
                    <div className="flex items-center gap-2">
                        <ActivityIcon size={16} className="text-[#FC4C02]" />
                        <span className="text-sm font-semibold text-white">AUGE</span>
                    </div>
                    <button onClick={onClose} className="p-2 text-[#555] hover:text-white transition-colors -m-2">
                        <XIcon size={16} />
                    </button>
                </div>

                <div className="flex-1 overflow-y-auto custom-scrollbar p-4 space-y-6">
                    {visibleAvisos.length > 0 && (
                        <section>
                            <h3 className="text-xs font-bold uppercase tracking-wide text-[#999] mb-3">
                                Avisos ({visibleAvisos.length})
                            </h3>
                            <p className="text-[10px] text-[#666] mb-2">Desliza para descartar · Toca para corregir</p>
                            <div className="space-y-2">
                                {visibleAvisos.map(aviso => (
                                    <AvisoRow
                                        key={aviso.id}
                                        aviso={aviso}
                                        onDismiss={() => onDismissAviso?.(aviso.id)}
                                        onApplyCorrection={onApplyCorrection}
                                        onAlertClick={aviso.muscle ? () => onAlertClick?.(aviso.muscle!) : undefined}
                                    />
                                ))}
                            </div>
                        </section>
                    )}

                    {visibleAvisos.length === 0 && (
                        <div className="text-center py-8">
                            <ActivityIcon size={24} className="text-[#00F19F] mx-auto mb-2" />
                            <p className="text-xs text-[#999]">Sin avisos. Todo en orden.</p>
                        </div>
                    )}

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
