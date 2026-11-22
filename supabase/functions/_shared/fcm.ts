// Envoi Android par FCM HTTP v1, avec le compte de service Firebase rangé dans le secret
// FCM_SERVICE_ACCOUNT (le fichier JSON entier). Le jeton OAuth est gardé tant qu'il est valable.

import type { FcmMessage } from "./payloads.ts";

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
}

let account: ServiceAccount | null | undefined;
let cached: { token: string; expires: number } | null = null;

export function fcmConfigured(): boolean {
  return serviceAccount() != null;
}

function serviceAccount(): ServiceAccount | null {
  if (account !== undefined) return account;
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  account = raw ? JSON.parse(raw) as ServiceAccount : null;
  return account;
}

export function base64url(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

export function pemToDer(pem: string): Uint8Array<ArrayBuffer> {
  // Un secret collé sur une ligne garde parfois des « \n » littéraux : on les retire aussi.
  const b64 = pem.replace(/\\n/g, "").replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

async function accessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.expires > now + 60) return cached.token;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const unsigned = `${base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }))}.${
    base64url(JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: tokenUri,
      iat: now,
      exp: now + 3600,
    }))
  }`;
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const res = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${base64url(signature)}`,
    }),
  });
  if (!res.ok) throw new Error(`OAuth Google : HTTP ${res.status} ${await res.text()}`);
  const json = await res.json();
  cached = { token: json.access_token, expires: now + (json.expires_in ?? 3600) };
  return cached.token;
}

export type SendResult = { ok: true } | { ok: false; invalidToken: boolean; error: string };

export async function sendFcm(message: FcmMessage): Promise<SendResult> {
  const sa = serviceAccount();
  if (!sa) return { ok: false, invalidToken: false, error: "FCM_SERVICE_ACCOUNT manquant" };
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${await accessToken(sa)}`, "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
  });
  if (res.ok) return { ok: true };
  const text = await res.text();
  // Jeton périmé (app désinstallée) : UNREGISTERED, ou jeton mal formé.
  const invalidToken = res.status === 404 || text.includes("UNREGISTERED") ||
    (res.status === 400 && text.includes("registration token"));
  return { ok: false, invalidToken, error: `FCM HTTP ${res.status} ${text.slice(0, 200)}` };
}
