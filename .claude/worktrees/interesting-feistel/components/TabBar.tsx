// components/TabBar.tsx
// Android Material 3–style bottom navigation

import React, { useState, useRef, useCallback, useEffect } from 'react';
import { View, TabBarActions } from '../types';
import { DumbbellIcon, RingIcon, PlateIcon, WikiLabIcon, KpknLogoIcon, TripleRingsIcon, UserBadgeIcon, SettingsIcon } from './icons';
import { WorkoutSessionActionBar, WorkoutCarouselPlaceholderBar, EditorActionBar } from './ContextualActionBars';
import { useAppContext } from '../contexts/AppContext';
import { RegisterFoodDrawer } from './nutrition/RegisterFoodDrawer';
import { NutritionLog } from '../types';

interface TabBarProps {
    activeView: View;
    navigate: (view: View) => void;
    context: 'default' | 'workout' | 'session-editor' | 'log-workout' | 'program-editor' | 'exercise-detail';
    actions: TabBarActions;
    workoutViewMode?: 'carousel' | 'list';
    isCollapsed?: boolean;
    onCollapsedChange?: (collapsed: boolean) => void;
}

const IconNavButton: React.FC<{
    icon: React.FC<any>;
    isActive: boolean;
    onClick: () => void;
    label: string;
    testId?: string;
    isTextIcon?: boolean;
}> = ({ icon: Icon, isActive, onClick, label, testId, isTextIcon }) => {
    const sizeStyle = { width: 'clamp(36px, 11vw, 48px)', height: 'clamp(36px, 11vw, 48px)' };
    return (
        <div className="relative flex flex-col items-center justify-center" style={sizeStyle}>
            <button
                data-testid={testId}
                onClick={onClick}
                aria-label={label}
                type="button"
                className="flex items-center justify-center rounded-full transition duration-300 flex-shrink-0 w-full h-full"
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
        </div>
    );
};

