// components/SubTabBar.tsx
import React from 'react';
import { useAppContext, useAppDispatch, useUIState, useUIDispatch } from '../contexts/AppContext';
import {
    SearchIcon, DumbbellIcon, ClipboardListIcon, PlusIcon,
    UtensilsIcon, BrainIcon, TrendingUpIcon, BodyIcon,
    ActivityIcon, SettingsIcon, WorkoutIcon
} from './icons';

interface SubTabBarProps {
    context: 'kpkn' | 'food-database' | 'progress' | 'athlete-profile' | null;
    isActive: boolean;
    viewingExerciseId?: string | null;
    onEditExercisePress?: () => void;
    onFoodAppendixPress?: () => void;
}

const SubTabBar: React.FC<SubTabBarProps> = ({ context, isActive, viewingExerciseId, onEditExercisePress, onFoodAppendixPress }) => {
    const { navigateTo, openCustomExerciseEditor, view, setIsStartWorkoutModalOpen, setIsNutritionLogModalOpen } = useAppContext();
    const { setExerciseToAddId } = useAppDispatch();
    const { searchQuery, activeSubTabs, isLogActionSheetOpen } = useUIState();
    const { setSearchQuery, setActiveSubTabs, setIsLogActionSheetOpen } = useUIDispatch();

    const activeSubTab = context ? (activeSubTabs[context] || (context === 'progress' ? 'cuerpo' : context === 'athlete-profile' ? 'vitals' : 'Explorar')) : null;

    if (!context) return null;

    const handleAction = (callback: () => void) => {
        callback();
        setIsLogActionSheetOpen(false);
    };

    const renderKpknButtons = () => (
        <div className="flex items-center justify-center w-full h-full px-6 gap-8 overflow-hidden">
            <button
                onClick={() => handleAction(() => {
                    if (onFoodAppendixPress) onFoodAppendixPress();
                    else setIsNutritionLogModalOpen(true);
                })}
                className="flex items-center justify-center active:scale-95 transition-all group py-2"
            >
                <span className="text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F]/40 group-hover:text-primary transition-all font-['Roboto']">Comida</span>
            </button>

            <div className="w-[1px] h-3 bg-slate-400/20" />

            <button
                onClick={() => handleAction(() => navigateTo('settings'))}
                className="flex items-center justify-center active:scale-95 transition-all group py-2"
            >
                <span className="text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F]/40 group-hover:text-primary transition-all font-['Roboto']">Ajustes</span>
            </button>

            <div className="w-[1px] h-3 bg-slate-400/20" />

            <button
                onClick={() => handleAction(() => setIsStartWorkoutModalOpen(true))}
                className="flex items-center justify-center active:scale-95 transition-all group py-2"
            >
                <span className="text-[10px] font-black uppercase tracking-[0.2em] text-[#49454F]/40 group-hover:text-primary transition-all font-['Roboto']">Sesión</span>
            </button>
        </div>
    );

    const renderDatabaseControls = () => (
        <div className="flex items-center gap-1.5 w-full px-2 py-1">
            <div className="relative flex-grow">
                <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder={view === 'food-database' ? "Buscar alimento..." : "Buscar ejercicio..."}
                    className="w-full h-9 bg-black/5 rounded-xl pl-9 pr-3 text-[11px] focus:outline-none focus:ring-1 focus:ring-blue-500 placeholder-zinc-400"
                />
                <SearchIcon className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
            </div>

            <div className="flex gap-1">
                {view === 'kpkn' && (
                    <button
                        onClick={() => openCustomExerciseEditor()}
                        className="p-2 rounded-lg bg-blue-600 text-white active:scale-95 shadow-lg shadow-blue-500/20"
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
            <div className="grid grid-cols-2 gap-1.5 w-full px-2">
                {pTabs.map(tab => (
                    <button
                        key={tab.id}
                        onClick={() => setActiveSubTabs(p => ({ ...p, 'progress': tab.id }))}
                        className={`h-8 flex items-center justify-center text-[9px] font-black uppercase rounded-lg transition-all gap-1.5
                                  ${activeSubTab === tab.id ? 'bg-blue-600 text-white shadow-md' : 'bg-black/5 text-slate-500'}`}
                    >
                        <tab.icon size={12} />
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
                { id: 'nutrition', label: 'Metas', icon: UtensilsIcon, action: () => setActiveSubTabs(p => ({ ...p, 'athlete-profile': 'nutrition' })) }
            ];
        }

        return (
            <div className="w-full flex items-center justify-center px-2 gap-1.5">
                {buttons.map((btn) => (
                    <button
                        key={btn.id}
                        onClick={btn.action}
                        className={`h-8 flex-1 flex items-center justify-center text-[9px] font-black uppercase rounded-lg transition-all gap-1.5
                                  ${activeSubTab === btn.id ? 'bg-blue-600 text-white shadow-md' : 'bg-black/5 text-slate-500'}`}
                    >
                        {btn.icon && <btn.icon size={12} />}
                        <span>{btn.label}</span>
                    </button>
                ))}
            </div>
        );
    };

    return (
        <div className="w-full flex flex-col justify-center items-center">
            <div className={`w-full px-1 transition-all duration-500 delay-100 ${isActive ? 'translate-y-0 opacity-100' : 'translate-y-4 opacity-0'}`}>
                {context === 'kpkn' && (view !== 'kpkn' || isLogActionSheetOpen) ? renderKpknButtons() :
                    (context === 'kpkn' || context === 'food-database') ? renderDatabaseControls() :
                        context === 'progress' ? renderProgressButtons() :
                            renderDefaultButtons()}
            </div>
        </div>
    );
};

export default SubTabBar;