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
            <div className="bg-[var(--md-sys-color-surface-container-low)] rounded-3xl p-6 border border-[var(--md-sys-color-outline-variant)]/50">
                <div className="flex items-center gap-3 mb-4">
                    <div className="bg-[var(--md-sys-color-primary-container)] p-3 rounded-2xl text-[var(--md-sys-color-on-primary-container)]">
                        <ActivityIcon size={16} />
                    </div>
                    <h4 className="text-label-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-widest opacity-70">
                        Historial de calibración KPKN
                    </h4>
                </div>
                <p className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] opacity-40 uppercase tracking-widest py-4 text-center">Todavía no hay calibraciones.</p>
            </div>
        );
    }

    return (
        <div className="bg-[var(--md-sys-color-surface-container-low)] rounded-3xl p-6 border border-[var(--md-sys-color-outline-variant)]/50 shadow-sm transition-all hover:bg-[var(--md-sys-color-surface-container)]">
            <div className="flex items-center gap-3 mb-5 pb-4 border-b border-[var(--md-sys-color-outline-variant)]/30">
                <div className="bg-[var(--md-sys-color-primary-container)] p-3 rounded-2xl text-[var(--md-sys-color-on-primary-container)] shadow-inner">
                    <ActivityIcon size={16} />
                </div>
                <h4 className="text-label-sm font-black text-[var(--md-sys-color-on-surface)] uppercase tracking-widest opacity-70">
                    Historial de calibración KPKN
                </h4>
            </div>
            <div className="space-y-3 max-h-[220px] overflow-y-auto custom-scrollbar pr-2">
                {entries.map((entry, idx) => (
                    <div
                        key={`${entry.date}-${idx}`}
                        className="py-3 px-4 rounded-2xl bg-[var(--md-sys-color-surface-container-high)] border border-[var(--md-sys-color-outline-variant)]/30 shadow-sm"
                    >
                        <div className="flex items-center justify-between gap-3 mb-2">
                            <span className="text-label-sm font-black text-[var(--md-sys-color-on-surface)] opacity-80 uppercase tracking-wider">{entry.date}</span>
                            <span
                                className={`text-[9px] font-black px-2 py-1 rounded-full uppercase border ${entry.source === 'manual'
                                        ? 'bg-[var(--md-sys-color-tertiary-container)] text-[var(--md-sys-color-on-tertiary-container)] border-[var(--md-sys-color-tertiary)]/20 shadow-sm'
                                        : 'bg-[var(--md-sys-color-primary-container)] text-[var(--md-sys-color-on-primary-container)] border-[var(--md-sys-color-primary)]/20 shadow-sm'
                                    }`}
                            >
                                {entry.source === 'manual' ? 'Manual' : 'Automática'}
                            </span>
                        </div>
                        <ul className="space-y-1.5 pt-1">
                            {entry.changes.map((c, i) => (
                                <li key={i} className="text-label-sm font-black text-[var(--md-sys-color-on-surface-variant)] opacity-50 flex items-center justify-between border-b border-[var(--md-sys-color-outline-variant)]/20 last:border-0 pb-1 last:pb-0">
                                    <span className="truncate pr-2">{c.muscle}</span>
                                    <span className="shrink-0 flex items-center gap-1">
                                        <span className="opacity-40">{c.prev.minEffectiveVolume}-{c.prev.maxRecoverableVolume}</span>
                                        <span className="text-[var(--md-sys-color-primary)]">→</span>
                                        <span className="text-[var(--md-sys-color-on-surface)] opacity-80 font-bold">{c.next.minEffectiveVolume}-{c.next.maxRecoverableVolume}</span>
                                    </span>
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
