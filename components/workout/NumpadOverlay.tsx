// components/workout/NumpadOverlay.tsx — Teclado numérico custom compacto (sin blur en fondo)
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

    const digits: string[][] = [['7', '8', '9'], ['4', '5', '6'], ['1', '2', '3'], ['.', '0', '⌫']];
    if (mode === 'integer') digits[3] = ['', '0', '⌫'];

    return (
        <div className="fixed inset-0 z-[100] flex flex-col justify-end bg-black/40" onClick={onClose}>
            <div className="bg-[#0a0a0a] border-t border-white/10 rounded-t-xl px-4 pt-3 pb-[max(0.5rem,env(safe-area-inset-bottom))] shadow-xl" onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between mb-2">
                    <span className="text-[9px] font-mono font-bold uppercase tracking-widest text-slate-500">{label}</span>
                    <button type="button" onClick={onClose} className="text-amber-400 text-[10px] font-bold py-1 px-2 hover:text-amber-300">Cerrar</button>
                </div>
                <div className="text-right text-2xl font-mono font-bold text-white tabular-nums min-h-[2rem] mb-3 pr-1 break-all">
                    {value || '0'}
                </div>
                <div className="flex flex-col gap-2.5 w-full max-w-[280px] mx-auto mb-3">
                    {digits.map((row, ri) => (
                        <div key={ri} className="flex gap-2 justify-center">
                            {row.map((k, ci) =>
                                k ? (
                                    <button key={`${ri}-${ci}-${k}`} type="button" onClick={() => handleKey(k)} className="w-14 h-12 min-w-[3.5rem] min-h-[3rem] shrink-0 rounded-lg bg-slate-800/80 border border-white/10 text-white font-mono text-lg font-bold hover:bg-slate-700/80 hover:border-amber-500/30 active:scale-95 transition-all flex items-center justify-center">
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
                        <button type="button" onClick={onNext} className="flex-1 py-2.5 rounded-lg bg-amber-600 text-white font-mono font-bold text-[10px] uppercase tracking-widest hover:bg-amber-500 transition-colors min-h-[44px]">
                            Siguiente
                        </button>
                    )}
                    <button type="button" onClick={onClose} className="flex-1 py-2.5 rounded-lg bg-slate-800 text-slate-300 font-mono font-bold text-[10px] uppercase tracking-widest border border-white/10 hover:bg-slate-700 transition-colors min-h-[44px]">
                        Listo
                    </button>
                </div>
            </div>
        </div>
    );
};

export default NumpadOverlay;
