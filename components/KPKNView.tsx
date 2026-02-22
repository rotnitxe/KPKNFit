
// components/KPKNView.tsx
import React, { useState, useMemo } from 'react';
import { ExerciseMuscleInfo, ExercisePlaylist } from '../types';
import { ChevronRightIcon, PlusIcon, TrashIcon, TrophyIcon, ActivityIcon, TargetIcon, BrainIcon, DumbbellIcon, ClipboardListIcon } from './icons';
import { useAppContext, useAppDispatch, useUIState } from '../contexts/AppContext';
import Button from './ui/Button';
import Card from './ui/Card';
import CoachMark from './ui/CoachMark';

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo }> = React.memo(({ exercise }) => {
    const { navigateTo } = useAppContext();
    return (
        <div
            onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
            className="p-4 flex justify-between items-center cursor-pointer list-none bg-slate-900/40 hover:bg-slate-800/60 rounded-xl border border-white/5 transition-all duration-200 group active:scale-[0.99]"
        >
            <div>
                <h3 className="font-bold text-white text-md group-hover:text-primary-color transition-colors">{exercise.name}</h3>
                <p className="text-xs text-slate-400 mt-0.5">{exercise.type} • {exercise.equipment}</p>
            </div>
            <ChevronRightIcon className="text-slate-600 group-hover:text-white transition-colors" size={18} />
        </div>
    );
});

