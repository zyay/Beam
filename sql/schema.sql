-- Beam mental health — user accounts (run once in the Vercel Postgres / Neon SQL editor)
CREATE TABLE IF NOT EXISTS users (
  id            uuid PRIMARY KEY,
  email         text NOT NULL UNIQUE,
  password_hash text NOT NULL,
  name          text,
  profile       jsonb,
  created_at    timestamptz NOT NULL DEFAULT now()
);

-- Daily mood check-ins (Prehľad screen). One row per user per day; a repeat
-- check-in for the same day overwrites mood/note.
CREATE TABLE IF NOT EXISTS checkins (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  day        date NOT NULL,
  mood       int  NOT NULL CHECK (mood BETWEEN 1 AND 5),
  note       text,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, day)
);
CREATE INDEX IF NOT EXISTS checkins_user_day_idx ON checkins (user_id, day DESC);
