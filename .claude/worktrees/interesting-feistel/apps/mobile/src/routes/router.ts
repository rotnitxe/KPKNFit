import { navigateToPath, routerBack, setRouterRef } from './navigation';

type NavigatePayload = { to: string; replace?: boolean };

export const hashHistory = {
  back: routerBack,
};

export const router = {
  navigate(payload: NavigatePayload) {
    navigateToPath(payload.to);
  },
  history: hashHistory,
};

setRouterRef(router);

