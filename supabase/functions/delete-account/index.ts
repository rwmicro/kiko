// « Supprimer mon compte » depuis l'app (jeton de la personne connectée), et chaque nuit, appelée par
// la base avec le secret rangé dans Vault, pour les comptes sans ouverture de l'app depuis 2 ans.
import { adminClient, authorized } from "../_shared/db.ts";
import { forget } from "../_shared/forget.ts";

// L'aperçu web appelle la fonction depuis le navigateur.
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function reply(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: cors });
  if (req.method !== "POST") return reply({ error: "method not allowed" }, 405);
  const db = adminClient();

  if (await authorized(req, db)) {
    const ids = await db.rpc("inactive_accounts", { p_limit: 50 });
    if (ids.error) return reply({ error: ids.error.message }, 500);
    let deleted = 0;
    for (const id of (ids.data ?? []) as string[]) {
      try {
        await forget(db, id);
        deleted++;
      } catch (e) {
        console.error("inactive account not deleted:", (e as Error).message);
      }
    }
    return reply({ deleted });
  }

  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return reply({ error: "unauthorized" }, 401);
  const { data, error } = await db.auth.getUser(token);
  if (error || !data.user) return reply({ error: "unauthorized" }, 401);

  try {
    await forget(db, data.user.id);
  } catch (e) {
    return reply({ error: (e as Error).message }, 500);
  }
  return reply({ deleted: true });
});
