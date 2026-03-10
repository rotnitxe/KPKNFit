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
    const timer = setTimeout(() => {
      if (inputRef.current) {
        inputRef.current.focus();
        inputRef.current.select();
      }
    }, 150);

    return () => clearTimeout(timer);
  }, []);

  const handleInput = (event: React.ChangeEvent<HTMLInputElement>) => {
    let nextValue = event.target.value.replace(',', '.');

    if (mode === 'integer') {
      nextValue = nextValue.replace(/[^0-9]/g, '');
    } else {
      nextValue = nextValue.replace(/[^0-9.]/g, '');
      const parts = nextValue.split('.');
      if (parts.length > 2) nextValue = parts[0] + '.' + parts.slice(1).join('');
    }

    onChange(nextValue);
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter') {
      hapticImpact(ImpactStyle.Medium);
      if (onNext) onNext();
      else onClose();
    }

    if (event.key === 'Escape') onClose();
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-[1000000] flex flex-col justify-start px-6 pt-[12vh] pointer-events-none">
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="workout-modal-backdrop absolute inset-0 pointer-events-auto"
          onClick={onClose}
        />

        <motion.div
          initial={{ opacity: 0, y: -40, scale: 0.92 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -20, scale: 0.95 }}
          transition={{ type: 'spring', damping: 25, stiffness: 200 }}
          className="pointer-events-auto relative z-10 mx-auto w-full max-w-sm overflow-hidden rounded-[36px] border border-white/75 liquid-glass-panel p-6 shadow-[0_24px_48px_rgba(78,56,24,0.18)]"
          onClick={(event) => event.stopPropagation()}
        >
          <div className="mb-5 flex items-center justify-between gap-4">
            <div className="min-w-0">
              <span className="inline-flex rounded-full border border-white/70 bg-[var(--md-sys-color-secondary-container)] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--md-sys-color-on-secondary-container)]">
                Entrada rapida
              </span>
              <p className="mt-3 truncate text-[20px] font-medium tracking-[-0.02em] text-[var(--md-sys-color-on-surface)]">
                {label}
              </p>
            </div>
            <button
              type="button"
              onClick={onClose}
              className="workout-pressable flex h-11 w-11 items-center justify-center rounded-full border border-[var(--md-sys-color-outline-variant)] bg-white/60 text-[var(--md-sys-color-on-surface-variant)]"
            >
              <span className="text-xl leading-none">x</span>
            </button>
          </div>

          <div className="mb-8 rounded-[28px] border border-white/75 bg-white/55 px-5 py-6 text-center shadow-[inset_0_1px_0_rgba(255,255,255,0.7)]">
            <input
              ref={inputRef}
              type="text"
              inputMode={mode === 'decimal' ? 'decimal' : 'numeric'}
              value={value}
              onChange={handleInput}
              onKeyDown={handleKeyDown}
              placeholder="0"
              className="w-full border-none bg-transparent p-0 text-center text-6xl font-medium tracking-[-0.05em] text-[var(--md-sys-color-on-surface)] placeholder:text-[var(--md-sys-color-outline-variant)] focus:ring-0 tabular-nums"
              autoFocus
            />
            <div className="mx-auto mt-4 h-1 w-full rounded-full bg-[var(--md-sys-color-outline-variant)]/60">
              <motion.div
                layoutId="activeInputLine"
                className="h-full rounded-full bg-[var(--md-sys-color-primary)]"
                style={{ width: `${Math.max(16, Math.min(100, (value.length || 1) * 22))}%` }}
              />
            </div>
            {mode === 'decimal' && (
              <p className="mt-3 text-[10px] font-semibold uppercase tracking-[0.14em] text-[var(--md-sys-color-on-surface-variant)]">
                Usa punto para decimales
              </p>
            )}
          </div>

          <div className="flex gap-3">
            {showNextButton && onNext && (
              <button
                type="button"
                onClick={() => {
                  hapticImpact(ImpactStyle.Light);
                  onNext();
                }}
                className="workout-pressable h-14 flex-1 rounded-full border border-[var(--md-sys-color-outline-variant)] bg-white/60 text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--md-sys-color-on-surface-variant)]"
              >
                Siguiente
              </button>
            )}
            <button
              type="button"
              onClick={() => {
                hapticImpact(ImpactStyle.Medium);
                onClose();
              }}
              className="workout-pressable h-14 flex-[1.4] rounded-full border border-white/75 bg-[var(--md-sys-color-primary)] text-[11px] font-black uppercase tracking-[0.18em] text-[var(--md-sys-color-on-primary)] shadow-[0_16px_30px_rgba(122,93,32,0.18)]"
            >
              Confirmar
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};

export default NumpadOverlay;
