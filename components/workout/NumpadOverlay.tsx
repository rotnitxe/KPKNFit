// components/workout/NumpadOverlay.tsx — Teclado numérico para peso/reps. Estética "Tú": gris claro, teclas blancas.
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
            <div className="bg-[#e5e5e5] px-4 pt-3 pb-[max(0.5rem,env(safe-area-inset-bottom))]" onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between mb-2">
                    <span className="text-[10px] font-semibold uppercase tracking-wide text-[#525252]">{label}</span>
                    <button type="button" onClick={onClose} className="text-[#525252] text-[10px] font-medium py-1 px-2 hover:text-[#1a1a1a]">Cerrar</button>
                </div>
                <div className="text-right text-2xl font-semibold text-[#1a1a1a] tabular-nums min-h-[2rem] mb-3 pr-1 break-all">
                    {value || '0'}
                </div>
                {showQuickLoad && (
                    <div className="flex gap-2 mb-3">
                        {[5, 10, 15, 20].map((n) => (
                            <button key={n} type="button" onClick={() => applyQuick(String(n))} className="flex-1 py-2.5 bg-white border border-[#a3a3a3] text-[#1a1a1a] font-medium text-sm hover:bg-[#f5f5f5] active:scale-[0.98] transition-all">
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
                                    <button key={`${ri}-${ci}-${k}`} type="button" onClick={() => handleKey(k)} className="w-14 h-12 min-w-[3.5rem] min-h-[3rem] shrink-0 bg-white border border-[#a3a3a3] text-[#1a1a1a] font-semibold text-lg hover:bg-[#f5f5f5] active:scale-[0.98] transition-all flex items-center justify-center">
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
                        <button type="button" onClick={onNext} className="flex-1 py-3 bg-white text-[#1a1a1a] font-semibold text-[10px] uppercase tracking-wide border border-[#a3a3a3] hover:bg-[#f5f5f5] min-h-[44px]">
                            Siguiente
                        </button>
                    )}
                    <button type="button" onClick={onClose} className="flex-1 py-3 bg-white text-[#1a1a1a] font-semibold text-[10px] uppercase tracking-wide border border-[#a3a3a3] hover:bg-[#f5f5f5] min-h-[44px]">
                        Listo
                    </button>
                </div>
            </div>
        </div>
    );
};

export default NumpadOverlay;
