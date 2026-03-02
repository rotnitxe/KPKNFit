// components/AddToPlaylistSheet.tsx - Diseño unificado: gris medio-claro
import React, { useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExercisePlaylist } from '../types';
import { XIcon, PlusIcon } from './icons';

const AddToPlaylistSheet: React.FC = () => {
    const { isAddToPlaylistSheetOpen, exerciseToAddId, exercisePlaylists } = useAppState();
    const { setIsAddToPlaylistSheetOpen, addOrUpdatePlaylist } = useAppDispatch();
    const [isCreatingNew, setIsCreatingNew] = useState(false);
    const [newPlaylistName, setNewPlaylistName] = useState('');

    const handleClose = () => {
        setIsAddToPlaylistSheetOpen(false);
        setIsCreatingNew(false);
        setNewPlaylistName('');
    };

    const handleSelectPlaylist = (playlistId: string) => {
        if (!exerciseToAddId) return;
        const playlist = exercisePlaylists.find(p => p.id === playlistId);
        if (playlist) {
            if (playlist.exerciseIds.includes(exerciseToAddId)) {
                // Already in list, do nothing or show toast
            } else {
                const updatedPlaylist = { ...playlist, exerciseIds: [...playlist.exerciseIds, exerciseToAddId] };
                addOrUpdatePlaylist(updatedPlaylist);
            }
            handleClose();
        }
    };
    
    const handleCreateAndAdd = () => {
        if (!newPlaylistName.trim() || !exerciseToAddId) return;
        const newPlaylist: ExercisePlaylist = {
            id: crypto.randomUUID(),
            name: newPlaylistName.trim(),
            exerciseIds: [exerciseToAddId],
        };
        addOrUpdatePlaylist(newPlaylist);
        handleClose();
    };

    if (!isAddToPlaylistSheetOpen) return null;

    return (
        <>
            <div className="fixed inset-0 z-[200] bg-black/30 animate-fade-in" onClick={handleClose} aria-hidden />
            <div className="fixed left-0 right-0 bottom-0 z-[201] bg-[#e5e5e5] flex flex-col animate-slide-up" style={{ height: '90vh', maxHeight: '90dvh' }}>
                <header className="flex items-center justify-between p-4 shrink-0">
                    <h2 className="text-base font-black text-[#1a1a1a] uppercase tracking-tight">Añadir a Lista</h2>
                    <button onClick={handleClose} className="p-2 text-[#525252] hover:text-[#1a1a1a]" aria-label="Cerrar">
                        <XIcon size={18} />
                    </button>
                </header>

                <div className="flex-1 overflow-y-auto p-4 pb-[max(1rem,env(safe-area-inset-bottom))] space-y-3">
                    {isCreatingNew ? (
                        <div className="space-y-3 animate-fade-in">
                            <input
                                type="text"
                                value={newPlaylistName}
                                onChange={e => setNewPlaylistName(e.target.value)}
                                placeholder="Nombre de la nueva lista"
                                className="w-full px-3 py-2.5 bg-white border border-[#a3a3a3] text-[#1a1a1a] text-sm focus:border-[#525252] focus:outline-none"
                                autoFocus
                            />
                            <div className="flex gap-2 pt-2">
                                <button onClick={() => setIsCreatingNew(false)} className="flex-1 py-3 bg-white text-[#1a1a1a] font-semibold text-sm border border-[#a3a3a3]">Cancelar</button>
                                <button onClick={handleCreateAndAdd} disabled={!newPlaylistName.trim()} className="flex-1 py-3 bg-white text-[#1a1a1a] font-semibold text-sm border border-[#a3a3a3] disabled:opacity-50 disabled:cursor-not-allowed">Crear y Añadir</button>
                            </div>
                        </div>
                    ) : (
                        <>
                            <button onClick={() => setIsCreatingNew(true)} className="w-full flex items-center gap-2 py-3 px-4 bg-white text-[#1a1a1a] font-semibold text-sm border border-[#a3a3a3]">
                                <PlusIcon size={18} />
                                Crear Nueva Lista
                            </button>
                            {exercisePlaylists.length > 0 && (
                                <div className="space-y-2 pt-2">
                                    {exercisePlaylists.map(playlist => (
                                        <button
                                            key={playlist.id}
                                            onClick={() => handleSelectPlaylist(playlist.id)}
                                            className="w-full text-left p-3 bg-white border border-[#a3a3a3] hover:bg-[#f5f5f5]"
                                        >
                                            <p className="font-semibold text-[#1a1a1a]">{playlist.name}</p>
                                            <p className="text-xs text-[#525252]">{playlist.exerciseIds.length} ejercicios</p>
                                        </button>
                                    ))}
                                </div>
                            )}
                        </>
                    )}
                </div>
            </div>
        </>
    );
};

export default AddToPlaylistSheet;