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

    signInWithGoogle: async () => {
        const { error } = await supabase.auth.signInWithOAuth({
            provider: 'google',
            options: { redirectTo: window.location.origin },
        });
        if (error) throw error;
    },

    signInWithApple: async () => {
        const { error } = await supabase.auth.signInWithOAuth({
            provider: 'apple',
            options: { redirectTo: window.location.origin },
        });
        if (error) throw error;
    },

    signOut: async () => {
        await supabase.auth.signOut();
        set({ user: null, session: null, isAuthenticated: false });
    },
}));
