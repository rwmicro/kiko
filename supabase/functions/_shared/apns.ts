// Envoi iPhone par APNs, avec une clé d'authentification .p8 (secrets APNS_KEY, APNS_KEY_ID,
// APNS_TEAM_ID). Le jeton JWT est signé en ES256 et réutilisé pendant 50 minutes, comme
// le demande Apple. Les jetons de développement (Xcode) passent par le serveur « sandbox ».

import type { ApnsRequest } from "./payloads.ts";
import { base64url, pemToDer, type SendResult } from "./fcm.ts";

let cached: { jwt: string; issued: number } | null = null;

export function apnsConfigured(): boolean {
  return Boolean(Deno.env.get("APNS_KEY") && Deno.env.get("APNS_KEY_ID") && Deno.env.get("APNS_TEAM_ID"));
}

export function bundleId(): string {
  return Deno.env.get("APNS_BUNDLE_ID") ?? "app.kiko";
}

async function providerToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && now - cached.issued < 50 * 60) return cached.jwt;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(Deno.env.get("APNS_KEY")!),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  const unsigned = `${base64url(JSON.stringify({ alg: "ES256", kid: Deno.env.get("APNS_KEY_ID") }))}.${
    base64url(JSON.stringify({ iss: Deno.env.get("APNS_TEAM_ID"), iat: now }))
  }`;
  // WebCrypto rend déjà la signature au format r||s attendu par JWT.
  const signature = new Uint8Array(
    await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, new TextEncoder().encode(unsigned)),
  );
  cached = { jwt: `${unsigned}.${base64url(signature)}`, issued: now };
  return cached.jwt;
}

export async function sendApns(req: ApnsRequest): Promise<SendResult> {
  if (!apnsConfigured()) return { ok: false, invalidToken: false, error: "Clé APNs manquante" };
  const host = req.sandbox ? "api.sandbox.push.apple.com" : "api.push.apple.com";
  const res = await fetch(`https://${host}/3/device/${req.token}`, {
    method: "POST",
    headers: { authorization: `bearer ${await providerToken()}`, ...req.headers },
    body: JSON.stringify(req.body),
  });
  if (res.ok) return { ok: true };
  const text = await res.text();
  const invalidToken = res.status === 410 || /BadDeviceToken|Unregistered|DeviceTokenNotForTopic/.test(text);
  return { ok: false, invalidToken, error: `APNs HTTP ${res.status} ${text.slice(0, 200)}` };
}
