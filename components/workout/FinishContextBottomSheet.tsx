// components/workout/FinishContextBottomSheet.tsx
// Bottom sheet al long-press en botón Finalizar: Pausar o Cancelar sesión

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
            <div className="fixed inset-0 z-[190] bg-black/40 backdrop-blur-sm animate-fade-in" onClick={onClose} aria-hidden />
            <div className="fixed left-0 right-0 bottom-0 z-[200] bg-[#0a0a0a] border-t border-white/10 rounded-t-xl shadow-2xl animate-slide-up pb-[max(1rem, env(safe-area-inset-bottom))]">
                <div className="w-10 h-1 bg-slate-600 rounded-full mx-auto mt-2 mb-4" aria-hidden />
                <div className="px-4 pb-4 space-y-2">
                    <button
                        onClick={() => { onPause(); onClose(); }}
                        className="w-full flex items-center gap-3 px-4 py-3 rounded-lg bg-slate-800/50 border border-white/5 hover:bg-amber-950/30 hover:border-amber-500/30 transition-colors text-left"
                    >
                        <PauseIcon size={20} className="text-amber-400" />
                        <span className="font-bold text-white">Pausar sesión</span>
                    </button>
                    <button
                        onClick={() => { onCancel(); onClose(); }}
                        className="w-full flex items-center gap-3 px-4 py-3 rounded-lg bg-slate-800/50 border border-white/5 hover:bg-red-950/30 hover:border-red-500/30 transition-colors text-left"
                    >
                        <XCircleIcon size={20} className="text-red-400" />
                        <span className="font-bold text-white">Cancelar sesión</span>
                    </button>
                </div>
            </div>
        </>
    );
};
