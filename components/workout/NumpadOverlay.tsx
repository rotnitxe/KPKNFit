// components/workout/NumpadOverlay.tsx — Teclado numérico custom (Fricción Cero)
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

    const digits = [['1', '2', '3'], ['4', '5', '6'], ['7', '8', '9'], ['.', '0', '⌫']];
    if (mode === 'integer') digits[3] = ['', '0', '⌫'];

    return (
        <div className="fixed inset-0 z-[100] flex flex-col justify-end bg-black/60 backdrop-blur-sm" onClick={onClose}>
            <div className="bg-[#15171E] border-t border-[#2A2D38] rounded-t-2xl p-4 pb-[max(1rem,env(safe-area-inset-bottom))] shadow-xl" onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between mb-3">
                    <span className="text-[10px] font-mono font-black uppercase tracking-widest text-[#A0A7B8]">{label}</span>
                    <button type="button" onClick={onClose} className="text-[#00F0FF] text-xs font-bold">Cerrar</button>
                </div>
                <div className="text-right text-2xl font-mono font-black text-white tabular-nums min-h-[2.5rem] mb-4 pr-2">
                    {value || '0'}
                </div>
                <div className="grid grid-cols-3 gap-2 mb-3">
                    {digits.map((row, ri) => (
                        <div key={ri} className="flex gap-2 justify-center">
                            {row.map((k) => (
                                k ? (
                                    <button key={k} type="button" onClick={() => handleKey(k)} className="w-full max-w-[72px] h-12 rounded-lg bg-[#2A2D38] border border-[#2A2D38] text-white font-mono text-lg font-bold hover:border-[#00F0FF]/50 hover:bg-[#2A2D38]/80 active:scale-95 transition-all">
                                        {k}
                                    </button>
                                ) : <div key={`empty-${ri}`} className="w-full max-w-[72px]" />
                            ))}
                        </div>
                    ))}
                </div>
                <div className="flex gap-2">
                    {showNextButton && onNext && (
                        <button type="button" onClick={onNext} className="flex-1 py-3 rounded-lg bg-[#00F0FF] text-black font-mono font-black text-xs uppercase tracking-widest">
                            Siguiente
                        </button>
                    )}
                    <button type="button" onClick={onClose} className="flex-1 py-3 rounded-lg bg-[#2A2D38] text-white font-mono font-bold text-xs uppercase tracking-widest border border-[#2A2D38]">
                        Listo
                    </button>
                </div>
            </div>
        </div>
    );
};

export default NumpadOverlay;
