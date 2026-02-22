/**
 * Supabase client singleton.
 * URL and anon key are configured here; they are safe to expose (RLS protects data).
 */
import { createClient, SupabaseClient } from '@supabase/supabase-js';

const SUPABASE_URL = (globalThis as any).__KPKN_SUPABASE_URL__ || 'https://YOUR_PROJECT.supabase.co';
const SUPABASE_ANON_KEY = (globalThis as any).__KPKN_SUPABASE_ANON_KEY__ || 'YOUR_ANON_KEY';

export const supabase: SupabaseClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: {
        autoRefreshToken: true,
        persistSession: true,
        detectSessionInUrl: true,
    },
});
