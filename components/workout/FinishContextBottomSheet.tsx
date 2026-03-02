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
            <div className="fixed inset-0 z-[190] bg-black/30 animate-fade-in" onClick={onClose} aria-hidden />
            <div className="fixed left-0 right-0 bottom-0 z-[200] bg-[#e5e5e5] animate-slide-up pb-[max(1rem, env(safe-area-inset-bottom))]">
                <div className="w-10 h-1 bg-[#a3a3a3] mx-auto mt-2 mb-4" aria-hidden />
                <div className="px-4 pb-4 space-y-2">
                    <button
                        onClick={() => { onPause(); onClose(); }}
                        className="w-full flex items-center gap-3 px-4 py-3 bg-white border border-[#a3a3a3] hover:bg-[#f5f5f5] transition-colors text-left"
                    >
                        <PauseIcon size={20} className="text-[#525252]" />
                        <span className="font-bold text-[#1a1a1a]">Pausar sesión</span>
                    </button>
                    <button
                        onClick={() => { onCancel(); onClose(); }}
                        className="w-full flex items-center gap-3 px-4 py-3 bg-white border border-[#a3a3a3] hover:bg-[#f5f5f5] transition-colors text-left"
                    >
                        <XCircleIcon size={20} className="text-[#525252]" />
                        <span className="font-bold text-[#1a1a1a]">Cancelar sesión</span>
                    </button>
                </div>
            </div>
        </>
    );
};
