// components/workout/NumpadOverlay.tsx
// Inline numeric input that triggers native Android keyboard directly

import React, { useEffect, useRef, useCallback } from 'react';
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
    // Focus immediately when component mounts
    const timer = setTimeout(() => {
      if (inputRef.current) {
        inputRef.current.focus();
        inputRef.current.select();
      }
    }, 50);

    return () => clearTimeout(timer);
  }, []);

  const handleInput = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    let nextValue = event.target.value.replace(',', '.');

    if (mode === 'integer') {
      nextValue = nextValue.replace(/[^0-9]/g, '');
    } else {
      nextValue = nextValue.replace(/[^0-9.]/g, '');
      const parts = nextValue.split('.');
      if (parts.length > 2) nextValue = parts[0] + '.' + parts.slice(1).join('');
    }

    onChange(nextValue);
  }, [mode, onChange]);

  const handleKeyDown = useCallback((event: React.KeyboardEvent) => {
    if (event.key === 'Enter') {
      hapticImpact(ImpactStyle.Medium);
      if (onNext) onNext();
      else onClose();
    }

    if (event.key === 'Escape') onClose();
  }, [onNext, onClose]);

  const handleConfirm = useCallback(() => {
    hapticImpact(ImpactStyle.Medium);
    if (onNext) onNext();
    else onClose();
  }, [onNext, onClose]);

  return (
    <div className="fixed inset-x-0 bottom-0 z-[10000] px-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
      <div className="mx-auto max-w-md rounded-t-[32px] border border-[var(--md-sys-color-outline-variant)] bg-[var(--md-sys-color-surface-container-high)] p-4 shadow-[0_-8px_24px_rgba(0,0,0,0.3)]">
        <div className="mb-3 flex items-center justify-between">
          <span className="text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--md-sys-color-on-surface-variant)]">
            {label}
          </span>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full border border-[var(--md-sys-color-outline-variant)] bg-[var(--md-sys-color-surface)] text-[var(--md-sys-color-on-surface-variant)]"
          >
            <span className="text-lg leading-none">×</span>
          </button>
        </div>

        <div className="mb-4">
          <input
            ref={inputRef}
            type="text"
            inputMode={mode === 'decimal' ? 'decimal' : 'numeric'}
            value={value}
            onChange={handleInput}
            onKeyDown={handleKeyDown}
            placeholder="0"
            className="w-full border-none bg-[var(--md-sys-color-surface)] p-4 text-center text-4xl font-medium tracking-[-0.03em] text-[var(--md-sys-color-on-surface)] placeholder:text-[var(--md-sys-color-outline-variant)] focus:outline-none focus:ring-2 focus:ring-[var(--md-sys-color-primary)] tabular-nums rounded-[20px]"
            autoFocus
          />
        </div>

        <div className="flex gap-2">
          {showNextButton && onNext && (
            <button
              type="button"
              onClick={() => {
                hapticImpact(ImpactStyle.Light);
                onNext();
              }}
              className="flex-1 h-12 rounded-full border border-[var(--md-sys-color-outline-variant)] bg-[var(--md-sys-color-surface)] text-[10px] font-semibold uppercase tracking-[0.14em] text-[var(--md-sys-color-on-surface-variant)]"
            >
              Siguiente
            </button>
          )}
          <button
            type="button"
            onClick={handleConfirm}
            className="flex-[1.4] h-12 rounded-full border border-white/75 bg-[var(--md-sys-color-primary)] text-[10px] font-black uppercase tracking-[0.14em] text-[var(--md-sys-color-on-primary)] shadow-[0_8px_16px_rgba(122,93,32,0.12)]"
          >
            Confirmar
          </button>
        </div>
      </div>
    </div>
  );
};

export default NumpadOverlay;
