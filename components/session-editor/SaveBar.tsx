import React from 'react';

interface SaveBarProps {
    hasChanges: boolean;
    modifiedCount: number;
    onSave: () => void;
    onCancel: () => void;
}

const SaveBar: React.FC<SaveBarProps> = ({ hasChanges, modifiedCount, onSave, onCancel }) => {
    return (
        <div className="sticky bottom-0 z-20 bg-black border-t border-white/[0.08] px-4 py-3 flex items-center gap-3">
            <button
                onClick={onCancel}
                className="px-4 py-2.5 rounded-lg text-xs font-medium text-[#999] hover:text-white hover:bg-white/5 transition-all"
            >
                Cancelar
            </button>
            <div className="flex-1" />
            {hasChanges && (
                <div className="flex items-center gap-1.5 mr-2">
                    <div className="w-1.5 h-1.5 rounded-full bg-[#FC4C02] animate-pulse" />
                    <span className="text-[10px] text-[#999] font-medium">
                        {modifiedCount > 1 ? `${modifiedCount} sesiones modificadas` : 'Sin guardar'}
                    </span>
                </div>
            )}
            <button
                onClick={onSave}
                disabled={!hasChanges}
                className={`px-6 py-2.5 rounded-lg text-xs font-bold transition-all ${
                    hasChanges
                        ? 'bg-[#FC4C02] text-white hover:brightness-110 shadow-[0_0_20px_rgba(252,76,2,0.2)]'
                        : 'bg-white/5 text-[#555] cursor-not-allowed'
                }`}
            >
                Guardar
            </button>
        </div>
    );
};

export default SaveBar;
