/**
 * Auth store – manages Supabase authentication state.
 * Replaces the Google sign-in flow from useGoogleDrive.
 */
import { create } from 'zustand';
import { supabase } from '../services/supabaseService';
import type { User, Session as SupabaseSession } from '@supabase/supabase-js';

interface AuthStoreState {
    user: User | null;
    session: SupabaseSession | null;
    isLoading: boolean;
    isAuthenticated: boolean;

    initialize: () => Promise<void>;
    signInWithGoogle: () => Promise<void>;
    signInWithApple: () => Promise<void>;
    signInWithOAuth: (provider: 'google' | 'apple' | 'github') => Promise<void>;
    signIn: (email: string, password: string) => Promise<{ error: any }>;
    signUp: (email: string, password: string) => Promise<{ error: any }>;
    signOut: () => Promise<void>;
}

export const useAuthStore = create<AuthStoreState>()((set, get) => ({
    user: null,
    session: null,
    isLoading: true,
    isAuthenticated: false,

    initialize: async () => {
        try {
            const { data: { session } } = await supabase.auth.getSession();
            set({
                session,
                user: session?.user ?? null,
                isAuthenticated: !!session,
                isLoading: false,
            });

            supabase.auth.onAuthStateChange((_event, session) => {
                set({
                    session,
                    user: session?.user ?? null,
                    isAuthenticated: !!session,
                });
            });
        } catch (e) {
            console.error('Auth init error:', e);
            set({ isLoading: false });
        }
    },

    signInWithOAuth: async (provider: 'google' | 'apple' | 'github') => {
        const { error } = await supabase.auth.signInWithOAuth({
            provider,
            options: { redirectTo: window.location.origin },
        });
        if (error) throw error;
    },

    signInWithGoogle: async () => {
        await get().signInWithOAuth('google');
    },

    signInWithApple: async () => {
        await get().signInWithOAuth('apple');
    },

    signIn: async (email, password) => {
        const { error } = await supabase.auth.signInWithPassword({ email, password });
        return { error };
    },

    signUp: async (email, password) => {
        const { error } = await supabase.auth.signUp({ email, password });
        return { error };
    },

    signOut: async () => {
        await supabase.auth.signOut();
        set({ user: null, session: null, isAuthenticated: false });
    },
}));
