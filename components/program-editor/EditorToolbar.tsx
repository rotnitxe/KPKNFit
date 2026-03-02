import React, { useState } from 'react';
import { SaveIcon, XIcon, ChevronDownIcon, TrashIcon, EditIcon } from '../icons';

interface EditorToolbarProps {
    programName: string;
    onChangeName: (name: string) => void;
    hasUnsavedChanges: boolean;
    onSave: () => void;
    onCancel: () => void;
    onDuplicate?: () => void;
    onExport?: () => void;
    onDelete?: () => void;
}

const EditorToolbar: React.FC<EditorToolbarProps> = ({
    programName, onChangeName, hasUnsavedChanges,
    onSave, onCancel, onDuplicate, onExport, onDelete,
}) => {
    const [menuOpen, setMenuOpen] = useState(false);

    return (
        <div className="flex items-center justify-between px-4 py-2.5 border-b border-[#3f3f3f] bg-[#1a1a1a] shrink-0">
            {/* Left: Back + Name */}
            <div className="flex items-center gap-3 flex-1 min-w-0">
                <button
                    onClick={onCancel}
                    className="p-1.5 rounded-full border border-[#3f3f3f] text-[#a3a3a3] hover:text-white transition-colors shrink-0"
                >
                    <XIcon size={16} />
                </button>
                <div className="flex items-center gap-2 flex-1 min-w-0">
                    <EditIcon size={14} className="text-[#737373] shrink-0" />
                    <input
                        type="text"
                        value={programName}
                        onChange={e => onChangeName(e.target.value)}
                        className="bg-transparent text-sm font-medium text-white focus:ring-0 border-none p-0 flex-1 min-w-0"
                        placeholder="Nombre del programa"
                    />
                </div>
                {hasUnsavedChanges && (
                    <div className="w-2 h-2 rounded-full bg-[#a3a3a3] shrink-0 animate-pulse" title="Cambios sin guardar" />
                )}
            </div>

            {/* Right: Actions */}
            <div className="flex items-center gap-2 shrink-0 ml-3">
                <button
                    onClick={onSave}
                    className="flex items-center gap-1.5 px-4 py-2 bg-white text-[#1a1a1a] text-[9px] font-medium uppercase tracking-widest hover:bg-[#e5e5e5] transition-colors"
                >
                    <SaveIcon size={12} /> Guardar
                </button>

                <div className="relative">
                    <button
                        onClick={() => setMenuOpen(!menuOpen)}
                        className="p-2 rounded-lg border border-[#3f3f3f] text-[#a3a3a3] hover:text-white transition-colors"
                    >
                        <ChevronDownIcon size={14} />
                    </button>
                    {menuOpen && (
                        <>
                            <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
                            <div className="absolute top-full right-0 mt-1 bg-[#252525] border border-[#3f3f3f] overflow-hidden z-50 min-w-[160px] animate-fade-in">
                                {onDuplicate && (
                                    <button
                                        onClick={() => { onDuplicate(); setMenuOpen(false); }}
                                        className="w-full text-left px-4 py-2.5 text-[10px] font-medium text-[#a3a3a3] hover:bg-[#2a2a2a] hover:text-white transition-colors"
                                    >
                                        Duplicar
                                    </button>
                                )}
                                {onExport && (
                                    <button
                                        onClick={() => { onExport(); setMenuOpen(false); }}
                                        className="w-full text-left px-4 py-2.5 text-[10px] font-medium text-[#a3a3a3] hover:bg-[#2a2a2a] hover:text-white transition-colors"
                                    >
                                        Exportar JSON
                                    </button>
                                )}
                                {onDelete && (
                                    <button
                                        onClick={() => { onDelete(); setMenuOpen(false); }}
                                        className="w-full text-left px-4 py-2.5 text-[10px] font-medium text-red-400 hover:bg-[#2a2a2a] transition-colors flex items-center gap-2"
                                    >
                                        <TrashIcon size={12} /> Eliminar
                                    </button>
                                )}
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};

export default EditorToolbar;