const KPKNView: React.FC = () => {
    const { exerciseList, exercisePlaylists, addOrUpdatePlaylist, deletePlaylist, muscleHierarchy, settings } = useAppContext();
    const { setSettings, navigateTo } = useAppDispatch();
    const { activeSubTabs, searchQuery } = useUIState();
    
    // SubTabBar controls the view logic via activeSubTabs context, or we default to 'Explorar'
    const currentTab = activeSubTabs['kpkn'] || 'Explorar';
    
    const [newPlaylistName, setNewPlaylistName] = useState('');
    const [isCreatingNew, setIsCreatingNew] = useState(false);

    const handleDismissTour = () => setSettings({ hasSeenKPKNTour: true });

    const handleCreatePlaylist = () => {
        if (!newPlaylistName.trim()) return;
        const newPlaylist: ExercisePlaylist = {
            id: crypto.randomUUID(),
            name: newPlaylistName.trim(),
            exerciseIds: [],
        };
        addOrUpdatePlaylist(newPlaylist);
        setNewPlaylistName('');
        setIsCreatingNew(false);
    };

    const handleDeletePlaylist = (playlistId: string) => {
        if (window.confirm("¿Estás seguro de que quieres eliminar esta lista?")) {
            deletePlaylist(playlistId);
        }
    }
    
    const hallOfFameExercises = useMemo(() => exerciseList.filter(ex => ex.isHallOfFame), [exerciseList]);
    const bodyPartCategories = useMemo(() => Object.keys(muscleHierarchy.bodyPartHierarchy).sort((a, b) => a.localeCompare(b)), [muscleHierarchy]);
    
    const specialCategories = useMemo(() => {
        const allSpecial = Object.keys(muscleHierarchy.specialCategories || {});
        return allSpecial;
    }, [muscleHierarchy]);

    const filteredExercises = useMemo(() => {
        if (!searchQuery) return [];
        const query = searchQuery.toLowerCase();
        return exerciseList.filter(ex => 
            ex.name.toLowerCase().includes(query) || 
            (ex.alias && ex.alias.toLowerCase().includes(query)) ||
            ex.involvedMuscles.some(m => m.muscle.toLowerCase().includes(query))
        ).slice(0, 30); 
    }, [searchQuery, exerciseList]);
    
    const renderExploreTab = () => (
        <div className="space-y-6 animate-fade-in">
            {searchQuery.length > 0 ? (
                <div>
                     <h2 className="text-xs font-black text-slate-500 uppercase tracking-widest mb-4 ml-1">Resultados ({filteredExercises.length})</h2>
                     <div className="space-y-2">
                        {filteredExercises.length > 0 ? (
                            filteredExercises.map(ex => <ExerciseItem key={ex.id} exercise={ex} />)
                        ) : (
                            <div className="text-center py-10 opacity-50">
                                <DumbbellIcon size={40} className="mx-auto text-slate-600 mb-2"/>
                                <p className="text-slate-400">No se encontraron ejercicios.</p>
                            </div>
                        )}
                     </div>
                </div>
            ) : (
                <>
                    {/* Functional Groups Grid */}
                    {specialCategories.length > 0 && (
                        <div>
                            <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2 px-1"><ActivityIcon className="text-sky-400" size={20}/> Grupos Funcionales</h2>
                            <div className="grid grid-cols-2 gap-3">
                                {specialCategories.map(categoryName => (
                                    <div key={categoryName} onClick={() => navigateTo('chain-detail', { chainId: categoryName })} className="bg-slate-900/50 border border-white/5 p-4 rounded-2xl cursor-pointer hover:bg-slate-800 hover:border-sky-500/30 transition-all group">
                                        <h3 className="font-bold text-slate-200 text-sm uppercase tracking-wide group-hover:text-sky-300 transition-colors">{categoryName}</h3>
                                        <div className="w-8 h-1 bg-sky-500/20 rounded-full mt-2 group-hover:bg-sky-500 transition-colors"></div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Anatomy List */}
                    <div>
                        <h2 className="text-xl font-bold text-white mb-4 flex items-center gap-2 px-1 mt-2"><BrainIcon className="text-purple-400" size={20}/> Anatomía</h2>
                        <div className="space-y-2">
                            {bodyPartCategories.map(categoryName => (
                                <div key={categoryName} onClick={() => navigateTo('muscle-category', { categoryName })} className="bg-slate-900/30 p-4 flex justify-between items-center cursor-pointer rounded-xl border border-white/5 hover:border-purple-500/30 hover:bg-slate-800 transition-all group">
                                    <h2 className="text-lg font-bold text-slate-200 group-hover:text-white">{categoryName}</h2>
                                    <ChevronRightIcon className="text-slate-600 group-hover:text-purple-400 transition-colors" />
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Hall of Fame */}
                    {hallOfFameExercises.length > 0 && (
                        <div className="pt-4">
                            <div className="flex justify-between items-center mb-3 px-1">
                                <h2 className="text-xl font-bold text-yellow-400 flex items-center gap-2">
                                    <TrophyIcon /> Hall Of Fame
                                </h2>
                                <button onClick={() => navigateTo('hall-of-fame')} className="text-xs font-bold text-slate-500 hover:text-white uppercase tracking-wider">Ver todo</button>
                            </div>
                            <div className="relative">
                                <div className="flex overflow-x-auto snap-x snap-mandatory space-x-3 pb-4 -mx-4 px-4 hide-scrollbar">
                                    {hallOfFameExercises.map(ex => (
                                        <div key={ex.id} onClick={() => navigateTo('exercise-detail', { exerciseId: ex.id })} className="snap-center flex-shrink-0 w-40 h-32 bg-gradient-to-br from-slate-900 to-black rounded-2xl p-4 flex flex-col justify-between cursor-pointer border border-yellow-900/30 hover:border-yellow-500/50 transition-all group relative overflow-hidden">
                                            <div className="absolute inset-0 bg-yellow-500/5 opacity-0 group-hover:opacity-100 transition-opacity"></div>
                                            <h3 className="font-bold text-white text-sm leading-tight z-10">{ex.name}</h3>
                                            <p className="text-[10px] text-yellow-500/80 font-black uppercase tracking-wider z-10">{ex.involvedMuscles?.find(m => m.role === 'primary')?.muscle}</p>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}
                </>
            )}
        </div>
    );
    
    const renderListsTab = () => (
         <div className="space-y-4 animate-fade-in">
            {!isCreatingNew && (
                <Button onClick={() => setIsCreatingNew(true)} className="w-full !py-4 shadow-lg shadow-primary-color/20">
                    <PlusIcon /> Crear Nueva Lista
                </Button>
            )}
            {isCreatingNew && (
                <Card className="animate-fade-in-up">
                    <input 
                        type="text" 
                        value={newPlaylistName} 
                        onChange={e => setNewPlaylistName(e.target.value)} 
                        placeholder="Nombre de la nueva lista"
                        className="w-full bg-slate-900 border border-slate-700 rounded-lg p-3 text-white mb-3"
                        autoFocus
                    />
                    <div className="flex gap-2">
                        <Button onClick={() => setIsCreatingNew(false)} variant="secondary" className="flex-1">Cancelar</Button>
                        <Button onClick={handleCreatePlaylist} disabled={!newPlaylistName.trim()} className="flex-1">Crear</Button>
                    </div>
                </Card>
            )}
            
            <div className="space-y-3">
                {Array.isArray(exercisePlaylists) && exercisePlaylists.length > 0 ? (
                    exercisePlaylists.map(playlist => (
                        <details key={playlist.id} className="glass-card !p-0 group">
                            <summary className="p-4 cursor-pointer list-none flex justify-between items-center bg-slate-900/20 hover:bg-slate-800/40 transition-colors">
                                <div>
                                    <h3 className="font-bold text-white text-lg">{playlist.name}</h3>
                                    <p className="text-xs text-slate-400 font-medium">{playlist.exerciseIds.length} ejercicios</p>
                                </div>
                                <div className="flex items-center gap-3">
                                    <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); handleDeletePlaylist(playlist.id); }} className="p-2 text-slate-600 hover:text-red-400 transition-colors"><TrashIcon size={16}/></button>
                                    <ChevronRightIcon className="details-arrow transition-transform text-slate-500" />
                                </div>
                            </summary>
                            <div className="border-t border-slate-700/30 p-2 space-y-1 bg-black/20">
                                {playlist.exerciseIds.length > 0 ? playlist.exerciseIds.map(exId => {
                                    const exercise = exerciseList.find(e => e.id === exId);
                                    return exercise ? <ExerciseItem key={exId} exercise={exercise} /> : null;
                                }) : <p className="text-xs text-slate-500 text-center p-4 italic">Esta lista está vacía. Añade ejercicios desde el botón "+" en la vista de detalle.</p>}
                            </div>
                        </details>
                    ))
                ) : (
                    !isCreatingNew && (
                        <div className="text-center py-12 opacity-50">
                            <ClipboardListIcon size={48} className="mx-auto text-slate-600 mb-2"/>
                            <p className="text-slate-400">Aún no has creado ninguna lista.</p>
                        </div>
                    )
                )}
            </div>
        </div>
    );

    return (
        <div className="pt-4 pb-32 px-4 max-w-4xl mx-auto">
             {!settings.hasSeenKPKNTour && (
                <CoachMark 
                    title="KPKN: Base de Conocimiento" 
                    description="Aquí encontrarás todos los ejercicios y alimentos disponibles. Crea listas, busca por músculo o explora nuestra base de datos." 
                    onClose={handleDismissTour}
                />
             )}
             
            {/* Unified Title (No local header controls anymore) */}
             <div className="mb-6">
                <h1 className="text-4xl font-black uppercase tracking-tighter text-white">
                    {currentTab === 'Explorar' ? 'Base de Datos' : 'Mis Listas'}
                </h1>
                <p className="text-slate-400 text-xs font-bold uppercase tracking-widest mt-1">
                    {currentTab === 'Explorar' ? 'Enciclopedia de Ejercicios' : 'Colecciones Personalizadas'}
                </p>
            </div>

            {currentTab === 'Explorar' ? renderExploreTab() : renderListsTab()}
        </div>
    );
};

export default KPKNView;
