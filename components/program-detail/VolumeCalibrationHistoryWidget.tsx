import React from 'react';
import { VolumeCalibrationEntry } from '../../types';
import { ActivityIcon } from '../icons';

interface VolumeCalibrationHistoryWidgetProps {
    history: VolumeCalibrationEntry[] | undefined;
}

const VolumeCalibrationHistoryWidget: React.FC<VolumeCalibrationHistoryWidgetProps> = ({ history }) => {
    const entries = (history || []).slice().sort((a, b) => b.date.localeCompare(a.date));

    if (entries.length === 0) {
        return (
            <div className="bg-[#1a1a1a] rounded-xl p-4 border border-white/5">
                <div className="flex items-center gap-2 mb-3">
                    <div className="bg-cyan-500/20 p-2 rounded-lg text-cyan-400">
                        <ActivityIcon size={14} />
                    </div>
                    <h4 className="text-[10px] font-bold text-[#8E8E93] uppercase tracking-wide">
                        Historial de calibración KPKN
                    </h4>
                </div>
                <p className="text-[11px] text-[#8E8E93]">Aún no hay recalibraciones.</p>
            </div>
        );
    }

    return (
        <div className="bg-[#1a1a1a] rounded-xl p-4 border border-white/5">
            <div className="flex items-center gap-2 mb-3">
                <div className="bg-cyan-500/20 p-2 rounded-lg text-cyan-400">
                    <ActivityIcon size={14} />
                </div>
                <h4 className="text-[10px] font-bold text-[#8E8E93] uppercase tracking-wide">
                    Historial de calibración KPKN
                </h4>
            </div>
            <div className="space-y-2 max-h-[180px] overflow-y-auto custom-scrollbar">
                {entries.map((entry, idx) => (
                    <div
                        key={`${entry.date}-${idx}`}
                        className="py-2 px-3 rounded-lg bg-black/30 border border-white/5"
                    >
                        <div className="flex items-center justify-between gap-2 mb-1">
                            <span className="text-[11px] font-bold text-white">{entry.date}</span>
                            <span
                                className={`text-[9px] font-bold px-1.5 py-0.5 rounded ${
                                    entry.source === 'manual'
                                        ? 'bg-amber-500/20 text-amber-400'
                                        : 'bg-cyan-500/20 text-cyan-400'
                                }`}
                            >
                                {entry.source === 'manual' ? 'Manual' : 'Auto'}
                            </span>
                        </div>
                        <ul className="text-[10px] text-[#8E8E93] space-y-0.5">
                            {entry.changes.map((c, i) => (
                                <li key={i}>
                                    {c.muscle}: {c.prev.minEffectiveVolume}–{c.prev.maxRecoverableVolume} →{' '}
                                    {c.next.minEffectiveVolume}–{c.next.maxRecoverableVolume}
                                </li>
                            ))}
                        </ul>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default VolumeCalibrationHistoryWidget;
