// components/workout/FinishContextBottomSheet.tsx
// Bottom sheet al long-press en boton Finalizar: Pausar o Cancelar sesion.

import React from 'react';
import { PauseIcon, XCircleIcon } from '../icons';

interface FinishContextBottomSheetProps {
    isOpen: boolean;
    onClose: () => void;
    onPause: () => void;
    onCancel: () => void;
}

export const FinishContextBottomSheet: React.FC<FinishContextBottomSheetProps> = ({
    isOpen,
    onClose,
    onPause,
    onCancel,
}) => {
    if (!isOpen) return null;
    return (
        <>
            <div className="fixed inset-0 z-[190] bg-black/30 backdrop-blur-[4px] animate-fade-in" onClick={onClose} aria-hidden />
            <div
              className="fixed left-0 right-0 bottom-0 z-[200] liquid-glass-panel animate-slide-up pb-[max(1rem, env(safe-area-inset-bottom))] rounded-t-[28px] border-t border-[var(--md-sys-color-outline-variant)]/50"
              style={{
                background: 'linear-gradient(160deg, rgba(254,247,255,0.92) 0%, rgba(247,242,250,0.86) 100%)',
                backdropFilter: 'blur(20px) saturate(140%)',
                WebkitBackdropFilter: 'blur(20px) saturate(140%)',
              }}
            >
                <div className="w-10 h-1 rounded-full bg-[var(--md-sys-color-outline)]/70 mx-auto mt-2 mb-4" aria-hidden />
                <div className="px-4 pb-4 space-y-2">
                    <button
                        onClick={() => { onPause(); onClose(); }}
                        className="workout-pressable w-full flex items-center gap-3 px-4 py-3 rounded-[16px] bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] hover:border-[var(--md-sys-color-outline)] transition-colors text-left"
                    >
                        <PauseIcon size={20} className="text-[var(--md-sys-color-primary)]" />
                        <span className="font-semibold text-[var(--md-sys-color-on-surface)]">Pausar sesion</span>
                    </button>
                    <button
                        onClick={() => { onCancel(); onClose(); }}
                        className="workout-pressable w-full flex items-center gap-3 px-4 py-3 rounded-[16px] bg-[var(--md-sys-color-surface)] border border-[var(--md-sys-color-outline-variant)] hover:border-[var(--md-sys-color-outline)] transition-colors text-left"
                    >
                        <XCircleIcon size={20} className="text-[var(--md-sys-color-error)]" />
                        <span className="font-semibold text-[var(--md-sys-color-on-surface)]">Cancelar sesion</span>
                    </button>
                </div>
            </div>
        </>
    );
};

