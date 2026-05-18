-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Enable trigram indexing (required for LIKE/ILIKE optimized search later)
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Enable full-text search dictionary
CREATE EXTENSION IF NOT EXISTS "unaccent";