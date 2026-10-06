-- Runs automatically the first time the container starts
CREATE EXTENSION IF NOT EXISTS vector;

-- Hibernate will create/manage this table via ddl-auto: update,
-- but the vector column type needs pgvector to exist first, which is why
-- this file runs before the app connects.
