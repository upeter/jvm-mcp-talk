-- Ensure pgvector extension exists before any tables reference the `vector` type
-- This file name is prefixed with 00- so it runs first

-- Public schema is default, but ensure it exists
CREATE SCHEMA IF NOT EXISTS public;

-- Create the pgvector extension in the public schema
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

-- Optional: make sure current user can use the type
-- Not strictly needed for superuser, but safe for non-superuser scenarios
GRANT USAGE ON SCHEMA public TO PUBLIC;
