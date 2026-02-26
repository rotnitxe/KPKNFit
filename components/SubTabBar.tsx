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
    const { navigateTo, openCustomExerciseEditor, view } = useAppContext();
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
        <div className="flex items-center gap-1.5 w-full px-2 py-1">
            {/* Search Box */}
            <div className="relative flex-grow">
                <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder={view === 'food-database' ? "Buscar alimento..." : "Buscar ejercicio, músculo, articulación..."}
                    className="w-full h-9 bg-black/90 backdrop-blur-md border border-white/10 rounded-xl pl-9 pr-3 py-1.5 text-[11px] text-white focus:outline-none focus:ring-1 focus:ring-white/30 placeholder-white/30"
                />
                <SearchIcon className="absolute left-2.5 top-1/2 -translate-y-1/2 text-white/40" size={12} />
            </div>

            {/* Icon Actions */}
            <div className="flex gap-1">
                {/* Toggle ejercicios: solo en food-database (KPKN ya no incluye alimentos, usar Nutrition) */}
                {view === 'food-database' && (
                    <button 
                        onClick={() => navigateTo('kpkn')}
                        className="p-2 rounded-lg bg-black/90 text-slate-400 hover:text-blue-400 border border-white/10 transition-all shrink-0"
                        title="Ir a Ejercicios"
                    >
                        <DumbbellIcon size={16} />
                    </button>
                )}

                {/* Context-Specific Actions */}
                {view === 'kpkn' && (
                     <button 
                        onClick={() => { setActiveSubTabs(p => ({...p, 'kpkn': activeSubTab === 'Mis Listas' ? 'Explorar' : 'Mis Listas'})); }}
                        className={`p-2 rounded-lg transition-all border border-white/10 shrink-0 ${activeSubTab === 'Mis Listas' ? 'bg-white text-black' : 'bg-black/90 text-slate-400 hover:text-white'}`}
                        title="Mis Listas"
                    >
                        <ClipboardListIcon size={16} />
                    </button>
                )}
                
                {/* Add Button: solo en KPKN (crear ejercicio) */}
                {view === 'kpkn' && (
                    <button 
                        onClick={openCustomExerciseEditor}
                        className="p-2 rounded-lg bg-primary-color text-white shrink-0 hover:brightness-110 active:scale-95"
                        title="Crear Ejercicio"
                    >
                        <PlusIcon size={16} />
                    </button>
                )}
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
            <div className="grid grid-cols-2 gap-1.5 w-full px-2 py-1">
                {pTabs.map(tab => (
                    <button
                        key={tab.id}
                        onClick={() => setActiveSubTabs(p => ({ ...p, 'progress': tab.id }))}
                        className={`h-8 flex items-center justify-center text-[10px] font-black uppercase rounded-lg transition-all duration-200 active:scale-95 gap-1.5 border border-white/5
                                  ${activeSubTab === tab.id ? 'bg-white text-black' : 'bg-black/80 backdrop-blur-md hover:bg-white/10 text-white'}`}
                    >
                        <tab.icon size={12}/>
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
            <div className="w-full flex items-center justify-center px-2 py-1 gap-1.5">
                {buttons.map((btn) => (
                    <button
                        key={btn.id}
                        onClick={btn.action}
                        className={`h-8 flex-1 flex items-center justify-center text-[10px] font-black uppercase rounded-lg transition-all duration-200 active:scale-95 gap-1.5 border border-white/5
                                  ${activeSubTab === btn.id ? 'bg-white text-black' : 'bg-black/80 backdrop-blur-md hover:bg-white/10 text-white'}`}
                    >
                        {btn.icon && <btn.icon size={12}/>}
                        <span>{btn.label}</span>
                    </button>
                ))}
            </div>
        );
    };

    // Integrada en la TabBar: misma barra, sin separación
    return (
        <div 
            className={`w-full shrink-0 transition-all duration-200 ease-out flex flex-col justify-center items-center gap-1 py-1.5 border-b border-white/5
                        ${isActive ? 'opacity-100 max-h-[80px]' : 'opacity-0 max-h-0 py-0 overflow-hidden pointer-events-none'}`}
        >
            {isDatabaseContext ? renderDatabaseControls() : 
             context === 'progress' ? renderProgressButtons() : 
             renderDefaultButtons()}
        </div>
    );
};

export default SubTabBar;