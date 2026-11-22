// Un jeton pour entrer dans la salle LiveKit d'un appel. Appelée par l'app, avec le jeton de la
// personne connectée : l'appelant dès que ça sonne, la personne appelée une fois qu'elle a décroché
// (voir call_room_for dans supabase/migrations/20261122000000_calls.sql).
import { adminClient, json } from "../_shared/db.ts";
import { isUuid, livekitConfigured, livekitToken } from "../_shared/livekit.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method not allowed" }, 405);
  if (!livekitConfigured()) return json({ error: "LiveKit n'est pas configuré" }, 503);
  const db = adminClient();

  const bearer = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!bearer) return json({ error: "unauthorized" }, 401);
  const { data: auth, error: authError } = await db.auth.getUser(bearer);
  if (authError || !auth.user) return json({ error: "unauthorized" }, 401);

  const body = await req.json().catch(() => null) as { call_id?: unknown } | null;
  const callId = body?.call_id;
  if (!isUuid(callId)) return json({ error: "call_id manquant" }, 400);

  const { data: room, error } = await db.rpc("call_room_for", { p_call: callId, p_user: auth.user.id });
  if (error) return json({ error: error.message }, 500);
  // Appel fini, refusé, pas encore décroché, ou pas le sien : même réponse, sans détail.
  if (typeof room !== "string") return json({ error: "call_unavailable" }, 403);

  const token = await livekitToken({
    apiKey: Deno.env.get("LIVEKIT_API_KEY")!,
    apiSecret: Deno.env.get("LIVEKIT_API_SECRET")!,
    identity: auth.user.id,
    room,
  });
  return json({ url: Deno.env.get("LIVEKIT_URL"), token, room });
});