const PrimeNextTabBar: React.FC<TabBarProps> = ({ 
    activeView, 
    navigate, 
    actions,
    isCollapsed: parentIsCollapsed,
    onCollapsedChange 
}) => {
    const { activeProgramState, navigateTo } = useAppContext();
    const [isPressing, setIsPressing] = useState(false);
    const longPressTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    
    // Usar estado local solo si no viene del padre
    const [localCollapsed, setLocalCollapsed] = useState(false);
    const isCollapsed = parentIsCollapsed !== undefined ? parentIsCollapsed : localCollapsed;
    const setIsCollapsed = onCollapsedChange || setLocalCollapsed;

    const toggleCollapse = useCallback(() => setIsCollapsed(isCollapsed ? false : true), [isCollapsed, setIsCollapsed]);
    const startLongPress = useCallback(() => {
        if (longPressTimerRef.current) return;
        setIsPressing(true);
        longPressTimerRef.current = setTimeout(() => {
            toggleCollapse();
            longPressTimerRef.current = null;
            setIsPressing(false);
        }, 500);
    }, [toggleCollapse]);
    const cancelLongPress = useCallback(() => {
        if (longPressTimerRef.current) {
            clearTimeout(longPressTimerRef.current);
            longPressTimerRef.current = null;
        }
        setIsPressing(false);
    }, []);
    useEffect(() => () => cancelLongPress(), [cancelLongPress]);

    // Nueva configuración de tabs: 6 tabs en total
    // Izquierda: RINGS, Entreno, Nutrición (Pilares del Progreso Físico)
    // Derecha: Perfil, WikiLab, Ajustes (Información Útil)
    const leftTabs = ['rings', 'programs', 'nutrition'];
    const rightTabs = ['profile', 'wiki-home', 'settings'];

    const TAB_CONFIG: Record<string, { icon: React.FC<any>, label: string, view: View, isTextIcon?: boolean }> = {
        'profile': { icon: UserBadgeIcon, label: 'Mi Perfil', view: 'athlete-id' },
        'rings': { icon: TripleRingsIcon, label: 'Mis RINGS', view: 'my-rings' },
        'programs': { icon: DumbbellIcon, label: 'Entrenar', view: 'programs' },
        'nutrition': { icon: PlateIcon, label: 'Nutrición', view: 'nutrition' },
        'wiki-home': { icon: WikiLabIcon, label: 'WikiLab', view: 'wiki-home', isTextIcon: true },
        'settings': { icon: SettingsIcon, label: 'Ajustes', view: 'settings' },
    };

    const handleNavClick = (view: View) => {
        if (view === 'programs' && activeProgramState && activeProgramState.status === 'active') {
            navigate('programs');
            navigateTo('program-detail', { programId: activeProgramState.programId });
            return;
        }
        navigate(view);
    };

    return (
        <nav
            role="navigation"
            aria-label="Navegación principal"
            className="relative flex w-full h-full items-center justify-center bg-transparent gap-0 overflow-hidden"
        >
            {/* Left tabs - fold inward toward center */}
            <div
                className={`flex items-center justify-end h-full transition-all duration-500 ease-[cubic-bezier(0.2,0,0,1)] overflow-hidden ${
                    isCollapsed
                        ? 'w-0 opacity-0'
                        : 'w-[calc(50%-38px)] opacity-100'
                }`}
            >
                <div className={`flex items-center justify-end gap-1 transition-all duration-500 ease-[cubic-bezier(0.2,0,0,1)] ${
                    isCollapsed ? 'translate-x-8 opacity-0' : 'translate-x-0 opacity-100'
                }`}>
                    {leftTabs.map((tabKey, index) => {
                        const config = TAB_CONFIG[tabKey];
                        let isActive = false;
                        if (tabKey === 'rings') isActive = activeView === 'my-rings';
                        if (tabKey === 'programs') isActive = activeView === 'programs' || activeView.startsWith('program-') || activeView === 'session-editor';
                        if (tabKey === 'nutrition') isActive = ['nutrition', 'body-progress', 'food-database', 'food-detail', 'smart-meal-planner'].includes(activeView);

                        return (
                            <div key={`nav-${tabKey}`} className="relative flex flex-col items-center">
                                <IconNavButton
                                    testId={`nav-${tabKey}`}
                                    icon={config.icon}
                                    isActive={isActive}
                                    onClick={() => handleNavClick(config.view)}
                                    label={config.label}
                                    isTextIcon={config.isTextIcon}
                                />
                                {/* Línea indicadora debajo del ícono activo */}
                                {isActive && (
                                    <div className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-8 h-0.5 bg-[#3F51B5] rounded-full" />
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* Center KPKN button - Click normal = Home, Long-press = colapsar TabBar */}
            <div className="flex items-center justify-center shrink-0 relative z-10 px-2">
                {/* Background circle that appears when collapsed */}
                <div
                    className={`absolute inset-0 rounded-full transition-all duration-500 ease-[cubic-bezier(0.2,0,0,1)] ${
                        isCollapsed ? 'opacity-100 scale-100' : 'opacity-0 scale-75'
                    }`}
                    style={{
                        background: 'rgba(255, 255, 255, 0.95)',
                        boxShadow: '0 4px 16px rgba(0, 0, 0, 0.15)'
                    }}
                />

                <button
                    data-testid="nav-kpkn-home"
                    aria-label="Inicio"
                    type="button"
                    onClick={() => navigate('home')}
                    onPointerDown={startLongPress}
                    onPointerUp={cancelLongPress}
                    onPointerLeave={cancelLongPress}
                    onPointerCancel={cancelLongPress}
                    className={`relative flex items-center justify-center rounded-full transition-all duration-300 ${
                        isPressing ? 'scale-[0.95]' : 'scale-100'
                    }`}
                >
                    <KpknLogoIcon
                        size={36}
                        className="text-[var(--md-sys-color-primary)]/90"
                    />
                </button>
            </div>

            {/* Right tabs - fold inward toward center */}
            <div
                className={`flex items-center justify-start h-full transition-all duration-500 ease-[cubic-bezier(0.2,0,0,1)] overflow-hidden ${
                    isCollapsed
                        ? 'w-0 opacity-0'
                        : 'w-[calc(50%-38px)] opacity-100'
                }`}
            >
                <div className={`flex items-center justify-start gap-1 transition-all duration-500 ease-[cubic-bezier(0.2,0,0,1)] ${
                    isCollapsed ? '-translate-x-8 opacity-0' : 'translate-x-0 opacity-100'
                }`}>
                    {rightTabs.map((tabKey, index) => {
                        const config = TAB_CONFIG[tabKey];
                        let isActive = false;
                        if (tabKey === 'profile') isActive = activeView === 'athlete-id';
                        if (tabKey === 'wiki-home') isActive = ['wiki-home', 'kpkn', 'exercise-database', 'ai-art-studio', 'body-lab', 'mobility-lab', 'training-purpose', 'muscle-category', 'chain-detail', 'exercise-detail', 'muscle-group-detail', 'joint-detail', 'tendon-detail', 'movement-pattern-detail', 'body-part-detail'].includes(activeView) || (activeView.endsWith('-detail') && !activeView.startsWith('program-'));
                        if (tabKey === 'settings') isActive = activeView === 'settings';

                        return (
                            <div key={`nav-${tabKey}`} className="relative flex flex-col items-center">
                                <IconNavButton
                                    testId={`nav-${tabKey}`}
                                    icon={config.icon}
                                    isActive={isActive}
                                    onClick={() => handleNavClick(config.view)}
                                    label={config.label}
                                    isTextIcon={config.isTextIcon}
                                />
                                {/* Línea indicadora debajo del ícono activo */}
                                {isActive && (
                                    <div className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-8 h-0.5 bg-[#3F51B5] rounded-full" />
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
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
        <div className="w-full h-full pointer-events-auto bg-transparent overflow-hidden">
            {content}
        </div>
    );
};

// ─── TabBar Container (con animación de pliegue completa) ───────────────────

interface TabBarContainerProps {
    settings: any;
    tabBarContext: any;
    tabBarActions: any;
    activeView: View;
    navigateTo: (view: View, props?: any) => void;
    isFoodAppendixOpen: boolean;
    setIsNutritionLogModalOpen: (open: boolean) => void;
    setFoodRegistrationMode: (mode: 'drawer' | 'appendix') => void;
    handleSaveNutritionLog: (log: NutritionLog) => void;
}

export const TabBarContainer: React.FC<TabBarContainerProps> = ({
    settings,
    tabBarContext,
    tabBarActions,
    activeView,
    navigateTo,
    isFoodAppendixOpen,
    setIsNutritionLogModalOpen,
    setFoodRegistrationMode,
    handleSaveNutritionLog,
}) => {
    const [isCollapsed, setIsCollapsed] = useState(false);

    return (
        <div
            className="fixed bottom-6 left-0 right-0 z-[99999] px-4 pointer-events-none pb-[env(safe-area-inset-bottom,0px)] flex justify-center"
        >
            <div
                className={`pointer-events-auto relative overflow-hidden rounded-[32px] flex flex-col p-2 transition-all duration-500 ease-[cubic-bezier(0.2,0,0,1)]`}
                style={{
                    width: isCollapsed ? '76px' : '100%',
                    maxWidth: isCollapsed ? '76px' : '420px',
                    background: 'rgba(255, 255, 255, 0.72)',
                    backdropFilter: 'blur(40px) saturate(160%)',
                    WebkitBackdropFilter: 'blur(40px) saturate(160%)',
                    boxShadow: '0 25px 80px -15px rgba(0, 0, 0, 0.4), 0 10px 30px -10px rgba(0, 0, 0, 0.2), 0 0 0 1px rgba(0, 0, 0, 0.05)'
                }}
            >
                {/* Food Appendix */}
                <div className="absolute inset-0 flex justify-center items-end pointer-events-none">
                    <div
                        className={`pointer-events-none transition-all duration-500 ease-[cubic-bezier(0.34,1.56,0.64,1)] ${
                            isFoodAppendixOpen ? 'opacity-100 translate-y-0 pointer-events-auto' : 'opacity-0 translate-y-4'
                        }`}
                    >
                        <RegisterFoodDrawer
                            isOpen={isFoodAppendixOpen}
                            onClose={() => {
                                setIsNutritionLogModalOpen(false);
                                setFoodRegistrationMode('drawer');
                            }}
                            onSave={(log) => {
                                handleSaveNutritionLog(log);
                                setIsNutritionLogModalOpen(false);
                                setFoodRegistrationMode('drawer');
                            }}
                            settings={settings}
                            displayMode="appendix"
                        />
                    </div>
                </div>

                {/* Main TabBar base - pasa el estado colapsado */}
                <div className="h-[68px] shrink-0 w-full">
                    <TabBar
                        activeView={activeView}
                        navigate={(v) => navigateTo(v)}
                        context={tabBarContext}
                        actions={tabBarActions}
                        workoutViewMode="carousel"
                        isCollapsed={isCollapsed}
                        onCollapsedChange={setIsCollapsed}
                    />
                </div>
            </div>
        </div>
    );
};

export default TabBar;
