import React from 'react';
import { motion } from 'framer-motion';
import { hapticImpact, ImpactStyle } from '../../services/hapticsService';

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
        hapticImpact(ImpactStyle.Light);
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
        hapticImpact(ImpactStyle.Medium);
        const n = parseFloat(value || '0');
        const v = parseFloat(add);
        onChange(String(n + v));
    };

    const digits: string[][] = [['7', '8', '9'], ['4', '5', '6'], ['1', '2', '3'], ['.', '0', '⌫']];
    if (mode === 'integer') digits[3] = ['', '0', '⌫'];

    const showQuickLoad = mode === 'decimal';

    return (
        <div className="fixed inset-0 z-[200] flex flex-col justify-end">
            <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={onClose}
                className="absolute inset-0 bg-black/40 backdrop-blur-[2px]"
            />
            <motion.div
                initial={{ translateY: '100%' }}
                animate={{ translateY: 0 }}
                exit={{ translateY: '100%' }}
                transition={{ type: 'spring', damping: 28, stiffness: 220 }}
                className="relative liquid-glass-panel px-6 pt-5 pb-[max(1.5rem, env(safe-area-inset-bottom))] rounded-t-[40px] border-t border-white/10"
                style={{
                    background: 'linear-gradient(180deg, rgba(28, 27, 31, 0.9) 0%, rgba(15, 15, 15, 0.98) 100%)',
                    backdropFilter: 'blur(30px) saturate(160%)',
                    WebkitBackdropFilter: 'blur(30px) saturate(160%)',
                }}
                onClick={e => e.stopPropagation()}
            >
                {/* Handle */}
                <div className="self-center w-12 h-1.5 bg-white/10 rounded-full mb-6 mx-auto" />

                <div className="flex items-center justify-between mb-4">
                    <span className="text-[10px] font-bold uppercase tracking-[0.2em] text-white/40">{label}</span>
                    <button type="button" onClick={onClose} className="text-[var(--m3-primary)] text-[10px] font-bold uppercase tracking-widest py-1 px-3 bg-[var(--m3-primary)]/10 rounded-full">Listo</button>
                </div>

                <div className="flex justify-end items-baseline gap-2 mb-6 pr-2">
                    <span className="text-4xl font-black text-white tabular-nums tracking-tighter">
                        {value || '0'}
                    </span>
                    {mode === 'decimal' && <span className="text-sm font-bold text-white/30 uppercase tracking-widest">Kg</span>}
                </div>

                {showQuickLoad && (
                    <div className="flex gap-2.5 mb-6">
                        {[1.25, 2.5, 5, 10].map((n) => (
                            <button key={n} type="button" onClick={() => applyQuick(String(n))} className="workout-pressable flex-1 py-3 rounded-2xl bg-white/5 border border-white/10 text-white/90 font-bold text-xs hover:bg-white/10 active:scale-[0.98] transition-all">
                                +{n}
                            </button>
                        ))}
                    </div>
                )}

                <div className="grid grid-cols-3 gap-3 w-full max-w-sm mx-auto mb-6">
                    {digits.flat().map((k, i) => (
                        <button
                            key={i}
                            type="button"
                            onClick={() => k && handleKey(k)}
                            className={`workout-pressable aspect-[1.4/1] rounded-2xl flex items-center justify-center font-black text-xl transition-all border ${!k ? 'opacity-0 pointer-events-none' :
                                k === '⌫' ? 'bg-white/5 border-white/10 text-white/60' :
                                    'bg-white/5 border-white/10 text-white/90 hover:border-white/30'
                                }`}
                        >
                            {k}
                        </button>
                    ))}
                </div>

                <div className="flex gap-3 mt-2">
                    {showNextButton && onNext && (
                        <button type="button" onClick={onNext} className="workout-pressable flex-1 h-14 rounded-full bg-white/5 border border-white/10 text-white/60 font-black text-[10px] uppercase tracking-[0.2em] active:scale-[0.98] transition-all">
                            Siguiente
                        </button>
                    )}
                    <button type="button" onClick={onClose} className="workout-pressable flex-[2] h-14 rounded-full bg-[var(--m3-primary)] text-[var(--m3-on-primary)] font-black text-[10px] uppercase tracking-[0.2em] active:scale-[0.98] transition-all shadow-lg shadow-[var(--m3-primary)]/10">
                        Confirmar
                    </button>
                </div>
            </motion.div>
        </div>
    );
};


export default NumpadOverlay;

