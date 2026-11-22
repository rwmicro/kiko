// Jetons d'accès LiveKit (JWT signé en HS256 avec la clé d'API du projet LiveKit).
// Secrets de la fonction : LIVEKIT_URL (wss://…), LIVEKIT_API_KEY, LIVEKIT_API_SECRET.
//
// Un jeton ne donne droit qu'à une salle, celle de l'appel, et qu'au micro : pas de caméra,
// pas de partage d'écran, pas de messages de données. Il expire au bout de 10 minutes
// (il ne sert qu'à entrer : l'appel continue ensuite tant que la salle est ouverte).

import { base64url } from "./fcm.ts";

export function livekitConfigured(): boolean {
  return Boolean(Deno.env.get("LIVEKIT_URL") && Deno.env.get("LIVEKIT_API_KEY") && Deno.env.get("LIVEKIT_API_SECRET"));
}

export interface TokenRequest {
  apiKey: string;
  apiSecret: string;
  /** L'identifiant Supabase de la personne : ni nom, ni e-mail ne partent chez LiveKit. */
  identity: string;
  room: string;
  now?: Date;
  ttlSeconds?: number;
}

/** Les droits d'un participant, tels que LiveKit les lit dans le jeton (champ « video »). */
export function claims(req: TokenRequest): Record<string, unknown> {
  const now = Math.floor((req.now ?? new Date()).getTime() / 1000);
  return {
    iss: req.apiKey,
    sub: req.identity,
    nbf: now - 10,
    exp: now + (req.ttlSeconds ?? 600),
    jti: crypto.randomUUID(),
    video: {
      room: req.room,
      roomJoin: true,
      canPublish: true,
      canSubscribe: true,
      canPublishData: false,
      canPublishSources: ["microphone"],
      canUpdateOwnMetadata: false,
    },
    // La salle se crée à la première entrée : deux personnes au plus, fermée vite une fois vide.
    roomConfig: { name: req.room, maxParticipants: 2, emptyTimeout: 60, departureTimeout: 20 },
  };
}

export async function livekitToken(req: TokenRequest): Promise<string> {
  const unsigned = `${base64url(JSON.stringify({ alg: "HS256", typ: "JWT" }))}.${base64url(JSON.stringify(claims(req)))}`;
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(req.apiSecret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(unsigned)));
  return `${unsigned}.${base64url(signature)}`;
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: unknown): value is string {
  return typeof value === "string" && uuid.test(value);
}
