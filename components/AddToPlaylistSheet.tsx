// components/AddToPlaylistSheet.tsx
import React, { useState } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { ExercisePlaylist } from '../types';
import { XIcon, PlusIcon } from './icons';
import Button from './ui/Button';

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
            <div className="bottom-sheet-backdrop" onClick={handleClose} />
            <div className="bottom-sheet-content open">
                <div className="bottom-sheet-grabber" />
                <header className="flex items-center justify-between p-4 flex-shrink-0 border-b border-border-color">
                    <h2 className="text-xl font-bold text-white">Añadir a Lista</h2>
                    <button onClick={handleClose} className="p-2 text-slate-300">
                        <XIcon />
                    </button>
                </header>
                
                <div className="flex-grow overflow-y-auto p-4 space-y-3">
                    {isCreatingNew ? (
                        <div className="space-y-3 animate-fade-in">
                            <input
                                type="text"
                                value={newPlaylistName}
                                onChange={e => setNewPlaylistName(e.target.value)}
                                placeholder="Nombre de la nueva lista"
                                className="w-full"
                                autoFocus
                            />
                            <div className="flex gap-2">
                                <Button onClick={() => setIsCreatingNew(false)} variant="secondary" className="flex-1">Cancelar</Button>
                                <Button onClick={handleCreateAndAdd} disabled={!newPlaylistName.trim()} className="flex-1">Crear y Añadir</Button>
                            </div>
                        </div>
                    ) : (
                        <>
                            <Button onClick={() => setIsCreatingNew(true)} className="w-full !justify-start !py-3">
                                <PlusIcon /> Crear Nueva Lista
                            </Button>
                            {exercisePlaylists.length > 0 && (
                                 <div className="space-y-2 pt-2">
                                    {exercisePlaylists.map(playlist => (
                                        <button 
                                            key={playlist.id} 
                                            onClick={() => handleSelectPlaylist(playlist.id)}
                                            className="w-full text-left p-3 bg-slate-800/50 hover:bg-slate-700/50 rounded-lg"
                                        >
                                            <p className="font-semibold text-white">{playlist.name}</p>
                                            <p className="text-xs text-slate-400">{playlist.exerciseIds.length} ejercicios</p>
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