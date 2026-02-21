// components/TabBar.tsx
import React from 'react';
import { View, TabBarActions } from '../types';
import { DumbbellIcon, UtensilsIcon, PlusIcon, ActivityIcon, ClipboardListIcon, HomeIcon } from './icons';
import { playSound } from '../services/soundService';
import { hapticImpact as _hapticImpact, ImpactStyle } from '../services/hapticsService';
import { WorkoutSessionActionBar, EditorActionBar } from './ContextualActionBars';
import { useAppContext } from '../contexts/AppContext';

// Bypass de TypeScript: Adaptador para que los strings literales sean aceptados como Enums
const hapticImpact = (style?: any) => _hapticImpact(style);

interface TabBarProps {
  activeView: View;
  navigate: (view: View) => void;
  context: 'default' | 'workout' | 'session-editor' | 'log-workout' | 'program-editor' | 'exercise-detail';
  actions: TabBarActions;
  isSubTabBarActive?: boolean;
}

const NavButton: React.FC<{
    icon: React.FC<any>;
    isActive: boolean;
    onClick: () => void;
}> = ({ icon: Icon, isActive, onClick }) => (
    <button
        onClick={onClick}
        className="relative z-10 flex flex-col items-center justify-center w-full h-full outline-none group"
    >
        <Icon 
            size={22} 
            className={`transition-colors duration-500 ease-out ${isActive ? 'text-white' : 'text-zinc-500 group-hover:text-zinc-300'}`} 
            strokeWidth={isActive ? 2.5 : 2} 
        />
    </button>
);

const PrimeNextTabBar: React.FC<TabBarProps> = ({ activeView, navigate, actions }) => {
    const { programs, navigateTo, activeProgramState } = useAppContext();

    // NUEVA CONFIGURACIÓN DE TABS
    const forceTabs = ['home', 'programs', 'nutrition', 'your-lab'];
    
    const TAB_CONFIG: Record<string, { icon: React.FC<any>, label: string, view: View }> = {
        'home': { icon: HomeIcon, label: 'Inicio', view: 'home' },
        'programs': { icon: DumbbellIcon, label: 'Programas', view: 'programs' },
        'nutrition': { icon: UtensilsIcon, label: 'Nutrición', view: 'nutrition' },
        'your-lab': { icon: ClipboardListIcon, label: 'YourLab', view: 'your-lab' },
    };

    const handleNavClick = (view: View) => {
        playSound(activeView === view ? 'ui-click-sound' : 'tab-switch-sound');
        hapticImpact(ImpactStyle.Light);

        // LÓGICA INTELIGENTE DE PROGRAMAS (TÚNEL HACIA PROGRAMA ACTIVO)
        // Push 'programs' first so that Back from program-detail lands on the list
        if (view === 'programs' && activeProgramState && activeProgramState.status === 'active') {
            navigate('programs');
            navigateTo('program-detail', { programId: activeProgramState.programId });
            return;
        }

        navigate(view);
    };
    
    const leftTabs = forceTabs.slice(0, 2);
    const rightTabs = forceTabs.slice(2, 4);
    const allSlots = [...leftTabs, 'CENTER', ...rightTabs];
    const activeIndex = allSlots.indexOf(activeView);

    return (
        // NUEVO DISEÑO: Gris muy oscuro (zinc-900), translúcido (85%) y blur
        <div className="relative flex w-full h-full bg-zinc-900/85 backdrop-blur-2xl border-t border-white/5 items-center">
            
            <div 
                className="absolute top-0 bottom-0 left-0 flex items-center justify-center transition-transform duration-500 ease-[cubic-bezier(0.25,1,0.5,1)] pointer-events-none"
                style={{ 
                    width: `${100 / allSlots.length}%`,
                    transform: `translateX(${activeIndex * 100}%)`,
                    opacity: activeIndex !== -1 ? 1 : 0
                }}
            >
                <div className="w-10 h-10 bg-white/10 rounded-full" />
            </div>

            {leftTabs.map(tabKey => (
                <div key={tabKey} className="flex-1 h-full">
                    <NavButton icon={TAB_CONFIG[tabKey].icon} isActive={activeView === TAB_CONFIG[tabKey].view} onClick={() => handleNavClick(TAB_CONFIG[tabKey].view)} />
                </div>
            ))}

            <div className="flex-1 flex items-center justify-center h-full relative z-20">
                 <button onClick={actions.onLogPress} className="flex items-center justify-center group outline-none">
                    <div className="w-12 h-12 rounded-full bg-white text-black flex items-center justify-center group-active:scale-90 transition-transform duration-200 shadow-[0_0_20px_rgba(255,255,255,0.2)]">
                         <PlusIcon size={22} strokeWidth={3} />
                    </div>
                </button>
            </div>

            {rightTabs.map(tabKey => (
                <div key={tabKey} className="flex-1 h-full">
                    <NavButton icon={TAB_CONFIG[tabKey].icon} isActive={activeView === TAB_CONFIG[tabKey].view} onClick={() => handleNavClick(TAB_CONFIG[tabKey].view)} />
                </div>
            ))}
        </div>
    );
}

const TabBar: React.FC<TabBarProps> = (props) => {
    const { context, actions } = props;
    let content: React.ReactNode;
    switch (context) {
        case 'workout': content = <WorkoutSessionActionBar actions={actions} />; break;
        case 'session-editor':
        case 'log-workout':
        case 'program-editor': content = <EditorActionBar context={context} actions={actions} />; break;
        default: content = <PrimeNextTabBar {...props} />; break;
    }
    return <div className="w-full h-full pointer-events-auto">{content}</div>;
};

export default TabBar;