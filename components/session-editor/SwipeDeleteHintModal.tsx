import React from 'react';

const HINT_KEY = 'kpkn_seen_swipe_delete_hint';

interface SwipeDeleteHintModalProps {
    onClose: () => void;
}

export const SwipeDeleteHintModal: React.FC<SwipeDeleteHintModalProps> = ({ onClose }) => {
    const handleDismiss = () => {
        try { localStorage.setItem(HINT_KEY, '1'); } catch (_) {}
        onClose();
    };

    return (
        <div className="fixed inset-0 z-[500] flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm" onClick={handleDismiss}>
            <div
                className="bg-[#111] border border-white/10 rounded-2xl p-6 max-w-[320px] shadow-xl animate-modal-pop"
                onClick={e => e.stopPropagation()}
            >
                <div className="flex gap-4 items-start">
                    <img src="/CaupolicanAlerta.svg" alt="" className="w-14 h-14 object-contain flex-shrink-0" aria-hidden />
                    <div>
                        <h3 className="text-sm font-black text-white uppercase tracking-wider mb-2">¿Cómo eliminar una serie?</h3>
                        <p className="text-xs text-zinc-400 leading-relaxed mb-4">
                            Arrastra la serie hacia la derecha para eliminarla. Así ahorramos espacio y mantienes la interfaz limpia.
                        </p>
                        <button
                            onClick={handleDismiss}
                            className="w-full py-2.5 rounded-lg bg-[#00F0FF] text-black font-black text-[10px] uppercase tracking-widest hover:brightness-110 transition-all"
                        >
                            Entendido
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};
