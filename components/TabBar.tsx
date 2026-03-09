// components/TabBar.tsx
// Android Material 3–style bottom navigation

import React from 'react';
import { View, TabBarActions } from '../types';
import { DumbbellIcon, PlusIcon, RingIcon, PlateIcon, WikiLabIcon, KpknLogoIcon } from './icons';
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
        className="relative flex flex-col items-center justify-center flex-1 h-full gap-1 min-w-0 outline-none active:scale-95 transition-all duration-300 cursor-pointer group"
    >
        {/* Active Indicator (Material 3 Pill) */}
        <div
            className={`flex items-center justify-center w-14 h-8 rounded-full transition-all duration-400 ${isActive
                ? 'bg-[var(--md-sys-color-secondary-container)] shadow-sm'
                : 'bg-transparent group-hover:bg-[var(--md-sys-color-on-surface)]/[0.08]'
                }`}
        >
            {isTextIcon ? (
                <Icon size={20} className={isActive ? 'text-[var(--md-sys-color-on-secondary-container)]' : 'text-[var(--md-sys-color-on-surface-variant)]'} />
            ) : (
                <Icon
                    size={22}
                    className={isActive ? 'text-[var(--md-sys-color-on-secondary-container)] fill-current' : 'text-[var(--md-sys-color-on-surface-variant)]'}
                    strokeWidth={isActive ? 2.5 : 2}
                />
            )}
        </div>
        <span
            className={`text-[10px] font-black uppercase tracking-widest transition-colors duration-300 truncate max-w-full px-1 ${isActive ? 'text-[var(--md-sys-color-on-surface)]' : 'text-[var(--md-sys-color-on-surface-variant)]/70'
                }`}
        >
            {label}
        </span>
    </button>
);

const PrimeNextTabBar: React.FC<TabBarProps> = ({ activeView, navigate, actions, isSubTabBarActive }) => {
    const { activeProgramState, navigateTo } = useAppContext();

    const forceTabs = ['home', 'programs', 'nutrition', 'wiki-home'];

    const TAB_CONFIG: Record<string, { icon: React.FC<any>, label: string, view: View, isTextIcon?: boolean }> = {
        'home': { icon: RingIcon, label: 'Tú', view: 'home' },
        'programs': { icon: DumbbellIcon, label: 'Entrenar', view: 'programs' },
        'nutrition': { icon: PlateIcon, label: 'Nutrición', view: 'nutrition' },
        'wiki-home': { icon: WikiLabIcon, label: 'WikiLab', view: 'wiki-home', isTextIcon: true },
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
            className="relative flex w-full h-full items-center justify-between px-2 bg-transparent"
        >
            <div className="flex flex-1 items-center justify-around h-full">
                {leftTabs.map(tabKey => {
                    const config = TAB_CONFIG[tabKey];
                    let isActive = false;
                    if (tabKey === 'home') isActive = activeView === 'home' || activeView === 'home-card-page';
                    if (tabKey === 'programs') isActive = activeView === 'programs' || activeView.startsWith('program-') || activeView === 'session-editor';

                    return (
                        <NavButton
                            key={tabKey}
                            testId={`nav-${tabKey}`}
                            icon={config.icon}
                            isActive={isActive}
                            onClick={() => handleNavClick(config.view)}
                            label={config.label}
                            isTextIcon={config.isTextIcon}
                        />
                    );
                })}
            </div>

            <div className="flex items-center justify-center px-3">
                <button
                    data-testid="nav-plus"
                    aria-label="Menu principal"
                    onClick={actions.onLogPress}
                    className={`flex items-center justify-center active:scale-95 transition-all duration-400 relative
                                ${isSubTabBarActive ? 'drop-shadow-[0_0_15px_rgba(var(--md-sys-color-primary-rgb),0.5)]' : ''}`}
                >
                    <KpknLogoIcon
                        size={isSubTabBarActive ? 54 : 48}
                        className={`transition-all duration-500 ${isSubTabBarActive ? 'text-[var(--md-sys-color-primary)]' : 'text-[var(--md-sys-color-primary)]/80 saturate-[0.8]'}`}
                    />
                    {isSubTabBarActive && (
                        <div className="absolute inset-0 bg-[var(--md-sys-color-primary)]/20 blur-2xl rounded-full scale-150 animate-pulse -z-10" />
                    )}
                </button>
            </div>

            <div className="flex flex-1 items-center justify-around h-full">
                {rightTabs.map(tabKey => {
                    const config = TAB_CONFIG[tabKey];
                    let isActive = false;
                    if (tabKey === 'nutrition') isActive = ['nutrition', 'body-progress', 'food-database', 'food-detail', 'smart-meal-planner'].includes(activeView);
                    if (tabKey === 'wiki-home') isActive = ['wiki-home', 'kpkn', 'exercise-database', 'ai-art-studio', 'body-lab', 'mobility-lab', 'training-purpose', 'muscle-category', 'chain-detail', 'exercise-detail', 'muscle-group-detail', 'joint-detail', 'tendon-detail', 'movement-pattern-detail', 'body-part-detail'].includes(activeView) || (activeView.endsWith('-detail') && !activeView.startsWith('program-'));

                    return (
                        <NavButton
                            key={tabKey}
                            testId={`nav-${tabKey}`}
                            icon={config.icon}
                            isActive={isActive}
                            onClick={() => handleNavClick(config.view)}
                            label={config.label}
                            isTextIcon={config.isTextIcon}
                        />
                    );
                })}
            </div>
        </nav>
    );
};

const TabBar: React.FC<TabBarProps> = (props) => {
    const { context, actions, workoutViewMode, isSubTabBarActive } = props;
    let content: React.ReactNode;

    // Use isSubTabBarActive to change the look of the logo button if needed
    // But we need to pass it down to PrimeNextTabBar or handle it here.
    // PrimeNextTabBar is where the logo is rendered.

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
        <div className="w-full h-full pointer-events-auto bg-transparent overflow-hidden">
            {content}
        </div>
    );
};

export default TabBar;
