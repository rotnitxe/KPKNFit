// components/KPKNView.tsx
import React, { useState, useMemo } from 'react';
import { ExerciseMuscleInfo, ExercisePlaylist } from '../types';
import { ChevronRightIcon, PlusIcon, TrashIcon, ActivityIcon, BrainIcon, DumbbellIcon, ClipboardListIcon } from './icons';
import { useAppContext, useAppDispatch, useUIState } from '../contexts/AppContext';
import CoachMark from './ui/CoachMark';

type WikiTab = 'exercises' | 'anatomy' | 'patterns';

const WIKI_TABS: { id: WikiTab; label: string; accent: string }[] = [
    { id: 'exercises', label: 'Ejercicios', accent: 'sky' },
    { id: 'anatomy', label: 'Anatomía', accent: 'purple' },
    { id: 'patterns', label: 'Patrones', accent: 'emerald' },
];

const ExerciseItem: React.FC<{ exercise: ExerciseMuscleInfo }> = React.memo(({ exercise }) => {
    const { navigateTo } = useAppContext();
    return (
        <div
            onClick={() => navigateTo('exercise-detail', { exerciseId: exercise.id })}
            className="p-4 flex justify-between items-center cursor-pointer list-none bg-[#0a0a0a] hover:bg-slate-900/80 rounded-xl border border-orange-500/20 hover:border-orange-500/40 transition-all duration-200 group active:scale-[0.99]"
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
            (ex.equipment && ex.equipment.toLowerCase().includes(q)) ||
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
                            className="p-5 rounded-xl border border-orange-500/20 bg-[#0a0a0a] hover:border-orange-500/40 cursor-pointer transition-all flex items-center justify-between"
                        >
                            <div>
                                <h3 className="text-[10px] font-mono font-black uppercase tracking-widest text-orange-500/90">Base de ejercicios</h3>
                                <p className="text-white font-mono text-sm mt-1">{exerciseList.length} ejercicios</p>
                            </div>
                            <ChevronRightIcon className="text-orange-500/60" size={20} />
                        </div>
                        <div className="grid grid-cols-2 gap-2">
                            {exerciseList.filter(ex => ex.type === 'Básico').slice(0, 6).map(ex => (
                                <div key={ex.id} onClick={() => navigateTo('exercise-detail', { exerciseId: ex.id })}
                                    className="p-4 rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/50 cursor-pointer transition-all">
                                    <h3 className="font-bold text-white text-sm">{ex.name}</h3>
                                    <p className="text-[10px] text-orange-500/80 font-mono mt-0.5">{ex.involvedMuscles?.find(m => m.role === 'primary')?.muscle || ex.subMuscleGroup}</p>
                                    {(ex.efc != null || ex.cnc != null || ex.ssc != null) && (
                                        <div className="flex gap-1.5 mt-2 text-[9px] font-mono text-slate-500">
                                            {ex.efc != null && <span>EFC:{ex.efc}</span>}
                                            {ex.cnc != null && <span>CNC:{ex.cnc}</span>}
                                            {ex.ssc != null && <span>SSC:{ex.ssc}</span>}
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                );
            case 'anatomy':
                return (
                    <div className="space-y-6">
                        <div>
                            <h2 className="text-[10px] font-mono font-black text-purple-500/90 uppercase tracking-widest mb-3">Anatomía por zona</h2>
                            <div className="space-y-2">
                                {bodyPartCategories.map(cat => (
                                    <div key={cat} onClick={() => navigateTo('muscle-category', { categoryName: cat })}
                                        className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/40 cursor-pointer transition-all">
                                        <span className="font-mono font-semibold text-slate-200 text-sm">{cat}</span>
                                        <ChevronRightIcon className="text-slate-500" size={18} />
                                    </div>
                                ))}
                            </div>
                        </div>
                        <div>
                            <h2 className="text-[10px] font-mono font-black text-cyan-500/90 uppercase tracking-widest mb-3">Articulaciones y Tendones</h2>
                            <div className="space-y-2">
                                <div className="mb-2">
                                    <span className="text-[10px] font-mono text-slate-500 uppercase">Articulaciones</span>
                                    <div className="mt-1 space-y-2">
                                        {(jointDatabase || []).slice(0, 12).map(j => (
                                            <div key={j.id} onClick={() => navigateTo('joint-detail', { jointId: j.id })}
                                                className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/40 cursor-pointer transition-all">
                                                <span className="font-mono font-semibold text-slate-200 text-sm">{j.name?.split('(')[0]?.trim() || j.name}</span>
                                                <ChevronRightIcon className="text-slate-500" size={18} />
                                            </div>
                                        ))}
                                    </div>
                                </div>
                                <div>
                                    <span className="text-[10px] font-mono text-slate-500 uppercase">Tendones</span>
                                    <div className="mt-1 space-y-2">
                                        {(tendonDatabase || []).slice(0, 8).map(t => (
                                            <div key={t.id} onClick={() => navigateTo('tendon-detail', { tendonId: t.id })}
                                                className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/40 cursor-pointer transition-all">
                                                <span className="font-mono font-semibold text-slate-200 text-sm">{t.name}</span>
                                                <ChevronRightIcon className="text-slate-500" size={18} />
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        </div>
                        {specialCategories.length > 0 && (
                            <div>
                                <h2 className="text-[10px] font-mono font-black text-purple-500/90 uppercase tracking-widest mb-3">Grupos funcionales</h2>
                                <div className="grid grid-cols-2 gap-2">
                                    {specialCategories.map(cat => (
                                        <div key={cat} onClick={() => navigateTo('chain-detail', { chainId: cat })}
                                            className="p-3 rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/40 cursor-pointer">
                                            <span className="font-semibold text-slate-200 text-sm">{cat}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                );
            case 'patterns':
                return (
                    <div className="space-y-2">
                        <h2 className="text-[10px] font-mono font-black text-emerald-500/90 uppercase tracking-widest mb-3">Patrones de movimiento ({movementPatternDatabase?.length || 0})</h2>
                        {(movementPatternDatabase || []).map(p => (
                            <div key={p.id} onClick={() => navigateTo('movement-pattern-detail', { movementPatternId: p.id })}
                                className="p-4 flex justify-between items-center rounded-xl bg-[#0a0a0a] border border-orange-500/20 hover:border-orange-500/40 cursor-pointer transition-all">
                                            <span className="font-mono font-semibold text-white text-sm">{p.name}</span>
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
                                    <div className="pb-6 border-b border-white/5">
                                        <h2 className="text-[10px] font-mono font-black text-sky-500/90 uppercase tracking-widest mb-3 ml-1">Ejercicios ({unifiedSearchResults.exercises.length})</h2>
                                        <div className="space-y-2">
                                            {unifiedSearchResults.exercises.map(ex => <ExerciseItem key={ex.id} exercise={ex} />)}
                                        </div>
                                    </div>
                                )}
                                {unifiedSearchResults.muscles.length > 0 && (
                                    <div className="pb-6 border-b border-white/5">
                                        <h2 className="text-[10px] font-mono font-black text-purple-500/90 uppercase tracking-widest mb-3 ml-1">Músculos ({unifiedSearchResults.muscles.length})</h2>
                                        <div className="space-y-2">
                                            {unifiedSearchResults.muscles.map(m => (
                                                <div key={m.id} onClick={() => navigateTo('muscle-group-detail', { muscleGroupId: m.id })} className="p-4 flex justify-between items-center cursor-pointer bg-[#0a0a0a] hover:bg-slate-900/80 rounded-xl border border-orange-500/20 hover:border-orange-500/40 transition-all">
                                                    <span className="font-bold text-white">{m.name}</span>
                                                    <ChevronRightIcon className="text-slate-500" size={18} />
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                                {unifiedSearchResults.joints.length > 0 && (
                                    <div className="pb-6 border-b border-white/5">
                                        <h2 className="text-[10px] font-mono font-black text-cyan-500/90 uppercase tracking-widest mb-3 ml-1">Articulaciones ({unifiedSearchResults.joints.length})</h2>
                                        <div className="space-y-2">
                                            {unifiedSearchResults.joints.map(j => (
                                                <div key={j.id} onClick={() => navigateTo('joint-detail', { jointId: j.id })} className="p-4 flex justify-between items-center cursor-pointer bg-[#0a0a0a] hover:bg-slate-900/80 rounded-xl border border-orange-500/20 hover:border-orange-500/40 transition-all">
                                                    <span className="font-bold text-white">{j.name}</span>
                                                    <ChevronRightIcon className="text-slate-500" size={18} />
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                                {unifiedSearchResults.tendons.length > 0 && (
                                    <div className="pb-6 border-b border-white/5">
                                        <h2 className="text-[10px] font-mono font-black text-amber-500/90 uppercase tracking-widest mb-3 ml-1">Tendones ({unifiedSearchResults.tendons.length})</h2>
                                        <div className="space-y-2">
                                            {unifiedSearchResults.tendons.map(t => (
                                                <div key={t.id} onClick={() => navigateTo('tendon-detail', { tendonId: t.id })} className="p-4 flex justify-between items-center cursor-pointer bg-[#0a0a0a] hover:bg-slate-900/80 rounded-xl border border-orange-500/20 hover:border-orange-500/40 transition-all">
                                                    <span className="font-bold text-white">{t.name}</span>
                                                    <ChevronRightIcon className="text-slate-500" size={18} />
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                                {unifiedSearchResults.patterns.length > 0 && (
                                    <div>
                                        <h2 className="text-[10px] font-mono font-black text-emerald-500/90 uppercase tracking-widest mb-3 ml-1">Patrones ({unifiedSearchResults.patterns.length})</h2>
                                        <div className="space-y-2">
                                            {unifiedSearchResults.patterns.map(p => (
                                                <div key={p.id} onClick={() => navigateTo('movement-pattern-detail', { movementPatternId: p.id })} className="p-4 flex justify-between items-center cursor-pointer bg-[#0a0a0a] hover:bg-slate-900/80 rounded-xl border border-orange-500/20 hover:border-orange-500/40 transition-all">
                                                    <span className="font-bold text-white">{p.name}</span>
                                                    <ChevronRightIcon className="text-slate-500" size={18} />
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </>
                        ) : (
                            <div className="text-center py-12 rounded-xl border border-white/5 bg-[#0a0a0a]">
                                <DumbbellIcon size={40} className="mx-auto text-slate-600 mb-2"/>
                                <p className="text-slate-400 font-mono text-sm">No se encontraron resultados.</p>
                                <p className="text-[10px] text-slate-500 font-mono mt-1">Prueba con músculos, articulaciones o tendones.</p>
                            </div>
                        )}
                    </div>
                ) : (
                    <div className="space-y-6">
                        <div className="flex overflow-x-auto gap-1 pb-2 -mx-1 hide-scrollbar border-b border-white/5">
                            {WIKI_TABS.map(tab => {
                                const isActive = activeWikiTab === tab.id;
                                const activeClasses: Record<string, string> = {
                                    sky: 'bg-sky-500/20 text-sky-400 border border-sky-500/40',
                                    purple: 'bg-purple-500/20 text-purple-400 border border-purple-500/40',
                                    cyan: 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/40',
                                    amber: 'bg-amber-500/20 text-amber-400 border border-amber-500/40',
                                    emerald: 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40',
                                };
                                return (
                                    <button
                                        key={tab.id}
                                        onClick={() => setActiveWikiTab(tab.id)}
                                        className={`flex-shrink-0 px-4 py-2.5 rounded-lg text-[11px] font-mono font-bold transition-all border border-transparent ${
                                            isActive ? activeClasses[tab.accent] : 'text-slate-500 hover:text-slate-300 hover:bg-white/5'
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
                <button
                    onClick={() => setIsCreatingNew(true)}
                    className="w-full py-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a] hover:bg-[#0d0d0d] hover:border-orange-500/40 transition-all flex items-center justify-center gap-2 font-mono text-sm font-bold text-orange-500/90"
                >
                    <PlusIcon size={18} /> CREAR NUEVA LISTA
                </button>
            )}
            {isCreatingNew && (
                <div className="p-4 rounded-xl border border-orange-500/20 bg-[#0a0a0a] animate-fade-in-up">
                    <input 
                        type="text" 
                        value={newPlaylistName} 
                        onChange={e => setNewPlaylistName(e.target.value)} 
                        placeholder="Nombre de la nueva lista"
                        className="w-full bg-[#0d0d0d] border border-white/5 rounded-lg p-3 text-white font-mono text-sm mb-3 placeholder:text-slate-500 focus:border-orange-500/40 outline-none"
                        autoFocus
                    />
                    <div className="flex gap-2">
                        <button onClick={() => setIsCreatingNew(false)} className="flex-1 py-2.5 rounded-lg border border-white/10 text-slate-400 font-mono text-xs hover:bg-white/5">CANCELAR</button>
                        <button onClick={handleCreatePlaylist} disabled={!newPlaylistName.trim()} className="flex-1 py-2.5 rounded-lg bg-orange-500/20 text-orange-400 font-mono text-xs font-bold border border-orange-500/30 disabled:opacity-40 disabled:cursor-not-allowed">CREAR</button>
                    </div>
                </div>
            )}
            
            <div className="space-y-2">
                {Array.isArray(exercisePlaylists) && exercisePlaylists.length > 0 ? (
                    exercisePlaylists.map(playlist => (
                        <details key={playlist.id} className="rounded-xl border border-orange-500/20 bg-[#0a0a0a] overflow-hidden group">
                            <summary className="p-4 cursor-pointer list-none flex justify-between items-center hover:bg-[#0d0d0d] transition-colors">
                                <div>
                                    <h3 className="font-bold text-white font-mono text-sm">{playlist.name}</h3>
                                    <p className="text-[10px] text-orange-500/80 font-mono mt-0.5">{playlist.exerciseIds.length} ejercicios</p>
                                </div>
                                <div className="flex items-center gap-2">
                                    <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); handleDeletePlaylist(playlist.id); }} className="p-2 text-slate-500 hover:text-red-400 transition-colors" title="Eliminar lista"><TrashIcon size={14}/></button>
                                    <ChevronRightIcon className="details-arrow transition-transform text-slate-500" size={16} />
                                </div>
                            </summary>
                            <div className="border-t border-white/5 p-2 space-y-1 bg-[#080808]">
                                {playlist.exerciseIds.length > 0 ? playlist.exerciseIds.map(exId => {
                                    const exercise = exerciseList.find(e => e.id === exId);
                                    return exercise ? <ExerciseItem key={exId} exercise={exercise} /> : null;
                                }) : <p className="text-[10px] text-slate-500 font-mono text-center p-4">Lista vacía. Añade ejercicios desde el botón + en la vista de detalle.</p>}
                            </div>
                        </details>
                    ))
                ) : (
                    !isCreatingNew && (
                        <div className="text-center py-12 rounded-xl border border-white/5 bg-[#0a0a0a]">
                            <ClipboardListIcon size={40} className="mx-auto text-slate-600 mb-2"/>
                            <p className="text-slate-400 font-mono text-sm">Aún no has creado ninguna lista.</p>
                            <p className="text-[10px] text-slate-500 font-mono mt-1">Crea una para organizar tus ejercicios favoritos.</p>
                        </div>
                    )
                )}
            </div>
        </div>
    );

    return (
        <div className="pt-4 pb-[max(120px,calc(90px+env(safe-area-inset-bottom,0px)+24px))] px-4 max-w-4xl mx-auto bg-[#0a0a0a] min-h-screen">
             {!settings.hasSeenKPKNTour && (
                <CoachMark 
                    title="KPKN: Base de Conocimiento" 
                    description="Aquí encontrarás todos los ejercicios y alimentos disponibles. Crea listas, busca por músculo o explora nuestra base de datos." 
                    onClose={handleDismissTour}
                />
             )}
             
            <div className="mb-6 border-b border-white/5 pb-4">
                <h1 className="text-2xl font-black font-mono uppercase tracking-tight text-white">
                    {currentTab === 'Explorar' ? 'WikiLab' : 'Mis Listas'}
                </h1>
                <p className="text-[10px] text-orange-500/80 font-mono uppercase tracking-widest mt-1">
                    {currentTab === 'Explorar' ? 'base de conocimiento · ejercicios · anatomía · patrones' : 'colecciones personalizadas'}
                </p>
            </div>

            {currentTab === 'Explorar' ? renderExploreTab() : renderListsTab()}
        </div>
    );
};

export default KPKNView;
