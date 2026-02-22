import type { View } from '../types';

let _router: any = null;

export function setRouterRef(router: any) {
    _router = router;
}

export function getRouterRef() {
    return _router;
}

export function viewToPath(view: View, data?: any): string {
    switch (view) {
        case 'home': return '/';
        case 'nutrition': return '/nutrition';
        case 'recovery': return '/recovery';
        case 'sleep': return '/sleep';
        case 'tasks': return '/tasks';
        case 'programs': return '/programs';
        case 'program-detail': return `/programs/${data?.programId || '_'}`;
        case 'program-editor': return `/programs/${data?.programId || '_new'}/edit`;
        case 'session-editor': return '/session-editor';
        case 'workout': return '/workout';
        case 'log-workout': return '/log-workout';
        case 'session-detail': return '/session-detail';
        case 'progress': return '/progress';
        case 'settings': return '/settings';
        case 'coach': return '/coach';
        case 'log-hub': return '/log-hub';
        case 'achievements': return '/achievements';
        case 'kpkn': return '/kpkn';
        case 'exercise-detail': return `/kpkn/exercise/${data?.exerciseId || '_'}`;
        case 'muscle-group-detail': return `/kpkn/muscle/${data?.muscleGroupId || '_'}`;
        case 'body-part-detail': return `/kpkn/body-part/${data?.bodyPartId || '_'}`;
        case 'chain-detail': return `/kpkn/chain/${data?.chainId || '_'}`;
        case 'muscle-category': return `/kpkn/category/${encodeURIComponent(data?.categoryName || '_')}`;
        case 'hall-of-fame': return '/hall-of-fame';
        case 'body-lab': return '/body-lab';
        case 'mobility-lab': return '/mobility-lab';
        case 'ai-art-studio': return '/ai-art-studio';
        case 'training-purpose': return '/training-purpose';
        case 'exercise-database': return '/exercise-database';
        case 'food-database': return '/food-database';
        case 'food-detail': return '/food-database';
        case 'smart-meal-planner': return '/smart-meal-planner';
        case 'social-feed': return '/';
        case 'athlete-profile': return '/';
        default: return '/';
    }
}

export function routerNavigate(view: View, data?: any, replace?: boolean) {
    if (!_router) return;
    const path = viewToPath(view, data);
    try {
        _router.navigate({ to: path, replace });
    } catch (e) {
        console.warn('Router navigation failed, falling back:', e);
        if (replace) window.history.replaceState({ view }, '', `#${path}`);
        else window.history.pushState({ view }, '', `#${path}`);
    }
}

export function routerBack() {
    if (_router) {
        _router.history.back();
    } else {
        window.history.back();
    }
}
