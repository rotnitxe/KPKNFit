import { createRouter, createRoute, createRootRoute, createHashHistory, Outlet } from '@tanstack/react-router';
import { setRouterRef } from './navigation';

const rootRoute = createRootRoute({ component: () => <Outlet /> });

function outlet() { return { component: () => <Outlet /> }; }

const routeTree = rootRoute.addChildren([
    createRoute({ getParentRoute: () => rootRoute, path: '/', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/nutrition', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/recovery', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/sleep', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/tasks', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/programs', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/programs/$programId', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/programs/$programId/metric/$metricId', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/programs/$programId/edit', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/session-editor', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/workout', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/log-workout', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/session-detail', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/progress', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/settings', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/coach', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/log-hub', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/achievements', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/kpkn', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/kpkn/exercise/$exerciseId', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/kpkn/muscle/$muscleGroupId', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/kpkn/joint/$jointId', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/kpkn/tendon/$tendonId', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/kpkn/pattern/$movementPatternId', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/kpkn/body-part/$bodyPartId', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/kpkn/chain/$chainId', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/kpkn/category/$categoryName', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/body-lab', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/mobility-lab', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/ai-art-studio', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/training-purpose', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/exercise-database', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/food-database', ...outlet() }),
    createRoute({ getParentRoute: () => rootRoute, path: '/smart-meal-planner', ...outlet() }),
]);

export const hashHistory = createHashHistory();

export const router = createRouter({
    routeTree,
    history: hashHistory,
    defaultNotFoundComponent: () => null,
});

setRouterRef(router);

declare module '@tanstack/react-router' {
    interface Register {
        router: typeof router;
    }
}
