import React, { useState, useRef, useMemo } from 'react';
import { XIcon, ActivityIcon, TargetIcon } from '../icons';
import { Session, ExerciseMuscleInfo } from '../../types';
import { calculateUnifiedMuscleVolume } from '../../services/volumeCalculator';

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
    severity?: 'info' | 'warning' | 'critical';
}

export type AvisoKind = 'volume' | 'neural' | 'suggestion';
export type CorrectionType = 'reduce_series' | 'reduce_rpe' | 'change_to_machine' | 'reduce_volume_rpe' | 'add_series';

export interface UnifiedAviso {
    id: string;
    kind: AvisoKind;
    message: string;
    severity: 'info' | 'warning' | 'critical';
    type?: string;
    muscle?: string;
    exerciseName?: string;
    correctionType?: CorrectionType;
}

interface DrainEstimate {
    msc: number;
    snc: number;
    spinal: number;
    totalSets: number;
    estimatedDuration: number;
    difficulty?: number;
}

interface AugeDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    session: Session;
    weekSessions: Session[];
    exerciseList: ExerciseMuscleInfo[];
    volumeAlerts: VolumeAlert[];
    neuralAlerts: NeuralAlert[];
    suggestions: AugeSuggestion[];
    sessionDrain: DrainEstimate;
    weeklyDrain: DrainEstimate;
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
            severity: sug.severity || 'warning',
            exerciseName: sug.exerciseName,
            correctionType: sug.severity === 'info' ? 'add_series' : 'reduce_rpe',
        });
    });
    return result;
};

/** Descripción clara para el usuario de qué hará la corrección al aplicarla. */
const getCorrectionDescription = (aviso: UnifiedAviso): string => {
    switch (aviso.correctionType) {
        case 'reduce_series':
            return aviso.muscle
                ? `Se quitará 1 serie en ejercicios de ${aviso.muscle}.`
                : 'Se quitará 1 serie en el ejercicio afectado.';
        case 'reduce_rpe':
            return 'Se bajará la intensidad en series pesadas: si usas RPE se bajará el RPE; si usas RIR se subirá el RIR; si usas Fallo se pasará a RIR; si usas %1RM se subirán repeticiones para bajar carga.';
        case 'change_to_machine':
            return 'Se bajará la intensidad (RPE/RIR/Fallo) en las series pesadas. Considera cambiar a variante máquina o cable.';
        case 'reduce_volume_rpe':
            return 'Se limitarán a 3 series y se bajará la intensidad (RPE/RIR/Fallo o repeticiones en %1RM) en las series pesadas.';
        case 'add_series':
            return aviso.muscle
                ? `Se añadirá 1 serie en ejercicios de ${aviso.muscle}.`
                : 'Se añadirá 1 serie en el ejercicio afectado.';
        default:
            return 'Se aplicará la corrección sugerida.';
    }
};

