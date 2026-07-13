import { create } from 'zustand';

export type ToastType = 'success' | 'danger' | 'warning' | 'info';

export interface ToastData {
  id: number;
  message: string;
  type: ToastType;
  title?: string;
  duration: number;
  createdAt: number;
  why?: string;
}

type Updater<T> = T | ((prev: T) => T);

interface UIStoreState {
  view: string;
  historyStack: { view: string; data?: unknown }[];
  isMenuOpen: boolean;
  isTimersModalOpen: boolean;
  isReadinessModalOpen: boolean;
  activeProgramId: string | null;
  toasts: ToastData[];
  toastQueue: Array<{ message: string; type: ToastType; title?: string; duration?: number; why?: string }>;
  setView: (view: string) => void;
  setHistoryStack: (updater: Updater<{ view: string; data?: unknown }[]>) => void;
  setIsMenuOpen: (value: Updater<boolean>) => void;
  setIsTimersModalOpen: (value: Updater<boolean>) => void;
  setIsReadinessModalOpen: (value: Updater<boolean>) => void;
  setActiveProgramId: (value: string | null) => void;
  setToasts: (value: Updater<ToastData[]>) => void;
  addToast: (message: string, type?: ToastType, title?: string, duration?: number, why?: string) => void;
  removeToast: (id: number) => void;
}

function applyUpdater<T>(current: T, updater: Updater<T>): T {
  return typeof updater === 'function' ? (updater as (prev: T) => T)(current) : updater;
}

export const useUIStore = create<UIStoreState>()((set) => ({
  view: 'home',
  historyStack: [{ view: 'home' }],
  isMenuOpen: false,
  isTimersModalOpen: false,
  isReadinessModalOpen: false,
  activeProgramId: null,
  toasts: [],
  toastQueue: [],

  setView: (view) => set({ view }),
  setHistoryStack: (updater) => set((state) => ({ historyStack: applyUpdater(state.historyStack, updater) })),
  setIsMenuOpen: (value) => set((state) => ({ isMenuOpen: applyUpdater(state.isMenuOpen, value) })),
  setIsTimersModalOpen: (value) =>
    set((state) => ({ isTimersModalOpen: applyUpdater(state.isTimersModalOpen, value) })),
  setIsReadinessModalOpen: (value) =>
    set((state) => ({ isReadinessModalOpen: applyUpdater(state.isReadinessModalOpen, value) })),
  setActiveProgramId: (value) => set({ activeProgramId: value }),
  setToasts: (value) => set((state) => ({ toasts: applyUpdater(state.toasts, value) })),

  addToast: (message, type = 'success', title, duration = 3000, why) =>
    set((state) => {
      const next: ToastData = {
        id: Date.now() + Math.round(Math.random() * 1000),
        message,
        type,
        title,
        duration,
        createdAt: Date.now(),
        why,
      };

      if (state.toasts.length >= 3) {
        return {
          toastQueue: [...state.toastQueue, { message, type, title, duration, why }],
        };
      }

      return { toasts: [...state.toasts, next] };
    }),

  removeToast: (id) =>
    set((state) => {
      const filtered = state.toasts.filter((toast) => toast.id !== id);
      if (state.toastQueue.length === 0) {
        return { toasts: filtered };
      }

      const [nextQueued, ...restQueue] = state.toastQueue;
      const queuedToast: ToastData = {
        id: Date.now() + Math.round(Math.random() * 1000),
        message: nextQueued.message,
        type: nextQueued.type,
        title: nextQueued.title,
        duration: nextQueued.duration ?? 3000,
        createdAt: Date.now(),
        why: nextQueued.why,
      };
      return { toasts: [...filtered, queuedToast], toastQueue: restQueue };
    }),
}));

