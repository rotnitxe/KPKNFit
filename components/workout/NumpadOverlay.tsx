// components/workout/NumpadOverlay.tsx
// Teclado numerico para peso/reps con estilo M3 surface + glass.
import React from 'react';

export interface NumpadOverlayProps {
    value: string;
    onChange: (value: string) => void;
    onClose: () => void;
    onNext?: () => void;
    mode: 'decimal' | 'integer';
    label: string;
    showNextButton?: boolean;
}

const NumpadOverlay: React.FC<NumpadOverlayProps> = ({
    value,
    onChange,
    onClose,
    onNext,
    mode,
    label,
    showNextButton = true,
}) => {
    const handleKey = (key: string) => {
        if (key === '⌫') {
            onChange(value.slice(0, -1));
            return;
        }
        if (key === '.' && mode === 'integer') return;
        if (key === '.' && value.includes('.')) return;
        if (key === '.' && value === '') {
            onChange('0.');
            return;
        }
        onChange(value + key);
    };

    const applyQuick = (add: string) => {
        const n = parseFloat(value || '0');
        const v = parseFloat(add);
        onChange(String(n + v));
    };

    const digits: string[][] = [['7', '8', '9'], ['4', '5', '6'], ['1', '2', '3'], ['.', '0', '⌫']];
    if (mode === 'integer') digits[3] = ['', '0', '⌫'];

    const showQuickLoad = mode === 'decimal';

    return (
        <div className="fixed inset-0 z-[100] flex flex-col justify-end bg-black/40" onClick={onClose}>
            <div
                className="liquid-glass-panel px-4 pt-3 pb-[max(0.5rem,env(safe-area-inset-bottom))] rounded-t-[28px] border-t border-[var(--md-sys-color-outline-variant)]/50"
                style={{
                    background: 'linear-gradient(160deg, rgba(254,247,255,0.94) 0%, rgba(247,242,250,0.9) 100%)',
                    backdropFilter: 'blur(22px) saturate(140%)',
                    WebkitBackdropFilter: 'blur(22px) saturate(140%)',
                }}
                onClick={e => e.stopPropagation()}
            >
                <div className="flex items-center justify-between mb-2">
                    <span className="text-[11px] font-semibold uppercase tracking-wide text-[var(--md-sys-color-on-surface-variant)]">{label}</span>
                    <button type="button" onClick={onClose} className="text-[var(--md-sys-color-on-surface-variant)] text-[11px] font-medium py-1 px-2 hover:text-[var(--md-sys-color-on-surface)]">Cerrar</button>
                </div>
                <div className="text-right text-2xl font-semibold text-[var(--md-sys-color-on-surface)] tabular-nums min-h-[2rem] mb-3 pr-1 break-all">
                    {value || '0'}
                </div>
                {showQuickLoad && (
                    <div className="flex gap-2 mb-3">
                        {[5, 10, 15, 20].map((n) => (
                            <button key={n} type="button" onClick={() => applyQuick(String(n))} className="workout-pressable flex-1 py-2.5 rounded-[12px] bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface)] font-medium text-sm hover:border-[var(--md-sys-color-outline)] active:scale-[0.98] transition-all">
                                +{n}
                            </button>
                        ))}
                    </div>
                )}
                <div className="flex flex-col gap-2.5 w-full max-w-[280px] mx-auto mb-3">
                    {digits.map((row, ri) => (
                        <div key={ri} className="flex gap-2 justify-center">
                            {row.map((k, ci) =>
                                k ? (
                                    <button key={`${ri}-${ci}-${k}`} type="button" onClick={() => handleKey(k)} className="workout-pressable w-14 h-12 min-w-[3.5rem] min-h-[3rem] shrink-0 rounded-[12px] bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] text-[var(--md-sys-color-on-surface)] font-semibold text-lg hover:border-[var(--md-sys-color-outline)] active:scale-[0.98] transition-all flex items-center justify-center">
                                        {k}
                                    </button>
                                ) : (
                                    <div key={`empty-${ri}-${ci}`} className="w-14 h-12 min-w-[3.5rem] shrink-0" aria-hidden />
                                )
                            )}
                        </div>
                    ))}
                </div>
                <div className="flex gap-2">
                    {showNextButton && onNext && (
                        <button type="button" onClick={onNext} className="workout-pressable flex-1 py-3 rounded-[999px] bg-[var(--md-sys-color-secondary-container)] text-[var(--md-sys-color-on-secondary-container)] font-semibold text-[11px] uppercase tracking-wide border border-[var(--md-sys-color-outline-variant)] min-h-[44px]">
                            Siguiente
                        </button>
                    )}
                    <button type="button" onClick={onClose} className="workout-pressable flex-1 py-3 rounded-[999px] bg-[var(--md-sys-color-primary)] text-[var(--md-sys-color-on-primary)] font-semibold text-[11px] uppercase tracking-wide border border-[var(--md-sys-color-primary)] min-h-[44px]">
                        Listo
                    </button>
                </div>
            </div>
        </div>
    );
};

export default NumpadOverlay;