const getCorrectionButtonLabel = (aviso: UnifiedAviso): string => {
    switch (aviso.correctionType) {
        case 'reduce_series':
            return aviso.muscle ? `Quitar 1 serie en ${aviso.muscle}` : 'Quitar 1 serie';
        case 'reduce_rpe':
            return 'Bajar intensidad';
        case 'change_to_machine':
            return 'Bajar intensidad';
        case 'reduce_volume_rpe':
            return 'Reducir volumen e intensidad';
        case 'add_series':
            return aviso.muscle ? `Añadir 1 serie en ${aviso.muscle}` : 'Añadir serie';
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
                        : aviso.severity === 'info'
                            ? 'bg-[#00F19F]/5 border-[#00F19F]/15 hover:bg-[#00F19F]/10'
                            : 'bg-[#FFD60A]/5 border-[#FFD60A]/10 hover:bg-[#FFD60A]/10'
                }`}
            >
                <div className="flex items-start gap-2">
                    <div className={`w-1.5 h-1.5 rounded-full mt-1.5 shrink-0 ${
                        aviso.severity === 'critical' ? 'bg-[#FF3B30]' : aviso.severity === 'info' ? 'bg-[#00F19F]' : 'bg-[#FFD60A]'
                    }`} />
                    <div className="flex-1 min-w-0">
                        <p className="text-xs text-white leading-relaxed">{aviso.message}</p>
                        {aviso.kind === 'neural' && aviso.type && (
                            <span className="text-[10px] text-[#555] mt-0.5 block">{aviso.type}</span>
                        )}
                        {expanded && onApplyCorrection && (
                            <div className="mt-3 pt-3 border-t border-white/10 space-y-2">
                                <p className="text-[10px] text-[#999] leading-relaxed">{getCorrectionDescription(aviso)}</p>
                                <button
                                    onClick={handleApply}
                                    className="px-3 py-1.5 rounded-lg bg-[#00F0FF] text-white text-[10px] font-bold hover:brightness-110 transition-all"
                                >
                                    {getCorrectionButtonLabel(aviso)}
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
    isOpen, onClose, session, weekSessions, exerciseList,
    volumeAlerts, neuralAlerts, suggestions,
    sessionDrain, weeklyDrain,
    onAlertClick, onApplyCorrection, onDismissAviso, dismissedAvisoIds = new Set(),
}) => {
    const [volumeContext, setVolumeContext] = useState<'session' | 'week'>('session');
    const [drainContext, setDrainContext] = useState<'session' | 'week'>('session');
    const volumeData = useMemo(() => {
        const sessions = volumeContext === 'session' ? [session] : weekSessions;
        return calculateUnifiedMuscleVolume(sessions, exerciseList);
    }, [session, weekSessions, exerciseList, volumeContext]);
    const drain = drainContext === 'session' ? sessionDrain : weeklyDrain;

    if (!isOpen) return null;

    const allAvisos = buildUnifiedAvisos(volumeAlerts, neuralAlerts, suggestions);
    const visibleAvisos = allAvisos.filter(a => !dismissedAvisoIds.has(a.id));

    return (
        <>
            <div className="fixed inset-0 z-[110] bg-black/40" onClick={onClose} />
            <div className="fixed top-0 right-0 bottom-0 z-[111] w-[320px] max-w-[85vw] bg-[#111] border-l border-white/[0.08] flex flex-col animate-slide-left">
                <div className="flex items-center justify-between px-4 py-3 border-b border-white/[0.08]">
                    <div className="flex items-center gap-2">
                        <ActivityIcon size={16} className="text-[#00F0FF]" />
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
                        <div className="text-center py-4">
                            <ActivityIcon size={24} className="text-[#00F19F] mx-auto mb-2" />
                            <p className="text-xs text-[#999]">Sin avisos. Todo en orden.</p>
                        </div>
                    )}

                    {/* Volumen por músculo con switch sesión / semana */}
                    <section>
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-xs font-bold uppercase tracking-wide text-[#999] flex items-center gap-1.5">
                                <TargetIcon size={12} />
                                Volumen por músculo
                            </h3>
                            <div className="flex rounded-lg border border-white/10 overflow-hidden">
                                <button
                                    onClick={() => setVolumeContext('session')}
                                    className={`px-2.5 py-1 text-[9px] font-bold uppercase transition-colors ${volumeContext === 'session' ? 'bg-[#00F0FF] text-white' : 'bg-white/5 text-[#666] hover:text-white'}`}
                                >
                                    Sesión
                                </button>
                                <button
                                    onClick={() => setVolumeContext('week')}
                                    className={`px-2.5 py-1 text-[9px] font-bold uppercase transition-colors ${volumeContext === 'week' ? 'bg-[#00F0FF] text-white' : 'bg-white/5 text-[#666] hover:text-white'}`}
                                >
                                    Semana
                                </button>
                            </div>
                        </div>
                        {volumeData.length === 0 ? (
                            <p className="text-[10px] text-[#666] py-2">Agrega ejercicios para ver el volumen.</p>
                        ) : (
                            <div className="space-y-2 max-h-40 overflow-y-auto custom-scrollbar">
                                {volumeData.map(v => (
                                    <div key={v.muscleGroup} className="flex items-center gap-3">
                                        <span className="text-[10px] font-bold text-[#999] w-24 truncate">{v.muscleGroup}</span>
                                        <div className="flex-1 h-2 bg-white/5 rounded-full overflow-hidden">
                                            <div
                                                className={`h-full rounded-full transition-all ${v.displayVolume > 5 ? 'bg-red-500' : v.displayVolume > 3 ? 'bg-[#FFD60A]' : 'bg-[#00F19F]'}`}
                                                style={{ width: `${Math.min(100, (v.displayVolume / 8) * 100)}%` }}
                                            />
                                        </div>
                                        <span className="text-[10px] font-mono font-bold text-white w-8 text-right">{v.displayVolume.toFixed(1)}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </section>

                    {/* Fatiga AUGE con switch sesión / semana */}
                    <section>
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="text-xs font-bold uppercase tracking-wide text-[#999] flex items-center gap-1.5">
                                <ActivityIcon size={12} />
                                Fatiga AUGE
                            </h3>
                            <div className="flex rounded-lg border border-white/10 overflow-hidden">
                                <button
                                    onClick={() => setDrainContext('session')}
                                    className={`px-2.5 py-1 text-[9px] font-bold uppercase transition-colors ${drainContext === 'session' ? 'bg-[#00F0FF] text-white' : 'bg-white/5 text-[#666] hover:text-white'}`}
                                >
                                    Sesión
                                </button>
                                <button
                                    onClick={() => setDrainContext('week')}
                                    className={`px-2.5 py-1 text-[9px] font-bold uppercase transition-colors ${drainContext === 'week' ? 'bg-[#00F0FF] text-white' : 'bg-white/5 text-[#666] hover:text-white'}`}
                                >
                                    Semana
                                </button>
                            </div>
                        </div>
                        <div className="space-y-3">
                            <div className="flex items-center justify-between">
                                <span className="text-xs text-[#999]">Sets efectivos</span>
                                <span className="text-sm font-mono font-bold text-white">{drain.totalSets}</span>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-xs text-[#999]">Duración est.</span>
                                <span className="text-sm font-mono font-bold text-white">{drain.estimatedDuration}min</span>
                            </div>
                            {drain.difficulty != null && (
                                <div className="flex items-center justify-between">
                                    <span className="text-xs text-[#999]">Dificultad</span>
                                    <span className="text-sm font-mono font-bold text-white">{drain.difficulty}/10</span>
                                </div>
                            )}
                            <div className="space-y-2 pt-2">
                                <DrainBar label="MSC" value={drain.msc} />
                                <DrainBar label="SNC" value={drain.snc} />
                                <DrainBar label="Espinal" value={drain.spinal} />
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
