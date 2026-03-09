import React, { useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
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

/**
 * NumpadOverlay - Puente al teclado nativo del sistema.
 * Diseñado para aparecer en la parte superior del viewport para evitar el traslape 
 * del teclado virtual (overlaysContent: true) y mantener la vista estable.
 */
const NumpadOverlay: React.FC<NumpadOverlayProps> = ({
    value,
    onChange,
    onClose,
    onNext,
    mode,
    label,
    showNextButton = true,
}) => {
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        // Enfoque inmediato para disparar el teclado nativo
        const timer = setTimeout(() => {
            if (inputRef.current) {
                inputRef.current.focus();
                // Opcional: seleccionar texto para facilitar edición rápida
                inputRef.current.select();
            }
        }, 150);
        return () => clearTimeout(timer);
    }, []);

    const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
        let v = e.target.value.replace(',', '.'); // Normalizar coma decimal

        if (mode === 'integer') {
            v = v.replace(/[^0-9]/g, '');
        } else {
            v = v.replace(/[^0-9.]/g, '');
            // Evitar múltiples puntos
            const parts = v.split('.');
            if (parts.length > 2) v = parts[0] + '.' + parts.slice(1).join('');
        }
        onChange(v);
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            hapticImpact(ImpactStyle.Medium);
            if (onNext) onNext();
            else onClose();
        }
        if (e.key === 'Escape') onClose();
    };

    return (
        <AnimatePresence>
            <div className="fixed inset-0 z-[1000000] flex flex-col justify-start pt-[12vh] px-6 pointer-events-none">
                {/* Backdrop oscuro con blur para enfocar el input */}
                <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className="absolute inset-0 bg-black/60 backdrop-blur-xl pointer-events-auto"
                    onClick={onClose}
                />

                <motion.div
                    initial={{ opacity: 0, y: -40, scale: 0.92 }}
                    animate={{ opacity: 1, y: 0, scale: 1 }}
                    exit={{ opacity: 0, y: -20, scale: 0.95 }}
                    transition={{ type: 'spring', damping: 25, stiffness: 200 }}
                    className="relative z-10 w-full max-w-sm mx-auto liquid-glass-panel p-6 rounded-[32px] border border-white/10 shadow-2xl pointer-events-auto overflow-hidden"
                    style={{
                        background: 'linear-gradient(180deg, rgba(35, 35, 40, 0.95) 0%, rgba(20, 20, 25, 0.98) 100%)',
                    }}
                    onClick={e => e.stopPropagation()}
                >
                    {/* Header */}
                    <div className="flex items-center justify-between mb-5">
                        <div className="flex flex-col">
                            <span className="text-[10px] font-black uppercase tracking-[0.2em] text-[var(--m3-primary)]">Entrada de datos</span>
                            <span className="text-[14px] font-bold text-white/90">{label}</span>
                        </div>
                        <button
                            onClick={onClose}
                            className="workout-pressable h-10 w-10 flex items-center justify-center rounded-full bg-white/5 border border-white/10 text-white/40"
                        >
                            ✕
                        </button>
                    </div>

                    {/* Input Area */}
                    <div className="relative mb-8">
                        <input
                            ref={inputRef}
                            type="text"
                            inputMode={mode === 'decimal' ? 'decimal' : 'numeric'}
                            value={value}
                            onChange={handleInput}
                            onKeyDown={handleKeyDown}
                            placeholder="0"
                            className="w-full bg-transparent border-none text-6xl font-black text-white p-0 focus:ring-0 tabular-nums placeholder-white/5 text-center"
                            autoFocus
                        />
                        {mode === 'decimal' && (
                            <span className="absolute right-2 bottom-3 text-xs font-black text-[var(--m3-primary)]/40 uppercase tracking-widest">Kg</span>
                        )}

                        {/* Indicador de foco dinámico */}
                        <motion.div
                            layoutId="activeInputLine"
                            className="w-full h-1 bg-gradient-to-r from-transparent via-[var(--m3-primary)] to-transparent rounded-full mt-3 blur-[1px]"
                        />
                    </div>

                    {/* Action Buttons */}
                    <div className="flex gap-3">
                        {showNextButton && onNext && (
                            <button
                                onClick={() => { hapticImpact(ImpactStyle.Light); onNext(); }}
                                className="flex-1 h-14 rounded-full bg-white/5 border border-white/10 text-white font-bold text-[11px] uppercase tracking-[0.2em] active:scale-95 transition-transform"
                            >
                                Sig.
                            </button>
                        )}
                        <button
                            onClick={() => { hapticImpact(ImpactStyle.Medium); onClose(); }}
                            className="flex-[2] h-14 rounded-full bg-[var(--m3-primary)] text-[var(--m3-on-primary)] font-black text-[11px] uppercase tracking-[0.2em] shadow-lg shadow-[var(--m3-primary)]/20 active:scale-95 transition-transform"
                        >
                            Confirmar
                        </button>
                    </div>

                    {/* Quick Suggestions (opcional, manteniendo sutileza) */}
                    {mode === 'decimal' && (
                        <div className="mt-6 flex justify-center gap-4 text-white/20 text-[9px] font-bold uppercase tracking-widest">
                            <span>Sugerencia: {label.includes('Kg') ? 'Usa punto (.) para decimales' : ''}</span>
                        </div>
                    )}
                </motion.div>
            </div>
        </AnimatePresence>
    );
};


export default NumpadOverlay;

