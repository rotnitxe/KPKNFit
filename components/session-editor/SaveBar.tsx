import React from 'react';

interface SaveBarProps {
    hasChanges: boolean;
    modifiedCount: number;
    onSave: () => void;
    onCancel: () => void;
}

const SaveBar: React.FC<SaveBarProps> = ({ hasChanges, modifiedCount, onSave, onCancel }) => {
    return (
        <div className="fixed bottom-24 left-4 right-4 z-20 md:left-1/2 md:right-auto md:-translate-x-1/2 md:max-w-md pointer-events-auto">
            <div className="bg-[#0d0d0d]/95 backdrop-blur-xl border border-[#FC4C02]/30 rounded-2xl px-4 py-3 flex items-center gap-3 shadow-[0_0_30px_rgba(252,76,2,0.15)]">
                <button
                    onClick={onCancel}
                    className="px-4 py-2.5 rounded-xl text-xs font-medium text-[#999] hover:text-white hover:bg-white/5 transition-all"
                >
                    Cancelar
                </button>
                <div className="flex-1" />
                {hasChanges && (
                    <div className="flex items-center gap-1.5 mr-2">
                        <div className="w-1.5 h-1.5 rounded-full bg-[#FC4C02] animate-pulse" />
                        <span className="text-[10px] text-[#999] font-medium">
                            {modifiedCount > 1 ? `${modifiedCount} sesiones` : 'Sin guardar'}
                        </span>
                    </div>
                )}
                <button
                    onClick={onSave}
                    disabled={!hasChanges}
                    className={`px-6 py-2.5 rounded-xl text-xs font-bold transition-all ${
                        hasChanges
                            ? 'bg-[#FC4C02] text-white hover:brightness-110 shadow-[0_0_15px_rgba(252,76,2,0.3)]'
                            : 'bg-white/5 text-[#555] cursor-not-allowed'
                    }`}
                >
                    Guardar
                </button>
            </div>
        </div>
    );
};

export default SaveBar;
