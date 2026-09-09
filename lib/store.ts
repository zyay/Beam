/**
 * User storage: Vercel Postgres (Neon) when POSTGRES_URL is set,
 * otherwise a local JSON file so the app runs in dev/CI with no database.
 * Both backends share one interface; production always has POSTGRES_URL.
 */
import { promises as fs } from "fs";
import path from "path";
import { randomUUID } from "crypto";

export type Profile = {
  mood?: string;
  stressors?: string[];
  goals?: string[];
  cadence?: string;
  time?: string;
};

export type UserRow = {
  id: string;
  email: string;
  password_hash: string;
  name: string | null;
  profile: Profile | null;
  created_at: string;
};

const FILE = path.join(process.cwd(), ".data", "users.json");

async function fileRead(): Promise<UserRow[]> {
  try {
    return JSON.parse(await fs.readFile(FILE, "utf-8")) as UserRow[];
  } catch {
    return [];
  }
}

async function fileWrite(rows: UserRow[]) {
  await fs.mkdir(path.dirname(FILE), { recursive: true });
  await fs.writeFile(FILE, JSON.stringify(rows, null, 2), "utf-8");
}

function hasDb() {
  return Boolean(process.env.POSTGRES_URL);
}

/** On Vercel the filesystem is ephemeral — without POSTGRES_URL auth must
 * fail loudly instead of silently losing accounts. */
function assertDb() {
  if (!hasDb() && process.env.VERCEL) {
    throw new Error("NO_DB");
  }
}

export async function createUser(
  email: string,
  passwordHash: string,
  name: string | null
): Promise<UserRow> {
  assertDb();
  if (hasDb()) {
    const { sql } = await import("@vercel/postgres");
    const res = await sql`
      INSERT INTO users (id, email, password_hash, name, profile)
      VALUES (${randomUUID()}, ${email.toLowerCase()}, ${passwordHash}, ${name}, '{}'::jsonb)
      RETURNING id, email, password_hash, name, profile, created_at
    `;
    return res.rows[0] as UserRow;
  }
  const rows = await fileRead();
  const row: UserRow = {
    id: randomUUID(),
    email: email.toLowerCase(),
    password_hash: passwordHash,
    name,
    profile: null,
    created_at: new Date().toISOString(),
  };
  rows.push(row);
  await fileWrite(rows);
  return row;
}

export async function getUserByEmail(email: string): Promise<UserRow | null> {
  assertDb();
  if (hasDb()) {
    const { sql } = await import("@vercel/postgres");
    const res = await sql`
      SELECT id, email, password_hash, name, profile, created_at
      FROM users WHERE email = ${email.toLowerCase()} LIMIT 1
    `;
    return (res.rows[0] as UserRow) ?? null;
  }
  const rows = await fileRead();
  return rows.find((r) => r.email === email.toLowerCase()) ?? null;
}

export async function getUserById(id: string): Promise<UserRow | null> {
  if (hasDb()) {
    const { sql } = await import("@vercel/postgres");
    const res = await sql`
      SELECT id, email, password_hash, name, profile, created_at
      FROM users WHERE id = ${id} LIMIT 1
    `;
    return (res.rows[0] as UserRow) ?? null;
  }
  const rows = await fileRead();
  return rows.find((r) => r.id === id) ?? null;
}

export async function updateProfile(
  id: string,
  profile: Profile,
  name?: string | null
): Promise<UserRow | null> {
  if (hasDb()) {
    const { sql } = await import("@vercel/postgres");
    const row = name !== undefined
      ? (
          await sql`
            UPDATE users SET profile = ${JSON.stringify(profile)}::jsonb, name = ${name}
            WHERE id = ${id}
            RETURNING id, email, password_hash, name, profile, created_at
          `
        ).rows[0]
      : (
          await sql`
            UPDATE users SET profile = ${JSON.stringify(profile)}::jsonb
            WHERE id = ${id}
            RETURNING id, email, password_hash, name, profile, created_at
          `
        ).rows[0];
    return (row as UserRow) ?? null;
  }
  const rows = await fileRead();
  const row = rows.find((r) => r.id === id);
  if (!row) return null;
  row.profile = profile;
  if (name !== undefined && name !== null) row.name = name;
  await fileWrite(rows);
  return row;
}

/* ------------------------- mood check-ins (Prehľad) ------------------------ */

export type Checkin = {
  day: string; // YYYY-MM-DD, one row per user per day
  mood: number; // 1..5
  note: string | null;
  created_at: string;
};

const CHECKINS_FILE = path.join(process.cwd(), ".data", "checkins.json");

type CheckinFileShape = Record<string, Record<string, Checkin>>;

async function checkinsFileRead(): Promise<CheckinFileShape> {
  try {
    return JSON.parse(await fs.readFile(CHECKINS_FILE, "utf-8")) as CheckinFileShape;
  } catch {
    return {};
  }
}

async function checkinsFileWrite(data: CheckinFileShape) {
  await fs.mkdir(path.dirname(CHECKINS_FILE), { recursive: true });
  await fs.writeFile(CHECKINS_FILE, JSON.stringify(data, null, 2), "utf-8");
}

function todayKey(): string {
  return new Date().toISOString().slice(0, 10);
}

export async function upsertCheckin(
  userId: string,
  day: string,
  mood: number,
  note: string | null
): Promise<Checkin> {
  const safeDay = /^\d{4}-\d{2}-\d{2}$/.test(day) ? day : todayKey();
  const safeMood = Math.min(5, Math.max(1, Math.round(mood)));
  const cleanNote = note?.trim() ? note.trim().slice(0, 500) : null;
  if (hasDb()) {
    const { sql } = await import("@vercel/postgres");
    const res = await sql`
      INSERT INTO checkins (id, user_id, day, mood, note)
      VALUES (gen_random_uuid(), ${userId}, ${safeDay}::date, ${safeMood}, ${cleanNote})
      ON CONFLICT (user_id, day)
      DO UPDATE SET mood = EXCLUDED.mood, note = EXCLUDED.note, created_at = now()
      RETURNING to_char(day, 'YYYY-MM-DD') AS day, mood, note, created_at
    `;
    return {
      day: res.rows[0].day as string,
      mood: res.rows[0].mood as number,
      note: (res.rows[0].note as string | null) ?? null,
      created_at: (res.rows[0].created_at as Date).toISOString(),
    };
  }
  const data = await checkinsFileRead();
  const row: Checkin = {
    day: safeDay,
    mood: safeMood,
    note: cleanNote,
    created_at: new Date().toISOString(),
  };
  data[userId] = data[userId] ?? {};
  data[userId][safeDay] = row;
  await checkinsFileWrite(data);
  return row;
}

export async function listCheckins(userId: string, days = 90): Promise<Checkin[]> {
  if (hasDb()) {
    const { sql } = await import("@vercel/postgres");
    const res = await sql`
      SELECT to_char(day, 'YYYY-MM-DD') AS day, mood, note, created_at
      FROM checkins
      WHERE user_id = ${userId} AND day >= current_date - ${days}::int
      ORDER BY day ASC
    `;
    return res.rows.map((r) => ({
      day: r.day as string,
      mood: r.mood as number,
      note: (r.note as string | null) ?? null,
      created_at: (r.created_at as Date).toISOString(),
    }));
  }
  const data = await checkinsFileRead();
  const all = Object.values(data[userId] ?? {});
  const cutoff = new Date(Date.now() - days * 86400000).toISOString().slice(0, 10);
  return all
    .filter((c) => c.day >= cutoff)
    .sort((a, b) => a.day.localeCompare(b.day));
}
