import { SignJWT, jwtVerify } from "jose";
import { cookies } from "next/headers";

export const SESSION_COOKIE = "beam_session";
const ALG = "HS256";

function secretKey() {
  const secret =
    process.env.AUTH_SECRET ||
    "beam-dev-secret-change-me-in-production-0123456789";
  return new TextEncoder().encode(secret);
}

export async function createSessionToken(userId: string, email: string) {
  return new SignJWT({ email })
    .setProtectedHeader({ alg: ALG })
    .setSubject(userId)
    .setIssuedAt()
    .setExpirationTime("30d")
    .sign(secretKey());
}

export async function verifySessionToken(token: string) {
  try {
    const { payload } = await jwtVerify(token, secretKey());
    return payload.sub ? { userId: payload.sub } : null;
  } catch {
    return null;
  }
}

/** Server-side current user id from the session cookie (or null). */
export async function currentUserId(): Promise<string | null> {
  const jar = await cookies();
  const token = jar.get(SESSION_COOKIE)?.value;
  if (!token) return null;
  const session = await verifySessionToken(token);
  return session?.userId ?? null;
}
