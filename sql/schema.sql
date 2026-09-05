-- Beam mental health — user accounts (run once in the Vercel Postgres / Neon SQL editor)
CREATE TABLE IF NOT EXISTS users (
  id            uuid PRIMARY KEY,
  email         text NOT NULL UNIQUE,
  password_hash text NOT NULL,
  name          text,
  profile       jsonb,
  created_at    timestamptz NOT NULL DEFAULT now()
);
