import { create } from 'zustand';

type AuthError = { message: string };

interface AuthStoreState {
  user: unknown | null;
  session: unknown | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  initialize: () => Promise<void>;
  signInWithGoogle: () => Promise<void>;
  signInWithApple: () => Promise<void>;
  signInWithOAuth: (provider: 'google' | 'apple' | 'github') => Promise<void>;
  signIn: (email: string, password: string) => Promise<{ error: AuthError | null }>;
  signUp: (email: string, password: string) => Promise<{ error: AuthError | null }>;
  signOut: () => Promise<void>;
}

function unsupportedError(message = 'Autenticacion remota no habilitada en RN offline build.'): AuthError {
  return { message };
}

export const useAuthStore = create<AuthStoreState>()((set) => ({
  user: null,
  session: null,
  isLoading: true,
  isAuthenticated: false,

  initialize: async () => {
    set({ isLoading: false, isAuthenticated: false, user: null, session: null });
  },

  signInWithOAuth: async (_provider) => {
    throw new Error(unsupportedError().message);
  },

  signInWithGoogle: async () => {
    throw new Error(unsupportedError().message);
  },

  signInWithApple: async () => {
    throw new Error(unsupportedError().message);
  },

  signIn: async (_email, _password) => ({ error: unsupportedError() }),

  signUp: async (_email, _password) => ({ error: unsupportedError() }),

  signOut: async () => {
    set({ user: null, session: null, isAuthenticated: false });
  },
}));

