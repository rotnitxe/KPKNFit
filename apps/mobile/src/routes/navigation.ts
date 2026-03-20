import {
  navigateFromExternalTarget,
  routerBack as navigationBack,
  routerNavigate as navigationNavigate,
  viewToPath as navigationViewToPath,
} from '@/navigation/navigationRef';

type LegacyView = Parameters<typeof navigationNavigate>[0];
type NavigationData = Parameters<typeof navigationNavigate>[1];

let routerRef: unknown = null;

export function setRouterRef(router: unknown) {
  routerRef = router;
}

export function getRouterRef() {
  return routerRef;
}

export function viewToPath(view: LegacyView, data?: NavigationData) {
  return navigationViewToPath(view, data);
}

export function routerNavigate(view: LegacyView, data?: NavigationData, _replace?: boolean) {
  navigationNavigate(view, data);
}

export function routerBack() {
  navigationBack();
}

export function navigateToPath(path: string) {
  navigateFromExternalTarget(path);
}

