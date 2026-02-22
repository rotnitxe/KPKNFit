-- KPKN Supabase Migration: user_data table for bidirectional sync
-- Run this in the Supabase SQL editor (Dashboard > SQL Editor)

-- 1. Create the user_data table
CREATE TABLE IF NOT EXISTS public.user_data (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    data_key   TEXT NOT NULL,
    data       JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, data_key)
);

-- 2. Index for fast lookups by user
CREATE INDEX IF NOT EXISTS idx_user_data_user_id ON public.user_data(user_id);
CREATE INDEX IF NOT EXISTS idx_user_data_key ON public.user_data(user_id, data_key);

-- 3. Row Level Security (users can only access their own data)
ALTER TABLE public.user_data ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own data"
    ON public.user_data FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own data"
    ON public.user_data FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own data"
    ON public.user_data FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own data"
    ON public.user_data FOR DELETE
    USING (auth.uid() = user_id);

-- 4. Auto-update updated_at on modification
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON public.user_data
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();
