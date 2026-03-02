// components/TabBar.tsx
// Android Material 3–style bottom navigation

import React from 'react';
import { View, TabBarActions } from '../types';
import { DumbbellIcon, PlusIcon, RingIcon, PlateIcon, WikiLabIcon } from './icons';
import { WorkoutSessionActionBar, WorkoutCarouselPlaceholderBar, EditorActionBar } from './ContextualActionBars';
import { useAppContext } from '../contexts/AppContext';

interface TabBarProps {
  activeView: View;
  navigate: (view: View) => void;
  context: 'default' | 'workout' | 'session-editor' | 'log-workout' | 'program-editor' | 'exercise-detail';
  actions: TabBarActions;
  isSubTabBarActive?: boolean;
  workoutViewMode?: 'carousel' | 'list';
}

const NavButton: React.FC<{
    icon: React.FC<any>;
    isActive: boolean;
    onClick: () => void;
    label: string;
    testId?: string;
    isTextIcon?: boolean;
}> = ({ icon: Icon, isActive, onClick, label, testId, isTextIcon }) => (
    <button
        data-testid={testId}
        onClick={onClick}
        aria-label={label}
        className="relative flex flex-col items-center justify-center flex-1 h-full gap-1 py-2 min-w-0 outline-none active:scale-95 transition-transform"
    >
        <span
            className={`flex items-center justify-center w-10 h-8 rounded-full transition-colors ${
                isActive ? 'bg-white/12 text-white' : 'text-zinc-500'
            }`}
        >
            {isTextIcon ? (
                <Icon size={20} className={isActive ? 'text-white' : 'text-zinc-500'} />
            ) : (
                <Icon
                    size={22}
                    className={isActive ? 'text-white' : 'text-zinc-500'}
                    strokeWidth={isActive ? 2.5 : 2}
                />
            )}
        </span>
        <span
            className={`text-[10px] font-medium truncate max-w-full px-1 ${
                isActive ? 'text-white' : 'text-zinc-500'
            }`}
        >
            {label}
        </span>
    </button>
);

const PrimeNextTabBar: React.FC<TabBarProps> = ({ activeView, navigate, actions }) => {
    const { activeProgramState, navigateTo } = useAppContext();

    const forceTabs = ['home', 'programs', 'nutrition', 'kpkn'];

    const TAB_CONFIG: Record<string, { icon: React.FC<any>, label: string, view: View, isTextIcon?: boolean }> = {
        'home': { icon: RingIcon, label: 'Tú', view: 'home' },
        'programs': { icon: DumbbellIcon, label: 'Entrenamiento', view: 'programs' },
        'nutrition': { icon: PlateIcon, label: 'Nutrición', view: 'nutrition' },
        'kpkn': { icon: WikiLabIcon, label: 'WikiLab', view: 'kpkn', isTextIcon: true },
    };

    const handleNavClick = (view: View) => {
        if (view === 'programs' && activeProgramState && activeProgramState.status === 'active') {
            navigate('programs');
            navigateTo('program-detail', { programId: activeProgramState.programId });
            return;
        }
        navigate(view);
    };

    const leftTabs = forceTabs.slice(0, 2);
    const rightTabs = forceTabs.slice(2, 4);

    return (
        <nav
            role="navigation"
            aria-label="Navegación principal"
            className="relative flex w-full h-full items-stretch min-h-[64px] px-1"
        >
            {leftTabs.map(tabKey => (
                <NavButton
                    key={tabKey}
                    testId={`nav-${tabKey}`}
                    icon={TAB_CONFIG[tabKey].icon}
                    isActive={activeView === TAB_CONFIG[tabKey].view || (tabKey === 'nutrition' && activeView === 'body-progress')}
                    onClick={() => handleNavClick(TAB_CONFIG[tabKey].view)}
                    label={TAB_CONFIG[tabKey].label}
                    isTextIcon={TAB_CONFIG[tabKey].isTextIcon}
                />
            ))}

            <div className="flex items-center justify-center flex-1 min-w-0 py-1">
                <button
                    data-testid="nav-plus"
                    aria-label="Abrir menú de registro"
                    onClick={actions.onLogPress}
                    className="flex items-center justify-center w-14 h-14 rounded-2xl bg-white text-black shadow-lg active:scale-95 transition-transform"
                >
                    <PlusIcon size={24} strokeWidth={3} />
                </button>
            </div>

            {rightTabs.map(tabKey => (
                <NavButton
                    key={tabKey}
                    testId={`nav-${tabKey}`}
                    icon={TAB_CONFIG[tabKey].icon}
                    isActive={activeView === TAB_CONFIG[tabKey].view || (tabKey === 'nutrition' && activeView === 'body-progress')}
                    onClick={() => handleNavClick(TAB_CONFIG[tabKey].view)}
                    label={TAB_CONFIG[tabKey].label}
                    isTextIcon={TAB_CONFIG[tabKey].isTextIcon}
                />
            ))}
        </nav>
    );
};

const TabBar: React.FC<TabBarProps> = (props) => {
    const { context, actions, workoutViewMode } = props;
    let content: React.ReactNode;
    switch (context) {
        case 'workout': content = (workoutViewMode === 'carousel' || workoutViewMode === undefined)
            ? <WorkoutCarouselPlaceholderBar actions={actions} />
            : <WorkoutSessionActionBar actions={actions} />; break;
        case 'session-editor':
        case 'log-workout':
        case 'program-editor': content = <EditorActionBar context={context} actions={actions} />; break;
        default: content = <PrimeNextTabBar {...props} />; break;
    }
    return (
        <div
            className="w-full h-full pointer-events-auto"
            style={{ backgroundColor: 'var(--tab-bar-bg-color, #121212)' }}
        >
            {content}
        </div>
    );
};

export default TabBar;
