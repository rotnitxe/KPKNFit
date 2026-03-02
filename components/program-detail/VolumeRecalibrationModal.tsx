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
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
            <div className="bg-[#1a1a1a] border border-white/10 rounded-2xl max-w-md w-full max-h-[85vh] flex flex-col shadow-xl">
                <div className="flex items-center justify-between p-4 border-b border-white/5">
                    <h2 className="text-sm font-black text-white uppercase tracking-wide">
                        Recalibrar volumen KPKN
                    </h2>
                    <button
                        onClick={onCancel}
                        className="p-2 -m-2 rounded-lg text-[#8E8E93] hover:text-white hover:bg-white/5 transition-colors"
                        aria-label="Cerrar"
                    >
                        <XIcon size={18} />
                    </button>
                </div>

                <div className="p-4 overflow-y-auto custom-scrollbar flex-1 min-h-0">
                    <p className="text-[10px] text-[#8E8E93] mb-4">
                        {programName ? `Aplicar cambios al programa "${programName}".` : 'Basado en tu feedback post-entreno.'}
                    </p>

                    <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                            <thead>
                                <tr className="border-b border-white/10">
                                    <th className="text-[9px] font-bold text-[#8E8E93] uppercase tracking-wider py-2 pr-2">Músculo</th>
                                    <th className="text-[9px] font-bold text-[#8E8E93] uppercase tracking-wider py-2 pr-2">Actual (min–máx)</th>
                                    <th className="text-[9px] font-bold text-[#8E8E93] uppercase tracking-wider py-2 pr-2">Sugerido</th>
                                    <th className="text-[9px] font-bold text-[#8E8E93] uppercase tracking-wider py-2 pr-2">Razón</th>
                                </tr>
                            </thead>
                            <tbody>
                                {suggestions.map((s, idx) => (
                                    <tr key={idx} className="border-b border-white/5">
                                        <td className="py-2.5 pr-2 text-xs font-bold text-white">
                                            {s.muscle}
                                        </td>
                                        <td className="py-2.5 pr-2 text-[10px] text-[#8E8E93]">
                                            {s.currentRec.minEffectiveVolume}–{s.currentRec.maxRecoverableVolume}
                                        </td>
                                        <td className="py-2.5 pr-2 text-[10px] font-bold text-cyan-400">
                                            {s.suggestedRec.minEffectiveVolume}–{s.suggestedRec.maxRecoverableVolume}
                                        </td>
                                        <td className="py-2.5 pr-2 text-[9px] text-[#8E8E93] max-w-[120px]">
                                            {s.reason}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>

                <div className="flex gap-2 p-4 border-t border-white/5">
                    <button
                        onClick={onCancel}
                        className="flex-1 py-2.5 rounded-xl border border-white/10 text-xs font-bold text-[#8E8E93] hover:bg-white/5 transition-colors"
                    >
                        Cancelar
                    </button>
                    <button
                        onClick={onConfirm}
                        className="flex-1 py-2.5 rounded-xl bg-cyan-500/20 border border-cyan-500/30 text-cyan-400 text-xs font-bold hover:bg-cyan-500/30 transition-colors"
                    >
                        Aplicar cambios
                    </button>
                </div>
            </div>
        </div>
    );
};

export default VolumeRecalibrationModal;
