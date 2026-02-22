// components/SubTabBar.tsx
import React, { useMemo } from 'react';
import { useAppContext, useAppDispatch, useUIState, useUIDispatch } from '../contexts/AppContext';
import { SearchIcon, DumbbellIcon, ClipboardListIcon, PlusIcon, UtensilsIcon, BrainIcon, TrendingUpIcon, BodyIcon, ActivityIcon, PencilIcon, ClipboardPlusIcon } from './icons';

interface SubTabBarProps {
  context: 'kpkn' | 'food-database' | 'progress' | 'athlete-profile' | null;
  isActive: boolean;
  viewingExerciseId?: string | null;
  onEditExercisePress?: () => void;
}

const SubTabBar: React.FC<SubTabBarProps> = ({ context, isActive, viewingExerciseId, onEditExercisePress }) => {
    const { 
        openFoodEditor, navigateTo,
        openCustomExerciseEditor,
        view
    } = useAppContext();
    const { setExerciseToAddId } = useAppDispatch();
    const { searchQuery, activeSubTabs } = useUIState();
    const { setSearchQuery, setActiveSubTabs, setIsAddToPlaylistSheetOpen } = useUIDispatch();

    const handleAddToPlaylist = () => {
        if (viewingExerciseId) {
            setExerciseToAddId(viewingExerciseId);
            setIsAddToPlaylistSheetOpen(true);
        }
    };

    const isDatabaseContext = context === 'kpkn' || context === 'food-database';

    const activeSubTab = context ? (activeSubTabs[context] || (context === 'progress' ? 'cuerpo' : context === 'athlete-profile' ? 'vitals' : 'Explorar')) : null;

    if (!context) return null;

    const renderDatabaseControls = () => (
        <div className="flex items-center gap-2 w-full px-3">
            {/* Search Box */}
            <div className="relative flex-grow">
                <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder={view === 'food-database' ? "Buscar alimento..." : "Buscar ejercicio..."}
                    className="w-full h-10 bg-black/80 backdrop-blur-md border border-white/10 rounded-2xl pl-10 pr-4 py-2 text-xs text-white focus:outline-none focus:ring-1 focus:ring-white/30 placeholder-white/20"
                />
                <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 text-white/40" size={14} />
            </div>

            {/* Icon Actions */}
            <div className="flex gap-1">
                {/* DATABASE TOGGLE SWITCH */}
                {view === 'kpkn' ? (
                     <button 
                        onClick={() => navigateTo('food-database')}
                        className="p-2.5 rounded-xl bg-black/80 text-slate-400 hover:text-orange-400 hover:bg-orange-900/20 border border-white/10 transition-all"
                        title="Ir a Alimentos"
                    >
                        <UtensilsIcon size={18} />
                    </button>
                ) : (
                    <button 
                        onClick={() => navigateTo('kpkn')}
                        className="p-2.5 rounded-xl bg-black/80 text-slate-400 hover:text-blue-400 hover:bg-blue-900/20 border border-white/10 transition-all"
                        title="Ir a Ejercicios"
                    >
                        <DumbbellIcon size={18} />
                    </button>
                )}

                {/* Context-Specific Actions */}
                {view === 'kpkn' && (
                     <button 
                        onClick={() => { setActiveSubTabs(p => ({...p, 'kpkn': activeSubTab === 'Mis Listas' ? 'Explorar' : 'Mis Listas'})); }}
                        className={`p-2.5 rounded-xl transition-all border border-white/10 ${activeSubTab === 'Mis Listas' ? 'bg-white text-black shadow-lg' : 'bg-black/80 text-slate-400 hover:text-white'}`}
                        title="Mis Listas"
                    >
                        <ClipboardListIcon size={18} />
                    </button>
                )}
                
                {/* Add Button Logic */}
                <button 
                    onClick={() => view === 'food-database' ? openFoodEditor() : openCustomExerciseEditor()}
                    className="p-2.5 rounded-xl bg-primary-color text-white shadow-lg shadow-primary-color/20 hover:brightness-110 active:scale-95"
                    title={view === 'food-database' ? "Añadir Alimento" : "Crear Ejercicio"}
                >
                    <PlusIcon size={18} />
                </button>
            </div>
        </div>
    );

    const renderProgressButtons = () => {
        const pTabs = [
            { id: 'cuerpo', label: 'Cuerpo', icon: BodyIcon },
            { id: 'coach', label: 'Coach IA', icon: BrainIcon },
            { id: 'fuerza', label: 'Fuerza', icon: DumbbellIcon },
            { id: 'correlaciones', label: 'Correlaciones', icon: TrendingUpIcon }
        ];

        return (
            <div className="grid grid-cols-2 gap-2 w-full px-4">
                {pTabs.map(tab => (
                    <button
                        key={tab.id}
                        onClick={() => setActiveSubTabs(p => ({ ...p, 'progress': tab.id }))}
                        className={`h-9 flex items-center justify-center text-[10px] font-black uppercase rounded-xl transition-all duration-300 active:scale-95 gap-2 shadow-sm border border-white/5
                                  ${activeSubTab === tab.id ? 'bg-white text-black' : 'bg-black/60 backdrop-blur-md hover:bg-white/10 text-white'}`}
                    >
                        <tab.icon size={14}/>
                        <span>{tab.label}</span>
                    </button>
                ))}
            </div>
        );
    };

    const renderDefaultButtons = () => {
        let buttons: any[] = [];
         if (context === 'athlete-profile') {
            buttons = [
                { id: 'vitals', label: 'Vitales', icon: ActivityIcon, action: () => setActiveSubTabs(p => ({ ...p, 'athlete-profile': 'vitals' })) },
                { id: 'anatomy', label: 'Anatomía', icon: BrainIcon, action: () => setActiveSubTabs(p => ({ ...p, 'athlete-profile': 'anatomy' })) },
                { id: 'nutrition', label: 'Nutrición', icon: UtensilsIcon, action: () => setActiveSubTabs(p => ({ ...p, 'athlete-profile': 'nutrition' })) }
            ];
        }

        return (
            <div className="w-full flex items-center justify-center px-4 gap-3">
                {buttons.map((btn) => (
                    <button
                        key={btn.id}
                        onClick={btn.action}
                        className={`h-9 flex-1 flex items-center justify-center text-[10px] font-black uppercase rounded-xl transition-all duration-300 active:scale-95 gap-2 shadow-sm border border-white/5
                                  ${activeSubTab === btn.id ? 'bg-white text-black' : 'bg-black/60 backdrop-blur-md hover:bg-white/10 text-white'}`}
                    >
                        {btn.icon && <btn.icon size={14}/>}
                        <span>{btn.label}</span>
                    </button>
                ))}
            </div>
        );
    };

    // Position adjusted to sit above the new anchored bar (approx 85px for safety)
    return (
        <div 
            className={`absolute bottom-[85px] left-0 right-0 w-full transition-all duration-500 ease-[cubic-bezier(0.34,1.56,0.64,1)] flex flex-col justify-end items-center gap-3 pb-2 z-50
                        ${isActive ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4 pointer-events-none'}`}
        >
            {isDatabaseContext ? renderDatabaseControls() : 
             context === 'progress' ? renderProgressButtons() : 
             renderDefaultButtons()}
        </div>
    );
};

export default SubTabBar;