import React from 'react';
import { VolumeSuggestion } from '../../services/volumeCalibrationService';
import { XIcon } from '../icons';

interface VolumeRecalibrationModalProps {
    suggestions: VolumeSuggestion[];
    programName?: string;
    onConfirm: () => void;
    onCancel: () => void;
}

const VolumeRecalibrationModal: React.FC<VolumeRecalibrationModalProps> = ({
    suggestions,
    programName,
    onConfirm,
    onCancel,
}) => {
    if (suggestions.length === 0) return null;

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 backdrop-blur-md p-6">
            <div className="bg-[var(--md-sys-color-surface-container-low)] border border-[var(--md-sys-color-outline-variant)]/50 rounded-[2.5rem] max-w-lg w-full max-h-[90vh] flex flex-col shadow-2xl overflow-hidden animate-in fade-in zoom-in duration-300">
                <div className="flex items-center justify-between p-6 pb-4">
                    <h2 className="text-title-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-widest opacity-80">
                        Ajuste de Volumen KPKN
                    </h2>
                    <button
                        onClick={onCancel}
                        className="p-3 -m-3 rounded-full text-[var(--md-sys-color-on-surface-variant)] hover:bg-[var(--md-sys-color-surface-variant)] transition-all active:scale-90"
                        aria-label="Cerrar"
                    >
                        <XIcon size={20} />
                    </button>
                </div>

                <div className="px-6 py-2 overflow-y-auto custom-scrollbar flex-1 min-h-0">
                    <p className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] opacity-40 uppercase tracking-widest mb-6 border-b border-[var(--md-sys-color-outline-variant)]/20 pb-4">
                        {programName ? `Sugerencias para "${programName}"` : 'Basado en tu feedback post-entreno.'}
                    </p>

                    <div className="space-y-3 mb-6">
                        {suggestions.map((s, idx) => (
                            <div key={idx} className="bg-[var(--md-sys-color-surface-container-highest)] p-5 rounded-3xl border border-[var(--md-sys-color-outline-variant)]/30 shadow-sm transition-all hover:scale-[1.02]">
                                <div className="flex justify-between items-center mb-4">
                                    <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-widest">{s.muscle}</span>
                                    <div className="flex items-center gap-2 bg-[var(--md-sys-color-surface-container-low)] px-3 py-1.5 rounded-full border border-[var(--md-sys-color-outline-variant)]/20">
                                        <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] line-through opacity-30">{s.currentRec.minEffectiveVolume}–{s.currentRec.maxRecoverableVolume}</span>
                                        <span className="text-[var(--md-sys-color-primary)] font-black">→</span>
                                        <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface)]">{s.suggestedRec.minEffectiveVolume}–{s.suggestedRec.maxRecoverableVolume}</span>
                                    </div>
                                </div>
                                <div className="bg-[var(--md-sys-color-primary-container)]/30 p-3 rounded-2xl border border-[var(--md-sys-color-primary)]/10">
                                    <p className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] opacity-60 leading-relaxed italic">
                                        "{s.reason}"
                                    </p>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                <div className="flex gap-4 p-6">
                    <button
                        onClick={onCancel}
                        className="flex-1 py-4 rounded-full border border-[var(--md-sys-color-outline-variant)] text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] uppercase tracking-widest hover:bg-[var(--md-sys-color-surface-variant)] transition-all active:scale-95"
                    >
                        Ignorar
                    </button>
                    <button
                        onClick={onConfirm}
                        className="flex-1 py-4 rounded-full bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] text-label-sm font-black uppercase tracking-widest hover:brightness-110 shadow-xl transition-all active:scale-95"
                    >
                        Calibrar
                    </button>
                </div>
            </div>
        </div>
    );
};

export default VolumeRecalibrationModal;
