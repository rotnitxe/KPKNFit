// components/TabBar.tsx
// Android Material 3–style bottom navigation

import React, { useState, useRef, useCallback, useEffect } from 'react';
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

const IconNavButton: React.FC<{
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
        type="button"
        className={`flex items-center justify-center w-12 h-12 rounded-full transition duration-300 ${isActive
            ? 'bg-[var(--md-sys-color-secondary-container)] shadow-[0_6px_12px_rgba(0,0,0,0.18)]'
            : 'hover:bg-[var(--md-sys-color-on-surface)]/[0.12]'
            }`}
    >
        {isTextIcon ? (
            <Icon size={20} className={isActive ? 'text-[var(--md-sys-color-on-secondary-container)]' : 'text-[var(--md-sys-color-on-surface-variant)]'} />
        ) : (
            <Icon
                size={20}
                className={isActive ? 'text-[var(--md-sys-color-on-secondary-container)]' : 'text-[var(--md-sys-color-on-surface-variant)]'}
                strokeWidth={isActive ? 2.5 : 2}
            />
        )}
    </button>
);

const PrimeNextTabBar: React.FC<TabBarProps> = ({ activeView, navigate, actions }) => {
    const { activeProgramState, navigateTo } = useAppContext();
    const [isCollapsed, setIsCollapsed] = useState(false);
    const longPressTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const toggleCollapse = useCallback(() => setIsCollapsed(prev => !prev), []);
    const startLongPress = useCallback(() => {
        if (longPressTimerRef.current) return;
        longPressTimerRef.current = setTimeout(() => {
            toggleCollapse();
            longPressTimerRef.current = null;
        }, 600);
    }, [toggleCollapse]);
    const cancelLongPress = useCallback(() => {
        if (longPressTimerRef.current) {
            clearTimeout(longPressTimerRef.current);
            longPressTimerRef.current = null;
        }
    }, []);
    useEffect(() => () => cancelLongPress(), [cancelLongPress]);

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
            {!isCollapsed && (
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
            )}

            <div className={`flex items-center justify-center ${isCollapsed ? 'flex-1' : 'px-3'}`}>
                <button
                    data-testid="nav-plus"
                    aria-label="Menu principal"
                    type="button"
                    onClick={actions.onLogPress}
                    onPointerDown={startLongPress}
                    onPointerUp={cancelLongPress}
                    onPointerLeave={cancelLongPress}
                    onPointerCancel={cancelLongPress}
                    className={`flex items-center justify-center w-16 h-16 rounded-full transition-all duration-300 ${isCollapsed
                        ? 'bg-[var(--md-sys-color-primary)] shadow-[0_18px_44px_rgba(0,0,0,0.35)]'
                        : 'bg-white/0 hover:bg-[var(--md-sys-color-primary)]/[0.16] border border-white/0'
                        }`}
                >
                    <KpknLogoIcon
                        size={isCollapsed ? 60 : 52}
                        className={isCollapsed ? 'text-[var(--md-sys-color-on-primary)]' : 'text-[var(--md-sys-color-primary)]/90'}
                    />
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
