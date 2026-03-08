import React, { useState } from 'react';
import { SaveIcon, XIcon, ChevronDownIcon, TrashIcon, EditIcon, MenuIcon } from '../icons';

interface EditorToolbarProps {
    programName: string;
    onChangeName: (name: string) => void;
    hasUnsavedChanges: boolean;
    onSave: () => void;
    onCancel: () => void;
    onDuplicate?: () => void;
    onExport?: () => void;
    onDelete?: () => void;
    onToggleSidebar?: () => void;
}

const EditorToolbar: React.FC<EditorToolbarProps> = ({
    programName, onChangeName, hasUnsavedChanges,
    onSave, onCancel, onDuplicate, onExport, onDelete, onToggleSidebar,
}) => {
    const [menuOpen, setMenuOpen] = useState(false);

    return (
        <div className="flex items-center justify-between px-6 py-4 border-b border-[var(--md-sys-color-outline-variant)] bg-white/80 backdrop-blur-md shrink-0 sticky top-0 z-[100]">
            {/* Left: Back + Name */}
            <div className="flex items-center gap-4 flex-1 min-w-0">
                {onToggleSidebar && (
                    <button
                        onClick={onToggleSidebar}
                        className="w-10 h-10 rounded-full flex items-center justify-center text-black hover:bg-[var(--md-sys-color-surface-container-highest)] transition-all active:scale-90"
                    >
                        <MenuIcon size={20} />
                    </button>
                )}
                <div className="flex items-center gap-3 flex-1 min-w-0">
                    <EditIcon size={16} className="text-black/40 shrink-0" />
                    <input
                        type="text"
                        value={programName}
                        onChange={e => onChangeName(e.target.value)}
                        className="bg-transparent text-lg font-black text-black uppercase tracking-tighter focus:ring-0 border-none p-0 flex-1 min-w-0 placeholder-black/10"
                        placeholder="Nombre del programa"
                    />
                </div>
                {hasUnsavedChanges && (
                    <div className="w-2.5 h-2.5 rounded-full bg-[var(--md-sys-color-primary)] shrink-0 animate-pulse shadow-[0_0_8px_rgba(var(--md-sys-color-primary-rgb),0.5)]" title="Cambios sin guardar" />
                )}
            </div>

            {/* Right: Actions */}
            <div className="flex items-center gap-3 shrink-0 ml-4">
                <button
                    onClick={onSave}
                    className="flex items-center gap-2 px-6 py-2.5 bg-black text-white rounded-xl text-[10px] font-black uppercase tracking-widest hover:bg-black/90 hover:shadow-lg transition-all active:scale-95"
                >
                    <SaveIcon size={14} /> Guardar
                </button>

                <div className="relative">
                    <button
                        onClick={() => setMenuOpen(!menuOpen)}
                        className="w-10 h-10 rounded-xl border border-[var(--md-sys-color-outline-variant)] flex items-center justify-center text-black/60 hover:text-black hover:bg-[var(--md-sys-color-surface-container-highest)] transition-all"
                    >
                        <ChevronDownIcon size={16} />
                    </button>
                    {menuOpen && (
                        <>
                            <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
                            <div className="absolute top-full right-0 mt-2 bg-white border border-[var(--md-sys-color-outline-variant)] rounded-2xl shadow-2xl overflow-hidden z-50 min-w-[200px] animate-in fade-in slide-in-from-top-2 duration-200">
                                <div className="px-4 py-2 mb-1 border-b border-[var(--md-sys-color-surface-container-low)]">
                                    <span className="text-[9px] font-black text-black/40 uppercase tracking-widest">Opciones</span>
                                </div>
                                {onCancel && (
                                    <button
                                        onClick={() => { onCancel(); setMenuOpen(false); }}
                                        className="w-full text-left px-4 py-2 mb-1 border-b border-[var(--md-sys-color-surface-container-low)] text-[11px] font-black uppercase tracking-tight text-black/60 hover:bg-[var(--md-sys-color-surface-container-low)] hover:text-black transition-colors"
                                    >
                                        Descartar Cambios
                                    </button>
                                )}
                                {onDuplicate && (
                                    <button
                                        onClick={() => { onDuplicate(); setMenuOpen(false); }}
                                        className="w-full text-left px-4 py-3 text-[11px] font-black uppercase tracking-tight text-black/60 hover:bg-[var(--md-sys-color-surface-container-low)] hover:text-black transition-colors"
                                    >
                                        Duplicar Programa
                                    </button>
                                )}
                                {onExport && (
                                    <button
                                        onClick={() => { onExport(); setMenuOpen(false); }}
                                        className="w-full text-left px-4 py-3 text-[11px] font-black uppercase tracking-tight text-black/60 hover:bg-[var(--md-sys-color-surface-container-low)] hover:text-black transition-colors"
                                    >
                                        Exportar JSON
                                    </button>
                                )}
                                {onDelete && (
                                    <button
                                        onClick={() => { onDelete(); setMenuOpen(false); }}
                                        className="w-full text-left px-4 py-3 text-[11px] font-black uppercase tracking-tight text-[var(--md-sys-color-error)] hover:bg-[var(--md-sys-color-error-container)]/30 transition-colors flex items-center gap-2"
                                    >
                                        <TrashIcon size={14} /> Eliminar Programa
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
