// components/KPKNView.tsx
import React, { useState, useMemo } from 'react';
import { ExerciseMuscleInfo, ExercisePlaylist } from '../types';
import { ChevronRightIcon, PlusIcon, TrashIcon, TrophyIcon, ActivityIcon, BrainIcon, DumbbellIcon, ClipboardListIcon } from './icons';
import { useAppContext, useAppDispatch, useUIState } from '../contexts/AppContext';
import Button from './ui/Button';
import Card from './ui/Card';
import CoachMark from './ui/CoachMark';

type WikiTab = 'exercises' | 'anatomy' | 'joints' | 'tendons' | 'patterns';

const WIKI_TABS: { id: WikiTab; label: string; accent: string }[] = [
    { id: 'exercises', label: 'Ejercicios', accent: 'sky' },
    { id: 'anatomy', label: 'Anatomía', accent: 'purple' },
    { id: 'joints', label: 'Articulaciones', accent: 'cyan' },
    { id: 'tendons', label: 'Tendones', accent: 'amber' },
    { id: 'patterns', label: 'Patrones', accent: 'emerald' },
];

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
    const { exerciseList, exercisePlaylists, addOrUpdatePlaylist, deletePlaylist, settings, muscleGroupData, muscleHierarchy, jointDatabase, tendonDatabase, movementPatternDatabase } = useAppContext();
    const { setSettings, navigateTo } = useAppDispatch();
    const { activeSubTabs, searchQuery } = useUIState();
    
    // SubTabBar controls the view logic via activeSubTabs context, or we default to 'Explorar'
    const currentTab = activeSubTabs['kpkn'] || 'Explorar';
    
    const [newPlaylistName, setNewPlaylistName] = useState('');
    const [isCreatingNew, setIsCreatingNew] = useState(false);
    const [activeWikiTab, setActiveWikiTab] = useState<WikiTab>('exercises');

    const bodyPartCategories = Object.keys(muscleHierarchy?.bodyPartHierarchy || {}).sort((a, b) => a.localeCompare(b));
    const specialCategories = Object.keys(muscleHierarchy?.specialCategories || {});

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
    
    const unifiedSearchResults = useMemo(() => {
        if (!searchQuery || searchQuery.length < 2) return { exercises: [], muscles: [], joints: [], tendons: [], patterns: [] };
        const q = searchQuery.toLowerCase().trim();
        const exercises = exerciseList.filter(ex =>
            ex.name.toLowerCase().includes(q) ||
            (ex.alias && ex.alias.toLowerCase().includes(q)) ||
            ex.involvedMuscles.some(m => m.muscle.toLowerCase().includes(q))
        ).slice(0, 15);

        // Músculos: preferir padres (Pectoral, Deltoides), pero incluir hijos si el padre no está en DB (ej. Supraespinoso)
        const childNames = new Set<string>();
        const childToParent = new Map<string, string>();
        Object.values(muscleHierarchy?.bodyPartHierarchy || {}).forEach(subgroups => {
            subgroups.forEach(sg => {
                if (typeof sg === 'object' && sg !== null) {
                    const parent = Object.keys(sg)[0];
                    (sg as Record<string, string[]>)[parent]?.forEach(child => {
                        childNames.add(child);
                        childToParent.set(child, parent);
                    });
                }
            });
        });
        const parentMuscles = (muscleGroupData || []).filter(m => !childNames.has(m.name));
        const parentsToIncludeFromChildMatch = new Set<string>();
        const matchingChildrenWithoutParent = new Set<string>();
        childToParent.forEach((parent, child) => {
            if (child.toLowerCase().includes(q)) {
                parentsToIncludeFromChildMatch.add(parent);
                const parentInDb = (muscleGroupData || []).find(m => m.name === parent);
                if (!parentInDb) matchingChildrenWithoutParent.add(child);
            }
        });
        const muscles = (muscleGroupData || [])
            .filter(m =>
                m.name.toLowerCase().includes(q) ||
                (m.description || '').toLowerCase().includes(q) ||
                parentsToIncludeFromChildMatch.has(m.name) ||
                matchingChildrenWithoutParent.has(m.name)
            )
            .slice(0, 10);
        const joints = (jointDatabase || []).filter(j =>
            j.name.toLowerCase().includes(q) || (j.description || '').toLowerCase().includes(q)
        ).slice(0, 8);
        const tendons = (tendonDatabase || []).filter(t =>
            t.name.toLowerCase().includes(q) || (t.description || '').toLowerCase().includes(q)
        ).slice(0, 8);
        const patterns = (movementPatternDatabase || []).filter(p =>
            p.name.toLowerCase().includes(q) || (p.description || '').toLowerCase().includes(q)
        ).slice(0, 8);
        return { exercises, muscles, joints, tendons, patterns };
    }, [searchQuery, exerciseList, muscleGroupData, muscleHierarchy, jointDatabase, tendonDatabase, movementPatternDatabase]);
    
    const renderWikiTabContent = () => {
        switch (activeWikiTab) {
            case 'exercises':
                return (
                    <div className="space-y-6">
                        <div
                            onClick={() => navigateTo('exercise-database')}
                            className="p-6 rounded-2xl border border-sky-500/30 bg-sky-500/10 hover:bg-sky-500/20 cursor-pointer transition-all flex items-center justify-between"
                        >
                            <div>
                                <h3 className="font-bold text-white text-lg">Base de ejercicios</h3>
                                <p className="text-slate-400 text-sm mt-1">{exerciseList.length} ejercicios disponibles</p>
                            </div>
                            <ChevronRightIcon className="text-sky-400" size={24} />
                        </div>
                        <div className="grid grid-cols-2 gap-2">
                            {exerciseList.filter(ex => ex.isHallOfFame).slice(0, 6).map(ex => (
                                <div key={ex.id} onClick={() => navigateTo('exercise-detail', { exerciseId: ex.id })}
                                    className="p-4 rounded-xl bg-slate-900/50 border border-white/5 hover:border-sky-500/30 cursor-pointer">
                                    <h3 className="font-bold text-white text-sm">{ex.name}</h3>
                                    <p className="text-[10px] text-sky-500/80 mt-0.5">{ex.involvedMuscles?.find(m => m.role === 'primary')?.muscle}</p>
                                </div>
                            ))}
                        </div>
                        <button onClick={() => navigateTo('hall-of-fame')} className="text-xs font-bold text-sky-500 hover:text-sky-400">Ver Hall of Fame</button>
                    </div>
                );
            case 'anatomy':
                return (
                    <div className="space-y-6">
                        <div>
                            <h2 className="text-xs font-black text-purple-500 uppercase tracking-widest mb-3">Anatomía por zona</h2>
                            <div className="space-y-2">
                                {bodyPartCategories.map(cat => (
                                    <div key={cat} onClick={() => navigateTo('muscle-category', { categoryName: cat })}
                                        className="p-4 flex justify-between items-center rounded-xl bg-slate-900/40 border border-white/5 hover:border-purple-500/30 cursor-pointer transition-all">
                                        <span className="font-semibold text-slate-200">{cat}</span>
                                        <ChevronRightIcon className="text-slate-500" size={18} />
                                    </div>
                                ))}
                            </div>
                        </div>
                        {specialCategories.length > 0 && (
                            <div>
                                <h2 className="text-xs font-black text-purple-500 uppercase tracking-widest mb-3">Grupos funcionales</h2>
                                <div className="grid grid-cols-2 gap-2">
                                    {specialCategories.map(cat => (
                                        <div key={cat} onClick={() => navigateTo('chain-detail', { chainId: cat })}
                                            className="p-3 rounded-xl bg-slate-900/50 border border-white/5 hover:border-purple-500/30 cursor-pointer">
                                            <span className="font-semibold text-slate-200 text-sm">{cat}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                );
            case 'joints':
                return (
                    <div className="space-y-2">
                        <h2 className="text-xs font-black text-cyan-500 uppercase tracking-widest mb-3">Articulaciones ({jointDatabase?.length || 0})</h2>
                        {(jointDatabase || []).map(j => (
                            <div key={j.id} onClick={() => navigateTo('joint-detail', { jointId: j.id })}
                                className="p-4 flex justify-between items-center rounded-xl bg-slate-900/40 border border-white/5 hover:border-cyan-500/30 cursor-pointer transition-all">
                                <span className="font-semibold text-white">{j.name.split('(')[0].trim()}</span>
                                <ChevronRightIcon className="text-slate-500" size={18} />
                            </div>
                        ))}
                    </div>
                );
            case 'tendons':
                return (
                    <div className="space-y-2">
                        <h2 className="text-xs font-black text-amber-500 uppercase tracking-widest mb-3">Tendones ({tendonDatabase?.length || 0})</h2>
                        {(tendonDatabase || []).map(t => (
                            <div key={t.id} onClick={() => navigateTo('tendon-detail', { tendonId: t.id })}
                                className="p-4 flex justify-between items-center rounded-xl bg-slate-900/40 border border-white/5 hover:border-amber-500/30 cursor-pointer transition-all">
                                <span className="font-semibold text-white">{t.name}</span>
                                <ChevronRightIcon className="text-slate-500" size={18} />
                            </div>
                        ))}
                    </div>
                );
            case 'patterns':
                return (
                    <div className="space-y-2">
                        <h2 className="text-xs font-black text-emerald-500 uppercase tracking-widest mb-3">Patrones de movimiento ({movementPatternDatabase?.length || 0})</h2>
                        {(movementPatternDatabase || []).map(p => (
                            <div key={p.id} onClick={() => navigateTo('movement-pattern-detail', { movementPatternId: p.id })}
                                className="p-4 flex justify-between items-center rounded-xl bg-slate-900/40 border border-white/5 hover:border-emerald-500/30 cursor-pointer transition-all">
                                <span className="font-semibold text-white">{p.name}</span>
                                <ChevronRightIcon className="text-slate-500" size={18} />
                            </div>
                        ))}
                    </div>
                );
            default:
                return null;
        }
    };

    const renderExploreTab = () => {
        const hasResults = searchQuery.length >= 2 && (
            unifiedSearchResults.exercises.length > 0 ||
            unifiedSearchResults.muscles.length > 0 ||
            unifiedSearchResults.joints.length > 0 ||
            unifiedSearchResults.tendons.length > 0 ||
            unifiedSearchResults.patterns.length > 0
        );
        return (
            <div className="space-y-6 animate-fade-in">
                {searchQuery.length >= 2 ? (
                    <div className="space-y-8">
                        {hasResults ? (
                            <>
                                {unifiedSearchResults.exercises.length > 0 && (
                                    <div className="pb-6 border-b border-white/10">
                                        <h2 className="text-xs font-black text-sky-500 uppercase tracking-widest mb-3 ml-1">Ejercicios ({unifiedSearchResults.exercises.length})</h2>
                                        <div className="space-y-2">
                                            {unifiedSearchResults.exercises.map(ex => <ExerciseItem key={ex.id} exercise={ex} />)}
                                        </div>
                                    </div>
                                )}
                                {unifiedSearchResults.muscles.length > 0 && (
                                    <div className="pb-6 border-b border-white/10">
                                        <h2 className="text-xs font-black text-purple-500 uppercase tracking-widest mb-3 ml-1">Músculos ({unifiedSearchResults.muscles.length})</h2>
                                        <div className="space-y-2">
                                            {unifiedSearchResults.muscles.map(m => (
                                                <div key={m.id} onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })} className="p-4 flex justify-between items-center cursor-pointer bg-slate-900/40 hover:bg-slate-800/60 rounded-xl border border-white/5 transition-all">
                                                    <span className="font-bold text-white">{m.name}</span>
                                                    <ChevronRightIcon className="text-slate-500" size={18} />
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                                {unifiedSearchResults.joints.length > 0 && (
                                    <div className="pb-6 border-b border-white/10">
                                        <h2 className="text-xs font-black text-cyan-500 uppercase tracking-widest mb-3 ml-1">Articulaciones ({unifiedSearchResults.joints.length})</h2>
                                        <div className="space-y-2">
                                            {unifiedSearchResults.joints.map(j => (
                                                <div key={j.id} onClick={() => navigateTo('joint-detail', { jointId: j.id })} className="p-4 flex justify-between items-center cursor-pointer bg-slate-900/40 hover:bg-slate-800/60 rounded-xl border border-white/5 transition-all">
                                                    <span className="font-bold text-white">{j.name}</span>
                                                    <ChevronRightIcon className="text-slate-500" size={18} />
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                                {unifiedSearchResults.tendons.length > 0 && (
                                    <div className="pb-6 border-b border-white/10">
                                        <h2 className="text-xs font-black text-amber-500 uppercase tracking-widest mb-3 ml-1">Tendones ({unifiedSearchResults.tendons.length})</h2>
                                        <div className="space-y-2">
                                            {unifiedSearchResults.tendons.map(t => (
                                                <div key={t.id} onClick={() => navigateTo('tendon-detail', { tendonId: t.id })} className="p-4 flex justify-between items-center cursor-pointer bg-slate-900/40 hover:bg-slate-800/60 rounded-xl border border-white/5 transition-all">
                                                    <span className="font-bold text-white">{t.name}</span>
                                                    <ChevronRightIcon className="text-slate-500" size={18} />
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                                {unifiedSearchResults.patterns.length > 0 && (
                                    <div>
                                        <h2 className="text-xs font-black text-emerald-500 uppercase tracking-widest mb-3 ml-1">Patrones ({unifiedSearchResults.patterns.length})</h2>
                                        <div className="space-y-2">
                                            {unifiedSearchResults.patterns.map(p => (
                                                <div key={p.id} onClick={() => navigateTo('movement-pattern-detail', { movementPatternId: p.id })} className="p-4 flex justify-between items-center cursor-pointer bg-slate-900/40 hover:bg-slate-800/60 rounded-xl border border-white/5 transition-all">
                                                    <span className="font-bold text-white">{p.name}</span>
                                                    <ChevronRightIcon className="text-slate-500" size={18} />
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </>
                        ) : (
                            <div className="text-center py-10 opacity-50">
                                <DumbbellIcon size={40} className="mx-auto text-slate-600 mb-2"/>
                                <p className="text-slate-400">No se encontraron resultados.</p>
                                <p className="text-slate-500 text-xs mt-1">Prueba con músculos, articulaciones o tendones.</p>
                            </div>
                        )}
                    </div>
                ) : (
                    <div className="space-y-6">
                        <div className="flex overflow-x-auto gap-1 pb-2 -mx-1 hide-scrollbar border-b border-white/10">
                            {WIKI_TABS.map(tab => {
                                const isActive = activeWikiTab === tab.id;
                                const activeClasses: Record<string, string> = {
                                    sky: 'bg-sky-500/20 text-sky-400 border-b-2 border-sky-500',
                                    purple: 'bg-purple-500/20 text-purple-400 border-b-2 border-purple-500',
                                    cyan: 'bg-cyan-500/20 text-cyan-400 border-b-2 border-cyan-500',
                                    amber: 'bg-amber-500/20 text-amber-400 border-b-2 border-amber-500',
                                    emerald: 'bg-emerald-500/20 text-emerald-400 border-b-2 border-emerald-500',
                                };
                                return (
                                    <button
                                        key={tab.id}
                                        onClick={() => setActiveWikiTab(tab.id)}
                                        className={`flex-shrink-0 px-4 py-2.5 rounded-t-lg text-sm font-bold transition-all ${
                                            isActive ? activeClasses[tab.accent] : 'text-slate-500 hover:text-slate-300'
                                        }`}
                                    >
                                        {tab.label}
                                    </button>
                                );
                            })}
                        </div>
                        {renderWikiTabContent()}
                    </div>
                )}
            </div>
        );
    };
    
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
                    {currentTab === 'Explorar' ? 'Wiki/Lab' : 'Mis Listas'}
                </h1>
                <p className="text-slate-400 text-xs font-bold uppercase tracking-widest mt-1">
                    {currentTab === 'Explorar' ? 'Base de conocimiento para el entrenamiento' : 'Colecciones Personalizadas'}
                </p>
            </div>

            {currentTab === 'Explorar' ? renderExploreTab() : renderListsTab()}
        </div>
    );
};

export default KPKNView;
