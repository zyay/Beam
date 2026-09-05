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

export async function createUser(
  email: string,
  passwordHash: string,
  name: string | null
): Promise<UserRow> {
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
